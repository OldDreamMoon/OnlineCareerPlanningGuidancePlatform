SET @ddl = IF (
    EXISTS(
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'mentor_profiles'
           AND column_name = 'avatar_url'
    ),
    'SELECT 1',
    'ALTER TABLE mentor_profiles ADD COLUMN avatar_url VARCHAR(255) NULL AFTER job_title'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF (
    EXISTS(
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'mentor_profiles'
           AND column_name = 'service_scenes'
    ),
    'SELECT 1',
    'ALTER TABLE mentor_profiles ADD COLUMN service_scenes VARCHAR(512) NULL AFTER expertise_tags'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE mentor_profiles
   SET service_scenes = '简历诊断,项目表达,模拟面试复盘'
 WHERE service_scenes IS NULL
    OR TRIM(service_scenes) = '';

CREATE TABLE IF NOT EXISTS mentor_favorites (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_user_id BIGINT NOT NULL,
  mentor_user_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_mentor_favorites_student_mentor (student_user_id, mentor_user_id),
  KEY idx_mentor_favorites_student_time (student_user_id, created_at),
  KEY idx_mentor_favorites_mentor_time (mentor_user_id, created_at),
  CONSTRAINT fk_mentor_favorites_student FOREIGN KEY (student_user_id) REFERENCES users(id),
  CONSTRAINT fk_mentor_favorites_mentor FOREIGN KEY (mentor_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
