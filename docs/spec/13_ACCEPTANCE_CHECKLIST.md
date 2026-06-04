# 验收检查清单

> **文档状态**：`evolving` · 最后审核：2026-04-18 · 已按当前实现、当前部署结果与现有自动化覆盖同步一轮；后续继续随增量更新。
>
> 补充说明：
> - 本文中的 `[x]` 表示“当前实现与既有自动化/人工验收记录已能支撑该能力成立”，不等同于“已完成最新一轮全浏览器回归”。
> - 对仍依赖真环境体验确认的事项，本轮采用“新增补充检查项”方式记录，而不直接回退既有勾选状态。


## 1. 模块验收清单
### 1.1 鉴权与权限
- [x] 注册/登录/当前用户接口可用。
- [x] 角色权限拦截生效。
- [x] 正式页角色守卫已集中到路由层，不再由各页面在顶层各自处理 `Navigate("/403")`。
- [x] `/dashboard` 已收口为共享工作台入口，登录后会按角色分流到 `/student/dashboard`、`/mentor/dashboard`、`/enterprise/dashboard` 或 `/admin/dashboard`。
- [x] 学生 / 导师 / 企业误入其他角色私有域时，会优先自动回到当前角色工作台或最近业务页；明确受限功能仍统一进入 `/403`。
- [x] 越权请求返回 `AUTH-1004`。
- [x] 正式注册链路支持 `Geetest -> /auth/email/send-code -> /auth/email/verify-code -> emailVerificationToken -> /auth/register(/with-certification)`。
- [x] 登录页“忘记密码”已接入真实找回链路：发送验证码、验证码校验与重置密码均可通过公开 auth 接口完成。
- [x] 导师 / 企业注册资料阶段已切到正式认证资料提交流程，不再依赖 debug 假注册主链路。
- [x] 导师 / 企业注册成功后可进入各自真实工作台，并保留退出登录入口便于重复调试。
- [x] 导师资料页已形成真实工作区：支持头像上传、昵称编辑、真名展示开关、密码修改弹层、认证材料管理与排期批量维护。

### 1.2 AI 文本工具
- [x] 简历优化返回结构化结果。
- [x] 文本面试支持多轮会话。
- [x] AI 响应带元信息和 `traceId`。
- [x] `/ai/history` 已作为统一 AI 复盘中心承接简历与模拟面试 deep-link 回流，通知与页面 CTA 可直接带入目标记录。

### 1.3 成长体系
- [x] 技能树展示与进度持久化。
- [x] 当日重复签到会被拦截。
- [x] 积分账本为追加式。
- [ ] 若进入 `/skills` 星图模板专项，至少完成 Phase 1：主画布、节点详情与状态更新已接真实技能树契约；Phase 2/3 项按 `docs/spec/16_SKILLS_STAR_MAP_ALIGNMENT_PLAN.md` 分阶段验收。

### 1.4 社区
- [x] 发帖/回帖/点赞可用。
- [x] AI 一楼回答带 AI 标识。
- [x] 审核下架有审计日志。
- [ ] 社区发帖与回帖已统一使用轻量 Markdown 编辑器壳层，并能稳定渲染标题、分点、加粗、引用、代码块与链接。
- [ ] 社区首页支持关键词、问题场景与标签筛选，帖子详情支持 `resolvedStatus` 展示与回帖闭环。

### 1.5 咨询与支付
- [x] 订单可创建。
- [x] 支付可发起。
- [x] 回调验签可用。
- [x] 重复回调幂等通过。
- [x] 订单可演示 `PAID -> ANSWERED -> CLOSED`。
- [x] 导师订单中心已切到 `GET /api/v1/consult/orders/mentor-workbench`，支持关键词、状态、服务方式、时间、支付方式、风险、排序与服务端分页。
- [x] 导师履约区已接通订单详情、消息、附件与 `POST /api/v1/consult/orders/{orderNo}/ai-reply-draft`，且仅在 `PAID / ANSWERED` 状态允许继续发送导师消息。
- [x] 导师财务中心已接通 `GET /api/v1/mentor/finance/overview` 与提现记录持久化接口，账单前台每页 `8` 条、提现记录前台每页 `3` 条。
- [x] 管理员可对 `PAID/ANSWERED/CLOSED` 订单执行手工退款，必要时释放预约时段并回收评价/导师统计。
- [x] `SANDBOX` 订单支持交易查询、未支付关单与退款状态查询，且交易查询可补记 `PAYING -> PAID`。
- [x] 管理后台支付对账页可识别异常单，并支持人工取消未支付挂起单、补记支付成功或登记外部退款已确认。

