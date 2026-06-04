#!/usr/bin/env python3
"""本地 MySQL -> PostgreSQL 真数据覆盖迁移脚本。

用途：
1. 盘点当前本地 MySQL / PostgreSQL 的业务表、列映射与行数差异
2. 在执行覆盖导入前，保留 MySQL 全量备份与 PostgreSQL 当前快照
3. 生成并执行 PostgreSQL 导入 SQL，覆盖当前种子数据，使其回到 MySQL 真相层
4. 修正 identity / sequence，并输出逐表对账结果

注意：
- 仅面向当前仓库本地 Docker 基础设施：`bishe_mysql` / `bishe_postgres`
- 默认排除 `flyway_schema_history`
- 当前唯一允许的 source-only 列差异是 `skill_node_resources.id`
"""

from __future__ import annotations

import argparse
import gzip
import json
import subprocess
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path
from typing import Any


EXCLUDED_TABLES = {"flyway_schema_history"}
ALLOWED_SOURCE_ONLY_COLUMNS = {("skill_node_resources", "id")}
JSON_TYPES = {"json", "jsonb"}
TEXT_TYPES = {"text", "character varying", "character"}
INTEGER_TYPES = {"smallint", "integer", "bigint"}
NUMERIC_TYPES = {"numeric"}
TEMP_DIR_NAME = "db-migration"
DEFAULT_BATCH_SIZE = 100


class MigrationError(RuntimeError):
    """业务化迁移异常。"""


@dataclass(frozen=True)
class MysqlConfig:
    container: str
    database: str
    user: str
    password: str


@dataclass(frozen=True)
class PostgresConfig:
    container: str
    database: str
    user: str
    password: str


@dataclass(frozen=True)
class ColumnMeta:
    table_name: str
    ordinal_position: int
    column_name: str
    data_type: str


def now_utc() -> str:
    return datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")


def info(message: str) -> None:
    print(f"[INFO] {message}")


def done(message: str) -> None:
    print(f"[DONE] {message}")


def warn(message: str) -> None:
    print(f"[WARN] {message}", file=sys.stderr)


def escape_mysql_identifier(identifier: str) -> str:
    return f"`{identifier.replace('`', '``')}`"


def escape_pg_identifier(identifier: str) -> str:
    return f'"{identifier.replace(chr(34), chr(34) * 2)}"'


