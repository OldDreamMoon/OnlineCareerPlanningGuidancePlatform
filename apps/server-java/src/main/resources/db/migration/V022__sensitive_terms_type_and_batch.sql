ALTER TABLE sensitive_terms
    ADD COLUMN term_type VARCHAR(50) NOT NULL DEFAULT 'OTHER' AFTER term;

UPDATE sensitive_terms
   SET term_type = 'OTHER'
 WHERE term_type IS NULL
    OR term_type = '';

CREATE INDEX idx_sensitive_terms_type_enabled ON sensitive_terms(term_type, enabled, is_whitelist);
