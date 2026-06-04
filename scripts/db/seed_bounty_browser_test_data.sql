-- 企业实战 / 学生悬赏大厅宽屏浏览测试数据补充脚本
-- 目标：为 `/bounty`、`/bounty/:taskId`、`/enterprise/tasks`、`/enterprise/tasks/:taskId`
--       补齐真实合理且可重复执行的测试样本。
-- 特性：
-- 1. 优先复用现有两个企业测试账号与现有学生测试账号，不制造“新账号孤岛数据”
-- 2. 按“企业邮箱 + 任务标题”幂等写入任务，按“任务 + 学生”幂等写入提交
-- 3. 共补 12 条企业实战任务，覆盖 OPEN / CLOSED、即将截止、已完成筛选、已结束、
--    待处理 / 处理中 / 继续接触 / 未入选四类提交状态

SET NAMES utf8mb4;

SET @seed_now = NOW();
SET @seed_password_hash = COALESCE(
  (SELECT password_hash FROM users WHERE role = 'ENTERPRISE' ORDER BY id LIMIT 1),
  (SELECT password_hash FROM users WHERE role = 'STUDENT' ORDER BY id LIMIT 1),
  (SELECT password_hash FROM users ORDER BY id LIMIT 1)
);
SET @seed_admin_user_id = (SELECT id FROM users WHERE role = 'ADMIN' ORDER BY id LIMIT 1);

START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS tmp_bounty_seed_enterprises;
CREATE TEMPORARY TABLE tmp_bounty_seed_enterprises (
  email          VARCHAR(255) NOT NULL,
  display_name   VARCHAR(100) NOT NULL,
  real_name      VARCHAR(100) NOT NULL,
  company_name   VARCHAR(200) NOT NULL,
  job_title      VARCHAR(100) NOT NULL,
  industry       VARCHAR(100) NOT NULL,
  company_size   VARCHAR(50)  NOT NULL,
  hiring_tags    VARCHAR(512) NOT NULL,
  bio            TEXT         NOT NULL,
  external_links TEXT         NULL,
  preferences    TEXT         NULL,
  review_note    VARCHAR(1000) NULL
);

INSERT INTO tmp_bounty_seed_enterprises (
  email, display_name, real_name, company_name, job_title, industry, company_size,
  hiring_tags, bio, external_links, preferences, review_note
)
VALUES
  (
    'enterprise.beichenhr@bishe.local',
    '北辰智联 HR',
    '林岚',
    '北辰智联',
    '招聘负责人',
    '企业服务 / SaaS',
    '50-200 人',
    '前端产品化,增长分析,B端交付',
    '北辰智联正在把校招活动页、客户运营后台和知识库工具逐步产品化，当前更希望找到能把业务问题说清楚、并愿意把方案落到页面和数据验证上的学生。',
    'https://talent.beichen-demo.local',
    '更偏好能清楚表达拆解逻辑、主动说明验证路径、并能把方案写成可交付材料的同学。',
    '资料完整，可继续发布任务并处理提交。'
  ),
  (
    'enterprise.yunqiaodata@bishe.local',
    '云桥数据招聘',
    '周越',
    '云桥数据',
    '人才合作经理',
    '数据智能 / AI 应用',
    '20-50 人',
    '数据产品,BI分析,AI应用',
    '云桥数据当前重点补齐数据产品、分析看板和 AI 应用评估场景，希望通过轻量企业任务先筛出表达稳、交付清楚、能快速推进细节的学生。',
    'https://jobs.yunqiao-demo.local',
    '提交时优先说明你的判断过程、指标口径和交付边界；如果有多份材料，请标出最推荐先看的那一份。',
    '资料齐全，认证状态正常，可继续使用企业任务闭环。'
  );

INSERT INTO users (email, password_hash, role, tier, status, display_name, real_name, last_login_at, is_deleted, created_at, updated_at)
SELECT
  e.email,
  @seed_password_hash,
  'ENTERPRISE',
  'FREE',
  'ACTIVE',
  e.display_name,
  e.real_name,
  DATE_SUB(@seed_now, INTERVAL 6 HOUR),
  0,
  DATE_SUB(@seed_now, INTERVAL 25 DAY),
  DATE_SUB(@seed_now, INTERVAL 6 HOUR)
FROM tmp_bounty_seed_enterprises e
LEFT JOIN users u ON u.email = e.email
WHERE u.id IS NULL;

UPDATE users u
JOIN tmp_bounty_seed_enterprises e ON e.email = u.email
SET
  u.real_name = e.real_name,
  u.updated_at = DATE_SUB(@seed_now, INTERVAL 5 HOUR)
WHERE u.role = 'ENTERPRISE' AND u.is_deleted = 0;

INSERT INTO enterprise_profiles (
  user_id, company_name, industry, company_size, hiring_tags, contact_title,
  bio, external_links, preferences, approval_status, created_at, updated_at
)
SELECT
  u.id,
  e.company_name,
  e.industry,
  e.company_size,
  e.hiring_tags,
  e.job_title,
  e.bio,
  e.external_links,
  e.preferences,
  'APPROVED',
  DATE_SUB(@seed_now, INTERVAL 25 DAY),
  DATE_SUB(@seed_now, INTERVAL 5 HOUR)
FROM tmp_bounty_seed_enterprises e
JOIN users u ON u.email = e.email
ON DUPLICATE KEY UPDATE
  company_name = VALUES(company_name),
  industry = VALUES(industry),
  company_size = VALUES(company_size),
  hiring_tags = VALUES(hiring_tags),
  contact_title = VALUES(contact_title),
  bio = VALUES(bio),
  external_links = VALUES(external_links),
  preferences = VALUES(preferences),
  approval_status = VALUES(approval_status),
  updated_at = VALUES(updated_at);

