# 管理员后台重构蓝图（中文）

> **文档状态**：`evolving` · 最后审核：2026-04-09
>
> **适用范围**：管理员后台 `/admin/*`
>
> **用途**：基于当前真实代码与已落地前台能力，重新定义管理员后台下一轮重构的目标信息架构、治理域边界、实施优先级与交接方式，确保后续 Agent 可以不依赖聊天记录，直接从文档继续执行。
>
> **关联文档**：
> - [`14_FRONTEND_ROUTES_AND_PAGES.md`](./14_FRONTEND_ROUTES_AND_PAGES.md)
> - [`15_FRONTEND_REFACTOR_CHECKLIST.md`](./15_FRONTEND_REFACTOR_CHECKLIST.md)
> - [`06_AI_MODULE_SPEC.md`](./06_AI_MODULE_SPEC.md)
> - [`.agent_tracking/state_snapshot.yaml`](.agent_tracking/state_snapshot.yaml)
> - [`.agent_tracking/NEXT_SESSION_HANDOFF.md`](.agent_tracking/NEXT_SESSION_HANDOFF.md)

## 1. 文档目标

本文件不是接口清单，也不是当前后台页面的改样式备注。

它的目标是：

- 基于当前真实代码，重新判断管理员后台已经落后于哪些业务域
- 明确下一轮管理员后台不应继续沿用“早期 6 个页面”的旧信息架构
- 给后续 Agent 一份可直接接续执行的重构蓝图，而不是只能从聊天记录里恢复背景
- 把 AI 专项、通知运营、企业任务治理、导师经营治理、技能资源治理等关键域放回平台级治理视角

一句话总结：

`管理员后台下一轮重构的核心，不是继续补旧后台页面，而是按前台真实业务域重建平台治理后台。`

## 2. 当前背景与核心判断

### 2.1 当前项目状态

- 前台学生、导师、企业三角色主链路已基本落地，当前默认只承接少量回归修补
- 管理员后台是项目早期的初版实现，覆盖面与抽象层级仍停留在“基础运营控制台”阶段
- 继续只围绕当前 `/admin/dashboard`、内容治理、售后对账、AI 网关等旧菜单做增量修补，已经无法反映真实平台全貌

### 2.2 当前管理员后台真实落地范围

当前管理端前端只正式暴露以下一级域：

- `dashboard`
- `content`
- `orders`
- `ai-gateway`
- `users`
- `feature-flags`

对应代码见：

- [`apps/web/src/adminNavigation.ts`](apps/web/src/adminNavigation.ts)
- [`apps/web/src/App.tsx`](apps/web/src/App.tsx)

### 2.3 当前前台真实业务版图

前台已经明显超出上述后台范围，至少包括：

- 学生：工作台、资料中心、AI 简历、AI 面试、AI 复盘中心、通知中心、导师广场、咨询创单/订单、企业任务大厅/详情、技能星图、社区、公开个人页
- 导师：工作台、资料与认证、多套餐、排期、订单中心、履约工作区、财务中心、社区
- 企业：工作台、资料与认证、Logo、通知偏好、任务中心、任务发布/编辑、任务审核工作区
- 共享：统一通知中心、统一鉴权、角色路由分流

对应代码与文档见：

- [`apps/web/src/App.tsx`](apps/web/src/App.tsx)
- [`14_FRONTEND_ROUTES_AND_PAGES.md`](./14_FRONTEND_ROUTES_AND_PAGES.md)

### 2.4 重构结论

因此，管理员后台当前的主要问题不是“某个页面交互旧了”，而是：

- 覆盖域不足
- 后台对象模型落后于真实前台业务对象
- 很多能力已有后端基础，但尚未形成 admin 产品化工作台
- AI 管理仍偏技术网关视角，缺真实业务应用运营视角

## 3. 当前真实基线

### 3.1 当前管理员侧已有能力

前端现有正式页面：

- `/admin/dashboard`
- `/admin/content`
- `/admin/consult/orders`
- `/admin/ai/gateway`
- `/admin/users`
- `/admin/users/reviews`
- `/admin/feature-flags`

后端现有 admin 命名空间：

- `/api/v1/admin/dashboard`
- `/api/v1/admin/users`
- `/api/v1/admin/content`
- `/api/v1/admin/feature-flags`
- `/api/v1/admin/system`
- `/api/v1/admin/ai`
- `/api/v1/admin/consult/orders`
- `/api/v1/admin/consult/after-sales/requests`
- `/api/v1/admin/payments/reconciliation`
- `/api/v1/admin/notifications`
- `/api/v1/admin/growth`

