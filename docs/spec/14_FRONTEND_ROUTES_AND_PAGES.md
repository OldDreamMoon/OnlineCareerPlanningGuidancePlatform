# 前端路由与页面清单（中文）

> **文档状态**：`evolving` · 最后审核：2026-04-16 · 当前已进入“前后端主链路人工测试收口、角色路由守卫集中化与文档回写”阶段。

## 1. 目标
- 定义 v1 前端页面、角色路由与守卫边界。
- 让前后端开发在没有高保真设计稿时仍能稳定推进联调。
- 为答辩演示保留清晰的角色入口与闭环页面。

## 2. 技术选型
- 框架：React 18 + TypeScript
- 构建工具：Vite
- 路由：React Router v6
- 全局状态：React Context（按需补 zustand）
- HTTP：Fetch + 登录态封装
- C 端样式：以 Tailwind CSS + Framer Motion 为当前正式基线，按 shadcn/ui 的组件风格逐步沉淀学生 / 导师 / 企业 / Landing 页面
- B 端样式：管理员后台固定采用 Ant Design 5 + ProComponents，并通过独立 `adminTheme.css` 与根类作用域做样式隔离，避免污染 C 端页面

## 3. 路由守卫策略
- 未登录访问受保护路由：统一由 `RequireAuthenticatedRoute` 重定向到 `/auth?mode=login`，并保留完整 `pathname + search + hash` 作为登录后回跳依据。
- `/dashboard` 不再承载学生真实页面，而是统一工作台入口；登录后会按当前角色分流到 `/student/dashboard`、`/mentor/dashboard`、`/enterprise/dashboard` 或 `/admin/dashboard`。
- 学生 / 导师 / 企业正式页统一由 `RequireRoleRoute` 在路由层做角色守卫，不再由各页面在组件顶层各自处理 `Navigate("/403")`。
- 角色不匹配时分两类处理：
  - `redirect`：误入其他角色的私有工作台域、资料域、订单/任务中心等路径时，直接回当前角色的真实工作台或更贴近的业务页。
  - `forbidden`：访问明确受限功能时统一进入 `/403`，例如导师访问学生 AI 功能、导师广场、咨询创建/详情，或企业访问社区域。
- `/403` 登录态主按钮统一为“返回我的工作台”；守卫可传入 `attemptedPath / fallbackPath`，用于展示实际被拦截路径并回退到合理入口。
- Token 过期：自动尝试 `/auth/refresh`，失败后清空会话并回到登录页
- 守卫判断口径：JWT 中的 `role`
- 管理员继续使用独立 `RequireAdminRoute`，不并入普通 C 端共享守卫。

## 4. 路由表
### 4.1 公开路由
| 路由 | 页面 | 说明 |
|------|------|------|
| `/` | Landing Page | 公共首页，展示产品价值、角色入口与演示路径 |
| `/auth` | 统一认证页 | 统一承载登录 / 注册切换，视觉基线对齐 `apps/web/templates/auth.jsx`；注册先收集昵称、邮箱、密码与邮箱验证码；“发送验证码”按钮会先通过 `GET /auth/captcha/config` 读取 Geetest 公开配置，动态加载 Geetest v4 `bind` 模式脚本，完成 `POST /auth/captcha/geetest/verify` 后再调用 `POST /auth/email/send-code`；用户输入验证码后，点击“继续下一步”会调用 `POST /auth/email/verify-code` 换取短时邮箱验证凭证，并在当前认证卡片内以上滑面板展开角色选择与资料补充，不再跳转独立第二页；登录面板中的“忘记密码”已接入独立弹层，顺序调用 `POST /auth/password/reset/send-code`、`POST /auth/password/reset/verify-code` 与 `POST /auth/password/reset/change` 完成找回；学生接 `/auth/register + /profiles/students/me`，导师 / 企业直接走 `/auth/register-with-certification` 提交正式认证资料；管理员登录也在同一视觉壳层内以上滑面板呈现 |
| `/auth/step2` | 旧注册第二页兼容路由 | 不再承担正式注册主入口；当前仅用于兼容旧链接，未完成注册时重定向到 `/auth?mode=register&panel=register-details`，已登录用户则回到各自工作台 |
| `/login` | 登录兼容路由 | 重定向到 `/auth?mode=login`，用于兼容旧入口与守卫跳转 |
| `/register` | 注册兼容路由 | 重定向到 `/auth?mode=register`，并保留现有 query 参数用于注册资料阶段角色预选 |
| `/admin/login` | 管理员登录兼容路由 | 重定向到 `/auth?mode=login&panel=admin-login`，保留守卫与旧入口兼容能力 |
| `/403` | 无权限页 | 角色越权兜底 |