INSERT INTO certification_submissions (
  user_id, user_role, real_name, company_name, job_title,
  status, review_note, reviewed_by, reviewed_at, previous_submission_id,
  is_current, submitted_at, created_at, updated_at
)
SELECT
  u.id,
  'ENTERPRISE',
  e.real_name,
  e.company_name,
  e.job_title,
  'APPROVED',
  e.review_note,
  @seed_admin_user_id,
  DATE_SUB(@seed_now, INTERVAL 20 DAY),
  NULL,
  1,
  DATE_SUB(@seed_now, INTERVAL 24 DAY),
  DATE_SUB(@seed_now, INTERVAL 24 DAY),
  DATE_SUB(@seed_now, INTERVAL 20 DAY)
FROM tmp_bounty_seed_enterprises e
JOIN users u ON u.email = e.email
WHERE NOT EXISTS (
  SELECT 1
  FROM certification_submissions cs
  WHERE cs.user_id = u.id
    AND cs.is_current = 1
);

DROP TEMPORARY TABLE IF EXISTS tmp_bounty_seed_students;
CREATE TEMPORARY TABLE tmp_bounty_seed_students (
  email            VARCHAR(255) NOT NULL,
  display_name     VARCHAR(100) NOT NULL,
  real_name        VARCHAR(100) NOT NULL,
  school_name      VARCHAR(150) NOT NULL,
  school_name_key  VARCHAR(160) NOT NULL,
  major            VARCHAR(100) NOT NULL,
  grade            VARCHAR(20)  NOT NULL,
  target_position  VARCHAR(100) NOT NULL,
  skill_tags       VARCHAR(512) NOT NULL,
  self_intro       TEXT         NOT NULL,
  github           VARCHAR(255) NULL,
  portfolio        VARCHAR(255) NULL
);

INSERT INTO tmp_bounty_seed_students (
  email, display_name, real_name, school_name, school_name_key, major, grade,
  target_position, skill_tags, self_intro, github, portfolio
)
VALUES
  (
    'student.liujianing@bishe.local',
    '刘嘉宁',
    '刘嘉宁',
    '华东理工大学',
    'huadongligongdaxue',
    '软件工程',
    '大四',
    'Java 后端开发',
    'Java,Spring Boot,MySQL,Redis',
    '希望把课程与实习项目讲成更像真实业务系统的后端同学，擅长把接口、缓存和数据库链路拆开表达。',
    'https://github.com/liujianing-dev',
    'https://portfolio.example.com/liujianing'
  ),
  (
    'student.xuanran@bishe.local',
    '安然',
    '安然',
    '南京航空航天大学',
    'nanjinghangkonghangtiandaxue',
    '计算机科学与技术',
    '大四',
    '前端开发实习',
    'React,TypeScript,交互设计,前端工程化',
    '更关注复杂交互、页面信息架构和前端工程化表达，希望找到能把方案讲清楚的真实任务练手。',
    'https://github.com/anran-fe',
    'https://portfolio.example.com/anran'
  ),
  (
    'student.zhoumuyang@bishe.local',
    '周沐阳',
    '周沐阳',
    '同济大学',
    'tongjidaxue',
    '信息管理与信息系统',
    '研一',
    '数据产品培训生',
    'SQL,数据分析,BI,用户研究',
    '偏数据产品和分析方向，擅长把指标口径、业务问题和页面信息组织到同一套表达里。',
    'https://github.com/zhoumuyang-data',
    'https://portfolio.example.com/zhoumuyang'
  ),
  (
    'student.heqingyan@bishe.local',
    '何清妍',
    '何清妍',
    '西安电子科技大学',
    'xidiandaxue',
    '软件工程',
    '大四',
    '后端开发工程师',
    'Java,微服务,数据库设计,系统设计',
    '擅长服务端方案拆解、接口联调和异常排查，希望通过真实任务继续强化系统设计表达。',
    'https://github.com/heqingyan-backend',
    'https://portfolio.example.com/heqingyan'
  );

INSERT INTO users (email, password_hash, role, tier, status, display_name, real_name, last_login_at, is_deleted, created_at, updated_at)
SELECT
  s.email,
  @seed_password_hash,
  'STUDENT',
  'FREE',
  'ACTIVE',
  s.display_name,
  s.real_name,
  DATE_SUB(@seed_now, INTERVAL 8 HOUR),
  0,
  DATE_SUB(@seed_now, INTERVAL 40 DAY),
  DATE_SUB(@seed_now, INTERVAL 8 HOUR)
FROM tmp_bounty_seed_students s
LEFT JOIN users u ON u.email = s.email
WHERE u.id IS NULL;

INSERT INTO student_profiles (
  user_id, school_name, school_name_key, major, grade, target_position,
  skill_tags, self_intro, github, portfolio, created_at, updated_at
)
SELECT
  u.id,
  s.school_name,
  s.school_name_key,
  s.major,
  s.grade,
  s.target_position,
  s.skill_tags,
  s.self_intro,
  s.github,
  s.portfolio,
  DATE_SUB(@seed_now, INTERVAL 40 DAY),
  DATE_SUB(@seed_now, INTERVAL 8 HOUR)
FROM tmp_bounty_seed_students s
JOIN users u ON u.email = s.email
LEFT JOIN student_profiles sp ON sp.user_id = u.id
WHERE sp.id IS NULL;

