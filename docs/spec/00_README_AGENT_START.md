# Agent 启动文档

> **文档状态**：`frozen` · 最后审核：2026-03-05 · 本文件是全局基线与枚举 SSOT，变更需经用户审批。
>

## 项目一句话定义
一个面向大学生的 AI 职业成长与导师连接平台，将 AI 工具、游戏化学习、真人导师咨询和企业悬赏任务整合为一个可演示的闭环系统。

## 主要目标
交付可用于毕业设计答辩的原型系统，并形成一套“任何 Agent 可直接执行”的工程文档集。

## 固定技术栈
- 前端：React 18、TypeScript、Vite
  - C 端样式（学生/导师/企业/公开页）：Tailwind CSS + shadcn/ui + Framer Motion
  - B 端样式（Admin 后台）：Ant Design 5 + Ant Design ProComponents
- 后端：Java 21、Spring Boot 3、Spring Security、MyBatis-Plus
- AI 模块：集成在 Java 后端内部（兼容 OpenAI 协议，多模型路由、成本控制、用户分级配额）
- 数据与中间件：MySQL 8、Redis、MinIO
- 基础设施：Nginx、Docker Compose、Linux
- 支付：支付宝沙箱（主）+ 模拟支付（回退）

## 角色定义
- `STUDENT`：使用 AI 工具、技能成长、社区互动、购买导师咨询、提交企业任务。
- `MENTOR`：维护主页、接单并进行异步咨询回复。
- `ENTERPRISE`：发布悬赏任务并审核学生提交。
- `ADMIN`：内容治理、AI 配置、特性开关与日志审计。

## 开发优先级
1. 先完成 `P0`（核心闭环）。
2. 再完成 `P1`（商业与产品价值闭环）。
3. 最后做 `P2` 单一亮点（语音面试最小闭环）。

## 快速启动命令约定
```bash
# 前端
cd apps/web && npm install && npm run dev

# Java 后端（含 AI 模块）
cd apps/server-java && ./mvnw spring-boot:run

# 基础设施
cd infra && docker compose up -d
```

## 文档导航

> **按需加载原则**：每次会话仅读取与当前任务直接相关的文档。本文件（00）为必读基线。

| 文档 | 适用场景 |
|------|----------|
| [01_PRD.md](./01_PRD.md) | 理解业务范围与产品目标 |
| [02_SRS.md](./02_SRS.md) | 查阅 FR/NFR 编号、状态机、权限矩阵 |
| [03_ARCHITECTURE.md](./03_ARCHITECTURE.md) | 理解架构边界、时序、可靠性设计 |
| [04_API_SPEC.md](./04_API_SPEC.md) | 开发具体接口时查阅契约 |
| [05_DB_SCHEMA.md](./05_DB_SCHEMA.md) | 开发数据层、编写迁移脚本 |
| [06_AI_MODULE_SPEC.md](./06_AI_MODULE_SPEC.md) | 开发 AI 模块（路由、成本、配额） |
| [07_PAYMENT_AND_ORDER_SPEC.md](./07_PAYMENT_AND_ORDER_SPEC.md) | 开发支付与订单模块 |
| [08_TEST_PLAN.md](./08_TEST_PLAN.md) | 进入测试阶段 |
| [09_DEPLOYMENT_RUNBOOK.md](./09_DEPLOYMENT_RUNBOOK.md) | 部署与运维 |
| [10_MILESTONE_AND_BACKLOG.md](./10_MILESTONE_AND_BACKLOG.md) | 阶段规划与进度回顾 |
| [11_RISK_FALLBACK_DECISIONS.md](./11_RISK_FALLBACK_DECISIONS.md) | 遇到风险或需要回退决策 |
| [12_AGENT_TASK_BREAKDOWN.md](./12_AGENT_TASK_BREAKDOWN.md) | 任务拆解与选择 |
| [13_ACCEPTANCE_CHECKLIST.md](./13_ACCEPTANCE_CHECKLIST.md) | 发布前验收 |
| [14_FRONTEND_ROUTES_AND_PAGES.md](./14_FRONTEND_ROUTES_AND_PAGES.md) | 开发前端页面 |
| [15_FRONTEND_REFACTOR_CHECKLIST.md](./15_FRONTEND_REFACTOR_CHECKLIST.md) | 前端正式重构清单 |
| [16_SKILLS_STAR_MAP_ALIGNMENT_PLAN.md](./16_SKILLS_STAR_MAP_ALIGNMENT_PLAN.md) | `/skills` 星图模板对齐与分阶段实施规划 |

