import {
  Clock3,
  Flag,
  MessageSquare,
  ShieldCheck,
} from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import CommunityEmptyState from "../components/community/CommunityEmptyState";
import CommunityModuleLayout from "../components/community/CommunityModuleLayout";
import CommunityToast, { type CommunityToastState } from "../components/community/CommunityToast";
import {
  createCommunityReport,
  listCommunityReports,
  type CommunityReportRecord,
} from "../lib/community";
import { formatDateTime, formatCount } from "../lib/formatters";
import { ApiClientError } from "../lib/apiClient";
import {
  getReportReasonLabel,
  joinClasses,
} from "../components/community/communityUtils";

const STATUS_OPTIONS = [
  { value: "", label: "全部状态" },
  { value: "PENDING", label: "待处理" },
  { value: "ACCEPTED", label: "已采纳" },
  { value: "REJECTED", label: "已驳回" },
  { value: "CLOSED", label: "已关闭" },
];

const REPORT_REASON_OPTIONS = [
  { code: "ABUSE", label: "人身攻击" },
  { code: "SPAM", label: "广告引流" },
  { code: "MISLEADING", label: "不实信息" },
  { code: "RISK_LINK", label: "风险链接" },
  { code: "EXPLICIT", label: "低俗违规" },
  { code: "OTHER", label: "其他" },
];

function toUserMessage(error: unknown, fallback: string) {
  if (error instanceof ApiClientError) {
    return error.message || fallback;
  }
  if (error instanceof Error) {
    return error.message || fallback;
  }
  return fallback;
}

function getReportStatusMeta(status: string | null | undefined) {
  switch ((status ?? "").toUpperCase()) {
    case "ACCEPTED":
      return {
        label: "已采纳",
        className: "border border-emerald-200 bg-emerald-50 text-emerald-700",
      };
    case "REJECTED":
      return {
        label: "已驳回",
        className: "border border-rose-200 bg-rose-50 text-rose-700",
      };
    case "CLOSED":
      return {
        label: "已关闭",
        className: "border border-slate-200 bg-slate-100 text-slate-600",
      };
    case "PENDING":
    default:
      return {
        label: "待处理",
        className: "border border-amber-200 bg-amber-50 text-amber-700",
      };
  }
}