这些能力足以说明管理员后台并非空白，但仍只是“基础治理底盘”。

### 3.2 当前管理员后台未覆盖但前台已落地的关键域

- 企业任务/悬赏治理
- 导师经营治理
- 统一通知运营
- 技能星图与资源治理
- AI 应用渠道治理
- 更完整的运行配置与降级控制
- 面向三角色真实业务的总览看板

### 3.3 当前可直接复用的非 admin 后端能力

虽然这些域缺正式 admin 工作台，但后端已存在可利用的基础能力：

- 企业任务域：[BountyController.java](apps/server-java/src/main/java/com/bishe/server/bounty/api/BountyController.java)
- 导师财务域：[MentorFinanceController.java](apps/server-java/src/main/java/com/bishe/server/mentor/api/MentorFinanceController.java)
- 技能树域：[SkillController.java](apps/server-java/src/main/java/com/bishe/server/skill/api/SkillController.java)
- 通知公告入口：[AdminNotificationController.java](apps/server-java/src/main/java/com/bishe/server/notification/api/AdminNotificationController.java)
- AI 网关控制台：[AdminAiGatewayController.java](apps/server-java/src/main/java/com/bishe/server/ai/api/AdminAiGatewayController.java)

这意味着下一轮重点不是从零建设全部能力，而是：

- 把已有后端能力重新组织成平台治理后台
- 补足缺失的 admin 聚合接口和运营视图

## 4. 当前后台落后点总清单

### 4.1 总览看板落后于真实业务版图

当前问题：

- workbench 主要统计举报、待审、售后、对账与 AI provider 运行态
- operations dashboard 主要围绕学生激活、咨询转化、AI 调用、社区覆盖和内容治理

对应代码：

- [`apps/server-java/src/main/java/com/bishe/server/dashboard/AdminDashboardService.java`](apps/server-java/src/main/java/com/bishe/server/dashboard/AdminDashboardService.java)

缺口：

- 没有企业任务总量、待处理提交、任务风险与平台干预数据
- 没有导师经营、提现审核、排期风险、履约健康度
- 没有通知派发、失败重试、公告覆盖与邮件 readiness
- 没有技能资源、节点质量与专项 AI 面试运营指标
- 没有 AI 应用渠道级成功率、延迟、成本与错误分布

重构方向：

- 首页拆成“待处理事项总览 + 平台经营总览”两层
- 看板口径由“旧后台模块指标”改为“前台真实业务域指标”

### 4.2 企业任务/悬赏域缺平台治理后台

当前问题：

- 企业任务完整主链路已落地，但平台管理员没有正式治理入口
- 目前只有通用 `/api/v1/bounty/tasks*` 对 ADMIN 开放读取能力，不等于正式 admin 工作台

对应代码：

- [`apps/web/src/pages/EnterpriseDashboardPage.tsx`](apps/web/src/pages/EnterpriseDashboardPage.tsx)
- [`apps/web/src/pages/EnterpriseTaskCenterPage.tsx`](apps/web/src/pages/EnterpriseTaskCenterPage.tsx)
- [`apps/web/src/pages/EnterpriseTaskReviewPage.tsx`](apps/web/src/pages/EnterpriseTaskReviewPage.tsx)
- [`apps/server-java/src/main/java/com/bishe/server/bounty/api/BountyController.java`](apps/server-java/src/main/java/com/bishe/server/bounty/api/BountyController.java)

缺口：

- 没有 `/admin/enterprise/tasks` 或 `/admin/bounty` 工作台
- 没有任务巡检、异常下架、冻结、平台申诉、质量抽检、企业活跃度看板
- 没有平台视角的任务详情页与企业画像聚合

重构方向：

- 新增企业任务治理台，优先做平台巡检与人工干预
- 保持“轻量任务运营台”定位，不扩成完整 ATS

### 4.3 导师经营域缺后台治理

当前问题：

- 导师前台已有多套餐、排期、订单、财务、提现演示流转
- 平台仍缺导师经营治理台

对应代码：

- [`apps/web/src/pages/MentorDashboardPage.tsx`](apps/web/src/pages/MentorDashboardPage.tsx)
- [`apps/web/src/pages/MentorProfilePage.tsx`](apps/web/src/pages/MentorProfilePage.tsx)
- [`apps/web/src/pages/MentorOrderCenterPage.tsx`](apps/web/src/pages/MentorOrderCenterPage.tsx)
- [`apps/web/src/pages/MentorFinancePage.tsx`](apps/web/src/pages/MentorFinancePage.tsx)
- [`apps/server-java/src/main/java/com/bishe/server/mentor/api/MentorFinanceController.java`](apps/server-java/src/main/java/com/bishe/server/mentor/api/MentorFinanceController.java)