INSERT INTO student_portrait_snapshots (student_user_id, portrait_tags, evidence, updated_at)
SELECT
  u.id,
  CASE s.email
    WHEN 'student.liujianing@bishe.local' THEN '[{"code":"backend_project","label":"后端工程潜力","source":"PROFILE","confidence":0.92},{"code":"system_design","label":"系统设计潜力","source":"RULE","confidence":0.84}]'
    WHEN 'student.xuanran@bishe.local' THEN '[{"code":"frontend_product","label":"前端产品化表达","source":"PROFILE","confidence":0.9},{"code":"community_active","label":"社区互动积极","source":"COMMUNITY","confidence":0.82}]'
    WHEN 'student.zhoumuyang@bishe.local' THEN '[{"code":"data_product","label":"数据产品导向","source":"PROFILE","confidence":0.88},{"code":"analysis_structured","label":"分析表达结构化","source":"RULE","confidence":0.79}]'
    ELSE '[{"code":"backend_reasoning","label":"系统拆解意识强","source":"PROFILE","confidence":0.87},{"code":"delivery_stable","label":"交付说明完整","source":"RULE","confidence":0.8}]'
  END,
  CASE s.email
    WHEN 'student.liujianing@bishe.local' THEN '[{"type":"skill","label":"Java, Spring Boot, Redis"},{"type":"target","label":"Java 后端开发"}]'
    WHEN 'student.xuanran@bishe.local' THEN '[{"type":"skill","label":"React, TypeScript, 工程化"},{"type":"community","label":"近期社区互动活跃"}]'
    WHEN 'student.zhoumuyang@bishe.local' THEN '[{"type":"skill","label":"SQL, BI, 数据分析"},{"type":"target","label":"数据产品培训生"}]'
    ELSE '[{"type":"skill","label":"数据库设计, 微服务"},{"type":"target","label":"后端开发工程师"}]'
  END,
  DATE_SUB(@seed_now, INTERVAL 12 HOUR)
FROM tmp_bounty_seed_students s
JOIN users u ON u.email = s.email
LEFT JOIN student_portrait_snapshots snapshot ON snapshot.student_user_id = u.id
WHERE snapshot.id IS NULL;

DROP TEMPORARY TABLE IF EXISTS tmp_bounty_seed_tasks;
CREATE TEMPORARY TABLE tmp_bounty_seed_tasks (
  seed_key               VARCHAR(32)  NOT NULL PRIMARY KEY,
  enterprise_email       VARCHAR(255) NOT NULL,
  title                  VARCHAR(200) NOT NULL,
  summary                VARCHAR(255) NOT NULL,
  background             TEXT         NOT NULL,
  problem                TEXT         NOT NULL,
  delivery_what          TEXT         NOT NULL,
  delivery_formats       VARCHAR(255) NOT NULL,
  value_most             TEXT         NOT NULL,
  reward_description     VARCHAR(255) NOT NULL,
  status                 VARCHAR(20)  NOT NULL,
  accepted_student_email VARCHAR(255) NULL,
  deadline_day_offset    INT          NULL,
  created_hours_ago      INT          NOT NULL,
  updated_hours_ago      INT          NOT NULL,
  closed_hours_ago       INT          NULL,
  direction_tags         VARCHAR(255) NOT NULL,
  audience_tags          VARCHAR(255) NOT NULL,
  reference_link         VARCHAR(255) NULL,
  notes                  TEXT         NOT NULL
);

