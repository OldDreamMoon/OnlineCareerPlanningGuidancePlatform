SET NAMES utf8mb4;

START TRANSACTION;

SET @admin_user_id = (
  SELECT id
  FROM users
  WHERE role = 'ADMIN'
  ORDER BY id
  LIMIT 1
);

SET @student_104 = 104;
SET @student_105 = 105;
SET @student_112 = 112;
SET @mentor_108 = 108;
SET @mentor_109 = 109;
SET @mentor_113 = 113;

UPDATE users
SET email = 'student.chensiyu@bishe.local',
    display_name = '陈思语',
    real_name = '陈思语',
    status = 'ACTIVE',
    updated_at = '2026-04-05 16:08:00'
WHERE id = @student_104;

UPDATE users
SET email = 'student.sunzeyu@bishe.local',
    display_name = '孙泽宇',
    real_name = '孙泽宇',
    status = 'ACTIVE',
    updated_at = '2026-04-05 16:08:00'
WHERE id = @student_105;

UPDATE users
SET email = 'student.linjiaqi@bishe.local',
    display_name = '林嘉琪',
    real_name = '林嘉琪',
    status = 'ACTIVE',
    updated_at = '2026-04-05 16:08:00'
WHERE id = @student_112;

UPDATE users
SET display_name = '导师韩雪',
    real_name = '韩雪',
    status = 'ACTIVE',
    updated_at = '2026-04-05 16:08:00'
WHERE id = @mentor_108;

UPDATE users
SET display_name = '导师沈越',
    real_name = '沈越',
    status = 'PENDING',
    updated_at = '2026-04-05 16:08:00'
WHERE id = @mentor_109;

UPDATE users
SET email = 'mentor.chenzhi@bishe.local',
    display_name = '导师陈知',
    real_name = '陈知',
    status = 'ACTIVE',
    updated_at = '2026-04-05 16:08:00'
WHERE id = @mentor_113;

INSERT INTO student_profiles (
  user_id,
  school_name,
  school_name_key,
  major,
  grade,
  gpa,
  target_position,
  honors,
  github,
  portfolio,
  skill_tags,
  self_intro,
  social_links_json,
  created_at,
  updated_at
)
VALUES
  (
    @student_104,
    '华东理工学院',
    '华东理工学院',
    '智能科学与技术',
    '大四',
    '3.62/4.00',
    '推荐算法 / 数据产品实习生',
    '校级算法建模竞赛二等奖；院级优秀项目负责人',
    'https://github.com/chensiyu-lab',
    'https://chensiyu-portfolio.example.com',
    'Python,推荐系统,SQL,实验设计,项目复盘',
    '最近在准备推荐算法和数据产品相关实习，希望把课程项目里的召回、排序和实验评估讲出业务目标、指标边界和真实约束。',
    JSON_ARRAY(
      JSON_OBJECT('platform', 'GITHUB', 'value', 'github.com/chensiyu-lab'),
      JSON_OBJECT('platform', 'PORTFOLIO', 'value', 'chensiyu-portfolio.example.com')
    ),
    '2026-03-18 21:00:00',
    '2026-04-05 16:08:00'
  ),
  (
    @student_105,
    '江南工业大学',
    '江南工业大学',
    '软件工程',
    '大四',
    '3.48/4.00',
    '后端开发工程师',
    '校级创新实践项目负责人',
    'https://github.com/sunzeyu-dev',
    NULL,
    'Java,Spring Boot,MySQL,Redis,系统设计',
    '正在把校园二手交易平台项目重构成更适合校招表达的案例，重点补搜索、推荐和风控链路的业务价值、技术取舍和系统设计追问准备。',
    JSON_ARRAY(
      JSON_OBJECT('platform', 'GITHUB', 'value', 'github.com/sunzeyu-dev')
    ),
    '2026-03-18 21:05:00',
    '2026-04-05 16:08:00'
  ),
  (
    @student_112,
    '临海大学',
    '临海大学',
    '数字媒体技术',
    '大四',
    '3.71/4.00',
    '前端开发 / 增长产品实习生',
    '校级优秀前端项目奖；增长实验社负责人',
    'https://github.com/linjiaqi-lab',
    'https://linjiaqi-portfolio.example.com',
    'React,TypeScript,性能优化,数据埋点,增长分析',
    '同时在看前端开发和增长产品方向，最近在整理作品集、性能优化案例以及 Offer 对比判断，希望把项目结果和增长思路讲得更清楚。',
    JSON_ARRAY(
      JSON_OBJECT('platform', 'GITHUB', 'value', 'github.com/linjiaqi-lab'),
      JSON_OBJECT('platform', 'PORTFOLIO', 'value', 'linjiaqi-portfolio.example.com')
    ),
    '2026-03-18 21:10:00',
    '2026-04-05 16:08:00'
  )
