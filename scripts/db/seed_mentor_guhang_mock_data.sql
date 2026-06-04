SET NAMES utf8mb4;

START TRANSACTION;

SET @mentor_user_id = (
  SELECT id
  FROM users
  WHERE email = 'mentor.guhang@bishe.local'
  LIMIT 1
);
SET @admin_user_id = (
  SELECT id
  FROM users
  WHERE role = 'ADMIN'
  ORDER BY id
  LIMIT 1
);
SET @student_101 = 101;
SET @student_102 = 102;
SET @student_103 = 103;
SET @student_104 = 104;
SET @student_105 = 105;
SET @student_106 = 106;

SET @order_001 = 'CONS-GUHANG-20260404-001';
SET @order_002 = 'CONS-GUHANG-20260404-002';
SET @order_003 = 'CONS-GUHANG-20260404-003';
SET @order_004 = 'CONS-GUHANG-20260404-004';
SET @order_005 = 'CONS-GUHANG-20260404-005';

SET @seed_post_title = '导师答疑：后端项目复盘时，先把哪三层讲清楚';

DELETE FROM notifications
WHERE event_id LIKE 'SEED-GUHANG-%'
   OR ref_id IN (
    SELECT order_no
    FROM consult_orders
    WHERE mentor_user_id = @mentor_user_id
  );

DELETE FROM mentor_withdrawal_requests
WHERE mentor_user_id = @mentor_user_id;

DELETE FROM payment_records
WHERE order_no IN (
  SELECT order_no
  FROM consult_orders
  WHERE mentor_user_id = @mentor_user_id
);

DELETE FROM consult_after_sales_requests
WHERE order_no IN (
  SELECT order_no
  FROM consult_orders
  WHERE mentor_user_id = @mentor_user_id
);

DELETE FROM consult_order_attachments
WHERE order_no IN (
  SELECT order_no
  FROM consult_orders
  WHERE mentor_user_id = @mentor_user_id
);

DELETE FROM consult_reviews
WHERE order_no IN (
  SELECT order_no
  FROM consult_orders
  WHERE mentor_user_id = @mentor_user_id
);

DELETE FROM consult_messages
WHERE order_no IN (
  SELECT order_no
  FROM consult_orders
  WHERE mentor_user_id = @mentor_user_id
);

DELETE FROM mentor_schedule_slots
WHERE mentor_user_id = @mentor_user_id;

DELETE FROM consult_orders
WHERE mentor_user_id = @mentor_user_id;

SET @existing_seed_post_id = (
  SELECT id
  FROM posts
  WHERE user_id = @mentor_user_id
    AND title = @seed_post_title
  ORDER BY id DESC
  LIMIT 1
);

DELETE FROM comments
WHERE post_id = @existing_seed_post_id;

DELETE FROM posts
WHERE id = @existing_seed_post_id;

UPDATE mentor_profiles
SET expertise_tags = '职业规划,简历诊断,后端面试,项目复盘,校招策略',
    service_scenes = '简历诊断,项目表达,模拟面试复盘,校招投递策略,Offer 对比与决策',
    bio = '曾负责互联网平台校招与新人成长营，近几年持续做毕业生项目复盘与求职辅导，擅长把零散经历整理成“业务背景-关键动作-结果指标-复盘反思”的完整表达，也会重点帮学生补齐系统设计与追问应对。',
    suitable_for = '适合已经有 1-2 段项目或实习经历、希望把后端项目讲得更像真实业务，并同步梳理投递节奏和面试答题结构的同学。',
    not_suitable_for = '不适合完全没有可讨论材料、只想临时要一份模板答案，或暂时不愿意补充项目细节与量化结果的同学。',
    prep_materials = '建议提前准备最新简历、目标岗位 JD、项目结构图或接口说明、最想重点复盘的 1-2 段经历；如果有最近面试记录也一并带上。',
    reply_rhythm = '工作日晚间集中处理，通常 12 小时内给到首轮结构化反馈；如果涉及系统设计或 Offer 决策，会在 24 小时内补第二轮建议。',
    price_fen = 12900,
    is_available = 1,
    company_name = '云汐平台',
    job_title = '资深后端工程师 / 校招导师',
    avatar_url = 'https://api.dicebear.com/7.x/notionists/svg?seed=GuHang',
    avatar_updated_at = '2026-04-04 20:45:00',
    approval_status = 'APPROVED',
    total_orders = 0,
    avg_rating = 0.00
WHERE user_id = @mentor_user_id;

UPDATE users
SET real_name = '顾航',
    updated_at = '2026-04-04 20:45:00'
WHERE id = @mentor_user_id;

SET @current_cert_submission_id = (
  SELECT id
  FROM certification_submissions
  WHERE user_id = @mentor_user_id
    AND is_current = 1
  ORDER BY submitted_at DESC, id DESC
  LIMIT 1
);

UPDATE certification_submissions
SET user_role = 'MENTOR',
    real_name = '顾航',
    company_name = '云汐平台',
    job_title = '资深后端工程师 / 校招导师',
    status = 'APPROVED',
    review_note = '资料完整，导师侧演示账号已通过认证审核，可正常展示资料与服务能力。',
    reviewed_by = @admin_user_id,
    reviewed_at = '2026-03-18 18:20:00',
    previous_submission_id = NULL,
    is_current = 1,
    submitted_at = '2026-03-17 21:15:00',
    created_at = '2026-03-17 21:15:00',
    updated_at = '2026-04-04 20:45:00'
