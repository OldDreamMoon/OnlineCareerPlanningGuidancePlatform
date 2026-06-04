# 平台级通知系统模块规范

> **文档状态**：`evolving` · 最后审核：2026-03-26 · 本文用于收口“平台级通知系统首版”需求与实现边界，后续 API / DB / 路由同步以本文件为专项基线。
>

## 0. 当前实现更新（2026-03-26）

截至本轮，平台级通知系统首版已经形成真实代码落地，当前已实现：

- 后端统一通知基座
  - `notifications` 已升级为站内收件箱真相层
  - 已新增 `notification_events`、`notification_dispatch_jobs`、`notification_dispatch_attempts`、`notification_preferences`
  - 已落地统一发布服务、偏好服务、渠道 worker、WebSocket ticket 握手与 ACK 回执
- 首版已接通的业务事件
  - AI 简历异步任务成功 / 失败
  - 咨询 / 支付 / 售后等既有旧链路的兼容通知
  - 企业悬赏提交提醒与审核结果提醒
  - 认证审核结果
  - 管理员系统公告广播
- 前端真实接入
  - 已新增 `/notifications` 共享通知中心页
  - 已接通 `POST /api/v1/notifications/ws-ticket` 与 `/ws/notifications?ticket=...`
  - 已接通浏览器 Notification API
  - 已在学生工作台、导师工作台、AI 简历优化页顶栏启用真实通知铃铛入口
  - 企业 `VIEW_BOUNTY_TASK` 当前已优先 deep-link 到 `/enterprise/tasks/:taskId`，若 payload 内存在 `submissionId`，则会进一步定位到 `/enterprise/tasks/:taskId?submissionId=...`
  - 学生 `VIEW_BOUNTY_TASK` 当前已优先 deep-link 到 `/bounty/:taskId`
  - 企业 `VIEW_CERTIFICATION_STATUS` 当前已优先 deep-link 到 `/enterprise/profile`
- 当前仍明确未纳入
  - 社区点赞 / 有帮助等高频噪音类通知
  - `POST /api/v1/notifications/test-email`
  - 通知治理后台页与复杂规则编排器

## 1. 目标与定位

当前项目已经不再处于“功能概念验证”阶段，而是进入“多业务域已真实落地、需要统一用户触达能力”的阶段。

现有系统里已经存在：

- 咨询域与悬赏域触发的基础站内通知
- `notifications` 表与列表 / 未读数 / 已读回写 API
- AI 异步任务的 `ai_async_task_jobs` 与 `ai_async_task_events`

但当前仍缺少以下平台级能力：

- 统一的通知事件模型，而不是各业务各自直接写一条通知文案
- 统一的站内收件箱视图与稳定跳转语义
- 网页端实时通知推送能力
- 离线通知补偿与“错过后再拉取”的完整闭环
- 渠道抽象、队列重试、投递生命周期管理
- 面向未来邮箱通知、通知规则、更多业务域接入的兼容能力

一句话定位：

`平台级通知系统应该成为“所有业务事件对用户的统一触达中枢”，而不是某个单独业务的附属提醒模块。`

## 2. 首版目标

本次首版不是只做一个“通知列表页”，而是要落地平台级通知系统的主体骨架。

首版目标如下：

1. 保留并升级现有站内通知能力，使其成为统一收件箱真相层。
2. 新增网页端实时通知推送，正式接通 WebSocket。
3. 支持离线通知拉取、未读统计、补偿同步与基础生命周期管理。
4. 抽象统一的通知事件、投递任务、渠道配置与用户规则模型。
5. 首版即支持站内通知与网页实时通知两条正式渠道。
6. 首版在设计上兼容邮箱通知，并允许邮箱通知在同一流程下实际接入。
7. 首版支持基础通知规则，不要求一步到位做到复杂营销级规则引擎。
8. 首版仅有限接入社区事务通知：允许帖子回复、审核结果、举报结果进入统一通知中心；点赞等高频噪音类事件继续后置，避免范围膨胀与跳转面失控。

## 3. 范围定义

### 3.1 首版纳入范围

