import { type ReactNode, lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  Alert,
  Button,
  Form,
  Input,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
  type TableColumnsType,
} from "antd";
import {
  ArrowRight,
  CheckCircle2,
  Download,
  Eye,
  Flag,
  History,
  ListChecks,
  RefreshCcw,
  Search,
  ShieldAlert,
  Trash2,
  Type,
  Upload,
} from "lucide-react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { AdminAnimatedNumber, canAnimateAdminValue } from "../components/admin/AdminAnimatedNumber";
import { useLatestRequest } from "../hooks/useLatestRequest";
import { buildAdminStaleCacheKey, useAdminStaleCache } from "../hooks/useAdminStaleCache";
import { ApiClientError, apiRequest, buildQuery, isAbortError } from "../lib/apiClient";
import {
  auditActionLabelMap,
  getLabel,
  getReadableCodeLabel,
  gatewayLogStatusLabelMap,
  gatewayTaskTypeLabelMap,
  moderationActionLabelMap,
  moderationReasonLabelMap,
  riskLevelLabelMap,
  sensitiveTermTypeLabelMap,
  sourceTypeLabelMap,
  targetTypeLabelMap,
} from "../lib/adminLabels";
import { formatCount, formatDateTime } from "../lib/formatters";
import {
  AdminDetailPlaceholder,
  AdminFilterBar,
  AdminMiniStat,
  AdminPageFrame,
  AdminPageHeader,
  AdminSettingSwitch,
  AdminSurfaceCard,
  joinAdminClassNames,
} from "../components/admin/AdminOpsPrimitives";
import "./AdminContentModerationPage.css";

const { Search: SearchInput, TextArea } = Input;
const { Paragraph, Text } = Typography;

const AdminContentAiTraceModal = lazy(() => import("../components/admin/overlays/AdminContentAiTraceModal"));
const AdminContentModerationOverlays = lazy(() => import("../components/admin/overlays/AdminContentModerationOverlays"));

const contentTabKeys = ["home", "reports", "review", "audit", "terms"] as const;
type ContentTabKey = typeof contentTabKeys[number];

const contentTablePaginationLocale = {
  items_per_page: "条/页",
  jump_to: "跳至",
  jump_to_confirm: "确定",
  page: "页",
  prev_page: "上一页",
  next_page: "下一页",
  prev_5: "向前 5 页",
  next_5: "向后 5 页",
  prev_3: "向前 3 页",
  next_3: "向后 3 页",
} as const;

type ContentAccentTone = "indigo" | "amber" | "emerald" | "rose" | "violet" | "slate";

const contentAccentClassMap: Record<ContentAccentTone, {
  badge: string;
  icon: string;
  value: string;
}> = {
  indigo: {
    badge: "bg-indigo-100 text-indigo-700",
    icon: "bg-indigo-100 text-indigo-600",
    value: "text-indigo-700",
  },
  amber: {
    badge: "bg-amber-100 text-amber-700",
    icon: "bg-amber-100 text-amber-600",
    value: "text-amber-700",
  },
  emerald: {
    badge: "bg-emerald-100 text-emerald-700",
    icon: "bg-emerald-100 text-emerald-600",
    value: "text-emerald-700",
  },
  rose: {
    badge: "bg-rose-100 text-rose-700",
    icon: "bg-rose-100 text-rose-600",
    value: "text-rose-700",
  },
  violet: {
    badge: "bg-violet-100 text-violet-700",
    icon: "bg-violet-100 text-violet-600",
    value: "text-violet-700",
  },
  slate: {
    badge: "bg-slate-200 text-slate-700",
    icon: "bg-slate-200 text-slate-700",
    value: "text-slate-700",
  },
};

function renderModerationMetricValue(value: ReactNode) {
  if (canAnimateAdminValue(value)) {
    return <AdminAnimatedNumber value={value} />;
  }
  return value;
}

function ModerationWorkspaceStatCard({
  label,
  value,
  note,
  icon: Icon,
  tone,
  valueClassName,
}: {
  label: string;
  value: ReactNode;
  note: ReactNode;
  icon: typeof Flag;
  tone: ContentAccentTone;
  valueClassName?: string;
}) {
  const palette = contentAccentClassMap[tone];

  return (
    <div className={joinAdminClassNames("admin-content-workspace-stat admin-content-workspace-stat--metric", `admin-content-workspace-stat--${tone}`)}>
      <div className="admin-content-workspace-stat__head">
        <span className={joinAdminClassNames("admin-content-workspace-stat__icon", palette.icon)}>
          <Icon size={20} />
        </span>
        <div className="admin-typography-card-title min-w-0 text-slate-700">{label}</div>
      </div>
      <div className={joinAdminClassNames("admin-content-workspace-stat__value mt-5", palette.value, valueClassName)}>
        {renderModerationMetricValue(value)}
      </div>
      <div className="admin-typography-card-note mt-2 text-slate-500">{note}</div>
    </div>
  );
}

function ModerationReminderCard({
  title,
  description,
  icon: Icon,
  tone,
}: {
  title: string;
  description: ReactNode;
  icon: typeof Flag;
  tone: ContentAccentTone;
}) {
  const palette = contentAccentClassMap[tone];

  return (
    <div className={joinAdminClassNames("admin-content-callout-card", `admin-content-callout-card--${tone}`)}>
      <div className="admin-content-callout-card__head">
        <span className={joinAdminClassNames("admin-content-callout-card__icon", palette.icon)}>
          <Icon size={20} />
        </span>
        <div className="admin-typography-card-title min-w-0 text-slate-800">{title}</div>
      </div>
      <div className="admin-typography-card-note mt-4 text-slate-600">{description}</div>
    </div>
  );
}

function normalizeContentTab(value: string | null, traceId?: string | null): ContentTabKey {
  if (traceId?.trim()) {
    return "audit";
  }
  if (value === "home" || value === "overview") {
    return "home";
  }
  if (value === "settings" || value === "terms") {
    return "terms";
  }
  if (value && contentTabKeys.includes(value as ContentTabKey)) {
    return value as ContentTabKey;
  }
  return "home";
}

function getAdminUserTargetId(targetType: string | null | undefined, targetId: string | null | undefined) {
  if (targetType !== "USER" || !targetId) {
    return null;
  }
  const numericValue = Number(targetId);
  return Number.isInteger(numericValue) ? numericValue : null;
}

const reportStatusOptions = [
  { label: "状态：全部", value: "" },
  { label: "待处理", value: "PENDING" },
  { label: "已采纳", value: "ACCEPTED" },
  { label: "已驳回", value: "REJECTED" },
  { label: "已关闭", value: "CLOSED" },
];

const targetTypeOptions = [
  { label: "目标：全部", value: "" },
  { label: "帖子", value: "POST" },
  { label: "评论", value: "COMMENT" },
  { label: "用户", value: "USER" },
];

const reviewSourceOptions = [
  { label: "来源：全部", value: "" },
  { label: "社区帖子", value: "COMMUNITY_POST" },
  { label: "社区评论", value: "COMMUNITY_COMMENT" },
  { label: "智能生成内容", value: "AI_OUTPUT" },
];

const reportDecisionOptions = [
  { label: "采纳举报", value: "ACCEPTED" },
  { label: "驳回举报", value: "REJECTED" },
  { label: "关闭工单", value: "CLOSED" },
];

const reportActionOptions = [
  { label: "下架内容", value: "TAKE_DOWN" },
  { label: "恢复内容", value: "RESTORE" },
  { label: "不做动作", value: "NO_ACTION" },
];

const reviewDecisionOptions = [
  { label: "通过", value: "APPROVE" },
  { label: "驳回", value: "REJECT" },
];

const sensitiveRiskOptions = [
  { label: "极高风险", value: "CRITICAL" },
  { label: "高风险", value: "HIGH" },
  { label: "中风险", value: "MEDIUM" },
  { label: "低风险", value: "LOW" },
];

const sensitiveActionOptions = [
  { label: "放行", value: "PASS" },
  { label: "脱敏", value: "MASK" },
  { label: "拦截", value: "BLOCK" },
  { label: "转人工审核", value: "REVIEW" },
];

const termStatusOptions = [
  { label: "状态：全部", value: "" },
  { label: "启用中", value: "enabled" },
  { label: "已停用", value: "disabled" },
  { label: "白名单", value: "whitelist" },
];

const sourceScopeOptions = [
  { label: "帖子", value: "COMMUNITY_POST" },
  { label: "评论", value: "COMMUNITY_COMMENT" },
  { label: "智能输入内容", value: "AI_INPUT" },
  { label: "智能生成内容", value: "AI_OUTPUT" },
  { label: "全局", value: "ALL" },
];

const termTypeOptions = [
  { label: "政治", value: "POLITICS" },
  { label: "色情", value: "PORNOGRAPHY" },
  { label: "恐怖", value: "TERROR" },
  { label: "暴力", value: "VIOLENCE" },
  { label: "欺诈", value: "FRAUD" },
  { label: "辱骂", value: "ABUSE" },
  { label: "广告", value: "ADVERTISEMENT" },
  { label: "违规", value: "ILLEGAL" },
  { label: "其他", value: "OTHER" },
];

type ReportItem = {
  reportId: number;
  targetType: "POST" | "COMMENT" | "USER";
  targetId: string;
  contentPostId: string | null;
  contentTitle: string | null;
  contentBody: string | null;
  reasonCode: string;
  status: string;
  latestAction: string | null;
  reportCount: number;
  createdAt: string;
  updatedAt: string;
};

type ReportListResponse = {
  records: ReportItem[];
  total: number;
};

type ReportListCachePayload = {
  records: ReportItem[];
  total: number;
};

type ReportActionItem = {
  actionId: number;
  operatorUserId: number;
  operatorDisplayName: string;
  decision: string;
  action: string;
  comment: string | null;
  createdAt: string;
};

type ReportActionHistoryResponse = {
  records: ReportActionItem[];
};

type ReportDetailResponse = {
  reportId: number;
  reporterUserId: number;
  reporterDisplayName: string | null;
  targetType: "POST" | "COMMENT" | "USER";
  targetId: string;
  contentPostId: string | null;
  contentTitle: string | null;
  contentBody: string | null;
  reasonCode: string;
  reportDetail: string | null;
  status: string;
  latestAction: string | null;
  reportCount: number;
  targetStatus: string | null;
  targetRiskLevel: string | null;
  createdAt: string;
  updatedAt: string;
};

type ReviewQueueItem = {
  itemId: number;
  sourceType: string;
  targetType: string;
  targetId: string;
  riskLevel: string;
  reasonCode: string;
  preview: string;
  createdAt: string;
};

type ReviewQueueResponse = {
  records: ReviewQueueItem[];
  total: number;
};

type ReviewQueueCachePayload = {
  records: ReviewQueueItem[];
  total: number;
};

type ReviewQueueDetailResponse = {
  itemId: number;
  sourceType: string;
  targetType: string;
  targetId: string;
  riskLevel: string;
  reasonCode: string;
  preview: string;
  contentTitle: string | null;
  contentBody: string | null;
  postId: string | null;
  postTitle: string | null;
  postBody: string | null;
  authorUserId: number | null;
  authorDisplayName: string | null;
  authorRole: string | null;
  createdAt: string;
};

type AuditLogItem = {
  id: number;
  traceId: string;
  operatorUserId: number;
  operatorDisplayName: string;
  actionType: string;
  targetType: string;
  targetId: string;
  detailJson: string;
  createdAt: string;
};

type AuditLogListResponse = {
  records: AuditLogItem[];
  total: number;
};

type AuditLogCachePayload = {
  records: AuditLogItem[];
  total: number;
};

type AiTraceLogItem = {
  id: number;
  traceId: string;
  userId: number;
  userEmail: string | null;
  userDisplayName: string | null;
  taskType: string;
  provider: string;
  model: string;
  status: string;
  errorCode: string | null;
  latencyMs: number;
  totalTokens: number;
  thoughtsTokens: number;
  estimatedCost: string;
  resultSummary: string | null;
  createdAt: string | null;
};

