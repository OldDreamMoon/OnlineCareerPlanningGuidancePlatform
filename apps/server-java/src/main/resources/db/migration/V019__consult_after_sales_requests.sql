CREATE TABLE consult_after_sales_requests (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    requester_user_id BIGINT NOT NULL,
    request_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reason TEXT NOT NULL,
    review_note TEXT,
    reviewer_user_id BIGINT,
    auto_triggered BOOLEAN NOT NULL DEFAULT FALSE,
    reviewed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_consult_after_sales_requester FOREIGN KEY (requester_user_id) REFERENCES users(id),
    CONSTRAINT fk_consult_after_sales_reviewer FOREIGN KEY (reviewer_user_id) REFERENCES users(id)
);

CREATE INDEX idx_consult_after_sales_order_time ON consult_after_sales_requests(order_no, created_at);
CREATE INDEX idx_consult_after_sales_status_time ON consult_after_sales_requests(status, created_at);
CREATE INDEX idx_consult_after_sales_requester_time ON consult_after_sales_requests(requester_user_id, created_at);
