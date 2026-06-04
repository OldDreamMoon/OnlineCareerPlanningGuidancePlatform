SET NAMES utf8mb4;

SET @seed_now = NOW();
SET @seed_password_hash = COALESCE(
  (SELECT password_hash FROM users WHERE role = 'MENTOR' ORDER BY id LIMIT 1),
  (SELECT password_hash FROM users ORDER BY id LIMIT 1)
);
SET @seed_admin_user_id = (SELECT id FROM users WHERE role = 'ADMIN' ORDER BY id LIMIT 1);

CREATE TEMPORARY TABLE seed_mentor_catalog (
  email VARCHAR(255) NOT NULL,
  display_name VARCHAR(100) NOT NULL,
  real_name VARCHAR(100) NOT NULL,
  company_name VARCHAR(200) NOT NULL,
  job_title VARCHAR(100) NOT NULL,
  expertise_tags VARCHAR(512) NOT NULL,
  service_scenes VARCHAR(512) NOT NULL,
  bio TEXT NOT NULL,
  suitable_for TEXT NOT NULL,
  not_suitable_for TEXT NOT NULL,
  prep_materials TEXT NOT NULL,
  reply_rhythm TEXT NOT NULL,
  price_fen INT NOT NULL,
  is_available TINYINT(1) NOT NULL,
  approval_status VARCHAR(20) NOT NULL,
  total_orders INT NOT NULL,
  avg_rating DECIMAL(3,2) NOT NULL,
  created_days_ago INT NOT NULL,
  last_login_hours_ago INT NOT NULL,
  review_note VARCHAR(1000) NULL
);

INSERT INTO seed_mentor_catalog (
  email, display_name, real_name, company_name, job_title, expertise_tags, service_scenes,
  bio, suitable_for, not_suitable_for, prep_materials, reply_rhythm,
  price_fen, is_available, approval_status, total_orders, avg_rating,
  created_days_ago, last_login_hours_ago, review_note
)
VALUES
  ('mentor.chengyanbei@bishe.local', '导师程砚北', '程砚北', '云栈科技', '高级后端工程师', 'Java 后端,系统设计,简历诊断', '简历诊断,项目表达,模拟面试复盘', '长期参与校招面试与新人带教，擅长把项目经历拆成业务目标、关键决策和结果指标。', '适合已经有 1-2 个项目、希望把后端项目讲得更像真实业务的人。', '不适合完全零基础、还没有任何项目材料的同学。', '最新简历、目标岗位 JD、最想重点展开的项目说明。', '通常当天晚间首轮反馈，复杂问题会在 24 小时内补第二轮建议。', 15900, 1, 'APPROVED', 28, 4.95, 120, 6, NULL),
  ('mentor.linqiao@bishe.local', '导师林乔', '林乔', '鹿鸣互娱', '资深前端工程师', '前端项目,性能优化,作品集表达', '项目表达,模拟面试复盘,校招投递策略', '聚焦前端项目表达、作品集叙事和性能优化故事线，适合准备前端实习和校招的学生。', '适合希望补齐项目亮点、性能优化表达和前端面试问答结构的同学。', '不适合只想要模板化八股答案、不愿意回看自己项目细节的人。', '简历、项目仓库地址或截图、目标公司岗位 JD。', '工作日晚间集中回复，通常 12 小时内给到第一版建议。', 14900, 1, 'APPROVED', 19, 4.88, 95, 18, NULL),
  ('mentor.songyue@bishe.local', '导师宋越', '宋越', '北辰产品研究院', '产品负责人', '产品策划,职业规划,Offer 选择', '岗位方向选择,Offer 对比与决策,简历诊断', '擅长帮助学生梳理产品岗位方向、项目亮点和 Offer 取舍逻辑。', '适合产品、运营、商业分析方向，或对岗位路径选择犹豫的同学。', '不适合只想快速改一版措辞、不愿意讨论岗位匹配度的人。', '简历、过往项目总结、目前在投岗位和已拿到的 Offer 信息。', '一般在 24 小时内给出结构化判断，Offer 比较类会补充决策框架。', 16900, 1, 'APPROVED', 16, 4.84, 90, 30, NULL),
  ('mentor.hejin@bishe.local', '导师何槿', '何槿', '象限数据', '数据分析负责人', '数据分析,SQL,业务理解', '简历诊断,项目表达,岗位方向选择', '偏业务分析与数据岗位，擅长把课程项目改写成带业务假设和数据结论的经历。', '适合数据分析、BI、商业分析等方向，想提升项目业务感的人。', '不适合完全没有数据项目、也暂时不愿意补案例的人。', '简历、数据项目文档、可展示的分析结论或图表。', '通常 24 小时内回复，项目拆解类会按优先级给修改清单。', 13900, 1, 'APPROVED', 14, 4.78, 88, 26, NULL),
  ('mentor.xuzhe@bishe.local', '导师徐喆', '徐喆', '飞书教育', '测试开发专家', '测试开发,自动化测试,职业规划', '校招投递策略,模拟面试复盘,岗位方向选择', '聚焦测试开发、质量保障和校招规划，擅长把测试项目讲出工程价值。', '适合测试开发、质量工程、自动化测试方向学生。', '不适合把测试经历只当成补位项、不愿意深入工程细节的人。', '简历、项目职责、测试方案或自动化脚本说明。', '一般 12-18 小时内回复，投递策略类会直接给批次建议。', 12900, 1, 'APPROVED', 12, 4.73, 82, 10, NULL),
  ('mentor.wenrao@bishe.local', '导师温饶', '温饶', '星河出行', '算法工程师', '算法岗,笔试复盘,简历诊断', '简历诊断,模拟面试复盘,校招投递策略', '擅长算法岗简历打磨、笔试复盘和面试表达，强调把竞赛与项目经历讲清楚。', '适合算法、推荐、搜索方向，已有算法题和项目基础的同学。', '不适合希望短时间从零冲刺算法岗但没有任何基础的人。', '简历、刷题记录、比赛或项目摘要。', '通常在 24 小时内回复，会区分简历问题和笔试问题分别给建议。', 17900, 1, 'APPROVED', 10, 4.69, 78, 42, NULL),
  ('mentor.qiuyu@bishe.local', '导师邱聿', '邱聿', '岚桥云', '资深后端工程师', 'Redis,MySQL,微服务', '项目表达,模拟面试复盘,简历诊断', '擅长后端项目复盘、系统设计表达和常见缓存数据库问题的结构化回答。', '适合做过服务端项目，想把“为什么这么设计”说清楚的学生。', '不适合只有 CRUD 项目、又不愿意补充场景和指标的人。', '简历、项目架构图或文字说明、目标岗位 JD。', '一般工作日夜间回复，第一轮多给结构建议，第二轮补细节。', 16900, 1, 'APPROVED', 22, 4.91, 76, 8, NULL),
  ('mentor.yeqing@bishe.local', '导师叶青', '叶青', '风帆资本', '战略分析经理', '商业分析,职业规划,转行求职', '转行 / 跨专业求职,岗位方向选择,Offer 对比与决策', '适合跨专业、转岗位和偏分析类求职，强调路径选择与材料取舍。', '适合想从文科、理工其他方向转产品、分析、运营岗位的人。', '不适合只想问一句“能不能转”而没有任何背景材料的人。', '简历、当前背景、目标岗位列表、最担心的转行问题。', '一般 24 小时内回复，路径建议会按短期和中期拆开写。', 18900, 1, 'APPROVED', 9, 4.76, 70, 60, NULL),
  ('mentor.jiangnan@bishe.local', '导师江楠', '江楠', '追光智能', 'NLP 工程师', 'AI 应用,Prompt 工程,项目复盘', '项目表达,模拟面试复盘,转行 / 跨专业求职', '主要帮助 AI 应用方向学生梳理项目故事线、技术边界与面试表达。', '适合做过大模型应用、AI 产品或相关课程项目的同学。', '不适合只有概念理解、没有做过任何 AI 相关实践的人。', '简历、项目简介、模型与数据集说明、目标岗位 JD。', '通常当天内回复，AI 项目会特别强调真实限制与评估方法。', 19900, 1, 'APPROVED', 8, 4.82, 68, 12, NULL),
  ('mentor.luoman@bishe.local', '导师骆蔓', '骆蔓', '轻舟电商', '运营负责人', '运营增长,数据复盘,表达结构', '岗位方向选择,简历诊断,校招投递策略', '擅长运营和增长方向简历、项目亮点梳理与校招节奏建议。', '适合运营、增长、活动策划、用户研究方向学生。', '不适合完全没有任何项目或校园经历的同学。', '简历、项目结果、数据截图或复盘结论。', '一般 24 小时内给到一版修改建议，数据表达会单独标注。', 11900, 1, 'APPROVED', 11, 4.72, 66, 20, NULL),
  ('mentor.cenxi@bishe.local', '导师岑溪', '岑溪', '伏笔安全', '安全研发工程师', '安全研发,CTF,简历诊断', '简历诊断,项目表达,模拟面试复盘', '关注安全研发和攻防方向求职，擅长把比赛与项目经历讲得更贴近岗位。', '适合安全、渗透、攻防、开发安全方向学生。', '不适合只想问刷题路线、不愿意整理项目经历的人。', '简历、项目经历、比赛记录或安全研究摘要。', '通常 24 小时内回复，简历和面试问题会分段给建议。', 16900, 1, 'APPROVED', 7, 4.71, 62, 14, NULL),
  ('mentor.peiyu@bishe.local', '导师裴聿', '裴聿', '深途机器人', '嵌入式工程师', '嵌入式,单片机,项目表达', '项目表达,岗位方向选择,校招投递策略', '主要帮助嵌入式、硬件相关学生梳理项目表达和岗位选择。', '适合做过单片机、嵌入式控制、机器人项目的人。', '不适合完全没有硬件项目、只想泛泛聊职业规划的人。', '简历、硬件项目描述、目标岗位列表。', '一般 24 小时内回复，项目表达会强调约束条件和测试结果。', 14900, 1, 'APPROVED', 6, 4.68, 58, 44, NULL),
  ('mentor.ningzhou@bishe.local', '导师宁舟', '宁舟', '海森游戏', '客户端主程', 'C++,图形基础,项目复盘', '简历诊断,模拟面试复盘,Offer 对比与决策', '适合客户端、图形和游戏方向学生，强调项目复杂度、性能瓶颈和工程取舍。', '适合准备客户端开发、引擎、图形相关岗位的人。', '不适合缺少任何相关项目、仅凭兴趣咨询的人。', '简历、项目截图、关键模块说明、意向岗位。', '通常当天深夜统一回复，技术追问会补充到第二轮。', 18900, 1, 'APPROVED', 13, 4.86, 55, 36, NULL),
  ('mentor.muyan@bishe.local', '导师穆言', '穆言', '时序医疗', '数据产品经理', '数据产品,项目拆解,转行建议', '转行 / 跨专业求职,项目表达,岗位方向选择', '擅长帮助理工背景学生转向数据产品、B 端产品或业务分析方向。', '适合想从技术转产品，或想把项目讲成产品闭环的学生。', '不适合没有任何项目、只希望快速包装简历的人。', '简历、做过的项目、目标岗位描述。', '通常 24 小时内回复，会先判断方向再给表达建议。', 15900, 1, 'APPROVED', 9, 4.79, 52, 48, NULL),
  ('mentor.yufei@bishe.local', '导师俞斐', '俞斐', '云图工业', '解决方案架构师', '解决方案,ToB 沟通,职业规划', '岗位方向选择,Offer 对比与决策,项目表达', '偏 ToB 和解决方案方向，擅长讲清项目落地、客户场景和沟通方式。', '适合售前、解决方案、客户成功、B 端产品相关方向。', '不适合只想改几句措辞、不愿意讨论场景和对象的人。', '简历、项目场景、沟通对象和成果说明。', '一般 24 小时内回复，ToB 材料会重点看“对象-问题-动作-结果”。', 17900, 1, 'APPROVED', 8, 4.77, 49, 72, NULL),
  ('mentor.shuqin@bishe.local', '导师舒沁', '舒沁', '萤火财务', '高级前端工程师', '前端工程化,跨端开发,简历诊断', '简历诊断,项目表达,模拟面试复盘', '擅长前端工程化、跨端项目和校招面试表达，重视项目复杂度和协作价值。', '适合前端、移动端、跨端开发方向学生。', '不适合只有静态页面练习、没有完整项目的人。', '简历、项目仓库或截图、目标岗位 JD。', '通常 12 小时内回复，简历结构和项目讲法会一起改。', 13900, 1, 'APPROVED', 15, 4.83, 46, 5, NULL),
  ('mentor.tangxi@bishe.local', '导师唐汐', '唐汐', '墨羽网络', '校招面试官', '校招评估,简历结构,投递节奏', '校招投递策略,简历诊断,Offer 对比与决策', '长期参与校招流程，擅长评估简历完整度、投递节奏和 Offer 决策。', '适合秋招、春招阶段对节奏和策略没有把握的学生。', '不适合只想问行业八卦、不愿意整理当前进度的人。', '简历、当前投递记录、已面试岗位和结果。', '一般当天给策略判断，第二天补更具体的简历修改意见。', 12900, 1, 'APPROVED', 18, 4.89, 44, 4, NULL),
  ('mentor.haoran@bishe.local', '导师郝然', '郝然', '见远智库', '商业研究员', '商业研究,咨询表达,职业规划', '岗位方向选择,转行 / 跨专业求职,项目表达', '偏商业研究与咨询表达，适合把研究、竞赛、案例拆成岗位可读材料。', '适合商业分析、咨询、研究类岗位或跨专业求职学生。', '不适合缺少任何研究或项目材料、希望纯聊天式咨询的人。', '简历、研究报告摘要、案例分析或竞赛经历。', '通常 24 小时内回复，材料会按逻辑链条拆开点评。', 14900, 0, 'REJECTED', 5, 4.55, 40, 96, '公司在职证明与服务经历说明不完整，请补充可验证材料后再提交。'),
  ('mentor.jiayi@bishe.local', '导师贾奕', '贾奕', '探索汽车', '资深后端工程师', '高并发,系统设计,项目复盘', '项目表达,模拟面试复盘,Offer 对比与决策', '擅长帮助后端学生把复杂链路、容量预估和稳定性设计讲清楚。', '适合做过服务端或分布式项目、准备冲中大厂后端岗位的人。', '不适合只有课程实验、又不愿意补场景和数据的人。', '简历、系统设计题记录、项目拆解文档。', '通常 24 小时内回复，系统设计类问题会补充参考框架。', 19900, 1, 'APPROVED', 21, 4.92, 38, 3, NULL),
  ('mentor.ruoxi@bishe.local', '导师若溪', '若溪', '白塔影像', '算法产品经理', '算法产品,跨学科求职,简历诊断', '转行 / 跨专业求职,简历诊断,岗位方向选择', '擅长算法产品、AI 产品和跨学科求职表达，强调背景差异与可迁移能力。', '适合从理工、医工、统计等方向转 AI 产品或数据产品的学生。', '不适合尚未明确目标方向、也不愿意先做岗位筛选的人。', '简历、项目经历、目标岗位清单、最担心的转行问题。', '一般 24 小时内回复，方向判断与简历修改会同步给出。', 15900, 1, 'APPROVED', 7, 4.74, 34, 28, NULL),
  ('mentor.anxu@bishe.local', '导师安序', '安序', '澄观科技', '工程效能平台主管', 'DevOps,工程效能,管理视角', '项目表达,岗位方向选择,Offer 对比与决策', '偏平台工程、工程效率与组织协作，适合希望补“项目管理和平台价值”表达的学生。', '适合 DevOps、平台工程、基础架构和工程效能方向学生。', '不适合只关心八股、不愿讲协作与交付影响的人。', '简历、项目职责、CI/CD 或平台建设相关说明。', '当前暂停接新单，恢复接单后会按排队顺序回复。', 17900, 0, 'APPROVED', 11, 4.70, 32, 168, NULL),
  ('mentor.kexin@bishe.local', '导师柯昕', '柯昕', '晓鹿教育', '用户增长经理', '增长策略,数据分析,表达复盘', '岗位方向选择,校招投递策略,项目表达', '主要帮助运营增长方向学生梳理数据复盘、活动结果和投递策略。', '适合运营、增长、内容策略和用户研究方向学生。', '不适合没有任何校园或实习项目、只想快速改措辞的人。', '简历、项目结果、数据指标、目前投递进展。', '当前排期较满，暂停接新咨询；资料建议仍可先准备。', 11900, 0, 'APPROVED', 9, 4.67, 30, 144, NULL),
  ('mentor.zishu@bishe.local', '导师子书', '子书', '临界设计', '设计策略顾问', 'UX,转产品,作品集', '转行 / 跨专业求职,项目表达,简历诊断', '偏设计转产品、作品集叙事和用户研究表达，适合跨方向求职。', '适合设计、交互、服务设计背景想转产品或研究岗位的人。', '不适合没有作品或项目材料、还不愿意梳理目标方向的人。', '简历、作品集链接、项目说明和目标岗位。', '资料已提交审核，期间可先整理作品集与项目复盘。', 14900, 0, 'PENDING', 0, 0.00, 14, 60, NULL),
  ('mentor.yunhe@bishe.local', '导师云和', '云和', '澜石资本', '投资分析师', '金融求职,Offer 选择,职业规划', 'Offer 对比与决策,岗位方向选择,转行 / 跨专业求职', '聚焦金融、投资、研究类岗位求职和 Offer 选择，强调经历匹配与节奏规划。', '适合金融、经管背景，或希望转向研究分析类岗位的学生。', '不适合完全不清楚行业方向、也不愿意先筛岗位的人。', '简历、已投岗位、面试反馈、Offer 情况。', '当前处于审核中，建议先把投递记录和项目经历整理完整。', 18900, 0, 'PENDING', 0, 0.00, 11, 84, NULL);

