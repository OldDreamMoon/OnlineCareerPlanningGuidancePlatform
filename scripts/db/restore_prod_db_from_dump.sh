#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="$ROOT_DIR/infra/docker/.env.prod"
COMPOSE_FILE="$ROOT_DIR/infra/docker/docker-compose.infra.prod.yml"
BACKUP_DIR="$ROOT_DIR/.cache/db-backups"
DUMP_FILE=""
MODE="auto"
RUN_CHECK=1
SKIP_BACKUP=0
RECREATE_DB=0
CONFIRMED=0
DB_NAME_OVERRIDE=""
EFFECTIVE_MODE=""
BACKUP_FILE=""
POSTGRES_DB_HOST=""
POSTGRES_DB_PORT=""
INFRA_COMPOSE_PROJECT_NAME_EFFECTIVE=""

usage() {
  cat <<'EOF'
用法：
  bash scripts/db/restore_prod_db_from_dump.sh --dump-file /path/to/bishe_refresh.sql.gz --yes

说明：
  - 这是服务器侧 PostgreSQL 覆盖恢复脚本，适用于把本地开发库导出的 plain SQL dump 覆盖到服务器 PostgreSQL
  - 默认从 infra/docker/.env.prod 读取 PostgreSQL 连接配置
  - 默认先备份服务器当前库到 .cache/db-backups/
  - 默认优先使用宿主机 psql/pg_dump；若使用 --recreate-db，则 auto 模式会优先切到 docker compose exec postgres
  - 建议本地导出时使用不带 CREATE DATABASE 的 plain dump，例如：
      PGPASSWORD=bishe pg_dump --clean --if-exists --no-owner --no-privileges -h127.0.0.1 -p5432 -Ubishe -d bishe | gzip > bishe_refresh.sql.gz

可选参数：
  --dump-file <path>      指定要导入的 .sql 或 .sql.gz 文件
  --env-file <path>       指定基础设施环境变量文件
  --compose-file <path>   指定基础设施 compose 文件
  --backup-dir <path>     指定备份输出目录，默认 .cache/db-backups
  --db-name <name>        覆盖目标数据库名，默认读取 POSTGRES_DB 或 bishe
  --psql                  强制使用宿主机 psql/pg_dump
  --compose               强制使用 docker compose exec postgres
  --recreate-db           导入前 DROP + CREATE 目标数据库（更适合结构差异较大的覆盖）
  --skip-backup           跳过导入前备份（不推荐）
  --skip-check            导入后跳过基础校验
  --yes                   确认执行覆盖导入
  -h, --help              查看帮助

推荐流程：
  1. 在本地开发机导出最新 bishe dump
  2. 把 dump 上传到服务器
  3. 登录服务器后执行：
       bash scripts/db/restore_prod_db_from_dump.sh --dump-file /path/to/bishe_refresh.sql.gz --recreate-db --yes
EOF
}

