# AI 网关提示词维护子路由 UI 设计需求

## 页面标识
- 页面名称：AI 网关 / 提示词维护
- 子路由：`/admin/ai/gateway?tab=templates`
- 对应组件片段：`AdminAiGatewayPage -> templates tab`
- 页面定位：固定业务场景下既有模板版本的查看、试渲染与正文维护页

## 页面目标
- 围绕当前已绑定模板做查看和维护，不再提供独立的“模板版本库中心”
- 让管理员在同一工作面完成：
  - 查看模板正文、变量定义、消息包
  - 用固定变量结构试渲染
  - 编辑正文、描述、消息包
- 明确变量定义是场景契约的一部分，默认只读，不在后台自由增删

## 信息架构
- 左侧场景模板导航：场景名、模板族、当前版本、状态
- 中部模板详情区：
  - 基础标签：任务类型、模板格式、版本状态
  - 模板正文
  - 变量定义 JSON（只读）
  - 消息包 JSON
- 右侧预览与编辑区：
  - 自动填充示例变量
  - 渲染预览表单与渲染结果
  - 正文 / 描述 / 消息包编辑区

## 数据承接
- 主要接口：`GET /admin/ai/prompt-templates`、`POST /admin/ai/prompt-templates`、`PUT /admin/ai/prompt-templates/:id`、`POST /admin/ai/prompt-templates/render-preview`、`POST /admin/ai/prompt-templates/:id/publish`、`POST /admin/ai/prompt-templates/:id/rollback`。
- 模板对象 `PromptTemplateItem`：`id`、`taskType`、`templateName`、`versionNo`、`status`、`templateFormat`、`content`、`description`、`variablesJson`、`bundleJson`、`createdAt`、`updatedAt`。
- 试渲染请求体：`{ taskType, templateFormat, content, bundleJson, variablesJson, renderVariablesJson }`。
- 试渲染返回 `PromptPreviewPayload`：`taskType`、`templateFormat`、`renderedContent`、`renderedBundleJson`、`placeholderVariables[]`、`missingVariables[]`、`resolvedVariablesJson`。
- 编辑保存请求体沿用既有 `PUT /admin/ai/prompt-templates/:id`，但当前页面只允许维护正文、描述和消息包，不默认开放变量结构和版本状态修改。
- 固定场景模板导航依赖固定场景注册表与 route-policy 绑定关系，语音 / 转写类场景若不依赖模板，应在左侧列表中显式标注“无模板场景”。
- 当场景还没有任何可维护模板时，页面需要支持“补建首个模板版本”；当场景已经有历史模板时，页面需要支持“从当前版本新建草稿版本”，而不是只允许直接改动当前版本。

## 关键交互
- 查看模板时要先自动带入示例变量 JSON，便于直接试渲染
- 缺失变量需要给出强提醒
- 模板编辑保存后，场景路由页与当前模板详情需要同步刷新
- 占位标签需要支持一键匹配预览，帮助管理员快速确认固定场景下的模板变量是否完整
- 从场景路由或日志详情进入模板维护时，页面必须直接定位到对应场景 / 模板，减少二次查找成本
- “创建首个模板版本”和“新建草稿版本”都应默认复用场景既有契约信息：`taskType`、建议模板名、模板格式、变量定义与消息包骨架

## 设计限制
- 必须服务于“固定模板维护”，不要再恢复大而全的模板中心
- 变量定义只读展示，若要新增占位字段，应走场景契约变更，不在页面侧直接开放
- 允许使用深色代码区展示正文与渲染结果，但整体仍需保持后台风格
