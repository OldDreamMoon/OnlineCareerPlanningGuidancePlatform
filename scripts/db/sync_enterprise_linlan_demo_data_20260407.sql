-- 林岚企业侧演示数据补强脚本
-- 目标：
-- 1. 补齐北辰智联（林岚）企业账号的多任务、多提交、多结果留痕样本
-- 2. 补齐企业通知中心、认证历史与通知偏好样本
-- 3. 全部操作保持幂等，可重复执行

SET NAMES utf8mb4;

SET @seed_now = NOW();
SET @enterprise_email = 'enterprise.beichenhr@bishe.local';
SET @enterprise_user_id = (
  SELECT id
  FROM users
  WHERE email = @enterprise_email
    AND role = 'ENTERPRISE'
    AND is_deleted = 0
  LIMIT 1
);
SET @admin_user_id = (
  SELECT id
  FROM users
  WHERE role = 'ADMIN'
    AND is_deleted = 0
  ORDER BY id
  LIMIT 1
);
SET @current_certification_submission_id = (
  SELECT id
  FROM certification_submissions
  WHERE user_id = @enterprise_user_id
    AND is_current = 1
  ORDER BY id DESC
  LIMIT 1
);

START TRANSACTION;

-- 1. 企业资料补强
UPDATE enterprise_profiles
SET
  company_name = '北辰智联',
  industry = '企业服务 / SaaS',
  company_size = '50-200 人',
  hiring_tags = '前端产品化,增长分析,B端交付,线索运营,客户成功',
  contact_title = '招聘负责人',
  bio = '北辰智联当前重点在做校招活动页、线索分层工具、客户成功周报和知识库检索等一批偏产品化的内部项目。我们更希望通过轻量企业任务先筛出能把问题拆清楚、方案讲明白、并愿意把页面与数据验证一起想透的学生。',
  external_links = '官网：https://talent.beichen-demo.local
领英：https://www.linkedin.com/company/beichen-demo
案例库：https://docs.example.com/beichen-cases',
  preferences = '更偏好主动说明判断依据、验证路径和交付边界的同学；如果有多份材料，请标出最推荐先看的那一份，并在开头用 3-5 句话说明你的核心结论。',
  approval_status = 'APPROVED',
  updated_at = DATE_SUB(@seed_now, INTERVAL 20 MINUTE)
WHERE user_id = @enterprise_user_id;

-- 2. 通知偏好补齐
DROP TEMPORARY TABLE IF EXISTS tmp_linlan_notification_preferences;
CREATE TEMPORARY TABLE tmp_linlan_notification_preferences (
  category VARCHAR(32) NOT NULL PRIMARY KEY,
  inbox_enabled TINYINT(1) NOT NULL,
  websocket_enabled TINYINT(1) NOT NULL,
  browser_popup_enabled TINYINT(1) NOT NULL,
  email_enabled TINYINT(1) NOT NULL,
  email_urgency_threshold VARCHAR(20) NOT NULL,
  quiet_hours_json VARCHAR(255) NULL
);

INSERT INTO tmp_linlan_notification_preferences (
  category, inbox_enabled, websocket_enabled, browser_popup_enabled, email_enabled, email_urgency_threshold, quiet_hours_json
)
VALUES
  ('AI_TASK', 1, 1, 0, 0, 'HIGH', NULL),
  ('CONSULT', 1, 1, 0, 0, 'HIGH', NULL),
  ('BOUNTY', 1, 1, 1, 1, 'NORMAL', '{"start":"23:30","end":"08:00"}'),
  ('CERTIFICATION', 1, 1, 1, 1, 'HIGH', NULL),
  ('SYSTEM', 1, 1, 1, 1, 'HIGH', NULL),
  ('COMMUNITY', 1, 0, 0, 0, 'HIGH', NULL);

UPDATE notification_preferences target
JOIN tmp_linlan_notification_preferences source ON source.category = target.category
SET
  target.inbox_enabled = source.inbox_enabled,
  target.websocket_enabled = source.websocket_enabled,
  target.browser_popup_enabled = source.browser_popup_enabled,
  target.email_enabled = source.email_enabled,
  target.email_urgency_threshold = source.email_urgency_threshold,
  target.quiet_hours_json = source.quiet_hours_json,
  target.updated_at = DATE_SUB(@seed_now, INTERVAL 18 MINUTE)