INSERT INTO tmp_bounty_seed_tasks (
  seed_key, enterprise_email, title, summary, background, problem,
  delivery_what, delivery_formats, value_most, reward_description, status,
  accepted_student_email, deadline_day_offset, created_hours_ago, updated_hours_ago, closed_hours_ago,
  direction_tags, audience_tags, reference_link, notes
)
VALUES
  (
    'BT001',
    'enterprise.beichenhr@bishe.local',
    '校招活动报名漏斗复盘与前端改版方案',
    '围绕报名页转化路径给出一版可落地的前端改版方案',
    '北辰智联正在准备新一轮校招专题活动页，现有报名入口来自社群、内容号和官网落地页，但用户进入后容易在介绍区和报名表单之间来回跳出。',
    '我们希望先用一轮轻量实战任务，筛出能把问题拆清楚、又能把方案落到页面结构与埋点上的学生。',
    '提交一份改版方案说明，最好同时给出关键页面草图或高保真，以及报名漏斗的核心埋点建议。',
    '说明文档 / 设计稿链接 / 演示视频/链接',
    '问题拆解是否清楚、信息层级是否合理、是否能提出可验证的转化假设。',
    '终面优先沟通机会 + 任务奖金 600 元',
    'OPEN',
    NULL,
    3,
    240,
    18,
    NULL,
    '前端开发 / 数据分析 / 产品经理',
    '大三/研二 / 有实习经验',
    'https://figma.example.com/beichen-campus-funnel',
    '平台内只承接方案提交与结果留痕；如果方案合适，会通过企业任务工作区继续发送后续接触通知。'
  ),
  (
    'BT002',
    'enterprise.beichenhr@bishe.local',
    '企业知识库智能检索评估看板',
    '补一版能解释召回效果和问题样本的评估看板草稿',
    '北辰智联近期把内部知识库接入了简单的语义检索能力，但产品和业务同学很难直观看到“哪些问题搜得到、哪些问题搜不到”。',
    '我们希望有同学先把评估信息结构、样本展示方式和问题分类面板梳理成一版能讨论的看板方案。',
    '提交信息架构说明、核心面板草图，以及你认为最该优先展示的评估指标说明。',
    '说明文档 / 设计稿链接 / 演示视频/链接',
    '指标解释是否对业务同学友好，样本区是否能帮助快速复盘问题，页面结构是否利于后续扩展。',
    '后续交流机会 + 任务奖金 800 元',
    'OPEN',
    NULL,
    8,
    216,
    22,
    NULL,
    '前端开发 / AI算法 / 数据分析',
    '应届生 / 有实习经验',
    'https://docs.example.com/beichen-rag-eval',
    '如果你会额外给出问题分类口径或标注规则，也欢迎一并补充。'
  ),
  (
    'BT003',
    'enterprise.beichenhr@bishe.local',
    'B 端线索分层策略说明页与埋点方案',
    '把线索分层规则讲清楚，并补一版说明页和关键埋点思路',
    '北辰智联当前在做一轮线索运营工具升级，内部已经有基础的分层规则，但销售和运营同学对不同层级的定义、触发条件和后续动作理解不一致。',
    '我们希望找到能把“规则解释 + 页面承载 + 埋点验证”串起来的学生，先输出一版说明页和埋点方案。',
    '提交页面结构说明、规则解释文案、关键埋点字段建议，若有页面草图更好。',
    '说明文档 / 设计稿链接',
    '是否能把抽象规则翻译成业务可读语言，是否知道如何验证页面上线后的使用效果。',
    '进一步交流机会 + 任务奖金 700 元',
    'CLOSED',
    'student.zhoumuyang@bishe.local',
    -6,
    300,
    120,
    110,
    '产品经理 / 数据分析 / 市场营销',
    '应届生 / 大三/研二',
    'https://docs.example.com/beichen-lead-tiering',
    '该任务已经完成本轮筛选，保留用于学生大厅和企业审核工作区回看完整结果留痕。'
  ),
  (
    'BT004',
    'enterprise.beichenhr@bishe.local',
    '供应链库存预警规则配置台体验优化',
    '梳理库存预警规则配置台的交互和异常提示策略',
    '北辰智联服务的客户里有一批做供应链协同的团队，当前预警规则配置台已经能用，但策略说明、阈值输入和异常提示都偏工程语义。',
    '我们想先看一版更适合业务同学理解和操作的配置体验优化建议，而不是直接重做整套系统。',
    '提交体验问题清单、关键页面交互建议，以及你认为最应该优先收口的提示文案。',
    '说明文档 / 设计稿链接',
    '是否能区分“必须先修”的关键体验问题和“可后续优化”的细节项，是否理解 B 端配置台的使用场景。',
    '任务奖金 500 元 + 优先进入下一轮任务池',
    'OPEN',
    NULL,
    2,
    150,
    14,
    NULL,
    '前端开发 / 后端架构 / 产品经理',
    '有实习经验 / 应届生',
    'https://docs.example.com/beichen-inventory-alert',
    '不要求你做完整高保真，只要能把问题拆清楚并给出有依据的优化建议即可。'
  ),
  (
    'BT005',
    'enterprise.beichenhr@bishe.local',
    '校招岗位画像标签词典整理与后台录入工具',
    '整理岗位画像标签词典，并给后台录入工具一版结构化方案',
    '北辰智联近期在整理不同岗位的学生画像标签，希望把校招项目、技能、表达风格等信息沉淀成更稳定的标签词典。',
    '当前团队缺一版清楚的标签层级说明和录入工具草稿，希望有同学先做出结构化方案，帮助后续继续扩展。',
    '提交标签词典分层方案、录入页面草图，以及字段校验和管理建议。',
    '说明文档 / 设计稿链接',
    '标签定义是否清晰、后台录入流程是否简洁、是否考虑到后续维护成本。',
    '任务奖金 650 元',
    'CLOSED',
    NULL,
    -4,
    288,
    96,
    90,
    '产品经理 / 后端架构 / 数据分析',
    '应届生 / 有实习经验',
    'https://docs.example.com/beichen-tag-dictionary',
    '该任务本轮未筛到继续接触对象，保留给企业审核工作区回看“已结束但未录用”的结果样本。'
  ),
  (
    'BT006',
    'enterprise.beichenhr@bishe.local',
    '客户成功周报自动汇总模板与可视化草稿',
    '围绕客户成功团队周报，整理一版自动汇总模板和可视化草稿',
    '北辰智联内部客户成功团队每周会整理续费风险、客户反馈和推进状态，但现有周报大多靠人工复制，结构不统一。',
    '我们希望找到能把模板、指标展示和关键结论区组织清楚的学生，先做一版轻量可视化草稿。',
    '提交周报模板结构、重点指标展示方式，以及你建议保留的结论摘要区。',
    '说明文档 / 设计稿链接 / 演示视频/链接',
    '是否能把“数据 + 结论 + 下一步动作”放到同一个阅读节奏里，是否考虑到了管理者快速浏览场景。',
    '任务奖金 550 元 + 后续任务优先邀请',
    'OPEN',
    NULL,
    11,
    190,
    12,
    NULL,
    '数据分析 / 产品经理 / 市场营销',
    '大三/研二 / 应届生',
    'https://docs.example.com/beichen-cs-weekly',
    '如果你有更好的周报阅读顺序建议，也欢迎写在补充说明里。'
  ),
  (
    'BT007',
    'enterprise.yunqiaodata@bishe.local',
    '数据产品岗案例拆解手册交互化改版',
    '把原本纯文档的案例拆解手册改成更易浏览的交互化版本',
    '云桥数据会给校招生提供一份“数据产品岗案例拆解手册”，但现在主要是长文档，学生和面试官回看时都不够高效。',
    '我们想先看一版更像产品化页面的组织方式，把案例背景、关键判断和结论留痕拆成更清晰的模块。',
    '提交交互结构说明、页面模块划分，以及你认为最值得保留的案例导航方式。',
    '说明文档 / 设计稿链接',
    '是否能兼顾“快速浏览”和“深入阅读”两种路径，是否知道数据产品材料该如何控制信息密度。',
    '任务奖金 700 元 + 下一轮任务优先邀请',
    'OPEN',
    NULL,
    7,
    170,
    24,
    NULL,
    '产品经理 / UI/UX设计 / 数据分析',
    '大三/研二 / 应届生',
    'https://docs.example.com/yunqiao-casebook',
    '更偏好能讲清楚信息结构而不是只做视觉包装的方案。'
  ),
  (
    'BT008',
    'enterprise.yunqiaodata@bishe.local',
    '物流异常监控看板接口联调与说明文档',
    '围绕物流异常监控看板，补一版接口联调说明和页面承载方案',
    '云桥数据正在接入一个物流异常监控看板，当前已有基础接口，但字段解释、异常类型和状态切换说明不够统一。',
    '我们希望先找到能把接口字段、页面信息层级和异常处理说明讲清楚的学生，完成一版联调型方案。',
    '提交接口字段说明、页面承载草图、异常场景说明，以及必要的联调注意事项。',
    '说明文档 / 设计稿链接 / 代码仓库链接',
    '是否能把接口字段翻译成业务可读语言，是否考虑到异常状态、空数据和排查入口。',
    '邀请面试机会 + 任务奖金 900 元',
    'CLOSED',
    'student.heqingyan@bishe.local',
    -7,
    260,
    72,
    60,
    '后端架构 / 前端开发 / 数据分析',
    '有实习经验 / 应届生',
    'https://docs.example.com/yunqiao-logistics',
    '该任务已完成本轮筛选，保留用于展示“已完成筛选 + 多提交对比”的回看场景。'
  ),
  (
    'BT009',
    'enterprise.yunqiaodata@bishe.local',
    'AI 面试复盘摘要卡片文案与布局优化建议',
    '围绕 AI 复盘摘要卡片，给一版文案节奏和布局层次优化建议',
    '云桥数据内部有一条 AI 复盘摘要模块，希望让学生在更短时间内看懂“问题、亮点、下一步”。当前卡片文案偏长，布局也不够稳定。',
    '我们想看一版兼顾阅读效率和结果留痕的卡片改造建议，而不是直接做成复杂交互。',
    '提交卡片信息层级建议、文案精简原则，以及可选的布局草图。',
    '说明文档 / 设计稿链接',
    '是否能保住重点信息，是否理解复盘场景中“先扫结论再看细节”的阅读顺序。',
    '任务奖金 500 元',
    'OPEN',
    NULL,
    5,
    120,
    10,
    NULL,
    '前端开发 / 产品经理 / AI算法',
    '大三/研二 / 有实习经验',
    'https://figma.example.com/yunqiao-ai-review',
    '如果你能额外给出“不同长度文案如何收口”的处理建议，会更加分。'
  ),
  (
    'BT010',
    'enterprise.yunqiaodata@bishe.local',
    '设备巡检小程序任务流转页重构建议',
    '针对巡检任务流转页，整理一版状态切换与操作入口重构建议',
    '云桥数据在做一个轻量设备巡检小程序，当前任务流转页能完成基本功能，但状态切换、回填信息和异常说明都比较散。',
    '我们想要一版面向一线使用者的结构梳理，帮助后续把流程页做得更易理解、也更好培训。',
    '提交问题清单、页面结构建议，以及关键状态切换的提示语义。',
    '说明文档 / 设计稿链接',
    '是否理解一线场景下的阅读压力，是否能把状态切换和下一步动作设计得足够明确。',
    '任务奖金 600 元',
    'OPEN',
    NULL,
    14,
    96,
    9,
    NULL,
    'UI/UX设计 / 产品经理 / 前端开发',
    '应届生 / 大三/研二',
    'https://docs.example.com/yunqiao-inspection',
    '更看重流程梳理和提示语义，不要求你画完整的高保真原型。'
  ),
  (
    'BT011',
    'enterprise.yunqiaodata@bishe.local',
    '商家入驻资料审核流程梳理与提示文案包',
    '把商家入驻资料审核流程梳理清楚，并补一版提示文案包',
    '云桥数据最近在调整商家入驻审核流程，当前后台能跑通，但页面提示和审核节点解释不够统一，容易引起重复沟通。',
    '我们希望先收一版偏产品运营视角的流程梳理和文案方案，看谁能把复杂节点说得更易懂。',
    '提交流程节点梳理、各节点提示文案建议，以及可能需要重点解释的高频问题。',
    '说明文档 / 设计稿链接',
    '是否能站在审核方和提交方两侧理解问题，是否知道如何把提示文案写得更克制、更明确。',
    '任务奖金 520 元',
    'CLOSED',
    NULL,
    -8,
    312,
    140,
    132,
    '产品经理 / 市场营销 / UI/UX设计',
    '应届生 / 有实习经验',
    'https://docs.example.com/yunqiao-onboarding',
    '该任务已结束但未筛出继续接触对象，用于保留“有提交但最终未继续推进”的测试样本。'
  ),
  (
    'BT012',
    'enterprise.yunqiaodata@bishe.local',
    '用户流失预警专题分析与指标解释页',
    '补一版流失预警专题分析页面，重点讲清指标口径与结论阅读顺序',
    '云桥数据正在准备一个用户流失预警专题页，希望把复杂的指标、分群和重点结论整理成业务同学能快速扫读的版本。',
    '我们希望先通过企业任务筛出对指标口径、页面组织和结论摘要都有感觉的学生。',
    '提交页面结构方案、关键指标解释方式，以及你建议重点展示的风险结论。',
    '说明文档 / 设计稿链接 / 演示视频/链接',
    '是否能把分析逻辑和页面表达结合起来，是否知道如何控制结论区的信息密度。',
    '任务奖金 880 元 + 进一步交流机会',
    'OPEN',
    NULL,
    1,
    72,
    6,
    NULL,
    '数据分析 / 产品经理 / 市场营销',
    '有实习经验 / 应届生',
    'https://docs.example.com/yunqiao-churn',
    '当前更偏好会写分析解释、也能给出页面组织建议的综合型方案。'
  );

