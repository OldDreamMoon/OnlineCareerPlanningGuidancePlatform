#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
ARCHIVE_DIR="$ROOT_DIR/scripts/db/archive/mysql_admin_demo"
ENV_FILE="$ROOT_DIR/infra/docker/.env.prod"
COMPOSE_FILE="$ROOT_DIR/infra/docker/docker-compose.infra.prod.yml"
SQL_FILE="$ARCHIVE_DIR/admin_demo_data.sql"
CHECK_SQL_FILE="$ARCHIVE_DIR/check_admin_demo_data.sql"
RESET_SQL_FILE="$ARCHIVE_DIR/reset_admin_demo_data.sql"
MODE="auto"
RUN_CHECK=1
CHECK_ONLY=0
CONFIRMED=0
INFRA_COMPOSE_PROJECT_NAME_EFFECTIVE=""

usage() {
  cat <<'EOF'
用法：
  bash scripts/db/archive/mysql_admin_demo/import_admin_demo_data.sh --yes

说明：
  - 该脚本已归档，仅供追溯历史 MySQL 演示数据基线时手工使用
  - 默认从 infra/docker/.env.prod 读取历史 MySQL 连接配置
  - 默认优先使用宿主机 mysql 客户端；若不可用，则回退到 docker compose exec mysql
  - 默认导入后自动执行 scripts/db/archive/mysql_admin_demo/check_admin_demo_data.sql 做校验

可选参数：
  --env-file <path>       指定基础设施环境变量文件
  --compose-file <path>   指定基础设施 compose 文件
  --sql-file <path>       指定要导入的 SQL 文件
  --mysql                 强制使用宿主机 mysql 客户端
  --compose               强制使用 docker compose exec mysql
  --check-only            不导入，只执行归档目录内的 check_admin_demo_data.sql
  --skip-check            导入后跳过校验
  --yes                   确认执行导入（导入是破坏性重置）
  -h, --help              查看帮助
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file)
      ENV_FILE="$(cd "$(dirname "$2")" && pwd)/$(basename "$2")"
      shift 2
      ;;
    --compose-file)
      COMPOSE_FILE="$(cd "$(dirname "$2")" && pwd)/$(basename "$2")"
      shift 2
      ;;
    --sql-file)
      SQL_FILE="$(cd "$(dirname "$2")" && pwd)/$(basename "$2")"
      shift 2
      ;;
    --mysql)
      MODE="mysql"
      shift
      ;;
    --compose)
      MODE="compose"
      shift
      ;;
    --check-only)
      CHECK_ONLY=1
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
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "未知参数：$1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

require_file() {
  local path="$1"
  local label="$2"
  if [[ ! -f "$path" ]]; then
    echo "[ERROR] $label 不存在：$path" >&2
    exit 1
  fi
}

require_file "$ENV_FILE" "环境变量文件"
require_file "$COMPOSE_FILE" "Compose 文件"
require_file "$SQL_FILE" "导入 SQL"
require_file "$CHECK_SQL_FILE" "校验 SQL"
require_file "$RESET_SQL_FILE" "基础演示数据 SQL"

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

INFRA_COMPOSE_PROJECT_NAME_EFFECTIVE="${INFRA_COMPOSE_PROJECT_NAME:-bishe_prod_infra}"

MYSQL_DB_NAME="${MYSQL_DATABASE:-bishe}"
MYSQL_DB_USER="${MYSQL_USER:-root}"
MYSQL_DB_PASSWORD="${MYSQL_PASSWORD:-${MYSQL_ROOT_PASSWORD:-}}"
MYSQL_PORT_BIND_VALUE="${MYSQL_PORT_BIND:-127.0.0.1:3306}"

if [[ "$MYSQL_PORT_BIND_VALUE" == *:* ]]; then
  MYSQL_DB_HOST="${MYSQL_PORT_BIND_VALUE%:*}"
  MYSQL_DB_PORT="${MYSQL_PORT_BIND_VALUE##*:}"
else
  MYSQL_DB_HOST="127.0.0.1"
  MYSQL_DB_PORT="$MYSQL_PORT_BIND_VALUE"
fi

