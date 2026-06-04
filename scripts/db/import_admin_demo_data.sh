#!/usr/bin/env bash
set -euo pipefail

ARCHIVE_DIR="scripts/db/archive/mysql_admin_demo"

cat >&2 <<EOF
[ERROR] scripts/db/import_admin_demo_data.sh 已归档退出当前 PostgreSQL 部署基线。

- 历史 MySQL 管理员演示数据脚本现存放于：$ARCHIVE_DIR
- 如需追溯历史实现，请阅读：$ARCHIVE_DIR/README.md
- 如需在历史 MySQL 环境显式复用，请手工执行：
  bash $ARCHIVE_DIR/import_admin_demo_data.sh --yes

注意：如果当前目标是 PostgreSQL 服务器，请重新实现 PostgreSQL 版 seed / reset / check 脚本，不要直接复用这组 MySQL 方言脚本。
EOF

exit 1
