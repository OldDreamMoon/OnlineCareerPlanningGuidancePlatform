# 部署运行手册

> **文档状态**：`evolving` · 最后审核：2026-04-18 · 已按当前生产部署结构与当前服务器落地结果同步：`Caddy + 双层 Compose + GHCR 镜像发布 + 单一 prod env`
>

## 1. 部署目标
- 环境：Linux 公网服务器
- 编排：Docker Compose
- 公网入口：Caddy
- HTTPS：由 Caddy 自动申请和续期
- 发布方式：前后端镜像通过 GHCR 发布，基础设施与应用层分离部署
- 当前 GitHub Actions 默认负责构建并推送镜像，并通过 SSH 在服务器上启动基础设施层并自动部署应用层；数据库/演示数据导入仅在首次建站或需要重建数据时人工执行

当前事实：
- 项目当前已按上述方案成功部署在公网服务器上。
- 因此本文后续内容不再以“候选部署方案”口吻描述，而是作为当前真实运行路径说明。

## 2. 前置条件
- 已安装 Docker / Docker Compose
- 域名已解析到服务器
- 已开放 `80/443`
- 已准备 GHCR 拉取权限、数据库密码、JWT 密钥、AI / 支付 / 认证所需环境变量

## 3. 目录约定
```text
/project
  /apps/web
  /apps/server-java
  /infra
    /caddy/Caddyfile
    /docker/docker-compose.infra.prod.yml
    /docker/docker-compose.app.prod.yml
    /docker/.env.prod
```

说明：
- `infra/docker/docker-compose.infra.prod.yml` 负责基础设施层：PostgreSQL / Redis / MinIO。
- `infra/docker/docker-compose.app.prod.yml` 负责应用层：Caddy / Web / Server。
- 两层通过共享外部网络 `bishe_prod` 通信。
- 当前默认运行链与生产部署模板均已切到 PostgreSQL；如线上仍存在旧 MySQL 环境，需先执行数据迁移与切换演练，再按本文档发版。
- 生产环境统一只维护一份 `infra/docker/.env.prod`；但为避免 `--remove-orphans` 误删基础设施容器，基础设施层与应用层仍使用不同的 Compose project name。

## 4. 环境变量模板
### 4.1 统一入口 `infra/docker/.env.prod`
```env
INFRA_COMPOSE_PROJECT_NAME=bishe_prod_infra
APP_COMPOSE_PROJECT_NAME=bishe_prod_app
NETWORK_NAME=bishe_prod

POSTGRES_DB=bishe
POSTGRES_USER=bishe
POSTGRES_PASSWORD=replace_with_strong_postgres_password
POSTGRES_PORT_BIND=127.0.0.1:5432

REDIS_PORT_BIND=127.0.0.1:6379

MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=replace_with_strong_minio_password
MINIO_PORT_BIND=127.0.0.1:9000
MINIO_CONSOLE_PORT_BIND=127.0.0.1:9001

GHCR_NAMESPACE=your-ghcr-namespace
APP_IMAGE_TAG=latest
APP_DOMAIN=example.com
APP_TLS_EMAIL=admin@example.com

SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://postgres:5432/bishe
DB_USERNAME=bishe
DB_PASSWORD=replace_with_postgres_app_password
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=

JWT_SECRET=replace_with_at_least_32_chars_secret
JWT_ACCESS_EXPIRE_MINUTES=120
JWT_REFRESH_EXPIRE_DAYS=7
ADMIN_INIT_EMAIL=admin@example.com
ADMIN_INIT_PASSWORD=ReplaceWithStrongAdminPassword123!

NOTIFICATION_APP_BASE_URL=https://example.com

AUTH_CAPTCHA_GEETEST_ENABLED=true
AUTH_CAPTCHA_GEETEST_CAPTCHA_ID=your_geetest_captcha_id
AUTH_CAPTCHA_GEETEST_CAPTCHA_KEY=your_geetest_captcha_key
AUTH_CAPTCHA_GEETEST_VERIFY_URL=https://gcaptcha4.geetest.com/validate
AUTH_CAPTCHA_GEETEST_TIMEOUT_MS=5000
AUTH_CAPTCHA_GEETEST_PROOF_TTL_SECONDS=600
AUTH_EMAIL_RESEND_API_KEY=your_resend_api_key
AUTH_EMAIL_RESEND_FROM_EMAIL=noreply@example.com
AUTH_EMAIL_RESEND_API_URL=https://api.resend.com/emails
AUTH_EMAIL_CODE_TTL_SECONDS=600
AUTH_EMAIL_RESEND_COOLDOWN_SECONDS=60
AUTH_EMAIL_PROOF_TTL_SECONDS=1800
```

