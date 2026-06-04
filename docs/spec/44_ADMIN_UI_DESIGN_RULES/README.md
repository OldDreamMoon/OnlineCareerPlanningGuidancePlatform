# 管理员后台 UI 设计需求目录

## 设计总基线
- 设计对象为管理员后台 `/admin/*`，以当前真实代码和已落地前台业务为准。
- 前端技术栈：React 18 + TypeScript + Vite 5 + React Router 6。
- UI 架构必须以 Ant Design 5 为主，允许使用 `@ant-design/pro-components`；Tailwind CSS 4 只做布局与间距辅助，不替代 Ant Design 组件体系。
- 管理后台统一接入 `AdminShellLayout`：左侧导航、顶部管理员信息栏、右侧内容区。
- 主题基线：主色 `#6366f1`，成功 `#10b981`，警告 `#f59e0b`，错误 `#ef4444`，基础圆角 `8px`，主要卡片圆角以 `16px-24px` 为主。
- 当前验收基线为宽屏桌面端，建议以 `1440px` 画板输出，不要求移动端和平板设计稿。
- AI 相关页面当前执行新的双域分层：`AI 应用运营` 负责业务场景权益与计费策略，`AI 网关` 负责技术治理；`运行配置中心` 只保留运行快照与快捷直达。
- AI 固定业务场景清单以 `adminAiChannelCatalog` / `AiApplicationSceneRegistry` 为真相层，页面只维护这批已落地场景，不再把“新增业务场景”作为后台常规动作。
- 当前运营权益真相层已对 `COMMUNITY_REPLY` 的部分固定场景支持 `sceneCode` 精确配额；其余大多数能力仍以 `tier + taskType` 为主。若多个固定场景共享同一 `taskType`，运营台需显式标注“共享策略”或“随主会话计费”；若后续要求同一 `taskType` 下更多场景拥有不同免费额度或积分价格，需要继续扩展后端 quota schema，再放开为场景级独立策略。

