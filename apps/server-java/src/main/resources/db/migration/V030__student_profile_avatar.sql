-- 学生资料中心头像能力：补齐 MinIO 元数据字段。

ALTER TABLE student_profiles
  ADD COLUMN avatar_bucket VARCHAR(100) NULL AFTER wechat,
  ADD COLUMN avatar_object_key VARCHAR(255) NULL AFTER avatar_bucket,
  ADD COLUMN avatar_content_type VARCHAR(100) NULL AFTER avatar_object_key,
  ADD COLUMN avatar_updated_at DATETIME NULL AFTER avatar_content_type;