ON DUPLICATE KEY UPDATE
  school_name = VALUES(school_name),
  school_name_key = VALUES(school_name_key),
  major = VALUES(major),
  grade = VALUES(grade),
  gpa = VALUES(gpa),
  target_position = VALUES(target_position),
  honors = VALUES(honors),
  github = VALUES(github),
  portfolio = VALUES(portfolio),
  skill_tags = VALUES(skill_tags),
  self_intro = VALUES(self_intro),
  social_links_json = VALUES(social_links_json),
  updated_at = VALUES(updated_at);

INSERT INTO mentor_profiles (
  user_id,
  expertise_tags,
  service_scenes,
  bio,
  suitable_for,
  not_suitable_for,
  prep_materials,
  reply_rhythm,
  price_fen,
  is_available,
  company_name,
  job_title,
  avatar_url,
  avatar_updated_at,
  approval_status,
  total_orders,
  avg_rating,
  created_at,
  updated_at
)
VALUES
  (
    @mentor_108,
    '系统设计,项目复盘,简历诊断,职业规划',
    '简历诊断,项目表达,模拟面试复盘,Offer 对比与决策',
    '持续参与后端与平台工程校招面试，擅长把项目约束、设计取舍和结果指标拆成结构化表达，也会帮助学生校正投递节奏。',
    '适合已有后端或数据平台项目，希望补系统设计、项目复盘和校招节奏判断的同学。',
    '不适合完全没有可讨论项目材料、只想临时拿模板答案的同学。',
    '简历、目标岗位 JD、项目结构图或关键链路说明、最近面试反馈。',
    '通常在 12 小时内给到第一轮反馈，复杂问题会在 24 小时内补充第二轮建议。',
    13900,
    1,
    '澄川科技',
    '资深后端工程师 / 校招面试官',
    'https://api.dicebear.com/7.x/notionists/svg?seed=HanXue',
    '2026-04-05 16:08:00',
    'APPROVED',
    3,
    4.00,
    '2026-03-16 20:10:00',
    '2026-04-05 16:08:00'
  ),
  (
    @mentor_109,
    '算法岗辅导,表达纠偏,项目深挖,转岗规划',
    '简历诊断,项目表达,模拟面试复盘,岗位方向选择',
    '主要帮助算法与后端交叉方向学生梳理项目亮点、转岗表达和技术深挖，但当前正在补充排期说明与服务承诺。',
    '适合已有算法、推荐或服务端项目，想把岗位方向和项目表达收口清楚的同学。',
    '不适合还没有任何项目材料、也暂时不愿补完整体目标岗位信息的同学。',
    '简历、目标岗位列表、最近项目复盘记录、最想重点纠偏的 1-2 段经历。',
    '当前处于重新开放排期前的审核阶段，资料完善后会按预约顺序恢复接单。',
    15900,
    0,
    '星律智能',
    '算法工程师 / 求职辅导导师',
    'https://api.dicebear.com/7.x/notionists/svg?seed=ShenYue',
    '2026-04-05 16:08:00',
    'PENDING',
    1,
    0.00,
    '2026-03-16 20:15:00',
    '2026-04-05 16:08:00'
  ),
  (
    @mentor_113,
    '数据产品,项目复盘,职业规划,简历诊断',
    '岗位方向选择,项目表达,简历诊断,Offer 对比与决策',
    '长期参与数据产品与分析岗位面试，擅长帮助学生把课程项目、业务问题和结果指标讲成完整闭环。',
    '适合想投数据产品、商业分析或兼顾前端数据化方向的同学。',
    '不适合只想快速改措辞、暂时不愿梳理项目背景和目标岗位的同学。',
    '简历、项目材料、目标岗位 JD、最近一次面试或投递反馈。',
    '通常在 24 小时内给到结构化建议，涉及岗位选择会补一轮决策框架。',
    15900,
    1,
    '沐川科技',
    '数据产品负责人 / 校招导师',
    'https://api.dicebear.com/7.x/notionists/svg?seed=ChenZhi',
    '2026-04-05 16:08:00',
    'APPROVED',
    0,
    0.00,
    '2026-03-17 18:39:27',
    '2026-04-05 16:08:00'
  )
