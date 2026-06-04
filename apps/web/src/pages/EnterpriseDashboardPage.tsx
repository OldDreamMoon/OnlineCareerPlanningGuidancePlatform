import { AnimatePresence, motion } from "framer-motion";
import {
  AlertCircle,
  ArrowRight,
  BellRing,
  Briefcase,
  Building2,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  ClipboardList,
  Clock,
  Compass,
  FileSignature,
  FileUp,
  Inbox,
  LayoutDashboard,
  MailOpen,
  MessageSquare,
  ShieldCheck,
  Target,
  UserPlus,
  X,
  type LucideIcon,
} from "lucide-react";
import { startTransition, useEffect, useRef, useState, type CSSProperties, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import EnterpriseIdentityLogo from "../components/avatar/EnterpriseIdentityLogo";
import StudentIdentityAvatar from "../components/avatar/StudentIdentityAvatar";
import WorkspacePageLoadingScreen from "../components/WorkspacePageLoadingScreen";
import WorkspaceRoleTopbar from "../components/workspace/WorkspaceRoleTopbar";
import { ApiClientError, apiRequest, buildQuery, isAbortError } from "../lib/apiClient";
import { resolveEnterpriseAccountName } from "../lib/enterpriseIdentity";
import { buildEnterpriseLogoUrl } from "../lib/enterpriseLogo";
import { buildEnterpriseTaskReviewHref } from "../lib/enterpriseTasks";
import {
  getNotificationActionLabel,
  getNotificationCategoryLabel,
  listNotifications,
  resolveNotificationHref,
  type NotificationRecord,
} from "../lib/notifications";
import { getEnterpriseWorkspaceNavItems } from "../lib/workspaceNav";
import {
  buildWorkspaceSnapshotStorageKey,
  readWorkspaceSnapshot,
  writeWorkspaceSnapshot,
} from "../lib/workspaceSnapshot";
import {
  formatDate,
  formatDateTime,
  formatRelativeTime as formatRelativeTimeByBrowserTimezone,
  toTimestamp,
} from "../lib/formatters";
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

type CertificationOwnViewResponse = {
  userId: number;
  role: string;
  approvalStatus: string;
  currentSubmission: CertificationSubmission | null;
  submissions: CertificationSubmission[];
};

type CertificationSubmission = {
  submissionId: number;
  userId: number;
  role: string;
  realName: string;
  companyName: string;
  jobTitle: string;
  status: string;
  current: boolean;
  reviewNote: string | null;
  previousSubmissionId: number | null;
  submittedAt: string | null;
  reviewedAt: string | null;
  assets: CertificationAsset[];
};

type CertificationAsset = {
  assetId: number;
  bucket: string;
  objectKey: string;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
  lifecycleStatus: string;
  deleteReason: string | null;
  uploadedAt: string | null;
  deletedAt: string | null;
};

type EnterpriseTaskCenterResponse = {
  tasks: TaskCenterTaskItem[];
  total: number;
};

type BountyTaskItem = {
  taskId: number;
  enterpriseUserId: number;
  enterpriseName: string;
  enterpriseLogoUrl: string | null;
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
  recentSubmissions: BountySubmissionItem[];
};

type BountySubmissionItem = {
  submissionId: number;
  studentUserId: number;
  studentName: string;
  studentAvatar?: StudentAvatarMeta | null;
  status: string;
  contentSummary: string;
  contentText?: string | null;
  attachmentLinks?: string[];
  communityScore7d: number;
  portraitTags: string[];
  portraitUpdatedAt?: string | null;
  reviewComment: string | null;
  reviewedAt: string | null;
  createdAt: string | null;
};

type TaskSubmissionSnapshot = {
  taskId: number;
  total: number;
  partial: boolean;
  records: BountySubmissionItem[];
};

type EnterpriseDashboardBundle = {
  profile: EnterpriseOwnProfileResponse;
  certification: CertificationOwnViewResponse;
  tasks: BountyTaskItem[];
  taskSnapshots: TaskSubmissionSnapshot[];
  notifications: NotificationRecord[];
};

type DeadlineMeta = {
  label: string;
  toneClassName: string;
  urgent: boolean;
};

type PriorityBanner = {
  sectionLabel: string;
  icon: LucideIcon;
  iconClassName: string;
  title: string;
  description: string;
  primaryHref: string;
  primaryLabel: string;
  secondaryHref: string;
  secondaryLabel: string;
};

type TaskAttentionCard = {
  task: BountyTaskItem;
  snapshot: TaskSubmissionSnapshot | null;
  pendingCount: number;
  reviewedCount: number;
  recent7dCount: number;
  recentSubmissions: BountySubmissionItem[];
  deadlineMeta: DeadlineMeta;
};

type ResultRecord = {
  taskId: number;
  taskTitle: string;
  studentUserId: number;
  studentName: string;
  studentAvatar?: StudentAvatarMeta | null;
  submissionId: number;
  status: "ACCEPTED" | "REJECTED";
  reviewComment: string | null;
  reviewedAt: string | null;
};

type DashboardReminder = {
  id: string;
  title: string;
  content: string;
  badgeLabel: string;
  href: string;
  actionLabel: string;
  createdAt: number | string | null;
  tone: "bounty" | "certification";
};

type DashboardLayoutStyle = CSSProperties & {
  "--enterprise-dashboard-column-height"?: string;
  "--enterprise-dashboard-task-height"?: string;
  "--enterprise-dashboard-reminder-height"?: string;
  "--enterprise-dashboard-reviewed-height"?: string;
  "--enterprise-dashboard-right-rows"?: string;
  "--enterprise-dashboard-gap"?: string;
};

const ENTERPRISE_DASHBOARD_CAROUSEL_ROTATION_MS = 15000;

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

const quickActions = [
  { href: "/enterprise/tasks/create", label: "发布新任务", desc: "开始整理新的业务任务", icon: FileSignature },
  { href: "/enterprise/tasks", label: "打开任务中心", desc: "查看全部任务与提交进展", icon: Briefcase },
  { href: "/enterprise/tasks?submission=待处理", label: "待审核提交", desc: "直接查看待处理提交", icon: Inbox },
  { href: "/enterprise/profile", label: "企业资料与认证", desc: "维护企业信息与认证状态", icon: Building2 },
] as const;

function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function getAttentionTaskPreviewCount(total: number) {
  if (total <= 0) {
    return 0;
  }
  if (total <= 2) {
    return total;
  }
  if (total <= 5) {
    return 3;
  }
  return 4;
}

function AnimatedCount({
  value,
  className,
  formatter = (nextValue: number) => String(nextValue),
  duration = 650,
}: {
  value: number;
  className?: string;
  formatter?: (value: number) => string;
  duration?: number;
}) {
  const [displayValue, setDisplayValue] = useState(value);
  const displayValueRef = useRef(value);

  useEffect(() => {
    const startValue = displayValueRef.current;
    const endValue = value;

    if (startValue === endValue) {
      setDisplayValue(endValue);
      return;
    }

    let animationFrame = 0;
    const animationStart = performance.now();

    const tick = (now: number) => {
      const progress = Math.min((now - animationStart) / duration, 1);
      const easedProgress = 1 - (1 - progress) ** 3;
      const nextValue = startValue + (endValue - startValue) * easedProgress;

      displayValueRef.current = nextValue;
      setDisplayValue(nextValue);

      if (progress < 1) {
        animationFrame = window.requestAnimationFrame(tick);
      }
    };

    animationFrame = window.requestAnimationFrame(tick);

    return () => {
      window.cancelAnimationFrame(animationFrame);
    };
  }, [duration, value]);

  return <span className={className}>{formatter(Math.round(displayValue))}</span>;
}

function EmptyState({
  icon: Icon,
  title,
  desc,
  actionText,
  href,
}: {
  icon: LucideIcon;
  title: string;
  desc: string;
  actionText?: string;
  href?: string;
}) {
  return (
    <div className="flex w-full flex-col items-center justify-center rounded-2xl border border-dashed border-slate-300 bg-slate-50/50 px-6 py-10 text-center">
      <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-white text-slate-400 shadow-sm ring-1 ring-slate-100">
        <Icon size={24} />
      </div>
      <h3 className="text-base font-bold text-slate-900">{title}</h3>
      <p className="mt-1.5 max-w-[280px] text-[13px] leading-relaxed text-slate-500">{desc}</p>
      {actionText && href ? (
        <DashboardLink href={href} className="mt-5 inline-flex items-center text-sm font-bold text-indigo-600 transition-colors hover:text-indigo-700">
          {actionText}
          <ArrowRight size={14} className="ml-1" />
        </DashboardLink>
      ) : null}
    </div>
  );
}

function DashboardLink({
  href,
  className,
  children,
}: {
  href: string;
  className: string;
  children: ReactNode;
}) {
  if (href.startsWith("#")) {
    return <a href={href} className={className}>{children}</a>;
  }
  return <Link to={href} className={className}>{children}</Link>;
}

function getGreeting() {
  const hour = new Date().getHours();
  if (hour < 11) {
    return "早上好";
  }
  if (hour < 14) {
    return "中午好";
  }
  if (hour < 18) {
    return "下午好";
  }
  return "晚上好";
}

function formatRelativeTime(value: string | number | null | undefined) {
  return formatRelativeTimeByBrowserTimezone(value, "刚刚");
}

function getDeadlineMeta(deadlineAt: string | null | undefined): DeadlineMeta {
  if (!deadlineAt) {
    return {
      label: "未设置截止时间",
      toneClassName: "text-slate-500",
      urgent: false,
    };
  }

  const timestamp = toTimestamp(deadlineAt);
  if (Number.isNaN(timestamp)) {
    return {
      label: deadlineAt,
      toneClassName: "text-slate-500",
      urgent: false,
    };
  }

  const diffMs = timestamp - Date.now();
  const diffDays = Math.ceil(diffMs / 86400000);

  if (diffDays <= 0) {
    return {
      label: `已于 ${formatDate(deadlineAt)} 截止`,
      toneClassName: "text-rose-600",
      urgent: true,
    };
  }

  if (diffDays <= 2) {
    return {
      label: `${diffDays} 天后截止`,
      toneClassName: "text-rose-600",
      urgent: true,
    };
  }

  if (diffDays <= 7) {
    return {
      label: `${diffDays} 天后截止`,
      toneClassName: "text-amber-600",
      urgent: true,
    };
  }

  return {
    label: `截止于 ${formatDate(deadlineAt)}`,
    toneClassName: "text-slate-500",
    urgent: false,
  };
}

function getSubmissionStatusMeta(status: string | null | undefined) {
  switch ((status ?? "").toUpperCase()) {
    case "REVIEWING":
      return {
        label: "处理中",
        className: "border border-sky-200 bg-sky-50 text-sky-700",
      };
    case "ACCEPTED":
      return {
        label: "继续接触",
        className: "border border-emerald-200 bg-emerald-50 text-emerald-700",
      };
    case "REJECTED":
      return {
        label: "未入选",
        className: "border border-slate-200 bg-slate-100 text-slate-600",
      };
    case "SUBMITTED":
    default:
      return {
        label: "待处理",
        className: "border border-amber-200 bg-amber-50 text-amber-700",
      };
  }
}

function isPendingSubmission(status: string | null | undefined) {
  return status === "SUBMITTED" || status === "REVIEWING";
}

function isReviewedSubmission(status: string | null | undefined): status is "ACCEPTED" | "REJECTED" {
  return status === "ACCEPTED" || status === "REJECTED";
}

function buildPriorityBanner(
  profile: EnterpriseOwnProfileResponse,
  certification: CertificationOwnViewResponse,
  stats: {
    activeTasks: number;
    pendingSubmissions: number;
    expiringSoon: number;
  },
): PriorityBanner {
  if (profile.approvalStatus === "REJECTED") {
    return {
      sectionLabel: "认证待处理",
      icon: AlertCircle,
      iconClassName: "from-rose-400 to-rose-500",
      title: "企业认证待补充",
      description: certification.currentSubmission?.reviewNote?.trim()
        || "请根据审核意见补充材料后重新提交。",
      primaryHref: "/enterprise/profile",
      primaryLabel: "处理认证资料",
      secondaryHref: "/enterprise/tasks",
      secondaryLabel: "打开任务中心",
    };
  }

  if (profile.approvalStatus !== "APPROVED") {
    return {
      sectionLabel: "认证状态",
      icon: ShieldCheck,
      iconClassName: "from-amber-400 to-orange-500",
      title: "企业认证审核中",
      description: certification.currentSubmission?.submittedAt
        ? `资料已于 ${formatDateTime(certification.currentSubmission.submittedAt)} 提交，审核期间可继续查看任务进展与学生提交。`
        : "审核期间可继续查看任务进展与学生提交。",
      primaryHref: "/enterprise/profile",
      primaryLabel: "查看认证状态",
      secondaryHref: "/enterprise/tasks",
      secondaryLabel: "打开任务中心",
    };
  }

  if (stats.pendingSubmissions > 0 || stats.expiringSoon > 0) {
    return {
      sectionLabel: "今日重点",
      icon: Target,
      iconClassName: "from-amber-400 to-orange-500",
      title: "优先处理待审核提交",
      description: `有 ${stats.pendingSubmissions} 份提交待处理，另有 ${stats.expiringSoon} 个任务临近截止。`,
      primaryHref: "/enterprise/tasks?submission=待处理",
      primaryLabel: "去任务中心处理",
      secondaryHref: "/enterprise/tasks",
      secondaryLabel: "查看全部任务",
    };
  }

  if (stats.activeTasks === 0) {
    return {
      sectionLabel: "开始新任务",
      icon: Compass,
      iconClassName: "from-sky-400 to-indigo-500",
      title: "还没有进行中的任务",
      description: "发布任务后，可在这里持续查看进展与处理记录。",
      primaryHref: "/enterprise/tasks/create",
      primaryLabel: "发布新任务",
      secondaryHref: "/enterprise/tasks",
      secondaryLabel: "打开任务中心",
    };
  }

  return {
      sectionLabel: "工作进展",
      icon: CheckCircle2,
      iconClassName: "from-emerald-400 to-emerald-500",
      title: "任务进展平稳",
      description: `现有 ${stats.activeTasks} 个进行中任务，可继续关注新的学生提交。`,
      primaryHref: "/enterprise/tasks",
      primaryLabel: "打开任务中心",
      secondaryHref: "#notifications",
      secondaryLabel: "查看业务提醒",
    };
}

async function fetchEnterpriseDashboardBundle(signal?: AbortSignal): Promise<EnterpriseDashboardBundle> {
  const [profile, certification, taskResponse, notificationResponse] = await Promise.all([
    apiRequest<EnterpriseOwnProfileResponse>("/profiles/enterprises/me", { signal }),
    apiRequest<CertificationOwnViewResponse>("/certification/me", { signal }),
    apiRequest<EnterpriseTaskCenterResponse>("/bounty/enterprise/task-center", { signal }),
    listNotifications({ page: 1, size: 8 }),
  ]);

  const tasks = taskResponse.tasks.map(({ recentSubmissions: _recentSubmissions, ...task }) => task);
  const taskSnapshots = taskResponse.tasks.map((task) => ({
    taskId: task.taskId,
    total: task.submissionCount,
    partial: task.submissionCount > task.recentSubmissions.length,
    records: task.recentSubmissions,
  } satisfies TaskSubmissionSnapshot));
  const notifications = notificationResponse.records
    .filter((item) => item.category === "BOUNTY" || item.category === "CERTIFICATION")
    .slice(0, 8);

  return {
    profile,
    certification,
    tasks,
    taskSnapshots,
    notifications,
  };
}

function normalizeEnterpriseDashboardBundle(bundle: EnterpriseDashboardBundle): EnterpriseDashboardBundle {
  return {
    ...bundle,
    tasks: Array.isArray(bundle.tasks)
      ? bundle.tasks.map((task) => ({
        ...task,
        pendingCount: Number.isFinite(task.pendingCount) ? task.pendingCount : 0,
        contactedCount: Number.isFinite(task.contactedCount) ? task.contactedCount : 0,
        reviewedCount: Number.isFinite(task.reviewedCount) ? task.reviewedCount : 0,
      }))
      : [],
    taskSnapshots: Array.isArray(bundle.taskSnapshots) ? bundle.taskSnapshots : [],
    notifications: Array.isArray(bundle.notifications) ? bundle.notifications : [],
  };
}

function buildDashboardReminders(
  bundle: EnterpriseDashboardBundle,
): DashboardReminder[] {
  const reminders = (Array.isArray(bundle.notifications) ? bundle.notifications : []).map((notification) => ({
    id: `notification-${notification.id}`,
    title: notification.title,
    content: notification.content,
    badgeLabel: getNotificationCategoryLabel(notification.category),
    href: resolveNotificationHref(notification, "ENTERPRISE"),
    actionLabel: getNotificationActionLabel(notification, "ENTERPRISE"),
    createdAt: notification.createdAt,
    tone: notification.category === "CERTIFICATION" ? "certification" : "bounty",
  } satisfies DashboardReminder));

  const hasCertificationReminder = reminders.some((item) => item.tone === "certification");
  if (hasCertificationReminder || bundle.profile.approvalStatus === "APPROVED") {
    return reminders;
  }

  const certificationReminder: DashboardReminder = bundle.profile.approvalStatus === "REJECTED"
    ? {
      id: "certification-reminder-rejected",
      title: "企业认证资料待补件",
      content: bundle.certification.currentSubmission?.reviewNote?.trim()
        || "请补充认证材料后再继续发布或重新开放任务。",
      badgeLabel: "认证提醒",
      href: "/enterprise/profile",
      actionLabel: "去资料页查看",
      createdAt: bundle.certification.currentSubmission?.reviewedAt ?? bundle.certification.currentSubmission?.submittedAt ?? null,
      tone: "certification",
    }
    : {
      id: "certification-reminder-pending",
      title: "企业认证审核中",
      content: bundle.certification.currentSubmission?.submittedAt
        ? `资料已于 ${formatDateTime(bundle.certification.currentSubmission.submittedAt)} 提交，审核期间可继续查看任务进展。`
        : "企业认证审核中，暂不能发布新任务或重新开放任务。",
      badgeLabel: "认证提醒",
      href: "/enterprise/profile",
      actionLabel: "查看认证状态",
      createdAt: bundle.certification.currentSubmission?.submittedAt ?? null,
      tone: "certification",
    };

  return [certificationReminder, ...reminders];
}

function EnterpriseDashboardFrame({
  displayName,
  companyName,
  logoUrl,
  loading,
  lastUpdatedAt,
  onRefresh,
  children,
}: {
  displayName: string | null | undefined;
  companyName: string | null | undefined;
  logoUrl?: string | null;
  loading: boolean;
  lastUpdatedAt: string | null;
  onRefresh: () => void;
  children: ReactNode;
}) {
  return (
    <div className="relative min-h-screen bg-[#eef3ff] text-slate-900">
      <div className="pointer-events-none fixed inset-0 z-0 overflow-hidden">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(71,85,105,0.12),transparent_30%),radial-gradient(circle_at_bottom_right,rgba(14,165,233,0.08),transparent_28%),linear-gradient(180deg,#f1f5f9_0%,#f8fafc_58%,#f8fafc_100%)]" />
        <div className="page-top-glow page-top-glow--slate" />
      </div>

      <WorkspaceRoleTopbar
        sectionLabel="Enterprise Dashboard"
        title="企业工作台"
        icon={Building2}
        navItems={getEnterpriseWorkspaceNavItems("dashboard")}
        displayName={displayName}
        userSubtitle={companyName?.trim() || "企业账号"}
        userFallbackLabel="企业代表"
        userFallbackInitial="企"
        userAvatarUrl={logoUrl}
        userAvatarDisplayName={companyName?.trim() || displayName?.trim() || "企业"}
        lastUpdatedAt={lastUpdatedAt}
        onRefresh={onRefresh}
        refreshing={loading}
        refreshTitle="刷新企业工作台数据"
      />

      <main className="relative z-10 mx-auto w-full max-w-[96rem] px-4 pb-6 pt-8 sm:px-6 lg:px-8 lg:pb-8 lg:pt-10">
        {children}
      </main>
    </div>
  );
}

function EnterpriseDashboardSkeleton({ displayName }: { displayName: string | null | undefined }) {
  return (
    <WorkspacePageLoadingScreen
      title="正在准备企业工作台"
      description="正在加载招聘概览、任务进度和待处理提醒，请稍候。"
    />
  );
}

function EnterpriseDashboardErrorState({
  displayName,
  companyName,
  refreshing,
  lastUpdatedAt,
  onRefresh,
  errorMessage,
}: {
  displayName: string | null | undefined;
  companyName: string | null | undefined;
  refreshing: boolean;
  lastUpdatedAt: string | null;
  onRefresh: () => void;
  errorMessage: string;
}) {
  return (
    <EnterpriseDashboardFrame
      displayName={displayName}
      companyName={companyName}
      loading={refreshing}
      lastUpdatedAt={lastUpdatedAt}
      onRefresh={onRefresh}
    >
      <div className="mx-auto max-w-3xl rounded-[2rem] border border-rose-200 bg-white/90 p-8 shadow-[0_22px_60px_rgba(148,163,184,0.15)] backdrop-blur-xl">
        <div className="flex flex-col gap-5 sm:flex-row sm:items-start sm:justify-between">
          <div className="flex items-start gap-4">
            <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-rose-50 text-rose-500">
              <AlertCircle size={24} />
            </div>
            <div>
              <div className="text-[12px] text-rose-400">读取失败</div>
              <h1 className="mt-2 text-2xl font-bold text-slate-900">企业工作台暂时无法加载</h1>
              <p className="mt-3 text-[15px] leading-7 text-slate-600">{errorMessage}</p>
              <p className="mt-3 text-[15px] leading-7 text-slate-500">
                请重新加载后继续查看工作台。
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onRefresh}
            className="inline-flex items-center justify-center rounded-full bg-slate-900 px-6 py-3 text-sm font-semibold text-white transition-transform hover:-translate-y-0.5 hover:bg-slate-800"
          >
            重新加载
            <ArrowRight size={16} className="ml-2" />
          </button>
        </div>
      </div>
    </EnterpriseDashboardFrame>
  );
}

