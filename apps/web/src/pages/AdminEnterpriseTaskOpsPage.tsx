import { lazy, Suspense, useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import {
  Alert,
  Button,
  Empty,
  Input,
  Popconfirm,
  Select,
  Table,
  Tag,
  Typography,
  message,
  type TableColumnsType,
} from "antd";
import {
  AlertTriangle,
  BriefcaseBusiness,
  CheckCircle2,
  Clock3,
  FolderKanban,
  RefreshCcw,
  ShieldAlert,
} from "lucide-react";
import { AdminAnimatedNumber, canAnimateAdminValue } from "../components/admin/AdminAnimatedNumber";
import { useLatestRequest } from "../hooks/useLatestRequest";
import { buildAdminStaleCacheKey, useAdminStaleCache } from "../hooks/useAdminStaleCache";
import { ApiClientError, apiRequest, buildQuery, isAbortError } from "../lib/apiClient";
import {
  approvalStatusLabelMap,
  bountyTaskStatusLabelMap,
  getLabel,
  riskLevelLabelMap,
} from "../lib/adminLabels";
import { formatCount, formatDateTime, formatPercent, formatRelativeTime } from "../lib/formatters";
import { AdminFilterBar, AdminPageFrame } from "../components/admin/AdminOpsPrimitives";
import AdminIdentityAvatar from "../components/admin/AdminIdentityAvatar";

const AdminEnterpriseTaskDetailModal = lazy(() => import("../components/admin/overlays/AdminEnterpriseTaskDetailModal"));

const { Title, Paragraph } = Typography;
const { Search: SearchInput } = Input;

type TimeValue = number | string | null;

type EnterpriseTaskOpsOverviewPayload = {
  totalTaskCount: number;
  openTaskCount: number;
  riskyTaskCount: number;
  highRiskTaskCount: number;
  staleReviewTaskCount: number;
  deadlinePressureTaskCount: number;
  closedWithoutAcceptedTaskCount: number;
};

type RiskSignalItem = {
  code: string;
  level: string;
  label: string;
  description: string;
};

type EnterpriseTaskOpsRecord = {
  taskId: number;
  enterpriseUserId: number;
  enterpriseName: string;
  enterpriseLogoUrl: string | null;
  enterpriseApprovalStatus: string;
  title: string;
  descriptionSummary: string;
  descriptionPreview: string;
  rewardDescription: string;
  status: string;
  acceptedSubmissionId: number | null;
  submissionCount: number;
  pendingSubmissionCount: number;
  acceptedSubmissionCount: number;
  rejectedSubmissionCount: number;
  reviewedSubmissionCount: number;
  deadlineAt: TimeValue;
  closedAt: TimeValue;
  createdAt: TimeValue;
  updatedAt: TimeValue;
  latestSubmissionAt: TimeValue;
  highestRiskLevel: string | null;
  riskSignals: RiskSignalItem[];
};

type EnterpriseTaskOpsListPayload = {
  records: EnterpriseTaskOpsRecord[];
  total: number;
  page: number;
  size: number;
};

type EnterpriseTaskListCachePayload = {
  records: EnterpriseTaskOpsRecord[];
  total: number;
  selectedTaskId: number | null;
};

type EnterpriseTaskOpsDetailPayload = EnterpriseTaskOpsRecord;

type BountyTaskManageResponse = {
  taskId: number;
  status: string;
  updatedAt: TimeValue;
};

type SummaryCardTone = "sky" | "emerald" | "rose" | "amber" | "indigo";

type SummaryCardItem = {
  key: string;
  title: string;
  value: ReactNode;
  note: ReactNode;
  icon: typeof FolderKanban;
  tone: SummaryCardTone;
};

const statusOptions = [
  { label: "任务状态：全部", value: "" },
  { label: getLabel("OPEN", bountyTaskStatusLabelMap, "招募中"), value: "OPEN" },
  { label: getLabel("CLOSED", bountyTaskStatusLabelMap, "已关闭"), value: "CLOSED" },
];

const riskLevelOptions = [
  { label: "风险等级：全部", value: "" },
  { label: getLabel("CRITICAL", riskLevelLabelMap, "极高风险"), value: "CRITICAL" },
  { label: getLabel("HIGH", riskLevelLabelMap, "高风险"), value: "HIGH" },
  { label: getLabel("MEDIUM", riskLevelLabelMap, "中风险"), value: "MEDIUM" },
  { label: getLabel("LOW", riskLevelLabelMap, "低风险"), value: "LOW" },
];

function joinClassNames(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function getToneClasses(tone: SummaryCardTone) {
  switch (tone) {
    case "emerald":
      return {
        icon: "bg-emerald-50 text-emerald-600",
        line: "from-emerald-400 to-emerald-500",
        note: "text-emerald-700",
      };
    case "rose":
      return {
        icon: "bg-rose-50 text-rose-600",
        line: "from-rose-400 to-rose-500",
        note: "text-rose-700",
      };
    case "amber":
      return {
        icon: "bg-amber-50 text-amber-600",
        line: "from-amber-400 to-amber-500",
        note: "text-amber-700",
      };
    case "indigo":
      return {
        icon: "bg-indigo-50 text-indigo-600",
        line: "from-indigo-400 to-indigo-500",
        note: "text-indigo-700",
      };
    case "sky":
    default:
      return {
        icon: "bg-sky-50 text-sky-600",
        line: "from-sky-400 to-cyan-400",
        note: "text-sky-700",
      };
  }
}

function getApprovalTag(status: string) {
  if (status === "APPROVED") {
    return <Tag color="success">{getLabel(status, approvalStatusLabelMap, status)}</Tag>;
  }
  if (status === "REJECTED") {
    return <Tag color="error">{getLabel(status, approvalStatusLabelMap, status)}</Tag>;
  }
  return <Tag color="warning">{getLabel(status, approvalStatusLabelMap, status)}</Tag>;
}

function getTaskStatusTag(status: string) {
  if (status === "OPEN") {
    return <Tag color="processing">{getLabel(status, bountyTaskStatusLabelMap, status)}</Tag>;
  }
  return <Tag>{getLabel(status, bountyTaskStatusLabelMap, status)}</Tag>;
}

function getRiskTag(level: string | null | undefined) {
  if (!level) {
    return <Tag color="success">当前平稳</Tag>;
  }
  if (level === "CRITICAL") {
    return <Tag color="error">{getLabel(level, riskLevelLabelMap, level)}</Tag>;
  }
  if (level === "HIGH") {
    return <Tag color="volcano">{getLabel(level, riskLevelLabelMap, level)}</Tag>;
  }
  if (level === "MEDIUM") {
    return <Tag color="warning">{getLabel(level, riskLevelLabelMap, level)}</Tag>;
  }
  return <Tag>{getLabel(level, riskLevelLabelMap, level)}</Tag>;
}

function getRiskSignalTag(signal: RiskSignalItem) {
  if (signal.level === "CRITICAL") {
    return <Tag color="error">{signal.label}</Tag>;
  }
  if (signal.level === "HIGH") {
    return <Tag color="volcano">{signal.label}</Tag>;
  }
  if (signal.level === "MEDIUM") {
    return <Tag color="warning">{signal.label}</Tag>;
  }
  return <Tag>{signal.label}</Tag>;
}

function buildTaskAction(record: EnterpriseTaskOpsRecord) {
  if (record.status === "OPEN") {
    return {
      action: "CLOSE",
      label: "关闭任务",
      confirmTitle: "确认关闭该任务？",
      confirmDescription: "关闭后学生无法继续提交，适合平台发现异常时先行止损。",
    } as const;
  }
  return {
    action: "REOPEN",
    label: "重新开放",
    confirmTitle: "确认重新开放该任务？",
    confirmDescription: "恢复开放后学生可以继续提交；已采纳结果的任务不可重开。",
  } as const;
}

function SummaryCard({ item }: { item: SummaryCardItem }) {
  const tone = getToneClasses(item.tone);
  const Icon = item.icon;
  const renderedValue = canAnimateAdminValue(item.value) ? <AdminAnimatedNumber value={item.value} /> : item.value;

  return (
    <div className="relative overflow-hidden rounded-[28px] border border-slate-200/70 bg-white p-6 shadow-[0_16px_40px_rgba(15,23,42,0.06)]">
      <div className={joinClassNames("absolute inset-x-0 top-0 h-1 bg-gradient-to-r", tone.line)} />
      <div className="flex items-center gap-4">
        <div className={joinClassNames("flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl", tone.icon)}>
          <Icon size={20} />
        </div>
        <div className="admin-typography-card-title min-w-0 text-slate-700">{item.title}</div>
      </div>
      <div className="mt-5">
        <div className="admin-typography-card-value font-['Manrope'] text-slate-900">
          {renderedValue}
        </div>
        <div className="admin-typography-card-note mt-2 text-slate-500">{item.note}</div>
      </div>
    </div>
  );
}

export default function AdminEnterpriseTaskOpsPage() {
  const createOverviewRequest = useLatestRequest();
  const createListRequest = useLatestRequest();
  const createDetailRequest = useLatestRequest();
  const [keywordDraft, setKeywordDraft] = useState("");
  const [keyword, setKeyword] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [riskLevelFilter, setRiskLevelFilter] = useState("");
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  // 后台列表使用 stale cache，切回页面时先展示旧结果，再按最新筛选刷新。
  const overviewCache = useAdminStaleCache<EnterpriseTaskOpsOverviewPayload>("admin-enterprise-tasks:overview");
  const listCache = useAdminStaleCache<EnterpriseTaskListCachePayload>(
    buildAdminStaleCacheKey("admin-enterprise-tasks:list", {
      page,
      pageSize,
      keyword,
      statusFilter,
      riskLevelFilter,
    }),
  );
  const [overview, setOverview] = useState<EnterpriseTaskOpsOverviewPayload | null>(() => overviewCache.cached);
  const [records, setRecords] = useState<EnterpriseTaskOpsRecord[]>(() => listCache.cached?.records ?? []);
  const [detailRecord, setDetailRecord] = useState<EnterpriseTaskOpsDetailPayload | null>(null);
  const [overviewLoading, setOverviewLoading] = useState(() => !overviewCache.hasCache);
  const [listLoading, setListLoading] = useState(() => !listCache.hasCache);
  const [detailLoading, setDetailLoading] = useState(false);
  const [overviewError, setOverviewError] = useState<string | null>(null);
  const [listError, setListError] = useState<string | null>(null);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [total, setTotal] = useState(() => listCache.cached?.total ?? 0);
  const [selectedTaskId, setSelectedTaskId] = useState<number | null>(() => listCache.cached?.selectedTaskId ?? null);
  const [managingTaskId, setManagingTaskId] = useState<number | null>(null);
  const [detailModalOpen, setDetailModalOpen] = useState(false);

  const loadOverview = useCallback(async (showLoading = true) => {
    const request = createOverviewRequest();
    if (showLoading) {
      setOverviewLoading(true);
    }
    setOverviewError(null);
    try {
      // 概览卡由后端统一计算风险数量，前端不重复推导平台级口径。
      const payload = await apiRequest<EnterpriseTaskOpsOverviewPayload>("/admin/enterprise/tasks/overview", {
        signal: request.signal,
      });
      if (!request.isCurrent()) {
        return;
      }
      setOverview(payload);
      overviewCache.write(payload);
    } catch (error) {
      if (isAbortError(error) || !request.isCurrent()) {
        return;
      }
      const apiError = error as ApiClientError;
      setOverviewError(apiError.message || "加载企业任务概览失败");
    } finally {
      if (request.isCurrent()) {
        setOverviewLoading(false);
      }
    }
  }, [createOverviewRequest, overviewCache]);

  const loadTasks = useCallback(async (showLoading = true) => {
    const request = createListRequest();
    if (showLoading) {
      setListLoading(true);
    }
    setListError(null);
    try {
      // 列表筛选保持服务端分页，避免后台大表在前端一次性展开。
      const query = buildQuery({
        page,
        size: pageSize,
        keyword: keyword || undefined,
        status: statusFilter || undefined,
        riskLevel: riskLevelFilter || undefined,
      });
      const payload = await apiRequest<EnterpriseTaskOpsListPayload>(`/admin/enterprise/tasks${query}`, {
        signal: request.signal,
      });
      if (!request.isCurrent()) {
        return;
      }
      setRecords(payload.records);
      setTotal(payload.total);
      // 当前选中项不在新结果里时，默认落到第一页第一条，详情弹层再按需补拉。
      const nextSelectedTaskId = payload.records.length === 0
        ? null
        : payload.records.some((item) => item.taskId === selectedTaskId)
          ? selectedTaskId
          : payload.records[0].taskId;
      setSelectedTaskId(() => nextSelectedTaskId);
      listCache.write({
        records: payload.records,
        total: payload.total,
        selectedTaskId: nextSelectedTaskId,
      });
    } catch (error) {
      if (isAbortError(error) || !request.isCurrent()) {
        return;
      }
      const apiError = error as ApiClientError;
      setListError(apiError.message || "加载企业任务列表失败");
      message.error(apiError.message || "加载企业任务列表失败");
    } finally {
      if (request.isCurrent()) {
        setListLoading(false);
      }
    }
  }, [createListRequest, keyword, listCache, page, pageSize, riskLevelFilter, selectedTaskId, statusFilter]);

  useEffect(() => {
    if (!overviewCache.cached) {
      return;
    }
    setOverview(overviewCache.cached);
  }, [overviewCache.cached]);

  useEffect(() => {
    if (!listCache.cached) {
      return;
    }
    setRecords(listCache.cached.records);
    setTotal(listCache.cached.total);
    setSelectedTaskId(listCache.cached.selectedTaskId);
  }, [listCache.cached]);

  useEffect(() => {
    void loadOverview(!overviewCache.hasCache);
  }, [loadOverview, overviewCache.hasCache]);

  useEffect(() => {
    void loadTasks(!listCache.hasCache);
  }, [listCache.hasCache, loadTasks]);

  const selectedListRecord = useMemo(
    () => records.find((item) => item.taskId === selectedTaskId) ?? null,
    [records, selectedTaskId],
  );

  const selectedRecord = detailRecord ?? selectedListRecord;

  const syncRecordIntoList = useCallback((nextRecord: EnterpriseTaskOpsDetailPayload) => {
    // 详情接口返回的风险信号更完整，同步回列表保持弹层关闭后的状态一致。
    setRecords((current) => current.map((item) => (item.taskId === nextRecord.taskId ? nextRecord : item)));
  }, []);

  const loadTaskDetail = useCallback(async (taskId: number) => {
    const request = createDetailRequest();
    setDetailLoading(true);
    setDetailError(null);
    try {
      // 详情只在打开弹层或刷新时读取，减少后台表格初次加载压力。
      const payload = await apiRequest<EnterpriseTaskOpsDetailPayload>(`/admin/enterprise/tasks/${taskId}`, {
        signal: request.signal,
      });
      if (!request.isCurrent()) {
        return;
      }
      setDetailRecord(payload);
      syncRecordIntoList(payload);
    } catch (error) {
      if (isAbortError(error) || !request.isCurrent()) {
        return;
      }
      const apiError = error as ApiClientError;
      setDetailError(apiError.message || "加载任务详情失败");
      message.error(apiError.message || "加载任务详情失败");
    } finally {
      if (request.isCurrent()) {
        setDetailLoading(false);
      }
    }
  }, [createDetailRequest, syncRecordIntoList]);

  const selectedInspectionSummary = useMemo(() => {
    if (!selectedRecord) {
      return null;
    }
    // 平台建议是前端解释层，用于辅助人工巡检，不直接驱动后端处置。
    const submissionCount = selectedRecord.submissionCount;
    const pendingSubmissionCount = selectedRecord.pendingSubmissionCount;
    const reviewedSubmissionCount = selectedRecord.reviewedSubmissionCount;
    const acceptedSubmissionCount = selectedRecord.acceptedSubmissionCount;
    const reviewCoverage = formatPercent(reviewedSubmissionCount, submissionCount, 1);
    const reviewCoverageValue = submissionCount > 0
      ? Math.min(100, Math.max(0, (reviewedSubmissionCount / submissionCount) * 100))
      : 0;
    const hasPendingQueue = pendingSubmissionCount > 0;
    const closedWithoutAccepted =
      selectedRecord.status === "CLOSED" &&
      submissionCount > 0 &&
      selectedRecord.acceptedSubmissionId == null &&
      acceptedSubmissionCount <= 0;

    let recommendation = "当前提交流转已基本收口，可继续观察企业后续处理与任务关闭节奏。";
    if (hasPendingQueue && pendingSubmissionCount >= 3) {
      recommendation = "待处理提交较多，建议优先回查企业审核节奏，并确认学生是否已收到结果通知。";
    } else if (closedWithoutAccepted) {
      recommendation = "任务已关闭但没有明确采纳结果，建议优先核查企业反馈与通知结果，必要时平台介入补发。";
    } else if (submissionCount === 0 && selectedRecord.status === "OPEN") {
      recommendation = "当前任务尚无学生提交，建议结合截止时间和企业认证状态，判断是否需要提前下架或调整。";
    } else if (selectedRecord.enterpriseApprovalStatus !== "APPROVED") {
      recommendation = "企业认证尚未完全收口，建议先回查认证审核结果，再继续观察任务交付质量。";
    }

    return {
      pendingSubmissionCount,
      reviewCoverage,
      reviewCoverageValue,
      closedWithoutAccepted,
      hasPendingQueue,
      recommendation,
    };
  }, [selectedRecord]);

  const summaryCards = useMemo<SummaryCardItem[]>(() => [
    {
      key: "totalTaskCount",
      title: "平台任务总数",
      value: formatCount(overview?.totalTaskCount ?? 0),
      note: "当前纳入平台巡检的企业任务总量",
      icon: FolderKanban,
      tone: "sky",
    },
    {
      key: "openTaskCount",
      title: "招募中任务",
      value: formatCount(overview?.openTaskCount ?? 0),
      note: "仍允许学生继续提交的开放任务",
      icon: BriefcaseBusiness,
      tone: "emerald",
    },
    {
      key: "riskyTaskCount",
      title: "需要介入",
      value: formatCount(overview?.riskyTaskCount ?? 0),
      note: `其中高风险 ${formatCount(overview?.highRiskTaskCount ?? 0)} 项`,
      icon: AlertTriangle,
      tone: "rose",
    },
    {
      key: "staleReviewTaskCount",
      title: "提交积压",
      value: formatCount(overview?.staleReviewTaskCount ?? 0),
      note: "多份待处理提交超过 72 小时未收口",
      icon: Clock3,
      tone: "amber",
    },
    {
      key: "deadlinePressureTaskCount",
      title: "临期压力",
      value: formatCount(overview?.deadlinePressureTaskCount ?? 0),
      note: "已超截止或临期零提交任务",
      icon: ShieldAlert,
      tone: "indigo",
    },
    {
      key: "closedWithoutAcceptedTaskCount",
      title: "关闭未采纳",
      value: formatCount(overview?.closedWithoutAcceptedTaskCount ?? 0),
      note: "存在提交但仍未明确采纳结果的关闭任务",
      icon: CheckCircle2,
      tone: "sky",
    },
  ], [overview]);

  const handleRefresh = useCallback(() => {
    void Promise.all([loadOverview(), loadTasks()]);
  }, [loadOverview, loadTasks]);

  const handleRefreshDetail = useCallback(() => {
    if (selectedTaskId == null) {
      return;
    }
    void loadTaskDetail(selectedTaskId);
  }, [loadTaskDetail, selectedTaskId]);

  const handleManageTask = useCallback(
    async (record: EnterpriseTaskOpsRecord, action: "CLOSE" | "REOPEN") => {
      setManagingTaskId(record.taskId);
      try {
        // 平台关闭/重开是最小干预动作，成功后刷新概览、列表和当前详情三处数据。
        const response = await apiRequest<BountyTaskManageResponse>(`/admin/enterprise/tasks/${record.taskId}/manage`, {
          method: "POST",
          body: JSON.stringify({ action }),
        });
        message.success(
          `任务 #${response.taskId} 已切换为 ${getLabel(response.status, bountyTaskStatusLabelMap, response.status)}`,
        );
        await Promise.all([loadOverview(), loadTasks(), loadTaskDetail(record.taskId)]);
      } catch (error) {
        const apiError = error as ApiClientError;
        message.error(apiError.message || "更新任务状态失败");
      } finally {
        setManagingTaskId(null);
      }
    },
    [loadOverview, loadTaskDetail, loadTasks],
  );

  const handleOpenTaskDetail = useCallback((record: EnterpriseTaskOpsRecord) => {
    // 先用表格行快照打开弹层，再异步补充提交摘要和风险明细。
    setSelectedTaskId(record.taskId);
    setDetailRecord(record);
    setDetailError(null);
    setDetailModalOpen(true);
    void loadTaskDetail(record.taskId);
  }, [loadTaskDetail]);

  const columns = useMemo<TableColumnsType<EnterpriseTaskOpsRecord>>(
    () => [
      {
        title: "任务",
        dataIndex: "title",
        key: "title",
        width: 286,
        render: (_value, record) => (
          <div className="space-y-2">
            <button
              type="button"
              className="text-left font-['Manrope'] text-base font-extrabold tracking-tight text-slate-900 transition-colors hover:text-indigo-600"
              onClick={() => handleOpenTaskDetail(record)}
            >
              {record.title}
            </button>
            <div className="line-clamp-2 text-sm leading-6 text-slate-500">{record.descriptionSummary}</div>
            <div className="text-xs text-slate-400">
              创建于 {formatDateTime(record.createdAt, "待补充")} · 奖励 {record.rewardDescription?.trim() || "待补充"}
            </div>
          </div>
        ),
      },
      {
        title: "关联企业",
        dataIndex: "enterpriseName",
        key: "enterpriseName",
        width: 196,
        render: (_value, record) => (
          <div className="flex items-start gap-3">
            <AdminIdentityAvatar
              role="ENTERPRISE"
              userId={record.enterpriseUserId}
              displayName={record.enterpriseName}
              enterpriseName={record.enterpriseName}
              enterpriseLogoUrl={record.enterpriseLogoUrl}
              className="!h-11 !w-11 !min-w-11 !shrink-0"
              textClassName="text-sm"
            />
            <div className="space-y-1">
              <div className="text-xs font-semibold text-slate-400">企业 #{record.enterpriseUserId}</div>
              <div className="font-semibold text-slate-900">{record.enterpriseName}</div>
              <div className="flex flex-wrap items-center gap-2">
                {getApprovalTag(record.enterpriseApprovalStatus)}
              </div>
            </div>
          </div>
        ),
      },
      {
        title: "状态",
        key: "status",
        width: 180,
        render: (_value, record) => (
          <div className="space-y-2">
            <div className="flex flex-wrap items-center gap-2">
              {getTaskStatusTag(record.status)}
              {getRiskTag(record.highestRiskLevel)}
            </div>
            <div className="text-xs text-slate-500">截止 {formatDateTime(record.deadlineAt, "未设置")}</div>
            <div className="text-xs text-slate-400">最近更新 {formatRelativeTime(record.updatedAt, "刚刚")}</div>
          </div>
        ),
      },
      {
        title: "提交进度",
        key: "submissions",
        width: 164,
        render: (_value, record) => (
          <div className="space-y-1.5 text-sm text-slate-600">
            <div>总提交 {formatCount(record.submissionCount)} 份</div>
            <div>待处理 {formatCount(record.pendingSubmissionCount)} 份</div>
            <div>已采纳 {formatCount(record.acceptedSubmissionCount)} 份</div>
            <div>已驳回 {formatCount(record.rejectedSubmissionCount)} 份</div>
          </div>
        ),
      },
      {
        title: "风险信号",
        key: "riskSignals",
        width: 246,
        render: (_value, record) => (
          <div className="space-y-2">
            {record.riskSignals.length === 0 ? (
              <span className="text-sm text-slate-400">-</span>
            ) : (
              <>
                <div className="flex flex-wrap gap-2">
                  {record.riskSignals.slice(0, 2).map((signal) => (
                    <span key={signal.code}>{getRiskSignalTag(signal)}</span>
                  ))}
                  {record.riskSignals.length > 2 ? <Tag>+{record.riskSignals.length - 2}</Tag> : null}
                </div>
                <div className="line-clamp-2 text-xs leading-5 text-slate-500">{record.riskSignals[0]?.description}</div>
              </>
            )}
          </div>
        ),
      },
      {
        title: "平台动作",
        key: "actions",
        width: 150,
        render: (_value, record) => {
          const action = buildTaskAction(record);
          const disabled = action.action === "REOPEN" && record.acceptedSubmissionId != null;
          const actionButtonClassName = disabled
            ? "!h-8 !rounded-full !border-none !bg-slate-200 !px-4 !font-semibold !text-slate-400 !shadow-none hover:!bg-slate-200 hover:!text-slate-400"
            : record.status === "OPEN"
              ? "!h-8 !rounded-full !border-none !bg-[linear-gradient(135deg,#e11d48,#be123c)] !px-4 !font-semibold !text-white !shadow-[0_8px_18px_rgba(190,24,93,0.22)] hover:!bg-[linear-gradient(135deg,#e11d48,#be123c)] hover:!text-white"
              : "!h-8 !rounded-full !border-none !bg-[linear-gradient(135deg,#059669,#047857)] !px-4 !font-semibold !text-white !shadow-[0_8px_18px_rgba(4,120,87,0.18)] hover:!bg-[linear-gradient(135deg,#059669,#047857)] hover:!text-white";

          return (
            <div className="space-y-2">
              <Popconfirm
                title={action.confirmTitle}
                description={action.confirmDescription}
                okText="确认"
                cancelText="取消"
                disabled={disabled}
                onConfirm={() => handleManageTask(record, action.action)}
              >
                <Button
                  size="small"
                  loading={managingTaskId === record.taskId}
                  disabled={disabled}
                  className={actionButtonClassName}
                >
                  {action.label}
                </Button>
              </Popconfirm>
              <div>
                <Button
                  type="link"
                  size="small"
                  className="!h-auto !px-0 !font-semibold !text-indigo-600 hover:!text-indigo-700"
                  onClick={() => handleOpenTaskDetail(record)}
                >
                  查看详情
                </Button>
              </div>
              {disabled ? <div className="text-xs text-slate-400">已采纳结果的任务不可重开</div> : null}
            </div>
          );
        },
      },
    ],
    [handleManageTask, handleOpenTaskDetail, managingTaskId],
  );

  const pageRefreshLoading = overviewLoading || listLoading;

  return (
    <AdminPageFrame className="space-y-8">
      <section className="flex flex-col gap-5 xl:flex-row xl:items-end xl:justify-between">
        <div className="max-w-3xl">
          <span className="admin-typography-section-label inline-flex rounded-full bg-sky-100 px-3 py-1 text-sky-700">
            ENTERPRISE TASK OPS
          </span>
          <Title level={2} className="admin-typography-hero-title !mb-3 !mt-5 !font-['Manrope'] !text-slate-900">
            企业任务治理
          </Title>
          <Paragraph className="admin-typography-hero-description !mb-0 !max-w-3xl !text-slate-500">
            查看企业任务状态、提交积压与风险信号。
          </Paragraph>
        </div>
        <div className="flex flex-wrap gap-3">
          <Button className="!h-11 !rounded-2xl !border-slate-200 !px-5 !shadow-none" onClick={handleRefresh} disabled={pageRefreshLoading}>
            <RefreshCcw size={16} className={pageRefreshLoading ? "mr-2 animate-spin" : "mr-2"} />
            刷新数据
          </Button>
        </div>
      </section>

      {overviewError ? <Alert type="error" showIcon message={overviewError} className="rounded-3xl" /> : null}

      <div className="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-6">
        {summaryCards.map((item) => <SummaryCard key={item.key} item={item} />)}
      </div>

      <div className="space-y-5">
        <AdminFilterBar>
          <SearchInput
            allowClear
            placeholder="搜索任务名称、ID 或企业"
            className="min-w-[280px] flex-1"
            value={keywordDraft}
            onChange={(event) => setKeywordDraft(event.target.value)}
            onSearch={(value) => {
              setPage(1);
              setKeyword(value.trim());
            }}
          />
          <Select
            className="min-w-[170px]"
            value={statusFilter}
            options={statusOptions}
            onChange={(value) => {
              setPage(1);
              setStatusFilter(value);
            }}
          />
          <Select
            className="min-w-[180px]"
            value={riskLevelFilter}
            options={riskLevelOptions}
            onChange={(value) => {
              setPage(1);
              setRiskLevelFilter(value);
            }}
          />
          {(keyword || statusFilter || riskLevelFilter) ? (
            <Button
              className="!h-11 !rounded-2xl !border-slate-200"
              onClick={() => {
                setKeywordDraft("");
                setKeyword("");
                setStatusFilter("");
                setRiskLevelFilter("");
                setPage(1);
              }}
            >
              清空筛选
            </Button>
          ) : null}
        </AdminFilterBar>

        <div className="rounded-[32px] border border-slate-200/70 bg-white p-6 shadow-none">
          <div className="mb-5 flex items-center justify-between gap-3">
            <div className="admin-typography-surface-title font-['Manrope'] text-slate-900">任务列表</div>
            <div className="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-500">
              {formatCount(total)} 条
            </div>
          </div>

          {listError ? <Alert type="error" showIcon message={listError} className="mb-4 rounded-2xl" /> : null}

          <Table<EnterpriseTaskOpsRecord>
            rowKey="taskId"
            loading={listLoading}
            columns={columns}
            dataSource={records}
            pagination={{
              current: page,
              pageSize,
              total,
              showSizeChanger: true,
              pageSizeOptions: ["10", "20", "50"],
              onChange: (nextPage, nextPageSize) => {
                setPage(nextPage);
                setPageSize(nextPageSize);
              },
            }}
            locale={{
              emptyText: (
                <Empty
                  description="当前筛选条件下没有企业任务"
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                />
              ),
            }}
            onRow={(record) => ({
              onClick: () => handleOpenTaskDetail(record),
            })}
            rowClassName={(record) => joinClassNames(
              "cursor-pointer transition-colors hover:!bg-slate-50",
              record.taskId === selectedTaskId && detailModalOpen && "!bg-sky-50/80",
            )}
            scroll={{ x: 1160 }}
          />
        </div>
      </div>

      {detailModalOpen ? (
        <Suspense fallback={null}>
          <AdminEnterpriseTaskDetailModal
            context={{
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
            }}
          />
        </Suspense>
      ) : null}
    </AdminPageFrame>
  );
}