ON DUPLICATE KEY UPDATE
  expertise_tags = VALUES(expertise_tags),
  service_scenes = VALUES(service_scenes),
  bio = VALUES(bio),
  suitable_for = VALUES(suitable_for),
  not_suitable_for = VALUES(not_suitable_for),
  prep_materials = VALUES(prep_materials),
  reply_rhythm = VALUES(reply_rhythm),
  price_fen = VALUES(price_fen),
  is_available = VALUES(is_available),
  company_name = VALUES(company_name),
  job_title = VALUES(job_title),
  avatar_url = VALUES(avatar_url),
  avatar_updated_at = VALUES(avatar_updated_at),
  approval_status = VALUES(approval_status),
  total_orders = VALUES(total_orders),
  avg_rating = VALUES(avg_rating),
  updated_at = VALUES(updated_at);

SET @mentor_108_cert_id = (
  SELECT id
  FROM certification_submissions
  WHERE user_id = @mentor_108
    AND is_current = 1
  ORDER BY submitted_at DESC, id DESC
  LIMIT 1
);

UPDATE certification_submissions
SET user_role = 'MENTOR',
    real_name = '韩雪',
    company_name = '澄川科技',
    job_title = '资深后端工程师 / 校招面试官',
    status = 'APPROVED',
    review_note = '资料完整，当前可正常公开展示导师名片与服务信息。',
    reviewed_by = @admin_user_id,
    reviewed_at = '2026-03-19 19:20:00',
    previous_submission_id = NULL,
    is_current = 1,
    submitted_at = '2026-03-18 22:10:00',
    created_at = '2026-03-18 22:10:00',
    updated_at = '2026-04-05 16:08:00'
WHERE id = @mentor_108_cert_id;

INSERT INTO certification_submissions (
  user_id,
  user_role,
  real_name,
  company_name,
  job_title,
  status,
  review_note,
  reviewed_by,
  reviewed_at,
  previous_submission_id,
  is_current,
  submitted_at,
  created_at,
  updated_at
)
SELECT
  @mentor_108,
  'MENTOR',
  '韩雪',
  '澄川科技',
  '资深后端工程师 / 校招面试官',
  'APPROVED',
  '资料完整，当前可正常公开展示导师名片与服务信息。',
  @admin_user_id,
  '2026-03-19 19:20:00',
  NULL,
  1,
  '2026-03-18 22:10:00',
  '2026-03-18 22:10:00',
  '2026-04-05 16:08:00'
WHERE @mentor_108_cert_id IS NULL;

SET @mentor_109_cert_id = (
  SELECT id
  FROM certification_submissions
  WHERE user_id = @mentor_109
    AND is_current = 1
  ORDER BY submitted_at DESC, id DESC
  LIMIT 1
);

UPDATE certification_submissions
SET user_role = 'MENTOR',
    real_name = '沈越',
    company_name = '星律智能',
    job_title = '算法工程师 / 求职辅导导师',
    status = 'PENDING',
    review_note = '正在补充排期与服务时效说明，审核通过后恢复公开接单。',
    reviewed_by = NULL,
    reviewed_at = NULL,
    previous_submission_id = NULL,
    is_current = 1,
    submitted_at = '2026-04-02 17:30:00',
    created_at = '2026-04-02 17:30:00',
    updated_at = '2026-04-05 16:08:00'
WHERE id = @mentor_109_cert_id;

INSERT INTO certification_submissions (
  user_id,
  user_role,
  real_name,
  company_name,
  job_title,
  status,
  review_note,
  reviewed_by,
  reviewed_at,
  previous_submission_id,
  is_current,
  submitted_at,
  created_at,
  updated_at
)
SELECT
  @mentor_109,
  'MENTOR',
  '沈越',
  '星律智能',
  '算法工程师 / 求职辅导导师',
  'PENDING',
  '正在补充排期与服务时效说明，审核通过后恢复公开接单。',
  NULL,
  NULL,
  NULL,
  1,
  '2026-04-02 17:30:00',
  '2026-04-02 17:30:00',
  '2026-04-05 16:08:00'
