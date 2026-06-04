import { motion } from "framer-motion";
import {
  AlertCircle,
  ArrowLeft,
  ArrowUpRight,
  CheckCircle2,
  Clock,
  CreditCard,
  Loader2,
  MessageSquare,
  RefreshCw,
  Send,
  ShieldAlert,
  ShieldCheck,
  Sparkles,
  Star,
  Target,
  Trash2,
  UploadCloud,
  XCircle,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState, type ChangeEvent } from "react";
import { Link, Navigate, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import MentorIdentityAvatar from "../components/avatar/MentorIdentityAvatar";
import StudentWorkspaceNav from "../components/student/StudentWorkspaceNav";
import WorkspacePageLoadingScreen from "../components/WorkspacePageLoadingScreen";
import { ApiClientError, apiRequest, buildQuery, isAbortError } from "../lib/apiClient";
import { resolveConsultSceneLabel } from "../lib/consultSceneLabels";
import { formatDateTime, formatMoneyFen, toTimestamp } from "../lib/formatters";
import { buildMentorIdentityLine } from "../lib/mentorNames";

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
  sourceStage: string;
  sizeBytes: number;
  lifecycleStatus: string;
  uploadedAt: string;
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

type ConsultOrderAttachmentListResponse = {
  records: ConsultOrderAttachmentItem[];
  currentAttachmentCount: number;
};

type PaymentCreateResponse = {
  orderNo: string;
  status: string;
  paymentMode: string;
  channel: string;
  paymentUrl: string | null;
  instruction: string;
};

type PaymentSuccessResponse = {
  orderNo: string;
  status: string;
  paymentMode: string;
  alreadyProcessed: boolean;
  paidAt: string | null;
};

type PaymentQueryResponse = {
  orderNo: string;
  localStatus: string;
  paymentMode: string;
  providerTradeNo: string | null;
  tradeStatus: string | null;
  gatewayCode: string;
  gatewayMessage: string;
  syncedToPaid: boolean;
  paidAt: string | null;
  queriedAt: string;
};

type PaymentCloseResponse = {
  orderNo: string;
  localStatus: string;
  paymentMode: string;
  providerTradeNo: string | null;
  gatewayCode: string;
  gatewayMessage: string;
  closed: boolean;
  closedAt: string;
};

type MentorDetailResponse = {
  userId: number;
  displayName: string;
  realName: string | null;
  showRealName: boolean;
  companyName: string | null;
  jobTitle: string | null;
  avatarUrl: string;
  expertiseTags: string[];
  serviceScenes: string[];
  bio: string | null;
  priceFen: number;
  avgRating: number | null;
  totalOrders: number;
  available: boolean;
};

type AttachmentTypeOption = {
  code: string;
  label: string;
  slotMode: "single" | "multi";
  tip: string;
};

type TimelineItem = {
  id: string;
  title: string;
  time: string | null;
  active: boolean;
};

const MATERIAL_TYPE_OPTIONS: AttachmentTypeOption[] = [
  { code: "RESUME", label: "简历", slotMode: "single", tip: "单槽位，上传新文件会替换当前简历。"},
  { code: "JOB_DESCRIPTION", label: "岗位 JD", slotMode: "single", tip: "单槽位，适合持续替换最新目标岗位 JD。"},
  { code: "PROJECT_MATERIAL", label: "项目材料", slotMode: "multi", tip: "可并存多份，例如项目文档、作品截图、总结材料。"},
  { code: "OFFER_MATERIAL", label: "Offer 材料", slotMode: "multi", tip: "可并存多份，用于 Offer 对比与决策咨询。"},
  { code: "SUPPLEMENTARY", label: "补充材料", slotMode: "multi", tip: "用于继续追加背景说明、证明材料或上下文补充。"},
];

const MATERIAL_TYPE_MAP = new Map(MATERIAL_TYPE_OPTIONS.map((item) => [item.code, item]));

const STATUS_META: Record<string, { label: string; className: string }> = {
  CREATED: { label: "待支付", className: "border-slate-200 bg-white text-slate-700" },
  PAYING: { label: "支付中", className: "border-cyan-200 bg-cyan-50 text-cyan-700" },
  PAID: { label: "待导师回复", className: "border-amber-200 bg-amber-50 text-amber-700" },
  ANSWERED: { label: "已回复待确认", className: "border-blue-200 bg-blue-50 text-blue-700" },
  CLOSED: { label: "已完成", className: "border-emerald-200 bg-emerald-50 text-emerald-700" },
  REFUNDED: { label: "已退款", className: "border-slate-200 bg-slate-100 text-slate-600" },
  CANCELED: { label: "已取消", className: "border-slate-200 bg-slate-100 text-slate-600" },
  FAILED: { label: "支付失败", className: "border-rose-200 bg-rose-50 text-rose-700" },
};

const SOURCE_PAGE_LABELS: Record<string, string> = {
  MENTOR_MARKETPLACE: "导师广场主列表",
  MENTOR_MARKETPLACE_RECOMMENDATION: "导师广场 AI 推荐区",
  MENTOR_MARKETPLACE_FAVORITES: "导师广场收藏列表",
};

const AFTER_SALES_STATUS_LABELS: Record<string, string> = {
  PENDING: "待审核",
  APPROVED: "已通过",
  REJECTED: "已驳回",
};

function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function parseTime(value?: string | null) {
  return toTimestamp(value);
}

function formatFileSize(sizeBytes: number) {
  if (sizeBytes >= 1024 * 1024) {
    return `${(sizeBytes / (1024 * 1024)).toFixed(1)} MB`;
  }
  return `${Math.max(1, Math.round(sizeBytes / 1024))} KB`;
}

function formatCountdown(targetAt: string | null, now: number) {
  const target = parseTime(targetAt);
  if (Number.isNaN(target)) {
    return null;
  }
  const diff = target - now;
  if (diff <= 0) {
    return "已到期";
  }

  const totalSeconds = Math.floor(diff / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;

  if (hours > 0) {
    return `${hours}小时 ${minutes}分`;
  }
  if (minutes > 0) {
    return `${minutes}分 ${seconds}秒`;
  }
  return `${seconds}秒`;
}

function getAttachmentTypeLabel(type: string) {
  return MATERIAL_TYPE_MAP.get(type)?.label ?? type;
}

function getSourceLabel(sourcePage?: string | null) {
  if (!sourcePage) {
    return "导师广场";
  }
  return SOURCE_PAGE_LABELS[sourcePage] ?? sourcePage;
}

function getStatusMeta(status?: string | null) {
  return STATUS_META[status ?? ""] ?? { label: status || "未知状态", className: "border-slate-200 bg-white text-slate-700" };
}

function getMessageRoleLabel(role: string) {
  if (role === "MENTOR") {
    return "导师";
  }
  if (role === "STUDENT") {
    return "学生";
  }
  return role;
}

function canManageOrderMaterials(status?: string | null) {
  return Boolean(status && !["CLOSED", "CANCELED", "FAILED", "REFUNDED"].includes(status));
}

function canSendOrderMessage(status?: string | null) {
  return canManageOrderMaterials(status);
}

function buildTimeline(detail: ConsultOrderDetailResponse | null): TimelineItem[] {
  if (!detail) {
    return [];
  }
  return [
    { id: "created", title: "订单创建", time: detail.createdAt, active: true },
    { id: "paid", title: "支付完成", time: detail.paidAt, active: ["PAID", "ANSWERED", "CLOSED", "REFUNDED"].includes(detail.status) },
    { id: "answered", title: "导师回复", time: detail.status === "ANSWERED" || detail.status === "CLOSED" ? detail.mentorReplyDeadlineAt : null, active: ["ANSWERED", "CLOSED"].includes(detail.status) },
    { id: "closed", title: "订单结束", time: detail.closedAt, active: ["CLOSED", "REFUNDED", "CANCELED"].includes(detail.status) },
  ];
}

export default function ConsultOrderDetailPage() {
  const { role, displayName } = useAuth();
  const navigate = useNavigate();
  const { orderNo = "" } = useParams();
  const [searchParams] = useSearchParams();
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const [detail, setDetail] = useState<ConsultOrderDetailResponse | null>(null);
  const [messages, setMessages] = useState<ConsultMessageItem[]>([]);
  const [attachments, setAttachments] = useState<ConsultOrderAttachmentItem[]>([]);
  const [mentor, setMentor] = useState<MentorDetailResponse | null>(null);

  const [pageLoading, setPageLoading] = useState(true);
  const [pageError, setPageError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);

  const [paymentLoading, setPaymentLoading] = useState(false);
  const [paymentOpsLoading, setPaymentOpsLoading] = useState<"query" | "close" | null>(null);
  const [paymentFeedback, setPaymentFeedback] = useState<string | null>(null);
  const [lastPaymentAction, setLastPaymentAction] = useState<PaymentCreateResponse | null>(null);

  const [actionLoading, setActionLoading] = useState<"cancel" | "close" | null>(null);
  const [actionFeedback, setActionFeedback] = useState<string | null>(null);

  const [messageDraft, setMessageDraft] = useState("");
  const [messageSending, setMessageSending] = useState(false);
  const [messageFeedback, setMessageFeedback] = useState<string | null>(null);

  const [uploadType, setUploadType] = useState(MATERIAL_TYPE_OPTIONS[0].code);
  const [uploadDescription, setUploadDescription] = useState("");
  const [pendingFiles, setPendingFiles] = useState<File[]>([]);
  const [uploading, setUploading] = useState(false);
  const [uploadFeedback, setUploadFeedback] = useState<string | null>(null);
  const [deletingAttachmentId, setDeletingAttachmentId] = useState<number | null>(null);

  const [reviewRating, setReviewRating] = useState(5);
  const [reviewComment, setReviewComment] = useState("");
  const [reviewSaving, setReviewSaving] = useState(false);
  const [reviewFeedback, setReviewFeedback] = useState<string | null>(null);

  const [afterSalesReason, setAfterSalesReason] = useState("");
  const [afterSalesSubmitting, setAfterSalesSubmitting] = useState(false);
  const [afterSalesFeedback, setAfterSalesFeedback] = useState<string | null>(null);
  const workspaceNav = (
    <StudentWorkspaceNav
      displayName={displayName}
      activeKey="consultOrders"
      sectionLabel="Order Detail"
      title="咨询订单详情"
    />
  );

  const [clockNow, setClockNow] = useState(() => Date.now());

  useEffect(() => {
    const timer = window.setInterval(() => {
      setClockNow(Date.now());
    }, 1000);
    return () => window.clearInterval(timer);
  }, []);

  const loadBundle = useCallback(async (options?: { signal?: AbortSignal; silent?: boolean }) => {
    if (!orderNo) {
      return;
    }

    if (options?.silent) {
      setRefreshing(true);
    } else {
      setPageLoading(true);
    }
    setPageError(null);

    try {
      // 详情、消息和附件并行加载；操作后的 silent refresh 复用同一入口。
      const [detailResponse, messagesResponse, attachmentsResponse] = await Promise.all([
        apiRequest<ConsultOrderDetailResponse>(`/consult/orders/${orderNo}`, { signal: options?.signal }),
        apiRequest<ConsultMessageListResponse>(`/consult/orders/${orderNo}/messages`, { signal: options?.signal }),
        apiRequest<ConsultOrderAttachmentListResponse>(`/consult/orders/${orderNo}/attachments`, { signal: options?.signal }),
      ]);
      setDetail(detailResponse);
      setMessages(messagesResponse.records);
      setAttachments(attachmentsResponse.records);
    } catch (error) {
      if (isAbortError(error)) {
        return;
      }
      const apiError = error as ApiClientError;
      setPageError(apiError.message || "订单详情加载失败");
    } finally {
      setPageLoading(false);
      setRefreshing(false);
    }
  }, [orderNo]);

  useEffect(() => {
    const controller = new AbortController();
    void loadBundle({ signal: controller.signal });
    return () => controller.abort();
  }, [loadBundle]);

  useEffect(() => {
    if (!detail?.mentorUserId) {
      setMentor(null);
      return;
    }
    const controller = new AbortController();
    // 导师资料是展示增强，不影响订单状态机和履约操作。
    void apiRequest<MentorDetailResponse>(`/mentors/${detail.mentorUserId}`, { signal: controller.signal })
      .then((response) => {
        setMentor(response);
      })
      .catch((error) => {
        if (!isAbortError(error)) {
          setMentor(null);
        }
      });
    return () => controller.abort();
  }, [detail?.mentorUserId]);

  const createdFromCreatePage = searchParams.get("entry") === "create";
  const partialAttachmentUpload = searchParams.get("attachmentUpload") === "partial";
  const statusMeta = getStatusMeta(detail?.status);
  const activeAttachmentType = MATERIAL_TYPE_MAP.get(uploadType) ?? MATERIAL_TYPE_OPTIONS[0];
  const currentPaymentMode = detail?.paymentMode ?? lastPaymentAction?.paymentMode ?? null;
  const autoCancelCountdown = formatCountdown(detail?.autoCancelAt ?? null, clockNow);
  const replyCountdown = formatCountdown(detail?.mentorReplyDeadlineAt ?? null, clockNow);
  const timelineItems = useMemo(() => buildTimeline(detail), [detail]);
  const recreateOrderLink = `/consult/create${buildQuery({
    mentorUserId: detail?.mentorUserId,
    scene: detail?.prepSheetSnapshot?.scene,
    source: detail?.sourcePage,
  })}`;
  const currentMaterialCount = detail?.attachmentsSummary?.currentAttachmentCount ?? attachments.length;
  const currentMaterialTypes = detail?.attachmentsSummary?.currentMaterialTypes?.length
    ? detail.attachmentsSummary.currentMaterialTypes
    : Array.from(new Set(attachments.map((item) => item.attachmentType)));
  const hasPendingAfterSales = Boolean(detail?.afterSalesRequests?.some((item) => item.status === "PENDING"));
  const canSubmitAfterSales = Boolean(detail && ["PAID", "ANSWERED", "CLOSED"].includes(detail.status) && !hasPendingAfterSales && detail.status !== "REFUNDED");
  const canCloseOrder = detail?.status === "ANSWERED";
  const canReviewOrder = detail?.status === "CLOSED" && !detail.review;
  const canUpload = canManageOrderMaterials(detail?.status);
  const canSendMessage = canSendOrderMessage(detail?.status);
  const canMockPaySuccess = detail?.status === "PAYING" && (detail.paymentMode === "MOCK" || lastPaymentAction?.paymentMode === "MOCK");
  const canQuerySandboxPayment = Boolean(detail && ["CREATED", "PAYING"].includes(detail.status) && currentPaymentMode === "SANDBOX");
  const canCloseSandboxPayment = Boolean(detail && ["CREATED", "PAYING"].includes(detail.status) && currentPaymentMode === "SANDBOX");

  const handleRefresh = async () => {
    await loadBundle({ silent: true });
  };

  const handlePaymentCreate = async () => {
    if (!detail) {
      return;
    }

    setPaymentLoading(true);
    setPaymentFeedback(null);
    try {
      // 支付入口支持 mock/sandbox，后续查询或关闭仍统一回到当前订单详情刷新。
      const response = await apiRequest<PaymentCreateResponse>(`/pay/orders/${detail.orderNo}/create`, {
        method: "POST",
      });
      setLastPaymentAction(response);
      setPaymentFeedback(response.instruction);
      if (response.paymentUrl) {
        window.open(response.paymentUrl, "_blank", "noopener,noreferrer");
      }
      await loadBundle({ silent: true });
    } catch (error) {
      const apiError = error as ApiClientError;
      setPaymentFeedback(apiError.message || "发起支付失败");
    } finally {
      setPaymentLoading(false);
    }
  };

  const handleCancelOrder = async () => {
    if (!detail) {
      return;
    }

    setActionLoading("cancel");
    setActionFeedback(null);
    try {
      await apiRequest(`/consult/orders/${detail.orderNo}/cancel`, { method: "POST" });
      setActionFeedback("订单已取消，若有预约时段系统也会同步释放。");
      await loadBundle({ silent: true });
    } catch (error) {
      const apiError = error as ApiClientError;
      setActionFeedback(apiError.message || "取消订单失败");
    } finally {
      setActionLoading(null);
    }
  };

  const handleMockPaymentSuccess = async () => {
    if (!detail) {
      return;
    }

    setPaymentLoading(true);
    setPaymentFeedback(null);
    try {
      const response = await apiRequest<PaymentSuccessResponse>(`/pay/mock/orders/${detail.orderNo}/success${buildQuery({
        reason: "STUDENT_ORDER_DETAIL_PAGE",
      })}`, {
        method: "POST",
      });
      setPaymentFeedback(response.alreadyProcessed ? "这笔模拟支付已经处理过，订单状态已同步刷新。" : "模拟支付已成功，订单现在进入已支付状态。");
      await loadBundle({ silent: true });
    } catch (error) {
      const apiError = error as ApiClientError;
      setPaymentFeedback(apiError.message || "模拟支付成功联调失败");
    } finally {
      setPaymentLoading(false);
    }
  };

  const handleQueryPayment = async () => {
    if (!detail) {
      return;
    }

    setPaymentOpsLoading("query");
    setPaymentFeedback(null);
    try {
      const response = await apiRequest<PaymentQueryResponse>(`/consult/orders/${detail.orderNo}/payment/query`, {
        method: "POST",
      });
      const feedbackParts = [
        response.tradeStatus ? `网关状态：${response.tradeStatus}` : "网关已返回最新支付状态。",
        response.providerTradeNo ? `交易号：${response.providerTradeNo}` : null,
        response.syncedToPaid
          ? "本地订单已同步为已支付。"
          : response.localStatus === "PAYING"
            ? "订单仍处于支付中，可稍后继续查询。"
            : `当前订单状态：${response.localStatus}。`,
      ].filter(Boolean);
      setPaymentFeedback(feedbackParts.join(" "));
      await loadBundle({ silent: true });
    } catch (error) {
      const apiError = error as ApiClientError;
      setPaymentFeedback(apiError.message || "查询支付状态失败");
    } finally {
      setPaymentOpsLoading(null);
    }
  };

  const handleClosePayment = async () => {
    if (!detail) {
      return;
    }

    setPaymentOpsLoading("close");
    setPaymentFeedback(null);
    try {
      const response = await apiRequest<PaymentCloseResponse>(`/consult/orders/${detail.orderNo}/payment/close`, {
        method: "POST",
      });
      const feedbackParts = [
        response.closed ? "支付单已关闭，未支付订单会同步取消。" : "支付单关闭结果已返回。",
        response.providerTradeNo ? `交易号：${response.providerTradeNo}` : null,
        `当前订单状态：${response.localStatus}。`,
      ].filter(Boolean);
      setPaymentFeedback(feedbackParts.join(" "));
      await loadBundle({ silent: true });
    } catch (error) {
      const apiError = error as ApiClientError;
      setPaymentFeedback(apiError.message || "关闭支付单失败");
    } finally {
      setPaymentOpsLoading(null);
    }
  };

  const handleCloseOrder = async () => {
    if (!detail) {
      return;
    }

    setActionLoading("close");
    setActionFeedback(null);
    try {
      await apiRequest(`/consult/orders/${detail.orderNo}/close`, { method: "POST" });
      setActionFeedback("订单已确认完成，现在可以补充本次服务评价。");
      await loadBundle({ silent: true });
    } catch (error) {
      const apiError = error as ApiClientError;
      setActionFeedback(apiError.message || "确认完成失败");
    } finally {
      setActionLoading(null);
    }
  };

  const handleSendMessage = async () => {
    if (!detail || !messageDraft.trim()) {
      return;
    }

    setMessageSending(true);
    setMessageFeedback(null);
    try {
      await apiRequest(`/consult/orders/${detail.orderNo}/messages`, {
        method: "POST",
        body: JSON.stringify({ messageText: messageDraft.trim() }),
      });
      setMessageDraft("");
      await loadBundle({ silent: true });
    } catch (error) {
      const apiError = error as ApiClientError;
      setMessageFeedback(apiError.message || "发送消息失败");
    } finally {
      setMessageSending(false);
    }
  };

  const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files ?? []);
    setPendingFiles(files);
    setUploadFeedback(null);
  };

  const handleUploadAttachments = async () => {
    if (!detail || !pendingFiles.length) {
      return;
    }

    setUploading(true);
    setUploadFeedback(null);
    try {
      const formData = new FormData();
      const sourceStage = ["CREATED", "PAYING"].includes(detail.status)
        ? "ORDER_CREATE"
        : activeAttachmentType.slotMode === "single"
          ? "CHAT_REPLACE"
          : "CHAT_APPEND";

      formData.append("manifestJson", JSON.stringify({
        items: pendingFiles.map(() => ({
          attachmentType: activeAttachmentType.code,
          slotCode: activeAttachmentType.code,
          description: uploadDescription.trim(),
          replaceCurrent: activeAttachmentType.slotMode === "single",
          sourceStage,
        })),
      }));
      pendingFiles.forEach((file) => {
        formData.append("files", file);
      });

      await apiRequest(`/consult/orders/${detail.orderNo}/attachments/batch`, {
        method: "POST",
        body: formData,
      });
      setPendingFiles([]);
      setUploadDescription("");
      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
      await loadBundle({ silent: true });
    } catch (error) {
      const apiError = error as ApiClientError;
      setUploadFeedback(apiError.message || "上传材料失败");
    } finally {
      setUploading(false);
    }
  };

  const handleDeleteAttachment = async (attachmentId: number) => {
    if (!detail) {
      return;
    }

    setDeletingAttachmentId(attachmentId);
    setUploadFeedback(null);
    try {
      await apiRequest(`/consult/orders/${detail.orderNo}/attachments/${attachmentId}`, {
        method: "DELETE",
      });
      await loadBundle({ silent: true });
    } catch (error) {
      const apiError = error as ApiClientError;
      setUploadFeedback(apiError.message || "删除材料失败");
    } finally {
      setDeletingAttachmentId(null);
    }
  };

  const handleCreateReview = async () => {
    if (!detail) {
      return;
    }

    setReviewSaving(true);
    setReviewFeedback(null);
    try {
      await apiRequest(`/consult/orders/${detail.orderNo}/review`, {
        method: "POST",
        body: JSON.stringify({
          rating: reviewRating,
          comment: reviewComment.trim(),
        }),
      });
      setReviewComment("");
      setReviewFeedback("评价已提交，导师侧评分会同步刷新。");
      await loadBundle({ silent: true });
    } catch (error) {
      const apiError = error as ApiClientError;
      setReviewFeedback(apiError.message || "提交评价失败");
    } finally {
      setReviewSaving(false);
    }
  };

  const handleCreateAfterSales = async () => {
    if (!detail || !afterSalesReason.trim()) {
      return;
    }

    setAfterSalesSubmitting(true);
    setAfterSalesFeedback(null);
    try {
      await apiRequest(`/consult/orders/${detail.orderNo}/after-sales/requests`, {
        method: "POST",
        body: JSON.stringify({ reason: afterSalesReason.trim() }),
      });
      setAfterSalesReason("");
      setAfterSalesFeedback("售后申请已提交，平台会继续审核并在订单内同步结果。");
      await loadBundle({ silent: true });
    } catch (error) {
      const apiError = error as ApiClientError;
      setAfterSalesFeedback(apiError.message || "提交售后申请失败");
    } finally {
      setAfterSalesSubmitting(false);
    }
  };

  if (!orderNo) {
    return <Navigate to="/student/dashboard" replace />;
  }

  if (pageLoading && !detail) {
    return (
      <WorkspacePageLoadingScreen
        title="正在准备咨询订单页"
        description="正在加载订单信息、沟通记录和交付材料，请稍候。"
      />
    );
  }

  return (
    <div className="relative min-h-screen bg-[#f4f7ff] pb-20 text-slate-900">
      <div className="pointer-events-none fixed inset-0 z-0 bg-[radial-gradient(circle_at_top,rgba(79,70,229,0.18),transparent_34%),radial-gradient(circle_at_bottom_right,rgba(20,184,166,0.12),transparent_28%),linear-gradient(180deg,#f5f7ff_0%,#f8fafc_60%,#f8fafc_100%)]" />
      <div className="page-top-glow page-top-glow--violet-soft" />
      {workspaceNav}

      <div className="relative z-10 mx-auto w-full max-w-[96rem] px-6 py-10 lg:px-8">
        <motion.div initial={{ opacity: 0, y: 18 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.3 }}>
          <header className="rounded-[2.4rem] border border-white/80 bg-white/88 p-6 shadow-[0_30px_90px_rgba(15,23,42,0.10)] backdrop-blur lg:p-8">
            <div className="flex flex-col gap-6 lg:flex-row lg:items-start lg:justify-between">
              <div>
                <div className="flex flex-wrap items-center gap-3">
                  <Link
                    to="/consult/orders"
                    className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700 transition-colors hover:border-slate-300 hover:text-slate-900"
                  >
                    <ArrowLeft size={16} />
                    返回我的订单
                  </Link>
                  <button
                    type="button"
                    onClick={() => navigate("/mentors")}
                    className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700 transition-colors hover:border-slate-300 hover:text-slate-900"
                  >
                    <Target size={16} />
                    返回导师广场
                  </button>
                  <Link
                    to={recreateOrderLink}
                    className="inline-flex items-center gap-2 rounded-full border border-indigo-100 bg-indigo-50 px-4 py-2 text-sm font-semibold text-indigo-600 transition-colors hover:bg-indigo-100"
                  >
                    <Sparkles size={16} />
                    重新创建新订单
                  </Link>
                  <span className={joinClasses("inline-flex items-center gap-2 rounded-full border px-4 py-2 text-sm font-semibold", statusMeta.className)}>
                    <Clock size={15} />
                    {statusMeta.label}
                  </span>
                </div>

                <div className="mt-5 flex flex-wrap items-center gap-3 text-sm font-semibold text-slate-500">
                  <span className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 normal-case tracking-normal text-slate-500">
                    {getSourceLabel(detail?.sourcePage)}
                  </span>
                </div>

                <h1 className="mt-4 text-3xl font-black tracking-tight text-slate-900 lg:text-[2.55rem]">
                  继续推进你的咨询订单
                </h1>
                <p className="mt-3 max-w-3xl text-sm leading-7 text-slate-600 lg:text-[15px]">
                  在这里继续处理支付、消息、材料、评价和售后。
                </p>
              </div>

              <div className="grid gap-3 sm:grid-cols-3 lg:w-[360px]">
                <div className="rounded-[1.6rem] border border-slate-100 bg-slate-50 px-4 py-4">
                  <div className="text-sm font-bold text-slate-500">订单号</div>
                  <div className="mt-2 text-sm font-semibold text-slate-900">{detail?.orderNo}</div>
                </div>
                <div className="rounded-[1.6rem] border border-slate-100 bg-slate-50 px-4 py-4">
                  <div className="text-sm font-bold text-slate-500">金额</div>
                  <div className="mt-2 text-sm font-semibold text-slate-900">{formatMoneyFen(detail?.amountFen)}</div>
                </div>
                <div className="rounded-[1.6rem] border border-slate-100 bg-slate-50 px-4 py-4">
                  <div className="text-sm font-bold text-slate-500">材料数</div>
                  <div className="mt-2 text-sm font-semibold text-slate-900">{currentMaterialCount} 份</div>
                </div>
              </div>
            </div>

            {createdFromCreatePage && partialAttachmentUpload ? (
              <div className={joinClasses(
                "mt-6 rounded-[1.8rem] border px-5 py-4 text-sm",
                partialAttachmentUpload
                  ? "border-amber-200 bg-amber-50 text-amber-800"
                  : "border-emerald-200 bg-emerald-50 text-emerald-800",
              )}>
                <div className="flex items-start gap-3">
                  {partialAttachmentUpload ? <AlertCircle size={18} className="mt-0.5 shrink-0" /> : <CheckCircle2 size={18} className="mt-0.5 shrink-0" />}
                  <div>
                    <div className="font-semibold">订单已创建，部分材料还需要补传</div>
                    <div className="mt-1 leading-6">
                      主订单已创建成功，可以直接在下方继续补传或替换材料。
                    </div>
                  </div>
                </div>
              </div>
            ) : null}

            {pageError ? (
              <div className="mt-6 rounded-[1.8rem] border border-rose-200 bg-rose-50 px-5 py-4 text-sm text-rose-700">
                <div className="flex items-start gap-3">
                  <AlertCircle size={18} className="mt-0.5 shrink-0" />
                  <div>
                    <div className="font-semibold">订单详情刷新失败</div>
                    <div className="mt-1">{pageError}</div>
                  </div>
                </div>
              </div>
            ) : null}
          </header>
        </motion.div>

        <div className="mt-8 grid gap-8 lg:grid-cols-12">
          <div className="space-y-8 lg:col-span-8">
            <motion.section
              initial={{ opacity: 0, y: 18 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.28, delay: 0.04 }}
              className="rounded-[2.2rem] border border-white/80 bg-white/92 p-6 shadow-[0_24px_80px_rgba(15,23,42,0.10)]"
            >
              <div className="flex flex-wrap items-start justify-between gap-4">
                <div>
                  <h2 className="text-2xl font-black text-slate-950">这单到底要解决什么</h2>
                </div>
                <button
                  type="button"
                  onClick={() => void handleRefresh()}
                  disabled={refreshing}
                  className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700 transition-colors hover:border-slate-300 hover:text-slate-900 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  <RefreshCw size={15} className={refreshing ? "animate-spin" : ""} />
                  刷新订单状态
                </button>
              </div>

              <div className="mt-6 rounded-[1.8rem] border border-slate-100 bg-slate-50 px-5 py-5">
                <div className="text-sm font-bold text-slate-500">订单主问题摘要</div>
                <div className="mt-3 text-sm leading-7 text-slate-700">{detail?.questionText?.trim() || "当前订单没有额外摘要。"}</div>
              </div>

              <div className="mt-5 grid gap-4 md:grid-cols-2">
                {[
                  { title: "本次最想解决的问题", value: detail?.questionPayload?.primaryConcern || detail?.problemSummary, tone: "bg-rose-50 border-rose-100" },
                  { title: "我的背景情况", value: detail?.questionPayload?.background, tone: "bg-sky-50 border-sky-100" },
                  { title: "我已经尝试过什么", value: detail?.questionPayload?.attemptedActions, tone: "bg-violet-50 border-violet-100" },
                  { title: "我希望导师给出的帮助", value: detail?.questionPayload?.expectedHelp, tone: "bg-emerald-50 border-emerald-100" },
                  { title: "额外补充说明", value: detail?.questionPayload?.additionalNotes, tone: "bg-amber-50 border-amber-100" },
                ].map((item) => (
                  <div key={item.title} className={joinClasses("rounded-[1.6rem] border px-4 py-4", item.tone)}>
                    <div className="text-sm font-bold text-slate-800">{item.title}</div>
                    <div className="mt-3 text-sm leading-6 text-slate-600">{item.value?.trim() || "当前没有补充这一部分内容。"}</div>
                  </div>
                ))}
              </div>

              <div className="mt-6 grid gap-4 lg:grid-cols-2">
                <div className="rounded-[1.8rem] border border-slate-100 bg-white px-5 py-5">
                  <div className="text-sm font-bold text-slate-800">核心问题</div>
                  <div className="mt-4 space-y-3">
                    {detail?.coreQuestions?.length ? detail.coreQuestions.map((question, index) => (
                      <div key={`${question}-${index}`} className="rounded-2xl border border-slate-100 bg-slate-50 px-4 py-3 text-sm leading-6 text-slate-700">
                        <span className="mr-2 inline-flex h-6 w-6 items-center justify-center rounded-full bg-white text-xs font-bold text-slate-500">{index + 1}</span>
                        {question}
                      </div>
                    )) : <div className="text-sm text-slate-500">当前没有结构化核心问题。</div>}
                  </div>
                </div>

                <div className="rounded-[1.8rem] border border-slate-100 bg-white px-5 py-5">
                  <div className="text-sm font-bold text-slate-800">预期收获</div>
                  <div className="mt-4 flex flex-wrap gap-2">
                    {detail?.expectedOutcomes?.length ? detail.expectedOutcomes.map((item) => (
                      <span key={item} className="rounded-full border border-indigo-100 bg-indigo-50 px-3 py-2 text-sm font-medium text-indigo-600">
                        {item}
                      </span>
                    )) : <div className="text-sm text-slate-500">当前没有填写预期收获。</div>}
                  </div>
                </div>
              </div>
            </motion.section>

            <motion.section
              initial={{ opacity: 0, y: 18 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.28, delay: 0.08 }}
              className="rounded-[2.2rem] border border-white/80 bg-white/92 p-6 shadow-[0_24px_80px_rgba(15,23,42,0.10)]"
            >
              <div className="flex flex-wrap items-center justify-between gap-4">
                <div>
                  <h2 className="text-2xl font-black text-slate-950">准备单与当前有效材料</h2>
                  <p className="mt-2 text-sm leading-7 text-slate-600">
                    在这里继续补充或替换当前有效材料。
                  </p>
                </div>
                <div className="rounded-[1.6rem] border border-slate-100 bg-slate-50 px-4 py-4 text-sm text-slate-600">
                  当前有效材料 <span className="font-black text-slate-900">{currentMaterialCount}</span> 份
                </div>
              </div>

              <div className="mt-6 grid gap-5 lg:grid-cols-2">
                <div className="rounded-[1.8rem] border border-slate-100 bg-slate-50 px-5 py-5">
                  <div className="text-sm font-bold text-slate-800">准备单摘要</div>
                  <div className="mt-3 text-sm leading-7 text-slate-600">
                    {detail?.prepSheetSnapshot?.summaryDraft?.trim() || "当前没有额外准备单摘要。"}
                  </div>
                  <div className="mt-4 flex flex-wrap gap-2">
                    {detail?.prepSheetSnapshot?.suggestedMaterials?.length ? detail.prepSheetSnapshot.suggestedMaterials.map((item) => (
                      <span key={item} className="rounded-full border border-slate-200 bg-white px-3 py-2 text-sm text-slate-600">
                        {item}
                      </span>
                    )) : <span className="text-sm text-slate-500">当前没有建议材料。</span>}
                  </div>
                </div>

                <div className="rounded-[1.8rem] border border-slate-100 bg-slate-50 px-5 py-5">
                  <div className="text-sm font-bold text-slate-800">材料类型摘要</div>
                  <div className="mt-4 flex flex-wrap gap-2">
                    {(currentMaterialTypes.length ? currentMaterialTypes : detail?.selectedMaterialTypes || []).length
                      ? (currentMaterialTypes.length ? currentMaterialTypes : detail?.selectedMaterialTypes || []).map((type) => (
                        <span key={type} className="rounded-full border border-emerald-100 bg-emerald-50 px-3 py-2 text-sm font-medium text-emerald-700">
                          {getAttachmentTypeLabel(type)}
                        </span>
                      ))
                      : <span className="text-sm text-slate-500">当前还没有同步材料类型。</span>}
                  </div>
                </div>
              </div>

              <div className="mt-6 rounded-[1.8rem] border border-slate-100 bg-slate-50 px-5 py-5">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div>
                    <div className="text-sm font-bold text-slate-800">继续上传或替换材料</div>
                    <div className="mt-1 text-sm text-slate-500">{activeAttachmentType.tip}</div>
                  </div>
                  <span className={joinClasses(
                    "rounded-full border px-3 py-1.5 text-xs font-semibold",
                    canUpload ? "border-emerald-200 bg-emerald-50 text-emerald-700" : "border-slate-200 bg-white text-slate-500",
                  )}>
                    {canUpload ? "当前状态允许更新材料" : "当前状态已关闭材料更新"}
                  </span>
                </div>

                <div className="mt-5 grid gap-4 md:grid-cols-[220px_minmax(0,1fr)]">
                  <div className="space-y-3">
                    {MATERIAL_TYPE_OPTIONS.map((option) => (
                      <button
                        key={option.code}
                        type="button"
                        onClick={() => setUploadType(option.code)}
                        className={joinClasses(
                          "w-full rounded-[1.4rem] border px-4 py-3 text-left transition-colors",
                          uploadType === option.code
                            ? "border-indigo-200 bg-white text-slate-900 shadow-[0_16px_30px_rgba(79,70,229,0.08)]"
                            : "border-transparent bg-white/70 text-slate-500 hover:border-slate-200 hover:text-slate-700",
                        )}
                      >
                        <div className="text-sm font-bold">{option.label}</div>
                        <div className="mt-1 text-xs">{option.slotMode === "single" ? "单槽位" : "多槽位"}</div>
                      </button>
                    ))}
                  </div>

                  <div className="rounded-[1.6rem] border border-dashed border-slate-200 bg-white px-5 py-5">
                    <div className="grid gap-4">
                      <input
                        ref={fileInputRef}
                        type="file"
                        multiple={activeAttachmentType.slotMode === "multi"}
                        onChange={handleFileChange}
                        disabled={!canUpload || uploading}
                        className="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600 file:mr-4 file:rounded-full file:border-0 file:bg-slate-900 file:px-4 file:py-2 file:text-sm file:font-semibold file:text-white hover:file:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60"
                      />
                      <textarea
                        rows={3}
                        value={uploadDescription}
                        onChange={(event) => setUploadDescription(event.target.value)}
                        disabled={!canUpload || uploading}
                        className="rounded-[1.4rem] border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-700 outline-none transition-colors focus:border-indigo-200 focus:bg-white disabled:cursor-not-allowed disabled:opacity-60"
                        placeholder="可选：给导师补充说明这份材料的上下文，例如“这是我准备投递大厂前端实习的最新版简历”。"
                      />
                      {pendingFiles.length ? (
                        <div className="rounded-[1.4rem] border border-slate-100 bg-slate-50 px-4 py-4">
                          <div className="text-sm font-semibold text-slate-700">待上传文件</div>
                          <div className="mt-3 space-y-2">
                            {pendingFiles.map((file) => (
                              <div key={`${file.name}-${file.size}`} className="flex items-center justify-between gap-3 rounded-2xl border border-white bg-white px-4 py-3 text-sm text-slate-600">
                                <span className="truncate">{file.name}</span>
                                <span className="shrink-0 text-xs text-slate-400">{formatFileSize(file.size)}</span>
                              </div>
                            ))}
                          </div>
                        </div>
                      ) : null}
                      {uploadFeedback ? <div className="text-sm text-rose-600">{uploadFeedback}</div> : null}
                      <div className="flex flex-wrap gap-3">
                        <button
                          type="button"
                          onClick={() => void handleUploadAttachments()}
                          disabled={!canUpload || uploading || pendingFiles.length === 0}
                          className="inline-flex items-center gap-2 rounded-full bg-slate-900 px-5 py-3 text-sm font-semibold text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          {uploading ? <Loader2 size={16} className="animate-spin" /> : <UploadCloud size={16} />}
                          上传当前材料
                        </button>
                        {pendingFiles.length ? (
                          <button
                            type="button"
                            onClick={() => {
                              setPendingFiles([]);
                              if (fileInputRef.current) {
                                fileInputRef.current.value = "";
                              }
                            }}
                            className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-5 py-3 text-sm font-semibold text-slate-700 transition-colors hover:border-slate-300 hover:text-slate-900"
                          >
                            清空待上传
                          </button>
                        ) : null}
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <div className="mt-6 space-y-3">
                {attachments.length ? attachments.map((attachment) => (
                  <div key={attachment.attachmentId} className="flex flex-col gap-3 rounded-[1.6rem] border border-slate-100 bg-white px-5 py-4 shadow-[0_12px_30px_rgba(15,23,42,0.04)] sm:flex-row sm:items-start sm:justify-between">
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-semibold text-slate-600">
                          {getAttachmentTypeLabel(attachment.attachmentType)}
                        </span>
                        <span className="text-xs text-slate-400">{formatFileSize(attachment.sizeBytes)}</span>
                        <span className="text-xs text-slate-400">上传于 {formatDateTime(attachment.uploadedAt)}</span>
                      </div>
                      <div className="mt-3 text-sm font-semibold text-slate-800">{attachment.originalFilename}</div>
                      <div className="mt-1 text-sm leading-6 text-slate-500">{attachment.description?.trim() || "当前没有额外材料说明。"}</div>
                    </div>
                    <div className="flex items-center gap-3">
                      <button
                        type="button"
                        onClick={() => void handleDeleteAttachment(attachment.attachmentId)}
                        disabled={!canUpload || deletingAttachmentId === attachment.attachmentId}
                        className="inline-flex items-center gap-2 rounded-full border border-rose-200 bg-rose-50 px-4 py-2 text-sm font-semibold text-rose-600 transition-colors hover:bg-rose-100 disabled:cursor-not-allowed disabled:opacity-60"
                      >
                        {deletingAttachmentId === attachment.attachmentId ? <Loader2 size={15} className="animate-spin" /> : <Trash2 size={15} />}
                        删除
                      </button>
                    </div>
                  </div>
                )) : (
                  <div className="rounded-[1.6rem] border border-dashed border-slate-200 bg-slate-50 px-5 py-8 text-sm text-slate-500">
                    当前还没有同步材料。你可以继续上传简历、岗位 JD、项目材料或其他补充文件。
                  </div>
                )}
              </div>
            </motion.section>

            <motion.section
              initial={{ opacity: 0, y: 18 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.28, delay: 0.12 }}
              className="rounded-[2.2rem] border border-white/80 bg-white/92 p-6 shadow-[0_24px_80px_rgba(15,23,42,0.10)]"
            >
              <div className="inline-flex items-center gap-2 rounded-full border border-amber-100 bg-amber-50 px-3 py-1 text-xs font-semibold text-amber-700">
                <MessageSquare size={14} />
                订单消息与状态动作
              </div>

              <div className="mt-4 flex flex-wrap items-start justify-between gap-4">
                <div>
                  <h2 className="text-2xl font-black text-slate-900">消息、评价与售后入口</h2>
                  <p className="mt-2 text-sm leading-7 text-slate-600">
                    消息沟通、确认完成、评价和售后都在这里处理。
                  </p>
                </div>
                <div className="flex flex-wrap gap-3">
                  {detail?.status === "CREATED" || detail?.status === "PAYING" ? (
                    <button
                      type="button"
                      onClick={() => void handlePaymentCreate()}
                      disabled={paymentLoading}
                      className="inline-flex items-center gap-2 rounded-full bg-indigo-600 px-5 py-3 text-sm font-semibold text-white transition-colors hover:bg-indigo-500 disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      {paymentLoading ? <Loader2 size={16} className="animate-spin" /> : <CreditCard size={16} />}
                      {detail.status === "PAYING" ? "重新拉起支付" : "发起支付"}
                    </button>
                  ) : null}
                  {canMockPaySuccess ? (
                    <button
                      type="button"
                      onClick={() => void handleMockPaymentSuccess()}
                      disabled={paymentLoading}
                      className="inline-flex items-center gap-2 rounded-full border border-cyan-200 bg-cyan-50 px-5 py-3 text-sm font-semibold text-cyan-700 transition-colors hover:bg-cyan-100 disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      {paymentLoading ? <Loader2 size={16} className="animate-spin" /> : <CheckCircle2 size={16} />}
                      模拟支付成功
                    </button>
                  ) : null}
                  {canQuerySandboxPayment ? (
                    <button
                      type="button"
                      onClick={() => void handleQueryPayment()}
                      disabled={paymentOpsLoading === "query"}
                      className="inline-flex items-center gap-2 rounded-full border border-cyan-200 bg-cyan-50 px-5 py-3 text-sm font-semibold text-cyan-700 transition-colors hover:bg-cyan-100 disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      {paymentOpsLoading === "query" ? <Loader2 size={16} className="animate-spin" /> : <RefreshCw size={16} />}
                      查询支付状态
                    </button>
                  ) : null}
                  {canCloseSandboxPayment ? (
                    <button
                      type="button"
                      onClick={() => void handleClosePayment()}
                      disabled={paymentOpsLoading === "close"}
                      className="inline-flex items-center gap-2 rounded-full border border-amber-200 bg-amber-50 px-5 py-3 text-sm font-semibold text-amber-700 transition-colors hover:bg-amber-100 disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      {paymentOpsLoading === "close" ? <Loader2 size={16} className="animate-spin" /> : <ShieldAlert size={16} />}
                      关闭沙箱支付单
                    </button>
                  ) : null}
                  {canCloseOrder ? (
                    <button
                      type="button"
                      onClick={() => void handleCloseOrder()}
                      disabled={actionLoading === "close"}
                      className="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-50 px-5 py-3 text-sm font-semibold text-emerald-700 transition-colors hover:bg-emerald-100 disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      {actionLoading === "close" ? <Loader2 size={16} className="animate-spin" /> : <CheckCircle2 size={16} />}
                      确认服务完成
                    </button>
                  ) : null}
                  {detail?.status === "CREATED" || detail?.status === "PAYING" ? (
                    <button
                      type="button"
                      onClick={() => void handleCancelOrder()}
                      disabled={actionLoading === "cancel"}
                      className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-5 py-3 text-sm font-semibold text-slate-700 transition-colors hover:border-slate-300 hover:text-slate-900 disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      {actionLoading === "cancel" ? <Loader2 size={16} className="animate-spin" /> : <XCircle size={16} />}
                      取消未支付订单
                    </button>
                  ) : null}
                </div>
              </div>

              {paymentFeedback ? (
                <div className="mt-5 rounded-[1.6rem] border border-indigo-100 bg-indigo-50 px-4 py-4 text-sm text-indigo-700">
                  <div className="font-semibold">支付提示</div>
                  <div className="mt-1">{paymentFeedback}</div>
                  {lastPaymentAction?.paymentUrl ? (
                    <a
                      href={lastPaymentAction.paymentUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="mt-3 inline-flex items-center gap-2 font-semibold text-indigo-700 underline underline-offset-4"
                    >
                      打开支付页
                      <ArrowUpRight size={14} />
                    </a>
                  ) : null}
                </div>
              ) : null}
              {actionFeedback ? <div className="mt-5 text-sm text-emerald-600">{actionFeedback}</div> : null}

              <div className="mt-6 grid gap-6 lg:grid-cols-2">
                <div className="rounded-[1.8rem] border border-slate-100 bg-slate-50 px-5 py-5">
                  <div className="text-sm font-bold text-slate-800">消息线程</div>
                  <div className="mt-4 space-y-3">
                    {messages.length ? messages.map((message) => {
                      const isMentorMessage = message.senderRole === "MENTOR";
                      return (
                        <div
                          key={message.id}
                          className={joinClasses(
                            "rounded-[1.4rem] border px-4 py-4",
                            isMentorMessage ? "border-indigo-100 bg-white" : "border-slate-200 bg-slate-100",
                          )}
                        >
                          <div className="flex flex-wrap items-center justify-between gap-3">
                            <div className="text-sm font-semibold text-slate-800">
                              {message.senderDisplayName}
                              <span className="ml-2 text-xs font-medium text-slate-400">{getMessageRoleLabel(message.senderRole)}</span>
                            </div>
                            <div className="text-xs text-slate-400">{formatDateTime(message.createdAt)}</div>
                          </div>
                          <div className="mt-3 text-sm leading-7 text-slate-600">{message.messageText}</div>
                        </div>
                      );
                    }) : <div className="text-sm text-slate-500">当前还没有消息记录。</div>}
                  </div>

                  <div className="mt-5 rounded-[1.6rem] border border-white bg-white px-4 py-4">
                    <textarea
                      rows={4}
                      value={messageDraft}
                      onChange={(event) => setMessageDraft(event.target.value)}
                      disabled={!canSendMessage || messageSending}
                      placeholder={canSendMessage ? "继续补充背景、追问或对导师回复做进一步确认。" : "当前订单状态已关闭消息输入。"}
                      className="w-full rounded-[1.2rem] border border-slate-200 bg-slate-50 px-4 py-3 text-sm leading-6 text-slate-700 outline-none transition-colors focus:border-indigo-200 focus:bg-white disabled:cursor-not-allowed disabled:opacity-60"
                    />
                    {messageFeedback ? <div className="mt-3 text-sm text-rose-600">{messageFeedback}</div> : null}
                    <div className="mt-4 flex justify-end">
                      <button
                        type="button"
                        onClick={() => void handleSendMessage()}
                        disabled={!canSendMessage || messageSending || !messageDraft.trim()}
                        className="inline-flex items-center gap-2 rounded-full bg-slate-900 px-5 py-3 text-sm font-semibold text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60"
                      >
                        {messageSending ? <Loader2 size={16} className="animate-spin" /> : <Send size={16} />}
                        发送补充消息
                      </button>
                    </div>
                  </div>
                </div>

                <div className="space-y-5">
                  <div className="rounded-[1.8rem] border border-slate-100 bg-slate-50 px-5 py-5">
                    <div className="flex items-center gap-2 text-sm font-bold text-slate-800">
                      <Star size={16} className="text-amber-500" />
                      评价与完成确认
                    </div>
                    {detail?.review ? (
                      <div className="mt-4 rounded-[1.4rem] border border-white bg-white px-4 py-4">
                        <div className="text-sm font-semibold text-slate-800">{detail.review.rating} / 5 分</div>
                        <div className="mt-2 text-sm leading-6 text-slate-600">{detail.review.comment?.trim() || "本次评价没有附加文字说明。"}</div>
                        <div className="mt-2 text-xs text-slate-400">提交于 {formatDateTime(detail.review.createdAt)}</div>
                      </div>
                    ) : canReviewOrder ? (
                      <div className="mt-4 rounded-[1.4rem] border border-white bg-white px-4 py-4">
                        <div className="flex flex-wrap gap-2">
                          {[1, 2, 3, 4, 5].map((value) => (
                            <button
                              key={value}
                              type="button"
                              onClick={() => setReviewRating(value)}
                              className={joinClasses(
                                "rounded-full border px-3 py-2 text-sm font-semibold transition-colors",
                                reviewRating === value
                                  ? "border-amber-200 bg-amber-50 text-amber-700"
                                  : "border-slate-200 bg-white text-slate-500 hover:border-slate-300 hover:text-slate-700",
                              )}
                            >
                              {value} 分
                            </button>
                          ))}
                        </div>
                        <textarea
                          rows={4}
                          value={reviewComment}
                          onChange={(event) => setReviewComment(event.target.value)}
                          placeholder="可选：告诉导师哪些建议最有帮助，便于后续继续优化服务。"
                          className="mt-4 w-full rounded-[1.2rem] border border-slate-200 bg-slate-50 px-4 py-3 text-sm leading-6 text-slate-700 outline-none transition-colors focus:border-indigo-200 focus:bg-white"
                        />
                        {reviewFeedback ? <div className="mt-3 text-sm text-emerald-600">{reviewFeedback}</div> : null}
                        <div className="mt-4 flex justify-end">
                          <button
                            type="button"
                            onClick={() => void handleCreateReview()}
                            disabled={reviewSaving}
                            className="inline-flex items-center gap-2 rounded-full bg-amber-500 px-5 py-3 text-sm font-semibold text-white transition-colors hover:bg-amber-400 disabled:cursor-not-allowed disabled:opacity-60"
                          >
                            {reviewSaving ? <Loader2 size={16} className="animate-spin" /> : <Star size={16} />}
                            提交评价
                          </button>
                        </div>
                      </div>
                    ) : (
                      <div className="mt-4 text-sm leading-6 text-slate-500">
                        当前还不能提交评价。只有订单确认完成后，才会开放评价入口。
                      </div>
                    )}
                  </div>

                  <div className="rounded-[1.8rem] border border-slate-100 bg-slate-50 px-5 py-5">
                    <div className="flex items-center gap-2 text-sm font-bold text-slate-800">
                      <ShieldAlert size={16} className="text-rose-500" />
                      售后申请与历史
                    </div>
                    <div className="mt-2 text-sm leading-6 text-slate-500">
                      订单处于 `PAID / ANSWERED / CLOSED` 时可以提交退款售后申请；导师超时未答时，系统也会自动兜底发起处理。
                    </div>

                    {canSubmitAfterSales ? (
                      <div className="mt-4 rounded-[1.4rem] border border-white bg-white px-4 py-4">
                        <textarea
                          rows={4}
                          value={afterSalesReason}
                          onChange={(event) => setAfterSalesReason(event.target.value)}
                          placeholder="请说明申请售后的原因，例如服务不匹配、沟通后仍未解决问题等。"
                          className="w-full rounded-[1.2rem] border border-slate-200 bg-slate-50 px-4 py-3 text-sm leading-6 text-slate-700 outline-none transition-colors focus:border-indigo-200 focus:bg-white"
                        />
                        {afterSalesFeedback ? <div className="mt-3 text-sm text-emerald-600">{afterSalesFeedback}</div> : null}
                        <div className="mt-4 flex justify-end">
                          <button
                            type="button"
                            onClick={() => void handleCreateAfterSales()}
                            disabled={afterSalesSubmitting || !afterSalesReason.trim()}
                            className="inline-flex items-center gap-2 rounded-full border border-rose-200 bg-rose-50 px-5 py-3 text-sm font-semibold text-rose-600 transition-colors hover:bg-rose-100 disabled:cursor-not-allowed disabled:opacity-60"
                          >
                            {afterSalesSubmitting ? <Loader2 size={16} className="animate-spin" /> : <ShieldAlert size={16} />}
                            提交售后申请
                          </button>
                        </div>
                      </div>
                    ) : null}

                    <div className="mt-4 space-y-3">
                      {detail?.afterSalesRequests?.length ? detail.afterSalesRequests.map((item) => (
                        <div key={item.id} className="rounded-[1.4rem] border border-white bg-white px-4 py-4">
                          <div className="flex flex-wrap items-center justify-between gap-3">
                            <div className="text-sm font-semibold text-slate-800">售后申请 #{item.id}</div>
                            <span className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-semibold text-slate-600">
                              {AFTER_SALES_STATUS_LABELS[item.status] ?? item.status}
                            </span>
                          </div>
                          <div className="mt-3 text-sm leading-6 text-slate-600">{item.reason}</div>
                          {item.reviewNote ? <div className="mt-3 text-sm text-slate-500">审核备注：{item.reviewNote}</div> : null}
                          <div className="mt-3 text-xs text-slate-400">
                            提交于 {formatDateTime(item.createdAt)}{item.reviewedAt ? ` · 审核于 ${formatDateTime(item.reviewedAt)}` : ""}{item.autoTriggered ? " · 系统自动触发" : ""}
                          </div>
                        </div>
                      )) : (
                        <div className="rounded-[1.4rem] border border-dashed border-slate-200 bg-white px-4 py-6 text-sm text-slate-500">
                          当前还没有售后申请记录。
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            </motion.section>
          </div>

          <div className="space-y-8 lg:col-span-4">
            <motion.aside
              initial={{ opacity: 0, y: 18 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.28, delay: 0.06 }}
              className="sticky top-6 space-y-8"
            >
              <section className="overflow-hidden rounded-[2.2rem] border border-white/80 bg-white/92 shadow-[0_24px_80px_rgba(15,23,42,0.10)]">
                <div className="h-2 w-full bg-gradient-to-r from-indigo-500 via-cyan-500 to-amber-400" />
                <div className="p-6">
                  <div className="text-sm font-bold text-slate-800">导师与订单摘要</div>
                  <div className="mt-5 flex items-center gap-4">
                    {mentor?.avatarUrl ? (
                      <MentorIdentityAvatar
                        userId={mentor.userId}
                        displayName={mentor.displayName}
                        avatarUrl={mentor.avatarUrl}
                        alt={mentor.displayName}
                        className="h-14 w-14 bg-slate-100"
                      />
                    ) : (
                      <div className="flex h-14 w-14 items-center justify-center rounded-full bg-slate-100 text-lg font-black text-slate-500">
                        {detail?.mentorDisplayName?.slice(0, 1) || "导"}
                      </div>
                    )}
                    <div className="min-w-0">
                      <div className="truncate text-lg font-black text-slate-900">{mentor?.displayName || detail?.mentorDisplayName}</div>
                      <div className="mt-1 truncate text-sm text-slate-500">
                        {buildMentorIdentityLine(mentor ?? {
                          displayName: detail?.mentorDisplayName ?? null,
                        }, detail?.mentorDisplayName)}
                      </div>
                    </div>
                  </div>

                  {mentor?.expertiseTags?.length ? (
                    <div className="mt-5 flex flex-wrap gap-2">
                      {mentor.expertiseTags.slice(0, 6).map((tag) => (
                        <span key={tag} className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-semibold text-slate-600">
                          {tag}
                        </span>
                      ))}
                    </div>
                  ) : null}

                  <div className="mt-6 space-y-4">
                    <div className="rounded-[1.4rem] border border-slate-100 bg-slate-50 px-4 py-4">
                      <div className="text-xs font-semibold text-slate-400">咨询场景</div>
                      <div className="mt-2 text-sm font-semibold text-slate-900">
                        {resolveConsultSceneLabel({
                          sceneCode: detail?.sceneCode,
                          prepScene: detail?.prepSheetSnapshot?.scene,
                          hasAppointment: Boolean(detail?.appointmentStartAt || detail?.appointmentEndAt),
                        })}
                      </div>
                    </div>
                    <div className="rounded-[1.4rem] border border-slate-100 bg-slate-50 px-4 py-4">
                      <div className="text-xs font-semibold text-slate-400">下单时间</div>
                      <div className="mt-2 text-sm font-semibold text-slate-900">{formatDateTime(detail?.createdAt)}</div>
                    </div>
                    <div className="rounded-[1.4rem] border border-slate-100 bg-slate-50 px-4 py-4">
                      <div className="text-xs font-semibold text-slate-400">支付模式</div>
                      <div className="mt-2 text-sm font-semibold text-slate-900">{detail?.paymentMode || lastPaymentAction?.paymentMode || "待创建支付单"}</div>
                    </div>
                    <div className="rounded-[1.4rem] border border-slate-100 bg-slate-50 px-4 py-4">
                      <div className="text-xs font-semibold text-slate-400">预约时间</div>
                      <div className="mt-2 text-sm font-semibold text-slate-900">
                        {detail?.appointmentStartAt
                          ? `${formatDateTime(detail.appointmentStartAt)} - ${formatDateTime(detail.appointmentEndAt)}`
                          : "本单没有预约固定时段"}
                      </div>
                    </div>
                  </div>
                </div>
              </section>

              <section className="rounded-[2.2rem] border border-white/80 bg-white/92 p-6 shadow-[0_24px_80px_rgba(15,23,42,0.10)]">
                <div className="text-sm font-bold text-slate-800">倒计时与关键节点</div>
                <div className="mt-5 space-y-4">
                  <div className="rounded-[1.4rem] border border-slate-100 bg-slate-50 px-4 py-4">
                    <div className="flex items-center gap-2 text-sm font-semibold text-slate-800">
                      <Clock size={16} className="text-slate-500" />
                      支付倒计时
                    </div>
                    <div className="mt-2 text-lg font-black text-slate-900">{autoCancelCountdown || "当前无需支付倒计时"}</div>
                    <div className="mt-1 text-sm text-slate-500">自动取消时间：{formatDateTime(detail?.autoCancelAt)}</div>
                  </div>

                  <div className="rounded-[1.4rem] border border-slate-100 bg-slate-50 px-4 py-4">
                    <div className="flex items-center gap-2 text-sm font-semibold text-slate-800">
                      <ShieldCheck size={16} className="text-emerald-500" />
                      导师回复 SLA
                    </div>
                    <div className="mt-2 text-lg font-black text-slate-900">{replyCountdown || "当前无需回复倒计时"}</div>
                    <div className="mt-1 text-sm text-slate-500">截止时间：{formatDateTime(detail?.mentorReplyDeadlineAt)}</div>
                  </div>
                </div>

                <div className="mt-6 space-y-4">
                  {timelineItems.map((item) => (
                    <div key={item.id} className="flex items-start gap-3">
                      <div className={joinClasses(
                        "mt-1 h-3.5 w-3.5 rounded-full border-2",
                        item.active ? "border-emerald-400 bg-emerald-400" : "border-slate-300 bg-white",
                      )}
                      />
                      <div>
                        <div className="text-sm font-semibold text-slate-800">{item.title}</div>
                        <div className="text-xs text-slate-400">{item.time ? formatDateTime(item.time) : "等待进入该阶段"}</div>
                      </div>
                    </div>
                  ))}
                </div>
              </section>

              <section className="rounded-[2.2rem] border border-white/80 bg-white/92 p-6 shadow-[0_24px_80px_rgba(15,23,42,0.10)]">
                <div className="text-sm font-bold text-slate-800">服务提醒</div>
                <div className="mt-5 space-y-3">
                  <div className="rounded-[1.4rem] border border-indigo-100 bg-indigo-50 px-4 py-4 text-sm leading-6 text-indigo-700">
                    <div className="font-semibold">材料更新规则</div>
                    <div className="mt-1">简历与岗位 JD 是单槽位材料，上传新版本会替换当前有效副本；项目材料、Offer 材料与补充材料可并存。</div>
                  </div>
                  <div className="rounded-[1.4rem] border border-amber-100 bg-amber-50 px-4 py-4 text-sm leading-6 text-amber-700">
                    <div className="font-semibold">支付与回复节点</div>
                    <div className="mt-1">订单先创建、再支付；支付完成后会开始计算导师回复时限，超时系统会自动走售后退款兜底。</div>
                  </div>
                  <div className="rounded-[1.4rem] border border-emerald-100 bg-emerald-50 px-4 py-4 text-sm leading-6 text-emerald-700">
                    <div className="font-semibold">评价与售后</div>
                    <div className="mt-1">确认服务完成后即可提交评价；若服务不匹配，可在订单内直接发起售后申请并持续回看审核结果。</div>
                  </div>
                </div>
              </section>
            </motion.aside>
          </div>
        </div>
      </div>
    </div>
  );
}
