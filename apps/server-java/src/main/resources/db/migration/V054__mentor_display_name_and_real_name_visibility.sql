SET @ddl = IF (
    EXISTS(
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'mentor_profiles'
           AND column_name = 'show_real_name'
    ),
    'SELECT 1',
    'ALTER TABLE mentor_profiles ADD COLUMN show_real_name TINYINT(1) NOT NULL DEFAULT 0 AFTER job_title'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
