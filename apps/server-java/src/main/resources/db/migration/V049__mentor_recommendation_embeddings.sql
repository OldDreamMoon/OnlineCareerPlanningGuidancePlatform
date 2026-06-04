CREATE TABLE IF NOT EXISTS student_recommendation_snapshots (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_user_id BIGINT NOT NULL,
  content_text TEXT NOT NULL,
  target_position VARCHAR(100) NULL,
  skill_tags_json TEXT NULL,
  portrait_tags_json TEXT NULL,
  latest_resume_record_id BIGINT NULL,
  latest_resume_target_role VARCHAR(100) NULL,
  latest_resume_summary TEXT NULL,
  latest_resume_suggestions_json TEXT NULL,
  latest_interview_session_id VARCHAR(64) NULL,
  latest_interview_target_role VARCHAR(100) NULL,
  latest_interview_weaknesses_json TEXT NULL,
  latest_interview_suggestions_json TEXT NULL,
  signal_flags_json TEXT NULL,
  content_hash CHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_student_recommendation_snapshots_student (student_user_id),
  KEY idx_student_recommendation_snapshots_hash (content_hash),
  CONSTRAINT fk_student_recommendation_snapshots_student FOREIGN KEY (student_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS mentor_recommendation_snapshots (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  mentor_user_id BIGINT NOT NULL,
  content_text TEXT NOT NULL,
  expertise_tags_json TEXT NULL,
  service_scenes_json TEXT NULL,
  quality_score INT NOT NULL DEFAULT 0,
  price_fen INT NOT NULL DEFAULT 0,
  is_available TINYINT(1) NOT NULL DEFAULT 1,
  content_hash CHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_mentor_recommendation_snapshots_mentor (mentor_user_id),
  KEY idx_mentor_recommendation_snapshots_hash (content_hash),
  CONSTRAINT fk_mentor_recommendation_snapshots_mentor FOREIGN KEY (mentor_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS recommendation_embedding_vectors (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  entity_type VARCHAR(30) NOT NULL,
  entity_id BIGINT NOT NULL,
  model_code VARCHAR(60) NOT NULL,
  vector_dim INT NOT NULL,
  vector_json LONGTEXT NOT NULL,
  content_hash CHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_recommendation_embedding_vectors_entity (entity_type, entity_id, model_code),
  KEY idx_recommendation_embedding_vectors_hash (content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS mentor_recommendation_runs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_user_id BIGINT NOT NULL,
  scene VARCHAR(60) NULL,
  keyword VARCHAR(255) NULL,
  expertise VARCHAR(255) NULL,
  filter_payload_json TEXT NOT NULL,
  recall_model_code VARCHAR(60) NOT NULL,
  rerank_version VARCHAR(60) NOT NULL,
  weak_signal TINYINT(1) NOT NULL DEFAULT 0,
  candidate_count INT NOT NULL DEFAULT 0,
  recalled_count INT NOT NULL DEFAULT 0,
  top_mentor_user_ids_json TEXT NOT NULL,
  basis_summary TEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_mentor_recommendation_runs_student_time (student_user_id, created_at),
  CONSTRAINT fk_mentor_recommendation_runs_student FOREIGN KEY (student_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS mentor_recommendation_events (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  run_id BIGINT NOT NULL,
  mentor_user_id BIGINT NOT NULL,
  event_type VARCHAR(30) NOT NULL,
  stage_rank INT NULL,
  score DECIMAL(10, 6) NULL,
  detail_json TEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_mentor_recommendation_events_run (run_id, created_at),
  KEY idx_mentor_recommendation_events_mentor (mentor_user_id, created_at),
  CONSTRAINT fk_mentor_recommendation_events_run FOREIGN KEY (run_id) REFERENCES mentor_recommendation_runs(id),
  CONSTRAINT fk_mentor_recommendation_events_mentor FOREIGN KEY (mentor_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
