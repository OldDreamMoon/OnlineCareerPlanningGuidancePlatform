# MySQL 到 PostgreSQL 迁移与持久层重构总计划

> **文档状态**：`frozen` · 最后审核：2026-04-17 · 数据库迁移主线已完成；本文件保留为 PostgreSQL 收口事实、回滚边界与后续独立运维动作参考。
>

## 1. 文档目标
本文件最初用于把“数据库从 MySQL 迁移到 PostgreSQL、淘汰主代码中的显式 SQL、并清理当前数据库结构”这三件高风险工作收口为一套可执行、可分阶段验收、可回滚的总计划。当前迁移主线已经完成，本文件转为保留：

- 已落地的迁移事实
- 仍有效的技术边界与回滚抓手
- 与数据库迁移相邻、但已独立到部署运维层的 cutover 注意事项

本文件覆盖：
- 当前数据库与持久层基线
- 迁移边界、冻结项与成功标准
- 分阶段实施计划与每阶段产物
- 关键技术决策、注意事项与高风险点
- 回归验证、切换与回滚方案

本文件不覆盖：
- 某一张表的最终字段级改造细节
- 本轮尚未完成 inventory 前的删表 / 合表定案
- 具体页面视觉或交互设计

## 1.1 当前收口结论
截至 2026-04-17，当前数据库迁移主题已经形成完成态：

- 主代码 `apps/server-java/src/main/java` 已无直接注入 `JdbcTemplate` 的 Repository。
- 默认应用运行链、本地一键脚本、生产部署模板与 PostgreSQL 覆盖恢复脚本均已切到 PostgreSQL。
- 历史 MySQL 管理员演示数据脚本已归档到 [`scripts/db/archive/mysql_admin_demo/README.md`](scripts/db/archive/mysql_admin_demo/README.md)，不再属于当前 PostgreSQL 部署基线。
- 若线上仍存在旧 MySQL 服务器，最终 dump / PostgreSQL 恢复演练 / 维护窗口切流属于独立部署运维动作，不再作为当前数据库迁移主题的 active 待办。

## 2. 当前基线事实
截至 2026-04-17，当前数据库迁移相关事实如下：

- 当前仓库内默认运行配置与生产部署模板均已切到 PostgreSQL，运行配置见 [`application.yml`](apps/server-java/src/main/resources/application.yml) 与 [`docker-compose.infra.prod.yml`](infra/docker/docker-compose.infra.prod.yml)；若线上仍存在旧 MySQL 环境，需在发布窗口内完成数据迁移与切换演练。
- 当前已完成本地 MySQL 全量备份，基线产物位于：
  - `.cache/db-backups/bishe_local_full_backup_20260416T061558Z_schema.sql.gz`
  - `.cache/db-backups/bishe_local_full_backup_20260416T061558Z_full.sql.gz`
  - `.cache/db-backups/bishe_local_full_backup_20260416T061558Z_table_stats.tsv`
  - `.cache/db-backups/bishe_local_full_backup_20260416T061558Z_manifest.txt`