缺口：

- 没有导师套餐治理与价格巡检
- 没有排期覆盖风险与可预约性巡检
- 没有平台提现审核台
- 没有履约 SLA、售后压力、导师质量画像的运营工作台

重构方向：

- 新增导师经营治理台
- 第一阶段以“套餐、排期、履约、提现、质量”五类治理对象为核心

### 4.4 通知运营域缺后台页面

当前问题：

- 后端已有管理员公告接口，但前端 admin 菜单没有通知运营页
- 用户侧通知中心已是正式主链路，但后台没有派发、失败、偏好、公告的统一运营台

对应代码：

- [`apps/server-java/src/main/java/com/bishe/server/notification/api/AdminNotificationController.java`](apps/server-java/src/main/java/com/bishe/server/notification/api/AdminNotificationController.java)
- [`apps/web/src/pages/NotificationsPage.tsx`](apps/web/src/pages/NotificationsPage.tsx)

缺口：

- 没有系统公告发布页
- 没有派发失败重试与邮件 readiness
- 没有深链接目标巡检
- 没有按分类/角色查看通知健康度

重构方向：

- 新增 `/admin/notifications`
- 让“公告发布、派发观测、失败重试、偏好巡检”成为同一运营域

### 4.5 技能星图/资源域缺后台内容治理

当前问题：

- 学生技能树已经是正式页面
- 后端技能树与资源数据已经存在
- 但管理员原先没有节点、资源、排序、上下线、演示数据维护后台
- 即使补完后台 CMS，运营仍缺一个不切换学生身份就能快速核对节点实际画布效果的只读预览入口

对应代码：

- [`apps/web/src/pages/SkillsPage.tsx`](apps/web/src/pages/SkillsPage.tsx)
- [`apps/server-java/src/main/java/com/bishe/server/skill/api/SkillController.java`](apps/server-java/src/main/java/com/bishe/server/skill/api/SkillController.java)

缺口：

- 没有节点内容 CMS
- 没有资源条目管理
- 没有专项 AI 面试场景绑定位
- 没有技能树演示数据维护工具
- 没有管理员专用的技能星图结构预览页

重构方向：

- 新增技能资源后台
- 第一阶段先做节点与资源 CMS
- 为技能治理台补一条管理员只读预览链路，优先只校对“背景 + 节点 + 路径 + 详情”，不强绑学生成长 HUD
- 后续再接专项 AI 场景配置

### 4.6 系统设置域过窄

当前问题：

- 当前 feature flag 只有 `payment.mode`、`voice.enabled`、`community.ai-pre-answer.enabled`

对应代码：

- [`apps/server-java/src/main/java/com/bishe/server/featureflag/FeatureFlagService.java`](apps/server-java/src/main/java/com/bishe/server/featureflag/FeatureFlagService.java)

缺口：

- 无法承接多 AI 场景、多业务降级、多模块运行时切换
- 不足以支撑后续管理员后台的真实运行配置中心

重构方向：

- 把系统设置扩成“运行配置中心”
- 拆分为业务开关、运营参数、AI 渠道开关、风险降级开关四类

### 4.7 AI 管理台仍停留在“网关控制台”，不是“AI 应用运营后台”

当前问题：

- 现有 admin AI 页能管理 provider、route、prompt、quota、logs、cost、runtime
- 但真实 AI 调用已经覆盖多个前台业务场景
- 当前后台无法按业务应用渠道聚合观察和配置

对应代码：

- [`apps/web/src/pages/AdminAiGatewayPage.tsx`](apps/web/src/pages/AdminAiGatewayPage.tsx)
- [`apps/server-java/src/main/java/com/bishe/server/ai/gateway/AiGatewayService.java`](apps/server-java/src/main/java/com/bishe/server/ai/gateway/AiGatewayService.java)

真实已落地的 AI 场景至少包括：

