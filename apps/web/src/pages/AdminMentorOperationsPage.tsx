import { lazy, Suspense, useCallback, useEffect, useMemo, useState } from "react";
import {
  Alert,
  Button,
  Empty,
  Input,
  Popconfirm,
  Select,
  Table,
  Tag,
  message,
  type TableColumnsType,
} from "antd";
import {
  AlertTriangle,
  Banknote,
  Briefcase,
  Calendar,
  CalendarCheck,
  CheckCircle2,
  ChevronRight,
  GraduationCap,
  History,
  Package,
  RefreshCcw,
  ShieldCheck,
  Star,
  User,
  WalletCards,
  XCircle,
} from "lucide-react";
import { useNavigate } from "react-router-dom";
import { useLatestRequest } from "../hooks/useLatestRequest";
import { buildAdminStaleCacheKey, useAdminStaleCache } from "../hooks/useAdminStaleCache";
import { ApiClientError, apiRequest, buildQuery, isAbortError } from "../lib/apiClient";
import { approvalStatusLabelMap, getLabel, riskLevelLabelMap } from "../lib/adminLabels";
import { formatCount, formatDateTime, formatMoneyFen, formatRelativeTime } from "../lib/formatters";
import {
  AdminDetailPlaceholder,
  AdminFilterBar,
  AdminMetricCard,
  AdminPageFrame,
  AdminPageHeader,
  AdminSurfaceCard,
  joinAdminClassNames,
} from "../components/admin/AdminOpsPrimitives";
import AdminIdentityAvatar from "../components/admin/AdminIdentityAvatar";

const AdminMentorDetailModal = lazy(() => import("../components/admin/overlays/AdminMentorDetailModal"));

const { Search } = Input;

type TimeValue = number | string | null;

type MentorOpsOverviewPayload = {
  totalMentorCount: number;
  approvedMentorCount: number;
  pendingApprovalCount: number;
  riskyMentorCount: number;
  scheduleRiskMentorCount: number;
  fulfillmentRiskMentorCount: number;
  pendingWithdrawalMentorCount: number;
  pendingWithdrawalAmountFen: number;
};

type RiskSignalItem = {
  code: string;
  level: string;
  label: string;
  description: string;
};

type MentorOpsRecord = {
  mentorUserId: number;
  displayName: string;
  realName: string | null;
  showRealName: boolean;
  companyName: string | null;
  jobTitle: string | null;
  avatarUrl: string | null;
  approvalStatus: string;
  available: boolean;
  totalPackageCount: number;
  enabledPackageCount: number;
  enabledAppointmentPackageCount: number;
  startingPriceFen: number;
  enabledPackageNames: string[];
  serviceScenes: string[];
  avgRating: number;
  weekAvailableSlotCount: number;
  nextThreeDayAvailableSlotCount: number;
  upcomingBookedSlotCount: number;
  nextAvailableAt: TimeValue;
  nextBookedAt: TimeValue;
  totalOrderCount: number;
  paidOrderCount: number;
  answeredOrderCount: number;
  closedOrderCount: number;
  refundedOrderCount: number;
  overdueReplyOrderCount: number;
  expiringReplyOrderCount: number;
  pendingAfterSalesCount: number;
  afterSalesImpactCount: number;
  latestOrderActivityAt: TimeValue;
  pendingWithdrawalCount: number;
  processingWithdrawalCount: number;
  completedWithdrawalCount: number;
  rejectedWithdrawalCount: number;
  openWithdrawalCount: number;
  openWithdrawalAmountFen: number;
  latestWithdrawalId: number | null;
  latestWithdrawalAmountFen: number | null;
  latestWithdrawalStatus: string | null;
  latestWithdrawalNote: string | null;
  latestWithdrawalCreatedAt: TimeValue;
  latestWithdrawalUpdatedAt: TimeValue;
  profileUpdatedAt: TimeValue;
  highestRiskLevel: string | null;
  riskSignals: RiskSignalItem[];
};

type MentorOpsListPayload = {
  records: MentorOpsRecord[];
  total: number;
};

type MentorOpsListCachePayload = {
  records: MentorOpsRecord[];
  total: number;
  selectedMentorId: number | null;
};

type WithdrawalManageResponse = {
  withdrawalId: number;
  mentorUserId: number;
  status: string;
  updatedAt: TimeValue;
};

