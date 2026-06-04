SET @prompt_templates_template_format_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'prompt_templates'
       AND column_name = 'template_format'
);
SET @prompt_templates_template_format_ddl = IF(
    @prompt_templates_template_format_exists = 0,
    'ALTER TABLE prompt_templates ADD COLUMN template_format VARCHAR(32) NOT NULL DEFAULT ''TEXT'' AFTER status',
    'SELECT 1'
);
PREPARE stmt FROM @prompt_templates_template_format_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @prompt_templates_bundle_json_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'prompt_templates'
       AND column_name = 'bundle_json'
);
SET @prompt_templates_bundle_json_ddl = IF(
    @prompt_templates_bundle_json_exists = 0,
    'ALTER TABLE prompt_templates ADD COLUMN bundle_json LONGTEXT NULL AFTER variables_json',
    'SELECT 1'
);
PREPARE stmt FROM @prompt_templates_bundle_json_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
