SET NAMES utf8mb4;

START TRANSACTION;

SET @sync_time = '2026-04-12 21:20:00';

CREATE TEMPORARY TABLE tmp_governance_posts (
  seed_key      VARCHAR(16)   NOT NULL PRIMARY KEY,
  author_email  VARCHAR(255)  NOT NULL,
  old_title     VARCHAR(200)  NOT NULL,
  new_title     VARCHAR(200)  NOT NULL,
  tags          VARCHAR(255)  NOT NULL,
  content       TEXT          NOT NULL,
  risk_level    VARCHAR(20)   NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO tmp_governance_posts (
  seed_key, author_email, old_title, new_title, tags, content, risk_level
) VALUES
  ('P021', 'student.liujianing@bishe.local', '求助：有人私聊收费内推，信息真假怎么判断', '收费内推渠道整理，需要的同学自取', '收费内推,导流信息,风险内容', '手里整理了几条收费内推渠道和带投入口，想了解的同学可以先在评论里留一下联系方式，我再统一发群和表单。', 'HIGH'),
  ('P022', 'student.xuanran@bishe.local', '简历代投服务靠谱吗？有人发了外部联系方式', '简历代投服务报价整理，留下联系方式可细聊', '简历代投,导流信息,风险内容', '最近有人问代投服务怎么收费，我把几档“简历修改 + 代投跟进”的报价整理了一下。想看的可以先留外部联系方式，我这边再发完整说明。', 'HIGH'),
  ('P023', 'student.zhoumuyang@bishe.local', '笔试原题售卖群值不值得加，感觉描述很夸张', '笔试原题售卖群入口汇总，想进的自行领取', '笔试原题,导流信息,风险内容', '最近有人问原题群入口，我手里有几个更新比较快的群和资料包，想进的可以在评论区留言，我再统一拉群。', 'HIGH'),
  ('P024', 'student.heqingyan@bishe.local', 'Offer 比较时有人私聊收费咨询，这种情况正常吗', 'Offer 比较帖下有人私聊收费咨询，管理员可以帮看下吗', 'offer选择,收费咨询,风险判断', '我刚发了一条 offer 对比求助，结果有人私聊我说可以付费帮做决策分析。我不确定这类消息算不算正常咨询，先把情况同步出来，请管理员帮忙看看。', 'MEDIUM');

UPDATE posts p
JOIN users u
  ON u.id = p.user_id
 AND u.is_deleted = 0
JOIN tmp_governance_posts g
  ON g.author_email = u.email
 AND (p.title = g.old_title OR p.title = g.new_title)
SET p.title = g.new_title,
    p.tags = g.tags,
    p.content = g.content,
    p.moderation_status = 'REVIEW',
    p.risk_level = g.risk_level,
    p.updated_at = @sync_time
WHERE p.is_deleted = 0;

CREATE TEMPORARY TABLE tmp_governance_post_ids AS
SELECT
  g.seed_key,
  p.id AS post_id
FROM tmp_governance_posts g
JOIN users u
  ON u.email = g.author_email
 AND u.is_deleted = 0
JOIN posts p
  ON p.user_id = u.id
 AND p.title = g.new_title
 AND p.is_deleted = 0;

CREATE TEMPORARY TABLE tmp_governance_comments (
  comment_key    VARCHAR(24)   NOT NULL PRIMARY KEY,
  seed_key       VARCHAR(16)   NOT NULL,
  author_email   VARCHAR(255)  NOT NULL,
  content        TEXT          NOT NULL,
  created_at     DATETIME      NOT NULL,
  updated_at     DATETIME      NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO tmp_governance_comments (
  comment_key, seed_key, author_email, content, created_at, updated_at
) VALUES
  ('C021A', 'P021', 'student.zhoumuyang@bishe.local', '我这边也有付费内推群和外部表单入口，想要的同学可以直接私聊我。', '2026-04-11 18:10:00', '2026-04-12 20:10:00'),
  ('C022A', 'P022', 'student.heqingyan@bishe.local', '这类代投一般都走外部联系方式，先确认预算再聊具体方案。', '2026-04-11 18:35:00', '2026-04-12 20:25:00');

INSERT INTO comments (
  post_id,
  user_id,
  content,
  is_ai,
  moderation_status,
  risk_level,
  last_moderation_event_id,
  is_deleted,
  created_at,
  updated_at
)
SELECT
  p.post_id,
  u.id,
  c.content,
  0,
  'REVIEW',
  'HIGH',
  NULL,
  0,
  c.created_at,
  c.updated_at
FROM tmp_governance_comments c
JOIN tmp_governance_post_ids p
  ON p.seed_key = c.seed_key
JOIN users u
  ON u.email = c.author_email
 AND u.is_deleted = 0
LEFT JOIN comments existing
  ON existing.post_id = p.post_id
 AND existing.user_id = u.id
 AND existing.content = c.content
 AND existing.is_deleted = 0
WHERE existing.id IS NULL;

CREATE TEMPORARY TABLE tmp_governance_comment_ids AS
SELECT
  c.comment_key,
  existing.id AS comment_id
FROM tmp_governance_comments c
JOIN tmp_governance_post_ids p
  ON p.seed_key = c.seed_key
JOIN users u
  ON u.email = c.author_email
 AND u.is_deleted = 0
JOIN comments existing
  ON existing.post_id = p.post_id
 AND existing.user_id = u.id
 AND existing.content = c.content
 AND existing.is_deleted = 0;

SET @post_327 = (SELECT post_id FROM tmp_governance_post_ids WHERE seed_key = 'P021' LIMIT 1);
SET @post_328 = (SELECT post_id FROM tmp_governance_post_ids WHERE seed_key = 'P022' LIMIT 1);
SET @post_329 = (SELECT post_id FROM tmp_governance_post_ids WHERE seed_key = 'P023' LIMIT 1);
SET @post_330 = (SELECT post_id FROM tmp_governance_post_ids WHERE seed_key = 'P024' LIMIT 1);
SET @comment_c021a = (SELECT comment_id FROM tmp_governance_comment_ids WHERE comment_key = 'C021A' LIMIT 1);
SET @comment_c022a = (SELECT comment_id FROM tmp_governance_comment_ids WHERE comment_key = 'C022A' LIMIT 1);

UPDATE posts
SET moderation_status = 'PASS',
    risk_level = 'LOW',
    last_moderation_event_id = NULL,
    updated_at = @sync_time
WHERE id = 302
  AND is_deleted = 0;

UPDATE posts
SET moderation_status = 'REVIEW',
    risk_level = 'HIGH',
    updated_at = @sync_time
WHERE id = @post_327
  AND is_deleted = 0;

UPDATE posts
SET moderation_status = 'BLOCK',
    risk_level = 'HIGH',
    updated_at = @sync_time
WHERE id = @post_328
  AND is_deleted = 0;

UPDATE posts
SET moderation_status = 'REVIEW',
    risk_level = 'HIGH',
    updated_at = @sync_time
WHERE id = @post_329
  AND is_deleted = 0;

UPDATE posts
SET moderation_status = 'REVIEW',
    risk_level = 'MEDIUM',
    updated_at = @sync_time
WHERE id = @post_330
  AND is_deleted = 0;

UPDATE comments
SET moderation_status = 'BLOCK',
    risk_level = 'HIGH',
    updated_at = @sync_time
WHERE id = @comment_c021a
  AND is_deleted = 0;

UPDATE comments
SET moderation_status = 'REVIEW',
    risk_level = 'HIGH',
    updated_at = @sync_time
WHERE id = @comment_c022a
  AND is_deleted = 0;

UPDATE content_moderation_events
SET source_type = 'COMMUNITY_POST',
    target_type = 'POST',
    target_id = CAST(@post_327 AS CHAR),
    risk_level = 'HIGH',
    action = 'REVIEW',
    reason_code = 'POLICY_REVIEW_REQUIRED',
    masked_text = '收费内推渠道整理，需要人工复核。',
    operator_user_id = 0,
    created_at = '2026-04-12 17:50:00'
WHERE trace_id = 'trc_gov_demo_20260412_review_post_327';

INSERT INTO content_moderation_events (
  trace_id, source_type, target_type, target_id, risk_level, action, reason_code, masked_text, operator_user_id, created_at
)
SELECT
  'trc_gov_demo_20260412_review_post_327',
  'COMMUNITY_POST',
  'POST',
  CAST(@post_327 AS CHAR),
  'HIGH',
  'REVIEW',
  'POLICY_REVIEW_REQUIRED',
  '收费内推渠道整理，需要人工复核。',
  0,
  '2026-04-12 17:50:00'
FROM dual
WHERE @post_327 IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM content_moderation_events
    WHERE trace_id = 'trc_gov_demo_20260412_review_post_327'
  );

UPDATE content_moderation_events
SET source_type = 'COMMUNITY_POST',
    target_type = 'POST',
    target_id = CAST(@post_329 AS CHAR),
    risk_level = 'HIGH',
    action = 'REVIEW',
    reason_code = 'POLICY_REVIEW_REQUIRED',
    masked_text = '原题售卖群入口汇总，需要人工复核。',
    operator_user_id = 0,
    created_at = '2026-04-12 18:05:00'
WHERE trace_id = 'trc_gov_demo_20260412_review_post_329';

INSERT INTO content_moderation_events (
  trace_id, source_type, target_type, target_id, risk_level, action, reason_code, masked_text, operator_user_id, created_at
)
SELECT
  'trc_gov_demo_20260412_review_post_329',
  'COMMUNITY_POST',
  'POST',
  CAST(@post_329 AS CHAR),
  'HIGH',
  'REVIEW',
  'POLICY_REVIEW_REQUIRED',
  '原题售卖群入口汇总，需要人工复核。',
  0,
  '2026-04-12 18:05:00'
FROM dual
WHERE @post_329 IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM content_moderation_events
    WHERE trace_id = 'trc_gov_demo_20260412_review_post_329'
  );

