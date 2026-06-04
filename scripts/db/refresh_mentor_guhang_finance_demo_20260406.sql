SET NAMES utf8mb4;

START TRANSACTION;

SET @mentor_user_id = (
  SELECT id
  FROM users
  WHERE email = 'mentor.guhang@bishe.local'
  LIMIT 1
);

SET @student_101 = 101;
SET @student_102 = 102;
SET @student_103 = 103;
SET @student_104 = 104;
SET @student_105 = 105;
SET @student_106 = 106;
SET @student_112 = 112;

SET @order_006 = 'CONS-GUHANG-20260405-006';
SET @order_007 = 'CONS-GUHANG-20260401-007';
SET @order_008 = 'CONS-GUHANG-20260406-008';
SET @order_009 = 'CONS-GUHANG-20260320-009';

DELETE FROM payment_records
WHERE order_no IN (@order_006, @order_007, @order_008, @order_009);

DELETE FROM consult_after_sales_requests
WHERE order_no IN (@order_006, @order_007, @order_008, @order_009);

DELETE FROM consult_order_attachments
WHERE order_no IN (@order_006, @order_007, @order_008, @order_009);

DELETE FROM consult_reviews
WHERE order_no IN (@order_006, @order_007, @order_008, @order_009);

DELETE FROM consult_messages
WHERE order_no IN (@order_006, @order_007, @order_008, @order_009);

DELETE FROM mentor_schedule_slots
WHERE booked_order_no IN (@order_006, @order_007, @order_008, @order_009)
   OR (
    mentor_user_id = @mentor_user_id
    AND start_at IN ('2026-04-07 19:30:00', '2026-04-02 20:00:00', '2026-03-21 20:30:00')
  );

