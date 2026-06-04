# 数据库表 Inventory 与迁移批次建议

> **文档状态**：`frozen` · 最后审核：2026-04-17 · 数据库迁移主线已完成；本文保留为库表 inventory、迁移边界与后续结构治理参考基线。

## 1. 文档目标
本文件最初用于把当前数据库中的 63 张表整理成一份可执行的 inventory 文档，服务于以下目标；当前迁移主线已完成，本文保留为冻结参考：

- 明确每张表当前属于哪个业务域、承担什么职责、主要由哪些代码入口访问。
- 先冻结“哪些表必须保留并优先迁移、哪些表是配置/事件/辅助结构、哪些对象可进入后续复核池”。
- 为后续 PostgreSQL schema 基线、JPA/Hibernate 建模、Testcontainers 回归和分批迁移提供分组依据。

本文件是 [`46_MYSQL_TO_POSTGRESQL_MIGRATION_MASTER_PLAN.md`](./46_MYSQL_TO_POSTGRESQL_MIGRATION_MASTER_PLAN.md) 的执行性补充，不直接决定字段级删改和实体级重构细节。

## 2. 盘点方法与判读边界
### 2.1 数据来源
- 表规模来自 2026-04-16 当前本地 MySQL 快照统计。
- 代码归属来自主代码中对表名的静态引用采样，重点关注 Repository / Service / Admin 入口。
- 当前统计口径包含业务表、配置表、事件表、系统元数据表。

### 2.2 当前总量摘要
- 当前共盘点到 `63` 张表。
- 当前本地样本总计约 `2606` 行、`8323072` bytes，约 `7.94 MiB`。
- 当前本地样本不是生产体量，因此“行数少”只可用于辅助判断，不可直接作为删表依据。

### 2.3 重点观察
- `ai_async_task_jobs` 仅 `5` 行但占用约 `3.58 MiB`，说明该表承载了大量 JSON 快照；当前 PostgreSQL 基线已完成 `JSONB + retention` 收口，后续仍需持续观察体量增长与清理效果。
- `mentor_recommendation_events` 以 `607` 行成为当前本地样本中行数最多的表，说明推荐链路当前更多是“运行留痕 + 事件分析”而不是单表真相层。
- 当前 `0` 行表共有 5 张：`certification_submission_assets`、`notification_dispatch_attempts`、`notification_dispatch_jobs`、`student_profile_privacy_settings`、`student_profile_security_codes`。这些表都仍有主代码引用，当前只能进入复核池，不能直接认定为废表。

### 2.4 分类口径
本文件统一使用以下分类：

- `核心真相表`：用户可见或业务流程必须依赖的主事实数据。
- `配置/策略表`：功能开关、策略、模板、规则、渠道配置。
- `日志/事件表`：审计、运行留痕、操作历史、事件流水。
- `生命周期/辅助表`：附件、派发任务、验证码、收藏、消息附件等辅助结构。
- `衍生快照/缓存表`：画像、推荐快照、向量缓存、运行结果衍生物。
- `系统元数据`：Flyway 等系统级元信息。

## 3. Inventory 摘要
### 3.1 当前高行数表
| 表名 | 行数 | 体量 | 说明 |
|---|---:|---:|---|
| `mentor_recommendation_events` | 607 | 240 KB | 推荐运行事件留痕，后续需保留期策略 |
| `content_moderation_events` | 219 | 96 KB | 内容治理审计事件 |
| `skill_node_resources` | 148 | 96 KB | 技能节点资源关联表 |
| `ai_call_logs` | 139 | 384 KB | AI 调用与计费/路由日志 |
| `skills` | 122 | 64 KB | 技能树节点真相层 |
| `interview_messages` | 106 | 96 KB | AI 面试消息正文 |
| `mentor_schedule_slots` | 102 | 64 KB | 导师排期时段真相层 |
| `notifications` | 98 | 160 KB | 用户通知主表 |

