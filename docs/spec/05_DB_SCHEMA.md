# 数据库设计说明（当前默认 PostgreSQL，保留历史 MySQL 基线参考）

> **文档状态**：`evolving` · 最后审核：2026-04-18 · 当前默认运行时已切到 PostgreSQL；本文按“业务字段语义 + 当前 PostgreSQL 真相层 + 历史 MySQL 参考”组织，便于论文描述最终数据模型演进。
>

## 1. 设计原则
- 当前默认运行时事务真相由 PostgreSQL 统一保存；历史 MySQL 只保留为 dump / checkpoint / 回滚参考。
- 本文中的字段描述以业务语义为主；若历史章节仍出现 `DATETIME/TINYINT/JSON/TEXT` 等 MySQL 风格表述，应理解为跨阶段设计记法，当前物理真相以 `db/migration-pg` 和 PostgreSQL 实际 schema 为准。
- 支付与积分变化使用追加式账本。
- 关键业务状态使用明确枚举值。
- 所有可变表包含 `created_at/updated_at`。
- 内容类表支持软删除。

## 2. 核心用户表
### 2.1 `users`
用途：账号与角色主体。

关键字段：
- `id` BIGINT PK
- `email` UNIQUE
- `password_hash`
- `role`（`STUDENT|MENTOR|ENTERPRISE|ADMIN`）
- `tier`（`FREE|PREMIUM`，默认 `FREE`）
- `status`（`ACTIVE|PENDING|SUSPENDED`）
- `display_name`（账号昵称 / 当前外显名）
- `real_name` VARCHAR(100)（真实姓名）
- `last_login_at`
- `created_at`、`updated_at`
- `is_deleted`

索引：
- `uk_users_email (email)`
- `idx_users_role_status (role, status)`

### 2.2 `student_profiles`
学生扩展信息，`user_id` 唯一关联 `users.id`。

关键字段：
- `id` BIGINT PK
- `user_id` BIGINT UNIQUE FK → `users.id`
- `job_status` VARCHAR(50)（当前求职状态）
- `major` VARCHAR(100)（专业）
- `grade` VARCHAR(20)（年级，如“大2”“研一”）
- `gpa` VARCHAR(50)（GPA 展示文案）
- `target_position` VARCHAR(100)（目标岗位）
- `honors` TEXT（荣誉奖项，支持多行）
- `github` VARCHAR(255)（GitHub / 代码主页）
- `portfolio` VARCHAR(255)（作品集 / 个人站）
- `social_links_json` JSON（受控外部主页账号列表，当前允许 `GITHUB / PORTFOLIO / GITEE / JUEJIN / CSDN / ZHIHU / BILIBILI / XIAOHONGSHU / WEIBO`）
- `phone` VARCHAR(50)（手机号）
- `wechat` VARCHAR(100)（微信号）
- `avatar_bucket` VARCHAR(100)（头像对象存储 bucket）
- `avatar_object_key` VARCHAR(255)（头像对象 key）
- `avatar_content_type` VARCHAR(100)（头像标准化后的内容类型）
- `avatar_updated_at` DATETIME（头像最近更新时间）
- `skill_tags` VARCHAR(512)（技能标签，逗号分隔）
- `self_intro` TEXT（冷启动自我介绍）
- `created_at`、`updated_at`

说明：
- 学生头像二进制内容不直接入库，正式环境写入 MinIO；`student_profiles` 仅保存当前生效头像的对象元数据。

### 2.2.1 `student_profile_privacy_settings`
学生资料中心隐私矩阵，按学生唯一保存一份当前配置。

关键字段：
- `id` BIGINT PK
- `student_user_id` BIGINT UNIQUE FK → `users.id`
- `settings_json` JSON / TEXT（可见性矩阵序列化结果）
- `created_at`、`updated_at`

### 2.2.2 `student_profile_security_codes`
学生资料中心安全验证码持久化表，用于邮箱换绑与密码修改。

关键字段：
- `id` BIGINT PK
- `student_user_id` BIGINT FK → `users.id`
- `purpose` VARCHAR(64)（当前已使用 `PROFILE_EMAIL_CURRENT_CODE|PROFILE_EMAIL_NEW_CODE|PROFILE_PASSWORD_RESET_CODE`）
- `target_email` VARCHAR(255)（验证码目标邮箱）
- `code_hash` CHAR(64)（验证码哈希）
- `expires_at` DATETIME（过期时间）
- `next_send_at` DATETIME（下一次允许发送时间）
- `created_at`、`updated_at`

约束与索引：
- 唯一键：`(student_user_id, purpose, target_email)`

### 2.3 `mentor_profiles`
导师扩展信息，`user_id` 唯一关联 `users.id`。

关键字段：
- `id` BIGINT PK
- `user_id` BIGINT UNIQUE FK → `users.id`
- `company_name` VARCHAR(200)（公司或机构名称）
- `job_title` VARCHAR(100)（当前职级 / Title）
- `avatar_url` VARCHAR(255)（导师公开头像 URL / 兼容外链地址，可空）
- `avatar_bucket` VARCHAR(100)（导师头像对象存储 bucket，可空）
- `avatar_object_key` VARCHAR(255)（导师头像对象 key，可空）
- `avatar_content_type` VARCHAR(100)（导师头像标准化后的内容类型，可空）
- `avatar_updated_at` DATETIME（导师头像最近更新时间，可空）
- `expertise_tags` VARCHAR(512)（领域标签，逗号分隔）
- `service_scenes` VARCHAR(512)（导师公开服务场景，逗号分隔）
- `bio` TEXT（个人简介）
- `suitable_for` TEXT（适合服务对象 / 场景说明）
- `not_suitable_for` TEXT（不适合承接的诉求说明）
- `prep_materials` TEXT（建议学生提前准备的材料）
- `reply_rhythm` TEXT（导师回复节奏说明）
- `price_fen` INT（咨询单价，单位分）
- `is_available` TINYINT(1)（是否接单）
- `approval_status` VARCHAR(20)（`PENDING|APPROVED|REJECTED`）
- `total_orders` INT DEFAULT 0
- `avg_rating` DECIMAL(3,2) DEFAULT 0.00
- `created_at`、`updated_at`

说明：
- 当前正式认证主链路下，导师账号创建后默认 `approval_status=PENDING`。
- 文字身份信息（`company_name`、`job_title`）与正式认证资料提交共享同一组基础字段。
- `avatar_url + service_scenes` 当前已作为学生端导师广场的公开字段来源，用于卡片展示、问题场景筛选与推荐系统计算。
- 导师头像二进制内容不直接入库，正式环境写入 MinIO；`mentor_profiles` 保存当前生效头像的对象元数据，并通过公开读取接口对外提供稳定 `avatarUrl`。
- `suitable_for / not_suitable_for / prep_materials / reply_rhythm` 当前用于承载导师资料页中的服务规则编辑台与学生侧预览信息。

### 2.4 `enterprise_profiles`
企业扩展信息，含公司信息与审核状态。