### 1.6 企业任务 / 悬赏
- [x] 企业可发布任务，并支持手动关闭/重开未收口任务。
- [x] 学生可提交链接/文本成果，重复提交会被拦截。
- [x] 企业任务审核工作区当前可按状态、关键词与最小贡献分筛选学生提交；其中 `PENDING` 视图按 `SUBMITTED + REVIEWING` 合并，贡献分筛选已接入真实后端提交列表，关键词搜索继续保留前端即时过滤。
- [x] 企业可审核并结束状态，采纳后其他待审核提交自动收口为未采纳。
- [x] 企业审核结果可触发统一通知入箱，并可从通知中心稳定直达学生 `/bounty/:taskId` 或企业 `/enterprise/tasks/:taskId` 对应页面回看该结果。
- [x] 企业工作台至少展示认证状态、任务统计、待处理提交、最近任务与快捷发布入口。
- [x] 企业工作台右侧已形成“最新业务提醒 + 结果与继续接触留痕”双闭环，分别承接企业真实通知收件箱与企业自己发出的审核结果记录。
- [x] 企业工作台首页当前已收口为“需要处理的任务轻量分页 + 业务提醒轮播 + 处理记录轮播”；其中任务列表每页 `5` 条，业务提醒展示最近 `3` 条，处理记录展示最近 `5` 条，不再固定截断或无限拉长首页。
- [x] 企业审核工作区可向认可的学生发送一次“继续接触 / 进一步交流 / 面试邀请 / 录用意向”等轻量通知，并在通知中心留痕；当前还支持按本次审核选择是否同步进入邮件队列。
- [x] 企业可向未入选学生发送结果通知，支持鼓励性模板或补充备注；当前也支持按本次审核选择是否同步进入邮件队列。
- [x] 企业资料页当前已收口为 `profile / certification / notifications` 页内路由，支持 Logo 设置、认证附件查看与密码修改弹窗。

### 1.7 语音亮点
- [x] 语音最小闭环至少成功一次。
- [x] STT/TTS 失败能回退文本。
- [x] 模拟面试页支持 AI 追问播报与报告页“朗读本轮总结”，前端已完成 PCM/音频格式适配与播放态切换。

### 1.8 后台运维能力
- [x] 可配置模型路由。
- [x] 管理后台可预览路由命中结果。
- [x] `/admin/ai/runtime-settings` 可切换调试模式与 AI 请求日志开关，并立即影响 AI 网关运行时日志行为。
- [x] AI 日志可按条件查询。
- [x] Provider `maxRetries` 可作为重试机制替代探活，并能在开启日志开关后看到重试日志。
- [x] 管理后台可管理 Prompt 模板版本（`DRAFT/ACTIVE/INACTIVE`）。
- [x] 管理后台支持支付模式 / 语音 / 社区 AI 预答三类功能开关，并立即影响运行时行为。
- [x] Swagger UI 可通过 `/swagger-ui/index.html` 打开，并基于 `/v3/api-docs` 渲染接口文档。
- [x] Route 可绑定 `promptTemplateName`，且列表/预览可显示当前命中的模板版本。
- [x] 绑定模板的 route 在运行时优先使用 `ACTIVE` 模板内容；未绑定模板时回退 `systemPrompt`。
- [x] 管理后台支持 Prompt 模板渲染预览，并返回占位变量、缺失变量与最终渲染结果。
- [x] 管理后台支持显式发布 Prompt 模板版本，并自动降级同模板族的旧 `ACTIVE` 版本。
- [x] 管理后台支持把 Prompt 模板回滚到指定历史版本。
- [x] 绑定模板的 route 若缺少 `ACTIVE` 版本或运行时变量缺失，会返回 `AI-2003`，不静默回退。
- [x] 生产部署口径当前已统一为 `Caddy + 双层 Docker Compose + GitHub Actions 自动更新应用层`。