- 当前 PostgreSQL 开发基础设施已收敛到单一 [`docker-compose.dev.yml`](infra/docker/docker-compose.dev.yml)；[`04_start_services.sh`](scripts/dev/04_start_services.sh) 会一键拉起 Redis / MinIO / PostgreSQL。
- 当前本地 PostgreSQL runtime 路径也已接通：[`docker-compose.dev.yml`](infra/docker/docker-compose.dev.yml) 的应用库名已统一为 `bishe`，[`04_start_services.sh`](scripts/dev/04_start_services.sh) 会在旧 `bishe_pg` volume 存在时自动补建 `bishe` 数据库，[`05_start_apps_local.sh`](scripts/dev/05_start_apps_local.sh) 已支持 `--postgres`，并已实测通过 `SPRING_PROFILES_ACTIVE=postgres` 启动 Java 后端完成空库迁移与健康检查。
- 当前 PostgreSQL 容器已验证可启动，版本为 PostgreSQL 16。
- 当前 MySQL 库中已确认至少 63 张表。
- 当前 PostgreSQL 新 migration 链已推进到 [`V044__harden_ai_async_task_jsonb.sql`](apps/server-java/src/main/resources/db/migration-pg/V044__harden_ai_async_task_jsonb.sql)，`db/migration-pg` 当前共 44 份 migration。
- 当前 `apps/server-java/src/main/java/com/bishe/server` 已无直接注入 `JdbcTemplate` 的主代码 Repository。
- 当前 [`ContentGovernanceRepository.java`](apps/server-java/src/main/java/com/bishe/server/governance/ContentGovernanceRepository.java) 已完成举报、待审队列与审计日志等治理状态机的 JPA 收口；主代码 Repository 技术栈替换主线已结束，后续默认转入 schema hardening 与类型例外回收。
- 当前 [`ConsultRepository.java`](apps/server-java/src/main/java/com/bishe/server/consult/repository/ConsultRepository.java) 已在 `a9656a1` / `16f81df` / `3e7ff59` / `ae1c11d` / `ca612b4` 五个 checkpoint 中完成辅助读写、订单状态流转、创单消息附件与复杂列表/详情查询的 JPA 收口，不再属于剩余显式 SQL 仓储集合。
- 当前 PostgreSQL 基线已由 [`V041__sync_consult_create_schema.sql`](apps/server-java/src/main/resources/db/migration-pg/V041__sync_consult_create_schema.sql) 补齐咨询交易主链所需的 `consult_orders` 创单快照字段，以及 `consult_messages / consult_order_attachments` 两张表；咨询域后续无需回头重复做 schema 预补齐。
- 当前治理域已通过 [`ModerationPolicyRepository.java`](apps/server-java/src/main/java/com/bishe/server/governance/ModerationPolicyRepository.java)、[`SensitiveTermRepository.java`](apps/server-java/src/main/java/com/bishe/server/governance/SensitiveTermRepository.java) 与重写后的 [`ContentGovernanceRepository.java`](apps/server-java/src/main/java/com/bishe/server/governance/ContentGovernanceRepository.java) 完成配置子域 + 举报 / 待审 / 审计状态机的 JPA 收口，并通过 [`V043__harden_sensitive_terms_boolean_flags.sql`](apps/server-java/src/main/resources/db/migration-pg/V043__harden_sensitive_terms_boolean_flags.sql) 将 `sensitive_terms.enabled / is_whitelist` 从 `SMALLINT` 回收到 `BOOLEAN`。
- 当前 AI async 域已通过 [`V044__harden_ai_async_task_jsonb.sql`](apps/server-java/src/main/resources/db/migration-pg/V044__harden_ai_async_task_jsonb.sql) 将 `ai_async_task_jobs / ai_async_task_events` 的 JSON 快照字段从 `TEXT` 回收到 `JSONB`，并通过 [`AiAsyncTaskMaintenanceJob.java`](apps/server-java/src/main/java/com/bishe/server/ai/gateway/task/AiAsyncTaskMaintenanceJob.java) 补齐默认 30 天终态任务 retention 清理。
- 当前 Growth `DATE` 绑定例外也已在 H2/PG 双验证下完成最终定性：[`GrowthCheckinJpaRepository.java`](apps/server-java/src/main/java/com/bishe/server/growth/repository/jpa/GrowthCheckinJpaRepository.java) 继续保留极小范围 native SQL + `CAST(:date AS DATE)`，作为当前 H2 `test` profile 下的受控例外；数据库迁移主线不再存在 active 待处理条目。
- 当前 `test` profile 仍基于 H2，并通过 [`application-test.yml`](apps/server-java/src/test/resources/application-test.yml) + [`schema.sql`](apps/server-java/src/test/resources/schema.sql) 模拟 MySQL 行为；使用 `@ActiveProfiles("test")` 的测试类数量约 34 个。
- 当前默认运行时 datasource 已由 [`application.yml`](apps/server-java/src/main/resources/application.yml) 切到 PostgreSQL；[`05_start_apps_local.sh`](scripts/dev/05_start_apps_local.sh) 无参默认走 PostgreSQL，生产部署模板也已统一改为 PostgreSQL infra + `jdbc:postgresql://postgres:5432/bishe`。
- 当前 PostgreSQL/JPA pilot 已完成并验证通过的仓储包括：[`UserRepository.java`](apps/server-java/src/main/java/com/bishe/server/auth/repository/UserRepository.java)、[`StudentProfileRepository.java`](apps/server-java/src/main/java/com/bishe/server/profile/repository/StudentProfileRepository.java)、[`StudentProfileSecurityCodeRepository.java`](apps/server-java/src/main/java/com/bishe/server/profile/repository/StudentProfileSecurityCodeRepository.java)、[`EnterpriseProfileRepository.java`](apps/server-java/src/main/java/com/bishe/server/profile/repository/EnterpriseProfileRepository.java)、[`SkillRepository.java`](apps/server-java/src/main/java/com/bishe/server/skill/repository/SkillRepository.java)、[`AdminSkillOpsRepository.java`](apps/server-java/src/main/java/com/bishe/server/skill/repository/AdminSkillOpsRepository.java)、[`GrowthRepository.java`](apps/server-java/src/main/java/com/bishe/server/growth/repository/GrowthRepository.java)、[`FeatureFlagRepository.java`](apps/server-java/src/main/java/com/bishe/server/featureflag/FeatureFlagRepository.java)、[`CertificationRepository.java`](apps/server-java/src/main/java/com/bishe/server/certification/repository/CertificationRepository.java)、[`MentorServicePackageRepository.java`](apps/server-java/src/main/java/com/bishe/server/mentor/repository/MentorServicePackageRepository.java)、[`MentorScheduleRepository.java`](apps/server-java/src/main/java/com/bishe/server/mentor/schedule/repository/MentorScheduleRepository.java)、[`MentorFavoriteRepository.java`](apps/server-java/src/main/java/com/bishe/server/mentor/repository/MentorFavoriteRepository.java)、[`MentorRecommendationRepository.java`](apps/server-java/src/main/java/com/bishe/server/mentor/repository/MentorRecommendationRepository.java)、[`MentorFinanceRepository.java`](apps/server-java/src/main/java/com/bishe/server/mentor/repository/MentorFinanceRepository.java)、[`AdminMentorOpsRepository.java`](apps/server-java/src/main/java/com/bishe/server/mentor/repository/AdminMentorOpsRepository.java)、[`MentorRepository.java`](apps/server-java/src/main/java/com/bishe/server/mentor/repository/MentorRepository.java)、[`NotificationPreferenceRepository.java`](apps/server-java/src/main/java/com/bishe/server/notification/repository/NotificationPreferenceRepository.java)、[`NotificationEventRepository.java`](apps/server-java/src/main/java/com/bishe/server/notification/repository/NotificationEventRepository.java)、[`NotificationRepository.java`](apps/server-java/src/main/java/com/bishe/server/notification/repository/NotificationRepository.java)、[`AdminNotificationOpsRepository.java`](apps/server-java/src/main/java/com/bishe/server/notification/repository/AdminNotificationOpsRepository.java)、[`NotificationDispatchJobRepository.java`](apps/server-java/src/main/java/com/bishe/server/notification/repository/NotificationDispatchJobRepository.java)、[`AiGatewayRuntimeSettingRepository.java`](apps/server-java/src/main/java/com/bishe/server/ai/gateway/AiGatewayRuntimeSettingRepository.java)、[`AiProviderConfigRepository.java`](apps/server-java/src/main/java/com/bishe/server/ai/gateway/AiProviderConfigRepository.java)、[`AiProviderModelRepository.java`](apps/server-java/src/main/java/com/bishe/server/ai/gateway/AiProviderModelRepository.java)、[`AiModelRouteRepository.java`](apps/server-java/src/main/java/com/bishe/server/ai/gateway/AiModelRouteRepository.java)、[`AiQuotaPolicyRepository.java`](apps/server-java/src/main/java/com/bishe/server/ai/quota/AiQuotaPolicyRepository.java)、[`AiQuotaRepository.java`](apps/server-java/src/main/java/com/bishe/server/ai/quota/AiQuotaRepository.java) 中 `ai_call_logs` 的统计/落库、[`AiInterviewRepository.java`](apps/server-java/src/main/java/com/bishe/server/ai/interview/AiInterviewRepository.java)、[`AiHistoryRepository.java`](apps/server-java/src/main/java/com/bishe/server/ai/history/AiHistoryRepository.java)、[`AiAsyncTaskRepository.java`](apps/server-java/src/main/java/com/bishe/server/ai/gateway/task/AiAsyncTaskRepository.java)、[`AiGatewayAdminAnalyticsRepository.java`](apps/server-java/src/main/java/com/bishe/server/ai/gateway/AiGatewayAdminAnalyticsRepository.java)、[`AdminDashboardRepository.java`](apps/server-java/src/main/java/com/bishe/server/dashboard/AdminDashboardRepository.java)、[`AdminBountyOpsRepository.java`](apps/server-java/src/main/java/com/bishe/server/bounty/repository/AdminBountyOpsRepository.java)、[`BountyRepository.java`](apps/server-java/src/main/java/com/bishe/server/bounty/repository/BountyRepository.java)、[`AdminPaymentRepository.java`](apps/server-java/src/main/java/com/bishe/server/consult/repository/AdminPaymentRepository.java)、[`ConsultAfterSalesRepository.java`](apps/server-java/src/main/java/com/bishe/server/consult/repository/ConsultAfterSalesRepository.java) 与 [`CommunityRepository.java`](apps/server-java/src/main/java/com/bishe/server/community/repository/CommunityRepository.java)。
- 当前最新已公开同步的数据库迁移收口 checkpoint 包括：
  - `9712b6e feat(runtime): 默认切到 PostgreSQL 运行链`
  - `ea557fc fix(ops): 恢复 PostgreSQL 恢复脚本执行权限`
  - `75e854e chore(db): 归档历史 MySQL 管理员演示数据脚本`
  其中 `75e854e` 已将历史 MySQL demo/seed 运维脚本整体移出当前 PostgreSQL 基线。

