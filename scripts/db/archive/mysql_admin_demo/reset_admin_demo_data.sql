SET NAMES utf8mb4;

SET @seed_now = NOW();
SET @seed_password_hash = (SELECT password_hash FROM users WHERE id = 1 LIMIT 1);
SET @slot_o1_start = TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '20:30:00');
SET @slot_o1_end = TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '21:30:00');
SET @slot_o2_start = TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '19:00:00');
SET @slot_o2_end = TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '20:00:00');
SET @slot_o3_start = TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 4 DAY), '20:00:00');
SET @slot_o3_end = TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 4 DAY), '21:00:00');
SET @slot_o4_start = TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '20:00:00');
SET @slot_o4_end = TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '21:00:00');
SET @slot_o5_start = TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 5 DAY), '19:00:00');
SET @slot_o5_end = TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 5 DAY), '20:00:00');
SET @slot_o6_start = TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 11 DAY), '20:00:00');
SET @slot_o6_end = TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 11 DAY), '21:00:00');
SET @slot_o7_start = TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '14:00:00');
SET @slot_o7_end = TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '15:00:00');

SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE bounty_submissions;
TRUNCATE TABLE bounty_tasks;
TRUNCATE TABLE consult_reviews;
TRUNCATE TABLE consult_messages;
TRUNCATE TABLE consult_after_sales_requests;
TRUNCATE TABLE payment_records;
TRUNCATE TABLE mentor_schedule_slots;
TRUNCATE TABLE consult_orders;
TRUNCATE TABLE notifications;
TRUNCATE TABLE content_report_actions;
TRUNCATE TABLE content_reports;
TRUNCATE TABLE content_moderation_events;
TRUNCATE TABLE comments;
TRUNCATE TABLE post_likes;
TRUNCATE TABLE posts;
TRUNCATE TABLE student_portrait_snapshots;
TRUNCATE TABLE skill_progress;
TRUNCATE TABLE checkins;
TRUNCATE TABLE points_ledger;
TRUNCATE TABLE interview_messages;
TRUNCATE TABLE interview_sessions;
TRUNCATE TABLE ai_call_logs;
TRUNCATE TABLE audit_logs;
TRUNCATE TABLE student_profiles;
TRUNCATE TABLE mentor_profiles;
TRUNCATE TABLE enterprise_profiles;
DELETE FROM users WHERE role <> 'ADMIN';
ALTER TABLE users AUTO_INCREMENT = 2;

SET FOREIGN_KEY_CHECKS = 1;

UPDATE users
   SET display_name = '系统管理员',
       status = 'ACTIVE',
       tier = 'PREMIUM',
       last_login_at = @seed_now,
       updated_at = @seed_now
 WHERE id = 1;

INSERT INTO users(id, email, password_hash, role, tier, status, display_name, last_login_at, is_deleted, created_at, updated_at)
VALUES
  (2, 'student.zhangchen@bishe.local', @seed_password_hash, 'STUDENT', 'PREMIUM', 'ACTIVE', '张晨', DATE_SUB(@seed_now, INTERVAL 1 DAY), 0, DATE_SUB(@seed_now, INTERVAL 25 DAY), DATE_SUB(@seed_now, INTERVAL 1 DAY)),
  (3, 'student.linyue@bishe.local', @seed_password_hash, 'STUDENT', 'FREE', 'ACTIVE', '林悦', DATE_SUB(@seed_now, INTERVAL 2 DAY), 0, DATE_SUB(@seed_now, INTERVAL 18 DAY), DATE_SUB(@seed_now, INTERVAL 2 DAY)),
  (4, 'student.wangyifan@bishe.local', @seed_password_hash, 'STUDENT', 'FREE', 'SUSPENDED', '王一帆', DATE_SUB(@seed_now, INTERVAL 35 DAY), 0, DATE_SUB(@seed_now, INTERVAL 20 DAY), DATE_SUB(@seed_now, INTERVAL 10 DAY)),
  (5, 'student.zhaoqing@bishe.local', @seed_password_hash, 'STUDENT', 'PREMIUM', 'ACTIVE', '赵晴', DATE_SUB(@seed_now, INTERVAL 6 DAY), 0, DATE_SUB(@seed_now, INTERVAL 7 DAY), DATE_SUB(@seed_now, INTERVAL 6 DAY)),
  (6, 'student.chennuo@bishe.local', @seed_password_hash, 'STUDENT', 'PREMIUM', 'ACTIVE', '陈诺', DATE_SUB(@seed_now, INTERVAL 4 HOUR), 0, DATE_SUB(@seed_now, INTERVAL 16 DAY), DATE_SUB(@seed_now, INTERVAL 4 HOUR)),
  (7, 'mentor.liran@bishe.local', @seed_password_hash, 'MENTOR', 'PREMIUM', 'ACTIVE', '导师李然', DATE_SUB(@seed_now, INTERVAL 2 HOUR), 0, DATE_SUB(@seed_now, INTERVAL 40 DAY), DATE_SUB(@seed_now, INTERVAL 2 HOUR)),
  (8, 'mentor.zhoulan@bishe.local', @seed_password_hash, 'MENTOR', 'PREMIUM', 'ACTIVE', '导师周岚', DATE_SUB(@seed_now, INTERVAL 3 DAY), 0, DATE_SUB(@seed_now, INTERVAL 35 DAY), DATE_SUB(@seed_now, INTERVAL 3 DAY)),
  (9, 'enterprise.sparkhr@bishe.local', @seed_password_hash, 'ENTERPRISE', 'PREMIUM', 'ACTIVE', '星火科技 HR', DATE_SUB(@seed_now, INTERVAL 1 DAY), 0, DATE_SUB(@seed_now, INTERVAL 14 DAY), DATE_SUB(@seed_now, INTERVAL 1 DAY));
ALTER TABLE users AUTO_INCREMENT = 10;

INSERT INTO student_profiles(id, user_id, major, grade, target_position, skill_tags, self_intro, created_at, updated_at)
VALUES
  (1, 2, '软件工程', '大四', 'Java 后端开发', 'Java,Spring Boot,MySQL,Redis', '正在准备秋招，希望把项目表达和业务指标讲清楚。', DATE_SUB(@seed_now, INTERVAL 25 DAY), DATE_SUB(@seed_now, INTERVAL 1 DAY)),
  (2, 3, '计算机科学与技术', '大三', '后端开发实习', 'Java,SQL,数据结构', '希望在实习前补齐项目量化成果和数据库基础。', DATE_SUB(@seed_now, INTERVAL 18 DAY), DATE_SUB(@seed_now, INTERVAL 2 DAY)),
  (3, 4, '信息安全', '大四', '测试开发', 'Python,SQL,自动化测试', '目前在整理测试经历，准备重新规划岗位方向。', DATE_SUB(@seed_now, INTERVAL 20 DAY), DATE_SUB(@seed_now, INTERVAL 10 DAY)),
  (4, 5, '数字媒体技术', '大三', '产品运营实习', '数据分析,内容策划,用户调研', '想把校园项目整理成更有业务价值的经历。', DATE_SUB(@seed_now, INTERVAL 7 DAY), DATE_SUB(@seed_now, INTERVAL 7 DAY)),
  (5, 6, '计算机科学与技术', '研一', '后端开发工程师', 'Java,Spring Boot,微服务,数据库设计', '希望通过模拟面试把项目深度和系统设计表达得更完整。', DATE_SUB(@seed_now, INTERVAL 16 DAY), DATE_SUB(@seed_now, INTERVAL 4 HOUR));