WHERE @mentor_109_cert_id IS NULL;

SET @mentor_113_cert_id = (
  SELECT id
  FROM certification_submissions
  WHERE user_id = @mentor_113
    AND is_current = 1
  ORDER BY submitted_at DESC, id DESC
  LIMIT 1
);

UPDATE certification_submissions
SET user_role = 'MENTOR',
    real_name = '陈知',
    company_name = '沐川科技',
    job_title = '数据产品负责人 / 校招导师',
    status = 'APPROVED',
    review_note = '资料完整，导师侧演示账号已通过认证审核，可正常展示资料与服务能力。',
    reviewed_by = @admin_user_id,
    reviewed_at = '2026-03-18 18:20:00',
    previous_submission_id = NULL,
    is_current = 1,
    submitted_at = '2026-03-17 18:39:27',
    created_at = '2026-03-17 18:39:27',
    updated_at = '2026-04-05 16:08:00'
WHERE id = @mentor_113_cert_id;

INSERT INTO certification_submissions (
  user_id,
  user_role,
  real_name,
  company_name,
  job_title,
  status,
  review_note,
  reviewed_by,
  reviewed_at,
  previous_submission_id,
  is_current,
  submitted_at,
  created_at,
  updated_at
)
SELECT
  @mentor_113,
  'MENTOR',
  '陈知',
  '沐川科技',
  '数据产品负责人 / 校招导师',
  'APPROVED',
  '资料完整，导师侧演示账号已通过认证审核，可正常展示资料与服务能力。',
  @admin_user_id,
  '2026-03-18 18:20:00',
  NULL,
  1,
  '2026-03-17 18:39:27',
  '2026-03-17 18:39:27',
  '2026-04-05 16:08:00'
WHERE @mentor_113_cert_id IS NULL;

UPDATE consult_orders
SET question_text = '请老师帮我梳理数据产品岗案例拆解框架，重点看需求定义、指标口径和汇报结构。',
    updated_at = '2026-04-05 16:08:00',
    scene_code = 'PROJECT_EXPRESSION',
    source_page = 'MENTOR_MARKETPLACE_RECOMMENDATION',
    question_payload_json = JSON_OBJECT(
      'background', '我准备投数据产品和商业分析方向，手上有一个留存分析案例，但当前表达更像课程作业。',
      'expectedHelp', '希望把需求背景、分析动作、指标口径和结论拆成更适合面试的结构。',
      'primaryConcern', '担心案例讲不出业务目标和分析判断。',
      'additionalNotes', '如果能顺带看下汇报结构会更好。',
      'attemptedActions', '已经整理了一版案例文档，但仍然偏平。'
    ),
    problem_summary = '希望梳理数据产品案例的业务背景、指标口径和汇报结构。',
    core_questions_json = JSON_ARRAY('案例拆解该先讲问题还是先讲方法', '指标口径怎么解释更稳', '汇报结构如何避免像课程作业'),
    expected_outcomes_json = JSON_ARRAY('形成一版更适合数据产品岗的案例讲法', '明确需要补充的业务背景和结论表达'),
    selected_material_types = 'RESUME,PROJECT_MATERIAL,SUPPLEMENTARY',
    prep_sheet_snapshot_json = JSON_OBJECT(
      'scene', '项目表达',
      'summaryDraft', '希望把留存分析案例讲成完整的数据产品案例。',
      'coreQuestions', JSON_ARRAY('如何解释指标口径', '汇报结构如何更有业务感'),
      'suggestedMaterials', JSON_ARRAY('简历', '案例文档', '目标岗位 JD'),
      'expectedOutcomes', JSON_ARRAY('拿到一版更完整的案例表达', '明确后续补充项')
    )
WHERE order_no = 'CONS20260304003';