### 4.2 共享认证路由
| 路由 | 页面 | 说明 |
|------|------|------|
| `/dashboard` | 工作台入口分流页 | 统一工作台入口；登录后按角色分流到真实工作台，不再作为学生真实页面使用 |
| `/students/:studentUserId` | 学生公开空间 | 社区与贡献榜的人物入口；本人访问时走公开预览，其他用户按公开资料裁切展示 |
| `/notifications` | 通知中心 | 统一通知收件箱、实时状态、全部已读与分类偏好入口；学生 / 导师 / 企业复用同一页面 |

### 4.3 学生路由（STUDENT）
| 路由 | 页面 | 优先级 | 说明 |
|------|------|--------|------|
| `/student/dashboard` | 学生控制台 | P0 | 学生端真实工作台，吸收成长首页职责，展示签到日历 / 连签奖励 / 每日任务 / 积分摘要、画像概览、继续事项工作区与 AI / 技能 / 导师 / 订单 / 悬赏快捷入口；继续事项当前已真实接入未读通知、最新 AI 复盘与未完成咨询订单 |
| `/ai/resume` | 简历优化 | P0 | AI 简历优化；当前已切到异步任务提交，结果同时可从统一通知中心与顶栏通知铃铛补偿进入；主历史入口已优先收口到 `/ai/history`，页内历史抽屉仅保留为兼容速览 |
| `/ai/interview` | AI 模拟面试 | P0 | 岗位选择、`text / voice / live` 三模式、转写追问、AI 点评与结果页；当前已接通消息追问 TTS、报告页“朗读本轮总结”，并新增基于独立 Python WebSocket 的 Gemini Live 测试模式，结束后会把 transcript 回灌到正式复盘链路 |
| `/ai/history` | AI 复盘中心 | P1 | 独立承接简历复盘与模拟面试复盘的历史回看、详情工作区与跨模块回流；已正式落地顶部概览 + 左侧历史列表 + 右侧详情工作区，并支持 `?type=resume&recordId=...`、`?type=resume&taskId=...`、`?type=interview&sessionId=...` 深链接选中与通知回流；简历报告导出当前已切为基于历史详情数据的浏览器侧标准 PDF 渲染，不再依赖旧的打印预览链路 |
| `/profile` | 账号与资料中心 | P0 | 基于 `apps/web/templates/student profile.jsx` 落地的学生资料中心：包含求职名片、AI 画像、隐私矩阵、邮箱换绑与密码修改四个 Tab；hero 右侧独立账号概览卡已移除，账号身份并入左侧主卡；接入 `GET/PUT /profiles/students/me`、`POST/GET /profiles/students/me/avatar`、`PUT /profiles/students/me/privacy` 与资料中心安全验证码接口，支持头像上传 / 替换 / 裁剪，头像经前端裁剪压缩与后端二次压缩后写入 MinIO，并按 VIP / 普通用户展示不同身份徽标；求职名片与学生注册资料现已同步采集学校名称，外部主页区也已从固定 `GitHub + 个人网站` 升级为受控平台下拉列表，可配置 GitHub、个人站及常见国内平台账号，隐私矩阵继续用统一 `social` 行控制该区域展示；隐私矩阵现同时提供“同校学生”和“平台学生”两个学生角色维度 |
| `/students/:studentUserId` | 学生公开空间 | P1 | 学生在平台内的公开人物页，一期前端优先承接社区与贡献榜中的作者入口；本人访问时会基于 `GET /profiles/students/me` 与隐私矩阵形成公开预览，其他学生访问时优先展示社区帖子、评论与贡献榜可拿到的公开线索，不等待专门公共接口才开始落页；正式设计以 `docs/spec/38_STUDENT_PUBLIC_PROFILE_UI_DESIGN_RULES.md` 为准 |
| `/skills` | 技能星图 | P0 | 星图式沉浸技能树；Phase 1 已落地真实技能树、节点详情、状态更新、失败提示与移动端纵向降级，当前阶段人工审查以宽屏桌面端为准，后续继续按专项文档推进主攻目标持久化、资源与定向 AI |
| `/growth` | 成长明细页（规划位） | P2 | 当前首页职责已并入 `/student/dashboard`；若后续签到 / 任务 / 积分需要独立深页，再展开为独立路由 |
| `/mentors` | 导师广场 | P1 | 学生端导师发现与决策页：正式需求以 `docs/spec/21_MENTOR_MARKETPLACE_UI_DESIGN_RULES.md` 为准，负责 AI 推荐、问题场景筛选、收藏、快速预览与咨询准备单 |
| `/mentors/:mentorUserId` | 导师详情兼容深链 | P1 | 当前不单独维护导师长页；该路由已作为兼容深链入口落地，并统一收口到 `/mentors?mentor=...` 的 master-detail 选中态，正式详情承接仍在 `/mentors` 当前设计中完成 |
| `/consult/create` | 创建咨询订单 | P1 | 学生端正式订单创建页：承接导师广场准备单，负责导师确认、结构化问题填写、咨询材料包、多文件上传、规则确认与创建订单；当前已接通 `/profiles/students/me` 的目标岗位与画像摘要带入，并改为页内编辑“建议准备材料”；创建成功后会真实跳转 `/consult/orders/:orderNo`，若材料上传部分失败则在详情页继续提醒补传；正式设计以 `docs/spec/25_STUDENT_CONSULT_ORDER_CREATE_UI_DESIGN_RULES.md` 为准 |
| `/consult/orders` | 我的订单 | P1 | 学生端真实订单列表页：已接通 `/consult/orders`，支持状态筛选、分页与回流到 `/consult/orders/:orderNo`；工作台入口、创单页与详情页都已向该页收口 |
| `/consult/orders/:orderNo` | 订单详情 | P1 | 学生端真实订单详情页：已接通支付、沙箱支付状态查询、沙箱支付单关闭、消息线程、材料补传/删除、预约时间、评价、售后申请与售后历史，并展示导师回复 SLA 截止时间与超时自动退款提示 |
| `/community` | 社区首页 / 互助工作台 | P0 | 社区模块主入口：按统一模块工作台组织，负责关键词 / 场景 / 标签筛选、帖子内容流、快速发帖、贡献榜预览与社区通知摘要；发帖通过页内工作台完成，不单独拆 `/community/create`；正式设计以 `docs/spec/27_COMMUNITY_SYSTEM_UI_DESIGN_RULES.md` 为准 |
| `/community/:postId` | 帖子详情 / 讨论工作区 | P0 | 渲染 Markdown 正文，展示评论流、AI 辅助卡、回复编辑区、举报入口与社区通知回流承接；回复通过页内编辑区完成，不单独拆回复页；正式设计以 `docs/spec/27_COMMUNITY_SYSTEM_UI_DESIGN_RULES.md` 为准 |
| `/community/reports` | 举报与治理反馈 | P1 | 社区治理反馈中心，承接我的举报记录、结果状态与从帖子详情 / 通知带入的预填举报信息；正式设计以 `docs/spec/27_COMMUNITY_SYSTEM_UI_DESIGN_RULES.md` 为准 |
| `/community/leaderboard` | 社区贡献榜 | P1 | 最近 7 天社区贡献榜，负责公式说明、榜单展示、个人定位与回跳社区 / 画像；正式设计以 `docs/spec/27_COMMUNITY_SYSTEM_UI_DESIGN_RULES.md` 为准 |
| `/bounty` | 悬赏任务列表 | P1 | 学生端真实企业实战任务列表页：支持关键词 / 状态筛选、分页浏览与从通知 / 工作台稳定回流 |
| `/bounty/:taskId` | 悬赏详情/提交 | P1 | 学生端真实任务详情与成果提交页：查看任务说明、提交文本 / 链接成果，并回看个人审核状态与结果说明 |