UPDATE content_moderation_events
SET source_type = 'COMMUNITY_POST',
    target_type = 'POST',
    target_id = CAST(@post_330 AS CHAR),
    risk_level = 'MEDIUM',
    action = 'REVIEW',
    reason_code = 'POLICY_REVIEW_REQUIRED',
    masked_text = '收费咨询求助帖，继续人工复核。',
    operator_user_id = 0,
    created_at = '2026-04-12 18:15:00'
WHERE trace_id = 'trc_gov_demo_20260412_review_post_330';

INSERT INTO content_moderation_events (
  trace_id, source_type, target_type, target_id, risk_level, action, reason_code, masked_text, operator_user_id, created_at
)
SELECT
  'trc_gov_demo_20260412_review_post_330',
  'COMMUNITY_POST',
  'POST',
  CAST(@post_330 AS CHAR),
  'MEDIUM',
  'REVIEW',
  'POLICY_REVIEW_REQUIRED',
  '收费咨询求助帖，继续人工复核。',
  0,
  '2026-04-12 18:15:00'
FROM dual
WHERE @post_330 IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM content_moderation_events
    WHERE trace_id = 'trc_gov_demo_20260412_review_post_330'
  );

UPDATE content_moderation_events
SET source_type = 'COMMUNITY_COMMENT',
    target_type = 'COMMENT',
    target_id = CAST(@comment_c022a AS CHAR),
    risk_level = 'HIGH',
    action = 'REVIEW',
    reason_code = 'POLICY_REVIEW_REQUIRED',
    masked_text = '评论引导外部联系方式，需要人工复核。',
    operator_user_id = 0,
    created_at = '2026-04-12 18:25:00'