const riskLevelOptions = [
  { label: "风险等级：全部", value: "" },
  { label: getLabel("CRITICAL", riskLevelLabelMap, "极高风险"), value: "CRITICAL" },
  { label: getLabel("HIGH", riskLevelLabelMap, "高风险"), value: "HIGH" },
  { label: getLabel("MEDIUM", riskLevelLabelMap, "中风险"), value: "MEDIUM" },
  { label: getLabel("LOW", riskLevelLabelMap, "低风险"), value: "LOW" },
];

const approvalOptions = [
  { label: "认证状态：全部", value: "" },
  { label: getLabel("PENDING", approvalStatusLabelMap, "待认证"), value: "PENDING" },
  { label: getLabel("APPROVED", approvalStatusLabelMap, "已认证"), value: "APPROVED" },
  { label: getLabel("REJECTED", approvalStatusLabelMap, "已驳回"), value: "REJECTED" },
];

const withdrawalStatusLabelMap: Record<string, string> = {
  PENDING: "待打款",
  PROCESSING: "打款中",
  COMPLETED: "已完成",
  REJECTED: "已驳回",
  CANCELED: "已取消",
};

const withdrawalStatusOptions = [
  { label: "提现状态：全部", value: "" },
  { label: getLabel("PENDING", withdrawalStatusLabelMap, "待打款"), value: "PENDING" },
  { label: getLabel("PROCESSING", withdrawalStatusLabelMap, "打款中"), value: "PROCESSING" },
  { label: getLabel("COMPLETED", withdrawalStatusLabelMap, "已完成"), value: "COMPLETED" },
  { label: getLabel("REJECTED", withdrawalStatusLabelMap, "已驳回"), value: "REJECTED" },
];

function formatRating(value: number | null | undefined) {
  if (value == null || Number.isNaN(value)) {
    return "0.0";
  }
  return value.toFixed(1);
}

function buildMentorIdentityLine(record: Pick<MentorOpsRecord, "realName" | "companyName">) {
  return [record.realName || null, record.companyName || "未补充机构"].filter(Boolean).join(" · ");
}

