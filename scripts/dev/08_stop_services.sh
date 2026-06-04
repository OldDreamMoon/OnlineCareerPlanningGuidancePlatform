#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="$ROOT/infra/docker/docker-compose.dev.yml"

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
  exit 1
fi

if docker compose version >/dev/null 2>&1; then
  docker compose -f "$COMPOSE_FILE" stop redis minio postgres
elif command -v docker-compose >/dev/null 2>&1; then
  docker-compose -f "$COMPOSE_FILE" stop redis minio postgres
else
  echo "[错误] 未找到 Docker Compose（docker compose / docker-compose）" >&2
  exit 1
fi

echo "[完成] 基础服务已停止（redis / minio / postgres 容器保留未删除）。"