WHERE trace_id = 'trc_gov_demo_20260412_review_comment_c022a';

INSERT INTO content_moderation_events (
  trace_id, source_type, target_type, target_id, risk_level, action, reason_code, masked_text, operator_user_id, created_at
)
SELECT
  'trc_gov_demo_20260412_review_comment_c022a',
  'COMMUNITY_COMMENT',
  'COMMENT',
  CAST(@comment_c022a AS CHAR),
  'HIGH',
  'REVIEW',
  'POLICY_REVIEW_REQUIRED',
  '评论引导外部联系方式，需要人工复核。',
  0,
  '2026-04-12 18:25:00'
FROM dual
WHERE @comment_c022a IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM content_moderation_events
    WHERE trace_id = 'trc_gov_demo_20260412_review_comment_c022a'
  );

UPDATE content_moderation_events
SET source_type = 'COMMUNITY_POST',
    target_type = 'POST',
    target_id = CAST(@post_328 AS CHAR),
    risk_level = 'HIGH',
    action = 'BLOCK',
    reason_code = 'REPORT_DECISION_TAKE_DOWN',
    masked_text = NULL,
    operator_user_id = 1,
    created_at = '2026-04-12 20:40:00'
WHERE trace_id = 'trc_gov_demo_20260412_report_post_328';

INSERT INTO content_moderation_events (
  trace_id, source_type, target_type, target_id, risk_level, action, reason_code, masked_text, operator_user_id, created_at
)
SELECT
  'trc_gov_demo_20260412_report_post_328',
  'COMMUNITY_POST',
  'POST',
  CAST(@post_328 AS CHAR),
  'HIGH',
  'BLOCK',
  'REPORT_DECISION_TAKE_DOWN',
  NULL,
  1,
  '2026-04-12 20:40:00'
