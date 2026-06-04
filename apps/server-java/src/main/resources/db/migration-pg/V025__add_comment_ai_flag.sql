ALTER TABLE comments
    ADD COLUMN IF NOT EXISTS is_ai BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_comments_post_ai_cover
    ON comments(post_id, is_deleted, moderation_status, is_ai);
