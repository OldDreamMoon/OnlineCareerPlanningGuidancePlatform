# 运行配置中心运营参数子路由 UI 设计需求

## 页面标识
- 页面名称：运行配置中心 / 运营参数
- 子路由：`/admin/runtime?tab=operations`
- 对应组件片段：`AdminSystemSettingsPage -> renderOperations()`
- 页面定位：运行观测参数与平台运营阈值维护页

## 项目基线
- 前端技术栈：React 18 + TypeScript + Vite 5 + React Router 6
- UI 架构必须以 Ant Design 5 为主，允许使用 `@ant-design/pro-components`；Tailwind CSS 4 只做布局与间距辅助
- 页面接入 `AdminShellLayout`
- 桌面端优先，建议按 `1440px` 画板设计
- 主题主色 `#6366f1`，成功 `#10b981`，警告 `#f59e0b`，错误 `#ef4444`

## 页面目标
- 分开维护 AI 运行观测参数与平台运营阈值
- 让管理员快速理解每个参数对日志量、排障效率、治理阈值的影响
- 保留“当前值 vs 默认值”的对照关系

## 信息架构
- 顶部说明告警
- 左侧 AI 观测参数主表单
- 右侧平台运营参数侧卡

## 必备模块
- 顶部告警：说明“运营参数负责运行观测与治理阈值”
- AI 观测参数卡：
- 调试日志开关
- 请求摘要日志开关
- 当前值与默认值对照
- 最近一次运行参数更新时间
- 平台运营参数卡：
- 举报自动隐藏阈值数值展示
- 阈值输入控件
- 当前快照时间
- 风险保护层开启数量
- 保存按钮：保存运行观测参数、保存运营参数

## 数据承接
- 主要接口：`GET /admin/system/console-snapshot`、`PUT /admin/ai/runtime-settings`、`PUT /admin/content/moderation/policies`。
- AI 观测参数对象 `RuntimeSettingsPayload`：`debugModeEnabled`、`aiRequestLogEnabled`、`defaultDebugModeEnabled`、`defaultAiRequestLogEnabled`、`updatedAt`。
- AI 观测参数保存载荷：`PUT /admin/ai/runtime-settings`，请求体 `{ debugModeEnabled: boolean; aiRequestLogEnabled: boolean }`。
- 平台运营参数对象复用 `ModerationPoliciesResponse`：`aiInputEnabled`、`aiOutputEnabled`、`communityStrictReviewEnabled`、`autoHideReportThreshold`；其中本子页主要编辑 `autoHideReportThreshold`，其余字段用于右侧联动说明。
- 运营参数保存接口：`PUT /admin/content/moderation/policies`，提交整个对象，而不是只提交阈值字段。
- 当前值 vs 默认值的对照只存在于 AI 观测参数卡，运营阈值卡主要展示当前数值和风险影响。
- 时间字段格式：`updatedAt` 兼容 `number | string | null`；阈值字段为数字；布尔开关直接对应 `Switch` 状态。

## 关键交互
- 两块配置区应支持并行理解，不应互相干扰
- 调试日志与请求摘要日志要明确展示其排障价值和日志成本
- 举报自动隐藏阈值应被设计成可感知强度的数字配置，而非普通输入框
- 用户保存后应在原位获得反馈，不需要跳转

## 设计限制
- 优先使用 Ant Design 的 `Form`、`Switch`、`InputNumber`、`Card`、`Alert`、`Button`
- 页面需要保持“主配置区 + 解释型侧栏”的宽屏布局
- 数值阈值和布尔开关要有不同的视觉语义