INSERT INTO mentor_profiles(id, user_id, expertise_tags, bio, price_fen, is_available, approval_status, total_orders, avg_rating, created_at, updated_at)
VALUES
  (1, 7, '职业规划,简历诊断,后端面试', '曾负责校招面试与新人成长计划，擅长把项目经历提炼成业务价值表达。', 9900, 1, 'APPROVED', 4, 4.90, DATE_SUB(@seed_now, INTERVAL 40 DAY), DATE_SUB(@seed_now, INTERVAL 2 HOUR)),
  (2, 8, '项目复盘,模拟面试,职业咨询', '聚焦应届生项目复盘与结构化表达，适合需要补齐项目叙事的同学。', 12900, 1, 'PENDING', 3, 4.75, DATE_SUB(@seed_now, INTERVAL 35 DAY), DATE_SUB(@seed_now, INTERVAL 3 DAY));

INSERT INTO enterprise_profiles(id, user_id, company_name, industry, company_size, hiring_tags, approval_status, created_at, updated_at)
VALUES
  (1, 9, '星火科技', '互联网招聘服务', '50-200人', '校招,前端,后端', 'APPROVED', DATE_SUB(@seed_now, INTERVAL 14 DAY), DATE_SUB(@seed_now, INTERVAL 1 DAY));

INSERT INTO feature_flags(flag_key, flag_value, description, updated_by, created_at, updated_at)
VALUES
  ('payment.mode', 'MOCK', '控制咨询订单创建支付时默认走 MOCK 还是 SANDBOX。', 1, @seed_now, @seed_now),
  ('voice.enabled', 'true', '控制语音面试入口、语音往返与 TTS 文本转语音能力。', 1, @seed_now, @seed_now),
  ('community.ai-pre-answer.enabled', 'true', '控制社区帖子 AI 自动首评与预回答草稿生成能力。', 1, @seed_now, @seed_now)
ON DUPLICATE KEY UPDATE
  flag_value = VALUES(flag_value),
  description = VALUES(description),
  updated_by = VALUES(updated_by),
  updated_at = VALUES(updated_at);

INSERT INTO posts(id, user_id, title, content, tags, moderation_status, risk_level, last_moderation_event_id, is_deleted, created_at, updated_at)
VALUES
  (1, 2, '秋招 Java 后端项目怎么突出业务指标', '我在课程项目里做了订单与库存模块，现在准备秋招，想把“做了什么”和“产生了什么结果”讲得更像业务闭环。大家通常会怎么提炼指标？', '秋招准备,项目复盘,后端开发', 'PASS', 'LOW', 5, 0, DATE_SUB(@seed_now, INTERVAL 2 DAY), DATE_SUB(@seed_now, INTERVAL 2 DAY)),
  (2, 3, '实习前 SQL 与索引该怎么补最有效', '这周在刷 MySQL 题，感觉零散知识点很多。有没有适合一周内快速补齐索引、执行计划和事务理解的方法？', 'SQL,MySQL,实习准备', 'PASS', 'LOW', 6, 0, DATE_SUB(@seed_now, INTERVAL 1 DAY), DATE_SUB(@seed_now, INTERVAL 1 DAY)),
  (3, 5, '求助：某培训群突然私信推销课程', '今天收到私信推荐所谓“保 offer 课程”，内容很夸张，还附带外部联系方式。我不确定这种内容算不算违规，先发出来请管理员帮忙看看。', '社区治理,举报协作', 'REVIEW', 'MEDIUM', 1, 0, DATE_SUB(@seed_now, INTERVAL 3 HOUR), DATE_SUB(@seed_now, INTERVAL 2 HOUR)),
  (4, 7, '面试复盘时最容易被忽略的三类细节', '很多同学会把注意力都放在八股和项目亮点上，但真正影响追问深度的，往往是边界条件、监控方式和失败复盘这三件事。', '导师分享,面试复盘,职业规划', 'PASS', 'LOW', NULL, 0, DATE_SUB(@seed_now, INTERVAL 6 DAY), DATE_SUB(@seed_now, INTERVAL 6 DAY));

INSERT INTO comments(id, post_id, user_id, content, is_ai, moderation_status, risk_level, last_moderation_event_id, is_deleted, created_at, updated_at)
VALUES
  (1, 1, 3, '我会先按“场景—动作—结果”拆开，再补一个量化指标，比如接口耗时下降多少、多少并发下稳定运行。', 0, 'PASS', 'LOW', NULL, 0, DATE_SUB(@seed_now, INTERVAL 47 HOUR), DATE_SUB(@seed_now, INTERVAL 47 HOUR)),
  (2, 1, 7, '可以先把项目目标和结果写成三句话，再把每一句背后的技术动作单独展开，这样更容易兼顾业务与技术。', 1, 'PASS', 'LOW', 3, 0, DATE_SUB(@seed_now, INTERVAL 46 HOUR), DATE_SUB(@seed_now, INTERVAL 46 HOUR)),
  (3, 2, 2, '我最近是按“索引原理 -> explain -> 事务隔离 -> 典型 SQL 改写”这条线在补，效率还不错。', 0, 'PASS', 'LOW', NULL, 0, DATE_SUB(@seed_now, INTERVAL 20 HOUR), DATE_SUB(@seed_now, INTERVAL 20 HOUR)),
  (4, 2, 4, '这种问题网上搜一下就有，别总来问。', 0, 'BLOCK', 'HIGH', 4, 0, DATE_SUB(@seed_now, INTERVAL 5 DAY), DATE_SUB(@seed_now, INTERVAL 4 DAY)),
  (5, 1, 5, '群里那种“包过”“包 offer”的宣传我也遇到过，建议先别留下联系方式。', 0, 'REVIEW', 'HIGH', 2, 0, DATE_SUB(@seed_now, INTERVAL 110 MINUTE), DATE_SUB(@seed_now, INTERVAL 100 MINUTE));

INSERT INTO post_likes(id, post_id, user_id, created_at)
VALUES
  (1, 1, 3, DATE_SUB(@seed_now, INTERVAL 45 HOUR)),
  (2, 1, 6, DATE_SUB(@seed_now, INTERVAL 44 HOUR)),
  (3, 1, 8, DATE_SUB(@seed_now, INTERVAL 43 HOUR)),
  (4, 2, 2, DATE_SUB(@seed_now, INTERVAL 18 HOUR)),
  (5, 2, 7, DATE_SUB(@seed_now, INTERVAL 17 HOUR)),
  (6, 4, 2, DATE_SUB(@seed_now, INTERVAL 5 DAY)),
  (7, 4, 3, DATE_SUB(@seed_now, INTERVAL 5 DAY));