WHERE id = @current_cert_submission_id;

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
  @mentor_user_id,
  'MENTOR',
  '顾航',
  '云汐平台',
  '资深后端工程师 / 校招导师',
  'APPROVED',
  '资料完整，导师侧演示账号已通过认证审核，可正常展示资料与服务能力。',
  @admin_user_id,
  '2026-03-18 18:20:00',
  NULL,
  1,
  '2026-03-17 21:15:00',
  '2026-03-17 21:15:00',
  '2026-04-04 20:45:00'
WHERE @mentor_user_id IS NOT NULL
  AND @current_cert_submission_id IS NULL;

INSERT INTO consult_orders (
  order_no,
  student_user_id,
  mentor_user_id,
  amount_fen,
  status,
  question_text,
  paid_at,
  closed_at,
  created_at,
  updated_at,
  appointment_start_at,
  appointment_end_at,
  scene_code,
  source_page,
  question_payload_json,
  problem_summary,
  core_questions_json,
  expected_outcomes_json,
  selected_material_types,
  prep_sheet_snapshot_json
)
VALUES
  (
    @order_001,
    @student_103,
    @mentor_user_id,
    12900,
    'PAID',
    '我想把后端项目里的库存预占、消息队列和重试机制讲得更像真实业务，而不是功能堆砌。',
    '2026-04-04 09:18:00',
    NULL,
    '2026-04-04 09:15:00',
    '2026-04-04 09:18:00',
    '2026-04-06 20:00:00',
    '2026-04-06 20:45:00',
    'PROJECT_EXPRESSION',
    'MENTOR_MARKETPLACE_RECOMMENDATION',
    JSON_OBJECT(
      'background', '最近准备后端面试，项目里做过库存预占、MQ 异步削峰和失败重试，但目前表达比较散。',
      'expectedHelp', '希望先梳理项目主线，再补充系统设计追问准备方向。',
      'primaryConcern', '想知道怎么讲业务约束、技术方案和兜底策略。',
      'additionalNotes', '希望老师能顺手指出哪些细节最容易被追问。',
      'attemptedActions', '已经自己整理过一版项目描述，但仍然像功能罗列。'
    ),
    '重点是把库存预占、异步链路和失败重试讲出业务场景、设计取舍和稳定性收益。',
    JSON_ARRAY('库存预占应该先讲什么约束', '消息队列削峰与重试怎么串成一条主线', '系统设计追问如何拆层回答'),
    JSON_ARRAY('形成一版更完整的项目表达', '明确后续系统设计准备重点'),
    'RESUME,PROJECT_MATERIAL,JOB_DESCRIPTION',
    JSON_OBJECT(
      'scene', '项目表达',
      'summaryDraft', '希望把库存预占、MQ 异步和失败重试讲成完整业务案例。',
      'coreQuestions', JSON_ARRAY('如何先讲约束再讲方案', '如何组织系统设计追问'),
      'suggestedMaterials', JSON_ARRAY('简历', '项目结构图', '目标 JD'),
      'expectedOutcomes', JSON_ARRAY('拿到一版完整表达结构', '明确下一步补充材料')
    )
  ),
  (
    @order_002,
    @student_104,
    @mentor_user_id,
    15900,
    'ANSWERED',
    '我在简历里写了一个推荐系统课程项目，但面试时总被问为什么方案成立、指标为什么可信。',
    '2026-04-02 10:30:00',
    NULL,
    '2026-04-02 10:25:00',
    '2026-04-03 21:10:00',
    '2026-04-03 20:00:00',
    '2026-04-03 20:45:00',
    'INTERVIEW_REVIEW',
    'MENTOR_MARKETPLACE',
    JSON_OBJECT(
      'background', '课程项目做了召回、排序和简单 A/B 验证，但当前说法缺少业务假设和指标解释。',
      'expectedHelp', '希望做一次模拟面试式复盘，重点改“为什么这样设计”。',
      'primaryConcern', '经常被问指标可信度和实验边界。',
      'additionalNotes', '如果能顺带指出简历里需要补的内容更好。',
      'attemptedActions', '已经准备了项目 PPT 和一版问答清单。'
    ),
    '需要围绕推荐系统项目补齐业务目标、实验边界和指标解释逻辑。',
    JSON_ARRAY('推荐系统项目先讲目标还是架构', '指标可信度怎么解释', '课程项目如何避免讲成作业感'),
    JSON_ARRAY('形成一版更稳定的答题结构', '同步补一轮简历项目表达'),
    'RESUME,PROJECT_MATERIAL,SUPPLEMENTARY',
    JSON_OBJECT(
      'scene', '模拟面试复盘',
      'summaryDraft', '希望围绕推荐系统课程项目做一次结构化复盘。',
      'coreQuestions', JSON_ARRAY('先讲目标还是先讲技术方案', '如何解释指标可信度'),
      'suggestedMaterials', JSON_ARRAY('简历', '项目 PPT', '实验结果截图'),
      'expectedOutcomes', JSON_ARRAY('拿到一版答题框架', '知道简历还要补哪些信息')
    )
  ),
  (
    @order_003,
    @student_105,
    @mentor_user_id,
    16900,
    'CLOSED',
    '我做过一个校园二手交易平台，项目里有搜索、推荐和风控，但面试里总是越讲越散。',
    '2026-03-29 13:20:00',
    '2026-03-30 21:05:00',
    '2026-03-29 13:10:00',
    '2026-03-30 21:05:00',
    '2026-03-30 20:00:00',
    '2026-03-30 20:45:00',
    'PROJECT_EXPRESSION',
    'MENTOR_MARKETPLACE_FAVORITES',
    JSON_OBJECT(
      'background', '项目包含搜索、推荐和违规交易识别，但目前没有主次，回答容易跑偏。',
      'expectedHelp', '希望老师帮我拆出一条最适合校招面试的主线。',
      'primaryConcern', '担心自己讲太多功能，反而没有亮点。',
      'additionalNotes', '如果能把简历项目描述一起顺一遍就更好了。',
      'attemptedActions', '已经把项目模块写成清单，但还不会取舍。'
    ),
    '核心是重构项目主线，明确业务价值、技术亮点和面试追问优先级。',
    JSON_ARRAY('搜索推荐风控应该怎么取主线', '项目亮点如何取舍', '面试追问先准备哪几类'),
    JSON_ARRAY('形成一版更聚焦的项目讲法', '知道该删掉哪些冗余细节'),
    'RESUME,PROJECT_MATERIAL',
    JSON_OBJECT(
      'scene', '项目表达',
      'summaryDraft', '希望把校园二手交易平台项目讲得更聚焦。',
      'coreQuestions', JSON_ARRAY('如何聚焦主线', '哪些亮点适合保留'),
      'suggestedMaterials', JSON_ARRAY('简历', '项目文档', '演示截图'),
      'expectedOutcomes', JSON_ARRAY('拿到一版主线表达', '明确追问准备清单')
    )
  ),
  (
    @order_004,
    @student_106,
    @mentor_user_id,
    13900,
    'CLOSED',
    '我想从数据分析转后端开发，简历里只有一个数据平台项目，不知道怎么补工程化和服务端视角。',
    '2026-03-24 16:12:00',
    '2026-03-25 20:20:00',
    '2026-03-24 16:05:00',
    '2026-03-25 20:20:00',
    '2026-03-25 19:30:00',
    '2026-03-25 20:15:00',
    'DELIVERY_STRATEGY',
    'MENTOR_MARKETPLACE_RECOMMENDATION',
    JSON_OBJECT(
      'background', '当前经历更偏数据分析，希望把数据平台项目重写得更贴近后端岗位要求。',
      'expectedHelp', '希望老师帮我判断哪些工程化内容值得补，哪些方向更适合当前阶段。',
      'primaryConcern', '担心项目表达和目标岗位不匹配。',
      'additionalNotes', '如果需要我愿意补充项目里的接口、任务调度和数据链路细节。',
      'attemptedActions', '已经尝试把项目改写成服务端项目，但还不够自然。'
    ),
    '重点是帮助转后端方向的学生把数据平台项目重写成更像服务端工程项目的表达。',
    JSON_ARRAY('转后端时项目经历怎么选', '数据平台项目怎么补工程化视角', '投递节奏应该如何安排'),
    JSON_ARRAY('明确转后端的表达策略', '形成一版更匹配岗位的项目描述'),
    'RESUME,SUPPLEMENTARY',
    JSON_OBJECT(
      'scene', '校招投递策略',
      'summaryDraft', '希望从数据分析转后端时重写项目并梳理投递方向。',
      'coreQuestions', JSON_ARRAY('项目应该怎么重写', '哪些岗位更值得先投'),
      'suggestedMaterials', JSON_ARRAY('简历', '岗位 JD', '项目流程图'),
      'expectedOutcomes', JSON_ARRAY('明确表达策略', '知道下一步该补什么材料')
    )
  ),
  (
    @order_005,
    @student_102,
    @mentor_user_id,
    10900,
    'REFUNDED',
    '原本想约一次简历问诊，但我后来发现目标岗位方向变动比较大，担心当前问题描述不够准确。',
    '2026-03-27 12:10:00',
    '2026-03-30 11:00:00',
    '2026-03-27 12:00:00',
    '2026-03-30 11:00:00',
    '2026-03-29 20:00:00',
    '2026-03-29 20:45:00',
    'RESUME_DIAGNOSIS',
    'MENTOR_MARKETPLACE',
    JSON_OBJECT(
      'background', '原计划做简历问诊，但在下单后目标岗位从产品转向数据产品，问题边界变化较大。',
      'expectedHelp', '希望先确认方向是否需要重新梳理，再决定是否继续当前订单。',
      'primaryConcern', '担心当前订单内容与最新目标不匹配。',
      'additionalNotes', '如果不合适，希望直接走退款并重新准备材料。',
      'attemptedActions', '已自己重新整理了一版方向说明。'
    ),
    '学生在支付后调整目标岗位，希望中止当前问诊并重新准备材料。',
    JSON_ARRAY('当前问题是否还适合继续', '如果重做资料需要先补哪些内容'),
    JSON_ARRAY('确认是否应退款重开', '明确重整资料的优先级'),
    'RESUME,JOB_DESCRIPTION',
    JSON_OBJECT(
      'scene', '简历诊断',
      'summaryDraft', '学生希望先判断是否应退款后重开。',
      'coreQuestions', JSON_ARRAY('是否适合继续当前订单', '若重开资料应先补哪些信息'),
      'suggestedMaterials', JSON_ARRAY('简历', '目标岗位清单'),
      'expectedOutcomes', JSON_ARRAY('明确当前订单处理方式', '确定后续准备方向')
    )
  )
