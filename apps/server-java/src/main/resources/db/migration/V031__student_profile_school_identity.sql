-- 学生资料中心学校身份：为同校学生标签与隐私管理补齐学校字段。

ALTER TABLE student_profiles
  ADD COLUMN school_name VARCHAR(150) NULL AFTER job_status,
  ADD COLUMN school_name_key VARCHAR(160) NULL AFTER school_name;

CREATE INDEX idx_student_profiles_school_name_key ON student_profiles(school_name_key);
