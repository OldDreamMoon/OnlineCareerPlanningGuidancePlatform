# 导师经营治理台 UI 设计需求

## 页面标识
- 页面名称：导师经营治理台
- 路由：`/admin/mentors/operations`
- 对应组件：`AdminMentorOperationsPage`
- 页面定位：平台视角巡检导师套餐、排期、履约、提现和质量风险

## 项目基线
- 前端技术栈：React 18 + TypeScript + Vite 5 + React Router 6
- UI 架构必须以 Ant Design 5 为主，允许使用 `@ant-design/pro-components`；Tailwind CSS 4 只做布局与间距辅助
- 页面接入 `AdminShellLayout`
- 桌面端优先，建议按 `1440px` 画板设计
- 主题主色 `#6366f1`，成功 `#10b981`，警告 `#f59e0b`，错误 `#ef4444`
- 页面是经营治理页，不是导师个人工作台

## 页面目标
- 快速识别导师供给健康度和经营风险
- 处理提现状态流转
- 与用户详情、认证审核、支付与售后形成联动

## 信息架构
- 顶部标题与说明
- 4 张概览卡
- 巡检筛选区
- 导师经营表格
- 导师经营详情工作区（当前实现为详情弹窗）
- 风险与建议区
- 提现操作区

## 必备模块
- 概览卡：导师总数、待认证导师、高风险导师、待处理提现
- 筛选项：关键词、认证状态、风险等级、提现状态
- 表格字段：导师、套餐与价格、排期可预约、履约与质量、提现队列、风险信号、操作
- 详情字段：资料更新时间、最近订单活动、下次可预约、下次已预定、最近提现状态
- 经营摘要：服务场景、套餐结构、履约与评分
- 风险与建议：逐条风险信号说明
- 直达动作：查看导师详情、去认证审核、查看支付与售后
- 详情层：导师经营详情弹窗，承接对象识别、经营判断、风险说明和提现处理

## 数据承接
- 主要接口：`GET /admin/mentors/operations/overview`、`GET /admin/mentors/operations?page&size&keyword&approvalStatus&riskLevel&withdrawalStatus`、`POST /admin/mentors/operations/withdrawals/:withdrawalId/status`。
- 概览对象 `MentorOpsOverviewPayload`：`totalMentorCount`、`approvedMentorCount`、`pendingApprovalCount`、`riskyMentorCount`、`scheduleRiskMentorCount`、`fulfillmentRiskMentorCount`、`pendingWithdrawalMentorCount`、`pendingWithdrawalAmountFen`。
- 列表筛选参数：`page`、`size`、`keyword`、`approvalStatus`、`riskLevel`、`withdrawalStatus`。
- 列表行对象 `MentorOpsRecord` 需至少按四组展示：
- 主体信息：`mentorUserId`、`displayName`、`realName`、`showRealName`、`companyName`、`jobTitle`、`approvalStatus`、`available`
- 套餐供给：`totalPackageCount`、`enabledPackageCount`、`enabledAppointmentPackageCount`、`startingPriceFen`、`enabledPackageNames[]`、`serviceScenes[]`、`avgRating`
- 排期履约：`weekAvailableSlotCount`、`nextThreeDayAvailableSlotCount`、`upcomingBookedSlotCount`、`nextAvailableAt`、`nextBookedAt`、`totalOrderCount`、`paidOrderCount`、`answeredOrderCount`、`closedOrderCount`、`refundedOrderCount`、`overdueReplyOrderCount`、`expiringReplyOrderCount`、`pendingAfterSalesCount`、`afterSalesImpactCount`、`latestOrderActivityAt`
- 提现与风险：`pendingWithdrawalCount`、`processingWithdrawalCount`、`completedWithdrawalCount`、`rejectedWithdrawalCount`、`openWithdrawalCount`、`openWithdrawalAmountFen`、`latestWithdrawalId`、`latestWithdrawalAmountFen`、`latestWithdrawalStatus`、`latestWithdrawalNote`、`latestWithdrawalCreatedAt`、`latestWithdrawalUpdatedAt`、`profileUpdatedAt`、`highestRiskLevel`、`riskSignals[]`
- 提现状态流转载荷：`POST /admin/mentors/operations/withdrawals/:withdrawalId/status`，请求体 `{ status: string }`，界面上至少承接待打款、打款中、已完成、已驳回。
- 时间字段格式：本页统一兼容 `number | string | null`；金额字段后端以分为单位返回，前端转成人民币展示。

## 关键交互
- 点击表格行切换详情
- 提现状态流转需要确认弹窗
- 对未认证导师应突出“去认证审核”
- 刷新要同步更新概览、列表和当前详情
- 点击列表行或“查看详情”按钮打开详情弹窗，关闭后不丢失列表筛选与分页状态

## 设计限制
- 优先使用 Ant Design 的 `Card`、`Table`、`Descriptions`、`Tag`、`Alert`、`Button`
- 经营摘要与风险摘要要分区明确，不能混成一块信息墙
- 提现状态与风险等级要有强提示色
- 详情弹窗的独立设计要求见 [31_ADMIN_MENTOR_OPS_DETAIL_MODAL.md](./31_ADMIN_MENTOR_OPS_DETAIL_MODAL.md)
