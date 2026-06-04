CREATE TABLE IF NOT EXISTS mentor_service_packages (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  mentor_user_id BIGINT NOT NULL,
  package_name VARCHAR(40) NOT NULL,
  scene_code VARCHAR(60) NOT NULL,
  scene_label VARCHAR(30) NOT NULL,
  delivery_mode VARCHAR(20) NOT NULL,
  duration_minutes INT NULL,
  price_fen INT NOT NULL DEFAULT 0,
  description VARCHAR(240) NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  sort_no INT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_mentor_service_packages_mentor FOREIGN KEY (mentor_user_id) REFERENCES users(id),
  CONSTRAINT uq_mentor_service_packages_sort UNIQUE (mentor_user_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_mentor_service_packages_enabled ON mentor_service_packages(mentor_user_id, enabled, sort_no);

SET @ddl = IF (
    EXISTS(
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'consult_orders'
           AND column_name = 'service_package_snapshot_json'
    ),
    'SELECT 1',
    'ALTER TABLE consult_orders ADD COLUMN service_package_snapshot_json TEXT NULL AFTER prep_sheet_snapshot_json'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO mentor_service_packages(
    mentor_user_id,
    package_name,
    scene_code,
    scene_label,
    delivery_mode,
    duration_minutes,
    price_fen,
    description,
    enabled,
    sort_no,
    created_at,
    updated_at
)
SELECT mp.user_id,
       '标准图文咨询',
       CASE COALESCE(NULLIF(SUBSTRING_INDEX(COALESCE(mp.service_scenes, ''), ',', 1), ''), '简历诊断')
           WHEN '项目表达' THEN 'PROJECT_STORYTELLING'
           WHEN '模拟面试复盘' THEN 'MOCK_INTERVIEW_REVIEW'
           WHEN '岗位方向选择' THEN 'CAREER_DIRECTION'
           WHEN '校招投递策略' THEN 'CAMPUS_RECRUITMENT_STRATEGY'
           WHEN '转行 / 跨专业求职' THEN 'CAREER_TRANSITION'
           WHEN 'Offer 对比与决策' THEN 'OFFER_DECISION'
           ELSE 'RESUME_DIAGNOSIS'
       END AS scene_code,
       COALESCE(NULLIF(SUBSTRING_INDEX(COALESCE(mp.service_scenes, ''), ',', 1), ''), '简历诊断') AS scene_label,
       'TEXT_ASYNC',
       NULL,
       CASE WHEN mp.price_fen < 0 THEN 0 ELSE mp.price_fen END,
       '适合先整理问题与材料，由导师给出正式文字建议和下一步行动方向。',
       1,
       1,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
  FROM mentor_profiles mp
  JOIN users u ON u.id = mp.user_id AND u.role = 'MENTOR'
 WHERE NOT EXISTS (
           SELECT 1
             FROM mentor_service_packages existing
            WHERE existing.mentor_user_id = mp.user_id
       );