INSERT INTO content_moderation_events(id, trace_id, source_type, target_type, target_id, risk_level, action, reason_code, masked_text, operator_user_id, created_at)
VALUES
  (1, 'trc_admin_demo_review_post_001', 'COMMUNITY_POST', 'POST', '3', 'MEDIUM', 'REVIEW', 'POLICY_SENSITIVE_PROMOTION', '求助：某培训群突然私信推销课程', 0, DATE_SUB(@seed_now, INTERVAL 2 HOUR)),
  (2, 'trc_admin_demo_review_comment_001', 'COMMUNITY_COMMENT', 'COMMENT', '5', 'HIGH', 'REVIEW', 'POLICY_CONTACT_RISK', '建议先别留下联系方式', 0, DATE_SUB(@seed_now, INTERVAL 100 MINUTE)),
  (3, 'trc_admin_demo_pass_comment_001', 'COMMUNITY_COMMENT', 'COMMENT', '2', 'LOW', 'PASS', 'RULE_CLEAR', '可以先把项目目标和结果写成三句话', 7, DATE_SUB(@seed_now, INTERVAL 46 HOUR)),
  (4, 'trc_admin_demo_block_comment_001', 'COMMUNITY_COMMENT', 'COMMENT', '4', 'HIGH', 'BLOCK', 'REPORT_DECISION_TAKE_DOWN', '这种问题网上搜一下就有，别总来问。', 1, DATE_SUB(@seed_now, INTERVAL 4 DAY)),
  (5, 'trc_admin_demo_pass_post_001', 'COMMUNITY_POST', 'POST', '1', 'LOW', 'PASS', 'RULE_CLEAR', '秋招 Java 后端项目怎么突出业务指标', 2, DATE_SUB(@seed_now, INTERVAL 2 DAY)),
  (6, 'trc_admin_demo_pass_post_002', 'COMMUNITY_POST', 'POST', '2', 'LOW', 'PASS', 'RULE_CLEAR', '实习前 SQL 与索引该怎么补最有效', 3, DATE_SUB(@seed_now, INTERVAL 1 DAY));

INSERT INTO content_reports(id, reporter_user_id, target_type, target_id, reason_code, detail, status, latest_action, created_at, updated_at, closed_at)
VALUES
  (1, 3, 'POST', '3', 'ABUSE', '帖子疑似引流推销，建议管理员复核处理。', 'PENDING', 'NONE', DATE_SUB(@seed_now, INTERVAL 70 MINUTE), DATE_SUB(@seed_now, INTERVAL 70 MINUTE), NULL),
  (2, 2, 'COMMENT', '4', 'SPAM', '评论语气攻击性较强，已影响正常讨论氛围。', 'ACCEPTED', 'TAKE_DOWN', DATE_SUB(@seed_now, INTERVAL 4 DAY), DATE_SUB(@seed_now, INTERVAL 3 DAY), DATE_SUB(@seed_now, INTERVAL 3 DAY)),
  (3, 7, 'POST', '1', 'DUPLICATE', '内容本身无明显违规，已确认无需下架。', 'REJECTED', 'NO_ACTION', DATE_SUB(@seed_now, INTERVAL 2 DAY), DATE_SUB(@seed_now, INTERVAL 45 HOUR), DATE_SUB(@seed_now, INTERVAL 45 HOUR));

INSERT INTO content_report_actions(id, report_id, operator_user_id, decision, action, comment, created_at)
VALUES
  (1, 2, 1, 'ACCEPTED', 'TAKE_DOWN', '已确认评论存在攻击性表达，执行下架处理。', DATE_SUB(@seed_now, INTERVAL 3 DAY)),
  (2, 3, 1, 'REJECTED', 'NO_ACTION', '已复核原帖内容，保留正常讨论。', DATE_SUB(@seed_now, INTERVAL 45 HOUR));

INSERT INTO audit_logs(id, trace_id, operator_user_id, action_type, target_type, target_id, detail_json, created_at)
VALUES
  (1, 'trc_admin_demo_content_001', 1, 'CONTENT_REVIEW_DECISION', 'POST', '3', '{"decision":"REVIEW","note":"保留待人工复核，继续观察举报情况"}', DATE_SUB(@seed_now, INTERVAL 2 HOUR)),
  (2, 'trc_admin_demo_content_002', 1, 'REPORT_DECISION', 'COMMENT', '4', '{"decision":"ACCEPTED","action":"TAKE_DOWN","note":"确认存在攻击性内容"}', DATE_SUB(@seed_now, INTERVAL 3 DAY)),
  (3, 'trc_admin_demo_payment_001', 1, 'ADMIN_PAYMENT_MARK_REVIEWED', 'ORDER', 'ORD-DEMO-20260309-004', '{"note":"已联系学生确认支付未完成，先人工登记待后续跟进","previousOrderStatus":"PAYING","latestPaymentStatus":null}', DATE_SUB(@seed_now, INTERVAL 1 DAY)),
  (4, 'trc_admin_demo_payment_002', 1, 'ADMIN_PAYMENT_CONFIRM_EXTERNAL_REFUND', 'ORDER', 'ORD-DEMO-20260309-005', '{"note":"已确认外部退款链路完成，平台状态与外部资金状态一致","previousOrderStatus":"REFUNDED","latestPaymentStatus":"REFUND_SUCCESS"}', DATE_SUB(@seed_now, INTERVAL 4 DAY)),
  (5, 'trc_admin_demo_content_003', 1, 'AUTO_HIDE', 'COMMENT', '4', '{"reason":"举报累计达到阈值后自动隐藏，管理员已完成复核"}', DATE_SUB(@seed_now, INTERVAL 4 DAY));

