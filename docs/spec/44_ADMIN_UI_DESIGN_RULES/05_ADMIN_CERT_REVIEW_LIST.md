# 认证审核工作区列表页 UI 设计需求

## 页面标识
- 页面名称：认证审核工作区
- 路由：`/admin/users/reviews`
- 对应组件：`AdminUserCertificationReviewsPage`
- 页面定位：导师与企业认证审核的工作台入口

## 项目基线
- 前端技术栈：React 18 + TypeScript + Vite 5 + React Router 6
- UI 架构必须以 Ant Design 5 为主，允许使用 `@ant-design/pro-components`；Tailwind CSS 4 只做布局与间距辅助
- 页面接入 `AdminShellLayout`
- 桌面端优先，建议按 `1440px` 画板设计
- 主题主色 `#6366f1`，成功 `#10b981`，警告 `#f59e0b`，错误 `#ef4444`
- 页面需要突出审核效率和待审优先级

## 页面目标
- 快速处理导师和企业的认证申请
- 支持按角色、状态、关键词筛选审核队列
- 为审核详情工作区提供稳定入口

## 信息架构
- 顶部标题、说明、返回用户中心入口
- 筛选工具栏
- 审核列表表格

## 必备模块
- 筛选项：关键词、角色、状态
- 列表字段：用户身份、角色、当前认证状态、提交编号、实名/企业信息、材料数量、提交时间、审核时间
- 行内动作：进入审核详情
- 列表默认偏向待审核状态

## 数据承接
- 主要接口：`GET /admin/users/certification-reviews?page&size&keyword&role&status`。
- 默认筛选：页面首屏默认 `status = PENDING`，设计稿要支持“待审核优先”的默认选中态。
- 列表筛选参数：`page`、`size`、`keyword`、`role`、`status`，角色只包含 `MENTOR | ENTERPRISE`。
- 列表行对象 `ReviewListItem`：`userId`、`email`、`displayName`、`role`、`approvalStatus`、`submissionId`、`submissionStatus`、`realName`、`companyName`、`jobTitle`、`activeAssetCount`、`primaryAssetName`、`submittedAt`、`reviewedAt`。
- 关键信息差异：导师侧重点是真实姓名和岗位；企业侧还要展示企业名；附件区承接 `activeAssetCount` 与首个材料名 `primaryAssetName`。
- 行跳转：进入 `/admin/users/reviews/:userId` 后读取完整提交链，不在列表页内直接审核。
- 时间字段格式：提交时间、审核时间为 `string | null`；认证状态与提交状态都使用 `PENDING | APPROVED | REJECTED`。

## 关键交互
- 点击行或动作进入 `/admin/users/reviews/:userId`
- 筛选状态与分页需可持续
- 支持从列表直接识别导师/企业差异

## 设计限制
- 优先使用 Ant Design 的 `Table`、`Select`、`Input.Search`、`Badge`、`Tag`、`Button`
- 设计重点是审核效率，不需要做复杂图表
- 待审核、已通过、已驳回三种状态必须有明确视觉区分