DELETE FROM consult_orders
WHERE order_no IN (@order_006, @order_007, @order_008, @order_009);

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
    @order_006,
    @student_101,
    @mentor_user_id,
    17900,
    'ANSWERED',
    '我想把 Java 服务端项目里的缓存一致性、延迟队列和降级方案讲成一条业务主线，避免面试里被问散。',
    '2026-04-05 11:28:00',
    NULL,
    '2026-04-05 11:20:00',
    '2026-04-05 21:40:00',
    '2026-04-07 19:30:00',
    '2026-04-07 20:15:00',
    'PROJECT_EXPRESSION',
    'MENTOR_MARKETPLACE_RECOMMENDATION',
    JSON_OBJECT(
      'background', '最近在准备 Java 后端岗位，项目里涉及缓存一致性、异步通知和服务降级，但当前回答像是在堆概念。',
      'expectedHelp', '希望老师帮我先梳理一条业务主线，再指出哪些追问值得重点准备。',
      'primaryConcern', '如何把约束、方案和兜底措施讲成一条完整链路。',
      'additionalNotes', '如果能一起指出简历里哪些句子最容易被追问会更有帮助。',
      'attemptedActions', '已经整理了一版项目草稿，但还是经常被面试官打断。'
    ),
    '核心是把缓存一致性、延迟队列和降级策略放回到业务链路里讲清楚，而不是只罗列技术名词。',
    JSON_ARRAY('缓存一致性应该先讲业务约束还是方案', '延迟队列在这里承担什么角色', '降级方案怎么证明真的解决了风险'),
    JSON_ARRAY('形成一版更顺的项目表达', '明确系统设计追问的准备重点'),
    'RESUME,PROJECT_MATERIAL',
    JSON_OBJECT(
      'scene', '项目表达',
      'summaryDraft', '希望把缓存一致性、延迟队列和降级方案讲成一条清晰主线。',
      'coreQuestions', JSON_ARRAY('如何先讲约束再讲方案', '如何组织稳定性追问回答'),
      'suggestedMaterials', JSON_ARRAY('简历', '项目结构图'),
      'expectedOutcomes', JSON_ARRAY('形成一版主线表达', '知道追问该怎么准备')
    )
  ),
  (
    @order_007,
    @student_104,
    @mentor_user_id,
    14900,
    'CLOSED',
    '我手里有平台大厂边缘组和中厂核心岗两个 offer，想结合自己的项目经历和成长路径一起梳理怎么选。',
    '2026-04-01 14:18:00',
    '2026-04-02 21:36:00',
    '2026-04-01 14:10:00',
    '2026-04-02 21:36:00',
    '2026-04-02 20:00:00',
    '2026-04-02 20:45:00',
    'DELIVERY_STRATEGY',
    'MENTOR_MARKETPLACE',
    JSON_OBJECT(
      'background', '目前同时拿到两个方向差异较大的 offer，一个品牌更强，一个业务和产出空间更集中。',
      'expectedHelp', '希望老师结合我的项目基础和成长节奏，判断哪个路径更适合当前阶段。',
      'primaryConcern', '不知道应该优先看平台、团队、业务密度还是后续转岗空间。',
      'additionalNotes', '如果能顺手给我一个对外解释 offer 选择的说法也很好。',
      'attemptedActions', '已经自己列过优劣势清单，但还是很难真正权衡。'
    ),
    '核心是帮助学生把 offer 对比拆到团队带教、任务密度、核心链路参与度和未来一年成长节奏上。',
    JSON_ARRAY('品牌和成长速度应该怎么权衡', '中厂核心岗的风险和机会分别是什么', '如何向家里解释最终选择'),
    JSON_ARRAY('形成一版更稳的决策结论', '明确入职前还要补的准备动作'),
    'RESUME,JOB_DESCRIPTION',
    JSON_OBJECT(
      'scene', '校招投递策略',
      'summaryDraft', '希望把两个 offer 的利弊拆得更具体，并尽快形成决策。',
      'coreQuestions', JSON_ARRAY('怎么拆维度比较', '入职前还要补什么'),
      'suggestedMaterials', JSON_ARRAY('简历', '两个岗位说明'),
      'expectedOutcomes', JSON_ARRAY('明确选择结论', '形成入职前准备清单')
    )
  ),
  (
    @order_008,
    @student_105,
    @mentor_user_id,
    9900,
    'PAID',
    '我想先做一次图文简历问诊，把春招中后段的简历重点和项目排序尽快定下来。',
    '2026-04-06 15:10:00',
    NULL,
    '2026-04-06 15:05:00',
    '2026-04-06 15:10:00',
    NULL,
    NULL,
    'RESUME_DIAGNOSIS',
    'MENTOR_MARKETPLACE_FAVORITES',
    JSON_OBJECT(
      'background', '春招中后段面试反馈开始集中在“重点不突出”，希望先快速收一轮简历结构。',
      'expectedHelp', '希望老师先帮我判断项目排序、亮点提炼和岗位匹配度。',
      'primaryConcern', '简历里做过的三个项目不知道哪个该主打，哪个应该下沉到补充部分。',
      'additionalNotes', '这次先图文沟通，如果需要再补一轮材料。',
      'attemptedActions', '已经改过几版简历，但每次越改越像流水账。'
    ),
    '学生希望先通过图文问诊快速收一轮简历结构，尽快确定项目排序和重点表达。',
    JSON_ARRAY('三个项目怎么排优先级', '亮点和职责怎样拆开写', '哪些内容应该删除'),
    JSON_ARRAY('拿到一版更聚焦的简历结构', '明确后续还要补哪些材料'),
    'RESUME,SUPPLEMENTARY',
    JSON_OBJECT(
      'scene', '简历诊断',
      'summaryDraft', '希望先快速确认春招中后段简历的主项目和排序方式。',
      'coreQuestions', JSON_ARRAY('主项目怎么选', '哪些内容该删'),
      'suggestedMaterials', JSON_ARRAY('简历', '补充说明'),
      'expectedOutcomes', JSON_ARRAY('形成一版更聚焦的简历', '明确后续补充方向')
    )
  ),
  (
    @order_009,
    @student_106,
    @mentor_user_id,
    20900,
    'CLOSED',
    '系统设计面里我总是能说出模块，却讲不清约束、容量和取舍，想做一次完整复盘。',
    '2026-03-20 18:12:00',
    '2026-03-21 22:10:00',
    '2026-03-20 18:05:00',
    '2026-03-21 22:10:00',
    '2026-03-21 20:30:00',
    '2026-03-21 21:15:00',
    'INTERVIEW_REVIEW',
    'MENTOR_MARKETPLACE_RECOMMENDATION',
    JSON_OBJECT(
      'background', '最近做系统设计面试时，回答总是停留在组件罗列层，没有形成约束到方案的推导。',
      'expectedHelp', '希望老师帮我把高并发场景下的设计回答拆成更稳定的结构。',
      'primaryConcern', '容量估算、瓶颈判断和取舍理由总是说不扎实。',
      'additionalNotes', '如果能顺带结合我现有项目经历给几条映射建议会更好。',
      'attemptedActions', '已经看过几套八股题答案，但落到自己表达时还是容易散。'
    ),
    '重点是把系统设计回答从组件罗列升级到“场景约束-容量估算-核心方案-风险兜底”的完整链路。',
    JSON_ARRAY('系统设计回答怎么先讲约束', '容量估算需要细到什么程度', '方案取舍应该怎么解释'),
    JSON_ARRAY('形成一版更稳定的系统设计回答结构', '知道后续该如何结合项目经历举例'),
    'RESUME,PROJECT_MATERIAL',
    JSON_OBJECT(
      'scene', '模拟面试复盘',
      'summaryDraft', '希望把系统设计回答讲出约束、容量和取舍，而不是停留在组件堆砌。',
      'coreQuestions', JSON_ARRAY('如何先讲约束', '如何解释取舍'),
      'suggestedMaterials', JSON_ARRAY('简历', '项目材料'),
      'expectedOutcomes', JSON_ARRAY('形成一版系统设计答题框架', '明确后续练习重点')
    )
  );

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
  (@order_006, 'ALIPAY', 'SANDBOX', 'TRADE-CONS-GUHANG-20260405-006-INIT', 17900, 'INIT', 'seed-guhang-payment-006-init', NULL, '2026-04-05 11:20:10', '2026-04-05 11:20:10'),
  (@order_006, 'ALIPAY', 'SANDBOX', 'TRADE-CONS-GUHANG-20260405-006-SUCCESS', 17900, 'SUCCESS', 'seed-guhang-payment-006-success', NULL, '2026-04-05 11:28:00', '2026-04-05 11:28:00'),
  (@order_007, 'ALIPAY', 'SANDBOX', 'TRADE-CONS-GUHANG-20260401-007-INIT', 14900, 'INIT', 'seed-guhang-payment-007-init', NULL, '2026-04-01 14:10:10', '2026-04-01 14:10:10'),
  (@order_007, 'ALIPAY', 'SANDBOX', 'TRADE-CONS-GUHANG-20260401-007-SUCCESS', 14900, 'SUCCESS', 'seed-guhang-payment-007-success', NULL, '2026-04-01 14:18:00', '2026-04-01 14:18:00'),
  (@order_008, 'ALIPAY', 'SANDBOX', 'TRADE-CONS-GUHANG-20260406-008-INIT', 9900, 'INIT', 'seed-guhang-payment-008-init', NULL, '2026-04-06 15:05:10', '2026-04-06 15:05:10'),
  (@order_008, 'ALIPAY', 'SANDBOX', 'TRADE-CONS-GUHANG-20260406-008-SUCCESS', 9900, 'SUCCESS', 'seed-guhang-payment-008-success', NULL, '2026-04-06 15:10:00', '2026-04-06 15:10:00'),
  (@order_009, 'ALIPAY', 'SANDBOX', 'TRADE-CONS-GUHANG-20260320-009-INIT', 20900, 'INIT', 'seed-guhang-payment-009-init', NULL, '2026-03-20 18:05:10', '2026-03-20 18:05:10'),
  (@order_009, 'ALIPAY', 'SANDBOX', 'TRADE-CONS-GUHANG-20260320-009-SUCCESS', 20900, 'SUCCESS', 'seed-guhang-payment-009-success', NULL, '2026-03-20 18:12:00', '2026-03-20 18:12:00');

