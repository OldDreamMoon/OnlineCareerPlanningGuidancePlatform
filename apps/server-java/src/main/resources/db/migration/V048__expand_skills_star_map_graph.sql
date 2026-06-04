CREATE TABLE IF NOT EXISTS skill_relations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    source_node_code VARCHAR(100) NOT NULL,
    target_node_code VARCHAR(100) NOT NULL,
    relation_type VARCHAR(30) NOT NULL,
    label VARCHAR(100) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_skill_relations_unique UNIQUE (source_node_code, target_node_code, relation_type),
    CONSTRAINT fk_skill_relations_source FOREIGN KEY (source_node_code) REFERENCES skills(node_code),
    CONSTRAINT fk_skill_relations_target FOREIGN KEY (target_node_code) REFERENCES skills(node_code)
);

INSERT INTO skills(node_code, label, description, parent_code, sort_order, created_at, updated_at) VALUES
('computational_thinking', '计算思维与问题拆解', '把底层语言、数据结构与算法训练串成一条抽象问题与拆解问题的主线。', 'programming_language_foundations', 200, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('memory_model_pointers', '内存模型与指针意识', '理解地址、内存布局、生命周期和指针操作，为系统编程打地基。', 'c_programming_basics', 211, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('graph_problem_modeling', '图与关系建模', '把复杂业务、网络和依赖关系抽象成图结构与状态迁移问题。', 'algorithms_analysis', 222, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('problem_solving_patterns', '问题求解套路', '沉淀搜索、回溯、贪心、分治与动态规划的识别信号。', 'algorithms_analysis', 223, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('design_patterns_refactoring', '设计模式与重构', '通过模式识别和重构手法提升面向对象代码的扩展性与可维护性。', 'object_oriented_modeling', 311, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('engineering_code_conventions', '工程编码规范', '围绕命名、分层、异常、日志与可读性形成稳定编码习惯。', 'object_oriented_modeling', 312, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('java_collections_io', '集合、I/O 与并发入口', '从集合框架、文件 I/O 到线程基础，建立 Java 工程运行时认知。', 'java_programming', 320, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('concurrent_programming_basics', '并发编程基础', '理解线程安全、锁、线程池与并发容器的基本用法与风险。', 'java_collections_io', 321, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('requirement_analysis', '需求分析与任务拆解', '在动手实现前先把问题背景、目标、边界与验收标准讲清楚。', 'software_engineering_delivery', 410, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('software_architecture_design', '系统设计与架构分层', '从模块职责、边界划分到调用链路，搭起应用系统的主骨架。', 'requirement_analysis', 411, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('service_layer_design', '服务层设计', '明确应用服务、领域对象、事务边界与对外暴露接口的组织方式。', 'backend_service_development', 413, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('domain_modeling_design', '业务领域建模', '把真实业务概念映射成可演进的数据对象、状态机和规则边界。', 'service_layer_design', 415, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('browser_runtime_mechanics', '浏览器运行机制', '理解渲染流水线、事件循环、缓存与网络请求在浏览器中的协作。', 'frontend_engineering', 421, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('frontend_component_architecture', '组件架构设计', '通过组件边界、复用策略和职责拆分构建可维护的前端界面。', 'frontend_engineering', 422, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('state_data_flow', '状态与数据流', '梳理页面状态、异步数据和组件通信的来源、去向与同步策略。', 'frontend_component_architecture', 423, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('unit_integration_testing', '单元与集成测试', '让测试覆盖函数、模块和依赖协作，而不是只停留在 Happy Path。', 'automated_testing', 432, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('end_to_end_regression', '端到端回归验证', '围绕关键业务路径设计回归用例，守住版本发布前的稳定性。', 'automated_testing', 433, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ci_cd_pipeline', 'CI/CD 流水线', '把构建、检测、部署与回滚路径收进自动化流水线。', 'devops_delivery', 441, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('observability_incident_response', '可观测性与故障响应', '通过日志、指标、链路与值班流程定位线上异常并快速恢复。', 'devops_delivery', 442, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('sql_query_writing', 'SQL 编写与结果验证', '把查询语句写对、写清楚，并能验证结果、边界和代价。', 'relational_databases', 511, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('data_warehouse_etl', '数据仓库与 ETL', '理解离线数仓、指标口径、批处理链路和基础 ETL 流程。', 'data_database_systems', 520, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('analytics_data_visualization', '分析表达与数据可视化', '把分析结果整理成图表、指标故事和可沟通的结论。', 'data_warehouse_etl', 522, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('process_thread_models', '进程与线程模型', '拆清进程、线程、上下文切换和调度之间的关系。', 'operating_systems', 613, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('memory_management', '内存管理', '理解虚拟内存、分页、分段、缓存与内存泄漏定位思路。', 'operating_systems', 614, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('file_system_io', '文件系统与 I/O', '理解文件抽象、磁盘读写、缓存与常见 I/O 模型。', 'operating_systems', 615, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('shell_tooling_automation', 'Shell 与自动化工具', '把命令行、脚本和批处理能力沉淀成日常排障与自动化效率。', 'linux_operations', 617, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('service_governance_resilience', '服务治理与韧性设计', '围绕超时、限流、熔断、重试与降级处理复杂调用链。', 'distributed_systems', 641, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('container_kubernetes', '容器编排与 Kubernetes', '从镜像、Pod、Service 到部署策略理解云原生运行时。', 'cloud_native_basics', 643, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('data_cleaning_feature_engineering', '数据清洗与特征工程', '把原始数据整理成可训练、可评估、可复用的特征输入。', 'python_data_tools', 711, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('probability_inference', '概率与统计推断', '从分布、期望到估计与假设检验，补齐机器学习前置数学。', 'statistics_foundation', 721, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('model_evaluation_validation', '模型评估与验证', '围绕指标选择、数据切分、过拟合与泛化能力建立评估框架。', 'machine_learning', 723, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('tree_linear_models', '线性模型与树模型', '掌握最常见的传统机器学习模型及其适用边界。', 'machine_learning', 724, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('neural_network_training', '神经网络训练流程', '围绕损失函数、优化器、正则化和调参形成训练闭环。', 'deep_learning', 726, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('prompt_rag_agent', 'Prompt、RAG 与 Agent 设计', '把提示词工程、检索增强和工具调用组织成可落地的大模型方案。', 'llm_applications', 728, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('logic_and_proofs', '逻辑与证明方法', '用命题逻辑、归纳法和反证法训练严谨推导能力。', 'discrete_mathematics', 811, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('technology_society_governance', '技术治理与社会影响', '从平台责任、算法治理到公共影响理解技术决策的外部后果。', 'engineering_ethics', 823, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('project_storytelling', '项目叙事与成果表达', '把项目背景、决策过程、指标结果和个人贡献讲成完整故事。', 'resume_portfolio', 921, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('self_reflection_planning', '自我复盘与阶段规划', '把学习、求职、实习过程中的反馈沉淀成下一阶段的行动计划。', 'growth_mindset', 946, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

UPDATE skills
SET label = '计算机科学成长底座',
    description = '作为整张技能星图的中央枢纽，把技术基础、工程实践、职业发展和成长韧性汇成一棵主树。',
    parent_code = NULL,
    sort_order = 100
WHERE node_code = 'programming_language_foundations';

UPDATE skills
SET label = 'C 语言与底层感知',
    description = '从指针、内存和过程式编程建立对机器执行过程的直觉。',
    parent_code = 'computational_thinking',
    sort_order = 210
WHERE node_code = 'c_programming_basics';

UPDATE skills
SET label = 'Java 与工程语言实践',
    description = '掌握面向对象语法、标准库和工程开发里最常见的 Java 基础能力。',
    parent_code = 'programming_language_foundations',
    sort_order = 300
WHERE node_code = 'java_programming';

UPDATE skills
SET label = '对象建模与职责拆分',
    description = '把类、接口、对象协作与职责边界设计得更稳定、更清晰。',
    parent_code = 'java_programming',
    sort_order = 310
WHERE node_code = 'object_oriented_modeling';

UPDATE skills
SET label = '数据结构与组织方式',
    description = '围绕线性表、树、图、哈希和状态表示组织问题信息。',
    parent_code = 'computational_thinking',
    sort_order = 220
WHERE node_code = 'data_structures';

UPDATE skills
SET label = '算法分析与复杂度意识',
    description = '通过时间复杂度、空间复杂度和常见策略训练解题与实现判断力。',
    parent_code = 'data_structures',
    sort_order = 221
WHERE node_code = 'algorithms_analysis';

UPDATE skills
SET label = '软件工程与应用交付',
    description = '把需求理解、系统设计、协作开发、测试和交付串成完整工程闭环。',
    parent_code = 'programming_language_foundations',
    sort_order = 400
WHERE node_code = 'software_engineering_delivery';

UPDATE skills
SET label = '前端工程',
    description = '关注浏览器、组件化、状态管理与面向产品界面的前端实现方式。',
    parent_code = 'software_architecture_design',
    sort_order = 420
WHERE node_code = 'frontend_engineering';

UPDATE skills
SET label = '后端服务开发',
    description = '把接口、服务层、持久层与业务规则组织成稳定可维护的后端系统。',
    parent_code = 'software_architecture_design',
    sort_order = 412
WHERE node_code = 'backend_service_development';

UPDATE skills
SET label = '接口契约设计',
    description = '围绕资源、协议、错误码、版本演进和协作边界设计稳定 API。',
    parent_code = 'service_layer_design',
    sort_order = 414
WHERE node_code = 'api_contract_design';

UPDATE skills
SET label = '自动化测试',
    description = '用测试守住关键业务路径、模块协作和版本回归稳定性。',
    parent_code = 'collaborative_development',
    sort_order = 431
WHERE node_code = 'automated_testing';

UPDATE skills
SET label = '协作开发流程',
    description = '通过任务拆分、代码评审、分支策略和文档同步提升多人协作效率。',
    parent_code = 'software_engineering_delivery',
    sort_order = 430
WHERE node_code = 'collaborative_development';

UPDATE skills
SET label = 'DevOps 与发布交付',
    description = '围绕构建、部署、环境管理与发布回滚形成交付工程能力。',
    parent_code = 'collaborative_development',
    sort_order = 440
WHERE node_code = 'devops_delivery';

UPDATE skills
SET label = '数据、数据库与信息系统',
    description = '从数据建模、数据库到数仓分析与数据系统理解信息如何被沉淀和使用。',
    parent_code = 'programming_language_foundations',
    sort_order = 500
WHERE node_code = 'data_database_systems';

UPDATE skills
SET label = '关系数据库',
    description = '掌握表结构、约束、SQL、索引与关系型数据组织方式。',
    parent_code = 'data_database_systems',
    sort_order = 510
WHERE node_code = 'relational_databases';

UPDATE skills
SET label = '数据建模',
    description = '把业务对象、关系和查询需求沉淀成兼顾一致性与演进性的结构。',
    parent_code = 'relational_databases',
    sort_order = 512
WHERE node_code = 'data_modeling';

UPDATE skills
SET label = '查询优化',
    description = '理解执行计划、索引命中、慢查询定位和常见 SQL 调优策略。',
    parent_code = 'data_modeling',
    sort_order = 514
WHERE node_code = 'query_optimization';

UPDATE skills
SET label = '事务与一致性',
    description = '掌握隔离级别、锁、并发写入和一致性约束的核心问题。',
    parent_code = 'data_modeling',
    sort_order = 513
WHERE node_code = 'transaction_consistency';

UPDATE skills
SET label = '大数据基础',
    description = '理解批处理、流处理、消息系统和规模化数据处理的基本框架。',
    parent_code = 'data_warehouse_etl',
    sort_order = 521
WHERE node_code = 'big_data_foundations';

UPDATE skills
SET label = '系统、网络与基础设施',
    description = '从组成原理、操作系统、网络、安全到分布式基础设施构建系统视角。',
    parent_code = 'programming_language_foundations',
    sort_order = 600
WHERE node_code = 'systems_infrastructure';

UPDATE skills
SET label = '计算机组成原理',
    description = '理解 CPU、存储层次、指令执行与整机结构，补齐底层运行直觉。',
    parent_code = 'systems_infrastructure',
    sort_order = 610
WHERE node_code = 'computer_organization';

UPDATE skills
SET label = '操作系统',
    description = '从进程线程、内存管理到文件与调度理解系统资源如何被组织。',
    parent_code = 'computer_organization',
    sort_order = 612
WHERE node_code = 'operating_systems';

UPDATE skills
SET label = 'Linux 运维基础',
    description = '围绕命令行、权限、日志、进程和服务管理建立系统排障能力。',
    parent_code = 'operating_systems',
    sort_order = 616
WHERE node_code = 'linux_operations';

UPDATE skills
SET label = '网络安全与服务韧性',
    description = '把网络通信、安全防护、服务治理与稳定性设计串成一条系统链路。',
    parent_code = 'systems_infrastructure',
    sort_order = 630
WHERE node_code = 'network_security';

UPDATE skills
SET label = '分布式系统基础',
    description = '理解复制、负载均衡、服务发现和跨节点调用中的关键难题。',
    parent_code = 'network_security',
    sort_order = 640
WHERE node_code = 'distributed_systems';

UPDATE skills
SET label = '云原生基础',
    description = '围绕容器、镜像、编排和配置管理理解现代基础设施的运行方式。',
    parent_code = 'distributed_systems',
    sort_order = 642
WHERE node_code = 'cloud_native_basics';

UPDATE skills
SET label = '计算机网络',
    description = '掌握网络分层、设备角色、请求路径和典型排障思路。',
    parent_code = 'network_security',
    sort_order = 631
WHERE node_code = 'computer_networks';

UPDATE skills
SET label = '网络协议',
    description = '理解 TCP/IP、HTTP、DNS、TLS 等协议在真实业务中的协作方式。',
    parent_code = 'computer_networks',
    sort_order = 632
WHERE node_code = 'network_protocols';

UPDATE skills
SET label = 'Web 安全基础',
    description = '识别认证授权、XSS、CSRF、注入和常见安全设计缺口。',
    parent_code = 'network_protocols',
    sort_order = 633
WHERE node_code = 'web_security';

UPDATE skills
SET label = '应用密码学',
    description = '理解哈希、加密、签名、证书与密钥管理在工程中的用法。',
    parent_code = 'web_security',
    sort_order = 634
WHERE node_code = 'applied_cryptography';

UPDATE skills
SET label = '安全工程实践',
    description = '把安全要求落实到接口、配置、代码审计和上线检查清单。',
    parent_code = 'applied_cryptography',
    sort_order = 635
WHERE node_code = 'secure_engineering';

UPDATE skills
SET label = '硬件与嵌入式',
    description = '理解数字逻辑、单片机、嵌入式软件与设备联动的工程链路。',
    parent_code = 'computer_organization',
    sort_order = 620
WHERE node_code = 'hardware_embedded';

UPDATE skills
SET label = '数字逻辑',
    description = '围绕布尔代数、组合逻辑和时序逻辑建立硬件表达基础。',
    parent_code = 'hardware_embedded',
    sort_order = 621
WHERE node_code = 'digital_logic';

UPDATE skills
SET label = '微机原理',
    description = '理解总线、中断、寄存器和外设通信在嵌入式场景里的工作方式。',
    parent_code = 'hardware_embedded',
    sort_order = 622
WHERE node_code = 'microcomputer_principles';

UPDATE skills
SET label = '嵌入式系统',
    description = '面向资源受限设备学习驱动、调试与软硬件协同实现。', 
    parent_code = 'microcomputer_principles',
    sort_order = 623
WHERE node_code = 'embedded_systems';

UPDATE skills
SET label = '物联网系统设计',
    description = '连接传感器、边缘设备、通信链路和云端平台，形成完整 IoT 方案。',
    parent_code = 'embedded_systems',
    sort_order = 624
WHERE node_code = 'iot_system_design';

UPDATE skills
SET label = '智能与数据科学',
    description = '从数据处理、统计推断到机器学习与大模型应用构建智能系统视角。',
    parent_code = 'programming_language_foundations',
    sort_order = 700
WHERE node_code = 'ai_data_science';

UPDATE skills
SET label = 'Python 数据工具',
    description = '用 Python 处理数据、写脚本和搭实验原型，形成数据工作台能力。',
    parent_code = 'ai_data_science',
    sort_order = 710
WHERE node_code = 'python_data_tools';

UPDATE skills
SET label = '统计学基础',
    description = '从概率、分布到估计与推断，为机器学习与实验分析做准备。',
    parent_code = 'ai_data_science',
    sort_order = 720
WHERE node_code = 'statistics_foundation';

UPDATE skills
SET label = '机器学习',
    description = '围绕特征、训练、评估和泛化能力理解典型机器学习流程。',
    parent_code = 'statistics_foundation',
    sort_order = 722
WHERE node_code = 'machine_learning';

UPDATE skills
SET label = '深度学习',
    description = '通过神经网络、反向传播和训练策略理解表示学习方法。',
    parent_code = 'machine_learning',
    sort_order = 725
WHERE node_code = 'deep_learning';

UPDATE skills
SET label = '大模型应用',
    description = '把提示词工程、知识检索与工具调用组织成可落地 AI 应用。', 
    parent_code = 'deep_learning',
    sort_order = 727
WHERE node_code = 'llm_applications';

UPDATE skills
SET label = '推荐系统基础',
    description = '把召回、排序、特征与反馈闭环组合成个性化推荐系统。', 
    parent_code = 'machine_learning',
    sort_order = 729
WHERE node_code = 'recommendation_systems';

UPDATE skills
SET label = '计算理论与技术史',
    description = '把理论基础、编译原理、技术演进和工程伦理放进同一条长期学习主线。',
    parent_code = 'programming_language_foundations',
    sort_order = 800
WHERE node_code = 'theory_history';

UPDATE skills
SET label = '离散数学',
    description = '从集合、逻辑、关系到图论建立形式化表达能力。',
    parent_code = 'theory_history',
    sort_order = 810
WHERE node_code = 'discrete_mathematics';

UPDATE skills
SET label = '编译原理',
    description = '理解词法、语法、中间表示和编译器把语言变成机器执行单元的过程。',
    parent_code = 'discrete_mathematics',
    sort_order = 812
WHERE node_code = 'compiler_principles';

UPDATE skills
SET label = '计算机发展史',
    description = '理解关键技术浪潮、平台变迁与工程范式如何一轮轮演进。',
    parent_code = 'theory_history',
    sort_order = 820
WHERE node_code = 'cs_history';

UPDATE skills
SET label = '开源协作文化',
    description = '从许可证、社区协作到公共技术生态理解开源世界的工作方式。',
    parent_code = 'cs_history',
    sort_order = 821
WHERE node_code = 'open_source_culture';

UPDATE skills
SET label = '工程伦理',
    description = '在隐私、偏见、平台责任与技术边界之间形成更稳的判断框架。',
    parent_code = 'open_source_culture',
    sort_order = 822
WHERE node_code = 'engineering_ethics';

UPDATE skills
SET label = '职业发展与成长韧性',
    description = '把岗位理解、求职表达、实习适应与心理调节组织成长期成长支线。',
    parent_code = 'programming_language_foundations',
    sort_order = 900
WHERE node_code = 'career_employment_readiness';

UPDATE skills
SET label = '职业定位',
    description = '理解自己的能力边界、兴趣方向和阶段性求职目标。',
    parent_code = 'career_employment_readiness',
    sort_order = 910
WHERE node_code = 'career_positioning';

UPDATE skills
SET label = '岗位信息研判',
    description = '学会拆解 JD、岗位能力图谱与行业差异，判断自己该补什么。',
    parent_code = 'career_positioning',
    sort_order = 911
WHERE node_code = 'job_market_research';

UPDATE skills
SET label = '简历与作品集表达',
    description = '把经历、项目和成果组织成 HR 与面试官能迅速抓住重点的材料。',
    parent_code = 'career_positioning',
    sort_order = 920
WHERE node_code = 'resume_portfolio';

UPDATE skills
SET label = '面试准备与复盘',
    description = '围绕自我介绍、项目讲述、行为面试和复盘机制持续迭代表达。', 
    parent_code = 'resume_portfolio',
    sort_order = 922
WHERE node_code = 'interview_preparation';

UPDATE skills
SET label = '人脉拓展与个人品牌',
    description = '通过校友、社区、公开表达和持续输出建立长期职业影响力。',
    parent_code = 'job_market_research',
    sort_order = 912
WHERE node_code = 'networking_personal_brand';

UPDATE skills
SET label = '实习与职场适应',
    description = '帮助学生在进入团队后快速适应任务节奏、协作方式与基本职业规范。',
    parent_code = 'career_employment_readiness',
    sort_order = 930
WHERE node_code = 'internship_workplace_adaptation';

UPDATE skills
SET label = '实习目标设定',
    description = '进入团队前明确阶段目标、学习重点和希望拿到的成果。', 
    parent_code = 'internship_workplace_adaptation',
    sort_order = 931
WHERE node_code = 'internship_goal_setting';

UPDATE skills
SET label = '任务执行与跟进',
    description = '学会拆任务、报风险、同步进度并持续把事情推进到可交付。', 
    parent_code = 'internship_goal_setting',
    sort_order = 932
WHERE node_code = 'task_execution_followup';

UPDATE skills
SET label = '职场沟通',
    description = '在向导师、同事或产品同步信息时保持准确、简洁和对齐。', 
    parent_code = 'internship_workplace_adaptation',
    sort_order = 933
WHERE node_code = 'workplace_communication';

UPDATE skills
SET label = '团队协作',
    description = '在多人协作中理解上下游、反馈闭环与角色边界。', 
    parent_code = 'workplace_communication',
    sort_order = 934
WHERE node_code = 'teamwork_collaboration';

UPDATE skills
SET label = '职场专业度',
    description = '在时间观念、文档沉淀、责任感和反馈习惯上形成稳定职业素养。',
    parent_code = 'task_execution_followup',
    sort_order = 935
WHERE node_code = 'workplace_professionalism';

UPDATE skills
SET label = '心理调节与成长韧性',
    description = '帮助学生在求职、学习和实习压力下保持稳定节奏与恢复能力。',
    parent_code = 'career_employment_readiness',
    sort_order = 940
WHERE node_code = 'wellbeing_self_management';

UPDATE skills
SET label = '压力管理',
    description = '识别压力来源，建立拆解任务、缓冲节奏和恢复状态的方法。', 
    parent_code = 'wellbeing_self_management',
    sort_order = 941
WHERE node_code = 'stress_management';

UPDATE skills
SET label = '情绪调节',
    description = '识别情绪波动、避免陷入自我否定，并做更平稳的表达与恢复。', 
    parent_code = 'stress_management',
    sort_order = 942
WHERE node_code = 'emotion_regulation';

UPDATE skills
SET label = '成长型心态',
    description = '把失败、反馈与短期挫折转化成下一轮学习与行动计划。', 
    parent_code = 'wellbeing_self_management',
    sort_order = 944
WHERE node_code = 'growth_mindset';

UPDATE skills
SET label = '习惯与精力管理',
    description = '通过作息、复盘、专注与阶段节奏管理维持长期输出。', 
    parent_code = 'growth_mindset',
    sort_order = 945
WHERE node_code = 'habit_energy_management';

UPDATE skills
SET label = '倦怠预防',
    description = '识别长期高压和低反馈带来的倦怠信号，及时调整节奏与目标。', 
    parent_code = 'emotion_regulation',
    sort_order = 943
WHERE node_code = 'burnout_prevention';

DELETE FROM skill_relations;

INSERT INTO skill_relations(source_node_code, target_node_code, relation_type, label, sort_order, created_at, updated_at) VALUES
('computational_thinking', 'data_modeling', 'ADVANCE_TO', '抽象与拆解能力会迁移到数据建模', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('algorithms_analysis', 'machine_learning', 'ADVANCE_TO', '算法基础会直接支撑机器学习理解', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('object_oriented_modeling', 'domain_modeling_design', 'BRIDGE', '对象建模会过渡到业务领域建模', 30, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('java_collections_io', 'distributed_systems', 'ADVANCE_TO', '并发与 I/O 基础会延伸到分布式调用', 40, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('frontend_component_architecture', 'api_contract_design', 'CO_LEARN', '前后端协作时适合同步补接口契约', 50, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('state_data_flow', 'relational_databases', 'CO_LEARN', '前端状态流和后端数据模型最好一起梳理', 60, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('backend_service_development', 'transaction_consistency', 'ADVANCE_TO', '服务开发深入后会遇到事务与一致性', 70, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('api_contract_design', 'network_protocols', 'CO_LEARN', '接口设计与协议语义适合并行补强', 80, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('automated_testing', 'observability_incident_response', 'BRIDGE', '测试策略会在上线监控与排障中继续发挥作用', 90, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('devops_delivery', 'cloud_native_basics', 'ADVANCE_TO', '交付流程成熟后会自然走向云原生', 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('query_optimization', 'observability_incident_response', 'BRIDGE', '慢查询治理需要监控和排障联动', 110, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('linux_operations', 'devops_delivery', 'CO_LEARN', 'Linux 运维基础和交付能力适合同时练', 120, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('computer_networks', 'distributed_systems', 'ADVANCE_TO', '网络理解越扎实，分布式系统越容易吃透', 130, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('web_security', 'secure_engineering', 'ADVANCE_TO', '单点安全知识最终要落到工程实践', 140, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('python_data_tools', 'analytics_data_visualization', 'BRIDGE', '数据清洗之后还要能把结果讲出来', 150, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('machine_learning', 'recommendation_systems', 'ADVANCE_TO', '模型基础可以直接延伸到推荐系统', 160, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('llm_applications', 'api_contract_design', 'BRIDGE', '大模型接入最终仍要回到接口与服务设计', 170, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('discrete_mathematics', 'algorithms_analysis', 'ADVANCE_TO', '离散数学会回流强化算法理解', 180, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('compiler_principles', 'operating_systems', 'BRIDGE', '编译、运行时与系统层会在实现细节上相遇', 190, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('open_source_culture', 'collaborative_development', 'CO_LEARN', '开源协作会直接提升多人协作能力', 200, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('project_storytelling', 'interview_preparation', 'ADVANCE_TO', '项目叙事整理好后就能进入面试表达', 210, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('task_execution_followup', 'collaborative_development', 'BRIDGE', '任务推进能力需要靠真实协作场景磨出来', 220, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('growth_mindset', 'problem_solving_patterns', 'CO_LEARN', '成长型心态能支撑长期的问题求解训练', 230, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('self_reflection_planning', 'interview_preparation', 'BRIDGE', '复盘规划会直接提升面试复盘质量', 240, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