WHERE target.user_id = @enterprise_user_id;

INSERT INTO notification_preferences (
  user_id, category, inbox_enabled, websocket_enabled, browser_popup_enabled,
  email_enabled, email_urgency_threshold, quiet_hours_json, updated_at
)
SELECT
  @enterprise_user_id,
  source.category,
  source.inbox_enabled,
  source.websocket_enabled,
  source.browser_popup_enabled,
  source.email_enabled,
  source.email_urgency_threshold,
  source.quiet_hours_json,
  DATE_SUB(@seed_now, INTERVAL 18 MINUTE)
FROM tmp_linlan_notification_preferences source
WHERE NOT EXISTS (
  SELECT 1
  FROM notification_preferences target
  WHERE target.user_id = @enterprise_user_id
    AND target.category = source.category
);

-- 3. 认证历史补一条旧补件记录，并把当前通过记录接成历史链
INSERT INTO certification_submissions (
  user_id, user_role, real_name, company_name, job_title,
  status, review_note, reviewed_by, reviewed_at, previous_submission_id,
  is_current, submitted_at, created_at, updated_at
)
SELECT
  @enterprise_user_id,
  'ENTERPRISE',
  '林岚',
  '北辰智联',
  '招聘负责人',
  'REJECTED',
  '营业执照边缘遮挡，且联系人职位说明不够完整，建议重新上传更清晰材料并补充岗位职责说明。',
  @admin_user_id,
  DATE_SUB(@seed_now, INTERVAL 39 DAY),
  NULL,
  0,
  DATE_SUB(@seed_now, INTERVAL 42 DAY),
  DATE_SUB(@seed_now, INTERVAL 42 DAY),
  DATE_SUB(@seed_now, INTERVAL 39 DAY)
WHERE @enterprise_user_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM certification_submissions
    WHERE user_id = @enterprise_user_id
      AND is_current = 0
      AND status = 'REJECTED'
      AND review_note = '营业执照边缘遮挡，且联系人职位说明不够完整，建议重新上传更清晰材料并补充岗位职责说明。'
  );

SET @previous_certification_submission_id = (
  SELECT id
  FROM certification_submissions
  WHERE user_id = @enterprise_user_id
    AND is_current = 0
    AND status = 'REJECTED'
    AND review_note = '营业执照边缘遮挡，且联系人职位说明不够完整，建议重新上传更清晰材料并补充岗位职责说明。'
  ORDER BY id DESC
  LIMIT 1
);

UPDATE certification_submissions
SET
  previous_submission_id = @previous_certification_submission_id,
  review_note = '资料完整，可继续发布任务并处理提交。',
  reviewed_by = COALESCE(reviewed_by, @admin_user_id),
  reviewed_at = COALESCE(reviewed_at, DATE_SUB(@seed_now, INTERVAL 26 DAY)),
  updated_at = DATE_SUB(@seed_now, INTERVAL 26 DAY)
WHERE id = @current_certification_submission_id;

-- 4. 林岚企业任务补强
DROP TEMPORARY TABLE IF EXISTS tmp_linlan_tasks;
CREATE TEMPORARY TABLE tmp_linlan_tasks (
  title VARCHAR(200) NOT NULL PRIMARY KEY,
  description_text TEXT NOT NULL,
  reward_description VARCHAR(255) NOT NULL,
  task_status VARCHAR(20) NOT NULL,
  deadline_at DATETIME NULL,
  closed_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL
);