说明：
- 正式启用注册验证码链路时，需要同时配置 Geetest 与 Resend 相关环境变量。
- `infra/docker/.env.prod` 是生产默认配置入口，但 `docker compose --env-file` 只负责变量替换；后端容器内可见变量仍以 `infra/docker/docker-compose.app.prod.yml` 的 `server.environment` 白名单为准。
- 根目录 `.env` 与子应用 `.env` 只用于本地开发，不参与服务器 Docker 运行。
- Web 生产容器内部使用 Nginx 托管静态文件，但公网统一只暴露 Caddy。
- 当前 `prod` profile 直接沿用 PostgreSQL 默认运行链；若线上仍保留 MySQL 历史库，请将其视为归档/回滚参考，而不是继续让应用直接连 MySQL 运行。

## 5. 服务清单与端口映射
| 层级 | 服务 | 容器 | 端口 | 暴露策略 | 备注 |
|------|------|------|------|----------|------|
| 基础设施 | PostgreSQL | `postgres` | `5432` | 仅本机/内网 | 事务数据 |
| 基础设施 | Redis | `redis` | `6379` | 仅本机/内网 | 缓存、限流、配额 |
| 基础设施 | MinIO | `minio` | `9000/9001` | 仅本机/内网 | 对象存储与控制台 |
| 应用 | Server | `server` | `8080` | 不直连公网 | Spring Boot 后端 |
| 应用 | Web | `web` | `80` | 不直连公网 | 静态站点容器 |
| 应用 | Caddy | `caddy` | `80/443` | 唯一公网入口 | TLS、反向代理 |

## 6. 部署步骤
1. 准备代码与统一环境变量文件：`infra/docker/.env.prod`。
2. 首次部署前创建共享网络：
```bash
docker network create bishe_prod
```
3. 启动基础设施层：
```bash
docker compose \
  -p bishe_prod_infra \
  --env-file infra/docker/.env.prod \
  -f infra/docker/docker-compose.infra.prod.yml \
  up -d
```
4. 如需导入业务或演示数据，先手工恢复 PostgreSQL dump：
```bash
bash scripts/db/restore_prod_db_from_dump.sh --dump-file /path/to/your_dump.sql.gz --recreate-db --yes
```
说明：
- 当前没有“由 GitHub Actions 自动导入的 PostgreSQL 模拟数据”。
- 历史 MySQL 演示数据脚本已归档，不属于当前 PostgreSQL 部署基线。
5. 启动或更新应用层：
```bash
docker compose \
  -p bishe_prod_app \
  --env-file infra/docker/.env.prod \
  -f infra/docker/docker-compose.app.prod.yml \
  pull

docker compose \
  -p bishe_prod_app \
  --env-file infra/docker/.env.prod \
  -f infra/docker/docker-compose.app.prod.yml \
  up -d
```
6. 检查健康状态、数据库迁移与关键冒烟接口。

补充：
- 若使用当前 GitHub Actions 发布链，第 3 步基础设施启动与第 5 步应用层部署可以由 workflow 通过 SSH 自动完成。
- workflow 当前不会自动执行第 4 步的数据导入；若是首次建站或需要重建演示数据，仍需人工恢复 PostgreSQL dump。