### 4.4 导师路由（MENTOR）
| 路由 | 页面 | 优先级 | 说明 |
|------|------|--------|------|
| `/mentor/dashboard` | 导师工作台 | P1 | 导师登录后的日常分诊台，负责认证状态、待处理订单、排期状态摘要、经营摘要与快捷入口；正式设计以 `docs/spec/19_MENTOR_DASHBOARD_UI_DESIGN_RULES.md` 为准 |
| `/mentor/profile` | 导师资料页 | P1 | 导师公开服务名片编辑台 + 学生侧预览台 + 认证材料中心 + 排期管理页；当前已真实接通头像上传、昵称编辑、真名展示开关、多套餐、密码修改弹层与通知 Tab；正式设计以 `docs/spec/20_MENTOR_PROFILE_UI_DESIGN_RULES.md` 为准 |
| `/mentor/orders` | 导师订单中心 | P1 | 导师订单中心主工作台，负责搜索筛选、优先级分诊、SLA / 风险识别、订单列表、详情弹窗与进入履约区；当前已接通 `GET /api/v1/consult/orders/mentor-workbench` 服务端聚合查询；正式设计以 `docs/spec/23_MENTOR_ORDER_WORKBENCH_UI_DESIGN_RULES.md` 为准 |
| `/mentor/orders/:orderNo/workspace` | 订单履约工作区 | P1 | 单笔咨询的独立履约页，负责完整消息线程、正式回复编辑、必要上下文、附件预览、评价与售后记录；当前已接通 AI 回复草稿与工作区快照静默回刷；正式设计以 `docs/spec/24_MENTOR_ORDER_FULFILLMENT_WORKSPACE_UI_DESIGN_RULES.md` 为准 |
| `/mentor/finance` | 导师财务中心 | P1 | 导师经营与账单中心，负责财务概览、账单明细、退款影响、导出与模拟提现管理；当前已接通 `GET /api/v1/mentor/finance/overview` 与提现持久化接口；正式设计以 `docs/spec/22_MENTOR_FINANCE_CENTER_UI_DESIGN_RULES.md` 为准 |
| `/community` | 社区广场（复用） | P0 | 与学生共用的社区模块；导师可浏览、发帖、回帖与回看讨论 |
| `/community/:postId` | 帖子详情（复用） | P0 | 与学生共用的讨论工作区 |
| `/community/reports` | 举报与治理反馈（复用） | P1 | 举报记录查询 |
| `/community/leaderboard` | 社区贡献榜（复用） | P1 | 查看榜单、回看社区贡献口径 |

