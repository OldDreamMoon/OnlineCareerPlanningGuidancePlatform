CREATE TABLE IF NOT EXISTS ai_provider_models (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_config_id BIGINT NOT NULL,
    model_code VARCHAR(120) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    input_cost_per_1k DECIMAL(10, 6) NOT NULL DEFAULT 0,
    output_cost_per_1k DECIMAL(10, 6) NOT NULL DEFAULT 0,
    context_window INT NULL,
    max_output_tokens INT NULL,
    supported_task_types_json LONGTEXT NULL,
    notes VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ai_provider_models_provider_model UNIQUE (provider_config_id, model_code),
    CONSTRAINT fk_ai_provider_models_provider FOREIGN KEY (provider_config_id) REFERENCES ai_provider_configs(id)
);

CREATE INDEX idx_ai_provider_models_provider_enabled
    ON ai_provider_models(provider_config_id, enabled, model_code);

CREATE TABLE IF NOT EXISTS ai_scene_route_policies (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    policy_code VARCHAR(80) NOT NULL,
    task_type VARCHAR(40) NOT NULL,
    scene_code VARCHAR(60) NOT NULL,
    user_tier VARCHAR(20) NOT NULL DEFAULT 'FREE',
    strategy_type VARCHAR(20) NOT NULL DEFAULT 'SINGLE',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    notes VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ai_scene_route_policies_code UNIQUE (policy_code),
    CONSTRAINT uq_ai_scene_route_policies_scene_tier UNIQUE (task_type, scene_code, user_tier)
);

CREATE INDEX idx_ai_scene_route_policies_lookup
    ON ai_scene_route_policies(task_type, scene_code, user_tier, enabled);

SET @route_policy_id_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_model_routes'
       AND column_name = 'scene_route_policy_id'
);
SET @route_policy_id_ddl = IF(
    @route_policy_id_exists = 0,
    'ALTER TABLE ai_model_routes ADD COLUMN scene_route_policy_id BIGINT NULL AFTER scene_code',
    'SELECT 1'
);
PREPARE stmt FROM @route_policy_id_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @route_weight_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_model_routes'
       AND column_name = 'candidate_weight'
);
SET @route_weight_ddl = IF(
    @route_weight_exists = 0,
    'ALTER TABLE ai_model_routes ADD COLUMN candidate_weight INT NOT NULL DEFAULT 100 AFTER priority_no',
    'SELECT 1'
);
PREPARE stmt FROM @route_weight_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @route_policy_fk_exists = (
    SELECT COUNT(*)
      FROM information_schema.table_constraints
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_model_routes'
       AND constraint_name = 'fk_ai_model_routes_policy'
);
SET @route_policy_fk_ddl = IF(
    @route_policy_fk_exists = 0,
    'ALTER TABLE ai_model_routes ADD CONSTRAINT fk_ai_model_routes_policy FOREIGN KEY (scene_route_policy_id) REFERENCES ai_scene_route_policies(id)',
    'SELECT 1'
);
PREPARE stmt FROM @route_policy_fk_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @route_policy_idx_exists = (
    SELECT COUNT(*)
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_model_routes'
       AND index_name = 'idx_ai_model_routes_policy_priority'
);
SET @route_policy_idx_ddl = IF(
    @route_policy_idx_exists = 0,
    'CREATE INDEX idx_ai_model_routes_policy_priority ON ai_model_routes(scene_route_policy_id, enabled, priority_no)',
    'SELECT 1'
);
PREPARE stmt FROM @route_policy_idx_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @call_log_route_code_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_call_logs'
       AND column_name = 'route_code'
);
SET @call_log_route_code_ddl = IF(
    @call_log_route_code_exists = 0,
    'ALTER TABLE ai_call_logs ADD COLUMN route_code VARCHAR(80) NULL AFTER model',
    'SELECT 1'
);
PREPARE stmt FROM @call_log_route_code_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @call_log_route_policy_code_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_call_logs'
       AND column_name = 'route_policy_code'
);
SET @call_log_route_policy_code_ddl = IF(
    @call_log_route_policy_code_exists = 0,
    'ALTER TABLE ai_call_logs ADD COLUMN route_policy_code VARCHAR(80) NULL AFTER route_code',
    'SELECT 1'
);
PREPARE stmt FROM @call_log_route_policy_code_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @call_log_model_idx_exists = (
    SELECT COUNT(*)
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_call_logs'
       AND index_name = 'idx_ai_call_logs_model_time'
);
SET @call_log_model_idx_ddl = IF(
    @call_log_model_idx_exists = 0,
    'CREATE INDEX idx_ai_call_logs_model_time ON ai_call_logs(model, created_at)',
    'SELECT 1'
);
PREPARE stmt FROM @call_log_model_idx_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @call_log_tier_idx_exists = (
    SELECT COUNT(*)
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_call_logs'
       AND index_name = 'idx_ai_call_logs_user_tier_time'
);
SET @call_log_tier_idx_ddl = IF(
    @call_log_tier_idx_exists = 0,
    'CREATE INDEX idx_ai_call_logs_user_tier_time ON ai_call_logs(user_tier, created_at)',
    'SELECT 1'
);
PREPARE stmt FROM @call_log_tier_idx_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
