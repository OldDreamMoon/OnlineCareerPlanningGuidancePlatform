# 技能星图页面对齐与分阶段实施规划

> **文档状态**：`evolving` · 最后审核：2026-03-19
>
> **适用阶段**：学生端前端正式重构阶段；当前重点是把 `apps/web/templates/skills.jsx` 的视觉骨架转成可联调、可答辩、可持续扩展的真实技能页。
>
> **关联文档**：
> - `docs/spec/04_API_SPEC.md`
> - `docs/spec/05_DB_SCHEMA.md`
> - `docs/spec/10_MILESTONE_AND_BACKLOG.md`
> - `docs/spec/14_FRONTEND_ROUTES_AND_PAGES.md`
> - `docs/spec/15_FRONTEND_REFACTOR_CHECKLIST.md`

## 1. 目标
- 为学生端 `/skills` 提供一份独立的专项规划文档，避免后续 Agent 仅凭模板截图或零散对话推进。
- 明确 `apps/web/templates/skills.jsx` 中哪些视觉与交互应该保留、哪些可直接对接当前后端、哪些需要后端增量贴合。
- 把 skills 页拆成分阶段任务，降低“模板很好看但接不上真实功能”的风险。

## 2. 当前背景

### 2.1 当前学生端信息架构结论
- `dashboard` 吸收原本 `growth` 首页职责，负责展示签到、每日任务、积分摘要、成长信号与推荐下一步动作。
- `profile` 收口为“账号与资料中心”，负责个人资料维护与画像反馈展示。
- `skills` 保持独立路由，用于承接更沉浸式、游戏化的技能成长体验。
- `growth` 暂不作为优先独立页面推进；后续仅在签到 / 任务 / 积分需要独立承载更深明细时再扩展。

### 2.2 当前后端已具备的真实能力
- 技能树查询：`GET /skills/tree`
- 技能进度更新：`POST /skills/progress`
- 成长体系基础接口：
  - `POST /growth/checkin`
  - `GET /growth/tasks/daily`
  - `POST /growth/tasks/{taskId}/complete`
  - `GET /growth/points/ledger`
- 学生画像与成长证据：`GET /profiles/students/me`
- AI 面试主链路：`POST /ai/interview/sessions`

### 2.3 当前模板 `skills.jsx` 的定位
- 它不是单纯的“技能树列表页模板”，而是一个完整的“成长星图系统”视觉原型。
- 模板内混合了 4 类能力：
  1. 技能树本体：主画布、缩放、节点、连线、状态
  2. 成长 HUD：等级、经验、称号、用户层级、统计摘要
  3. 节点拓展：主攻目标、外链学习资源
  4. AI 联动：从技能节点发起专项 AI 面试验证
- 其中第 1 类可较快落地，第 2~4 类需要分层处理，不宜一次性全部硬接。

### 2.4 当前落地进展（2026-03-20）
- Phase 1 已完成前端真实接线：新增 [`SkillsPage.tsx`](apps/web/src/pages/SkillsPage.tsx) 与 `/skills` 路由，并在学生工作台 [`DashboardPage.tsx`](apps/web/src/pages/DashboardPage.tsx) 放开入口。
- 桌面端已保留模板最有辨识度的骨架：沉浸式深色主画布、节点连线、拖拽缩放、右侧详情面板、左下 HUD、右上图例。
- 已接通真实接口闭环：`GET /skills/tree`、`POST /skills/progress`、节点状态映射、前置依赖失败提示。
- 移动端未强塞桌面星图交互，已降级为纵向技能路径列表；但根据 2026-03-20 的新执行口径，当前阶段人工审查与修正仍以宽屏桌面端为准。
- 仍未进入当前范围：主攻目标后端持久化、推荐学习资源、技能定向 AI 面试、等级 / 经验 / 称号系统。

## 3. 核心决策

### 3.1 总体策略
- `/skills` 采用“保视觉骨架、分阶段接能力”的策略推进。
- 第一阶段优先保证“星图主画布 + 节点详情 + 状态更新”形成真实闭环。
- 第二阶段再补“当前主攻目标 + HUD 摘要 + 推荐资源 + 技能定向 AI”。
- 等级、经验、称号等重成长体系暂不作为当前必做项。

### 3.2 技能页对齐原则
1. 先保住模板最有辨识度的视觉核心，不为了快速接线而退化成普通列表页。
2. 先接已有技能树契约，不为了模板效果立即大改全站成长模型。
3. 能以前端本地状态暂存的交互，不立即强迫后端补数据库。
4. 若某项能力要进入真实演示主链路，必须补最小契约而不是长期依赖假数据。

