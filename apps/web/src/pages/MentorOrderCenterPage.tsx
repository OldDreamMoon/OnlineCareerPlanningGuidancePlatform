import { AnimatePresence, motion } from "framer-motion";
import {
  AlertCircle,
  AlertTriangle,
  ArrowUpRight,
  Briefcase,
  CheckCircle2,
  Clock,
  FileText,
  Filter,
  LayoutDashboard,
  MessageSquare,
  Search,
  Sparkles,
  User,
  Wallet,
  X,
  XCircle,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import WorkspaceRoleTopbar from "../components/workspace/WorkspaceRoleTopbar";
import { ApiClientError, apiRequest, buildQuery, isAbortError } from "../lib/apiClient";
import { resolveConsultSceneLabel } from "../lib/consultSceneLabels";
import { formatDateTime, formatMoneyFen, toTimestamp } from "../lib/formatters";
import { getMentorWorkspaceNavItems } from "../lib/workspaceNav";
import {
  buildWorkspaceSnapshotStorageKey,
  readWorkspaceSnapshot,
  writeWorkspaceSnapshot,
} from "../lib/workspaceSnapshot";

type ConsultOrderListResponse = {
  summary: {
    pendingReplyCount: number;
    expiringSoonCount: number;
    waitingConfirmationCount: number;
    afterSalesImpactCount: number;
  };
  records: ConsultOrderSummary[];
  total: number;
  page: number;
  size: number;
  availablePaymentModes: string[];
};

type ConsultOrderSummary = {
  orderNo: string;
  counterpartUserId: number;
  counterpartDisplayName: string;
  amountFen: number;
  status: string;
  questionText: string | null;
  paymentMode: string | null;
  appointmentStartAt: number | null;
  appointmentEndAt: number | null;
  createdAt: number | null;
  paidAt: number | null;
  closedAt: number | null;
  autoCancelAt: number | null;
  mentorReplyDeadlineAt: number | null;
  afterSalesImpact: boolean;
  pendingAfterSales: boolean;
  latestAfterSalesStatus: string | null;
};

type ConsultAfterSalesRequestSummary = {
  id: number;
  requesterUserId: number;
  requestType: string;
  status: string;
  reason: string;
  reviewNote: string | null;
  reviewerUserId: number | null;
  autoTriggered: boolean;
  createdAt: string | null;
  reviewedAt: string | null;
};

type ConsultOrderAttachmentItem = {
  attachmentId: number;
  attachmentType: string;
  slotCode: string;
  originalFilename: string;
  description: string | null;
  sourceStage: string | null;
  sizeBytes: number;
  lifecycleStatus: string | null;
  uploadedAt: string | null;
};

type ConsultOrderDetailResponse = {
  orderNo: string;
  studentUserId: number;
  studentDisplayName: string;
  mentorUserId: number;
  mentorDisplayName: string;
  amountFen: number;
  status: string;
  sceneCode: string | null;
  sourcePage: string | null;
  questionText: string | null;
  questionPayload: {
    primaryConcern: string | null;
    background: string | null;
    attemptedActions: string | null;
    expectedHelp: string | null;
    additionalNotes: string | null;
  } | null;
  problemSummary: string | null;
  coreQuestions: string[];
  expectedOutcomes: string[];
  selectedMaterialTypes: string[];
  prepSheetSnapshot: {
    scene: string | null;
    summaryDraft: string | null;
    coreQuestions: string[];
    suggestedMaterials: string[];
    expectedOutcomes: string[];
  } | null;
  attachmentsSummary: {
    currentAttachmentCount: number;
    currentMaterialTypes: string[];
    records: ConsultOrderAttachmentItem[];
  } | null;
  studentProfile: {
    jobStatus: string | null;
    schoolName: string | null;
    major: string | null;
    grade: string | null;
    gpa: string | null;
    targetPosition: string | null;
    honors: string | null;
    skillTags: string[];
    selfIntro: string | null;
    portraitTags: Array<{
      code: string | null;
      label: string | null;
      source: string | null;
    }>;
    portraitUpdatedAt: string | null;
  } | null;
  paymentMode: string | null;
  appointmentStartAt: number | null;
  appointmentEndAt: number | null;
  createdAt: number | null;
  paidAt: number | null;
  closedAt: number | null;
  autoCancelAt: number | null;
  mentorReplyDeadlineAt: number | null;
  review: {
    rating: number;
    comment: string | null;
    createdAt: number | null;
  } | null;
  afterSalesRequests: ConsultAfterSalesRequestSummary[];
};

type StatusConfig = {
  label: string;
  colorClass: string;
  icon: typeof Clock;
};

type ServiceFilter = "ALL" | "TEXT" | "APPOINTMENT";
type TimeFilter = "ALL" | "CREATED_7D" | "PAID_7D" | "UPCOMING_APPOINTMENT";
type RiskFilter = "ALL" | "EXPIRING" | "NEED_CONFIRMATION" | "AFTER_SALES";
type SortMode = "PRIORITY" | "LATEST_CREATED" | "LATEST_PAID" | "DEADLINE_ASC" | "AMOUNT_DESC";

type MentorOrderCenterSnapshot = {
  summary: ConsultOrderListResponse["summary"];
  orders: ConsultOrderSummary[];
  ordersTotal: number;
  availablePaymentModes: string[];
  detailMap: Record<string, ConsultOrderDetailResponse>;
};

const CLIENT_PAGE_SIZE = 10;
const EXPIRING_SOON_WINDOW_MS = 6 * 60 * 60 * 1000;
const MATERIAL_TYPE_LABELS = new Map<string, string>([
  ["RESUME", "简历"],
  ["JOB_DESCRIPTION", "岗位 JD"],
  ["PROJECT_MATERIAL", "项目材料"],
  ["OFFER_MATERIAL", "Offer 材料"],
  ["SUPPLEMENTARY", "补充材料"],
]);
const SOURCE_PAGE_LABELS: Record<string, string> = {
  MENTOR_MARKETPLACE: "导师广场主列表",
  MENTOR_MARKETPLACE_RECOMMENDATION: "导师广场 AI 推荐区",
  MENTOR_MARKETPLACE_FAVORITES: "导师广场收藏列表",
};

const statusConfigMap: Record<string, StatusConfig> = {
  CREATED: { label: "待支付", colorClass: "text-slate-600 bg-slate-50 border-slate-200", icon: Clock },
  PAYING: { label: "支付中", colorClass: "text-cyan-600 bg-cyan-50 border-cyan-200", icon: Clock },
  PAID: { label: "待回复", colorClass: "text-amber-600 bg-amber-50 border-amber-200", icon: Clock },
  ANSWERED: { label: "已回复待确认", colorClass: "text-blue-600 bg-blue-50 border-blue-200", icon: MessageSquare },
  CLOSED: { label: "已完成", colorClass: "text-emerald-600 bg-emerald-50 border-emerald-200", icon: CheckCircle2 },
  REFUNDED: { label: "已退款", colorClass: "text-slate-600 bg-slate-50 border-slate-200", icon: XCircle },
  CANCELED: { label: "已取消", colorClass: "text-slate-500 bg-slate-50 border-slate-200", icon: XCircle },
  FAILED: { label: "支付失败", colorClass: "text-rose-600 bg-rose-50 border-rose-200", icon: AlertCircle },
};

const containerVariants = {
  hidden: { opacity: 0 },
  show: { opacity: 1, transition: { staggerChildren: 0.08 } },
};

const itemVariants = {
  hidden: { opacity: 0, y: 24 },
  show: { opacity: 1, y: 0, transition: { type: "spring" as const, stiffness: 250, damping: 24 } },
};

function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function getInitial(name: string) {
  return name.trim().charAt(0).toUpperCase() || "导";
}

function parseTime(value?: number | string | null) {
  return toTimestamp(value);
}

function trimText(value?: string | null) {
  return value?.trim() ?? "";
}

function getOrderStatusConfig(status: string) {
  return statusConfigMap[status] ?? statusConfigMap.CREATED;
}

function getOrderStudentName(order: ConsultOrderSummary) {
  return order.counterpartDisplayName?.trim() || `学生 #${order.counterpartUserId}`;
}

function getStudentInitial(name: string) {
  return name.trim().charAt(0).toUpperCase() || "学";
}

function isAppointmentOrder(order: Pick<ConsultOrderSummary, "appointmentStartAt" | "appointmentEndAt"> | Pick<ConsultOrderDetailResponse, "appointmentStartAt" | "appointmentEndAt">) {
  return Boolean(order.appointmentStartAt || order.appointmentEndAt);
}

function getServiceTypeLabel(order: Pick<ConsultOrderSummary, "appointmentStartAt" | "appointmentEndAt"> | Pick<ConsultOrderDetailResponse, "appointmentStartAt" | "appointmentEndAt">) {
  return isAppointmentOrder(order) ? "预约咨询" : "图文咨询";
}

function isPaidOrderStatus(status?: string | null) {
  return status === "PAID" || status === "ANSWERED" || status === "CLOSED" || status === "REFUNDED";
}

function getPaymentModeLabel(value?: string | null, status?: string | null) {
  if (!value && isPaidOrderStatus(status)) {
    return "支付记录待补齐";
  }
  if (!value) {
    return "待支付";
  }
  if (value === "MOCK") {
    return "模拟支付";
  }
  if (value === "SANDBOX") {
    return "支付宝沙箱";
  }
  return value;
}

function getAttachmentTypeLabel(type?: string | null) {
  if (!type) {
    return "未标记材料";
  }
  return MATERIAL_TYPE_LABELS.get(type) ?? type;
}

function getSourceLabel(sourcePage?: string | null) {
  if (!sourcePage) {
    return "导师广场";
  }
  return SOURCE_PAGE_LABELS[sourcePage] ?? sourcePage;
}

function formatAppointmentSummary(order: Pick<ConsultOrderSummary, "appointmentStartAt" | "appointmentEndAt"> | Pick<ConsultOrderDetailResponse, "appointmentStartAt" | "appointmentEndAt">) {
  if (!order.appointmentStartAt && !order.appointmentEndAt) {
    return "图文咨询，无固定预约时段";
  }
  if (order.appointmentStartAt && order.appointmentEndAt) {
    return `${formatDateTime(order.appointmentStartAt)} - ${formatDateTime(order.appointmentEndAt)}`;
  }
  return formatDateTime(order.appointmentStartAt ?? order.appointmentEndAt);
}

function getSceneLabel(detail?: Pick<ConsultOrderDetailResponse, "sceneCode" | "prepSheetSnapshot" | "appointmentStartAt" | "appointmentEndAt"> | null) {
  return resolveConsultSceneLabel({
    sceneCode: detail?.sceneCode,
    prepScene: detail?.prepSheetSnapshot?.scene,
    hasAppointment: detail ? isAppointmentOrder(detail) : false,
  });
}

function resolveServiceFilter(value: string | null): ServiceFilter {
  return value === "TEXT" || value === "APPOINTMENT" ? value : "ALL";
}

function resolveTimeFilter(value: string | null): TimeFilter {
  return value === "CREATED_7D" || value === "PAID_7D" || value === "UPCOMING_APPOINTMENT" ? value : "ALL";
}

function resolveRiskFilter(value: string | null): RiskFilter {
  return value === "EXPIRING" || value === "NEED_CONFIRMATION" || value === "AFTER_SALES" ? value : "ALL";
}

function resolveSortMode(value: string | null): SortMode {
  return value === "LATEST_CREATED" || value === "LATEST_PAID" || value === "DEADLINE_ASC" || value === "AMOUNT_DESC"
    ? value
    : "PRIORITY";
}

function isDeadlineExpired(deadlineAt?: number | string | null) {
  const timestamp = parseTime(deadlineAt);
  return Number.isFinite(timestamp) && timestamp <= Date.now();
}

function isDeadlineExpiringSoon(deadlineAt?: number | string | null) {
  const timestamp = parseTime(deadlineAt);
  if (!Number.isFinite(timestamp) || timestamp <= Date.now()) {
    return false;
  }
  return timestamp - Date.now() <= EXPIRING_SOON_WINDOW_MS;
}

function formatRemainingTime(deadlineAt?: number | string | null) {
  const timestamp = parseTime(deadlineAt);
  if (!Number.isFinite(timestamp)) {
    return null;
  }

  const diff = timestamp - Date.now();
  const absolute = Math.abs(diff);
  const hours = Math.floor(absolute / (60 * 60 * 1000));
  const minutes = Math.floor((absolute % (60 * 60 * 1000)) / (60 * 1000));

  if (diff <= 0) {
    if (hours > 0) {
      return `已超时 ${hours}h`;
    }
    return `已超时 ${Math.max(minutes, 1)}m`;
  }

  if (hours > 0) {
    return `剩余 ${hours}h ${minutes}m`;
  }

  return `剩余 ${Math.max(minutes, 1)}m`;
}

function getLatestAfterSalesRequest(detail?: ConsultOrderDetailResponse | null) {
  return detail?.afterSalesRequests?.[0] ?? null;
}

function getVisiblePageNumbers(currentPage: number, totalPages: number) {
  const start = Math.max(1, currentPage - 1);
  const end = Math.min(totalPages, start + 2);
  const normalizedStart = Math.max(1, end - 2);
  return Array.from({ length: end - normalizedStart + 1 }, (_, index) => normalizedStart + index);
}

function resolvePositivePage(rawValue?: string | null) {
  const parsed = Number(rawValue);
  return Number.isFinite(parsed) && parsed >= 1 ? Math.floor(parsed) : 1;
}

function toSummaryFromDetail(detail: ConsultOrderDetailResponse): ConsultOrderSummary {
  const latestAfterSales = detail.afterSalesRequests?.[0] ?? null;
  const pendingAfterSales = detail.afterSalesRequests?.some((item) => item.status === "PENDING") ?? false;
  return {
    orderNo: detail.orderNo,
    counterpartUserId: detail.studentUserId,
    counterpartDisplayName: detail.studentDisplayName,
    amountFen: detail.amountFen,
    status: detail.status,
    questionText: detail.questionText,
    paymentMode: detail.paymentMode,
    appointmentStartAt: detail.appointmentStartAt,
    appointmentEndAt: detail.appointmentEndAt,
    createdAt: detail.createdAt,
    paidAt: detail.paidAt,
    closedAt: detail.closedAt,
    autoCancelAt: detail.autoCancelAt,
    mentorReplyDeadlineAt: detail.mentorReplyDeadlineAt,
    afterSalesImpact: detail.status === "REFUNDED" || Boolean(detail.afterSalesRequests?.length),
    pendingAfterSales,
    latestAfterSalesStatus: latestAfterSales?.status ?? null,
  };
}

function OrderDetailModal({
  order,
  detail,
  loading,
  copied,
  onCopy,
  onClose,
  onEnterWorkspace,
}: {
  order: ConsultOrderSummary;
  detail: ConsultOrderDetailResponse | null;
  loading: boolean;
  copied: boolean;
  onCopy: () => void;
  onClose: () => void;
  onEnterWorkspace: () => void;
}) {
  const statusConfig = getOrderStatusConfig(detail?.status ?? order.status);
  const StatusIcon = statusConfig.icon;
  const latestAfterSales = getLatestAfterSalesRequest(detail);
  const sceneLabel = getSceneLabel(detail);
  const sourceLabel = getSourceLabel(detail?.sourcePage);
  const questionBlocks = [
    { title: "本次最想优先解决的问题", value: detail?.questionPayload?.primaryConcern || detail?.problemSummary, tone: "border-rose-100 bg-rose-50/70" },
    { title: "学生背景情况", value: detail?.questionPayload?.background, tone: "border-sky-100 bg-sky-50/70" },
    { title: "已经尝试过什么", value: detail?.questionPayload?.attemptedActions, tone: "border-violet-100 bg-violet-50/70" },
    { title: "希望导师提供的帮助", value: detail?.questionPayload?.expectedHelp, tone: "border-emerald-100 bg-emerald-50/70" },
    { title: "额外补充说明", value: detail?.questionPayload?.additionalNotes, tone: "border-amber-100 bg-amber-50/70" },
  ];
  const coreQuestions = detail?.coreQuestions?.length ? detail.coreQuestions : detail?.prepSheetSnapshot?.coreQuestions ?? [];
  const expectedOutcomes = detail?.expectedOutcomes?.length ? detail.expectedOutcomes : detail?.prepSheetSnapshot?.expectedOutcomes ?? [];
  const currentMaterialTypes = detail?.attachmentsSummary?.currentMaterialTypes?.length
    ? detail.attachmentsSummary.currentMaterialTypes
    : detail?.selectedMaterialTypes ?? [];
  const currentAttachments = detail?.attachmentsSummary?.records ?? [];
  const studentProfile = detail?.studentProfile ?? null;
  const studentSkillTags = studentProfile?.skillTags ?? [];
  const studentPortraitTags = studentProfile?.portraitTags ?? [];
  const studentAcademicMeta = [trimText(studentProfile?.schoolName), trimText(studentProfile?.major)].filter(Boolean).join(" · ");
  const studentStageMeta = [trimText(studentProfile?.grade), trimText(studentProfile?.gpa)].filter(Boolean).join(" · ");
  const appointmentSummary = formatAppointmentSummary(detail ?? order);
  const replyDeadlineSummary = detail?.mentorReplyDeadlineAt
    ? `${formatDateTime(detail.mentorReplyDeadlineAt)}${formatRemainingTime(detail.mentorReplyDeadlineAt) ? ` · ${formatRemainingTime(detail.mentorReplyDeadlineAt)}` : ""}`
    : "待支付后生成";
  const modalInfoItems = [
    { label: "学生昵称", value: detail?.studentDisplayName ?? getOrderStudentName(order) },
    { label: "服务类型", value: getServiceTypeLabel(detail ?? order) },
    { label: "咨询场景", value: sceneLabel },
    { label: "来源入口", value: sourceLabel },
    { label: "支付方式", value: getPaymentModeLabel(detail?.paymentMode ?? order.paymentMode, detail?.status ?? order.status) },
    { label: "预约时间", value: appointmentSummary },
    {
      label: "回复截止",
      value: replyDeadlineSummary,
      valueClassName: isDeadlineExpired(detail?.mentorReplyDeadlineAt) ? "text-rose-600" : "text-slate-900",
    },
    { label: "求职状态", value: trimText(studentProfile?.jobStatus) || "待补充" },
    { label: "学校 / 专业", value: studentAcademicMeta || "待补充" },
    { label: "年级 / GPA", value: studentStageMeta || "待补充" },
    { label: "目标岗位", value: trimText(studentProfile?.targetPosition) || "待补充" },
  ];

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.16, ease: "easeOut" }}
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4 backdrop-blur-sm sm:p-6"
      onClick={onClose}
    >
      <motion.div
        initial={{ y: 12, opacity: 0.96 }}
        animate={{ y: 0, opacity: 1 }}
        exit={{ y: 8, opacity: 0 }}
        transition={{ duration: 0.18, ease: "easeOut" }}
        className="relative flex max-h-[90vh] w-full max-w-5xl flex-col overflow-hidden rounded-[2rem] bg-white text-base shadow-[0_35px_80px_rgba(15,23,42,0.2)]"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-slate-100 bg-slate-50/60 px-6 py-4">
          <div className="flex items-center gap-3">
            <div className={joinClasses("flex h-10 w-10 items-center justify-center rounded-xl border", statusConfig.colorClass)}>
              <StatusIcon size={20} />
            </div>
            <div>
              <h3 className="text-[22px] font-black tracking-tight text-slate-900">订单详情快照</h3>
              <p className="text-[13px] font-medium tracking-wide text-slate-500">{order.orderNo}</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="flex h-9 w-9 items-center justify-center rounded-full bg-white text-slate-400 shadow-sm ring-1 ring-slate-200 transition-all hover:bg-slate-50 hover:text-slate-700 active:scale-90"
          >
            <X size={18} />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto px-6 py-6">
          {loading && !detail ? (
            <div className="space-y-5">
              <div className="h-24 rounded-3xl bg-slate-100" />
              <div className="h-36 rounded-3xl bg-slate-100" />
              <div className="grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(18rem,0.8fr)]">
                <div className="h-72 rounded-3xl bg-slate-100" />
                <div className="h-72 rounded-3xl bg-slate-100" />
              </div>
            </div>
          ) : (
            <div className="space-y-6">
              <div className="rounded-[1.6rem] border border-slate-200 bg-slate-50/90 p-5 shadow-[0_14px_34px_rgba(15,23,42,0.08)]">
                <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
                  <div className="min-w-0 flex-1">
                    <div className="text-[14px] font-semibold tracking-[0.12em] text-slate-500">当前状态</div>
                    <div className="mt-3 flex flex-wrap items-center gap-2.5">
                      <span className={joinClasses("rounded-full border px-4 py-2 text-base font-bold shadow-sm", statusConfig.colorClass)}>
                        {statusConfig.label}
                      </span>
                      {latestAfterSales ? (
                        <span className="inline-flex items-center gap-1.5 rounded-full border border-rose-200 bg-rose-50 px-4 py-2 text-base font-bold text-rose-600 shadow-sm">
                          <AlertCircle size={14} />
                          售后 {latestAfterSales.status === "PENDING" ? "处理中" : "已留痕"}
                        </span>
                      ) : null}
                      {(detail?.status ?? order.status) === "ANSWERED" ? (
                        <span className="rounded-full border border-blue-200 bg-blue-50 px-4 py-2 text-base font-bold text-blue-600 shadow-sm">
                          等待学生确认
                        </span>
                      ) : null}
                    </div>
                  </div>
                  <div className="w-full shrink-0 rounded-[1.35rem] border border-slate-200 bg-white px-5 py-4 shadow-sm xl:w-[17rem]">
                    <div className="text-[14px] font-semibold tracking-[0.12em] text-slate-500">订单金额</div>
                    <div className="mt-2 text-[32px] font-black tracking-tight text-slate-900">{formatMoneyFen(order.amountFen)}</div>
                  </div>
                </div>
              </div>

              <div>
                <h4 className="flex items-center gap-2 text-[19px] font-bold text-slate-900">
                  <MessageSquare size={16} className="text-indigo-500" />
                  咨询问题与结构化快照
                </h4>
                <div className="mt-3 rounded-2xl border border-slate-200 bg-slate-50 p-4">
                  <p className="text-base leading-8 text-slate-700">
                    {trimText(detail?.questionText ?? order.questionText) || "学生尚未补充问题描述。"}
                  </p>
                </div>
                <div className="mt-3 grid gap-3 sm:grid-cols-2">
                  {questionBlocks.map((item) => (
                    <div key={item.title} className={joinClasses("rounded-2xl border p-4", item.tone, item.title === "额外补充说明" && "sm:col-span-2")}>
                      <div className="text-[17px] font-bold text-slate-800">{item.title}</div>
                      <div className="mt-3 text-base leading-8 text-slate-600">{trimText(item.value) || "当前没有补充这一部分内容。"}</div>
                    </div>
                  ))}
                </div>
              </div>

              <div className="grid gap-6">
                <div className="grid gap-6 xl:grid-cols-[minmax(0,1.05fr)_minmax(25rem,0.95fr)]">
                  <div>
                    <div className="grid gap-4 lg:grid-cols-2">
                    <div className="rounded-2xl border border-slate-200 bg-white p-4">
                      <div className="text-[17px] font-bold text-slate-900">核心问题清单</div>
                      <div className="mt-4 space-y-3">
                        {coreQuestions.length ? (
                          coreQuestions.map((question, index) => (
                            <div key={`${question}-${index}`} className="rounded-xl border border-slate-100 bg-slate-50 px-4 py-3 text-base leading-8 text-slate-700">
                              <span className="mr-2 inline-flex h-6 w-6 items-center justify-center rounded-full bg-white text-sm font-bold text-slate-500">{index + 1}</span>
                              {question}
                            </div>
                          ))
                        ) : (
                          <div className="rounded-xl border border-dashed border-slate-200 bg-slate-50 px-4 py-5 text-base text-slate-500">
                            当前没有结构化核心问题，导师可先按问题原文和消息线程继续判断。
                          </div>
                        )}
                      </div>
                    </div>

                    <div className="rounded-2xl border border-slate-200 bg-white p-4">
                      <div className="text-[17px] font-bold text-slate-900">期望结果与准备单</div>
                      <div className="mt-4 flex flex-wrap gap-2">
                        {expectedOutcomes.length ? (
                          expectedOutcomes.map((item) => (
                            <span key={item} className="rounded-full border border-indigo-100 bg-indigo-50 px-3 py-2 text-base font-semibold text-indigo-700">
                              {item}
                            </span>
                          ))
                        ) : (
                          <span className="text-base text-slate-500">当前没有填写明确的预期结果。</span>
                        )}
                      </div>
                      <div className="mt-4 rounded-xl border border-slate-100 bg-slate-50 p-4 text-base leading-8 text-slate-600">
                        {trimText(detail?.prepSheetSnapshot?.summaryDraft) || "当前没有额外准备单摘要。"}
                      </div>
                      <div className="mt-3 flex flex-wrap gap-2">
                        {detail?.prepSheetSnapshot?.suggestedMaterials?.length ? (
                          detail.prepSheetSnapshot.suggestedMaterials.map((item) => (
                            <span key={item} className="rounded-full border border-slate-200 bg-white px-3 py-1.5 text-[13px] font-semibold text-slate-600">
                              {item}
                            </span>
                          ))
                        ) : (
                          <span className="text-[13px] text-slate-500">当前没有额外建议材料。</span>
                        )}
                      </div>
                    </div>
                  </div>
                </div>

                <div className="rounded-2xl border border-slate-200 bg-white p-4">
                  <h4 className="flex items-center gap-2 text-[19px] font-bold text-slate-900">
                    <FileText size={16} className="text-amber-500" />
                    当前材料摘要
                  </h4>
                  <div className="mt-3 flex items-center justify-between gap-3">
                    <div className="text-[17px] font-bold text-slate-900">已同步有效材料</div>
                    <span className="rounded-full border border-emerald-100 bg-emerald-50 px-3 py-1 text-sm font-bold text-emerald-700">
                      {detail?.attachmentsSummary?.currentAttachmentCount ?? 0} 份
                    </span>
                  </div>
                  <div className="mt-4 flex flex-wrap gap-2">
                    {currentMaterialTypes.length ? (
                      currentMaterialTypes.map((type) => (
                        <span key={type} className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1.5 text-sm font-medium text-slate-700">
                          {getAttachmentTypeLabel(type)}
                        </span>
                      ))
                    ) : (
                      <span className="text-base text-slate-500">当前还没有同步材料类型。</span>
                    )}
                  </div>
                  <div className="mt-4 grid gap-2 md:grid-cols-2 xl:grid-cols-4">
                    {currentAttachments.length ? (
                      currentAttachments.slice(0, 4).map((attachment) => (
                        <div key={attachment.attachmentId} className="rounded-xl border border-slate-100 bg-slate-50 px-3 py-2.5">
                          <div className="truncate text-base font-semibold text-slate-800">{attachment.originalFilename}</div>
                          <div className="mt-1 text-[13px] text-slate-500">
                            {getAttachmentTypeLabel(attachment.attachmentType)}
                            {attachment.description ? ` · ${attachment.description}` : ""}
                          </div>
                        </div>
                      ))
                    ) : (
                      <div className="rounded-xl border border-dashed border-slate-200 bg-slate-50 px-4 py-5 text-base text-slate-500 md:col-span-2 xl:col-span-4">
                        当前没有附件清单，导师可进入履约区继续阅读或补充材料。
                      </div>
                    )}
                  </div>
                  {currentAttachments.length > 4 ? (
                    <div className="mt-3 text-[13px] text-slate-500">还有 {currentAttachments.length - 4} 份材料已在履约区详情中同步。</div>
                  ) : null}
                </div>

                <div className="space-y-4 xl:col-span-2">
                  <div className="rounded-2xl border border-slate-200 bg-slate-50/70 p-4">
                    {detail?.review ? (
                      <div className="flex flex-col gap-4 xl:flex-row xl:items-start">
                        <div className="w-full shrink-0 xl:w-40">
                          <div className="text-[18px] font-bold text-slate-900">评价反馈</div>
                          <div className="mt-2 inline-flex rounded-full border border-amber-200 bg-amber-50 px-3 py-1 text-sm font-bold text-amber-600">
                            {detail.review.rating} / 5 分
                          </div>
                        </div>
                        <p className="min-w-0 flex-1 text-base leading-8 text-slate-700">
                          {trimText(detail.review.comment) || "学生未留下文字评价。"}
                        </p>
                        <div className="shrink-0 text-[13px] font-medium text-slate-500 xl:w-56 xl:text-right">
                          评价时间 {formatDateTime(detail.review.createdAt)}
                        </div>
                      </div>
                    ) : (
                      <div className="flex flex-col gap-4 xl:flex-row xl:items-center">
                        <div className="w-full shrink-0 text-[18px] font-bold text-slate-900 xl:w-40">评价反馈</div>
                        <div className="min-w-0 flex-1 text-base leading-8 text-slate-400">当前订单还没有评价记录。</div>
                      </div>
                    )}
                  </div>

                  <div className="rounded-2xl border border-slate-200 bg-slate-50/70 p-4">
                    {latestAfterSales ? (
                      <div className="flex flex-col gap-4 xl:flex-row xl:items-start">
                        <div className="w-full shrink-0 xl:w-40">
                          <div className="text-[18px] font-bold text-slate-900">售后摘要</div>
                          <div className="mt-2 inline-flex rounded-full border border-rose-200 bg-rose-50 px-3 py-1 text-sm font-bold text-rose-600">
                            {latestAfterSales.status === "PENDING" ? "待审核" : latestAfterSales.status === "APPROVED" ? "已通过" : latestAfterSales.status === "REJECTED" ? "已拒绝" : latestAfterSales.status}
                          </div>
                        </div>
                        <p className="min-w-0 flex-1 text-base leading-8 text-slate-700">{latestAfterSales.reason}</p>
                        <div className="shrink-0 text-[13px] font-medium text-slate-500 xl:w-56 xl:text-right">
                          提交 {formatDateTime(latestAfterSales.createdAt)}
                          {latestAfterSales.reviewedAt ? ` · 审核 ${formatDateTime(latestAfterSales.reviewedAt)}` : ""}
                        </div>
                      </div>
                    ) : (
                      <div className="flex flex-col gap-4 xl:flex-row xl:items-center">
                        <div className="w-full shrink-0 text-[18px] font-bold text-slate-900 xl:w-40">售后摘要</div>
                        <div className="min-w-0 flex-1 text-base leading-8 text-slate-400">当前订单暂无售后记录。</div>
                      </div>
                    )}
                  </div>
                </div>
              </div>

                <div className="rounded-2xl border border-slate-200 bg-white p-4">
                  <h4 className="flex items-center gap-2 text-[19px] font-bold text-slate-900">
                    <User size={16} className="text-teal-500" />
                    订单来源与服务信息
                  </h4>
                  <div className="mt-3 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                    {modalInfoItems.map((item) => (
                      <div key={item.label} className="rounded-xl border border-slate-100 bg-slate-50 px-3.5 py-3">
                        <div className="text-[13px] font-semibold text-slate-500">{item.label}</div>
                        <div className={joinClasses("mt-2 text-base font-semibold leading-8 text-slate-900", item.valueClassName)}>
                          {item.value}
                        </div>
                      </div>
                    ))}
                  </div>
                  <div className="mt-3 grid gap-3 xl:grid-cols-[minmax(0,1fr)_minmax(0,1.2fr)_minmax(0,1.2fr)]">
                    <div className="rounded-xl border border-slate-100 bg-slate-50 px-3 py-3">
                      <div className="text-[13px] font-semibold text-slate-500">技能标签</div>
                      <div className="mt-2 flex flex-wrap gap-2">
                        {studentSkillTags.length ? (
                          studentSkillTags.map((item) => (
                            <span key={item} className="rounded-full border border-sky-100 bg-sky-50 px-2.5 py-1 text-sm font-semibold text-sky-700">
                              {item}
                            </span>
                          ))
                        ) : (
                          <span className="text-[13px] text-slate-500">当前没有同步技能标签。</span>
                        )}
                      </div>
                    </div>
                    <div className="rounded-xl border border-slate-100 bg-slate-50 px-3 py-3">
                      <div className="flex items-center justify-between gap-3">
                          <div className="text-[13px] font-semibold text-slate-500">画像标签</div>
                          <div className="text-[13px] text-slate-400">
                          {studentProfile?.portraitUpdatedAt ? formatDateTime(studentProfile.portraitUpdatedAt) : "未同步"}
                        </div>
                      </div>
                      <div className="mt-2 flex flex-wrap gap-2">
                        {studentPortraitTags.length ? (
                          studentPortraitTags.map((item, index) => (
                            <span
                              key={`${item.code ?? item.label ?? "portrait"}-${index}`}
                              className="rounded-full border border-violet-100 bg-violet-50 px-2.5 py-1 text-sm font-semibold text-violet-700"
                            >
                              {item.label ?? item.code ?? "画像标签"}
                            </span>
                          ))
                        ) : (
                          <span className="text-[13px] text-slate-500">当前没有同步动态画像。</span>
                        )}
                      </div>
                    </div>
                    <div className="rounded-xl border border-slate-100 bg-slate-50 px-3 py-3 text-base leading-8 text-slate-600">
                      {trimText(studentProfile?.selfIntro) || trimText(studentProfile?.honors) || "当前没有更多学生侧补充简介。"}
                    </div>
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>

        <div className="flex items-center justify-between gap-4 border-t border-slate-100 bg-white p-5">
          <button
            type="button"
            onClick={onCopy}
            className="rounded-xl px-4 py-2.5 text-[15px] font-medium text-slate-600 transition-all hover:bg-slate-100 active:scale-95"
          >
            {copied ? "已复制订单号" : "复制订单号"}
          </button>
          <button
            type="button"
            onClick={onEnterWorkspace}
            className="flex max-w-xs flex-1 items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-indigo-600 to-violet-600 px-6 py-3 text-[15px] font-bold text-white shadow-[0_8px_20px_rgba(79,70,229,0.25)] transition-all hover:-translate-y-0.5 hover:shadow-[0_12px_25px_rgba(79,70,229,0.35)] active:scale-[0.98]"
          >
            进入履约工作区
            <ArrowUpRight size={16} />
          </button>
        </div>
      </motion.div>
    </motion.div>
  );
}

