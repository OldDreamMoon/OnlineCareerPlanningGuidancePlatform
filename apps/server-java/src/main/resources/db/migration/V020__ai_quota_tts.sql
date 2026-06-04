INSERT INTO ai_quota_policies(tier, task_type, daily_free_limit, points_per_call, daily_max_limit, model_preference, max_input_tokens, created_at, updated_at)
SELECT 'FREE', 'TTS', 2, 5, 10, 'mock-economy-model', 2000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ai_quota_policies WHERE tier = 'FREE' AND task_type = 'TTS');

INSERT INTO ai_quota_policies(tier, task_type, daily_free_limit, points_per_call, daily_max_limit, model_preference, max_input_tokens, created_at, updated_at)
SELECT 'PREMIUM', 'TTS', -1, 0, -1, 'mock-premium-model', 4000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ai_quota_policies WHERE tier = 'PREMIUM' AND task_type = 'TTS');