- `RESUME / RESUME_OPTIMIZE`
- `COMMUNITY_REPLY / COMMUNITY_PRE_ANSWER`
- `COMMUNITY_REPLY / MENTOR_PREP_SHEET_GENERATE`
- `ICEBREAK / ICEBREAK_MESSAGE`
- `INTERVIEW_TEXT / INTERVIEW_OPENING`
- `INTERVIEW_TEXT / INTERVIEW_REPLY`
- `INTERVIEW_TEXT / INTERVIEW_ANSWER_HELPER`
- `INTERVIEW_SUMMARY / INTERVIEW_SUMMARY`
- `STT / INTERVIEW_VOICE_TRANSCRIBE`
- `TTS / TEXT_TO_SPEECH`

缺口：

- 没有“AI 应用渠道”层
- 不能按渠道查看状态、成本、成功率、模板、路由与配额
- 不能从前台入口视角观察 AI 产品表现

重构方向：

- AI 域必须拆成“AI 网关层 + AI 应用运营层”两层

### 4.8 AI 模板管理 UI 落后于后端能力

当前问题：

- 后端 prompt template 已支持 `templateFormat`、`bundleJson`
- 前端模板表单仍只支持 `content / description / variablesJson`

对应代码：

- [`apps/server-java/src/main/java/com/bishe/server/ai/gateway/AiGatewayAdminService.java`](apps/server-java/src/main/java/com/bishe/server/ai/gateway/AiGatewayAdminService.java)
- [`apps/server-java/src/main/java/com/bishe/server/ai/api/AdminAiGatewayController.java`](apps/server-java/src/main/java/com/bishe/server/ai/api/AdminAiGatewayController.java)
- [`apps/web/src/pages/AdminAiGatewayPage.tsx`](apps/web/src/pages/AdminAiGatewayPage.tsx)

缺口：

- 无法编辑 `MESSAGE_BUNDLE`
- 无法维护 `bundleJson`
- 无法从运营视角理解模板归属到哪个应用渠道
- 缺模板版本差异对比与更完整发布链路

重构方向：

- 把模板管理升级到 2.0

### 4.9 AI 路由管理 UI 落后于后端能力

当前问题：

- 后端 meta 和 route 已支持 `executionMode`
- 前端 meta 未接 `executionModes`
- 路由编辑表单没有 `executionMode`

缺口：

- 同步/流式/异步/实时语义无法在后台明确维护
- 当前只能通过 preview 间接看到命中结果，不利于配置治理

重构方向：

- 路由编辑、列表与详情统一补齐 `executionMode`
- 路由视图要能直接看到“服务哪个应用渠道”

### 4.10 AI 配额模型与真实前台语义存在断层

当前问题：

- AI 网关任务类型已包含 `INTERVIEW_SUMMARY / STT / TTS`
- quota 任务类型仍未完整对齐

对应代码：

- [`apps/server-java/src/main/java/com/bishe/server/ai/gateway/AiGatewayTaskType.java`](apps/server-java/src/main/java/com/bishe/server/ai/gateway/AiGatewayTaskType.java)
- [`apps/server-java/src/main/java/com/bishe/server/ai/quota/AiTaskType.java`](apps/server-java/src/main/java/com/bishe/server/ai/quota/AiTaskType.java)

缺口：

- 语音转写和面试总结等场景无法被后台配额页完整表达
- 后续若要做渠道级 quota，现有任务类型模型不够细

重构方向：

- 先补齐 quota 枚举与网关任务类型对齐
- 第二阶段再考虑按“任务类型 + 应用渠道”扩展更细粒度的配额治理

## 5. 目标信息架构（建议版）

管理员后台下一轮建议按真实治理域重组为以下一级菜单：

1. `总览`
   - `/admin/dashboard`
   - 待处理事项总览 + 平台经营概览
2. `用户与认证`
   - `/admin/users`
   - `/admin/users/reviews`
   - 用户状态、角色画像、认证审核
3. `企业任务`
   - `/admin/enterprise/tasks`
   - 平台任务巡检、异常任务处理、提交质量抽检
4. `导师经营`
   - `/admin/mentors/operations`
   - 套餐、排期、履约、提现、质量画像
5. `通知运营`
   - `/admin/notifications`
   - 公告、派发健康度、失败重试、偏好巡检
6. `AI 应用运营`
   - `/admin/ai/applications`
   - 渠道、模板归属、应用成功率、成本、降级
7. `AI 网关`
   - `/admin/ai/gateway`
   - provider、route、runtime、logs、cost、quota
8. `内容治理`
   - `/admin/content`
   - 举报、待审、策略、词库、审计
9. `支付与售后`
   - `/admin/consult/orders`
   - `/admin/payments/reconciliation`
   - 咨询订单、售后、平台内对账