export default function MentorOrderCenterPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { displayName, userId } = useAuth();
  const [orders, setOrders] = useState<ConsultOrderSummary[]>([]);
  const [summary, setSummary] = useState<ConsultOrderListResponse["summary"]>({
    pendingReplyCount: 0,
    expiringSoonCount: 0,
    waitingConfirmationCount: 0,
    afterSalesImpactCount: 0,
  });
  const [ordersTotal, setOrdersTotal] = useState(0);
  const [availablePaymentModes, setAvailablePaymentModes] = useState<string[]>([]);
  const [ordersLoading, setOrdersLoading] = useState(true);
  const [ordersError, setOrdersError] = useState<string | null>(null);
  const [detailMap, setDetailMap] = useState<Record<string, ConsultOrderDetailResponse>>({});
  const [selectedOrderNo, setSelectedOrderNo] = useState<string | null>(() => searchParams.get("orderNo")?.trim() || null);
  const [selectedOrderFallback, setSelectedOrderFallback] = useState<ConsultOrderSummary | null>(null);
  const [selectedOrderLoading, setSelectedOrderLoading] = useState(false);
  const [refreshSeed, setRefreshSeed] = useState(0);
  const [searchQuery, setSearchQuery] = useState(() => searchParams.get("keyword") ?? "");
  const [statusFilter, setStatusFilter] = useState(() => searchParams.get("status") ?? "ALL");
  const [serviceFilter, setServiceFilter] = useState<ServiceFilter>(() => resolveServiceFilter(searchParams.get("service")));
  const [timeFilter, setTimeFilter] = useState<TimeFilter>(() => resolveTimeFilter(searchParams.get("time")));
  const [paymentFilter, setPaymentFilter] = useState(() => searchParams.get("paymentMode") ?? "ALL");
  const [riskFilter, setRiskFilter] = useState<RiskFilter>(() => resolveRiskFilter(searchParams.get("risk")));
  const [sortMode, setSortMode] = useState<SortMode>(() => resolveSortMode(searchParams.get("sort")));
  const [currentPage, setCurrentPage] = useState(() => resolvePositivePage(searchParams.get("page")));
  const [lastUpdatedAt, setLastUpdatedAt] = useState<string | null>(null);
  const [copiedOrderNo, setCopiedOrderNo] = useState<string | null>(null);
  const detailMapRef = useRef<Record<string, ConsultOrderDetailResponse>>({});
  const searchParamSignatureRef = useRef(searchParams.toString());
  const skipSearchParamWriteRef = useRef(false);
  const snapshotKey = buildWorkspaceSnapshotStorageKey("mentor", "orders", userId ?? "current");

  const persistOrderCenterSnapshot = (
    nextSummary: ConsultOrderListResponse["summary"],
    nextOrders: ConsultOrderSummary[],
    nextOrdersTotal: number,
    nextAvailablePaymentModes: string[],
    nextDetailMap: Record<string, ConsultOrderDetailResponse>,
    updatedAt = lastUpdatedAt ?? new Date().toISOString(),
  ) => {
    // 订单中心快照包含列表摘要和已打开详情，返回页面时先回显再刷新。
    writeWorkspaceSnapshot(snapshotKey, {
      summary: nextSummary,
      orders: nextOrders,
      ordersTotal: nextOrdersTotal,
      availablePaymentModes: nextAvailablePaymentModes,
      detailMap: nextDetailMap,
    } satisfies MentorOrderCenterSnapshot, updatedAt);
  };

  useEffect(() => {
    detailMapRef.current = detailMap;
  }, [detailMap]);

  useEffect(() => {
    const currentSignature = searchParams.toString();
    if (currentSignature === searchParamSignatureRef.current) {
      return;
    }

    skipSearchParamWriteRef.current = true;
    searchParamSignatureRef.current = currentSignature;
    setSelectedOrderNo(searchParams.get("orderNo")?.trim() || null);
    setSearchQuery(searchParams.get("keyword") ?? "");
    setStatusFilter(searchParams.get("status") ?? "ALL");
    setServiceFilter(resolveServiceFilter(searchParams.get("service")));
    setTimeFilter(resolveTimeFilter(searchParams.get("time")));
    setPaymentFilter(searchParams.get("paymentMode") ?? "ALL");
    setRiskFilter(resolveRiskFilter(searchParams.get("risk")));
    setSortMode(resolveSortMode(searchParams.get("sort")));
    setCurrentPage(resolvePositivePage(searchParams.get("page")));
  }, [searchParams]);

  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    const snapshot = readWorkspaceSnapshot<MentorOrderCenterSnapshot>(snapshotKey);

    if (snapshot?.data) {
      // service-side workbench 是真数据源；快照只用于减少回访白屏。
      setSummary(snapshot.data.summary ?? {
        pendingReplyCount: 0,
        expiringSoonCount: 0,
        waitingConfirmationCount: 0,
        afterSalesImpactCount: 0,
      });
      setOrders(snapshot.data.orders);
      setOrdersTotal(snapshot.data.ordersTotal);
      setAvailablePaymentModes(snapshot.data.availablePaymentModes ?? []);
      setDetailMap(snapshot.data.detailMap);
      setLastUpdatedAt(snapshot.updatedAt);
      setOrdersError(null);
    }

    const loadOrders = async () => {
      setOrdersLoading(true);
      setOrdersError(null);

      try {
        // 筛选、排序和风险条件都交给 mentor-workbench 后端聚合处理。
        const response = await apiRequest<ConsultOrderListResponse>(
          `/consult/orders/mentor-workbench${buildQuery({
            page: currentPage,
            size: CLIENT_PAGE_SIZE,
            keyword: searchQuery.trim() || undefined,
            status: statusFilter !== "ALL" ? statusFilter : undefined,
            serviceFilter: serviceFilter !== "ALL" ? serviceFilter : undefined,
            timeFilter: timeFilter !== "ALL" ? timeFilter : undefined,
            paymentMode: paymentFilter !== "ALL" ? paymentFilter : undefined,
            riskFilter: riskFilter !== "ALL" ? riskFilter : undefined,
            sortMode: sortMode !== "PRIORITY" ? sortMode : undefined,
          })}`,
          { signal: controller.signal },
        );
        if (!active) {
          return;
        }

        setSummary(response.summary);
        setOrders(response.records);
        setOrdersTotal(response.total);
        setAvailablePaymentModes(response.availablePaymentModes);
        const updatedAt = new Date().toISOString();
        setLastUpdatedAt(updatedAt);
        persistOrderCenterSnapshot(response.summary, response.records, response.total, response.availablePaymentModes, detailMapRef.current, updatedAt);
      } catch (error) {
        if (isAbortError(error)) {
          return;
        }
        const apiError = error as ApiClientError;
        if (active) {
          if (snapshot?.data) {
            setOrdersError(null);
          } else {
            setOrdersError(apiError.message || "导师订单加载失败");
            setSummary({
              pendingReplyCount: 0,
              expiringSoonCount: 0,
              waitingConfirmationCount: 0,
              afterSalesImpactCount: 0,
            });
            setOrders([]);
            setOrdersTotal(0);
            setAvailablePaymentModes([]);
          }
        }
      } finally {
        if (active) {
          setOrdersLoading(false);
        }
      }
    };

    void loadOrders();

    return () => {
      active = false;
      controller.abort();
    };
  }, [currentPage, paymentFilter, refreshSeed, riskFilter, searchQuery, serviceFilter, snapshotKey, sortMode, statusFilter, timeFilter]);

  useEffect(() => {
    if (!selectedOrderNo) {
      setSelectedOrderFallback(null);
      return;
    }

    const currentPageOrder = orders.find((item) => item.orderNo === selectedOrderNo);
    if (currentPageOrder) {
      setSelectedOrderFallback(null);
      return;
    }

    const cachedDetail = detailMapRef.current[selectedOrderNo];
    if (cachedDetail) {
      // 详情弹层先使用已缓存快照，避免从列表切回时重复等待。
      setSelectedOrderFallback(toSummaryFromDetail(cachedDetail));
      return;
    }

    let active = true;
    const controller = new AbortController();
    const loadSelectedOrder = async () => {
      setSelectedOrderFallback(null);
      setSelectedOrderLoading(true);
      try {
        const response = await apiRequest<ConsultOrderDetailResponse>(`/consult/orders/${selectedOrderNo}`, { signal: controller.signal });
        if (!active) {
          return;
        }
        setSelectedOrderFallback(toSummaryFromDetail(response));
        setDetailMap((previous) => {
          const nextDetailMap = { ...previous, [selectedOrderNo]: response };
          persistOrderCenterSnapshot(summary, orders, ordersTotal, availablePaymentModes, nextDetailMap);
          return nextDetailMap;
        });
      } catch (error) {
        if (!isAbortError(error) && active) {
          const apiError = error as ApiClientError;
          setOrdersError(apiError.message || "订单快照加载失败");
        }
      } finally {
        if (active) {
          setSelectedOrderLoading(false);
        }
      }
    };

    void loadSelectedOrder();

    return () => {
      active = false;
      controller.abort();
    };
  }, [availablePaymentModes, orders, ordersTotal, selectedOrderNo, summary]);

  useEffect(() => {
    setCurrentPage(1);
  }, [paymentFilter, riskFilter, searchQuery, serviceFilter, sortMode, statusFilter, timeFilter]);

  useEffect(() => {
    if (!copiedOrderNo) {
      return;
    }

    const timer = window.setTimeout(() => {
      setCopiedOrderNo(null);
    }, 1600);

    return () => {
      window.clearTimeout(timer);
    };
  }, [copiedOrderNo]);

  useEffect(() => {
    if (skipSearchParamWriteRef.current) {
      skipSearchParamWriteRef.current = false;
      return;
    }

    const nextParams = new URLSearchParams(searchParams);
    const writeParam = (key: string, value: string | null, defaultValue = "") => {
      if (!value || value === defaultValue) {
        nextParams.delete(key);
        return;
      }
      nextParams.set(key, value);
    };

    writeParam("keyword", searchQuery.trim());
    writeParam("status", statusFilter, "ALL");
    writeParam("service", serviceFilter, "ALL");
    writeParam("time", timeFilter, "ALL");
    writeParam("paymentMode", paymentFilter, "ALL");
    writeParam("risk", riskFilter, "ALL");
    writeParam("sort", sortMode, "PRIORITY");
    writeParam("page", currentPage > 1 ? String(currentPage) : null);
    writeParam("orderNo", selectedOrderNo);

    const nextSignature = nextParams.toString();
    if (nextSignature !== searchParams.toString()) {
      searchParamSignatureRef.current = nextSignature;
      setSearchParams(nextParams, { replace: true });
    }
  }, [currentPage, paymentFilter, riskFilter, searchParams, searchQuery, selectedOrderNo, serviceFilter, setSearchParams, sortMode, statusFilter, timeFilter]);

  const selectedOrder = selectedOrderNo
    ? orders.find((item) => item.orderNo === selectedOrderNo) ?? selectedOrderFallback ?? null
    : null;
  const selectedOrderDetail = selectedOrderNo ? detailMap[selectedOrderNo] ?? null : null;
  const totalPages = Math.max(1, Math.ceil(ordersTotal / CLIENT_PAGE_SIZE));
  const safeCurrentPage = Math.min(currentPage, totalPages);
  const pageStartIndex = (safeCurrentPage - 1) * CLIENT_PAGE_SIZE;
  const visibleOrders = orders;
  const visiblePageNumbers = getVisiblePageNumbers(safeCurrentPage, totalPages);

  useEffect(() => {
    if (currentPage > totalPages) {
      setCurrentPage(totalPages);
    }
  }, [currentPage, totalPages]);

  const handleRefresh = () => {
    setRefreshSeed((value) => value + 1);
  };

  const handleOpenSnapshot = async (orderNo: string) => {
    setSelectedOrderNo(orderNo);
    if (detailMapRef.current[orderNo]) {
      return;
    }

    // 表格行打开的是快照弹层，正式履约仍通过工作区入口进入。
    setSelectedOrderLoading(true);
    try {
      const response = await apiRequest<ConsultOrderDetailResponse>(`/consult/orders/${orderNo}`);
      setSelectedOrderFallback(toSummaryFromDetail(response));
      setDetailMap((previous) => {
        const nextDetailMap = { ...previous, [orderNo]: response };
        persistOrderCenterSnapshot(summary, orders, ordersTotal, availablePaymentModes, nextDetailMap);
        return nextDetailMap;
      });
    } catch (error) {
      const apiError = error as ApiClientError;
      setOrdersError(apiError.message || "订单快照加载失败");
    } finally {
      setSelectedOrderLoading(false);
    }
  };

  const handleCopyOrderNo = async () => {
    if (!selectedOrder) {
      return;
    }

    try {
      if (typeof navigator !== "undefined" && navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(selectedOrder.orderNo);
      }
      setCopiedOrderNo(selectedOrder.orderNo);
    } catch {
      setCopiedOrderNo(null);
    }
  };

  const handleEnterWorkspace = (orderNo: string) => {
    navigate(`/mentor/orders/${orderNo}/workspace`);
  };

  return (
    <div className="relative min-h-screen bg-slate-50 font-sans text-slate-900">
      <div className="pointer-events-none fixed inset-0 bg-[radial-gradient(ellipse_at_top,rgba(99,102,241,0.08),transparent_50%),radial-gradient(ellipse_at_top_right,rgba(20,184,166,0.05),transparent_40%)]" />

      <WorkspaceRoleTopbar
        sectionLabel="Order Center"
        title="导师订单中心"
        icon={Briefcase}
        navItems={getMentorWorkspaceNavItems("orders")}
        displayName={displayName}
        userSubtitle="继续处理咨询订单"
        userFallbackLabel="导师"
        userFallbackInitial="导"
        lastUpdatedAt={lastUpdatedAt}
        onRefresh={handleRefresh}
        refreshing={ordersLoading}
        refreshTitle="刷新导师订单中心数据"
      />

      <main className="relative mx-auto w-full max-w-[96rem] px-4 pb-6 pt-8 sm:px-6 lg:px-8 lg:pb-8 lg:pt-10">
        <motion.div variants={containerVariants} initial="hidden" animate="show" className="space-y-4 text-[15px]">
          <motion.section variants={itemVariants}>
            <div className="relative overflow-hidden rounded-[1.75rem] bg-gradient-to-br from-slate-900 via-indigo-950 to-slate-900 px-5 py-5 shadow-lg sm:px-6">
              <div className="absolute -right-10 -top-10 h-40 w-40 rounded-full bg-indigo-500/20 blur-3xl" />
              <div className="absolute bottom-0 left-10 h-24 w-24 rounded-full bg-teal-500/20 blur-2xl" />

              <div className="relative z-10 max-w-3xl">
                <div className="inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/5 px-3 py-1 text-[11px] text-indigo-200 backdrop-blur-md">
                  <Sparkles size={12} />
                  Order Management
                </div>
                <h1 className="mt-2 text-2xl font-bold tracking-tight text-white sm:text-[2rem]">分诊与履约枢纽</h1>
                <p className="mt-1.5 text-[15px] leading-7 text-indigo-100/80 sm:text-base">
                  在这里集中查看订单进展，快速定位需要优先处理的咨询。
                </p>
                <div className="mt-3 flex flex-wrap items-center gap-3 text-sm text-indigo-100/75">
                  <span>{ordersLoading ? "正在同步订单..." : `当前页 ${orders.length} 单，共 ${ordersTotal} 单订单`}</span>
                  <span>可按优先级、状态和风险快速筛选</span>
                  <span>{selectedOrderNo ? "正在查看订单快照" : "选中订单后可侧边查看快照"}</span>
                </div>
              </div>
            </div>
          </motion.section>

          <motion.section variants={itemVariants}>
            <div className="mb-2.5 flex items-center justify-between">
              <div>
                <h2 className="text-lg font-bold text-slate-900">待办焦点</h2>
                <p className="mt-1 text-sm text-slate-500">总览卡片基于当前同步到本地会话的订单视图即时计算。</p>
              </div>
            </div>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <div className="group relative overflow-hidden rounded-[1.25rem] border border-white bg-white/80 p-4 shadow-[0_8px_30px_rgba(15,23,42,0.04)] backdrop-blur-md transition-all hover:-translate-y-1 hover:shadow-[0_15px_40px_rgba(15,23,42,0.08)]">
                <div className="absolute inset-x-0 top-0 h-1 bg-gradient-to-r from-amber-400 to-orange-400 opacity-80" />
                <div className="flex items-center justify-between">
                  <div className="text-base font-semibold text-slate-500">待回复订单</div>
                  <div className="flex h-8 w-8 items-center justify-center rounded-full bg-amber-50 text-amber-600">
                    <MessageSquare size={16} />
                  </div>
                </div>
                <div className="mt-2.5 flex items-baseline gap-1.5">
                  <span className="text-2xl font-black tracking-tight text-slate-900">{summary.pendingReplyCount}</span>
                  <span className="text-sm font-medium text-slate-500">单</span>
                </div>
              </div>

              <div className="group relative overflow-hidden rounded-[1.25rem] border border-white bg-white/80 p-4 shadow-[0_8px_30px_rgba(15,23,42,0.04)] backdrop-blur-md transition-all hover:-translate-y-1 hover:shadow-[0_15px_40px_rgba(15,23,42,0.08)]">
                <div className="absolute inset-x-0 top-0 h-1 bg-gradient-to-r from-rose-400 to-red-500 opacity-80" />
                <div className="flex items-center justify-between">
                  <div className="text-base font-semibold text-slate-500">即将超时</div>
                  <div className="flex h-8 w-8 items-center justify-center rounded-full bg-rose-50 text-rose-600">
                    <Clock size={16} />
                  </div>
                </div>
                <div className="mt-2.5 flex items-baseline gap-1.5">
                  <span className="text-2xl font-black tracking-tight text-rose-600">{summary.expiringSoonCount}</span>
                  <span className="text-sm font-medium text-slate-500">单接近回复时限</span>
                </div>
              </div>

              <div className="group relative overflow-hidden rounded-[1.25rem] border border-white bg-white/80 p-4 shadow-[0_8px_30px_rgba(15,23,42,0.04)] backdrop-blur-md transition-all hover:-translate-y-1 hover:shadow-[0_15px_40px_rgba(15,23,42,0.08)]">
                <div className="absolute inset-x-0 top-0 h-1 bg-gradient-to-r from-blue-400 to-indigo-500 opacity-80" />
                <div className="flex items-center justify-between">
                  <div className="text-base font-semibold text-slate-500">已回复待确认</div>
                  <div className="flex h-8 w-8 items-center justify-center rounded-full bg-blue-50 text-blue-600">
                    <CheckCircle2 size={16} />
                  </div>
                </div>
                <div className="mt-2.5 flex items-baseline gap-1.5">
                  <span className="text-2xl font-black tracking-tight text-slate-900">{summary.waitingConfirmationCount}</span>
                  <span className="text-sm font-medium text-slate-500">单等待学生动作</span>
                </div>
              </div>

              <div className="group relative overflow-hidden rounded-[1.25rem] border border-white bg-white/80 p-4 shadow-[0_8px_30px_rgba(15,23,42,0.04)] backdrop-blur-md transition-all hover:-translate-y-1 hover:shadow-[0_15px_40px_rgba(15,23,42,0.08)]">
                <div className="absolute inset-x-0 top-0 h-1 bg-gradient-to-r from-slate-300 to-slate-400 opacity-80" />
                <div className="flex items-center justify-between">
                  <div className="text-base font-semibold text-slate-500">退款 / 售后</div>
                  <div className="flex h-8 w-8 items-center justify-center rounded-full bg-slate-100 text-slate-600">
                    <AlertCircle size={16} />
                  </div>
                </div>
                <div className="mt-2.5 flex items-baseline gap-1.5">
                  <span className="text-2xl font-black tracking-tight text-slate-900">{summary.afterSalesImpactCount}</span>
                  <span className="text-sm font-medium text-slate-500">单存在影响</span>
                </div>
              </div>
            </div>
          </motion.section>

          <motion.section
            variants={itemVariants}
            className="rounded-[1.25rem] border border-white/80 bg-white/80 px-4 py-3 shadow-[0_8px_30px_rgba(148,163,184,0.08)] backdrop-blur-xl"
          >
            <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
              <div className="relative max-w-md flex-1">
                <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5">
                  <Search size={16} className="text-slate-400" />
                </div>
                <input
                  value={searchQuery}
                  onChange={(event) => setSearchQuery(event.target.value)}
                  type="text"
                  placeholder="搜索订单号 / 学生昵称 / 问题关键词..."
                  className="w-full rounded-full border border-slate-200 bg-slate-50 py-2.5 pl-10 pr-4 text-[15px] text-slate-900 outline-none transition-all placeholder:text-slate-400 focus:border-indigo-400 focus:bg-white focus:ring-4 focus:ring-indigo-500/10"
                />
              </div>

              <div className="flex flex-wrap items-center gap-2.5">
                <label className="inline-flex items-center gap-1.5 rounded-full border border-slate-200 bg-white px-3.5 py-2.5 text-[15px] font-medium text-slate-700 shadow-sm transition-all hover:bg-slate-50">
                  <Filter size={15} className="text-slate-400" />
                  <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)} className="bg-transparent outline-none">
                    <option value="ALL">全部状态</option>
                    <option value="PAID">待回复</option>
                    <option value="ANSWERED">已回复待确认</option>
                    <option value="CLOSED">已完成</option>
                    <option value="REFUNDED">已退款</option>
                    <option value="CREATED">待支付</option>
                  </select>
                </label>

                <label className="inline-flex items-center gap-1.5 rounded-full border border-slate-200 bg-white px-3.5 py-2.5 text-[15px] font-medium text-slate-700 shadow-sm transition-all hover:bg-slate-50">
                  <select value={serviceFilter} onChange={(event) => setServiceFilter(event.target.value as ServiceFilter)} className="bg-transparent outline-none">
                    <option value="ALL">图文 / 预约</option>
                    <option value="TEXT">图文咨询</option>
                    <option value="APPOINTMENT">预约咨询</option>
                  </select>
                </label>

                <label className="inline-flex items-center gap-1.5 rounded-full border border-slate-200 bg-white px-3.5 py-2.5 text-[15px] font-medium text-slate-700 shadow-sm transition-all hover:bg-slate-50">
                  <select value={timeFilter} onChange={(event) => setTimeFilter(event.target.value as TimeFilter)} className="bg-transparent outline-none">
                    <option value="ALL">全部时间</option>
                    <option value="CREATED_7D">近 7 天创建</option>
                    <option value="PAID_7D">近 7 天支付</option>
                    <option value="UPCOMING_APPOINTMENT">待开始预约</option>
                  </select>
                </label>

                <label className="inline-flex items-center gap-1.5 rounded-full border border-slate-200 bg-white px-3.5 py-2.5 text-[15px] font-medium text-slate-700 shadow-sm transition-all hover:bg-slate-50">
                  <select value={paymentFilter} onChange={(event) => setPaymentFilter(event.target.value)} className="bg-transparent outline-none">
                    <option value="ALL">全部支付方式</option>
                    <option value="UNSET">待支付 / 未配置</option>
                    {availablePaymentModes.map((mode) => (
                      <option key={mode} value={mode}>{getPaymentModeLabel(mode)}</option>
                    ))}
                  </select>
                </label>

                <label className="inline-flex items-center gap-1.5 rounded-full border border-slate-200 bg-white px-3.5 py-2.5 text-[15px] font-medium text-slate-700 shadow-sm transition-all hover:bg-slate-50">
                  <select value={sortMode} onChange={(event) => setSortMode(event.target.value as SortMode)} className="bg-transparent outline-none">
                    <option value="PRIORITY">优先级排序</option>
                    <option value="LATEST_CREATED">最新创建优先</option>
                    <option value="LATEST_PAID">最新支付优先</option>
                    <option value="DEADLINE_ASC">最临近截止优先</option>
                    <option value="AMOUNT_DESC">金额从高到低</option>
                  </select>
                </label>

                <div className="hidden h-5 w-px bg-slate-200 sm:block" />

                <div className="flex items-center gap-1 rounded-full border border-slate-200/60 bg-slate-100/80 p-1">
                  {[
                    { value: "ALL", label: "全部" },
                    { value: "EXPIRING", label: "即将超时" },
                    { value: "NEED_CONFIRMATION", label: "需确认" },
                    { value: "AFTER_SALES", label: "售后中" },
                  ].map((item) => (
                    <button
                      key={item.value}
                      type="button"
                      onClick={() => setRiskFilter(item.value as RiskFilter)}
                      className={joinClasses(
                        "rounded-full px-3 py-1.5 text-sm transition-all active:scale-95",
                        riskFilter === item.value ? "bg-white font-bold text-slate-800 shadow-sm" : "font-medium text-slate-600 hover:text-slate-900",
                      )}
                    >
                      {item.label}
                    </button>
                  ))}
                </div>
              </div>
            </div>
          </motion.section>

          <motion.section variants={itemVariants}>
            {ordersError ? (
              <div className="rounded-[1.5rem] border border-rose-200 bg-rose-50 px-5 py-4 text-[15px] leading-7 text-rose-700">
                {ordersError}
              </div>
            ) : null}

            {ordersLoading && orders.length === 0 ? (
              <div className="space-y-3">
                {Array.from({ length: 3 }).map((_, index) => (
                  <div key={index} className="h-40 rounded-[1.5rem] border border-slate-200 bg-white" />
                ))}
              </div>
            ) : orders.length === 0 ? (
              <div className="rounded-[1.75rem] border border-slate-200 bg-white px-6 py-12 text-center shadow-sm">
                <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-slate-100 text-slate-500">
                  <Search size={20} />
                </div>
                <h3 className="mt-4 text-lg font-bold text-slate-900">暂无匹配订单</h3>
                <p className="mt-2 text-[15px] leading-7 text-slate-500">可以尝试放宽状态、风险或关键词条件，重新回到全部订单视图。</p>
              </div>
            ) : (
              <>
                <div className="space-y-3">
                  {visibleOrders.map((order) => {
                    const statusConfig = getOrderStatusConfig(order.status);
                    const StatusIcon = statusConfig.icon;
                    const hasOverdueRisk = isDeadlineExpired(order.mentorReplyDeadlineAt);
                    const hasExpiringRisk = isDeadlineExpiringSoon(order.mentorReplyDeadlineAt);
                    const riskLabel = hasOverdueRisk
                      ? "已超时"
                      : hasExpiringRisk
                        ? `即将超时 (${formatRemainingTime(order.mentorReplyDeadlineAt)})`
                        : null;
                    const latestAfterSalesStatus = order.latestAfterSalesStatus;

                    return (
                      <div
                        key={order.orderNo}
                        className={joinClasses(
                          "group relative overflow-hidden rounded-[1.5rem] border p-4 shadow-sm transition-all sm:p-5",
                          hasOverdueRisk || hasExpiringRisk
                            ? "border-rose-300 bg-rose-50/40 hover:border-rose-400 hover:shadow-[0_15px_40px_rgba(225,29,72,0.12)]"
                            : "border-slate-200 bg-white hover:border-indigo-200 hover:shadow-[0_15px_40px_rgba(79,70,229,0.08)]",
                        )}
                      >
                        <div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between">
                          <div className="max-w-3xl flex-1 space-y-3.5">
                            <div className="flex flex-wrap items-center gap-2.5">
                              <span className={joinClasses("inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-sm font-bold", statusConfig.colorClass)}>
                                <StatusIcon size={14} />
                                {statusConfig.label}
                              </span>
                              <span className="rounded-full bg-slate-100 px-2.5 py-1 text-sm font-semibold text-slate-600">
                                {getServiceTypeLabel(order)}
                              </span>
                              <span className="text-sm font-medium text-slate-400">订单号: {order.orderNo}</span>
                              {riskLabel ? (
                                <span className="inline-flex items-center gap-1.5 rounded-full bg-gradient-to-r from-rose-500 to-red-500 px-3 py-1 text-sm font-bold text-white shadow-sm ring-1 ring-rose-500/20">
                                  <span className="relative flex h-2 w-2">
                                    <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-white opacity-75" />
                                    <span className="relative inline-flex h-2 w-2 rounded-full bg-white" />
                                  </span>
                                  {riskLabel}
                                </span>
                              ) : null}
                              {order.afterSalesImpact ? (
                                <span className="inline-flex items-center gap-1 rounded-full border border-rose-200 bg-rose-50 px-3 py-1 text-sm font-bold text-rose-600">
                                  <AlertTriangle size={12} />
                                  {latestAfterSalesStatus === "PENDING" ? "售后处理中" : "存在售后记录"}
                                </span>
                              ) : null}
                            </div>

                            <div className="flex items-start gap-3.5">
                              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-indigo-100 to-blue-100 text-base font-bold text-indigo-700 shadow-inner">
                                {getStudentInitial(getOrderStudentName(order))}
                              </div>
                              <div className="space-y-1 pt-0.5">
                                <h3 className="text-lg font-bold text-slate-900">{getOrderStudentName(order)}</h3>
                                <p className="line-clamp-2 text-[15px] leading-7 text-slate-600">
                                  {order.questionText?.trim() || "学生尚未补充问题摘要。"}
                                </p>
                                <div className="flex flex-wrap gap-3 text-sm text-slate-400">
                                  <span>支付方式 {getPaymentModeLabel(order.paymentMode, order.status)}</span>
                                  <span>{order.appointmentStartAt ? `预约 ${formatDateTime(order.appointmentStartAt)}` : "未设置预约时段"}</span>
                                  <span>{order.mentorReplyDeadlineAt ? `回复截止 ${formatDateTime(order.mentorReplyDeadlineAt)}` : "支付后显示回复时限"}</span>
                                </div>
                              </div>
                            </div>
                          </div>

                          <div className="flex flex-col items-start gap-4 border-t border-slate-100/80 pt-4 lg:items-end lg:border-l lg:border-t-0 lg:pl-5 lg:pt-0">
                            <div className="flex w-full items-center justify-between lg:w-auto lg:flex-col lg:items-end lg:gap-1">
                              <div className="text-xl font-black text-slate-900">{formatMoneyFen(order.amountFen)}</div>
                              <div className="text-sm text-slate-400">创建于 {formatDateTime(order.createdAt)}</div>
                            </div>

                            <div className="flex w-full items-center gap-2.5 lg:w-auto">
                              <button
                                type="button"
                                onClick={() => void handleOpenSnapshot(order.orderNo)}
                                className="inline-flex h-10 flex-1 items-center justify-center rounded-xl border border-slate-200 bg-white px-4 text-[15px] font-semibold text-slate-700 shadow-sm transition-all hover:-translate-y-0.5 hover:bg-slate-50 active:scale-95 lg:flex-none"
                              >
                                查看快照
                              </button>
                              <button
                                type="button"
                                onClick={() => handleEnterWorkspace(order.orderNo)}
                                className={joinClasses(
                                  "inline-flex h-10 flex-1 items-center justify-center rounded-xl px-5 text-[15px] font-bold text-white shadow-md transition-all hover:-translate-y-0.5 hover:shadow-lg active:scale-95 lg:flex-none",
                                  hasOverdueRisk || hasExpiringRisk ? "bg-rose-600 hover:bg-rose-700 shadow-rose-600/20" : "bg-slate-900 hover:bg-slate-800",
                                )}
                              >
                                进入履约区
                              </button>
                            </div>
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>

                <div className="mt-6 flex flex-col items-center justify-between gap-4 border-t border-slate-200 pt-6 sm:flex-row">
                  <div className="text-[15px] text-slate-500">
                    显示 <span className="font-medium text-slate-900">{ordersTotal === 0 ? 0 : pageStartIndex + 1}</span> 到{" "}
                    <span className="font-medium text-slate-900">{Math.min(pageStartIndex + visibleOrders.length, ordersTotal)}</span> 条，共{" "}
                    <span className="font-medium text-slate-900">{ordersTotal}</span> 条订单
                  </div>
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      disabled={safeCurrentPage <= 1}
                      onClick={() => setCurrentPage((page) => Math.max(1, page - 1))}
                      className="inline-flex h-10 items-center justify-center rounded-lg border border-slate-200 bg-white px-3 text-[15px] font-medium text-slate-600 transition-all hover:bg-slate-50 active:scale-95 disabled:pointer-events-none disabled:opacity-50"
                    >
                      上一页
                    </button>
                    {visiblePageNumbers.map((pageNumber) => (
                      <button
                        key={pageNumber}
                        type="button"
                        onClick={() => setCurrentPage(pageNumber)}
                        className={joinClasses(
                          "inline-flex h-10 w-10 items-center justify-center rounded-lg text-[15px] font-medium transition-all active:scale-95",
                          safeCurrentPage === pageNumber
                            ? "bg-indigo-600 text-white shadow-sm hover:bg-indigo-700"
                            : "border border-slate-200 bg-white text-slate-600 hover:bg-slate-50",
                        )}
                      >
                        {pageNumber}
                      </button>
                    ))}
                    <button
                      type="button"
                      disabled={safeCurrentPage >= totalPages}
                      onClick={() => setCurrentPage((page) => Math.min(totalPages, page + 1))}
                      className="inline-flex h-10 items-center justify-center rounded-lg border border-slate-200 bg-white px-3 text-[15px] font-medium text-slate-600 transition-all hover:bg-slate-50 active:scale-95 disabled:pointer-events-none disabled:opacity-50"
                    >
                      下一页
                    </button>
                  </div>
                </div>
              </>
            )}
          </motion.section>
        </motion.div>
      </main>

      <AnimatePresence>
        {selectedOrder ? (
          <OrderDetailModal
            order={selectedOrder}
            detail={selectedOrderDetail}
            loading={selectedOrderLoading && !selectedOrderDetail}
            copied={copiedOrderNo === selectedOrder.orderNo}
            onCopy={() => void handleCopyOrderNo()}
            onClose={() => setSelectedOrderNo(null)}
            onEnterWorkspace={() => handleEnterWorkspace(selectedOrder.orderNo)}
          />
        ) : null}
      </AnimatePresence>
    </div>
  );
}
