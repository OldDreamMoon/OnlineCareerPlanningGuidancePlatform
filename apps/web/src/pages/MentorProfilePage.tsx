import { AnimatePresence, motion } from "framer-motion";
import {
  AlertCircle,
  ArrowLeft,
  BellRing,
  BriefcaseBusiness,
  Building2,
  CalendarClock,
  CalendarDays,
  ChevronRight,
  Check,
  CheckCircle2,
  Clock3,
  CreditCard,
  Edit3,
  Eye,
  FileCheck,
  FileText,
  Heart,
  History,
  Info,
  KeyRound,
  MessageSquare,
  Plus,
  RefreshCw,
  ShieldCheck,
  Star,
  Tags,
  ThumbsUp,
  UploadCloud,
  User,
  X,
  type LucideIcon,
} from "lucide-react";
import { useEffect, useRef, useState, type ChangeEvent, type ReactNode } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import MentorIdentityAvatar from "../components/avatar/MentorIdentityAvatar";
import LazyProfileNotificationPreferencesPanel from "../components/notifications/LazyProfileNotificationPreferencesPanel";
import MentorAvatarEditor, { type MentorAvatarUploadResult } from "../components/profile/MentorAvatarEditor";
import WorkspacePageLoadingScreen from "../components/WorkspacePageLoadingScreen";
import WorkspaceRoleTopbar from "../components/workspace/WorkspaceRoleTopbar";
import { ApiClientError, apiRequest, isAbortError } from "../lib/apiClient";
import {
  formatCount,
  formatDateTime,
  formatMoneyFen,
  formatMonthDay,
  formatTime,
  toTimestamp,
} from "../lib/formatters";
import { buildMentorIdentityLine } from "../lib/mentorNames";
import {
  getMentorWorkspaceNavItems,
} from "../lib/workspaceNav";
import {
  buildWorkspaceSnapshotStorageKey,
  readWorkspaceSnapshot,
  writeWorkspaceSnapshot,
} from "../lib/workspaceSnapshot";
import { writeMentorIdentitySnapshot } from "../lib/mentorIdentity";

type TimeValue = number | string | null;

type SendCodeResponse = {
  sent: boolean;
  stage: string;
  targetEmail: string;
  deliveryChannel: string;
  expiresAt: TimeValue;
  nextSendAt: TimeValue;
  debugCode: string | null;
};

type VerifyCodeResponse = {
  verified: boolean;
  verificationToken: string;
  expiresAt: TimeValue;
};

type PageState = "loading" | "ready" | "error";
type TabKey = "profile" | "preview" | "certification" | "schedule" | "notifications";

type MentorServicePackage = {
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
};

type MentorProfileResponse = {
  userId: number;
  displayName: string;
  realName: string | null;
  showRealName: boolean;
  companyName: string | null;
  jobTitle: string | null;
  avatarUrl: string;
  avatarConfigured: boolean;
  avatarContentType: string | null;
  avatarUpdatedAt: TimeValue;
  expertiseTags: string[];
  serviceScenes: string[];
  bio: string | null;
  suitableFor: string | null;
  notSuitableFor: string | null;
  prepMaterials: string | null;
  replyRhythm: string | null;
  priceFen: number;
  packages: MentorServicePackage[];
  avgRating: number | null;
  totalOrders: number;
  available: boolean;
  approvalStatus: string;
};

type MentorProfileFormData = {
  displayName: string;
  realName: string;
  showRealName: boolean;
  companyName: string;
  jobTitle: string;
  expertiseTags: string[];
  serviceScenes: string[];
  bio: string;
  suitableFor: string;
  notSuitableFor: string;
  prepMaterials: string;
  replyRhythm: string;
  packages: MentorServicePackage[];
  priceFen: number;
  available: boolean;
};

type CertificationAsset = {
  assetId: number;
  originalFilename: string;
  contentType: string | null;
  sizeBytes: number | null;
  lifecycleStatus: string | null;
  uploadedAt: TimeValue;
};

type CertificationSubmission = {
  submissionId: number;
  userId: number;
  role: string;
  realName: string | null;
  companyName: string | null;
  jobTitle: string | null;
  status: string;
  current: boolean;
  reviewNote: string | null;
  previousSubmissionId: number | null;
  submittedAt: TimeValue;
  reviewedAt: TimeValue;
  assets: CertificationAsset[];
};

type CertificationOwnViewResponse = {
  userId: number;
  role: string;
  approvalStatus: string;
  currentSubmission: CertificationSubmission | null;
  submissions: CertificationSubmission[];
};

type MentorScheduleSlot = {
  id: number;
  mentorUserId: number;
  startAt: TimeValue;
  endAt: TimeValue;
  status: "AVAILABLE" | "BOOKED" | string;
  bookedOrderNo: string | null;
};

type MentorScheduleSlotListResponse = {
  records: MentorScheduleSlot[];
};

type MentorScheduleSlotBatchCreateResponse = {
  records: MentorScheduleSlot[];
  createdCount: number;
  skippedCount: number;
};

type MentorScheduleSlotBatchDeleteResponse = {
  matchedCount: number;
  deletedCount: number;
  lockedCount: number;
};

type ToastState = {
  tone: "success" | "error" | "info" | "warning" | "neutral" | "cancel";
  message: string;
};

type DebugCodeHint = {
  code: string;
  targetEmail: string;
};

type PasswordModalState = {
  isOpen: boolean;
  step: "verifyCode" | "changePassword";
  code: string;
  newPassword: string;
  passwordResetToken: string;
  busy: boolean;
  debugHint: DebugCodeHint | null;
};

type CertificationFormState = {
  realName: string;
  companyName: string;
  jobTitle: string;
  file: File | null;
  busy: boolean;
};

type ScheduleFormState = {
  date: string;
  time: string;
  durationMinutes: number;
  busy: boolean;
};

type RecurringScheduleMode = "CREATE" | "REPLACE" | "CLEAR";

type RecurringScheduleFormState = {
  startDate: string;
  time: string;
  durationMinutes: number;
  weeks: number;
  weekdays: number[];
  mode: RecurringScheduleMode;
  busy: boolean;
};

type SpecialDateScheduleFormState = {
  date: string;
  time: string;
  durationMinutes: number;
  clearBusy: boolean;
  addBusy: boolean;
};

type PreviewScenarioKey = "CURRENT" | "APPROVED_OPEN" | "APPROVED_PAUSED" | "PENDING_REVIEW" | "REJECTED_FIX";

type MentorProfileWorkspaceSnapshot = {
  profile: MentorProfileResponse;
  certification: CertificationOwnViewResponse | null;
  scheduleRecords: MentorScheduleSlot[];
};

const SERVICE_SCENE_OPTIONS = [
  "简历诊断",
  "项目表达",
  "模拟面试复盘",
  "岗位方向选择",
  "校招投递策略",
  "转行 / 跨专业求职",
  "Offer 对比与决策",
] as const;

const PROFILE_TABS: Array<{ id: TabKey; label: string; icon: LucideIcon }> = [
  { id: "profile", label: "资料与服务", icon: User },
  { id: "preview", label: "学生视角预览", icon: Eye },
  { id: "certification", label: "认证材料", icon: FileCheck },
  { id: "schedule", label: "排期管理", icon: CalendarDays },
  { id: "notifications", label: "通知提醒", icon: BellRing },
];

const DURATION_OPTIONS = [30, 45, 60, 90];
const RECURRING_WEEKS_OPTIONS = [2, 4, 6, 8];
const WEEKDAY_OPTIONS = [
  { value: 1, label: "周一" },
  { value: 2, label: "周二" },
  { value: 3, label: "周三" },
  { value: 4, label: "周四" },
  { value: 5, label: "周五" },
  { value: 6, label: "周六" },
  { value: 7, label: "周日" },
] as const;

const PREVIEW_SCENARIO_OPTIONS: Array<{ id: PreviewScenarioKey; label: string }> = [
  { id: "CURRENT", label: "当前真实状态" },
  { id: "APPROVED_OPEN", label: "已公开且可接单" },
  { id: "APPROVED_PAUSED", label: "已公开但暂停接单" },
  { id: "PENDING_REVIEW", label: "审核中预览" },
  { id: "REJECTED_FIX", label: "待补件不公开" },
];

const RECURRING_SCHEDULE_MODE_OPTIONS: Array<{ id: RecurringScheduleMode; label: string; description: string }> = [
  { id: "CREATE", label: "补充生成", description: "保留现有排期，仅把新规则对应的时段继续铺进去。" },
  { id: "REPLACE", label: "覆盖这些日期", description: "先清空这些日期里未被预约的时段，再按新规则重建。" },
  { id: "CLEAR", label: "清空这些日期", description: "仅清空这些日期里未被预约的时段，不再新建时段。" },
];

function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function resolveProfileTab(value: string | null): TabKey {
  return PROFILE_TABS.some((tab) => tab.id === value) ? (value as TabKey) : "profile";
}

function resolveServiceSceneCode(sceneLabel: string) {
  switch (sceneLabel) {
    case "项目表达":
      return "PROJECT_STORYTELLING";
    case "模拟面试复盘":
      return "MOCK_INTERVIEW_REVIEW";
    case "岗位方向选择":
      return "CAREER_DIRECTION";
    case "校招投递策略":
      return "CAMPUS_RECRUITMENT_STRATEGY";
    case "转行 / 跨专业求职":
      return "CAREER_TRANSITION";
    case "Offer 对比与决策":
      return "OFFER_DECISION";
    case "简历诊断":
    default:
      return "RESUME_DIAGNOSIS";
  }
}

function computeStartingPriceFen(packages: MentorServicePackage[], fallbackPriceFen = 0) {
  const enabledPrices = packages
    .filter((item) => item.enabled)
    .map((item) => item.priceFen)
    .filter((value) => Number.isFinite(value) && value >= 0);
  if (!enabledPrices.length) {
    return Math.max(0, fallbackPriceFen);
  }
  return Math.min(...enabledPrices);
}

function createPackageDraft(
  sortNo: number,
  sceneLabel: string = SERVICE_SCENE_OPTIONS[0],
  priceFen = 0,
): MentorServicePackage {
  return {
    id: sortNo,
    packageName: sortNo === 1 ? "标准图文咨询" : `服务套餐 ${sortNo}`,
    sceneCode: resolveServiceSceneCode(sceneLabel),
    sceneLabel,
    deliveryMode: "TEXT_ASYNC",
    durationMinutes: null,
    priceFen: Math.max(0, priceFen),
    description: sortNo === 1 ? "适合先梳理问题与材料，由导师给出正式建议和下一步行动方向。" : "",
    enabled: true,
    sortNo,
  };
}

function normalizeMentorPackages(
  packages: MentorServicePackage[] | null | undefined,
  fallbackPriceFen: number,
  serviceScenes: string[],
) {
  if (packages?.length) {
    return packages.map((item, index) => ({
      ...item,
      id: item.id ?? index + 1,
      sceneLabel: item.sceneLabel || serviceScenes[0] || SERVICE_SCENE_OPTIONS[0],
      sceneCode: item.sceneCode || resolveServiceSceneCode(item.sceneLabel || serviceScenes[0] || SERVICE_SCENE_OPTIONS[0]),
      deliveryMode: item.deliveryMode || "TEXT_ASYNC",
      durationMinutes: item.deliveryMode === "APPOINTMENT" ? item.durationMinutes ?? 45 : null,
      description: item.description ?? "",
      enabled: item.enabled ?? true,
      sortNo: item.sortNo ?? index + 1,
      priceFen: Math.max(0, item.priceFen ?? fallbackPriceFen),
    }));
  }
  return [
    createPackageDraft(
      1,
      serviceScenes[0] || SERVICE_SCENE_OPTIONS[0],
      Math.max(0, fallbackPriceFen),
    ),
  ];
}

function buildProfileFormData(profile: MentorProfileResponse): MentorProfileFormData {
  const packages = normalizeMentorPackages(profile.packages, profile.priceFen ?? 0, profile.serviceScenes ?? []);
  return {
    displayName: profile.displayName,
    realName: profile.realName ?? "",
    showRealName: profile.showRealName ?? false,
    companyName: profile.companyName ?? "",
    jobTitle: profile.jobTitle ?? "",
    expertiseTags: profile.expertiseTags ?? [],
    serviceScenes: profile.serviceScenes ?? [],
    bio: profile.bio ?? "",
    suitableFor: profile.suitableFor ?? "",
    notSuitableFor: profile.notSuitableFor ?? "",
    prepMaterials: profile.prepMaterials ?? "",
    replyRhythm: profile.replyRhythm ?? "",
    packages,
    priceFen: computeStartingPriceFen(packages, profile.priceFen ?? 0),
    available: profile.available,
  };
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

function getToastPresentation(tone: ToastState["tone"]) {
  if (tone === "success") {
    return {
      title: "保存成功",
      icon: CheckCircle2,
      shellClassName: "border border-emerald-100/90 bg-[#f2fcf7] ring-1 ring-emerald-100/90 shadow-[0_18px_40px_rgba(16,185,129,0.12)]",
      iconClassName: "bg-emerald-100 text-emerald-600",
      titleClassName: "text-emerald-700",
      messageClassName: "text-slate-600",
    };
  }

  if (tone === "info") {
    return {
      title: "提示",
      icon: Info,
      shellClassName: "border border-sky-100/90 bg-[#f3f9ff] ring-1 ring-sky-100/90 shadow-[0_18px_40px_rgba(96,165,250,0.12)]",
      iconClassName: "bg-sky-100 text-sky-600",
      titleClassName: "text-sky-700",
      messageClassName: "text-slate-600",
    };
  }

  if (tone === "warning") {
    return {
      title: "请先处理",
      icon: AlertCircle,
      shellClassName: "border border-amber-100/90 bg-[#fff9ef] ring-1 ring-amber-100/90 shadow-[0_18px_40px_rgba(245,158,11,0.14)]",
      iconClassName: "bg-amber-100 text-amber-600",
      titleClassName: "text-amber-700",
      messageClassName: "text-slate-600",
    };
  }

  if (tone === "neutral") {
    return {
      title: "已恢复",
      icon: X,
      shellClassName: "border border-slate-200/90 bg-[#f7f9fc] ring-1 ring-slate-200/90 shadow-[0_18px_40px_rgba(148,163,184,0.12)]",
      iconClassName: "bg-slate-200/80 text-slate-600",
      titleClassName: "text-slate-700",
      messageClassName: "text-slate-600",
    };
  }

  if (tone === "cancel") {
    return {
      title: "已取消",
      icon: X,
      shellClassName: "border border-rose-100/90 bg-[#fff3f5] ring-1 ring-rose-100/90 shadow-[0_18px_40px_rgba(244,114,182,0.12)]",
      iconClassName: "bg-rose-100 text-rose-600",
      titleClassName: "text-rose-700",
      messageClassName: "text-slate-600",
    };
  }

  return {
    title: "操作未完成",
    icon: AlertCircle,
    shellClassName: "border border-rose-100/90 bg-[#fff4f6] ring-1 ring-rose-100/90 shadow-[0_18px_40px_rgba(244,114,182,0.12)]",
    iconClassName: "bg-rose-100 text-rose-600",
    titleClassName: "text-rose-700",
    messageClassName: "text-slate-600",
  };
}

function getEditorBarPresentation(mode: "editing" | "pending") {
  if (mode === "pending") {
    return {
      icon: AlertCircle,
      shellClassName: "border border-amber-100/90 bg-[#fff9ef] shadow-[0_18px_42px_rgba(245,158,11,0.14)]",
      iconClassName: "bg-amber-100 text-amber-600",
      textClassName: "text-amber-900/90",
      secondaryButtonClassName: "border-amber-100 bg-white/92 text-amber-700 hover:border-amber-200 hover:bg-white hover:text-amber-800",
      primaryButtonClassName: "bg-amber-500 text-white hover:bg-amber-400",
    };
  }

  return {
    icon: Edit3,
    shellClassName: "border border-emerald-100/90 bg-[#f2fcf7] shadow-[0_18px_42px_rgba(16,185,129,0.14)]",
    iconClassName: "bg-emerald-100 text-emerald-600",
    textClassName: "text-slate-700",
    secondaryButtonClassName: "border-emerald-100 bg-white/92 text-emerald-700 hover:border-emerald-200 hover:bg-white hover:text-emerald-800",
    primaryButtonClassName: "bg-emerald-600 text-white hover:bg-emerald-500",
  };
}

function getAvatarLabel(name: string) {
  const normalized = name.trim();
  return normalized ? normalized.charAt(0).toUpperCase() : "导";
}

function formatPriceInput(priceFen: number) {
  return Number.isFinite(priceFen) ? String(Math.round(priceFen / 100)) : "0";
}

function parsePriceFen(value: string) {
  if (!value.trim()) {
    return 0;
  }
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed < 0) {
    return 0;
  }
  return Math.round(parsed * 100);
}

function getApprovalMeta(status: string | null | undefined) {
  switch (status) {
    case "APPROVED":
      return {
        label: "已认证导师",
        heroClassName: "border-emerald-200 bg-emerald-50 text-emerald-700",
        panelClassName: "border-emerald-100 bg-emerald-50/70",
        title: "资料已审核通过",
        description: "你的导师名片已经正式上线，学生现在可以按公开资料查看并预约咨询。",
      };
    case "REJECTED":
      return {
        label: "待补充材料",
        heroClassName: "border-rose-200 bg-rose-50 text-rose-700",
        panelClassName: "border-rose-100 bg-rose-50/70",
        title: "认证材料需要补充",
        description: "根据审核意见补充资料后再提交，审核通过后你的导师名片会恢复公开展示。",
      };
    default:
      return {
        label: "审核中",
        heroClassName: "border-amber-200 bg-amber-50 text-amber-700",
        panelClassName: "border-amber-100 bg-amber-50/70",
        title: "认证材料审核中",
        description: "平台正在核对你的导师信息，结果出来后会自动更新到这里。",
      };
  }
}

function sortScheduleRecords(records: MentorScheduleSlot[]) {
  return [...records].sort((left, right) => toTimestamp(left.startAt) - toTimestamp(right.startAt));
}

function formatScheduleCardTime(startAt: TimeValue) {
  const fallback = typeof startAt === "string" ? startAt : "—";
  return `${formatMonthDay(startAt, fallback)} ${formatTime(startAt, fallback)}`;
}

function formatDurationMinutes(startAt: TimeValue, endAt: TimeValue) {
  const start = toTimestamp(startAt);
  const end = toTimestamp(endAt);
  if (Number.isNaN(start) || Number.isNaN(end) || end <= start) {
    return 0;
  }
  return Math.round((end - start) / 60000);
}

