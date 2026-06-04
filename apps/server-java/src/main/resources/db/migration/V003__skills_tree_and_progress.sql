CREATE TABLE IF NOT EXISTS skills (
    node_code VARCHAR(100) PRIMARY KEY,
    label VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    parent_code VARCHAR(100) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_skills_parent_code FOREIGN KEY (parent_code) REFERENCES skills(node_code)
);

CREATE TABLE IF NOT EXISTS skill_progress (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_user_id BIGINT NOT NULL,
    node_code VARCHAR(100) NOT NULL,
    progress_status VARCHAR(20) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_skill_progress_user_node UNIQUE (student_user_id, node_code),
    CONSTRAINT fk_skill_progress_user FOREIGN KEY (student_user_id) REFERENCES users(id),
    CONSTRAINT fk_skill_progress_node FOREIGN KEY (node_code) REFERENCES skills(node_code)
);

INSERT INTO skills(node_code, label, description, parent_code, sort_order, created_at, updated_at)
SELECT 'java_basics', 'Java 基础', '掌握 Java 语法、集合与面向对象基础。', NULL, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM skills WHERE node_code = 'java_basics');

INSERT INTO skills(node_code, label, description, parent_code, sort_order, created_at, updated_at)
SELECT 'sql_basics', 'SQL 基础', '掌握 MySQL 常见查询、索引与事务基础。', NULL, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM skills WHERE node_code = 'sql_basics');

INSERT INTO skills(node_code, label, description, parent_code, sort_order, created_at, updated_at)
SELECT 'spring_boot', 'Spring Boot', '能够完成 REST API、配置与基础工程搭建。', 'java_basics', 30, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM skills WHERE node_code = 'spring_boot');

INSERT INTO skills(node_code, label, description, parent_code, sort_order, created_at, updated_at)
SELECT 'api_design', '接口设计', '掌握分页、鉴权、错误码与接口契约设计。', 'spring_boot', 40, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM skills WHERE node_code = 'api_design');

INSERT INTO skills(node_code, label, description, parent_code, sort_order, created_at, updated_at)
SELECT 'deployment_basics', '部署基础', '了解日志、配置分环境与服务部署发布。', 'api_design', 50, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM skills WHERE node_code = 'deployment_basics');