### 1.9 AI 配额与成本
- [x] FREE 用户超出每日免费额度后正确扣积分。
- [x] 积分不足时返回友好提示（`AI-2201`）。
- [x] PREMIUM 用户不受配额限制。
- [x] 管理首页可查看运营数据看板。
- [x] 管理后台成本看板可查看调用统计。
- [x] 管理后台支持按 `today/week/month` 导出 AI 成本看板 CSV。
- [x] 管理后台可查看并更新 AI 配额策略（含 `maxInputTokens`）。

### 1.10 站内通知
- [x] AI 简历异步任务、咨询/支付/售后、企业悬赏、认证审核与系统公告可触发统一通知入箱。
- [x] AI 模拟面试总结生成后可触发统一通知入箱，并 deep-link 到 `/ai/history?type=interview&sessionId=...`。
- [x] 未读通知数量、单条已读与全部已读正确。
- [x] WebSocket 短票据握手与 ACK 回执可工作，在线用户能收到实时推送。
- [x] 浏览器 Notification API 在用户授权且页面非聚焦时可触发桌面提醒。
- [x] 通知偏好可控制 `websocket/browserPopup/email` 渠道。
- [ ] `COMMUNITY` 分类已有限正式接入：至少支持帖子回复、审核结果、举报结果中的核心事务通知，并稳定跳转到 `/community/:postId` 或 `/community/reports`；点赞等高频事件继续后置。

### 1.11 体验增强
- [x] 简历优化支持 SSE 分段流式输出（当前为服务端按总结 / 优势 / 风险 / 建议分段推送最终结果）。
- [x] AI 面试文本/语音支持真流式文字返回：`/reply/stream` 返回 `reply_delta`，`/voice-roundtrip/stream` 返回 `transcript + reply_delta`，OpenAI-compatible 主链路接入 provider 原生 SSE。
- [x] AI 面试当前已支持 `text / voice / live` 三模式；其中 `live` 测试模式已形成“Java 占位创建 -> Python WS bridge -> transcript 回灌 -> 正式 summary / 通知 / 复盘中心”的最小闭环。
- [x] AI 简历异步任务离开页面后可通过通知中心补偿查看结果。
- [x] AI 面试总结报告可生成。
- [x] 咨询评价可提交且导师评分更新。
- [x] 导师列表支持搜索筛选。
- [x] AI 使用历史可回溯。
- [ ] AI 面试 `live` 测试模式仍需继续做浏览器实机回归：至少确认麦克风权限、`/ai/interview/live/ws` 连通性、实时字幕滚动、静音/重连，以及“结束 Live 并生成复盘”后的 transcript 回灌。

### 1.12 学生画像与社区贡献榜
- [x] 学生资料支持 `skillTags` + `selfIntro` 冷启动输入。
- [x] 学生画像可返回标签、证据计数、更新时间。
- [x] 技能进度或社区互动后可触发画像刷新（含每日兜底重算）。
- [x] 企业在任务提交列表可按画像标签与最小贡献分筛选。
- [x] 社区 7 日贡献榜排序符合约定公式与排序规则。

### 1.13 性能基线
- [ ] 非 AI 接口 p95 < 500ms。
- [ ] AI 接口 p95 < 6s（外部故障除外）。
- [x] 支付回调在重复通知下稳定。
- [ ] 10 并发用户下无数据破坏。

### 1.14 内容审查与举报治理
- [x] AI 输入命中高风险时返回 `MOD-1001` 且阻断调用。
- [x] AI 输出命中高风险时返回 `MOD-1002` 或脱敏结果。
- [x] 社区内容支持 `PASS|REVIEW|BLOCK`，待审内容默认不可见。
- [x] 举报创建、去重、频控（`MOD-1004`）可用。
- [x] 管理员可在举报中心完成处置并回写举报状态。
- [x] 敏感词与策略管理可在线生效。
- [x] 审查事件与处置流水可通过 `traceId` 追踪。

