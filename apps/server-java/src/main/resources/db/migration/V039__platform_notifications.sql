ALTER TABLE notifications
    MODIFY COLUMN content TEXT NOT NULL,
    ADD COLUMN category VARCHAR(32) NULL AFTER type,
    ADD COLUMN title VARCHAR(160) NULL AFTER category,
    ADD COLUMN ref_type VARCHAR(60) NULL AFTER content,
    ADD COLUMN action_code VARCHAR(80) NULL AFTER ref_id,
    ADD COLUMN priority VARCHAR(20) NULL AFTER action_code,
    ADD COLUMN event_id VARCHAR(64) NULL AFTER priority,
    ADD COLUMN payload_json LONGTEXT NULL AFTER event_id,
    ADD COLUMN archived_at DATETIME NULL AFTER read_at,
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER created_at;

UPDATE notifications
SET category = CASE
                   WHEN type LIKE 'CONSULT_%' THEN 'CONSULT'
                   WHEN type LIKE 'BOUNTY_%' THEN 'BOUNTY'
                   WHEN type LIKE 'CERTIFICATION_%' THEN 'CERTIFICATION'
                   WHEN type LIKE 'AI_%' THEN 'AI_TASK'
                   ELSE 'SYSTEM'
               END,
    title = CASE
                WHEN type = 'CONSULT_PAID' THEN '咨询订单已支付'
                WHEN type = 'CONSULT_REPLIED' THEN '导师已回复你的咨询'
                WHEN type = 'CONSULT_CANCELED' THEN '咨询订单已取消'
                WHEN type = 'CONSULT_TIMEOUT_CANCELED' THEN '咨询订单已超时关闭'
                WHEN type = 'CONSULT_CLOSED' THEN '咨询订单已关闭'
                WHEN type = 'CONSULT_REVIEWED' THEN '咨询订单收到新评价'
                WHEN type = 'CONSULT_AFTER_SALES_SUBMITTED' THEN '售后申请已提交'
                WHEN type = 'CONSULT_AFTER_SALES_PENDING' THEN '咨询订单收到售后申请'
                WHEN type = 'CONSULT_AFTER_SALES_REJECTED' THEN '售后申请未通过'
                WHEN type = 'CONSULT_REFUNDED' THEN '咨询订单已退款'
                WHEN type = 'CONSULT_PAYMENT_RECONCILED' THEN '支付状态已人工确认'
                WHEN type = 'CONSULT_PAYMENT_EXCEPTION' THEN '支付异常订单已处理'
                WHEN type = 'CONSULT_REFUND_CONFIRMED' THEN '退款处理已确认'
                WHEN type = 'BOUNTY_SUBMITTED' THEN '悬赏任务有新的成果提交'
                WHEN type = 'BOUNTY_REVIEWED' THEN '悬赏任务审核结果已更新'
                WHEN type = 'CERTIFICATION_APPROVED' THEN '认证审核已通过'
                WHEN type = 'CERTIFICATION_REJECTED' THEN '认证审核未通过'
                WHEN type = 'CERTIFICATION_RESUBMIT_REQUIRED' THEN '认证资料需要重新提交'
                WHEN type = 'SYSTEM_ANNOUNCEMENT' THEN '平台公告'
                WHEN type = 'SYSTEM_MAINTENANCE' THEN '系统维护通知'
                ELSE '平台通知'
            END,
    priority = CASE
                   WHEN type IN ('CONSULT_PAYMENT_EXCEPTION', 'CONSULT_REFUNDED', 'CERTIFICATION_REJECTED', 'AI_RESUME_TASK_FAILED', 'SYSTEM_MAINTENANCE')
                       THEN 'HIGH'
                   ELSE 'NORMAL'
               END,
    updated_at = COALESCE(updated_at, created_at);

ALTER TABLE notifications
    MODIFY COLUMN category VARCHAR(32) NOT NULL,
    MODIFY COLUMN title VARCHAR(160) NOT NULL,
    MODIFY COLUMN priority VARCHAR(20) NOT NULL,
    ADD INDEX idx_notifications_user_read_archived (user_id, is_read, archived_at, created_at),
    ADD INDEX idx_notifications_event_id (event_id),
    ADD INDEX idx_notifications_category_time (category, created_at);

CREATE TABLE notification_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    type VARCHAR(60) NOT NULL,
    category VARCHAR(32) NOT NULL,
    source_type VARCHAR(60),
    source_id VARCHAR(64),
    actor_user_id BIGINT,
    priority VARCHAR(20) NOT NULL,
    dedupe_key VARCHAR(160),
    payload_json LONGTEXT,
    occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_notification_events_event_id UNIQUE (event_id),
    CONSTRAINT uq_notification_events_dedupe_key UNIQUE (dedupe_key),
    CONSTRAINT fk_notification_events_actor FOREIGN KEY (actor_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_notification_events_category_time ON notification_events(category, occurred_at, id);
CREATE INDEX idx_notification_events_source ON notification_events(source_type, source_id, occurred_at, id);

CREATE TABLE notification_dispatch_jobs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL,
    notification_id BIGINT NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    channel VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 3,
    next_run_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_owner VARCHAR(80),
    lease_expires_at DATETIME,
    sent_at DATETIME,
    acked_at DATETIME,
    failed_at DATETIME,
    error_code VARCHAR(40),
    error_message VARCHAR(500),
    payload_json LONGTEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_notification_dispatch_jobs_job_id UNIQUE (job_id),
    CONSTRAINT fk_notification_dispatch_jobs_notification FOREIGN KEY (notification_id) REFERENCES notifications(id),
    CONSTRAINT fk_notification_dispatch_jobs_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_notification_dispatch_jobs_status_run ON notification_dispatch_jobs(status, next_run_at, id);
CREATE INDEX idx_notification_dispatch_jobs_user_channel ON notification_dispatch_jobs(user_id, channel, status, id);

CREATE TABLE notification_dispatch_attempts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id BIGINT NOT NULL,
    attempt_no INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    request_snapshot_json LONGTEXT,
    response_snapshot_json LONGTEXT,
    error_code VARCHAR(40),
    error_message VARCHAR(500),
    latency_ms BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_dispatch_attempts_job FOREIGN KEY (job_id) REFERENCES notification_dispatch_jobs(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_notification_dispatch_attempts_job ON notification_dispatch_attempts(job_id, created_at, id);

CREATE TABLE notification_preferences (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    category VARCHAR(32) NOT NULL,
    inbox_enabled TINYINT(1) NOT NULL DEFAULT 1,
    websocket_enabled TINYINT(1) NOT NULL DEFAULT 1,
    browser_popup_enabled TINYINT(1) NOT NULL DEFAULT 1,
    email_enabled TINYINT(1) NOT NULL DEFAULT 0,
    email_urgency_threshold VARCHAR(20) NOT NULL DEFAULT 'HIGH',
    quiet_hours_json VARCHAR(255),
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_notification_preferences_user_category UNIQUE (user_id, category),
    CONSTRAINT fk_notification_preferences_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_notification_preferences_user ON notification_preferences(user_id, category);