关键字段：
- `id` BIGINT PK
- `user_id` BIGINT UNIQUE FK → `users.id`
- `company_name` VARCHAR(200)（公司名称）
- `industry` VARCHAR(100)（行业）
- `company_size` VARCHAR(50)（规模，如“50-200人”）
- `hiring_tags` VARCHAR(512)（招聘标签，逗号分隔）
- `contact_title` VARCHAR(100)（企业联系人当前岗位 / Title）
- `bio` TEXT（企业简介，可空）
- `external_links` TEXT（联系方式与外部链接，可空）
- `preferences` TEXT（招募偏好与学生提示，可空）
- `logo_bucket` VARCHAR(100)（企业 Logo 对象存储 bucket，可空）
- `logo_object_key` VARCHAR(255)（企业 Logo 对象 key，可空）
- `logo_content_type` VARCHAR(100)（企业 Logo 内容类型，可空）
- `logo_updated_at` DATETIME（企业 Logo 最近更新时间，可空）
- `approval_status` VARCHAR(20)（`PENDING|APPROVED|REJECTED`）
- `created_at`、`updated_at`

说明：
- 当前正式认证主链路下，企业账号创建后默认 `approval_status=PENDING`。
- 当前前端第二步与企业资料自助编辑都围绕 `company_name + contact_title + users.real_name` 这一组认证身份字段展开。
- `bio / external_links / preferences` 已由企业资料页真实使用，不再只是预留字段。
- 企业 Logo 二进制内容当前不直接落本表，而是写入对象存储；本表只保存 Logo 资源元数据，供企业资料页、企业工作台、任务列表与学生侧企业标识展示读取。
- 当前代码、Flyway 迁移与 repository 仍保留并使用本表；若某个本地开发库中缺失 `enterprise_profiles`，应优先排查迁移执行状态或环境漂移，而不是直接将本节视为过时文档。

### 2.4.1 `certification_submissions`
导师 / 企业认证资料提交主表，用于记录注册首次提交与后续重提版本链。

关键字段：
- `id` BIGINT PK
- `user_id` BIGINT FK → `users.id`
- `role` VARCHAR(20)（`MENTOR|ENTERPRISE`）
- `real_name` VARCHAR(100)
- `company_name` VARCHAR(200)
- `job_title` VARCHAR(100)
- `status` VARCHAR(20)（`PENDING|APPROVED|REJECTED|REPLACED`）
- `is_current` TINYINT(1)（是否为当前有效提交）
- `review_note` TEXT（管理员审核备注）
- `previous_submission_id` BIGINT（上一版本提交 ID，可空）
- `reviewed_by_user_id` BIGINT（审核管理员，可空）
- `submitted_at`
- `reviewed_at`
- `created_at`、`updated_at`

约束与索引：
- 索引：`(user_id, is_current)`、`(role, status, submitted_at)`、`(previous_submission_id)`

说明：
- 每次新提交都会新增一条记录，而不是覆盖旧版本。
- 历史 current submission 在新版本生成后会标记为 `REPLACED`，用于保留完整审核轨迹。

### 2.4.2 `certification_submission_assets`
认证提交附件表，用于保存正式认证资料对象元数据与生命周期状态。

关键字段：
- `id` BIGINT PK
- `submission_id` BIGINT FK → `certification_submissions.id`
- `bucket` VARCHAR(100)
- `object_key` VARCHAR(255)
- `original_filename` VARCHAR(255)
- `content_type` VARCHAR(100)
- `size_bytes` BIGINT
- `lifecycle_status` VARCHAR(20)（`ACTIVE|REPLACED`）
- `delete_reason` VARCHAR(100)（如 `REPLACED_BY_NEW_SUBMISSION`）
- `uploaded_at`
- `deleted_at`
- `created_at`、`updated_at`

约束与索引：
- 索引：`(submission_id, lifecycle_status)`、`(object_key)`

说明：
- 新版本提交后，旧附件对象会尝试从对象存储物理删除。
- 即便对象删除成功，也会在本表中保留 `REPLACED` 元数据留痕，便于管理员回溯资料生命周期。

### 2.5 `student_portrait_snapshots`（新增）
学生动态画像快照表（规则打标结果，按学生唯一）。

关键字段：
- `id` BIGINT PK
- `student_user_id` BIGINT UNIQUE FK → `users.id`
- `portrait_tags` JSON（标签数组，含 `code/label/source/confidence`）
- `evidence` JSON（证据计数，如 `masteredSkills/interviewMessages7d/posts7d`）
- `updated_at` DATETIME（最近刷新时间）

索引：
- `uk_portrait_student (student_user_id)`
- `idx_portrait_updated_at (updated_at)`

补充说明：
- 当前画像已不再只依赖技能 / 社区 / 面试消息计数。
- 最新实现会额外纳入：
  - 学生资料中的目标岗位、技能标签、自我介绍
  - 最近一次 AI 简历诊断的 `summary / suggestions / targetRole`
  - 最近一次 AI 面试总结的 `weaknesses / suggestions / targetRole`
- 当前已新增更细标签，如 `TARGET_DIRECTION_BACKEND`、`TARGET_DIRECTION_FRONTEND`、`DATA_ANALYSIS_ORIENTATION`、`RESUME_EXPRESSION_NEEDS_IMPROVEMENT`、`INTERVIEW_SYSTEM_DESIGN_GAP`、`INTERVIEW_COMMUNICATION_GAP` 等。
- 当前真实表仍以 `portrait_tags + evidence + updated_at` 为主，不把自然语言总结直接当作画像真相层。
- 下一阶段蓝图建议扩展以下字段或等价 JSON 结构：
  - `strength_tags_json`
  - `risk_tags_json`
  - `signal_level`
  - `freshness_level`
  - `summary_headline`
  - `summary_text`
  - `next_actions_json`
  - `summary_version`
- 若后续引入 LLM，只能写入表达层字段，不应覆盖结构化标签和证据真相。

### 2.5.1 `student_recommendation_snapshots`
学生推荐快照表，承接导师推荐所需的学生侧综合语义上下文。

关键字段：
- `id` BIGINT PK
- `student_user_id` BIGINT UNIQUE FK → `users.id`
- `content_text` TEXT（用于生成 embedding 的标准化文本快照）
- `target_position` VARCHAR(100)
- `skill_tags_json` TEXT
- `portrait_tags_json` TEXT
- `latest_resume_record_id` BIGINT
- `latest_resume_target_role` VARCHAR(100)
- `latest_resume_summary` TEXT
- `latest_resume_suggestions_json` TEXT
- `latest_interview_session_id` VARCHAR(64)
- `latest_interview_target_role` VARCHAR(100)
- `latest_interview_weaknesses_json` TEXT
- `latest_interview_suggestions_json` TEXT
- `signal_flags_json` TEXT（是否带入简历/面试/画像信号）
- `content_hash` CHAR(64)
- `created_at`、`updated_at`

说明：
- 这是“推荐专用快照”，不替代学生正式资料表。
- 推荐请求会先刷新该表，再决定是否复用已有 embedding 向量。
- 当前推荐主线正式定位为“基于内容的推荐 + 规则重排”；协同过滤不在本表承载范围内。

### 2.5.2 `mentor_recommendation_snapshots`
导师推荐快照表，承接导师公开资料的推荐语义切片。

关键字段：
- `id` BIGINT PK
- `mentor_user_id` BIGINT UNIQUE FK → `users.id`
- `content_text` TEXT（由导师公开资料拼出的标准化文本快照）
- `expertise_tags_json` TEXT
- `service_scenes_json` TEXT
- `quality_score` INT
- `price_fen` INT
- `is_available` TINYINT(1)
- `content_hash` CHAR(64)
- `created_at`、`updated_at`

