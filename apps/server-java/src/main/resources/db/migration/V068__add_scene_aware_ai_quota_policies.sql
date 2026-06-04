SET @scene_code_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_quota_policies'
       AND column_name = 'scene_code'
);
SET @scene_code_ddl = IF(
    @scene_code_exists = 0,
    'ALTER TABLE ai_quota_policies ADD COLUMN scene_code VARCHAR(60) NULL AFTER task_type',
    'SELECT 1'
);
PREPARE stmt FROM @scene_code_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @legacy_uq_exists = (
    SELECT COUNT(*)
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_quota_policies'
       AND index_name = 'uq_ai_quota_policies_tier_task'
);
SET @legacy_uq_ddl = IF(
    @legacy_uq_exists > 0,
    'ALTER TABLE ai_quota_policies DROP INDEX uq_ai_quota_policies_tier_task',
    'SELECT 1'
);
PREPARE stmt FROM @legacy_uq_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @scene_uq_exists = (
    SELECT COUNT(*)
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_quota_policies'
       AND index_name = 'uq_ai_quota_policies_tier_task_scene'
);
SET @scene_uq_ddl = IF(
    @scene_uq_exists = 0,
    'ALTER TABLE ai_quota_policies ADD CONSTRAINT uq_ai_quota_policies_tier_task_scene UNIQUE (tier, task_type, scene_code)',
    'SELECT 1'
);
PREPARE stmt FROM @scene_uq_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @lookup_idx_exists = (
    SELECT COUNT(*)
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_quota_policies'
       AND index_name = 'idx_ai_quota_policies_lookup'
);
SET @lookup_idx_ddl = IF(
    @lookup_idx_exists = 0,
    'CREATE INDEX idx_ai_quota_policies_lookup ON ai_quota_policies(tier, task_type, scene_code)',
    'SELECT 1'
);
PREPARE stmt FROM @lookup_idx_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO ai_quota_policies(
    tier,
    task_type,
    scene_code,
    daily_free_limit,
    points_per_call,
    daily_max_limit,
    model_preference,
    max_input_tokens,
    created_at,
    updated_at
)
SELECT
    'FREE',
    'COMMUNITY_REPLY',
    'COMMUNITY_PRE_ANSWER',
    5,
    2,
    20,
    'mock-economy-model',
    4000,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
      FROM ai_quota_policies
     WHERE tier = 'FREE'
       AND task_type = 'COMMUNITY_REPLY'
       AND scene_code = 'COMMUNITY_PRE_ANSWER'
);

INSERT INTO ai_quota_policies(
    tier,
    task_type,
    scene_code,
    daily_free_limit,
    points_per_call,
    daily_max_limit,
    model_preference,
    max_input_tokens,
    created_at,
    updated_at
)
SELECT
    'FREE',
    'COMMUNITY_REPLY',
    'MENTOR_PREP_SHEET_GENERATE',
    2,
    6,
    8,
    'mock-economy-model',
    8000,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
      FROM ai_quota_policies
     WHERE tier = 'FREE'
       AND task_type = 'COMMUNITY_REPLY'
       AND scene_code = 'MENTOR_PREP_SHEET_GENERATE'
);

INSERT INTO ai_quota_policies(
    tier,
    task_type,
    scene_code,
    daily_free_limit,
    points_per_call,
    daily_max_limit,
    model_preference,
    max_input_tokens,
    created_at,
    updated_at
)
SELECT
    'PREMIUM',
    'COMMUNITY_REPLY',
    'COMMUNITY_PRE_ANSWER',
    -1,
    0,
    -1,
    'mock-premium-model',
    12000,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
      FROM ai_quota_policies
     WHERE tier = 'PREMIUM'
       AND task_type = 'COMMUNITY_REPLY'
       AND scene_code = 'COMMUNITY_PRE_ANSWER'
);

INSERT INTO ai_quota_policies(
    tier,
    task_type,
    scene_code,
    daily_free_limit,
    points_per_call,
    daily_max_limit,
    model_preference,
    max_input_tokens,
    created_at,
    updated_at
)
SELECT
    'PREMIUM',
    'COMMUNITY_REPLY',
    'MENTOR_PREP_SHEET_GENERATE',
    -1,
    0,
    -1,
    'mock-premium-model',
    16000,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
      FROM ai_quota_policies
     WHERE tier = 'PREMIUM'
       AND task_type = 'COMMUNITY_REPLY'
       AND scene_code = 'MENTOR_PREP_SHEET_GENERATE'
);
