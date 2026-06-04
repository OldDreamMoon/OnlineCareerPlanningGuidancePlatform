-- 2026-05-17 答辩演示数据基准（PostgreSQL）
--
-- 主演示对象：
--   学生：安然（student.xuanran@bishe.local，当前 display_name 为“安然”）
--   导师：顾航航（mentor.guhang@bishe.local）
--   企业：北辰智联（enterprise.beichenhr@bishe.local）
--
-- 设计原则：
--   1. 不新增 / 不修改 users、student_profiles、mentor_profiles、enterprise_profiles 等稳定主数据。
--   2. 只追加或受控 upsert 2026-05-17 前后的业务演示数据。
--   3. 所有可清理数据统一带 DEFENSE-20260517 / [答辩演示] / defense-20260517 前缀。
--   4. 默认 dry-run：直接执行会 ROLLBACK；确认无误后使用 -v demo_commit=1 才 COMMIT。
--
-- dry-run:
--   PGPASSWORD=bishe psql -h 127.0.0.1 -U bishe -d bishe -f scripts/db/seed_defense_demo_data_20260517.sql
--
-- commit:
--   PGPASSWORD=bishe psql -h 127.0.0.1 -U bishe -d bishe -v demo_commit=1 -f scripts/db/seed_defense_demo_data_20260517.sql

\set ON_ERROR_STOP on
\if :{?demo_commit}
\else
\set demo_commit 0
\endif

BEGIN;
SET LOCAL TIME ZONE 'UTC';

DO $$
DECLARE
    missing_accounts TEXT;
BEGIN
    SELECT string_agg(label, ', ' ORDER BY label)
      INTO missing_accounts
      FROM (
        VALUES
          ('管理员 admin@bishe.local', 'admin@bishe.local', 'ADMIN'),
          ('学生 安然 student.xuanran@bishe.local', 'student.xuanran@bishe.local', 'STUDENT'),
          ('导师 顾航航 mentor.guhang@bishe.local', 'mentor.guhang@bishe.local', 'MENTOR'),
          ('企业 北辰智联 enterprise.beichenhr@bishe.local', 'enterprise.beichenhr@bishe.local', 'ENTERPRISE'),
          ('学生 刘嘉宁 student.liujianing@bishe.local', 'student.liujianing@bishe.local', 'STUDENT'),
          ('学生 周沐阳 student.zhoumuyang@bishe.local', 'student.zhoumuyang@bishe.local', 'STUDENT'),
          ('学生 陈思语 student.chensiyu@bishe.local', 'student.chensiyu@bishe.local', 'STUDENT'),
          ('学生 孙泽宇 student.sunzeyu@bishe.local', 'student.sunzeyu@bishe.local', 'STUDENT'),
          ('学生 何清妍 student.heqingyan@bishe.local', 'student.heqingyan@bishe.local', 'STUDENT'),
          ('学生 林嘉琪 student.linjiaqi@bishe.local', 'student.linjiaqi@bishe.local', 'STUDENT'),
          ('导师 韩雪 mentor.hanxue@bishe.local', 'mentor.hanxue@bishe.local', 'MENTOR'),
          ('导师 程砚北 mentor.chengyanbei@bishe.local', 'mentor.chengyanbei@bishe.local', 'MENTOR'),
          ('导师 林乔 mentor.linqiao@bishe.local', 'mentor.linqiao@bishe.local', 'MENTOR'),
          ('导师 何槿 mentor.hejin@bishe.local', 'mentor.hejin@bishe.local', 'MENTOR'),
          ('导师 邱聿 mentor.qiuyu@bishe.local', 'mentor.qiuyu@bishe.local', 'MENTOR'),
          ('导师 江楠 mentor.jiangnan@bishe.local', 'mentor.jiangnan@bishe.local', 'MENTOR'),
          ('导师 舒沁 mentor.shuqin@bishe.local', 'mentor.shuqin@bishe.local', 'MENTOR'),
          ('导师 唐汐 mentor.tangxi@bishe.local', 'mentor.tangxi@bishe.local', 'MENTOR'),
          ('企业 云桥数据 enterprise.yunqiaodata@bishe.local', 'enterprise.yunqiaodata@bishe.local', 'ENTERPRISE')
      ) AS required_accounts(label, email, role)
     WHERE NOT EXISTS (
        SELECT 1
          FROM users u
         WHERE u.email = required_accounts.email
           AND u.role = required_accounts.role
           AND COALESCE(u.is_deleted, FALSE) = FALSE
     );

    IF missing_accounts IS NOT NULL THEN
        RAISE EXCEPTION '缺少答辩演示依赖账号：%', missing_accounts;
    END IF;
END $$;

CREATE TEMP TABLE tmp_defense_users AS
SELECT
    max(id) FILTER (WHERE email = 'admin@bishe.local') AS admin_user_id,
    max(id) FILTER (WHERE email = 'student.xuanran@bishe.local') AS anran_user_id,
    max(id) FILTER (WHERE email = 'mentor.guhang@bishe.local') AS guhang_user_id,
    max(id) FILTER (WHERE email = 'enterprise.beichenhr@bishe.local') AS beichen_user_id,
    max(id) FILTER (WHERE email = 'enterprise.yunqiaodata@bishe.local') AS yunqiao_user_id,
    max(id) FILTER (WHERE email = 'student.liujianing@bishe.local') AS liujianing_user_id,
    max(id) FILTER (WHERE email = 'student.zhoumuyang@bishe.local') AS zhoumuyang_user_id,
    max(id) FILTER (WHERE email = 'student.chensiyu@bishe.local') AS chensiyu_user_id,
    max(id) FILTER (WHERE email = 'student.sunzeyu@bishe.local') AS sunzeyu_user_id,
    max(id) FILTER (WHERE email = 'student.heqingyan@bishe.local') AS heqingyan_user_id,
    max(id) FILTER (WHERE email = 'student.linjiaqi@bishe.local') AS linjiaqi_user_id,
    max(id) FILTER (WHERE email = 'mentor.hanxue@bishe.local') AS hanxue_user_id,
    max(id) FILTER (WHERE email = 'mentor.chengyanbei@bishe.local') AS chengyanbei_user_id,
    max(id) FILTER (WHERE email = 'mentor.linqiao@bishe.local') AS linqiao_user_id,
    max(id) FILTER (WHERE email = 'mentor.hejin@bishe.local') AS hejin_user_id,
    max(id) FILTER (WHERE email = 'mentor.qiuyu@bishe.local') AS qiuyu_user_id,
    max(id) FILTER (WHERE email = 'mentor.jiangnan@bishe.local') AS jiangnan_user_id,
    max(id) FILTER (WHERE email = 'mentor.shuqin@bishe.local') AS shuqin_user_id,
    max(id) FILTER (WHERE email = 'mentor.tangxi@bishe.local') AS tangxi_user_id
FROM users;

CREATE TEMP TABLE tmp_defense_orders (
    order_no TEXT PRIMARY KEY,
    student_key TEXT NOT NULL,
    mentor_key TEXT NOT NULL,
    student_user_id BIGINT NOT NULL,
    mentor_user_id BIGINT NOT NULL,
    amount_fen INT NOT NULL,
    status TEXT NOT NULL,
    scene_code TEXT NOT NULL,
    scene_label TEXT NOT NULL,
    package_name TEXT NOT NULL,
    delivery_mode TEXT NOT NULL,
    duration_minutes INT,
    source_page TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    paid_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    appointment_start_at TIMESTAMPTZ,
    appointment_end_at TIMESTAMPTZ,
    review_rating SMALLINT,
    review_comment TEXT,
    review_created_at TIMESTAMPTZ,
    question_text TEXT NOT NULL,
    problem_summary TEXT NOT NULL,
    selected_material_types TEXT,
    core_questions TEXT[] NOT NULL,
    expected_outcomes TEXT[] NOT NULL
);

INSERT INTO tmp_defense_orders (
    order_no, student_key, mentor_key, student_user_id, mentor_user_id, amount_fen, status,
    scene_code, scene_label, package_name, delivery_mode, duration_minutes, source_page,
    created_at, updated_at, paid_at, closed_at, appointment_start_at, appointment_end_at,
    review_rating, review_comment, review_created_at, question_text, problem_summary,
    selected_material_types, core_questions, expected_outcomes
)
SELECT
    v.order_no,
    v.student_key,
    v.mentor_key,
    CASE v.student_key
        WHEN 'ANRAN' THEN u.anran_user_id
        WHEN 'LIUJIANING' THEN u.liujianing_user_id
        WHEN 'ZHOUMUYANG' THEN u.zhoumuyang_user_id
        WHEN 'CHENSIYU' THEN u.chensiyu_user_id
        WHEN 'SUNZEYU' THEN u.sunzeyu_user_id
        WHEN 'HEQINGYAN' THEN u.heqingyan_user_id
        WHEN 'LINJIAQI' THEN u.linjiaqi_user_id
    END AS student_user_id,
    CASE v.mentor_key
        WHEN 'GUHANG' THEN u.guhang_user_id
        WHEN 'HANXUE' THEN u.hanxue_user_id
        WHEN 'CHENGYANBEI' THEN u.chengyanbei_user_id
        WHEN 'LINQIAO' THEN u.linqiao_user_id
        WHEN 'HEJIN' THEN u.hejin_user_id
        WHEN 'QIUYU' THEN u.qiuyu_user_id
        WHEN 'JIANGNAN' THEN u.jiangnan_user_id
        WHEN 'SHUQIN' THEN u.shuqin_user_id
        WHEN 'TANGXI' THEN u.tangxi_user_id
    END AS mentor_user_id,
    v.amount_fen,
    v.status,
    v.scene_code,
    v.scene_label,
    v.package_name,
    v.delivery_mode,
    v.duration_minutes,
    v.source_page,
    v.created_at,
    v.updated_at,
    v.paid_at,
    v.closed_at,
    v.appointment_start_at,
    v.appointment_end_at,
    v.review_rating,
    v.review_comment,
    v.review_created_at,
    v.question_text,
    v.problem_summary,
    v.selected_material_types,
    v.core_questions,
    v.expected_outcomes
