# 内容治理审计日志子路由 UI 设计需求

## 页面标识
- 页面名称：内容治理 / 审计日志
- 子路由：`/admin/content?tab=audit`
- 对应组件片段：`AdminContentModerationPage -> renderAuditLogs()`
- 页面定位：治理动作留痕与 trace 反查工作区

## 项目基线
- 前端技术栈：React 18 + TypeScript + Vite 5 + React Router 6
- UI 架构必须以 Ant Design 5 为主，允许使用 `@ant-design/pro-components`；Tailwind CSS 4 只做布局与间距辅助
- 页面接入 `AdminShellLayout`
- 桌面端优先，建议按 `1440px` 画板设计
- 主题主色 `#6366f1`，成功 `#10b981`，警告 `#f59e0b`，错误 `#ef4444`
- 支持通过 `traceId` 直接打开本子页

## 页面目标
- 按目标类型、动作、目标编号、traceId 回查治理动作
- 为内容治理与 AI 网关日志建立联查能力
- 支持打开目标用户详情

## 信息架构
- 多条件筛选工具栏
- 审计日志表格
- 审计详情层

## 必备模块
- 筛选项：目标类型、动作类型、目标编号、追踪编号
- 表格字段：操作人、动作、目标、摘要、时间、trace
- 详情层：原始数据、目标信息、关联用户、trace 入口
- 刷新动作

## 数据承接
- 主要接口：`GET /admin/content/audit-logs?page&size&targetType&actionType&targetId&traceId`。
- 列表筛选参数：`page`、`size`、`targetType`、`actionType`、`targetId`、`traceId`；页面不需要单独详情接口，详情层直接消费当前行数据。
- 列表对象 `AuditLogItem`：`id`、`traceId`、`operatorUserId`、`operatorDisplayName`、`actionType`、`targetType`、`targetId`、`detailJson`、`createdAt`。
- `detailJson` 可能是 JSON 字符串，也可能是普通文本；设计稿需要给代码块/结构化键值预留双形态展示。
- 关联排查：`traceId` 可跳去 `/admin/ai/gateway?tab=logs&traceId=...`；当 `targetType = USER` 时，`targetId` 可映射到用户详情页。
- 本页重点是“谁在什么时候对哪个目标做了什么动作”，因此操作人、目标对象和 trace 编号都要比普通文本更醒目。
- 时间字段格式：审计时间为字符串；目标 ID 和 trace ID 都可能较长，需要支持截断与悬浮查看。

## 关键交互
- 输入 `traceId` 后应快速过滤定位
- 支持从日志详情直达用户详情或 AI 网关日志
- 日志行要有“查看详情”动作

## 设计限制
- 优先使用 Ant Design 的 `Table`、`Input`、`Select`、`Tag`、`Drawer/Modal`
- 审计页风格要更克制偏技术，不需要内容卡片化装饰
- 长文本和 JSON 需要适合桌面端阅读