type AiTraceLogListResponse = {
  records: AiTraceLogItem[];
  total: number;
};

type AiTraceLogDetailResponse = AiTraceLogItem & {
  resultPayloadJson: string | null;
  governanceTraceSummary: {
    auditCount: number;
    recentActionTypes: string[];
    latestAuditAt: string | null;
  };
};

type SensitiveTermItem = {
  termId: number;
  term: string;
  termType: string;
  riskLevel: string;
  action: string;
  sourceScope: string;
  whitelist: boolean;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
};

type SensitiveTermFormValues = Pick<SensitiveTermItem, "term" | "termType" | "riskLevel" | "action" | "sourceScope" | "whitelist" | "enabled">;
type SensitiveTermImportConfigValues = Pick<SensitiveTermItem, "termType" | "riskLevel" | "action" | "sourceScope" | "whitelist" | "enabled">;

const DEFAULT_SENSITIVE_TERM_FORM_VALUES: SensitiveTermFormValues = {
  term: "",
  termType: "OTHER",
  riskLevel: "HIGH",
  action: "BLOCK",
  sourceScope: "COMMUNITY_POST",
  whitelist: false,
  enabled: true,
};

const DEFAULT_SENSITIVE_TERM_IMPORT_CONFIG_VALUES: SensitiveTermImportConfigValues = {
  termType: "OTHER",
  riskLevel: "HIGH",
  action: "BLOCK",
  sourceScope: "COMMUNITY_POST",
  whitelist: false,
  enabled: true,
};

type SensitiveTermsResponse = {
  records: SensitiveTermItem[];
  total: number;
  page: number;
  size: number;
  summary: {
    total: number;
    enabled: number;
    whitelist: number;
    categories: number;
  };
};

type SensitiveTermsCachePayload = {
  records: SensitiveTermItem[];
  total: number;
  summary: SensitiveTermsResponse["summary"];
};

type SensitiveTermBatchOperationResponse = {
  affectedCount: number;
};

type SensitiveTermImportResponse = {
  totalRows: number;
  createdCount: number;
  updatedCount: number;
  skippedCount: number;
  errors: string[];
};

function getRiskTagColor(level: string) {
  if (level === "CRITICAL") {
    return "volcano";
  }
  if (level === "HIGH") {
    return "error";
  }
  if (level === "MEDIUM") {
    return "warning";
  }
  return "default";
}

function getReportStatusTag(status: string) {
  if (status === "PENDING") {
    return <Tag color="processing">待处理</Tag>;
  }
  if (status === "ACCEPTED") {
    return <Tag color="success">已采纳</Tag>;
  }
  if (status === "REJECTED") {
    return <Tag>已驳回</Tag>;
  }
  if (status === "CLOSED") {
    return <Tag color="warning">已关闭</Tag>;
  }
  return <Tag>{status}</Tag>;
}

function getTargetTypeTag(targetType: string) {
  return (
    <Tag color={targetType === "POST" ? "blue" : targetType === "COMMENT" ? "cyan" : "purple"}>
      {getReadableCodeLabel(targetType, targetTypeLabelMap, targetType || "未知")}
    </Tag>
  );
}

function getDisplayText(value: string | null | undefined, fallback = "—") {
  return value && value.trim() ? value : fallback;
}

function getCommunityContextHref(
  targetType: string | null | undefined,
  targetId: string | null | undefined,
  contentPostId: string | null | undefined,
) {
  const normalizedTargetId = targetId?.trim();
  const normalizedPostId = contentPostId?.trim();

  if (targetType === "POST" && normalizedTargetId) {
    return `/community/${encodeURIComponent(normalizedTargetId)}`;
  }

  if (targetType === "COMMENT" && normalizedTargetId && normalizedPostId) {
    return `/community/${encodeURIComponent(normalizedPostId)}#comment-${encodeURIComponent(normalizedTargetId)}`;
  }

  return null;
}

function getReportContextActionLabel(targetType: string | null | undefined) {
  if (targetType === "POST") {
    return "打开原帖全文";
  }
  if (targetType === "COMMENT") {
    return "打开所属帖子并定位评论";
  }
  return "查看内容上下文";
}

function getReportContextHint(
  targetType: string | null | undefined,
  targetId: string | null | undefined,
  contentPostId: string | null | undefined,
) {
  if (targetType === "POST" && targetId?.trim()) {
    return `举报目标为帖子 #${targetId.trim()}，可直接打开前台讨论详情核对原文。`;
  }
  if (targetType === "COMMENT" && targetId?.trim() && contentPostId?.trim()) {
    return `举报目标为评论 #${targetId.trim()}，将打开所属帖子 #${contentPostId.trim()} 并定位到对应回复。`;
  }
  if (targetType === "USER") {
    return "举报目标为用户账号，建议结合举报说明与用户详情一并判断。";
  }
  return "该工单未返回可直接打开的社区上下文编号，可先依据正文摘要和举报说明处理。";
}

function getResponseFilename(response: Response, fallback: string) {
  const contentDisposition = response.headers.get("content-disposition") || "";
  const matched = contentDisposition.match(/filename="?([^";]+)"?/i);
  return matched?.[1] ?? fallback;
}

