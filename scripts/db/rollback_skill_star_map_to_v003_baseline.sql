CREATE TEMPORARY TABLE tmp_skill_star_nodes (
    node_code VARCHAR(100) PRIMARY KEY
);

INSERT INTO tmp_skill_star_nodes (node_code)
WITH RECURSIVE current_skill_tree AS (
    SELECT node_code
    FROM skills
    WHERE node_code = 'programming_language_foundations'

    UNION

    SELECT child.node_code
    FROM skills child
    INNER JOIN current_skill_tree parent ON child.parent_code = parent.node_code
)
SELECT node_code
FROM current_skill_tree;

DELETE FROM skill_progress
WHERE node_code IN (SELECT node_code FROM tmp_skill_star_nodes);

DELETE FROM skill_relations
WHERE source_node_code IN (SELECT node_code FROM tmp_skill_star_nodes)
   OR target_node_code IN (SELECT node_code FROM tmp_skill_star_nodes);

SET @previous_foreign_key_checks = @@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM skills
WHERE node_code IN (SELECT node_code FROM tmp_skill_star_nodes);

SET FOREIGN_KEY_CHECKS = @previous_foreign_key_checks;

DROP TEMPORARY TABLE tmp_skill_star_nodes;

INSERT INTO skills(node_code, label, description, parent_code, sort_order, created_at, updated_at) VALUES
('java_basics', 'Java 基础', '掌握 Java 语法、集合与面向对象基础。', NULL, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('sql_basics', 'SQL 基础', '掌握 MySQL 常见查询、索引与事务基础。', NULL, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('spring_boot', 'Spring Boot', '能够完成 REST API、配置与基础工程搭建。', 'java_basics', 30, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('api_design', '接口设计', '掌握分页、鉴权、错误码与接口契约设计。', 'spring_boot', 40, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('deployment_basics', '部署基础', '了解日志、配置分环境与服务部署发布。', 'api_design', 50, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
