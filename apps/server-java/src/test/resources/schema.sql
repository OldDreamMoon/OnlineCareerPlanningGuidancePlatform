CREATE TABLE IF NOT EXISTS users (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  email         VARCHAR(255) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role          VARCHAR(20)  NOT NULL,
  tier          VARCHAR(20)  NOT NULL DEFAULT 'FREE',
  status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
  display_name  VARCHAR(100) NOT NULL,
  real_name     VARCHAR(100),
  last_login_at TIMESTAMP    NULL,
  is_deleted    TINYINT      NOT NULL DEFAULT 0,
  created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS student_profiles (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id         BIGINT       NOT NULL,
  job_status      VARCHAR(50),
  school_name     VARCHAR(150),
  school_name_key VARCHAR(160),
  major           VARCHAR(100),
  grade           VARCHAR(20),
  gpa             VARCHAR(50),
  target_position VARCHAR(100),
  honors          CLOB,
  github          VARCHAR(255),
  portfolio       VARCHAR(255),
  social_links_json CLOB,
  phone           VARCHAR(50),
  wechat          VARCHAR(100),
  avatar_bucket   VARCHAR(100),
  avatar_object_key VARCHAR(255),
  avatar_content_type VARCHAR(100),
  avatar_updated_at TIMESTAMP,
  skill_tags      VARCHAR(512),
  self_intro      CLOB,
  created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (user_id)
);

CREATE INDEX idx_student_profiles_school_name_key ON student_profiles(school_name_key);

CREATE TABLE IF NOT EXISTS mentor_profiles (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id         BIGINT        NOT NULL,
  company_name    VARCHAR(200),
  job_title       VARCHAR(100),
  show_real_name  TINYINT       NOT NULL DEFAULT 0,
  avatar_url      VARCHAR(255),
  avatar_bucket   VARCHAR(100),
  avatar_object_key VARCHAR(255),
  avatar_content_type VARCHAR(100),
  avatar_updated_at TIMESTAMP,
  expertise_tags  VARCHAR(512),
  service_scenes  VARCHAR(512),
  bio             CLOB,
  suitable_for    CLOB,
  not_suitable_for CLOB,
  prep_materials  CLOB,
  reply_rhythm    CLOB,
  price_fen       INT           NOT NULL DEFAULT 5000,
  is_available    TINYINT       NOT NULL DEFAULT 1,
  approval_status VARCHAR(20)   NOT NULL DEFAULT 'APPROVED',
  total_orders    INT           NOT NULL DEFAULT 0,
  avg_rating      DECIMAL(3, 2) NOT NULL DEFAULT 0.00,
  created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (user_id)
);

CREATE TABLE IF NOT EXISTS mentor_favorites (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_user_id  BIGINT      NOT NULL,
  mentor_user_id   BIGINT      NOT NULL,
  created_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (student_user_id, mentor_user_id),
  CONSTRAINT fk_mentor_favorites_student_user FOREIGN KEY (student_user_id) REFERENCES users(id),
  CONSTRAINT fk_mentor_favorites_mentor_user FOREIGN KEY (mentor_user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS enterprise_profiles (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id         BIGINT       NOT NULL,
  company_name    VARCHAR(200),
  industry        VARCHAR(100),
  company_size    VARCHAR(50),
  hiring_tags     VARCHAR(512),
  contact_title   VARCHAR(100),
  bio             CLOB,
  external_links  CLOB,
  preferences     CLOB,
  logo_bucket     VARCHAR(100),
  logo_object_key VARCHAR(255),
  logo_content_type VARCHAR(100),
  logo_updated_at TIMESTAMP,
  approval_status VARCHAR(20)  NOT NULL DEFAULT 'APPROVED',
  created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (user_id)
);

CREATE TABLE IF NOT EXISTS certification_submissions (
  id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id              BIGINT       NOT NULL,
  user_role            VARCHAR(20)  NOT NULL,
  real_name            VARCHAR(100) NOT NULL,
  company_name         VARCHAR(200),
  job_title            VARCHAR(100),
  status               VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
  review_note          VARCHAR(1000),
  reviewed_by          BIGINT,
  reviewed_at          TIMESTAMP,
  previous_submission_id BIGINT,
  is_current           TINYINT      NOT NULL DEFAULT 1,
  submitted_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_certification_submissions_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_certification_submissions_reviewer FOREIGN KEY (reviewed_by) REFERENCES users(id),
  CONSTRAINT fk_certification_submissions_previous FOREIGN KEY (previous_submission_id) REFERENCES certification_submissions(id)
);

CREATE INDEX idx_certification_submissions_user ON certification_submissions(user_id, is_current, submitted_at);
CREATE INDEX idx_certification_submissions_status ON certification_submissions(status, submitted_at);

CREATE TABLE IF NOT EXISTS certification_submission_assets (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  submission_id     BIGINT       NOT NULL,
  storage_bucket    VARCHAR(100) NOT NULL,
  object_key        VARCHAR(255) NOT NULL,
  original_filename VARCHAR(255) NOT NULL,
  content_type      VARCHAR(100),
  size_bytes        BIGINT       NOT NULL,
  lifecycle_status  VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
  delete_reason     VARCHAR(100),
  deleted_at        TIMESTAMP,
  created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_certification_assets_object_key UNIQUE (object_key),
  CONSTRAINT fk_certification_assets_submission FOREIGN KEY (submission_id) REFERENCES certification_submissions(id)
);

CREATE INDEX idx_certification_assets_submission ON certification_submission_assets(submission_id, lifecycle_status);

CREATE TABLE IF NOT EXISTS mentor_service_packages (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  mentor_user_id BIGINT NOT NULL,
  package_name VARCHAR(40) NOT NULL,
  scene_code VARCHAR(60) NOT NULL,
  scene_label VARCHAR(30) NOT NULL,
  delivery_mode VARCHAR(20) NOT NULL,
  duration_minutes INT,
  price_fen INT NOT NULL DEFAULT 0,
  description VARCHAR(240),
  enabled TINYINT NOT NULL DEFAULT 1,
  sort_no INT NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_mentor_service_packages_sort UNIQUE (mentor_user_id, sort_no),
  CONSTRAINT fk_mentor_service_packages_mentor FOREIGN KEY (mentor_user_id) REFERENCES users(id)
);

CREATE INDEX idx_mentor_service_packages_enabled ON mentor_service_packages(mentor_user_id, enabled, sort_no);

CREATE TABLE IF NOT EXISTS student_portrait_snapshots (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_user_id BIGINT       NOT NULL,
  portrait_tags   CLOB         NOT NULL,
  evidence        CLOB         NOT NULL,
  updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (student_user_id)
);

CREATE TABLE IF NOT EXISTS student_recommendation_snapshots (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_user_id BIGINT NOT NULL,
  content_text CLOB NOT NULL,
  target_position VARCHAR(100),
  skill_tags_json CLOB,
  portrait_tags_json CLOB,
  latest_resume_record_id BIGINT,
  latest_resume_target_role VARCHAR(100),
  latest_resume_summary CLOB,
  latest_resume_suggestions_json CLOB,
  latest_interview_session_id VARCHAR(64),
  latest_interview_target_role VARCHAR(100),
  latest_interview_weaknesses_json CLOB,
  latest_interview_suggestions_json CLOB,
  signal_flags_json CLOB,
  content_hash CHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (student_user_id)
);

CREATE TABLE IF NOT EXISTS mentor_recommendation_snapshots (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  mentor_user_id BIGINT NOT NULL,
  content_text CLOB NOT NULL,
  expertise_tags_json CLOB,
  service_scenes_json CLOB,
  quality_score INT NOT NULL DEFAULT 0,
  price_fen INT NOT NULL DEFAULT 0,
  is_available TINYINT NOT NULL DEFAULT 1,
  content_hash CHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (mentor_user_id)
);

CREATE TABLE IF NOT EXISTS recommendation_embedding_vectors (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  entity_type VARCHAR(30) NOT NULL,
  entity_id BIGINT NOT NULL,
  model_code VARCHAR(60) NOT NULL,
  vector_dim INT NOT NULL,
  vector_json CLOB NOT NULL,
  content_hash CHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (entity_type, entity_id, model_code)
);

CREATE TABLE IF NOT EXISTS mentor_recommendation_runs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_user_id BIGINT NOT NULL,
  scene VARCHAR(60),
  keyword VARCHAR(255),
  expertise VARCHAR(255),
  filter_payload_json CLOB NOT NULL,
  recall_model_code VARCHAR(60) NOT NULL,
  rerank_version VARCHAR(60) NOT NULL,
  weak_signal TINYINT NOT NULL DEFAULT 0,
  candidate_count INT NOT NULL DEFAULT 0,
  recalled_count INT NOT NULL DEFAULT 0,
  top_mentor_user_ids_json CLOB NOT NULL,
  basis_summary CLOB,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS mentor_recommendation_events (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  run_id BIGINT NOT NULL,
  mentor_user_id BIGINT NOT NULL,
  event_type VARCHAR(30) NOT NULL,
  stage_rank INT,
  score DECIMAL(10, 6),
  detail_json CLOB,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS student_profile_privacy_settings (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_user_id BIGINT       NOT NULL,
  settings_json   CLOB         NOT NULL,
  created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (student_user_id)
);

CREATE TABLE IF NOT EXISTS student_profile_security_codes (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_user_id BIGINT       NOT NULL,
  purpose         VARCHAR(64)  NOT NULL,
  target_email    VARCHAR(255) NOT NULL,
  code_hash       CHAR(64)     NOT NULL,
  expires_at      TIMESTAMP    NOT NULL,
  next_send_at    TIMESTAMP    NOT NULL,
  created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (student_user_id, purpose, target_email)
);

CREATE TABLE IF NOT EXISTS posts (
  id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id                  BIGINT       NOT NULL,
  title                    VARCHAR(200) NOT NULL,
  content                  CLOB         NOT NULL,
  tags                     VARCHAR(255),
  scenario_code            VARCHAR(60)  NOT NULL DEFAULT 'GENERAL_HELP',
  resolved_status          VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
  moderation_status        VARCHAR(20)  NOT NULL DEFAULT 'PASS',
  risk_level               VARCHAR(20)  NOT NULL DEFAULT 'LOW',
  last_moderation_event_id BIGINT,
  is_deleted               TINYINT      NOT NULL DEFAULT 0,
  created_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS comments (
  id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
  post_id                  BIGINT      NOT NULL,
  user_id                  BIGINT      NOT NULL,
  content                  CLOB        NOT NULL,
  is_ai                    TINYINT     NOT NULL DEFAULT 0,
  moderation_status        VARCHAR(20) NOT NULL DEFAULT 'PASS',
  risk_level               VARCHAR(20) NOT NULL DEFAULT 'LOW',
  last_moderation_event_id BIGINT,
  is_deleted               TINYINT     NOT NULL DEFAULT 0,
  created_at               TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at               TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS post_likes (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  post_id    BIGINT     NOT NULL,
  user_id    BIGINT     NOT NULL,
  created_at TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (post_id, user_id)
);

CREATE TABLE skills (
    node_code VARCHAR(100) PRIMARY KEY,
    label VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    parent_code VARCHAR(100),
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_skills_parent_code FOREIGN KEY (parent_code) REFERENCES skills(node_code)
);

CREATE TABLE skill_progress (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_user_id BIGINT NOT NULL,
    node_code VARCHAR(100) NOT NULL,
    progress_status VARCHAR(20) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_skill_progress_user_node UNIQUE (student_user_id, node_code),
    CONSTRAINT fk_skill_progress_user FOREIGN KEY (student_user_id) REFERENCES users(id),
    CONSTRAINT fk_skill_progress_node FOREIGN KEY (node_code) REFERENCES skills(node_code)
);

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

CREATE TABLE skill_relations (
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

UPDATE skills SET label = '计算机科学成长底座', description = '作为整张技能星图的中央枢纽，把技术基础、工程实践、职业发展和成长韧性汇成一棵主树。', parent_code = NULL, sort_order = 100 WHERE node_code = 'programming_language_foundations';
UPDATE skills SET label = 'C 语言与底层感知', description = '从指针、内存和过程式编程建立对机器执行过程的直觉。', parent_code = 'computational_thinking', sort_order = 210 WHERE node_code = 'c_programming_basics';
UPDATE skills SET label = 'Java 与工程语言实践', description = '掌握面向对象语法、标准库和工程开发里最常见的 Java 基础能力。', parent_code = 'programming_language_foundations', sort_order = 300 WHERE node_code = 'java_programming';
UPDATE skills SET label = '对象建模与职责拆分', description = '把类、接口、对象协作与职责边界设计得更稳定、更清晰。', parent_code = 'java_programming', sort_order = 310 WHERE node_code = 'object_oriented_modeling';
UPDATE skills SET label = '数据结构与组织方式', description = '围绕线性表、树、图、哈希和状态表示组织问题信息。', parent_code = 'computational_thinking', sort_order = 220 WHERE node_code = 'data_structures';
UPDATE skills SET label = '算法分析与复杂度意识', description = '通过时间复杂度、空间复杂度和常见策略训练解题与实现判断力。', parent_code = 'data_structures', sort_order = 221 WHERE node_code = 'algorithms_analysis';
UPDATE skills SET label = '软件工程与应用交付', description = '把需求理解、系统设计、协作开发、测试和交付串成完整工程闭环。', parent_code = 'programming_language_foundations', sort_order = 400 WHERE node_code = 'software_engineering_delivery';
UPDATE skills SET label = '前端工程', description = '关注浏览器、组件化、状态管理与面向产品界面的前端实现方式。', parent_code = 'software_architecture_design', sort_order = 420 WHERE node_code = 'frontend_engineering';
UPDATE skills SET label = '后端服务开发', description = '把接口、服务层、持久层与业务规则组织成稳定可维护的后端系统。', parent_code = 'software_architecture_design', sort_order = 412 WHERE node_code = 'backend_service_development';
UPDATE skills SET label = '接口契约设计', description = '围绕资源、协议、错误码、版本演进和协作边界设计稳定 API。', parent_code = 'service_layer_design', sort_order = 414 WHERE node_code = 'api_contract_design';
UPDATE skills SET label = '自动化测试', description = '用测试守住关键业务路径、模块协作和版本回归稳定性。', parent_code = 'collaborative_development', sort_order = 431 WHERE node_code = 'automated_testing';
UPDATE skills SET label = '协作开发流程', description = '通过任务拆分、代码评审、分支策略和文档同步提升多人协作效率。', parent_code = 'software_engineering_delivery', sort_order = 430 WHERE node_code = 'collaborative_development';
UPDATE skills SET label = 'DevOps 与发布交付', description = '围绕构建、部署、环境管理与发布回滚形成交付工程能力。', parent_code = 'collaborative_development', sort_order = 440 WHERE node_code = 'devops_delivery';
UPDATE skills SET label = '数据、数据库与信息系统', description = '从数据建模、数据库到数仓分析与数据系统理解信息如何被沉淀和使用。', parent_code = 'programming_language_foundations', sort_order = 500 WHERE node_code = 'data_database_systems';
UPDATE skills SET label = '关系数据库', description = '掌握表结构、约束、SQL、索引与关系型数据组织方式。', parent_code = 'data_database_systems', sort_order = 510 WHERE node_code = 'relational_databases';
UPDATE skills SET label = '数据建模', description = '把业务对象、关系和查询需求沉淀成兼顾一致性与演进性的结构。', parent_code = 'relational_databases', sort_order = 512 WHERE node_code = 'data_modeling';
UPDATE skills SET label = '查询优化', description = '理解执行计划、索引命中、慢查询定位和常见 SQL 调优策略。', parent_code = 'data_modeling', sort_order = 514 WHERE node_code = 'query_optimization';
UPDATE skills SET label = '事务与一致性', description = '掌握隔离级别、锁、并发写入和一致性约束的核心问题。', parent_code = 'data_modeling', sort_order = 513 WHERE node_code = 'transaction_consistency';
UPDATE skills SET label = '大数据基础', description = '理解批处理、流处理、消息系统和规模化数据处理的基本框架。', parent_code = 'data_warehouse_etl', sort_order = 521 WHERE node_code = 'big_data_foundations';
UPDATE skills SET label = '系统、网络与基础设施', description = '从组成原理、操作系统、网络、安全到分布式基础设施构建系统视角。', parent_code = 'programming_language_foundations', sort_order = 600 WHERE node_code = 'systems_infrastructure';
UPDATE skills SET label = '计算机组成原理', description = '理解 CPU、存储层次、指令执行与整机结构，补齐底层运行直觉。', parent_code = 'systems_infrastructure', sort_order = 610 WHERE node_code = 'computer_organization';
UPDATE skills SET label = '操作系统', description = '从进程线程、内存管理到文件与调度理解系统资源如何被组织。', parent_code = 'computer_organization', sort_order = 612 WHERE node_code = 'operating_systems';
UPDATE skills SET label = 'Linux 运维基础', description = '围绕命令行、权限、日志、进程和服务管理建立系统排障能力。', parent_code = 'operating_systems', sort_order = 616 WHERE node_code = 'linux_operations';
UPDATE skills SET label = '网络安全与服务韧性', description = '把网络通信、安全防护、服务治理与稳定性设计串成一条系统链路。', parent_code = 'systems_infrastructure', sort_order = 630 WHERE node_code = 'network_security';
UPDATE skills SET label = '分布式系统基础', description = '理解复制、负载均衡、服务发现和跨节点调用中的关键难题。', parent_code = 'network_security', sort_order = 640 WHERE node_code = 'distributed_systems';
UPDATE skills SET label = '云原生基础', description = '围绕容器、镜像、编排和配置管理理解现代基础设施的运行方式。', parent_code = 'distributed_systems', sort_order = 642 WHERE node_code = 'cloud_native_basics';
UPDATE skills SET label = '计算机网络', description = '掌握网络分层、设备角色、请求路径和典型排障思路。', parent_code = 'network_security', sort_order = 631 WHERE node_code = 'computer_networks';
UPDATE skills SET label = '网络协议', description = '理解 TCP/IP、HTTP、DNS、TLS 等协议在真实业务中的协作方式。', parent_code = 'computer_networks', sort_order = 632 WHERE node_code = 'network_protocols';
UPDATE skills SET label = 'Web 安全基础', description = '识别认证授权、XSS、CSRF、注入和常见安全设计缺口。', parent_code = 'network_protocols', sort_order = 633 WHERE node_code = 'web_security';
UPDATE skills SET label = '应用密码学', description = '理解哈希、加密、签名、证书与密钥管理在工程中的用法。', parent_code = 'web_security', sort_order = 634 WHERE node_code = 'applied_cryptography';
UPDATE skills SET label = '安全工程实践', description = '把安全要求落实到接口、配置、代码审计和上线检查清单。', parent_code = 'applied_cryptography', sort_order = 635 WHERE node_code = 'secure_engineering';
UPDATE skills SET label = '硬件与嵌入式', description = '理解数字逻辑、单片机、嵌入式软件与设备联动的工程链路。', parent_code = 'computer_organization', sort_order = 620 WHERE node_code = 'hardware_embedded';
UPDATE skills SET label = '数字逻辑', description = '围绕布尔代数、组合逻辑和时序逻辑建立硬件表达基础。', parent_code = 'hardware_embedded', sort_order = 621 WHERE node_code = 'digital_logic';
UPDATE skills SET label = '微机原理', description = '理解总线、中断、寄存器和外设通信在嵌入式场景里的工作方式。', parent_code = 'hardware_embedded', sort_order = 622 WHERE node_code = 'microcomputer_principles';
UPDATE skills SET label = '嵌入式系统', description = '面向资源受限设备学习驱动、调试与软硬件协同实现。', parent_code = 'microcomputer_principles', sort_order = 623 WHERE node_code = 'embedded_systems';
UPDATE skills SET label = '物联网系统设计', description = '连接传感器、边缘设备、通信链路和云端平台，形成完整 IoT 方案。', parent_code = 'embedded_systems', sort_order = 624 WHERE node_code = 'iot_system_design';
UPDATE skills SET label = '智能与数据科学', description = '从数据处理、统计推断到机器学习与大模型应用构建智能系统视角。', parent_code = 'programming_language_foundations', sort_order = 700 WHERE node_code = 'ai_data_science';
UPDATE skills SET label = 'Python 数据工具', description = '用 Python 处理数据、写脚本和搭实验原型，形成数据工作台能力。', parent_code = 'ai_data_science', sort_order = 710 WHERE node_code = 'python_data_tools';
UPDATE skills SET label = '统计学基础', description = '从概率、分布到估计与推断，为机器学习与实验分析做准备。', parent_code = 'ai_data_science', sort_order = 720 WHERE node_code = 'statistics_foundation';
UPDATE skills SET label = '机器学习', description = '围绕特征、训练、评估和泛化能力理解典型机器学习流程。', parent_code = 'statistics_foundation', sort_order = 722 WHERE node_code = 'machine_learning';
UPDATE skills SET label = '深度学习', description = '通过神经网络、反向传播和训练策略理解表示学习方法。', parent_code = 'machine_learning', sort_order = 725 WHERE node_code = 'deep_learning';
UPDATE skills SET label = '大模型应用', description = '把提示词工程、知识检索与工具调用组织成可落地 AI 应用。', parent_code = 'deep_learning', sort_order = 727 WHERE node_code = 'llm_applications';
UPDATE skills SET label = '推荐系统基础', description = '把召回、排序、特征与反馈闭环组合成个性化推荐系统。', parent_code = 'machine_learning', sort_order = 729 WHERE node_code = 'recommendation_systems';
INSERT INTO skills(node_code, label, description, parent_code, sort_order, created_at, updated_at) VALUES
('prompt_context_engineering', '提示词与上下文工程', '把 system prompt、few-shot、约束、文件上下文与历史对话组织成稳定可复用的模型输入。', 'prompt_rag_agent', 730, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai_native_development', 'AI 原生开发范式', '围绕需求拆解、上下文组织、模型协作和人工验收建立新一代开发流程。', 'prompt_context_engineering', 731, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('vibe_coding_workflows', 'Vibe Coding 工作流', '通过自然语言驱动原型、快速试错与交互探索，但同时守住边界、验证与版本控制。', 'ai_native_development', 732, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai_pair_programming', 'AI 结对编程', '把任务拆分、代码生成、补全、解释和人工修正组织成高频协作循环。', 'vibe_coding_workflows', 733, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai_codebase_grounding', '代码库检索与上下文对齐', '让模型在真实仓库结构、接口契约、依赖边界和现有代码风格中工作，而不是脱离上下文生成代码。', 'ai_pair_programming', 734, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai_coding_agent_tools', '工具调用与编码 Agent', '把搜索、读写文件、运行测试、检查日志和执行命令串成可控的编码代理链路。', 'ai_codebase_grounding', 735, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai_code_review_guardrails', 'AI 代码审查与护栏', '围绕 diff 审查、测试门禁、权限边界、回滚策略和安全检查约束 AI 输出风险。', 'ai_coding_agent_tools', 736, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai_eval_observability', 'AI 编程评测与可观测性', '通过任务集、通过率、人工复查、失败留痕和运行指标判断 AI 开发链路是否真正有效。', 'ai_code_review_guardrails', 737, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO skills(node_code, label, description, parent_code, sort_order, created_at, updated_at) VALUES
('ai_programming_engineering', 'AI 编程与智能开发', '把提示词、AI 协作编码、编码 Agent 与工程治理串成一棵独立的应用型学习主树。', 'programming_language_foundations', 750, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('prompt_pattern_design', '提示模式设计', '围绕角色设定、few-shot、格式约束、分步指令和输出模板沉淀可复用提示模式。', 'prompt_context_engineering', 752, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('context_window_orchestration', '上下文窗口编排', '学会选择仓库片段、文档、错误日志和历史对话，让模型看到刚好够用的上下文。', 'prompt_context_engineering', 753, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('tool_schema_design', '工具接口与 Schema 设计', '把函数调用、参数约束、结构化返回和工具契约设计得足够清晰，便于模型稳定调用。', 'prompt_context_engineering', 754, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai_debug_refactor_loops', 'AI 调试与重构闭环', '让模型参与问题定位、回归验证、代码解释和重构建议，但始终保留人工判断与验收。', 'ai_native_development', 763, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai_coding_agent_systems', '编码 Agent 系统', '从上下文检索、工具链调用到多阶段执行，理解编码 Agent 的系统组成。', 'ai_programming_engineering', 770, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('multi_agent_workflows', '多 Agent 协作编排', '把需求分析、实现、测试、审查等不同角色代理组织成可控的多阶段流水线。', 'ai_coding_agent_systems', 773, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai_engineering_governance', 'AI 工程治理', '围绕评测、审查、路由、成本和安全边界，让 AI 编程能力真正能落地到团队流程。', 'ai_programming_engineering', 780, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('model_routing_cost_control', '模型路由与成本控制', '根据任务类型、时延要求、预算和稳定性选择模型、降级路径与缓存策略。', 'ai_engineering_governance', 783, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
UPDATE skills SET label = '提示词与上下文工程', description = '围绕 system prompt、上下文裁剪、文件片段与约束指令组织稳定可复用的模型输入。', parent_code = 'ai_programming_engineering', sort_order = 751 WHERE node_code = 'prompt_context_engineering';
UPDATE skills SET label = 'AI 原生开发流程', description = '把需求拆解、原型试错、AI 协作编码和人工验收组织成新的开发工作流。', parent_code = 'ai_programming_engineering', sort_order = 760 WHERE node_code = 'ai_native_development';
UPDATE skills SET label = 'Vibe Coding 原型流', description = '适合快速探索交互、原型和想法，但必须辅以人工审查、测试和版本管理。', parent_code = 'ai_native_development', sort_order = 761 WHERE node_code = 'vibe_coding_workflows';
UPDATE skills SET label = 'AI 结对编程', description = '把补全、解释、实现、纠错与人工修改串成高频协作循环，提升开发吞吐。', parent_code = 'ai_native_development', sort_order = 762 WHERE node_code = 'ai_pair_programming';
UPDATE skills SET label = '代码库检索与上下文对齐', description = '让模型在真实仓库结构、接口契约、依赖边界和现有代码风格中工作，而不是脱离上下文生成代码。', parent_code = 'ai_coding_agent_systems', sort_order = 771 WHERE node_code = 'ai_codebase_grounding';
UPDATE skills SET label = '工具调用与编码 Agent', description = '把搜索、读写文件、运行测试、检查日志和执行命令串成可控的编码代理链路。', parent_code = 'ai_coding_agent_systems', sort_order = 772 WHERE node_code = 'ai_coding_agent_tools';
UPDATE skills SET label = 'AI 代码审查与护栏', description = '围绕 diff 审查、测试门禁、权限边界、回滚策略和安全检查约束 AI 输出风险。', parent_code = 'ai_engineering_governance', sort_order = 781 WHERE node_code = 'ai_code_review_guardrails';
UPDATE skills SET label = 'AI 编程评测与可观测性', description = '通过任务集、通过率、人工复查、失败留痕和运行指标判断 AI 开发链路是否真正有效。', parent_code = 'ai_engineering_governance', sort_order = 782 WHERE node_code = 'ai_eval_observability';
UPDATE skills SET label = 'AI 编程与智能开发', description = '把提示词、AI 协作编码、编码 Agent 与工程治理按真实落地流程串成一棵应用型学习主树。', parent_code = 'programming_language_foundations', sort_order = 750 WHERE node_code = 'ai_programming_engineering';
UPDATE skills SET label = '提示词与上下文工程', description = '先学会把目标、约束、仓库片段与运行信息组织成模型真正能用的输入。', parent_code = 'ai_programming_engineering', sort_order = 751 WHERE node_code = 'prompt_context_engineering';
UPDATE skills SET label = '提示模式设计', description = '围绕角色设定、few-shot、输出模板和分步指令沉淀稳定可复用的提示套路。', parent_code = 'prompt_context_engineering', sort_order = 752 WHERE node_code = 'prompt_pattern_design';
UPDATE skills SET label = '上下文窗口编排', description = '学会裁剪文件、日志、文档和历史对话，让模型在有限窗口里看到最关键的信息。', parent_code = 'prompt_context_engineering', sort_order = 753 WHERE node_code = 'context_window_orchestration';
UPDATE skills SET label = '工具接口与 Schema 设计', description = '在理解上下文裁剪之后，继续学习把工具参数、结构化返回和调用约束设计得足够清晰。', parent_code = 'context_window_orchestration', sort_order = 754 WHERE node_code = 'tool_schema_design';
UPDATE skills SET label = 'AI 原生开发流程', description = '把需求拆解、原型试错、AI 协作编码与人工验收组织成新的开发工作流。', parent_code = 'ai_programming_engineering', sort_order = 760 WHERE node_code = 'ai_native_development';
UPDATE skills SET label = 'AI 结对编程', description = '把补全、解释、实现、纠错与人工修改串成高频协作循环，逐步形成稳定的协作节奏。', parent_code = 'ai_native_development', sort_order = 762 WHERE node_code = 'ai_pair_programming';
UPDATE skills SET label = 'AI 调试与重构闭环', description = '在结对编程之后继续深入，让模型参与定位问题、验证修改和辅助重构，但仍由人工最终验收。', parent_code = 'ai_pair_programming', sort_order = 763 WHERE node_code = 'ai_debug_refactor_loops';
UPDATE skills SET label = '编码 Agent 系统', description = '从上下文检索、工具链调用到多阶段执行，理解编码 Agent 的系统组成。', parent_code = 'ai_programming_engineering', sort_order = 770 WHERE node_code = 'ai_coding_agent_systems';
UPDATE skills SET label = '代码库检索与上下文对齐', description = '先让模型在真实仓库结构、接口契约、依赖边界和既有风格中找到正确上下文。', parent_code = 'ai_coding_agent_systems', sort_order = 771 WHERE node_code = 'ai_codebase_grounding';
UPDATE skills SET label = '工具调用与编码 Agent', description = '在代码库对齐之后，再把搜索、读写文件、运行测试与命令执行串成可控的编码代理链路。', parent_code = 'ai_codebase_grounding', sort_order = 772 WHERE node_code = 'ai_coding_agent_tools';
UPDATE skills SET label = '多 Agent 协作编排', description = '当单 Agent 链路稳定之后，再把需求分析、实现、测试和审查组织成多阶段代理流水线。', parent_code = 'ai_coding_agent_tools', sort_order = 773 WHERE node_code = 'multi_agent_workflows';
UPDATE skills SET label = 'AI 代码审查与护栏', description = '先围绕 diff 审查、测试门禁、权限边界和回滚策略建立 AI 输出的基础护栏。', parent_code = 'ai_engineering_governance', sort_order = 781 WHERE node_code = 'ai_code_review_guardrails';
UPDATE skills SET label = 'AI 编程评测与可观测性', description = '在护栏建立后，进一步通过任务集、通过率、失败留痕和运行指标判断链路是否真正有效。', parent_code = 'ai_code_review_guardrails', sort_order = 782 WHERE node_code = 'ai_eval_observability';
UPDATE skills SET label = '模型路由与成本控制', description = '最后再根据任务类型、时延要求、预算和稳定性选择模型、降级路径与缓存策略。', parent_code = 'ai_eval_observability', sort_order = 783 WHERE node_code = 'model_routing_cost_control';
UPDATE skills SET label = '计算理论与技术史', description = '把理论基础、编译原理、技术演进和工程伦理放进同一条长期学习主线。', parent_code = 'programming_language_foundations', sort_order = 800 WHERE node_code = 'theory_history';
UPDATE skills SET label = '离散数学', description = '从集合、逻辑、关系到图论建立形式化表达能力。', parent_code = 'theory_history', sort_order = 810 WHERE node_code = 'discrete_mathematics';
UPDATE skills SET label = '编译原理', description = '理解词法、语法、中间表示和编译器把语言变成机器执行单元的过程。', parent_code = 'discrete_mathematics', sort_order = 812 WHERE node_code = 'compiler_principles';
UPDATE skills SET label = '计算机发展史', description = '理解关键技术浪潮、平台变迁与工程范式如何一轮轮演进。', parent_code = 'theory_history', sort_order = 820 WHERE node_code = 'cs_history';
UPDATE skills SET label = '开源协作文化', description = '从许可证、社区协作到公共技术生态理解开源世界的工作方式。', parent_code = 'cs_history', sort_order = 821 WHERE node_code = 'open_source_culture';
UPDATE skills SET label = '工程伦理', description = '在隐私、偏见、平台责任与技术边界之间形成更稳的判断框架。', parent_code = 'open_source_culture', sort_order = 822 WHERE node_code = 'engineering_ethics';
UPDATE skills SET label = '职业发展与成长韧性', description = '把岗位理解、求职表达、实习适应与心理调节组织成长期成长支线。', parent_code = 'programming_language_foundations', sort_order = 900 WHERE node_code = 'career_employment_readiness';
UPDATE skills SET label = '职业定位', description = '理解自己的能力边界、兴趣方向和阶段性求职目标。', parent_code = 'career_employment_readiness', sort_order = 910 WHERE node_code = 'career_positioning';
UPDATE skills SET label = '岗位信息研判', description = '学会拆解 JD、岗位能力图谱与行业差异，判断自己该补什么。', parent_code = 'career_positioning', sort_order = 911 WHERE node_code = 'job_market_research';
UPDATE skills SET label = '简历与作品集表达', description = '把经历、项目和成果组织成 HR 与面试官能迅速抓住重点的材料。', parent_code = 'career_positioning', sort_order = 920 WHERE node_code = 'resume_portfolio';
UPDATE skills SET label = '面试准备与复盘', description = '围绕自我介绍、项目讲述、行为面试和复盘机制持续迭代表达。', parent_code = 'resume_portfolio', sort_order = 922 WHERE node_code = 'interview_preparation';
UPDATE skills SET label = '人脉拓展与个人品牌', description = '通过校友、社区、公开表达和持续输出建立长期职业影响力。', parent_code = 'job_market_research', sort_order = 912 WHERE node_code = 'networking_personal_brand';
UPDATE skills SET label = '实习与职场适应', description = '帮助学生在进入团队后快速适应任务节奏、协作方式与基本职业规范。', parent_code = 'career_employment_readiness', sort_order = 930 WHERE node_code = 'internship_workplace_adaptation';
UPDATE skills SET label = '实习目标设定', description = '进入团队前明确阶段目标、学习重点和希望拿到的成果。', parent_code = 'internship_workplace_adaptation', sort_order = 931 WHERE node_code = 'internship_goal_setting';
UPDATE skills SET label = '任务执行与跟进', description = '学会拆任务、报风险、同步进度并持续把事情推进到可交付。', parent_code = 'internship_goal_setting', sort_order = 932 WHERE node_code = 'task_execution_followup';
UPDATE skills SET label = '职场沟通', description = '在向导师、同事或产品同步信息时保持准确、简洁和对齐。', parent_code = 'internship_workplace_adaptation', sort_order = 933 WHERE node_code = 'workplace_communication';
UPDATE skills SET label = '团队协作', description = '在多人协作中理解上下游、反馈闭环与角色边界。', parent_code = 'workplace_communication', sort_order = 934 WHERE node_code = 'teamwork_collaboration';
UPDATE skills SET label = '职场专业度', description = '在时间观念、文档沉淀、责任感和反馈习惯上形成稳定职业素养。', parent_code = 'task_execution_followup', sort_order = 935 WHERE node_code = 'workplace_professionalism';
UPDATE skills SET label = '心理调节与成长韧性', description = '帮助学生在求职、学习和实习压力下保持稳定节奏与恢复能力。', parent_code = 'career_employment_readiness', sort_order = 940 WHERE node_code = 'wellbeing_self_management';
UPDATE skills SET label = '压力管理', description = '识别压力来源，建立拆解任务、缓冲节奏和恢复状态的方法。', parent_code = 'wellbeing_self_management', sort_order = 941 WHERE node_code = 'stress_management';
UPDATE skills SET label = '情绪调节', description = '识别情绪波动、避免陷入自我否定，并做更平稳的表达与恢复。', parent_code = 'stress_management', sort_order = 942 WHERE node_code = 'emotion_regulation';
UPDATE skills SET label = '成长型心态', description = '把失败、反馈与短期挫折转化成下一轮学习与行动计划。', parent_code = 'wellbeing_self_management', sort_order = 944 WHERE node_code = 'growth_mindset';
UPDATE skills SET label = '习惯与精力管理', description = '通过作息、复盘、专注与阶段节奏管理维持长期输出。', parent_code = 'growth_mindset', sort_order = 945 WHERE node_code = 'habit_energy_management';
UPDATE skills SET label = '倦怠预防', description = '识别长期高压和低反馈带来的倦怠信号，及时调整节奏与目标。', parent_code = 'emotion_regulation', sort_order = 943 WHERE node_code = 'burnout_prevention';

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
('self_reflection_planning', 'interview_preparation', 'BRIDGE', '复盘规划会直接提升面试复盘质量', 240, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('prompt_context_engineering', 'requirement_analysis', 'CO_LEARN', '高质量提示词离不开清晰需求与约束表达', 250, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('vibe_coding_workflows', 'project_storytelling', 'BRIDGE', '快速原型之后仍要补齐方案说明与成果表达', 260, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai_pair_programming', 'collaborative_development', 'CO_LEARN', '人与 AI 协作本质上仍需要任务拆分与协作规范', 270, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai_codebase_grounding', 'api_contract_design', 'BRIDGE', '仓库上下文对齐离不开接口契约和边界定义', 280, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai_coding_agent_tools', 'automated_testing', 'ADVANCE_TO', '编码 Agent 真正可用必须挂在测试与验证链路上', 290, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai_code_review_guardrails', 'secure_engineering', 'CO_LEARN', 'AI 输出审查和安全工程检查需要同步建设', 300, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai_eval_observability', 'observability_incident_response', 'ADVANCE_TO', 'AI 编程链路最终仍要靠评测和运行观测收口', 310, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai_programming_engineering', 'prompt_rag_agent', 'BRIDGE', 'AI 编程工程会回流到 Prompt、RAG 与 Agent 方案设计', 320, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('tool_schema_design', 'api_contract_design', 'BRIDGE', '工具契约设计和 API 契约设计本质上是同一类边界表达', 330, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('context_window_orchestration', 'data_modeling', 'CO_LEARN', '上下文筛选能力会直接受益于数据结构和信息组织方式', 340, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai_debug_refactor_loops', 'design_patterns_refactoring', 'CO_LEARN', 'AI 重构建议要建立在设计模式和重构判断力之上', 350, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('multi_agent_workflows', 'collaborative_development', 'BRIDGE', '多 Agent 编排和多人协作流程都依赖清晰的任务拆分与交接', 360, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('model_routing_cost_control', 'service_governance_resilience', 'BRIDGE', '模型路由、超时和降级策略需要和服务治理能力一起设计', 370, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

CREATE TABLE skill_node_resources (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    resource_code VARCHAR(100) NOT NULL,
    node_code VARCHAR(100) NOT NULL,
    resource_type VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    source_label VARCHAR(100) NOT NULL,
    duration_label VARCHAR(50) NOT NULL,
    link_url VARCHAR(500) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_skill_node_resources_code UNIQUE (resource_code),
    CONSTRAINT fk_skill_node_resources_node FOREIGN KEY (node_code) REFERENCES skills(node_code)
);

INSERT INTO skill_node_resources(resource_code, node_code, resource_type, title, source_label, duration_label, link_url, sort_order, created_at, updated_at) VALUES
('ossu-cs-roadmap', 'programming_language_foundations', 'doc', 'OSSU Computer Science 路线图', 'OSSU', '长期参考', 'https://github.com/ossu/computer-science', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('cs50', 'programming_language_foundations', 'video', 'CS50 计算机科学导论', 'Harvard', '课程系列', 'https://cs50.harvard.edu/x/', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('spring-guides', 'backend_service_development', 'doc', 'Spring 官方 Guides', 'Spring', '长期参考', 'https://spring.io/guides', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('backend-checklist', 'backend_service_development', 'article', '服务分层与工程结构复盘清单', '静态推荐', '阅读 18m', 'https://12factor.net/', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('mysql-reference', 'relational_databases', 'doc', 'MySQL Reference Manual', 'MySQL', '长期参考', 'https://dev.mysql.com/doc/', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('sqlbolt', 'relational_databases', 'article', 'SQLBolt 交互式 SQL 练习', 'SQLBolt', '练习 30m', 'https://sqlbolt.com/', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ostep', 'operating_systems', 'doc', 'Operating Systems: Three Easy Pieces', 'OSTEP', '长期参考', 'https://pages.cs.wisc.edu/~remzi/OSTEP/', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('os-review', 'operating_systems', 'article', '进程、线程与内存管理复盘提纲', '静态推荐', '阅读 25m', 'https://pages.cs.wisc.edu/~remzi/OSTEP/', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('mdn-http-overview', 'computer_networks', 'doc', 'HTTP 与 Web 通信总览', 'MDN', '长期参考', 'https://developer.mozilla.org/en-US/docs/Web/HTTP/Overview', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('network-learning', 'computer_networks', 'article', '网络分层与请求路径入门', 'Cloudflare', '阅读 20m', 'https://www.cloudflare.com/learning/network-layer/what-is-a-computer-network/', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ml-crash-course', 'machine_learning', 'doc', 'Machine Learning Crash Course', 'Google', '长期参考', 'https://developers.google.com/machine-learning/crash-course', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ml-eval-notes', 'machine_learning', 'article', '特征工程与模型评估入门', '静态推荐', '阅读 22m', 'https://developers.google.com/machine-learning/crash-course/framing/check-your-understanding', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('nand2tetris', 'digital_logic', 'doc', 'Nand to Tetris 课程主页', 'Nand2Tetris', '长期参考', 'https://www.nand2tetris.org/', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('digital-logic-video', 'digital_logic', 'video', '数字逻辑与硬件系统导学', 'Bilibili', '3h 40m', 'https://www.bilibili.com', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('computer-history-timeline', 'cs_history', 'doc', 'Computer History Timeline', 'Computer History Museum', '长期参考', 'https://www.computerhistory.org/timeline/computers/', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('opensource-history', 'cs_history', 'article', '从技术史到开源协作的脉络速览', '静态推荐', '阅读 16m', 'https://opensource.com/resources/what-open-source', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('career-planning-guide', 'career_positioning', 'doc', '职业规划入门指南', 'Coursera', '长期参考', 'https://www.coursera.org/articles/career-planning', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('job-role-notes', 'career_positioning', 'article', '岗位方向拆解与自我评估清单', '静态推荐', '阅读 15m', 'https://www.indeed.com/career-advice/finding-a-job/career-planning-guide', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('resume-guide', 'resume_portfolio', 'doc', '简历写作与作品集整理指南', 'Indeed', '长期参考', 'https://www.indeed.com/career-advice/resumes-cover-letters', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('portfolio-checklist', 'resume_portfolio', 'article', '项目经历与作品集表达检查表', '静态推荐', '阅读 18m', 'https://www.indeed.com/career-advice/resumes-cover-letters/how-to-make-a-portfolio', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('interview-question-guide', 'interview_preparation', 'doc', '常见面试问题准备指南', 'Indeed', '长期参考', 'https://www.indeed.com/career-advice/interviewing/common-interview-questions-and-answers', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('behavioral-interview-notes', 'interview_preparation', 'article', 'STAR 法则与面试复盘模板', '静态推荐', '阅读 20m', 'https://www.themuse.com/advice/star-interview-method', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('workplace-communication', 'workplace_communication', 'doc', '职场沟通基础手册', 'MindTools', '长期参考', 'https://www.mindtools.com/CommSkll/CommunicationIntro.htm', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('daily-sync-template', 'workplace_communication', 'article', '日报、周报与同步进度的表达模板', '静态推荐', '阅读 12m', 'https://www.atlassian.com/blog/productivity/how-to-write-status-updates', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('stress-management-guide', 'stress_management', 'doc', '压力管理基础指南', 'Mind', '长期参考', 'https://www.mind.org.uk/information-support/types-of-mental-health-problems/stress/', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('stress-reset-checklist', 'stress_management', 'article', '高压阶段的恢复动作清单', '静态推荐', '阅读 10m', 'https://www.helpguide.org/mental-health/stress/stress-management', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('growth-mindset', 'growth_mindset', 'doc', 'Growth Mindset 入门材料', 'Mindset Works', '长期参考', 'https://www.mindsetworks.com/science/', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('feedback-reframe', 'growth_mindset', 'article', '如何把失败和反馈转化为下一轮行动', '静态推荐', '阅读 14m', 'https://fs.blog/carol-dweck-mindset/', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('computational-thinking-course', 'computational_thinking', 'doc', 'Computational Thinking for Problem Solving', 'University of Pennsylvania', '长期参考', 'https://www.coursera.org/learn/computational-thinking-problem-solving', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('problem-decomposition-notes', 'computational_thinking', 'article', '问题抽象与任务拆解练习清单', '静态推荐', '阅读 18m', 'https://developers.google.com/tech-writing', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('system-design-primer', 'software_architecture_design', 'doc', 'System Design Primer', 'GitHub', '长期参考', 'https://github.com/donnemartin/system-design-primer', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('architecture-checklist', 'software_architecture_design', 'article', '系统分层与模块边界检查表', '静态推荐', '阅读 20m', 'https://martinfowler.com/architecture/', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('etl-overview', 'data_warehouse_etl', 'doc', 'ETL 与数据仓库基础总览', 'IBM', '长期参考', 'https://www.ibm.com/topics/etl', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('analytics-metric-model', 'data_warehouse_etl', 'article', '指标口径与数仓建模入门', '静态推荐', '阅读 16m', 'https://www.getdbt.com/blog/what-is-dimensional-modeling', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('resilience-patterns', 'service_governance_resilience', 'doc', 'Resilience Patterns', 'Microsoft', '长期参考', 'https://learn.microsoft.com/en-us/azure/architecture/patterns/category/resiliency', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('governance-playbook', 'service_governance_resilience', 'article', '限流、熔断与重试策略速查', '静态推荐', '阅读 15m', 'https://aws.amazon.com/builders-library/timeouts-retries-and-backoff-with-jitter/', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('openai-prompt-guide', 'prompt_rag_agent', 'doc', 'Prompt Engineering Guide', 'Prompting Guide', '长期参考', 'https://www.promptingguide.ai/', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('rag-agent-notes', 'prompt_rag_agent', 'article', 'RAG 与 Agent 方案拆解清单', '静态推荐', '阅读 22m', 'https://www.pinecone.io/learn/retrieval-augmented-generation/', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('openai-prompt-engineering-guide', 'prompt_context_engineering', 'doc', 'Prompt Engineering Guide', 'OpenAI Docs', '长期参考', 'https://platform.openai.com/docs/guides/prompt-engineering', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('anthropic-prompt-overview', 'prompt_context_engineering', 'doc', 'Prompt Engineering Overview', 'Anthropic Docs', '长期参考', 'https://docs.anthropic.com/en/docs/build-with-claude/prompt-engineering/overview', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai-native-dev-checklist', 'ai_native_development', 'article', 'AI 原生开发分层检查表', '静态推荐', '阅读 18m', 'https://docs.github.com/en/copilot', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('copilot-docs-overview', 'ai_native_development', 'doc', 'GitHub Copilot 文档中心', 'GitHub', '长期参考', 'https://docs.github.com/en/copilot', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('aider-repo', 'vibe_coding_workflows', 'doc', 'Aider：终端中的 AI 结对编程', 'GitHub', '长期参考', 'https://github.com/Aider-AI/aider', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('vibe-coding-playbook', 'vibe_coding_workflows', 'article', '从想法到原型的 Vibe Coding 迭代清单', '静态推荐', '阅读 16m', 'https://docs.github.com/en/copilot', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('openhands-repo', 'ai_coding_agent_tools', 'doc', 'OpenHands 开源编码 Agent', 'GitHub', '长期参考', 'https://github.com/OpenHands/OpenHands', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('openai-tools-guide', 'ai_coding_agent_tools', 'doc', 'Tools 指南', 'OpenAI Docs', '长期参考', 'https://platform.openai.com/docs/guides/tools', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('promptfoo-docs', 'ai_code_review_guardrails', 'doc', 'Promptfoo 开源评测与护栏工具', 'GitHub', '长期参考', 'https://github.com/promptfoo/promptfoo', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai-review-checklist', 'ai_code_review_guardrails', 'article', 'AI 代码审查与回滚检查清单', '静态推荐', '阅读 14m', 'https://github.com/promptfoo/promptfoo', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('openai-evals-guide', 'ai_eval_observability', 'doc', 'Evals 指南', 'OpenAI Docs', '长期参考', 'https://platform.openai.com/docs/guides/evals', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai-observability-checklist', 'ai_eval_observability', 'article', 'AI 开发链路评测与留痕清单', '静态推荐', '阅读 15m', 'https://github.com/promptfoo/promptfoo', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai-programming-overview', 'ai_programming_engineering', 'doc', 'GitHub Copilot 文档中心', 'GitHub', '长期参考', 'https://docs.github.com/en/copilot', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('prompt-pattern-playbook', 'prompt_pattern_design', 'doc', 'Prompt Engineering Guide', 'OpenAI Docs', '长期参考', 'https://platform.openai.com/docs/guides/prompt-engineering', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('context-window-checklist', 'context_window_orchestration', 'article', '上下文筛选与窗口编排清单', '静态推荐', '阅读 15m', 'https://modelcontextprotocol.io/introduction', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('tool-schema-guide', 'tool_schema_design', 'doc', 'Tools 指南', 'OpenAI Docs', '长期参考', 'https://platform.openai.com/docs/guides/tools', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('aider-debug-loop', 'ai_debug_refactor_loops', 'doc', 'Aider：终端中的 AI 结对编程', 'GitHub', '长期参考', 'https://github.com/Aider-AI/aider', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('agent-systems-overview', 'ai_coding_agent_systems', 'doc', 'OpenHands 开源编码 Agent', 'GitHub', '长期参考', 'https://github.com/OpenHands/OpenHands', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('multi-agent-autogen', 'multi_agent_workflows', 'doc', 'AutoGen 多 Agent 框架', 'GitHub', '长期参考', 'https://github.com/microsoft/autogen', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai-governance-evals', 'ai_engineering_governance', 'doc', 'Evals 指南', 'OpenAI Docs', '长期参考', 'https://platform.openai.com/docs/guides/evals', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('model-routing-litellm', 'model_routing_cost_control', 'doc', 'LiteLLM 路由与成本控制', 'GitHub', '长期参考', 'https://github.com/BerriAI/litellm', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('star-storytelling', 'project_storytelling', 'doc', 'STAR 项目叙事模板', 'The Muse', '长期参考', 'https://www.themuse.com/advice/star-interview-method', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('project-presentation-checklist', 'project_storytelling', 'article', '项目成果表达与量化结果检查表', '静态推荐', '阅读 12m', 'https://www.indeed.com/career-advice/interviewing/project-based-interview-questions', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_ai_codebase_grounding_ref', 'ai_codebase_grounding', 'doc', 'Model Context Protocol Introduction', 'MCP', '长期参考', 'https://modelcontextprotocol.io/introduction', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_ai_data_science_ref', 'ai_data_science', 'doc', 'Machine Learning Crash Course', 'Google', '长期参考', 'https://developers.google.com/machine-learning/crash-course', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_ai_pair_programming_ref', 'ai_pair_programming', 'doc', 'GitHub Copilot Docs', 'GitHub Docs', '长期参考', 'https://docs.github.com/en/copilot', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_algorithms_analysis_ref', 'algorithms_analysis', 'doc', 'Algorithms, 4th Edition', 'Princeton', '长期参考', 'https://algs4.cs.princeton.edu/home/', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_analytics_data_visualization_ref', 'analytics_data_visualization', 'doc', 'Plotly Python Getting Started', 'Plotly', '长期参考', 'https://plotly.com/python/getting-started/', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_api_contract_design_ref', 'api_contract_design', 'doc', 'Best practices for RESTful API design', 'Microsoft Learn', '长期参考', 'https://learn.microsoft.com/en-us/azure/architecture/best-practices/api-design', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_applied_cryptography_ref', 'applied_cryptography', 'article', 'What is encryption?', 'Cloudflare', '阅读 10m', 'https://www.cloudflare.com/learning/ssl/what-is-encryption/', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_automated_testing_ref', 'automated_testing', 'article', 'The Practical Test Pyramid', 'Martin Fowler', '阅读 18m', 'https://martinfowler.com/articles/practical-test-pyramid.html', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_big_data_foundations_ref', 'big_data_foundations', 'article', 'What is big data?', 'IBM', '阅读 10m', 'https://www.ibm.com/think/topics/big-data', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_browser_runtime_mechanics_ref', 'browser_runtime_mechanics', 'doc', 'JavaScript Event Loop', 'MDN', '长期参考', 'https://developer.mozilla.org/en-US/docs/Web/JavaScript/Event_loop', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_burnout_prevention_ref', 'burnout_prevention', 'article', 'Burnout prevention and recovery', 'HelpGuide', '阅读 10m', 'https://www.helpguide.org/mental-health/stress/burnout-prevention-and-recovery', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_c_programming_basics_ref', 'c_programming_basics', 'doc', 'C language reference', 'cppreference', '长期参考', 'https://en.cppreference.com/w/c/language.html', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_career_employment_readiness_ref', 'career_employment_readiness', 'article', 'What Is Career Development?', 'Coursera', '阅读 12m', 'https://www.coursera.org/articles/career-development', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_ci_cd_pipeline_ref', 'ci_cd_pipeline', 'doc', 'Understanding GitHub Actions', 'GitHub Docs', '长期参考', 'https://docs.github.com/en/actions/about-github-actions/understanding-github-actions', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_cloud_native_basics_ref', 'cloud_native_basics', 'doc', 'CNCF Cloud Native Glossary', 'CNCF', '长期参考', 'https://glossary.cncf.io/', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_collaborative_development_ref', 'collaborative_development', 'doc', 'Git Tutorials', 'Atlassian', '长期参考', 'https://www.atlassian.com/git/tutorials', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_compiler_principles_ref', 'compiler_principles', 'doc', 'My First Language Frontend with LLVM', 'LLVM', '长期参考', 'https://llvm.org/docs/tutorial/MyFirstLanguageFrontend/index.html', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_computer_organization_ref', 'computer_organization', 'doc', 'Nand to Tetris', 'Nand2Tetris', '长期参考', 'https://www.nand2tetris.org/', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_concurrent_programming_basics_ref', 'concurrent_programming_basics', 'doc', 'Concurrency', 'Oracle', '长期参考', 'https://docs.oracle.com/javase/tutorial/essential/concurrency/index.html', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_container_kubernetes_ref', 'container_kubernetes', 'doc', 'Kubernetes Basics', 'Kubernetes', '长期参考', 'https://kubernetes.io/docs/tutorials/kubernetes-basics/', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_data_cleaning_feature_engineering_ref', 'data_cleaning_feature_engineering', 'doc', 'Preprocessing data', 'scikit-learn', '长期参考', 'https://scikit-learn.org/stable/modules/preprocessing.html', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_data_database_systems_ref', 'data_database_systems', 'article', 'Database management', 'IBM', '阅读 12m', 'https://www.ibm.com/think/topics/database-management', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_data_modeling_ref', 'data_modeling', 'article', 'What is data modeling?', 'IBM', '阅读 10m', 'https://www.ibm.com/think/topics/data-modeling', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_data_structures_ref', 'data_structures', 'tool', 'VisuAlgo 数据结构可视化', 'VisuAlgo', '互动参考', 'https://visualgo.net/en', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_deep_learning_ref', 'deep_learning', 'doc', 'Learn the Basics', 'PyTorch', '长期参考', 'https://pytorch.org/tutorials/beginner/basics/intro.html', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_design_patterns_refactoring_ref', 'design_patterns_refactoring', 'doc', 'Design Patterns', 'Refactoring.Guru', '长期参考', 'https://refactoring.guru/design-patterns', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_devops_delivery_ref', 'devops_delivery', 'doc', 'What is DevOps?', 'Atlassian', '长期参考', 'https://www.atlassian.com/devops', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_discrete_mathematics_ref', 'discrete_mathematics', 'doc', 'Mathematics for Computer Science', 'MIT OCW', '长期参考', 'https://ocw.mit.edu/courses/6-042j-mathematics-for-computer-science-fall-2010/', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_distributed_systems_ref', 'distributed_systems', 'doc', 'MIT 6.824 Distributed Systems', 'MIT', '长期参考', 'https://pdos.csail.mit.edu/6.824/', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_domain_modeling_design_ref', 'domain_modeling_design', 'article', 'Domain Model', 'Martin Fowler', '阅读 8m', 'https://martinfowler.com/eaaCatalog/domainModel.html', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_embedded_systems_ref', 'embedded_systems', 'doc', 'Zephyr Project Introduction', 'Zephyr', '长期参考', 'https://docs.zephyrproject.org/latest/introduction/index.html', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_emotion_regulation_ref', 'emotion_regulation', 'article', 'Emotional Intelligence Toolkit', 'HelpGuide', '阅读 12m', 'https://www.helpguide.org/mental-health/wellbeing/emotional-intelligence-toolkit', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_end_to_end_regression_ref', 'end_to_end_regression', 'doc', 'Playwright Introduction', 'Playwright', '长期参考', 'https://playwright.dev/docs/intro', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_engineering_code_conventions_ref', 'engineering_code_conventions', 'doc', 'Google Java Style Guide', 'Google', '长期参考', 'https://google.github.io/styleguide/javaguide.html', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_engineering_ethics_ref', 'engineering_ethics', 'doc', 'ACM Code of Ethics', 'ACM', '长期参考', 'https://www.acm.org/code-of-ethics', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_file_system_io_ref', 'file_system_io', 'doc', 'File System Intro', 'OSTEP', '长期参考', 'https://pages.cs.wisc.edu/~remzi/OSTEP/file-intro.pdf', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_frontend_component_architecture_ref', 'frontend_component_architecture', 'doc', 'Thinking in React', 'React Docs', '长期参考', 'https://react.dev/learn/thinking-in-react', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_frontend_engineering_ref', 'frontend_engineering', 'doc', 'MDN Learn Web Development', 'MDN', '长期参考', 'https://developer.mozilla.org/en-US/docs/Learn_web_development/Core', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_graph_problem_modeling_ref', 'graph_problem_modeling', 'doc', 'Neo4j Graph Modeling', 'Neo4j', '长期参考', 'https://neo4j.com/books/neo4j-graph-modeling/', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_habit_energy_management_ref', 'habit_energy_management', 'article', 'Habits Guide: How to Build Good Habits and Break Bad Ones', 'James Clear', '阅读 12m', 'https://jamesclear.com/habits', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_hardware_embedded_ref', 'hardware_embedded', 'doc', 'Arduino Documentation', 'Arduino', '长期参考', 'https://docs.arduino.cc/', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_internship_goal_setting_ref', 'internship_goal_setting', 'article', 'How to Write SMART Goals', 'Atlassian', '阅读 8m', 'https://www.atlassian.com/blog/productivity/how-to-write-smart-goals', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_internship_workplace_adaptation_ref', 'internship_workplace_adaptation', 'article', 'Internship tips', 'Indeed', '阅读 10m', 'https://www.indeed.com/career-advice/starting-new-job/internship-tips', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_iot_system_design_ref', 'iot_system_design', 'article', 'What is the Internet of Things?', 'IBM', '阅读 10m', 'https://www.ibm.com/think/topics/internet-of-things', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_java_collections_io_ref', 'java_collections_io', 'doc', 'The Collections Trail', 'Oracle', '长期参考', 'https://docs.oracle.com/javase/tutorial/collections/index.html', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_java_programming_ref', 'java_programming', 'doc', 'Java Language Basics', 'Oracle', '长期参考', 'https://docs.oracle.com/javase/tutorial/java/nutsandbolts/index.html', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_job_market_research_ref', 'job_market_research', 'article', 'How to Read a Job Description', 'Indeed', '阅读 10m', 'https://www.indeed.com/career-advice/finding-a-job/how-to-read-a-job-description', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_linux_operations_ref', 'linux_operations', 'doc', 'Linux Journey', 'Linux Journey', '长期参考', 'https://linuxjourney.com/', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_llm_applications_ref', 'llm_applications', 'doc', 'The Hugging Face LLM Course', 'Hugging Face', '长期参考', 'https://huggingface.co/learn/llm-course/chapter1/1', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_logic_and_proofs_ref', 'logic_and_proofs', 'doc', 'Mathematics for Computer Science Readings', 'MIT OCW', '长期参考', 'https://ocw.mit.edu/courses/6-042j-mathematics-for-computer-science-fall-2010/pages/readings/', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_memory_management_ref', 'memory_management', 'doc', 'Virtual Memory Intro', 'OSTEP', '长期参考', 'https://pages.cs.wisc.edu/~remzi/OSTEP/vm-intro.pdf', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_memory_model_pointers_ref', 'memory_model_pointers', 'doc', 'Pointers in C', 'cppreference', '长期参考', 'https://en.cppreference.com/w/c/language/pointer.html', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_microcomputer_principles_ref', 'microcomputer_principles', 'doc', 'Computer Organization and Architecture Tutorials', 'GeeksforGeeks', '长期参考', 'https://www.geeksforgeeks.org/computer-organization-architecture/computer-organization-and-architecture-tutorials/', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_model_evaluation_validation_ref', 'model_evaluation_validation', 'doc', 'Model selection and evaluation', 'scikit-learn', '长期参考', 'https://scikit-learn.org/stable/model_selection.html', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_network_protocols_ref', 'network_protocols', 'doc', 'HTTP Overview', 'MDN', '长期参考', 'https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Overview', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_network_security_ref', 'network_security', 'article', 'What is network security?', 'Cloudflare', '阅读 10m', 'https://www.cloudflare.com/learning/security/what-is-network-security/', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_networking_personal_brand_ref', 'networking_personal_brand', 'article', 'What Is Personal Branding?', 'Forbes Books', '阅读 8m', 'https://books.forbes.com/blog/what-is-personal-branding/', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_neural_network_training_ref', 'neural_network_training', 'doc', 'Training Neural Networks', 'PyTorch', '长期参考', 'https://pytorch.org/tutorials/beginner/introyt/trainingyt.html', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_object_oriented_modeling_ref', 'object_oriented_modeling', 'article', 'What is object-oriented programming?', 'IBM', '阅读 12m', 'https://www.ibm.com/think/topics/object-oriented-programming', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_observability_incident_response_ref', 'observability_incident_response', 'doc', 'Site Reliability Engineering', 'Google SRE', '长期参考', 'https://sre.google/sre-book/table-of-contents/', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_open_source_culture_ref', 'open_source_culture', 'article', 'How to Contribute to Open Source', 'Open Source Guides', '阅读 15m', 'https://opensource.guide/how-to-contribute/', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_probability_inference_ref', 'probability_inference', 'doc', 'Seeing Theory: Basic Probability', 'Brown University', '长期参考', 'https://seeing-theory.brown.edu/basic-probability/index.html', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_problem_solving_patterns_ref', 'problem_solving_patterns', 'doc', 'cp-algorithms', 'cp-algorithms', '长期参考', 'https://cp-algorithms.com/', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_process_thread_models_ref', 'process_thread_models', 'doc', 'Threads Intro', 'OSTEP', '长期参考', 'https://pages.cs.wisc.edu/~remzi/OSTEP/threads-intro.pdf', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_python_data_tools_ref', 'python_data_tools', 'doc', 'pandas Getting Started', 'pandas', '长期参考', 'https://pandas.pydata.org/docs/getting_started/index.html', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_query_optimization_ref', 'query_optimization', 'doc', 'EXPLAIN Output Format', 'MySQL', '长期参考', 'https://dev.mysql.com/doc/refman/8.0/en/explain.html', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_recommendation_systems_ref', 'recommendation_systems', 'doc', 'Microsoft Recommenders', 'GitHub', '长期参考', 'https://github.com/recommenders-team/recommenders', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_requirement_analysis_ref', 'requirement_analysis', 'article', 'Requirements management guide', 'Atlassian', '阅读 10m', 'https://www.atlassian.com/agile/project-management/requirements', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_secure_engineering_ref', 'secure_engineering', 'doc', 'OWASP Cheat Sheet Series', 'OWASP', '长期参考', 'https://cheatsheetseries.owasp.org/', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_self_reflection_planning_ref', 'self_reflection_planning', 'article', 'How to Build a Growth Plan', 'Coursera', '阅读 12m', 'https://www.coursera.org/articles/build-a-growth-plan', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_service_layer_design_ref', 'service_layer_design', 'article', 'Service Layer', 'Martin Fowler', '阅读 10m', 'https://martinfowler.com/eaaCatalog/serviceLayer.html', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_shell_tooling_automation_ref', 'shell_tooling_automation', 'doc', 'Bash Reference Manual', 'GNU', '长期参考', 'https://www.gnu.org/software/bash/manual/bash.html', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_software_engineering_delivery_ref', 'software_engineering_delivery', 'doc', 'What is software development?', 'Atlassian', '长期参考', 'https://www.atlassian.com/software-development', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_sql_query_writing_ref', 'sql_query_writing', 'tool', 'SQLBolt', 'SQLBolt', '练习 30m', 'https://sqlbolt.com/', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_state_data_flow_ref', 'state_data_flow', 'doc', 'Sharing State Between Components', 'React Docs', '长期参考', 'https://react.dev/learn/sharing-state-between-components', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_statistics_foundation_ref', 'statistics_foundation', 'doc', 'Seeing Theory', 'Brown University', '长期参考', 'https://seeing-theory.brown.edu/', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_systems_infrastructure_ref', 'systems_infrastructure', 'doc', 'OSTEP Introduction', 'OSTEP', '长期参考', 'https://pages.cs.wisc.edu/~remzi/OSTEP/intro.pdf', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_task_execution_followup_ref', 'task_execution_followup', 'article', 'Write effective updates to projects, goals, and topics', 'Atlassian Support', '阅读 8m', 'https://support.atlassian.com/platform-experiences/docs/write-effective-updates-to-projects-goals-and-topics/', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_teamwork_collaboration_ref', 'teamwork_collaboration', 'article', 'Teamwork examples', 'Atlassian', '阅读 10m', 'https://www.atlassian.com/blog/teamwork/teamwork-examples', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_technology_society_governance_ref', 'technology_society_governance', 'article', 'OECD AI Principles', 'OECD', '阅读 12m', 'https://oecd.ai/en/ai-principles', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_theory_history_ref', 'theory_history', 'doc', 'Computer History Timeline', 'Computer History Museum', '长期参考', 'https://www.computerhistory.org/timeline/computers/', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_transaction_consistency_ref', 'transaction_consistency', 'doc', 'InnoDB Transaction Model', 'MySQL', '长期参考', 'https://dev.mysql.com/doc/refman/8.0/en/innodb-transaction-model.html', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_tree_linear_models_ref', 'tree_linear_models', 'doc', 'Supervised learning', 'scikit-learn', '长期参考', 'https://scikit-learn.org/stable/supervised_learning.html', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_unit_integration_testing_ref', 'unit_integration_testing', 'doc', 'JUnit 5 User Guide', 'JUnit', '长期参考', 'https://junit.org/junit5/docs/current/user-guide/', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_web_security_ref', 'web_security', 'doc', 'OWASP Top 10', 'OWASP', '长期参考', 'https://owasp.org/www-project-top-ten/', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_wellbeing_self_management_ref', 'wellbeing_self_management', 'article', 'Mental health at work', 'WHO', '阅读 10m', 'https://www.who.int/news-room/fact-sheets/detail/mental-health-at-work', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('auto_workplace_professionalism_ref', 'workplace_professionalism', 'article', 'Guide to professionalism in the workplace', 'Indeed', '阅读 10m', 'https://www.indeed.com/career-advice/career-development/the-ultimate-guide-to-professionalism', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

UPDATE skills
SET label = 'Java 语言与工程实践',
    description = '掌握面向对象语法、标准库和工程开发里最常见的 Java 基础能力。'
WHERE node_code = 'java_programming';

UPDATE skills
SET parent_code = 'software_engineering_delivery'
WHERE node_code = 'software_architecture_design';

UPDATE skills
SET label = '系统与基础设施',
    description = '从组成原理、操作系统、分布式与云原生基础设施构建系统视角。'
WHERE node_code = 'systems_infrastructure';

UPDATE skills
SET label = '网络与安全',
    description = '聚焦网络通信、协议分层与安全防护，建立面向真实互联网系统的基础认知。'
WHERE node_code = 'network_security';

UPDATE skills
SET parent_code = 'systems_infrastructure'
WHERE node_code = 'distributed_systems';

UPDATE skills
SET parent_code = 'ai_coding_agent_systems'
WHERE node_code = 'multi_agent_workflows';

UPDATE skills
SET parent_code = 'ai_engineering_governance'
WHERE node_code IN ('ai_eval_observability', 'model_routing_cost_control');

UPDATE skills
SET parent_code = 'theory_history'
WHERE node_code = 'engineering_ethics';

UPDATE skills
SET label = '职业发展与求职准备',
    description = '围绕岗位理解、简历表达与求职策略，建立从校园到就业市场的过渡能力。'
WHERE node_code = 'career_employment_readiness';

UPDATE skills
SET parent_code = 'programming_language_foundations'
WHERE node_code IN ('internship_workplace_adaptation', 'wellbeing_self_management');

INSERT INTO skill_relations(source_node_code, target_node_code, relation_type, label, sort_order, created_at, updated_at) VALUES
('requirement_analysis', 'software_architecture_design', 'ADVANCE_TO', '需求澄清后通常会进入架构与模块设计', 380, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('engineering_code_conventions', 'collaborative_development', 'CO_LEARN', '工程规范只有放进协作流程里才会真正稳定', 390, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('unit_integration_testing', 'ci_cd_pipeline', 'ADVANCE_TO', '测试能力最终会收进持续集成流水线', 400, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('end_to_end_regression', 'observability_incident_response', 'BRIDGE', '关键链路回归和线上故障排查需要共用验证思路', 410, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('browser_runtime_mechanics', 'web_security', 'CO_LEARN', '浏览器运行机制会直接影响前端安全理解', 420, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('process_thread_models', 'concurrent_programming_basics', 'ADVANCE_TO', '操作系统线程模型会回流到并发编程实践', 430, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('file_system_io', 'linux_operations', 'CO_LEARN', '文件与 I/O 理解会直接提升 Linux 排障能力', 440, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('container_kubernetes', 'observability_incident_response', 'BRIDGE', '容器编排上线后必须配套观测与故障响应', 450, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('data_cleaning_feature_engineering', 'model_evaluation_validation', 'ADVANCE_TO', '特征工程完成后自然进入模型评估与验证', 460, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('prompt_pattern_design', 'prompt_rag_agent', 'BRIDGE', '提示模式沉淀后会汇入完整的 RAG 与 Agent 设计', 470, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('teamwork_collaboration', 'collaborative_development', 'BRIDGE', '团队协作能力最终会落到真实工程协作流程', 480, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('burnout_prevention', 'self_reflection_planning', 'BRIDGE', '倦怠预防最终要回到节奏复盘与阶段规划', 490, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('workplace_professionalism', 'collaborative_development', 'CO_LEARN', '职业化表达与交付责任感会直接影响工程协作质量', 500, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

UPDATE skills
SET parent_code = 'software_architecture_design'
WHERE node_code = 'service_layer_design';

UPDATE skills
SET parent_code = 'hardware_embedded'
WHERE node_code = 'embedded_systems';

UPDATE skills
SET parent_code = 'distributed_systems'
WHERE node_code = 'container_kubernetes';

UPDATE skills
SET parent_code = 'career_positioning'
WHERE node_code = 'networking_personal_brand';

UPDATE skills
SET parent_code = 'stress_management'
WHERE node_code = 'burnout_prevention';

UPDATE skills
SET parent_code = 'workplace_communication'
WHERE node_code = 'workplace_professionalism';

UPDATE skills
SET parent_code = 'prompt_context_engineering'
WHERE node_code = 'tool_schema_design';

UPDATE skills
SET parent_code = 'ai_coding_agent_systems'
WHERE node_code = 'ai_coding_agent_tools';

UPDATE skills
SET parent_code = 'network_security'
WHERE node_code = 'web_security';

UPDATE skills
SET parent_code = 'web_security'
WHERE node_code = 'secure_engineering';

INSERT INTO skill_relations(source_node_code, target_node_code, relation_type, label, sort_order, created_at, updated_at) VALUES
('memory_model_pointers', 'memory_management', 'ADVANCE_TO', '理解指针与内存布局后，会更容易掌握系统中的内存管理', 510, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('graph_problem_modeling', 'data_modeling', 'BRIDGE', '图结构抽象能力会迁移到复杂业务与数据关系建模', 520, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('sql_query_writing', 'query_optimization', 'ADVANCE_TO', '能写出正确 SQL 后，下一步就是关注执行计划与优化', 530, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('big_data_foundations', 'distributed_systems', 'BRIDGE', '大数据系统最终会落到分布式存储与计算基础', 540, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('shell_tooling_automation', 'ci_cd_pipeline', 'ADVANCE_TO', '脚本自动化能力最终会接到流水线编排', 550, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('digital_logic', 'microcomputer_principles', 'ADVANCE_TO', '数字逻辑理解会继续进入微机结构与接口系统', 560, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('iot_system_design', 'computer_networks', 'CO_LEARN', '物联网系统设计离不开设备联网与协议理解', 570, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('probability_inference', 'machine_learning', 'ADVANCE_TO', '概率推断是理解机器学习建模假设的入口', 580, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('tree_linear_models', 'recommendation_systems', 'BRIDGE', '经典监督学习模型常会落到推荐排序与召回场景', 590, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('neural_network_training', 'llm_applications', 'ADVANCE_TO', '掌握神经网络训练流程后，更容易理解大模型应用边界', 600, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('logic_and_proofs', 'compiler_principles', 'ADVANCE_TO', '形式化推理能力会继续支撑编译与程序语义理解', 610, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('technology_society_governance', 'ai_engineering_governance', 'BRIDGE', '技术治理视角会直接回流到 AI 工程治理实践', 620, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('habit_energy_management', 'burnout_prevention', 'CO_LEARN', '稳定作息与精力管理能直接帮助倦怠预防', 630, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('networking_personal_brand', 'project_storytelling', 'CO_LEARN', '个人品牌表达最终仍要靠项目叙事来支撑', 640, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('backend_service_development', 'service_layer_design', 'ADVANCE_TO', '后端开发深入后会进入服务分层与边界设计', 650, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('cloud_native_basics', 'container_kubernetes', 'ADVANCE_TO', '理解云原生抽象后，下一步通常就是容器编排实践', 660, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('context_window_orchestration', 'tool_schema_design', 'CO_LEARN', '上下文编排与工具契约设计需要一起打磨', 670, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ai_codebase_grounding', 'ai_coding_agent_tools', 'ADVANCE_TO', '先做好代码库对齐，工具调用才真正稳定可控', 680, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('job_market_research', 'networking_personal_brand', 'BRIDGE', '岗位研究最终要延伸到行业连接与个人曝光', 690, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('task_execution_followup', 'workplace_professionalism', 'BRIDGE', '任务推进习惯最终会沉淀为职场专业度', 700, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('emotion_regulation', 'burnout_prevention', 'CO_LEARN', '情绪调节能力会直接影响倦怠识别与恢复', 710, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('network_protocols', 'web_security', 'CO_LEARN', '协议语义理解会直接影响 Web 安全判断', 720, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('applied_cryptography', 'secure_engineering', 'CO_LEARN', '密码学原理只有落到工程实践中才真正有价值', 730, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('microcomputer_principles', 'embedded_systems', 'ADVANCE_TO', '理解微机与接口后，更容易进入嵌入式系统设计', 740, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

DELETE FROM skill_relations
WHERE (source_node_code = 'requirement_analysis' AND target_node_code = 'software_architecture_design' AND relation_type = 'ADVANCE_TO')
   OR (source_node_code = 'engineering_code_conventions' AND target_node_code = 'collaborative_development' AND relation_type = 'CO_LEARN')
   OR (source_node_code = 'unit_integration_testing' AND target_node_code = 'ci_cd_pipeline' AND relation_type = 'ADVANCE_TO')
   OR (source_node_code = 'end_to_end_regression' AND target_node_code = 'observability_incident_response' AND relation_type = 'BRIDGE')
   OR (source_node_code = 'browser_runtime_mechanics' AND target_node_code = 'web_security' AND relation_type = 'CO_LEARN')
   OR (source_node_code = 'process_thread_models' AND target_node_code = 'concurrent_programming_basics' AND relation_type = 'ADVANCE_TO')
   OR (source_node_code = 'file_system_io' AND target_node_code = 'linux_operations' AND relation_type = 'CO_LEARN')
   OR (source_node_code = 'container_kubernetes' AND target_node_code = 'observability_incident_response' AND relation_type = 'BRIDGE')
   OR (source_node_code = 'data_cleaning_feature_engineering' AND target_node_code = 'model_evaluation_validation' AND relation_type = 'ADVANCE_TO')
   OR (source_node_code = 'prompt_pattern_design' AND target_node_code = 'prompt_rag_agent' AND relation_type = 'BRIDGE')
   OR (source_node_code = 'teamwork_collaboration' AND target_node_code = 'collaborative_development' AND relation_type = 'BRIDGE')
   OR (source_node_code = 'burnout_prevention' AND target_node_code = 'self_reflection_planning' AND relation_type = 'BRIDGE')
   OR (source_node_code = 'workplace_professionalism' AND target_node_code = 'collaborative_development' AND relation_type = 'CO_LEARN')
   OR (source_node_code = 'memory_model_pointers' AND target_node_code = 'memory_management' AND relation_type = 'ADVANCE_TO')
   OR (source_node_code = 'graph_problem_modeling' AND target_node_code = 'data_modeling' AND relation_type = 'BRIDGE')
   OR (source_node_code = 'sql_query_writing' AND target_node_code = 'query_optimization' AND relation_type = 'ADVANCE_TO')
   OR (source_node_code = 'big_data_foundations' AND target_node_code = 'distributed_systems' AND relation_type = 'BRIDGE')
   OR (source_node_code = 'shell_tooling_automation' AND target_node_code = 'ci_cd_pipeline' AND relation_type = 'ADVANCE_TO')
   OR (source_node_code = 'digital_logic' AND target_node_code = 'microcomputer_principles' AND relation_type = 'ADVANCE_TO')
   OR (source_node_code = 'iot_system_design' AND target_node_code = 'computer_networks' AND relation_type = 'CO_LEARN')
   OR (source_node_code = 'probability_inference' AND target_node_code = 'machine_learning' AND relation_type = 'ADVANCE_TO')
   OR (source_node_code = 'tree_linear_models' AND target_node_code = 'recommendation_systems' AND relation_type = 'BRIDGE')
   OR (source_node_code = 'neural_network_training' AND target_node_code = 'llm_applications' AND relation_type = 'ADVANCE_TO')
   OR (source_node_code = 'logic_and_proofs' AND target_node_code = 'compiler_principles' AND relation_type = 'ADVANCE_TO')
   OR (source_node_code = 'technology_society_governance' AND target_node_code = 'ai_engineering_governance' AND relation_type = 'BRIDGE')
   OR (source_node_code = 'habit_energy_management' AND target_node_code = 'burnout_prevention' AND relation_type = 'CO_LEARN')
   OR (source_node_code = 'networking_personal_brand' AND target_node_code = 'project_storytelling' AND relation_type = 'CO_LEARN')
   OR (source_node_code = 'backend_service_development' AND target_node_code = 'service_layer_design' AND relation_type = 'ADVANCE_TO')
   OR (source_node_code = 'cloud_native_basics' AND target_node_code = 'container_kubernetes' AND relation_type = 'ADVANCE_TO')
   OR (source_node_code = 'context_window_orchestration' AND target_node_code = 'tool_schema_design' AND relation_type = 'CO_LEARN')
   OR (source_node_code = 'ai_codebase_grounding' AND target_node_code = 'ai_coding_agent_tools' AND relation_type = 'ADVANCE_TO')
   OR (source_node_code = 'job_market_research' AND target_node_code = 'networking_personal_brand' AND relation_type = 'BRIDGE')
   OR (source_node_code = 'task_execution_followup' AND target_node_code = 'workplace_professionalism' AND relation_type = 'BRIDGE')
   OR (source_node_code = 'emotion_regulation' AND target_node_code = 'burnout_prevention' AND relation_type = 'CO_LEARN')
   OR (source_node_code = 'network_protocols' AND target_node_code = 'web_security' AND relation_type = 'CO_LEARN')
   OR (source_node_code = 'applied_cryptography' AND target_node_code = 'secure_engineering' AND relation_type = 'CO_LEARN')
   OR (source_node_code = 'microcomputer_principles' AND target_node_code = 'embedded_systems' AND relation_type = 'ADVANCE_TO');

UPDATE skills
SET parent_code = 'requirement_analysis'
WHERE node_code = 'software_architecture_design';

UPDATE skills
SET parent_code = 'network_security'
WHERE node_code = 'distributed_systems';

UPDATE skills
SET parent_code = 'ai_coding_agent_tools'
WHERE node_code = 'multi_agent_workflows';

UPDATE skills
SET parent_code = 'ai_code_review_guardrails'
WHERE node_code = 'ai_eval_observability';

UPDATE skills
SET parent_code = 'ai_eval_observability'
WHERE node_code = 'model_routing_cost_control';

UPDATE skills
SET parent_code = 'open_source_culture'
WHERE node_code = 'engineering_ethics';

UPDATE skills
SET parent_code = 'career_employment_readiness'
WHERE node_code IN ('internship_workplace_adaptation', 'wellbeing_self_management');

UPDATE skills
SET parent_code = 'backend_service_development'
WHERE node_code = 'service_layer_design';

UPDATE skills
SET parent_code = 'microcomputer_principles'
WHERE node_code = 'embedded_systems';

UPDATE skills
SET parent_code = 'cloud_native_basics'
WHERE node_code = 'container_kubernetes';

UPDATE skills
SET parent_code = 'job_market_research'
WHERE node_code = 'networking_personal_brand';

UPDATE skills
SET parent_code = 'emotion_regulation'
WHERE node_code = 'burnout_prevention';

UPDATE skills
SET parent_code = 'task_execution_followup'
WHERE node_code = 'workplace_professionalism';

UPDATE skills
SET parent_code = 'context_window_orchestration'
WHERE node_code = 'tool_schema_design';

UPDATE skills
SET parent_code = 'ai_codebase_grounding'
WHERE node_code = 'ai_coding_agent_tools';

UPDATE skills
SET parent_code = 'network_protocols'
WHERE node_code = 'web_security';

UPDATE skills
SET parent_code = 'applied_cryptography'
WHERE node_code = 'secure_engineering';

CREATE TABLE daily_tasks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_code VARCHAR(50) NOT NULL,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    points INT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_daily_tasks_task_code UNIQUE (task_code)
);

CREATE TABLE checkins (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_user_id BIGINT NOT NULL,
    checkin_date DATE NOT NULL,
    streak_count INT NOT NULL,
    points_earned INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_checkins_user_date UNIQUE (student_user_id, checkin_date),
    CONSTRAINT fk_checkins_user FOREIGN KEY (student_user_id) REFERENCES users(id)
);

CREATE TABLE points_ledger (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_user_id BIGINT NOT NULL,
    delta_points INT NOT NULL,
    reason_code VARCHAR(50) NOT NULL,
    balance_after INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_points_ledger_user FOREIGN KEY (student_user_id) REFERENCES users(id)
);

CREATE INDEX idx_points_ledger_student_time ON points_ledger(student_user_id, created_at);

CREATE TABLE growth_checkin_reward_rules (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    reward_code VARCHAR(50) NOT NULL,
    streak_days INT NOT NULL,
    bonus_points INT NOT NULL,
    reward_title VARCHAR(100) NOT NULL,
    reward_description VARCHAR(255),
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_growth_checkin_reward_rules_code UNIQUE (reward_code),
    CONSTRAINT uq_growth_checkin_reward_rules_streak UNIQUE (streak_days)
);

CREATE TABLE consult_orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    student_user_id BIGINT NOT NULL,
    mentor_user_id BIGINT NOT NULL,
    scene_code VARCHAR(60),
    source_page VARCHAR(80),
    amount_fen INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    question_text CLOB,
    question_payload_json CLOB,
    problem_summary CLOB,
    core_questions_json CLOB,
    expected_outcomes_json CLOB,
    selected_material_types VARCHAR(255),
    prep_sheet_snapshot_json CLOB,
    service_package_snapshot_json CLOB,
    appointment_start_at TIMESTAMP,
    appointment_end_at TIMESTAMP,
    paid_at TIMESTAMP,
    closed_at TIMESTAMP,
    review_rating TINYINT,
    review_comment CLOB,
    review_created_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_consult_orders_order_no UNIQUE (order_no),
    CONSTRAINT fk_consult_orders_student FOREIGN KEY (student_user_id) REFERENCES users(id),
    CONSTRAINT fk_consult_orders_mentor FOREIGN KEY (mentor_user_id) REFERENCES users(id)
);

CREATE INDEX idx_consult_orders_student_time ON consult_orders(student_user_id, created_at);
CREATE INDEX idx_consult_orders_mentor_time ON consult_orders(mentor_user_id, created_at);
CREATE INDEX idx_consult_orders_mentor_review_time ON consult_orders(mentor_user_id, review_created_at);

CREATE TABLE consult_messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    order_id BIGINT,
    sender_user_id BIGINT NOT NULL,
    sender_role VARCHAR(20) NOT NULL,
    message_text CLOB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_consult_messages_sender FOREIGN KEY (sender_user_id) REFERENCES users(id),
    CONSTRAINT fk_consult_messages_order FOREIGN KEY (order_id) REFERENCES consult_orders(id)
);

CREATE INDEX idx_consult_messages_order_time ON consult_messages(order_no, created_at);
CREATE INDEX idx_consult_messages_order_id_time ON consult_messages(order_id, created_at);

CREATE TABLE consult_order_attachments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    order_id BIGINT,
    uploaded_by_user_id BIGINT NOT NULL,
    attachment_type VARCHAR(40) NOT NULL,
    slot_code VARCHAR(60) NOT NULL,
    source_stage VARCHAR(30) NOT NULL DEFAULT 'ORDER_CREATE',
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL,
    storage_bucket VARCHAR(120) NOT NULL,
    object_key VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    lifecycle_status VARCHAR(20) NOT NULL DEFAULT 'CURRENT',
    replaced_attachment_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_consult_order_attachments_uploader FOREIGN KEY (uploaded_by_user_id) REFERENCES users(id),
    CONSTRAINT fk_consult_order_attachments_order FOREIGN KEY (order_id) REFERENCES consult_orders(id)
);

CREATE INDEX idx_consult_order_attachments_order_time ON consult_order_attachments(order_no, created_at);
CREATE INDEX idx_consult_order_attachments_order_lifecycle ON consult_order_attachments(order_no, lifecycle_status);
CREATE INDEX idx_consult_order_attachments_slot ON consult_order_attachments(order_no, slot_code, lifecycle_status);
CREATE INDEX idx_consult_order_attachments_order_id_time ON consult_order_attachments(order_id, created_at);
CREATE INDEX idx_consult_order_attachments_order_id_lifecycle ON consult_order_attachments(order_id, lifecycle_status);
CREATE INDEX idx_consult_order_attachments_order_id_slot ON consult_order_attachments(order_id, slot_code, lifecycle_status);

CREATE TABLE payment_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    order_id BIGINT,
    channel VARCHAR(20) NOT NULL,
    mode VARCHAR(20) NOT NULL,
    provider_trade_no VARCHAR(128),
    amount_fen INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'INIT',
    idempotency_key VARCHAR(128),
    raw_callback CLOB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payment_records_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_payment_records_order FOREIGN KEY (order_id) REFERENCES consult_orders(id)
);

CREATE INDEX idx_payment_records_order_time ON payment_records(order_no, created_at);
CREATE INDEX idx_payment_records_order_id_time ON payment_records(order_id, created_at);

CREATE TABLE mentor_withdrawal_requests (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    mentor_user_id BIGINT NOT NULL,
    amount_fen INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    note VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mentor_withdrawal_requests_user FOREIGN KEY (mentor_user_id) REFERENCES users(id)
);

CREATE INDEX idx_mentor_withdrawal_requests_user_time ON mentor_withdrawal_requests(mentor_user_id, created_at);

CREATE TABLE mentor_schedule_slots (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    mentor_user_id BIGINT NOT NULL,
    start_at TIMESTAMP NOT NULL,
    end_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    booked_order_no VARCHAR(64),
    booked_order_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_mentor_schedule_slots UNIQUE (mentor_user_id, start_at, end_at),
    CONSTRAINT fk_mentor_schedule_slots_mentor FOREIGN KEY (mentor_user_id) REFERENCES users(id),
    CONSTRAINT fk_mentor_schedule_slots_booked_order FOREIGN KEY (booked_order_id) REFERENCES consult_orders(id)
);

CREATE INDEX idx_mentor_schedule_slots_mentor_time ON mentor_schedule_slots(mentor_user_id, start_at);
CREATE INDEX idx_mentor_schedule_slots_status_time ON mentor_schedule_slots(status, start_at);
CREATE INDEX idx_mentor_schedule_slots_booked_order_time ON mentor_schedule_slots(booked_order_id, status, start_at);

CREATE TABLE consult_after_sales_requests (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    order_id BIGINT,
    requester_user_id BIGINT NOT NULL,
    request_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reason CLOB NOT NULL,
    review_note CLOB,
    reviewer_user_id BIGINT,
    auto_triggered BOOLEAN NOT NULL DEFAULT FALSE,
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_consult_after_sales_requester FOREIGN KEY (requester_user_id) REFERENCES users(id),
    CONSTRAINT fk_consult_after_sales_reviewer FOREIGN KEY (reviewer_user_id) REFERENCES users(id),
    CONSTRAINT fk_consult_after_sales_order FOREIGN KEY (order_id) REFERENCES consult_orders(id)
);

CREATE INDEX idx_consult_after_sales_order_time ON consult_after_sales_requests(order_no, created_at);
CREATE INDEX idx_consult_after_sales_status_time ON consult_after_sales_requests(status, created_at);
CREATE INDEX idx_consult_after_sales_requester_time ON consult_after_sales_requests(requester_user_id, created_at);
CREATE INDEX idx_consult_after_sales_order_id_time ON consult_after_sales_requests(order_id, created_at);

CREATE TABLE notifications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL,
    category VARCHAR(32) NOT NULL,
    title VARCHAR(160) NOT NULL,
    content CLOB NOT NULL,
    ref_type VARCHAR(60),
    ref_id VARCHAR(64),
    action_code VARCHAR(80),
    priority VARCHAR(20) NOT NULL,
    event_id VARCHAR(64),
    payload_json CLOB,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP,
    archived_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_notifications_user_time ON notifications(user_id, created_at);
CREATE INDEX idx_notifications_user_read ON notifications(user_id, is_read, created_at);
CREATE INDEX idx_notifications_user_read_archived ON notifications(user_id, is_read, archived_at, created_at);
CREATE INDEX idx_notifications_event_id ON notifications(event_id);
CREATE INDEX idx_notifications_category_time ON notifications(category, created_at);

CREATE TABLE notification_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(64) NOT NULL,
    type VARCHAR(60) NOT NULL,
    category VARCHAR(32) NOT NULL,
    source_type VARCHAR(60),
    source_id VARCHAR(64),
    actor_user_id BIGINT,
    priority VARCHAR(20) NOT NULL,
    dedupe_key VARCHAR(160),
    payload_json CLOB,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_notification_events_event_id UNIQUE (event_id),
    CONSTRAINT uq_notification_events_dedupe_key UNIQUE (dedupe_key),
    CONSTRAINT fk_notification_events_actor FOREIGN KEY (actor_user_id) REFERENCES users(id)
);

CREATE INDEX idx_notification_events_category_time ON notification_events(category, occurred_at, id);
CREATE INDEX idx_notification_events_source ON notification_events(source_type, source_id, occurred_at, id);

CREATE TABLE notification_dispatch_jobs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_id VARCHAR(64) NOT NULL,
    notification_id BIGINT NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    channel VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 3,
    next_run_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_owner VARCHAR(80),
    lease_expires_at TIMESTAMP,
    sent_at TIMESTAMP,
    acked_at TIMESTAMP,
    failed_at TIMESTAMP,
    error_code VARCHAR(40),
    error_message VARCHAR(500),
    payload_json CLOB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_notification_dispatch_jobs_job_id UNIQUE (job_id),
    CONSTRAINT fk_notification_dispatch_jobs_notification FOREIGN KEY (notification_id) REFERENCES notifications(id),
    CONSTRAINT fk_notification_dispatch_jobs_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_notification_dispatch_jobs_status_run ON notification_dispatch_jobs(status, next_run_at, id);
CREATE INDEX idx_notification_dispatch_jobs_user_channel ON notification_dispatch_jobs(user_id, channel, status, id);

CREATE TABLE notification_dispatch_attempts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_id BIGINT NOT NULL,
    attempt_no INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    request_snapshot_json CLOB,
    response_snapshot_json CLOB,
    error_code VARCHAR(40),
    error_message VARCHAR(500),
    latency_ms BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_dispatch_attempts_job FOREIGN KEY (job_id) REFERENCES notification_dispatch_jobs(id)
);

CREATE INDEX idx_notification_dispatch_attempts_job ON notification_dispatch_attempts(job_id, created_at, id);

CREATE TABLE notification_preferences (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    category VARCHAR(32) NOT NULL,
    inbox_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    websocket_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    browser_popup_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    email_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    email_urgency_threshold VARCHAR(20) NOT NULL DEFAULT 'HIGH',
    quiet_hours_json VARCHAR(255),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_notification_preferences_user_category UNIQUE (user_id, category),
    CONSTRAINT fk_notification_preferences_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_notification_preferences_user ON notification_preferences(user_id, category);

INSERT INTO daily_tasks(task_code, title, description, points, is_active, sort_order, created_at, updated_at) VALUES
('TASK_RESUME_OPTIMIZE', '完成一次简历优化', '针对目标岗位优化一版简历内容并记录调整点。', 10, TRUE, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('TASK_SKILL_PROGRESS', '点亮一个技能节点', '在技能树中完成一个学习节点更新，保持成长连续性。', 8, TRUE, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('TASK_COMMUNITY_INTERACT', '参与一次社区互动', '阅读并发布一条有价值的社区互动内容。', 6, TRUE, 30, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO growth_checkin_reward_rules(reward_code, streak_days, bonus_points, reward_title, reward_description, is_enabled, sort_order, created_at, updated_at) VALUES
('CHECKIN_STREAK_3', 3, 6, '三日连签奖励', '连续签到 3 天可额外获得 6 积分。', TRUE, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('CHECKIN_STREAK_7', 7, 14, '七日连签奖励', '连续签到 7 天可额外获得 14 积分。', TRUE, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('CHECKIN_STREAK_14', 14, 30, '十四日连签奖励', '连续签到 14 天可额外获得 30 积分。', TRUE, 30, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

CREATE TABLE ai_quota_policies (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tier VARCHAR(20) NOT NULL,
    task_type VARCHAR(30) NOT NULL,
    scene_code VARCHAR(60),
    daily_free_limit INT NOT NULL,
    points_per_call INT NOT NULL,
    daily_max_limit INT NOT NULL,
    model_preference VARCHAR(50),
    max_input_tokens INT NOT NULL DEFAULT 4000,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ai_quota_policies_tier_task_scene UNIQUE (tier, task_type, scene_code)
);

CREATE INDEX idx_ai_quota_policies_lookup ON ai_quota_policies(tier, task_type, scene_code);

CREATE TABLE ai_call_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    trace_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    task_type VARCHAR(30) NOT NULL,
    scene_code VARCHAR(60),
    provider VARCHAR(50) NOT NULL,
    model VARCHAR(50) NOT NULL,
    route_code VARCHAR(80),
    route_policy_code VARCHAR(80),
    latency_ms BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    error_code VARCHAR(20),
    request_tokens INT NOT NULL DEFAULT 0,
    response_tokens INT NOT NULL DEFAULT 0,
    total_tokens INT NOT NULL DEFAULT 0,
    thoughts_tokens INT NOT NULL DEFAULT 0,
    reasoning_effort VARCHAR(20),
    thinking_budget INT,
    thinking_level VARCHAR(40),
    estimated_cost DECIMAL(10, 6) NOT NULL DEFAULT 0,
    charged_points INT NOT NULL DEFAULT 0,
    quota_weight INT NOT NULL DEFAULT 1,
    result_summary CLOB,
    result_payload_json CLOB,
    user_deleted_at TIMESTAMP,
    user_tier VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_call_logs_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_ai_call_logs_user_task_time ON ai_call_logs(user_id, task_type, created_at);
CREATE INDEX idx_ai_call_logs_trace_id ON ai_call_logs(trace_id);
CREATE INDEX idx_ai_call_logs_task_scene_time ON ai_call_logs(task_type, scene_code, created_at);
CREATE INDEX idx_ai_call_logs_model_time ON ai_call_logs(model, created_at);
CREATE INDEX idx_ai_call_logs_user_tier_time ON ai_call_logs(user_tier, created_at);

INSERT INTO ai_quota_policies(tier, task_type, scene_code, daily_free_limit, points_per_call, daily_max_limit, model_preference, max_input_tokens, created_at, updated_at) VALUES
('FREE', 'RESUME', NULL, 3, 10, 20, 'mock-economy-model', 4000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('FREE', 'INTERVIEW_TEXT', NULL, 5, 5, 30, 'mock-economy-model', 4000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('FREE', 'COMMUNITY_REPLY', NULL, 5, 2, 20, 'mock-economy-model', 4000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('FREE', 'COMMUNITY_REPLY', 'COMMUNITY_PRE_ANSWER', 5, 2, 20, 'mock-economy-model', 4000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('FREE', 'COMMUNITY_REPLY', 'MENTOR_PREP_SHEET_GENERATE', 2, 6, 8, 'mock-economy-model', 8000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('FREE', 'ICEBREAK', NULL, 3, 3, 15, 'mock-economy-model', 4000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('FREE', 'TTS', NULL, 2, 5, 10, 'mock-economy-model', 2000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('PREMIUM', 'RESUME', NULL, -1, 0, -1, 'mock-premium-model', 12000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('PREMIUM', 'INTERVIEW_TEXT', NULL, -1, 0, -1, 'mock-premium-model', 12000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('PREMIUM', 'COMMUNITY_REPLY', NULL, -1, 0, -1, 'mock-premium-model', 12000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('PREMIUM', 'COMMUNITY_REPLY', 'COMMUNITY_PRE_ANSWER', -1, 0, -1, 'mock-premium-model', 12000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('PREMIUM', 'COMMUNITY_REPLY', 'MENTOR_PREP_SHEET_GENERATE', -1, 0, -1, 'mock-premium-model', 16000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('PREMIUM', 'ICEBREAK', NULL, -1, 0, -1, 'mock-premium-model', 12000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('PREMIUM', 'TTS', NULL, -1, 0, -1, 'mock-premium-model', 4000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

CREATE TABLE interview_sessions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(64) NOT NULL,
    student_user_id BIGINT NOT NULL,
    target_role VARCHAR(100) NOT NULL,
    mode VARCHAR(30) NOT NULL,
    resume_context_json CLOB,
    session_context_json CLOB,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    reply_round_limit INT NOT NULL DEFAULT 8,
    reply_round_used INT NOT NULL DEFAULT 0,
    summary_generated TINYINT NOT NULL DEFAULT 0,
    prepaid_points INT NOT NULL DEFAULT 0,
    reserved_quota_weight INT NOT NULL DEFAULT 5,
    summary_overall_score INT,
    summary_strengths_json CLOB,
    summary_weaknesses_json CLOB,
    summary_suggestions_json CLOB,
    summary_provider VARCHAR(50),
    summary_model VARCHAR(100),
    summary_latency_ms BIGINT,
    summary_generated_at TIMESTAMP,
    finish_reason VARCHAR(50),
    ended_by_ai TINYINT NOT NULL DEFAULT 0,
    user_deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_interview_sessions_session_id UNIQUE (session_id),
    CONSTRAINT fk_interview_sessions_user FOREIGN KEY (student_user_id) REFERENCES users(id)
);

CREATE INDEX idx_interview_sessions_user_time ON interview_sessions(student_user_id, created_at);

CREATE TABLE interview_messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_pk BIGINT NOT NULL,
    sender_role VARCHAR(20) NOT NULL,
    message_text CLOB NOT NULL,
    coach_feedback CLOB,
    score_hint INT,
    audio_object_key VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_interview_messages_session FOREIGN KEY (session_pk) REFERENCES interview_sessions(id)
);

CREATE INDEX idx_interview_messages_session_time ON interview_messages(session_pk, created_at);

CREATE TABLE audit_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    trace_id VARCHAR(64) NOT NULL,
    operator_user_id BIGINT NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    target_type VARCHAR(30),
    target_id VARCHAR(100),
    detail_json CLOB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_logs_trace ON audit_logs(trace_id);
CREATE INDEX idx_audit_logs_action_time ON audit_logs(action_type, created_at);

CREATE TABLE sensitive_terms (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    term VARCHAR(200) NOT NULL,
    term_type VARCHAR(50) NOT NULL DEFAULT 'OTHER',
    risk_level VARCHAR(20) NOT NULL,
    action VARCHAR(20) NOT NULL,
    source_scope VARCHAR(50) NOT NULL,
    is_whitelist BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_sensitive_terms_scope UNIQUE (term, source_scope, is_whitelist)
);

CREATE INDEX idx_sensitive_terms_scope_enabled ON sensitive_terms(source_scope, enabled, is_whitelist);
CREATE INDEX idx_sensitive_terms_type_enabled ON sensitive_terms(term_type, enabled, is_whitelist);

CREATE TABLE moderation_policies (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    policy_key VARCHAR(100) NOT NULL,
    policy_value VARCHAR(500) NOT NULL,
    description VARCHAR(500),
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_moderation_policies_key UNIQUE (policy_key)
);

INSERT INTO moderation_policies(policy_key, policy_value, description, updated_by, updated_at) VALUES
('ai_input_enabled', 'true', 'AI 输入审查开关', 0, CURRENT_TIMESTAMP),
('ai_output_enabled', 'true', 'AI 输出审查开关', 0, CURRENT_TIMESTAMP),
('community_strict_review_enabled', 'true', '社区内容审查开关', 0, CURRENT_TIMESTAMP),
('auto_hide_report_threshold', '3', '举报自动隐藏阈值', 0, CURRENT_TIMESTAMP);

CREATE TABLE content_moderation_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    trace_id VARCHAR(64) NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_id VARCHAR(100),
    risk_level VARCHAR(20) NOT NULL,
    action VARCHAR(20) NOT NULL,
    reason_code VARCHAR(100) NOT NULL,
    masked_text CLOB,
    operator_user_id BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_moderation_trace ON content_moderation_events(trace_id);
CREATE INDEX idx_moderation_target ON content_moderation_events(target_type, target_id, created_at);

CREATE TABLE content_reports (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    reporter_user_id BIGINT NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id VARCHAR(100) NOT NULL,
    reason_code VARCHAR(50) NOT NULL,
    detail VARCHAR(1000),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    latest_action VARCHAR(50) NOT NULL DEFAULT 'NONE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMP,
    CONSTRAINT uq_content_reports_dedupe UNIQUE (reporter_user_id, target_type, target_id, reason_code),
    CONSTRAINT fk_content_reports_user FOREIGN KEY (reporter_user_id) REFERENCES users(id)
);

CREATE INDEX idx_content_reports_reporter ON content_reports(reporter_user_id, created_at);
CREATE INDEX idx_content_reports_target ON content_reports(target_type, target_id, status);

CREATE TABLE content_report_actions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    report_id BIGINT NOT NULL,
    operator_user_id BIGINT NOT NULL,
    decision VARCHAR(20) NOT NULL,
    action VARCHAR(50) NOT NULL,
    comment VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_content_report_actions_report FOREIGN KEY (report_id) REFERENCES content_reports(id),
    CONSTRAINT fk_content_report_actions_user FOREIGN KEY (operator_user_id) REFERENCES users(id)
);

CREATE INDEX idx_content_report_actions_report ON content_report_actions(report_id, created_at);

CREATE TABLE bounty_tasks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    enterprise_user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description CLOB NOT NULL,
    reward_description VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    accepted_submission_id BIGINT,
    deadline_at TIMESTAMP,
    closed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bounty_tasks_enterprise FOREIGN KEY (enterprise_user_id) REFERENCES users(id)
);

CREATE INDEX idx_bounty_tasks_enterprise_time ON bounty_tasks(enterprise_user_id, created_at);
CREATE INDEX idx_bounty_tasks_status_time ON bounty_tasks(status, created_at);

CREATE TABLE bounty_submissions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    student_user_id BIGINT NOT NULL,
    content_text CLOB,
    attachment_links VARCHAR(1000),
    status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    review_comment VARCHAR(500),
    contact_intent VARCHAR(80),
    reject_template VARCHAR(160),
    review_note CLOB,
    reviewed_at TIMESTAMP,
    reviewer_user_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_bounty_submissions_task_student UNIQUE (task_id, student_user_id),
    CONSTRAINT fk_bounty_submissions_task FOREIGN KEY (task_id) REFERENCES bounty_tasks(id),
    CONSTRAINT fk_bounty_submissions_student FOREIGN KEY (student_user_id) REFERENCES users(id),
    CONSTRAINT fk_bounty_submissions_reviewer FOREIGN KEY (reviewer_user_id) REFERENCES users(id)
);

ALTER TABLE bounty_tasks ADD CONSTRAINT fk_bounty_tasks_accepted_submission FOREIGN KEY (accepted_submission_id) REFERENCES bounty_submissions(id);

CREATE INDEX idx_bounty_submissions_task_time ON bounty_submissions(task_id, created_at);
CREATE INDEX idx_bounty_submissions_student_time ON bounty_submissions(student_user_id, created_at);
CREATE INDEX idx_bounty_submissions_status_time ON bounty_submissions(status, created_at);

CREATE TABLE bounty_submission_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    submission_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    actor_user_id BIGINT,
    event_type VARCHAR(40) NOT NULL,
    comment_text VARCHAR(500),
    contact_intent VARCHAR(80),
    reject_template VARCHAR(160),
    note CLOB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bounty_submission_events_submission FOREIGN KEY (submission_id) REFERENCES bounty_submissions(id),
    CONSTRAINT fk_bounty_submission_events_task FOREIGN KEY (task_id) REFERENCES bounty_tasks(id),
    CONSTRAINT fk_bounty_submission_events_actor FOREIGN KEY (actor_user_id) REFERENCES users(id)
);

CREATE INDEX idx_bounty_submission_events_submission_time ON bounty_submission_events(submission_id, created_at, id);
CREATE INDEX idx_bounty_submission_events_task_time ON bounty_submission_events(task_id, created_at, id);

CREATE TABLE ai_provider_configs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_code VARCHAR(60) NOT NULL,
    provider_type VARCHAR(40) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    base_url VARCHAR(255) NOT NULL,
    api_key_ciphertext TEXT,
    api_key_masked VARCHAR(80),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    timeout_ms INT NOT NULL DEFAULT 15000,
    max_retries INT NOT NULL DEFAULT 1,
    cost_per_1k_input DECIMAL(10, 6) NOT NULL DEFAULT 0,
    cost_per_1k_output DECIMAL(10, 6) NOT NULL DEFAULT 0,
    extra_config_json LONGTEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ai_provider_configs_code UNIQUE (provider_code)
);

CREATE TABLE ai_provider_models (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_config_id BIGINT NOT NULL,
    model_code VARCHAR(120) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    input_cost_per_1k DECIMAL(10, 6) NOT NULL DEFAULT 0,
    output_cost_per_1k DECIMAL(10, 6) NOT NULL DEFAULT 0,
    context_window INT,
    max_output_tokens INT,
    supported_task_types_json CLOB,
    notes VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ai_provider_models_provider_model UNIQUE (provider_config_id, model_code),
    CONSTRAINT fk_ai_provider_models_provider FOREIGN KEY (provider_config_id) REFERENCES ai_provider_configs(id)
);

CREATE INDEX idx_ai_provider_models_provider_enabled ON ai_provider_models(provider_config_id, enabled, model_code);

CREATE TABLE ai_scene_route_policies (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    policy_code VARCHAR(80) NOT NULL,
    task_type VARCHAR(40) NOT NULL,
    scene_code VARCHAR(60) NOT NULL,
    user_tier VARCHAR(20) NOT NULL DEFAULT 'FREE',
    strategy_type VARCHAR(20) NOT NULL DEFAULT 'SINGLE',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    notes VARCHAR(255),
    extra_config_json LONGTEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ai_scene_route_policies_code UNIQUE (policy_code),
    CONSTRAINT uq_ai_scene_route_policies_scene_tier UNIQUE (task_type, scene_code, user_tier)
);

CREATE INDEX idx_ai_scene_route_policies_lookup ON ai_scene_route_policies(task_type, scene_code, user_tier, enabled);

CREATE TABLE ai_model_routes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    route_code VARCHAR(60) NOT NULL,
    task_type VARCHAR(40) NOT NULL,
    scene_code VARCHAR(60),
    scene_route_policy_id BIGINT,
    provider_config_id BIGINT NOT NULL,
    model_name VARCHAR(120) NOT NULL,
    priority_no INT NOT NULL DEFAULT 100,
    candidate_weight INT NOT NULL DEFAULT 100,
    execution_mode VARCHAR(32) NOT NULL DEFAULT 'SYNC_BLOCKING',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    temperature DECIMAL(5, 2) NOT NULL DEFAULT 0.20,
    system_prompt LONGTEXT,
    prompt_template_name VARCHAR(80),
    extra_config_json LONGTEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ai_model_routes_code UNIQUE (route_code),
    CONSTRAINT fk_ai_model_routes_policy FOREIGN KEY (scene_route_policy_id) REFERENCES ai_scene_route_policies(id),
    CONSTRAINT fk_ai_model_routes_provider FOREIGN KEY (provider_config_id) REFERENCES ai_provider_configs(id)
);

CREATE INDEX idx_ai_model_routes_task_scene_priority ON ai_model_routes(task_type, scene_code, enabled, priority_no);
CREATE INDEX idx_ai_model_routes_prompt_template ON ai_model_routes(task_type, prompt_template_name);
CREATE INDEX idx_ai_model_routes_policy_priority ON ai_model_routes(scene_route_policy_id, enabled, priority_no);

CREATE TABLE prompt_templates (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_type VARCHAR(40) NOT NULL,
    template_name VARCHAR(80) NOT NULL,
    version_no INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    template_format VARCHAR(32) NOT NULL DEFAULT 'TEXT',
    content CLOB NOT NULL,
    description VARCHAR(255),
    variables_json CLOB,
    bundle_json CLOB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_prompt_templates_version UNIQUE (task_type, template_name, version_no)
);

CREATE INDEX idx_prompt_templates_task_name_status ON prompt_templates(task_type, template_name, status, version_no);

CREATE TABLE ai_async_task_jobs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    task_type VARCHAR(40) NOT NULL,
    scene_code VARCHAR(60),
    route_code VARCHAR(60),
    execution_mode VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    provider_code VARCHAR(60),
    provider_type VARCHAR(40),
    model_name VARCHAR(120),
    prompt_template_name VARCHAR(80),
    prompt_template_version_no INT,
    input_snapshot_json JSON,
    context_json JSON,
    prompt_snapshot_json JSON,
    route_snapshot_json JSON,
    result_summary CLOB,
    result_payload_json JSON,
    error_code VARCHAR(40),
    error_message VARCHAR(500),
    current_attempt INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 3,
    next_run_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_owner VARCHAR(80),
    lease_expires_at TIMESTAMP,
    queued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ai_async_task_jobs_task_id UNIQUE (task_id),
    CONSTRAINT fk_ai_async_task_jobs_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_ai_async_task_jobs_status_next_run ON ai_async_task_jobs(status, next_run_at, id);
CREATE INDEX idx_ai_async_task_jobs_user_created ON ai_async_task_jobs(user_id, created_at, id);
CREATE INDEX idx_ai_async_task_jobs_task_scene ON ai_async_task_jobs(task_type, scene_code, status, id);

CREATE TABLE ai_async_task_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(64) NOT NULL,
    task_job_id BIGINT NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    delivery_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    payload_json JSON,
    published_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ai_async_task_events_event_id UNIQUE (event_id),
    CONSTRAINT fk_ai_async_task_events_job FOREIGN KEY (task_job_id) REFERENCES ai_async_task_jobs(id),
    CONSTRAINT fk_ai_async_task_events_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_ai_async_task_events_delivery_status ON ai_async_task_events(delivery_status, created_at, id);
CREATE INDEX idx_ai_async_task_events_task_id ON ai_async_task_events(task_id, created_at, id);

CREATE TABLE ai_gateway_runtime_settings (
    setting_key VARCHAR(80) PRIMARY KEY,
    setting_value VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE feature_flags (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    flag_key VARCHAR(100) NOT NULL,
    flag_value VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_feature_flags_key UNIQUE (flag_key)
);

CREATE INDEX idx_feature_flags_updated_at ON feature_flags(updated_at);
