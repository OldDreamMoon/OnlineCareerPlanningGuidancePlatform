ALTER TABLE enterprise_profiles
    ADD COLUMN bio TEXT NULL COMMENT '企业简介',
    ADD COLUMN external_links TEXT NULL COMMENT '企业联系方式与外部链接',
    ADD COLUMN preferences TEXT NULL COMMENT '企业招募偏好与学生提示';
