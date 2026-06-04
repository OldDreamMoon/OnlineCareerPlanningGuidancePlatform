import { AnimatePresence, motion } from "framer-motion";
import {
  AlertCircle,
  ArrowRight,
  Award,
  Bell,
  Bot,
  CalendarDays,
  Check,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Compass,
  FileText,
  Flame,
  GraduationCap,
  Lock,
  MessageSquare,
  ReceiptText,
  RefreshCw,
  ShieldCheck,
  Sparkles,
  Target,
  TrendingUp,
  type LucideIcon,
} from "lucide-react";
import { Suspense, lazy, useEffect, useRef, useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import StudentWorkspaceTopbar, { buildStudentWorkspacePrimaryNav } from "../components/student/StudentWorkspaceTopbar";
import WorkspacePageLoadingScreen from "../components/WorkspacePageLoadingScreen";
import { ApiClientError, apiRequest, buildQuery } from "../lib/apiClient";
import {
  formatCount,
  formatDate,
  formatDateTime,
  isSameLocalDate,
} from "../lib/formatters";
import { getNotificationUnreadCount } from "../lib/notifications";
import { getRoleDisplayLabel } from "../lib/roleLabels";
import { buildStudentNickname } from "../lib/studentNames";
import { type StudentAvatarMeta } from "../lib/studentAvatar";
import { STUDENT_DASHBOARD_ROUTE } from "../lib/workspaceRoutes";

const DashboardOverlay = lazy(() => import("./DashboardOverlays"));

const CONTINUE_DESK_ENTRY_COUNT = 3;
const CONTINUE_DESK_ROTATION_MS = 6000;
type TimeValue = number | string;

type StudentProfileResponse = {
  userId: number;
  displayName: string;
  avatar?: StudentAvatarMeta | null;
  tier?: string | null;
  completionRate?: number | null;
  realName: string | null;
  jobStatus?: string | null;
  schoolName?: string | null;
  major: string | null;
  grade: string | null;
  gpa?: string | null;
  targetPosition: string | null;
  honors?: string | null;
  socialLinks?: Array<{
    platform: string;
    value: string;
  }> | null;
  phone?: string | null;
  wechat?: string | null;
  skillTags: string[] | null;
  selfIntro: string | null;
  portrait: {
    tags: Array<{
      code: string;
      label: string;
      source: string;
      confidence: number | null;
    }> | null;
    evidence: {
      masteredSkills: number;
      learningSkills: number;
      interviewMessages7d: number;
      posts7d: number;
      comments7d: number;
      likesReceived7d: number;
    } | null;
    updatedAt: TimeValue | null;
  } | null;
  communityScore7d: number;
};

type DailyTaskItemResponse = {
  taskId: number;
  taskCode: string;
  title: string;
  description: string | null;
  points: number;
  completed: boolean;
};

type PointsLedgerResponse = {
  balance: number;
  records: Array<{
    deltaPoints: number;
    reasonCode: string;
    balanceAfter: number;
    createdAt: TimeValue;
  }>;
  total: number;
};

type GrowthCheckinRewardItem = {
  rewardCode: string;
  title: string;
  description: string;
  streakDays: number;
  bonusPoints: number;
};

type GrowthCheckinNextRewardItem = GrowthCheckinRewardItem & {
  remainingDays: number;
};

type GrowthCheckinResponse = {
  streak: number;
  pointsEarned: number;
  checkinDate: string;
  basePointsEarned: number;
  bonusPointsEarned: number;
  newBalance: number;
  triggeredReward: GrowthCheckinRewardItem | null;
};

type GrowthCheckinOverviewResponse = {
  signedInToday: boolean;
  growthJourneyDays: number;
  currentStreak: number;
  latestCheckinDate: string | null;
  currentMonth: string;
  daysInCurrentMonth: number;
  checkedInDays: number[];
  basePointsPerDay: number;
  rewardRules: GrowthCheckinRewardItem[];
  nextReward: GrowthCheckinNextRewardItem | null;
};

type ChecklistItem = {
  label: string;
  done: boolean;
};

type RecommendedAction = {
  sectionLabel: string;
  title: string;
  description: string;
  ctaLabel: string;
  href: string;
  type: "route" | "anchor";
};

type RewardState = {
  title: string;
  subtitle: string;
  points: number;
  tone: "amber" | "emerald" | "indigo";
  highlight: string;
};

type GrowthWorkspaceData = {
  dailyTasks: DailyTaskItemResponse[];
  dailyTasksLoaded: boolean;
  pointsLedger: PointsLedgerResponse | null;
  pointsLedgerLoaded: boolean;
  checkinOverview: GrowthCheckinOverviewResponse | null;
  checkinOverviewLoaded: boolean;
  errorMessage: string | null;
};

type CheckinProgressSummary = {
  headline: string;
  detail: string;
  progressValue: number;
  progressText: string;
  rewardLabel: string;
};

type CalendarDayCell =
  | {
      key: string;
      type: "placeholder";
    }
  | {
      key: string;
      type: "day";
      day: number;
      checkedIn: boolean;
      isToday: boolean;
      isFuture: boolean;
    };

type GrowthWorkspacePanelsState = {
  dailyTasks: DailyTaskItemResponse[];
  pointsLedger: PointsLedgerResponse | null;
  checkinOverview: GrowthCheckinOverviewResponse | null;
};

type DashboardContinueSnapshot = {
  unreadCount: number | null;
  latestAiRecord: DashboardContinueAiRecord | null;
  pendingOrder: DashboardContinueConsultOrder | null;
};

type DashboardWorkspaceSnapshot = {
  version: 1;
  userId: number;
  savedAt: string;
  profile: StudentProfileResponse;
  dailyTasks: DailyTaskItemResponse[];
  pointsLedger: PointsLedgerResponse | null;
  checkinOverview: GrowthCheckinOverviewResponse | null;
  continueState: DashboardContinueSnapshot | null;
  growthError: string | null;
};

type DashboardContinueAiRecord = {
  id: number;
  taskType: "RESUME" | "INTERVIEW_TEXT";
  summary: string;
  pointsConsumed: number;
  sessionId: string | null;
  status: string | null;
  createdAt: TimeValue | null;
};

type DashboardContinueAiHistoryResponse = {
  records: DashboardContinueAiRecord[];
  total: number;
  page: number;
  size: number;
};

type DashboardContinueConsultOrder = {
  orderNo: string;
  counterpartUserId: number;
  counterpartDisplayName: string;
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
};

type DashboardContinueConsultOrderResponse = {
  records: DashboardContinueConsultOrder[];
  total: number;
  page: number;
  size: number;
};

type DashboardContinueState = {
  loading: boolean;
  error: string | null;
  unreadCount: number | null;
  latestAiRecord: DashboardContinueAiRecord | null;
  pendingOrder: DashboardContinueConsultOrder | null;
};

const DASHBOARD_SNAPSHOT_STORAGE_KEY_PREFIX = "bishe.student.dashboard.snapshot.v1";

function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function getDashboardSnapshotStorageKey(userId: number) {
  return `${DASHBOARD_SNAPSHOT_STORAGE_KEY_PREFIX}.${userId}`;
}

function getDashboardSnapshotStorage() {
  if (typeof window === "undefined") {
    return null;
  }

  return window.sessionStorage;
}

function isContinuableAiInterviewRecord(record: DashboardContinueAiRecord | null | undefined) {
  return record?.taskType === "INTERVIEW_TEXT"
    && Boolean(record.sessionId?.trim())
    && (record.status ?? "").trim().toUpperCase() === "ACTIVE";
}

function pickContinueAiRecord(records: DashboardContinueAiRecord[] | null | undefined) {
  if (!records || records.length === 0) {
    return null;
  }
  return records.find((item) => isContinuableAiInterviewRecord(item)) ?? records[0] ?? null;
}

function getContinueAiHref(record: DashboardContinueAiRecord | null) {
  if (!record) {
    return "/ai/history";
  }
  if (record.taskType === "RESUME") {
    return `/ai/history${buildQuery({ type: "resume", recordId: record.id })}`;
  }
  if (isContinuableAiInterviewRecord(record)) {
    return `/ai/interview/session${buildQuery({ sessionId: record.sessionId })}`;
  }
  if (record.sessionId) {
    return `/ai/history${buildQuery({ type: "interview", sessionId: record.sessionId })}`;
  }
  return "/ai/history";
}

function isContinuableConsultOrderStatus(status: string | null | undefined) {
  return !["CLOSED", "CANCELED", "REFUNDED", "FAILED"].includes((status ?? "").trim().toUpperCase());
}

function getContinueConsultOrderActionLabel(order: DashboardContinueConsultOrder | null) {
  switch (order?.status) {
    case "CREATED":
    case "PAYING":
      return "继续支付";
    case "PAID":
      return "查看等待状态";
    case "ANSWERED":
      return "查看导师答复";
    default:
      return "查看订单详情";
  }
}

function getContinueConsultOrderStatusLabel(status: string | null | undefined) {
  switch ((status ?? "").trim().toUpperCase()) {
    case "CREATED":
      return "待支付";
    case "PAYING":
      return "支付中";
    case "PAID":
      return "待导师回复";
    case "ANSWERED":
      return "已回复待确认";
    case "CLOSED":
      return "已完成";
    case "REFUNDED":
      return "已退款";
    case "CANCELED":
      return "已取消";
    case "FAILED":
      return "支付失败";
    default:
      return status || "处理中";
  }
}

function getLatestContinueTimestamp(order: DashboardContinueConsultOrder | null) {
  return order?.paidAt ?? order?.appointmentStartAt ?? order?.createdAt ?? null;
}

function buildContinueSnapshot(state: DashboardContinueState): DashboardContinueSnapshot | null {
  // 继续入口只缓存已成功拿到的轻量状态，避免把 loading/error 写进下次首屏。
  if (
    state.unreadCount === null
    && state.latestAiRecord === null
    && state.pendingOrder === null
  ) {
    return null;
  }

  return {
    unreadCount: state.unreadCount,
    latestAiRecord: state.latestAiRecord,
    pendingOrder: state.pendingOrder,
  };
}

function readDashboardSnapshot(userId: number | null): DashboardWorkspaceSnapshot | null {
  if (typeof userId !== "number") {
    return null;
  }

  const storage = getDashboardSnapshotStorage();

  if (!storage) {
    return null;
  }

  const rawValue = storage.getItem(getDashboardSnapshotStorageKey(userId));

  if (!rawValue) {
    return null;
  }

  try {
    const parsed = JSON.parse(rawValue) as Partial<DashboardWorkspaceSnapshot>;

    // Dashboard 快照强绑定学生 userId，防止同浏览器切换账号后串数据。
    if (
      parsed.version !== 1
      || parsed.userId !== userId
      || !parsed.profile
      || typeof parsed.profile.userId !== "number"
      || !Array.isArray(parsed.dailyTasks)
    ) {
      storage.removeItem(getDashboardSnapshotStorageKey(userId));
      return null;
    }

    return {
      version: 1,
      userId,
      savedAt: typeof parsed.savedAt === "string" ? parsed.savedAt : new Date().toISOString(),
      profile: parsed.profile as StudentProfileResponse,
      dailyTasks: parsed.dailyTasks as DailyTaskItemResponse[],
      pointsLedger: (parsed.pointsLedger as PointsLedgerResponse | null | undefined) ?? null,
      checkinOverview: (parsed.checkinOverview as GrowthCheckinOverviewResponse | null | undefined) ?? null,
      continueState: (parsed.continueState as DashboardContinueSnapshot | null | undefined) ?? null,
      growthError: typeof parsed.growthError === "string" ? parsed.growthError : null,
    };
  } catch {
    storage.removeItem(getDashboardSnapshotStorageKey(userId));
    return null;
  }
}

function writeDashboardSnapshot(
  userId: number | null,
  payload: Omit<DashboardWorkspaceSnapshot, "version" | "userId" | "savedAt">,
) {
  if (typeof userId !== "number") {
    return;
  }

  const storage = getDashboardSnapshotStorage();

  if (!storage) {
    return;
  }

  const snapshot: DashboardWorkspaceSnapshot = {
    version: 1,
    userId,
    savedAt: new Date().toISOString(),
    ...payload,
  };

  storage.setItem(getDashboardSnapshotStorageKey(userId), JSON.stringify(snapshot));
}

function mergeGrowthWorkspacePanels(
  previousState: GrowthWorkspacePanelsState,
  nextState: GrowthWorkspaceData,
): GrowthWorkspacePanelsState {
  // 成长面板是三块独立接口，某块失败时保留上一轮成功数据。
  return {
    dailyTasks: nextState.dailyTasksLoaded ? nextState.dailyTasks : previousState.dailyTasks,
    pointsLedger: nextState.pointsLedgerLoaded ? nextState.pointsLedger : previousState.pointsLedger,
    checkinOverview: nextState.checkinOverviewLoaded ? nextState.checkinOverview : previousState.checkinOverview,
  };
}

function getTaskNoteHoverMotion(isCompleted: boolean) {
  return {
    scale: isCompleted ? 1.03 : 1.045,
    rotate: 0,
    y: -6,
    zIndex: 12,
    boxShadow: isCompleted
      ? "0 24px 42px -20px rgba(148,163,184,0.42), 0 14px 24px -20px rgba(15,23,42,0.10)"
      : "0 28px 46px -20px rgba(15,23,42,0.18), 0 16px 30px -22px rgba(15,23,42,0.12)",
  };
}

function getGreeting() {
  const hour = new Date().getHours();

  if (hour < 11) {
    return "早上好";
  }

  if (hour < 18) {
    return "下午好";
  }

  return "晚上好";
}

function calculateProfileCompletion(profile: StudentProfileResponse) {
  const socialLinkCount = Math.min(profile.socialLinks?.length ?? 0, 2);
  // 完整度优先使用后端字段，前端 checklist 用于展示缺口和兜底计算。
  const checklist: ChecklistItem[] = [
    { label: "真实姓名", done: Boolean(profile.realName?.trim()) },
    { label: "当前求职状态", done: Boolean(profile.jobStatus?.trim()) },
    { label: "学校名称", done: Boolean(profile.schoolName?.trim()) },
    { label: "年级", done: Boolean(profile.grade?.trim()) },
    { label: "专业", done: Boolean(profile.major?.trim()) },
    { label: "GPA", done: Boolean(profile.gpa?.trim()) },
    { label: "目标岗位", done: Boolean(profile.targetPosition?.trim()) },
    { label: "荣誉奖项", done: Boolean(profile.honors?.trim()) },
    { label: "外部主页", done: socialLinkCount >= 1 },
    { label: "补充第二个外部主页", done: socialLinkCount >= 2 },
    { label: "手机号", done: Boolean(profile.phone?.trim()) },
    { label: "微信号", done: Boolean(profile.wechat?.trim()) },
    { label: "技能标签", done: Boolean(profile.skillTags?.length) },
    { label: "自我介绍", done: Boolean(profile.selfIntro?.trim()) },
  ];

  const completedCount = checklist.filter((item) => item.done).length;
  const fallbackPercent = Math.round((completedCount / checklist.length) * 100);
  const percent = typeof profile.completionRate === "number"
    ? Math.max(0, Math.min(100, Math.round(profile.completionRate)))
    : fallbackPercent;

  return {
    checklist,
    completedCount,
    percent,
    pendingCount: checklist.length - completedCount,
  };
}

function buildRecommendedAction(
  profile: StudentProfileResponse,
  completion: ReturnType<typeof calculateProfileCompletion>,
  options: {
    signedInToday: boolean;
    remainingTasksCount: number;
  },
): RecommendedAction {
  // 推荐动作按“签到 -> 每日任务 -> 资料缺口 -> AI 练习”逐层收敛。
  if (!options.signedInToday) {
    return {
      sectionLabel: "今天先点亮第一步",
      title: "先完成今日签到，把成长积分领到手",
      description: "先把今天的第一步完成，后面的任务、练习和准备都会更顺手一些。",
      ctaLabel: "去看签到日历",
      href: "#task-board",
      type: "anchor",
    };
  }

  if (options.remainingTasksCount > 0) {
    return {
      sectionLabel: "先把任务压成一条线",
      title: `还有 ${options.remainingTasksCount} 项每日任务待完成`,
      description: "先挑一项最顺手的完成掉，把今天的状态带起来，再去做后面的准备会更轻松。",
      ctaLabel: "查看任务公告板",
      href: "#task-board",
      type: "anchor",
    };
  }

  if (!profile.realName?.trim() || !profile.grade?.trim() || !profile.major?.trim()) {
    return {
      sectionLabel: "优先补齐基础画像",
      title: "先把你的当前阶段说清楚",
      description: "把基础信息补齐后，后面的建议会更贴近你，也更容易看清自己下一步该往哪走。",
      ctaLabel: "查看画像快照",
      href: "#portrait-snapshot",
      type: "anchor",
    };
  }

  if (!profile.targetPosition?.trim()) {
    return {
      sectionLabel: "优先明确目标岗位",
      title: "先把你想投递的方向固定下来",
      description: "方向越清楚，后面的简历准备、面试练习和技能补强就会越聚焦。",
      ctaLabel: "去看画像快照",
      href: "#portrait-snapshot",
      type: "anchor",
    };
  }

  if (!profile.skillTags?.length || !profile.selfIntro?.trim()) {
    return {
      sectionLabel: "画像已经有了骨架",
      title: "再补一点关键词，让准备动作更聚焦",
      description: "技能标签和自我介绍会直接影响简历优化、画像反馈和咨询准备。现在适合先看清楚还有哪些信息没有补齐。",
      ctaLabel: "查看待补内容",
      href: "#portrait-snapshot",
      type: "anchor",
    };
  }

  if ((profile.portrait?.tags?.length ?? 0) === 0) {
    return {
      sectionLabel: "画像还在形成中",
      title: "先做一次真实 AI 练习，给画像喂入新信号",
      description: "你已经具备基础资料了，接下来最有价值的是继续产生行为数据。一次简历优化或模拟面试，都能帮助画像更快成形。",
      ctaLabel: "进入 AI 简历优化",
      href: "/ai/resume",
      type: "route",
    };
  }

  return {
    sectionLabel: "今天建议先做这一件事",
    title: "把画像反馈转成一次具体准备动作",
    description: `资料完整度已达到 ${completion.percent}% ，现在适合直接进入 AI 工具做一次针对性的简历或面试准备。`,
    ctaLabel: "进入模拟面试",
    href: "/ai/interview",
    type: "route",
  };
}

function getReasonLabel(reasonCode: string) {
  if (reasonCode.startsWith("CHECKIN_STREAK_")) {
    return "连签奖励";
  }

  switch (reasonCode) {
    case "CHECKIN":
      return "今日签到";
    case "TASK_RESUME_OPTIMIZE":
      return "简历优化任务";
    case "TASK_SKILL_PROGRESS":
      return "技能成长任务";
    case "TASK_COMMUNITY_INTERACT":
      return "社区互动任务";
    case "TEST_GRANT":
      return "测试补分";
    default:
      return reasonCode;
  }
}

function getTaskVisual(taskCode: string) {
  const doneBadgeClassName = "border-white/80 bg-white/90 text-slate-400";
  const doneIconClassName = "bg-white/90 text-slate-400 ring-1 ring-white/75";
  const actionClassName = "border-slate-300 text-slate-300 hover:border-slate-800 hover:text-slate-700";
  const doneClassName = "border-emerald-500 bg-white/95 text-emerald-500 shadow-[0_12px_24px_rgba(16,185,129,0.16)]";

  switch (taskCode) {
    case "TASK_RESUME_OPTIMIZE":
      return {
        icon: FileText,
        noteClassName: "border-amber-200/80 bg-amber-100/92 shadow-[0_24px_52px_rgba(245,158,11,0.18)]",
        doneNoteClassName: "border-amber-200/65 bg-gradient-to-br from-amber-50/85 via-stone-50 to-white saturate-50 shadow-[0_20px_42px_rgba(148,163,184,0.14)]",
        badgeClassName: "border-white/80 bg-white/75 text-amber-700",
        doneBadgeClassName,
        iconClassName: "bg-white text-amber-600",
        doneIconClassName,
        actionClassName,
        doneClassName,
      };
    case "TASK_SKILL_PROGRESS":
      return {
        icon: Compass,
        noteClassName: "border-pink-200/80 bg-pink-100/92 shadow-[0_24px_52px_rgba(236,72,153,0.18)]",
        doneNoteClassName: "border-pink-200/65 bg-gradient-to-br from-pink-50/85 via-slate-50 to-white saturate-50 shadow-[0_20px_42px_rgba(148,163,184,0.14)]",
        badgeClassName: "border-white/80 bg-white/75 text-pink-700",
        doneBadgeClassName,
        iconClassName: "bg-white text-pink-600",
        doneIconClassName,
        actionClassName,
        doneClassName,
      };
    case "TASK_COMMUNITY_INTERACT":
      return {
        icon: MessageSquare,
        noteClassName: "border-sky-200/80 bg-sky-100/92 shadow-[0_24px_52px_rgba(14,165,233,0.18)]",
        doneNoteClassName: "border-sky-200/65 bg-gradient-to-br from-sky-50/85 via-slate-50 to-white saturate-50 shadow-[0_20px_42px_rgba(148,163,184,0.14)]",
        badgeClassName: "border-white/80 bg-white/75 text-sky-700",
        doneBadgeClassName,
        iconClassName: "bg-white text-sky-600",
        doneIconClassName,
        actionClassName,
        doneClassName,
      };
    default:
      return {
        icon: Sparkles,
        noteClassName: "border-violet-200/80 bg-violet-100/92 shadow-[0_24px_52px_rgba(139,92,246,0.18)]",
        doneNoteClassName: "border-violet-200/65 bg-gradient-to-br from-violet-50/85 via-slate-50 to-white saturate-50 shadow-[0_20px_42px_rgba(148,163,184,0.14)]",
        badgeClassName: "border-white/80 bg-white/75 text-violet-700",
        doneBadgeClassName,
        iconClassName: "bg-white text-violet-600",
        doneIconClassName,
        actionClassName,
        doneClassName,
      };
  }
}

function formatCheckinMonthLabel(currentMonth: string | null | undefined) {
  if (!currentMonth || !/^\d{4}-\d{2}$/.test(currentMonth)) {
    const today = new Date();
    return `${today.getFullYear()} 年 ${today.getMonth() + 1} 月`;
  }

  const [yearString, monthString] = currentMonth.split("-");
  return `${yearString} 年 ${Number(monthString)} 月`;
}

function buildCalendarCells(overview: GrowthCheckinOverviewResponse | null): CalendarDayCell[] {
  if (!overview || !/^\d{4}-\d{2}$/.test(overview.currentMonth)) {
    return [];
  }

  const [yearString, monthString] = overview.currentMonth.split("-");
  const year = Number(yearString);
  const month = Number(monthString);
  const monthStartDay = new Date(year, month - 1, 1).getDay();
  const startOffset = (monthStartDay + 6) % 7;
  const today = new Date();
  const isCurrentMonth = year === today.getFullYear() && month === today.getMonth() + 1;
  const todayDay = isCurrentMonth ? today.getDate() : -1;
  const checkedInDays = new Set(overview.checkedInDays);
  const cells: CalendarDayCell[] = [];

  // 日历以周一为首列，月初前补 placeholder 保持 7 列布局稳定。
  for (let index = 0; index < startOffset; index += 1) {
    cells.push({
      key: `placeholder-${index}`,
      type: "placeholder",
    });
  }

  for (let day = 1; day <= overview.daysInCurrentMonth; day += 1) {
    const isToday = todayDay === day;
    cells.push({
      key: `day-${day}`,
      type: "day",
      day,
      checkedIn: checkedInDays.has(day) || (overview.signedInToday && isToday),
      isToday,
      isFuture: todayDay > 0 ? day > todayDay : false,
    });
  }

  return cells;
}

function getCheckinProgressSummary(overview: GrowthCheckinOverviewResponse | null): CheckinProgressSummary {
  if (!overview) {
    return {
      headline: "签到总览同步中",
      detail: "日历、连签奖励和下一档冲刺信息会在总览接口同步成功后显示。",
      progressValue: 0,
      progressText: "—",
      rewardLabel: "连签奖励稍后更新",
    };
  }

  const rewardRules = [...overview.rewardRules].sort((left, right) => left.streakDays - right.streakDays);
  const nextReward = overview.nextReward;

  if (!nextReward) {
    const topReward = rewardRules[rewardRules.length - 1];

    return {
      headline: topReward ? `已抵达当前最高档：${topReward.title}` : "当前已进入稳定签到节奏",
      detail: topReward ? topReward.description : "继续保持连续签到，可以持续沉淀真实成长行为数据。",
      progressValue: 1,
      progressText: String(overview.currentStreak),
      rewardLabel: topReward ? `最高档奖励 +${formatCount(topReward.bonusPoints)} 积分` : `基础签到 +${formatCount(overview.basePointsPerDay)} 积分`,
    };
  }

  let previousMilestone = 0;
  for (const rule of rewardRules) {
    if (rule.streakDays < nextReward.streakDays) {
      previousMilestone = rule.streakDays;
    }
  }

  const segmentTotal = Math.max(nextReward.streakDays - previousMilestone, 1);
  const segmentDone = Math.max(overview.currentStreak - previousMilestone, 0);

  return {
    headline: `再签到 ${nextReward.remainingDays} 天，解锁 ${nextReward.title}`,
    detail: nextReward.description,
    progressValue: Math.min(segmentDone / segmentTotal, 1),
    progressText: `${overview.currentStreak}/${nextReward.streakDays}`,
    rewardLabel: `额外 +${formatCount(nextReward.bonusPoints)} 积分`,
  };
}

async function fetchGrowthWorkspaceData(): Promise<GrowthWorkspaceData> {
  // 成长工作台允许部分可用，三个接口用 allSettled 分别合并。
  const [tasksResult, ledgerResult, checkinOverviewResult] = await Promise.allSettled([
    apiRequest<DailyTaskItemResponse[]>("/growth/tasks/daily"),
    apiRequest<PointsLedgerResponse>("/growth/points/ledger?page=1&size=12"),
    apiRequest<GrowthCheckinOverviewResponse>("/growth/checkin/overview"),
  ]);

  const missingParts: string[] = [];
  const dailyTasks = tasksResult.status === "fulfilled" ? tasksResult.value : [];
  const pointsLedger = ledgerResult.status === "fulfilled" ? ledgerResult.value : null;
  const checkinOverview = checkinOverviewResult.status === "fulfilled" ? checkinOverviewResult.value : null;

  if (tasksResult.status === "rejected") {
    missingParts.push("每日任务");
  }

  if (ledgerResult.status === "rejected") {
    missingParts.push("积分账本");
  }

  if (checkinOverviewResult.status === "rejected") {
    missingParts.push("签到总览");
  }

  return {
    dailyTasks,
    dailyTasksLoaded: tasksResult.status === "fulfilled",
    pointsLedger,
    pointsLedgerLoaded: ledgerResult.status === "fulfilled",
    checkinOverview,
    checkinOverviewLoaded: checkinOverviewResult.status === "fulfilled",
    errorMessage: missingParts.length > 0
      ? `${missingParts.join("、")}暂时没有同步成功，你仍然可以先浏览已加载的工作台信息。`
      : null,
  };
}

function SkeletonBlock({
  className,
}: {
  className: string;
}) {
  return <div aria-hidden className={joinClasses("bg-slate-200/80 motion-safe:animate-pulse", className)} />;
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

function DashboardTaskSkeletonNote({
  angle,
  noteClassName,
}: {
  angle: number;
  noteClassName: string;
}) {
  return (
    <motion.div
      initial={{ rotate: angle }}
      animate={{ rotate: angle }}
      whileHover={getTaskNoteHoverMotion(false)}
      transition={{ type: "spring", stiffness: 500, damping: 25 }}
      className={joinClasses(
        "group relative flex min-h-[176px] flex-col justify-between rounded-bl-md rounded-br-[1.8rem] rounded-tl-sm rounded-tr-md border px-5 py-4 [transform-origin:center_top] will-change-transform",
        noteClassName,
      )}
    >
      <StickyNotePin />

      <div className="mt-1.5 flex items-start justify-between gap-3">
        <SkeletonBlock className="h-11 w-11 rounded-[0.95rem] bg-white/85 transition-transform duration-300 group-hover:-translate-y-0.5 group-hover:rotate-3" />
        <SkeletonBlock className="h-8 w-8 rounded-full border-2 border-white/85 bg-white/90" />
      </div>

      <div className="mt-4 space-y-2.5">
        <SkeletonBlock className="h-3 w-16 rounded-full bg-white/65" />
        <SkeletonBlock className="h-6 w-4/5 rounded-xl bg-white/80" />
        <SkeletonBlock className="h-3.5 w-full rounded-full bg-white/65" />
        <SkeletonBlock className="h-3.5 w-5/6 rounded-full bg-white/55" />
      </div>

      <div className="mt-5 flex items-center justify-between gap-3">
        <SkeletonBlock className="h-9 w-24 rounded-xl border border-white/80 bg-white/85 transition-transform duration-300 group-hover:-translate-y-0.5" />
        <SkeletonBlock className="h-4 w-16 rounded-full bg-white/60 transition-transform duration-300 group-hover:-translate-y-0.5" />
      </div>

      <div className="pointer-events-none absolute bottom-0 right-0 h-5 w-5 rounded-br-[1.8rem] rounded-tl-xl bg-gradient-to-tl from-black/5 to-transparent transition-opacity duration-300 group-hover:from-black/10" />
    </motion.div>
  );
}

function StickyNotePin() {
  return (
    <div className="absolute -top-3 left-1/2 z-10 -translate-x-1/2 drop-shadow-sm transition-transform duration-300 group-hover:-translate-y-0.5 group-hover:scale-105">
      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M12 14V21" stroke="#64748b" strokeWidth="2" strokeLinecap="round" />
        <path
          d="M9 14H15C15.5523 14 16 13.5523 16 13V9C16 8.5 16.5 8 17 7.5V5H7V7.5C7.5 8 8 8.5 8 9V13C8 13.5523 8.44772 14 9 14Z"
          fill="#ef4444"
          stroke="#dc2626"
          strokeWidth="1"
          strokeLinejoin="round"
        />
        <circle cx="10.5" cy="7.5" r="1.5" fill="white" fillOpacity="0.5" />
      </svg>
    </div>
  );
}

function DashboardOverlayFallback() {
  return (
    <div className="fixed inset-0 z-[130] flex items-center justify-center bg-slate-950/28 p-4 backdrop-blur-sm">
      <div className="rounded-[1.6rem] border border-white/75 bg-white/92 px-6 py-5 shadow-[0_20px_50px_rgba(15,23,42,0.18)]">
        <div className="flex items-center gap-3">
          <div className="h-8 w-8 animate-spin rounded-full border-2 border-slate-200 border-t-indigo-600" />
          <div className="text-sm font-semibold text-slate-600">正在加载弹层...</div>
        </div>
      </div>
    </div>
  );
}

function ContinueDeskCard({
  sectionLabel: _sectionLabel,
  title,
  description,
  meta,
  href,
  actionLabel,
  icon: Icon,
  glowClassName,
  buttonClassName,
  iconClassName,
}: {
  sectionLabel: string;
  title: string;
  description: string;
  meta: ReactNode;
  href: string;
  actionLabel: string;
  icon: LucideIcon;
  glowClassName: string;
  buttonClassName: string;
  iconClassName: string;
}) {
  return (
    <motion.article
      className="relative overflow-hidden rounded-[1.65rem] border border-white/80 bg-white/88 p-3.5 shadow-[0_18px_50px_rgba(148,163,184,0.14)] backdrop-blur-xl"
    >
      <div className={joinClasses("absolute inset-x-0 top-0 h-20 bg-gradient-to-r opacity-90", glowClassName)} />
      <div className="relative flex min-h-[13.75rem] flex-col">
        <div className="flex min-h-[3.55rem] items-center justify-between gap-4">
          <div>
            <h3 className="text-[1.34rem] font-black leading-tight tracking-tight text-slate-950">{title}</h3>
          </div>
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[1rem] bg-white/95 shadow-sm">
            <Icon size={20} className={iconClassName} />
          </div>
        </div>

        <p className="mt-2 min-h-[48px] line-clamp-2 text-sm leading-6 text-slate-600">{description}</p>
        <div className="mt-2 text-sm font-medium leading-6 text-slate-500">{meta}</div>
        <div className="mt-auto pt-3">
          <Link
            to={href}
            className={joinClasses(
              "group/continue relative inline-flex items-center overflow-hidden rounded-full border px-4 py-2 text-sm font-semibold transition-all duration-300 hover:-translate-y-1",
              buttonClassName,
            )}
          >
            <span className="pointer-events-none absolute right-4 top-1/2 h-2 w-2 -translate-y-1/2 rounded-full bg-current opacity-20 transition-all duration-500 group-hover/continue:scale-[12] group-hover/continue:opacity-[0.08]" />
            <span className="relative">{actionLabel}</span>
            <ArrowRight size={16} className="ml-2 transition-transform duration-300 group-hover/continue:translate-x-1.5" />
          </Link>
        </div>
      </div>
    </motion.article>
  );
}

function GrowthCabinCard({
  sectionLabel: _sectionLabel,
  title,
  description,
  meta,
  icon,
  action,
  actionDisabled,
  glowClassName,
}: {
  sectionLabel: string;
  title: string;
  description: string;
  meta?: ReactNode;
  icon: ReactNode;
  action: ReactNode;
  actionDisabled?: boolean;
  glowClassName: string;
}) {
  return (
    <motion.article
      whileHover={{ y: -6, rotate: actionDisabled ? 0 : -0.4 }}
      className={joinClasses(
        "group relative flex h-full flex-col overflow-hidden rounded-[1.65rem] border border-white/80 p-4 shadow-[0_18px_50px_rgba(148,163,184,0.14)] backdrop-blur-xl",
        actionDisabled ? "bg-white/82" : "bg-white/88",
      )}
    >
      <div className={joinClasses("absolute inset-x-0 top-0 h-20 bg-gradient-to-r opacity-90 transition-transform duration-500 group-hover:scale-[1.03]", glowClassName)} />
      <div className="relative flex h-full flex-col">
        <div className="flex min-h-[3.55rem] items-center justify-between gap-4">
          <div>
            <h3 className="text-[1.34rem] font-black leading-tight tracking-tight text-slate-950">{title}</h3>
          </div>
          <div className="flex h-10 w-10 items-center justify-center rounded-[1rem] bg-white/95 shadow-sm transition-transform duration-300 group-hover:-translate-y-1 group-hover:rotate-3">
            {icon}
          </div>
        </div>

        <p className="mt-2 min-h-[52px] line-clamp-2 text-sm leading-6 text-slate-600">{description}</p>
        {meta ? (
          <div className="mt-2 text-sm font-medium leading-6 text-slate-500">{meta}</div>
        ) : null}
        <div className="mt-auto pt-4">{action}</div>
      </div>
    </motion.article>
  );
}

export default function DashboardPage() {
  const { displayName, logout, role, userId } = useAuth();
  const roleLabel = getRoleDisplayLabel(role);
  const initialSnapshotRef = useRef<DashboardWorkspaceSnapshot | null>(
    // 学生工作台首屏先读 session 快照，后台再刷新画像和成长数据。
    role === "STUDENT" ? readDashboardSnapshot(userId) : null,
  );
  const initialSnapshot = initialSnapshotRef.current;
  const [profile, setProfile] = useState<StudentProfileResponse | null>(() => initialSnapshot?.profile ?? null);
  const [dailyTasks, setDailyTasks] = useState<DailyTaskItemResponse[]>(() => initialSnapshot?.dailyTasks ?? []);
  const [pointsLedger, setPointsLedger] = useState<PointsLedgerResponse | null>(() => initialSnapshot?.pointsLedger ?? null);
  const [checkinOverview, setCheckinOverview] = useState<GrowthCheckinOverviewResponse | null>(() => initialSnapshot?.checkinOverview ?? null);
  const [loading, setLoading] = useState(role === "STUDENT");
  const [error, setError] = useState<string | null>(null);
  const [growthError, setGrowthError] = useState<string | null>(() => initialSnapshot?.growthError ?? null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [checkinSubmitting, setCheckinSubmitting] = useState(false);
  const [reward, setReward] = useState<RewardState | null>(null);
  const [calendarOpen, setCalendarOpen] = useState(false);
  const [celebrationOverlayLoaded, setCelebrationOverlayLoaded] = useState(false);
  const [calendarOverlayLoaded, setCalendarOverlayLoaded] = useState(false);
  const [continueState, setContinueState] = useState<DashboardContinueState>(() => ({
    loading: role === "STUDENT",
    error: null,
    unreadCount: initialSnapshot?.continueState?.unreadCount ?? null,
    latestAiRecord: initialSnapshot?.continueState?.latestAiRecord ?? null,
    pendingOrder: initialSnapshot?.continueState?.pendingOrder ?? null,
  }));
  const [continueDeskIndex, setContinueDeskIndex] = useState(0);
  const workspaceStateRef = useRef<{
    profile: StudentProfileResponse | null;
    dailyTasks: DailyTaskItemResponse[];
    pointsLedger: PointsLedgerResponse | null;
    checkinOverview: GrowthCheckinOverviewResponse | null;
    continueState: DashboardContinueState;
  }>({
    profile: initialSnapshot?.profile ?? null,
    dailyTasks: initialSnapshot?.dailyTasks ?? [],
    pointsLedger: initialSnapshot?.pointsLedger ?? null,
    checkinOverview: initialSnapshot?.checkinOverview ?? null,
    continueState: {
      loading: role === "STUDENT",
      error: null,
      unreadCount: initialSnapshot?.continueState?.unreadCount ?? null,
      latestAiRecord: initialSnapshot?.continueState?.latestAiRecord ?? null,
      pendingOrder: initialSnapshot?.continueState?.pendingOrder ?? null,
    },
  });

  const openCalendarOverlay = () => {
    setCalendarOverlayLoaded(true);
    setCalendarOpen(true);
  };

  const closeCalendarOverlay = () => {
    setCalendarOpen(false);
  };

  const openCelebrationOverlay = (nextReward: RewardState) => {
    setCelebrationOverlayLoaded(true);
    setReward(nextReward);
  };

  const closeCelebrationOverlay = () => {
    setReward(null);
  };

  useEffect(() => {
    // workspaceStateRef 给异步回调读取最新快照，避免闭包拿到旧面板状态。
    workspaceStateRef.current = {
      profile,
      dailyTasks,
      pointsLedger,
      checkinOverview,
      continueState,
    };
  }, [checkinOverview, continueState, dailyTasks, pointsLedger, profile]);

  useEffect(() => {
    if (role !== "STUDENT") {
      return;
    }

    const snapshot = readDashboardSnapshot(userId);

    // 切换账号或重新进入学生工作台时，先恢复当前用户自己的快照。
    setProfile(snapshot?.profile ?? null);
    setDailyTasks(snapshot?.dailyTasks ?? []);
    setPointsLedger(snapshot?.pointsLedger ?? null);
    setCheckinOverview(snapshot?.checkinOverview ?? null);
    setContinueState({
      loading: role === "STUDENT",
      error: null,
      unreadCount: snapshot?.continueState?.unreadCount ?? null,
      latestAiRecord: snapshot?.continueState?.latestAiRecord ?? null,
      pendingOrder: snapshot?.continueState?.pendingOrder ?? null,
    });
    setGrowthError(snapshot?.growthError ?? null);
    setError(null);
  }, [role, userId]);

  useEffect(() => {
    if (role !== "STUDENT") {
      setLoading(false);
      setError(null);
      setGrowthError(null);
      setContinueState({
        loading: false,
        error: null,
        unreadCount: null,
        latestAiRecord: null,
        pendingOrder: null,
      });
      setProfile(null);
      setDailyTasks([]);
      setPointsLedger(null);
      setCheckinOverview(null);
      return;
    }

    let active = true;

    const loadDashboard = async () => {
      const cachedSnapshot = readDashboardSnapshot(userId);
      const currentProfileUserId = workspaceStateRef.current.profile?.userId ?? null;
      // 同一学生刷新时可复用内存态；跨账号则退回该账号的 session 快照。
      const canReuseCurrentWorkspace = currentProfileUserId !== null && (userId === null || currentProfileUserId === userId);
      const fallbackGrowthPanels = canReuseCurrentWorkspace
        ? {
          dailyTasks: workspaceStateRef.current.dailyTasks,
          pointsLedger: workspaceStateRef.current.pointsLedger,
          checkinOverview: workspaceStateRef.current.checkinOverview,
        }
        : {
          dailyTasks: cachedSnapshot?.dailyTasks ?? [],
          pointsLedger: cachedSnapshot?.pointsLedger ?? null,
          checkinOverview: cachedSnapshot?.checkinOverview ?? null,
        };
      const fallbackProfile = canReuseCurrentWorkspace ? workspaceStateRef.current.profile : cachedSnapshot?.profile ?? null;

      setLoading(true);
      setError(null);

      try {
        // 资料和成长面板并行加载，成长接口局部失败不影响资料主链。
        const [profileResponse, growthData] = await Promise.all([
          apiRequest<StudentProfileResponse>("/profiles/students/me"),
          fetchGrowthWorkspaceData(),
        ]);

        if (!active) {
          return;
        }

        const mergedGrowthPanels = mergeGrowthWorkspacePanels(
          fallbackGrowthPanels,
          growthData,
        );

        // 写快照时保留继续入口，避免后续成长刷新把通知/订单入口抹掉。
        setProfile(profileResponse);
        setDailyTasks(mergedGrowthPanels.dailyTasks);
        setPointsLedger(mergedGrowthPanels.pointsLedger);
        setCheckinOverview(mergedGrowthPanels.checkinOverview);
        setGrowthError(growthData.errorMessage);
        writeDashboardSnapshot(userId ?? profileResponse.userId, {
          profile: profileResponse,
          dailyTasks: mergedGrowthPanels.dailyTasks,
          pointsLedger: mergedGrowthPanels.pointsLedger,
          checkinOverview: mergedGrowthPanels.checkinOverview,
          continueState: buildContinueSnapshot(workspaceStateRef.current.continueState),
          growthError: growthData.errorMessage,
        });
      } catch (requestError) {
        if (!active) {
          return;
        }

        const apiError = requestError as ApiClientError;
        if (fallbackProfile) {
          setGrowthError(apiError.message || "工作台暂时没有同步成功，先为你保留上一次加载的内容。");
        } else {
          setError(apiError.message || "学生工作台加载失败，请稍后重试。");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    };

    void loadDashboard();

    return () => {
      active = false;
    };
  }, [refreshKey, role, userId]);

  useEffect(() => {
    if (role !== "STUDENT") {
      return;
    }

    let active = true;
    setContinueState((current) => ({
      ...current,
      loading: true,
      error: null,
    }));

    void Promise.allSettled([
      // 继续入口聚合通知、AI 历史和咨询订单，任何一块失败都不阻塞其他入口。
      getNotificationUnreadCount(),
      apiRequest<DashboardContinueAiHistoryResponse>(`/ai/history${buildQuery({ page: 1, size: 10 })}`),
      apiRequest<DashboardContinueConsultOrderResponse>(`/consult/orders${buildQuery({ page: 1, size: 6 })}`),
    ]).then((results) => {
      if (!active) {
        return;
      }

      const [notificationResult, aiResult, orderResult] = results;
      const partialErrors: string[] = [];

      if (notificationResult.status === "rejected") {
        partialErrors.push("未读通知");
      }
      if (aiResult.status === "rejected") {
        partialErrors.push("AI 复盘");
      }
      if (orderResult.status === "rejected") {
        partialErrors.push("咨询订单");
      }

      setContinueState({
        loading: false,
        error: partialErrors.length > 0
          ? `${partialErrors.join(" / ")} 暂时没有同步成功，已先展示当前可用的继续入口。`
          : null,
        unreadCount: notificationResult.status === "fulfilled" ? notificationResult.value.unreadCount : null,
        latestAiRecord: aiResult.status === "fulfilled" ? pickContinueAiRecord(aiResult.value.records) : null,
        pendingOrder: orderResult.status === "fulfilled"
          ? orderResult.value.records.find((item) => isContinuableConsultOrderStatus(item.status)) ?? null
          : null,
      });

      const snapshotProfile = workspaceStateRef.current.profile;
      const profileUserId = snapshotProfile?.userId ?? userId ?? null;
      if (profileUserId !== null && snapshotProfile) {
        // 继续入口刷新后回写同一 Dashboard 快照，让首屏“接着做”不闪空。
        const nextContinueState: DashboardContinueState = {
          loading: false,
          error: partialErrors.length > 0
            ? `${partialErrors.join(" / ")} 暂时没有同步成功，已先展示当前可用的继续入口。`
            : null,
          unreadCount: notificationResult.status === "fulfilled" ? notificationResult.value.unreadCount : null,
          latestAiRecord: aiResult.status === "fulfilled" ? pickContinueAiRecord(aiResult.value.records) : null,
          pendingOrder: orderResult.status === "fulfilled"
            ? orderResult.value.records.find((item) => isContinuableConsultOrderStatus(item.status)) ?? null
            : null,
        };
        writeDashboardSnapshot(profileUserId, {
          profile: snapshotProfile,
          dailyTasks: workspaceStateRef.current.dailyTasks,
          pointsLedger: workspaceStateRef.current.pointsLedger,
          checkinOverview: workspaceStateRef.current.checkinOverview,
          continueState: buildContinueSnapshot(nextContinueState),
          growthError: growthError,
        });
      }
    });

    return () => {
      active = false;
    };
  }, [growthError, refreshKey, role, userId]);

  async function refreshGrowthPanels() {
    // 签到或重复签到兜底后，只刷新成长三块，不重拉整个学生资料。
    const growthData = await fetchGrowthWorkspaceData();
    const mergedGrowthPanels = mergeGrowthWorkspacePanels(
      {
        dailyTasks: workspaceStateRef.current.dailyTasks,
        pointsLedger: workspaceStateRef.current.pointsLedger,
        checkinOverview: workspaceStateRef.current.checkinOverview,
      },
      growthData,
    );
    setDailyTasks(mergedGrowthPanels.dailyTasks);
    setPointsLedger(mergedGrowthPanels.pointsLedger);
    setCheckinOverview(mergedGrowthPanels.checkinOverview);
    setGrowthError(growthData.errorMessage);
    if (workspaceStateRef.current.profile) {
      writeDashboardSnapshot(userId ?? workspaceStateRef.current.profile.userId, {
        profile: workspaceStateRef.current.profile,
        dailyTasks: mergedGrowthPanels.dailyTasks,
        pointsLedger: mergedGrowthPanels.pointsLedger,
        checkinOverview: mergedGrowthPanels.checkinOverview,
        continueState: buildContinueSnapshot(workspaceStateRef.current.continueState),
        growthError: growthData.errorMessage,
      });
    }
  }

  async function handleCheckin() {
    if (checkinSubmitting) {
      return;
    }

    setCheckinSubmitting(true);

    try {
      // 签到成功后先弹奖励层，再刷新任务、积分和日历状态。
      const response = await apiRequest<GrowthCheckinResponse>("/growth/checkin", {
        method: "POST",
      });

      closeCalendarOverlay();
      openCelebrationOverlay({
        title: response.triggeredReward ? `解锁 ${response.triggeredReward.title}` : "签到成功",
        subtitle: response.triggeredReward
          ? `连续签到 ${response.streak} 天，额外奖励已经到账。`
          : response.streak > 1
            ? `今天的签到已计入成长记录，当前连续签到 ${response.streak} 天。`
            : "今天的签到已计入成长记录，新的准备节奏已经开启。",
        points: response.pointsEarned,
        tone: "amber",
        highlight: response.triggeredReward
          ? response.triggeredReward.description
          : `基础签到 +${formatCount(response.basePointsEarned)} 积分，当前余额 ${formatCount(response.newBalance)}`,
      });
      await refreshGrowthPanels();
    } catch (requestError) {
      const apiError = requestError as ApiClientError;

      if (apiError.code === "BIZ-1301") {
        // 重复签到属于状态不同步，刷新成长面板后展示今日完成态。
        await refreshGrowthPanels();
        closeCalendarOverlay();
        setGrowthError("你今天已经签到过了，工作台已按今日完成态重新同步。");
      } else {
        setGrowthError(apiError.message || "签到失败，请稍后重试。");
      }
    } finally {
      setCheckinSubmitting(false);
    }
  }

  const showInitialSkeleton = loading && !profile;
  const isBackgroundRefreshing = loading && !!profile;
  const signedInTodayPreview = checkinOverview?.signedInToday
    ?? (pointsLedger?.records ?? []).some(
      (record) => record.reasonCode === "CHECKIN" && isSameLocalDate(record.createdAt, new Date()),
    );

  useEffect(() => {
    if (isBackgroundRefreshing) {
      console.debug("[DashboardPage] 正在刷新今天的进度");
      return;
    }

    if (!showInitialSkeleton) {
      console.debug("[DashboardPage] 今天的进度刷新完成");
    }
  }, [isBackgroundRefreshing, showInitialSkeleton]);

  useEffect(() => {
    if (!signedInTodayPreview) {
      setContinueDeskIndex(0);
    }
  }, [signedInTodayPreview]);

  useEffect(() => {
    if (!signedInTodayPreview || showInitialSkeleton) {
      return;
    }

    const timer = window.setInterval(() => {
      setContinueDeskIndex((current) => (current + 1) % CONTINUE_DESK_ENTRY_COUNT);
    }, CONTINUE_DESK_ROTATION_MS);

    return () => window.clearInterval(timer);
  }, [showInitialSkeleton, signedInTodayPreview]);

  if (showInitialSkeleton) {
    return (
      <WorkspacePageLoadingScreen
        title="正在准备学生工作台"
        description="正在加载今日进度、成长画像和任务提醒，请稍候。"
      />
    );
  }

  if (!profile) {
    return (
      <div className="relative min-h-screen bg-slate-950 text-white">
        <div className="pointer-events-none fixed inset-0 z-0 overflow-hidden">
          <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(99,102,241,0.35),transparent_36%),linear-gradient(180deg,#020617_0%,#0f172a_100%)]" />
        </div>
        <div className="relative z-10 mx-auto flex min-h-screen w-full max-w-4xl items-center px-6 py-16 lg:px-8">
          <div className="w-full rounded-[2rem] border border-rose-400/20 bg-white/8 p-8 shadow-[0_32px_80px_rgba(15,23,42,0.42)] backdrop-blur-xl lg:p-10">
            <div className="inline-flex items-center gap-2 rounded-full border border-rose-400/20 bg-rose-400/10 px-4 py-2 text-xs text-rose-100">
              <Bell size={14} />
              工作台读取失败
            </div>
            <h1 className="mt-6 text-3xl font-semibold tracking-tight text-white lg:text-4xl">
              学生画像暂时没有成功加载
            </h1>
            <p className="mt-4 max-w-2xl text-base leading-8 text-slate-300">
              {error || "这一步通常与会话过期、网络波动或画像资料尚未写入有关。你可以先重试，也可以先返回首页继续浏览公开入口。"}
            </p>
            <div className="mt-8 flex flex-wrap gap-4">
              <button
                type="button"
                onClick={() => setRefreshKey((value) => value + 1)}
                className="inline-flex items-center justify-center rounded-full bg-white px-6 py-3 text-sm font-semibold text-slate-900 transition-transform hover:-translate-y-0.5"
              >
                重新加载
              </button>
              <Link
                to="/"
                className="inline-flex items-center justify-center rounded-full border border-white/16 bg-white/8 px-6 py-3 text-sm font-semibold text-white transition-colors hover:bg-white/12"
              >
                返回首页
              </Link>
            </div>
          </div>
        </div>
      </div>
    );
  }

  const profileData: StudentProfileResponse = profile ?? {
    userId: 0,
    displayName: displayName || "同学",
    completionRate: null,
    realName: null,
    jobStatus: null,
    schoolName: null,
    major: null,
    grade: null,
    gpa: null,
    targetPosition: null,
    honors: null,
    socialLinks: null,
    phone: null,
    wechat: null,
    skillTags: null,
    selfIntro: null,
    portrait: null,
    communityScore7d: 0,
  };
  const greeting = getGreeting();
  const studentName = buildStudentNickname(profileData, displayName, "同学");
  const completion = calculateProfileCompletion(profileData);
  const portraitTags = profileData.portrait?.tags ?? [];
  const portraitEvidence = profileData.portrait?.evidence;
  const ledgerRecords = pointsLedger?.records ?? [];
  const latestLedgerRecord = ledgerRecords[0] ?? null;
  const signedInTodayByLedger = ledgerRecords.some(
    (record) => record.reasonCode === "CHECKIN" && isSameLocalDate(record.createdAt, new Date()),
  );
  const signedInToday = checkinOverview?.signedInToday ?? signedInTodayByLedger;
  const growthJourneyDays = checkinOverview?.growthJourneyDays ?? null;
  const currentStreak = checkinOverview?.currentStreak ?? 0;
  const completedTasksCount = dailyTasks.filter((task) => task.completed).length;
  const remainingTasksCount = Math.max(dailyTasks.length - completedTasksCount, 0);
  const action = showInitialSkeleton
    ? null
    : buildRecommendedAction(profileData, completion, {
      signedInToday,
      remainingTasksCount,
    });
  const checkinSummary = signedInToday
    ? currentStreak > 0
      ? `连续签到 ${formatCount(currentStreak)} 天`
      : "今日已签到"
    : currentStreak > 0
      ? `当前连签 ${formatCount(currentStreak)} 天`
      : "今日待签到";
  const portraitUpdatedLabel = profileData.portrait?.updatedAt
    ? formatDateTime(profileData.portrait.updatedAt)
    : "画像会在资料与行为积累后刷新";
  const checkinProgress = getCheckinProgressSummary(checkinOverview);
  const checkinCalendarCells = buildCalendarCells(checkinOverview);
  const checkinMonthLabel = formatCheckinMonthLabel(checkinOverview?.currentMonth);
  const heroJourneyDays = showInitialSkeleton ? 0 : growthJourneyDays;
  const boardBalanceValue = showInitialSkeleton ? 0 : pointsLedger?.balance ?? null;
  const completionDisplayValue = showInitialSkeleton ? 0 : completion.percent;
  const completionProgressWidth = showInitialSkeleton ? 0 : completion.percent;
  const masteredSkillsValue = showInitialSkeleton ? 0 : portraitEvidence?.masteredSkills ?? 0;
  const learningSkillsValue = showInitialSkeleton ? 0 : portraitEvidence?.learningSkills ?? 0;
  const communityScoreValue = showInitialSkeleton ? 0 : profileData.communityScore7d;
  const interviewSignalValue = showInitialSkeleton ? 0 : portraitEvidence?.interviewMessages7d ?? 0;
  const missingProfileItems = completion.checklist.filter((item) => !item.done);
  const visibleMissingProfileItems = missingProfileItems.slice(0, 4);
  const hiddenMissingProfileCount = Math.max(missingProfileItems.length - visibleMissingProfileItems.length, 0);
  const showCompletionCard = showInitialSkeleton || completion.percent < 100;
  const showMissingProfileCard = showInitialSkeleton || missingProfileItems.length > 0;
  const boardTasks = showInitialSkeleton
    ? []
    : [...dailyTasks]
      .sort((left, right) => Number(left.completed) - Number(right.completed))
      .slice(0, 4);
  const taskBoardPlaceholderCount = showInitialSkeleton ? 0 : Math.max(4 - boardTasks.length, 0);
  const footprintItems = showInitialSkeleton
    ? []
    : [
      {
        id: "checkin",
        content: signedInToday
          ? `今天已经完成签到，当前连签 ${formatCount(currentStreak)} 天。`
          : currentStreak > 0
            ? `距离下一档连签奖励还差 ${formatCount(checkinOverview?.nextReward?.remainingDays ?? 1)} 天，适合先把签到补上。`
            : "今天还没有签到，先点亮第一枚成长火种会更合适。",
        meta: checkinProgress.rewardLabel,
      },
      {
        id: "tasks",
        content: remainingTasksCount > 0
          ? `今天还有 ${formatCount(remainingTasksCount)} 项任务待完成，最适合先挑一张便签解决。`
          : "今日任务已经清空，可以直接转去 AI 简历优化或模拟面试继续积累画像。",
        meta: dailyTasks.length > 0 ? `完成度 ${completedTasksCount}/${dailyTasks.length}` : "任务清单准备中",
      },
      {
        id: "ledger",
        content: latestLedgerRecord
          ? `最近一笔成长记录是“${getReasonLabel(latestLedgerRecord.reasonCode)}”，积分余额现为 ${pointsLedger ? formatCount(pointsLedger.balance) : "—"}。`
          : "还没有读取到最近积分流水，完成签到或任务后会在这里留下真实痕迹。",
        meta: latestLedgerRecord ? formatDateTime(latestLedgerRecord.createdAt) : "等待第一条成长记录",
      },
      {
        id: "portrait",
        content: portraitTags.length > 0
          ? `画像已经形成 ${formatCount(portraitTags.length)} 个标签，继续使用 AI 工具会带来更多有效信号。`
          : missingProfileItems.length > 0
            ? `资料完整度现在是 ${completion.percent}% ，核心资料里还有 ${formatCount(missingProfileItems.length)} 项待补，画像会更快稳定下来。`
            : `资料完整度现在是 ${completion.percent}% ，其余信息可以继续到资料中心补齐，画像会随着资料和行为信号继续刷新。`,
        meta: portraitUpdatedLabel,
      },
    ];
  const continueAiHref = getContinueAiHref(continueState.latestAiRecord);
  const continueAiActionLabel = continueState.latestAiRecord
    ? continueState.latestAiRecord.taskType === "RESUME"
      ? "查看简历复盘"
      : isContinuableAiInterviewRecord(continueState.latestAiRecord)
        ? "继续这轮面试"
        : "查看面试复盘"
    : "打开复盘中心";
  const continueAiMeta = continueState.latestAiRecord
    ? `${continueState.latestAiRecord.taskType === "RESUME"
      ? "简历复盘"
      : isContinuableAiInterviewRecord(continueState.latestAiRecord)
        ? "进行中面试"
        : "模拟面试"} · ${formatDateTime(continueState.latestAiRecord.createdAt)}`
    : "简历优化与模拟面试都会统一回流";
  const continueOrderHref = continueState.pendingOrder
    ? `/consult/orders/${encodeURIComponent(continueState.pendingOrder.orderNo)}`
    : "/consult/orders";
  const continueOrderMeta = continueState.pendingOrder
    ? `${getContinueConsultOrderStatusLabel(continueState.pendingOrder.status)} · ${formatDateTime(getLatestContinueTimestamp(continueState.pendingOrder))}`
    : "支付、消息、评价和售后都在订单页承接";
  const continueOrderActionLabel = continueState.pendingOrder
    ? getContinueConsultOrderActionLabel(continueState.pendingOrder)
    : "查看全部订单";
  const continueDeskEntries = [
    {
      sectionLabel: "Notify",
      title: "通知中心",
      description: continueState.unreadCount && continueState.unreadCount > 0
        ? `当前有 ${formatCount(continueState.unreadCount)} 条新提醒待查看，先回看最新动态。`
        : "AI 结果、订单进度和任务提醒都会继续统一回到这里。",
      meta: continueState.unreadCount && continueState.unreadCount > 0 ? "优先处理新提醒" : "支持回看历史通知与提醒偏好",
      href: "/notifications",
      actionLabel: continueState.unreadCount && continueState.unreadCount > 0 ? "查看通知" : "打开通知中心",
      icon: Bell,
      glowClassName: "from-indigo-200 via-indigo-50 to-white",
      buttonClassName: "border-indigo-600/70 bg-transparent text-indigo-700 shadow-[0_14px_30px_rgba(79,70,229,0.10)] hover:border-indigo-700 hover:text-indigo-800",
      iconClassName: "text-indigo-600",
    },
    {
      sectionLabel: continueState.latestAiRecord && isContinuableAiInterviewRecord(continueState.latestAiRecord) ? "Interview" : "Review",
      title: continueState.latestAiRecord
        ? continueState.latestAiRecord.taskType === "RESUME"
          ? "简历复盘"
          : isContinuableAiInterviewRecord(continueState.latestAiRecord)
            ? "继续面试"
            : "面试复盘"
        : "AI 复盘中心",
      description: continueState.latestAiRecord
        ? isContinuableAiInterviewRecord(continueState.latestAiRecord)
          ? continueState.latestAiRecord.summary?.trim() || "有一轮模拟面试仍在进行中，可以直接回到会话页继续作答。"
          : continueState.latestAiRecord.summary?.trim() || "最近一条复盘结果已经生成，适合继续回看结论并推进下一步。"
        : "简历优化和模拟面试的结果都会沉淀到这里，方便统一回看。",
      meta: continueAiMeta,
      href: continueAiHref,
      actionLabel: continueAiActionLabel,
      icon: Sparkles,
      glowClassName: "from-teal-200 via-teal-50 to-white",
      buttonClassName: "border-teal-600/70 bg-transparent text-teal-700 shadow-[0_14px_30px_rgba(20,184,166,0.10)] hover:border-teal-700 hover:text-teal-800",
      iconClassName: "text-teal-600",
    },
    {
      sectionLabel: "Orders",
      title: "咨询订单",
      description: continueState.pendingOrder
        ? `与 ${continueState.pendingOrder.counterpartDisplayName} 的咨询仍在进行，回到订单页继续处理后续动作。`
        : "支付、补材料、查看答复和售后处理，都会统一收在订单页。",
      meta: continueOrderMeta,
      href: continueOrderHref,
      actionLabel: continueOrderActionLabel,
      icon: ReceiptText,
      glowClassName: "from-cyan-200 via-cyan-50 to-white",
      buttonClassName: "border-cyan-600/70 bg-transparent text-cyan-700 shadow-[0_14px_30px_rgba(6,182,212,0.10)] hover:border-cyan-700 hover:text-cyan-800",
      iconClassName: "text-cyan-600",
    },
  ] as const;
  const activeContinueDeskEntry = continueDeskEntries[continueDeskIndex] ?? continueDeskEntries[0];
  const quickEntries: Array<{
    title: string;
    description: string;
    sectionLabel: string;
    meta?: ReactNode;
    icon: LucideIcon;
    glowClassName: string;
    buttonClassName: string;
    href?: string;
    actionLabel: string;
    disabled?: boolean;
  }> = [
    {
      title: "简历实验室",
      description: "围绕目标岗位快速整理简历，尽快拿到更清晰的修改建议。",
      sectionLabel: "Resume",
      icon: FileText,
      glowClassName: "from-sky-200 via-sky-50 to-white",
      buttonClassName: "border-sky-600/70 bg-transparent text-sky-700 shadow-[0_14px_30px_rgba(59,130,246,0.10)] hover:border-sky-700 hover:text-sky-800",
      href: "/ai/resume",
      actionLabel: "去优化简历",
    },
    {
      title: "全真模拟面试",
      description: "用一场贴近真实场景的模拟面试，练回答节奏和临场表达。",
      sectionLabel: "Interview",
      icon: Bot,
      glowClassName: "from-teal-200 via-teal-50 to-white",
      buttonClassName: "border-teal-600/70 bg-transparent text-teal-700 shadow-[0_14px_30px_rgba(20,184,166,0.10)] hover:border-teal-700 hover:text-teal-800",
      href: "/ai/interview",
      actionLabel: "开启面练",
    },
    {
      title: "技能星图",
      description: "看看自己已经掌握了什么、还在补哪一块，把能力地图继续点亮。",
      sectionLabel: "Skills",
      icon: Target,
      glowClassName: "from-violet-200 via-violet-50 to-white",
      buttonClassName: "border-violet-600/70 bg-transparent text-violet-700 shadow-[0_14px_30px_rgba(139,92,246,0.10)] hover:border-violet-700 hover:text-violet-800",
      href: "/skills",
      actionLabel: "进入技能星图",
    },
    {
      title: "AI 复盘中心",
      description: "把简历和面试结果放到同一个入口回看，决定下一步怎么推进。",
      sectionLabel: "Review",
      icon: Sparkles,
      glowClassName: "from-indigo-200 via-indigo-50 to-white",
      buttonClassName: "border-indigo-600/70 bg-transparent text-indigo-700 shadow-[0_14px_30px_rgba(79,70,229,0.10)] hover:border-indigo-700 hover:text-indigo-800",
      href: "/ai/history",
      actionLabel: "打开复盘中心",
    },
    {
      title: "社区互助站",
      description: "去社区提问、看经验贴或参与讨论，继续补充你的求职信息流。",
      sectionLabel: "Community",
      icon: MessageSquare,
      glowClassName: "from-rose-200 via-rose-50 to-white",
      buttonClassName: "border-rose-600/70 bg-transparent text-rose-700 shadow-[0_14px_30px_rgba(244,63,94,0.10)] hover:border-rose-700 hover:text-rose-800",
      href: "/community",
      actionLabel: "进入社区互助",
    },
    {
      title: "大厂导师局",
      description: "先浏览导师服务与方向，再决定要不要发起一对一咨询。",
      sectionLabel: "Mentor",
      icon: GraduationCap,
      glowClassName: "from-amber-200 via-amber-50 to-white",
      buttonClassName: "border-amber-600/70 bg-transparent text-amber-700 shadow-[0_14px_30px_rgba(245,158,11,0.10)] hover:border-amber-700 hover:text-amber-800",
      href: "/mentors",
      actionLabel: "浏览导师大厅",
    },
    {
      title: "我的咨询单",
      description: "已创建的咨询订单都会回到这里，支付、补材料和查看回复更顺手。",
      sectionLabel: "Orders",
      icon: ReceiptText,
      glowClassName: "from-cyan-200 via-cyan-50 to-white",
      buttonClassName: "border-cyan-600/70 bg-transparent text-cyan-700 shadow-[0_14px_30px_rgba(6,182,212,0.10)] hover:border-cyan-700 hover:text-cyan-800",
      href: "/consult/orders",
      actionLabel: "查看我的订单",
    },
    {
      title: "企业实战任务",
      description: "浏览企业发布的任务机会，提交成果后回看审核反馈。",
      sectionLabel: "Bounty",
      icon: Award,
      glowClassName: "from-violet-200 via-violet-50 to-white",
      buttonClassName: "border-violet-600/70 bg-transparent text-violet-700 shadow-[0_14px_30px_rgba(124,58,237,0.10)] hover:border-violet-700 hover:text-violet-800",
      href: "/bounty",
      actionLabel: "去看实战任务",
    },
  ];

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

  return (
    <div className="relative min-h-screen bg-[#eef3ff] text-slate-900">
      <div className="pointer-events-none fixed inset-0 z-0 overflow-hidden">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(79,70,229,0.18),transparent_30%),radial-gradient(circle_at_bottom_right,rgba(20,184,166,0.12),transparent_28%),linear-gradient(180deg,#eff6ff_0%,#f8fafc_58%,#f8fafc_100%)]" />
        <div className="page-top-glow page-top-glow--indigo" />
      </div>

      <StudentWorkspaceTopbar
        sectionLabel="Student Dashboard"
        title="学生工作台"
        navItems={buildStudentWorkspacePrimaryNav("dashboard")}
        rightActions={(
          <button
            type="button"
            onClick={() => setRefreshKey((value) => value + 1)}
            disabled={loading}
            className="inline-flex h-10 items-center justify-center rounded-full border border-slate-200 bg-white px-4 text-sm font-semibold text-slate-600 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600 disabled:cursor-not-allowed disabled:opacity-60"
          >
            <RefreshCw size={17} className={joinClasses(loading && "animate-spin")} />
          </button>
        )}
        position="fixed"
        captureKey={`dashboard-loading:${loading ? "1" : "0"}`}
        userId={profileData.userId || undefined}
        displayName={studentName}
        avatar={profileData.avatar}
        tier={profileData.tier}
        userSubtitle={roleLabel}
      />

      <main className="relative z-10 mx-auto w-full max-w-[96rem] px-4 pb-6 pt-24 sm:px-6 lg:px-8 lg:pb-8 lg:pt-24">
        <motion.div variants={containerVariants} initial="hidden" animate="show" className="space-y-8">
          <motion.section id="overview" variants={itemVariants} className="scroll-mt-28 grid gap-4 xl:grid-cols-[1.72fr_0.88fr]">
            <div className="relative overflow-hidden rounded-[2.25rem] bg-gradient-to-br from-indigo-900 via-indigo-800 to-blue-950 px-6 py-6 text-white shadow-[0_24px_58px_rgba(67,56,202,0.28)] lg:px-7 lg:py-7">
              <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_right,rgba(255,255,255,0.16),transparent_36%),linear-gradient(135deg,transparent_0%,rgba(255,255,255,0.04)_100%)]" />
              <div className="absolute -right-16 -top-14 opacity-10">
                <Compass size={220} />
              </div>
              <div className="absolute -bottom-20 right-0 h-52 w-52 rounded-full bg-cyan-400/16 blur-3xl" />

              <div className="relative flex h-full flex-col justify-between gap-5">
                <div className="space-y-3">
                  <div>
                    <h1 className="max-w-3xl text-3xl font-semibold tracking-tight text-white lg:text-[3.35rem]">
                      {greeting}，{studentName}
                    </h1>
                    <p className="mt-3 max-w-2xl text-[15px] leading-7 text-indigo-100/90 lg:text-[17px]">
                      {heroJourneyDays !== null ? (
                        <>
                          今天是你开启这段成长旅程的第{" "}
                          <AnimatedCount
                            value={heroJourneyDays}
                            className="px-1 text-xl font-bold text-teal-300 lg:text-2xl"
                            formatter={(nextValue) => formatCount(nextValue)}
                          />
                          天。<br className="hidden md:block" />
                          先完成签到，再挑一件最顺手的事推进，慢慢把今天的状态和节奏都带起来。
                        </>
                      ) : (
                        <>
                          今天先把最重要的几件事排清楚，一步一步来，后面的准备就会顺很多。
                        </>
                      )}
                    </p>
                  </div>
                </div>

                <div className="flex flex-wrap gap-2.5">
                  <div className="rounded-full border border-white/14 bg-white/10 px-4 py-1.5 text-sm text-indigo-50">
                    目标岗位：{profileData.targetPosition?.trim() || (showInitialSkeleton ? "整理中" : "待明确")}
                  </div>
                  <div className="rounded-full border border-white/14 bg-white/10 px-4 py-1.5 text-sm text-indigo-50">
                    专业：{profileData.major?.trim() || (showInitialSkeleton ? "整理中" : "待补充")}
                  </div>
                  <div className="rounded-full border border-white/14 bg-white/10 px-4 py-1.5 text-sm text-indigo-50">
                    年级：{profileData.grade?.trim() || (showInitialSkeleton ? "整理中" : "待补充")}
                  </div>
                </div>
              </div>
            </div>

            <aside
              id="continue-desk"
              className="scroll-mt-28 rounded-[1.9rem] border border-white/70 bg-white/84 p-4 shadow-[0_22px_60px_rgba(148,163,184,0.18)] backdrop-blur-xl"
            >
              <div className="flex items-center justify-between gap-3">
                <div>
                  <h2 className="text-xl font-bold leading-tight text-slate-950">
                    {signedInToday ? "继续事项" : "今日签到卡"}
                  </h2>
                </div>
                <div className="flex items-center gap-2">
                  <div className="flex h-10 w-10 items-center justify-center rounded-2xl bg-indigo-50 text-indigo-600">
                    {signedInToday ? <Sparkles size={20} /> : <CalendarDays size={20} />}
                  </div>
                </div>
              </div>

              {!signedInToday ? (
                <div className="mt-4 rounded-[1.65rem] border border-indigo-100 bg-gradient-to-br from-indigo-50 via-white to-cyan-50 p-4">
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <div className="text-sm font-semibold text-indigo-600">签到状态</div>
                      <div className="mt-1 text-2xl font-black tracking-tight text-slate-900">
                        {showInitialSkeleton ? "正在准备签到信息" : checkinSummary}
                      </div>
                      <div className="mt-1.5 text-sm leading-6 text-slate-600">
                        {showInitialSkeleton
                          ? "正在为你更新今天的签到状态，稍后就会显示完整结果。"
                          : "先完成签到，把今天的成长行为正式点亮，再去处理任务和其他准备动作。"}
                      </div>
                    </div>
                    <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-[1.25rem] bg-white text-orange-500 shadow-sm">
                      <Flame size={20} />
                    </div>
                  </div>

                  <div className="mt-4 h-2 overflow-hidden rounded-full bg-indigo-100">
                    <div
                      className={joinClasses(
                        "h-full rounded-full bg-gradient-to-r from-amber-400 to-orange-500 transition-all",
                        showInitialSkeleton && "w-[34%] motion-safe:animate-pulse",
                      )}
                      style={showInitialSkeleton ? undefined : { width: `${Math.max(checkinProgress.progressValue * 100, 0)}%` }}
                    />
                  </div>
                  <div className="mt-2.5 text-sm font-semibold text-slate-800">
                    {showInitialSkeleton ? "签到进度整理中" : checkinProgress.headline}
                  </div>
                  <div className="mt-1 text-sm leading-6 text-slate-500">
                    {showInitialSkeleton ? "连签奖励和本月签到记录马上就会补上。" : checkinProgress.detail}
                  </div>

                  <button
                    type="button"
                    onClick={openCalendarOverlay}
                    disabled={showInitialSkeleton}
                    className={joinClasses(
                      "mt-4 inline-flex w-full items-center justify-center rounded-2xl px-5 py-3 text-sm font-semibold transition-all disabled:cursor-not-allowed disabled:border disabled:border-slate-200 disabled:bg-white/70 disabled:text-slate-400 disabled:shadow-none",
                      "bg-gradient-to-r from-amber-400 to-orange-500 text-white shadow-[0_18px_35px_rgba(251,146,60,0.26)] hover:-translate-y-0.5 hover:shadow-[0_24px_38px_rgba(251,146,60,0.32)]",
                    )}
                  >
                    {showInitialSkeleton ? "稍等片刻" : "去完成今日签到"}
                  </button>
                </div>
              ) : (
                <div className="mt-4 space-y-3">
                  {continueState.error ? (
                    <div className="flex items-start gap-3 rounded-[1.4rem] border border-amber-200 bg-amber-50 px-4 py-4 text-sm text-amber-800">
                      <AlertCircle size={18} className="mt-0.5 shrink-0" />
                      <div>{continueState.error}</div>
                    </div>
                  ) : null}

                  <div className="flex items-center justify-between gap-3 text-sm text-slate-500">
                    <div className="flex items-center gap-2">
                      {continueDeskEntries.map((entry, index) => (
                        <button
                          key={entry.sectionLabel}
                          type="button"
                          onClick={() => setContinueDeskIndex(index)}
                          className={joinClasses(
                            "h-2.5 rounded-full transition-all",
                            index === continueDeskIndex ? "w-8 bg-indigo-600" : "w-2.5 bg-slate-300 hover:bg-slate-400",
                          )}
                          aria-label={`切换到${entry.sectionLabel}`}
                        />
                      ))}
                    </div>
                    <div className="flex items-center gap-2">
                      <span className="tabular-nums">
                        {continueDeskIndex + 1} / {continueDeskEntries.length}
                      </span>
                      <button
                        type="button"
                        onClick={() => setContinueDeskIndex((current) => (current - 1 + continueDeskEntries.length) % continueDeskEntries.length)}
                        className="inline-flex h-8 w-8 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-500 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600"
                        aria-label="查看上一张继续事项"
                      >
                        <ChevronLeft size={15} />
                      </button>
                      <button
                        type="button"
                        onClick={() => setContinueDeskIndex((current) => (current + 1) % continueDeskEntries.length)}
                        className="inline-flex h-8 w-8 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-500 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600"
                        aria-label="查看下一张继续事项"
                      >
                        <ChevronRight size={15} />
                      </button>
                    </div>
                  </div>

                  <AnimatePresence mode="wait">
                    <motion.div
                      key={activeContinueDeskEntry.sectionLabel}
                      initial={{ opacity: 0, x: 18 }}
                      animate={{ opacity: 1, x: 0 }}
                      exit={{ opacity: 0, x: -18 }}
                      transition={{ duration: 0.22 }}
                    >
                      <ContinueDeskCard
                        sectionLabel={activeContinueDeskEntry.sectionLabel}
                        title={activeContinueDeskEntry.title}
                        description={activeContinueDeskEntry.description}
                        meta={activeContinueDeskEntry.meta}
                        href={activeContinueDeskEntry.href}
                        actionLabel={activeContinueDeskEntry.actionLabel}
                        icon={activeContinueDeskEntry.icon}
                        glowClassName={activeContinueDeskEntry.glowClassName}
                        buttonClassName={activeContinueDeskEntry.buttonClassName}
                        iconClassName={activeContinueDeskEntry.iconClassName}
                      />
                    </motion.div>
                  </AnimatePresence>
                </div>
              )}
            </aside>
          </motion.section>

          <div className="grid gap-8 lg:grid-cols-12">
            <div className="space-y-8 lg:col-span-8">
              <motion.section
                id="task-board"
                variants={itemVariants}
                className="relative scroll-mt-28 overflow-hidden rounded-[2rem] border-2 border-slate-200 bg-[#FAF9F6] px-6 py-8 shadow-[0_20px_55px_rgba(148,163,184,0.14)] sm:px-10"
              >
                <div className="absolute inset-0 opacity-20 [background-image:repeating-linear-gradient(transparent,transparent_39px,#94a3b8_39px,#94a3b8_40px)] [background-position-y:56px]" />
                <div className="absolute left-1/2 top-0 z-20 flex w-40 -translate-x-1/2 justify-center drop-shadow-md">
                  <svg width="140" height="45" viewBox="0 0 140 45" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <rect x="20" y="5" width="100" height="25" rx="4" fill="#e2e8f0" stroke="#94a3b8" strokeWidth="2" />
                    <rect x="35" y="-5" width="70" height="15" rx="3" fill="#cbd5e1" stroke="#94a3b8" strokeWidth="2" />
                    <circle cx="50" cy="10" r="3" fill="#64748b" />
                    <circle cx="90" cy="10" r="3" fill="#64748b" />
                    <line x1="25" y1="25" x2="115" y2="25" stroke="#94a3b8" strokeWidth="2" strokeLinecap="round" />
                  </svg>
                </div>

                <div className="relative z-10">
                  <div className="mt-2 flex flex-col gap-3 border-b-2 border-dashed border-slate-300 pb-5 sm:flex-row sm:items-end sm:justify-between">
                    <div>
                      <h2 className="text-2xl font-black tracking-tight text-slate-900">任务公告板</h2>
                      <p className="mt-1.5 text-sm font-medium text-slate-500">
                        把今天想推进的事一张张记下来，按自己的节奏完成就好。
                      </p>
                    </div>

                    <div className="ml-auto min-w-[10.5rem] rounded-[1.4rem] border-2 border-slate-200 bg-white px-4 py-2.5 text-right shadow-sm">
                      <div className="text-sm font-semibold text-slate-500">积分余额</div>
                      {boardBalanceValue !== null ? (
                        <div className="mt-1 text-2xl font-black tabular-nums tracking-tight text-slate-900">
                          <AnimatedCount
                            value={boardBalanceValue}
                            formatter={(nextValue) => formatCount(nextValue)}
                          />
                        </div>
                      ) : (
                        <div className="mt-1 text-2xl font-black tabular-nums tracking-tight text-slate-400">稍后更新</div>
                      )}
                    </div>
                  </div>

                  {growthError && !showInitialSkeleton ? (
                    <div className="mt-6 flex items-start gap-3 rounded-[1.5rem] border border-amber-200 bg-amber-50 px-4 py-4 text-sm text-amber-800">
                      <AlertCircle size={18} className="mt-0.5 shrink-0" />
                      <div className="min-w-0">
                        <div className="font-semibold">成长数据同步不完整</div>
                        <div className="mt-1 leading-7">{growthError}</div>
                      </div>
                      <button
                        type="button"
                        onClick={() => setRefreshKey((value) => value + 1)}
                        className="ml-auto inline-flex shrink-0 items-center rounded-full border border-amber-300 bg-white px-3 py-1.5 text-xs font-semibold text-amber-700 transition-colors hover:bg-amber-100"
                      >
                        重试
                      </button>
                    </div>
                  ) : null}

                  <div className="mt-5 grid gap-5 sm:grid-cols-2">
                    {showInitialSkeleton ? (
                      <>
                        <DashboardTaskSkeletonNote angle={-2} noteClassName="border-slate-200/90 bg-white/92 shadow-[0_22px_48px_rgba(148,163,184,0.14)]" />
                        <DashboardTaskSkeletonNote angle={1.5} noteClassName="border-stone-200/90 bg-stone-50/92 shadow-[0_22px_48px_rgba(148,163,184,0.14)]" />
                        <DashboardTaskSkeletonNote angle={-1.5} noteClassName="border-slate-200/90 bg-slate-50/92 shadow-[0_22px_48px_rgba(148,163,184,0.14)]" />
                        <DashboardTaskSkeletonNote angle={1} noteClassName="border-blue-100/80 bg-white/92 shadow-[0_22px_48px_rgba(148,163,184,0.14)]" />
                      </>
                    ) : null}
                    {boardTasks.map((task, index) => {
                      const visual = getTaskVisual(task.taskCode);
                      const TaskIcon = visual.icon;
                      const noteAngles = [-2, 1.5, -1.5, 1];
                      const angle = noteAngles[index % noteAngles.length];
                      const noteInteractive = !task.completed;

                      return (
                        <motion.article
                          key={task.taskId}
                          initial={{ rotate: angle }}
                          animate={{ rotate: angle }}
                          whileHover={noteInteractive ? getTaskNoteHoverMotion(false) : undefined}
                          transition={{ type: "spring", stiffness: 500, damping: 25 }}
                          className={joinClasses(
                            "relative flex min-h-[176px] flex-col justify-between rounded-bl-md rounded-br-[1.8rem] rounded-tl-sm rounded-tr-md border px-5 py-4 [transform-origin:center_top] will-change-transform",
                            noteInteractive ? "group cursor-pointer" : "cursor-default",
                            task.completed ? visual.doneNoteClassName : visual.noteClassName,
                          )}
                        >
                          <StickyNotePin />
                          {task.completed ? (
                            <div className="pointer-events-none absolute inset-0 z-0 rounded-bl-md rounded-br-[1.8rem] rounded-tl-sm rounded-tr-md bg-white/60" />
                          ) : null}

                          <div className="relative z-10 mt-1.5 flex items-start justify-between gap-3">
                            <div className={joinClasses("flex h-11 w-11 items-center justify-center rounded-[0.95rem] shadow-sm transition-transform duration-300 group-hover:-translate-y-0.5 group-hover:rotate-3", task.completed ? visual.doneIconClassName : visual.iconClassName)}>
                              <TaskIcon size={19} />
                            </div>
                            <div
                              className={joinClasses(
                                "inline-flex h-9 shrink-0 items-center gap-1.5 rounded-full border px-3.5 text-xs font-bold shadow-sm transition-transform duration-300 group-hover:-translate-y-0.5",
                                task.completed
                                  ? "border-emerald-200/90 bg-white/92 text-emerald-700"
                                  : "border-white/80 bg-white/88 text-slate-500",
                              )}
                            >
                              {task.completed ? (
                                <Check size={17} strokeWidth={4} />
                              ) : (
                                <Sparkles size={15} />
                              )}
                              <span>{task.completed ? "已完成" : "进行中"}</span>
                            </div>
                          </div>

                          <div className="relative z-10 mt-4">
                            <div
                              className={joinClasses(
                                "text-sm font-bold text-slate-500",
                                task.completed && "text-slate-400/95",
                              )}
                            >
                              {task.completed ? "已完成便签" : getReasonLabel(task.taskCode)}
                            </div>
                            <h3
                              className={joinClasses(
                                "mt-2 text-xl font-black leading-snug tracking-tight text-slate-900",
                                task.completed && "text-slate-400 line-through decoration-slate-400/90 decoration-[1.5px]",
                              )}
                            >
                              {task.title}
                            </h3>
                            <p
                              className={joinClasses(
                                "mt-2.5 min-h-[48px] text-[15px] leading-6 text-slate-600",
                                task.completed && "text-slate-400/90 line-through decoration-slate-300/90 decoration-[1.5px]",
                              )}
                            >
                              {task.description?.trim() || "完成这项真实动作后，工作台会自动同步最新进度。"}
                            </p>
                          </div>

                          <div className="relative z-10 mt-5 flex items-center justify-between gap-3">
                            <div
                              className={joinClasses(
                                "rounded-xl border px-3 py-1.5 text-[15px] font-black shadow-sm transition-transform duration-300 group-hover:-translate-y-0.5",
                                task.completed ? visual.doneBadgeClassName : visual.badgeClassName,
                              )}
                            >
                              +{formatCount(task.points)} 积分
                            </div>
                            {task.completed ? (
                              <div className="inline-flex items-center rounded-full border border-white/80 bg-white/92 px-3 py-1.5 text-sm font-semibold text-slate-500 shadow-sm transition-transform duration-300 group-hover:-translate-y-0.5">
                                <CheckCircle2 size={15} className="mr-1.5 text-emerald-500" />
                                已完成归档
                              </div>
                            ) : (
                              <div className="text-sm font-semibold text-slate-500 transition-transform duration-300 group-hover:-translate-y-0.5">
                                等待完成
                              </div>
                            )}
                          </div>

                          <div className="pointer-events-none absolute bottom-0 right-0 h-5 w-5 rounded-br-[1.8rem] rounded-tl-xl bg-gradient-to-tl from-black/5 to-transparent transition-opacity duration-300 group-hover:from-black/10" />
                        </motion.article>
                      );
                    })}

                    {Array.from({ length: taskBoardPlaceholderCount }).map((_, index) => {
                      const noteAngles = [1.2, -1, 0.8, -1.3];
                      const angle = noteAngles[index % noteAngles.length];

                      return (
                        <motion.article
                          key={`placeholder-note-${index}`}
                          initial={{ rotate: angle }}
                          animate={{ rotate: angle }}
                          whileHover={undefined}
                          transition={{ type: "spring", stiffness: 500, damping: 25 }}
                          className="relative flex min-h-[176px] flex-col justify-between rounded-bl-md rounded-br-[1.8rem] rounded-tl-sm rounded-tr-md border border-dashed border-slate-300 bg-white/80 px-5 py-4 shadow-[0_20px_42px_rgba(148,163,184,0.14)] [transform-origin:center_top] will-change-transform"
                        >
                          <StickyNotePin />
                          <div className="mt-1.5">
                            <div className="text-sm font-bold text-slate-400">空白便签</div>
                            <div className="mt-3.5 space-y-2.5">
                              <div className="h-4 w-2/3 rounded-full bg-slate-200/80" />
                              <div className="h-3.5 w-full rounded-full bg-slate-200/70" />
                              <div className="h-3.5 w-5/6 rounded-full bg-slate-200/60" />
                              <div className="h-3.5 w-3/5 rounded-full bg-slate-200/50" />
                            </div>
                          </div>
                          <div className="mt-5 flex items-center justify-between gap-3">
                            <div className="rounded-xl border border-slate-200 bg-white px-3 py-1.5 text-[15px] font-semibold text-slate-400 shadow-sm">
                              预留位置
                            </div>
                            <div className="text-sm font-semibold text-slate-400">
                              即将补位
                            </div>
                          </div>
                          <div className="pointer-events-none absolute bottom-0 right-0 h-5 w-5 rounded-br-[1.8rem] rounded-tl-xl bg-gradient-to-tl from-black/5 to-transparent" />
                        </motion.article>
                      );
                    })}
                  </div>
                </div>
              </motion.section>

              <motion.section
                id="growth-cabin"
                variants={itemVariants}
                className="scroll-mt-28 rounded-[2rem] border border-white/80 bg-white/84 p-6 shadow-[0_22px_60px_rgba(148,163,184,0.18)] backdrop-blur-xl"
              >
                <div className="mb-5 border-b border-slate-100 pb-5">
                  <h2 className="text-2xl font-bold text-slate-950">核心成长舱</h2>
                  <p className="mt-2 text-sm leading-6 text-slate-500">
                    把常用功能集中收在这里，方便你顺手继续下一步。
                  </p>
                </div>

                <div className="grid gap-4 md:grid-cols-2">
                  {quickEntries.map((entry) => {
                    const EntryIcon = entry.icon;

                    return (
                      <GrowthCabinCard
                        key={entry.title}
                        sectionLabel={entry.sectionLabel}
                        title={entry.title}
                        description={entry.description}
                        meta={entry.meta}
                        icon={<EntryIcon size={22} className={entry.disabled ? "text-slate-400" : "text-slate-700"} />}
                        actionDisabled={entry.disabled}
                        glowClassName={entry.glowClassName}
                        action={entry.disabled ? (
                          <div className="inline-flex items-center rounded-full border border-slate-200 bg-slate-100 px-4 py-2.5 text-sm font-semibold text-slate-500">
                            <Lock size={15} className="mr-2" />
                            {entry.actionLabel}
                          </div>
                        ) : (
                          <Link
                            to={entry.href || STUDENT_DASHBOARD_ROUTE}
                            className={joinClasses(
                              "group/entry relative inline-flex items-center overflow-hidden rounded-full border px-4 py-2.5 text-sm font-semibold transition-all duration-300 hover:-translate-y-1",
                              entry.buttonClassName,
                            )}
                          >
                            <span className="pointer-events-none absolute right-4 top-1/2 h-2 w-2 -translate-y-1/2 rounded-full bg-current opacity-20 transition-all duration-500 group-hover/entry:scale-[12] group-hover/entry:opacity-[0.08]" />
                            <span className="relative">{entry.actionLabel}</span>
                            <ArrowRight size={16} className="ml-2 transition-transform duration-300 group-hover/entry:translate-x-1.5" />
                          </Link>
                        )}
                      />
                    );
                  })}
                </div>
              </motion.section>
            </div>

            <div className="space-y-8 lg:col-span-4">
              <motion.aside
                id="portrait-snapshot"
                variants={itemVariants}
                className="scroll-mt-28 rounded-[2rem] border border-white/70 bg-white/84 p-6 shadow-[0_22px_60px_rgba(148,163,184,0.18)] backdrop-blur-xl"
              >
                <div className="flex items-center justify-between">
                  <div>
                    <h2 className="text-xl font-bold leading-tight text-slate-950">你的职场画像</h2>
                  </div>
                  <div className="rounded-full bg-slate-50 px-3 py-1.5 text-xs font-semibold text-slate-500">
                    {showInitialSkeleton ? "画像整理中" : portraitUpdatedLabel}
                  </div>
                </div>

                {showCompletionCard || showMissingProfileCard ? (
                  <div className="mt-6 space-y-4">
                    {showCompletionCard ? (
                      <div className="rounded-[1.8rem] border border-slate-100 bg-slate-50/70 p-5">
                        <div className="flex items-end justify-between gap-4">
                          <div>
                            <div className="text-sm font-bold text-slate-500">资料完整度</div>
                            <div className="mt-1 text-xs leading-6 text-slate-500">
                              先看一眼当前进度，资料越完整，你后面拿到的建议也会越贴合自己。
                            </div>
                          </div>
                          <div className="text-3xl font-black text-indigo-600 tabular-nums">
                            <AnimatedCount value={completionDisplayValue} formatter={(nextValue) => `${nextValue}%`} />
                          </div>
                        </div>
                        <div className="mt-4 h-3 overflow-hidden rounded-full bg-slate-200/80 p-0.5">
                          <div
                            className="relative h-full rounded-full bg-gradient-to-r from-indigo-400 to-indigo-600 transition-all duration-700"
                            style={{ width: `${completionProgressWidth}%` }}
                          >
                            <div className="absolute inset-0 bg-[linear-gradient(45deg,rgba(255,255,255,.18)_25%,transparent_25%,transparent_50%,rgba(255,255,255,.18)_50%,rgba(255,255,255,.18)_75%,transparent_75%,transparent)] bg-[length:1rem_1rem]" />
                          </div>
                        </div>
                      </div>
                    ) : null}

                    {showMissingProfileCard ? (
                      <div className="rounded-[1.5rem] border border-slate-200 bg-slate-50/80 p-5">
                        <div className="flex items-center justify-between gap-4">
                          <div>
                            <div className="text-sm font-semibold text-slate-500">待补信息</div>
                            <div className="mt-1.5 text-sm font-semibold text-slate-800">
                              {showInitialSkeleton ? "稍等一下，这里会告诉你接下来先补什么" : `还有 ${missingProfileItems.length} 项基础信息待补齐`}
                            </div>
                          </div>
                          <ShieldCheck size={18} className="text-indigo-500" />
                        </div>
                        {showInitialSkeleton ? null : (
                          <div className="mt-4">
                            <div className="flex flex-wrap gap-2">
                              {visibleMissingProfileItems.map((item) => (
                                <div key={item.label} className="rounded-full border border-slate-200 bg-white px-3 py-2 text-xs font-medium text-slate-600">
                                  {item.label}
                                </div>
                              ))}
                              {hiddenMissingProfileCount > 0 ? (
                                <div className="rounded-full border border-dashed border-slate-300 bg-white/70 px-3 py-2 text-xs font-medium text-slate-500">
                                  其余 {formatCount(hiddenMissingProfileCount)} 项到资料中心继续补齐
                                </div>
                              ) : null}
                              <Link
                                to="/profile"
                                className="inline-flex items-center gap-2 rounded-full border border-indigo-200 bg-white px-4 py-2 text-xs font-semibold text-indigo-600 transition-all duration-300 hover:-translate-y-0.5 hover:border-indigo-300 hover:bg-indigo-50 hover:text-indigo-700"
                              >
                                去补充资料
                                <ArrowRight size={14} />
                              </Link>
                            </div>
                          </div>
                        )}
                      </div>
                    ) : null}
                  </div>
                ) : null}

                <div className="mt-6 grid grid-cols-2 gap-4">
                  {[
                    {
                      label: "已掌握技能",
                      value: masteredSkillsValue,
                    },
                    {
                      label: "学习中技能",
                      value: learningSkillsValue,
                    },
                    {
                      label: "本周社区分",
                      value: communityScoreValue,
                    },
                    {
                      label: "7 天面试信号",
                      value: interviewSignalValue,
                    },
                  ].map((item) => (
                    <div key={item.label} className="rounded-[1.4rem] border border-slate-200 bg-slate-50 px-4 py-4 text-center">
                      <div className="text-3xl font-black text-slate-800 tabular-nums">
                        <AnimatedCount value={item.value} formatter={(nextValue) => formatCount(nextValue)} />
                      </div>
                      <div className="mt-1 text-xs font-bold text-slate-500">{item.label}</div>
                    </div>
                  ))}
                </div>

                <div className="mt-6 rounded-[1.5rem] border border-dashed border-slate-200 bg-slate-50/80 p-5">
                  <div className="text-sm font-bold text-slate-500">AI 自动提炼标签</div>
                  <div className="mt-3 text-sm leading-7 text-slate-500">
                    这里会陆续整理出更能代表你的关键词，帮你更快看清自己的优势和成长方向。
                  </div>
                </div>
              </motion.aside>

              <motion.section
                variants={itemVariants}
                className="rounded-[2rem] border border-white/70 bg-white/84 p-6 shadow-[0_22px_60px_rgba(148,163,184,0.18)] backdrop-blur-xl"
              >
                <div className="flex items-center justify-between gap-4">
                  <div>
                    <h2 className="text-xl font-bold leading-tight text-slate-950">成长足迹</h2>
                  </div>
                  <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-slate-100 text-slate-700">
                    <TrendingUp size={20} />
                  </div>
                </div>

                <div className="mt-6 space-y-4">
                  {showInitialSkeleton ? (
                    [0, 1, 2, 3].map((index) => (
                      <div key={`footprint-skeleton-${index}`} className="flex flex-col">
                        <div
                          className={joinClasses(
                            "rounded-2xl px-4 py-4 shadow-sm",
                            index % 2 === 0 ? "mr-6 rounded-tl-sm bg-indigo-50" : "ml-6 rounded-tr-sm bg-slate-50",
                          )}
                        >
                          <SkeletonBlock className="h-4 w-full rounded-full" />
                          <SkeletonBlock className="mt-2 h-4 w-4/5 rounded-full" />
                          <SkeletonBlock className="mt-3 h-3 w-20 rounded-full" />
                        </div>
                      </div>
                    ))
                  ) : (
                    footprintItems.map((item, index) => (
                      <div key={item.id} className="flex flex-col">
                        <div
                          className={joinClasses(
                            "rounded-2xl px-4 py-4 text-sm font-medium leading-7 shadow-sm",
                            index % 2 === 0
                              ? "mr-6 rounded-tl-sm bg-indigo-50 text-indigo-900"
                              : "ml-6 rounded-tr-sm bg-slate-50 text-slate-700",
                          )}
                        >
                          {item.content}
                          <span className="mt-2 block text-xs font-semibold text-slate-400">
                            {item.meta}
                          </span>
                        </div>
                      </div>
                    ))
                  )}
                </div>

                <div className="mt-6 rounded-[1.5rem] border border-slate-200 bg-slate-50/85 p-5">
                  <div className="text-sm font-semibold text-slate-500">下一步建议</div>
                  {showInitialSkeleton ? (
                    <>
                      <SkeletonBlock className="mt-2 h-6 w-3/4 rounded-xl" />
                      <div className="mt-3 space-y-2">
                        <SkeletonBlock className="h-4 w-full rounded-full" />
                        <SkeletonBlock className="h-4 w-5/6 rounded-full" />
                      </div>
                    </>
                  ) : (
                    <>
                      <div className="mt-2 text-lg font-semibold text-slate-900">{action?.title}</div>
                      <p className="mt-3 text-sm leading-7 text-slate-600">{action?.description}</p>
                    </>
                  )}
                </div>
              </motion.section>
            </div>
          </div>
        </motion.div>
      </main>

      {celebrationOverlayLoaded ? (
        <Suspense fallback={reward ? <DashboardOverlayFallback /> : null}>
          <DashboardOverlay
            kind="celebration"
            reward={reward}
            onClose={closeCelebrationOverlay}
          />
        </Suspense>
      ) : null}

      {calendarOverlayLoaded ? (
        <Suspense fallback={calendarOpen ? <DashboardOverlayFallback /> : null}>
          <DashboardOverlay
            kind="calendar"
            open={calendarOpen}
            hasOverview={Boolean(checkinOverview)}
            progress={checkinProgress}
            calendarCells={checkinCalendarCells}
            rewardRules={checkinOverview?.rewardRules ?? []}
            currentStreak={currentStreak}
            signedInToday={signedInToday}
            monthLabel={checkinMonthLabel}
            basePoints={checkinOverview?.basePointsPerDay ?? 10}
            submitting={checkinSubmitting}
            onClose={closeCalendarOverlay}
            onCheckin={() => void handleCheckin()}
          />
        </Suspense>
      ) : null}
    </div>
  );
}
