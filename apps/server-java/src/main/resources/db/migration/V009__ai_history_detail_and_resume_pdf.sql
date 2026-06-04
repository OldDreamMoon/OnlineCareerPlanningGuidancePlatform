SET @column_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_call_logs'
       AND column_name = 'quota_weight'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE ai_call_logs ADD COLUMN quota_weight INT NOT NULL DEFAULT 1',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_call_logs'
       AND column_name = 'result_summary'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE ai_call_logs ADD COLUMN result_summary TEXT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_call_logs'
       AND column_name = 'result_payload_json'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE ai_call_logs ADD COLUMN result_payload_json LONGTEXT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_call_logs'
       AND column_name = 'user_deleted_at'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE ai_call_logs ADD COLUMN user_deleted_at TIMESTAMP NULL',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'interview_sessions'
       AND column_name = 'reserved_quota_weight'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE interview_sessions ADD COLUMN reserved_quota_weight INT NOT NULL DEFAULT 5',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'interview_sessions'
       AND column_name = 'summary_overall_score'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE interview_sessions ADD COLUMN summary_overall_score INT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'interview_sessions'
       AND column_name = 'summary_strengths_json'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE interview_sessions ADD COLUMN summary_strengths_json LONGTEXT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'interview_sessions'
       AND column_name = 'summary_weaknesses_json'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE interview_sessions ADD COLUMN summary_weaknesses_json LONGTEXT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'interview_sessions'
       AND column_name = 'summary_suggestions_json'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE interview_sessions ADD COLUMN summary_suggestions_json LONGTEXT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'interview_sessions'
       AND column_name = 'summary_provider'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE interview_sessions ADD COLUMN summary_provider VARCHAR(50) NULL',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'interview_sessions'
       AND column_name = 'summary_model'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE interview_sessions ADD COLUMN summary_model VARCHAR(100) NULL',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'interview_sessions'
       AND column_name = 'summary_latency_ms'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE interview_sessions ADD COLUMN summary_latency_ms BIGINT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'interview_sessions'
       AND column_name = 'summary_generated_at'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE interview_sessions ADD COLUMN summary_generated_at TIMESTAMP NULL',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'interview_sessions'
       AND column_name = 'finish_reason'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE interview_sessions ADD COLUMN finish_reason VARCHAR(50) NULL',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'interview_sessions'
       AND column_name = 'ended_by_ai'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE interview_sessions ADD COLUMN ended_by_ai TINYINT NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'interview_sessions'
       AND column_name = 'user_deleted_at'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE interview_sessions ADD COLUMN user_deleted_at TIMESTAMP NULL',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
