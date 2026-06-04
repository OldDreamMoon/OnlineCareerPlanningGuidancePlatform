# AI 模块规范

> **文档状态**：`evolving` · 最后审核：2026-04-18 · 模块架构已重新定义为 Java 内部模块；当前仅 AI 面试 `live` 测试模式增设了可选 Python bridge，本文已按最终实现回写，适合作为论文中的 AI 子系统说明。
>

## 1. 目标与范围
AI 模块是 Java 后端内部的一个独立包（`ai/`），负责统一管理所有 AI 供应商调用。

> **架构决策**：正式 AI 网关继续不独立部署额外 AI 服务，AI 模块集成在 Java 后端内部，遵循模块化单体原则；当前仅 AI 面试 `live` 测试模式增设了一个可选 Python WebSocket bridge，用于浏览器直连 Gemini Live，但它不承载业务真相。

目标：
- 统一 OpenAI 兼容协议，对上层业务屏蔽供应商差异。
- 按任务类型与用户等级路由模型与供应商。
- 实现超时/重试/熔断/回退。
- 追踪调用日志与成本。
- 按用户等级控制配额，与积分系统联动。
- 与内容治理模块联动，实现 AI 输入/输出双向审查。

## 1.1 当前实现边界
- 正式业务真相、配额、历史、总结、通知和审计仍全部收敛在 Java 后端。
- `apps/interview-live-python` 只服务 `live` 实时语音测试模式，不替代正式 `voice-roundtrip` 语音面试主链。
- 当前已部署版本中，AI 模块既支撑学生侧 AI 能力，也支撑管理员后台的 provider / route / template / runtime 运维能力。

## 2. 模块内部结构

```
ai/
├── gateway/           # 核心调用入口
│   ├── AiGatewayService        # 统一调用入口
│   ├── ProviderClient          # 供应商 HTTP 适配（WebClient）
│   └── ProviderClientFactory   # 按供应商创建客户端
├── routing/           # 任务路由
│   ├── ModelRouter             # taskType + tier → provider + model
│   └── RoutingConfigLoader     # 从 DB 读取路由配置
├── prompt/            # Prompt 模板
│   ├── PromptTemplateService   # 模板渲染
│   └── TemplateVariables       # 变量定义
├── quota/             # 配额与成本
│   ├── QuotaChecker            # 配额检查（每日免费 + 积分扣减）
│   ├── CostCalculator          # 成本计算（tokens × 单价）
│   └── AiRateLimiter           # 用户级限流（Redis）
├── resilience/        # 可靠性
│   ├── AiCircuitBreaker        # 熔断（Resilience4j）
│   └── FallbackStrategy        # 回退策略
├── moderation/        # 内容审查
│   ├── ModerationAdapter        # 调用统一审查引擎
│   └── ModerationResultMapper   # 审查结果归一化
├── speech/            # 语音处理
│   ├── SttService
│   └── TtsService
└── log/               # 调用日志
    └── AiCallLogService
```