INSERT INTO bounty_tasks (
  enterprise_user_id, title, description, reward_description, status,
  accepted_submission_id, deadline_at, closed_at, created_at, updated_at
)
SELECT
  u.id,
  t.title,
  CONCAT(
    '任务摘要：', t.summary,
    '\n\n【任务背景】\n', t.background,
    '\n\n【希望解决的问题】\n', t.problem,
    '\n\n【需要提交什么】\n', t.delivery_what,
    '\n\n【交付形式】\n', t.delivery_formats,
    '\n\n【最看重什么】\n', t.value_most,
    '\n\n【适合方向】\n', t.direction_tags,
    '\n\n【适合人群】\n', t.audience_tags,
    '\n\n【参考资料】\n', COALESCE(t.reference_link, '待企业补充'),
    '\n\n【注意事项】\n', t.notes
  ),
  t.reward_description,
  t.status,
  NULL,
  CASE
    WHEN t.deadline_day_offset IS NULL THEN NULL
    ELSE TIMESTAMP(DATE_ADD(DATE(@seed_now), INTERVAL t.deadline_day_offset DAY), '23:59:00')
  END,
  CASE
    WHEN t.status = 'CLOSED' AND t.closed_hours_ago IS NOT NULL THEN DATE_SUB(@seed_now, INTERVAL t.closed_hours_ago HOUR)
    ELSE NULL
  END,
  DATE_SUB(@seed_now, INTERVAL t.created_hours_ago HOUR),
  DATE_SUB(@seed_now, INTERVAL t.updated_hours_ago HOUR)
