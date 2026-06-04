ALTER TABLE bounty_submissions
    ADD COLUMN contact_intent VARCHAR(80) NULL COMMENT '企业继续接触意图',
    ADD COLUMN reject_template VARCHAR(160) NULL COMMENT '企业未入选模板',
    ADD COLUMN review_note TEXT NULL COMMENT '企业审核补充说明';

CREATE TABLE IF NOT EXISTS bounty_submission_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    submission_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    actor_user_id BIGINT NULL,
    event_type VARCHAR(40) NOT NULL,
    comment_text VARCHAR(500) NULL,
    contact_intent VARCHAR(80) NULL,
    reject_template VARCHAR(160) NULL,
    note TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bounty_submission_events_submission FOREIGN KEY (submission_id) REFERENCES bounty_submissions(id),
    CONSTRAINT fk_bounty_submission_events_task FOREIGN KEY (task_id) REFERENCES bounty_tasks(id),
    CONSTRAINT fk_bounty_submission_events_actor FOREIGN KEY (actor_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_bounty_submission_events_submission_time ON bounty_submission_events(submission_id, created_at, id);
CREATE INDEX idx_bounty_submission_events_task_time ON bounty_submission_events(task_id, created_at, id);

INSERT INTO bounty_submission_events (
    submission_id,
    task_id,
    actor_user_id,
    event_type,
    note,
    created_at
)
SELECT bs.id,
       bs.task_id,
       bs.student_user_id,
       'SUBMITTED',
       CASE
           WHEN bs.content_text IS NULL OR CHAR_LENGTH(TRIM(bs.content_text)) = 0 THEN NULL
           ELSE LEFT(TRIM(bs.content_text), 255)
       END,
       bs.created_at
FROM bounty_submissions bs;

INSERT INTO bounty_submission_events (
    submission_id,
    task_id,
    actor_user_id,
    event_type,
    comment_text,
    created_at
)
SELECT bs.id,
       bs.task_id,
       bs.reviewer_user_id,
       CASE
           WHEN bs.status = 'ACCEPTED' THEN 'CONTACT_SENT'
           ELSE 'REJECT_SENT'
       END,
       bs.review_comment,
       bs.reviewed_at
FROM bounty_submissions bs
WHERE bs.status IN ('ACCEPTED', 'REJECTED')
  AND bs.reviewed_at IS NOT NULL;

INSERT INTO bounty_submission_events (
    submission_id,
    task_id,
    actor_user_id,
    event_type,
    note,
    created_at
)
SELECT bs.id,
       bt.id,
       bt.enterprise_user_id,
       'TASK_CLOSED_AFTER_ACCEPT',
       '任务已结束，当前结果留痕已固定在工作区中。',
       bt.closed_at
FROM bounty_tasks bt
JOIN bounty_submissions bs
  ON bs.id = bt.accepted_submission_id
WHERE bt.accepted_submission_id IS NOT NULL
  AND bt.closed_at IS NOT NULL;