ON DUPLICATE KEY UPDATE
  student_user_id = VALUES(student_user_id),
  mentor_user_id = VALUES(mentor_user_id),
  amount_fen = VALUES(amount_fen),
  status = VALUES(status),
  question_text = VALUES(question_text),
  paid_at = VALUES(paid_at),
  closed_at = VALUES(closed_at),
  created_at = VALUES(created_at),
  updated_at = VALUES(updated_at),
  appointment_start_at = VALUES(appointment_start_at),
  appointment_end_at = VALUES(appointment_end_at),
  scene_code = VALUES(scene_code),
  source_page = VALUES(source_page),
  question_payload_json = VALUES(question_payload_json),
  problem_summary = VALUES(problem_summary),
  core_questions_json = VALUES(core_questions_json),
  expected_outcomes_json = VALUES(expected_outcomes_json),
  selected_material_types = VALUES(selected_material_types),
  prep_sheet_snapshot_json = VALUES(prep_sheet_snapshot_json);

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
VALUES
  (@order_001, 'ALIPAY', 'SANDBOX', 'TRADE-CONS-GUHANG-20260404-001-INIT', 12900, 'INIT', 'seed-guhang-payment-001-init', NULL, '2026-04-04 09:15:20', '2026-04-04 09:15:20'),
  (@order_001, 'ALIPAY', 'SANDBOX', 'TRADE-CONS-GUHANG-20260404-001-SUCCESS', 12900, 'SUCCESS', 'seed-guhang-payment-001-success', NULL, '2026-04-04 09:18:00', '2026-04-04 09:18:00'),
  (@order_002, 'ALIPAY', 'SANDBOX', 'TRADE-CONS-GUHANG-20260404-002-INIT', 15900, 'INIT', 'seed-guhang-payment-002-init', NULL, '2026-04-02 10:25:20', '2026-04-02 10:25:20'),
  (@order_002, 'ALIPAY', 'SANDBOX', 'TRADE-CONS-GUHANG-20260404-002-SUCCESS', 15900, 'SUCCESS', 'seed-guhang-payment-002-success', NULL, '2026-04-02 10:30:00', '2026-04-02 10:30:00'),
  (@order_003, 'ALIPAY', 'SANDBOX', 'TRADE-CONS-GUHANG-20260404-003-INIT', 16900, 'INIT', 'seed-guhang-payment-003-init', NULL, '2026-03-29 13:10:30', '2026-03-29 13:10:30'),
  (@order_003, 'ALIPAY', 'SANDBOX', 'TRADE-CONS-GUHANG-20260404-003-SUCCESS', 16900, 'SUCCESS', 'seed-guhang-payment-003-success', NULL, '2026-03-29 13:20:00', '2026-03-29 13:20:00'),
  (@order_004, 'ALIPAY', 'SANDBOX', 'TRADE-CONS-GUHANG-20260404-004-INIT', 13900, 'INIT', 'seed-guhang-payment-004-init', NULL, '2026-03-24 16:05:20', '2026-03-24 16:05:20'),
  (@order_004, 'ALIPAY', 'SANDBOX', 'TRADE-CONS-GUHANG-20260404-004-SUCCESS', 13900, 'SUCCESS', 'seed-guhang-payment-004-success', NULL, '2026-03-24 16:12:00', '2026-03-24 16:12:00'),
  (@order_005, 'ALIPAY', 'SANDBOX', 'TRADE-CONS-GUHANG-20260404-005-INIT', 10900, 'INIT', 'seed-guhang-payment-005-init', NULL, '2026-03-27 12:00:20', '2026-03-27 12:00:20'),
  (@order_005, 'ALIPAY', 'SANDBOX', 'TRADE-CONS-GUHANG-20260404-005-SUCCESS', 10900, 'SUCCESS', 'seed-guhang-payment-005-success', NULL, '2026-03-27 12:10:00', '2026-03-27 12:10:00');