INSERT INTO users (
  email, password_hash, role, tier, status, display_name, real_name,
  last_login_at, is_deleted, created_at, updated_at
)
SELECT
  s.email,
  @seed_password_hash,
  'MENTOR',
  'PREMIUM',
  'ACTIVE',
  s.display_name,
  s.real_name,
  DATE_SUB(@seed_now, INTERVAL s.last_login_hours_ago HOUR),
  0,
  DATE_SUB(@seed_now, INTERVAL s.created_days_ago DAY),
  DATE_SUB(@seed_now, INTERVAL LEAST(s.last_login_hours_ago, s.created_days_ago * 24) HOUR)
FROM seed_mentor_catalog s
ON DUPLICATE KEY UPDATE
  password_hash = VALUES(password_hash),
  role = VALUES(role),
  tier = VALUES(tier),
  status = VALUES(status),
  display_name = VALUES(display_name),
  real_name = VALUES(real_name),
  last_login_at = VALUES(last_login_at),
  is_deleted = VALUES(is_deleted),
  updated_at = VALUES(updated_at);

INSERT INTO mentor_profiles (
  user_id, expertise_tags, service_scenes, bio, suitable_for, not_suitable_for,
  prep_materials, reply_rhythm, price_fen, is_available, company_name, job_title,
  approval_status, total_orders, avg_rating, created_at, updated_at
)
SELECT
  u.id,
  s.expertise_tags,
  s.service_scenes,
  s.bio,
  s.suitable_for,
  s.not_suitable_for,
  s.prep_materials,
  s.reply_rhythm,
  s.price_fen,
  s.is_available,
  s.company_name,
  s.job_title,
  s.approval_status,
  s.total_orders,
  s.avg_rating,
  DATE_SUB(@seed_now, INTERVAL s.created_days_ago DAY),
  DATE_SUB(@seed_now, INTERVAL LEAST(s.last_login_hours_ago, s.created_days_ago * 24) HOUR)
FROM seed_mentor_catalog s
JOIN users u ON u.email = s.email
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
  approval_status = VALUES(approval_status),
  total_orders = VALUES(total_orders),
  avg_rating = VALUES(avg_rating),
  updated_at = VALUES(updated_at);