## 文档清单
- [01_ADMIN_LOGIN_PANEL.md](./01_ADMIN_LOGIN_PANEL.md) `路由 /admin/login`
- [02_ADMIN_DASHBOARD.md](./02_ADMIN_DASHBOARD.md) `路由 /admin/dashboard`
- [03_ADMIN_USERS_LIST.md](./03_ADMIN_USERS_LIST.md) `路由 /admin/users`
- [04_ADMIN_USER_DETAIL.md](./04_ADMIN_USER_DETAIL.md) `路由 /admin/users/:userId`
- [05_ADMIN_CERT_REVIEW_LIST.md](./05_ADMIN_CERT_REVIEW_LIST.md) `路由 /admin/users/reviews`
- [06_ADMIN_CERT_REVIEW_DETAIL.md](./06_ADMIN_CERT_REVIEW_DETAIL.md) `路由 /admin/users/reviews/:userId`
- [07_ADMIN_ENTERPRISE_TASK_OPS.md](./07_ADMIN_ENTERPRISE_TASK_OPS.md) `路由 /admin/enterprise/tasks`
- [08_ADMIN_MENTOR_OPS.md](./08_ADMIN_MENTOR_OPS.md) `路由 /admin/mentors/operations`
- [09_ADMIN_NOTIFICATIONS.md](./09_ADMIN_NOTIFICATIONS.md) `路由 /admin/notifications`
- [10_ADMIN_AI_APPLICATIONS.md](./10_ADMIN_AI_APPLICATIONS.md) `路由 /admin/ai/applications`
- [11_ADMIN_AI_GATEWAY_MAIN.md](./11_ADMIN_AI_GATEWAY_MAIN.md) `路由 /admin/ai/gateway`
- [12_ADMIN_AI_GATEWAY_PROVIDERS.md](./12_ADMIN_AI_GATEWAY_PROVIDERS.md) `子路由 /admin/ai/gateway?tab=providers`
- [13_ADMIN_AI_GATEWAY_ROUTES.md](./13_ADMIN_AI_GATEWAY_ROUTES.md) `子路由 /admin/ai/gateway?tab=routes`
- [14_ADMIN_AI_GATEWAY_TEMPLATES.md](./14_ADMIN_AI_GATEWAY_TEMPLATES.md) `子路由 /admin/ai/gateway?tab=templates`
- [15_ADMIN_AI_GATEWAY_RUNTIME.md](./15_ADMIN_AI_GATEWAY_RUNTIME.md) `兼容说明：原 runtime tab 已并入 /admin/runtime?tab=ai-channels`
- [16_ADMIN_AI_GATEWAY_LOGS.md](./16_ADMIN_AI_GATEWAY_LOGS.md) `子路由 /admin/ai/gateway?tab=logs`
- [17_ADMIN_CONTENT_MAIN.md](./17_ADMIN_CONTENT_MAIN.md) `路由 /admin/content`
- [18_ADMIN_CONTENT_REPORTS.md](./18_ADMIN_CONTENT_REPORTS.md) `子路由 /admin/content?tab=reports`
- [19_ADMIN_CONTENT_REVIEW_QUEUE.md](./19_ADMIN_CONTENT_REVIEW_QUEUE.md) `子路由 /admin/content?tab=review`
- [20_ADMIN_CONTENT_AUDIT_LOGS.md](./20_ADMIN_CONTENT_AUDIT_LOGS.md) `子路由 /admin/content?tab=audit`
- [21_ADMIN_CONTENT_SENSITIVE_TERMS.md](./21_ADMIN_CONTENT_SENSITIVE_TERMS.md) `子路由 /admin/content?tab=settings`
- [22_ADMIN_ORDERS_MAIN.md](./22_ADMIN_ORDERS_MAIN.md) `路由 /admin/consult/orders`
- [23_ADMIN_ORDERS_AFTER_SALES.md](./23_ADMIN_ORDERS_AFTER_SALES.md) `子路由 /admin/consult/orders?tab=after-sales`
- [24_ADMIN_ORDERS_RECONCILIATION.md](./24_ADMIN_ORDERS_RECONCILIATION.md) `子路由 /admin/consult/orders?tab=reconciliation`
- [25_ADMIN_SKILLS_OPS.md](./25_ADMIN_SKILLS_OPS.md) `路由 /admin/skills`
- [26_ADMIN_RUNTIME_MAIN.md](./26_ADMIN_RUNTIME_MAIN.md) `路由 /admin/runtime`
- [27_ADMIN_RUNTIME_BUSINESS.md](./27_ADMIN_RUNTIME_BUSINESS.md) `子路由 /admin/runtime?tab=business`
- [28_ADMIN_RUNTIME_OPERATIONS.md](./28_ADMIN_RUNTIME_OPERATIONS.md) `子路由 /admin/runtime?tab=operations`
- [29_ADMIN_RUNTIME_AI_CHANNELS.md](./29_ADMIN_RUNTIME_AI_CHANNELS.md) `子路由 /admin/runtime?tab=ai-channels`
- [30_ADMIN_RUNTIME_DEGRADE.md](./30_ADMIN_RUNTIME_DEGRADE.md) `子路由 /admin/runtime?tab=degrade`
- [31_ADMIN_MENTOR_OPS_DETAIL_MODAL.md](./31_ADMIN_MENTOR_OPS_DETAIL_MODAL.md) `详情层 /admin/mentors/operations 内导师经营详情弹窗`
- [32_ADMIN_SKILLS_PREVIEW.md](./32_ADMIN_SKILLS_PREVIEW.md) `路由 /admin/skills/preview，复用学生星图骨架的管理员预览说明`

## 重定向别名
- `/admin/orders` 与 `/admin/payments` 重定向到 `/admin/consult/orders`
- `/admin/ai-gateway` 重定向到 `/admin/ai/gateway`
- `/admin/feature-flags`、`/admin/system/settings`、`/admin/settings` 重定向到 `/admin/runtime`
