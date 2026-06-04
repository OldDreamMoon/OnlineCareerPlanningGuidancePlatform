SET @ai_call_logs_thoughts_tokens_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_call_logs'
       AND column_name = 'thoughts_tokens'
);
SET @ai_call_logs_thoughts_tokens_ddl = IF(
    @ai_call_logs_thoughts_tokens_exists = 0,
    'ALTER TABLE ai_call_logs ADD COLUMN thoughts_tokens INT NOT NULL DEFAULT 0 AFTER total_tokens',
    'SELECT 1'
);
PREPARE stmt FROM @ai_call_logs_thoughts_tokens_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ai_call_logs_reasoning_effort_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_call_logs'
       AND column_name = 'reasoning_effort'
);
SET @ai_call_logs_reasoning_effort_ddl = IF(
    @ai_call_logs_reasoning_effort_exists = 0,
    'ALTER TABLE ai_call_logs ADD COLUMN reasoning_effort VARCHAR(20) NULL AFTER thoughts_tokens',
    'SELECT 1'
);
PREPARE stmt FROM @ai_call_logs_reasoning_effort_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ai_call_logs_thinking_budget_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_call_logs'
       AND column_name = 'thinking_budget'
);
SET @ai_call_logs_thinking_budget_ddl = IF(
    @ai_call_logs_thinking_budget_exists = 0,
    'ALTER TABLE ai_call_logs ADD COLUMN thinking_budget INT NULL AFTER reasoning_effort',
    'SELECT 1'
);
PREPARE stmt FROM @ai_call_logs_thinking_budget_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ai_call_logs_thinking_level_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_call_logs'
       AND column_name = 'thinking_level'
);
SET @ai_call_logs_thinking_level_ddl = IF(
    @ai_call_logs_thinking_level_exists = 0,
    'ALTER TABLE ai_call_logs ADD COLUMN thinking_level VARCHAR(40) NULL AFTER thinking_budget',
    'SELECT 1'
);
PREPARE stmt FROM @ai_call_logs_thinking_level_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
