ALTER TABLE mentor_profiles
    ADD COLUMN IF NOT EXISTS show_real_name BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE mentor_profiles
    ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(255);

ALTER TABLE mentor_profiles
    ADD COLUMN IF NOT EXISTS avatar_object_key VARCHAR(255);

ALTER TABLE mentor_profiles
    ADD COLUMN IF NOT EXISTS avatar_updated_at TIMESTAMPTZ;

ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS title VARCHAR(200);

ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS content TEXT;

ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS tags VARCHAR(255);

ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS scenario_code VARCHAR(60) NOT NULL DEFAULT 'GENERAL_HELP';

ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS resolved_status VARCHAR(20) NOT NULL DEFAULT 'OPEN';

ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS risk_level VARCHAR(20) NOT NULL DEFAULT 'LOW';

ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS last_moderation_event_id BIGINT;

ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE posts
   SET title = COALESCE(title, '')
 WHERE title IS NULL;

UPDATE posts
   SET content = COALESCE(content, '')
 WHERE content IS NULL;

UPDATE posts
   SET updated_at = COALESCE(updated_at, created_at, CURRENT_TIMESTAMP)
 WHERE updated_at IS NULL;

ALTER TABLE posts
    ALTER COLUMN title SET NOT NULL;

ALTER TABLE posts
    ALTER COLUMN content SET NOT NULL;

ALTER TABLE comments
    ADD COLUMN IF NOT EXISTS content TEXT;

ALTER TABLE comments
    ADD COLUMN IF NOT EXISTS risk_level VARCHAR(20) NOT NULL DEFAULT 'LOW';

ALTER TABLE comments
    ADD COLUMN IF NOT EXISTS last_moderation_event_id BIGINT;

ALTER TABLE comments
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE comments
   SET content = COALESCE(content, '')
 WHERE content IS NULL;

UPDATE comments
   SET updated_at = COALESCE(updated_at, created_at, CURRENT_TIMESTAMP)
 WHERE updated_at IS NULL;

ALTER TABLE comments
    ALTER COLUMN content SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_posts_user_created
    ON posts(user_id, created_at);

CREATE INDEX IF NOT EXISTS idx_posts_moderation_created
    ON posts(moderation_status, created_at);

CREATE INDEX IF NOT EXISTS idx_posts_scenario_created
    ON posts(scenario_code, created_at);

CREATE INDEX IF NOT EXISTS idx_posts_resolved_created
    ON posts(resolved_status, created_at);

CREATE INDEX IF NOT EXISTS idx_comments_post_created
    ON comments(post_id, created_at);

CREATE INDEX IF NOT EXISTS idx_comments_user_created
    ON comments(user_id, created_at);

CREATE INDEX IF NOT EXISTS idx_comments_moderation_created
    ON comments(moderation_status, created_at);

CREATE INDEX IF NOT EXISTS idx_post_likes_user_created
    ON post_likes(user_id, created_at);

CREATE INDEX IF NOT EXISTS idx_post_likes_post_created
    ON post_likes(post_id, created_at);