- 统一通知事件模型
- 统一站内通知收件箱
- 网页端 WebSocket 实时推送
- 浏览器 Notification API 提醒
- 离线通知拉取与未读数同步
- 通知队列、重试、死信 / 失败留痕
- 基础通知规则
- 邮件渠道抽象与首批邮件通知接入
- 以下业务域接入：
  - AI 异步任务
  - 导师咨询与支付 / 售后
  - 企业悬赏
  - 认证审核
  - 系统公告
  - 社区（限帖子回复、审核结果、举报结果）

### 3.2 首版暂不纳入范围

- 社区点赞 / 有帮助、社区榜单变化、AI 自动首评等高频或弱事务通知
- App Push、短信、微信模板消息等移动端 / 外部渠道
- 复杂多级审批流通知
- 营销消息、批量运营活动消息
- 用户自定义复杂规则编排器
- 多端会话同步优化到“每个设备单独已读状态”

### 3.3 首版边界说明

- 站内收件箱仍是平台通知的事实真相层。
- WebSocket 与浏览器 Notification API 属于触达层，不是数据真相层。
- 邮箱通知首版可接入，但只接“事务型通知”，不进入营销或订阅邮件体系。
- 社区域首版只接事务型通知：帖子回复、审核结果、举报结果；其他高频弱事务事件继续后置，不进入正式触达范围。

## 4. 上位约束与当前现实

### 4.1 上位约束

- `docs/spec/01_PRD.md`
  - 站内通知属于 P1 正式能力
  - 整体产品强调“AI + 成长 + 导师 + 企业”的统一闭环体验
- `docs/spec/02_SRS.md`
  - `FR-054 ~ FR-057` 已定义系统事件通知、实时推送、离线拉取与已读能力
- `docs/spec/03_ARCHITECTURE.md`
  - 当前架构口径已经承认“事件写入通知表 -> 前端拉取未读 -> 浏览器弹窗提醒”的基本路径
- `docs/spec/06_AI_MODULE_SPEC.md`
  - AI 异步任务历史口径以轮询为主，当前首版已开始接入统一通知中心
- `docs/spec/14_FRONTEND_ROUTES_AND_PAGES.md`
  - `/notifications` 已被声明为正式路由，本轮已补齐真实前端工作区落地

### 4.2 当前现实

- 后端已有基础通知模块：
  - `notification` 包
  - `notifications` 表
  - `GET /notifications`
  - `GET /notifications/unread-count`
  - `POST /notifications/{id}/read`
  - `POST /notifications/read-all`
- 当前业务里已直接触发通知的模块主要是：
  - 咨询
  - 支付 / 售后
  - 企业悬赏
- 当前 AI 异步任务成功 / 失败、认证审核结果与系统公告已经和统一通知中心打通。
- 当前前端已经真实接入：
  - `/notifications`
  - 顶栏通知铃铛入口
  - WebSocket 实时推送
  - 浏览器 Notification API
- 当前社区通知已从“完全后置”调整为“有限正式接入”规划位：只覆盖帖子回复、审核结果、举报结果，其他事件继续保持边界收口。

这意味着首版不能按“从零开始造通知模块”的思路推进，而应按“从咨询域基础通知升级为平台级通知系统”的思路推进。

## 5. 核心设计原则

### 5.1 收件箱优先

站内收件箱是通知事实真相层。

无论实时推送、浏览器弹窗还是邮箱发送是否成功：

- 站内通知都必须先有稳定入箱记录
- 用户未在线时也必须可在后续登录后看到
- 前端页面必须可基于站内收件箱完成追溯与补偿

### 5.2 事件与投递分离

通知事件不等于通知投递。

同一个业务事件可能：

- 生成一条或多条站内通知
- 同时触发 WebSocket 推送
- 同时触发邮件投递
- 部分渠道成功、部分渠道失败

因此必须分开建模：

- 业务事件
- 用户收件箱项
- 渠道投递任务

### 5.3 结构化跳转优先于硬编码 URL

服务端不直接固化最终页面 URL，而应固化：

- `refType`
- `refId`
- `actionCode`
- 必要的 `payloadJson`

由前端根据当前真实路由解析跳转目标。

这样可以避免当前前端路由仍在演进时，后端提前把 URL 写死导致死链。

### 5.4 WebSocket 是实时通道，不是唯一通道

WebSocket 负责“尽快通知”，不是“唯一通知来源”。

