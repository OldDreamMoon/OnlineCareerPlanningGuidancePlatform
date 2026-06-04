#!/usr/bin/env bash
set -euo pipefail

check_required_cmd() {
  local cmd="$1"
  local label="$2"
  local use_mise="${3:-0}"
  if command -v "$cmd" >/dev/null 2>&1; then
    local ver
    if [ "$use_mise" = "1" ] && command -v mise >/dev/null 2>&1 && [ -f "$ROOT/mise.toml" ]; then
      ver="$(mise exec -- "$cmd" --version 2>/dev/null | head -n 1 || true)"
    else
      ver="$($cmd --version 2>/dev/null | head -n 1 || true)"
    fi
    echo "[就绪] ${label}: ${ver:-已安装}"
  else
    echo "[缺失] ${label}: 未找到（必需）"
    MISSING_REQUIRED=$((MISSING_REQUIRED + 1))
  fi
}

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MISSING_REQUIRED=0
MISSING_OPTIONAL=0

echo "== 环境预检 =="
check_required_cmd git "Git"
check_required_cmd node "Node.js" 1
check_required_cmd npm "npm" 1
check_required_cmd java "Java" 1
check_required_cmd mvn "Maven" 1
check_required_cmd docker "Docker"
check_required_cmd curl "curl"

if command -v java >/dev/null 2>&1; then
  if command -v mise >/dev/null 2>&1 && [ -f "$ROOT/mise.toml" ]; then
    JAVA_MAJOR="$(mise exec -- java -version 2>&1 | awk -F'[\".]' '/version/ {print $2; exit}')"
  else
    JAVA_MAJOR="$(java -version 2>&1 | awk -F'[\".]' '/version/ {print $2; exit}')"
  fi
  if [ "${JAVA_MAJOR:-0}" -lt 21 ]; then
    echo "[警告] Java: 当前版本为 ${JAVA_MAJOR:-未知}，后端 pom.xml 要求 Java 21；请确认 mise.toml 是否已生效。"
    MISSING_OPTIONAL=$((MISSING_OPTIONAL + 1))
  fi
fi

if command -v docker >/dev/null 2>&1; then
  if docker compose version >/dev/null 2>&1; then
    echo "[就绪] Docker Compose: $(docker compose version | head -n 1)"
  elif command -v docker-compose >/dev/null 2>&1; then
    echo "[就绪] Docker Compose: $(docker-compose --version | head -n 1)"
  else
    echo "[缺失] Docker Compose: 未找到（必需）"
    MISSING_REQUIRED=$((MISSING_REQUIRED + 1))
  fi
fi

echo ""
if [ "$MISSING_REQUIRED" -eq 0 ]; then
  echo "[完成] 环境预检通过，所有必需工具均已就绪。"
else
  echo "[警告] 环境预检完成，但仍缺少 ${MISSING_REQUIRED} 个必需工具。"
  echo "请先安装缺失工具，再进入后续安装或启动阶段。"
fi

if [ "$MISSING_OPTIONAL" -gt 0 ]; then
  echo "[提示] 存在 ${MISSING_OPTIONAL} 个版本或配置提示，请按项目 mise.toml 与 AGENTS.md 修正后再执行重型构建。"
fi
