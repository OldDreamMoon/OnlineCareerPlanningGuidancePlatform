SET NAMES utf8mb4;

START TRANSACTION;

SET @sync_time = '2026-04-07 21:40:00';

UPDATE users
SET status = 'ACTIVE',
    updated_at = @sync_time
WHERE id = 111
  AND role = 'ENTERPRISE';

UPDATE users
SET status = 'PENDING',
    updated_at = @sync_time
WHERE id IN (137, 138)
  AND role = 'MENTOR';

UPDATE student_profiles
SET job_status = '春招后端投递中',
    gpa = '3.58/4.00',
    honors = '校级服务创新项目负责人',
    updated_at = @sync_time
WHERE user_id = 101;

UPDATE student_profiles
SET job_status = '前端方向求职准备中',
    gpa = '3.76/4.00',
    honors = '院级交互设计优秀作品奖',
    updated_at = @sync_time
WHERE user_id = 102;

UPDATE student_profiles
SET job_status = '暑期实习准备中',
    gpa = '3.83/4.00',
    honors = '研究生数据建模竞赛二等奖',
    updated_at = @sync_time
WHERE user_id = 103;

UPDATE student_profiles
SET job_status = '推荐算法 / 数据产品实习投递中',
    updated_at = @sync_time
WHERE user_id = 104;

UPDATE student_profiles
SET job_status = '后端方向春招冲刺中',
    updated_at = @sync_time
WHERE user_id = 105;

UPDATE student_profiles
SET job_status = '后端方向面试复盘中',
    gpa = '3.64/4.00',
    honors = '校级优秀毕业设计培育项目',
    updated_at = @sync_time
WHERE user_id = 106;

UPDATE student_profiles
SET job_status = '前端 / 增长方向双线投递中',
    updated_at = @sync_time
WHERE user_id = 112;

UPDATE mentor_profiles
SET price_fen = 9900,
    updated_at = @sync_time
WHERE user_id = 107;

UPDATE mentor_service_packages
SET package_name = '简历问诊',
    scene_code = 'RESUME_DIAGNOSIS',
    scene_label = '简历诊断',
    delivery_mode = 'TEXT_ASYNC',
    duration_minutes = NULL,
    price_fen = 9900,
    description = '适合先快速收一轮简历结构、项目主次和投递重点。',
    enabled = 1,
    sort_no = 1,
    updated_at = @sync_time
WHERE mentor_user_id = 107
  AND sort_no = 1;

UPDATE mentor_service_packages
SET package_name = '项目表达深挖',
    scene_code = 'PROJECT_STORYTELLING',
    scene_label = '项目表达',
    delivery_mode = 'APPOINTMENT',
    duration_minutes = 45,
    price_fen = 16900,
    description = '围绕项目主线、约束条件和追问准备做一次结构化复盘。',
    enabled = 1,
    sort_no = 2,
    updated_at = @sync_time
WHERE mentor_user_id = 107
  AND sort_no = 2;

INSERT INTO mentor_service_packages (
  mentor_user_id,
  package_name,
  scene_code,
  scene_label,
  delivery_mode,
  duration_minutes,
  price_fen,
  description,
  enabled,
  sort_no,
  created_at,
  updated_at
)
SELECT
  107,
  '模拟面试复盘',
  'MOCK_INTERVIEW_REVIEW',
  '模拟面试复盘',
  'APPOINTMENT',
  45,
  17900,
  '重点围绕高频追问、答题结构和表达稳定性做复盘打磨。',
  1,
  3,
  @sync_time,
  @sync_time
FROM dual
WHERE NOT EXISTS (
  SELECT 1
  FROM mentor_service_packages
  WHERE mentor_user_id = 107
    AND sort_no = 3
);

INSERT INTO mentor_service_packages (
  mentor_user_id,
  package_name,
  scene_code,
  scene_label,
  delivery_mode,
  duration_minutes,
  price_fen,
  description,
  enabled,
  sort_no,
  created_at,
  updated_at
)
SELECT
  107,
  '校招策略梳理',
  'CAMPUS_RECRUITMENT_STRATEGY',
  '校招投递策略',
  'APPOINTMENT',
  45,
  14900,
  '适合梳理 offer 选择、投递节奏、沟通边界和下一步行动优先级。',
  1,
  4,
  @sync_time,
  @sync_time
FROM dual
WHERE NOT EXISTS (
  SELECT 1
  FROM mentor_service_packages
  WHERE mentor_user_id = 107
    AND sort_no = 4
);

UPDATE consult_orders
SET scene_code = 'PROJECT_STORYTELLING'
WHERE scene_code = 'PROJECT_EXPRESSION';

UPDATE consult_orders
SET scene_code = 'MOCK_INTERVIEW_REVIEW'
WHERE scene_code = 'INTERVIEW_REVIEW';

UPDATE consult_orders
SET scene_code = 'CAMPUS_RECRUITMENT_STRATEGY'
WHERE scene_code = 'DELIVERY_STRATEGY';