说明：
- 当前快照来源以导师公开卡片字段为主，不引入私有资料。
- 后续若要增加正式认证履历摘要，可继续在本表增量扩展。

### 2.5.3 `recommendation_embedding_vectors`
推荐向量缓存表，统一保存学生/导师推荐快照的 embedding 向量。

关键字段：
- `id` BIGINT PK
- `entity_type` VARCHAR(30)（当前已使用 `STUDENT|MENTOR`）
- `entity_id` BIGINT
- `model_code` VARCHAR(60)（当前实现为 `LOCAL_HASHED_BOW_V1`）
- `vector_dim` INT
- `vector_json` LONGTEXT / TEXT
- `content_hash` CHAR(64)
- `created_at`、`updated_at`

说明：
- 当前先用 MySQL 落向量缓存，不急着上专门向量库。
- 若后续接真实 embedding，只需新增 model_code 和重刷向量，不影响现有推荐日志结构。
- 当前 `model_code=LOCAL_HASHED_BOW_V1`；后续可平滑新增本地中文 embedding 或 AI gateway 独立 embedding provider。

### 2.5.4 `mentor_recommendation_runs`
导师推荐运行主表，记录一次推荐请求的输入快照和总体结果。

关键字段：
- `id` BIGINT PK
- `student_user_id` BIGINT FK → `users.id`
- `scene`、`keyword`、`expertise`
- `filter_payload_json` TEXT
- `recall_model_code` VARCHAR(60)
- `rerank_version` VARCHAR(60)
- `weak_signal` TINYINT(1)
- `candidate_count` INT
- `recalled_count` INT
- `top_mentor_user_ids_json` TEXT
- `basis_summary` TEXT
- `created_at`

说明：
- 该表用于“可解释、可复现、可控”的推荐留痕，不对前端直接暴露。

### 2.5.5 `mentor_recommendation_events`
导师推荐事件明细表，记录召回与重排阶段的单导师过程数据。

关键字段：
- `id` BIGINT PK
- `run_id` BIGINT FK → `mentor_recommendation_runs.id`
- `mentor_user_id` BIGINT FK → `users.id`
- `event_type` VARCHAR(30)（如 `RECALL|RERANK`）
- `stage_rank` INT
- `score` DECIMAL(10,6)
- `detail_json` TEXT
- `created_at`

说明：
- `detail_json` 当前会保存语义相似度、场景命中、关键词命中、最终理由和风险提示等调试信息。

## 3. AI 与面试表
### 3.1 `ai_call_logs`
记录每次 AI 调用：`trace_id`、`task_type`、模型、耗时、状态、错误码、token 用量、预估费用、积分扣减、用户等级。

扩展字段（相对原始设计新增）：
- `request_tokens` INT
- `response_tokens` INT
- `total_tokens` INT
- `estimated_cost` DECIMAL(10,6)（预估费用，元）
- `charged_points` INT（本次调用实际计入日志的积分消耗）
- `user_tier` VARCHAR(20)（调用时的用户等级快照）

### 3.2 `ai_async_task_jobs`
AI 异步任务主表，当前已正式承接简历优化异步任务。

关键字段：
- `id` BIGINT PK
- `task_id` VARCHAR(64) UNIQUE
- `user_id` BIGINT FK → `users.id`
- `task_type` VARCHAR(40)
- `scene_code` VARCHAR(60)
- `route_code` VARCHAR(60)
- `execution_mode` VARCHAR(32)
- `status` VARCHAR(32)
- `provider_code`、`provider_type`、`model_name`
- `prompt_template_name`、`prompt_template_version_no`
- `input_snapshot_json` JSON / JSONB
- `context_json` JSON / JSONB
- `prompt_snapshot_json` JSON / JSONB
- `route_snapshot_json` JSON / JSONB
- `result_summary` TEXT
- `result_payload_json` JSON / JSONB
- `error_code`、`error_message`
- `current_attempt`、`max_attempts`
- `next_run_at`
- `lease_owner`、`lease_expires_at`
- `queued_at`、`started_at`、`finished_at`
- `created_at`、`updated_at`

索引：
- `uq_ai_async_task_jobs_task_id (task_id)`
- `idx_ai_async_task_jobs_status_next_run (status, next_run_at, id)`
- `idx_ai_async_task_jobs_user_created (user_id, created_at, id)`
- `idx_ai_async_task_jobs_task_scene (task_type, scene_code, status, id)`

说明：
- 当前正式任务类型以简历优化为主。
- PostgreSQL 基线已通过 [`V044__harden_ai_async_task_jsonb.sql`](apps/server-java/src/main/resources/db/migration-pg/V044__harden_ai_async_task_jsonb.sql) 将上述快照字段统一收口为 `JSONB`；H2 测试基线继续以 `JSON` 类型维持近似语义。
- 当前已新增 [`AiAsyncTaskMaintenanceJob.java`](apps/server-java/src/main/java/com/bishe/server/ai/gateway/task/AiAsyncTaskMaintenanceJob.java)，默认按 30 天保留期清理 `SUCCEEDED / FAILED / CANCELLED` 终态任务，避免异步任务快照无限增长。
- 下一阶段画像提取 / 画像总结将优先复用本表，而不是引入第二套任务系统。
- 规划态建议新增：
  - `task_type=PORTRAIT_REFRESH`
  - `task_type=PORTRAIT_SUMMARY`
  - `scene_code=STUDENT_PORTRAIT_REFRESH`
  - `scene_code=STUDENT_PORTRAIT_SUMMARY`
- 画像相关任务的 `input_snapshot_json` 应优先存结构化摘要和 hash，不直接保存完整简历或完整面试逐字稿。

### 3.3 `ai_async_task_events`
AI 异步任务事件表，记录任务状态变化与后续投递状态。

关键字段：
- `id` BIGINT PK
- `event_id` VARCHAR(64) UNIQUE
- `task_job_id` BIGINT FK → `ai_async_task_jobs.id`
- `task_id` VARCHAR(64)
- `user_id` BIGINT FK → `users.id`
- `event_type` VARCHAR(60)
- `delivery_status` VARCHAR(32)
- `payload_json` JSON / JSONB
- `published_at`
- `created_at`、`updated_at`

索引：
- `uq_ai_async_task_events_event_id (event_id)`
- `idx_ai_async_task_events_delivery_status (delivery_status, created_at, id)`
- `idx_ai_async_task_events_task_id (task_id, created_at, id)`

说明：
- 当前可用于任务状态轮询、通知投递和后续 WebSocket / 推送扩展。
- PostgreSQL 基线已通过 [`V044__harden_ai_async_task_jsonb.sql`](apps/server-java/src/main/resources/db/migration-pg/V044__harden_ai_async_task_jsonb.sql) 将 `payload_json` 收口为 `JSONB`。
- 画像异步任务若接入本底座，也应沿用同一事件留痕与投递语义。

### 3.4 `interview_sessions`
面试会话主表，含 `mode`（`INTERVIEW_TEXT|VOICE_MINIMAL`）。

扩展字段：
- `reply_round_limit` INT（当前会话允许的最大追问轮次快照）
- `reply_round_used` INT（当前会话已消耗追问轮次）
- `summary_generated` TINYINT（总结是否已生成）
- `prepaid_points` INT（会话开始时一次性预扣的积分）

