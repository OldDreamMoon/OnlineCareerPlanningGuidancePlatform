CREATE TABLE IF NOT EXISTS feature_flags (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    flag_key VARCHAR(100) NOT NULL,
    flag_value VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_feature_flags_key UNIQUE (flag_key)
);

CREATE INDEX idx_feature_flags_updated_at ON feature_flags(updated_at);