UPDATE mentor_profiles mp
JOIN users u ON u.id = mp.user_id
SET mp.avatar_url = CASE u.email
  WHEN 'mentor.chengyanbei@bishe.local' THEN 'https://api.dicebear.com/7.x/notionists/svg?seed=ChengYanBei'
  WHEN 'mentor.linqiao@bishe.local' THEN 'https://api.dicebear.com/7.x/notionists/svg?seed=LinQiao'
  WHEN 'mentor.songyue@bishe.local' THEN 'https://api.dicebear.com/7.x/notionists/svg?seed=SongYue'
  WHEN 'mentor.hejin@bishe.local' THEN 'https://api.dicebear.com/7.x/notionists/svg?seed=HeJin'
  WHEN 'mentor.xuzhe@bishe.local' THEN 'https://api.dicebear.com/7.x/notionists/svg?seed=XuZhe'
  WHEN 'mentor.wenrao@bishe.local' THEN 'https://api.dicebear.com/7.x/notionists/svg?seed=WenRao'
  WHEN 'mentor.qiuyu@bishe.local' THEN 'https://api.dicebear.com/7.x/notionists/svg?seed=QiuYu'
  WHEN 'mentor.yeqing@bishe.local' THEN 'https://api.dicebear.com/7.x/notionists/svg?seed=YeQing'
  WHEN 'mentor.jiangnan@bishe.local' THEN 'https://api.dicebear.com/7.x/notionists/svg?seed=JiangNan'
  WHEN 'mentor.luoman@bishe.local' THEN 'https://api.dicebear.com/7.x/notionists/svg?seed=LuoMan'
  WHEN 'mentor.cenxi@bishe.local' THEN 'https://api.dicebear.com/7.x/notionists/svg?seed=CenXi'
  WHEN 'mentor.peiyu@bishe.local' THEN 'https://api.dicebear.com/7.x/notionists/svg?seed=PeiYu'
  WHEN 'mentor.ningzhou@bishe.local' THEN 'https://api.dicebear.com/7.x/notionists/svg?seed=NingZhou'
  WHEN 'mentor.muyan@bishe.local' THEN 'https://api.dicebear.com/7.x/notionists/svg?seed=MuYan'
  WHEN 'mentor.yufei@bishe.local' THEN 'https://api.dicebear.com/7.x/notionists/svg?seed=YuFei'
  WHEN 'mentor.shuqin@bishe.local' THEN 'https://api.dicebear.com/7.x/notionists/svg?seed=ShuQin'
  WHEN 'mentor.tangxi@bishe.local' THEN 'https://api.dicebear.com/7.x/notionists/svg?seed=TangXi'
  WHEN 'mentor.haoran@bishe.local' THEN 'https://api.dicebear.com/7.x/notionists/svg?seed=HaoRan'
  WHEN 'mentor.jiayi@bishe.local' THEN 'https://api.dicebear.com/7.x/notionists/svg?seed=JiaYi'
  WHEN 'mentor.ruoxi@bishe.local' THEN 'https://api.dicebear.com/7.x/notionists/svg?seed=RuoXi'
  WHEN 'mentor.anxu@bishe.local' THEN 'https://api.dicebear.com/7.x/notionists/svg?seed=AnXu'
  WHEN 'mentor.kexin@bishe.local' THEN 'https://api.dicebear.com/7.x/notionists/svg?seed=KeXin'
  WHEN 'mentor.zishu@bishe.local' THEN 'https://api.dicebear.com/7.x/notionists/svg?seed=ZiShu'
  WHEN 'mentor.yunhe@bishe.local' THEN 'https://api.dicebear.com/7.x/notionists/svg?seed=YunHe'
  ELSE mp.avatar_url
END
WHERE u.email IN (
  SELECT email FROM seed_mentor_catalog
);

INSERT INTO certification_submissions (
  user_id, user_role, real_name, company_name, job_title, status, review_note,
  reviewed_by, reviewed_at, previous_submission_id, is_current, submitted_at, created_at, updated_at
)
SELECT
  u.id,
  'MENTOR',
  s.real_name,
  s.company_name,
  s.job_title,
  s.approval_status,
  CASE
    WHEN s.approval_status = 'APPROVED' THEN '资料审核通过，可正常公开展示导师名片。'
    WHEN s.approval_status = 'REJECTED' THEN COALESCE(s.review_note, '资料未通过审核，请补充可验证的服务经历与在职证明。')
    ELSE NULL
  END,
  CASE
    WHEN s.approval_status = 'PENDING' THEN NULL
    ELSE @seed_admin_user_id
  END,
  CASE
    WHEN s.approval_status = 'PENDING' THEN NULL
    ELSE DATE_SUB(@seed_now, INTERVAL GREATEST(s.created_days_ago - 1, 0) DAY)
  END,
  NULL,
  1,
  DATE_SUB(@seed_now, INTERVAL GREATEST(s.created_days_ago - 2, 0) DAY),
  DATE_SUB(@seed_now, INTERVAL GREATEST(s.created_days_ago - 2, 0) DAY),
  DATE_SUB(@seed_now, INTERVAL GREATEST(s.created_days_ago - 2, 0) DAY)
FROM seed_mentor_catalog s
JOIN users u ON u.email = s.email
WHERE NOT EXISTS (
  SELECT 1
  FROM certification_submissions cs
  WHERE cs.user_id = u.id
    AND cs.is_current = 1
);

CREATE TEMPORARY TABLE seed_mentor_students (
  email VARCHAR(255) NOT NULL,
  school_name VARCHAR(150) NOT NULL,
  school_name_key VARCHAR(160) NOT NULL,
  major VARCHAR(100) NOT NULL,
  grade VARCHAR(20) NOT NULL,
  target_position VARCHAR(100) NOT NULL,
  skill_tags VARCHAR(512) NOT NULL,
  self_intro TEXT NOT NULL,
  github VARCHAR(255) NULL,
  portfolio VARCHAR(255) NULL,
  portrait_tags_json TEXT NOT NULL,
  portrait_evidence_json TEXT NOT NULL,
  resume_trace_id VARCHAR(64) NOT NULL,
  resume_target_role VARCHAR(100) NOT NULL,
  resume_summary TEXT NOT NULL,
  resume_suggestions_json TEXT NOT NULL,
  resume_created_hours_ago INT NOT NULL,
  interview_session_id VARCHAR(64) NOT NULL,
  interview_target_role VARCHAR(100) NOT NULL,
  interview_score INT NOT NULL,
  interview_strengths_json TEXT NOT NULL,
  interview_weaknesses_json TEXT NOT NULL,
  interview_suggestions_json TEXT NOT NULL,
  interview_created_hours_ago INT NOT NULL
);

INSERT INTO seed_mentor_students (
  email, school_name, school_name_key, major, grade, target_position, skill_tags, self_intro,
  github, portfolio, portrait_tags_json, portrait_evidence_json,
  resume_trace_id, resume_target_role, resume_summary, resume_suggestions_json, resume_created_hours_ago,
  interview_session_id, interview_target_role, interview_score, interview_strengths_json,
  interview_weaknesses_json, interview_suggestions_json, interview_created_hours_ago
)
VALUES
  (
    'student.liujianing@bishe.local',
    '江城理工大学',
    '江城理工大学',
    '软件工程',
    '大四',
    'Java 后端开发实习生',
    'Java,Spring Boot,Redis,MySQL,系统设计',
    '最近在整理后端项目简历，希望把库存、订单和缓存一致性相关经历讲得更像真实业务项目，也想补强系统设计回答结构。',
    'https://github.com/liujianing-dev',
    NULL,
    '[{"code":"TARGET_DIRECTION_BACKEND","label":"后端求职导向","source":"PROFILE","confidence":0.9},{"code":"RESUME_EXPRESSION_NEEDS_IMPROVEMENT","label":"简历成果表达待强化","source":"AI_RESUME","confidence":0.86},{"code":"INTERVIEW_SYSTEM_DESIGN_GAP","label":"系统设计表达待补强","source":"AI_INTERVIEW","confidence":0.88}]',
    '{"targetPosition":"Java 后端开发实习生","skillTags":"Java,Spring Boot,Redis,MySQL,系统设计","resumeFocus":"补量化结果和技术取舍","interviewFocus":"缓存一致性与系统设计表达"}',
    'trc_mqa_resume_liujianing_001',
    'Java 后端开发实习生',
    '项目经历仍偏流水账，建议补充业务目标、量化结果与技术取舍。',
    '["补充量化指标","把技术取舍讲清楚","突出订单与缓存链路的业务背景"]',
    30,
    'sess_mqa_reco_liujianing_001',
    'Java 后端开发实习生',
    83,
    '["项目背景能快速说明白","对数据库基本概念比较熟悉"]',
    '["缓存一致性解释不稳","系统设计回答结构偏散"]',
    '["先讲业务约束再讲方案","补充容量与回滚思路"]',
    26
  ),
  (
    'student.xuanran@bishe.local',
    '临海大学',
    '临海大学',
    '数字媒体技术',
    '大四',
    '前端开发工程师',
    'React,TypeScript,Vite,前端工程化,作品集表达',
    '目标是前端实习和校招，希望把管理台、数据看板和作品集讲得更完整，现在最担心项目亮点和性能优化成果写得不够立体。',
    'https://github.com/xuanran-ui',
    'https://xuanran-portfolio.example.com',
    '[{"code":"TARGET_DIRECTION_FRONTEND","label":"前端求职导向","source":"PROFILE","confidence":0.88},{"code":"RESUME_PROJECT_EXPRESSION_NEEDS_IMPROVEMENT","label":"简历项目表达待强化","source":"AI_RESUME","confidence":0.84},{"code":"INTERVIEW_PROJECT_EXPRESSION_GAP","label":"项目表达存在短板","source":"AI_INTERVIEW","confidence":0.86}]',
    '{"targetPosition":"前端开发工程师","skillTags":"React,TypeScript,Vite,前端工程化,作品集表达","resumeFocus":"项目复杂度与性能优化表达","interviewFocus":"项目亮点和协作价值"}',
    'trc_mqa_resume_xuanran_001',
    '前端开发工程师',
    '项目前端复杂度与性能优化成果表达还不够集中，建议补充交互难点和结果指标。',
    '["突出复杂交互场景","补充性能优化结果","把作品集入口前置"]',
    28,
    'sess_mqa_reco_xuanran_001',
    '前端开发工程师',
    85,
    '["项目整体节奏感不错","对组件拆分思路比较清楚"]',
    '["项目亮点提炼不够集中","回答时容易先讲实现后讲结果"]',
    '["先给结论再展开细节","把性能优化前后对比讲具体"]',
    24
  ),
  (
    'student.zhoumuyang@bishe.local',
    '北川财经大学',
    '北川财经大学',
    '信息管理与信息系统',
    '研一',
    '数据分析实习生',
    'SQL,Excel,Tableau,Python,业务分析',
    '想投数据分析和商业分析方向，目前课程项目比较多，正在补业务问题定义、指标结论和分析故事线的表达。',
    'https://github.com/zhoumuyang-bi',
    NULL,
    '[{"code":"DATA_ANALYSIS_ORIENTATION","label":"数据分析导向","source":"PROFILE","confidence":0.87},{"code":"RESUME_DIRECTION_NEEDS_ALIGNMENT","label":"简历岗位匹配仍需收口","source":"AI_RESUME","confidence":0.78},{"code":"INTERVIEW_COMMUNICATION_GAP","label":"面试表达结构待补强","source":"AI_INTERVIEW","confidence":0.82}]',
    '{"targetPosition":"数据分析实习生","skillTags":"SQL,Excel,Tableau,Python,业务分析","resumeFocus":"业务问题与结论表达","interviewFocus":"回答结构和结论先行"}',
    'trc_mqa_resume_zhoumuyang_001',
    '数据分析实习生',
    '项目结论与岗位匹配度还可以继续收口，建议强化业务问题定义和分析结论的结构。',
    '["先写业务问题再写分析动作","补充关键指标口径","让结论更贴近目标岗位"]',
    36,
    'sess_mqa_reco_zhoumuyang_001',
    '数据分析实习生',
    80,
    '["数据清洗思路比较完整","对图表展示有基础"]',
    '["回答结构偏散","业务问题定义不够聚焦"]',
    '["先给一句结论","补充指标变化和业务含义"]',
    32
  ),
  (
    'student.heqingyan@bishe.local',
    '华南科技学院',
    '华南科技学院',
    '计算机科学与技术',
    '大四',
    '后端开发工程师',
    'Java,MySQL,Redis,数据埋点,项目复盘',
    '最近在准备后端校招，既想补项目表达，也想把埋点分析、接口联调和排障经历整理成更有判断力的个人贡献。',
    'https://github.com/heqingyan-dev',
    NULL,
    '[{"code":"TARGET_DIRECTION_BACKEND","label":"后端求职导向","source":"PROFILE","confidence":0.9},{"code":"RESUME_PROJECT_EXPRESSION_NEEDS_IMPROVEMENT","label":"简历项目表达待强化","source":"AI_RESUME","confidence":0.84},{"code":"INTERVIEW_PROJECT_EXPRESSION_GAP","label":"项目表达存在短板","source":"AI_INTERVIEW","confidence":0.86}]',
    '{"targetPosition":"后端开发工程师","skillTags":"Java,MySQL,Redis,数据埋点,项目复盘","resumeFocus":"联调与排障价值表达","interviewFocus":"项目贡献和设计取舍"}',
    'trc_mqa_resume_heqingyan_001',
    '后端开发工程师',
    '接口联调、排障和埋点分析的个人贡献还不够突出，建议按问题、动作、结果重写项目经历。',
    '["把联调与排障写成问题解决闭环","补充埋点分析的业务结果","强调个人判断和推进动作"]',
    22,
    'sess_mqa_reco_heqingyan_001',
    '后端开发工程师',
    84,
    '["项目问题意识不错","对接口链路比较熟悉"]',
    '["项目贡献还不够聚焦","设计取舍解释不够有层次"]',
    '["先讲问题背景再讲动作","把设计选择和结果绑定起来"]',
    20
  );

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
  DATE_SUB(@seed_now, INTERVAL 6 HOUR)