## 7. 路由与公网入口基线
- Caddy 统一监听 `80/443`。
- `/api/*` 转发到 Spring Boot 容器。
- 其余请求转发到前端静态站点容器。
- 浏览器侧推荐统一通过同域访问：
  - 前端：`https://your-domain/`
  - API：`https://your-domain/api/v1/...`

## 8. 数据库迁移与冒烟测试
- 当前数据库迁移由应用启动时的 Flyway 自动执行。
- 每次发版前仍建议先备份数据库，再执行应用层更新。

最小冒烟：
1. `GET /api/v1/health`
2. 登录接口
3. 关键 AI 接口或后台配置接口
4. 支付模式查询 / 下单接口
5. 若启用正式注册校验，额外检查 Geetest 配置读取与邮箱验证码发信链路

## 9. 日志与故障排查
应用层：
```bash
docker compose -p bishe_prod_app --env-file infra/docker/.env.prod -f infra/docker/docker-compose.app.prod.yml ps
docker compose -p bishe_prod_app --env-file infra/docker/.env.prod -f infra/docker/docker-compose.app.prod.yml logs -f caddy
docker compose -p bishe_prod_app --env-file infra/docker/.env.prod -f infra/docker/docker-compose.app.prod.yml logs -f web
docker compose -p bishe_prod_app --env-file infra/docker/.env.prod -f infra/docker/docker-compose.app.prod.yml logs -f server
```

基础设施层：
```bash
docker compose -p bishe_prod_infra --env-file infra/docker/.env.prod -f infra/docker/docker-compose.infra.prod.yml ps
docker compose -p bishe_prod_infra --env-file infra/docker/.env.prod -f infra/docker/docker-compose.infra.prod.yml logs -f postgres
```

常见问题：
- `AUTH` 错误：检查 JWT、管理员初始账号与 Geetest / Resend 配置。
- AI 超时：检查模型路由、外网出口与供应商配额。
- 支付回调不到达：检查 `notify_url`、证书与公网端口。
- HTTPS 签发失败：优先检查域名解析与 `80/443` 是否被其他进程占用。

## 10. 回滚步骤
1. 保持基础设施层运行，优先只回滚应用层镜像标签。
2. 修改 `infra/docker/.env.prod` 中的 `APP_IMAGE_TAG` 为上一稳定版本。
3. 重新执行应用层 `docker compose up -d`。
4. 若本轮迁移引入不兼容变更，再恢复 PostgreSQL 快照并重跑冒烟测试；不要再把“直接切回 MySQL runtime”当成默认回滚路径。

## 11. 安全基线
- 仅 Caddy 对公网开放。
- PostgreSQL / Redis / MinIO 默认只绑定本机或内网。
- 密钥、支付私钥、AI API Key、Resend API Key 不入 Git。
- 支付回调验签保持开启。
- 默认管理员密码在首次部署后立即更改。

## 12. 发布完成标准
- 基础设施层与应用层容器均健康
- Flyway 迁移成功
- 冒烟测试通过
- `caddy/web/server` 启动日志无关键错误
- 关键答辩脚本可运行

## 13. 本手册范围
覆盖 v1 当前实际生产部署路径：`单机 Linux + Docker Compose + Caddy + GHCR 镜像`，不扩展到复杂集群编排。

## 14. 决策摘要
- 基础设施层与应用层拆分，减少发版时对数据库和对象存储的扰动。
- 公网入口统一由 Caddy 承担，前后端通过同域反代访问。
- 生产部署统一收敛到 `infra/docker/.env.prod`，但继续保留两套 Compose project name 隔离基础设施与应用层。

## 15. 验收标准
1. 全新服务器可按本文档完成首发部署。
2. 应用层可独立升级和回滚，不破坏基础设施层。
