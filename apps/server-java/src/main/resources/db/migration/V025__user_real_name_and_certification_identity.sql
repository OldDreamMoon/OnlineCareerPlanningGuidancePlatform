SET @ddl = IF (
    EXISTS(
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'users'
           AND column_name = 'real_name'
    ),
    'SELECT 1',
    'ALTER TABLE users ADD COLUMN real_name VARCHAR(100) NULL AFTER display_name'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE users
   SET real_name = display_name
 WHERE real_name IS NULL
    OR TRIM(real_name) = '';

SET @ddl = IF (
    EXISTS(
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'mentor_profiles'
           AND column_name = 'company_name'
    ),
    'SELECT 1',
    'ALTER TABLE mentor_profiles ADD COLUMN company_name VARCHAR(200) NULL AFTER is_available'
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
           AND column_name = 'job_title'
    ),
    'SELECT 1',
    'ALTER TABLE mentor_profiles ADD COLUMN job_title VARCHAR(100) NULL AFTER company_name'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE mentor_profiles
   SET company_name = COALESCE(company_name, '待补充'),
       job_title = COALESCE(job_title, '待补充')
 WHERE company_name IS NULL
    OR job_title IS NULL;

SET @ddl = IF (
    EXISTS(
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'enterprise_profiles'
           AND column_name = 'contact_title'
    ),
    'SELECT 1',
    'ALTER TABLE enterprise_profiles ADD COLUMN contact_title VARCHAR(100) NULL AFTER hiring_tags'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE enterprise_profiles
   SET company_name = COALESCE(company_name, '待补充'),
       contact_title = COALESCE(contact_title, '待补充')
 WHERE company_name IS NULL
    OR contact_title IS NULL;
