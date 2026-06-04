# 交易中枢支付异常对账子路由 UI 设计需求

## 页面标识
- 页面名称：交易中枢 / 支付异常对账
- 子路由：`/admin/consult/orders?tab=reconciliation`
- 对应组件片段：`AdminOrdersReconciliationPage -> renderReconciliation()`
- 页面定位：支付异常识别、人工诊断、人工处理工作区

## 项目基线
- 前端技术栈：React 18 + TypeScript + Vite 5 + React Router 6
- UI 架构必须以 Ant Design 5 为主，允许使用 `@ant-design/pro-components`；Tailwind CSS 4 只做布局与间距辅助
- 页面接入 `AdminShellLayout`
- 桌面端优先，建议按 `1440px` 画板设计
- 主题主色 `#6366f1`，成功 `#10b981`，警告 `#f59e0b`，错误 `#ef4444`

## 页面目标
- 聚焦异常待处理订单，支持按状态和异常标签筛选
- 帮助管理员理解订单、支付流水、人工动作之间的时间线
- 支持在详情抽屉中直接执行推荐人工动作

## 信息架构
- 统计卡区
- 筛选工具栏
- 对账表格
- 对账详情抽屉
- 人工处理弹窗

## 必备模块
- 统计卡：异常待处理、本页已人工处理、本页命中异常标签
- 筛选项：关键词、订单状态、对账状态、应用筛选、刷新列表
- 表格字段：订单号、金额、支付渠道、支付状态、对账状态、异常标签、最近人工处理、操作
- 详情抽屉区块：订单与用户信息、异常标签、订单与对账时间线、推荐人工动作、最近一次人工处理、支付记录表、问题摘要
- 人工处理弹窗字段：处理动作、处理备注
- 用户跳转：查看学生详情、查看导师详情

## 数据承接
- 主要接口：`GET /admin/payments/reconciliation?page&size&keyword&orderStatus&reconciliationStatus`、`GET /admin/payments/reconciliation/:orderNo`、`POST /admin/payments/reconciliation/:orderNo/handle`。
- 列表对象 `ReconciliationItem`：`orderNo`、`studentUserId`、`studentDisplayName`、`mentorUserId`、`mentorDisplayName`、`amountFen`、`orderStatus`、`paymentMode`、`paymentChannel`、`latestPaymentStatus`、`providerTradeNo`、`reconciliationStatus`、`issueTags[]`、`latestManualAction`、`latestManualNote`、`latestManualHandledAt`、`createdAt`、`paidAt`、`closedAt`、`latestPaymentCreatedAt`。
- 对账详情对象 `ReconciliationDetailResponse`：在列表字段基础上补充 `questionText`、预约时间、`recommendedActions[]`、`latestManualHandling`、`paymentRecords[]`。
- 最近一次人工处理对象：`latestManualHandling = { action, note, operatorUserId, processedAt }`，适合做摘要卡和时间线节点。
- 支付流水对象 `paymentRecords[]`：`channel`、`mode`、`status`、`providerTradeNo`、`amountFen`、`idempotencyKey`、`rawCallback`、`createdAt`。
- 人工处理载荷：`POST /admin/payments/reconciliation/:orderNo/handle`，请求体 `{ action: string; note: string }`；动作名称来自推荐动作或手工选择。
- 时间字段格式：本页时间统一为 `string | null`；异常标签、推荐动作都是字符串数组；金额字段为分转元展示。

## 关键交互
- 异常标签和对账状态需要一眼识别高风险记录
- 点击“诊断详情”后应在抽屉中保留当前页面上下文
- 推荐人工动作要能直接转为提交动作，减少二次输入
- 提交人工处理后应回刷列表与当前详情，不应让管理员丢失诊断上下文

## 设计限制
- 优先使用 Ant Design 的 `Table`、`Drawer`、`Tag`、`Alert`、`Timeline`、`Descriptions`、`Modal`
- 对账详情抽屉要偏技术诊断台风格，信息块清晰分组
- 支付记录表和时间线必须兼容长交易号、长备注和多条流水