INSERT INTO consult_messages (
  order_no,
  sender_user_id,
  sender_role,
  message_text,
  created_at
)
VALUES
  (@order_006, @student_101, 'STUDENT', '老师您好，我现在一讲这个项目就会把缓存、队列、降级全摊开，像在背模块清单。', '2026-04-05 11:36:00'),
  (@order_006, @mentor_user_id, 'MENTOR', '先只保留一条业务主线，比如“高峰期如何保证下单链路可用”，其他技术动作都围着它展开。', '2026-04-05 20:58:00'),
  (@order_006, @student_101, 'STUDENT', '明白了，我会先把业务约束和失败场景补完整，再回头讲具体方案。', '2026-04-05 21:32:00'),

  (@order_007, @student_104, 'STUDENT', '两个 offer 我都能接受，但总怕选完之后发现成长速度和自己预期不一致。', '2026-04-02 20:03:00'),
  (@order_007, @mentor_user_id, 'MENTOR', '先拆成四个维度看：带教、任务密度、是否接核心链路、以及一年后可迁移的能力。', '2026-04-02 20:16:00'),

  (@order_008, @student_105, 'STUDENT', '老师您好，我这次先想快速收一轮简历结构，尤其是三个项目的排序和主次。', '2026-04-06 15:12:00'),

  (@order_009, @student_106, 'STUDENT', '系统设计题里我总能想到 Redis、MQ、数据库分片，但不知道该从哪开始讲。', '2026-03-21 20:32:00'),
  (@order_009, @mentor_user_id, 'MENTOR', '第一句先别讲组件，先讲约束：流量多大、延迟目标是多少、容错边界在哪里。', '2026-03-21 20:40:00'),
  (@order_009, @student_106, 'STUDENT', '那我是不是应该先做一个粗容量估算，再决定哪些链路必须异步化？', '2026-03-21 20:55:00'),
  (@order_009, @mentor_user_id, 'MENTOR', '对，容量估算和瓶颈判断要先出来，后面的缓存、异步和拆分才有依据。', '2026-03-21 21:02:00');