- 连接断开时不能丢业务事实
- 推送失败时不能影响站内通知入箱
- 客户端仍需保留轮询与重同步能力

### 5.5 规则决定是否触达，不决定是否留痕

首版规则默认只影响“额外渠道是否触发”，不应让核心事务通知完全消失。

建议原则：

- 站内收件箱默认保留平台关键事务通知
- 用户规则主要影响：
  - 是否弹浏览器提醒
  - 是否发邮件
  - 是否允许低优先级通知进入“打扰态”

### 5.6 首版优先事务型通知

首版优先支持与任务、订单、审核结果直接相关的事务型通知，不以社交互动和营销消息为主。

## 6. 首版角色与典型场景

### 6.1 学生

- AI 简历异步任务提交后离开页面，任务成功 / 失败时收到通知
- 导师回复咨询、退款结果、售后审核结果时收到通知
- 企业审核悬赏提交结果时收到通知

### 6.2 导师

- 学生完成支付后收到新订单提醒
- 学生发起售后、订单被关闭、评价提交后收到通知
- 认证审核结果变更时收到通知

### 6.3 企业

- 学生提交悬赏成果后收到通知
- 企业自身认证结果变更时收到通知

### 6.4 管理员

- 首版不以管理员个人通知为重点
- 但系统公告与后续死信 / 失败投递治理页面需保留扩展位

## 7. 首版事件接入范围

### 7.1 AI 异步任务

首版正式接入：

- `AI_RESUME_TASK_SUCCEEDED`
- `AI_RESUME_TASK_FAILED`
- `AI_INTERVIEW_SUMMARY_READY`

可选接入：

- `AI_RESUME_TASK_SUBMITTED`
- `AI_RESUME_TASK_RETRY_SCHEDULED`

首版动作语义建议：

- `VIEW_AI_RESUME_TASK_RESULT`
- `VIEW_AI_RESUME_TASK_STATUS`
- `VIEW_AI_REVIEW_CENTER`

### 7.2 咨询 / 支付 / 售后

首版正式接入：

- `CONSULT_PAID`
- `CONSULT_REPLIED`
- `CONSULT_CANCELED`
- `CONSULT_TIMEOUT_CANCELED`
- `CONSULT_CLOSED`
- `CONSULT_REVIEWED`
- `CONSULT_AFTER_SALES_SUBMITTED`
- `CONSULT_AFTER_SALES_PENDING`
- `CONSULT_AFTER_SALES_REJECTED`
- `CONSULT_REFUNDED`
- `CONSULT_PAYMENT_RECONCILED`
- `CONSULT_PAYMENT_EXCEPTION`

建议预留但可后续补：

- `CONSULT_ATTACHMENT_UPDATED`
- `CONSULT_SLA_RISK`

### 7.3 企业悬赏

首版正式接入：

- `BOUNTY_SUBMITTED`
- `BOUNTY_REVIEWED`

### 7.4 认证审核

首版正式接入：

- `CERTIFICATION_APPROVED`
- `CERTIFICATION_REJECTED`
- `CERTIFICATION_RESUBMIT_REQUIRED`

### 7.5 系统公告

首版正式接入：

- `SYSTEM_ANNOUNCEMENT`
- `SYSTEM_MAINTENANCE`

### 7.6 首版明确不接入

- `POST_COMMENTED`
- `POST_LIKED`
- `REPORT_RESULT_UPDATED`
- 其他社区互动类事件

## 8. 通知生命周期

首版通知生命周期统一定义为：

1. **业务触发**
   - 业务模块产生领域事件，例如“简历任务成功”“导师回复订单”“企业审核结果已出”
2. **事件标准化**
   - 通知模块把业务事件转成统一 `NotificationEvent`
3. **接收人与规则解析**
   - 解析接收人
   - 解析优先级
   - 套用通知规则与渠道偏好
4. **站内入箱**
   - 生成用户收件箱记录
   - 这是通知真相层
5. **渠道投递任务入队**
   - 生成 WebSocket / Email 等渠道任务
6. **投递执行**
   - worker 取任务并执行
   - 失败时重试
7. **客户端确认**
   - WebSocket 通道可做收到确认
   - 但“收到”不等于“已读”