export default function CommunityReportsPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const reportIdFromQuery = Number(searchParams.get("reportId") || "");
  const prefillTargetType = (searchParams.get("targetType") || "").toUpperCase();
  const prefillTargetId = searchParams.get("targetId") || "";
  const prefillTitle = searchParams.get("title") || "";
  const prefillBody = searchParams.get("body") || "";
  const prefillReasonCode = (searchParams.get("reasonCode") || "ABUSE").toUpperCase();
  const prefillDetail = searchParams.get("detail") || "";

  const [statusFilter, setStatusFilter] = useState("");
  const [records, setRecords] = useState<CommunityReportRecord[]>([]);
  const [selectedReportId, setSelectedReportId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<CommunityToastState | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  const [prefillReason, setPrefillReason] = useState(prefillReasonCode);
  const [prefillExtra, setPrefillExtra] = useState(prefillDetail);
  const [submitting, setSubmitting] = useState(false);
  const hasReportsLoadFailure = !loading && !!error && records.length === 0;

  const showToast = (text: string, tone: CommunityToastState["tone"] = "info") => {
    setToast({
      id: Date.now() + Math.random(),
      tone,
      text,
    });
  };

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);

    void listCommunityReports({ page: 1, size: 12, status: statusFilter || undefined }).then((data) => {
      if (!active) {
        return;
      }
      setRecords(data.records);
      setSelectedReportId((current) => {
        if (reportIdFromQuery && data.records.some((item) => item.reportId === reportIdFromQuery)) {
          return reportIdFromQuery;
        }
        if (current && data.records.some((item) => item.reportId === current)) {
          return current;
        }
        return data.records[0]?.reportId ?? null;
      });
    }).catch((fetchError) => {
      if (!active) {
        return;
      }
      const message = toUserMessage(fetchError, "治理反馈暂时加载失败，请稍后重试。");
      setError(message);
      showToast(message, "error");
      setRecords([]);
      setSelectedReportId(null);
    }).finally(() => {
      if (!active) {
        return;
      }
      setLoading(false);
    });

    return () => {
      active = false;
    };
  }, [refreshKey, reportIdFromQuery, statusFilter]);

  const selectedReport = records.find((item) => item.reportId === selectedReportId) ?? null;
  const pendingCount = records.filter((item) => item.status === "PENDING").length;

  const handleSubmitPrefill = async () => {
    if (!prefillTargetType || !prefillTargetId) {
      showToast("缺少举报目标，暂时无法从治理页继续提交。", "error");
      return;
    }

    setSubmitting(true);
    try {
      const response = await createCommunityReport({
        targetType: prefillTargetType,
        targetId: prefillTargetId,
        reasonCode: prefillReason,
        detail: prefillExtra.trim() || undefined,
      });
      showToast("举报已提交，右侧详情区会继续展示后续处理状态。", "success");
      navigate(`/community/reports?reportId=${encodeURIComponent(String(response.reportId))}`, { replace: true });
    } catch (submitError) {
      showToast(toUserMessage(submitError, "举报提交失败，请稍后再试。"), "error");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <CommunityModuleLayout
      activeTab="reports"
      topbarSectionLabel="Community Reports"
      topbarTitle="举报与治理反馈"
      title="我的治理与申诉记录"
      description="这里会把你提交过的举报、当前处理状态和平台给出的最新动作说明统一沉淀下来，方便你回看整个治理闭环。"
      stats={[
        {
          label: "记录总数",
          value: loading ? "—" : formatCount(records.length),
          hint: "当前页展示最近 12 条治理记录。",
          accentClassName: "text-indigo-600",
        },
        {
          label: "待处理",
          value: loading ? "—" : formatCount(pendingCount),
          hint: "平台会优先处理仍未关闭的举报条目。",
          accentClassName: "text-amber-500",
        },
      ]}
    >
      <div className="grid items-start gap-6 lg:grid-cols-12">
        <div className="space-y-4 lg:col-span-7">
          <div className="rounded-[1.7rem] border border-slate-100 bg-white px-5 py-5 shadow-sm">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="flex min-h-[3.25rem] items-center">
                <h2 className="text-2xl font-black leading-tight text-slate-950">举报记录区</h2>
              </div>
              <div className="flex flex-wrap gap-2">
                {STATUS_OPTIONS.map((option) => (
                  <button
                    key={option.value || "ALL"}
                    type="button"
                    onClick={() => setStatusFilter(option.value)}
                    className={joinClasses(
                      "rounded-full border px-3 py-1.5 text-sm font-semibold transition-colors",
                      statusFilter === option.value
                        ? "border-indigo-200 bg-indigo-50 text-indigo-700"
                        : "border-slate-200 bg-white text-slate-600 hover:border-indigo-200",
                    )}
                  >
                    {option.label}
                  </button>
                ))}
              </div>
            </div>
          </div>

          {loading ? (
            <div className="rounded-[1.8rem] border border-slate-100 bg-white px-6 py-16 text-center shadow-sm">
              <div className="inline-flex items-center gap-2 text-base font-semibold text-slate-500">
                <Clock3 size={16} className="text-indigo-500" />
                正在加载治理反馈...
              </div>
            </div>
          ) : hasReportsLoadFailure ? (
            <CommunityEmptyState
              icon={ShieldCheck}
              title="治理记录暂时没有同步成功"
              description="这批治理反馈还没有成功刷新，你可以稍后再试，或先回讨论大厅继续浏览内容。"
              action={(
                <button
                  type="button"
                  onClick={() => setRefreshKey((current) => current + 1)}
                  className="rounded-full bg-slate-900 px-5 py-2.5 text-base font-semibold text-white transition-colors hover:bg-slate-800"
                >
                  重新加载治理记录
                </button>
              )}
            />
          ) : records.length === 0 ? (
            <CommunityEmptyState
              icon={Flag}
              title="还没有治理反馈记录"
              description="当你提交过举报后，平台会把处理状态、结果说明和后续动作统一沉淀在这里。"
              action={(
                <Link
                  to="/community"
                  className="rounded-full bg-slate-900 px-5 py-2.5 text-base font-semibold !text-white transition-colors hover:bg-slate-800 hover:!text-white visited:!text-white"
                >
                  返回讨论大厅
                </Link>
              )}
            />
          ) : (
            <div className="space-y-4">
              {records.map((record) => {
                const statusMeta = getReportStatusMeta(record.status);
                const selected = record.reportId === selectedReportId;

                return (
                  <button
                    key={record.reportId}
                    type="button"
                    onClick={() => setSelectedReportId(record.reportId)}
                    className={joinClasses(
                      "block w-full rounded-[1.7rem] border px-5 py-5 text-left shadow-sm transition-all",
                      selected
                        ? "border-indigo-200 bg-indigo-50/70 shadow-[0_18px_48px_rgba(99,102,241,0.12)]"
                        : "border-slate-100 bg-white hover:border-indigo-100 hover:bg-white",
                    )}
                  >
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div>
                        <div className="text-sm font-semibold text-slate-500">
                          举报 #{record.reportId}
                        </div>
                        <h3 className="mt-1.5 text-xl font-bold text-slate-950">
                          {record.contentTitle || `${record.targetType} #${record.targetId}`}
                        </h3>
                      </div>
                      <span className={joinClasses("rounded-full px-3 py-1.5 text-sm font-semibold", statusMeta.className)}>
                        {statusMeta.label}
                      </span>
                    </div>

                    <div className="mt-3 text-base leading-8 text-slate-500">
                      原因：{getReportReasonLabel(record.reasonCode)}
                    </div>
                    <div className="mt-2 line-clamp-2 text-base leading-8 text-slate-500">
                      {record.contentBody || "平台会在核查后把处理结果同步回这一条记录。"}
                    </div>

                    <div className="mt-4 flex flex-wrap items-center gap-3 text-sm text-slate-400">
                      <span>提交于 {formatDateTime(record.createdAt)}</span>
                      {record.latestAction ? <span>最新动作：{record.latestAction}</span> : null}
                    </div>
                  </button>
                );
              })}
            </div>
          )}
        </div>

        <aside className="space-y-6 lg:col-span-5 lg:sticky lg:top-[92px]">
          <div className="rounded-[1.85rem] border border-slate-100 bg-white px-5 py-5 shadow-sm">
            <div className="mb-3 flex items-center gap-2 text-base font-bold text-slate-900">
              <ShieldCheck size={16} className="text-emerald-500" />
              详情预览区
            </div>

            {selectedReport ? (
              <div className="space-y-4">
                <div className="rounded-[1.4rem] border border-slate-100 bg-slate-50 px-4 py-4">
                  <div className="text-sm font-bold text-slate-600">目标内容</div>
                  <div className="mt-2 text-base font-bold text-slate-900">
                    {selectedReport.contentTitle || `${selectedReport.targetType} #${selectedReport.targetId}`}
                  </div>
                  <div className="mt-2 text-base leading-8 text-slate-500">
                    {selectedReport.contentBody || "平台当前只保留与治理相关的上下文摘要。"}
                  </div>
                </div>

                <div className="space-y-3 text-base leading-8 text-slate-600">
                  <div className="flex items-center justify-between gap-3">
                    <span>举报原因</span>
                    <span className="font-semibold text-slate-900">{getReportReasonLabel(selectedReport.reasonCode)}</span>
                  </div>
                  <div className="flex items-center justify-between gap-3">
                    <span>最新状态</span>
                    <span className={joinClasses("rounded-full px-2.5 py-1 text-sm font-semibold", getReportStatusMeta(selectedReport.status).className)}>
                      {getReportStatusMeta(selectedReport.status).label}
                    </span>
                  </div>
                  <div className="flex items-center justify-between gap-3">
                    <span>最近更新时间</span>
                    <span className="font-semibold text-slate-900">{formatDateTime(selectedReport.updatedAt)}</span>
                  </div>
                </div>

                {selectedReport.detail ? (
                  <div className="rounded-[1.3rem] border border-slate-100 bg-slate-50 px-4 py-4 text-base leading-8 text-slate-600">
                    <div className="text-sm font-bold text-slate-600">我补充的说明</div>
                    <div className="mt-2">{selectedReport.detail}</div>
                  </div>
                ) : null}

                {selectedReport.contentPostId ? (
                  <Link
                    to={`/community/${encodeURIComponent(selectedReport.contentPostId)}`}
                    className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-4 py-2.5 text-base font-semibold text-slate-700 transition-colors hover:border-indigo-200 hover:text-indigo-600"
                  >
                    <MessageSquare size={15} />
                    回看关联帖子
                  </Link>
                ) : null}
              </div>
            ) : (
              <div className="rounded-[1.4rem] border border-dashed border-slate-200 bg-slate-50 px-4 py-6 text-base leading-8 text-slate-500">
                选中左侧任意一条治理记录后，这里会展示目标内容、当前状态和最近的处理动作。
              </div>
            )}
          </div>

          {prefillTargetType && prefillTargetId ? (
            <div className="rounded-[1.85rem] border border-indigo-100 bg-white px-5 py-5 shadow-sm">
              <div className="mb-3 flex items-center gap-2 text-base font-bold text-indigo-600">
                <Flag size={16} />
                预填承接区
              </div>
              <p className="text-base leading-8 text-slate-500">
                你是从帖子详情带着举报上下文进入的，可以在这里继续补全说明后再正式提交。
              </p>

              <div className="mt-4 rounded-[1.3rem] border border-slate-100 bg-slate-50 px-4 py-4">
                <div className="text-sm font-bold text-slate-600">当前目标</div>
                <div className="mt-2 text-base font-bold text-slate-900">
                  {prefillTitle || `${prefillTargetType} #${prefillTargetId}`}
                </div>
                {prefillBody ? <div className="mt-2 text-base leading-8 text-slate-500">{prefillBody}</div> : null}
              </div>

              <div className="mt-4 space-y-4">
                <div>
                  <label className="mb-2 block text-base font-semibold text-slate-900">举报原因</label>
                  <div className="flex flex-wrap gap-2">
                    {REPORT_REASON_OPTIONS.map((option) => (
                      <button
                        key={option.code}
                        type="button"
                        onClick={() => setPrefillReason(option.code)}
                        className={joinClasses(
                          "rounded-full border px-3 py-1.5 text-sm font-semibold transition-colors",
                          prefillReason === option.code
                            ? "border-rose-200 bg-rose-50 text-rose-700"
                            : "border-slate-200 bg-white text-slate-600 hover:border-rose-200 hover:text-rose-600",
                        )}
                      >
                        {option.label}
                      </button>
                    ))}
                  </div>
                </div>

                <div>
                  <label className="mb-2 block text-base font-semibold text-slate-900">补充说明</label>
                  <textarea
                    rows={4}
                    value={prefillExtra}
                    onChange={(event) => setPrefillExtra(event.target.value)}
                    placeholder="把你认为存在风险的细节说明清楚，便于平台更快判断。"
                    className="w-full resize-none rounded-[1.3rem] border border-slate-200 px-4 py-3 text-base leading-8 text-slate-700 outline-none transition-colors placeholder:text-slate-400 focus:border-indigo-300"
                  />
                </div>
              </div>

              <button
                type="button"
                disabled={submitting}
                onClick={() => void handleSubmitPrefill()}
                className="mt-4 w-full rounded-full bg-indigo-600 px-4 py-2.5 text-base font-semibold text-white transition-colors hover:bg-indigo-700 disabled:cursor-not-allowed disabled:bg-slate-200 disabled:text-slate-400"
              >
                {submitting ? "提交中..." : "提交这条举报"}
              </button>
            </div>
          ) : null}
        </aside>
      </div>
      <CommunityToast
        toast={toast}
        onClose={(toastId) => {
          setToast((current) => (current?.id === toastId ? null : current));
        }}
      />
    </CommunityModuleLayout>
  );
}
