# 管理员登录页 UI 设计需求

## 页面标识
- 页面名称：管理员登录页
- 路由：`/admin/login`
- 对应实现：`AdminLoginPage -> /auth?mode=login&panel=admin-login`，实际 UI 承载组件为 `AuthPage`
- 页面定位：管理员进入后台前的独立登录入口，不使用后台壳层

## 项目基线
- 前端技术栈：React 18 + TypeScript + Vite 5 + React Router 6
- UI 架构必须以 Ant Design 5 为主，允许参考 `@ant-design/pro-components`；Tailwind CSS 4 只做布局辅助
- 当前页面不接入 `AdminShellLayout`，是全屏登录页
- 桌面端优先，建议按 `1440px` 画板设计
- 主题主色 `#6366f1`，成功 `#10b981`，警告 `#f59e0b`，错误 `#ef4444`
- 设计结果需保留共享认证页的品牌感和独立管理员身份识别

## 页面目标
- 让管理员快速、安全地进入后台
- 视觉上与普通学生/导师/企业登录区分开
- 保留共享认证系统的品牌一致性

## 信息架构
- 全屏沉浸式背景
- 居中单卡片登录容器
- 管理员身份标识区
- 管理员邮箱输入
- 管理员密码输入
- 登录反馈区
- 主操作按钮

## 必备模块
- 管理员专属标题、副标题、身份标签
- 邮箱输入框与密码输入框
- 错误、成功、处理中三态反馈
- “进入管理员后台”主按钮
- 可保留品牌吉祥物或安全感插画，但不能喧宾夺主

## 数据承接
- 登录表单状态：`adminLoginForm = { email: string; password: string }`，页面只承接管理员邮箱和密码两个核心字段。
- 反馈区状态：`adminLoginFeedback = { tone: "info" | "error" | "success"; message: string }`，设计稿需要预留固定反馈容器，不只依赖 toast。
- 页面入口：后台登录真实承载仍是 `AuthPage` 的 `panel=admin-login` 分支，`/admin/login` 只是管理员登录别名路由。
- 提交结果：登录成功后前端还会校验当前账号角色是否为 `ADMIN`，非管理员账号也要回到页内错误反馈态。
- 成功跳转：鉴权通过后进入 `/admin/dashboard`，失败时停留当前页并保留输入内容。
- 字段格式：邮箱和密码均为字符串；反馈消息为短文本；不需要额外验证码或多因素字段。

## 关键交互
- 输入聚焦、校验失败、提交中状态必须清晰
- 登录失败反馈需要固定展示区域，避免只用 toast
- 回车可提交
- 成功后进入 `/admin/dashboard`

## 设计限制
- 组件结构优先使用 Ant Design 的 `Card`、`Form`、`Input`、`Alert`、`Button`
- 风格可以比普通登录更克制、更安全感，但不要做成企业 OA 风格
- 当前阶段不要求移动端单独稿