function formatFileSize(sizeBytes: number | null | undefined) {
  if (!sizeBytes || sizeBytes <= 0) {
    return "—";
  }
  if (sizeBytes < 1024) {
    return `${sizeBytes} B`;
  }
  if (sizeBytes < 1024 * 1024) {
    return `${(sizeBytes / 1024).toFixed(1)} KB`;
  }
  return `${(sizeBytes / (1024 * 1024)).toFixed(1)} MB`;
}

function formatAssetLifecycleStatus(status: string | null | undefined) {
  switch (status) {
    case "ACTIVE":
      return "当前材料";
    case "ARCHIVED":
      return "历史材料";
    case "DELETED":
      return "已移除";
    default:
      return "已上传";
  }
}

function formatScheduleStatusLabel(status: string) {
  if (status === "AVAILABLE") {
    return "可预约";
  }

  if (status === "BOOKED") {
    return "已预约";
  }

  return "待确认";
}

function createDefaultScheduleForm(durationMinutes = 45): ScheduleFormState {
  const next = new Date();
  next.setDate(next.getDate() + 1);
  next.setHours(20, 0, 0, 0);
  const date = formatDateInputValue(next);
  const time = `${String(next.getHours()).padStart(2, "0")}:${String(next.getMinutes()).padStart(2, "0")}`;
  return {
    date,
    time,
    durationMinutes,
    busy: false,
  };
}

function createDefaultRecurringScheduleForm(durationMinutes = 45): RecurringScheduleFormState {
  const next = new Date();
  next.setDate(next.getDate() + 1);
  next.setHours(20, 0, 0, 0);
  const date = formatDateInputValue(next);
  const time = `${String(next.getHours()).padStart(2, "0")}:${String(next.getMinutes()).padStart(2, "0")}`;
  const weekday = next.getDay() === 0 ? 7 : next.getDay();
  return {
    startDate: date,
    time,
    durationMinutes,
    weeks: 4,
    weekdays: [weekday],
    mode: "CREATE",
    busy: false,
  };
}

function createDefaultSpecialDateScheduleForm(durationMinutes = 45): SpecialDateScheduleFormState {
  const next = new Date();
  next.setDate(next.getDate() + 1);
  next.setHours(20, 0, 0, 0);
  return {
    date: formatDateInputValue(next),
    time: `${String(next.getHours()).padStart(2, "0")}:${String(next.getMinutes()).padStart(2, "0")}`,
    durationMinutes,
    clearBusy: false,
    addBusy: false,
  };
}