## 2.1 当前实现快照
- 文本能力已落地：简历优化、文本面试创建/追问/总结、AI 历史回溯。
- 简历优化已支持答辩演示版 SSE：`POST /ai/resume/optimize/stream` 返回 `text/event-stream`，由服务端在拿到最终结果后按总结 / 优势 / 风险 / 建议分段推送；同步接口和 SSE `done.result` 当前都会携带 `recordId`，前端可直接精确绑定本次历史记录并导出 PDF。
- 简历优化上下文已扩展为 `targetRole + targetContext + jobDescription + inputMode`：文本模式与 PDF 模式都会把目标语境、岗位 JD 和文件信息纳入提示词与历史 payload，结果统一收口为中文输出。
- 简历优化的结构化结果已扩展为基础四段结果之外的 `scoreLabel / structureItems / rewriteItems`；若模型未稳定返回，后端会给出可回放的 fallback 结构地图与改写沙盘，避免前端再做本地猜测。
- 简历历史详情现在会直接返回目标岗位、目标语境、输入模式、JD、原始文本 / PDF 文件名等上下文，逐步去除结果页对 localStorage 元数据的依赖。
- 学生端当前的简历 PDF 导出已从浏览器 HTML 打印切换为标准 PDF 渲染：前端基于 `GET /ai/history/resume/{recordId}` 返回的结构化详情，用 `@react-pdf/renderer` 在浏览器侧生成下载文件，并改为按需动态加载导出模块，避免将导出依赖常驻进主业务包。
- 文本面试已支持真流式追问：`POST /ai/interview/sessions/{sessionId}/reply/stream` 会把 `followUpQuestion` 的文字增量以 `reply_delta` 事件持续返回，最终 `done` 再补齐 `coachFeedback / scoreHint / shouldFinish / summary`。
- 语音面试已支持“转写 + 追问”双阶段流式：`POST /ai/interview/sessions/{sessionId}/voice-roundtrip/stream` 先返回 `transcript` 事件，再持续返回 `reply_delta`，前端可同步展示最近转写与 AI 追问生成过程。
- AI 面试当前已形成 `text / voice / live` 三模式：`TEXT / VOICE` 继续走 Java 正式链路；`LIVE` 仅测试模式，创建会话时通过 `sessionContext.answerMode=LIVE` 让 Java 预留积分 / quota 并创建占位会话，实时音频与字幕由独立 `apps/interview-live-python` bridge 对接 Gemini Live，结束后前端再调用 `POST /ai/interview/sessions/{sessionId}/live-transcript` 导回正式 transcript。
- provider 流式策略当前以 OpenAI-compatible 为主链路：优先调用 provider 原生 SSE/token 流，并在网关层从 JSON 输出中增量提取 `followUpQuestion` 字段；其他 provider 允许保持同步 fallback，但对前端仍统一成 SSE 接口契约。
- 流式治理新增 preview 审查：推流过程中用 `previewAiOutput` 对累计 `followUpQuestion` 做阻断/脱敏预检，完成后再执行正式 `moderateAiOutput` 落库，避免审计事件被高频 delta 刷爆。
- 语音能力仍复用 `OpenAiCompatibleClient` 的 `/audio/transcriptions` 做 STT，再进入既有文本面试追问与总结链路；语音会话继续写入 `interview_sessions/interview_messages`，并在 `audio_object_key` 记录占位对象标识。
- TTS 已接入 Gemini Native 原生 `generateContent` 音频格式：请求体使用 `contents[].parts[].text + generationConfig.responseModalities=["audio"] + speechConfig.voiceConfig.prebuiltVoiceConfig.voiceName`，返回 `inlineData` 中的 base64 PCM 音频。
- `/ai/interview` 当前可对 AI 面试官消息触发 TTS 播放：前端把 `audio/L16;codec=pcm;rate=24000` 的 PCM 数据打包为 WAV 后直接播放，仍保留文本展示作为失败回退。
- `apps/interview-live-python` 默认暴露 `/ai/interview/live/ws` 与 `/ai/interview/live/healthz`；它只负责实时音频 / 字幕 / token 事件桥接，不替代 Java 侧的会话、历史、总结、通知与配额体系。
- 管理后台 `/admin/ai/gateway` 已支持 provider / route CRUD、路由命中预览、AI 日志筛选、成本看板，以及 OpenAI-compatible / Gemini Native / NewAPI 场景提示与预设。
- Gemini Native 的媒体直传链路已按 NewAPI 兼容约束实现：使用 `/v1beta/models/{model}:generateContent`，并通过 `inlineData(base64)` 上传媒体数据，不使用 `fileData.fileUri` / File API。
- Gemini Native 的 JSON 型任务当前不再只依赖 prompt 约束，而是会显式发送 `generationConfig.responseMimeType=application/json + responseJsonSchema`。当前内置 schema 已覆盖：简历优化、文本面试首问/追问/总结、社区预答与导师破冰消息。
- 基于当前 `.env` 指向的 NewAPI Gemini 真实探测，`responseSchema` 与 `responseJsonSchema` 两种 Structured Output 字段当前都可成功返回 JSON；网关默认已收口到更贴近 Gemini 官方 Structured Output 文档的 `responseJsonSchema`。
- AI 网关现已补齐统一的“思考量”抽象：`AiRouteResolver` 会结合 `taskType + sceneCode + executionMode + inputMode` 生成默认推理档位，并允许 provider / route `extra_config_json` 用 `reasoningEffort / thinkingBudget / thinkingLevel` 覆写；当前默认策略大致为“语音关闭、实时交互偏低、简历离线任务偏高、普通同步分析居中”。
- Gemini Native 当前会按模型族自动切换思考量协议：Gemini 2.5 / NewAPI Gemini 兼容链路优先发送 `generationConfig.thinkingConfig.thinkingBudget`，Gemini 3 系列优先发送 `generationConfig.thinkingConfig.thinkingLevel`；`OFF / LOW / MEDIUM / HIGH / DYNAMIC` 会在网关侧统一归一并映射为具体预算或档位。
- `usageMetadata.thoughtsTokenCount` 已纳入运行时元数据：网关会把 `thoughtsTokens / reasoningEffort / thinkingBudget / thinkingLevel` 写入 `ai_call_logs`，并在管理员后台的路由预览与 AI 日志详情中展示；当前仍不会把模型内部思考文本透传给前端。
- STT 链路当前仍按“纯文本转写”处理，不附带 JSON 结构化输出 schema，避免把语音转写错误约束成 JSON 任务。
- 简历 PDF 优化已切换为 Gemini Native 原生直传：`POST /ai/resume/optimize/pdf` 不再在服务端先用 PDFBox 提取文本，而是把 PDF bytes 以 `contents[0].parts[0].text + inlineData(application/pdf, base64)` 一并发送给模型，并要求仅返回结构化 JSON；prompt 中会同时带入 `targetContext / jobDescription / resumeFileName`。
- 为降低新环境联调成本，简历链路在 `ai.gateway.mode=ROUTED` 且当前 OpenAI-compatible 配置表现为 Gemini / NewAPI Gemini 兼容时，会在系统启动时自动自举 `SYSTEM_RESUME_GEMINI_NATIVE` provider、`RESUME_OPTIMIZE_CORE` ACTIVE prompt template 与 `SYSTEM_RESUME_OPTIMIZE` route；因此 `/ai/resume` 与 `/ai/resume/review` 不再依赖先去后台手工配置 AI 网关。
- 简历自举现在不仅会创建 system route，还会在 system route 可用后自动停用历史遗留的 `RESUME_OPTIMIZE_MAIN / RESUME_DEFAULT` 路由，并将旧的 `resume_optimize_cn` 模板降为 `INACTIVE`，确保文本简历与 PDF 简历都会稳定落到 `SYSTEM_RESUME_OPTIMIZE -> SYSTEM_RESUME_GEMINI_NATIVE`。
- 简历专用自举 provider 会为 PDF 直传链路保留更高的最小超时基线：即使 `.env` 中默认 `AI_PROVIDER_TIMEOUT_MS` 仍为 15000ms，简历专用 provider 在启动自举时也会至少使用 60000ms，以避免 PDF 上传到 Gemini / NewAPI Gemini 时频繁触发 502 超时。
- 简历链路已补齐首条真正落地的异步任务闭环：`POST /ai/resume/tasks` 可提交文本或 PDF 简历分析任务，`GET /ai/tasks/{taskId}` 可查询排队 / 运行 / 成功 / 失败状态；worker 执行成功后仍会生成既有 `ai_call_logs` 历史记录，并在 `resultPayload.recordId` / `linkedRecordId` 中回传给前端。
- `V033 / V034` 已改为基于 `information_schema` 的兼容式 DDL，不再依赖 `ADD COLUMN IF NOT EXISTS` 这类在当前开发库 MySQL 上不稳定的语法；当前本地开发库已验证可顺利迁移至 `v034`。

