import { AnimatePresence, motion } from "framer-motion";
import {
  AlertCircle,
  ArrowUpRight,
  Briefcase,
  CheckCircle2,
  Clock3,
  Download,
  Eye,
  FileText,
  Landmark,
  LayoutDashboard,
  MessageSquare,
  RefreshCw,
  Search,
  Wallet,
  X,
  XCircle,
  type LucideIcon,
} from "lucide-react";
import { Suspense, lazy, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import WorkspacePageLoadingScreen from "../components/WorkspacePageLoadingScreen";
import WorkspaceRoleTopbar from "../components/workspace/WorkspaceRoleTopbar";
import { ApiClientError, apiRequest, buildQuery, isAbortError } from "../lib/apiClient";
import {
  formatDateInputValue,
  formatDateTime,
  formatMoneyFen,
  toTimestamp,
} from "../lib/formatters";
import { getMentorWorkspaceNavItems } from "../lib/workspaceNav";
import {
  buildWorkspaceSnapshotStorageKey,
  readWorkspaceSnapshot,
  writeWorkspaceSnapshot,
} from "../lib/workspaceSnapshot";

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

type ConsultOrderDetailResponse = {
  orderNo: string;
  studentUserId: number;
  studentDisplayName: string;
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
  expectedOutcomes: string[];
  prepSheetSnapshot: {
    scene: string | null;
    summaryDraft: string | null;
    expectedOutcomes: string[];
  } | null;
  attachmentsSummary: {
    currentAttachmentCount: number;
    currentMaterialTypes: string[];
  } | null;
  paymentMode: string | null;
  appointmentStartAt: number | null;
  appointmentEndAt: number | null;
  createdAt: number | null;
  paidAt: number | null;
  closedAt: number | null;
  mentorReplyDeadlineAt: number | null;
  review: {
    rating: number;
    comment: string | null;
    createdAt: number | null;
  } | null;
  afterSalesRequests: ConsultAfterSalesRequestSummary[];
};

type WithdrawalStatus = "PENDING" | "PROCESSING" | "COMPLETED" | "REJECTED" | "CANCELED";

type WithdrawalRequestRecord = {
  id: number;
  amountFen: number;
  status: WithdrawalStatus;
  createdAt: string;
  updatedAt: string;
  note: string | null;
};

type MentorWithdrawalListResponse = {
  records: WithdrawalRequestRecord[];
};

type FinanceMetric = {
  label: string;
  value: string;
  helper: string;
  icon: LucideIcon;
  toneClass: string;
};

type FinanceStatusFilter = "ALL" | "PAID" | "COMPLETED" | "REFUNDED";
type FinanceRangeFilter = "ALL" | "30D" | "90D";
type TrendRangeFilter = "7D" | "30D" | "MTD";

type TrendSeriesItem = {
  key: string;
  label: string;
  paidFen: number;
  refundedFen: number;
};

type BillTimelineStep = {
  id: string;
  title: string;
  time: string;
  completed: boolean;
  active: boolean;
};

type MentorFinanceSnapshot = {
  overview: MentorFinanceOverviewResponse;
  withdrawalRecords: WithdrawalRequestRecord[];
};

type MentorFinanceOverviewResponse = {
  metrics: {
    totalRevenueFen: number;
    totalRevenueOrderCount: number;
    completedIncomeFen: number;
    closedCount: number;
    availableWithdrawalFen: number;
    pendingWithdrawalFen: number;
    completedWithdrawalFen: number;
    refundedAmountFen: number;
    refundedOrderCount: number;
    answeredCount: number;
    avgRating: number | string | null;
  };
  trend: TrendSeriesItem[];
  bills: {
    records: ConsultOrderSummary[];
    total: number;
    page: number;
    size: number;
  };
};

const WITHDRAWAL_RECORD_PAGE_SIZE = 3;
const BILL_PAGE_SIZE = 8;
const WITHDRAWAL_STATUS_LABELS: Record<WithdrawalStatus, string> = {
  PENDING: "待审核",
  PROCESSING: "处理中",
  COMPLETED: "已入账",
  REJECTED: "未通过",
  CANCELED: "已撤回",
};

const containerVariants = {
  hidden: { opacity: 0 },
  show: { opacity: 1, transition: { staggerChildren: 0.08 } },
};

const itemVariants = {
  hidden: { opacity: 0, y: 24 },
  show: { opacity: 1, y: 0, transition: { type: "spring" as const, stiffness: 250, damping: 24 } },
};

const MentorFinanceTrendChart = lazy(() => import("../components/charts/MentorFinanceTrendChart"));

const SOURCE_PAGE_LABELS: Record<string, string> = {
  MENTOR_MARKETPLACE: "导师广场主列表",
  MENTOR_MARKETPLACE_RECOMMENDATION: "导师广场 AI 推荐区",
  MENTOR_MARKETPLACE_FAVORITES: "导师广场收藏列表",
};

function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function buildPageNumbers(totalPages: number, currentPage: number) {
  const end = Math.min(totalPages, Math.max(5, currentPage + 2));
  const start = Math.max(1, end - 4);
  return Array.from({ length: end - start + 1 }, (_, index) => start + index);
}

type PaginationControlsProps = {
  page: number;
  totalPages: number;
  totalItems: number;
  pageSize: number;
  label: string;
  onPageChange: (page: number) => void;
};

function PaginationControls({
  page,
  totalPages,
  totalItems,
  pageSize,
  label,
  onPageChange,
}: PaginationControlsProps) {
  if (totalItems <= pageSize) {
    return null;
  }

  const pageNumbers = buildPageNumbers(totalPages, page);
  const startItem = (page - 1) * pageSize + 1;
  const endItem = Math.min(totalItems, page * pageSize);

  return (
    <div className="mt-5 flex flex-col gap-3 rounded-[1.25rem] border border-slate-200 bg-slate-50 px-4 py-4 lg:flex-row lg:items-center lg:justify-between">
      <div className="text-[14px] leading-6 text-slate-500">
        当前显示 {label}第 {startItem}-{endItem} 条，共 {totalItems} 条
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <button
          type="button"
          onClick={() => onPageChange(page - 1)}
          disabled={page <= 1}
          className="rounded-full border border-slate-200 bg-white px-3.5 py-2 text-[13px] font-semibold text-slate-600 transition-colors hover:border-slate-300 hover:text-slate-900 disabled:cursor-not-allowed disabled:opacity-50"
        >
          上一页
        </button>
        {pageNumbers.map((pageNumber) => (
          <button
            key={pageNumber}
            type="button"
            onClick={() => onPageChange(pageNumber)}
            className={joinClasses(
              "rounded-full px-3.5 py-2 text-[13px] font-semibold transition-colors",
              pageNumber === page
                ? "bg-slate-900 text-white"
                : "border border-slate-200 bg-white text-slate-600 hover:border-slate-300 hover:text-slate-900",
            )}
          >
            {pageNumber}
          </button>
        ))}
        <button
          type="button"
          onClick={() => onPageChange(page + 1)}
          disabled={page >= totalPages}
          className="rounded-full border border-slate-200 bg-white px-3.5 py-2 text-[13px] font-semibold text-slate-600 transition-colors hover:border-slate-300 hover:text-slate-900 disabled:cursor-not-allowed disabled:opacity-50"
        >
          下一页
        </button>
      </div>
    </div>
  );
}

function getInitial(name: string) {
  return name.trim().charAt(0).toUpperCase() || "导";
}

function parseTime(value?: number | string | null) {
  return toTimestamp(value);
}

function normalizeRating(value: number | string | null | undefined) {
  if (value === null || value === undefined || value === "") {
    return 0;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function getPaymentModeLabel(value?: string | null) {
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

function getSourceLabel(value?: string | null) {
  if (!value) {
    return "导师广场";
  }
  return SOURCE_PAGE_LABELS[value] ?? value;
}

function isAppointmentOrder(order: Pick<ConsultOrderSummary, "appointmentStartAt" | "appointmentEndAt"> | Pick<ConsultOrderDetailResponse, "appointmentStartAt" | "appointmentEndAt">) {
  return Boolean(order.appointmentStartAt || order.appointmentEndAt);
}

function getServiceTypeLabel(order: Pick<ConsultOrderSummary, "appointmentStartAt" | "appointmentEndAt"> | Pick<ConsultOrderDetailResponse, "appointmentStartAt" | "appointmentEndAt">) {
  return isAppointmentOrder(order) ? "预约咨询" : "图文咨询";
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

function getFinanceOrderStatus(order: ConsultOrderSummary) {
  switch (order.status) {
    case "PAID":
      return {
        label: "已支付待履约",
        className: "border-amber-200 bg-amber-50 text-amber-700",
      };
    case "ANSWERED":
      return {
        label: "已履约待确认",
        className: "border-sky-200 bg-sky-50 text-sky-700",
      };
    case "CLOSED":
      return {
        label: "已完成",
        className: "border-emerald-200 bg-emerald-50 text-emerald-700",
      };
    case "REFUNDED":
      return {
        label: "已退款",
        className: "border-rose-200 bg-rose-50 text-rose-700",
      };
    case "CREATED":
    case "PAYING":
      return {
        label: "待支付",
        className: "border-slate-200 bg-slate-100 text-slate-600",
      };
    default:
      return {
        label: "已关闭 / 失效",
        className: "border-slate-200 bg-slate-100 text-slate-600",
      };
  }
}

function buildErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiClientError && error.message) {
    return error.message;
  }
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return fallback;
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

function buildBillTimeline(detail: ConsultOrderDetailResponse | null, summary: ConsultOrderSummary | null): BillTimelineStep[] {
  const source = detail ?? summary;
  if (!source) {
    return [];
  }

  const latestAfterSales = getLatestAfterSalesRequest(detail);
  const status = detail?.status ?? summary?.status ?? "CREATED";
  const mentorReplyDeadlineAt = detail?.mentorReplyDeadlineAt ?? null;

  return [
    {
      id: "created",
      title: "订单创建",
      time: formatDateTime(source.createdAt),
      completed: Boolean(source.createdAt),
      active: true,
    },
    {
      id: "paid",
      title: "支付完成",
      time: source.paidAt ? formatDateTime(source.paidAt) : "待支付",
      completed: Boolean(source.paidAt),
      active: status !== "CREATED" && status !== "PAYING",
    },
    {
      id: "service",
      title: status === "REFUNDED" ? "履约转售后" : "履约状态",
      time:
        status === "PAID"
          ? mentorReplyDeadlineAt
            ? `待导师处理 · 截止 ${formatDateTime(mentorReplyDeadlineAt)}`
            : "待导师处理"
          : status === "ANSWERED"
            ? "导师已履约，待学生确认"
            : status === "CLOSED"
              ? "学生已确认关闭"
              : status === "REFUNDED"
                ? latestAfterSales?.createdAt
                  ? `售后触发于 ${formatDateTime(latestAfterSales.createdAt)}`
                  : "已进入退款流程"
                : "尚未进入履约阶段",
      completed: status === "ANSWERED" || status === "CLOSED",
      active: status === "PAID" || status === "ANSWERED" || status === "REFUNDED",
    },
    {
      id: "settled",
      title: status === "REFUNDED" ? "退款完成" : "订单关闭",
      time:
        status === "REFUNDED"
          ? formatDateTime(latestAfterSales?.reviewedAt ?? latestAfterSales?.createdAt)
          : formatDateTime(source.closedAt),
      completed: status === "CLOSED" || status === "REFUNDED",
      active: status === "ANSWERED" || status === "CLOSED" || status === "REFUNDED",
    },
  ];
}

async function loadWithdrawalRecords(signal?: AbortSignal) {
  const response = await apiRequest<MentorWithdrawalListResponse>("/mentor/finance/withdrawals", { signal });
  return response.records;
}

async function loadFinanceOverview(
  {
    keyword,
    statusFilter,
    rangeFilter,
    trendRangeFilter,
    billPage,
  }: {
    keyword: string;
    statusFilter: FinanceStatusFilter;
    rangeFilter: FinanceRangeFilter;
    trendRangeFilter: TrendRangeFilter;
    billPage: number;
  },
  signal?: AbortSignal,
) {
  return apiRequest<MentorFinanceOverviewResponse>(
    `/mentor/finance/overview${buildQuery({
      page: billPage,
      size: BILL_PAGE_SIZE,
      keyword: keyword.trim() || undefined,
      status: statusFilter !== "ALL" ? statusFilter : undefined,
      range: rangeFilter !== "ALL" ? rangeFilter : undefined,
      trendRange: trendRangeFilter,
    })}`,
    { signal },
  );
}

export default function MentorFinancePage() {
  const { role, displayName, userId } = useAuth();
  const [overview, setOverview] = useState<MentorFinanceOverviewResponse | null>(null);
  const [withdrawalRecords, setWithdrawalRecords] = useState<WithdrawalRequestRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdatedAt, setLastUpdatedAt] = useState<string | null>(null);
  const [keyword, setKeyword] = useState("");
  const [statusFilter, setStatusFilter] = useState<FinanceStatusFilter>("ALL");
  const [rangeFilter, setRangeFilter] = useState<FinanceRangeFilter>("ALL");
  const [trendRangeFilter, setTrendRangeFilter] = useState<TrendRangeFilter>("30D");
  const [withdrawAmountYuan, setWithdrawAmountYuan] = useState("");
  const [withdrawNote, setWithdrawNote] = useState("");
  const [withdrawFeedback, setWithdrawFeedback] = useState<string | null>(null);
  const [creatingWithdrawal, setCreatingWithdrawal] = useState(false);
  const [withdrawalBusyId, setWithdrawalBusyId] = useState<number | null>(null);
  const [withdrawalPage, setWithdrawalPage] = useState(1);
  const [billPage, setBillPage] = useState(1);
  const [selectedBill, setSelectedBill] = useState<ConsultOrderSummary | null>(null);
  const [selectedBillDetail, setSelectedBillDetail] = useState<ConsultOrderDetailResponse | null>(null);
  const [billDetailLoading, setBillDetailLoading] = useState(false);
  const [billDetailError, setBillDetailError] = useState<string | null>(null);
  const snapshotKey = buildWorkspaceSnapshotStorageKey("mentor", "finance", userId ?? "current");

  const persistFinanceSnapshot = (
    nextOverview: MentorFinanceOverviewResponse,
    nextWithdrawalRecords: WithdrawalRequestRecord[],
    updatedAt = new Date().toISOString(),
  ) => {
    // 财务中心把账单聚合和提现记录一起存，返回页面时可先回显完整工作区。
    writeWorkspaceSnapshot(snapshotKey, {
      overview: nextOverview,
      withdrawalRecords: nextWithdrawalRecords,
    } satisfies MentorFinanceSnapshot, updatedAt);
    setLastUpdatedAt(updatedAt);
    return updatedAt;
  };

  useEffect(() => {
    if (role !== "MENTOR") {
      return undefined;
    }

    const snapshot = readWorkspaceSnapshot<MentorFinanceSnapshot>(snapshotKey);
    if (snapshot?.data) {
      // 首屏先恢复快照，随后分接口刷新提现和账单。
      if (snapshot.data.overview) {
        setOverview(snapshot.data.overview);
        setLastUpdatedAt(snapshot.updatedAt);
        setError(null);
      }
      setWithdrawalRecords(snapshot.data.withdrawalRecords ?? []);
    }
  }, [role, snapshotKey]);

  useEffect(() => {
    if (role !== "MENTOR") {
      return undefined;
    }

    const controller = new AbortController();
    const snapshot = readWorkspaceSnapshot<MentorFinanceSnapshot>(snapshotKey);

    // 提现记录独立刷新，避免账单筛选变化时反复请求同一份提现列表。
    void loadWithdrawalRecords(controller.signal)
      .then((records) => {
        setWithdrawalRecords(records);
        const snapshotOverview = overview ?? snapshot?.data?.overview;
        if (snapshotOverview) {
          persistFinanceSnapshot(snapshotOverview, records, new Date().toISOString());
        }
      })
      .catch((requestError) => {
        if (isAbortError(requestError)) {
          return;
        }
        if (!snapshot?.data) {
          setError(buildErrorMessage(requestError, "提现记录加载失败，请稍后重试。"));
        }
      });

    return () => controller.abort();
  }, [role, snapshotKey]);

  useEffect(() => {
    if (role !== "MENTOR") {
      return undefined;
    }

    const controller = new AbortController();
    const snapshot = readWorkspaceSnapshot<MentorFinanceSnapshot>(snapshotKey);
    setLoading(true);
    setError(null);

    // 账单聚合随筛选和分页变化，趋势窗口也由同一接口返回，便于保持口径一致。
    void loadFinanceOverview(
      {
        keyword,
        statusFilter,
        rangeFilter,
        trendRangeFilter,
        billPage,
      },
      controller.signal,
    )
      .then((response) => {
        setOverview(response);
        persistFinanceSnapshot(response, withdrawalRecords.length ? withdrawalRecords : snapshot?.data?.withdrawalRecords ?? [], new Date().toISOString());
        setError(null);
      })
      .catch((requestError) => {
        if (isAbortError(requestError)) {
          return;
        }
        if (!snapshot?.data) {
          setError(buildErrorMessage(requestError, "导师财务中心加载失败，请稍后重试。"));
        } else {
          setError(null);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      });

    return () => controller.abort();
  }, [billPage, keyword, rangeFilter, role, snapshotKey, statusFilter, trendRangeFilter]);

  useEffect(() => {
    if (!selectedBill) {
      setSelectedBillDetail(null);
      setBillDetailError(null);
      return;
    }

    const controller = new AbortController();
    setBillDetailLoading(true);
    setBillDetailError(null);

    // 账单列表只放摘要，点击后再取订单详情拼出支付、售后和服务时间线。
    void apiRequest<ConsultOrderDetailResponse>(`/consult/orders/${selectedBill.orderNo}`, { signal: controller.signal })
      .then((response) => {
        setSelectedBillDetail(response);
      })
      .catch((requestError) => {
        if (!isAbortError(requestError)) {
          setBillDetailError(buildErrorMessage(requestError, "账单详情读取失败，请稍后重试。"));
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setBillDetailLoading(false);
        }
      });

    return () => controller.abort();
  }, [selectedBill]);

  useEffect(() => {
    setBillPage(1);
  }, [keyword, statusFilter, rangeFilter]);

  const refreshFinanceData = async () => {
    // 手动刷新同时校准账单聚合和提现状态，成功后覆盖工作区快照。
    const [overviewResponse, withdrawalsResponse] = await Promise.all([
      loadFinanceOverview({
        keyword,
        statusFilter,
        rangeFilter,
        trendRangeFilter,
        billPage,
      }),
      loadWithdrawalRecords(),
    ]);
    setOverview(overviewResponse);
    setWithdrawalRecords(withdrawalsResponse);
    persistFinanceSnapshot(overviewResponse, withdrawalsResponse);
    return { overviewResponse, withdrawalsResponse };
  };

  const handleRefresh = async () => {
    setRefreshing(true);
    setError(null);
    const hasCachedData = Boolean(overview);
    try {
      await refreshFinanceData();
    } catch (requestError) {
      if (!hasCachedData) {
        setError(buildErrorMessage(requestError, "导师财务中心刷新失败，请稍后重试。"));
      }
    } finally {
      setRefreshing(false);
    }
  };

  const metrics = overview?.metrics;
  const trendSeries = overview?.trend ?? [];
  const billRecords = overview?.bills.records ?? [];
  const completedIncomeFen = metrics?.completedIncomeFen ?? 0;
  const refundedAmountFen = metrics?.refundedAmountFen ?? 0;
  const pendingWithdrawalFen = metrics?.pendingWithdrawalFen ?? 0;
  const completedWithdrawalFen = metrics?.completedWithdrawalFen ?? 0;
  const availableWithdrawalFen = metrics?.availableWithdrawalFen ?? 0;
  const normalizedRating = normalizeRating(metrics?.avgRating);
  const withdrawalTotalPages = Math.max(1, Math.ceil(withdrawalRecords.length / WITHDRAWAL_RECORD_PAGE_SIZE));
  const billTotalPages = Math.max(1, Math.ceil((overview?.bills.total ?? 0) / BILL_PAGE_SIZE));
  const pagedWithdrawalRecords = useMemo(
    () => withdrawalRecords.slice((withdrawalPage - 1) * WITHDRAWAL_RECORD_PAGE_SIZE, withdrawalPage * WITHDRAWAL_RECORD_PAGE_SIZE),
    [withdrawalPage, withdrawalRecords],
  );

  useEffect(() => {
    setWithdrawalPage((current) => Math.min(current, withdrawalTotalPages));
  }, [withdrawalTotalPages]);

  useEffect(() => {
    setBillPage((current) => Math.min(current, billTotalPages));
  }, [billTotalPages]);

  const financeMetrics = useMemo<FinanceMetric[]>(() => [
    {
      label: "累计成交额",
      value: formatMoneyFen(metrics?.totalRevenueFen ?? 0),
      helper: `${metrics?.totalRevenueOrderCount ?? 0} 单订单进入成交口径`,
      icon: Wallet,
      toneClass: "from-slate-900 via-slate-800 to-slate-900 text-white",
    },
    {
      label: "已完成收入",
      value: formatMoneyFen(completedIncomeFen),
      helper: `${metrics?.closedCount ?? 0} 单已完成服务`,
      icon: CheckCircle2,
      toneClass: "from-emerald-500 to-teal-500 text-white",
    },
    {
      label: "可提现金额",
      value: formatMoneyFen(availableWithdrawalFen),
      helper: "按当前收入与申请进度计算",
      icon: Landmark,
      toneClass: "from-indigo-500 to-violet-500 text-white",
    },
    {
      label: "已退款金额",
      value: formatMoneyFen(refundedAmountFen),
      helper: `${metrics?.refundedOrderCount ?? 0} 单退款影响`,
      icon: AlertCircle,
      toneClass: "from-rose-500 to-orange-500 text-white",
    },
    {
      label: "提现中",
      value: formatMoneyFen(pendingWithdrawalFen),
      helper: `${withdrawalRecords.filter((item) => item.status === "PENDING" || item.status === "PROCESSING").length} 条申请待处理`,
      icon: Clock3,
      toneClass: "from-amber-400 to-orange-400 text-white",
    },
    {
      label: "平均评分",
      value: normalizedRating.toFixed(1),
      helper: "来自已完成订单的评价",
      icon: Briefcase,
      toneClass: "from-sky-500 to-cyan-500 text-white",
    },
  ], [availableWithdrawalFen, completedIncomeFen, metrics?.closedCount, metrics?.refundedOrderCount, metrics?.totalRevenueFen, metrics?.totalRevenueOrderCount, normalizedRating, pendingWithdrawalFen, refundedAmountFen, withdrawalRecords]);

  const handleExportBills = () => {
    // 导出仅使用当前页账单，保持和页面筛选结果一致。
    const lines = [
      ["订单号", "学生", "财务状态", "金额(元)", "支付方式", "创建时间", "支付时间", "完成时间"].join(","),
      ...billRecords.map((order) => [
        order.orderNo,
        `"${order.counterpartDisplayName}"`,
        getFinanceOrderStatus(order).label,
        (order.amountFen / 100).toFixed(2),
        getPaymentModeLabel(order.paymentMode),
        order.createdAt ?? "",
        order.paidAt ?? "",
        order.closedAt ?? "",
      ].join(",")),
    ];

    const blob = new Blob([`\uFEFF${lines.join("\n")}`], { type: "text/csv;charset=utf-8;" });
    const url = window.URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `mentor-finance-bills-${formatDateInputValue(new Date())}.csv`;
    anchor.click();
    window.URL.revokeObjectURL(url);
  };

  const handleCreateWithdrawal = async () => {
    const nextAmountFen = Math.round(Number(withdrawAmountYuan) * 100);
    if (!Number.isFinite(nextAmountFen) || nextAmountFen <= 0) {
      setWithdrawFeedback("请先填写有效的提现金额。");
      return;
    }
    if (nextAmountFen > availableWithdrawalFen) {
      setWithdrawFeedback("提现金额不能超过当前可提现额度。");
      return;
    }

    setCreatingWithdrawal(true);
    setWithdrawFeedback(null);
    try {
      // 提现申请先插入本地列表给出即时反馈，再后台刷新可提现额度。
      const response = await apiRequest<WithdrawalRequestRecord>("/mentor/finance/withdrawals", {
        method: "POST",
        body: JSON.stringify({
          amountFen: nextAmountFen,
          note: withdrawNote.trim() || null,
        }),
      });
      const nextWithdrawalRecords = [response, ...withdrawalRecords];
      setWithdrawalRecords(nextWithdrawalRecords);
      setWithdrawalPage(1);
      if (overview) {
        persistFinanceSnapshot(overview, nextWithdrawalRecords, new Date().toISOString());
      }
      setWithdrawAmountYuan("");
      setWithdrawNote("");
      setWithdrawFeedback("提现申请已提交，请留意后续进度。");
      try {
        await refreshFinanceData();
      } catch {
        // 保留当前成功反馈，后台刷新失败时继续展示本地最新记录。
      }
    } catch (requestError) {
      setWithdrawFeedback(buildErrorMessage(requestError, "提现申请提交失败，请稍后重试。"));
    } finally {
      setCreatingWithdrawal(false);
    }
  };

  const handleUpdateWithdrawalStatus = async (recordId: number, nextStatus: WithdrawalStatus) => {
    setWithdrawalBusyId(recordId);
    setWithdrawFeedback(null);
    try {
      // 演示流转受后端状态机限制，前端只提交目标状态。
      const response = await apiRequest<WithdrawalRequestRecord>(`/mentor/finance/withdrawals/${recordId}/status`, {
        method: "POST",
        body: JSON.stringify({ status: nextStatus }),
      });
      const nextWithdrawalRecords = withdrawalRecords.map((item) => (item.id === recordId ? response : item));
      setWithdrawalRecords(nextWithdrawalRecords);
      if (overview) {
        persistFinanceSnapshot(overview, nextWithdrawalRecords, new Date().toISOString());
      }
      setWithdrawFeedback(`当前申请已更新为“${WITHDRAWAL_STATUS_LABELS[response.status]}”。`);
      try {
        await refreshFinanceData();
      } catch {
        // 保留本地状态，后台刷新失败时不覆盖当前流转结果。
      }
    } catch (requestError) {
      setWithdrawFeedback(buildErrorMessage(requestError, "提现状态更新失败，请稍后重试。"));
    } finally {
      setWithdrawalBusyId(null);
    }
  };

  if (loading && !overview) {
    return (
      <WorkspacePageLoadingScreen
        title="正在准备导师财务中心"
        description="正在加载收入概览、账单记录和提现信息，请稍候。"
      />
    );
  }

  const selectedBillStatus = selectedBill ? getFinanceOrderStatus(selectedBill) : null;
  const detailExpectedOutcomes = selectedBillDetail?.expectedOutcomes?.length
    ? selectedBillDetail.expectedOutcomes
    : selectedBillDetail?.prepSheetSnapshot?.expectedOutcomes ?? [];
  const detailLatestAfterSales = getLatestAfterSalesRequest(selectedBillDetail);
  const billTimeline = buildBillTimeline(selectedBillDetail, selectedBill);
  const trendRangeLabel = trendRangeFilter === "7D" ? "近 7 天" : trendRangeFilter === "30D" ? "近 30 天" : "本月";

  return (
    <div className="relative min-h-screen bg-[#f6f7fb] text-slate-900">
      <div className="pointer-events-none fixed inset-0 z-0 overflow-hidden">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(79,70,229,0.1),transparent_30%),radial-gradient(circle_at_bottom_right,rgba(20,184,166,0.08),transparent_30%),linear-gradient(180deg,#eef2ff_0%,#f8fafc_58%,#f8fafc_100%)]" />
        <div className="page-top-glow page-top-glow--indigo-soft" />
      </div>

      <WorkspaceRoleTopbar
        sectionLabel="Finance Center"
        title="导师财务中心"
        icon={Wallet}
        navItems={getMentorWorkspaceNavItems("finance")}
        displayName={displayName}
        userSubtitle="查看收入与提现进度"
        userFallbackLabel="导师"
        userFallbackInitial="导"
        lastUpdatedAt={lastUpdatedAt}
        onRefresh={() => {
          void handleRefresh();
        }}
        refreshing={refreshing}
        refreshTitle="刷新导师财务中心数据"
      />

      <main className="relative z-10 mx-auto w-full max-w-[96rem] px-4 pb-6 pt-8 sm:px-6 lg:px-8 lg:pb-8 lg:pt-10">
        <motion.div variants={containerVariants} initial="hidden" animate="show" className="space-y-6">
          <motion.section
            variants={itemVariants}
            className="relative overflow-hidden rounded-[2rem] bg-gradient-to-br from-slate-900 via-indigo-950 to-slate-900 px-7 py-7 text-white shadow-lg lg:px-8 lg:py-8"
          >
            <div className="absolute right-0 top-0 h-44 w-44 rounded-full bg-indigo-500/20 blur-3xl" />
            <div className="absolute bottom-0 left-0 h-32 w-32 rounded-full bg-teal-500/15 blur-3xl" />
            <div className="relative z-10 flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
              <div className="max-w-3xl">
                <h1 className="text-[2.55rem] font-black tracking-tight lg:text-[2.9rem]">收入概览、账单记录与提现进度</h1>
                <p className="mt-4 max-w-3xl text-[15px] leading-8 text-indigo-100/80 lg:text-base">
                  在这里查看收入、账单和提现进度，重要变化都会同步到记录里。
                </p>
              </div>
              <div className="rounded-[1.5rem] border border-white/10 bg-white/8 px-5 py-4 text-[15px] font-medium leading-7 text-indigo-100/80">
                当前共 {overview?.bills.total ?? 0} 条账单记录，{withdrawalRecords.length} 条提现记录
              </div>
            </div>
          </motion.section>

          {error && !overview ? (
            <motion.section variants={itemVariants} className="rounded-[1.5rem] border border-rose-200 bg-rose-50 px-5 py-4 text-[15px] leading-7 text-rose-700">
              {error}
            </motion.section>
          ) : null}

          <motion.section variants={itemVariants} className="grid gap-4 lg:grid-cols-3 xl:grid-cols-6">
            {financeMetrics.map((item) => {
              const Icon = item.icon;
              return (
                <div key={item.label} className={joinClasses("rounded-[1.5rem] bg-gradient-to-br p-6 shadow-[0_12px_40px_rgba(15,23,42,0.12)]", item.toneClass)}>
                  <div className="flex items-center justify-between">
                    <div className="text-sm font-semibold text-white/78">{item.label}</div>
                    <Icon size={20} className="text-white/80" />
                  </div>
                  <div className="mt-5 text-[2rem] font-black tracking-tight lg:text-[2.1rem]">{item.value}</div>
                  <div className="mt-2 text-[13px] leading-6 text-white/70">{item.helper}</div>
                </div>
              );
            })}
          </motion.section>

          <motion.section variants={itemVariants} className="grid gap-6 xl:grid-cols-[minmax(0,1.45fr)_minmax(340px,0.85fr)]">
            <div className="rounded-[2rem] border border-white/80 bg-white/88 p-6 shadow-[0_20px_60px_rgba(148,163,184,0.14)] backdrop-blur-xl">
              <div className="flex items-center justify-between gap-4">
                <div>
                  <h2 className="text-[1.65rem] font-bold text-slate-950">{trendRangeLabel}成交与退款趋势</h2>
                  <p className="mt-2 text-[15px] leading-7 text-slate-500">按日期汇总成交与退款金额，方便直接查看最近波动。</p>
                </div>
                <div className="flex flex-wrap items-center justify-end gap-2">
                  <div className="flex items-center rounded-full border border-slate-200 bg-slate-50 p-1">
                    {[
                      { value: "7D", label: "近 7 天" },
                      { value: "30D", label: "近 30 天" },
                      { value: "MTD", label: "本月" },
                    ].map((item) => (
                      <button
                        key={item.value}
                        type="button"
                        onClick={() => setTrendRangeFilter(item.value as TrendRangeFilter)}
                        className={joinClasses(
                          "rounded-full px-4 py-1.5 text-[13px] font-semibold transition-colors",
                          trendRangeFilter === item.value
                            ? "bg-white text-slate-900 shadow-sm"
                            : "text-slate-500 hover:text-slate-900",
                        )}
                      >
                        {item.label}
                      </button>
                    ))}
                  </div>
                  <div className="rounded-full border border-slate-200 bg-slate-50 px-4 py-1.5 text-[13px] font-semibold text-slate-500">
                    单位：元
                  </div>
                </div>
              </div>

              <Suspense
                fallback={(
                  <div className="mt-5 rounded-[1.5rem] border border-slate-100 bg-slate-50/70 px-3 py-4">
                    <div className="h-[308px] animate-pulse rounded-[1.25rem] border border-slate-100 bg-white/80" />
                  </div>
                )}
              >
                <MentorFinanceTrendChart trendSeries={trendSeries} />
              </Suspense>
            </div>

            <div className="rounded-[2rem] border border-white/80 bg-white/88 p-6 shadow-[0_20px_60px_rgba(148,163,184,0.14)] backdrop-blur-xl">
              <h2 className="text-[1.65rem] font-bold text-slate-950">退款影响与待确认风险</h2>
              <p className="mt-2 text-[14px] leading-7 text-slate-500">优先关注这两类会影响收入确认的订单。</p>
              <div className="mt-5 space-y-3">
                <div className="rounded-[1.25rem] border border-slate-200 bg-slate-50/70 px-4 py-4">
                  <div className="flex items-start justify-between gap-4">
                    <div className="flex items-start gap-3">
                      <div className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-rose-50 text-rose-500">
                        <XCircle size={17} />
                      </div>
                      <div>
                        <div className="text-[16px] font-bold text-slate-900">已退款订单</div>
                        <div className="mt-2 text-[14px] leading-7 text-slate-600">
                          已退款 {formatMoneyFen(refundedAmountFen)}，会直接拉低当前可提现额度。
                        </div>
                      </div>
                    </div>
                    <div className="rounded-full bg-rose-50 px-3 py-1.5 text-[13px] font-semibold text-rose-600">
                      {metrics?.refundedOrderCount ?? 0} 条
                    </div>
                  </div>
                </div>

                <div className="rounded-[1.25rem] border border-slate-200 bg-slate-50/70 px-4 py-4">
                  <div className="flex items-start justify-between gap-4">
                    <div className="flex items-start gap-3">
                      <div className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-amber-50 text-amber-500">
                        <Clock3 size={17} />
                      </div>
                      <div>
                        <div className="text-[16px] font-bold text-slate-900">已履约待确认</div>
                        <div className="mt-2 text-[14px] leading-7 text-slate-600">
                          这部分金额暂未计入已完成收入，需等待学生确认。
                        </div>
                      </div>
                    </div>
                    <div className="rounded-full bg-amber-50 px-3 py-1.5 text-[13px] font-semibold text-amber-600">
                      {metrics?.answeredCount ?? 0} 条
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </motion.section>

          <motion.section variants={itemVariants} className="grid gap-6 xl:grid-cols-[minmax(340px,0.9fr)_minmax(0,1.35fr)]">
            <div className="rounded-[2rem] border border-white/80 bg-white/88 p-6 shadow-[0_20px_60px_rgba(148,163,184,0.14)] backdrop-blur-xl">
              <div className="flex min-h-[4.1rem] flex-col justify-center">
                <h2 className="text-[1.72rem] font-bold leading-tight text-slate-950">提交提现申请</h2>
                <p className="mt-2 text-[15px] leading-7 text-slate-500">
                  在这里提交申请，并查看当前可申请额度与备注信息。
                </p>
              </div>

              <div className="mt-5 grid gap-3 sm:grid-cols-2">
                <div className="rounded-[1.25rem] border border-slate-100 bg-slate-50 px-4 py-4">
                  <div className="text-[15px] text-slate-500">当前可申请额度</div>
                  <div className="mt-2 text-[2rem] font-black text-slate-900">{formatMoneyFen(availableWithdrawalFen)}</div>
                  <div className="mt-1 text-[13px] leading-6 text-slate-400">已完成收入扣除处理中与已入账金额后的剩余额度</div>
                </div>
                <div className="rounded-[1.25rem] border border-slate-100 bg-slate-50 px-4 py-4">
                  <div className="text-[15px] text-slate-500">流转中的申请</div>
                  <div className="mt-2 text-[2rem] font-black text-slate-900">{formatMoneyFen(pendingWithdrawalFen)}</div>
                  <div className="mt-1 text-[13px] leading-6 text-slate-400">
                    {withdrawalRecords.filter((item) => item.status === "PENDING" || item.status === "PROCESSING").length} 条申请仍在处理中
                  </div>
                </div>
              </div>

              <div className="mt-5 grid gap-3">
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  value={withdrawAmountYuan}
                  onChange={(event) => setWithdrawAmountYuan(event.target.value)}
                  placeholder="填写本次申请金额（元）"
                  className="rounded-[1rem] border border-slate-200 bg-white px-4 py-3.5 text-[15px] text-slate-700 outline-none transition-colors focus:border-indigo-300"
                />
                <textarea
                  rows={4}
                  value={withdrawNote}
                  onChange={(event) => setWithdrawNote(event.target.value)}
                  placeholder="可选：补充这次申请的备注，例如到账说明或处理备注。"
                  className="rounded-[1rem] border border-slate-200 bg-white px-4 py-3.5 text-[15px] leading-7 text-slate-700 outline-none transition-colors focus:border-indigo-300"
                />
                {withdrawFeedback ? (
                  <div className="rounded-[1rem] border border-indigo-100 bg-indigo-50 px-4 py-3 text-[15px] font-medium leading-7 text-indigo-700">
                    {withdrawFeedback}
                  </div>
                ) : null}
                <button
                  type="button"
                  onClick={() => void handleCreateWithdrawal()}
                  disabled={availableWithdrawalFen <= 0 || creatingWithdrawal}
                  className="inline-flex items-center justify-center rounded-full bg-slate-900 px-5 py-3.5 text-[15px] font-semibold text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {creatingWithdrawal ? <RefreshCw size={16} className="mr-2 animate-spin" /> : <ArrowUpRight size={16} className="mr-2" />}
                  {creatingWithdrawal ? "提交中" : "提交提现申请"}
                </button>
              </div>
            </div>

            <div className="rounded-[2rem] border border-white/80 bg-white/88 p-6 shadow-[0_20px_60px_rgba(148,163,184,0.14)] backdrop-blur-xl">
              <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                <div className="flex min-h-[4.1rem] flex-col justify-center">
                  <h2 className="text-[1.72rem] font-bold leading-tight text-slate-950">查看流转进度</h2>
                  <p className="mt-2 text-[15px] leading-7 text-slate-500">
                    这里展示每笔申请的状态、备注和更新时间。
                  </p>
                </div>
                <div className="grid gap-3 sm:grid-cols-2 lg:min-w-[15rem] lg:grid-cols-1">
                  <div className="rounded-[1rem] border border-slate-100 bg-slate-50 px-4 py-3">
                    <div className="text-[13px] text-slate-500">累计申请记录</div>
                    <div className="mt-1 text-xl font-black text-slate-900">{withdrawalRecords.length}</div>
                  </div>
                  <div className="rounded-[1rem] border border-slate-100 bg-slate-50 px-4 py-3">
                    <div className="text-[13px] text-slate-500">已入账金额</div>
                    <div className="mt-1 text-xl font-black text-slate-900">{formatMoneyFen(completedWithdrawalFen)}</div>
                  </div>
                </div>
              </div>

              <div className="mt-5 rounded-[1.25rem] border border-dashed border-slate-200 bg-slate-50 px-4 py-3 text-[14px] leading-7 text-slate-500">
                你可以在这里继续跟进每笔申请的处理状态。
              </div>

              <div className="mt-5 space-y-3">
                {withdrawalRecords.length ? pagedWithdrawalRecords.map((record) => (
                  <div key={record.id} className="rounded-[1.25rem] border border-slate-200 bg-white px-4 py-4">
                    <div className="grid gap-4 lg:grid-cols-[minmax(13rem,0.7fr)_minmax(0,1fr)_auto] lg:items-center">
                      <div>
                        <div className="flex flex-wrap items-center gap-2">
                          <div className="text-lg font-bold text-slate-900">{formatMoneyFen(record.amountFen)}</div>
                          <span className={joinClasses(
                            "rounded-full px-3 py-1.5 text-[13px] font-semibold",
                            record.status === "COMPLETED" && "bg-emerald-50 text-emerald-700",
                            record.status === "PENDING" && "bg-amber-50 text-amber-700",
                            record.status === "PROCESSING" && "bg-sky-50 text-sky-700",
                            record.status === "REJECTED" && "bg-rose-50 text-rose-700",
                            record.status === "CANCELED" && "bg-slate-100 text-slate-600",
                          )}>
                            {WITHDRAWAL_STATUS_LABELS[record.status]}
                          </span>
                        </div>
                        <div className="mt-2 flex flex-wrap gap-x-3 gap-y-1 text-[12px] leading-6 text-slate-400">
                          <span>创建于 {formatDateTime(record.createdAt)}</span>
                          <span>最近更新 {formatDateTime(record.updatedAt)}</span>
                        </div>
                      </div>

                      <div className="rounded-[1rem] border border-slate-100 bg-slate-50 px-4 py-3 text-[14px] leading-6 text-slate-600">
                        {record.note?.trim() || "当前没有补充备注。"}
                      </div>

                      <div className="flex flex-wrap gap-2 lg:justify-end">
                        {record.status === "PENDING" ? (
                          <>
                            <button
                              type="button"
                              onClick={() => void handleUpdateWithdrawalStatus(record.id, "PROCESSING")}
                              disabled={withdrawalBusyId === record.id}
                              className="rounded-full border border-sky-200 bg-sky-50 px-3.5 py-2 text-[13px] font-semibold text-sky-700 disabled:cursor-not-allowed disabled:opacity-60"
                            >
                              推进到处理中
                            </button>
                            <button
                              type="button"
                              onClick={() => void handleUpdateWithdrawalStatus(record.id, "CANCELED")}
                              disabled={withdrawalBusyId === record.id}
                              className="rounded-full border border-slate-200 bg-slate-100 px-3.5 py-2 text-[13px] font-semibold text-slate-600 disabled:cursor-not-allowed disabled:opacity-60"
                            >
                              撤回申请
                            </button>
                          </>
                        ) : null}
                        {record.status === "PROCESSING" ? (
                          <>
                            <button
                              type="button"
                              onClick={() => void handleUpdateWithdrawalStatus(record.id, "COMPLETED")}
                              disabled={withdrawalBusyId === record.id}
                              className="rounded-full border border-emerald-200 bg-emerald-50 px-3.5 py-2 text-[13px] font-semibold text-emerald-700 disabled:cursor-not-allowed disabled:opacity-60"
                            >
                              推进到已入账
                            </button>
                            <button
                              type="button"
                              onClick={() => void handleUpdateWithdrawalStatus(record.id, "REJECTED")}
                              disabled={withdrawalBusyId === record.id}
                              className="rounded-full border border-rose-200 bg-rose-50 px-3.5 py-2 text-[13px] font-semibold text-rose-700 disabled:cursor-not-allowed disabled:opacity-60"
                            >
                              标记未通过
                            </button>
                          </>
                        ) : null}
                      </div>
                    </div>
                  </div>
                )) : (
                  <div className="rounded-[1.25rem] border border-dashed border-slate-200 bg-slate-50 px-4 py-5 text-[15px] leading-7 text-slate-500">
                    当前还没有提现申请记录。提交申请后，可在这里查看后续进度。
                  </div>
                )}
              </div>
              <PaginationControls
                page={withdrawalPage}
                totalPages={withdrawalTotalPages}
                totalItems={withdrawalRecords.length}
                pageSize={WITHDRAWAL_RECORD_PAGE_SIZE}
                label="提现记录"
                onPageChange={setWithdrawalPage}
              />
            </div>
          </motion.section>

          <motion.section
            variants={itemVariants}
            className="rounded-[2rem] border border-white/80 bg-white/88 p-6 shadow-[0_20px_60px_rgba(148,163,184,0.14)] backdrop-blur-xl"
          >
            <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
              <div className="flex min-h-[4.1rem] flex-col justify-center">
                <h2 className="text-[1.72rem] font-bold leading-tight text-slate-950">账单明细</h2>
                <p className="mt-2 text-[15px] leading-7 text-slate-500">
                  当前账单直接按咨询订单状态映射展示，支持在本页打开账单详情抽屉，快速查看问题摘要、服务方式、来源与时间线关键信息。
                </p>
              </div>
              <button
                type="button"
                onClick={handleExportBills}
                className="inline-flex items-center justify-center rounded-full border border-slate-200 bg-white px-4 py-3 text-[15px] font-semibold text-slate-700 transition-colors hover:border-indigo-200 hover:text-indigo-600"
              >
                <Download size={16} className="mr-2" />
                导出当前页账单
              </button>
            </div>

            <div className="mt-6 flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
              <div className="relative max-w-md flex-1">
                <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5">
                  <Search size={16} className="text-slate-400" />
                </div>
                <input
                  value={keyword}
                  onChange={(event) => setKeyword(event.target.value)}
                  type="text"
                  placeholder="搜索订单号 / 学生昵称 / 问题关键词..."
                  className="w-full rounded-full border border-slate-200 bg-slate-50 py-3 pl-10 pr-4 text-[15px] text-slate-900 outline-none transition-all placeholder:text-slate-400 focus:border-indigo-400 focus:bg-white focus:ring-4 focus:ring-indigo-500/10"
                />
              </div>

              <div className="flex flex-wrap items-center gap-2.5">
                <label className="inline-flex items-center gap-1.5 rounded-full border border-slate-200 bg-white px-3.5 py-2.5 text-[15px] font-medium text-slate-700 shadow-sm">
                  <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as FinanceStatusFilter)} className="bg-transparent outline-none">
                    <option value="ALL">全部账单状态</option>
                    <option value="PAID">已支付待履约 / 待确认</option>
                    <option value="COMPLETED">已完成</option>
                    <option value="REFUNDED">已退款</option>
                  </select>
                </label>
                <label className="inline-flex items-center gap-1.5 rounded-full border border-slate-200 bg-white px-3.5 py-2.5 text-[15px] font-medium text-slate-700 shadow-sm">
                  <select value={rangeFilter} onChange={(event) => setRangeFilter(event.target.value as FinanceRangeFilter)} className="bg-transparent outline-none">
                    <option value="ALL">全部时间</option>
                    <option value="30D">近 30 天</option>
                    <option value="90D">近 90 天</option>
                  </select>
                </label>
              </div>
            </div>

            <div className="mt-6 space-y-3">
              {billRecords.length ? billRecords.map((order) => {
                const financeStatus = getFinanceOrderStatus(order);
                return (
                  <div key={order.orderNo} className="rounded-[1.5rem] border border-slate-200 bg-white px-5 py-5 shadow-sm">
                    <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                      <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-2">
                          <div className="text-[18px] font-bold text-slate-900">{order.counterpartDisplayName || `学生 #${order.counterpartUserId}`}</div>
                          <span className={joinClasses("rounded-full border px-3 py-1.5 text-[13px] font-semibold", financeStatus.className)}>
                            {financeStatus.label}
                          </span>
                        </div>
                        <div className="mt-2 text-[15px] leading-7 text-slate-600">
                          {order.questionText?.trim() || "学生暂未补充问题摘要。"}
                        </div>
                        <div className="mt-3 flex flex-wrap items-center gap-3 text-[13px] leading-6 text-slate-400">
                          <span>订单号 {order.orderNo}</span>
                          <span>支付方式 {getPaymentModeLabel(order.paymentMode)}</span>
                          <span>{getServiceTypeLabel(order)}</span>
                          <span>创建于 {formatDateTime(order.createdAt)}</span>
                          {order.paidAt ? <span>支付于 {formatDateTime(order.paidAt)}</span> : null}
                          {order.closedAt ? <span>完成/关闭于 {formatDateTime(order.closedAt)}</span> : null}
                        </div>
                      </div>
                      <div className="shrink-0 text-right">
                        <div className="text-[1.75rem] font-black text-slate-900">{formatMoneyFen(order.amountFen)}</div>
                        <div className="mt-3 flex flex-col items-end gap-2">
                          <button
                            type="button"
                            onClick={() => setSelectedBill(order)}
                            className="inline-flex items-center gap-1 text-[15px] font-semibold text-slate-600 transition-colors hover:text-indigo-700"
                          >
                            查看账单详情
                            <Eye size={14} />
                          </button>
                          <Link
                            to={`/mentor/orders/${encodeURIComponent(order.orderNo)}/workspace`}
                            className="inline-flex items-center gap-1 text-[15px] font-semibold text-indigo-600 transition-colors hover:text-indigo-800"
                          >
                            进入履约区
                            <ArrowUpRight size={14} />
                          </Link>
                        </div>
                      </div>
                    </div>
                  </div>
                );
              }) : (
                <div className="rounded-[1.5rem] border border-dashed border-slate-200 bg-slate-50 px-5 py-8 text-center text-[15px] leading-7 text-slate-500">
                  当前筛选条件下没有账单记录。
                </div>
              )}
            </div>
            <PaginationControls
              page={billPage}
              totalPages={billTotalPages}
              totalItems={overview?.bills.total ?? 0}
              pageSize={BILL_PAGE_SIZE}
              label="账单"
              onPageChange={setBillPage}
            />
          </motion.section>
        </motion.div>
      </main>

      <AnimatePresence>
        {selectedBill ? (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-50 bg-slate-900/35 backdrop-blur-[2px]"
          >
            <div className="absolute inset-y-0 right-0 flex w-full max-w-2xl">
              <motion.aside
                initial={{ x: 420 }}
                animate={{ x: 0 }}
                exit={{ x: 420 }}
                transition={{ type: "spring", stiffness: 260, damping: 28 }}
                className="ml-auto flex h-full w-full max-w-2xl flex-col overflow-hidden border-l border-white/60 bg-white shadow-[0_24px_80px_rgba(15,23,42,0.18)]"
              >
                <div className="border-b border-slate-100 px-6 py-5">
                  <div className="flex items-start justify-between gap-4">
                    <div>
                      <h2 className="text-[1.9rem] font-bold leading-tight text-slate-950">{selectedBill.orderNo}</h2>
                      <div className="mt-3 flex flex-wrap items-center gap-2">
                        {selectedBillStatus ? (
                          <span className={joinClasses("rounded-full border px-3 py-1.5 text-[13px] font-semibold", selectedBillStatus.className)}>
                            {selectedBillStatus.label}
                          </span>
                        ) : null}
                        <span className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1.5 text-[13px] font-semibold text-slate-600">
                          {getServiceTypeLabel(selectedBillDetail ?? selectedBill)}
                        </span>
                        <span className="rounded-full border border-slate-200 bg-white px-3 py-1.5 text-[13px] text-slate-500">
                          {selectedBill.counterpartDisplayName || `学生 #${selectedBill.counterpartUserId}`}
                        </span>
                      </div>
                    </div>
                    <button
                      type="button"
                      onClick={() => setSelectedBill(null)}
                      className="inline-flex h-10 w-10 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-500 transition-colors hover:border-slate-300 hover:text-slate-900"
                    >
                      <X size={18} />
                    </button>
                  </div>
                </div>

                <div className="flex-1 space-y-5 overflow-y-auto px-6 py-6">
                  {billDetailError ? (
                    <div className="rounded-[1.25rem] border border-rose-200 bg-rose-50 px-4 py-4 text-[15px] leading-7 text-rose-700">
                      {billDetailError}
                    </div>
                  ) : null}

                  {billDetailLoading && !selectedBillDetail ? (
                    <div className="space-y-4">
                      <div className="h-28 rounded-[1.5rem] bg-slate-100" />
                      <div className="h-40 rounded-[1.5rem] bg-slate-100" />
                      <div className="h-48 rounded-[1.5rem] bg-slate-100" />
                    </div>
                  ) : (
                    <>
                      <section className="rounded-[1.5rem] border border-slate-200 bg-slate-50/80 p-5">
                        <div className="flex items-center justify-between gap-4">
                          <div>
                            <div className="text-sm font-semibold text-slate-500">账单金额</div>
                            <div className="mt-1.5 text-[2.2rem] font-black text-slate-900">{formatMoneyFen(selectedBill.amountFen)}</div>
                          </div>
                          <div className="text-right text-[15px] text-slate-500">
                            <div>支付方式</div>
                            <div className="mt-1 text-base font-semibold text-slate-800">{getPaymentModeLabel(selectedBillDetail?.paymentMode ?? selectedBill.paymentMode)}</div>
                          </div>
                        </div>
                      </section>

                      <section className="grid gap-4 md:grid-cols-2">
                        <div className="rounded-[1.5rem] border border-slate-200 bg-white p-5">
                          <div className="text-base font-bold text-slate-900">订单上下文</div>
                          <div className="mt-4 space-y-3 text-[15px] text-slate-600">
                            <div>
                              <div className="text-[13px] text-slate-400">服务方式</div>
                              <div className="mt-1 text-[15px] font-semibold text-slate-800">{getServiceTypeLabel(selectedBillDetail ?? selectedBill)}</div>
                            </div>
                            <div>
                              <div className="text-[13px] text-slate-400">订单来源</div>
                              <div className="mt-1 text-[15px] font-semibold text-slate-800">{getSourceLabel(selectedBillDetail?.sourcePage)}</div>
                            </div>
                            <div>
                              <div className="text-[13px] text-slate-400">场景标签</div>
                              <div className="mt-1 text-[15px] font-semibold text-slate-800">{selectedBillDetail?.prepSheetSnapshot?.scene || selectedBillDetail?.sceneCode || getServiceTypeLabel(selectedBillDetail ?? selectedBill)}</div>
                            </div>
                            <div>
                              <div className="text-[13px] text-slate-400">预约信息</div>
                              <div className="mt-1 text-[15px] font-semibold text-slate-800">{formatAppointmentSummary(selectedBillDetail ?? selectedBill)}</div>
                            </div>
                          </div>
                        </div>

                        <div className="rounded-[1.5rem] border border-slate-200 bg-white p-5">
                          <div className="text-base font-bold text-slate-900">关键时间</div>
                          <div className="mt-4 space-y-3 text-[15px] text-slate-600">
                            <div>
                              <div className="text-[13px] text-slate-400">创建时间</div>
                              <div className="mt-1 text-[15px] font-semibold text-slate-800">{formatDateTime(selectedBillDetail?.createdAt ?? selectedBill.createdAt)}</div>
                            </div>
                            <div>
                              <div className="text-[13px] text-slate-400">支付时间</div>
                              <div className="mt-1 text-[15px] font-semibold text-slate-800">{formatDateTime(selectedBillDetail?.paidAt ?? selectedBill.paidAt)}</div>
                            </div>
                            <div>
                              <div className="text-[13px] text-slate-400">完成 / 关闭时间</div>
                              <div className="mt-1 text-[15px] font-semibold text-slate-800">{formatDateTime(selectedBillDetail?.closedAt ?? selectedBill.closedAt)}</div>
                            </div>
                            <div>
                              <div className="text-[13px] text-slate-400">导师回复截止</div>
                              <div className="mt-1 text-[15px] font-semibold text-slate-800">{selectedBillDetail?.mentorReplyDeadlineAt ? formatDateTime(selectedBillDetail.mentorReplyDeadlineAt) : "当前无额外截止提醒"}</div>
                            </div>
                          </div>
                        </div>
                      </section>

                      <section className="rounded-[1.5rem] border border-slate-200 bg-white p-5">
                        <div className="flex items-center gap-2 text-base font-bold text-slate-900">
                          <FileText size={14} />
                          问题与结果摘要
                        </div>
                        <div className="mt-4 rounded-[1rem] border border-slate-100 bg-slate-50 p-4 text-[15px] leading-8 text-slate-700">
                          {selectedBillDetail?.questionText?.trim() || selectedBill.questionText?.trim() || "当前没有同步到更完整的问题原文。"}
                        </div>
                        <div className="mt-4 grid gap-4 md:grid-cols-2">
                          <div className="rounded-[1rem] border border-slate-100 bg-white p-4">
                            <div className="text-[13px] font-semibold text-slate-500">结构化主问题</div>
                            <div className="mt-2 text-[15px] leading-7 text-slate-700">
                              {selectedBillDetail?.questionPayload?.primaryConcern || selectedBillDetail?.problemSummary || "当前没有补充更细的结构化问题。"}
                            </div>
                          </div>
                          <div className="rounded-[1rem] border border-slate-100 bg-white p-4">
                            <div className="text-[13px] font-semibold text-slate-500">附件与结果</div>
                            <div className="mt-2 text-[15px] leading-7 text-slate-700">
                              {selectedBillDetail?.attachmentsSummary?.currentAttachmentCount
                                ? `当前有效附件 ${selectedBillDetail.attachmentsSummary.currentAttachmentCount} 份`
                                : "当前没有附件摘要"}
                            </div>
                            <div className="mt-2 flex flex-wrap gap-2">
                              {detailExpectedOutcomes.length ? detailExpectedOutcomes.map((item) => (
                                <span key={item} className="rounded-full border border-indigo-100 bg-indigo-50 px-3 py-1.5 text-[13px] font-semibold text-indigo-700">
                                  {item}
                                </span>
                              )) : (
                                <span className="text-[13px] text-slate-500">当前没有额外结果目标</span>
                              )}
                            </div>
                          </div>
                        </div>
                      </section>

                      <section className="grid gap-4 md:grid-cols-[minmax(0,1.1fr)_minmax(0,0.9fr)]">
                        <div className="rounded-[1.5rem] border border-slate-200 bg-white p-5">
                          <div className="text-base font-bold text-slate-900">订单时间线</div>
                          <div className="relative mt-5 pl-2">
                            <div className="absolute bottom-2 left-[11px] top-2 w-px bg-slate-100" />
                            <div className="space-y-5">
                              {billTimeline.map((step) => (
                                <div key={step.id} className="relative flex items-start gap-3">
                                  <div className={joinClasses(
                                    "relative z-10 flex h-5 w-5 shrink-0 items-center justify-center rounded-full border-2 bg-white",
                                    step.completed ? "border-emerald-500 text-emerald-500" : step.active ? "border-indigo-500 text-indigo-500" : "border-slate-200 text-slate-300",
                                  )}>
                                    {step.completed ? (
                                      <CheckCircle2 size={11} strokeWidth={3} />
                                    ) : step.active ? (
                                      <Clock3 size={11} />
                                    ) : null}
                                  </div>
                                  <div className="pt-0.5">
                                    <div className={joinClasses("text-[15px] font-semibold", step.active || step.completed ? "text-slate-900" : "text-slate-400")}>{step.title}</div>
                                    <div className="mt-1 text-[13px] text-slate-500">{step.time}</div>
                                  </div>
                                </div>
                              ))}
                            </div>
                          </div>
                        </div>

                        <div className="space-y-4">
                          <section className="rounded-[1.5rem] border border-slate-200 bg-white p-5">
                            <div className="text-base font-bold text-slate-900">学生评价摘要</div>
                            {selectedBillDetail?.review ? (
                              <div className="mt-4 space-y-2">
                                <div className="text-[15px] font-semibold text-amber-600">{selectedBillDetail.review.rating} / 5 分</div>
                                <div className="text-[15px] leading-7 text-slate-700">
                                  {selectedBillDetail.review.comment?.trim() || "学生未留下文字评价。"}
                                </div>
                                <div className="text-[13px] text-slate-500">评价时间 {formatDateTime(selectedBillDetail.review.createdAt)}</div>
                              </div>
                            ) : (
                              <div className="mt-4 text-[15px] leading-7 text-slate-500">当前账单对应订单还没有学生评价记录。</div>
                            )}
                          </section>

                          <section className="rounded-[1.5rem] border border-slate-200 bg-white p-5">
                            <div className="text-base font-bold text-slate-900">退款 / 售后摘要</div>
                            {detailLatestAfterSales ? (
                              <div className="mt-4 space-y-3">
                                <span className="inline-flex rounded-full border border-rose-200 bg-rose-50 px-3 py-1.5 text-[13px] font-bold text-rose-700">
                                  {getAfterSalesStatusLabel(detailLatestAfterSales.status)}
                                </span>
                                <div className="text-[15px] leading-7 text-slate-700">{detailLatestAfterSales.reason}</div>
                                {detailLatestAfterSales.reviewNote ? (
                                  <div className="rounded-xl border border-rose-100 bg-rose-50/70 px-3 py-2 text-[13px] leading-6 text-rose-700">
                                    审核备注：{detailLatestAfterSales.reviewNote}
                                  </div>
                                ) : null}
                                <div className="text-[13px] text-slate-500">
                                  发起 {formatDateTime(detailLatestAfterSales.createdAt)}
                                  {detailLatestAfterSales.reviewedAt ? ` · 处理 ${formatDateTime(detailLatestAfterSales.reviewedAt)}` : ""}
                                </div>
                              </div>
                            ) : selectedBill.status === "REFUNDED" ? (
                              <div className="mt-4 text-[15px] leading-7 text-slate-500">当前订单已进入退款口径，但还没有同步到更完整的售后明细。</div>
                            ) : (
                              <div className="mt-4 text-[15px] leading-7 text-slate-500">当前账单对应订单暂无售后记录。</div>
                            )}
                          </section>
                        </div>
                      </section>
                    </>
                  )}
                </div>

                <div className="border-t border-slate-100 px-6 py-4">
                  <div className="flex flex-wrap justify-end gap-3">
                    <button
                      type="button"
                      onClick={() => setSelectedBill(null)}
                      className="inline-flex items-center justify-center rounded-full border border-slate-200 bg-white px-4 py-3 text-[15px] font-semibold text-slate-600 transition-colors hover:border-slate-300 hover:text-slate-900"
                    >
                      关闭
                    </button>
                    <Link
                      to={`/mentor/orders?orderNo=${encodeURIComponent(selectedBill.orderNo)}`}
                      className="inline-flex items-center justify-center rounded-full border border-slate-200 bg-white px-4 py-3 text-[15px] font-semibold text-slate-700 transition-colors hover:border-slate-300 hover:text-slate-900"
                    >
                      回订单中心查看
                    </Link>
                    <Link
                      to={`/mentor/orders/${encodeURIComponent(selectedBill.orderNo)}/workspace`}
                      className="inline-flex items-center justify-center rounded-full bg-slate-900 px-5 py-3 text-[15px] font-semibold text-white transition-colors hover:bg-slate-800"
                    >
                      前往履约工作区
                      <ArrowUpRight size={14} className="ml-2" />
                    </Link>
                  </div>
                </div>
              </motion.aside>
            </div>
          </motion.div>
        ) : null}
      </AnimatePresence>
    </div>
  );
}
