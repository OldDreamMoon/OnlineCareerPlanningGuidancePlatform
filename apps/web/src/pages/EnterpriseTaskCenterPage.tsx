import { AnimatePresence, motion } from "framer-motion";
import {
  AlertCircle,
  ArrowLeft,
  Briefcase,
  CalendarDays,
  ChevronRight,
  Clock3,
  Filter,
  Inbox,
  Plus,
  RefreshCw,
  Search,
  Target,
  UserCheck,
  X,
  type LucideIcon,
} from "lucide-react";
import { startTransition, useCallback, useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import StudentIdentityAvatar from "../components/avatar/StudentIdentityAvatar";
import EnterpriseWorkspaceShell from "../components/enterprise/EnterpriseWorkspaceShell";
import WorkspacePageLoadingScreen from "../components/WorkspacePageLoadingScreen";
import { ApiClientError, apiRequest, isAbortError } from "../lib/apiClient";
import { resolveEnterpriseAccountName } from "../lib/enterpriseIdentity";
import { buildEnterpriseLogoUrl } from "../lib/enterpriseLogo";
import {
  buildEnterpriseTaskReviewHref,
  getEnterpriseTaskUiStatus,
  parseEnterpriseTaskDescription,
  summarizeTaskDescription,
} from "../lib/enterpriseTasks";
import { getEnterpriseWorkspaceNavItems } from "../lib/workspaceNav";
import {
  formatDateTime,
  formatMonthDay,
  formatRelativeTime as formatRelativeTimeByBrowserTimezone,
} from "../lib/formatters";
import {
  buildWorkspaceSnapshotStorageKey,
  readWorkspaceSnapshot,
  writeWorkspaceSnapshot,
} from "../lib/workspaceSnapshot";
import { buildStudentAvatarPath, type StudentAvatarMeta } from "../lib/studentAvatar";

type EnterpriseOwnProfileResponse = {
  userId: number;
  displayName: string;
  realName: string | null;
  companyName: string | null;
  jobTitle: string | null;
  logoUrl: string | null;
  logoUpdatedAt?: number | string | null;
  approvalStatus: string;
};

type BountyTaskListResponse = {
  tasks: TaskCenterTaskItem[];
  total: number;
};

type BountyTaskItem = {
  taskId: number;
  enterpriseUserId: number;
  enterpriseName: string;
  enterpriseLogoUrl?: string | null;
  title: string;
  descriptionSummary: string;
  rewardDescription: string;
  status: string;
  submissionCount: number;
  acceptedSubmissionId: number | null;
  deadlineAt: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  pendingCount: number;
  contactedCount: number;
  reviewedCount: number;
};

type TaskCenterTaskItem = BountyTaskItem & {
  pendingCount: number;
  contactedCount: number;
  reviewedCount: number;
  recentSubmissions: BountySubmissionItem[];
};

type BountyTaskDetailResponse = {
  taskId: number;
  enterpriseUserId: number;
  enterpriseName: string;
  title: string;
  description: string;
  rewardDescription: string;
  status: string;
  submissionCount: number;
  acceptedSubmissionId: number | null;
  mine: boolean;
  deadlineAt: string | null;
  closedAt: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};

type BountySubmissionItem = {
  submissionId: number;
  studentUserId: number;
  studentName: string;
  studentAvatar?: StudentAvatarMeta | null;
  status: string;
  contentSummary: string;
  contentText: string | null;
  attachmentLinks: string[];
  communityScore7d: number;
  portraitTags: string[];
  portraitUpdatedAt: string | null;
  reviewComment: string | null;
  reviewedAt: string | null;
  createdAt: string | null;
};

type BountyTaskManageResponse = {
  taskId: number;
  status: string;
  updatedAt: string;
};

type TaskUiStatus = "进行中" | "即将截止" | "已完成筛选" | "已结束";
type SubmissionFilter = "全部" | "有提交" | "待处理" | "已有继续接触结果";

type TaskSnapshot = {
  records: BountySubmissionItem[];
  total: number;
  partial: boolean;
  loadError: string | null;
};

type ToastState = {
  tone: "success" | "error" | "info";
  message: string;
};

type EnterpriseTaskCenterSnapshot = {
  profile: EnterpriseOwnProfileResponse | null;
  tasks: BountyTaskItem[];
  snapshots: Record<number, TaskSnapshot>;
  taskDetails: Record<number, BountyTaskDetailResponse>;
};

const containerVariants = {
  hidden: { opacity: 0 },
  show: {
    opacity: 1,
    transition: { staggerChildren: 0.08 },
  },
};

const itemVariants = {
  hidden: { opacity: 0, y: 24 },
  show: {
    opacity: 1,
    y: 0,
    transition: { type: "spring" as const, stiffness: 250, damping: 24 },
  },
};

const panelTransition = {
  duration: 0.18,
  ease: [0.22, 1, 0.36, 1] as const,
};

const STATUS_CONFIG: Record<TaskUiStatus, { color: string; bg: string; border: string }> = {
  进行中: { color: "text-indigo-700", bg: "bg-indigo-50", border: "border-indigo-200" },
  即将截止: { color: "text-amber-700", bg: "bg-amber-50", border: "border-amber-200" },
  已完成筛选: { color: "text-emerald-700", bg: "bg-emerald-50", border: "border-emerald-200" },
  已结束: { color: "text-slate-600", bg: "bg-slate-100", border: "border-slate-200" },
};

function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
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

function coerceSubmissionFilter(value: string | null): SubmissionFilter {
  if (value === "有提交" || value === "待处理" || value === "已有继续接触结果") {
    return value;
  }
  return "全部";
}

function formatShortDate(dateValue: string | null | undefined) {
  return formatMonthDay(dateValue, "待补充");
}

function formatRelativeTime(value: string | null | undefined) {
  return formatRelativeTimeByBrowserTimezone(value, "刚刚");
}

function getSubmissionStatusMeta(status: string) {
  if (status === "ACCEPTED") {
    return { label: "已继续接触", className: "border border-emerald-200 bg-emerald-50 text-emerald-700" };
  }
  if (status === "REJECTED") {
    return { label: "已反馈结果", className: "border border-slate-200 bg-slate-100 text-slate-600" };
  }
  if (status === "REVIEWING") {
    return { label: "审核中", className: "border border-blue-200 bg-blue-50 text-blue-700" };
  }
  return { label: "待处理", className: "border border-amber-200 bg-amber-50 text-amber-700" };
}

function EnterpriseTaskCenterSkeleton({
  displayName,
  companyName,
}: {
  displayName: string | null | undefined;
  companyName: string | null | undefined;
}) {
  return (
    <WorkspacePageLoadingScreen
      title="正在准备任务中心"
      description="正在加载任务列表、报名进度和筛选结果，请稍候。"
    />
  );
}

export default function EnterpriseTaskCenterPage() {
  const { role, userId } = useAuth();
  const [searchParams] = useSearchParams();

  const [profile, setProfile] = useState<EnterpriseOwnProfileResponse | null>(null);
  const [tasks, setTasks] = useState<BountyTaskItem[]>([]);
  const [snapshots, setSnapshots] = useState<Record<number, TaskSnapshot>>({});
  const [taskDetails, setTaskDetails] = useState<Record<number, BountyTaskDetailResponse>>({});
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [lastUpdatedAt, setLastUpdatedAt] = useState<string | null>(null);
  const [selectedTaskId, setSelectedTaskId] = useState<number | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<TaskUiStatus | "全部">("全部");
  const [submissionFilter, setSubmissionFilter] = useState<SubmissionFilter>(() => coerceSubmissionFilter(searchParams.get("submission")));
  const [managingTaskId, setManagingTaskId] = useState<number | null>(null);
  const [toast, setToast] = useState<ToastState | null>(null);
  const [highlightTaskId, setHighlightTaskId] = useState<number | null>(null);
  // 企业任务中心快照带上 userId，避免不同企业账号复用同一工作台缓存。
  const snapshotKey = buildWorkspaceSnapshotStorageKey("enterprise", "task-center", userId ?? "current");

  // 发布、编辑、通知回流都会落到任务中心，再由这些 query 驱动选中和高亮。
  const createdTaskId = Number(searchParams.get("createdTaskId") ?? 0) || null;
  const editedTaskId = Number(searchParams.get("editedTaskId") ?? 0) || null;
  const taskIdFromQuery = Number(searchParams.get("taskId") ?? 0) || null;

  useEffect(() => {
    if (!toast) {
      return undefined;
    }
    const timer = window.setTimeout(() => setToast(null), 3200);
    return () => window.clearTimeout(timer);
  }, [toast]);

  const persistWorkspaceSnapshot = useCallback((
    snapshot: EnterpriseTaskCenterSnapshot,
    updatedAt = new Date().toISOString(),
  ) => {
    // 任务列表、最近提交快照和已打开详情一起落盘，返回工作台时减少白屏。
    writeWorkspaceSnapshot(snapshotKey, snapshot, updatedAt);
    setLastUpdatedAt(updatedAt);
    return updatedAt;
  }, [snapshotKey]);

  const loadAll = useCallback(async (options?: { silent?: boolean; background?: boolean; signal?: AbortSignal }) => {
    const silent = options?.silent ?? false;
    const background = options?.background ?? false;
    if (!silent) {
      setLoading(true);
    }
    setRefreshing(true);
    setLoadError(null);

    try {
      // 聚合接口一次返回企业任务及最近提交，企业资料并行读取只用于壳层身份展示。
      const [profileResponse, taskResponse] = await Promise.all([
        apiRequest<EnterpriseOwnProfileResponse>("/profiles/enterprises/me", { signal: options?.signal }),
        apiRequest<BountyTaskListResponse>("/bounty/enterprise/task-center", { signal: options?.signal }),
      ]);

      // recentSubmissions 属于右侧任务快照，不直接塞回任务列表记录。
      const taskRecords = taskResponse.tasks.map(({ recentSubmissions, ...task }) => task);
      const snapshotEntries = taskResponse.tasks.map((task) => [
        task.taskId,
        {
          records: task.recentSubmissions,
          total: task.submissionCount,
          partial: task.submissionCount > task.recentSubmissions.length,
          loadError: null,
        } satisfies TaskSnapshot,
      ] as const);

      const nextSnapshot = {
        profile: profileResponse,
        tasks: taskRecords,
        snapshots: Object.fromEntries(snapshotEntries),
        taskDetails: {},
      } satisfies EnterpriseTaskCenterSnapshot;
      const updatedAt = persistWorkspaceSnapshot(nextSnapshot);

      startTransition(() => {
        setProfile(profileResponse);
        setTasks(taskRecords);
        setSnapshots(Object.fromEntries(snapshotEntries));
        setLastUpdatedAt(updatedAt);
        setLoading(false);
      });
    } catch (error) {
      if (isAbortError(error)) {
        return;
      }
      if (background) {
        setLoadError(null);
        setLoading(false);
        return;
      }
      setLoadError(buildErrorMessage(error, "企业任务中心数据加载失败，请稍后重试。"));
      setLoading(false);
    } finally {
      setRefreshing(false);
    }
  }, [persistWorkspaceSnapshot]);

  useEffect(() => {
    if (role !== "ENTERPRISE") {
      return undefined;
    }

    const controller = new AbortController();
    const snapshot = readWorkspaceSnapshot<EnterpriseTaskCenterSnapshot>(snapshotKey);

    // 有工作台快照时先展示旧状态，再静默刷新服务端聚合结果。
    if (snapshot?.data) {
      setProfile(snapshot.data.profile);
      setTasks(snapshot.data.tasks);
      setSnapshots(snapshot.data.snapshots);
      setTaskDetails(snapshot.data.taskDetails);
      setLastUpdatedAt(snapshot.updatedAt);
      setLoading(false);
      setLoadError(null);
      void loadAll({ silent: true, background: true, signal: controller.signal });
    } else {
      void loadAll({ signal: controller.signal });
    }

    return () => controller.abort();
  }, [loadAll, role, snapshotKey]);

  useEffect(() => {
    if (!createdTaskId || tasks.length === 0 || highlightTaskId === createdTaskId) {
      return;
    }
    // 新建成功回流后自动选中新任务，减少再去列表里查找的成本。
    if (tasks.some((task) => task.taskId === createdTaskId)) {
      setSelectedTaskId(createdTaskId);
      setHighlightTaskId(createdTaskId);
      setToast({ tone: "success", message: "新任务已发布，可继续查看详情。" });
    }
  }, [createdTaskId, highlightTaskId, tasks]);

  useEffect(() => {
    if (!editedTaskId || tasks.length === 0 || highlightTaskId === editedTaskId) {
      return;
    }
    // 编辑成功回流同样只处理一次高亮，避免重复 toast。
    if (tasks.some((task) => task.taskId === editedTaskId)) {
      setSelectedTaskId(editedTaskId);
      setHighlightTaskId(editedTaskId);
      setToast({ tone: "success", message: "任务内容已更新。" });
    }
  }, [editedTaskId, highlightTaskId, tasks]);

  useEffect(() => {
    if (createdTaskId || editedTaskId || !taskIdFromQuery || tasks.length === 0 || selectedTaskId === taskIdFromQuery) {
      return;
    }
    if (tasks.some((task) => task.taskId === taskIdFromQuery)) {
      setSelectedTaskId(taskIdFromQuery);
    }
  }, [createdTaskId, editedTaskId, selectedTaskId, taskIdFromQuery, tasks]);

  useEffect(() => {
    if (!selectedTaskId || taskDetails[selectedTaskId]) {
      return;
    }

    let cancelled = false;
    // 详情是按需加载；列表先给摘要，选中后再补完整 description 供右侧预览。
    void apiRequest<BountyTaskDetailResponse>(`/bounty/tasks/${selectedTaskId}`)
      .then((response) => {
        if (cancelled) {
          return;
        }
        const updatedAt = new Date().toISOString();
        setTaskDetails((current) => {
          const nextTaskDetails = {
            ...current,
            [selectedTaskId]: response,
          };
          writeWorkspaceSnapshot(snapshotKey, {
            profile,
            tasks,
            snapshots,
            taskDetails: nextTaskDetails,
          } satisfies EnterpriseTaskCenterSnapshot, updatedAt);
          return nextTaskDetails;
        });
        setLastUpdatedAt(updatedAt);
      })
      .catch((error) => {
        if (cancelled) {
          return;
        }
        setToast({ tone: "error", message: buildErrorMessage(error, "任务详情读取失败，请稍后重试。") });
      });

    return () => {
      cancelled = true;
    };
  }, [selectedTaskId, taskDetails]);

  const taskUiStatusMap = useMemo(() => {
    const entries = tasks.map((task) => [task.taskId, getEnterpriseTaskUiStatus(task)] as const);
    return Object.fromEntries(entries) as Record<number, TaskUiStatus>;
  }, [tasks]);

  const taskMetricsMap = useMemo(() => {
    const entries = tasks.map((task) => [task.taskId, {
      pendingCount: task.pendingCount,
      contactedCount: task.contactedCount,
      reviewedCount: task.reviewedCount,
    }] as const);
    return Object.fromEntries(entries) as Record<number, { pendingCount: number; contactedCount: number; reviewedCount: number }>;
  }, [tasks]);

  const filteredTasks = useMemo(() => {
    // 后端负责企业归属与统计，前端只做当前工作区内的二次搜索和视图筛选。
    return tasks.filter((task) => {
      const uiStatus = taskUiStatusMap[task.taskId];
      const metrics = taskMetricsMap[task.taskId] ?? { pendingCount: 0, contactedCount: 0, reviewedCount: 0 };
      const description = taskDetails[task.taskId]?.description ?? task.descriptionSummary;
      const parsed = parseEnterpriseTaskDescription(description);
      const matchSearch =
        !searchQuery.trim()
        || [task.title, task.descriptionSummary, parsed.summary, parsed.background, parsed.problem]
          .filter(Boolean)
          .some((value) => value.toLowerCase().includes(searchQuery.trim().toLowerCase()));
      const matchStatus = statusFilter === "全部" || uiStatus === statusFilter;
      const matchSubmission =
        submissionFilter === "全部"
        || (submissionFilter === "有提交" && task.submissionCount > 0)
        || (submissionFilter === "待处理" && metrics.pendingCount > 0)
        || (submissionFilter === "已有继续接触结果" && metrics.contactedCount > 0);
      return matchSearch && matchStatus && matchSubmission;
    });
  }, [searchQuery, statusFilter, submissionFilter, taskDetails, taskMetricsMap, taskUiStatusMap, tasks]);

  const selectedTask = useMemo(() => tasks.find((task) => task.taskId === selectedTaskId) ?? null, [selectedTaskId, tasks]);
  const selectedTaskDetail = selectedTask ? taskDetails[selectedTask.taskId] ?? null : null;
  const selectedSnapshot = selectedTask ? snapshots[selectedTask.taskId] : undefined;
  const selectedTaskParsed = selectedTaskDetail ? parseEnterpriseTaskDescription(selectedTaskDetail.description) : null;
  const selectedReviewSubmissionId = selectedSnapshot?.records.find((submission) => submission.status === "SUBMITTED" || submission.status === "REVIEWING")?.submissionId
    ?? selectedSnapshot?.records[0]?.submissionId
    ?? null;

  const stats = useMemo(() => {
    const activeTasks = tasks.filter((task) => task.status === "OPEN").length;
    const expiringSoon = tasks.filter((task) => taskUiStatusMap[task.taskId] === "即将截止").length;
    const pendingSubmissions = tasks.reduce((sum, task) => sum + (taskMetricsMap[task.taskId]?.pendingCount ?? 0), 0);
    const closedTasks = tasks.filter((task) => task.status === "CLOSED").length;
    return { activeTasks, expiringSoon, pendingSubmissions, closedTasks };
  }, [taskMetricsMap, taskUiStatusMap, tasks]);
  const canReopenTasks = !profile || profile.approvalStatus === "APPROVED";
  const reopenBlockedMessage = profile?.approvalStatus === "REJECTED"
    ? "企业认证补充完成后即可重新开放任务"
    : "企业认证通过后即可重新开放任务";

  const handleManageTask = async (task: BountyTaskItem) => {
    const nextAction = task.status === "OPEN" ? "CLOSE" : "REOPEN";
    setManagingTaskId(task.taskId);
    try {
      // 关闭/重开后同步更新列表和已打开详情，再写回工作台快照。
      const response = await apiRequest<BountyTaskManageResponse>(`/bounty/tasks/${task.taskId}/manage`, {
        method: "POST",
        body: JSON.stringify({ action: nextAction }),
      });
      const nextTasks = tasks.map((item) => (
        item.taskId === task.taskId ? { ...item, status: response.status, updatedAt: response.updatedAt } : item
      ));
      const currentDetail = taskDetails[task.taskId];
      const nextTaskDetails = currentDetail
        ? {
          ...taskDetails,
          [task.taskId]: {
            ...currentDetail,
            status: response.status,
            updatedAt: response.updatedAt,
            closedAt: response.status === "CLOSED" ? response.updatedAt : null,
          },
        }
        : taskDetails;
      const updatedAt = persistWorkspaceSnapshot({
        profile,
        tasks: nextTasks,
        snapshots,
        taskDetails: nextTaskDetails,
      });
      setTasks(nextTasks);
      setTaskDetails(nextTaskDetails);
      setToast({
        tone: "success",
        message: nextAction === "CLOSE" ? "任务已关闭，学生端将不再继续接收新提交。" : "任务已重新开放，可以继续接收学生提交。",
      });
      setLastUpdatedAt(updatedAt);
    } catch (error) {
      setToast({ tone: "error", message: buildErrorMessage(error, "任务状态更新失败，请稍后重试。") });
    } finally {
      setManagingTaskId(null);
    }
  };

  if (loading && tasks.length === 0 && !loadError) {
    const accountName = resolveEnterpriseAccountName(profile);
    return (
      <EnterpriseTaskCenterSkeleton
        displayName={accountName}
        companyName={profile?.companyName ?? null}
      />
    );
  }

  const resolvedLogoUrl = buildEnterpriseLogoUrl(profile?.logoUrl ?? null, profile?.logoUpdatedAt);
  const accountName = resolveEnterpriseAccountName(profile);

  return (
    <EnterpriseWorkspaceShell
      sectionLabel="Enterprise Task Center"
      title="企业任务中心"
      displayName={accountName}
      companyName={profile?.companyName ?? null}
      logoUrl={resolvedLogoUrl}
      userSubtitle={profile?.companyName?.trim() || "企业账号"}
      navItems={getEnterpriseWorkspaceNavItems("tasks")}
      loading={refreshing || managingTaskId !== null}
      lastUpdatedAt={lastUpdatedAt}
      onRefresh={() => {
        void loadAll({ silent: true });
      }}
    >
      <motion.div variants={containerVariants} initial="hidden" animate="show" className="space-y-6">
        {loadError ? (
          <motion.section variants={itemVariants} className="rounded-[1.5rem] border border-amber-200 bg-amber-50/90 px-5 py-4 text-[16px] leading-7 text-amber-900 shadow-[0_10px_28px_rgba(245,158,11,0.12)]">
            <div className="flex items-start gap-3">
              <AlertCircle size={18} className="mt-0.5 shrink-0 text-amber-600" />
              <div>{loadError}</div>
            </div>
          </motion.section>
        ) : null}

        {profile && profile.approvalStatus !== "APPROVED" ? (
          <motion.section variants={itemVariants} className="rounded-[1.5rem] border border-amber-200 bg-amber-50/90 px-5 py-4 text-[16px] leading-7 text-amber-900 shadow-[0_10px_28px_rgba(245,158,11,0.12)]">
            <div className="flex items-start gap-3">
              <AlertCircle size={18} className="mt-0.5 shrink-0 text-amber-600" />
              <div>
                <div className="font-semibold">
                  {profile.approvalStatus === "REJECTED" ? "企业认证待补充" : "企业认证审核中"}
                </div>
                <div className="mt-1">
                  审核完成前，可继续查看现有任务并整理发布内容；新建任务与重新开放任务将在认证通过后开放。
                </div>
              </div>
            </div>
          </motion.section>
        ) : null}

        <motion.section variants={itemVariants} className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <Link to="/enterprise/dashboard" className="mb-4 inline-flex items-center text-[16px] font-medium text-slate-500 transition-colors hover:text-slate-900">
              <ArrowLeft size={16} className="mr-1.5" />
              返回工作台
            </Link>
            <h1 className="text-[36px] font-bold tracking-tight text-slate-900">任务中心</h1>
            <p className="mt-2 text-[17px] text-slate-500">
              查看任务进展、最新提交与处理状态。
            </p>
          </div>
          <Link
            to="/enterprise/tasks/create"
            className="inline-flex items-center justify-center rounded-full bg-slate-900 px-6 py-2.5 text-[16px] font-semibold !text-white shadow-md transition-all hover:bg-slate-800 hover:!text-white hover:shadow-lg visited:!text-white"
          >
            <Plus size={16} className="mr-2" />
            发布新任务
          </Link>
        </motion.section>

        <motion.section variants={itemVariants} className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          <div className="rounded-[1.5rem] border border-white/80 bg-white/85 p-5 shadow-[0_8px_30px_rgba(148,163,184,0.08)] backdrop-blur-xl">
            <div className="flex items-center gap-3 text-slate-500">
              <Target size={18} className="text-indigo-500" />
              <span className="text-[16px] font-semibold">进行中任务</span>
            </div>
            <div className="mt-3 text-[36px] font-black text-slate-900">{stats.activeTasks}</div>
          </div>
          <div className="rounded-[1.5rem] border border-white/80 bg-white/85 p-5 shadow-[0_8px_30px_rgba(148,163,184,0.08)] backdrop-blur-xl">
            <div className="flex items-center gap-3 text-slate-500">
              <Clock3 size={18} className="text-amber-500" />
              <span className="text-[16px] font-semibold">即将截止</span>
            </div>
            <div className="mt-3 text-[36px] font-black text-slate-900">{stats.expiringSoon}</div>
          </div>
          <div className="rounded-[1.5rem] border border-white/80 bg-white/85 p-5 shadow-[0_8px_30px_rgba(148,163,184,0.08)] backdrop-blur-xl">
            <div className="flex items-center gap-3 text-slate-500">
              <Inbox size={18} className="text-rose-500" />
              <span className="text-[16px] font-semibold">有提交待处理</span>
            </div>
            <div className="mt-3 flex items-baseline gap-2">
              <span className="text-[36px] font-black text-slate-900">{stats.pendingSubmissions}</span>
              <span className="text-[16px] font-medium text-slate-400">份学生提交</span>
            </div>
          </div>
          <div className="rounded-[1.5rem] border border-white/80 bg-white/85 p-5 shadow-[0_8px_30px_rgba(148,163,184,0.08)] backdrop-blur-xl">
            <div className="flex items-center gap-3 text-slate-500">
              <Briefcase size={18} className="text-slate-400" />
              <span className="text-[16px] font-semibold">历史已结束</span>
            </div>
            <div className="mt-3 text-[36px] font-black text-slate-900">{stats.closedTasks}</div>
          </div>
        </motion.section>

        <motion.section variants={itemVariants} className="flex flex-col gap-4 rounded-[1.5rem] border border-white/80 bg-white/60 p-2 shadow-sm backdrop-blur-md sm:flex-row sm:items-center sm:justify-between">
          <div className="flex flex-1 items-center gap-2 px-3">
            <Search size={18} className="text-slate-400" />
            <input
              type="text"
              placeholder="搜索任务标题或关键词..."
              className="w-full border-none bg-transparent text-[16px] font-medium text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-0"
              value={searchQuery}
              onChange={(event) => setSearchQuery(event.target.value)}
            />
          </div>
          <div className="flex items-center gap-2 overflow-x-auto p-1">
            {(["全部", "进行中", "即将截止", "已完成筛选", "已结束"] as const).map((status) => (
              <button
                key={status}
                type="button"
                onClick={() => setStatusFilter(status)}
                className={joinClasses(
                  "whitespace-nowrap rounded-full px-4 py-1.5 text-[16px] font-medium transition-all",
                  statusFilter === status
                    ? "border border-slate-200/60 bg-white text-slate-900 shadow-sm"
                    : "text-slate-500 hover:bg-white/50 hover:text-slate-800",
                )}
              >
                {status}
              </button>
            ))}
            <div className="mx-2 hidden h-6 w-px bg-slate-200 sm:block" />
            {(["全部", "有提交", "待处理", "已有继续接触结果"] as const).map((filterValue) => (
              <button
                key={filterValue}
                type="button"
                onClick={() => setSubmissionFilter(filterValue)}
                className={joinClasses(
                  "whitespace-nowrap rounded-full px-4 py-1.5 text-[16px] font-medium transition-all",
                  submissionFilter === filterValue
                    ? "border border-slate-200/60 bg-white text-slate-900 shadow-sm"
                    : "text-slate-500 hover:bg-white/50 hover:text-slate-800",
                )}
              >
                {filterValue}
              </button>
            ))}
            <div className="flex items-center gap-1.5 whitespace-nowrap rounded-full border border-slate-200/70 bg-white/70 px-4 py-1.5 text-[16px] font-medium text-slate-600">
              <Filter size={14} />
              按最新提交筛选
            </div>
          </div>
        </motion.section>

        <motion.section variants={itemVariants} className="flex items-start gap-6 relative min-h-[500px]">
          <motion.div
            className={joinClasses(
              "flex flex-1 flex-col gap-3 transition-[width,opacity] duration-200 ease-out",
              selectedTaskId ? "hidden lg:flex lg:w-2/3" : "w-full",
            )}
          >
            {filteredTasks.length === 0 ? (
              <div className="flex flex-col items-center justify-center rounded-[2rem] border border-dashed border-slate-300 bg-white/50 py-20 text-center">
                <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-slate-100 text-slate-400">
                  <Inbox size={24} />
                </div>
                <h3 className="text-[22px] font-semibold text-slate-900">暂未找到匹配任务</h3>
                <p className="mt-1 text-[16px] text-slate-500">可以调整搜索或筛选条件，或者直接发布新的企业任务。</p>
                <Link
                  to="/enterprise/tasks/create"
                  className="mt-5 inline-flex items-center rounded-full bg-slate-900 px-5 py-2.5 text-[16px] font-semibold !text-white transition-colors hover:bg-slate-800 hover:!text-white visited:!text-white"
                >
                  <Plus size={16} className="mr-2" />
                  发布新任务
                </Link>
              </div>
            ) : (
              filteredTasks.map((task) => {
                const uiStatus = taskUiStatusMap[task.taskId];
                const metrics = taskMetricsMap[task.taskId] ?? { pendingCount: 0, contactedCount: 0, reviewedCount: 0 };
                const drawerDescription = taskDetails[task.taskId]?.description ?? task.descriptionSummary;
                const parsed = parseEnterpriseTaskDescription(drawerDescription);
                const canReopen = task.status === "CLOSED" && task.acceptedSubmissionId === null;
                const canClose = task.status === "OPEN";
                const reopenBlockedByApproval = canReopen && !canReopenTasks;
                const highlight = highlightTaskId === task.taskId;

                return (
                  <motion.div
                    key={task.taskId}
                    onClick={() => setSelectedTaskId(task.taskId)}
                    className={joinClasses(
                      "group relative flex cursor-pointer flex-col gap-4 rounded-[1.5rem] border bg-white/85 p-5 transition-all backdrop-blur-xl hover:shadow-[0_12px_40px_rgba(148,163,184,0.12)]",
                      selectedTaskId === task.taskId ? "border-indigo-400 shadow-md ring-1 ring-indigo-400/20" : "border-white/80 shadow-sm hover:border-slate-300",
                      highlight && "ring-2 ring-emerald-300/60",
                    )}
                  >
                    <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-start">
                      <div className="flex-1 space-y-2">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className={joinClasses("rounded-md border px-2 py-0.5 text-[13px] font-bold", STATUS_CONFIG[uiStatus].bg, STATUS_CONFIG[uiStatus].color, STATUS_CONFIG[uiStatus].border)}>
                            {uiStatus}
                          </span>
                          <span className="border-l border-slate-200 pl-2 text-[14px] font-medium text-slate-400">
                            ID: TSK-{String(task.taskId).padStart(4, "0")}
                          </span>
                        </div>
                        <h3 className="text-[22px] font-bold text-slate-900 transition-colors group-hover:text-indigo-700">{task.title}</h3>
                        <div className="flex flex-wrap items-center gap-4 text-[14px] font-medium text-slate-500">
                          <span className="flex items-center gap-1.5">
                            <CalendarDays size={14} className="text-slate-400" />
                            截止：{formatShortDate(task.deadlineAt)}
                          </span>
                          <span className="flex items-center gap-1.5">
                            <RefreshCw size={14} className="text-slate-400" />
                            最近更新：{formatShortDate(task.updatedAt)}
                          </span>
                        </div>
                        <p className="line-clamp-2 text-[16px] leading-7 text-slate-600">{summarizeTaskDescription(parsed)}</p>
                      </div>

                      <div className="flex shrink-0 flex-row items-center gap-3 sm:flex-col sm:items-end">
                        <div className="flex gap-4 rounded-xl border border-slate-100 bg-slate-50 px-4 py-2 sm:gap-6">
                          <div className="text-center">
                            <div className="text-[13px] font-bold text-slate-500">总提交</div>
                            <div className="mt-0.5 text-[17px] font-black text-slate-700">{task.submissionCount}</div>
                          </div>
                          <div className="w-px bg-slate-200" />
                          <div className="text-center">
                            <div className="text-[13px] font-bold text-slate-500">待处理</div>
                            <div className={joinClasses("mt-0.5 text-[17px] font-black", metrics.pendingCount > 0 ? "text-amber-500" : "text-slate-400")}>
                              {metrics.pendingCount}
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>

                    <div className="flex items-center justify-between border-t border-slate-100 pt-4">
                      <div className="flex items-center gap-1.5 rounded-md bg-emerald-50 px-2 py-1 text-[14px] font-medium text-emerald-600">
                        <UserCheck size={14} />
                        已继续接触 {metrics.contactedCount} 人
                      </div>

                      <div className="flex flex-wrap items-center gap-2">
                        <button
                          type="button"
                          onClick={(event) => {
                            event.stopPropagation();
                            setSelectedTaskId(task.taskId);
                          }}
                          className="hidden h-8 items-center justify-center rounded-full border border-slate-200 bg-white px-4 text-[14px] font-semibold text-slate-600 transition-colors hover:bg-slate-50 hover:text-slate-900 sm:inline-flex"
                        >
                          查看详情
                        </button>
                        <Link
                          to={`/enterprise/tasks/${task.taskId}`}
                          onClick={(event) => event.stopPropagation()}
                          className="inline-flex h-8 items-center justify-center gap-1.5 rounded-full bg-slate-900 px-4 text-[14px] font-semibold !text-white transition-colors hover:bg-slate-800 hover:!text-white visited:!text-white"
                        >
                          进入审核工作区
                          <ChevronRight size={14} />
                        </Link>
                        <button
                          type="button"
                          onClick={(event) => {
                            event.stopPropagation();
                            void handleManageTask(task);
                          }}
                          disabled={managingTaskId === task.taskId || (!canClose && !canReopen) || reopenBlockedByApproval}
                          className={joinClasses(
                            "inline-flex h-8 items-center justify-center rounded-full border px-4 text-[14px] font-semibold transition-colors",
                            (canClose || canReopen) && !reopenBlockedByApproval
                              ? "border-slate-200 bg-white text-slate-600 hover:bg-slate-50 hover:text-slate-900"
                              : "cursor-not-allowed border-slate-200 bg-slate-100 text-slate-400",
                          )}
                          title={
                            task.acceptedSubmissionId
                              ? "已有中选结果的任务不能再重新开放"
                              : reopenBlockedByApproval
                                ? reopenBlockedMessage
                                : undefined
                          }
                        >
                          {task.status === "OPEN" ? "关闭任务" : canReopen ? "重新开放" : "已完成筛选"}
                        </button>
                      </div>
                    </div>
                  </motion.div>
                );
              })
            )}
          </motion.div>

          <AnimatePresence initial={false}>
            {selectedTask ? (
              <motion.div
                initial={{ opacity: 0, x: 18 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: 12 }}
                transition={panelTransition}
                className="fixed inset-y-0 right-0 z-50 w-full shrink-0 sm:w-[420px] lg:sticky lg:top-28 lg:block lg:h-[calc(100vh-8rem)] lg:w-1/3 lg:self-start"
              >
                <div className="flex h-full flex-col rounded-none border-l border-white/80 bg-white/95 p-6 shadow-[0_24px_58px_rgba(15,23,42,0.15)] backdrop-blur-2xl sm:rounded-[2rem] sm:border lg:max-h-[calc(100vh-8rem)] lg:overflow-hidden lg:p-7">
                  <div className="border-b border-slate-100 pb-4">
                    <div className="flex items-start justify-between">
                      <div className="flex min-h-[3.65rem] items-center">
                        <h2 className="pr-4 text-[22px] font-bold leading-tight text-slate-950">{selectedTask.title}</h2>
                      </div>
                      <button
                        type="button"
                        onClick={() => setSelectedTaskId(null)}
                        className="shrink-0 rounded-full bg-slate-50 p-1.5 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700"
                      >
                        <X size={18} />
                      </button>
                    </div>
                  </div>

                  <div className="flex-1 overflow-y-auto py-5 space-y-6">
                    <div className="flex flex-wrap gap-2">
                      <span className={joinClasses("rounded-full border px-3 py-1 text-[14px] font-bold", STATUS_CONFIG[taskUiStatusMap[selectedTask.taskId]].bg, STATUS_CONFIG[taskUiStatusMap[selectedTask.taskId]].color, STATUS_CONFIG[taskUiStatusMap[selectedTask.taskId]].border)}>
                        状态：{taskUiStatusMap[selectedTask.taskId]}
                      </span>
                      <span className="rounded-full border border-slate-200 bg-slate-100 px-3 py-1 text-[14px] font-medium text-slate-600">
                        截止：{formatShortDate(selectedTask.deadlineAt)}
                      </span>
                    </div>

                    <div className="space-y-2">
                      <div className="flex items-center gap-1.5 text-[14px] font-bold text-slate-900">
                        <Briefcase size={14} className="text-indigo-500" />
                        任务背景
                      </div>
                      <div className="rounded-xl border border-slate-100 bg-slate-50 p-3.5 text-[16px] leading-relaxed text-slate-600">
                        {selectedTaskParsed?.background || selectedTask.descriptionSummary || "待补充任务背景。"}
                      </div>
                    </div>

                    {selectedTaskParsed?.problem ? (
                      <div className="space-y-2">
                        <div className="flex items-center gap-1.5 text-[14px] font-bold text-slate-900">
                          <Target size={14} className="text-amber-500" />
                          希望解决的问题
                        </div>
                        <div className="rounded-xl border border-slate-100 bg-white p-3.5 text-[16px] leading-relaxed text-slate-600 shadow-sm">
                          {selectedTaskParsed.problem}
                        </div>
                      </div>
                    ) : null}

                    <div className="space-y-2">
                      <div className="flex items-center gap-1.5 text-[14px] font-bold text-slate-900">
                        <Inbox size={14} className="text-emerald-500" />
                        交付要求
                      </div>
                      <div className="rounded-xl border border-slate-100 bg-white p-3.5 text-[16px] leading-relaxed text-slate-600 shadow-sm">
                        {selectedTaskParsed?.deliveryWhat || "待补充交付要求。"}
                      </div>
                      {selectedTaskParsed?.deliveryFormats.length ? (
                        <div className="flex flex-wrap gap-2">
                          {selectedTaskParsed.deliveryFormats.map((format) => (
                            <span key={format} className="rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-[13px] text-slate-500">
                              {format}
                            </span>
                          ))}
                        </div>
                      ) : null}
                    </div>

                    <div className="space-y-2">
                      <div className="flex items-center gap-1.5 text-[14px] font-bold text-slate-900">
                        <Target size={14} className="text-amber-500" />
                        奖励说明
                      </div>
                      <div className="rounded-xl border border-amber-100 bg-amber-50/50 p-3.5 text-[16px] font-medium text-amber-800">
                        {selectedTask.rewardDescription || "待补充"}
                      </div>
                    </div>

                    <div className="grid grid-cols-2 gap-3">
                      <div className="rounded-xl border border-slate-100 bg-white p-3 shadow-sm">
                        <div className="text-[13px] font-bold text-slate-400">提交数量</div>
                        <div className="mt-1 text-[30px] font-black text-slate-800">{selectedTask.submissionCount}</div>
                      </div>
                      <div className="rounded-xl border border-emerald-100 bg-emerald-50/50 p-3 shadow-sm">
                        <div className="text-[13px] font-bold text-emerald-600">已继续接触</div>
                        <div className="mt-1 text-[30px] font-black text-emerald-700">{taskMetricsMap[selectedTask.taskId]?.contactedCount ?? 0}</div>
                      </div>
                      <div className="col-span-2 flex items-center justify-between rounded-xl border border-amber-100 bg-white p-3 shadow-sm">
                        <div>
                          <div className="text-[13px] font-bold text-amber-600">待处理的新提交</div>
                        <div className="mt-1 text-[22px] font-black text-amber-600">{taskMetricsMap[selectedTask.taskId]?.pendingCount ?? 0}</div>
                      </div>
                      {(taskMetricsMap[selectedTask.taskId]?.pendingCount ?? 0) > 0 ? (
                          <div className="rounded-md bg-amber-50 px-2 py-1 text-[14px] font-semibold text-amber-500">需优先处理</div>
                      ) : null}
                    </div>
                  </div>

                    <div className="text-center text-[13px] text-slate-400">
                      最近一次处理时间：{formatDateTime(selectedTask.updatedAt ?? selectedTask.createdAt ?? new Date().toISOString())}
                    </div>

                    <div className="space-y-3">
                      <div className="flex items-center justify-between">
                        <div className="text-[14px] font-bold text-slate-900">最近提交与处理状态</div>
                        {selectedSnapshot?.partial ? (
                          <span className="rounded-full border border-amber-200 bg-amber-50 px-2.5 py-1 text-[13px] font-semibold text-amber-700">
                            显示 {selectedSnapshot.records.length}/{selectedSnapshot.total} 份
                          </span>
                        ) : null}
                      </div>
                      {selectedSnapshot?.loadError ? (
                        <div className="rounded-xl border border-amber-200 bg-amber-50/70 p-3 text-[14px] leading-6 text-amber-800">
                          {selectedSnapshot.loadError}
                        </div>
                      ) : null}
                      {selectedSnapshot && selectedSnapshot.records.length > 0 ? (
                        <div className="space-y-3">
                          {selectedSnapshot.records.slice(0, 6).map((submission) => {
                            const statusMeta = getSubmissionStatusMeta(submission.status);
                            return (
                              <div key={submission.submissionId} className="rounded-xl border border-slate-100 bg-slate-50/80 p-3.5">
                                <div className="flex items-start justify-between gap-3">
                                  <div className="flex min-w-0 items-start gap-3">
                                    <StudentIdentityAvatar
                                      userId={submission.studentUserId}
                                      role="STUDENT"
                                      displayName={submission.studentName}
                                      avatar={submission.studentAvatar}
                                      avatarPath={buildStudentAvatarPath(submission.studentUserId, submission.studentAvatar?.updatedAt)}
                                      className="h-10 w-10 border border-white bg-white shadow-sm"
                                      textClassName="text-[15px]"
                                    />
                                    <div className="min-w-0">
                                      <div className="text-[16px] font-semibold text-slate-900">{submission.studentName}</div>
                                      <div className="mt-1 text-[14px] text-slate-500">提交于 {formatRelativeTime(submission.createdAt)}</div>
                                    </div>
                                  </div>
                                  <span className={joinClasses("inline-flex rounded-full px-2.5 py-1 text-[13px] font-semibold", statusMeta.className)}>
                                    {statusMeta.label}
                                  </span>
                                </div>
                                <p className="mt-2 text-[16px] leading-6 text-slate-600">
                                  {submission.contentSummary || submission.contentText || "这份提交暂未附加补充说明。"}
                                </p>
                                <div className="mt-2 flex flex-wrap gap-2">
                                  {submission.portraitTags.slice(0, 3).map((tag) => (
                                    <span key={tag} className="rounded-full border border-slate-200 bg-white px-2 py-0.5 text-[13px] text-slate-500">
                                      {tag}
                                    </span>
                                  ))}
                                  <span className="rounded-full border border-slate-200 bg-white px-2 py-0.5 text-[13px] text-slate-500">
                                    近 7 天社区分 {submission.communityScore7d}
                                  </span>
                                </div>
                                <div className="mt-3 flex justify-end">
                                  <Link
                                    to={buildEnterpriseTaskReviewHref(selectedTask.taskId, submission.submissionId)}
                                    className="inline-flex items-center text-[14px] font-bold text-indigo-600 transition-colors hover:text-indigo-700"
                                  >
                                    继续处理该提交
                                    <ChevronRight size={14} className="ml-1" />
                                  </Link>
                                </div>
                              </div>
                            );
                          })}
                        </div>
                      ) : (
                        <div className="rounded-xl border border-dashed border-slate-200 bg-slate-50/70 px-4 py-8 text-center text-[16px] text-slate-500">
                          该任务暂未收到新的学生提交。
                        </div>
                      )}
                    </div>
                  </div>

                  <div className="mt-auto flex flex-col gap-3 border-t border-slate-100 pt-4">
                    <Link
                      to={buildEnterpriseTaskReviewHref(selectedTask.taskId, selectedReviewSubmissionId)}
                      className="inline-flex w-full items-center justify-center rounded-xl bg-slate-900 py-3.5 text-[16px] font-bold !text-white shadow-md transition-all hover:-translate-y-0.5 hover:bg-slate-800 hover:!text-white visited:!text-white"
                    >
                      进入任务审核工作区
                      <ChevronRight size={16} className="ml-2" />
                    </Link>
                    <div className="grid grid-cols-2 gap-3">
                      {selectedTask.acceptedSubmissionId === null ? (
                        <Link
                          to={`/enterprise/tasks/create?editTaskId=${selectedTask.taskId}`}
                          className="inline-flex items-center justify-center rounded-xl border border-slate-200 bg-white py-2.5 text-[14px] font-bold text-slate-600 transition-colors hover:bg-slate-50"
                        >
                          编辑任务信息
                        </Link>
                      ) : (
                        <button
                          type="button"
                          disabled
                          className="inline-flex cursor-not-allowed items-center justify-center rounded-xl border border-slate-200 bg-slate-100 py-2.5 text-[14px] font-bold text-slate-400"
                          title="已有中选结果的任务不再允许编辑"
                        >
                          已完成筛选
                        </button>
                      )}
                      <button
                        type="button"
                        onClick={() => void handleManageTask(selectedTask)}
                        disabled={
                          managingTaskId === selectedTask.taskId
                          || (selectedTask.status === "CLOSED" && selectedTask.acceptedSubmissionId !== null)
                          || (selectedTask.status === "CLOSED" && selectedTask.acceptedSubmissionId === null && !canReopenTasks)
                        }
                        className={joinClasses(
                          "inline-flex items-center justify-center rounded-xl border px-3 py-2.5 text-[14px] font-bold transition-colors",
                          (selectedTask.status === "CLOSED" && selectedTask.acceptedSubmissionId !== null)
                          || (selectedTask.status === "CLOSED" && selectedTask.acceptedSubmissionId === null && !canReopenTasks)
                            ? "cursor-not-allowed border-slate-200 bg-slate-100 text-slate-400"
                            : selectedTask.status === "OPEN"
                              ? "border-rose-200 bg-rose-50 text-rose-600 hover:bg-rose-100"
                              : "border-emerald-200 bg-emerald-50 text-emerald-700 hover:bg-emerald-100",
                        )}
                        title={
                          selectedTask.acceptedSubmissionId
                            ? "已有中选结果的任务不能再重新开放"
                            : selectedTask.status === "CLOSED" && !canReopenTasks
                              ? reopenBlockedMessage
                              : "切换任务开启状态"
                        }
                      >
                        {selectedTask.status === "OPEN" ? "关闭任务" : "重新开放"}
                      </button>
                    </div>
                    <div className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-[14px] leading-6 text-slate-500">
                      {selectedTask.acceptedSubmissionId === null
                        ? "可在这里查看任务进展，也可返回发布页继续完善内容。"
                        : "已有中选结果，任务内容已锁定。"}
                    </div>
                  </div>
                </div>
              </motion.div>
            ) : null}
          </AnimatePresence>
        </motion.section>
      </motion.div>

      <AnimatePresence>
        {toast ? (
          <motion.div
            initial={{ opacity: 0, y: 24 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 24 }}
            className="pointer-events-none fixed bottom-6 left-1/2 z-50 -translate-x-1/2"
          >
            <div
              className={joinClasses(
                "rounded-full px-5 py-3 text-[16px] font-semibold shadow-[0_14px_40px_rgba(15,23,42,0.18)]",
                toast.tone === "success" && "bg-slate-900 text-white",
                toast.tone === "error" && "bg-rose-600 text-white",
                toast.tone === "info" && "bg-white text-slate-700",
              )}
            >
              {toast.message}
            </div>
          </motion.div>
        ) : null}
      </AnimatePresence>
    </EnterpriseWorkspaceShell>
  );
}
