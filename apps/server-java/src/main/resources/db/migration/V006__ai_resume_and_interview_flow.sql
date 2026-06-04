CREATE TABLE IF NOT EXISTS interview_sessions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(64) NOT NULL,
    student_user_id BIGINT NOT NULL,
    target_role VARCHAR(100) NOT NULL,
    mode VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_interview_sessions_session_id UNIQUE (session_id),
    CONSTRAINT fk_interview_sessions_user FOREIGN KEY (student_user_id) REFERENCES users(id)
);

CREATE INDEX idx_interview_sessions_user_time ON interview_sessions(student_user_id, created_at);

CREATE TABLE IF NOT EXISTS interview_messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_pk BIGINT NOT NULL,
    sender_role VARCHAR(20) NOT NULL,
    message_text TEXT NOT NULL,
    score_hint INT NULL,
    audio_object_key VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_interview_messages_session FOREIGN KEY (session_pk) REFERENCES interview_sessions(id)
);

CREATE INDEX idx_interview_messages_session_time ON interview_messages(session_pk, created_at);