function WorkspaceModalOverlay({
  children,
  onClose,
  maxWidthClassName = "max-w-2xl",
}: {
  children: ReactNode;
  onClose: () => void;
  maxWidthClassName?: string;
}) {
  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="fixed inset-0 z-[70] flex items-center justify-center bg-slate-950/45 p-4 backdrop-blur-sm"
      onClick={onClose}
    >
      <motion.div
        initial={{ scale: 0.96, y: 18, opacity: 0 }}
        animate={{ scale: 1, y: 0, opacity: 1 }}
        exit={{ scale: 0.98, y: 18, opacity: 0 }}
        transition={{ type: "spring", bounce: 0.24 }}
        className={joinClasses(
          "relative w-full rounded-[2rem] bg-white p-7 shadow-[0_32px_80px_rgba(15,23,42,0.24)] sm:p-8",
          maxWidthClassName,
        )}
        onClick={(event) => event.stopPropagation()}
      >
        <button
          type="button"
          onClick={onClose}
          className="absolute right-4 top-4 flex h-9 w-9 items-center justify-center rounded-full bg-slate-100 text-slate-500 transition-colors hover:bg-slate-200 hover:text-slate-900"
        >
          <X size={18} />
        </button>
        {children}
      </motion.div>
    </motion.div>
  );
}