## 不可更改的默认决策
- v1 使用模块化单体，不拆微服务。
- v1 不做复杂推荐算法，采用规则匹配。
- 企业任务仅做文本/链接点评，不做代码沙箱执行。
- 种子数据人工预置。
- 支付双轨并存：沙箱可用，模拟可切换。
- 语音仅做“上传音频 -> STT -> LLM -> TTS”最小闭环，不做实时流式（暂时）。
- 前端双库策略：C 端使用 shadcn/ui + Tailwind，Admin 后台使用 Ant Design，路由级隔离。
- 所有 AI 调用必须统一经过 Java 后端内部的 AI 模块（不独立部署额外 AI 服务）。
- AI 调用消耗积分（FREE 用户每日有免费额度，超出扣积分；PREMIUM 用户不限）。
- v1 内容治理仅覆盖文本（用户输入、AI 输出、社区内容），不做图片/音频 OCR 审查。

## 本文档范围
本文档是总入口与全局基线，模块细则以 `01` 到 `16` 文档为准。

## 术语表
- `MVP`：最小可行产品。
- `闭环`：无需人工补丁即可完整演示的一条业务链。
- `支付双轨`：沙箱支付和模拟支付均可运行，可通过开关切换。
- `Trace ID`：请求全链路追踪标识。
- `Ledger`：追加式账本，不覆盖历史记录。
- `RBAC`：基于角色的访问控制（Role-Based Access Control）。
- `幂等`：重复请求不会产生副作用，结果与单次请求一致。
- `STT`：语音转文本（Speech-To-Text）。
- `TTS`：文本转语音（Text-To-Speech）。
- `状态机`：业务对象（如订单）按预定义规则在有限状态间流转的模型。
- `SSOT`：单一真相源（Single Source of Truth），下方枚举定义即为本项目的枚举 SSOT。
- `内容审查`：对用户输入、AI 输出和社区文本做规则判定，输出风险等级和处置动作。

## 统一枚举定义（SSOT）

> **本节是所有枚举值的唯一真相源。** 其他文档（SRS/API/DB/AI 模块）引用此处，不再独立定义。
> 新增或变更枚举值时，必须先修改本节，再同步到引用处。

- **`UserRole`**：`STUDENT | MENTOR | ENTERPRISE | ADMIN`
- **`UserTier`**：`FREE | PREMIUM`
- **`OrderStatus`**：`CREATED | PAYING | PAID | ANSWERED | CLOSED | FAILED | CANCELED | REFUNDED`
- **`SkillNodeStatus`**：`LOCKED | LEARNING | MASTERED`
- **`AiTaskType`**：`RESUME | INTERVIEW_TEXT | INTERVIEW_SUMMARY | COMMUNITY_REPLY | ICEBREAK | STT | TTS`
  > `INTERVIEW_SUMMARY`：面试结束后生成总结报告。`EMOTION_LIGHT` 已移除，该功能不在 v1 范围内。
- **`BountySubmissionStatus`**：`SUBMITTED | REVIEWING | ACCEPTED | REJECTED`
- **`UserAccountStatus`**：`ACTIVE | PENDING | SUSPENDED`
- **`PaymentMode`**：`SANDBOX | MOCK`
- **`ModerationAction`**：`PASS | MASK | BLOCK | REVIEW`
- **`ModerationRiskLevel`**：`LOW | MEDIUM | HIGH | CRITICAL`
- **`ReportTargetType`**：`POST | COMMENT | USER`
- **`ReportStatus`**：`PENDING | ACCEPTED | REJECTED | CLOSED`
- **`ModerationSourceType`**：`AI_INPUT | AI_OUTPUT | COMMUNITY_POST | COMMUNITY_COMMENT | CONSULT_MESSAGE | BOUNTY_TEXT`

## 验收基线
满足以下条件则本文档目标达成：
1. 任何新 Agent 仅读取本文档与导航可进入开发。
2. 所有全局约束与默认值在子文档中可追溯。
3. 枚举定义与所有引用文档保持一致。