FROM dual
WHERE @post_328 IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM content_moderation_events
    WHERE trace_id = 'trc_gov_demo_20260412_report_post_328'
  );

UPDATE content_moderation_events
SET source_type = 'COMMUNITY_COMMENT',
    target_type = 'COMMENT',
    target_id = CAST(@comment_c021a AS CHAR),
    risk_level = 'HIGH',
    action = 'BLOCK',
    reason_code = 'REPORT_DECISION_TAKE_DOWN',
    masked_text = NULL,
    operator_user_id = 1,
    created_at = '2026-04-12 20:10:00'
WHERE trace_id = 'trc_gov_demo_20260412_report_comment_c021a';

INSERT INTO content_moderation_events (
  trace_id, source_type, target_type, target_id, risk_level, action, reason_code, masked_text, operator_user_id, created_at
)
SELECT
  'trc_gov_demo_20260412_report_comment_c021a',
  'COMMUNITY_COMMENT',
  'COMMENT',
  CAST(@comment_c021a AS CHAR),
  'HIGH',
  'BLOCK',
  'REPORT_DECISION_TAKE_DOWN',
  NULL,
  1,
  '2026-04-12 20:10:00'
FROM dual
WHERE @comment_c021a IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM content_moderation_events
    WHERE trace_id = 'trc_gov_demo_20260412_report_comment_c021a'
  );

UPDATE posts
SET last_moderation_event_id = (
      SELECT id
      FROM content_moderation_events
      WHERE trace_id = 'trc_gov_demo_20260412_review_post_327'
      LIMIT 1
    )
WHERE id = @post_327
  AND is_deleted = 0;

UPDATE posts
SET last_moderation_event_id = (
      SELECT id
      FROM content_moderation_events
      WHERE trace_id = 'trc_gov_demo_20260412_report_post_328'
      LIMIT 1
    )
WHERE id = @post_328
  AND is_deleted = 0;

UPDATE posts
SET last_moderation_event_id = (
      SELECT id
      FROM content_moderation_events
      WHERE trace_id = 'trc_gov_demo_20260412_review_post_329'
      LIMIT 1
    )
WHERE id = @post_329
  AND is_deleted = 0;

UPDATE posts
SET last_moderation_event_id = (
      SELECT id
      FROM content_moderation_events
      WHERE trace_id = 'trc_gov_demo_20260412_review_post_330'
      LIMIT 1
    )
WHERE id = @post_330
  AND is_deleted = 0;

UPDATE comments
SET last_moderation_event_id = (
      SELECT id
      FROM content_moderation_events
      WHERE trace_id = 'trc_gov_demo_20260412_report_comment_c021a'
      LIMIT 1
    )
WHERE id = @comment_c021a
  AND is_deleted = 0;

UPDATE comments
SET last_moderation_event_id = (
      SELECT id
      FROM content_moderation_events
      WHERE trace_id = 'trc_gov_demo_20260412_review_comment_c022a'
      LIMIT 1
    )
WHERE id = @comment_c022a
  AND is_deleted = 0;

INSERT INTO content_reports (
  id, reporter_user_id, target_type, target_id, reason_code, detail, status, latest_action, created_at, updated_at, closed_at
)
VALUES
  (361, 102, 'POST', CAST(@post_327 AS CHAR), 'RISK_LINK', '正文在公开区整理收费内推渠道并引导留联系方式，建议人工复核。', 'PENDING', 'NONE', '2026-04-12 20:10:00', '2026-04-12 20:10:00', NULL),
  (362, 103, 'COMMENT', CAST(@comment_c021a AS CHAR), 'UNSAFE_GUIDE', '评论直接引导私聊并提供外部群入口，建议下架。', 'ACCEPTED', 'TAKE_DOWN', '2026-04-12 18:20:00', '2026-04-12 20:10:00', '2026-04-12 20:10:00'),
  (363, 106, 'POST', CAST(@post_330 AS CHAR), 'OFF_TOPIC', '内容更像求助反馈，不足以直接认定违规，先保留记录。', 'REJECTED', 'NO_ACTION', '2026-04-12 18:45:00', '2026-04-12 19:30:00', '2026-04-12 19:30:00'),
  (364, 101, 'COMMENT', CAST(@comment_c022a AS CHAR), 'RISK_LINK', '评论在引导走外部联系方式，建议继续人工复核。', 'PENDING', 'NONE', '2026-04-12 20:25:00', '2026-04-12 20:25:00', NULL),
  (365, 105, 'POST', CAST(@post_328 AS CHAR), 'SPAM', '正文直接整理代投报价并引导联系方式，建议下架。', 'ACCEPTED', 'TAKE_DOWN', '2026-04-12 18:35:00', '2026-04-12 20:40:00', '2026-04-12 20:40:00')
