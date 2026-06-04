# 用户与风控中心列表页 UI 设计需求

## 页面标识
- 页面名称：用户与风控中心
- 路由：`/admin/users`
- 对应组件：`AdminUsersPage`
- 页面定位：用户档案总入口，负责用户概览、筛选列表和管理入口

## 项目基线
- 前端技术栈：React 18 + TypeScript + Vite 5 + React Router 6
- UI 架构必须以 Ant Design 5 为主，允许使用 `@ant-design/pro-components`；Tailwind CSS 4 只做布局与间距辅助
- 页面接入 `AdminShellLayout`
- 桌面端优先，建议按 `1440px` 画板设计
- 主题主色 `#6366f1`，成功 `#10b981`，警告 `#f59e0b`，错误 `#ef4444`
- 当前页面数据密度高，优先考虑筛选效率和状态识别

## 页面目标
- 统一查看平台用户规模、角色结构、活跃度和认证状态
- 快速筛到目标账号并进入详情或认证审核工作区
- 支撑账号风控与角色治理

## 信息架构
- 顶部标题、说明、认证审核快捷入口、刷新按钮
- 8 张摘要统计卡
- 列表筛选工具栏
- 用户表格

## 必备模块
- 摘要卡：平台总用户、入驻导师、企业账号、近 7 日活跃、高级会员占比、待认证主体、异常/封禁账号、近 7 日新增
- 筛选项：关键词、角色、账户状态、认证状态
- 用户表格列：身份信息、角色、套餐、认证状态、账户状态、最近登录、操作
- 操作入口：管理、认证审核

## 数据承接
- 主要接口：`GET /admin/users/summary`、`GET /admin/users?page&size&keyword&role&status&approvalStatus`。
- 顶部摘要卡承接 `UserSummaryResponse`：`totalUsers`、`mentorUsers`、`enterpriseUsers`、`premiumUsers`、`pendingApprovalUsers`、`suspendedUsers`、`activeUsers7d`、`newUsers7d`；其中“高级会员占比”由 `premiumUsers / totalUsers` 前端计算。
- 列表筛选参数：`page`、`size`、`keyword`、`role`、`status`、`approvalStatus`，都需要在工具栏有对应控件和回显状态。
- 列表行对象 `UserListItem`：`userId`、`email`、`displayName`、`role`、`tier`、`status`、`approvalStatus`、`createdAt`、`lastLoginAt`。
- 行内动作：进入 `/admin/users/:userId` 查看详情；进入 `/admin/users/reviews/:userId` 直接处理导师/企业认证。
- 状态字段枚举：`role = STUDENT | MENTOR | ENTERPRISE | ADMIN`，`tier = FREE | PREMIUM`，`status = ACTIVE | PENDING | SUSPENDED`，`approvalStatus = PENDING | APPROVED | REJECTED | null`。
- 时间字段格式：列表时间为 `string | null`；注册时间、最近登录时间都可能为空，需要设计缺省态。

## 关键交互
- 筛选和搜索必须支持组合使用
- 点击“管理”进入 `/admin/users/:userId`
- 点击“认证审核”进入 `/admin/users/reviews/:userId`
- 刷新动作要保留当前筛选条件

## 设计限制
- 优先使用 Ant Design 的 `Card`、`Table`、`Input.Search`、`Select`、`Badge`、`Tag`、`Button`
- 列表页不需要过重装饰，重点是状态可读、筛选清楚、操作直达
- 摘要卡和表格之间要有明显层级过渡
