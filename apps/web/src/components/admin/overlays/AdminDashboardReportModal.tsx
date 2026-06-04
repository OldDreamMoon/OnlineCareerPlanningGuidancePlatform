import { Alert, Button, Modal } from "antd";
import { Clock3, ShieldAlert, Users } from "lucide-react";
import { getLabel, moderationActionLabelMap, moderationReasonLabelMap, riskLevelLabelMap, targetTypeLabelMap } from "../../../lib/adminLabels";
import { formatCount, formatDateTime, formatRelativeTime } from "../../../lib/formatters";
import type { AdminWorkspaceModuleProps } from "../AdminLazyWorkspace";

function joinClassNames(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function clampPercent(value: number) {
  if (!Number.isFinite(value)) {
    return 0;
  }
  return Math.min(100, Math.max(0, value));
}

export default function AdminDashboardReportModal({ context }: AdminWorkspaceModuleProps<any>) {
  const {
    activeReport,
    reportDetail,
    reportDetailLoading,
    reportDetailError,
    reportModalStatus,
    reportModalPriority,
    reportTargetUserId,
    handleCloseReportDetail,
    navigate,
  } = context;

  if (!activeReport) {
    return null;
  }

  return (
    <Modal
      open={!!activeReport}
      onCancel={handleCloseReportDetail}
      footer={null}
      title={null}
      width={1080}
      centered
      destroyOnClose
      closable={false}
      styles={{
        content: {
          padding: 0,
          overflow: "hidden",
          borderRadius: 32,
          background: "rgba(255,255,255,0.92)",
          boxShadow: "0 28px 90px rgba(15,23,42,0.18)",
        },
        body: {
          padding: 0,
          background: "transparent",
        },
      }}
    >
      {reportDetailError ? (
        <div className="p-6">
          <Alert type="error" showIcon className="rounded-2xl" message="举报详情加载失败" description={reportDetailError} />
        </div>
      ) : reportDetailLoading && !reportDetail ? (
        <div className="py-16 text-center text-slate-500">正在加载举报详情...</div>
      ) : (
        <div className="flex h-[80vh] flex-col overflow-hidden rounded-[32px] bg-white/92 backdrop-blur-2xl">
          <div className="border-b border-slate-200/70 bg-white/70 px-8 py-7 backdrop-blur-xl">
            <div className="flex flex-col gap-5 xl:flex-row xl:items-start xl:justify-between">
              <div className="flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className={joinClassNames("rounded-full px-3 py-1 text-[11px] font-bold", reportModalStatus.className)}>
                    {reportModalStatus.label}
                  </span>
                  <span className={joinClassNames("rounded-full px-3 py-1 text-[11px] font-bold", reportModalPriority.className)}>
                    {reportModalPriority.label}
                  </span>
                  <span className="rounded-full bg-slate-100 px-3 py-1 text-[11px] font-bold text-slate-500">
                    {getLabel(reportDetail?.targetType ?? activeReport.targetType, targetTypeLabelMap, activeReport.targetType)}
                  </span>
                </div>
                <h2 className="mt-5 font-['Manrope'] text-[2rem] font-extrabold tracking-[-0.05em] text-slate-950">
                  {reportDetail?.contentTitle || activeReport.contentTitle || "未命名内容"}
                </h2>
                <div className="mt-3 text-sm leading-7 text-slate-500">
                  报告 #{reportDetail?.reportId ?? activeReport.reportId} · 累计 {formatCount(reportDetail?.reportCount ?? activeReport.reportCount)} 次举报
                  · 创建 {formatDateTime(reportDetail?.createdAt ?? activeReport.createdAt)}
                </div>
                <div className="mt-4 rounded-[20px] border border-slate-200/80 bg-slate-50/80 px-4 py-4 text-sm leading-7 text-slate-500">
                  这里展示举报摘要、关联用户和当前状态，方便在总览页内快速判断处理优先级。
                </div>
              </div>

              <div className="flex items-start gap-3">
                {reportDetail?.reporterUserId ? (
                  <Button
                    type="primary"
                    className="!h-11 !rounded-full !border-0 !bg-gradient-to-r !from-[#5b61f6] !to-[#4338ca] !px-5 !font-bold shadow-lg shadow-[#4f46e5]/20"
                    onClick={() => {
                      navigate(`/admin/users/${reportDetail.reporterUserId}`);
                      handleCloseReportDetail();
                    }}
                  >
                    查看举报人
                  </Button>
                ) : null}
                {reportTargetUserId ? (
                  <Button
                    className="!h-11 !rounded-full !border-slate-200 !bg-white !px-5 !font-semibold"
                    onClick={() => {
                      navigate(`/admin/users/${reportTargetUserId}`);
                      handleCloseReportDetail();
                    }}
                  >
                    查看被举报用户
                  </Button>
                ) : null}
                <Button
                  type="text"
                  className="!h-11 !rounded-full !px-4 !font-semibold !text-slate-500 hover:!bg-slate-100 hover:!text-slate-900"
                  onClick={handleCloseReportDetail}
                >
                  关闭
                </Button>
              </div>
            </div>
          </div>

          <div className="flex-1 overflow-y-auto bg-slate-50/80 p-8">
            <div className="grid grid-cols-12 gap-6">
              <div className="col-span-12 space-y-6 xl:col-span-8">
                <div className="grid gap-4 md:grid-cols-3">
                  {[
                    { label: "最新动作", value: getLabel(reportDetail?.latestAction, moderationActionLabelMap, reportDetail?.latestAction ?? "暂无动作") },
                    { label: "目标状态", value: reportDetail?.targetStatus || "—" },
                    { label: "风险等级", value: getLabel(reportDetail?.targetRiskLevel, riskLevelLabelMap, reportDetail?.targetRiskLevel || "—") },
                  ].map((item) => (
                    <div key={item.label} className="rounded-2xl border border-slate-200/80 bg-white px-5 py-5 shadow-sm">
                      <div className="text-[11px] font-black text-slate-400">{item.label}</div>
                      <div className="mt-3 font-['Manrope'] text-[1.15rem] font-bold text-slate-950">{item.value}</div>
                    </div>
                  ))}
                </div>

                <div className="rounded-[28px] border border-slate-200/80 bg-white p-8 shadow-none">
                  <div className="mb-8 flex items-center gap-3">
                    <div className="flex h-12 w-12 items-center justify-center rounded-[18px] bg-[#4647d3]/10 text-[#4647d3]">
                      <ShieldAlert size={20} />
                    </div>
                    <h3 className="font-['Manrope'] text-xl font-bold text-slate-950">举报内容概览</h3>
                  </div>
                  <div className="grid gap-6 md:grid-cols-2">
                    <div className="space-y-1">
                      <label className="text-[10px] font-black text-slate-400">举报原因</label>
                      <p className="text-lg font-semibold text-slate-950">
                        {getLabel(reportDetail?.reasonCode ?? activeReport.reasonCode, moderationReasonLabelMap, activeReport.reasonCode)}
                      </p>
                    </div>
                    <div className="space-y-1">
                      <label className="text-[10px] font-black text-slate-400">举报人</label>
                      <p className="text-lg font-semibold text-slate-950">
                        {reportDetail?.reporterDisplayName || "暂未返回举报人展示名"}
                      </p>
                    </div>
                    <div className="space-y-1 md:col-span-2">
                      <label className="text-[10px] font-black text-slate-400">举报补充说明</label>
                      <div className="mt-2 rounded-2xl bg-slate-50/90 px-4 py-4 text-sm leading-7 text-slate-600">
                        {reportDetail?.reportDetail || "当前没有补充说明。"}
                      </div>
                    </div>
                    <div className="space-y-1 md:col-span-2">
                      <label className="text-[10px] font-black text-slate-400">被举报内容正文</label>
                      <div className="mt-2 rounded-2xl bg-slate-50/90 px-4 py-4 text-sm leading-7 text-slate-600">
                        {reportDetail?.contentBody || "当前详情接口未返回正文内容。"}
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <div className="col-span-12 space-y-6 xl:col-span-4">
                <div className="rounded-[28px] bg-gradient-to-br from-[#5b61f6] via-[#4f46e5] to-[#4338ca] p-8 text-white shadow-[0_20px_40px_rgba(79,70,229,0.2)]">
                  <div className="mb-6 flex items-start justify-between">
                    <span className="text-xs font-bold text-white/75">举报热度</span>
                    <span className="rounded-full bg-white/16 px-3 py-1 text-xs font-bold">
                      总览只读详情
                    </span>
                  </div>
                  <div className="font-['Manrope'] text-5xl font-black">
                    {formatCount(reportDetail?.reportCount ?? activeReport.reportCount)}
                  </div>
                  <div className="mt-4 text-sm leading-7 text-white/80">
                    当前样本展示举报次数与最新处理状态，便于快速判断优先级。
                  </div>
                  <div className="mt-6 h-2 w-full overflow-hidden rounded-full bg-white/16">
                    <div
                      className="h-full rounded-full bg-white"
                      style={{ width: `${clampPercent(((reportDetail?.reportCount ?? activeReport.reportCount) / 6) * 100)}%` }}
                    />
                  </div>
                </div>

                <div className="rounded-[28px] border border-slate-200/80 bg-white p-6 shadow-none">
                  <div className="mb-6 flex items-center gap-3">
                    <Clock3 size={18} className="text-slate-500" />
                    <div className="font-semibold text-slate-950">时间轴摘要</div>
                  </div>
                  <div className="space-y-4">
                    {[
                      { label: "创建时间", value: formatDateTime(reportDetail?.createdAt ?? activeReport.createdAt) },
                      { label: "更新时间", value: formatDateTime(reportDetail?.updatedAt ?? activeReport.updatedAt) },
                      { label: "最近变化", value: formatRelativeTime(reportDetail?.updatedAt ?? activeReport.updatedAt) },
                    ].map((item) => (
                      <div key={item.label} className="flex items-center justify-between rounded-2xl bg-slate-50 px-4 py-3">
                        <span className="text-xs font-bold text-slate-500">{item.label}</span>
                        <span className="text-sm font-black text-slate-950">{item.value}</span>
                      </div>
                    ))}
                  </div>
                </div>

                <div className="rounded-[28px] border border-slate-200/80 bg-white p-6 shadow-none">
                  <div className="mb-6 flex items-center gap-3">
                    <Users size={18} className="text-[#4647d3]" />
                    <div className="font-semibold text-slate-950">关联用户</div>
                  </div>
                  <div className="space-y-3 text-sm leading-7 text-slate-600">
                    <div className="rounded-2xl bg-slate-50 px-4 py-4">
                      举报人 ID：{reportDetail?.reporterUserId ? `#${reportDetail.reporterUserId}` : "未返回"}
                    </div>
                    <div className="rounded-2xl bg-slate-50 px-4 py-4">
                      被举报目标：{reportTargetUserId ? `用户 #${reportTargetUserId}` : "当前目标不是用户或未返回用户 ID"}
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </Modal>
  );
}
