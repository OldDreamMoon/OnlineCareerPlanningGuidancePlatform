ALTER TABLE consult_orders
    ADD COLUMN IF NOT EXISTS review_rating SMALLINT,
    ADD COLUMN IF NOT EXISTS review_comment TEXT,
    ADD COLUMN IF NOT EXISTS review_created_at TIMESTAMPTZ;

UPDATE consult_orders AS orders
   SET review_rating = reviews.rating,
       review_comment = reviews.comment,
       review_created_at = reviews.created_at
  FROM consult_reviews AS reviews
 WHERE reviews.order_no = orders.order_no;

CREATE INDEX IF NOT EXISTS idx_consult_orders_mentor_review_time
    ON consult_orders (mentor_user_id, review_created_at DESC)
    WHERE review_rating IS NOT NULL;

DROP TABLE IF EXISTS consult_reviews;