## 3. 调用协议

### 3.1 内部调用入参（业务层 → AI 模块）
```java
AiRequest.builder()
    .traceId("trc_ai_001")
    .taskType(AiTaskType.RESUME)
    .userId(1001L)
    .userTier(UserTier.FREE)
    .payload(Map.of(
        "resumeText", "...",
        "targetRole", "Backend Engineer",
        "targetContext", "校招正式批",
        "jobDescription", "负责核心后端服务研发，要求熟悉 Java、MySQL、Redis 与性能优化。"
    ))
    .context(Map.of("inputMode", "text"))
    .build();
```

### 3.2 内部调用出参（AI 模块 → 业务层）
```java
AiResponse {
    boolean ok;
    String traceId;
    AiTaskType taskType;
    Object data;              // 业务结果
    AiMeta meta;              // 元信息
    ModerationResult moderation; // 审查结果（AI 输出）
}

AiMeta {
    String provider;
    String model;
    long latencyMs;
    int requestTokens;
    int responseTokens;
    int totalTokens;
    BigDecimal estimatedCost; // 预估费用（元）
    boolean fallbackUsed;
    String userTier;          // 调用时的用户等级快照
}

ModerationResult {
    String sourceType;        // AI_INPUT / AI_OUTPUT
    String riskLevel;         // LOW / MEDIUM / HIGH / CRITICAL
    String action;            // PASS / MASK / BLOCK / REVIEW
    String reasonCode;
}
```

