# 用户详情页 UI 设计需求

## 页面标识
- 页面名称：用户详情
- 路由：`/admin/users/:userId`
- 对应组件：`AdminUsersPage`
- 页面定位：在同一用户中心内打开具体账号的档案、风控和操作工作区

## 项目基线
- 前端技术栈：React 18 + TypeScript + Vite 5 + React Router 6
- UI 架构必须以 Ant Design 5 为主，允许使用 `@ant-design/pro-components`；Tailwind CSS 4 只做布局与间距辅助
- 页面接入 `AdminShellLayout`
- 桌面端优先，建议按 `1440px` 画板设计
- 主题主色 `#6366f1`，成功 `#10b981`，警告 `#f59e0b`，错误 `#ef4444`
- 该路由不是纯详情页，而是“列表 + 详情工作区”同页模式

## 页面目标
- 让管理员在不离开用户中心的情况下完成账号管理
- 同时展示账号档案、角色资料、认证入口和操作动作
- 对学生角色补充测试积分操作

## 信息架构
- 保留顶部摘要卡和列表上下文
- 详情工作区主卡
- 基础档案描述区
- 角色资料卡
- 密码重置卡
- 账户与认证操作卡
- 学生积分补发卡

## 必备模块
- 用户头像、昵称、邮箱、角色、状态、认证状态
- 基础描述字段：系统编号、注册时间、最近活跃、套餐等级、认证状态、近 7 天社区贡献分
- 角色资料：学生资料字段或其他角色基本画像
- 密码重置表单
- 账户状态操作：恢复正常、停用账号
- 认证审核入口
- 学生积分补发，仅对学生显示

## 数据承接
- 详情接口：`GET /admin/users/:userId`，详情对象 `UserDetailResponse` 包含 `userId`、`email`、`displayName`、`role`、`tier`、`status`、`approvalStatus`、`createdAt`、`communityScore7d`、`studentProfile | null`。
- 学生资料对象：`studentProfile = { major, grade, targetPosition, skillTags: string[], selfIntro }`；非学生角色该对象可能为 `null`，设计上要支持整块隐藏。
- 账户状态操作载荷：`POST /admin/users/:userId/status`，请求体 `{ status: "ACTIVE" | "SUSPENDED" }`。
- 密码重置载荷：`POST /admin/users/:userId/reset-password`，请求体 `{ newPassword: string }`。
- 学生积分补发载荷：`POST /admin/growth/points/grant`，请求体 `{ userId: number; points: number; reasonCode: string }`，当前主要使用 `reasonCode = "TEST_TOPUP"`。
- 页面仍保留上方用户列表上下文，详情区只是同路由下的工作区展开，不是脱离列表的纯独立详情页。
- 时间字段格式：详情时间为 `string | null`；社区贡献分为数字或 `null`；技能标签为字符串数组。

## 关键交互
- 支持从详情回到 `/admin/users`
- 重置密码、状态切换、补积分都要有明确成功/失败反馈
- 对导师和企业账号保留“去认证审核”入口
- 详情刷新不应影响上方列表筛选状态

## 设计限制
- 结构优先使用 Ant Design 的 `Descriptions`、`Card`、`Form`、`Input`、`InputNumber`、`Button`、`Alert`
- 详情区需要明显强于列表区的视觉聚焦，但不能脱离同页上下文
- 不要设计成纯 CRM 页面，要保留平台治理与风控语义