### 4.5 企业路由（ENTERPRISE）
| 路由 | 页面 | 优先级 | 说明 |
|------|------|--------|------|
| `/enterprise/dashboard` | 企业工作台 | P1 | 企业登录后的轻量任务运营台；当前真实结构为 `Hero + 统计卡 + 快捷操作 + 左侧待处理任务 + 右侧业务提醒/处理记录 + 任务详情抽屉`，其中业务提醒与处理记录已收口为轮播卡；正式设计以 `docs/spec/32_ENTERPRISE_WORKSPACE_UI_DESIGN_RULES.md` 为准 |
| `/enterprise/profile` | 企业资料与认证 | P1 | 企业身份资料、认证状态与通知提醒的统一维护入口；当前已收口为页内 `profile / certification / notifications` 三个板块，支持 Logo、密码修改弹窗与认证附件查看；正式设计以 `docs/spec/33_ENTERPRISE_PROFILE_AND_CERTIFICATION_UI_DESIGN_RULES.md` 为准 |
| `/enterprise/tasks` | 企业任务中心 | P1 | 企业多任务管理页；负责关键词搜索、状态/提交筛选、截止风险识别、关闭/重开、任务快照与进入审核工作区；当前已接入 `GET /api/v1/bounty/enterprise/task-center` 聚合接口；正式设计以 `docs/spec/35_ENTERPRISE_TASK_CENTER_UI_DESIGN_RULES.md` 为准 |
| `/enterprise/tasks/create` | 企业任务发布页 | P1 | 发布或编辑企业任务；负责任务背景、交付要求、奖励说明、截止时间与发布前预览；正式设计以 `docs/spec/34_ENTERPRISE_TASK_CREATE_UI_DESIGN_RULES.md` 为准 |
| `/enterprise/tasks/:taskId` | 企业任务审核工作区 | P1 | 单任务审核工作区；当前真实结构为 `顶部任务摘要 + 左侧提交队列 + 右侧连续阅读区 + 任务要求弹窗 + 联系方式/处理记录抽屉 + 结果动作弹窗`，支持关键词、状态与贡献分筛选、继续接触与未入选通知；正式设计以 `docs/spec/36_ENTERPRISE_TASK_REVIEW_WORKSPACE_UI_DESIGN_RULES.md` 为准 |
| `/enterprise/bounty/create` | 发布任务兼容路由 | P2 | 兼容旧入口，当前前端已重定向到 `/enterprise/tasks/create` |
| `/bounty/:taskId` | 任务详情兼容 / 学生侧详情 | P2 | 学生侧继续承接公开任务详情与提交流程；企业正式审核主工作区收口到 `/enterprise/tasks/:taskId`，必要时兼容跳转 |
| `/notifications` | 通知中心（复用） | P1 | 复用统一通知中心查看企业任务、继续接触、未入选与认证提醒；企业任务通知优先 deep-link 到 `/enterprise/tasks/:taskId`，企业认证提醒优先 deep-link 到 `/enterprise/profile` 的认证状态板块，无法直达时再回 `/notifications` |