UPDATE consult_orders
SET updated_at = '2026-04-05 16:08:00',
    scene_code = 'PROJECT_EXPRESSION',
    source_page = 'MENTOR_MARKETPLACE_RECOMMENDATION',
    question_payload_json = JSON_OBJECT(
      'background', '原本想做算法岗转后端岗的表达纠偏，但导师未按约定时间响应。',
      'expectedHelp', '希望把转岗项目表达和岗位说明重新收口。',
      'primaryConcern', '当前售后已结束，后续若继续咨询会重新下单。',
      'additionalNotes', '本单保留为历史退款订单。',
      'attemptedActions', '已整理项目清单和岗位列表。'
    ),
    problem_summary = '原计划做转岗表达纠偏，因导师超时未答已退款结束。',
    core_questions_json = JSON_ARRAY('算法经历如何转成后端表达', '转岗时项目亮点如何取舍'),
    expected_outcomes_json = JSON_ARRAY('保留历史退款链路', '后续重新预约时能直接衔接问题背景'),
    selected_material_types = 'RESUME,PROJECT_MATERIAL,JOB_DESCRIPTION',
    prep_sheet_snapshot_json = JSON_OBJECT(
      'scene', '项目表达',
      'summaryDraft', '原计划做算法转后端表达纠偏，当前订单已退款结束。',
      'coreQuestions', JSON_ARRAY('转岗表达如何收口', '项目亮点如何重排'),
      'suggestedMaterials', JSON_ARRAY('简历', '项目清单', '目标岗位列表'),
      'expectedOutcomes', JSON_ARRAY('保留问题背景', '后续重新预约时可继续沿用')
    )
WHERE order_no = 'CONS20260305004';

UPDATE consult_orders
SET question_text = '我想先预约一次推荐项目简历诊断，但材料还不够完整，准备补齐后再正式咨询。',
    updated_at = '2026-04-05 16:08:00',
    closed_at = '2026-03-07 12:30:00',
    scene_code = 'RESUME_DIAGNOSIS',
    source_page = 'MENTOR_MARKETPLACE_FAVORITES',
    question_payload_json = JSON_OBJECT(
      'background', '想做推荐系统项目的简历诊断，但目前实验结果和项目总结还没整理完整。',
      'expectedHelp', '先保留需求背景，后续补齐材料再重新预约。',
      'primaryConcern', '担心当前材料不够支撑一次高质量咨询。',
      'additionalNotes', '本单为主动取消的历史记录。',
      'attemptedActions', '已经列了需要补的实验结果和项目摘要。'
    ),
    problem_summary = '材料尚未准备完整，先取消本次预约并保留后续再约意向。',
    core_questions_json = JSON_ARRAY('推荐项目简历诊断需要准备哪些材料', '实验结果应该整理到什么程度'),
    expected_outcomes_json = JSON_ARRAY('明确后续补充材料清单', '保留后续重新下单的背景'),
    selected_material_types = 'RESUME,PROJECT_MATERIAL',
    prep_sheet_snapshot_json = JSON_OBJECT(
      'scene', '简历诊断',
      'summaryDraft', '计划做推荐项目简历诊断，当前先补齐材料后再约。',
      'coreQuestions', JSON_ARRAY('材料准备到什么程度再下单', '实验结果如何整理'),
      'suggestedMaterials', JSON_ARRAY('简历', '项目总结', '实验结果截图'),
      'expectedOutcomes', JSON_ARRAY('明确补充清单', '保留后续再约背景')
    )
WHERE order_no = 'CONS20260307005';

UPDATE consult_orders
SET updated_at = '2026-04-05 16:08:00',
    scene_code = 'INTERVIEW_REVIEW',
    source_page = 'MENTOR_MARKETPLACE',
    question_payload_json = JSON_OBJECT(
      'background', '做完 AI 模拟面试后，希望继续补系统设计题的结构化表达和项目深挖。',
      'expectedHelp', '重点想把需求、约束、方案、权衡四层讲法练顺。',
      'primaryConcern', '担心自己回答系统设计题时总是直接讲技术点。',
      'additionalNotes', '这单已经顺利完成，评价也已提交。',
      'attemptedActions', '整理了一版系统设计答题提纲。'
    ),
    problem_summary = '希望围绕系统设计与项目深挖做一次结构化复盘。',
    core_questions_json = JSON_ARRAY('系统设计回答如何先讲约束', '项目深挖时如何补充权衡和风险'),
    expected_outcomes_json = JSON_ARRAY('形成一版更稳定的系统设计回答结构', '明确后续要继续补的项目细节'),
    selected_material_types = 'RESUME,PROJECT_MATERIAL,SUPPLEMENTARY',
    prep_sheet_snapshot_json = JSON_OBJECT(
      'scene', '模拟面试复盘',
      'summaryDraft', '希望围绕系统设计和项目深挖做一次结构化复盘。',
      'coreQuestions', JSON_ARRAY('如何先讲约束再讲方案', '如何组织项目深挖'),
      'suggestedMaterials', JSON_ARRAY('简历', '模拟面试复盘', '项目结构图'),
      'expectedOutcomes', JSON_ARRAY('拿到一版稳定答题结构', '明确下一步补充项')
    )
