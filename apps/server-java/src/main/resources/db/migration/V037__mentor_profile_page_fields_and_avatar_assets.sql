SET @ddl = IF (
    EXISTS(
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'mentor_profiles'
           AND column_name = 'avatar_bucket'
    ),
    'SELECT 1',
    'ALTER TABLE mentor_profiles ADD COLUMN avatar_bucket VARCHAR(100) NULL AFTER avatar_url'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF (
    EXISTS(
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'mentor_profiles'
           AND column_name = 'avatar_object_key'
    ),
    'SELECT 1',
    'ALTER TABLE mentor_profiles ADD COLUMN avatar_object_key VARCHAR(255) NULL AFTER avatar_bucket'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF (
    EXISTS(
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'mentor_profiles'
           AND column_name = 'avatar_content_type'
    ),
    'SELECT 1',
    'ALTER TABLE mentor_profiles ADD COLUMN avatar_content_type VARCHAR(100) NULL AFTER avatar_object_key'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF (
    EXISTS(
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'mentor_profiles'
           AND column_name = 'avatar_updated_at'
    ),
    'SELECT 1',
    'ALTER TABLE mentor_profiles ADD COLUMN avatar_updated_at DATETIME NULL AFTER avatar_content_type'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF (
    EXISTS(
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'mentor_profiles'
           AND column_name = 'suitable_for'
    ),
    'SELECT 1',
    'ALTER TABLE mentor_profiles ADD COLUMN suitable_for TEXT NULL AFTER bio'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF (
    EXISTS(
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'mentor_profiles'
           AND column_name = 'not_suitable_for'
    ),
    'SELECT 1',
    'ALTER TABLE mentor_profiles ADD COLUMN not_suitable_for TEXT NULL AFTER suitable_for'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF (
    EXISTS(
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'mentor_profiles'
           AND column_name = 'prep_materials'
    ),
    'SELECT 1',
    'ALTER TABLE mentor_profiles ADD COLUMN prep_materials TEXT NULL AFTER not_suitable_for'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF (
    EXISTS(
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'mentor_profiles'
           AND column_name = 'reply_rhythm'
    ),
    'SELECT 1',
    'ALTER TABLE mentor_profiles ADD COLUMN reply_rhythm TEXT NULL AFTER prep_materials'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