### 3.5 `interview_messages`
面试消息明细，支持文本和音频对象键。

## 4. 成长与积分表
### 4.1 `skills`
技能树节点主数据。

### 4.2 `skill_progress`
学生节点进度，唯一键 `(student_user_id, node_code)`。

### 4.3 `daily_tasks`
每日任务池。

### 4.4 `checkins`
签到记录，唯一键 `(student_user_id, checkin_date)`。

### 4.5 `points_ledger`
积分账本（追加式）：`delta_points`、`reason_code`、`balance_after`。

规则：禁止更新历史账本行。

## 5. 社区表
### 5.1 `posts`
帖子主表：标题、正文、可见状态。

扩展字段：
- `tags` VARCHAR(255)（标签，逗号分隔，如“面经分享,技术讨论”）
- `moderation_status` VARCHAR(20)（`PASS|REVIEW|BLOCK`）
- `risk_level` VARCHAR(20)（`LOW|MEDIUM|HIGH|CRITICAL`）
- `last_moderation_event_id` BIGINT（最近一次审查事件 ID）

### 5.2 `comments`
评论表：支持 `is_ai` 标记。

扩展字段：
- `moderation_status` VARCHAR(20)（`PASS|REVIEW|BLOCK`）
- `risk_level` VARCHAR(20)（`LOW|MEDIUM|HIGH|CRITICAL`）
- `last_moderation_event_id` BIGINT（最近一次审查事件 ID）

### 5.3 `post_likes`
点赞关系表，唯一键 `(post_id, user_id)`。

说明（社区贡献榜）：
- `community_score_7d` 为查询时聚合指标，不落独立事实表。
- 聚合口径：`post*5 + comment*2 + like*1`。
- 仅统计 `PASS` 内容，窗口为近 7 天。

## 6. 咨询与支付表
### 6.1 `consult_orders`
咨询订单主表：`order_no`、用户、金额、状态、时间戳。

关键补充字段：
- `scene_code` VARCHAR(60)（订单所属咨询场景，如 `RESUME_DIAGNOSIS`）
- `source_page` VARCHAR(80)（下单入口来源，如导师广场主列表 / 推荐区 / 收藏区）
- `question_payload_json` TEXT（正式订单创建页的结构化问题输入快照）
- `problem_summary` TEXT（学生对本次咨询问题的浓缩摘要）
- `core_questions_json` TEXT（核心问题数组快照）
- `expected_outcomes_json` TEXT（期望输出数组快照）
- `selected_material_types` VARCHAR(255)（下单时勾选的材料类型快照，逗号分隔）
- `prep_sheet_snapshot_json` TEXT（从导师广场带入的准备单快照）
- `appointment_start_at` DATETIME（可空，订单预约开始时间快照）
- `appointment_end_at` DATETIME（可空，订单预约结束时间快照）

说明：
- 若学生下单时选择了导师排期时段，则会把预约时间快照写入订单，避免后续导师排期变动影响历史订单展示。
- `question_payload_json` 用于保留“主要问题 / 背景情况 / 已尝试动作 / 希望导师输出 / 额外说明”等结构化输入，方便后续在订单详情、履约区和售后回看时复原下单上下文。
- `source_page` 用于保留导师广场来源透传，便于分析订单来自主列表、推荐区还是收藏区。
- `problem_summary`、`core_questions_json`、`expected_outcomes_json` 与 `selected_material_types` 共同构成正式创单快照，用于学生详情页、导师履约区与售后争议回看。
- `prep_sheet_snapshot_json` 用于保留导师广场准备单导入时的 `summaryDraft/coreQuestions/suggestedMaterials/expectedOutcomes` 快照，避免推荐链路与正式订单链路断裂。

### 6.2 `consult_messages`
咨询消息线程。

说明：
- 聊天阶段如果学生补充或替换材料，前台可以通过系统消息提示“材料已更新”；旧材料版本不要求在前台消息流中完整展开。

### 6.3 `mentor_favorites`
学生收藏导师关系表。

关键字段：
- `id` BIGINT PK
- `student_user_id` BIGINT FK → `users.id`
- `mentor_user_id` BIGINT FK → `users.id`
- `created_at`

约束与索引：
- 唯一键：`(student_user_id, mentor_user_id)`，防止重复收藏
- 索引：`(student_user_id, created_at)`、`(mentor_user_id, created_at)`

说明：
- 当前收藏能力采用服务端持久化，不走纯前端本地收藏。
- 导师广场列表、推荐区与右侧预览区通过该表实时同步当前学生的收藏状态。

### 6.4 `payment_records`
支付记录：渠道、模式、交易号、状态、幂等键。

约束：`idempotency_key` 唯一，保证回调去重。

### 6.5 `consult_reviews`（新增）
咨询评价表，订单关闭后学生提交。

关键字段：
- `id` BIGINT PK
- `order_no` VARCHAR(64) UNIQUE FK → `consult_orders.order_no`
- `student_user_id` BIGINT FK → `users.id`
- `mentor_user_id` BIGINT FK → `users.id`
- `rating` TINYINT NOT NULL（1-5 星）
- `comment` TEXT（可选文字评价）
- `created_at`

说明：每个订单仅允许一次评价，通过 `order_no` 唯一约束保证。评价提交后更新 `mentor_profiles.avg_rating`。

### 6.6 `mentor_schedule_slots`（新增）
导师可预约时段表。

关键字段：
- `id` BIGINT PK
- `mentor_user_id` BIGINT FK → `users.id`
- `start_at` DATETIME NOT NULL
- `end_at` DATETIME NOT NULL
- `status` VARCHAR(20) NOT NULL（`AVAILABLE|BOOKED`）
- `booked_order_no` VARCHAR(64)（可空，关联已占用订单号）
- `created_at`、`updated_at`

约束与索引：
- 唯一键：`(mentor_user_id, start_at, end_at)`，避免重复排期
- 索引：`(mentor_user_id, start_at)`、`(status, start_at)`

说明：当前采用“学生下单即预占时段”的最小联调策略；未支付取消、超时回收已会自动释放时段，管理员手工退款在预约尚未结束时也会释放时段；改签/更复杂售后下的重绑规则仍留待后续版本决策。

### 6.7 `consult_after_sales_requests`（新增）
咨询售后申请表，用于承接学生退款诉求与管理员审核链。

关键字段：
- `id` BIGINT PK
- `order_no` VARCHAR(64) NOT NULL（关联咨询订单号）
- `requester_user_id` BIGINT NOT NULL FK → `users.id`
- `request_type` VARCHAR(20) NOT NULL（当前固定为 `REFUND`）
- `status` VARCHAR(20) NOT NULL（`PENDING|APPROVED|REJECTED`）
- `reason` TEXT NOT NULL
- `review_note` TEXT（可空）
- `reviewer_user_id` BIGINT FK → `users.id`（可空）
- `auto_triggered` BOOLEAN NOT NULL DEFAULT `false`
- `reviewed_at` DATETIME（可空）
- `created_at`、`updated_at`

约束与索引：
- 索引：`(order_no, created_at)`、`(status, created_at)`、`(requester_user_id, created_at)`

