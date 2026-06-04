ALTER TABLE enterprise_profiles
    ADD COLUMN IF NOT EXISTS company_name VARCHAR(200),
    ADD COLUMN IF NOT EXISTS industry VARCHAR(100),
    ADD COLUMN IF NOT EXISTS company_size VARCHAR(50),
    ADD COLUMN IF NOT EXISTS hiring_tags VARCHAR(512),
    ADD COLUMN IF NOT EXISTS contact_title VARCHAR(100),
    ADD COLUMN IF NOT EXISTS bio TEXT,
    ADD COLUMN IF NOT EXISTS external_links TEXT,
    ADD COLUMN IF NOT EXISTS preferences TEXT,
    ADD COLUMN IF NOT EXISTS logo_bucket VARCHAR(100),
    ADD COLUMN IF NOT EXISTS logo_object_key VARCHAR(255),
    ADD COLUMN IF NOT EXISTS logo_content_type VARCHAR(100),
    ADD COLUMN IF NOT EXISTS logo_updated_at TIMESTAMPTZ;

UPDATE enterprise_profiles
   SET company_name = COALESCE(company_name, '待补充'),
       industry = COALESCE(industry, '待补充'),
       company_size = COALESCE(company_size, '待补充'),
       hiring_tags = COALESCE(hiring_tags, '校招,实习'),
       contact_title = COALESCE(contact_title, '待补充')
 WHERE company_name IS NULL
    OR industry IS NULL
    OR company_size IS NULL
    OR hiring_tags IS NULL
    OR contact_title IS NULL;