ON DUPLICATE KEY UPDATE
  reporter_user_id = VALUES(reporter_user_id),
  target_type = VALUES(target_type),
  target_id = VALUES(target_id),
  reason_code = VALUES(reason_code),
  detail = VALUES(detail),
  status = VALUES(status),
  latest_action = VALUES(latest_action),
  created_at = VALUES(created_at),
  updated_at = VALUES(updated_at),
  closed_at = VALUES(closed_at);

INSERT INTO content_report_actions (
  id, report_id, operator_user_id, decision, action, comment, created_at
)
VALUES
  (371, 362, 1, 'ACCEPTED', 'TAKE_DOWN', '评论存在明确导流内容，已执行下架。', '2026-04-12 20:10:00'),
  (372, 363, 1, 'REJECTED', 'NO_ACTION', '当前更像风险求助反馈，不直接判定违规。', '2026-04-12 19:30:00'),
  (373, 365, 1, 'ACCEPTED', 'TAKE_DOWN', '正文包含代投报价和导流语义，已下架并记录审计。', '2026-04-12 20:40:00')
ON DUPLICATE KEY UPDATE
  report_id = VALUES(report_id),
  operator_user_id = VALUES(operator_user_id),
  decision = VALUES(decision),
  action = VALUES(action),
  comment = VALUES(comment),
  created_at = VALUES(created_at);

INSERT INTO audit_logs (
  id, trace_id, operator_user_id, action_type, target_type, target_id, detail_json, created_at
)
VALUES
  (901, 'trc_gov_demo_20260412_report_post_328', 1, 'REPORT_DECISION', 'POST', CAST(@post_328 AS CHAR), '{"reportId":365,"decision":"ACCEPTED","action":"TAKE_DOWN","comment":"正文包含代投报价和导流语义，已下架并记录审计。"}', '2026-04-12 20:40:00'),
  (902, 'trc_gov_demo_20260412_report_comment_c021a', 1, 'REPORT_DECISION', 'COMMENT', CAST(@comment_c021a AS CHAR), '{"reportId":362,"decision":"ACCEPTED","action":"TAKE_DOWN","comment":"评论存在明确导流内容，已执行下架。"}', '2026-04-12 20:10:00'),
  (903, 'trc_gov_demo_20260412_report_post_330', 1, 'REPORT_DECISION', 'POST', CAST(@post_330 AS CHAR), '{"reportId":363,"decision":"REJECTED","action":"NO_ACTION","comment":"当前更像风险求助反馈，不直接判定违规。"}', '2026-04-12 19:30:00')
ON DUPLICATE KEY UPDATE
  trace_id = VALUES(trace_id),
  operator_user_id = VALUES(operator_user_id),
  action_type = VALUES(action_type),
  target_type = VALUES(target_type),
  target_id = VALUES(target_id),
  detail_json = VALUES(detail_json),
  created_at = VALUES(created_at);

COMMIT;

SELECT
  id,
  title,
  moderation_status,
  risk_level,
  last_moderation_event_id
FROM posts
WHERE id IN (@post_327, @post_328, @post_329, @post_330)
ORDER BY id;

SELECT
  id,
  post_id,
  moderation_status,
  risk_level,
  LEFT(content, 80) AS content
FROM comments
WHERE id IN (@comment_c021a, @comment_c022a)
ORDER BY id;

SELECT
  id,
  target_type,
  target_id,
  reason_code,
  status,
  latest_action
FROM content_reports
WHERE id BETWEEN 361 AND 365
ORDER BY id;

SELECT
  trace_id,
  source_type,
  target_type,
  target_id,
  action,
  reason_code
FROM content_moderation_events
WHERE trace_id LIKE 'trc_gov_demo_20260412_%'
ORDER BY created_at, id;

SELECT
  id,
  trace_id,
  action_type,
  target_type,
  target_id
FROM audit_logs
WHERE id BETWEEN 901 AND 903
ORDER BY id;