FROM seed_mentor_students s
JOIN users u ON u.email = s.email
ON DUPLICATE KEY UPDATE
  school_name = VALUES(school_name),
  school_name_key = VALUES(school_name_key),
  major = VALUES(major),
  grade = VALUES(grade),
  target_position = VALUES(target_position),
  skill_tags = VALUES(skill_tags),
  self_intro = VALUES(self_intro),
  github = VALUES(github),
  portfolio = VALUES(portfolio),
  updated_at = VALUES(updated_at);

INSERT INTO student_portrait_snapshots (student_user_id, portrait_tags, evidence, updated_at)
SELECT
  u.id,
  s.portrait_tags_json,
  s.portrait_evidence_json,
  DATE_SUB(@seed_now, INTERVAL 4 HOUR)
FROM seed_mentor_students s
JOIN users u ON u.email = s.email
ON DUPLICATE KEY UPDATE
  portrait_tags = VALUES(portrait_tags),
  evidence = VALUES(evidence),
  updated_at = VALUES(updated_at);

INSERT INTO ai_call_logs (
  trace_id, user_id, task_type, provider, model, latency_ms, status, error_code,
  request_tokens, response_tokens, total_tokens, estimated_cost, user_tier, created_at,
  charged_points, quota_weight, result_summary, result_payload_json, user_deleted_at
)
SELECT
  s.resume_trace_id,
  u.id,
  'RESUME',
  'openai-compatible',
  'gpt-4o-mini',
  1420,
  'SUCCESS',
  NULL,
  780,
  320,
  1100,
  0.003900,
  COALESCE(u.tier, 'FREE'),
  DATE_SUB(@seed_now, INTERVAL s.resume_created_hours_ago HOUR),
  4,
  3,
  s.resume_summary,
  CONCAT(
    '{"targetRole":', QUOTE(s.resume_target_role),
    ',"summary":', QUOTE(s.resume_summary),
    ',"suggestions":', s.resume_suggestions_json,
    '}'
  ),
  NULL
FROM seed_mentor_students s
JOIN users u ON u.email = s.email
WHERE NOT EXISTS (
  SELECT 1
  FROM ai_call_logs logs
  WHERE logs.trace_id = s.resume_trace_id
);

INSERT INTO interview_sessions (
  session_id, student_user_id, target_role, mode, status, created_at, updated_at,
  reply_round_limit, reply_round_used, summary_generated, prepaid_points, reserved_quota_weight,
  summary_overall_score, summary_strengths_json, summary_weaknesses_json, summary_suggestions_json,
  summary_provider, summary_model, summary_latency_ms, summary_generated_at, finish_reason, ended_by_ai, user_deleted_at
)
SELECT
  s.interview_session_id,
  u.id,
  s.interview_target_role,
  'INTERVIEW_TEXT',
  'COMPLETED',
  DATE_SUB(@seed_now, INTERVAL s.interview_created_hours_ago HOUR),
  DATE_SUB(@seed_now, INTERVAL GREATEST(s.interview_created_hours_ago - 1, 0) HOUR),
  8,
  4,
  1,
  10,
  5,
  s.interview_score,
  s.interview_strengths_json,
  s.interview_weaknesses_json,
  s.interview_suggestions_json,
  'openai-compatible',
  'gpt-4o-mini',
  1680,
  DATE_SUB(@seed_now, INTERVAL GREATEST(s.interview_created_hours_ago - 1, 0) HOUR),
  'ROUND_LIMIT',
  1,
  NULL
FROM seed_mentor_students s
JOIN users u ON u.email = s.email
ON DUPLICATE KEY UPDATE
  target_role = VALUES(target_role),
  status = VALUES(status),
  updated_at = VALUES(updated_at),
  reply_round_limit = VALUES(reply_round_limit),
  reply_round_used = VALUES(reply_round_used),
  summary_generated = VALUES(summary_generated),
  prepaid_points = VALUES(prepaid_points),
  reserved_quota_weight = VALUES(reserved_quota_weight),
  summary_overall_score = VALUES(summary_overall_score),
  summary_strengths_json = VALUES(summary_strengths_json),
  summary_weaknesses_json = VALUES(summary_weaknesses_json),
  summary_suggestions_json = VALUES(summary_suggestions_json),
  summary_provider = VALUES(summary_provider),
  summary_model = VALUES(summary_model),
  summary_latency_ms = VALUES(summary_latency_ms),
  summary_generated_at = VALUES(summary_generated_at),
  finish_reason = VALUES(finish_reason),
  ended_by_ai = VALUES(ended_by_ai),
  user_deleted_at = VALUES(user_deleted_at);

CREATE TEMPORARY TABLE seed_mentor_interview_messages (
  session_id VARCHAR(64) NOT NULL,
  sender_role VARCHAR(20) NOT NULL,
  message_text TEXT NOT NULL,
  coach_feedback TEXT NULL,
  score_hint INT NULL,
  created_hours_ago INT NOT NULL
);

INSERT INTO seed_mentor_interview_messages(session_id, sender_role, message_text, coach_feedback, score_hint, created_hours_ago)
VALUES
  ('sess_mqa_reco_liujianing_001', 'INTERVIEWER', '请你介绍一个最能代表你后端能力的项目。', NULL, NULL, 26),
  ('sess_mqa_reco_liujianing_001', 'STUDENT', '我负责过订单和库存链路，但现在讲出来还像课程作业。', NULL, 74, 26),
  ('sess_mqa_reco_liujianing_001', 'INTERVIEWER', '如果追问缓存一致性，你会怎么展开？', NULL, NULL, 25),
  ('sess_mqa_reco_liujianing_001', 'STUDENT', '我会先讲读多写少和双写风险，不过现在还不够稳。', '可以先交代约束条件，再讲为什么选缓存方案。', 78, 25),
  ('sess_mqa_reco_xuanran_001', 'INTERVIEWER', '请说一个你最想在前端面试里重点讲的项目。', NULL, NULL, 24),
  ('sess_mqa_reco_xuanran_001', 'STUDENT', '我做过管理台和数据看板，但总觉得亮点不够聚焦。', NULL, 76, 24),
  ('sess_mqa_reco_xuanran_001', 'INTERVIEWER', '如果面试官追问性能优化，你会怎么讲结果？', NULL, NULL, 23),
  ('sess_mqa_reco_xuanran_001', 'STUDENT', '我会说做了懒加载和拆包，但还不会把结果对比讲得很具体。', '先给结果，再补具体动作和前后差异。', 79, 23),
  ('sess_mqa_reco_zhoumuyang_001', 'INTERVIEWER', '你会怎么解释一个数据分析项目的业务问题？', NULL, NULL, 32),
  ('sess_mqa_reco_zhoumuyang_001', 'STUDENT', '我通常先讲做了哪些清洗和分析，但结论有时不够聚焦。', '先用一句话说明业务问题和最终结论。', 75, 32),
  ('sess_mqa_reco_heqingyan_001', 'INTERVIEWER', '你如何证明联调和排障是你的核心贡献？', NULL, NULL, 20),
  ('sess_mqa_reco_heqingyan_001', 'STUDENT', '我能说出排查过程，但不太会把判断和结果写成闭环。', '把问题背景、动作和结果串成一条线。', 77, 20);

INSERT INTO interview_messages (
  session_pk, sender_role, message_text, coach_feedback, score_hint, audio_object_key, created_at
)
SELECT
  session.id,
  sim.sender_role,
  sim.message_text,
  sim.coach_feedback,
  sim.score_hint,
  NULL,
  DATE_SUB(@seed_now, INTERVAL sim.created_hours_ago HOUR)
