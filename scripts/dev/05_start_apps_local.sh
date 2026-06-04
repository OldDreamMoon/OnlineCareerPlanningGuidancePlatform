#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WEB_CACHE_DIR="${NPM_CONFIG_CACHE:-${npm_config_cache:-$HOME/.cache/dev/npm}}"
M2_CACHE_DIR="${MAVEN_REPO_LOCAL:-$HOME/.cache/dev/maven/repository}"
PID_DIR="$ROOT/.cache/pids"
POSTGRES_DB_URL_DEFAULT="jdbc:postgresql://127.0.0.1:5432/bishe"
SERVER_DB_TARGET_OVERRIDE=""
MISE_RUN=""

if command -v mise >/dev/null 2>&1 && [ -f "$ROOT/mise.toml" ]; then
  MISE_RUN="mise exec --"
fi

load_env_file() {
  local env_file="$1"
  if [ -f "$env_file" ]; then
    echo "[环境] 加载 ${env_file#$ROOT/}"
    set -a
    # shellcheck source=/dev/null
    . "$env_file"
    set +a
  fi
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --postgres)
      SERVER_DB_TARGET_OVERRIDE="postgres"
      ;;
    --mysql)
      echo "[错误] MySQL runtime 已不再受支持；当前本地一键启动基线已经切到 PostgreSQL。" >&2
      echo "如需查看历史 MySQL 基线，请使用历史 Git checkpoint 或数据库备份，而不是通过本脚本启动应用。" >&2
      exit 1
      ;;
    *)
      echo "[错误] 不支持的参数：$1" >&2
      echo "用法：bash scripts/dev/05_start_apps_local.sh [--postgres]" >&2
      exit 1
      ;;
  esac
  shift
done

echo "== 启动本地应用 =="
echo "该脚本会使用 nohup 在后台启动前后端。"
mkdir -p "$PID_DIR"

echo "[准备] 先停止已存在的本地应用进程..."
bash "$ROOT/scripts/dev/06_stop_apps_local.sh"

load_env_file "$ROOT/.env"

SERVER_DB_TARGET="${SERVER_DB_TARGET_OVERRIDE:-${SERVER_DB_TARGET:-postgres}}"
JAVA_SPRING_PROFILES_ACTIVE="postgres"
JAVA_DB_URL="${DB_URL:-}"
JAVA_DB_USERNAME="${DB_USERNAME:-bishe}"
JAVA_DB_PASSWORD="${DB_PASSWORD:-bishe}"

case "$SERVER_DB_TARGET" in
  postgres)
    # PostgreSQL 已成为本地默认运行目标，显式覆盖旧 `.env` 中残留的 MySQL profile/URL。
    if [ -z "$JAVA_DB_URL" ] || [[ "$JAVA_DB_URL" == jdbc:mysql:* ]]; then
      JAVA_DB_URL="$POSTGRES_DB_URL_DEFAULT"
    fi
    ;;
  mysql)
    echo "[错误] MySQL runtime 已不再受支持；当前默认运行链已切换到 PostgreSQL。" >&2
    echo "请清理旧的 SERVER_DB_TARGET=mysql / DB_URL=jdbc:mysql:* 配置后重试。" >&2
    exit 1
    ;;
  *)
    echo "[错误] 不支持的 SERVER_DB_TARGET：$SERVER_DB_TARGET" >&2
    echo "当前仅支持：postgres" >&2
    exit 1
    ;;
esac

# web
if [ -f "$ROOT/apps/web/package.json" ]; then
  echo "[前端] 如有需要将自动安装依赖..."
  mkdir -p "$WEB_CACHE_DIR"
  (cd "$ROOT/apps/web" && npm_config_cache="$WEB_CACHE_DIR" $MISE_RUN npm install)
  echo "[前端] 正在启动，监听端口 :5173"
  nohup bash -c "cd '$ROOT/apps/web' && npm_config_cache='$WEB_CACHE_DIR' $MISE_RUN npm run dev" > "$ROOT/apps/web/dev.log" 2>&1 &
  echo $! > "$PID_DIR/web.pid"
else
  echo "[前端] 跳过启动（未找到 package.json）"
fi

# java
if [ -f "$ROOT/apps/server-java/pom.xml" ]; then
  echo "[后端] 正在启动，监听端口 :${SERVER_PORT:-8080}（db=${SERVER_DB_TARGET}, profiles=${JAVA_SPRING_PROFILES_ACTIVE:-default}）"
  echo "[后端] 数据源地址：$JAVA_DB_URL"
  mkdir -p "$M2_CACHE_DIR"
  nohup env \
    SPRING_PROFILES_ACTIVE="$JAVA_SPRING_PROFILES_ACTIVE" \
    DB_URL="$JAVA_DB_URL" \
    DB_USERNAME="$JAVA_DB_USERNAME" \
    DB_PASSWORD="$JAVA_DB_PASSWORD" \
    bash -c "cd '$ROOT/apps/server-java' && $MISE_RUN mvn -Dmaven.repo.local='$M2_CACHE_DIR' -DskipTests spring-boot:run" > "$ROOT/apps/server-java/dev.log" 2>&1 &
  echo $! > "$PID_DIR/server-java.pid"
else
  echo "[后端] 跳过启动（未找到 pom.xml）"
fi

echo "[完成] 已发出本地应用启动命令。"
echo "日志文件："
echo "- apps/web/dev.log"
echo "- apps/server-java/dev.log"
echo "健康检查："
echo "- http://127.0.0.1:${SERVER_PORT:-8080}/actuator/health"