INSERT INTO consult_reviews (
  order_no,
  student_user_id,
  mentor_user_id,
  rating,
  comment,
  created_at
)
VALUES
  (@order_007, @student_104, @mentor_user_id, 4, '老师把 offer 比较拆得很细，不只是讲大厂和中厂的标签，而是真的帮我拆清楚了成长路径。', '2026-04-02 21:42:00'),
  (@order_009, @student_106, @mentor_user_id, 5, '终于知道系统设计该先讲约束和容量，不再是一上来就堆组件了。', '2026-03-21 22:18:00');

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
  (@order_006, @student_101, 'RESUME', 'RESUME', 'ORDER_CREATE', 'liwenhao-java-backend-resume.pdf', 'application/pdf', 496640, 'bishe-consult-materials', 'consult-orders/CONS-GUHANG-20260405-006/RESUME/liwenhao-java-backend-resume.pdf', '包含缓存一致性和服务降级项目的一版简历。', 'CURRENT', NULL, '2026-04-05 11:21:00', '2026-04-05 11:21:00'),
  (@order_006, @student_101, 'PROJECT_MATERIAL', 'PROJECT_MATERIAL', 'ORDER_CREATE', 'cache-queue-degrade-architecture.pdf', 'application/pdf', 702464, 'bishe-consult-materials', 'consult-orders/CONS-GUHANG-20260405-006/PROJECT_MATERIAL/cache-queue-degrade-architecture.pdf', '高峰期下单链路的结构图和降级策略说明。', 'CURRENT', NULL, '2026-04-05 11:22:00', '2026-04-05 11:22:00'),
  (@order_007, @student_104, 'RESUME', 'RESUME', 'ORDER_CREATE', 'chenzixuan-offer-choice-resume.pdf', 'application/pdf', 438272, 'bishe-consult-materials', 'consult-orders/CONS-GUHANG-20260401-007/RESUME/chenzixuan-offer-choice-resume.pdf', '当前简历版本，重点是项目经历和求职方向。', 'CURRENT', NULL, '2026-04-01 14:11:00', '2026-04-01 14:11:00'),
  (@order_007, @student_104, 'JOB_DESCRIPTION', 'JOB_DESCRIPTION', 'ORDER_CREATE', 'offer-comparison-notes.pdf', 'application/pdf', 214016, 'bishe-consult-materials', 'consult-orders/CONS-GUHANG-20260401-007/JOB_DESCRIPTION/offer-comparison-notes.pdf', '两个 offer 的岗位职责和团队情况整理。', 'CURRENT', NULL, '2026-04-01 14:12:00', '2026-04-01 14:12:00'),
  (@order_008, @student_105, 'RESUME', 'RESUME', 'ORDER_CREATE', 'sunzeyu-spring-resume-v4.pdf', 'application/pdf', 421888, 'bishe-consult-materials', 'consult-orders/CONS-GUHANG-20260406-008/RESUME/sunzeyu-spring-resume-v4.pdf', '准备做中后段春招简历收口的一版简历。', 'CURRENT', NULL, '2026-04-06 15:06:00', '2026-04-06 15:06:00'),
  (@order_008, @student_105, 'SUPPLEMENTARY', 'SUPPLEMENTARY', 'ORDER_CREATE', 'project-priority-notes.docx', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 188416, 'bishe-consult-materials', 'consult-orders/CONS-GUHANG-20260406-008/SUPPLEMENTARY/project-priority-notes.docx', '三个项目的主次排序和老师希望重点看的问题。', 'CURRENT', NULL, '2026-04-06 15:07:00', '2026-04-06 15:07:00'),
  (@order_009, @student_106, 'RESUME', 'RESUME', 'ORDER_CREATE', 'heqingyan-system-design-resume.pdf', 'application/pdf', 447488, 'bishe-consult-materials', 'consult-orders/CONS-GUHANG-20260320-009/RESUME/heqingyan-system-design-resume.pdf', '包含服务端项目和系统设计准备情况的一版简历。', 'CURRENT', NULL, '2026-03-20 18:06:00', '2026-03-20 18:06:00'),
  (@order_009, @student_106, 'PROJECT_MATERIAL', 'PROJECT_MATERIAL', 'ORDER_CREATE', 'high-concurrency-design-notes.pdf', 'application/pdf', 688128, 'bishe-consult-materials', 'consult-orders/CONS-GUHANG-20260320-009/PROJECT_MATERIAL/high-concurrency-design-notes.pdf', '高并发系统设计草稿和容量估算笔记。', 'CURRENT', NULL, '2026-03-20 18:07:00', '2026-03-20 18:07:00');

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
  (@mentor_user_id, '2026-04-07 19:30:00', '2026-04-07 20:15:00', 'BOOKED', @order_006, '2026-04-05 11:20:00', '2026-04-05 21:40:00'),
  (@mentor_user_id, '2026-04-02 20:00:00', '2026-04-02 20:45:00', 'BOOKED', @order_007, '2026-04-01 14:10:00', '2026-04-02 21:36:00'),
  (@mentor_user_id, '2026-03-21 20:30:00', '2026-03-21 21:15:00', 'BOOKED', @order_009, '2026-03-20 18:05:00', '2026-03-21 22:10:00')
ON DUPLICATE KEY UPDATE
  status = VALUES(status),
  booked_order_no = VALUES(booked_order_no),
  created_at = VALUES(created_at),
  updated_at = VALUES(updated_at);

DROP TEMPORARY TABLE IF EXISTS tmp_guhang_extra_finance_orders;

CREATE TEMPORARY TABLE tmp_guhang_extra_finance_orders (
  order_no VARCHAR(64) NOT NULL PRIMARY KEY,
  student_user_id BIGINT NOT NULL,
  amount_fen INT NOT NULL,
  status VARCHAR(20) NOT NULL,
  question_text TEXT NOT NULL,
  paid_at DATETIME NOT NULL,
  closed_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  appointment_start_at DATETIME NULL,
  appointment_end_at DATETIME NULL,
  scene_code VARCHAR(64) NOT NULL,
  source_page VARCHAR(64) NOT NULL,
  scene_label VARCHAR(64) NOT NULL,
  summary_draft TEXT NOT NULL,
  primary_concern VARCHAR(255) NOT NULL,
  material_types VARCHAR(255) NOT NULL,
  review_rating TINYINT NULL,
  review_comment TEXT NULL,
  review_created_at DATETIME NULL,
  refund_reason TEXT NULL,
  refund_review_note TEXT NULL,
  refund_created_at DATETIME NULL,
  refund_reviewed_at DATETIME NULL
);