def sql_quote(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def run_command(
    command: list[str],
    *,
    cwd: Path,
    input_text: str | None = None,
) -> str:
    result = subprocess.run(
        command,
        cwd=cwd,
        input=input_text,
        text=True,
        capture_output=True,
        check=False,
    )
    if result.returncode != 0:
        stderr = result.stderr.strip()
        stdout = result.stdout.strip()
        raise MigrationError(
            "命令执行失败：\n"
            f"  cmd: {' '.join(command)}\n"
            f"  stdout: {stdout or '<empty>'}\n"
            f"  stderr: {stderr or '<empty>'}"
        )
    return result.stdout


def run_mysql_query(root: Path, config: MysqlConfig, sql: str) -> str:
    return run_command(
        [
            "docker",
            "exec",
            config.container,
            "mysql",
            f"-u{config.user}",
            f"-p{config.password}",
            "--default-character-set=utf8mb4",
            "-NB",
            "--raw",
            "-D",
            config.database,
            "-e",
            sql,
        ],
        cwd=root,
    )


def run_postgres_query(root: Path, config: PostgresConfig, sql: str, *, database: str | None = None) -> str:
    return run_command(
        [
            "docker",
            "exec",
            "-e",
            f"PGPASSWORD={config.password}",
            config.container,
            "psql",
            "-v",
            "ON_ERROR_STOP=1",
            "-U",
            config.user,
            "-d",
            database or config.database,
            "-At",
            "-F",
            "\t",
            "-c",
            sql,
        ],
        cwd=root,
    )


def run_postgres_script(root: Path, config: PostgresConfig, sql_text: str) -> None:
    run_command(
        [
            "docker",
            "exec",
            "-i",
            "-e",
            f"PGPASSWORD={config.password}",
            config.container,
            "psql",
            "-v",
            "ON_ERROR_STOP=1",
            "-U",
            config.user,
            "-d",
            config.database,
        ],
        cwd=root,
        input_text=sql_text,
    )


def backup_postgres(root: Path, config: PostgresConfig, backup_dir: Path, timestamp: str) -> Path:
    backup_dir.mkdir(parents=True, exist_ok=True)
    output_file = backup_dir / f"{config.database}_before_mysql_data_cutover_{timestamp}.sql.gz"
    info(f"备份当前 PostgreSQL -> {output_file}")
    result = subprocess.run(
        [
            "docker",
            "exec",
            "-e",
            f"PGPASSWORD={config.password}",
            config.container,
            "pg_dump",
            "--clean",
            "--if-exists",
            "--no-owner",
            "--no-privileges",
            "-U",
            config.user,
            "-d",
            config.database,
        ],
        cwd=root,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if result.returncode != 0:
        raise MigrationError(
            "PostgreSQL 备份失败：\n"
            f"  stdout: {result.stdout.decode().strip() or '<empty>'}\n"
            f"  stderr: {result.stderr.decode().strip() or '<empty>'}"
        )
    with gzip.open(output_file, "wb") as handle:
        handle.write(result.stdout)
    return output_file


def backup_mysql(root: Path) -> None:
    info("执行 MySQL 全量备份脚本")
    run_command(["bash", "scripts/db/backup_local_mysql_full.sh"], cwd=root)


def parse_table_columns(output: str) -> dict[str, list[ColumnMeta]]:
    tables: dict[str, list[ColumnMeta]] = {}
    for raw_line in output.splitlines():
        if not raw_line.strip():
            continue
        table_name, ordinal_position, column_name, data_type = raw_line.split("\t", 3)
        tables.setdefault(table_name, []).append(
            ColumnMeta(
                table_name=table_name,
                ordinal_position=int(ordinal_position),
                column_name=column_name,
                data_type=data_type,
            )
        )
    return tables


def parse_primary_keys(output: str) -> dict[str, list[str]]:
    primary_keys: dict[str, list[str]] = {}
    for raw_line in output.splitlines():
        if not raw_line.strip():
            continue
        table_name, column_name, _ordinal = raw_line.split("\t", 2)
        primary_keys.setdefault(table_name, []).append(column_name)
    return primary_keys


def fetch_mysql_tables(root: Path, config: MysqlConfig) -> list[str]:
    sql = (
        "SELECT table_name "
        "FROM information_schema.tables "
        "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' "
        "ORDER BY table_name"
    )
    return [line.strip() for line in run_mysql_query(root, config, sql).splitlines() if line.strip()]


def fetch_postgres_tables(root: Path, config: PostgresConfig) -> list[str]:
    sql = (
        "SELECT table_name "
        "FROM information_schema.tables "
        "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' "
        "ORDER BY table_name"
    )
    return [line.strip() for line in run_postgres_query(root, config, sql).splitlines() if line.strip()]


def fetch_mysql_columns(root: Path, config: MysqlConfig) -> dict[str, list[ColumnMeta]]:
    sql = (
        "SELECT table_name, ordinal_position, column_name, data_type "
        "FROM information_schema.columns "
        "WHERE table_schema = DATABASE() "
        "ORDER BY table_name, ordinal_position"
    )
    return parse_table_columns(run_mysql_query(root, config, sql))


def fetch_postgres_columns(root: Path, config: PostgresConfig) -> dict[str, list[ColumnMeta]]:
    sql = (
        "SELECT table_name, ordinal_position, column_name, data_type "
        "FROM information_schema.columns "
        "WHERE table_schema = 'public' "
        "ORDER BY table_name, ordinal_position"
    )
    return parse_table_columns(run_postgres_query(root, config, sql))


def fetch_mysql_primary_keys(root: Path, config: MysqlConfig) -> dict[str, list[str]]:
    sql = (
        "SELECT table_name, column_name, ordinal_position "
        "FROM information_schema.key_column_usage "
        "WHERE table_schema = DATABASE() AND constraint_name = 'PRIMARY' "
        "ORDER BY table_name, ordinal_position"
    )
    return parse_primary_keys(run_mysql_query(root, config, sql))


def fetch_postgres_sequence_columns(root: Path, config: PostgresConfig) -> list[tuple[str, str]]:
    sql = (
        "SELECT table_name, column_name "
        "FROM information_schema.columns "
        "WHERE table_schema = 'public' "
        "  AND (is_identity = 'YES' OR column_default LIKE 'nextval(%') "
        "ORDER BY table_name, ordinal_position"
    )
    pairs: list[tuple[str, str]] = []
    for raw_line in run_postgres_query(root, config, sql).splitlines():
        if not raw_line.strip():
            continue
        table_name, column_name = raw_line.split("\t", 1)
        if table_name in EXCLUDED_TABLES:
            continue
        pairs.append((table_name, column_name))
    return pairs


def exact_mysql_count(root: Path, config: MysqlConfig, table_name: str) -> int:
    sql = f"SELECT COUNT(*) FROM {escape_mysql_identifier(table_name)}"
    return int(run_mysql_query(root, config, sql).strip() or "0")


def exact_postgres_count(root: Path, config: PostgresConfig, table_name: str) -> int:
    sql = f"SELECT COUNT(*) FROM public.{escape_pg_identifier(table_name)}"
    return int(run_postgres_query(root, config, sql).strip() or "0")


def compare_schemas(
    mysql_columns: dict[str, list[ColumnMeta]],
    postgres_columns: dict[str, list[ColumnMeta]],
) -> tuple[list[tuple[str, str]], list[tuple[str, str]]]:
    source_only: list[tuple[str, str]] = []
    target_only: list[tuple[str, str]] = []

    for table_name, source_meta in mysql_columns.items():
        source_names = {item.column_name for item in source_meta}
        target_names = {item.column_name for item in postgres_columns.get(table_name, [])}
        for column_name in sorted(source_names - target_names):
            source_only.append((table_name, column_name))
        for column_name in sorted(target_names - source_names):
            target_only.append((table_name, column_name))

    return source_only, target_only


def ensure_expected_layout(
    mysql_tables: list[str],
    postgres_tables: list[str],
    mysql_columns: dict[str, list[ColumnMeta]],
    postgres_columns: dict[str, list[ColumnMeta]],
) -> None:
    mysql_table_set = set(mysql_tables)
    postgres_table_set = set(postgres_tables)
    if mysql_table_set != postgres_table_set:
        missing_in_pg = sorted(mysql_table_set - postgres_table_set)
        missing_in_mysql = sorted(postgres_table_set - mysql_table_set)
        raise MigrationError(
            "双库业务表集合不一致，无法继续导入：\n"
            f"  MySQL-only tables: {missing_in_pg}\n"
            f"  PostgreSQL-only tables: {missing_in_mysql}"
        )

    source_only, target_only = compare_schemas(mysql_columns, postgres_columns)
    unexpected_source_only = sorted(set(source_only) - ALLOWED_SOURCE_ONLY_COLUMNS)
    if unexpected_source_only or target_only:
        raise MigrationError(
            "检测到超出预期的列差异，当前脚本不会盲目导入：\n"
            f"  unexpected source-only: {unexpected_source_only}\n"
            f"  target-only: {target_only}"
        )


def load_mysql_rows_as_json(
    root: Path,
    mysql_config: MysqlConfig,
    table_name: str,
    column_names: list[str],
    order_columns: list[str],
) -> list[dict[str, Any]]:
    if not column_names:
        return []

    json_pairs = ", ".join(f"{sql_quote(column)}" + ", " + escape_mysql_identifier(column) for column in column_names)
    order_clause = ""
    if order_columns:
        order_expr = ", ".join(escape_mysql_identifier(column) for column in order_columns)
        order_clause = f" ORDER BY {order_expr}"
    sql = f"SELECT JSON_OBJECT({json_pairs}) FROM {escape_mysql_identifier(table_name)}{order_clause}"
    output = run_mysql_query(root, mysql_config, sql)
    rows: list[dict[str, Any]] = []
    for line_no, raw_line in enumerate(output.splitlines(), start=1):
        if not raw_line.strip():
            continue
        try:
            rows.append(json.loads(raw_line, parse_float=Decimal))
        except json.JSONDecodeError as exc:
            snippet = raw_line[max(0, exc.pos - 120) : exc.pos + 120]
            raise MigrationError(
                f"MySQL 行转 JSON 失败：table={table_name}, line={line_no}, pos={exc.pos}, msg={exc.msg}, snippet={snippet!r}"
            ) from exc
    return rows


def normalize_bool(value: Any) -> str:
    if isinstance(value, bool):
        return "TRUE" if value else "FALSE"
    if isinstance(value, (int, Decimal)):
        if value == 1:
            return "TRUE"
        if value == 0:
            return "FALSE"
    if isinstance(value, str):
        normalized = value.strip().lower()
        if normalized in {"1", "true", "t", "yes", "y", "on"}:
            return "TRUE"
        if normalized in {"0", "false", "f", "no", "n", "off"}:
            return "FALSE"
    raise MigrationError(f"无法把值转换为 boolean：{value!r}")


def normalize_integer(value: Any) -> str:
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, Decimal):
        return str(int(value))
    if isinstance(value, int):
        return str(value)
    if isinstance(value, str) and value.strip():
        return str(int(value.strip()))
    raise MigrationError(f"无法把值转换为整数：{value!r}")


def normalize_numeric(value: Any) -> str:
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, Decimal):
        return format(value, "f")
    if isinstance(value, int):
        return str(value)
    if isinstance(value, float):
        return format(Decimal(str(value)), "f")
    if isinstance(value, str) and value.strip():
        return format(Decimal(value.strip()), "f")
    raise MigrationError(f"无法把值转换为 numeric：{value!r}")


