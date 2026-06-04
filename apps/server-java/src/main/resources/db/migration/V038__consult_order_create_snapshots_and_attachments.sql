ALTER TABLE consult_orders
    ADD COLUMN scene_code VARCHAR(60) NULL,
    ADD COLUMN source_page VARCHAR(80) NULL,
    ADD COLUMN question_payload_json TEXT NULL,
    ADD COLUMN problem_summary TEXT NULL,
    ADD COLUMN core_questions_json TEXT NULL,
    ADD COLUMN expected_outcomes_json TEXT NULL,
    ADD COLUMN selected_material_types VARCHAR(255) NULL,
    ADD COLUMN prep_sheet_snapshot_json TEXT NULL;

CREATE TABLE IF NOT EXISTS consult_order_attachments (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no VARCHAR(64) NOT NULL,
  uploaded_by_user_id BIGINT NOT NULL,
  attachment_type VARCHAR(40) NOT NULL,
  slot_code VARCHAR(60) NOT NULL,
  source_stage VARCHAR(30) NOT NULL DEFAULT 'ORDER_CREATE',
  original_filename VARCHAR(255) NOT NULL,
  content_type VARCHAR(120) NOT NULL,
  size_bytes BIGINT NOT NULL,
  storage_bucket VARCHAR(120) NOT NULL,
  object_key VARCHAR(255) NOT NULL,
  description VARCHAR(255) NULL,
  lifecycle_status VARCHAR(20) NOT NULL DEFAULT 'CURRENT',
  replaced_attachment_id BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_consult_order_attachments_order_time (order_no, created_at),
  INDEX idx_consult_order_attachments_order_lifecycle (order_no, lifecycle_status),
  INDEX idx_consult_order_attachments_slot (order_no, slot_code, lifecycle_status),
  CONSTRAINT fk_consult_order_attachments_uploader FOREIGN KEY (uploaded_by_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
