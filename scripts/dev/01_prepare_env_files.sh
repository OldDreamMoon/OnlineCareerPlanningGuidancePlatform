#!/usr/bin/env bash
set -euo pipefail

copy_if_missing() {
  local src="$1"
  local dst="$2"
  if [ -f "$dst" ]; then
    echo "[跳过] $dst 已存在"
  else
    cp "$src" "$dst"
    echo "[新建] $dst"
  fi
}

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

copy_if_missing "$ROOT/.env.example" "$ROOT/.env"

mkdir -p \
  "${NPM_CONFIG_CACHE:-${npm_config_cache:-$HOME/.cache/dev/npm}}" \
  "${MAVEN_REPO_LOCAL:-$HOME/.cache/dev/maven/repository}" \
  "${GRADLE_USER_HOME:-$HOME/.cache/dev/gradle}" \
  "${UV_CACHE_DIR:-$HOME/.cache/dev/uv}" \
  "${PIP_CACHE_DIR:-$HOME/.cache/dev/pip}" \
  "$ROOT/.cache/pids" \
  "$ROOT/data/seed" \
  "$ROOT/data/uploads" \
  "$ROOT/tests/e2e" \
  "$ROOT/tests/integration" \
  "$ROOT/plan"

touch \
  "$ROOT/data/seed/.gitkeep" \
  "$ROOT/tests/e2e/.gitkeep" \
  "$ROOT/tests/integration/.gitkeep" \
  "$ROOT/plan/.gitkeep"

if [ ! -d "$ROOT/.agent_tracking" ] && [ -x "$ROOT/.agent_system/scripts/init_tracking_workspace.sh" ]; then
  (cd "$ROOT" && bash .agent_system/scripts/init_tracking_workspace.sh)
fi

echo "[完成] 环境文件与工作区准备完成。"
