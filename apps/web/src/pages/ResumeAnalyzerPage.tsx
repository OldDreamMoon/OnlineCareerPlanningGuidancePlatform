import { AnimatePresence, motion, type Variants } from "framer-motion";
import {
  AlertTriangle,
  AlignLeft,
  ArrowLeft,
  ArrowRight,
  Award,
  Bot,
  Check,
  CheckCircle2,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Clock3,
  Copy,
  Download,
  FileText,
  History,
  LayoutTemplate,
  MessageSquare,
  RefreshCw,
  ShieldAlert,
  Sparkles,
  Target,
  Trash2,
  UploadCloud,
  WandSparkles,
  X,
  Zap,
  type LucideIcon,
} from "lucide-react";
import {
  Suspense,
  lazy,
  startTransition,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ChangeEvent,
  type DragEvent,
  type KeyboardEvent,
  type ReactNode,
  type RefObject,
} from "react";
import { Link, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import StudentWorkspaceTopbar, { buildStudentWorkspacePrimaryNav } from "../components/student/StudentWorkspaceTopbar";
import { ApiClientError, apiRequest, buildQuery } from "../lib/apiClient";
import { moderationActionLabelMap, riskLevelLabelMap } from "../lib/adminLabels";
import { formatCount, formatDateTime, formatRelativeTime as formatRelativeTimeByBrowserTimezone } from "../lib/formatters";
import type { ResumeHistoryDetail } from "../lib/resumeReportPdfDocument";
import { getRoleDisplayLabel } from "../lib/roleLabels";
import { buildStudentNickname } from "../lib/studentNames";
import { type StudentAvatarMeta } from "../lib/studentAvatar";

const ResumeAnalyzerOverlay = lazy(() => import("./ResumeAnalyzerOverlays"));

type TimeValue = number | string;
type AnalyzeMode = "text" | "pdf";
type AnalyzeStatus = "idle" | "analyzing" | "complete" | "error";
type RouteMode = "compose" | "review";
type NoticeTone = "success" | "warning" | "info" | "error";

type StudentWorkspaceProfile = {
  userId: number;
  displayName: string;
  realName: string | null;
  major: string | null;
  grade: string | null;
  targetPosition: string | null;
  skillTags: string[] | null;
  selfIntro: string | null;
  avatar: StudentAvatarMeta | null;
  tier: string | null;
};

type ResumeAnalyzeResponse = {
  recordId: number | null;
  summary: string;
  strengths: string[];
  risks: string[];
  suggestions: string[];
  scoreLabel?: string | null;
  structureItems?: StructureMapItem[] | null;
  rewriteItems?: RewriteSandboxItem[] | null;
  aiMeta: {
    taskType: string;
    latencyMs: number;
  } | null;
  moderation: {
    sourceType: string;
    riskLevel: string;
    action: string;
    reasonCode: string;
  } | null;
};

type ResumeHistoryItem = {
  id: number;
  taskType: string;
  summary: string;
  pointsConsumed: number;
  sessionId: string | null;
  status: string | null;
  createdAt: TimeValue;
};

type ResumeHistoryResponse = {
  records: ResumeHistoryItem[];
  total: number;
  page: number;
  size: number;
};

type ResumeHistoryDetailResponse = ResumeAnalyzeResponse & {
  recordId: number;
  targetRole: string;
  targetContext: string;
  inputMode: AnalyzeMode;
  jobDescription: string;
  resumeText: string;
  pdfFileName: string | null;
  pointsConsumed: number;
  createdAt: TimeValue;
};

type AiQuotaRemainingResponse = {
  tier: string;
  quotas: Array<{
    taskType: string;
    dailyFreeLimit: number;
    usedToday: number;
    remaining: number;
  }>;
  pointsBalance: number;
};

type ResumeAsyncTaskSubmitResponse = {
  taskId: string;
  taskType: string;
  sceneCode: string;
  executionMode: string;
  status: string;
  createdAt: TimeValue;
};

type ResumeAsyncTaskDetailResponse = {
  taskId: string;
  taskType: string;
  sceneCode: string;
  routeCode: string | null;
  executionMode: string;
  status: string;
  terminal: boolean;
  currentAttempt: number;
  maxAttempts: number;
  providerCode: string | null;
  providerType: string | null;
  modelName: string | null;
  promptTemplateName: string | null;
  promptTemplateVersionNo: number | null;
  resultSummary: string | null;
  resultPayload: ResumeAnalyzeResponse | null;
  linkedRecordId: number | null;
  errorCode: string | null;
  errorMessage: string | null;
  nextRunAt: TimeValue | null;
  queuedAt: TimeValue | null;
  startedAt: TimeValue | null;
  finishedAt: TimeValue | null;
  createdAt: TimeValue;
  updatedAt: TimeValue;
};

type StreamPreviewState = {
  traceId: string | null;
  message: string | null;
  summary: string | null;
  strengths: string[] | null;
  risks: string[] | null;
  suggestions: string[] | null;
};

type ResumeComposeDraft = {
  version: 1;
  savedAt: string;
  targetContext: string;
  targetRole: string;
  inputMode: AnalyzeMode;
  jobDescription: string;
  resumeText: string;
  pdfFileName: string | null;
};

type ResumeReviewSnapshot = {
  version: 1;
  savedAt: string;
  completedAt: string;
  targetContext: string;
  targetRole: string;
  inputMode: AnalyzeMode;
  jobDescription: string;
  resumeText: string;
  pdfFileName: string | null;
  recordId: number | null;
  approximateBinding: boolean;
  result: ResumeAnalyzeResponse;
};

type ResumeHistoryLocalMeta = {
  targetContext: string;
  targetRole: string;
  inputMode: AnalyzeMode;
  jobDescription: string;
  savedAt: string;
  scoreLabel: string;
};

type ExportFeedbackState = {
  recordId: number;
  tone: NoticeTone;
  message: string;
};

type ReportSection = {
  id: "strengths" | "risks" | "suggestions";
  title: string;
  sectionLabel: string;
  items: string[];
  emptyText: string;
  icon: LucideIcon;
  tone: "success" | "warning" | "info";
};

type StructureMapItem = {
  label: string;
  score: number;
  tip: string;
};

type RewriteSandboxItem = {
  id: string;
  title: string;
  problem: string;
  beforeText: string;
  afterText: string;
  isHeuristic: boolean;
};

type ScoreStyle = {
  text: string;
  cardBg: string;
  cardText: string;
  cardSubtext: string;
  shadow: string;
  border: string;
};

type ReviewWorkspaceData = {
  source: "latest" | "history" | "recent-history";
  recordId: number | null;
  report: ResumeAnalyzeResponse;
  targetContext: string;
  targetRole: string;
  inputMode: AnalyzeMode;
  jobDescription: string;
  resumeText: string;
  pdfFileName: string | null;
  createdAt: TimeValue | null;
  approximateBinding: boolean;
};

type WorkspaceHeaderProps = {
  routeMode: RouteMode;
  studentName: string;
  roleLabel: string;
  userId: number | null;
  avatar: StudentAvatarMeta | null;
  tier: string | null;
  historyCount: number;
  onOpenHistory: () => void;
  reviewBackHref: string;
  reviewBackLabel: string;
};

type ComposePageProps = {
  draft: ResumeComposeDraft;
  resumeFile: File | null;
  profile: StudentWorkspaceProfile | null;
  profileDraft: string;
  quota: AiQuotaRemainingResponse | null;
  quotaLoading: boolean;
  quotaError: string | null;
  status: AnalyzeStatus;
  errorMessage: string | null;
  isAnalyzeDisabled: boolean;
  draftSavedAt: string | null;
  fileInputRef: RefObject<HTMLInputElement>;
  onOpenHistory: () => void;
  onOpenReviewCenter: () => void;
  onRefreshQuota: () => void;
  onAnalyze: () => void;
  onUseProfileDraft: () => void;
  onClearDraft: () => void;
  onTargetContextChange: (value: string) => void;
  onTargetRoleChange: (value: string) => void;
  onJobDescriptionChange: (value: string) => void;
  onInputModeChange: (mode: AnalyzeMode) => void;
  onResumeTextChange: (value: string) => void;
  onFileInputChange: (event: ChangeEvent<HTMLInputElement>) => void;
  onFileDrop: (event: DragEvent<HTMLLabelElement>) => void;
  onRemoveFile: () => void;
};

type ReviewPageProps = {
  reviewData: ReviewWorkspaceData | null;
  taskId: string | null;
  taskDetail: ResumeAsyncTaskDetailResponse | null;
  taskLoading: boolean;
  taskError: string | null;
  scoreLabel: string;
  scoreStyle: ScoreStyle;
  reviewSections: ReportSection[];
  structureItems: StructureMapItem[];
  rewriteItems: RewriteSandboxItem[];
  exportFeedback: ExportFeedbackState | null;
  exportingRecordId: number | null;
  copiedKey: string | null;
  historyCount: number;
  historyMode: boolean;
  backActionLabel: string;
  selectedHistoryLoading: boolean;
  selectedHistoryError: string | null;
  onBackToCompose: () => void;
  onDismissExportFeedback: () => void;
  onOpenHistory: () => void;
  onOpenReviewCenter: () => void;
  onExitHistory: () => void;
  onRefreshTask: () => void;
  onExport: (recordId: number, detail?: ResumeHistoryDetail) => void;
  onCopySummary: () => void;
  onCopyReport: () => void;
  onCopyRewrite: (item: RewriteSandboxItem, index: number) => void;
  onCycleRewrite: (index: number) => void;
};

const TARGET_CONTEXT_OPTIONS = [
  "实习投递 / 日常实习",
  "校招提前批",
  "校招正式批 / 秋招春招",
  "社招初阶 (1-3年)",
  "社招中高阶 (3年以上)",
  "跨专业 / 跨行转岗",
  "外企 / 海外岗",
  "自由职业 / 独立开发者",
];

const RESUME_HISTORY_PAGE_SIZE = 6;
const RESUME_DRAFT_STORAGE_KEY_PREFIX = "bishe.resume.compose.draft.v2";
const RESUME_REVIEW_STORAGE_KEY_PREFIX = "bishe.resume.review.latest.v1";
const RESUME_HISTORY_META_STORAGE_KEY_PREFIX = "bishe.resume.history.meta.v1";
const DEFAULT_TARGET_CONTEXT = TARGET_CONTEXT_OPTIONS[0];

const staggerContainer: Variants = {
  hidden: { opacity: 0 },
  show: {
    opacity: 1,
    transition: { staggerChildren: 0.08 },
  },
};

const fadeUpItem: Variants = {
  hidden: { opacity: 0, y: 16 },
  show: {
    opacity: 1,
    y: 0,
    transition: { type: "spring", stiffness: 260, damping: 24 },
  },
};

function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function getExportToastPresentation(tone: NoticeTone) {
  if (tone === "success") {
    return {
      title: "导出已就绪",
      icon: CheckCircle2,
      shellClassName: "border border-emerald-100/90 bg-[#f2fcf7] ring-1 ring-emerald-100/90 shadow-[0_18px_40px_rgba(16,185,129,0.12)]",
      iconClassName: "bg-emerald-100 text-emerald-600",
      titleClassName: "text-emerald-700",
      messageClassName: "text-slate-600",
    };
  }

  if (tone === "info") {
    return {
      title: "正在准备",
      icon: Clock3,
      shellClassName: "border border-sky-100/90 bg-[#f3f9ff] ring-1 ring-sky-100/90 shadow-[0_18px_40px_rgba(96,165,250,0.12)]",
      iconClassName: "bg-sky-100 text-sky-600",
      titleClassName: "text-sky-700",
      messageClassName: "text-slate-600",
    };
  }

  if (tone === "warning") {
    return {
      title: "请先处理",
      icon: AlertTriangle,
      shellClassName: "border border-amber-100/90 bg-[#fff9ef] ring-1 ring-amber-100/90 shadow-[0_18px_40px_rgba(245,158,11,0.14)]",
      iconClassName: "bg-amber-100 text-amber-600",
      titleClassName: "text-amber-700",
      messageClassName: "text-slate-600",
    };
  }

  return {
    title: "导出未完成",
    icon: AlertTriangle,
    shellClassName: "border border-rose-100/90 bg-[#fff4f6] ring-1 ring-rose-100/90 shadow-[0_18px_40px_rgba(244,114,182,0.12)]",
    iconClassName: "bg-rose-100 text-rose-600",
    titleClassName: "text-rose-700",
    messageClassName: "text-slate-600",
  };
}

function getStorage(kind: "local" | "session") {
  if (typeof window === "undefined") {
    return null;
  }

  return kind === "local" ? window.localStorage : window.sessionStorage;
}

function readStorageJson<T>(kind: "local" | "session", key: string) {
  const storage = getStorage(kind);
  if (!storage) {
    return null;
  }

  const raw = storage.getItem(key);
  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw) as T;
  } catch {
    storage.removeItem(key);
    return null;
  }
}

function writeStorageJson(kind: "local" | "session", key: string, value: unknown) {
  const storage = getStorage(kind);
  if (!storage) {
    return;
  }

  try {
    storage.setItem(key, JSON.stringify(value));
  } catch {
    // 忽略本地缓存失败，不阻断主流程。
  }
}

function removeStorageKey(kind: "local" | "session", key: string) {
  const storage = getStorage(kind);
  if (!storage) {
    return;
  }

  storage.removeItem(key);
}

function getResumeDraftStorageKey(userId: number) {
  return `${RESUME_DRAFT_STORAGE_KEY_PREFIX}.${userId}`;
}

function getResumeReviewStorageKey(userId: number) {
  return `${RESUME_REVIEW_STORAGE_KEY_PREFIX}.${userId}`;
}

function getResumeHistoryMetaStorageKey(userId: number) {
  return `${RESUME_HISTORY_META_STORAGE_KEY_PREFIX}.${userId}`;
}

function createDefaultDraft(): ResumeComposeDraft {
  return {
    version: 1,
    savedAt: "",
    targetContext: DEFAULT_TARGET_CONTEXT,
    targetRole: "",
    inputMode: "pdf",
    jobDescription: "",
    resumeText: "",
    pdfFileName: null,
  };
}

function hasMeaningfulDraft(draft: ResumeComposeDraft | null) {
  if (!draft) {
    return false;
  }

  return Boolean(
    draft.targetRole.trim()
      || draft.jobDescription.trim()
      || draft.resumeText.trim()
      || draft.pdfFileName,
  );
}

function buildProfileDraft(profile: StudentWorkspaceProfile | null) {
  if (!profile) {
    return "";
  }

  const nickname = buildStudentNickname(profile);
  const lines = [
    nickname ? `昵称：${nickname}` : null,
    profile.realName?.trim() && profile.realName.trim() !== nickname ? `真实姓名：${profile.realName.trim()}` : null,
    profile.grade?.trim() ? `年级：${profile.grade.trim()}` : null,
    profile.major?.trim() ? `专业：${profile.major.trim()}` : null,
    profile.targetPosition?.trim() ? `目标岗位：${profile.targetPosition.trim()}` : null,
    profile.skillTags && profile.skillTags.length > 0
      ? `技能关键词：${profile.skillTags.join("、")}`
      : null,
    profile.selfIntro?.trim() ? `当前阶段说明：${profile.selfIntro.trim()}` : null,
  ].filter(Boolean);

  return lines.join("\n");
}

function formatFileSize(file: File | null) {
  if (!file) {
    return "—";
  }

  if (file.size < 1024 * 1024) {
    return `${Math.max(file.size / 1024, 0.1).toFixed(1)} KB`;
  }

  return `${(file.size / (1024 * 1024)).toFixed(2)} MB`;
}

function formatQuotaLimit(value: number | undefined) {
  if (value === undefined) {
    return "—";
  }

  if (value < 0) {
    return "不限";
  }

  return `${value} 次`;
}

function buildAnalyzeErrorMessage(error: unknown, mode: AnalyzeMode) {
  if (error instanceof ApiClientError) {
    const providerStatus = readApiErrorNumber(error, "providerStatus");
    if (providerStatus === 429) {
      return "当前 AI 服务请求过多，请稍后再试。";
    }

    if (providerStatus === 408 || providerStatus === 504) {
      return "AI 服务响应超时，请稍后重试。";
    }

    if (providerStatus === 502 || providerStatus === 503) {
      return "当前 AI 服务暂时不可用，请稍后再试。";
    }

    if (providerStatus !== null && providerStatus >= 500) {
      return "AI 服务暂时异常，请稍后重试。";
    }

    if (error.code === "MOD-1001") {
      return "当前输入内容触发了安全策略拦截，请调整简历内容后再试。";
    }

    if (error.code === "BIZ-1001" && mode === "pdf") {
      return "当前上传文件不是 PDF，请重新选择简历文件。";
    }

    if (error.code === "AI-2001") {
      return error.message || "AI 服务暂时异常，请稍后重试。";
    }

    if (error.code === "AI-2202") {
      const retryAfterSeconds = readApiErrorNumber(error, "retryAfterSeconds");
      return retryAfterSeconds !== null && retryAfterSeconds > 0
        ? `当前请求过快，请 ${retryAfterSeconds} 秒后再试。`
        : "当前请求过快，请稍后再试。";
    }

    return error.message || "简历诊断暂时失败，请稍后重试。";
  }

  if (error instanceof Error && error.message) {
    return error.message;
  }

  return "简历诊断暂时失败，请稍后重试。";
}

