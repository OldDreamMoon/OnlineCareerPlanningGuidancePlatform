import { AnimatePresence, motion } from "framer-motion";
import {
  AlertCircle,
  ArrowLeft,
  Award,
  Bell,
  Building2,
  CalendarDays,
  ExternalLink,
  FileText,
  Github,
  Globe,
  History,
  Inbox,
  Info,
  Linkedin,
  Mail,
  MessageSquare,
  PenTool,
  Paperclip,
  Phone,
  Search,
  Send,
  Sparkles,
  Target,
  ThumbsUp,
  Twitter,
  User,
  Users,
  X,
  type LucideIcon,
} from "lucide-react";
import { startTransition, useEffect, useRef, useState, type ReactNode } from "react";
import { Link, Navigate, useParams, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import StudentIdentityAvatar from "../components/avatar/StudentIdentityAvatar";
import WorkspacePageLoadingScreen from "../components/WorkspacePageLoadingScreen";
import WorkspaceRoleTopbar from "../components/workspace/WorkspaceRoleTopbar";
import { ApiClientError, apiRequest, buildQuery, isAbortError } from "../lib/apiClient";
import { resolveEnterpriseAccountName } from "../lib/enterpriseIdentity";
import { buildEnterpriseLogoUrl } from "../lib/enterpriseLogo";
import { ENTERPRISE_PAGED_BATCH_SIZE, fetchAllPagedRecords } from "../lib/enterpriseTasks";
import {
  formatDateTime,
  formatRelativeTime as formatRelativeTimeByBrowserTimezone,
  toTimestamp,
} from "../lib/formatters";
import { getEnterpriseWorkspaceNavItems } from "../lib/workspaceNav";
import {
  buildWorkspaceSnapshotStorageKey,
  readWorkspaceSnapshot,
  writeWorkspaceSnapshot,
} from "../lib/workspaceSnapshot";
import { buildStudentAvatarPath, type StudentAvatarMeta } from "../lib/studentAvatar";

type PageState = "loading" | "ready" | "error";
type SubmissionTab = "ALL" | "PENDING" | "ACCEPTED" | "REJECTED";
type DecisionMode = "contact" | "reject";
type ReviewAssistDrawerTab = "contact" | "history" | null;

type ToastState = {
  tone: "success" | "error" | "info";
  message: string;
};

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

type StudentSocialLinkItem = {
  platform: string;
  value: string;
};

type SubmissionContactInfo = {
  hint: string;
  emailVisible: boolean;
  email: string | null;
  phoneVisible: boolean;
  phone: string | null;
  wechatVisible: boolean;
  wechat: string | null;
  socialVisible: boolean;
  socialLinks: StudentSocialLinkItem[];
};

type SubmissionHistoryItem = {
  eventId: number;
  eventType: string;
  occurredAt: string | null;
  comment: string | null;
  contactIntent: string | null;
  rejectTemplate: string | null;
  note: string | null;
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
  contactIntent: string | null;
  rejectTemplate: string | null;
  reviewNote: string | null;
  reviewedAt: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  contact: SubmissionContactInfo;
  history: SubmissionHistoryItem[];
};

type BountySubmissionListResponse = {
  records: BountySubmissionItem[];
  total: number;
  page: number;
  size: number;
};

type BountySubmissionReviewResponse = {
  submissionId: number;
  taskId: number;
  status: string;
  taskStatus: string;
  reviewedAt: string | null;
  comment: string | null;
  contactIntent: string | null;
  rejectTemplate: string | null;
  reviewNote: string | null;
  updatedAt: string | null;
  history: SubmissionHistoryItem[];
};

type EnterpriseTaskReviewBundle = {
  profile: EnterpriseOwnProfileResponse;
  task: BountyTaskDetailResponse;
  submissions: BountySubmissionItem[];
  submissionTotal: number;
  partial: boolean;
};

type SubmissionStatusCounts = {
  total: number;
  pending: number;
  accepted: number;
  rejected: number;
};

type SubmissionQueueSnapshot = {
  records: BountySubmissionItem[];
  total: number;
  partial: boolean;
};

type HistoryEntry = {
  id: string;
  action: string;
  time: string | null;
  note: string | null;
  tone: "default" | "success" | "muted";
};

type ParsedReviewComment = {
  contactIntent: string | null;
  rejectTemplate: string | null;
  note: string | null;
};

const containerVariants = {
  hidden: { opacity: 0 },
  show: {
    opacity: 1,
    transition: { staggerChildren: 0.08 },
  },
};

const itemVariants = {
  hidden: { opacity: 0, y: 20 },
  show: {
    opacity: 1,
    y: 0,
    transition: { type: "spring" as const, stiffness: 260, damping: 26 },
  },
};

const CONTACT_INTENTS = ["继续沟通", "进一步交流", "邀请面试", "录用意向"] as const;

const REJECT_TEMPLATES = [
  "本次任务方向暂不完全匹配",
  "本次提交仍有提升空间",
  "这次暂未继续推进，欢迎后续继续参与",
  "感谢本次投入与提交",
] as const;

const SCORE_FILTER_OPTIONS = [
  { label: "全部贡献分", value: 0 },
  { label: "5 分以上", value: 5 },
  { label: "10 分以上", value: 10 },
  { label: "20 分以上", value: 20 },
  { label: "40 分以上", value: 40 },
] as const;

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

function normalizeOptionalText(value: string | null | undefined) {
  return value?.trim() ?? "";
}

function normalizeSubmissionHistoryItem(item: SubmissionHistoryItem): SubmissionHistoryItem {
  return {
    eventId: item.eventId,
    eventType: item.eventType,
    occurredAt: item.occurredAt ?? null,
    comment: item.comment ?? null,
    contactIntent: item.contactIntent ?? null,
    rejectTemplate: item.rejectTemplate ?? null,
    note: item.note ?? null,
  };
}

function normalizeSubmissionItem(item: BountySubmissionItem): BountySubmissionItem {
  return {
    ...item,
    contactIntent: item.contactIntent ?? null,
    rejectTemplate: item.rejectTemplate ?? null,
    reviewNote: item.reviewNote ?? null,
    history: Array.isArray(item.history) ? item.history.map(normalizeSubmissionHistoryItem) : [],
  };
}

function readPositiveSubmissionId(value: string | null) {
  if (!value?.trim()) {
    return null;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? Math.trunc(parsed) : null;
}

function getSocialLinkLabel(platform: string | null | undefined) {
  switch ((platform ?? "").trim().toUpperCase()) {
    case "GITHUB":
      return "GitHub";
    case "PORTFOLIO":
      return "作品集";
    case "WEBSITE":
      return "个人网站";
    case "LINKEDIN":
      return "LinkedIn";
    case "X":
      return "X / Twitter";
    case "BEHANCE":
      return "Behance";
    case "DRIBBBLE":
      return "Dribbble";
    default:
      return platform?.trim() || "外部主页";
  }
}

function getSocialLinkIcon(platform: string | null | undefined): LucideIcon {
  switch ((platform ?? "").trim().toUpperCase()) {
    case "GITHUB":
      return Github;
    case "WEBSITE":
      return Globe;
    case "LINKEDIN":
      return Linkedin;
    case "X":
    case "TWITTER":
      return Twitter;
    case "PORTFOLIO":
      return FileText;
    case "BEHANCE":
    case "DRIBBBLE":
      return PenTool;
    default:
      return ExternalLink;
  }
}

function buildSocialLinkHref(platform: string | null | undefined, value: string | null | undefined) {
  const normalizedValue = normalizeOptionalText(value);
  if (!normalizedValue) {
    return null;
  }
  if (/^https?:\/\//i.test(normalizedValue)) {
    return normalizedValue;
  }
  if (normalizedValue.startsWith("www.") || normalizedValue.includes(".")) {
    return `https://${normalizedValue}`;
  }
  if ((platform ?? "").trim().toUpperCase() === "GITHUB") {
    return `https://github.com/${normalizedValue.replace(/^@/, "")}`;
  }
  return null;
}

function formatRelativeTime(value: string | null | undefined) {
  return formatRelativeTimeByBrowserTimezone(value, "刚刚");
}

function getTaskStatusMeta(task: BountyTaskDetailResponse | null) {
  if (!task) {
    return {
      label: "加载中",
      className: "border border-slate-200 bg-slate-100 text-slate-500",
    };
  }

  if (task.status === "CLOSED" && task.acceptedSubmissionId) {
    return {
      label: "已完成筛选",
      className: "border border-emerald-200 bg-emerald-50 text-emerald-700",
    };
  }

  if (task.status === "CLOSED") {
    return {
      label: "已结束",
      className: "border border-slate-200 bg-slate-100 text-slate-600",
    };
  }

  if (task.deadlineAt) {
    const diffDays = Math.ceil((toTimestamp(task.deadlineAt) - Date.now()) / 86400000);
    if (diffDays <= 3) {
      return {
        label: "即将截止",
        className: "border border-amber-200 bg-amber-50 text-amber-700",
      };
    }
  }

  return {
    label: "审核中",
    className: "border border-indigo-200 bg-indigo-50 text-indigo-700",
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

function buildReviewComment(mode: DecisionMode, contactIntent: string, rejectTemplate: string, actionNote: string) {
  const normalizedNote = actionNote.trim();
  // comment 同时给学生通知和历史记录使用，保持结构化字段与可读文本一致。
  if (mode === "contact") {
    return normalizedNote
      ? `继续接触意向：${contactIntent}\n补充说明：${normalizedNote}`
      : `继续接触意向：${contactIntent}`;
  }

  return normalizedNote
    ? `未入选原因：${rejectTemplate}\n补充说明：${normalizedNote}`
    : `未入选原因：${rejectTemplate}`;
}

function parseReviewComment(reviewComment: string | null | undefined): ParsedReviewComment {
  const normalized = reviewComment?.trim();
  if (!normalized) {
    return { contactIntent: null, rejectTemplate: null, note: null };
  }

  const lines = normalized
    .split(/\n+/)
    .map((item) => item.trim())
    .filter(Boolean);

  // 历史旧数据可能只有 comment 文本，按固定前缀尽量还原成结构化展示。
  const firstLine = lines[0] ?? "";
  const detailLine = lines
    .slice(1)
    .map((item) => item.replace(/^补充说明：/, "").trim())
    .filter(Boolean)
    .join(" ");

  if (firstLine.startsWith("继续接触意向：")) {
    return {
      contactIntent: firstLine.replace("继续接触意向：", "").trim() || null,
      rejectTemplate: null,
      note: detailLine || null,
    };
  }

  if (firstLine.startsWith("未入选原因：")) {
    return {
      contactIntent: null,
      rejectTemplate: firstLine.replace("未入选原因：", "").trim() || null,
      note: detailLine || null,
    };
  }

  return {
    contactIntent: null,
    rejectTemplate: null,
    note: normalized,
  };
}

function resolveStructuredReview(submission: Pick<BountySubmissionItem, "reviewComment" | "contactIntent" | "rejectTemplate" | "reviewNote">) {
  if (submission.contactIntent || submission.rejectTemplate || submission.reviewNote) {
    return {
      contactIntent: submission.contactIntent,
      rejectTemplate: submission.rejectTemplate,
      note: submission.reviewNote,
    };
  }
  return parseReviewComment(submission.reviewComment);
}

function resolveHistoryDetail(item: SubmissionHistoryItem) {
  if (item.contactIntent || item.rejectTemplate || item.note) {
    return {
      contactIntent: item.contactIntent,
      rejectTemplate: item.rejectTemplate,
      note: item.note,
    };
  }
  return parseReviewComment(item.comment);
}

function buildHistoryEntries(task: BountyTaskDetailResponse, submission: BountySubmissionItem): HistoryEntry[] {
  if (submission.history.length > 0) {
    // 新链路优先使用事件表，能区分自动拒绝、任务关闭和人工发送结果。
    return submission.history.map((item) => {
      const detail = resolveHistoryDetail(item);
      switch (item.eventType) {
        case "SUBMITTED":
          return {
            id: `history-${item.eventId}`,
            action: "学生提交了任务产出",
            time: item.occurredAt,
            note: item.note ?? submission.contentSummary ?? null,
            tone: "default" as const,
          };
        case "CONTACT_SENT":
          return {
            id: `history-${item.eventId}`,
            action: detail.contactIntent ? `企业发送了继续接触通知（${detail.contactIntent}）` : "企业发送了继续接触通知",
            time: item.occurredAt,
            note: detail.note ?? item.comment,
            tone: "success" as const,
          };
        case "REJECT_SENT":
          return {
            id: `history-${item.eventId}`,
            action: "企业发送了未入选通知",
            time: item.occurredAt,
            note: detail.rejectTemplate ?? detail.note ?? item.comment,
            tone: "muted" as const,
          };
        case "AUTO_REJECTED_TASK_CLOSED":
          return {
            id: `history-${item.eventId}`,
            action: "任务已完成筛选，本次提交自动结束",
            time: item.occurredAt,
            note: detail.note ?? detail.rejectTemplate ?? item.comment,
            tone: "muted" as const,
          };
        case "TASK_CLOSED_AFTER_ACCEPT":
          return {
            id: `history-${item.eventId}`,
            action: "任务已完成筛选",
            time: item.occurredAt,
            note: item.note ?? "任务已结束，处理结果已确认。",
            tone: "success" as const,
          };
        default:
          return {
            id: `history-${item.eventId}`,
            action: "企业更新了提交状态",
            time: item.occurredAt,
            note: item.note ?? item.comment,
            tone: "default" as const,
          };
      }
    }).sort((left, right) => toTimestamp(left.time) - toTimestamp(right.time));
  }

  // 兼容早期没有事件明细的提交记录，用提交状态和 reviewComment 拼最小时间线。
  const parsed = resolveStructuredReview(submission);
  const items: HistoryEntry[] = [
    {
      id: `submitted-${submission.submissionId}`,
      action: "学生提交了任务产出",
      time: submission.createdAt,
      note: submission.contentSummary || null,
      tone: "default",
    },
  ];

  if (submission.reviewedAt && submission.status === "ACCEPTED") {
    items.push({
      id: `reviewed-${submission.submissionId}`,
      action: parsed.contactIntent ? `企业发送了继续接触通知（${parsed.contactIntent}）` : "企业发送了继续接触通知",
      time: submission.reviewedAt,
      note: parsed.note ?? submission.reviewComment,
      tone: "success",
    });
  }

  if (submission.reviewedAt && submission.status === "REJECTED") {
    items.push({
      id: `reviewed-${submission.submissionId}`,
      action: "企业发送了未入选通知",
      time: submission.reviewedAt,
      note: parsed.rejectTemplate ?? parsed.note ?? submission.reviewComment,
      tone: "muted",
    });
  }

  if (task.acceptedSubmissionId === submission.submissionId && task.closedAt) {
    items.push({
      id: `closed-${submission.submissionId}`,
      action: "任务已完成筛选",
      time: task.closedAt,
      note: "任务已结束，处理结果已确认。",
      tone: "success",
    });
  }

  return items.sort((left, right) => toTimestamp(left.time) - toTimestamp(right.time));
}

function buildDisableReason(task: BountyTaskDetailResponse, submission: BountySubmissionItem | null) {
  if (!submission) {
    return "暂无可处理的学生提交。";
  }
  if (task.status === "CLOSED" && task.acceptedSubmissionId) {
    return "该任务已完成筛选，无需再次发送处理结果。";
  }
  if (task.status === "CLOSED") {
    return "该任务已关闭，暂不能继续发送处理结果。";
  }
  if (!isPendingSubmission(submission.status)) {
    return "该提交已处理完成，可在处理记录中查看详情。";
  }
  return null;
}

function buildSubmissionListQuery(params: {
  page: number;
  size: number;
  status?: string;
  portraitTag?: string;
  minCommunityScore7d?: number;
}) {
  return buildQuery({
    page: params.page,
    size: params.size,
    status: params.status,
    portraitTag: params.portraitTag?.trim() || undefined,
    minCommunityScore7d: params.minCommunityScore7d && params.minCommunityScore7d > 0
      ? params.minCommunityScore7d
      : undefined,
  });
}

async function fetchSubmissionCount(
  taskId: number,
  params: {
    status?: string;
    portraitTag?: string;
    minCommunityScore7d?: number;
    signal?: AbortSignal;
  },
) {
  const response = await apiRequest<BountySubmissionListResponse>(
    `/bounty/tasks/${taskId}/submissions${buildSubmissionListQuery({
      page: 1,
      size: 1,
      status: params.status,
      portraitTag: params.portraitTag,
      minCommunityScore7d: params.minCommunityScore7d,
    })}`,
    { signal: params.signal },
  );
  return response.total;
}

async function fetchSubmissionQueueSnapshot(
  taskId: number,
  params: {
    status?: string;
    portraitTag?: string;
    minCommunityScore7d?: number;
    signal?: AbortSignal;
  },
): Promise<SubmissionQueueSnapshot> {
  // 审核队列可能跨页，按固定 batch 拉齐当前筛选条件下的全部记录。
  const response = await fetchAllPagedRecords<BountySubmissionItem>(
    ({ page, size }) => apiRequest<BountySubmissionListResponse>(
      `/bounty/tasks/${taskId}/submissions${buildSubmissionListQuery({
        page,
        size,
        status: params.status,
        portraitTag: params.portraitTag,
        minCommunityScore7d: params.minCommunityScore7d,
      })}`,
      { signal: params.signal },
    ),
    { pageSize: ENTERPRISE_PAGED_BATCH_SIZE },
  );

  return {
    records: response.records.map(normalizeSubmissionItem),
    total: response.total,
    partial: response.total > response.records.length,
  };
}

function mergeSubmissionQueueSnapshots(...snapshots: SubmissionQueueSnapshot[]): SubmissionQueueSnapshot {
  const records = snapshots
    .flatMap((snapshot) => snapshot.records)
    .sort((left, right) => toTimestamp(right.createdAt) - toTimestamp(left.createdAt));

  return {
    records,
    total: snapshots.reduce((sum, snapshot) => sum + snapshot.total, 0),
    partial: snapshots.some((snapshot) => snapshot.partial),
  };
}

async function fetchEnterpriseTaskReviewBundle(taskId: number, signal?: AbortSignal): Promise<EnterpriseTaskReviewBundle> {
  // 工作区首屏需要企业身份、任务详情和提交队列三块数据，统一打包便于快照恢复。
  const [profile, task, submissionResponse] = await Promise.all([
    apiRequest<EnterpriseOwnProfileResponse>("/profiles/enterprises/me", { signal }),
    apiRequest<BountyTaskDetailResponse>(`/bounty/tasks/${taskId}`, { signal }),
    fetchAllPagedRecords<BountySubmissionItem>(
      ({ page, size }) => apiRequest<BountySubmissionListResponse>(`/bounty/tasks/${taskId}/submissions${buildQuery({ page, size })}`, { signal }),
      { pageSize: ENTERPRISE_PAGED_BATCH_SIZE },
    ),
  ]);

  return {
    profile,
    task,
    submissions: submissionResponse.records.map(normalizeSubmissionItem),
    submissionTotal: submissionResponse.total,
    partial: submissionResponse.total > submissionResponse.records.length,
  };
}

function WorkspaceFrame({
  task,
  profile,
  submissionTotal,
  pendingCount,
  loading,
  lastUpdatedAt,
  onRefresh,
  children,
}: {
  task: BountyTaskDetailResponse | null;
  profile: EnterpriseOwnProfileResponse | null;
  submissionTotal: number | null;
  pendingCount: number | null;
  loading: boolean;
  lastUpdatedAt: string | null;
  onRefresh: () => void;
  children: ReactNode;
}) {
  const resolvedLogoUrl = buildEnterpriseLogoUrl(profile?.logoUrl ?? null, profile?.logoUpdatedAt);
  const resolvedAccountName = resolveEnterpriseAccountName(profile);

  return (
    <div className="relative h-screen overflow-hidden bg-[#eef3ff] text-slate-900">
      <div className="pointer-events-none fixed inset-0 z-0 overflow-hidden">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(71,85,105,0.1),transparent_30%),radial-gradient(circle_at_bottom_right,rgba(20,184,166,0.06),transparent_28%),linear-gradient(180deg,#f1f5f9_0%,#f8fafc_58%,#f8fafc_100%)]" />
        <div className="page-top-glow page-top-glow--slate" />
      </div>

      <WorkspaceRoleTopbar
        sectionLabel="Enterprise Task Review"
        title="企业任务审核工作区"
        icon={Building2}
        navItems={getEnterpriseWorkspaceNavItems("tasks")}
        displayName={resolvedAccountName}
        userSubtitle={profile?.companyName?.trim() || task?.enterpriseName || "企业账号"}
        userFallbackLabel="企业代表"
        userFallbackInitial="企"
        userAvatarUrl={resolvedLogoUrl}
        userAvatarDisplayName={profile?.companyName?.trim() || profile?.displayName?.trim() || task?.enterpriseName || "企业"}
        lastUpdatedAt={lastUpdatedAt}
        onRefresh={onRefresh}
        refreshing={loading}
        refreshTitle="刷新审核工作区数据"
        maxWidthClassName="max-w-[100rem]"
      />

      <main className="relative mx-auto flex h-[calc(100vh-5rem)] w-full max-w-[100rem] flex-col gap-4 overflow-hidden px-4 py-4 sm:px-6 sm:py-5">
        {children}
      </main>
    </div>
  );
}

function WorkspaceSkeleton({ displayName }: { displayName: string | null | undefined }) {
  return (
    <WorkspacePageLoadingScreen
      title="正在准备企业任务审核工作区"
      description="正在加载任务详情、报名记录和沟通状态，请稍候。"
    />
  );
}

function WorkspaceErrorState({
  displayName,
  errorMessage,
  loading,
  lastUpdatedAt,
  onRefresh,
}: {
  displayName: string | null | undefined;
  errorMessage: string;
  loading: boolean;
  lastUpdatedAt: string | null;
  onRefresh: () => void;
}) {
  return (
    <WorkspaceFrame
      task={null}
      profile={displayName ? { userId: 0, displayName, realName: null, companyName: null, jobTitle: null, logoUrl: null, approvalStatus: "PENDING" } : null}
      submissionTotal={0}
      pendingCount={0}
      loading={loading}
      lastUpdatedAt={lastUpdatedAt}
      onRefresh={onRefresh}
    >
      <div className="mx-auto flex w-full max-w-3xl items-start rounded-[2rem] border border-rose-200 bg-white/92 p-8 shadow-[0_24px_60px_rgba(148,163,184,0.16)] backdrop-blur-xl">
        <div className="flex w-full flex-col gap-5 sm:flex-row sm:items-start sm:justify-between">
          <div className="flex items-start gap-4">
            <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-rose-50 text-rose-500">
              <AlertCircle size={24} />
            </div>
            <div>
              <div className="text-[13px] text-rose-400">读取失败</div>
              <h1 className="mt-2 text-[30px] font-bold text-slate-900">审核工作区暂时无法加载</h1>
              <p className="mt-3 text-[16px] leading-7 text-slate-600">{errorMessage}</p>
              <p className="mt-3 text-[16px] leading-7 text-slate-500">
                请重新加载，或先返回工作台继续其他操作。
              </p>
            </div>
          </div>
          <div className="flex shrink-0 flex-col gap-3">
            <button
              type="button"
              onClick={onRefresh}
              className="inline-flex items-center justify-center rounded-full bg-slate-900 px-6 py-3 text-[16px] font-semibold text-white transition-transform hover:-translate-y-0.5 hover:bg-slate-800"
            >
              重新加载
            </button>
            <Link
              to="/enterprise/dashboard"
              className="inline-flex items-center justify-center rounded-full border border-slate-200 bg-white px-6 py-3 text-[16px] font-semibold text-slate-600 transition-colors hover:border-slate-300 hover:text-slate-900"
            >
              返回工作台
            </Link>
          </div>
        </div>
      </div>
    </WorkspaceFrame>
  );
}

function SectionEmptyState({
  icon: Icon,
  title,
  desc,
  actionText,
  actionHref,
}: {
  icon: LucideIcon;
  title: string;
  desc: string;
  actionText?: string;
  actionHref?: string;
}) {
  return (
    <div className="flex flex-col items-center justify-center rounded-2xl border border-dashed border-slate-300 bg-slate-50/60 px-6 py-10 text-center">
      <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-white text-slate-400 shadow-sm ring-1 ring-slate-100">
        <Icon size={22} />
      </div>
      <div className="text-[17px] font-bold text-slate-900">{title}</div>
      <div className="mt-1.5 max-w-[320px] text-[14px] leading-6 text-slate-500">{desc}</div>
      {actionText && actionHref ? (
        <Link to={actionHref} className="mt-4 inline-flex items-center text-[16px] font-bold text-indigo-600 transition-colors hover:text-indigo-700">
          {actionText}
        </Link>
      ) : null}
    </div>
  );
}

function TaskRequirementsModal({
  open,
  task,
  onClose,
}: {
  open: boolean;
  task: BountyTaskDetailResponse;
  onClose: () => void;
}) {
  return (
    <AnimatePresence>
      {open ? (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          className="fixed inset-0 z-[72] flex items-center justify-center bg-slate-950/35 p-4 backdrop-blur-sm"
          onClick={onClose}
        >
          <motion.div
            initial={{ opacity: 0, y: 20, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 16, scale: 0.98 }}
            transition={{ type: "spring", stiffness: 260, damping: 26 }}
            className="flex max-h-[min(82vh,56rem)] w-full max-w-4xl flex-col overflow-hidden rounded-[2rem] border border-white/80 bg-white shadow-[0_30px_80px_rgba(15,23,42,0.24)]"
            onClick={(event) => event.stopPropagation()}
            role="dialog"
            aria-modal="true"
            aria-labelledby="enterprise-task-requirements-title"
          >
            <div className="border-b border-slate-100 bg-slate-50/85 px-6 py-5 sm:px-7">
              <div className="flex items-start justify-between gap-4">
                <div className="min-w-0">
                  <div className="text-[12px] font-bold text-slate-400">任务要求</div>
                  <h2 id="enterprise-task-requirements-title" className="mt-2 text-[26px] font-bold tracking-tight text-slate-900">
                    {task.title}
                  </h2>
                  <p className="mt-2 text-[15px] leading-7 text-slate-500">
                    查看任务背景、交付要求与奖励说明。
                  </p>
                </div>
                <button
                  type="button"
                  onClick={onClose}
                  className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-500 transition-colors hover:border-slate-300 hover:text-slate-900"
                >
                  <X size={18} />
                </button>
              </div>
            </div>

            <div className="flex-1 overflow-y-auto px-6 py-6 sm:px-7">
              <div className="grid gap-5 xl:grid-cols-[minmax(0,1.2fr)_20rem]">
                <div className="rounded-[1.6rem] border border-slate-200 bg-white p-5 shadow-sm">
                  <div className="mb-3 flex items-center gap-2 text-[14px] font-bold text-slate-400">
                    <FileText size={15} />
                    任务背景与交付要求
                  </div>
                  <div className="rounded-2xl border border-slate-100 bg-slate-50 p-5 text-[16px] leading-8 text-slate-700 whitespace-pre-line">
                    {task.description || "待补充任务说明。"}
                  </div>
                </div>

                <div className="space-y-5">
                  <div className="rounded-[1.6rem] border border-amber-100 bg-amber-50/75 p-5">
                    <div className="mb-3 flex items-center gap-2 text-[14px] font-bold text-amber-700">
                      <Target size={15} />
                      奖励说明
                    </div>
                    <div className="text-[16px] leading-7 text-amber-900/90 whitespace-pre-line">
                      {task.rewardDescription || "待补充奖励说明"}
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </motion.div>
        </motion.div>
      ) : null}
    </AnimatePresence>
  );
}

function ReviewAssistDrawer({
  tab,
  submission,
  historyEntries,
  onClose,
}: {
  tab: ReviewAssistDrawerTab;
  submission: BountySubmissionItem | null;
  historyEntries: HistoryEntry[];
  onClose: () => void;
}) {
  return (
    <AnimatePresence>
      {tab && submission ? (
        <>
          <motion.button
            type="button"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-[73] bg-slate-950/25 backdrop-blur-[2px]"
            aria-label="关闭辅助信息抽屉"
            onClick={onClose}
          />
          <motion.aside
            initial={{ x: 36, opacity: 0 }}
            animate={{ x: 0, opacity: 1 }}
            exit={{ x: 36, opacity: 0 }}
            transition={{ type: "spring", stiffness: 260, damping: 28 }}
            className="fixed inset-y-0 right-0 z-[74] flex w-full max-w-[30rem] flex-col overflow-hidden border-l border-white/70 bg-[#f8fbff] shadow-[-24px_0_60px_rgba(15,23,42,0.16)]"
          >
            <div className="border-b border-slate-200 bg-white/92 px-5 py-5 backdrop-blur-xl">
              <div className="flex items-start justify-between gap-4">
                <div className="flex min-w-0 items-start gap-4">
                  <StudentIdentityAvatar
                    userId={submission.studentUserId}
                    role="STUDENT"
                    displayName={submission.studentName}
                    avatar={submission.studentAvatar}
                    avatarPath={buildStudentAvatarPath(submission.studentUserId, submission.studentAvatar?.updatedAt)}
                    className="h-14 w-14 border border-white bg-white shadow-sm"
                    textClassName="text-lg"
                  />
                  <div className="min-w-0">
                    <div className="text-[12px] font-bold text-slate-400">辅助信息</div>
                    <div className="mt-2 truncate text-[22px] font-bold tracking-tight text-slate-900">{submission.studentName}</div>
                    <div className="mt-1 text-[14px] text-slate-500">查看联系方式与处理记录。</div>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={onClose}
                  className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-500 transition-colors hover:border-slate-300 hover:text-slate-900"
                >
                  <X size={18} />
                </button>
              </div>
            </div>

            <div className="flex-1 overflow-y-auto p-5 [&::-webkit-scrollbar]:w-1.5 [&::-webkit-scrollbar-thumb]:rounded-full [&::-webkit-scrollbar-thumb]:bg-slate-200">
              {tab === "contact" ? (
                <div className="space-y-4">
                  <div className="rounded-2xl border border-indigo-100 bg-indigo-50/50 p-4 text-[14px] leading-6 text-slate-600">
                    {submission.contact.hint || "以下为学生向企业开放的联系方式。"}
                  </div>

                  <div className="rounded-2xl border border-indigo-100 bg-white px-4 py-3 shadow-sm">
                    <div className="flex items-center gap-2 text-[14px] font-bold text-slate-700">
                      <Mail size={14} className="text-indigo-500" />
                      邮箱
                    </div>
                    <div className="mt-2 text-[16px] leading-6 text-slate-600">
                      {!submission.contact.emailVisible ? (
                        "暂未开放邮箱。"
                      ) : submission.contact.email ? (
                        <a href={`mailto:${submission.contact.email}`} className="font-semibold text-indigo-600 transition-colors hover:text-indigo-700">
                          {submission.contact.email}
                        </a>
                      ) : (
                        "已开放，暂未填写。"
                      )}
                    </div>
                  </div>

                  <div className="rounded-2xl border border-slate-200 bg-white px-4 py-3 shadow-sm">
                    <div className="flex items-center gap-2 text-[14px] font-bold text-slate-700">
                      <Phone size={14} className="text-slate-500" />
                      手机
                    </div>
                    <div className="mt-2 text-[16px] leading-6 text-slate-600">
                      {!submission.contact.phoneVisible ? (
                        "暂未开放手机号。"
                      ) : submission.contact.phone ? (
                        <a href={`tel:${submission.contact.phone}`} className="font-semibold text-slate-700 transition-colors hover:text-slate-900">
                          {submission.contact.phone}
                        </a>
                      ) : (
                        "已开放，暂未填写。"
                      )}
                    </div>
                  </div>

                  <div className="rounded-2xl border border-slate-200 bg-white px-4 py-3 shadow-sm">
                    <div className="flex items-center gap-2 text-[14px] font-bold text-slate-700">
                      <MessageSquare size={14} className="text-emerald-500" />
                      微信
                    </div>
                    <div className="mt-2 text-[16px] leading-6 text-slate-600">
                      {!submission.contact.wechatVisible ? (
                        "暂未开放微信。"
                      ) : submission.contact.wechat ? (
                        <span className="font-semibold text-slate-700">{submission.contact.wechat}</span>
                      ) : (
                        "已开放，暂未填写。"
                      )}
                    </div>
                  </div>

                  <div className="rounded-2xl border border-slate-200 bg-white px-4 py-3 shadow-sm">
                    <div className="flex items-center gap-2 text-[14px] font-bold text-slate-700">
                      <ExternalLink size={14} className="text-amber-500" />
                      外部主页与社交链接
                    </div>
                    <div className="mt-2 space-y-2 text-[16px] leading-6 text-slate-600">
                      {!submission.contact.socialVisible ? (
                        <div>暂未开放外部主页。</div>
                      ) : submission.contact.socialLinks.length > 0 ? (
                        submission.contact.socialLinks.map((link) => {
                          const href = buildSocialLinkHref(link.platform, link.value);
                          const Icon = getSocialLinkIcon(link.platform);
                          const cardClassName = "flex items-center justify-between gap-3 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 transition-colors";
                          return href ? (
                            <a
                              key={`${link.platform}-${link.value}`}
                              href={href}
                              target="_blank"
                              rel="noreferrer"
                              className={`${cardClassName} hover:border-indigo-200 hover:bg-indigo-50/50`}
                            >
                              <span className="flex items-center gap-2 text-[14px] font-bold text-slate-400">
                                <Icon size={15} />
                                {getSocialLinkLabel(link.platform)}
                              </span>
                              <span className="truncate font-medium text-indigo-600">{link.value}</span>
                            </a>
                          ) : (
                            <div
                              key={`${link.platform}-${link.value}`}
                              className={cardClassName}
                            >
                              <span className="flex items-center gap-2 text-[14px] font-bold text-slate-400">
                                <Icon size={15} />
                                {getSocialLinkLabel(link.platform)}
                              </span>
                              <span className="truncate font-medium text-slate-700">{link.value}</span>
                            </div>
                          );
                        })
                      ) : (
                        <div>外部主页已开放，但学生暂未填写。</div>
                      )}
                    </div>
                  </div>
                </div>
              ) : historyEntries.length > 0 ? (
                <div className="space-y-3">
                  {[...historyEntries].reverse().map((entry) => (
                    <div
                      key={entry.id}
                      className={joinClasses(
                        "rounded-2xl border p-4",
                        entry.tone === "success"
                          ? "border-emerald-100 bg-emerald-50/70"
                          : entry.tone === "muted"
                            ? "border-slate-200 bg-slate-50/80"
                            : "border-slate-200 bg-white",
                      )}
                    >
                      <div className="text-[16px] font-semibold text-slate-900">{entry.action}</div>
                      <div className="mt-1 text-[13px] text-slate-400">{entry.time ? formatDateTime(entry.time) : "时间待更新"}</div>
                      {entry.note ? (
                        <div className="mt-2 text-[14px] leading-6 text-slate-500 whitespace-pre-line">
                          {entry.note}
                        </div>
                      ) : null}
                    </div>
                  ))}
                </div>
              ) : (
                <SectionEmptyState
                  icon={History}
                  title="暂未生成处理记录"
                  desc="发送处理结果后，这里会保留对应记录。"
                />
              )}
            </div>
          </motion.aside>
        </>
      ) : null}
    </AnimatePresence>
  );
}

function DecisionSetupModal({
  open,
  mode,
  studentName,
  contactIntent,
  rejectTemplate,
  actionNote,
  syncEmailReminder,
  previewComment,
  disableReason,
  submittingDecision,
  onClose,
  onPickContactIntent,
  onPickRejectTemplate,
  onActionNoteChange,
  onToggleEmailReminder,
  onConfirm,
}: {
  open: boolean;
  mode: DecisionMode;
  studentName: string | null;
  contactIntent: string;
  rejectTemplate: string;
  actionNote: string;
  syncEmailReminder: boolean;
  previewComment: string;
  disableReason: string | null;
  submittingDecision: boolean;
  onClose: () => void;
  onPickContactIntent: (value: string) => void;
  onPickRejectTemplate: (value: string) => void;
  onActionNoteChange: (value: string) => void;
  onToggleEmailReminder: () => void;
  onConfirm: () => void;
}) {
  const isContactMode = mode === "contact";
  const title = isContactMode ? "继续接触设置" : "未入选通知设置";
  const subtitle = isContactMode
    ? `为 ${studentName ?? "该学生"} 设置继续接触内容。`
    : `为 ${studentName ?? "该学生"} 设置未入选通知内容。`;

  return (
    <AnimatePresence>
      {open ? (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          className="fixed inset-0 z-[78] flex items-center justify-center bg-slate-950/30 p-4 backdrop-blur-sm"
          onClick={onClose}
        >
          <motion.div
            initial={{ opacity: 0, y: 18, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 18, scale: 0.98 }}
            transition={{ type: "spring", stiffness: 280, damping: 28 }}
            className="flex max-h-[min(84vh,58rem)] w-full max-w-3xl flex-col overflow-hidden rounded-[2rem] border border-white/80 bg-white shadow-[0_30px_80px_rgba(15,23,42,0.24)]"
            onClick={(event) => event.stopPropagation()}
            role="dialog"
            aria-modal="true"
            aria-labelledby="enterprise-review-decision-setup-title"
          >
            <div className="border-b border-slate-100 bg-slate-50/80 px-6 py-5 sm:px-7">
              <div className="flex items-start justify-between gap-4">
                <div className="min-w-0">
                  <div className="text-[13px] font-bold text-slate-400">处理动作</div>
                  <h2 id="enterprise-review-decision-setup-title" className="mt-2 text-[24px] font-bold tracking-tight text-slate-900">
                    {title}
                  </h2>
                  <p className="mt-2 text-[16px] leading-7 text-slate-500">{subtitle}</p>
                </div>
                <button
                  type="button"
                  onClick={onClose}
                  className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-500 transition-colors hover:border-slate-300 hover:text-slate-900"
                >
                  <X size={18} />
                </button>
              </div>
            </div>

            <div className="flex-1 space-y-5 overflow-y-auto px-6 py-6 sm:px-7">
              {disableReason ? (
                <div className="rounded-2xl border border-amber-100 bg-amber-50/80 p-4 text-[16px] leading-7 text-amber-900">
                  <div className="flex items-start gap-3">
                    <AlertCircle size={18} className="mt-0.5 shrink-0 text-amber-600" />
                    <div>{disableReason}</div>
                  </div>
                </div>
              ) : null}

              {isContactMode ? (
                <div className="rounded-2xl border border-emerald-100 bg-emerald-50/50 p-5">
                  <div className="text-[16px] font-bold text-slate-800">继续接触意图</div>
                  <div className="mt-3 grid grid-cols-2 gap-2">
                    {CONTACT_INTENTS.map((intent) => (
                      <button
                        key={intent}
                        type="button"
                        onClick={() => onPickContactIntent(intent)}
                        className={joinClasses(
                          "rounded-xl border px-3 py-2 text-[16px] font-semibold transition-all",
                          contactIntent === intent
                            ? "border-emerald-300 bg-white text-emerald-700 shadow-sm"
                            : "border-emerald-100 bg-emerald-50/40 text-slate-600 hover:bg-white",
                        )}
                      >
                        {intent}
                      </button>
                    ))}
                  </div>
                </div>
              ) : (
                <div className="rounded-2xl border border-slate-200 bg-slate-50/70 p-5">
                  <div className="text-[16px] font-bold text-slate-800">未入选模板</div>
                  <div className="mt-3 space-y-2">
                    {REJECT_TEMPLATES.map((template) => (
                      <button
                        key={template}
                        type="button"
                        onClick={() => onPickRejectTemplate(template)}
                        className={joinClasses(
                          "w-full rounded-xl border px-3 py-2 text-left text-[16px] font-medium transition-all",
                          rejectTemplate === template
                            ? "border-slate-300 bg-white text-slate-800 shadow-sm"
                            : "border-slate-200 bg-slate-50 text-slate-600 hover:bg-white",
                        )}
                      >
                        {template}
                      </button>
                    ))}
                  </div>
                </div>
              )}

              <div className="rounded-2xl border border-slate-200 bg-white p-5">
                <div className="mb-3 flex items-center justify-between gap-3">
                  <label htmlFor="enterprise-review-action-note-modal" className="text-[16px] font-bold text-slate-800">
                    补充说明
                  </label>
                  <span className="text-[13px] text-slate-400">会随结果备注一起发送给学生</span>
                </div>
                <textarea
                  id="enterprise-review-action-note-modal"
                  value={actionNote}
                  onChange={(event) => onActionNoteChange(event.target.value)}
                  placeholder={isContactMode ? "例如：如果方便，希望你下周补充讲解这份方案的拆解思路。" : "例如：方案方向偏离本次任务目标，后续欢迎继续参与更匹配的题目。"}
                  className="min-h-[116px] w-full resize-none rounded-2xl border border-slate-200 bg-slate-50/60 p-3 text-[16px] leading-6 text-slate-700 focus:border-indigo-300 focus:bg-white focus:outline-none"
                />
              </div>

              <div className="rounded-2xl border border-slate-200 bg-slate-50/70 p-5">
                <div className="text-[16px] font-bold text-slate-800">通知方式</div>
                <div className="mt-3 space-y-3">
                  <div className="flex items-center justify-between rounded-xl border border-indigo-100 bg-white px-3 py-2.5 text-[16px] text-slate-700">
                    <span className="flex items-center gap-2 font-semibold">
                      <Bell size={15} className="text-indigo-500" />
                      站内通知
                    </span>
                    <span className="rounded-full border border-indigo-200 bg-indigo-50 px-2 py-0.5 text-[13px] font-semibold text-indigo-600">本次必发</span>
                  </div>
                  <button
                    type="button"
                    onClick={onToggleEmailReminder}
                    className={joinClasses(
                      "flex w-full items-center justify-between rounded-xl border px-3 py-3 text-left transition-colors",
                      syncEmailReminder
                        ? "border-emerald-200 bg-emerald-50/70 text-emerald-800"
                        : "border-slate-200 bg-white text-slate-600 hover:border-slate-300 hover:text-slate-800",
                    )}
                  >
                    <span className="flex items-center gap-2 font-semibold">
                      <Mail size={15} className={syncEmailReminder ? "text-emerald-500" : "text-slate-400"} />
                      邮件提醒
                    </span>
                    <span className={joinClasses(
                      "rounded-full px-2 py-0.5 text-[13px] font-semibold",
                      syncEmailReminder
                        ? "border border-emerald-200 bg-white text-emerald-700"
                        : "border border-slate-200 bg-slate-100 text-slate-500",
                    )}>
                      {syncEmailReminder ? "本次已开启" : "本次不开启"}
                    </span>
                  </button>
                </div>
              </div>

              <div className="rounded-2xl border border-slate-200 bg-slate-50/70 p-5">
                <div className="flex items-center gap-2 text-[16px] font-bold text-slate-800">
                  <MessageSquare size={16} className="text-slate-400" />
                  发送内容预览
                </div>
                <div className="mt-3 rounded-2xl border border-slate-200 bg-white p-4 text-[16px] leading-7 text-slate-600 whitespace-pre-line">
                  {previewComment}
                </div>
              </div>
            </div>

            <div className="border-t border-slate-100 bg-white px-6 py-5 sm:px-7">
              <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
                <button
                  type="button"
                  onClick={onClose}
                  className="inline-flex items-center justify-center rounded-2xl border border-slate-200 bg-white px-5 py-3 text-[16px] font-bold text-slate-700 transition-colors hover:border-slate-300 hover:bg-slate-50"
                >
                  暂不处理
                </button>
                <button
                  type="button"
                  onClick={onConfirm}
                  disabled={!!disableReason || submittingDecision}
                  className={joinClasses(
                    "inline-flex items-center justify-center rounded-2xl px-5 py-3 text-[16px] font-bold text-white transition-colors",
                    isContactMode ? "bg-emerald-600 hover:bg-emerald-500" : "bg-slate-900 hover:bg-slate-800",
                    (!!disableReason || submittingDecision) && "cursor-not-allowed bg-slate-300 hover:bg-slate-300",
                  )}
                >
                  {submittingDecision ? "正在提交结果..." : "继续确认发送"}
                </button>
              </div>
            </div>
          </motion.div>
        </motion.div>
      ) : null}
    </AnimatePresence>
  );
}

export default function EnterpriseTaskReviewPage() {
  const { role, displayName, userId } = useAuth();
  const { taskId: taskIdParam } = useParams<{ taskId: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const taskId = Number(taskIdParam);
  const requestedSubmissionId = readPositiveSubmissionId(searchParams.get("submissionId"));

  const [pageState, setPageState] = useState<PageState>("loading");
  const [bundle, setBundle] = useState<EnterpriseTaskReviewBundle | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [lastUpdatedAt, setLastUpdatedAt] = useState<string | null>(null);
  const [selectedSubmissionId, setSelectedSubmissionId] = useState<number | null>(null);
  const [taskRequirementsOpen, setTaskRequirementsOpen] = useState(false);
  const [assistDrawerTab, setAssistDrawerTab] = useState<ReviewAssistDrawerTab>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [activeTab, setActiveTab] = useState<SubmissionTab>("ALL");
  const [minCommunityScore, setMinCommunityScore] = useState(0);
  const [decisionMode, setDecisionMode] = useState<DecisionMode>("contact");
  const [contactIntent, setContactIntent] = useState<string>(CONTACT_INTENTS[0]);
  const [rejectTemplate, setRejectTemplate] = useState<string>(REJECT_TEMPLATES[0]);
  const [actionNote, setActionNote] = useState("");
  const [syncEmailReminder, setSyncEmailReminder] = useState(false);
  const [decisionConfigOpen, setDecisionConfigOpen] = useState(false);
  const [decisionConfirmOpen, setDecisionConfirmOpen] = useState(false);
  const [submittingDecision, setSubmittingDecision] = useState(false);
  const [toast, setToast] = useState<ToastState | null>(null);
  const [refreshTick, setRefreshTick] = useState(0);
  const [serverFilteredSnapshot, setServerFilteredSnapshot] = useState<SubmissionQueueSnapshot | null>(null);
  const [serverFilteredCounts, setServerFilteredCounts] = useState<SubmissionStatusCounts | null>(null);
  const [queueRefreshing, setQueueRefreshing] = useState(false);
  const [queueErrorMessage, setQueueErrorMessage] = useState<string | null>(null);

  const requestIdRef = useRef(0);
  const queueRequestIdRef = useRef(0);
  const snapshotKey = buildWorkspaceSnapshotStorageKey("enterprise", "task-review", userId ?? "current", taskId);

  useEffect(() => {
    if (!toast) {
      return undefined;
    }

    const timerId = window.setTimeout(() => {
      setToast(null);
    }, 3200);

    return () => {
      window.clearTimeout(timerId);
    };
  }, [toast]);

  useEffect(() => {
    if (role !== "ENTERPRISE" || !Number.isFinite(taskId) || taskId <= 0) {
      return undefined;
    }

    const controller = new AbortController();
    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;
    const snapshot = readWorkspaceSnapshot<EnterpriseTaskReviewBundle>(snapshotKey);
    const hasSnapshot = Boolean(snapshot?.data);

    if (snapshot?.data) {
      // 先展示上次审核工作台状态，再由后端 bundle 覆盖最新提交和任务状态。
      const normalizedSnapshot = {
        ...snapshot.data,
        submissions: snapshot.data.submissions.map(normalizeSubmissionItem),
      } satisfies EnterpriseTaskReviewBundle;
      startTransition(() => {
        setBundle(normalizedSnapshot);
        setPageState("ready");
        setErrorMessage(null);
        setLastUpdatedAt(snapshot.updatedAt);
      });
    } else {
      setPageState((current) => (current === "ready" ? current : "loading"));
    }

    setErrorMessage(null);

    void fetchEnterpriseTaskReviewBundle(taskId, controller.signal)
      .then((response) => {
        if (controller.signal.aborted || requestIdRef.current !== requestId) {
          return;
        }
        const updatedAt = new Date().toISOString();
        writeWorkspaceSnapshot(snapshotKey, response, updatedAt);

        startTransition(() => {
          setBundle(response);
          setPageState("ready");
          setErrorMessage(null);
          setLastUpdatedAt(updatedAt);
        });
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted || isAbortError(error) || requestIdRef.current !== requestId) {
          return;
        }
        if (hasSnapshot && refreshTick === 0) {
          setErrorMessage(null);
          setPageState("ready");
          return;
        }

        const nextMessage = buildErrorMessage(error, "网络请求异常，请稍后重试。");
        setErrorMessage(nextMessage);
        setPageState((current) => (current === "ready" ? current : "error"));
      });

    return () => {
      controller.abort();
    };
  }, [refreshTick, role, snapshotKey, taskId]);

  useEffect(() => {
    if (role !== "ENTERPRISE" || !bundle || !lastUpdatedAt || !Number.isFinite(taskId) || taskId <= 0) {
      return;
    }
    writeWorkspaceSnapshot(snapshotKey, bundle, lastUpdatedAt);
  }, [bundle, lastUpdatedAt, role, snapshotKey, taskId]);

  const hasServerDimensionFilters = minCommunityScore > 0;
  const shouldUseServerFilteredQueue = activeTab !== "ALL" || hasServerDimensionFilters;

  useEffect(() => {
    if (role !== "ENTERPRISE" || !bundle || !Number.isFinite(taskId) || taskId <= 0) {
      setServerFilteredSnapshot(null);
      setServerFilteredCounts(null);
      setQueueRefreshing(false);
      setQueueErrorMessage(null);
      return;
    }

    if (!shouldUseServerFilteredQueue) {
      setServerFilteredSnapshot(null);
      setServerFilteredCounts(null);
      setQueueRefreshing(false);
      setQueueErrorMessage(null);
      return;
    }

    const controller = new AbortController();
    const requestId = queueRequestIdRef.current + 1;
    queueRequestIdRef.current = requestId;
    const normalizedMinCommunityScore = minCommunityScore > 0 ? minCommunityScore : undefined;

    async function loadQueueFromServer() {
      setQueueRefreshing(true);
      setQueueErrorMessage(null);

      try {
        // PENDING 对应 SUBMITTED + REVIEWING，两次请求合并后再本地排序。
        const queueSnapshotPromise = activeTab === "PENDING"
          ? Promise.all([
            fetchSubmissionQueueSnapshot(taskId, {
              status: "SUBMITTED",
              minCommunityScore7d: normalizedMinCommunityScore,
              signal: controller.signal,
            }),
            fetchSubmissionQueueSnapshot(taskId, {
              status: "REVIEWING",
              minCommunityScore7d: normalizedMinCommunityScore,
              signal: controller.signal,
            }),
          ]).then(([submittedSnapshot, reviewingSnapshot]) => mergeSubmissionQueueSnapshots(submittedSnapshot, reviewingSnapshot))
          : fetchSubmissionQueueSnapshot(taskId, {
            status: activeTab === "ACCEPTED" ? "ACCEPTED" : activeTab === "REJECTED" ? "REJECTED" : undefined,
            minCommunityScore7d: normalizedMinCommunityScore,
            signal: controller.signal,
          });

        const countsPromise = hasServerDimensionFilters
          ? Promise.all([
            // 贡献分等服务端筛选开启时，tab 计数也必须按同一过滤维度重算。
            fetchSubmissionCount(taskId, {
              minCommunityScore7d: normalizedMinCommunityScore,
              signal: controller.signal,
            }),
            fetchSubmissionCount(taskId, {
              status: "SUBMITTED",
              minCommunityScore7d: normalizedMinCommunityScore,
              signal: controller.signal,
            }),
            fetchSubmissionCount(taskId, {
              status: "REVIEWING",
              minCommunityScore7d: normalizedMinCommunityScore,
              signal: controller.signal,
            }),
            fetchSubmissionCount(taskId, {
              status: "ACCEPTED",
              minCommunityScore7d: normalizedMinCommunityScore,
              signal: controller.signal,
            }),
            fetchSubmissionCount(taskId, {
              status: "REJECTED",
              minCommunityScore7d: normalizedMinCommunityScore,
              signal: controller.signal,
            }),
          ]).then(([total, submitted, reviewing, accepted, rejected]) => ({
            total,
            pending: submitted + reviewing,
            accepted,
            rejected,
          } satisfies SubmissionStatusCounts))
          : Promise.resolve<SubmissionStatusCounts | null>(null);

        const [queueSnapshot, counts] = await Promise.all([queueSnapshotPromise, countsPromise]);
        if (controller.signal.aborted || queueRequestIdRef.current !== requestId) {
          return;
        }

        setServerFilteredSnapshot(queueSnapshot);
        setServerFilteredCounts(counts);
      } catch (error: unknown) {
        if (controller.signal.aborted || isAbortError(error) || queueRequestIdRef.current !== requestId) {
          return;
        }
        setServerFilteredSnapshot(null);
        setServerFilteredCounts(null);
        setQueueErrorMessage(buildErrorMessage(error, "筛选结果暂未更新，先展示已加载内容。"));
      } finally {
        if (!controller.signal.aborted && queueRequestIdRef.current === requestId) {
          setQueueRefreshing(false);
        }
      }
    }

    void loadQueueFromServer();

    return () => {
      controller.abort();
    };
  }, [activeTab, bundle, hasServerDimensionFilters, minCommunityScore, role, shouldUseServerFilteredQueue, taskId]);

  const allSubmissions = bundle?.submissions ?? [];
  const normalizedSearch = searchQuery.trim().toLowerCase();
  // 无服务端筛选时直接用首屏 bundle，搜索关键词保持前端即时过滤。
  const locallyFilteredByServerRules = allSubmissions.filter((submission) => {
    const matchesTab = activeTab === "ALL"
      || (activeTab === "PENDING" && isPendingSubmission(submission.status))
      || (activeTab === "ACCEPTED" && submission.status === "ACCEPTED")
      || (activeTab === "REJECTED" && submission.status === "REJECTED");
    const matchesScore = submission.communityScore7d >= minCommunityScore;
    return matchesTab && matchesScore;
  });

  const queueBaseSubmissions = shouldUseServerFilteredQueue && serverFilteredSnapshot
    ? serverFilteredSnapshot.records
    : locallyFilteredByServerRules;
  const filteredSubmissions = queueBaseSubmissions.filter((submission) => {
    const haystack = [
      submission.studentName,
      submission.contentSummary,
      submission.contentText ?? "",
      submission.portraitTags.join(" "),
    ].join(" ").toLowerCase();
    return !normalizedSearch || haystack.includes(normalizedSearch);
  });

  const localCountBase = hasServerDimensionFilters
    ? allSubmissions.filter((submission) => {
      const matchesScore = submission.communityScore7d >= minCommunityScore;
      return matchesScore;
    })
    : allSubmissions;

  const pendingCount = serverFilteredCounts?.pending ?? localCountBase.filter((submission) => isPendingSubmission(submission.status)).length;
  const acceptedCount = serverFilteredCounts?.accepted ?? localCountBase.filter((submission) => submission.status === "ACCEPTED").length;
  const rejectedCount = serverFilteredCounts?.rejected ?? localCountBase.filter((submission) => submission.status === "REJECTED").length;
  const allTabCount = serverFilteredCounts?.total ?? (hasServerDimensionFilters ? localCountBase.length : bundle?.submissionTotal ?? localCountBase.length);
  const queuePartial = shouldUseServerFilteredQueue && serverFilteredSnapshot ? serverFilteredSnapshot.partial : bundle?.partial ?? false;
  const queueSummaryText = queueErrorMessage
    ? "筛选结果更新稍慢，先基于已加载内容继续查看。"
    : shouldUseServerFilteredQueue
      ? queueRefreshing
        ? "正在更新列表，请稍候。"
        : "已按筛选条件更新列表。"
      : "列表已按最新状态展示。";
  const queuePartialMessage = shouldUseServerFilteredQueue && serverFilteredSnapshot
    ? `已载入 ${serverFilteredSnapshot.records.length}/${serverFilteredSnapshot.total} 份提交，更多记录可稍后继续查看。`
    : bundle?.partial
      ? `已载入 ${bundle.submissions.length}/${bundle.submissionTotal} 份提交，更多记录可稍后继续查看。`
      : null;

  useEffect(() => {
    if (filteredSubmissions.length === 0) {
      if (selectedSubmissionId !== null) {
        setSelectedSubmissionId(null);
      }
      return;
    }

    const nextSelectedSubmissionId = filteredSubmissions.some((submission) => submission.submissionId === selectedSubmissionId)
      ? selectedSubmissionId
      : requestedSubmissionId && filteredSubmissions.some((submission) => submission.submissionId === requestedSubmissionId)
        ? requestedSubmissionId
        : filteredSubmissions[0].submissionId;

    // 通知带 submissionId 进入时优先聚焦目标提交，否则落到当前筛选第一条。
    if (nextSelectedSubmissionId !== selectedSubmissionId) {
      setSelectedSubmissionId(nextSelectedSubmissionId);
    }
  }, [filteredSubmissions, requestedSubmissionId, selectedSubmissionId]);

  const selectedSubmission = filteredSubmissions.find((submission) => submission.submissionId === selectedSubmissionId) ?? null;
  const nextPendingSubmission = allSubmissions.find((submission) => (
    submission.submissionId !== selectedSubmission?.submissionId
    && isPendingSubmission(submission.status)
  )) ?? null;
  const canAutoAdvanceToNextPending = !normalizedSearch
    && minCommunityScore === 0
    && (activeTab === "ALL" || activeTab === "PENDING");

  function resetReviewFilters(nextTab: SubmissionTab = "ALL") {
    setSearchQuery("");
    setMinCommunityScore(0);
    setActiveTab(nextTab);
  }

  function focusPendingSubmission(submissionId: number) {
    resetReviewFilters("PENDING");
    setSelectedSubmissionId(submissionId);
  }

  useEffect(() => {
    setAssistDrawerTab(null);
    setDecisionConfigOpen(false);
    setDecisionConfirmOpen(false);
    setSyncEmailReminder(false);
  }, [selectedSubmission?.submissionId]);

  useEffect(() => {
    if (selectedSubmissionId === requestedSubmissionId) {
      return;
    }

    const nextParams = new URLSearchParams(searchParams);
    if (selectedSubmissionId) {
      nextParams.set("submissionId", String(selectedSubmissionId));
    } else {
      nextParams.delete("submissionId");
    }
    setSearchParams(nextParams, { replace: true });
  }, [requestedSubmissionId, searchParams, selectedSubmissionId, setSearchParams]);

  if (!Number.isFinite(taskId) || taskId <= 0) {
    return <Navigate to="/enterprise/dashboard" replace />;
  }

  const historyEntries = bundle && selectedSubmission ? buildHistoryEntries(bundle.task, selectedSubmission) : [];
  const disableReason = bundle ? buildDisableReason(bundle.task, selectedSubmission) : null;
  const previewComment = buildReviewComment(decisionMode, contactIntent, rejectTemplate, actionNote);

  const handleRefresh = () => {
    setPageState((current) => (current === "ready" ? current : "loading"));
    setRefreshTick((current) => current + 1);
  };

  async function handleDecisionSubmit() {
    if (!bundle || !selectedSubmission || disableReason) {
      return;
    }

    const requestBody = {
      decision: decisionMode === "contact" ? "ACCEPT" : "REJECT",
      contactIntent: decisionMode === "contact" ? contactIntent : null,
      rejectTemplate: decisionMode === "reject" ? rejectTemplate : null,
      actionNote: actionNote.trim() || null,
      comment: previewComment,
      syncEmailReminder,
    };

    setDecisionConfirmOpen(false);
    setSubmittingDecision(true);
    try {
      // 采纳会触发后端单一中选约束：任务关闭，其他待处理提交自动结束。
      const response = await apiRequest<BountySubmissionReviewResponse>(`/bounty/submissions/${selectedSubmission.submissionId}/review`, {
        method: "POST",
        body: JSON.stringify(requestBody),
      });

      setBundle((current) => {
        if (!current) {
          return current;
        }
        return {
          ...current,
          task: {
            ...current.task,
            status: response.taskStatus,
            acceptedSubmissionId: response.status === "ACCEPTED" ? response.submissionId : current.task.acceptedSubmissionId,
            closedAt: response.status === "ACCEPTED" ? response.reviewedAt : current.task.closedAt,
          },
          submissions: current.submissions.map((submission) => (
            submission.submissionId === response.submissionId
              ? {
                ...submission,
                status: response.status,
                reviewComment: response.comment,
                contactIntent: response.contactIntent,
                rejectTemplate: response.rejectTemplate,
                reviewNote: response.reviewNote,
                reviewedAt: response.reviewedAt,
                updatedAt: response.updatedAt,
                history: response.history,
              }
              : submission
          )),
        };
      });
      setRefreshTick((current) => current + 1);
      setToast({
        tone: "success",
        message: decisionMode === "contact"
          ? syncEmailReminder
            ? "已向学生发送继续接触通知，并已同时发起邮件提醒。"
            : "已向学生发送继续接触通知，相关处理记录已更新。"
          : syncEmailReminder
            ? "已向学生发送未入选通知，并已同时发起邮件提醒。"
            : "已向学生发送未入选通知，相关处理记录已更新。",
      });
      setActionNote("");
      setSyncEmailReminder(false);
      if (decisionMode === "reject" && nextPendingSubmission && canAutoAdvanceToNextPending) {
        focusPendingSubmission(nextPendingSubmission.submissionId);
      }
      setPageState("ready");
      setRefreshTick((current) => current + 1);
    } catch (error: unknown) {
      setToast({
        tone: "error",
        message: buildErrorMessage(error, "审核结果提交失败，请稍后重试。"),
      });
    } finally {
      setSubmittingDecision(false);
    }
  }

  function handleRequestDecisionSubmit() {
    if (!bundle || !selectedSubmission || disableReason || submittingDecision) {
      return;
    }
    setDecisionConfirmOpen(true);
  }

  if (!bundle && pageState === "loading") {
    return <WorkspaceSkeleton displayName={displayName} />;
  }

  if (!bundle) {
    return (
      <WorkspaceErrorState
        displayName={displayName}
        errorMessage={errorMessage ?? "企业审核工作区暂时不可用。"}
        loading={pageState === "loading"}
        lastUpdatedAt={lastUpdatedAt}
        onRefresh={handleRefresh}
      />
    );
  }

  const showInlineError = !!errorMessage && !!bundle;
  const taskStatusMeta = getTaskStatusMeta(bundle.task);
  const decisionActionLabel = decisionMode === "contact" ? "继续接触" : "未入选通知";
  const decisionDetailLabel = decisionMode === "contact" ? contactIntent : rejectTemplate;
  const decisionConfirmTitle = decisionMode === "contact" ? "确认发送继续接触结果" : "确认发送未入选通知";
  const activeFilterSummary = [
    activeTab !== "ALL" ? `状态：${activeTab === "PENDING" ? "待处理" : activeTab === "ACCEPTED" ? "继续接触" : "未入选"}` : null,
    minCommunityScore > 0 ? `贡献分 >= ${minCommunityScore}` : null,
    normalizedSearch ? `搜索：${searchQuery.trim()}` : null,
  ].filter(Boolean) as string[];

  return (
    <>
      <WorkspaceFrame
        task={bundle.task}
        profile={bundle.profile}
        submissionTotal={bundle.submissionTotal}
        pendingCount={pendingCount}
        loading={pageState === "loading"}
        lastUpdatedAt={lastUpdatedAt}
        onRefresh={handleRefresh}
      >
        <motion.div variants={containerVariants} initial="hidden" animate="show" className="flex h-full min-h-0 w-full flex-col gap-4">
          <motion.section
            variants={itemVariants}
            className="overflow-hidden rounded-[1.6rem] border border-white/80 bg-white/92 shadow-[0_14px_40px_rgba(148,163,184,0.12)] backdrop-blur-xl"
          >
            <div className="flex flex-col gap-4 px-5 py-4 lg:flex-row lg:items-center lg:justify-between lg:px-6">
              <div className="min-w-0 flex-1">
                <Link
                  to={`/enterprise/tasks?taskId=${bundle.task.taskId}`}
                  className="inline-flex items-center text-[16px] font-semibold text-slate-500 transition-colors hover:text-slate-900"
                >
                  <ArrowLeft size={15} className="mr-1.5" />
                  返回任务中心
                </Link>
                <div className="mt-2 flex flex-wrap items-center gap-3">
                  <h1 className="text-[30px] font-bold tracking-tight text-slate-900">
                    {bundle.task.title}
                  </h1>
                  <span className={joinClasses("inline-flex items-center rounded-full px-3 py-1 text-[14px] font-bold", taskStatusMeta.className)}>
                    {taskStatusMeta.label}
                  </span>
                </div>
                <p className="mt-2 max-w-3xl text-[16px] leading-7 text-slate-500">
                  左侧查看提交列表，右侧主阅读区集中阅读方案内容，并通过弹窗完成处理动作。
                </p>
              </div>

              <div className="grid gap-3 sm:grid-cols-3 lg:min-w-[33rem]">
                <div className="rounded-2xl border border-slate-200 bg-slate-50/70 px-4 py-2.5">
                  <div className="flex items-center gap-2 text-[13px] font-bold text-slate-400">
                    <CalendarDays size={14} />
                    截止时间
                  </div>
                  <div className="mt-1.5 text-[17px] font-semibold text-slate-900">
                    {bundle.task.deadlineAt ? formatDateTime(bundle.task.deadlineAt) : "未设置截止时间"}
                  </div>
                </div>
                <div className="rounded-2xl border border-slate-200 bg-slate-50/70 px-4 py-2.5">
                  <div className="flex items-center gap-2 text-[13px] font-bold text-slate-400">
                    <Users size={14} />
                    提交数量
                  </div>
                  <div className="mt-1.5 text-[17px] font-semibold text-slate-900">
                    共 {bundle.task.submissionCount} 份
                  </div>
                </div>
                <div className="rounded-2xl border border-amber-100 bg-amber-50/70 px-4 py-2.5">
                  <div className="flex items-center gap-2 text-[13px] font-bold text-amber-600">
                    <Inbox size={14} />
                    待处理
                  </div>
                  <div className="mt-1.5 text-[17px] font-semibold text-amber-700">
                    {pendingCount} 份待审核
                  </div>
                </div>
              </div>
            </div>
          </motion.section>

          <div className="flex w-full min-h-0 flex-1 flex-col gap-4 lg:flex-row lg:items-stretch">
            <motion.section
              variants={itemVariants}
              className="flex min-h-0 flex-col overflow-hidden rounded-[1.5rem] border border-white/70 bg-white/78 shadow-[0_8px_30px_rgba(148,163,184,0.08)] backdrop-blur-md lg:h-full lg:w-[26rem] lg:flex-none"
            >
              <div className="border-b border-slate-100 bg-white/60 p-4">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <h2 className="text-[20px] font-bold tracking-tight text-slate-900">提交队列</h2>
                    <div className="mt-1 text-[15px] leading-6 text-slate-500">
                      {activeFilterSummary.length > 0 ? `已筛选：${activeFilterSummary.join(" / ")}` : queueSummaryText}
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    {queueRefreshing ? (
                      <span className="rounded-full border border-sky-200 bg-sky-50 px-2.5 py-1 text-[14px] font-semibold text-sky-700">
                        正在更新
                      </span>
                    ) : null}
                    <span className="rounded-full bg-slate-100 px-2.5 py-1 text-[14px] font-semibold text-slate-500">
                      {filteredSubmissions.length}
                    </span>
                  </div>
                </div>

                <label className="mt-4 flex items-center gap-3 rounded-2xl border border-slate-200 bg-white px-3 py-2 shadow-sm">
                  <Search size={16} className="text-slate-400" />
                  <input
                    type="text"
                    value={searchQuery}
                    onChange={(event) => setSearchQuery(event.target.value)}
                    placeholder="搜索学生、关键词或标签"
                    className="w-full border-none bg-transparent text-[16px] text-slate-700 placeholder:text-slate-400 focus:outline-none"
                  />
                </label>

                <div className="mt-4 flex flex-wrap gap-1.5">
                  {[
                    { key: "ALL", label: "全部", count: allTabCount },
                    { key: "PENDING", label: "待处理", count: pendingCount },
                    { key: "ACCEPTED", label: "继续接触", count: acceptedCount },
                    { key: "REJECTED", label: "未入选", count: rejectedCount },
                  ].map((tab) => (
                    <button
                      key={tab.key}
                      type="button"
                      onClick={() => setActiveTab(tab.key as SubmissionTab)}
                      className={joinClasses(
                        "inline-flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-[15px] font-semibold transition-all",
                        activeTab === tab.key
                          ? "bg-slate-800 text-white shadow-sm"
                          : "bg-slate-100/80 text-slate-500 hover:bg-slate-200/80 hover:text-slate-800",
                      )}
                    >
                      {tab.label}
                      <span className={joinClasses("rounded-full px-1.5 py-0.5 text-[14px]", activeTab === tab.key ? "bg-white/15 text-white" : "bg-white text-slate-400")}>
                        {tab.count}
                      </span>
                    </button>
                  ))}
                </div>

                <div className="mt-4 flex flex-wrap gap-2 border-t border-slate-100 pt-4">
                  <button
                    type="button"
                    onClick={() => resetReviewFilters("PENDING")}
                    className="rounded-full border border-amber-200 bg-amber-50 px-3 py-1.5 text-[14px] font-bold text-amber-700 transition-colors hover:bg-amber-100"
                  >
                    仅看待处理
                  </button>
                  <button
                    type="button"
                    onClick={() => resetReviewFilters("ACCEPTED")}
                    className="rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1.5 text-[14px] font-bold text-emerald-700 transition-colors hover:bg-emerald-100"
                  >
                    查看继续接触
                  </button>
                  <button
                    type="button"
                    onClick={() => resetReviewFilters("ALL")}
                    className="rounded-full border border-slate-200 bg-white px-3 py-1.5 text-[14px] font-bold text-slate-600 transition-colors hover:bg-slate-50 hover:text-slate-900"
                  >
                    清空筛选
                  </button>
                </div>

                <div className="mt-3 flex flex-wrap items-center gap-2">
                  <div className="flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-2.5 py-1.5 text-[15px] text-slate-500 shadow-sm">
                    <Award size={13} />
                    <span>贡献分</span>
                    <select
                      value={String(minCommunityScore)}
                      onChange={(event) => setMinCommunityScore(Number(event.target.value))}
                      className="border-none bg-transparent text-[15px] text-slate-600 focus:outline-none"
                    >
                      {SCORE_FILTER_OPTIONS.map((option) => (
                        <option key={option.value} value={option.value}>{option.label}</option>
                      ))}
                    </select>
                  </div>
                </div>

                {queueErrorMessage ? (
                  <div className="mt-3 flex items-start gap-2 rounded-xl border border-amber-100 bg-amber-50/80 px-3 py-2 text-[14px] leading-6 text-amber-800">
                    <AlertCircle size={14} className="mt-0.5 shrink-0 text-amber-600" />
                    <div>{queueErrorMessage}</div>
                  </div>
                ) : null}

                {queuePartial && queuePartialMessage ? (
                  <div className="mt-3 flex items-center gap-2 rounded-xl border border-amber-100 bg-amber-50/80 px-3 py-2 text-[14px] leading-6 text-amber-700">
                    <Info size={14} className="shrink-0" />
                    {queuePartialMessage}
                  </div>
                ) : null}
              </div>

              <div className="flex-1 space-y-2 overflow-y-auto p-2 [&::-webkit-scrollbar]:w-1.5 [&::-webkit-scrollbar-thumb]:rounded-full [&::-webkit-scrollbar-thumb]:bg-slate-200">
                {filteredSubmissions.length > 0 ? (
                  filteredSubmissions.map((submission) => {
                    const isSelected = submission.submissionId === selectedSubmissionId;
                    const statusMeta = getSubmissionStatusMeta(submission.status);

                    return (
                      <button
                        key={submission.submissionId}
                        type="button"
                        onClick={() => setSelectedSubmissionId(submission.submissionId)}
                        className={joinClasses(
                          "relative w-full overflow-hidden rounded-xl border p-3.5 text-left transition-all",
                          isSelected
                            ? "border-indigo-300 bg-white shadow-[0_8px_20px_rgba(99,102,241,0.1)] ring-1 ring-indigo-500/10"
                            : "border-slate-100 bg-white/60 hover:border-slate-300 hover:bg-white",
                        )}
                      >
                        {isSelected ? <div className="absolute inset-y-0 left-0 w-1 rounded-l-xl bg-indigo-500" /> : null}

                        <div className="flex items-start justify-between gap-3">
                          <div className="flex items-center gap-3">
                            <StudentIdentityAvatar
                              userId={submission.studentUserId}
                              role="STUDENT"
                              displayName={submission.studentName}
                              avatar={submission.studentAvatar}
                              avatarPath={buildStudentAvatarPath(submission.studentUserId, submission.studentAvatar?.updatedAt)}
                              className={joinClasses(
                                "h-10 w-10 border bg-white shadow-sm",
                                isSelected ? "border-indigo-200" : "border-white",
                              )}
                              textClassName="text-[15px]"
                            />
                            <div className="min-w-0">
                              <div className="truncate text-[17px] font-bold text-slate-900">{submission.studentName}</div>
                              <div className="mt-1 text-[15px] text-slate-400">{formatRelativeTime(submission.createdAt)}</div>
                            </div>
                          </div>
                          <span className={joinClasses("inline-flex shrink-0 rounded-full px-2 py-0.5 text-[14px] font-semibold", statusMeta.className)}>
                            {statusMeta.label}
                          </span>
                        </div>

                        <div className="mt-4 flex items-start justify-between gap-3">
                          <div className="flex min-w-0 flex-wrap gap-1.5">
                            {submission.portraitTags.slice(0, 2).map((tag) => (
                              <span key={tag} className="rounded bg-slate-50 px-2 py-1 text-[14px] text-slate-500 ring-1 ring-slate-100">
                                {tag}
                              </span>
                            ))}
                          </div>
                          <div className="inline-flex shrink-0 items-center gap-1 rounded-lg border border-amber-100 bg-amber-50 px-2 py-1 text-[15px] font-bold text-amber-600">
                            <Award size={13} />
                            {submission.communityScore7d} 分
                          </div>
                        </div>

                        <div className="mt-3 line-clamp-2 text-[15px] leading-6 text-slate-500">
                          {submission.contentSummary || submission.contentText?.trim() || "这份提交暂未附加补充说明。"}
                        </div>
                      </button>
                    );
                  })
                ) : (
                  <SectionEmptyState
                    icon={Inbox}
                    title="暂未找到匹配的提交"
                    desc="可以调整标签、贡献分或搜索词后继续筛选。"
                    actionText="回到企业工作台"
                    actionHref="/enterprise/dashboard"
                  />
                )}
              </div>
            </motion.section>

            <motion.section
              variants={itemVariants}
              className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-[1.5rem] border border-white/80 bg-white/95 shadow-[0_12px_40px_rgba(148,163,184,0.08)] backdrop-blur-xl lg:h-full lg:min-w-0"
            >
              {showInlineError ? (
                <div className="border-b border-amber-100 bg-amber-50/90 px-5 py-3 text-[16px] leading-6 text-amber-900">
                  <div className="flex items-start gap-3">
                    <AlertCircle size={16} className="mt-0.5 shrink-0 text-amber-600" />
                    <div>
                      刷新未完成，先展示最近一次可用的审核内容。
                      <div className="mt-1 text-[14px] text-amber-800/90">{errorMessage}</div>
                    </div>
                  </div>
                </div>
              ) : null}

              <div className="border-b border-slate-100 bg-slate-50/75 px-5 py-5">
                <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
                  <div className="min-w-0">
                    <div className="text-[14px] font-bold text-slate-400">提交阅读区</div>
                    {selectedSubmission ? (
                      <>
                        <div className="mt-2 flex flex-wrap items-center gap-3">
                          <div className="flex min-w-0 items-center gap-3">
                            <StudentIdentityAvatar
                              userId={selectedSubmission.studentUserId}
                              role="STUDENT"
                              displayName={selectedSubmission.studentName}
                              avatar={selectedSubmission.studentAvatar}
                              avatarPath={buildStudentAvatarPath(selectedSubmission.studentUserId, selectedSubmission.studentAvatar?.updatedAt)}
                              className="h-12 w-12 border border-white bg-white shadow-sm"
                              textClassName="text-[17px]"
                            />
                            <h2 className="truncate text-[28px] font-bold tracking-tight text-slate-900">{selectedSubmission.studentName} 的提交</h2>
                          </div>
                          <span className="rounded-full border border-slate-200 bg-white px-3 py-1 text-[15px] font-semibold text-slate-500">
                            ID: {selectedSubmission.submissionId}
                          </span>
                          <span className={joinClasses("rounded-full px-3 py-1 text-[15px] font-semibold", getSubmissionStatusMeta(selectedSubmission.status).className)}>
                            {getSubmissionStatusMeta(selectedSubmission.status).label}
                          </span>
                        </div>
                        <div className="mt-3 flex flex-wrap gap-2">
                          <span className="rounded-full border border-slate-200 bg-white px-3 py-1 text-[14px] font-medium text-slate-500">
                            提交于 {formatDateTime(selectedSubmission.createdAt)}
                          </span>
                        </div>
                      </>
                    ) : (
                      <div className="mt-2 text-[16px] leading-7 text-slate-500">
                        选择左侧提交后，在这里阅读方案内容并完成判断。
                      </div>
                    )}
                  </div>

                  <div className="flex shrink-0 flex-wrap gap-2">
                    <button
                      type="button"
                      onClick={() => setTaskRequirementsOpen(true)}
                      className="inline-flex items-center justify-center rounded-full border border-slate-200 bg-white px-4 py-2.5 text-[15px] font-semibold text-slate-700 transition-colors hover:border-slate-300 hover:bg-slate-50"
                    >
                      查看任务要求
                    </button>
                    <button
                      type="button"
                      onClick={() => setAssistDrawerTab("contact")}
                      disabled={!selectedSubmission}
                      className={joinClasses(
                        "inline-flex items-center justify-center rounded-full border px-4 py-2.5 text-[15px] font-semibold transition-colors",
                        selectedSubmission
                          ? "border-indigo-200 bg-indigo-50 text-indigo-700 hover:bg-indigo-100"
                          : "cursor-not-allowed border-slate-200 bg-slate-100 text-slate-400",
                      )}
                    >
                      联系方式
                    </button>
                    <button
                      type="button"
                      onClick={() => setAssistDrawerTab("history")}
                      disabled={!selectedSubmission}
                      className={joinClasses(
                        "inline-flex items-center justify-center rounded-full border px-4 py-2.5 text-[15px] font-semibold transition-colors",
                        selectedSubmission
                          ? "border-emerald-200 bg-emerald-50 text-emerald-700 hover:bg-emerald-100"
                          : "cursor-not-allowed border-slate-200 bg-slate-100 text-slate-400",
                      )}
                    >
                      处理记录
                    </button>
                  </div>
                </div>

              </div>

              <div className="min-h-0 flex-1 overflow-y-auto p-5 [&::-webkit-scrollbar]:w-1.5 [&::-webkit-scrollbar-thumb]:rounded-full [&::-webkit-scrollbar-thumb]:bg-slate-200">
                {selectedSubmission ? (
                  <div className="space-y-5">
                    <div className="rounded-2xl border border-slate-100 bg-slate-50 p-5 shadow-sm">
                      <div className="flex items-center gap-2 text-[15px] font-bold text-slate-500">
                        <FileText size={15} />
                        概览
                      </div>
                      <div className="mt-4 grid gap-3 xl:grid-cols-[minmax(0,1.15fr)_minmax(0,0.85fr)]">
                        <div className="rounded-2xl border border-slate-200 bg-white p-4">
                          <div className="text-[14px] font-bold text-slate-500">内容概览</div>
                          <div className="mt-3 text-[17px] leading-8 text-slate-700">
                            {selectedSubmission.contentSummary || "可查看下方正文与附件内容。"}
                          </div>
                        </div>
                        <div className="grid gap-3 sm:grid-cols-3 xl:grid-cols-1">
                          <div className="rounded-xl border border-slate-100 bg-white px-4 py-3">
                            <div className="text-[13px] font-semibold text-slate-400">提交时间</div>
                            <div className="mt-1 text-[16px] font-semibold text-slate-900">{formatDateTime(selectedSubmission.createdAt)}</div>
                          </div>
                          <div className="rounded-xl border border-slate-100 bg-white px-4 py-3">
                            <div className="text-[13px] font-semibold text-slate-400">附件数量</div>
                            <div className="mt-1 text-[16px] font-semibold text-slate-900">{selectedSubmission.attachmentLinks.length} 个</div>
                          </div>
                          <div className="rounded-xl border border-slate-100 bg-white px-4 py-3">
                            <div className="text-[13px] font-semibold text-slate-400">社区贡献分</div>
                            <div className="mt-1 text-[16px] font-semibold text-slate-900">{selectedSubmission.communityScore7d} 分</div>
                          </div>
                        </div>
                      </div>
                    </div>

                    <div className="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm">
                      <div className="flex items-center gap-2 text-[15px] font-bold text-slate-500">
                        <MessageSquare size={15} />
                        正文说明
                      </div>
                      <div className="mt-3 text-[17px] leading-8 text-slate-700 whitespace-pre-line">
                        {selectedSubmission.contentText?.trim() || "该提交未附加更多文字说明。"}
                      </div>
                    </div>

                    <div className="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm">
                      <div className="flex items-center gap-2 text-[15px] font-bold text-slate-500">
                        <Paperclip size={15} />
                        附件链接
                      </div>
                      <div className="mt-4 space-y-4">
                        {selectedSubmission.attachmentLinks.length > 0 ? (
                          <div className="grid gap-4">
                            {selectedSubmission.attachmentLinks.map((link) => (
                              <a
                                key={link}
                                href={link}
                                target="_blank"
                                rel="noreferrer"
                                className="group flex items-center justify-between rounded-2xl border border-slate-200 bg-slate-50/60 p-4 transition-all hover:border-indigo-300 hover:bg-white"
                              >
                                <div className="flex min-w-0 items-center gap-3">
                                  <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600">
                                    <ExternalLink size={18} />
                                  </div>
                                  <span className="truncate text-[16px] font-medium text-slate-700 transition-colors group-hover:text-indigo-600">
                                    {link}
                                  </span>
                                </div>
                              </a>
                            ))}
                          </div>
                        ) : (
                          <SectionEmptyState
                            icon={Paperclip}
                            title="暂未附加附件"
                            desc="本次提交以正文说明为主。"
                          />
                        )}
                      </div>
                    </div>

                    <div className="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm">
                      <div className="flex items-center gap-2 text-[15px] font-bold text-slate-500">
                        <Award size={15} />
                        平台证据
                      </div>
                      <div className="mt-4 grid gap-4 xl:grid-cols-[minmax(0,0.72fr)_minmax(0,1fr)]">
                        <div className="rounded-2xl border border-emerald-100/70 bg-emerald-50/50 p-5">
                          <div className="flex items-center gap-2 text-[18px] font-bold text-slate-800">
                            <Award size={16} className="text-emerald-500" />
                            近 7 天社区贡献分
                          </div>
                          <div className="mt-3 text-[36px] font-black tracking-tight text-emerald-700">
                            {selectedSubmission.communityScore7d}
                          </div>
                          <div className="mt-2 text-[15px] leading-7 text-slate-500">
                            可作为补充参考，帮助快速判断社区活跃度。
                          </div>
                        </div>

                        <div className="rounded-2xl border border-slate-200 bg-slate-50/55 p-5">
                          <div className="flex items-center gap-2 text-[18px] font-bold text-slate-800">
                            <User size={16} className="text-slate-500" />
                            画像标签
                          </div>
                          <div className="mt-3 flex flex-wrap gap-2">
                            {selectedSubmission.portraitTags.length > 0 ? (
                              selectedSubmission.portraitTags.map((tag) => (
                                <span key={tag} className="rounded-full border border-slate-200 bg-white px-3 py-1 text-[15px] font-medium text-slate-600">
                                  {tag}
                                </span>
                              ))
                            ) : (
                              <span className="text-[15px] text-slate-400">暂未生成画像标签。</span>
                            )}
                          </div>
                          <div className="mt-3 text-[15px] text-slate-400">
                            最近画像更新时间：{selectedSubmission.portraitUpdatedAt ? formatDateTime(selectedSubmission.portraitUpdatedAt) : "暂无"}
                          </div>
                        </div>
                      </div>
                    </div>

                    {selectedSubmission.reviewComment?.trim() ? (
                      <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                        <div className="flex items-center gap-2 text-[15px] font-bold text-slate-500">
                          <History size={15} />
                          处理备注
                        </div>
                        <div className="mt-3 text-[17px] leading-8 text-slate-600 whitespace-pre-line">
                          {selectedSubmission.reviewComment}
                        </div>
                      </div>
                    ) : null}
                  </div>
                ) : (
                  <SectionEmptyState
                    icon={Sparkles}
                    title="先从左侧选择一份学生提交"
                    desc="选择一位学生后，即可在这里集中阅读提交内容。"
                  />
                )}
              </div>

              {selectedSubmission ? (
                <div className="border-t border-slate-100 bg-white px-5 py-5">
                  {disableReason ? (
                    <div className="mb-4 rounded-2xl border border-amber-100 bg-amber-50/80 p-4 text-[16px] leading-7 text-amber-900">
                      <div className="flex items-start gap-3">
                        <AlertCircle size={18} className="mt-0.5 shrink-0 text-amber-600" />
                        <div>{disableReason}</div>
                      </div>
                    </div>
                  ) : null}

                  <div className={joinClasses(
                    "flex flex-wrap items-center gap-3",
                    nextPendingSubmission ? "justify-between" : "justify-center",
                  )}>
                    <div className="flex flex-wrap items-center gap-3">
                      <button
                        type="button"
                        onClick={() => {
                          setDecisionMode("contact");
                          setDecisionConfigOpen(true);
                        }}
                        disabled={!!disableReason || submittingDecision}
                        className={joinClasses(
                          "inline-flex items-center justify-center rounded-2xl px-5 py-3 text-[16px] font-bold text-white transition-colors",
                          "bg-emerald-600 hover:bg-emerald-500",
                          (!!disableReason || submittingDecision) && "cursor-not-allowed bg-slate-300 hover:bg-slate-300",
                        )}
                      >
                        继续接触
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          setDecisionMode("reject");
                          setDecisionConfigOpen(true);
                        }}
                        disabled={!!disableReason || submittingDecision}
                        className={joinClasses(
                          "inline-flex items-center justify-center rounded-2xl px-5 py-3 text-[16px] font-bold text-white transition-colors",
                          "bg-slate-900 hover:bg-slate-800",
                          (!!disableReason || submittingDecision) && "cursor-not-allowed bg-slate-300 hover:bg-slate-300",
                        )}
                      >
                        未入选通知
                      </button>
                    </div>

                    {nextPendingSubmission ? (
                      <button
                        type="button"
                        onClick={() => focusPendingSubmission(nextPendingSubmission.submissionId)}
                        className="inline-flex items-center justify-center rounded-2xl border border-slate-200 bg-white px-5 py-3 text-[16px] font-bold text-slate-700 transition-colors hover:border-slate-300 hover:bg-slate-50"
                      >
                        处理下一位待审核
                      </button>
                    ) : null}
                  </div>
                </div>
              ) : null}
            </motion.section>

          </div>
        </motion.div>
      </WorkspaceFrame>

      <TaskRequirementsModal
        open={taskRequirementsOpen}
        task={bundle.task}
        onClose={() => setTaskRequirementsOpen(false)}
      />

      <ReviewAssistDrawer
        tab={assistDrawerTab}
        submission={selectedSubmission}
        historyEntries={historyEntries}
        onClose={() => setAssistDrawerTab(null)}
      />

      <DecisionSetupModal
        open={decisionConfigOpen && !!selectedSubmission}
        mode={decisionMode}
        studentName={selectedSubmission?.studentName ?? null}
        contactIntent={contactIntent}
        rejectTemplate={rejectTemplate}
        actionNote={actionNote}
        syncEmailReminder={syncEmailReminder}
        previewComment={previewComment}
        disableReason={disableReason}
        submittingDecision={submittingDecision}
        onClose={() => setDecisionConfigOpen(false)}
        onPickContactIntent={setContactIntent}
        onPickRejectTemplate={setRejectTemplate}
        onActionNoteChange={setActionNote}
        onToggleEmailReminder={() => setSyncEmailReminder((current) => !current)}
        onConfirm={() => {
          setDecisionConfigOpen(false);
          handleRequestDecisionSubmit();
        }}
      />

      <AnimatePresence>
        {decisionConfirmOpen && selectedSubmission ? (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-[80] flex items-center justify-center bg-slate-950/30 p-4 backdrop-blur-sm"
            onClick={() => setDecisionConfirmOpen(false)}
          >
            <motion.div
              initial={{ opacity: 0, y: 18, scale: 0.96 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 18, scale: 0.98 }}
              transition={{ type: "spring", stiffness: 280, damping: 26 }}
              className="w-full max-w-2xl overflow-hidden rounded-[2rem] border border-white/80 bg-white shadow-[0_30px_80px_rgba(15,23,42,0.24)]"
              onClick={(event) => event.stopPropagation()}
              role="dialog"
              aria-modal="true"
              aria-labelledby="enterprise-review-confirm-title"
            >
              <div className="border-b border-slate-100 bg-slate-50/80 px-6 py-5 sm:px-7">
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <div className="text-[13px] font-bold text-slate-400">发送前确认</div>
                    <h2 id="enterprise-review-confirm-title" className="mt-2 text-[24px] font-bold tracking-tight text-slate-900">
                      {decisionConfirmTitle}
                    </h2>
                    <p className="mt-2 text-[16px] leading-7 text-slate-500">
                      确认后，将向 {selectedSubmission.studentName} 发送{decisionActionLabel}结果，并记录本次处理内容。
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() => setDecisionConfirmOpen(false)}
                    className="inline-flex shrink-0 items-center justify-center rounded-full border border-slate-200 bg-white px-4 py-2 text-[14px] font-semibold text-slate-600 transition-colors hover:border-slate-300 hover:text-slate-900"
                  >
                    返回修改
                  </button>
                </div>
              </div>

              <div className="space-y-5 px-6 py-6 sm:px-7">
                <div className="grid gap-3 sm:grid-cols-3">
                  <div className="rounded-2xl border border-slate-200 bg-slate-50/70 px-4 py-3">
                    <div className="flex items-center gap-2 text-[13px] font-bold text-slate-400">
                      <User size={14} />
                      学生信息
                    </div>
                    <div className="mt-3 flex items-center gap-3">
                      <StudentIdentityAvatar
                        userId={selectedSubmission.studentUserId}
                        role="STUDENT"
                        displayName={selectedSubmission.studentName}
                        avatar={selectedSubmission.studentAvatar}
                        avatarPath={buildStudentAvatarPath(selectedSubmission.studentUserId, selectedSubmission.studentAvatar?.updatedAt)}
                        className="h-11 w-11 border border-white bg-white shadow-sm"
                        textClassName="text-[15px]"
                      />
                      <div className="min-w-0">
                        <div className="text-[16px] font-semibold text-slate-900">{selectedSubmission.studentName}</div>
                        <div className="mt-1 text-[14px] text-slate-500">提交于 {formatDateTime(selectedSubmission.createdAt)}</div>
                      </div>
                    </div>
                  </div>
                  <div className="rounded-2xl border border-slate-200 bg-slate-50/70 px-4 py-3">
                    <div className="flex items-center gap-2 text-[13px] font-bold text-slate-400">
                      <Send size={14} />
                      本次动作
                    </div>
                    <div className="mt-2 text-[16px] font-semibold text-slate-900">{decisionActionLabel}</div>
                    <div className="mt-1 text-[14px] text-slate-500">{decisionDetailLabel}</div>
                  </div>
                  <div className="rounded-2xl border border-slate-200 bg-slate-50/70 px-4 py-3">
                    <div className="flex items-center gap-2 text-[13px] font-bold text-slate-400">
                      <Bell size={14} />
                      通知方式
                    </div>
                    <div className="mt-2 text-[16px] font-semibold text-slate-900">站内通知</div>
                    <div className="mt-1 text-[14px] text-slate-500">
                      {syncEmailReminder ? "同时请求邮件提醒" : "本次不额外发送邮件"}
                    </div>
                  </div>
                </div>

                <div className="rounded-[1.5rem] border border-slate-200 bg-slate-50/70 p-5">
                  <div className="flex items-center gap-2 text-[16px] font-bold text-slate-800">
                    <MessageSquare size={16} className="text-slate-400" />
                    发送内容预览
                  </div>
                  <div className="mt-3 rounded-2xl border border-slate-200 bg-white p-4 text-[16px] leading-7 text-slate-600 whitespace-pre-line">
                    {previewComment}
                  </div>
                </div>

                <div className="rounded-2xl border border-slate-200 bg-white px-4 py-4">
                  <div className="flex items-center gap-2 text-[14px] font-bold text-slate-800">
                    <CalendarDays size={15} className="text-slate-500" />
                    任务信息
                  </div>
                  <div className="mt-2 text-[14px] leading-6 text-slate-500">
                    {bundle.task.deadlineAt
                      ? `任务截止：${formatDateTime(bundle.task.deadlineAt)}`
                      : "未设置截止时间。"}
                  </div>
                  <div className="mt-2 text-[13px] leading-5 text-slate-400">
                    {nextPendingSubmission
                      ? `发送后，仍有 ${pendingCount - (isPendingSubmission(selectedSubmission.status) ? 1 : 0)} 份待处理提交。`
                      : "发送后将返回提交列表。"}
                  </div>
                </div>

                <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
                  <button
                    type="button"
                    onClick={() => setDecisionConfirmOpen(false)}
                    className="inline-flex items-center justify-center rounded-2xl border border-slate-200 bg-white px-5 py-3 text-[16px] font-bold text-slate-700 transition-colors hover:border-slate-300 hover:bg-slate-50"
                  >
                    再检查一下
                  </button>
                  <button
                    type="button"
                    onClick={() => void handleDecisionSubmit()}
                    disabled={submittingDecision}
                    className={joinClasses(
                      "inline-flex items-center justify-center rounded-2xl px-5 py-3 text-[16px] font-bold text-white transition-colors",
                      decisionMode === "contact"
                        ? "bg-emerald-600 hover:bg-emerald-500"
                        : "bg-slate-900 hover:bg-slate-800",
                      submittingDecision && "cursor-not-allowed bg-slate-300 hover:bg-slate-300",
                    )}
                  >
                    {submittingDecision ? "正在提交结果..." : `确认发送${decisionActionLabel}`}
                  </button>
                </div>
              </div>
            </motion.div>
          </motion.div>
        ) : null}
      </AnimatePresence>

      <AnimatePresence>
        {toast ? (
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 20 }}
            className="fixed bottom-6 left-1/2 z-50 -translate-x-1/2"
          >
            <div className={joinClasses(
              "min-w-[18rem] rounded-full px-5 py-3 text-[16px] font-semibold shadow-[0_16px_36px_rgba(15,23,42,0.16)] backdrop-blur-xl",
              toast.tone === "success" && "bg-emerald-600 text-white",
              toast.tone === "error" && "bg-rose-600 text-white",
              toast.tone === "info" && "bg-slate-900 text-white",
            )}>
              {toast.message}
            </div>
          </motion.div>
        ) : null}
      </AnimatePresence>
    </>
  );
}
