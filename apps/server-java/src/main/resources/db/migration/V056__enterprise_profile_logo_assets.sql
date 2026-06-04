ALTER TABLE enterprise_profiles
    ADD COLUMN logo_bucket VARCHAR(100) NULL COMMENT '企业 Logo 存储 bucket',
    ADD COLUMN logo_object_key VARCHAR(255) NULL COMMENT '企业 Logo 对象 key',
    ADD COLUMN logo_content_type VARCHAR(100) NULL COMMENT '企业 Logo 内容类型',
    ADD COLUMN logo_updated_at DATETIME NULL COMMENT '企业 Logo 最近更新时间';
