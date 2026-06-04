SELECT 'users' AS table_name, COUNT(*) AS total FROM users
UNION ALL SELECT 'student_profiles', COUNT(*) FROM student_profiles
UNION ALL SELECT 'mentor_profiles', COUNT(*) FROM mentor_profiles
UNION ALL SELECT 'enterprise_profiles', COUNT(*) FROM enterprise_profiles
UNION ALL SELECT 'posts', COUNT(*) FROM posts
UNION ALL SELECT 'comments', COUNT(*) FROM comments
UNION ALL SELECT 'post_likes', COUNT(*) FROM post_likes
UNION ALL SELECT 'content_reports', COUNT(*) FROM content_reports
UNION ALL SELECT 'content_moderation_events', COUNT(*) FROM content_moderation_events
UNION ALL SELECT 'consult_orders', COUNT(*) FROM consult_orders
UNION ALL SELECT 'payment_records', COUNT(*) FROM payment_records
UNION ALL SELECT 'consult_after_sales_requests', COUNT(*) FROM consult_after_sales_requests
UNION ALL SELECT 'consult_reviews', COUNT(*) FROM consult_reviews
UNION ALL SELECT 'notifications', COUNT(*) FROM notifications
UNION ALL SELECT 'points_ledger', COUNT(*) FROM points_ledger
UNION ALL SELECT 'checkins', COUNT(*) FROM checkins
UNION ALL SELECT 'skill_progress', COUNT(*) FROM skill_progress
UNION ALL SELECT 'student_portrait_snapshots', COUNT(*) FROM student_portrait_snapshots
UNION ALL SELECT 'interview_sessions', COUNT(*) FROM interview_sessions
UNION ALL SELECT 'interview_messages', COUNT(*) FROM interview_messages
UNION ALL SELECT 'ai_call_logs', COUNT(*) FROM ai_call_logs
UNION ALL SELECT 'bounty_tasks', COUNT(*) FROM bounty_tasks
UNION ALL SELECT 'bounty_submissions', COUNT(*) FROM bounty_submissions;

SELECT id, email, role, status, display_name, last_login_at FROM users ORDER BY id;
SELECT order_no, status, amount_fen, student_user_id, mentor_user_id, paid_at FROM consult_orders ORDER BY id;
SELECT order_no, status, request_type, requester_user_id, reviewer_user_id FROM consult_after_sales_requests ORDER BY id;

SELECT table_name, column_name, character_set_name, collation_name
  FROM information_schema.columns
 WHERE table_schema = DATABASE()
   AND table_name IN ('consult_orders', 'consult_after_sales_requests')
   AND column_name IN ('order_no', 'status', 'request_type')
 ORDER BY table_name, column_name;
SELECT id, target_type, status, latest_action, reason_code FROM content_reports ORDER BY id;
SELECT id, trace_id, user_id, task_type, provider, status, error_code FROM ai_call_logs ORDER BY id;

SELECT 'workbench_pending_reports' AS metric, COUNT(*) AS total
  FROM content_reports
 WHERE status = 'PENDING'
UNION ALL
SELECT 'workbench_review_queue_events' AS metric, COUNT(*) AS total
  FROM content_moderation_events
 WHERE action = 'REVIEW'
UNION ALL
SELECT 'workbench_pending_after_sales' AS metric, COUNT(*) AS total
  FROM consult_after_sales_requests
 WHERE status = 'PENDING'
UNION ALL
SELECT 'orders_created_or_paying' AS metric, COUNT(*) AS total
  FROM consult_orders
 WHERE status IN ('CREATED', 'PAYING')
UNION ALL
SELECT 'orders_paid_without_success_record' AS metric, COUNT(*) AS total
  FROM consult_orders co
 WHERE co.status IN ('PAID', 'ANSWERED', 'CLOSED', 'REFUNDED')
   AND NOT EXISTS (
         SELECT 1
           FROM payment_records pr
          WHERE pr.order_no = co.order_no
            AND pr.status = 'SUCCESS'
       );

SELECT status, COUNT(*) AS total
  FROM consult_after_sales_requests
 GROUP BY status
 ORDER BY status;

SELECT status, COUNT(*) AS total
  FROM consult_orders
 GROUP BY status
 ORDER BY status;

SELECT action, COUNT(*) AS total
  FROM content_moderation_events
 GROUP BY action
 ORDER BY action;

SELECT status, COUNT(*) AS total
  FROM content_reports
 GROUP BY status
 ORDER BY status;