## 3. 当前问题不是单一“换库”
本次变更不能被视为简单的 JDBC URL 替换。它本质上是三个强耦合改造同时发生：

1. 数据库平台迁移：MySQL -> PostgreSQL。
2. 持久层重构：`JdbcTemplate + 手写 SQL` -> 基于实体/Repository 的非显式 SQL 主路径。
3. 库表治理：盘点过时表、评估历史残留、判断是否可合表或归档。

若把这三件事一次性同时落地，风险会集中爆发在以下方面：

- SQL 方言兼容与 migration 脚本失效
- ORM 映射与现有查询行为不一致
- 测试基线整体失真
- 合表 / 删表后无法快速定位回归来源
- 出现问题时无法判断是“平台迁移问题”“ORM 重构问题”还是“结构治理问题”

因此，本计划明确采用分阶段推进，而不是一次性大爆炸式切换。

## 4. 当前已确认的高风险耦合点
### 4.1 MySQL 方言已深入主业务查询
当前主代码中已存在多个 PostgreSQL 下不能直接执行的 MySQL 语法或强耦合表达。

示例：

- `CAST(... AS CHAR)` 的多态目标关联：集中见 [`ContentGovernanceRepository.java`](apps/server-java/src/main/java/com/bishe/server/governance/ContentGovernanceRepository.java)
- 手写布尔整型与多态目标 join 的混合查询：集中见 [`ContentGovernanceRepository.java`](apps/server-java/src/main/java/com/bishe/server/governance/ContentGovernanceRepository.java)
- `ON DUPLICATE KEY UPDATE`：仍出现在 MySQL 旧 migration/seed 链中，如 [`V050__skill_node_resources.sql`](apps/server-java/src/main/resources/db/migration/V050__skill_node_resources.sql)
- `SUBSTRING_INDEX`：仍出现在 MySQL 旧 migration/seed 链中，如 [`V055__mentor_service_packages.sql`](apps/server-java/src/main/resources/db/migration/V055__mentor_service_packages.sql)