INSERT INTO tmp_guhang_extra_finance_orders (
  order_no,
  student_user_id,
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
  scene_label,
  summary_draft,
  primary_concern,
  material_types,
  review_rating,
  review_comment,
  review_created_at,
  refund_reason,
  refund_review_note,
  refund_created_at,
  refund_reviewed_at
)
VALUES
  (
    'CONS-GUHANG-20260226-010',
    @student_102,
    11800,
    'CLOSED',
    '我准备把第一次后端实习里的网关限流和监控告警写进简历，但现在讲出来还是像在记流水账。',
    '2026-02-26 10:42:00',
    '2026-02-27 20:18:00',
    '2026-02-26 10:35:00',
    '2026-02-27 20:18:00',
    '2026-02-27 19:30:00',
    '2026-02-27 20:15:00',
    'PROJECT_EXPRESSION',
    'MENTOR_MARKETPLACE',
    '项目表达',
    '想把网关限流和监控告警项目讲成一段更像真实业务的经历。',
    '如何把限流、监控和故障处置串成业务闭环',
    'RESUME,PROJECT_MATERIAL',
    5,
    '老师帮我把项目里真正有价值的约束和动作捞出来了，复盘后整段表达顺了很多。',
    '2026-02-27 20:24:00',
    NULL,
    NULL,
    NULL,
    NULL
  ),
  (
    'CONS-GUHANG-20260228-011',
    @student_103,
    9600,
    'REFUNDED',
    '原本想做一轮简历快调，但这周临时转去准备产品岗，当前问题边界已经完全变了。',
    '2026-02-28 14:25:00',
    '2026-03-01 11:40:00',
    '2026-02-28 14:18:00',
    '2026-03-01 11:40:00',
    NULL,
    NULL,
    'RESUME_DIAGNOSIS',
    'MENTOR_MARKETPLACE_FAVORITES',
    '简历诊断',
    '问题方向临时切换，想先取消本轮简历快调。',
    '当前准备方向变化后，旧问题单已经不适合继续推进',
    'RESUME,SUPPLEMENTARY',
    NULL,
    NULL,
    NULL,
    '学生本周临时转去准备产品岗，原本约好的后端简历快调已经不再适配当前目标，需要退款后重整材料再重新预约。',
    '已同意退款，建议学生先把目标岗位方向确定，再重新整理一版简历和问题清单。',
    '2026-03-01 09:15:00',
    '2026-03-01 11:40:00'
  ),
  (
    'CONS-GUHANG-20260302-012',
    @student_112,
    15100,
    'ANSWERED',
    '想把接下来两周的投递优先级和岗位组合重新排一下，不知道该先冲大厂还是先拿中厂稳定面试机会。',
    '2026-03-02 09:55:00',
    NULL,
    '2026-03-02 09:48:00',
    '2026-03-02 21:36:00',
    '2026-03-03 20:00:00',
    '2026-03-03 20:45:00',
    'DELIVERY_STRATEGY',
    'MENTOR_MARKETPLACE_RECOMMENDATION',
    '校招投递策略',
    '想把两周后的投递优先级和岗位组合重新排一下。',
    '手上岗位太散，不知道先冲大厂还是先拿中厂稳定面试机会',
    'RESUME,JOB_DESCRIPTION',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
  ),
  (
    'CONS-GUHANG-20260304-013',
    @student_101,
    18400,
    'CLOSED',
    '我最近面试回答总是先讲技术组件，想做一轮针对高并发项目的表达复盘。',
    '2026-03-04 18:12:00',
    '2026-03-05 21:20:00',
    '2026-03-04 18:05:00',
    '2026-03-05 21:20:00',
    '2026-03-05 20:00:00',
    '2026-03-05 20:45:00',
    'INTERVIEW_REVIEW',
    'MENTOR_MARKETPLACE',
    '模拟面试复盘',
    '想把高并发项目的回答从组件罗列改成更像业务推导的说法。',
    '如何先讲场景压力和瓶颈，再讲缓存和异步方案',
    'RESUME,PROJECT_MATERIAL',
    4,
    '老师会不断把我拉回到业务约束上，复盘完终于知道一开口该先说什么了。',
    '2026-03-05 21:28:00',
    NULL,
    NULL,
    NULL,
    NULL
  ),
  (
    'CONS-GUHANG-20260306-014',
    @student_104,
    8700,
    'PAID',
    '先做一轮图文简历快修，想确认项目排序和标题力度，避免春招中段越改越乱。',
    '2026-03-06 12:15:00',
    NULL,
    '2026-03-06 12:06:00',
    '2026-03-06 12:15:00',
    NULL,
    NULL,
    'RESUME_DIAGNOSIS',
    'MENTOR_MARKETPLACE',
    '简历诊断',
    '先做一轮图文简历快修，确认项目排序和标题力度。',
    '三段经历该怎么排序才更像后端候选人',
    'RESUME,SUPPLEMENTARY',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
  ),
  (
    'CONS-GUHANG-20260308-015',
    @student_105,
    13200,
    'CLOSED',
    '春招中段面试开始变密，想把投递节奏和复盘安排重新排一下，不然每天都在忙但没有重点。',
    '2026-03-08 15:20:00',
    '2026-03-09 21:05:00',
    '2026-03-08 15:10:00',
    '2026-03-09 21:05:00',
    '2026-03-09 20:00:00',
    '2026-03-09 20:45:00',
    'DELIVERY_STRATEGY',
    'MENTOR_MARKETPLACE_RECOMMENDATION',
    '校招投递策略',
    '春招中段面试变密，想把投递节奏和复盘安排重新排一下。',
    '如何在投递、笔试和面试复盘之间分配精力',
    'RESUME,JOB_DESCRIPTION',
    5,
    '这次把一周节奏拆得很清楚，老师给的优先级让我少了很多无效忙碌。',
    '2026-03-09 21:12:00',
    NULL,
    NULL,
    NULL,
    NULL
  ),
  (
    'CONS-GUHANG-20260310-016',
    @student_106,
    14300,
    'REFUNDED',
    '原计划做系统设计复盘，但这周临时中断准备，当前不适合继续推进这一轮咨询。',
    '2026-03-10 10:35:00',
    '2026-03-11 16:12:00',
    '2026-03-10 10:28:00',
    '2026-03-11 16:12:00',
    NULL,
    NULL,
    'INTERVIEW_REVIEW',
    'MENTOR_MARKETPLACE_FAVORITES',
    '模拟面试复盘',
    '原计划做系统设计复盘，但学生这周临时中断准备。',
    '当前准备节奏中断后，希望退款重新整理问题边界',
    'RESUME,PROJECT_MATERIAL',
    NULL,
    NULL,
    NULL,
    '学生本周临时暂停系统设计准备，原计划的复盘主题和材料都需要重新整理，希望本轮先退款，后续再带着新问题预约。',
    '已同意退款，建议学生等准备节奏恢复后，再重新整理一版系统设计问题清单。',
    '2026-03-11 14:05:00',
    '2026-03-11 16:12:00'
  ),
  (
    'CONS-GUHANG-20260312-017',
    @student_102,
    22100,
    'CLOSED',
    '想把负责的消息链路和失败重试方案讲成一套完整故事，而不是被追问时才一点点往外补。',
    '2026-03-12 19:28:00',
    '2026-03-13 22:00:00',
    '2026-03-12 19:20:00',
    '2026-03-13 22:00:00',
    '2026-03-13 20:30:00',
    '2026-03-13 21:20:00',
    'PROJECT_EXPRESSION',
    'MENTOR_MARKETPLACE_RECOMMENDATION',
    '项目表达',
    '想把消息链路和失败重试方案讲成一套完整故事。',
    '怎么把消息重试、幂等和告警讲出取舍',
    'RESUME,PROJECT_MATERIAL',
    5,
    '老师会逼着我把“为什么这么做”讲出来，不再只是背方案名词了。',
    '2026-03-13 22:08:00',
    NULL,
    NULL,
    NULL,
    NULL
  ),
  (
    'CONS-GUHANG-20260314-018',
    @student_103,
    10900,
    'ANSWERED',
    '先图文看一版简历，想把跨方向经历里还能保留的技术内容挑出来，别让岗位定位继续发散。',
    '2026-03-14 11:12:00',
    NULL,
    '2026-03-14 11:05:00',
    '2026-03-14 20:48:00',
    NULL,
    NULL,
    'RESUME_DIAGNOSIS',
    'MENTOR_MARKETPLACE',
    '简历诊断',
    '想先图文看一版简历，把跨方向经历里还能保留的技术内容挑出来。',
    '跨方向经历里哪些内容值得保留，哪些会分散岗位定位',
    'RESUME,SUPPLEMENTARY',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
  ),
  (
    'CONS-GUHANG-20260316-019',
    @student_112,
    17600,
    'CLOSED',
    '这周连续两场后端一面，想做一轮项目深挖复盘，避免回答像在背简历而不是自己真的做过。',
    '2026-03-16 16:50:00',
    '2026-03-17 21:10:00',
    '2026-03-16 16:42:00',
    '2026-03-17 21:10:00',
    '2026-03-17 20:00:00',
    '2026-03-17 20:50:00',
    'INTERVIEW_REVIEW',
    'MENTOR_MARKETPLACE_RECOMMENDATION',
    '模拟面试复盘',
    '做一轮项目深挖复盘，准备本周连续两场后端一面。',
    '怎样把项目追问回答得更像自己真的做过而不是背稿',
    'RESUME,PROJECT_MATERIAL',
    4,
    '老师会不断追问我细节和取舍，虽然有点痛苦，但这次真的把回答打磨扎实了。',
    '2026-03-17 21:18:00',
    NULL,
    NULL,
    NULL,
    NULL
  ),
  (
    'CONS-GUHANG-20260318-020',
    @student_101,
    15400,
    'PAID',
    '想先把接下来一周的投递优先级和岗位筛选标准定下来，不然投递列表越攒越长。',
    '2026-03-18 09:42:00',
    NULL,
    '2026-03-18 09:35:00',
    '2026-03-18 09:42:00',
    NULL,
    NULL,
    'DELIVERY_STRATEGY',
    'MENTOR_MARKETPLACE',
    '校招投递策略',
    '先把接下来一周的投递优先级和岗位筛选标准定下来。',
    '如何把投递列表缩到真正适合自己当前背景的岗位',
    'RESUME,JOB_DESCRIPTION',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
  ),
  (
    'CONS-GUHANG-20260322-021',
    @student_104,
    19800,
    'ANSWERED',
    '希望把做过的用户增长后台项目讲得更像工程负责人视角，而不是只停留在做了哪些接口。',
    '2026-03-22 13:30:00',
    NULL,
    '2026-03-22 13:18:00',
    '2026-03-22 22:05:00',
    '2026-03-23 19:30:00',
    '2026-03-23 20:15:00',
    'PROJECT_EXPRESSION',
    'MENTOR_MARKETPLACE_RECOMMENDATION',
    '项目表达',
    '希望把用户增长后台项目讲得更像工程负责人视角。',
    '怎样把指标、约束和方案取舍讲在同一条主线上',
    'RESUME,PROJECT_MATERIAL',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
  ),
  (
    'CONS-GUHANG-20260326-022',
    @student_105,
    12500,
    'REFUNDED',
    '原计划想做简历快调，但拿到新的岗位 JD 后决定重新准备，当前这轮建议已经不适用。',
    '2026-03-26 15:12:00',
    '2026-03-27 18:30:00',
    '2026-03-26 15:04:00',
    '2026-03-27 18:30:00',
    NULL,
    NULL,
    'RESUME_DIAGNOSIS',
    'MENTOR_MARKETPLACE_FAVORITES',
    '简历诊断',
    '原计划做简历快调，但学生拿到新岗位 JD 后决定重新准备。',
    '目标岗位变化后，原先这轮简历建议已不适用',
    'RESUME,SUPPLEMENTARY',
    NULL,
    NULL,
    NULL,
    '学生拿到的新岗位 JD 明显更偏数据方向，原先这轮后端简历建议不再适配，希望先退款再重新梳理材料。',
    '已同意退款，建议学生根据新岗位 JD 重新整理项目排序和关键词后再预约。',
    '2026-03-27 16:10:00',
    '2026-03-27 18:30:00'
  ),
  (
    'CONS-GUHANG-20260403-023',
    @student_112,
    16700,
    'CLOSED',
    'offer 快确定了，想把薪资沟通和入职节奏的说法再过一遍，避免最后阶段表达失误。',
    '2026-04-03 10:15:00',
    '2026-04-04 21:18:00',
    '2026-04-03 10:06:00',
    '2026-04-04 21:18:00',
    '2026-04-04 20:00:00',
    '2026-04-04 20:45:00',
    'DELIVERY_STRATEGY',
    'MENTOR_MARKETPLACE',
    '校招投递策略',
    '想把 offer 前的薪资沟通和入职节奏说法再过一遍。',
    '怎样把自己的底线和优先级说清楚，又不显得太僵硬',
    'RESUME,JOB_DESCRIPTION',
    5,
    '老师把沟通边界和说法拆得很清楚，最后一轮心态稳了很多。',
    '2026-04-04 21:26:00',
    NULL,
    NULL,
    NULL,
    NULL
  );