function ResultRecordPreviewModal({
  record,
  onClose,
}: {
  record: ResultRecord | null;
  onClose: () => void;
}) {
  if (!record) {
    return null;
  }

  const accepted = record.status === "ACCEPTED";
  const statusLabel = accepted ? "已发送继续接触" : "已发送未入选通知";

  return (
    <AnimatePresence>
      {record ? (
        <WorkspaceModalOverlay onClose={onClose} maxWidthClassName="max-w-3xl">
          <div className="mb-6 flex items-start justify-between gap-4 pr-8">
            <div className="flex min-w-0 items-start gap-4">
              <StudentIdentityAvatar
                userId={record.studentUserId}
                role="STUDENT"
                displayName={record.studentName}
                avatar={record.studentAvatar}
                avatarPath={buildStudentAvatarPath(record.studentUserId, record.studentAvatar?.updatedAt)}
                className="h-14 w-14 border border-white bg-white shadow-sm"
                textClassName="text-lg"
              />
              <div className="min-w-0">
                <div className="text-[12px] text-slate-400">处理备注</div>
                <h3 className="mt-2 text-[1.9rem] font-black tracking-tight text-slate-900">{record.studentName}</h3>
                <p className="mt-2 text-[15px] leading-7 text-slate-500">
                  这里展示你最近发送给学生的处理说明，便于快速回顾并继续跟进。
                </p>
              </div>
            </div>
            <span className={joinClasses(
              "mt-1 inline-flex shrink-0 rounded-full border px-3 py-1 text-[12px] font-semibold",
              accepted
                ? "border-emerald-200 bg-emerald-50 text-emerald-700"
                : "border-slate-200 bg-slate-100 text-slate-600",
            )}>
              {statusLabel}
            </span>
          </div>

          <div className="grid gap-4 rounded-[1.5rem] border border-slate-200 bg-slate-50/80 p-5 sm:grid-cols-2">
            <div>
              <div className="text-[12px] text-slate-400">任务名称</div>
              <div className="mt-2 text-[15px] font-semibold leading-7 text-slate-900">{record.taskTitle}</div>
            </div>
            <div>
              <div className="text-[12px] text-slate-400">发送时间</div>
              <div className="mt-2 text-[15px] font-semibold text-slate-900">
                {record.reviewedAt ? formatDateTime(record.reviewedAt) : "刚刚发送"}
              </div>
            </div>
          </div>

          <div className="mt-5 rounded-[1.5rem] border border-slate-200 bg-white p-5 shadow-[0_12px_30px_rgba(148,163,184,0.08)]">
            <div className="text-[12px] text-slate-400">完整备注</div>
            <div className="mt-3 rounded-[1.25rem] border border-slate-200 bg-slate-50/75 px-4 py-4 text-[15px] leading-8 text-slate-700 whitespace-pre-wrap">
              {record.reviewComment?.trim() || "这次处理未填写补充说明。"}
            </div>
          </div>

          <div className="mt-6 flex flex-wrap justify-end gap-3">
            <button
              type="button"
              onClick={onClose}
              className="inline-flex items-center justify-center rounded-full border border-slate-200 bg-white px-5 py-2.5 text-sm font-semibold text-slate-600 transition-colors hover:border-slate-300 hover:text-slate-900"
            >
              关闭
            </button>
            <Link
              to={buildEnterpriseTaskReviewHref(record.taskId, record.submissionId)}
              className={joinClasses(
                "inline-flex items-center justify-center rounded-full px-5 py-2.5 text-sm font-semibold text-white transition-transform hover:-translate-y-0.5",
                accepted
                  ? "bg-emerald-600 hover:bg-emerald-700"
                  : "bg-slate-900 hover:bg-slate-800",
              )}
            >
              回到该提交
              <ArrowRight size={14} className="ml-1.5" />
            </Link>
          </div>
        </WorkspaceModalOverlay>
      ) : null}
    </AnimatePresence>
  );
}

