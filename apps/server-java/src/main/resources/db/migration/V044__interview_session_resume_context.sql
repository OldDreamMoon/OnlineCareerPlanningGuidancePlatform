SET @interview_sessions_resume_context_ddl = (
  SELECT IF(
    EXISTS(
      SELECT 1
        FROM information_schema.columns
       WHERE table_schema = DATABASE()
         AND table_name = 'interview_sessions'
         AND column_name = 'resume_context_json'
    ),
    'SELECT 1',
    'ALTER TABLE interview_sessions ADD COLUMN resume_context_json LONGTEXT NULL AFTER mode'
  )
);
PREPARE stmt FROM @interview_sessions_resume_context_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
