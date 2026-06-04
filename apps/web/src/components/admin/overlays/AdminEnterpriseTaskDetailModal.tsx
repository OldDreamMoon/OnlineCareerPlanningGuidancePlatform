import { Alert, Button, Empty, Modal, Popconfirm, Tag } from "antd";
import { RefreshCcw } from "lucide-react";
import AdminIdentityAvatar from "../AdminIdentityAvatar";
import { formatCount, formatDateTime, formatRelativeTime } from "../../../lib/formatters";
import type { AdminWorkspaceModuleProps } from "../AdminLazyWorkspace";

export default function AdminEnterpriseTaskDetailModal({ context }: AdminWorkspaceModuleProps<any>) {
  const {
    detailModalOpen,
    setDetailModalOpen,
    selectedRecord,
    detailError,
    selectedInspectionSummary,
    getApprovalTag,
    getTaskStatusTag,
    getRiskTag,
    getRiskSignalTag,
    handleManageTask,
    managingTaskId,
    handleRefreshDetail,
    detailLoading,
  } = context;

  return (
    <Modal
      open={detailModalOpen}
      onCancel={() => setDetailModalOpen(false)}
      footer={null}
      width={1120}
      destroyOnClose={false}
      title={selectedRecord ? "任务详情摘要" : "任务详情"}
      styles={{ body: { background: "#f8fafc", paddingTop: 12 } }}
    >
      {!selectedRecord ? (
        <Empty description="请选择一条任务查看详情" image={Empty.PRESENTED_IMAGE_SIMPLE} />
      ) : (
        <div className="space-y-6 pt-2">
          {detailError ? <Alert type="error" showIcon message={detailError} className="rounded-2xl" /> : null}
          <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_320px]">
            <div className="space-y-6">
              <div className="overflow-hidden rounded-[30px] border border-indigo-100/80 bg-white shadow-[0_20px_48px_rgba(79,70,229,0.08)]">
                <div className="p-6">
                  <div className="flex items-start gap-4">
                    <div className="flex h-16 w-16 shrink-0 items-center justify-center rounded-[22px] bg-[linear-gradient(180deg,#eef2ff,#e0e7ff)] shadow-[inset_0_1px_0_rgba(255,255,255,0.8)]">
                      <AdminIdentityAvatar
                        role="ENTERPRISE"
                        userId={selectedRecord.enterpriseUserId}
                        displayName={selectedRecord.enterpriseName}
                        enterpriseName={selectedRecord.enterpriseName}
                        enterpriseLogoUrl={selectedRecord.enterpriseLogoUrl}
                        className="!h-12 !w-12 !min-w-12 !shrink-0 !bg-transparent !shadow-none"
                        textClassName="text-base"
                      />
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="font-['Manrope'] text-xl font-extrabold tracking-tight text-slate-900">
                        {selectedRecord.title}
                      </div>
                      <div className="mt-1 text-sm font-medium text-slate-500">
                        {selectedRecord.enterpriseName} · 创建于 {formatDateTime(selectedRecord.createdAt, "待补充")}
                      </div>
                      <div className="mt-3 flex flex-wrap gap-2">
                        {getApprovalTag(selectedRecord.enterpriseApprovalStatus)}
                        {getTaskStatusTag(selectedRecord.status)}
                        {getRiskTag(selectedRecord.highestRiskLevel)}
                      </div>
                    </div>
                  </div>

                  <div className="mt-6 grid grid-cols-1 gap-3 border-y border-slate-200/80 py-5 md:grid-cols-2">
                    <div className="rounded-[24px] bg-[linear-gradient(180deg,rgba(238,242,255,0.9),rgba(255,255,255,1))] px-5 py-4 shadow-[inset_0_1px_0_rgba(255,255,255,0.8)]">
                      <div className="text-[11px] font-bold text-slate-400">任务奖励</div>
                      <div className="mt-2 font-['Manrope'] text-[1.85rem] font-black tracking-tight text-indigo-600">
                        {selectedRecord.rewardDescription?.trim() || "待补充"}
                      </div>
                    </div>
                    <div className="rounded-[24px] bg-[linear-gradient(180deg,rgba(248,250,252,0.9),rgba(255,255,255,1))] px-5 py-4 shadow-[inset_0_1px_0_rgba(255,255,255,0.85)]">
                      <div className="text-[11px] font-bold text-slate-400">截止日期</div>
                      <div className="mt-2 font-['Manrope'] text-[1.85rem] font-black tracking-tight text-slate-900">
                        {formatDateTime(selectedRecord.deadlineAt, "未设置")}
                      </div>
                    </div>
                  </div>

                  <div className="mt-5 grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4">
                    <div className="rounded-2xl bg-slate-50 px-4 py-3">
                      <div className="text-[11px] font-bold text-slate-400">最近更新</div>
                      <div className="mt-2 text-sm font-semibold text-slate-900">{formatDateTime(selectedRecord.updatedAt, "待补充")}</div>
                    </div>
                    <div className="rounded-2xl bg-slate-50 px-4 py-3">
                      <div className="text-[11px] font-bold text-slate-400">最近提交</div>
                      <div className="mt-2 text-sm font-semibold text-slate-900">{formatDateTime(selectedRecord.latestSubmissionAt, "暂无提交")}</div>
                    </div>
                    <div className="rounded-2xl bg-slate-50 px-4 py-3">
                      <div className="text-[11px] font-bold text-slate-400">已采纳提交</div>
                      <div className="mt-2 text-sm font-semibold text-slate-900">
                        {selectedRecord.acceptedSubmissionId != null ? `#${selectedRecord.acceptedSubmissionId}` : "暂无"}
                      </div>
                    </div>
                    <div className="rounded-2xl bg-slate-50 px-4 py-3">
                      <div className="text-[11px] font-bold text-slate-400">总提交数</div>
                      <div className="mt-2 text-sm font-semibold text-slate-900">{formatCount(selectedRecord.submissionCount)} 份</div>
                    </div>
                  </div>

                  <div className="mt-6">
                    <div className="text-[11px] font-bold text-slate-400">任务摘要</div>
                    <div className="mt-3 rounded-[26px] border border-slate-200/80 bg-[linear-gradient(180deg,rgba(248,250,252,0.88),rgba(255,255,255,1))] px-5 py-4 text-sm leading-7 text-slate-600">
                      {selectedRecord.descriptionPreview}
                    </div>
                  </div>

                  <div className="mt-6">
                    <div className="text-[11px] font-bold text-slate-400">风险信号</div>
                    <div className="mt-3 space-y-3">
                      {selectedRecord.riskSignals.length > 0 ? (
                        selectedRecord.riskSignals.map((signal: any) => (
                          <div key={signal.code} className="rounded-[22px] border border-rose-100 bg-[linear-gradient(180deg,rgba(255,241,242,0.95),rgba(255,255,255,1))] px-4 py-4">
                            <div className="flex flex-wrap items-center gap-2">
                              {getRiskSignalTag(signal)}
                              <span className="text-sm font-semibold text-slate-900">{signal.label}</span>
                            </div>
                            <div className="mt-2 text-sm leading-6 text-slate-500">{signal.description}</div>
                          </div>
                        ))
                      ) : null}
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div className="space-y-5">
              <div className="rounded-[30px] border border-slate-200/70 bg-white p-5 shadow-[0_14px_36px_rgba(148,163,184,0.08)]">
                <div className="font-['Manrope'] text-lg font-extrabold tracking-tight text-slate-900">提交流转摘要</div>
                {!selectedInspectionSummary ? (
                  <Empty description="当前任务暂无提交流转摘要" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                ) : (
                  <div className="mt-4 space-y-3">
                    <div className="grid grid-cols-1 gap-2.5">
                      <div className="rounded-[20px] bg-[linear-gradient(180deg,rgba(238,242,255,0.92),rgba(255,255,255,1))] px-4 py-3.5">
                        <div className="text-[11px] font-bold text-slate-400">处理覆盖率</div>
                        <div className="mt-1.5 font-['Manrope'] text-[1.7rem] font-extrabold text-indigo-600">
                          {selectedInspectionSummary.reviewCoverage}
                        </div>
                      </div>
                      <div className="grid grid-cols-2 gap-2.5">
                        <div className="rounded-[20px] bg-[linear-gradient(180deg,rgba(255,247,237,0.94),rgba(255,255,255,1))] px-4 py-3.5">
                          <div className="text-[11px] font-bold text-slate-400">待处理压力</div>
                          <div className="mt-1.5 font-['Manrope'] text-[1.55rem] font-extrabold text-amber-600">
                            {formatCount(selectedInspectionSummary.pendingSubmissionCount)}
                          </div>
                        </div>
                        <div className="rounded-[20px] bg-[linear-gradient(180deg,rgba(240,253,250,0.94),rgba(255,255,255,1))] px-4 py-3.5">
                          <div className="text-[11px] font-bold text-slate-400">最近活动</div>
                          <div className="mt-1.5 font-['Manrope'] text-base font-extrabold text-slate-900">
                            {selectedRecord.latestSubmissionAt ? formatRelativeTime(selectedRecord.latestSubmissionAt, "暂无提交") : "暂无提交"}
                          </div>
                        </div>
                      </div>
                    </div>
                    <div className="rounded-[22px] border border-slate-200 bg-[linear-gradient(180deg,rgba(248,250,252,0.92),rgba(255,255,255,1))] px-4 py-3.5">
                      <div className="rounded-2xl bg-white/90 px-4 py-3.5 shadow-[inset_0_1px_0_rgba(255,255,255,0.9)]">
                        <div className="flex items-center justify-between gap-4 text-sm font-semibold text-slate-700">
                          <span>处理覆盖进度</span>
                          <span className="font-['Manrope'] text-base font-black text-indigo-600">
                            {selectedInspectionSummary.reviewCoverage}
                          </span>
                        </div>
                        <div className="mt-2.5 h-2.5 overflow-hidden rounded-full bg-slate-100">
                          <div
                            className="h-full rounded-full bg-[linear-gradient(90deg,#6366f1,#818cf8)] transition-all"
                            style={{ width: `${selectedInspectionSummary.reviewCoverageValue}%` }}
                          />
                        </div>
                      </div>
                      <div className="mt-3 flex flex-wrap gap-2">
                        {selectedInspectionSummary.hasPendingQueue ? <Tag color="warning">待处理提交</Tag> : <Tag color="success">提交已收口</Tag>}
                        {selectedInspectionSummary.closedWithoutAccepted ? <Tag>关闭未采纳</Tag> : null}
                        {selectedRecord.enterpriseApprovalStatus !== "APPROVED" ? getApprovalTag(selectedRecord.enterpriseApprovalStatus) : <Tag color="success">企业已认证</Tag>}
                      </div>
                      <div className="mt-3 rounded-[18px] border border-rose-100 bg-rose-50/70 px-4 py-2.5">
                        <div className="text-sm font-semibold text-rose-700">流转建议</div>
                        <div className="mt-1 text-sm leading-6 text-rose-700/90">{selectedInspectionSummary.recommendation}</div>
                      </div>
                    </div>
                  </div>
                )}
              </div>

              <div className="rounded-[30px] border border-slate-200/70 bg-white p-6 shadow-[0_14px_36px_rgba(148,163,184,0.08)]">
                <div className="font-['Manrope'] text-lg font-extrabold tracking-tight text-slate-900">治理动作</div>
                <div className="mt-5 space-y-3">
                  {selectedRecord.status === "OPEN" ? (
                    <Popconfirm
                      title="确认关闭该任务？"
                      description="关闭后学生无法继续提交，适合平台先行止损。"
                      okText="确认"
                      cancelText="取消"
                      onConfirm={() => handleManageTask(selectedRecord, "CLOSE")}
                    >
                      <Button
                        block
                        className="!h-11 !rounded-2xl !border-none !bg-[linear-gradient(135deg,#e11d48,#be123c)] !font-semibold !text-white !shadow-[0_10px_24px_rgba(190,24,93,0.24)] hover:!bg-[linear-gradient(135deg,#e11d48,#be123c)] hover:!text-white"
                        loading={managingTaskId === selectedRecord.taskId}
                      >
                        关闭任务
                      </Button>
                    </Popconfirm>
                  ) : (
                    <Popconfirm
                      title="确认重新开放该任务？"
                      description="重新开放后学生可以继续提交；已采纳结果的任务仍不可重开。"
                      okText="确认"
                      cancelText="取消"
                      disabled={selectedRecord.acceptedSubmissionId != null}
                      onConfirm={() => handleManageTask(selectedRecord, "REOPEN")}
                    >
                      <Button
                        block
                        className={
                          selectedRecord.acceptedSubmissionId != null
                            ? "!h-11 !rounded-2xl !border-none !bg-slate-200 !font-semibold !text-slate-400 !shadow-none hover:!bg-slate-200 hover:!text-slate-400"
                            : "!h-11 !rounded-2xl !border-none !bg-[linear-gradient(135deg,#059669,#047857)] !font-semibold !text-white !shadow-[0_10px_24px_rgba(4,120,87,0.22)] hover:!bg-[linear-gradient(135deg,#059669,#047857)] hover:!text-white"
                        }
                        loading={managingTaskId === selectedRecord.taskId}
                        disabled={selectedRecord.acceptedSubmissionId != null}
                      >
                        重新开放
                      </Button>
                    </Popconfirm>
                  )}
                  <Button
                    block
                    className="!h-11 !rounded-2xl !border-slate-200 !bg-white !font-semibold !text-slate-600 hover:!border-slate-300 hover:!text-slate-900"
                    onClick={handleRefreshDetail}
                    disabled={detailLoading}
                  >
                    <RefreshCcw size={16} className={detailLoading ? "mr-2 animate-spin" : "mr-2"} />
                    刷新当前任务
                  </Button>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </Modal>
  );
}
