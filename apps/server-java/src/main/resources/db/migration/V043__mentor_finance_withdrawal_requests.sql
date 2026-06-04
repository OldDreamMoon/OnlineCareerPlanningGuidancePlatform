CREATE TABLE IF NOT EXISTS mentor_withdrawal_requests (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  mentor_user_id BIGINT NOT NULL,
  amount_fen INT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  note VARCHAR(500),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_mentor_withdrawal_requests_user FOREIGN KEY (mentor_user_id) REFERENCES users(id)
);

CREATE INDEX idx_mentor_withdrawal_requests_user_time ON mentor_withdrawal_requests(mentor_user_id, created_at);