INSERT INTO tmp_linlan_tasks (
  title, description_text, reward_description, task_status, deadline_at, closed_at, created_at, updated_at
)
VALUES
  (
    '客户线索入库表单体验优化与错误态清单',
    '任务摘要：围绕客户线索入库表单，补一版体验优化与错误态收口方案

【任务背景】
北辰智联正在把销售线索从企业微信、官网表单和活动报名页统一收入口径，但现有录入表单在字段校验、异常提示和重复线索处理上还不够稳定。

【希望解决的问题】
我们希望先筛出能把问题拆清楚、又能把交互细节写成业务团队能直接讨论材料的同学。

【需要提交什么】
请提交问题清单、关键交互建议、错误态文案收口思路，以及你建议优先修复的 3-5 个体验点。

【交付形式】
说明文档 / 设计稿链接

【最看重什么】
是否能把“业务语义 + 表单交互 + 异常提示”串成一个完整闭环，而不是只做视觉层优化。

【适合方向】
前端开发 / 产品经理 / 数据分析

【适合人群】
大三/研二 / 有实习经验

【参考资料】
https://docs.example.com/beichen-wecom-lead-form

【注意事项】
如果你有多份材料，请在开头明确标注最推荐先看的那一份。',
    '任务奖金 580 元 + 下一轮任务优先邀请',
    'OPEN',
    DATE_ADD(DATE(@seed_now), INTERVAL 5 DAY) + INTERVAL 23 HOUR + INTERVAL 59 MINUTE,
    NULL,
    DATE_SUB(@seed_now, INTERVAL 5 DAY),
    DATE_SUB(@seed_now, INTERVAL 2 HOUR)
  ),
  (
    '试用转化日报摘要卡片与埋点核查方案',
    '任务摘要：把试用转化日报做成更适合管理者快速浏览的摘要卡片

【任务背景】
北辰智联客户成功团队每天都会看试用激活、核心行为触达和续费风险摘要，但现有日报更像原始数据拼接，阅读负担偏重。

【希望解决的问题】
我们希望先看到一版能同时兼顾“快速浏览结论”和“补看关键指标”的摘要卡片结构，并补一轮埋点核查建议。

【需要提交什么】
请提交摘要卡片结构说明、重点指标排序、异常提示样式建议，以及你认为最值得核查的埋点字段清单。

【交付形式】
说明文档 / 设计稿链接 / 演示视频/链接

【最看重什么】
是否知道管理者读日报时最先看什么，是否能把结论、风险和下一步动作放到同一套节奏里。

【适合方向】
前端开发 / 数据分析 / 产品经理

【适合人群】
应届生 / 大三/研二

【参考资料】
https://docs.example.com/beichen-trial-daily

【注意事项】
可以保留轻量草图，不要求直接做成高保真。',
    '任务奖金 620 元 + 后续方案沟通机会',
    'OPEN',
    DATE_ADD(DATE(@seed_now), INTERVAL 6 DAY) + INTERVAL 23 HOUR + INTERVAL 59 MINUTE,
    NULL,
    DATE_SUB(@seed_now, INTERVAL 4 DAY),
    DATE_SUB(@seed_now, INTERVAL 5 HOUR)
  ),
  (
    '客户流失风险标签解释页与客服提示语模板',
    '任务摘要：围绕流失风险标签，补一版解释页和客服提示语模板

【任务背景】
北辰智联正在推进客户流失预警模块，但业务同学对不同风险标签的含义、触发条件和跟进动作理解还不够一致。

【希望解决的问题】
我们希望先看一版适合客服和客户成功团队阅读的解释页草稿，以及一套相对克制、可直接拿来试用的提示语模板。

【需要提交什么】
请提交解释页结构、标签分层说明、客服提示语模板，以及你建议先验证的 2-3 个重点风险标签。

【交付形式】
说明文档 / 设计稿链接

【最看重什么】
是否能把抽象规则讲成业务可读语言，是否知道不同角色需要看到的信息深度差异。

【适合方向】
产品经理 / 市场营销 / 数据分析

【适合人群】
应届生 / 有实习经验

【参考资料】
https://docs.example.com/beichen-risk-tags

【注意事项】
不需要扩展成完整后台，只需要解释页和提示语模板这两个交付。',
    '任务奖金 480 元',
    'OPEN',
    DATE_ADD(DATE(@seed_now), INTERVAL 2 DAY) + INTERVAL 23 HOUR + INTERVAL 59 MINUTE,
    NULL,
    DATE_SUB(@seed_now, INTERVAL 3 DAY),
    DATE_SUB(@seed_now, INTERVAL 9 HOUR)
  ),
  (
    '校招简历初筛规则看板与运营备注区',
    '任务摘要：整理校招简历初筛规则，并补一版运营备注区和看板结构

【任务背景】
北辰智联近期在做校招简历初筛流程优化，希望把“规则解释、人工备注和复核路径”放到一套更容易对齐的页面里。

【希望解决的问题】
我们希望先看一版兼顾看板概览、规则解释和运营备注留痕的结构化方案，用来验证后续是否继续产品化。

【需要提交什么】
请提交页面结构说明、备注区字段建议、规则看板重点模块，以及你认为最值得优先展示的筛选依据。

【交付形式】
说明文档 / 设计稿链接 / 演示视频/链接

【最看重什么】
是否能把规则解释、人工判断和后续复核串起来，且页面节奏清楚不过载。

【适合方向】
产品经理 / 前端开发 / 数据分析

【适合人群】
大三/研二 / 有实习经验

【参考资料】
https://docs.example.com/beichen-resume-board

【注意事项】
该任务保留为已完成筛选样本，用于演示结果留痕和继续接触链路。',
    '优先沟通机会 + 任务奖金 760 元',
    'CLOSED',
    DATE_SUB(DATE(@seed_now), INTERVAL 1 DAY) + INTERVAL 23 HOUR + INTERVAL 59 MINUTE,
    DATE_SUB(@seed_now, INTERVAL 28 HOUR),
    DATE_SUB(@seed_now, INTERVAL 8 DAY),
    DATE_SUB(@seed_now, INTERVAL 28 HOUR)
  );

