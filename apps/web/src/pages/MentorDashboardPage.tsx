import { motion } from "framer-motion";
import {
  AlertCircle,
  ArrowRight,
  Briefcase,
  Calendar,
  CalendarDays,
  CheckCircle2,
  ChevronRight,
  Clock3,
  Compass,
  ShieldCheck,
  Star,
  Wallet,
  type LucideIcon,
} from "lucide-react";
import { startTransition, useEffect, useRef, useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import WorkspacePageLoadingScreen from "../components/WorkspacePageLoadingScreen";
import WorkspaceRoleTopbar from "../components/workspace/WorkspaceRoleTopbar";
import { approvalStatusLabelMap } from "../lib/adminLabels";
import { ApiClientError, apiRequest, isAbortError } from "../lib/apiClient";
import {
  formatDate,
  formatDateTime,
  formatMoneyFen,
  formatMonthDay,
  formatTime,
  toTimestamp,
} from "../lib/formatters";
import { getMentorWorkspaceNavItems } from "../lib/workspaceNav";
import {
  buildWorkspaceSnapshotStorageKey,
  readWorkspaceSnapshot,
  writeWorkspaceSnapshot,
} from "../lib/workspaceSnapshot";
import { writeMentorIdentitySnapshot } from "../lib/mentorIdentity";

type TimeValue = number | string | null;

type MentorDashboardResponse = {
  pendingPaidCount: number;
  answeredCount: number;
  closedCount: number;
  totalRevenueFen: number;
  totalOrders: number;
  avgRating: number | string | null;
  recentOrders: MentorRecentOrderItem[];
};

type MentorRecentOrderItem = {
  orderNo: string;
  studentUserId: number;
  studentDisplayName: string;
  amountFen: number;
  status: string;
  questionText: string | null;
  createdAt: TimeValue;
  paidAt: TimeValue;
  closedAt: TimeValue;
};

type MentorOwnProfileResponse = {
  userId: number;
  displayName: string;
  realName: string | null;
  showRealName: boolean;
  companyName: string | null;
  jobTitle: string | null;
  avatarUrl: string | null;
  expertiseTags: string[];
  serviceScenes: string[];
  bio: string | null;
  suitableFor: string | null;
  notSuitableFor: string | null;
  prepMaterials: string | null;
  replyRhythm: string | null;
  priceFen: number;
  packages: Array<{
    id: number;
    packageName: string;
    sceneCode: string;
    sceneLabel: string;
    deliveryMode: "TEXT_ASYNC" | "APPOINTMENT" | string;
    durationMinutes: number | null;
    priceFen: number;
    description: string | null;
    enabled: boolean;
    sortNo: number;
  }>;
  avgRating: number | string | null;
  totalOrders: number;
  available: boolean;
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
  submittedAt: TimeValue;
  reviewedAt: TimeValue;
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
  uploadedAt: TimeValue;
  deletedAt: TimeValue;
};

type MentorScheduleSlotListResponse = {
  records: MentorScheduleSlotResponse[];
};

type MentorScheduleSlotResponse = {
  id: number;
  mentorUserId: number;
  startAt: TimeValue;
  endAt: TimeValue;
  status: string;
  bookedOrderNo: string | null;
};

type MentorDashboardBundle = {
  dashboard: MentorDashboardResponse;
  profile: MentorOwnProfileResponse;
  certification: CertificationOwnViewResponse;
  schedule: MentorScheduleSlotListResponse;
};

type ScheduleSummary = {
  todayBooked: number;
  weekAvailable: number;
  nextBooked: MentorScheduleSlotResponse | null;
  nextAvailable: MentorScheduleSlotResponse | null;
  healthTone: "good" | "warning" | "neutral";
  healthTitle: string;
  healthDescription: string;
};

type ApprovalMeta = {
  label: string;
  badgeClassName: string;
  dotClassName: string;
  icon: LucideIcon;
  title: string;
  description: string;
};

const containerVariants = {
  hidden: { opacity: 0 },
  show: { opacity: 1, transition: { staggerChildren: 0.08 } },
};

const itemVariants = {
  hidden: { opacity: 0, y: 24 },
  show: { opacity: 1, y: 0, transition: { type: "spring" as const, stiffness: 250, damping: 24 } },
};

const quickActions = [
  { to: "/mentor/orders?status=PAID&sort=PRIORITY", label: "待处理订单", icon: Briefcase },
  { to: "/mentor/profile?tab=schedule", label: "排期管理", icon: CalendarDays },
  { to: "/mentor/finance", label: "财务中心", icon: Wallet },
  { to: "/community", label: "去社区答疑", icon: Compass },
] as const;

function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function normalizeRating(value: number | string | null | undefined) {
  if (value === null || value === undefined || value === "") {
    return 0;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function getInitial(value: string | null | undefined) {
  const normalized = value?.trim();
  return normalized ? normalized.charAt(0) : "导";
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

function formatSlotTime(value: TimeValue | undefined) {
  if (!value) {
    return "—";
  }
  const fallback = typeof value === "string" ? value : "—";
  return `${formatMonthDay(value, fallback)} ${formatTime(value, fallback)}`;
}

function formatMetricNumber(value: number) {
  return new Intl.NumberFormat("zh-CN").format(value);
}

function normalizeSummaryText(value: string | null | undefined, fallback: string) {
  const normalized = value?.trim();
  return normalized ? normalized : fallback;
}

function getEnabledPackages(packages: MentorOwnProfileResponse["packages"]) {
  return [...packages]
    .filter((item) => item.enabled)
    .sort((left, right) => left.sortNo - right.sortNo);
}

function getPackageLabel(item: MentorOwnProfileResponse["packages"][number]) {
  return item.packageName?.trim() || item.sceneLabel?.trim() || "服务套餐";
}

function getPackageModeLabel(deliveryMode: string) {
  return deliveryMode === "APPOINTMENT" ? "预约沟通" : "图文答疑";
}

function getApprovalMeta(status: string | null | undefined, reviewNote: string | null | undefined): ApprovalMeta {
  if (status === "APPROVED") {
    return {
      label: "已通过平台认证",
      badgeClassName: "border-emerald-300/45 bg-emerald-400/12 text-emerald-50",
      dotClassName: "bg-emerald-400",
      icon: ShieldCheck,
      title: "认证已通过，名片可以正式公开展示",
      description: "学生会按你的接单状态与排期查看预约情况。",
    };
  }

  if (status === "REJECTED") {
    return {
      label: "认证待补件",
      badgeClassName: "border-rose-300/40 bg-rose-400/12 text-rose-50",
      dotClassName: "bg-rose-400",
      icon: AlertCircle,
      title: "认证资料需要补充，名片暂不对外展示",
      description: reviewNote?.trim() || "请尽快补充更清晰的认证材料后重新提交审核。",
    };
  }

  return {
    label: "认证审核中",
    badgeClassName: "border-amber-300/45 bg-amber-300/12 text-amber-50",
    dotClassName: "bg-amber-300",
    icon: Clock3,
    title: "认证资料已提交，正在等待管理员审核",
    description: "审核通过后，你的导师名片会正式展示给学生。",
  };
}

function buildScheduleSummary(
  records: MentorScheduleSlotResponse[],
  available: boolean,
  approvalStatus: string | null | undefined,
): ScheduleSummary {
  const now = new Date();
  const todayStart = new Date(now);
  todayStart.setHours(0, 0, 0, 0);

  const tomorrowStart = new Date(todayStart);
  tomorrowStart.setDate(tomorrowStart.getDate() + 1);

  const weekEnd = new Date(todayStart);
  weekEnd.setDate(weekEnd.getDate() + 7);

  const upcoming = records
    .map((slot) => ({ slot, startAt: toTimestamp(slot.startAt) }))
    .filter((item) => Number.isFinite(item.startAt))
    .sort((left, right) => left.startAt - right.startAt);

  const todayBooked = upcoming.filter((item) => (
    item.slot.status === "BOOKED"
    && item.startAt >= todayStart.getTime()
    && item.startAt < tomorrowStart.getTime()
  )).length;

  const weekAvailable = upcoming.filter((item) => (
    item.slot.status === "AVAILABLE"
    && item.startAt >= todayStart.getTime()
    && item.startAt < weekEnd.getTime()
  )).length;

  const nextBooked = upcoming.find((item) => item.slot.status === "BOOKED" && item.startAt >= now.getTime())?.slot ?? null;
  const nextAvailable = upcoming.find((item) => item.slot.status === "AVAILABLE" && item.startAt >= now.getTime())?.slot ?? null;

  if (approvalStatus !== "APPROVED") {
    return {
      todayBooked,
      weekAvailable,
      nextBooked,
      nextAvailable,
      healthTone: "neutral",
      healthTitle: "先完成认证，再继续补排期会更有效",
      healthDescription: "审核通过后，学生侧才会正式看到你的名片与可预约状态。",
    };
  }

  if (!available) {
    return {
      todayBooked,
      weekAvailable,
      nextBooked,
      nextAvailable,
      healthTone: "neutral",
      healthTitle: "当前已暂停接单，排期处于待恢复状态",
      healthDescription: "恢复开放前，先把未来一周可预约时段准备好，会更方便学生下单。",
    };
  }

  if (weekAvailable === 0) {
    return {
      todayBooked,
      weekAvailable,
      nextBooked,
      nextAvailable,
      healthTone: "warning",
      healthTitle: "未来 7 天暂无可预约时段",
      healthDescription: "当前已开放接单，但可预约时段不足，学生可能无法顺利下单。",
    };
  }

  if (weekAvailable <= 2) {
    return {
      todayBooked,
      weekAvailable,
      nextBooked,
      nextAvailable,
      healthTone: "warning",
      healthTitle: "未来一周的可预约时段偏少",
      healthDescription: "补充更多空档后，学生会更容易在合适时间发起咨询。",
    };
  }

  return {
    todayBooked,
    weekAvailable,
    nextBooked,
    nextAvailable,
    healthTone: "good",
    healthTitle: "本周排期状态稳定",
    healthDescription: "当前可预约时段较充足，可以继续稳定承接新的咨询。",
  };
}

function buildHeroCopy(bundle: MentorDashboardBundle, scheduleSummary: ScheduleSummary) {
  const approvalMeta = getApprovalMeta(bundle.certification.approvalStatus, bundle.certification.currentSubmission?.reviewNote);
  const pendingCount = bundle.dashboard.pendingPaidCount;
  const answeredCount = bundle.dashboard.answeredCount;

  if (bundle.certification.approvalStatus === "REJECTED") {
    return {
      sectionLabel: approvalMeta.label,
      message: approvalMeta.description,
      primaryAction: { to: "/mentor/profile?tab=certification", label: "查看认证问题" },
      secondaryAction: { to: "/mentor/profile?tab=preview", label: "查看当前名片" },
    };
  }

  if (bundle.certification.approvalStatus === "PENDING") {
    return {
      sectionLabel: approvalMeta.label,
      message: approvalMeta.description,
      primaryAction: { to: "/mentor/profile?tab=certification", label: "查看认证进度" },
      secondaryAction: { to: "/mentor/profile?tab=preview", label: "确认名片快照" },
    };
  }

  if (!bundle.profile.available) {
    return {
      sectionLabel: "已通过认证，当前暂停接单",
      message: "学生侧仍会看到你的导师名片，但不会把你展示为可立即承接咨询的状态。",
      primaryAction: { to: "/mentor/profile?tab=preview", label: "查看服务名片" },
      secondaryAction: { to: "/mentor/profile?tab=schedule", label: "查看排期" },
    };
  }

  if (pendingCount > 0) {
    return {
      sectionLabel: "今日优先级已生成",
      message: `今天有 ${pendingCount} 个已支付订单等待优先处理，先看待处理订单会更高效。`,
      primaryAction: { to: "/mentor/orders?status=PAID&sort=PRIORITY", label: "查看待处理订单" },
      secondaryAction: { to: "/mentor/profile?tab=schedule", label: "查看排期" },
    };
  }

  if (scheduleSummary.weekAvailable === 0) {
    return {
      sectionLabel: "已开启接单，但当前排期偏紧",
      message: scheduleSummary.healthDescription,
      primaryAction: { to: "/mentor/profile?tab=schedule", label: "查看排期" },
      secondaryAction: { to: "/mentor/orders?sort=LATEST_CREATED", label: "查看最近订单" },
    };
  }

  if (answeredCount > 0) {
    return {
      sectionLabel: "当前暂无紧急新单",
      message: `还有 ${answeredCount} 个已答复订单等待学生确认关闭，当前可以一边跟进订单，一边保持排期与资料稳定。`,
      primaryAction: { to: "/mentor/orders?status=ANSWERED&sort=DEADLINE_ASC", label: "查看订单进展" },
      secondaryAction: { to: "/mentor/profile?tab=preview", label: "查看服务名片" },
    };
  }

  return {
    sectionLabel: "当前状态稳定",
    message: "认证、接单与排期状态都比较平稳，现在更适合继续维护服务名片与未来可预约时段。",
    primaryAction: { to: "/mentor/profile?tab=preview", label: "查看服务名片" },
    secondaryAction: { to: "/mentor/finance", label: "查看财务中心" },
  };
}

function getOrderStatusPresentation(status: string) {
  if (status === "PAID") {
    return {
      label: "待首次回复",
      chipClassName: "border-amber-200 bg-amber-50 text-amber-700",
      buttonLabel: "查看摘要",
      buttonClassName: "border border-slate-200 bg-white text-slate-900 shadow-sm hover:bg-slate-50",
    };
  }

  if (status === "ANSWERED") {
    return {
      label: "待学生确认",
      chipClassName: "border-sky-200 bg-sky-50 text-sky-700",
      buttonLabel: "查看摘要",
      buttonClassName: "border border-slate-200 bg-white text-slate-900 shadow-sm hover:bg-slate-50",
    };
  }

  return {
    label: "最近订单",
    chipClassName: "border-slate-200 bg-slate-100 text-slate-600",
    buttonLabel: "查看摘要",
    buttonClassName: "border border-slate-200 bg-white text-slate-900 shadow-sm hover:bg-slate-50",
  };
}

function buildOrderGroups(records: MentorRecentOrderItem[]) {
  return {
    paid: records.filter((item) => item.status === "PAID"),
    answered: records.filter((item) => item.status === "ANSWERED"),
  };
}

async function fetchMentorDashboardBundle(signal?: AbortSignal) {
  const [dashboard, profile, certification, schedule] = await Promise.all([
    apiRequest<MentorDashboardResponse>("/mentor/dashboard", { signal }),
    apiRequest<MentorOwnProfileResponse>("/mentor/profile", { signal }),
    apiRequest<CertificationOwnViewResponse>("/certification/me", { signal }),
    apiRequest<MentorScheduleSlotListResponse>("/mentor/schedule/slots/me", { signal }),
  ]);

  return {
    dashboard,
    profile,
    certification,
    schedule,
  } satisfies MentorDashboardBundle;
}

function AnimatedCount({
  value,
  className,
  formatter = (currentValue: number) => String(currentValue),
  duration = 650,
  precision = 0,
}: {
  value: number;
  className?: string;
  formatter?: (value: number) => string;
  duration?: number;
  precision?: number;
}) {
  const [displayValue, setDisplayValue] = useState(value);
  const displayValueRef = useRef(value);

  useEffect(() => {
    const startValue = displayValueRef.current;
    const endValue = value;

    if (startValue === endValue) {
      return;
    }

    let animationFrame = 0;
    const animationStart = performance.now();

    const tick = (now: number) => {
      const progress = Math.min((now - animationStart) / duration, 1);
      const easedProgress = 1 - Math.pow(1 - progress, 3);
      const nextValue = startValue + (endValue - startValue) * easedProgress;

      displayValueRef.current = nextValue;
      setDisplayValue(nextValue);

      if (progress < 1) {
        animationFrame = window.requestAnimationFrame(tick);
      }
    };

    animationFrame = window.requestAnimationFrame(tick);
    return () => window.cancelAnimationFrame(animationFrame);
  }, [duration, value]);

  return <span className={className}>{formatter(Number(displayValue.toFixed(precision)))}</span>;
}

function MentorDashboardFrame({
  displayName,
  avatarUrl,
  pendingCount,
  loading,
  lastUpdatedAt,
  onRefresh,
  children,
}: {
  displayName: string | null | undefined;
  avatarUrl?: string | null;
  pendingCount: number;
  loading: boolean;
  lastUpdatedAt: string | null;
  onRefresh: () => void;
  children: ReactNode;
}) {
  return (
    <div className="relative min-h-screen bg-[#eef3ff] text-slate-900">
      <div className="pointer-events-none fixed inset-0 z-0 overflow-hidden">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(71,85,105,0.12),transparent_30%),radial-gradient(circle_at_bottom_right,rgba(15,118,110,0.08),transparent_28%),linear-gradient(180deg,#f1f5f9_0%,#f8fafc_58%,#f8fafc_100%)]" />
        <div className="page-top-glow page-top-glow--slate" />
      </div>

      <WorkspaceRoleTopbar
        sectionLabel="Mentor Workspace"
        title="导师工作台"
        icon={Briefcase}
        navItems={getMentorWorkspaceNavItems("dashboard")}
        displayName={displayName}
        userAvatarUrl={avatarUrl}
        userSubtitle="入驻导师"
        userFallbackLabel="导师"
        userFallbackInitial="导"
        lastUpdatedAt={lastUpdatedAt}
        onRefresh={onRefresh}
        refreshing={loading}
        refreshTitle="刷新导师工作台数据"
      />

      <main className="relative z-10 mx-auto w-full max-w-[96rem] px-4 pb-6 pt-8 sm:px-6 lg:px-8 lg:pb-8 lg:pt-10">
        {children}
      </main>
    </div>
  );
}

function MentorDashboardSkeleton({ displayName }: { displayName: string | null | undefined }) {
  return (
    <WorkspacePageLoadingScreen
      title="正在准备导师工作台"
      description="正在加载订单概览、排期提醒和经营数据，请稍候。"
    />
  );
}

function DashboardErrorState({
  displayName,
  pendingCount,
  refreshing,
  lastUpdatedAt,
  onRefresh,
  errorMessage,
}: {
  displayName: string | null | undefined;
  pendingCount: number;
  refreshing: boolean;
  lastUpdatedAt: string | null;
  onRefresh: () => void;
  errorMessage: string;
}) {
  return (
    <MentorDashboardFrame
      displayName={displayName}
      pendingCount={pendingCount}
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
              <div className="text-[11px] text-rose-400">Load Failed</div>
              <h1 className="mt-2 text-2xl font-bold text-slate-900">导师工作台数据暂时没有同步成功</h1>
              <p className="mt-3 text-sm leading-7 text-slate-600">
                {errorMessage}
              </p>
              <p className="mt-3 text-sm leading-7 text-slate-500">
                当前不会改写任何导师资料，只是首页聚合接口读取失败。可以先手动刷新重试。
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
    </MentorDashboardFrame>
  );
}

function OrderGroupSection({
  title,
  description,
  orders,
  emptyTitle,
  emptyDescription,
}: {
  title: string;
  description: string;
  orders: MentorRecentOrderItem[];
  emptyTitle: string;
  emptyDescription: string;
}) {
  return (
    <div className="rounded-[1.75rem] border border-slate-200/80 bg-slate-50/65 p-5 sm:p-6">
      <div className="mb-4 flex items-start justify-between gap-4">
        <div>
          <div className="text-xs text-slate-400">{title}</div>
          <p className="mt-2 text-[15px] leading-7 text-slate-500">{description}</p>
        </div>
        <div className="rounded-full border border-slate-200 bg-white px-3 py-1 text-sm font-semibold text-slate-600">
          {orders.length} 单
        </div>
      </div>

      {orders.length > 0 ? (
        <div className="space-y-4">
          {orders.map((order) => {
            const statusPresentation = getOrderStatusPresentation(order.status);
            return (
              <div
                key={order.orderNo}
                className="group flex flex-col gap-4 rounded-2xl border border-slate-200 bg-white p-5 transition-all hover:border-indigo-200 hover:shadow-md"
              >
                <div className="flex items-start justify-between gap-4">
                  <div className="flex items-start gap-4">
                    <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-slate-100 text-lg font-bold text-slate-600">
                      {getInitial(order.studentDisplayName)}
                    </div>
                    <div>
                      <div className="flex flex-wrap items-center gap-2">
                        <h3 className="text-base font-bold text-slate-900">{order.studentDisplayName}</h3>
                        <span className={joinClasses("rounded px-2 py-0.5 text-[11px] font-bold border", statusPresentation.chipClassName)}>
                          {statusPresentation.label}
                        </span>
                      </div>
                      <p className="mt-2 line-clamp-2 text-[15px] leading-8 text-slate-600">
                        {order.questionText?.trim() || "学生暂未补充摘要，可进入履约区查看完整上下文。"}
                      </p>
                    </div>
                  </div>
                  <div className="shrink-0 text-right">
                    <div className="text-lg font-black text-slate-900">{formatMoneyFen(order.amountFen)}</div>
                    <div className="mt-1 text-sm text-slate-400">订单号 {order.orderNo}</div>
                  </div>
                </div>

                <div className="flex flex-col gap-3 border-t border-slate-100 pt-4 sm:flex-row sm:items-center sm:justify-between">
                  <div className="flex flex-wrap items-center gap-4 text-sm font-medium text-slate-400">
                    <span className="flex items-center gap-1">
                      <Clock3 size={12} />
                      创建于 {formatDate(order.createdAt)}
                    </span>
                    {order.paidAt ? (
                      <span className="flex items-center gap-1 text-indigo-500">
                        <Calendar size={12} />
                        支付于 {formatSlotTime(order.paidAt)}
                      </span>
                    ) : null}
                    {order.closedAt ? (
                      <span className="flex items-center gap-1 text-emerald-500">
                        <CheckCircle2 size={12} />
                        完成于 {formatSlotTime(order.closedAt)}
                      </span>
                    ) : null}
                  </div>
                  <Link
                    to={`/mentor/orders/${encodeURIComponent(order.orderNo)}/workspace`}
                    className={joinClasses(
                      "inline-flex items-center justify-center rounded-full px-5 py-2.5 text-sm font-bold transition-transform hover:-translate-y-0.5",
                      statusPresentation.buttonClassName,
                    )}
                  >
                    进入履约区
                  </Link>
                </div>
              </div>
            );
          })}
        </div>
      ) : (
        <div className="rounded-2xl border border-dashed border-slate-200 bg-white/70 px-5 py-8 text-center">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-slate-100 text-slate-500">
            <Compass size={20} />
          </div>
          <h3 className="mt-4 text-base font-semibold text-slate-900">{emptyTitle}</h3>
          <p className="mt-2 text-[15px] leading-8 text-slate-500">{emptyDescription}</p>
        </div>
      )}
    </div>
  );
}

export default function MentorDashboardPage() {
  const { role, displayName, userId } = useAuth();
  const [bundle, setBundle] = useState<MentorDashboardBundle | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [lastUpdatedAt, setLastUpdatedAt] = useState<string | null>(null);
  const requestIdRef = useRef(0);

  useEffect(() => {
    if (role !== "MENTOR") {
      return undefined;
    }

    const controller = new AbortController();
    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;
    const snapshotKey = buildWorkspaceSnapshotStorageKey("mentor", "dashboard", userId ?? "current");
    const snapshot = readWorkspaceSnapshot<MentorDashboardBundle>(snapshotKey);

    if (snapshot) {
      // 导师首页优先回显上次聚合快照，同时刷新导师身份快照给壳层头像使用。
      writeMentorIdentitySnapshot({
        userId: snapshot.data.profile.userId,
        displayName: snapshot.data.profile.displayName,
        avatarUrl: snapshot.data.profile.avatarUrl ?? null,
        updatedAt: snapshot.updatedAt,
      });
      startTransition(() => {
        setBundle(snapshot.data);
        setLastUpdatedAt(snapshot.updatedAt);
      });
    }

    setLoading(true);
    setErrorMessage(null);

    void fetchMentorDashboardBundle(controller.signal)
      .then((response) => {
        // requestId 保护避免旧请求覆盖快速重进页面后的新状态。
        if (controller.signal.aborted || requestIdRef.current !== requestId) {
          return;
        }
        const updatedAt = new Date().toISOString();
        writeMentorIdentitySnapshot({
          userId: response.profile.userId,
          displayName: response.profile.displayName,
          avatarUrl: response.profile.avatarUrl ?? null,
          updatedAt,
        });
        writeWorkspaceSnapshot(snapshotKey, response, updatedAt);
        startTransition(() => {
          setBundle(response);
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
          ? `${error.message}${error.traceId ? `（traceId: ${error.traceId}）` : ""}`
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
  }, [role, userId]);

  const handleRefresh = () => {
    if (role !== "MENTOR") {
      return;
    }

    // 手动刷新复用同一套快照写入，刷新失败时已有 bundle 仍保留在页面上。
    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;
    const snapshotKey = buildWorkspaceSnapshotStorageKey("mentor", "dashboard", userId ?? "current");
    const hasBundle = Boolean(bundle);
    setLoading(true);
    setErrorMessage(null);

    void fetchMentorDashboardBundle()
      .then((response) => {
        if (requestIdRef.current !== requestId) {
          return;
        }
        const updatedAt = new Date().toISOString();
        writeMentorIdentitySnapshot({
          userId: response.profile.userId,
          displayName: response.profile.displayName,
          avatarUrl: response.profile.avatarUrl ?? null,
          updatedAt,
        });
        writeWorkspaceSnapshot(snapshotKey, response, updatedAt);
        startTransition(() => {
          setBundle(response);
          setLastUpdatedAt(updatedAt);
          setErrorMessage(null);
        });
      })
      .catch((error: unknown) => {
        if (isAbortError(error) || requestIdRef.current !== requestId) {
          return;
        }
        if (hasBundle) {
          setErrorMessage(null);
          return;
        }
        const nextMessage = error instanceof ApiClientError
          ? `${error.message}${error.traceId ? `（traceId: ${error.traceId}）` : ""}`
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

  if (!bundle && loading) {
    return <MentorDashboardSkeleton displayName={displayName} />;
  }

  if (!bundle) {
    return (
      <DashboardErrorState
        displayName={displayName}
        pendingCount={0}
        refreshing={loading}
        lastUpdatedAt={lastUpdatedAt}
        onRefresh={handleRefresh}
        errorMessage={errorMessage ?? "导师工作台数据暂时不可用。"}
      />
    );
  }

  const scheduleSummary = buildScheduleSummary(
    bundle.schedule.records,
    bundle.profile.available,
    bundle.certification.approvalStatus,
  );
  const approvalMeta = getApprovalMeta(
    bundle.certification.approvalStatus,
    bundle.certification.currentSubmission?.reviewNote,
  );
  const heroCopy = buildHeroCopy(bundle, scheduleSummary);
  const orderGroups = buildOrderGroups(bundle.dashboard.recentOrders);
  const ApprovalIcon = approvalMeta.icon;
  const visibleTags = [...bundle.profile.expertiseTags, ...bundle.profile.serviceScenes]
    .filter((value, index, array) => value && array.indexOf(value) === index)
    .slice(0, 4);
  const normalizedRating = normalizeRating(bundle.dashboard.avgRating || bundle.profile.avgRating);
  const latestAsset = bundle.certification.currentSubmission?.assets?.[0] ?? null;
  const enabledPackages = getEnabledPackages(bundle.profile.packages);
  const appointmentPackageCount = enabledPackages.filter((item) => item.deliveryMode === "APPOINTMENT").length;
  const asyncPackageCount = enabledPackages.length - appointmentPackageCount;
  const serviceFocusSummary = enabledPackages.length > 0
    ? enabledPackages.slice(0, 3).map(getPackageLabel).join(" / ")
    : visibleTags.slice(0, 3).join(" / ") || "主打服务信息待补充";
  const serviceModeSummary = enabledPackages.length > 0
    ? `${asyncPackageCount} 个图文答疑 · ${appointmentPackageCount} 个预约沟通`
    : "还没有启用服务套餐";
  const suitableForSummary = normalizeSummaryText(
    bundle.profile.suitableFor,
    "适合已经有明确求职目标，希望把问题梳理成下一步行动的学生。",
  );
  const prepMaterialsSummary = normalizeSummaryText(
    bundle.profile.prepMaterials,
    "建议提前准备最新简历、目标岗位 JD，以及最想优先解决的 1 到 2 个问题。",
  );
  const replyRhythmSummary = normalizeSummaryText(
    bundle.profile.replyRhythm,
    bundle.profile.available
      ? "当前已开放接单，保持稳定回复节奏会更有利于学生做出选择。"
      : "恢复接单前，先确认好排期和回复节奏，体验会更顺畅。",
  );
  const followUpOrderCount = bundle.dashboard.pendingPaidCount + bundle.dashboard.answeredCount;
  return (
    <MentorDashboardFrame
      displayName={bundle.profile.displayName}
      avatarUrl={bundle.profile.avatarUrl}
      pendingCount={bundle.dashboard.pendingPaidCount}
      loading={loading}
      lastUpdatedAt={lastUpdatedAt}
      onRefresh={handleRefresh}
    >
      <motion.div variants={containerVariants} initial="hidden" animate="show" className="space-y-8">
        <motion.section
          id="overview"
          variants={itemVariants}
          className="relative scroll-mt-28 overflow-hidden rounded-[2.25rem] bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900 px-7 py-8 text-white shadow-[0_24px_58px_rgba(15,23,42,0.25)] lg:px-10"
        >
          <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_right,rgba(255,255,255,0.1),transparent_40%),linear-gradient(135deg,transparent_0%,rgba(255,255,255,0.02)_100%)]" />
          <div className="absolute -bottom-20 -right-10 opacity-10">
            <Briefcase size={280} />
          </div>

          <div className="relative flex flex-col gap-8 md:flex-row md:items-center md:justify-between">
            <div className="flex-1 space-y-5">
              <div className={joinClasses(
                "inline-flex items-center gap-2 rounded-full border px-4 py-2 text-sm font-medium  backdrop-blur-sm",
                approvalMeta.badgeClassName,
              )}>
                <ApprovalIcon size={16} />
                {approvalMeta.label}
              </div>

              <div>
                <h1 className="text-3xl font-semibold tracking-tight text-white lg:text-4xl">
                  {getGreeting()}，{bundle.profile.displayName} 导师
                </h1>
                <p className="mt-4 max-w-2xl text-[15px] leading-8 text-slate-300 lg:text-base">
                  {heroCopy.message}
                </p>
              </div>

              <div className="flex flex-wrap gap-3 pt-1">
                <span className="inline-flex items-center gap-2 rounded-full border border-white/12 bg-white/8 px-4 py-2 text-sm text-slate-200">
                  <span className={joinClasses("h-2 w-2 rounded-full", approvalMeta.dotClassName)} />
                  {approvalStatusLabelMap[bundle.certification.approvalStatus] ?? bundle.certification.approvalStatus}
                </span>
                <span className="inline-flex items-center gap-2 rounded-full border border-white/12 bg-white/8 px-4 py-2 text-sm text-slate-200">
                  <span className={joinClasses("h-2 w-2 rounded-full", bundle.profile.available ? "bg-emerald-400" : "bg-slate-400")} />
                  {bundle.profile.available ? "已开启接单" : "当前暂停接单"}
                </span>
                <span className="inline-flex items-center gap-2 rounded-full border border-white/12 bg-white/8 px-4 py-2 text-sm text-slate-200">
                  未来 7 天可预约时段 {scheduleSummary.weekAvailable} 个
                </span>
              </div>
            </div>

            <div className="flex shrink-0 flex-col gap-4 sm:flex-row">
              <Link
                to={heroCopy.primaryAction.to}
                className="inline-flex items-center justify-center rounded-full border border-white/90 bg-white px-8 py-3.5 text-sm font-bold shadow-lg transition-transform hover:-translate-y-0.5 hover:bg-slate-50 hover:shadow-xl"
                style={{ color: "#0f172a" }}
              >
                {heroCopy.primaryAction.label}
              </Link>
              <Link
                to={heroCopy.secondaryAction.to}
                className="inline-flex items-center justify-center rounded-full border border-white/90 bg-white px-8 py-3.5 text-sm font-bold shadow-lg transition-colors hover:bg-slate-50 hover:shadow-xl"
                style={{ color: "#0f172a" }}
              >
                {heroCopy.secondaryAction.label}
              </Link>
            </div>
          </div>
        </motion.section>

        <div className="grid gap-8 lg:grid-cols-12">
          <div className="flex flex-col gap-8 lg:col-span-8">
            <motion.section
              id="recent-orders"
              variants={itemVariants}
              className="scroll-mt-28 rounded-[2rem] border border-white/80 bg-white/85 p-6 shadow-[0_22px_60px_rgba(148,163,184,0.15)] backdrop-blur-xl sm:p-8"
            >
              <div className="mb-6 flex items-center justify-between border-b border-slate-100 pb-5">
                <div>
                  <h2 className="text-2xl font-bold text-slate-950">待处理订单</h2>
                </div>
                <Link
                  to="/mentor/profile?tab=schedule"
                  className="inline-flex items-center gap-1 text-[15px] font-semibold text-indigo-600 transition-colors hover:text-indigo-800"
                >
                  查看排期
                  <ChevronRight size={16} />
                </Link>
              </div>

              <div className="space-y-5">
                <OrderGroupSection
                  title="待首次回复"
                  description="学生已完成支付，当前最重要的是尽快给出第一轮正式回复。"
                  orders={orderGroups.paid}
                  emptyTitle="当前没有待首次回复的订单"
                  emptyDescription="可以把时间留给排期维护和服务资料优化。"
                />
                <OrderGroupSection
                  title="已答复待学生关闭"
                  description="已完成回复，当前重点是跟进学生确认结果和后续追问。"
                  orders={orderGroups.answered}
                  emptyTitle="当前没有待学生确认的订单"
                  emptyDescription="当前订单进展平稳，可以继续完善资料和排期。"
                />
              </div>
            </motion.section>

            <motion.section
              id="schedule-health"
              variants={itemVariants}
              className="scroll-mt-28 flex flex-1 flex-col rounded-[2rem] border border-white/80 bg-white/85 p-6 shadow-[0_22px_60px_rgba(148,163,184,0.15)] backdrop-blur-xl sm:p-8"
            >
              <div className="mb-6 flex items-center justify-between">
                <div>
                  <h2 className="text-xl font-bold text-slate-900">排期健康度</h2>
                  <p className={joinClasses(
                    "mt-1 text-[15px]",
                    scheduleSummary.healthTone === "warning" && "text-amber-700",
                    scheduleSummary.healthTone === "good" && "text-slate-500",
                    scheduleSummary.healthTone === "neutral" && "text-slate-500",
                  )}>
                    {scheduleSummary.healthDescription}
                  </p>
                </div>
                <div className={joinClasses(
                  "flex h-12 w-12 items-center justify-center rounded-2xl",
                  scheduleSummary.healthTone === "warning" && "bg-amber-50 text-amber-600",
                  scheduleSummary.healthTone === "good" && "bg-indigo-50 text-indigo-600",
                  scheduleSummary.healthTone === "neutral" && "bg-slate-100 text-slate-600",
                )}>
                  <CalendarDays size={24} />
                </div>
              </div>

              <div className="mb-5 rounded-2xl border border-slate-100 bg-slate-50 px-4 py-3">
                <div className="text-base font-semibold text-slate-900">{scheduleSummary.healthTitle}</div>
                <p className="mt-1 text-sm leading-7 text-slate-500">
                  这里先帮你看清关键排期信号，详细调整可前往排期管理。
                </p>
              </div>

              <div className="grid gap-4 sm:grid-cols-4">
                <div className="rounded-2xl border border-slate-100 bg-slate-50 p-4">
                  <div className="text-sm font-bold text-slate-500">今日已预约</div>
                  <div className="mt-2 text-2xl font-black text-slate-900">
                    {scheduleSummary.todayBooked}
                    <span className="ml-1 text-sm font-normal text-slate-500">单</span>
                  </div>
                </div>
                <div className="rounded-2xl border border-emerald-100 bg-emerald-50 p-4">
                  <div className="text-sm font-bold text-emerald-700">本周空闲时段</div>
                  <div className="mt-2 text-2xl font-black text-emerald-600">
                    {scheduleSummary.weekAvailable}
                    <span className="ml-1 text-sm font-normal text-emerald-500">个</span>
                  </div>
                </div>
                <div className="col-span-2 rounded-2xl border border-slate-100 bg-slate-50 p-4">
                  <div className="flex items-center justify-between gap-3 text-sm">
                    <span className="text-slate-500">最近一个已约时段</span>
                    <span className="font-semibold text-slate-900">{formatSlotTime(scheduleSummary.nextBooked?.startAt)}</span>
                  </div>
                  <div className="mt-3 flex items-center justify-between gap-3 text-sm">
                    <span className="text-slate-500">最近一个空档时段</span>
                    <span className="font-semibold text-emerald-600">{formatSlotTime(scheduleSummary.nextAvailable?.startAt)}</span>
                  </div>
                </div>
              </div>

              <div className="mt-auto pt-6">
                <Link
                  to="/mentor/profile?tab=schedule"
                  className="inline-flex w-full items-center justify-center rounded-2xl border border-slate-200 bg-white py-3.5 text-[15px] font-bold text-slate-700 shadow-sm transition-colors hover:border-slate-300 hover:bg-slate-50"
                >
                  前往排期管理
                </Link>
              </div>
            </motion.section>
          </div>

          <div className="flex flex-col gap-8 lg:col-span-4">
            <motion.section variants={itemVariants} className="grid grid-cols-2 gap-4">
              <div className="rounded-[1.5rem] border border-white/80 bg-white/85 p-5 text-center shadow-sm backdrop-blur-xl">
                <div className="text-sm font-bold  text-slate-500">待回复订单</div>
                <div className="mt-2 text-3xl font-black text-amber-500 tabular-nums">
                  <AnimatedCount value={bundle.dashboard.pendingPaidCount} />
                </div>
              </div>
              <div className="rounded-[1.5rem] border border-white/80 bg-white/85 p-5 text-center shadow-sm backdrop-blur-xl">
                <div className="text-sm font-bold  text-slate-500">平均评分</div>
                <div className="mt-2 flex items-baseline justify-center text-3xl font-black text-slate-800 tabular-nums">
                  {normalizedRating.toFixed(1)}
                  <Star size={14} className="ml-1 text-amber-400" fill="currentColor" />
                </div>
              </div>
              <div className="col-span-2 rounded-[1.5rem] border border-white/80 bg-white/85 p-5 shadow-sm backdrop-blur-xl">
                <div className="flex items-center justify-between">
                  <div>
                    <div className="text-sm font-bold  text-slate-500">累计成交额</div>
                    <div className="mt-1 text-2xl font-black text-slate-900 tabular-nums">
                      ¥
                      <AnimatedCount
                        value={bundle.dashboard.totalRevenueFen / 100}
                        precision={2}
                        formatter={(currentValue) => currentValue.toFixed(2)}
                      />
                    </div>
                  </div>
                  <div className="flex h-10 w-10 items-center justify-center rounded-full border border-slate-100 bg-slate-50 text-slate-400">
                    <Wallet size={18} />
                  </div>
                </div>
                <div className="mt-4 flex justify-between gap-4 border-t border-slate-100 pt-4 text-sm text-slate-500">
                  <span>待学生确认：{bundle.dashboard.answeredCount} 单</span>
                  <span>累计完成：{bundle.dashboard.closedCount} 单</span>
                </div>
              </div>
            </motion.section>

            <motion.section variants={itemVariants} className="grid grid-cols-4 gap-3">
              {quickActions.map((item) => {
                const Icon = item.icon;
                return (
                  <Link
                    key={item.to}
                    to={item.to}
                    className="flex flex-col items-center justify-center gap-2.5 rounded-2xl border border-slate-200 bg-white py-4.5 text-slate-600 shadow-sm transition-all hover:border-indigo-300 hover:bg-indigo-50/50 hover:text-indigo-600"
                  >
                    <Icon size={22} />
                    <span className="text-sm font-bold leading-none">{item.label}</span>
                  </Link>
                );
              })}
            </motion.section>

            {bundle.certification.approvalStatus !== "APPROVED" ? (
              <motion.section
                id="certification-status"
                variants={itemVariants}
                className="scroll-mt-28 rounded-[2rem] border border-white/80 bg-white/85 p-6 shadow-[0_22px_60px_rgba(148,163,184,0.15)] backdrop-blur-xl"
              >
                <div className="flex items-center justify-between gap-4">
                  <div>
                    <h2 className="text-xl font-bold leading-tight text-slate-950">认证状态摘要</h2>
                    <p className="mt-2 text-[15px] leading-8 text-slate-500">
                      这里展示当前认证进展与下一步处理方向。
                    </p>
                  </div>
                  <div className={joinClasses(
                    "flex h-12 w-12 items-center justify-center rounded-2xl",
                    bundle.certification.approvalStatus === "APPROVED" && "bg-emerald-50 text-emerald-600",
                    bundle.certification.approvalStatus === "REJECTED" && "bg-rose-50 text-rose-600",
                    bundle.certification.approvalStatus === "PENDING" && "bg-amber-50 text-amber-600",
                  )}>
                    <ApprovalIcon size={22} />
                  </div>
                </div>

                <div className="mt-5 rounded-2xl border border-slate-200/80 bg-slate-50/70 p-4">
                  <div className="flex items-center justify-between gap-4">
                    <div className="text-base font-semibold text-slate-900">{approvalMeta.title}</div>
                    <span className={joinClasses(
                      "rounded-full px-3 py-1 text-sm font-semibold",
                      bundle.certification.approvalStatus === "APPROVED" && "bg-emerald-100 text-emerald-700",
                      bundle.certification.approvalStatus === "REJECTED" && "bg-rose-100 text-rose-700",
                      bundle.certification.approvalStatus === "PENDING" && "bg-amber-100 text-amber-700",
                    )}>
                      {approvalStatusLabelMap[bundle.certification.approvalStatus] ?? bundle.certification.approvalStatus}
                    </span>
                  </div>
                  <p className="mt-3 text-[15px] leading-8 text-slate-600">{approvalMeta.description}</p>
                </div>

                <div className="mt-5 space-y-3 rounded-2xl border border-slate-100 bg-white/80 p-4">
                  <div className="flex items-center justify-between gap-3 text-sm">
                    <span className="text-slate-500">最近一次提交</span>
                    <span className="font-semibold text-slate-900">
                      {formatDateTime(bundle.certification.currentSubmission?.submittedAt)}
                    </span>
                  </div>
                  <div className="flex items-center justify-between gap-3 text-sm">
                    <span className="text-slate-500">最近一次审核</span>
                    <span className="font-semibold text-slate-900">
                      {formatDateTime(bundle.certification.currentSubmission?.reviewedAt)}
                    </span>
                  </div>
                  <div className="flex items-start justify-between gap-3 text-sm">
                    <span className="pt-0.5 text-slate-500">审核备注</span>
                    <span className="max-w-[15rem] text-right font-medium leading-6 text-slate-900">
                      {bundle.certification.currentSubmission?.reviewNote?.trim() || "当前暂无补件备注。"}
                    </span>
                  </div>
                  <div className="flex items-start justify-between gap-3 text-sm">
                    <span className="pt-0.5 text-slate-500">当前材料</span>
                    <span className="max-w-[15rem] text-right font-medium leading-6 text-slate-900">
                      {latestAsset?.originalFilename || "暂未读取到材料摘要"}
                    </span>
                  </div>
                </div>

                <div className="mt-5 rounded-2xl border border-dashed border-slate-200 bg-slate-50/70 px-4 py-3 text-sm leading-7 text-slate-500">
                  如需补充材料或查看完整记录，可前往资料页继续处理。
                </div>
              </motion.section>
            ) : (
              <motion.section
                id="service-readiness"
                variants={itemVariants}
                className="scroll-mt-28 flex flex-1 flex-col rounded-[2rem] border border-white/80 bg-white/85 p-6 shadow-[0_22px_60px_rgba(148,163,184,0.15)] backdrop-blur-xl"
              >
                <div className="flex items-center justify-between gap-4">
                  <div>
                    <h2 className="text-xl font-bold leading-tight text-slate-950">服务状态摘要</h2>
                  </div>
                  <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-indigo-50 text-indigo-600">
                    <ShieldCheck size={22} />
                  </div>
                </div>

                <div className="mt-5 grid grid-cols-2 gap-3">
                  <div className="rounded-2xl border border-slate-100 bg-slate-50 p-4">
                    <div className="text-sm font-semibold text-slate-500">已启用套餐</div>
                    <div className="mt-2 text-2xl font-black text-slate-900">
                      {enabledPackages.length}
                      <span className="ml-1 text-sm font-medium text-slate-500">个</span>
                    </div>
                    <div className="mt-2 text-sm leading-6 text-slate-500">{serviceModeSummary}</div>
                  </div>
                  <div className="rounded-2xl border border-emerald-100 bg-emerald-50 p-4">
                    <div className="text-sm font-semibold text-emerald-700">未来 7 天空档</div>
                    <div className="mt-2 text-2xl font-black text-emerald-600">
                      {scheduleSummary.weekAvailable}
                      <span className="ml-1 text-sm font-medium text-emerald-500">个</span>
                    </div>
                    <div className="mt-2 text-sm leading-6 text-emerald-700/80">
                      最近空档 {formatSlotTime(scheduleSummary.nextAvailable?.startAt)}
                    </div>
                  </div>
                  <div className="rounded-2xl border border-slate-100 bg-slate-50 p-4">
                    <div className="text-sm font-semibold text-slate-500">待跟进订单</div>
                    <div className="mt-2 text-2xl font-black text-slate-900">
                      {followUpOrderCount}
                      <span className="ml-1 text-sm font-medium text-slate-500">单</span>
                    </div>
                    <div className="mt-2 text-sm leading-6 text-slate-500">
                      待首次回复 {bundle.dashboard.pendingPaidCount} 单，待学生确认 {bundle.dashboard.answeredCount} 单
                    </div>
                  </div>
                  <div className="rounded-2xl border border-slate-100 bg-slate-50 p-4">
                    <div className="text-sm font-semibold text-slate-500">主打服务</div>
                    <div className="mt-2 text-[15px] font-semibold leading-7 text-slate-900">
                      {serviceFocusSummary}
                    </div>
                    <div className="mt-2 text-sm leading-6 text-slate-500">
                      起步价 {formatMoneyFen(bundle.profile.priceFen)}，当前已成交 {formatMetricNumber(bundle.dashboard.totalOrders)} 单
                    </div>
                  </div>
                </div>

                <div className="mt-4 rounded-2xl border border-slate-100 bg-slate-50 px-4 py-3.5">
                  <div className="text-sm font-semibold text-slate-500">回复节奏</div>
                  <div className="mt-2 text-[15px] leading-7 text-slate-900">{replyRhythmSummary}</div>
                </div>
                <div className="mt-4 rounded-2xl border border-slate-100 bg-slate-50 p-4">
                  <div className="flex items-center justify-between gap-3">
                    <div className="text-sm font-semibold text-slate-500">服务亮点</div>
                    {enabledPackages[0] ? (
                      <span className="rounded-full border border-slate-200 bg-white px-3 py-1 text-xs font-semibold text-slate-500">
                        {getPackageModeLabel(enabledPackages[0].deliveryMode)}
                      </span>
                    ) : null}
                  </div>
                  <div className="mt-2 text-[15px] font-semibold leading-7 text-slate-900">{serviceFocusSummary}</div>
                  <div className="mt-2 text-sm leading-6 text-slate-600">
                    {serviceModeSummary}
                  </div>
                  <div className="mt-3 flex flex-wrap gap-2">
                    {visibleTags.length > 0 ? (
                      visibleTags.map((tag) => (
                        <span key={tag} className="rounded-md border border-slate-200 bg-white px-2.5 py-1 text-sm text-slate-600">
                          {tag}
                        </span>
                      ))
                    ) : (
                      <span className="rounded-md border border-slate-200 bg-white px-2.5 py-1 text-sm text-slate-500">
                        服务标签待补充
                      </span>
                    )}
                  </div>
                </div>

                <div className="mt-4 grid gap-3 md:grid-cols-2">
                  <div className="rounded-2xl border border-slate-100 bg-slate-50 p-4">
                    <div className="text-sm font-semibold text-slate-500">适合这类咨询</div>
                    <div className="mt-2 text-[15px] leading-7 text-slate-900">{suitableForSummary}</div>
                  </div>
                  <div className="rounded-2xl border border-slate-100 bg-slate-50 p-4">
                    <div className="text-sm font-semibold text-slate-500">咨询前建议准备</div>
                    <div className="mt-2 text-[15px] leading-7 text-slate-900">{prepMaterialsSummary}</div>
                  </div>
                </div>

                <div className="mt-4 grid grid-cols-3 gap-3 rounded-2xl border border-slate-100 bg-slate-50 p-4 text-center">
                  <div>
                    <div className="text-sm font-semibold text-slate-400">起步价</div>
                    <div className="mt-2 text-lg font-black text-slate-900">{formatMoneyFen(bundle.profile.priceFen)}</div>
                  </div>
                  <div>
                    <div className="text-sm font-semibold text-slate-400">总订单</div>
                    <div className="mt-2 text-lg font-black text-slate-900">{formatMetricNumber(bundle.profile.totalOrders)}</div>
                  </div>
                  <div>
                    <div className="text-sm font-semibold text-slate-400">评分</div>
                    <div className="mt-2 text-lg font-black text-slate-900">{normalizeRating(bundle.profile.avgRating).toFixed(1)}</div>
                  </div>
                </div>

                <div className="mt-auto pt-6">
                  <Link
                    to="/mentor/profile?tab=preview"
                    className="inline-flex w-full items-center justify-center rounded-2xl bg-indigo-50 py-3 text-[15px] font-bold text-indigo-600 transition-colors hover:bg-indigo-100"
                  >
                    前往资料页查看服务详情
                  </Link>
                </div>
              </motion.section>
            )}
          </div>
        </div>
      </motion.div>
    </MentorDashboardFrame>
  );
}