### 4.2 MySQL DDL 已深入 Flyway 与测试 schema
当前 migration 与测试 schema 中大量存在：

- `AUTO_INCREMENT`
- `TINYINT(1)`
- `ENGINE=InnoDB`
- `DEFAULT CHARSET=utf8mb4`
- `COLLATE=utf8mb4_0900_ai_ci`
- `ON DUPLICATE KEY UPDATE`

这意味着后续不能直接复用当前 MySQL migration 链到 PostgreSQL。

### 4.3 H2 测试基线已与未来 PostgreSQL 目标偏离
当前测试不是“数据库无关”的纯逻辑测试，而是依赖 H2 模拟 MySQL 语义。继续保留这套测试基线，会导致：

- PostgreSQL 迁移后测试结果与真实运行不一致
- ORM 映射问题在 H2 下不一定能暴露
- 后续需要双重维护 H2 schema 与 PostgreSQL schema

## 5. 迁移目标与非目标
### 5.1 迁移目标
- 把主业务真相层数据库切换到 PostgreSQL。
- 在主代码中逐步淘汰 `JdbcTemplate + 手写 SQL` 的访问方式。
- 为主要业务域建立稳定的实体模型、Repository 约定和迁移规范。
- 完成数据库结构 inventory，并在功能对等后清理过时表 / 历史残留结构。
- 把测试主基线从 H2 模拟 MySQL 切换到 PostgreSQL 真实语义。

### 5.2 非目标
- 不在第一阶段追求“所有表全部合并成最少数量”。
- 不在未完成 inventory 前删表、改名、合表。
- 不在未完成持久层路线冻结前同时改动所有业务域。
- 不在第一阶段引入数据仓库、CQRS、事件溯源等额外架构复杂度。

## 6. 当前建议冻结的关键决策
### 6.1 总体策略
采用“四段式”推进：

1. 先固化基线与 inventory。
2. 再冻结 ORM / Repository 路线与 PostgreSQL schema 基线。
3. 再按业务域分批迁移，先达成功能对等。
4. 最后才做表治理、合表、删表和最终 cutover。

### 6.2 持久层推荐路线
当前推荐主路线为：

- 核心业务域：`JPA/Hibernate + Spring Data JPA`
- 后续复杂报表 / 运营聚合：优先先转实体 + projection / specification / criteria，不在第一阶段保留新的 `JdbcTemplate` 扩散

不推荐把 `Spring Data JDBC` 作为全项目默认方案，原因：

- 当前项目聚合查询、复杂筛选、后台报表与工作台类 SQL 较多
- 需要较强的关联映射和实体一致性能力
- 若用户目标是“主代码不再保留显式 SQL”，JPA/Hibernate 更符合长期方向

