# Java Backend Workspace

目标：实现核心业务后端（模块化单体）。

当前技术栈：Java 21 + Spring Boot 3 + Spring Security + Spring Data JPA + Flyway + Redis + SpringDoc。

## 当前状态
- 业务主线已覆盖：鉴权、画像/社区、成长中心、AI 简历优化、AI 文本/语音/Live 测试模式面试、导师咨询/支付、企业悬赏、通知中心、后台治理。
- AI 网关当前位于 `apps/server-java` 内部模块，不存在独立 `ai-gateway` 服务。
- AI 网关已支持三种运行模式：`MOCK`、`OPENAI_COMPATIBLE`（迁移兼容）、`ROUTED`（推荐）。
- 默认运行与部署基线已切到 PostgreSQL；历史 MySQL 仅保留为旧 checkpoint / dump / 回滚参考。

## AI 网关重构说明
### 1. 推荐模式
- 推荐设置：`AI_GATEWAY_MODE=ROUTED`
- 配置入口：管理员登录后进入前端 `/admin/ai/gateway`
- 配置内容：
  - provider：`providerType`、`baseUrl`、`apiKey`、`timeoutMs`、`maxRetries`
  - route：`taskType`、`sceneCode`、`modelName`、`systemPrompt`、`extraConfigJson`

### 2. 存储与脱敏
- provider 与 route 配置已落库到：
  - `ai_provider_configs`
  - `ai_model_routes`
- API Key 不再要求长期以业务环境变量形式维护；后台创建/更新时会加密存库。
- 列表接口与管理页只返回脱敏后的 `apiKeyMasked`，不会回显明文。
- 加密根密钥优先读取 `AI_GATEWAY_CONFIG_SECRET`，为空时回退到 `auth.jwt.secret` 派生。

### 3. 多 provider 路由
- 已支持 provider 类型：
  - `OPENAI_COMPATIBLE`
  - `GEMINI_NATIVE`
- 已支持路由任务类型：
  - `RESUME`
  - `INTERVIEW_TEXT`
  - `INTERVIEW_SUMMARY`
  - `STT`
  - `TTS`
- 路由规则：优先按 `taskType + sceneCode` 精确命中，未命中时回退到该 `taskType` 的默认路由。

### 4. 语音转写建议
- 浏览器端推荐继续使用 `MediaRecorder` 录制 `audio/webm`（`opus`）。
- 后端 Gemini native 适配会原样透传上传文件的 MIME Type 与音频字节，不把 `audio/webm` 写死在业务逻辑中。
- 推荐实践：
  - `INTERVIEW_TEXT` / `INTERVIEW_SUMMARY` -> OpenAI-compatible
  - `STT` -> Gemini native

### 5. 迁移兼容
- 若历史环境仍依赖 `AI_PROVIDER_*` 环境变量，可先开启：`AI_GATEWAY_ALLOW_LEGACY_FALLBACK=true`
- 该兼容仅用于迁移过渡；正式演示/答辩建议改为后台维护 provider 与路由。

## 本地运行
- 推荐先准备根 `.env` 并启动基础服务：
  - `bash scripts/dev/01_prepare_env_files.sh`
  - `bash scripts/dev/04_start_services.sh`
- 后端启动：
  - `cd apps/server-java && mise exec -- mvn -Dmaven.repo.local=/home/olddream/.cache/dev/maven/repository -DskipTests spring-boot:run`
- 当前推荐的一键本地运行入口已经默认切到 PostgreSQL：
  - `bash scripts/dev/05_start_apps_local.sh`
- 若需直接按 PostgreSQL 运行时基线启动：
  - `cd apps/server-java && SPRING_PROFILES_ACTIVE=postgres DB_URL=jdbc:postgresql://127.0.0.1:5432/bishe DB_USERNAME=bishe DB_PASSWORD=bishe mise exec -- mvn -Dmaven.repo.local=/home/olddream/.cache/dev/maven/repository -DskipTests spring-boot:run`
- 若需显式声明 PostgreSQL 目标：
  - `bash scripts/dev/05_start_apps_local.sh --postgres`
- 历史 MySQL 运行链已退出支持范围；如需回看旧基线，请使用历史 Git checkpoint 或数据库备份。
- 停止命令：
  - `bash scripts/dev/06_stop_apps_local.sh`
  - `bash scripts/dev/08_stop_services.sh`
- Agent 在工具环境中自测时，不使用 `scripts/dev/05_start_apps_local.sh` 拉起常驻应用。
- 如需同时联调前端，请以持久会话分别直启 Web/Java。

## 关键公开入口
- 健康检查：`GET /api/v1/health`
- Swagger/OpenAPI：`/swagger-ui/index.html`、`/v3/api-docs`
- 鉴权：`/api/v1/auth/**`
- AI 模块：`/api/v1/ai/**`
- 社区：`/api/v1/community/**`
- 导师咨询与支付：`/api/v1/consult/**`、`/api/v1/pay/**`
- 企业悬赏：`/api/v1/bounty/**`
- 管理后台：`/api/v1/admin/**`
