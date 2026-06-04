-- 管理员后台演示数据导入入口。
-- 注意：
-- 1. 该入口最终会执行 scripts/db/archive/mysql_admin_demo/reset_admin_demo_data.sql。
-- 2. 该脚本会清空大部分业务表并重建演示数据，只适合演示 / 测试环境。
-- 3. 导入前请先确认当前数据库中没有需要保留的正式业务数据。

source scripts/db/archive/mysql_admin_demo/reset_admin_demo_data.sql;