8. **用户已读 / 归档**
   - 用户在收件箱中标记已读
   - 后续可支持归档
9. **保留与清理**
   - 历史通知按保留策略归档或清理

### 8.1 关键语义区分

- `投递成功`
  - 某个渠道成功把通知发出去
- `客户端收到`
  - 浏览器通过 WS 收到消息并确认
- `用户已读`
  - 用户真正打开或手动标记为已读

首版不允许把这三个状态混为一谈。

## 9. 模块架构设计

### 9.1 模块分层

建议采用以下分层：

```text
business domain
  -> NotificationPublishService
     -> EventNormalizer / RecipientResolver / RuleEvaluator
        -> InboxRepository
        -> DispatchJobRepository
           -> WebSocketDispatcher
           -> EmailDispatcher
           -> Retry / Dead-letter Manager
```

### 9.2 业务模块接入方式

禁止业务模块继续长期直接写原始通知文案。

建议统一改为：

- `publish(NotificationPublishCommand command)`

命令中至少包含：

- `eventType`
- `category`
- `sourceType`
- `sourceId`
- `actorUserId`
- `recipientUserIds`
- `priority`
- `payload`
- `dedupeKey`

### 9.3 模板渲染层

通知模板建议采用代码注册表优先的方式，不强制首版做数据库模板平台。

首版建议：

- 站内标题与摘要模板：代码注册表
- 邮件标题与正文模板：代码注册表
- 保留后续迁移为 DB 配置模板的扩展位

原因：

- 首版重点在统一流程与渠道可靠性，不在通知运营后台
- 当前项目已存在 AI 模板后台，通知模板若同步做成后台管理，范围会显著放大

## 10. 数据模型设计

首版建议引入三层数据结构。

### 10.1 `notification_events`

用途：

- 记录标准化后的业务通知事件
- 作为 fan-out 与审计起点

建议字段：

- `id`
- `event_id`
- `category`
- `event_type`
- `source_type`
- `source_id`
- `actor_user_id`
- `priority`
- `dedupe_key`
- `payload_json`
- `occurred_at`
- `created_at`

### 10.2 `notifications`

用途：

- 用户站内收件箱
- 已读 / 未读与页面展示真相层

建议在现有表基础上升级，补齐：

- `category`
- `title`
- `content`
- `ref_type`
- `ref_id`
- `action_code`
- `priority`
- `event_id`
- `payload_json`
- `archived_at`
- `read_at`
- `created_at`

说明：

- 当前已有 `notifications` 表可以继续保留名称，避免无谓迁移震荡
- 但首版应把其语义升级为“用户收件箱项”，而不是“简单提醒表”

### 10.3 `notification_dispatch_jobs`

用途：

- 记录每个渠道的投递任务
- 支撑重试、失败、死信与运营可追溯

建议字段：

- `id`
- `job_id`
- `notification_id`
- `event_id`
- `user_id`
- `channel`
- `status`
- `attempt_count`
- `max_attempts`
- `next_run_at`
- `reserved_by`
- `reserved_at`
- `sent_at`
- `acked_at`
- `failed_at`
- `error_code`
- `error_message`
- `payload_json`
- `created_at`
- `updated_at`

### 10.4 `notification_dispatch_attempts`

用途：

- 留痕每次真实投递尝试

建议字段：

- `id`
- `job_id`
- `attempt_no`
- `status`
- `request_snapshot_json`
- `response_snapshot_json`
- `error_code`
- `error_message`
- `latency_ms`
- `created_at`

### 10.5 `notification_preferences`

用途：

- 用户通知规则与渠道偏好

建议字段：

- `id`
- `user_id`
- `category`
- `inbox_enabled`
- `websocket_enabled`
- `browser_popup_enabled`
- `email_enabled`
- `email_urgency_threshold`
- `quiet_hours_json`
- `updated_at`

### 10.6 兼容迁移策略

首版迁移建议：

1. 保留现有 `notifications` 表
2. 增量补字段，而不是直接推翻重建
3. 新增 `notification_events`
4. 新增 `notification_dispatch_jobs`
5. 新增 `notification_dispatch_attempts`
6. 新增 `notification_preferences`

原因：

- 当前咨询 / 悬赏通知已依赖 `notifications`
- 增量升级可以最小化对已落地链路的破坏