DELETE pr
FROM payment_records pr
JOIN tmp_guhang_extra_finance_orders t ON t.order_no = pr.order_no;

DELETE ar
FROM consult_after_sales_requests ar
JOIN tmp_guhang_extra_finance_orders t ON t.order_no = ar.order_no;

DELETE att
FROM consult_order_attachments att
JOIN tmp_guhang_extra_finance_orders t ON t.order_no = att.order_no;

DELETE rv
FROM consult_reviews rv
JOIN tmp_guhang_extra_finance_orders t ON t.order_no = rv.order_no;

DELETE msg
FROM consult_messages msg
JOIN tmp_guhang_extra_finance_orders t ON t.order_no = msg.order_no;

DELETE ss
FROM mentor_schedule_slots ss
JOIN tmp_guhang_extra_finance_orders t ON t.order_no = ss.booked_order_no;

DELETE co
FROM consult_orders co
JOIN tmp_guhang_extra_finance_orders t ON t.order_no = co.order_no;

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
SELECT
  t.order_no,
  t.student_user_id,
  @mentor_user_id,
  t.amount_fen,
  t.status,
  t.question_text,
  t.paid_at,
  t.closed_at,
  t.created_at,
  t.updated_at,
  t.appointment_start_at,
  t.appointment_end_at,
  t.scene_code,
  t.source_page,
  JSON_OBJECT(
    'background', CONCAT('当前希望围绕“', t.summary_draft, '”做一轮梳理。'),
    'expectedHelp', '希望导师帮助判断优先级，并给出更稳的一版下一步建议。',
    'primaryConcern', t.primary_concern,
    'additionalNotes', '这批为顾航的财务与订单演示扩展数据，内容按真实咨询口径补齐。',
    'attemptedActions', '已经自己整理过一版思路，但表达还不够稳定。'
  ),
  t.primary_concern,
  JSON_ARRAY(
    t.primary_concern,
    '下一轮应该优先补哪一块',
    '怎样把表达和材料再收紧'
  ),
  JSON_ARRAY(
    '明确当前问题的优先级',
    '拿到一版更稳的下一步动作'
  ),
  t.material_types,
  JSON_OBJECT(
    'scene', t.scene_label,
    'summaryDraft', t.summary_draft,
    'coreQuestions', JSON_ARRAY(t.primary_concern, '下一轮应该优先补哪一块'),
    'suggestedMaterials', JSON_ARRAY(
      '简历',
      CASE
        WHEN t.material_types LIKE '%PROJECT_MATERIAL%' THEN '项目材料'
        WHEN t.material_types LIKE '%JOB_DESCRIPTION%' THEN '岗位说明'
        ELSE '补充材料'
      END
    ),
    'expectedOutcomes', JSON_ARRAY(
      '明确当前问题的优先级',
      '拿到一版更稳的下一步动作'
    )
  )
