SET @route_policy_extra_config_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_scene_route_policies'
       AND column_name = 'extra_config_json'
);
SET @route_policy_extra_config_ddl = IF(
    @route_policy_extra_config_exists = 0,
    'ALTER TABLE ai_scene_route_policies ADD COLUMN extra_config_json LONGTEXT NULL AFTER notes',
    'SELECT 1'
);
PREPARE stmt FROM @route_policy_extra_config_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