function TaskSnapshotDrawer({
  task,
  snapshot,
  onClose,
}: {
  task: BountyTaskItem | null;
  snapshot: TaskSubmissionSnapshot | null;
  onClose: () => void;
}) {
  return (
    <AnimatePresence>
      {task ? (
        <>
          <motion.button
            type="button"
            aria-label="关闭任务快照抽屉"
            className="fixed inset-0 z-40 bg-slate-950/30 backdrop-blur-[2px]"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
          />
          <motion.aside
            className="fixed inset-y-0 right-0 z-50 w-full max-w-2xl overflow-y-auto border-l border-white/60 bg-[#f7faff] px-6 py-6 shadow-[-24px_0_60px_rgba(15,23,42,0.18)] sm:px-7"
            initial={{ x: 64, opacity: 0 }}
            animate={{ x: 0, opacity: 1 }}
            exit={{ x: 64, opacity: 0 }}
            transition={{ type: "spring", stiffness: 280, damping: 30 }}
          >
            <div className="mx-auto flex max-w-[42rem] flex-col gap-6">
              <div className="flex items-start justify-between gap-4">
                <div className="min-w-0">
                  <h2 className="text-[2rem] font-black tracking-tight text-slate-950">{task.title}</h2>
                  <p className="mt-2 text-[15px] leading-7 text-slate-500">
                    查看任务概况和最近提交，便于决定下一步安排。
                  </p>
                </div>
                <button
                  type="button"
                  onClick={onClose}
                  className="inline-flex h-10 w-10 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-500 shadow-sm transition-colors hover:border-slate-300 hover:text-slate-900"
                >
                  <X size={18} />
                </button>
              </div>

              <div className="rounded-[1.5rem] border border-white/80 bg-white/90 p-5 shadow-[0_18px_40px_rgba(148,163,184,0.12)]">
                <div className="grid gap-3 sm:grid-cols-2">
                  <div className="rounded-[1.2rem] border border-slate-200 bg-slate-50/70 p-4">
                    <div className="text-sm font-semibold text-slate-500">任务状态</div>
                    <div className="mt-2 text-[17px] font-bold text-slate-900">
                      {task.status === "OPEN" ? "进行中" : "已关闭"}
                    </div>
                  </div>
                  <div className="rounded-[1.2rem] border border-slate-200 bg-slate-50/70 p-4">
                    <div className="text-sm font-semibold text-slate-500">截止时间</div>
                    <div className="mt-2 text-[17px] font-bold text-slate-900">
                      {task.deadlineAt ? formatDateTime(task.deadlineAt) : "未设置截止时间"}
                    </div>
                  </div>
                  <div className="rounded-[1.2rem] border border-slate-200 bg-slate-50/70 p-4">
                    <div className="text-sm font-semibold text-slate-500">提交总数</div>
                    <div className="mt-2 text-[17px] font-bold text-slate-900">{task.submissionCount} 份</div>
                  </div>
                  <div className="rounded-[1.2rem] border border-slate-200 bg-slate-50/70 p-4">
                    <div className="text-sm font-semibold text-slate-500">奖励说明</div>
                    <div className="mt-2 text-[16px] font-bold leading-7 text-slate-900 whitespace-pre-wrap">
                      {task.rewardDescription || "待补充"}
                    </div>
                  </div>
                </div>
                <div className="mt-5 rounded-[1.25rem] border border-slate-200 bg-slate-50/80 p-4">
                  <div className="text-sm font-semibold text-slate-500">任务说明</div>
                  <div className="mt-3 text-[15px] leading-8 text-slate-700 whitespace-pre-wrap">
                    {task.descriptionSummary || "待补充任务说明。"}
                  </div>
                </div>
                <div className="mt-5 flex flex-wrap gap-3">
                  <Link
                    to={`/enterprise/tasks${buildQuery({ taskId: task.taskId })}`}
                    className="inline-flex items-center justify-center rounded-full bg-slate-900 px-5 py-2.5 text-sm font-semibold text-white transition-all hover:-translate-y-0.5 hover:bg-slate-800"
                  >
                    去任务中心查看
                    <ArrowRight size={14} className="ml-1.5" />
                  </Link>
                  <button
                    type="button"
                    onClick={onClose}
                    className="inline-flex items-center justify-center rounded-full border border-slate-200 bg-white px-5 py-2.5 text-sm font-semibold text-slate-600 transition-colors hover:border-slate-300 hover:text-slate-900"
                  >
                    关闭
                  </button>
                </div>
              </div>

              <div className="rounded-[1.5rem] border border-white/80 bg-white/90 p-5 shadow-[0_18px_40px_rgba(148,163,184,0.12)]">
                <div className="mb-4 flex items-center justify-between">
                  <div>
                    <h3 className="text-xl font-bold text-slate-950">最近提交与处理状态</h3>
                  </div>
                  {snapshot?.partial ? (
                    <span className="rounded-full border border-amber-200 bg-amber-50 px-3 py-1 text-[12px] font-semibold text-amber-700">
                      显示 {snapshot.records.length}/{snapshot.total} 份
                    </span>
                  ) : null}
                </div>

                {snapshot && snapshot.records.length > 0 ? (
                  <div className="space-y-4">
                    {[...snapshot.records]
                      .sort((left, right) => {
                        const rightTime = toTimestamp(right.createdAt ?? right.reviewedAt);
                        const leftTime = toTimestamp(left.createdAt ?? left.reviewedAt);
                        return rightTime - leftTime;
                      })
                      .slice(0, 8)
                      .map((submission) => {
                        const statusMeta = getSubmissionStatusMeta(submission.status);
                        const reviewHref = buildEnterpriseTaskReviewHref(task.taskId, submission.submissionId);
                        return (
                          <div key={submission.submissionId} className="rounded-2xl border border-slate-200 bg-slate-50/70 p-4 sm:p-5">
                            <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                              <div className="min-w-0">
                                <div className="flex items-center gap-2">
                                  <StudentIdentityAvatar
                                    userId={submission.studentUserId}
                                    role="STUDENT"
                                    displayName={submission.studentName}
                                    avatar={submission.studentAvatar}
                                    avatarPath={buildStudentAvatarPath(submission.studentUserId, submission.studentAvatar?.updatedAt)}
                                    className="h-8 w-8 border border-white bg-white shadow-sm"
                                    textClassName="text-[13px]"
                                  />
                                  <div>
                                    <div className="text-[15px] font-semibold text-slate-900">{submission.studentName}</div>
                                    <div className="text-[13px] text-slate-500">提交于 {formatRelativeTime(submission.createdAt)}</div>
                                  </div>
                                </div>
                              </div>
                              <span className={joinClasses("inline-flex shrink-0 rounded-full px-2.5 py-1 text-[12px] font-semibold", statusMeta.className)}>
                                {statusMeta.label}
                              </span>
                            </div>
                            <p className="mt-3 text-[15px] leading-8 text-slate-600">
                              {submission.contentSummary || submission.contentText?.trim() || "这份提交暂未附加补充说明。"}
                            </p>
                            <div className="mt-3 flex flex-wrap gap-2">
                              {submission.portraitTags.slice(0, 4).map((tag) => (
                                <span key={tag} className="rounded-full border border-slate-200 bg-white px-2.5 py-1 text-[12px] font-medium text-slate-500">
                                  {tag}
                                </span>
                              ))}
                              <span className="rounded-full border border-slate-200 bg-white px-2.5 py-1 text-[12px] font-medium text-slate-500">
                                近 7 天社区分 {submission.communityScore7d}
                              </span>
                            </div>
                            {submission.reviewComment?.trim() ? (
                              <div className="mt-3 rounded-2xl border border-slate-200 bg-white px-4 py-3 text-[13px] leading-6 text-slate-500 whitespace-pre-wrap">
                                审核备注：{submission.reviewComment}
                              </div>
                            ) : null}
                            <div className="mt-3 flex justify-end">
                              <Link
                                to={reviewHref}
                                className="inline-flex items-center text-sm font-bold text-indigo-600 transition-colors hover:text-indigo-700"
                              >
                                进入该提交审核
                                <ArrowRight size={14} className="ml-1" />
                              </Link>
                            </div>
                          </div>
                        );
                      })}
                  </div>
                ) : (
                  <EmptyState
                    icon={Inbox}
                    title="暂未收到新的学生提交"
                    desc="学生提交后，这里会显示最近记录与处理状态。"
                  />
                )}
              </div>
            </div>
          </motion.aside>
        </>
      ) : null}
    </AnimatePresence>
  );
}