### 4.6 管理员路由（ADMIN）
| 路由 | 页面 | 优先级 | 说明 |
|------|------|--------|------|
| `/admin/dashboard` | 管理首页 | P0 | 系统概览、运营数据看板、治理摘要与后台快捷入口 |
| `/admin/users` | 用户管理 | P1 | 用户列表、筛选检索与详情入口 |
| `/admin/users/:userId` | 用户详情 | P1 | 用户资料、状态切换、密码重置与积分补发 |
| `/admin/users/reviews` | 用户认证审核工作区 | P1 | 统一处理导师 / 企业当前有效认证提交，支持筛选、查看材料、审核备注与审核动作 |
| `/admin/users/reviews/:userId` | 用户认证审核详情 | P1 | 查看指定用户当前版本、历史版本链路与附件预览 |
| `/admin/consult/orders` | 订单售后 | P1 | 搜索订单、查看待处理售后申请、识别导师超时自动退款结果，并执行退款或审批退款申请 |
| `/admin/consult/orders/:orderNo` | 订单售后详情 | P1 | 查看指定订单售后详情、审核售后申请历史、支付联动信息与导师回复 SLA 截止时间 |
| `/admin/payments/reconciliation` | 支付对账 | P1 | 平台内对账、异常单筛查与人工处理 |
| `/admin/payments/reconciliation/:orderNo` | 支付对账详情 | P1 | 查看指定订单对账详情、支付流水与人工处理结果 |
| `/admin/ai/gateway` | AI 网关后台 | P1 | 管理 provider、route、提示词模板、配额、日志、成本与运行时设置 |
| `/admin/feature-flags` | 功能开关 | P1 | 管理支付、语音、社区 AI 预答等运行时开关 |
| `/admin/content` | 内容治理 | P1 | 举报中心、待审队列、审计日志与敏感词管理的统一工作区 |
| `/admin/skills` | 技能资源治理台 | P1 | 技能节点与资源条目的后台 CMS 工作台，负责搜索筛选、节点详情、资源详情、节点/资源创建编辑与星图结构验证入口 |
| `/admin/skills/preview` | 管理员技能星图预览页 | P1 | 管理员专用只读星图结构预览页；复用星图画布能力，但默认隐藏学生成长 HUD、AI 行动按钮与进度修改，仅用于节点位置、层级、路径与资源内容校对 |
| `/admin/content/reports` | 举报中心 | P1 | 举报列表、处理历史与处置入口 |
| `/admin/content/review-queue` | 待审队列 | P1 | 查看待审内容、详情与人工处置 |
| `/admin/content/audit-logs` | 内容治理审计日志 | P1 | 按 `targetType/targetId/actionType/traceId` 追踪治理处置链路 |
| `/admin/content/moderation/policies` | 治理策略 | P1 | 查看与更新 AI 输入/输出、社区严格审查等策略 |
| `/admin/content/sensitive-terms` | 敏感词库 | P1 | 敏感词列表、新增、编辑、批量启停/删除、类型筛选与 CSV 导入导出 |