## 11. 状态与枚举建议

### 11.1 `NotificationCategory`

- `AI_TASK`
- `CONSULT`
- `BOUNTY`
- `CERTIFICATION`
- `SYSTEM`
- `COMMUNITY`

说明：

- `COMMUNITY` 首版作为有限正式分组存在，只接帖子回复、审核结果、举报结果等核心事务事件

### 11.2 `NotificationPriority`

- `LOW`
- `NORMAL`
- `HIGH`
- `CRITICAL`

### 11.3 `NotificationChannel`

- `IN_APP`
- `WEB_SOCKET`
- `EMAIL`

后续预留：

- `SMS`
- `MOBILE_PUSH`

### 11.4 `NotificationInboxStatus`

建议不要单独存状态列，首版用时间戳表达：

- `read_at is null` -> 未读
- `read_at not null` -> 已读
- `archived_at not null` -> 已归档

### 11.5 `NotificationDispatchStatus`

- `PENDING`
- `RESERVED`
- `SENT`
- `ACKED`
- `FAILED`
- `DEAD_LETTER`
- `CANCELLED`
- `EXPIRED`

## 12. WebSocket 实时推送设计

### 12.1 选型建议

首版采用原生 JSON WebSocket，不强制引入 STOMP。

原因：

- 当前项目是模块化单体，通知实时需求聚焦单一主题
- 原生 JSON 协议更轻，便于前后端直接控制
- 不需要在首版额外引入 topic / broker 级复杂度

### 12.2 鉴权方式

不建议直接在 WebSocket URL 中长期暴露 JWT。

首版建议：

1. 前端先调用 HTTP 接口申请短期 `wsTicket`
2. 再使用 `wsTicket` 建立连接
3. 服务端校验 ticket 后绑定用户会话

建议接口：

- `POST /api/v1/notifications/ws-ticket`

返回：

- `ticket`
- `expiresAt`
- `wsUrl`

### 12.3 连接生命周期

客户端连接后：

1. 服务端返回 `WELCOME`
2. 客户端声明最后已确认的推送序号或最后同步时间
3. 服务端必要时返回 `SYNC_REQUIRED`
4. 正常推送通知增量
5. 客户端对收到的推送做 `ACK`
6. 心跳保活
7. 断线重连后执行差量补偿

### 12.4 服务端推送事件建议

- `WELCOME`
- `NOTIFICATION_CREATED`
- `UNREAD_COUNT_CHANGED`
- `SYNC_REQUIRED`
- `PING`

### 12.5 客户端上行事件建议

- `HELLO`
- `ACK`
- `PONG`
- `READ_SYNC`

### 12.6 WebSocket 与离线补偿关系

即使 WebSocket 连接失败：

- 通知仍已进入收件箱
- 客户端仍可通过 `/notifications` 与 `/notifications/unread-count` 补偿
- 重连后服务端应允许客户端做差量同步

## 13. 浏览器 Notification API 策略

浏览器弹窗只作为“前台提醒增强”，不替代站内收件箱。

首版建议规则：

- 默认只对 `HIGH` / `CRITICAL` 优先级显示系统弹窗
- 仅在以下条件同时满足时弹出：
  - 浏览器授权
  - 用户规则允许
  - 当前通知类型支持前台弹窗

首版建议支持弹窗的类型：

- `AI_RESUME_TASK_SUCCEEDED`
- `AI_RESUME_TASK_FAILED`
- `CONSULT_REPLIED`
- `CONSULT_REFUNDED`
- `BOUNTY_REVIEWED`
- `CERTIFICATION_APPROVED`
- `CERTIFICATION_REJECTED`

## 14. 邮箱通知设计

### 14.1 首版定位

邮箱通知首版只承载事务型通知，不承载营销消息。

### 14.2 渠道实现建议

项目当前注册链路已存在邮件基础设施，首版可复用同一邮件提供商能力。

建议邮箱通知由独立 dispatcher 处理：

- 读取投递任务
- 渲染邮件模板
- 调用邮件 provider
- 记录尝试与结果

### 14.3 首版推荐邮件通知类型

