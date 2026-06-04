# 内容治理中心主路由 UI 设计需求

## 页面标识
- 页面名称：内容治理中心
- 路由：`/admin/content`
- 对应组件：`AdminContentModerationPage`
- 页面定位：举报、待审、审计、敏感词的统一治理工作台

## 项目基线
- 前端技术栈：React 18 + TypeScript + Vite 5 + React Router 6
- UI 架构必须以 Ant Design 5 为主，允许使用 `@ant-design/pro-components`；Tailwind CSS 4 只做布局与间距辅助
- 页面接入 `AdminShellLayout`
- 桌面端优先，建议按 `1440px` 画板设计
- 主题主色 `#6366f1`，成功 `#10b981`，警告 `#f59e0b`，错误 `#ef4444`
- 页面是内容治理后台，不是社区前台延伸

## 页面目标
- 在一个主路由下统一承载举报处理、待审队列、审计日志、敏感词设置
- 提供多工作区 tab 切换
- 保留内容治理的摘要统计和明确的风险语义

## 信息架构
- 顶部标题、说明、刷新
- 摘要统计卡
- 一级 Tabs 导航
- 各 tab 独立工作区

## 必备模块
- 摘要卡：待处理举报、待审队列、当前页已采纳、启用词条
- 一级 tab：举报中心、待审队列、审计日志、敏感词设置
- 支持 query `tab` 深链
- 当带 `traceId` 时默认切到审计日志

## 数据承接
- 主路由初始化接口：`GET /admin/content/reports?page&size&status&targetType`、`GET /admin/content/review-queue?page&size&sourceType`、`GET /admin/content/audit-logs?page&size&targetType&actionType&targetId&traceId`、`GET /admin/content/sensitive-terms`。
- 摘要卡不是独立概览接口，而是由四组主数据前端计算：`reportTotal`、`reviewTotal`、`reports` 当前页中 `status = ACCEPTED` 的数量、`sensitiveTerms` 中 `enabled = true` 的数量。
- URL 规则：`tab` 控制四个工作区切换；带 `traceId` 时默认进入审计日志 tab，并把追踪编号预填进筛选区。
- 四个主工作区共享一套治理底层对象：举报单 `ReportItem`、待审项 `ReviewQueueItem`、审计日志 `AuditLogItem`、敏感词 `SensitiveTermItem`。
- 主路由需要承接多个详情层：举报详情与处置弹窗、待审详情与审核弹窗、审计详情层、敏感词编辑弹窗、CSV 导入结果反馈。
- 时间字段格式：内容治理主路由统一使用字符串时间；敏感词的批量操作结果只返回受影响数量，不返回完整列表。

## 关键交互
- tab 切换需要保留治理语义，不可像普通内容页
- 每个 tab 都要能跳往用户详情或 AI 网关等关联页
- 错误状态和空状态要可局部呈现

## 设计限制
- 优先使用 Ant Design 的 `Tabs`、`Card`、`Alert`、`Table`、`Button`
- 主路由视觉重点是治理秩序和风险感，不要做社区化风格
- 子 tab 设计稿必须共享统一页面框架