FROM tmp_bounty_seed_tasks t
JOIN users u ON u.email = t.enterprise_email
LEFT JOIN bounty_tasks bt ON bt.enterprise_user_id = u.id AND bt.title = t.title
WHERE bt.id IS NULL;

UPDATE bounty_tasks bt
JOIN users u ON u.id = bt.enterprise_user_id
JOIN tmp_bounty_seed_tasks t ON t.enterprise_email = u.email AND t.title = bt.title
SET
  bt.description = CONCAT(
    '任务摘要：', t.summary,
    '\n\n【任务背景】\n', t.background,
    '\n\n【希望解决的问题】\n', t.problem,
    '\n\n【需要提交什么】\n', t.delivery_what,
    '\n\n【交付形式】\n', t.delivery_formats,
    '\n\n【最看重什么】\n', t.value_most,
    '\n\n【适合方向】\n', t.direction_tags,
    '\n\n【适合人群】\n', t.audience_tags,
    '\n\n【参考资料】\n', COALESCE(t.reference_link, '待企业补充'),
    '\n\n【注意事项】\n', t.notes
  ),
  bt.reward_description = t.reward_description,
  bt.status = t.status,
  bt.accepted_submission_id = NULL,
  bt.deadline_at = CASE
    WHEN t.deadline_day_offset IS NULL THEN NULL
    ELSE TIMESTAMP(DATE_ADD(DATE(@seed_now), INTERVAL t.deadline_day_offset DAY), '23:59:00')
  END,
  bt.closed_at = CASE
    WHEN t.status = 'CLOSED' AND t.closed_hours_ago IS NOT NULL THEN DATE_SUB(@seed_now, INTERVAL t.closed_hours_ago HOUR)
    ELSE NULL
  END,
  bt.created_at = DATE_SUB(@seed_now, INTERVAL t.created_hours_ago HOUR),
  bt.updated_at = DATE_SUB(@seed_now, INTERVAL t.updated_hours_ago HOUR);

DROP TEMPORARY TABLE IF EXISTS tmp_bounty_seed_task_ids;
CREATE TEMPORARY TABLE tmp_bounty_seed_task_ids AS
SELECT
  t.seed_key,
  bt.id AS task_id,
  t.enterprise_email,
  t.accepted_student_email,
  t.updated_hours_ago
FROM tmp_bounty_seed_tasks t
JOIN users u ON u.email = t.enterprise_email
JOIN bounty_tasks bt ON bt.enterprise_user_id = u.id AND bt.title = t.title;

DROP TEMPORARY TABLE IF EXISTS tmp_bounty_seed_submissions;
CREATE TEMPORARY TABLE tmp_bounty_seed_submissions (
  seed_key         VARCHAR(32)  NOT NULL PRIMARY KEY,
  task_seed_key    VARCHAR(32)  NOT NULL,
  student_email    VARCHAR(255) NOT NULL,
  reviewer_email   VARCHAR(255) NULL,
  status           VARCHAR(20)  NOT NULL,
  content_text     TEXT         NOT NULL,
  attachment_links VARCHAR(1000) NULL,
  review_comment   VARCHAR(500) NULL,
  internal_note    TEXT         NULL,
  created_hours_ago INT         NOT NULL,
  reviewed_hours_ago INT        NULL,
  updated_hours_ago INT         NOT NULL
);

