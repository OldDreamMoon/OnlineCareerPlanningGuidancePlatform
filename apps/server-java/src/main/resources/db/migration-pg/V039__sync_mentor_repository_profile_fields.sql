ALTER TABLE mentor_profiles
    ADD COLUMN IF NOT EXISTS avatar_bucket VARCHAR(100);

ALTER TABLE mentor_profiles
    ADD COLUMN IF NOT EXISTS avatar_content_type VARCHAR(100);

ALTER TABLE mentor_profiles
    ADD COLUMN IF NOT EXISTS bio TEXT;

ALTER TABLE mentor_profiles
    ADD COLUMN IF NOT EXISTS suitable_for TEXT;

ALTER TABLE mentor_profiles
    ADD COLUMN IF NOT EXISTS not_suitable_for TEXT;

ALTER TABLE mentor_profiles
    ADD COLUMN IF NOT EXISTS prep_materials TEXT;

ALTER TABLE mentor_profiles
    ADD COLUMN IF NOT EXISTS reply_rhythm TEXT;

ALTER TABLE mentor_profiles
    ADD COLUMN IF NOT EXISTS total_orders INTEGER NOT NULL DEFAULT 0;

UPDATE mentor_profiles
   SET expertise_tags = '职业规划,简历诊断'
 WHERE expertise_tags IS NULL
    OR btrim(expertise_tags) = '';

UPDATE mentor_profiles
   SET service_scenes = '简历诊断,项目表达,模拟面试复盘'
 WHERE service_scenes IS NULL
    OR btrim(service_scenes) = '';

UPDATE mentor_profiles profile
   SET bio = '导师 ' || usr.display_name || ' 还没有补充完整简介，建议尽快完善个人介绍与服务说明。'
  FROM users usr
 WHERE profile.user_id = usr.id
   AND (profile.bio IS NULL OR btrim(profile.bio) = '');
