#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="$ROOT_DIR/infra/docker/.env.prod"
COMPOSE_FILE="$ROOT_DIR/infra/docker/docker-compose.infra.prod.yml"
SEED_FILE="$ROOT_DIR/scripts/db/seed_defense_demo_data_20260517.sql"
MODE="auto"
COMMIT_SEED=0
CONFIRMED=0
RUN_CHECK=1
DB_NAME_OVERRIDE=""
EFFECTIVE_MODE=""
POSTGRES_DB_HOST=""
POSTGRES_DB_PORT=""
INFRA_COMPOSE_PROJECT_NAME_EFFECTIVE=""

usage() {
  cat <<'EOF'
用法：
  bash scripts/db/apply_defense_demo_seed_prod.sh [--dry-run]
  bash scripts/db/apply_defense_demo_seed_prod.sh --commit --yes

说明：
  - 这是服务器侧答辩演示 seed 数据执行脚本，默认读取 infra/docker/.env.prod。
  - 默认 dry-run：会执行 seed SQL 内部完整校验和写入流程，但事务末尾 ROLLBACK。
  - 真正写入必须同时传入 --commit --yes；脚本会向 seed SQL 传入 -v demo_commit=1。
  - seed SQL 只清理并重建 DEFENSE-20260517 / [答辩演示] / defense-20260517 前缀演示数据，
    不新增或修改 users、student_profiles、mentor_profiles、enterprise_profiles 等稳定主数据。
  - 默认 auto 模式优先使用宿主机 psql；若服务器未安装 psql，则使用 docker compose exec postgres。

可选参数：
  --dry-run               只做回滚演练（默认）
  --commit                真正提交 DEFENSE-20260517 演示数据
  --yes                   与 --commit 配合，确认执行写库
  --env-file <path>       指定生产环境变量文件，默认 infra/docker/.env.prod
  --compose-file <path>   指定基础设施 compose 文件，默认 infra/docker/docker-compose.infra.prod.yml
  --seed-file <path>      指定 seed SQL 文件，默认 scripts/db/seed_defense_demo_data_20260517.sql
  --db-name <name>        覆盖目标数据库名，默认读取 POSTGRES_DB 或 bishe
  --psql                  强制使用宿主机 psql
  --compose               强制使用 docker compose exec postgres
  --skip-check            写入后跳过摘要校验
  -h, --help              查看帮助

推荐流程：
  1. 先执行 dry-run：
       bash scripts/db/apply_defense_demo_seed_prod.sh
  2. 核对输出摘要和缺失账号检查均正常后，再执行提交：
       bash scripts/db/apply_defense_demo_seed_prod.sh --commit --yes
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

docker_compose_infra() {
  (
    cd "$ROOT_DIR"
    docker compose -p "$INFRA_COMPOSE_PROJECT_NAME_EFFECTIVE" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
  )
}

run_seed_via_psql() {
  (
    cd "$ROOT_DIR"
    PGPASSWORD="$POSTGRES_APP_PASSWORD" psql \
      --set ON_ERROR_STOP=1 \
      -v "demo_commit=$COMMIT_SEED" \
      -h "$POSTGRES_DB_HOST" \
      -p "$POSTGRES_DB_PORT" \
      -U "$POSTGRES_APP_USER" \
      -d "$POSTGRES_DB_NAME" \
      -f "$SEED_FILE"
  )
}

run_seed_via_compose() {
  docker_compose_infra \
    exec -T \
    -e IMPORT_DB_USER="$POSTGRES_APP_USER" \
    -e IMPORT_DB_PASSWORD="$POSTGRES_APP_PASSWORD" \
    -e IMPORT_DB_NAME="$POSTGRES_DB_NAME" \
    -e DEMO_COMMIT="$COMMIT_SEED" \
    postgres \
    sh -lc '
      export PGPASSWORD="$IMPORT_DB_PASSWORD"
      psql --set ON_ERROR_STOP=1 -v "demo_commit=$DEMO_COMMIT" -U "$IMPORT_DB_USER" -d "$IMPORT_DB_NAME"
    ' < "$SEED_FILE"
}

run_query_output_via_psql() {
  local sql_text="$1"
  (
    cd "$ROOT_DIR"
    PGPASSWORD="$POSTGRES_APP_PASSWORD" psql \
      --set ON_ERROR_STOP=1 \
      -h "$POSTGRES_DB_HOST" \
      -p "$POSTGRES_DB_PORT" \
      -U "$POSTGRES_APP_USER" \
      -d "$POSTGRES_DB_NAME" \
      -At <<<"$sql_text"
  )
}

run_query_output_via_compose() {
  local sql_text="$1"
  printf '%s\n' "$sql_text" | docker_compose_infra \
    exec -T \
    -e IMPORT_DB_USER="$POSTGRES_APP_USER" \
    -e IMPORT_DB_PASSWORD="$POSTGRES_APP_PASSWORD" \
    -e IMPORT_DB_NAME="$POSTGRES_DB_NAME" \
    postgres \
    sh -lc '
      export PGPASSWORD="$IMPORT_DB_PASSWORD"
      psql --set ON_ERROR_STOP=1 -U "$IMPORT_DB_USER" -d "$IMPORT_DB_NAME" -At
    '
}

run_query_output() {
  local sql_text="$1"
  case "$EFFECTIVE_MODE" in
    psql)
      run_query_output_via_psql "$sql_text"
      ;;
    compose)
      run_query_output_via_compose "$sql_text"
      ;;
    *)
      echo "[错误] 未知执行模式：$EFFECTIVE_MODE" >&2
      exit 1
      ;;
  esac
}

