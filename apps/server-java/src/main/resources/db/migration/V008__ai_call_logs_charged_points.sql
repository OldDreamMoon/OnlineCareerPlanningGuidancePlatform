SET @column_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_call_logs'
       AND column_name = 'charged_points'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE ai_call_logs ADD COLUMN charged_points INT NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
