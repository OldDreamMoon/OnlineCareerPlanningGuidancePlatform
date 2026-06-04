# AI 赋能大学生职业规划平台

本项目是一个面向大学生职业规划与就业准备场景的全栈 Web 平台，覆盖学生、导师、企业和管理员四类角色。系统围绕学生画像、技能成长、AI 简历优化、模拟面试、导师咨询、企业悬赏任务、社区互动与后台治理等业务链路，形成从个人能力诊断到职业实践反馈的闭环。

仓库主体包括 Web 前端、Java 后端、实时面试桥接服务、数据库迁移、Docker 部署模板、运行脚本和规格文档。

## 功能概览

- 学生端：注册登录、资料中心、成长工作台、技能星图、AI 简历优化、AI 文本/语音/Live 测试模式面试、AI 复盘中心、导师广场、咨询订单、企业任务大厅、社区与通知中心。
- 导师端：导师资料维护、服务套餐、排期管理、订单工作台、咨询履约、财务与提现记录。
- 企业端：企业资料与认证、任务发布、任务中心、投稿审核、录用与任务运营。
- 管理端：用户与认证审核、导师运营、企业任务运营、内容治理、通知运营、订单对账、技能运营、运行配置和 AI 网关管理。
- AI 能力：支持 mock、OpenAI-compatible 和 Gemini native provider，通过后台 AI 网关维护 provider、模型、路由和提示词模板。
- 通知与治理：站内通知、WebSocket 实时回流、通知派发任务、内容举报、敏感词策略、审计日志和运行配置降级。

## 技术栈

- 前端：React 18、TypeScript、Vite、React Router、Ant Design、Framer Motion、Tailwind CSS。
- 后端：Java 21、Spring Boot 3、Spring Security、Spring Data JPA、Flyway、SpringDoc。
- 数据与基础设施：PostgreSQL、Redis、MinIO、Docker Compose、Caddy。
- AI Live 桥接：Python 3.12、FastAPI/WebSocket、Gemini Live API。
- 构建与部署：npm、Maven、Docker、GHCR、GitHub Actions 部署模板。

## 目录结构

```text
.
├── apps/
│   ├── web/                    # React 前端，统一承载四类角色页面
│   ├── server-java/            # Spring Boot 后端模块化单体
│   └── interview-live-python/  # AI Live 面试测试桥接服务
├── infra/
│   ├── docker/                 # 本地与生产 Docker Compose 模板
│   └── caddy/                  # 生产入口反向代理配置
├── scripts/
│   ├── dev/                    # 本地环境检查、服务启动、烟测脚本
│   ├── db/                     # 数据库 seed、迁移和备份辅助脚本
│   └── deploy/                 # 生产服务器部署辅助脚本
├── docs/
│   └── spec/                   # 需求、接口、数据库、UI 与部署规格文档
├── tests/                      # 端到端与集成测试入口
├── .github/workflows/          # GitHub Actions 部署模板
├── .env.example                # 本地开发环境变量示例
├── mise.toml                   # 推荐运行时版本声明
└── Makefile                    # 常用命令入口
```

## 本地运行

建议在 Linux / WSL2 环境中运行，并安装 Docker、mise、Java 21、Node.js、Maven 和 Python 3.12。仓库根目录的 `mise.toml` 声明了推荐运行时版本。

1. 准备环境变量：

```bash
cp .env.example .env
bash scripts/dev/01_prepare_env_files.sh
```

2. 启动 PostgreSQL、Redis、MinIO 等基础服务：

```bash
bash scripts/dev/04_start_services.sh
```

3. 启动后端和前端：

```bash
bash scripts/dev/05_start_apps_local.sh
```

4. 执行轻量烟测：

```bash
bash scripts/dev/07_smoke_check.sh
```

5. 停止服务：

```bash
bash scripts/dev/06_stop_apps_local.sh
bash scripts/dev/08_stop_services.sh
```

也可以分别启动前后端：

```bash
cd apps/server-java
mise exec -- mvn -Dmaven.repo.local=$HOME/.cache/dev/maven/repository -DskipTests spring-boot:run
```

```bash
cd apps/web
npm_config_cache=$HOME/.cache/dev/npm mise exec -- npm run dev
```

前端开发服务器默认通过 Vite 代理访问后端 `/api/v1`；Live 面试测试桥默认使用 `127.0.0.1:8765`，仅在调试 `/ai/interview/live/**` 时需要单独启动 `apps/interview-live-python`。

## 配置说明

- `.env.example` 提供本地开发所需的环境变量结构；运行前可复制为 `.env` 并按实际环境填写数据库、JWT、AI provider、邮件、验证码和支付相关配置。
- `infra/docker/.env.prod.example` 提供生产部署的环境变量结构；服务器侧应根据实际域名、数据库、对象存储、AI provider、邮件、验证码和支付配置进行填写。
- 默认数据库基线为 PostgreSQL；历史 MySQL 相关脚本只作为旧数据迁移或备份参考，不是当前默认运行链路。
- AI 网关推荐使用 `AI_GATEWAY_MODE=ROUTED`，在管理员后台维护 provider、模型路由和提示词模板；`AI_PROVIDER_*` 环境变量主要用于迁移兼容。
- 支付模块默认可使用 `PAYMENT_MODE=MOCK` 进行本地联调；接入支付宝沙箱时需要补齐沙箱应用配置和密钥。

## 数据库

后端使用 Flyway 管理数据库结构：

- PostgreSQL migration：`apps/server-java/src/main/resources/db/migration-pg`
- H2 测试基线：`apps/server-java/src/test/resources/schema.sql`
- 演示数据与数据库辅助脚本：`scripts/db`

本地首次启动后端时会自动执行 PostgreSQL migration。若需要导入演示数据，请先确认当前数据库状态并备份，再按 `scripts/db` 下对应脚本说明执行。

## 部署

仓库保留 Docker Compose 与 GitHub Actions 部署模板：

- 基础设施：`infra/docker/docker-compose.infra.prod.yml`
- 应用服务：`infra/docker/docker-compose.app.prod.yml`
- 反向代理：`infra/caddy/Caddyfile`
- 服务器初始化：`scripts/deploy/bootstrap_infra_prod.sh`
- 应用部署：`scripts/deploy/deploy_app_prod.sh`
- GitHub Actions 模板：`.github/workflows/deploy-prod.yml`

GitHub Actions 可按仓库设置启用。启用前需要在仓库 Secrets 和服务器 `.env.prod` 中配置 GHCR、SSH、域名、TLS 邮箱、数据库、JWT、AI provider、邮件、验证码和支付等生产参数。

## 文档入口

- [需求与架构规格](docs/spec/01_PRD.md)
- [API 规格](docs/spec/04_API_SPEC.md)
- [数据库结构说明](docs/spec/05_DB_SCHEMA.md)
- [部署规格](docs/spec/09_DEPLOYMENT_RUNBOOK.md)
- [测试计划](docs/spec/08_TEST_PLAN.md)
