import { Alert, Button, Drawer, Form, Input, Modal, Select, Tag, Typography } from "antd";
import { Eye } from "lucide-react";
import {
  AdminDetailPlaceholder,
  AdminMiniStat,
  AdminSettingSwitch,
} from "../AdminOpsPrimitives";
import { getLabel, getReadableCodeLabel, moderationActionLabelMap, riskLevelLabelMap, sensitiveTermTypeLabelMap, sourceTypeLabelMap, targetTypeLabelMap } from "../../../lib/adminLabels";
import type { AdminWorkspaceModuleProps } from "../AdminLazyWorkspace";

const { TextArea } = Input;
const { Paragraph } = Typography;

export default function AdminContentModerationOverlays({ context }: AdminWorkspaceModuleProps<any>) {
  const {
    activeTab,
    reportDrawerOpen,
    setReportDrawerOpen,
    selectedReport,
    reportDetailLoading,
    reportDetail,
    reportActions,
    getTargetTypeTag,
    getReportStatusTag,
    getRiskTagColor,
    formatCount,
    getDisplayText,
    selectedReportTargetId,
    selectedReportTargetType,
    selectedReportContentPostId,
    getReportContextHint,
    selectedReportContextHref,
    getReportContextActionLabel,
    navigate,
    selectedReportTargetUserId,
    reportDecisionForm,
    setReportDecisionOpen,
    reviewDrawerOpen,
    setReviewDrawerOpen,
    selectedReview,
    reviewDetailLoading,
    reviewDetail,
    reviewDecisionForm,
    setReviewDecisionOpen,
    auditDrawerOpen,
    setAuditDrawerOpen,
    selectedAuditLog,
    selectedAuditDetailObject,
    auditDetailLongTextKeys,
    auditDetailFieldLabelMap,
    formatAuditDetailValue,
    safePrettifyJson,
    selectedAuditTargetUserId,
    handleOpenAiTraceModal,
    termDrawerOpen,
    setTermDrawerOpen,
    focusedTerm,
    openTermEditor,
    reportDecisionOpen,
    handleSubmitReportDecision,
    reportDecisionSaving,
    reportDecisionOptions,
    reportActionOptions,
    reviewDecisionOpen,
    handleSubmitReviewDecision,
    reviewDecisionSaving,
    reviewDecisionOptions,
    termImportConfigOpen,
    closeTermImportConfig,
    handleConfirmPlainTextImport,
    termImporting,
    pendingImportFile,
    termImportConfigForm,
    termImportConfigInitialValues,
    termTypeOptions,
    sensitiveRiskOptions,
    sensitiveActionOptions,
    sourceScopeOptions,
    termEditorOpen,
    editingTerm,
    closeTermEditor,
    termEditForm,
    termEditInitialValues,
    termSaving,
    termEditSaving,
    handleUpdateTerm,
    handleCreateTerm,
    formatDateTime,
  } = context;

  return (
    <>
      <Drawer
        title="举报详情"
        placement="right"
        width={560}
        open={activeTab === "reports" && reportDrawerOpen}
        onClose={() => setReportDrawerOpen(false)}
        destroyOnHidden={false}
        styles={{
          body: { padding: 24, background: "#f8fafc" },
        }}
      >
        {!selectedReport ? (
          <AdminDetailPlaceholder description="请选择一条举报查看详情" />
        ) : reportDetailLoading ? (
          <div className="rounded-2xl bg-white px-4 py-6 text-sm text-slate-500">正在加载举报详情...</div>
        ) : (
          <div className="space-y-5">
            <div className="rounded-[26px] bg-gradient-to-br from-[#111827] via-[#1f2937] to-[#be123c] p-6 text-white shadow-[0_18px_36px_rgba(15,23,42,0.16)]">
              <div className="font-['Manrope'] text-xl font-black tracking-tight text-white">
                {reportDetail?.contentTitle || `举报 #${selectedReport.reportId}`}
              </div>
              <div className="mt-3 flex flex-wrap gap-2">
                {getTargetTypeTag(reportDetail?.targetType || selectedReport.targetType)}
                {getReportStatusTag(reportDetail?.status || selectedReport.status)}
                {reportDetail?.targetRiskLevel ? <Tag color={getRiskTagColor(reportDetail.targetRiskLevel)}>{getLabel(reportDetail.targetRiskLevel, riskLevelLabelMap, reportDetail.targetRiskLevel)}</Tag> : null}
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <AdminMiniStat className="admin-content-mini-stat" label="举报次数" value={formatCount(reportDetail?.reportCount ?? selectedReport.reportCount)} />
              <AdminMiniStat className="admin-content-mini-stat" label="举报原因" value={getReadableCodeLabel(reportDetail?.reasonCode || selectedReport.reasonCode, context.moderationReasonLabelMap, reportDetail?.reasonCode || selectedReport.reasonCode)} />
              <AdminMiniStat className="admin-content-mini-stat" label="创建时间" value={formatDateTime(reportDetail?.createdAt || selectedReport.createdAt)} />
              <AdminMiniStat className="admin-content-mini-stat" label="最后更新" value={formatDateTime(reportDetail?.updatedAt || selectedReport.updatedAt)} />
            </div>

            <div className="rounded-[26px] border border-slate-200 bg-white px-4 py-4 shadow-[0_10px_28px_rgba(15,23,42,0.04)]">
              <div className="text-[11px] font-bold text-slate-400">原文与补充说明</div>
              <div className="mt-3 space-y-3">
                <div className="rounded-2xl bg-slate-50 px-4 py-4">
                  <div className="text-[11px] font-bold text-slate-400">原文</div>
                  <Paragraph className="!mb-0 !mt-3 !text-sm !leading-7 !text-slate-600">
                    {getDisplayText(reportDetail?.contentBody, "暂无正文摘要")}
                  </Paragraph>
                </div>
                <div className="rounded-2xl bg-slate-50 px-4 py-4">
                  <div className="text-[11px] font-bold text-slate-400">举报说明</div>
                  <Paragraph className="!mb-0 !mt-3 !text-sm !leading-7 !text-slate-500">
                    {getDisplayText(reportDetail?.reportDetail, "举报人未补充更多说明")}
                  </Paragraph>
                </div>
              </div>
            </div>

            <div className="rounded-[26px] border border-slate-200 bg-white px-4 py-4 shadow-[0_10px_28px_rgba(15,23,42,0.04)]">
              <div className="text-[11px] font-bold text-slate-400">对象信息与上下文</div>
              <div className="mt-3 space-y-3">
                <div className="rounded-2xl bg-slate-50 px-4 py-3">
                  <div className="text-[11px] font-bold text-slate-400">目标编号</div>
                  <div className="mt-2 font-mono text-sm text-slate-900">{getDisplayText(selectedReportTargetId)}</div>
                </div>
                <div className="rounded-2xl bg-slate-50 px-4 py-3">
                  <div className="text-[11px] font-bold text-slate-400">关联帖子</div>
                  <div className="mt-2 font-mono text-sm text-slate-900">
                    {getDisplayText(
                      selectedReportTargetType === "POST" ? selectedReportTargetId : selectedReportContentPostId,
                      "当前目标未返回帖子编号",
                    )}
                  </div>
                </div>
                <div className="rounded-2xl bg-slate-50 px-4 py-3">
                  <div className="text-[11px] font-bold text-slate-400">处置线索</div>
                  <div className="mt-2 text-sm leading-6 text-slate-600">
                    当前状态：{getReadableCodeLabel(reportDetail?.targetStatus, moderationActionLabelMap, getDisplayText(reportDetail?.targetStatus, "未返回"))}
                    <br />
                    最新动作：{getReadableCodeLabel(reportDetail?.latestAction || selectedReport.latestAction, moderationActionLabelMap, getDisplayText(reportDetail?.latestAction || selectedReport.latestAction, "尚未处置"))}
                  </div>
                </div>
                <div className="rounded-2xl border border-dashed border-slate-200 bg-slate-50/70 px-4 py-4 text-sm leading-6 text-slate-500">
                  {getReportContextHint(selectedReportTargetType, selectedReportTargetId, selectedReportContentPostId)}
                </div>
                {selectedReportContextHref ? (
                  <Button
                    block
                    className="!h-11 !rounded-2xl !border-slate-200"
                    href={selectedReportContextHref}
                    target="_blank"
                    rel="noreferrer"
                  >
                    <Eye size={15} className="mr-2" />
                    {getReportContextActionLabel(selectedReportTargetType)}
                  </Button>
                ) : null}
              </div>
            </div>

            <div className="rounded-[26px] border border-slate-200 bg-white px-4 py-4 shadow-[0_10px_28px_rgba(15,23,42,0.04)]">
              <div className="text-[11px] font-bold text-slate-400">处理历史</div>
              <div className="mt-3 space-y-3">
                {reportActions.length > 0 ? reportActions.map((item: any) => (
                  <div key={item.actionId} className="rounded-2xl bg-slate-50 px-4 py-3">
                    <div className="flex flex-wrap items-center gap-2">
                      <Tag>{getReadableCodeLabel(item.decision, moderationActionLabelMap, item.decision)}</Tag>
                      <Tag color="processing">{getReadableCodeLabel(item.action, moderationActionLabelMap, item.action)}</Tag>
                    </div>
                    <div className="mt-2 text-sm text-slate-900">{item.operatorDisplayName}</div>
                    <div className="mt-1 text-xs text-slate-400">{formatDateTime(item.createdAt)}</div>
                    {item.comment ? <div className="mt-2 text-sm leading-6 text-slate-500">{item.comment}</div> : null}
                  </div>
                )) : (
                  <div className="rounded-2xl bg-slate-50 px-4 py-3 text-sm text-slate-500">当前没有历史处置记录。</div>
                )}
              </div>
            </div>

            <div className="space-y-3">
              {reportDetail?.reporterUserId ? (
                <Button block className="!h-11 !rounded-2xl !border-slate-200" onClick={() => navigate(`/admin/users/${reportDetail.reporterUserId}`)}>
                  举报人详情
                </Button>
              ) : null}
              {selectedReportTargetUserId ? (
                <Button block className="!h-11 !rounded-2xl !border-slate-200" onClick={() => navigate(`/admin/users/${selectedReportTargetUserId}`)}>
                  查看目标用户
                </Button>
              ) : null}
              <Button
                block
                type="primary"
                className="!h-11 !rounded-2xl !border-none !bg-rose-600 !shadow-none"
                onClick={() => {
                  reportDecisionForm.setFieldsValue({
                    decision: selectedReport.status === "PENDING" ? "ACCEPTED" : "CLOSED",
                    action: selectedReport.status === "PENDING" ? "TAKE_DOWN" : "NO_ACTION",
                    comment: "",
                  });
                  setReportDecisionOpen(true);
                }}
              >
                处置当前举报
              </Button>
            </div>
          </div>
        )}
      </Drawer>

      <Drawer
        title="待审详情"
        placement="right"
        width={560}
        open={activeTab === "review" && reviewDrawerOpen}
        onClose={() => setReviewDrawerOpen(false)}
        destroyOnHidden={false}
        styles={{
          body: { padding: 24, background: "#f8fafc" },
        }}
      >
        {!selectedReview ? (
          <AdminDetailPlaceholder description="请选择一条待审内容查看详情" />
        ) : reviewDetailLoading ? (
          <div className="rounded-2xl bg-white px-4 py-6 text-sm text-slate-500">正在加载待审详情...</div>
        ) : (
          <div className="space-y-5">
            <div className="rounded-[26px] bg-gradient-to-br from-[#7c2d12] via-[#b45309] to-[#d97706] p-6 text-white shadow-[0_18px_36px_rgba(146,64,14,0.16)]">
              <div className="font-['Manrope'] text-xl font-black tracking-tight text-white">
                {reviewDetail?.contentTitle || `待审项 #${selectedReview.itemId}`}
              </div>
              <div className="mt-3 flex flex-wrap gap-2">
                <Tag color="white" style={{ color: "#b45309", border: "none" }}>
                  {getReadableCodeLabel(reviewDetail?.sourceType || selectedReview.sourceType, sourceTypeLabelMap, reviewDetail?.sourceType || selectedReview.sourceType)}
                </Tag>
                <Tag color={getRiskTagColor(reviewDetail?.riskLevel || selectedReview.riskLevel)}>
                  {getLabel(reviewDetail?.riskLevel || selectedReview.riskLevel, riskLevelLabelMap, reviewDetail?.riskLevel || selectedReview.riskLevel)}
                </Tag>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <AdminMiniStat className="admin-content-mini-stat" label="目标对象" value={`${getReadableCodeLabel(reviewDetail?.targetType || selectedReview.targetType, targetTypeLabelMap, reviewDetail?.targetType || selectedReview.targetType)} · ${reviewDetail?.targetId || selectedReview.targetId}`} />
              <AdminMiniStat className="admin-content-mini-stat" label="命中原因" value={getReadableCodeLabel(reviewDetail?.reasonCode || selectedReview.reasonCode, context.moderationReasonLabelMap, reviewDetail?.reasonCode || selectedReview.reasonCode)} />
              <AdminMiniStat className="admin-content-mini-stat" label="创建时间" value={formatDateTime(reviewDetail?.createdAt || selectedReview.createdAt)} />
              <AdminMiniStat className="admin-content-mini-stat" label="关联帖子" value={getDisplayText(reviewDetail?.postTitle || reviewDetail?.postId, "—")} />
            </div>

            <div className="rounded-[26px] border border-slate-200 bg-white px-4 py-4 shadow-[0_10px_28px_rgba(15,23,42,0.04)]">
              <div className="text-[11px] font-bold text-slate-400">内容预览</div>
              <Paragraph className="!mb-0 !mt-3 !text-sm !leading-7 !text-slate-600">
                {getDisplayText(reviewDetail?.contentBody || reviewDetail?.preview || selectedReview.preview, "暂无内容")}
              </Paragraph>
              {reviewDetail?.postBody ? (
                <div className="mt-4 rounded-2xl bg-slate-50 px-4 py-4 text-sm leading-6 text-slate-500">
                  上下文：{reviewDetail.postBody}
                </div>
              ) : null}
            </div>

            <div className="space-y-3">
              {reviewDetail?.authorUserId ? (
                <Button block className="!h-11 !rounded-2xl !border-slate-200" onClick={() => navigate(`/admin/users/${reviewDetail.authorUserId}`)}>
                  查看作者详情
                </Button>
              ) : null}
              <Button
                block
                type="primary"
                className="!h-11 !rounded-2xl !border-none !bg-amber-600 !shadow-none"
                onClick={() => {
                  reviewDecisionForm.setFieldsValue({ decision: "APPROVE", comment: "" });
                  setReviewDecisionOpen(true);
                }}
              >
                审核当前待审项
              </Button>
            </div>
          </div>
        )}
      </Drawer>

      <Drawer
        title="日志详情"
        placement="right"
        width={560}
        open={activeTab === "audit" && auditDrawerOpen}
        onClose={() => setAuditDrawerOpen(false)}
        destroyOnHidden={false}
        styles={{
          body: { padding: 24, background: "#f8fafc" },
        }}
      >
        {!selectedAuditLog ? (
          <AdminDetailPlaceholder description="请选择一条审计日志查看详情" />
        ) : (
          <div className="space-y-5">
            <div className="rounded-[26px] bg-gradient-to-br from-[#0f172a] via-[#111827] to-[#475569] p-6 text-white shadow-[0_18px_36px_rgba(15,23,42,0.18)]">
              <div className="font-['Manrope'] text-xl font-black tracking-tight text-white">
                {getReadableCodeLabel(selectedAuditLog.actionType, context.auditActionLabelMap, selectedAuditLog.actionType)}
              </div>
              <div className="mt-2 font-mono text-xs  text-white/70">{selectedAuditLog.traceId}</div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <AdminMiniStat className="admin-content-mini-stat" label="操作人" value={selectedAuditLog.operatorDisplayName} />
              <AdminMiniStat className="admin-content-mini-stat" label="操作时间" value={formatDateTime(selectedAuditLog.createdAt)} />
              <AdminMiniStat className="admin-content-mini-stat" label="目标类型" value={getReadableCodeLabel(selectedAuditLog.targetType, targetTypeLabelMap, selectedAuditLog.targetType)} />
              <AdminMiniStat className="admin-content-mini-stat" label="目标 ID" value={selectedAuditLog.targetId} />
            </div>

            <div className="rounded-[26px] border border-slate-200 bg-white px-4 py-4 shadow-[0_10px_28px_rgba(15,23,42,0.04)]">
              <div className="text-[11px] font-bold text-slate-400">操作详情</div>
              {selectedAuditDetailObject && Object.keys(selectedAuditDetailObject).length > 0 ? (
                <div className="mt-3 space-y-4">
                  <div className="overflow-hidden rounded-2xl border border-slate-200 bg-slate-50">
                    <div className="divide-y divide-slate-200">
                      {Object.entries(selectedAuditDetailObject)
                        .filter(([key]) => !auditDetailLongTextKeys.has(key))
                        .map(([key, value]) => (
                          <div key={key} className="grid grid-cols-[112px_minmax(0,1fr)] gap-3 px-4 py-3">
                            <div className="text-[11px] font-bold text-slate-400">
                              {auditDetailFieldLabelMap[key] ?? key}
                            </div>
                            <div className="break-all text-sm font-semibold leading-6 text-slate-900">
                              {formatAuditDetailValue(key, value)}
                            </div>
                          </div>
                        ))}
                    </div>
                  </div>

                  {Object.entries(selectedAuditDetailObject)
                    .filter(([key]) => auditDetailLongTextKeys.has(key))
                    .map(([key, value]) => (
                      <div key={key} className="overflow-hidden rounded-2xl border border-slate-200 bg-slate-50">
                        <div className="border-b border-slate-200 px-4 py-3 text-[11px] font-bold text-slate-400">
                          {auditDetailFieldLabelMap[key] ?? key}
                        </div>
                        <div className="whitespace-pre-wrap break-words px-4 py-3 text-sm leading-7 text-slate-600">
                          {formatAuditDetailValue(key, value)}
                        </div>
                      </div>
                    ))}

                  <details className="group rounded-2xl border border-slate-200 bg-slate-50/80 px-4 py-4">
                    <summary className="cursor-pointer list-none text-sm font-semibold text-slate-700">
                      查看原始记录
                    </summary>
                    <pre className="mt-3 overflow-auto rounded-2xl bg-slate-900 px-4 py-4 text-xs leading-6 text-slate-100">
                      {safePrettifyJson(selectedAuditLog.detailJson)}
                    </pre>
                  </details>
                </div>
              ) : (
                <pre className="mt-3 overflow-auto rounded-2xl bg-slate-900 px-4 py-4 text-xs leading-6 text-slate-100">
                  {safePrettifyJson(selectedAuditLog.detailJson)}
                </pre>
              )}
            </div>

            <div className="space-y-3">
              {selectedAuditTargetUserId ? (
                <Button block className="!h-11 !rounded-2xl !border-slate-200" onClick={() => navigate(`/admin/users/${selectedAuditTargetUserId}`)}>
                  查看关联用户
                </Button>
              ) : null}
              <Button
                block
                className="!h-11 !rounded-2xl !border-slate-200"
                onClick={() => handleOpenAiTraceModal(selectedAuditLog.traceId)}
              >
                查看关联生成记录
              </Button>
            </div>
          </div>
        )}
      </Drawer>

      <Drawer
        title="词条详情"
        placement="right"
        width={520}
        open={activeTab === "terms" && termDrawerOpen}
        onClose={() => setTermDrawerOpen(false)}
        destroyOnHidden={false}
        styles={{
          body: { padding: 24, background: "#f8fafc" },
        }}
      >
        {!focusedTerm ? (
          <AdminDetailPlaceholder description="请选择一条敏感词查看详情" />
        ) : (
          <div className="space-y-5">
            <div className="rounded-[26px] bg-gradient-to-br from-[#581c87] via-[#7c3aed] to-[#4338ca] p-6 text-white shadow-[0_18px_36px_rgba(109,40,217,0.18)]">
              <div className="font-['Manrope'] text-2xl font-black tracking-tight text-white">{focusedTerm.term}</div>
              <div className="mt-3 flex flex-wrap gap-2">
                <Tag color="white" style={{ color: "#6d28d9", border: "none" }}>
                  {getReadableCodeLabel(focusedTerm.termType, sensitiveTermTypeLabelMap, focusedTerm.termType)}
                </Tag>
                <Tag color={getRiskTagColor(focusedTerm.riskLevel)}>
                  {getLabel(focusedTerm.riskLevel, riskLevelLabelMap, focusedTerm.riskLevel)}
                </Tag>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <AdminMiniStat className="admin-content-mini-stat" label="来源范围" value={getReadableCodeLabel(focusedTerm.sourceScope, sourceTypeLabelMap, focusedTerm.sourceScope)} />
              <AdminMiniStat className="admin-content-mini-stat" label="治理动作" value={getReadableCodeLabel(focusedTerm.action, moderationActionLabelMap, focusedTerm.action)} />
              <AdminMiniStat className="admin-content-mini-stat" label="启用状态" value={focusedTerm.enabled ? "启用中" : "已停用"} />
              <AdminMiniStat className="admin-content-mini-stat" label="白名单" value={focusedTerm.whitelist ? "是" : "否"} />
            </div>

            <Button
              block
              className="!h-11 !rounded-2xl !border-slate-200"
              onClick={() => {
                openTermEditor(focusedTerm);
              }}
            >
              <Eye size={15} className="mr-2" />
              编辑当前词条
            </Button>
          </div>
        )}
      </Drawer>

      <Modal
        title="处置举报"
        open={reportDecisionOpen}
        forceRender
        onCancel={() => setReportDecisionOpen(false)}
        onOk={() => void handleSubmitReportDecision()}
        confirmLoading={reportDecisionSaving}
        okText="提交处置"
        cancelText="取消"
        destroyOnHidden
      >
        <Form layout="vertical" form={reportDecisionForm}>
          <Form.Item label="处理决定" name="decision" rules={[{ required: true, message: "请选择处理决定" }]}>
            <Select options={reportDecisionOptions} onChange={(value) => {
              if (value === "ACCEPTED") {
                reportDecisionForm.setFieldValue("action", "TAKE_DOWN");
                return;
              }
              reportDecisionForm.setFieldValue("action", "NO_ACTION");
            }} />
          </Form.Item>
          <Form.Item label="执行动作" name="action" rules={[{ required: true, message: "请选择执行动作" }]}>
            <Select options={reportActionOptions} />
          </Form.Item>
          <Form.Item label="备注" name="comment">
            <TextArea rows={4} placeholder="补充说明平台的处置依据" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="审核待审项"
        open={reviewDecisionOpen}
        forceRender
        onCancel={() => setReviewDecisionOpen(false)}
        onOk={() => void handleSubmitReviewDecision()}
        confirmLoading={reviewDecisionSaving}
        okText="提交审核"
        cancelText="取消"
        destroyOnHidden
      >
        <Form layout="vertical" form={context.reviewDecisionForm}>
          <Form.Item label="审核结果" name="decision" rules={[{ required: true, message: "请选择审核结果" }]}>
            <Select options={reviewDecisionOptions} />
          </Form.Item>
          <Form.Item label="审核备注" name="comment">
            <TextArea rows={4} placeholder="说明通过或驳回原因" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="导入纯文本词库"
        open={termImportConfigOpen}
        forceRender
        onCancel={closeTermImportConfig}
        onOk={() => void handleConfirmPlainTextImport()}
        confirmLoading={termImporting}
        okText="开始导入"
        cancelText="取消"
        destroyOnHidden
      >
        <div className="space-y-4">
          <div className="rounded-2xl bg-slate-50 px-4 py-4 text-sm leading-6 text-slate-500">
            {pendingImportFile ? (
              <>
                当前文件：<span className="font-medium text-slate-700">{pendingImportFile.name}</span>
                <br />
                系统会按“每行一个词条”读取，并统一应用下方配置。
              </>
            ) : "请选择要导入的文本词库文件。"}
          </div>
          <Form layout="vertical" form={termImportConfigForm} initialValues={termImportConfigInitialValues}>
            <div className="grid gap-4 md:grid-cols-2">
              <Form.Item label="分类" name="termType" rules={[{ required: true, message: "请选择分类" }]}>
                <Select options={termTypeOptions} />
              </Form.Item>
              <Form.Item label="风险等级" name="riskLevel" rules={[{ required: true, message: "请选择风险等级" }]}>
                <Select options={sensitiveRiskOptions} />
              </Form.Item>
            </div>
            <div className="grid gap-4 md:grid-cols-2">
              <Form.Item label="治理动作" name="action" rules={[{ required: true, message: "请选择治理动作" }]}>
                <Select options={sensitiveActionOptions} />
              </Form.Item>
              <Form.Item label="适用范围" name="sourceScope" rules={[{ required: true, message: "请选择来源范围" }]}>
                <Select options={sourceScopeOptions} />
              </Form.Item>
            </div>
            <div className="grid gap-4 md:grid-cols-2">
              <Form.Item label="白名单" name="whitelist" valuePropName="checked">
                <AdminSettingSwitch />
              </Form.Item>
              <Form.Item label="启用状态" name="enabled" valuePropName="checked">
                <AdminSettingSwitch />
              </Form.Item>
            </div>
          </Form>
        </div>
      </Modal>

      <Drawer
        title={editingTerm ? "编辑敏感词" : "新增敏感词"}
        placement="right"
        width={520}
        open={activeTab === "terms" && termEditorOpen}
        onClose={closeTermEditor}
        destroyOnHidden
        styles={{
          body: { padding: 24, background: "#f8fafc" },
        }}
      >
        <div className="space-y-5">
          <div className="rounded-[26px] border border-slate-200 bg-white px-5 py-5 shadow-[0_10px_28px_rgba(15,23,42,0.04)]">
            <div className="font-['Manrope'] text-xl font-black tracking-tight text-slate-900">
              {editingTerm ? editingTerm.term : "新词条"}
            </div>
            <div className="mt-2 text-sm leading-6 text-slate-500">
              {editingTerm ? "直接调整词条策略，保存后立即回写当前词库。" : "在抽屉内完成新增，避免打断列表筛选和批量治理。"}
            </div>
          </div>

          <div className="rounded-[26px] border border-slate-200 bg-white px-5 py-5 shadow-[0_10px_28px_rgba(15,23,42,0.04)]">
            <Form layout="vertical" form={termEditForm} className="admin-content-inline-form" initialValues={termEditInitialValues}>
              <Form.Item label="词条" name="term" rules={[{ required: true, message: "请输入词条" }]}>
                <Input placeholder="例如：刷单" />
              </Form.Item>
              <div className="grid gap-4 md:grid-cols-2">
                <Form.Item label="分类" name="termType" rules={[{ required: true, message: "请选择分类" }]}>
                  <Select options={termTypeOptions} />
                </Form.Item>
                <Form.Item label="风险等级" name="riskLevel" rules={[{ required: true, message: "请选择风险等级" }]}>
                  <Select options={sensitiveRiskOptions} />
                </Form.Item>
              </div>
              <div className="grid gap-4 md:grid-cols-2">
                <Form.Item label="治理动作" name="action" rules={[{ required: true, message: "请选择治理动作" }]}>
                  <Select options={sensitiveActionOptions} />
                </Form.Item>
                <Form.Item label="来源范围" name="sourceScope" rules={[{ required: true, message: "请选择来源范围" }]}>
                  <Select options={sourceScopeOptions} />
                </Form.Item>
              </div>
              <div className="grid gap-4 md:grid-cols-2">
                <Form.Item label="白名单" name="whitelist" valuePropName="checked">
                  <AdminSettingSwitch />
                </Form.Item>
                <Form.Item label="启用状态" name="enabled" valuePropName="checked">
                  <AdminSettingSwitch />
                </Form.Item>
              </div>
            </Form>
          </div>

          <div className="flex gap-3">
            <Button className="!h-11 flex-1 !rounded-2xl !border-slate-200" onClick={closeTermEditor}>
              取消
            </Button>
            <Button
              type="primary"
              className="!h-11 flex-1 !rounded-2xl !border-none !bg-[#4647d3] !shadow-none"
              loading={editingTerm ? termEditSaving : termSaving}
              onClick={() => void (editingTerm ? handleUpdateTerm() : handleCreateTerm())}
            >
              {editingTerm ? "保存修改" : "加入词库"}
            </Button>
          </div>
        </div>
      </Drawer>
    </>
  );
}