### 6.3 测试路线推荐
当前推荐：

- 放弃把 H2 继续作为数据库迁移后的主要集成测试基线
- 改为 PostgreSQL Testcontainers 或等价真实 PostgreSQL 测试容器
- `schema.sql` 不再继续作为长期主维护入口

### 6.4 表治理冻结规则
在以下条件全部满足前，不得删表 / 合表 / 重命名：

- 该表已完成读写入口映射
- 已确认是否仍被服务端代码、前端流程、管理后台、演示脚本或 migration 依赖
- 已给出回滚 SQL / 迁移 SQL / 数据校验方式
- 已完成 PostgreSQL 对等功能验证

## 7. 成功标准（DoD）
本次迁移最终完成时，至少需要满足：

1. Java 主业务已切换到 PostgreSQL。
2. 主业务 Repository 不再依赖 `JdbcTemplate + 手写 SQL`。
3. H2 不再作为迁移正确性的主判据；PostgreSQL Testcontainers 承担数据库迁移主验证基线。
4. PostgreSQL migration 链可从空库完整初始化。
5. 所有 P0/P1 业务链路完成迁移后回归通过。
6. 结构治理后的表删改可追溯，并具备回滚方案。
7. 保留一个清晰可回退的 MySQL 基线 checkpoint 与数据库 dump。

## 8. 全流程实施计划
### 8.1 阶段 0：基线冻结与回滚抓手
目标：
- 固化“开始大改前”的代码和数据库基线。

当前已完成：
- MySQL 全量备份
- PostgreSQL 开发 infra 准备
- 本文档输出
- 已创建本地 Git checkpoint；当前不做镜像远程同步

产物：
- Git checkpoint commit
- `.cache/db-backups/*`
- 本迁移总计划文档

注意事项：
- 本阶段不改业务持久层，不做 schema 改名 / 合表 / 删除。
- 任何后续不可逆结构变更，都必须先回看本阶段基线。

### 8.2 阶段 1：数据库 inventory 与表用途矩阵
目标：
- 对当前 63 张表完成“用途、入口、状态、治理建议”的结构化盘点。

必须产出：
- 表用途矩阵，字段至少包括：
  - 表名
  - 业务域
  - 主要读入口
  - 主要写入口
  - 是否用户可见
  - 是否管理后台可见
  - 是否仅测试 / 演示依赖
  - 当前数据量级
  - 是否疑似历史残留
  - 是否候选归档
  - 是否候选合并
  - 风险等级

建议分类：
- 账户与资料域
- 导师与推荐域
- 咨询与支付域
- 社区与治理域
- AI 历史 / 面试 / 网关域
- 通知域
- 技能 / 成长域
- 基础设施与审计域

注意事项：
- “引用次数少”不等于“可删除”。
- 行数为 0 不等于“无价值”；可能是生命周期表、配置表或待激活表。
- `flyway_schema_history`、审计表、异步任务表、通知派发表等不应因为看起来“分散”就贸然合并。

### 8.3 阶段 2：持久层与实体建模约定冻结
目标：
- 在动手迁移前，先统一实体建模、事务边界、ID、时间、布尔和 JSON 字段策略。

建议统一规则：
- 主键：优先使用 `BIGINT GENERATED BY DEFAULT AS IDENTITY`
- 布尔：原 `TINYINT(1)` 统一评估为 `BOOLEAN`
- 枚举：先保留 `VARCHAR` + 代码层枚举，不在第一阶段引入 PostgreSQL enum
- JSON：优先迁到 `JSONB`
- 长文本：迁到 `TEXT`
- 时间：默认统一到 `TIMESTAMPTZ`，与 Java `Instant` 语义对齐
- 软删除：保留显式字段，不在第一阶段强行改为归档表

当前不建议在第一阶段引入：
- PostgreSQL 自定义 enum
- 表分区
- 逻辑多租户
- 宽表合并

### 8.4 阶段 3：PostgreSQL schema 基线设计
目标：
- 为 PostgreSQL 建立一套新的 schema 初始化链，而不是直接执行现有 MySQL migration。

建议方案：
- 新建 PostgreSQL 专用 Flyway 基线目录或等价新链路
- 把 MySQL migration 中的业务事实结构重新表达为 PostgreSQL 版本
- 把 seed / demo / upsert 型脚本拆出，不混入基础建表迁移

关键注意事项：
- 当前 MySQL migration 中存在 `DEFAULT CHARSET / COLLATE / ENGINE=InnoDB`，在 PostgreSQL 中必须删除
- `AUTO_INCREMENT` 改为 `IDENTITY`
- `ON DUPLICATE KEY UPDATE` 改为 `ON CONFLICT`
- `DATE_SUB` 改为 `CURRENT_TIMESTAMP - INTERVAL '7 day'`
- `GROUP_CONCAT` 改为 `string_agg`
- `SUBSTRING_INDEX` 需改写为 PostgreSQL 等价表达，必要时重写查询结构