### 3.2 当前高体量表
| 表名 | 行数 | 体量 | 说明 |
|---|---:|---:|---|
| `ai_async_task_jobs` | 5 | 3.58 MB | 大量上下文与结果快照，已完成 `JSONB` + retention 收口 |
| `ai_call_logs` | 139 | 384 KB | AI 请求/响应/计费留痕较重 |
| `mentor_recommendation_events` | 607 | 240 KB | 推荐事件明细 |
| `notifications` | 98 | 160 KB | 主通知内容可能含较多 payload |
| `consult_orders` | 39 | 144 KB | 交易主单表，需谨慎迁移 |
| `ai_async_task_events` | 26 | 128 KB | 异步任务事件 |
| `notification_events` | 35 | 128 KB | 通知事件流水 |
| `recommendation_embedding_vectors` | 26 | 128 KB | 向量缓存，后续可考虑 `pgvector` |

## 4. 表用途矩阵
### 4.1 账号、资料与认证域
| 表名 | 用途定位 | 主代码归属 | 规模（行 / 体量） | 分类 | 批次 | 当前建议 |
|---|---|---|---|---|---|---|
| `users` | 全站账号真相层，承接登录、角色、主身份信息 | `UserRepository`、`AdminDashboardRepository` | 39 / 48 KB | 核心真相表 | `A1` | 保留并最优先迁移，是绝大多数外键的根 |
| `student_profiles` | 学生补充资料、头像、偏好、公开展示入口 | `StudentProfileRepository` | 7 / 64 KB | 核心真相表 | `A1` | 保留并优先迁移 |
| `student_profile_privacy_settings` | 学生公开资料隐私覆盖配置 | `StudentProfileRepository` | 0 / 32 KB | 配置/策略表 | `A1` | 保留；纳入复核池，确认是否长期需要独立表 |
| `student_profile_security_codes` | 学生资料安全验证码与发送节流 | `StudentProfileSecurityCodeRepository` | 0 / 48 KB | 生命周期/辅助表 | `A1` | 保留；纳入复核池，后续可评估并入统一验证码体系 |
| `mentor_profiles` | 导师扩展资料、认证状态、经营信息 | `MentorRepository`、`AdminMentorOpsRepository` | 28 / 64 KB | 核心真相表 | `A1` | 保留并优先迁移 |
| `enterprise_profiles` | 企业扩展资料、公开展示、任务方身份补充 | `EnterpriseProfileRepository`、`BountyRepository` | 2 / 32 KB | 核心真相表 | `A1` | 保留并优先迁移 |
| `certification_submissions` | 导师/企业认证提交与审核主记录 | `CertificationRepository` | 31 / 80 KB | 核心真相表 | `A2` | 保留；与 profile 域一起迁移 |
| `certification_submission_assets` | 认证材料附件对象引用 | `CertificationRepository` | 0 / 48 KB | 生命周期/辅助表 | `A2` | 保留；纳入复核池，当前禁止删除或折叠到 JSON |

### 4.2 成长与技能域
| 表名 | 用途定位 | 主代码归属 | 规模（行 / 体量） | 分类 | 批次 | 当前建议 |
|---|---|---|---|---|---|---|
| `checkins` | 学生签到事实与连续签到状态 | `GrowthRepository` | 4 / 32 KB | 核心真相表 | `B1` | 保留；已完成 JPA/PG pilot，签到日期绑定例外见 `change_request_log.md` |
| `growth_checkin_reward_rules` | 签到奖励规则配置 | `GrowthRepository` | 2 / 48 KB | 配置/策略表 | `B1` | 保留；已完成 JPA/PG pilot，继续作为规则表单独维护 |
| `daily_tasks` | 成长任务模板/积分任务配置 | `GrowthRepository` | 2 / 32 KB | 配置/策略表 | `B1` | 保留；已完成 JPA/PG pilot，不建议现在并入其他表 |
| `points_ledger` | 积分流水与余额事实 | `GrowthRepository` | 42 / 32 KB | 核心真相表 | `B1` | 保留；已完成 JPA/PG pilot，是积分真相层 |
| `skills` | 技能树节点主表 | `SkillRepository`、`AdminSkillOpsRepository` | 122 / 64 KB | 核心真相表 | `B2` | 保留；已完成 JPA/PG pilot，是当前已验证的首批样板域 |
| `skill_relations` | 技能树关系边 | `SkillRepository`、`AdminSkillOpsRepository` | 37 / 48 KB | 核心真相表 | `B2` | 保留；与 `skills` 同批迁移 |
| `skill_node_resources` | 技能节点学习资源关联 | `SkillRepository`、`AdminSkillOpsRepository` | 148 / 96 KB | 生命周期/辅助表 | `B2` | 保留；与技能域一起迁移 |
| `skill_progress` | 学生技能进度事实 | `SkillRepository`、`GrowthRepository` | 8 / 48 KB | 核心真相表 | `B2` | 保留；与 `skills` 同批迁移 |

