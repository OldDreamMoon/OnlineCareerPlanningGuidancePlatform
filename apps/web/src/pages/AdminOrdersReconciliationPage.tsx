import { lazy, Suspense, useCallback, useEffect, useMemo, useState } from "react";
import {
  Alert,
  Button,
  Empty,
  Form,
  Input,
  Pagination,
  Select,
  Table,
  Tag,
  Typography,
  message,
  type TableColumnsType,
} from "antd";
import {
  AlertCircle,
  ClipboardCheck,
  RefreshCcw,
  ShieldCheck,
  Wallet,
  WalletCards,
} from "lucide-react";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  AdminMetricCard,
  AdminFilterBar,
  AdminPageFrame,
  AdminPageHeader,
  AdminSurfaceCard,
} from "../components/admin/AdminOpsPrimitives";
import { useLatestRequest } from "../hooks/useLatestRequest";
import { buildAdminStaleCacheKey, useAdminStaleCache } from "../hooks/useAdminStaleCache";
import { ApiClientError, apiRequest, buildQuery, isAbortError } from "../lib/apiClient";
import {
  afterSalesRequestTypeLabelMap,
  afterSalesStatusLabelMap,
  getLabel,
  orderStatusLabelMap,
  paymentChannelLabelMap,
  paymentModeLabelMap,
  paymentStatusLabelMap,
  reconciliationActionLabelMap,
  reconciliationIssueTagLabelMap,
  reconciliationStatusLabelMap,
} from "../lib/adminLabels";
import { formatCount, formatDateTime, formatMoneyFen, formatRelativeTime, toTimestamp } from "../lib/formatters";

const { Paragraph, Text } = Typography;
const { Search: SearchInput } = Input;
const AdminOrdersReconciliationOverlays = lazy(() => import("../components/admin/overlays/AdminOrdersReconciliationOverlays"));

const orderTabs = ["after-sales", "reconciliation"] as const;
type OrderTabKey = typeof orderTabs[number];

const tablePageSizeOptions = [10, 20, 50].map((size) => ({
  label: `${size} 条/页`,
  value: size,
}));

type ReviewFormValues = {
  decision: "approve" | "reject";
  reviewNote: string;
};

type RefundFormValues = {
  reason: string;
};

type ManualActionFormValues = {
  action: string;
  note: string;
};

type TimeValue = string | number | null;

type AfterSalesRequestItem = {
  requestId: number;
  orderNo: string;
  orderStatus: string;
  amountFen: number;
  studentUserId: number;
  studentDisplayName: string;
  mentorUserId: number;
  mentorDisplayName: string;
  requestType: string;
  status: string;
  reason: string;
  reviewNote: string | null;
  reviewerUserId: number | null;
  autoTriggered: boolean;
  createdAt: string;
  reviewedAt: string | null;
};

type AfterSalesListResponse = {
  records: AfterSalesRequestItem[];
  total: number;
  page: number;
  size: number;
};

type AfterSalesListCachePayload = {
  records: AfterSalesRequestItem[];
  total: number;
};

type OrderDetailResponse = {
  orderNo: string;
  studentUserId: number;
  studentDisplayName: string;
  mentorUserId: number;
  mentorDisplayName: string;
  amountFen: number;
  status: string;
  questionText: string | null;
  paymentMode: string | null;
  appointmentStartAt: string | null;
  appointmentEndAt: string | null;
  createdAt: string | null;
  paidAt: string | null;
  closedAt: string | null;
  autoCancelAt: string | null;
  mentorReplyDeadlineAt: string | null;
  payment: {
    channel: string | null;
    mode: string | null;
    status: string | null;
    providerTradeNo: string | null;
    idempotencyKey: string | null;
    recordedAt: string | null;
  } | null;
  review: {
    rating: number;
    comment: string | null;
    createdAt: string | null;
  } | null;
  refund: {
    reason: string | null;
    operatorUserId: number;
    previousStatus: string | null;
    slotReleased: boolean;
    reviewRemoved: boolean;
    processedAt: string | null;
  } | null;
  afterSalesRequests: Array<{
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
  }>;
};

type AfterSalesReviewResponse = {
  requestId: number;
  orderNo: string;
  status: string;
  reviewNote: string | null;
  reviewedAt: string | null;
  refund: OrderRefundResponse | null;
};

type OrderRefundResponse = {
  orderNo: string;
  status: string;
  processedAt: string | null;
  slotReleased: boolean;
  reviewRemoved: boolean;
  reason: string;
  externalRefundTriggered: boolean;
  externalProviderTradeNo: string | null;
  externalRefundRequestNo: string | null;
  externalRefundStatus: string | null;
};

type PaymentQueryResponse = {
  orderNo: string;
  localStatus: string;
  paymentMode: string;
  providerTradeNo: string | null;
  tradeStatus: string | null;
  gatewayCode: string | null;
  gatewayMessage: string | null;
  syncedToPaid: boolean;
  paidAt: string | null;
  queriedAt: string | null;
};

type PaymentCloseResponse = {
  orderNo: string;
  localStatus: string;
  paymentMode: string;
  providerTradeNo: string | null;
  gatewayCode: string | null;
  gatewayMessage: string | null;
  closed: boolean;
  closedAt: string | null;
};

type RefundQueryResponse = {
  orderNo: string;
  paymentMode: string;
  providerTradeNo: string | null;
  refundRequestNo: string | null;
  gatewayCode: string | null;
  gatewayMessage: string | null;
  refundStatus: string | null;
  refundAmountFen: number;
  queriedAt: string | null;
};

type ReconciliationItem = {
  orderNo: string;
  studentUserId: number;
  studentDisplayName: string;
  mentorUserId: number;
  mentorDisplayName: string;
  amountFen: number;
  orderStatus: string;
  paymentMode: string;
  paymentChannel: string | null;
  latestPaymentStatus: string | null;
  providerTradeNo: string | null;
  reconciliationStatus: string;
  issueTags: string[];
  latestManualAction: string | null;
  latestManualNote: string | null;
  latestManualHandledAt: string | null;
  createdAt: string | null;
  paidAt: string | null;
  closedAt: string | null;
  latestPaymentCreatedAt: string | null;
};

type ReconciliationListResponse = {
  records: ReconciliationItem[];
  total: number;
  page: number;
  size: number;
};

type ReconciliationListCachePayload = {
  records: ReconciliationItem[];
  total: number;
};

type ReconciliationDetailResponse = {
  orderNo: string;
  studentUserId: number;
  studentDisplayName: string;
  mentorUserId: number;
  mentorDisplayName: string;
  amountFen: number;
  orderStatus: string;
  questionText: string | null;
  paymentMode: string | null;
  paymentChannel: string | null;
  latestPaymentStatus: string | null;
  providerTradeNo: string | null;
  appointmentStartAt: string | null;
  appointmentEndAt: string | null;
  createdAt: string | null;
  paidAt: string | null;
  closedAt: string | null;
  reconciliationStatus: string;
  issueTags: string[];
  recommendedActions: string[];
  latestManualHandling: {
    action: string;
    note: string;
    operatorUserId: number;
    processedAt: string | null;
  } | null;
  paymentRecords: Array<{
    channel: string | null;
    mode: string | null;
    status: string | null;
    providerTradeNo: string | null;
    amountFen: number;
    idempotencyKey: string | null;
    rawCallback: string | null;
    createdAt: string | null;
  }>;
};

type PaymentHandleResponse = {
  orderNo: string;
  action: string;
  orderStatus: string;
  reconciliationStatus: string;
  handledAt: string | null;
  note: string;
};