def json_ready(value: Any) -> Any:
    if isinstance(value, Decimal):
        if value == value.to_integral():
            return int(value)
        return float(value)
    if isinstance(value, list):
        return [json_ready(item) for item in value]
    if isinstance(value, dict):
        return {key: json_ready(item) for key, item in value.items()}
    return value


def normalize_text(value: Any) -> str:
    if isinstance(value, (dict, list)):
        return json.dumps(json_ready(value), ensure_ascii=False, separators=(",", ":"))
    if isinstance(value, Decimal):
        return format(value, "f")
    if isinstance(value, bool):
        return "true" if value else "false"
    return str(value)


def normalize_json(value: Any) -> str:
    if isinstance(value, str):
        candidate = value.strip()
        if not candidate:
            raise MigrationError("json/jsonb 字段出现空字符串，无法导入")
        json.loads(candidate, parse_float=Decimal)
        return candidate
    return json.dumps(json_ready(value), ensure_ascii=False, separators=(",", ":"))


def format_value_for_pg(value: Any, data_type: str) -> str:
    if value is None:
        return "NULL"
    if data_type == "boolean":
        return normalize_bool(value)
    if data_type in INTEGER_TYPES:
        return normalize_integer(value)
    if data_type in NUMERIC_TYPES:
        return normalize_numeric(value)
    if data_type in JSON_TYPES:
        return sql_quote(normalize_json(value))
    if data_type in TEXT_TYPES or data_type.startswith("timestamp") or data_type == "date":
        return sql_quote(normalize_text(value))
    raise MigrationError(f"当前脚本尚未支持 PostgreSQL 类型：{data_type}")


