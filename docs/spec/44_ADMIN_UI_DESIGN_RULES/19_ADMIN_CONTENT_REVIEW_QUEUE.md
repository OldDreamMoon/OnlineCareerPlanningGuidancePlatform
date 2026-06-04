# 内容治理待审队列子路由 UI 设计需求

## 页面标识
- 页面名称：内容治理 / 待审队列
- 子路由：`/admin/content?tab=review`
- 对应组件片段：`AdminContentModerationPage -> renderReviewQueue()`
- 页面定位：社区帖子、评论和 AI 输出的人工复核工作区

## 项目基线
- 前端技术栈：React 18 + TypeScript + Vite 5 + React Router 6
- UI 架构必须以 Ant Design 5 为主，允许使用 `@ant-design/pro-components`；Tailwind CSS 4 只做布局与间距辅助
- 页面接入 `AdminShellLayout`
- 桌面端优先，建议按 `1440px` 画板设计
- 主题主色 `#6366f1`，成功 `#10b981`，警告 `#f59e0b`，错误 `#ef4444`
- 该页强调人工审核效率和风险等级识别

## 页面目标
- 从队列中快速处理待审内容
- 清楚展示来源类型、目标类型、命中原因和风险等级
- 在一页内完成查看详情、通过、驳回

## 信息架构
- 顶部筛选与刷新
- 待审表格
- 详情层
- 审核决策层

## 必备模块
- 筛选项：来源类型
- 表格列：待审项编号、来源类型、目标类型、内容概要、命中原因、风险等级、创建时间、操作
- 详情区需显示全文和上下文
- 审核动作：通过、驳回

## 数据承接
- 主要接口：`GET /admin/content/review-queue?page&size&sourceType`、`GET /admin/content/review-queue/:itemId`、`POST /admin/content/review-queue/:itemId/decision`。
- 列表筛选参数：`page`、`size`、`sourceType`，来源类型主要包括 `COMMUNITY_POST`、`COMMUNITY_COMMENT`、`AI_OUTPUT`。
- 列表对象 `ReviewQueueItem`：`itemId`、`sourceType`、`targetType`、`targetId`、`riskLevel`、`reasonCode`、`preview`、`createdAt`。
- 详情对象 `ReviewQueueDetailResponse`：`itemId`、`sourceType`、`targetType`、`targetId`、`riskLevel`、`reasonCode`、`preview`、`contentTitle`、`contentBody`、`postId`、`postTitle`、`postBody`、`authorUserId`、`authorDisplayName`、`authorRole`、`createdAt`。
- 审核载荷：`POST /admin/content/review-queue/:itemId/decision`，请求体 `{ decision: "APPROVE" | "REJECT"; comment: string }`。
- 详情层要兼容三种内容来源：社区帖子、社区评论、AI 输出，因此需要为“正文”“所属帖子上下文”“作者信息”预留可缺省区域。
- 时间字段格式：待审时间为字符串；风险等级、来源类型、目标类型均是枚举字符串。

## 关键交互
- 点击“查看详情”进入完整阅读
- 支持直接在列表行做通过/驳回快捷动作
- 审核后队列要即时刷新

## 设计限制
- 优先使用 Ant Design 的 `Table`、`Tag`、`Button`、`Drawer/Modal`
- 风险等级必须具备强色彩区分
- 审核动作要非常明确，避免误操作