10. `技能资源`
   - `/admin/skills`
   - 节点、资源、排序、显示状态、演示数据
11. `运行配置`
   - `/admin/runtime`
   - feature flags、降级、运营参数

说明：

- 当前现有的 `feature-flags` 最终建议收口到 `运行配置`
- 当前 `ai-gateway` 需要与新的 `ai/applications` 并存，不能再让 AI 域只剩一个技术控制台

## 6. AI 专项蓝图

### 6.1 必须拆成两层

#### A. AI 网关层

负责：

- provider
- route
- runtime
- logs
- cost
- quota
- 技术排障

#### B. AI 应用运营层

负责：

- 业务渠道
- 场景归属
- 模板族归属
- 渠道启停
- 执行模式
- 应用成功率/失败率/延迟/成本
- 与前台入口和业务模块的映射

### 6.2 AI 应用渠道建议实体

建议新增 `AiApplicationChannel` 概念，至少包含：

- `channelCode`
- `displayName`
- `ownerDomain`
- `frontEntry`
- `taskType`
- `sceneCode`
- `executionMode`
- `routeCode`
- `promptTemplateName`
- `quotaTaskType`
- `enabled`
- `status`
- `recentCalls`
- `successRate`
- `avgLatencyMs`
- `estimatedCost`
- `lastErrorSummary`

### 6.3 首批必须纳入的渠道

建议首批渠道最少覆盖：

- `resume.optimize`
- `community.pre-answer`
- `community.mentor-prep-sheet`
- `icebreak.message`
- `interview.opening`
- `interview.reply`
- `interview.answer-helper`
- `interview.summary`
- `interview.voice-transcribe`
- `interview.tts`

### 6.4 Prompt 模板管理 2.0 必须补齐

必须补：

- `templateFormat`
- `bundleJson`
- `TEXT` 与 `MESSAGE_BUNDLE` 双模式编辑
- 模板变量定义与预览
- 模板版本对比
- 模板发布/回滚留痕
- 渠道归属展示

不应继续停留在：

- 单一文本框编辑 prompt 正文
- 只看 `content` 和 `variablesJson`

### 6.5 Route 管理必须补齐

必须补：

- `executionMode`
- `sceneCode`
- route 服务的应用渠道
- route 与模板族的显式关系
- route 命中预览的业务可读说明

### 6.6 Quota 模型重构建议

第一阶段：

- 先让 quota 任务类型与网关任务类型对齐
- 至少补上 `INTERVIEW_SUMMARY` 与 `STT`

第二阶段：

- 再评估是否需要“任务类型 + 应用渠道”双层配额
- 仅在真实运营需要时再细化，不在当前阶段一次性做过重建模

### 6.7 AI 第一批实施范围建议

第一批 AI 域不追求一次性做完所有后端抽象，建议收口为：

1. 新增 `/admin/ai/applications` 只读聚合页
2. 补齐 `Prompt Template 2.0`
3. 补齐 route `executionMode`
4. quota 任务类型对齐
5. dashboard 接入 AI 渠道级指标摘要

## 7. 分阶段重构清单

### 7.1 P0：必须先做

- 管理员后台新信息架构与菜单重组
- AI 应用运营台
- Prompt 模板管理 2.0
- Route `executionMode` 补齐
- 通知运营台
- 企业任务治理台
- dashboard 指标重算

### 7.2 P1：第二批推进

- 导师经营治理台
- 技能资源后台
- 运行配置中心扩容
- 用户与认证后台的经营画像扩展

### 7.3 P2：后续增强

- 操作审计、版本治理、灰度与回滚
- 跨域 drill-down 分析
- 运营修复工具与批处理能力

## 8. 推荐实施顺序

建议后续 Agent 默认按以下顺序推进，除非用户重新指定优先级：

1. `A1` 管理员信息架构重组
2. `A2` AI 应用运营台首版
3. `A3` Prompt 模板管理 2.0 + route `executionMode`
4. `A4` 通知运营台首版
5. `A5` 企业任务治理台首版
6. `A6` dashboard V2
7. `B1` 导师经营治理台
8. `B2` 技能资源后台

原因：

- AI 是当前平台最具辨识度也最容易被答辩追问的能力
- 通知、企业任务和 dashboard 最能体现“平台治理能力”
- 导师经营和技能资源很重要，但可以在平台治理骨架稳定后再进入

## 9. 可直接接续的任务拆分

### 9.1 `A1-ADMIN-IA-SKELETON`

目标：