INSERT INTO consult_orders(id, order_no, student_user_id, mentor_user_id, amount_fen, status, question_text, paid_at, closed_at, created_at, updated_at, appointment_start_at, appointment_end_at)
VALUES
  (1, 'ORD-DEMO-20260309-001', 2, 7, 9900, 'CREATED', '想请老师帮我看一版 Java 后端实习简历，重点是项目里的业务指标怎么表达。', NULL, NULL, DATE_SUB(@seed_now, INTERVAL 20 MINUTE), DATE_SUB(@seed_now, INTERVAL 20 MINUTE), @slot_o1_start, @slot_o1_end),
  (2, 'ORD-DEMO-20260309-002', 3, 7, 9900, 'PAID', '秋招前如何补齐项目亮点和量化成果，想先梳理表达框架。', DATE_SUB(@seed_now, INTERVAL 47 HOUR), NULL, DATE_SUB(@seed_now, INTERVAL 48 HOUR), DATE_SUB(@seed_now, INTERVAL 47 HOUR), @slot_o2_start, @slot_o2_end),
  (3, 'ORD-DEMO-20260309-003', 6, 8, 12900, 'ANSWERED', '目标转后端实习，想复盘已有项目并准备一轮结构化答题。', DATE_SUB(@seed_now, INTERVAL 119 HOUR), NULL, DATE_SUB(@seed_now, INTERVAL 120 HOUR), DATE_SUB(@seed_now, INTERVAL 96 HOUR), @slot_o3_start, @slot_o3_end),
  (4, 'ORD-DEMO-20260309-004', 2, 8, 12900, 'PAYING', '支付回调似乎没有回来，想确认订单状态和预约时段是否还在。', NULL, NULL, DATE_SUB(@seed_now, INTERVAL 2 DAY), DATE_SUB(@seed_now, INTERVAL 1 DAY), @slot_o4_start, @slot_o4_end),
  (5, 'ORD-DEMO-20260309-005', 3, 8, 12900, 'REFUNDED', '原定咨询时间临时冲突，希望退款处理。', DATE_SUB(@seed_now, INTERVAL 143 HOUR), NULL, DATE_SUB(@seed_now, INTERVAL 144 HOUR), DATE_SUB(@seed_now, INTERVAL 4 DAY), @slot_o5_start, @slot_o5_end),
  (6, 'ORD-DEMO-20260309-006', 5, 7, 9900, 'CLOSED', '希望模拟一轮 Java 八股 + 项目追问，帮我找出回答中的空档。', DATE_SUB(@seed_now, INTERVAL 287 HOUR), DATE_SUB(@seed_now, INTERVAL 264 HOUR), DATE_SUB(@seed_now, INTERVAL 288 HOUR), DATE_SUB(@seed_now, INTERVAL 264 HOUR), @slot_o6_start, @slot_o6_end),
  (7, 'ORD-DEMO-20260309-007', 6, 7, 9900, 'PAID', '订单状态已显示已支付，但支付流水还没对上，请管理员协助核验。', DATE_SUB(@seed_now, INTERVAL 17 HOUR), NULL, DATE_SUB(@seed_now, INTERVAL 18 HOUR), DATE_SUB(@seed_now, INTERVAL 17 HOUR), @slot_o7_start, @slot_o7_end);

INSERT INTO mentor_schedule_slots(id, mentor_user_id, start_at, end_at, status, booked_order_no, created_at, updated_at)
VALUES
  (1, 7, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 4 DAY), '19:00:00'), TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 4 DAY), '20:00:00'), 'AVAILABLE', NULL, DATE_SUB(@seed_now, INTERVAL 1 DAY), DATE_SUB(@seed_now, INTERVAL 1 DAY)),
  (2, 7, @slot_o1_start, @slot_o1_end, 'BOOKED', 'ORD-DEMO-20260309-001', DATE_SUB(@seed_now, INTERVAL 20 MINUTE), DATE_SUB(@seed_now, INTERVAL 20 MINUTE)),
  (3, 7, @slot_o2_start, @slot_o2_end, 'BOOKED', 'ORD-DEMO-20260309-002', DATE_SUB(@seed_now, INTERVAL 48 HOUR), DATE_SUB(@seed_now, INTERVAL 47 HOUR)),
  (4, 8, @slot_o3_start, @slot_o3_end, 'BOOKED', 'ORD-DEMO-20260309-003', DATE_SUB(@seed_now, INTERVAL 120 HOUR), DATE_SUB(@seed_now, INTERVAL 96 HOUR)),
  (5, 8, @slot_o4_start, @slot_o4_end, 'BOOKED', 'ORD-DEMO-20260309-004', DATE_SUB(@seed_now, INTERVAL 2 DAY), DATE_SUB(@seed_now, INTERVAL 1 DAY)),
  (6, 8, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 5 DAY), '20:00:00'), TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 5 DAY), '21:00:00'), 'AVAILABLE', NULL, DATE_SUB(@seed_now, INTERVAL 1 DAY), DATE_SUB(@seed_now, INTERVAL 1 DAY)),
  (7, 7, @slot_o7_start, @slot_o7_end, 'BOOKED', 'ORD-DEMO-20260309-007', DATE_SUB(@seed_now, INTERVAL 18 HOUR), DATE_SUB(@seed_now, INTERVAL 17 HOUR));

INSERT INTO payment_records(id, order_no, channel, mode, provider_trade_no, amount_fen, status, idempotency_key, raw_callback, created_at, updated_at)
VALUES
  (1, 'ORD-DEMO-20260309-001', 'ALIPAY', 'SANDBOX', NULL, 9900, 'INIT', 'INIT:ORD-DEMO-20260309-001', '{"stage":"create","note":"待支付，保留正常 pending 样本"}', DATE_SUB(@seed_now, INTERVAL 15 MINUTE), DATE_SUB(@seed_now, INTERVAL 15 MINUTE)),
  (2, 'ORD-DEMO-20260309-002', 'ALIPAY', 'SANDBOX', NULL, 9900, 'INIT', 'INIT:ORD-DEMO-20260309-002', '{"stage":"create"}', DATE_SUB(@seed_now, INTERVAL 47 HOUR), DATE_SUB(@seed_now, INTERVAL 47 HOUR)),
  (3, 'ORD-DEMO-20260309-002', 'ALIPAY', 'SANDBOX', 'ALI-DEMO-20260309-002', 9900, 'SUCCESS', 'SUCCESS:ORD-DEMO-20260309-002', '{"stage":"notify","tradeStatus":"TRADE_SUCCESS"}', DATE_SUB(@seed_now, INTERVAL 46 HOUR), DATE_SUB(@seed_now, INTERVAL 46 HOUR)),
  (4, 'ORD-DEMO-20260309-003', 'ALIPAY', 'SANDBOX', 'ALI-DEMO-20260309-003', 12900, 'SUCCESS', 'SUCCESS:ORD-DEMO-20260309-003', '{"stage":"notify","tradeStatus":"TRADE_SUCCESS"}', DATE_SUB(@seed_now, INTERVAL 119 HOUR), DATE_SUB(@seed_now, INTERVAL 119 HOUR)),
  (5, 'ORD-DEMO-20260309-005', 'ALIPAY', 'SANDBOX', NULL, 12900, 'INIT', 'INIT:ORD-DEMO-20260309-005', '{"stage":"create"}', DATE_SUB(@seed_now, INTERVAL 143 HOUR), DATE_SUB(@seed_now, INTERVAL 143 HOUR)),
  (6, 'ORD-DEMO-20260309-005', 'ALIPAY', 'SANDBOX', 'ALI-DEMO-20260309-005', 12900, 'SUCCESS', 'SUCCESS:ORD-DEMO-20260309-005', '{"stage":"notify","tradeStatus":"TRADE_SUCCESS"}', DATE_SUB(@seed_now, INTERVAL 142 HOUR), DATE_SUB(@seed_now, INTERVAL 142 HOUR)),
  (7, 'ORD-DEMO-20260309-005', 'ALIPAY', 'SANDBOX', 'ALI-DEMO-20260309-005', 12900, 'REFUND_SUCCESS', 'REFUND:ORD-DEMO-20260309-005', '{"stage":"refund","tradeStatus":"REFUND_SUCCESS"}', DATE_SUB(@seed_now, INTERVAL 4 DAY), DATE_SUB(@seed_now, INTERVAL 4 DAY)),
  (8, 'ORD-DEMO-20260309-006', 'ALIPAY', 'SANDBOX', 'ALI-DEMO-20260309-006', 9900, 'SUCCESS', 'SUCCESS:ORD-DEMO-20260309-006', '{"stage":"notify","tradeStatus":"TRADE_SUCCESS"}', DATE_SUB(@seed_now, INTERVAL 287 HOUR), DATE_SUB(@seed_now, INTERVAL 287 HOUR)),
  (9, 'ORD-DEMO-20260309-007', 'ALIPAY', 'SANDBOX', NULL, 9900, 'INIT', 'INIT:ORD-DEMO-20260309-007', '{"stage":"create","note":"订单状态已支付但成功回执未落单，保留异常样本"}', DATE_SUB(@seed_now, INTERVAL 17 HOUR), DATE_SUB(@seed_now, INTERVAL 17 HOUR));

