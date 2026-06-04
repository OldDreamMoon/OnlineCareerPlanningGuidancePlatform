-- 学生资料中心安全验证码持久化，支持刷新页面或单实例重启后的继续校验。

CREATE TABLE IF NOT EXISTS student_profile_security_codes (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_user_id BIGINT       NOT NULL,
  purpose         VARCHAR(64)  NOT NULL,
  target_email    VARCHAR(255) NOT NULL,
  code_hash       CHAR(64)     NOT NULL,
  expires_at      DATETIME     NOT NULL,
  next_send_at    DATETIME     NOT NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_student_profile_security_code (student_user_id, purpose, target_email),
  KEY idx_student_profile_security_expires_at (expires_at),
  CONSTRAINT fk_student_profile_security_user FOREIGN KEY (student_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