### 4.3 导师供给、咨询与支付域
| 表名 | 用途定位 | 主代码归属 | 规模（行 / 体量） | 分类 | 批次 | 当前建议 |
|---|---|---|---|---|---|---|
| `mentor_service_packages` | 导师服务套餐配置 | `MentorServicePackageRepository`、`AdminMentorOpsRepository` | 28 / 48 KB | 核心真相表 | `C1` | 保留；已完成 JPA/PG pilot，作为供给侧基础表继续保留 |
| `mentor_schedule_slots` | 导师可预约时段 | `MentorScheduleRepository`、`AdminMentorOpsRepository` | 102 / 64 KB | 核心真相表 | `C1` | 保留；已完成 JPA/PG pilot，事务、重复约束与时间窗口语义已验证 |
| `mentor_favorites` | 学生收藏导师关系 | `MentorFavoriteRepository`、`MentorService` | 15 / 64 KB | 生命周期/辅助表 | `C1` | 保留；已完成 JPA/PG pilot，并已随导师主仓储与导师广场/资料链路收口 |
| `consult_orders` | 咨询订单主单表 | `ConsultRepository`、`AdminPaymentRepository`、`MentorFinanceRepository` | 39 / 144 KB | 核心真相表 | `C2` | 保留；咨询交易主链已完成 JPA 收口，后续重点只剩金额/售后回归与独立运维切流 |
| `consult_messages` | 咨询消息正文 | `ConsultRepository` | 47 / 48 KB | 核心真相表 | `C2` | 保留；与订单同批迁移 |
| `consult_order_attachments` | 订单附件对象引用 | `ConsultRepository` | 20 / 80 KB | 生命周期/辅助表 | `C2` | 保留；不建议现在折叠入订单 JSON |
| `consult_reviews` | 咨询评价与服务反馈 | `ConsultRepository`、`MentorRepository` | 16 / 64 KB | 核心真相表 | `C2` | 保留 |
| `consult_after_sales_requests` | 售后申请与处理状态 | `ConsultAfterSalesRepository`、`ConsultRepository` | 11 / 80 KB | 核心真相表 | `C2` | 保留；已完成 JPA/PG pilot，并已随咨询主链整体收口 |
| `payment_records` | 支付记录与支付状态 | `AdminPaymentRepository`、`ConsultRepository`、`MentorFinanceRepository` | 66 / 48 KB | 核心真相表 | `C2` | 保留；金额链路禁止提前重构 |
| `mentor_withdrawal_requests` | 导师提现申请与审核状态 | `MentorFinanceRepository`、`AdminMentorOpsRepository` | 10 / 32 KB | 核心真相表 | `C2` | 保留；资金相关，不能作为首批 pilot |

### 4.4 企业任务与交付域
| 表名 | 用途定位 | 主代码归属 | 规模（行 / 体量） | 分类 | 批次 | 当前建议 |
|---|---|---|---|---|---|---|
| `bounty_tasks` | 企业任务/悬赏主表 | `BountyRepository`、`AdminBountyOpsRepository` | 18 / 96 KB | 核心真相表 | `D1` | 保留；依赖 `users`、`enterprise_profiles` |
| `bounty_submissions` | 学生任务提交主表 | `BountyRepository`、`AdminBountyOpsRepository` | 28 / 96 KB | 核心真相表 | `D1` | 保留；与任务同批迁移 |
| `bounty_submission_events` | 提交状态变化事件 | `BountyRepository` | 43 / 64 KB | 日志/事件表 | `D1` | 保留；迁移后补 retention 规则 |

