#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SQL_FILE="$ROOT_DIR/scripts/db/seed_mentor_browser_test_data.sql"
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_DB="${MYSQL_DB:-bishe}"
MYSQL_USER="${MYSQL_USER:-bishe}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-bishe}"
REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${REDIS_PORT:-6379}"

echo "[INFO] 开始重刷导师浏览器测试数据"
(
  cd "$ROOT_DIR"
  MYSQL_PWD="$MYSQL_PASSWORD" mysql \
    --default-character-set=utf8mb4 \
    --protocol=TCP \
    -h "$MYSQL_HOST" \
    -P "$MYSQL_PORT" \
    -u "$MYSQL_USER" \
    "$MYSQL_DB" < "$SQL_FILE"
)

echo "[INFO] 开始清理导师相关 Redis 缓存"
python3 - <<'PY'
import os
import socket

host = os.environ.get("REDIS_HOST", "127.0.0.1")
port = int(os.environ.get("REDIS_PORT", "6379"))

def send(*parts: str) -> str:
    payload = b"*" + str(len(parts)).encode() + b"\r\n"
    for part in parts:
        data = part.encode()
        payload += b"$" + str(len(data)).encode() + b"\r\n" + data + b"\r\n"
    sock = socket.create_connection((host, port), timeout=3)
    sock.sendall(payload)
    response = b""
    while True:
        chunk = sock.recv(65536)
        if not chunk:
            break
        response += chunk
        if len(chunk) < 65536:
            break
    sock.close()
    return response.decode("utf-8", "replace")

raw = send("KEYS", "mentor:*").splitlines()
keys = []
index = 0
while index < len(raw):
    line = raw[index]
    if line.startswith("$") and index + 1 < len(raw):
        keys.append(raw[index + 1])
        index += 2
        continue
    index += 1

if not keys:
    print("[INFO] 未发现导师相关 Redis 缓存键")
else:
    print(f"[INFO] 删除导师缓存键 {len(keys)} 个")
    print(send("DEL", *keys).strip())
PY

echo "[DONE] 导师测试数据与导师缓存已同步刷新"