FROM tmp_guhang_extra_finance_orders t;

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
  t.order_no,
  'ALIPAY',
  'SANDBOX',
  CONCAT('TRADE-', t.order_no, '-INIT'),
  t.amount_fen,
  'INIT',
  CONCAT('seed-guhang-', LOWER(REPLACE(t.order_no, 'CONS-', '')), '-init'),
  NULL,
  DATE_ADD(t.created_at, INTERVAL 10 SECOND),
  DATE_ADD(t.created_at, INTERVAL 10 SECOND)
FROM tmp_guhang_extra_finance_orders t
;

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
  t.order_no,
  'ALIPAY',
  'SANDBOX',
  CONCAT('TRADE-', t.order_no, '-SUCCESS'),
  t.amount_fen,
  'SUCCESS',
  CONCAT('seed-guhang-', LOWER(REPLACE(t.order_no, 'CONS-', '')), '-success'),
  NULL,
  t.paid_at,
  t.paid_at
FROM tmp_guhang_extra_finance_orders t;

INSERT INTO consult_reviews (
  order_no,
  student_user_id,
  mentor_user_id,
  rating,
  comment,
  created_at
)
SELECT
  t.order_no,
  t.student_user_id,
  @mentor_user_id,
  t.review_rating,
  t.review_comment,
  t.review_created_at