INSERT INTO bounty_tasks (
  enterprise_user_id, title, description, reward_description, status,
  accepted_submission_id, deadline_at, closed_at, created_at, updated_at
)
SELECT
  @enterprise_user_id,
  source.title,
  source.description_text,
  source.reward_description,
  source.task_status,
  NULL,
  source.deadline_at,
  source.closed_at,
  source.created_at,
  source.updated_at
FROM tmp_linlan_tasks source
WHERE NOT EXISTS (
  SELECT 1
  FROM bounty_tasks target
  WHERE target.enterprise_user_id = @enterprise_user_id
    AND target.title = source.title
);

UPDATE bounty_tasks target
JOIN tmp_linlan_tasks source ON source.title = target.title
SET
  target.description = source.description_text,
  target.reward_description = source.reward_description,
  target.status = source.task_status,
  target.deadline_at = source.deadline_at,
  target.closed_at = source.closed_at,
  target.updated_at = source.updated_at
WHERE target.enterprise_user_id = @enterprise_user_id;

SET @task_lead_form_id = (
  SELECT id FROM bounty_tasks
  WHERE enterprise_user_id = @enterprise_user_id
    AND title = '客户线索入库表单体验优化与错误态清单'
  LIMIT 1
);
SET @task_trial_daily_id = (
  SELECT id FROM bounty_tasks
  WHERE enterprise_user_id = @enterprise_user_id
    AND title = '试用转化日报摘要卡片与埋点核查方案'
  LIMIT 1
);
SET @task_risk_tags_id = (
  SELECT id FROM bounty_tasks
  WHERE enterprise_user_id = @enterprise_user_id
    AND title = '客户流失风险标签解释页与客服提示语模板'
  LIMIT 1
);
SET @task_resume_board_id = (
  SELECT id FROM bounty_tasks
  WHERE enterprise_user_id = @enterprise_user_id
    AND title = '校招简历初筛规则看板与运营备注区'
  LIMIT 1
);

-- 5. 新任务对应提交补强
DROP TEMPORARY TABLE IF EXISTS tmp_linlan_submissions;
CREATE TEMPORARY TABLE tmp_linlan_submissions (
  task_title VARCHAR(200) NOT NULL,
  student_email VARCHAR(255) NOT NULL,
  content_text TEXT NOT NULL,
  attachment_links VARCHAR(1000) NULL,
  submission_status VARCHAR(20) NOT NULL,
  review_comment VARCHAR(500) NULL,
  internal_note TEXT NULL,
  created_at DATETIME NOT NULL,
  reviewed_at DATETIME NULL
);

