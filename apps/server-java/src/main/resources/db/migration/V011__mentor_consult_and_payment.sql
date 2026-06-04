CREATE TABLE IF NOT EXISTS mentor_profiles (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  expertise_tags VARCHAR(512) NULL,
  bio TEXT NULL,
  price_fen INT NOT NULL DEFAULT 5000,
  is_available TINYINT(1) NOT NULL DEFAULT 1,
  approval_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
  total_orders INT NOT NULL DEFAULT 0,
  avg_rating DECIMAL(3, 2) NOT NULL DEFAULT 0.00,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_mentor_profiles_user (user_id),
  CONSTRAINT fk_mentor_profiles_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO mentor_profiles(user_id, expertise_tags, bio, price_fen, is_available, approval_status, total_orders, avg_rating, created_at, updated_at)
SELECT u.id,
       '职业规划,简历诊断',
       CONCAT('导师 ', u.display_name, ' 暂未补充完整简介，当前可用于最小联调演示。'),
       5000,
       1,
       'APPROVED',
       0,
       0.00,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
  FROM users u
  LEFT JOIN mentor_profiles mp ON mp.user_id = u.id
 WHERE u.role = 'MENTOR'
   AND u.is_deleted = 0
   AND mp.user_id IS NULL;

CREATE TABLE IF NOT EXISTS consult_orders (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no VARCHAR(64) NOT NULL,
  student_user_id BIGINT NOT NULL,
  mentor_user_id BIGINT NOT NULL,
  amount_fen INT NOT NULL COMMENT '金额（分）',
  status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
  question_text TEXT NULL,
  paid_at DATETIME NULL,
  closed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_consult_orders_order_no (order_no),
  INDEX idx_consult_orders_student (student_user_id, created_at),
  INDEX idx_consult_orders_mentor (mentor_user_id, created_at),
  CONSTRAINT fk_consult_orders_student FOREIGN KEY (student_user_id) REFERENCES users(id),
  CONSTRAINT fk_consult_orders_mentor FOREIGN KEY (mentor_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS consult_messages (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no VARCHAR(64) NOT NULL,
  sender_user_id BIGINT NOT NULL,
  sender_role VARCHAR(20) NOT NULL,
  message_text TEXT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_consult_messages_order_time (order_no, created_at),
  CONSTRAINT fk_consult_messages_sender FOREIGN KEY (sender_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS payment_records (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no VARCHAR(64) NOT NULL,
  channel VARCHAR(20) NOT NULL COMMENT 'ALIPAY|MOCK',
  mode VARCHAR(20) NOT NULL COMMENT 'SANDBOX|MOCK',
  provider_trade_no VARCHAR(128) NULL,
  amount_fen INT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'INIT',
  idempotency_key VARCHAR(128) NULL,
  raw_callback TEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_payment_records_idempotency (idempotency_key),
  INDEX idx_payment_records_order_time (order_no, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS consult_reviews (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no VARCHAR(64) NOT NULL,
  student_user_id BIGINT NOT NULL,
  mentor_user_id BIGINT NOT NULL,
  rating TINYINT NOT NULL,
  comment TEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_consult_reviews_order_no (order_no),
  INDEX idx_consult_reviews_mentor_time (mentor_user_id, created_at),
  CONSTRAINT fk_consult_reviews_student FOREIGN KEY (student_user_id) REFERENCES users(id),
  CONSTRAINT fk_consult_reviews_mentor FOREIGN KEY (mentor_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