WHERE order_no = 'CONS20260308006';

UPDATE consult_orders
SET status = 'REFUNDED',
    closed_at = '2026-04-04 05:48:23',
    updated_at = '2026-04-04 05:48:23'
WHERE order_no = 'CONS-MQA-20260401-004';

UPDATE consult_orders
SET status = 'CANCELED',
    closed_at = '2026-04-01 09:12:00',
    updated_at = '2026-04-01 09:12:00'
WHERE order_no = 'CONS-MQA-20260401-007';

UPDATE consult_orders
SET status = 'REFUNDED',
    closed_at = '2026-04-03 10:38:45',
    updated_at = '2026-04-03 10:38:45'
WHERE order_no = 'CONS-MQA-20260401-011';

DELETE FROM payment_records
WHERE order_no LIKE 'CONS-MQA-20260401-%';

INSERT INTO payment_records (
  order_no,
  channel,
  mode,
  provider_trade_no,
  amount_fen,
  status,
  idempotency_key,
  created_at,
  updated_at
)
SELECT
  c.order_no,
  'ALIPAY',
  'SANDBOX',
  CONCAT('sandbox_', LOWER(REPLACE(c.order_no, '-', '_')), '_', c.payment_suffix),
  o.amount_fen,
  c.payment_status,
  CONCAT('seed_', LOWER(REPLACE(c.order_no, '-', '_')), '_', LOWER(c.payment_status)),
  c.payment_time,
  c.payment_time
FROM (
  SELECT 'CONS-MQA-20260401-001' AS order_no, 'SUCCESS' AS payment_status, 'pay' AS payment_suffix, '2026-03-21 04:38:17' AS payment_time
  UNION ALL SELECT 'CONS-MQA-20260401-002', 'SUCCESS', 'pay', '2026-03-31 08:38:17'
  UNION ALL SELECT 'CONS-MQA-20260401-003', 'SUCCESS', 'pay', '2026-03-25 18:38:17'
  UNION ALL SELECT 'CONS-MQA-20260401-004', 'SUCCESS', 'pay', '2026-04-02 18:38:17'
  UNION ALL SELECT 'CONS-MQA-20260401-004', 'REFUND_SUCCESS', 'refund', '2026-04-04 05:48:23'
  UNION ALL SELECT 'CONS-MQA-20260401-005', 'SUCCESS', 'pay', '2026-03-26 12:38:17'
  UNION ALL SELECT 'CONS-MQA-20260401-006', 'SUCCESS', 'pay', '2026-03-29 00:38:17'
  UNION ALL SELECT 'CONS-MQA-20260401-006', 'REFUND_SUCCESS', 'refund', '2026-03-29 10:38:17'
  UNION ALL SELECT 'CONS-MQA-20260401-009', 'SUCCESS', 'pay', '2026-03-27 06:38:17'
  UNION ALL SELECT 'CONS-MQA-20260401-010', 'SUCCESS', 'pay', '2026-04-01 08:38:17'
  UNION ALL SELECT 'CONS-MQA-20260401-011', 'SUCCESS', 'pay', '2026-04-02 10:38:17'
  UNION ALL SELECT 'CONS-MQA-20260401-011', 'REFUND_SUCCESS', 'refund', '2026-04-03 10:38:45'
  UNION ALL SELECT 'CONS-MQA-20260401-012', 'SUCCESS', 'pay', '2026-03-28 12:38:17'
) c
JOIN consult_orders o ON o.order_no = c.order_no;

DELETE FROM consult_after_sales_requests
WHERE order_no IN (
  'CONS-MQA-20260401-004',
  'CONS-MQA-20260401-006',
  'CONS-MQA-20260401-011'
);