### 4.5 社区与内容治理域
| 表名 | 用途定位 | 主代码归属 | 规模（行 / 体量） | 分类 | 批次 | 当前建议 |
|---|---|---|---|---|---|---|
| `posts` | 社区帖子主表 | `CommunityRepository`、`CommunityService` | 31 / 80 KB | 核心真相表 | `E1` | 保留；社区主真相层 |
| `comments` | 帖子评论/回复主表 | `CommunityRepository`、`CommunityService` | 30 / 64 KB | 核心真相表 | `E1` | 保留 |
| `post_likes` | 帖子点赞关系 | `CommunityRepository` | 58 / 64 KB | 生命周期/辅助表 | `E1` | 保留；与帖子评论一起迁移 |
| `content_reports` | 举报主记录 | `ContentGovernanceRepository` | 5 / 64 KB | 核心真相表 | `E2` | 保留；治理主真相层 |
| `content_report_actions` | 举报处理动作流水 | `ContentGovernanceRepository` | 3 / 32 KB | 日志/事件表 | `E2` | 保留；禁止与 `content_reports` 直接合并 |
| `content_moderation_events` | 内容治理事件留痕 | `ContentGovernanceRepository`、`AdminDashboardRepository` | 219 / 96 KB | 日志/事件表 | `E2` | 保留；后续加保留期策略 |
| `moderation_policies` | 内容治理策略配置 | `ContentGovernanceRepository` | 4 / 32 KB | 配置/策略表 | `E2` | 保留 |
| `sensitive_terms` | 敏感词库与白名单配置 | `ContentGovernanceRepository` | 81 / 64 KB | 配置/策略表 | `E2` | 保留 |

### 4.6 通知域
| 表名 | 用途定位 | 主代码归属 | 规模（行 / 体量） | 分类 | 批次 | 当前建议 |
|---|---|---|---|---|---|---|
| `notifications` | 用户通知主表 | `NotificationRepository`、`NotificationService` | 98 / 160 KB | 核心真相表 | `F1` | 保留；已完成 JPA/PG pilot，是通知中心的核心真相层 |
| `notification_events` | 通知事件流水 | `NotificationEventRepository` | 35 / 128 KB | 日志/事件表 | `F1` | 保留；已完成 JPA/PG pilot，若未来需要 retention 再单开治理专题 |
| `notification_preferences` | 用户通知偏好设置 | `NotificationPreferenceRepository` | 13 / 48 KB | 配置/策略表 | `F1` | 保留；已完成 JPA/PG pilot，可作为通知域配置入口 |
| `notification_dispatch_jobs` | 通知渠道派发任务队列 | `NotificationDispatchJobRepository`、`AdminNotificationOpsRepository` | 0 / 80 KB | 生命周期/辅助表 | `F1` | 保留；纳入复核池，但当前绝不能删除 |
| `notification_dispatch_attempts` | 派发尝试/重试明细 | `NotificationDispatchJobRepository` | 0 / 32 KB | 日志/事件表 | `F1` | 保留；纳入复核池，后续再评估是否需要独立表 |

