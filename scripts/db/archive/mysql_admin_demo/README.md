# 历史 MySQL 管理员演示数据脚本归档

本目录保存的是历史 MySQL 基线下的管理员后台演示数据脚本：

- `admin_demo_data.sql`
- `reset_admin_demo_data.sql`
- `check_admin_demo_data.sql`
- `import_admin_demo_data.sh`

当前仓库的默认运行链、部署模板和恢复脚本已经统一切到 PostgreSQL，这组脚本不再属于当前 PostgreSQL 部署基线。

使用约束：

- 不要在当前 PostgreSQL 基线上直接复用这组脚本
- 如果只是排查历史实现，可直接阅读本目录内容
- 如果确实需要在历史 MySQL 环境复用，可显式执行 `bash scripts/db/archive/mysql_admin_demo/import_admin_demo_data.sh --yes`
- 如果未来还需要“管理员演示数据”能力，应基于 PostgreSQL 重新设计一套新的 seed / reset / check 脚本，而不是直接迁抄这里的 MySQL 方言实现