INSERT INTO consult_messages(id, order_no, sender_user_id, sender_role, message_text, created_at)
VALUES
  (1, 'ORD-DEMO-20260309-003', 6, 'STUDENT', '我担心项目描述太像课程作业，老师能帮我把亮点讲得更像真实业务问题吗？', DATE_SUB(@seed_now, INTERVAL 118 HOUR)),
  (2, 'ORD-DEMO-20260309-003', 8, 'MENTOR', '可以，先把项目里真正负责的模块拆出来，再补一次性能指标和失败复盘。', DATE_SUB(@seed_now, INTERVAL 117 HOUR)),
  (3, 'ORD-DEMO-20260309-006', 5, 'STUDENT', '我回答“为什么要这么设计”时总是会卡住，尤其是数据库和缓存部分。', DATE_SUB(@seed_now, INTERVAL 286 HOUR)),
  (4, 'ORD-DEMO-20260309-006', 7, 'MENTOR', '先别急着背答案，先把业务场景、约束条件和指标目标写出来，再去解释设计取舍。', DATE_SUB(@seed_now, INTERVAL 285 HOUR)),
  (5, 'ORD-DEMO-20260309-006', 5, 'STUDENT', '明白了，我会把接口耗时和缓存命中率这些指标也补进项目复盘里。', DATE_SUB(@seed_now, INTERVAL 284 HOUR)),
  (6, 'ORD-DEMO-20260309-006', 7, 'MENTOR', '很好，下一轮重点练习“为什么不用别的方案”和“问题发生后如何定位”。', DATE_SUB(@seed_now, INTERVAL 283 HOUR));

INSERT INTO consult_after_sales_requests(id, order_no, requester_user_id, request_type, status, reason, review_note, reviewer_user_id, auto_triggered, reviewed_at, created_at, updated_at)
VALUES
  (1, 'ORD-DEMO-20260309-003', 6, 'REFUND', 'PENDING', '导师答复方向有帮助，但我还想申请补充说明或退款，请管理员协助判断。', NULL, NULL, 0, NULL, DATE_SUB(@seed_now, INTERVAL 1 DAY), DATE_SUB(@seed_now, INTERVAL 1 DAY)),
  (2, 'ORD-DEMO-20260309-005', 3, 'REFUND', 'APPROVED', '咨询时间与课程冲突，已和导师沟通过无法改期。', '已核验双方沟通记录，按退款流程处理。', 1, 0, DATE_SUB(@seed_now, INTERVAL 4 DAY), DATE_SUB(@seed_now, INTERVAL 4 DAY), DATE_SUB(@seed_now, INTERVAL 4 DAY)),
  (3, 'ORD-DEMO-20260309-006', 5, 'REFUND', 'REJECTED', '问题已得到解决，后续不再需要退款。', '订单已完成并收到评价，不再满足退款条件。', 1, 0, DATE_SUB(@seed_now, INTERVAL 10 DAY), DATE_SUB(@seed_now, INTERVAL 10 DAY), DATE_SUB(@seed_now, INTERVAL 10 DAY));

INSERT INTO consult_reviews(id, order_no, student_user_id, mentor_user_id, rating, comment, created_at)
VALUES
  (1, 'ORD-DEMO-20260309-006', 5, 7, 5, '导师会把“项目动作”和“业务结果”分层拆开讲，复盘后我的表达清晰很多。', DATE_SUB(@seed_now, INTERVAL 263 HOUR));

INSERT INTO notifications(id, user_id, type, content, ref_id, is_read, read_at, created_at)
VALUES
  (1, 7, 'CONSULT_PAID', '新的咨询订单已支付，可开始答复。', 'ORD-DEMO-20260309-002', 0, NULL, DATE_SUB(@seed_now, INTERVAL 46 HOUR)),
  (2, 6, 'CONSULT_AFTER_SALES_PENDING', '你的售后申请已提交，管理员正在复核。', 'ORD-DEMO-20260309-003', 0, NULL, DATE_SUB(@seed_now, INTERVAL 1 DAY)),
  (3, 2, 'CONSULT_PAYMENT_EXCEPTION', '管理员已人工登记该支付异常订单，后续会继续跟进。', 'ORD-DEMO-20260309-004', 0, NULL, DATE_SUB(@seed_now, INTERVAL 1 DAY)),
  (4, 3, 'CONSULT_REFUNDED', '你的咨询订单已完成退款处理。', 'ORD-DEMO-20260309-005', 1, DATE_SUB(@seed_now, INTERVAL 4 DAY), DATE_SUB(@seed_now, INTERVAL 4 DAY)),
  (5, 8, 'CONSULT_REFUNDED', '某咨询订单已完成退款，平台侧状态已同步。', 'ORD-DEMO-20260309-005', 0, NULL, DATE_SUB(@seed_now, INTERVAL 4 DAY)),
  (6, 3, 'CONSULT_REFUND_CONFIRMED', '管理员已确认该订单的外部退款处理完成。', 'ORD-DEMO-20260309-005', 0, NULL, DATE_SUB(@seed_now, INTERVAL 4 DAY)),
  (7, 7, 'CONSULT_CLOSED', '学生已确认并关闭咨询订单。', 'ORD-DEMO-20260309-006', 0, NULL, DATE_SUB(@seed_now, INTERVAL 264 HOUR)),
  (8, 7, 'CONSULT_REVIEWED', '学生已提交本次咨询评价，可查看最新反馈。', 'ORD-DEMO-20260309-006', 0, NULL, DATE_SUB(@seed_now, INTERVAL 263 HOUR)),
  (9, 2, 'COMMUNITY_INTERACTION', '你的帖子收到新的高质量回复。', '1', 1, DATE_SUB(@seed_now, INTERVAL 45 HOUR), DATE_SUB(@seed_now, INTERVAL 45 HOUR)),
  (10, 1, 'ADMIN_DIGEST', '本地演示数据已刷新，可继续进行管理员端联调。', NULL, 0, NULL, DATE_SUB(@seed_now, INTERVAL 5 MINUTE));

