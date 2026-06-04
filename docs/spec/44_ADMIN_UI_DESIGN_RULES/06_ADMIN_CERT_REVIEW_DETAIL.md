# 认证审核详情页 UI 设计需求

## 页面标识
- 页面名称：认证审核详情
- 路由：`/admin/users/reviews/:userId`
- 对应组件：`AdminUserCertificationReviewsPage`
- 页面定位：认证材料查看、历史提交回溯和审核结论提交工作区

## 项目基线
- 前端技术栈：React 18 + TypeScript + Vite 5 + React Router 6
- UI 架构必须以 Ant Design 5 为主，允许使用 `@ant-design/pro-components`；Tailwind CSS 4 只做布局与间距辅助
- 页面接入 `AdminShellLayout`
- 桌面端优先，建议按 `1440px` 画板设计
- 主题主色 `#6366f1`，成功 `#10b981`，警告 `#f59e0b`，错误 `#ef4444`
- 该路由仍属于“列表 + 详情工作区”同页模式

## 页面目标
- 让管理员能连续查看主体资料、当前提交、历史版本和材料附件
- 在同页完成通过/驳回审核并填写审核备注
- 降低来回跳转成本

## 信息架构
- 保留左侧或上方列表上下文
- 当前主体信息卡
- 当前提交信息卡
- 材料附件区
- 提交历史时间线
- 审核操作区

## 必备模块
- 主体身份信息：昵称、邮箱、角色、当前认证状态
- 当前提交字段：实名、企业名、岗位、提交时间、审核时间、审核备注
- 材料附件列表，支持预览/下载
- 历史提交时间线
- 审核备注输入框
- 通过、驳回动作按钮

## 数据承接
- 详情接口：`GET /admin/users/:userId/certification-review`，返回 `ReviewDetailResponse = { userId, email, displayName, role, approvalStatus, currentSubmission, submissions[] }`。
- 当前提交对象 `CertificationSubmission`：`submissionId`、`userId`、`role`、`realName`、`companyName`、`jobTitle`、`status`、`current`、`reviewNote`、`previousSubmissionId`、`submittedAt`、`reviewedAt`、`assets[]`。
- 附件对象 `CertificationAsset`：`assetId`、`bucket`、`objectKey`、`originalFilename`、`contentType`、`sizeBytes`、`lifecycleStatus`、`deleteReason`、`uploadedAt`、`deletedAt`。
- 审核提交接口：`POST /admin/users/:userId/certification-review`，请求体 `{ approvalStatus: "APPROVED" | "REJECTED"; reviewNote: string }`。
- 附件预览/下载接口：`GET /certification/assets/:assetId/content`，设计稿要预留“在新窗口打开”或“独立预览层”的入口。
- 历史时间线承接 `submissions[]`，同一主体可能有多次提交，需要显示版本关系和上一版本编号 `previousSubmissionId`。
- 时间字段格式：所有提交/审核/上传时间均为 `string | null`；附件大小为字节整数，前端会转换成 KB/MB。

## 关键交互
- 审核动作提交后需即时刷新列表和详情状态
- 材料预览应在新窗口或明确的预览层中打开
- 详情页必须保留返回列表的明确入口

## 设计限制
- 优先使用 Ant Design 的 `Descriptions`、`Card`、`Timeline`、`Button`、`Tag`、`Alert`
- 审核动作区要固定且清晰，避免淹没在长材料列表中
- 附件区要优先支持桌面端快速浏览和批量查看
