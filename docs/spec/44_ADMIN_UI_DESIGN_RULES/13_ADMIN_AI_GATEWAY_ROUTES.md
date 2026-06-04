# AI 网关场景路由子路由 UI 设计需求

## 页面标识
- 页面名称：AI 网关 / 场景路由
- 子路由：`/admin/ai/gateway?tab=routes`
- 兼容规则：旧 `?tab=scenes` 统一映射到 `routes`
- 对应组件片段：`AdminAiGatewayPage -> routes tab`
- 页面定位：固定 AI 业务场景的渠道、模型、用户层级与故障切换策略维护台

## 页面目标
- 不再提供“任意新增业务场景”的主流程，只维护当前已落地场景的路由策略
- 以“一个场景 + 一个服务层级”作为最小治理单元
- 让管理员在同一工作面里完成渠道选择、模型选择、策略切换、候选顺序 / 权重调整、日志联查与路由试算
- 让普通 / VIP 差异真正落在路由选择层，而不是回到运营台或服务商页重复配置

## 信息架构
- 顶部说明：明确 AI 网关已按“固定场景 + 固定服务层级”治理
- 路由摘要区：固定场景数、已配置策略数、普通/VIP 差异策略数、待补候选策略数
- 场景路由列表：
  - 基本信息：场景名、归属域、前台入口、sceneCode、taskType
  - 策略信息：服务层级、策略类型（单通道 / 故障切换 / 加权分流）、是否启用、备注
  - 候选列表：服务商、模型、优先级、权重、执行模式、模板绑定
  - 排障动作：带入路由试算、查看场景日志、跳去提示词维护
- 右侧试算区：任务类型 + 场景编码 + 用户层级 + 模型偏好 -> 当前命中链路结果

## 数据承接
- 主要接口：`GET /admin/ai/meta`、`GET /admin/ai/route-policies`、`PUT /admin/ai/route-policies/:id`、`GET /admin/ai/routes`、`POST /admin/ai/routes/resolve-preview`、`GET /admin/ai/logs?...`。
- 场景真相层来自固定注册表 `adminAiChannelCatalog` / `AiApplicationSceneRegistry`，页面必须全量展示所有已定义场景，不能只显示有日志样本的场景。
- 路由策略对象 `RoutePolicyItem`：`id`、`policyCode`、`taskType`、`sceneCode`、`tier`、`strategyType`、`enabled`、`notes`、`candidates[]`。
- `tier` 实际取值为 `ALL | FREE | PREMIUM`：`ALL` 作为通用兜底，`FREE / PREMIUM` 作为不同用户层级的覆盖策略；页面需要把覆盖关系表达清楚。
- 候选对象 `RouteCandidateItem`：`routeId`、`routeCode`、`providerConfigId`、`providerCode`、`providerDisplayName`、`providerType`、`modelName`、`priorityNo`、`candidateWeight`、`executionMode`、`enabled`、`promptTemplateName`、`promptTemplateVersionNo`、`extraConfigJson`。
- 路由试算返回 `RoutePreviewPayload`：用于展示当前命中的策略、服务商、模型、执行模式、模板版本、思考配置和成本口径。

## 关键交互
- 策略类型允许在 `单通道 / 故障切换 / 加权分流` 之间切换
- 候选渠道必须支持按优先级或权重维护
- 场景行支持快速跳日志；日志回看也要能反向定位当前路由策略
- 若普通与 VIP 使用不同渠道 / 模型，必须显式展示差异，而不是只靠模型名暗示
- 若场景理论上需要模板但未找到可维护版本，必须给出显式缺口提示
- 若当前只存在 `ALL` 策略，也要明确告诉管理员“普通 / VIP 暂时共用此链路”，避免误判成已拆层

## 当前缺口与 P0 收口要求
- 路由页中的 `FREE / PREMIUM` 差异必须由真实业务调用链消费，不能只在 `POST /admin/ai/routes/resolve-preview` 里可见；凡是已经拿到用户套餐层级的 AI 任务，都必须把 `tier` 继续传给 `AiGatewayService / AiRouteResolver`。
- 新增策略时，页面应优先推荐当前场景尚未覆盖的层级，避免管理员在已有 `ALL` 方案的前提下反复手改 tier 才能补 `FREE` 或 `PREMIUM`。
- 场景卡跳去模板维护时，必须直接切到模板工作面并聚焦当前场景，而不是要求管理员在长页面里二次查找。
- 候选维护的主操作应聚焦在 `服务商 / 模型 / 优先级 / 权重 / 模板绑定 / 启停`，`routeCode / extraConfigJson / systemPrompt` 等偏技术字段应弱化到高级设置区。

## 设计限制
- 必须突出“固定场景路由治理”语义，避免再做成通用配置中心
- 禁止出现“新增业务场景”作为主按钮
- 场景卡允许比普通表格更具信息密度，但仍需保持后台秩序感
- 该工作面只承载技术治理动作，不解释运营价值，不展示业务经营榜单