### 4.7 AI 网关、面试与异步任务域
| 表名 | 用途定位 | 主代码归属 | 规模（行 / 体量） | 分类 | 批次 | 当前建议 |
|---|---|---|---|---|---|---|
| `ai_gateway_runtime_settings` | AI 网关运行时总配置 | `AiGatewayRuntimeSettingRepository`、`AiGatewayRuntimeSettingsService` | 2 / 16 KB | 配置/策略表 | `G1` | 保留；已完成 JPA/PG pilot，并已作为 AI 网关配置域样板收口 |
| `ai_provider_configs` | Provider 配置主表 | `AiGatewayAdminRepository` | 2 / 32 KB | 配置/策略表 | `G1` | 保留 |
| `ai_provider_models` | Provider 模型清单 | `AiGatewayAdminRepository` | 4 / 48 KB | 配置/策略表 | `G1` | 保留 |
| `ai_model_routes` | 模型路由配置 | `AiGatewayAdminRepository` | 10 / 96 KB | 配置/策略表 | `G1` | 保留；PostgreSQL 下重点改造 upsert 语义 |
| `prompt_templates` | AI 提示词模板与版本入口 | `AiGatewayAdminRepository` | 8 / 80 KB | 配置/策略表 | `G1` | 保留 |
| `ai_scene_route_policies` | 场景级路由/思考量策略 | `AiGatewayAdminRepository` | 11 / 64 KB | 配置/策略表 | `G1` | 保留 |
| `ai_quota_policies` | AI 配额/权益策略 | `AiQuotaPolicyRepository`、`AiQuotaAdminService`、`AiQuotaService` | 14 / 48 KB | 配置/策略表 | `G1` | 保留；已完成 JPA/PG pilot，并已随 AI 配额配置域收口 |
| `ai_call_logs` | AI 调用、成本、路由命中与回放日志 | `AiHistoryRepository`、`AiQuotaRepository`、`AdminDashboardRepository` | 139 / 384 KB | 日志/事件表 | `G2` | 保留；后续优先做 `JSONB` 与保留期治理 |
| `ai_async_task_jobs` | 异步 AI 任务主表，持有上下文与结果快照 | `AiAsyncTaskRepository` | 5 / 3.58 MB | 生命周期/辅助表 | `G2` | 保留；已完成 JPA/PG pilot，并已通过 `V044` 收口为 `JSONB` + 默认 30 天 retention |
| `ai_async_task_events` | 异步 AI 任务事件流水 | `AiAsyncTaskRepository` | 26 / 128 KB | 日志/事件表 | `G2` | 保留；已完成 JPA/PG pilot，并已随 `V044` 收口为 `JSONB` + 任务级联清理 |
| `interview_sessions` | AI 面试会话主表 | `AiInterviewRepository`、`AiHistoryRepository` | 17 / 112 KB | 核心真相表 | `G3` | 保留；注意 session context JSON 迁移 |
| `interview_messages` | AI 面试消息正文 | `AiInterviewRepository` | 106 / 96 KB | 核心真相表 | `G3` | 保留；与会话同批迁移 |

### 4.8 画像与推荐域
| 表名 | 用途定位 | 主代码归属 | 规模（行 / 体量） | 分类 | 批次 | 当前建议 |
|---|---|---|---|---|---|---|
| `student_portrait_snapshots` | 学生画像快照 | `StudentProfileRepository`、`AdminDashboardRepository` | 7 / 48 KB | 衍生快照/缓存表 | `H1` | 保留；等核心真相稳定后再迁 |
| `student_recommendation_snapshots` | 学生推荐输入快照 | `MentorRecommendationRepository` | 2 / 48 KB | 衍生快照/缓存表 | `H1` | 保留；暂不和画像表合并 |
| `mentor_recommendation_snapshots` | 导师推荐输入快照 | `MentorRecommendationRepository` | 24 / 48 KB | 衍生快照/缓存表 | `H2` | 保留 |
| `recommendation_embedding_vectors` | 推荐向量缓存 | `MentorRecommendationRepository` | 26 / 128 KB | 衍生快照/缓存表 | `H2` | 保留；切到 PostgreSQL 后可再评估 `pgvector` |
| `mentor_recommendation_runs` | 推荐运行主记录 | `MentorRecommendationRepository` | 31 / 32 KB | 日志/事件表 | `H2` | 保留；不要与事件表合并 |
| `mentor_recommendation_events` | 推荐过程事件明细 | `MentorRecommendationRepository` | 607 / 240 KB | 日志/事件表 | `H2` | 保留；迁移后优先加 retention |

