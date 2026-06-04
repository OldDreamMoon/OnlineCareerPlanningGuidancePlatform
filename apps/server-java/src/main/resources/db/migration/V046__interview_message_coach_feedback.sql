SET @interview_messages_coach_feedback_ddl = (
  SELECT IF(
    EXISTS(
      SELECT 1
        FROM information_schema.columns
       WHERE table_schema = DATABASE()
         AND table_name = 'interview_messages'
         AND column_name = 'coach_feedback'
    ),
    'SELECT 1',
    'ALTER TABLE interview_messages ADD COLUMN coach_feedback TEXT NULL AFTER message_text'
  )
);
PREPARE stmt FROM @interview_messages_coach_feedback_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