INSERT INTO consult_messages (
  order_no,
  sender_user_id,
  sender_role,
  message_text,
  created_at
)
VALUES
  (@order_001, @student_103, 'STUDENT', '老师您好，我最怕被追问为什么要做库存预占，感觉自己讲不清业务约束。', '2026-04-04 09:22:00'),
  (@order_001, @student_103, 'STUDENT', '我把项目结构图和一版简历都整理好了，想先听您建议从哪一层开讲。', '2026-04-04 09:25:00'),

  (@order_002, @student_104, 'STUDENT', '老师，我的推荐系统项目总被问“为什么指标可信”，这部分我回答得很虚。', '2026-04-03 20:02:00'),
  (@order_002, @mentor_user_id, 'MENTOR', '先别急着补模型细节，第一步先把业务目标、样本边界和评价指标解释顺。', '2026-04-03 20:12:00'),
  (@order_002, @mentor_user_id, 'MENTOR', '我建议你把项目拆成“目标-数据-方案-验证-边界”五段，简历里也同步补一句指标来源。', '2026-04-03 20:18:00'),

  (@order_003, @student_105, 'STUDENT', '这个校园二手交易平台我什么都想讲，结果每次都像在报菜单。', '2026-03-30 20:01:00'),
  (@order_003, @mentor_user_id, 'MENTOR', '先只保留一条主线，比如“如何提升交易撮合效率并控制风险”，其他模块都围绕它服务。', '2026-03-30 20:10:00'),
  (@order_003, @student_105, 'STUDENT', '明白了，我回去把搜索、推荐、风控重新归类到这条主线里。', '2026-03-30 20:36:00'),
  (@order_003, @mentor_user_id, 'MENTOR', '对，简历里也只保留最能体现你判断和取舍的两三个动作，不要把功能平铺。', '2026-03-30 20:43:00'),

  (@order_004, @student_106, 'STUDENT', '我现在最纠结的是数据平台项目如何改得更像后端，而不是分析工具。', '2026-03-25 19:32:00'),
  (@order_004, @mentor_user_id, 'MENTOR', '你可以把重点放在接口、任务编排、异常兜底和数据链路治理，而不是图表结果本身。', '2026-03-25 19:45:00'),
  (@order_004, @student_106, 'STUDENT', '这样的话我应该把“调度失败重跑”和“数据校验”写成工程保障，对吗？', '2026-03-25 20:05:00'),
  (@order_004, @mentor_user_id, 'MENTOR', '对，而且要补一句这些动作解决了什么稳定性问题，这样岗位匹配感会强很多。', '2026-03-25 20:11:00'),

  (@order_005, @student_102, 'STUDENT', '老师，我下单后发现目标岗位从产品转成数据产品了，担心当前问诊方向不准。', '2026-03-29 19:40:00'),
  (@order_005, @mentor_user_id, 'MENTOR', '如果方向变化较大，建议你先别勉强继续当前问题，我们可以按新方向重新准备材料。', '2026-03-29 19:48:00'),
  (@order_005, @student_102, 'STUDENT', '那我先申请退款重整理资料，之后再重新下单。', '2026-03-29 19:55:00');

