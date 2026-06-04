CREATE INDEX IF NOT EXISTS idx_notification_dispatch_jobs_status_created
    ON notification_dispatch_jobs(status, created_at, id);