def batched(items: list[str], batch_size: int) -> list[list[str]]:
    return [items[index : index + batch_size] for index in range(0, len(items), batch_size)]


def build_import_sql(
    root: Path,
    mysql_config: MysqlConfig,
    postgres_columns: dict[str, list[ColumnMeta]],
    mysql_primary_keys: dict[str, list[str]],
    sequence_columns: list[tuple[str, str]],
    table_order: list[str],
    batch_size: int,
) -> str:
    sql_parts: list[str] = [
        "\\set ON_ERROR_STOP on",
        "BEGIN;",
        "SET LOCAL client_min_messages = WARNING;",
        "SET LOCAL session_replication_role = replica;",
    ]

    truncate_tables = ", ".join(f"public.{escape_pg_identifier(table_name)}" for table_name in table_order)
    sql_parts.append(f"TRUNCATE TABLE {truncate_tables} RESTART IDENTITY CASCADE;")

    for table_name in table_order:
        target_columns = postgres_columns[table_name]
        column_names = [column.column_name for column in target_columns]
        data_types = {column.column_name: column.data_type for column in target_columns}
        rows = load_mysql_rows_as_json(
            root,
            mysql_config,
            table_name,
            column_names,
            mysql_primary_keys.get(table_name, []),
        )
        if not rows:
            continue

        insert_head = (
            f"INSERT INTO public.{escape_pg_identifier(table_name)} "
            f"({', '.join(escape_pg_identifier(column_name) for column_name in column_names)}) VALUES "
        )
        rendered_rows: list[str] = []
        for row in rows:
            rendered_columns = [
                format_value_for_pg(row.get(column_name), data_types[column_name])
                for column_name in column_names
            ]
            rendered_rows.append("(" + ", ".join(rendered_columns) + ")")
        for chunk in batched(rendered_rows, batch_size):
            sql_parts.append(insert_head + ",\n".join(chunk) + ";")

    sql_parts.append("COMMIT;")

    for table_name, column_name in sequence_columns:
        sql_parts.append(
            "SELECT setval("
            f"pg_get_serial_sequence('public.{table_name}', '{column_name}'), "
            f"COALESCE((SELECT MAX({escape_pg_identifier(column_name)}) FROM public.{escape_pg_identifier(table_name)}), 1), "
            f"(SELECT COUNT(*) > 0 FROM public.{escape_pg_identifier(table_name)})"
            ");"
        )

    return "\n".join(sql_parts) + "\n"


