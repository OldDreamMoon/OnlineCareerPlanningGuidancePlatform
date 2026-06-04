CREATE TEMPORARY TABLE tmp_old_skill_nodes (
    node_code VARCHAR(100) PRIMARY KEY
);

INSERT INTO tmp_old_skill_nodes (node_code)
WITH RECURSIVE old_skill_nodes AS (
    SELECT node_code
    FROM skills
    WHERE node_code IN (
        'java_basics',
        'sql_basics',
        'spring_boot',
        'api_design',
        'deployment_basics'
    )

    UNION

    SELECT child.node_code
    FROM skills child
    INNER JOIN old_skill_nodes parent ON child.parent_code = parent.node_code
)
SELECT node_code
FROM old_skill_nodes;

DELETE FROM skill_progress
WHERE node_code IN (SELECT node_code FROM tmp_old_skill_nodes);

SET @previous_foreign_key_checks = @@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM skills
WHERE node_code IN (SELECT node_code FROM tmp_old_skill_nodes);

SET FOREIGN_KEY_CHECKS = @previous_foreign_key_checks;

DROP TEMPORARY TABLE tmp_old_skill_nodes;

INSERT INTO skills(node_code, label, description, parent_code, sort_order, created_at, updated_at) VALUES
('programming_language_foundations', '程序设计与语言基础', '建立计算机科学与技术专业最底层的语言认知、编码规范与抽象能力。', NULL, 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('software_engineering_delivery', '软件工程与交付', '围绕真实项目协作、需求落地、测试与交付形成完整的软件工程视角。', NULL, 200, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('data_database_systems', '数据与数据库系统', '从数据建模、关系数据库到大数据基础，理解信息如何被稳定存储与分析。', NULL, 300, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('systems_infrastructure', '系统与基础设施', '补齐组成原理、操作系统、分布式与云原生的系统底座认知。', NULL, 400, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('network_security', '网络与安全', '理解网络通信、协议分层与安全防护的核心机制，建立面向真实互联网系统的安全意识。', NULL, 500, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai_data_science', '智能与数据科学', '从统计基础、机器学习到大模型应用，形成面向智能系统的学习路线。', NULL, 600, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('hardware_embedded', '硬件与嵌入式', '从数字逻辑到嵌入式系统，补足软硬件协同的工程理解。', NULL, 700, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('theory_history', '计算理论与技术史', '补齐离散数学、编译原理、技术演进与工程伦理等长期能力。', NULL, 800, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('c_programming_basics', 'C 语言基础', '理解指针、内存、过程式编程与底层调试方式，为系统方向学习做准备。', 'programming_language_foundations', 110, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('java_programming', 'Java 程序设计', '掌握面向对象语法、集合、异常、常见标准库与工程化编码习惯。', 'programming_language_foundations', 120, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('object_oriented_modeling', '面向对象建模', '学会通过类、接口、职责拆分与抽象建模表达复杂业务。', 'java_programming', 130, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('data_structures', '数据结构', '掌握线性表、树、图、哈希等基础结构，并理解不同结构的适用场景。', 'programming_language_foundations', 140, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('algorithms_analysis', '算法分析', '建立时间复杂度、空间复杂度、贪心、分治与动态规划等核心算法意识。', 'data_structures', 150, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('frontend_engineering', '前端工程基础', '理解浏览器、组件化、状态管理与现代前端工程组织方式。', 'software_engineering_delivery', 210, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('backend_service_development', '后端服务开发', '掌握服务分层、业务建模、接口实现与基础中间件接入。', 'software_engineering_delivery', 220, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('api_contract_design', '接口契约设计', '能够从资源、鉴权、错误码、分页与演进策略角度设计稳定 API。', 'backend_service_development', 230, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('automated_testing', '自动化测试', '理解单元测试、集成测试、回归验证与质量门禁的基本方法。', 'backend_service_development', 240, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('collaborative_development', '协作开发流程', '掌握 Git 工作流、代码评审、任务拆分与多人协作的基本规范。', 'software_engineering_delivery', 250, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('devops_delivery', 'DevOps 与交付', '理解构建、部署、环境隔离、CI/CD 与线上发布的完整闭环。', 'api_contract_design', 260, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('relational_databases', '关系数据库', '掌握表结构、范式、索引、主外键与 SQL 语句的核心使用方式。', 'data_database_systems', 310, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('data_modeling', '数据建模', '能够围绕业务域抽象实体关系，并兼顾查询性能与可演进性。', 'relational_databases', 320, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('query_optimization', '查询优化', '理解执行计划、索引命中、慢查询定位与常见 SQL 调优思路。', 'relational_databases', 330, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('transaction_consistency', '事务与一致性', '掌握隔离级别、锁、并发写入与一致性约束的基本原理。', 'data_modeling', 340, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('big_data_foundations', '大数据基础', '了解批流处理、数据湖仓、消息系统与大规模数据处理的基本概念。', 'query_optimization', 350, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('computer_organization', '计算机组成原理', '理解指令执行、CPU、存储层次与整机结构，建立系统运行的底层视角。', 'systems_infrastructure', 410, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('operating_systems', '操作系统', '掌握进程线程、内存管理、文件系统与调度等核心操作系统概念。', 'systems_infrastructure', 420, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('linux_operations', 'Linux 运维基础', '具备常见命令行操作、权限管理、日志排障与服务运维基本能力。', 'operating_systems', 430, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('distributed_systems', '分布式系统基础', '理解 CAP、复制、负载均衡、服务发现与分布式调用的核心问题。', 'operating_systems', 440, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('cloud_native_basics', '云原生基础', '掌握容器、镜像、编排、配置管理与现代基础设施的基本概念。', 'distributed_systems', 450, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('computer_networks', '计算机网络', '掌握分层模型、路由交换、常见网络设备与网络排障的基本思路。', 'network_security', 510, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('network_protocols', '网络协议', '理解 TCP/IP、HTTP、DNS、TLS 等协议在真实系统中的协作方式。', 'computer_networks', 520, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('web_security', 'Web 安全基础', '理解认证授权、XSS、CSRF、注入等常见 Web 安全问题。', 'network_security', 530, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('applied_cryptography', '应用密码学', '理解哈希、对称加密、非对称加密、签名与证书的工程使用场景。', 'web_security', 540, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('secure_engineering', '安全工程实践', '学会把安全意识融入代码、配置、接口与上线检查清单之中。', 'applied_cryptography', 550, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('python_data_tools', 'Python 数据工具', '掌握 Python 在数据处理、脚本自动化与实验原型中的基础能力。', 'ai_data_science', 610, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('statistics_foundation', '统计学基础', '理解概率、分布、假设检验与回归等机器学习前置知识。', 'ai_data_science', 620, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('machine_learning', '机器学习', '掌握监督学习、特征工程、模型评估与常见算法应用方法。', 'statistics_foundation', 630, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('deep_learning', '深度学习', '理解神经网络、反向传播、训练流程与常见深度学习结构。', 'machine_learning', 640, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('llm_applications', '大模型应用', '了解提示词工程、RAG、Agent、评测与大模型产品化接入方式。', 'deep_learning', 650, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('recommendation_systems', '推荐系统基础', '理解召回、排序、特征构建与个性化推荐的基本流程。', 'machine_learning', 660, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('digital_logic', '数字逻辑', '掌握布尔代数、组合逻辑、时序逻辑与简单硬件电路设计概念。', 'hardware_embedded', 710, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('microcomputer_principles', '微机原理', '理解单片机、总线、中断与外设通信的基本工作方式。', 'digital_logic', 720, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('embedded_systems', '嵌入式系统', '掌握面向资源受限环境的软件设计、驱动基础与硬件协同调试思路。', 'microcomputer_principles', 730, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('iot_system_design', '物联网系统设计', '理解传感器、边缘设备、数据采集与云端联动的整体架构。', 'embedded_systems', 740, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('discrete_mathematics', '离散数学', '掌握集合、逻辑、关系、图论与证明方法，为理论课程打基础。', 'theory_history', 810, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('compiler_principles', '编译原理', '理解词法分析、语法分析、中间表示与编译器工作流程。', 'discrete_mathematics', 820, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('cs_history', '计算机发展史', '了解从图灵机、冯诺依曼结构到互联网与开源时代的关键节点。', 'theory_history', 830, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('open_source_culture', '开源协作文化', '理解自由软件、开源社区协作、许可证与公共技术生态的价值。', 'cs_history', 840, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('engineering_ethics', '工程伦理', '理解隐私、算法偏见、平台责任与技术决策的社会影响。', 'open_source_culture', 850, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('career_employment_readiness', '就业与职业准备', '围绕岗位理解、简历表达与求职策略，建立从校园到就业市场的过渡能力。', NULL, 900, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('career_positioning', '职业定位', '理解自己的能力边界、兴趣方向与可切入岗位，明确阶段性求职目标。', 'career_employment_readiness', 910, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('job_market_research', '岗位信息研判', '学会阅读 JD、拆解岗位要求、识别行业差异与招聘节奏。', 'career_employment_readiness', 920, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('resume_portfolio', '简历与作品集表达', '把项目、经历和能力整理成能被 HR 与面试官快速理解的材料。', 'career_positioning', 930, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('interview_preparation', '面试准备与复盘', '建立自我介绍、项目讲述、行为面试和技术面复盘的基本方法。', 'resume_portfolio', 940, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('networking_personal_brand', '人脉拓展与个人品牌', '理解校友、导师、社区和公开表达在求职过程中的长期价值。', 'job_market_research', 950, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('internship_workplace_adaptation', '实习与职场适应', '帮助学生在进入团队后快速适应任务节奏、协作方式与职场基本规范。', NULL, 1000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('internship_goal_setting', '实习目标设定', '进入团队前先明确实习周期目标、学习重点和阶段性成果预期。', 'internship_workplace_adaptation', 1010, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('task_execution_followup', '任务执行与跟进', '学会拆解任务、同步进展、暴露风险并持续推进交付。', 'internship_goal_setting', 1020, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('workplace_communication', '职场沟通', '理解向导师、同事、产品或 HR 沟通时的语气、节奏与信息完整度。', 'internship_workplace_adaptation', 1030, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('teamwork_collaboration', '团队协作', '学会在多人协作中对齐上下游、处理分工边界和形成有效反馈。', 'workplace_communication', 1040, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('workplace_professionalism', '职场专业度', '在时间观念、反馈习惯、文档沉淀和责任感上形成稳定职业素养。', 'task_execution_followup', 1050, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('wellbeing_self_management', '心理调节与成长韧性', '帮助学生在求职、学习和实习压力下保持稳定节奏与持续成长心态。', NULL, 1100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('stress_management', '压力管理', '识别压力来源，建立可执行的缓冲、拆解与恢复方法。', 'wellbeing_self_management', 1110, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('emotion_regulation', '情绪调节', '学会识别情绪波动、避免陷入自我否定，并进行更平稳的表达与恢复。', 'stress_management', 1120, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('growth_mindset', '成长型心态', '把失败、反馈与短期挫折视为成长过程的一部分，而不是能力定型。', 'wellbeing_self_management', 1130, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('habit_energy_management', '习惯与精力管理', '通过作息、复盘、专注习惯和阶段节奏管理维持长期输出。', 'growth_mindset', 1140, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('burnout_prevention', '倦怠预防', '识别长期高压与低反馈带来的倦怠信号，及时调整目标与恢复方式。', 'emotion_regulation', 1150, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
