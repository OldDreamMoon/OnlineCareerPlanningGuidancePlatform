CREATE TABLE IF NOT EXISTS ai_gateway_runtime_settings (
    setting_key VARCHAR(80) PRIMARY KEY,
    setting_value VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_gateway_runtime_settings_updated_at
    ON ai_gateway_runtime_settings(updated_at);