INSERT INTO consult_reviews (
  order_no,
  student_user_id,
  mentor_user_id,
  rating,
  comment,
  created_at
)
VALUES
  (@order_003, @student_105, @mentor_user_id, 5, '老师特别会帮我抓主线，原本一堆功能点被整理成一条很清晰的项目故事线。', '2026-03-30 21:08:00'),
  (@order_004, @student_106, @mentor_user_id, 5, '对转后端这件事给了很务实的建议，还明确指出哪些工程化细节最值得补。', '2026-03-25 20:24:00')
ON DUPLICATE KEY UPDATE
  student_user_id = VALUES(student_user_id),
  mentor_user_id = VALUES(mentor_user_id),
  rating = VALUES(rating),
  comment = VALUES(comment),
  created_at = VALUES(created_at);

INSERT INTO consult_order_attachments (
  order_no,
  uploaded_by_user_id,
  attachment_type,
  slot_code,
  source_stage,
  original_filename,
  content_type,
  size_bytes,
  storage_bucket,
  object_key,
  description,
  lifecycle_status,
  replaced_attachment_id,
  created_at,
  updated_at
)
VALUES
  (@order_001, @student_103, 'RESUME', 'RESUME', 'ORDER_CREATE', 'zhoumuyang-backend-resume-v3.pdf', 'application/pdf', 524288, 'bishe-consult-materials', 'consult-orders/CONS-GUHANG-20260404-001/RESUME/zhoumuyang-backend-resume-v3.pdf', '最新后端简历，重点标出库存预占与异步链路项目。', 'CURRENT', NULL, '2026-04-04 09:16:00', '2026-04-04 09:16:00'),
  (@order_001, @student_103, 'PROJECT_MATERIAL', 'PROJECT_MATERIAL', 'ORDER_CREATE', 'inventory-project-architecture.pdf', 'application/pdf', 782336, 'bishe-consult-materials', 'consult-orders/CONS-GUHANG-20260404-001/PROJECT_MATERIAL/inventory-project-architecture.pdf', '库存预占与消息队列链路架构图。', 'CURRENT', NULL, '2026-04-04 09:17:00', '2026-04-04 09:17:00'),
  (@order_001, @student_103, 'JOB_DESCRIPTION', 'JOB_DESCRIPTION', 'ORDER_CREATE', 'backend-intern-jd.pdf', 'application/pdf', 193536, 'bishe-consult-materials', 'consult-orders/CONS-GUHANG-20260404-001/JOB_DESCRIPTION/backend-intern-jd.pdf', '目标后端实习岗位 JD，已标记最关心的项目与稳定性要求。', 'CURRENT', NULL, '2026-04-04 09:17:20', '2026-04-04 09:17:20'),
  (@order_002, @student_104, 'RESUME', 'RESUME', 'ORDER_CREATE', 'chensiyu-recommendation-project-resume.pdf', 'application/pdf', 471040, 'bishe-consult-materials', 'consult-orders/CONS-GUHANG-20260404-002/RESUME/chensiyu-recommendation-project-resume.pdf', '含推荐系统课程项目的一版简历。', 'CURRENT', NULL, '2026-04-02 10:26:00', '2026-04-02 10:26:00'),
  (@order_002, @student_104, 'PROJECT_MATERIAL', 'PROJECT_MATERIAL', 'ORDER_CREATE', 'recommendation-project-slides.pdf', 'application/pdf', 618496, 'bishe-consult-materials', 'consult-orders/CONS-GUHANG-20260404-002/PROJECT_MATERIAL/recommendation-project-slides.pdf', '推荐系统课程项目 PPT，含召回、排序与实验结果页。', 'CURRENT', NULL, '2026-04-02 10:27:00', '2026-04-02 10:27:00'),
  (@order_002, @student_104, 'SUPPLEMENTARY', 'SUPPLEMENTARY', 'CHAT_APPEND', 'reco-metrics-notes.docx', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 356352, 'bishe-consult-materials', 'consult-orders/CONS-GUHANG-20260404-002/SUPPLEMENTARY/reco-metrics-notes.docx', '项目实验指标说明和问答草稿。', 'CURRENT', NULL, '2026-04-03 20:05:00', '2026-04-03 20:05:00'),
  (@order_003, @student_105, 'RESUME', 'RESUME', 'ORDER_CREATE', 'sunzeyu-marketplace-resume.pdf', 'application/pdf', 428032, 'bishe-consult-materials', 'consult-orders/CONS-GUHANG-20260404-003/RESUME/sunzeyu-marketplace-resume.pdf', '包含校园二手交易平台项目的最新版简历。', 'CURRENT', NULL, '2026-03-29 13:14:00', '2026-03-29 13:14:00'),
  (@order_003, @student_105, 'PROJECT_MATERIAL', 'PROJECT_MATERIAL', 'ORDER_CREATE', 'campus-market-project-summary.pdf', 'application/pdf', 643072, 'bishe-consult-materials', 'consult-orders/CONS-GUHANG-20260404-003/PROJECT_MATERIAL/campus-market-project-summary.pdf', '校园二手交易平台项目总结。', 'CURRENT', NULL, '2026-03-29 13:15:00', '2026-03-29 13:15:00'),
  (@order_004, @student_106, 'RESUME', 'RESUME', 'ORDER_CREATE', 'heqingyan-backend-transition-resume.pdf', 'application/pdf', 452608, 'bishe-consult-materials', 'consult-orders/CONS-GUHANG-20260404-004/RESUME/heqingyan-backend-transition-resume.pdf', '转后端方向的最新简历版本，重点补了接口与任务编排经历。', 'CURRENT', NULL, '2026-03-24 16:06:00', '2026-03-24 16:06:00'),
  (@order_004, @student_106, 'SUPPLEMENTARY', 'SUPPLEMENTARY', 'ORDER_CREATE', 'data-platform-flowchart.png', 'image/png', 284672, 'bishe-consult-materials', 'consult-orders/CONS-GUHANG-20260404-004/SUPPLEMENTARY/data-platform-flowchart.png', '数据平台任务链路和失败重跑流程图。', 'CURRENT', NULL, '2026-03-24 16:07:00', '2026-03-24 16:07:00'),
  (@order_005, @student_102, 'RESUME', 'RESUME', 'ORDER_CREATE', 'anran-data-product-resume.pdf', 'application/pdf', 401408, 'bishe-consult-materials', 'consult-orders/CONS-GUHANG-20260404-005/RESUME/anran-data-product-resume.pdf', '原咨询订单对应的一版简历，当前方向已准备重整。', 'CURRENT', NULL, '2026-03-27 12:01:00', '2026-03-27 12:01:00'),
  (@order_005, @student_102, 'JOB_DESCRIPTION', 'JOB_DESCRIPTION', 'ORDER_CREATE', 'data-product-target-jd.pdf', 'application/pdf', 188416, 'bishe-consult-materials', 'consult-orders/CONS-GUHANG-20260404-005/JOB_DESCRIPTION/data-product-target-jd.pdf', '学生后续转向数据产品方向时重新整理的目标 JD。', 'CURRENT', NULL, '2026-03-27 12:02:00', '2026-03-27 12:02:00');

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
    @order_005,
    @student_102,
    'REFUND',
    'APPROVED',
    '学生在支付后调整目标岗位方向，当前订单问题边界已明显变化，希望退款后重新准备材料再下单。',
    '已同意退款，建议学生按数据产品新方向重整简历和问题清单后再重新预约。',
    @admin_user_id,
    0,
    '2026-03-30 11:00:00',
    '2026-03-30 09:30:00',
    '2026-03-30 11:00:00'
  );

