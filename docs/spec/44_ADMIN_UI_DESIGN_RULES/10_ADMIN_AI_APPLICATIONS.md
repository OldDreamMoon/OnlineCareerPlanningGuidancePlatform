# AI 应用运营台 UI 设计需求

## 页面标识
- 页面名称：AI 应用运营台
- 路由：`/admin/ai/applications`
- 对应组件：`AdminAiApplicationsPage`
- 页面定位：从业务场景经营与权益策略视角总览 AI 应用；AI 网关负责技术治理，本页负责运营定价、免费额度、用户分层与经营结果判断

## 项目基线
- 前端技术栈：React 18 + TypeScript + Vite 5 + React Router 6
- UI 架构必须以 Ant Design 5 为主，允许使用 `@ant-design/pro-components`；Tailwind CSS 4 只做布局与间距辅助
- 页面接入 `AdminShellLayout`
- 桌面端优先，建议按 `1440px` 画板设计
- 主题主色 `#6366f1`，成功 `#10b981`，警告 `#f59e0b`，错误 `#ef4444`
- 页面以桌面端运营工作台为主，允许进行业务策略编辑，但不承载 provider / route / template 级技术深改

## 页面目标
- 让管理员从“业务场景”而不是“技术对象”看 AI
- 看清不同 AI 功能的免费额度、积分定价、用户层级权益与成本回收压力
- 让“普通 / VIP”两种服务档位的权益差异在运营侧可配置、可解释
- 在不重复 AI 网关技术数据的前提下，给出进入 AI 网关与运行配置的下一步入口
- 明确告诉管理员哪些场景当前共用同一套计费规则，避免把后端尚未拆开的共享策略误读成场景独立策略

## 信息架构
- 顶部 Hero 区
- 4 张摘要卡
- 经营榜单区
- 业务场景策略表
- 用户分层与计费策略区

## 必备模块
- Hero 区标签、说明、跳转 AI 网关、跳转运行配置
- 摘要卡：近 N 日活跃业务场景、近 N 日总调用、近 N 日上游总成本、当前启用的权益策略数
- 榜单：热门场景榜、成本关注榜、风险处理榜、静默待激活榜
- 业务场景策略表列：业务场景、归属域、服务层级、每日免费额度、单次积分、每日上限、模型偏好、最大输入、当前经营状态、下一步
- 用户分层区：普通 / VIP 权益摘要、差异说明、是否允许高质量模型或语音能力
- 下一步动作：去运行配置 AI 渠道、去 AI 网关路由、去 AI 网关提示词、进入 AI 网关继续排障

## 数据承接
- 主要接口：`GET /admin/ai/application-ops?days=...`、`GET /admin/ai/quota-policies`、`PUT /admin/ai/quota-policies/:id`。
- 本页直接消费后端聚合后的经营口径真相层，同时将底层 `quota-policies` 按固定业务场景映射为“运营策略”工作面。
- 当前运营权益真相层已对 `COMMUNITY_REPLY` 的部分固定场景支持 `sceneCode` 精确覆写，其余大多数能力仍按 `tier + taskType` 结算；因此页面必须同时区分“场景独立策略”“共享套餐策略”“随主会话计费”，而不是把所有场景都伪装成完全独立的后端配置。
- 顶部摘要对象 `overview` 用于承接：`activeScenes`、`totalCalls`、`totalCost`、`riskPendingCount`、`silentSceneCount` 等经营摘要。
- 榜单对象：
  - `hotScenes[]`：热门场景榜，强调调用量与活跃度
  - `costScenes[]`：成本关注榜，强调成本消耗与单位调用压力
  - `riskScenes[]`：风险处理榜，强调缺模板、停用、异常或配置缺口
  - `silentScenes[]`：静默待激活榜，强调长时间无调用或已登记未起量场景
- 经营总表对象 `records[]` 至少支持：`sceneCode`、`sceneName`、`taskType`、`ownerDomain`、`calls`、`successRate`、`avgLatencyMs`、`totalCost`、`status`、`riskSummary`、`nextAction`。
- 运营策略对象可由固定场景映射层聚合，至少支持：`channelCode`、`channelName`、`tier`、`dailyFreeLimit`、`pointsPerCall`、`dailyMaxLimit`、`modelPreference`、`maxInputTokens`、`linkedTaskTypes[]`。
- 若本页展示共享策略，需要额外表达：`policyScope = SHARED_TASK_TYPE | SCENE_OVERRIDE`、`sharedSceneCodes[]` 或同等可读信息，方便说明当前策略影响范围。
- 时间字段格式：本页统一兼容 `number | string | null`；AI 成本字段为人民币字符串（元）；本页不默认展开 JSON 技术字段。

## 关键交互
- 每条场景记录都要提供下一步治理入口
- 榜单与总表要支持对“成本高”“配置缺口”“长时间静默”“风险未收口”的场景快速识别
- 页面需要支持运营策略编辑，包括每日免费额度、单次积分、每日上限与模型偏好
- 页面不负责 provider / route / prompt 的深度编辑，但必须能把管理员送到 AI 网关或运行配置的对应工作区
- 当某条运营策略会影响多个场景时，需要在卡片或表格中直接解释“共享范围”，不要让管理员误以为改的是单一场景

## 当前缺口与扩展
- 现阶段已为 `COMMUNITY_PRE_ANSWER`、`MENTOR_PREP_SHEET_GENERATE` 落地独立 scene 配额策略，运营台可分别维护普通 / VIP 的免费额度、单次积分和每日上限。
- `INTERVIEW_TEXT` 仍按主会话包共享配额；`INTERVIEW_ANSWER_HELPER`、`INTERVIEW_VOICE_TRANSCRIBE`、`INTERVIEW_SUMMARY` 在运营台按“随主会话计费”展示，不提供单独扣分入口。
- 若后续答辩或真实运营需要把更多共享 `taskType` 的场景继续拆成独立积分定价，应继续扩展 `scene_code` 维度并将配额解析链路从 `taskType` 升级为 `taskType + sceneCode` 优先匹配。

## 设计限制
- 优先使用 Ant Design 的 `Card`、`Table`、`Tag`、`Button`、`Alert`
- 视觉重点放在“场景经营状态”和“权益策略”而不是技术字段堆砌
- 不要在本页重复展示 provider / route / template 级技术细节，避免与 AI 网关信息冗余
- 表格宽度较大，需按桌面端横向信息密度设计