function formatDateInputValue(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function toIsoRange(date: string, time: string, durationMinutes: number) {
  const start = new Date(`${date}T${time}:00`);
  if (Number.isNaN(start.getTime())) {
    return null;
  }
  const end = new Date(start.getTime() + durationMinutes * 60_000);
  return {
    startAt: start.toISOString(),
    endAt: end.toISOString(),
  };
}

function createDayRange(date: string) {
  const start = new Date(`${date}T00:00:00`);
  if (Number.isNaN(start.getTime())) {
    return null;
  }
  const end = new Date(start);
  end.setDate(end.getDate() + 1);
  return {
    startAt: start.toISOString(),
    endAt: end.toISOString(),
  };
}

function buildRecurringScheduleDays(form: RecurringScheduleFormState) {
  const base = new Date(`${form.startDate}T${form.time}:00`);
  if (Number.isNaN(base.getTime())) {
    return [];
  }
  const baseWeekday = base.getDay() === 0 ? 7 : base.getDay();
  const midnightBase = new Date(base);
  midnightBase.setHours(0, 0, 0, 0);
  const days: Date[] = [];
  const dayKeys = new Set<string>();

  for (let weekOffset = 0; weekOffset < form.weeks; weekOffset += 1) {
    form.weekdays.forEach((weekday) => {
      const offsetDays = weekday - baseWeekday + weekOffset * 7;
      const nextDay = new Date(midnightBase);
      nextDay.setDate(midnightBase.getDate() + offsetDays);
      nextDay.setHours(0, 0, 0, 0);
      const nextDayEnd = new Date(nextDay);
      nextDayEnd.setDate(nextDay.getDate() + 1);
      const dayKey = formatDateInputValue(nextDay);
      if (nextDayEnd.getTime() > Date.now() && !dayKeys.has(dayKey)) {
        dayKeys.add(dayKey);
        days.push(nextDay);
      }
    });
  }

  return days.sort((left, right) => left.getTime() - right.getTime());
}

function buildRecurringScheduleRanges(form: RecurringScheduleFormState) {
  return buildRecurringScheduleDays(form)
    .map((day) => toIsoRange(formatDateInputValue(day), form.time, form.durationMinutes))
    .filter((item): item is { startAt: string; endAt: string } => Boolean(item));
}

function buildRecurringScheduleDayRanges(form: RecurringScheduleFormState) {
  return buildRecurringScheduleDays(form)
    .map((day) => createDayRange(formatDateInputValue(day)))
    .filter((item): item is { startAt: string; endAt: string } => Boolean(item));
}

function isSlotInsideAnyRange(slot: MentorScheduleSlot, ranges: Array<{ startAt: string; endAt: string }>) {
  const slotStart = toTimestamp(slot.startAt);
  const slotEnd = toTimestamp(slot.endAt);
  if (!Number.isFinite(slotStart) || !Number.isFinite(slotEnd)) {
    return false;
  }
  return ranges.some((range) => {
    const rangeStart = toTimestamp(range.startAt);
    const rangeEnd = toTimestamp(range.endAt);
    return Number.isFinite(rangeStart)
      && Number.isFinite(rangeEnd)
      && slotStart >= rangeStart
      && slotEnd <= rangeEnd;
  });
}

function stripAvailableSlotsInRanges(
  records: MentorScheduleSlot[],
  ranges: Array<{ startAt: string; endAt: string }>,
) {
  return records.filter((slot) => slot.status !== "AVAILABLE" || !isSlotInsideAnyRange(slot, ranges));
}

function getScheduleDateKey(value: TimeValue) {
  const timestamp = toTimestamp(value);
  if (!Number.isFinite(timestamp)) {
    return "";
  }
  return formatDateInputValue(new Date(timestamp));
}

function createCertificationForm(profile: MentorProfileResponse, certification: CertificationOwnViewResponse | null): CertificationFormState {
  const currentSubmission = certification?.currentSubmission;
  return {
    realName: profile.realName ?? currentSubmission?.realName ?? "",
    companyName: profile.companyName ?? currentSubmission?.companyName ?? "",
    jobTitle: profile.jobTitle ?? currentSubmission?.jobTitle ?? "",
    file: null,
    busy: false,
  };
}

function calculateCountdown(nextSendAt: TimeValue | null | undefined) {
  if (!nextSendAt) {
    return 60;
  }

  const seconds = Math.ceil((toTimestamp(nextSendAt) - Date.now()) / 1000);
  return Math.max(seconds, 0);
}

function createPasswordModalState(overrides?: Partial<PasswordModalState>): PasswordModalState {
  return {
    isOpen: false,
    step: "verifyCode",
    code: "",
    newPassword: "",
    passwordResetToken: "",
    busy: false,
    debugHint: null,
    ...overrides,
  };
}

export default function MentorProfilePage() {
  const {
    role,
    displayName: authDisplayName,
    userId,
    email,
    refreshProfile: refreshAuthProfile,
  } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();

  const [pageState, setPageState] = useState<PageState>("loading");
  const [activeTab, setActiveTab] = useState<TabKey>(() => resolveProfileTab(searchParams.get("tab")));
  const [profile, setProfile] = useState<MentorProfileResponse | null>(null);
  const [formData, setFormData] = useState<MentorProfileFormData | null>(null);
  const [certification, setCertification] = useState<CertificationOwnViewResponse | null>(null);
  const [scheduleRecords, setScheduleRecords] = useState<MentorScheduleSlot[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [lastUpdatedAt, setLastUpdatedAt] = useState<string | null>(null);
  const [isEditing, setIsEditing] = useState(false);
  const [tagInput, setTagInput] = useState("");
  const [saving, setSaving] = useState(false);
  const [toast, setToast] = useState<ToastState | null>(null);
  const [certModalOpen, setCertModalOpen] = useState(false);
  const [certForm, setCertForm] = useState<CertificationFormState | null>(null);
  const [assetLoadingId, setAssetLoadingId] = useState<number | null>(null);
  const [scheduleModalOpen, setScheduleModalOpen] = useState(false);
  const [scheduleForm, setScheduleForm] = useState<ScheduleFormState>(createDefaultScheduleForm);
  const [recurringScheduleModalOpen, setRecurringScheduleModalOpen] = useState(false);
  const [recurringScheduleForm, setRecurringScheduleForm] = useState<RecurringScheduleFormState>(createDefaultRecurringScheduleForm);
  const [specialDateModalOpen, setSpecialDateModalOpen] = useState(false);
  const [specialDateForm, setSpecialDateForm] = useState<SpecialDateScheduleFormState>(createDefaultSpecialDateScheduleForm);
  const [deletingSlotId, setDeletingSlotId] = useState<number | null>(null);
  const [previewScenario, setPreviewScenario] = useState<PreviewScenarioKey>("CURRENT");
  const [passwordCountdown, setPasswordCountdown] = useState(0);
  const [passwordModal, setPasswordModal] = useState<PasswordModalState>(createPasswordModalState());

  const latestSnapshotRef = useRef<MentorProfileWorkspaceSnapshot | null>(null);
  const snapshotKey = buildWorkspaceSnapshotStorageKey("mentor", "profile", userId ?? "current");

  const showToast = (message: string, tone: ToastState["tone"] = "success") => {
    setToast({ message, tone });
  };

  useEffect(() => {
    if (!toast) {
      return undefined;
    }
    const timer = window.setTimeout(() => setToast(null), 3200);
    return () => window.clearTimeout(timer);
  }, [toast]);

  useEffect(() => {
    if (passwordCountdown <= 0) {
      return undefined;
    }

    const timer = window.setTimeout(() => setPasswordCountdown((current) => current - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [passwordCountdown]);

  const applyWorkspaceSnapshot = (snapshot: MentorProfileWorkspaceSnapshot, updatedAt: string) => {
    // 资料、认证和排期作为一个工作区快照恢复，编辑中的表单不被旧快照覆盖。
    latestSnapshotRef.current = snapshot;
    setProfile(snapshot.profile);
    setFormData((current) => {
      if (isEditing && current) {
        return current;
      }
      return buildProfileFormData(snapshot.profile);
    });
    setCertification(snapshot.certification);
    setScheduleRecords(sortScheduleRecords(snapshot.scheduleRecords));
    setLastUpdatedAt(updatedAt);
    setPageState("ready");
  };

  const persistWorkspaceSnapshot = (
    snapshot: MentorProfileWorkspaceSnapshot,
    updatedAt = new Date().toISOString(),
  ) => {
    // 保存快照时同步导师身份快照，侧边栏与订单页能复用最新头像和昵称。
    latestSnapshotRef.current = snapshot;
    writeMentorIdentitySnapshot({
      userId: snapshot.profile.userId,
      displayName: snapshot.profile.displayName,
      avatarUrl: snapshot.profile.avatarUrl ?? null,
      avatarUpdatedAt: snapshot.profile.avatarUpdatedAt,
      updatedAt,
    });
    writeWorkspaceSnapshot(snapshotKey, snapshot, updatedAt);
    setLastUpdatedAt(updatedAt);
    return updatedAt;
  };

  const loadAll = async (options?: { silent?: boolean; signal?: AbortSignal }) => {
    const silent = options?.silent ?? false;
    const signal = options?.signal;
    if (!silent) {
      setPageState((current) => (profile ? current : "loading"));
    }
    setRefreshing(true);
    setLoadError(null);

    try {
      // 三组接口并行加载，页面只在全部数据一致后进入 ready，避免排期和认证状态错位。
      const [profileResponse, certificationResponse, scheduleResponse] = await Promise.all([
        apiRequest<MentorProfileResponse>("/mentor/profile", { signal }),
        apiRequest<CertificationOwnViewResponse>("/certification/me", { signal }),
        apiRequest<MentorScheduleSlotListResponse>("/mentor/schedule/slots/me", { signal }),
      ]);

      if (signal?.aborted) {
        return;
      }

      const nextSnapshot: MentorProfileWorkspaceSnapshot = {
        profile: profileResponse,
        certification: certificationResponse,
        scheduleRecords: sortScheduleRecords(scheduleResponse.records ?? []),
      };
      const updatedAt = persistWorkspaceSnapshot(nextSnapshot);
      applyWorkspaceSnapshot(nextSnapshot, updatedAt);
    } catch (error) {
      if (isAbortError(error) || signal?.aborted) {
        return;
      }
      const message = buildErrorMessage(error, "导师资料与服务页加载失败，请稍后重试。");
      if (!latestSnapshotRef.current) {
        setLoadError(message);
        setPageState("error");
        return;
      }
      setLoadError(null);
    } finally {
      if (!signal?.aborted) {
        setRefreshing(false);
      }
    }
  };

  useEffect(() => {
    if (role !== "MENTOR") {
      return undefined;
    }

    const controller = new AbortController();
    const snapshot = readWorkspaceSnapshot<MentorProfileWorkspaceSnapshot>(snapshotKey);

    if (snapshot?.data) {
      // 先用 session 快照撑起页面，再静默刷新后端最新资料。
      applyWorkspaceSnapshot(snapshot.data, snapshot.updatedAt);
      setLoadError(null);
      void loadAll({ silent: true, signal: controller.signal });
    } else {
      latestSnapshotRef.current = null;
      void loadAll({ signal: controller.signal });
    }

    return () => controller.abort();
  }, [role, snapshotKey]);

  useEffect(() => {
    const nextTab = resolveProfileTab(searchParams.get("tab"));
    setActiveTab((current) => (current === nextTab ? current : nextTab));
  }, [searchParams]);

  useEffect(() => {
    if (pageState !== "ready") {
      return;
    }

    if (searchParams.get("action") !== "password") {
      return;
    }

    // 通知或资料页 deep-link 可以直接打开改密弹窗，消费后清理 query。
    setPasswordModal(createPasswordModalState({ isOpen: true }));
    setPasswordCountdown(0);

    const nextSearchParams = new URLSearchParams(searchParams);
    nextSearchParams.delete("action");
    setSearchParams(nextSearchParams, { replace: true });
  }, [pageState, searchParams, setSearchParams]);

  const initialFormData = profile ? buildProfileFormData(profile) : null;

  const isDirty = Boolean(
    initialFormData
    && formData
    && JSON.stringify(initialFormData) !== JSON.stringify(formData),
  );

  const currentApprovalStatus = certification?.approvalStatus ?? profile?.approvalStatus ?? "PENDING";
  const previewAvailability = formData?.available ?? false;
  const previewStatus = (() => {
    // 预览态把审核状态和可接单开关合成学生端会看到的导师名片状态。
    const normalized = previewScenario === "CURRENT"
      ? { approvalStatus: currentApprovalStatus, available: previewAvailability }
      : previewScenario === "APPROVED_OPEN"
        ? { approvalStatus: "APPROVED", available: true }
        : previewScenario === "APPROVED_PAUSED"
          ? { approvalStatus: "APPROVED", available: false }
          : previewScenario === "PENDING_REVIEW"
            ? { approvalStatus: "PENDING", available: false }
            : { approvalStatus: "REJECTED", available: false };

    if (normalized.approvalStatus === "APPROVED" && normalized.available) {
      return {
        approvalStatus: normalized.approvalStatus,
        available: normalized.available,
        badgeLabel: "已公开展示 · 当前可接单",
        badgeClassName: "border-emerald-200 bg-emerald-50 text-emerald-700",
        summary: "学生现在会把你视为可预约导师，建议重点确认价格、标签、排期和服务说明是否一眼清楚。",
        visibilityCopy: "你的名片和预约入口已对学生开放。",
      };
    }

    if (normalized.approvalStatus === "APPROVED") {
      return {
        approvalStatus: normalized.approvalStatus,
        available: normalized.available,
        badgeLabel: "已公开展示 · 暂停接单",
        badgeClassName: "border-slate-200 bg-slate-100 text-slate-700",
        summary: "学生仍然能看到你的导师名片，但会知道你目前暂不接新咨询。",
        visibilityCopy: "你的名片仍会展示，预约入口暂时关闭。",
      };
    }

    if (normalized.approvalStatus === "REJECTED") {
      return {
        approvalStatus: normalized.approvalStatus,
        available: normalized.available,
        badgeLabel: "待补件 · 当前不公开",
        badgeClassName: "border-rose-200 bg-rose-50 text-rose-700",
        summary: "当前不会对学生公开，建议先把名片信息补充完整，方便材料通过后直接上线。",
        visibilityCopy: "通过认证后，这张名片会正式展示。",
      };
    }

    return {
      approvalStatus: normalized.approvalStatus,
      available: normalized.available,
      badgeLabel: "审核中 · 预览待公开",
      badgeClassName: "border-amber-200 bg-amber-50 text-amber-700",
      summary: "现在更适合检查信息是否完整顺畅，审核通过后就会按下面的样子对外展示。",
      visibilityCopy: "审核通过后即可正式展示。",
    };
  })();

  const previewScheduleSummary = (() => {
    // 排期摘要只统计未来时段，已过期 slot 不参与名片预览。
    const now = Date.now();
    const weekDeadline = now + 7 * 24 * 60 * 60 * 1000;
    const futureSlots = scheduleRecords.filter((slot) => {
      const timestamp = toTimestamp(slot.startAt);
      return Number.isFinite(timestamp) && timestamp >= now;
    });
    const futureAvailable = futureSlots.filter((slot) => slot.status === "AVAILABLE");
    const futureBooked = futureSlots.filter((slot) => slot.status === "BOOKED");
    const weekAvailableCount = futureAvailable.filter((slot) => toTimestamp(slot.startAt) <= weekDeadline).length;

    return {
      nextAvailableLabel: futureAvailable[0] ? formatScheduleCardTime(futureAvailable[0].startAt) : "当前还没有未来可预约时段",
      nextBookedLabel: futureBooked[0] ? formatScheduleCardTime(futureBooked[0].startAt) : "当前还没有已预约时段",
      weekAvailableCount,
    };
  })();

  const specialDateSlots = scheduleRecords.filter((slot) => getScheduleDateKey(slot.startAt) === specialDateForm.date);

  if (pageState === "loading") {
    return (
      <WorkspacePageLoadingScreen
        title="正在打开导师资料页"
        description="正在为你准备资料、认证信息和排期安排，请稍候。"
      />
    );
  }

  if (pageState === "error" || !profile || !formData) {
    return (
      <div className="relative min-h-screen bg-[#f0f9f6]">
        <div className="pointer-events-none fixed inset-0 z-0 overflow-hidden">
          <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(16,185,129,0.14),transparent_30%),radial-gradient(circle_at_bottom_right,rgba(20,184,166,0.1),transparent_30%),linear-gradient(180deg,#f0fdf4_0%,#f8fafc_60%,#f8fafc_100%)]" />
          <div className="page-top-glow page-top-glow--emerald" />
        </div>
        <div className="relative z-10 flex min-h-screen items-center justify-center px-6">
          <div className="w-full max-w-xl rounded-[2rem] border border-white/70 bg-white/86 px-8 py-10 text-center shadow-[0_24px_60px_rgba(148,163,184,0.18)] backdrop-blur-xl">
            <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl bg-rose-50 text-rose-500 shadow-sm">
              <AlertCircle size={30} />
            </div>
            <h1 className="mt-5 text-2xl font-semibold text-slate-900">导师资料与服务暂时无法加载</h1>
            <p className="mt-3 text-sm leading-7 text-slate-500">{loadError ?? "请稍后重试。"}</p>
            <button
              type="button"
              onClick={() => void loadAll()}
              className="mt-7 inline-flex items-center justify-center rounded-full bg-slate-900 px-6 py-3 text-sm font-semibold text-white transition-colors hover:bg-slate-800"
            >
              <RefreshCw size={16} className="mr-2" />
              重新加载
            </button>
          </div>
        </div>
      </div>
    );
  }

  const approvalMeta = getApprovalMeta(certification?.approvalStatus ?? profile.approvalStatus);
  const heroName = profile.displayName.trim() || authDisplayName || "导师";
  const avatarLabel = getAvatarLabel(heroName);
  const visibleBio = formData.bio.trim() || "补充一段清晰的导师介绍后，学生会更快了解你的背景与辅导风格。";
  const enabledPackages = formData.packages.filter((item) => item.enabled);
  const enabledAppointmentPackage = enabledPackages.find((item) => item.deliveryMode === "APPOINTMENT") ?? null;
  const previewPackages = enabledPackages.length ? enabledPackages : formData.packages;
  const previewPrimaryPackage = previewPackages[0] ?? null;
  const previewStartingPriceFen = computeStartingPriceFen(formData.packages, formData.priceFen);

  const previewServiceScenes = formData.serviceScenes.length ? formData.serviceScenes : ["职业方向梳理"];
  const previewPrepMaterials = formData.prepMaterials.trim() || "最新简历、目标岗位 JD、最卡的 1-2 个问题";
  const previewReplyRhythm = formData.replyRhythm.trim() || "补充你的回复节奏后，学生会更清楚什么时候适合来找你。";
  const previewSuitableFor = formData.suitableFor.trim() || "适合已经有明确求职目标、希望聚焦某个求职卡点继续突破的学生。";
  const previewNotSuitableFor = formData.notSuitableFor.trim() || "补充清楚暂不适合的情况后，学生会更容易判断你们是否匹配。";
  const previewRoleText = buildMentorIdentityLine(formData, heroName);
  const previewListServiceScenes = previewServiceScenes.slice(0, 3);
  const previewListExpertiseTags = formData.expertiseTags.slice(0, 2);
  const floatingBarVisible = activeTab === "profile" && isEditing;
  const editorBarMode = isDirty ? "pending" : "editing";
  const editorBarPresentation = getEditorBarPresentation(editorBarMode);
  const editorFloatingMessage = isDirty
    ? "你正在更新导师名片，记得保存本次修改。"
    : "已进入编辑状态，修改完成后记得保存。";

  const handleTabChange = (nextTab: TabKey) => {
    // 资料编辑态不允许切 tab，避免用户误以为草稿已经保存。
    if (isEditing && isDirty) {
      showToast("请先保存或撤销当前修改，再切换到其他板块。", "warning");
      return;
    }
    setActiveTab(nextTab);
    setSearchParams((previous) => {
      const next = new URLSearchParams(previous);
      next.set("tab", nextTab);
      return next;
    }, { replace: true });
  };

  const handleManualRefresh = () => {
    if (isEditing && isDirty) {
      showToast("你有未保存的修改，请先保存或取消后再刷新。", "warning");
      return;
    }
    void loadAll({ silent: true });
  };

  const handleStartEdit = () => {
    setIsEditing(true);
    showToast("现在可以开始调整导师资料了。", "info");
  };

  const handleReset = () => {
    if (!initialFormData) {
      return;
    }
    setFormData(initialFormData);
    setIsEditing(false);
    setTagInput("");
    showToast("本次修改已取消。", "cancel");
  };

  const handleAddTag = () => {
    if (!formData) {
      return;
    }
    const normalized = tagInput.trim();
    if (!normalized) {
      return;
    }
    if (formData.expertiseTags.includes(normalized)) {
      setTagInput("");
      return;
    }
    if (formData.expertiseTags.length >= 6) {
      showToast("擅长标签最多保留 6 个，请精简核心方向。", "warning");
      return;
    }
    setFormData({
      ...formData,
      expertiseTags: [...formData.expertiseTags, normalized],
    });
    setTagInput("");
  };

  const handleRemoveTag = (tag: string) => {
    if (!formData) {
      return;
    }
    setFormData({
      ...formData,
      expertiseTags: formData.expertiseTags.filter((item) => item !== tag),
    });
  };

  const handleToggleScene = (scene: string) => {
    if (!formData || !isEditing) {
      return;
    }
    const nextScenes = formData.serviceScenes.includes(scene)
      ? formData.serviceScenes.filter((item) => item !== scene)
      : [...formData.serviceScenes, scene];
    setFormData({
      ...formData,
      serviceScenes: nextScenes,
    });
  };

  const handlePackagesChange = (nextPackages: MentorServicePackage[]) => {
    if (!formData) {
      return;
    }
    // 套餐排序、场景码和起步价在前端先归一，后端仍会再次校验。
    const normalized = nextPackages.map((item, index) => ({
      ...item,
      id: item.id || index + 1,
      sortNo: index + 1,
      sceneCode: resolveServiceSceneCode(item.sceneLabel),
      durationMinutes: item.deliveryMode === "APPOINTMENT" ? item.durationMinutes ?? 45 : null,
    }));
    setFormData({
      ...formData,
      packages: normalized,
      priceFen: computeStartingPriceFen(normalized, formData.priceFen),
    });
  };

  const handleOpenScheduleModal = () => {
    setScheduleForm(createDefaultScheduleForm(enabledAppointmentPackage?.durationMinutes ?? 45));
    setScheduleModalOpen(true);
  };

  const handleOpenRecurringScheduleModal = () => {
    setRecurringScheduleForm(createDefaultRecurringScheduleForm(enabledAppointmentPackage?.durationMinutes ?? 45));
    setRecurringScheduleModalOpen(true);
  };

  const handleOpenSpecialDateModal = () => {
    setSpecialDateForm(createDefaultSpecialDateScheduleForm(enabledAppointmentPackage?.durationMinutes ?? 45));
    setSpecialDateModalOpen(true);
  };

  const commitScheduleRecords = (nextScheduleRecords: MentorScheduleSlot[]) => {
    // 排期调整成功后只更新本地排期片段，不强制重拉整页资料。
    setScheduleRecords(nextScheduleRecords);
    if (profile) {
      persistWorkspaceSnapshot({
        profile,
        certification,
        scheduleRecords: nextScheduleRecords,
      });
    }
  };

  const handleSave = async () => {
    if (!formData) {
      return;
    }
    const normalizedDisplayName = formData.displayName.trim();
    if (!normalizedDisplayName) {
      showToast("请先填写导师昵称。", "error");
      return;
    }
    if (formData.showRealName && !formData.realName.trim()) {
      showToast("开启真名展示前，请先补全真实姓名。", "error");
      return;
    }
    if (!formData.packages.length) {
      showToast("请至少保留一个服务套餐。", "error");
      return;
    }
    const enabledPackageCount = formData.packages.filter((item) => item.enabled).length;
    const appointmentPackageCount = formData.packages.filter((item) => item.deliveryMode === "APPOINTMENT").length;
    if (!enabledPackageCount) {
      showToast("请至少启用一个对外展示的服务套餐。", "error");
      return;
    }
    if (appointmentPackageCount > 1) {
      showToast("当前仅支持保留一个预约型套餐。", "error");
      return;
    }
    if (formData.packages.some((item) => !item.packageName.trim())) {
      showToast("请先补全每个套餐的名称。", "error");
      return;
    }
    if (formData.packages.some((item) => item.priceFen < 0)) {
      showToast("套餐价格不能小于 0。", "error");
      return;
    }
    if (formData.packages.some((item) => item.deliveryMode === "APPOINTMENT" && !item.durationMinutes)) {
      showToast("预约型套餐需要明确填写服务时长。", "error");
      return;
    }
    setSaving(true);
    try {
      // 导师公开名片和服务套餐一起提交，起步价由启用套餐重新计算。
      const response = await apiRequest<MentorProfileResponse>("/mentor/profile", {
        method: "PUT",
        body: JSON.stringify({
          displayName: normalizedDisplayName,
          showRealName: formData.showRealName,
          jobTitle: formData.jobTitle,
          expertiseTags: formData.expertiseTags,
          serviceScenes: formData.serviceScenes,
          bio: formData.bio,
          suitableFor: formData.suitableFor,
          notSuitableFor: formData.notSuitableFor,
          prepMaterials: formData.prepMaterials,
          replyRhythm: formData.replyRhythm,
          priceFen: computeStartingPriceFen(formData.packages, formData.priceFen),
          packages: formData.packages.map((item) => ({
            packageName: item.packageName.trim(),
            sceneCode: resolveServiceSceneCode(item.sceneLabel),
            sceneLabel: item.sceneLabel,
            deliveryMode: item.deliveryMode,
            durationMinutes: item.deliveryMode === "APPOINTMENT" ? item.durationMinutes ?? 45 : null,
            priceFen: item.priceFen,
            description: item.description?.trim() || "",
            enabled: item.enabled,
          })),
          available: formData.available,
        }),
      });
      const nextSnapshot: MentorProfileWorkspaceSnapshot = {
        profile: response,
        certification,
        scheduleRecords,
      };
      setProfile(response);
      setFormData(buildProfileFormData(response));
      setIsEditing(false);
      persistWorkspaceSnapshot(nextSnapshot);
      try {
        await refreshAuthProfile();
      } catch {
        // 登录态昵称未及时刷新时，不影响资料主流程保存成功。
      }
      showToast("个人资料及服务设置已更新。");
    } catch (error) {
      showToast(buildErrorMessage(error, "资料保存失败，请稍后重试。"), "error");
    } finally {
      setSaving(false);
    }
  };

  const handleOpenCertificationModal = () => {
    setCertForm(createCertificationForm(profile, certification));
    setCertModalOpen(true);
  };

  const handleCertificationFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0] ?? null;
    setCertForm((current) => (current ? { ...current, file } : current));
  };

  const handleSubmitCertification = async () => {
    if (!certForm) {
      return;
    }
    if (!certForm.realName.trim() || !certForm.companyName.trim() || !certForm.jobTitle.trim()) {
      showToast("请先补全真实姓名、公司和职位后再重新提交认证。", "error");
      return;
    }
    if (!certForm.file) {
      showToast("请先选择认证材料文件。", "error");
      return;
    }

    setCertForm({ ...certForm, busy: true });
    try {
      // 认证补件沿用统一认证 submission 链，提交成功后整页静默刷新。
      const payload = new FormData();
      payload.append("realName", certForm.realName.trim());
      payload.append("companyName", certForm.companyName.trim());
      payload.append("jobTitle", certForm.jobTitle.trim());
      payload.append("file", certForm.file);
      await apiRequest<CertificationSubmission>("/certification/me", {
        method: "POST",
        body: payload,
      });
      setCertModalOpen(false);
      await loadAll({ silent: true });
      showToast("认证资料已提交，等待平台审核。");
    } catch (error) {
      setCertForm((current) => (current ? { ...current, busy: false } : current));
      showToast(buildErrorMessage(error, "认证材料提交失败，请稍后重试。"), "error");
    }
  };

  const handleOpenAsset = async (asset: CertificationAsset) => {
    setAssetLoadingId(asset.assetId);
    try {
      const response = await apiRequest<Response>(`/certification/assets/${asset.assetId}/content`, {
        rawResponse: true,
      });
      if (!(response instanceof Response) || !response.ok) {
        throw new ApiClientError("认证附件读取失败", response instanceof Response ? response.status : 500);
      }
      const blob = await response.blob();
      const objectUrl = window.URL.createObjectURL(blob);
      window.open(objectUrl, "_blank", "noopener,noreferrer");
      window.setTimeout(() => window.URL.revokeObjectURL(objectUrl), 60_000);
    } catch (error) {
      showToast(buildErrorMessage(error, "认证附件读取失败，请稍后重试。"), "error");
    } finally {
      setAssetLoadingId(null);
    }
  };

  const handleCreateSchedule = async () => {
    const range = toIsoRange(scheduleForm.date, scheduleForm.time, scheduleForm.durationMinutes);
    if (!range) {
      showToast("请先填写有效的日期和时间。", "error");
      return;
    }
    setScheduleForm((current) => ({ ...current, busy: true }));
    try {
      const response = await apiRequest<MentorScheduleSlot>("/mentor/schedule/slots", {
        method: "POST",
        body: JSON.stringify(range),
      });
      const nextScheduleRecords = sortScheduleRecords([...scheduleRecords, response]);
      commitScheduleRecords(nextScheduleRecords);
      setScheduleModalOpen(false);
      setScheduleForm(createDefaultScheduleForm());
      showToast("新的可预约时间已添加。");
    } catch (error) {
      setScheduleForm((current) => ({ ...current, busy: false }));
      showToast(buildErrorMessage(error, "新增排期失败，请稍后重试。"), "error");
    }
  };

  const handleCreateRecurringSchedule = async () => {
    if (!recurringScheduleForm.weekdays.length) {
      showToast("请至少选择一个固定周几。", "error");
      return;
    }
    const records = buildRecurringScheduleRanges(recurringScheduleForm);
    const clearRanges = buildRecurringScheduleDayRanges(recurringScheduleForm);
    if (recurringScheduleForm.mode !== "CLEAR" && !records.length) {
      showToast("没有生成可用时段，请检查开始日期和时间。", "error");
      return;
    }
    if ((recurringScheduleForm.mode === "REPLACE" || recurringScheduleForm.mode === "CLEAR") && !clearRanges.length) {
      showToast("没有找到需要调整的日期，请检查开始日期和固定周几。", "error");
      return;
    }

    setRecurringScheduleForm((current) => ({ ...current, busy: true }));
    let clearedOnServer = false;
    try {
      let deleteResponse: MentorScheduleSlotBatchDeleteResponse | null = null;
      let createResponse: MentorScheduleSlotBatchCreateResponse | null = null;
      let nextScheduleRecords = scheduleRecords;

      if (recurringScheduleForm.mode === "REPLACE" || recurringScheduleForm.mode === "CLEAR") {
        // 覆盖/清空只删除未来可用时段，已被订单锁定的时段由后端保留。
        deleteResponse = await apiRequest<MentorScheduleSlotBatchDeleteResponse>("/mentor/schedule/slots/batch-delete", {
          method: "POST",
          body: JSON.stringify({
            records: clearRanges,
          }),
        });
        clearedOnServer = true;
        nextScheduleRecords = stripAvailableSlotsInRanges(nextScheduleRecords, clearRanges);
      }

      if (recurringScheduleForm.mode !== "CLEAR") {
        // CREATE/REPLACE 都会批量补新时段，重复时段由服务端跳过并返回 skippedCount。
        createResponse = await apiRequest<MentorScheduleSlotBatchCreateResponse>("/mentor/schedule/slots/batch", {
          method: "POST",
          body: JSON.stringify({
            records,
          }),
        });
        nextScheduleRecords = sortScheduleRecords([
          ...nextScheduleRecords,
          ...(createResponse.records ?? []),
        ]);
      }

      commitScheduleRecords(nextScheduleRecords);
      setRecurringScheduleModalOpen(false);
      setRecurringScheduleForm(createDefaultRecurringScheduleForm(enabledAppointmentPackage?.durationMinutes ?? 45));
      if (recurringScheduleForm.mode === "CREATE" && createResponse) {
        if (createResponse.createdCount > 0 && createResponse.skippedCount > 0) {
          showToast(`已生成 ${createResponse.createdCount} 个固定时段，自动跳过 ${createResponse.skippedCount} 个重复时段。`);
          return;
        }
        if (createResponse.createdCount > 0) {
          showToast(`已生成 ${createResponse.createdCount} 个固定时段。`);
          return;
        }
        showToast("本次没有新增时段，生成范围内的时段可能已经存在。", "warning");
        return;
      }

      if (recurringScheduleForm.mode === "REPLACE" && deleteResponse && createResponse) {
        showToast(
          `已覆盖未来排期，清空 ${deleteResponse.deletedCount} 个未预约时段，保留 ${deleteResponse.lockedCount} 个已锁定时段，新生成 ${createResponse.createdCount} 个时段。`,
          createResponse.createdCount > 0 || deleteResponse.deletedCount > 0 ? "success" : "warning",
        );
        return;
      }

      if (deleteResponse) {
        if (deleteResponse.deletedCount > 0 || deleteResponse.lockedCount > 0) {
          showToast(`已清空 ${deleteResponse.deletedCount} 个未预约时段，保留 ${deleteResponse.lockedCount} 个已锁定时段。`);
          return;
        }
        showToast("这批日期里暂时没有可清空的未来时段。", "warning");
      }
    } catch (error) {
      if (clearedOnServer) {
        try {
          await loadAll({ silent: true });
        } catch {
          // 覆盖失败后，下次进入页面会继续以缓存和后台结果重新对齐。
        }
      }
      setRecurringScheduleForm((current) => ({ ...current, busy: false }));
      showToast(buildErrorMessage(error, "固定排期调整失败，请稍后重试。"), "error");
    }
  };

  const handleClearSpecialDate = async () => {
    const range = createDayRange(specialDateForm.date);
    if (!range) {
      showToast("请先选择一个有效日期。", "error");
      return;
    }
    setSpecialDateForm((current) => ({ ...current, clearBusy: true }));
    try {
      const response = await apiRequest<MentorScheduleSlotBatchDeleteResponse>("/mentor/schedule/slots/batch-delete", {
        method: "POST",
        body: JSON.stringify({
          records: [range],
        }),
      });
      const nextScheduleRecords = stripAvailableSlotsInRanges(scheduleRecords, [range]);
      commitScheduleRecords(nextScheduleRecords);
      if (response.deletedCount > 0 || response.lockedCount > 0) {
        showToast(`已调整 ${specialDateForm.date}，清空 ${response.deletedCount} 个未预约时段，保留 ${response.lockedCount} 个已锁定时段。`);
        return;
      }
      showToast("这一天暂时没有可清空的未来时段。", "warning");
    } catch (error) {
      showToast(buildErrorMessage(error, "清理当天排期失败，请稍后重试。"), "error");
    } finally {
      setSpecialDateForm((current) => ({ ...current, clearBusy: false }));
    }
  };

  const handleAddSpecialDateSlot = async () => {
    const range = toIsoRange(specialDateForm.date, specialDateForm.time, specialDateForm.durationMinutes);
    if (!range) {
      showToast("请先填写有效的日期和时间。", "error");
      return;
    }
    setSpecialDateForm((current) => ({ ...current, addBusy: true }));
    try {
      const response = await apiRequest<MentorScheduleSlot>("/mentor/schedule/slots", {
        method: "POST",
        body: JSON.stringify(range),
      });
      const nextScheduleRecords = sortScheduleRecords([...scheduleRecords, response]);
      commitScheduleRecords(nextScheduleRecords);
      showToast("这个特殊日期时段已补充到排期里。");
    } catch (error) {
      showToast(buildErrorMessage(error, "补充当天时段失败，请稍后重试。"), "error");
    } finally {
      setSpecialDateForm((current) => ({ ...current, addBusy: false }));
    }
  };

  const handleDeleteSlot = async (slotId: number) => {
    setDeletingSlotId(slotId);
    try {
      await apiRequest<void>(`/mentor/schedule/slots/${slotId}`, { method: "DELETE" });
      const nextScheduleRecords = scheduleRecords.filter((item) => item.id !== slotId);
      commitScheduleRecords(nextScheduleRecords);
      showToast("这个可预约时间已移除。");
    } catch (error) {
      showToast(buildErrorMessage(error, "删除排期失败，请稍后重试。"), "error");
    } finally {
      setDeletingSlotId(null);
    }
  };

  const handleSendPasswordCode = async () => {
    setPasswordModal((current) => ({ ...current, busy: true }));
    try {
      // 导师改密复用资料安全验证码链，mock 环境会返回 debugCode。
      const response = await apiRequest<SendCodeResponse>("/mentor/profile/security/password/send-code", {
        method: "POST",
      });
      setPasswordCountdown(calculateCountdown(response.nextSendAt));
      setPasswordModal((current) => ({
        ...current,
        debugHint: response.debugCode
          ? { code: response.debugCode, targetEmail: response.targetEmail }
          : null,
      }));
      showToast(
        response.debugCode
          ? "验证码已准备好，可直接使用下方验证码继续。"
          : `验证码已发送至 ${response.targetEmail}`,
        response.debugCode ? "info" : "success",
      );
    } catch (error) {
      showToast(buildErrorMessage(error, "密码验证码发送失败，请稍后重试。"), "error");
    } finally {
      setPasswordModal((current) => ({ ...current, busy: false }));
    }
  };

  const handleVerifyPasswordCode = async () => {
    if (!passwordModal.code.trim()) {
      showToast("请输入当前邮箱收到的验证码。", "error");
      return;
    }

    setPasswordModal((current) => ({ ...current, busy: true }));
    try {
      const response = await apiRequest<VerifyCodeResponse>("/mentor/profile/security/password/verify-code", {
        method: "POST",
        body: JSON.stringify({ code: passwordModal.code.trim() }),
      });
      setPasswordModal((current) => ({
        ...current,
        busy: false,
        step: "changePassword",
        code: "",
        passwordResetToken: response.verificationToken,
        debugHint: null,
      }));
      setPasswordCountdown(0);
      showToast("验证通过，请设置新的登录密码。");
    } catch (error) {
      setPasswordModal((current) => ({ ...current, busy: false }));
      showToast(buildErrorMessage(error, "验证失败，请稍后重试。"), "error");
    }
  };

  const handleChangePassword = async () => {
    const nextPassword = passwordModal.newPassword.trim();
    if (!nextPassword) {
      showToast("请输入新的登录密码。", "error");
      return;
    }
    if (!/^(?=.*[A-Za-z])(?=.*\d).{8,64}$/.test(nextPassword)) {
      showToast("密码至少 8 位，并且需要同时包含字母和数字。", "error");
      return;
    }

    setPasswordModal((current) => ({ ...current, busy: true }));
    try {
      await apiRequest("/mentor/profile/security/password/change", {
        method: "POST",
        body: JSON.stringify({
          passwordResetToken: passwordModal.passwordResetToken,
          newPassword: nextPassword,
        }),
      });
      setPasswordModal(createPasswordModalState());
      setPasswordCountdown(0);
      showToast("登录密码已更新。");
    } catch (error) {
      setPasswordModal((current) => ({ ...current, busy: false }));
      showToast(buildErrorMessage(error, "密码修改失败，请稍后重试。"), "error");
    }
  };

  const currentSubmission = certification?.currentSubmission;

  return (
    <div className="relative min-h-screen bg-[#f0f9f6] text-slate-900">
      <div className="pointer-events-none fixed inset-0 z-0 overflow-hidden">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(16,185,129,0.12),transparent_35%),radial-gradient(circle_at_bottom_right,rgba(20,184,166,0.1),transparent_30%),linear-gradient(180deg,#f0fdf4_0%,#f8fafc_60%,#f8fafc_100%)]" />
        <div className="page-top-glow page-top-glow--emerald" />
      </div>

      <WorkspaceRoleTopbar
        sectionLabel="Profile & Services"
        title="导师资料与服务"
        icon={BriefcaseBusiness}
        navItems={getMentorWorkspaceNavItems(activeTab === "notifications" ? "notifications" : "profile")}
        displayName={heroName}
        userAvatarUrl={profile.avatarUrl}
        userSubtitle="导师资料与服务维护"
        userFallbackLabel="导师"
        userFallbackInitial="导"
        lastUpdatedAt={lastUpdatedAt}
        onRefresh={handleManualRefresh}
        refreshing={refreshing}
        refreshTitle="刷新当前页面内容"
        rightActions={(
          <Link
            to="/mentor/dashboard"
            className="hidden h-10 items-center justify-center rounded-full border border-slate-200 bg-white px-4 text-sm font-semibold text-slate-600 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600 md:inline-flex"
          >
            <ArrowLeft size={16} className="mr-2" />
            返回工作台
          </Link>
        )}
      />

      <main className="relative mx-auto w-full max-w-[96rem] px-4 pb-6 pt-8 sm:px-6 lg:px-8 lg:pb-8 lg:pt-10">
        <div className="space-y-8">
          <section>
            <motion.div
              initial={{ opacity: 0, y: 15 }}
              animate={{ opacity: 1, y: 0 }}
              className="relative overflow-hidden rounded-[2.35rem] border border-white/70 bg-white/85 p-6 shadow-[0_20px_50px_rgba(15,23,42,0.06)] backdrop-blur-xl lg:px-8 lg:py-7"
            >
              <div className="absolute -right-14 -top-14 h-48 w-48 rounded-full bg-teal-100/60 blur-3xl" />
              <div className="absolute bottom-0 left-0 h-40 w-40 rounded-full bg-emerald-100/50 blur-3xl" />

              <div className="relative z-10 flex flex-col gap-6 lg:flex-row lg:items-center lg:justify-between">
                <div className="flex flex-col gap-5 md:flex-row md:items-start">
                  <div className="relative shrink-0">
                    <MentorAvatarEditor
                      userId={profile.userId}
                      heroName={heroName}
                      avatarConfigured={profile.avatarConfigured}
                      avatarUrl={profile.avatarUrl}
                      approvalStatus={currentApprovalStatus}
                      onAvatarUploaded={(response: MentorAvatarUploadResult) => {
                        const nextProfile = {
                          ...profile,
                          avatarUrl: response.avatarUrl,
                          avatarConfigured: true,
                          avatarContentType: response.contentType,
                          avatarUpdatedAt: response.updatedAt,
                        };
                        const updatedAt = typeof response.updatedAt === "number"
                          ? new Date(response.updatedAt).toISOString()
                          : String(response.updatedAt);
                        setProfile(nextProfile);
                        persistWorkspaceSnapshot({
                          profile: nextProfile,
                          certification,
                          scheduleRecords,
                        }, updatedAt);
                      }}
                      onToast={showToast}
                    />
                  </div>

                  <div className="max-w-2xl pt-1">
                    <div className="mb-3 flex flex-wrap items-center gap-2">
                      <span className={joinClasses("rounded-full border px-3 py-1.5 text-xs font-bold", formData.available ? "border-emerald-200 bg-emerald-50 text-emerald-700" : "border-slate-200 bg-slate-100 text-slate-600")}>
                        <span className={joinClasses("mr-1.5 inline-block h-1.5 w-1.5 rounded-full", formData.available ? "bg-emerald-500" : "bg-slate-400")} />
                        {formData.available ? "当前可接单" : "当前暂停接单"}
                      </span>
                      <span className={joinClasses("rounded-full border px-3 py-1.5 text-xs font-bold", approvalMeta.heroClassName)}>
                        {approvalMeta.label}
                      </span>
                    </div>

                    <h1 className="flex items-center text-3xl font-bold tracking-tight text-slate-900 lg:text-4xl">
                      {heroName}
                    </h1>

                    <div className="mt-2.5 flex flex-wrap items-center text-base font-medium text-slate-600">
                      <Building2 size={16} className="mr-1.5 text-slate-400" />
                      <span>{formData.companyName || "待补充公司信息"}</span>
                      <span className="mx-2 text-slate-300">|</span>
                      <span>{formData.jobTitle || "待补充职位信息"}</span>
                    </div>

                    <div className="mt-4 flex flex-wrap gap-2">
                      {formData.expertiseTags.length > 0 ? (
                        formData.expertiseTags.map((tag) => (
                          <span key={tag} className="rounded-lg border border-slate-200 bg-slate-50/80 px-2.5 py-1 text-xs font-medium text-slate-500">
                            {tag}
                          </span>
                        ))
                      ) : (
                        <span className="rounded-lg border border-dashed border-slate-200 bg-white/80 px-3 py-1.5 text-xs font-medium text-slate-400">
                          补充擅长标签后，学生会更快看懂你的优势
                        </span>
                      )}
                    </div>

                    <div className="mt-5 flex flex-wrap gap-2">
                      <button
                        type="button"
                        onClick={() => {
                          handleTabChange("profile");
                          setIsEditing(true);
                        }}
                        className="inline-flex items-center rounded-full bg-slate-900 px-4 py-2 text-sm font-bold text-white shadow-sm transition-colors hover:bg-slate-800"
                      >
                        <Edit3 size={14} className="mr-2" />
                        编辑资料
                      </button>
                      <button
                        type="button"
                        onClick={() => handleTabChange("preview")}
                        className="inline-flex items-center rounded-full border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-600 transition-colors hover:border-emerald-200 hover:text-emerald-600"
                      >
                        <Eye size={14} className="mr-2" />
                        查看学生视角
                      </button>
                      <button
                        type="button"
                        onClick={() => handleTabChange("schedule")}
                        className="inline-flex items-center rounded-full border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-600 transition-colors hover:border-emerald-200 hover:text-emerald-600"
                      >
                        <CalendarDays size={14} className="mr-2" />
                        管理排期
                      </button>
                    </div>
                  </div>
                </div>

                <div className="flex shrink-0 lg:w-[20rem] lg:items-center">
                  <div className="grid w-full grid-cols-3 gap-3">
                    <StatTile
                      icon={<Star size={18} className="fill-current text-amber-500" />}
                      value={profile.avgRating?.toFixed(1) ?? "0.0"}
                      label="综合评分"
                    />
                    <StatTile
                      icon={<CheckCircle2 size={18} className="text-emerald-500" />}
                      value={formatCount(profile.totalOrders)}
                      label="累计咨询"
                    />
                    <StatTile
                      icon={<CreditCard size={18} className="text-teal-500" />}
                      value={`${formatMoneyFen(previewStartingPriceFen).replace(".00", "")} 起`}
                      label="套餐起步价"
                    />
                  </div>
                </div>
              </div>
            </motion.div>
          </section>

          <div className="sticky top-[5.25rem] z-30 rounded-[1.6rem] border border-white/70 bg-white/70 p-2 shadow-sm backdrop-blur-xl">
            <div className="mx-auto grid w-full max-w-4xl grid-cols-5 gap-2">
              {PROFILE_TABS.map((tab) => {
                const active = activeTab === tab.id;
                const Icon = tab.icon;
                return (
                  <button
                    key={tab.id}
                    type="button"
                    onClick={() => handleTabChange(tab.id)}
                    className={joinClasses(
                      "relative flex min-h-[54px] items-center justify-center rounded-[1.15rem] px-2 py-2 text-sm font-semibold transition-all md:text-base",
                      active ? "text-emerald-700" : "text-slate-500 hover:bg-white/60 hover:text-slate-700",
                    )}
                  >
                    {active && (
                      <motion.div
                        layoutId="mentor-profile-tab-indicator"
                        className="absolute inset-0 rounded-[1.15rem] border border-emerald-100/50 bg-white shadow-[0_4px_12px_rgba(16,185,129,0.08)]"
                      />
                    )}
                    <span
                      className={joinClasses(
                        "relative z-10 mr-2 hidden h-7 w-7 items-center justify-center rounded-xl border transition-colors sm:flex",
                        active ? "border-emerald-100 bg-emerald-50 text-emerald-600" : "border-slate-200 bg-white/80 text-slate-400",
                      )}
                    >
                      <Icon size={14} />
                    </span>
                    <span className="relative z-10 whitespace-nowrap">{tab.label}</span>
                  </button>
                );
              })}
            </div>
          </div>

          <AnimatePresence mode="wait">
            {activeTab === "profile" && (
              <motion.div
                key="profile"
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -12 }}
                className="space-y-6"
              >
                <section className="overflow-hidden rounded-[2rem] border border-white/70 bg-white/80 shadow-[0_15px_40px_rgba(15,23,42,0.04)] backdrop-blur-xl">
                  <div className="flex flex-col gap-4 border-b border-slate-100 bg-slate-50/50 px-6 py-5 lg:flex-row lg:items-center lg:justify-between">
                    <div>
                      <h2 className="flex items-center text-xl font-semibold text-slate-900">
                        <BriefcaseBusiness size={18} className="mr-2 text-emerald-500" />
                        我的导师名片
                      </h2>
                      <p className="mt-1.5 text-sm font-medium text-slate-500">
                        这里填写的内容会直接影响学生第一次看到你的印象。
                      </p>
                    </div>
                    {!isEditing ? (
                      <button
                        type="button"
                        onClick={handleStartEdit}
                        className="inline-flex w-max items-center justify-center rounded-xl bg-emerald-50 px-4 py-2.5 text-sm font-bold text-emerald-600 transition-colors hover:bg-emerald-100"
                      >
                        <Edit3 size={16} className="mr-2" />
                        编辑资料
                      </button>
                    ) : (
                      <div className="inline-flex items-center rounded-full border border-amber-200 bg-amber-50 px-3 py-1.5 text-sm font-bold text-amber-600">
                        编辑中
                      </div>
                    )}
                  </div>

                  <div className="space-y-6 p-6">
                    <div className="grid gap-4 md:grid-cols-2">
                      <EditableField
                        label="对外展示称呼"
                        icon={User}
                        value={formData.displayName}
                        isEditing={isEditing}
                        onChange={(value) => setFormData({ ...formData, displayName: value })}
                        helper="这是学生看到的主要称呼。"
                      />
                      <EditableField
                        label="真实姓名"
                        icon={ShieldCheck}
                        value={formData.realName}
                        isEditing={false}
                        onChange={() => {}}
                        helper="如需更正，请联系管理员处理。"
                        headerRight={(
                          <InlineSwitch
                            value={formData.showRealName}
                            isEditing={isEditing}
                            onChange={(nextValue) => setFormData({ ...formData, showRealName: nextValue })}
                          />
                        )}
                      />
                    </div>

                    <div className="grid gap-4 md:grid-cols-2">
                      <EditableField
                        label="所在公司"
                        icon={Building2}
                        value={formData.companyName}
                        isEditing={false}
                        onChange={() => {}}
                        helper="如需更正，请联系管理员处理。"
                      />
                      <EditableField
                        label="当前职位"
                        icon={BriefcaseBusiness}
                        value={formData.jobTitle}
                        isEditing={isEditing}
                        onChange={(value) => setFormData({ ...formData, jobTitle: value })}
                      />
                    </div>

                    <div className="grid gap-4 md:grid-cols-2">
                      <div className="rounded-[1.45rem] border border-slate-100 bg-white/60 px-5 py-4 shadow-sm">
                        <label className="flex items-center text-sm font-bold text-slate-700">
                          <CreditCard size={16} className="mr-2 text-emerald-500" />
                          套餐起步价
                        </label>
                        <div className="mt-3 text-[1.7rem] font-black leading-none text-slate-900">
                          {formatMoneyFen(computeStartingPriceFen(formData.packages, formData.priceFen))}
                        </div>
                        <div className="mt-2 text-sm leading-6 text-slate-500">
                          学生看到的是起步价，下单时会按所选套餐结算。
                        </div>
                      </div>
                      <ToggleField
                        label="接单状态"
                        description="关闭后学生仍能看到你的名片，但会知道你暂时不接新咨询。"
                        value={formData.available}
                        isEditing={isEditing}
                        onChange={(nextValue) => setFormData({ ...formData, available: nextValue })}
                      />
                    </div>

                    <ServicePackageEditorCard
                      isEditing={isEditing}
                      packages={formData.packages}
                      onChange={handlePackagesChange}
                      onNotify={showToast}
                    />

                    <TagEditorCard
                      isEditing={isEditing}
                      tags={formData.expertiseTags}
                      tagInput={tagInput}
                      onTagInputChange={setTagInput}
                      onAddTag={handleAddTag}
                      onRemoveTag={handleRemoveTag}
                    />

                    <SceneSelectorCard
                      selectedScenes={formData.serviceScenes}
                      isEditing={isEditing}
                      onToggle={handleToggleScene}
                    />

                    <div className="rounded-[1.45rem] border border-slate-100 bg-white/60 px-5 py-5 shadow-sm">
                      <label className="mb-2 flex items-center text-sm font-bold text-slate-700">
                        <FileCheck size={16} className="mr-2 text-emerald-500" />
                        导师个人简介
                      </label>
                      {isEditing ? (
                        <textarea
                          value={formData.bio}
                          onChange={(event) => setFormData({ ...formData, bio: event.target.value })}
                          rows={4}
                          className="w-full rounded-xl border-2 border-slate-200 p-3 text-sm text-slate-800 outline-none transition-colors focus:border-emerald-400"
                          placeholder="介绍你的经历、擅长方向和陪伴学生的方式..."
                        />
                      ) : (
                        <p className="whitespace-pre-wrap text-sm leading-relaxed text-slate-600">
                          {formData.bio || "还没有填写导师介绍。"}
                        </p>
                      )}
                    </div>
                  </div>
                </section>

                <section className="overflow-hidden rounded-[2rem] border border-white/70 bg-white/80 shadow-[0_15px_40px_rgba(15,23,42,0.04)] backdrop-blur-xl">
                  <div className="flex items-center gap-2 border-b border-slate-100 bg-slate-50/50 px-6 py-5">
                    <ShieldCheck size={18} className="text-teal-500" />
                    <h2 className="text-xl font-semibold text-slate-900">服务边界与接单偏好</h2>
                  </div>
                  <div className="grid gap-5 p-6 md:grid-cols-2">
                    <TextAreaField
                      label="适合咨询的人群"
                      icon={ThumbsUp}
                      value={formData.suitableFor}
                      isEditing={isEditing}
                      onChange={(value) => setFormData({ ...formData, suitableFor: value })}
                      placeholder="例如：1-3 年工作经验、正在准备大厂面试的前端方向同学"
                    />
                    <TextAreaField
                      label="不适合咨询的范围"
                      icon={AlertCircle}
                      value={formData.notSuitableFor}
                      isEditing={isEditing}
                      onChange={(value) => setFormData({ ...formData, notSuitableFor: value })}
                      placeholder="例如：零基础希望手把手从语法开始带练的同学"
                    />
                    <TextAreaField
                      label="建议学生提前准备"
                      icon={FileCheck}
                      value={formData.prepMaterials}
                      isEditing={isEditing}
                      onChange={(value) => setFormData({ ...formData, prepMaterials: value })}
                      placeholder="例如：最新简历、目标岗位 JD、当前最卡的 1-2 个问题"
                    />
                    <TextAreaField
                      label="预计回复节奏"
                      icon={Clock3}
                      value={formData.replyRhythm}
                      isEditing={isEditing}
                      onChange={(value) => setFormData({ ...formData, replyRhythm: value })}
                      placeholder="例如：工作日晚间集中回复，周末可安排语音咨询"
                    />
                  </div>
                </section>
              </motion.div>
            )}

            {activeTab === "preview" && (
              <motion.div
                key="preview"
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -12 }}
                className="space-y-6"
              >
                <div className="flex items-start gap-4 rounded-[1.5rem] border border-indigo-100 bg-indigo-50 p-5">
                  <div className="mt-0.5"><Eye size={20} className="text-indigo-500" /></div>
                  <div>
                    <h3 className="font-bold text-indigo-900">学生视角预览</h3>
                    <p className="mt-1 text-sm text-indigo-700/80">
                      这里展示的是学生浏览导师时看到的页面效果，你可以直接检查整体观感和信息表达是否顺畅。
                    </p>
                  </div>
                </div>

                <div className="rounded-[1.5rem] border border-slate-200 bg-white/85 p-5 shadow-sm">
                  <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
                    <div className="flex min-h-[3.65rem] flex-col justify-center">
                      <h4 className="text-xl font-bold leading-tight text-slate-950">公开效果预览</h4>
                      <p className="mt-1 text-sm leading-7 text-slate-500">{previewStatus.summary}</p>
                    </div>
                    <div className="flex flex-wrap gap-2">
                      {PREVIEW_SCENARIO_OPTIONS.map((item) => (
                        <button
                          key={item.id}
                          type="button"
                          onClick={() => setPreviewScenario(item.id)}
                          className={joinClasses(
                            "rounded-full border px-4 py-2 text-sm font-semibold transition-colors",
                            previewScenario === item.id
                              ? "border-slate-900 bg-slate-900 text-white"
                              : "border-slate-200 bg-white text-slate-600 hover:border-slate-300 hover:text-slate-900",
                          )}
                        >
                          {item.label}
                        </button>
                      ))}
                    </div>
                  </div>
                </div>

                <div className="grid gap-6 xl:grid-cols-[minmax(0,0.88fr)_minmax(0,1.12fr)]">
                  <section className="rounded-[1.8rem] border border-slate-200 bg-white p-6 shadow-sm">
                    <div className="flex items-start justify-between gap-3">
                      <div className="flex min-h-[4.1rem] flex-col justify-center">
                        <h4 className="text-xl font-bold leading-tight text-slate-950">导师广场列表卡预览</h4>
                        <p className="mt-1 text-sm leading-7 text-slate-500">
                          这里就是学生在导师广场里最先看到的样子，重点看头像、身份介绍、价格和标签是否自然顺眼。
                        </p>
                      </div>
                      <span className={joinClasses("rounded-full border px-3 py-1 text-xs font-bold", previewStatus.badgeClassName)}>
                        {previewStatus.badgeLabel}
                      </span>
                    </div>

                    <div className="mt-5">
                      <div className="group w-full cursor-default rounded-[1.8rem] border border-white/80 bg-white/60 p-5 text-left shadow-sm backdrop-blur-sm">
                        <div className="flex items-start justify-between gap-4">
                          <div className="flex min-w-0 gap-4">
                            <div className="relative shrink-0">
                              <MentorIdentityAvatar
                                userId={profile.userId}
                                displayName={heroName}
                                avatarUrl={profile.avatarConfigured ? profile.avatarUrl : null}
                                className="h-14 w-14 shadow-sm"
                                fallbackClassName="bg-gradient-to-br from-indigo-100 to-indigo-50 text-indigo-700"
                                textClassName="text-lg"
                                fallbackLabel={avatarLabel}
                              />
                            </div>
                            <div className="min-w-0">
                              <div className="font-bold text-slate-900">{heroName}</div>
                              <div className="mt-0.5 flex items-center gap-1.5 text-sm font-medium text-slate-600">
                                <BriefcaseBusiness size={14} className="shrink-0 opacity-60" />
                                <span className="truncate">{previewRoleText}</span>
                              </div>
                              <p className="mt-3 line-clamp-2 text-base leading-7 text-slate-500">{visibleBio}</p>
                            </div>
                          </div>
                          <div className="shrink-0 text-right">
                            <div className="text-xl font-black text-slate-900">{formatMoneyFen(previewStartingPriceFen)}</div>
                            <div className="mt-0.5 text-sm text-slate-500">
                              {previewPrimaryPackage
                                ? `${previewPrimaryPackage.packageName} · ${previewPrimaryPackage.deliveryMode === "APPOINTMENT"
                                  ? `${previewPrimaryPackage.durationMinutes ?? 45} 分钟预约`
                                  : "图文异步服务"}`
                                : "支持按套餐选择服务方式"}
                            </div>
                          </div>
                        </div>

                        <div className="mt-4 flex items-center gap-2 overflow-hidden whitespace-nowrap">
                          {previewListServiceScenes.map((tag) => (
                            <span key={tag} className="shrink-0 rounded-md bg-slate-100 px-2.5 py-1 text-sm font-medium text-slate-600">
                              {tag}
                            </span>
                          ))}
                          {previewListExpertiseTags.map((tag) => (
                            <span key={tag} className="shrink-0 rounded-md border border-slate-200 px-2.5 py-1 text-sm font-medium text-slate-500">
                              {tag}
                            </span>
                          ))}
                        </div>

                        <div className="mt-5 flex items-center justify-between border-t border-slate-100 pt-4">
                          <div className="flex items-center gap-4 text-sm font-medium text-slate-600">
                            <div className="flex items-center text-amber-500">
                              <Star size={16} fill="currentColor" className="mr-1" />
                              <span className="text-slate-700">{profile.avgRating?.toFixed(1) ?? "—"}</span>
                            </div>
                            <div className="flex items-center">
                              <MessageSquare size={16} className="mr-1.5 opacity-60" />
                              {profile.totalOrders} 次咨询
                            </div>
                          </div>
                          <div className="inline-flex h-10 w-10 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-300 shadow-sm">
                            <Heart size={16} />
                          </div>
                        </div>
                      </div>
                    </div>
                  </section>

                  <section className="rounded-[1.8rem] border border-slate-200 bg-white p-6 shadow-sm">
                    <div className="flex items-start justify-between gap-3">
                      <div className="flex min-h-[4.1rem] flex-col justify-center">
                        <h4 className="text-xl font-bold leading-tight text-slate-950">导师详情卡预览</h4>
                        <p className="mt-1 text-sm leading-7 text-slate-500">
                          这里展示的是学生点进详情后看到的关键信息，方便你检查内容是否完整、节奏是否舒服。
                        </p>
                      </div>
                      <span className={joinClasses("rounded-full border px-3 py-1 text-xs font-bold", previewStatus.badgeClassName)}>
                        {previewStatus.badgeLabel}
                      </span>
                    </div>

                    <div className="mt-5 overflow-hidden rounded-[2.2rem] border border-white/80 bg-white/90 shadow-[0_30px_80px_rgba(15,23,42,0.12)] backdrop-blur-xl">
                      <div className="relative h-32 shrink-0 bg-gradient-to-br from-indigo-500 via-purple-500 to-amber-400 opacity-90">
                        <div className="absolute inset-0 bg-black/10 mix-blend-overlay" />
                      </div>

                      <div className="relative px-6 pb-6 pt-0">
                        <div className="-mt-10 mb-4 flex items-end justify-between">
                          <MentorIdentityAvatar
                            userId={profile.userId}
                            displayName={heroName}
                            avatarUrl={profile.avatarConfigured ? profile.avatarUrl : null}
                            className="h-20 w-20 rounded-2xl shadow-lg"
                            imageClassName="rounded-2xl"
                            fallbackClassName="bg-slate-100 text-slate-500"
                            textClassName="text-2xl"
                            fallbackLabel={avatarLabel}
                          />
                          <div className="mb-1">
                            <div className="inline-flex h-10 w-10 items-center justify-center rounded-full border border-slate-200 bg-white/80 text-slate-300 shadow-sm backdrop-blur-md">
                              <Heart size={16} />
                            </div>
                          </div>
                        </div>

                        <div>
                          <h2 className="text-2xl font-black text-slate-900">{heroName}</h2>
                          <div className="mt-1 text-base font-semibold text-slate-600">{previewRoleText}</div>
                        </div>

                        <div className="mt-4 flex flex-wrap gap-2">
                          {formData.expertiseTags.map((tag) => (
                            <span key={tag} className="rounded-md border border-slate-200 bg-slate-50 px-2.5 py-1 text-sm font-semibold text-slate-600">
                              {tag}
                            </span>
                          ))}
                          {previewListServiceScenes.map((tag) => (
                            <span key={tag} className="rounded-md bg-indigo-50 px-2.5 py-1 text-sm font-semibold text-indigo-600">
                              {tag}
                            </span>
                          ))}
                        </div>

                        <p className="mt-5 text-base leading-8 text-slate-600">
                          {visibleBio}
                        </p>

                        <div className="mt-5 grid gap-3">
                          <div className="grid gap-3 sm:grid-cols-2">
                            <div className="rounded-2xl border border-emerald-100 bg-emerald-50/70 p-4">
                              <div className="text-sm font-bold text-emerald-600">适合咨询</div>
                              <div className="mt-2 text-base leading-7 text-slate-700">
                                {previewSuitableFor}
                              </div>
                            </div>
                            <div className="rounded-2xl border border-amber-100 bg-amber-50/70 p-4">
                              <div className="text-sm font-bold text-amber-600">不太适合</div>
                              <div className="mt-2 text-base leading-7 text-slate-700">
                                {previewNotSuitableFor}
                              </div>
                            </div>
                          </div>

                          <div className="grid gap-3 sm:grid-cols-2">
                            <div className="rounded-2xl border border-slate-200 bg-slate-50/80 p-4">
                              <div className="text-sm font-bold text-slate-600">建议提前准备</div>
                              <div className="mt-2 text-base leading-7 text-slate-700">
                                {previewPrepMaterials}
                              </div>
                            </div>
                            <div className="rounded-2xl border border-indigo-100 bg-indigo-50/80 p-4">
                              <div className="text-sm font-bold text-indigo-600">回复节奏</div>
                              <div className="mt-2 text-base leading-7 text-slate-700">
                                {previewReplyRhythm}
                              </div>
                            </div>
                          </div>
                        </div>

                        <div className="mt-6 rounded-2xl border border-slate-100 bg-slate-50 p-4">
                          <div className="mb-3 flex items-center justify-between">
                            <span className="text-sm font-bold  text-slate-400">最新评价</span>
                            <div className="flex items-center text-sm font-bold text-amber-500">
                              <Star size={12} fill="currentColor" className="mr-1" />
                              {profile.avgRating?.toFixed(1) ?? "—"}
                            </div>
                          </div>
                          <div className="text-sm italic text-slate-400">暂无公开评价</div>
                        </div>
                      </div>

                      <div className="shrink-0 border-t border-slate-100 bg-white/95 p-6 backdrop-blur-md">
                        <div className="mb-4 flex items-center justify-between">
                          <div>
                            <div className="text-sm font-bold  text-slate-400">套餐起步价</div>
                            <div className="mt-0.5 text-2xl font-black text-slate-900">{formatMoneyFen(previewStartingPriceFen)}</div>
                          </div>
                          <div className="text-right">
                            <div className="text-sm font-bold  text-slate-400">状态</div>
                            <div className={joinClasses("mt-1 text-base font-bold", previewStatus.available ? "text-emerald-500" : "text-amber-500")}>
                              {previewStatus.available ? "近期可预约" : "排期较满"}
                            </div>
                          </div>
                        </div>

                        <div className="group flex w-full items-center justify-center rounded-2xl bg-gradient-to-r from-indigo-600 to-indigo-500 px-5 py-4 text-base font-bold text-white shadow-[0_15px_30px_rgba(79,70,229,0.3)]">
                          <FileText size={18} className="mr-2 opacity-80" />
                          生成咨询准备单
                          <ChevronRight size={18} className="ml-1 opacity-60" />
                        </div>
                        <p className="mt-3 text-center text-sm text-slate-400">先准备问题，后决定是否下单</p>
                      </div>
                    </div>
                  </section>
                </div>
              </motion.div>
            )}

            {activeTab === "certification" && (
              <motion.div
                key="certification"
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -12 }}
                className="space-y-6"
              >
                <div className="mx-auto max-w-3xl rounded-[2rem] border border-white/70 bg-white/80 p-8 text-center shadow-[0_15px_40px_rgba(15,23,42,0.04)] backdrop-blur-xl">
                  <div className={joinClasses("mx-auto mb-5 flex h-20 w-20 items-center justify-center rounded-full", approvalMeta.panelClassName)}>
                    <ShieldCheck size={40} className="text-emerald-500" />
                  </div>
                  <h2 className="mb-2 text-2xl font-bold text-slate-900">{approvalMeta.title}</h2>
                  <p className="mx-auto mb-8 max-w-2xl text-slate-500">{approvalMeta.description}</p>

                  <div className="rounded-2xl border border-slate-100 bg-slate-50 p-5 text-left">
                    <div className="mb-3 text-base font-bold text-slate-800">当前认证资料</div>
                    {currentSubmission ? (
                      <div className="space-y-4">
                        <div className="grid gap-3 md:grid-cols-2">
                          <SummaryLine label="认证姓名" value={currentSubmission.realName} />
                          <SummaryLine label="所在公司" value={currentSubmission.companyName} />
                          <SummaryLine label="当前职位" value={currentSubmission.jobTitle} />
                          <SummaryLine label="提交时间" value={formatDateTime(currentSubmission.submittedAt)} />
                        </div>
                        <div className="rounded-xl border border-white bg-white px-4 py-3">
                          <div className="text-sm font-bold text-slate-600">审核备注</div>
                          <div className="mt-2 text-sm leading-7 text-slate-600">
                            {currentSubmission.reviewNote || "当前还没有补充说明。"}
                          </div>
                        </div>
                        <div className="space-y-2">
                          {currentSubmission.assets.length > 0 ? (
                            currentSubmission.assets.map((asset) => (
                              <div key={asset.assetId} className="flex items-center justify-between gap-4 rounded-xl border border-white bg-white px-4 py-3">
                                <div className="min-w-0">
                                  <div className="truncate text-sm font-semibold text-slate-900">{asset.originalFilename}</div>
                                  <div className="mt-1 text-xs text-slate-500">
                                    {formatFileSize(asset.sizeBytes)} · {formatAssetLifecycleStatus(asset.lifecycleStatus)} · {formatDateTime(asset.uploadedAt)}
                                  </div>
                                </div>
                                <button
                                  type="button"
                                  onClick={() => void handleOpenAsset(asset)}
                                  disabled={assetLoadingId === asset.assetId}
                                  className="inline-flex items-center rounded-full border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-600 transition-colors hover:border-emerald-200 hover:text-emerald-600 disabled:cursor-not-allowed disabled:opacity-60"
                                >
                                  {assetLoadingId === asset.assetId ? <RefreshCw size={14} className="mr-2 animate-spin" /> : <Eye size={14} className="mr-2" />}
                                  查看
                                </button>
                              </div>
                            ))
                          ) : (
                            <div className="rounded-xl border border-dashed border-slate-200 bg-white px-4 py-4 text-sm text-slate-500">
                              当前这次提交还没有可查看的附件。
                            </div>
                          )}
                        </div>
                      </div>
                    ) : (
                      <div className="rounded-xl border border-dashed border-slate-200 bg-white px-4 py-4 text-sm text-slate-500">
                        你还没有提交认证资料，先补充材料后再来查看。
                      </div>
                    )}
                  </div>

                  <button
                    type="button"
                    onClick={handleOpenCertificationModal}
                    className="mt-8 flex w-full items-center justify-center text-sm font-semibold text-slate-500 transition-colors hover:text-emerald-600"
                  >
                    <History size={16} className="mr-1.5" />
                    查看提交记录或更新资料
                  </button>
                </div>
              </motion.div>
            )}

            {activeTab === "notifications" && (
              <motion.div
                key="notifications"
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -12 }}
                className="space-y-6"
              >
                <LazyProfileNotificationPreferencesPanel
                  variant="mentor"
                  title="通知提醒"
                  description="按你的节奏接收提醒，重要消息会正常送达。"
                />
              </motion.div>
            )}

            {activeTab === "schedule" && (
              <motion.div
                key="schedule"
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -12 }}
                className="space-y-6"
              >
                <div className="rounded-[2rem] border border-white/70 bg-white/80 p-8 shadow-[0_15px_40px_rgba(15,23,42,0.04)] backdrop-blur-xl">
                  <div className="mb-6 flex items-center justify-between">
                    <div>
                      <h2 className="flex items-center text-xl font-bold text-slate-900">
                        <CalendarDays size={20} className="mr-2 text-teal-500" />
                        近期可预约时段
                      </h2>
                      <p className="mt-1 text-sm text-slate-500">把近期方便咨询的时间安排在这里，已被预约的时段会自动锁定。</p>
                      <p className="mt-2 text-sm leading-7 text-slate-500">
                        {enabledAppointmentPackage
                          ? `当前预约服务为「${enabledAppointmentPackage.packageName}」，建议按 ${enabledAppointmentPackage.durationMinutes ?? 45} 分钟安排时段。`
                          : "先启用预约服务，再开放可预约时段。"}
                      </p>
                    </div>
                    <div className="flex flex-wrap gap-2">
                      <button
                        type="button"
                        onClick={handleOpenRecurringScheduleModal}
                        className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-bold text-slate-700 shadow-sm transition-colors hover:border-slate-300 hover:bg-slate-50"
                      >
                        固定每周生成
                      </button>
                      <button
                        type="button"
                        onClick={handleOpenSpecialDateModal}
                        className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-bold text-slate-700 shadow-sm transition-colors hover:border-slate-300 hover:bg-slate-50"
                      >
                        特殊日期调整
                      </button>
                      <button
                        type="button"
                        onClick={handleOpenScheduleModal}
                        className="rounded-xl bg-slate-900 px-4 py-2 text-sm font-bold text-white shadow-sm transition-colors hover:bg-slate-800"
                      >
                        新增可约时段
                      </button>
                    </div>
                  </div>

                  {scheduleRecords.length > 0 ? (
                    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
                      {scheduleRecords.map((slot) => {
                        const durationMinutes = formatDurationMinutes(slot.startAt, slot.endAt);
                        const isAvailable = slot.status === "AVAILABLE";
                        return (
                          <div
                            key={slot.id}
                            className={joinClasses(
                              "relative flex h-36 flex-col justify-between overflow-hidden rounded-2xl border p-4",
                              isAvailable ? "border-emerald-200 bg-emerald-50" : "border-slate-200 bg-slate-50",
                            )}
                          >
                            <div className={joinClasses(
                              "absolute right-0 top-0 rounded-bl-lg px-2 py-1 text-[10px] font-bold text-white",
                              isAvailable ? "bg-emerald-500" : "bg-slate-400",
                            )}>
                              {formatScheduleStatusLabel(slot.status)}
                            </div>
                            <div>
                              <div className="text-lg font-bold text-slate-800">{formatScheduleCardTime(slot.startAt)}</div>
                              <div className="mt-1 text-xs font-medium text-slate-500">
                                时长：{durationMinutes > 0 ? `${durationMinutes} 分钟` : "待确认"}
                              </div>
                              {!isAvailable && (
                                <div className="mt-2 text-xs text-slate-500">
                                  已被预约{slot.bookedOrderNo ? ` · 订单 ${slot.bookedOrderNo}` : ""}
                                </div>
                              )}
                            </div>
                            <div className="mt-auto flex gap-2">
                              {isAvailable ? (
                                <button
                                  type="button"
                                  onClick={() => void handleDeleteSlot(slot.id)}
                                  disabled={deletingSlotId === slot.id}
                                  className="text-xs font-bold text-rose-500 transition-colors hover:text-rose-600 disabled:cursor-not-allowed disabled:opacity-60"
                                >
                                  {deletingSlotId === slot.id ? "删除中..." : "删除"}
                                </button>
                              ) : (
                                <span className="text-xs font-bold text-slate-400">已锁定</span>
                              )}
                            </div>
                          </div>
                        );
                      })}

                      <button
                        type="button"
                        onClick={handleOpenRecurringScheduleModal}
                        className="group flex h-36 flex-col items-center justify-center rounded-2xl border border-dashed border-slate-300 p-4 text-slate-400 transition-colors hover:border-emerald-300 hover:bg-slate-50 hover:text-emerald-500"
                      >
                        <CalendarDays size={24} className="mb-2 transition-transform group-hover:scale-110" />
                        <span className="text-sm font-semibold">固定每周生成</span>
                      </button>

                      <button
                        type="button"
                        onClick={handleOpenSpecialDateModal}
                        className="group flex h-36 flex-col items-center justify-center rounded-2xl border border-dashed border-slate-300 p-4 text-slate-400 transition-colors hover:border-emerald-300 hover:bg-slate-50 hover:text-emerald-500"
                      >
                        <CalendarClock size={24} className="mb-2 transition-transform group-hover:scale-110" />
                        <span className="text-sm font-semibold">特殊日期调整</span>
                      </button>

                      <button
                        type="button"
                        onClick={handleOpenScheduleModal}
                        className="group flex h-36 flex-col items-center justify-center rounded-2xl border border-dashed border-slate-300 p-4 text-slate-400 transition-colors hover:border-emerald-300 hover:bg-slate-50 hover:text-emerald-500"
                      >
                        <Plus size={24} className="mb-2 transition-transform group-hover:scale-110" />
                        <span className="text-sm font-semibold">添加更多时段</span>
                      </button>
                    </div>
                  ) : (
                    <div className="rounded-[1.6rem] border border-dashed border-slate-200 bg-slate-50/70 px-6 py-10 text-center">
                      <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-white text-teal-500 shadow-sm">
                        <CalendarClock size={24} />
                      </div>
                      <h3 className="mt-4 text-lg font-semibold text-slate-900">还没有配置可预约时段</h3>
                      <p className="mt-2 text-sm leading-7 text-slate-500">
                        先补充几个明确的可约时间，学生在下单前就能更快判断你最近是否方便接单。
                      </p>
                      <div className="mt-6 flex flex-wrap items-center justify-center gap-3">
                        <button
                          type="button"
                          onClick={handleOpenRecurringScheduleModal}
                          className="inline-flex items-center rounded-full border border-slate-200 bg-white px-5 py-2.5 text-sm font-bold text-slate-700 transition-colors hover:border-slate-300 hover:bg-slate-50"
                        >
                          <CalendarDays size={14} className="mr-2" />
                          固定每周生成
                        </button>
                        <button
                          type="button"
                          onClick={handleOpenSpecialDateModal}
                          className="inline-flex items-center rounded-full border border-slate-200 bg-white px-5 py-2.5 text-sm font-bold text-slate-700 transition-colors hover:border-slate-300 hover:bg-slate-50"
                        >
                          <CalendarClock size={14} className="mr-2" />
                          特殊日期调整
                        </button>
                        <button
                          type="button"
                          onClick={handleOpenScheduleModal}
                          className="inline-flex items-center rounded-full bg-slate-900 px-5 py-2.5 text-sm font-bold text-white transition-colors hover:bg-slate-800"
                        >
                          <Plus size={14} className="mr-2" />
                          新增第一个可约时段
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </main>

      <AnimatePresence>
        {certModalOpen && certForm && (
          <ModalOverlay onClose={() => !certForm.busy && setCertModalOpen(false)} maxWidthClassName="max-w-2xl">
            <div className="mb-6 text-center">
              <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl border border-emerald-100 bg-emerald-50 text-emerald-500 shadow-sm">
                <History size={28} />
              </div>
              <h3 className="text-2xl font-bold text-slate-900">认证提交记录</h3>
              <p className="mt-2 text-sm font-medium text-slate-500">
                查看过往提交记录，也可以在这里更新认证资料。
              </p>
            </div>

            <div className="space-y-6">
              <div className="relative ml-3 max-h-[24rem] space-y-5 overflow-y-auto border-l-2 border-emerald-100 pl-5 pr-2">
                {(certification?.submissions?.length ?? 0) > 0 ? (
                  certification?.submissions.map((submission) => (
                    <div key={submission.submissionId} className="relative">
                      <div className={joinClasses(
                        "absolute -left-[1.65rem] top-1 h-4 w-4 rounded-full border-4 border-white shadow-sm",
                        submission.current ? "bg-emerald-500" : "bg-slate-300",
                      )} />
                      <div className="text-sm font-bold text-slate-900">
                        {submission.status === "APPROVED" ? "审核通过" : submission.status === "REJECTED" ? "需要补充资料" : "等待审核"}
                      </div>
                      <div className="mt-1 text-xs text-slate-500">{formatDateTime(submission.submittedAt)}</div>
                      <div className="mt-2 rounded-xl border border-slate-100 bg-slate-50/80 p-3 text-sm text-slate-600">
                        <div>{submission.companyName || "未填写公司"} · {submission.jobTitle || "未填写职位"}</div>
                        <div className="mt-1">{submission.reviewNote || "当前还没有补充说明。"}</div>
                      </div>
                    </div>
                  ))
                ) : (
                  <div className="rounded-xl border border-dashed border-slate-200 bg-slate-50/80 p-4 text-sm text-slate-500">
                    你还没有历史提交记录，下面可以直接补充第一版认证资料。
                  </div>
                )}
              </div>

              <div className="space-y-4 border-t border-slate-100 pt-4">
                <div className="grid gap-4 md:grid-cols-2">
                  <EditableField
                    label="真实姓名"
                    icon={User}
                    value={certForm.realName}
                    isEditing={false}
                    onChange={() => {}}
                    helper="如需更正，请联系管理员处理。"
                  />
                  <EditableField
                    label="所在公司"
                    icon={Building2}
                    value={certForm.companyName}
                    isEditing={false}
                    onChange={() => {}}
                    helper="如需更正，请联系管理员处理。"
                  />
                </div>
                <EditableField
                  label="当前职位"
                  icon={BriefcaseBusiness}
                  value={certForm.jobTitle}
                  isEditing
                  onChange={(value) => setCertForm({ ...certForm, jobTitle: value })}
                />

                <div className="rounded-[1.45rem] border-2 border-dashed border-slate-200 bg-slate-50 px-5 py-5">
                  <label className="flex items-center text-sm font-bold text-slate-700">
                    <UploadCloud size={16} className="mr-2 text-emerald-500" />
                    认证材料文件
                  </label>
                  <div className="mt-3 rounded-2xl border border-white bg-white px-4 py-4">
                    <input type="file" onChange={handleCertificationFileChange} className="block w-full text-sm text-slate-600 file:mr-4 file:rounded-full file:border-0 file:bg-slate-900 file:px-4 file:py-2 file:text-sm file:font-semibold file:text-white hover:file:bg-slate-800" />
                    <div className="mt-3 text-xs text-slate-500">
                      {certForm.file ? `已选择：${certForm.file.name}` : "可上传在职证明、工牌、履历截图或其他能证明导师身份的材料。"}
                    </div>
                  </div>
                </div>

                <button
                  type="button"
                  disabled={certForm.busy}
                  onClick={() => void handleSubmitCertification()}
                  className="inline-flex w-full items-center justify-center rounded-xl bg-slate-900 px-5 py-3.5 text-sm font-bold text-white shadow-md transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-70"
                >
                  {certForm.busy ? (
                    <>
                      <RefreshCw size={16} className="mr-2 animate-spin" />
                      正在提交
                    </>
                  ) : (
                    <>
                      <UploadCloud size={16} className="mr-2" />
                      提交更新后的认证资料
                    </>
                  )}
                </button>
                <p className="text-center text-xs text-slate-400">
                  提交后平台会按最新资料重新审核，你的公开状态也会随之更新。
                </p>
              </div>
            </div>
          </ModalOverlay>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {recurringScheduleModalOpen && (
          <ModalOverlay onClose={() => !recurringScheduleForm.busy && setRecurringScheduleModalOpen(false)} maxWidthClassName="max-w-2xl">
            <div className="mb-6 text-center">
              <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl border border-teal-100 bg-teal-50 text-teal-500 shadow-sm">
                <CalendarDays size={28} />
              </div>
              <h3 className="text-2xl font-bold text-slate-900">固定排期批量调整</h3>
              <p className="mt-2 text-sm font-medium text-slate-500">
                适合把固定时段一次性铺到接下来几周，也可以直接覆盖或清空一批未来日期。
              </p>
            </div>

            <div className="space-y-5">
              <div>
                <label className="mb-2 block text-sm font-bold text-slate-700">这次怎么处理</label>
                <div className="grid gap-3 md:grid-cols-3">
                  {RECURRING_SCHEDULE_MODE_OPTIONS.map((option) => {
                    const selected = recurringScheduleForm.mode === option.id;
                    return (
                      <button
                        key={option.id}
                        type="button"
                        onClick={() => setRecurringScheduleForm((current) => ({ ...current, mode: option.id }))}
                        className={joinClasses(
                          "rounded-[1.4rem] border px-4 py-4 text-left transition-colors",
                          selected
                            ? "border-slate-900 bg-slate-900 text-white shadow-sm"
                            : "border-slate-200 bg-white text-slate-700 hover:border-slate-300 hover:bg-slate-50",
                        )}
                      >
                        <div className="text-sm font-bold">{option.label}</div>
                        <div className={joinClasses("mt-2 text-xs leading-6", selected ? "text-white/80" : "text-slate-500")}>
                          {option.description}
                        </div>
                      </button>
                    );
                  })}
                </div>
              </div>

              <div className="grid gap-4 md:grid-cols-2">
                <div>
                  <label className="mb-2 block text-sm font-bold text-slate-700">从哪一天开始</label>
                  <input
                    type="date"
                    value={recurringScheduleForm.startDate}
                    onChange={(event) => setRecurringScheduleForm((current) => ({ ...current, startDate: event.target.value }))}
                    className="w-full rounded-xl border-2 border-slate-200 bg-white px-4 py-3 text-sm font-semibold text-slate-700 outline-none transition-colors focus:border-teal-400"
                  />
                </div>
                <div>
                  <label className="mb-2 block text-sm font-bold text-slate-700">持续周数</label>
                  <select
                    value={recurringScheduleForm.weeks}
                    onChange={(event) => setRecurringScheduleForm((current) => ({ ...current, weeks: Number(event.target.value) }))}
                    className="w-full rounded-xl border-2 border-slate-200 bg-white px-4 py-3 text-sm font-semibold text-slate-700 outline-none transition-colors focus:border-teal-400"
                  >
                    {RECURRING_WEEKS_OPTIONS.map((weeks) => (
                      <option key={weeks} value={weeks}>{weeks} 周</option>
                    ))}
                  </select>
                </div>
              </div>

              <div>
                <label className="mb-2 block text-sm font-bold text-slate-700">固定周几</label>
                <div className="flex flex-wrap gap-2">
                  {WEEKDAY_OPTIONS.map((item) => {
                    const selected = recurringScheduleForm.weekdays.includes(item.value);
                    return (
                      <button
                        key={item.value}
                        type="button"
                        onClick={() => setRecurringScheduleForm((current) => ({
                          ...current,
                          weekdays: selected
                            ? current.weekdays.filter((weekday) => weekday !== item.value)
                            : [...current.weekdays, item.value].sort((left, right) => left - right),
                        }))}
                        className={joinClasses(
                          "rounded-full border px-4 py-2 text-sm font-semibold transition-colors",
                          selected
                            ? "border-slate-900 bg-slate-900 text-white"
                            : "border-slate-200 bg-white text-slate-600 hover:border-slate-300 hover:text-slate-900",
                        )}
                      >
                        {item.label}
                      </button>
                    );
                  })}
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="mb-2 block text-sm font-bold text-slate-700">固定时间</label>
                  <input
                    type="time"
                    value={recurringScheduleForm.time}
                    onChange={(event) => setRecurringScheduleForm((current) => ({ ...current, time: event.target.value }))}
                    className="w-full rounded-xl border-2 border-slate-200 bg-white px-4 py-3 text-sm font-semibold text-slate-700 outline-none transition-colors focus:border-teal-400"
                  />
                </div>
                <div>
                  <label className="mb-2 block text-sm font-bold text-slate-700">单次时长</label>
                  <select
                    value={recurringScheduleForm.durationMinutes}
                    onChange={(event) => setRecurringScheduleForm((current) => ({ ...current, durationMinutes: Number(event.target.value) }))}
                    className="w-full rounded-xl border-2 border-slate-200 bg-white px-4 py-3 text-sm font-semibold text-slate-700 outline-none transition-colors focus:border-teal-400"
                  >
                    {DURATION_OPTIONS.map((duration) => (
                      <option key={duration} value={duration}>{duration} 分钟</option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="rounded-[1.2rem] border border-sky-100 bg-sky-50/80 px-4 py-4 text-sm leading-7 text-sky-900">
                {recurringScheduleForm.mode === "CREATE"
                  ? "按当前规则补充未来可预约时段，已存在的时段会自动跳过。"
                  : recurringScheduleForm.mode === "REPLACE"
                    ? "按当前规则重排所选日期，已预约时段会自动保留。"
                    : "清空所选日期中未被预约的时段，已预约时段会保留。"}
              </div>

              <button
                type="button"
                disabled={recurringScheduleForm.busy}
                onClick={() => void handleCreateRecurringSchedule()}
                className="inline-flex w-full items-center justify-center rounded-xl bg-slate-900 px-5 py-3.5 text-sm font-bold text-white shadow-md transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-70"
              >
                {recurringScheduleForm.busy ? (
                  <>
                    <RefreshCw size={16} className="mr-2 animate-spin" />
                    正在处理
                  </>
                ) : (
                  recurringScheduleForm.mode === "CREATE"
                    ? "确认补充固定排期"
                    : recurringScheduleForm.mode === "REPLACE"
                      ? "确认覆盖这些日期"
                      : "确认清空这些日期"
                )}
              </button>
            </div>
          </ModalOverlay>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {scheduleModalOpen && (
          <ModalOverlay onClose={() => !scheduleForm.busy && setScheduleModalOpen(false)}>
            <div className="mb-6 text-center">
              <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl border border-teal-100 bg-teal-50 text-teal-500 shadow-sm">
                <CalendarClock size={28} />
              </div>
              <h3 className="text-2xl font-bold text-slate-900">新增可约时段</h3>
              <p className="mt-2 text-sm font-medium text-slate-500">
                先补充一个方便咨询的时间，学生下单时就能看到。
              </p>
            </div>

            <div className="space-y-5">
              <div>
                <label className="mb-2 block text-sm font-bold text-slate-700">咨询日期</label>
                <input
                  type="date"
                  value={scheduleForm.date}
                  onChange={(event) => setScheduleForm((current) => ({ ...current, date: event.target.value }))}
                  className="w-full rounded-xl border-2 border-slate-200 bg-white px-4 py-3 text-sm font-semibold text-slate-700 outline-none transition-colors focus:border-teal-400"
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="mb-2 block text-sm font-bold text-slate-700">起始时间</label>
                  <input
                    type="time"
                    value={scheduleForm.time}
                    onChange={(event) => setScheduleForm((current) => ({ ...current, time: event.target.value }))}
                    className="w-full rounded-xl border-2 border-slate-200 bg-white px-4 py-3 text-sm font-semibold text-slate-700 outline-none transition-colors focus:border-teal-400"
                  />
                </div>
                <div>
                  <label className="mb-2 block text-sm font-bold text-slate-700">单次时长</label>
                  <select
                    value={scheduleForm.durationMinutes}
                    onChange={(event) => setScheduleForm((current) => ({ ...current, durationMinutes: Number(event.target.value) }))}
                    className="w-full rounded-xl border-2 border-slate-200 bg-white px-4 py-3 text-sm font-semibold text-slate-700 outline-none transition-colors focus:border-teal-400"
                  >
                    {DURATION_OPTIONS.map((duration) => (
                      <option key={duration} value={duration}>{duration} 分钟</option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="rounded-[1.2rem] border border-sky-100 bg-sky-50/80 px-4 py-4 text-sm leading-7 text-sky-900">
                保存后，这段时间会同步到你的可预约时间里，学生下单时会按这里发起预约。
              </div>

              <button
                type="button"
                disabled={scheduleForm.busy}
                onClick={() => void handleCreateSchedule()}
                className="inline-flex w-full items-center justify-center rounded-xl bg-slate-900 px-5 py-3.5 text-sm font-bold text-white shadow-md transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-70"
              >
                {scheduleForm.busy ? (
                  <>
                    <RefreshCw size={16} className="mr-2 animate-spin" />
                    正在保存
                  </>
                ) : (
                  "确认添加"
                )}
              </button>
            </div>
          </ModalOverlay>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {specialDateModalOpen && (
          <ModalOverlay
            onClose={() => !(specialDateForm.clearBusy || specialDateForm.addBusy) && setSpecialDateModalOpen(false)}
            maxWidthClassName="max-w-3xl"
          >
            <div className="mb-6 text-center">
              <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl border border-amber-100 bg-amber-50 text-amber-500 shadow-sm">
                <CalendarClock size={28} />
              </div>
              <h3 className="text-2xl font-bold text-slate-900">特殊日期调整</h3>
              <p className="mt-2 text-sm font-medium text-slate-500">
                适合临时请假、加开额外时段，或者单独调整某一天的排期。
              </p>
            </div>

            <div className="space-y-6">
              <div className="grid gap-4 md:grid-cols-[minmax(0,1fr),minmax(0,1.2fr)]">
                <div className="rounded-[1.5rem] border border-slate-200 bg-slate-50/70 p-5">
                  <label className="mb-2 block text-sm font-bold text-slate-700">选择日期</label>
                  <input
                    type="date"
                    value={specialDateForm.date}
                    onChange={(event) => setSpecialDateForm((current) => ({ ...current, date: event.target.value }))}
                    className="w-full rounded-xl border-2 border-slate-200 bg-white px-4 py-3 text-sm font-semibold text-slate-700 outline-none transition-colors focus:border-amber-400"
                  />

                  <div className="mt-5 rounded-[1.2rem] border border-white bg-white px-4 py-4">
                    <div className="text-sm font-bold text-slate-900">当天已有排期</div>
                    <div className="mt-3 space-y-2">
                      {specialDateSlots.length > 0 ? (
                        specialDateSlots.map((slot) => (
                          <div key={slot.id} className="flex items-center justify-between rounded-xl border border-slate-100 px-3 py-2.5">
                            <div>
                              <div className="text-sm font-semibold text-slate-800">{formatScheduleCardTime(slot.startAt)}</div>
                              <div className="mt-1 text-xs text-slate-500">
                                {formatDurationMinutes(slot.startAt, slot.endAt)} 分钟 · {formatScheduleStatusLabel(slot.status)}
                              </div>
                            </div>
                            <div className={joinClasses(
                              "rounded-full px-2.5 py-1 text-xs font-bold",
                              slot.status === "AVAILABLE" ? "bg-emerald-50 text-emerald-600" : "bg-slate-100 text-slate-600",
                            )}>
                              {slot.status === "AVAILABLE" ? "可调整" : "已锁定"}
                            </div>
                          </div>
                        ))
                      ) : (
                        <div className="rounded-xl border border-dashed border-slate-200 bg-slate-50 px-3 py-4 text-sm text-slate-500">
                          这一天当前还没有排期，你可以直接补一个临时时段。
                        </div>
                      )}
                    </div>

                    <button
                      type="button"
                      disabled={specialDateForm.clearBusy || specialDateForm.addBusy}
                      onClick={() => void handleClearSpecialDate()}
                      className="mt-4 inline-flex w-full items-center justify-center rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm font-bold text-rose-600 transition-colors hover:bg-rose-100 disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      {specialDateForm.clearBusy ? (
                        <>
                          <RefreshCw size={16} className="mr-2 animate-spin" />
                          正在清理
                        </>
                      ) : (
                        "清空当天未预约时段"
                      )}
                    </button>
                    <p className="mt-2 text-xs leading-6 text-slate-400">
                      已被学生预约的时段会自动保留，不会在这里被误删。
                    </p>
                  </div>
                </div>

                <div className="rounded-[1.5rem] border border-slate-200 bg-white p-5 shadow-sm">
                  <div className="text-sm font-bold text-slate-900">补一个临时时段</div>
                  <p className="mt-1 text-sm leading-7 text-slate-500">
                    适合临时加开咨询，或者把固定排期之外的补充时间放进来。
                  </p>

                  <div className="mt-5 grid grid-cols-2 gap-4">
                    <div>
                      <label className="mb-2 block text-sm font-bold text-slate-700">开始时间</label>
                      <input
                        type="time"
                        value={specialDateForm.time}
                        onChange={(event) => setSpecialDateForm((current) => ({ ...current, time: event.target.value }))}
                        className="w-full rounded-xl border-2 border-slate-200 bg-white px-4 py-3 text-sm font-semibold text-slate-700 outline-none transition-colors focus:border-amber-400"
                      />
                    </div>
                    <div>
                      <label className="mb-2 block text-sm font-bold text-slate-700">单次时长</label>
                      <select
                        value={specialDateForm.durationMinutes}
                        onChange={(event) => setSpecialDateForm((current) => ({ ...current, durationMinutes: Number(event.target.value) }))}
                        className="w-full rounded-xl border-2 border-slate-200 bg-white px-4 py-3 text-sm font-semibold text-slate-700 outline-none transition-colors focus:border-amber-400"
                      >
                        {DURATION_OPTIONS.map((duration) => (
                          <option key={duration} value={duration}>{duration} 分钟</option>
                        ))}
                      </select>
                    </div>
                  </div>

                  <div className="mt-5 rounded-[1.2rem] border border-amber-100 bg-amber-50/70 px-4 py-4 text-sm leading-7 text-amber-900">
                    这里补充的是单独某一天的临时时段，不会影响其他日期已经排好的固定排期。
                  </div>

                  <button
                    type="button"
                    disabled={specialDateForm.clearBusy || specialDateForm.addBusy}
                    onClick={() => void handleAddSpecialDateSlot()}
                    className="mt-5 inline-flex w-full items-center justify-center rounded-xl bg-slate-900 px-5 py-3.5 text-sm font-bold text-white shadow-md transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-70"
                  >
                    {specialDateForm.addBusy ? (
                      <>
                        <RefreshCw size={16} className="mr-2 animate-spin" />
                        正在补充
                      </>
                    ) : (
                      "补充这个临时时段"
                    )}
                  </button>
                </div>
              </div>
            </div>
          </ModalOverlay>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {passwordModal.isOpen ? (
          <ModalOverlay
            onClose={() => {
              if (passwordModal.busy) {
                return;
              }
              setPasswordModal(createPasswordModalState());
              setPasswordCountdown(0);
            }}
          >
            <div className="mb-6 text-center">
              <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-rose-50 text-rose-500">
                <KeyRound size={32} />
              </div>
              <h3 className="text-2xl font-semibold text-slate-900">修改登录密码</h3>
              <p className="mt-2 text-sm font-medium text-slate-500">
                {passwordModal.step === "verifyCode"
                  ? `请先完成当前邮箱 ${email ?? "当前邮箱"} 的验证`
                  : "请设置新的登录密码"}
              </p>
            </div>

            <div className="space-y-5">
              {passwordModal.step === "verifyCode" ? (
                <div>
                  <label className="mb-2 block text-sm font-bold text-slate-700">验证码</label>
                  <div className="flex gap-3">
                    <input
                      type="text"
                      maxLength={6}
                      value={passwordModal.code}
                      onChange={(event) => setPasswordModal((current) => ({
                        ...current,
                        code: event.target.value.replace(/\D/g, ""),
                      }))}
                      placeholder="6位数字"
                      className="min-w-0 flex-1 rounded-xl border-2 border-slate-200 bg-slate-50 px-4 py-3 text-center text-sm font-semibold tracking-[0.28em] text-slate-700 outline-none transition-colors focus:border-emerald-400 focus:bg-white"
                    />
                    <button
                      type="button"
                      disabled={passwordCountdown > 0 || passwordModal.busy}
                      onClick={() => void handleSendPasswordCode()}
                      className="whitespace-nowrap rounded-xl border border-emerald-100 bg-emerald-50 px-4 py-3 text-sm font-semibold text-emerald-700 transition-colors hover:bg-emerald-100 disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      {passwordCountdown > 0 ? `${passwordCountdown}s 后重发` : "获取验证码"}
                    </button>
                  </div>
                </div>
              ) : (
                <div>
                  <label className="mb-2 block text-sm font-bold text-slate-700">新密码</label>
                  <input
                    type="password"
                    value={passwordModal.newPassword}
                    onChange={(event) => setPasswordModal((current) => ({ ...current, newPassword: event.target.value }))}
                    placeholder="至少 8 位，包含字母和数字"
                    className="w-full rounded-xl border-2 border-slate-200 bg-slate-50 px-4 py-3 text-sm font-medium text-slate-700 outline-none transition-colors focus:border-emerald-400 focus:bg-white"
                  />
                  <p className="mt-2 text-xs leading-6 text-slate-500">
                    新密码至少 8 位，并需同时包含字母和数字。
                  </p>
                </div>
              )}

              {passwordModal.debugHint ? (
                <InlineDebugCodeCard
                  targetEmail={passwordModal.debugHint.targetEmail}
                  code={passwordModal.debugHint.code}
                />
              ) : null}

              <button
                type="button"
                disabled={passwordModal.busy}
                onClick={() => {
                  if (passwordModal.step === "verifyCode") {
                    void handleVerifyPasswordCode();
                  } else {
                    void handleChangePassword();
                  }
                }}
                className="inline-flex w-full items-center justify-center rounded-xl bg-slate-900 px-5 py-3.5 text-sm font-semibold text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60"
              >
                {passwordModal.busy ? (
                  <>
                    <RefreshCw size={16} className="mr-2 animate-spin" />
                    正在处理
                  </>
                ) : passwordModal.step === "verifyCode" ? (
                  "验证并下一步"
                ) : (
                  "完成修改"
                )}
              </button>
            </div>
          </ModalOverlay>
        ) : null}
      </AnimatePresence>

      <AnimatePresence>
        {toast || floatingBarVisible ? (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="pointer-events-none fixed bottom-0 left-0 right-0 z-[60] p-4 sm:p-6"
          >
            <div className="mx-auto flex w-full max-w-3xl flex-col gap-3">
              {toast ? (
                <motion.div
                  key={`toast-${toast.tone}-${toast.message}`}
                  initial={{ y: 30, opacity: 0, scale: 0.97 }}
                  animate={{ y: 0, opacity: 1, scale: 1 }}
                  exit={{ y: 18, opacity: 0, scale: 0.98 }}
                  className="pointer-events-auto"
                >
                  <div className={joinClasses(
                    "mx-auto flex w-full items-center gap-4 overflow-hidden rounded-full px-6 py-3.5 shadow-2xl backdrop-blur-xl",
                    "transition-[background-color,border-color,box-shadow] duration-300 ease-out",
                    getToastPresentation(toast.tone).shellClassName,
                  )}>
                    <div className={joinClasses("flex h-10 w-10 shrink-0 items-center justify-center rounded-full transition-colors duration-300", getToastPresentation(toast.tone).iconClassName)}>
                      {(() => {
                        const ToastIcon = getToastPresentation(toast.tone).icon;
                        return <ToastIcon size={18} />;
                      })()}
                    </div>
                    <div className="min-w-0 flex-1 text-center">
                      <div className={joinClasses("truncate text-base font-black tracking-[0.02em]", getToastPresentation(toast.tone).titleClassName)}>
                        {toast.tone === "cancel" ? toast.message : getToastPresentation(toast.tone).title}
                      </div>
                      {toast.tone === "cancel" ? null : (
                        <p className={joinClasses("truncate text-[15px] leading-5", getToastPresentation(toast.tone).messageClassName)}>
                          {toast.message}
                        </p>
                      )}
                    </div>
                    {toast.tone === "cancel" ? (
                      <span aria-hidden="true" className="block h-8 w-8 shrink-0" />
                    ) : (
                      <button
                        type="button"
                        onClick={() => setToast(null)}
                        className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full border border-slate-200 bg-white/80 text-slate-500 transition-colors duration-300 hover:border-slate-300 hover:bg-white hover:text-slate-700"
                      >
                        <X size={15} />
                      </button>
                    )}
                  </div>
                </motion.div>
              ) : null}

              {floatingBarVisible ? (
                <motion.div
                  key="mentor-editor-floating-bar"
                  initial={{ y: 44, opacity: 0 }}
                  animate={{ y: 0, opacity: 1 }}
                  exit={{ y: 44, opacity: 0 }}
                  transition={{ type: "spring", stiffness: 300, damping: 30 }}
                  className="pointer-events-auto"
                >
                  <div className={joinClasses(
                    "w-full overflow-hidden rounded-full px-6 py-3.5 backdrop-blur-xl",
                    "transition-[background-color,border-color,box-shadow] duration-300 ease-out",
                    editorBarPresentation.shellClassName,
                  )}>
                    <div className="flex items-center justify-between gap-4">
                      <div className="flex min-w-0 items-center gap-3.5">
                        <div className={joinClasses("flex h-10 w-10 shrink-0 items-center justify-center rounded-full transition-colors duration-300", editorBarPresentation.iconClassName)}>
                          {(() => {
                            const EditorBarIcon = editorBarPresentation.icon;
                            return <EditorBarIcon size={17} />;
                          })()}
                        </div>
                        <div className="min-w-0 flex-1 overflow-hidden text-center">
                          <AnimatePresence mode="wait" initial={false}>
                            <motion.div
                              key={editorFloatingMessage}
                              initial={{ opacity: 0, y: 10 }}
                              animate={{ opacity: 1, y: 0 }}
                              exit={{ opacity: 0, y: -10 }}
                              transition={{ duration: 0.26, ease: "easeOut" }}
                              className={joinClasses("truncate text-[15px] font-medium leading-6 transition-colors duration-300", editorBarPresentation.textClassName)}
                            >
                              {editorFloatingMessage}
                            </motion.div>
                          </AnimatePresence>
                        </div>
                      </div>
                      <div className="flex shrink-0 items-center gap-2.5">
                        <button
                          type="button"
                          onClick={handleReset}
                          className={joinClasses(
                            "inline-flex h-10 items-center rounded-full border px-4 text-[14px] font-semibold transition-colors duration-300",
                            editorBarPresentation.secondaryButtonClassName,
                          )}
                        >
                          取消编辑
                        </button>
                        <button
                          type="button"
                          disabled={saving || !isDirty}
                          onClick={() => void handleSave()}
                          className={joinClasses(
                            "inline-flex h-10 items-center rounded-full px-5 text-[14px] font-semibold shadow-sm transition-colors duration-300 disabled:cursor-not-allowed disabled:opacity-60",
                            editorBarPresentation.primaryButtonClassName,
                          )}
                        >
                          {saving ? (
                            <>
                              <RefreshCw size={16} className="mr-2 animate-spin" />
                              正在保存
                            </>
                          ) : (
                            "保存资料"
                          )}
                        </button>
                      </div>
                    </div>
                  </div>
                </motion.div>
              ) : null}
            </div>
          </motion.div>
        ) : null}
      </AnimatePresence>
    </div>
  );
}

function StatTile({ icon, value, label }: { icon: ReactNode; value: string; label: string }) {
  return (
    <div className="rounded-[1.45rem] border border-slate-100 bg-white/70 p-4 shadow-sm backdrop-blur-md">
      <div className="mb-1.5 flex items-center gap-1.5">
        {icon}
        <div className="text-[2rem] font-black leading-none text-slate-800">{value}</div>
      </div>
      <div className="text-sm font-bold text-slate-600">{label}</div>
    </div>
  );
}

function SummaryLine({ label, value }: { label: string; value: string | null | undefined }) {
  return (
    <div className="rounded-xl border border-white bg-white px-4 py-3">
      <div className="text-sm font-semibold text-slate-500">{label}</div>
      <div className="mt-2 text-sm font-semibold text-slate-900">{value || "—"}</div>
    </div>
  );
}

function InlineDebugCodeCard({
  targetEmail,
  code,
}: {
  targetEmail: string;
  code: string;
}) {
  return (
    <div className="rounded-[1.25rem] border border-sky-100 bg-sky-50/80 px-4 py-4">
      <div className="text-base font-bold text-sky-700">验证提示</div>
      <div className="mt-2 text-base leading-7 text-sky-900">
        当前可直接使用下方验证码完成 <span className="font-semibold">{targetEmail}</span> 的验证：
      </div>
      <div className="mt-3 rounded-xl border border-white/80 bg-white px-4 py-3 text-center text-lg font-black tracking-[0.4em] text-sky-700 shadow-sm">
        {code}
      </div>
    </div>
  );
}

function ModalOverlay({
  children,
  onClose,
  maxWidthClassName = "max-w-md",
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
      className="fixed inset-0 z-[70] flex items-center justify-center bg-slate-900/40 p-4 backdrop-blur-sm"
      onClick={onClose}
    >
      <motion.div
        initial={{ scale: 0.94, y: 18, opacity: 0 }}
        animate={{ scale: 1, y: 0, opacity: 1 }}
        exit={{ scale: 0.96, y: 18, opacity: 0 }}
        transition={{ type: "spring", bounce: 0.3 }}
        className={joinClasses("relative w-full rounded-[2rem] bg-white p-8 shadow-2xl", maxWidthClassName)}
        onClick={(event) => event.stopPropagation()}
      >
        <button
          type="button"
          onClick={onClose}
          className="absolute right-5 top-5 flex h-8 w-8 items-center justify-center rounded-full bg-slate-100 text-slate-500 transition-colors hover:bg-slate-200"
        >
          <X size={18} />
        </button>
        {children}
      </motion.div>
    </motion.div>
  );
}

function EditableField({
  label,
  icon: Icon,
  value,
  isEditing,
  onChange,
  type = "text",
  helper = "",
  headerRight,
}: {
  label: string;
  icon: LucideIcon;
  value: string;
  isEditing: boolean;
  onChange: (value: string) => void;
  type?: string;
  helper?: string;
  headerRight?: ReactNode;
}) {
  return (
    <div className="rounded-[1.45rem] border border-slate-100 bg-white/60 px-5 py-4 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <label className="flex items-center text-sm font-bold text-slate-700">
          <Icon size={16} className="mr-2 text-emerald-500" />
          {label}
        </label>
        {headerRight}
      </div>
      <div className="mt-3">
        {isEditing ? (
          <div>
            <input
              type={type}
              value={value}
              onChange={(event) => onChange(event.target.value)}
              className="w-full rounded-xl border-2 border-slate-200 px-3 py-2.5 text-sm font-medium text-slate-800 outline-none transition-colors focus:border-emerald-400"
            />
            {helper ? <div className="mt-1.5 text-xs text-slate-400">{helper}</div> : null}
          </div>
        ) : (
          <>
            <div className="text-base font-semibold text-slate-900">
              {value ? value : <span className="text-sm font-medium text-slate-400">未填写</span>}
            </div>
            {helper ? <div className="mt-1.5 text-xs text-slate-400">{helper}</div> : null}
          </>
        )}
      </div>
    </div>
  );
}

function InlineSwitch({
  value,
  isEditing,
  onChange,
  ariaLabel = "公开展示真名",
}: {
  value: boolean;
  isEditing: boolean;
  onChange: (nextValue: boolean) => void;
  ariaLabel?: string;
}) {
  return (
    <button
      type="button"
      disabled={!isEditing}
      onClick={() => onChange(!value)}
      className={joinClasses(
        "relative inline-flex h-8 w-14 shrink-0 items-center rounded-full transition-colors",
        value ? "bg-emerald-500" : "bg-slate-300",
        !isEditing && "cursor-not-allowed opacity-70",
      )}
      aria-label={ariaLabel}
      aria-pressed={value}
    >
      <span
        className={joinClasses(
          "inline-block h-6 w-6 rounded-full bg-white shadow transition-transform",
          value ? "translate-x-7" : "translate-x-1",
        )}
      />
    </button>
  );
}

function ToggleField({
  label,
  description,
  value,
  isEditing,
  onChange,
  onLabel,
  offLabel,
}: {
  label: string;
  description: string;
  value: boolean;
  isEditing: boolean;
  onChange: (nextValue: boolean) => void;
  onLabel?: string;
  offLabel?: string;
}) {
  return (
    <div className="rounded-[1.45rem] border border-slate-100 bg-white/60 px-5 py-4 shadow-sm">
      <div className="flex items-start justify-between gap-4">
        <div>
          <div className="text-sm font-bold text-slate-700">{label}</div>
          <div className="mt-2 text-sm leading-6 text-slate-500">{description}</div>
        </div>
        <button
          type="button"
          disabled={!isEditing}
          onClick={() => onChange(!value)}
          className={joinClasses(
            "relative mt-1 inline-flex h-8 w-14 items-center rounded-full transition-colors",
            value ? "bg-emerald-500" : "bg-slate-300",
            !isEditing && "cursor-not-allowed opacity-70",
          )}
        >
          <span
            className={joinClasses(
              "inline-block h-6 w-6 rounded-full bg-white shadow transition-transform",
              value ? "translate-x-7" : "translate-x-1",
            )}
          />
        </button>
      </div>
      <div className="mt-3 text-sm font-semibold text-slate-700">
        当前状态：{value ? (onLabel ?? "可接单") : (offLabel ?? "暂停接单")}
      </div>
    </div>
  );
}

function ServicePackageEditorCard({
  isEditing,
  packages,
  onChange,
  onNotify,
}: {
  isEditing: boolean;
  packages: MentorServicePackage[];
  onChange: (nextPackages: MentorServicePackage[]) => void;
  onNotify: (message: string, tone?: ToastState["tone"]) => void;
}) {
  const handleUpdatePackage = (index: number, updater: (current: MentorServicePackage) => MentorServicePackage) => {
    const nextPackages = packages.map((item, itemIndex) => (
      itemIndex === index
        ? {
          ...updater(item),
          sortNo: itemIndex + 1,
        }
        : item
    ));
    onChange(nextPackages);
  };

  const handleAddPackage = () => {
    if (packages.length >= 4) {
      onNotify("当前最多保留 4 个服务套餐。", "warning");
      return;
    }
    const preferredScene = packages[0]?.sceneLabel || SERVICE_SCENE_OPTIONS[0];
    const nextPriceFen = computeStartingPriceFen(packages, 0);
    onChange([
      ...packages,
      createPackageDraft(packages.length + 1, preferredScene, nextPriceFen),
    ]);
  };

  const handleRemovePackage = (index: number) => {
    if (packages.length <= 1) {
      onNotify("至少需要保留一个服务套餐。", "warning");
      return;
    }
    onChange(packages.filter((_, itemIndex) => itemIndex !== index));
  };

  return (
    <div className="rounded-[1.45rem] border border-slate-100 bg-white/60 px-5 py-5 shadow-sm">
      <div className="flex flex-col gap-3 border-b border-slate-100 pb-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center text-sm font-bold text-slate-700">
            <CreditCard size={16} className="mr-2 text-emerald-500" />
            服务套餐配置
          </div>
          <p className="mt-2 text-sm leading-7 text-slate-500">
            学生会在创单页看到这里配置的套餐。首版最多保留 4 个套餐，其中预约型套餐最多 1 个。
          </p>
        </div>
        {isEditing ? (
          <button
            type="button"
            onClick={handleAddPackage}
            className="inline-flex items-center justify-center rounded-xl border border-emerald-100 bg-emerald-50 px-4 py-2.5 text-sm font-bold text-emerald-600 transition-colors hover:bg-emerald-100"
          >
            <Plus size={16} className="mr-2" />
            新增套餐
          </button>
        ) : null}
      </div>

      <div className="mt-5 space-y-4">
        {packages.map((item, index) => {
          const otherAppointmentExists = packages.some((pkg, pkgIndex) => pkgIndex !== index && pkg.deliveryMode === "APPOINTMENT");
          return (
            <div key={`${item.id}-${index}`} className="rounded-[1.35rem] border border-slate-100 bg-white/80 p-4 shadow-sm">
              <div className="flex flex-col gap-3 border-b border-slate-100 pb-4 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <div className="text-base font-bold text-slate-900">{item.packageName || `服务套餐 ${index + 1}`}</div>
                  <div className="mt-1 text-sm text-slate-500">
                    {item.sceneLabel} · {item.deliveryMode === "APPOINTMENT" ? `${item.durationMinutes ?? 45} 分钟预约` : "图文异步服务"}
                  </div>
                </div>
                <div className="flex items-center gap-3">
                  <InlineSwitch
                    value={item.enabled}
                    isEditing={isEditing}
                    ariaLabel={`切换${item.packageName || `套餐 ${index + 1}`}的启用状态`}
                    onChange={(nextValue) => {
                      const enabledCount = packages.filter((pkg) => pkg.enabled).length;
                      if (!nextValue && item.enabled && enabledCount <= 1) {
                        onNotify("请至少保留一个启用中的套餐。", "warning");
                        return;
                      }
                      handleUpdatePackage(index, (current) => ({ ...current, enabled: nextValue }));
                    }}
                  />
                  {isEditing && packages.length > 1 ? (
                    <button
                      type="button"
                      onClick={() => handleRemovePackage(index)}
                      className="inline-flex items-center rounded-lg border border-rose-100 bg-rose-50 px-3 py-2 text-sm font-semibold text-rose-600 transition-colors hover:bg-rose-100"
                    >
                      <X size={14} className="mr-1.5" />
                      删除
                    </button>
                  ) : null}
                </div>
              </div>

              <div className="mt-4 grid gap-4 lg:grid-cols-2">
                <EditableField
                  label="套餐名称"
                  icon={BriefcaseBusiness}
                  value={item.packageName}
                  isEditing={isEditing}
                  onChange={(value) => handleUpdatePackage(index, (current) => ({ ...current, packageName: value }))}
                  helper="例如：标准图文咨询、45 分钟预约复盘。"
                />
                <EditableField
                  label="价格（元）"
                  icon={CreditCard}
                  value={formatPriceInput(item.priceFen)}
                  isEditing={isEditing}
                  onChange={(value) => handleUpdatePackage(index, (current) => ({ ...current, priceFen: parsePriceFen(value) }))}
                  type="number"
                  helper="学生创建订单时会按这里填写的金额结算。"
                />

                <div className="rounded-[1.2rem] border border-slate-100 bg-slate-50/80 px-4 py-4">
                  <label className="text-sm font-bold text-slate-700">服务场景</label>
                  {isEditing ? (
                    <select
                      value={item.sceneLabel}
                      onChange={(event) => handleUpdatePackage(index, (current) => ({
                        ...current,
                        sceneLabel: event.target.value,
                        sceneCode: resolveServiceSceneCode(event.target.value),
                      }))}
                      className="mt-3 w-full rounded-xl border-2 border-slate-200 bg-white px-3 py-2.5 text-sm font-medium text-slate-800 outline-none transition-colors focus:border-emerald-400"
                    >
                      {SERVICE_SCENE_OPTIONS.map((scene) => (
                        <option key={scene} value={scene}>
                          {scene}
                        </option>
                      ))}
                    </select>
                  ) : (
                    <div className="mt-3 text-base font-semibold text-slate-900">{item.sceneLabel}</div>
                  )}
                </div>

                <div className="rounded-[1.2rem] border border-slate-100 bg-slate-50/80 px-4 py-4">
                  <label className="text-sm font-bold text-slate-700">服务方式</label>
                  {isEditing ? (
                    <div className="mt-3 space-y-3">
                      <div className="flex flex-wrap gap-2">
                        {[
                          { value: "TEXT_ASYNC", label: "图文异步" },
                          { value: "APPOINTMENT", label: "预约时段" },
                        ].map((option) => (
                          <button
                            key={option.value}
                            type="button"
                            onClick={() => {
                              if (option.value === "APPOINTMENT" && otherAppointmentExists) {
                                onNotify("当前仅支持保留一个预约型套餐。", "warning");
                                return;
                              }
                              handleUpdatePackage(index, (current) => ({
                                ...current,
                                deliveryMode: option.value,
                                durationMinutes: option.value === "APPOINTMENT" ? current.durationMinutes ?? 45 : null,
                              }));
                            }}
                            className={joinClasses(
                              "rounded-full border px-3 py-2 text-sm font-semibold transition-colors",
                              item.deliveryMode === option.value
                                ? "border-slate-900 bg-slate-900 text-white"
                                : "border-slate-200 bg-white text-slate-600 hover:border-slate-300 hover:text-slate-900",
                            )}
                          >
                            {option.label}
                          </button>
                        ))}
                      </div>
                      {item.deliveryMode === "APPOINTMENT" ? (
                        <div>
                          <label className="text-sm font-medium text-slate-600">服务时长</label>
                          <select
                            value={String(item.durationMinutes ?? 45)}
                            onChange={(event) => handleUpdatePackage(index, (current) => ({
                              ...current,
                              durationMinutes: Number(event.target.value) || 45,
                            }))}
                            className="mt-2 w-full rounded-xl border-2 border-slate-200 bg-white px-3 py-2.5 text-sm font-medium text-slate-800 outline-none transition-colors focus:border-emerald-400"
                          >
                            {DURATION_OPTIONS.map((duration) => (
                              <option key={duration} value={duration}>
                                {duration} 分钟
                              </option>
                            ))}
                          </select>
                        </div>
                      ) : (
                        <div className="text-sm leading-6 text-slate-500">
                          创建订单后直接进入支付和材料同步，不要求学生提前选择排期时段。
                        </div>
                      )}
                    </div>
                  ) : (
                    <div className="mt-3 text-base font-semibold text-slate-900">
                      {item.deliveryMode === "APPOINTMENT" ? `${item.durationMinutes ?? 45} 分钟预约` : "图文异步服务"}
                    </div>
                  )}
                </div>
              </div>

              <div className="mt-4 rounded-[1.2rem] border border-slate-100 bg-slate-50/80 px-4 py-4">
                <label className="text-sm font-bold text-slate-700">套餐说明</label>
                {isEditing ? (
                  <textarea
                    value={item.description ?? ""}
                    onChange={(event) => handleUpdatePackage(index, (current) => ({ ...current, description: event.target.value }))}
                    rows={3}
                    className="mt-3 w-full rounded-xl border-2 border-slate-200 bg-white p-3 text-sm text-slate-800 outline-none transition-colors focus:border-emerald-400"
                    placeholder="告诉学生这类套餐更适合什么问题、会得到什么形式的帮助。"
                  />
                ) : (
                  <p className="mt-3 text-sm leading-7 text-slate-600">
                    {item.description?.trim() || "还没有补充套餐说明。"}
                  </p>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function TagEditorCard({
  isEditing,
  tags,
  tagInput,
  onTagInputChange,
  onAddTag,
  onRemoveTag,
}: {
  isEditing: boolean;
  tags: string[];
  tagInput: string;
  onTagInputChange: (value: string) => void;
  onAddTag: () => void;
  onRemoveTag: (tag: string) => void;
}) {
  return (
    <div className="rounded-[1.45rem] border border-slate-100 bg-white/60 px-5 py-5 shadow-sm">
      <label className="flex items-center text-sm font-bold text-slate-700">
        <Tags size={16} className="mr-2 text-emerald-500" />
        擅长方向标签 (最多 6 个)
      </label>
      <div className="mt-4">
        {isEditing ? (
          <div className="space-y-3">
            <div className="flex flex-wrap gap-2">
              {tags.map((tag) => (
                <span key={tag} className="inline-flex items-center rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-1 text-sm font-medium text-emerald-700">
                  {tag}
                  <button type="button" onClick={() => onRemoveTag(tag)} className="ml-2 text-emerald-400 transition-colors hover:text-emerald-600">
                    <X size={14} />
                  </button>
                </span>
              ))}
            </div>
            <div className="flex max-w-sm gap-2">
              <input
                type="text"
                value={tagInput}
                onChange={(event) => onTagInputChange(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "Enter") {
                    event.preventDefault();
                    onAddTag();
                  }
                }}
                placeholder="输入后按回车添加"
                className="flex-1 rounded-xl border-2 border-slate-200 px-3 py-2 text-sm outline-none transition-colors focus:border-emerald-400"
              />
              <button type="button" onClick={onAddTag} className="rounded-xl bg-slate-900 px-4 py-2 text-sm font-semibold text-white">
                添加
              </button>
            </div>
          </div>
        ) : (
          <div className="flex flex-wrap gap-2">
            {tags.length > 0 ? tags.map((tag) => (
              <span key={tag} className="inline-flex items-center rounded-lg border border-slate-200 bg-slate-50 px-3 py-1.5 text-sm font-medium text-slate-600">
                {tag}
              </span>
            )) : (
              <span className="text-sm text-slate-400">还没有补充擅长标签。</span>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

function SceneSelectorCard({
  selectedScenes,
  isEditing,
  onToggle,
}: {
  selectedScenes: string[];
  isEditing: boolean;
  onToggle: (scene: string) => void;
}) {
  return (
    <div className="rounded-[1.45rem] border border-slate-100 bg-white/60 px-5 py-5 shadow-sm">
      <label className="flex items-center text-sm font-bold text-slate-700">
        <CalendarDays size={16} className="mr-2 text-teal-500" />
        擅长咨询场景
      </label>
      <p className="mt-2 text-sm text-slate-500">这些场景能帮助学生更快判断你是否适合他们当前的问题。</p>
      <div className="mt-4 flex flex-wrap gap-2">
        {SERVICE_SCENE_OPTIONS.map((scene) => {
          const active = selectedScenes.includes(scene);
          return (
            <button
              key={scene}
              type="button"
              disabled={!isEditing}
              onClick={() => onToggle(scene)}
              className={joinClasses(
                "rounded-full border px-3 py-2 text-sm font-semibold transition-colors",
                active ? "border-teal-200 bg-teal-50 text-teal-700" : "border-slate-200 bg-white text-slate-500",
                isEditing ? "hover:border-teal-200 hover:text-teal-600" : "cursor-default",
              )}
            >
              {scene}
            </button>
          );
        })}
      </div>
    </div>
  );
}

function TextAreaField({
  label,
  icon: Icon,
  value,
  isEditing,
  onChange,
  placeholder,
}: {
  label: string;
  icon: LucideIcon;
  value: string;
  isEditing: boolean;
  onChange: (value: string) => void;
  placeholder: string;
}) {
  return (
    <div className="rounded-[1.45rem] border border-slate-100 bg-white/60 px-5 py-4 shadow-sm">
      <label className="mb-2 flex items-center text-sm font-bold text-slate-700">
        <Icon size={16} className="mr-2 text-teal-500" />
        {label}
      </label>
      {isEditing ? (
        <textarea
          value={value}
          onChange={(event) => onChange(event.target.value)}
          placeholder={placeholder}
          rows={4}
          className="w-full rounded-xl border-2 border-slate-200 p-3 text-sm text-slate-800 outline-none transition-colors focus:border-teal-400"
        />
      ) : (
        <div className="min-h-[5rem] text-sm font-medium leading-relaxed text-slate-600">
          {value ? value : <span className="text-slate-400">还没有补充这部分内容。</span>}
        </div>
      )}
    </div>
  );
}