INSERT INTO consult_after_sales_requests (
  order_no,
  requester_user_id,
  request_type,
  status,
  reason,
  review_note,
  reviewer_user_id,
  auto_triggered,
  reviewed_at,
  created_at,
  updated_at
)
VALUES
  (
    'CONS-MQA-20260401-004',
    106,
    'REFUND',
    'APPROVED',
    '导师在规定时限内未正式答复，系统已自动发起售后退款',
    '系统自动审批：导师超时未答',
    @admin_user_id,
    1,
    '2026-04-04 05:48:23',
    '2026-04-04 05:48:23',
    '2026-04-04 05:48:23'
  ),
  (
    'CONS-MQA-20260401-006',
    101,
    'REFUND',
    'APPROVED',
    '学生本周时间冲突，申请退款并保留后续改约沟通。',
    '已确认学生主动退款诉求，后续可重新预约。',
    @admin_user_id,
    0,
    '2026-03-29 10:38:17',
    '2026-03-29 10:38:17',
    '2026-03-29 10:38:17'
  ),
  (
    'CONS-MQA-20260401-011',
    101,
    'REFUND',
    'APPROVED',
    '导师在规定时限内未正式答复，系统已自动发起售后退款',
    '系统自动审批：导师超时未答',
    @admin_user_id,
    1,
    '2026-04-03 10:38:45',
    '2026-04-03 10:38:45',
    '2026-04-03 10:38:45'
  );

DELETE FROM notification_dispatch_attempts
WHERE job_id IN (
  SELECT id
  FROM notification_dispatch_jobs
  WHERE notification_id IN (
    SELECT id
    FROM notifications
    WHERE category = 'CONSULT'
      AND ref_id IN (
        'CONS-MQA-20260401-004',
        'CONS-MQA-20260401-006',
        'CONS-MQA-20260401-011'
      )
  )
);

DELETE FROM notification_dispatch_jobs
WHERE notification_id IN (
  SELECT id
  FROM notifications
  WHERE category = 'CONSULT'
    AND ref_id IN (
      'CONS-MQA-20260401-004',
      'CONS-MQA-20260401-006',
      'CONS-MQA-20260401-011'
    )
);

DELETE FROM notifications
WHERE category = 'CONSULT'
  AND ref_id IN (
    'CONS-MQA-20260401-004',
    'CONS-MQA-20260401-006',
    'CONS-MQA-20260401-011'
  );