FROM tmp_guhang_extra_finance_orders t
WHERE t.review_rating IS NOT NULL;

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
SELECT
  t.order_no,
  t.student_user_id,
  'REFUND',
  'APPROVED',
  t.refund_reason,
  t.refund_review_note,
  @mentor_user_id,
  0,
  t.refund_reviewed_at,
  t.refund_created_at,
  COALESCE(t.refund_reviewed_at, t.refund_created_at)
FROM tmp_guhang_extra_finance_orders t
WHERE t.status = 'REFUNDED';

INSERT INTO mentor_schedule_slots (
  mentor_user_id,
  start_at,
  end_at,
  status,
  booked_order_no,
  created_at,
  updated_at
)
SELECT
  @mentor_user_id,
  t.appointment_start_at,
  t.appointment_end_at,
  'BOOKED',
  t.order_no,
  t.created_at,
  t.updated_at
FROM tmp_guhang_extra_finance_orders t
WHERE t.appointment_start_at IS NOT NULL
ON DUPLICATE KEY UPDATE
  status = VALUES(status),
  booked_order_no = VALUES(booked_order_no),
  created_at = VALUES(created_at),
  updated_at = VALUES(updated_at);

DELETE FROM mentor_withdrawal_requests
WHERE mentor_user_id = @mentor_user_id;

INSERT INTO mentor_withdrawal_requests (
  mentor_user_id,
  amount_fen,
  status,
  note,
  created_at,
  updated_at
)
VALUES
  (@mentor_user_id, 9800, 'PENDING', '本周新增两笔咨询后，先提交一笔小额结算申请，方便核对最新可提现额度。', '2026-04-05 21:10:00', '2026-04-05 21:10:00'),
  (@mentor_user_id, 7600, 'PROCESSING', '三月底完成的一笔项目复盘订单已进入处理，当前等待平台结算回执。', '2026-04-03 10:15:00', '2026-04-03 15:40:00'),
  (@mentor_user_id, 12000, 'COMPLETED', '上一轮月末结算已完成入账，用来对照财务中心的已入账状态。', '2026-03-28 11:15:00', '2026-03-29 09:05:00'),
  (@mentor_user_id, 5400, 'REJECTED', '曾提交过一笔临时申请，但因为到账备注不完整被退回，后续改按新口径重新整理。', '2026-03-22 16:20:00', '2026-03-22 19:10:00'),
  (@mentor_user_id, 6800, 'CANCELED', '一笔订单进入退款收口后，主动撤回了原申请，等待重新核算可提现额度。', '2026-03-18 09:35:00', '2026-03-19 08:10:00'),
  (@mentor_user_id, 9100, 'COMPLETED', '更早一轮已完成订单的结算记录，保留作历史流转演示数据。', '2026-03-12 14:05:00', '2026-03-13 10:20:00');

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
    updated_at = '2026-04-06 19:40:00'
WHERE user_id = @mentor_user_id;

DROP TEMPORARY TABLE IF EXISTS tmp_guhang_extra_finance_orders;

COMMIT;