function buildAsyncTaskErrorMessage(taskDetail: ResumeAsyncTaskDetailResponse | null, mode: AnalyzeMode) {
  if (!taskDetail) {
    return buildAnalyzeErrorMessage(new Error("简历诊断暂时失败，请稍后重试。"), mode);
  }

  if (taskDetail.errorCode === "AI-2001") {
    return taskDetail.errorMessage?.trim() || "AI 服务暂时异常，请稍后重试。";
  }

  if (taskDetail.errorCode === "BIZ-1001" && mode === "pdf") {
    return "当前上传文件不是 PDF，请重新选择简历文件。";
  }

  if (taskDetail.errorCode === "MOD-1001") {
    return "当前输入内容触发了安全策略拦截，请调整简历内容后再试。";
  }

  return taskDetail.errorMessage?.trim() || "这次诊断没有成功完成，请稍后重试。";
}

function readApiErrorDataRecord(error: ApiClientError) {
  return error.data && typeof error.data === "object"
    ? (error.data as Record<string, unknown>)
    : null;
}

function readApiErrorNumber(error: ApiClientError, key: string) {
  const record = readApiErrorDataRecord(error);
  const value = record?.[key];
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function getAsyncTaskStatusLabel(status: string | null) {
  switch (status) {
    case "PENDING":
      return "排队中";
    case "RUNNING":
      return "处理中";
    case "SUCCEEDED":
      return "已完成";
    case "FAILED":
      return "失败";
    default:
      return "处理中";
  }
}

function buildAsyncTaskProgressMessage(taskDetail: ResumeAsyncTaskDetailResponse | null) {
  if (!taskDetail) {
    return "诊断任务已经提交，正在进入后台队列。";
  }

  if (taskDetail.status === "PENDING") {
    return "诊断任务已进入后台队列，正在等待 worker 开始处理。";
  }

  if (taskDetail.status === "RUNNING") {
    return taskDetail.currentAttempt > 1
      ? `后台正在重试第 ${taskDetail.currentAttempt} 次诊断，请稍候。`
      : "后台正在解析简历内容，并生成结构化诊断结果。";
  }

  if (taskDetail.status === "SUCCEEDED") {
    return "诊断结果已经生成，正在整理详情内容。";
  }

  if (taskDetail.status === "FAILED") {
    return "诊断任务未能成功完成，你可以返回编辑页调整后重试。";
  }

  return "后台正在处理这份简历，请稍候。";
}

function extractResumeTaskResult(taskDetail: ResumeAsyncTaskDetailResponse | null) {
  if (!taskDetail?.resultPayload) {
    return null;
  }

  const recordId = taskDetail.linkedRecordId ?? taskDetail.resultPayload.recordId ?? null;
  return {
    ...taskDetail.resultPayload,
    recordId,
  } satisfies ResumeAnalyzeResponse;
}

function parseSseBlock(block: string) {
  const normalizedBlock = block.trim();

  if (!normalizedBlock) {
    return null;
  }

  let eventName = "message";
  const dataLines: string[] = [];

  normalizedBlock.split("\n").forEach((line) => {
    if (line.startsWith("event:")) {
      eventName = line.slice(6).trim();
      return;
    }

    if (line.startsWith("data:")) {
      dataLines.push(line.slice(5).trim());
    }
  });

  const rawData = dataLines.join("\n");

  if (!rawData) {
    return { eventName, payload: null };
  }

  try {
    return {
      eventName,
      payload: JSON.parse(rawData) as Record<string, unknown>,
    };
  } catch {
    return {
      eventName,
      payload: { message: rawData },
    };
  }
}

async function buildApiClientErrorFromResponse(response: Response, fallbackMessage: string) {
  const rawText = await response.text();

  if (!rawText) {
    return new ApiClientError(fallbackMessage, response.status);
  }

  try {
    const payload = JSON.parse(rawText) as {
      message?: string;
      code?: string;
      traceId?: string | null;
      data?: unknown;
    };

    return new ApiClientError(
      payload.message ?? fallbackMessage,
      response.status,
      payload.code ?? "HTTP_ERROR",
      payload.traceId ?? null,
      payload.data ?? null,
    );
  } catch {
    return new ApiClientError(rawText, response.status);
  }
}

async function copyText(text: string) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text);
    return;
  }

  const textArea = document.createElement("textarea");
  textArea.value = text;
  textArea.style.position = "fixed";
  textArea.style.opacity = "0";
  document.body.appendChild(textArea);
  textArea.focus();
  textArea.select();
  document.execCommand("copy");
  textArea.remove();
}

async function loadResumeReportExporter() {
  return import("../lib/resumeReportPrint");
}

function formatRelativeTime(value: TimeValue | null) {
  return formatRelativeTimeByBrowserTimezone(value, "刚刚");
}

function formatDurationSeconds(value: number | null | undefined) {
  if (typeof value !== "number" || Number.isNaN(value)) {
    return null;
  }

  return `${(value / 1000).toFixed(2)} 秒`;
}

function formatModerationSummary(moderation: ResumeAnalyzeResponse["moderation"]) {
  if (!moderation) {
    return null;
  }

  const actionLabel = moderationActionLabelMap[moderation.action] ?? moderation.action;
  const riskLabel = riskLevelLabelMap[moderation.riskLevel] ?? moderation.riskLevel;
  const parts = [actionLabel, riskLabel].filter((item) => item && item.trim().length > 0);
  return parts.length ? parts.join(" / ") : "已完成审核";
}

function getStructureTipFallback(label: string, score: number) {
  switch (label) {
    case "基本信息":
      return score >= 4 ? "表达完整，阅读较顺畅" : "建议补联系信息与求职目标";
    case "教育背景":
      return score >= 4 ? "教育信息较完整" : "建议补学校、专业与阶段";
    case "技能描述":
      return score >= 4 ? "技能栈较完整" : "建议补技术关键词与熟练场景";
    case "项目经历":
      return score >= 4 ? "项目主线较清晰" : "建议补技术动作与结果";
    case "实习经历":
      return score >= 4 ? "业务价值表达较完整" : "建议补业务背景与个人贡献";
    case "岗位匹配度":
      return score >= 4 ? "与目标岗位较贴合" : "建议补岗位关键词与成果";
    default:
      return score >= 4 ? "整体表达较完整" : "建议继续补强岗位证据";
  }
}

function shortenStructureTip(label: string, tip: string, score: number) {
  const normalized = tip.trim().replace(/[。！!]+$/g, "");
  if (!normalized) {
    return getStructureTipFallback(label, score);
  }

  const exactMap: Record<string, string> = {
    "技术栈全面，但可针对核心项目深入挖掘技术挑战和解决方案": "技术栈较全，可补核心技术亮点",
    "多数项目成果有清晰的量化数据，但部分可进一步突出业务影响": "量化结果较清晰，可再补业务影响",
  };

  if (exactMap[normalized]) {
    return exactMap[normalized];
  }

  if (normalized.length <= 18) {
    return normalized;
  }

  if (/量化/.test(normalized) && /业务影响|业务价值/.test(normalized)) {
    return "量化结果较清晰，可再补业务影响";
  }

  if (/技术栈|技术挑战|解决方案/.test(normalized)) {
    return "技术栈较全，可补核心技术亮点";
  }

  if (/技术动作/.test(normalized) && /量化/.test(normalized)) {
    return "建议补技术动作与结果";
  }

  if (/业务背景/.test(normalized) && /个人贡献/.test(normalized)) {
    return "建议补业务背景与个人贡献";
  }

  if (/学校|专业|学历|阶段/.test(normalized)) {
    return "建议补学校、专业与阶段";
  }

  if (/联系|联系方式|求职目标/.test(normalized)) {
    return "建议补联系信息与求职目标";
  }

  if (/岗位|关键词/.test(normalized) && /成果|匹配|对齐/.test(normalized)) {
    return "建议补岗位关键词与成果";
  }

  return getStructureTipFallback(label, score);
}

function getStructureLabelTone(label: string, index: number) {
  const normalized = label.trim();

  if (normalized.includes("基本")) {
    return "border-sky-200 bg-gradient-to-r from-sky-100 to-cyan-100 text-sky-800";
  }

  if (normalized.includes("教育")) {
    return "border-violet-200 bg-gradient-to-r from-violet-100 to-fuchsia-100 text-violet-800";
  }

  if (normalized.includes("技能")) {
    return "border-indigo-200 bg-gradient-to-r from-indigo-100 to-blue-100 text-indigo-800";
  }

  if (normalized.includes("项目")) {
    return "border-emerald-200 bg-gradient-to-r from-emerald-100 to-teal-100 text-emerald-800";
  }

  if (normalized.includes("实习")) {
    return "border-amber-200 bg-gradient-to-r from-amber-100 to-orange-100 text-amber-800";
  }

  if (normalized.includes("岗位") || normalized.includes("匹配")) {
    return "border-rose-200 bg-gradient-to-r from-rose-100 to-pink-100 text-rose-800";
  }

  const fallbackTones = [
    "border-sky-200 bg-gradient-to-r from-sky-100 to-cyan-100 text-sky-800",
    "border-violet-200 bg-gradient-to-r from-violet-100 to-fuchsia-100 text-violet-800",
    "border-indigo-200 bg-gradient-to-r from-indigo-100 to-blue-100 text-indigo-800",
    "border-emerald-200 bg-gradient-to-r from-emerald-100 to-teal-100 text-emerald-800",
    "border-amber-200 bg-gradient-to-r from-amber-100 to-orange-100 text-amber-800",
    "border-rose-200 bg-gradient-to-r from-rose-100 to-pink-100 text-rose-800",
  ];

  return fallbackTones[index % fallbackTones.length];
}

function buildReportSections(
  report: Pick<ResumeAnalyzeResponse, "strengths" | "risks" | "suggestions"> | null,
): ReportSection[] {
  if (!report) {
    return [];
  }

  return [
    {
      id: "strengths",
      title: "经历亮点与保留项",
      sectionLabel: "Strengths",
      items: report.strengths,
      emptyText: "当前结果里没有额外亮点条目。",
      icon: Award,
      tone: "success",
    },
    {
      id: "risks",
      title: "匹配度风险与缺口",
      sectionLabel: "Risks",
      items: report.risks,
      emptyText: "当前结果里没有额外风险条目。",
      icon: AlertTriangle,
      tone: "warning",
    },
    {
      id: "suggestions",
      title: "下一步可执行建议",
      sectionLabel: "Suggestions",
      items: report.suggestions,
      emptyText: "当前结果里没有额外建议条目。",
      icon: WandSparkles,
      tone: "info",
    },
  ];
}

function buildReportCopyText(
  contextTitle: string,
  contextValue: string,
  report: Pick<ResumeAnalyzeResponse, "summary" | "strengths" | "risks" | "suggestions">,
) {
  const buildList = (title: string, items: string[]) => {
    if (!items.length) {
      return `${title}\n暂无`;
    }

    return `${title}\n${items.map((item, index) => `${index + 1}. ${item}`).join("\n")}`;
  };

  return [
    `${contextTitle}：${contextValue || "未填写"}`,
    "",
    `总结：${report.summary}`,
    "",
    buildList("亮点", report.strengths),
    "",
    buildList("风险", report.risks),
    "",
    buildList("建议", report.suggestions),
  ].join("\n");
}

function calculateHeuristicScore(report: ResumeAnalyzeResponse | null) {
  if (!report) {
    return "B";
  }

  if (report.scoreLabel?.trim()) {
    return report.scoreLabel.trim().toUpperCase();
  }

  const score =
    76
    + report.strengths.length * 6
    - report.risks.length * 5
    - Math.max(report.suggestions.length - 1, 0) * 2;

  if (score >= 92) {
    return "S";
  }

  if (score >= 88) {
    return "A+";
  }

  if (score >= 84) {
    return "A";
  }

  if (score >= 80) {
    return "A-";
  }

  if (score >= 76) {
    return "B+";
  }

  if (score >= 72) {
    return "B";
  }

  if (score >= 68) {
    return "B-";
  }

  if (score >= 62) {
    return "C+";
  }

  if (score >= 56) {
    return "C";
  }

  return "D";
}

function buildResumeExportDetail(reviewData: ReviewWorkspaceData): ResumeHistoryDetail | null {
  if (!reviewData.recordId) {
    return null;
  }

  return {
    recordId: reviewData.recordId,
    summary: reviewData.report.summary,
    strengths: reviewData.report.strengths,
    risks: reviewData.report.risks,
    suggestions: reviewData.report.suggestions,
    scoreLabel: reviewData.report.scoreLabel ?? "",
    structureItems: reviewData.report.structureItems ?? [],
    rewriteItems: reviewData.report.rewriteItems ?? [],
    targetRole: reviewData.targetRole,
    targetContext: reviewData.targetContext,
    inputMode: reviewData.inputMode,
    jobDescription: reviewData.jobDescription,
    resumeText: reviewData.resumeText,
    pdfFileName: reviewData.pdfFileName,
    pointsConsumed: 0,
    createdAt: reviewData.createdAt ?? "",
    aiMeta: reviewData.report.aiMeta,
    moderation: reviewData.report.moderation,
  };
}

function getScoreStyle(score: string): ScoreStyle {
  const normalizedScore = score.toUpperCase();

  if (normalizedScore.startsWith("A") || normalizedScore === "S") {
    return {
      text: "text-emerald-500",
      cardBg: "bg-gradient-to-br from-emerald-400 to-teal-500",
      cardText: "text-white",
      cardSubtext: "text-emerald-50",
      shadow: "shadow-[0_16px_30px_rgba(16,185,129,0.35)]",
      border: "border-emerald-300",
    };
  }

  if (normalizedScore.startsWith("B")) {
    return {
      text: "text-indigo-600",
      cardBg: "bg-gradient-to-br from-indigo-500 to-blue-600",
      cardText: "text-white",
      cardSubtext: "text-indigo-100",
      shadow: "shadow-[0_16px_30px_rgba(79,70,229,0.35)]",
      border: "border-indigo-400",
    };
  }

  if (normalizedScore.startsWith("C")) {
    return {
      text: "text-amber-500",
      cardBg: "bg-gradient-to-br from-amber-400 to-orange-500",
      cardText: "text-white",
      cardSubtext: "text-amber-50",
      shadow: "shadow-[0_16px_30px_rgba(245,158,11,0.35)]",
      border: "border-amber-300",
    };
  }

  return {
    text: "text-rose-500",
    cardBg: "bg-gradient-to-br from-rose-400 to-red-500",
    cardText: "text-white",
    cardSubtext: "text-rose-50",
    shadow: "shadow-[0_16px_30px_rgba(225,29,72,0.35)]",
    border: "border-rose-300",
  };
}

function pickMeaningfulLines(text: string) {
  return text
    .split(/\n+/)
    .map((item) => item.trim())
    .filter((item) => item.length >= 10)
    .slice(0, 8);
}

function deriveRewriteTitle(problem: string, index: number) {
  if (problem.includes("项目")) {
    return "项目经历描述优化";
  }

  if (problem.includes("实习")) {
    return "实习经历价值补强";
  }

  if (problem.includes("技能")) {
    return "技能表达层级重写";
  }

  if (problem.includes("自我评价") || problem.includes("总结")) {
    return "自我评价与定位收口";
  }

  return index === 0 ? "重点经历改写优化" : "表达角度补强";
}

function buildRewriteAfterText(
  beforeText: string,
  problem: string,
  targetRole: string,
  variantIndex: number,
) {
  const normalizedBefore = beforeText.replace(/[。；;]+$/, "");
  const conciseProblem = problem.split(/[，。；;]/)[0]?.trim() || "补充技术动作与结果";
  const roleLabel = targetRole || "目标岗位";

  const templates = [
    `面向 ${roleLabel}，建议把这段经历改成“场景 + 动作 + 结果”的一句话：围绕核心模块承担具体职责，点明采用的技术方案，并把“${conciseProblem}”落到可量化成果上。`,
    `建议强化“你做了什么”和“最终产生了什么变化”两层信息，例如：围绕 ${roleLabel} 的关键需求，主导或负责相关模块迭代，突出技术决策、难点处理与最终指标提升。`,
    `可以把“${normalizedBefore || "当前经历描述"}”升级为结果导向表达：先交代业务场景，再写技术动作，最后补上性能、效率或稳定性等可感知结果，并紧扣“${conciseProblem}”。`,
  ];

  return templates[variantIndex % templates.length];
}

function buildRewriteSandboxItems(
  reviewData: ReviewWorkspaceData | null,
  variantSeeds: number[],
) {
  if (!reviewData) {
    return [];
  }

  if (reviewData.report.rewriteItems?.length) {
    return reviewData.report.rewriteItems.map((item, index) => ({
      id: item.id || `rewrite-${index + 1}`,
      title: item.title,
      problem: item.problem,
      beforeText: item.beforeText,
      afterText: item.afterText,
      isHeuristic: Boolean(item.isHeuristic),
    }));
  }

  const sourceLines = pickMeaningfulLines(reviewData.resumeText);
  const issues = [...reviewData.report.suggestions, ...reviewData.report.risks]
    .filter((item) => item.trim())
    .slice(0, 4);

  const fallbackBeforeTexts = [
    "负责公司项目的日常开发与维护，完成多个模块的页面与接口联调。",
    "具备较强的沟通协作能力，能快速适应团队节奏，认真负责。",
    "参与核心业务模块迭代，能够配合需求完成页面、接口与联调交付。",
    "熟悉常见开发流程，能够支持版本发布与线上问题排查。",
  ];

  const fallbackIssues = [
    "建议补充业务价值、工程化实践与量化结果，让项目经历更有说服力。",
    "建议减少空泛形容词，改成更具体的岗位匹配证据与能力描述。",
    "建议突出关键技术动作，让经历不只停留在职责罗列。",
    "建议补充结果变化与个人贡献，让亮点更容易被识别。",
  ];

  return Array.from({ length: 4 }).map((_, index) => {
    const problem = issues[index] ?? fallbackIssues[index];
    const beforeText =
      sourceLines[index]
      ?? sourceLines[index % Math.max(sourceLines.length, 1)]
      ?? fallbackBeforeTexts[index]
      ?? fallbackBeforeTexts[index % fallbackBeforeTexts.length];

    return {
      id: `rewrite-${index + 1}`,
      title: deriveRewriteTitle(problem, index),
      problem,
      beforeText,
      afterText: buildRewriteAfterText(
        beforeText,
        problem,
        reviewData.targetRole,
        variantSeeds[index] ?? 0,
      ),
      isHeuristic: true,
    } satisfies RewriteSandboxItem;
  });
}