### 8.5 阶段 4：测试基线迁移
目标：
- 让测试对 PostgreSQL 真实语义负责。

建议动作：
- 新增 PostgreSQL Testcontainers 依赖
- 新建 `application-test-postgres.yml` 或直接通过 Testcontainers 动态注入 datasource
- 逐步让现有 `@ActiveProfiles("test")` 集成测试改跑 PostgreSQL
- 停止继续扩张 H2 的 `schema.sql`

完成标准：
- 关键 P0/P1 集成测试已在 PostgreSQL 下通过
- H2 不再是数据库迁移正确性的判定依据

### 8.6 阶段 5：按业务域分批迁移 Repository
原则：
- 一次只迁移一个业务域到“可验证状态”
- 每个批次都要同时完成：
  - Entity/Repository 重建
  - PostgreSQL schema 对应迁移
  - 测试迁移
  - 定向回归

推荐批次如下。

#### 批次 A：账户 / 资料 / 配置基础域
范围建议：
- `users`
- `student_profiles`
- `mentor_profiles`
- `enterprise_profiles`
- `student_profile_privacy_settings`
- `student_profile_security_codes`
- `feature_flags`

原因：
- 业务核心但模型相对直白
- 可作为 JPA 建模规范的样板
- 后续其他业务域都依赖这些基础实体

注意事项：
- 邮箱与角色状态相关索引要明确保留
- 不要在本批次提前动认证资料生命周期表的结构治理

#### 批次 B：技能 / 成长 / 导师基础能力域
范围建议：
- `skills`
- `skill_relations`
- `skill_progress`
- `skill_node_resources`
- `checkins`
- `daily_tasks`
- `growth_checkin_reward_rules`
- `mentor_service_packages`
- `mentor_schedule_slots`
- `mentor_favorites`

原因：
- 查询复杂度中等
- 适合验证树结构、自关联、聚合读模型和布尔字段迁移

注意事项：
- `skills` / `skill_relations` 先保结构，不在本阶段合并
- 种子数据相关 upsert 迁移要特别审慎

#### 批次 C：咨询 / 支付 / 售后域
范围建议：
- `consult_orders`
- `consult_messages`
- `consult_order_attachments`
- `consult_reviews`
- `consult_after_sales_requests`
- `payment_records`
- `points_ledger`
- `mentor_withdrawal_requests`

原因：
- 这是状态机与事务最敏感的一组
- 必须在持久层路线稳定后再迁

注意事项：
- 历史上咨询域查询最重，见 [`ConsultRepository.java`](apps/server-java/src/main/java/com/bishe/server/consult/repository/ConsultRepository.java)；但该仓储现已完成五批 JPA 收口，后续主要通过回归测试维持行为一致性
- [`ConsultAfterSalesRepository.java`](apps/server-java/src/main/java/com/bishe/server/consult/repository/ConsultAfterSalesRepository.java) 已作为单仓储 pilot 完成 JPA/PG/H2 验证，但不要把这一步误判成整个咨询交易域已经解耦完成
- `a9656a1`、`16f81df`、`3e7ff59`、`ae1c11d` 与 `ca612b4` 已把 [`ConsultRepository.java`](apps/server-java/src/main/java/com/bishe/server/consult/repository/ConsultRepository.java) 中的辅助读写、订单状态流转、创单/消息/附件，以及 `findOrderDetail`、学生/导师订单列表、导师工作台与管理员订单列表等复杂读查询全部切到 JPA；当前不要再把咨询交易域当作“剩余显式 SQL 主仓储”
- PostgreSQL 基线已在 `65cf9c9` 的 [`V041__sync_consult_create_schema.sql`](apps/server-java/src/main/resources/db/migration-pg/V041__sync_consult_create_schema.sql) 中补齐 `consult_orders` 创单快照字段，以及 `consult_messages / consult_order_attachments` 两张表；后续继续推进咨询主链时应直接复用该基线，不再回头重复做 schema 预补齐
- 订单号、交易号、幂等键必须在 PostgreSQL 下重新验证唯一约束行为

#### 批次 D：社区 / 治理 / 通知域
范围建议：
- `posts`
- `comments`
- `post_likes`
- `content_moderation_events`
- `content_reports`
- `content_report_actions`
- `moderation_policies`（已完成 JPA pilot）
- `sensitive_terms`（已完成 JPA pilot）
- `notifications`
- `notification_events`
- `notification_dispatch_jobs`
- `notification_dispatch_attempts`
- `notification_preferences`

原因：
- 涉及软删除、聚合查询、治理策略、渠道派发与未读计数
- 与管理后台强耦合，适合在前面几批稳定后再做