## 4. 任务类型与路由

统一任务类型（枚举 SSOT 见 00_README）：
- `RESUME`
- `INTERVIEW_TEXT`
- `INTERVIEW_SUMMARY`
- `COMMUNITY_REPLY`
- `ICEBREAK`
- `STT`
- `TTS`

补充说明：
- `live` 测试模式当前不新增独立 `AiTaskType`；Java 侧占位创建继续记为 `INTERVIEW_TEXT`，正式复盘继续记为 `INTERVIEW_SUMMARY`，独立 Python bridge 自身不进入统一 quota 枚举。

路由流程：
1. 按 `taskType + userTier` 读取激活路由。
2. PREMIUM 用户优先使用高质量模型。
3. FREE 用户使用经济模型。
4. 发生可重试错误时切备用模型/供应商。
5. 全部失败返回受控错误。

### 4.1 AI 内容审查链路
1. 业务层发起 AI 请求前调用审查引擎（`sourceType=AI_INPUT`）。
2. 命中 `BLOCK` 时直接返回 `MOD-1001`，不触发模型调用。
3. AI 生成结果后再次调用审查引擎（`sourceType=AI_OUTPUT`）。
4. 命中 `MASK` 时返回脱敏文本，命中 `BLOCK` 时返回 `MOD-1002`。
5. 所有审查事件写入 `content_moderation_events`，并绑定 `traceId`。

## 5. 用户分级与配额

### 5.1 用户等级
- `FREE`：默认等级，使用经济模型，有每日免费额度，超出扣积分。
- `PREMIUM`：v1 由管理员手动分配，使用高质量模型，不限额度不扣积分。

### 5.2 配额检查流程
```
AI 请求到达
  │
  ├── PREMIUM 用户 → 跳过配额检查，直接调用
  │
  └── FREE 用户
        │
        ├── 每日免费次数未用完 → 免费调用（不扣积分）
        │
        └── 每日免费次数已用完
              │
              ├── 普通 AI 调用：积分余额 ≥ points_per_call → 按次扣积分 + 调用
              │     └── 积分扣减通过 points_ledger 追加记录
              │
              ├── 文本面试会话：创建前按固定会话包预扣（默认 1 次首问 + 3 次追问 + 1 次总结）
              │     └── 会话开始后默认不再对 reply/summary 逐次扣分，避免中途因积分不足无法完成总结
              │     └── Gemini Live 测试模式复用同一会话包；Java 侧只做占位创建与 transcript / summary 正式留痕
              │
              └── 积分不足 → 返回 AI-2201 "额度不足，完成任务赚取积分"
```

### 5.3 配额配置表（`ai_quota_policies`）
| 字段 | 类型 | 说明 |
|------|------|------|
| `tier` | VARCHAR(20) | `FREE` / `PREMIUM` |
| `task_type` | VARCHAR(30) | 任务类型 |
| `daily_free_limit` | INT | 每日免费次数 |
| `points_per_call` | INT | 超出免费后每次消耗积分 |
| `daily_max_limit` | INT | 每日总上限（含积分调用） |
| `model_preference` | VARCHAR(50) | 该等级优先使用的模型 |
| `max_input_tokens` | INT | 单次最大输入 token |

### 5.4 建议默认配额（种子数据）
| 等级 | 任务类型 | 每日免费 | 积分消耗 | 每日总上限 |
|------|---------|---------|---------|-----------|
| FREE | RESUME | 3 | 10 | 20 |
| FREE | INTERVIEW_TEXT | 5 | 5 | 30 |
| FREE | COMMUNITY_REPLY | 5 | 2 | 20 |
| FREE | ICEBREAK | 3 | 3 | 15 |
| FREE | STT | 2 | 8 | 10 |
| FREE | TTS | 2 | 5 | 10 |
| PREMIUM | * | 不限 | 0 | 200 |

## 6. 成本追踪

### 6.1 成本数据来源
- AI 供应商返回的 `usage.prompt_tokens` 和 `usage.completion_tokens`。
- 模型单价配置在 `ai_model_routes` 表中。