function countSignals(text: string, keywords: string[]) {
  return keywords.filter((keyword) => text.includes(keyword)).length;
}

function clampScore(score: number) {
  return Math.max(1, Math.min(5, score));
}

function buildStructureMapItems(
  reviewData: ReviewWorkspaceData | null,
  profile: StudentWorkspaceProfile | null,
) {
  if (!reviewData) {
    return [];
  }

  if (reviewData.report.structureItems?.length) {
    return reviewData.report.structureItems.map((item) => {
      const score = Math.max(1, Math.min(5, Number(item.score) || 3));
      return {
        label: item.label,
        score,
        tip: shortenStructureTip(item.label, item.tip, score),
      };
    });
  }

  const heuristicText = [
    reviewData.resumeText,
    reviewData.jobDescription,
    reviewData.report.summary,
    reviewData.report.strengths.join(" "),
    reviewData.report.risks.join(" "),
    reviewData.report.suggestions.join(" "),
    profile?.major ?? "",
    profile?.grade ?? "",
  ]
    .join(" ")
    .toLowerCase();

  const basicScore = clampScore(
    2
    + (reviewData.targetRole.trim() ? 1 : 0)
    + (countSignals(heuristicText, ["邮箱", "电话", "联系", "姓名"]) > 0 ? 1 : 0),
  );

  const educationScore = clampScore(
    1
    + (profile?.major ? 1 : 0)
    + (profile?.grade ? 1 : 0)
    + (countSignals(heuristicText, ["大学", "学院", "专业", "学历", "gpa"]) > 0 ? 2 : 0),
  );

  const skillScore = clampScore(
    2
    + (profile?.skillTags && profile.skillTags.length > 0 ? 1 : 0)
    + (countSignals(heuristicText, ["react", "vue", "typescript", "javascript", "技能", "工程化", "性能"]) > 0 ? 2 : 0),
  );

  const projectScore = clampScore(
    1
    + (countSignals(heuristicText, ["项目", "负责", "优化", "性能", "模块"]) > 0 ? 2 : 0)
    + (reviewData.report.strengths.length > 0 ? 1 : 0)
    - (reviewData.report.risks.some((item) => item.includes("项目")) ? 1 : 0),
  );

  const internshipScore = clampScore(
    1
    + (countSignals(heuristicText, ["实习", "业务", "团队", "协作"]) > 0 ? 2 : 0)
    + (reviewData.report.strengths.some((item) => item.includes("实习")) ? 1 : 0)
    - (reviewData.report.risks.some((item) => item.includes("实习")) ? 1 : 0),
  );

  const matchScore = clampScore(
    2
    + (reviewData.jobDescription.trim() ? 1 : 0)
    + (reviewData.report.strengths.length > reviewData.report.risks.length ? 1 : 0)
    + (reviewData.targetRole.trim() ? 1 : 0)
    - (reviewData.report.risks.length >= 3 ? 1 : 0),
  );

  return [
    {
      label: "基本信息",
      score: basicScore,
      tip: shortenStructureTip("基本信息", basicScore >= 4 ? "表达完整，页面可读性较好" : "建议补齐联系方式与求职目标语义", basicScore),
    },
    {
      label: "教育背景",
      score: educationScore,
      tip: shortenStructureTip("教育背景", educationScore >= 4 ? "教育信息已经具备说服力" : "建议更明确学校、专业与阶段信息", educationScore),
    },
    {
      label: "技能描述",
      score: skillScore,
      tip: shortenStructureTip("技能描述", skillScore >= 4 ? "技能点已基本成体系" : "建议区分熟练度并补齐场景", skillScore),
    },
    {
      label: "项目经历",
      score: projectScore,
      tip: shortenStructureTip("项目经历", projectScore >= 4 ? "项目主线已较清晰" : "建议补充技术动作与量化结果", projectScore),
    },
    {
      label: "实习经历",
      score: internshipScore,
      tip: shortenStructureTip("实习经历", internshipScore >= 4 ? "业务价值表达较完整" : "建议补充业务背景与个人贡献", internshipScore),
    },
    {
      label: "岗位匹配度",
      score: matchScore,
      tip: shortenStructureTip("岗位匹配度", matchScore >= 4 ? "与目标岗位较贴合" : "建议继续针对岗位要求做关键词补强", matchScore),
    },
  ] satisfies StructureMapItem[];
}

function readHistoryMetaMap(userId: number | null) {
  if (!userId) {
    return {} as Record<string, ResumeHistoryLocalMeta>;
  }

  return (
    readStorageJson<Record<string, ResumeHistoryLocalMeta>>(
      "local",
      getResumeHistoryMetaStorageKey(userId),
    ) ?? {}
  );
}

function writeHistoryMetaMap(
  userId: number | null,
  nextMap: Record<string, ResumeHistoryLocalMeta>,
) {
  if (!userId) {
    return;
  }

  writeStorageJson("local", getResumeHistoryMetaStorageKey(userId), nextMap);
}

function getHistoryMeta(userId: number | null, recordId: number | null) {
  if (!userId || !recordId) {
    return null;
  }

  const metaMap = readHistoryMetaMap(userId);
  return metaMap[String(recordId)] ?? null;
}

function getHistoryRecordTitle(record: ResumeHistoryItem) {
  const firstClause = record.summary.replace(/\s+/g, " ").split(/[。；;]/)[0].trim();
  return firstClause ? firstClause.slice(0, 20) : `诊断记录 #${record.id}`;
}

function buildReviewWorkspaceData(
  latestSnapshot: ResumeReviewSnapshot | null,
  fallbackDetail: ResumeHistoryDetailResponse | null,
  viewingHistory: boolean,
  viewingHistoryId: number | null,
  historyMeta: ResumeHistoryLocalMeta | null,
) {
  if (viewingHistory && fallbackDetail) {
    // 历史详情优先作为复盘真相，local meta 只补齐目标岗位等本地展示字段。
    return {
      source: "history",
      recordId: fallbackDetail.recordId,
      report: fallbackDetail,
      targetContext: fallbackDetail.targetContext || historyMeta?.targetContext || "历史快照",
      targetRole: fallbackDetail.targetRole || historyMeta?.targetRole || `历史记录 #${fallbackDetail.recordId}`,
      inputMode: fallbackDetail.inputMode || historyMeta?.inputMode || "pdf",
      jobDescription: fallbackDetail.jobDescription || historyMeta?.jobDescription || "",
      resumeText: fallbackDetail.resumeText || "",
      pdfFileName: fallbackDetail.pdfFileName ?? null,
      createdAt: fallbackDetail.createdAt,
      approximateBinding: false,
    } satisfies ReviewWorkspaceData;
  }

  if (latestSnapshot) {
    // 最新任务结果保存在 sessionStorage，方便 compose -> review 跳转后立即展示。
    return {
      source: "latest",
      recordId: latestSnapshot.recordId,
      report: latestSnapshot.result,
      targetContext: latestSnapshot.targetContext,
      targetRole: latestSnapshot.targetRole,
      inputMode: latestSnapshot.inputMode,
      jobDescription: latestSnapshot.jobDescription,
      resumeText: latestSnapshot.resumeText,
      pdfFileName: latestSnapshot.pdfFileName,
      createdAt: latestSnapshot.completedAt,
      approximateBinding: latestSnapshot.approximateBinding,
    } satisfies ReviewWorkspaceData;
  }

  if (!viewingHistory && fallbackDetail) {
    return {
      source: "recent-history",
      recordId: fallbackDetail.recordId,
      report: fallbackDetail,
      targetContext: fallbackDetail.targetContext || historyMeta?.targetContext || "最近一次保存记录",
      targetRole: fallbackDetail.targetRole || historyMeta?.targetRole || `历史记录 #${fallbackDetail.recordId}`,
      inputMode: fallbackDetail.inputMode || historyMeta?.inputMode || "pdf",
      jobDescription: fallbackDetail.jobDescription || historyMeta?.jobDescription || "",
      resumeText: fallbackDetail.resumeText || "",
      pdfFileName: fallbackDetail.pdfFileName ?? null,
      createdAt: fallbackDetail.createdAt,
      approximateBinding: false,
    } satisfies ReviewWorkspaceData;
  }

  if (viewingHistory && viewingHistoryId) {
    return null;
  }

  return null;
}

function ResumeOverlayFallback() {
  return (
    <div className="fixed inset-0 z-[130] flex items-center justify-center bg-slate-900/18 backdrop-blur-sm">
      <div className="rounded-[1.6rem] border border-white/70 bg-white/92 px-6 py-5 shadow-[0_20px_50px_rgba(15,23,42,0.16)]">
        <div className="flex items-center gap-3">
          <div className="h-8 w-8 animate-spin rounded-full border-2 border-slate-200 border-t-indigo-600" />
          <div className="text-sm font-semibold text-slate-600">正在加载操作面板...</div>
        </div>
      </div>
    </div>
  );
}

function Card({ className = "", children }: { className?: string; children?: ReactNode }) {
  return (
    <div
      className={joinClasses(
        "rounded-[2rem] border border-white/70 bg-white/84 shadow-[0_22px_60px_rgba(148,163,184,0.18)] backdrop-blur-xl",
        className,
      )}
    >
      {children}
    </div>
  );
}

