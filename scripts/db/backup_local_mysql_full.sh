#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"
BACKUP_DIR="${BACKUP_DIR:-$ROOT_DIR/.cache/db-backups}"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"

usage() {
  cat <<'EOF'
用法：
  bash scripts/db/backup_local_mysql_full.sh

说明：
  - 默认读取项目根目录 .env 中的 MYSQL_HOST / MYSQL_PORT / MYSQL_DB / MYSQL_USER / MYSQL_PASSWORD
  - 输出目录默认是 .cache/db-backups/
  - 会同时导出：
      1. 仅结构的 schema 备份
      2. 结构 + 数据的完整备份
      3. 表清单与估算行数
      4. 备份摘要 manifest

可选环境变量：
  ENV_FILE=/abs/path/to/.env
  BACKUP_DIR=/abs/path/to/output
  MYSQL_HOST / MYSQL_PORT / MYSQL_DB / MYSQL_USER / MYSQL_PASSWORD
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

require_command() {
  local cmd="$1"
  local label="$2"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "[错误] 缺少 $label：$cmd" >&2
    exit 1
  fi
}

if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_DB="${MYSQL_DB:-bishe}"
MYSQL_USER="${MYSQL_USER:-bishe}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-${DB_PASSWORD:-}}"

if [[ -z "$MYSQL_PASSWORD" ]]; then
  echo "[错误] 未解析到 MYSQL_PASSWORD，请检查 $ENV_FILE 或显式传入环境变量。" >&2
  exit 1
fi

require_command mysql "mysql 客户端"
require_command mysqldump "mysqldump 客户端"
require_command gzip "gzip"

mkdir -p "$BACKUP_DIR"

BASE_NAME="${MYSQL_DB}_local_full_backup_${TIMESTAMP}"
SCHEMA_FILE="$BACKUP_DIR/${BASE_NAME}_schema.sql.gz"
FULL_FILE="$BACKUP_DIR/${BASE_NAME}_full.sql.gz"
TABLE_STATS_FILE="$BACKUP_DIR/${BASE_NAME}_table_stats.tsv"
MANIFEST_FILE="$BACKUP_DIR/${BASE_NAME}_manifest.txt"

MYSQL_COMMON_ARGS=(
  --default-character-set=utf8mb4
  --protocol=TCP
  -h "$MYSQL_HOST"
  -P "$MYSQL_PORT"
  -u "$MYSQL_USER"
)

echo "[信息] 验证数据库连通性：$MYSQL_USER@$MYSQL_HOST:$MYSQL_PORT/$MYSQL_DB"
MYSQL_PWD="$MYSQL_PASSWORD" mysql "${MYSQL_COMMON_ARGS[@]}" -D "$MYSQL_DB" -e "SELECT 1;" >/dev/null

echo "[信息] 导出仅结构备份 -> $SCHEMA_FILE"
MYSQL_PWD="$MYSQL_PASSWORD" mysqldump \
  "${MYSQL_COMMON_ARGS[@]}" \
  --single-transaction \
  --no-tablespaces \
  --routines \
  --triggers \
  --events \
  --no-data \
  --databases "$MYSQL_DB" | gzip -c > "$SCHEMA_FILE"

echo "[信息] 导出完整备份（结构 + 数据）-> $FULL_FILE"
MYSQL_PWD="$MYSQL_PASSWORD" mysqldump \
  "${MYSQL_COMMON_ARGS[@]}" \
  --single-transaction \
  --no-tablespaces \
  --routines \
  --triggers \
  --events \
  --hex-blob \
  --databases "$MYSQL_DB" | gzip -c > "$FULL_FILE"

echo "[信息] 导出表统计 -> $TABLE_STATS_FILE"
MYSQL_PWD="$MYSQL_PASSWORD" mysql "${MYSQL_COMMON_ARGS[@]}" -N -B <<SQL > "$TABLE_STATS_FILE"
SELECT table_name,
       table_type,
       engine,
       COALESCE(table_rows, 0) AS estimated_rows,
       COALESCE(data_length, 0) AS data_length,
       COALESCE(index_length, 0) AS index_length
  FROM information_schema.tables
 WHERE table_schema = '${MYSQL_DB}'
 ORDER BY table_name;
SQL

TABLE_COUNT="$(MYSQL_PWD="$MYSQL_PASSWORD" mysql "${MYSQL_COMMON_ARGS[@]}" -N -B -D "$MYSQL_DB" -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE();")"
DB_SIZE_BYTES="$(MYSQL_PWD="$MYSQL_PASSWORD" mysql "${MYSQL_COMMON_ARGS[@]}" -N -B -e "SELECT COALESCE(SUM(data_length + index_length), 0) FROM information_schema.tables WHERE table_schema = '${MYSQL_DB}';")"

cat > "$MANIFEST_FILE" <<EOF
backup_timestamp_utc=$TIMESTAMP
mysql_host=$MYSQL_HOST
mysql_port=$MYSQL_PORT
mysql_db=$MYSQL_DB
mysql_user=$MYSQL_USER
table_count=$TABLE_COUNT
estimated_db_size_bytes=$DB_SIZE_BYTES
schema_file=$(basename "$SCHEMA_FILE")
full_file=$(basename "$FULL_FILE")
table_stats_file=$(basename "$TABLE_STATS_FILE")
EOF

echo "[完成] 数据库全量备份完成"
echo "[完成] schema 备份：$SCHEMA_FILE"
echo "[完成] full 备份：$FULL_FILE"
echo "[完成] 表统计：$TABLE_STATS_FILE"
echo "[完成] 摘要文件：$MANIFEST_FILE"