### 4.9 平台配置、系统审计与元数据
| 表名 | 用途定位 | 主代码归属 | 规模（行 / 体量） | 分类 | 批次 | 当前建议 |
|---|---|---|---|---|---|---|
| `feature_flags` | 运行时功能开关 | `FeatureFlagRepository` | 7 / 48 KB | 配置/策略表 | `S1` | 保留；作为基础配置表尽早迁移 |
| `audit_logs` | 跨域后台/治理/支付审计日志 | `ContentGovernanceRepository`、`ConsultRepository`、`AdminPaymentRepository` | 27 / 48 KB | 日志/事件表 | `S1` | 保留；作为跨域审计表单独治理 |
| `flyway_schema_history` | Flyway 执行元数据 | Flyway runtime | 39 / 32 KB | 系统元数据 | `S0` | 不做业务迁移；PostgreSQL 侧应从新基线重新生成 |

## 5. 迁移批次建议
### 5.1 建议顺序
推荐采用以下顺序推进，而不是按“文件多/表少”随机挑选：

1. `S0` PostgreSQL 基线与系统元数据重建
2. `S1` 平台配置与跨域审计
3. `A1-A2` 账号、资料、认证域
4. `B1-B2` 成长与技能域
5. `C1-C2` 导师供给、咨询与支付域
6. `D1` 企业任务域
7. `E1-E2` 社区与内容治理域
8. `F1` 通知域
9. `G1-G3` AI 网关、面试与异步任务域
10. `H1-H2` 画像与推荐域

### 5.2 各批次目标
| 批次 | 目标 | 主要表 | 说明 |
|---|---|---|---|
| `S0` | 建立 PostgreSQL 新 Flyway 基线 | `flyway_schema_history` | 不迁历史元数据，只在 PG 建新链 |
| `S1` | 保底恢复平台配置与跨域审计 | `feature_flags`、`audit_logs` | 先恢复运行时开关与审计骨架 |
| `A1` | 先站稳身份主干 | `users`、`student_profiles`、`mentor_profiles`、`enterprise_profiles` | 所有业务外键的上游依赖 |
| `A2` | 补认证闭环 | `certification_submissions`、`certification_submission_assets` | 附件对象引用与审核状态独立保留 |
| `B1` | 迁移成长真相与规则 | `checkins`、`points_ledger`、`daily_tasks`、`growth_checkin_reward_rules` | 低资金风险，可先切核心流水；当前已完成 `GrowthRepository` 的 JPA/PG pilot |
| `B2` | 迁移技能树与学习进度 | `skills`、`skill_relations`、`skill_node_resources`、`skill_progress` | 建议作为首批 ORM pilot 域 |
| `C1` | 迁导师供给侧 | `mentor_service_packages`、`mentor_schedule_slots`、`mentor_favorites` | 先补服务供给与排期 |
| `C2` | 迁咨询交易链路 | `consult_*`、`payment_records`、`mentor_withdrawal_requests` | 涉及金额、售后与锁语义，不能抢跑 |
| `D1` | 迁企业任务闭环 | `bounty_*` | 依赖账号/企业资料已稳定 |
| `E1` | 迁社区主真相层 | `posts`、`comments`、`post_likes` | 用户侧可见内容主链路 |
| `E2` | 迁举报与治理体系 | `content_*`、`moderation_policies`、`sensitive_terms` | 审计与治理动作保持分表 |
| `F1` | 迁通知中心与派发骨架 | `notifications`、`notification_*` | 当前已先完成 `notification_preferences`、`notification_events`、`notifications`，仍禁止把派发表和主通知表折叠 |
| `G1` | 迁 AI 网关配置真相层 | `ai_provider_*`、`ai_model_routes`、`prompt_templates`、`ai_scene_route_policies`、`ai_quota_policies`、`ai_gateway_runtime_settings` | 先恢复后台可维护配置；当前已先完成 `ai_gateway_runtime_settings` 与 `ai_quota_policies`，其余配置表继续拆批 |
| `G2` | 迁 AI 日志与异步任务 | `ai_call_logs`、`ai_async_task_*` | `ai_call_logs` 已由 `AiQuotaRepository` / `AiHistoryRepository` / `AiGatewayAdminAnalyticsRepository` 逐步去 SQL 化；`ai_async_task_*` 已完成 JPA 迁移与 `JSONB + retention` 收口，后续重点转为持续监控保留期与存储增长 |
| `G3` | 迁 AI 面试会话真相层 | `interview_sessions`、`interview_messages` | 需验证 JSON context 与流式链路 |
| `H1` | 迁学生画像与输入快照 | `student_portrait_snapshots`、`student_recommendation_snapshots` | 属于衍生数据，后迁更稳 |
| `H2` | 迁导师推荐快照、向量与运行留痕 | `mentor_recommendation_*`、`recommendation_embedding_vectors` | 依赖上游真相层和 AI/技能数据全部稳定 |

