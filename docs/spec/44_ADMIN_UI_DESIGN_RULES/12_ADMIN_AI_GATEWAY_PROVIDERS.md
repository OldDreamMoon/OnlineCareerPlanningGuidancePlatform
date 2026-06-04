# AI 网关服务商子路由 UI 设计需求

## 页面标识
- 页面名称：AI 网关 / 服务商
- 子路由：`/admin/ai/gateway?tab=providers`
- 对应组件片段：`AdminAiGatewayPage -> renderProviders()`
- 页面定位：维护上游渠道接入信息、模型池与模型级成本价格，同时观察服务商运行态

## 项目基线
- 前端技术栈：React 18 + TypeScript + Vite 5 + React Router 6
- UI 架构必须以 Ant Design 5 为主，允许使用 `@ant-design/pro-components`；Tailwind CSS 4 只做布局与间距辅助
- 页面接入 `AdminShellLayout`
- 桌面端优先，建议按 `1440px` 画板设计
- 主题主色 `#6366f1`，成功 `#10b981`，警告 `#f59e0b`，错误 `#ef4444`
- 该子页需要承载高密度技术状态信息

## 页面目标
- 管理服务商池配置
- 在每个服务商下维护“支持的模型 ID + 对应人民币成本价格”
- 快速看清最近 24 小时服务商健康波动，为异常 provider 的深入处理提供主入口

## 信息架构
- 运行态摘要卡区
- 服务商运行态列表
- 服务商池表格
- 服务商详情/编辑抽屉
- 模型清单与模型成本表

## 必备模块
- 服务商运行态列表：健康、波动、异常、空闲、停用
- 每个服务商的调用量、成功率、平均延迟、小时块热力
- 服务商池表格
- 模型成本表：模型 ID、显示名、状态、输入单价、输出单价、适用能力、上下文长度
- 动作：刷新、新增服务商、编辑服务商、测试连通性

## 数据承接
- 主要接口：`GET /admin/ai/providers`、`GET /admin/ai/provider-runtime-stats?hours=24&timezone=...`、`POST /admin/ai/providers/:id/connectivity-test`、`POST /admin/ai/providers`、`PUT /admin/ai/providers/:id`。
- 服务商对象 `ProviderItem`：`id`、`providerCode`、`providerType`、`displayName`、`baseUrl`、`enabled`、`timeoutMs`、`maxRetries`、`apiKeyMasked`、`hasApiKey`、`extraConfigJson`、`models[]`、`createdAt`、`updatedAt`。
- 模型对象 `ProviderModelItem`：`id`、`providerId`、`modelCode`、`displayName`、`enabled`、`inputCostPer1k`、`outputCostPer1k`、`contextWindow`、`maxOutputTokens`、`supportedTaskTypesJson`、`notes`、`createdAt`、`updatedAt`。
- 运行态对象 `ProviderRuntimeStatsPayload`：总览字段 `totalProviders`、`healthyProviders`、`degradedProviders`、`downProviders`、`idleProviders`、`disabledProviders`，以及 `providers[]` 明细。
- 单个运行态明细字段：`providerId`、`providerCode`、`providerDisplayName`、`providerType`、`enabled`、`runtimeStatus`、`successRate`、`totalCalls`、`avgLatencyMs`、`lastEventAt`、`blocks[]`；其中 `blocks[]` 代表按小时切片的调用块，包含 `label`、`calls`、`successCalls`、`avgLatencyMs`、`status`。
- 连通测试返回 `ProviderConnectivityPayload`：`providerId`、`providerCode`、`providerDisplayName`、`providerType`、`baseUrl`、`probeUrl`、`reachable`、`authenticated`、`available`、`httpStatus`、`latencyMs`、`status`、`message`、`checkedAt`。
- 服务商表单字段：`providerCode`、`providerType`、`displayName`、`baseUrl`、`apiKey`、`enabled`、`timeoutMs`、`maxRetries`、`extraConfigJson`。
- 模型表单字段：`modelCode`、`displayName`、`enabled`、`inputCostPer1k`、`outputCostPer1k`、`contextWindow`、`maxOutputTokens`、`supportedTaskTypesJson`、`notes`。
- 时间字段格式：本页统一兼容 `number | string | null`；成本与成功率字段以字符串返回，前端负责格式化。

## 关键交互
- 点击服务商应支持进入编辑或打开详情抽屉
- 运行态块需要可 hover 查看详细统计
- 异常状态必须在列表层面快速识别
- 模型列表必须允许直接编辑成本价格，避免价格散落在 route 或 `extraConfigJson`
- 同一 provider 下不同模型价格差异必须可视化对比
- 普通 / VIP 是否命中同一模型不在本页决定；本页只维护“这个渠道能提供哪些模型以及它们的真实成本”，层级差异交给路由页选择

## 设计限制
- 优先使用 Ant Design 的 `Card`、`Table`、`Tag`、`Tooltip`、`Button`、`Modal/Drawer`
- 运行态块需要在桌面端可横向展开，不做移动端压缩方案
- 技术状态视觉要强，但不能像监控大屏
