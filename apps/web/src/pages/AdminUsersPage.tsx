import { lazy, Suspense, useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import {
  Alert,
  Badge,
  Button,
  Form,
  Input,
  InputNumber,
  Pagination,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
  type TableColumnsType,
} from "antd";
import {
  Activity,
  ArrowLeft,
  Building2,
  Crown,
  Edit2,
  GraduationCap,
  KeyRound,
  Lock,
  RefreshCcw,
  Search,
  Shield,
  Sparkles,
  UserCheck,
  UserCog,
  Users,
  UserX,
} from "lucide-react";
import { useNavigate, useParams } from "react-router-dom";
import {
  type AdminAccentTone,
  AdminFilterBar,
  AdminMetricCard,
  AdminPageFrame,
  AdminPageHeader,
  AdminSurfaceCard,
} from "../components/admin/AdminOpsPrimitives";
import { useLatestRequest } from "../hooks/useLatestRequest";
import { buildAdminStaleCacheKey, useAdminStaleCache } from "../hooks/useAdminStaleCache";
import { ApiClientError, apiRequest, buildQuery, isAbortError } from "../lib/apiClient";
import {
  accountStatusBadgeMap,
  accountStatusLabelMap,
  approvalStatusLabelMap,
  getLabel,
  growthReasonCodeLabelMap,
  roleColorMap,
  roleLabelMap,
  tierLabelMap,
} from "../lib/adminLabels";
import { formatCount, formatDateTime, formatPercent } from "../lib/formatters";
import AdminIdentityAvatar from "../components/admin/AdminIdentityAvatar";

const AdminUsersDetailModal = lazy(() => import("../components/admin/overlays/AdminUsersDetailModal"));

const { Text } = Typography;

type ApprovalStatus = "PENDING" | "APPROVED" | "REJECTED";

type UserSummaryResponse = {
  totalUsers: number;
  mentorUsers: number;
  enterpriseUsers: number;
  premiumUsers: number;
  pendingApprovalUsers: number;
  suspendedUsers: number;
  activeUsers7d: number;
  newUsers7d: number;
};

type UserListItem = {
  userId: number;
  email: string;
  displayName: string;
  role: "STUDENT" | "MENTOR" | "ENTERPRISE" | "ADMIN";
  tier: "FREE" | "PREMIUM";
  status: "ACTIVE" | "PENDING" | "SUSPENDED";
  approvalStatus: ApprovalStatus | null;
  createdAt: string | null;
  lastLoginAt: string | null;
};

type UserListResponse = {
  records: UserListItem[];
  total: number;
  page: number;
  size: number;
};

type UserTier = UserListItem["tier"];
type UserActionLoadingKey = "STATUS" | "PASSWORD" | "POINTS" | "TIER_FREE" | "TIER_PREMIUM";

type UserDetailResponse = {
  userId: number;
  email: string;
  displayName: string;
  role: UserListItem["role"];
  tier: UserListItem["tier"];
  status: UserListItem["status"];
  approvalStatus: ApprovalStatus | null;
  createdAt: string | null;
  studentProfile: {
    major: string | null;
    grade: string | null;
    targetPosition: string | null;
    skillTags: string[];
    selfIntro: string | null;
  } | null;
  communityScore7d: number | null;
};

type UserListCachePayload = {
  records: UserListItem[];
  total: number;
};

type GrantPointsResponse = {
  userId: number;
  deltaPoints: number;
  newBalance: number;
  reasonCode: string;
};

type SummaryCardItem = {
  key: string;
  label: string;
  value: ReactNode;
  note: string;
  tone: AdminAccentTone;
  icon: typeof Users;
};

const roleOptions = [
  { label: "全部角色", value: "" },
  { label: "学生", value: "STUDENT" },
  { label: "导师", value: "MENTOR" },
  { label: "企业", value: "ENTERPRISE" },
  { label: "管理员", value: "ADMIN" },
];

const statusOptions = [
  { label: "全部账户状态", value: "" },
  { label: "正常", value: "ACTIVE" },
  { label: "待激活", value: "PENDING" },
  { label: "已封禁", value: "SUSPENDED" },
];

const approvalStatusOptions = [
  { label: "全部认证状态", value: "" },
  { label: "待认证", value: "PENDING" },
  { label: "已认证", value: "APPROVED" },
  { label: "已驳回", value: "REJECTED" },
];

const grantReasonOptions = [
  { label: getLabel("TEST_TOPUP", growthReasonCodeLabelMap, "人工补发"), value: "TEST_TOPUP" },
];

const filterInputClassName =
  "!h-11 !rounded-2xl !border-transparent !bg-white !px-1 !shadow-sm hover:!border-transparent focus-within:!border-transparent";

const filterSelectClassName =
  "min-w-[160px] [&_.ant-select-arrow]:!text-slate-400 [&_.ant-select-selector]:!h-11 [&_.ant-select-selector]:!items-center [&_.ant-select-selector]:!rounded-2xl [&_.ant-select-selector]:!border-transparent [&_.ant-select-selector]:!bg-white [&_.ant-select-selector]:!px-3 [&_.ant-select-selector]:!shadow-sm";

export default function AdminUsersPage() {
  const navigate = useNavigate();
  const { userId: userIdParam } = useParams();
  const activeUserId = userIdParam ? Number(userIdParam) : null;

  const [passwordForm] = Form.useForm();
  const [pointsForm] = Form.useForm();
  const createSummaryRequest = useLatestRequest();
  const createListRequest = useLatestRequest();
  const createDetailRequest = useLatestRequest();

  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keywordInput, setKeywordInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [roleFilterInput, setRoleFilterInput] = useState("");
  const [roleFilter, setRoleFilter] = useState("");
  const [statusFilterInput, setStatusFilterInput] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [approvalStatusFilterInput, setApprovalStatusFilterInput] = useState("");
  const [approvalStatusFilter, setApprovalStatusFilter] = useState("");
  const summaryCache = useAdminStaleCache<UserSummaryResponse>("admin-users:summary");
  const listCache = useAdminStaleCache<UserListCachePayload>(
    buildAdminStaleCacheKey("admin-users:list", {
      page,
      pageSize,
      keyword,
      roleFilter,
      statusFilter,
      approvalStatusFilter,
    }),
  );
  const detailCache = useAdminStaleCache<UserDetailResponse | null>(
    buildAdminStaleCacheKey("admin-users:detail", {
      activeUserId: activeUserId ?? "none",
    }),
  );

  const [summary, setSummary] = useState<UserSummaryResponse | null>(() => summaryCache.cached);
  const [users, setUsers] = useState<UserListItem[]>(() => listCache.cached?.records ?? []);
  const [total, setTotal] = useState(() => listCache.cached?.total ?? 0);
  const [summaryLoading, setSummaryLoading] = useState(() => !summaryCache.hasCache);
  const [listLoading, setListLoading] = useState(() => !listCache.hasCache);
  const [detailLoading, setDetailLoading] = useState(() => Boolean(activeUserId) && !detailCache.hasCache);
  const [actionLoadingKey, setActionLoadingKey] = useState<UserActionLoadingKey | null>(null);
  const [summaryError, setSummaryError] = useState<string | null>(null);
  const [listError, setListError] = useState<string | null>(null);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [currentUser, setCurrentUser] = useState<UserDetailResponse | null>(() => detailCache.cached);

  const isApprovalManagedRole = (role: UserListItem["role"]) => role === "MENTOR" || role === "ENTERPRISE";
  const actionLoading = actionLoadingKey !== null;

  const premiumRatio = useMemo(() => {
    if (!summary || summary.totalUsers <= 0) {
      return "0.0%";
    }

    return formatPercent(summary.premiumUsers, summary.totalUsers, 1);
  }, [summary]);

  const summaryCards = useMemo<SummaryCardItem[]>(
    () => [
      {
        key: "totalUsers",
        label: "平台总用户",
        value: summaryLoading ? "—" : formatCount(summary?.totalUsers ?? 0),
        note: "当前已注册的全量账号规模。",
        tone: "indigo",
        icon: Users,
      },
      {
        key: "mentorUsers",
        label: "入驻导师",
        value: summaryLoading ? "—" : formatCount(summary?.mentorUsers ?? 0),
        note: "当前可纳入治理的导师主体。",
        tone: "violet",
        icon: Shield,
      },
      {
        key: "enterpriseUsers",
        label: "企业账号",
        value: summaryLoading ? "—" : formatCount(summary?.enterpriseUsers ?? 0),
        note: "企业主体规模与发布能力基线。",
        tone: "sky",
        icon: Building2,
      },
      {
        key: "activeUsers7d",
        label: "近 7 日活跃",
        value: summaryLoading ? "—" : formatCount(summary?.activeUsers7d ?? 0),
        note: "用来感知平台近期活跃度。",
        tone: "emerald",
        icon: Activity,
      },
      {
        key: "premiumRatio",
        label: "高级会员占比",
        value: summaryLoading ? "—" : premiumRatio,
        note: `高级会员 ${formatCount(summary?.premiumUsers ?? 0)} 人。`,
        tone: "amber",
        icon: Crown,
      },
      {
        key: "pendingApprovalUsers",
        label: "待认证主体",
        value: summaryLoading ? "—" : formatCount(summary?.pendingApprovalUsers ?? 0),
        note: "可前往认证审核查看处理进度。",
        tone: "slate",
        icon: UserCog,
      },
      {
        key: "suspendedUsers",
        label: "异常 / 封禁账号",
        value: summaryLoading ? "—" : formatCount(summary?.suspendedUsers ?? 0),
        note: "可直接在详情弹窗做状态管理。",
        tone: "rose",
        icon: UserX,
      },
      {
        key: "newUsers7d",
        label: "近 7 日新增",
        value: summaryLoading ? "—" : formatCount(summary?.newUsers7d ?? 0),
        note: "辅助判断近期拉新与准入节奏。",
        tone: "teal",
        icon: Sparkles,
      },
    ],
    [premiumRatio, summary, summaryLoading],
  );

  const activeFilterTags = useMemo(
    () => [
      keyword ? `关键词：${keyword}` : null,
      roleFilter ? `角色：${roleOptions.find((item) => item.value === roleFilter)?.label ?? roleFilter}` : null,
      statusFilter ? `状态：${statusOptions.find((item) => item.value === statusFilter)?.label ?? statusFilter}` : null,
      approvalStatusFilter
        ? `认证：${approvalStatusOptions.find((item) => item.value === approvalStatusFilter)?.label ?? approvalStatusFilter}`
        : null,
    ].filter(Boolean) as string[],
    [approvalStatusFilter, keyword, roleFilter, statusFilter],
  );

  const loadSummary = useCallback(async (showLoading = true) => {
    const request = createSummaryRequest();
    if (showLoading) {
      setSummaryLoading(true);
    }
    setSummaryError(null);

    try {
      // 用户概览单独缓存，列表筛选变化不需要重算顶部统计。
      const response = await apiRequest<UserSummaryResponse>("/admin/users/summary", { signal: request.signal });
      if (!request.isCurrent()) {
        return;
      }
      setSummary(response);
      summaryCache.write(response);
    } catch (error) {
      if (isAbortError(error) || !request.isCurrent()) {
        return;
      }
      const apiError = error as ApiClientError;
      setSummaryError(apiError.message || "加载用户概览失败");
    } finally {
      if (request.isCurrent()) {
        setSummaryLoading(false);
      }
    }
  }, [createSummaryRequest, summaryCache]);

  const loadUsers = useCallback(async (showLoading = true) => {
    const request = createListRequest();
    if (showLoading) {
      setListLoading(true);
    }
    setListError(null);

    try {
      // 用户列表使用服务端分页筛选，前端只维护筛选输入和当前页缓存。
      const response = await apiRequest<UserListResponse>(
        `/admin/users${buildQuery({ page, size: pageSize, keyword, role: roleFilter, status: statusFilter, approvalStatus: approvalStatusFilter })}`,
        { signal: request.signal },
      );
      if (!request.isCurrent()) {
        return;
      }
      setUsers(response.records);
      setTotal(response.total);
      listCache.write({
        records: response.records,
        total: response.total,
      });
    } catch (error) {
      if (isAbortError(error) || !request.isCurrent()) {
        return;
      }
      const apiError = error as ApiClientError;
      setListError(apiError.message || "加载用户列表失败");
    } finally {
      if (request.isCurrent()) {
        setListLoading(false);
      }
    }
  }, [approvalStatusFilter, createListRequest, keyword, listCache, page, pageSize, roleFilter, statusFilter]);

  const loadDetail = useCallback(async (targetUserId: number, showLoading = true) => {
    const request = createDetailRequest();
    if (showLoading) {
      setDetailLoading(true);
    }
    setDetailError(null);

    try {
      // URL 中的 userId 就是详情弹层真相源，便于通知或外部入口直接定位。
      const response = await apiRequest<UserDetailResponse>(`/admin/users/${targetUserId}`, { signal: request.signal });
      if (!request.isCurrent()) {
        return;
      }
      setCurrentUser(response);
      detailCache.write(response);
      passwordForm.resetFields();
      pointsForm.setFieldsValue({ points: 30, reasonCode: "TEST_TOPUP" });
    } catch (error) {
      if (isAbortError(error) || !request.isCurrent()) {
        return;
      }
      const apiError = error as ApiClientError;
      setCurrentUser(null);
      setDetailError(apiError.message || "加载用户详情失败");
    } finally {
      if (request.isCurrent()) {
        setDetailLoading(false);
      }
    }
  }, [createDetailRequest, detailCache, passwordForm, pointsForm]);

  useEffect(() => {
    if (!summaryCache.cached) {
      return;
    }
    setSummary(summaryCache.cached);
  }, [summaryCache.cached]);

  useEffect(() => {
    if (!listCache.cached) {
      return;
    }
    setUsers(listCache.cached.records);
    setTotal(listCache.cached.total);
  }, [listCache.cached]);

  useEffect(() => {
    if (!detailCache.cached) {
      return;
    }
    setCurrentUser(detailCache.cached);
    passwordForm.resetFields();
    pointsForm.setFieldsValue({ points: 30, reasonCode: "TEST_TOPUP" });
  }, [detailCache.cached, passwordForm, pointsForm]);

  useEffect(() => {
    void loadSummary(!summaryCache.hasCache);
  }, [loadSummary, summaryCache.hasCache]);

  useEffect(() => {
    void loadUsers(!listCache.hasCache);
  }, [listCache.hasCache, loadUsers]);

  useEffect(() => {
    if (!activeUserId || Number.isNaN(activeUserId)) {
      setCurrentUser(null);
      setDetailError(null);
      return;
    }

    void loadDetail(activeUserId, !detailCache.hasCache);
  }, [activeUserId, detailCache.hasCache, loadDetail]);

  const applyUserFilters = useCallback(() => {
    // 输入区和生效筛选分离，只有点击查询才重置分页并触发请求。
    setPage(1);
    setKeyword(keywordInput.trim());
    setRoleFilter(roleFilterInput);
    setStatusFilter(statusFilterInput);
    setApprovalStatusFilter(approvalStatusFilterInput);
  }, [approvalStatusFilterInput, keywordInput, roleFilterInput, statusFilterInput]);

  const resetUserFilters = useCallback(() => {
    setPage(1);
    setKeywordInput("");
    setKeyword("");
    setRoleFilterInput("");
    setRoleFilter("");
    setStatusFilterInput("");
    setStatusFilter("");
    setApprovalStatusFilterInput("");
    setApprovalStatusFilter("");
  }, []);

  const refreshListData = useCallback(async () => {
    await Promise.all([loadSummary(), loadUsers()]);
  }, [loadSummary, loadUsers]);

  const selectedUserFromList = users.find((item) => item.userId === activeUserId) ?? null;

  const studentProfileCompleteness = useMemo(() => {
    if (!currentUser?.studentProfile) {
      return null;
    }

    const filledFieldCount = [
      currentUser.studentProfile.major,
      currentUser.studentProfile.grade,
      currentUser.studentProfile.targetPosition,
      currentUser.studentProfile.selfIntro,
      currentUser.studentProfile.skillTags.length > 0 ? "HAS_SKILLS" : "",
    ].filter(Boolean).length;

    return Math.round((filledFieldCount / 5) * 100);
  }, [currentUser]);

  const renderTierTag = (tier: UserListItem["tier"]) => {
    if (tier === "PREMIUM") {
      return (
        <Tag color="gold" bordered={false}>
          <span className="inline-flex items-center gap-1 whitespace-nowrap align-middle">
            <Crown size={12} className="shrink-0" />
            <span>{tierLabelMap[tier] ?? tier}</span>
          </span>
        </Tag>
      );
    }

    return <Tag bordered={false}>{tierLabelMap[tier] ?? tier}</Tag>;
  };

  const tierDisplayLabelMap: Record<UserTier, string> = {
    FREE: "普通用户",
    PREMIUM: "VIP 会员",
  };

  const renderApprovalTag = (role: UserListItem["role"], approvalStatus: ApprovalStatus | null) => {
    if (!isApprovalManagedRole(role)) {
      return <Text type="secondary">不适用</Text>;
    }

    if (!approvalStatus) {
      return <Tag bordered={false}>未建档</Tag>;
    }

    const color: "success" | "error" | "warning" = approvalStatus === "APPROVED" ? "success" : approvalStatus === "REJECTED" ? "error" : "warning";
    return <Badge status={color} text={approvalStatusLabelMap[approvalStatus] ?? approvalStatus} />;
  };

  const handleOpenUser = useCallback((record: UserListItem) => {
    navigate(`/admin/users/${record.userId}`);
  }, [navigate]);

  const handleOpenCertificationWorkspace = useCallback((userId: number) => {
    navigate(`/admin/users/reviews/${userId}`);
  }, [navigate]);

  const handleCloseUser = () => {
    navigate("/admin/users");
    passwordForm.resetFields();
    pointsForm.resetFields();
  };

  const handleUpdateStatus = async (nextStatus: "ACTIVE" | "SUSPENDED") => {
    if (!activeUserId || !currentUser) {
      return;
    }

    setActionLoadingKey("STATUS");
    try {
      // 账号封禁/恢复后同时刷新概览、列表和详情，避免弹层状态滞后。
      await apiRequest<void>(`/admin/users/${activeUserId}/status`, {
        method: "POST",
        body: JSON.stringify({ status: nextStatus }),
      });
      message.success(`已将用户状态更新为 ${nextStatus === "ACTIVE" ? "正常" : "封禁"}`);
      await Promise.all([loadSummary(), loadUsers(), loadDetail(activeUserId)]);
    } catch (error) {
      const apiError = error as ApiClientError;
      message.error(apiError.message || "更新用户状态失败");
    } finally {
      setActionLoadingKey(null);
    }
  };

  const handleResetPassword = async () => {
    if (!activeUserId) {
      return;
    }

    try {
      const values = await passwordForm.validateFields();
      setActionLoadingKey("PASSWORD");
      try {
        await apiRequest<void>(`/admin/users/${activeUserId}/reset-password`, {
          method: "POST",
          body: JSON.stringify({ newPassword: values.newPassword }),
        });
        passwordForm.resetFields();
        message.success("密码已重置，可使用新密码重新登录");
      } catch (error) {
        const apiError = error as ApiClientError;
        message.error(apiError.message || "重置密码失败");
      } finally {
        setActionLoadingKey(null);
      }
    } catch {
      return;
    }
  };

  const handleUpdateTier = async (nextTier: UserTier) => {
    if (!activeUserId || !currentUser || currentUser.role !== "STUDENT" || currentUser.tier === nextTier) {
      return;
    }

    setActionLoadingKey(nextTier === "PREMIUM" ? "TIER_PREMIUM" : "TIER_FREE");
    try {
      await apiRequest<void>(`/admin/users/${activeUserId}/tier`, {
        method: "POST",
        body: JSON.stringify({ tier: nextTier }),
      });
      message.success(`已切换为${tierDisplayLabelMap[nextTier]}`);
      await Promise.all([loadSummary(), loadUsers(), loadDetail(activeUserId)]);
    } catch (error) {
      const apiError = error as ApiClientError;
      message.error(apiError.message || "切换用户组失败");
    } finally {
      setActionLoadingKey(null);
    }
  };

  const handleGrantPoints = async () => {
    if (!activeUserId || !currentUser) {
      return;
    }

    try {
      const values = await pointsForm.validateFields();
      setActionLoadingKey("POINTS");
      try {
        // 积分补发走成长模块接口，用户详情只负责展示补发后的余额。
        const response = await apiRequest<GrantPointsResponse>("/admin/growth/points/grant", {
          method: "POST",
          body: JSON.stringify({
            userId: activeUserId,
            points: values.points,
            reasonCode: values.reasonCode,
          }),
        });
        message.success(`已补发 ${response.deltaPoints} 积分，当前余额 ${response.newBalance}`);
        pointsForm.resetFields(["points"]);
        pointsForm.setFieldValue("points", 30);
        await loadDetail(activeUserId);
      } catch (error) {
        const apiError = error as ApiClientError;
        message.error(apiError.message || "补发积分失败");
      } finally {
        setActionLoadingKey(null);
      }
    } catch {
      return;
    }
  };

  const columns = useMemo<TableColumnsType<UserListItem>>(
    () => [
      {
        title: "用户编号",
        dataIndex: "userId",
        width: 128,
        render: (userId: number) => <span className="font-mono text-xs font-semibold text-slate-400">#{userId}</span>,
      },
      {
        title: "邮箱 / 昵称",
        key: "profile",
        render: (_value, record) => (
          <Space size={12}>
            <AdminIdentityAvatar
              role={record.role}
              userId={record.userId}
              displayName={record.displayName}
              className="!h-10 !w-10 !min-w-10 !shrink-0"
              textClassName="text-sm"
            />
            <div className="min-w-0">
              <div className="font-semibold text-slate-900">{record.displayName}</div>
              <div className="truncate text-xs text-slate-500">{record.email}</div>
            </div>
          </Space>
        ),
      },
      {
        title: "角色",
        dataIndex: "role",
        width: 120,
        render: (role: UserListItem["role"]) => (
          <Tag color={roleColorMap[role]} bordered={false}>
            {roleLabelMap[role] ?? role}
          </Tag>
        ),
      },
      {
        title: "套餐",
        dataIndex: "tier",
        width: 130,
        render: (tier: UserListItem["tier"]) => renderTierTag(tier),
      },
      {
        title: "认证状态",
        dataIndex: "approvalStatus",
        width: 150,
        render: (_value, record) => renderApprovalTag(record.role, record.approvalStatus),
      },
      {
        title: "账户状态",
        dataIndex: "status",
        width: 150,
        render: (status: UserListItem["status"]) => (
          <Badge status={accountStatusBadgeMap[status]} text={accountStatusLabelMap[status] ?? status} />
        ),
      },
      {
        title: "最近登录",
        dataIndex: "lastLoginAt",
        width: 180,
        render: (lastLoginAt: string | null) => <Text type="secondary">{formatDateTime(lastLoginAt)}</Text>,
      },
      {
        title: "操作",
        key: "action",
        width: 220,
        align: "right",
        render: (_value, record) => (
          <div className="flex justify-end gap-2">
            {isApprovalManagedRole(record.role) ? (
              <Button
                size="small"
                className="!h-9 !rounded-xl !border-transparent !bg-slate-100 !px-3 !font-semibold !text-slate-600 shadow-none hover:!border-transparent hover:!bg-slate-200 hover:!text-slate-700"
                onClick={(event) => {
                  event.stopPropagation();
                  handleOpenCertificationWorkspace(record.userId);
                }}
              >
                认证审核
              </Button>
            ) : null}
            <Button
              size="small"
              icon={<Edit2 size={14} />}
              className="!h-9 !rounded-xl !border-transparent !bg-indigo-50 !px-3 !font-semibold !text-indigo-600 shadow-none hover:!border-transparent hover:!bg-indigo-100 hover:!text-indigo-600"
              onClick={(event) => {
                event.stopPropagation();
                handleOpenUser(record);
              }}
            >
              管理
            </Button>
          </div>
        ),
      },
    ],
    [handleOpenCertificationWorkspace, handleOpenUser],
  );

  const refreshButtonLoading = summaryLoading || listLoading;
  const heroTimestamp = useMemo(() => {
    const timestamps = [summaryCache.cachedAt, listCache.cachedAt].filter((item): item is number => typeof item === "number");
    if (timestamps.length === 0) {
      return null;
    }
    return Math.max(...timestamps);
  }, [listCache.cachedAt, summaryCache.cachedAt]);

  return (
    <>
      <AdminPageFrame>
        <AdminPageHeader
          sectionLabel="USER OPERATIONS"
          title="用户与认证"
          description="聚合账号规模、认证状态与当前筛选结果，默认优先展示缓存快照并在后台静默更新。"
          tone="indigo"
          actions={(
            <Button
              disabled={refreshButtonLoading}
              className="!h-11 !rounded-2xl !border-slate-200 !px-5 !shadow-none"
              onClick={() => void refreshListData()}
            >
              <RefreshCcw size={15} className={refreshButtonLoading ? "mr-2 animate-spin" : "mr-2"} />
              刷新数据
            </Button>
          )}
        />

        <div className="flex flex-wrap items-center gap-2">
          <span className="admin-typography-chip rounded-full bg-white px-3 py-1 text-slate-400 shadow-sm">
            {heroTimestamp ? `数据刷新于 ${formatDateTime(heroTimestamp)}` : "进入页面后会自动静默更新最新数据"}
          </span>
        </div>

        {summaryError || listError ? (
          <Alert
            type="warning"
            showIcon
            className="rounded-[24px]"
            message="部分数据加载失败"
            description={summaryError || listError}
          />
        ) : null}

        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          {summaryCards.map(({ key, ...card }) => (
            <AdminMetricCard key={key} {...card} />
          ))}
        </div>

        <AdminFilterBar className="!p-6">
          <div className="flex w-full flex-col gap-6">
            <div className="flex flex-col gap-3 xl:flex-row xl:items-end xl:justify-between">
              <div>
                <div className="admin-typography-surface-title text-slate-950">筛选工作台</div>
                <div className="admin-typography-surface-description mt-1 text-slate-500">
                  支持关键词、角色、账户状态和认证状态组合筛选，账号管理与详情查看都可在当前页完成。
                </div>
              </div>
              <div className="flex flex-wrap items-center gap-3">
                <Tag bordered={false} className="admin-typography-chip m-0 rounded-full bg-white px-3 py-1 text-slate-500">
                  共 {formatCount(total)} 条记录
                </Tag>
                <Button
                  icon={<Lock size={15} />}
                  className="!h-11 !rounded-2xl !border-0 !bg-[#e9edff] !px-5 !font-semibold !text-[#4338ca] shadow-none hover:!bg-[#dfe5ff] hover:!text-[#3730a3]"
                  onClick={() => navigate("/admin/users/reviews")}
                >
                  认证审核队列
                </Button>
              </div>
            </div>

            <div className="grid gap-4 xl:grid-cols-[minmax(0,1.45fr)_repeat(3,minmax(160px,1fr))_auto_auto]">
              <div className="flex flex-col gap-2">
                <label className="admin-typography-field-label px-1 text-slate-400">关键词</label>
                <Input
                  value={keywordInput}
                  onChange={(event) => setKeywordInput(event.target.value)}
                  onPressEnter={applyUserFilters}
                  placeholder="搜索用户ID、邮箱或姓名..."
                  prefix={<Search size={16} className="text-slate-400" />}
                  className={filterInputClassName}
                />
              </div>

              <div className="flex flex-col gap-2">
                <label className="admin-typography-field-label px-1 text-slate-400">角色</label>
                <Select
                  value={roleFilterInput}
                  onChange={setRoleFilterInput}
                  options={roleOptions}
                  className={filterSelectClassName}
                />
              </div>

              <div className="flex flex-col gap-2">
                <label className="admin-typography-field-label px-1 text-slate-400">账户状态</label>
                <Select
                  value={statusFilterInput}
                  onChange={setStatusFilterInput}
                  options={statusOptions}
                  className={filterSelectClassName}
                />
              </div>

              <div className="flex flex-col gap-2">
                <label className="admin-typography-field-label px-1 text-slate-400">认证状态</label>
                <Select
                  value={approvalStatusFilterInput}
                  onChange={setApprovalStatusFilterInput}
                  options={approvalStatusOptions}
                  className={filterSelectClassName}
                />
              </div>

              <div className="flex items-end gap-2">
                <Button
                  type="primary"
                  className="!h-11 !rounded-2xl !border-0 !bg-gradient-to-r !from-[#5b61f6] !to-[#4338ca] !px-6 !font-semibold shadow-lg shadow-[#4f46e5]/15"
                  onClick={applyUserFilters}
                >
                  应用筛选
                </Button>
                <Button
                  className="!h-11 !rounded-2xl !border-transparent !bg-transparent !px-4 !font-semibold !text-[#4f46e5] shadow-none hover:!bg-white hover:!text-[#4338ca]"
                  onClick={resetUserFilters}
                >
                  重置
                </Button>
              </div>
            </div>

            <div className="flex flex-wrap items-center gap-2">
              {activeFilterTags.length > 0 ? (
                activeFilterTags.map((tag) => (
                  <Tag key={tag} bordered={false} className="admin-typography-chip m-0 rounded-full bg-white px-3 py-1 text-slate-600 shadow-sm">
                    {tag}
                  </Tag>
                ))
              ) : (
                <Tag bordered={false} className="admin-typography-chip m-0 rounded-full bg-white px-3 py-1 text-slate-500 shadow-sm">
                  当前未应用附加筛选条件
                </Tag>
              )}
            </div>
          </div>
        </AdminFilterBar>

        <AdminSurfaceCard
          title="用户列表"
          description="点击任意一行即可打开用户详情弹窗。"
          extra={(
            <div className="admin-typography-chip text-slate-500">
              当前页 <span className="font-semibold text-slate-950">{page}</span> / {Math.max(1, Math.ceil(total / pageSize))}
            </div>
          )}
          className="!rounded-[28px] !border-white !shadow-[0_12px_42px_rgba(44,47,49,0.05)]"
          bodyClassName="!p-0"
        >
          <Table
            rowKey="userId"
            className="[&_.ant-table]:!bg-transparent [&_.ant-table-container:before]:!hidden [&_.ant-table-container:after]:!hidden [&_.ant-table-tbody>tr>td]:!border-b-slate-100 [&_.ant-table-tbody>tr>td]:!px-6 [&_.ant-table-tbody>tr>td]:!py-5 [&_.ant-table-thead>tr>th]:!border-b-slate-200/80 [&_.ant-table-thead>tr>th]:!bg-slate-50/70 [&_.ant-table-thead>tr>th]:!px-6 [&_.ant-table-thead>tr>th]:!py-5"
            columns={columns}
            dataSource={users}
            loading={listLoading}
            rowClassName="cursor-pointer transition-colors hover:!bg-slate-50/80"
            onRow={(record) => ({
              onClick: () => handleOpenUser(record),
            })}
            pagination={false}
            locale={{
              emptyText: listError ? "用户列表加载失败" : "暂无匹配的用户记录",
            }}
            scroll={{ x: 1080 }}
          />

          <div className="flex flex-col gap-3 border-t border-slate-200/80 px-6 py-5 md:flex-row md:items-center md:justify-between">
            <div className="admin-typography-caption text-slate-500">
              {total > 0
                ? `显示 ${(page - 1) * pageSize + 1}-${Math.min(page * pageSize, total)} 条，共 ${total} 条`
                : "暂无用户记录"}
            </div>
            <div className="flex flex-wrap items-center gap-3">
              <div className="flex items-center gap-2 text-sm text-slate-500">
                <span>每页</span>
                <Select
                  value={pageSize}
                  options={[
                    { label: "10 条", value: 10 },
                    { label: "20 条", value: 20 },
                    { label: "50 条", value: 50 },
                  ]}
                  onChange={(value: number) => {
                    setPage(1);
                    setPageSize(value);
                  }}
                  className="w-[96px] [&_.ant-select-selector]:!h-9 [&_.ant-select-selector]:!rounded-xl [&_.ant-select-selector]:!border-slate-200"
                />
              </div>
              <Pagination
                size="small"
                current={page}
                pageSize={pageSize}
                total={total}
                showSizeChanger={false}
                onChange={(nextPage) => {
                  setPage(nextPage);
                }}
              />
            </div>
          </div>
        </AdminSurfaceCard>
      </AdminPageFrame>

      {activeUserId != null ? (
        <Suspense fallback={null}>
          <AdminUsersDetailModal
            context={{
              activeUserId,
              currentUser,
              selectedUserFromList,
              detailError,
              detailLoading,
              studentProfileCompleteness,
              actionLoading,
              actionLoadingKey,
              passwordForm,
              pointsForm,
              grantReasonOptions,
              handleCloseUser,
              handleUpdateStatus,
              handleUpdateTier,
              handleResetPassword,
              handleGrantPoints,
              handleOpenCertificationWorkspace,
              navigate,
              renderTierTag,
              renderApprovalTag,
              isApprovalManagedRole,
            }}
          />
        </Suspense>
      ) : null}
    </>
  );
}
