-- 生产 MySQL 基础设施当前使用 utf8mb4_unicode_ci 作为 server/database 默认排序规则，
-- 而历史咨询表多为 `DEFAULT CHARSET=utf8mb4`，在 MySQL 8 下会落成 utf8mb4_0900_ai_ci。
-- V019 创建的 consult_after_sales_requests 没有显式声明字符集/排序规则，导致其 order_no/status 等
-- 字符列可能与 consult_orders 等老表产生 collation 混用，在 JOIN/FILTER 时触发 error 1267。
ALTER TABLE consult_after_sales_requests
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