INSERT INTO mentor_schedule_slots (
  mentor_user_id,
  start_at,
  end_at,
  status,
  booked_order_no,
  created_at,
  updated_at
)
VALUES
  (@mentor_user_id, '2026-04-06 20:00:00', '2026-04-06 20:45:00', 'BOOKED', @order_001, '2026-04-04 09:15:00', '2026-04-04 09:18:00'),
  (@mentor_user_id, '2026-04-03 20:00:00', '2026-04-03 20:45:00', 'BOOKED', @order_002, '2026-04-02 10:25:00', '2026-04-03 20:18:00'),
  (@mentor_user_id, '2026-03-30 20:00:00', '2026-03-30 20:45:00', 'BOOKED', @order_003, '2026-03-29 13:10:00', '2026-03-30 21:05:00'),
  (@mentor_user_id, '2026-03-25 19:30:00', '2026-03-25 20:15:00', 'BOOKED', @order_004, '2026-03-24 16:05:00', '2026-03-25 20:20:00'),
  (@mentor_user_id, '2026-03-29 20:00:00', '2026-03-29 20:45:00', 'BOOKED', @order_005, '2026-03-27 12:00:00', '2026-03-30 11:00:00'),
  (@mentor_user_id, '2026-04-08 20:00:00', '2026-04-08 20:45:00', 'AVAILABLE', NULL, '2026-04-04 18:10:00', '2026-04-04 18:10:00'),
  (@mentor_user_id, '2026-04-10 20:00:00', '2026-04-10 20:45:00', 'AVAILABLE', NULL, '2026-04-04 18:10:00', '2026-04-04 18:10:00')