## 5. 当前页面落地状态
### 5.1 已落地页面
- Landing 首页、统一认证页（含注册资料上滑面板与管理员登录上滑面板）、403、404
- 共享认证页：`/dashboard` 现已正式收口为统一工作台入口页，负责按角色分流到真实工作台；`/notifications` 与 `/students/:studentUserId` 作为跨角色共享页继续保留
- 学生：`/student/dashboard`、AI（简历优化 + 语音/文本模拟面试）、`/bounty` + `/bounty/:taskId` 企业实战任务链路、`/mentors` 导师广场与 `/mentors/:mentorUserId` 兼容深链、`/consult/create` 正式订单创建页、`/consult/orders` 真实订单列表页与 `/consult/orders/:orderNo` 真实订单详情页；`/growth` 首页职责已并入 `/student/dashboard`
- 学生：`/profile` 作为账号与资料中心、`/students/:studentUserId` 作为社区与贡献榜中的公开人物页、`/skills` 作为沉浸式技能星图；其中 `/students/:studentUserId` 当前优先以“前端先落页 + 社区公开线索缓存”方式形成一期最小可读版本，后续再评估是否需要独立模板重构与公共读取接口；`/skills` 已完成 Phase 1 真实接线，后续继续按专项文档推进 Phase 2+
- 社区：当前路由职责、需求边界与 UI 设计已在 [`27_COMMUNITY_SYSTEM_UI_DESIGN_RULES.md`](docs/spec/27_COMMUNITY_SYSTEM_UI_DESIGN_RULES.md) 收口为“统一模块 + 4 个正式路由 + 发帖/回帖页内完成”的方案；真实页面仍待按专项文档落地
- 通知：`/notifications` 已形成真实统一通知中心；`apps/web/src/main.tsx` 已在应用根部挂载 `NotificationCenterProvider`，当前学生 / 导师 / 企业顶栏通知铃铛均已接通未读数、WebSocket 实时推送与浏览器桌面提醒
- `/profile` 本轮已完成第一波桌面端样式与头像能力收口：hero 右侧独立账号概览卡已移除，账号身份与 VIP / 普通用户徽标并入左侧主卡；新增头像上传 / 替换 / 裁剪组件，头像经前端 1:1 裁剪压缩与后端二次压缩后写入 MinIO；中部 Tab 导航已按模板居中收口。此前第二层工程收口仍保持有效：`selfIntro` 字数上限回归模板的 200 字、`realName` 支持清空、资料中心安全验证码改为后端持久化保存，开发态验证码也从 Toast 暴露改为 modal 内联提示。
- 导师：`/mentor/dashboard`、`/mentor/profile`、`/mentor/orders`、`/mentor/orders/:orderNo/workspace` 与 `/mentor/finance` 已形成真实可演示页面；其中 `/mentor/dashboard` 已从页内锚点式摘要页收口为跨页分诊台，并采用“旧快照先渲染 + 后台静默刷新”；`/mentor/profile` 已真实接通资料编辑、学生侧预览、认证材料、排期管理、头像上传、昵称编辑、真名展示开关、多套餐与密码修改弹层；`/mentor/orders` 已切到 `GET /api/v1/consult/orders/mentor-workbench` 服务端聚合查询，支持筛选、排序、风险识别、详情快照弹窗与进入履约区；`/mentor/orders/:orderNo/workspace` 已正式接入结构化问题、准备单、附件、评价、售后与 AI 回复草稿，并在发送成功后即时写回本地工作区快照；`/mentor/finance` 已切到 `GET /api/v1/mentor/finance/overview`，账单按服务端分页返回，提现记录与状态流转已持久化。导师端正式职责继续按 `/mentor/dashboard` 工作台、`/mentor/profile` 资料页、`/mentor/orders` 订单中心主工作台、`/mentor/orders/:orderNo/workspace` 独立履约工作区与 `/mentor/finance` 财务中心拆分，其中订单中心、履约区与财务中心专项规则分别以 `docs/spec/23_MENTOR_ORDER_WORKBENCH_UI_DESIGN_RULES.md`、`docs/spec/24_MENTOR_ORDER_FULFILLMENT_WORKSPACE_UI_DESIGN_RULES.md`、`docs/spec/22_MENTOR_FINANCE_CENTER_UI_DESIGN_RULES.md` 为准
- 企业：`/enterprise/dashboard`、`/enterprise/profile`、`/enterprise/tasks`、`/enterprise/tasks/create`、`/enterprise/tasks/:taskId` 已形成真实可演示页面，并与 `/notifications` 建立企业任务与认证提醒的真实回流；其中企业工作台、资料页、任务中心、发布页与审核工作区当前都已适配“旧快照先渲染 + 后台静默刷新”的工作区模式；企业端定位仍为轻量任务运营台，不扩张为完整 ATS / 实时聊天系统；真实任务验证、线下面试与录用手续继续在平台外完成，平台内只承接结果通知与一次轻量继续接触闭环
- 管理员：控制台（含运营数据看板）、用户、订单售后、支付对账、AI 网关后台、功能开关、内容治理（举报 / 待审 / 审计 / 策略 / 词库）
- 路由层：学生 / 导师 / 企业正式页当前已统一改为路由层共享守卫处理；私有域误入优先自动重定向回本角色工作台或贴近业务页，明确受限功能再统一进入 `/403`

补充说明（2026-03-19）：
- 学生端当前稳定开放的 C 端主路由已扩展为 `/student/dashboard`、`/profile`、`/skills`、`/ai/resume`、`/ai/interview`、`/mentors`、`/mentors/:mentorUserId`（兼容深链）、`/bounty`、`/bounty/:taskId`、`/consult/create`、`/consult/orders`、`/consult/orders/:orderNo`；其中 `/dashboard` 已收口为共享工作台入口。
- 导师详情当前正式采用 `/mentors` 内的 master-detail 承接：`/mentors/:mentorUserId` 只作为兼容深链接入口，把目标导师收口到 `/mentors?mentor=...` 的选中态，不再单独维护独立长页。
- `/ai/history` 已正式接入真实页面代码：当前由 `AiReviewCenterPage.tsx` 承接 AI 复盘中心，并与 `/ai/resume`、`/ai/interview`、`/notifications` 和学生工作台形成回流闭环；专项视觉规则仍以 `docs/spec/37_AI_REVIEW_CENTER_UI_DESIGN_RULES.md` 为准。
- `/student/dashboard` 已贴近 `apps/web/templates/student dashboard2.jsx`：在保留 hero / 顶栏的同时，接通 `GET /growth/checkin/overview`，补齐签到日历 modal、连签奖励进度、Hero 区“成长计划第 N 天”、居中奖励弹窗、任务公告板、更简洁的画像快照，以及新的“继续事项”工作区。
- `/skills` 已按 `apps/web/templates/skills.jsx` 的星图骨架完成 Phase 1：新增真实路由 [`/skills`](apps/web/src/pages/SkillsPage.tsx)，桌面端保留拖拽缩放星图、节点连线、右侧详情与 HUD，移动端保留纵向降级路径；并已接通 `GET /skills/tree`、`POST /skills/progress` 与失败提示，不再是单纯规划位。当前阶段人工审查与修正仍以宽屏桌面端为准。
- `/growth` 的首页级职责已收口到 `/student/dashboard`；是否继续保留独立深页，以后续签到 / 任务 / 积分明细需求为准。

