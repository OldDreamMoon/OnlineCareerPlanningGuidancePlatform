# 运行配置中心业务开关子路由 UI 设计需求

## 页面标识
- 页面名称：运行配置中心 / 业务开关
- 子路由：`/admin/runtime?tab=business`
- 对应组件片段：`AdminSystemSettingsPage -> renderBusiness()`
- 页面定位：平台级 feature flag 分组治理页

## 项目基线
- 前端技术栈：React 18 + TypeScript + Vite 5 + React Router 6
- UI 架构必须以 Ant Design 5 为主，允许使用 `@ant-design/pro-components`；Tailwind CSS 4 只做布局与间距辅助
- 页面接入 `AdminShellLayout`
- 桌面端优先，建议按 `1440px` 画板设计
- 主题主色 `#6366f1`，成功 `#10b981`，警告 `#f59e0b`，错误 `#ef4444`

## 页面目标
- 按真实治理域维护平台级业务开关
- 让管理员明确当前值、默认值、风险等级与覆盖情况
- 避免继续使用早期平铺式开关列表

## 信息架构
- 顶部说明告警
- 按治理域分组的开关区块
- 单个开关卡片

## 必备模块
- 顶部告警：展示总开关数、已覆盖默认值数量、当前分层维护原则
- 分组区块：
- 支付与交易
- AI 体验入口
- 社区运营
- 用户域
- 系统级开关
- 单个开关卡片元素：标题、说明、风险级别、当前值、默认值、覆盖状态、更新时间、保存动作

## 数据承接
- 主要接口：`GET /admin/system/console-snapshot`、`POST /admin/feature-flags`。
- 单个开关对象 `FeatureFlagItem`：`key`、`displayName`、`description`、`valueType`、`allowedValues[]`、`currentValue`、`defaultValue`、`overridden`、`updatedBy`、`updatedAt`。
- 业务开关按前端元数据分组展示，真实分组包括 `PAYMENT`、`AI`、`COMMUNITY`、`USER`、`SYSTEM`，设计稿要保留“分组治理”而不是一张总表。
- 保存载荷：`POST /admin/feature-flags`，请求体 `{ key: string; value: string | boolean }`；`valueType = BOOLEAN | ENUM` 决定开关控件还是下拉控件。
- 当前值和默认值需要并列承接，`overridden = true` 表示当前值已偏离系统默认。
- 时间字段格式：`updatedAt` 兼容 `number | string | null`；`updatedBy` 可能为空。

## 关键交互
- 分组标题需要强调治理域，不是简单列表分栏
- 每个开关都应并列展示“当前值”和“默认值”
- 高风险开关需有更强的视觉提醒
- 修改后应就地反馈保存状态，不需要离开当前分组

## 设计限制
- 优先使用 Ant Design 的 `Alert`、`Card`、`Switch`、`Select`、`Tag`、`Button`
- 开关页应采用卡片矩阵，不要退回普通表格
- 风险和覆盖状态的可视化优先级高于装饰性设计