FROM seed_mentor_interview_messages sim
JOIN interview_sessions session ON session.session_id = sim.session_id
WHERE NOT EXISTS (
  SELECT 1
  FROM interview_messages im
  WHERE im.session_pk = session.id
    AND im.sender_role = sim.sender_role
    AND im.message_text = sim.message_text
);

CREATE TEMPORARY TABLE seed_mentor_favorites (
  student_email VARCHAR(255) NOT NULL,
  mentor_email VARCHAR(255) NOT NULL,
  created_hours_ago INT NOT NULL
);

INSERT INTO seed_mentor_favorites(student_email, mentor_email, created_hours_ago)
VALUES
  ('student.liujianing@bishe.local', 'mentor.chengyanbei@bishe.local', 96),
  ('student.liujianing@bishe.local', 'mentor.linqiao@bishe.local', 72),
  ('student.liujianing@bishe.local', 'mentor.songyue@bishe.local', 48),
  ('student.liujianing@bishe.local', 'mentor.qiuyu@bishe.local', 24),
  ('student.liujianing@bishe.local', 'mentor.tangxi@bishe.local', 8),
  ('student.xuanran@bishe.local', 'mentor.shuqin@bishe.local', 60),
  ('student.xuanran@bishe.local', 'mentor.hejin@bishe.local', 54),
  ('student.xuanran@bishe.local', 'mentor.ruoxi@bishe.local', 30),
  ('student.xuanran@bishe.local', 'mentor.jiayi@bishe.local', 18),
  ('student.zhoumuyang@bishe.local', 'mentor.wenrao@bishe.local', 84),
  ('student.zhoumuyang@bishe.local', 'mentor.ningzhou@bishe.local', 58),
  ('student.zhoumuyang@bishe.local', 'mentor.cenxi@bishe.local', 36),
  ('student.heqingyan@bishe.local', 'mentor.yufei@bishe.local', 40),
  ('student.heqingyan@bishe.local', 'mentor.muyan@bishe.local', 28),
  ('student.heqingyan@bishe.local', 'mentor.anxu@bishe.local', 14);

INSERT IGNORE INTO mentor_favorites(student_user_id, mentor_user_id, created_at)
SELECT
  su.id,
  mu.id,
  DATE_SUB(@seed_now, INTERVAL sf.created_hours_ago HOUR)
FROM seed_mentor_favorites sf
JOIN users su ON su.email = sf.student_email
JOIN users mu ON mu.email = sf.mentor_email;

CREATE TEMPORARY TABLE seed_mentor_orders (
  order_no VARCHAR(64) NOT NULL,
  student_email VARCHAR(255) NOT NULL,
  mentor_email VARCHAR(255) NOT NULL,
  amount_fen INT NOT NULL,
  status VARCHAR(20) NOT NULL,
  scene_code VARCHAR(60) NULL,
  source_page VARCHAR(80) NULL,
  question_text TEXT NULL,
  problem_summary TEXT NULL,
  core_questions_json TEXT NULL,
  expected_outcomes_json TEXT NULL,
  selected_material_types VARCHAR(255) NULL,
  prep_sheet_snapshot_json TEXT NULL,
  created_hours_ago INT NOT NULL,
  paid_hours_ago INT NULL,
  closed_hours_ago INT NULL,
  appointment_day_offset INT NULL,
  appointment_start_time VARCHAR(8) NULL,
  appointment_end_time VARCHAR(8) NULL
);

INSERT INTO seed_mentor_orders (
  order_no, student_email, mentor_email, amount_fen, status, scene_code, source_page,
  question_text, problem_summary, core_questions_json, expected_outcomes_json,
  selected_material_types, prep_sheet_snapshot_json,
  created_hours_ago, paid_hours_ago, closed_hours_ago,
  appointment_day_offset, appointment_start_time, appointment_end_time
)
VALUES
  ('CONS-MQA-20260401-001', 'student.liujianing@bishe.local', 'mentor.chengyanbei@bishe.local', 15900, 'CLOSED', 'RESUME_DIAGNOSIS', 'MENTOR_MARKETPLACE_RECOMMENDATION', '我现在的后端项目描述太像课程作业，想把它讲得更像真实业务项目。', '希望把项目经历从“做了什么”升级成“为什么这么做、结果如何”。', '["怎样把库存与订单链路讲出业务背景","项目结果指标应该怎么写","面试追问系统设计时如何接住"]', '["得到一版可直接改进的项目表达框架","明确后端项目常见追问的准备方向"]', 'RESUME,JOB_DESCRIPTION,PROJECT_MATERIAL', '{"scene":"简历诊断","summaryDraft":"希望围绕后端项目补齐业务目标、关键决策与结果指标表达。","coreQuestions":["项目怎么体现复杂度","如何解释设计取舍"],"suggestedMaterials":["最新简历","目标岗位 JD","项目结构图"],"expectedOutcomes":["形成一版更像真实业务项目的简历描述","明确面试追问的准备重点"]}', 320, 318, 272, NULL, NULL, NULL),
  ('CONS-MQA-20260401-002', 'student.xuanran@bishe.local', 'mentor.chengyanbei@bishe.local', 15900, 'ANSWERED', 'INTERVIEW_REVIEW', 'MENTOR_MARKETPLACE', '模拟面试时我总说不清为什么要用 Redis 和 MySQL 组合，想做一次复盘。', '希望把系统设计中的约束条件、缓存策略和排查思路讲清楚。', '["如何先讲业务约束再讲方案","缓存一致性怎么回答更稳","排查链路如何组织"]', '["拿到一套更稳定的系统设计回答框架","知道下一轮该重点练什么"]', 'RESUME,PROJECT_MATERIAL', '{"scene":"模拟面试复盘","summaryDraft":"围绕缓存与数据库组合方案做系统设计表达复盘。","coreQuestions":["如何解释方案选择","如何应对缓存一致性追问"],"suggestedMaterials":["项目介绍","最近面试复盘记录"],"expectedOutcomes":["形成稳定的回答框架","明确下一轮训练重点"]}', 78, 74, NULL, NULL, NULL, NULL),
  ('CONS-MQA-20260401-003', 'student.zhoumuyang@bishe.local', 'mentor.linqiao@bishe.local', 14900, 'CLOSED', 'PROJECT_EXPRESSION', 'MENTOR_MARKETPLACE', '我做了前端管理台和数据可视化项目，但简历上看起来太平。', '希望把前端项目里的复杂交互、性能优化和协作价值讲得更有层次。', '["前端项目怎么避免只写页面搭建","性能优化成果怎么写","如何讲团队协作"]', '["得到一版更完整的前端项目亮点表达","明确作品集要补什么内容"]', 'RESUME,PROJECT_MATERIAL,SUPPLEMENTARY', '{"scene":"项目表达","summaryDraft":"希望把前端项目中的复杂交互、性能优化和协作过程讲清楚。","coreQuestions":["前端项目如何写出复杂度","作品集要突出哪些内容"],"suggestedMaterials":["最新简历","项目截图","仓库链接"],"expectedOutcomes":["输出一版更立体的项目描述","确定作品集补充清单"]}', 210, 208, 154, NULL, NULL, NULL),
  ('CONS-MQA-20260401-004', 'student.heqingyan@bishe.local', 'mentor.hejin@bishe.local', 13900, 'REFUNDED', 'DATA_ANALYSIS', 'MENTOR_MARKETPLACE_RECOMMENDATION', '我准备投数据分析实习，但项目里大多是课程分析，没有业务感。', '希望把课程项目改成更贴近业务问题与分析结论的表达。', '["数据项目怎样写业务问题","分析结论要怎么呈现","图表和指标怎么取舍"]', '["拿到一版数据分析项目改写框架","明确后续需要补的数据和结论"]', 'RESUME,JOB_DESCRIPTION,PROJECT_MATERIAL', '{"scene":"项目表达","summaryDraft":"希望把数据分析项目补出业务问题和结论。","coreQuestions":["如何强调业务价值","图表和指标怎么写"],"suggestedMaterials":["简历","项目文档","岗位 JD"],"expectedOutcomes":["形成更像业务分析的项目描述","明确要补充的关键结论"]}', 18, 16, 6, 2, '20:00:00', '21:00:00'),
  ('CONS-MQA-20260401-005', 'student.linjiaqi@bishe.local', 'mentor.songyue@bishe.local', 16900, 'CLOSED', 'OFFER_DECISION', 'MENTOR_MARKETPLACE_FAVORITES', '我拿到了一个中厂产品实习和一个小厂增长岗，不知道怎么选。', '希望从成长性、带教、业务复杂度和后续转正可能性几个角度做判断。', '["Offer 选择时最该看什么","成长性怎么判断","产品和增长起步怎么选"]', '["得到一版 Offer 对比框架","明确我更适合先去哪类团队"]', 'RESUME,SUPPLEMENTARY', '{"scene":"Offer 对比与决策","summaryDraft":"希望比较两个 Offer 的成长性、带教和岗位匹配度。","coreQuestions":["如何比较成长路径","产品与增长的起步差异"],"suggestedMaterials":["简历","Offer 信息","面试反馈"],"expectedOutcomes":["形成清晰的 Offer 对比框架","明确短期选择建议"]}', 192, 190, 168, NULL, NULL, NULL),
  ('CONS-MQA-20260401-006', 'student.liujianing@bishe.local', 'mentor.qiuyu@bishe.local', 16900, 'REFUNDED', 'INTERVIEW_REVIEW', 'MENTOR_MARKETPLACE', '我约了系统设计复盘，但这周时间冲突，希望先退款。', '订单已支付，但后续无法按原计划完成咨询。', '["是否能改期","退款流程如何走"]', '["确认退款状态","保留后续再约的可能"]', 'RESUME,PROJECT_MATERIAL', '{"scene":"模拟面试复盘","summaryDraft":"原计划做系统设计复盘，当前因时间冲突需要处理退款。","coreQuestions":["是否可以改期","退款进度如何查看"],"suggestedMaterials":["订单信息","目前准备材料"],"expectedOutcomes":["明确退款处理节奏","保留后续再约可能"]}', 132, 130, 120, 1, '19:30:00', '20:30:00'),
  ('CONS-MQA-20260401-007', 'student.heqingyan@bishe.local', 'mentor.shuqin@bishe.local', 13900, 'CANCELED', 'RESUME_DIAGNOSIS', 'MENTOR_MARKETPLACE', '我想先下单做前端简历诊断，重点看项目表达和作品集顺序。', '需要确认简历结构、项目顺序和作品集入口是否合理。', '["前端简历项目顺序怎么排","作品集入口要不要单独强调"]', '["拿到一版简历结构建议","确认作品集展示顺序"]', 'RESUME,PROJECT_MATERIAL', '{"scene":"简历诊断","summaryDraft":"希望先做前端简历诊断，重点看项目表达和作品集顺序。","coreQuestions":["项目顺序怎么排","作品集入口如何展示"],"suggestedMaterials":["简历","作品集链接","目标 JD"],"expectedOutcomes":["形成更合理的简历结构","明确作品集的呈现顺序"]}', 6, NULL, 2, NULL, NULL, NULL),
  ('CONS-MQA-20260401-008', 'student.zhoumuyang@bishe.local', 'mentor.tangxi@bishe.local', 12900, 'CANCELED', 'DELIVERY_STRATEGY', 'MENTOR_MARKETPLACE_FAVORITES', '我想系统梳理春招投递节奏，但临时决定自己先跑一轮。', '订单未支付，先关闭保留后续再下单。', '["简历先改还是先投递","春招如何分批次投"]', '["保留后续再咨询的可能"]', 'RESUME,SUPPLEMENTARY', '{"scene":"校招投递策略","summaryDraft":"想梳理春招投递节奏，但决定先自己试一轮。","coreQuestions":["先改简历还是先投","如何分批次投递"],"suggestedMaterials":["简历","投递清单"],"expectedOutcomes":["明确后续何时再咨询"]}', 40, NULL, 12, NULL, NULL, NULL),
  ('CONS-MQA-20260401-009', 'student.xuanran@bishe.local', 'mentor.jiayi@bishe.local', 19900, 'CLOSED', 'INTERVIEW_REVIEW', 'MENTOR_MARKETPLACE_RECOMMENDATION', '我在后端面试里经常被问高并发和削峰限流，回答总是很散。', '希望得到一套更稳定的系统设计答题框架和复盘清单。', '["高并发问题如何先讲约束","削峰限流要怎么举例","系统设计回答怎么分层"]', '["形成更稳定的系统设计答题结构","明确后续面试重点"]', 'RESUME,PROJECT_MATERIAL,JOB_DESCRIPTION', '{"scene":"模拟面试复盘","summaryDraft":"围绕高并发、削峰限流和系统设计回答做复盘。","coreQuestions":["如何先讲约束再讲方案","如何组织系统设计回答"],"suggestedMaterials":["简历","系统设计题记录","岗位 JD"],"expectedOutcomes":["形成更稳定的答题框架","明确后续练习清单"]}', 174, 172, 126, NULL, NULL, NULL),
  ('CONS-MQA-20260401-010', 'student.linjiaqi@bishe.local', 'mentor.linqiao@bishe.local', 14900, 'ANSWERED', 'INTERVIEW_REVIEW', 'MENTOR_MARKETPLACE', '我最近的前端面试总卡在性能优化和工程化部分，想做一次针对性复盘。', '希望把性能优化、埋点和工程化实践讲清楚。', '["性能优化怎么讲得有证据","工程化实践怎么体现价值"]', '["形成一版更稳定的面试表达结构","知道作品集还缺哪些内容"]', 'RESUME,PROJECT_MATERIAL,SUPPLEMENTARY', '{"scene":"模拟面试复盘","summaryDraft":"希望围绕性能优化和工程化做前端面试复盘。","coreQuestions":["如何证明优化效果","工程化实践如何体现价值"],"suggestedMaterials":["简历","项目文档","面试复盘"],"expectedOutcomes":["形成更稳定的表达框架","明确作品集补充项"]}', 52, 50, NULL, 3, '19:00:00', '20:00:00'),
  ('CONS-MQA-20260401-011', 'student.liujianing@bishe.local', 'mentor.hejin@bishe.local', 13900, 'REFUNDED', 'PROJECT_EXPRESSION', 'MENTOR_MARKETPLACE', '我希望把数据分析项目里做过的 A/B 测试和指标拆得更完整。', '重点想改项目表达和图表结论呈现。', '["A/B 测试项目怎么写","分析结论应该呈现到什么程度"]', '["拿到项目改写清单","明确还需要补哪些指标和图表"]', 'RESUME,PROJECT_MATERIAL,SUPPLEMENTARY', '{"scene":"项目表达","summaryDraft":"希望把数据分析项目中的 A/B 测试和指标结论写得更完整。","coreQuestions":["A/B 测试项目如何写","结论要怎么呈现"],"suggestedMaterials":["简历","项目报告","图表"],"expectedOutcomes":["输出项目改写清单","明确补充的指标和图表"]}', 26, 24, 12, NULL, NULL, NULL),
  ('CONS-MQA-20260401-012', 'student.heqingyan@bishe.local', 'mentor.qiuyu@bishe.local', 16900, 'CLOSED', 'PROJECT_EXPRESSION', 'MENTOR_MARKETPLACE_RECOMMENDATION', '我希望把缓存和数据库设计相关的项目经历讲得更有层次。', '重点是补充场景、约束条件、设计取舍和问题排查。', '["缓存和数据库设计如何分层讲","如何解释排查和优化过程"]', '["形成一版更完整的后端项目表达","明确系统设计追问的准备方向"]', 'RESUME,PROJECT_MATERIAL,JOB_DESCRIPTION', '{"scene":"项目表达","summaryDraft":"希望把缓存和数据库设计相关项目讲得更完整。","coreQuestions":["如何分层讲设计取舍","如何解释排查过程"],"suggestedMaterials":["简历","项目结构图","岗位 JD"],"expectedOutcomes":["形成更完整的项目表达","明确系统设计追问准备重点"]}', 144, 142, 90, NULL, NULL, NULL);

