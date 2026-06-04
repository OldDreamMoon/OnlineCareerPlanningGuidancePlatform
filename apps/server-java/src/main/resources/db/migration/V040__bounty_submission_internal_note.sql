ALTER TABLE bounty_submissions
    ADD COLUMN internal_note TEXT NULL COMMENT '企业内部审核备注，仅企业可见';