UPDATE consult_orders
SET service_package_snapshot_json = JSON_OBJECT(
  'packageName',
  CASE scene_code
    WHEN 'RESUME_DIAGNOSIS' THEN '简历问诊'
    WHEN 'PROJECT_STORYTELLING' THEN '项目表达深挖'
    WHEN 'MOCK_INTERVIEW_REVIEW' THEN '模拟面试复盘'
    WHEN 'CAREER_DIRECTION' THEN '岗位方向选择'
    WHEN 'CAMPUS_RECRUITMENT_STRATEGY' THEN '校招策略梳理'
    WHEN 'OFFER_DECISION' THEN 'Offer 对比与决策'
    WHEN 'CAREER_TRANSITION' THEN '转行 / 跨专业求职'
    WHEN 'DATA_ANALYSIS' THEN '数据分析复盘'
    ELSE '咨询服务'
  END,
  'sceneCode', scene_code,
  'sceneLabel',
  CASE scene_code
    WHEN 'RESUME_DIAGNOSIS' THEN '简历诊断'
    WHEN 'PROJECT_STORYTELLING' THEN '项目表达'
    WHEN 'MOCK_INTERVIEW_REVIEW' THEN '模拟面试复盘'
    WHEN 'CAREER_DIRECTION' THEN '岗位方向选择'
    WHEN 'CAMPUS_RECRUITMENT_STRATEGY' THEN '校招投递策略'
    WHEN 'OFFER_DECISION' THEN 'Offer 对比与决策'
    WHEN 'CAREER_TRANSITION' THEN '转行 / 跨专业求职'
    WHEN 'DATA_ANALYSIS' THEN '数据分析'
    ELSE '综合咨询'
  END,
  'deliveryMode',
  CASE
    WHEN appointment_start_at IS NOT NULL OR appointment_end_at IS NOT NULL THEN 'APPOINTMENT'
    ELSE 'TEXT_ASYNC'
  END,
  'durationMinutes',
  CASE
    WHEN appointment_start_at IS NOT NULL AND appointment_end_at IS NOT NULL
      THEN TIMESTAMPDIFF(MINUTE, appointment_start_at, appointment_end_at)
    ELSE NULL
  END,
  'priceFen', amount_fen,
  'description',
  CASE scene_code
    WHEN 'RESUME_DIAGNOSIS' THEN '优先帮助学生快速收拢简历结构、项目排序和投递重点。'
    WHEN 'PROJECT_STORYTELLING' THEN '围绕业务主线、关键动作和结果指标打磨项目表达。'
    WHEN 'MOCK_INTERVIEW_REVIEW' THEN '聚焦答题结构、追问应对和表达稳定性。'
    WHEN 'CAREER_DIRECTION' THEN '聚焦岗位方向、阶段性目标和行动计划。'
    WHEN 'CAMPUS_RECRUITMENT_STRATEGY' THEN '聚焦投递节奏、offer 权衡和沟通策略。'
    WHEN 'OFFER_DECISION' THEN '聚焦 offer 对比、取舍维度和入职准备。'
    WHEN 'CAREER_TRANSITION' THEN '聚焦转行路径和材料重组。'
    WHEN 'DATA_ANALYSIS' THEN '聚焦分析思路、指标拆解和业务表达。'
    ELSE '帮助学生收口当前最需要解决的问题。'
  END
)
WHERE service_package_snapshot_json IS NULL
   OR service_package_snapshot_json = '';

INSERT INTO payment_records (
  order_no,
  channel,
  mode,
  provider_trade_no,
  amount_fen,
  status,
  idempotency_key,
  raw_callback,
  created_at,
  updated_at
)
SELECT
  o.order_no,
  'ALIPAY',
  COALESCE((
    SELECT p.mode
    FROM payment_records p
    WHERE p.order_no = o.order_no
      AND p.status = 'SUCCESS'
    ORDER BY p.id DESC
    LIMIT 1
  ), 'SANDBOX'),
  CONCAT('REFUND-', o.order_no),
  o.amount_fen,
  'REFUND_SUCCESS',
  CONCAT('REFUND_SUCCESS:', o.order_no),
  NULL,
  COALESCE((
    SELECT MAX(a.reviewed_at)
    FROM consult_after_sales_requests a
    WHERE a.order_no = o.order_no
      AND a.status = 'APPROVED'
  ), o.closed_at, o.updated_at),
  COALESCE((
    SELECT MAX(a.reviewed_at)
    FROM consult_after_sales_requests a
    WHERE a.order_no = o.order_no
      AND a.status = 'APPROVED'
  ), o.closed_at, o.updated_at)
FROM consult_orders o
WHERE o.status = 'REFUNDED'
  AND EXISTS (
    SELECT 1
    FROM consult_after_sales_requests a
    WHERE a.order_no = o.order_no
      AND a.status = 'APPROVED'
  )
  AND NOT EXISTS (
    SELECT 1
    FROM payment_records p
    WHERE p.order_no = o.order_no
      AND p.status = 'REFUND_SUCCESS'
  );

COMMIT;