run_seed() {
  case "$EFFECTIVE_MODE" in
    psql)
      run_seed_via_psql
      ;;
    compose)
      run_seed_via_compose
      ;;
    *)
      echo "[错误] 未知执行模式：$EFFECTIVE_MODE" >&2
      exit 1
      ;;
  esac
}

run_post_commit_checks() {
  echo "[信息] 写入后摘要校验："
  run_query_output "
SELECT 'consult_orders=' || count(*) FROM consult_orders WHERE order_no LIKE 'CONS-DEF-20260517-%'
UNION ALL
SELECT 'payment_records=' || count(*) FROM payment_records WHERE order_no LIKE 'CONS-DEF-20260517-%'
UNION ALL
SELECT 'bounty_tasks=' || count(*) FROM bounty_tasks WHERE title LIKE '[答辩演示]%'
UNION ALL
SELECT 'ai_async_task_jobs=' || count(*) FROM ai_async_task_jobs WHERE task_id LIKE 'aitk_defense_20260517_%'
UNION ALL
SELECT 'interview_sessions=' || count(*) FROM interview_sessions WHERE session_id LIKE 'ivs_def_20260517_%'
UNION ALL
SELECT 'posts=' || count(*) FROM posts WHERE title LIKE '[答辩演示]%'
UNION ALL
SELECT 'notifications=' || count(*) FROM notifications WHERE event_id LIKE 'defense-20260517-%'
ORDER BY 1;
"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run)
      COMMIT_SEED=0
      shift
      ;;
    --commit)
      COMMIT_SEED=1
      shift
      ;;
    --yes)
      CONFIRMED=1
      shift
      ;;
    --env-file)
      ENV_FILE="$(resolve_existing_path "$2")"
      shift 2
      ;;
    --compose-file)
      COMPOSE_FILE="$(resolve_existing_path "$2")"
      shift 2
      ;;
    --seed-file)
      SEED_FILE="$(resolve_existing_path "$2")"
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
    --skip-check)
      RUN_CHECK=0
      shift
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

require_file "$ENV_FILE" "生产环境变量文件"
require_file "$COMPOSE_FILE" "基础设施 Compose 文件"
require_file "$SEED_FILE" "答辩演示 seed SQL 文件"

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

INFRA_COMPOSE_PROJECT_NAME_EFFECTIVE="${INFRA_COMPOSE_PROJECT_NAME:-bishe_prod_infra}"
POSTGRES_DB_NAME="${DB_NAME_OVERRIDE:-${POSTGRES_DB:-bishe}}"
POSTGRES_PORT_BIND_VALUE="${POSTGRES_PORT_BIND:-127.0.0.1:5432}"
POSTGRES_APP_USER="${POSTGRES_USER:-bishe}"
POSTGRES_APP_PASSWORD="${POSTGRES_PASSWORD:-}"

if [[ -z "$POSTGRES_APP_PASSWORD" ]]; then
  echo "[错误] 未能从 $ENV_FILE 解析到 POSTGRES_PASSWORD" >&2
  exit 1
fi

validate_identifier "POSTGRES_DB" "$POSTGRES_DB_NAME"
validate_identifier "POSTGRES_USER" "$POSTGRES_APP_USER"
parse_postgres_bind "$POSTGRES_PORT_BIND_VALUE"

case "$MODE" in
  auto)
    if command -v psql >/dev/null 2>&1; then
      EFFECTIVE_MODE="psql"
    elif command -v docker >/dev/null 2>&1; then
      EFFECTIVE_MODE="compose"
    else
      echo "[错误] auto 模式下未找到可用的 psql 或 docker compose" >&2
      exit 1
    fi
    ;;
  psql)
    EFFECTIVE_MODE="psql"
    require_command psql "psql 客户端"
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
else
  require_command docker "docker"
fi

if [[ "$COMMIT_SEED" -eq 1 && "$CONFIRMED" -ne 1 ]]; then
  cat >&2 <<EOF
[错误] 当前命令会向生产 PostgreSQL 写入 DEFENSE-20260517 答辩演示数据，必须显式确认。

保守路径：先执行 dry-run，确认 seed SQL 的依赖账号检查和摘要输出正常。
平衡路径：执行 --commit --yes，脚本只会清理并重建 DEFENSE-20260517 / [答辩演示] 前缀数据。
回滚口径：重复执行本脚本会先清理同前缀演示数据再重建；如需完全移除，可按 seed SQL 开头的前缀清理范围定向删除。

确认后执行：
  bash scripts/db/apply_defense_demo_seed_prod.sh --commit --yes
EOF
  exit 1
fi

echo "[信息] 项目根目录：$ROOT_DIR"
echo "[信息] 环境文件：$ENV_FILE"
echo "[信息] Compose 文件：$COMPOSE_FILE"
echo "[信息] seed 文件：$SEED_FILE"
echo "[信息] 目标数据库：$POSTGRES_APP_USER@$POSTGRES_DB_HOST:$POSTGRES_DB_PORT/$POSTGRES_DB_NAME"
echo "[信息] 执行模式：$MODE -> $EFFECTIVE_MODE"

if [[ "$COMMIT_SEED" -eq 1 ]]; then
  echo "[警告] 当前为 COMMIT 模式，将真正写入 DEFENSE-20260517 答辩演示数据。"
else
  echo "[信息] 当前为 dry-run 模式，seed SQL 会在事务末尾 ROLLBACK。"
fi

run_seed

if [[ "$COMMIT_SEED" -eq 1 && "$RUN_CHECK" -eq 1 ]]; then
  run_post_commit_checks
fi

if [[ "$COMMIT_SEED" -eq 1 ]]; then
  echo "[完成] DEFENSE-20260517 答辩演示 seed 数据已提交。"
else
  echo "[完成] DEFENSE-20260517 答辩演示 seed dry-run 已完成，数据库未落入演示数据。"
fi