ON DUPLICATE KEY UPDATE
  status = VALUES(status),
  booked_order_no = VALUES(booked_order_no),
  created_at = VALUES(created_at),
  updated_at = VALUES(updated_at);

INSERT INTO mentor_withdrawal_requests (
  mentor_user_id,
  amount_fen,
  status,
  note,
  created_at,
  updated_at
)
VALUES
  (@mentor_user_id, 8000, 'PENDING', '[SEED-GUHANG] 本周计划先提一笔待结算收入，作为财务中心演示数据。', '2026-04-02 09:40:00', '2026-04-02 09:40:00'),
  (@mentor_user_id, 7600, 'PROCESSING', '[SEED-GUHANG] 三月下旬已完成订单的一笔模拟提现，当前等待平台打款。', '2026-03-31 18:20:00', '2026-04-01 09:10:00'),
  (@mentor_user_id, 12000, 'COMPLETED', '[SEED-GUHANG] 作为导师端财务页演示的已完成提现记录。', '2026-03-26 11:15:00', '2026-03-27 14:05:00');

INSERT INTO notification_preferences (
  user_id,
  category,
  inbox_enabled,
  websocket_enabled,
  browser_popup_enabled,
  email_enabled,
  email_urgency_threshold,
  quiet_hours_json,
  updated_at
)
VALUES
  (@mentor_user_id, 'CONSULT', 1, 1, 1, 1, 'HIGH', NULL, '2026-04-04 20:46:00'),
  (@mentor_user_id, 'CERTIFICATION', 1, 1, 1, 1, 'NORMAL', NULL, '2026-04-04 20:46:00'),
  (@mentor_user_id, 'COMMUNITY', 1, 1, 0, 0, 'HIGH', NULL, '2026-04-04 20:46:00')
ON DUPLICATE KEY UPDATE
  inbox_enabled = VALUES(inbox_enabled),
  websocket_enabled = VALUES(websocket_enabled),
  browser_popup_enabled = VALUES(browser_popup_enabled),
  email_enabled = VALUES(email_enabled),
  email_urgency_threshold = VALUES(email_urgency_threshold),
  quiet_hours_json = VALUES(quiet_hours_json),
  updated_at = VALUES(updated_at);

INSERT INTO posts (
  user_id,
  title,
  content,
  tags,
  scenario_code,
  resolved_status,
  moderation_status,
  risk_level,
  is_deleted,
  created_at,
  updated_at
)
VALUES
  (
    @mentor_user_id,
    @seed_post_title,
    '很多同学复盘后端项目时，第一反应是把模块一股脑讲完，结果面试官听不到重点。我的建议是先固定三层：\n\n1. 这件事解决了什么业务问题。\n2. 方案为什么这样设计，核心约束是什么。\n3. 最后带来了什么结果，以及如果重做你会怎么优化。\n\n先把这三层讲顺，再补缓存、消息队列、重试、监控这些细节，面试会稳很多。',
    '后端项目,面试复盘,导师答疑',
    'GENERAL_HELP',
    'OPEN',
    'PASS',
    'LOW',
    0,
    '2026-04-04 18:30:00',
    '2026-04-04 18:30:00'
  );

SET @seed_post_id = (
  SELECT id
  FROM posts
  WHERE user_id = @mentor_user_id
    AND title = @seed_post_title
  ORDER BY id DESC
  LIMIT 1
);

INSERT INTO comments (
  post_id,
  user_id,
  content,
  is_ai,
  moderation_status,
  risk_level,
  is_deleted,
  created_at,
  updated_at
)
SELECT
  @seed_post_id,
  @student_103,
  '这个“三层讲法”很有用，我以前总是直接讲技术点，难怪面试官听着没有主线。',
  0,
  'PASS',
  'LOW',
  0,
  '2026-04-04 18:42:00',
  '2026-04-04 18:42:00'
WHERE @seed_post_id IS NOT NULL
UNION ALL
SELECT
  @seed_post_id,
  @student_104,
  '我发现如果先把业务问题说清楚，后面的指标和实验边界也更容易被理解，感谢老师总结。',
  0,
  'PASS',
  'LOW',
  0,
  '2026-04-04 18:50:00',
  '2026-04-04 18:50:00'
WHERE @seed_post_id IS NOT NULL;

