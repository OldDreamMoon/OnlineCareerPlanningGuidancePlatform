SET @ai_model_routes_execution_mode_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_model_routes'
       AND column_name = 'execution_mode'
);
SET @ai_model_routes_execution_mode_ddl = IF(
    @ai_model_routes_execution_mode_exists = 0,
    'ALTER TABLE ai_model_routes ADD COLUMN execution_mode VARCHAR(32) NOT NULL DEFAULT ''SYNC_BLOCKING'' AFTER priority_no',
    'SELECT 1'
);
PREPARE stmt FROM @ai_model_routes_execution_mode_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS ai_async_task_jobs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    task_type VARCHAR(40) NOT NULL,
    scene_code VARCHAR(60) NULL,
    route_code VARCHAR(60) NULL,
    execution_mode VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    provider_code VARCHAR(60) NULL,
    provider_type VARCHAR(40) NULL,
    model_name VARCHAR(120) NULL,
    prompt_template_name VARCHAR(80) NULL,
    prompt_template_version_no INT NULL,
    input_snapshot_json LONGTEXT NULL,
    context_json LONGTEXT NULL,
    prompt_snapshot_json LONGTEXT NULL,
    route_snapshot_json LONGTEXT NULL,
    result_summary TEXT NULL,
    result_payload_json LONGTEXT NULL,
    error_code VARCHAR(40) NULL,
    error_message VARCHAR(500) NULL,
    current_attempt INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 3,
    next_run_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_owner VARCHAR(80) NULL,
    lease_expires_at TIMESTAMP NULL,
    queued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ai_async_task_jobs_task_id UNIQUE (task_id),
    CONSTRAINT fk_ai_async_task_jobs_user FOREIGN KEY (user_id) REFERENCES users(id)
);

SET @idx_ai_async_task_jobs_status_next_run_exists = (
    SELECT COUNT(*)
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_async_task_jobs'
       AND index_name = 'idx_ai_async_task_jobs_status_next_run'
);
SET @idx_ai_async_task_jobs_status_next_run_ddl = IF(
    @idx_ai_async_task_jobs_status_next_run_exists = 0,
    'CREATE INDEX idx_ai_async_task_jobs_status_next_run ON ai_async_task_jobs(status, next_run_at, id)',
    'SELECT 1'
);
PREPARE stmt FROM @idx_ai_async_task_jobs_status_next_run_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_ai_async_task_jobs_user_created_exists = (
    SELECT COUNT(*)
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_async_task_jobs'
       AND index_name = 'idx_ai_async_task_jobs_user_created'
);
SET @idx_ai_async_task_jobs_user_created_ddl = IF(
    @idx_ai_async_task_jobs_user_created_exists = 0,
    'CREATE INDEX idx_ai_async_task_jobs_user_created ON ai_async_task_jobs(user_id, created_at, id)',
    'SELECT 1'
);
PREPARE stmt FROM @idx_ai_async_task_jobs_user_created_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_ai_async_task_jobs_task_scene_exists = (
    SELECT COUNT(*)
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_async_task_jobs'
       AND index_name = 'idx_ai_async_task_jobs_task_scene'
);
SET @idx_ai_async_task_jobs_task_scene_ddl = IF(
    @idx_ai_async_task_jobs_task_scene_exists = 0,
    'CREATE INDEX idx_ai_async_task_jobs_task_scene ON ai_async_task_jobs(task_type, scene_code, status, id)',
    'SELECT 1'
);
PREPARE stmt FROM @idx_ai_async_task_jobs_task_scene_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS ai_async_task_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(64) NOT NULL,
    task_job_id BIGINT NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    delivery_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    payload_json LONGTEXT NULL,
    published_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ai_async_task_events_event_id UNIQUE (event_id),
    CONSTRAINT fk_ai_async_task_events_job FOREIGN KEY (task_job_id) REFERENCES ai_async_task_jobs(id),
    CONSTRAINT fk_ai_async_task_events_user FOREIGN KEY (user_id) REFERENCES users(id)
);

SET @idx_ai_async_task_events_delivery_status_exists = (
    SELECT COUNT(*)
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_async_task_events'
       AND index_name = 'idx_ai_async_task_events_delivery_status'
);
SET @idx_ai_async_task_events_delivery_status_ddl = IF(
    @idx_ai_async_task_events_delivery_status_exists = 0,
    'CREATE INDEX idx_ai_async_task_events_delivery_status ON ai_async_task_events(delivery_status, created_at, id)',
    'SELECT 1'
);
PREPARE stmt FROM @idx_ai_async_task_events_delivery_status_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_ai_async_task_events_task_id_exists = (
    SELECT COUNT(*)
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_async_task_events'
       AND index_name = 'idx_ai_async_task_events_task_id'
);
SET @idx_ai_async_task_events_task_id_ddl = IF(
    @idx_ai_async_task_events_task_id_exists = 0,
    'CREATE INDEX idx_ai_async_task_events_task_id ON ai_async_task_events(task_id, created_at, id)',
    'SELECT 1'
);
PREPARE stmt FROM @idx_ai_async_task_events_task_id_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