INSERT INTO tmp_linlan_submissions (
  task_title, student_email, content_text, attachment_links, submission_status,
  review_comment, internal_note, created_at, reviewed_at
)
VALUES
  (
    '客户线索入库表单体验优化与错误态清单',
    'student.xuanran@bishe.local',
    '我补了一版客户线索入库表单的错误态收口清单，并按「字段校验 -> 重复线索 -> 权限限制 -> 异常兜底」四段来组织页面阅读顺序。建议企业优先看第一页的关键问题列表和 Figma 里标红的错误态对照。',
    'https://figma.example.com/submission/linlan-001,https://docs.example.com/submission/linlan-001',
    'SUBMITTED',
    NULL,
    '页面层级和表单节奏都比较稳，后续重点看重复线索和无权限场景的解释是否还可以更简洁。',
    DATE_SUB(@seed_now, INTERVAL 11 HOUR),
    NULL
  ),
  (
    '客户线索入库表单体验优化与错误态清单',
    'student.zhoumuyang@bishe.local',
    '我将这份方案拆成“录入前提醒、录入中反馈、录入后确认”三段，并补了一张异常路径总览图。重点想证明哪些错误态需要保留上下文，哪些可以直接给出下一步动作。',
    'https://docs.example.com/submission/linlan-002',
    'REVIEWING',
    NULL,
    '结构清楚，问题分类也稳，正在继续比对错误态文案和业务动作指向是否足够直接。',
    DATE_SUB(@seed_now, INTERVAL 18 HOUR),
    NULL
  ),
  (
    '试用转化日报摘要卡片与埋点核查方案',
    'student.liujianing@bishe.local',
    '我先把日报拆成“当日概览、异常提醒、跟进建议、埋点核查”四块，并补了一版字段命名和接口输出顺序建议。虽然偏后端视角，但希望能帮助后续前端卡片落地时减少信息对不齐的问题。',
    'https://docs.example.com/submission/linlan-003',
    'SUBMITTED',
    NULL,
    '字段和埋点说明很细，后续重点看摘要卡片的阅读顺序是否还能更贴管理者视角。',
    DATE_SUB(@seed_now, INTERVAL 8 HOUR),
    NULL
  ),
  (
    '试用转化日报摘要卡片与埋点核查方案',
    'student.heqingyan@bishe.local',
    '我补了一版试用转化日报卡片的核心模块排序，并把“激活异常、关键行为缺失、续费风险”做成三级摘要区。说明文档里额外补了埋点核查优先级和异常归因路径。',
    'https://docs.example.com/submission/linlan-004,https://figma.example.com/submission/linlan-004',
    'REVIEWING',
    NULL,
    '方案兼顾了指标和动作，正在进一步核对摘要区信息量和异常归因路径的可读性。',
    DATE_SUB(@seed_now, INTERVAL 15 HOUR),
    NULL
  ),
  (
    '客户流失风险标签解释页与客服提示语模板',
    'student.zhoumuyang@bishe.local',
    '这版我把风险标签解释页分成“标签含义、触发样例、建议动作、客服话术”四层，并按客服一线最常问的问题先排序。提示语模板控制在可直接复制的短句范围内，避免太像内部规则说明。',
    'https://docs.example.com/submission/linlan-005',
    'SUBMITTED',
    NULL,
    '标签解释和客服语言比较贴业务语境，后续重点看样例区是否还可以更聚焦。',
    DATE_SUB(@seed_now, INTERVAL 22 HOUR),
    NULL
  ),
  (
    '校招简历初筛规则看板与运营备注区',
    'student.xuanran@bishe.local',
    '我把校招初筛看板做成了“规则概览 + 人工备注 + 复核提醒”三段，并在备注区里补了一版状态切换和协作说明。重点希望让运营同学能快速知道为什么被标记、接下来要看哪一项。',
    'https://figma.example.com/submission/linlan-006,https://docs.example.com/submission/linlan-006',
    'ACCEPTED',
    '继续接触意向：邀请面试\n补充说明：看板结构和运营备注区衔接得很自然，规则解释也足够克制，适合继续聊细节取舍。',
    '页面结构和备注区说明很完整，适合继续深入讨论字段粒度和协作提醒。',
    DATE_SUB(@seed_now, INTERVAL 7 DAY),
    DATE_SUB(@seed_now, INTERVAL 28 HOUR)
  ),
  (
    '校招简历初筛规则看板与运营备注区',
    'student.liujianing@bishe.local',
    '我更偏接口和字段组织视角，补了一版规则看板需要的状态字段、备注字段以及复核链路说明。页面草图部分相对简单，主要想先把数据结构和操作逻辑讲清楚。',
    'https://docs.example.com/submission/linlan-007',
    'REJECTED',
    '未入选原因：当前提交完成度还可以继续加强\n补充说明：规则和字段拆解比较清楚，但页面承载、备注节奏和运营阅读顺序还可以再展开。',
    '更偏后端和字段层设计，页面承载和运营阅读节奏还不够完整。',
    DATE_SUB(@seed_now, INTERVAL 7 DAY) + INTERVAL 2 HOUR,
    DATE_SUB(@seed_now, INTERVAL 30 HOUR)
  );

