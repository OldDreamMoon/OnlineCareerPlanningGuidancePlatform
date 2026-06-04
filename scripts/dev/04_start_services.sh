#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="$ROOT/infra/docker/docker-compose.dev.yml"
POSTGRES_CONTAINER_NAME="bishe_postgres"
POSTGRES_APP_DB="bishe"
POSTGRES_APP_USER="bishe"

ensure_postgres_app_database() {
  if [ ! -f "$POSTGRES_COMPOSE_FILE" ]; then
    return
  fi

  if ! docker ps --format '{{.Names}}' | grep -qx "$POSTGRES_CONTAINER_NAME"; then
    echo "[警告] PostgreSQL 容器未运行，跳过应用数据库检查：$POSTGRES_CONTAINER_NAME" >&2
    return
  fi

  local db_exists
  db_exists="$(docker exec "$POSTGRES_CONTAINER_NAME" psql -U "$POSTGRES_APP_USER" -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$POSTGRES_APP_DB'" 2>/dev/null || true)"
  if [ "$db_exists" = "1" ]; then
    echo "[就绪] PostgreSQL 应用库已存在：$POSTGRES_APP_DB"
    return
  fi

  echo "[初始化] 创建 PostgreSQL 应用库：$POSTGRES_APP_DB"
  docker exec "$POSTGRES_CONTAINER_NAME" psql -U "$POSTGRES_APP_USER" -d postgres -c "CREATE DATABASE $POSTGRES_APP_DB"
}

if [ ! -f "$COMPOSE_FILE" ]; then
  echo "[错误] 缺少 Compose 文件：$COMPOSE_FILE" >&2
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "[错误] 未找到 Docker" >&2
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "[错误] 无法访问 Docker daemon，可能是权限或服务未启动。" >&2
  echo "可尝试执行：" >&2
  echo "  sudo systemctl enable --now docker" >&2
  echo "  sudo usermod -aG docker \"$USER\"" >&2
  echo "  newgrp docker    # 或重新登录终端后再试" >&2
  exit 1
fi

if docker compose version >/dev/null 2>&1; then
  docker compose -f "$COMPOSE_FILE" up -d
elif command -v docker-compose >/dev/null 2>&1; then
  docker-compose -f "$COMPOSE_FILE" up -d
else
  echo "[错误] 未找到 Docker Compose（docker compose / docker-compose）" >&2
  exit 1
fi

ensure_postgres_app_database

echo "[完成] 基础服务已启动（redis / minio / postgres）。"
