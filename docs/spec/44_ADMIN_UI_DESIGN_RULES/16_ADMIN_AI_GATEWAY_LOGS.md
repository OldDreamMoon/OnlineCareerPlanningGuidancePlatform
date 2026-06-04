# AI 网关调用日志子路由 UI 设计需求

## 页面标识
- 页面名称：AI 网关 / 调用日志
- 子路由：`/admin/ai/gateway?tab=logs`
- 对应组件片段：`AdminAiGatewayPage -> renderLogs()`
- 页面定位：AI 调用链路排查和 trace 定位工作区

## 项目基线
- 前端技术栈：React 18 + TypeScript + Vite 5 + React Router 6
- UI 架构必须以 Ant Design 5 为主，允许使用 `@ant-design/pro-components`；Tailwind CSS 4 只做布局与间距辅助
- 页面接入 `AdminShellLayout`
- 桌面端优先，建议按 `1440px` 画板设计
- 主题主色 `#6366f1`，成功 `#10b981`，警告 `#f59e0b`，错误 `#ef4444`
- 带 `traceId` 打开该页时需要强化追踪场景

## 页面目标
- 按任务、场景、服务商、状态、traceId、userId 快速定位 AI 请求
- 展示日志列表并进入详情
- 支撑从内容治理、通知等其他页面带 traceId 反查
- 支撑基于当前配置的链路排障，而不只是静态看日志

## 信息架构
- 顶部多条件筛选区
- 日志表格
- 右侧日志详情抽屉

## 必备模块
- 筛选项：任务类型、场景编码、服务商、状态、追踪编号、用户编号
- 日志表格
- 日志详情：请求上下文、结果摘要、当前链路快照、原始 JSON、关联跳转
- 刷新按钮

## 数据承接
- 主要接口：`GET /admin/ai/logs?page&size&taskType&sceneCode&provider&status&traceId&userId`、`GET /admin/ai/logs/:id`。
- 列表筛选参数：`page`、`size`、`taskType`、`sceneCode`、`provider`、`status`、`traceId`、`userId`；当 URL 中存在 `traceId` 时页面默认带入该值。
- 日志行对象 `AiLogItem`：`id`、`traceId`、`userId`、`userEmail`、`userDisplayName`、`taskType`、`sceneCode`、`provider`、`model`、`status`、`errorCode`、`latencyMs`、`requestTokens`、`responseTokens`、`totalTokens`、`thoughtsTokens`、`reasoningEffort`、`thinkingBudget`、`thinkingLevel`、`estimatedCost`、`chargedPoints`、`quotaWeight`、`resultSummary`、`userTier`、`createdAt`。
- 详情对象 `AiLogDetailPayload` 在 `AiLogItem` 基础上额外包含：
  - `resultPayloadJson`
  - `governanceTraceSummary = { auditCount, recentActionTypes, latestAuditAt }`
  - `currentRouteSnapshot = { routeCode, sceneCode, providerCode, providerDisplayName, providerType, model, executionMode, promptTemplateName, promptTemplateVersionNo, reasoningEffort, thinkingBudget, thinkingLevel, thinkingSource } | null`
- 日志详情需要支持技术排查字段：模型、Provider、sceneCode、token 用量、thinking 配置、结果摘要、原始 JSON、治理留痕数量、当前链路快照。
- `userTier` 当前作为日志展示字段保留，用于判断普通 / VIP 是否命中了预期链路；若后续需要把 `model / userTier` 升级为列表筛选条件，应先补后端查询参数与索引。
- `currentRouteSnapshot` 的语义是“按当前 AI 网关配置推导，仅用于技术排障，不代表历史调用当时的完整配置”。
- 关联跳转语义：`traceId` 可联动到内容治理审计日志，`userId` 可联动到用户详情页。
- 字段格式：成本字段为人民币字符串（元）；token 和积分为数字；时间字段统一兼容 `number | string | null`；原始结果载荷为 JSON 字符串。

## 关键交互
- 通过 query 中的 `traceId` 直接进入筛选状态
- 列表行要能打开右侧抽屉详情
- 详情需要支持跳去内容治理审计等关联页面
- 日志表格中的任务 / 场景、服务商等字段可以作为快捷筛选入口
- 日志详情允许提供“查看同场景日志”“查看同服务商日志”“仅看当前 Trace”“带入路由预览”等排障动作

## 设计限制
- 优先使用 Ant Design 的 `Table`、`Drawer/Modal`、`Input`、`Select`、`Alert`、`Tag`
- 日志表格要支持宽字段横向阅读
- 详情区要适合技术排查，不做营销式视觉