FROM (
    VALUES
      (
        'CONS-DEF-20260517-001', 'ANRAN', 'GUHANG', 9900, 'CREATED',
        'RESUME_DIAGNOSIS', '简历诊断', '简历问诊', 'TEXT_ASYNC', NULL, 'MENTOR_MARKETPLACE',
        TIMESTAMPTZ '2026-05-17 09:45:00+08', TIMESTAMPTZ '2026-05-17 09:45:00+08',
        NULL::TIMESTAMPTZ, NULL::TIMESTAMPTZ, NULL::TIMESTAMPTZ, NULL::TIMESTAMPTZ,
        NULL::SMALLINT, NULL::TEXT, NULL::TIMESTAMPTZ,
        '我想把前端项目里的性能优化、交互状态和数据埋点写得更像真实业务成果，避免简历只剩技术名词。',
        '学生已经进入答辩和春招同步冲刺阶段，需要快速确认简历主项目排序和成果表达。',
        'RESUME,PROJECT_MATERIAL',
        ARRAY['主项目应该放性能优化还是交互设计', '如何把页面体验和数据验证写成成果', '哪些技术细节不适合放在简历第一屏']::TEXT[],
        ARRAY['形成一版更聚焦的简历主线', '明确后续补充材料清单']::TEXT[]
      ),
      (
        'CONS-DEF-20260517-002', 'ANRAN', 'GUHANG', 16900, 'PAYING',
        'PROJECT_STORYTELLING', '项目表达', '项目表达深挖', 'APPOINTMENT', 45, 'MENTOR_MARKETPLACE_RECOMMENDATION',
        TIMESTAMPTZ '2026-05-17 09:38:00+08', TIMESTAMPTZ '2026-05-17 09:41:00+08',
        NULL::TIMESTAMPTZ, NULL::TIMESTAMPTZ, TIMESTAMPTZ '2026-05-18 19:30:00+08', TIMESTAMPTZ '2026-05-18 20:15:00+08',
        NULL::SMALLINT, NULL::TEXT, NULL::TIMESTAMPTZ,
        '我准备明天集中讲毕业设计项目，希望先请导师看一下项目表达路线是否能从用户场景自然过渡到技术实现。',
        '订单已经拉起沙箱支付但尚未完成支付，用于演示 PAYING 和支付记录 INIT 状态。',
        'RESUME,PROJECT_MATERIAL,SCREENSHOT',
        ARRAY['如何从用户场景讲到技术实现', '哪些模块适合放到答辩重点', '支付完成后导师工作区会如何承接']::TEXT[],
        ARRAY['完成项目表达提纲', '确认答辩讲述优先级', '支付后进入导师履约工作区']::TEXT[]
      ),
      (
        'CONS-DEF-20260517-003', 'ANRAN', 'GUHANG', 9900, 'PAID',
        'RESUME_DIAGNOSIS', '简历诊断', '简历问诊', 'TEXT_ASYNC', NULL, 'MENTOR_MARKETPLACE_FAVORITES',
        TIMESTAMPTZ '2026-05-17 08:40:00+08', TIMESTAMPTZ '2026-05-17 08:46:00+08',
        TIMESTAMPTZ '2026-05-17 08:46:00+08', NULL::TIMESTAMPTZ, NULL::TIMESTAMPTZ, NULL::TIMESTAMPTZ,
        NULL::SMALLINT, NULL::TEXT, NULL::TIMESTAMPTZ,
        '这版简历主要投前端和增长方向，想让顾航航老师帮我判断项目排序、指标描述和技术栈呈现是否一致。',
        '已支付但导师还没有首条正式回复，是导师工作区待处理队列的主演示订单。',
        'RESUME,JOB_DESCRIPTION',
        ARRAY['前端项目和增长项目怎么排序', '指标描述是否过度包装', '岗位关键词应该放在哪一段']::TEXT[],
        ARRAY['拿到一版结构化简历反馈', '确认投递前最后修改清单']::TEXT[]
      ),
      (
        'CONS-DEF-20260517-004', 'ANRAN', 'GUHANG', 17900, 'ANSWERED',
        'MOCK_INTERVIEW_REVIEW', '模拟面试复盘', '模拟面试复盘', 'APPOINTMENT', 45, 'MENTOR_MARKETPLACE_RECOMMENDATION',
        TIMESTAMPTZ '2026-05-16 18:10:00+08', TIMESTAMPTZ '2026-05-17 09:25:00+08',
        TIMESTAMPTZ '2026-05-16 18:15:00+08', NULL::TIMESTAMPTZ, TIMESTAMPTZ '2026-05-17 15:00:00+08', TIMESTAMPTZ '2026-05-17 15:45:00+08',
        NULL::SMALLINT, NULL::TEXT, NULL::TIMESTAMPTZ,
        '昨天模拟面试时我回答项目性能优化有点散，想请导师帮我按面试追问路径复盘。',
        '导师已回复，学生尚未确认关闭，用于演示 ANSWERED 状态、消息流和学生确认入口。',
        'RESUME,INTERVIEW_RECORD,PROJECT_MATERIAL',
        ARRAY['首问时项目背景应该讲多长', '性能优化数据怎么证明可信', '被追问组件层级时怎么收束回答']::TEXT[],
        ARRAY['形成一版面试复盘笔记', '明确下次模拟面试的练习重点']::TEXT[]
      ),
      (
        'CONS-DEF-20260517-005', 'ANRAN', 'GUHANG', 16900, 'CLOSED',
        'PROJECT_STORYTELLING', '项目表达', '项目表达深挖', 'APPOINTMENT', 45, 'MENTOR_MARKETPLACE_RECOMMENDATION',
        TIMESTAMPTZ '2026-05-12 20:10:00+08', TIMESTAMPTZ '2026-05-15 21:20:00+08',
        TIMESTAMPTZ '2026-05-12 20:16:00+08', TIMESTAMPTZ '2026-05-15 21:20:00+08', TIMESTAMPTZ '2026-05-14 20:00:00+08', TIMESTAMPTZ '2026-05-14 20:45:00+08',
        5::SMALLINT, '老师把项目主线拆得很清楚，尤其是通知回流和企业任务 viewer 态这两块，答辩里可以直接讲。', TIMESTAMPTZ '2026-05-15 21:18:00+08',
        '我想围绕毕业设计准备一版 8 分钟项目讲述，重点讲产品闭环、AI 能力和企业任务。',
        '已完成且已评价，是学生订单列表、导师评价和财务统计的核心样例。',
        'PROJECT_MATERIAL,PPT_OUTLINE',
        ARRAY['8 分钟讲述应该怎么分配模块', 'AI 网关和通知回流讲到什么深度', '企业任务模块怎么体现真实闭环']::TEXT[],
        ARRAY['完成答辩项目讲述提纲', '得到导师评价数据', '进入导师收入统计']::TEXT[]
      ),
      (
        'CONS-DEF-20260517-006', 'ANRAN', 'GUHANG', 9900, 'REFUNDED',
        'RESUME_DIAGNOSIS', '简历诊断', '简历问诊', 'TEXT_ASYNC', NULL, 'MENTOR_MARKETPLACE',
        TIMESTAMPTZ '2026-05-15 08:20:00+08', TIMESTAMPTZ '2026-05-16 09:05:00+08',
        TIMESTAMPTZ '2026-05-15 08:25:00+08', TIMESTAMPTZ '2026-05-16 09:05:00+08', NULL::TIMESTAMPTZ, NULL::TIMESTAMPTZ,
        NULL::SMALLINT, NULL::TEXT, NULL::TIMESTAMPTZ,
        '这单用于测试导师 24 小时内未回复时，系统自动发起售后并退款的完整兜底链路。',
        '导师首复超时后已自动退款，展示 24 小时未回复兜底、售后申请和退款支付记录。',
        'RESUME',
        ARRAY['导师未回复时学生权益如何保障', '系统如何记录自动售后来源']::TEXT[],
        ARRAY['演示自动售后申请', '演示退款通知和支付记录']::TEXT[]
      ),
      (
        'CONS-DEF-20260517-007', 'LIUJIANING', 'GUHANG', 14900, 'CREATED',
        'CAMPUS_RECRUITMENT_STRATEGY', '校招投递策略', '校招策略梳理', 'APPOINTMENT', 45, 'MENTOR_MARKETPLACE',
        TIMESTAMPTZ '2026-05-17 09:20:00+08', TIMESTAMPTZ '2026-05-17 09:20:00+08',
        NULL::TIMESTAMPTZ, NULL::TIMESTAMPTZ, TIMESTAMPTZ '2026-05-19 20:00:00+08', TIMESTAMPTZ '2026-05-19 20:45:00+08',
        NULL::SMALLINT, NULL::TEXT, NULL::TIMESTAMPTZ,
        '春招后端投递进入尾声，希望确认最后一周是继续海投还是集中准备两家重点公司。',
        '创建时间已超过 30 分钟，用于在 5 月 17 日 10 点基线下演示未支付订单超时回收候选。',
        'RESUME,JOB_DESCRIPTION',
        ARRAY['还要不要继续海投', '如何选择重点公司准备', '预约时段超时后如何释放']::TEXT[],
        ARRAY['演示 CREATED 超时边界', '为超时取消任务提供样例']::TEXT[]
      ),
      (
        'CONS-DEF-20260517-008', 'ZHOUMUYANG', 'GUHANG', 9900, 'CANCELED',
        'RESUME_DIAGNOSIS', '简历诊断', '简历问诊', 'TEXT_ASYNC', NULL, 'MENTOR_MARKETPLACE',
        TIMESTAMPTZ '2026-05-17 08:30:00+08', TIMESTAMPTZ '2026-05-17 09:05:00+08',
        NULL::TIMESTAMPTZ, TIMESTAMPTZ '2026-05-17 09:05:00+08', NULL::TIMESTAMPTZ, NULL::TIMESTAMPTZ,
        NULL::SMALLINT, NULL::TEXT, NULL::TIMESTAMPTZ,
        '数据分析实习简历先下单但没有及时支付，系统按 30 分钟规则关闭。',
        '已因未支付超时取消，用于演示 CANCELED 终态和通知回流。',
        'RESUME',
        ARRAY['未支付订单为什么被取消', '取消后导师时段如何处理']::TEXT[],
        ARRAY['演示超时取消终态', '演示学生通知']::TEXT[]
      ),
      (
        'CONS-DEF-20260517-009', 'CHENSIYU', 'HANXUE', 13900, 'PAID',
        'RESUME_DIAGNOSIS', '简历诊断', '标准图文咨询', 'TEXT_ASYNC', NULL, 'MENTOR_MARKETPLACE',
        TIMESTAMPTZ '2026-05-16 10:15:00+08', TIMESTAMPTZ '2026-05-16 10:20:00+08',
        TIMESTAMPTZ '2026-05-16 10:20:00+08', NULL::TIMESTAMPTZ, NULL::TIMESTAMPTZ, NULL::TIMESTAMPTZ,
        NULL::SMALLINT, NULL::TEXT, NULL::TIMESTAMPTZ,
        '推荐算法和数据产品双线投递时，简历标题和项目顺序不知道怎么兼顾。',
        '支付时间距离 5 月 17 日 10 点还有 20 分钟才满 24 小时，用于演示导师首复临期风险。',
        'RESUME,JOB_DESCRIPTION',
        ARRAY['算法和产品经历怎么取舍', '双线投递会不会显得目标不清楚']::TEXT[],
        ARRAY['展示导师待回复临期订单', '给后台风险筛选提供样例']::TEXT[]
      ),
      (
        'CONS-DEF-20260517-010', 'SUNZEYU', 'HANXUE', 13900, 'REFUNDED',
        'RESUME_DIAGNOSIS', '简历诊断', '标准图文咨询', 'TEXT_ASYNC', NULL, 'MENTOR_MARKETPLACE',
        TIMESTAMPTZ '2026-05-15 09:10:00+08', TIMESTAMPTZ '2026-05-16 10:00:00+08',
        TIMESTAMPTZ '2026-05-15 09:15:00+08', TIMESTAMPTZ '2026-05-16 10:00:00+08', NULL::TIMESTAMPTZ, NULL::TIMESTAMPTZ,
        NULL::SMALLINT, NULL::TEXT, NULL::TIMESTAMPTZ,
        '该订单故意不写导师回复，用于演示系统自动识别导师超时未回复并触发退款。',
        '另一个导师超时退款样例，保证后台售后列表中不只出现单一导师。',
        'RESUME',
        ARRAY['导师超时未回复如何自动处理', '退款后评价和完成单如何回滚']::TEXT[],
        ARRAY['演示多导师超时退款', '演示后台售后统计']::TEXT[]
      ),
      (
        'CONS-DEF-20260517-011', 'HEQINGYAN', 'CHENGYANBEI', 15900, 'ANSWERED',
        'MOCK_INTERVIEW_REVIEW', '模拟面试复盘', '标准图文咨询', 'TEXT_ASYNC', NULL, 'MENTOR_MARKETPLACE',
        TIMESTAMPTZ '2026-05-15 21:00:00+08', TIMESTAMPTZ '2026-05-16 18:30:00+08',
        TIMESTAMPTZ '2026-05-15 21:04:00+08', NULL::TIMESTAMPTZ, NULL::TIMESTAMPTZ, NULL::TIMESTAMPTZ,
        NULL::SMALLINT, NULL::TEXT, NULL::TIMESTAMPTZ,
        '后端面试中回答缓存和数据库事务时容易绕，想做一次图文复盘。',
        '导师已回复，学生尚未确认关闭，支撑导师端多导师订单中心。',
        'INTERVIEW_RECORD,PROJECT_MATERIAL',
        ARRAY['缓存和事务怎么分层讲', '回答太长时怎么收束']::TEXT[],
        ARRAY['形成后端面试复盘清单', '演示导师回复消息']::TEXT[]
      ),
      (
        'CONS-DEF-20260517-012', 'LINJIAQI', 'LINQIAO', 14900, 'CLOSED',
        'PROJECT_STORYTELLING', '项目表达', '标准图文咨询', 'TEXT_ASYNC', NULL, 'MENTOR_MARKETPLACE',
        TIMESTAMPTZ '2026-05-11 19:10:00+08', TIMESTAMPTZ '2026-05-13 22:15:00+08',
        TIMESTAMPTZ '2026-05-11 19:18:00+08', TIMESTAMPTZ '2026-05-13 22:15:00+08', NULL::TIMESTAMPTZ, NULL::TIMESTAMPTZ,
        5::SMALLINT, '林乔老师帮我把前端性能优化和增长分析两条线拆开了，简历表达更稳。', TIMESTAMPTZ '2026-05-13 22:10:00+08',
        '前端和增长双线投递时，希望导师帮我把项目亮点拆成两版表达。',
        '其他学生完整完成单，支撑社区和导师评分的丰富度。',
        'RESUME,PROJECT_MATERIAL',
        ARRAY['前端和增长两版表达如何区分', '项目指标怎么讲得可信']::TEXT[],
        ARRAY['得到两版项目表达', '补充已评价订单样例']::TEXT[]
      ),
      (
        'CONS-DEF-20260517-013', 'LIUJIANING', 'GUHANG', 9900, 'PAYING',
        'RESUME_DIAGNOSIS', '简历诊断', '简历问诊', 'TEXT_ASYNC', NULL, 'MENTOR_MARKETPLACE_FAVORITES',
        TIMESTAMPTZ '2026-05-17 09:40:00+08', TIMESTAMPTZ '2026-05-17 09:42:00+08',
        NULL::TIMESTAMPTZ, NULL::TIMESTAMPTZ, NULL::TIMESTAMPTZ, NULL::TIMESTAMPTZ,
        NULL::SMALLINT, NULL::TEXT, NULL::TIMESTAMPTZ,
        '后端春招最后一轮投递前，希望快速让顾航航老师看一遍简历。',
        '第二个 PAYING 样例，保证支付列表里能看到多笔待回调订单。',
        'RESUME',
        ARRAY['简历主项目是否需要再压缩', '实习经历弱时如何补项目成果']::TEXT[],
        ARRAY['演示支付中状态', '补充学生订单列表多样性']::TEXT[]
      ),
      (
        'CONS-DEF-20260517-014', 'ZHOUMUYANG', 'HEJIN', 13900, 'CREATED',
        'DATA_ANALYSIS', '数据分析', '标准图文咨询', 'TEXT_ASYNC', NULL, 'MENTOR_MARKETPLACE',
        TIMESTAMPTZ '2026-05-17 09:50:00+08', TIMESTAMPTZ '2026-05-17 09:50:00+08',
        NULL::TIMESTAMPTZ, NULL::TIMESTAMPTZ, NULL::TIMESTAMPTZ, NULL::TIMESTAMPTZ,
        NULL::SMALLINT, NULL::TEXT, NULL::TIMESTAMPTZ,
        '数据分析岗案例分析写得像课程作业，希望导师帮忙改成业务分析表达。',
        '未支付但仍在 30 分钟有效期内的 CREATED 样例。',
        'RESUME,PROJECT_MATERIAL',
        ARRAY['分析案例如何体现业务判断', 'SQL 和可视化成果怎么排序']::TEXT[],
        ARRAY['演示未支付有效期', '支撑任务大厅学生侧数据']::TEXT[]
      ),
      (
        'CONS-DEF-20260517-015', 'ANRAN', 'GUHANG', 14900, 'PAID',
        'CAMPUS_RECRUITMENT_STRATEGY', '校招投递策略', '校招策略梳理', 'APPOINTMENT', 45, 'MENTOR_MARKETPLACE',
        TIMESTAMPTZ '2026-05-17 08:55:00+08', TIMESTAMPTZ '2026-05-17 09:05:00+08',
        TIMESTAMPTZ '2026-05-17 09:05:00+08', NULL::TIMESTAMPTZ, TIMESTAMPTZ '2026-05-17 16:00:00+08', TIMESTAMPTZ '2026-05-17 16:45:00+08',
        NULL::SMALLINT, NULL::TEXT, NULL::TIMESTAMPTZ,
        '今天下午想把答辩演示和求职投递时间安排一起梳理，避免两边都失控。',
        '答辩当天预约型已支付订单，用于展示当天日程和导师待履约状态。',
        'PPT_OUTLINE,RESUME',
        ARRAY['答辩前后如何安排投递动作', '哪些材料今天必须完成', '预约订单如何进入导师工作区']::TEXT[],
        ARRAY['形成当天行动清单', '展示预约时段占用']::TEXT[]
      ),
      (
        'CONS-DEF-20260517-016', 'ANRAN', 'GUHANG', 16900, 'ANSWERED',
        'PROJECT_STORYTELLING', '项目表达', '项目表达深挖', 'APPOINTMENT', 45, 'MENTOR_MARKETPLACE',
        TIMESTAMPTZ '2026-05-14 19:30:00+08', TIMESTAMPTZ '2026-05-16 22:30:00+08',
        TIMESTAMPTZ '2026-05-14 19:36:00+08', NULL::TIMESTAMPTZ, TIMESTAMPTZ '2026-05-16 20:00:00+08', TIMESTAMPTZ '2026-05-16 20:45:00+08',
        NULL::SMALLINT, NULL::TEXT, NULL::TIMESTAMPTZ,
        '导师已经给了项目表达建议，但我对其中一处企业任务模块的解释还有疑问，准备发起售后沟通。',
        '已回复但存在待处理售后申请，展示订单详情里的售后入口和后台待办。',
        'PROJECT_MATERIAL,PPT_OUTLINE',
        ARRAY['企业任务模块该不该作为创新点', '售后申请如何进入平台审核']::TEXT[],
        ARRAY['演示售后待处理', '补充后台咨询治理样例']::TEXT[]
      ),
      (
        'CONS-DEF-20260517-017', 'CHENSIYU', 'QIUYU', 16900, 'CLOSED',
        'PROJECT_STORYTELLING', '项目表达', '标准图文咨询', 'TEXT_ASYNC', NULL, 'MENTOR_MARKETPLACE',
        TIMESTAMPTZ '2026-05-10 20:00:00+08', TIMESTAMPTZ '2026-05-12 21:30:00+08',
        TIMESTAMPTZ '2026-05-10 20:08:00+08', TIMESTAMPTZ '2026-05-12 21:30:00+08', NULL::TIMESTAMPTZ, NULL::TIMESTAMPTZ,
        4::SMALLINT, '邱聿老师把项目追问拆成数据库、缓存和通知三类，比较适合准备面试。', TIMESTAMPTZ '2026-05-12 21:25:00+08',
        '推荐系统项目里同时有画像和企业任务，希望导师帮我拆成更容易讲的结构。',
        '其他导师完成单，丰富导师端财务和后台订单统计。',
        'RESUME,PROJECT_MATERIAL',
        ARRAY['画像和推荐怎么分开讲', '如何处理工程追问']::TEXT[],
        ARRAY['补充已完成评价样例', '增强导师评价分布']::TEXT[]
      ),
      (
        'CONS-DEF-20260517-018', 'HEQINGYAN', 'GUHANG', 17900, 'REFUNDED',
        'MOCK_INTERVIEW_REVIEW', '模拟面试复盘', '模拟面试复盘', 'APPOINTMENT', 45, 'MENTOR_MARKETPLACE_RECOMMENDATION',
        TIMESTAMPTZ '2026-05-13 18:40:00+08', TIMESTAMPTZ '2026-05-15 11:30:00+08',
        TIMESTAMPTZ '2026-05-13 18:45:00+08', TIMESTAMPTZ '2026-05-15 11:30:00+08', TIMESTAMPTZ '2026-05-14 19:30:00+08', TIMESTAMPTZ '2026-05-14 20:15:00+08',
        NULL::SMALLINT, NULL::TEXT, NULL::TIMESTAMPTZ,
        '面试复盘后学生认为预约内容没有覆盖材料中最关心的问题，申请平台审核退款。',
        '人工售后退款样例，区别于自动导师超时退款。',
        'INTERVIEW_RECORD,PROJECT_MATERIAL',
        ARRAY['人工售后和自动售后怎么区分', '退款记录如何和支付记录对应']::TEXT[],
        ARRAY['演示人工售后通过', '展示 REFUND_SUCCESS 记录']::TEXT[]
      ),
      (
        'CONS-DEF-20260517-019', 'LINJIAQI', 'GUHANG', 14900, 'CANCELED',
        'CAMPUS_RECRUITMENT_STRATEGY', '校招投递策略', '校招策略梳理', 'APPOINTMENT', 45, 'MENTOR_MARKETPLACE',
        TIMESTAMPTZ '2026-05-16 12:00:00+08', TIMESTAMPTZ '2026-05-16 12:18:00+08',
        NULL::TIMESTAMPTZ, TIMESTAMPTZ '2026-05-16 12:18:00+08', TIMESTAMPTZ '2026-05-18 20:00:00+08', TIMESTAMPTZ '2026-05-18 20:45:00+08',
        NULL::SMALLINT, NULL::TEXT, NULL::TIMESTAMPTZ,
        '学生创建预约后发现时间冲突，主动取消未支付订单。',
        '学生主动取消未支付订单，用于区别系统超时取消。',
        'RESUME',
        ARRAY['学生取消和系统超时取消有什么差别', '预约时段如何释放']::TEXT[],
        ARRAY['演示手动取消终态', '补充取消通知样例']::TEXT[]
      ),
      (
        'CONS-DEF-20260517-020', 'ANRAN', 'GUHANG', 9900, 'CLOSED',
        'RESUME_DIAGNOSIS', '简历诊断', '简历问诊', 'TEXT_ASYNC', NULL, 'MENTOR_MARKETPLACE',
        TIMESTAMPTZ '2026-05-09 21:10:00+08', TIMESTAMPTZ '2026-05-11 22:10:00+08',
        TIMESTAMPTZ '2026-05-09 21:16:00+08', TIMESTAMPTZ '2026-05-11 22:10:00+08', NULL::TIMESTAMPTZ, NULL::TIMESTAMPTZ,
        4::SMALLINT, '这次先解决了简历结构问题，后续项目表达还需要再单独约一次。', TIMESTAMPTZ '2026-05-11 22:05:00+08',
        '答辩前一周先做了一轮简历快诊，用于和 5 月 17 日当天订单形成时间线。',
        '安然历史完成单，支撑学生个人订单时间线和导师评价。',
        'RESUME',
        ARRAY['简历第一屏该保留哪些信息', '项目表达后续是否需要单独咨询']::TEXT[],
        ARRAY['展示历史完成订单', '补充学生评价样例']::TEXT[]
      )
) AS v(
    order_no, student_key, mentor_key, amount_fen, status,
    scene_code, scene_label, package_name, delivery_mode, duration_minutes, source_page,
    created_at, updated_at, paid_at, closed_at, appointment_start_at, appointment_end_at,
    review_rating, review_comment, review_created_at, question_text, problem_summary,
    selected_material_types, core_questions, expected_outcomes
)
CROSS JOIN tmp_defense_users u;

-- 清理上一次同前缀演示数据，避免重复执行造成脏数据。
DELETE FROM notification_dispatch_attempts
 WHERE job_id IN (
    SELECT id FROM notification_dispatch_jobs
     WHERE job_id LIKE 'def25-dj-%'
        OR event_id LIKE 'defense-20260517-%'
 );

DELETE FROM notification_dispatch_jobs
 WHERE job_id LIKE 'def25-dj-%'
    OR event_id LIKE 'defense-20260517-%';

DELETE FROM notifications
 WHERE event_id LIKE 'defense-20260517-%'
    OR ref_id LIKE 'CONS-DEF-20260517-%'
    OR payload_json LIKE '%DEFENSE-20260517%';

DELETE FROM notification_events
 WHERE event_id LIKE 'defense-20260517-%'
    OR dedupe_key LIKE 'defense-20260517-%'
    OR payload_json LIKE '%DEFENSE-20260517%';

DELETE FROM audit_logs
 WHERE trace_id LIKE 'defense-20260517-%'
    OR detail_json LIKE '%DEFENSE-20260517%';

DELETE FROM content_report_actions
 WHERE report_id IN (
    SELECT id
      FROM content_reports
     WHERE detail LIKE '%DEFENSE-20260517%'
        OR target_id IN (
            SELECT id::TEXT FROM posts WHERE title LIKE '[答辩演示]%'
        )
        OR target_id IN (
            SELECT c.id::TEXT
              FROM comments c
              JOIN posts p ON p.id = c.post_id
             WHERE p.title LIKE '[答辩演示]%'
        )
 );

DELETE FROM content_reports
 WHERE detail LIKE '%DEFENSE-20260517%'
    OR target_id IN (SELECT id::TEXT FROM posts WHERE title LIKE '[答辩演示]%')
    OR target_id IN (
        SELECT c.id::TEXT
          FROM comments c
          JOIN posts p ON p.id = c.post_id
         WHERE p.title LIKE '[答辩演示]%'
    );

DELETE FROM content_moderation_events
 WHERE trace_id LIKE 'defense-20260517-%'
    OR masked_text LIKE '%DEFENSE-20260517%';

DELETE FROM post_likes
 WHERE post_id IN (SELECT id FROM posts WHERE title LIKE '[答辩演示]%');

DELETE FROM comments
 WHERE post_id IN (SELECT id FROM posts WHERE title LIKE '[答辩演示]%');

DELETE FROM posts
 WHERE title LIKE '[答辩演示]%';

UPDATE bounty_tasks
   SET accepted_submission_id = NULL
 WHERE title LIKE '[答辩演示]%';

DELETE FROM bounty_submission_events
 WHERE task_id IN (SELECT id FROM bounty_tasks WHERE title LIKE '[答辩演示]%');

DELETE FROM bounty_submissions
 WHERE task_id IN (SELECT id FROM bounty_tasks WHERE title LIKE '[答辩演示]%');

DELETE FROM bounty_tasks
 WHERE title LIKE '[答辩演示]%';

DELETE FROM consult_after_sales_requests
 WHERE order_no LIKE 'CONS-DEF-20260517-%';

DELETE FROM payment_records
 WHERE order_no LIKE 'CONS-DEF-20260517-%'
    OR idempotency_key LIKE 'DEF20260517:%';

DELETE FROM consult_order_attachments
 WHERE order_no LIKE 'CONS-DEF-20260517-%'
    OR object_key LIKE 'defense/20260517/%';

DELETE FROM consult_messages
 WHERE order_no LIKE 'CONS-DEF-20260517-%';