### 6.2 成本计算公式
```
estimated_cost = (request_tokens / 1000 × cost_per_1k_input)
               + (response_tokens / 1000 × cost_per_1k_output)
```

### 6.3 成本记录
每次 AI 调用在 `ai_call_logs` 中记录：
- `request_tokens`、`response_tokens`、`total_tokens`
- `estimated_cost`（预估费用，元）
- `user_tier`（调用时的用户等级快照）

### 6.4 管理后台成本看板与日志诊断
Admin 后台提供基于 `ai_call_logs` 的聚合查询与诊断能力：
- 今日/本周/本月总调用次数与预估成本
- 按模型/供应商/任务类型/用户等级分布
- Top N 用户调用量
- 按 `taskType/provider/status/userId` 条件过滤 AI 调用日志
- 按 `taskType + sceneCode + modelPreference` 预览当前路由命中结果
- 支持 `GET /admin/ai/cost-dashboard/export?period=today|week|month` 导出 CSV，按 `OVERVIEW / MODEL / PROVIDER / TASK_TYPE / USER_TIER / TOP_USER` 分段输出，便于答辩演示和离线归档

## 7. 提供商配置模型
必须配置：
- `providerKey`
- `baseUrl`
- `apiKeyRef`
- `enabled`
- `priorityNo`
- `timeoutMs`
- `rateLimitQps`

模型路由字段：
- `taskType`
- `providerKey`
- `modelName`
- `isFallback`
- `enabled`
- `cost_per_1k_input`（新增）
- `cost_per_1k_output`（新增）

## 8. Prompt 模板管理
当前实现采用“后台版本管理 + route 绑定模板名 + 变量渲染 + 运行时取 ACTIVE 版本”的轻量方案。

规则：
- 模板以 `(taskType, templateName, versionNo)` 管理版本。
- 模板状态分为 `DRAFT`、`ACTIVE`、`INACTIVE`。
- 同一 `(taskType, templateName)` 同时仅允许一个 `ACTIVE` 版本。
- route 通过 `ai_model_routes.prompt_template_name` 可选绑定模板名，而不是直接绑定模板主键。
- 当 route 已绑定模板名时，运行时必须读取同 `taskType + templateName` 下的 `ACTIVE` 模板；若不存在 `ACTIVE` 版本，则直接返回 `AI-2003`，不再静默回退到 route 自身的 `systemPrompt`。
- 当 route 未绑定模板时，继续使用 route 自身的 `systemPrompt`，以兼容既有配置。
- 模板正文支持 `{{variableName}}` 占位符；`variablesJson` 可承载变量声明、说明、默认值、样例值等元数据，并参与渲染。
- 运行时渲染采用严格模式：若模板中出现的占位变量在当前请求上下文中缺失，则立即返回 `AI-2003`，避免未渲染模板直接发送给模型。
- 后台支持 `POST /admin/ai/prompt-templates/render-preview` 进行渲染预览，输入模板正文、变量声明与示例变量 JSON 后，可返回渲染结果、占位变量列表、缺失变量列表和最终解析变量。
- 后台支持 `POST /admin/ai/prompt-templates/{templateId}/publish` 显式发布某个版本；发布后该模板族的其他 `ACTIVE` 版本会自动降为 `INACTIVE`。
- 后台支持 `POST /admin/ai/prompt-templates/{templateId}/rollback` 回滚到指定历史版本；目标版本会重新激活，保证 route 绑定模板名后的运行时语义与后台看到的 `ACTIVE` 版本一致。
- 路由列表与 `/admin/ai/routes/resolve-preview` 会返回 `promptTemplateName` 与当前命中的 `promptTemplateVersionNo`，方便联调定位。
- 当前简历优化额外有一层系统自举兜底：当运行环境启用 `ROUTED` 模式且 provider 配置满足 Gemini / NewAPI Gemini 兼容条件时，后端会在启动阶段确保 `RESUME_OPTIMIZE_CORE` 与 `SYSTEM_RESUME_OPTIMIZE` 可用，并主动停用本地历史遗留的 resume demo 路由 / 模板，避免新环境或旧开发库同时存在多套 resume 配置而导致文本链路继续误命中旧 route。

当前边界：
- 当前预览与运行时渲染均只支持单模板正文插值，不支持多模板拼装、条件分支或模板函数。
- 当前未实现更重的模板审批/审核流；后续可在现有发布/回滚基础上继续扩展。