注意事项：
- 通知相关表不能因为“数量多”就直接合表，它们承担事实、收件箱、派发任务、派发尝试四类不同职责
- 治理域配置子域与 `content_reports / content_report_actions / content_moderation_events / audit_logs` + review queue 工作流已全部完成 JPA 收口，且 `sensitive_terms.enabled / is_whitelist` 与 AI async `TEXT -> JSONB + retention` 已完成规范回收；Growth `DATE` 绑定例外也已完成定性并转为受控边界
- 内容治理域的查询条件组合多，迁移后要重点做筛选与分页回归

#### 批次 E：AI / 面试 / 历史 / 配额域
范围建议：
- `ai_call_logs`
- `ai_provider_configs`
- `ai_provider_models`
- `ai_model_routes`
- `ai_scene_route_policies`
- `ai_quota_policies`
- `prompt_templates`
- `interview_sessions`
- `interview_messages`
- `ai_async_task_jobs`
- `ai_async_task_events`

原因：
- 该域已积累大量管理后台配置、异步任务和运行日志
- 查询、配置和留痕表交织度高，适合后置

注意事项：
- 这组表不建议先做合并治理，先保证功能对等
- 提示词模板与模型路由是配置真相层，迁移时不能只顾实体映射而忽略运营可维护性
- 当前已先把 [`AiQuotaPolicyRepository.java`](apps/server-java/src/main/java/com/bishe/server/ai/quota/AiQuotaPolicyRepository.java) 从 [`AiQuotaRepository.java`](apps/server-java/src/main/java/com/bishe/server/ai/quota/AiQuotaRepository.java) 中抽离出来承接 `ai_quota_policies`；`AiQuotaRepository` 当前只保留 `ai_call_logs` 的显式 SQL，不要把“策略表已迁移”误判成“AI 配额域已整体完成”

#### 批次 F：推荐 / 管理分析域
范围建议：
- `student_portrait_snapshots`
- `student_recommendation_snapshots`
- `mentor_recommendation_snapshots`
- `recommendation_embedding_vectors`
- `mentor_recommendation_runs`
- `mentor_recommendation_events`
- 管理后台各种分析与聚合查询

原因：
- 这是最适合后置清理和重构的部分
- 部分查询是典型报表型读模型，不适合在主业务还没稳定时强行改写

注意事项：
- 推荐快照 / run / event / vector 四类表当前职责不同，不应在功能尚未跑通前合并
- 管理分析查询可以允许更晚收口，但不允许扩散新的 `JdbcTemplate`

### 8.7 阶段 6：库表治理与清理
目标：
- 在 PostgreSQL 功能对等后，才开始判断删表 / 合表 / 归档。

治理顺序建议：
1. 先判定“明确仍在用”
2. 再判定“明确仅用于历史兼容或旧演示”
3. 最后再讨论“是否可以合并”

合表判定标准：
- 生命周期完全一致
- 写入边界完全一致
- 查询热点高度重叠
- 合并后不会削弱审计、留痕或回溯能力

当前明确不建议优先动手合并的类别：
- 通知事实 / 收件箱 / 派发 / attempt
- AI 异步任务 job / event
- 推荐 snapshot / run / event / vector
- 咨询订单 / 消息 / 附件 / 评价 / 售后

### 8.8 阶段 7：切换演练（Cutover rehearsal）
目标：
- 在真正切流前完成至少一次“从空 PostgreSQL 初始化 + 导数 + 回归”的完整演练。

必须覆盖：
- PostgreSQL 空库初始化
- schema 初始化
- 样例 / 演示数据导入
- 核心业务 smoke
- 回滚演练

### 8.9 阶段 8：正式切换与回滚
切换前条件：
- 所有 P0/P1 业务链路 PostgreSQL 回归通过
- 数据迁移脚本已完成 dry-run
- 当前 checkpoint commit 已确认可回退
- MySQL 备份与恢复演练已可执行

切换步骤建议：
1. 冻结写流或进入维护窗口
2. 再做一次最终 MySQL dump
3. 将最终 MySQL dump 导入 PostgreSQL，并执行 PostgreSQL 恢复/校验脚本
4. 按当前默认 PostgreSQL 配置启动应用
5. 先跑只读 smoke，再开放写流
6. 观察关键指标与错误日志

## 9. 关键注意事项清单
### 9.1 类型映射注意事项
- `TINYINT(1)` 只有在明确语义为布尔时才迁为 `BOOLEAN`
- `rating`、`level`、`sort_no` 等数值枚举不要误迁为布尔
- JSON 建议迁为 `JSONB`
- 以 `Instant` 读写的时间字段应优先迁为 `TIMESTAMPTZ`

