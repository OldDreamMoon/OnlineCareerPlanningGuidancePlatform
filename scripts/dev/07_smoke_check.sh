#!/usr/bin/env bash
set -euo pipefail

check_http() {
  local name="$1"
  local url="$2"
  if curl -fsS --max-time 5 "$url" >/dev/null; then
    echo "[通过] ${name}：${url}"
  else
    echo "[失败] ${name}：${url}" >&2
    return 1
  fi
}

echo "== 冒烟检查 =="

check_http "前端页面" "http://127.0.0.1:5173"
check_http "后端健康接口" "http://127.0.0.1:8080/api/v1/health"

echo "[完成] 冒烟检查通过。"
