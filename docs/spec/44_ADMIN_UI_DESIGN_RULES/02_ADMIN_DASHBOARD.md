# 管理员总览 UI 设计需求

## 页面标识
- 页面名称：管理员总览
- 路由：`/admin/dashboard`
- 对应组件：`AdminDashboardV2Page`
- 页面定位：平台治理首页，负责跨域待办、经营快照和联动风险总览

## 项目基线
- 前端技术栈：React 18 + TypeScript + Vite 5 + React Router 6
- UI 架构必须以 Ant Design 5 为主，允许使用 `@ant-design/pro-components`；Tailwind CSS 4 只做布局与间距辅助
- 页面接入 `AdminShellLayout`
- 桌面端优先，建议按 `1440px` 画板设计
- 主题主色 `#6366f1`，成功 `#10b981`，警告 `#f59e0b`，错误 `#ef4444`
- 该页为高信息密度后台首页，必须优先可读性与跳转效率

## 页面目标
- 让管理员在一屏内判断当前平台最该处理的事项
- 用真实治理域替代早期单一增长看板
- 为后续治理页提供强入口和联动决策依据

## 信息架构
- 顶部页标题、说明、刷新动作
- 第一层总指标卡
- 快捷进入区
- 待处理事项卡组
- 治理域快照卡组
- AI 场景运行摘要区
- 内容治理样本表
- 右侧纵向联动摘要区

## 必备模块
- 4 张总指标卡：待处理事项、高风险事项、AI 渠道覆盖、近 7 日 AI 成本
- 快捷进入按钮区：认证审核、企业任务、导师经营、技能资源、通知运营、运行配置、AI 应用、内容治理、支付与售后
- 待处理事项卡组：待认证主体、企业任务风险、导师提现待处理、待处理举报、内容审核队列、通知死信、售后待审核、对账异常、技能资源缺口
- 治理域快照卡组：用户与认证、企业任务、通知运营、内容治理、导师经营、技能资源、运行配置与风控、AI 应用运营
- AI 场景运行摘要：活跃场景、高成本场景、异常成功率场景
- 待处理举报表格
- 右侧纵向摘要：AI 成本 Top 模型、支付与售后运行摘要、交付链路联动风险、AI 运行联动风险、供需与交付准备度、认证与触达联动

## 数据承接
- 主要接口：`GET /admin/dashboard/workbench`、`GET /admin/users/summary`、`GET /admin/enterprise/tasks/overview`、`GET /admin/mentors/operations/overview`、`GET /admin/notifications/overview`、`GET /admin/skills/overview`、`GET /admin/system/console-snapshot`、`GET /admin/ai/routes`、`GET /admin/ai/prompt-templates`、`GET /admin/ai/cost-dashboard?period=week`、`GET /admin/ai/application-scene-metrics?days=7`、`GET /admin/content/reports?page=1&size=5&status=PENDING`。
- 顶部总指标承接 `WorkbenchSummary`：`generatedAt`、`pendingReports`、`reviewQueue`、`afterSalesRequests`、`reconciliationReviewRequired`、`totalPendingTasks`、`providerRuntime{ totalProviders, healthyProviders, degradedProviders, downProviders, disabledProviders, idleProviders, unhealthyProviders, hours, timezone }`。
- 治理域快照承接多组摘要对象：用户域用 `UserSummaryResponse`，企业任务域用 `EnterpriseTaskOpsOverviewPayload`，导师域用 `MentorOpsOverviewPayload`，通知域用 `NotificationOpsOverviewPayload`，技能域用 `SkillOpsOverviewPayload`，运行配置域用 `ConsoleSnapshotPayload`。
- AI 运行摘要承接 `RouteItem`、`PromptTemplateItem`、`CostDashboardPayload`、`ApplicationSceneMetricItem`，并按 `taskType + sceneCode` 聚合成场景卡，展示调用量、成功率、平均延迟、预估成本、最近调用时间、主路由和模板覆盖情况。
- 举报样本表承接 `ReportItem`：`reportId`、`targetType`、`contentTitle`、`reasonCode`、`status`、`reportCount`、`createdAt`，只展示待处理样本。
- 页面内“待处理事项”“治理域快照”“右侧纵向联动摘要”大量数据是前端基于上述接口二次组合，不是单独新接口，设计稿要为模块化局部失败和局部空态预留位置。
- 时间字段格式：大盘页统一兼容 `number | string | null`；金额字段中 AI 成本为人民币字符串（元），支付类金额为分转元展示。

## 关键交互
- 每张摘要卡和每个模块都要有明确“进入治理页”动作
- 跳转需要支持带 query 的深链，如支付页不同 tab、运行配置 AI 渠道 tab
- 首页组件应支持局部加载失败，不阻断全页阅读

## 设计限制
- 推荐使用 Ant Design 的 `Card`、`Row`、`Col`、`Table`、`Tag`、`Button`、`Empty`、`Alert`
- 页面必须保持“首页总览”属性，不要设计成巨型数据报表
- 需要明显区分一级经营信息、二级联动风险、三级样本列表