## 9. 超时、重试、熔断

### 9.1 技术选型
- HTTP 客户端：Spring WebClient（非阻塞）
- 熔断框架：Resilience4j
- 线程模型：Java 21 虚拟线程 + 独立 AI 线程池

### 9.2 默认策略
- 文本任务超时 15s。
- STT/TTS 超时 20s。
- 瞬时错误默认重试 1 次；管理员可通过 provider 的 `maxRetries` 调整。
- 当前不单独做 provider 探活，统一通过重试日志辅助判断链路稳定性。

可重试错误：429、5xx、网络超时。
不可重试：参数错误、鉴权错误、模板缺失。

### 9.3 熔断策略
- 窗口大小：60s。
- 失败率阈值：50%（窗口内请求超过 5 次且失败率超过 50%）。
- 熔断后冷却时间：30s。
- 冷却后半开探测：放行 1 次请求，成功则关闭熔断，失败则重新开启。

### 9.4 线程池隔离
```yaml
ai:
  thread-pool:
    core-size: 10
    max-size: 20
    queue-capacity: 50
    thread-name-prefix: "ai-"
```
AI 任务使用独立线程池，不影响其他业务接口。

## 10. 降级策略
- STT 失败：返回 `AI-2102` 并建议回退文本模式。
- TTS 失败：返回 `AI-2103` 与 `fallbackMode=TEXT`，前端继续保留文本追问展示，不阻断面试链路。
- 所有文本模型失败：返回可解释降级消息。
- 模板缺失：返回 `AI-2003`。
- 配额不足：返回 `AI-2201`。
- 输入审查阻断：返回 `MOD-1001`。
- 输出审查阻断：返回 `MOD-1002`。
- 审查引擎异常：按开关降级为记录告警并透传结果（仅用于应急回退）。

## 11. 异步任务队列
当前异步任务底座已从“概念规划”进入“数据库队列 + worker 落地”阶段，首个正式接入业务为简历优化。

当前实现：
- 使用数据库表 `ai_async_task_jobs` 存储任务主记录，`ai_async_task_events` 存储状态事件。
- `AiAsyncTaskService` 负责提交任务、认领任务、推进状态和写事件。
- `AiAsyncTaskWorker` 通过 `@Scheduled` 轮询认领任务，并按 `AiAsyncTaskProcessor` 分发到具体业务处理器。
- 当前已落地 `ResumeOptimizeAsyncTaskProcessor`：支持文本简历与 PDF 简历两种离线任务。
- 异步任务执行成功后仍复用既有业务链路写入 `ai_call_logs`，因此结果页、历史详情与 PDF 导出接口不需要另起一套存储。
- PostgreSQL 基线已通过 [`V044__harden_ai_async_task_jsonb.sql`](apps/server-java/src/main/resources/db/migration-pg/V044__harden_ai_async_task_jsonb.sql) 将任务/事件快照字段从 `TEXT` 收口到 `JSONB`，H2 基线则继续用 `JSON` 保持契约接近。
- 当前已新增 [`AiAsyncTaskMaintenanceJob.java`](apps/server-java/src/main/java/com/bishe/server/ai/gateway/task/AiAsyncTaskMaintenanceJob.java)，默认每小时按 30 天保留期清理 `SUCCEEDED / FAILED / CANCELLED` 终态任务及其事件；对应服务实现由 [`AiAsyncTaskService.java`](apps/server-java/src/main/java/com/bishe/server/ai/gateway/task/AiAsyncTaskService.java) 的 `purgeTerminalTasks(...)` 承接。

规划补充：
- 学生画像后续将优先复用同一套异步任务底座，而不是再引入新的任务框架。
- 画像相关任务建议拆分为：
  - `PORTRAIT_REFRESH`
    - 负责画像真相层重算：标签、证据、signalLevel、freshnessLevel
    - 原则上不依赖外部 LLM
  - `PORTRAIT_SUMMARY`
    - 负责画像表达层结果：headline、summary、nextActions
    - 默认先走模板化总结，后续可选低频 LLM 润色
- 当前不对外暴露画像任务提交 API，优先由资料更新、技能进度、社区互动、AI 简历 / 面试结果落库等内域事件触发。