INSERT INTO bounty_submissions (
  task_id, student_user_id, content_text, attachment_links, status,
  review_comment, reviewed_at, reviewer_user_id, created_at, updated_at, internal_note
)
SELECT
  task.id,
  student.id,
  source.content_text,
  source.attachment_links,
  source.submission_status,
  source.review_comment,
  source.reviewed_at,
  CASE
    WHEN source.submission_status IN ('ACCEPTED', 'REJECTED', 'REVIEWING') THEN @enterprise_user_id
    ELSE NULL
  END,
  source.created_at,
  COALESCE(source.reviewed_at, source.created_at),
  source.internal_note
FROM tmp_linlan_submissions source
JOIN bounty_tasks task
  ON task.enterprise_user_id = @enterprise_user_id
 AND task.title = source.task_title
JOIN users student
  ON student.email = source.student_email
WHERE NOT EXISTS (
  SELECT 1
  FROM bounty_submissions target
  WHERE target.task_id = task.id
    AND target.student_user_id = student.id
    AND target.content_text = source.content_text
);

UPDATE bounty_submissions target
JOIN bounty_tasks task
  ON task.id = target.task_id
JOIN users student
  ON student.id = target.student_user_id
JOIN tmp_linlan_submissions source
  ON source.task_title = task.title
 AND source.student_email = student.email
 AND source.content_text = target.content_text
SET
  target.attachment_links = source.attachment_links,
  target.status = source.submission_status,
  target.review_comment = source.review_comment,
  target.reviewed_at = source.reviewed_at,
  target.reviewer_user_id = CASE
    WHEN source.submission_status IN ('ACCEPTED', 'REJECTED', 'REVIEWING') THEN @enterprise_user_id
    ELSE NULL
  END,
  target.internal_note = source.internal_note,
  target.updated_at = COALESCE(source.reviewed_at, source.created_at)
WHERE task.enterprise_user_id = @enterprise_user_id;

SET @submission_lead_form_xuanran = (
  SELECT bs.id
  FROM bounty_submissions bs
  JOIN users u ON u.id = bs.student_user_id
  WHERE bs.task_id = @task_lead_form_id
    AND u.email = 'student.xuanran@bishe.local'
  ORDER BY bs.id DESC
  LIMIT 1
);
SET @submission_trial_heqingyan = (
  SELECT bs.id
  FROM bounty_submissions bs
  JOIN users u ON u.id = bs.student_user_id
  WHERE bs.task_id = @task_trial_daily_id
    AND u.email = 'student.heqingyan@bishe.local'
  ORDER BY bs.id DESC
  LIMIT 1
);
SET @submission_risk_zhoumuyang = (
  SELECT bs.id
  FROM bounty_submissions bs
  JOIN users u ON u.id = bs.student_user_id
  WHERE bs.task_id = @task_risk_tags_id
    AND u.email = 'student.zhoumuyang@bishe.local'
  ORDER BY bs.id DESC
  LIMIT 1
);
SET @submission_resume_board_xuanran = (
  SELECT bs.id
  FROM bounty_submissions bs
  JOIN users u ON u.id = bs.student_user_id
  WHERE bs.task_id = @task_resume_board_id
    AND u.email = 'student.xuanran@bishe.local'
  ORDER BY bs.id DESC
  LIMIT 1
);

