CREATE TABLE IF NOT EXISTS prompt_templates (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_type VARCHAR(40) NOT NULL,
    template_name VARCHAR(80) NOT NULL,
    version_no INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    content LONGTEXT NOT NULL,
    description VARCHAR(255),
    variables_json LONGTEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_prompt_templates_version UNIQUE (task_type, template_name, version_no)
);

CREATE INDEX idx_prompt_templates_task_name_status ON prompt_templates(task_type, template_name, status, version_no);

SET @route_prompt_template_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_model_routes'
       AND column_name = 'prompt_template_name'
);
SET @route_prompt_template_ddl = IF(
    @route_prompt_template_exists = 0,
    'ALTER TABLE ai_model_routes ADD COLUMN prompt_template_name VARCHAR(80) NULL AFTER system_prompt',
    'SELECT 1'
);
PREPARE stmt FROM @route_prompt_template_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @route_prompt_template_index_exists = (
    SELECT COUNT(*)
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_model_routes'
       AND index_name = 'idx_ai_model_routes_prompt_template'
);
SET @route_prompt_template_index_ddl = IF(
    @route_prompt_template_index_exists = 0,
    'CREATE INDEX idx_ai_model_routes_prompt_template ON ai_model_routes(task_type, prompt_template_name)',
    'SELECT 1'
);
PREPARE stmt FROM @route_prompt_template_index_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
