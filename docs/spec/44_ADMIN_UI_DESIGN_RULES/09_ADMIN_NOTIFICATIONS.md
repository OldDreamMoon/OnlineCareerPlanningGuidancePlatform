# 通知运营台 UI 设计需求

## 页面标识
- 页面名称：通知运营台
- 路由：`/admin/notifications`
- 对应组件：`AdminNotificationsPage`
- 页面定位：系统公告发布、派发健康度观测和失败任务重投工作台

## 项目基线
- 前端技术栈：React 18 + TypeScript + Vite 5 + React Router 6
- UI 架构必须以 Ant Design 5 为主，允许使用 `@ant-design/pro-components`；Tailwind CSS 4 只做布局与间距辅助
- 页面接入 `AdminShellLayout`
- 桌面端优先，建议按 `1440px` 画板设计
- 主题主色 `#6366f1`，成功 `#10b981`，警告 `#f59e0b`，错误 `#ef4444`
- 当前页面是通知治理后台，不是用户通知中心

## 页面目标
- 在同一页完成公告发布、通道 readiness 查看、历史公告回看和失败任务处理
- 强化通知链路的后台运营属性
- 支持前台落点预判和死信重投

## 信息架构
- 顶部深色 Hero 区
- 概览统计卡
- 左侧公告发布表单
- 右侧通道 readiness 区
- 最近系统公告表格
- 派发任务队列表格

## 必备模块
- Hero 区标签、说明、刷新、跳转用户通知中心
- 统计卡：累计公告批次、待发送任务、等待重试、死信任务
- 公告发布表单：标题、正文、目标角色、优先级、动作编码、引用类型、引用 ID
- 发布前前台落点预览
- 通道 readiness：最近一次公告、WebSocket 状态、邮件状态、发件地址、近 7 日通道统计
- 公告历史表：公告、目标角色、动作、前台落点预览、入箱量、发布时间
- 派发任务表：任务、渠道/状态、尝试次数、错误摘要、时间、操作

## 数据承接
- 主要接口：`GET /admin/notifications/overview`、`GET /admin/notifications/announcements`、`GET /admin/notifications/dispatch-jobs?page&size&status&channel`、`POST /admin/notifications/announcements`、`POST /admin/notifications/dispatch-jobs/:jobId/retry`。
- 概览对象 `NotificationOpsOverviewPayload`：`announcementCount`、`announcementsLast7Days`、`pendingJobCount`、`retryJobCount`、`deadJobCount`、`emailReady`、`emailSender`、`websocketReady`、`lastAnnouncementAt`、`channelStats[]`。
- 通道统计对象 `ChannelOpsSummaryItem`：`channel`、`total`、`sent`、`acked`、`retryWait`、`dead`、`skipped`，适合做 readiness 卡片和通道健康分栏。
- 公告历史对象 `AnnouncementItem`：`eventId`、`title`、`content`、`priority`、`targetRoles[]`、`notificationCount`、`refType`、`refId`、`actionCode`、`createdAt`。
- 派发任务对象 `DispatchJobItem`：`jobId`、`notificationId`、`eventId`、`userId`、`channel`、`status`、`attemptCount`、`maxAttempts`、`nextRunAt`、`sentAt`、`ackedAt`、`failedAt`、`errorCode`、`errorMessage`、`title`、`content`、`type`、`createdAt`、`updatedAt`。
- 公告发布载荷：`POST /admin/notifications/announcements`，请求体 `{ title, content, targetRoles, priority, actionCode, refType, refId }`；发布前前台落点预览由这些字段在前端解析得到。
- 重投动作：`POST /admin/notifications/dispatch-jobs/:jobId/retry`，返回 `jobId`、`status`、`nextRunAt`，设计上要支持刷新后的状态回写。
- 时间字段格式：本页统一兼容 `number | string | null`；角色列表是字符串数组；错误信息字段可能为 `null`。

## 关键交互
- 发布公告前实时展示学生/导师/企业落点预览
- 支持按状态和渠道筛选派发队列
- 对死信和可重试任务显示“重新入队”
- 页面内需要明确区分“发布行为”和“观测/处置行为”

## 设计限制
- 优先使用 Ant Design 的 `Card`、`Form`、`Table`、`Descriptions`、`Alert`、`Tag`、`Button`
- 顶部 Hero 可强化运营感，但下方表格和表单仍需保持标准后台组件秩序
- 不要把页面设计成营销活动后台，应保留平台治理感