const afterSalesStatusOptions = [
  { label: "全部售后状态", value: "" },
  { label: "待审核", value: "PENDING" },
  { label: "已通过", value: "APPROVED" },
  { label: "已拒绝", value: "REJECTED" },
];

const reconciliationStatusOptions = [
  { label: "全部对账状态", value: "" },
  { label: "异常待处理", value: "REVIEW_REQUIRED" },
  { label: "人工处理中", value: "REVIEWED_PENDING" },
  { label: "已人工解决", value: "MANUALLY_RESOLVED" },
  { label: "已匹配", value: "MATCHED" },
  { label: "处理中", value: "PENDING" },
];

const orderStatusOptions = [
  { label: "全部订单状态", value: "" },
  { label: "待支付", value: "PAYING" },
  { label: "已支付", value: "PAID" },
  { label: "已关闭", value: "CLOSED" },
  { label: "已退款", value: "REFUNDED" },
];

function resolveOrderTab(tab: string | null): OrderTabKey {
  return tab === "reconciliation" ? "reconciliation" : "after-sales";
}

function stringifyPayload(payload: unknown) {
  try {
    return JSON.stringify(payload, null, 2);
  } catch {
    return String(payload);
  }
}

function getAfterSalesStatusTag(status: string) {
  if (status === "PENDING") {
    return <Tag color="warning">待审核</Tag>;
  }
  if (status === "APPROVED") {
    return <Tag color="success">已通过</Tag>;
  }
  if (status === "REJECTED") {
    return <Tag>已拒绝</Tag>;
  }
  return <Tag>{getLabel(status, afterSalesStatusLabelMap, status)}</Tag>;
}

function getReconciliationStatusTag(status: string) {
  if (status === "REVIEW_REQUIRED") {
    return <Tag color="error">异常待处理</Tag>;
  }
  if (status === "REVIEWED_PENDING") {
    return <Tag color="warning">人工处理中</Tag>;
  }
  if (status === "MANUALLY_RESOLVED") {
    return <Tag color="processing">人工已解决</Tag>;
  }
  if (status === "MATCHED") {
    return <Tag color="success">已匹配</Tag>;
  }
  return <Tag>{getLabel(status, reconciliationStatusLabelMap, status)}</Tag>;
}

function buildTimelineItems(
  entries: Array<{
    sortAt: number;
    color: string;
    title: string;
    note: string;
    meta: string;
  }>,
) {
  return entries
    .filter((item) => Number.isFinite(item.sortAt))
    .sort((left, right) => left.sortAt - right.sortAt)
    .map((item) => ({
      color: item.color,
      children: (
        <div className="pb-3">
          <Text strong className="block text-sm text-slate-900">
            {item.title}
          </Text>
          <Paragraph className="!mb-1 !mt-1 !text-sm !leading-6 !text-slate-500">{item.note}</Paragraph>
          <Text type="secondary" className="text-xs">
            {item.meta}
          </Text>
        </div>
      ),
    }));
}

export default function AdminOrdersReconciliationPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const createAfterSalesRequest = useLatestRequest();
  const createReconciliationRequest = useLatestRequest();

  const activeTab = resolveOrderTab(searchParams.get("tab"));

  const [afterSalesKeywordInput, setAfterSalesKeywordInput] = useState("");
  const [afterSalesKeyword, setAfterSalesKeyword] = useState("");
  const [afterSalesStatus, setAfterSalesStatus] = useState(searchParams.get("afterSalesStatus") ?? "PENDING");
  const [afterSalesPage, setAfterSalesPage] = useState(1);
  const [afterSalesPageSize, setAfterSalesPageSize] = useState(10);
  const afterSalesCache = useAdminStaleCache<AfterSalesListCachePayload>(
    buildAdminStaleCacheKey("admin-orders:after-sales", {
      afterSalesPage,
      afterSalesPageSize,
      afterSalesKeyword,
      afterSalesStatus,
    }),
  );
  const [afterSalesRecords, setAfterSalesRecords] = useState<AfterSalesRequestItem[]>(() => afterSalesCache.cached?.records ?? []);
  const [afterSalesTotal, setAfterSalesTotal] = useState(() => afterSalesCache.cached?.total ?? 0);
  const [afterSalesLoading, setAfterSalesLoading] = useState(() => !afterSalesCache.hasCache);
  const [afterSalesError, setAfterSalesError] = useState<string | null>(null);

  const [reconciliationKeywordInput, setReconciliationKeywordInput] = useState("");
  const [reconciliationKeyword, setReconciliationKeyword] = useState("");
  const [reconciliationOrderStatus, setReconciliationOrderStatus] = useState("");
  const [reconciliationStatus, setReconciliationStatus] = useState(searchParams.get("reconciliationStatus") ?? "REVIEW_REQUIRED");
  const [reconciliationPage, setReconciliationPage] = useState(1);
  const [reconciliationPageSize, setReconciliationPageSize] = useState(10);
  const reconciliationCache = useAdminStaleCache<ReconciliationListCachePayload>(
    buildAdminStaleCacheKey("admin-orders:reconciliation", {
      reconciliationPage,
      reconciliationPageSize,
      reconciliationKeyword,
      reconciliationOrderStatus,
      reconciliationStatus,
    }),
  );
  const [reconciliationRecords, setReconciliationRecords] = useState<ReconciliationItem[]>(() => reconciliationCache.cached?.records ?? []);
  const [reconciliationTotal, setReconciliationTotal] = useState(() => reconciliationCache.cached?.total ?? 0);
  const [reconciliationLoading, setReconciliationLoading] = useState(() => !reconciliationCache.hasCache);
  const [reconciliationError, setReconciliationError] = useState<string | null>(null);

  const [currentAfterSalesRecord, setCurrentAfterSalesRecord] = useState<AfterSalesRequestItem | null>(null);
  const [afterSalesReviewOpen, setAfterSalesReviewOpen] = useState(false);
  const [afterSalesReviewSaving, setAfterSalesReviewSaving] = useState(false);
  const [afterSalesReviewForm] = Form.useForm<ReviewFormValues>();
  const [afterSalesPriorityPage, setAfterSalesPriorityPage] = useState(1);

  const [orderDetailOpen, setOrderDetailOpen] = useState(false);
  const [orderDetailLoading, setOrderDetailLoading] = useState(false);
  const [orderDetail, setOrderDetail] = useState<OrderDetailResponse | null>(null);

  const [refundOpen, setRefundOpen] = useState(false);
  const [refundSaving, setRefundSaving] = useState(false);
  const [refundForm] = Form.useForm<RefundFormValues>();

  const [gatewayResultOpen, setGatewayResultOpen] = useState(false);
  const [gatewayResultTitle, setGatewayResultTitle] = useState("");
  const [gatewayResultContent, setGatewayResultContent] = useState("");

  const [reconciliationDetailOpen, setReconciliationDetailOpen] = useState(false);
  const [reconciliationDetailLoading, setReconciliationDetailLoading] = useState(false);
  const [reconciliationDetail, setReconciliationDetail] = useState<ReconciliationDetailResponse | null>(null);

  const [handleActionOpen, setHandleActionOpen] = useState(false);
  const [handleActionSaving, setHandleActionSaving] = useState(false);
  const [currentRecommendedAction, setCurrentRecommendedAction] = useState<string | null>(null);
  const [handleActionForm] = Form.useForm<ManualActionFormValues>();

  const syncQuery = useCallback((updates: Record<string, string | null>) => {
    // 售后和对账共用页面，tab 与重点筛选放进 query 便于后台深链定位。
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      Object.entries(updates).forEach(([key, value]) => {
        if (!value) {
          next.delete(key);
        } else {
          next.set(key, value);
        }
      });
      return next;
    }, { replace: true });
  }, [setSearchParams]);

  useEffect(() => {
    const nextAfterSalesStatus = searchParams.get("afterSalesStatus") ?? "PENDING";
    const nextReconciliationStatus = searchParams.get("reconciliationStatus") ?? "REVIEW_REQUIRED";

    if (nextAfterSalesStatus !== afterSalesStatus) {
      setAfterSalesStatus(nextAfterSalesStatus);
      setAfterSalesPage(1);
    }
    if (nextReconciliationStatus !== reconciliationStatus) {
      setReconciliationStatus(nextReconciliationStatus);
      setReconciliationPage(1);
    }
  }, [afterSalesStatus, reconciliationStatus, searchParams]);

  const loadAfterSales = useCallback(async (showLoading = true) => {
    const request = createAfterSalesRequest();
    if (showLoading) {
      setAfterSalesLoading(true);
    }
    setAfterSalesError(null);

    try {
      // 售后工单列表由咨询售后服务提供，自动触发的超时退款会带 autoTriggered 标识。
      const response = await apiRequest<AfterSalesListResponse>(
        `/admin/consult/after-sales/requests${buildQuery({
          page: afterSalesPage,
          size: afterSalesPageSize,
          keyword: afterSalesKeyword || undefined,
          status: afterSalesStatus || undefined,
        })}`,
        { signal: request.signal },
      );
      if (!request.isCurrent()) {
        return;
      }
      setAfterSalesRecords(response.records);
      setAfterSalesTotal(response.total);
      afterSalesCache.write({
        records: response.records,
        total: response.total,
      });
    } catch (error) {
      if (isAbortError(error) || !request.isCurrent()) {
        return;
      }
      const apiError = error as ApiClientError;
      setAfterSalesError(apiError.message || "加载售后工单失败");
    } finally {
      if (request.isCurrent()) {
        setAfterSalesLoading(false);
      }
    }
  }, [afterSalesCache, afterSalesKeyword, afterSalesPage, afterSalesPageSize, afterSalesStatus, createAfterSalesRequest]);

  const loadReconciliation = useCallback(async (showLoading = true) => {
    const request = createReconciliationRequest();
    if (showLoading) {
      setReconciliationLoading(true);
    }
    setReconciliationError(null);

    try {
      // 对账列表聚焦本地订单和支付流水不一致的 REVIEW_REQUIRED 记录。
      const response = await apiRequest<ReconciliationListResponse>(
        `/admin/payments/reconciliation${buildQuery({
          page: reconciliationPage,
          size: reconciliationPageSize,
          keyword: reconciliationKeyword || undefined,
          orderStatus: reconciliationOrderStatus || undefined,
          reconciliationStatus: reconciliationStatus || undefined,
        })}`,
        { signal: request.signal },
      );
      if (!request.isCurrent()) {
        return;
      }
      setReconciliationRecords(response.records);
      setReconciliationTotal(response.total);
      reconciliationCache.write({
        records: response.records,
        total: response.total,
      });
    } catch (error) {
      if (isAbortError(error) || !request.isCurrent()) {
        return;
      }
      const apiError = error as ApiClientError;
      setReconciliationError(apiError.message || "加载异常对账失败");
    } finally {
      if (request.isCurrent()) {
        setReconciliationLoading(false);
      }
    }
  }, [createReconciliationRequest, reconciliationCache, reconciliationKeyword, reconciliationOrderStatus, reconciliationPage, reconciliationPageSize, reconciliationStatus]);

  useEffect(() => {
    if (!afterSalesCache.cached) {
      return;
    }
    setAfterSalesRecords(afterSalesCache.cached.records);
    setAfterSalesTotal(afterSalesCache.cached.total);
  }, [afterSalesCache.cached]);

  useEffect(() => {
    if (!reconciliationCache.cached) {
      return;
    }
    setReconciliationRecords(reconciliationCache.cached.records);
    setReconciliationTotal(reconciliationCache.cached.total);
  }, [reconciliationCache.cached]);

  useEffect(() => {
    void loadAfterSales(!afterSalesCache.hasCache);
  }, [afterSalesCache.hasCache, loadAfterSales]);

  useEffect(() => {
    void loadReconciliation(!reconciliationCache.hasCache);
  }, [loadReconciliation, reconciliationCache.hasCache]);

  const loadOrderDetail = useCallback(async (
    orderNo: string,
    options?: { clearCurrent?: boolean },
  ) => {
    setOrderDetailLoading(true);
    if (options?.clearCurrent) {
      setOrderDetail(null);
    }

    try {
      // 售后审核前拉订单详情，弹层才能同时展示支付、材料、退款和履约时间线。
      const response = await apiRequest<OrderDetailResponse>(`/admin/consult/orders/${orderNo}`);
      setOrderDetail(response);
      return response;
    } catch (error) {
      const apiError = error as ApiClientError;
      message.error(apiError.message || "加载订单详情失败");
      return null;
    } finally {
      setOrderDetailLoading(false);
    }
  }, []);

  const showGatewayResult = useCallback((title: string, payload: unknown) => {
    setGatewayResultTitle(title);
    setGatewayResultContent(stringifyPayload(payload));
    setGatewayResultOpen(true);
  }, []);

  const afterSalesSummary = useMemo(() => {
    const pendingCount = afterSalesRecords.filter((item) => item.status === "PENDING").length;
    const autoTriggeredCount = afterSalesRecords.filter((item) => item.autoTriggered).length;
    const pageAmountFen = afterSalesRecords.reduce((sum, item) => sum + item.amountFen, 0);
    const reviewedCount = afterSalesRecords.filter((item) => item.status !== "PENDING").length;
    return { pendingCount, autoTriggeredCount, pageAmountFen, reviewedCount };
  }, [afterSalesRecords]);

  const reconciliationSummary = useMemo(() => {
    const reviewRequiredCount = reconciliationRecords.filter((item) => item.reconciliationStatus === "REVIEW_REQUIRED").length;
    const handledCount = reconciliationRecords.filter((item) => Boolean(item.latestManualAction)).length;
    const issueCount = reconciliationRecords.filter((item) => item.issueTags.length > 0).length;
    return { reviewRequiredCount, handledCount, issueCount };
  }, [reconciliationRecords]);

  const afterSalesPriorityRecords = useMemo(() => (
    [...afterSalesRecords]
      .sort((left, right) => {
        // 待处理、自动触发、金额高的工单优先出现在处理建议区。
        const leftScore = (left.status === "PENDING" ? 1000 : 0) + (left.autoTriggered ? 200 : 0) + left.amountFen;
        const rightScore = (right.status === "PENDING" ? 1000 : 0) + (right.autoTriggered ? 200 : 0) + right.amountFen;
        return rightScore - leftScore;
      })
  ), [afterSalesRecords]);

  const afterSalesPriorityPageSize = 4;
  const afterSalesPriorityPageRecords = useMemo(() => {
    const startIndex = (afterSalesPriorityPage - 1) * afterSalesPriorityPageSize;
    return afterSalesPriorityRecords.slice(startIndex, startIndex + afterSalesPriorityPageSize);
  }, [afterSalesPriorityPage, afterSalesPriorityRecords]);

  useEffect(() => {
    const totalPages = Math.max(1, Math.ceil(afterSalesPriorityRecords.length / afterSalesPriorityPageSize));
    if (afterSalesPriorityPage > totalPages) {
      setAfterSalesPriorityPage(totalPages);
    }
  }, [afterSalesPriorityPage, afterSalesPriorityRecords]);

  useEffect(() => {
    setAfterSalesPriorityPage(1);
  }, [afterSalesKeyword, afterSalesPage, afterSalesStatus]);

  const orderLifecycleTimelineItems = useMemo(() => {
    if (!orderDetail) {
      return [];
    }
    return buildTimelineItems([
      // 订单时间线把支付、预约、导师回复时限、退款和关闭节点拼成审核证据链。
      {
        sortAt: toTimestamp(orderDetail.createdAt),
        color: "blue",
        title: "订单创建",
        note: `订单 ${orderDetail.orderNo} 已创建，金额 ${formatMoneyFen(orderDetail.amountFen)}`,
        meta: formatDateTime(orderDetail.createdAt),
      },
      {
        sortAt: toTimestamp(orderDetail.paidAt),
        color: "green",
        title: "订单支付",
        note: `支付模式 ${getLabel(orderDetail.paymentMode, paymentModeLabelMap, orderDetail.paymentMode || "—")}，支付状态 ${getLabel(orderDetail.payment?.status, paymentStatusLabelMap, orderDetail.payment?.status || "—")}`,
        meta: formatDateTime(orderDetail.paidAt),
      },
      {
        sortAt: toTimestamp(orderDetail.appointmentStartAt),
        color: "cyan",
        title: "咨询预约开始",
        note: `${formatDateTime(orderDetail.appointmentStartAt)} - ${formatDateTime(orderDetail.appointmentEndAt)}`,
        meta: orderDetail.appointmentStartAt ? "已进入咨询履约时段" : "—",
      },
      {
        sortAt: toTimestamp(orderDetail.mentorReplyDeadlineAt),
        color: "orange",
        title: "导师回复时限",
        note: `回复截止时间 ${formatDateTime(orderDetail.mentorReplyDeadlineAt)}`,
        meta: "售后审核前的重要履约判断节点",
      },
      {
        sortAt: toTimestamp(orderDetail.autoCancelAt),
        color: "gold",
        title: "自动取消节点",
        note: `系统预设自动取消时间 ${formatDateTime(orderDetail.autoCancelAt)}`,
        meta: "适用于未支付或未履约场景",
      },
      {
        sortAt: toTimestamp(orderDetail.refund?.processedAt),
        color: "red",
        title: "退款处理",
        note: orderDetail.refund ? `退款原因 ${orderDetail.refund.reason || "—"}，释放席位 ${orderDetail.refund.slotReleased ? "是" : "否"}` : "—",
        meta: formatDateTime(orderDetail.refund?.processedAt),
      },
      {
        sortAt: toTimestamp(orderDetail.closedAt),
        color: "gray",
        title: "订单关闭",
        note: `订单最终状态 ${getLabel(orderDetail.status, orderStatusLabelMap, orderDetail.status)}`,
        meta: formatDateTime(orderDetail.closedAt),
      },
    ]);
  }, [orderDetail]);

  const mentorReplyOverdue = useMemo(() => {
    if (!orderDetail || orderDetail.status !== "PAID" || !orderDetail.mentorReplyDeadlineAt) {
      return false;
    }
    const deadline = toTimestamp(orderDetail.mentorReplyDeadlineAt);
    return Number.isFinite(deadline) && deadline <= Date.now();
  }, [orderDetail]);

  const reconciliationTimelineItems = useMemo(() => {
    if (!reconciliationDetail) {
      return [];
    }

    const paymentEntries = reconciliationDetail.paymentRecords.map((record, index) => ({
      sortAt: toTimestamp(record.createdAt),
      color: record.status === "SUCCESS" ? "green" : record.status === "CLOSED" ? "gray" : "blue",
      title: `支付流水 #${index + 1}`,
      note: `${getLabel(record.channel, paymentChannelLabelMap, record.channel || "—")} / ${getLabel(record.mode, paymentModeLabelMap, record.mode || "—")} · ${getLabel(record.status, paymentStatusLabelMap, record.status || "—")} · ${formatMoneyFen(record.amountFen)}`,
      meta: `${formatDateTime(record.createdAt)}${record.providerTradeNo ? ` · ${record.providerTradeNo}` : ""}`,
    }));

    return buildTimelineItems([
      // 对账时间线以支付流水为主，人工处理记录只作为最后的运营处置痕迹。
      {
        sortAt: toTimestamp(reconciliationDetail.createdAt),
        color: "blue",
        title: "订单创建",
        note: `订单 ${reconciliationDetail.orderNo} 创建，当前订单状态 ${getLabel(reconciliationDetail.orderStatus, orderStatusLabelMap, reconciliationDetail.orderStatus)}`,
        meta: formatDateTime(reconciliationDetail.createdAt),
      },
      ...paymentEntries,
      {
        sortAt: toTimestamp(reconciliationDetail.paidAt),
        color: "green",
        title: "本地支付落账",
        note: `本地状态 ${getLabel(reconciliationDetail.latestPaymentStatus, paymentStatusLabelMap, reconciliationDetail.latestPaymentStatus || "—")}，对账状态 ${getLabel(reconciliationDetail.reconciliationStatus, reconciliationStatusLabelMap, reconciliationDetail.reconciliationStatus)}`,
        meta: formatDateTime(reconciliationDetail.paidAt),
      },
      {
        sortAt: toTimestamp(reconciliationDetail.latestManualHandling?.processedAt),
        color: "purple",
        title: "人工处理",
        note: reconciliationDetail.latestManualHandling
          ? `${getLabel(reconciliationDetail.latestManualHandling.action, reconciliationActionLabelMap, reconciliationDetail.latestManualHandling.action)} · ${reconciliationDetail.latestManualHandling.note}`
          : "—",
        meta: reconciliationDetail.latestManualHandling?.processedAt
          ? `管理员 #${reconciliationDetail.latestManualHandling.operatorUserId} · ${formatDateTime(reconciliationDetail.latestManualHandling.processedAt)}`
          : "—",
      },
      {
        sortAt: toTimestamp(reconciliationDetail.closedAt),
        color: "gray",
        title: "订单关闭",
        note: `关闭后状态 ${getLabel(reconciliationDetail.orderStatus, orderStatusLabelMap, reconciliationDetail.orderStatus)}`,
        meta: formatDateTime(reconciliationDetail.closedAt),
      },
    ]);
  }, [reconciliationDetail]);

  const handleOpenAfterSalesReview = useCallback((record: AfterSalesRequestItem) => {
    setCurrentAfterSalesRecord(record);
    setAfterSalesReviewOpen(true);
    afterSalesReviewForm.setFieldsValue({ decision: "approve", reviewNote: record.reviewNote ?? "" });
  }, [afterSalesReviewForm]);

  const handleOpenOrderDetail = useCallback(async (record: AfterSalesRequestItem) => {
    if (orderDetailLoading) {
      return;
    }

    const loadingMessageKey = `order-detail-${record.orderNo}`;
    message.open({
      key: loadingMessageKey,
      type: "loading",
      content: `正在加载订单 ${record.orderNo} 详情`,
      duration: 0,
    });

    const response = await loadOrderDetail(record.orderNo, { clearCurrent: true });
    message.destroy(loadingMessageKey);

    if (!response) {
      return;
    }

    setCurrentAfterSalesRecord(record);
    setOrderDetailOpen(true);
  }, [loadOrderDetail, orderDetailLoading]);

  const handleSubmitAfterSalesReview = useCallback(async () => {
    if (!currentAfterSalesRecord) {
      return;
    }

    try {
      const values = await afterSalesReviewForm.validateFields();
      setAfterSalesReviewSaving(true);
      try {
        // 同意售后可能直接触发退款，返回结果用于提示订单最终状态。
        const response = await apiRequest<AfterSalesReviewResponse>(
          `/admin/consult/after-sales/requests/${currentAfterSalesRecord.requestId}/review`,
          {
            method: "POST",
            body: JSON.stringify({
              approved: values.decision === "approve",
              reviewNote: values.reviewNote,
            }),
          },
        );
        setAfterSalesReviewOpen(false);
        message.success(
          response.refund
            ? `申请已审核并触发退款，订单 ${response.orderNo}`
            : `申请已审核，当前状态 ${getLabel(response.status, afterSalesStatusLabelMap, response.status)}`,
        );
        await Promise.all([
          loadAfterSales(),
          orderDetailOpen ? loadOrderDetail(currentAfterSalesRecord.orderNo) : Promise.resolve(),
        ]);
      } catch (error) {
        const apiError = error as ApiClientError;
        message.error(apiError.message || "审核售后申请失败");
      } finally {
        setAfterSalesReviewSaving(false);
      }
    } catch {
      return;
    }
  }, [afterSalesReviewForm, currentAfterSalesRecord, loadAfterSales, loadOrderDetail, orderDetailOpen]);

  const handleQueryTrade = useCallback(async () => {
    if (!currentAfterSalesRecord) {
      return;
    }

    try {
      const response = await apiRequest<PaymentQueryResponse>(`/admin/consult/orders/${currentAfterSalesRecord.orderNo}/payment/query`, {
        method: "POST",
      });
      showGatewayResult("交易查询结果", response);
      await loadOrderDetail(currentAfterSalesRecord.orderNo);
    } catch (error) {
      const apiError = error as ApiClientError;
      message.error(apiError.message || "查询交易失败");
    }
  }, [currentAfterSalesRecord, loadOrderDetail, showGatewayResult]);

  const handleCloseTrade = useCallback(async () => {
    if (!currentAfterSalesRecord) {
      return;
    }

    try {
      const response = await apiRequest<PaymentCloseResponse>(`/admin/consult/orders/${currentAfterSalesRecord.orderNo}/payment/close`, {
        method: "POST",
      });
      showGatewayResult("关闭交易结果", response);
      await Promise.all([loadAfterSales(), loadOrderDetail(currentAfterSalesRecord.orderNo)]);
    } catch (error) {
      const apiError = error as ApiClientError;
      message.error(apiError.message || "关闭交易失败");
    }
  }, [currentAfterSalesRecord, loadAfterSales, loadOrderDetail, showGatewayResult]);

  const handleQueryRefund = useCallback(async () => {
    if (!currentAfterSalesRecord) {
      return;
    }

    try {
      const response = await apiRequest<RefundQueryResponse>(`/admin/consult/orders/${currentAfterSalesRecord.orderNo}/payment/refund-query`);
      showGatewayResult("退款查询结果", response);
    } catch (error) {
      const apiError = error as ApiClientError;
      message.error(apiError.message || "查询退款状态失败");
    }
  }, [currentAfterSalesRecord, showGatewayResult]);

  const handleManualRefund = useCallback(async () => {
    if (!currentAfterSalesRecord) {
      return;
    }

    try {
      const values = await refundForm.validateFields();
      setRefundSaving(true);
      try {
        // 手工退款是后台兜底入口，成功后同步刷新售后列表和订单详情。
        const response = await apiRequest<OrderRefundResponse>(`/admin/consult/orders/${currentAfterSalesRecord.orderNo}/refund`, {
          method: "POST",
          body: JSON.stringify({ reason: values.reason }),
        });
        setRefundOpen(false);
        refundForm.resetFields();
        message.success(`订单 ${response.orderNo} 已完成退款处理`);
        showGatewayResult("退款处理结果", response);
        await Promise.all([loadAfterSales(), loadOrderDetail(currentAfterSalesRecord.orderNo)]);
      } catch (error) {
        const apiError = error as ApiClientError;
        message.error(apiError.message || "手工退款失败");
      } finally {
        setRefundSaving(false);
      }
    } catch {
      return;
    }
  }, [currentAfterSalesRecord, loadAfterSales, loadOrderDetail, refundForm, showGatewayResult]);

  const handleOpenReconciliationDetail = useCallback(async (record: ReconciliationItem) => {
    setReconciliationDetailOpen(true);
    setReconciliationDetailLoading(true);
    setReconciliationDetail(null);

    try {
      const response = await apiRequest<ReconciliationDetailResponse>(`/admin/payments/reconciliation/${record.orderNo}`);
      setReconciliationDetail(response);
    } catch (error) {
      const apiError = error as ApiClientError;
      message.error(apiError.message || "加载对账详情失败");
    } finally {
      setReconciliationDetailLoading(false);
    }
  }, []);

  const handleOpenManualAction = useCallback((action: string) => {
    setCurrentRecommendedAction(action);
    setHandleActionOpen(true);
    handleActionForm.setFieldsValue({ action, note: "" });
  }, [handleActionForm]);

  const handleSubmitManualAction = useCallback(async () => {
    if (!reconciliationDetail) {
      return;
    }

    try {
      const values = await handleActionForm.validateFields();
      setHandleActionSaving(true);
      try {
        // 对账人工处理只记录运营动作，不直接绕过支付网关改流水。
        const response = await apiRequest<PaymentHandleResponse>(`/admin/payments/reconciliation/${reconciliationDetail.orderNo}/handle`, {
          method: "POST",
          body: JSON.stringify(values),
        });
        setHandleActionOpen(false);
        message.success(`已执行人工处理：${getLabel(response.action, reconciliationActionLabelMap, response.action)}`);
        await Promise.all([
          loadReconciliation(),
          handleOpenReconciliationDetail({
            orderNo: reconciliationDetail.orderNo,
            studentUserId: reconciliationDetail.studentUserId,
            studentDisplayName: reconciliationDetail.studentDisplayName,
            mentorUserId: reconciliationDetail.mentorUserId,
            mentorDisplayName: reconciliationDetail.mentorDisplayName,
            amountFen: reconciliationDetail.amountFen,
            orderStatus: reconciliationDetail.orderStatus,
            paymentMode: reconciliationDetail.paymentMode ?? "",
            paymentChannel: reconciliationDetail.paymentChannel,
            latestPaymentStatus: reconciliationDetail.latestPaymentStatus,
            providerTradeNo: reconciliationDetail.providerTradeNo,
            reconciliationStatus: reconciliationDetail.reconciliationStatus,
            issueTags: reconciliationDetail.issueTags,
            latestManualAction: response.action,
            latestManualNote: response.note,
            latestManualHandledAt: response.handledAt,
            createdAt: reconciliationDetail.createdAt,
            paidAt: reconciliationDetail.paidAt,
            closedAt: reconciliationDetail.closedAt,
            latestPaymentCreatedAt: null,
          }),
        ]);
      } catch (error) {
        const apiError = error as ApiClientError;
        message.error(apiError.message || "提交人工处理失败");
      } finally {
        setHandleActionSaving(false);
      }
    } catch {
      return;
    }
  }, [handleActionForm, handleOpenReconciliationDetail, loadReconciliation, reconciliationDetail]);

  const afterSalesColumns = useMemo<TableColumnsType<AfterSalesRequestItem>>(
    () => [
      {
        title: "售后申请",
        key: "request",
        render: (_value, record) => (
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <Text strong className="text-sm text-slate-900">#{record.requestId}</Text>
              <Tag color="blue">{getLabel(record.requestType, afterSalesRequestTypeLabelMap, record.requestType)}</Tag>
              {record.autoTriggered ? <Tag color="warning">自动触发</Tag> : null}
            </div>
            <div className="mt-2 text-xs text-slate-500">{record.orderNo}</div>
            <div className="mt-1 text-xs text-slate-400">
              提交 {formatDateTime(record.createdAt)} · {formatRelativeTime(record.createdAt)}
            </div>
          </div>
        ),
      },
      {
        title: "交易对象",
        key: "users",
        render: (_value, record) => (
          <div className="space-y-2 text-sm leading-5">
            <div>
              <div className="text-xs text-slate-400">学生</div>
              <div className="font-medium text-slate-900">{record.studentDisplayName}</div>
            </div>
            <div>
              <div className="text-xs text-slate-400">导师</div>
              <div className="font-medium text-slate-900">{record.mentorDisplayName}</div>
            </div>
          </div>
        ),
      },
      {
        title: "金额与状态",
        key: "status",
        render: (_value, record) => (
          <div className="space-y-2">
            <div className="font-['Manrope'] text-lg font-black text-slate-950">{formatMoneyFen(record.amountFen)}</div>
            <div className="flex flex-wrap gap-2">
              <Tag>{getLabel(record.orderStatus, orderStatusLabelMap, record.orderStatus)}</Tag>
              {getAfterSalesStatusTag(record.status)}
            </div>
          </div>
        ),
      },
      {
        title: "售后原因",
        dataIndex: "reason",
        render: (reason: string) => (
          <Paragraph
            ellipsis={{ rows: 2, tooltip: reason }}
            className="!mb-0 !max-w-[220px] !text-sm !leading-6 !text-slate-500"
          >
            {reason}
          </Paragraph>
        ),
      },
      {
        title: "处理动作",
        key: "action",
        render: (_value, record) => (
          <div className="flex w-[96px] flex-col gap-2">
            {record.status === "PENDING" ? (
              <Button
                type="primary"
                className="!h-9 !rounded-xl !border-none !bg-indigo-600 !px-0"
                onClick={(event) => {
                  event.stopPropagation();
                  handleOpenAfterSalesReview(record);
                }}
              >
                立即审核
              </Button>
            ) : null}
            <Button
              className="!h-9 !rounded-xl !border-slate-200 !px-0"
              onClick={(event) => {
                event.stopPropagation();
                void handleOpenOrderDetail(record);
              }}
            >
              订单详情
            </Button>
          </div>
        ),
      },
    ],
    [handleOpenAfterSalesReview, handleOpenOrderDetail],
  );

  const reconciliationColumns = useMemo<TableColumnsType<ReconciliationItem>>(
    () => [
      {
        title: "异常订单",
        key: "order",
        render: (_value, record) => (
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <Text strong className="block text-sm text-slate-900">{record.orderNo}</Text>
              {record.reconciliationStatus === "REVIEW_REQUIRED" ? <Tag color="error">待处理</Tag> : null}
            </div>
            <div className="mt-2 flex flex-wrap gap-2 text-xs text-slate-500">
              <Tag>{getLabel(record.paymentChannel, paymentChannelLabelMap, record.paymentChannel || "待补记")}</Tag>
              <Tag>{getLabel(record.paymentMode, paymentModeLabelMap, record.paymentMode)}</Tag>
            </div>
            <div className="mt-1 text-xs text-slate-400">
              创建 {formatDateTime(record.createdAt)} · 支付 {formatDateTime(record.paidAt)}
            </div>
          </div>
        ),
      },
      {
        title: "订单金额",
        key: "amount",
        render: (_value, record) => (
          <div className="font-['Manrope'] text-lg font-black text-slate-950">{formatMoneyFen(record.amountFen)}</div>
        ),
      },
      {
        title: "交易对象",
        key: "participants",
        render: (_value, record) => (
          <div className="rounded-2xl bg-slate-50 px-4 py-4">
            <div className="text-[11px] text-slate-400">学生</div>
            <div className="mt-1 text-sm font-medium text-slate-800">{record.studentDisplayName}</div>
            <div className="mt-3 text-[11px] text-slate-400">导师</div>
            <div className="mt-1 text-sm font-medium text-slate-800">{record.mentorDisplayName}</div>
          </div>
        ),
      },
      {
        title: "诊断状态",
        key: "diagnosis",
        render: (_value, record) => (
          <div className="space-y-2">
            <div className="flex flex-wrap gap-2">
              <Tag>{getLabel(record.latestPaymentStatus, paymentStatusLabelMap, record.latestPaymentStatus || "—")}</Tag>
              {getReconciliationStatusTag(record.reconciliationStatus)}
            </div>
            <div className="text-xs font-medium text-slate-500">
              {record.issueTags.length > 0 ? `命中 ${formatCount(record.issueTags.length)} 个异常标签` : "当前未命中异常标签"}
            </div>
            <div className="flex flex-wrap gap-2">
              {record.issueTags.length > 0 ? record.issueTags.map((tag) => (
                <Tag key={tag} color="error">
                  {getLabel(tag, reconciliationIssueTagLabelMap, tag)}
                </Tag>
              )) : <Text type="secondary">暂无异常标签</Text>}
            </div>
          </div>
        ),
      },
      {
        title: "最近人工处理",
        key: "manual",
        render: (_value, record) => (
          record.latestManualAction ? (
            <div className="min-w-0">
              <Tag color="processing">{getLabel(record.latestManualAction, reconciliationActionLabelMap, record.latestManualAction)}</Tag>
              <Paragraph
                ellipsis={{ rows: 2, tooltip: record.latestManualNote || "已记录人工备注" }}
                className="!mb-0 !mt-2 !max-w-[260px] !text-sm !leading-6 !text-slate-500"
              >
                {record.latestManualNote || "已记录人工备注"}
              </Paragraph>
              <div className="mt-1 text-xs text-slate-400">{formatDateTime(record.latestManualHandledAt)}</div>
            </div>
          ) : (
            <Text type="secondary">暂无人工处理</Text>
          )
        ),
      },
      {
        title: "处理动作",
        key: "action",
        render: (_value, record) => (
          <Button
            type={record.reconciliationStatus === "REVIEW_REQUIRED" ? "primary" : "default"}
            className={record.reconciliationStatus === "REVIEW_REQUIRED" ? "!h-10 !rounded-xl !border-none !bg-rose-600" : "!h-10 !rounded-xl !border-slate-200"}
            onClick={(event) => {
              event.stopPropagation();
              void handleOpenReconciliationDetail(record);
            }}
          >
            诊断详情
          </Button>
        ),
      },
    ],
    [handleOpenReconciliationDetail],
  );

  const paymentRecordColumns = useMemo<TableColumnsType<ReconciliationDetailResponse["paymentRecords"][number]>>(
    () => [
      {
        title: "创建时间",
        dataIndex: "createdAt",
        render: (value: TimeValue) => formatDateTime(value),
      },
      {
        title: "状态",
        dataIndex: "status",
        render: (value: string | null) => <Tag>{getLabel(value, paymentStatusLabelMap, value || "—")}</Tag>,
      },
      {
        title: "渠道",
        dataIndex: "channel",
        render: (value: string | null) => getLabel(value, paymentChannelLabelMap, value || "—"),
      },
      {
        title: "模式",
        dataIndex: "mode",
        render: (value: string | null) => getLabel(value, paymentModeLabelMap, value || "—"),
      },
      {
        title: "金额",
        dataIndex: "amountFen",
        render: (value: number) => formatMoneyFen(value),
      },
      {
        title: "交易号",
        dataIndex: "providerTradeNo",
        render: (value: string | null) => <Text code className="whitespace-normal break-all">{value || "—"}</Text>,
      },
    ],
    [],
  );

  const isBootFailed = !afterSalesLoading && !reconciliationLoading && Boolean(afterSalesError && reconciliationError);
  const activeMetricCards = activeTab === "after-sales"
    ? [
      {
        icon: ClipboardCheck,
        label: "待审核申请",
        value: afterSalesLoading ? "—" : formatCount(afterSalesSummary.pendingCount),
        note: "优先处理仍待结论确认的售后工单。",
        badge: "PENDING",
        tone: "amber" as const,
      },
      {
        icon: WalletCards,
        label: "本页申请金额",
        value: afterSalesLoading ? "—" : formatMoneyFen(afterSalesSummary.pageAmountFen),
        note: "帮助快速判断当前退款压力。",
        badge: "AMOUNT",
        tone: "indigo" as const,
      },
      {
        icon: ShieldCheck,
        label: "自动触发工单",
        value: afterSalesLoading ? "—" : formatCount(afterSalesSummary.autoTriggeredCount),
        note: "通常来自超时、履约异常或风控规则。",
        badge: "AUTO",
        tone: "emerald" as const,
      },
    ]
    : [
      {
        icon: AlertCircle,
        label: "异常待处理",
        value: reconciliationLoading ? "—" : formatCount(reconciliationSummary.reviewRequiredCount),
        note: "需要继续诊断和人工确认的异常订单。",
        badge: "RECON",
        tone: "rose" as const,
      },
      {
        icon: ClipboardCheck,
        label: "已人工处理",
        value: reconciliationLoading ? "—" : formatCount(reconciliationSummary.handledCount),
        note: "便于判断当前队列是否进入人工兜底阶段。",
        badge: "HANDLED",
        tone: "indigo" as const,
      },
      {
        icon: Wallet,
        label: "命中异常标签",
        value: reconciliationLoading ? "—" : formatCount(reconciliationSummary.issueCount),
        note: "优先查看金额差异、落账异常和退款状态类问题。",
        badge: "RISK",
        tone: "amber" as const,
      },
    ];
  const activeWorkspaceMeta = activeTab === "after-sales"
    ? {
      sectionLabel: "AFTER SALES",
      title: "售后工单",
      description: "聚焦售后申请审核、退款判断与订单排查。",
    }
    : {
      sectionLabel: "RECONCILIATION",
      title: "异常对账",
      description: "聚焦支付异常识别、人工诊断与异常对账处理。",
    };

  const currentTabRefreshLoading = activeTab === "after-sales" ? afterSalesLoading : reconciliationLoading;

  return (
    <AdminPageFrame>
        <AdminPageHeader
          sectionLabel={activeWorkspaceMeta.sectionLabel}
          title={activeWorkspaceMeta.title}
          tone="indigo"
          description={activeWorkspaceMeta.description}
          actions={(
            <>
              <Button className="!h-11 !rounded-2xl !border-slate-200" onClick={() => navigate("/admin/runtime?tab=operations")}>
                查看运行配置
              </Button>
              <Button
                className="!h-11 !rounded-2xl !border-slate-200 !px-5 !shadow-none"
                disabled={currentTabRefreshLoading}
                onClick={() => {
                  if (activeTab === "after-sales") {
                    void loadAfterSales();
                    return;
                  }
                  void loadReconciliation();
                }}
              >
                <RefreshCcw size={16} className={currentTabRefreshLoading ? "mr-2 animate-spin" : "mr-2"} />
                刷新数据
              </Button>
            </>
          )}
        />

        <section className="grid gap-6 md:grid-cols-3">
          {activeMetricCards.map((card) => (
            <AdminMetricCard
              key={card.label}
              icon={card.icon}
              label={card.label}
              value={card.value}
              note={card.note}
              badge={card.badge}
              tone={card.tone}
            />
          ))}
        </section>

        {isBootFailed ? (
          <Alert
            type="error"
            showIcon
            className="rounded-[28px]"
            message="交易与售后加载失败"
            description={`${afterSalesError}；${reconciliationError}`}
          />
        ) : null}

        {activeTab === "after-sales" ? (
          <div className="space-y-6 pt-4">
            <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_360px]">
              <div className="space-y-6">
                <AdminSurfaceCard
                  title={`售后申请列表 · ${formatCount(afterSalesTotal)} 条`}
                  description="快速定位待处理申请，必要时进入订单详情继续处理。"
                  extra={(
                    <Button className="!rounded-xl !border-slate-200" onClick={() => void loadAfterSales()} disabled={afterSalesLoading}>
                      <RefreshCcw size={16} className={afterSalesLoading ? "mr-2 animate-spin" : "mr-2"} />
                      刷新列表
                    </Button>
                  )}
                >
                  <div className="space-y-4">
                    <AdminFilterBar>
                      <SearchInput
                        allowClear
                        placeholder="搜索订单号 / 学生 / 导师"
                        className="min-w-[280px] flex-1"
                        value={afterSalesKeywordInput}
                        onChange={(event) => setAfterSalesKeywordInput(event.target.value)}
                        onSearch={(value) => {
                          setAfterSalesPage(1);
                          setAfterSalesKeyword(value.trim());
                        }}
                      />
                      <Select
                        value={afterSalesStatus}
                        options={afterSalesStatusOptions}
                        className="!min-w-[180px]"
                        onChange={(value) => {
                          setAfterSalesPage(1);
                          setAfterSalesStatus(value);
                          syncQuery({ afterSalesStatus: value || null });
                        }}
                      />
                      {afterSalesKeyword || afterSalesStatus ? (
                        <Button
                          className="!h-11 !rounded-2xl !border-slate-200"
                          onClick={() => {
                            setAfterSalesKeywordInput("");
                            setAfterSalesKeyword("");
                            setAfterSalesStatus("");
                            setAfterSalesPage(1);
                            syncQuery({ afterSalesStatus: null });
                          }}
                        >
                          清空筛选
                        </Button>
                      ) : null}
                    </AdminFilterBar>

                    {afterSalesError ? (
                      <Alert type="error" showIcon message="售后列表加载失败" description={afterSalesError} />
                    ) : null}

                    <Table
                      rowKey="requestId"
                      dataSource={afterSalesRecords}
                      columns={afterSalesColumns}
                      loading={afterSalesLoading}
                      tableLayout="auto"
                      rowClassName={() => "cursor-pointer"}
                      onRow={(record) => ({
                        onClick: () => {
                          void handleOpenOrderDetail(record);
                        },
                      })}
                      pagination={{
                        current: afterSalesPage,
                        pageSize: afterSalesPageSize,
                        total: afterSalesTotal,
                        showSizeChanger: { options: tablePageSizeOptions },
                        onChange: (page, size) => {
                          setAfterSalesPage(page);
                          setAfterSalesPageSize(size);
                        },
                      }}
                      locale={{ emptyText: <Empty description="当前没有匹配的售后申请" image={Empty.PRESENTED_IMAGE_SIMPLE} /> }}
                    />
                  </div>
                </AdminSurfaceCard>
              </div>

              <div className="space-y-6">
                <AdminSurfaceCard title="优先处理工单" description="结合待审核状态、自动触发标记和金额高低，优先查看更需要处理的订单。">
                  <div className="space-y-3">
                    {afterSalesPriorityPageRecords.length > 0 ? afterSalesPriorityPageRecords.map((record) => (
                      <button
                        key={record.requestId}
                        type="button"
                        className="w-full rounded-[24px] border border-slate-200/80 bg-white px-4 py-4 text-left shadow-[0_10px_30px_rgba(15,23,42,0.05)] transition hover:-translate-y-0.5 hover:border-amber-200 hover:shadow-[0_16px_34px_rgba(245,158,11,0.12)]"
                        onClick={() => void handleOpenOrderDetail(record)}
                      >
                        <div className="flex items-start justify-between gap-3">
                          <div className="flex items-start gap-3">
                            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl bg-amber-100 text-amber-600">
                              <AlertCircle size={18} />
                            </div>
                            <div>
                              <div className="text-sm font-semibold text-slate-900">{record.orderNo}</div>
                              <div className="mt-1 text-xs text-slate-500">{record.studentDisplayName} / {record.mentorDisplayName}</div>
                            </div>
                          </div>
                          <div className="rounded-full bg-slate-100 px-3 py-1 text-xs font-black text-slate-900">{formatMoneyFen(record.amountFen)}</div>
                        </div>
                        <div className="mt-3 flex flex-wrap gap-2">
                          {getAfterSalesStatusTag(record.status)}
                          {record.autoTriggered ? <Tag color="warning">自动触发</Tag> : null}
                        </div>
                        <div className="mt-3 line-clamp-2 text-sm leading-6 text-slate-500">{record.reason}</div>
                        <div className="mt-3 text-xs font-semibold text-indigo-600">查看订单详情</div>
                      </button>
                    )) : (
                      <Empty description="当前没有需要优先处理的工单" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                    )}

                    {afterSalesPriorityRecords.length > afterSalesPriorityPageSize ? (
                      <div className="flex justify-end border-t border-slate-100 pt-2">
                        <Pagination
                          simple
                          current={afterSalesPriorityPage}
                          pageSize={afterSalesPriorityPageSize}
                          total={afterSalesPriorityRecords.length}
                          onChange={(page) => setAfterSalesPriorityPage(page)}
                        />
                      </div>
                    ) : null}
                  </div>
                </AdminSurfaceCard>
              </div>
            </div>
          </div>
        ) : (
          <div className="space-y-6 pt-4">
            <AdminSurfaceCard
              title={`对账工单列表 · ${formatCount(reconciliationTotal)} 条`}
              description="聚焦异常订单，集中完成诊断与人工处理。"
              extra={(
                <Button className="!rounded-xl !border-slate-200" onClick={() => void loadReconciliation()} disabled={reconciliationLoading}>
                  <RefreshCcw size={16} className={reconciliationLoading ? "mr-2 animate-spin" : "mr-2"} />
                  刷新列表
                </Button>
              )}
            >
              <div className="space-y-4">
                <AdminFilterBar>
                  <SearchInput
                    allowClear
                    placeholder="搜索订单号 / 学生 / 导师"
                    className="min-w-[280px] flex-1"
                    value={reconciliationKeywordInput}
                    onChange={(event) => setReconciliationKeywordInput(event.target.value)}
                    onSearch={(value) => {
                      setReconciliationPage(1);
                      setReconciliationKeyword(value.trim());
                    }}
                  />
                  <Select
                    value={reconciliationOrderStatus}
                    options={orderStatusOptions}
                    className="!min-w-[170px]"
                    onChange={(value) => {
                      setReconciliationPage(1);
                      setReconciliationOrderStatus(value);
                    }}
                  />
                  <Select
                    value={reconciliationStatus}
                    options={reconciliationStatusOptions}
                    className="!min-w-[190px]"
                    onChange={(value) => {
                      setReconciliationPage(1);
                      setReconciliationStatus(value);
                      syncQuery({ reconciliationStatus: value || null });
                    }}
                  />
                  {(reconciliationKeyword || reconciliationOrderStatus || reconciliationStatus) ? (
                    <Button
                      className="!h-11 !rounded-2xl !border-slate-200"
                      onClick={() => {
                        setReconciliationKeywordInput("");
                        setReconciliationKeyword("");
                        setReconciliationOrderStatus("");
                        setReconciliationStatus("");
                        setReconciliationPage(1);
                        syncQuery({ reconciliationStatus: null });
                      }}
                    >
                      清空筛选
                    </Button>
                  ) : null}
                </AdminFilterBar>

                {reconciliationError ? (
                  <Alert type="error" showIcon message="对账列表加载失败" description={reconciliationError} />
                ) : null}

                <Table
                  rowKey="orderNo"
                  dataSource={reconciliationRecords}
                  columns={reconciliationColumns}
                  loading={reconciliationLoading}
                  tableLayout="auto"
                  rowClassName={() => "cursor-pointer"}
                  onRow={(record) => ({
                    onClick: () => {
                      void handleOpenReconciliationDetail(record);
                    },
                  })}
                  pagination={{
                    current: reconciliationPage,
                    pageSize: reconciliationPageSize,
                    total: reconciliationTotal,
                    showSizeChanger: { options: tablePageSizeOptions },
                    onChange: (page, size) => {
                      setReconciliationPage(page);
                      setReconciliationPageSize(size);
                    },
                  }}
                  locale={{ emptyText: <Empty description="当前没有匹配的对账记录" image={Empty.PRESENTED_IMAGE_SIMPLE} /> }}
                />
              </div>
            </AdminSurfaceCard>
          </div>
        )}
        {afterSalesReviewOpen
        || orderDetailOpen
        || refundOpen
        || reconciliationDetailOpen
        || handleActionOpen
        || gatewayResultOpen ? (
          <Suspense fallback={null}>
            <AdminOrdersReconciliationOverlays
              context={{
                afterSalesReviewOpen,
                setAfterSalesReviewOpen,
                handleSubmitAfterSalesReview,
                afterSalesReviewSaving,
                currentAfterSalesRecord,
                afterSalesReviewForm,
                orderDetailOpen,
                setOrderDetailOpen,
                setOrderDetail,
                setCurrentAfterSalesRecord,
                orderDetail,
                orderDetailLoading,
                mentorReplyOverdue,
                orderLifecycleTimelineItems,
                navigate,
                handleQueryTrade,
                handleCloseTrade,
                handleQueryRefund,
                refundForm,
                refundOpen,
                setRefundOpen,
                handleManualRefund,
                refundSaving,
                getAfterSalesStatusTag,
                reconciliationDetailOpen,
                setReconciliationDetailOpen,
                reconciliationDetailLoading,
                reconciliationDetail,
                handleOpenManualAction,
                paymentRecordColumns,
                reconciliationTimelineItems,
                currentRecommendedAction,
                handleActionOpen,
                setHandleActionOpen,
                handleSubmitManualAction,
                handleActionSaving,
                handleActionForm,
                gatewayResultOpen,
                setGatewayResultOpen,
                gatewayResultTitle,
                gatewayResultContent,
              }}
            />
          </Suspense>
        ) : null}
    </AdminPageFrame>
  );
}
