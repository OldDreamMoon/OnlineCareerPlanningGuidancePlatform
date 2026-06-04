INSERT INTO ai_quota_policies(tier, task_type, daily_free_limit, points_per_call, daily_max_limit, model_preference, max_input_tokens, created_at, updated_at)
SELECT 'FREE', 'COMMUNITY_REPLY', 5, 2, 20, 'mock-economy-model', 4000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ai_quota_policies WHERE tier = 'FREE' AND task_type = 'COMMUNITY_REPLY');

INSERT INTO ai_quota_policies(tier, task_type, daily_free_limit, points_per_call, daily_max_limit, model_preference, max_input_tokens, created_at, updated_at)
SELECT 'FREE', 'ICEBREAK', 3, 3, 15, 'mock-economy-model', 4000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ai_quota_policies WHERE tier = 'FREE' AND task_type = 'ICEBREAK');

INSERT INTO ai_quota_policies(tier, task_type, daily_free_limit, points_per_call, daily_max_limit, model_preference, max_input_tokens, created_at, updated_at)
SELECT 'PREMIUM', 'COMMUNITY_REPLY', -1, 0, -1, 'mock-premium-model', 12000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ai_quota_policies WHERE tier = 'PREMIUM' AND task_type = 'COMMUNITY_REPLY');

INSERT INTO ai_quota_policies(tier, task_type, daily_free_limit, points_per_call, daily_max_limit, model_preference, max_input_tokens, created_at, updated_at)
SELECT 'PREMIUM', 'ICEBREAK', -1, 0, -1, 'mock-premium-model', 12000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ai_quota_policies WHERE tier = 'PREMIUM' AND task_type = 'ICEBREAK');