UPDATE bounty_tasks
SET
  accepted_submission_id = NULL,
  status = 'OPEN',
  closed_at = NULL
WHERE id IN (@task_lead_form_id, @task_trial_daily_id, @task_risk_tags_id);

UPDATE bounty_tasks
SET
  accepted_submission_id = @submission_resume_board_xuanran,
  status = 'CLOSED',
  closed_at = DATE_SUB(@seed_now, INTERVAL 28 HOUR),
  updated_at = DATE_SUB(@seed_now, INTERVAL 28 HOUR)
WHERE id = @task_resume_board_id;

-- 6. 企业通知中心数据补强
DROP TEMPORARY TABLE IF EXISTS tmp_linlan_notifications;
CREATE TEMPORARY TABLE tmp_linlan_notifications (
  event_id VARCHAR(64) NOT NULL PRIMARY KEY,
  notification_type VARCHAR(50) NOT NULL,
  notification_category VARCHAR(32) NOT NULL,
  title VARCHAR(160) NOT NULL,
  content TEXT NOT NULL,
  ref_type VARCHAR(60) NULL,
  ref_id VARCHAR(64) NULL,
  action_code VARCHAR(80) NULL,
  priority VARCHAR(20) NOT NULL,
  payload_json LONGTEXT NULL,
  is_read TINYINT(1) NOT NULL,
  created_at DATETIME NOT NULL,
  read_at DATETIME NULL
);

