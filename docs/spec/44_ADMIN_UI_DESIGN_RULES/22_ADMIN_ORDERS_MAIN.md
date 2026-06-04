# 交易中枢主路由 UI 设计需求

## 页面标识
- 页面名称：交易中枢
- 主路由：`/admin/consult/orders`
- 对应组件：`AdminOrdersReconciliationPage`
- 页面定位：售后审核与支付异常对账的统一入口页

## 项目基线
- 前端技术栈：React 18 + TypeScript + Vite 5 + React Router 6
- UI 架构必须以 Ant Design 5 为主，允许使用 `@ant-design/pro-components`；Tailwind CSS 4 只做布局与间距辅助
- 页面接入 `AdminShellLayout`
- 桌面端优先，建议按 `1440px` 画板设计
- 主题主色 `#6366f1`，成功 `#10b981`，警告 `#f59e0b`，错误 `#ef4444`
- 别名路由：`/admin/orders`、`/admin/payments` 重定向到 `/admin/consult/orders`

## 页面目标
- 让管理员在一个治理域内处理售后退款审核与支付异常对账
- 通过 tab 清晰区分两种工作流，但保持统一的页面心智
- 为详情处理层预留订单、支付、人工动作的下钻空间

## 信息架构
- 页面标题区
- 初始化错误反馈区
- 顶部 tab 切换
- 售后退款审核子页容器
- 支付异常对账子页容器
- 详情弹层体系

## 必备模块
- 标题副文案：说明“统一处理售后审核、退款操作与支付对账”
- Tab 1：售后退款审核
- Tab 2：支付异常对账
- 支持通过 URL `tab` 参数保持当前工作分区
- 支持全局重试加载
- 详情层体系：售后审核弹窗、订单详情弹窗、手工退款弹窗、对账详情抽屉、人工处理弹窗、网关结果弹窗

## 数据承接
- 主路由核心接口：`GET /admin/consult/after-sales/requests?...`、`GET /admin/payments/reconciliation?...`、`GET /admin/consult/orders/:orderNo`、`GET /admin/payments/reconciliation/:orderNo`。
- 顶部 tab 仅切换两个工作流：`after-sales` 和 `reconciliation`，通过 URL 参数 `tab` 保持状态；两个 tab 的筛选条件和分页状态独立维护。
- 主路由共享详情层数据：售后域使用 `OrderDetailResponse`、`AfterSalesReviewResponse`、`PaymentQueryResponse`、`PaymentCloseResponse`、`RefundQueryResponse`、`OrderRefundResponse`；对账域使用 `ReconciliationDetailResponse`、`PaymentHandleResponse`。
- 页面内所有用户跳转都依赖订单或对账详情中的 `studentUserId`、`mentorUserId`，设计稿需要在详情层为双用户入口预留固定位置。
- 时间字段格式：交易中枢统一兼容 `string | null` 和少量 `number | string | null` 混合时间；金额字段后端用分返回，前端转成人民币展示。
- 风险结果层是独立的网关返回展示容器，专门承接支付查询、关单、退款查询和退款结果的原始结构化响应。

## 关键交互
- 切换 tab 时要保持统一壳层，不应重置整个页面心智
- 各 tab 的筛选条件应独立维护，并通过 URL 参数保留核心状态
- 从任意 tab 打开的详情层都应支持继续跳转到用户详情页
- 弹窗和抽屉需适配长文本、时间线和结构化描述信息

## 设计限制
- 优先使用 Ant Design 的 `Tabs`、`Alert`、`Card`、`Table`、`Modal`、`Drawer`、`Timeline`、`Descriptions`
- 交易中枢应强调风险与处理动作，不要做成偏营销感的页面
- 两个子工作区要保持统一视觉骨架，但颜色和风险提示可区分
