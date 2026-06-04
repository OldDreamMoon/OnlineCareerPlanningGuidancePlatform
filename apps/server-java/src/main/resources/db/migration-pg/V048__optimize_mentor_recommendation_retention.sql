CREATE INDEX IF NOT EXISTS idx_mentor_recommendation_runs_created
    ON mentor_recommendation_runs(created_at, id);
