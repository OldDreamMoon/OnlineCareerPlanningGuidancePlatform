CREATE TABLE IF NOT EXISTS ai_provider_configs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_code VARCHAR(60) NOT NULL,
    provider_type VARCHAR(40) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    base_url VARCHAR(255) NOT NULL,
    api_key_ciphertext TEXT,
    api_key_masked VARCHAR(80),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    timeout_ms INT NOT NULL DEFAULT 15000,
    max_retries INT NOT NULL DEFAULT 1,
    cost_per_1k_input DECIMAL(10, 6) NOT NULL DEFAULT 0,
    cost_per_1k_output DECIMAL(10, 6) NOT NULL DEFAULT 0,
    extra_config_json LONGTEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ai_provider_configs_code UNIQUE (provider_code)
);

CREATE TABLE IF NOT EXISTS ai_model_routes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    route_code VARCHAR(60) NOT NULL,
    task_type VARCHAR(40) NOT NULL,
    scene_code VARCHAR(60),
    provider_config_id BIGINT NOT NULL,
    model_name VARCHAR(120) NOT NULL,
    priority_no INT NOT NULL DEFAULT 100,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    temperature DECIMAL(5, 2) NOT NULL DEFAULT 0.20,
    system_prompt LONGTEXT,
    extra_config_json LONGTEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ai_model_routes_code UNIQUE (route_code),
    CONSTRAINT fk_ai_model_routes_provider FOREIGN KEY (provider_config_id) REFERENCES ai_provider_configs(id)
);

CREATE INDEX idx_ai_model_routes_task_scene_priority ON ai_model_routes(task_type, scene_code, enabled, priority_no);