- `AI_RESUME_TASK_SUCCEEDED`
- `AI_RESUME_TASK_FAILED`
- `CONSULT_REFUNDED`
- `CERTIFICATION_APPROVED`
- `CERTIFICATION_REJECTED`
- `BOUNTY_REVIEWED`（仅当企业审核页显式勾选邮件提醒时）

### 14.4 首版不建议直接发邮件的类型

- `CONSULT_PAID`
- `BOUNTY_SUBMITTED`
- `CONSULT_CLOSED`

这类事件更适合站内 + WebSocket 实时提醒，不必加重邮箱打扰。

## 15. 通知规则设计

### 15.1 首版目标

首版规则不追求复杂编排，而是支持“按分类 + 按渠道”的基础开关。

### 15.2 首版最小规则能力

对每个用户、每个分类，支持以下维度：

- 是否允许 WebSocket 实时推送
- 是否允许浏览器弹窗
- 是否允许邮件通知
- 邮件仅通知高优先级，还是通知全部
- 安静时段

### 15.3 建议默认值

- `IN_APP`
  - 默认开启，且不建议普通用户关闭
- `WEB_SOCKET`
  - 默认开启
- `BROWSER_POPUP`
  - 默认关闭，需用户授权
- `EMAIL`
  - 对 `AI_TASK / CERTIFICATION / CONSULT` 默认开启高优先级通知
  - 对 `BOUNTY` 默认关闭或仅高优先级开启
  - 企业审核工作区当前已支持对单次 `BOUNTY_REVIEWED` 结果显式请求邮件入队；该请求仍会尊重学生自己的邮件开关

### 15.4 首版不做的规则能力

- 多条件 if-else 编排
- 用户自定义模板
- digest 订阅频率编排
- 节假日规则 / 工作日规则

## 16. 队列与生命周期管理

### 16.1 队列选型

首版建议采用数据库任务队列，不强制引入 MQ。

原因：

- 当前项目已在 AI 异步任务中使用数据库队列思路
- 单机模块化单体更适合先复用同类模式
- 易于答辩演示、审计与排障

### 16.2 执行模型

- 发布通知时：
  - 同事务写 `notification_events`
  - 写 `notifications`
  - 写 `notification_dispatch_jobs`
- worker 按 `PENDING -> RESERVED -> SENT / FAILED / DEAD_LETTER` 推进
- 失败采用指数退避

### 16.3 建议重试策略

- `WEB_SOCKET`
  - 更偏即时
  - 可尝试 1 到 2 次短退避
- `EMAIL`
  - 更偏最终送达
  - 可尝试 3 到 5 次长退避

### 16.4 离线语义

离线不意味着失败。

当用户离线时：

- 站内收件箱项已经存在
- WebSocket 投递可标记为失败或未确认
- 用户下次上线通过收件箱与未读数补偿获取

### 16.5 死信与治理

首版建议支持：

- 死信标记
- 最近错误码与错误信息留痕
- 后续后台治理页预留入口

首版不要求立刻建设完整后台治理页，但后端数据结构必须支持。

## 17. API 设计建议

### 17.1 继续保留并升级的接口

- `GET /api/v1/notifications`
- `GET /api/v1/notifications/unread-count`
- `POST /api/v1/notifications/{notificationId}/read`
- `POST /api/v1/notifications/read-all`

### 17.2 首版新增接口

- `POST /api/v1/notifications/ws-ticket`
- `GET /api/v1/notifications/preferences`
- `PUT /api/v1/notifications/preferences`

说明：

- `POST /api/v1/notifications/test-email` 当前仍保留为后续扩展位，首版真实实现尚未落地

### 17.3 列表接口建议增强字段

通知列表项建议返回：

- `id`
- `eventId`
- `category`
- `type`
- `title`
- `content`
- `priority`
- `read`
- `actionCode`
- `refType`
- `refId`
- `payload`
- `createdAt`
- `readAt`

### 17.4 未读数接口建议

首版至少返回：

- 总未读数

可选增强：

- 各分类未读数

例如：

- `consultUnreadCount`
- `aiTaskUnreadCount`
- `bountyUnreadCount`

## 18. 前端页面与交互要求

> 正式 UI / 信息架构 / 产品化文案基线以 [`39_NOTIFICATION_CENTER_UI_DESIGN_RULES.md`](docs/spec/39_NOTIFICATION_CENTER_UI_DESIGN_RULES.md) 为准；本章继续保留通知系统层面的能力要求、跳转语义与实时交互边界。

