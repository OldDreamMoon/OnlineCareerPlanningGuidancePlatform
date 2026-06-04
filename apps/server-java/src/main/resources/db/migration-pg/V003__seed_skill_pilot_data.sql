INSERT INTO skills(node_code, label, description, parent_code, sort_order, created_at, updated_at)
VALUES
    ('programming_language_foundations', '程序设计与语言基础', '技能树根节点', NULL, 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('java_programming', 'Java 程序设计', '掌握 Java 核心语法和工程基础', 'programming_language_foundations', 120, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('object_oriented_modeling', '面向对象建模', '围绕类、接口与职责边界建模', 'java_programming', 130, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('backend_service_development', '后端服务开发', '围绕服务分层、接口与业务实现展开', 'programming_language_foundations', 220, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('transaction_consistency', '事务与一致性', '理解事务、锁与一致性保障', 'backend_service_development', 340, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO skill_relations(source_node_code, target_node_code, relation_type, label, sort_order, created_at, updated_at)
VALUES
    ('java_programming', 'object_oriented_modeling', 'ADVANCE_TO', 'Java 基础进阶到对象建模', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO skill_node_resources(resource_code, node_code, resource_type, title, source_label, duration_label, link_url, sort_order, created_at, updated_at)
VALUES
    ('skill_pg_root_doc', 'programming_language_foundations', 'article', '程序设计底座入门', '平台内置', '阅读 12m', 'https://example.com/skill/root', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('skill_pg_backend_video', 'backend_service_development', 'video', '后端服务开发导论', '平台内置', '观看 18m', 'https://example.com/skill/backend', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