function Button({
  className = "",
  variant = "default",
  size = "default",
  children,
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement> & {
  className?: string;
  variant?: "default" | "secondary" | "outline" | "ghost" | "danger";
  size?: "default" | "sm" | "lg" | "icon";
}) {
  const baseStyle =
    "inline-flex items-center justify-center rounded-full text-sm font-semibold transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 disabled:pointer-events-none disabled:opacity-50 hover:-translate-y-0.5 active:scale-[0.98]";

  const variants = {
    default:
      "bg-gradient-to-r from-indigo-500 to-indigo-600 text-white hover:from-indigo-600 hover:to-indigo-700 shadow-[0_12px_24px_rgba(79,70,229,0.25)]",
    secondary: "bg-slate-100 text-slate-900 hover:bg-slate-200 shadow-sm",
    outline:
      "border border-slate-200 bg-white text-slate-600 hover:border-indigo-200 hover:text-indigo-600 shadow-sm",
    ghost:
      "text-slate-600 hover:bg-slate-100 hover:text-slate-900 shadow-none hover:shadow-none hover:translate-y-0 active:scale-100",
    danger: "bg-rose-50 text-rose-600 hover:bg-rose-100",
  };

  const sizes = {
    default: "h-11 px-5 py-2.5",
    sm: "h-9 px-4 text-xs",
    lg: "h-12 px-8 text-base",
    icon: "h-11 w-11",
  };

  return (
    <button
      {...props}
      className={joinClasses(baseStyle, variants[variant], sizes[size], className)}
    >
      {children}
    </button>
  );
}

function Badge({
  children,
  variant = "default",
  className = "",
}: {
  children: ReactNode;
  variant?: "default" | "success" | "warning" | "outline";
  className?: string;
}) {
  const variants = {
    default: "bg-indigo-50 text-indigo-700 border-indigo-200",
    success: "bg-emerald-50 text-emerald-700 border-emerald-200",
    warning: "bg-amber-50 text-amber-700 border-amber-200",
    outline: "border-slate-200 text-slate-600 bg-white",
  };

  return (
    <span
      className={joinClasses(
        "inline-flex items-center rounded-full border px-3 py-1 text-xs font-semibold shadow-sm transition-colors",
        variants[variant],
        className,
      )}
    >
      {children}
    </span>
  );
}

function TextInput(
  props: React.InputHTMLAttributes<HTMLInputElement> & { className?: string },
) {
  const { className = "", ...rest } = props;

  return (
    <input
      {...rest}
      className={joinClasses(
        "flex h-11 w-full rounded-[1.25rem] border border-slate-200 bg-white/90 px-4 py-2.5 text-sm ring-offset-white placeholder:text-slate-400 shadow-sm transition-colors hover:border-slate-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500",
        className,
      )}
    />
  );
}

function Textarea(
  props: React.TextareaHTMLAttributes<HTMLTextAreaElement> & { className?: string },
) {
  const { className = "", ...rest } = props;

  return (
    <textarea
      {...rest}
      className={joinClasses(
        "flex min-h-[120px] w-full resize-none rounded-[1.25rem] border border-slate-200 bg-white/90 px-4 py-3 text-sm ring-offset-white placeholder:text-slate-400 shadow-sm transition-colors hover:border-slate-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500",
        className,
      )}
    />
  );
}

function CustomSelect({
  value,
  onChange,
  options,
}: {
  value: string;
  onChange: (nextValue: string) => void;
  options: string[];
}) {
  const [isOpen, setIsOpen] = useState(false);

  return (
    <div className="relative w-full">
      {isOpen ? (
        <button
          type="button"
          className="fixed inset-0 z-40 cursor-default"
          onClick={() => setIsOpen(false)}
        />
      ) : null}

      <button
        type="button"
        onClick={() => setIsOpen((current) => !current)}
        className={joinClasses(
          "relative z-40 flex h-11 w-full items-center justify-between rounded-[1.25rem] border border-slate-200 bg-white/90 px-5 py-2.5 text-sm text-slate-800 shadow-sm transition-all hover:border-slate-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500",
          isOpen && "border-indigo-300 ring-2 ring-indigo-500/20",
        )}
      >
        <span className="truncate font-bold text-slate-700">{value}</span>
        <ChevronDown
          size={16}
          className={joinClasses(
            "text-slate-400 transition-transform duration-300",
            isOpen && "rotate-180 text-indigo-500",
          )}
          strokeWidth={2.5}
        />
      </button>

      <AnimatePresence>
        {isOpen ? (
          <motion.div
            initial={{ opacity: 0, y: -8, scale: 0.96 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -8, scale: 0.96 }}
            transition={{ duration: 0.15, ease: "easeOut" }}
            className="absolute left-0 right-0 z-50 mt-2 overflow-hidden rounded-[1.25rem] border border-slate-100 bg-white/95 py-2 shadow-[0_16px_40px_rgba(0,0,0,0.12)] backdrop-blur-xl"
          >
            <div className="max-h-[260px] overflow-y-auto" style={{ scrollbarWidth: "thin" }}>
              {options.map((option) => (
                <button
                  key={option}
                  type="button"
                  onClick={() => {
                    onChange(option);
                    setIsOpen(false);
                  }}
                  className={joinClasses(
                    "flex w-full items-center justify-between px-5 py-3 text-sm transition-colors",
                    value === option
                      ? "bg-indigo-50/80 font-black text-indigo-700"
                      : "font-semibold text-slate-600 hover:bg-slate-50",
                  )}
                >
                  <span className="truncate">{option}</span>
                  {value === option ? (
                    <Check size={16} className="shrink-0 text-indigo-600" strokeWidth={3} />
                  ) : null}
                </button>
              ))}
            </div>
          </motion.div>
        ) : null}
      </AnimatePresence>
    </div>
  );
}

function WorkspaceHeader({
  routeMode,
  studentName,
  roleLabel,
  userId,
  avatar,
  tier,
  reviewBackHref,
  reviewBackLabel,
}: WorkspaceHeaderProps) {
  const topbarSectionLabel = routeMode === "review" ? "Resume Review" : "Resume Optimizer";
  const topbarTitle = routeMode === "review" ? "简历复盘" : "简历优化";

  return (
    <StudentWorkspaceTopbar
      sectionLabel={topbarSectionLabel}
      title={topbarTitle}
      navItems={buildStudentWorkspacePrimaryNav("resume")}
      rightActions={routeMode === "review" ? (
        <Link
          to={reviewBackHref}
          className="hidden h-10 items-center justify-center rounded-full border border-slate-200 bg-white px-4 text-sm font-semibold text-slate-600 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600 sm:inline-flex"
        >
          <ArrowLeft size={16} className="mr-2" />
          {reviewBackLabel}
        </Link>
      ) : (
        <Link
          to="/student/dashboard"
          className="hidden h-10 items-center justify-center rounded-full border border-slate-200 bg-white px-4 text-sm font-semibold text-slate-600 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600 sm:inline-flex"
        >
          返回工作台
        </Link>
      )}
      position="sticky"
      userId={userId}
      displayName={studentName}
      avatar={avatar}
      tier={tier}
      userSubtitle={roleLabel}
      className="z-50 bg-white/80"
    />
  );
}

function ComposePage({
  draft,
  resumeFile,
  profile,
  profileDraft,
  quota,
  quotaLoading,
  quotaError,
  status,
  errorMessage,
  isAnalyzeDisabled,
  draftSavedAt,
  fileInputRef,
  onOpenHistory,
  onOpenReviewCenter,
  onRefreshQuota,
  onAnalyze,
  onUseProfileDraft,
  onClearDraft,
  onTargetContextChange,
  onTargetRoleChange,
  onJobDescriptionChange,
  onInputModeChange,
  onResumeTextChange,
  onFileInputChange,
  onFileDrop,
  onRemoveFile,
}: ComposePageProps) {
  const resumeQuota = quota?.quotas.find((item) => item.taskType === "RESUME") ?? null;
  const pdfReady = Boolean(resumeFile);
  const pdfNeedsReupload = Boolean(draft.pdfFileName && !resumeFile);
  const hasPdfReference = pdfReady || pdfNeedsReupload;
  const PdfStatusIcon = pdfReady ? CheckCircle2 : pdfNeedsReupload ? History : UploadCloud;

  const heroStatusPills = [
    {
      label: "模式",
      value: draft.inputMode === "pdf" ? "PDF 解析" : "文本诊断",
      detail:
        draft.inputMode === "pdf"
          ? "适合原版简历直传"
          : "适合快速连续改稿",
      icon: draft.inputMode === "pdf" ? UploadCloud : AlignLeft,
      frameClassName: "border-indigo-200/80 bg-gradient-to-r from-indigo-50 via-white to-violet-50",
      iconClassName: "border-indigo-200 bg-white text-indigo-600 shadow-indigo-100",
      valueClassName: "text-indigo-700",
    },
  ] as const;

  return (
    <motion.div
      variants={staggerContainer}
      initial="hidden"
      animate="show"
      exit={{ opacity: 0, y: -15, transition: { duration: 0.2 } }}
      className="space-y-8"
    >
      <motion.div variants={fadeUpItem} className="grid grid-cols-1 items-start gap-6 lg:grid-cols-12">
        <div className="flex flex-col justify-center space-y-4 lg:col-span-8">
          <div>
            <h1 className="text-3xl font-semibold tracking-tight text-slate-950 lg:text-4xl">
              先准备材料，
              <br className="sm:hidden" />
              再拿到更有针对性的简历建议
            </h1>
          </div>
          <p className="max-w-2xl text-base leading-relaxed text-slate-500">
            提供你的目标语境、岗位要求以及当前简历材料。AI 会先生成本次诊断摘要，再由你决定是否进入详细结果页继续查看。
          </p>

          <div className="flex flex-col items-start gap-4 pt-2">
            <div className="flex w-full max-w-4xl flex-wrap gap-3">
              {heroStatusPills.map((item) => {
                const StatusIcon = item.icon;

                return (
                  <div
                    key={item.label}
                    className={joinClasses(
                      "inline-flex min-h-[4.5rem] min-w-[14rem] max-w-[19rem] items-center gap-3 rounded-[1.45rem] border px-4 py-3 shadow-[0_12px_28px_rgba(148,163,184,0.12)] backdrop-blur-xl",
                      item.frameClassName,
                    )}
                  >
                    <div
                      className={joinClasses(
                        "flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl border shadow-sm",
                        item.iconClassName,
                      )}
                    >
                      <StatusIcon size={17} strokeWidth={2.3} />
                    </div>
                    <div className="min-w-0">
                      <div className="text-[13px] font-bold tracking-[0.08em] text-slate-700">
                        {item.label}
                      </div>
                      <div className={joinClasses("mt-1 truncate text-base font-black tracking-tight", item.valueClassName)}>
                        {item.value}
                      </div>
                      <div className="mt-0.5 truncate text-xs leading-5 text-slate-500">{item.detail}</div>
                    </div>
                  </div>
                );
              })}

              <button
                type="button"
                onClick={onOpenReviewCenter}
                className="group relative inline-flex min-h-[4.5rem] min-w-[14rem] max-w-[19rem] items-center justify-between gap-4 overflow-hidden rounded-[1.45rem] border border-sky-200/80 bg-gradient-to-r from-sky-50 via-white to-emerald-50 px-5 py-3 text-left transition-colors duration-300 hover:border-cyan-300"
              >
                <span className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_top_left,rgba(56,189,248,0.18),transparent_42%),linear-gradient(120deg,rgba(34,211,238,0.08),rgba(45,212,191,0.14))] opacity-0 transition-opacity duration-300 group-hover:opacity-100" />
                <span className="pointer-events-none absolute -left-16 top-0 h-full w-28 bg-white/45 blur-2xl opacity-0 transition-all duration-700 group-hover:left-28 group-hover:opacity-70" />
                <div className="flex min-w-0 items-center gap-3">
                  <div className="relative flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl border border-sky-200 bg-white text-sky-600 transition-colors duration-300 group-hover:border-cyan-200 group-hover:bg-cyan-50">
                    <History size={17} strokeWidth={2.4} />
                  </div>
                  <div className="relative flex min-w-0 flex-col justify-center">
                    <div className="text-[13px] font-bold tracking-[0.08em] text-sky-700">
                      复盘中心
                    </div>
                    <div className="mt-1 truncate text-base font-black tracking-tight text-slate-900">
                      进入复盘中心
                    </div>
                  </div>
                </div>
                <ArrowRight size={18} className="relative shrink-0 text-cyan-600 transition-colors duration-300 group-hover:text-sky-700" strokeWidth={2.6} />
              </button>
            </div>
          </div>
        </div>

        <div className="flex flex-col justify-center lg:col-span-4">
          <Card className="bg-gradient-to-br from-white to-indigo-50/40 p-5 xl:p-6">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="flex items-center gap-2 text-base font-bold text-slate-900">
                <RefreshCw size={16} className="text-indigo-500" strokeWidth={2.5} />
                分析流程
              </h2>
            </div>

            <div className="space-y-3">
              {[
                { step: 1, title: "准备材料与目标", active: true },
                { step: 2, title: "发起智能诊断分析", active: status === "analyzing" || status === "complete" },
                { step: 3, title: "查看详细结果页", active: status === "complete" },
              ].map((item) => (
                <div key={item.step} className="flex items-center text-sm">
                  <div
                    className={joinClasses(
                      "mr-3 flex h-5 w-5 items-center justify-center rounded-full text-[11px] font-black shadow-sm",
                      item.active
                        ? "bg-indigo-600 text-white"
                        : "border border-slate-200/60 bg-slate-100 text-slate-400",
                    )}
                  >
                    {item.step}
                  </div>
                  <span className={item.active ? "font-bold text-slate-800" : "font-medium text-slate-500"}>
                    {item.title}
                  </span>
                </div>
              ))}
            </div>

            <div className="mt-5 border-t border-slate-100/80 pt-4 text-xs text-slate-500">
              <div className="flex items-center justify-between">
                <span className="font-medium">
                  今日可用：
                  <strong className="ml-1 text-[13px] text-indigo-600">
                    {quotaLoading ? "读取中..." : formatQuotaLimit(resumeQuota?.remaining)}
                  </strong>
                </span>
                <span className="font-medium">
                  积分：
                  <strong className="ml-1 text-[13px] text-slate-700">
                    {formatCount(quota?.pointsBalance ?? 0)}
                  </strong>
                </span>
              </div>
              <div className="mt-2 flex items-center justify-between">
                <span>等级：{quota?.tier ?? "未获取"}</span>
                <button
                  type="button"
                  onClick={onRefreshQuota}
                  className="inline-flex items-center font-semibold text-indigo-600 transition-colors hover:text-indigo-700"
                >
                  <RefreshCw size={12} className={joinClasses("mr-1", quotaLoading && "animate-spin")} />
                  刷新
                </button>
              </div>
              {quotaError ? (
                <div className="mt-3 rounded-xl border border-amber-100 bg-amber-50 px-3 py-2 leading-6 text-amber-700">
                  {quotaError}
                </div>
              ) : null}
            </div>
          </Card>
        </div>
      </motion.div>

      <motion.div variants={fadeUpItem}>
        <Card className="overflow-hidden p-2 sm:p-3">
          <div className="grid grid-cols-1 gap-2 lg:grid-cols-12">
            <div className="rounded-[1.8rem] border border-slate-100 bg-white/60 p-6 shadow-sm sm:p-8 lg:col-span-5">
              <div className="mb-6">
                <h2 className="text-2xl font-black tracking-tight text-slate-950">目标语境设定</h2>
              </div>

              <div className="space-y-8">
                <div className="space-y-3">
                  <label className="text-sm font-semibold text-slate-700">当前求职阶段与语境</label>
                  <CustomSelect
                    value={draft.targetContext}
                    onChange={onTargetContextChange}
                    options={TARGET_CONTEXT_OPTIONS}
                  />
                </div>

                <div className="space-y-3">
                  <label className="text-sm font-semibold text-slate-700">目标岗位</label>
                  <TextInput
                    placeholder="例如：前端开发工程师、产品经理"
                    value={draft.targetRole}
                    onChange={(event) => onTargetRoleChange(event.target.value)}
                  />
                  {profile?.targetPosition?.trim() ? (
                    <div className="text-xs leading-6 text-slate-500">
                      当前资料中心目标岗位提示：
                      <span className="ml-1 font-semibold text-indigo-600">
                        {profile.targetPosition.trim()}
                      </span>
                    </div>
                  ) : null}
                </div>

                <div className="space-y-3 pt-2">
                  <label className="flex items-center justify-between text-sm font-semibold text-slate-700">
                    <span>
                      岗位要求对照
                      <span className="ml-1 rounded-md border border-indigo-100 bg-indigo-50 px-2 py-0.5 text-xs font-normal text-indigo-500">
                        推荐
                      </span>
                    </span>
                  </label>
                  <Textarea
                    placeholder="粘贴目标岗位的职责与要求，AI 会据此对照你的简历匹配度。"
                    className="h-36 bg-slate-50/50"
                    value={draft.jobDescription}
                    onChange={(event) => onJobDescriptionChange(event.target.value)}
                  />
                  <div className="rounded-[1.4rem] border border-indigo-100 bg-gradient-to-r from-indigo-50 to-sky-50 px-4 py-3.5 text-sm leading-7 text-slate-600">
                    <span className="font-semibold text-indigo-700">对照说明：</span>
                    补充一段岗位要求后，系统会优先围绕该岗位的职责重点、能力要求和表达侧重点来分析你的简历；职位职责、任职要求和加分项都可以直接粘贴。
                  </div>
                </div>
              </div>
            </div>

            <div className="relative flex flex-col rounded-[1.8rem] border border-slate-100 bg-white/60 p-6 shadow-sm sm:p-8 lg:col-span-7">
              <div className="mb-6">
                <h2 className="text-2xl font-black tracking-tight text-slate-950">简历材料输入</h2>
              </div>

              <div className="mb-6 flex w-fit rounded-full border border-slate-200/60 bg-slate-100/80 p-1 shadow-inner">
                <button
                  type="button"
                  onClick={() => onInputModeChange("pdf")}
                  className={joinClasses(
                    "rounded-full px-5 py-2 text-sm font-semibold transition-all",
                    draft.inputMode === "pdf"
                      ? "bg-white text-slate-900 shadow-sm"
                      : "text-slate-500 hover:text-slate-700",
                  )}
                >
                  PDF 文件上传
                </button>
                <button
                  type="button"
                  onClick={() => onInputModeChange("text")}
                  className={joinClasses(
                    "rounded-full px-5 py-2 text-sm font-semibold transition-all",
                    draft.inputMode === "text"
                      ? "bg-white text-slate-900 shadow-sm"
                      : "text-slate-500 hover:text-slate-700",
                  )}
                >
                  文本粘贴
                </button>
              </div>

              <div className="flex min-h-[280px] flex-1 flex-col justify-center">
                <AnimatePresence mode="wait">
                  {draft.inputMode === "pdf" ? (
                    <motion.div
                      key="pdf"
                      initial={{ opacity: 0 }}
                      animate={{ opacity: 1 }}
                      exit={{ opacity: 0 }}
                      className="flex h-full flex-col"
                    >
                      <label
                        onDragOver={(event) => event.preventDefault()}
                        onDrop={onFileDrop}
                        className={joinClasses(
                          "group relative flex h-full min-h-[300px] cursor-pointer flex-col items-center justify-center rounded-[1.5rem] border-2 border-dashed p-8 text-center transition-all duration-300",
                          pdfReady
                            ? "border-emerald-300 bg-gradient-to-br from-emerald-50 via-white to-indigo-50 shadow-[0_24px_50px_rgba(16,185,129,0.14)] hover:border-emerald-400 hover:bg-emerald-50/90"
                            : pdfNeedsReupload
                              ? "border-amber-300 bg-gradient-to-br from-amber-50 via-white to-orange-50 shadow-[0_24px_50px_rgba(245,158,11,0.12)] hover:border-amber-400 hover:bg-amber-50/90"
                              : "border-indigo-200/80 bg-indigo-50/40 hover:border-indigo-300 hover:bg-indigo-50/80",
                        )}
                      >
                        <input
                          ref={fileInputRef}
                          type="file"
                          accept=".pdf,application/pdf"
                          className="hidden"
                          onChange={onFileInputChange}
                        />
                        {hasPdfReference ? (
                          <>
                            <div className="absolute right-4 top-4 z-10">
                              <Button
                                size="sm"
                                variant="outline"
                                onClick={(event) => {
                                  event.preventDefault();
                                  event.stopPropagation();
                                  onRemoveFile();
                                }}
                              >
                                移除
                              </Button>
                            </div>
                            <div className="w-full max-w-2xl rounded-[1.7rem] border border-white/80 bg-white/88 p-5 text-left shadow-[0_18px_40px_rgba(148,163,184,0.14)] backdrop-blur-sm">
                              <div className="flex items-start gap-4">
                                <div
                                  className={joinClasses(
                                    "flex h-14 w-14 shrink-0 items-center justify-center rounded-[1.25rem] border bg-white shadow-sm",
                                    pdfReady
                                      ? "border-emerald-200 text-emerald-600"
                                      : "border-amber-200 text-amber-600",
                                  )}
                                >
                                  <PdfStatusIcon size={24} />
                                </div>
                                <div className="min-w-0 flex-1">
                                  <div className="flex flex-wrap items-center gap-2">
                                    <div className="text-[11px] text-slate-400">Resume PDF</div>
                                    <Badge
                                      variant={pdfReady ? "success" : "warning"}
                                      className="px-2.5 py-1 text-[10px] font-bold"
                                    >
                                      {pdfReady ? "已选择" : "待重传"}
                                    </Badge>
                                  </div>
                                  <div className="mt-2 truncate text-base font-black tracking-tight text-slate-900">
                                    {resumeFile?.name || draft.pdfFileName || "暂未选择文件"}
                                  </div>
                                  <div className="mt-2 text-sm leading-6 text-slate-600">
                                    {resumeFile
                                      ? `${formatFileSize(resumeFile)} · 文件已就绪，可以直接开始分析`
                                      : "已恢复上次文件名，真正开始分析前仍需要重新上传 PDF"}
                                  </div>
                                </div>
                              </div>
                              <div className="mt-4 rounded-[1.2rem] border border-slate-100 bg-slate-50/80 px-4 py-3 text-sm leading-6 text-slate-600">
                                {pdfReady
                                  ? "继续点击当前上传框可重新选择文件；开始分析时会按这份 PDF 直接解析。"
                                  : "我们只恢复了文件名和上下文，不会保留本地二进制文件；重新上传后即可继续。"}
                              </div>
                            </div>
                          </>
                        ) : (
                          <>
                            <motion.div
                              animate={{ y: [0, -6, 0] }}
                              transition={{ repeat: Number.POSITIVE_INFINITY, duration: 3, ease: "easeInOut" }}
                              className="mb-5 flex h-16 w-16 items-center justify-center rounded-2xl border border-indigo-100 bg-white text-indigo-500 shadow-sm"
                            >
                              <PdfStatusIcon size={28} />
                            </motion.div>
                            <h3 className="mb-2 text-lg font-bold text-slate-800">点击或拖拽上传 PDF</h3>
                            <p className="max-w-xs text-sm leading-relaxed text-slate-500">
                              支持 .pdf 格式，最大不超过 5MB。系统将自动解析布局与文本内容。
                            </p>
                          </>
                        )}
                      </label>
                    </motion.div>
                  ) : (
                    <motion.div
                      key="text"
                      initial={{ opacity: 0 }}
                      animate={{ opacity: 1 }}
                      exit={{ opacity: 0 }}
                      className="flex h-full flex-col"
                    >
                      <div className="mb-3 flex items-center justify-between gap-3">
                        <div className="text-sm font-semibold text-slate-700">文本简历内容</div>
                        {profileDraft ? (
                          <button
                            type="button"
                            onClick={onUseProfileDraft}
                            className="inline-flex items-center text-xs font-semibold text-indigo-600 transition-colors hover:text-indigo-700"
                          >
                            <Sparkles size={14} className="mr-1.5" />
                            使用当前画像提示填充
                          </button>
                        ) : null}
                      </div>
                      <Textarea
                        className="min-h-[300px] flex-1 text-sm leading-relaxed"
                        placeholder="在此粘贴你的纯文本简历内容..."
                        value={draft.resumeText}
                        onChange={(event) => onResumeTextChange(event.target.value)}
                      />
                    </motion.div>
                  )}
                </AnimatePresence>
              </div>

              {status === "error" ? (
                <div className="mt-6 rounded-[1.4rem] border border-rose-200 bg-rose-50/80 px-4 py-4 text-sm leading-6 text-rose-700">
                  <div className="flex items-center font-bold">
                    <AlertTriangle size={16} className="mr-2" />
                    这次诊断没有成功完成
                  </div>
                  <div className="mt-2 text-rose-700/90">
                    {errorMessage || "当前请求没有拿到有效结果。你可以检查输入材料后再次尝试。"}
                  </div>
                </div>
              ) : null}

              <div className="mt-8 flex flex-col items-center justify-between gap-4 border-t border-slate-200/60 pt-6 sm:flex-row">
                <div className="flex items-center gap-2 text-sm font-medium text-slate-500">
                  <span className="flex h-5 w-5 items-center justify-center rounded-full bg-emerald-100 text-emerald-600">
                    <CheckCircle2 size={12} strokeWidth={3} />
                  </span>
                  {draftSavedAt ? `草稿已于 ${formatRelativeTime(draftSavedAt)} 保存` : "已开启草稿自动保存"}
                </div>
                <div className="flex w-full gap-3 sm:w-auto">
                  <Button variant="ghost" className="hidden sm:flex" onClick={onClearDraft}>
                    清空重填
                  </Button>
                  <Button
                    size="lg"
                    className="w-full min-w-[180px] sm:w-auto"
                    onClick={onAnalyze}
                    disabled={isAnalyzeDisabled || status === "analyzing"}
                  >
                    {status === "analyzing" ? (
                      <span className="flex items-center gap-2">
                        <motion.div
                          animate={{ rotate: 360 }}
                          transition={{ repeat: Number.POSITIVE_INFINITY, duration: 1, ease: "linear" }}
                        >
                          <RefreshCw size={18} />
                        </motion.div>
                        正在深度诊断...
                      </span>
                    ) : (
                      <span className="flex items-center gap-2 text-base">
                        开始智能诊断
                        <ChevronRight size={18} />
                      </span>
                    )}
                  </Button>
                </div>
              </div>
            </div>
          </div>
        </Card>
      </motion.div>
    </motion.div>
  );
}