DELETE FROM mentor_schedule_slots
 WHERE booked_order_no LIKE 'CONS-DEF-20260517-%'
    OR (
        mentor_user_id IN (
            SELECT guhang_user_id FROM tmp_defense_users
            UNION ALL SELECT hanxue_user_id FROM tmp_defense_users
            UNION ALL SELECT chengyanbei_user_id FROM tmp_defense_users
            UNION ALL SELECT linqiao_user_id FROM tmp_defense_users
            UNION ALL SELECT hejin_user_id FROM tmp_defense_users
        )
        AND start_at >= TIMESTAMPTZ '2026-05-17 00:00:00+08'
        AND start_at < TIMESTAMPTZ '2026-05-23 00:00:00+08'
    );

DELETE FROM consult_orders
 WHERE order_no LIKE 'CONS-DEF-20260517-%';

DELETE FROM ai_async_task_events
 WHERE task_id LIKE 'aitk_defense_20260517_%'
    OR event_id LIKE 'defense-20260517-ai-%';

DELETE FROM ai_async_task_jobs
 WHERE task_id LIKE 'aitk_defense_20260517_%';

DELETE FROM ai_call_logs
 WHERE trace_id LIKE 'defense-20260517-ai-%';

DELETE FROM interview_messages
 WHERE session_pk IN (
    SELECT id FROM interview_sessions WHERE session_id LIKE 'ivs_def_20260517_%'
 );

DELETE FROM interview_sessions
 WHERE session_id LIKE 'ivs_def_20260517_%';

DELETE FROM points_ledger
 WHERE reason_code LIKE 'DEFENSE_20260517_%';

DELETE FROM mentor_withdrawal_requests
 WHERE note LIKE '[答辩演示]%';

-- 1. 咨询订单、支付、时段、消息、附件和售后。
INSERT INTO consult_orders (
    order_no, student_user_id, mentor_user_id, amount_fen, status, question_text,
    paid_at, closed_at, created_at, updated_at, appointment_start_at, appointment_end_at,
    scene_code, source_page, question_payload_json, problem_summary, core_questions_json,
    expected_outcomes_json, selected_material_types, prep_sheet_snapshot_json,
    service_package_snapshot_json, review_rating, review_comment, review_created_at
)
SELECT
    o.order_no,
    o.student_user_id,
    o.mentor_user_id,
    o.amount_fen,
    o.status,
    o.question_text,
    o.paid_at,
    o.closed_at,
    o.created_at,
    o.updated_at,
    o.appointment_start_at,
    o.appointment_end_at,
    o.scene_code,
    o.source_page,
    jsonb_build_object(
        'background', o.question_text,
        'expectedHelp', o.problem_summary,
        'primaryConcern', o.core_questions[1],
        'additionalNotes', 'DEFENSE-20260517：答辩演示订单，围绕真实求职和项目表达场景构造。',
        'attemptedActions', '学生已整理简历、项目材料或面试记录，准备让导师做最后一轮结构化反馈。'
    )::TEXT,
    o.problem_summary,
    to_jsonb(o.core_questions)::TEXT,
    to_jsonb(o.expected_outcomes)::TEXT,
    o.selected_material_types,
    jsonb_build_object(
        'scene', o.scene_label,
        'summaryDraft', o.problem_summary,
        'coreQuestions', to_jsonb(o.core_questions),
        'suggestedMaterials', string_to_array(COALESCE(o.selected_material_types, ''), ','),
        'expectedOutcomes', to_jsonb(o.expected_outcomes),
        'demoTag', 'DEFENSE-20260517'
    )::TEXT,
    jsonb_build_object(
        'packageName', o.package_name,
        'sceneCode', o.scene_code,
        'sceneLabel', o.scene_label,
        'deliveryMode', o.delivery_mode,
        'durationMinutes', o.duration_minutes,
        'priceFen', o.amount_fen,
        'demoTag', 'DEFENSE-20260517'
    )::TEXT,
    o.review_rating,
    o.review_comment,
    o.review_created_at
FROM tmp_defense_orders o
ON CONFLICT (order_no) DO UPDATE SET
    student_user_id = EXCLUDED.student_user_id,
    mentor_user_id = EXCLUDED.mentor_user_id,
    amount_fen = EXCLUDED.amount_fen,
    status = EXCLUDED.status,
    question_text = EXCLUDED.question_text,
    paid_at = EXCLUDED.paid_at,
    closed_at = EXCLUDED.closed_at,
    created_at = EXCLUDED.created_at,
    updated_at = EXCLUDED.updated_at,
    appointment_start_at = EXCLUDED.appointment_start_at,
    appointment_end_at = EXCLUDED.appointment_end_at,
    scene_code = EXCLUDED.scene_code,
    source_page = EXCLUDED.source_page,
    question_payload_json = EXCLUDED.question_payload_json,
    problem_summary = EXCLUDED.problem_summary,
    core_questions_json = EXCLUDED.core_questions_json,
    expected_outcomes_json = EXCLUDED.expected_outcomes_json,
    selected_material_types = EXCLUDED.selected_material_types,
    prep_sheet_snapshot_json = EXCLUDED.prep_sheet_snapshot_json,
    service_package_snapshot_json = EXCLUDED.service_package_snapshot_json,
    review_rating = EXCLUDED.review_rating,
    review_comment = EXCLUDED.review_comment,
    review_created_at = EXCLUDED.review_created_at;

CREATE TEMP TABLE tmp_defense_order_ids AS
SELECT o.*, co.id AS order_id
  FROM tmp_defense_orders o
  JOIN consult_orders co ON co.order_no = o.order_no;

CREATE TEMP TABLE tmp_defense_slots (
    mentor_key TEXT NOT NULL,
    mentor_user_id BIGINT NOT NULL,
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    status TEXT NOT NULL,
    order_no TEXT
);

INSERT INTO tmp_defense_slots (mentor_key, mentor_user_id, start_at, end_at, status, order_no)
SELECT
    v.mentor_key,
    CASE v.mentor_key
        WHEN 'GUHANG' THEN u.guhang_user_id
        WHEN 'HANXUE' THEN u.hanxue_user_id
        WHEN 'CHENGYANBEI' THEN u.chengyanbei_user_id
        WHEN 'LINQIAO' THEN u.linqiao_user_id
        WHEN 'HEJIN' THEN u.hejin_user_id
    END,
    v.start_at,
    v.end_at,
    v.status,
    v.order_no
FROM (
    VALUES
      ('GUHANG', TIMESTAMPTZ '2026-05-17 15:00:00+08', TIMESTAMPTZ '2026-05-17 15:45:00+08', 'BOOKED', 'CONS-DEF-20260517-004'),
      ('GUHANG', TIMESTAMPTZ '2026-05-17 16:00:00+08', TIMESTAMPTZ '2026-05-17 16:45:00+08', 'BOOKED', 'CONS-DEF-20260517-015'),
      ('GUHANG', TIMESTAMPTZ '2026-05-18 19:30:00+08', TIMESTAMPTZ '2026-05-18 20:15:00+08', 'BOOKED', 'CONS-DEF-20260517-002'),
      ('GUHANG', TIMESTAMPTZ '2026-05-19 20:00:00+08', TIMESTAMPTZ '2026-05-19 20:45:00+08', 'BOOKED', 'CONS-DEF-20260517-007'),
      ('GUHANG', TIMESTAMPTZ '2026-05-16 20:00:00+08', TIMESTAMPTZ '2026-05-16 20:45:00+08', 'BOOKED', 'CONS-DEF-20260517-016'),
      ('GUHANG', TIMESTAMPTZ '2026-05-14 19:30:00+08', TIMESTAMPTZ '2026-05-14 20:15:00+08', 'BOOKED', 'CONS-DEF-20260517-018'),
      ('GUHANG', TIMESTAMPTZ '2026-05-18 20:00:00+08', TIMESTAMPTZ '2026-05-18 20:45:00+08', 'AVAILABLE', NULL),
      ('GUHANG', TIMESTAMPTZ '2026-05-19 19:00:00+08', TIMESTAMPTZ '2026-05-19 19:45:00+08', 'AVAILABLE', NULL),
      ('GUHANG', TIMESTAMPTZ '2026-05-20 20:00:00+08', TIMESTAMPTZ '2026-05-20 20:45:00+08', 'AVAILABLE', NULL),
      ('GUHANG', TIMESTAMPTZ '2026-05-21 20:00:00+08', TIMESTAMPTZ '2026-05-21 20:45:00+08', 'AVAILABLE', NULL),
      ('HANXUE', TIMESTAMPTZ '2026-05-18 19:30:00+08', TIMESTAMPTZ '2026-05-18 20:15:00+08', 'AVAILABLE', NULL),
      ('HANXUE', TIMESTAMPTZ '2026-05-19 20:00:00+08', TIMESTAMPTZ '2026-05-19 20:45:00+08', 'AVAILABLE', NULL),
      ('CHENGYANBEI', TIMESTAMPTZ '2026-05-18 21:00:00+08', TIMESTAMPTZ '2026-05-18 21:45:00+08', 'AVAILABLE', NULL),
      ('LINQIAO', TIMESTAMPTZ '2026-05-20 19:30:00+08', TIMESTAMPTZ '2026-05-20 20:15:00+08', 'AVAILABLE', NULL),
      ('HEJIN', TIMESTAMPTZ '2026-05-20 20:30:00+08', TIMESTAMPTZ '2026-05-20 21:15:00+08', 'AVAILABLE', NULL)
) AS v(mentor_key, start_at, end_at, status, order_no)
CROSS JOIN tmp_defense_users u;

INSERT INTO mentor_schedule_slots (
    mentor_user_id, start_at, end_at, status, booked_order_no, created_at, updated_at, booked_order_id
)
SELECT
    s.mentor_user_id,
    s.start_at,
    s.end_at,
    s.status,
    s.order_no,
    TIMESTAMPTZ '2026-05-10 10:00:00+08',
    TIMESTAMPTZ '2026-05-17 10:00:00+08',
    oi.order_id
FROM tmp_defense_slots s
LEFT JOIN tmp_defense_order_ids oi ON oi.order_no = s.order_no
ON CONFLICT (mentor_user_id, start_at, end_at) DO UPDATE SET
    status = EXCLUDED.status,
    booked_order_no = EXCLUDED.booked_order_no,
    booked_order_id = EXCLUDED.booked_order_id,
    updated_at = EXCLUDED.updated_at;

INSERT INTO payment_records (
    order_no, channel, mode, provider_trade_no, amount_fen, status,
    idempotency_key, raw_callback, created_at, updated_at, order_id
)
SELECT
    o.order_no,
    'ALIPAY',
    'SANDBOX',
    'defense-init-' || o.order_no,
    o.amount_fen,
    'INIT',
    'DEF20260517:INIT:' || o.order_no,
    jsonb_build_object('demoTag', 'DEFENSE-20260517', 'stage', 'INIT', 'orderNo', o.order_no)::TEXT,
    o.created_at + INTERVAL '2 minutes',
    o.created_at + INTERVAL '2 minutes',
    o.order_id
FROM tmp_defense_order_ids o
WHERE o.status IN ('PAYING', 'PAID', 'ANSWERED', 'CLOSED', 'REFUNDED')
ON CONFLICT (idempotency_key) DO UPDATE SET
    status = EXCLUDED.status,
    raw_callback = EXCLUDED.raw_callback,
    updated_at = EXCLUDED.updated_at,
    order_id = EXCLUDED.order_id;

INSERT INTO payment_records (
    order_no, channel, mode, provider_trade_no, amount_fen, status,
    idempotency_key, raw_callback, created_at, updated_at, order_id
)
SELECT
    o.order_no,
    'ALIPAY',
    'SANDBOX',
    'defense-success-' || o.order_no,
    o.amount_fen,
    'SUCCESS',
    'DEF20260517:SUCCESS:' || o.order_no,
    jsonb_build_object('demoTag', 'DEFENSE-20260517', 'stage', 'SUCCESS', 'tradeStatus', 'TRADE_SUCCESS', 'orderNo', o.order_no)::TEXT,
    o.paid_at,
    o.paid_at,
    o.order_id
FROM tmp_defense_order_ids o
WHERE o.paid_at IS NOT NULL
ON CONFLICT (idempotency_key) DO UPDATE SET
    status = EXCLUDED.status,
    raw_callback = EXCLUDED.raw_callback,
    updated_at = EXCLUDED.updated_at,
    order_id = EXCLUDED.order_id;

INSERT INTO payment_records (
    order_no, channel, mode, provider_trade_no, amount_fen, status,
    idempotency_key, raw_callback, created_at, updated_at, order_id
)
SELECT
    o.order_no,
    'ALIPAY',
    'SANDBOX',
    'defense-refund-' || o.order_no,
    o.amount_fen,
    'REFUND_SUCCESS',
    'DEF20260517:REFUND:' || o.order_no,
    jsonb_build_object('demoTag', 'DEFENSE-20260517', 'stage', 'REFUND_SUCCESS', 'orderNo', o.order_no)::TEXT,
    o.closed_at,
    o.closed_at,
    o.order_id
FROM tmp_defense_order_ids o
WHERE o.status = 'REFUNDED'
ON CONFLICT (idempotency_key) DO UPDATE SET
    status = EXCLUDED.status,
    raw_callback = EXCLUDED.raw_callback,
    updated_at = EXCLUDED.updated_at,
    order_id = EXCLUDED.order_id;

