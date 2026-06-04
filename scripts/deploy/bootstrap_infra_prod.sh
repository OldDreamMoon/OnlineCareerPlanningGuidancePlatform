#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
INFRA_ENV_FILE="${INFRA_ENV_FILE:-$ROOT/infra/docker/.env.prod}"
INFRA_COMPOSE_FILE="${INFRA_COMPOSE_FILE:-$ROOT/infra/docker/docker-compose.infra.prod.yml}"
NETWORK_NAME="${NETWORK_NAME:-bishe_prod}"

if [ ! -f "$INFRA_ENV_FILE" ]; then
  echo "[错误] 缺少基础设施环境文件：$INFRA_ENV_FILE" >&2
  echo "请先将 infra/docker/.env.prod.example 复制为 infra/docker/.env.prod，并填入真实配置。" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$INFRA_ENV_FILE"
set +a

INFRA_COMPOSE_PROJECT_NAME_EFFECTIVE="${INFRA_COMPOSE_PROJECT_NAME:-bishe_prod_infra}"

docker network inspect "$NETWORK_NAME" >/dev/null 2>&1 || docker network create "$NETWORK_NAME" >/dev/null

docker compose -p "$INFRA_COMPOSE_PROJECT_NAME_EFFECTIVE" --env-file "$INFRA_ENV_FILE" -f "$INFRA_COMPOSE_FILE" up -d

echo "[完成] 基础设施服务已通过 $INFRA_COMPOSE_FILE 启动。"
echo "[下一步] 请确认 infra/docker/.env.prod 已填写完成；默认执行 scripts/deploy/deploy_app_prod.sh 会直接拉取并启动应用层，如只想预拉镜像可改用 --prepare-only。"
