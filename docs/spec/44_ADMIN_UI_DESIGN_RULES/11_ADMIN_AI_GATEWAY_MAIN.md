# AI 网关管理台主路由 UI 设计需求

## 页面标识
- 页面名称：AI 网关管理台
- 路由：`/admin/ai/gateway`
- 对应组件：`AdminAiGatewayPageRefit`
- 页面定位：AI 技术治理主控台，负责服务商、固定场景路由、固定场景提示词与调用日志；不再承载运营计费、免费额度与用户权益配置

## 项目基线
- 前端技术栈：React 18 + TypeScript + Vite 5 + React Router 6
- UI 架构必须以 Ant Design 5 为主，允许使用 `@ant-design/pro-components`；Tailwind CSS 4 只做布局与间距辅助
- 页面接入 `AdminShellLayout`
- 桌面端优先，建议按 `1440px` 画板设计
- 主题主色 `#6366f1`，成功 `#10b981`，警告 `#f59e0b`，错误 `#ef4444`
- 该页为高复杂度后台技术工作台，需要明显的主导航与子工作区层次

## 页面目标
- 统一承载服务商、模型、场景路由、模板细节与日志的后台治理
- 在统一页面框架内承接顶部概览与下方工作区，不再在页内重复一级 tab
- 为运行配置中心和 AI 应用运营台提供技术底盘，但不重复运营侧经营摘要和定价策略
- 明确承接普通 / VIP / 通用三层路由差异，但不承接积分定价、免费额度和运营权益说明

## 信息架构
- 顶部标题、说明、全局刷新
- 初始化错误/局部错误提示区
- 顶部 4 张摘要卡
- 壳层二级导航负责主工作区切换
- `场景维护` 内部再分 `场景路由 / 提示词维护` 双工作面
- `服务商` 工作区内部同时承接运行态摘要与诊断开关
- `调用日志` 使用右侧抽屉承接完整排障详情

## 必备模块
- 摘要卡：启用服务商、已登记模型、固定场景路由策略、生效模板
- 壳层二级导航：场景维护、服务商、调用日志
- 场景维护页内工作面：场景路由、提示词维护
- 支持通过 query `tab` 定位子工作区
- 当带 `traceId` 打开时默认进入日志工作区

## 数据承接
- 主路由初始化接口：`GET /admin/ai/meta`、`GET /admin/ai/providers`、`GET /admin/ai/route-policies`、`GET /admin/ai/routes`、`GET /admin/ai/prompt-templates`、`GET /admin/ai/provider-runtime-stats?...`、`GET /admin/ai/logs?...`。
- 固定场景清单来自前后端统一场景注册表 `adminAiChannelCatalog` / `AiApplicationSceneRegistry`，页面必须全量展示这些真实场景。
- 元信息对象 `MetaPayload`：`providerTypes[]`、`taskTypes[]`、`executionModes[]`、`tierOptions[]`、`routeStrategyTypes[]`，用于所有下级表单的选项源。
- 主路由共享底表对象：`ProviderItem`、`ProviderModelItem`、`RoutePolicyItem`、`RouteCandidateItem`、`PromptTemplateItem`、`AiLogItem`；各子页都从这些真相层对象切片展示，不再单独造静态结构。
- 用户层级路由差异以 `RoutePolicyItem.userTier = ALL | FREE | PREMIUM` 为真相层；其中 `ALL` 代表通用回退策略，`FREE / PREMIUM` 代表普通 / VIP 显式覆盖。
- 顶部 4 张摘要卡由全量列表前端汇总：启用服务商数、模型登记数、固定场景路由策略数、生效模板数。
- URL 规则：`tab` 控制主工作区，`traceId` 存在时默认切到 `logs` 工作区并带入追踪筛选；旧 `tab=routes|templates` 兼容映射到 `scenes`，旧 `tab=runtime` 统一外跳 `/admin/runtime?tab=ai-channels`。
- 本页还承接多个二级弹层：模板渲染预览、服务商连通测试结果、日志详情抽屉。
- 时间字段格式：AI 网关主路由统一兼容 `number | string | null`；成本字段多为人民币字符串（元）；扩展配置和结果载荷均以 JSON 字符串承接。

## 关键交互
- 顶部概览保持全局固定语义，工作区内容按需切换
- 壳层导航切换不能丢失当前治理语义；页内仅保留场景维护的双工作面切换
- 初始化失败、局部失败、加载中都要有统一后台风格反馈
- 该页默认承接技术治理动作，任何“运营热榜 / 重点用户排行 / 业务增长解读”都应留在 AI 应用运营台
- 当前页不再鼓励新增业务场景；如需新增固定场景，应先修改场景注册表、自举链路和后端调用契约，再回到页面侧维护
- 当前调用日志已记录 `userTier` 供技术观察，但日志列表筛选当前仍以 `taskType / sceneCode / provider / status / traceId / userId` 为主；若后续需要 `model / userTier` 级筛选，应作为后端检索增强单独实施
- 调用日志抽屉必须承接用户、令牌、积分权重、治理留痕、当前链路快照与原始结果 JSON，保证技术排障闭环

## 当前缺口与 P0 收口要求
- `scenes` 工作区中的 `FREE / PREMIUM / ALL` 路由差异不能只在管理员预览中生效；学生 AI、导师准备单、社区自动首评、语音面试、面试总结等真实调用链必须把 `userTier` 传入网关解析层，确保运行时命中与后台试算一致。
- `scenes` 工作区当前同时承载“场景路由”和“提示词维护”两块内容，页面内必须提供明确的二级工作面切换或锚点定位；从场景卡、日志抽屉跳转到模板维护时，应直接切到模板工作面并聚焦目标模板，而不是只改内部选中态。
- 当场景存在可维护模板缺口时，页面不能只提示“暂无可维护模板”；至少要支持创建首个模板版本，并让管理员继续完成绑定或发布。
- 提示词维护必须补齐“从现有版本创建新草稿版本”的正规版本流，不再长期依赖直接编辑当前版本后再发布。
- 页面摘要、模板绑定数和“已上线”口径应优先以启用中的 provider / route / policy / template 关系为准，避免停用配置继续占用主视图统计。

## 设计限制
- 页面主体优先使用 Ant Design 的 `Card`、`Alert`、`Button`；如需场景内分面切换，只在局部使用轻量 tab/分段器
- 该页是技术后台，不要求运营感 Hero，但要求强秩序、强密度、强操作感
- 子 tab 设计稿必须共享同一页面框架和视觉体系