INSERT INTO tmp_bounty_seed_submissions (
  seed_key, task_seed_key, student_email, reviewer_email, status,
  content_text, attachment_links, review_comment, internal_note,
  created_hours_ago, reviewed_hours_ago, updated_hours_ago
)
VALUES
  (
    'BS001',
    'BT001',
    'student.xuanran@bishe.local',
    NULL,
    'SUBMITTED',
    '我先从报名漏斗现状、关键跳出点和页面信息层级三个部分拆了问题，并给了一版低保真草图，重点想解决介绍区过长和报名表单阻塞的问题。',
    'https://figma.example.com/submission/bs001,https://docs.example.com/submission/bs001',
    NULL,
    '页面层级和转化路径表达比较清楚，可以继续观察是否需要进入处理中。',
    28,
    NULL,
    28
  ),
  (
    'BS002',
    'BT001',
    'student.heqingyan@bishe.local',
    NULL,
    'REVIEWING',
    '我从现有活动页入口链路出发补了一版埋点方案，同时把报名确认页、成功页和后续提醒链路整理成了更适合追踪转化的页面结构。',
    'https://docs.example.com/submission/bs002',
    NULL,
    '方案对埋点链路的考虑较完整，正在补看页面阅读顺序和提示文案部分。',
    22,
    NULL,
    12
  ),
  (
    'BS003',
    'BT002',
    'student.liujianing@bishe.local',
    NULL,
    'SUBMITTED',
    '我把知识库评估看板拆成召回概览、失败样本、问题分类和人工复核四个区块，并补充了指标解释文案，方便业务同学快速理解检索效果。',
    'https://docs.example.com/submission/bs003',
    NULL,
    '看板区块划分清楚，后续重点看指标口径和问题分类是否足够稳定。',
    34,
    NULL,
    24
  ),
  (
    'BS004',
    'BT003',
    'student.zhoumuyang@bishe.local',
    'enterprise.beichenhr@bishe.local',
    'ACCEPTED',
    '我把线索分层方案拆成“分层定义、触发条件、页面承载、埋点验证”四层，并给了一版说明页结构和运营同学能直接对照使用的示意内容。',
    'https://docs.example.com/submission/bs004,https://figma.example.com/submission/bs004',
    '继续接触意向：进一步交流\n补充说明：规则解释清楚，页面承载和验证路径都比较完整，适合继续讨论细节取舍。',
    '拆解完整，说明页结构清晰，后续可重点继续讨论字段命名和埋点优先级。',
    148,
    112,
    112
  ),
  (
    'BS005',
    'BT003',
    'student.xuanran@bishe.local',
    'enterprise.beichenhr@bishe.local',
    'REJECTED',
    '我主要从页面视觉呈现角度做了一版说明页草稿，也补充了标签和卡片样式，但对分层规则和验证埋点部分展开得不多。',
    'https://figma.example.com/submission/bs005',
    '未入选原因：本次任务方向暂不完全匹配\n补充说明：页面表达不错，但当前更看重分层规则解释和验证路径的完整度。',
    '偏视觉呈现，策略解释和埋点部分不足。',
    146,
    114,
    114
  ),
  (
    'BS006',
    'BT005',
    'student.liujianing@bishe.local',
    'enterprise.beichenhr@bishe.local',
    'REJECTED',
    '我从数据库结构和后台录入字段设计出发整理了一版标签词典表结构，但没有进一步展开管理流程和运营同学如何使用。',
    'https://docs.example.com/submission/bs006',
    '未入选原因：当前提交完成度还可以继续加强\n补充说明：表结构思路可以，但标签层级、维护流程和后台使用场景还不够完整。',
    '更像后端表设计，缺少词典层级和运营使用视角。',
    122,
    92,
    92
  ),
  (
    'BS007',
    'BT005',
    'student.heqingyan@bishe.local',
    'enterprise.beichenhr@bishe.local',
    'REJECTED',
    '我补了一版录入页面的字段和校验规则，也写了错误提示文案，但对于标签词典的业务分类和维护节奏说明得不够深入。',
    'https://docs.example.com/submission/bs007',
    '未入选原因：这次暂未继续推进，欢迎后续继续参与\n补充说明：校验规则比较完整，但词典分层和业务语义还可以再展开。',
    '校验规则清楚，但词典定义层级还不够成熟。',
    118,
    90,
    90
  ),
  (
    'BS008',
    'BT006',
    'student.zhoumuyang@bishe.local',
    NULL,
    'SUBMITTED',
    '我把客户成功周报拆成风险概览、重点客户、动作进展和下周计划四个区块，并给了管理者快速浏览版和明细版两套阅读顺序。',
    'https://docs.example.com/submission/bs008,https://figma.example.com/submission/bs008',
    NULL,
    '阅读顺序设计合理，后续重点看指标口径与摘要区是否还能再收紧。',
    18,
    NULL,
    18
  ),
  (
    'BS009',
    'BT006',
    'student.heqingyan@bishe.local',
    NULL,
    'REVIEWING',
    '我从指标来源、周报结构和行动项留痕三个方向补了一版方案，重点想让周报既能复盘又能推动下一步动作。',
    'https://docs.example.com/submission/bs009',
    NULL,
    '方案兼顾了指标与动作，正在进一步比对可读性和实际落地成本。',
    16,
    NULL,
    10
  ),
  (
    'BS010',
    'BT007',
    'student.zhoumuyang@bishe.local',
    NULL,
    'SUBMITTED',
    '我把案例手册拆成案例导航、背景问题、关键判断、复盘结论四大区块，并补了一版适合边看边回顾的页面信息结构。',
    'https://figma.example.com/submission/bs010',
    NULL,
    '结构思路较顺，后续重点看内容密度和导航方式是否还需要继续压缩。',
    32,
    NULL,
    20
  ),
  (
    'BS011',
    'BT008',
    'student.heqingyan@bishe.local',
    'enterprise.yunqiaodata@bishe.local',
    'ACCEPTED',
    '我围绕接口字段说明、异常状态处理和页面信息层级整理了一版联调说明，同时补了异常清单和排查入口说明，方便产品和前端统一理解。',
    'https://docs.example.com/submission/bs011,https://github.com/demo/bs011',
    '继续接触意向：邀请面试\n补充说明：接口解释清楚，异常状态和联调说明都比较成熟，适合继续深入沟通。',
    '字段说明、异常处理和页面承载三块都比较稳，可直接作为继续接触样本。',
    86,
    64,
    64
  ),
  (
    'BS012',
    'BT008',
    'student.liujianing@bishe.local',
    'enterprise.yunqiaodata@bishe.local',
    'REJECTED',
    '我从接口结构和数据回传角度写了一版字段表，但页面承载和异常场景的处理说明补得比较少。',
    'https://docs.example.com/submission/bs012',
    '未入选原因：当前提交完成度还可以继续加强\n补充说明：字段表方向正确，但页面信息组织和异常说明还不够完整。',
    '更偏接口字典，缺少页面承载和异常展示部分。',
    82,
    62,
    62
  ),
  (
    'BS013',
    'BT008',
    'student.xuanran@bishe.local',
    'enterprise.yunqiaodata@bishe.local',
    'REJECTED',
    '我主要补了一版看板视觉排布和重点卡片样式，对联调文档和字段解释部分展开得不够。',
    'https://figma.example.com/submission/bs013',
    '未入选原因：本次任务方向暂不完全匹配\n补充说明：视觉表达不错，但当前任务更看重接口联调说明与异常处理逻辑。',
    '偏视觉稿，联调说明不足。',
    80,
    60,
    60
  ),
  (
    'BS014',
    'BT009',
    'student.xuanran@bishe.local',
    NULL,
    'SUBMITTED',
    '我把复盘摘要卡片改成“结论、亮点、问题、下一步”四层结构，并给了三种不同字数下的收口方式，方便控制内容密度。',
    'https://figma.example.com/submission/bs014',
    NULL,
    '卡片信息层级比较稳，后续重点看不同长度文案的自适应处理。',
    14,
    NULL,
    14
  ),
  (
    'BS015',
    'BT009',
    'student.heqingyan@bishe.local',
    NULL,
    'SUBMITTED',
    '我从阅读顺序和文案节奏出发补了一版摘要卡片草图，同时把“先看结论、再看证据”的浏览路径写成了结构说明。',
    'https://docs.example.com/submission/bs015',
    NULL,
    '阅读顺序设计比较清晰，后续重点看布局稳定性和卡片间距。',
    12,
    NULL,
    12
  ),
  (
    'BS016',
    'BT011',
    'student.xuanran@bishe.local',
    'enterprise.yunqiaodata@bishe.local',
    'REJECTED',
    '我整理了一版流程节点和提示文案，但更偏页面展示，对审核双方的操作差异和异常场景考虑得还不够。',
    'https://docs.example.com/submission/bs016',
    '未入选原因：当前提交完成度还可以继续加强\n补充说明：文案方向可以，但审核节点差异和异常场景还不够完整。',
    '流程节点有基础，但双侧视角和异常场景不足。',
    150,
    134,
    134
  ),
  (
    'BS017',
    'BT012',
    'student.zhoumuyang@bishe.local',
    NULL,
    'REVIEWING',
    '我先从指标口径、风险结论区和明细解释区三部分整理了一版结构，重点让业务同学可以先看结论，再决定是否展开看明细。',
    'https://docs.example.com/submission/bs017,https://figma.example.com/submission/bs017',
    NULL,
    '指标口径和结论区表达比较好，正在进一步对照明细展开方式。',
    8,
    NULL,
    5
  ),
  (
    'BS018',
    'BT012',
    'student.liujianing@bishe.local',
    NULL,
    'SUBMITTED',
    '我把专题页拆成风险总览、核心指标、分群解释和动作建议四块，并额外补了“异常指标如何解释”的文案建议。',
    'https://docs.example.com/submission/bs018',
    NULL,
    '结构完整，适合作为对照样本继续比较结论区和指标区的阅读顺序。',
    10,
    NULL,
    6
  );