if [[ -z "$MYSQL_DB_PASSWORD" ]]; then
  echo "[ERROR] 未能从 $ENV_FILE 解析到 MYSQL_PASSWORD 或 MYSQL_ROOT_PASSWORD" >&2
  exit 1
fi

run_sql_via_mysql() {
  local sql_path="$1"
  (
    cd "$ROOT_DIR"
    MYSQL_PWD="$MYSQL_DB_PASSWORD" mysql \
      --default-character-set=utf8mb4 \
      --protocol=TCP \
      -h "$MYSQL_DB_HOST" \
      -P "$MYSQL_DB_PORT" \
      -u "$MYSQL_DB_USER" \
      "$MYSQL_DB_NAME" < "$sql_path"
  )
}

run_sql_via_compose() {
  local sql_path="$1"
  (
    cd "$ROOT_DIR"
    docker compose -p "$INFRA_COMPOSE_PROJECT_NAME_EFFECTIVE" \
      --env-file "$ENV_FILE" \
      -f "$COMPOSE_FILE" \
      exec -T \
      -e IMPORT_DB_USER="$MYSQL_DB_USER" \
      -e IMPORT_DB_PASSWORD="$MYSQL_DB_PASSWORD" \
      -e IMPORT_DB_NAME="$MYSQL_DB_NAME" \
      mysql \
      sh -lc 'export MYSQL_PWD="$IMPORT_DB_PASSWORD"; mysql --default-character-set=utf8mb4 -u"$IMPORT_DB_USER" "$IMPORT_DB_NAME"' < "$sql_path"
  )
}

run_sql() {
  local sql_path="$1"
  local effective_sql_path="$sql_path"
  local sql_basename

  sql_basename="$(basename "$sql_path")"
  if [[ "$sql_basename" == "admin_demo_data.sql" && "$MODE" == "compose" ]]; then
    effective_sql_path="$RESET_SQL_FILE"
  fi

  case "$MODE" in
    mysql)
      run_sql_via_mysql "$effective_sql_path"
      ;;
    compose)
      run_sql_via_compose "$effective_sql_path"
      ;;
    auto)
      if command -v mysql >/dev/null 2>&1; then
        run_sql_via_mysql "$effective_sql_path"
      else
        run_sql_via_compose "$effective_sql_path"
      fi
      ;;
    *)
      echo "[ERROR] 不支持的执行模式：$MODE" >&2
      exit 1
      ;;
  esac
}

if [[ "$CHECK_ONLY" -eq 0 && "$CONFIRMED" -ne 1 ]]; then
  cat >&2 <<EOF
[ERROR] 导入管理员演示数据会重置大部分业务表，属于破坏性操作。
请确认当前数据库只用于演示 / 测试，然后重新执行：

  bash scripts/db/archive/mysql_admin_demo/import_admin_demo_data.sh --yes
EOF
  exit 1
fi

echo "[INFO] Root dir: $ROOT_DIR"
echo "[INFO] Env file: $ENV_FILE"
echo "[INFO] SQL file: $SQL_FILE"
echo "[INFO] DB target: $MYSQL_DB_USER@$MYSQL_DB_HOST:$MYSQL_DB_PORT/$MYSQL_DB_NAME"
echo "[INFO] Mode: $MODE"
if [[ "$(basename "$SQL_FILE")" == "admin_demo_data.sql" && "$MODE" == "compose" ]]; then
  echo "[INFO] Compose 模式下将直接执行基础数据文件：$RESET_SQL_FILE"
fi

if [[ "$CHECK_ONLY" -eq 1 ]]; then
  echo "[INFO] 仅执行数据校验"
  run_sql "$CHECK_SQL_FILE"
  exit 0
fi

echo "[INFO] 开始导入管理员演示数据"
run_sql "$SQL_FILE"
echo "[INFO] 管理员演示数据导入完成"

if [[ "$RUN_CHECK" -eq 1 ]]; then
  echo "[INFO] 开始执行导入后校验"
  run_sql "$CHECK_SQL_FILE"
fi

echo "[DONE] 可开始复测 /api/v1/admin/dashboard/workbench 与 /api/v1/admin/consult/after-sales/requests"