INSERT INTO consult_orders (
  order_no, student_user_id, mentor_user_id, amount_fen, status, question_text,
  paid_at, closed_at, created_at, updated_at, appointment_start_at, appointment_end_at,
  scene_code, source_page, question_payload_json, problem_summary,
  core_questions_json, expected_outcomes_json, selected_material_types, prep_sheet_snapshot_json
)
SELECT
  so.order_no,
  su.id,
  mu.id,
  so.amount_fen,
  so.status,
  so.question_text,
  CASE WHEN so.paid_hours_ago IS NULL THEN NULL ELSE DATE_SUB(@seed_now, INTERVAL so.paid_hours_ago HOUR) END,
  CASE WHEN so.closed_hours_ago IS NULL THEN NULL ELSE DATE_SUB(@seed_now, INTERVAL so.closed_hours_ago HOUR) END,
  DATE_SUB(@seed_now, INTERVAL so.created_hours_ago HOUR),
  DATE_SUB(@seed_now, INTERVAL so.created_hours_ago HOUR),
  CASE
    WHEN so.appointment_day_offset IS NULL OR so.appointment_start_time IS NULL THEN NULL
    ELSE TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL so.appointment_day_offset DAY), so.appointment_start_time)
  END,
  CASE
    WHEN so.appointment_day_offset IS NULL OR so.appointment_end_time IS NULL THEN NULL
    ELSE TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL so.appointment_day_offset DAY), so.appointment_end_time)
  END,
  so.scene_code,
  so.source_page,
  JSON_OBJECT(
    'primaryConcern', so.problem_summary,
    'background', so.question_text,
    'attemptedActions', '已根据当前简历和项目材料做过一轮自查，需要导师进一步梳理表达结构。',
    'expectedHelp', '希望得到结构化修改建议和后续准备方向。',
    'additionalNotes', '本条为导师系统浏览器回归测试数据。'
  ),
  so.problem_summary,
  so.core_questions_json,
  so.expected_outcomes_json,
  so.selected_material_types,
  so.prep_sheet_snapshot_json
FROM seed_mentor_orders so
JOIN users su ON su.email = so.student_email
JOIN users mu ON mu.email = so.mentor_email
ON DUPLICATE KEY UPDATE
  student_user_id = VALUES(student_user_id),
  mentor_user_id = VALUES(mentor_user_id),
  amount_fen = VALUES(amount_fen),
  status = VALUES(status),
  question_text = VALUES(question_text),
  paid_at = VALUES(paid_at),
  closed_at = VALUES(closed_at),
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

DELETE FROM payment_records
WHERE order_no LIKE 'CONS-MQA-20260401-%';

CREATE TEMPORARY TABLE seed_mentor_payment_records (
  order_no VARCHAR(64) NOT NULL,
  channel VARCHAR(20) NOT NULL,
  mode VARCHAR(20) NOT NULL,
  provider_trade_no VARCHAR(128) NOT NULL,
  payment_status VARCHAR(20) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  created_hours_ago INT NOT NULL
);

INSERT INTO seed_mentor_payment_records (
  order_no, channel, mode, provider_trade_no, payment_status, idempotency_key, created_hours_ago
)
VALUES
  ('CONS-MQA-20260401-001', 'ALIPAY', 'SANDBOX', 'sandbox_cons_mqa_001_pay', 'SUCCESS', 'seed_cons_mqa_001_success', 318),
  ('CONS-MQA-20260401-002', 'ALIPAY', 'SANDBOX', 'sandbox_cons_mqa_002_pay', 'SUCCESS', 'seed_cons_mqa_002_success', 74),
  ('CONS-MQA-20260401-003', 'ALIPAY', 'SANDBOX', 'sandbox_cons_mqa_003_pay', 'SUCCESS', 'seed_cons_mqa_003_success', 208),
  ('CONS-MQA-20260401-004', 'ALIPAY', 'SANDBOX', 'sandbox_cons_mqa_004_pay', 'SUCCESS', 'seed_cons_mqa_004_success', 16),
  ('CONS-MQA-20260401-004', 'ALIPAY', 'SANDBOX', 'sandbox_cons_mqa_004_refund', 'REFUND_SUCCESS', 'seed_cons_mqa_004_refund', 6),
  ('CONS-MQA-20260401-005', 'ALIPAY', 'SANDBOX', 'sandbox_cons_mqa_005_pay', 'SUCCESS', 'seed_cons_mqa_005_success', 190),
  ('CONS-MQA-20260401-006', 'ALIPAY', 'SANDBOX', 'sandbox_cons_mqa_006_pay', 'SUCCESS', 'seed_cons_mqa_006_success', 130),
  ('CONS-MQA-20260401-006', 'ALIPAY', 'SANDBOX', 'sandbox_cons_mqa_006_refund', 'REFUND_SUCCESS', 'seed_cons_mqa_006_refund', 120),
  ('CONS-MQA-20260401-009', 'ALIPAY', 'SANDBOX', 'sandbox_cons_mqa_009_pay', 'SUCCESS', 'seed_cons_mqa_009_success', 172),
  ('CONS-MQA-20260401-010', 'ALIPAY', 'SANDBOX', 'sandbox_cons_mqa_010_pay', 'SUCCESS', 'seed_cons_mqa_010_success', 50),
  ('CONS-MQA-20260401-011', 'ALIPAY', 'SANDBOX', 'sandbox_cons_mqa_011_pay', 'SUCCESS', 'seed_cons_mqa_011_success', 24),
  ('CONS-MQA-20260401-011', 'ALIPAY', 'SANDBOX', 'sandbox_cons_mqa_011_refund', 'REFUND_SUCCESS', 'seed_cons_mqa_011_refund', 12),
  ('CONS-MQA-20260401-012', 'ALIPAY', 'SANDBOX', 'sandbox_cons_mqa_012_pay', 'SUCCESS', 'seed_cons_mqa_012_success', 142);