def collect_counts(
    root: Path,
    mysql_config: MysqlConfig,
    postgres_config: PostgresConfig,
    tables: list[str],
) -> list[tuple[str, int, int, str]]:
    rows: list[tuple[str, int, int, str]] = []
    for table_name in tables:
        mysql_count = exact_mysql_count(root, mysql_config, table_name)
        postgres_count = exact_postgres_count(root, postgres_config, table_name)
        status = "MATCH" if mysql_count == postgres_count else "DIFF"
        rows.append((table_name, mysql_count, postgres_count, status))
    return rows


def write_count_report(report_file: Path, rows: list[tuple[str, int, int, str]]) -> None:
    report_file.parent.mkdir(parents=True, exist_ok=True)
    content = ["table_name\tmysql_count\tpostgres_count\tstatus"]
    content.extend(
        f"{table_name}\t{mysql_count}\t{postgres_count}\t{status}"
        for table_name, mysql_count, postgres_count, status in rows
    )
    report_file.write_text("\n".join(content) + "\n", encoding="utf-8")


def print_count_summary(rows: list[tuple[str, int, int, str]]) -> None:
    diff_rows = [row for row in rows if row[3] != "MATCH"]
    print("table_name\tmysql_count\tpostgres_count\tstatus")
    for table_name, mysql_count, postgres_count, status in rows:
        print(f"{table_name}\t{mysql_count}\t{postgres_count}\t{status}")
    done(f"逐表对账完成：共 {len(rows)} 张业务表，差异 {len(diff_rows)} 张")


def build_argument_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="将本地 Docker MySQL 真数据覆盖迁移到 PostgreSQL，并输出逐表对账结果。"
    )
    parser.add_argument("--apply", action="store_true", help="执行覆盖导入；默认仅做 plan / 对账")
    parser.add_argument("--yes", action="store_true", help="与 --apply 一起使用，确认执行高风险覆盖导入")
    parser.add_argument("--skip-backup", action="store_true", help="跳过导入前的 MySQL / PostgreSQL 备份")
    parser.add_argument("--mysql-container", default="bishe_mysql")
    parser.add_argument("--mysql-db", default="bishe")
    parser.add_argument("--mysql-user", default="bishe")
    parser.add_argument("--mysql-password", default="bishe")
    parser.add_argument("--postgres-container", default="bishe_postgres")
    parser.add_argument("--postgres-db", default="bishe")
    parser.add_argument("--postgres-user", default="bishe")
    parser.add_argument("--postgres-password", default="bishe")
    parser.add_argument("--batch-size", type=int, default=DEFAULT_BATCH_SIZE)
    parser.add_argument(
        "--backup-dir",
        default=".cache/db-backups",
        help="备份输出目录，默认 .cache/db-backups",
    )
    parser.add_argument(
        "--report-dir",
        default=f".cache/{TEMP_DIR_NAME}",
        help=f"报告输出目录，默认 .cache/{TEMP_DIR_NAME}",
    )
    return parser


