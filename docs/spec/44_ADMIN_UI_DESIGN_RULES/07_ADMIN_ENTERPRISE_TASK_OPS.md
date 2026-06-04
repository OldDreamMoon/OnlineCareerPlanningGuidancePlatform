# 企业任务治理台 UI 设计需求

## 页面标识
- 页面名称：企业任务治理台
- 路由：`/admin/enterprise/tasks`
- 对应组件：`AdminEnterpriseTaskOpsPage`
- 页面定位：平台巡检企业任务、识别风险并执行最小干预

## 项目基线
- 前端技术栈：React 18 + TypeScript + Vite 5 + React Router 6
- UI 架构必须以 Ant Design 5 为主，允许使用 `@ant-design/pro-components`；Tailwind CSS 4 只做布局与间距辅助
- 页面接入 `AdminShellLayout`
- 桌面端优先，建议按 `1440px` 画板设计
- 主题主色 `#6366f1`，成功 `#10b981`，警告 `#f59e0b`，错误 `#ef4444`
- 页面定位是治理后台，不是企业 ATS

## 页面目标
- 平台视角巡检企业任务总量、风险和积压
- 提供关闭任务、重新开放两个最小干预动作
- 串联企业详情、认证审核和通知运营排查

## 信息架构
- 顶部标题、边界说明、平台干预提醒
- 6 张概览卡
- 列表筛选区
- 任务列表表格
- 任务详情工作区
- 提交质量抽检区
- 平台动作区

## 必备模块
- 概览卡：平台任务总数、招募中任务、需要介入、提交积压、临期压力、关闭未采纳
- 筛选项：关键词、任务状态、风险等级
- 表格字段：任务、企业、状态、提交进度、风险信号、平台动作
- 详情字段：任务摘要、创建/更新时间、截止时间、奖励描述、最近提交、已采纳提交
- 提交质量抽检：审核覆盖率、待处理压力、最近提交活动、平台建议
- 直达动作：查看企业详情、去认证审核、去通知运营、关闭任务、重新开放

## 数据承接
- 主要接口：`GET /admin/enterprise/tasks/overview`、`GET /admin/enterprise/tasks?page&size&keyword&status&riskLevel`、`POST /admin/enterprise/tasks/:taskId/manage`。
- 概览对象 `EnterpriseTaskOpsOverviewPayload`：`totalTaskCount`、`openTaskCount`、`riskyTaskCount`、`highRiskTaskCount`、`staleReviewTaskCount`、`deadlinePressureTaskCount`、`closedWithoutAcceptedTaskCount`。
- 列表筛选参数：`page`、`size`、`keyword`、`status`、`riskLevel`，其中 `status` 主要是 `OPEN | CLOSED`。
- 列表行对象 `EnterpriseTaskOpsRecord`：`taskId`、`enterpriseUserId`、`enterpriseName`、`enterpriseLogoUrl`、`enterpriseApprovalStatus`、`title`、`descriptionSummary`、`descriptionPreview`、`rewardDescription`、`status`、`acceptedSubmissionId`、`submissionCount`、`pendingSubmissionCount`、`acceptedSubmissionCount`、`rejectedSubmissionCount`、`reviewedSubmissionCount`、`deadlineAt`、`closedAt`、`createdAt`、`updatedAt`、`latestSubmissionAt`、`highestRiskLevel`、`riskSignals[]`。
- 风险信号对象 `RiskSignalItem`：`code`、`level`、`label`、`description`，设计上要支持多枚风险标签和展开解释。
- 平台动作载荷：`POST /admin/enterprise/tasks/:taskId/manage`，请求体 `{ action: "CLOSE" | "REOPEN" }`。
- 详情区的“提交质量抽检”和“平台建议”主要由列表对象里的提交数量、最近提交时间、已采纳状态和 `riskSignals[]` 前端推导得出，不是独立详情接口。
- 时间字段格式：企业任务治理页统一兼容 `number | string | null`；提交数量和任务编号为整数。

## 关键交互
- 点击表格行切换下方详情工作区
- 若任务已采纳结果，不允许后台重开
- 关闭/重开都必须是明确确认动作
- 通知排查入口需与“待处理提交/关闭未采纳”场景联动出现

## 设计限制
- 优先使用 Ant Design 的 `Card`、`Table`、`Descriptions`、`Tag`、`Alert`、`Popconfirm`
- 风险信号、任务状态、企业认证状态必须有清晰颜色区分
- 详情区要兼顾治理判断和轻量干预，不做复杂项目管理界面
