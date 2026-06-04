CREATE TABLE IF NOT EXISTS ai_quota_policies (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tier VARCHAR(20) NOT NULL,
    task_type VARCHAR(30) NOT NULL,
    daily_free_limit INT NOT NULL,
    points_per_call INT NOT NULL,
    daily_max_limit INT NOT NULL,
    model_preference VARCHAR(50) NULL,
    max_input_tokens INT NOT NULL DEFAULT 4000,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ai_quota_policies_tier_task UNIQUE (tier, task_type)
);

CREATE TABLE IF NOT EXISTS ai_call_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    trace_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    task_type VARCHAR(30) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    model VARCHAR(50) NOT NULL,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    error_code VARCHAR(20) NULL,
    request_tokens INT NOT NULL DEFAULT 0,
    response_tokens INT NOT NULL DEFAULT 0,
    total_tokens INT NOT NULL DEFAULT 0,
    estimated_cost DECIMAL(10, 6) NOT NULL DEFAULT 0,
    user_tier VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_call_logs_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_ai_call_logs_user_task_time ON ai_call_logs(user_id, task_type, created_at);
CREATE INDEX idx_ai_call_logs_trace_id ON ai_call_logs(trace_id);

INSERT INTO ai_quota_policies(tier, task_type, daily_free_limit, points_per_call, daily_max_limit, model_preference, max_input_tokens, created_at, updated_at)
SELECT 'FREE', 'RESUME', 3, 10, 20, 'mock-economy-model', 4000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ai_quota_policies WHERE tier = 'FREE' AND task_type = 'RESUME');

INSERT INTO ai_quota_policies(tier, task_type, daily_free_limit, points_per_call, daily_max_limit, model_preference, max_input_tokens, created_at, updated_at)
SELECT 'FREE', 'INTERVIEW_TEXT', 5, 5, 30, 'mock-economy-model', 4000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ai_quota_policies WHERE tier = 'FREE' AND task_type = 'INTERVIEW_TEXT');

INSERT INTO ai_quota_policies(tier, task_type, daily_free_limit, points_per_call, daily_max_limit, model_preference, max_input_tokens, created_at, updated_at)
SELECT 'PREMIUM', 'RESUME', -1, 0, -1, 'mock-premium-model', 12000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ai_quota_policies WHERE tier = 'PREMIUM' AND task_type = 'RESUME');

INSERT INTO ai_quota_policies(tier, task_type, daily_free_limit, points_per_call, daily_max_limit, model_preference, max_input_tokens, created_at, updated_at)
SELECT 'PREMIUM', 'INTERVIEW_TEXT', -1, 0, -1, 'mock-premium-model', 12000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ai_quota_policies WHERE tier = 'PREMIUM' AND task_type = 'INTERVIEW_TEXT');