### 5.3 首批 ORM Pilot 事实与后续建议
历史上不建议把首批试点直接放在以下域，这一判断当前仍成立：

- `users/auth`：依赖太广，任何建模误差都会扩散到全站。
- `consult/payment`：金额链路、售后和后台对账风险过高。
- `完整通知派发域`：`notification_dispatch_jobs` / `notification_dispatch_attempts` 的异步派发与 WebSocket ack 语义复杂。
- `ai_gateway`：配置、运行日志和模板版本耦合较强。

实际落地后，当前已完成并通过验证的 pilot 范围包括：

- `S1` 平台基础配置：`feature_flags`
- `A1` 依赖主干：`users`、`student_profiles`、`student_profile_privacy_settings`、`student_profile_security_codes`、`enterprise_profiles`
- `A2` 认证域：`certification_submissions`、`certification_submission_assets`
- `B1` 成长域：`checkins`、`points_ledger`、`daily_tasks`、`growth_checkin_reward_rules`
- `B2` 技能域：`skills`、`skill_relations`、`skill_node_resources`、`skill_progress`
- `C1` 供给侧局部：`mentor_service_packages`、`mentor_schedule_slots`、`mentor_favorites`
- `F1` 通知域已完成入口：`notification_preferences`、`notification_events`、`notifications`，以及运营台读模型 `notification_dispatch_jobs`
- `G1` 配置域局部：`ai_gateway_runtime_settings`、`ai_provider_configs`、`ai_provider_models`、`ai_model_routes`、`prompt_templates`、`ai_scene_route_policies`、`ai_quota_policies`
- `G2` AI 日志链路局部：`ai_call_logs`
- `G3` 文本面试域：`interview_sessions`、`interview_messages`
- `H2` 推荐域局部：`mentor_recommendation_*`、`recommendation_embedding_vectors`
- 管理侧聚合读模型：`AdminDashboardRepository`、`AdminBountyOpsRepository`、`AdminPaymentRepository`

若未来以独立专题重开 PostgreSQL 结构治理或能力增强，更适合作为低风险切入方向的是：

- 低耦合单表或轻聚合对象：优先筛掉会牵连调度/支付状态机、调用日志或多表配置拼装的候选
- 结构治理类专题：优先从 retention、归档、索引与事件表冷热分层这类不改主交易状态机的方向切入

若未来重开独立专题，仍应避免直接抢跑的方向：

- 真实生产 cutover：最终 MySQL dump、PostgreSQL 恢复演练与维护窗口切流
- PostgreSQL 版管理员演示数据：新的 seed / reset / check 专题
- 受控例外回收：Growth `DATE` 绑定、导师排期事务边界等需要单独验证的边界

这些结论当前仍成立，原因是：

- 高耦合交易链路、演示数据重建与生产 cutover 的回归面和回滚成本都显著高于单表/治理专题。
- 事件表、异步任务与真实切流都涉及状态机、数据量或维护窗口，不适合再与普通结构治理混批。
- 已落地的 pilot 模式已经证明“先做边界清晰、风险可控、可独立验证的主题”仍是未来独立专题的最低风险切入方式。

## 6. 当前不建议立即删改的对象
以下对象即使当前看起来“表多、分散、行数少”，也不应在 PostgreSQL 功能对等前做删表/合表：