INSERT INTO points_ledger(id, student_user_id, delta_points, reason_code, balance_after, created_at)
VALUES
  (1, 2, 60, 'REGISTER', 60, DATE_SUB(@seed_now, INTERVAL 25 DAY)),
  (2, 2, 10, 'CHECKIN', 70, DATE_SUB(@seed_now, INTERVAL 3 DAY)),
  (3, 2, 6, 'COMMUNITY_INTERACT', 76, DATE_SUB(@seed_now, INTERVAL 2 DAY)),
  (4, 2, 8, 'SKILL_PROGRESS', 84, DATE_SUB(@seed_now, INTERVAL 1 DAY)),
  (5, 3, 60, 'REGISTER', 60, DATE_SUB(@seed_now, INTERVAL 18 DAY)),
  (6, 3, 8, 'SKILL_PROGRESS', 68, DATE_SUB(@seed_now, INTERVAL 4 DAY)),
  (7, 3, 10, 'BOUNTY_SUBMISSION', 78, DATE_SUB(@seed_now, INTERVAL 2 DAY)),
  (8, 4, 60, 'REGISTER', 60, DATE_SUB(@seed_now, INTERVAL 20 DAY)),
  (9, 5, 60, 'REGISTER', 60, DATE_SUB(@seed_now, INTERVAL 7 DAY)),
  (10, 5, 10, 'CHECKIN', 70, DATE_SUB(@seed_now, INTERVAL 1 DAY)),
  (11, 5, 15, 'CONSULT_REVIEW_REWARD', 85, DATE_SUB(@seed_now, INTERVAL 11 DAY)),
  (12, 6, 60, 'REGISTER', 60, DATE_SUB(@seed_now, INTERVAL 16 DAY)),
  (13, 6, 8, 'SKILL_PROGRESS', 68, DATE_SUB(@seed_now, INTERVAL 3 DAY)),
  (14, 6, 6, 'COMMUNITY_INTERACT', 74, DATE_SUB(@seed_now, INTERVAL 1 DAY));

INSERT INTO checkins(id, student_user_id, checkin_date, streak_count, points_earned, created_at)
VALUES
  (1, 2, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 3, 10, DATE_SUB(@seed_now, INTERVAL 2 DAY)),
  (2, 3, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 2, 10, DATE_SUB(@seed_now, INTERVAL 4 DAY)),
  (3, 5, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 2, 10, DATE_SUB(@seed_now, INTERVAL 1 DAY)),
  (4, 6, CURDATE(), 4, 10, @seed_now);

INSERT INTO skill_progress(id, student_user_id, node_code, progress_status, updated_at)
VALUES
  (1, 2, 'programming_language_foundations', 'MASTERED', DATE_SUB(@seed_now, INTERVAL 10 DAY)),
  (2, 2, 'java_programming', 'MASTERED', DATE_SUB(@seed_now, INTERVAL 7 DAY)),
  (3, 2, 'software_engineering_delivery', 'MASTERED', DATE_SUB(@seed_now, INTERVAL 6 DAY)),
  (4, 2, 'career_employment_readiness', 'MASTERED', DATE_SUB(@seed_now, INTERVAL 6 DAY)),
  (5, 2, 'career_positioning', 'MASTERED', DATE_SUB(@seed_now, INTERVAL 5 DAY)),
  (6, 2, 'object_oriented_modeling', 'LEARNING', DATE_SUB(@seed_now, INTERVAL 3 DAY)),
  (7, 2, 'software_architecture_design', 'LEARNING', DATE_SUB(@seed_now, INTERVAL 2 DAY)),
  (8, 2, 'resume_portfolio', 'LEARNING', DATE_SUB(@seed_now, INTERVAL 2 DAY)),
  (9, 2, 'project_storytelling', 'LEARNING', DATE_SUB(@seed_now, INTERVAL 1 DAY)),
  (10, 3, 'programming_language_foundations', 'MASTERED', DATE_SUB(@seed_now, INTERVAL 9 DAY)),
  (11, 3, 'data_database_systems', 'MASTERED', DATE_SUB(@seed_now, INTERVAL 8 DAY)),
  (12, 3, 'relational_databases', 'MASTERED', DATE_SUB(@seed_now, INTERVAL 6 DAY)),
  (13, 3, 'data_modeling', 'LEARNING', DATE_SUB(@seed_now, INTERVAL 2 DAY)),
  (14, 3, 'analytics_data_visualization', 'LEARNING', DATE_SUB(@seed_now, INTERVAL 1 DAY)),
  (15, 5, 'programming_language_foundations', 'MASTERED', DATE_SUB(@seed_now, INTERVAL 6 DAY)),
  (16, 5, 'software_engineering_delivery', 'MASTERED', DATE_SUB(@seed_now, INTERVAL 4 DAY)),
  (17, 5, 'internship_workplace_adaptation', 'MASTERED', DATE_SUB(@seed_now, INTERVAL 4 DAY)),
  (18, 5, 'collaborative_development', 'LEARNING', DATE_SUB(@seed_now, INTERVAL 2 DAY)),
  (19, 5, 'automated_testing', 'LEARNING', DATE_SUB(@seed_now, INTERVAL 1 DAY)),
  (20, 5, 'workplace_communication', 'LEARNING', DATE_SUB(@seed_now, INTERVAL 1 DAY)),
  (21, 6, 'programming_language_foundations', 'MASTERED', DATE_SUB(@seed_now, INTERVAL 10 DAY)),
  (22, 6, 'ai_data_science', 'MASTERED', DATE_SUB(@seed_now, INTERVAL 9 DAY)),
  (23, 6, 'statistics_foundation', 'MASTERED', DATE_SUB(@seed_now, INTERVAL 6 DAY)),
  (24, 6, 'wellbeing_self_management', 'MASTERED', DATE_SUB(@seed_now, INTERVAL 7 DAY)),
  (25, 6, 'machine_learning', 'LEARNING', DATE_SUB(@seed_now, INTERVAL 3 DAY)),
  (26, 6, 'deep_learning', 'LEARNING', DATE_SUB(@seed_now, INTERVAL 2 DAY)),
  (27, 6, 'llm_applications', 'LEARNING', DATE_SUB(@seed_now, INTERVAL 1 DAY)),
  (28, 6, 'growth_mindset', 'LEARNING', DATE_SUB(@seed_now, INTERVAL 2 DAY)),
  (29, 6, 'prompt_rag_agent', 'MASTERED', DATE_SUB(@seed_now, INTERVAL 2 DAY)),
  (30, 6, 'ai_programming_engineering', 'MASTERED', DATE_SUB(@seed_now, INTERVAL 18 HOUR)),
  (31, 6, 'prompt_context_engineering', 'LEARNING', DATE_SUB(@seed_now, INTERVAL 14 HOUR)),
  (32, 6, 'ai_native_development', 'MASTERED', DATE_SUB(@seed_now, INTERVAL 12 HOUR)),
  (33, 6, 'vibe_coding_workflows', 'LEARNING', DATE_SUB(@seed_now, INTERVAL 10 HOUR)),
  (34, 6, 'ai_coding_agent_systems', 'LEARNING', DATE_SUB(@seed_now, INTERVAL 8 HOUR)),
  (35, 6, 'ai_engineering_governance', 'LEARNING', DATE_SUB(@seed_now, INTERVAL 6 HOUR));

