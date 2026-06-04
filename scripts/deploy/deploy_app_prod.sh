#!/usr/bin/env bash
set -euo pipefail

DEPLOY_ROOT="${DEPLOY_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
DEPLOY_BRANCH="${DEPLOY_BRANCH:-main}"
PROD_ENV_FILE="${PROD_ENV_FILE:-${APP_ENV_FILE:-$DEPLOY_ROOT/infra/docker/.env.prod}}"
APP_COMPOSE_FILE="${APP_COMPOSE_FILE:-$DEPLOY_ROOT/infra/docker/docker-compose.app.prod.yml}"
NETWORK_NAME="${NETWORK_NAME:-bishe_prod}"
GHCR_REGISTRY="${GHCR_REGISTRY:-ghcr.io}"
APP_IMAGE_TAG_OVERRIDE="${APP_IMAGE_TAG:-}"
GHCR_NAMESPACE_OVERRIDE="${GHCR_NAMESPACE:-}"
APP_IMAGE_TAG="${APP_IMAGE_TAG:-latest}"
DEPLOY_MODE="deploy"

usage() {
  cat <<'EOF'
用法：
  bash scripts/deploy/deploy_app_prod.sh [--deploy | --prepare-only | --start-only]

说明：
  - 默认模式或 --deploy：同步代码、登录 GHCR、拉取镜像并直接启动应用层
  - --prepare-only：同步代码、登录 GHCR、拉取镜像并创建应用层容器，但不启动
  - --start-only：基于当前 infra/docker/.env.prod 直接启动应用层；适合在已 prepare 且数据导入完成后手工放行
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --deploy)
      DEPLOY_MODE="deploy"
      ;;
    --prepare-only)
      DEPLOY_MODE="prepare"
      ;;
    --start-only)
      DEPLOY_MODE="start"
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "[错误] 未知参数：$1" >&2
      usage
      exit 1
      ;;
  esac
  shift
done

upsert_env() {
  local key="$1"
  local value="$2"
  local file="$3"

  if grep -q "^${key}=" "$file"; then
    sed -i "s|^${key}=.*|${key}=${value}|" "$file"
  else
    printf '%s=%s\n' "$key" "$value" >> "$file"
  fi
}

if [ ! -d "$DEPLOY_ROOT/.git" ]; then
  echo "[错误] DEPLOY_ROOT 不是 Git 工作区：$DEPLOY_ROOT" >&2
  exit 1
fi

if [ ! -f "$PROD_ENV_FILE" ]; then
  echo "[错误] 缺少生产环境文件：$PROD_ENV_FILE" >&2
  echo "请先将 infra/docker/.env.prod.example 复制为 infra/docker/.env.prod，并填入真实配置。" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$PROD_ENV_FILE"
set +a

echo "[环境] 应用部署读取：$PROD_ENV_FILE"
echo "[环境] 说明：--env-file 用于 Compose 变量替换；后端容器实际可见变量由 infra/docker/docker-compose.app.prod.yml 的 server.environment 白名单注入。"

APP_IMAGE_TAG="${APP_IMAGE_TAG_OVERRIDE:-${APP_IMAGE_TAG:-latest}}"
if [ -n "$GHCR_NAMESPACE_OVERRIDE" ]; then
  GHCR_NAMESPACE="$GHCR_NAMESPACE_OVERRIDE"
fi

APP_COMPOSE_PROJECT_NAME_EFFECTIVE="${APP_COMPOSE_PROJECT_NAME:-bishe_prod_app}"

if [ "$DEPLOY_MODE" != "start" ]; then
  git -C "$DEPLOY_ROOT" fetch origin "$DEPLOY_BRANCH"
  if git -C "$DEPLOY_ROOT" show-ref --verify --quiet "refs/heads/$DEPLOY_BRANCH"; then
    git -C "$DEPLOY_ROOT" checkout "$DEPLOY_BRANCH"
  else
    git -C "$DEPLOY_ROOT" checkout -B "$DEPLOY_BRANCH" "origin/$DEPLOY_BRANCH"
  fi
  git -C "$DEPLOY_ROOT" reset --hard "origin/$DEPLOY_BRANCH"
fi

if [ "$DEPLOY_MODE" != "start" ] && [ -n "${GHCR_USERNAME:-}" ] && [ -n "${GHCR_TOKEN:-}" ]; then
  printf '%s' "$GHCR_TOKEN" | docker login "$GHCR_REGISTRY" -u "$GHCR_USERNAME" --password-stdin >/dev/null
fi

docker network inspect "$NETWORK_NAME" >/dev/null 2>&1 || docker network create "$NETWORK_NAME" >/dev/null

upsert_env "APP_IMAGE_TAG" "$APP_IMAGE_TAG" "$PROD_ENV_FILE"

if [ -n "${GHCR_NAMESPACE:-}" ]; then
  upsert_env "GHCR_NAMESPACE" "$GHCR_NAMESPACE" "$PROD_ENV_FILE"
fi

case "$DEPLOY_MODE" in
  prepare)
    docker compose -p "$APP_COMPOSE_PROJECT_NAME_EFFECTIVE" --env-file "$PROD_ENV_FILE" -f "$APP_COMPOSE_FILE" pull
    docker compose -p "$APP_COMPOSE_PROJECT_NAME_EFFECTIVE" --env-file "$PROD_ENV_FILE" -f "$APP_COMPOSE_FILE" up --no-start --force-recreate --remove-orphans
    docker image prune -f >/dev/null || true
    echo "[完成] 应用层已完成 prepare，APP_IMAGE_TAG=$APP_IMAGE_TAG（容器已创建但未启动）"
    docker compose -p "$APP_COMPOSE_PROJECT_NAME_EFFECTIVE" --env-file "$PROD_ENV_FILE" -f "$APP_COMPOSE_FILE" ps -a
    echo "[下一步] 待基础设施与 PostgreSQL 数据就绪后，请执行：bash scripts/deploy/deploy_app_prod.sh --start-only"
    ;;
  start)
    docker compose -p "$APP_COMPOSE_PROJECT_NAME_EFFECTIVE" --env-file "$PROD_ENV_FILE" -f "$APP_COMPOSE_FILE" up -d --remove-orphans
    echo "[完成] 应用层已启动，APP_IMAGE_TAG=${APP_IMAGE_TAG:-$(grep '^APP_IMAGE_TAG=' "$PROD_ENV_FILE" | cut -d= -f2-)}"
    docker compose -p "$APP_COMPOSE_PROJECT_NAME_EFFECTIVE" --env-file "$PROD_ENV_FILE" -f "$APP_COMPOSE_FILE" ps
    ;;
  deploy)
    docker compose -p "$APP_COMPOSE_PROJECT_NAME_EFFECTIVE" --env-file "$PROD_ENV_FILE" -f "$APP_COMPOSE_FILE" pull
    docker compose -p "$APP_COMPOSE_PROJECT_NAME_EFFECTIVE" --env-file "$PROD_ENV_FILE" -f "$APP_COMPOSE_FILE" up -d --remove-orphans
    docker image prune -f >/dev/null || true
    echo "[完成] 应用层已部署，APP_IMAGE_TAG=$APP_IMAGE_TAG"
    docker compose -p "$APP_COMPOSE_PROJECT_NAME_EFFECTIVE" --env-file "$PROD_ENV_FILE" -f "$APP_COMPOSE_FILE" ps
    ;;
  *)
    echo "[错误] 未知部署模式：$DEPLOY_MODE" >&2
    exit 1
    ;;
esac
