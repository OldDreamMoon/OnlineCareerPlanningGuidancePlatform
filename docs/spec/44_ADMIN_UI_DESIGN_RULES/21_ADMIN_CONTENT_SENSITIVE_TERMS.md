# 内容治理敏感词管理子路由 UI 设计需求

## 页面标识
- 页面名称：内容治理 / 敏感词管理
- 子路由：`/admin/content?tab=settings`
- 对应组件片段：`AdminContentModerationPage -> renderSettings()`
- 页面定位：敏感词词库录入、筛选、批量治理工作区

## 项目基线
- 前端技术栈：React 18 + TypeScript + Vite 5 + React Router 6
- UI 架构必须以 Ant Design 5 为主，允许使用 `@ant-design/pro-components`；Tailwind CSS 4 只做布局与间距辅助
- 页面接入 `AdminShellLayout`
- 桌面端优先，建议按 `1440px` 画板设计
- 主题主色 `#6366f1`，成功 `#10b981`，警告 `#f59e0b`，错误 `#ef4444`

## 页面目标
- 支持管理员快速录入敏感词并立即进入生效词库
- 支持按类型、风险、状态、范围筛选词条
- 支持批量启用、停用、删除，以及 CSV 导入导出

## 信息架构
- 录入卡片
- 词库工具栏
- 批量操作条
- 敏感词表格
- 编辑词条弹窗

## 必备模块
- 顶部摘要标签：词条总数、启用数、白名单数、类型数
- 录入表单字段：敏感词、类型、风险等级、处置动作、适用范围、白名单、启用状态
- 工具栏：关键词搜索、类型筛选、风险筛选、状态筛选、清空筛选
- 词库动作：导入 CSV、导出 CSV、刷新词库
- 批量动作：批量启用、批量停用、批量删除
- 表格字段：敏感词、类型、风险等级、处置动作、范围、状态、更新时间、操作
- 编辑弹窗：展示词条编号、创建时间、最近更新时间，并允许修改核心字段

## 数据承接
- 主要接口：`GET /admin/content/sensitive-terms`、`POST /admin/content/sensitive-terms`、`PUT /admin/content/sensitive-terms/:termId`、`DELETE /admin/content/sensitive-terms/:termId`、`POST /admin/content/sensitive-terms/batch-status`、`POST /admin/content/sensitive-terms/batch-delete`、`GET /admin/content/sensitive-terms/export`、`POST /admin/content/sensitive-terms/import`。
- 词条对象 `SensitiveTermItem`：`termId`、`term`、`termType`、`riskLevel`、`action`、`sourceScope`、`whitelist`、`enabled`、`createdAt`、`updatedAt`。
- 新增/编辑表单载荷统一使用：`{ term, termType, riskLevel, action, sourceScope, whitelist, enabled }`。
- 批量启停载荷：`POST /admin/content/sensitive-terms/batch-status`，请求体 `{ termIds: number[]; enabled: boolean }`；批量删除载荷：`POST /admin/content/sensitive-terms/batch-delete`，请求体 `{ termIds: number[] }`。
- 导入接口使用 `multipart/form-data` 上传 CSV 文件；导入结果 `SensitiveTermImportResponse` 包含 `totalRows`、`createdCount`、`updatedCount`、`skippedCount`、`errors[]`。
- 导出 CSV 字段口径应与录入/编辑表单一致，设计稿要为“字段顺序固定、后续交给外部模板复用”留出位置。
- 时间字段格式：创建时间与更新时间均为字符串；布尔字段 `whitelist`、`enabled` 直接影响标签和开关状态。

## 关键交互
- 新增词条后应立即进入词库列表并可被筛选定位
- 表格支持多选，批量操作区要明确反馈当前已选数量
- 导入 CSV 与导出 CSV 的字段结构需保持一致
- 编辑和批量删除都要有明确确认反馈

## 设计限制
- 优先使用 Ant Design 的 `Form`、`Input`、`Select`、`Switch`、`Tag`、`Table`、`Modal`、`Upload`
- 录入区与词库区应在宽屏下明显分层，不要做成单一长表单
- 词条风险与处置动作要有稳定的颜色编码，便于批量巡检
