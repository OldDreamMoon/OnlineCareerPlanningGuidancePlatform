CREATE TABLE IF NOT EXISTS enterprise_profiles (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  company_name VARCHAR(200) NULL,
  industry VARCHAR(100) NULL,
  company_size VARCHAR(50) NULL,
  hiring_tags VARCHAR(512) NULL,
  approval_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_enterprise_profiles_user (user_id),
  CONSTRAINT fk_enterprise_profiles_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO enterprise_profiles(user_id, company_name, industry, company_size, hiring_tags, approval_status, created_at, updated_at)
SELECT u.id,
       CONCAT(u.display_name, ' 企业账号'),
       '待补充',
       '待补充',
       '校招,实习',
       'APPROVED',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
  FROM users u
  LEFT JOIN enterprise_profiles ep ON ep.user_id = u.id
 WHERE u.role = 'ENTERPRISE'
   AND u.is_deleted = 0
   AND ep.user_id IS NULL;
