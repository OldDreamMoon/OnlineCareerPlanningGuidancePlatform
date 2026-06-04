ALTER TABLE sensitive_terms
    ALTER COLUMN is_whitelist DROP DEFAULT,
    ALTER COLUMN enabled DROP DEFAULT;

ALTER TABLE sensitive_terms
    ALTER COLUMN is_whitelist TYPE BOOLEAN USING (is_whitelist <> 0),
    ALTER COLUMN enabled TYPE BOOLEAN USING (enabled <> 0);

ALTER TABLE sensitive_terms
    ALTER COLUMN is_whitelist SET DEFAULT FALSE,
    ALTER COLUMN enabled SET DEFAULT TRUE;
