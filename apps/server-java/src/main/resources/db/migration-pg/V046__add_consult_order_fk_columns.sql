ALTER TABLE consult_messages
    ADD COLUMN IF NOT EXISTS order_id BIGINT;

UPDATE consult_messages AS message
   SET order_id = orders.id
  FROM consult_orders AS orders
 WHERE message.order_id IS NULL
   AND orders.order_no = message.order_no;

ALTER TABLE consult_messages
    DROP CONSTRAINT IF EXISTS fk_consult_messages_order;

ALTER TABLE consult_messages
    ADD CONSTRAINT fk_consult_messages_order
        FOREIGN KEY (order_id) REFERENCES consult_orders(id);

CREATE INDEX IF NOT EXISTS idx_consult_messages_order_id_time
    ON consult_messages(order_id, created_at);

ALTER TABLE consult_order_attachments
    ADD COLUMN IF NOT EXISTS order_id BIGINT;

UPDATE consult_order_attachments AS attachment
   SET order_id = orders.id
  FROM consult_orders AS orders
 WHERE attachment.order_id IS NULL
   AND orders.order_no = attachment.order_no;

ALTER TABLE consult_order_attachments
    DROP CONSTRAINT IF EXISTS fk_consult_order_attachments_order;

ALTER TABLE consult_order_attachments
    ADD CONSTRAINT fk_consult_order_attachments_order
        FOREIGN KEY (order_id) REFERENCES consult_orders(id);

CREATE INDEX IF NOT EXISTS idx_consult_order_attachments_order_id_time
    ON consult_order_attachments(order_id, created_at);

CREATE INDEX IF NOT EXISTS idx_consult_order_attachments_order_id_lifecycle
    ON consult_order_attachments(order_id, lifecycle_status);

CREATE INDEX IF NOT EXISTS idx_consult_order_attachments_order_id_slot
    ON consult_order_attachments(order_id, slot_code, lifecycle_status);

ALTER TABLE consult_after_sales_requests
    ADD COLUMN IF NOT EXISTS order_id BIGINT;

UPDATE consult_after_sales_requests AS request
   SET order_id = orders.id
  FROM consult_orders AS orders
 WHERE request.order_id IS NULL
   AND orders.order_no = request.order_no;

ALTER TABLE consult_after_sales_requests
    DROP CONSTRAINT IF EXISTS fk_consult_after_sales_order;

ALTER TABLE consult_after_sales_requests
    ADD CONSTRAINT fk_consult_after_sales_order
        FOREIGN KEY (order_id) REFERENCES consult_orders(id);

CREATE INDEX IF NOT EXISTS idx_consult_after_sales_order_id_time
    ON consult_after_sales_requests(order_id, created_at);

ALTER TABLE payment_records
    ADD COLUMN IF NOT EXISTS order_id BIGINT;

UPDATE payment_records AS payment
   SET order_id = orders.id
  FROM consult_orders AS orders
 WHERE payment.order_id IS NULL
   AND orders.order_no = payment.order_no;

ALTER TABLE payment_records
    DROP CONSTRAINT IF EXISTS fk_payment_records_order;

ALTER TABLE payment_records
    ADD CONSTRAINT fk_payment_records_order
        FOREIGN KEY (order_id) REFERENCES consult_orders(id);

CREATE INDEX IF NOT EXISTS idx_payment_records_order_id_time
    ON payment_records(order_id, created_at);

ALTER TABLE mentor_schedule_slots
    ADD COLUMN IF NOT EXISTS booked_order_id BIGINT;

UPDATE mentor_schedule_slots AS slot
   SET booked_order_id = orders.id
  FROM consult_orders AS orders
 WHERE slot.booked_order_id IS NULL
   AND slot.booked_order_no IS NOT NULL
   AND orders.order_no = slot.booked_order_no;

ALTER TABLE mentor_schedule_slots
    DROP CONSTRAINT IF EXISTS fk_mentor_schedule_slots_booked_order;

ALTER TABLE mentor_schedule_slots
    ADD CONSTRAINT fk_mentor_schedule_slots_booked_order
        FOREIGN KEY (booked_order_id) REFERENCES consult_orders(id);

CREATE INDEX IF NOT EXISTS idx_mentor_schedule_slots_booked_order_time
    ON mentor_schedule_slots(booked_order_id, status, start_at);