SET @seed_comment_id = (
  SELECT id
  FROM comments
  WHERE post_id = @seed_post_id
  ORDER BY id DESC
  LIMIT 1
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
  read_at,
  archived_at,
  created_at,
  updated_at
)
VALUES
  (
    @mentor_user_id,
    'CONSULT_PAID',
    'CONSULT',
    '咨询订单已支付',
    '周沐阳的新订单已完成支付，预约时段为 4 月 6 日 20:00，可提前查看材料并准备回复。',
    'CONSULT_ORDER',
    @order_001,
    'VIEW_CONSULT_ORDER',
    'HIGH',
    'SEED-GUHANG-CONSULT-PAID-001',
    JSON_OBJECT('legacyCompat', TRUE, 'eventType', 'CONSULT_PAID', 'sourceType', 'CONSULT_ORDER', 'sourceId', @order_001, 'orderNo', @order_001),
    0,
    NULL,
    NULL,
    '2026-04-04 09:18:10',
    '2026-04-04 09:18:10'
  ),
  (
    @mentor_user_id,
    'CONSULT_REVIEWED',
    'CONSULT',
    '咨询订单收到新评价',
    '孙泽宇完成了本次项目表达复盘，并留下了新的 5 星评价。',
    'CONSULT_ORDER',
    @order_003,
    'VIEW_CONSULT_ORDER',
    'NORMAL',
    'SEED-GUHANG-CONSULT-REVIEWED-003',
    JSON_OBJECT('legacyCompat', TRUE, 'eventType', 'CONSULT_REVIEWED', 'sourceType', 'CONSULT_ORDER', 'sourceId', @order_003, 'orderNo', @order_003),
    1,
    '2026-03-30 21:30:00',
    NULL,
    '2026-03-30 21:08:30',
    '2026-03-30 21:30:00'
  ),
  (
    @mentor_user_id,
    'CONSULT_REFUNDED',
    'CONSULT',
    '咨询订单已退款',
    '安然的订单退款已处理完成，本单已进入退款收口，可查看历史沟通与原因记录。',
    'CONSULT_ORDER',
    @order_005,
    'VIEW_CONSULT_ORDER',
    'HIGH',
    'SEED-GUHANG-CONSULT-REFUNDED-005',
    JSON_OBJECT('legacyCompat', TRUE, 'eventType', 'CONSULT_REFUNDED', 'sourceType', 'CONSULT_ORDER', 'sourceId', @order_005, 'orderNo', @order_005),
    0,
    NULL,
    NULL,
    '2026-03-30 11:00:10',
    '2026-03-30 11:00:10'
  ),
  (
    @mentor_user_id,
    'CERTIFICATION_APPROVED',
    'CERTIFICATION',
    '认证审核已通过',
    '导师资料与认证信息已补齐，当前可正常公开展示服务卡片与工作台能力。',
    'CERTIFICATION',
    CAST(@mentor_user_id AS CHAR),
    'VIEW_CERTIFICATION_STATUS',
    'HIGH',
    'SEED-GUHANG-CERT-APPROVED-001',
    JSON_OBJECT('eventType', 'CERTIFICATION_APPROVED', 'sourceType', 'CERTIFICATION', 'sourceId', CAST(@mentor_user_id AS CHAR)),
    1,
    '2026-04-04 20:47:00',
    NULL,
    '2026-04-04 20:46:10',
    '2026-04-04 20:47:00'
  ),
  (
    @mentor_user_id,
    'SYSTEM_ANNOUNCEMENT',
    'SYSTEM',
    '平台公告',
    '导师工作台的订单、支付、履约、财务与资料页演示数据已同步刷新，可直接用于前端联调回归。',
    'NOTIFICATION_CENTER',
    'seed-mentor-guhang-20260404',
    'VIEW_NOTIFICATION_CENTER',
    'NORMAL',
    'SEED-GUHANG-SYSTEM-ANNOUNCEMENT-001',
    JSON_OBJECT('eventType', 'SYSTEM_ANNOUNCEMENT', 'sourceType', 'NOTIFICATION_CENTER', 'sourceId', 'seed-mentor-guhang-20260404'),
    0,
    NULL,
    NULL,
    '2026-04-04 20:48:00',
    '2026-04-04 20:48:00'
  ),
  (
    @mentor_user_id,
    'COMMUNITY_POST_REPLIED',
    'COMMUNITY',
    '你的帖子收到了新回复',
    '陈思语在你的帖子《导师答疑：后端项目复盘时，先把哪三层讲清楚》下补充了自己的理解。',
    'COMMUNITY_POST',
    CAST(@seed_post_id AS CHAR),
    'VIEW_COMMUNITY_POST',
    'NORMAL',
    'SEED-GUHANG-COMMUNITY-REPLIED-001',
    JSON_OBJECT(
      'postId', @seed_post_id,
      'commentId', @seed_comment_id,
      'postTitle', @seed_post_title,
      'scenarioCode', 'GENERAL_HELP',
      'actorDisplayName', '陈思语',
      'actorRole', 'STUDENT',
      'eventType', 'COMMUNITY_POST_REPLIED',
      'sourceType', 'COMMUNITY_POST',
      'sourceId', CAST(@seed_post_id AS CHAR)
    ),
    0,
    NULL,
    NULL,
    '2026-04-04 18:50:10',
    '2026-04-04 18:50:10'
  );

UPDATE mentor_profiles
SET total_orders = (
      SELECT COUNT(*)
      FROM consult_orders
      WHERE mentor_user_id = @mentor_user_id
        AND status = 'CLOSED'
    ),
    avg_rating = COALESCE((
      SELECT ROUND(AVG(rating), 2)
      FROM consult_reviews
      WHERE mentor_user_id = @mentor_user_id
    ), 0.00),
    updated_at = '2026-04-05 15:30:00'
WHERE user_id = @mentor_user_id;

COMMIT;