| 对象组 | 当前原因 | 当前禁止动作 |
|---|---|---|
| `notification_dispatch_jobs` + `notification_dispatch_attempts` | 承担通知派发队列、重试、ack 与后台干预能力 | 禁止合并进 `notifications` 或直接删除 |
| `ai_async_task_jobs` + `ai_async_task_events` | 承担 AI 异步任务快照、状态机和留痕 | 禁止把事件压回主表 JSON 或直接删表 |
| `content_reports` + `content_report_actions` + `content_moderation_events` | 举报主记录、处置动作、治理事件是三种不同视角 | 禁止在迁移前合并成单表 |
| `mentor_recommendation_runs` + `mentor_recommendation_events` + `*_snapshots` + `recommendation_embedding_vectors` | 同时承载输入快照、向量缓存、运行主记录和事件明细 | 禁止为了“减表数量”强行合并 |
| `certification_submission_assets` | 当前虽为 0 行，但附件对象访问链路仍存在 | 禁止直接删除 |
| `student_profile_security_codes` | 当前虽为 0 行，但安全验证码逻辑仍有主仓储 | 禁止直接删除 |

## 7. 当前可进入复核池的对象
下列对象可以在 PostgreSQL 功能对等之后进入复核，但本轮只允许“记录问题”，不允许直接动刀：

| 表 / 表组 | 当前现象 | 后续可复核方向 | 当前结论 |
|---|---|---|---|
| `student_profile_privacy_settings` | 当前 0 行，可能长期走默认公开策略 | 确认是否需要独立用户级覆盖表，或未来折叠到 `student_profiles` | 仅复核，不迁移前不删 |
| `student_profile_security_codes` | 当前 0 行，属于短生命周期安全数据 | 后续可评估并入统一验证码/安全校验体系 | 仅复核，不迁移前不删 |
| `certification_submission_assets` | 当前 0 行，但附件访问链路仍在 | 若未来附件生命周期被大幅简化，可再评估是否改为 submission JSON 引用 | 仅复核，不迁移前不删 |
| `notification_dispatch_attempts` | 当前 0 行，可能尚未在本地样本触发 | 迁移稳定后再根据真实重试明细需求决定是否保留独立表 | 仅复核，不迁移前不删 |
| `ai_async_task_jobs` | 行数极低但体量很大 | PostgreSQL 下优先改 `JSONB`、评估清理/归档策略 | 先保留结构，再做存储治理 |
| `ai_call_logs`、`notification_events`、`content_moderation_events`、`mentor_recommendation_events` | 均属于事件/留痕类表，未来增长会更快 | 迁移完成后统一补 retention、归档、冷热分层策略 | 当前不能因为“事件多”而合并 |
| `recommendation_embedding_vectors` | 当前仍以 JSON 向量缓存为主 | PostgreSQL 上可评估 `pgvector`，但应在推荐链路稳定后再做 | 当前先原样迁移 |

## 8. 当前维护策略
数据库迁移主题完成后，本文档的默认使用方式调整为：

1. 把本 inventory 作为冻结参考基线，而不是继续当作 active 批次排程板。
2. 继续保留 `db/migration-pg` 作为 PostgreSQL 权威基线；若未来要做结构治理，以本文的对象分类和复核池结论为起点。
3. 持续使用 PostgreSQL Testcontainers + H2 业务回归双验证，但不再把“继续扩批迁移”作为当前默认动作。
4. 若未来重新打开数据库主题，只允许按独立专题推进，例如：保留期治理、`pgvector` 评估、生产切流运维演练，或个别受控例外回收。

## 9. 本文当前结论
截至 2026-04-17，当前数据库结构与 inventory 的核心判断如下：

- 当前项目已经完成 MySQL -> PostgreSQL 迁移主线，不再存在 active 的“按批次继续迁库表/仓储”待办。
- 当前确实仍存在可进入复核池的对象，但没有任何一张表已经满足“可直接删除/可直接合并”的证据条件；后续若要做结构治理，应作为独立专题重开。
- 当前这份 inventory 的价值，已经从“指导下一批迁移”转为“为后续 PostgreSQL 结构治理、归档和运维排查提供冻结事实基线”。
