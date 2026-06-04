import { Alert, AutoComplete, Button, Drawer, Form, Input, InputNumber, Modal, Select, Switch, Tag, Typography } from "antd";
import { Plus, Search } from "lucide-react";
import { AdminDetailPlaceholder, AdminMiniStat } from "../AdminOpsPrimitives";
import { auditActionLabelMap, gatewayExecutionModeLabelMap, gatewayProviderTypeLabelMap, gatewayTaskTypeLabelMap, getLabel } from "../../../lib/adminLabels";
import { formatCount, formatDateTime } from "../../../lib/formatters";
import type { AdminWorkspaceModuleProps } from "../AdminLazyWorkspace";

const { Paragraph, Text } = Typography;
const { TextArea } = Input;

const thinkingEffortOptions = [
  { label: "关闭", value: "OFF" },
  { label: "低", value: "LOW" },
  { label: "中", value: "MEDIUM" },
  { label: "高", value: "HIGH" },
  { label: "动态", value: "DYNAMIC" },
];

const thinkingLevelOptions = [
  { label: "minimal", value: "minimal" },
  { label: "low", value: "low" },
  { label: "standard", value: "standard" },
  { label: "medium", value: "medium" },
  { label: "high", value: "high" },
];

export default function AdminAiGatewayOverlays({ context }: AdminWorkspaceModuleProps<any>) {
  const {
    scenePreviewDrawerOpen,
    scenePreviewDrawerTitle,
    setScenePreviewDrawerOpen,
    routePreviewForm,
    taskTypeOptions,
    meta,
    tierCardOrder,
    gatewayTierLabelMap,
    allModelOptions,
    routePreviewLoading,
    handleSubmitRoutePreview,
    routePreviewError,
    routePreviewResult,
    formatCny,
    gatewayRouteStrategyLabelMap,
    providerEditorOpen,
    editingProvider,
    providerSaving,
    handleSaveProvider,
    setProviderEditorOpen,
    providerForm,
    getMappedOptionLabel,
    connectivityTarget,
    setConnectivityTarget,
    setConnectivityResult,
    connectivityResult,
    connectivityLoading,
    getRuntimeStatusTag,
    policyEditorOpen,
    editingPolicy,
    policySeedScene,
    policySaving,
    handleSavePolicy,
    setPolicyEditorOpen,
    policyForm,
    buildDefaultPolicyCode,
    candidateEditorOpen,
    editingCandidate,
    candidateParentPolicy,
    candidateSaving,
    handleSaveCandidate,
    setCandidateEditorOpen,
    candidateForm,
    providerOptions,
    candidateModelOptions,
    templateOptions,
    handleGenerateRouteCode,
    templateEditorOpen,
    selectedTemplate,
    templateEditorSaving,
    handleSaveTemplate,
    setTemplateEditorOpen,
    templateEditForm,
    logDrawerOpen,
    selectedLog,
    setLogDrawerOpen,
    logDetailLoading,
    logDetailError,
    selectedLogDetail,
    getLogStatusTag,
    formatThinkingSummary,
    safePrettifyJson,
    handleLocateRouteFromLog,
    handleLocateTemplateFromLog,
    applyLogFilters,
    navigate,
    getCurrencyLabel,
  } = context;

  return (
    <>
      <Drawer
        open={scenePreviewDrawerOpen}
        width={560}
        destroyOnClose={false}
        title={scenePreviewDrawerTitle}
        onClose={() => setScenePreviewDrawerOpen(false)}
      >
        <div className="space-y-6">
          <Form form={routePreviewForm} layout="vertical" initialValues={{ userTier: "ALL" }}>
            <Form.Item label="任务类型" name="taskType" rules={[{ required: true, message: "请选择任务类型" }]}>
              <Select options={taskTypeOptions} />
            </Form.Item>
            <Form.Item label="场景编码" name="sceneCode">
              <Input placeholder="请输入场景编码" />
            </Form.Item>
            <Form.Item label="用户层级" name="userTier" rules={[{ required: true, message: "请选择用户层级" }]}>
              <Select
                options={
                  meta.tierOptions.length > 0
                    ? meta.tierOptions.map((item: any) => ({
                      label: gatewayTierLabelMap[item.code as keyof typeof gatewayTierLabelMap] || item.label || item.code,
                      value: item.code,
                    }))
                    : tierCardOrder.map((tier: string) => ({ label: gatewayTierLabelMap[tier as keyof typeof gatewayTierLabelMap], value: tier }))
                }
              />
            </Form.Item>
            <Form.Item label="模型偏好" name="modelPreference">
              <AutoComplete options={allModelOptions} placeholder="可选，优先匹配某个模型" />
            </Form.Item>
            <Button
              type="primary"
              className="!h-11 !rounded-2xl !border-none !bg-sky-600"
              loading={routePreviewLoading}
              onClick={() => void handleSubmitRoutePreview()}
            >
              <Search size={16} className="mr-2" />
              查看预览
            </Button>
          </Form>

          {routePreviewError ? <Alert type="error" showIcon className="rounded-2xl" message={routePreviewError} /> : null}

          {!routePreviewResult ? (
            <AdminDetailPlaceholder description="填写条件后查看方案详情" />
          ) : (
            <div className="space-y-4">
              <div className="rounded-[24px] bg-gradient-to-br from-[#0f172a] via-[#1e293b] to-[#334155] px-5 py-5 text-white">
                <div className="text-[11px] font-bold text-white/60">命中方案</div>
                <div className="mt-3 font-['Manrope'] text-2xl font-black tracking-[-0.04em] text-white">{routePreviewResult.routeCode}</div>
                <div className="mt-2 text-sm text-white/72">
                  {routePreviewResult.providerDisplayName} · {routePreviewResult.model}
                </div>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <AdminMiniStat label="用户层级" value={gatewayTierLabelMap[(routePreviewResult.routePolicyUserTier || "ALL") as keyof typeof gatewayTierLabelMap] || routePreviewResult.routePolicyUserTier || "通用"} />
                <AdminMiniStat
                  label="分流方式"
                  value={routePreviewResult.routeStrategyType
                    ? gatewayRouteStrategyLabelMap[routePreviewResult.routeStrategyType as keyof typeof gatewayRouteStrategyLabelMap] || routePreviewResult.routeStrategyType
                    : "单通道"}
                />
                <AdminMiniStat label="服务商" value={routePreviewResult.providerDisplayName} />
                <AdminMiniStat label="模型" value={routePreviewResult.model} />
                <AdminMiniStat
                  label="内容模板"
                  value={routePreviewResult.promptTemplateName
                    ? `${routePreviewResult.promptTemplateName}${routePreviewResult.promptTemplateVersionNo ? ` v${routePreviewResult.promptTemplateVersionNo}` : ""}`
                    : "使用系统默认内容"}
                />
                <AdminMiniStat label="响应模式" value={getLabel(routePreviewResult.executionMode, gatewayExecutionModeLabelMap, routePreviewResult.executionMode)} />
                <AdminMiniStat
                  label="思考配置"
                  value={formatThinkingSummary(
                    routePreviewResult.reasoningEffort,
                    routePreviewResult.thinkingBudget,
                    routePreviewResult.thinkingLevel,
                    null,
                  ) || "沿用系统默认"}
                />
                <AdminMiniStat label="输入单价" value={`${formatCny(routePreviewResult.costPer1kInput, 6)} / 1K`} />
                <AdminMiniStat label="输出单价" value={`${formatCny(routePreviewResult.costPer1kOutput, 6)} / 1K`} />
              </div>
              <div className="rounded-2xl bg-slate-50 px-4 py-4 text-sm leading-6 text-slate-600">
                当前将通过 <span className="font-semibold text-slate-900">{routePreviewResult.providerDisplayName}</span> 提供
                <span className="font-semibold text-slate-900"> {routePreviewResult.model} </span>
                服务，{routePreviewResult.promptTemplateName
                  ? `并使用模板 ${routePreviewResult.promptTemplateName}${routePreviewResult.promptTemplateVersionNo ? ` v${routePreviewResult.promptTemplateVersionNo}` : ""}。`
                  : "并沿用系统默认内容。"}
                当前思考策略来源 {routePreviewResult.thinkingSource || "SYSTEM_DEFAULT"}。
              </div>
            </div>
          )}
        </div>
      </Drawer>

      <Modal
        open={providerEditorOpen}
        width={1100}
        destroyOnClose
        title={editingProvider ? `维护渠道 · ${editingProvider.displayName}` : "新增渠道"}
        okText="保存"
        cancelText="取消"
        confirmLoading={providerSaving}
        onOk={() => void handleSaveProvider()}
        onCancel={() => setProviderEditorOpen(false)}
      >
        <Form form={providerForm} layout="vertical">
          <div className="grid gap-4 md:grid-cols-2">
            <Form.Item label="渠道编码" name="providerCode" rules={[{ required: true, message: "请输入渠道编码" }]}>
              <Input placeholder="例如：GEMINI_MAIN" />
            </Form.Item>
            <Form.Item label="渠道类型" name="providerType" rules={[{ required: true, message: "请选择渠道类型" }]}>
              <Select
                options={meta.providerTypes.map((item: any) => ({
                  label: getMappedOptionLabel(item.code, gatewayProviderTypeLabelMap, item.label),
                  value: item.code,
                }))}
              />
            </Form.Item>
            <Form.Item label="显示名称" name="displayName" rules={[{ required: true, message: "请输入显示名称" }]}>
              <Input placeholder="例如：Google Gemini 主渠道" />
            </Form.Item>
            <Form.Item label="上游地址" name="baseUrl" rules={[{ required: true, message: "请输入上游地址" }]}>
              <Input placeholder="https://generativelanguage.googleapis.com" />
            </Form.Item>
            <Form.Item label="渠道密钥" name="apiKey">
              <Input.Password placeholder={editingProvider?.hasApiKey ? "留空则保持当前密钥不变" : "请输入渠道密钥"} />
            </Form.Item>
            <Form.Item label="额外配置 JSON" name="extraConfigJson">
              <TextArea rows={3} placeholder='{"region":"asia-east1"}' />
            </Form.Item>
            <Form.Item label="超时（毫秒）" name="timeoutMs" rules={[{ required: true, message: "请输入超时" }]}>
              <InputNumber className="w-full" min={1000} max={120000} />
            </Form.Item>
            <Form.Item label="最大重试次数" name="maxRetries" rules={[{ required: true, message: "请输入重试次数" }]}>
              <InputNumber className="w-full" min={0} max={10} />
            </Form.Item>
            <Form.Item label="默认输入单价（元 / 1K）" name="costPer1kInput" rules={[{ required: true, message: "请输入输入单价" }]}>
              <Input placeholder="0.0000" />
            </Form.Item>
            <Form.Item label="默认输出单价（元 / 1K）" name="costPer1kOutput" rules={[{ required: true, message: "请输入输出单价" }]}>
              <Input placeholder="0.0000" />
            </Form.Item>
            <Form.Item label="启用渠道" name="enabled" valuePropName="checked">
              <Switch />
            </Form.Item>
          </div>

          <Form.List name="models">
            {(fields, { add, remove }) => (
              <div className="mt-4 space-y-4">
                <div className="flex items-center justify-between">
                  <div className="text-base font-semibold text-slate-900">模型清单</div>
                  <Button
                    className="!rounded-xl !border-slate-200"
                    onClick={() => add({
                      modelCode: "",
                      displayName: "",
                      enabled: true,
                      inputCostPer1k: "0",
                      outputCostPer1k: "0",
                      supportedTaskTypes: [],
                    })}
                  >
                    <Plus size={14} className="mr-1" />
                    添加模型
                  </Button>
                </div>

                {fields.map((field) => (
                  <div key={field.key} className="rounded-[24px] border border-slate-200 bg-slate-50 px-4 py-4">
                    <div className="mb-4 flex items-center justify-between">
                      <div className="text-sm font-semibold text-slate-900">模型 #{field.name + 1}</div>
                      <Button danger type="link" className="!px-0" onClick={() => remove(field.name)}>
                        删除
                      </Button>
                    </div>
                    <div className="grid gap-4 md:grid-cols-2">
                      <Form.Item label="模型 ID" name={[field.name, "modelCode"]} rules={[{ required: true, message: "请输入模型 ID" }]}>
                        <Input placeholder="gemini-2.5-flash" />
                      </Form.Item>
                      <Form.Item label="显示名称" name={[field.name, "displayName"]} rules={[{ required: true, message: "请输入显示名称" }]}>
                        <Input placeholder="Gemini 2.5 Flash" />
                      </Form.Item>
                      <Form.Item label="输入单价（元 / 1K）" name={[field.name, "inputCostPer1k"]} rules={[{ required: true, message: "请输入输入单价" }]}>
                        <Input placeholder="0.0000" />
                      </Form.Item>
                      <Form.Item label="输出单价（元 / 1K）" name={[field.name, "outputCostPer1k"]} rules={[{ required: true, message: "请输入输出单价" }]}>
                        <Input placeholder="0.0000" />
                      </Form.Item>
                      <Form.Item label="上下文长度" name={[field.name, "contextWindow"]}>
                        <InputNumber className="w-full" min={1} max={10000000} />
                      </Form.Item>
                      <Form.Item label="最大输出 Tokens" name={[field.name, "maxOutputTokens"]}>
                        <InputNumber className="w-full" min={1} max={10000000} />
                      </Form.Item>
                      <Form.Item label="适用能力" name={[field.name, "supportedTaskTypes"]}>
                        <Select mode="multiple" options={taskTypeOptions} placeholder="可不填，表示全能力可用" />
                      </Form.Item>
                      <Form.Item label="启用模型" name={[field.name, "enabled"]} valuePropName="checked">
                        <Switch />
                      </Form.Item>
                      <Form.Item label="备注" name={[field.name, "notes"]} className="md:col-span-2">
                        <TextArea rows={2} placeholder="例如：VIP 优先、上下文更长、TTS 专用" />
                      </Form.Item>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </Form.List>
        </Form>
      </Modal>

      <Modal
        open={Boolean(connectivityTarget)}
        width={720}
        destroyOnClose
        footer={null}
        title={connectivityTarget ? `渠道连通性检测 · ${connectivityTarget.displayName}` : "渠道连通性检测"}
        onCancel={() => {
          setConnectivityTarget(null);
          setConnectivityResult(null);
        }}
      >
        {connectivityLoading ? (
          <div className="rounded-2xl bg-slate-50 px-4 py-8 text-sm text-slate-500">正在检测渠道连通性...</div>
        ) : connectivityResult ? (
          <div className="space-y-4">
            <div className="flex flex-wrap gap-2">
              {connectivityResult.available ? <Tag color="success">可用</Tag> : <Tag color="error">不可用</Tag>}
              {connectivityResult.reachable ? <Tag color="success">可访问</Tag> : <Tag color="error">不可访问</Tag>}
              {connectivityResult.authenticated ? <Tag color="success">鉴权通过</Tag> : <Tag color="warning">鉴权未通过</Tag>}
              {getRuntimeStatusTag(connectivityResult.status)}
            </div>
            <div className="grid grid-cols-2 gap-3">
              <AdminMiniStat label="HTTP 状态" value={connectivityResult.httpStatus ?? "—"} />
              <AdminMiniStat label="延迟" value={`${connectivityResult.latencyMs} ms`} />
              <AdminMiniStat label="检测时间" value={formatDateTime(connectivityResult.checkedAt)} />
              <AdminMiniStat label="探测地址" value={connectivityResult.probeUrl || connectivityResult.baseUrl} />
            </div>
            <div className="rounded-2xl bg-slate-50 px-4 py-4 text-sm leading-6 text-slate-600">
              {connectivityResult.message}
            </div>
          </div>
        ) : (
          <AdminDetailPlaceholder description="当前没有检测结果" />
        )}
      </Modal>

      <Modal
        open={policyEditorOpen}
        width={640}
        destroyOnClose
        title={editingPolicy ? `维护方案 · ${editingPolicy.policyCode}` : `创建方案 · ${policySeedScene?.displayName || ""}`}
        okText="保存"
        cancelText="取消"
        confirmLoading={policySaving}
        onOk={() => void handleSavePolicy()}
        onCancel={() => setPolicyEditorOpen(false)}
      >
        <Form
          form={policyForm}
          layout="vertical"
          onValuesChange={(changedValues) => {
            if (!editingPolicy && policySeedScene && changedValues.userTier) {
              policyForm.setFieldValue("policyCode", buildDefaultPolicyCode(policySeedScene, changedValues.userTier));
            }
          }}
        >
          <Form.Item label="方案编码（可按层级自动生成）" name="policyCode" rules={[{ required: true, message: "请输入方案编码" }]}>
            <Input placeholder="例如：INTERVIEW_TEXT_INTERVIEW_SESSION_FREE" />
          </Form.Item>
          <div className="grid gap-4 md:grid-cols-2">
            <Form.Item label="任务类型" name="taskType" rules={[{ required: true, message: "请选择任务类型" }]}>
              <Select options={taskTypeOptions} disabled />
            </Form.Item>
            <Form.Item label="场景编码" name="sceneCode" rules={[{ required: true, message: "请输入场景编码" }]}>
              <Input disabled />
            </Form.Item>
            <Form.Item label="用户层级" name="userTier" rules={[{ required: true, message: "请选择用户层级" }]}>
              <Select options={tierCardOrder.map((tier: string) => ({ label: gatewayTierLabelMap[tier as keyof typeof gatewayTierLabelMap], value: tier }))} />
            </Form.Item>
            <Form.Item label="分流方式" name="strategyType" rules={[{ required: true, message: "请选择分流方式" }]}>
              <Select
                options={
                  meta.routeStrategyTypes.length > 0
                    ? meta.routeStrategyTypes.map((item: any) => ({
                      label: gatewayRouteStrategyLabelMap[item.code as keyof typeof gatewayRouteStrategyLabelMap] || item.label || item.code,
                      value: item.code,
                    }))
                    : Object.entries(gatewayRouteStrategyLabelMap).map(([value, label]) => ({ value, label }))
                }
              />
            </Form.Item>
          </div>
          <div className="rounded-[24px] border border-slate-200 bg-slate-50 px-4 py-4">
            <div className="text-sm font-semibold text-slate-900">思考策略配置</div>
            <div className="mt-1 text-xs leading-6 text-slate-500">
              这里按场景与用户层级配置默认思考策略。通常优先只配“思考强度”，需要精细控成本时再补预算，只有模型原生要求时再填层级。
            </div>
            <div className="mt-4 grid gap-4 md:grid-cols-3">
              <Form.Item label="思考强度（推荐配置）" name="reasoningEffort" className="!mb-0">
                <Select allowClear options={thinkingEffortOptions} placeholder="优先配置这一项" />
              </Form.Item>
              <Form.Item label="思考预算（精细控成本）" name="thinkingBudget" className="!mb-0">
                <InputNumber className="w-full" min={0} max={1000000} placeholder="需要控成本时再填，例如 1024" />
              </Form.Item>
              <Form.Item label="思考层级（模型原生）" name="thinkingLevel" className="!mb-0">
                <AutoComplete options={thinkingLevelOptions} placeholder="仅在模型原生需要时填写" />
              </Form.Item>
            </div>
          </div>
          <Form.Item label="备注" name="notes">
            <TextArea rows={3} placeholder="例如：VIP 优先走高质量模型，普通层级保留通用回退" />
          </Form.Item>
          <Form.Item label="启用方案" name="enabled" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={candidateEditorOpen}
        width={860}
        destroyOnClose
        title={editingCandidate ? `维护备选方案 · ${editingCandidate.routeCode}` : `添加备选方案 · ${candidateParentPolicy?.policyCode || ""}`}
        okText="保存"
        cancelText="取消"
        confirmLoading={candidateSaving}
        onOk={() => void handleSaveCandidate()}
        onCancel={() => setCandidateEditorOpen(false)}
      >
        <Form form={candidateForm} layout="vertical">
          <div className="grid gap-4 md:grid-cols-2">
            <Form.Item label="任务类型" name="taskType" rules={[{ required: true, message: "请选择任务类型" }]}>
              <Select options={taskTypeOptions} disabled />
            </Form.Item>
            <Form.Item label="场景编码" name="sceneCode" rules={[{ required: true, message: "请输入场景编码" }]}>
              <Input disabled />
            </Form.Item>
            <Form.Item label="服务商" name="providerConfigId" rules={[{ required: true, message: "请选择服务商" }]}>
              <Select options={providerOptions} />
            </Form.Item>
            <Form.Item label="模型 ID" name="modelName" rules={[{ required: true, message: "请选择模型" }]}>
              <Select options={candidateModelOptions} />
            </Form.Item>
            <Form.Item label="优先级" name="priorityNo" rules={[{ required: true, message: "请输入优先级" }]}>
              <InputNumber className="w-full" min={1} max={1000} />
            </Form.Item>
            <Form.Item label="分配权重" name="candidateWeight" rules={[{ required: true, message: "请输入分配权重" }]}>
              <InputNumber className="w-full" min={1} max={1000} />
            </Form.Item>
            <Form.Item label="执行模式" name="executionMode" rules={[{ required: true, message: "请选择执行模式" }]}>
              <Select options={meta.executionModes.map((item: any) => ({ label: getMappedOptionLabel(item.code, gatewayExecutionModeLabelMap, item.label), value: item.code }))} />
            </Form.Item>
            <Form.Item label="绑定模板" name="promptTemplateName">
              <Select allowClear options={templateOptions} placeholder="不填则使用系统默认内容" />
            </Form.Item>
            <Form.Item label="启用方案" name="enabled" valuePropName="checked">
              <Switch />
            </Form.Item>
          </div>
          <div className="rounded-[24px] border border-slate-200 bg-slate-50 px-4 py-4">
            <div className="mb-4 flex items-center justify-between gap-3">
              <div>
                <div className="text-sm font-semibold text-slate-900">高级设置</div>
                <div className="mt-1 text-xs leading-6 text-slate-500">策略层已经支持正式思考量配置，这里只保留 route 级高级覆写。</div>
              </div>
              <Button className="!rounded-xl !border-slate-200" onClick={handleGenerateRouteCode}>
                自动生成编码
              </Button>
            </div>
            <div className="grid gap-4 md:grid-cols-2">
              <Form.Item label="方案编码（可自动生成）" name="routeCode" rules={[{ required: true, message: "请输入方案编码" }]}>
                <Input placeholder="例如：INTERVIEW_SESSION_FREE_GEMINI_2_5_FLASH" />
              </Form.Item>
              <Form.Item label="温度" name="temperature" rules={[{ required: true, message: "请输入温度" }]}>
                <Input placeholder="0.2" />
              </Form.Item>
            </div>
            <Form.Item label="补充说明" name="systemPrompt">
              <TextArea rows={3} placeholder="需要补充场景偏好或输出边界时再填写" />
            </Form.Item>
            <Form.Item className="!mb-0" label="额外配置 JSON" name="extraConfigJson">
              <TextArea rows={4} placeholder='{"thinking":{"reasoningEffort":"HIGH"}}' />
            </Form.Item>
          </div>
        </Form>
      </Modal>

      <Modal
        open={templateEditorOpen}
        width={900}
        destroyOnClose
        title={selectedTemplate ? `编辑模板 · ${selectedTemplate.templateName} v${selectedTemplate.versionNo}` : "编辑模板"}
        okText="保存"
        cancelText="取消"
        confirmLoading={templateEditorSaving}
        onOk={() => void handleSaveTemplate()}
        onCancel={() => setTemplateEditorOpen(false)}
      >
        <Form form={templateEditForm} layout="vertical">
          <Form.Item label="说明" name="description">
            <TextArea rows={2} placeholder="说明当前模板适用的业务语境与注意事项" />
          </Form.Item>
          <Form.Item label="模板正文" name="content" rules={[{ required: true, message: "请输入模板正文" }]}>
            <TextArea rows={14} />
          </Form.Item>
          <Form.Item label="消息包 JSON" name="bundleJson">
            <TextArea rows={6} placeholder='[{"role":"system","content":"..."}]' />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        open={logDrawerOpen}
        width={760}
        destroyOnClose={false}
        title={selectedLog ? `调用记录 · ${selectedLog.traceId}` : "调用记录"}
        onClose={() => setLogDrawerOpen(false)}
      >
        {!selectedLog ? (
          <AdminDetailPlaceholder description="请选择一条日志查看详情" />
        ) : logDetailLoading ? (
          <div className="rounded-2xl bg-slate-50 px-4 py-8 text-sm text-slate-500">正在加载日志详情...</div>
        ) : logDetailError ? (
          <Alert type="error" showIcon className="rounded-2xl" message={logDetailError} />
        ) : selectedLogDetail ? (
          <div className="space-y-4">
            <div className="rounded-[24px] bg-gradient-to-br from-[#111827] via-[#1e293b] to-[#334155] px-5 py-5 text-white">
              <div className="text-[11px] font-bold tracking-[0.18em] text-white/60">调用编号</div>
              <div className="mt-3 font-mono text-sm text-white/88">{selectedLogDetail.traceId}</div>
              <div className="mt-3 flex flex-wrap gap-2">
                {getLogStatusTag(selectedLogDetail.status)}
                <Tag color="white" style={{ color: "#0f172a", border: "none" }}>
                  {getLabel(selectedLogDetail.taskType, gatewayTaskTypeLabelMap, selectedLogDetail.taskType)}
                </Tag>
                <Tag color="white" style={{ color: "#0f172a", border: "none" }}>
                  {selectedLogDetail.userTier ? gatewayTierLabelMap[selectedLogDetail.userTier as keyof typeof gatewayTierLabelMap] || selectedLogDetail.userTier : "未知层级"}
                </Tag>
                {selectedLogDetail.errorCode ? (
                  <Tag color="error">{selectedLogDetail.errorCode}</Tag>
                ) : null}
              </div>
              <div className="mt-4 grid gap-3 md:grid-cols-2">
                <div className="rounded-2xl bg-white/10 px-4 py-3">
                  <div className="text-[11px] text-white/60">关联用户</div>
                  <div className="mt-1 text-sm font-semibold text-white">
                    {selectedLogDetail.userDisplayName || `用户 #${selectedLogDetail.userId}`}
                  </div>
                  <div className="mt-1 text-xs text-white/72">{selectedLogDetail.userEmail || "未记录邮箱"}</div>
                </div>
                <div className="rounded-2xl bg-white/10 px-4 py-3">
                  <div className="text-[11px] text-white/60">创建时间</div>
                  <div className="mt-1 text-sm font-semibold text-white">{formatDateTime(selectedLogDetail.createdAt)}</div>
                  <div className="mt-1 text-xs text-white/72">
                    场景 {selectedLogDetail.sceneCode || "未记录"} · 用户编号 {selectedLogDetail.userId}
                  </div>
                </div>
              </div>
            </div>

            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
              <AdminMiniStat label="输入令牌" value={formatCount(selectedLogDetail.requestTokens)} />
              <AdminMiniStat label="输出令牌" value={formatCount(selectedLogDetail.responseTokens)} />
              <AdminMiniStat label="思考令牌" value={formatCount(selectedLogDetail.thoughtsTokens)} />
              <AdminMiniStat label="总令牌" value={formatCount(selectedLogDetail.totalTokens)} />
              <AdminMiniStat label="场景编码" value={selectedLogDetail.sceneCode || "未记录"} />
              <AdminMiniStat label="服务商" value={selectedLogDetail.provider} />
              <AdminMiniStat label="模型" value={selectedLogDetail.model} />
              <AdminMiniStat label="耗时" value={`${selectedLogDetail.latencyMs} ms`} />
              <AdminMiniStat label="预估成本" value={formatCny(selectedLogDetail.estimatedCost, 4)} />
              <AdminMiniStat label="积分扣减" value={formatCount(selectedLogDetail.chargedPoints)} />
              <AdminMiniStat label="配额权重" value={formatCount(selectedLogDetail.quotaWeight)} />
              <AdminMiniStat
                label="本次思考配置"
                value={formatThinkingSummary(
                  selectedLogDetail.reasoningEffort,
                  selectedLogDetail.thinkingBudget,
                  selectedLogDetail.thinkingLevel,
                  selectedLogDetail.thoughtsTokens,
                ) || "未上报"}
              />
            </div>

            <div className="rounded-2xl bg-slate-50 px-4 py-4 text-sm leading-6 text-slate-600">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="text-sm font-semibold text-slate-900">治理留痕</div>
                <Tag color={selectedLogDetail.governanceTraceSummary.auditCount > 0 ? "processing" : "default"}>
                  {selectedLogDetail.governanceTraceSummary.auditCount > 0
                    ? `审计 ${formatCount(selectedLogDetail.governanceTraceSummary.auditCount)} 条`
                    : "暂无治理动作"}
                </Tag>
              </div>
              <div className="mt-3 flex flex-wrap gap-2">
                {selectedLogDetail.governanceTraceSummary.recentActionTypes.length > 0 ? (
                  selectedLogDetail.governanceTraceSummary.recentActionTypes.map((actionType: string) => (
                    <Tag key={actionType}>{getLabel(actionType, auditActionLabelMap, actionType)}</Tag>
                  ))
                ) : (
                  <Text type="secondary" className="text-sm">当前追踪编号还没有关联治理动作。</Text>
                )}
              </div>
              <div className="mt-3 text-xs text-slate-500">
                最近治理时间 {formatDateTime(selectedLogDetail.governanceTraceSummary.latestAuditAt)}
              </div>
            </div>

            <div className="rounded-2xl bg-slate-50 px-4 py-4 text-sm leading-6 text-slate-600">
              <div className="text-sm font-semibold text-slate-900">当前链路快照</div>
              {selectedLogDetail.currentRouteSnapshot ? (
                <>
                  <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
                    <AdminMiniStat label="方案编码" value={selectedLogDetail.currentRouteSnapshot.routeCode} />
                    <AdminMiniStat label="分流分组" value={selectedLogDetail.currentRouteSnapshot.routePolicyCode || "未返回"} />
                    <AdminMiniStat
                      label="服务层级"
                      value={selectedLogDetail.currentRouteSnapshot.routePolicyUserTier
                        ? gatewayTierLabelMap[selectedLogDetail.currentRouteSnapshot.routePolicyUserTier as keyof typeof gatewayTierLabelMap]
                          || selectedLogDetail.currentRouteSnapshot.routePolicyUserTier
                        : "通用"}
                    />
                    <AdminMiniStat
                      label="分流方式"
                      value={selectedLogDetail.currentRouteSnapshot.routeStrategyType
                        ? gatewayRouteStrategyLabelMap[selectedLogDetail.currentRouteSnapshot.routeStrategyType as keyof typeof gatewayRouteStrategyLabelMap]
                          || selectedLogDetail.currentRouteSnapshot.routeStrategyType
                        : "未返回"}
                    />
                    <AdminMiniStat
                      label="执行模式"
                      value={getLabel(
                        selectedLogDetail.currentRouteSnapshot.executionMode,
                        gatewayExecutionModeLabelMap,
                        selectedLogDetail.currentRouteSnapshot.executionMode,
                      )}
                    />
                    <AdminMiniStat
                      label="服务商类型"
                      value={getLabel(
                        selectedLogDetail.currentRouteSnapshot.providerType,
                        gatewayProviderTypeLabelMap,
                        selectedLogDetail.currentRouteSnapshot.providerType,
                      )}
                    />
                    <AdminMiniStat label="服务商" value={selectedLogDetail.currentRouteSnapshot.providerDisplayName} />
                    <AdminMiniStat label="模型" value={selectedLogDetail.currentRouteSnapshot.model} />
                    <AdminMiniStat
                      label="内容模板"
                      value={selectedLogDetail.currentRouteSnapshot.promptTemplateName
                        ? `${selectedLogDetail.currentRouteSnapshot.promptTemplateName}${selectedLogDetail.currentRouteSnapshot.promptTemplateVersionNo ? ` v${selectedLogDetail.currentRouteSnapshot.promptTemplateVersionNo}` : ""}`
                        : "使用系统默认内容"}
                    />
                    <AdminMiniStat
                      label="链路思考配置"
                      value={formatThinkingSummary(
                        selectedLogDetail.currentRouteSnapshot.reasoningEffort,
                        selectedLogDetail.currentRouteSnapshot.thinkingBudget,
                        selectedLogDetail.currentRouteSnapshot.thinkingLevel,
                        null,
                      ) || "未设置"}
                    />
                  </div>
                  <div className="mt-4 rounded-2xl bg-white px-4 py-4 text-sm leading-6 text-slate-600">
                    <div>
                      当前价格口径：输入 {formatCny(selectedLogDetail.currentRouteSnapshot.costPer1kInput, 6)} / 输出 {formatCny(selectedLogDetail.currentRouteSnapshot.costPer1kOutput, 6)} / 1K
                      {selectedLogDetail.currentRouteSnapshot.costCurrency ? `（${getCurrencyLabel(selectedLogDetail.currentRouteSnapshot.costCurrency)}）` : ""}。
                    </div>
                    <div className="mt-2">
                      思考配置来源：{selectedLogDetail.currentRouteSnapshot.thinkingSource || "未返回"}。
                    </div>
                  </div>
                </>
              ) : (
                <div className="mt-4 rounded-2xl bg-white px-4 py-4 text-sm text-slate-500">
                  当前未找到对应方案。
                </div>
              )}
            </div>

            <div className="rounded-2xl bg-slate-50 px-4 py-4 text-sm leading-6 text-slate-600">
              <div className="text-sm font-semibold text-slate-900">结果摘要</div>
              <Paragraph className="!mb-0 !mt-3 whitespace-pre-wrap !text-sm !leading-7 !text-slate-600">
                {selectedLogDetail.resultSummary || "暂无结果摘要。"}
              </Paragraph>
            </div>

            {selectedLogDetail.resultPayloadJson ? (
              <div className="rounded-2xl bg-slate-50 px-4 py-4 text-sm leading-6 text-slate-600">
                <div className="text-sm font-semibold text-slate-900">原始结果载荷</div>
                <pre className="mt-3 max-h-[360px] overflow-auto rounded-2xl bg-white px-4 py-4 text-xs leading-6 text-slate-700">
                  {safePrettifyJson(selectedLogDetail.resultPayloadJson)}
                </pre>
              </div>
            ) : null}

            <div className="flex flex-wrap gap-3">
              <Button className="!h-11 !rounded-2xl !border-slate-200" onClick={() => handleLocateRouteFromLog(selectedLogDetail)}>
                定位当前方案
              </Button>
              <Button className="!h-11 !rounded-2xl !border-slate-200" onClick={() => handleLocateTemplateFromLog(selectedLogDetail)}>
                定位当前模板
              </Button>
              <Button
                className="!h-11 !rounded-2xl !border-slate-200"
                onClick={() => applyLogFilters({
                  taskType: selectedLogDetail.taskType,
                  sceneCode: selectedLogDetail.sceneCode,
                })}
              >
                查看同场景日志
              </Button>
              <Button
                className="!h-11 !rounded-2xl !border-slate-200"
                onClick={() => applyLogFilters({ traceId: selectedLogDetail.traceId })}
              >
                仅看本次调用
              </Button>
              <Button className="!h-11 !rounded-2xl !border-slate-200" onClick={() => navigate(`/admin/content?tab=audit&traceId=${encodeURIComponent(selectedLogDetail.traceId)}`)}>
                查看内容治理
              </Button>
              <Button className="!h-11 !rounded-2xl !border-slate-200" onClick={() => navigate(`/admin/users/${selectedLogDetail.userId}`)}>
                查看关联用户
              </Button>
            </div>
          </div>
        ) : (
          <AdminDetailPlaceholder description="当前日志暂无可展示详情" />
        )}
      </Drawer>
    </>
  );
}
