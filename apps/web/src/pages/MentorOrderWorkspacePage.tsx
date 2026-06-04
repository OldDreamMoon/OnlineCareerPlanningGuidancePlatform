import { AnimatePresence, motion, type Variants } from "framer-motion";
import {
  AlertCircle,
  ArrowLeft,
  Bot,
  CalendarDays,
  CheckCircle2,
  ChevronDown,
  Clock,
  CreditCard,
  Download,
  Eye,
  FileText,
  Info,
  MessageSquare,
  RefreshCw,
  Send,
  ShieldAlert,
  ShieldCheck,
  Sparkles,
  Star,
  Target,
  User,
  Wand2,
  XCircle,
  type LucideIcon,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { Link, useParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import WorkspacePageLoadingScreen from "../components/WorkspacePageLoadingScreen";
import WorkspaceRoleTopbar from "../components/workspace/WorkspaceRoleTopbar";
import { ApiClientError, apiRequest, isAbortError } from "../lib/apiClient";
import { resolveConsultSceneLabel } from "../lib/consultSceneLabels";
import { formatDateTime, formatMoneyFen, toTimestamp } from "../lib/formatters";
import { getMentorWorkspaceNavItems } from "../lib/workspaceNav";
import {
  buildWorkspaceSnapshotStorageKey,
  readWorkspaceSnapshot,
  writeWorkspaceSnapshot,
} from "../lib/workspaceSnapshot";

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
  appointmentStartAt: string | null;
  appointmentEndAt: string | null;
  createdAt: string | null;
  paidAt: string | null;
  closedAt: string | null;
  autoCancelAt: string | null;
  mentorReplyDeadlineAt: string | null;
  review: {
    rating: number;
    comment: string | null;
    createdAt: string | null;
  } | null;
  afterSalesRequests: ConsultAfterSalesRequestSummary[];
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

type ConsultOrderAttachmentListResponse = {
  records: ConsultOrderAttachmentItem[];
  currentAttachmentCount: number;
};

type ConsultMessageListResponse = {
  records: ConsultMessageItem[];
};

type ConsultMessageItem = {
  id: number;
  senderUserId: number;
  senderDisplayName: string;
  senderRole: string;
  messageText: string;
  createdAt: string;
};

type AiMetaPayload = {
  taskType: string;
  provider: string;
  model: string;
  latencyMs: number;
};

type AiModerationPayload = {
  sourceType: string;
  riskLevel: string;
  action: string;
  reasonCode: string;
};

type MentorReplyDraftResponse = {
  draftReply: string;
  generationMode: string;
  appliedInstruction: string | null;
  aiMeta: AiMetaPayload | null;
  moderation: AiModerationPayload | null;
};

type RiskChip = {
  key: string;
  label: string;
  className: string;
  icon: LucideIcon;
};

type TimelineStep = {
  id: string;
  title: string;
  time: string;
  active: boolean;
  completed: boolean;
};

type StatusConfig = {
  label: string;
  badgeClass: string;
};

type ReplySnippet = {
  id: string;
  label: string;
  text: string;
};

type MentorOrderWorkspaceSnapshot = {
  detail: ConsultOrderDetailResponse;
  messages: ConsultMessageItem[];
  attachments: ConsultOrderAttachmentItem[];
};

const EXPIRING_SOON_WINDOW_MS = 24 * 60 * 60 * 1000;

const sectionVariants: Variants = {
  hidden: { opacity: 0, y: 20 },
  show: {
    opacity: 1,
    y: 0,
    transition: { type: "spring", stiffness: 250, damping: 24 },
  },
};

const statusConfigMap: Record<string, StatusConfig> = {
  CREATED: { label: "待支付", badgeClass: "bg-slate-100 text-slate-700 border-slate-200" },
  PAYING: { label: "支付中", badgeClass: "bg-cyan-50 text-cyan-700 border-cyan-200" },
  PAID: { label: "待导师回复", badgeClass: "bg-amber-50 text-amber-700 border-amber-200" },
  ANSWERED: { label: "已回复待确认", badgeClass: "bg-indigo-50 text-indigo-700 border-indigo-200" },
  CLOSED: { label: "已完成", badgeClass: "bg-emerald-50 text-emerald-700 border-emerald-200" },
  REFUNDED: { label: "已退款", badgeClass: "bg-slate-100 text-slate-700 border-slate-200" },
  CANCELED: { label: "已取消", badgeClass: "bg-slate-100 text-slate-600 border-slate-200" },
  FAILED: { label: "支付失败", badgeClass: "bg-rose-50 text-rose-700 border-rose-200" },
};

const MATERIAL_TYPE_OPTIONS = [
  { code: "RESUME", label: "简历" },
  { code: "JOB_DESCRIPTION", label: "岗位 JD" },
  { code: "PROJECT_MATERIAL", label: "项目材料" },
  { code: "OFFER_MATERIAL", label: "Offer 材料" },
  { code: "SUPPLEMENTARY", label: "补充材料" },
] as const;

const MATERIAL_TYPE_MAP = new Map<string, string>(MATERIAL_TYPE_OPTIONS.map((item) => [item.code, item.label]));
const ATTACHMENT_LIFECYCLE_LABELS: Record<string, string> = {
  CURRENT: "当前有效",
  ACTIVE: "当前有效",
  SUPERSEDED: "历史版本",
  REPLACED: "历史版本",
  DELETED: "已删除",
};

const SOURCE_PAGE_LABELS: Record<string, string> = {
  MENTOR_MARKETPLACE: "导师广场主列表",
  MENTOR_MARKETPLACE_RECOMMENDATION: "导师广场 AI 推荐区",
  MENTOR_MARKETPLACE_FAVORITES: "导师广场收藏列表",
};

function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function parseTime(value?: string | null) {
  return toTimestamp(value);
}

function trimText(value?: string | null) {
  return value?.trim() ?? "";
}

function getInitial(value?: string | null, fallback = "学") {
  return trimText(value).charAt(0).toUpperCase() || fallback;
}

function truncateText(value: string, maxLength = 72) {
  if (value.length <= maxLength) {
    return value;
  }
  return `${value.slice(0, maxLength).trim()}...`;
}

function getAttachmentTypeLabel(type?: string | null) {
  if (!type) {
    return "未标记材料";
  }
  return MATERIAL_TYPE_MAP.get(type) ?? type;
}

function getAttachmentLifecycleLabel(status?: string | null) {
  if (!status) {
    return "当前有效";
  }
  return ATTACHMENT_LIFECYCLE_LABELS[status] ?? status;
}

function isPaidOrderStatus(status?: string | null) {
  return status === "PAID" || status === "ANSWERED" || status === "CLOSED" || status === "REFUNDED";
}

function formatFileSize(sizeBytes: number | null | undefined) {
  if (!sizeBytes || sizeBytes <= 0) {
    return "大小未知";
  }
  if (sizeBytes < 1024) {
    return `${sizeBytes} B`;
  }
  if (sizeBytes < 1024 * 1024) {
    return `${(sizeBytes / 1024).toFixed(1)} KB`;
  }
  return `${(sizeBytes / (1024 * 1024)).toFixed(1)} MB`;
}

function getSourceLabel(sourcePage?: string | null) {
  if (!sourcePage) {
    return "导师广场";
  }
  return SOURCE_PAGE_LABELS[sourcePage] ?? sourcePage;
}

function isAppointmentOrder(order?: Pick<ConsultOrderDetailResponse, "appointmentStartAt" | "appointmentEndAt"> | null) {
  return Boolean(order?.appointmentStartAt || order?.appointmentEndAt);
}

function getServiceTypeLabel(order?: Pick<ConsultOrderDetailResponse, "appointmentStartAt" | "appointmentEndAt"> | null) {
  return isAppointmentOrder(order) ? "预约咨询" : "图文咨询";
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

function getOrderStatusConfig(status?: string | null) {
  return statusConfigMap[status ?? ""] ?? statusConfigMap.CREATED;
}

function isDeadlineExpired(deadlineAt?: string | null) {
  const timestamp = parseTime(deadlineAt);
  return Number.isFinite(timestamp) && timestamp <= Date.now();
}

function isDeadlineExpiringSoon(deadlineAt?: string | null) {
  const timestamp = parseTime(deadlineAt);
  if (!Number.isFinite(timestamp) || timestamp <= Date.now()) {
    return false;
  }
  return timestamp - Date.now() <= EXPIRING_SOON_WINDOW_MS;
}

function formatRemainingTime(deadlineAt?: string | null) {
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

function formatAppointmentSummary(detail: Pick<ConsultOrderDetailResponse, "appointmentStartAt" | "appointmentEndAt">) {
  if (!detail.appointmentStartAt && !detail.appointmentEndAt) {
    return "图文咨询，无固定预约时段";
  }

  if (detail.appointmentStartAt && detail.appointmentEndAt) {
    return `${formatDateTime(detail.appointmentStartAt)} - ${formatDateTime(detail.appointmentEndAt)}`;
  }

  return formatDateTime(detail.appointmentStartAt ?? detail.appointmentEndAt);
}

function getSceneLabel(detail?: Pick<ConsultOrderDetailResponse, "sceneCode" | "prepSheetSnapshot" | "appointmentStartAt" | "appointmentEndAt"> | null) {
  return resolveConsultSceneLabel({
    sceneCode: detail?.sceneCode,
    prepScene: detail?.prepSheetSnapshot?.scene,
    hasAppointment: Boolean(detail?.appointmentStartAt || detail?.appointmentEndAt),
  });
}

function getReplyBlockedReason(status?: string | null) {
  if (!status) {
    return "正在准备当前回复区...";
  }
  if (status === "CLOSED") {
    return "当前订单已关闭，不能继续发送消息";
  }
  if (status === "REFUNDED") {
    return "当前订单已退款，沟通区仅保留历史记录查看";
  }
  if (status === "CANCELED" || status === "FAILED") {
    return "当前订单已终止，沟通区仅保留历史记录查看";
  }
  if (status === "CREATED" || status === "PAYING") {
    return "订单尚未完成支付，导师暂不能发送正式回复";
  }
  return null;
}

function getLatestAfterSalesRequest(detail?: ConsultOrderDetailResponse | null) {
  return detail?.afterSalesRequests?.[0] ?? null;
}

function getAfterSalesStatusLabel(status?: string | null) {
  if (status === "PENDING") {
    return "待平台处理";
  }
  if (status === "APPROVED") {
    return "已通过";
  }
  if (status === "REJECTED") {
    return "已拒绝";
  }
  return status || "未知";
}

function buildErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiClientError) {
    if (error.status === 404) {
      return "当前订单不存在，或你已无权访问该履约工作区。";
    }
    if (error.status === 403) {
      return "你没有权限访问这笔订单的履约工作区。";
    }
    return error.message || fallback;
  }
  return fallback;
}

function buildDraftNotice(response: MentorReplyDraftResponse) {
  const parts = [
    response.generationMode === "POLISH_EXISTING"
      ? "已根据你当前输入的内容整理出一版更顺畅的表达，你可以继续按自己的语气调整。"
      : "已为你整理出一版可直接修改的回复草稿，确认后再发送给学生即可。",
  ];

  if (trimText(response.appliedInstruction)) {
    parts.push(`本次已结合你的回复偏好：${response.appliedInstruction}。`);
  }

  return parts.join(" ");
}

function buildWorkspaceStatusCopy(detail: ConsultOrderDetailResponse | null) {
  if (!detail) {
    return "正在整理当前订单信息。";
  }

  if (detail.status === "PAID") {
    return detail.mentorReplyDeadlineAt
      ? `订单已支付，请在 ${formatDateTime(detail.mentorReplyDeadlineAt)} 前完成首次正式回复。`
      : "订单已支付，可以在下方消息线程中开始正式回复。";
  }
  if (detail.status === "ANSWERED") {
    return "导师已经给出正式回复，当前阶段以继续沟通和等待学生确认完成为主。";
  }
  if (detail.status === "CLOSED") {
    return "学生已确认问题解决，当前工作区仅保留历史记录查看。";
  }
  if (detail.status === "REFUNDED") {
    return "订单已进入退款流程，当前页面保留历史沟通与订单信息供查看。";
  }
  if (detail.status === "CREATED" || detail.status === "PAYING") {
    return "订单还在支付中，导师暂时不能发送正式回复。";
  }
  return "页面内容已更新，可继续查看订单详情与历史沟通。";
}

function buildReplySnippets(detail: ConsultOrderDetailResponse): ReplySnippet[] {
  const expectedHelp = trimText(detail.questionPayload?.expectedHelp) || trimText(detail.problemSummary) || "先聚焦最核心的问题";
  const materialTypes = detail.attachmentsSummary?.currentMaterialTypes?.length
    ? detail.attachmentsSummary.currentMaterialTypes.map((item) => getAttachmentTypeLabel(item)).join("、")
    : "简历 / JD / 项目材料";
  const expectedOutcomes = detail.expectedOutcomes.length
    ? detail.expectedOutcomes.slice(0, 2)
    : detail.prepSheetSnapshot?.expectedOutcomes?.slice(0, 2) ?? [];

  return [
    {
      id: "priority",
      label: "先确认优先级",
      text: `我先围绕“${truncateText(expectedHelp, 36)}”这一点展开，优先把最影响结果的部分确认清楚。`,
    },
    {
      id: "steps",
      label: "给出执行步骤",
      text: expectedOutcomes.length
        ? `这条建议我会拆成可执行步骤，优先帮助你推进到：${expectedOutcomes.join("、")}。`
        : "我会把建议拆成 2-3 个可直接执行的步骤，方便你照着修改或准备。",
    },
    {
      id: "materials",
      label: "提醒补材料",
      text: `如果你希望我判断得更细，可以继续补充 ${materialTypes}，这样我会给出更贴合材料的建议。`,
    },
  ];
}

function buildTimeline(detail: ConsultOrderDetailResponse, messages: ConsultMessageItem[]): TimelineStep[] {
  const firstMentorMessage = messages.find((item) => item.senderRole === "MENTOR");
  const latestAfterSales = getLatestAfterSalesRequest(detail);
  const finalStepTitle = detail.status === "REFUNDED" ? "订单退款完成" : "学生确认完成";
  const finalStepTime = detail.status === "REFUNDED"
    ? formatDateTime(latestAfterSales?.reviewedAt ?? latestAfterSales?.createdAt)
    : detail.closedAt
      ? formatDateTime(detail.closedAt)
      : "待确认";

  return [
    {
      id: "created",
      title: "学生下单创建",
      time: formatDateTime(detail.createdAt),
      active: false,
      completed: Boolean(detail.createdAt),
    },
    {
      id: "paid",
      title: "支付完成",
      time: detail.paidAt ? formatDateTime(detail.paidAt) : "待支付",
      active: detail.status === "CREATED" || detail.status === "PAYING",
      completed: Boolean(detail.paidAt),
    },
    {
      id: "mentor-replied",
      title: "导师正式回复",
      time: firstMentorMessage ? formatDateTime(firstMentorMessage.createdAt) : detail.status === "REFUNDED" ? "已终止" : "待处理",
      active: detail.status === "PAID",
      completed: Boolean(firstMentorMessage),
    },
    {
      id: "closed",
      title: finalStepTitle,
      time: finalStepTime,
      active: detail.status === "ANSWERED",
      completed: detail.status === "CLOSED" || detail.status === "REFUNDED",
    },
  ];
}

function buildRiskChips(detail: ConsultOrderDetailResponse | null) {
  if (!detail) {
    return [];
  }

  const chips: RiskChip[] = [];
  const latestAfterSales = getLatestAfterSalesRequest(detail);

  if (latestAfterSales?.status === "PENDING") {
    chips.push({
      key: "after-sales-pending",
      label: "存在售后申请",
      className: "bg-rose-100 text-rose-700 border-rose-200/80",
      icon: AlertCircle,
    });
  }

  if (isDeadlineExpired(detail.mentorReplyDeadlineAt)) {
    chips.push({
      key: "deadline-overdue",
      label: "已超回复时限",
      className: "bg-rose-100 text-rose-700 border-rose-200/80",
      icon: Clock,
    });
  } else if (isDeadlineExpiringSoon(detail.mentorReplyDeadlineAt)) {
    chips.push({
      key: "deadline-soon",
      label: `距离回复截止 ${formatRemainingTime(detail.mentorReplyDeadlineAt)}`,
      className: "bg-amber-100 text-amber-700 border-amber-200/80",
      icon: Clock,
    });
  }

  if (detail.status === "ANSWERED") {
    chips.push({
      key: "student-confirmation",
      label: "待学生确认",
      className: "bg-indigo-100 text-indigo-700 border-indigo-200/80",
      icon: CheckCircle2,
    });
  }

  if (detail.status === "REFUNDED") {
    chips.push({
      key: "refunded",
      label: "订单已退款",
      className: "bg-slate-200 text-slate-700 border-slate-300/80",
      icon: XCircle,
    });
  }

  return chips.slice(0, 2);
}

function SectionCard({
  title,
  sectionLabel: _sectionLabel,
  icon: Icon,
  children,
  className,
  hasTopGradient = false,
}: {
  title: string;
  sectionLabel: string;
  icon?: LucideIcon;
  children: ReactNode;
  className?: string;
  hasTopGradient?: boolean;
}) {
  return (
    <motion.section variants={sectionVariants} className={joinClasses("group relative overflow-hidden rounded-[1.8rem] border border-white/70 bg-white/84 shadow-[0_20px_50px_rgba(148,163,184,0.1)] backdrop-blur-xl", className)}>
      {hasTopGradient ? <div className="h-2 w-full bg-gradient-to-r from-indigo-500 via-violet-500 to-amber-400" /> : null}
      <div className={joinClasses("flex h-full flex-col p-6", hasTopGradient && "pt-5")}>
        <div className="mb-5 flex min-h-[3.65rem] items-center justify-between gap-4">
          <div>
            <h2 className="text-[1.42rem] font-bold leading-tight text-slate-950 transition-colors group-hover:text-indigo-900">{title}</h2>
          </div>
          {Icon ? (
            <div className="flex h-11 w-11 items-center justify-center rounded-[1.2rem] border border-indigo-100/60 bg-indigo-50/80 text-indigo-600 shadow-sm transition-transform duration-300 group-hover:scale-110 group-hover:rotate-3">
              <Icon size={20} />
            </div>
          ) : null}
        </div>
        {children}
      </div>
    </motion.section>
  );
}

function RequirementArchiveDetails({
  detail,
  sourceLabel,
}: {
  detail: ConsultOrderDetailResponse;
  sourceLabel: string;
}) {
  const coreQuestions = detail.coreQuestions.length ? detail.coreQuestions : detail.prepSheetSnapshot?.coreQuestions ?? [];
  const expectedOutcomes = detail.expectedOutcomes.length ? detail.expectedOutcomes : detail.prepSheetSnapshot?.expectedOutcomes ?? [];
  const suggestedMaterials = detail.prepSheetSnapshot?.suggestedMaterials ?? [];

  return (
    <div className="space-y-5">
      <div className="space-y-1">
        <div className="text-base font-bold text-indigo-700">订单主问题摘要</div>
        <div className="rounded-xl border border-white bg-white/80 p-4 text-base leading-8 text-slate-700 shadow-sm">
          {trimText(detail.questionText) || "学生尚未在订单中补充问题原文。"}
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        {[
          { title: "本次最想优先解决的问题", value: detail.questionPayload?.primaryConcern || detail.problemSummary, tone: "border-rose-100 bg-rose-50/70" },
          { title: "学生背景情况", value: detail.questionPayload?.background, tone: "border-sky-100 bg-sky-50/70" },
          { title: "已经尝试过什么", value: detail.questionPayload?.attemptedActions, tone: "border-violet-100 bg-violet-50/70" },
          { title: "希望导师提供的帮助", value: detail.questionPayload?.expectedHelp, tone: "border-emerald-100 bg-emerald-50/70" },
          { title: "额外补充说明", value: detail.questionPayload?.additionalNotes, tone: "border-amber-100 bg-amber-50/70" },
        ].map((item) => (
          <div key={item.title} className={joinClasses("rounded-xl border p-4", item.tone, item.title === "额外补充说明" && "sm:col-span-2")}>
            <div className="text-[1.05rem] font-bold text-slate-800">{item.title}</div>
            <div className="mt-3 text-[15px] leading-7 text-slate-600">{trimText(item.value) || "当前没有补充这一部分内容。"}</div>
          </div>
        ))}
      </div>

      <div className="grid gap-4 xl:grid-cols-2">
        <div className="rounded-[1.2rem] border border-slate-200 bg-white/90 p-4">
          <div className="text-base font-bold text-slate-800">核心问题清单</div>
          <div className="mt-4 space-y-3">
            {coreQuestions.length ? (
              coreQuestions.map((question, index) => (
                <div key={`${question}-${index}`} className="rounded-xl border border-slate-100 bg-slate-50 px-4 py-3 text-[15px] leading-7 text-slate-700">
                  <span className="mr-2 inline-flex h-6 w-6 items-center justify-center rounded-full bg-white text-sm font-bold text-slate-500">{index + 1}</span>
                  {question}
                </div>
              ))
            ) : (
              <div className="rounded-xl border border-dashed border-slate-200 bg-slate-50 px-4 py-5 text-[15px] text-slate-500">
                当前没有结构化核心问题，导师先按问题原文和消息线程继续判断。
              </div>
            )}
          </div>
        </div>

        <div className="rounded-[1.2rem] border border-slate-200 bg-white/90 p-4">
          <div className="text-base font-bold text-slate-800">期望结果与准备单</div>
          <div className="mt-4 flex flex-wrap gap-2">
            {expectedOutcomes.length ? (
              expectedOutcomes.map((item) => (
                <span key={item} className="rounded-full border border-indigo-100 bg-indigo-50 px-3 py-2 text-[15px] font-semibold text-indigo-700">
                  {item}
                </span>
              ))
            ) : (
              <span className="text-[15px] text-slate-500">当前没有填写明确的预期结果。</span>
            )}
          </div>
          <div className="mt-4 rounded-xl border border-slate-100 bg-slate-50 p-4 text-base leading-8 text-slate-600">
            {trimText(detail.prepSheetSnapshot?.summaryDraft) || "当前没有额外准备单摘要。"}
          </div>
          <div className="mt-3 flex flex-wrap gap-2">
            {suggestedMaterials.length ? suggestedMaterials.map((item) => (
              <span key={item} className="rounded-full border border-slate-200 bg-white px-3 py-1.5 text-sm font-semibold text-slate-600">
                {item}
              </span>
            )) : (
              <span className="text-sm text-slate-500">当前没有额外建议材料。</span>
            )}
          </div>
        </div>
      </div>

      <div className="rounded-[1.2rem] border border-slate-200 bg-slate-50/80 p-4">
        <div className="text-base font-bold text-slate-800">履约信息</div>
        <div className="mt-4 grid gap-3 sm:grid-cols-2">
          <div className="rounded-xl border border-white bg-white/90 p-3">
            <div className="text-sm font-semibold text-slate-500">订单来源</div>
            <div className="mt-2 text-base font-semibold text-slate-800">{sourceLabel}</div>
          </div>
          <div className="rounded-xl border border-white bg-white/90 p-3">
            <div className="text-sm font-semibold text-slate-500">沟通方式与预约</div>
            <div className="mt-2 text-base font-semibold text-slate-800">{formatAppointmentSummary(detail)}</div>
          </div>
        </div>
        <div className="mt-3 rounded-xl border border-white bg-white/90 p-3 text-base leading-8 text-slate-600">
          {buildWorkspaceStatusCopy(detail)}
        </div>
      </div>
    </div>
  );
}

function WorkspaceFrame({
  displayName,
  lastUpdatedAt,
  refreshing,
  onRefresh,
  children,
}: {
  displayName: string | null | undefined;
  lastUpdatedAt: string | null;
  refreshing: boolean;
  onRefresh: () => void;
  children: ReactNode;
}) {
  return (
    <div className="relative min-h-screen bg-[#eef3ff] text-slate-900">
      <div className="pointer-events-none fixed inset-0 z-0 overflow-hidden">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(79,70,229,0.12),transparent_30%),radial-gradient(circle_at_bottom_right,rgba(20,184,166,0.08),transparent_28%),linear-gradient(180deg,#eff6ff_0%,#f8fafc_58%,#f8fafc_100%)]" />
        <div className="page-top-glow page-top-glow--indigo-soft" />
      </div>

      <WorkspaceRoleTopbar
        sectionLabel="Service Workspace"
        title="导师履约工作区"
        icon={Sparkles}
        navItems={getMentorWorkspaceNavItems("orders")}
        displayName={displayName}
        userSubtitle="履约处理中"
        userFallbackLabel="导师"
        userFallbackInitial="导"
        lastUpdatedAt={lastUpdatedAt}
        onRefresh={onRefresh}
        refreshing={refreshing}
        refreshTitle="刷新导师履约工作区数据"
      />

      <main className="relative z-10 mx-auto w-full max-w-[96rem] px-4 pb-6 pt-8 sm:px-6 lg:px-8 lg:pb-8 lg:pt-10">{children}</main>
    </div>
  );
}

function WorkspaceSkeleton({ displayName }: { displayName: string | null | undefined }) {
  return (
    <WorkspacePageLoadingScreen
      title="正在准备导师履约工作区"
      description="正在加载订单信息、学员材料和沟通记录，请稍候。"
    />
  );
}

export default function MentorOrderWorkspacePage() {
  const { role, displayName, userId } = useAuth();
  const { orderNo = "" } = useParams();
  const [detail, setDetail] = useState<ConsultOrderDetailResponse | null>(null);
  const [messages, setMessages] = useState<ConsultMessageItem[]>([]);
  const [attachments, setAttachments] = useState<ConsultOrderAttachmentItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [replyText, setReplyText] = useState("");
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState<string | null>(null);
  const [showDraftPanel, setShowDraftPanel] = useState(false);
  const [draftNotice, setDraftNotice] = useState<string | null>(null);
  const [draftInstruction, setDraftInstruction] = useState("");
  const [draftAssistLoading, setDraftAssistLoading] = useState(false);
  const [isRequirementExpanded, setIsRequirementExpanded] = useState(false);
  const [lastUpdatedAt, setLastUpdatedAt] = useState<string | null>(null);
  const [attachmentLoadingId, setAttachmentLoadingId] = useState<number | null>(null);
  const [attachmentFeedback, setAttachmentFeedback] = useState<string | null>(null);
  const messagesEndRef = useRef<HTMLDivElement | null>(null);
  const previousMessageCountRef = useRef(0);
  const hasInitializedMessageViewportRef = useRef(false);
  const latestSnapshotRef = useRef<MentorOrderWorkspaceSnapshot | null>(null);
  const snapshotKey = buildWorkspaceSnapshotStorageKey("mentor", "order-workspace", userId ?? "current", orderNo);

  const applyWorkspaceSnapshot = (snapshot: MentorOrderWorkspaceSnapshot, updatedAt: string) => {
    latestSnapshotRef.current = snapshot;
    setDetail(snapshot.detail);
    setMessages(snapshot.messages);
    setAttachments(snapshot.attachments);
    setLastUpdatedAt(updatedAt);
  };

  const persistWorkspaceSnapshot = (
    snapshot: MentorOrderWorkspaceSnapshot,
    updatedAt = new Date().toISOString(),
  ) => {
    latestSnapshotRef.current = snapshot;
    writeWorkspaceSnapshot(snapshotKey, snapshot, updatedAt);
    setLastUpdatedAt(updatedAt);
    return updatedAt;
  };

  const loadWorkspace = useCallback(
    async ({ showSkeleton, signal }: { showSkeleton: boolean; signal?: AbortSignal }) => {
      if (showSkeleton) {
        setLoading(true);
      } else {
        setRefreshing(true);
      }

      setError(null);

      try {
        // 履约工作区并行加载订单、消息和附件，并把完整工作台写入快照。
        const [detailResponse, messageResponse, attachmentsResponse] = await Promise.all([
          apiRequest<ConsultOrderDetailResponse>(`/consult/orders/${orderNo}`, { signal }),
          apiRequest<ConsultMessageListResponse>(`/consult/orders/${orderNo}/messages`, { signal }),
          apiRequest<ConsultOrderAttachmentListResponse>(`/consult/orders/${orderNo}/attachments`, { signal }),
        ]);

        if (signal?.aborted) {
          return;
        }

        const nextSnapshot: MentorOrderWorkspaceSnapshot = {
          detail: detailResponse,
          messages: messageResponse.records,
          attachments: attachmentsResponse.records.length > 0
            ? attachmentsResponse.records
            : detailResponse.attachmentsSummary?.records ?? [],
        };

        const updatedAt = persistWorkspaceSnapshot(nextSnapshot);
        applyWorkspaceSnapshot(nextSnapshot, updatedAt);
      } catch (requestError) {
        if (!isAbortError(requestError) && !signal?.aborted) {
          if (latestSnapshotRef.current) {
            setError(null);
          } else {
            setError(buildErrorMessage(requestError, "订单履约工作区加载失败，请稍后重试。"));
          }
        }
      } finally {
        if (!signal?.aborted) {
          setLoading(false);
          setRefreshing(false);
        }
      }
    },
    [orderNo, snapshotKey],
  );

  useEffect(() => {
    if (!orderNo) {
      setError("缺少订单号，无法进入履约工作区。");
      setLoading(false);
      return;
    }

    const controller = new AbortController();
    const snapshot = readWorkspaceSnapshot<MentorOrderWorkspaceSnapshot>(snapshotKey);

    if (snapshot?.data) {
      // 先用旧 workspace 快照回显，再静默刷新真实订单状态。
      applyWorkspaceSnapshot(snapshot.data, snapshot.updatedAt);
      setError(null);
      setLoading(false);
      void loadWorkspace({ showSkeleton: false, signal: controller.signal });
    } else {
      latestSnapshotRef.current = null;
      void loadWorkspace({ showSkeleton: true, signal: controller.signal });
    }

    return () => controller.abort();
  }, [loadWorkspace, orderNo, snapshotKey]);

  useEffect(() => {
    if (!messagesEndRef.current) {
      return;
    }

    if (!hasInitializedMessageViewportRef.current) {
      hasInitializedMessageViewportRef.current = true;
      previousMessageCountRef.current = messages.length;
      return;
    }

    if (messages.length > previousMessageCountRef.current) {
      // 新消息进入时自动滚到底，初次加载不强制跳动视口。
      messagesEndRef.current.scrollIntoView({ behavior: "smooth" });
    }

    previousMessageCountRef.current = messages.length;
  }, [messages]);

  const mentorName = displayName || detail?.mentorDisplayName || "导师";
  const statusConfig = useMemo(() => getOrderStatusConfig(detail?.status), [detail?.status]);
  const riskChips = useMemo(() => buildRiskChips(detail), [detail]);
  const latestAfterSales = useMemo(() => getLatestAfterSalesRequest(detail), [detail]);
  const currentAttachments = useMemo(() => {
    if (attachments.length > 0) {
      return attachments;
    }
    return detail?.attachmentsSummary?.records ?? [];
  }, [attachments, detail?.attachmentsSummary?.records]);
  const currentMaterialTypes = useMemo(() => {
    if (detail?.attachmentsSummary?.currentMaterialTypes?.length) {
      return detail.attachmentsSummary.currentMaterialTypes;
    }
    return Array.from(new Set(currentAttachments.map((item) => item.attachmentType)));
  }, [currentAttachments, detail?.attachmentsSummary?.currentMaterialTypes]);
  const timeline = useMemo(() => (detail ? buildTimeline(detail, messages) : []), [detail, messages]);
  const replySnippets = useMemo(() => (detail ? buildReplySnippets(detail) : []), [detail]);
  const sceneLabel = useMemo(() => getSceneLabel(detail), [detail]);
  const sourceLabel = useMemo(() => getSourceLabel(detail?.sourcePage), [detail?.sourcePage]);
  const requirementPreview = useMemo(() => {
    const raw = trimText(detail?.questionPayload?.primaryConcern)
      || trimText(detail?.problemSummary)
      || trimText(detail?.questionText);
    if (!raw) {
      return "点击查看学生问题原文、背景信息与准备单。";
    }
    return raw.length > 96 ? `${raw.slice(0, 96)}...` : raw;
  }, [detail?.problemSummary, detail?.questionPayload?.primaryConcern, detail?.questionText]);
  const studentProfile = detail?.studentProfile ?? null;
  const studentSkillTags = studentProfile?.skillTags ?? [];
  const studentPortraitTags = studentProfile?.portraitTags ?? [];
  const studentAcademicMeta = [trimText(studentProfile?.schoolName), trimText(studentProfile?.major)].filter(Boolean).join(" · ");
  const studentStageMeta = [trimText(studentProfile?.grade), trimText(studentProfile?.gpa)].filter(Boolean).join(" · ");
  const hasStudentProfileSummary = Boolean(
    trimText(studentProfile?.jobStatus)
    || trimText(studentProfile?.schoolName)
    || trimText(studentProfile?.major)
    || trimText(studentProfile?.grade)
    || trimText(studentProfile?.gpa)
    || trimText(studentProfile?.targetPosition)
    || trimText(studentProfile?.honors)
    || trimText(studentProfile?.selfIntro)
    || studentSkillTags.length
    || studentPortraitTags.length,
  );
  const canReply = detail?.status === "PAID" || detail?.status === "ANSWERED";
  const replyBlockedReason = getReplyBlockedReason(detail?.status);
  const hasReplyDraft = Boolean(replyText.trim());
  const replyHint = detail
    ? detail.status === "PAID"
      ? "发送首条导师回复后，订单会自动切换为“已回复待确认”。"
      : "继续补充建议时，学生会收到新的履约通知。"
    : "请先等待订单信息加载完成。";

  const handleRefresh = () => {
    void loadWorkspace({ showSkeleton: false });
  };

  const handleDraftAssist = async () => {
    if (!detail || !canReply || draftAssistLoading) {
      return;
    }

    const normalizedInstruction = draftInstruction.trim();
    const normalizedReplyText = replyText.trim();
    setDraftAssistLoading(true);
    setSendError(null);

    try {
      // AI 草稿可基于当前草稿和补充指令生成，也可作为空白回复起点。
      const response = await apiRequest<MentorReplyDraftResponse>(`/consult/orders/${orderNo}/ai-reply-draft`, {
        method: "POST",
        body: JSON.stringify({
          currentDraft: normalizedReplyText || undefined,
          instruction: normalizedInstruction || undefined,
        }),
      });

      setReplyText(response.draftReply);
      setShowDraftPanel(true);
      setDraftNotice(buildDraftNotice(response));
    } catch (requestError) {
      if (!isAbortError(requestError)) {
        setSendError(buildErrorMessage(requestError, "AI 草稿生成失败，请稍后重试。"));
      }
    } finally {
      setDraftAssistLoading(false);
    }
  };

  const handleInsertReplySnippet = (snippet: ReplySnippet) => {
    setReplyText((current) => {
      const normalized = current.trim();
      return normalized ? `${normalized}\n\n${snippet.text}` : snippet.text;
    });
    setShowDraftPanel(true);
    setDraftNotice(`已为你补上一段“${snippet.label}”建议，可以继续修改成最终回复。`);
    setSendError(null);
  };

  const handleOpenAttachment = async (attachment: ConsultOrderAttachmentItem, mode: "preview" | "download") => {
    setAttachmentLoadingId(attachment.attachmentId);
    setAttachmentFeedback(null);
    try {
      // 附件预览使用 object URL，浏览器拦截新窗口时自动退化为下载。
      const response = await apiRequest<Response>(`/consult/orders/${orderNo}/attachments/${attachment.attachmentId}/content`, {
        rawResponse: true,
      });
      if (!(response instanceof Response) || !response.ok) {
        throw new ApiClientError("附件读取失败", response instanceof Response ? response.status : 500);
      }

      const blob = await response.blob();
      const objectUrl = window.URL.createObjectURL(blob);

      if (mode === "download") {
        const anchor = document.createElement("a");
        anchor.href = objectUrl;
        anchor.download = attachment.originalFilename || `attachment-${attachment.attachmentId}`;
        anchor.click();
      } else {
        const opened = window.open(objectUrl, "_blank", "noopener,noreferrer");
        if (!opened) {
          const anchor = document.createElement("a");
          anchor.href = objectUrl;
          anchor.download = attachment.originalFilename || `attachment-${attachment.attachmentId}`;
          anchor.click();
        }
      }

      window.setTimeout(() => window.URL.revokeObjectURL(objectUrl), 60_000);
    } catch (requestError) {
      setAttachmentFeedback(buildErrorMessage(requestError, "附件读取失败，请稍后重试。"));
    } finally {
      setAttachmentLoadingId(null);
    }
  };

  const handleSendReply = async () => {
    const messageText = replyText.trim();
    if (!messageText || !detail || !canReply) {
      return;
    }

    setSending(true);
    setSendError(null);

    try {
      await apiRequest(`/consult/orders/${orderNo}/messages`, {
        method: "POST",
        body: JSON.stringify({ messageText }),
      });

      const optimisticMessage: ConsultMessageItem = {
        id: Date.now(),
        senderUserId: userId ?? detail.mentorUserId,
        senderDisplayName: displayName?.trim() || detail.mentorDisplayName,
        senderRole: "MENTOR",
        messageText,
        createdAt: new Date().toISOString(),
      };
      const nextDetail = detail.status === "PAID"
        ? { ...detail, status: "ANSWERED" }
        : detail;
      const nextMessages = [...messages, optimisticMessage];
      const updatedAt = new Date().toISOString();

      // 发送成功先做本地乐观更新，PAID -> ANSWERED 的真实状态随后通过刷新校准。
      setDetail(nextDetail);
      setMessages(nextMessages);
      setReplyText("");
      setDraftNotice("回复已发送，页面内容已更新。");
      persistWorkspaceSnapshot({
        detail: nextDetail,
        messages: nextMessages,
        attachments: currentAttachments,
      }, updatedAt);
      await loadWorkspace({ showSkeleton: false });
    } catch (requestError) {
      if (!isAbortError(requestError)) {
        setSendError(buildErrorMessage(requestError, "发送失败，请稍后重试。"));
        void loadWorkspace({ showSkeleton: false });
      }
    } finally {
      setSending(false);
    }
  };

  if (loading) {
    return (
      <WorkspaceFrame
        displayName={mentorName}
        lastUpdatedAt={lastUpdatedAt}
        refreshing={refreshing}
        onRefresh={handleRefresh}
      >
        <WorkspaceSkeleton displayName={mentorName} />
      </WorkspaceFrame>
    );
  }

  if (!detail) {
    return (
      <WorkspaceFrame
        displayName={mentorName}
        lastUpdatedAt={lastUpdatedAt}
        refreshing={refreshing}
        onRefresh={handleRefresh}
      >
        <div className="rounded-[2rem] border border-white/70 bg-white/84 p-8 shadow-[0_20px_50px_rgba(148,163,184,0.1)] backdrop-blur-xl lg:p-10">
          <h1 className="text-3xl font-semibold tracking-tight text-slate-950 lg:text-4xl">订单履约工作区暂时无法加载</h1>
          <p className="mt-4 max-w-3xl text-base leading-8 text-slate-600">{error || "当前没有获取到订单数据，请稍后重试。"}</p>
          <div className="mt-8 flex flex-wrap gap-4">
            <button
              type="button"
              onClick={handleRefresh}
              className="inline-flex items-center justify-center rounded-full bg-slate-900 px-6 py-3 text-sm font-semibold text-white transition-colors hover:bg-slate-800"
            >
              <RefreshCw size={16} className="mr-2" />
              重新加载
            </button>
            <Link
              to="/mentor/orders"
              className="inline-flex items-center justify-center rounded-full border border-slate-200 bg-white px-6 py-3 text-sm font-semibold text-slate-700 transition-colors hover:border-slate-300 hover:text-slate-900"
            >
              <ArrowLeft size={16} className="mr-2" />
              返回订单中心
            </Link>
          </div>
        </div>
      </WorkspaceFrame>
    );
  }

  return (
    <WorkspaceFrame
      displayName={mentorName}
      lastUpdatedAt={lastUpdatedAt}
      refreshing={refreshing}
      onRefresh={handleRefresh}
    >
      <motion.div initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }} className="mb-6 flex flex-col gap-4 rounded-[2rem] border border-white/80 bg-white/60 p-4 pl-5 shadow-[0_10px_30px_rgba(15,23,42,0.04)] backdrop-blur-md md:flex-row md:items-center md:justify-between">
        <div className="flex items-center gap-4">
          <Link
            to="/mentor/orders"
            className="flex h-10 w-10 items-center justify-center rounded-full border border-slate-100 bg-white text-slate-500 shadow-sm transition-colors hover:bg-indigo-50 hover:text-indigo-600"
            aria-label="返回订单中心"
          >
            <ArrowLeft size={18} />
          </Link>
          <div className="h-6 w-px bg-slate-200" />
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-[1.55rem] font-bold tracking-tight text-slate-900">{detail.orderNo}</h1>
              <span className={joinClasses("rounded-md border px-2.5 py-1 text-[13px] font-semibold", statusConfig.badgeClass)}>
                {statusConfig.label}
              </span>
              <span className="rounded-md border border-slate-200 bg-slate-50 px-2.5 py-1 text-[13px] font-semibold text-slate-600">
                {getServiceTypeLabel(detail)}
              </span>
              {riskChips.map((chip) => {
                const Icon = chip.icon;
                return (
                  <span key={chip.key} className={joinClasses("flex items-center gap-1 rounded-md border px-2.5 py-1 text-[13px] font-semibold", chip.className)}>
                    <Icon size={12} />
                    {chip.label}
                  </span>
                );
              })}
            </div>
            <div className="mt-1.5 flex flex-wrap items-center gap-3 text-[15px] font-medium text-slate-500">
              <span>{isAppointmentOrder(detail) ? `预约时间：${formatAppointmentSummary(detail)}` : "图文咨询，可直接在线履约"}</span>
              <span className="hidden h-4 w-px bg-slate-200 md:inline-flex" />
              <span>支付方式：{getPaymentModeLabel(detail.paymentMode, detail.status)}</span>
            </div>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <button
            type="button"
            onClick={handleRefresh}
            className="flex h-10 items-center justify-center gap-2 rounded-full border border-slate-200 bg-white px-4 text-[15px] font-medium text-slate-600 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600"
          >
            <RefreshCw size={16} className={joinClasses(refreshing && "animate-spin")} />
            刷新状态
          </button>
        </div>
      </motion.div>

      {error ? (
        <motion.div initial={{ opacity: 0, y: -8 }} animate={{ opacity: 1, y: 0 }} className="mb-6 rounded-[1.5rem] border border-rose-200 bg-rose-50/90 px-5 py-4 text-[15px] leading-7 text-rose-700 shadow-sm">
          {error}
        </motion.div>
      ) : null}

      <motion.div initial="hidden" animate="show" variants={{ show: { transition: { staggerChildren: 0.08 } } }} className="space-y-8">
        <div className="grid items-start gap-8 lg:grid-cols-12 lg:items-stretch">
          <div className="flex min-h-[700px] flex-col gap-6 lg:col-span-8 lg:min-h-[calc(100vh-12rem)]">
            <motion.div variants={sectionVariants} className="relative shrink-0 overflow-hidden rounded-[1.8rem] border border-indigo-100/80 bg-gradient-to-br from-indigo-50/60 to-white/60 shadow-[0_12px_30px_rgba(99,102,241,0.06)] backdrop-blur-xl">
              <div className="pointer-events-none absolute right-0 top-0 h-48 w-48 rounded-full bg-indigo-400/10 blur-[50px]" />

              <button
                type="button"
                onClick={() => setIsRequirementExpanded(true)}
                className="group relative z-10 flex w-full items-start gap-4 p-5 text-left"
              >
                <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-[1.2rem] border border-indigo-200/50 bg-indigo-100/60 text-indigo-600 shadow-sm transition-transform group-hover:scale-105">
                  <Target size={20} />
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center justify-between gap-3">
                    <h2 className="text-base font-bold text-slate-800">学生咨询需求档案</h2>
                    <div className="flex items-center gap-2 text-indigo-600">
                      <span className="text-sm font-semibold">查看详情</span>
                      <ChevronDown size={16} className="-rotate-90 transition-transform duration-300 group-hover:translate-x-0.5" />
                    </div>
                  </div>
                  <div className="mt-2 flex flex-wrap gap-2">
                    <span className="inline-flex items-center rounded-md bg-indigo-100/80 px-2.5 py-1 text-[13px] font-semibold text-indigo-700">
                      场景：{sceneLabel}
                    </span>
                    <span className="inline-flex items-center rounded-md border border-indigo-100 bg-white px-2.5 py-1 text-[13px] font-medium text-slate-600">
                      当前状态：{statusConfig.label}
                    </span>
                    <span className="inline-flex items-center rounded-md border border-indigo-100 bg-white px-2.5 py-1 text-[13px] font-medium text-slate-600">
                      已整理当前问题与材料
                    </span>
                  </div>
                  <div className="mt-3 rounded-[1rem] border border-white/80 bg-white/75 px-4 py-3 text-[15px] leading-6 text-slate-600 shadow-sm">
                    {requirementPreview}
                  </div>
                </div>
              </button>
            </motion.div>

            <AnimatePresence>
              {isRequirementExpanded ? (
                <motion.div
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  exit={{ opacity: 0 }}
                  className="fixed inset-0 z-40 overflow-y-auto px-4 pb-8 pt-24 sm:px-6 lg:px-8"
                >
                  <button
                    type="button"
                    aria-label="关闭需求档案详情"
                    onClick={() => setIsRequirementExpanded(false)}
                    className="absolute inset-0 h-full w-full cursor-default bg-slate-950/16 backdrop-blur-[3px]"
                  />
                  <motion.div
                    initial={{ opacity: 0, y: 24, scale: 0.98 }}
                    animate={{ opacity: 1, y: 0, scale: 1 }}
                    exit={{ opacity: 0, y: 16, scale: 0.98 }}
                    transition={{ type: "spring", stiffness: 260, damping: 28 }}
                    className="relative mx-auto w-full max-w-[72rem] overflow-hidden rounded-[1.85rem] border border-white/80 bg-white/96 shadow-[0_28px_80px_rgba(15,23,42,0.18)]"
                  >
                    <div className="border-b border-indigo-100/70 bg-[linear-gradient(135deg,rgba(238,242,255,0.92),rgba(255,255,255,0.96))] px-5 py-4 sm:px-6">
                      <div className="flex items-start justify-between gap-4">
                        <div>
                          <h3 className="text-[1.6rem] font-bold tracking-tight text-slate-900">学生咨询需求档案</h3>
                        </div>
                        <button
                          type="button"
                          onClick={() => setIsRequirementExpanded(false)}
                          className="inline-flex h-11 w-11 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-500 shadow-sm transition-colors hover:border-slate-300 hover:text-slate-700"
                        >
                          <XCircle size={18} />
                        </button>
                      </div>
                      <div className="mt-4 flex flex-wrap gap-2">
                        <span className="inline-flex items-center rounded-md bg-indigo-100/80 px-3 py-1.5 text-sm font-semibold text-indigo-700">
                          场景：{sceneLabel}
                        </span>
                        <span className="inline-flex items-center rounded-md border border-indigo-100 bg-white px-3 py-1.5 text-sm font-medium text-slate-600">
                          当前状态：{statusConfig.label}
                        </span>
                        <span className="inline-flex items-center rounded-md border border-indigo-100 bg-white px-3 py-1.5 text-sm font-medium text-slate-600">
                          订单来源：{sourceLabel}
                        </span>
                      </div>
                    </div>

                    <div className="max-h-[78vh] overflow-y-auto px-5 py-5 pr-4 sm:px-6 [&::-webkit-scrollbar-thumb]:rounded-full [&::-webkit-scrollbar-thumb]:bg-slate-200/80 [&::-webkit-scrollbar-track]:bg-transparent [&::-webkit-scrollbar]:w-1.5 hover:[&::-webkit-scrollbar-thumb]:bg-slate-300">
                      <RequirementArchiveDetails detail={detail} sourceLabel={sourceLabel} />
                    </div>
                  </motion.div>
                </motion.div>
              ) : null}
            </AnimatePresence>

            <motion.div variants={sectionVariants} className="flex-1 overflow-y-auto rounded-[2rem] border border-white/70 bg-white/60 p-6 pr-4 shadow-[0_20px_50px_rgba(148,163,184,0.1)] backdrop-blur-xl [&::-webkit-scrollbar-thumb]:rounded-full [&::-webkit-scrollbar-thumb]:bg-slate-200/80 [&::-webkit-scrollbar-thumb]:transition-colors [&::-webkit-scrollbar-track]:bg-transparent [&::-webkit-scrollbar]:w-1.5 hover:[&::-webkit-scrollbar-thumb]:bg-slate-300">
              <div className="mb-6 text-center text-sm font-semibold text-slate-500">履约沟通记录</div>

              {messages.length === 0 ? (
                <div className="flex h-full min-h-[18rem] items-center justify-center rounded-[1.5rem] border border-dashed border-slate-200 bg-white/80 text-[15px] text-slate-500">
                  暂时还没有沟通记录，稍后可以刷新查看。
                </div>
              ) : (
                <div className="space-y-6">
                  {messages.map((message) => {
                    const isMentorMessage = message.senderRole === "MENTOR";
                    const isStudentMessage = message.senderRole === "STUDENT";

                    if (!isMentorMessage && !isStudentMessage) {
                      return (
                        <div key={message.id} className="my-3 flex justify-center">
                          <div className="rounded-full border border-slate-200/70 bg-slate-100/90 px-4 py-1.5 text-sm text-slate-500 backdrop-blur-sm">
                            {message.messageText} · {formatDateTime(message.createdAt)}
                          </div>
                        </div>
                      );
                    }

                    const bubbleName = isMentorMessage ? "我（导师）" : trimText(message.senderDisplayName) || detail.studentDisplayName;

                    return (
                      <motion.div key={message.id} initial={{ opacity: 0, y: 10, scale: 0.98 }} animate={{ opacity: 1, y: 0, scale: 1 }} className={joinClasses("flex gap-4", isMentorMessage ? "flex-row-reverse" : "flex-row")}>
                        <div className="shrink-0">
                          {isMentorMessage ? (
                            <div className="flex h-10 w-10 items-center justify-center rounded-[1.2rem] bg-indigo-600 text-white shadow-md">
                              <Star size={18} fill="currentColor" />
                            </div>
                          ) : (
                            <div className="flex h-10 w-10 items-center justify-center rounded-[1.2rem] border border-slate-200 bg-white text-base font-bold text-slate-600 shadow-sm">
                              {getInitial(detail.studentDisplayName)}
                            </div>
                          )}
                        </div>

                        <div className={joinClasses("flex max-w-[82%] flex-col", isMentorMessage ? "items-end" : "items-start")}>
                          <div className="mb-1.5 flex items-baseline gap-2 px-1">
                            <span className="text-sm font-semibold text-slate-700">{bubbleName}</span>
                            <span className="text-xs text-slate-400">{formatDateTime(message.createdAt)}</span>
                          </div>
                          <div className={joinClasses("rounded-[1.5rem] px-5 py-3.5 text-[15px] leading-7 shadow-sm", isMentorMessage ? "rounded-tr-sm bg-indigo-600 text-white" : "rounded-tl-sm border border-slate-100 bg-white text-slate-800")}>
                            {message.messageText}
                          </div>
                        </div>
                      </motion.div>
                    );
                  })}
                  <div ref={messagesEndRef} />
                </div>
              )}
            </motion.div>

            <motion.div variants={sectionVariants} className="group relative shrink-0 overflow-hidden rounded-[2rem] border border-white/80 bg-white/90 p-4 shadow-[0_12px_40px_rgba(15,23,42,0.12)] backdrop-blur-xl transition-all">
              {!canReply && replyBlockedReason ? (
                <div className="absolute inset-0 z-20 flex items-center justify-center rounded-[2rem] bg-slate-50/80 backdrop-blur-[2px]">
                  <div className="flex items-center gap-2 rounded-full border border-rose-200 bg-rose-50 px-5 py-2.5 text-base font-semibold text-rose-700 shadow-sm">
                    <ShieldAlert size={16} className="text-rose-500" />
                    {replyBlockedReason}
                  </div>
                </div>
              ) : null}

              <div className="mb-3 flex items-center justify-between gap-3 px-2">
                <div className="flex flex-wrap items-center gap-2 text-sm font-bold text-slate-700">
                  <MessageSquare size={14} />
                  导师正式回复
                  <span className="rounded-full border border-slate-200 bg-white px-3 py-1 text-[11px] font-medium normal-case tracking-normal text-slate-400 shadow-sm">
                    当前为文字回复
                  </span>
                </div>
                <div className="flex flex-wrap items-center justify-end gap-2">
                  {replySnippets.map((snippet) => (
                    <button
                      key={snippet.id}
                      type="button"
                      onClick={() => handleInsertReplySnippet(snippet)}
                      className="rounded-full border border-slate-200 bg-white px-3 py-1.5 text-sm font-semibold text-slate-600 transition-colors hover:border-indigo-200 hover:text-indigo-600"
                    >
                      {snippet.label}
                    </button>
                  ))}
                </div>
              </div>

              <div className="mb-3 flex flex-col gap-3 px-2 lg:flex-row lg:items-center">
                <label className="flex-1">
                  <span className="mb-1 flex items-center gap-1.5 text-sm font-semibold text-slate-500">
                    <Sparkles size={12} />
                    回复偏好
                  </span>
                  <input
                    type="text"
                    value={draftInstruction}
                    onChange={(event) => setDraftInstruction(event.target.value)}
                    placeholder="可选，例如：更直接一些 / 先聚焦项目表达 / 语气更温和"
                    maxLength={300}
                    disabled={!canReply || draftAssistLoading || sending}
                    className="w-full rounded-full border border-violet-100 bg-violet-50/40 px-4 py-2.5 text-[15px] text-slate-700 outline-none transition-all placeholder:text-slate-400 focus:border-violet-200 focus:bg-white focus:ring-2 focus:ring-violet-500/15 disabled:cursor-not-allowed disabled:opacity-60"
                  />
                </label>
                <button
                  type="button"
                  onClick={() => void handleDraftAssist()}
                  disabled={!canReply || draftAssistLoading || sending}
                  className="flex items-center justify-center gap-2 rounded-full border border-violet-100 bg-gradient-to-r from-violet-50 to-fuchsia-50 px-4 py-2.5 text-[15px] font-semibold text-violet-700 transition-shadow hover:shadow-sm disabled:cursor-not-allowed disabled:opacity-60 lg:min-w-[11rem]"
                >
                  {draftAssistLoading ? <RefreshCw size={14} className="animate-spin" /> : <Wand2 size={14} />}
                  {draftAssistLoading ? "AI 处理中" : hasReplyDraft ? "AI 润色草稿" : "AI 生成草稿"}
                </button>
              </div>

              <AnimatePresence initial={false}>
                {showDraftPanel ? (
                  <motion.div initial={{ opacity: 0, height: 0, marginBottom: 0 }} animate={{ opacity: 1, height: "auto", marginBottom: 12 }} exit={{ opacity: 0, height: 0, marginBottom: 0 }} className="overflow-hidden rounded-xl border border-violet-100 bg-violet-50/50 p-3">
                    <div className="flex items-start gap-3">
                      <div className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-violet-100 text-violet-600">
                        <Bot size={14} />
                      </div>
                      <div className="flex-1">
                        <div className="mb-1 text-sm font-bold text-violet-900">草稿参考</div>
                        <div className="text-[15px] leading-7 text-violet-800">
                          {draftNotice || "已生成一版回复草稿，你可以继续调整后再发送。"}
                        </div>
                        <div className="mt-2 flex gap-2">
                          <button type="button" onClick={() => setShowDraftPanel(false)} className="text-sm font-medium text-violet-600 transition-colors hover:text-violet-800">
                            收起建议
                          </button>
                        </div>
                      </div>
                    </div>
                  </motion.div>
                ) : null}
              </AnimatePresence>

              {sendError ? (
                <div className="mb-3 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-[15px] text-rose-700">
                  {sendError}
                </div>
              ) : null}

              <div className="relative">
                <textarea
                  className="w-full resize-none rounded-[1.2rem] border-0 bg-slate-50/50 p-4 pb-20 text-[15px] leading-7 text-slate-800 placeholder-slate-400 transition-all focus:bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
                  rows={5}
                  placeholder="在此输入导师正式回复，给出清晰、可执行的建议。"
                  value={replyText}
                  onChange={(event) => setReplyText(event.target.value)}
                  maxLength={2000}
                  disabled={!canReply || sending || draftAssistLoading}
                />

                <div className="absolute bottom-3 left-4 text-xs text-slate-400">
                  {replyHint}
                </div>

                <div className="absolute bottom-3 right-3 flex items-center gap-2">
                  <span className="hidden text-xs font-medium text-slate-400 sm:inline-flex">{replyText.length}/2000</span>
                  <button
                    type="button"
                    onClick={handleSendReply}
                    disabled={!replyText.trim() || !canReply || sending || draftAssistLoading}
                    className="flex items-center gap-2 rounded-full bg-slate-900 px-5 py-2.5 text-[15px] font-semibold text-white shadow-md transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {sending ? <RefreshCw size={14} className="animate-spin" /> : <Send size={14} />}
                    {sending ? "发送中" : "发送"}
                  </button>
                </div>
              </div>
            </motion.div>
          </div>

          <div className="flex min-h-[700px] flex-col gap-6 lg:col-span-4 lg:min-h-[calc(100vh-12rem)]">
            <SectionCard title="回复参考" sectionLabel="学生信息" icon={User} hasTopGradient className="shrink-0">
              <div className="mb-5 flex items-center gap-4">
                <div className="flex h-14 w-14 items-center justify-center rounded-full border border-slate-200 bg-slate-100 text-lg font-bold text-slate-600">
                  {getInitial(detail.studentDisplayName)}
                </div>
                <div>
                  <div className="text-xl font-bold text-slate-900">{detail.studentDisplayName}</div>
                  <div className="mt-1 text-sm leading-6 text-slate-500">
                    目标岗位、求职进度与重点信息
                  </div>
                </div>
              </div>

              <div className="grid gap-3 sm:grid-cols-2">
                <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
                  <div className="mb-1 text-sm font-semibold text-slate-500">目标岗位</div>
                  <div className="text-[15px] font-semibold text-slate-800">{trimText(studentProfile?.targetPosition) || "待补充"}</div>
                </div>
                <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
                  <div className="mb-1 text-sm font-semibold text-slate-500">求职状态</div>
                  <div className="text-[15px] font-semibold text-slate-800">{trimText(studentProfile?.jobStatus) || "待补充"}</div>
                </div>
                <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
                  <div className="mb-1 text-sm font-semibold text-slate-500">学校 / 专业</div>
                  <div className="text-[15px] font-semibold text-slate-800">{studentAcademicMeta || "待补充"}</div>
                </div>
                <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
                  <div className="mb-1 text-sm font-semibold text-slate-500">年级 / GPA</div>
                  <div className="text-[15px] font-semibold text-slate-800">{studentStageMeta || "待补充"}</div>
                </div>
              </div>

              {!hasStudentProfileSummary ? (
                <div className="mt-4 rounded-xl border border-dashed border-slate-200 bg-slate-50 px-4 py-4 text-[15px] text-slate-500">
                  学生提供的资料还不多，建议先结合上方需求和沟通内容继续回复。
                </div>
              ) : null}

              <div className="mt-4 space-y-3">
                <div className="rounded-xl border border-slate-100 bg-white p-4">
                  <div className="text-sm font-semibold text-slate-500">技能标签</div>
                  <div className="mt-3 flex flex-wrap gap-2">
                    {studentSkillTags.length ? studentSkillTags.map((item) => (
                      <span key={item} className="rounded-full border border-sky-100 bg-sky-50 px-3 py-1.5 text-sm font-semibold text-sky-700">
                        {item}
                      </span>
                    )) : (
                      <span className="text-[15px] text-slate-500">暂时还没有可参考的技能标签。</span>
                    )}
                  </div>
                </div>
                <div className="rounded-xl border border-slate-100 bg-white p-4">
                  <div className="flex items-center justify-between gap-3">
                    <div className="text-sm font-semibold text-slate-500">近期关注点</div>
                    <div className="text-xs text-slate-400">
                      {studentProfile?.portraitUpdatedAt ? `最近整理于 ${formatDateTime(studentProfile.portraitUpdatedAt)}` : "最近整理时间待更新"}
                    </div>
                  </div>
                  <div className="mt-3 flex flex-wrap gap-2">
                    {studentPortraitTags.length ? studentPortraitTags.map((item, index) => (
                      <span
                        key={`${item.code ?? item.label ?? "portrait"}-${index}`}
                        className="rounded-full border border-violet-100 bg-violet-50 px-3 py-1.5 text-sm font-semibold text-violet-700"
                      >
                        {item.label ?? item.code ?? "关注点"}
                      </span>
                    )) : (
                      <span className="text-[15px] text-slate-500">暂时还没有整理出近期关注点。</span>
                    )}
                  </div>
                </div>
              </div>
            </SectionCard>

            <SectionCard title="资料与附件" sectionLabel="材料附件" icon={FileText} className="flex flex-1 flex-col">
              <div className="mb-4 flex items-center justify-between rounded-[1rem] border border-slate-100 bg-slate-50/80 px-4 py-3">
                <div>
                  <div className="text-sm font-semibold text-slate-500">当前有效材料</div>
                  <div className="mt-1 text-base font-semibold text-slate-800">
                    {detail.attachmentsSummary?.currentAttachmentCount ?? currentAttachments.length} 份
                  </div>
                </div>
                <div className="flex flex-wrap justify-end gap-2">
                  {currentMaterialTypes.length ? currentMaterialTypes.map((item) => (
                    <span key={item} className="rounded-full border border-emerald-100 bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-700">
                      {getAttachmentTypeLabel(item)}
                    </span>
                  )) : (
                    <span className="text-sm text-slate-500">暂未归类</span>
                  )}
                </div>
              </div>

              <div className="flex-1 space-y-4 overflow-y-auto pr-1">
                {attachmentFeedback ? (
                  <div className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-[15px] text-rose-700">
                    {attachmentFeedback}
                  </div>
                ) : null}

                {currentAttachments.length ? (
                  <div className="space-y-3">
                    {currentAttachments.map((attachment) => (
                      <div key={attachment.attachmentId} className="rounded-[1.2rem] border border-slate-200 bg-white p-4 shadow-sm">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-xs font-semibold text-slate-600">
                            {getAttachmentTypeLabel(attachment.attachmentType)}
                          </span>
                          <span className={joinClasses(
                            "rounded-full border px-2.5 py-1 text-xs font-semibold",
                            attachment.lifecycleStatus === "REPLACED"
                              ? "border-amber-100 bg-amber-50 text-amber-700"
                              : attachment.lifecycleStatus === "DELETED"
                                ? "border-rose-100 bg-rose-50 text-rose-700"
                                : "border-emerald-100 bg-emerald-50 text-emerald-700",
                          )}>
                            {getAttachmentLifecycleLabel(attachment.lifecycleStatus)}
                          </span>
                        </div>
                        <div className="mt-2 flex flex-wrap gap-x-3 gap-y-1 text-[13px] text-slate-400">
                          <span>{formatFileSize(attachment.sizeBytes)}</span>
                          <span>上传于 {formatDateTime(attachment.uploadedAt)}</span>
                        </div>
                        <div className="mt-3 text-base font-semibold text-slate-900">{attachment.originalFilename}</div>
                        <div className="mt-1 text-[15px] leading-7 text-slate-600">
                          {trimText(attachment.description) || "当前没有额外材料说明。"}
                        </div>
                        <div className="mt-4 flex flex-wrap gap-2">
                          <button
                            type="button"
                            onClick={() => void handleOpenAttachment(attachment, "preview")}
                            disabled={attachmentLoadingId === attachment.attachmentId}
                            className="inline-flex items-center rounded-full border border-indigo-200 bg-indigo-50 px-3 py-1.5 text-sm font-semibold text-indigo-700 transition-colors hover:bg-indigo-100 disabled:cursor-not-allowed disabled:opacity-60"
                          >
                            {attachmentLoadingId === attachment.attachmentId ? <RefreshCw size={12} className="mr-1.5 animate-spin" /> : <Eye size={12} className="mr-1.5" />}
                            预览
                          </button>
                          <button
                            type="button"
                            onClick={() => void handleOpenAttachment(attachment, "download")}
                            disabled={attachmentLoadingId === attachment.attachmentId}
                            className="inline-flex items-center rounded-full border border-slate-200 bg-white px-3 py-1.5 text-sm font-semibold text-slate-600 transition-colors hover:border-slate-300 hover:text-slate-900 disabled:cursor-not-allowed disabled:opacity-60"
                          >
                            <Download size={12} className="mr-1.5" />
                            下载
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="rounded-[1.2rem] border border-dashed border-slate-200 bg-slate-50 px-4 py-6 text-[15px] text-slate-500">
                    当前还没有可查看的材料。后续如果学生补充简历、JD 或项目材料，这里会继续更新。
                  </div>
                )}
              </div>
            </SectionCard>
          </div>
        </div>

        <div className="grid gap-8 md:grid-cols-2">
          <SectionCard title="订单信息" sectionLabel="订单概览" icon={CalendarDays}>
            <div className="mb-5 flex items-center justify-between rounded-[1rem] bg-slate-900 px-4 py-3.5 text-white shadow-sm">
              <div className="flex items-center gap-2">
                <CreditCard size={16} className="text-slate-400" />
                <span className="text-base font-medium text-slate-300">实际支付金额</span>
              </div>
              <span className="text-[1.7rem] font-black tabular-nums">{formatMoneyFen(detail.amountFen)}</span>
            </div>

              <div className="grid gap-3 rounded-[1.2rem] border border-slate-100 bg-slate-50/80 p-4 md:grid-cols-2">
              <div>
                <div className="text-sm font-semibold text-slate-500">支付方式</div>
                <div className="mt-2 text-[15px] font-semibold text-slate-800">{getPaymentModeLabel(detail.paymentMode, detail.status)}</div>
              </div>
              <div>
                <div className="text-sm font-semibold text-slate-500">订单创建</div>
                <div className="mt-2 text-[15px] font-semibold text-slate-800">{formatDateTime(detail.createdAt)}</div>
              </div>
            </div>

            <div className="relative mt-6 pl-2">
              <div className="absolute bottom-2 left-[15px] top-2 w-[2px] bg-slate-100" />
              <div className="space-y-6">
                {timeline.map((step) => (
                  <div key={step.id} className="relative flex items-start gap-4">
                    <div className={joinClasses("relative z-10 flex h-6 w-6 shrink-0 items-center justify-center rounded-full border-2 bg-white transition-colors", step.completed ? "border-emerald-500 text-emerald-500" : step.active ? "border-indigo-500" : "border-slate-200")}>
                      {step.completed ? (
                        <CheckCircle2 size={12} strokeWidth={4} />
                      ) : step.active ? (
                        <motion.div animate={{ scale: [1, 1.4, 1], opacity: [1, 0.4, 1] }} transition={{ repeat: Infinity, duration: 2 }} className="h-2 w-2 rounded-full bg-indigo-500" />
                      ) : null}
                    </div>
                    <div className="pt-0.5">
                      <div className={joinClasses("text-base font-bold", step.active || step.completed ? "text-slate-800" : "text-slate-400")}>{step.title}</div>
                      <div className={joinClasses("mt-1 text-xs", step.active || step.completed ? "text-slate-500" : "text-slate-400")}>{step.time}</div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </SectionCard>

          <div className="space-y-6">
            <SectionCard title="学生评价" sectionLabel="评价反馈" icon={Star}>
              {detail.review ? (
                <div className="space-y-4">
                  <div className="flex items-center gap-1">
                    {Array.from({ length: 5 }).map((_, index) => (
                      <Star key={index} size={16} className={index < detail.review!.rating ? "fill-amber-400 text-amber-400" : "fill-slate-200 text-slate-200"} />
                    ))}
                    <span className="ml-2 text-sm font-bold text-amber-500">{detail.review.rating}.0</span>
                  </div>
                  <p className="relative rounded-[1rem] border border-slate-100 bg-slate-50 p-4 text-[15px] leading-7 text-slate-700">
                    <span className="absolute -top-2 left-4 bg-slate-50 px-1 font-serif text-2xl leading-none text-slate-300">"</span>
                    {trimText(detail.review.comment) || "学生未留下文字评价。"}
                  </p>
                  <div className="text-xs font-medium text-slate-400">{formatDateTime(detail.review.createdAt)}</div>
                </div>
              ) : (
                <div className="flex flex-col items-center gap-2 rounded-[1rem] border border-slate-100 bg-slate-50 p-5 text-center text-[15px] text-slate-500">
                  <Info size={20} className="text-slate-400" />
                  <div>评价信息会在订单完成后展示，当前阶段先专注履约沟通。</div>
                </div>
              )}
            </SectionCard>

            <SectionCard title="售后记录" sectionLabel="售后进展" icon={ShieldCheck}>
              {latestAfterSales ? (
                <div className="rounded-[1.2rem] border border-rose-200 bg-rose-50 p-4">
                  <div className="mb-3 flex items-center justify-between gap-3">
                    <span className="flex items-center gap-1.5 rounded-md border border-rose-100 bg-white px-2.5 py-1 text-sm font-bold text-rose-700 shadow-sm">
                      <AlertCircle size={14} className="text-rose-500" />
                      {getAfterSalesStatusLabel(latestAfterSales.status)}
                    </span>
                    <span className="text-xs font-medium text-rose-400/80">{formatDateTime(latestAfterSales.createdAt)}</span>
                  </div>
                  <div className="mt-2 text-[15px] font-medium text-rose-900">
                    <span className="text-rose-600/80">发起原因：</span>
                    {latestAfterSales.reason}
                  </div>
                  {latestAfterSales.reviewNote ? (
                    <div className="mt-3 rounded-lg border border-rose-100/60 bg-white/70 p-2.5 text-sm leading-6 text-rose-700">
                      <b>审核备注：</b>{latestAfterSales.reviewNote}
                    </div>
                  ) : null}
                  <div className="mt-3 rounded-lg border border-rose-100/50 bg-white/60 p-2.5 text-sm leading-6 text-rose-600">
                    <b>处理说明：</b>
                    {latestAfterSales.autoTriggered
                      ? "由于订单超时未处理，平台已自动发起售后，后续会继续跟进。"
                      : "当前售后正在由平台处理，你可以在这里查看进展。"}
                  </div>
                </div>
              ) : (
                <div className="flex flex-col items-center gap-2 rounded-[1rem] border border-slate-100 bg-slate-50 p-5 text-center text-[15px] text-slate-500">
                  <ShieldCheck size={20} className="text-slate-400" />
                  <div>当前订单履约正常，暂无售后记录。</div>
                </div>
              )}
            </SectionCard>
          </div>
        </div>
      </motion.div>
    </WorkspaceFrame>
  );
}