当前真实状态补充：
- 画像相关 `sceneCode=STUDENT_PORTRAIT_SUMMARY` 与 AI gateway 路由 / 模板已存在，用于低频表达层润色。
- 但画像异步任务本身尚未接入 `AiAsyncTaskWorker`；当前 worker 仍只正式处理简历优化任务。

当前简历异步接口契约：
- `POST /api/v1/ai/resume/tasks`
  - `application/json`：文本简历分析
  - `multipart/form-data`：PDF 简历分析
- `GET /api/v1/ai/tasks/{taskId}`：查询任务详情与最终结果

任务状态：
- `PENDING`
- `RUNNING`
- `RETRY_WAIT`
- `SUCCEEDED`
- `FAILED`
- `CANCELLED`

当前设计取舍：
- 任务提交阶段会先做 AI 输入审查，但配额扣减与真正模型调用仍在 worker 执行阶段完成，因此若排队期间用户配额状态变化，任务可能在执行时失败。
- 当前 PDF 异步任务为了保证“单机最小闭环”，会把上传文件内容以 base64 形式保存在任务输入快照里；后续若接对象存储或统一文件中转服务，再迁移为更轻量的文件引用方案。
- 当前对前端只暴露轮询接口，还没有接入 SSE 任务事件流、WebSocket 或统一通知中心推送。
- 当前 route / prompt / provider 快照已在提交时落库用于审计，但 worker 执行阶段仍默认复用当下业务服务链路，而不是完全从快照重放 provider 调用。
- 异步任务快照字段当前已不再继续以 `TEXT` 过渡；后续若新增同类 payload 字段，PostgreSQL 默认应直接使用 `JSONB`，避免再次偏离全局建模约定。
- 画像异步任务若接入本底座，`input_snapshot_json` 应尽量保留结构化信号摘要或内容 hash，避免直接持久化完整简历、完整面试逐字稿和敏感联系方式。

## 12. 日志与监控
`ai_call_logs` 必须记录：
- `traceId`、`taskType`、`provider`、`model`
- `latencyMs`、token 计数（request/response/total）
- `estimated_cost`
- `user_tier`
- `status`、`errorCode`

`content_moderation_events` 必须记录：
- `traceId`、`sourceType`、`targetId`
- `riskLevel`、`action`、`reasonCode`
- `operatorUserId`（系统自动或管理员）

核心指标：
- 成功率
- p95 延迟
- 回退使用率
- 各供应商错误率
- 日/周/月成本

运行时日志开关：
- `debugModeEnabled`：控制路由分发、命中 provider/route/model、成功结果等诊断日志。
- `aiRequestLogEnabled`：控制请求开始、重试日志，以及错误场景下请求体/响应体摘要日志。
- 两个开关均可通过后台 `/admin/ai/runtime-settings` 动态调整；数据库未配置时回退到 `.env` 默认值。

## 13. 后台配置项
管理员必须可：
1. 管理供应商配置。
2. 管理任务路由（含分级模型设置）。
3. 预览任务在当前路由表下的实际命中 provider / route / model。
4. 管理模板版本。
5. 管理配额策略。
6. 查询调用日志与成本看板。
7. 切换 AI 相关开关。
8. 切换 AI 输入/输出审查开关并设置自动隐藏阈值。

## 14. 安全规则
- AI 供应商密钥通过环境变量或加密配置管理，不落日志明文。
- 限制输入长度与上传大小（按 `max_input_tokens` 配置）。
- AI 调用日志中用户输入做脱敏处理（v1 可选）。
- 审查命中原因仅返回 `reasonCode`，不返回具体敏感词原文。

## 15. 决策摘要
- AI 模块集成在 Java 后端内部，不独立部署服务。
- 任务路由与模板版本可在线配置，无需重启。
- 回退能力优先于"单模型最优效果"。
- 成本追踪和用户分级配额为 v1 内置能力。
- 积分消耗通过 `points_ledger` 追加，与成长体系共用账本。
- AI 输入/输出审查为默认开启，异常时允许按开关回退。

## 16. 验收标准（DoD）
1. 所有 AI 任务类型可通过模块调用并返回结果。
2. 路由切换不需要重启后端。
3. 日志、token、成本可查询。
4. 每类任务至少验证 1 个失败回退用例。
5. FREE 用户配额限制生效（超出每日免费后扣积分）。
6. 积分不足时返回友好提示。
7. AI 输入/输出审查链路可用并可写入审查事件日志。