CREATE TEMP TABLE tmp_defense_messages (
    order_no TEXT NOT NULL,
    sender_key TEXT NOT NULL,
    sender_user_id BIGINT NOT NULL,
    sender_role TEXT NOT NULL,
    message_text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

INSERT INTO tmp_defense_messages (order_no, sender_key, sender_user_id, sender_role, message_text, created_at)
SELECT
    v.order_no,
    v.sender_key,
    CASE v.sender_key
        WHEN 'ANRAN' THEN u.anran_user_id
        WHEN 'GUHANG' THEN u.guhang_user_id
        WHEN 'HANXUE' THEN u.hanxue_user_id
        WHEN 'CHENGYANBEI' THEN u.chengyanbei_user_id
        WHEN 'LINQIAO' THEN u.linqiao_user_id
        WHEN 'QIUYU' THEN u.qiuyu_user_id
        WHEN 'HEQINGYAN' THEN u.heqingyan_user_id
        WHEN 'LINJIAQI' THEN u.linjiaqi_user_id
        WHEN 'CHENSIYU' THEN u.chensiyu_user_id
    END,
    v.sender_role,
    v.message_text,
    v.created_at
FROM (
    VALUES
      ('CONS-DEF-20260517-004', 'ANRAN', 'STUDENT', '老师，我把昨天模拟面试的录音和简历都补上了，主要想看项目性能优化这段怎么讲。', TIMESTAMPTZ '2026-05-16 18:25:00+08'),
      ('CONS-DEF-20260517-004', 'GUHANG', 'MENTOR', '我先看了你的回答，问题不是技术点不够，而是开头没有先说明业务约束。建议用“用户进入页面慢 -> 定位渲染瓶颈 -> 拆分动效层级 -> 验证前后帧率”这条线讲。', TIMESTAMPTZ '2026-05-17 09:20:00+08'),
      ('CONS-DEF-20260517-004', 'GUHANG', 'MENTOR', '如果被追问技术细节，可以重点讲首屏动画暂停策略、离屏动效收敛和卡片文字清晰度这三处取舍。', TIMESTAMPTZ '2026-05-17 09:24:00+08'),
      ('CONS-DEF-20260517-005', 'ANRAN', 'STUDENT', '这是我整理的 8 分钟项目讲述稿，想请老师帮我确认模块顺序。', TIMESTAMPTZ '2026-05-13 21:00:00+08'),
      ('CONS-DEF-20260517-005', 'GUHANG', 'MENTOR', '你的主线可以从“学生求职准备”切入，不要一开始就讲技术栈。先讲角色、再讲导师咨询和企业任务，最后落到 AI 网关和通知回流。', TIMESTAMPTZ '2026-05-14 21:00:00+08'),
      ('CONS-DEF-20260517-005', 'ANRAN', 'STUDENT', '收到，我按这个顺序把 PPT 第 15-20 页重新对齐了。', TIMESTAMPTZ '2026-05-15 20:40:00+08'),
      ('CONS-DEF-20260517-011', 'HEQINGYAN', 'STUDENT', '我现在回答缓存和事务时容易混在一起，想知道怎么按面试问题分层。', TIMESTAMPTZ '2026-05-15 21:20:00+08'),
      ('CONS-DEF-20260517-011', 'CHENGYANBEI', 'MENTOR', '可以先把问题分成一致性、性能和失败恢复三类，每类只讲一个具体场景，不要把所有中间件一起堆上去。', TIMESTAMPTZ '2026-05-16 18:25:00+08'),
      ('CONS-DEF-20260517-012', 'LINJIAQI', 'STUDENT', '我想把前端和增长分析拆成两版简历，但担心看起来目标不集中。', TIMESTAMPTZ '2026-05-11 19:30:00+08'),
      ('CONS-DEF-20260517-012', 'LINQIAO', 'MENTOR', '建议保留同一个项目，但准备两种摘要：前端版强调组件和性能，增长版强调指标口径和验证路径。', TIMESTAMPTZ '2026-05-12 12:10:00+08'),
      ('CONS-DEF-20260517-016', 'ANRAN', 'STUDENT', '老师，我按建议改了企业任务模块说明，但还是担心答辩老师问“企业真实参与度”时不好回答。', TIMESTAMPTZ '2026-05-15 19:50:00+08'),
      ('CONS-DEF-20260517-016', 'GUHANG', 'MENTOR', '这个问题可以直接讲“轻量企业任务”边界：企业提供任务和审核反馈，平台通过提交、事件、通知和后台治理保证闭环，不需要夸成真实招聘系统。', TIMESTAMPTZ '2026-05-16 22:20:00+08'),
      ('CONS-DEF-20260517-017', 'CHENSIYU', 'STUDENT', '我想把画像和推荐系统讲成一个完整模块，但怕技术细节太散。', TIMESTAMPTZ '2026-05-10 20:30:00+08'),
      ('CONS-DEF-20260517-017', 'QIUYU', 'MENTOR', '建议拆成画像事实层、推荐召回层和解释回流层，答辩时每层只举一个数据来源。', TIMESTAMPTZ '2026-05-11 16:20:00+08'),
      ('CONS-DEF-20260517-020', 'ANRAN', 'STUDENT', '我先发一版简历，想确认第一屏是不是太满。', TIMESTAMPTZ '2026-05-09 21:20:00+08'),
      ('CONS-DEF-20260517-020', 'GUHANG', 'MENTOR', '第一屏要减掉泛技术栈，保留目标岗位、主项目成果和一个可量化指标。其他内容放到项目经历细节里。', TIMESTAMPTZ '2026-05-10 12:30:00+08')
) AS v(order_no, sender_key, sender_role, message_text, created_at)
CROSS JOIN tmp_defense_users u;

INSERT INTO consult_messages (order_no, sender_user_id, sender_role, message_text, created_at, order_id)
SELECT m.order_no, m.sender_user_id, m.sender_role, m.message_text, m.created_at, oi.order_id
  FROM tmp_defense_messages m
  JOIN tmp_defense_order_ids oi ON oi.order_no = m.order_no;

CREATE TEMP TABLE tmp_defense_attachments (
    order_no TEXT NOT NULL,
    uploader_key TEXT NOT NULL,
    uploaded_by_user_id BIGINT NOT NULL,
    attachment_type TEXT NOT NULL,
    slot_code TEXT NOT NULL,
    source_stage TEXT NOT NULL,
    original_filename TEXT NOT NULL,
    content_type TEXT NOT NULL,
    size_bytes BIGINT NOT NULL,
    object_key TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

INSERT INTO tmp_defense_attachments (
    order_no, uploader_key, uploaded_by_user_id, attachment_type, slot_code, source_stage,
    original_filename, content_type, size_bytes, object_key, description, created_at
)
SELECT
    v.order_no,
    v.uploader_key,
    CASE v.uploader_key
        WHEN 'ANRAN' THEN u.anran_user_id
        WHEN 'HEQINGYAN' THEN u.heqingyan_user_id
        WHEN 'LINJIAQI' THEN u.linjiaqi_user_id
        WHEN 'CHENSIYU' THEN u.chensiyu_user_id
    END,
    v.attachment_type,
    v.slot_code,
    v.source_stage,
    v.original_filename,
    v.content_type,
    v.size_bytes,
    v.object_key,
    v.description,
    v.created_at
FROM (
    VALUES
      ('CONS-DEF-20260517-003', 'ANRAN', 'RESUME', 'primary-resume', 'ORDER_CREATE', '安然_前端增长方向简历_v5.pdf', 'application/pdf', 428320, 'defense/20260517/consult/anran_resume_v5.pdf', '投递前最终版简历', TIMESTAMPTZ '2026-05-17 08:42:00+08'),
      ('CONS-DEF-20260517-004', 'ANRAN', 'INTERVIEW_RECORD', 'mock-interview-record', 'ORDER_CREATE', '模拟面试录音转写_前端性能优化.docx', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 218760, 'defense/20260517/consult/anran_mock_interview_notes.docx', '模拟面试转写与追问记录', TIMESTAMPTZ '2026-05-16 18:20:00+08'),
      ('CONS-DEF-20260517-005', 'ANRAN', 'PROJECT_MATERIAL', 'project-outline', 'ORDER_CREATE', '毕业设计项目讲述提纲_v3.md', 'text/markdown', 82340, 'defense/20260517/consult/anran_project_outline_v3.md', '答辩项目讲述提纲', TIMESTAMPTZ '2026-05-13 20:55:00+08'),
      ('CONS-DEF-20260517-005', 'ANRAN', 'PPT_OUTLINE', 'defense-ppt', 'ORDER_CREATE', '答辩PPT页面规划_安然版.pdf', 'application/pdf', 612480, 'defense/20260517/consult/anran_defense_ppt_plan.pdf', '答辩 PPT 页面规划', TIMESTAMPTZ '2026-05-13 20:56:00+08'),
      ('CONS-DEF-20260517-011', 'HEQINGYAN', 'PROJECT_MATERIAL', 'backend-project', 'ORDER_CREATE', '后端项目复盘材料.pdf', 'application/pdf', 383210, 'defense/20260517/consult/heqingyan_backend_project.pdf', '后端项目复盘材料', TIMESTAMPTZ '2026-05-15 21:05:00+08'),
      ('CONS-DEF-20260517-012', 'LINJIAQI', 'RESUME', 'resume-growth-frontend', 'ORDER_CREATE', '林嘉琪_前端增长双线简历.pdf', 'application/pdf', 354120, 'defense/20260517/consult/linjiaqi_resume.pdf', '前端 / 增长双线简历', TIMESTAMPTZ '2026-05-11 19:15:00+08'),
      ('CONS-DEF-20260517-016', 'ANRAN', 'PROJECT_MATERIAL', 'enterprise-task-section', 'AFTER_SALES', '企业任务模块答辩说明补充.md', 'text/markdown', 65210, 'defense/20260517/consult/anran_enterprise_task_note.md', '售后沟通补充说明', TIMESTAMPTZ '2026-05-17 09:10:00+08'),
      ('CONS-DEF-20260517-017', 'CHENSIYU', 'PROJECT_MATERIAL', 'recommendation-project', 'ORDER_CREATE', '画像推荐项目复盘材料.pdf', 'application/pdf', 491120, 'defense/20260517/consult/chensiyu_reco_project.pdf', '画像推荐项目复盘材料', TIMESTAMPTZ '2026-05-10 20:05:00+08'),
      ('CONS-DEF-20260517-020', 'ANRAN', 'RESUME', 'resume-old', 'ORDER_CREATE', '安然_简历快诊版_v4.pdf', 'application/pdf', 402120, 'defense/20260517/consult/anran_resume_v4.pdf', '答辩前一周简历快诊版本', TIMESTAMPTZ '2026-05-09 21:14:00+08')
) AS v(order_no, uploader_key, attachment_type, slot_code, source_stage, original_filename, content_type, size_bytes, object_key, description, created_at)
CROSS JOIN tmp_defense_users u;

INSERT INTO consult_order_attachments (
    order_no, uploaded_by_user_id, attachment_type, slot_code, source_stage,
    original_filename, content_type, size_bytes, storage_bucket, object_key,
    description, lifecycle_status, replaced_attachment_id, created_at, updated_at, order_id
)
SELECT
    a.order_no,
    a.uploaded_by_user_id,
    a.attachment_type,
    a.slot_code,
    a.source_stage,
    a.original_filename,
    a.content_type,
    a.size_bytes,
    'bishe-assets',
    a.object_key,
    a.description,
    'CURRENT',
    NULL,
    a.created_at,
    a.created_at,
    oi.order_id
FROM tmp_defense_attachments a
JOIN tmp_defense_order_ids oi ON oi.order_no = a.order_no;

INSERT INTO consult_after_sales_requests (
    order_no, requester_user_id, request_type, status, reason, review_note,
    reviewer_user_id, auto_triggered, reviewed_at, created_at, updated_at, order_id
)
SELECT
    v.order_no,
    CASE v.requester_key
        WHEN 'ANRAN' THEN u.anran_user_id
        WHEN 'SUNZEYU' THEN u.sunzeyu_user_id
        WHEN 'HEQINGYAN' THEN u.heqingyan_user_id
    END,
    'REFUND',
    v.status,
    v.reason,
    v.review_note,
    CASE WHEN v.reviewer_key = 'ADMIN' THEN u.admin_user_id ELSE NULL END,
    v.auto_triggered,
    v.reviewed_at,
    v.created_at,
    v.updated_at,
    oi.order_id
FROM (
    VALUES
      ('CONS-DEF-20260517-006', 'ANRAN', 'APPROVED', '导师超过 24 小时未回复，系统自动发起售后退款。', '系统判定导师首复超时，自动通过并执行退款。', 'ADMIN', TRUE, TIMESTAMPTZ '2026-05-16 09:02:00+08', TIMESTAMPTZ '2026-05-16 09:00:00+08', TIMESTAMPTZ '2026-05-16 09:05:00+08'),
      ('CONS-DEF-20260517-010', 'SUNZEYU', 'APPROVED', '导师超过 24 小时未回复，学生未收到咨询结果。', '系统自动审批退款，保留导师超时记录。', 'ADMIN', TRUE, TIMESTAMPTZ '2026-05-16 09:58:00+08', TIMESTAMPTZ '2026-05-16 09:55:00+08', TIMESTAMPTZ '2026-05-16 10:00:00+08'),
      ('CONS-DEF-20260517-016', 'ANRAN', 'PENDING', '对导师回复中的企业任务模块解释仍有疑问，希望平台协助确认是否需要补充说明。', NULL::TEXT, NULL::TEXT, FALSE, NULL::TIMESTAMPTZ, TIMESTAMPTZ '2026-05-17 09:30:00+08', TIMESTAMPTZ '2026-05-17 09:30:00+08'),
      ('CONS-DEF-20260517-018', 'HEQINGYAN', 'APPROVED', '预约复盘没有覆盖学生最关心的面试材料，申请人工退款。', '平台审核后认定服务结果不完整，已执行人工退款。', 'ADMIN', FALSE, TIMESTAMPTZ '2026-05-15 11:20:00+08', TIMESTAMPTZ '2026-05-15 10:50:00+08', TIMESTAMPTZ '2026-05-15 11:30:00+08'),
      ('CONS-DEF-20260517-020', 'ANRAN', 'REJECTED', '希望对已完成的简历快诊追加退款。', '订单已完成且导师反馈内容完整，本次售后不予通过。', 'ADMIN', FALSE, TIMESTAMPTZ '2026-05-12 10:30:00+08', TIMESTAMPTZ '2026-05-12 09:40:00+08', TIMESTAMPTZ '2026-05-12 10:30:00+08')
) AS v(order_no, requester_key, status, reason, review_note, reviewer_key, auto_triggered, reviewed_at, created_at, updated_at)
CROSS JOIN tmp_defense_users u
JOIN tmp_defense_order_ids oi ON oi.order_no = v.order_no;

-- 2. 企业任务、学生提交与企业审核事件。
CREATE TEMP TABLE tmp_defense_tasks (
    task_key TEXT PRIMARY KEY,
    enterprise_key TEXT NOT NULL,
    enterprise_user_id BIGINT NOT NULL,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    reward_description TEXT NOT NULL,
    status TEXT NOT NULL,
    deadline_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    accepted_student_key TEXT
);

INSERT INTO tmp_defense_tasks (
    task_key, enterprise_key, enterprise_user_id, title, description, reward_description,
    status, deadline_at, closed_at, created_at, updated_at, accepted_student_key
)
SELECT
    v.task_key,
    v.enterprise_key,
    CASE v.enterprise_key
        WHEN 'BEICHEN' THEN u.beichen_user_id
        WHEN 'YUNQIAO' THEN u.yunqiao_user_id
    END,
    v.title,
    v.description,
    v.reward_description,
    v.status,
    v.deadline_at,
    v.closed_at,
    v.created_at,
    v.updated_at,
    v.accepted_student_key
FROM (
    VALUES
      ('frontend-polish', 'BEICHEN', '[答辩演示] 校招活动页首屏性能与动效优化复盘',
       '任务背景：北辰智联校招活动页首屏存在动效卡顿和信息卡片文字发糊问题。\n交付要求：请给出性能问题定位、可落地调整方案、验收指标和风险说明。\n适合人群：有 React / Vite / 前端性能优化经验的学生。\n参考资料：活动页截图、性能面板记录、现有 CSS 片段。',
       '企业内推优先沟通 + 300 元项目奖金', 'OPEN', TIMESTAMPTZ '2026-05-20 23:59:00+08', NULL::TIMESTAMPTZ, TIMESTAMPTZ '2026-05-13 10:00:00+08', TIMESTAMPTZ '2026-05-17 09:50:00+08', NULL::TEXT),
      ('growth-dashboard', 'BEICHEN', '[答辩演示] 试用转化日报摘要卡片与埋点核查方案',
       '任务背景：客户成功团队需要一页日报，快速看到试用客户转化风险。\n交付要求：设计摘要卡片、指标口径、埋点核查清单和异常处理说明。\n适合人群：前端、增长分析、数据产品方向学生。',
       '150 元项目奖金 + 简历项目反馈', 'OPEN', TIMESTAMPTZ '2026-05-17 23:59:00+08', NULL::TIMESTAMPTZ, TIMESTAMPTZ '2026-05-14 09:30:00+08', TIMESTAMPTZ '2026-05-17 09:40:00+08', NULL::TEXT),
      ('overdue-open', 'BEICHEN', '[答辩演示] 客户线索入库表单体验优化与错误态清单',
       '任务背景：销售线索录入表单存在字段过多、错误提示不清楚的问题。\n交付要求：输出表单分组、错误态说明、低保真原型和验收清单。\n说明：该任务截止时间早于答辩基线，用于后台风险识别演示。',
       '200 元项目奖金', 'OPEN', TIMESTAMPTZ '2026-05-15 23:59:00+08', NULL::TIMESTAMPTZ, TIMESTAMPTZ '2026-05-09 14:00:00+08', TIMESTAMPTZ '2026-05-17 09:00:00+08', NULL::TEXT),
      ('accepted-anran', 'BEICHEN', '[答辩演示] 企业知识库智能检索结果解释页',
       '任务背景：知识库检索命中结果较多，用户不知道为什么推荐这些内容。\n交付要求：给出结果解释区布局、排序依据文案、空状态与异常状态。\n说明：该任务已采纳安然的方案，用于演示企业审核闭环。',
       '500 元项目奖金 + 企业导师反馈', 'CLOSED', TIMESTAMPTZ '2026-05-16 23:59:00+08', TIMESTAMPTZ '2026-05-17 09:10:00+08', TIMESTAMPTZ '2026-05-10 11:00:00+08', TIMESTAMPTZ '2026-05-17 09:10:00+08', 'ANRAN'),
      ('closed-no-accept', 'YUNQIAO', '[答辩演示] 数据产品岗案例拆解手册交互化改版',
       '任务背景：云桥数据希望把案例拆解手册改成可交互页面。\n交付要求：输出信息架构、页面流程、指标解释和可验证范围。\n说明：该任务已关闭但未采纳，用于后台治理边界演示。',
       '项目反馈 + 后续实习沟通机会', 'CLOSED', TIMESTAMPTZ '2026-05-14 23:59:00+08', TIMESTAMPTZ '2026-05-16 18:00:00+08', TIMESTAMPTZ '2026-05-08 13:00:00+08', TIMESTAMPTZ '2026-05-16 18:00:00+08', NULL::TEXT),
      ('ai-eval', 'YUNQIAO', '[答辩演示] AI 面试复盘摘要卡片文案与布局优化',
       '任务背景：AI 面试复盘信息量偏大，希望用摘要卡片帮助学生快速定位问题。\n交付要求：输出复盘卡片结构、评分解释文案和下一步行动建议。',
       '200 元项目奖金 + 数据产品导师反馈', 'OPEN', TIMESTAMPTZ '2026-05-22 23:59:00+08', NULL::TIMESTAMPTZ, TIMESTAMPTZ '2026-05-15 12:00:00+08', TIMESTAMPTZ '2026-05-17 09:35:00+08', NULL::TEXT),
      ('accepted-zhou', 'YUNQIAO', '[答辩演示] 用户流失预警专题分析与指标解释页',
       '任务背景：业务团队需要一个能解释流失风险来源的专题分析页。\n交付要求：定义指标、风险标签、解释文案和示例 SQL。\n说明：该任务已采纳周沐阳方案，用于非主演示用户填充。',
       '400 元项目奖金 + 远程实习面谈', 'CLOSED', TIMESTAMPTZ '2026-05-16 23:59:00+08', TIMESTAMPTZ '2026-05-17 08:30:00+08', TIMESTAMPTZ '2026-05-09 09:30:00+08', TIMESTAMPTZ '2026-05-17 08:30:00+08', 'ZHOUMUYANG'),
      ('empty-open', 'BEICHEN', '[答辩演示] 客户成功周报自动汇总模板',
       '任务背景：客户成功团队每周需要重复整理客户跟进摘要。\n交付要求：设计自动汇总模板字段、异常提醒和人工确认入口。\n说明：该任务暂未收到提交，用于学生任务大厅空提交样例。',
       '150 元项目奖金', 'OPEN', TIMESTAMPTZ '2026-05-25 23:59:00+08', NULL::TIMESTAMPTZ, TIMESTAMPTZ '2026-05-16 10:00:00+08', TIMESTAMPTZ '2026-05-17 09:20:00+08', NULL::TEXT)
) AS v(task_key, enterprise_key, title, description, reward_description, status, deadline_at, closed_at, created_at, updated_at, accepted_student_key)
CROSS JOIN tmp_defense_users u;

INSERT INTO bounty_tasks (
    enterprise_user_id, title, description, reward_description, status,
    accepted_submission_id, deadline_at, closed_at, created_at, updated_at
)
SELECT enterprise_user_id, title, description, reward_description, status,
       NULL, deadline_at, closed_at, created_at, updated_at
  FROM tmp_defense_tasks;

CREATE TEMP TABLE tmp_defense_task_ids AS
SELECT t.*, bt.id AS task_id
  FROM tmp_defense_tasks t
  JOIN bounty_tasks bt ON bt.title = t.title;

CREATE TEMP TABLE tmp_defense_submissions (
    task_key TEXT NOT NULL,
    student_key TEXT NOT NULL,
    student_user_id BIGINT NOT NULL,
    content_text TEXT NOT NULL,
    attachment_links TEXT,
    status TEXT NOT NULL,
    review_comment TEXT,
    reviewed_at TIMESTAMPTZ,
    reviewer_user_id BIGINT,
    contact_intent TEXT,
    reject_template TEXT,
    review_note TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

INSERT INTO tmp_defense_submissions (
    task_key, student_key, student_user_id, content_text, attachment_links, status,
    review_comment, reviewed_at, reviewer_user_id, contact_intent, reject_template,
    review_note, created_at, updated_at
)
SELECT
    v.task_key,
    v.student_key,
    CASE v.student_key
        WHEN 'ANRAN' THEN u.anran_user_id
        WHEN 'LIUJIANING' THEN u.liujianing_user_id
        WHEN 'ZHOUMUYANG' THEN u.zhoumuyang_user_id
        WHEN 'CHENSIYU' THEN u.chensiyu_user_id
        WHEN 'HEQINGYAN' THEN u.heqingyan_user_id
        WHEN 'LINJIAQI' THEN u.linjiaqi_user_id
    END,
    v.content_text,
    v.attachment_links,
    v.status,
    v.review_comment,
    v.reviewed_at,
    CASE v.enterprise_key
        WHEN 'BEICHEN' THEN u.beichen_user_id
        WHEN 'YUNQIAO' THEN u.yunqiao_user_id
    END,
    v.contact_intent,
    v.reject_template,
    v.review_note,
    v.created_at,
    v.updated_at
FROM (
    VALUES
      ('frontend-polish', 'ANRAN', 'BEICHEN', '我把问题拆成三层：背景动效合成成本、前景轨道亚像素位移、卡片 backdrop-filter 导致的文字重采样，并给出保留视觉效果的最小调整路径。附件里包含性能面板截图和验收清单。', 'https://docs.example.com/defense/anran/frontend-polish', 'REVIEWING', NULL::TEXT, NULL::TIMESTAMPTZ, '希望企业先看性能定位是否准确。', NULL::TEXT, '学生方案完整，待企业进一步审核。', TIMESTAMPTZ '2026-05-17 09:05:00+08', TIMESTAMPTZ '2026-05-17 09:40:00+08'),
      ('frontend-polish', 'LIUJIANING', 'BEICHEN', '我从后端接口耗时角度补了一份排查建议，认为首屏可以先拆 API 与静态资源加载时间。', 'https://docs.example.com/defense/liujianing/frontend-api-check', 'SUBMITTED', NULL::TEXT, NULL::TIMESTAMPTZ, NULL::TEXT, NULL::TEXT, NULL::TEXT, TIMESTAMPTZ '2026-05-16 20:10:00+08', TIMESTAMPTZ '2026-05-16 20:10:00+08'),
      ('growth-dashboard', 'ANRAN', 'BEICHEN', '我设计了四张摘要卡片：新增试用、活跃下降、功能触达和转化风险，并补了每个指标的埋点口径。', 'https://docs.example.com/defense/anran/growth-dashboard', 'SUBMITTED', NULL::TEXT, NULL::TIMESTAMPTZ, NULL::TEXT, NULL::TEXT, NULL::TEXT, TIMESTAMPTZ '2026-05-17 08:50:00+08', TIMESTAMPTZ '2026-05-17 08:50:00+08'),
      ('growth-dashboard', 'ZHOUMUYANG', 'BEICHEN', '我补了一版偏数据分析的方案，重点是指标口径、漏斗拆分和异常样本回看。', 'https://docs.example.com/defense/zhou/growth-dashboard', 'REVIEWING', NULL::TEXT, NULL::TIMESTAMPTZ, '可以继续沟通数据口径部分。', NULL::TEXT, '该方案数据分析维度较完整。', TIMESTAMPTZ '2026-05-16 17:20:00+08', TIMESTAMPTZ '2026-05-17 09:10:00+08'),
      ('overdue-open', 'LINJIAQI', 'BEICHEN', '我提交了表单分组、错误提示文案和一个低保真流程图，但还没有补完整验收指标。', 'https://docs.example.com/defense/linjiaqi/form-polish', 'SUBMITTED', NULL::TEXT, NULL::TIMESTAMPTZ, NULL::TEXT, NULL::TEXT, NULL::TEXT, TIMESTAMPTZ '2026-05-14 15:30:00+08', TIMESTAMPTZ '2026-05-14 15:30:00+08'),
      ('accepted-anran', 'ANRAN', 'BEICHEN', '我的方案把解释页拆成“命中原因、排序依据、下一步操作”三块，并给了空状态和异常状态文案。', 'https://docs.example.com/defense/anran/kb-explain', 'ACCEPTED', '方案结构完整，解释文案能直接进入产品评审，已采纳。', TIMESTAMPTZ '2026-05-17 09:05:00+08', '希望进入后续实习沟通。', NULL::TEXT, '已联系学生，任务结果固定。', TIMESTAMPTZ '2026-05-15 10:00:00+08', TIMESTAMPTZ '2026-05-17 09:05:00+08'),
      ('accepted-anran', 'HEQINGYAN', 'BEICHEN', '我补了一版偏后端接口的解释字段设计，但页面交互部分不够完整。', 'https://docs.example.com/defense/heqingyan/kb-api', 'REJECTED', '任务已采纳其他同学方案，本次提交不再进入后续沟通。', TIMESTAMPTZ '2026-05-17 09:06:00+08', NULL::TEXT, '任务已结束，已有中选方案。', '企业已选定更完整的前端交互方案。', TIMESTAMPTZ '2026-05-15 11:30:00+08', TIMESTAMPTZ '2026-05-17 09:06:00+08'),
      ('closed-no-accept', 'CHENSIYU', 'YUNQIAO', '我输出了一版案例拆解手册信息架构，但没有补交互原型。', 'https://docs.example.com/defense/chensiyu/case-manual', 'REJECTED', '本轮任务已关闭，暂不采纳。', TIMESTAMPTZ '2026-05-16 17:30:00+08', NULL::TEXT, '交付不完整', '任务关闭但未选定最终方案。', TIMESTAMPTZ '2026-05-13 10:10:00+08', TIMESTAMPTZ '2026-05-16 17:30:00+08'),
      ('ai-eval', 'ANRAN', 'YUNQIAO', '我把 AI 面试复盘摘要卡片拆成分数解释、优势、风险和下一步练习四块，并补了跳转到复盘中心的交互。', 'https://docs.example.com/defense/anran/ai-review-card', 'SUBMITTED', NULL::TEXT, NULL::TIMESTAMPTZ, NULL::TEXT, NULL::TEXT, NULL::TEXT, TIMESTAMPTZ '2026-05-17 09:25:00+08', TIMESTAMPTZ '2026-05-17 09:25:00+08'),
      ('accepted-zhou', 'ZHOUMUYANG', 'YUNQIAO', '我按流失预警业务拆了指标口径、风险解释和样例 SQL，并补充了异常样本回看路径。', 'https://docs.example.com/defense/zhou/churn-analysis', 'ACCEPTED', '指标解释清楚，样例 SQL 可复用，已采纳。', TIMESTAMPTZ '2026-05-17 08:25:00+08', '希望安排远程实习面谈。', NULL::TEXT, '已联系学生，任务结果固定。', TIMESTAMPTZ '2026-05-15 16:20:00+08', TIMESTAMPTZ '2026-05-17 08:25:00+08'),
      ('accepted-zhou', 'ANRAN', 'YUNQIAO', '我从前端页面解释角度补了一版方案，但数据指标拆解不如周沐阳完整。', 'https://docs.example.com/defense/anran/churn-page', 'REJECTED', '任务已采纳其他同学方案，本次提交未入选。', TIMESTAMPTZ '2026-05-17 08:26:00+08', NULL::TEXT, '任务已结束，已有中选方案。', '企业已选定数据分析更完整的方案。', TIMESTAMPTZ '2026-05-15 17:10:00+08', TIMESTAMPTZ '2026-05-17 08:26:00+08')
) AS v(task_key, student_key, enterprise_key, content_text, attachment_links, status, review_comment, reviewed_at, contact_intent, reject_template, review_note, created_at, updated_at)
CROSS JOIN tmp_defense_users u;

INSERT INTO bounty_submissions (
    task_id, student_user_id, content_text, attachment_links, status, review_comment,
    reviewed_at, reviewer_user_id, created_at, updated_at, contact_intent,
    reject_template, review_note
)
SELECT
    ti.task_id,
    s.student_user_id,
    s.content_text,
    s.attachment_links,
    s.status,
    s.review_comment,
    s.reviewed_at,
    s.reviewer_user_id,
    s.created_at,
    s.updated_at,
    s.contact_intent,
    s.reject_template,
    s.review_note
FROM tmp_defense_submissions s
JOIN tmp_defense_task_ids ti ON ti.task_key = s.task_key;

CREATE TEMP TABLE tmp_defense_submission_ids AS
SELECT s.*, ti.task_id, bs.id AS submission_id
  FROM tmp_defense_submissions s
  JOIN tmp_defense_task_ids ti ON ti.task_key = s.task_key
  JOIN bounty_submissions bs
    ON bs.task_id = ti.task_id
   AND bs.student_user_id = s.student_user_id;

UPDATE bounty_tasks bt
   SET accepted_submission_id = s.submission_id,
       updated_at = GREATEST(bt.updated_at, s.updated_at)
  FROM tmp_defense_task_ids ti
  JOIN tmp_defense_submission_ids s
    ON s.task_key = ti.task_key
   AND s.student_key = ti.accepted_student_key
 WHERE bt.id = ti.task_id
   AND ti.accepted_student_key IS NOT NULL;

INSERT INTO bounty_submission_events (
    submission_id, task_id, actor_user_id, event_type, comment_text,
    contact_intent, reject_template, note, created_at
)
SELECT
    submission_id, task_id, student_user_id, 'SUBMITTED',
    'DEFENSE-20260517：学生提交企业任务成果。', NULL, NULL, NULL, created_at
FROM tmp_defense_submission_ids;

INSERT INTO bounty_submission_events (
    submission_id, task_id, actor_user_id, event_type, comment_text,
    contact_intent, reject_template, note, created_at
)
SELECT
    submission_id, task_id, reviewer_user_id,
    CASE
        WHEN status = 'ACCEPTED' THEN 'CONTACT_SENT'
        WHEN status = 'REJECTED' THEN 'REJECT_SENT'
        ELSE 'CONTACT_SENT'
    END,
    COALESCE(review_comment, '企业已更新处理意见。'),
    contact_intent,
    reject_template,
    review_note,
    COALESCE(reviewed_at, updated_at)
FROM tmp_defense_submission_ids
WHERE status IN ('ACCEPTED', 'REJECTED', 'REVIEWING');

INSERT INTO bounty_submission_events (
    submission_id, task_id, actor_user_id, event_type, comment_text,
    contact_intent, reject_template, note, created_at
)
SELECT
    submission_id, task_id, reviewer_user_id, 'TASK_CLOSED_AFTER_ACCEPT',
    NULL, NULL, NULL, '任务已结束，当前结果留痕已固定在工作区中。', reviewed_at + INTERVAL '1 minute'
FROM tmp_defense_submission_ids
WHERE status = 'ACCEPTED';

-- 3. AI 异步任务、AI 调用日志、面试会话。
CREATE TEMP TABLE tmp_defense_ai_jobs (
    task_id TEXT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    task_type TEXT NOT NULL,
    scene_code TEXT NOT NULL,
    route_code TEXT NOT NULL,
    execution_mode TEXT NOT NULL,
    status TEXT NOT NULL,
    provider_code TEXT NOT NULL,
    provider_type TEXT NOT NULL,
    model_name TEXT NOT NULL,
    result_summary TEXT,
    error_code TEXT,
    error_message TEXT,
    current_attempt INT NOT NULL,
    next_run_at TIMESTAMPTZ NOT NULL,
    queued_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

INSERT INTO tmp_defense_ai_jobs
SELECT
    v.task_id,
    CASE v.user_key
        WHEN 'ANRAN' THEN u.anran_user_id
        WHEN 'LIUJIANING' THEN u.liujianing_user_id
        WHEN 'HEQINGYAN' THEN u.heqingyan_user_id
    END,
    'RESUME',
    'RESUME_OPTIMIZE',
    'SYSTEM_RESUME_OPTIMIZE',
    'ASYNC_JOB',
    v.status,
    'SYSTEM_RESUME_GEMINI_NATIVE',
    'GEMINI_NATIVE',
    'gemini-2.5-flash',
    v.result_summary,
    v.error_code,
    v.error_message,
    v.current_attempt,
    v.next_run_at,
    v.queued_at,
    v.started_at,
    v.finished_at,
    v.created_at,
    v.updated_at
FROM (
    VALUES
      ('aitk_defense_20260517_anran_resume_success', 'ANRAN', 'SUCCEEDED', '已生成前端 / 增长方向简历优化报告，建议突出性能优化、埋点验证和答辩项目闭环。', NULL::TEXT, NULL::TEXT, 1, TIMESTAMPTZ '2026-05-17 08:40:00+08', TIMESTAMPTZ '2026-05-17 08:30:00+08', TIMESTAMPTZ '2026-05-17 08:31:00+08', TIMESTAMPTZ '2026-05-17 08:39:00+08', TIMESTAMPTZ '2026-05-17 08:30:00+08', TIMESTAMPTZ '2026-05-17 08:39:00+08'),
      ('aitk_defense_20260517_anran_resume_running', 'ANRAN', 'RUNNING', '正在生成答辩版项目亮点摘要。', NULL::TEXT, NULL::TEXT, 1, TIMESTAMPTZ '2026-05-17 10:02:00+08', TIMESTAMPTZ '2026-05-17 09:58:00+08', TIMESTAMPTZ '2026-05-17 09:59:00+08', NULL::TIMESTAMPTZ, TIMESTAMPTZ '2026-05-17 09:58:00+08', TIMESTAMPTZ '2026-05-17 09:59:00+08'),
      ('aitk_defense_20260517_anran_resume_failed', 'ANRAN', 'FAILED', NULL::TEXT, 'PROVIDER_TIMEOUT', '模型响应超时，已记录失败并通知学生稍后重试。', 3, TIMESTAMPTZ '2026-05-16 22:20:00+08', TIMESTAMPTZ '2026-05-16 22:00:00+08', TIMESTAMPTZ '2026-05-16 22:01:00+08', TIMESTAMPTZ '2026-05-16 22:18:00+08', TIMESTAMPTZ '2026-05-16 22:00:00+08', TIMESTAMPTZ '2026-05-16 22:18:00+08'),
      ('aitk_defense_20260517_liujianing_resume_pending', 'LIUJIANING', 'PENDING', '等待异步 worker 处理后端简历优化任务。', NULL::TEXT, NULL::TEXT, 0, TIMESTAMPTZ '2026-05-17 10:20:00+08', TIMESTAMPTZ '2026-05-17 09:55:00+08', NULL::TIMESTAMPTZ, NULL::TIMESTAMPTZ, TIMESTAMPTZ '2026-05-17 09:55:00+08', TIMESTAMPTZ '2026-05-17 09:55:00+08'),
      ('aitk_defense_20260517_heqingyan_resume_retry', 'HEQINGYAN', 'RETRY_WAIT', '第一次调用失败，等待重试。', 'REQUEST_FAILED', '上游网关临时失败，稍后重试。', 1, TIMESTAMPTZ '2026-05-17 10:30:00+08', TIMESTAMPTZ '2026-05-17 09:35:00+08', TIMESTAMPTZ '2026-05-17 09:36:00+08', NULL::TIMESTAMPTZ, TIMESTAMPTZ '2026-05-17 09:35:00+08', TIMESTAMPTZ '2026-05-17 09:37:00+08')
) AS v(task_id, user_key, status, result_summary, error_code, error_message, current_attempt, next_run_at, queued_at, started_at, finished_at, created_at, updated_at)
CROSS JOIN tmp_defense_users u;

INSERT INTO ai_async_task_jobs (
    task_id, user_id, task_type, scene_code, route_code, execution_mode, status,
    provider_code, provider_type, model_name, prompt_template_name, prompt_template_version_no,
    input_snapshot_json, context_json, prompt_snapshot_json, route_snapshot_json,
    result_summary, result_payload_json, error_code, error_message, current_attempt, max_attempts,
    next_run_at, lease_owner, lease_expires_at, queued_at, started_at, finished_at,
    created_at, updated_at
)
SELECT
    task_id, user_id, task_type, scene_code, route_code, execution_mode, status,
    provider_code, provider_type, model_name, 'RESUME_OPTIMIZE_CORE', 1,
    jsonb_build_object('targetRole', '前端开发 / 增长产品实习生', 'demoTag', 'DEFENSE-20260517'),
    jsonb_build_object('source', 'defense-demo', 'baselineAt', '2026-05-17T10:00:00+08:00'),
    jsonb_build_object('template', 'RESUME_OPTIMIZE_CORE', 'version', 1),
    jsonb_build_object('routeCode', route_code, 'providerCode', provider_code, 'model', model_name),
    result_summary,
    CASE WHEN status = 'SUCCEEDED' THEN jsonb_build_object(
        'overallScore', 86,
        'summary', result_summary,
        'sections', jsonb_build_array('项目主线', '成果表达', '岗位关键词'),
        'demoTag', 'DEFENSE-20260517'
    ) ELSE NULL END,
    error_code,
    error_message,
    current_attempt,
    3,
    next_run_at,
    CASE WHEN status = 'RUNNING' THEN 'defense-demo-worker' ELSE NULL END,
    CASE WHEN status = 'RUNNING' THEN TIMESTAMPTZ '2026-05-17 10:08:00+08' ELSE NULL END,
    queued_at,
    started_at,
    finished_at,
    created_at,
    updated_at
FROM tmp_defense_ai_jobs;

CREATE TEMP TABLE tmp_defense_ai_job_ids AS
SELECT j.*, dbj.id AS task_job_id
  FROM tmp_defense_ai_jobs j
  JOIN ai_async_task_jobs dbj ON dbj.task_id = j.task_id;

INSERT INTO ai_async_task_events (
    event_id, task_job_id, task_id, user_id, event_type, delivery_status,
    payload_json, published_at, created_at, updated_at
)
SELECT
    'defense-20260517-ai-sub-' || left(md5(task_id), 12),
    task_job_id, task_id, user_id, 'SUBMITTED', 'PENDING',
    jsonb_build_object('demoTag', 'DEFENSE-20260517', 'taskId', task_id),
    NULL, queued_at, queued_at
FROM tmp_defense_ai_job_ids;

INSERT INTO ai_async_task_events (
    event_id, task_job_id, task_id, user_id, event_type, delivery_status,
    payload_json, published_at, created_at, updated_at
)
SELECT
    'defense-20260517-ai-start-' || left(md5(task_id), 12),
    task_job_id, task_id, user_id, 'STARTED', 'PENDING',
    jsonb_build_object('demoTag', 'DEFENSE-20260517', 'taskId', task_id),
    NULL, started_at, started_at
FROM tmp_defense_ai_job_ids
WHERE started_at IS NOT NULL;

INSERT INTO ai_async_task_events (
    event_id, task_job_id, task_id, user_id, event_type, delivery_status,
    payload_json, published_at, created_at, updated_at
)
SELECT
    'defense-20260517-ai-term-' || left(md5(task_id), 12),
    task_job_id, task_id, user_id,
    CASE WHEN status = 'SUCCEEDED' THEN 'SUCCEEDED' ELSE 'FAILED' END,
    'PENDING',
    jsonb_build_object('demoTag', 'DEFENSE-20260517', 'taskId', task_id, 'status', status),
    finished_at, finished_at, finished_at
FROM tmp_defense_ai_job_ids
WHERE status IN ('SUCCEEDED', 'FAILED')
  AND finished_at IS NOT NULL;

INSERT INTO ai_call_logs (
    trace_id, user_id, task_type, scene_code, provider, model, route_code,
    route_policy_code, latency_ms, status, error_code, request_tokens,
    response_tokens, total_tokens, thoughts_tokens, reasoning_effort,
    thinking_budget, thinking_level, estimated_cost, charged_points,
    quota_weight, result_summary, result_payload_json, user_deleted_at,
    user_tier, created_at
)
SELECT
    v.trace_id,
    CASE v.user_key
        WHEN 'ANRAN' THEN u.anran_user_id
        WHEN 'LIUJIANING' THEN u.liujianing_user_id
        WHEN 'ZHOUMUYANG' THEN u.zhoumuyang_user_id
        WHEN 'HEQINGYAN' THEN u.heqingyan_user_id
    END,
    v.task_type, v.scene_code, v.provider, v.model, v.route_code,
    v.route_policy_code, v.latency_ms, v.status, v.error_code, v.request_tokens,
    v.response_tokens, v.request_tokens + v.response_tokens, v.thoughts_tokens,
    'LOW', 0, 'minimal', 0, v.charged_points, 1,
    v.result_summary,
    jsonb_build_object('demoTag', 'DEFENSE-20260517', 'scene', v.scene_code)::TEXT,
    NULL, 'FREE', v.created_at
FROM (
    VALUES
      ('defense-20260517-ai-001', 'ANRAN', 'RESUME', 'RESUME_OPTIMIZE', 'SYSTEM_RESUME_GEMINI_NATIVE', 'gemini-2.5-flash', 'SYSTEM_RESUME_OPTIMIZE', 'SYSTEM_RESUME_OPTIMIZE_FREE', 8420, 'SUCCESS', NULL::TEXT, 1680, 1280, 0, 10, '安然简历优化完成。', TIMESTAMPTZ '2026-05-17 08:39:00+08'),
      ('defense-20260517-ai-002', 'ANRAN', 'INTERVIEW_TEXT', 'INTERVIEW_OPENING', 'SYSTEM_INTERVIEW_GEMINI_NATIVE', 'gemini-2.5-flash', 'SYSTEM_INTERVIEW_OPENING', 'SYSTEM_INTERVIEW_FREE', 1200, 'SUCCESS', NULL::TEXT, 620, 420, 0, 5, '生成前端性能优化面试开场问题。', TIMESTAMPTZ '2026-05-17 09:05:00+08'),
      ('defense-20260517-ai-003', 'ANRAN', 'INTERVIEW_TEXT', 'INTERVIEW_REPLY', 'SYSTEM_INTERVIEW_GEMINI_NATIVE', 'gemini-2.5-flash', 'SYSTEM_INTERVIEW_REPLY', 'SYSTEM_INTERVIEW_FREE', 1850, 'SUCCESS', NULL::TEXT, 760, 540, 0, 5, '生成追问：如何证明性能优化有效。', TIMESTAMPTZ '2026-05-17 09:12:00+08'),
      ('defense-20260517-ai-004', 'ANRAN', 'INTERVIEW_TEXT', 'INTERVIEW_REPLY', 'SYSTEM_INTERVIEW_GEMINI_NATIVE', 'gemini-2.5-flash', 'SYSTEM_INTERVIEW_REPLY', 'SYSTEM_INTERVIEW_FREE', 1720, 'SUCCESS', NULL::TEXT, 810, 590, 0, 5, '生成追问：为什么移除 backdrop blur。', TIMESTAMPTZ '2026-05-17 09:18:00+08'),
      ('defense-20260517-ai-005', 'ANRAN', 'INTERVIEW_SUMMARY', 'INTERVIEW_SUMMARY', 'SYSTEM_INTERVIEW_GEMINI_NATIVE', 'gemini-2.5-flash', 'SYSTEM_INTERVIEW_SUMMARY', 'SYSTEM_INTERVIEW_FREE', 4210, 'SUCCESS', NULL::TEXT, 1420, 980, 0, 8, '生成面试复盘摘要。', TIMESTAMPTZ '2026-05-17 09:45:00+08'),
      ('defense-20260517-ai-006', 'ANRAN', 'RESUME', 'RESUME_OPTIMIZE', 'SYSTEM_RESUME_GEMINI_NATIVE', 'gemini-2.5-flash', 'SYSTEM_RESUME_OPTIMIZE', 'SYSTEM_RESUME_OPTIMIZE_FREE', 15000, 'TIMEOUT', 'PROVIDER_TIMEOUT', 1700, 0, 0, 10, '模型超时，稍后重试。', TIMESTAMPTZ '2026-05-16 22:18:00+08'),
      ('defense-20260517-ai-007', 'LIUJIANING', 'RESUME', 'RESUME_OPTIMIZE', 'SYSTEM_RESUME_GEMINI_NATIVE', 'gemini-2.5-flash', 'SYSTEM_RESUME_OPTIMIZE', 'SYSTEM_RESUME_OPTIMIZE_FREE', 0, 'SUCCESS', NULL::TEXT, 1520, 1020, 0, 10, '后端简历优化历史样例。', TIMESTAMPTZ '2026-05-15 20:15:00+08'),
      ('defense-20260517-ai-008', 'ZHOUMUYANG', 'INTERVIEW_SUMMARY', 'INTERVIEW_SUMMARY', 'SYSTEM_INTERVIEW_GEMINI_NATIVE', 'gemini-2.5-flash', 'SYSTEM_INTERVIEW_SUMMARY', 'SYSTEM_INTERVIEW_FREE', 3860, 'SUCCESS', NULL::TEXT, 1210, 890, 0, 8, '数据分析面试复盘摘要。', TIMESTAMPTZ '2026-05-16 18:40:00+08'),
      ('defense-20260517-ai-009', 'HEQINGYAN', 'RESUME', 'RESUME_OPTIMIZE', 'SYSTEM_RESUME_GEMINI_NATIVE', 'gemini-2.5-flash', 'SYSTEM_RESUME_OPTIMIZE', 'SYSTEM_RESUME_OPTIMIZE_FREE', 5100, 'ERROR', 'REQUEST_FAILED', 1510, 0, 0, 10, '网关临时失败，进入重试等待。', TIMESTAMPTZ '2026-05-17 09:37:00+08'),
      ('defense-20260517-ai-010', 'ANRAN', 'COMMUNITY_REPLY', 'COMMUNITY_PRE_ANSWER', 'SYSTEM_MENTOR_PREP_GEMINI_NATIVE', 'gemini-2.5-flash', 'SYSTEM_COMMUNITY_PRE_ANSWER', 'SYSTEM_COMMUNITY_FREE', 1850, 'SUCCESS', NULL::TEXT, 540, 360, 0, 2, '生成社区回复草稿。', TIMESTAMPTZ '2026-05-17 09:55:00+08')
) AS v(trace_id, user_key, task_type, scene_code, provider, model, route_code, route_policy_code, latency_ms, status, error_code, request_tokens, response_tokens, thoughts_tokens, charged_points, result_summary, created_at)
CROSS JOIN tmp_defense_users u;

CREATE TEMP TABLE tmp_defense_interview_sessions (
    session_id TEXT PRIMARY KEY,
    student_key TEXT NOT NULL,
    student_user_id BIGINT NOT NULL,
    target_role TEXT NOT NULL,
    mode TEXT NOT NULL,
    status TEXT NOT NULL,
    reply_round_limit INT NOT NULL,
    reply_round_used INT NOT NULL,
    summary_generated INT NOT NULL,
    prepaid_points INT NOT NULL,
    reserved_quota_weight INT NOT NULL,
    summary_overall_score INT,
    summary_generated_at TIMESTAMPTZ,
    finish_reason TEXT,
    ended_by_ai INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

INSERT INTO tmp_defense_interview_sessions
SELECT
    v.session_id,
    v.student_key,
    CASE v.student_key
        WHEN 'ANRAN' THEN u.anran_user_id
        WHEN 'LIUJIANING' THEN u.liujianing_user_id
        WHEN 'ZHOUMUYANG' THEN u.zhoumuyang_user_id
        WHEN 'HEQINGYAN' THEN u.heqingyan_user_id
    END,
    v.target_role, v.mode, v.status, v.reply_round_limit, v.reply_round_used,
    v.summary_generated, v.prepaid_points, v.reserved_quota_weight,
    v.summary_overall_score, v.summary_generated_at, v.finish_reason, v.ended_by_ai,
    v.created_at, v.updated_at
FROM (
    VALUES
      ('ivs_def_20260517_anran_frontend_completed', 'ANRAN', '前端开发 / 增长产品实习生', 'INTERVIEW_TEXT', 'COMPLETED', 8, 5, 1, 0, 5, 86, TIMESTAMPTZ '2026-05-17 09:45:00+08', 'MANUAL_SUMMARY', 0, TIMESTAMPTZ '2026-05-17 09:02:00+08', TIMESTAMPTZ '2026-05-17 09:45:00+08'),
      ('ivs_def_20260517_anran_live_active', 'ANRAN', '前端性能优化专项', 'VOICE_MINIMAL', 'ACTIVE', 8, 2, 0, 0, 5, NULL::INT, NULL::TIMESTAMPTZ, NULL::TEXT, 0, TIMESTAMPTZ '2026-05-17 09:50:00+08', TIMESTAMPTZ '2026-05-17 09:58:00+08'),
      ('ivs_def_20260516_zhou_data_completed', 'ZHOUMUYANG', '数据分析实习生', 'INTERVIEW_TEXT', 'COMPLETED', 8, 4, 1, 0, 5, 82, TIMESTAMPTZ '2026-05-16 18:40:00+08', 'ROUND_LIMIT', 1, TIMESTAMPTZ '2026-05-16 18:00:00+08', TIMESTAMPTZ '2026-05-16 18:40:00+08'),
      ('ivs_def_20260515_liujianing_backend_completed', 'LIUJIANING', 'Java 后端开发实习生', 'INTERVIEW_TEXT', 'COMPLETED', 8, 4, 1, 0, 5, 84, TIMESTAMPTZ '2026-05-15 21:00:00+08', 'ENOUGH_DEPTH', 0, TIMESTAMPTZ '2026-05-15 20:20:00+08', TIMESTAMPTZ '2026-05-15 21:00:00+08'),
      ('ivs_def_20260514_heqingyan_deleted', 'HEQINGYAN', '后端开发工程师', 'INTERVIEW_TEXT', 'DELETED', 8, 1, 0, 0, 5, NULL::INT, NULL::TIMESTAMPTZ, 'USER_CANCELLED', 0, TIMESTAMPTZ '2026-05-14 20:00:00+08', TIMESTAMPTZ '2026-05-14 20:10:00+08')
) AS v(session_id, student_key, target_role, mode, status, reply_round_limit, reply_round_used, summary_generated, prepaid_points, reserved_quota_weight, summary_overall_score, summary_generated_at, finish_reason, ended_by_ai, created_at, updated_at)
CROSS JOIN tmp_defense_users u;

INSERT INTO interview_sessions (
    session_id, student_user_id, target_role, mode, resume_context_json,
    session_context_json, status, reply_round_limit, reply_round_used,
    summary_generated, prepaid_points, reserved_quota_weight, summary_overall_score,
    summary_strengths_json, summary_weaknesses_json, summary_suggestions_json,
    summary_provider, summary_model, summary_latency_ms, summary_generated_at,
    finish_reason, ended_by_ai, user_deleted_at, created_at, updated_at
)
SELECT
    session_id, student_user_id, target_role, mode,
    jsonb_build_object('demoTag', 'DEFENSE-20260517', 'resumeFocus', '项目表达和岗位匹配')::TEXT,
    jsonb_build_object('demoTag', 'DEFENSE-20260517', 'baselineAt', '2026-05-17T10:00:00+08:00')::TEXT,
    status, reply_round_limit, reply_round_used, summary_generated,
    prepaid_points, reserved_quota_weight, summary_overall_score,
    CASE WHEN summary_generated = 1 THEN jsonb_build_array('项目经历具体', '能说明性能优化过程', '回答结构较稳定')::TEXT ELSE NULL END,
    CASE WHEN summary_generated = 1 THEN jsonb_build_array('开头背景略长', '数据验证需要更明确', '结尾行动建议不够聚焦')::TEXT ELSE NULL END,
    CASE WHEN summary_generated = 1 THEN jsonb_build_array('用 STAR 结构收束回答', '补充优化前后指标', '准备一个失败排查案例')::TEXT ELSE NULL END,
    CASE WHEN summary_generated = 1 THEN 'SYSTEM_INTERVIEW_GEMINI_NATIVE' ELSE NULL END,
    CASE WHEN summary_generated = 1 THEN 'gemini-2.5-flash' ELSE NULL END,
    CASE WHEN summary_generated = 1 THEN 4200 ELSE NULL END,
    summary_generated_at, finish_reason, ended_by_ai, NULL, created_at, updated_at
FROM tmp_defense_interview_sessions
ON CONFLICT (session_id) DO UPDATE SET
    student_user_id = EXCLUDED.student_user_id,
    target_role = EXCLUDED.target_role,
    mode = EXCLUDED.mode,
    resume_context_json = EXCLUDED.resume_context_json,
    session_context_json = EXCLUDED.session_context_json,
    status = EXCLUDED.status,
    reply_round_limit = EXCLUDED.reply_round_limit,
    reply_round_used = EXCLUDED.reply_round_used,
    summary_generated = EXCLUDED.summary_generated,
    summary_overall_score = EXCLUDED.summary_overall_score,
    summary_strengths_json = EXCLUDED.summary_strengths_json,
    summary_weaknesses_json = EXCLUDED.summary_weaknesses_json,
    summary_suggestions_json = EXCLUDED.summary_suggestions_json,
    summary_provider = EXCLUDED.summary_provider,
    summary_model = EXCLUDED.summary_model,
    summary_latency_ms = EXCLUDED.summary_latency_ms,
    summary_generated_at = EXCLUDED.summary_generated_at,
    finish_reason = EXCLUDED.finish_reason,
    ended_by_ai = EXCLUDED.ended_by_ai,
    created_at = EXCLUDED.created_at,
    updated_at = EXCLUDED.updated_at;

CREATE TEMP TABLE tmp_defense_interview_session_ids AS
SELECT s.*, dbs.id AS session_pk
  FROM tmp_defense_interview_sessions s
  JOIN interview_sessions dbs ON dbs.session_id = s.session_id;

INSERT INTO interview_messages (
    session_pk, sender_role, message_text, coach_feedback, score_hint, audio_object_key, created_at
)
SELECT
    sid.session_pk, v.sender_role, v.message_text, v.coach_feedback, v.score_hint,
    v.audio_object_key, v.created_at
FROM (
    VALUES
      ('ivs_def_20260517_anran_frontend_completed', 'AI', '请先用 2 分钟介绍你在毕业设计中做过的前端性能优化。', NULL::TEXT, NULL::INT, NULL::TEXT, TIMESTAMPTZ '2026-05-17 09:03:00+08'),
      ('ivs_def_20260517_anran_frontend_completed', 'USER', '我主要处理了落地页背景动效卡顿和卡片文字发糊问题，先定位合成层和 backdrop-filter，再保留动效做分层优化。', '回答能说明问题背景，但建议先补一句用户侧影响。', 78, NULL::TEXT, TIMESTAMPTZ '2026-05-17 09:08:00+08'),
      ('ivs_def_20260517_anran_frontend_completed', 'AI', '你为什么没有直接删除背景动效，而是选择保留视觉效果后优化合成成本？', NULL::TEXT, NULL::INT, NULL::TEXT, TIMESTAMPTZ '2026-05-17 09:12:00+08'),
      ('ivs_def_20260517_anran_frontend_completed', 'USER', '因为首页承担产品化叙事，直接删除会影响第一印象，所以我先暂停离屏循环动画、把光晕 transform 和 blur 拆开，并移除主体卡片的 backdrop-blur。', '这段回答结构较好，可以补充验证方式。', 86, NULL::TEXT, TIMESTAMPTZ '2026-05-17 09:18:00+08'),
      ('ivs_def_20260517_anran_frontend_completed', 'AI', '如果面试官追问验证指标，你会怎么回答？', NULL::TEXT, NULL::INT, NULL::TEXT, TIMESTAMPTZ '2026-05-17 09:25:00+08'),
      ('ivs_def_20260517_anran_frontend_completed', 'USER', '我会说明用浏览器 Performance 面板看长任务、合成层数量和滚动切屏掉帧情况，同时用人工逐屏检查文字清晰度。', '验证路径清楚，建议再补一句风险回滚。', 88, NULL::TEXT, TIMESTAMPTZ '2026-05-17 09:32:00+08'),
      ('ivs_def_20260517_anran_live_active', 'AI', '我们继续做语音专项，请先说明“卡片文字发糊”可能由哪些层级因素引起。', NULL::TEXT, NULL::INT, 'defense/20260517/interview/anran_live_ai_opening.mp3', TIMESTAMPTZ '2026-05-17 09:52:00+08'),
      ('ivs_def_20260517_anran_live_active', 'USER', '我会先看文字是不是被 blur 层盖住，再看父级 transform 是否造成重采样，以及 backdrop-filter 是否让文字参与了额外合成。', '方向正确，继续追问具体排查顺序。', 84, 'defense/20260517/interview/anran_live_user_001.wav', TIMESTAMPTZ '2026-05-17 09:57:00+08'),
      ('ivs_def_20260516_zhou_data_completed', 'AI', '请解释一个你做过的数据分析指标口径。', NULL::TEXT, NULL::INT, NULL::TEXT, TIMESTAMPTZ '2026-05-16 18:05:00+08'),
      ('ivs_def_20260516_zhou_data_completed', 'USER', '我会先定义观察对象、统计窗口和排除条件，再说明为什么这个指标能反映转化风险。', '回答较稳定，可以补业务例子。', 82, NULL::TEXT, TIMESTAMPTZ '2026-05-16 18:12:00+08'),
      ('ivs_def_20260515_liujianing_backend_completed', 'AI', '请介绍一个你处理异步通知的项目经历。', NULL::TEXT, NULL::INT, NULL::TEXT, TIMESTAMPTZ '2026-05-15 20:25:00+08'),
      ('ivs_def_20260515_liujianing_backend_completed', 'USER', '我会把通知发布拆成事件落库、站内信入箱、派发任务和前端 actionCode 回流四层。', '结构清楚，建议说明失败重试。', 85, NULL::TEXT, TIMESTAMPTZ '2026-05-15 20:33:00+08')
) AS v(session_id, sender_role, message_text, coach_feedback, score_hint, audio_object_key, created_at)
JOIN tmp_defense_interview_session_ids sid ON sid.session_id = v.session_id;

-- 4. 成长、积分、技能星图和学生画像。
INSERT INTO checkins (student_user_id, checkin_date, streak_count, points_earned, created_at)
SELECT u.anran_user_id, d::DATE, (d::DATE - DATE '2026-05-11') + 1, CASE WHEN d::DATE = DATE '2026-05-17' THEN 24 ELSE 10 END,
       (d::DATE + TIME '08:20')::TIMESTAMPTZ
  FROM tmp_defense_users u
 CROSS JOIN generate_series(DATE '2026-05-11', DATE '2026-05-17', INTERVAL '1 day') AS d
ON CONFLICT (student_user_id, checkin_date) DO UPDATE SET
    streak_count = EXCLUDED.streak_count,
    points_earned = EXCLUDED.points_earned,
    created_at = EXCLUDED.created_at;

INSERT INTO checkins (student_user_id, checkin_date, streak_count, points_earned, created_at)
SELECT u.liujianing_user_id, d::DATE, (d::DATE - DATE '2026-05-15') + 1, 10,
       (d::DATE + TIME '08:45')::TIMESTAMPTZ
  FROM tmp_defense_users u
 CROSS JOIN generate_series(DATE '2026-05-15', DATE '2026-05-17', INTERVAL '1 day') AS d
ON CONFLICT (student_user_id, checkin_date) DO UPDATE SET
    streak_count = EXCLUDED.streak_count,
    points_earned = EXCLUDED.points_earned,
    created_at = EXCLUDED.created_at;

INSERT INTO points_ledger (student_user_id, delta_points, reason_code, balance_after, created_at)
SELECT * FROM (
    SELECT u.anran_user_id, 10, 'DEFENSE_20260517_CHECKIN_20260511', 214, TIMESTAMPTZ '2026-05-11 08:20:00+08' FROM tmp_defense_users u
    UNION ALL SELECT u.anran_user_id, 10, 'DEFENSE_20260517_CHECKIN_20260512', 224, TIMESTAMPTZ '2026-05-12 08:20:00+08' FROM tmp_defense_users u
    UNION ALL SELECT u.anran_user_id, 10, 'DEFENSE_20260517_CHECKIN_20260513', 234, TIMESTAMPTZ '2026-05-13 08:20:00+08' FROM tmp_defense_users u
    UNION ALL SELECT u.anran_user_id, 10, 'DEFENSE_20260517_CHECKIN_20260514', 244, TIMESTAMPTZ '2026-05-14 08:20:00+08' FROM tmp_defense_users u
    UNION ALL SELECT u.anran_user_id, 10, 'DEFENSE_20260517_CHECKIN_20260515', 254, TIMESTAMPTZ '2026-05-15 08:20:00+08' FROM tmp_defense_users u
    UNION ALL SELECT u.anran_user_id, 10, 'DEFENSE_20260517_CHECKIN_20260516', 264, TIMESTAMPTZ '2026-05-16 08:20:00+08' FROM tmp_defense_users u
    UNION ALL SELECT u.anran_user_id, 24, 'DEFENSE_20260517_CHECKIN_STREAK7', 288, TIMESTAMPTZ '2026-05-17 08:20:00+08' FROM tmp_defense_users u
    UNION ALL SELECT u.anran_user_id, 10, 'DEFENSE_20260517_AI_RESUME_SUCCESS', 298, TIMESTAMPTZ '2026-05-17 08:40:00+08' FROM tmp_defense_users u
    UNION ALL SELECT u.anran_user_id, 8, 'DEFENSE_20260517_SKILL_MASTERED', 306, TIMESTAMPTZ '2026-05-17 09:10:00+08' FROM tmp_defense_users u
    UNION ALL SELECT u.liujianing_user_id, 10, 'DEFENSE_20260517_CHECKIN_LIU_20260517', 142, TIMESTAMPTZ '2026-05-17 08:45:00+08' FROM tmp_defense_users u
    UNION ALL SELECT u.zhoumuyang_user_id, 12, 'DEFENSE_20260517_BOUNTY_ACCEPTED', 78, TIMESTAMPTZ '2026-05-17 08:35:00+08' FROM tmp_defense_users u
) AS x(student_user_id, delta_points, reason_code, balance_after, created_at);

INSERT INTO skill_progress (student_user_id, node_code, progress_status, updated_at)
SELECT
    CASE v.student_key
        WHEN 'ANRAN' THEN u.anran_user_id
        WHEN 'LIUJIANING' THEN u.liujianing_user_id
        WHEN 'ZHOUMUYANG' THEN u.zhoumuyang_user_id
        WHEN 'HEQINGYAN' THEN u.heqingyan_user_id
    END,
    v.node_code,
    v.progress_status,
    v.updated_at
FROM (
    VALUES
      ('ANRAN', 'programming_language_foundations', 'MASTERED', TIMESTAMPTZ '2026-05-17 09:00:00+08'),
      ('ANRAN', 'computational_thinking', 'MASTERED', TIMESTAMPTZ '2026-05-17 09:01:00+08'),
      ('ANRAN', 'software_engineering_delivery', 'MASTERED', TIMESTAMPTZ '2026-05-17 09:02:00+08'),
      ('ANRAN', 'ai_programming_engineering', 'MASTERED', TIMESTAMPTZ '2026-05-17 09:03:00+08'),
      ('ANRAN', 'ai_data_science', 'LEARNING', TIMESTAMPTZ '2026-05-17 09:04:00+08'),
      ('ANRAN', 'career_employment_readiness', 'LEARNING', TIMESTAMPTZ '2026-05-17 09:05:00+08'),
      ('LIUJIANING', 'java_programming', 'MASTERED', TIMESTAMPTZ '2026-05-16 19:00:00+08'),
      ('LIUJIANING', 'systems_infrastructure', 'LEARNING', TIMESTAMPTZ '2026-05-17 08:50:00+08'),
      ('ZHOUMUYANG', 'data_database_systems', 'MASTERED', TIMESTAMPTZ '2026-05-17 08:40:00+08'),
      ('ZHOUMUYANG', 'ai_data_science', 'LEARNING', TIMESTAMPTZ '2026-05-17 08:41:00+08'),
      ('HEQINGYAN', 'java_programming', 'LEARNING', TIMESTAMPTZ '2026-05-16 21:10:00+08')
) AS v(student_key, node_code, progress_status, updated_at)
CROSS JOIN tmp_defense_users u
ON CONFLICT (student_user_id, node_code) DO UPDATE SET
    progress_status = EXCLUDED.progress_status,
    updated_at = EXCLUDED.updated_at;

INSERT INTO student_portrait_snapshots (student_user_id, portrait_tags, evidence, updated_at)
SELECT u.anran_user_id,
       jsonb_build_array(
           jsonb_build_object('code', 'DEFENSE_FRONTEND_GROWTH_MAIN', 'label', '前端与增长双线主线清晰', 'source', 'DEFENSE-20260517', 'confidence', 0.94),
           jsonb_build_object('code', 'RESUME_EXPRESSION_READY', 'label', '简历表达接近可投递', 'source', 'AI_RESUME', 'confidence', 0.88),
           jsonb_build_object('code', 'CONSULT_ACTIVE_WITH_GUHANG', 'label', '正在接受顾航航导师辅导', 'source', 'CONSULT', 'confidence', 0.91)
       )::TEXT,
       jsonb_build_object(
           'demoTag', 'DEFENSE-20260517',
           'signals', jsonb_build_array('7 日连续打卡', '简历优化成功', '企业任务被北辰智联采纳', '顾航航咨询订单多状态覆盖')
       )::TEXT,
       TIMESTAMPTZ '2026-05-17 09:50:00+08'
  FROM tmp_defense_users u
ON CONFLICT (student_user_id) DO UPDATE SET
    portrait_tags = EXCLUDED.portrait_tags,
    evidence = EXCLUDED.evidence,
    updated_at = EXCLUDED.updated_at;

-- 5. 社区、举报治理、审计。
CREATE TEMP TABLE tmp_defense_posts (
    post_key TEXT PRIMARY KEY,
    user_key TEXT NOT NULL,
    user_id BIGINT NOT NULL,
    moderation_status TEXT NOT NULL,
    is_deleted BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    tags TEXT,
    scenario_code TEXT NOT NULL,
    resolved_status TEXT NOT NULL,
    risk_level TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

INSERT INTO tmp_defense_posts
SELECT
    v.post_key,
    v.user_key,
    CASE v.user_key
        WHEN 'ANRAN' THEN u.anran_user_id
        WHEN 'GUHANG' THEN u.guhang_user_id
        WHEN 'LIUJIANING' THEN u.liujianing_user_id
        WHEN 'ZHOUMUYANG' THEN u.zhoumuyang_user_id
        WHEN 'HEQINGYAN' THEN u.heqingyan_user_id
        WHEN 'LINJIAQI' THEN u.linjiaqi_user_id
    END,
    v.moderation_status,
    v.is_deleted,
    v.created_at,
    v.title,
    v.content,
    v.tags,
    v.scenario_code,
    v.resolved_status,
    v.risk_level,
    v.updated_at
FROM (
    VALUES
      ('anran-defense-help', 'ANRAN', 'PASS', FALSE, TIMESTAMPTZ '2026-05-17 09:12:00+08', '[答辩演示] 前端性能优化项目如何讲得不像流水账', '我准备答辩时讲落地页背景动效优化，包括离屏动画暂停、光晕分层、整数像素位移和去掉主体卡片 backdrop-blur。想问问大家这个顺序是否自然？ DEFENSE-20260517', '前端性能,答辩,项目表达', 'INTERVIEW_EXPERIENCE', 'OPEN', 'LOW', TIMESTAMPTZ '2026-05-17 09:12:00+08'),
      ('guhang-answer', 'GUHANG', 'PASS', FALSE, TIMESTAMPTZ '2026-05-17 09:20:00+08', '[答辩演示] 导师答疑：项目复盘先讲约束，再讲技术取舍', '很多同学讲项目时直接堆技术名词。更稳的方式是先讲用户侧问题和业务约束，再讲你为什么选择当前方案，最后补验证和回滚。 DEFENSE-20260517', '导师答疑,项目复盘,面试', 'GENERAL_HELP', 'OPEN', 'LOW', TIMESTAMPTZ '2026-05-17 09:20:00+08'),
      ('liujianing-backend', 'LIUJIANING', 'PASS', FALSE, TIMESTAMPTZ '2026-05-16 20:30:00+08', '[答辩演示] 后端项目里通知回流和异步派发怎么讲', '我准备讲 notification_events、notifications 和 dispatch_jobs 三层，但担心答辩时老师问为什么不直接发通知。大家会怎么解释？ DEFENSE-20260517', '后端,通知,答辩', 'GENERAL_HELP', 'OPEN', 'LOW', TIMESTAMPTZ '2026-05-16 20:30:00+08'),
      ('zhou-data', 'ZHOUMUYANG', 'PASS', FALSE, TIMESTAMPTZ '2026-05-16 18:45:00+08', '[答辩演示] 数据分析岗案例：指标口径要不要放在简历里', '我刚完成云桥数据的企业任务，感觉指标口径是亮点，但简历里写太多会不会太重？ DEFENSE-20260517', '数据分析,简历,企业任务', 'RESUME_REVIEW', 'OPEN', 'LOW', TIMESTAMPTZ '2026-05-16 18:45:00+08'),
      ('heqingyan-after-sales', 'HEQINGYAN', 'PASS', FALSE, TIMESTAMPTZ '2026-05-15 11:45:00+08', '[答辩演示] 咨询售后申请被通过后，订单页会显示哪些信息', '我有一单人工售后退款已经通过，想确认订单页和通知中心里的状态分别怎么看。 DEFENSE-20260517', '咨询订单,售后,退款', 'GENERAL_HELP', 'RESOLVED', 'LOW', TIMESTAMPTZ '2026-05-15 12:30:00+08'),
      ('linjiaqi-growth', 'LINJIAQI', 'PASS', FALSE, TIMESTAMPTZ '2026-05-15 20:40:00+08', '[答辩演示] 前端和增长双线投递怎么准备两版项目表达', '我想把同一个项目准备成前端版和增长版，前端版讲组件和性能，增长版讲指标和验证路径。这样会不会割裂？ DEFENSE-20260517', '前端,增长,项目表达', 'CAREER_DIRECTION', 'OPEN', 'LOW', TIMESTAMPTZ '2026-05-15 20:40:00+08'),
      ('risk-post', 'ANRAN', 'REVIEW', FALSE, TIMESTAMPTZ '2026-05-17 09:35:00+08', '[答辩演示] 求职资料外链集合，管理员帮忙看下是否合规', '这里整理了一些外部链接和资料入口，担心有些链接不适合公开放在社区里，请管理员帮忙看下。 DEFENSE-20260517', '资料链接,社区治理', 'GENERAL_HELP', 'OPEN', 'MEDIUM', TIMESTAMPTZ '2026-05-17 09:35:00+08'),
      ('blocked-post', 'LIUJIANING', 'BLOCK', FALSE, TIMESTAMPTZ '2026-05-14 22:10:00+08', '[答辩演示] 不合规内推渠道示例，供治理演示', '这是一条专门用于答辩演示的高风险社区内容，管理员将通过举报和治理事件展示下架链路。 DEFENSE-20260517', '治理演示,高风险', 'GENERAL_HELP', 'OPEN', 'HIGH', TIMESTAMPTZ '2026-05-14 22:10:00+08')
) AS v(post_key, user_key, moderation_status, is_deleted, created_at, title, content, tags, scenario_code, resolved_status, risk_level, updated_at)
CROSS JOIN tmp_defense_users u;

INSERT INTO posts (
    user_id, moderation_status, is_deleted, created_at, title, content,
    tags, scenario_code, resolved_status, risk_level, last_moderation_event_id, updated_at
)
SELECT user_id, moderation_status, is_deleted, created_at, title, content,
       tags, scenario_code, resolved_status, risk_level, NULL, updated_at
FROM tmp_defense_posts;

CREATE TEMP TABLE tmp_defense_post_ids AS
SELECT p.*, dbp.id AS post_id
  FROM tmp_defense_posts p
  JOIN posts dbp ON dbp.title = p.title;

CREATE TEMP TABLE tmp_defense_comments (
    comment_key TEXT PRIMARY KEY,
    post_key TEXT NOT NULL,
    user_key TEXT NOT NULL,
    user_id BIGINT NOT NULL,
    moderation_status TEXT NOT NULL,
    is_deleted BOOLEAN NOT NULL,
    is_ai BOOLEAN NOT NULL,
    content TEXT NOT NULL,
    risk_level TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

INSERT INTO tmp_defense_comments
SELECT
    v.comment_key, v.post_key, v.user_key,
    CASE v.user_key
        WHEN 'ANRAN' THEN u.anran_user_id
        WHEN 'GUHANG' THEN u.guhang_user_id
        WHEN 'LIUJIANING' THEN u.liujianing_user_id
        WHEN 'ZHOUMUYANG' THEN u.zhoumuyang_user_id
        WHEN 'HEQINGYAN' THEN u.heqingyan_user_id
        WHEN 'LINJIAQI' THEN u.linjiaqi_user_id
    END,
    v.moderation_status, v.is_deleted, v.is_ai, v.content, v.risk_level, v.created_at, v.updated_at
FROM (
    VALUES
      ('c1', 'anran-defense-help', 'GUHANG', 'PASS', FALSE, FALSE, '可以先讲用户能感知到的卡顿和文字发糊，再讲你没有删动效，而是做了分层和暂停策略。', 'LOW', TIMESTAMPTZ '2026-05-17 09:22:00+08', TIMESTAMPTZ '2026-05-17 09:22:00+08'),
      ('c2', 'anran-defense-help', 'LIUJIANING', 'PASS', FALSE, FALSE, '我觉得你可以补一句“为什么这个优化不影响业务接口”，这样老师能听出边界。', 'LOW', TIMESTAMPTZ '2026-05-17 09:30:00+08', TIMESTAMPTZ '2026-05-17 09:30:00+08'),
      ('c3', 'guhang-answer', 'ANRAN', 'PASS', FALSE, FALSE, '收到，我会按“问题、取舍、验证、回滚”四段整理答辩稿。', 'LOW', TIMESTAMPTZ '2026-05-17 09:28:00+08', TIMESTAMPTZ '2026-05-17 09:28:00+08'),
      ('c4', 'liujianing-backend', 'GUHANG', 'PASS', FALSE, FALSE, '可以强调事件和站内信解耦，后续邮件、WebSocket 和补偿任务都能复用同一个事件。', 'LOW', TIMESTAMPTZ '2026-05-16 21:00:00+08', TIMESTAMPTZ '2026-05-16 21:00:00+08'),
      ('c5', 'zhou-data', 'ANRAN', 'PASS', FALSE, FALSE, '可以写一行代表性指标口径，把完整 SQL 和图表放作品集链接里。', 'LOW', TIMESTAMPTZ '2026-05-16 19:10:00+08', TIMESTAMPTZ '2026-05-16 19:10:00+08'),
      ('c6', 'heqingyan-after-sales', 'GUHANG', 'PASS', FALSE, FALSE, '订单详情页会看到售后申请和退款状态，通知中心也能通过订单 actionCode 回流。', 'LOW', TIMESTAMPTZ '2026-05-15 12:10:00+08', TIMESTAMPTZ '2026-05-15 12:10:00+08'),
      ('c7', 'linjiaqi-growth', 'ANRAN', 'PASS', FALSE, FALSE, '我也准备了两版表达，关键是同一个项目主线不变，只换强调重点。', 'LOW', TIMESTAMPTZ '2026-05-15 21:00:00+08', TIMESTAMPTZ '2026-05-15 21:00:00+08'),
      ('c8', 'risk-post', 'GUHANG', 'REVIEW', FALSE, FALSE, '建议先不要放可疑外链，改成说明资料来源和筛选标准。 DEFENSE-20260517', 'MEDIUM', TIMESTAMPTZ '2026-05-17 09:42:00+08', TIMESTAMPTZ '2026-05-17 09:42:00+08')
) AS v(comment_key, post_key, user_key, moderation_status, is_deleted, is_ai, content, risk_level, created_at, updated_at)
CROSS JOIN tmp_defense_users u;

INSERT INTO comments (
    post_id, user_id, moderation_status, is_deleted, created_at, is_ai,
    content, risk_level, last_moderation_event_id, updated_at
)
SELECT p.post_id, c.user_id, c.moderation_status, c.is_deleted, c.created_at,
       c.is_ai, c.content, c.risk_level, NULL, c.updated_at
  FROM tmp_defense_comments c
  JOIN tmp_defense_post_ids p ON p.post_key = c.post_key;

CREATE TEMP TABLE tmp_defense_comment_ids AS
SELECT c.*, dbc.id AS comment_id
  FROM tmp_defense_comments c
  JOIN tmp_defense_post_ids p ON p.post_key = c.post_key
  JOIN comments dbc ON dbc.post_id = p.post_id AND dbc.content = c.content;

INSERT INTO post_likes (post_id, user_id, created_at)
SELECT p.post_id, liker.user_id, p.created_at + INTERVAL '10 minutes'
  FROM tmp_defense_post_ids p
  JOIN LATERAL (
      SELECT anran_user_id AS user_id FROM tmp_defense_users
      UNION ALL SELECT guhang_user_id FROM tmp_defense_users
      UNION ALL SELECT liujianing_user_id FROM tmp_defense_users
      UNION ALL SELECT zhoumuyang_user_id FROM tmp_defense_users
      UNION ALL SELECT linjiaqi_user_id FROM tmp_defense_users
  ) liker ON liker.user_id <> p.user_id
 WHERE p.post_key IN ('anran-defense-help', 'guhang-answer', 'liujianing-backend', 'zhou-data', 'linjiaqi-growth')
ON CONFLICT (post_id, user_id) DO NOTHING;

INSERT INTO content_reports (
    reporter_user_id, target_type, target_id, reason_code, detail, status,
    latest_action, created_at, updated_at, closed_at
)
SELECT u.linjiaqi_user_id, 'POST', p.post_id::TEXT, 'RISK_LINK',
       'DEFENSE-20260517：举报该帖包含外部资料链接，需要管理员核查。', 'PENDING',
       'NONE', TIMESTAMPTZ '2026-05-17 09:45:00+08', TIMESTAMPTZ '2026-05-17 09:45:00+08', NULL::TIMESTAMPTZ
  FROM tmp_defense_post_ids p CROSS JOIN tmp_defense_users u
 WHERE p.post_key = 'risk-post'
ON CONFLICT (reporter_user_id, target_type, target_id, reason_code) DO UPDATE SET
    detail = EXCLUDED.detail,
    status = EXCLUDED.status,
    latest_action = EXCLUDED.latest_action,
    updated_at = EXCLUDED.updated_at,
    closed_at = EXCLUDED.closed_at;

INSERT INTO content_reports (
    reporter_user_id, target_type, target_id, reason_code, detail, status,
    latest_action, created_at, updated_at, closed_at
)
SELECT u.anran_user_id, 'POST', p.post_id::TEXT, 'SPAM',
       'DEFENSE-20260517：举报高风险内推渠道示例。', 'ACCEPTED',
       'TAKE_DOWN', TIMESTAMPTZ '2026-05-15 08:30:00+08', TIMESTAMPTZ '2026-05-15 09:10:00+08', TIMESTAMPTZ '2026-05-15 09:10:00+08'
  FROM tmp_defense_post_ids p CROSS JOIN tmp_defense_users u
 WHERE p.post_key = 'blocked-post'
ON CONFLICT (reporter_user_id, target_type, target_id, reason_code) DO UPDATE SET
    detail = EXCLUDED.detail,
    status = EXCLUDED.status,
    latest_action = EXCLUDED.latest_action,
    updated_at = EXCLUDED.updated_at,
    closed_at = EXCLUDED.closed_at;

INSERT INTO content_report_actions (report_id, operator_user_id, decision, action, comment, created_at)
SELECT r.id, u.admin_user_id, 'ACCEPTED', 'TAKE_DOWN',
       'DEFENSE-20260517：演示管理员确认举报并下架高风险内容。', TIMESTAMPTZ '2026-05-15 09:10:00+08'
  FROM content_reports r
  JOIN tmp_defense_post_ids p ON r.target_id = p.post_id::TEXT
 CROSS JOIN tmp_defense_users u
 WHERE p.post_key = 'blocked-post'
   AND r.reason_code = 'SPAM';

INSERT INTO content_moderation_events (
    trace_id, source_type, target_type, target_id, risk_level, action,
    reason_code, masked_text, operator_user_id, created_at
)
SELECT
    'defense-20260517-community-' || p.post_key,
    'COMMUNITY_POST',
    'POST',
    p.post_id::TEXT,
    p.risk_level,
    CASE p.moderation_status WHEN 'PASS' THEN 'PASS' WHEN 'BLOCK' THEN 'BLOCK' ELSE 'REVIEW' END,
    CASE p.risk_level WHEN 'LOW' THEN 'RULE_CLEAR' WHEN 'MEDIUM' THEN 'POLICY_REVIEW_REQUIRED' ELSE 'UNVERIFIED_EXTERNAL_LINK' END,
    left(p.content, 240),
    CASE WHEN p.moderation_status = 'PASS' THEN 0 ELSE u.admin_user_id END,
    p.created_at
FROM tmp_defense_post_ids p
CROSS JOIN tmp_defense_users u;

INSERT INTO audit_logs (trace_id, operator_user_id, action_type, target_type, target_id, detail_json, created_at)
SELECT 'defense-20260517-consult-aftersales-pending', u.admin_user_id, 'CONSULT_AFTER_SALES_CREATED', 'CONSULT_ORDER', 'CONS-DEF-20260517-016',
       jsonb_build_object('demoTag', 'DEFENSE-20260517', 'status', 'PENDING')::TEXT, TIMESTAMPTZ '2026-05-17 09:31:00+08'
  FROM tmp_defense_users u
UNION ALL
SELECT 'defense-20260517-content-takedown', u.admin_user_id, 'CONTENT_REPORT_REVIEWED', 'POST', p.post_id::TEXT,
       jsonb_build_object('demoTag', 'DEFENSE-20260517', 'decision', 'TAKE_DOWN')::TEXT, TIMESTAMPTZ '2026-05-15 09:10:00+08'
  FROM tmp_defense_users u
  JOIN tmp_defense_post_ids p ON p.post_key = 'blocked-post'
UNION ALL
SELECT 'defense-20260517-payment-refund', u.admin_user_id, 'ADMIN_CONSULT_REFUND', 'CONSULT_ORDER', 'CONS-DEF-20260517-018',
       jsonb_build_object('demoTag', 'DEFENSE-20260517', 'source', 'MANUAL_AFTER_SALES')::TEXT, TIMESTAMPTZ '2026-05-15 11:30:00+08'
  FROM tmp_defense_users u;

-- 6. 导师财务样例。
INSERT INTO mentor_withdrawal_requests (mentor_user_id, amount_fen, status, note, created_at, updated_at)
SELECT u.guhang_user_id, 35800, 'PENDING', '[答辩演示] 顾航航 5 月中旬咨询收入提现待审核', TIMESTAMPTZ '2026-05-17 09:40:00+08', TIMESTAMPTZ '2026-05-17 09:40:00+08' FROM tmp_defense_users u
UNION ALL SELECT u.guhang_user_id, 16900, 'PROCESSING', '[答辩演示] 顾航航历史提现处理中', TIMESTAMPTZ '2026-05-15 18:00:00+08', TIMESTAMPTZ '2026-05-16 10:00:00+08' FROM tmp_defense_users u
UNION ALL SELECT u.guhang_user_id, 9900, 'COMPLETED', '[答辩演示] 顾航航历史提现已完成', TIMESTAMPTZ '2026-05-10 12:00:00+08', TIMESTAMPTZ '2026-05-11 09:00:00+08' FROM tmp_defense_users u
UNION ALL SELECT u.hanxue_user_id, 13900, 'REJECTED', '[答辩演示] 韩雪提现驳回：账户信息需补充', TIMESTAMPTZ '2026-05-14 13:00:00+08', TIMESTAMPTZ '2026-05-14 18:00:00+08' FROM tmp_defense_users u
UNION ALL SELECT u.linqiao_user_id, 14900, 'CANCELED', '[答辩演示] 林乔主动取消提现申请', TIMESTAMPTZ '2026-05-13 13:00:00+08', TIMESTAMPTZ '2026-05-13 14:00:00+08' FROM tmp_defense_users u;

-- 7. 通知事件、站内信和派发任务。
CREATE TEMP TABLE tmp_defense_notifications (
    event_id TEXT PRIMARY KEY,
    recipient_user_id BIGINT NOT NULL,
    type TEXT NOT NULL,
    category TEXT NOT NULL,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    ref_type TEXT,
    ref_id TEXT,
    action_code TEXT,
    priority TEXT NOT NULL,
    actor_user_id BIGINT,
    payload_json TEXT,
    occurred_at TIMESTAMPTZ NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE
);

INSERT INTO tmp_defense_notifications (
    event_id, recipient_user_id, type, category, title, content, ref_type, ref_id,
    action_code, priority, actor_user_id, payload_json, occurred_at, is_read
)
SELECT 'defense-20260517-consult-paid-003', u.guhang_user_id, 'CONSULT_PAID', 'CONSULT', '新的咨询订单已支付', '安然的简历诊断订单已支付，可开始答复。', 'CONSULT_ORDER', 'CONS-DEF-20260517-003', 'VIEW_CONSULT_ORDER', 'HIGH', u.anran_user_id,
       jsonb_build_object('demoTag', 'DEFENSE-20260517', 'orderNo', 'CONS-DEF-20260517-003')::TEXT, TIMESTAMPTZ '2026-05-17 08:46:00+08', FALSE FROM tmp_defense_users u
UNION ALL SELECT 'defense-20260517-consult-replied-004', u.anran_user_id, 'CONSULT_REPLIED', 'CONSULT', '导师已回复你的咨询', '顾航航已回复模拟面试复盘订单，请查看并决定是否关闭。', 'CONSULT_ORDER', 'CONS-DEF-20260517-004', 'VIEW_CONSULT_ORDER', 'HIGH', u.guhang_user_id,
       jsonb_build_object('demoTag', 'DEFENSE-20260517', 'orderNo', 'CONS-DEF-20260517-004')::TEXT, TIMESTAMPTZ '2026-05-17 09:24:00+08', FALSE FROM tmp_defense_users u
UNION ALL SELECT 'defense-20260517-consult-reviewed-005', u.guhang_user_id, 'CONSULT_REVIEWED', 'CONSULT', '咨询订单收到新评价', '安然已提交项目表达咨询评价。', 'CONSULT_ORDER', 'CONS-DEF-20260517-005', 'VIEW_CONSULT_ORDER', 'NORMAL', u.anran_user_id,
       jsonb_build_object('demoTag', 'DEFENSE-20260517', 'orderNo', 'CONS-DEF-20260517-005')::TEXT, TIMESTAMPTZ '2026-05-15 21:18:00+08', TRUE FROM tmp_defense_users u
UNION ALL SELECT 'defense-20260517-consult-refunded-006', u.anran_user_id, 'CONSULT_REFUNDED', 'CONSULT', '咨询订单已退款', '导师超时未回复，系统已自动退款。', 'CONSULT_ORDER', 'CONS-DEF-20260517-006', 'VIEW_CONSULT_ORDER', 'HIGH', NULL,
       jsonb_build_object('demoTag', 'DEFENSE-20260517', 'orderNo', 'CONS-DEF-20260517-006')::TEXT, TIMESTAMPTZ '2026-05-16 09:05:00+08', FALSE FROM tmp_defense_users u
UNION ALL SELECT 'defense-20260517-consult-timeout-canceled-008', u.zhoumuyang_user_id, 'CONSULT_TIMEOUT_CANCELED', 'CONSULT', '咨询订单已超时关闭', '你的未支付咨询订单已超时取消，请根据需要重新下单。', 'CONSULT_ORDER', 'CONS-DEF-20260517-008', 'VIEW_CONSULT_ORDER', 'NORMAL', NULL,
       jsonb_build_object('demoTag', 'DEFENSE-20260517', 'orderNo', 'CONS-DEF-20260517-008')::TEXT, TIMESTAMPTZ '2026-05-17 09:05:00+08', FALSE FROM tmp_defense_users u
UNION ALL SELECT 'defense-20260517-consult-aftersales-016-admin', u.admin_user_id, 'CONSULT_AFTER_SALES_PENDING', 'CONSULT', '咨询订单收到售后申请', '安然提交了顾航航咨询订单售后申请，等待平台审核。', 'CONSULT_ORDER', 'CONS-DEF-20260517-016', 'VIEW_CONSULT_ORDER', 'HIGH', u.anran_user_id,
       jsonb_build_object('demoTag', 'DEFENSE-20260517', 'orderNo', 'CONS-DEF-20260517-016')::TEXT, TIMESTAMPTZ '2026-05-17 09:30:00+08', FALSE FROM tmp_defense_users u
UNION ALL SELECT 'defense-20260517-bounty-submitted-frontend', u.beichen_user_id, 'BOUNTY_SUBMITTED', 'BOUNTY', '悬赏任务有新的成果提交', '安然提交了校招活动页性能优化方案。', 'BOUNTY_TASK', ti.task_id::TEXT, 'VIEW_BOUNTY_TASK', 'HIGH', u.anran_user_id,
       jsonb_build_object('demoTag', 'DEFENSE-20260517', 'taskId', ti.task_id, 'submissionId', si.submission_id)::TEXT, TIMESTAMPTZ '2026-05-17 09:05:00+08', FALSE
  FROM tmp_defense_users u JOIN tmp_defense_task_ids ti ON ti.task_key = 'frontend-polish' JOIN tmp_defense_submission_ids si ON si.task_key = 'frontend-polish' AND si.student_key = 'ANRAN'
UNION ALL SELECT 'defense-20260517-bounty-reviewed-anran', u.anran_user_id, 'BOUNTY_REVIEWED', 'BOUNTY', '悬赏任务审核结果已更新', '北辰智联已采纳你的知识库解释页方案。', 'BOUNTY_TASK', ti.task_id::TEXT, 'VIEW_BOUNTY_TASK', 'NORMAL', u.beichen_user_id,
       jsonb_build_object('demoTag', 'DEFENSE-20260517', 'taskId', ti.task_id, 'submissionId', si.submission_id)::TEXT, TIMESTAMPTZ '2026-05-17 09:05:00+08', FALSE
  FROM tmp_defense_users u JOIN tmp_defense_task_ids ti ON ti.task_key = 'accepted-anran' JOIN tmp_defense_submission_ids si ON si.task_key = 'accepted-anran' AND si.student_key = 'ANRAN'
UNION ALL SELECT 'defense-20260517-bounty-reviewed-zhou', u.zhoumuyang_user_id, 'BOUNTY_REVIEWED', 'BOUNTY', '悬赏任务审核结果已更新', '云桥数据已采纳你的流失预警分析方案。', 'BOUNTY_TASK', ti.task_id::TEXT, 'VIEW_BOUNTY_TASK', 'NORMAL', u.yunqiao_user_id,
       jsonb_build_object('demoTag', 'DEFENSE-20260517', 'taskId', ti.task_id, 'submissionId', si.submission_id)::TEXT, TIMESTAMPTZ '2026-05-17 08:25:00+08', FALSE
  FROM tmp_defense_users u JOIN tmp_defense_task_ids ti ON ti.task_key = 'accepted-zhou' JOIN tmp_defense_submission_ids si ON si.task_key = 'accepted-zhou' AND si.student_key = 'ZHOUMUYANG'
UNION ALL SELECT 'defense-20260517-ai-resume-success-anran', u.anran_user_id, 'AI_RESUME_TASK_SUCCEEDED', 'AI_TASK', 'AI 简历任务已完成', '你的前端 / 增长方向简历优化报告已生成。', 'AI_ASYNC_TASK', 'aitk_defense_20260517_anran_resume_success', 'VIEW_AI_RESUME_TASK_RESULT', 'NORMAL', NULL,
       jsonb_build_object('demoTag', 'DEFENSE-20260517', 'taskId', 'aitk_defense_20260517_anran_resume_success')::TEXT, TIMESTAMPTZ '2026-05-17 08:39:00+08', FALSE FROM tmp_defense_users u
UNION ALL SELECT 'defense-20260517-ai-resume-failed-anran', u.anran_user_id, 'AI_RESUME_TASK_FAILED', 'AI_TASK', 'AI 简历任务执行失败', '答辩版项目亮点摘要生成失败，可稍后重试。', 'AI_ASYNC_TASK', 'aitk_defense_20260517_anran_resume_failed', 'VIEW_AI_RESUME_TASK_STATUS', 'HIGH', NULL,
       jsonb_build_object('demoTag', 'DEFENSE-20260517', 'taskId', 'aitk_defense_20260517_anran_resume_failed')::TEXT, TIMESTAMPTZ '2026-05-16 22:18:00+08', FALSE FROM tmp_defense_users u
UNION ALL SELECT 'defense-20260517-ai-interview-summary-anran', u.anran_user_id, 'AI_INTERVIEW_SUMMARY_READY', 'AI_TASK', 'AI 面试复盘已生成', '前端性能优化专项面试复盘已生成。', 'AI_INTERVIEW', 'ivs_def_20260517_anran_frontend_completed', 'VIEW_AI_REVIEW_CENTER', 'NORMAL', NULL,
       jsonb_build_object('demoTag', 'DEFENSE-20260517', 'sessionId', 'ivs_def_20260517_anran_frontend_completed')::TEXT, TIMESTAMPTZ '2026-05-17 09:45:00+08', FALSE FROM tmp_defense_users u
UNION ALL SELECT 'defense-20260517-community-risk-post', u.admin_user_id, 'COMMUNITY_REPORT_PENDING', 'COMMUNITY', '社区举报待处理', '有一条答辩演示社区举报等待处理。', 'COMMUNITY_REPORT', p.post_id::TEXT, 'VIEW_COMMUNITY_REPORTS', 'HIGH', u.linjiaqi_user_id,
       jsonb_build_object('demoTag', 'DEFENSE-20260517', 'postId', p.post_id)::TEXT, TIMESTAMPTZ '2026-05-17 09:45:00+08', FALSE
  FROM tmp_defense_users u JOIN tmp_defense_post_ids p ON p.post_key = 'risk-post'
UNION ALL SELECT 'defense-20260517-system-admin-digest', u.admin_user_id, 'ADMIN_DIGEST', 'SYSTEM', '答辩演示运营摘要', '2026-05-17 演示数据已覆盖咨询、企业任务、AI、社区治理和通知回流。', 'SYSTEM', 'DEFENSE-20260517', 'VIEW_NOTIFICATION_CENTER', 'NORMAL', NULL,
       jsonb_build_object('demoTag', 'DEFENSE-20260517')::TEXT, TIMESTAMPTZ '2026-05-17 10:00:00+08', FALSE FROM tmp_defense_users u;

INSERT INTO notification_events (
    event_id, type, category, source_type, source_id, actor_user_id, priority,
    dedupe_key, payload_json, occurred_at, created_at
)
SELECT
    event_id, type, category, ref_type, ref_id, actor_user_id, priority,
    event_id, payload_json, occurred_at, occurred_at
FROM tmp_defense_notifications
ON CONFLICT (event_id) DO UPDATE SET
    type = EXCLUDED.type,
    category = EXCLUDED.category,
    source_type = EXCLUDED.source_type,
    source_id = EXCLUDED.source_id,
    actor_user_id = EXCLUDED.actor_user_id,
    priority = EXCLUDED.priority,
    payload_json = EXCLUDED.payload_json,
    occurred_at = EXCLUDED.occurred_at;

INSERT INTO notifications (
    user_id, type, category, title, content, ref_type, ref_id, action_code,
    priority, event_id, payload_json, is_read, read_at, archived_at, created_at, updated_at
)
SELECT
    recipient_user_id, type, category, title, content, ref_type, ref_id, action_code,
    priority, event_id, payload_json, is_read,
    CASE WHEN is_read THEN occurred_at + INTERVAL '10 minutes' ELSE NULL END,
    NULL,
    occurred_at,
    occurred_at
FROM tmp_defense_notifications;

CREATE TEMP TABLE tmp_defense_notification_ids AS
SELECT n.id AS notification_id, tn.*
  FROM tmp_defense_notifications tn
  JOIN notifications n
    ON n.event_id = tn.event_id
   AND n.user_id = tn.recipient_user_id;

INSERT INTO notification_dispatch_jobs (
    job_id, notification_id, event_id, user_id, channel, status, attempt_count,
    max_attempts, next_run_at, lease_owner, lease_expires_at, sent_at, acked_at,
    failed_at, error_code, error_message, payload_json, created_at, updated_at
)
SELECT
    'def25-dj-' || lpad(row_number() OVER (ORDER BY occurred_at, event_id)::TEXT, 3, '0'),
    notification_id,
    event_id,
    recipient_user_id,
    'WEBSOCKET',
    CASE WHEN is_read THEN 'SENT' ELSE 'PENDING' END,
    CASE WHEN is_read THEN 1 ELSE 0 END,
    3,
    occurred_at,
    NULL, NULL,
    CASE WHEN is_read THEN occurred_at + INTERVAL '5 minutes' ELSE NULL END,
    CASE WHEN is_read THEN occurred_at + INTERVAL '10 minutes' ELSE NULL END,
    NULL, NULL, NULL,
    payload_json,
    occurred_at,
    occurred_at
FROM tmp_defense_notification_ids
WHERE event_id IN (
    'defense-20260517-consult-paid-003',
    'defense-20260517-consult-replied-004',
    'defense-20260517-bounty-submitted-frontend',
    'defense-20260517-bounty-reviewed-anran',
    'defense-20260517-ai-resume-success-anran',
    'defense-20260517-system-admin-digest'
);

INSERT INTO notification_dispatch_attempts (
    job_id, attempt_no, status, request_snapshot_json, response_snapshot_json,
    error_code, error_message, latency_ms, created_at
)
SELECT
    id, 1, 'SENT',
    jsonb_build_object('demoTag', 'DEFENSE-20260517', 'channel', channel)::TEXT,
    jsonb_build_object('ok', TRUE)::TEXT,
    NULL, NULL, 35, COALESCE(sent_at, created_at + INTERVAL '5 minutes')
FROM notification_dispatch_jobs
WHERE job_id LIKE 'def25-dj-%'
  AND status = 'SENT';

-- 8. 结果摘要：用于执行后快速检查。
SELECT 'consult_orders' AS table_name, count(*) AS demo_count
  FROM consult_orders
 WHERE order_no LIKE 'CONS-DEF-20260517-%'
UNION ALL
SELECT 'payment_records', count(*)
  FROM payment_records
 WHERE order_no LIKE 'CONS-DEF-20260517-%'
UNION ALL
SELECT 'bounty_tasks', count(*)
  FROM bounty_tasks
 WHERE title LIKE '[答辩演示]%'
UNION ALL
SELECT 'bounty_submissions', count(*)
  FROM bounty_submissions
 WHERE task_id IN (SELECT id FROM bounty_tasks WHERE title LIKE '[答辩演示]%')
UNION ALL
SELECT 'ai_async_task_jobs', count(*)
  FROM ai_async_task_jobs
 WHERE task_id LIKE 'aitk_defense_20260517_%'
UNION ALL
SELECT 'interview_sessions', count(*)
  FROM interview_sessions
 WHERE session_id LIKE 'ivs_def_20260517_%'
UNION ALL
SELECT 'posts', count(*)
  FROM posts
 WHERE title LIKE '[答辩演示]%'
UNION ALL
SELECT 'notifications', count(*)
  FROM notifications
 WHERE event_id LIKE 'defense-20260517-%'
ORDER BY table_name;

\if :demo_commit
COMMIT;
\echo 'DEFENSE-20260517 seed committed.'
\else
ROLLBACK;
\echo 'DEFENSE-20260517 seed dry-run finished and rolled back. Re-run with -v demo_commit=1 to commit.'
\endif
