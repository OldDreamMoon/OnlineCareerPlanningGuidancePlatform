CREATE TABLE IF NOT EXISTS bounty_tasks (
  id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
  enterprise_user_id     BIGINT       NOT NULL,
  title                  VARCHAR(200) NOT NULL,
  description            TEXT         NOT NULL,
  reward_description     VARCHAR(255) NOT NULL,
  status                 VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
  accepted_submission_id BIGINT       NULL,
  deadline_at            DATETIME     NULL,
  closed_at              DATETIME     NULL,
  created_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_bounty_tasks_enterprise FOREIGN KEY (enterprise_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_bounty_tasks_enterprise_time ON bounty_tasks(enterprise_user_id, created_at);
CREATE INDEX idx_bounty_tasks_status_time ON bounty_tasks(status, created_at);

CREATE TABLE IF NOT EXISTS bounty_submissions (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  task_id          BIGINT        NOT NULL,
  student_user_id  BIGINT        NOT NULL,
  content_text     TEXT          NULL,
  attachment_links VARCHAR(1000) NULL,
  status           VARCHAR(20)   NOT NULL DEFAULT 'SUBMITTED',
  review_comment   VARCHAR(500)  NULL,
  reviewed_at      DATETIME      NULL,
  reviewer_user_id BIGINT        NULL,
  created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uq_bounty_submissions_task_student UNIQUE (task_id, student_user_id),
  CONSTRAINT fk_bounty_submissions_task FOREIGN KEY (task_id) REFERENCES bounty_tasks(id),
  CONSTRAINT fk_bounty_submissions_student FOREIGN KEY (student_user_id) REFERENCES users(id),
  CONSTRAINT fk_bounty_submissions_reviewer FOREIGN KEY (reviewer_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_bounty_submissions_task_time ON bounty_submissions(task_id, created_at);
CREATE INDEX idx_bounty_submissions_student_time ON bounty_submissions(student_user_id, created_at);
CREATE INDEX idx_bounty_submissions_status_time ON bounty_submissions(status, created_at);

ALTER TABLE bounty_tasks
  ADD CONSTRAINT fk_bounty_tasks_accepted_submission FOREIGN KEY (accepted_submission_id) REFERENCES bounty_submissions(id);