### 5.2 管理员接口覆盖状态
- `/admin/dashboard`：已接入 `/admin/dashboard/operations`，并联动 `/admin/content/*` 摘要接口与 `/admin/growth/points/grant`。
- `/admin/users`：已接入 `/admin/users`、`/admin/users/{userId}`、`/admin/users/{userId}/status`、`/admin/users/{userId}/reset-password` 与 `/admin/growth/points/grant`；认证审核主入口已改为独立工作区 `/admin/users/reviews`，旧 `/admin/users/{userId}/approval-status` 仅保留兼容，不再作为前端主链路。
- `/admin/users/reviews`：已接入 `/admin/users/certification-reviews`、`/admin/users/{userId}/certification-review`、`/admin/users/{userId}/certification-review`（POST）以及 `/certification/assets/{assetId}/content`。
- `/admin/consult/orders`：已接入 `/admin/consult/orders`、`/admin/consult/orders/{orderNo}`、`/admin/consult/orders/{orderNo}/refund`、`/admin/consult/orders/{orderNo}/payment/query`、`/admin/consult/orders/{orderNo}/payment/close`、`/admin/consult/orders/{orderNo}/payment/refund-query`、`/admin/consult/after-sales/requests` 与 `/admin/consult/after-sales/requests/{requestId}/review`。
- `/admin/payments/reconciliation`：已接入 `/admin/payments/reconciliation`、`/admin/payments/reconciliation/{orderNo}` 与 `/admin/payments/reconciliation/{orderNo}/handle`。
- `/admin/ai/gateway`：已接入 `/admin/ai/meta`、provider / route / prompt-template 增改、模板发布 / 回滚 / 渲染预览、运行设置、日志、配额、成本看板 / 导出与路由命中预览。
- `/admin/skills`：已接入 `/admin/skills/overview`、`/admin/skills/nodes`、`/admin/skills/resources`、节点/资源增改，以及管理员只读预览接口 `/admin/skills/preview-tree`。
- `/admin/content/*`：已接入举报列表 / 处理历史 / 处置、待审队列 / 详情 / 处置、审计日志、治理策略查询 / 更新、敏感词查询 / 新增 / 删除。
- `/admin/feature-flags`：已接入 `/admin/feature-flags` 的查询与更新。

### 5.3 悬赏 / 企业任务页面说明
- `/bounty`：已形成学生真实任务列表页，负责关键词 / 状态筛选、分页浏览与进入详情
- `/bounty/:taskId`：
  - 学生视角：查看详情、提交成果、查看个人审核状态
  - 企业视角：保留兼容跳转语义；正式审核主工作区收口到 `/enterprise/tasks/:taskId`
- `/enterprise/dashboard`：已形成轻量企业工作台，当前真实结构为 Hero、统计卡、快捷操作、待处理任务主列、业务提醒轮播、处理记录轮播与任务详情抽屉；不再承担旧 `taskId` 查询参数兼容回流
- `/enterprise/profile`：企业资料与认证中心，当前已收口为 `?tab=profile|certification|notifications` 页内多页路由，承接企业基础信息、Logo、企业简介、联系方式与外部链接、招募偏好提示、招聘方向、认证状态、认证附件与通知提醒设置；顶栏用户菜单中的“修改密码”会通过 `action=password` 打开弹窗
- `/enterprise/tasks`：企业任务中心，用于管理多任务、搜索与筛选状态、识别截止风险、关闭/重开、查看任务快照并进入单任务审核工作区，并承接 `createdTaskId` / `editedTaskId` / 旧 `taskId` query 的聚焦回流
- `/enterprise/tasks/create`：企业任务发布 / 编辑入口；负责任务背景、交付要求、奖励说明与发布前预览；当前正式复用 `editTaskId` query 进入编辑模式，不单独新建编辑路由
- `/enterprise/tasks/:taskId`：企业任务审核工作区；负责查看学生提交、按状态 / 贡献分筛选、给出继续接触 / 未入选结果，并对结果通知做留痕；当前已收口为“左侧提交队列 + 右侧连续阅读区 + 任务要求弹窗 + 联系方式/处理记录抽屉 + 结果动作弹窗”的结构，关键词搜索继续保留前端即时过滤，且已去除旧的内部审核备注能力
- 企业端轻量沟通说明：平台内只承接“结果通知 + 一次轻量继续接触”，不建设完整实时聊天、面试排班或录用验证系统