function ReviewAssessmentCard({ section }: { section: ReportSection }) {
  const toneClasses =
    section.tone === "success"
      ? {
          border: "border-emerald-100",
          line: "from-emerald-400 to-teal-500",
          icon: "text-emerald-500",
          iconWrap: "bg-emerald-50 text-emerald-600 border-emerald-100",
          badge: "border-emerald-200/70 bg-gradient-to-br from-emerald-100 to-teal-100 text-emerald-700",
          shadow: "shadow-[0_16px_40px_rgba(16,185,129,0.08)]",
        }
      : section.tone === "warning"
        ? {
            border: "border-amber-100",
            line: "from-amber-400 to-orange-500",
            icon: "text-amber-500",
            iconWrap: "bg-amber-50 text-amber-600 border-amber-100",
            badge: "border-amber-200/70 bg-gradient-to-br from-amber-100 to-orange-100 text-amber-700",
            shadow: "shadow-[0_16px_40px_rgba(245,158,11,0.08)]",
          }
        : {
            border: "border-indigo-100",
            line: "from-indigo-500 to-violet-500",
            icon: "text-indigo-500",
            iconWrap: "bg-indigo-50 text-indigo-600 border-indigo-100",
            badge: "border-indigo-200/70 bg-gradient-to-br from-indigo-100 to-violet-100 text-indigo-700",
            shadow: "shadow-[0_16px_40px_rgba(79,70,229,0.08)]",
          };

  const SectionIcon = section.icon;

  return (
    <motion.div
      initial={{ opacity: 0, x: -10 }}
      whileInView={{ opacity: 1, x: 0 }}
      viewport={{ once: true, margin: "-40px" }}
      transition={{ duration: 0.4 }}
      className={joinClasses(
        "group relative overflow-hidden rounded-[1.8rem] border bg-white p-7 sm:p-8",
        toneClasses.border,
        toneClasses.shadow,
      )}
    >
      <div className={joinClasses("absolute bottom-0 left-0 top-0 w-2 bg-gradient-to-b", toneClasses.line)} />
      <div className="mb-5 ml-2 flex items-center gap-3">
        <div
          className={joinClasses(
            "flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl border shadow-sm",
            toneClasses.iconWrap,
          )}
        >
          <SectionIcon size={18} className={toneClasses.icon} />
        </div>
        <h3 className="text-xl font-black text-slate-900">{section.title}</h3>
      </div>
      {section.items.length ? (
        <ul className="ml-2 space-y-4">
          {section.items.map((item, index) => (
            <li key={`${section.id}-${index}`} className="flex items-start">
              <div className={joinClasses("mt-0.5 mr-3 flex h-6 w-6 shrink-0 items-center justify-center rounded-full border text-xs font-bold shadow-sm", toneClasses.badge)}>
                {index + 1}
              </div>
              <span className="text-[15px] font-medium leading-relaxed text-slate-600">{item}</span>
            </li>
          ))}
        </ul>
      ) : (
        <div className="ml-2 rounded-[1.25rem] border border-dashed border-slate-200 bg-slate-50/80 px-4 py-4 text-sm leading-7 text-slate-500">
          {section.emptyText}
        </div>
      )}
    </motion.div>
  );
}

function ReviewPage({
  reviewData,
  taskId,
  taskDetail,
  taskLoading,
  taskError,
  scoreLabel,
  scoreStyle,
  reviewSections,
  structureItems,
  rewriteItems,
  exportFeedback,
  exportingRecordId,
  copiedKey,
  historyCount,
  historyMode,
  backActionLabel,
  selectedHistoryLoading,
  selectedHistoryError,
  onBackToCompose,
  onDismissExportFeedback,
  onOpenHistory,
  onOpenReviewCenter,
  onExitHistory,
  onRefreshTask,
  onExport,
  onCopySummary,
  onCopyReport,
  onCopyRewrite,
  onCycleRewrite,
}: ReviewPageProps) {
  const [activeSection, setActiveSection] = useState("overview");
  const [expandedRewriteId, setExpandedRewriteId] = useState<string | null>(rewriteItems[0]?.id ?? null);
  const taskStatusLabel = getAsyncTaskStatusLabel(taskDetail?.status ?? null);
  const currentExportFeedback =
    exportFeedback && reviewData?.recordId === exportFeedback.recordId ? exportFeedback : null;

  const navItems = [
    { id: "overview", label: "诊断概览", icon: LayoutTemplate },
    { id: "structure", label: "完整度地图", icon: Target },
    { id: "details", label: "详细评估", icon: AlignLeft },
    { id: "sandbox", label: "部分修改建议", icon: RefreshCw },
  ];

  useEffect(() => {
    if (!rewriteItems.length) {
      setExpandedRewriteId(null);
      return;
    }

    setExpandedRewriteId((current) =>
      current && rewriteItems.some((item) => item.id === current) ? current : rewriteItems[0].id,
    );
  }, [rewriteItems]);

  useEffect(() => {
    if (!reviewData || typeof IntersectionObserver === "undefined") {
      return;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        const visibleEntry = entries
          .filter((entry) => entry.isIntersecting)
          .sort((left, right) => right.intersectionRatio - left.intersectionRatio)[0];

        if (visibleEntry?.target.id) {
          setActiveSection(visibleEntry.target.id);
        }
      },
      {
        rootMargin: "-20% 0px -55% 0px",
        threshold: [0.2, 0.4, 0.6],
      },
    );

    navItems.forEach((item) => {
      const element = document.getElementById(item.id);
      if (element) {
        observer.observe(element);
      }
    });

    return () => observer.disconnect();
  }, [reviewData]);

  const scrollToSection = (sectionId: string) => {
    setActiveSection(sectionId);
    document.getElementById(sectionId)?.scrollIntoView({
      behavior: "smooth",
      block: "start",
    });
  };

  if (selectedHistoryLoading && historyMode && !reviewData) {
    return (
      <div className="grid gap-8 lg:grid-cols-[13rem_minmax(0,1fr)]">
        <aside className="hidden lg:block" />
        <div className="space-y-6">
          <Card className="h-56 bg-white/70" />
          <Card className="h-48 bg-white/70" />
          <Card className="h-72 bg-white/70" />
        </div>
      </div>
    );
  }

  if (selectedHistoryError && historyMode) {
    return (
      <Card className="p-8">
        <div className="rounded-[1.5rem] border border-rose-100 bg-rose-50 px-5 py-5 text-sm leading-7 text-rose-700">
          {selectedHistoryError}
        </div>
      </Card>
    );
  }

  if (taskId && (!reviewData || taskDetail?.status === "FAILED")) {
    const waiting = !taskDetail || !taskDetail.terminal || taskDetail.status === "SUCCEEDED";
    const headline = waiting ? "后台正在生成这份诊断结果" : "这次诊断没有成功完成";
    const description = waiting
      ? buildAsyncTaskProgressMessage(taskDetail)
      : taskError || buildAsyncTaskErrorMessage(taskDetail, "text");

    return (
      <Card className="overflow-hidden p-2">
        <div className="rounded-[1.65rem] border border-indigo-50 bg-gradient-to-br from-indigo-50/80 via-white to-teal-50/50 p-8 sm:p-10">
          <div className="mx-auto max-w-3xl">
            <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl bg-indigo-100 text-indigo-600">
              {waiting ? (
                <RefreshCw size={28} className={joinClasses(taskLoading && "animate-spin")} />
              ) : (
                <AlertTriangle size={28} />
              )}
            </div>
            <h1 className="mt-6 text-center text-3xl font-black tracking-tight text-slate-900">
              {headline}
            </h1>
            <p className="mt-4 text-center text-sm leading-7 text-slate-600">
              {description}
            </p>

            <div className="mt-8 grid gap-4 rounded-[1.6rem] border border-white/80 bg-white/80 p-5 shadow-[0_18px_40px_rgba(148,163,184,0.12)] backdrop-blur-sm sm:grid-cols-2 xl:grid-cols-4">
              <div>
                <div className="text-[11px] font-bold text-slate-400">Task</div>
                <div className="mt-2 text-sm font-semibold text-slate-800">{taskId}</div>
              </div>
              <div>
                <div className="text-[11px] font-bold text-slate-400">Status</div>
                <div className="mt-2 text-sm font-semibold text-slate-800">{taskStatusLabel}</div>
              </div>
              <div>
                <div className="text-[11px] font-bold text-slate-400">Provider / Model</div>
                <div className="mt-2 text-sm font-semibold text-slate-800">
                  {taskDetail?.providerCode || "等待分配"}
                  <span className="mx-1 text-slate-300">/</span>
                  {taskDetail?.modelName || "等待解析"}
                </div>
              </div>
              <div>
                <div className="text-[11px] font-bold text-slate-400">Attempt</div>
                <div className="mt-2 text-sm font-semibold text-slate-800">
                  {taskDetail ? `${taskDetail.currentAttempt} / ${taskDetail.maxAttempts}` : "—"}
                </div>
              </div>
            </div>

            {taskDetail?.resultSummary ? (
              <div className="mt-5 rounded-[1.4rem] border border-indigo-100 bg-indigo-50/70 px-5 py-4 text-sm leading-7 text-slate-700">
                <span className="font-semibold text-indigo-700">后台摘要：</span>
                {taskDetail.resultSummary}
              </div>
            ) : null}

            {taskError && waiting ? (
              <div className="mt-5 rounded-[1.4rem] border border-rose-100 bg-rose-50 px-5 py-4 text-sm leading-7 text-rose-700">
                {taskError}
              </div>
            ) : null}

            <div className="mt-8 flex flex-wrap justify-center gap-3">
              <Button onClick={onRefreshTask} disabled={taskLoading}>
                <RefreshCw size={16} className={joinClasses("mr-2", taskLoading && "animate-spin")} />
                刷新任务状态
              </Button>
              <Button variant="outline" onClick={onBackToCompose}>
                <ArrowLeft size={16} className="mr-2" />
                {backActionLabel}
              </Button>
              <Button variant="outline" onClick={onOpenReviewCenter}>
                <History size={16} className="mr-2" />
                前往复盘中心
              </Button>
            </div>
          </div>
        </div>
      </Card>
    );
  }

  if (!reviewData) {
    return (
      <Card className="overflow-hidden p-2">
        <div className="rounded-[1.65rem] border border-indigo-50 bg-gradient-to-br from-indigo-50/80 via-white to-teal-50/50 p-8 sm:p-10">
          <div className="mx-auto max-w-2xl text-center">
            <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl bg-indigo-100 text-indigo-600">
              <Bot size={30} />
            </div>
            <h1 className="mt-6 text-3xl font-black tracking-tight text-slate-900">
              复盘中心还没有可展示的结果
            </h1>
            <p className="mt-4 text-sm leading-7 text-slate-600">
              先回到提交操作页发起一轮新的诊断，或前往复盘中心统一回看已有结果。
            </p>
            <div className="mt-8 flex flex-wrap justify-center gap-3">
              <Button onClick={onBackToCompose}>
                <ArrowLeft size={16} className="mr-2" />
                {backActionLabel}
              </Button>
              <Button variant="outline" onClick={onOpenReviewCenter}>
                <History size={16} className="mr-2" />
                前往复盘中心
              </Button>
            </div>
          </div>
        </div>
      </Card>
    );
  }

  return (
    <motion.div
      variants={staggerContainer}
      initial="hidden"
      animate="show"
      exit={{ opacity: 0, x: -20, transition: { duration: 0.2 } }}
      className="relative flex flex-col items-start gap-8 lg:flex-row"
    >
      <motion.aside variants={fadeUpItem} className="hidden w-48 shrink-0 lg:sticky lg:top-24 lg:block">
        <div className="mb-6 flex items-center">
          <button
            type="button"
            onClick={onBackToCompose}
            className="flex items-center text-sm font-semibold text-slate-500 transition-colors hover:text-slate-800"
          >
            <ArrowLeft size={16} className="mr-1.5" />
            {backActionLabel}
          </button>
        </div>

        <nav className="relative space-y-2 before:absolute before:bottom-2 before:left-[11px] before:top-2 before:w-[2px] before:bg-slate-200/60">
          {navItems.map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => scrollToSection(item.id)}
              className={joinClasses(
                "relative z-10 flex w-full items-center rounded-[1.25rem] border px-3 py-2 text-sm font-semibold transition-all",
                activeSection === item.id
                  ? "border-indigo-100 bg-indigo-50 text-indigo-700 shadow-sm"
                  : "border-transparent text-slate-500 hover:bg-slate-100/50 hover:text-slate-800",
              )}
            >
              <div
                className={joinClasses(
                  "mr-3 flex h-6 w-6 items-center justify-center rounded-full border bg-white transition-colors",
                  activeSection === item.id
                    ? "border-indigo-400 text-indigo-600 shadow-sm"
                    : "border-slate-200 text-slate-400",
                )}
              >
                <item.icon size={12} strokeWidth={2.5} />
              </div>
              {item.label}
            </button>
          ))}
        </nav>
      </motion.aside>

      <div className="w-full max-w-[68rem] flex-1 space-y-10 pb-32 lg:ml-4 xl:ml-6">
        <AnimatePresence>
          {historyMode ? (
            <motion.div
              initial={{ opacity: 0, y: -10, height: 0 }}
              animate={{ opacity: 1, y: 0, height: "auto" }}
              exit={{ opacity: 0, y: -10, height: 0 }}
              className="sticky top-24 z-30 mb-6 rounded-[1.5rem] border border-amber-200/60 bg-amber-50/88 p-5 shadow-[0_14px_36px_rgba(251,191,36,0.16)] backdrop-blur-md"
            >
              <div className="flex items-center gap-4">
                <div className="shrink-0 rounded-xl border border-amber-200/50 bg-amber-100/80 p-2.5 text-amber-600 shadow-sm">
                  <Clock3 size={20} strokeWidth={2.5} />
                </div>
                <div>
                  <h4 className="text-[15px] font-bold text-amber-900">当前处于「历史快照」模式</h4>
                  <p className="mt-1 text-xs font-medium leading-relaxed text-amber-700">
                    你正在查看过去的诊断记录，此处建议可能不适用于你最新的简历改动。
                  </p>
                </div>
              </div>
            </motion.div>
          ) : null}
        </AnimatePresence>

        <motion.div variants={fadeUpItem} id="overview" className="scroll-mt-24 space-y-6">
          <Card className="overflow-hidden border-0 bg-white p-2 shadow-[0_22px_60px_rgba(148,163,184,0.18)]">
            <div className="relative overflow-hidden rounded-[1.65rem] border border-indigo-50 bg-gradient-to-br from-indigo-50/80 via-white to-teal-50/50 p-6 sm:p-8">
              <div className="pointer-events-none absolute -right-16 -top-14 text-indigo-600 opacity-5">
                <Target size={220} />
              </div>
              <div className="relative z-10 mb-6 flex flex-wrap items-center justify-between gap-4">
                <div>
                  <h1 className="mb-3 text-3xl font-black tracking-tight text-slate-950 sm:text-4xl">
                    本次诊断总评
                  </h1>
                  <div className="flex flex-wrap items-center gap-2 text-sm font-medium text-slate-500">
                    <Badge variant="outline" className="bg-white/60">
                      {reviewData.targetRole}
                    </Badge>
                    <span className="text-slate-300">|</span>
                    <span>{reviewData.targetContext}</span>
                    <span className="text-slate-300">|</span>
                    <span>{reviewData.createdAt ? formatRelativeTime(reviewData.createdAt) : "刚刚完成"}</span>
                  </div>
                </div>

                <motion.div
                  initial={{ scale: 0.8, opacity: 0, rotate: -5 }}
                  animate={{ scale: 1, opacity: 1, rotate: 3 }}
                  transition={{ type: "spring", bounce: 0.35, duration: 0.8, delay: 0.15 }}
                  className={joinClasses(
                    "flex h-32 w-32 flex-col items-center justify-center rounded-[2rem] border shadow-lg sm:mr-8 sm:h-36 sm:w-36 lg:mr-14 lg:h-40 lg:w-40 xl:mr-16",
                    scoreStyle.cardBg,
                    scoreStyle.shadow,
                    scoreStyle.border,
                  )}
                >
                  <span className={joinClasses("text-6xl font-black drop-shadow-md sm:text-7xl", scoreStyle.cardText)}>
                    {scoreLabel}
                  </span>
                  <span className={joinClasses("mt-1.5 text-[13px] font-bold sm:text-[15px]", scoreStyle.cardSubtext)}>
                    推荐度
                  </span>
                </motion.div>
              </div>

              <div className="relative z-10 rounded-[1.5rem] border border-white bg-white/70 p-6 text-slate-700 shadow-sm backdrop-blur-sm">
                <p className="mb-3 text-[15px]">
                  <strong className="font-bold text-slate-900">一句话总结：</strong>
                  {reviewData.report.summary}
                </p>
                <p className="text-sm font-medium text-slate-600">
                  当前推荐度、结构地图和部分修改建议为前端体验层的可视化表达，核心结论仍以真实返回的摘要、亮点、风险与建议为准。
                </p>
              </div>

              <div className="relative z-10 mt-5 flex flex-wrap gap-2 text-xs text-slate-500">
                {formatDurationSeconds(reviewData.report.aiMeta?.latencyMs) ? (
                  <span className="rounded-full border border-slate-200 bg-white px-3 py-1.5">
                    用时 {formatDurationSeconds(reviewData.report.aiMeta?.latencyMs)}
                  </span>
                ) : null}
                {formatModerationSummary(reviewData.report.moderation) ? (
                  <span className="rounded-full border border-slate-200 bg-white px-3 py-1.5">
                    审核：{formatModerationSummary(reviewData.report.moderation)}
                  </span>
                ) : null}
                <span className="rounded-full border border-slate-200 bg-white px-3 py-1.5">
                  {reviewData.inputMode === "pdf" ? "PDF 模式" : "文本模式"}
                </span>
              </div>

              <div className="relative z-10 mt-6 flex flex-wrap gap-3">
                <Button variant="outline" className="rounded-full bg-white" onClick={onCopySummary}>
                  <Copy size={15} className="mr-2" />
                  {copiedKey === "review-summary" ? "已复制摘要" : "复制摘要"}
                </Button>
                <Button variant="outline" className="rounded-full bg-white" onClick={onOpenHistory}>
                  <History size={15} className="mr-2" />
                  {historyCount > 0 ? `页内速览 (${historyCount})` : "页内速览"}
                </Button>
                <Button variant="outline" className="rounded-full bg-white" onClick={onCopyReport}>
                  <Copy size={15} className="mr-2" />
                  {copiedKey === "review-report" ? "已复制结果" : "复制完整报告"}
                </Button>
                {reviewData.recordId ? (
                  <Button
                    variant="outline"
                    className="rounded-full bg-white"
                    onClick={() => onExport(reviewData.recordId!, buildResumeExportDetail(reviewData) ?? undefined)}
                    disabled={exportingRecordId === reviewData.recordId}
                  >
                    <Download size={15} className="mr-2" />
                    {exportingRecordId === reviewData.recordId ? "导出中..." : "导出报告"}
                  </Button>
                ) : null}
                <Link
                  to="/mentors"
                  className="inline-flex items-center justify-center rounded-full border border-emerald-200 bg-emerald-50 px-5 py-2.5 text-sm font-semibold text-emerald-700 shadow-sm transition-colors hover:border-emerald-300 hover:bg-emerald-100"
                >
                  <MessageSquare size={15} className="mr-2" />
                  去导师页继续沟通
                </Link>
              </div>
            </div>
          </Card>

        </motion.div>

        <motion.div variants={fadeUpItem} id="structure" className="scroll-mt-24">
          <div className="mb-5 flex flex-col sm:flex-row sm:items-end sm:justify-between">
            <div>
              <h2 className="text-2xl font-black tracking-tight text-slate-950">简历结构完整度地图</h2>
            </div>
            <Badge variant="outline" className="mt-3 bg-white text-slate-500 sm:mt-0">
              结构评估结果
            </Badge>
          </div>
          <Card className="p-8">
            <div className="grid grid-cols-1 gap-x-12 gap-y-8 md:grid-cols-2">
              {structureItems.map((item, index) => (
                <div key={item.label} className="flex flex-col gap-2">
                  <div className="flex items-center gap-3 text-sm">
                    <span
                      className={joinClasses(
                        "inline-flex w-[7rem] shrink-0 items-center justify-center rounded-full border px-3 py-1.5 text-sm font-bold shadow-sm",
                        getStructureLabelTone(item.label, index),
                      )}
                    >
                      {item.label}
                    </span>
                    <span className="min-w-0 truncate text-xs font-medium text-slate-500">{item.tip}</span>
                  </div>
                  <div className="flex h-2.5 gap-1.5">
                    {[1, 2, 3, 4, 5].map((level) => (
                      <motion.div
                        key={`${item.label}-${level}`}
                        initial={{ opacity: 0, scale: 0.8 }}
                        whileInView={{ opacity: 1, scale: 1 }}
                        viewport={{ once: true }}
                        transition={{ duration: 0.3, delay: index * 0.05 + level * 0.04 }}
                        className={joinClasses(
                          "flex-1 rounded-full",
                          level <= item.score
                            ? item.score <= 2
                              ? "bg-gradient-to-r from-amber-400 to-amber-500 shadow-sm"
                              : item.score === 5
                                ? "bg-gradient-to-r from-emerald-400 to-emerald-500 shadow-sm"
                                : "bg-gradient-to-r from-indigo-400 to-indigo-500 shadow-sm"
                            : "bg-slate-100",
                        )}
                      />
                    ))}
                  </div>
                </div>
              ))}
            </div>
          </Card>
        </motion.div>

        <motion.div variants={fadeUpItem} id="details" className="scroll-mt-24 space-y-6">
          <div className="mb-5">
            <h2 className="text-2xl font-black tracking-tight text-slate-950">详细评估与建议</h2>
          </div>

          {reviewSections.map((section) => (
            <ReviewAssessmentCard key={section.id} section={section} />
          ))}
        </motion.div>

        <motion.div variants={fadeUpItem} id="sandbox" className="scroll-mt-24">
          <div className="mb-5 flex flex-col sm:flex-row sm:items-end sm:justify-between">
            <div>
              <h2 className="text-2xl font-black tracking-tight text-slate-950">部分修改建议</h2>
            </div>
            <Badge variant="outline" className="mt-3 border-indigo-200 bg-indigo-50 font-bold text-indigo-700 sm:mt-0">
              共 {formatCount(rewriteItems.length)} 条修改建议
            </Badge>
          </div>

          <div className="space-y-6">
            {rewriteItems.map((item, index) => {
              const isExpanded = expandedRewriteId === item.id;

              return (
                <div
                  key={item.id}
                  role="button"
                  tabIndex={0}
                  onClick={() => setExpandedRewriteId((current) => (current === item.id ? null : item.id))}
                  onKeyDown={(event: KeyboardEvent<HTMLDivElement>) => {
                    if (event.key === "Enter" || event.key === " ") {
                      event.preventDefault();
                      setExpandedRewriteId((current) => (current === item.id ? null : item.id));
                    }
                  }}
                >
                  <Card
                    className={joinClasses(
                      "cursor-pointer overflow-hidden bg-white transition-colors",
                      isExpanded ? "p-2" : "border border-slate-200/80 bg-white/90 p-1.5",
                    )}
                  >
                    <div className={joinClasses("rounded-[1.5rem]", isExpanded ? "border border-indigo-50 bg-slate-50/50" : "bg-slate-50/60")}>
                      <div
                        className={joinClasses(
                          "flex items-center justify-between gap-3 px-5 py-4 transition-colors",
                          isExpanded ? "border-b border-slate-100 px-6" : "hover:bg-white/70",
                        )}
                      >
                        <div className={joinClasses("flex min-w-0 flex-1 items-center text-[17px] font-bold sm:text-[18px]", isExpanded ? "text-slate-800" : "text-slate-600")}>
                          <span
                            className={joinClasses(
                              "mr-3 flex h-6 w-6 items-center justify-center rounded-[0.4rem] text-xs font-black shadow-sm",
                              isExpanded
                                ? "bg-gradient-to-br from-indigo-500 to-violet-600 text-white"
                                : "bg-slate-200 text-slate-600",
                            )}
                          >
                            {index + 1}
                          </span>
                          <span className="truncate">{item.title}</span>
                        </div>
                        <div className="flex items-center gap-2">
                          {item.isHeuristic ? (
                            <Button
                              size="sm"
                              variant="outline"
                              className="rounded-full bg-white"
                              onClick={(event) => {
                                event.stopPropagation();
                                onCycleRewrite(index);
                                setExpandedRewriteId(item.id);
                              }}
                            >
                              <RefreshCw size={14} className="mr-1.5" />
                              换个角度
                            </Button>
                          ) : null}
                          <span className="rounded-full border border-slate-100 bg-white p-1.5 text-slate-400 shadow-sm transition-colors">
                            <ChevronDown
                              size={16}
                              strokeWidth={2.5}
                              className={joinClasses("transition-transform duration-300", isExpanded && "rotate-180 text-indigo-600")}
                            />
                          </span>
                        </div>
                      </div>

                      <AnimatePresence initial={false}>
                        {isExpanded ? (
                          <motion.div
                            initial={{ opacity: 0, height: 0 }}
                            animate={{ opacity: 1, height: "auto" }}
                            exit={{ opacity: 0, height: 0 }}
                            transition={{ duration: 0.24, ease: "easeOut" }}
                          >
                            <div className="grid grid-cols-1 gap-8 bg-white/40 p-6 sm:p-8 xl:grid-cols-2">
                              <div className="space-y-3">
                                <div className="flex justify-between text-sm font-bold tracking-[0.08em] text-slate-500">
                                  <span>原写法</span>
                                </div>
                                <div className="rounded-[1.25rem] border border-slate-100 bg-slate-50 p-5 text-[15px] font-medium leading-relaxed text-slate-500 decoration-rose-400/60 decoration-2 line-through">
                                  {item.beforeText}
                                </div>
                                <div className="mt-2 flex items-start px-1 text-sm font-medium text-rose-500">
                                  <AlertTriangle size={16} className="mr-2 mt-0.5 shrink-0" />
                                  {item.problem}
                                </div>
                              </div>

                              <div className="space-y-3">
                                <div className="flex items-center text-sm font-bold tracking-[0.08em] text-indigo-600">
                                  <Sparkles size={16} className="mr-1.5" />
                                  推荐写法
                                </div>
                                <div className="relative rounded-[1.25rem] border border-indigo-100/80 bg-gradient-to-br from-indigo-50/80 to-blue-50/30 p-5 text-[15px] font-medium leading-relaxed text-indigo-950 shadow-sm">
                                  {item.afterText}
                                </div>
                                {item.isHeuristic ? (
                                  <div className="text-xs leading-6 text-slate-500">
                                    当前为系统基于问题点生成的修改建议预演，可直接作为你继续打磨简历的参考。
                                  </div>
                                ) : null}
                              </div>
                            </div>
                            <div className="flex flex-col items-center justify-between gap-4 rounded-b-[1.5rem] border-t border-slate-100 bg-white px-6 py-5 sm:flex-row">
                              <span className="text-sm font-medium text-slate-500">
                                你可以直接采纳当前 AI 推荐的表达框架。
                              </span>
                              <div className="flex w-full gap-3 sm:w-auto">
                                {item.isHeuristic ? (
                                  <Button
                                    variant="outline"
                                    className="flex-1 rounded-full bg-white sm:flex-none"
                                    onClick={(event) => {
                                      event.stopPropagation();
                                      onCycleRewrite(index);
                                    }}
                                  >
                                    <RefreshCw size={16} className="mr-2" />
                                    换个角度
                                  </Button>
                                ) : null}
                                <Button
                                  className="flex-1 rounded-full sm:flex-none"
                                  onClick={(event) => {
                                    event.stopPropagation();
                                    onCopyRewrite(item, index);
                                  }}
                                >
                                  <Check size={16} className="mr-2" strokeWidth={3} />
                                  {copiedKey === item.id ? "已复制" : "采纳复制"}
                                </Button>
                              </div>
                            </div>
                          </motion.div>
                        ) : null}
                      </AnimatePresence>
                    </div>
                  </Card>
                </div>
              );
            })}
          </div>
        </motion.div>
      </div>

      <AnimatePresence>
        {currentExportFeedback ? (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="pointer-events-none fixed bottom-0 left-0 right-0 z-[70] p-4 sm:p-6"
          >
            <div className="mx-auto w-full max-w-3xl">
              <motion.div
                key={`resume-export-toast-${currentExportFeedback.tone}-${currentExportFeedback.message}`}
                initial={{ y: 30, opacity: 0, scale: 0.97 }}
                animate={{ y: 0, opacity: 1, scale: 1 }}
                exit={{ y: 18, opacity: 0, scale: 0.98 }}
                className="pointer-events-auto"
              >
                <div
                  className={joinClasses(
                    "mx-auto flex w-full items-center gap-4 overflow-hidden rounded-full px-6 py-3.5 shadow-2xl backdrop-blur-xl",
                    "transition-[background-color,border-color,box-shadow] duration-300 ease-out",
                    getExportToastPresentation(currentExportFeedback.tone).shellClassName,
                  )}
                >
                  <div
                    className={joinClasses(
                      "flex h-10 w-10 shrink-0 items-center justify-center rounded-full transition-colors duration-300",
                      getExportToastPresentation(currentExportFeedback.tone).iconClassName,
                    )}
                  >
                    {(() => {
                      const ToastIcon = getExportToastPresentation(currentExportFeedback.tone).icon;
                      return <ToastIcon size={18} className={joinClasses(currentExportFeedback.tone === "info" && exportingRecordId !== null && "animate-spin")} />;
                    })()}
                  </div>
                  <div className="min-w-0 flex-1 text-center">
                    <div
                      className={joinClasses(
                        "truncate text-base font-black tracking-[0.02em]",
                        getExportToastPresentation(currentExportFeedback.tone).titleClassName,
                      )}
                    >
                      {getExportToastPresentation(currentExportFeedback.tone).title}
                    </div>
                    <p
                      className={joinClasses(
                        "truncate text-[15px] leading-5",
                        getExportToastPresentation(currentExportFeedback.tone).messageClassName,
                      )}
                    >
                      {currentExportFeedback.message}
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={onDismissExportFeedback}
                    className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full border border-slate-200 bg-white/80 text-slate-500 transition-colors duration-300 hover:border-slate-300 hover:bg-white hover:text-slate-700"
                    aria-label="关闭导出提示"
                  >
                    <X size={15} />
                  </button>
                </div>
              </motion.div>
            </div>
          </motion.div>
        ) : null}
      </AnimatePresence>
    </motion.div>
  );
}