### 18.1 `/notifications` 页面定位

`/notifications` 是学生 / 导师 / 企业共享的通知中心，不是某个业务的子页面。

### 18.2 页面结构建议

建议固定包含：

1. 页面头部
2. 分类筛选区
3. 未读 / 全部切换
4. 通知列表区
5. 右侧详情预览区或详情抽屉
6. 通知设置入口

### 18.3 列表能力

- 按分类筛选
- 按未读筛选
- 单条已读
- 全部已读
- 稳定跳转
- 新通知即时插入

### 18.4 首版跳转规则

前端需内置动作解析器，例如：

- `VIEW_AI_RESUME_TASK_RESULT`
- `VIEW_AI_REVIEW_CENTER`
- `VIEW_CONSULT_ORDER`
- `VIEW_BOUNTY_TASK`
- `VIEW_CERTIFICATION_STATUS`

当前已落地的角色跳转规则补充：

- `VIEW_AI_REVIEW_CENTER`
  - 学生：优先跳转 `/ai/history`
  - 若 payload 携带 `sessionId`，前端应优先 deep-link 到 `/ai/history?type=interview&sessionId=...`
  - 若 payload 携带 `linkedRecordId`，前端应优先 deep-link 到 `/ai/history?type=resume&recordId=...`
  - 若 payload 仅携带异步 `taskId`，前端允许先跳 `/ai/history?type=resume&taskId=...`，由复盘中心自行轮询任务状态并收口到最终记录

- `VIEW_BOUNTY_TASK`
  - 企业：优先跳转 `/enterprise/tasks/:taskId`
  - 学生：优先跳转 `/bounty/:taskId`
  - 其他角色：若当前动作目标页未正式落地，允许先回 `/notifications` 或相关列表页补偿进入
- `VIEW_CERTIFICATION_STATUS`
  - 企业：优先跳转 `/enterprise/profile`
  - 导师：优先跳转 `/mentor/profile`
  - 学生：优先跳转 `/dashboard`

当目标深链尚未正式落地时：

- 允许优雅降级到相关列表页或工作台
- 不允许直接跳空白页或 404
- 若当前通知未携带足够的 `refId / payload` 目标信息，通知中心详情区 CTA 应显式禁用并给出兜底说明，不允许静默无响应。

### 18.5 页面入口建议

由于当前 C 端仍没有统一稳定的公共壳层，首版不要求“一次性改成全站统一导航铃铛”。

当前已经落地的首版入口策略：

- 在学生工作台、导师工作台与 AI 简历优化页顶栏启用真实通知铃铛
- 新增 `/notifications` 共享通知中心页作为统一承接入口
- 企业端当前仍沿用工作台壳层，待企业侧真实前端页面继续推进后再补统一入口

### 18.6 实时交互要求

- WS 在线时：
  - 未读数即时更新
  - 列表即时插入
  - 满足规则时触发浏览器弹窗
- WS 断开时：
  - 前端显示降级状态
  - 定时轮询未读数
  - 必要时重新同步通知列表

## 19. 与现有模块的集成要求

### 19.1 AI 异步任务

- `AiAsyncTaskService` 的关键状态推进后，应发布统一通知事件
- 不直接让前端只靠轮询任务接口判断结果

### 19.2 咨询域

- 咨询域现有直接创建通知的逻辑应逐步迁到统一发布接口
- 首版允许内部继续调用通知模块，但不建议各服务长期手写文案和类型字符串

### 19.3 悬赏域

- 当前已有 `BOUNTY_SUBMITTED / BOUNTY_REVIEWED` 事件，可直接映射到统一分类与动作码

### 19.4 认证审核

- 审核结果是典型事务型通知，适合首版正式接入

### 19.5 社区域

- 首版只保留接口兼容位与分类枚举位
- 不进入正式接入范围

## 20. 可靠性与安全要求

### 20.1 可靠性

- 通知入箱必须与业务事务保持一致性
- 渠道失败不影响站内入箱
- 重复发布需通过 `dedupeKey` 去重
- WS 断线必须允许补偿同步

### 20.2 安全

