#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PID_DIR="$ROOT/.cache/pids"

collect_descendants() {
  local parent_pid="$1"
  local child_pid
  for child_pid in $(pgrep -P "$parent_pid" 2>/dev/null || true); do
    echo "$child_pid"
    collect_descendants "$child_pid"
  done
}

terminate_pid_tree() {
  local pid="$1"
  local name="$2"
  if [ -z "$pid" ] || ! kill -0 "$pid" >/dev/null 2>&1; then
    return
  fi

  local all_pids="$pid"
  local child_pid
  while IFS= read -r child_pid; do
    if [ -n "$child_pid" ]; then
      all_pids="$all_pids $child_pid"
    fi
  done < <(collect_descendants "$pid")

  # 先温和终止，防止子进程（vite/java）残留。
  kill -TERM $all_pids >/dev/null 2>&1 || true
  sleep 1
  if kill -0 "$pid" >/dev/null 2>&1; then
    kill -KILL $all_pids >/dev/null 2>&1 || true
  fi
  echo "[停止] ${name}（pids:${all_pids}）"
}

stop_by_pid_file() {
  local name="$1"
  local file="$2"
  if [ ! -f "$file" ]; then
    echo "[跳过] 未找到 ${name} 的 pid 文件"
    return
  fi

  local pid
  pid="$(cat "$file")"
  if [ -n "$pid" ] && kill -0 "$pid" >/dev/null 2>&1; then
    terminate_pid_tree "$pid" "$name"
  else
    echo "[跳过] ${name} 的 pid 文件存在，但进程已不在运行"
  fi
  rm -f "$file"
}

cleanup_by_pattern() {
  local name="$1"
  local pattern="$2"
  local pids
  pids="$(pgrep -f "$pattern" || true)"
  if [ -z "$pids" ]; then
    return
  fi

  kill -TERM $pids >/dev/null 2>&1 || true
  sleep 1
  pids="$(pgrep -f "$pattern" || true)"
  if [ -n "$pids" ]; then
    kill -KILL $pids >/dev/null 2>&1 || true
  fi
  echo "[清理] 已清理 ${name} 的残留进程"
}

echo "== 停止本地应用 =="

stop_by_pid_file "web" "$PID_DIR/web.pid"
stop_by_pid_file "server-java" "$PID_DIR/server-java.pid"

# 兜底清理：处理 pid 文件缺失/过期导致的残留进程。
cleanup_by_pattern "web(vite)" "$ROOT/apps/web/node_modules/.bin/vite"
cleanup_by_pattern "server-java(maven)" "$ROOT/apps/server-java.*spring-boot:run"
cleanup_by_pattern "server-java(java)" "$ROOT/apps/server-java/target/classes"

echo "[完成] 已发出本地应用停止命令。"