def main() -> int:
    parser = build_argument_parser()
    args = parser.parse_args()

    if args.apply and not args.yes:
        raise MigrationError("执行覆盖导入必须显式带上 --yes")

    root = Path(__file__).resolve().parents[2]
    report_dir = (root / args.report_dir).resolve()
    backup_dir = (root / args.backup_dir).resolve()
    timestamp = now_utc()

    mysql_config = MysqlConfig(
        container=args.mysql_container,
        database=args.mysql_db,
        user=args.mysql_user,
        password=args.mysql_password,
    )
    postgres_config = PostgresConfig(
        container=args.postgres_container,
        database=args.postgres_db,
        user=args.postgres_user,
        password=args.postgres_password,
    )

    info("盘点双库表与列映射")
    mysql_tables = fetch_mysql_tables(root, mysql_config)
    postgres_tables = fetch_postgres_tables(root, postgres_config)
    mysql_columns = fetch_mysql_columns(root, mysql_config)
    postgres_columns = fetch_postgres_columns(root, postgres_config)
    ensure_expected_layout(mysql_tables, postgres_tables, mysql_columns, postgres_columns)

    table_order = [table_name for table_name in mysql_tables if table_name not in EXCLUDED_TABLES]
    mysql_primary_keys = fetch_mysql_primary_keys(root, mysql_config)
    sequence_columns = fetch_postgres_sequence_columns(root, postgres_config)

    precheck_rows = collect_counts(root, mysql_config, postgres_config, table_order)
    precheck_report = report_dir / f"mysql_pg_counts_precheck_{timestamp}.tsv"
    write_count_report(precheck_report, precheck_rows)
    info(f"已写入 precheck 报告：{precheck_report}")
    print_count_summary(precheck_rows)

    import_sql = build_import_sql(
        root,
        mysql_config,
        postgres_columns,
        mysql_primary_keys,
        sequence_columns,
        table_order,
        args.batch_size,
    )
    import_sql_file = report_dir / f"mysql_to_pg_import_{timestamp}.sql"
    import_sql_file.parent.mkdir(parents=True, exist_ok=True)
    import_sql_file.write_text(import_sql, encoding="utf-8")
    info(f"已生成导入 SQL：{import_sql_file}")

    if not args.apply:
        done("plan 模式结束，尚未写入 PostgreSQL")
        return 0

    if not args.skip_backup:
        backup_mysql(root)
        pg_backup = backup_postgres(root, postgres_config, backup_dir, timestamp)
        info(f"PostgreSQL 备份完成：{pg_backup}")
    else:
        warn("已跳过导入前备份，请确认你已有可回退快照")

    info("执行 PostgreSQL 覆盖导入")
    run_postgres_script(root, postgres_config, import_sql)

    postcheck_rows = collect_counts(root, mysql_config, postgres_config, table_order)
    postcheck_report = report_dir / f"mysql_pg_counts_postcheck_{timestamp}.tsv"
    write_count_report(postcheck_report, postcheck_rows)
    info(f"已写入 postcheck 报告：{postcheck_report}")
    print_count_summary(postcheck_rows)

    diff_rows = [row for row in postcheck_rows if row[3] != "MATCH"]
    if diff_rows:
        raise MigrationError(
            "导入已执行，但仍存在逐表行数差异，请优先恢复快照或修复脚本后再继续：\n"
            + "\n".join(
                f"  {table_name}: mysql={mysql_count}, postgres={postgres_count}"
                for table_name, mysql_count, postgres_count, _status in diff_rows
            )
        )

    done("MySQL -> PostgreSQL 真数据覆盖迁移完成，逐表行数已对齐")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except MigrationError as exc:
        print(f"[ERROR] {exc}", file=sys.stderr)
        raise SystemExit(1)