- 重组 admin 菜单、路由占位与壳层分组

建议改动点：

- [`apps/web/src/adminNavigation.ts`](apps/web/src/adminNavigation.ts)
- [`apps/web/src/App.tsx`](apps/web/src/App.tsx)
- [`apps/web/src/components/AdminShellLayout.tsx`](apps/web/src/components/AdminShellLayout.tsx)

验收：

- 新一级菜单可访问
- 旧路径兼容跳转不坏
- 当前已存在页面不回退

### 9.2 `A2-AI-APPLICATION-OPS`

目标：

- 新增 `/admin/ai/applications`
- 先做应用渠道聚合视图

建议第一版只读字段：

- 渠道名
- 所属业务域
- 前台入口
- taskType
- sceneCode
- executionMode
- 绑定 route
- 绑定模板
- 启停状态
- 最近调用量
- 成功率
- 平均延迟
- 预估成本

验收：

- 至少覆盖当前 10 个真实 AI 渠道
- 能直接从后台看出“哪个渠道服务哪个前台入口”

### 9.3 `A3-AI-TEMPLATE-AND-ROUTE-UPGRADE`

目标：

- 升级现有 `/admin/ai/gateway`

必须补齐：

- 模板表单支持 `templateFormat`
- 模板表单支持 `bundleJson`
- route 表单支持 `executionMode`
- 列表直接显示 execution mode

验收：

- 与现有后端字段完全对齐
- 不再出现后端已支持、前端不可维护的字段断层

### 9.4 `A4-NOTIFICATION-OPS`

目标：

- 新增 `/admin/notifications`

首版范围：

- 公告发布
- 公告记录
- 派发状态摘要
- 失败重试入口
- 通道 readiness
- 偏好巡检摘要

验收：

- 平台公告发布可从后台完整操作
- 至少能观察公告类通知的投递与失败情况

### 9.5 `A5-ENTERPRISE-TASK-OPS`

目标：

- 新增平台企业任务治理台

首版范围：

- 任务列表
- 任务状态
- 企业信息摘要
- 提交数量
- 风险标记
- 平台干预动作

验收：

- 管理员可从平台视角巡检企业任务，不再依赖企业自身工作台

### 9.6 `A6-DASHBOARD-V2`

目标：

- 重做 `/admin/dashboard`

首版指标族建议：

- 待处理事项：认证、举报、售后、通知失败、任务风险、提现审核
- 平台经营：企业任务、导师经营、AI 渠道、通知运营、技能资源

验收：

- 看板不再只反映旧后台模块
- 能体现平台真实经营现状

## 10. 当前不建议立即做的事

- 不要先把旧 6 个 admin 页面做大规模视觉美化，再来补结构
- 不要先进入复杂 BI 图表、拖拽分析、离线报表系统
- 不要先把企业任务后台扩成 ATS
- 不要先在 AI 域一次性上过重审批流或复杂工作流编排

当前最重要的是：

- 正确重建后台对象模型
- 让前台真实业务域都能在后台找到治理入口

## 11. 交接与恢复规则

后续 Agent 若从任意时刻接手管理员后台主线，默认补读顺序建议为：

1. [`AGENTS.md`](AGENTS.md)
2. [`.agent_tracking/state_snapshot.yaml`](.agent_tracking/state_snapshot.yaml)
3. [`.agent_tracking/NEXT_SESSION_HANDOFF.md`](.agent_tracking/NEXT_SESSION_HANDOFF.md)
4. [`.agent_tracking/task_board.md`](.agent_tracking/task_board.md)
5. 本文档 [`43_ADMIN_BACKOFFICE_REFACTOR_BLUEPRINT.md`](./43_ADMIN_BACKOFFICE_REFACTOR_BLUEPRINT.md)

默认启动动作：

- 先确认用户是否继续管理员主线
- 若无新裁决，默认从 `A1` 或 `A2` 开始
- 继续显式避让 `experiments/gemini_live_interview_lab/*` 无关脏改
- 若需要完整后端集成测试，先评估 `R-TEST-H2-001`

## 12. 当前阶段完成定义（针对本蓝图）

本蓝图阶段的完成定义不是“后台已经实现完”，而是：

- 已完成后台现状与真实前台落地的差距梳理
- 已形成稳定的下一轮治理域信息架构
- 已明确 AI 专项的两层模型与第一批实施范围
- 已给出后续 Agent 可直接接续的任务拆分和顺序

达到以上条件后，后续实现阶段即可不再依赖聊天记录本身。