function buildFulfillmentRate(record: Pick<MentorOpsRecord, "totalOrderCount" | "answeredOrderCount">) {
  if (record.totalOrderCount <= 0) {
    return 100;
  }
  return Math.min(100, Math.round((record.answeredOrderCount / record.totalOrderCount) * 1000) / 10);
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

function getAvailabilityTag(available: boolean) {
  return available ? <Tag color="processing">可接单</Tag> : <Tag>暂停接单</Tag>;
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

function getWithdrawalTag(status: string | null | undefined) {
  if (!status) {
    return <Tag>暂无提现</Tag>;
  }
  if (status === "COMPLETED") {
    return <Tag color="success">{getLabel(status, withdrawalStatusLabelMap, status)}</Tag>;
  }
  if (status === "REJECTED") {
    return <Tag color="error">{getLabel(status, withdrawalStatusLabelMap, status)}</Tag>;
  }
  if (status === "PROCESSING") {
    return <Tag color="processing">{getLabel(status, withdrawalStatusLabelMap, status)}</Tag>;
  }
  return <Tag color="warning">{getLabel(status, withdrawalStatusLabelMap, status)}</Tag>;
}

function describeWithdrawalAction(record: MentorOpsRecord) {
  if (!record.latestWithdrawalId || !record.latestWithdrawalStatus) {
    return null;
  }
  if (record.latestWithdrawalStatus === "PENDING") {
    return {
      primary: {
        status: "PROCESSING",
        label: "进入打款中",
        title: "确认进入打款中？",
        description: "该提现申请会被标记为处理中，便于平台继续跟进打款结果。",
      },
      secondary: {
        status: "REJECTED",
        label: "驳回申请",
        title: "确认驳回该提现吗？",
        description: "驳回后该笔提现会退出当前待处理队列，请确认线下沟通已经完成。",
      },
    } as const;
  }
  if (record.latestWithdrawalStatus === "PROCESSING") {
    return {
      primary: {
        status: "COMPLETED",
        label: "标记已完成",
        title: "确认该笔提现已完成？",
        description: "完成后导师财务侧会看到该笔申请已收口。",
      },
      secondary: {
        status: "REJECTED",
        label: "驳回申请",
        title: "确认驳回该提现吗？",
        description: "仅当流程确定无法继续时才使用驳回，请谨慎操作。",
      },
    } as const;
  }
  return null;
}

export default function AdminMentorOperationsPage() {
  const navigate = useNavigate();
  const createOverviewRequest = useLatestRequest();
  const createListRequest = useLatestRequest();
  const [keywordDraft, setKeywordDraft] = useState("");
  const [keyword, setKeyword] = useState("");
  const [approvalStatusFilter, setApprovalStatusFilter] = useState("");
  const [riskLevelFilter, setRiskLevelFilter] = useState("");
  const [withdrawalStatusFilter, setWithdrawalStatusFilter] = useState("");
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const overviewCache = useAdminStaleCache<MentorOpsOverviewPayload>("admin-mentor-operations:overview");
  const listCache = useAdminStaleCache<MentorOpsListCachePayload>(
    buildAdminStaleCacheKey("admin-mentor-operations:list", {
      page,
      pageSize,
      keyword,
      approvalStatusFilter,
      riskLevelFilter,
      withdrawalStatusFilter,
    }),
  );
  const [overview, setOverview] = useState<MentorOpsOverviewPayload | null>(() => overviewCache.cached);
  const [records, setRecords] = useState<MentorOpsRecord[]>(() => listCache.cached?.records ?? []);
  const [overviewLoading, setOverviewLoading] = useState(() => !overviewCache.hasCache);
  const [listLoading, setListLoading] = useState(() => !listCache.hasCache);
  const [overviewError, setOverviewError] = useState<string | null>(null);
  const [listError, setListError] = useState<string | null>(null);
  const [total, setTotal] = useState(() => listCache.cached?.total ?? 0);
  const [selectedMentorId, setSelectedMentorId] = useState<number | null>(() => listCache.cached?.selectedMentorId ?? null);
  const [detailModalOpen, setDetailModalOpen] = useState(false);
  const [managingWithdrawalId, setManagingWithdrawalId] = useState<number | null>(null);

  const loadOverview = useCallback(async (showLoading = true) => {
    const request = createOverviewRequest();
    if (showLoading) {
      setOverviewLoading(true);
    }
    setOverviewError(null);
    try {
      // 导师运营概览独立于列表筛选，作为后台总览和当前页的共同指标源。
      const payload = await apiRequest<MentorOpsOverviewPayload>("/admin/mentors/operations/overview", {
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
      setOverviewError(apiError.message || "加载导师经营概览失败");
    } finally {
      if (request.isCurrent()) {
        setOverviewLoading(false);
      }
    }
  }, [createOverviewRequest, overviewCache]);

  const loadMentors = useCallback(async (showLoading = true) => {
    const request = createListRequest();
    if (showLoading) {
      setListLoading(true);
    }
    setListError(null);
    try {
      // 经营列表由服务端按认证、风险和提现状态筛选，前端保留当前选中导师。
      const query = buildQuery({
        page,
        size: pageSize,
        keyword: keyword || undefined,
        approvalStatus: approvalStatusFilter || undefined,
        riskLevel: riskLevelFilter || undefined,
        withdrawalStatus: withdrawalStatusFilter || undefined,
      });
      const payload = await apiRequest<MentorOpsListPayload>(`/admin/mentors/operations${query}`, {
        signal: request.signal,
      });
      if (!request.isCurrent()) {
        return;
      }
      setRecords(payload.records);
      setTotal(payload.total);
      const nextSelectedMentorId = payload.records.length === 0
        ? null
        : payload.records.some((item) => item.mentorUserId === selectedMentorId)
          ? selectedMentorId
          : payload.records[0].mentorUserId;
      setSelectedMentorId(nextSelectedMentorId);
      listCache.write({
        records: payload.records,
        total: payload.total,
        selectedMentorId: nextSelectedMentorId,
      });
    } catch (error) {
      if (isAbortError(error) || !request.isCurrent()) {
        return;
      }
      const apiError = error as ApiClientError;
      setListError(apiError.message || "加载导师经营列表失败");
    } finally {
      if (request.isCurrent()) {
        setListLoading(false);
      }
    }
  }, [approvalStatusFilter, createListRequest, keyword, listCache, page, pageSize, riskLevelFilter, selectedMentorId, withdrawalStatusFilter]);

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
    setSelectedMentorId(listCache.cached.selectedMentorId);
  }, [listCache.cached]);

  useEffect(() => {
    void loadOverview(!overviewCache.hasCache);
  }, [loadOverview, overviewCache.hasCache]);

  useEffect(() => {
    void loadMentors(!listCache.hasCache);
  }, [listCache.hasCache, loadMentors]);

  const selectedRecord = useMemo(
    () => records.find((item) => item.mentorUserId === selectedMentorId) ?? null,
    [records, selectedMentorId],
  );

  const handleRefresh = useCallback(() => {
    // 刷新时概览和列表一起校准，风险数和表格记录保持同一时间口径。
    void Promise.all([loadOverview(), loadMentors()]);
  }, [loadMentors, loadOverview]);

  const openMentorDetail = useCallback((mentorUserId: number) => {
    setSelectedMentorId(mentorUserId);
    setDetailModalOpen(true);
  }, []);

  const handleManageWithdrawal = useCallback(async (record: MentorOpsRecord, status: string) => {
    if (!record.latestWithdrawalId) {
      return;
    }
    setManagingWithdrawalId(record.latestWithdrawalId);
    try {
      // 后台只推进最新提现申请，具体合法流转由后端财务状态机判断。
      await apiRequest<WithdrawalManageResponse>(`/admin/mentors/operations/withdrawals/${record.latestWithdrawalId}/status`, {
        method: "POST",
        body: JSON.stringify({ status }),
      });
      message.success(`已更新 ${record.displayName} 的提现状态`);
      await Promise.all([loadOverview(), loadMentors()]);
    } catch (error) {
      const apiError = error as ApiClientError;
      message.error(apiError.message || "更新提现状态失败");
    } finally {
      setManagingWithdrawalId(null);
    }
  }, [loadMentors, loadOverview]);

  const summaryCards = useMemo(
    () => [
      {
        key: "totalMentorCount",
        label: "导师总数",
        value: formatCount(overview?.totalMentorCount ?? 0),
        note: `已认证 ${formatCount(overview?.approvedMentorCount ?? 0)} 位导师`,
        icon: GraduationCap,
        tone: "indigo" as const,
      },
      {
        key: "pendingApprovalCount",
        label: "待认证导师",
        value: formatCount(overview?.pendingApprovalCount ?? 0),
        note: "优先联动账号治理和认证补齐",
        icon: ShieldCheck,
        tone: "amber" as const,
      },
      {
        key: "riskyMentorCount",
        label: "高风险导师",
        value: formatCount(overview?.riskyMentorCount ?? 0),
        note: `排期风险 ${formatCount(overview?.scheduleRiskMentorCount ?? 0)} · 履约风险 ${formatCount(overview?.fulfillmentRiskMentorCount ?? 0)}`,
        icon: AlertTriangle,
        tone: "rose" as const,
      },
      {
        key: "pendingWithdrawalAmountFen",
        label: "待处理提现",
        value: formatMoneyFen(overview?.pendingWithdrawalAmountFen ?? 0),
        note: `${formatCount(overview?.pendingWithdrawalMentorCount ?? 0)} 位导师仍在提现队列`,
        icon: WalletCards,
        tone: "teal" as const,
      },
    ],
    [overview],
  );

  const columns = useMemo<TableColumnsType<MentorOpsRecord>>(
    () => [
      {
        title: "导师与经营主体",
        dataIndex: "displayName",
        key: "displayName",
        render: (_value, record) => (
          <div className="flex items-start gap-3">
            <AdminIdentityAvatar
              role="MENTOR"
              userId={record.mentorUserId}
              displayName={record.displayName}
              mentorAvatarUrl={record.avatarUrl}
              className="!h-11 !w-11 !min-w-11 !shrink-0"
              textClassName="text-sm"
            />
            <div className="min-w-0 flex-1 space-y-1">
              <button
                type="button"
                className="block text-left font-['Manrope'] text-base font-extrabold tracking-tight text-slate-900 transition-colors hover:text-indigo-600"
                onClick={() => openMentorDetail(record.mentorUserId)}
              >
                {record.displayName}
              </button>
              <div className="text-sm text-slate-500">{buildMentorIdentityLine(record)}</div>
              <div className="text-sm text-slate-400">{record.jobTitle || "未补充职位"}</div>
              <div className="flex flex-wrap gap-2">
                {getApprovalTag(record.approvalStatus)}
                {getAvailabilityTag(record.available)}
              </div>
            </div>
          </div>
        ),
      },
      {
        title: "套餐供给",
        key: "packages",
        render: (_value, record) => (
          <div className="space-y-2">
            <div className="font-semibold text-slate-900">{formatMoneyFen(record.startingPriceFen)}</div>
            <div className="text-sm text-slate-500">
              启用 {formatCount(record.enabledPackageCount)} / {formatCount(record.totalPackageCount)} 个套餐
            </div>
            <div className="flex flex-wrap gap-2">
              {record.enabledPackageNames.length > 0
                ? record.enabledPackageNames.slice(0, 2).map((item) => <Tag key={`${record.mentorUserId}-${item}`}>{item}</Tag>)
                : <Tag>暂无启用套餐</Tag>}
              {record.enabledAppointmentPackageCount > 0 ? <Tag color="processing">含预约型套餐</Tag> : null}
            </div>
          </div>
        ),
      },
      {
        title: "排期与履约",
        key: "delivery",
        render: (_value, record) => (
          <div className="space-y-1.5 text-sm text-slate-600">
            <div>7 天空档 {formatCount(record.weekAvailableSlotCount)}</div>
            <div>3 天可约 {formatCount(record.nextThreeDayAvailableSlotCount)}</div>
            <div>已预定 {formatCount(record.upcomingBookedSlotCount)}</div>
            <div className="text-xs text-slate-400">活动 {formatRelativeTime(record.latestOrderActivityAt, "暂无")}</div>
          </div>
        ),
      },
      {
        title: "质量信号",
        key: "quality",
        render: (_value, record) => (
          <div className="space-y-2">
            <div className="flex items-center gap-2 text-sm font-semibold text-slate-900">
              <Star size={14} className="text-amber-500" />
              评分 {formatRating(record.avgRating)} / 5.0
            </div>
            <div className="flex flex-wrap gap-2">
              {record.overdueReplyOrderCount > 0 ? <Tag color="error">超时未答 {record.overdueReplyOrderCount}</Tag> : null}
              {record.expiringReplyOrderCount > 0 ? <Tag color="warning">即将超时 {record.expiringReplyOrderCount}</Tag> : null}
              {record.pendingAfterSalesCount > 0 ? <Tag color="volcano">待售后 {record.pendingAfterSalesCount}</Tag> : null}
              {record.overdueReplyOrderCount === 0 && record.expiringReplyOrderCount === 0 && record.pendingAfterSalesCount === 0 ? (
                <Tag color="success">履约平稳</Tag>
              ) : null}
            </div>
          </div>
        ),
      },
      {
        title: "提现队列",
        key: "withdrawal",
        render: (_value, record) => (
          <div className="space-y-2">
            <div className="flex flex-wrap gap-2">
              {getWithdrawalTag(record.latestWithdrawalStatus)}
              {record.openWithdrawalCount > 0 ? <Tag color="processing">待处理 {record.openWithdrawalCount}</Tag> : null}
            </div>
            <div className="font-semibold text-slate-900">{formatMoneyFen(record.openWithdrawalAmountFen)}</div>
            <div className="text-xs text-slate-400">
              最近申请 {formatDateTime(record.latestWithdrawalCreatedAt, "暂无")}
            </div>
          </div>
        ),
      },
      {
        title: "风险信号",
        key: "risks",
        render: (_value, record) => (
          <div className="space-y-2">
            {getRiskTag(record.highestRiskLevel)}
            <div className="flex flex-wrap gap-2">
              {record.riskSignals.length > 0
                ? record.riskSignals.slice(0, 2).map((signal) => <span key={signal.code}>{getRiskSignalTag(signal)}</span>)
                : <Tag color="success">未命中巡检规则</Tag>}
              {record.riskSignals.length > 2 ? <Tag>+{record.riskSignals.length - 2}</Tag> : null}
            </div>
          </div>
        ),
      },
      {
        title: "操作",
        key: "actions",
        render: (_value, record) => {
          const actions = describeWithdrawalAction(record);

          return (
            <div className="space-y-2" onClick={(event) => event.stopPropagation()}>
              <Button size="small" className="!rounded-full !border-slate-200" onClick={() => openMentorDetail(record.mentorUserId)}>
                查看详情
              </Button>
              {actions ? (
                <>
                  <Popconfirm
                    title={actions.primary.title}
                    description={actions.primary.description}
                    okText="确认"
                    cancelText="取消"
                    onConfirm={() => handleManageWithdrawal(record, actions.primary.status)}
                  >
                    <Button
                      size="small"
                      type="primary"
                      className="!rounded-full !bg-indigo-600 !shadow-none"
                      loading={managingWithdrawalId === record.latestWithdrawalId}
                    >
                      {actions.primary.label}
                    </Button>
                  </Popconfirm>
                  <Popconfirm
                    title={actions.secondary.title}
                    description={actions.secondary.description}
                    okText="确认"
                    cancelText="取消"
                    onConfirm={() => handleManageWithdrawal(record, actions.secondary.status)}
                  >
                    <Button
                      size="small"
                      danger
                      className="!rounded-full"
                      loading={managingWithdrawalId === record.latestWithdrawalId}
                    >
                      {actions.secondary.label}
                    </Button>
                  </Popconfirm>
                </>
              ) : null}
            </div>
          );
        },
      },
    ],
    [handleManageWithdrawal, managingWithdrawalId, openMentorDetail],
  );

  const selectedWithdrawalActions = selectedRecord ? describeWithdrawalAction(selectedRecord) : null;
  const selectedFulfillmentRate = selectedRecord ? buildFulfillmentRate(selectedRecord) : 0;

  const refreshButtonLoading = overviewLoading || listLoading;

  return (
    <AdminPageFrame>
      <AdminPageHeader
        sectionLabel="Mentor Governance"
        title="导师经营治理"
        description="查看导师供给、排期履约、提现状态与质量风险。"
        tone="indigo"
        actions={(
          <Button className="!h-11 !rounded-2xl !border-slate-200 !px-5 !shadow-none" onClick={handleRefresh} disabled={refreshButtonLoading}>
            <RefreshCcw size={16} className={refreshButtonLoading ? "mr-2 animate-spin" : "mr-2"} />
            刷新数据
          </Button>
        )}
      />

      {overviewError ? <Alert type="error" showIcon className="rounded-[28px]" message={overviewError} /> : null}

      <div className="grid grid-cols-1 gap-5 md:grid-cols-2 2xl:grid-cols-4">
        {summaryCards.map((item) => (
          <AdminMetricCard
            key={item.key}
            icon={item.icon}
            label={item.label}
            value={item.value}
            note={item.note}
            tone={item.tone}
          />
        ))}
      </div>

      <div className="space-y-5">
        <AdminFilterBar>
          <Search
            allowClear
            placeholder="搜索导师、机构、岗位或套餐"
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
            value={approvalStatusFilter}
            options={approvalOptions}
            onChange={(value) => {
              setPage(1);
              setApprovalStatusFilter(value);
            }}
          />
          <Select
            className="min-w-[170px]"
            value={riskLevelFilter}
            options={riskLevelOptions}
            onChange={(value) => {
              setPage(1);
              setRiskLevelFilter(value);
            }}
          />
          <Select
            className="min-w-[180px]"
            value={withdrawalStatusFilter}
            options={withdrawalStatusOptions}
            onChange={(value) => {
              setPage(1);
              setWithdrawalStatusFilter(value);
            }}
          />
          {(keyword || approvalStatusFilter || riskLevelFilter || withdrawalStatusFilter) ? (
            <Button
              className="!h-11 !rounded-2xl !border-slate-200"
              onClick={() => {
                setKeywordDraft("");
                setKeyword("");
                setApprovalStatusFilter("");
                setRiskLevelFilter("");
                setWithdrawalStatusFilter("");
                setPage(1);
              }}
            >
              清空筛选
            </Button>
          ) : null}
        </AdminFilterBar>

        <AdminSurfaceCard
          title="导师经营巡检列表"
          description="查看导师供给、履约、提现和风险信号，点击任意行或详情按钮使用弹窗展开经营详情。"
          extra={<div className="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-500">当前 {formatCount(total)} 条</div>}
        >
          {listError ? <Alert type="error" showIcon className="mb-4 rounded-2xl" message={listError} /> : null}
          <Table<MentorOpsRecord>
            rowKey="mentorUserId"
            loading={listLoading}
            size="middle"
            columns={columns}
            dataSource={records}
            tableLayout="auto"
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
            locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前筛选条件下没有导师经营记录" /> }}
            onRow={(record) => ({
              onClick: () => openMentorDetail(record.mentorUserId),
            })}
            rowClassName={(record) => joinAdminClassNames(
              "cursor-pointer transition-colors hover:!bg-slate-50",
              record.mentorUserId === selectedMentorId && "!bg-indigo-50/70",
            )}
          />
        </AdminSurfaceCard>
      </div>

      {detailModalOpen ? (
        <Suspense fallback={null}>
          <AdminMentorDetailModal
            context={{
              detailModalOpen,
              setDetailModalOpen,
              selectedRecord,
              getApprovalTag,
              getAvailabilityTag,
              buildMentorIdentityLine,
              selectedFulfillmentRate,
              getRiskSignalTag,
              handleRefresh,
              refreshButtonLoading,
              navigate,
              selectedWithdrawalActions,
              handleManageWithdrawal,
              formatRating,
            }}
          />
        </Suspense>
      ) : null}
    </AdminPageFrame>
  );
}