INSERT INTO student_portrait_snapshots(id, student_user_id, portrait_tags, evidence, updated_at)
VALUES
  (1, 2,
   JSON_ARRAY(
     JSON_OBJECT('code', 'SKILL_PROGRESS_ACTIVE', 'label', '技能成长清晰', 'source', 'SKILL_PROGRESS', 'confidence', 0.87),
     JSON_OBJECT('code', 'COMMUNITY_ACTIVE', 'label', '社区互动积极', 'source', 'COMMUNITY', 'confidence', 0.88)
   ),
   JSON_OBJECT('masteredSkills', 5, 'learningSkills', 4, 'interviewMessages7d', 3, 'posts7d', 1, 'comments7d', 1, 'likesReceived7d', 3),
   DATE_SUB(@seed_now, INTERVAL 1 DAY)
  ),
  (2, 3,
   JSON_ARRAY(
     JSON_OBJECT('code', 'SKILL_PROGRESS_ACTIVE', 'label', '技能成长清晰', 'source', 'SKILL_PROGRESS', 'confidence', 0.82),
     JSON_OBJECT('code', 'COMMUNITY_ACTIVE', 'label', '社区互动积极', 'source', 'COMMUNITY', 'confidence', 0.78)
   ),
   JSON_OBJECT('masteredSkills', 3, 'learningSkills', 2, 'interviewMessages7d', 0, 'posts7d', 1, 'comments7d', 1, 'likesReceived7d', 2),
   DATE_SUB(@seed_now, INTERVAL 2 DAY)
  ),
  (3, 5,
   JSON_ARRAY(),
   JSON_OBJECT('masteredSkills', 3, 'learningSkills', 3, 'interviewMessages7d', 0, 'posts7d', 1, 'comments7d', 1, 'likesReceived7d', 0),
   DATE_SUB(@seed_now, INTERVAL 1 DAY)
  ),
  (4, 6,
   JSON_ARRAY(
     JSON_OBJECT('code', 'SKILL_PROGRESS_ACTIVE', 'label', '技能成长清晰', 'source', 'SKILL_PROGRESS', 'confidence', 0.92),
     JSON_OBJECT('code', 'INTERVIEW_ACTIVE', 'label', '模拟面试积极', 'source', 'AI_INTERVIEW', 'confidence', 0.82)
   ),
   JSON_OBJECT('masteredSkills', 7, 'learningSkills', 7, 'interviewMessages7d', 6, 'posts7d', 0, 'comments7d', 0, 'likesReceived7d', 0),
   DATE_SUB(@seed_now, INTERVAL 6 HOUR)
  );

INSERT INTO interview_sessions(id, session_id, student_user_id, target_role, mode, status, created_at, updated_at, reply_round_limit, reply_round_used, summary_generated, prepaid_points, reserved_quota_weight, summary_overall_score, summary_strengths_json, summary_weaknesses_json, summary_suggestions_json, summary_provider, summary_model, summary_latency_ms, summary_generated_at, finish_reason, ended_by_ai, user_deleted_at)
VALUES
  (1, 'sess_demo_text_001', 2, 'Java 后端开发实习生', 'INTERVIEW_TEXT', 'ACTIVE', DATE_SUB(@seed_now, INTERVAL 1 DAY), DATE_SUB(@seed_now, INTERVAL 12 HOUR), 5, 2, 0, 15, 5, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL),
  (2, 'sess_demo_text_002', 6, '后端开发工程师（校招）', 'INTERVIEW_TEXT', 'COMPLETED', DATE_SUB(@seed_now, INTERVAL 3 DAY), DATE_SUB(@seed_now, INTERVAL 70 HOUR), 6, 4, 1, 20, 5, 84, '["项目表达清晰","沟通自然"]', '["数据库调优细节不足"]', '["补充压测与索引优化案例"]', 'openai-compatible', 'gpt-4o-mini', 1820, DATE_SUB(@seed_now, INTERVAL 70 HOUR), 'ROUND_LIMIT', 1, NULL);

INSERT INTO interview_messages(id, session_pk, sender_role, message_text, score_hint, audio_object_key, created_at)
VALUES
  (1, 1, 'SYSTEM', '本轮模拟将聚焦项目介绍与接口设计。', NULL, NULL, DATE_SUB(@seed_now, INTERVAL 1 DAY)),
  (2, 1, 'INTERVIEWER', '请先用两分钟介绍一个你最熟悉的后端项目。', NULL, NULL, DATE_SUB(@seed_now, INTERVAL 23 HOUR)),
  (3, 1, 'STUDENT', '我负责了订单管理和库存扣减模块，重点做了接口梳理与数据库设计。', 72, NULL, DATE_SUB(@seed_now, INTERVAL 23 HOUR)),
  (4, 1, 'INTERVIEWER', '如果并发流量突然上来，你会优先排查哪些指标？', NULL, NULL, DATE_SUB(@seed_now, INTERVAL 22 HOUR)),
  (5, 2, 'SYSTEM', '本轮模拟已进入总结阶段。', NULL, NULL, DATE_SUB(@seed_now, INTERVAL 72 HOUR)),
  (6, 2, 'INTERVIEWER', '请介绍一次你做系统设计取舍的经历。', NULL, NULL, DATE_SUB(@seed_now, INTERVAL 71 HOUR)),
  (7, 2, 'STUDENT', '我在项目里把同步调用拆成异步消息，核心原因是高峰期响应时间波动明显。', 85, NULL, DATE_SUB(@seed_now, INTERVAL 71 HOUR)),
  (8, 2, 'INTERVIEWER', '为什么最终选了消息队列，而不是先做简单缓存？', NULL, NULL, DATE_SUB(@seed_now, INTERVAL 70 HOUR)),
  (9, 2, 'STUDENT', '因为主要瓶颈在下游处理链路，不只是热点读，缓存只能缓解一部分读压力。', 88, NULL, DATE_SUB(@seed_now, INTERVAL 70 HOUR)),
  (10, 2, 'SYSTEM', '本轮总结已生成，请查看优势与改进建议。', NULL, NULL, DATE_SUB(@seed_now, INTERVAL 70 HOUR));