INSERT INTO payment_records (
  order_no, channel, mode, provider_trade_no, amount_fen, status, idempotency_key, created_at, updated_at
)
SELECT
  spr.order_no,
  spr.channel,
  spr.mode,
  spr.provider_trade_no,
  co.amount_fen,
  spr.payment_status,
  spr.idempotency_key,
  DATE_SUB(@seed_now, INTERVAL spr.created_hours_ago HOUR),
  DATE_SUB(@seed_now, INTERVAL spr.created_hours_ago HOUR)
FROM seed_mentor_payment_records spr
JOIN consult_orders co ON co.order_no = spr.order_no;

DELETE FROM consult_after_sales_requests
WHERE order_no IN (
  'CONS-MQA-20260401-004',
  'CONS-MQA-20260401-006',
  'CONS-MQA-20260401-011'
);

CREATE TEMPORARY TABLE seed_mentor_after_sales (
  order_no VARCHAR(64) NOT NULL,
  requester_email VARCHAR(255) NOT NULL,
  request_type VARCHAR(20) NOT NULL,
  status VARCHAR(20) NOT NULL,
  reason TEXT NOT NULL,
  review_note TEXT NULL,
  auto_triggered TINYINT(1) NOT NULL,
  created_hours_ago INT NOT NULL
);

INSERT INTO seed_mentor_after_sales (
  order_no, requester_email, request_type, status, reason, review_note, auto_triggered, created_hours_ago
)
VALUES
  ('CONS-MQA-20260401-004', 'student.heqingyan@bishe.local', 'REFUND', 'APPROVED', '导师在规定时限内未正式答复，系统已自动发起售后退款', '系统自动审批：导师超时未答', 1, 6),
  ('CONS-MQA-20260401-006', 'student.liujianing@bishe.local', 'REFUND', 'APPROVED', '学生本周时间冲突，申请退款并保留后续改约沟通。', '已确认学生主动退款诉求，后续可重新预约。', 0, 120),
  ('CONS-MQA-20260401-011', 'student.liujianing@bishe.local', 'REFUND', 'APPROVED', '导师在规定时限内未正式答复，系统已自动发起售后退款', '系统自动审批：导师超时未答', 1, 12);

INSERT INTO consult_after_sales_requests (
  order_no, requester_user_id, request_type, status, reason, review_note, reviewer_user_id, auto_triggered, reviewed_at, created_at, updated_at
)
SELECT
  sas.order_no,
  u.id,
  sas.request_type,
  sas.status,
  sas.reason,
  sas.review_note,
  @seed_admin_user_id,
  sas.auto_triggered,
  DATE_SUB(@seed_now, INTERVAL sas.created_hours_ago HOUR),
  DATE_SUB(@seed_now, INTERVAL sas.created_hours_ago HOUR),
  DATE_SUB(@seed_now, INTERVAL sas.created_hours_ago HOUR)
FROM seed_mentor_after_sales sas
JOIN users u ON u.email = sas.requester_email;

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

CREATE TEMPORARY TABLE seed_mentor_consult_notifications (
  user_email VARCHAR(255) NOT NULL,
  type VARCHAR(50) NOT NULL,
  title VARCHAR(160) NOT NULL,
  content TEXT NOT NULL,
  ref_id VARCHAR(64) NOT NULL,
  event_id VARCHAR(64) NOT NULL,
  created_hours_ago INT NOT NULL
);

INSERT INTO seed_mentor_consult_notifications (
  user_email, type, title, content, ref_id, event_id, created_hours_ago
)
VALUES
  ('student.heqingyan@bishe.local', 'CONSULT_REFUNDED', '咨询订单已退款', '导师超时未答，系统已自动发起售后并完成退款。', 'CONS-MQA-20260401-004', 'seed_mqa_004_student_refund', 6),
  ('mentor.hejin@bishe.local', 'CONSULT_PAYMENT_EXCEPTION', '支付异常订单已处理', '订单 CONS-MQA-20260401-004 因超时未答已自动退款，请确认排期释放。', 'CONS-MQA-20260401-004', 'seed_mqa_004_mentor_refund', 6),
  ('student.liujianing@bishe.local', 'CONSULT_REFUNDED', '咨询订单已退款', '订单 CONS-MQA-20260401-006 已完成退款，后续仍可重新预约。', 'CONS-MQA-20260401-006', 'seed_mqa_006_student_refund', 120),
  ('mentor.qiuyu@bishe.local', 'CONSULT_PAYMENT_EXCEPTION', '支付异常订单已处理', '订单 CONS-MQA-20260401-006 已按学生申请完成退款，请确认排期释放。', 'CONS-MQA-20260401-006', 'seed_mqa_006_mentor_refund', 120),
  ('student.liujianing@bishe.local', 'CONSULT_REFUNDED', '咨询订单已退款', '导师超时未答，系统已自动发起售后并完成退款。', 'CONS-MQA-20260401-011', 'seed_mqa_011_student_refund', 12),
  ('mentor.hejin@bishe.local', 'CONSULT_PAYMENT_EXCEPTION', '支付异常订单已处理', '订单 CONS-MQA-20260401-011 因超时未答已自动退款，请确认排期释放。', 'CONS-MQA-20260401-011', 'seed_mqa_011_mentor_refund', 12);

INSERT INTO notifications (
  user_id, type, category, title, content, ref_type, ref_id, action_code, priority, event_id, payload_json, is_read, created_at, updated_at
)
SELECT
  u.id,
  scn.type,
  'CONSULT',
  scn.title,
  scn.content,
  'CONSULT_ORDER',
  scn.ref_id,
  'VIEW_CONSULT_ORDER',
  'HIGH',
  scn.event_id,
  JSON_OBJECT('legacyCompat', TRUE, 'eventType', scn.type, 'sourceType', 'CONSULT_ORDER', 'sourceId', scn.ref_id),
  0,
  DATE_SUB(@seed_now, INTERVAL scn.created_hours_ago HOUR),
  DATE_SUB(@seed_now, INTERVAL scn.created_hours_ago HOUR)
FROM seed_mentor_consult_notifications scn
JOIN users u ON u.email = scn.user_email;

CREATE TEMPORARY TABLE seed_mentor_slots (
  mentor_email VARCHAR(255) NOT NULL,
  day_offset INT NOT NULL,
  start_time VARCHAR(8) NOT NULL,
  end_time VARCHAR(8) NOT NULL,
  status VARCHAR(20) NOT NULL,
  booked_order_no VARCHAR(64) NULL,
  created_hours_ago INT NOT NULL
);

INSERT INTO seed_mentor_slots(mentor_email, day_offset, start_time, end_time, status, booked_order_no, created_hours_ago)
VALUES
  ('mentor.chengyanbei@bishe.local', 1, '19:00:00', '20:00:00', 'AVAILABLE', NULL, 12),
  ('mentor.chengyanbei@bishe.local', 4, '20:00:00', '21:00:00', 'AVAILABLE', NULL, 12),
  ('mentor.linqiao@bishe.local', 2, '19:30:00', '20:30:00', 'AVAILABLE', NULL, 18),
  ('mentor.linqiao@bishe.local', 3, '19:00:00', '20:00:00', 'BOOKED', 'CONS-MQA-20260401-010', 18),
  ('mentor.songyue@bishe.local', 5, '20:00:00', '21:00:00', 'AVAILABLE', NULL, 24),
  ('mentor.hejin@bishe.local', 2, '20:00:00', '21:00:00', 'BOOKED', 'CONS-MQA-20260401-004', 26),
  ('mentor.hejin@bishe.local', 6, '19:30:00', '20:30:00', 'AVAILABLE', NULL, 26),
  ('mentor.qiuyu@bishe.local', 1, '19:30:00', '20:30:00', 'BOOKED', 'CONS-MQA-20260401-006', 30),
  ('mentor.qiuyu@bishe.local', 4, '19:30:00', '20:30:00', 'AVAILABLE', NULL, 30),
  ('mentor.shuqin@bishe.local', 3, '20:00:00', '21:00:00', 'AVAILABLE', NULL, 8),
  ('mentor.tangxi@bishe.local', 2, '21:00:00', '22:00:00', 'AVAILABLE', NULL, 10),
  ('mentor.jiayi@bishe.local', 1, '20:00:00', '21:00:00', 'AVAILABLE', NULL, 6),
  ('mentor.ruoxi@bishe.local', 5, '19:00:00', '20:00:00', 'AVAILABLE', NULL, 20),
  ('mentor.wenrao@bishe.local', 6, '20:00:00', '21:00:00', 'AVAILABLE', NULL, 22),
  ('mentor.ningzhou@bishe.local', 7, '19:30:00', '20:30:00', 'AVAILABLE', NULL, 24),
  ('mentor.muyan@bishe.local', 8, '20:00:00', '21:00:00', 'AVAILABLE', NULL, 28),
  ('mentor.yufei@bishe.local', 3, '18:30:00', '19:30:00', 'AVAILABLE', NULL, 36),
  ('mentor.anxu@bishe.local', 9, '20:00:00', '21:00:00', 'AVAILABLE', NULL, 72),
  ('mentor.kexin@bishe.local', 10, '19:00:00', '20:00:00', 'AVAILABLE', NULL, 72),
  ('mentor.zishu@bishe.local', 11, '20:00:00', '21:00:00', 'AVAILABLE', NULL, 60);