export default function EnterpriseDashboardPage() {
  const { role, displayName, userId } = useAuth();
  const [bundle, setBundle] = useState<EnterpriseDashboardBundle | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [lastUpdatedAt, setLastUpdatedAt] = useState<string | null>(null);
  const [selectedTaskId, setSelectedTaskId] = useState<number | null>(null);
  const [selectedResultRecord, setSelectedResultRecord] = useState<ResultRecord | null>(null);
  const [reminderCarouselIndex, setReminderCarouselIndex] = useState(0);
  const [reminderCarouselResetKey, setReminderCarouselResetKey] = useState(0);
  const [reviewedCarouselIndex, setReviewedCarouselIndex] = useState(0);
  const [reviewedCarouselResetKey, setReviewedCarouselResetKey] = useState(0);
  const requestIdRef = useRef(0);
  const snapshotKey = buildWorkspaceSnapshotStorageKey("enterprise", "dashboard", userId ?? "current");

  useEffect(() => {
    if (role !== "ENTERPRISE") {
      return undefined;
    }

    const controller = new AbortController();
    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;
    const snapshot = readWorkspaceSnapshot<EnterpriseDashboardBundle>(snapshotKey);

    if (snapshot?.data) {
      // 企业首页先回显任务/通知/提交快照，再刷新后端聚合数据。
      startTransition(() => {
        setBundle(normalizeEnterpriseDashboardBundle(snapshot.data));
        setLastUpdatedAt(snapshot.updatedAt);
      });
    }

    setLoading(true);
    setErrorMessage(null);

    void fetchEnterpriseDashboardBundle(controller.signal)
      .then((response) => {
        // 多次进入或刷新时只允许最后一轮请求写入页面状态。
        if (controller.signal.aborted || requestIdRef.current !== requestId) {
          return;
        }
        const updatedAt = new Date().toISOString();
        writeWorkspaceSnapshot(snapshotKey, response, updatedAt);
        startTransition(() => {
          setBundle(normalizeEnterpriseDashboardBundle(response));
          setLastUpdatedAt(updatedAt);
          setErrorMessage(null);
        });
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted || isAbortError(error) || requestIdRef.current !== requestId) {
          return;
        }
        if (snapshot?.data) {
          setErrorMessage(null);
          return;
        }
        const nextMessage = error instanceof ApiClientError
          ? error.message
          : "网络请求异常，请稍后重试。";
        setErrorMessage(nextMessage);
      })
      .finally(() => {
        if (controller.signal.aborted || requestIdRef.current !== requestId) {
          return;
        }
        setLoading(false);
      });

    return () => {
      controller.abort();
    };
  }, [role, snapshotKey]);

  const handleRefresh = () => {
    if (role !== "ENTERPRISE") {
      return;
    }

    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;
    const hasBundle = Boolean(bundle);
    setLoading(true);
    setErrorMessage(null);

    void fetchEnterpriseDashboardBundle()
      .then((response) => {
        if (requestIdRef.current !== requestId) {
          return;
        }
        const updatedAt = new Date().toISOString();
        writeWorkspaceSnapshot(snapshotKey, response, updatedAt);
        startTransition(() => {
          setBundle(normalizeEnterpriseDashboardBundle(response));
          setLastUpdatedAt(updatedAt);
          setErrorMessage(null);
        });
      })
      .catch((error: unknown) => {
        if (isAbortError(error) || requestIdRef.current !== requestId) {
          return;
        }
        if (hasBundle) {
          const nextMessage = error instanceof ApiClientError
            ? error.message
            : "网络请求异常，请稍后重试。";
          setErrorMessage(nextMessage);
          return;
        }
        const nextMessage = error instanceof ApiClientError
          ? error.message
          : "网络请求异常，请稍后重试。";
        setErrorMessage(nextMessage);
      })
      .finally(() => {
        if (requestIdRef.current !== requestId) {
          return;
        }
        setLoading(false);
      });
  };

  const taskSnapshotMap = new Map((bundle?.taskSnapshots ?? []).map((item) => [item.taskId, item]));
  const taskCards = (bundle?.tasks ?? []).map((task) => {
    // 首页卡片把任务主体和提交快照合成，方便直接判断待审核压力。
    const snapshot = taskSnapshotMap.get(task.taskId) ?? null;
    const records = snapshot?.records ?? [];
    const pendingRecords = records.filter((record) => isPendingSubmission(record.status));
    const recentSubmissions = [...(pendingRecords.length > 0 ? pendingRecords : records)]
      .sort((left, right) => {
        const rightTime = toTimestamp(right.createdAt ?? right.reviewedAt);
        const leftTime = toTimestamp(left.createdAt ?? left.reviewedAt);
        return rightTime - leftTime;
      })
      .slice(0, 3);
    const recent7dCount = records.filter((record) => {
      if (!record.createdAt) {
        return false;
      }
      const diffMs = Date.now() - toTimestamp(record.createdAt);
      return diffMs >= 0 && diffMs <= 7 * 86400000;
    }).length;

    return {
      task,
      snapshot,
      pendingCount: task.pendingCount,
      reviewedCount: task.reviewedCount,
      recent7dCount,
      recentSubmissions,
      deadlineMeta: getDeadlineMeta(task.deadlineAt),
    } satisfies TaskAttentionCard;
  });

  const attentionTasks = taskCards
    // 待审核、临近截止、有提交的开放任务优先进入企业首页注意区。
    .filter((item) => item.task.status === "OPEN" && (item.pendingCount > 0 || item.deadlineMeta.urgent || item.task.submissionCount > 0))
    .sort((left, right) => {
      if (right.pendingCount !== left.pendingCount) {
        return right.pendingCount - left.pendingCount;
      }
      if (left.deadlineMeta.urgent !== right.deadlineMeta.urgent) {
        return left.deadlineMeta.urgent ? -1 : 1;
      }
      return toTimestamp(right.task.updatedAt ?? right.task.createdAt)
        - toTimestamp(left.task.updatedAt ?? left.task.createdAt);
    });

  const reviewedRecords = taskCards
    // 最近处理结果从每个任务快照抽平，右侧轮播只展示已审核提交。
    .flatMap((item) => {
      const snapshot = item.snapshot;
      if (!snapshot) {
        return [];
      }
      return snapshot.records
        .filter((record) => isReviewedSubmission(record.status))
        .map((record) => ({
          taskId: item.task.taskId,
          taskTitle: item.task.title,
          studentName: record.studentName,
          studentUserId: record.studentUserId,
          studentAvatar: record.studentAvatar,
          submissionId: record.submissionId,
          status: record.status as "ACCEPTED" | "REJECTED",
          reviewComment: record.reviewComment,
          reviewedAt: record.reviewedAt,
        } satisfies ResultRecord));
    })
    .sort((left, right) => toTimestamp(right.reviewedAt) - toTimestamp(left.reviewedAt));
  const dashboardReminders = bundle ? buildDashboardReminders(bundle).slice(0, 5) : [];
  const visibleAttentionTasks = attentionTasks.slice(0, getAttentionTaskPreviewCount(attentionTasks.length));
  const reminderCarouselItems = dashboardReminders.slice(0, 3);
  const reviewedCarouselItems = reviewedRecords.slice(0, 5);
  const taskPanelMinHeightRem = visibleAttentionTasks.length > 0
    ? 14.5 + visibleAttentionTasks.length * 9.1
    : 28;
  const reminderPanelMinHeightRem = reminderCarouselItems.length > 0 ? 16.4 : 15.5;
  const reviewedPanelMinHeightRem = reviewedCarouselItems.length > 0 ? 19.4 : 16.5;
  const dashboardColumnGapRem = 1.25;
  const desktopDashboardMinHeightRem = Math.max(
    taskPanelMinHeightRem,
    reminderPanelMinHeightRem + reviewedPanelMinHeightRem + dashboardColumnGapRem,
  );
  const rightColumnRowWeights = `${Math.max(reminderPanelMinHeightRem, 15)}fr ${Math.max(reviewedPanelMinHeightRem, 18)}fr`;
  const dashboardLayoutStyle: DashboardLayoutStyle = {
    "--enterprise-dashboard-column-height": `${desktopDashboardMinHeightRem}rem`,
    "--enterprise-dashboard-task-height": `${taskPanelMinHeightRem}rem`,
    "--enterprise-dashboard-reminder-height": `${reminderPanelMinHeightRem}rem`,
    "--enterprise-dashboard-reviewed-height": `${reviewedPanelMinHeightRem}rem`,
    "--enterprise-dashboard-right-rows": rightColumnRowWeights,
    "--enterprise-dashboard-gap": `${dashboardColumnGapRem}rem`,
  };
  const activeReminder = reminderCarouselItems[reminderCarouselIndex] ?? reminderCarouselItems[0] ?? null;
  const activeReviewedRecord = reviewedCarouselItems[reviewedCarouselIndex] ?? reviewedCarouselItems[0] ?? null;

  useEffect(() => {
    setReminderCarouselIndex((current) => (
      reminderCarouselItems.length === 0 ? 0 : Math.min(current, reminderCarouselItems.length - 1)
    ));
  }, [reminderCarouselItems.length]);

  useEffect(() => {
    if (reminderCarouselItems.length <= 1) {
      return;
    }
    // 通知提醒和审核结果轮播分开计时，用户手动切换后重置节奏。
    const timer = window.setInterval(() => {
      setReminderCarouselIndex((current) => (current + 1) % reminderCarouselItems.length);
    }, ENTERPRISE_DASHBOARD_CAROUSEL_ROTATION_MS);
    return () => window.clearInterval(timer);
  }, [reminderCarouselItems.length, reminderCarouselResetKey]);

  useEffect(() => {
    setReviewedCarouselIndex((current) => (
      reviewedCarouselItems.length === 0 ? 0 : Math.min(current, reviewedCarouselItems.length - 1)
    ));
  }, [reviewedCarouselItems.length]);

  useEffect(() => {
    if (reviewedCarouselItems.length <= 1) {
      return;
    }
    const timer = window.setInterval(() => {
      setReviewedCarouselIndex((current) => (current + 1) % reviewedCarouselItems.length);
    }, ENTERPRISE_DASHBOARD_CAROUSEL_ROTATION_MS);
    return () => window.clearInterval(timer);
  }, [reviewedCarouselItems.length, reviewedCarouselResetKey]);

  const handleReminderCarouselSelect = (nextIndex: number) => {
    if (reminderCarouselItems.length === 0) {
      return;
    }
    setReminderCarouselIndex((nextIndex + reminderCarouselItems.length) % reminderCarouselItems.length);
    // 手动选择后刷新 resetKey，下一轮自动轮播从当前项重新计时。
    setReminderCarouselResetKey((current) => current + 1);
  };

  const handleReviewedCarouselSelect = (nextIndex: number) => {
    if (reviewedCarouselItems.length === 0) {
      return;
    }
    setReviewedCarouselIndex((nextIndex + reviewedCarouselItems.length) % reviewedCarouselItems.length);
    setReviewedCarouselResetKey((current) => current + 1);
  };

  if (!bundle && loading) {
    return <EnterpriseDashboardSkeleton displayName={displayName} />;
  }

  if (!bundle) {
    return (
      <EnterpriseDashboardErrorState
        displayName={displayName}
        companyName={null}
        refreshing={loading}
        lastUpdatedAt={lastUpdatedAt}
        onRefresh={handleRefresh}
        errorMessage={errorMessage ?? "企业工作台数据暂时不可用。"}
      />
    );
  }

  const stats = {
    activeTasks: bundle.tasks.filter((task) => task.status === "OPEN").length,
    pendingSubmissions: taskCards.reduce((sum, item) => sum + item.pendingCount, 0),
    expiringSoon: taskCards.filter((item) => item.task.status === "OPEN" && item.deadlineMeta.urgent).length,
    completedNotifications: reviewedRecords.length,
    newSubmissions7d: taskCards.reduce((sum, item) => sum + item.recent7dCount, 0),
  };

  const priorityBanner = buildPriorityBanner(bundle.profile, bundle.certification, {
    activeTasks: stats.activeTasks,
    pendingSubmissions: stats.pendingSubmissions,
    expiringSoon: stats.expiringSoon,
  });
  const selectedTask = bundle.tasks.find((task) => task.taskId === selectedTaskId) ?? null;
  const selectedSnapshot = selectedTask ? (taskSnapshotMap.get(selectedTask.taskId) ?? null) : null;
  const showInlineError = !!errorMessage && !!bundle;
  const resolvedLogoUrl = buildEnterpriseLogoUrl(bundle.profile.logoUrl, bundle.profile.logoUpdatedAt);
  const accountName = resolveEnterpriseAccountName(bundle.profile);
  const heroApprovalBadge = bundle.profile.approvalStatus === "APPROVED"
    ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-400"
    : bundle.profile.approvalStatus === "REJECTED"
      ? "border-rose-500/30 bg-rose-500/10 text-rose-300"
      : "border-amber-400/30 bg-amber-400/10 text-amber-300";

  const statCards = [
    { label: "进行中任务", value: stats.activeTasks, icon: Briefcase, color: "text-indigo-600", bg: "bg-indigo-50", border: "border-indigo-100/70" },
    { label: "待审核提交", value: stats.pendingSubmissions, icon: Inbox, color: "text-amber-600", bg: "bg-amber-50", border: "border-amber-100/70" },
    { label: "即将截止任务", value: stats.expiringSoon, icon: Clock, color: "text-rose-600", bg: "bg-rose-50", border: "border-rose-100/70" },
    { label: "近 7 天新增", value: stats.newSubmissions7d, icon: FileUp, color: "text-emerald-600", bg: "bg-emerald-50", border: "border-emerald-100/70" },
    { label: "已发处理结果", value: stats.completedNotifications, icon: MessageSquare, color: "text-slate-600", bg: "bg-slate-100", border: "border-slate-200/70" },
  ] as const;

  return (
    <>
      <EnterpriseDashboardFrame
        displayName={accountName}
        companyName={bundle.profile.companyName || bundle.profile.displayName}
        logoUrl={resolvedLogoUrl}
        loading={loading}
        lastUpdatedAt={lastUpdatedAt}
        onRefresh={handleRefresh}
      >
        <motion.div variants={containerVariants} initial="hidden" animate="show" className="space-y-6">
          {showInlineError ? (
            <motion.section variants={itemVariants} className="rounded-[1.5rem] border border-amber-200 bg-amber-50/90 px-5 py-4 text-sm leading-7 text-amber-900 shadow-[0_10px_28px_rgba(245,158,11,0.12)]">
              <div className="flex items-start gap-3">
                <AlertCircle size={18} className="mt-0.5 shrink-0 text-amber-600" />
                <div>
                  <div className="font-semibold">刷新未完成，先展示最近一次可用的工作台内容。</div>
                  <div className="mt-1 text-amber-800/90">{errorMessage}</div>
                </div>
              </div>
            </motion.section>
          ) : null}

          <motion.section
            id="overview"
            variants={itemVariants}
            className="relative scroll-mt-28 overflow-hidden rounded-[2.25rem] bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900 px-7 py-8 text-white shadow-[0_24px_58px_rgba(15,23,42,0.25)] lg:px-10 lg:py-10"
          >
            <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_right,rgba(255,255,255,0.1),transparent_40%),linear-gradient(135deg,transparent_0%,rgba(255,255,255,0.02)_100%)]" />
            <div className="absolute -bottom-20 -right-10 opacity-[0.07]">
              <LayoutDashboard size={280} />
            </div>

            <div className="relative flex flex-col gap-8 md:flex-row md:items-center md:justify-between">
              <div className="flex-1 space-y-5">
                <div>
                  <div className="mb-2 flex items-center gap-3">
                    <div className="inline-flex items-center gap-2 rounded-full border border-white/12 bg-white/10 px-4 py-2 shadow-[0_10px_30px_rgba(15,23,42,0.18)] backdrop-blur-md">
                      <EnterpriseIdentityLogo
                        companyName={bundle.profile.companyName || bundle.profile.displayName}
                        logoUrl={resolvedLogoUrl}
                        className="h-9 w-9 rounded-[0.95rem] shadow-sm"
                        imageClassName="p-0"
                        fallbackClassName="bg-white/15 text-white"
                        textClassName="text-sm"
                      />
                      <div className="text-[15px] font-semibold tracking-[0.08em] text-white sm:text-[17px]">
                        {bundle.profile.companyName?.trim() || bundle.profile.displayName || "企业账号"}
                      </div>
                    </div>
                    <span className={joinClasses("inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[12px] font-semibold backdrop-blur-md", heroApprovalBadge)}>
                      <ShieldCheck size={12} />
                      {bundle.profile.approvalStatus === "APPROVED" ? "认证已通过" : bundle.profile.approvalStatus === "REJECTED" ? "认证待补件" : "认证审核中"}
                    </span>
                  </div>
                  <h1 className="text-3xl font-semibold tracking-tight text-white lg:text-4xl">
                    {getGreeting()}，{bundle.profile.realName?.trim() || bundle.profile.displayName || "企业同学"}
                  </h1>
                  <p className="mt-3 max-w-2xl text-[15px] leading-7 text-slate-300">
                    集中查看任务进展、学生提交和最新处理动态。
                  </p>
                </div>
              </div>

              <div className="flex shrink-0 flex-col gap-4 sm:min-w-[240px]">
                <DashboardLink
                  href={priorityBanner.primaryHref}
                  className="group relative inline-flex items-center justify-center overflow-hidden rounded-full bg-gradient-to-r from-indigo-500 to-blue-600 px-8 py-4 text-[15px] font-bold text-white shadow-[0_0_20px_rgba(99,102,241,0.3)] transition-all duration-300 hover:-translate-y-1 hover:shadow-[0_0_30px_rgba(99,102,241,0.5)] active:scale-95"
                >
                  <span className="absolute inset-0 -translate-x-full bg-gradient-to-r from-transparent via-white/20 to-transparent transition-transform duration-700 ease-out group-hover:translate-x-full" />
                  <span className="relative flex items-center gap-2">
                    <ArrowRight size={18} />
                    {priorityBanner.primaryLabel}
                  </span>
                </DashboardLink>
                <DashboardLink
                  href={priorityBanner.secondaryHref}
                  className="group relative inline-flex items-center justify-center overflow-hidden rounded-full border border-white/20 bg-white/10 px-8 py-4 text-[15px] font-bold text-white backdrop-blur-md transition-all duration-300 hover:-translate-y-1 hover:bg-white/20 active:scale-95"
                >
                  <span className="relative flex items-center gap-2">
                    <Inbox size={20} className="text-indigo-300" />
                    {priorityBanner.secondaryLabel}
                  </span>
                </DashboardLink>
              </div>
            </div>
          </motion.section>

          <motion.section variants={itemVariants} className="grid grid-cols-2 gap-3 lg:grid-cols-5">
            {statCards.map((stat) => {
              const Icon = stat.icon;
              return (
                <div
                  key={stat.label}
                  className="group relative overflow-hidden rounded-[1.5rem] border border-white/80 bg-white/85 p-4 shadow-[0_8px_20px_rgba(148,163,184,0.08)] backdrop-blur-xl transition-all duration-300 hover:-translate-y-1 hover:border-white hover:bg-white hover:shadow-[0_14px_30px_rgba(148,163,184,0.15)]"
                >
                  <div className={joinClasses("absolute -right-8 -top-8 h-24 w-24 rounded-full opacity-60 blur-xl transition-transform duration-700 group-hover:scale-125", stat.bg)} />
                  <div className="relative flex flex-col gap-2.5">
                      <div className="flex items-center justify-between">
                      <div className="text-sm font-bold text-slate-500">
                        {stat.label}
                      </div>
                      <div className={joinClasses("flex h-8 w-8 shrink-0 items-center justify-center rounded-xl border transition-all duration-300 group-hover:-rotate-6 group-hover:scale-110", stat.bg, stat.color, stat.border)}>
                        <Icon size={16} />
                      </div>
                    </div>
                    <div className="text-[1.8rem] font-black tracking-tight text-slate-800 tabular-nums">
                      <AnimatedCount value={stat.value} />
                    </div>
                  </div>
                </div>
              );
            })}
          </motion.section>

          <motion.section id="publish" variants={itemVariants} className="scroll-mt-28 grid grid-cols-2 gap-3 md:grid-cols-4">
            {quickActions.map((item) => {
              const Icon = item.icon;
              return (
                <Link
                  key={item.label}
                  to={item.href}
                  className="group flex items-center gap-3 rounded-[1.25rem] border border-white/80 bg-white/60 p-3.5 transition-all hover:border-indigo-100 hover:bg-white hover:shadow-md"
                >
                  <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-slate-100 text-slate-500 transition-colors group-hover:bg-indigo-50 group-hover:text-indigo-600">
                    <Icon size={18} />
                  </div>
                  <div>
                    <div className="text-sm font-bold text-slate-900 transition-colors group-hover:text-indigo-600">{item.label}</div>
                    <div className="text-[13px] text-slate-500">{item.desc}</div>
                  </div>
                </Link>
              );
            })}
          </motion.section>

          <div
            className="grid items-stretch gap-6 lg:min-h-[var(--enterprise-dashboard-column-height)] lg:grid-cols-12"
            style={dashboardLayoutStyle}
          >
            <div
              className="flex h-full min-h-[34rem] flex-col gap-5 lg:col-span-7 lg:min-h-[var(--enterprise-dashboard-column-height)] xl:col-span-7"
            >
              <motion.section
                variants={itemVariants}
                id="tasks"
                className="flex h-full min-h-[34rem] scroll-mt-28 flex-col rounded-[2rem] border border-white/80 bg-white/85 p-5 shadow-[0_22px_60px_rgba(148,163,184,0.15)] backdrop-blur-xl sm:p-6 lg:min-h-[var(--enterprise-dashboard-task-height)]"
              >
                <div className="mb-5 flex items-center justify-between gap-4">
                  <div>
                    <div className="flex items-center gap-2">
                      <ClipboardList size={20} className="text-indigo-500" />
                      <h2 className="text-xl font-bold text-slate-900">需要处理的任务</h2>
                    </div>
                    <p className="mt-1.5 text-[15px] text-slate-500">
                      按任务查看待处理提交和截止安排。
                    </p>
                  </div>
                  <Link to="/enterprise/tasks" className="text-sm font-bold text-indigo-600 hover:text-indigo-700">
                    打开任务中心 &rarr;
                  </Link>
                </div>

                {attentionTasks.length > 0 ? (
                  <div id="submissions" className="flex min-h-0 flex-1 flex-col">
                    <div className="space-y-3 pr-1 sm:pr-2">
                      {visibleAttentionTasks.map((item) => (
                        <div key={item.task.taskId} className="group rounded-[1.25rem] border border-slate-200 bg-white p-3.5 transition-all hover:border-indigo-200 hover:shadow-md">
                          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                            <div className="min-w-0">
                              <h3 className="line-clamp-1 text-[1.02rem] font-bold text-slate-900">{item.task.title}</h3>
                              <div className="mt-2 flex flex-wrap items-center gap-2.5 text-[13px] text-slate-500">
                                <span className="flex items-center gap-1.5 rounded border border-amber-100 bg-amber-50 px-2 py-0.5 font-medium text-amber-600">
                                  <Inbox size={14} />
                                  {item.pendingCount} 份待处理
                                </span>
                                <span className={joinClasses("flex items-center gap-1", item.deadlineMeta.toneClassName)}>
                                  <Clock size={14} className="text-current" />
                                  {item.deadlineMeta.label}
                                </span>
                                <span className="flex items-center gap-1 text-slate-400">
                                  <FileUp size={14} />
                                  近 7 天新增 {item.recent7dCount} 份
                                </span>
                              </div>
                            </div>
                            <div className="flex shrink-0 flex-wrap items-center gap-2.5">
                              <Link
                                to={`/enterprise/tasks${buildQuery({ taskId: item.task.taskId })}`}
                                className="inline-flex items-center justify-center rounded-xl bg-slate-900 px-4 py-2 text-sm font-bold text-white visited:text-white hover:-translate-y-0.5 hover:bg-slate-800 hover:text-white hover:shadow-lg focus:text-white active:text-white"
                              >
                                <span className="text-white">去任务中心查看</span>
                              </Link>
                              <button
                                type="button"
                                onClick={() => setSelectedTaskId(item.task.taskId)}
                                className="inline-flex items-center justify-center rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-bold text-slate-600 transition-all hover:-translate-y-0.5 hover:border-slate-300 hover:text-slate-900"
                              >
                                查看任务详情
                              </button>
                            </div>
                          </div>

                          <div className="mt-3 rounded-xl border border-slate-100 bg-slate-50/70 p-3">
                            <div className="mb-2.5 flex items-center justify-between gap-3">
                              <span className="text-sm font-bold text-slate-500">最新提交动态</span>
                              {item.snapshot?.partial ? (
                                <span className="shrink-0 rounded-full border border-amber-200 bg-amber-50 px-2.5 py-1 text-[12px] font-semibold text-amber-700">
                                  显示 {item.snapshot.records.length}/{item.snapshot.total} 份
                                </span>
                              ) : null}
                            </div>
                            {item.recentSubmissions.length > 0 ? (
                              <div className="-mx-1 flex gap-2 overflow-x-auto px-1 pb-1">
                                {item.recentSubmissions.map((submission) => {
                                  const statusMeta = getSubmissionStatusMeta(submission.status);
                                  return (
                                    <Link
                                      key={submission.submissionId}
                                      to={buildEnterpriseTaskReviewHref(item.task.taskId, submission.submissionId)}
                                      className="flex shrink-0 items-center gap-2 rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 shadow-sm transition-colors hover:border-indigo-200 hover:bg-indigo-50/60"
                                    >
                                      <StudentIdentityAvatar
                                        userId={submission.studentUserId}
                                        role="STUDENT"
                                        displayName={submission.studentName}
                                        avatar={submission.studentAvatar}
                                        avatarPath={buildStudentAvatarPath(submission.studentUserId, submission.studentAvatar?.updatedAt)}
                                        className="h-5 w-5 border border-white bg-white shadow-sm"
                                        textClassName="text-[10px]"
                                      />
                                      <span className="max-w-[7rem] truncate text-[13px] font-semibold text-slate-700">{submission.studentName}</span>
                                      <span className="shrink-0 text-[12px] text-slate-400">{formatRelativeTime(submission.createdAt)}</span>
                                      <span className={joinClasses("shrink-0 rounded-full px-1.5 py-0.5 text-[12px] font-semibold", statusMeta.className)}>
                                        {statusMeta.label}
                                      </span>
                                    </Link>
                                  );
                                })}
                                {item.task.submissionCount > item.recentSubmissions.length ? (
                                  <div className="flex shrink-0 items-center justify-center rounded-lg border border-dashed border-slate-300 px-3 py-1.5 text-[12px] font-medium text-slate-400">
                                    共 {item.task.submissionCount} 份提交
                                  </div>
                                ) : null}
                              </div>
                            ) : (
                              <div className="rounded-2xl border border-dashed border-slate-200 bg-white/70 px-4 py-4 text-sm text-slate-500">
                                该任务暂未收到新的学生提交。
                              </div>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                ) : (
                  <EmptyState
                    icon={CheckCircle2}
                    title="暂无待处理任务"
                    desc={stats.activeTasks > 0
                      ? "进行中的任务暂无新的提交或紧急截止提醒。"
                      : "还没有进行中的企业任务，可先发布新的任务。"}
                    actionText={stats.activeTasks > 0 ? "打开任务中心" : "发布第一个任务"}
                    href={stats.activeTasks > 0 ? "/enterprise/tasks" : "/enterprise/tasks/create"}
                  />
                )}
              </motion.section>
            </div>

            <div
              className="flex h-full min-h-[34rem] flex-col gap-5 lg:col-span-5 lg:grid lg:min-h-[var(--enterprise-dashboard-column-height)] xl:col-span-5"
              style={{
                ...dashboardLayoutStyle,
                gridTemplateRows: "var(--enterprise-dashboard-right-rows)",
                rowGap: "var(--enterprise-dashboard-gap)",
              }}
            >
              <motion.section
                variants={itemVariants}
                id="notifications"
                className="flex min-h-[16rem] scroll-mt-28 flex-col rounded-[2rem] border border-white/80 bg-white/85 p-5 shadow-[0_22px_60px_rgba(148,163,184,0.15)] backdrop-blur-xl lg:min-h-[var(--enterprise-dashboard-reminder-height)]"
              >
                <div className="mb-4 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <BellRing size={18} className="text-blue-500" />
                    <h2 className="text-xl font-bold text-slate-900">最新业务提醒</h2>
                  </div>
                  <Link to="/notifications" className="text-sm font-bold text-slate-400 hover:text-slate-600">通知中心</Link>
                </div>

                {dashboardReminders.length > 0 ? (
                  <div className="flex min-h-0 flex-1 flex-col">
                    <div className="mb-3 flex items-center justify-between gap-3 text-sm text-slate-500">
                      <div className="flex items-center gap-2">
                        {reminderCarouselItems.map((reminder, index) => (
                          <button
                            key={reminder.id}
                            type="button"
                            onClick={() => handleReminderCarouselSelect(index)}
                            className={joinClasses(
                              "h-2.5 rounded-full transition-all",
                              index === reminderCarouselIndex ? "w-8 bg-indigo-600" : "w-2.5 bg-slate-300 hover:bg-slate-400",
                            )}
                            aria-label={`切换到第 ${index + 1} 条业务提醒`}
                          />
                        ))}
                      </div>
                      <div className="flex items-center gap-2">
                        <span className="tabular-nums">{reminderCarouselIndex + 1} / {reminderCarouselItems.length}</span>
                        <button
                          type="button"
                          onClick={() => handleReminderCarouselSelect(reminderCarouselIndex - 1)}
                          className="inline-flex h-8 w-8 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-500 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600"
                          aria-label="查看上一条业务提醒"
                        >
                          <ChevronLeft size={15} />
                        </button>
                        <button
                          type="button"
                          onClick={() => handleReminderCarouselSelect(reminderCarouselIndex + 1)}
                          className="inline-flex h-8 w-8 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-500 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600"
                          aria-label="查看下一条业务提醒"
                        >
                          <ChevronRight size={15} />
                        </button>
                      </div>
                    </div>
                    <AnimatePresence mode="wait">
                      {activeReminder ? (
                        <motion.div
                          key={activeReminder.id}
                          initial={{ opacity: 0, x: 18 }}
                          animate={{ opacity: 1, x: 0 }}
                          exit={{ opacity: 0, x: -18 }}
                          transition={{ duration: 0.22 }}
                          className="flex min-h-0 flex-1 flex-col"
                        >
                          <div className={joinClasses(
                            "flex min-h-0 flex-1 flex-col rounded-[1.55rem] border p-4 shadow-[0_16px_40px_rgba(148,163,184,0.12)]",
                            activeReminder.tone === "certification"
                              ? "border-amber-100 bg-gradient-to-br from-amber-50 via-white to-orange-50/70"
                              : "border-indigo-100 bg-gradient-to-br from-indigo-50 via-white to-sky-50/70",
                          )}>
                            <div className="flex items-start justify-between gap-3">
                              <div className={joinClasses(
                                "flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl",
                                activeReminder.tone === "certification"
                                  ? "bg-amber-100 text-amber-600"
                                  : "bg-indigo-100 text-indigo-600",
                              )}>
                                {activeReminder.tone === "certification" ? <ShieldCheck size={18} /> : <Inbox size={18} />}
                              </div>
                              <span className={joinClasses(
                                "shrink-0 rounded-full border px-2.5 py-1 text-[12px] font-semibold",
                                activeReminder.tone === "certification"
                                  ? "border-amber-200 bg-white text-amber-700"
                                  : "border-indigo-200 bg-white text-indigo-700",
                              )}>
                                {activeReminder.badgeLabel}
                              </span>
                            </div>
                            <div className="mt-3 text-[11px] font-semibold text-slate-400">
                              {activeReminder.tone === "certification" ? "Certification" : "Business Alert"}
                            </div>
                            <div className="mt-1.5 line-clamp-2 text-[1rem] font-bold leading-7 text-slate-900">
                              {activeReminder.title}
                            </div>
                            <div className="mt-2 line-clamp-4 text-[13px] leading-6 text-slate-600">
                              {activeReminder.content}
                            </div>
                            <div className="mt-auto flex items-center justify-between gap-3 pt-4">
                              <div className="text-[12px] text-slate-400">{formatRelativeTime(activeReminder.createdAt)}</div>
                              <Link
                                to={activeReminder.href}
                                className="inline-flex items-center text-sm font-bold text-indigo-600 transition-colors hover:text-indigo-700"
                              >
                                {activeReminder.actionLabel}
                                <ArrowRight size={14} className="ml-1" />
                              </Link>
                            </div>
                          </div>
                        </motion.div>
                      ) : null}
                    </AnimatePresence>
                  </div>
                ) : (
                  <EmptyState
                    icon={BellRing}
                    title="暂无新的业务提醒"
                    desc="新的业务提醒会显示在这里，方便快速回到对应页面。"
                    actionText="打开通知中心"
                    href="/notifications"
                  />
                )}
              </motion.section>

              <motion.section
                variants={itemVariants}
                className="flex min-h-[16rem] flex-col rounded-[2rem] border border-white/80 bg-white/85 p-5 shadow-[0_22px_60px_rgba(148,163,184,0.15)] backdrop-blur-xl lg:min-h-[var(--enterprise-dashboard-reviewed-height)]"
              >
                <div className="mb-4 flex items-center gap-2">
                  <UserPlus size={18} className="text-emerald-500" />
                  <h2 className="text-xl font-bold text-slate-900">最新处理记录</h2>
                </div>

                {reviewedRecords.length > 0 ? (
                  <div className="flex min-h-0 flex-1 flex-col">
                    <div className="mb-3 flex items-center justify-between gap-3 text-sm text-slate-500">
                      <div className="flex items-center gap-2">
                        {reviewedCarouselItems.map((record, index) => (
                          <button
                            key={`${record.taskId}-${record.submissionId}`}
                            type="button"
                            onClick={() => handleReviewedCarouselSelect(index)}
                            className={joinClasses(
                              "h-2.5 rounded-full transition-all",
                              index === reviewedCarouselIndex ? "w-8 bg-emerald-600" : "w-2.5 bg-slate-300 hover:bg-slate-400",
                            )}
                            aria-label={`切换到第 ${index + 1} 条处理记录`}
                          />
                        ))}
                      </div>
                      <div className="flex items-center gap-2">
                        <span className="tabular-nums">{reviewedCarouselIndex + 1} / {reviewedCarouselItems.length}</span>
                        <button
                          type="button"
                          onClick={() => handleReviewedCarouselSelect(reviewedCarouselIndex - 1)}
                          className="inline-flex h-8 w-8 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-500 shadow-sm transition-colors hover:border-emerald-200 hover:text-emerald-700"
                          aria-label="查看上一条处理记录"
                        >
                          <ChevronLeft size={15} />
                        </button>
                        <button
                          type="button"
                          onClick={() => handleReviewedCarouselSelect(reviewedCarouselIndex + 1)}
                          className="inline-flex h-8 w-8 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-500 shadow-sm transition-colors hover:border-emerald-200 hover:text-emerald-700"
                          aria-label="查看下一条处理记录"
                        >
                          <ChevronRight size={15} />
                        </button>
                      </div>
                    </div>
                    <AnimatePresence mode="wait">
                      {activeReviewedRecord ? (
                        <motion.div
                          key={`${activeReviewedRecord.taskId}-${activeReviewedRecord.submissionId}`}
                          initial={{ opacity: 0, x: 18 }}
                          animate={{ opacity: 1, x: 0 }}
                          exit={{ opacity: 0, x: -18 }}
                          transition={{ duration: 0.22 }}
                          className="flex min-h-0 flex-1 flex-col"
                        >
                          <div
                            className={joinClasses(
                              "flex min-h-0 flex-1 flex-col rounded-[1.55rem] border p-4 shadow-[0_16px_40px_rgba(148,163,184,0.12)]",
                              activeReviewedRecord.status === "ACCEPTED"
                                ? "border-emerald-100 bg-gradient-to-br from-emerald-50 via-white to-teal-50/70"
                                : "border-slate-200 bg-gradient-to-br from-slate-100 via-white to-slate-50/80",
                            )}
                          >
                            <div className="flex items-start justify-between gap-3">
                              <div className="flex min-w-0 items-start gap-3">
                                <StudentIdentityAvatar
                                  userId={activeReviewedRecord.studentUserId}
                                  role="STUDENT"
                                  displayName={activeReviewedRecord.studentName}
                                  avatar={activeReviewedRecord.studentAvatar}
                                  avatarPath={buildStudentAvatarPath(activeReviewedRecord.studentUserId, activeReviewedRecord.studentAvatar?.updatedAt)}
                                  className="h-11 w-11 border border-white bg-white shadow-sm"
                                  textClassName="text-[15px]"
                                />
                                <div className="min-w-0">
                                  <div className="text-[11px] font-semibold text-slate-400">
                                    {activeReviewedRecord.status === "ACCEPTED" ? "继续接触" : "处理结果"}
                                  </div>
                                  <div className="mt-1.5 text-[1rem] font-bold text-slate-900">{activeReviewedRecord.studentName}</div>
                                  <div className="mt-1 line-clamp-1 text-[13px] text-slate-500">{activeReviewedRecord.taskTitle}</div>
                                </div>
                              </div>
                              <span className={joinClasses(
                                "shrink-0 rounded-full border bg-white px-2.5 py-1 text-[12px] font-semibold",
                                activeReviewedRecord.status === "ACCEPTED"
                                  ? "border-emerald-200 text-emerald-700"
                                  : "border-slate-200 text-slate-600",
                              )}>
                                {activeReviewedRecord.status === "ACCEPTED" ? "已发送继续接触" : "已发送未入选通知"}
                              </span>
                            </div>
                            <div className="mt-3 text-[12px] text-slate-400">{formatRelativeTime(activeReviewedRecord.reviewedAt)}</div>
                            <div className="mt-3 rounded-[1.2rem] border border-white/80 bg-white/85 px-3.5 py-3 text-[13px] leading-6 text-slate-600">
                              {activeReviewedRecord.reviewComment?.trim()
                                ? `备注：${activeReviewedRecord.reviewComment}`
                                : "本次处理未附加备注。"}
                            </div>
                            <div className="mt-auto flex flex-wrap items-center gap-3 pt-4">
                              {activeReviewedRecord.reviewComment?.trim() ? (
                                <button
                                  type="button"
                                  onClick={() => setSelectedResultRecord(activeReviewedRecord)}
                                  className="inline-flex items-center text-sm font-bold text-indigo-600 transition-colors hover:text-indigo-700"
                                >
                                  查看完整备注
                                  <ArrowRight size={14} className="ml-1" />
                                </button>
                              ) : null}
                              <Link
                                to={buildEnterpriseTaskReviewHref(activeReviewedRecord.taskId, activeReviewedRecord.submissionId)}
                                className={joinClasses(
                                  "inline-flex items-center text-sm font-bold transition-colors",
                                  activeReviewedRecord.status === "ACCEPTED"
                                    ? "text-emerald-700 hover:text-emerald-800"
                                    : "text-slate-700 hover:text-slate-900",
                                )}
                              >
                                回到该提交
                                <ArrowRight size={14} className="ml-1" />
                              </Link>
                            </div>
                          </div>
                        </motion.div>
                      ) : null}
                    </AnimatePresence>
                  </div>
                ) : (
                  <EmptyState
                    icon={MailOpen}
                    title="暂无处理记录"
                    desc="发出处理结果后，这里会保留最近记录，便于回看。"
                    actionText="去任务中心处理"
                    href="/enterprise/tasks?submission=待处理"
                  />
                )}
              </motion.section>
            </div>
          </div>
        </motion.div>
      </EnterpriseDashboardFrame>

      <TaskSnapshotDrawer
        task={selectedTask}
        snapshot={selectedSnapshot}
        onClose={() => setSelectedTaskId(null)}
      />
      <ResultRecordPreviewModal
        record={selectedResultRecord}
        onClose={() => setSelectedResultRecord(null)}
      />
    </>
  );
}