- WebSocket 连接不能长期直接暴露主 JWT
- 邮件渠道需遵循现有密钥管理规范
- 通知 payload 不得直接泄露敏感数据
- 浏览器弹窗文本应以摘要为主，不直接回显敏感字段

### 20.3 审计

必须可追溯：

- 哪个事件触发了通知
- 给谁发了哪些渠道
- 各渠道是否发送成功
- 失败原因是什么

## 21. 首版实施建议

建议按 4 步推进：

### 第 1 步：后端通知基座升级

- 升级 `notifications` 表
- 新增事件 / 投递任务 / 偏好表
- 统一发布接口

### 第 2 步：业务域接入

- 先接 AI 异步任务
- 再收口咨询 / 悬赏 / 认证

### 第 3 步：前端通知中心与 WS

- 新建 `/notifications`
- 接通 WS ticket + WS client
- 加入未读数轮询降级

### 第 4 步：邮箱与规则

- 接入邮件 dispatcher
- 落地基础偏好设置
- 完成端到端回归

### 21.1 当前进度写实（2026-03-24）

- 第 1 步：已落地
- 第 2 步：已落地 AI 简历异步任务、认证审核、系统公告，以及旧咨询链路兼容接入
- 第 3 步：已落地 `/notifications`、WS ticket + WS client、未读数同步与浏览器桌面提醒
- 第 4 步：邮件 dispatcher 与基础偏好已经落地，`test-email` 接口和治理后台页仍待后续扩展

## 22. 测试要求

首版至少新增以下测试：

### 22.1 后端集成测试

- 业务事件触发后成功入箱
- 相同 `dedupeKey` 不重复生成通知
- WS 投递任务正确生成
- 邮件任务正确生成
- 已读回写正确
- 规则关闭邮件后不生成邮件任务
- 用户离线时站内通知仍可查询

### 22.2 前端联调测试

- `/notifications` 正常展示
- WS 在线时未读数即时更新
- WS 断开时轮询补偿生效
- 浏览器弹窗权限流正常
- 点击通知可稳定跳转，企业任务通知优先进入 `/enterprise/tasks/:taskId`，企业认证提醒优先进入 `/enterprise/profile`

### 22.3 端到端测试

- 提交 AI 简历异步任务，任务成功后收到通知并跳转到结果页
- 导师回复咨询后学生收到实时通知
- 企业审核悬赏后学生收到通知
- 认证审核通过 / 驳回后用户收到通知

## 23. 完成定义（DoD）

满足以下条件，则可认定“平台级通知系统首版”完成：

1. 站内收件箱已从咨询域基础提醒升级为平台统一收件箱。
2. WebSocket 实时推送可在网页端稳定工作。
3. 离线用户重新进入系统后能看到未读通知并完成补偿同步。
4. AI 异步任务、咨询、悬赏、认证至少四类业务已正式接入。
5. 首版通知规则可控制 WebSocket / 浏览器弹窗 / 邮件渠道。
6. 邮箱通知已具备正式接入能力，并至少覆盖部分事务型通知。
7. 队列、重试、失败留痕、死信兼容位已落地。
8. 社区通知已按“有限正式接入”收口：回复 / 审核结果 / 举报结果可用，点赞等高频噪音类事件继续后置，系统边界仍清晰且文档口径一致。

## 24. 当前待同步文档

本文件新增后，后续应按实现节奏继续同步：

- `docs/spec/02_SRS.md`
- `docs/spec/03_ARCHITECTURE.md`
- `docs/spec/04_API_SPEC.md`
- `docs/spec/05_DB_SCHEMA.md`
- `docs/spec/08_TEST_PLAN.md`
- `docs/spec/10_MILESTONE_AND_BACKLOG.md`
- `docs/spec/12_AGENT_TASK_BREAKDOWN.md`
- `docs/spec/13_ACCEPTANCE_CHECKLIST.md`
- `docs/spec/14_FRONTEND_ROUTES_AND_PAGES.md`
- `docs/spec/15_FRONTEND_REFACTOR_CHECKLIST.md`

说明：

- `docs/spec/00_README_AGENT_START.md` 当前为 `frozen`，若后续需要把通知相关枚举正式纳入 SSOT，需在实现边界更稳定后单独裁决。