说明：v1 已实现“学生发起售后申请 -> 管理员审核通过/驳回 -> 审核通过时复用统一退款逻辑”的最小闭环；`auto_triggered` 字段现已用于标记系统自动兜底场景，例如导师超时未答时由系统自动创建/审批的退款售后单。

### 6.8 `consult_order_attachments`（已实现）
咨询订单材料包表，用于保存订单创建页初始上传的多文件材料，以及聊天阶段追加 / 替换后的当前有效副本。

关键字段：
- `id` BIGINT PK
- `order_no` VARCHAR(64) NOT NULL FK → `consult_orders.order_no`
- `uploaded_by_user_id` BIGINT NOT NULL FK → `users.id`
- `attachment_type` VARCHAR(50) NOT NULL（`RESUME|JOB_DESCRIPTION|PROJECT_MATERIAL|OFFER_MATERIAL|SUPPLEMENTARY`）
- `slot_code` VARCHAR(50) NOT NULL（如 `RESUME_MAIN`、`JOB_DESCRIPTION_MAIN`、`PROJECT_MATERIAL`）
- `source_stage` VARCHAR(20) NOT NULL（`ORDER_CREATE|CHAT_APPEND|CHAT_REPLACE`）
- `original_filename` VARCHAR(255) NOT NULL
- `content_type` VARCHAR(120) NOT NULL
- `size_bytes` BIGINT NOT NULL
- `storage_bucket` VARCHAR(120) NOT NULL
- `object_key` VARCHAR(255) NOT NULL
- `description` VARCHAR(255)（可空，材料说明）
- `lifecycle_status` VARCHAR(20) NOT NULL（`CURRENT|SUPERSEDED|DELETED`）
- `replaced_attachment_id` BIGINT（可空，指向被替换的上一份材料）
- `created_at`、`updated_at`

约束与索引：
- 索引：`(order_no, created_at)`、`(order_no, lifecycle_status)`、`(order_no, slot_code, lifecycle_status)`

说明：
- 当前正式范围要求支持多文件上传，但不要求前台文件预览或显式版本管理页面。
- 单槽位材料如 `RESUME_MAIN`、`JOB_DESCRIPTION_MAIN` 默认只允许保留一份 `CURRENT` 副本；当学生上传替换文件时，旧文件转为 `SUPERSEDED`。
- 多槽位材料如 `PROJECT_MATERIAL`、`OFFER_MATERIAL` 可并存多份 `CURRENT` 记录。
- 当前真实实现以 `created_at` 作为上传时间展示来源，学生端创单页、订单详情页与导师履约区均以“当前有效材料”作为默认展示口径。
- 导师前台默认只查看 `CURRENT` 材料；旧版本仅在后台审计、售后或后续高级历史页中按需追溯。

## 7. 企业悬赏表
### 7.1 `bounty_tasks`
企业任务主表。

关键字段：
- `id` BIGINT PK
- `enterprise_user_id` BIGINT NOT NULL FK → `users.id`
- `title` VARCHAR(200) NOT NULL
- `description` TEXT NOT NULL
- `reward_description` VARCHAR(255) NOT NULL
- `status` VARCHAR(20) NOT NULL（`OPEN|CLOSED`）
- `accepted_submission_id` BIGINT（可空，指向中选提交）
- `deadline_at` DATETIME（可空）
- `closed_at` DATETIME（可空）
- `created_at`、`updated_at`

约束与索引：
- 索引：`(enterprise_user_id, created_at)`、`(status, created_at)`

说明：
- 企业可手动 `CLOSE/REOPEN` 未收口任务。
- 一旦采纳某个学生提交，任务会自动转为 `CLOSED`，并记录 `accepted_submission_id`。

### 7.2 `bounty_submissions`
学生提交表。

关键字段：
- `id` BIGINT PK
- `task_id` BIGINT NOT NULL FK → `bounty_tasks.id`
- `student_user_id` BIGINT NOT NULL FK → `users.id`
- `content_text` TEXT（可空）
- `attachment_links` VARCHAR(1000)（可空，逗号分隔）
- `status` VARCHAR(20) NOT NULL（`SUBMITTED|REVIEWING|ACCEPTED|REJECTED`）
- `review_comment` VARCHAR(500)（可空）
- `contact_intent` VARCHAR(80)（可空，企业继续接触意图）
- `reject_template` VARCHAR(160)（可空，企业未入选模板）
- `review_note` TEXT（可空，企业审核补充说明）
- `reviewed_at` DATETIME（可空）
- `reviewer_user_id` BIGINT（可空，FK → `users.id`）
- `created_at`、`updated_at`

约束与索引：
- 唯一键：`(task_id, student_user_id)`，同一学生对同一任务仅允许 1 次提交
- 索引：`(task_id, created_at)`、`(student_user_id, created_at)`、`(status, created_at)`

说明：
- v1 仅支持文本说明与外部链接，不做代码沙箱执行。
- 企业采纳某个提交后，其余 `SUBMITTED|REVIEWING` 提交会自动批量改为 `REJECTED`，以便任务状态收口。
- `review_comment` 继续作为学生可见结果说明；`contact_intent / reject_template / review_note` 用于企业审核工作区的结构化决策留存。

### 7.3 `bounty_submission_events`
企业任务提交处理事件表。