## 6. 组件级约定
- `AppShell`：统一侧边导航与内容区容器
- `RequireAuthenticatedRoute`：统一登录态守卫；未登录时回 `/auth?mode=login` 并保留完整回跳路径
- `RequireRoleRoute`：统一学生 / 导师 / 企业角色守卫；支持 `redirect / forbidden` 两类错配策略
- `RequireAdminRoute`：管理员后台独立守卫，不与 C 端共享角色守卫混用
- `task-card` / `panel-block` / `stats-grid`：阶段一通用展示组件样式类
- `NotificationCenterProvider`：应用级通知上下文，统一维护未读数、WS 连接状态、桌面提醒授权、单条/批量已读回写

### 6.1 Skills 专项约定
- `/skills` 允许采用显著不同于工作台的沉浸式视觉语言，但必须保留“返回工作台”的清晰入口。
- 技能页 UI 中的 `LOCKED` 为展示态，真实进度状态继续以 `NOT_STARTED/LEARNING/MASTERED + unlocked` 组合映射。
- skills 相关扩展能力（主攻目标、推荐资源、定向 AI）统一按 `docs/spec/16_SKILLS_STAR_MAP_ALIGNMENT_PLAN.md` 分阶段推进。

## 7. 决策摘要
- 企业端正式路由收口到 `/enterprise/*`；`/bounty/:taskId` 继续保留为学生侧详情与兼容入口，不再作为企业正式审核主工作区。
- 导师详情当前正式收口到 `/mentors` 的 master-detail 结构；`/mentors/:mentorUserId` 只承担兼容深链接与未来扩展位，不再作为当前独立长页实现。
- `/dashboard` 当前只保留“共享工作台入口 / 兼容入口”语义；学生真实工作台已固定为 `/student/dashboard`。
- 角色错配不再一刀切直接 `/403`：私有工作台域优先自动回当前角色工作台或最近业务页，明确受限功能再统一进入 `/403`。
- 企业账号不再访问社区域；社区正式复用范围收口为学生 + 导师。
- 企业端按“轻量任务运营台”设计，不扩张为完整 ATS、实时聊天或面试排班系统。
- 平台内只承接“任务发布 -> 提交查看 -> 审核决策 -> 结果通知 -> 一次轻量继续接触”闭环；真实验证、线下面试与录用手续继续在平台外完成。
- 阶段一优先“页面可访问 + 闭环可演示”，不追求视觉精细化。
- 通知中心当前已支持 AI 简历任务、咨询订单、认证状态、企业任务审核工作区、企业资料页与系统公告等统一跳转语义；社区域本轮裁决为有限正式接入，只纳入帖子回复 / 审核结果 / 举报结果等核心事务通知，点赞等高频噪音类事件继续后置。
- 项目当前采用“双设计体系”：管理员后台保持 Ant Design 独立后台语言，C 端统一走 Tailwind CSS + Framer Motion 基线，并逐步向 shadcn/ui 风格组件收口。
- `apps/web/templates/Landing Page.jsx` 仅作为 Landing 首页的视觉参考，首页文案、信息模块与 CTA 必须回到真实业务需求、演示数据与当前可访问入口。

## 8. 验收标准
1. 所有已声明路由可访问且角色守卫生效；`/dashboard` 能按角色分流到真实工作台。
2. 学生 / 导师 / 企业误入其他角色私有域时，会按 `redirect` 策略自动回到当前角色工作台或最近业务页。
3. 明确受限功能继续按 `forbidden` 策略统一进入 `/403`，且登录态主 CTA 为“返回我的工作台”。
4. 企业工作台、任务中心、任务审核工作区与通知中心形成轻量企业任务闭环。
5. 学生可从列表进入详情并提交成果，并能收到企业继续接触或未入选结果通知。
6. 企业审核结果与继续接触记录可在通知中心稳定回看；企业任务通知已优先跳转 `/enterprise/tasks/:taskId`，企业认证提醒已优先跳转 `/enterprise/profile` 的认证状态板块。