function safePrettifyJson(value: string) {
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

const auditDetailFieldLabelMap: Record<string, string> = {
  reportId: "举报单号",
  decision: "处理决定",
  action: "处理动作",
  comment: "处理备注",
  orderNo: "订单号",
  requestType: "售后类型",
  reason: "申请原因",
  autoTriggered: "触发方式",
  status: "结果状态",
  reviewNote: "审核备注",
  refundReason: "退款说明",
  gatewayCode: "网关编码",
  gatewayMessage: "网关提示",
  subCode: "子编码",
  subMessage: "子提示",
  tradeStatus: "交易状态",
  providerTradeNo: "交易流水号",
  syncedToPaid: "支付状态同步",
  alreadyClosed: "是否已关单",
  errorCode: "错误码",
  errorMessage: "错误信息",
  amountFen: "金额",
  slotReleased: "预约释放",
  outRequestNo: "退款请求号",
};

const auditDetailStatusLabelMap: Record<string, string> = {
  ...moderationActionLabelMap,
  ...gatewayLogStatusLabelMap,
  PENDING: "待处理",
  APPROVED: "已通过",
  REJECTED: "已拒绝",
  REFUND_SUCCESS: "退款成功",
  TRADE_SUCCESS: "交易成功",
  TRADE_CLOSED: "交易关闭",
  WAIT_BUYER_PAY: "等待支付",
};

const auditDetailRequestTypeLabelMap: Record<string, string> = {
  REFUND: "退款",
};

const auditDetailBooleanLabelMap: Partial<Record<string, { trueLabel: string; falseLabel: string }>> = {
  autoTriggered: { trueLabel: "系统自动触发", falseLabel: "人工发起" },
  syncedToPaid: { trueLabel: "已同步为支付成功", falseLabel: "未同步支付状态" },
  alreadyClosed: { trueLabel: "是", falseLabel: "否" },
  slotReleased: { trueLabel: "已释放", falseLabel: "未释放" },
};

const auditDetailLongTextKeys = new Set([
  "comment",
  "reason",
  "reviewNote",
  "refundReason",
  "gatewayMessage",
  "subMessage",
  "errorMessage",
]);

function parseAuditDetailObject(value: string): Record<string, unknown> | null {
  try {
    const parsed = JSON.parse(value);
    if (!parsed || Array.isArray(parsed) || typeof parsed !== "object") {
      return null;
    }
    return parsed as Record<string, unknown>;
  } catch {
    return null;
  }
}

function formatAuditDetailValue(key: string, value: unknown): ReactNode {
  if (value === null || value === undefined || value === "") {
    return "—";
  }

  if (typeof value === "boolean") {
    const labelPair = auditDetailBooleanLabelMap[key];
    return labelPair ? (value ? labelPair.trueLabel : labelPair.falseLabel) : (value ? "是" : "否");
  }

  if (typeof value === "number") {
    if (key === "amountFen") {
      return `${formatCount(value)} 分`;
    }
    return formatCount(value);
  }

  if (typeof value === "string") {
    if (key === "decision" || key === "action") {
      return getReadableCodeLabel(value, moderationActionLabelMap, value);
    }
    if (key === "requestType") {
      return getReadableCodeLabel(value, auditDetailRequestTypeLabelMap, value);
    }
    if (key === "status" || key === "tradeStatus") {
      return getReadableCodeLabel(value, auditDetailStatusLabelMap, value);
    }
    if (key === "reasonCode") {
      return getReadableCodeLabel(value, moderationReasonLabelMap, value);
    }
    return value;
  }

  if (Array.isArray(value)) {
    return value.length === 0 ? "—" : value.map((item) => {
      if (typeof item === "string" || typeof item === "number") {
        return String(item);
      }
      return safePrettifyJson(JSON.stringify(item));
    }).join("、");
  }

  return safePrettifyJson(JSON.stringify(value));
}

function formatCny(value: string | null | undefined) {
  if (!value) {
    return "¥0.0000";
  }
  const numericValue = Number(value);
  if (Number.isNaN(numericValue)) {
    return value;
  }
  return `¥${numericValue.toFixed(4)}`;
}

function maskSensitiveTermForTable(term: string) {
  const normalizedTerm = term.trim();
  if (!normalizedTerm) {
    return "—";
  }
  if (normalizedTerm.length === 1) {
    return "*";
  }
  if (normalizedTerm.length === 2) {
    return `${normalizedTerm.slice(0, 1)}*`;
  }
  return `${normalizedTerm.slice(0, 1)}***${normalizedTerm.slice(-1)}`;
}

function getAiLogStatusTag(status: string) {
  const label = getReadableCodeLabel(status, gatewayLogStatusLabelMap, status);
  if (status === "SUCCESS") {
    return <Tag color="success">{label}</Tag>;
  }
  if (status === "FAILED" || status === "ERROR") {
    return <Tag color="error">{label}</Tag>;
  }
  if (status === "TIMEOUT") {
    return <Tag color="warning">{label}</Tag>;
  }
  if (status === "RUNNING") {
    return <Tag color="processing">{label}</Tag>;
  }
  return <Tag>{label}</Tag>;
}

export default function AdminContentModerationPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [reportDecisionForm] = Form.useForm();
  const [reviewDecisionForm] = Form.useForm();
  const [termEditForm] = Form.useForm<SensitiveTermFormValues>();
  const [termImportConfigForm] = Form.useForm<SensitiveTermImportConfigValues>();
  const importInputRef = useRef<HTMLInputElement | null>(null);

  const createReportRequest = useLatestRequest();
  const createReviewRequest = useLatestRequest();
  const createAuditRequest = useLatestRequest();
  const createTermsRequest = useLatestRequest();
  const createAiTraceRequest = useLatestRequest();
  const createAiTraceDetailRequest = useLatestRequest();

  // traceId query 直接进入审计页，便于从 AI 日志、举报和通知链路反查。
  const initialTab = normalizeContentTab(searchParams.get("tab"), searchParams.get("traceId"));
  const [activeTab, setActiveTab] = useState<ContentTabKey>(initialTab);

  const [reportPage, setReportPage] = useState(1);
  const [reportPageSize, setReportPageSize] = useState(10);
  const [reportStatus, setReportStatus] = useState("");
  const [reportTargetType, setReportTargetType] = useState("");
  const [reportKeywordDraft, setReportKeywordDraft] = useState("");
  const [reportKeyword, setReportKeyword] = useState("");

  const [reviewPage, setReviewPage] = useState(1);
  const [reviewPageSize, setReviewPageSize] = useState(10);
  const [reviewSourceType, setReviewSourceType] = useState("");
  const [reviewKeywordDraft, setReviewKeywordDraft] = useState("");
  const [reviewKeyword, setReviewKeyword] = useState("");

  const [auditPage, setAuditPage] = useState(1);
  const [auditPageSize, setAuditPageSize] = useState(10);
  const [auditTargetType, setAuditTargetType] = useState("");
  const [auditActionType, setAuditActionType] = useState("");
  const [auditTargetIdDraft, setAuditTargetIdDraft] = useState(searchParams.get("targetId") ?? "");
  const [auditTargetId, setAuditTargetId] = useState(searchParams.get("targetId") ?? "");
  const [auditTraceIdDraft, setAuditTraceIdDraft] = useState(searchParams.get("traceId") ?? "");
  const [auditTraceId, setAuditTraceId] = useState(searchParams.get("traceId") ?? "");

  const [termPage, setTermPage] = useState(1);
  const [termPageSize, setTermPageSize] = useState(10);
  const [termKeywordDraft, setTermKeywordDraft] = useState("");
  const [termKeyword, setTermKeyword] = useState("");
  const [termTypeFilter, setTermTypeFilter] = useState("");
  const [termRiskFilter, setTermRiskFilter] = useState("");
  const [termStatusFilter, setTermStatusFilter] = useState("");

  // 后台治理页分 tab 缓存，避免举报/待审/审计/词库之间切换时反复白屏。
  const reportCache = useAdminStaleCache<ReportListCachePayload>(
    buildAdminStaleCacheKey("admin-content:reports", {
      reportPage,
      reportPageSize,
      reportStatus,
      reportTargetType,
    }),
  );
  const reviewCache = useAdminStaleCache<ReviewQueueCachePayload>(
    buildAdminStaleCacheKey("admin-content:review-queue", {
      reviewPage,
      reviewPageSize,
      reviewSourceType,
    }),
  );
  const auditCache = useAdminStaleCache<AuditLogCachePayload>(
    buildAdminStaleCacheKey("admin-content:audit-logs", {
      auditPage,
      auditPageSize,
      auditTargetType,
      auditActionType,
      auditTargetId,
      auditTraceId,
    }),
  );
  const termsCache = useAdminStaleCache<SensitiveTermsCachePayload>(
    buildAdminStaleCacheKey("admin-content:sensitive-terms", {
      termPage,
      termPageSize,
      termKeyword,
      termTypeFilter,
      termRiskFilter,
      termStatusFilter,
    }),
  );

  const [reports, setReports] = useState<ReportItem[]>(() => reportCache.cached?.records ?? []);
  const [reportTotal, setReportTotal] = useState(() => reportCache.cached?.total ?? 0);
  const [reportLoading, setReportLoading] = useState(() => !reportCache.hasCache);
  const [reportError, setReportError] = useState<string | null>(null);

  const [reviewItems, setReviewItems] = useState<ReviewQueueItem[]>(() => reviewCache.cached?.records ?? []);
  const [reviewTotal, setReviewTotal] = useState(() => reviewCache.cached?.total ?? 0);
  const [reviewLoading, setReviewLoading] = useState(() => !reviewCache.hasCache);
  const [reviewError, setReviewError] = useState<string | null>(null);

  const [auditLogs, setAuditLogs] = useState<AuditLogItem[]>(() => auditCache.cached?.records ?? []);
  const [auditTotal, setAuditTotal] = useState(() => auditCache.cached?.total ?? 0);
  const [auditLoading, setAuditLoading] = useState(() => !auditCache.hasCache && activeTab === "audit");
  const [auditError, setAuditError] = useState<string | null>(null);

  const [sensitiveTerms, setSensitiveTerms] = useState<SensitiveTermItem[]>(() => termsCache.cached?.records ?? []);
  const [termTotal, setTermTotal] = useState(() => termsCache.cached?.total ?? 0);
  const [termSummary, setTermSummary] = useState<SensitiveTermsResponse["summary"]>(() => termsCache.cached?.summary ?? {
    total: 0,
    enabled: 0,
    whitelist: 0,
    categories: 0,
  });
  const [termsLoading, setTermsLoading] = useState(() => !termsCache.hasCache);
  const [termsError, setTermsError] = useState<string | null>(null);
  const [selectedTermIds, setSelectedTermIds] = useState<number[]>([]);
  const [focusedTermId, setFocusedTermId] = useState<number | null>(null);
  const [editingTerm, setEditingTerm] = useState<SensitiveTermItem | null>(null);
  const [termEditInitialValues, setTermEditInitialValues] = useState<SensitiveTermFormValues>(DEFAULT_SENSITIVE_TERM_FORM_VALUES);
  const [termEditorOpen, setTermEditorOpen] = useState(false);
  const [termSaving, setTermSaving] = useState(false);
  const [termEditSaving, setTermEditSaving] = useState(false);
  const [termDeletingId, setTermDeletingId] = useState<number | null>(null);
  const [termBatchSaving, setTermBatchSaving] = useState(false);
  const [termBatchDeleting, setTermBatchDeleting] = useState(false);
  const [termExporting, setTermExporting] = useState(false);
  const [termImporting, setTermImporting] = useState(false);
  const [termImportConfigInitialValues, setTermImportConfigInitialValues] = useState<SensitiveTermImportConfigValues>(DEFAULT_SENSITIVE_TERM_IMPORT_CONFIG_VALUES);
  const [termImportConfigOpen, setTermImportConfigOpen] = useState(false);
  const [pendingImportFile, setPendingImportFile] = useState<File | null>(null);

  const [selectedReportId, setSelectedReportId] = useState<number | null>(null);
  const [reportDrawerOpen, setReportDrawerOpen] = useState(false);
  const [reportDetail, setReportDetail] = useState<ReportDetailResponse | null>(null);
  const [reportActions, setReportActions] = useState<ReportActionItem[]>([]);
  const [reportDetailLoading, setReportDetailLoading] = useState(false);
  const [reportDecisionOpen, setReportDecisionOpen] = useState(false);
  const [reportDecisionSaving, setReportDecisionSaving] = useState(false);

  const [selectedReviewId, setSelectedReviewId] = useState<number | null>(null);
  const [reviewDrawerOpen, setReviewDrawerOpen] = useState(false);
  const [reviewDetail, setReviewDetail] = useState<ReviewQueueDetailResponse | null>(null);
  const [reviewDetailLoading, setReviewDetailLoading] = useState(false);
  const [reviewDecisionOpen, setReviewDecisionOpen] = useState(false);
  const [reviewDecisionSaving, setReviewDecisionSaving] = useState(false);

  const [selectedAuditId, setSelectedAuditId] = useState<number | null>(null);
  const [auditDrawerOpen, setAuditDrawerOpen] = useState(false);
  const [termDrawerOpen, setTermDrawerOpen] = useState(false);
  const [aiTraceModalOpen, setAiTraceModalOpen] = useState(false);
  const [activeAiTraceId, setActiveAiTraceId] = useState<string | null>(null);
  const [aiTraceLogs, setAiTraceLogs] = useState<AiTraceLogItem[]>([]);
  const [aiTraceTotal, setAiTraceTotal] = useState(0);
  const [aiTraceLoading, setAiTraceLoading] = useState(false);
  const [aiTraceError, setAiTraceError] = useState<string | null>(null);
  const [selectedAiTraceLogId, setSelectedAiTraceLogId] = useState<number | null>(null);
  const [aiTraceDetail, setAiTraceDetail] = useState<AiTraceLogDetailResponse | null>(null);
  const [aiTraceDetailLoading, setAiTraceDetailLoading] = useState(false);
  const [aiTraceDetailError, setAiTraceDetailError] = useState<string | null>(null);

  useEffect(() => {
    setActiveTab(normalizeContentTab(searchParams.get("tab"), searchParams.get("traceId")));
    setAuditTraceIdDraft(searchParams.get("traceId") ?? "");
    setAuditTraceId(searchParams.get("traceId") ?? "");
  }, [searchParams]);

  useEffect(() => {
    setReportDrawerOpen(false);
    setReviewDrawerOpen(false);
    setAuditDrawerOpen(false);
    setTermDrawerOpen(false);
    setTermEditorOpen(false);
    setTermImportConfigOpen(false);
    setPendingImportFile(null);
  }, [activeTab]);

  const loadReports = useCallback(async (showLoading = true) => {
    const request = createReportRequest();
    if (showLoading) {
      setReportLoading(true);
    }
    setReportError(null);
    try {
      // 举报中心由服务端分页过滤，前端关键字只做当前页二次查找。
      const response = await apiRequest<ReportListResponse>(
        `/admin/content/reports${buildQuery({ page: reportPage, size: reportPageSize, status: reportStatus, targetType: reportTargetType })}`,
        { signal: request.signal },
      );
      if (!request.isCurrent()) {
        return;
      }
      setReports(response.records);
      setReportTotal(response.total);
      reportCache.write({
        records: response.records,
        total: response.total,
      });
      setSelectedReportId((current) => {
        if (response.records.length === 0) {
          return null;
        }
        return response.records.some((item) => item.reportId === current) ? current : response.records[0].reportId;
      });
    } catch (error) {
      if (isAbortError(error) || !request.isCurrent()) {
        return;
      }
      const apiError = error as ApiClientError;
      setReportError(apiError.message || "加载举报中心失败");
    } finally {
      if (request.isCurrent()) {
        setReportLoading(false);
      }
    }
  }, [createReportRequest, reportCache, reportPage, reportPageSize, reportStatus, reportTargetType]);

  const loadReviewQueue = useCallback(async (showLoading = true) => {
    const request = createReviewRequest();
    if (showLoading) {
      setReviewLoading(true);
    }
    setReviewError(null);
    try {
      // 待审队列来自治理服务的 REVIEW 项，处置后会转成 PASS/BLOCK。
      const response = await apiRequest<ReviewQueueResponse>(
        `/admin/content/review-queue${buildQuery({ page: reviewPage, size: reviewPageSize, sourceType: reviewSourceType })}`,
        { signal: request.signal },
      );
      if (!request.isCurrent()) {
        return;
      }
      setReviewItems(response.records);
      setReviewTotal(response.total);
      reviewCache.write({
        records: response.records,
        total: response.total,
      });
      setSelectedReviewId((current) => {
        if (response.records.length === 0) {
          return null;
        }
        return response.records.some((item) => item.itemId === current) ? current : response.records[0].itemId;
      });
    } catch (error) {
      if (isAbortError(error) || !request.isCurrent()) {
        return;
      }
      const apiError = error as ApiClientError;
      setReviewError(apiError.message || "加载待审队列失败");
    } finally {
      if (request.isCurrent()) {
        setReviewLoading(false);
      }
    }
  }, [createReviewRequest, reviewCache, reviewPage, reviewPageSize, reviewSourceType]);

  const loadAuditLogs = useCallback(async (showLoading = true) => {
    const request = createAuditRequest();
    if (showLoading) {
      setAuditLoading(true);
    }
    setAuditError(null);
    try {
      // 审计列表按 traceId/targetId 支持跨域联查，保留后端分页口径。
      const response = await apiRequest<AuditLogListResponse>(
        `/admin/content/audit-logs${buildQuery({
          page: auditPage,
          size: auditPageSize,
          targetType: auditTargetType || undefined,
          actionType: auditActionType || undefined,
          targetId: auditTargetId || undefined,
          traceId: auditTraceId || undefined,
        })}`,
        { signal: request.signal },
      );
      if (!request.isCurrent()) {
        return;
      }
      setAuditLogs(response.records);
      setAuditTotal(response.total);
      auditCache.write({
        records: response.records,
        total: response.total,
      });
      setSelectedAuditId((current) => {
        if (response.records.length === 0) {
          return null;
        }
        return response.records.some((item) => item.id === current) ? current : response.records[0].id;
      });
    } catch (error) {
      if (isAbortError(error) || !request.isCurrent()) {
        return;
      }
      const apiError = error as ApiClientError;
      setAuditError(apiError.message || "加载审计日志失败");
    } finally {
      if (request.isCurrent()) {
        setAuditLoading(false);
      }
    }
  }, [auditActionType, auditCache, auditPage, auditPageSize, auditTargetId, auditTargetType, auditTraceId, createAuditRequest]);

  const loadAiTraceLogs = useCallback(async (traceId: string) => {
    const request = createAiTraceRequest();
    setAiTraceLoading(true);
    setAiTraceError(null);
    setAiTraceLogs([]);
    setAiTraceTotal(0);
    setSelectedAiTraceLogId(null);
    setAiTraceDetail(null);
    setAiTraceDetailError(null);

    try {
      // 内容治理页只按 traceId 反查 AI 调用摘要，详情仍复用 AI 日志接口。
      const response = await apiRequest<AiTraceLogListResponse>(
        `/admin/ai/logs${buildQuery({ page: 1, size: 20, traceId: traceId.trim() || undefined })}`,
        { signal: request.signal },
      );
      if (!request.isCurrent()) {
        return;
      }
      setAiTraceLogs(response.records);
      setAiTraceTotal(response.total);
      setSelectedAiTraceLogId(response.records[0]?.id ?? null);
    } catch (error) {
      if (isAbortError(error) || !request.isCurrent()) {
        return;
      }
      const apiError = error as ApiClientError;
      setAiTraceError(apiError.message || "加载关联记录失败");
    } finally {
      if (request.isCurrent()) {
        setAiTraceLoading(false);
      }
    }
  }, [createAiTraceRequest]);

  const applyAuditFilters = useCallback(() => {
    const nextTargetId = auditTargetIdDraft.trim();
    const nextTraceId = auditTraceIdDraft.trim();
    setAuditPage(1);
    setAuditTargetId(nextTargetId);
    setAuditTraceId(nextTraceId);
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.set("tab", "audit");
      if (nextTraceId) {
        next.set("traceId", nextTraceId);
      } else {
        next.delete("traceId");
      }
      return next;
    }, { replace: true });
  }, [auditTargetIdDraft, auditTraceIdDraft, setSearchParams]);

  const resetAuditFilters = useCallback(() => {
    setAuditPage(1);
    setAuditTargetType("");
    setAuditActionType("");
    setAuditTargetId("");
    setAuditTargetIdDraft("");
    setAuditTraceId("");
    setAuditTraceIdDraft("");
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.set("tab", "audit");
      next.delete("traceId");
      return next;
    }, { replace: true });
  }, [setSearchParams]);

  const hasAuditFilters = Boolean(
    auditTargetType
    || auditActionType
    || auditTargetId
    || auditTraceId
    || auditTargetIdDraft.trim()
    || auditTraceIdDraft.trim(),
  );

  const loadAiTraceDetail = useCallback(async (logId: number) => {
    const request = createAiTraceDetailRequest();
    setAiTraceDetailLoading(true);
    setAiTraceDetailError(null);

    try {
      const response = await apiRequest<AiTraceLogDetailResponse>(`/admin/ai/logs/${logId}`, {
        signal: request.signal,
      });
      if (!request.isCurrent()) {
        return;
      }
      setAiTraceDetail(response);
    } catch (error) {
      if (isAbortError(error) || !request.isCurrent()) {
        return;
      }
      const apiError = error as ApiClientError;
      setAiTraceDetail(null);
      setAiTraceDetailError(apiError.message || "加载记录详情失败");
    } finally {
      if (request.isCurrent()) {
        setAiTraceDetailLoading(false);
      }
    }
  }, [createAiTraceDetailRequest]);

  const loadSensitiveTerms = useCallback(async (showLoading = true) => {
    const request = createTermsRequest();
    if (showLoading) {
      setTermsLoading(true);
    }
    setTermsError(null);
    try {
      const response = await apiRequest<SensitiveTermsResponse>(
        `/admin/content/sensitive-terms${buildQuery({
          page: termPage,
          size: termPageSize,
          keyword: termKeyword || undefined,
          termType: termTypeFilter || undefined,
          riskLevel: termRiskFilter || undefined,
          status: termStatusFilter || undefined,
        })}`,
        { signal: request.signal },
      );
      if (!request.isCurrent()) {
        return;
      }
      // 删除或筛选后当前页为空时自动回到最后一页，避免表格停在空页。
      if (response.records.length === 0 && response.total > 0 && termPage > 1) {
        const lastPage = Math.max(1, Math.ceil(response.total / termPageSize));
        if (lastPage !== termPage) {
          setTermPage(lastPage);
          return;
        }
      }
      setSensitiveTerms(response.records);
      setTermTotal(response.total);
      setTermSummary(response.summary);
      termsCache.write({
        records: response.records,
        total: response.total,
        summary: response.summary,
      });
      setSelectedTermIds((current) => current.filter((termId) => response.records.some((item) => item.termId === termId)));
      setFocusedTermId((current) => {
        if (response.records.length === 0) {
          return null;
        }
        return response.records.some((item) => item.termId === current) ? current : response.records[0].termId;
      });
    } catch (error) {
      if (isAbortError(error) || !request.isCurrent()) {
        return;
      }
      const apiError = error as ApiClientError;
      setTermsError(apiError.message || "加载敏感词失败");
    } finally {
      if (request.isCurrent()) {
        setTermsLoading(false);
      }
    }
  }, [createTermsRequest, termKeyword, termPage, termPageSize, termRiskFilter, termStatusFilter, termTypeFilter, termsCache]);

  useEffect(() => {
    if (!reportCache.cached) {
      return;
    }
    setReports(reportCache.cached.records);
    setReportTotal(reportCache.cached.total);
  }, [reportCache.cached]);

  useEffect(() => {
    if (!reviewCache.cached) {
      return;
    }
    setReviewItems(reviewCache.cached.records);
    setReviewTotal(reviewCache.cached.total);
  }, [reviewCache.cached]);

  useEffect(() => {
    if (!auditCache.cached) {
      return;
    }
    setAuditLogs(auditCache.cached.records);
    setAuditTotal(auditCache.cached.total);
  }, [auditCache.cached]);

  useEffect(() => {
    if (!termsCache.cached) {
      return;
    }
    setSensitiveTerms(termsCache.cached.records);
    setTermTotal(termsCache.cached.total);
    setTermSummary(termsCache.cached.summary);
  }, [termsCache.cached]);

  useEffect(() => {
    void loadReports(!reportCache.hasCache);
  }, [loadReports, reportCache.hasCache]);

  useEffect(() => {
    void loadReviewQueue(!reviewCache.hasCache);
  }, [loadReviewQueue, reviewCache.hasCache]);

  useEffect(() => {
    if (activeTab === "audit" || activeTab === "home") {
      void loadAuditLogs(!auditCache.hasCache);
    }
  }, [activeTab, auditCache.hasCache, loadAuditLogs]);

  useEffect(() => {
    if (!aiTraceModalOpen || !selectedAiTraceLogId) {
      setAiTraceDetail(null);
      setAiTraceDetailError(null);
      return;
    }

    void loadAiTraceDetail(selectedAiTraceLogId);
  }, [aiTraceModalOpen, loadAiTraceDetail, selectedAiTraceLogId]);

  useEffect(() => {
    void loadSensitiveTerms(!termsCache.hasCache);
  }, [loadSensitiveTerms, termsCache.hasCache]);

  useEffect(() => {
    if (activeTab !== "reports") {
      return;
    }
    const selectedReport = reports.find((item) => item.reportId === selectedReportId);
    if (!selectedReport) {
      setReportDetail(null);
      setReportActions([]);
      return;
    }
    let canceled = false;
    setReportDetailLoading(true);
    setReportDetail(null);
    // 举报详情和处置历史分接口读取，抽屉展示时再补全上下文。
    Promise.all([
      apiRequest<ReportDetailResponse>(`/admin/content/reports/${selectedReport.reportId}`),
      apiRequest<ReportActionHistoryResponse>(`/admin/content/reports/${selectedReport.reportId}/actions`),
    ]).then(([detailResponse, historyResponse]) => {
      if (canceled) {
        return;
      }
      setReportDetail(detailResponse);
      setReportActions(historyResponse.records);
    }).catch((error) => {
      if (canceled) {
        return;
      }
      const apiError = error as ApiClientError;
      message.error(apiError.message || "加载举报详情失败");
    }).finally(() => {
      if (!canceled) {
        setReportDetailLoading(false);
      }
    });
    return () => {
      canceled = true;
    };
  }, [activeTab, reports, selectedReportId]);

  useEffect(() => {
    if (activeTab !== "review") {
      return;
    }
    const selectedReview = reviewItems.find((item) => item.itemId === selectedReviewId);
    if (!selectedReview) {
      setReviewDetail(null);
      return;
    }
    let canceled = false;
    setReviewDetailLoading(true);
    setReviewDetail(null);
    // 待审详情可能包含完整正文，列表页只保留 preview。
    apiRequest<ReviewQueueDetailResponse>(`/admin/content/review-queue/${selectedReview.itemId}`)
      .then((response) => {
        if (!canceled) {
          setReviewDetail(response);
        }
      })
      .catch((error) => {
        if (canceled) {
          return;
        }
        const apiError = error as ApiClientError;
        message.error(apiError.message || "加载待审详情失败");
      })
      .finally(() => {
        if (!canceled) {
          setReviewDetailLoading(false);
        }
      });
    return () => {
      canceled = true;
    };
  }, [activeTab, reviewItems, selectedReviewId]);

  const selectedReport = useMemo(
    () => reports.find((item) => item.reportId === selectedReportId) ?? null,
    [reports, selectedReportId],
  );

  const selectedReview = useMemo(
    () => reviewItems.find((item) => item.itemId === selectedReviewId) ?? null,
    [reviewItems, selectedReviewId],
  );

  const selectedAuditLog = useMemo(
    () => auditLogs.find((item) => item.id === selectedAuditId) ?? null,
    [auditLogs, selectedAuditId],
  );

  const selectedAiTraceLog = useMemo(
    () => aiTraceLogs.find((item) => item.id === selectedAiTraceLogId) ?? null,
    [aiTraceLogs, selectedAiTraceLogId],
  );

  const filteredReports = useMemo(() => {
    const keyword = reportKeyword.trim().toLowerCase();
    if (!keyword) {
      return reports;
    }
    return reports.filter((item) => [
      String(item.reportId),
      item.contentTitle ?? "",
      item.contentBody ?? "",
      item.targetId ?? "",
      item.reasonCode ?? "",
    ].join(" ").toLowerCase().includes(keyword));
  }, [reportKeyword, reports]);

  const filteredReviewItems = useMemo(() => {
    const keyword = reviewKeyword.trim().toLowerCase();
    if (!keyword) {
      return reviewItems;
    }
    return reviewItems.filter((item) => [
      String(item.itemId),
      item.preview,
      item.targetId,
      item.reasonCode,
    ].join(" ").toLowerCase().includes(keyword));
  }, [reviewItems, reviewKeyword]);

  const handleRefreshCurrentTab = useCallback(() => {
    if (activeTab === "home") {
      // 首页摘要需要四个域一起刷新，其他 tab 只刷新当前工作面。
      void Promise.all([loadReports(), loadReviewQueue(), loadAuditLogs(), loadSensitiveTerms()]);
      return;
    }
    if (activeTab === "reports") {
      void loadReports();
      return;
    }
    if (activeTab === "review") {
      void loadReviewQueue();
      return;
    }
    if (activeTab === "audit") {
      void loadAuditLogs();
      return;
    }
    void loadSensitiveTerms();
  }, [activeTab, loadAuditLogs, loadReports, loadReviewQueue, loadSensitiveTerms]);

  const handleResetTermFilters = useCallback(() => {
    setTermKeywordDraft("");
    setTermKeyword("");
    setTermTypeFilter("");
    setTermRiskFilter("");
    setTermStatusFilter("");
    setTermPage(1);
  }, []);

  const handleOpenAiTraceModal = useCallback((traceId: string | null | undefined) => {
    if (!traceId?.trim()) {
      message.info("该条记录未附带关联编号，暂时无法查看关联生成记录。");
      return;
    }

    setAiTraceModalOpen(true);
    setActiveAiTraceId(traceId.trim());
    // 从举报或审计记录打开 AI 关联弹层时，保持原 tab 不变。
    void loadAiTraceLogs(traceId.trim());
  }, [loadAiTraceLogs]);

  const openReportDrawer = useCallback((reportId: number) => {
    setSelectedReportId(reportId);
    setReportDrawerOpen(true);
  }, []);

  const openReviewDrawer = useCallback((itemId: number) => {
    setSelectedReviewId(itemId);
    setReviewDrawerOpen(true);
  }, []);

  const openAuditDrawer = useCallback((auditId: number) => {
    setSelectedAuditId(auditId);
    setAuditDrawerOpen(true);
  }, []);

  const openTermDrawer = useCallback((termId: number) => {
    setFocusedTermId(termId);
    setTermDrawerOpen(true);
  }, []);

  const openTermEditor = useCallback((term: SensitiveTermItem | null = null) => {
    setEditingTerm(term);
    setTermDrawerOpen(false);
    setTermEditInitialValues({
      term: term?.term ?? "",
      termType: term?.termType ?? DEFAULT_SENSITIVE_TERM_FORM_VALUES.termType,
      riskLevel: term?.riskLevel ?? DEFAULT_SENSITIVE_TERM_FORM_VALUES.riskLevel,
      action: term?.action ?? DEFAULT_SENSITIVE_TERM_FORM_VALUES.action,
      sourceScope: term?.sourceScope ?? DEFAULT_SENSITIVE_TERM_FORM_VALUES.sourceScope,
      whitelist: term?.whitelist ?? DEFAULT_SENSITIVE_TERM_FORM_VALUES.whitelist,
      enabled: term?.enabled ?? DEFAULT_SENSITIVE_TERM_FORM_VALUES.enabled,
    });
    setTermEditorOpen(true);
  }, []);

  const closeTermEditor = useCallback(() => {
    setTermEditorOpen(false);
    setEditingTerm(null);
  }, []);

  const handleSubmitReportDecision = async () => {
    if (!selectedReport) {
      return;
    }
    try {
      const values = await reportDecisionForm.validateFields();
      setReportDecisionSaving(true);
      try {
        // 举报处置会写动作历史、目标状态和审计日志，提交后刷新列表即可。
        await apiRequest<void>(`/admin/content/reports/${selectedReport.reportId}/decision`, {
          method: "POST",
          body: JSON.stringify(values),
        });
        message.success(`举报 #${selectedReport.reportId} 已完成处置`);
        setReportDecisionOpen(false);
        await Promise.all([loadReports(), activeTab === "audit" ? loadAuditLogs() : Promise.resolve()]);
      } catch (error) {
        const apiError = error as ApiClientError;
        message.error(apiError.message || "提交举报处置失败");
      } finally {
        setReportDecisionSaving(false);
      }
    } catch {
      return;
    }
  };

  const handleSubmitReviewDecision = async () => {
    if (!selectedReview) {
      return;
    }
    try {
      const values = await reviewDecisionForm.validateFields();
      setReviewDecisionSaving(true);
      try {
        // 待审处置只处理当前 queue item，目标内容状态由后端治理服务统一落库。
        await apiRequest<void>(`/admin/content/review-queue/${selectedReview.itemId}/decision`, {
          method: "POST",
          body: JSON.stringify(values),
        });
        message.success(`待审项 #${selectedReview.itemId} 已完成审核`);
        setReviewDecisionOpen(false);
        await Promise.all([loadReviewQueue(), activeTab === "audit" ? loadAuditLogs() : Promise.resolve()]);
      } catch (error) {
        const apiError = error as ApiClientError;
        message.error(apiError.message || "提交待审处置失败");
      } finally {
        setReviewDecisionSaving(false);
      }
    } catch {
      return;
    }
  };

  const handleCreateTerm = async () => {
    try {
      const values = await termEditForm.validateFields();
      setTermSaving(true);
      try {
        const created = await apiRequest<SensitiveTermItem>("/admin/content/sensitive-terms", {
          method: "POST",
          body: JSON.stringify(values),
        });
        message.success("敏感词已新增");
        setFocusedTermId(created.termId);
        closeTermEditor();
        await loadSensitiveTerms();
      } catch (error) {
        const apiError = error as ApiClientError;
        message.error(apiError.message || "新增敏感词失败");
      } finally {
        setTermSaving(false);
      }
    } catch {
      return;
    }
  };

  const handleUpdateTerm = async () => {
    if (!editingTerm) {
      return;
    }
    try {
      const values = await termEditForm.validateFields();
      setTermEditSaving(true);
      try {
        await apiRequest<SensitiveTermItem>(`/admin/content/sensitive-terms/${editingTerm.termId}`, {
          method: "PUT",
          body: JSON.stringify(values),
        });
        message.success("敏感词已更新");
        closeTermEditor();
        await loadSensitiveTerms();
      } catch (error) {
        const apiError = error as ApiClientError;
        message.error(apiError.message || "更新敏感词失败");
      } finally {
        setTermEditSaving(false);
      }
    } catch {
      return;
    }
  };

  const handleDeleteTerm = async (termId: number) => {
    setTermDeletingId(termId);
    try {
      await apiRequest<void>(`/admin/content/sensitive-terms/${termId}`, { method: "DELETE" });
      message.success("敏感词已删除");
      await loadSensitiveTerms();
    } catch (error) {
      const apiError = error as ApiClientError;
      message.error(apiError.message || "删除敏感词失败");
    } finally {
      setTermDeletingId(null);
    }
  };

  const handleBatchUpdateTerms = async (enabled: boolean) => {
    if (!selectedTermIds.length) {
      message.warning("请先选择要操作的敏感词");
      return;
    }
    setTermBatchSaving(true);
    try {
      const response = await apiRequest<SensitiveTermBatchOperationResponse>("/admin/content/sensitive-terms/batch-status", {
        method: "POST",
        body: JSON.stringify({ termIds: selectedTermIds, enabled }),
      });
      message.success(`已批量${enabled ? "启用" : "停用"} ${response.affectedCount} 条敏感词`);
      setSelectedTermIds([]);
      await loadSensitiveTerms();
    } catch (error) {
      const apiError = error as ApiClientError;
      message.error(apiError.message || `批量${enabled ? "启用" : "停用"}失败`);
    } finally {
      setTermBatchSaving(false);
    }
  };

  const handleBatchDeleteTerms = async () => {
    if (!selectedTermIds.length) {
      message.warning("请先选择要删除的敏感词");
      return;
    }
    setTermBatchDeleting(true);
    try {
      const response = await apiRequest<SensitiveTermBatchOperationResponse>("/admin/content/sensitive-terms/batch-delete", {
        method: "POST",
        body: JSON.stringify({ termIds: selectedTermIds }),
      });
      message.success(`已批量删除 ${response.affectedCount} 条敏感词`);
      setSelectedTermIds([]);
      await loadSensitiveTerms();
    } catch (error) {
      const apiError = error as ApiClientError;
      message.error(apiError.message || "批量删除敏感词失败");
    } finally {
      setTermBatchDeleting(false);
    }
  };

  const handleExportTerms = async () => {
    try {
      setTermExporting(true);
      // 导出走原始 Response，文件名优先采用后端 Content-Disposition。
      const response = await apiRequest<Response>("/admin/content/sensitive-terms/export", { rawResponse: true });
      if (!response.ok) {
        throw new ApiClientError("导出敏感词失败", response.status);
      }
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = getResponseFilename(response, "sensitive-terms.csv");
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
      message.success("敏感词词库已开始下载");
    } catch (error) {
      const apiError = error as ApiClientError;
      message.error(apiError.message || "导出敏感词失败");
    } finally {
      setTermExporting(false);
    }
  };

  const handleImportTerms = async (file: File, options?: { fileFormat?: "CSV" | "PLAIN_TEXT"; config?: SensitiveTermImportConfigValues }) => {
    const formData = new FormData();
    formData.append("file", file);
    if (options?.fileFormat) {
      formData.append("fileFormat", options.fileFormat);
    }
    if (options?.config) {
      formData.append("termType", options.config.termType);
      formData.append("riskLevel", options.config.riskLevel);
      formData.append("action", options.config.action);
      formData.append("sourceScope", options.config.sourceScope);
      formData.append("whitelist", String(options.config.whitelist));
      formData.append("enabled", String(options.config.enabled));
    }
    setTermImporting(true);
    try {
      // CSV 使用文件内字段，纯文本则套用弹层里选择的统一治理配置。
      const result = await apiRequest<SensitiveTermImportResponse>("/admin/content/sensitive-terms/import", {
        method: "POST",
        body: formData,
      });
      await loadSensitiveTerms();
      setSelectedTermIds([]);
      if (result.errors.length) {
        message.warning(`新增 ${result.createdCount} 条，更新 ${result.updatedCount} 条，跳过 ${result.skippedCount} 条`);
      } else {
        message.success(`新增 ${result.createdCount} 条，更新 ${result.updatedCount} 条`);
      }
    } catch (error) {
      const apiError = error as ApiClientError;
      message.error(apiError.message || "导入敏感词失败");
    } finally {
      setTermImporting(false);
      if (importInputRef.current) {
        importInputRef.current.value = "";
      }
    }
  };

  const closeTermImportConfig = useCallback(() => {
    setTermImportConfigOpen(false);
    setPendingImportFile(null);
    if (importInputRef.current) {
      importInputRef.current.value = "";
    }
  }, []);

  const handleSelectTermImportFile = useCallback((file: File) => {
    const normalizedName = file.name.trim().toLowerCase();
    if (normalizedName.endsWith(".txt") || normalizedName.endsWith(".text")) {
      setPendingImportFile(file);
      setTermImportConfigInitialValues(DEFAULT_SENSITIVE_TERM_IMPORT_CONFIG_VALUES);
      setTermImportConfigOpen(true);
      return;
    }
    void handleImportTerms(file, { fileFormat: "CSV" });
  }, [handleImportTerms]);

  const handleConfirmPlainTextImport = useCallback(async () => {
    if (!pendingImportFile) {
      message.warning("请先选择要导入的词库文件");
      return;
    }
    try {
      const values = await termImportConfigForm.validateFields();
      await handleImportTerms(pendingImportFile, {
        fileFormat: "PLAIN_TEXT",
        config: values,
      });
      closeTermImportConfig();
    } catch {
      return;
    }
  }, [closeTermImportConfig, handleImportTerms, pendingImportFile, termImportConfigForm]);

  const termStats = useMemo(() => {
    return {
      total: termSummary.total,
      enabled: termSummary.enabled,
      whitelist: termSummary.whitelist,
      categories: termSummary.categories,
    };
  }, [termSummary]);

  const focusedTerm = useMemo(
    () => sensitiveTerms.find((item) => item.termId === focusedTermId) ?? null,
    [focusedTermId, sensitiveTerms],
  );

  const reportStatusSummary = useMemo(() => ({
    pending: filteredReports.filter((item) => item.status === "PENDING").length,
    accepted: filteredReports.filter((item) => item.status === "ACCEPTED").length,
    closed: filteredReports.filter((item) => item.status === "CLOSED").length,
  }), [filteredReports]);

  const reviewRiskSummary = useMemo(() => ({
    critical: filteredReviewItems.filter((item) => item.riskLevel === "CRITICAL").length,
    high: filteredReviewItems.filter((item) => item.riskLevel === "HIGH").length,
    aiOutput: filteredReviewItems.filter((item) => item.sourceType === "AI_OUTPUT").length,
    community: filteredReviewItems.filter((item) => item.sourceType !== "AI_OUTPUT").length,
  }), [filteredReviewItems]);

  const selectedReportTargetUserId = useMemo(
    () => getAdminUserTargetId(reportDetail?.targetType ?? selectedReport?.targetType, reportDetail?.targetId ?? selectedReport?.targetId),
    [reportDetail, selectedReport],
  );

  const selectedReportTargetType = reportDetail?.targetType ?? selectedReport?.targetType ?? null;
  const selectedReportTargetId = reportDetail?.targetId ?? selectedReport?.targetId ?? null;
  const selectedReportContentPostId = reportDetail?.contentPostId ?? selectedReport?.contentPostId ?? null;
  const selectedReportContextHref = getCommunityContextHref(
    selectedReportTargetType,
    selectedReportTargetId,
    selectedReportContentPostId,
  );

  const selectedAuditTargetUserId = useMemo(
    () => getAdminUserTargetId(selectedAuditLog?.targetType, selectedAuditLog?.targetId),
    [selectedAuditLog],
  );

  const selectedAuditDetailObject = useMemo(
    () => parseAuditDetailObject(selectedAuditLog?.detailJson ?? ""),
    [selectedAuditLog],
  );

  const workspaceHeroSummary = useMemo(() => {
    if (activeTab === "home") {
      return {
        badge: "内容总览",
        title: "内容治理",
        description: "先看治理摘要与重点提醒，再进入举报、待审、审计日志或敏感词库处理具体工作。",
      };
    }
    if (activeTab === "review") {
      return {
        badge: "待审处理",
        title: "待审队列",
        description: "查看命中原因与风险等级，快速完成通过或驳回。",
      };
    }
    if (activeTab === "audit") {
      return {
        badge: "操作记录",
        title: "审计日志",
        description: "按动作、对象和关联编号查看处理记录。",
      };
    }
    if (activeTab === "terms") {
      return {
        badge: "词库管理",
        title: "敏感词库",
        description: "管理词条录入、批量启停与风险动作配置。",
      };
    }
    return {
      badge: "举报处理",
      title: "举报中心",
      description: "筛选、核对与处置举报工单。",
    };
  }, [activeTab]);

  const homeWorkspaceCards = useMemo(() => [
    {
      key: "reports",
      title: "举报中心",
      description: "查看举报对象、原文与处置历史，完成采纳、驳回或关闭。",
      metricLabel: "待处理工单",
      metricValue: formatCount(reportStatusSummary.pending),
      note: `总计 ${formatCount(reportTotal)} 条举报工单`,
      icon: Flag,
      tone: "rose" as const,
      to: "/admin/content?tab=reports",
      cta: "进入举报中心",
    },
    {
      key: "review",
      title: "待审队列",
      description: "集中处理帖子、评论与智能生成内容中的高风险待审项。",
      metricLabel: "高优先级待审",
      metricValue: formatCount(reviewRiskSummary.critical + reviewRiskSummary.high),
      note: `队列总计 ${formatCount(reviewTotal)} 条`,
      icon: ListChecks,
      tone: "amber" as const,
      to: "/admin/content?tab=review",
      cta: "进入待审队列",
    },
    {
      key: "audit",
      title: "审计日志",
      description: "按动作、对象与关联编号查看处理记录和关联信息。",
      metricLabel: "最近日志量",
      metricValue: formatCount(auditTotal),
      note: auditTraceId ? `当前关联编号：${auditTraceId}` : "支持按关联编号检索和查看关联用户",
      icon: History,
      tone: "slate" as const,
      to: "/admin/content?tab=audit",
      cta: "进入审计日志",
    },
    {
      key: "terms",
      title: "敏感词库",
      description: "管理词条录入、启停、白名单与批量治理动作。",
      metricLabel: "启用规则",
      metricValue: formatCount(termStats.enabled),
      note: `总词条 ${formatCount(termStats.total)}，白名单 ${formatCount(termStats.whitelist)} 条`,
      icon: Type,
      tone: "violet" as const,
      to: "/admin/content?tab=settings",
      cta: "进入敏感词库",
    },
  ], [auditTotal, auditTraceId, reportStatusSummary.pending, reportTotal, reviewRiskSummary.critical, reviewRiskSummary.high, reviewTotal, termStats.enabled, termStats.total, termStats.whitelist]);

  const reportColumns = useMemo<TableColumnsType<ReportItem>>(
    () => [
      {
        title: "举报单号",
        dataIndex: "reportId",
        render: (value: number) => <Text strong>#{value}</Text>,
      },
      {
        title: "目标类型",
        dataIndex: "targetType",
        render: (value: string) => getTargetTypeTag(value),
      },
      {
        title: "内容概要",
        key: "content",
        render: (_value, record) => (
          <div className="min-w-0">
            <Text strong className="block text-sm text-slate-900">
              {record.contentTitle || `目标 ${record.targetId}`}
            </Text>
            <Text type="secondary" className="block text-xs leading-6">
              {getDisplayText(record.contentBody, "暂无正文摘要")}
            </Text>
          </div>
        ),
      },
      {
        title: "原因",
        dataIndex: "reasonCode",
        render: (value: string) => getReadableCodeLabel(value, moderationReasonLabelMap, value),
      },
      {
        title: "状态",
        dataIndex: "status",
        render: (value: string) => getReportStatusTag(value),
      },
      {
        title: "时间",
        render: (_value, record) => (
          <div className="space-y-1">
            <Text type="secondary" className="block text-xs">创建 {formatDateTime(record.createdAt)}</Text>
            <Text type="secondary" className="block text-xs">更新 {formatDateTime(record.updatedAt)}</Text>
          </div>
        ),
      },
      {
        title: "操作",
        key: "action",
        render: (_value, record) => (
          <Space size={[4, 4]} wrap>
            <Button
              type="link"
              size="small"
              onClick={(event) => {
                event.stopPropagation();
                openReportDrawer(record.reportId);
              }}
            >
              查看
            </Button>
            <Button
              type="link"
              size="small"
              onClick={(event) => {
                event.stopPropagation();
                setSelectedReportId(record.reportId);
                reportDecisionForm.setFieldsValue({
                  decision: record.status === "PENDING" ? "ACCEPTED" : "CLOSED",
                  action: record.status === "PENDING" ? "TAKE_DOWN" : "NO_ACTION",
                  comment: "",
                });
                setReportDecisionOpen(true);
              }}
            >
              处置
            </Button>
          </Space>
        ),
      },
    ],
    [openReportDrawer, reportDecisionForm],
  );

  const reviewColumns = useMemo<TableColumnsType<ReviewQueueItem>>(
    () => [
      {
        title: "待审编号",
        dataIndex: "itemId",
        render: (value: number) => <Text strong>#{value}</Text>,
      },
      {
        title: "来源",
        dataIndex: "sourceType",
        render: (value: string) => <Tag>{getReadableCodeLabel(value, sourceTypeLabelMap, value)}</Tag>,
      },
      {
        title: "目标",
        dataIndex: "targetType",
        render: (value: string) => getTargetTypeTag(value),
      },
      {
        title: "风险",
        dataIndex: "riskLevel",
        render: (value: string) => <Tag color={getRiskTagColor(value)}>{getLabel(value, riskLevelLabelMap, value)}</Tag>,
      },
      {
        title: "内容预览",
        dataIndex: "preview",
        render: (value: string) => <Text className="block text-sm leading-6 text-slate-600">{value}</Text>,
      },
      {
        title: "时间",
        dataIndex: "createdAt",
        render: (value: string) => formatDateTime(value),
      },
      {
        title: "操作",
        key: "actions",
        render: (_value, record) => (
          <Space size={[4, 4]} wrap>
            <Button
              type="link"
              size="small"
              onClick={(event) => {
                event.stopPropagation();
                openReviewDrawer(record.itemId);
              }}
            >
              查看
            </Button>
            <Button
              type="link"
              size="small"
              onClick={(event) => {
                event.stopPropagation();
                setSelectedReviewId(record.itemId);
                reviewDecisionForm.setFieldsValue({ decision: "APPROVE", comment: "" });
                setReviewDecisionOpen(true);
              }}
            >
              审核
            </Button>
          </Space>
        ),
      },
    ],
    [openReviewDrawer, reviewDecisionForm],
  );

  const auditActionOptions = useMemo(
    () => Object.entries(auditActionLabelMap).map(([value, label]) => ({ value, label })),
    [],
  );

  const auditColumns = useMemo<TableColumnsType<AuditLogItem>>(
    () => [
      {
        title: "追踪号",
        dataIndex: "traceId",
        render: (value: string) => (
          <span className="block break-all font-mono text-[11px] font-semibold tracking-[0.01em] text-slate-500">{value}</span>
        ),
      },
      {
        title: "动作类型",
        dataIndex: "actionType",
        render: (value: string) => (
          <span className="admin-content-action-chip">
            {getReadableCodeLabel(value, auditActionLabelMap, value)}
          </span>
        ),
      },
      {
        title: "目标对象",
        key: "target",
        render: (_value, record) => (
          <div className="flex flex-wrap items-center gap-3">
            <span className="admin-content-target-chip">
              {getReadableCodeLabel(record.targetType, targetTypeLabelMap, record.targetType)}
            </span>
            <Text type="secondary" className="!mb-0 block text-xs !text-slate-500">{record.targetId}</Text>
          </div>
        ),
      },
      {
        title: "操作人",
        key: "operator",
        render: (_value, record) => (
          <div className="flex flex-wrap items-center gap-2">
            <Text strong className="!mb-0 block text-sm text-slate-900">{record.operatorDisplayName}</Text>
            <Text type="secondary" className="!mb-0 block text-xs !text-slate-500">用户 #{record.operatorUserId}</Text>
          </div>
        ),
      },
      {
        title: "时间",
        dataIndex: "createdAt",
        render: (value: string) => <span className="whitespace-nowrap">{formatDateTime(value)}</span>,
      },
      {
        title: "操作",
        key: "action",
        render: (_value, record) => (
          <Button
            type="link"
            size="small"
            onClick={(event) => {
              event.stopPropagation();
              openAuditDrawer(record.id);
            }}
          >
            查看
          </Button>
        ),
      },
    ],
    [openAuditDrawer],
  );

  const termColumns = useMemo<TableColumnsType<SensitiveTermItem>>(
    () => [
      {
        title: "词条",
        dataIndex: "term",
        render: (value: string, record) => (
          <button
            type="button"
            className="text-left text-sm font-semibold text-slate-900 transition-colors hover:text-indigo-600"
            onClick={() => openTermDrawer(record.termId)}
          >
            {maskSensitiveTermForTable(value)}
          </button>
        ),
      },
      {
        title: "分类",
        dataIndex: "termType",
        render: (value: string) => getReadableCodeLabel(value, sensitiveTermTypeLabelMap, value),
      },
      {
        title: "风险 / 动作",
        key: "policy",
        render: (_value, record) => (
          <div className="space-y-2">
            <Tag color={getRiskTagColor(record.riskLevel)}>{getLabel(record.riskLevel, riskLevelLabelMap, record.riskLevel)}</Tag>
            <div className="text-xs text-slate-500">{getReadableCodeLabel(record.action, moderationActionLabelMap, record.action)}</div>
          </div>
        ),
      },
      {
        title: "来源 / 状态",
        key: "status",
        render: (_value, record) => (
          <div className="space-y-2">
            <Tag>{getReadableCodeLabel(record.sourceScope, sourceTypeLabelMap, record.sourceScope)}</Tag>
            <div className="flex flex-wrap gap-2">
              {record.enabled ? <Tag color="success">启用中</Tag> : <Tag>已停用</Tag>}
              {record.whitelist ? <Tag color="processing">白名单</Tag> : null}
            </div>
          </div>
        ),
      },
      {
        title: "更新时间",
        dataIndex: "updatedAt",
        render: (value: string) => formatDateTime(value),
      },
      {
        title: "操作",
        key: "actions",
        render: (_value, record) => (
          <Space size={[4, 4]} wrap>
            <Button
              type="link"
              size="small"
              onClick={(event) => {
                event.stopPropagation();
                openTermEditor(record);
              }}
            >
              编辑
            </Button>
            <Popconfirm
              title="确认删除该敏感词？"
              description="删除后词条会立即从当前词库移除。"
              okText="确认"
              cancelText="取消"
              onConfirm={() => void handleDeleteTerm(record.termId)}
            >
              <Button
                type="link"
                size="small"
                danger
                loading={termDeletingId === record.termId}
                onClick={(event) => event.stopPropagation()}
              >
                删除
              </Button>
            </Popconfirm>
          </Space>
        ),
      },
    ],
    [openTermDrawer, openTermEditor, termDeletingId],
  );

  const currentTabRefreshLoading = activeTab === "home"
    ? reportLoading || reviewLoading || auditLoading || termsLoading
    : activeTab === "reports"
      ? reportLoading
      : activeTab === "review"
        ? reviewLoading
        : activeTab === "audit"
          ? auditLoading
          : termsLoading;

  return (
    <AdminPageFrame className="admin-content-page">
      <AdminPageHeader
        sectionLabel={workspaceHeroSummary.badge}
        title={workspaceHeroSummary.title}
        description={workspaceHeroSummary.description}
        actions={(
          <Button className="!h-11 !rounded-2xl !border-slate-200 !px-5 !shadow-none" onClick={handleRefreshCurrentTab} disabled={currentTabRefreshLoading}>
            <RefreshCcw size={16} className={currentTabRefreshLoading ? "mr-2 animate-spin" : "mr-2"} />
            刷新数据
          </Button>
        )}
      />

      {activeTab === "home" ? (
                <div className="space-y-6">
                  <div className="grid gap-4 xl:grid-cols-2 2xl:grid-cols-4">
                    {homeWorkspaceCards.map((item) => {
                      const palette = contentAccentClassMap[item.tone];
                      const Icon = item.icon;

                      return (
                        <div key={item.key} className="admin-content-summary-card">
                          <div className="flex items-start justify-between gap-4">
                            <div>
                              <div className={joinAdminClassNames("inline-flex rounded-full px-3 py-1 text-[10px] font-bold", palette.badge)}>
                                {item.metricLabel}
                              </div>
                              <div className="mt-4 font-['Manrope'] text-[1.45rem] font-black tracking-tight text-slate-950">
                                {item.title}
                              </div>
                            </div>
                            <div className={joinAdminClassNames("flex h-11 w-11 items-center justify-center rounded-2xl", palette.icon)}>
                              <Icon size={18} />
                            </div>
                          </div>
                          <div className={joinAdminClassNames("mt-5 font-['Manrope'] text-[2rem] font-black tracking-[-0.05em]", palette.value)}>
                            {item.metricValue}
                          </div>
                          <div className="mt-2 text-sm leading-6 text-slate-500">{item.note}</div>
                          <Paragraph className="!mb-0 !mt-4 !text-sm !leading-7 !text-slate-500">
                            {item.description}
                          </Paragraph>
                        </div>
                      );
                    })}
                  </div>

                  <div className="grid gap-6 xl:grid-cols-[minmax(0,1.15fr)_360px]">
                    <AdminSurfaceCard
                      title="当前治理提醒"
                      description="主页只展示必要信息，具体处理请从对应工作区进入。"
                      className="admin-content-detail-card"
                      bodyClassName="grid gap-3 md:grid-cols-2"
                    >
                      <ModerationReminderCard
                        title="举报处理"
                        tone="rose"
                        icon={Flag}
                        description={(
                          <>
                          当前有 <strong className="font-semibold text-slate-950">{formatCount(reportStatusSummary.pending)}</strong> 条待处理举报，优先查看高频举报对象和需要立即下架的内容。
                          </>
                        )}
                      />
                      <ModerationReminderCard
                        title="人工复核"
                        tone="amber"
                        icon={ListChecks}
                        description={(
                          <>
                          当前高风险待审项共 <strong className="font-semibold text-slate-950">{formatCount(reviewRiskSummary.critical + reviewRiskSummary.high)}</strong> 条，其中智能生成内容 {formatCount(reviewRiskSummary.aiOutput)} 条。
                          </>
                        )}
                      />
                      <ModerationReminderCard
                        title="审计反查"
                        tone="slate"
                        icon={History}
                        description={(
                          <>
                          当前已载入审计记录 <strong className="font-semibold text-slate-950">{formatCount(auditTotal)}</strong> 条，可按关联编号继续查看相关生成记录。
                          </>
                        )}
                      />
                      <ModerationReminderCard
                        title="词库覆盖"
                        tone="violet"
                        icon={Type}
                        description={(
                          <>
                          当前启用词条 <strong className="font-semibold text-slate-950">{formatCount(termStats.enabled)}</strong> 条，白名单 {formatCount(termStats.whitelist)} 条，覆盖 {formatCount(termStats.categories)} 个分类。
                          </>
                        )}
                      />
                    </AdminSurfaceCard>

                    <AdminSurfaceCard
                      title="快速动作"
                      description="常用入口统一保留在主页，减少来回切页。"
                      className="admin-content-detail-card"
                      bodyClassName="space-y-4"
                    >
                      <div className="admin-content-quick-actions">
                        {homeWorkspaceCards.map((item) => {
                          const Icon = item.icon;
                          return (
                            <Button
                              key={item.key}
                              className={joinAdminClassNames(
                                "admin-content-quick-action",
                                `admin-content-quick-action--${item.tone}`,
                              )}
                              onClick={() => navigate(item.to)}
                            >
                              <span className="admin-content-quick-action__icon">
                                <Icon size={17} />
                              </span>
                              <span className="admin-content-quick-action__copy">
                                <span className="admin-content-quick-action__title">{item.title}</span>
                                <span className="admin-content-quick-action__note">{item.metricLabel}</span>
                              </span>
                              <span className="admin-content-quick-action__arrow" aria-hidden="true">
                                <ArrowRight size={16} />
                              </span>
                            </Button>
                          );
                        })}
                      </div>
                    </AdminSurfaceCard>
                  </div>
                </div>
        ) : activeTab === "reports" ? (
                <div className="space-y-6">
                  <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                    <ModerationWorkspaceStatCard
                      label="待处理工单"
                      value={formatCount(reportStatusSummary.pending)}
                      note="当前筛选结果内需要优先决策的举报。"
                      icon={Flag}
                      tone="rose"
                    />
                    <ModerationWorkspaceStatCard
                      label="已采纳"
                      value={formatCount(reportStatusSummary.accepted)}
                      note="当前页已完成治理闭环的举报工单。"
                      icon={CheckCircle2}
                      tone="emerald"
                    />
                    <ModerationWorkspaceStatCard
                      label="已关闭"
                      value={formatCount(reportStatusSummary.closed)}
                      note="无需继续动作但保留留痕的历史工单。"
                      icon={History}
                      tone="amber"
                    />
                    <ModerationWorkspaceStatCard
                      label="当前选中"
                      value={selectedReport ? `#${selectedReport.reportId}` : "未选择"}
                      note="右侧详情区与处置动作同步围绕当前工单展开。"
                      icon={Eye}
                      tone="slate"
                    />
                  </div>

                  <AdminFilterBar className="admin-content-toolbar">
                    <SearchInput
                      allowClear
                      className="min-w-[280px] flex-1"
                      value={reportKeywordDraft}
                      onChange={(event) => setReportKeywordDraft(event.target.value)}
                      onSearch={(value) => setReportKeyword(value.trim())}
                      placeholder="搜索举报内容、目标 ID 或原因"
                    />
                    <Select className="min-w-[160px]" value={reportStatus} options={reportStatusOptions} onChange={(value) => {
                      setReportPage(1);
                      setReportStatus(value);
                    }} />
                    <Select className="min-w-[160px]" value={reportTargetType} options={targetTypeOptions} onChange={(value) => {
                      setReportPage(1);
                      setReportTargetType(value);
                    }} />
                  </AdminFilterBar>

                  <AdminSurfaceCard
                    title={`举报列表 · ${formatCount(filteredReports.length)} 条`}
                    description="按目标类型、举报摘要与状态筛选工单，点击任意行在右侧展开详情。"
                    className="admin-content-table-card"
                    bodyClassName="space-y-4"
                  >
                    {reportError ? <Alert type="error" showIcon className="mb-4 rounded-2xl" message={reportError} /> : null}
                    <Table<ReportItem>
                      className="admin-content-table"
                      rowKey="reportId"
                      loading={reportLoading}
                      columns={reportColumns}
                      dataSource={filteredReports}
                      tableLayout="auto"
                      pagination={{
                        current: reportPage,
                        pageSize: reportPageSize,
                        total: reportTotal,
                        locale: contentTablePaginationLocale,
                        showSizeChanger: true,
                        pageSizeOptions: ["10", "20", "50"],
                        onChange: (nextPage, nextPageSize) => {
                          setReportPage(nextPage);
                          setReportPageSize(nextPageSize);
                        },
                      }}
                      onRow={(record) => ({
                        onClick: () => openReportDrawer(record.reportId),
                      })}
                      rowClassName={(record) => joinAdminClassNames(
                        "cursor-pointer transition-colors hover:!bg-slate-50",
                        record.reportId === selectedReportId && "admin-content-table-row-active",
                      )}
                    />
                  </AdminSurfaceCard>
                </div>
        ) : activeTab === "review" ? (
                <div className="space-y-6">
                  <div className="grid grid-cols-1 gap-6 xl:grid-cols-[290px_minmax(0,1fr)]">
                    <div className="space-y-6">
                      <div className="admin-content-review-aside">
                        <div>
                          <div className="text-[11px] font-bold text-slate-400">内容来源</div>
                          <div className="mt-2 font-['Manrope'] text-[1.25rem] font-black tracking-tight text-slate-950">来源筛选</div>
                        </div>
                        <div className="mt-5 space-y-2">
                          {reviewSourceOptions.map((option) => {
                            const active = reviewSourceType === option.value;
                            return (
                              <button
                                key={option.value || "ALL"}
                                type="button"
                                className={joinAdminClassNames("admin-content-source-button", active && "admin-content-source-button--active")}
                                onClick={() => {
                                  setReviewPage(1);
                                  setReviewSourceType(option.value);
                                }}
                              >
                                <span>{option.label.replace("来源：", "")}</span>
                                {active ? <span className="text-[11px] font-bold">已选</span> : null}
                              </button>
                            );
                          })}
                        </div>

                        <div className="mt-6 border-t border-slate-200/80 pt-6">
                          <div className="text-[11px] font-bold text-slate-400">风险概况</div>
                          <div className="mt-4 space-y-3">
                            <div className="admin-content-risk-row">
                              <span>极高 / 高风险</span>
                              <strong>{formatCount(reviewRiskSummary.critical + reviewRiskSummary.high)}</strong>
                            </div>
                            <div className="admin-content-risk-row">
                              <span>智能生成内容</span>
                              <strong>{formatCount(reviewRiskSummary.aiOutput)}</strong>
                            </div>
                            <div className="admin-content-risk-row">
                              <span>社区内容</span>
                              <strong>{formatCount(reviewRiskSummary.community)}</strong>
                            </div>
                          </div>
                        </div>
                      </div>

                    </div>

                    <div className="space-y-6">
                      <AdminFilterBar className="admin-content-toolbar">
                        <SearchInput
                          allowClear
                          className="min-w-[280px] flex-1"
                          value={reviewKeywordDraft}
                          onChange={(event) => setReviewKeywordDraft(event.target.value)}
                          onSearch={(value) => setReviewKeyword(value.trim())}
                          placeholder="搜索待审内容、目标 ID 或原因"
                        />
                        <Select className="min-w-[180px]" value={reviewSourceType} options={reviewSourceOptions} onChange={(value) => {
                          setReviewPage(1);
                          setReviewSourceType(value);
                        }} />
                      </AdminFilterBar>

                      <AdminSurfaceCard
                        title={`待审队列 · ${formatCount(filteredReviewItems.length)} 条`}
                        description="突出来源、风险与命中原因，便于连续处理。"
                        className="admin-content-table-card"
                        bodyClassName="space-y-4"
                      >
                      {reviewError ? <Alert type="error" showIcon className="mb-4 rounded-2xl" message={reviewError} /> : null}
                      <Table<ReviewQueueItem>
                        className="admin-content-table"
                        rowKey="itemId"
                        loading={reviewLoading}
                        columns={reviewColumns}
                        dataSource={filteredReviewItems}
                        tableLayout="auto"
                        pagination={{
                          current: reviewPage,
                          pageSize: reviewPageSize,
                          total: reviewTotal,
                          locale: contentTablePaginationLocale,
                          showSizeChanger: true,
                          pageSizeOptions: ["10", "20", "50"],
                          onChange: (nextPage, nextPageSize) => {
                            setReviewPage(nextPage);
                            setReviewPageSize(nextPageSize);
                          },
                        }}
                        onRow={(record) => ({
                          onClick: () => openReviewDrawer(record.itemId),
                        })}
                        rowClassName={(record) => joinAdminClassNames(
                          "cursor-pointer transition-colors hover:!bg-slate-50",
                          record.itemId === selectedReviewId && "admin-content-table-row-active",
                        )}
                      />
                      </AdminSurfaceCard>
                    </div>
                  </div>
                </div>
        ) : activeTab === "audit" ? (
                <div className="space-y-6">
                  <div className="grid gap-4 md:grid-cols-3">
                    <ModerationWorkspaceStatCard
                      label="当前返回"
                      value={formatCount(auditLogs.length)}
                      note="本次过滤命中的日志条数。"
                      icon={History}
                      tone="slate"
                    />
                    <ModerationWorkspaceStatCard
                      label="关联编号"
                      value={auditTraceId || "未指定"}
                      note="输入关联编号后可快速定位相关记录。"
                      icon={Search}
                      tone="indigo"
                      valueClassName="admin-content-workspace-stat__value--compact break-all font-mono !tracking-normal"
                    />
                    <ModerationWorkspaceStatCard
                      label="目标用户日志"
                      value={formatCount(auditLogs.filter((item) => item.targetType === "USER").length)}
                      note="可直接下钻到用户详情的审计记录。"
                      icon={Eye}
                      tone="emerald"
                    />
                  </div>

                  <AdminFilterBar className="admin-content-toolbar">
                    <SearchInput
                      allowClear
                      className="min-w-[280px] flex-1"
                      placeholder="搜索目标 ID"
                      value={auditTargetIdDraft}
                      onChange={(event) => setAuditTargetIdDraft(event.target.value)}
                      onSearch={() => applyAuditFilters()}
                    />
                    <SearchInput
                      allowClear
                      className="min-w-[320px] flex-[1.15]"
                      placeholder="搜索关联编号"
                      value={auditTraceIdDraft}
                      onChange={(event) => setAuditTraceIdDraft(event.target.value)}
                      onSearch={() => applyAuditFilters()}
                    />
                    <Select className="min-w-[170px]" value={auditTargetType} options={targetTypeOptions} onChange={(value) => {
                      setAuditPage(1);
                      setAuditTargetType(value);
                    }} />
                    <Select
                      showSearch
                      allowClear
                      className="min-w-[220px]"
                      placeholder="动作类型"
                      value={auditActionType || undefined}
                      options={auditActionOptions}
                      onChange={(value) => {
                        setAuditPage(1);
                        setAuditActionType(value || "");
                      }}
                    />
                    <Button className="!h-11 !rounded-2xl !border-slate-200" onClick={applyAuditFilters}>
                      <Search size={16} className="mr-2" />
                      查询
                    </Button>
                    {hasAuditFilters ? (
                      <Button className="!h-11 !rounded-2xl !border-slate-200" onClick={resetAuditFilters}>
                        清空筛选
                      </Button>
                    ) : null}
                  </AdminFilterBar>

                  <AdminSurfaceCard
                    title={`审计日志 · ${formatCount(auditTotal)} 条`}
                    description="查看动作、目标对象与关联编号对应的处理记录。"
                    className="admin-content-table-card"
                    bodyClassName="space-y-4"
                  >
                    {auditError ? <Alert type="error" showIcon className="mb-4 rounded-2xl" message={auditError} /> : null}
                    <Table<AuditLogItem>
                      className="admin-content-table"
                      rowKey="id"
                      loading={auditLoading}
                      columns={auditColumns}
                      dataSource={auditLogs}
                      tableLayout="auto"
                      pagination={{
                        current: auditPage,
                        pageSize: auditPageSize,
                        total: auditTotal,
                        locale: contentTablePaginationLocale,
                        showSizeChanger: true,
                        pageSizeOptions: ["10", "20", "50"],
                        onChange: (nextPage, nextPageSize) => {
                          setAuditPage(nextPage);
                          setAuditPageSize(nextPageSize);
                        },
                      }}
                      onRow={(record) => ({
                        onClick: () => openAuditDrawer(record.id),
                      })}
                      rowClassName={(record) => joinAdminClassNames(
                        "cursor-pointer transition-colors hover:!bg-slate-50",
                        record.id === selectedAuditId && "admin-content-table-row-active",
                      )}
                    />
                  </AdminSurfaceCard>
                </div>
        ) : (
                <div className="space-y-6">
                  <AdminFilterBar className="admin-content-toolbar">
                    <SearchInput
                      allowClear
                      className="min-w-[280px] flex-1"
                      value={termKeywordDraft}
                      onChange={(event) => setTermKeywordDraft(event.target.value)}
                      onSearch={(value) => {
                        setTermPage(1);
                        setTermKeyword(value.trim());
                      }}
                      placeholder="搜索词条、分类、来源或动作"
                    />
                    <Select
                      className="min-w-[160px]"
                      value={termTypeFilter}
                      options={[{ label: "分类：全部", value: "" }, ...termTypeOptions]}
                      onChange={(value) => {
                        setTermPage(1);
                        setTermTypeFilter(value);
                      }}
                    />
                    <Select
                      className="min-w-[160px]"
                      value={termRiskFilter}
                      options={[{ label: "风险：全部", value: "" }, ...sensitiveRiskOptions]}
                      onChange={(value) => {
                        setTermPage(1);
                        setTermRiskFilter(value);
                      }}
                    />
                    <Select
                      className="min-w-[160px]"
                      value={termStatusFilter}
                      options={termStatusOptions}
                      onChange={(value) => {
                        setTermPage(1);
                        setTermStatusFilter(value);
                      }}
                    />
                    {(termKeyword || termTypeFilter || termRiskFilter || termStatusFilter) ? (
                      <Button className="!h-11 !rounded-2xl !border-slate-200" onClick={handleResetTermFilters}>
                        清空筛选
                      </Button>
                    ) : null}
                  </AdminFilterBar>

                  <AdminSurfaceCard
                    title={`敏感词词库 · ${formatCount(termTotal)} 条`}
                    description="列表优先查看词条策略，新增与编辑统一在右侧抽屉完成。"
                    className="admin-content-table-card"
                    bodyClassName="space-y-4"
                    extra={(
                      <>
                        {selectedTermIds.length ? (
                          <div className="rounded-full bg-slate-100 px-3 py-2 text-xs font-semibold text-slate-500">
                            已选 {formatCount(selectedTermIds.length)} 条
                          </div>
                        ) : null}
                        {selectedTermIds.length ? (
                          <Button className="!h-11 !rounded-2xl !border-slate-200" loading={termBatchSaving} onClick={() => void handleBatchUpdateTerms(true)}>
                            批量启用
                          </Button>
                        ) : null}
                        {selectedTermIds.length ? (
                          <Button className="!h-11 !rounded-2xl !border-slate-200" loading={termBatchSaving} onClick={() => void handleBatchUpdateTerms(false)}>
                            批量停用
                          </Button>
                        ) : null}
                        {selectedTermIds.length ? (
                          <Button danger className="!h-11 !rounded-2xl" loading={termBatchDeleting} onClick={() => void handleBatchDeleteTerms()}>
                            <Trash2 size={15} className="mr-2" />
                            批量删除
                          </Button>
                        ) : null}
                        <Button className="!h-11 !rounded-2xl !border-slate-200" loading={termImporting} onClick={() => importInputRef.current?.click()}>
                          <Upload size={15} className="mr-2" />
                          导入
                        </Button>
                        <Button className="!h-11 !rounded-2xl !border-slate-200" loading={termExporting} onClick={() => void handleExportTerms()}>
                          <Download size={15} className="mr-2" />
                          导出
                        </Button>
                        <Button
                          type="primary"
                          className="!h-11 !rounded-2xl !border-none !bg-[#4647d3] !shadow-none"
                          onClick={() => openTermEditor()}
                        >
                          新增词条
                        </Button>
                      </>
                    )}
                  >
                    {termsError ? <Alert type="error" showIcon className="mb-4 rounded-2xl" message={termsError} /> : null}
                    <input
                      ref={importInputRef}
                      type="file"
                      accept=".csv,.txt,.text"
                      className="hidden"
                      onChange={(event) => {
                        const file = event.target.files?.[0];
                        if (file) {
                          handleSelectTermImportFile(file);
                        }
                      }}
                    />
                    <Table<SensitiveTermItem>
                      className="admin-content-table"
                      rowKey="termId"
                      loading={termsLoading}
                      tableLayout="auto"
                      rowSelection={{
                        selectedRowKeys: selectedTermIds,
                        onChange: (keys) => setSelectedTermIds(keys as number[]),
                      }}
                      columns={termColumns}
                      dataSource={sensitiveTerms}
                      pagination={{
                        current: termPage,
                        pageSize: termPageSize,
                        total: termTotal,
                        showSizeChanger: true,
                        pageSizeOptions: ["10", "20", "50"],
                        locale: contentTablePaginationLocale,
                        onChange: (page, pageSize) => {
                          setTermPage(page);
                          setTermPageSize(pageSize);
                        },
                      }}
                      onRow={(record) => ({
                        onClick: () => openTermDrawer(record.termId),
                      })}
                      rowClassName={(record) => joinAdminClassNames(
                        "cursor-pointer transition-colors hover:!bg-slate-50",
                        record.termId === focusedTermId && "admin-content-table-row-active",
                      )}
                    />
                  </AdminSurfaceCard>
                </div>
      )}

      {reportDrawerOpen
      || reviewDrawerOpen
      || auditDrawerOpen
      || termDrawerOpen
      || reportDecisionOpen
      || reviewDecisionOpen
      || termImportConfigOpen
      || termEditorOpen ? (
        <Suspense fallback={null}>
          <AdminContentModerationOverlays
            context={{
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
              moderationReasonLabelMap,
              auditActionLabelMap,
            }}
          />
        </Suspense>
      ) : null}

      {aiTraceModalOpen ? (
        <Suspense fallback={null}>
          <AdminContentAiTraceModal
            context={{
              aiTraceModalOpen,
              setAiTraceModalOpen,
              activeAiTraceId,
              aiTraceLoading,
              loadAiTraceLogs,
              formatCount,
              aiTraceTotal,
              aiTraceError,
              aiTraceLogs,
              formatDateTime,
              getReadableCodeLabel,
              gatewayTaskTypeLabelMap,
              getAiLogStatusTag,
              selectedAiTraceLogId,
              setSelectedAiTraceLogId,
              selectedAiTraceLog,
              aiTraceDetailLoading,
              aiTraceDetailError,
              aiTraceDetail,
              formatCny,
              safePrettifyJson,
              openUserDetail: (userId: number) => navigate(`/admin/users/${userId}`),
            }}
          />
        </Suspense>
      ) : null}

    </AdminPageFrame>
  );
}
