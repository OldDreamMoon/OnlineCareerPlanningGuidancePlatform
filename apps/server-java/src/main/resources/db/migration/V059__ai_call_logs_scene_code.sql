SET @ai_call_logs_scene_code_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_call_logs'
       AND column_name = 'scene_code'
);

SET @ai_call_logs_scene_code_ddl = IF(
    @ai_call_logs_scene_code_exists = 0,
    'ALTER TABLE ai_call_logs ADD COLUMN scene_code VARCHAR(60) NULL AFTER task_type',
    'SELECT 1'
);

PREPARE stmt FROM @ai_call_logs_scene_code_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_ai_call_logs_task_scene_time_exists = (
    SELECT COUNT(*)
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_call_logs'
       AND index_name = 'idx_ai_call_logs_task_scene_time'
);

SET @idx_ai_call_logs_task_scene_time_ddl = IF(
    @idx_ai_call_logs_task_scene_time_exists = 0,
    'CREATE INDEX idx_ai_call_logs_task_scene_time ON ai_call_logs(task_type, scene_code, created_at)',
    'SELECT 1'
);

PREPARE stmt FROM @idx_ai_call_logs_task_scene_time_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
