# 交易中枢售后退款审核子路由 UI 设计需求

## 页面标识
- 页面名称：交易中枢 / 售后退款审核
- 子路由：`/admin/consult/orders?tab=after-sales`
- 对应组件片段：`AdminOrdersReconciliationPage -> renderAfterSales()`
- 页面定位：售后申请筛选、审核、订单排查工作区

## 项目基线
- 前端技术栈：React 18 + TypeScript + Vite 5 + React Router 6
- UI 架构必须以 Ant Design 5 为主，允许使用 `@ant-design/pro-components`；Tailwind CSS 4 只做布局与间距辅助
- 页面接入 `AdminShellLayout`
- 桌面端优先，建议按 `1440px` 画板设计
- 主题主色 `#6366f1`，成功 `#10b981`，警告 `#f59e0b`，错误 `#ef4444`

## 页面目标
- 快速定位待审核售后申请
- 在审核动作前查看订单完整生命周期与支付摘要
- 支持必要的人工退款与沙箱支付排查

## 信息架构
- 统计卡区
- 筛选工具栏
- 售后申请表格
- 审核弹窗
- 订单详情大弹窗
- 手工退款弹窗
- 网关返回结果弹窗

## 必备模块
- 统计卡：待审核申请、本页申请金额、本页自动触发
- 筛选项：关键词、售后状态、应用筛选、刷新列表
- 表格字段：申请单号、订单号、订单金额、订单状态、售后原因、售后状态、提交时间、操作
- 审核弹窗字段：审核结论、审核备注
- 订单详情区块：订单基本信息、咨询问题、订单生命周期时间线、处理操作、最近支付记录、退款处理摘要、售后申请时间线
- 处理动作：手工退款、查询交易、关闭交易、查询退款
- 用户跳转：查看学生详情、查看导师详情

## 数据承接
- 主要接口：`GET /admin/consult/after-sales/requests?page&size&keyword&status`、`GET /admin/consult/orders/:orderNo`、`POST /admin/consult/after-sales/requests/:requestId/review`、`POST /admin/consult/orders/:orderNo/payment/query`、`POST /admin/consult/orders/:orderNo/payment/close`、`GET /admin/consult/orders/:orderNo/payment/refund-query`、`POST /admin/consult/orders/:orderNo/refund`。
- 列表对象 `AfterSalesRequestItem`：`requestId`、`orderNo`、`orderStatus`、`amountFen`、`studentUserId`、`studentDisplayName`、`mentorUserId`、`mentorDisplayName`、`requestType`、`status`、`reason`、`reviewNote`、`reviewerUserId`、`autoTriggered`、`createdAt`、`reviewedAt`。
- 审核提交载荷：`POST /admin/consult/after-sales/requests/:requestId/review`，请求体 `{ approved: boolean; reviewNote: string }`，前端表单里的“通过 / 驳回”会映射成布尔值。
- 订单详情对象 `OrderDetailResponse`：订单主体 `orderNo`、学生/导师身份、`amountFen`、`status`、`questionText`、`paymentMode`、预约时间、创建/支付/关闭/自动取消/导师回复截止时间；并嵌套 `payment`、`review`、`refund`、`afterSalesRequests[]`。
- 支付查询返回 `PaymentQueryResponse`，关单返回 `PaymentCloseResponse`，退款查询返回 `RefundQueryResponse`，手工退款返回 `OrderRefundResponse`；这些对象都要放进统一的“网关结果弹窗”。
- 手工退款载荷：`POST /admin/consult/orders/:orderNo/refund`，请求体 `{ reason: string }`。
- 时间字段格式：本页时间统一为 `string | null`；`amountFen` 为整数；`autoTriggered` 为布尔值，需要单独的自动触发标记。

## 关键交互
- 待审核记录要突出“审核”主动作，已处理记录保留“订单详情”
- 自动触发售后记录要有明显识别标记
- 订单详情弹窗内应支持继续执行手工退款和支付网关查询
- 沙箱查询、关单、退款查询结果要用专门结果层展示原始返回

## 设计限制
- 优先使用 Ant Design 的 `Table`、`Modal`、`Card`、`Statistic`、`Descriptions`、`Timeline`、`Alert`
- 订单详情是高信息密度弹窗，需要适合宽屏阅读，不要使用窄抽屉
- 风险动作如退款、超时提醒需有明显颜色层级