关键字段：
- `id` BIGINT PK
- `submission_id` BIGINT NOT NULL FK → `bounty_submissions.id`
- `task_id` BIGINT NOT NULL FK → `bounty_tasks.id`
- `actor_user_id` BIGINT（可空，FK → `users.id`）
- `event_type` VARCHAR(40) NOT NULL（当前已用：`SUBMITTED|CONTACT_SENT|REJECT_SENT|AUTO_REJECTED_TASK_CLOSED|TASK_CLOSED_AFTER_ACCEPT`）
- `comment_text` VARCHAR(500)（可空，学生可见结果说明快照）
- `contact_intent` VARCHAR(80)（可空）
- `reject_template` VARCHAR(160)（可空）
- `note` TEXT（可空，补充说明 / 快照摘要）
- `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP

约束与索引：
- 索引：`(submission_id, created_at, id)`、`(task_id, created_at, id)`

说明：
- 当前用于支撑企业审核工作区右侧“历史处理记录”的真实留痕，不再依赖前端纯推导。
- 该表记录的是提交级别事件，而不是完整招聘流程状态机。

## 8. 通知表（升级）
### 8.1 `notifications`
统一站内收件箱表，当前已从“业务直接写一条提醒”升级为平台通知真相层。

关键字段：
- `id` BIGINT PK
- `user_id` BIGINT NOT NULL FK → `users.id`（接收人）
- `type` VARCHAR(50) NOT NULL（通知事件类型，如 `AI_RESUME_TASK_SUCCEEDED`、`CONSULT_REPLIED`、`SYSTEM_ANNOUNCEMENT`）
- `category` VARCHAR(32) NOT NULL（`AI_TASK|CONSULT|BOUNTY|CERTIFICATION|SYSTEM|COMMUNITY`）
- `title` VARCHAR(160) NOT NULL（通知标题）
- `content` TEXT NOT NULL（通知正文摘要）
- `ref_type` VARCHAR(60)（关联业务类型，如 `AI_ASYNC_TASK|CONSULT_ORDER|CERTIFICATION`）
- `ref_id` VARCHAR(100)（关联业务 ID）
- `action_code` VARCHAR(80)（前端跳转动作，如 `VIEW_AI_RESUME_TASK_RESULT`）
- `priority` VARCHAR(20) NOT NULL（`LOW|NORMAL|HIGH|URGENT`）
- `event_id` VARCHAR(64)（对应的通知事件 ID，可空）
- `payload_json` LONGTEXT（扩展上下文，如 `taskId / browserPopupAllowed / sourceType`）
- `is_read` TINYINT(1) NOT NULL DEFAULT 0
- `read_at` DATETIME（首次已读时间）
- `archived_at` DATETIME（归档时间；`NULL` 表示仍在当前收件箱）
- `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
- `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP

索引：
- `idx_notifications_user_time (user_id, created_at)`
- `idx_notifications_user_read_archived (user_id, is_read, archived_at, created_at)`
- `idx_notifications_event_id (event_id)`
- `idx_notifications_category_time (category, created_at)`

说明：
- 站内收件箱是通知系统事实真相层；即使 WebSocket / 邮件投递失败，也必须先保证本表稳定留痕。
- 当前列表查询默认只返回 `archived_at IS NULL` 的记录。

### 8.2 `notification_events`
统一通知事件表，用于记录“哪个业务事件触发了通知”。

关键字段：
- `id` BIGINT PK
- `event_id` VARCHAR(64) UNIQUE（平台事件 ID）
- `type` VARCHAR(60) NOT NULL（事件类型，与 `notifications.type` 同源）
- `category` VARCHAR(32) NOT NULL
- `source_type` VARCHAR(60)（事件来源，如 `AI_ASYNC_TASK|CONSULT_ORDER|BOUNTY_TASK|CERTIFICATION`）
- `source_id` VARCHAR(64)（来源业务主键）
- `actor_user_id` BIGINT FK → `users.id`（触发事件的操作者，可空）
- `priority` VARCHAR(20) NOT NULL
- `dedupe_key` VARCHAR(160) UNIQUE（去重键，可空）
- `payload_json` LONGTEXT（原始事件上下文）
- `occurred_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
- `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP

索引：
- `uq_notification_events_event_id (event_id)`
- `uq_notification_events_dedupe_key (dedupe_key)`
- `idx_notification_events_category_time (category, occurred_at, id)`
- `idx_notification_events_source (source_type, source_id, occurred_at, id)`

说明：
- `notification_events` 记录事件本身；一个事件后续可以分发给多个用户并产生多条收件箱记录。
- 当前若 `dedupe_key` 命中，发布层会跳过重复事件，避免重复入箱。

### 8.3 `notification_dispatch_jobs`
渠道投递任务表，负责 WebSocket / 邮件等额外触达的生命周期管理。

关键字段：
- `id` BIGINT PK
- `job_id` VARCHAR(64) UNIQUE（渠道任务 ID）
- `notification_id` BIGINT NOT NULL FK → `notifications.id`
- `event_id` VARCHAR(64) NOT NULL（冗余事件 ID，便于排查）
- `user_id` BIGINT NOT NULL FK → `users.id`
- `channel` VARCHAR(32) NOT NULL（当前实现为 `WEBSOCKET|EMAIL`）
- `status` VARCHAR(32) NOT NULL DEFAULT `PENDING`（`PENDING|RUNNING|RETRY_WAIT|SENT|ACKED|SKIPPED|FAILED|DEAD`）
- `attempt_count` INT NOT NULL DEFAULT 0
- `max_attempts` INT NOT NULL DEFAULT 3
- `next_run_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
- `lease_owner` VARCHAR(80)（被 worker claim 的租约持有者）
- `lease_expires_at` DATETIME（租约过期时间）
- `sent_at` DATETIME（发送成功时间）
- `acked_at` DATETIME（仅 WebSocket ACK 回执成功时写入）
- `failed_at` DATETIME（进入失败 / 死信态时间）
- `error_code` VARCHAR(40)
- `error_message` VARCHAR(500)
- `payload_json` LONGTEXT（渠道投递快照）
- `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
- `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP

索引：
- `idx_notification_dispatch_jobs_status_run (status, next_run_at, id)`
- `idx_notification_dispatch_jobs_user_channel (user_id, channel, status, id)`

说明：
- `SENT` 表示渠道已成功发出；WebSocket 连接收到客户端 `ACK` 后再转 `ACKED`。
- `SKIPPED` 常用于“当前无活跃 WebSocket 会话”或“邮件配置缺失”等无需重试场景。
- `FAILED` 表示可重试失败；超过尝试上限后转 `DEAD`。

### 8.4 `notification_dispatch_attempts`
渠道任务尝试留痕表，用于审计每次投递与重试。

关键字段：
- `id` BIGINT PK
- `job_id` BIGINT NOT NULL FK → `notification_dispatch_jobs.id`
- `attempt_no` INT NOT NULL
- `status` VARCHAR(32) NOT NULL
- `request_snapshot_json` LONGTEXT
- `response_snapshot_json` LONGTEXT
- `error_code` VARCHAR(40)
- `error_message` VARCHAR(500)
- `latency_ms` BIGINT NOT NULL DEFAULT 0
- `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP

索引：
- `idx_notification_dispatch_attempts_job (job_id, created_at, id)`

### 8.5 `notification_preferences`
用户通知偏好表，按“用户 + 分类”维度覆盖系统默认值。

关键字段：
- `id` BIGINT PK
- `user_id` BIGINT NOT NULL FK → `users.id`
- `category` VARCHAR(32) NOT NULL
- `inbox_enabled` TINYINT(1) NOT NULL DEFAULT 1
- `websocket_enabled` TINYINT(1) NOT NULL DEFAULT 1
- `browser_popup_enabled` TINYINT(1) NOT NULL DEFAULT 1
- `email_enabled` TINYINT(1) NOT NULL DEFAULT 0
- `email_urgency_threshold` VARCHAR(20) NOT NULL DEFAULT `HIGH`
- `quiet_hours_json` VARCHAR(255)（当前以前端透传字符串 JSON 保存）
- `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP

约束与索引：
- 唯一键：`uq_notification_preferences_user_category (user_id, category)`
- 索引：`idx_notification_preferences_user (user_id, category)`

说明：
- 当前首版里 `inbox_enabled` 实际固定保持 `true`，保证事务通知必定入箱；其余开关用于控制 WebSocket、桌面提醒和邮件额外触达。
- 默认规则由服务层兜底：`SYSTEM` 默认邮件阈值为 `NORMAL`，其余事务分类默认阈值为 `HIGH`；`COMMUNITY` 当前默认不启用邮件触达。

## 9. 管理与配置表
### 9.1 `ai_provider_configs`
AI 提供商配置。

关键字段：
- `id` BIGINT PK
- `provider_code` VARCHAR(60) UNIQUE
- `provider_type` VARCHAR(40)（当前支持 `OPENAI_COMPATIBLE|GEMINI_NATIVE`）
- `display_name` VARCHAR(80)
- `base_url` VARCHAR(255)
- `api_key_ciphertext` TEXT（加密后存储）
- `api_key_masked` VARCHAR(80)（后台回显脱敏值）
- `enabled` BOOLEAN
- `timeout_ms` INT
- `max_retries` INT
- `cost_per_1k_input` DECIMAL(10,6)
- `cost_per_1k_output` DECIMAL(10,6)
- `extra_config_json` LONGTEXT
- `created_at`、`updated_at`