## 4. 模板模块与支撑度映射

| 模板模块 | 当前支撑度 | 结论 | 说明 |
|---|---|---|---|
| 星图主画布、拖拽、缩放 | 高 | 直接保留 | 属于纯前端视觉能力，可直接复用模板结构 |
| 节点树结构与连线 | 高 | 直接保留并真接接口 | `GET /skills/tree` 已提供 `nodeCode/label/description/parentCode/sortOrder/status/unlocked/updatedAt` |
| 节点详情侧栏 | 高 | 直接保留并真接接口 | 节点标题、说明、状态、更新时间均可映射真实字段 |
| 节点状态更新 | 高 | 直接实现 | `POST /skills/progress` 已可支持 `NOT_STARTED/LEARNING/MASTERED` 更新 |
| 状态图例 | 高 | 直接实现 | 视觉上可保留 `Locked/Learning/Mastered/Target`，其中 `Target` 第一阶段先作为本地态 |
| 左下角成长 HUD 样式 | 中 | 保留样式，内容降级 | 首轮不做等级/经验，先映射真实成长摘要 |
| 当前主攻目标（Track） | 低-中 | 第一阶段本地态，第二阶段持久化 | 当前后端无 focus skill 字段或接口 |
| 外链学习资源 | 低 | 第二阶段补配置驱动能力 | 当前无资源系统，建议先走配置文件而非建后台 |
| 发起专项 AI 面试验证 | 中 | 第二阶段实现 | AI 面试链路已存在，但暂无 skill-specific 上下文 |
| 等级 / 经验 / 称号 | 低 | 暂缓 | 当前 PRD / API / DB 未形成完整规则，不建议为模板硬上 |

## 5. 分阶段实施规划

### 5.1 Phase 1：技能树本体真实接线
目标：
- 让 `/skills` 从“纯视觉模板”变成真实可交互的技能页。

范围：
- 主画布、节点、连线、缩放与定位
- 节点详情面板
- 节点状态更新
- 进度汇总

前端任务：
- [x] 用真实 `GET /skills/tree` 替换模板中的 `mockSkillTree`
- [x] 建立 `status + unlocked` 到 UI 状态的映射关系
- [x] 支持点击节点展开详情
- [x] 接入 `POST /skills/progress`，完成“开始学习 / 标记掌握 / 回退未开始”按钮逻辑
- [x] 在失败时展示后端返回的前置条件或子节点冲突提示
- [x] 将模板右侧详情里的静态文案替换为真实节点说明与状态说明
- [x] 为移动端补纵向路径式降级，不强塞拖拽星图交互；当前阶段不把移动端体验作为正式验收阻塞项

后端任务：
- 无需先改数据库与主接口
- 仅在需要时补充错误码说明或接口注释，不优先新增契约

验收标准：
- [x] 学生可进入 `/skills`
- [x] 节点结构与状态来自真实接口
- [x] 可更新节点进度并立刻回显
- [x] 锁定节点与失败回退有明确提示
- [ ] 待补浏览器真环境确认宽屏桌面端的拖拽/缩放、节点交互与右侧详情体感

### 5.2 Phase 2：主攻目标与 HUD 摘要
目标：
- 在不引入完整等级系统的前提下，让 skills 页具备“正在重点攻克什么、我当前成长到哪一步”的产品感。

范围：
- 当前主攻目标（Track Target）
- 左下角成长 HUD
- 页面顶部或侧边的技能进度摘要

建议新增能力：
- `GET /skills/overview`
- `PUT /skills/focus`
- `GET /skills/focus`

建议返回的最小字段：
- `communityScore7d`
- `portraitUpdatedAt`
- `skillsSummary.total/mastered/learning/notStarted`
- `interviewMessages7d`
- `posts7d`
- `comments7d`
- `likesReceived7d`
- `currentFocusSkillCode`
- 可选：`tier`

推荐实现顺序：
1. 第一小步先把 Track Target 做成前端本地态
2. 若浏览器体验和演示价值成立，再补持久化接口
3. HUD 首轮只展示真实成长摘要，不扩展经验值体系

验收标准：
- 用户可以显式设置或切换“当前主攻目标”
- 技能页可显示稳定的成长摘要，而不是纯假数据
- Dashboard 可选复用同一份 focus skill / growth overview 数据

### 5.3 Phase 3：推荐资源与技能定向 AI
目标：
- 让节点详情面板从“技能说明”升级为“技能学习入口 + AI 验证入口”。