## 2. 演示前检查
- [ ] 种子数据已加载。
- [ ] 四类角色演示账号可登录。
- [ ] 沙箱支付与模拟回退都验证过。
- [ ] 若演示环境启用正式注册校验，Geetest 与 Resend 凭据已配置并完成现场环境发信检查。
- [ ] 浏览器通知权限、WebSocket 连接与 `/notifications` 路由已做一轮真环境检查。
- [ ] 已用学生 / 导师 / 企业账号做一轮跨角色访问回归，确认私有域走 `redirect`、明确受限功能走 `/403`，且 `/403` 登录态按钮文案与回退落点正确。
- [ ] 已用导师账号完成 `/mentor/dashboard -> /mentor/orders -> /mentor/orders/:orderNo/workspace -> /mentor/finance -> /mentor/profile` 一轮全链路宽屏桌面端回归，确认顶栏、跳转、缓存静默刷新与状态口径一致。
- [ ] `/community`、`/community/:postId`、`/community/reports`、`/community/leaderboard` 的 Markdown 渲染、筛选与通知回流已做一轮宽屏桌面端检查。
- [ ] 语音样例音频准备完成。
- [ ] 核心演示脚本已彩排。
- [x] 论文引用的 PRD / SRS / 架构 / API 文档已按最终实现同步，不再保留明显过时的规划态描述。

## 3. 发布前检查
- [ ] P0 全量完成。
- [ ] P1 付费咨询闭环完成。
- [ ] P2 语音最小闭环完成。
- [ ] 无 P0 阻塞缺陷。
- [ ] 安全基线通过。
- [ ] 回滚预案可执行。

## 4. 文档一致性检查
- [ ] `UserRole` 在 PRD/SRS/API/DB 一致。
- [ ] `OrderStatus` 在 SRS/API/DB/支付文档一致。
- [ ] `SkillNodeStatus` 在 SRS/API/DB 一致。
- [ ] `AiTaskType` 在 SRS/API/DB/AI 模块文档一致。
- [ ] `NotificationCategory` / `NotificationChannel` / `NotificationDispatchStatus` 在专项/API/DB/SRS/架构文档一致。
- [ ] `UserTier` 在 README/SRS/API/DB/AI 模块文档一致。
- [ ] `ModerationAction` 在 README/SRS/API/DB 文档一致。
- [ ] `ModerationRiskLevel` 在 README/SRS/API/DB 文档一致。
- [ ] `ReportTargetType` / `ReportStatus` 在 README/SRS/API/DB 文档一致。
- [ ] `ModerationSourceType` 在 README/SRS/API/DB 文档一致。
- [ ] 回退策略在 PRD/SRS/风险文档一致。
- [ ] API 示例与 DB 字段语义一致。

补充关注项（截至 2026-03-19）：
- 正式注册链路中的 `captchaVerificationToken`、Geetest `verificationToken` 与 `emailVerificationToken` 语义需继续保持一致，不得在不同文档中混用。
- 生产部署口径需统一以 `Caddy + infra/docker 双层 Compose` 为准，不再回退到旧的 `deploy/.env + Nginx` 表述。
- `/skills` 当前 UI 语义中的 `LOCKED` 由 `unlocked=false` 推导，而真实进度状态仍以 `NOT_STARTED/LEARNING/MASTERED` 为准；后续涉及 skills 专项时，优先以 `docs/spec/16` 中的映射说明保持文档口径。
- `/ai/interview` 当前支持 `text / voice / live` 三模式；其中 `live` 为测试模式，论文与文档中不应包装成已经替代正式语音面试主链。

## 5. 完成定义（DoD）
- [ ] 社区专项 UI 文档已补齐，`docs/spec` 文档集结构完整且可直接供新 Agent 接手。
- [ ] 每份文档包含目标、范围、决策、验收。
- [ ] 新 Agent 仅读取中文文档集即可开始实施。
- [ ] 枚举定义与 SSOT 一致，无漂移。

## 6. 本文档范围
作为最终交付前检查清单，不承载新的功能定义。

## 7. 决策摘要
- 验收以“闭环可演示 + 文档一致 + 可回退”为标准。

## 8. 验收标准
1. 核心检查项全部打勾。
2. 关键枚举与状态机无漂移。
