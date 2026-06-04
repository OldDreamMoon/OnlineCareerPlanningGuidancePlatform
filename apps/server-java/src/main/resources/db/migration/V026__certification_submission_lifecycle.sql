CREATE TABLE IF NOT EXISTS certification_submissions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  user_role VARCHAR(20) NOT NULL,
  real_name VARCHAR(100) NOT NULL,
  company_name VARCHAR(200) NULL,
  job_title VARCHAR(100) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  review_note VARCHAR(1000) NULL,
  reviewed_by BIGINT NULL,
  reviewed_at DATETIME NULL,
  previous_submission_id BIGINT NULL,
  is_current TINYINT(1) NOT NULL DEFAULT 1,
  submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_certification_submissions_user (user_id, is_current, submitted_at),
  KEY idx_certification_submissions_status (status, submitted_at),
  CONSTRAINT fk_certification_submissions_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_certification_submissions_reviewer FOREIGN KEY (reviewed_by) REFERENCES users(id),
  CONSTRAINT fk_certification_submissions_previous FOREIGN KEY (previous_submission_id) REFERENCES certification_submissions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS certification_submission_assets (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  submission_id BIGINT NOT NULL,
  storage_bucket VARCHAR(100) NOT NULL,
  object_key VARCHAR(255) NOT NULL,
  original_filename VARCHAR(255) NOT NULL,
  content_type VARCHAR(100) NULL,
  size_bytes BIGINT NOT NULL,
  lifecycle_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  delete_reason VARCHAR(100) NULL,
  deleted_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_certification_assets_object_key (object_key),
  KEY idx_certification_assets_submission (submission_id, lifecycle_status),
  CONSTRAINT fk_certification_assets_submission FOREIGN KEY (submission_id) REFERENCES certification_submissions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE mentor_profiles
  MODIFY COLUMN approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

ALTER TABLE enterprise_profiles
  MODIFY COLUMN approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
