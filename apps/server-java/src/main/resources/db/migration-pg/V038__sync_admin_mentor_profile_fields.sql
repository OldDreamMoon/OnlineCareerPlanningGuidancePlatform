ALTER TABLE mentor_profiles
    ADD COLUMN IF NOT EXISTS company_name VARCHAR(200);

ALTER TABLE mentor_profiles
    ADD COLUMN IF NOT EXISTS job_title VARCHAR(100);

ALTER TABLE mentor_profiles
    ADD COLUMN IF NOT EXISTS expertise_tags VARCHAR(512);

ALTER TABLE mentor_profiles
    ADD COLUMN IF NOT EXISTS service_scenes VARCHAR(512);

ALTER TABLE mentor_profiles
    ADD COLUMN IF NOT EXISTS price_fen INTEGER NOT NULL DEFAULT 5000;

ALTER TABLE mentor_profiles
    ADD COLUMN IF NOT EXISTS is_available BOOLEAN NOT NULL DEFAULT TRUE;