INSERT INTO notifications (
  user_id,
  type,
  category,
  title,
  content,
  ref_type,
  ref_id,
  action_code,
  priority,
  event_id,
  payload_json,
  is_read,
  created_at,
  updated_at
)
VALUES
  (
    106,
    'CONSULT_REFUNDED',
    'CONSULT',
    '咨询订单已退款',
    '导师超时未答，系统已自动发起售后并完成退款。',
    'CONSULT_ORDER',
    'CONS-MQA-20260401-004',
    'VIEW_CONSULT_ORDER',
    'HIGH',
    'seed_mqa_004_student_refund',
    JSON_OBJECT('legacyCompat', TRUE, 'eventType', 'CONSULT_REFUNDED', 'sourceType', 'CONSULT_ORDER', 'sourceId', 'CONS-MQA-20260401-004'),
    0,
    '2026-04-04 05:48:23',
    '2026-04-04 05:48:23'
  ),
  (
    118,
    'CONSULT_PAYMENT_EXCEPTION',
    'CONSULT',
    '支付异常订单已处理',
    '订单 CONS-MQA-20260401-004 因超时未答已自动退款，请确认排期释放。',
    'CONSULT_ORDER',
    'CONS-MQA-20260401-004',
    'VIEW_CONSULT_ORDER',
    'HIGH',
    'seed_mqa_004_mentor_refund',
    JSON_OBJECT('legacyCompat', TRUE, 'eventType', 'CONSULT_PAYMENT_EXCEPTION', 'sourceType', 'CONSULT_ORDER', 'sourceId', 'CONS-MQA-20260401-004'),
    0,
    '2026-04-04 05:48:23',
    '2026-04-04 05:48:23'
  ),
  (
    101,
    'CONSULT_REFUNDED',
    'CONSULT',
    '咨询订单已退款',
    '订单 CONS-MQA-20260401-006 已完成退款，后续仍可重新预约。',
    'CONSULT_ORDER',
    'CONS-MQA-20260401-006',
    'VIEW_CONSULT_ORDER',
    'HIGH',
    'seed_mqa_006_student_refund',
    JSON_OBJECT('legacyCompat', TRUE, 'eventType', 'CONSULT_REFUNDED', 'sourceType', 'CONSULT_ORDER', 'sourceId', 'CONS-MQA-20260401-006'),
    0,
    '2026-03-29 10:38:17',
    '2026-03-29 10:38:17'
  ),
  (
    121,
    'CONSULT_PAYMENT_EXCEPTION',
    'CONSULT',
    '支付异常订单已处理',
    '订单 CONS-MQA-20260401-006 已按学生申请完成退款，请确认排期释放。',
    'CONSULT_ORDER',
    'CONS-MQA-20260401-006',
    'VIEW_CONSULT_ORDER',
    'HIGH',
    'seed_mqa_006_mentor_refund',
    JSON_OBJECT('legacyCompat', TRUE, 'eventType', 'CONSULT_PAYMENT_EXCEPTION', 'sourceType', 'CONSULT_ORDER', 'sourceId', 'CONS-MQA-20260401-006'),
    0,
    '2026-03-29 10:38:17',
    '2026-03-29 10:38:17'
  ),
  (
    101,
    'CONSULT_REFUNDED',
    'CONSULT',
    '咨询订单已退款',
    '导师超时未答，系统已自动发起售后并完成退款。',
    'CONSULT_ORDER',
    'CONS-MQA-20260401-011',
    'VIEW_CONSULT_ORDER',
    'HIGH',
    'seed_mqa_011_student_refund',
    JSON_OBJECT('legacyCompat', TRUE, 'eventType', 'CONSULT_REFUNDED', 'sourceType', 'CONSULT_ORDER', 'sourceId', 'CONS-MQA-20260401-011'),
    0,
    '2026-04-03 10:38:45',
    '2026-04-03 10:38:45'
  ),
  (
    118,
    'CONSULT_PAYMENT_EXCEPTION',
    'CONSULT',
    '支付异常订单已处理',
    '订单 CONS-MQA-20260401-011 因超时未答已自动退款，请确认排期释放。',
    'CONSULT_ORDER',
    'CONS-MQA-20260401-011',
    'VIEW_CONSULT_ORDER',
    'HIGH',
    'seed_mqa_011_mentor_refund',
    JSON_OBJECT('legacyCompat', TRUE, 'eventType', 'CONSULT_PAYMENT_EXCEPTION', 'sourceType', 'CONSULT_ORDER', 'sourceId', 'CONS-MQA-20260401-011'),
    0,
    '2026-04-03 10:38:45',
    '2026-04-03 10:38:45'
  );

COMMIT;

SELECT id, email, role, display_name, real_name, status
FROM users
WHERE id IN (104, 105, 108, 109, 112, 113)
ORDER BY id;

SELECT user_id, school_name, major, grade, target_position, skill_tags
FROM student_profiles
WHERE user_id IN (104, 105, 112)
ORDER BY user_id;

SELECT user_id, company_name, job_title, approval_status, total_orders, avg_rating
FROM mentor_profiles
WHERE user_id IN (108, 109, 113)
ORDER BY user_id;

SELECT order_no, status, paid_at, closed_at
FROM consult_orders
WHERE order_no IN (
  'CONS-MQA-20260401-004',
  'CONS-MQA-20260401-006',
  'CONS-MQA-20260401-007',
  'CONS-MQA-20260401-011',
  'CONS20260304003',
  'CONS20260305004',
  'CONS20260307005',
  'CONS20260308006'
)
ORDER BY order_no;

SELECT order_no, COUNT(*) AS payment_count
FROM payment_records
WHERE order_no LIKE 'CONS-MQA-20260401-%'
GROUP BY order_no
ORDER BY order_no;

SELECT order_no, COUNT(*) AS after_sale_count
FROM consult_after_sales_requests
WHERE order_no IN (
  'CONS-MQA-20260401-004',
  'CONS-MQA-20260401-006',
  'CONS-MQA-20260401-011'
)
GROUP BY order_no
ORDER BY order_no;
