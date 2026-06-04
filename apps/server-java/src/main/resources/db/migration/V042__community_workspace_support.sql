SET @ddl = IF (
    EXISTS(
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'posts'
           AND column_name = 'scenario_code'
    ),
    'SELECT 1',
    'ALTER TABLE posts ADD COLUMN scenario_code VARCHAR(60) NOT NULL DEFAULT ''GENERAL_HELP'' AFTER tags'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF (
    EXISTS(
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'posts'
           AND column_name = 'resolved_status'
    ),
    'SELECT 1',
    'ALTER TABLE posts ADD COLUMN resolved_status VARCHAR(20) NOT NULL DEFAULT ''OPEN'' AFTER scenario_code'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF (
    EXISTS(
        SELECT 1
          FROM information_schema.statistics
         WHERE table_schema = DATABASE()
           AND table_name = 'posts'
           AND index_name = 'idx_posts_scenario_created'
    ),
    'SELECT 1',
    'CREATE INDEX idx_posts_scenario_created ON posts(scenario_code, created_at)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF (
    EXISTS(
        SELECT 1
          FROM information_schema.statistics
         WHERE table_schema = DATABASE()
           AND table_name = 'posts'
           AND index_name = 'idx_posts_resolved_created'
    ),
    'SELECT 1',
    'CREATE INDEX idx_posts_resolved_created ON posts(resolved_status, created_at)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
