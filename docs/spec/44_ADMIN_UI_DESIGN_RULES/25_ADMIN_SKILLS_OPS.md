# 技能资源治理台 UI 设计需求

## 页面标识
- 页面名称：技能资源治理台
- 主路由：`/admin/skills`
- 对应组件：`AdminSkillsOperationsPage`
- 页面定位：技能树节点与资源条目的后台 CMS 工作台

## 项目基线
- 前端技术栈：React 18 + TypeScript + Vite 5 + React Router 6
- UI 架构必须以 Ant Design 5 为主，允许使用 `@ant-design/pro-components`；Tailwind CSS 4 只做布局与间距辅助
- 页面接入 `AdminShellLayout`
- 桌面端优先，建议按 `1440px` 画板设计
- 主题主色 `#6366f1`，成功 `#10b981`，警告 `#f59e0b`，错误 `#ef4444`

## 页面目标
- 统一管理技能树节点与技能资源条目
- 支持节点、资源的筛选、详情查看、创建与编辑
- 强调“结构治理 + 内容治理”的双栏工作方式

## 信息架构
- 页面标题与首版边界提示
- 概览统计卡
- 双搜索工具栏
- 节点快速预览区
- 节点列表区
- 节点详情区
- 资源列表区
- 资源详情区
- 新建/编辑节点弹窗
- 新建/编辑资源弹窗

## 必备模块
- 顶部动作：新建节点、新建资源、刷新
- 边界提示：明确当前只支持 CMS 级治理，不含删除、整树重排、真上下线
- 统计卡：技能节点总数、技能关系总数、资源条目总数
- 节点快速预览：展示当前节点路径、星图落点说明、在学生端技能星图中的预览跳转入口
- 搜索筛选：节点关键词、父节点筛选、资源关键词、资源类型筛选
- 节点表字段：节点名称、层级、子节点数、资源数、入度/出度关系、排序、更新时间
- 节点详情：编码、名称、描述、父节点、子节点列表、该节点资源列表、资源类型摘要
- 资源表字段：资源名称、类型、所属节点、来源、时长/标签、排序、更新时间
- 资源详情：资源编码、标题、类型、所属节点、来源、链接地址、更新时间
- 弹窗表单字段：
- 节点：`nodeCode`、`label`、`description`、`parentCode`、`sortOrder`
- 资源：`resourceCode`、`nodeCode`、`resourceType`、`title`、`sourceLabel`、`durationLabel`、`linkUrl`、`sortOrder`

## 数据承接
- 主要接口：`GET /admin/skills/overview`、`GET /admin/skills/nodes`、`GET /admin/skills/resources`、`POST /admin/skills/nodes`、`PUT /admin/skills/nodes/:nodeCode`、`POST /admin/skills/resources`、`PUT /admin/skills/resources/:resourceCode`。
- 概览对象 `SkillOpsOverviewPayload`：`totalNodeCount`、`rootNodeCount`、`leafNodeCount`、`relationCount`、`totalResourceCount`、`nodesWithoutResourceCount`。
- 节点对象 `SkillNodeRecord`：`nodeCode`、`label`、`description`、`parentCode`、`parentLabel`、`sortOrder`、`childCount`、`resourceCount`、`outboundRelationCount`、`inboundRelationCount`、`updatedAt`。
- 资源对象 `SkillResourceRecord`：`resourceCode`、`nodeCode`、`nodeLabel`、`resourceType`、`title`、`sourceLabel`、`durationLabel`、`linkUrl`、`sortOrder`、`updatedAt`。
- 节点创建载荷：`POST /admin/skills/nodes`，请求体 `{ nodeCode, label, description?, parentCode?, sortOrder? }`；节点编辑载荷：`PUT /admin/skills/nodes/:nodeCode`，请求体 `{ label, description?, sortOrder? }`。
- 资源创建载荷：`POST /admin/skills/resources`，请求体 `{ resourceCode, nodeCode, resourceType, title, sourceLabel, durationLabel, linkUrl, sortOrder? }`；资源编辑载荷：`PUT /admin/skills/resources/:resourceCode`，请求体 `{ nodeCode, resourceType, title, sourceLabel, durationLabel, linkUrl, sortOrder? }`。
- 时间字段格式：技能治理页统一兼容 `number | string | null`；链接地址为字符串 URL；节点和资源编码都是稳定字符串主键。

## 关键交互
- 点击节点行后，右侧详情区要同步刷新该节点的子节点与资源摘要
- 节点详情区需要展示“路径面包屑 / 路径标签”，帮助运营快速理解当前节点在整棵技能树中的位置
- 节点详情区需要提供“打开技能星图预览”能力，优先跳到管理员只读预览路由 `/admin/skills/preview`
- 管理员预览页通过 query 传入 `nodeCode`、`focusNodeCode` 以定位节点；当前阶段仅保留背景、节点画布、路径与详情，不要求展示学生成长 HUD
- 点击资源行后，要能在详情区查看并快速打开外部链接
- 新建资源时若系统中无节点，需要明显提示先建节点
- 编辑节点与编辑资源都应保留当前选中上下文

## 推荐交互升级方向（供 UI 重设计）
- 不建议把节点治理完全做成单一表格页；更推荐“列表模式 + 结构模式”双视图并存
- 第一层仍保留表格/筛选能力，承担搜索、排序、批量巡检和录入效率
- 第二层强化结构感：节点详情区应突出节点路径、父子关系、资源覆盖和局部上下文，而不是只做字段回显
- 星图预览属于“理解结构”的辅助能力，不替代后台 CRUD；目标是帮助运营确认改动后的学生端实际呈现
- 当前阶段不建议直接做全量自由拖拽画布编辑；优先做局部预览、路径定位、跳转验证和上下文辅助

## 星图联动约束
- 学生端技能星图真实页面路由为 `/skills`
- 管理员只读预览路由为 `/admin/skills/preview`
- `/skills` 当前仍仅学生角色可访问；管理员默认通过 `/admin/skills/preview?nodeCode=<nodeCode>&focusNodeCode=<nodeCode>` 做结构验证
- 若需要核对真实学生 HUD 与学习态，再使用学生身份进入 `/skills`
- 当前管理员预览态定位为“结构验证工具”，若后续要扩成完整后台星图工作区，再单独评估是否补更多图层、筛选和审计信息

## 设计限制
- 优先使用 Ant Design 的 `Table`、`Card`、`Descriptions`、`Form`、`Input`、`Select`、`Modal`
- 页面应明显采用“列表 + 详情”的治理台结构，不要做成单列表
- 节点与资源需要通过颜色和图标区分“结构对象”与“内容对象”