范围：
- 节点推荐学习资源
- 从技能节点发起专项 AI 面试验证

建议新增能力：
- `GET /skills/resources/{nodeCode}` 或在 `GET /skills/tree` 中扩展 `resources`
- `POST /ai/interview/sessions` 增加可选上下文字段：
  - `skillNodeCode`
  - `skillLabel`
  - 或 `focusTopic`

资源系统推荐实现方式：
- 优先采用配置驱动，而不是立即建立数据库后台
- 推荐使用 `YAML/JSON` 配置：
  - `nodeCode`
  - `type`
  - `title`
  - `source`
  - `duration`
  - `url`

不建议首轮采用的方案：
- 直接新建一整套“学习资源管理后台”
- 引入复杂排序、评分、推荐算法

验收标准：
- 处于 `LEARNING` 或 `MASTERED` 的节点可看到推荐资源
- 从节点可直接发起带技能上下文的 AI 面试或跳转到 AI 面试创建页
- 资源与 AI 联动不会破坏原有 AI 面试主链路

### 5.3.1 专项面试设计结论（2026-04-03）
当前针对“技能节点专项 AI 测试”已形成如下设计结论，先记录为后续实现指导，不在本轮立即落地：

- 技能专项面试不应简单复用当前通用 `/ai/interview` 准备页并只做“隐藏部分配置项”。
- 原因是当前通用面试页的产品语义仍然是“岗位导向的求职模拟面试”，其准备参数、会话上下文与 opening / reply / summary prompt 主要围绕目标岗位、项目经历、简历背景和求职语境构建。
- 若仅在前端隐藏通用参数，而继续复用通用 prompt，会形成“界面看起来像技能专项测试，但 AI 实际仍按岗位面试出题”的语义错位。
- 因此，技能专项面试应视为“AI 面试体系下的独立场景模式”，而不是现有通用准备页的一个轻量开关。

### 5.3.2 推荐架构拆分（待实现）
推荐采用“独立场景入口 + 独立 AI scene / prompt template + 复用底层面试基础设施”的方案：

- 前端入口层：
  - 推荐新增独立精简入口路由，例如 `/ai/interview/skill/:nodeCode`
  - 该路由仍归属 AI 面试域，而不是挂到 `/skills/:nodeCode/interview`
  - 原因是它本质上仍然属于 AI 面试体系，只是场景更聚焦、参数更少
- 前端页面层：
  - 专项页应采用精简准备模式，只保留当前技能节点、验证范围、题型说明、作答模式、可选强度开关与开始按钮
  - 不再暴露目标企业、岗位 JD、大量带入资料、多组通用面试类型等岗位导向配置
- AI 路由 / 提示词层：
  - 不建议继续复用现有 `INTERVIEW_OPENING / INTERVIEW_REPLY / INTERVIEW_SUMMARY` 三组通用场景
  - 推荐新增独立 scene：
    - `SKILL_INTERVIEW_OPENING`
    - `SKILL_INTERVIEW_REPLY`
    - `SKILL_INTERVIEW_SUMMARY`
    - 可选：`SKILL_INTERVIEW_ANSWER_HELPER`
  - 上述 scene 仍可复用现有 AI gateway 的 `taskType + sceneCode + route + prompt template` 机制，不必新造一套 AI 基础设施
- 会话与存储层：
  - 不建议新建独立的 skill interview 会话系统
  - 继续复用现有 `POST /ai/interview/sessions`、历史、流式回复、复盘、积分与 quota 链路
  - 但需要在 `sessionContext` 中补充结构化场景标识，例如：
    - `scenarioCode=SKILL_NODE`
    - `skillContext.skillNodeCode`
    - `skillContext.skillLabel`
    - `skillContext.focusTopic`
    - `skillContext.questionStyle`
    - `skillContext.knowledgePoints`
- 技能节点配置层：
  - 不建议把专项面试配置直接并入 `skill_node_resources`
  - 推荐为技能节点增加独立 `interviewConfig` 能力，用于描述“这个节点该如何被测试”，而不是“这个节点推荐学什么资源”
  - 最小可配置项建议包括：
    - `nodeCode`
    - `enabled`
    - `focusTopic`
    - `questionStyle`
    - `defaultDifficulty`
    - `defaultAnswerMode`
    - `targetRoleHint`
    - `expectedKnowledgePoints`
    - 可选的 prompt seed / summary seed