INSERT INTO ai_call_logs(id, trace_id, user_id, task_type, provider, model, latency_ms, status, error_code, request_tokens, response_tokens, total_tokens, estimated_cost, user_tier, created_at, charged_points, quota_weight, result_summary, result_payload_json, user_deleted_at)
VALUES
  (1, 'trc_ai_demo_001', 2, 'INTERVIEW_TEXT', 'openai-compatible', 'gpt-4o-mini', 1850, 'SUCCESS', NULL, 820, 310, 1130, 0.004200, 'PREMIUM', DATE_SUB(@seed_now, INTERVAL 23 HOUR), 5, 5, '完成项目介绍追问一轮', '{"sessionId":"sess_demo_text_001","round":1}', NULL),
  (2, 'trc_ai_demo_002', 6, 'INTERVIEW_TEXT', 'openai-compatible', 'gpt-4o-mini', 2010, 'SUCCESS', NULL, 960, 420, 1380, 0.005300, 'PREMIUM', DATE_SUB(@seed_now, INTERVAL 71 HOUR), 5, 5, '完成系统设计追问', '{"sessionId":"sess_demo_text_002","round":2}', NULL),
  (3, 'trc_ai_demo_003', 6, 'INTERVIEW_SUMMARY', 'openai-compatible', 'gpt-4o-mini', 1820, 'SUCCESS', NULL, 780, 360, 1140, 0.004500, 'PREMIUM', DATE_SUB(@seed_now, INTERVAL 70 HOUR), 6, 5, '生成结构化面试总结', '{"sessionId":"sess_demo_text_002","overallScore":84}', NULL),
  (4, 'trc_ai_demo_004', 3, 'RESUME', 'openai-compatible', 'gpt-4o-mini', 1320, 'SUCCESS', NULL, 650, 280, 930, 0.003400, 'FREE', DATE_SUB(@seed_now, INTERVAL 4 DAY), 4, 3, '输出一版简历优化建议', '{"documentType":"resume"}', NULL),
  (5, 'trc_ai_demo_005', 6, 'COMMUNITY_REPLY', 'mock-fallback', 'community-assistant', 420, 'REJECTED', 'QUOTA_LIMIT', 120, 0, 120, 0.000000, 'PREMIUM', DATE_SUB(@seed_now, INTERVAL 9 HOUR), 0, 1, '今日社区 AI 预答额度已用尽', '{"reason":"daily_quota_limit"}', NULL),
  (6, 'trc_ai_demo_006', 6, 'INTERVIEW_TEXT', 'openai-compatible', 'gpt-4o-mini', 15000, 'FAILED', 'PROVIDER_TIMEOUT', 910, 0, 910, 0.000000, 'PREMIUM', DATE_SUB(@seed_now, INTERVAL 6 HOUR), 0, 5, '服务商超时，需重试', '{"sessionId":"sess_demo_text_002","stage":"reply"}', NULL),
  (7, 'trc_ai_demo_007', 2, 'STT', 'openai-compatible', 'gpt-4o-mini', 860, 'SUCCESS', NULL, 240, 120, 360, 0.001000, 'PREMIUM', DATE_SUB(@seed_now, INTERVAL 5 HOUR), 2, 1, '语音转写完成', '{"mode":"interview_voice"}', NULL),
  (8, 'trc_ai_demo_008', 2, 'TTS', 'openai-compatible', 'gpt-4o-mini', 640, 'SUCCESS', NULL, 110, 90, 200, 0.000800, 'PREMIUM', DATE_SUB(@seed_now, INTERVAL 4 HOUR), 2, 1, '文本转语音完成', '{"voice":"alloy"}', NULL),
  (9, 'trc_ai_demo_009', 5, 'INTERVIEW_TEXT', 'openai-compatible', 'gpt-4o-mini', 1730, 'SUCCESS', NULL, 700, 260, 960, 0.003600, 'PREMIUM', DATE_SUB(@seed_now, INTERVAL 12 HOUR), 5, 5, '完成一轮面试问答', '{"sessionId":"demo_premium_user_preview"}', NULL),
  (10, 'trc_ai_demo_010', 3, 'COMMUNITY_REPLY', 'mock-fallback', 'community-assistant', 390, 'SUCCESS', NULL, 180, 150, 330, 0.000200, 'FREE', DATE_SUB(@seed_now, INTERVAL 28 HOUR), 1, 1, '生成社区首评建议', '{"targetPostId":2}', NULL),
  (11, 'trc_ai_demo_011', 6, 'RESUME', 'openai-compatible', 'gpt-4o-mini', 1280, 'SUCCESS', NULL, 680, 300, 980, 0.003700, 'PREMIUM', DATE_SUB(@seed_now, INTERVAL 2 DAY), 4, 3, '输出项目经历重写建议', '{"documentType":"resume"}', NULL);

INSERT INTO bounty_tasks(id, enterprise_user_id, title, description, reward_description, status, accepted_submission_id, deadline_at, closed_at, created_at, updated_at)
VALUES
  (1, 9, '校招 JD 亮点整理', '请基于一份后端开发校招 JD，提炼岗位亮点、加分项和常见筛选关键词，形成一页结构化总结。', '企业内推机会 + 导师点评', 'OPEN', NULL, DATE_ADD(@seed_now, INTERVAL 5 DAY), NULL, DATE_SUB(@seed_now, INTERVAL 2 DAY), DATE_SUB(@seed_now, INTERVAL 2 DAY)),
  (2, 9, '数据分析岗位笔试题复盘', '整理一份常见数据分析笔试题型，并给出对应考察点与作答思路。', '远程体验面试资格', 'CLOSED', NULL, DATE_SUB(@seed_now, INTERVAL 1 DAY), DATE_SUB(@seed_now, INTERVAL 1 DAY), DATE_SUB(@seed_now, INTERVAL 6 DAY), DATE_SUB(@seed_now, INTERVAL 1 DAY));

INSERT INTO bounty_submissions(id, task_id, student_user_id, content_text, attachment_links, status, review_comment, reviewed_at, reviewer_user_id, created_at, updated_at)
VALUES
  (1, 1, 2, '已按“岗位职责 / 任职要求 / 隐含筛选项 / 面试侧重点”四个分区整理完成。', NULL, 'SUBMITTED', NULL, NULL, NULL, DATE_SUB(@seed_now, INTERVAL 36 HOUR), DATE_SUB(@seed_now, INTERVAL 36 HOUR)),
  (2, 1, 6, '补充了关键词词云和岗位亮点对照表，方便企业快速筛选。', NULL, 'REVIEWING', '企业正在查看中。', DATE_SUB(@seed_now, INTERVAL 12 HOUR), 9, DATE_SUB(@seed_now, INTERVAL 24 HOUR), DATE_SUB(@seed_now, INTERVAL 12 HOUR)),
  (3, 2, 3, '已按 SQL、统计、业务理解和案例分析四部分整理，并附简答模板。', NULL, 'ACCEPTED', '结构完整，适合继续扩展为笔试资料包。', DATE_SUB(@seed_now, INTERVAL 2 DAY), 9, DATE_SUB(@seed_now, INTERVAL 5 DAY), DATE_SUB(@seed_now, INTERVAL 2 DAY)),
  (4, 2, 5, '提供了一版题型清单，但业务分析部分还不够具体。', NULL, 'REJECTED', '需要补充案例分析与结论表达。', DATE_SUB(@seed_now, INTERVAL 2 DAY), 9, DATE_SUB(@seed_now, INTERVAL 5 DAY), DATE_SUB(@seed_now, INTERVAL 2 DAY));

UPDATE bounty_tasks
   SET accepted_submission_id = 3,
       updated_at = DATE_SUB(@seed_now, INTERVAL 2 DAY)
 WHERE id = 2;

SELECT 'admin demo data reset completed' AS message;