INSERT INTO tmp_linlan_notifications (
  event_id, notification_type, notification_category, title, content,
  ref_type, ref_id, action_code, priority, payload_json, is_read, created_at, read_at
)
VALUES
  (
    'demo-linlan-cert-resubmit',
    'CERTIFICATION_RESUBMIT_REQUIRED',
    'CERTIFICATION',
    '认证资料需要重新提交',
    '营业执照边缘遮挡，且联系人职位说明不够完整。平台已保留审核备注，建议重新上传更清晰材料。',
    'CERTIFICATION',
    CAST(@enterprise_user_id AS CHAR),
    'VIEW_CERTIFICATION_STATUS',
    'HIGH',
    JSON_OBJECT('userId', CAST(@enterprise_user_id AS CHAR), 'companyName', '北辰智联'),
    1,
    DATE_SUB(@seed_now, INTERVAL 39 DAY),
    DATE_SUB(@seed_now, INTERVAL 38 DAY)
  ),
  (
    'demo-linlan-cert-approved',
    'CERTIFICATION_APPROVED',
    'CERTIFICATION',
    '认证审核已通过',
    '北辰智联的认证资料已经审核通过，当前可以继续发布企业任务并处理学生提交。',
    'CERTIFICATION',
    CAST(@enterprise_user_id AS CHAR),
    'VIEW_CERTIFICATION_STATUS',
    'HIGH',
    JSON_OBJECT('userId', CAST(@enterprise_user_id AS CHAR), 'companyName', '北辰智联'),
    1,
    DATE_SUB(@seed_now, INTERVAL 26 DAY),
    DATE_SUB(@seed_now, INTERVAL 26 DAY) + INTERVAL 3 HOUR
  ),
  (
    'demo-linlan-bounty-submitted-lead-form',
    'BOUNTY_SUBMITTED',
    'BOUNTY',
    '悬赏任务有新的成果提交',
    '安然向「客户线索入库表单体验优化与错误态清单」提交了新的方案，并附带高保真链接与说明文档。',
    'BOUNTY_TASK',
    CAST(@task_lead_form_id AS CHAR),
    'VIEW_BOUNTY_TASK',
    'HIGH',
    JSON_OBJECT('taskId', CAST(@task_lead_form_id AS CHAR), 'submissionId', @submission_lead_form_xuanran, 'studentName', '安然'),
    0,
    DATE_SUB(@seed_now, INTERVAL 10 HOUR),
    NULL
  ),
  (
    'demo-linlan-bounty-submitted-trial-daily',
    'BOUNTY_SUBMITTED',
    'BOUNTY',
    '悬赏任务有新的成果提交',
    '何清妍向「试用转化日报摘要卡片与埋点核查方案」提交了方案，并额外补充了埋点核查优先级。',
    'BOUNTY_TASK',
    CAST(@task_trial_daily_id AS CHAR),
    'VIEW_BOUNTY_TASK',
    'HIGH',
    JSON_OBJECT('taskId', CAST(@task_trial_daily_id AS CHAR), 'submissionId', @submission_trial_heqingyan, 'studentName', '何清妍'),
    0,
    DATE_SUB(@seed_now, INTERVAL 7 HOUR),
    NULL
  ),
  (
    'demo-linlan-bounty-submitted-risk-tags',
    'BOUNTY_SUBMITTED',
    'BOUNTY',
    '悬赏任务有新的成果提交',
    '周沐阳向「客户流失风险标签解释页与客服提示语模板」提交了新的结构化方案。',
    'BOUNTY_TASK',
    CAST(@task_risk_tags_id AS CHAR),
    'VIEW_BOUNTY_TASK',
    'HIGH',
    JSON_OBJECT('taskId', CAST(@task_risk_tags_id AS CHAR), 'submissionId', @submission_risk_zhoumuyang, 'studentName', '周沐阳'),
    0,
    DATE_SUB(@seed_now, INTERVAL 6 HOUR),
    NULL
  ),
  (
    'demo-linlan-bounty-submitted-existing',
    'BOUNTY_SUBMITTED',
    'BOUNTY',
    '悬赏任务有新的成果提交',
    '何清妍向「客户成功周报自动汇总模板与可视化草稿」补交了一版更完整的摘要区说明。',
    'BOUNTY_TASK',
    '1208',
    'VIEW_BOUNTY_TASK',
    'HIGH',
    JSON_OBJECT('taskId', '1208', 'submissionId', 1222, 'studentName', '何清妍'),
    1,
    DATE_SUB(@seed_now, INTERVAL 5 DAY),
    DATE_SUB(@seed_now, INTERVAL 5 DAY) + INTERVAL 2 HOUR
  ),
  (
    'demo-linlan-system-announcement',
    'SYSTEM_ANNOUNCEMENT',
    'SYSTEM',
    '平台公告',
    '企业工作台已补齐任务分页、结果留痕和后台静默刷新，可继续按真实通知链路回看最新提交。',
    'NOTIFICATION_CENTER',
    NULL,
    'VIEW_NOTIFICATION_CENTER',
    'NORMAL',
    JSON_OBJECT('source', 'enterprise_demo_sync', 'target', 'notifications'),
    0,
    DATE_SUB(@seed_now, INTERVAL 4 HOUR),
    NULL
  );

INSERT INTO notifications (
  user_id, type, category, title, content, ref_type, ref_id,
  action_code, priority, event_id, payload_json, is_read, created_at, read_at, archived_at, updated_at
)
SELECT
  @enterprise_user_id,
  source.notification_type,
  source.notification_category,
  source.title,
  source.content,
  source.ref_type,
  source.ref_id,
  source.action_code,
  source.priority,
  source.event_id,
  source.payload_json,
  source.is_read,
  source.created_at,
  source.read_at,
  NULL,
  COALESCE(source.read_at, source.created_at)
FROM tmp_linlan_notifications source
WHERE NOT EXISTS (
  SELECT 1
  FROM notifications target
  WHERE target.user_id = @enterprise_user_id
    AND target.event_id = source.event_id
);

UPDATE notifications target
JOIN tmp_linlan_notifications source ON source.event_id = target.event_id
SET
  target.type = source.notification_type,
  target.category = source.notification_category,
  target.title = source.title,
  target.content = source.content,
  target.ref_type = source.ref_type,
  target.ref_id = source.ref_id,
  target.action_code = source.action_code,
  target.priority = source.priority,
  target.payload_json = source.payload_json,
  target.is_read = source.is_read,
  target.created_at = source.created_at,
  target.read_at = source.read_at,
  target.updated_at = COALESCE(source.read_at, source.created_at)
WHERE target.user_id = @enterprise_user_id;

COMMIT;