### 9.2 `ai_model_routes`
任务到模型路由配置。

关键字段：
- `id` BIGINT PK
- `route_code` VARCHAR(60) UNIQUE
- `task_type` VARCHAR(40)
- `scene_code` VARCHAR(60)（可空）
- `provider_config_id` BIGINT FK → `ai_provider_configs.id`
- `model_name` VARCHAR(120)
- `priority_no` INT
- `enabled` BOOLEAN
- `temperature` DECIMAL(5,2)
- `system_prompt` LONGTEXT（兼容旧 route 级提示词）
- `prompt_template_name` VARCHAR(80)（可空；绑定模板名）
- `extra_config_json` LONGTEXT
- `created_at`、`updated_at`

说明：
- 当前路由解析按 `task_type + scene_code + priority_no` 选择命中 route。
- 当 `prompt_template_name` 非空时，运行时会尝试读取同 `task_type + prompt_template_name` 下的 `ACTIVE` 模板内容；若 route 未绑定模板，则继续使用 `system_prompt`。

### 9.3 `ai_gateway_runtime_settings`
AI 网关运行时开关表。

关键字段：
- `setting_key` VARCHAR(64) PK（当前包含 `DEBUG_MODE_ENABLED`、`AI_REQUEST_LOG_ENABLED`）
- `setting_value` VARCHAR(200)（布尔值以字符串形式存储）
- `description` VARCHAR(255)
- `updated_by` BIGINT（可空，关联管理员用户）
- `updated_at` DATETIME

说明：
- 后台 `/admin/ai/runtime-settings` 会读写此表，用于动态控制 AI 网关诊断日志与 AI 请求日志开关。
- 若数据库中不存在对应 key，则回退到 `application.yml/.env` 中的默认值。

### 9.4 `prompt_templates`
Prompt 模板与版本状态。

关键字段：
- `id` BIGINT PK
- `task_type` VARCHAR(40)
- `template_name` VARCHAR(80)
- `version_no` INT
- `status` VARCHAR(20)（`DRAFT|ACTIVE|INACTIVE`）
- `content` LONGTEXT
- `description` VARCHAR(255)
- `variables_json` LONGTEXT（可空；当前用于保存变量说明/元数据）
- `created_at`、`updated_at`

约束与说明：
- 唯一键：`(task_type, template_name, version_no)`。
- service 层保证同一 `(task_type, template_name)` 同时仅一个 `ACTIVE` 版本。
- route 通过 `ai_model_routes.prompt_template_name` 仅绑定模板名；实际命中的模板版本取该任务下当前 `ACTIVE` 版本。

### 9.5 `feature_flags`
功能开关（支付模式、语音开关等）。

关键字段：
- `id` BIGINT PK
- `flag_key` VARCHAR(100) UNIQUE（如 `payment.mode`、`voice.enabled`）
- `flag_value` VARCHAR(100)
- `description` VARCHAR(255)
- `updated_by` BIGINT
- `created_at`、`updated_at`

说明：
- 运行时只维护少量高价值开关；当前最小实现覆盖支付模式、语音能力、社区 AI 预答。
- 若未配置数据库覆盖值，则回退到代码默认值 / 静态配置值。

### 9.6 `audit_logs`
审计日志。

扩展字段：
- `action_type` VARCHAR(50)（支持治理动作，如 `REPORT_DECISION|CONTENT_REVIEW_DECISION|AUTO_HIDE`）

### 9.7 `ai_quota_policies`
AI 配额策略配置表。

关键字段：
- `tier` VARCHAR(20)（`FREE|PREMIUM`）
- `task_type` VARCHAR(30)
- `daily_free_limit` INT（每日免费次数）
- `points_per_call` INT（超出免费后每次消耗积分）
- `daily_max_limit` INT（每日总上限）
- `model_preference` VARCHAR(50)（该等级优先使用的模型）
- `max_input_tokens` INT（单次最大输入 token）
- 唯一键：`(tier, task_type)`

### 9.8 `sensitive_terms`
敏感词库表（文本治理核心配置）。

关键字段：
- `id` BIGINT PK
- `term` VARCHAR(200) UNIQUE
- `term_type` VARCHAR(50)（`POLITICS|PORNOGRAPHY|TERROR|VIOLENCE|FRAUD|ABUSE|ADVERTISEMENT|ILLEGAL|OTHER`）
- `risk_level` VARCHAR(20)（`LOW|MEDIUM|HIGH|CRITICAL`）
- `action` VARCHAR(20)（`PASS|MASK|BLOCK|REVIEW`）
- `source_scope` VARCHAR(50)（`AI_INPUT|AI_OUTPUT|COMMUNITY_POST|COMMUNITY_COMMENT|ALL`）
- `is_whitelist` TINYINT(1) DEFAULT 0
- `enabled` TINYINT(1) DEFAULT 1
- `created_at`、`updated_at`

### 9.9 `moderation_policies`
审查策略表（支持运行时更新）。

关键字段：
- `id` BIGINT PK
- `policy_key` VARCHAR(100) UNIQUE（如 `ai_input_enabled`）
- `policy_value` VARCHAR(500)
- `description` VARCHAR(500)
- `updated_by` BIGINT
- `updated_at`

### 9.10 `content_moderation_events`
内容审查事件表（统一追踪 AI 与社区审查结果）。

关键字段：
- `id` BIGINT PK
- `trace_id` VARCHAR(64) NOT NULL
- `source_type` VARCHAR(30)（`AI_INPUT|AI_OUTPUT|COMMUNITY_POST|COMMUNITY_COMMENT|CONSULT_MESSAGE|BOUNTY_TEXT`）
- `target_type` VARCHAR(30)（`POST|COMMENT|AI_REQUEST|AI_RESPONSE|MESSAGE|BOUNTY`）
- `target_id` VARCHAR(100)
- `risk_level` VARCHAR(20)
- `action` VARCHAR(20)
- `reason_code` VARCHAR(100)
- `masked_text` TEXT（可选，脱敏后内容）
- `operator_user_id` BIGINT（系统自动为 0，人工处理写管理员 ID）
- `created_at`

索引：
- `idx_moderation_trace (trace_id)`
- `idx_moderation_target (target_type, target_id, created_at)`

### 9.11 `content_reports`
举报主表。

关键字段：
- `id` BIGINT PK
- `reporter_user_id` BIGINT FK → `users.id`
- `target_type` VARCHAR(20)（`POST|COMMENT|USER`）
- `target_id` VARCHAR(100)
- `reason_code` VARCHAR(50)
- `detail` VARCHAR(1000)
- `status` VARCHAR(20)（`PENDING|ACCEPTED|REJECTED|CLOSED`）
- `latest_action` VARCHAR(50)（`NONE|TAKE_DOWN|RESTORE|REJECT`）
- `created_at`、`updated_at`、`closed_at`

约束建议：
- 唯一键：`(reporter_user_id, target_type, target_id, reason_code)`（避免短时间重复举报）

### 9.12 `content_report_actions`
举报处置流水表（追加式）。