resolve_existing_path() {
  local path="$1"
  if [[ "$path" = /* ]]; then
    printf '%s\n' "$path"
    return
  fi
  printf '%s/%s\n' "$(pwd)" "$path"
}

resolve_dir_path() {
  local path="$1"
  if [[ "$path" = /* ]]; then
    printf '%s\n' "$path"
    return
  fi
  printf '%s/%s\n' "$(pwd)" "$path"
}

require_file() {
  local path="$1"
  local label="$2"
  if [[ ! -f "$path" ]]; then
    echo "[错误] $label 不存在：$path" >&2
    exit 1
  fi
}

require_command() {
  local cmd="$1"
  local label="$2"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "[错误] 缺少 $label：$cmd" >&2
    exit 1
  fi
}

validate_identifier() {
  local label="$1"
  local value="$2"
  if [[ ! "$value" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
    echo "[错误] $label 仅支持字母/数字/下划线且必须以字母或下划线开头：$value" >&2
    exit 1
  fi
}

quote_pg_identifier() {
  local value="$1"
  printf '"%s"' "$value"
}

parse_postgres_bind() {
  local bind_value="$1"
  if [[ "$bind_value" == *:* ]]; then
    POSTGRES_DB_HOST="${bind_value%:*}"
    POSTGRES_DB_PORT="${bind_value##*:}"
  else
    POSTGRES_DB_HOST="127.0.0.1"
    POSTGRES_DB_PORT="$bind_value"
  fi
}

psql_host_args() {
  printf -- "-h %s -p %s" "$POSTGRES_DB_HOST" "$POSTGRES_DB_PORT"
}

docker_compose_infra() {
  (
    cd "$ROOT_DIR"
    docker compose -p "$INFRA_COMPOSE_PROJECT_NAME_EFFECTIVE" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
  )
}

run_sql_text_via_psql() {
  local sql_text="$1"
  local user="$2"
  local password="$3"
  local db_name="$4"
  (
    cd "$ROOT_DIR"
    PGPASSWORD="$password" psql \
      --set ON_ERROR_STOP=1 \
      $(psql_host_args) \
      -U "$user" \
      -d "$db_name" <<<"$sql_text"
  )
}

run_sql_text_via_compose() {
  local sql_text="$1"
  local user="$2"
  local password="$3"
  local db_name="$4"
  printf '%s\n' "$sql_text" | docker_compose_infra \
    exec -T \
    -e IMPORT_DB_USER="$user" \
    -e IMPORT_DB_PASSWORD="$password" \
    -e IMPORT_DB_NAME="$db_name" \
    postgres \
    sh -lc '
      export PGPASSWORD="$IMPORT_DB_PASSWORD"
      psql --set ON_ERROR_STOP=1 -U "$IMPORT_DB_USER" -d "$IMPORT_DB_NAME"
    '
}

run_sql_text() {
  local sql_text="$1"
  local user="$2"
  local password="$3"
  local db_name="$4"
  case "$EFFECTIVE_MODE" in
    psql)
      run_sql_text_via_psql "$sql_text" "$user" "$password" "$db_name"
      ;;
    compose)
      run_sql_text_via_compose "$sql_text" "$user" "$password" "$db_name"
      ;;
    *)
      echo "[错误] 未知执行模式：$EFFECTIVE_MODE" >&2
      exit 1
      ;;
  esac
}

run_query_output_via_psql() {
  local sql_text="$1"
  local user="$2"
  local password="$3"
  local db_name="$4"
  (
    cd "$ROOT_DIR"
    PGPASSWORD="$password" psql \
      --set ON_ERROR_STOP=1 \
      $(psql_host_args) \
      -U "$user" \
      -d "$db_name" \
      -At <<<"$sql_text"
  )
}

run_query_output_via_compose() {
  local sql_text="$1"
  local user="$2"
  local password="$3"
  local db_name="$4"
  printf '%s\n' "$sql_text" | docker_compose_infra \
    exec -T \
    -e IMPORT_DB_USER="$user" \
    -e IMPORT_DB_PASSWORD="$password" \
    -e IMPORT_DB_NAME="$db_name" \
    postgres \
    sh -lc '
      export PGPASSWORD="$IMPORT_DB_PASSWORD"
      psql --set ON_ERROR_STOP=1 -U "$IMPORT_DB_USER" -d "$IMPORT_DB_NAME" -At
    '
}

run_query_output() {
  local sql_text="$1"
  local user="$2"
  local password="$3"
  local db_name="$4"
  case "$EFFECTIVE_MODE" in
    psql)
      run_query_output_via_psql "$sql_text" "$user" "$password" "$db_name"
      ;;
    compose)
      run_query_output_via_compose "$sql_text" "$user" "$password" "$db_name"
      ;;
    *)
      echo "[错误] 未知执行模式：$EFFECTIVE_MODE" >&2
      exit 1
      ;;
  esac
}

stream_dump_file() {
  case "$DUMP_FILE" in
    *.sql.gz)
      gzip -cd "$DUMP_FILE"
      ;;
    *.sql)
      cat "$DUMP_FILE"
      ;;
    *)
      echo "[错误] 仅支持 .sql 或 .sql.gz dump 文件：$DUMP_FILE" >&2
      exit 1
      ;;
  esac
}

filter_incompatible_dump_sql() {
  awk '
    BEGIN {
      skipped_transaction_timeout = 0
    }
    /^[[:space:]]*SET[[:space:]]+transaction_timeout[[:space:]]*=/ {
      skipped_transaction_timeout++
      next
    }
    {
      print
    }
    END {
      if (skipped_transaction_timeout > 0) {
        printf "[警告] 已过滤 dump 中 %d 条当前 PostgreSQL 版本不支持的 SET transaction_timeout 语句\n", skipped_transaction_timeout > "/dev/stderr"
      }
    }
  '
}

stream_compatible_dump_file() {
  stream_dump_file | filter_incompatible_dump_sql
}

backup_database_via_psql() {
  local output_file="$1"
  (
    cd "$ROOT_DIR"
    PGPASSWORD="$POSTGRES_BACKUP_PASSWORD" pg_dump \
      --clean \
      --if-exists \
      --no-owner \
      --no-privileges \
      $(psql_host_args) \
      -U "$POSTGRES_BACKUP_USER" \
      -d "$POSTGRES_DB_NAME" | gzip -c > "$output_file"
  )
}

backup_database_via_compose() {
  local output_file="$1"
  docker_compose_infra \
    exec -T \
    -e IMPORT_DB_USER="$POSTGRES_BACKUP_USER" \
    -e IMPORT_DB_PASSWORD="$POSTGRES_BACKUP_PASSWORD" \
    -e IMPORT_DB_NAME="$POSTGRES_DB_NAME" \
    postgres \
    sh -lc '
      export PGPASSWORD="$IMPORT_DB_PASSWORD"
      pg_dump --clean --if-exists --no-owner --no-privileges -U "$IMPORT_DB_USER" -d "$IMPORT_DB_NAME"
    ' | gzip -c > "$output_file"
}

backup_database() {
  local output_file="$1"
  case "$EFFECTIVE_MODE" in
    psql)
      backup_database_via_psql "$output_file"
      ;;
    compose)
      backup_database_via_compose "$output_file"
      ;;
    *)
      echo "[错误] 未知执行模式：$EFFECTIVE_MODE" >&2
      exit 1
      ;;
  esac
}

import_dump_via_psql() {
  (
    cd "$ROOT_DIR"
    stream_compatible_dump_file | PGPASSWORD="$POSTGRES_IMPORT_PASSWORD" psql \
      --set ON_ERROR_STOP=1 \
      $(psql_host_args) \
      -U "$POSTGRES_IMPORT_USER" \
      -d "$POSTGRES_DB_NAME"
  )
}

import_dump_via_compose() {
  stream_compatible_dump_file | docker_compose_infra \
    exec -T \
    -e IMPORT_DB_USER="$POSTGRES_IMPORT_USER" \
    -e IMPORT_DB_PASSWORD="$POSTGRES_IMPORT_PASSWORD" \
    -e IMPORT_DB_NAME="$POSTGRES_DB_NAME" \
    postgres \
    sh -lc '
      export PGPASSWORD="$IMPORT_DB_PASSWORD"
      psql --set ON_ERROR_STOP=1 -U "$IMPORT_DB_USER" -d "$IMPORT_DB_NAME"
    '
}

import_dump() {
  case "$EFFECTIVE_MODE" in
    psql)
      import_dump_via_psql
      ;;
    compose)
      import_dump_via_compose
      ;;
    *)
      echo "[错误] 未知执行模式：$EFFECTIVE_MODE" >&2
      exit 1
      ;;
  esac
}

ensure_target_database() {
  local quoted_db
  local quoted_owner
  local existing_db_count
  local create_sql

  quoted_db="$(quote_pg_identifier "$POSTGRES_DB_NAME")"
  quoted_owner="$(quote_pg_identifier "$POSTGRES_APP_USER")"
  existing_db_count="$(run_query_output "SELECT COUNT(*) FROM pg_database WHERE datname = '$POSTGRES_DB_NAME';" "$POSTGRES_ADMIN_USER" "$POSTGRES_ADMIN_PASSWORD" "$POSTGRES_ADMIN_DB" | head -n 1 || true)"

  if [[ "$RECREATE_DB" -eq 1 ]]; then
    echo "[信息] 将重建目标数据库：$POSTGRES_DB_NAME"
    run_sql_text "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$POSTGRES_DB_NAME' AND pid <> pg_backend_pid();" "$POSTGRES_ADMIN_USER" "$POSTGRES_ADMIN_PASSWORD" "$POSTGRES_ADMIN_DB"
    run_sql_text "DROP DATABASE IF EXISTS $quoted_db;" "$POSTGRES_ADMIN_USER" "$POSTGRES_ADMIN_PASSWORD" "$POSTGRES_ADMIN_DB"
    create_sql="CREATE DATABASE $quoted_db WITH OWNER $quoted_owner ENCODING 'UTF8' TEMPLATE template0;"
    run_sql_text "$create_sql" "$POSTGRES_ADMIN_USER" "$POSTGRES_ADMIN_PASSWORD" "$POSTGRES_ADMIN_DB"
    return
  fi

  if [[ "$existing_db_count" == "1" ]]; then
    echo "[信息] 检测到目标数据库已存在：$POSTGRES_DB_NAME"
    return
  fi

  echo "[信息] 目标数据库不存在，将自动创建：$POSTGRES_DB_NAME"
  create_sql="CREATE DATABASE $quoted_db WITH OWNER $quoted_owner ENCODING 'UTF8' TEMPLATE template0;"
  run_sql_text "$create_sql" "$POSTGRES_ADMIN_USER" "$POSTGRES_ADMIN_PASSWORD" "$POSTGRES_ADMIN_DB"
}

run_basic_checks() {
  local current_db
  local table_count
  local sample_tables
  local table_name
  local exists_count
  local row_count

  echo "[校验] 开始执行导入后基础校验"
  current_db="$(run_query_output "SELECT current_database();" "$POSTGRES_IMPORT_USER" "$POSTGRES_IMPORT_PASSWORD" "$POSTGRES_DB_NAME" | head -n 1)"
  table_count="$(run_query_output "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public';" "$POSTGRES_IMPORT_USER" "$POSTGRES_IMPORT_PASSWORD" "$POSTGRES_DB_NAME" | head -n 1)"
  sample_tables="$(run_query_output "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name LIMIT 15;" "$POSTGRES_IMPORT_USER" "$POSTGRES_IMPORT_PASSWORD" "$POSTGRES_DB_NAME" || true)"

  echo "[校验] current_database=$current_db"
  echo "[校验] public_table_count=$table_count"
  echo "[校验] sample_tables:"
  if [[ -n "$sample_tables" ]]; then
    printf '%s\n' "$sample_tables" | sed 's/^/  - /'
  else
    echo "  - (empty)"
  fi

  for table_name in users student_profiles mentor_profiles enterprise_profiles consult_orders ai_call_logs; do
    exists_count="$(run_query_output "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = '${table_name}';" "$POSTGRES_IMPORT_USER" "$POSTGRES_IMPORT_PASSWORD" "$POSTGRES_DB_NAME" | head -n 1)"
    if [[ "$exists_count" != "1" ]]; then
      echo "[校验][警告] 表不存在：$table_name"
      continue
    fi
    row_count="$(run_query_output "SELECT COUNT(*) FROM public.${table_name};" "$POSTGRES_IMPORT_USER" "$POSTGRES_IMPORT_PASSWORD" "$POSTGRES_DB_NAME" | head -n 1)"
    echo "[校验] $table_name rows=$row_count"
  done
}

target_database_exists() {
  local existing_db_count
  existing_db_count="$(run_query_output "SELECT COUNT(*) FROM pg_database WHERE datname = '$POSTGRES_DB_NAME';" "$POSTGRES_ADMIN_USER" "$POSTGRES_ADMIN_PASSWORD" "$POSTGRES_ADMIN_DB" | head -n 1 || true)"
  [[ "$existing_db_count" == "1" ]]
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dump-file)
      DUMP_FILE="$(resolve_existing_path "$2")"
      shift 2
      ;;
    --env-file)
      ENV_FILE="$(resolve_existing_path "$2")"
      shift 2
      ;;
    --compose-file)
      COMPOSE_FILE="$(resolve_existing_path "$2")"
      shift 2
      ;;
    --backup-dir)
      BACKUP_DIR="$(resolve_dir_path "$2")"
      shift 2
      ;;
    --db-name)
      DB_NAME_OVERRIDE="$2"
      shift 2
      ;;
    --psql)
      MODE="psql"
      shift
      ;;
    --compose)
      MODE="compose"
      shift
      ;;
    --recreate-db)
      RECREATE_DB=1
      shift
      ;;
    --skip-backup)
      SKIP_BACKUP=1
      shift
      ;;
    --skip-check)
      RUN_CHECK=0
      shift
      ;;
    --yes)
      CONFIRMED=1
      shift
      ;;
    --mysql)
      echo "[错误] 该脚本已迁移到 PostgreSQL，`--mysql` 不再受支持。" >&2
      exit 1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "[错误] 未知参数：$1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ -z "$DUMP_FILE" ]]; then
  echo "[错误] 请通过 --dump-file 指定要导入的 dump 文件。" >&2
  usage >&2
  exit 1
fi

require_file "$DUMP_FILE" "dump 文件"
require_file "$ENV_FILE" "环境变量文件"
require_file "$COMPOSE_FILE" "Compose 文件"
require_command gzip "gzip"

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

INFRA_COMPOSE_PROJECT_NAME_EFFECTIVE="${INFRA_COMPOSE_PROJECT_NAME:-bishe_prod_infra}"

POSTGRES_DB_NAME="${DB_NAME_OVERRIDE:-${POSTGRES_DB:-bishe}}"
POSTGRES_PORT_BIND_VALUE="${POSTGRES_PORT_BIND:-127.0.0.1:5432}"
POSTGRES_APP_USER="${POSTGRES_USER:-postgres}"
POSTGRES_APP_PASSWORD="${POSTGRES_PASSWORD:-}"
POSTGRES_ADMIN_DB="postgres"
POSTGRES_ADMIN_USER="$POSTGRES_APP_USER"
POSTGRES_ADMIN_PASSWORD="$POSTGRES_APP_PASSWORD"
POSTGRES_BACKUP_USER="$POSTGRES_APP_USER"
POSTGRES_BACKUP_PASSWORD="$POSTGRES_APP_PASSWORD"
POSTGRES_IMPORT_USER="$POSTGRES_APP_USER"
POSTGRES_IMPORT_PASSWORD="$POSTGRES_APP_PASSWORD"

if [[ -z "$POSTGRES_APP_PASSWORD" ]]; then
  echo "[错误] 未能从 $ENV_FILE 解析到 POSTGRES_PASSWORD" >&2
  exit 1
fi

validate_identifier "POSTGRES_DB" "$POSTGRES_DB_NAME"
validate_identifier "POSTGRES_USER" "$POSTGRES_APP_USER"
parse_postgres_bind "$POSTGRES_PORT_BIND_VALUE"

case "$MODE" in
  auto)
    if [[ "$RECREATE_DB" -eq 1 ]] && command -v docker >/dev/null 2>&1; then
      EFFECTIVE_MODE="compose"
    elif command -v psql >/dev/null 2>&1 && command -v pg_dump >/dev/null 2>&1; then
      EFFECTIVE_MODE="psql"
    elif command -v docker >/dev/null 2>&1; then
      EFFECTIVE_MODE="compose"
    else
      echo "[错误] auto 模式下未找到可用的 psql/pg_dump 或 docker compose" >&2
      exit 1
    fi
    ;;
  psql)
    EFFECTIVE_MODE="psql"
    require_command psql "psql 客户端"
    require_command pg_dump "pg_dump 客户端"
    ;;
  compose)
    EFFECTIVE_MODE="compose"
    require_command docker "docker"
    ;;
  *)
    echo "[错误] 不支持的执行模式：$MODE" >&2
    exit 1
    ;;
esac

if [[ "$EFFECTIVE_MODE" == "psql" ]]; then
  require_command psql "psql 客户端"
  require_command pg_dump "pg_dump 客户端"
else
  require_command docker "docker"
fi

if [[ "$CONFIRMED" -ne 1 ]]; then
  cat >&2 <<EOF
[错误] 这是覆盖性 PostgreSQL 导入，默认不会直接执行。

A 路径（保守）：先只备份当前服务器库，人工核对 dump 内容与目标数据库后再执行
B 路径（平衡）：执行当前命令，并保留自动备份作为回滚抓手
C 路径（激进）：在确认结构差异很大时加上 --recreate-db 强制重建数据库

回滚方式：
  直接使用脚本导出的备份文件重新执行一次恢复脚本。

验证点：
  1. 导入后 public schema 表数量不为 0
  2. users / student_profiles / mentor_profiles / enterprise_profiles / consult_orders / ai_call_logs 存在
  3. 应用启动后 Flyway 与健康检查正常

确认后重新执行：
  bash scripts/db/restore_prod_db_from_dump.sh --dump-file "$DUMP_FILE" --yes
EOF
  exit 1
fi

mkdir -p "$BACKUP_DIR"

echo "[信息] 项目根目录：$ROOT_DIR"
echo "[信息] 环境文件：$ENV_FILE"
echo "[信息] Compose 文件：$COMPOSE_FILE"
echo "[信息] dump 文件：$DUMP_FILE"
echo "[信息] 目标数据库：$POSTGRES_APP_USER@$POSTGRES_DB_HOST:$POSTGRES_DB_PORT/$POSTGRES_DB_NAME"
echo "[信息] 执行模式：$MODE -> $EFFECTIVE_MODE"

if [[ "$SKIP_BACKUP" -eq 0 ]]; then
  TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
  BACKUP_FILE="$BACKUP_DIR/${POSTGRES_DB_NAME}_before_restore_${TIMESTAMP}.sql.gz"
  if target_database_exists; then
    echo "[信息] 开始备份当前数据库 -> $BACKUP_FILE"
    backup_database "$BACKUP_FILE"
    echo "[信息] 当前数据库备份完成"
  else
    BACKUP_FILE=""
    echo "[警告] 目标数据库当前不存在，跳过导入前备份"
  fi
else
  echo "[警告] 已跳过导入前备份"
fi

ensure_target_database

echo "[信息] 开始导入 dump 文件"
import_dump
echo "[信息] dump 文件导入完成"

if [[ "$RUN_CHECK" -eq 1 ]]; then
  run_basic_checks
fi

echo "[完成] PostgreSQL 覆盖恢复完成"
if [[ -n "$BACKUP_FILE" ]]; then
  echo "[完成] 回滚备份：$BACKUP_FILE"
fi
