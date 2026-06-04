SET @column_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'interview_sessions'
       AND column_name = 'reply_round_limit'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE interview_sessions ADD COLUMN reply_round_limit INT NOT NULL DEFAULT 3',
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
       AND column_name = 'reply_round_used'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE interview_sessions ADD COLUMN reply_round_used INT NOT NULL DEFAULT 0',
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
       AND column_name = 'summary_generated'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE interview_sessions ADD COLUMN summary_generated TINYINT NOT NULL DEFAULT 0',
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
       AND column_name = 'prepaid_points'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE interview_sessions ADD COLUMN prepaid_points INT NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
