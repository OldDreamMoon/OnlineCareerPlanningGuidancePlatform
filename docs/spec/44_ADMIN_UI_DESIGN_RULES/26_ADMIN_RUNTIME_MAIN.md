# 运行配置中心主路由 UI 设计需求

## 页面标识
- 页面名称：运行配置中心
- 主路由：`/admin/runtime`
- 对应组件：`AdminSystemSettingsPage`
- 页面定位：业务开关、运营参数、AI 渠道开关、风险降级开关的统一配置中枢

## 项目基线
- 前端技术栈：React 18 + TypeScript + Vite 5 + React Router 6
- UI 架构必须以 Ant Design 5 为主，允许使用 `@ant-design/pro-components`；Tailwind CSS 4 只做布局与间距辅助
- 页面接入 `AdminShellLayout`
- 桌面端优先，建议按 `1440px` 画板设计
- 主题主色 `#6366f1`，成功 `#10b981`，警告 `#f59e0b`，错误 `#ef4444`
- 别名路由：`/admin/feature-flags`、`/admin/system/settings`、`/admin/settings` 重定向到 `/admin/runtime`

## 页面目标
- 把早期分散的设置页收口为一个治理中心
- 通过 tab 区分配置域，但保持统一的“快照 + 深入编辑”逻辑
- 为 AI 渠道、风险策略和运营参数提供跨页联动入口

## 信息架构
- 页面标题区
- 初始化异常反馈区
- 顶部概览卡区
- 四个 tab 容器
- 模板预览与模板编辑弹层

## 必备模块
- 标题副文案：说明“集中维护业务开关、运营参数、AI 渠道开关与风险降级开关”
- 概览卡：业务开关、运营参数、AI 渠道开关、风险降级开关
- Tab 1：业务开关
- Tab 2：运营参数
- Tab 3：AI 渠道开关
- Tab 4：风险降级开关
- 支持通过 URL `tab` 参数打开指定子页
- 模板弹层：查看 Prompt 模板、模板变量试渲染、版本对比、模板直改

## 数据承接
- 主路由初始化接口：`GET /admin/system/console-snapshot`、`GET /admin/ai/application-scene-metrics`、`GET /admin/ai/providers`、`GET /admin/ai/routes`、`GET /admin/ai/prompt-templates`。
- 控制台快照对象 `ConsoleSnapshotPayload`：`generatedAt`、`featureFlags[]`、`runtimeSettings`、`moderationPolicies`、`aiChannels`。
- 业务开关对象 `FeatureFlagItem`：`key`、`displayName`、`description`、`valueType`、`allowedValues[]`、`currentValue`、`defaultValue`、`overridden`、`updatedBy`、`updatedAt`。
- 运行参数对象 `RuntimeSettingsPayload`：`debugModeEnabled`、`aiRequestLogEnabled`、`defaultDebugModeEnabled`、`defaultAiRequestLogEnabled`、`updatedAt`；风险降级对象 `ModerationPoliciesResponse`：`aiInputEnabled`、`aiOutputEnabled`、`communityStrictReviewEnabled`、`autoHideReportThreshold`。
- AI 渠道总览对象 `AiChannelOverviewPayload`：`totalProviders`、`enabledProviders`、`healthyProviders`、`degradedProviders`、`downProviders`、`idleProviders`、`disabledProviders`、`totalRoutes`、`enabledRoutes`、`sceneBoundRoutes`、`syncBlockingRoutes`、`streamRoutes`、`asyncRoutes`、`realtimeRoutes`、`totalPromptTemplates`、`activePromptTemplates`、`draftPromptTemplates`、`inactivePromptTemplates`、`keyProviders[]`。
- 主路由还会并行加载 `ApplicationSceneMetricItem[]`、`ProviderDetailItem[]`、`RouteDetailItem[]`、`PromptTemplateDetailItem[]`，用于 AI 渠道 tab 和模板弹层。
- 模板弹层附加承接：模板试渲染表单 `renderVariablesJson`、模板直改表单 `{ description, templateFormat, content, variablesJson, bundleJson }`、版本对比目标版本 ID。
- 时间字段格式：运行配置中心统一兼容 `number | string | null`；JSON 文本和变量定义均以字符串形式承接。

## 关键交互
- 顶部概览卡要让管理员先看到全局健康度，再下钻到具体 tab
- tab 切换不应丢失页面主标题和快照心智
- AI 渠道 tab 需要允许跳转到 AI 应用运营台、AI 网关、内容治理中心
- Prompt 模板相关弹层要支持长正文、变量 JSON、Bundle JSON 和版本比较

## 设计限制
- 优先使用 Ant Design 的 `Tabs`、`Card`、`Alert`、`Form`、`Switch`、`Select`、`Modal`
- 整体风格要更像系统控制台，不要做成普通表单页
- 四个配置域需要有明确视觉区隔，但必须共享一套统一的页面骨架