关键字段：
- `id` BIGINT PK
- `report_id` BIGINT FK → `content_reports.id`
- `operator_user_id` BIGINT FK → `users.id`
- `decision` VARCHAR(20)（`ACCEPTED|REJECTED|CLOSED`）
- `action` VARCHAR(50)（`TAKE_DOWN|RESTORE|NO_ACTION`）
- `comment` VARCHAR(1000)
- `created_at`

## 10. 统一枚举

> 枚举定义的唯一真相源见 [00_README_AGENT_START.md §统一枚举定义](./00_README_AGENT_START.md#统一枚举定义ssot)。
> 本文引用不再独立维护，以 SSOT 为准。

治理相关枚举引用：
- `ModerationAction`
- `ModerationRiskLevel`
- `ReportTargetType`
- `ReportStatus`
- `ModerationSourceType`

## 10.1 核心表 DDL 示例

> 以下为关键表的参考 DDL，完整迁移脚本以 `V001__init.sql` 为准。

```sql
-- 用户主表
CREATE TABLE users (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  email         VARCHAR(255) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role          VARCHAR(20)  NOT NULL COMMENT 'STUDENT|MENTOR|ENTERPRISE|ADMIN',
  tier          VARCHAR(20)  NOT NULL DEFAULT 'FREE' COMMENT 'FREE|PREMIUM',
  status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE|PENDING|SUSPENDED',
  display_name  VARCHAR(100) NOT NULL,
  last_login_at DATETIME     NULL,
  is_deleted    TINYINT(1)   NOT NULL DEFAULT 0,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_users_email (email),
  INDEX idx_users_role_status (role, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 学生画像扩展表（含资料中心扩展与头像元数据）
CREATE TABLE student_profiles (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id         BIGINT       NOT NULL,
  job_status      VARCHAR(50)  NULL,
  school_name     VARCHAR(150) NULL COMMENT '学校展示名称',
  school_name_key VARCHAR(160) NULL COMMENT '学校归一化匹配键，供同校学生规则使用',
  major           VARCHAR(100) NULL,
  grade           VARCHAR(20)  NULL,
  gpa             VARCHAR(50)  NULL,
  target_position VARCHAR(100) NULL,
  honors          TEXT         NULL,
  github          VARCHAR(255) NULL,
  portfolio       VARCHAR(255) NULL,
  social_links_json JSON       NULL COMMENT '受控外部主页账号列表',
  phone           VARCHAR(50)  NULL,
  wechat          VARCHAR(100) NULL,
  avatar_bucket   VARCHAR(100) NULL,
  avatar_object_key VARCHAR(255) NULL,
  avatar_content_type VARCHAR(100) NULL,
  avatar_updated_at DATETIME   NULL,
  skill_tags      VARCHAR(512) NULL COMMENT '逗号分隔',
  self_intro      TEXT         NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_student_profile_user (user_id),
  INDEX idx_student_profiles_school_name_key (school_name_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 学生资料中心隐私矩阵
CREATE TABLE student_profile_privacy_settings (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_user_id BIGINT       NOT NULL,
  settings_json   JSON         NOT NULL COMMENT '隐私矩阵 JSON：每项包含 guest/student/platformStudent/mentor/enterprise 五类角色开关',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_student_profile_privacy_user (student_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 学生资料中心安全验证码
CREATE TABLE student_profile_security_codes (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_user_id BIGINT       NOT NULL,
  purpose         VARCHAR(64)  NOT NULL,
  target_email    VARCHAR(255) NOT NULL,
  code_hash       CHAR(64)     NOT NULL,
  expires_at      DATETIME     NOT NULL,
  next_send_at    DATETIME     NOT NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_student_profile_security_identity (student_user_id, purpose, target_email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 学生动态画像快照表
CREATE TABLE student_portrait_snapshots (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_user_id BIGINT       NOT NULL,
  portrait_tags   JSON         NOT NULL,
  evidence        JSON         NOT NULL,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_portrait_student (student_user_id),
  INDEX idx_portrait_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 咨询订单主表
CREATE TABLE consult_orders (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no        VARCHAR(64)  NOT NULL,
  student_user_id BIGINT       NOT NULL,
  mentor_user_id  BIGINT       NOT NULL,
  amount_fen      INT          NOT NULL COMMENT '金额（分）',
  status          VARCHAR(20)  NOT NULL DEFAULT 'CREATED',
  question_text   TEXT         NULL,
  appointment_start_at DATETIME NULL,
  appointment_end_at   DATETIME NULL,
  paid_at         DATETIME     NULL,
  closed_at       DATETIME     NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_order_no (order_no),
  INDEX idx_student (student_user_id),
  INDEX idx_mentor (mentor_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 学生收藏导师关系表
CREATE TABLE mentor_favorites (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_user_id  BIGINT       NOT NULL,
  mentor_user_id   BIGINT       NOT NULL,
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_mentor_favorite_student_mentor (student_user_id, mentor_user_id),
  INDEX idx_mentor_favorite_student_time (student_user_id, created_at),
  INDEX idx_mentor_favorite_mentor_time (mentor_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 导师可预约时段表
CREATE TABLE mentor_schedule_slots (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  mentor_user_id   BIGINT       NOT NULL,
  start_at         DATETIME     NOT NULL,
  end_at           DATETIME     NOT NULL,
  status           VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',
  booked_order_no  VARCHAR(64)  NULL,
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_mentor_slot (mentor_user_id, start_at, end_at),
  INDEX idx_mentor_slot_time (mentor_user_id, start_at),
  INDEX idx_mentor_slot_status_time (status, start_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 支付记录表
CREATE TABLE payment_records (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no         VARCHAR(64)  NOT NULL,
  channel          VARCHAR(20)  NOT NULL COMMENT 'ALIPAY|MOCK',
  mode             VARCHAR(20)  NOT NULL COMMENT 'SANDBOX|MOCK',
  provider_trade_no VARCHAR(128) NULL,
  amount_fen       INT          NOT NULL,
  status           VARCHAR(20)  NOT NULL DEFAULT 'INIT',
  idempotency_key  VARCHAR(128) NULL,
  raw_callback     TEXT         NULL,
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_idempotency (idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 积分账本（追加式）
CREATE TABLE points_ledger (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_user_id BIGINT      NOT NULL,
  delta_points    INT         NOT NULL COMMENT '变动量，正负均可',
  reason_code     VARCHAR(50) NOT NULL,
  balance_after   INT         NOT NULL,
  created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_student_time (student_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 11. 软删除与审计
- 社区内容表支持软删除。
- 账本与支付记录不允许正常业务删除。
- 管理动作必须写 `audit_logs`。
- 审查与举报处置必须写 `content_moderation_events` 与 `content_report_actions`。

## 12. 迁移规范
- 版本化脚本，如 `V001__init.sql`。
- 禁止无回滚方案的破坏性变更。

## 13. 本文档范围
覆盖 v1 所有业务所需核心表，不含未来数仓和高级推荐特征存储。

## 14. 决策摘要
- 账本优先、审计优先。
- 枚举值必须与 API/SRS 一致。
- 内容治理采用“事件表 + 主表状态”双轨设计，便于追溯与统计。

## 15. 验收标准
1. 关键业务表齐全且关系闭环。
2. 幂等、账本、审计约束可执行。

## 16. 数据模型目标
为 v1 提供稳定、可审计、可扩展的数据基础，确保订单支付、积分账本、AI 调用、学生画像快照、社区内容与内容治理事件都具备一致性与可追踪性。
