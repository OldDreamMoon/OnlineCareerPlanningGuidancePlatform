# 运行配置中心 AI 渠道开关子路由 UI 设计需求

## 页面标识
- 页面名称：运行配置中心 / AI 渠道开关
- 子路由：`/admin/runtime?tab=ai-channels`
- 对应组件片段：`AdminSystemSettingsPage -> renderAiChannels()`
- 页面定位：AI 渠道健康度、场景运行摘要与高频直改工作区

## 项目基线
- 前端技术栈：React 18 + TypeScript + Vite 5 + React Router 6
- UI 架构必须以 Ant Design 5 为主，允许使用 `@ant-design/pro-components`；Tailwind CSS 4 只做布局与间距辅助
- 页面接入 `AdminShellLayout`
- 桌面端优先，建议按 `1440px` 画板设计
- 主题主色 `#6366f1`，成功 `#10b981`，警告 `#f59e0b`，错误 `#ef4444`

## 页面目标
- 在运行配置中心内直接观察 provider、route、prompt 的运行状态
- 把 sceneCode 级 AI 场景指标与配置项联动展示
- 支持少量高频直改，复杂编辑继续导向 AI 网关
- 明确本页是“运行快照 + 快捷入口”，不承接正式积分计费与用户权益策略

## 信息架构
- 风险告警区
- 概览统计卡区
- 场景摘要区
- 场景排行区
- 关键 Provider 运行态区
- 执行结构摘要区
- 快捷进入区
- Prompt 模板查看/试渲染/版本对比弹层

## 必备模块
- 风险告警：provider 异常、场景指标加载失败、provider/route/template 详情加载失败
- 概览卡：Provider 可用度、Route 激活度、执行模式分布、Prompt 生效口径
- 场景摘要：活跃场景、总调用量、预估成本、低成功率场景、最高成本场景、重点关注场景
- 场景排行卡：展示前 5 个高调用场景
- 单场景卡必须包含：
- 场景名称、所属域、`channelCode`、`sceneCode`、前台入口、最近调用时间
- 主路由摘要、Provider/模型、执行模式、优先级、模板版本情况
- 高频直改：路由启停、执行模式切换、优先级调整
- 模板动作：查看模板、发布模板、回滚模板、深入编辑
- Provider 运行态卡：启停开关、调用量、成功率、平均延迟、运行状态
- 快捷进入：AI 应用运营台、AI 网关服务商、AI 网关场景路由、AI 网关提示词维护、内容治理中心

## 数据承接
- 主要接口：`GET /admin/system/console-snapshot`、`GET /admin/ai/application-scene-metrics`、`GET /admin/ai/providers`、`GET /admin/ai/routes`、`GET /admin/ai/prompt-templates`、`PUT /admin/ai/providers/:id`、`PUT /admin/ai/routes/:id`、`POST /admin/ai/prompt-templates/:id/publish`、`POST /admin/ai/prompt-templates/:id/rollback`、`POST /admin/ai/prompt-templates/render-preview`、`PUT /admin/ai/prompt-templates/:id`。
- 顶部概览复用 `ConsoleSnapshotPayload.aiChannels`，关键字段包括 `enabledProviders`、`healthyProviders`、`enabledRoutes`、`sceneBoundRoutes`、`syncBlockingRoutes`、`streamRoutes`、`asyncRoutes`、`realtimeRoutes`、`activePromptTemplates`、`draftPromptTemplates`、`inactivePromptTemplates`、`keyProviders[]`。
- 场景卡行对象 `RuntimeSceneMetricRow`：`sceneKey`、`channelCode`、`displayName`、`ownerDomain`、`frontEntry`、`summary`、`registered`、`taskType`、`sceneCode`、`calls`、`successCalls`、`successRate`、`avgLatencyMs`、`totalCost`、`lastCallAt`、`primaryRoute`、`routeCount`、`activeTemplate`、`latestTemplate`、`hasAnyTemplateVersion`、`templateVersionCount`、`historicalTemplateCount`、`rollbackTargetTemplate`。
- 关键 Provider 卡对象 `AiChannelProviderItem`：`providerId`、`providerCode`、`providerDisplayName`、`providerType`、`enabled`、`runtimeStatus`、`successRate`、`totalCalls`、`avgLatencyMs`、`lastEventAt`。
- 高频直改载荷：
- Provider 启停：`PUT /admin/ai/providers/:id`，复用 provider 全量对象，只切 `enabled`
- Route 启停 / 模式 / 优先级：`PUT /admin/ai/routes/:id`，复用 route 全量对象，主要修改 `enabled`、`executionMode`、`priorityNo`
- 模板动作：发布 `POST /admin/ai/prompt-templates/:id/publish`；回滚 `POST /admin/ai/prompt-templates/:id/rollback`，请求体 `{ targetTemplateId }`
- 模板弹层数据：查看模板正文与 `bundleJson`、`variablesJson`，试渲染表单只提交 `renderVariablesJson`，直改表单只编辑 `{ description, templateFormat, content, variablesJson, bundleJson }`。
- 时间字段格式：本页统一兼容 `number | string | null`；AI 成本为人民币字符串（元）；模板变量和 Bundle 为 JSON 字符串。

## 关键交互
- 场景卡应把“指标”和“配置”放在同一个工作视图里
- 低成功率、高成本、未登记目录等风险标签必须明显
- 允许直接启停 key provider 和主 route，但复杂变更需有“深入编辑”入口
- 模板弹层需支持变量自动填充、试渲染、当前版本与历史版本对比
- 本页不能再出现正式“每日免费额度 / 单次积分 / 服务层级权益”编辑表格，这些能力统一回到 AI 应用运营台

## 设计限制
- 优先使用 Ant Design 的 `Alert`、`Card`、`Switch`、`Select`、`InputNumber`、`Tag`、`Modal`
- 这是高信息密度配置页，优先强调层次与决策效率，不要追求大面积留白
- 场景、Provider、模板三个层级必须能被视觉上快速区分