INSERT INTO bounty_submissions (
  task_id, student_user_id, content_text, attachment_links, status,
  review_comment, internal_note, reviewed_at, reviewer_user_id, created_at, updated_at
)
SELECT
  task_ids.task_id,
  student_user.id,
  submission.content_text,
  submission.attachment_links,
  submission.status,
  submission.review_comment,
  submission.internal_note,
  CASE
    WHEN submission.reviewed_hours_ago IS NULL THEN NULL
    ELSE DATE_SUB(@seed_now, INTERVAL submission.reviewed_hours_ago HOUR)
  END,
  reviewer_user.id,
  DATE_SUB(@seed_now, INTERVAL submission.created_hours_ago HOUR),
  DATE_SUB(@seed_now, INTERVAL submission.updated_hours_ago HOUR)
FROM tmp_bounty_seed_submissions submission
JOIN tmp_bounty_seed_task_ids task_ids ON task_ids.seed_key = submission.task_seed_key
JOIN users student_user ON student_user.email = submission.student_email
LEFT JOIN users reviewer_user ON reviewer_user.email = submission.reviewer_email
ON DUPLICATE KEY UPDATE
  content_text = VALUES(content_text),
  attachment_links = VALUES(attachment_links),
  status = VALUES(status),
  review_comment = VALUES(review_comment),
  internal_note = VALUES(internal_note),
  reviewed_at = VALUES(reviewed_at),
  reviewer_user_id = VALUES(reviewer_user_id),
  created_at = VALUES(created_at),
  updated_at = VALUES(updated_at);

UPDATE bounty_tasks bt
JOIN tmp_bounty_seed_task_ids task_ids ON task_ids.task_id = bt.id
LEFT JOIN users accepted_user ON accepted_user.email = task_ids.accepted_student_email
LEFT JOIN bounty_submissions accepted_submission
  ON accepted_submission.task_id = bt.id
 AND accepted_submission.student_user_id = accepted_user.id
SET
  bt.accepted_submission_id = accepted_submission.id,
  bt.updated_at = DATE_SUB(@seed_now, INTERVAL task_ids.updated_hours_ago HOUR)
WHERE task_ids.accepted_student_email IS NOT NULL;

UPDATE bounty_tasks bt
JOIN tmp_bounty_seed_task_ids task_ids ON task_ids.task_id = bt.id
SET
  bt.accepted_submission_id = NULL,
  bt.updated_at = DATE_SUB(@seed_now, INTERVAL task_ids.updated_hours_ago HOUR)
WHERE task_ids.accepted_student_email IS NULL;

COMMIT;

SELECT
  COUNT(*) AS seeded_task_total
FROM bounty_tasks bt
JOIN tmp_bounty_seed_task_ids task_ids ON task_ids.task_id = bt.id;

SELECT
  bt.status,
  COUNT(*) AS count_per_status
FROM bounty_tasks bt
JOIN tmp_bounty_seed_task_ids task_ids ON task_ids.task_id = bt.id
GROUP BY bt.status
ORDER BY bt.status;

SELECT
  COUNT(*) AS seeded_submission_total
FROM bounty_submissions bs
JOIN tmp_bounty_seed_task_ids task_ids ON task_ids.task_id = bs.task_id;

SELECT
  ep.company_name,
  COUNT(bt.id) AS total_tasks,
  SUM(CASE WHEN bt.status = 'OPEN' THEN 1 ELSE 0 END) AS open_tasks,
  SUM(CASE WHEN bt.status = 'CLOSED' THEN 1 ELSE 0 END) AS closed_tasks
FROM bounty_tasks bt
JOIN users u ON u.id = bt.enterprise_user_id
JOIN enterprise_profiles ep ON ep.user_id = u.id
WHERE u.email IN ('enterprise.beichenhr@bishe.local', 'enterprise.yunqiaodata@bishe.local')
GROUP BY ep.company_name
ORDER BY ep.company_name;
