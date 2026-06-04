-- 学生账号与资料中心：补齐资料字段、隐私矩阵持久化。

ALTER TABLE student_profiles
  ADD COLUMN job_status VARCHAR(50) NULL AFTER user_id,
  ADD COLUMN gpa VARCHAR(50) NULL AFTER grade,
  ADD COLUMN honors TEXT NULL AFTER target_position,
  ADD COLUMN github VARCHAR(255) NULL AFTER honors,
  ADD COLUMN portfolio VARCHAR(255) NULL AFTER github,
  ADD COLUMN phone VARCHAR(50) NULL AFTER portfolio,
  ADD COLUMN wechat VARCHAR(100) NULL AFTER phone;

CREATE TABLE IF NOT EXISTS student_profile_privacy_settings (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_user_id BIGINT      NOT NULL,
  settings_json   JSON        NOT NULL,
  created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_student_profile_privacy_user (student_user_id),
  CONSTRAINT fk_student_profile_privacy_user FOREIGN KEY (student_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