function buildResumeReviewCenterHref({
  recordId,
  taskId,
}: {
  recordId?: number | null;
  taskId?: string | null;
}) {
  if (recordId && recordId > 0) {
    return `/ai/history${buildQuery({ type: "resume", recordId })}`;
  }
  if (taskId?.trim()) {
    return `/ai/history${buildQuery({ type: "resume", taskId: taskId.trim() })}`;
  }
  return "/ai/history?type=resume";
}

export default function ResumeAnalyzerPage() {
  const { ready, role, userId, displayName } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const routeMode: RouteMode = location.pathname.endsWith("/review") ? "review" : "compose";
  const viewingTaskId = (() => {
    const rawValue = searchParams.get("taskId");
    if (!rawValue) {
      return null;
    }

    return rawValue.trim() || null;
  })();
  const viewingHistoryId = (() => {
    const rawValue = searchParams.get("recordId");
    if (!rawValue) {
      return null;
    }

    const parsed = Number(rawValue);
    return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
  })();

  const [draft, setDraft] = useState<ResumeComposeDraft>(createDefaultDraft());
  const [resumeFile, setResumeFile] = useState<File | null>(null);
  const [status, setStatus] = useState<AnalyzeStatus>("idle");
  const [streamPreview, setStreamPreview] = useState<StreamPreviewState | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [profile, setProfile] = useState<StudentWorkspaceProfile | null>(null);
  const [quota, setQuota] = useState<AiQuotaRemainingResponse | null>(null);
  const [quotaLoading, setQuotaLoading] = useState(false);
  const [quotaError, setQuotaError] = useState<string | null>(null);
  const [historyData, setHistoryData] = useState<ResumeHistoryResponse | null>(null);
  const [historyPage, setHistoryPage] = useState(1);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [selectedHistoryDetail, setSelectedHistoryDetail] = useState<ResumeHistoryDetailResponse | null>(null);
  const [selectedHistoryLoading, setSelectedHistoryLoading] = useState(false);
  const [selectedHistoryError, setSelectedHistoryError] = useState<string | null>(null);
  const [composeTaskId, setComposeTaskId] = useState<string | null>(null);
  const [activeTaskDetail, setActiveTaskDetail] = useState<ResumeAsyncTaskDetailResponse | null>(null);
  const [activeTaskLoading, setActiveTaskLoading] = useState(false);
  const [activeTaskError, setActiveTaskError] = useState<string | null>(null);
  const [showHistoryDrawer, setShowHistoryDrawer] = useState(false);
  const [showSuccessModal, setShowSuccessModal] = useState(false);
  const [showDraftRecoveryModal, setShowDraftRecoveryModal] = useState(false);
  const [historyOverlayLoaded, setHistoryOverlayLoaded] = useState(false);
  const [successOverlayLoaded, setSuccessOverlayLoaded] = useState(false);
  const [draftOverlayLoaded, setDraftOverlayLoaded] = useState(false);
  const [latestSnapshot, setLatestSnapshot] = useState<ResumeReviewSnapshot | null>(null);
  const [draftSavedAt, setDraftSavedAt] = useState<string | null>(null);
  const [isDraftRecovered, setIsDraftRecovered] = useState(false);
  const [copiedKey, setCopiedKey] = useState<string | null>(null);
  const [exportingRecordId, setExportingRecordId] = useState<number | null>(null);
  const [deletingRecordId, setDeletingRecordId] = useState<number | null>(null);
  const [exportFeedback, setExportFeedback] = useState<ExportFeedbackState | null>(null);
  const [rewriteVariantSeeds, setRewriteVariantSeeds] = useState<number[]>([]);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const copyTimerRef = useRef<number | null>(null);
  const hydratedDraftUserIdRef = useRef<number | null>(null);
  const hydratedReviewUserIdRef = useRef<number | null>(null);
  const hydratedTaskResultRef = useRef<string | null>(null);

  const studentName = buildStudentNickname(profile, displayName, "同学");
  const roleLabel = getRoleDisplayLabel(role);
  const profileDraft = useMemo(() => buildProfileDraft(profile), [profile]);
  const historyTotalPages = useMemo(
    () => Math.max(1, Math.ceil((historyData?.total ?? 0) / RESUME_HISTORY_PAGE_SIZE)),
    [historyData?.total],
  );

  const reviewFallbackRecordId = useMemo(() => {
    if (viewingHistoryId || viewingTaskId || latestSnapshot) {
      return null;
    }

    return historyData?.records[0]?.id ?? null;
  }, [historyData?.records, latestSnapshot, viewingHistoryId, viewingTaskId]);

  const historyDetailRecordId = viewingHistoryId ?? reviewFallbackRecordId;
  const historyMeta = getHistoryMeta(userId, historyDetailRecordId);
  const activePollingTaskId = routeMode === "compose" ? composeTaskId : viewingTaskId;
  const reviewBackHref = viewingHistoryId
    ? buildResumeReviewCenterHref({ recordId: viewingHistoryId })
    : "/ai/resume";
  const reviewBackLabel = viewingHistoryId ? "返回复盘中心" : "返回编辑";

  const openHistoryDrawer = () => {
    setHistoryOverlayLoaded(true);
    setShowHistoryDrawer(true);
  };

  const closeHistoryDrawer = () => {
    setShowHistoryDrawer(false);
  };

  const openSuccessModal = () => {
    setSuccessOverlayLoaded(true);
    setShowSuccessModal(true);
  };

  const closeSuccessModal = () => {
    setShowSuccessModal(false);
  };

  const handleOpenReviewCenter = () => {
    navigate(buildResumeReviewCenterHref({
      recordId: historyDetailRecordId ?? latestSnapshot?.recordId ?? null,
      taskId: viewingTaskId ?? activeTaskDetail?.taskId ?? null,
    }));
  };

  const reviewData = useMemo(
    () =>
      buildReviewWorkspaceData(
        viewingTaskId ? null : latestSnapshot,
        selectedHistoryDetail,
        Boolean(viewingHistoryId),
        viewingHistoryId,
        historyMeta,
      ),
    [historyMeta, latestSnapshot, selectedHistoryDetail, viewingHistoryId, viewingTaskId],
  );

  const reviewSections = useMemo(() => buildReportSections(reviewData?.report ?? null), [reviewData?.report]);
  const scoreLabel = useMemo(() => calculateHeuristicScore(reviewData?.report ?? null), [reviewData?.report]);
  const scoreStyle = useMemo(() => getScoreStyle(scoreLabel), [scoreLabel]);
  const latestScoreLabel = useMemo(
    () => (latestSnapshot ? calculateHeuristicScore(latestSnapshot.result) : null),
    [latestSnapshot],
  );
  const latestScoreStyle = useMemo(
    () => getScoreStyle(latestScoreLabel ?? "B"),
    [latestScoreLabel],
  );
  const structureItems = useMemo(
    () => buildStructureMapItems(reviewData, profile),
    [profile, reviewData],
  );
  const rewriteItems = useMemo(
    () => buildRewriteSandboxItems(reviewData, rewriteVariantSeeds),
    [reviewData, rewriteVariantSeeds],
  );
  const isAnalyzeDisabled = useMemo(
    () =>
      !draft.targetRole.trim()
      || (draft.inputMode === "text" ? !draft.resumeText.trim() : !resumeFile),
    [draft.inputMode, draft.resumeText, draft.targetRole, resumeFile],
  );

  useEffect(
    () => () => {
      if (copyTimerRef.current) {
        window.clearTimeout(copyTimerRef.current);
      }
    },
    [],
  );

  useEffect(() => {
    if (!ready || role !== "STUDENT" || !userId) {
      return;
    }

    if (hydratedDraftUserIdRef.current === userId) {
      return;
    }

    const storedDraft =
      readStorageJson<ResumeComposeDraft>("local", getResumeDraftStorageKey(userId)) ?? null;

    // 草稿放 localStorage，跨刷新保留；解析失败由 readStorageJson 清理坏缓存。
    if (storedDraft && storedDraft.version === 1) {
      const recovered = hasMeaningfulDraft(storedDraft);
      setDraft({
        ...createDefaultDraft(),
        ...storedDraft,
      });
      setDraftSavedAt(storedDraft.savedAt || null);
      setIsDraftRecovered(recovered);
      setDraftOverlayLoaded(recovered);
      setShowDraftRecoveryModal(recovered);
    } else {
      setDraft(createDefaultDraft());
      setDraftSavedAt(null);
      setIsDraftRecovered(false);
      setDraftOverlayLoaded(false);
      setShowDraftRecoveryModal(false);
    }

    setResumeFile(null);
    hydratedDraftUserIdRef.current = userId;
  }, [ready, role, userId]);

  useEffect(() => {
    if (!ready || role !== "STUDENT" || !userId) {
      return;
    }

    if (hydratedReviewUserIdRef.current === userId) {
      return;
    }

    const storedSnapshot =
      readStorageJson<ResumeReviewSnapshot>("session", getResumeReviewStorageKey(userId)) ?? null;

    // 最近复盘只放 sessionStorage，避免很久以前的结果误当作当前任务完成态。
    if (storedSnapshot && storedSnapshot.version === 1) {
      setLatestSnapshot(storedSnapshot);
      if (storedSnapshot.result) {
        setStatus("complete");
      }
    } else {
      setLatestSnapshot(null);
    }

    hydratedReviewUserIdRef.current = userId;
  }, [ready, role, userId]);

  useEffect(() => {
    if (role !== "STUDENT" || !userId || hydratedDraftUserIdRef.current !== userId) {
      return;
    }

    const timer = window.setTimeout(() => {
      const nextSnapshot = {
        ...draft,
        savedAt: new Date().toISOString(),
      };

      if (!hasMeaningfulDraft(nextSnapshot)) {
        removeStorageKey("local", getResumeDraftStorageKey(userId));
        setDraftSavedAt(null);
        return;
      }

      writeStorageJson("local", getResumeDraftStorageKey(userId), nextSnapshot);
      setDraftSavedAt(nextSnapshot.savedAt);
    }, 480);

    return () => window.clearTimeout(timer);
  }, [draft, role, userId]);

  useEffect(() => {
    if (role !== "STUDENT" || !userId || hydratedReviewUserIdRef.current !== userId) {
      return;
    }

    if (!latestSnapshot) {
      removeStorageKey("session", getResumeReviewStorageKey(userId));
      return;
    }

    writeStorageJson("session", getResumeReviewStorageKey(userId), latestSnapshot);
  }, [latestSnapshot, role, userId]);

  useEffect(() => {
    if (role !== "STUDENT") {
      setProfile(null);
      return;
    }

    let active = true;

    const loadProfile = async () => {
      try {
        const response = await apiRequest<StudentWorkspaceProfile>("/profiles/students/me");

        if (!active) {
          return;
        }

        setProfile(response);
        setDraft((current) => ({
          ...current,
          targetRole: current.targetRole || response.targetPosition?.trim() || "",
        }));
      } catch {
        if (active) {
          setProfile(null);
        }
      }
    };

    void loadProfile();

    return () => {
      active = false;
    };
  }, [role]);

  async function loadQuota() {
    if (role !== "STUDENT") {
      return null;
    }

    setQuotaLoading(true);
    try {
      const response = await apiRequest<AiQuotaRemainingResponse>("/ai/quota/remaining");
      setQuota(response);
      setQuotaError(null);
      return response;
    } catch (error) {
      const apiError = error as ApiClientError;
      setQuotaError(apiError.message || "加载配额信息失败");
      return null;
    } finally {
      setQuotaLoading(false);
    }
  }

  useEffect(() => {
    void loadQuota();
  }, [role]);

  async function loadHistory(page: number) {
    if (role !== "STUDENT") {
      return null;
    }

    setHistoryLoading(true);
    try {
      const response = await apiRequest<ResumeHistoryResponse>(
        `/ai/history${buildQuery({
          page,
          size: RESUME_HISTORY_PAGE_SIZE,
          taskType: "RESUME",
        })}`,
      );

      setHistoryData(response);
      setHistoryError(null);
      return response;
    } catch (error) {
      const apiError = error as ApiClientError;
      setHistoryError(apiError.message || "加载历史记录失败");
      return null;
    } finally {
      setHistoryLoading(false);
    }
  }

  useEffect(() => {
    if (role !== "STUDENT") {
      setHistoryData(null);
      setHistoryError(null);
      return;
    }

    void loadHistory(historyPage);
  }, [historyPage, role]);

  useEffect(() => {
    if (!historyDetailRecordId || role !== "STUDENT") {
      setSelectedHistoryDetail(null);
      setSelectedHistoryError(null);
      return;
    }

    let active = true;
    setSelectedHistoryLoading(true);
    setSelectedHistoryError(null);

    const loadHistoryDetail = async () => {
      try {
        const response = await apiRequest<ResumeHistoryDetailResponse>(
          `/ai/history/resume/${historyDetailRecordId}`,
        );

        if (!active) {
          return;
        }

        setSelectedHistoryDetail(response);
      } catch (error) {
        const apiError = error as ApiClientError;
        if (active) {
          setSelectedHistoryError(apiError.message || "加载历史详情失败");
          setSelectedHistoryDetail(null);
        }
      } finally {
        if (active) {
          setSelectedHistoryLoading(false);
        }
      }
    };

    void loadHistoryDetail();

    return () => {
      active = false;
    };
  }, [historyDetailRecordId, role]);

  async function refreshActiveTask(taskId = activePollingTaskId) {
    if (!taskId || role !== "STUDENT") {
      return null;
    }

    setActiveTaskLoading(true);
    try {
      const response = await apiRequest<ResumeAsyncTaskDetailResponse>(`/ai/tasks/${taskId}`);
      setActiveTaskDetail(response);
      setActiveTaskError(null);
      return response;
    } catch (error) {
      const apiError = error as ApiClientError;
      setActiveTaskError(apiError.message || "读取诊断任务状态失败");
      return null;
    } finally {
      setActiveTaskLoading(false);
    }
  }

  useEffect(() => {
    if (!activePollingTaskId || role !== "STUDENT") {
      setActiveTaskDetail(null);
      setActiveTaskError(null);
      setActiveTaskLoading(false);
      hydratedTaskResultRef.current = null;
      return;
    }

    let active = true;
    let timerId: number | null = null;

    // compose 和 review 共用同一套异步任务轮询，终态后再统一装载结果。
    const pollTask = async () => {
      const detail = await refreshActiveTask(activePollingTaskId);
      if (!active) {
        return;
      }

      if (!detail) {
        if (routeMode === "compose") {
          setStatus("error");
          setErrorMessage("读取诊断任务状态失败，请稍后重试。");
          setComposeTaskId(null);
          closeSuccessModal();
        }
        return;
      }

      if (detail.terminal) {
        return;
      }

      const nextDelay = detail.status === "PENDING" ? 1200 : 1600;
      timerId = window.setTimeout(() => {
        void pollTask();
      }, nextDelay);
    };

    void pollTask();

    return () => {
      active = false;
      if (timerId) {
        window.clearTimeout(timerId);
      }
    };
  }, [activePollingTaskId, role, routeMode]);

  useEffect(() => {
    if (!activePollingTaskId || !activeTaskDetail || !activeTaskDetail.terminal) {
      return;
    }

    if (activeTaskDetail.status === "FAILED") {
      setStatus("error");
      setErrorMessage(buildAsyncTaskErrorMessage(activeTaskDetail, draft.inputMode));
      if (routeMode === "compose") {
        setComposeTaskId(null);
        closeSuccessModal();
      }
      return;
    }

    const taskResult = extractResumeTaskResult(activeTaskDetail);
    if (!taskResult || hydratedTaskResultRef.current === activeTaskDetail.taskId) {
      return;
    }

    // 同一个 taskId 只水合一次，避免轮询和路由切换重复写 latest snapshot。
    hydratedTaskResultRef.current = activeTaskDetail.taskId;
    const responseScore = calculateHeuristicScore(taskResult);

    void Promise.all([loadQuota(), bindLatestResultRecord(taskResult, responseScore)])
      .then(() => {
        setStatus("complete");
        setErrorMessage(null);
        setActiveTaskError(null);
        if (routeMode === "compose") {
          setComposeTaskId(null);
          openSuccessModal();
          return;
        }
        startTransition(() => {
          navigate("/ai/resume/review", { replace: true });
        });
      })
      .catch((error) => {
        hydratedTaskResultRef.current = null;
        const fallbackMessage =
          error instanceof Error && error.message
            ? error.message
            : "诊断结果已经生成，但装载到结果页时失败，请稍后重试。";
        setActiveTaskError(fallbackMessage);
        if (routeMode === "compose") {
          setStatus("error");
          setErrorMessage(fallbackMessage);
          setComposeTaskId(null);
          closeSuccessModal();
        }
      });
  }, [activePollingTaskId, activeTaskDetail, draft.inputMode, navigate, routeMode]);

  useEffect(() => {
    window.scrollTo({ top: 0, behavior: "smooth" });
  }, [location.pathname, location.search]);

  useEffect(() => {
    if (!exportFeedback || exportingRecordId !== null) {
      return undefined;
    }

    const timer = window.setTimeout(() => {
      setExportFeedback((current) =>
        current?.recordId === exportFeedback.recordId && current.message === exportFeedback.message
          ? null
          : current,
      );
    }, 3200);

    return () => window.clearTimeout(timer);
  }, [exportFeedback, exportingRecordId]);

  async function handleCopy(key: string, text: string) {
    if (!text.trim()) {
      return;
    }

    try {
      await copyText(text);
      setCopiedKey(key);

      if (copyTimerRef.current) {
        window.clearTimeout(copyTimerRef.current);
      }

      copyTimerRef.current = window.setTimeout(() => {
        setCopiedKey(null);
      }, 1800);
    } catch {
      setHistoryError("当前浏览器暂时无法自动复制内容，请手动复制。");
    }
  }

  function persistHistoryMeta(recordId: number, score: string) {
    if (!userId) {
      return;
    }

    const currentMap = readHistoryMetaMap(userId);
    currentMap[String(recordId)] = {
      targetContext: draft.targetContext,
      targetRole: draft.targetRole.trim(),
      inputMode: draft.inputMode,
      jobDescription: draft.jobDescription,
      savedAt: new Date().toISOString(),
      scoreLabel: score,
    };
    writeHistoryMetaMap(userId, currentMap);
  }

  function removeHistoryMeta(recordId: number) {
    if (!userId) {
      return;
    }

    const currentMap = readHistoryMetaMap(userId);
    delete currentMap[String(recordId)];
    writeHistoryMetaMap(userId, currentMap);
  }

  async function bindLatestResultRecord(response: ResumeAnalyzeResponse, score: string) {
    let recordId = response.recordId;
    let approximateBinding = false;

    if (!recordId) {
      // 后端未回 recordId 时，用最新历史记录近似绑定，保证导出和历史入口还能工作。
      const historyResponse = await loadHistory(1);
      const latestRecord = historyResponse?.records[0];
      if (latestRecord) {
        recordId = latestRecord.id;
        approximateBinding = true;
      }
    } else {
      await loadHistory(1);
    }

    if (recordId) {
      persistHistoryMeta(recordId, score);
    }

    const completedAt = new Date().toISOString();
    const nextSnapshot: ResumeReviewSnapshot = {
      version: 1,
      savedAt: completedAt,
      completedAt,
      targetContext: draft.targetContext,
      targetRole: draft.targetRole.trim(),
      inputMode: draft.inputMode,
      jobDescription: draft.jobDescription,
      resumeText: draft.resumeText,
      pdfFileName: draft.pdfFileName,
      recordId,
      approximateBinding,
      result: {
        ...response,
        recordId: response.recordId ?? recordId,
      },
    };

    setLatestSnapshot(nextSnapshot);
    return nextSnapshot;
  }

  const handleFileSelection = (nextFile: File | null) => {
    if (!nextFile) {
      setResumeFile(null);
      setDraft((current) => ({ ...current, pdfFileName: null }));
      return;
    }

    // 当前 PDF 简历走异步任务和 Gemini Native 路由，前端先挡住非 PDF 文件。
    const fileName = nextFile.name.toLowerCase();
    const isPdf = nextFile.type === "application/pdf" || fileName.endsWith(".pdf");

    if (!isPdf) {
      setErrorMessage("当前上传文件不是 PDF，请重新选择简历文件。");
      setStatus("error");
      setResumeFile(null);
      setDraft((current) => ({ ...current, pdfFileName: null }));
      return;
    }

    setErrorMessage(null);
    setResumeFile(nextFile);
    setDraft((current) => ({ ...current, pdfFileName: nextFile.name }));
    if (status === "error") {
      setStatus("idle");
    }
  };

  const handleFileInputChange = (event: ChangeEvent<HTMLInputElement>) => {
    handleFileSelection(event.target.files?.[0] ?? null);
  };

  const handleFileDrop = (event: DragEvent<HTMLLabelElement>) => {
    event.preventDefault();
    handleFileSelection(event.dataTransfer.files?.[0] ?? null);
  };

  const handleUseProfileDraft = () => {
    if (!profileDraft) {
      return;
    }

    setDraft((current) => ({
      ...current,
      inputMode: "text",
      resumeText: profileDraft,
    }));
    setErrorMessage(null);
    setStatus("idle");
  };

  const handleClearDraft = () => {
    setDraft(createDefaultDraft());
    setResumeFile(null);
    setStatus("idle");
    setStreamPreview(null);
    setErrorMessage(null);
    setIsDraftRecovered(false);
    setDraftOverlayLoaded(false);
    setShowDraftRecoveryModal(false);

    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }

    if (userId) {
      removeStorageKey("local", getResumeDraftStorageKey(userId));
    }
  };

  const handleContinueRecoveredDraft = () => {
    setShowDraftRecoveryModal(false);
    setIsDraftRecovered(false);
  };

  async function handleExport(recordId: number, detail?: ResumeHistoryDetail) {
    setExportingRecordId(recordId);
    setExportFeedback({
      recordId,
      tone: "info",
      message: "正在打开打印预览，请稍候。",
    });

    try {
      const { exportResumeReportAsPdf } = await loadResumeReportExporter();
      await exportResumeReportAsPdf(detail ?? recordId, displayName);

      setExportFeedback({
        recordId,
        tone: "success",
        message: `记录 #${recordId} 的打印预览已打开，请选择另存为 PDF。`,
      });
    } catch (error) {
      const apiError = error as ApiClientError | Error;
      setExportFeedback({
        recordId,
        tone: "error",
        message: apiError.message || "导出报告失败",
      });
    } finally {
      setExportingRecordId(null);
    }
  }

  async function handleDeleteHistory(recordId: number) {
    const confirmed = window.confirm("删除后这条简历历史将无法回看，确定继续吗？");
    if (!confirmed) {
      return;
    }

    setDeletingRecordId(recordId);

    try {
      await apiRequest(`/ai/history/resume/${recordId}`, { method: "DELETE" });
      removeHistoryMeta(recordId);

      if (latestSnapshot?.recordId === recordId) {
        setLatestSnapshot((current) =>
          current
            ? {
                ...current,
                recordId: null,
                approximateBinding: false,
                result: {
                  ...current.result,
                  recordId: null,
                },
              }
            : current,
        );
      }

      if (viewingHistoryId === recordId) {
        navigate("/ai/resume/review", { replace: true });
      }

      const nextTotal = Math.max((historyData?.total ?? 1) - 1, 0);
      const nextPage =
        nextTotal > 0
          ? Math.min(historyPage, Math.ceil(nextTotal / RESUME_HISTORY_PAGE_SIZE))
          : 1;

      if (nextPage !== historyPage) {
        setHistoryPage(nextPage);
      } else {
        await loadHistory(nextPage);
      }

      setSelectedHistoryError(null);
      setHistoryError(null);
      if (selectedHistoryDetail?.recordId === recordId) {
        setSelectedHistoryDetail(null);
      }
    } catch (error) {
      const apiError = error as ApiClientError;
      setSelectedHistoryError(apiError.message || "删除历史记录失败");
    } finally {
      setDeletingRecordId(null);
    }
  }

  async function runTextAnalyzeStream() {
    // 流式接口保留为文本简历备用链路；当前主按钮默认走异步任务。
    const response = await apiRequest<Response>("/ai/resume/optimize/stream", {
      method: "POST",
      body: JSON.stringify({
        targetRole: draft.targetRole.trim(),
        targetContext: draft.targetContext,
        jobDescription: draft.jobDescription.trim(),
        resumeText: draft.resumeText.trim(),
      }),
      rawResponse: true,
    });

    if (!response.ok) {
      throw await buildApiClientErrorFromResponse(response, "流式简历优化失败");
    }

    if (!response.body) {
      throw new ApiClientError("当前流式响应不可用，请稍后重试。", 500, "SSE_STREAM_EMPTY");
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    let finalResult: ResumeAnalyzeResponse | null = null;

    const consumeEventBlock = (rawBlock: string) => {
      const parsed = parseSseBlock(rawBlock);
      if (!parsed) {
        return;
      }

      const payload = parsed.payload as Record<string, unknown> | null;

      switch (parsed.eventName) {
        case "start":
          setStreamPreview((current) => ({
            traceId:
              typeof payload?.traceId === "string"
                ? payload.traceId
                : (current?.traceId ?? null),
            message:
              typeof payload?.message === "string"
                ? payload.message
                : (current?.message ?? "已开始处理你的文本简历。"),
            summary: current?.summary ?? null,
            strengths: current?.strengths ?? null,
            risks: current?.risks ?? null,
            suggestions: current?.suggestions ?? null,
          }));
          break;
        case "summary":
          setStreamPreview((current) => ({
            traceId:
              typeof payload?.traceId === "string"
                ? payload.traceId
                : (current?.traceId ?? null),
            message:
              typeof payload?.message === "string"
                ? payload.message
                : (current?.message ?? null),
            summary:
              typeof payload?.summary === "string"
                ? payload.summary
                : (current?.summary ?? null),
            strengths: current?.strengths ?? null,
            risks: current?.risks ?? null,
            suggestions: current?.suggestions ?? null,
          }));
          break;
        case "strengths":
          setStreamPreview((current) => ({
            traceId:
              typeof payload?.traceId === "string"
                ? payload.traceId
                : (current?.traceId ?? null),
            message: current?.message ?? null,
            summary: current?.summary ?? null,
            strengths: Array.isArray(payload?.items)
              ? payload.items.filter((item): item is string => typeof item === "string")
              : [],
            risks: current?.risks ?? null,
            suggestions: current?.suggestions ?? null,
          }));
          break;
        case "risks":
          setStreamPreview((current) => ({
            traceId:
              typeof payload?.traceId === "string"
                ? payload.traceId
                : (current?.traceId ?? null),
            message: current?.message ?? null,
            summary: current?.summary ?? null,
            strengths: current?.strengths ?? null,
            risks: Array.isArray(payload?.items)
              ? payload.items.filter((item): item is string => typeof item === "string")
              : [],
            suggestions: current?.suggestions ?? null,
          }));
          break;
        case "suggestions":
          setStreamPreview((current) => ({
            traceId:
              typeof payload?.traceId === "string"
                ? payload.traceId
                : (current?.traceId ?? null),
            message: current?.message ?? null,
            summary: current?.summary ?? null,
            strengths: current?.strengths ?? null,
            risks: current?.risks ?? null,
            suggestions: Array.isArray(payload?.items)
              ? payload.items.filter((item): item is string => typeof item === "string")
              : [],
          }));
          break;
        case "done":
          if (payload?.result) {
            finalResult = payload.result as ResumeAnalyzeResponse;
          }
          break;
        case "error":
          throw new ApiClientError(
            typeof payload?.message === "string" ? payload.message : "流式简历优化失败",
            400,
            typeof payload?.code === "string" ? payload.code : "HTTP_ERROR",
            typeof payload?.traceId === "string" ? payload.traceId : null,
            payload?.data ?? null,
          );
        default:
          break;
      }
    };

    while (true) {
      const { value, done } = await reader.read();
      buffer += decoder.decode(value ?? new Uint8Array(), { stream: !done }).replace(/\r\n/g, "\n");

      let boundaryIndex = buffer.indexOf("\n\n");
      while (boundaryIndex !== -1) {
        const block = buffer.slice(0, boundaryIndex);
        buffer = buffer.slice(boundaryIndex + 2);
        consumeEventBlock(block);
        boundaryIndex = buffer.indexOf("\n\n");
      }

      if (done) {
        break;
      }
    }

    if (buffer.trim()) {
      consumeEventBlock(buffer);
    }

    if (!finalResult) {
      throw new ApiClientError("AI 已完成流式输出，但未返回最终结果。", 500, "SSE_DONE_MISSING");
    }

    return finalResult;
  }

  async function submitResumeAnalyzeTask() {
    if (draft.inputMode === "text") {
      // 文本模式提交 JSON，后端会把 input/context/prompt/route 固化到异步任务。
      return apiRequest<ResumeAsyncTaskSubmitResponse>("/ai/resume/tasks", {
        method: "POST",
        body: JSON.stringify({
          targetRole: draft.targetRole.trim(),
          targetContext: draft.targetContext,
          jobDescription: draft.jobDescription.trim(),
          resumeText: draft.resumeText.trim(),
        }),
      });
    }

    const formData = new FormData();
    formData.set("targetRole", draft.targetRole.trim());
    formData.set("targetContext", draft.targetContext);
    formData.set("jobDescription", draft.jobDescription.trim());

    if (resumeFile) {
      formData.set("resumeFile", resumeFile);
    }

    // PDF 模式提交 FormData，文件本体随任务入队，轮询接口只返回状态和结果摘要。
    return apiRequest<ResumeAsyncTaskSubmitResponse>("/ai/resume/tasks", {
      method: "POST",
      body: formData,
    });
  }

  async function handleAnalyze() {
    if (isAnalyzeDisabled) {
      return;
    }

    if (userId) {
      // 提交前先落一次草稿，任务失败后仍能回到原输入继续修改。
      const submittedDraft = {
        ...draft,
        savedAt: new Date().toISOString(),
      };
      writeStorageJson("local", getResumeDraftStorageKey(userId), submittedDraft);
      setDraftSavedAt(submittedDraft.savedAt);
    }

    setStatus("analyzing");
    setErrorMessage(null);
    setExportFeedback(null);
    setStreamPreview(null);
    setComposeTaskId(null);
    closeSuccessModal();

    try {
      const taskTicket = await submitResumeAnalyzeTask();
      // 提交成功后弹出进度浮层，结果装载交给 activePollingTaskId 轮询流程。
      setActiveTaskDetail(null);
      setActiveTaskError(null);
      hydratedTaskResultRef.current = null;
      setComposeTaskId(taskTicket.taskId);
      setStreamPreview({
        traceId: null,
        message: "诊断任务已提交，正在整理简历内容并匹配目标岗位。",
        summary: null,
        strengths: null,
        risks: null,
        suggestions: null,
      });
      openSuccessModal();
    } catch (error) {
      setErrorMessage(buildAnalyzeErrorMessage(error, draft.inputMode));
      setStatus("error");
    }
  }

  function handleGoToReview() {
    closeSuccessModal();
    navigate("/ai/resume/review");
  }

  function handleOpenHistoryRecord(recordId: number) {
    closeHistoryDrawer();
    navigate(`/ai/history?type=resume&recordId=${recordId}`);
  }

  function handleCopyRewrite(item: RewriteSandboxItem, index: number) {
    void handleCopy(item.id || `rewrite-${index + 1}`, item.afterText);
  }

  function handleCycleRewrite(index: number) {
    setRewriteVariantSeeds((current) => {
      const next = [...current];
      next[index] = (next[index] ?? 0) + 1;
      return next;
    });
  }

  if (!ready) {
    return (
      <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-slate-950 px-6 py-10 text-white">
        <div className="pointer-events-none fixed inset-0 z-0 overflow-hidden">
          <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(99,102,241,0.35),transparent_42%),radial-gradient(circle_at_bottom,rgba(45,212,191,0.22),transparent_36%),linear-gradient(180deg,#020617_0%,#0f172a_100%)]" />
        </div>
        <div className="relative z-10 w-full max-w-md rounded-[2rem] border border-white/12 bg-white/8 p-8 shadow-[0_32px_80px_rgba(15,23,42,0.4)] backdrop-blur-xl">
          <div className="h-2 w-28 rounded-full bg-white/20" />
          <div className="mt-5 h-8 w-56 rounded-full bg-white/10" />
          <div className="mt-3 h-4 w-full rounded-full bg-white/8" />
          <div className="mt-2 h-4 w-4/5 rounded-full bg-white/8" />
          <div className="mt-10 grid grid-cols-2 gap-4">
            <div className="h-28 rounded-[1.5rem] bg-white/10" />
            <div className="h-28 rounded-[1.5rem] bg-white/10" />
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="relative min-h-screen bg-[#eef3ff] font-sans text-slate-900 selection:bg-indigo-100">
      <div className="pointer-events-none fixed inset-0 z-0 overflow-hidden">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(79,70,229,0.18),transparent_30%),radial-gradient(circle_at_bottom_right,rgba(20,184,166,0.12),transparent_28%),linear-gradient(180deg,#eff6ff_0%,#f8fafc_58%,#f8fafc_100%)]" />
        <div className="page-top-glow page-top-glow--indigo" />
      </div>

      <WorkspaceHeader
        routeMode={routeMode}
        studentName={studentName}
        roleLabel={roleLabel}
        userId={profile?.userId ?? userId}
        avatar={profile?.avatar ?? null}
        tier={profile?.tier ?? null}
        historyCount={historyData?.total ?? 0}
        onOpenHistory={openHistoryDrawer}
        reviewBackHref={reviewBackHref}
        reviewBackLabel={reviewBackLabel}
      />

      <main className="relative z-10 mx-auto w-full max-w-[96rem] px-4 pb-6 pt-8 sm:px-6 lg:px-8 lg:pb-8 lg:pt-10">
        <AnimatePresence mode="wait">
          {routeMode === "compose" ? (
            <ComposePage
              key="compose"
              draft={draft}
              resumeFile={resumeFile}
              profile={profile}
              profileDraft={profileDraft}
              quota={quota}
              quotaLoading={quotaLoading}
              quotaError={quotaError}
              status={status}
              errorMessage={errorMessage}
              isAnalyzeDisabled={isAnalyzeDisabled}
              draftSavedAt={draftSavedAt}
              fileInputRef={fileInputRef}
              onOpenHistory={openHistoryDrawer}
              onOpenReviewCenter={handleOpenReviewCenter}
              onRefreshQuota={() => void loadQuota()}
              onAnalyze={() => void handleAnalyze()}
              onUseProfileDraft={handleUseProfileDraft}
              onClearDraft={handleClearDraft}
              onTargetContextChange={(value) =>
                setDraft((current) => ({ ...current, targetContext: value }))
              }
              onTargetRoleChange={(value) =>
                setDraft((current) => ({ ...current, targetRole: value }))
              }
              onJobDescriptionChange={(value) =>
                setDraft((current) => ({ ...current, jobDescription: value }))
              }
              onInputModeChange={(mode) =>
                setDraft((current) => ({ ...current, inputMode: mode }))
              }
              onResumeTextChange={(value) =>
                setDraft((current) => ({ ...current, resumeText: value }))
              }
              onFileInputChange={handleFileInputChange}
              onFileDrop={handleFileDrop}
              onRemoveFile={() => {
                setResumeFile(null);
                setDraft((current) => ({ ...current, pdfFileName: null }));
                if (fileInputRef.current) {
                  fileInputRef.current.value = "";
                }
              }}
            />
          ) : (
            <ReviewPage
              key="review"
              reviewData={reviewData}
              taskId={viewingTaskId}
              taskDetail={activeTaskDetail}
              taskLoading={activeTaskLoading}
              taskError={activeTaskError}
              scoreLabel={scoreLabel}
              scoreStyle={scoreStyle}
              reviewSections={reviewSections}
              structureItems={structureItems}
              rewriteItems={rewriteItems}
              exportFeedback={exportFeedback}
              exportingRecordId={exportingRecordId}
              copiedKey={copiedKey}
              historyCount={historyData?.total ?? 0}
              historyMode={Boolean(viewingHistoryId)}
              backActionLabel={reviewBackLabel}
              selectedHistoryLoading={selectedHistoryLoading}
              selectedHistoryError={selectedHistoryError}
              onBackToCompose={() => navigate(reviewBackHref)}
              onDismissExportFeedback={() => setExportFeedback(null)}
              onOpenHistory={openHistoryDrawer}
              onOpenReviewCenter={handleOpenReviewCenter}
              onExitHistory={() => navigate("/ai/resume/review")}
              onRefreshTask={() => {
                void refreshActiveTask();
              }}
              onExport={(recordId, detail) => void handleExport(recordId, detail)}
              onCopySummary={() => {
                if (reviewData) {
                  void handleCopy("review-summary", reviewData.report.summary);
                }
              }}
              onCopyReport={() => {
                if (reviewData) {
                  void handleCopy(
                    "review-report",
                    buildReportCopyText("目标岗位", reviewData.targetRole, reviewData.report),
                  );
                }
              }}
              onCopyRewrite={handleCopyRewrite}
              onCycleRewrite={handleCycleRewrite}
            />
          )}
        </AnimatePresence>
      </main>

      {historyOverlayLoaded ? (
        <Suspense fallback={showHistoryDrawer ? <ResumeOverlayFallback /> : null}>
          <ResumeAnalyzerOverlay
            kind="history"
            isOpen={showHistoryDrawer}
            currentRecordId={viewingHistoryId ?? latestSnapshot?.recordId ?? null}
            historyData={historyData}
            loading={historyLoading}
            errorMessage={historyError}
            page={historyPage}
            totalPages={historyTotalPages}
            deletingRecordId={deletingRecordId}
            onClose={closeHistoryDrawer}
            onRefresh={() => void loadHistory(historyPage)}
            onPageChange={(page) => setHistoryPage(page)}
            onViewRecord={handleOpenHistoryRecord}
            onDeleteRecord={(recordId) => void handleDeleteHistory(recordId)}
          />
        </Suspense>
      ) : null}

      {successOverlayLoaded ? (
        <Suspense fallback={showSuccessModal ? <ResumeOverlayFallback /> : null}>
          <ResumeAnalyzerOverlay
            kind="success"
            open={showSuccessModal}
            isAnalyzing={status === "analyzing"}
            streamPreview={streamPreview}
            result={latestSnapshot?.result ?? null}
            scoreLabel={latestScoreLabel ?? "B"}
            scoreTextClassName={latestScoreStyle.text}
            onClose={closeSuccessModal}
            onGoToReview={handleGoToReview}
          />
        </Suspense>
      ) : null}

      {draftOverlayLoaded ? (
        <Suspense fallback={showDraftRecoveryModal ? <ResumeOverlayFallback /> : null}>
          <ResumeAnalyzerOverlay
            kind="draft"
            open={routeMode === "compose" && showDraftRecoveryModal && isDraftRecovered}
            savedAt={draftSavedAt}
            showPdfRestoreHint={Boolean(draft.pdfFileName && !resumeFile)}
            onContinue={handleContinueRecoveredDraft}
            onClear={handleClearDraft}
          />
        </Suspense>
      ) : null}
    </div>
  );
}