INSERT IGNORE INTO mentor_schedule_slots(
  mentor_user_id, start_at, end_at, status, booked_order_no, created_at, updated_at
)
SELECT
  mu.id,
  TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL ss.day_offset DAY), ss.start_time),
  TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL ss.day_offset DAY), ss.end_time),
  ss.status,
  ss.booked_order_no,
  DATE_SUB(@seed_now, INTERVAL ss.created_hours_ago HOUR),
  DATE_SUB(@seed_now, INTERVAL ss.created_hours_ago HOUR)
FROM seed_mentor_slots ss
JOIN users mu ON mu.email = ss.mentor_email;

CREATE TEMPORARY TABLE seed_mentor_messages (
  order_no VARCHAR(64) NOT NULL,
  sender_email VARCHAR(255) NOT NULL,
  sender_role VARCHAR(20) NOT NULL,
  message_text TEXT NOT NULL,
  created_hours_ago INT NOT NULL
);

INSERT INTO seed_mentor_messages(order_no, sender_email, sender_role, message_text, created_hours_ago)
VALUES
  ('CONS-MQA-20260401-001', 'student.liujianing@bishe.local', 'STUDENT', '我现在最大的困惑是项目里看起来都是增删改查，面试时很难讲出复杂度。', 319),
  ('CONS-MQA-20260401-001', 'mentor.chengyanbei@bishe.local', 'MENTOR', '先别急着追求术语，先把业务目标、约束条件和你负责的关键动作拆出来。', 317),
  ('CONS-MQA-20260401-001', 'student.liujianing@bishe.local', 'STUDENT', '明白了，我会补库存一致性和接口耗时这两块指标。', 316),
  ('CONS-MQA-20260401-002', 'student.xuanran@bishe.local', 'STUDENT', '我一被追问缓存一致性就会乱，容易直接开始背方案。', 77),
  ('CONS-MQA-20260401-002', 'mentor.chengyanbei@bishe.local', 'MENTOR', '下次先说业务容忍度，再说为什么接受短暂不一致，最后再落到具体策略。', 75),
  ('CONS-MQA-20260401-003', 'student.zhoumuyang@bishe.local', 'STUDENT', '我的前端项目写出来很像做页面，没有把复杂交互讲出来。', 209),
  ('CONS-MQA-20260401-003', 'mentor.linqiao@bishe.local', 'MENTOR', '可以把状态管理、异常处理和性能优化拆成三个层次，不要只列功能点。', 207),
  ('CONS-MQA-20260401-004', 'student.heqingyan@bishe.local', 'STUDENT', '我想重点改数据项目里的业务问题定义和结论表达。', 17),
  ('CONS-MQA-20260401-005', 'student.linjiaqi@bishe.local', 'STUDENT', '两个 Offer 我最纠结的是带教和后续成长速度。', 191),
  ('CONS-MQA-20260401-005', 'mentor.songyue@bishe.local', 'MENTOR', '建议先拆业务复杂度、带教质量、你能否接触核心链路，再讨论品牌。', 189),
  ('CONS-MQA-20260401-009', 'student.xuanran@bishe.local', 'STUDENT', '我答系统设计题时常常一上来就讲技术，不会先讲约束。', 173),
  ('CONS-MQA-20260401-009', 'mentor.jiayi@bishe.local', 'MENTOR', '可以固定成“目标-约束-核心链路-关键权衡-风险和兜底”这五步。', 171),
  ('CONS-MQA-20260401-010', 'student.linjiaqi@bishe.local', 'STUDENT', '前端性能优化我总不知道该怎么证明效果。', 51),
  ('CONS-MQA-20260401-010', 'mentor.linqiao@bishe.local', 'MENTOR', '尽量用耗时、包体积、交互等待时间或埋点指标去证明，不要只写“做了优化”。', 49),
  ('CONS-MQA-20260401-012', 'student.heqingyan@bishe.local', 'STUDENT', '我现在能说出用了 Redis，但说不清为什么当时那样设计。', 143),
  ('CONS-MQA-20260401-012', 'mentor.qiuyu@bishe.local', 'MENTOR', '先从数据访问压力、响应要求和容错边界讲，再解释缓存和数据库各自承担什么。', 141);

INSERT INTO consult_messages(order_no, sender_user_id, sender_role, message_text, created_at)
SELECT
  sm.order_no,
  su.id,
  sm.sender_role,
  sm.message_text,
  DATE_SUB(@seed_now, INTERVAL sm.created_hours_ago HOUR)
FROM seed_mentor_messages sm
JOIN users su ON su.email = sm.sender_email
WHERE NOT EXISTS (
  SELECT 1
  FROM consult_messages cm
  WHERE cm.order_no = sm.order_no
    AND cm.sender_user_id = su.id
    AND cm.message_text = sm.message_text
);

CREATE TEMPORARY TABLE seed_mentor_reviews (
  order_no VARCHAR(64) NOT NULL,
  student_email VARCHAR(255) NOT NULL,
  mentor_email VARCHAR(255) NOT NULL,
  rating TINYINT NOT NULL,
  comment TEXT NOT NULL,
  created_hours_ago INT NOT NULL
);

INSERT INTO seed_mentor_reviews(order_no, student_email, mentor_email, rating, comment, created_hours_ago)
VALUES
  ('CONS-MQA-20260401-001', 'student.liujianing@bishe.local', 'mentor.chengyanbei@bishe.local', 5, '导师会先帮我拆业务目标和约束，再补技术动作和结果，项目表达清楚了很多。', 271),
  ('CONS-MQA-20260401-003', 'student.zhoumuyang@bishe.local', 'mentor.linqiao@bishe.local', 5, '前端项目终于不再只是功能堆砌，复杂交互和性能优化的讲法都更清晰了。', 153),
  ('CONS-MQA-20260401-005', 'student.linjiaqi@bishe.local', 'mentor.songyue@bishe.local', 4, 'Offer 对比的框架很实用，帮助我把关注点从品牌转到了成长路径。', 167),
  ('CONS-MQA-20260401-009', 'student.xuanran@bishe.local', 'mentor.jiayi@bishe.local', 5, '系统设计回答的结构明显稳定了，不再一上来就只讲技术名词。', 125),
  ('CONS-MQA-20260401-012', 'student.heqingyan@bishe.local', 'mentor.qiuyu@bishe.local', 5, '缓存和数据库设计终于能按场景、约束和取舍来讲，不会只背方案。', 89);

INSERT INTO consult_reviews(order_no, student_user_id, mentor_user_id, rating, comment, created_at)
SELECT
  sr.order_no,
  su.id,
  mu.id,
  sr.rating,
  sr.comment,
  DATE_SUB(@seed_now, INTERVAL sr.created_hours_ago HOUR)
FROM seed_mentor_reviews sr
JOIN users su ON su.email = sr.student_email
JOIN users mu ON mu.email = sr.mentor_email
ON DUPLICATE KEY UPDATE
  student_user_id = VALUES(student_user_id),
  mentor_user_id = VALUES(mentor_user_id),
  rating = VALUES(rating),
  comment = VALUES(comment),
  created_at = VALUES(created_at);

CREATE TEMPORARY TABLE seed_mentor_withdrawals (
  mentor_email VARCHAR(255) NOT NULL,
  amount_fen INT NOT NULL,
  status VARCHAR(20) NOT NULL,
  note VARCHAR(500) NULL,
  created_hours_ago INT NOT NULL,
  updated_hours_ago INT NOT NULL
);

INSERT INTO seed_mentor_withdrawals(mentor_email, amount_fen, status, note, created_hours_ago, updated_hours_ago)
VALUES
  ('mentor.chengyanbei@bishe.local', 31800, 'COMPLETED', '三月已完成两笔订单结算，本次作为模拟提现留痕。', 220, 200),
  ('mentor.linqiao@bishe.local', 14900, 'PROCESSING', '等待平台确认本周前端项目辅导订单的提现处理。', 72, 24),
  ('mentor.jiayi@bishe.local', 39800, 'PENDING', '计划在本周集中发起一笔模拟提现申请。', 20, 20),
  ('mentor.kexin@bishe.local', 11900, 'REJECTED', '当前处于暂停接单阶段，建议恢复接单后再发起提现。', 168, 120);

INSERT INTO mentor_withdrawal_requests(mentor_user_id, amount_fen, status, note, created_at, updated_at)
SELECT
  mu.id,
  sw.amount_fen,
  sw.status,
  sw.note,
  DATE_SUB(@seed_now, INTERVAL sw.created_hours_ago HOUR),
  DATE_SUB(@seed_now, INTERVAL sw.updated_hours_ago HOUR)
FROM seed_mentor_withdrawals sw
JOIN users mu ON mu.email = sw.mentor_email
WHERE NOT EXISTS (
  SELECT 1
  FROM mentor_withdrawal_requests mwr
  WHERE mwr.mentor_user_id = mu.id
    AND mwr.amount_fen = sw.amount_fen
    AND mwr.status = sw.status
    AND COALESCE(mwr.note, '') = COALESCE(sw.note, '')
);

SELECT
  COUNT(*) AS seeded_mentor_accounts,
  SUM(CASE WHEN approval_status = 'APPROVED' THEN 1 ELSE 0 END) AS approved_mentor_accounts,
  SUM(CASE WHEN approval_status = 'APPROVED' AND is_available = 1 THEN 1 ELSE 0 END) AS approved_available_mentor_accounts
FROM seed_mentor_catalog;

SELECT COUNT(*) AS seeded_orders
FROM consult_orders
WHERE order_no LIKE 'CONS-MQA-20260401-%';

SELECT COUNT(*) AS seeded_reviews
FROM consult_reviews
WHERE order_no LIKE 'CONS-MQA-20260401-%';

SELECT COUNT(*) AS seeded_favorites
FROM mentor_favorites mf
JOIN users mu ON mu.id = mf.mentor_user_id
WHERE mu.email IN (SELECT email FROM seed_mentor_catalog);

SELECT COUNT(*) AS seeded_schedule_slots
FROM mentor_schedule_slots ms
JOIN users mu ON mu.id = ms.mentor_user_id
WHERE mu.email IN (SELECT email FROM seed_mentor_catalog);

SELECT COUNT(*) AS seeded_withdrawal_requests
FROM mentor_withdrawal_requests mwr
JOIN users mu ON mu.id = mwr.mentor_user_id
WHERE mu.email IN (SELECT email FROM seed_mentor_catalog);
