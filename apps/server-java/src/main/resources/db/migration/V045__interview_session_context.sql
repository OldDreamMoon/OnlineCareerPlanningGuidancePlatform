SET @interview_sessions_session_context_ddl = (
  SELECT IF(
    EXISTS(
      SELECT 1
        FROM information_schema.columns
       WHERE table_schema = DATABASE()
         AND table_name = 'interview_sessions'
         AND column_name = 'session_context_json'
    ),
    'SELECT 1',
    'ALTER TABLE interview_sessions ADD COLUMN session_context_json LONGTEXT NULL AFTER resume_context_json'
  )
);
PREPARE stmt FROM @interview_sessions_session_context_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
