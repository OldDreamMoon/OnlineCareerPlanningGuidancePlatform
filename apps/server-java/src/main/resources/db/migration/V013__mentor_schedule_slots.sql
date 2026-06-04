CREATE TABLE IF NOT EXISTS mentor_schedule_slots (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  mentor_user_id BIGINT NOT NULL,
  start_at TIMESTAMP NOT NULL,
  end_at TIMESTAMP NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
  booked_order_no VARCHAR(64),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_mentor_schedule_slots UNIQUE (mentor_user_id, start_at, end_at)
);

CREATE INDEX idx_mentor_schedule_slots_mentor_time ON mentor_schedule_slots(mentor_user_id, start_at);
CREATE INDEX idx_mentor_schedule_slots_status_time ON mentor_schedule_slots(status, start_at);

ALTER TABLE consult_orders ADD COLUMN appointment_start_at TIMESTAMP NULL;
ALTER TABLE consult_orders ADD COLUMN appointment_end_at TIMESTAMP NULL;
