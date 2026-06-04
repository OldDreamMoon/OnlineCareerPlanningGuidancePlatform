# 运行配置中心风险降级开关子路由 UI 设计需求

## 页面标识
- 页面名称：运行配置中心 / 风险降级开关
- 子路由：`/admin/runtime?tab=degrade`
- 对应组件片段：`AdminSystemSettingsPage -> renderDegrade()`
- 页面定位：AI 输入、AI 输出、社区严格审查三类保护层的控制页

## 项目基线
- 前端技术栈：React 18 + TypeScript + Vite 5 + React Router 6
- UI 架构必须以 Ant Design 5 为主，允许使用 `@ant-design/pro-components`；Tailwind CSS 4 只做布局与间距辅助
- 页面接入 `AdminShellLayout`
- 桌面端优先，建议按 `1440px` 画板设计
- 主题主色 `#6366f1`，成功 `#10b981`，警告 `#f59e0b`，错误 `#ef4444`

## 页面目标
- 让管理员直观看到当前风险保护层是否被放宽
- 集中控制 AI 输入审查、AI 输出审查、社区严格审查
- 同时展示与举报阈值之间的联动关系

## 信息架构
- 顶部风险告警
- 左侧保护层开关主表单
- 右侧风险联动提醒侧卡

## 必备模块
- 顶部告警：已放宽保护层列表或“当前全量开启”状态
- 保护层卡片：
- AI 输入审查
- AI 输出审查
- 社区严格审查
- 每张卡需包含：标题、说明、当前策略文案、开关控件
- 保存动作：保存风险降级开关
- 风险联动提醒区：
- 三个保护层当前状态
- 举报自动隐藏阈值只读联动展示
- 简短的风险影响说明

## 数据承接
- 主要接口：`GET /admin/system/console-snapshot`、`PUT /admin/content/moderation/policies`。
- 本子页核心对象 `ModerationPoliciesResponse`：`aiInputEnabled`、`aiOutputEnabled`、`communityStrictReviewEnabled`、`autoHideReportThreshold`。
- 保存载荷：`PUT /admin/content/moderation/policies`，请求体直接提交整个对象 `{ aiInputEnabled, aiOutputEnabled, communityStrictReviewEnabled, autoHideReportThreshold }`。
- 左侧三个保护层是可编辑核心动作，右侧的 `autoHideReportThreshold` 只作为联动只读信息展示，不在本子页单独编辑。
- 顶部风险告警由上述三个布尔字段前端推导：任一为 `false` 时进入“已放宽保护层”态；全部为 `true` 时显示“当前全量开启”。
- 时间字段不在本子页直接展示，但来源仍是运行配置中心的控制台快照；阈值字段为数字，三个保护层为布尔值。

## 关键交互
- 放宽任一保护层时，页面整体风险感知要明显提升
- 三个开关应是同等级核心动作，不要埋在长表单下方
- 保存后要在当前页明确反馈结果
- 右侧联动提醒要帮助管理员理解保护层与内容治理阈值的关系

## 设计限制
- 优先使用 Ant Design 的 `Alert`、`Card`、`Form`、`Switch`、`Button`、`Tag`
- 页面要突出“高风险配置”属性，视觉上比普通配置页更克制、更警示
- 左右两列应形成“操作区 + 风险解释区”的稳定桌面布局