### 5.3.3 本轮不建议进入的范围
- 不新建独立 `skill-interview` 后端服务
- 不复制一套新的 session / message / summary / history 表结构
- 不把所有技能节点默认都设为可发起专项面试
- 不把技能专项面试和“自动改技能进度”在同一轮绑定实现
- 不把当前通用 `/ai/interview` 直接改造成“既做岗位面试又做知识点测验”的大杂糅页面

### 5.4 Phase 4：重成长系统（可选延后）
目标：
- 仅在答辩需要“成长游戏化亮点”且前 3 阶段已稳定时，再评估完整等级体系。

可评估项：
- 等级
- 称号
- 经验值
- 等级奖励或更复杂成长轨迹

当前结论：
- 暂不进入实现
- 不作为 `/skills` 第一轮和第二轮的依赖项

## 6. UI 状态与真实契约映射

### 6.1 节点状态映射建议
- UI `LOCKED`：由 `unlocked=false` 推导得到
- UI `NOT_STARTED`：`unlocked=true` 且 `status=NOT_STARTED`
- UI `LEARNING`：`status=LEARNING`
- UI `MASTERED`：`status=MASTERED`
- UI `TARGET`：第一阶段以前端本地 focus 态实现；第二阶段再持久化

### 6.2 节点操作映射建议
- 若节点 `LOCKED`：只展示锁定说明，不展示状态更新按钮
- 若节点 `NOT_STARTED`：允许“开始学习”
- 若节点 `LEARNING`：允许“标记掌握”“设为主攻目标”
- 若节点 `MASTERED`：允许查看详情；是否允许回退由后端校验

### 6.3 HUD 内容降级建议
首轮可使用真实数据替代模板里的等级宇宙：
- 总节点数 / 已掌握 / 学习中 / 未开始
- 社区贡献分
- 近 7 天互动量
- 近 7 天面试消息数
- 当前主攻目标

不建议首轮继续使用：
- `level`
- `title`
- `exp`
- `nextLevelExp`

## 7. 数据与接口建议

### 7.1 当前继续复用的接口
- `GET /skills/tree`
- `POST /skills/progress`
- `GET /profiles/students/me`
- `POST /ai/interview/sessions`
- `POST /growth/checkin`
- `GET /growth/tasks/daily`
- `GET /growth/points/ledger`

### 7.2 建议新增的轻量接口
- `GET /skills/overview`
- `GET /skills/focus`
- `PUT /skills/focus`
- `GET /skills/resources/{nodeCode}`

### 7.3 建议的最小持久化方案
- 若要保存主攻目标，优先考虑：
  - 在 `student_profiles` 中增加 `current_focus_skill_code`
- 若要提供推荐资源，优先考虑：
  - 先用后端配置文件维护，不直接改数据库

## 8. 开发守则
- 不要为了模板效果立刻重写整个成长体系。
- 不要长期保留大块假数据；若某模块无法接真，需明确标记为预留或静态配置。
- 不要把 `/skills` 做成依赖大量后端新增能力才能打开的页面。
- 保持技能页与 `dashboard/profile` 的角色语义一致：前者负责沉浸成长，后两者负责总览与资料管理。

## 9. 后续 Agent 推荐执行顺序
1. 先读取本文件、`04_API_SPEC.md`、`14_FRONTEND_ROUTES_AND_PAGES.md`、`15_FRONTEND_REFACTOR_CHECKLIST.md`
2. 先完成 Phase 1，不跨阶段硬接 XP / Title / Reward
3. Phase 1 稳定后再决定是否推进 focus skill 的持久化
4. 再补资源配置与技能定向 AI，避免并行改动过多导致技能页与 AI 页同时失稳

## 10. 阶段完成定义

### Phase 1 DoD
- `/skills` 页面可访问
- 真实技能树可展示
- 节点可更新进度
- 失败提示和锁定说明清楚

### Phase 2 DoD
- skills 页能展示稳定的成长摘要
- 当前主攻目标可切换并回显
- Dashboard 可共享部分摘要能力

### Phase 3 DoD
- 节点资源列表可展示
- 节点可引导进入定向 AI 面试
- 不破坏既有 AI 面试主链路

## 11. 结论
- `apps/web/templates/skills.jsx` 适合作为学生端亮点页面的视觉主稿，不建议废弃。
- 当前最合理的路径不是“完全照搬模板”或“完全按现有后端降级”，而是采用“星图骨架保留 + 契约分阶段贴合”的方案。
- 后续任何 Agent 若要继续推进 `/skills`，应优先依据本文件拆阶段，再进入接口与实现细节，而不是直接围绕模板做全量复刻。