### 9.2 约束与索引注意事项
- 当前 MySQL 中依赖默认大小写/排序规则的地方，要在 PostgreSQL 下重新验证
- 不要默认 PostgreSQL 唯一索引与 MySQL 行为完全一致
- 所有邮箱、订单号、业务编码类唯一键，都需要迁移后重跑幂等与重复写入回归

### 9.3 ORM 注意事项
- 不要为了“消灭 SQL 字符串”而在一个 PR 中同时引入复杂双向关联、级联和懒加载地狱
- 先优先保证实体聚合边界稳定，再逐步抽象查询
- 复杂报表读取允许阶段性使用专门的 projection 查询实现，但不允许继续扩散 `JdbcTemplate`

### 9.4 migration 注意事项
- 现有 MySQL migration 不能直接复制改名为 PostgreSQL migration
- 种子数据 / 演示数据 / schema 初始化要分开
- `ON CONFLICT` 的唯一键依据必须和业务幂等语义一致，不能机械替换

### 9.5 表治理注意事项
- 不以“空表”“低引用”“看起来能并”作为删改依据
- 先建立证据矩阵，再做结构动作
- 任何删表或合表都必须单独出 change request 与回滚方案

## 10. 回归与验收策略
每个迁移批次都至少要完成以下验证：

1. PostgreSQL schema 初始化成功
2. 对应业务域的集成测试通过
3. 核心 API smoke 通过
4. 关键后台列表 / 详情 / 筛选 / 分页行为一致
5. 关键事务链路无明显行为偏差
6. 关键唯一键 / 并发 / 幂等行为通过

专项回归：
- 账户注册 / 登录 / 权限
- 导师广场 / 详情 / 收藏
- 咨询下单 / 支付 / 回复 / 售后 / 评价
- 社区发帖 / 评论 / 点赞 / 举报 / 审核
- 通知收件箱 / WebSocket / 派发 / ACK
- AI 面试 / 历史 / 配额 / 管理配置
- 管理后台分析与治理页

## 11. 回滚方案
### 11.1 代码回滚
- 使用本轮 checkpoint commit 作为大改前代码基线
- 若某一批次失败，优先 `git revert <commit>` 回退该批次，而不是 `reset --hard`

### 11.2 数据回滚
- 默认运行链已切到 PostgreSQL；真正上线切流前，MySQL dump 与历史 checkpoint 继续保留为权威回退源
- 出现不可恢复问题时：
  1. 停止 PostgreSQL 版应用
  2. 回退代码到 checkpoint 或上一稳定批次
  3. 恢复 PostgreSQL 快照；若 PG 数据本身不可救，再回退到 MySQL 历史快照与旧 checkpoint
  4. 视情况重新导入最近一次 MySQL dump

### 11.3 结构治理回滚
- 任何删表 / 合表动作都必须预留对应反向脚本
- 未提供反向脚本前，不允许执行破坏性结构治理

## 12. 本文档已输出的关联产物
数据库迁移主题收口后，本文档对应的关键产物已经形成：

- 表用途矩阵文档
- PostgreSQL 类型映射对照表
- JPA/实体建模约定文档
- PostgreSQL 测试基线迁移方案
- 分批迁移任务清单与库存盘点
- PostgreSQL 覆盖恢复与部署脚本

对应文件包括：

- [`47_DB_TABLE_INVENTORY_AND_MIGRATION_BATCHES.md`](docs/spec/47_DB_TABLE_INVENTORY_AND_MIGRATION_BATCHES.md)
- [`48_POSTGRESQL_CONVENTIONS_AND_SKILL_PILOT_PLAN.md`](docs/spec/48_POSTGRESQL_CONVENTIONS_AND_SKILL_PILOT_PLAN.md)
- [`09_DEPLOYMENT_RUNBOOK.md`](docs/spec/09_DEPLOYMENT_RUNBOOK.md)
- [`restore_prod_db_from_dump.sh`](scripts/db/restore_prod_db_from_dump.sh)

## 13. 当前收口结论
当前数据库迁移主题的正式结论如下：

1. 数据库迁移主线已经完成，不再存在 active 的“剩余显式 SQL 仓储替换”待办。
2. 当前仍保留的 Growth `DATE` 绑定 native SQL + `CAST(:date AS DATE)` 属于受控例外，而不是未完成项。
3. 真实服务器最终 MySQL dump / PostgreSQL 恢复演练 / 维护窗口切流属于独立部署运维动作，应按运行手册执行，不再把它挂在数据库迁移主题下继续追踪。
4. 若未来还要继续打开数据库相关工作，只能按独立专题重开，例如：
   - PostgreSQL 生产切流演练
   - PostgreSQL 版管理员演示数据 seed
   - H2 退出或 Hibernate 升级后的 Growth `DATE` 绑定例外回收
