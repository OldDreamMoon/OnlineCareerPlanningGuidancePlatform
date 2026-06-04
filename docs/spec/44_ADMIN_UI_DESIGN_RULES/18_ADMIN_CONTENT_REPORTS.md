# 内容治理举报中心子路由 UI 设计需求

## 页面标识
- 页面名称：内容治理 / 举报中心
- 子路由：`/admin/content?tab=reports`
- 对应组件片段：`AdminContentModerationPage -> renderReports()`
- 页面定位：举报工单处理工作区

## 项目基线
- 前端技术栈：React 18 + TypeScript + Vite 5 + React Router 6
- UI 架构必须以 Ant Design 5 为主，允许使用 `@ant-design/pro-components`；Tailwind CSS 4 只做布局与间距辅助
- 页面接入 `AdminShellLayout`
- 桌面端优先，建议按 `1440px` 画板设计
- 主题主色 `#6366f1`，成功 `#10b981`，警告 `#f59e0b`，错误 `#ef4444`
- 该页强调工单筛选、内容上下文和处置动作

## 页面目标
- 快速筛选举报单并查看上下文
- 完成采纳、驳回、关闭等治理动作
- 联查举报对象、用户详情和 AI trace

## 信息架构
- 筛选工具栏
- 举报表格
- 举报详情层
- 处理历史层
- 处置动作层

## 必备模块
- 筛选项：举报单号/摘要检索、状态、目标类型
- 表格列：举报对象、原因、举报数、提交时间、动作
- 举报详情：原文、上下文、对象信息、关联用户
- 历史时间线
- 处置动作：采纳举报、驳回举报、关闭工单、下架、恢复、不做动作

## 数据承接
- 主要接口：`GET /admin/content/reports?page&size&status&targetType`、`GET /admin/content/reports/:reportId`、`GET /admin/content/reports/:reportId/actions`、`POST /admin/content/reports/:reportId/decision`。
- 列表筛选参数：`page`、`size`、`status`、`targetType`，页面本地还支持“举报单号 / 内容摘要”关键词检索。
- 列表对象 `ReportItem`：`reportId`、`targetType`、`targetId`、`contentPostId`、`contentTitle`、`contentBody`、`reasonCode`、`status`、`latestAction`、`reportCount`、`createdAt`、`updatedAt`。
- 详情对象 `ReportDetailResponse`：`reportId`、`reporterUserId`、`reporterDisplayName`、`targetType`、`targetId`、`contentPostId`、`contentTitle`、`contentBody`、`reasonCode`、`reportDetail`、`status`、`latestAction`、`reportCount`、`targetStatus`、`targetRiskLevel`、`createdAt`、`updatedAt`。
- 历史动作对象 `ReportActionItem`：`actionId`、`operatorUserId`、`operatorDisplayName`、`decision`、`action`、`comment`、`createdAt`，适合做时间线或操作记录表。
- 处置载荷：`POST /admin/content/reports/:reportId/decision`，请求体 `{ decision, action, comment }`；`decision` 与 `action` 需要并列展示，不能混成一个字段。
- 关联排查：当目标类型是 `USER` 时，`targetId` 可直接映射到用户详情；当有 `traceId` 语义时可联查 AI 日志页。
- 时间字段格式：举报中心时间均为字符串；目标 ID 可能是数字字符串，也可能是帖子/评论编码字符串。

## 关键交互
- 支持查看原文与直达上下文
- 支持打开用户详情和 AI 网关日志页
- 处置后状态、动作标记和历史时间线要同步更新

## 设计限制
- 优先使用 Ant Design 的 `Table`、`Drawer/Modal`、`Timeline`、`Tag`、`Button`
- 举报详情要适合桌面端连续阅读，不要做轻飘弹层
- 工单状态和动作结果必须并列清楚
