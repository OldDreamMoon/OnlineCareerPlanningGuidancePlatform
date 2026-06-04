import { AnimatePresence, motion } from "framer-motion";
import {
  AlertCircle,
  ArrowRight,
  Bot,
  Briefcase,
  CheckCircle2,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Clock3,
  FileAudio2,
  Gauge,
  Headphones,
  MessageSquare,
  Mic,
  PhoneOff,
  Play,
  RefreshCw,
  Send,
  Sparkles,
  Target,
  UploadCloud,
  Volume2,
  Wallet,
  WandSparkles,
  X,
  type LucideIcon,
} from "lucide-react";
import { Suspense, lazy, startTransition, useEffect, useMemo, useRef, useState, type ChangeEvent, type KeyboardEvent, type ReactNode } from "react";
import { Link, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import StudentIdentityAvatar from "../components/avatar/StudentIdentityAvatar";
import StudentWorkspaceTopbar, { buildStudentWorkspacePrimaryNav } from "../components/student/StudentWorkspaceTopbar";
import WorkspacePageLoadingScreen from "../components/WorkspacePageLoadingScreen";
import { ApiClientError, apiRequest, buildQuery } from "../lib/apiClient";
import { formatDateTime, formatTime } from "../lib/formatters";
import { InterviewLiveClient, type InterviewLiveEvent } from "../lib/interviewLiveClient";
import { buildStudentNickname } from "../lib/studentNames";
import { type StudentAvatarMeta } from "../lib/studentAvatar";

const InterviewPracticeOverlay = lazy(() => import("./InterviewPracticeOverlays"));

type TimeValue = number | string;
type InterviewStage = "prepare" | "session" | "report";
type AnswerMode = "text" | "voice" | "live";
type DifficultyLevel = "EASY" | "MEDIUM" | "HARD";
type InterviewType = "PROJECT_DEEP_DIVE" | "FUNDAMENTALS" | "BEHAVIORAL" | "PRESSURE";
type InterviewerStyle = "COACHING" | "STANDARD" | "PRESSURE" | "HR";
type AnswerHelperCueKey = "STAR" | "METRICS" | "COMPLETENESS";
type MicStatus = "idle" | "checking" | "ready" | "blocked" | "unsupported";
const LATEST_FEEDBACK_ROTATION_MS = 10000;
const AUTO_FINISH_SUMMARY_MODAL_DELAY_MS = 900;
const AUTO_FINISH_REPORT_NAV_DELAY_MS = 2200;
const STREAMING_MESSAGE_VISUAL_SETTLE_MS = 900;
type PrepMaterialKey =
  | "CAMPUS_BACKGROUND"
  | "JOB_STATUS"
  | "ACADEMIC_RECORDS"
  | "GROWTH_PORTRAIT"
  | "LATEST_RESUME"
  | "SELF_INTRO"
  | "SKILL_TAGS"
  | "TARGET_COMPANY"
  | "TARGET_JD";
type ManualPrepMaterialKey = Exclude<PrepMaterialKey, "TARGET_COMPANY" | "TARGET_JD">;
type PrepareDrawerKey = "INTERVIEW_TYPE" | "INTERVIEWER_STYLE" | "DIFFICULTY" | "ANSWER_MODE" | "PREP_MATERIALS" | "ANSWER_HELPER";

type StudentProfileHint = {
  userId: number;
  displayName: string;
  realName: string | null;
  avatar?: StudentAvatarMeta | null;
  tier?: string | null;
  jobStatus: string | null;
  schoolName: string | null;
  major: string | null;
  grade: string | null;
  gpa: string | null;
  targetPosition: string | null;
  honors: string | null;
  skillTags: string[] | null;
  selfIntro: string | null;
  portrait?: {
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
  } | null;
};

type AiMetaPayload = {
  taskType: string;
  provider: string;
  model: string;
  latencyMs: number | null;
} | null;

type AiModerationPayload = {
  sourceType: string;
  riskLevel: string;
  action: string;
  reasonCode: string;
} | null;

type InterviewSummary = {
  overallScore: number;
  strengths: string[];
  weaknesses: string[];
  suggestions: string[];
  aiMeta: AiMetaPayload;
  moderation: AiModerationPayload;
};

type InterviewSessionCreateResponse = {
  sessionId: string;
  mode: string;
  firstQuestion: string;
  chargedPoints: number;
  pointsBalanceAfterReserve: number;
  quotaUnitsReserved: number;
  replyRoundLimit: number;
  moderation: AiModerationPayload;
};

type InterviewEntryOptionsResponse = {
  voiceAnswerEnabled: boolean;
  liveInterviewEnabled: boolean;
};

type InterviewReplyResponse = {
  followUpQuestion: string | null;
  coachFeedback: string | null;
  scoreHint: number;
  shouldFinish: boolean;
  finishReason: string | null;
  sessionStatus: string;
  summary: InterviewSummary | null;
  aiMeta: AiMetaPayload;
  moderation: AiModerationPayload;
};

type InterviewVoiceRoundtripResponse = {
  transcript: string;
  audioObjectKey: string | null;
  transcriptMeta: AiMetaPayload;
  followUpQuestion: string | null;
  coachFeedback: string | null;
  scoreHint: number;
  shouldFinish: boolean;
  finishReason: string | null;
  sessionStatus: string;
  summary: InterviewSummary | null;
  aiMeta: AiMetaPayload;
  moderation: AiModerationPayload;
};

type InterviewToastState = {
  id: number;
  tone: "error";
  message: string;
};

type LiveLogEntry = {
  id: string;
  title: string;
  detail: string;
};

type InterviewSessionMessageItem = {
  role: "ASSISTANT" | "USER" | "SYSTEM" | string;
  text: string;
  coachFeedback: string | null;
  scoreHint: number | null;
  audioObjectKey: string | null;
  createdAt: TimeValue;
};

type InterviewSessionDetailResponse = {
  sessionId: string;
  targetRole: string;
  mode: string;
  status: string;
  prepaidPoints: number;
  reservedQuotaWeight: number;
  pointsBalance?: number | null;
  replyRoundLimit: number;
  replyRoundUsed: number;
  endedByAi: boolean;
  finishReason: string;
  createdAt: TimeValue;
  summaryGeneratedAt: TimeValue | null;
  sessionContext: {
    interviewType: InterviewType;
    interviewerStyle: InterviewerStyle;
    difficulty: DifficultyLevel;
    answerMode: "TEXT" | "VOICE" | "LIVE";
    targetCompany: string;
    targetJobDescription: string;
    prepMaterialKeys: PrepMaterialKey[];
    answerHelperEnabled: boolean | null;
    answerHelperCueKeys: AnswerHelperCueKey[];
    promptContext: string;
  } | null;
  resumeContext: {
    recordId: number;
    summary: string;
    suggestions: string[];
    scoreLabel: string | null;
    targetRole: string;
    targetContext: string;
    inputMode: "text" | "pdf";
    jobDescription: string;
    pdfFileName: string | null;
    resumeTextExcerpt: string;
    createdAt: TimeValue | null;
  } | null;
  messages: InterviewSessionMessageItem[];
  summary: InterviewSummary | null;
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

type InterviewHistoryItem = {
  id: number;
  taskType: "INTERVIEW_TEXT";
  summary: string;
  pointsConsumed: number;
  sessionId: string | null;
  status: string | null;
  createdAt: TimeValue | null;
};

type InterviewHistoryResponse = {
  records: InterviewHistoryItem[];
  total: number;
  page: number;
  size: number;
};

type ResumePrepSnapshot = {
  recordId: number;
  summary: string;
  suggestions: string[];
  scoreLabel: string | null;
  targetRole: string;
  targetContext: string;
  inputMode: "text" | "pdf";
  jobDescription: string;
  resumeText: string;
  pdfFileName: string | null;
  pointsConsumed: number;
  createdAt: TimeValue;
};

type TextToSpeechResponse = {
  text: string;
  stylePrompt: string;
  voiceName: string;
  mimeType: string;
  sampleRate: number;
  audioBase64: string;
  aiMeta: AiMetaPayload;
};

type ChatMessage = {
  id: string;
  role: "ASSISTANT" | "USER";
  text: string;
  createdAt: TimeValue;
  badge: string | null;
  scoreHint: number | null;
  audioObjectKey: string | null;
  isStreaming?: boolean;
};

type SummarySection = {
  id: string;
  title: string;
  sectionLabel: string;
  items: string[];
  emptyText: string;
  icon: LucideIcon;
  tone: "success" | "warning" | "info";
};

type AnswerHelperInsight = {
  key: AnswerHelperCueKey;
  label: string;
  status: "ready" | "warn" | "idle";
  statusLabel: string;
  detail: string;
};

type InterviewAnswerHelperAnalysisItem = {
  key: AnswerHelperCueKey;
  level: "READY" | "WARN" | "INFO" | string;
  summary: string;
  nextAction: string;
};

type InterviewAnswerHelperAnalysis = {
  overallLevel: "READY" | "WARN" | "INFO" | string;
  overallSummary: string;
  items: InterviewAnswerHelperAnalysisItem[];
  details: string[];
  aiMeta: AiMetaPayload;
  moderation: AiModerationPayload;
};

const motivationalQuotes = [
  "深呼吸，这是一场可以反复重来的练习。",
  "先把经历讲清楚，比一开始就追求完美更重要。",
  "AI 会替你追问，但不会替你定义你的成长。",
  "从简历亮点到表达节奏，这里会帮你把问题暴露出来。",
  "先做一轮，再决定下一轮要不要继续加压。",
];

const difficultyOptions: Array<{
  value: DifficultyLevel;
  label: string;
  detail: string;
}> = [
  { value: "EASY", label: "基础摸底", detail: "先把经历讲顺" },
  { value: "MEDIUM", label: "常规面试", detail: "贴近真实校招" },
  { value: "HARD", label: "压力追问", detail: "为答辩和高压轮热身" },
];

const interviewTypeOptions: Array<{
  value: InterviewType;
  label: string;
  detail: string;
  note: string;
}> = [
  { value: "PROJECT_DEEP_DIVE", label: "项目深挖", detail: "围绕职责、权衡与结果持续追问", note: "当前推荐" },
  { value: "FUNDAMENTALS", label: "八股基础", detail: "更偏概念理解、原理讲解与知识点表达", note: "基础问答" },
  { value: "BEHAVIORAL", label: "行为面试", detail: "围绕经历、选择与沟通方式练习 STAR", note: "表达训练" },
  { value: "PRESSURE", label: "压力追问", detail: "连续追问细节与漏洞，练习临场稳定度", note: "冲刺模式" },
];

const interviewerStyleOptions: Array<{
  value: InterviewerStyle;
  label: string;
  detail: string;
  cue: string;
}> = [
  { value: "COACHING", label: "温和引导型", detail: "更像陪练教练，先帮你把表达讲顺。", cue: "适合第一轮" },
  { value: "STANDARD", label: "常规校招型", detail: "贴近真实一面节奏，关注基础、经历与沟通。", cue: "最贴近日常" },
  { value: "PRESSURE", label: "高压追问型", detail: "更强调细节补刀、稳定性与现场反应。", cue: "适合冲刺" },
  { value: "HR", label: "HR 沟通型", detail: "更关注动机、选择、协作与岗位匹配。", cue: "行为表达" },
];

const answerHelperOptions: Array<{
  key: AnswerHelperCueKey;
  label: string;
  detail: string;
}> = [
  { key: "STAR", label: "STAR 结构提醒", detail: "提醒你补齐场景、动作与结果，避免只讲结论。" },
  { key: "METRICS", label: "量化结果提醒", detail: "提醒你补充指标、数据和结果对比，减少空泛表达。" },
  { key: "COMPLETENESS", label: "回答完整度提示", detail: "提醒你收口、补充上下文，避免答到一半就结束。" },
];

const defaultAnswerHelperCueKeys = answerHelperOptions.map((item) => item.key);
const interviewPrivacyNoticeDailyKey = "interview-privacy-notice-dismissed-date";
const microphoneWaveLevels = [0.32, 0.5, 0.74, 0.46, 0.88, 0.62, 0.78, 0.56, 0.7, 0.4];

function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function decodeBase64ToBytes(base64: string) {
  const normalizedBase64 = base64.trim();
  const binary = window.atob(normalizedBase64);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  return bytes;
}

function createWavBlobFromPcm(bytes: Uint8Array, sampleRate: number) {
  const header = new ArrayBuffer(44);
  const view = new DataView(header);
  const dataLength = bytes.byteLength;
  const writeAscii = (offset: number, text: string) => {
    for (let index = 0; index < text.length; index += 1) {
      view.setUint8(offset + index, text.charCodeAt(index));
    }
  };

  writeAscii(0, "RIFF");
  view.setUint32(4, 36 + dataLength, true);
  writeAscii(8, "WAVE");
  writeAscii(12, "fmt ");
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true);
  view.setUint16(22, 1, true);
  view.setUint32(24, sampleRate, true);
  view.setUint32(28, sampleRate * 2, true);
  view.setUint16(32, 2, true);
  view.setUint16(34, 16, true);
  writeAscii(36, "data");
  view.setUint32(40, dataLength, true);

  return new Blob([header, Uint8Array.from(bytes)], { type: "audio/wav" });
}

function createAudioBlob(response: TextToSpeechResponse) {
  const bytes = Uint8Array.from(decodeBase64ToBytes(response.audioBase64));
  const normalizedMimeType = response.mimeType.trim().toLowerCase();

  if (normalizedMimeType.includes("audio/l16") || normalizedMimeType.includes("pcm")) {
    return createWavBlobFromPcm(bytes, response.sampleRate || 24000);
  }

  return new Blob([bytes], { type: response.mimeType || "audio/mpeg" });
}

function buildSummaryNarration(summary: InterviewSummary) {
  const sections = [
    `本轮模拟面试总分 ${summary.overallScore} 分。`,
    summary.strengths.length ? `高光表现：${summary.strengths.join("；")}。` : "",
    summary.weaknesses.length ? `待提升区：${summary.weaknesses.join("；")}。` : "",
    summary.suggestions.length ? `下一步建议：${summary.suggestions.join("；")}。` : "",
  ].filter(Boolean);
  return sections.join("");
}

function findOverlapLength(previous: string, incoming: string) {
  const maxLength = Math.min(previous.length, incoming.length);
  for (let length = maxLength; length > 0; length -= 1) {
    if (previous.slice(-length) === incoming.slice(0, length)) {
      return length;
    }
  }
  return 0;
}

function shouldTrimLeadingWhitespaceForCjk(previous: string, incoming: string) {
  if (!incoming) {
    return false;
  }
  return (
    /[\u3400-\u9fff\u3000-\u303f\uff00-\uffef]$/u.test(previous) &&
    /^\s+[\u3400-\u9fff\u3000-\u303f\uff00-\uffef]/u.test(incoming)
  );
}

function mergeStreamingText(previous: string, incoming: string) {
  if (!incoming) {
    return previous;
  }
  if (!previous) {
    return incoming;
  }
  if (shouldTrimLeadingWhitespaceForCjk(previous, incoming)) {
    incoming = incoming.trimStart();
  }
  if (incoming === previous) {
    return previous;
  }
  if (incoming.startsWith(previous)) {
    return incoming;
  }
  if (previous.startsWith(incoming)) {
    return previous;
  }

  const overlapLength = findOverlapLength(previous, incoming);
  if (overlapLength > 0) {
    return previous + incoming.slice(overlapLength);
  }

  const needsSpace = /[A-Za-z0-9]$/.test(previous) && /^[A-Za-z0-9]/.test(incoming);
  return needsSpace ? `${previous} ${incoming}` : previous + incoming;
}

function isAudioAnswerModeValue(mode: AnswerMode) {
  return mode === "voice" || mode === "live";
}

function getInterviewModeLabel(mode: AnswerMode) {
  return mode === "text" ? "文字面试" : mode === "voice" ? "语音面试" : "Live 实时面试";
}

function getAnswerModeLabel(mode: AnswerMode) {
  return mode === "text" ? "文字回答" : mode === "voice" ? "语音回答" : "Live 实时语音";
}

function getAnswerModeStatusLabel(mode: AnswerMode) {
  return mode === "text" ? "文字模式" : mode === "voice" ? "语音模式" : "Live 模式";
}

function isAnswerModeEntryEnabled(mode: AnswerMode, entryOptions: InterviewEntryOptionsResponse | null) {
  if (mode === "voice") {
    return entryOptions?.voiceAnswerEnabled ?? false;
  }
  if (mode === "live") {
    return entryOptions?.liveInterviewEnabled ?? false;
  }
  return true;
}

type PrepareSelectionTone = "indigo" | "teal" | "amber" | "slate";

type PrepareSelectionCardProps = {
  title: string;
  value: string;
  detail: string;
  icon: LucideIcon;
  tone?: PrepareSelectionTone;
  isActive: boolean;
  onClick: () => void;
};

function PrepareSelectionCard({
  title,
  value,
  detail,
  icon: TileIcon,
  tone = "slate",
  isActive,
  onClick,
}: PrepareSelectionCardProps) {
  const toneClassName = tone === "indigo"
    ? {
      card: "border-indigo-200 bg-indigo-50/80 shadow-[0_16px_34px_rgba(79,70,229,0.08)]",
      icon: "border-indigo-100 bg-indigo-50 text-indigo-600",
      action: "border-indigo-100 bg-white text-indigo-600",
      value: "border-indigo-200 bg-white text-indigo-700 shadow-[0_10px_24px_rgba(79,70,229,0.10)]",
      }
      : tone === "teal"
      ? {
        card: "border-teal-200 bg-teal-50/80 shadow-[0_16px_34px_rgba(20,184,166,0.08)]",
        icon: "border-teal-100 bg-teal-50 text-teal-600",
        action: "border-teal-100 bg-white text-teal-600",
        value: "border-teal-200 bg-white text-teal-700 shadow-[0_10px_24px_rgba(20,184,166,0.10)]",
        }
        : tone === "amber"
        ? {
          card: "border-amber-200 bg-amber-50/80 shadow-[0_16px_34px_rgba(245,158,11,0.08)]",
          icon: "border-amber-100 bg-amber-50 text-amber-600",
          action: "border-amber-100 bg-white text-amber-600",
          value: "border-amber-200 bg-white text-amber-700 shadow-[0_10px_24px_rgba(245,158,11,0.10)]",
        }
        : {
          card: "border-slate-300 bg-slate-100/90 shadow-[0_16px_34px_rgba(148,163,184,0.10)]",
          icon: "border-slate-200 bg-slate-100 text-slate-600",
          action: "border-slate-200 bg-white text-slate-600",
          value: "border-slate-300 bg-white text-slate-700 shadow-[0_10px_24px_rgba(148,163,184,0.10)]",
        };

  return (
    <button
      type="button"
      onClick={onClick}
      className={joinClasses(
        "group flex h-full min-h-[10.5rem] flex-col overflow-hidden rounded-[1.85rem] border px-4 py-4 text-left transition-[box-shadow,border-color,background-color] duration-200 ease-out",
        isActive ? toneClassName.card : "border-slate-200 bg-slate-50/85 hover:border-slate-300 hover:bg-white",
      )}
    >
      <div className="flex h-full items-center justify-between gap-3">
        <div className="flex min-w-0 flex-1 flex-col items-start gap-2.5">
          <div className="flex min-w-0 items-center gap-3">
            <div className={joinClasses("flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl border", toneClassName.icon)}>
              <TileIcon size={20} />
            </div>
            <div className="min-w-0 text-left">
              <div className="text-base font-bold tracking-tight text-slate-950">{title}</div>
            </div>
          </div>
          <div
            className={joinClasses(
              "inline-flex w-fit max-w-full items-center rounded-full border px-3 py-1.5 text-sm font-semibold",
              toneClassName.value,
            )}
          >
            <span className="truncate">{value}</span>
          </div>
          <div
            className="text-left text-[13px] leading-6 text-slate-500"
            style={{
              display: "-webkit-box",
              WebkitBoxOrient: "vertical",
              WebkitLineClamp: 2,
              overflow: "hidden",
            }}
          >
            {detail}
          </div>
        </div>
        <div
          className={joinClasses(
            "flex h-9 w-9 shrink-0 items-center justify-center rounded-2xl border transition-all",
            isActive ? toneClassName.action : "border-slate-200 bg-white text-slate-400 group-hover:text-slate-600",
          )}
        >
          <ChevronDown size={16} className={joinClasses("transition-transform", isActive ? "rotate-180" : "-rotate-90")} />
        </div>
      </div>
    </button>
  );
}

function createLocalId(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function parseInterviewRouteStage(pathname: string): InterviewStage {
  // 路由路径是 prepare/session/report 三段工作区的唯一入口真相。
  if (pathname.endsWith("/session")) {
    return "session";
  }
  if (pathname.endsWith("/review")) {
    return "report";
  }
  return "prepare";
}

function buildInterviewStageHref(stage: InterviewStage, sessionId?: string | null) {
  if (stage === "prepare") {
    return "/ai/interview/prepare";
  }

  const pathname = stage === "session" ? "/ai/interview/session" : "/ai/interview/review";
  return `${pathname}${buildQuery(sessionId?.trim() ? { sessionId: sessionId.trim() } : {})}`;
}

function isActiveInterviewStatus(status: string | null | undefined) {
  return (status ?? "").trim().toUpperCase() === "ACTIVE";
}

function findLatestContinuableInterview(records: InterviewHistoryItem[]) {
  return records.find((item) => item.sessionId?.trim() && isActiveInterviewStatus(item.status)) ?? null;
}

function buildHydratedChatMessages(
  messages: InterviewSessionMessageItem[],
  summaryReady: boolean,
): ChatMessage[] {
  // 详情恢复时只渲染用户和助手消息，系统日志不进入聊天流。
  const visibleMessages = messages.filter((message) => message.role === "USER" || message.role === "ASSISTANT");

  return visibleMessages.map((message, index) => {
    const isUser = message.role === "USER";
    const isFirstAssistant = !isUser && visibleMessages.slice(0, index).every((item) => item.role !== "ASSISTANT");
    const isLastAssistant = !isUser && visibleMessages.slice(index + 1).every((item) => item.role !== "ASSISTANT");

    return {
      id: createLocalId(isUser ? "history-user" : "history-assistant"),
      role: isUser ? "USER" : "ASSISTANT",
      text: message.text,
      createdAt: message.createdAt,
      badge: isUser
        ? message.audioObjectKey
          ? "语音转写"
          : "我的回答"
        : isFirstAssistant
          ? "开场问题"
          : summaryReady && isLastAssistant
            ? "结束提示"
            : "下一轮追问",
      scoreHint: message.scoreHint,
      audioObjectKey: message.audioObjectKey,
      isStreaming: false,
    };
  });
}

function buildPromptContextRows(promptContext: string) {
  return promptContext
    .split(/\n+/)
    .map((line) => line.trim())
    .filter(Boolean)
    .filter((line) => !line.startsWith("以下是候选人本轮额外授权带入的个人资料"))
    .map((line) => {
      const separatorIndex = line.indexOf("：");
      if (separatorIndex <= 0) {
        return {
          label: "资料摘要",
          value: line,
        };
      }
      return {
        label: line.slice(0, separatorIndex).trim(),
        value: line.slice(separatorIndex + 1).trim(),
      };
    });
}

function normalizeAnswerHelperCueKeys(cueKeys: string[] | null | undefined) {
  if (!cueKeys || cueKeys.length === 0) {
    return [] as AnswerHelperCueKey[];
  }
  return cueKeys.filter((key): key is AnswerHelperCueKey => answerHelperOptions.some((item) => item.key === key));
}

function formatClockTime(value: TimeValue) {
  return formatTime(value, typeof value === "string" ? value : "—");
}

function containsAnyKeyword(text: string, keywords: string[]) {
  if (!text) {
    return false;
  }

  const normalized = text.toLowerCase();
  return keywords.some((keyword) => normalized.includes(keyword.toLowerCase()));
}

function buildAnswerHelperInsights(
  options: Array<{ key: AnswerHelperCueKey; label: string }>,
  referenceText: string,
): AnswerHelperInsight[] {
  const normalized = referenceText.trim();
  if (!normalized) {
    return options.map((item) => ({
      key: item.key,
      label: item.label,
      status: "idle",
      statusLabel: "等待回答",
      detail: item.key === "STAR"
        ? "开始输入后，这里会提醒你是否交代了背景、动作和结果。"
        : item.key === "METRICS"
          ? "开始输入后，这里会检查是否出现了数据、比例或前后对比。"
          : "开始输入后，这里会提示你是否完成了结果收口和表达闭环。",
    }));
  }

  const hasDigits = /(?:\d|[０-９])/.test(normalized);
  const hasSituation = containsAnyKeyword(normalized, ["背景", "场景", "当时", "项目", "需求", "业务", "任务", "在", "负责"]);
  const hasAction = containsAnyKeyword(normalized, ["负责", "设计", "实现", "优化", "重构", "推进", "排查", "协调", "搭建", "上线", "改造"]);
  const hasResult = hasDigits || containsAnyKeyword(normalized, ["结果", "最终", "提升", "降低", "减少", "增加", "缩短", "节省", "优化到", "稳定"]);
  const hasClosing = containsAnyKeyword(normalized, ["复盘", "总结", "反思", "收获", "所以", "因此", "后续", "权衡"]);

  return options.map((item) => {
    if (item.key === "STAR") {
      const missingParts = [
        hasSituation ? null : "背景",
        hasAction ? null : "动作",
        hasResult ? null : "结果",
      ].filter(Boolean) as string[];

      if (missingParts.length === 0) {
        return {
          key: item.key,
          label: item.label,
          status: "ready",
          statusLabel: "结构较完整",
          detail: hasClosing
            ? "已经有背景、动作、结果和收口，结构比较完整。"
            : "背景、动作、结果已出现，结尾再补一句复盘或取舍会更稳。",
        };
      }

      return {
        key: item.key,
        label: item.label,
        status: "warn",
        statusLabel: "待补 STAR",
        detail: `当前还缺少${missingParts.join(" / ")}，建议按“背景 - 动作 - 结果”讲完整。`,
      };
    }

    if (item.key === "METRICS") {
      return hasDigits
        ? {
            key: item.key,
            label: item.label,
            status: "ready",
            statusLabel: "已看到量化",
            detail: "已经出现了数字或量化结果，继续补一组前后对比会更有说服力。",
          }
        : {
            key: item.key,
            label: item.label,
            status: "warn",
            statusLabel: "缺少量化",
            detail: "当前还没有明显的数据支撑，建议补充指标、比例、耗时或结果对比。",
          };
    }

    if (normalized.length >= 90 && hasResult) {
      return {
        key: item.key,
        label: item.label,
        status: "ready",
        statusLabel: "完成度较好",
        detail: hasClosing
          ? "回答已经有主线、结果和收口，完整度比较稳定。"
          : "主线和结果已经比较完整，再补一句复盘或反思会更自然。",
      };
    }

    return {
      key: item.key,
      label: item.label,
      status: "warn",
      statusLabel: normalized.length < 40 ? "内容偏短" : "建议再收口",
      detail: normalized.length < 40
        ? "当前内容偏短，建议至少补齐背景、动作和结果，再发出这一轮。"
        : "建议在结尾补一句结果影响、复盘或下一步，让回答更完整。",
    };
  });
}

function getAnswerHelperCueLabel(key: string) {
  return answerHelperOptions.find((item) => item.key === key)?.label ?? key;
}

function normalizeAnswerHelperAnalysisLevel(level: string | null | undefined) {
  const normalized = (level ?? "").trim().toUpperCase();
  if (normalized === "READY" || normalized === "WARN" || normalized === "INFO") {
    return normalized;
  }
  return "INFO";
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

function formatDurationLabel(durationMs: number) {
  const totalSeconds = Math.max(Math.floor(durationMs / 1000), 0);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;

  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}

function getCurrentDateStamp() {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function truncateText(value: string, maxLength: number) {
  const normalized = value.trim().replace(/\s+/g, " ");

  if (normalized.length <= maxLength) {
    return normalized;
  }

  return `${normalized.slice(0, Math.max(maxLength - 1, 1))}…`;
}

function buildInterviewPromptContextFromPrepMaterials(params: {
  selectedPrepMaterials: PrepMaterialKey[];
  educationBackgroundSummary: string;
  jobStatusText: string;
  academicRecordSummary: string;
  portraitTagLabels: string[];
  portraitEvidenceLines: string[];
  latestResumeSnapshot: ResumePrepSnapshot | null;
  selfIntro: string;
  skillTags: string[];
}) {
  const sections: string[] = [];
  const hasSelected = (materialKey: PrepMaterialKey) => params.selectedPrepMaterials.includes(materialKey);

  // 准备材料按用户授权拼成 prompt context，未选中的个人资料不进入模型。
  if (hasSelected("CAMPUS_BACKGROUND") && params.educationBackgroundSummary.trim()) {
    sections.push(`教育背景：${params.educationBackgroundSummary.trim()}`);
  }
  if (hasSelected("JOB_STATUS") && params.jobStatusText.trim()) {
    sections.push(`当前求职状态：${params.jobStatusText.trim()}`);
  }
  if (hasSelected("ACADEMIC_RECORDS") && params.academicRecordSummary.trim()) {
    sections.push(`学业记录：${params.academicRecordSummary.trim()}`);
  }
  if (hasSelected("GROWTH_PORTRAIT")) {
    const portraitSummary = [
      params.portraitTagLabels.length > 0 ? `成长关键词：${params.portraitTagLabels.slice(0, 8).join(" / ")}` : null,
      params.portraitEvidenceLines.length > 0 ? `近期训练画像：${params.portraitEvidenceLines.slice(0, 3).join("；")}` : null,
    ].filter(Boolean).join("；");
    if (portraitSummary) {
      sections.push(portraitSummary);
    }
  }
  if (hasSelected("LATEST_RESUME") && params.latestResumeSnapshot) {
    const resumeSummary = params.latestResumeSnapshot.summary?.trim()
      || params.latestResumeSnapshot.suggestions[0]?.trim()
      || params.latestResumeSnapshot.targetRole?.trim()
      || "最近一份 AI 简历记录";
    sections.push(`最近一份简历：已授权带入，当前重点摘要为“${truncateText(resumeSummary, 72)}”；更完整的经历内容以后端简历快照为准。`);
  }
  if (hasSelected("SELF_INTRO") && params.selfIntro.trim()) {
    sections.push(`自我介绍：${truncateText(params.selfIntro.trim(), 160)}`);
  }
  if (hasSelected("SKILL_TAGS") && params.skillTags.length > 0) {
    sections.push(`技能关键词：${params.skillTags.slice(0, 10).join(" / ")}`);
  }

  if (sections.length === 0) {
    return "";
  }
  return truncateText(
    [
      "以下是候选人本轮额外授权带入的个人资料，请仅作为提问和总结时的补充参考，不要补造未提供的事实。",
      ...sections,
    ].join("\n"),
    1800,
  );
}

function getPreferredWebmRecordingMimeType() {
  if (typeof MediaRecorder === "undefined") {
    return null;
  }

  const candidates = ["audio/webm;codecs=opus", "audio/webm"];
  return candidates.find((candidate) => MediaRecorder.isTypeSupported(candidate)) ?? null;
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

function buildInterviewErrorMessage(error: unknown) {
  if (!(error instanceof ApiClientError)) {
    return "模拟面试暂时失败，请稍后重试。";
  }

  if (error.code === "MOD-1001") {
    return "当前输入触发了安全策略拦截，请调整回答内容后再试。";
  }

  if (error.code === "MOD-1002") {
    return "AI 输出被安全策略拦截了，这一轮建议换个表达方式再试。";
  }

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

  if (error.code === "AI-2001") {
    return error.message || "AI 服务暂时异常，请稍后重试。";
  }

  if (error.code === "AI-2201") {
    return "当前积分或额度不足，先回工作台补充积分后再继续练习。";
  }

  if (error.code === "AI-2202") {
    const retryAfterSeconds = readApiErrorNumber(error, "retryAfterSeconds");
    return retryAfterSeconds !== null && retryAfterSeconds > 0
      ? `当前请求过快，请 ${retryAfterSeconds} 秒后再试。`
      : "当前请求过快，请稍后再试。";
  }

  if (error.code === "AI-2102") {
    return error.message || "语音转写失败，请重新录音或改用文字模式。";
  }

  if (error.code === "AI-2103") {
    return error.message || "语音播报生成失败，本次将先保留文字内容。";
  }

  if (error.code === "BIZ-1003") {
    return error.message || "当前语音能力暂未开启，请稍后再试。";
  }

  if (error.code === "BIZ-1001" && error.message.includes("round limit")) {
    return "当前会话已经到达安全上限，可以直接手动结束并生成复盘。";
  }

  if (error.code === "BIZ-1001" && error.message.includes("completed")) {
    return "当前会话已经结束，可以直接查看复盘。";
  }

  if (error.code === "BIZ-1001" && error.message.includes("audio file too large")) {
    return "这段录音文件太大了，请缩短录音时长后再试。";
  }

  if (error.code === "BIZ-1001" && error.message.includes("audio file required")) {
    return "这次没有拿到有效音频，请重新录制或重新选择文件。";
  }

  if (error.message.includes("voice feature disabled")) {
    return "当前语音能力暂未开启，请稍后再试。";
  }

  if (error.message.includes("tts failed")) {
    return "语音播报生成失败，本次将先保留文字内容。";
  }

  if (error.message.includes("stt failed")) {
    return "语音转写失败，请重新录音或改用文字模式。";
  }

  return error.message || "模拟面试暂时失败，请稍后重试。";
}

function buildInterviewDisplayName(profile: StudentProfileHint | null, fallbackDisplayName: string | null) {
  return buildStudentNickname(profile, fallbackDisplayName, "同学");
}

function InterviewOverlayFallback() {
  return (
    <div className="fixed inset-0 z-[130] flex items-center justify-center bg-slate-950/24 px-4 py-8 backdrop-blur-sm">
      <div className="rounded-[1.6rem] border border-white/75 bg-white/92 px-6 py-5 shadow-[0_20px_50px_rgba(15,23,42,0.18)]">
        <div className="flex items-center gap-3">
          <div className="h-8 w-8 animate-spin rounded-full border-2 border-slate-200 border-t-teal-600" />
          <div className="text-sm font-semibold text-slate-600">正在加载操作面板...</div>
        </div>
      </div>
    </div>
  );
}

function ScoreRing({ score, compact = false }: { score: number; compact?: boolean }) {
  const safeScore = Math.max(0, Math.min(score, 100));
  const size = compact ? 208 : 220;
  const center = size / 2;
  const radius = compact ? 74 : 78;
  const strokeWidth = compact ? 16 : 18;
  const circumference = 2 * Math.PI * radius;
  const dashOffset = circumference * (1 - safeScore / 100);
  const innerSizeClass = compact ? "h-[8.25rem] w-[8.25rem]" : "h-32 w-32";
  const titleClass = compact ? "text-[11px] tracking-[0.22em]" : "text-[11px] tracking-[0.24em]";
  const scoreClass = compact ? "text-[2.9rem]" : "text-5xl";
  const subtitleClass = compact ? "text-xs" : "text-xs";

  return (
    <div className={joinClasses("relative flex items-center justify-center", compact ? "h-[208px] w-[208px]" : "h-[220px] w-[220px]")}>
      <svg className="h-full w-full -rotate-90 overflow-visible" viewBox={`0 0 ${size} ${size}`}>
        <defs>
          <linearGradient id="interview-score-gradient" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#14b8a6" />
            <stop offset="100%" stopColor="#4f46e5" />
          </linearGradient>
        </defs>
        <circle cx={center} cy={center} r={radius} fill="none" stroke="rgba(148,163,184,0.18)" strokeWidth={strokeWidth} />
        <motion.circle
          cx={center}
          cy={center}
          r={radius}
          fill="none"
          stroke="url(#interview-score-gradient)"
          strokeLinecap="round"
          strokeWidth={strokeWidth}
          strokeDasharray={circumference}
          initial={{ strokeDashoffset: circumference }}
          animate={{ strokeDashoffset: dashOffset }}
          transition={{ duration: 1, ease: "easeOut" }}
        />
      </svg>
      <div className={joinClasses("absolute flex flex-col items-center justify-center rounded-full border border-white/80 bg-white/90 shadow-[0_18px_40px_rgba(79,70,229,0.14)]", innerSizeClass)}>
        <div className={joinClasses("text-slate-400", titleClass)}>整体表现</div>
        <div className={joinClasses("mt-2 font-black text-slate-900", scoreClass)}>{safeScore}</div>
        <div className={joinClasses("mt-1 text-slate-500", subtitleClass)}>本轮综合表现</div>
      </div>
    </div>
  );
}

function AnimatedScoreValue({ value }: { value: number | null }) {
  const previousValueRef = useRef<number | null>(value);
  const [motionState, setMotionState] = useState<{
    direction: -1 | 0 | 1;
    display: string;
  }>({
    direction: 0,
    display: value === null ? "—" : `${value}`,
  });

  useEffect(() => {
    const previousValue = previousValueRef.current;
    const nextDisplay = value === null ? "—" : `${value}`;
    const direction = previousValue === null || value === null || previousValue === value
      ? 0
      : value > previousValue
        ? 1
        : -1;
    setMotionState({
      direction,
      display: nextDisplay,
    });
    previousValueRef.current = value;
  }, [value]);

  return (
    <span className="relative inline-flex h-[1.5rem] min-w-[3ch] items-center justify-center overflow-hidden align-middle tabular-nums">
      <AnimatePresence initial={false} mode="wait">
        <motion.span
          key={motionState.display}
          initial={motionState.direction === 0 ? { opacity: 0.82 } : { y: motionState.direction > 0 ? "100%" : "-100%", opacity: 0 }}
          animate={{ y: "0%", opacity: 1 }}
          exit={motionState.direction === 0 ? { opacity: 0 } : { y: motionState.direction > 0 ? "-100%" : "100%", opacity: 0 }}
          transition={{ duration: motionState.direction === 0 ? 0.18 : 0.28, ease: "easeOut" }}
          className="absolute inset-0 flex items-center justify-center text-[1.35rem] font-semibold leading-none text-slate-900"
        >
          {motionState.display}
        </motion.span>
      </AnimatePresence>
    </span>
  );
}

function SummaryCard({
  section,
}: {
  section: SummarySection;
}) {
  const toneClassName = section.tone === "success"
    ? {
      border: "border-emerald-100 bg-emerald-50/60",
      badge: "bg-emerald-100 text-emerald-700",
      icon: "bg-emerald-100 text-emerald-600",
      title: "text-emerald-800",
    }
    : section.tone === "warning"
      ? {
        border: "border-amber-100 bg-amber-50/60",
        badge: "bg-amber-100 text-amber-700",
        icon: "bg-amber-100 text-amber-600",
        title: "text-amber-800",
      }
      : {
        border: "border-indigo-100 bg-indigo-50/60",
        badge: "bg-indigo-100 text-indigo-700",
        icon: "bg-indigo-100 text-indigo-600",
        title: "text-indigo-800",
      };

  const SectionIcon = section.icon;

  return (
    <div className={joinClasses("flex h-full flex-col rounded-[2rem] border p-6 shadow-sm", toneClassName.border)}>
      <div className="flex min-h-[3.65rem] items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className={joinClasses("flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl", toneClassName.icon)}>
            <SectionIcon size={20} />
          </div>
          <div>
            <h3 className={joinClasses("text-[1.35rem] font-bold leading-tight", toneClassName.title)}>{section.title}</h3>
          </div>
        </div>
        <div className={joinClasses("rounded-full px-3 py-1 text-[12px] font-semibold", toneClassName.badge)}>
          {section.items.length > 0 ? `${section.items.length} 条` : "待生成"}
        </div>
      </div>

      {section.items.length > 0 ? (
        <div className="mt-5 flex flex-1 flex-wrap content-start gap-3">
          {section.items.map((item, index) => (
            <div
              key={`${section.id}-${index}`}
              className="flex min-h-[5.75rem] min-w-[15rem] flex-1 items-start rounded-[1.4rem] border border-white/80 bg-white/80 px-4 py-3.5 text-[15px] leading-7 text-slate-600"
            >
              {item}
            </div>
          ))}
        </div>
      ) : (
        <div className="mt-5 flex flex-1 items-center rounded-[1.4rem] border border-dashed border-slate-200 bg-white/70 px-4 py-4 text-[15px] leading-7 text-slate-500">
          {section.emptyText}
        </div>
      )}
    </div>
  );
}

function InterviewPageSkeleton() {
  return (
    <WorkspacePageLoadingScreen
      title="正在准备模拟面试"
      description="正在加载个人画像、面试配置和练习上下文，请稍候。"
    />
  );
}

export default function InterviewPracticePage() {
  const { role, userId, displayName } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const stage = useMemo(() => parseInterviewRouteStage(location.pathname), [location.pathname]);
  const routeSessionId = useMemo(() => {
    const rawValue = searchParams.get("sessionId");
    if (!rawValue) {
      return null;
    }
    return rawValue.trim() || null;
  }, [searchParams]);
  const [answerMode, setAnswerMode] = useState<AnswerMode>("text");
  const isVoiceAnswerMode = answerMode === "voice";
  const isLiveAnswerMode = answerMode === "live";
  const isAudioAnswerMode = isAudioAnswerModeValue(answerMode);
  const [difficulty, setDifficulty] = useState<DifficultyLevel>("MEDIUM");
  const [interviewType, setInterviewType] = useState<InterviewType>("PROJECT_DEEP_DIVE");
  const [interviewerStyle, setInterviewerStyle] = useState<InterviewerStyle>("STANDARD");
  const [targetRole, setTargetRole] = useState("");
  const [targetJobDescription, setTargetJobDescription] = useState("");
  const [targetCompany, setTargetCompany] = useState("");
  const [answerHelperEnabled, setAnswerHelperEnabled] = useState(true);
  const [selectedAnswerHelperCueKeys, setSelectedAnswerHelperCueKeys] = useState<AnswerHelperCueKey[]>(defaultAnswerHelperCueKeys);
  const [selectedPrepMaterials, setSelectedPrepMaterials] = useState<PrepMaterialKey[]>([]);
  const [activePrepMaterialPreviewKey, setActivePrepMaterialPreviewKey] = useState<ManualPrepMaterialKey | null>(null);
  const [expandedPrepareDrawer, setExpandedPrepareDrawer] = useState<PrepareDrawerKey | null>(null);
  const [isStartConfirmOpen, setIsStartConfirmOpen] = useState(false);
  const [isPrivacyNoticeOpen, setIsPrivacyNoticeOpen] = useState(false);
  const [isContinuableInterviewPromptOpen, setIsContinuableInterviewPromptOpen] = useState(false);
  const [interviewOverlaysLoaded, setInterviewOverlaysLoaded] = useState(false);
  const [profileHint, setProfileHint] = useState<StudentProfileHint | null>(null);
  const [profileLoading, setProfileLoading] = useState(role === "STUDENT");
  const [profileError, setProfileError] = useState<string | null>(null);
  const [interviewEntryOptions, setInterviewEntryOptions] = useState<InterviewEntryOptionsResponse | null>(null);
  const [interviewEntryOptionsLoading, setInterviewEntryOptionsLoading] = useState(role === "STUDENT");
  const [latestResumeSnapshot, setLatestResumeSnapshot] = useState<ResumePrepSnapshot | null>(null);
  const [continuableInterviewRecord, setContinuableInterviewRecord] = useState<InterviewHistoryItem | null>(null);
  const [restoredResumeSnapshot, setRestoredResumeSnapshot] = useState<ResumePrepSnapshot | null>(null);
  const [restoredPromptContext, setRestoredPromptContext] = useState<string | null>(null);
  const [restoredPrepMaterialKeys, setRestoredPrepMaterialKeys] = useState<PrepMaterialKey[] | null>(null);
  const [quoteIndex, setQuoteIndex] = useState(0);
  const [micStatus, setMicStatus] = useState<MicStatus>("idle");
  const [micMessage, setMicMessage] = useState("语音模式支持浏览器录音开始，也可以在需要时补充音频文件；正式开始前，先做一次麦克风检测会更稳妥。");
  const [liveConnectionStatus, setLiveConnectionStatus] = useState("未连接");
  const [liveMicStatus, setLiveMicStatus] = useState("未授权");
  const [livePlaybackStatus, setLivePlaybackStatus] = useState("空闲");
  const [liveTokenCount, setLiveTokenCount] = useState(0);
  const [liveUserCaption, setLiveUserCaption] = useState("尚无输入字幕");
  const [liveModelCaption, setLiveModelCaption] = useState("尚无输出字幕");
  const [liveLogEntries, setLiveLogEntries] = useState<LiveLogEntry[]>([]);
  const [liveFinishPending, setLiveFinishPending] = useState(false);
  const [liveReconnectNonce, setLiveReconnectNonce] = useState(0);
  const [routeSessionHydrating, setRouteSessionHydrating] = useState(false);
  const [sessionContextSource, setSessionContextSource] = useState<"local" | "detail">("local");
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [sessionCreatedAt, setSessionCreatedAt] = useState<TimeValue | null>(null);
  const [replyRoundLimit, setReplyRoundLimit] = useState(0);
  const [replyRoundUsed, setReplyRoundUsed] = useState(0);
  const [chargedPoints, setChargedPoints] = useState<number | null>(null);
  const [pointsBalance, setPointsBalance] = useState<number | null>(null);
  const [quotaUnitsReserved, setQuotaUnitsReserved] = useState<number | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [draftAnswer, setDraftAnswer] = useState("");
  const [summary, setSummary] = useState<InterviewSummary | null>(null);
  const [latestCoachFeedback, setLatestCoachFeedback] = useState<string | null>(null);
  const [latestScoreHint, setLatestScoreHint] = useState<number | null>(null);
  const [latestAnswerHelperAnalysis, setLatestAnswerHelperAnalysis] = useState<InterviewAnswerHelperAnalysis | null>(null);
  const [latestFeedbackIndex, setLatestFeedbackIndex] = useState(0);
  const [latestFeedbackRotationNonce, setLatestFeedbackRotationNonce] = useState(0);
  const [answerHelperAnalysisPending, setAnswerHelperAnalysisPending] = useState(false);
  const [answerHelperAnalysisError, setAnswerHelperAnalysisError] = useState<string | null>(null);
  const [statusNote, setStatusNote] = useState<string | null>(null);
  const [, setErrorMessage] = useState<string | null>(null);
  const [toast, setToast] = useState<InterviewToastState | null>(null);
  const [sessionPending, setSessionPending] = useState(false);
  const [summaryPending, setSummaryPending] = useState(false);
  const [abandonPending, setAbandonPending] = useState(false);
  const [voiceFile, setVoiceFile] = useState<File | null>(null);
  const [isRecording, setIsRecording] = useState(false);
  const [recordingElapsedMs, setRecordingElapsedMs] = useState(0);
  const [recordingMimeType, setRecordingMimeType] = useState<string>("audio/webm");
  const [ttsPendingKey, setTtsPendingKey] = useState<string | null>(null);
  const [ttsPlayingKey, setTtsPlayingKey] = useState<string | null>(null);
  const messagesEndRef = useRef<HTMLDivElement | null>(null);
  const voiceFileInputRef = useRef<HTMLInputElement | null>(null);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const recordingStreamRef = useRef<MediaStream | null>(null);
  const recordingChunksRef = useRef<Blob[]>([]);
  const recordingStartedAtRef = useRef<number | null>(null);
  const recordingStopModeRef = useRef<"upload" | "save" | "discard">("save");
  const practiceGenerationRef = useRef(0);
  const answerHelperAnalysisSourceKeyRef = useRef<string | null>(null);
  const prepMaterialsInitializedRef = useRef(false);
  const privacyNoticeInitializedRef = useRef(false);
  const ttsAudioRef = useRef<HTMLAudioElement | null>(null);
  const ttsObjectUrlCacheRef = useRef(new Map<string, string>());
  const autoFinishSummaryModalTimeoutRef = useRef<number | null>(null);
  const autoFinishReportNavigationTimeoutRef = useRef<number | null>(null);
  const streamingMessageVisualSettleTimeoutRef = useRef<number | null>(null);
  const liveClientRef = useRef<InterviewLiveClient | null>(null);
  const liveEventHandlerRef = useRef<(event: InterviewLiveEvent) => void>(() => {});
  const livePendingUserMessageIdRef = useRef<string | null>(null);
  const livePendingModelMessageIdRef = useRef<string | null>(null);
  const liveUserStreamBufferRef = useRef("");
  const liveModelStreamBufferRef = useRef("");
  const liveTranscriptRef = useRef<Array<{ role: "USER" | "ASSISTANT"; text: string }>>([]);
  const liveAwaitingFinalTurnRef = useRef(false);
  const messagesRef = useRef<ChatMessage[]>([]);
  const isVoiceAnswerEntryEnabled = role !== "STUDENT" ? true : interviewEntryOptions?.voiceAnswerEnabled ?? false;
  const isLiveInterviewEntryEnabled = role !== "STUDENT" ? true : interviewEntryOptions?.liveInterviewEnabled ?? false;
  const isTextOnlyInterviewEntry = !isVoiceAnswerEntryEnabled && !isLiveInterviewEntryEnabled;
  const shouldShowRouteHydrationSkeleton = stage !== "prepare"
    && (
      interviewEntryOptionsLoading
      || (
        routeSessionHydrating
        && (sessionId !== routeSessionId || messages.length === 0 || (stage === "report" && !summary))
      )
    );

  const showErrorToast = (message: string) => {
    setErrorMessage(message);
    setToast({
      id: Date.now(),
      tone: "error",
      message,
    });
  };

  const isPracticeCurrent = (generation: number) => practiceGenerationRef.current === generation;
  const invalidatePracticeGeneration = () => {
    practiceGenerationRef.current += 1;
  };
  const navigateToInterviewStage = (nextStage: InterviewStage, nextSessionId?: string | null, replace = false) => {
    startTransition(() => {
      navigate(buildInterviewStageHref(nextStage, nextSessionId), { replace });
    });
  };

  const appendLiveLog = (title: string, detail = "") => {
    setLiveLogEntries((current) => [
      {
        id: createLocalId("live-log"),
        title,
        detail,
      },
      ...current,
    ].slice(0, 12));
  };

  const resetLiveTransportState = (options?: {
    preserveTranscript?: boolean;
    preserveLogs?: boolean;
  }) => {
    livePendingUserMessageIdRef.current = null;
    livePendingModelMessageIdRef.current = null;
    liveUserStreamBufferRef.current = "";
    liveModelStreamBufferRef.current = "";
    if (!options?.preserveTranscript) {
      liveTranscriptRef.current = [];
    }
    liveAwaitingFinalTurnRef.current = false;
    setLiveConnectionStatus("未连接");
    setLiveMicStatus("未授权");
    setLivePlaybackStatus("空闲");
    setLiveTokenCount(0);
    setLiveUserCaption("尚无输入字幕");
    setLiveModelCaption("尚无输出字幕");
    if (!options?.preserveLogs) {
      setLiveLogEntries([]);
    }
    setLiveFinishPending(false);
  };

  const closeLiveClient = (options?: {
    preserveTranscript?: boolean;
    preserveLogs?: boolean;
  }) => {
    if (liveClientRef.current) {
      liveClientRef.current.close();
      liveClientRef.current = null;
    }
    resetLiveTransportState(options);
  };

  const stopTtsPlayback = () => {
    if (ttsAudioRef.current) {
      ttsAudioRef.current.pause();
      ttsAudioRef.current.currentTime = 0;
      ttsAudioRef.current = null;
    }
    setTtsPlayingKey(null);
  };

  const clearStreamingMessageVisualSettleTimeout = () => {
    if (streamingMessageVisualSettleTimeoutRef.current !== null) {
      window.clearTimeout(streamingMessageVisualSettleTimeoutRef.current);
      streamingMessageVisualSettleTimeoutRef.current = null;
    }
  };

  const clearTtsObjectUrlCache = () => {
    ttsObjectUrlCacheRef.current.forEach((url) => {
      window.URL.revokeObjectURL(url);
    });
    ttsObjectUrlCacheRef.current.clear();
  };

  const clearAutoFinishTransitionTimeouts = () => {
    if (autoFinishSummaryModalTimeoutRef.current !== null) {
      window.clearTimeout(autoFinishSummaryModalTimeoutRef.current);
      autoFinishSummaryModalTimeoutRef.current = null;
    }
    if (autoFinishReportNavigationTimeoutRef.current !== null) {
      window.clearTimeout(autoFinishReportNavigationTimeoutRef.current);
      autoFinishReportNavigationTimeoutRef.current = null;
    }
  };

  const scheduleAutoFinishReportTransition = (
    generation: number,
    nextSessionId: string,
    nextSummary: InterviewSummary | null,
  ) => {
    clearAutoFinishTransitionTimeouts();
    if (nextSummary) {
      setSummary(nextSummary);
    }
    setStatusNote("这一轮问答已结束，正在整理本轮复盘，稍后会自动进入报告页。");

    autoFinishSummaryModalTimeoutRef.current = window.setTimeout(() => {
      if (!isPracticeCurrent(generation)) {
        return;
      }
      setSummaryPending(true);
    }, AUTO_FINISH_SUMMARY_MODAL_DELAY_MS);

    autoFinishReportNavigationTimeoutRef.current = window.setTimeout(() => {
      if (!isPracticeCurrent(generation)) {
        return;
      }
      clearAutoFinishTransitionTimeouts();
      if (!nextSummary) {
        void handleGenerateSummary();
        return;
      }
      setSummaryPending(false);
      navigateToInterviewStage("report", nextSessionId);
    }, AUTO_FINISH_REPORT_NAV_DELAY_MS);
  };

  useEffect(() => {
    if (!statusNote?.startsWith("会话已创建。")) {
      return;
    }

    const timeoutId = window.setTimeout(() => {
      setStatusNote((current) => (current === statusNote ? null : current));
    }, 15000);

    return () => {
      window.clearTimeout(timeoutId);
    };
  }, [statusNote]);

  useEffect(() => {
    const timer = window.setInterval(() => {
      setQuoteIndex((current) => (current + 1) % motivationalQuotes.length);
    }, 4000);

    return () => {
      window.clearInterval(timer);
    };
  }, []);

  useEffect(() => {
    setLatestFeedbackIndex(0);
    setLatestFeedbackRotationNonce((current) => current + 1);
  }, [sessionId, latestCoachFeedback, latestScoreHint, latestAnswerHelperAnalysis, answerHelperAnalysisPending, answerHelperAnalysisError, answerHelperEnabled]);

  useEffect(() => {
    if (stage !== "session") {
      return;
    }

    const timer = window.setInterval(() => {
      setLatestFeedbackIndex((current) => (current + 1) % 2);
    }, LATEST_FEEDBACK_ROTATION_MS);

    return () => {
      window.clearInterval(timer);
    };
  }, [stage, latestFeedbackRotationNonce]);

  useEffect(() => () => {
    closeLiveClient();
    stopTtsPlayback();
    clearTtsObjectUrlCache();
    clearAutoFinishTransitionTimeouts();
  }, []);

  useEffect(() => {
    if (role !== "STUDENT") {
      setProfileLoading(false);
      setProfileHint(null);
      setProfileError(null);
      return;
    }

    let active = true;

    const loadProfile = async () => {
      setProfileLoading(true);
      setProfileError(null);

      try {
        // 准备页先取学生资料，目标岗位默认回填但不覆盖用户已输入内容。
        const profile = await apiRequest<StudentProfileHint>("/profiles/students/me");
        if (!active) {
          return;
        }

        setProfileHint(profile);
        if (profile.targetPosition?.trim()) {
          setTargetRole((current) => current.trim() || profile.targetPosition?.trim() || "");
        }
      } catch (requestError) {
        if (!active) {
          return;
        }

        setProfileError(buildInterviewErrorMessage(requestError));
      } finally {
        if (active) {
          setProfileLoading(false);
        }
      }
    };

    void loadProfile();

    return () => {
      active = false;
    };
  }, [role]);

  useEffect(() => {
    if (role !== "STUDENT") {
      setInterviewEntryOptions(null);
      setInterviewEntryOptionsLoading(false);
      return;
    }

    let active = true;

    const loadInterviewEntryOptions = async () => {
      setInterviewEntryOptionsLoading(true);
      try {
        // 后端 entry-options 决定文字/语音/Live 哪些入口可以展示。
        const response = await apiRequest<InterviewEntryOptionsResponse>("/ai/interview/entry-options");
        if (!active) {
          return;
        }
        setInterviewEntryOptions(response);
      } catch {
        if (!active) {
          return;
        }
        setInterviewEntryOptions(null);
      } finally {
        if (active) {
          setInterviewEntryOptionsLoading(false);
        }
      }
    };

    void loadInterviewEntryOptions();

    return () => {
      active = false;
    };
  }, [role]);

  useEffect(() => {
    if (role !== "STUDENT") {
      setLatestResumeSnapshot(null);
      return;
    }

    let active = true;

    const loadLatestResumeSnapshot = async () => {
      try {
        // 最近简历只拉 preview，面试 prompt 使用摘要，不把完整历史详情塞进前端状态。
        const history = await apiRequest<ResumeHistoryResponse>(
          `/ai/history${buildQuery({ page: 1, size: 1, taskType: "RESUME" })}`,
        );

        if (!active) {
          return;
        }

        const latestRecordId = history.records[0]?.id;
        if (!latestRecordId) {
          setLatestResumeSnapshot(null);
          return;
        }

        const preview = await apiRequest<ResumePrepSnapshot>(`/ai/history/resume/${latestRecordId}/preview`);
        if (!active) {
          return;
        }

        setLatestResumeSnapshot({
          ...preview,
          inputMode: preview.inputMode === "pdf" ? "pdf" : "text",
        });
      } catch {
        if (active) {
          setLatestResumeSnapshot(null);
        }
      }
    };

    void loadLatestResumeSnapshot();

    return () => {
      active = false;
    };
  }, [role]);

  useEffect(() => {
    if (role !== "STUDENT" || stage !== "prepare") {
      if (role !== "STUDENT") {
        setContinuableInterviewRecord(null);
      }
      return;
    }

    let active = true;

    const loadContinuableInterview = async () => {
      try {
        // 准备页只提示最近一条 ACTIVE 会话，已完成会话统一从历史/报告入口查看。
        const history = await apiRequest<InterviewHistoryResponse>(
          `/ai/history${buildQuery({ page: 1, size: 10, taskType: "INTERVIEW_TEXT" })}`,
        );

        if (!active) {
          return;
        }

        setContinuableInterviewRecord(findLatestContinuableInterview(history.records));
      } catch {
        if (active) {
          setContinuableInterviewRecord(null);
        }
      }
    };

    void loadContinuableInterview();

    return () => {
      active = false;
    };
  }, [role, stage]);

  useEffect(() => {
    if (role !== "STUDENT" || stage !== "prepare" || interviewEntryOptionsLoading) {
      return;
    }
    if (!isAnswerModeEntryEnabled(answerMode, interviewEntryOptions)) {
      // 权益或运行配置关闭入口时，保守退回文字模式。
      closeLiveClient();
      setAnswerMode("text");
      setStatusNote("当前仅开放文字模式，请直接开始这一轮。");
    }
  }, [answerMode, interviewEntryOptions, interviewEntryOptionsLoading, role, stage]);

  useEffect(() => {
    if (stage !== "prepare") {
      setIsContinuableInterviewPromptOpen(false);
      return;
    }
    setIsContinuableInterviewPromptOpen(Boolean(continuableInterviewRecord?.sessionId));
  }, [continuableInterviewRecord?.sessionId, stage]);

  useEffect(() => {
    if (role !== "STUDENT" || profileLoading || privacyNoticeInitializedRef.current) {
      return;
    }

    privacyNoticeInitializedRef.current = true;

    try {
      const dismissedForToday = window.localStorage.getItem(interviewPrivacyNoticeDailyKey) === getCurrentDateStamp();
      if (!dismissedForToday) {
        setIsPrivacyNoticeOpen(true);
      }
    } catch {
      setIsPrivacyNoticeOpen(true);
    }
  }, [profileLoading, role]);

  useEffect(() => {
    messagesRef.current = messages;
  }, [messages]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, sessionPending]);

  useEffect(() => {
    if (!isRecording) {
      return;
    }

    const timer = window.setInterval(() => {
      if (recordingStartedAtRef.current === null) {
        return;
      }
      setRecordingElapsedMs(Date.now() - recordingStartedAtRef.current);
    }, 200);

    return () => {
      window.clearInterval(timer);
    };
  }, [isRecording]);

  useEffect(() => {
    return () => {
      clearStreamingMessageVisualSettleTimeout();
      recordingStopModeRef.current = "discard";
      if (mediaRecorderRef.current && mediaRecorderRef.current.state !== "inactive") {
        mediaRecorderRef.current.stop();
      }
      recordingStreamRef.current?.getTracks().forEach((track) => track.stop());
    };
  }, []);

  const studentName = useMemo(
    () => buildInterviewDisplayName(profileHint, displayName),
    [displayName, profileHint],
  );
  const skillTags = profileHint?.skillTags ?? [];
  const portraitTagLabels = (profileHint?.portrait?.tags ?? [])
    .map((tag) => tag.label?.trim())
    .filter(Boolean) as string[];
  const portraitEvidence = profileHint?.portrait?.evidence ?? null;
  const educationBackgroundLines = [
    profileHint?.schoolName?.trim() ? `学校：${profileHint.schoolName.trim()}` : null,
    profileHint?.major?.trim() ? `专业：${profileHint.major.trim()}` : null,
    profileHint?.grade?.trim() ? `年级：${profileHint.grade.trim()}` : null,
  ].filter(Boolean) as string[];
  const educationBackgroundSummary = [
    profileHint?.schoolName?.trim(),
    profileHint?.major?.trim(),
    profileHint?.grade?.trim(),
  ].filter(Boolean).join(" / ");
  const jobStatusText = profileHint?.jobStatus?.trim() ?? "";
  const honorLines = (profileHint?.honors ?? "")
    .split(/\n+/)
    .map((item) => item.trim())
    .filter(Boolean);
  const academicRecordLines = [
    profileHint?.gpa?.trim() ? `GPA：${profileHint.gpa.trim()}` : null,
    ...honorLines.slice(0, 2).map((item) => `荣誉：${truncateText(item, 52)}`),
  ].filter(Boolean) as string[];
  const academicRecordSummary = [
    profileHint?.gpa?.trim() ? `GPA ${profileHint.gpa.trim()}` : null,
    honorLines[0] ? truncateText(honorLines[0], 22) : null,
  ].filter(Boolean).join(" / ");
  const topbarSectionLabel = stage === "report"
    ? "Interview Review"
    : stage === "session"
      ? "Interview Session"
      : "Interview Prep";
  const topbarTitle = stage === "report"
    ? "面试复盘"
    : stage === "session"
      ? "模拟面试进行中"
      : "模拟面试准备";
  const portraitEvidenceLines = [
    (portraitEvidence?.masteredSkills ?? 0) > 0 ? `已掌握技能 ${portraitEvidence?.masteredSkills ?? 0} 项` : null,
    (portraitEvidence?.learningSkills ?? 0) > 0 ? `学习中技能 ${portraitEvidence?.learningSkills ?? 0} 项` : null,
    (portraitEvidence?.interviewMessages7d ?? 0) > 0 ? `近 7 天面试练习 ${portraitEvidence?.interviewMessages7d ?? 0} 次` : null,
  ].filter(Boolean) as string[];
  const selectedInterviewType = interviewTypeOptions.find((item) => item.value === interviewType) ?? interviewTypeOptions[0];
  const selectedInterviewerStyle = interviewerStyleOptions.find((item) => item.value === interviewerStyle) ?? interviewerStyleOptions[1];
  const selectedDifficulty = difficultyOptions.find((item) => item.value === difficulty) ?? difficultyOptions[1];
  const isDetailHydratedSession = sessionContextSource === "detail";
  const effectiveResumeSnapshot = isDetailHydratedSession && restoredResumeSnapshot ? restoredResumeSnapshot : latestResumeSnapshot;
  // 直接访问 session/report 时优先使用后端详情恢复出的快照，避免被最新简历覆盖。
  const restoredPrepMaterialKeySet = new Set(restoredPrepMaterialKeys ?? []);
  const restoredResumeSummaryLine = restoredResumeSnapshot
    ? restoredResumeSnapshot.summary?.trim()
      ? truncateText(restoredResumeSnapshot.summary, 34)
      : restoredResumeSnapshot.pdfFileName?.trim()
        ? restoredResumeSnapshot.pdfFileName.trim()
        : restoredResumeSnapshot.targetRole?.trim()
          ? `面向 ${restoredResumeSnapshot.targetRole.trim()} 的简历记录`
          : "最近一份简历记录"
    : "";
  const restoredPromptContextRows = isDetailHydratedSession && restoredPromptContext
    ? buildPromptContextRows(restoredPromptContext).map((item) => {
        if ((item.label === "最近一份简历" || item.label === "简历记录") && restoredResumeSummaryLine) {
          return {
            label: "简历记录",
            value: restoredResumeSummaryLine,
          };
        }
        return {
          label: item.label,
          value: truncateText(item.value, 42),
        };
      })
    : [];
  const latestResumeSummary = effectiveResumeSnapshot
    ? effectiveResumeSnapshot.summary?.trim()
      ? truncateText(effectiveResumeSnapshot.summary, 28)
      : effectiveResumeSnapshot.inputMode === "pdf"
        ? effectiveResumeSnapshot.pdfFileName?.trim() || "最近一次 PDF 简历"
        : effectiveResumeSnapshot.targetRole?.trim()
          ? `面向 ${effectiveResumeSnapshot.targetRole.trim()} 的简历记录`
          : "最近一份简历记录"
    : "";
  const latestResumeBullets = effectiveResumeSnapshot
    ? [
        effectiveResumeSnapshot.targetRole?.trim() ? `目标岗位：${effectiveResumeSnapshot.targetRole.trim()}` : null,
        effectiveResumeSnapshot.targetContext?.trim() ? `求职语境：${effectiveResumeSnapshot.targetContext.trim()}` : null,
        effectiveResumeSnapshot.inputMode === "pdf" && effectiveResumeSnapshot.pdfFileName?.trim()
          ? `文件：${effectiveResumeSnapshot.pdfFileName.trim()}`
          : null,
        effectiveResumeSnapshot.suggestions[0]?.trim() ? `优化重点：${truncateText(effectiveResumeSnapshot.suggestions[0], 52)}` : null,
        effectiveResumeSnapshot.createdAt ? `更新于：${formatDateTime(effectiveResumeSnapshot.createdAt)}` : null,
      ].filter(Boolean) as string[]
    : [];
  const latestResumeChips = effectiveResumeSnapshot
    ? [
        effectiveResumeSnapshot.scoreLabel?.trim() ? `推荐度 ${effectiveResumeSnapshot.scoreLabel.trim()}` : null,
        effectiveResumeSnapshot.targetContext?.trim() || null,
        effectiveResumeSnapshot.jobDescription?.trim() ? "含岗位要求" : null,
      ].filter(Boolean) as string[]
    : [];
  const progressPercent = replyRoundLimit > 0 ? Math.min(Math.round((replyRoundUsed / replyRoundLimit) * 100), 100) : 0;
  const sessionTitle = `${targetCompany.trim() ? `${targetCompany.trim()} · ` : ""}${targetRole.trim() || "模拟面试"}`;
  const textMessageCount = messages.filter((message) => message.role === "USER").length;
  const preferredRecordingMimeType = useMemo(() => getPreferredWebmRecordingMimeType(), []);
  const prepMaterialOptions: Array<{
    key: PrepMaterialKey;
    label: string;
    detail: string;
    available: boolean;
  }> = [
    {
      key: "CAMPUS_BACKGROUND",
      label: "教育背景",
      detail: educationBackgroundSummary || "学校 / 专业 / 年级还未补齐",
      available: educationBackgroundLines.length > 0,
    },
    {
      key: "JOB_STATUS",
      label: "求职状态",
      detail: jobStatusText || "当前求职状态还未填写",
      available: Boolean(jobStatusText),
    },
    {
      key: "ACADEMIC_RECORDS",
      label: "GPA 与荣誉",
      detail: academicRecordSummary || "GPA / 荣誉还未补充",
      available: academicRecordLines.length > 0,
    },
    {
      key: "GROWTH_PORTRAIT",
      label: "成长画像标签",
      detail: portraitTagLabels.length > 0
        ? `已整理 ${portraitTagLabels.length} 个成长关键词`
        : portraitEvidenceLines[0] ?? "成长画像还未生成",
      available: portraitTagLabels.length > 0 || portraitEvidenceLines.length > 0,
    },
    {
      key: "LATEST_RESUME",
      label: "最近一份简历",
      detail: latestResumeSummary || "还没有可带入的 AI 简历记录",
      available: Boolean(effectiveResumeSnapshot),
    },
    {
      key: "SELF_INTRO",
      label: "自我介绍",
      detail: profileHint?.selfIntro?.trim() ? truncateText(profileHint.selfIntro, 30) : "资料页里暂未填写自我介绍",
      available: Boolean(profileHint?.selfIntro?.trim()),
    },
    {
      key: "SKILL_TAGS",
      label: "技能关键词",
      detail: skillTags.length > 0 ? `已准备 ${skillTags.length} 个技能关键词` : "当前还没有可带入的技能关键词",
      available: skillTags.length > 0,
    },
    {
      key: "TARGET_COMPANY",
      label: "目标企业上下文",
      detail: targetCompany.trim() ? `会带入“${targetCompany.trim()}”这一练习上下文` : "填写目标企业后可带入当前上下文",
      available: Boolean(targetCompany.trim()),
    },
    {
      key: "TARGET_JD",
      label: "岗位要求",
      detail: targetJobDescription.trim() ? "会按填写的岗位要求做更有针对性的追问预设" : "填写岗位要求后，可把岗位要求一起带入这一轮",
      available: Boolean(targetJobDescription.trim()),
    },
  ];
  const autoPrepMaterialKeys: PrepMaterialKey[] = ["TARGET_COMPANY", "TARGET_JD"];
  const manualPrepMaterialOptions = prepMaterialOptions.filter(
    (item): item is (typeof prepMaterialOptions)[number] & { key: ManualPrepMaterialKey } => !autoPrepMaterialKeys.includes(item.key),
  );
  const autoEnabledPrepMaterials = isDetailHydratedSession
    ? prepMaterialOptions.filter((item) => autoPrepMaterialKeys.includes(item.key) && restoredPrepMaterialKeySet.has(item.key))
    : prepMaterialOptions.filter((item) => item.available && autoPrepMaterialKeys.includes(item.key));
  const selectedOptionalPrepMaterials = isDetailHydratedSession
    ? manualPrepMaterialOptions.filter((item) => restoredPrepMaterialKeySet.has(item.key))
    : manualPrepMaterialOptions.filter((item) => item.available && selectedPrepMaterials.includes(item.key));
  const enabledPrepMaterials = [...autoEnabledPrepMaterials, ...selectedOptionalPrepMaterials];
  const enabledAnswerHelperOptions = answerHelperOptions.filter((item) => selectedAnswerHelperCueKeys.includes(item.key));
  const enabledAnswerHelperCueKeysKey = enabledAnswerHelperOptions.map((item) => item.key).join(",");
  const answerHelperSummaryText = enabledAnswerHelperOptions.map((item) => item.label).join(" / ");
  const prepContextCount = (targetRole.trim() ? 1 : 0) + enabledPrepMaterials.length;
  const latestUserMessage = [...messages].reverse().find((message) => message.role === "USER") ?? null;
  const answerHelperReferenceText = draftAnswer.trim() || latestUserMessage?.text.trim() || "";
  const answerHelperReferenceLabel = draftAnswer.trim()
    ? "当前输入草稿"
    : latestUserMessage
      ? "最近一轮已发送回答"
      : "尚未开始作答";
  const answerHelperInsights = useMemo(
    () => buildAnswerHelperInsights(enabledAnswerHelperOptions, answerHelperReferenceText),
    [enabledAnswerHelperOptions, answerHelperReferenceText],
  );
  const recommendedInputPath = isLiveAnswerMode
    ? "实时麦克风优先"
    : isVoiceAnswerMode
      ? preferredRecordingMimeType
        ? "浏览器录音优先"
        : "手动上传音频优先"
      : "文字回答最稳妥";
  const headsetSuggestion = isAudioAnswerMode ? "建议佩戴耳机减少回声" : "文字模式无需麦克风也可开始";
  const recommendedPrepMaterialKeys = (
    interviewType === "PROJECT_DEEP_DIVE"
      ? (["SKILL_TAGS", "SELF_INTRO", "LATEST_RESUME", "GROWTH_PORTRAIT"] as ManualPrepMaterialKey[])
      : interviewType === "FUNDAMENTALS"
        ? (["CAMPUS_BACKGROUND", "SKILL_TAGS", "LATEST_RESUME"] as ManualPrepMaterialKey[])
        : interviewType === "BEHAVIORAL"
          ? (["SELF_INTRO", "JOB_STATUS", "LATEST_RESUME"] as ManualPrepMaterialKey[])
          : (["CAMPUS_BACKGROUND", "SKILL_TAGS", "SELF_INTRO", "LATEST_RESUME"] as ManualPrepMaterialKey[])
  ).filter((key) => manualPrepMaterialOptions.some((item) => item.key === key && item.available));
  const recommendedAnswerHelperCueKeys = (
    interviewType === "PROJECT_DEEP_DIVE" || interviewType === "PRESSURE"
      ? ["METRICS", "COMPLETENESS"]
      : interviewType === "FUNDAMENTALS"
        ? ["COMPLETENESS"]
        : ["STAR", "COMPLETENESS"]
  ) as AnswerHelperCueKey[];
  const prepMaterialPreviewByKey: Record<ManualPrepMaterialKey, {
    summary: string;
    emptyText: string;
    bullets?: string[];
    chips?: string[];
  }> = {
    CAMPUS_BACKGROUND: {
      summary: educationBackgroundSummary || "资料中心里还没补齐学校 / 专业 / 年级。",
      emptyText: "资料中心里还没补齐学校 / 专业 / 年级。",
      bullets: educationBackgroundLines,
    },
    JOB_STATUS: {
      summary: jobStatusText || "资料中心里还没填写当前求职状态。",
      emptyText: "资料中心里还没填写当前求职状态。",
    },
    ACADEMIC_RECORDS: {
      summary: academicRecordSummary || "资料中心里还没补 GPA 或荣誉信息。",
      emptyText: "资料中心里还没补 GPA 或荣誉信息。",
      bullets: academicRecordLines,
    },
    GROWTH_PORTRAIT: {
      summary: portraitTagLabels.length > 0
        ? `已生成 ${portraitTagLabels.length} 个成长关键词`
        : portraitEvidenceLines[0] ?? "成长画像还在生成中。",
      emptyText: "成长画像还在生成中。",
      bullets: portraitEvidenceLines,
      chips: portraitTagLabels.slice(0, 8),
    },
    LATEST_RESUME: {
      summary: effectiveResumeSnapshot
        ? effectiveResumeSnapshot.summary?.trim()
          ? truncateText(effectiveResumeSnapshot.summary, 220)
          : effectiveResumeSnapshot.suggestions[0]?.trim()
            ? truncateText(effectiveResumeSnapshot.suggestions[0], 220)
            : `最近一次带入的是 ${effectiveResumeSnapshot.pdfFileName?.trim() || "AI 简历记录"}。`
        : "还没有可带入的 AI 简历记录。",
      emptyText: "还没有可带入的 AI 简历记录。",
      bullets: latestResumeBullets,
      chips: latestResumeChips,
    },
    SELF_INTRO: {
      summary: profileHint?.selfIntro?.trim()
        ? truncateText(profileHint.selfIntro, 220)
        : "资料中心暂未填写自我介绍。",
      emptyText: "资料中心暂未填写自我介绍。",
    },
    SKILL_TAGS: {
      summary: skillTags.length > 0 ? `共整理 ${skillTags.length} 个技能关键词` : "当前还没有技能关键词可带入。",
      emptyText: "当前还没有技能关键词可带入。",
      chips: skillTags.slice(0, 10),
    },
  };
  const prepMaterialGroups: Array<{
    title: string;
    keys: ManualPrepMaterialKey[];
  }> = [
    {
      title: "背景资料",
      keys: ["CAMPUS_BACKGROUND", "JOB_STATUS", "ACADEMIC_RECORDS"],
    },
    {
      title: "表达素材",
      keys: ["SELF_INTRO", "SKILL_TAGS", "LATEST_RESUME"],
    },
    {
      title: "成长画像",
      keys: ["GROWTH_PORTRAIT"],
    },
  ];
  const prepMaterialMissingCount = manualPrepMaterialOptions.filter((item) => !item.available).length;
  const optionalAvailablePrepMaterialCount = manualPrepMaterialOptions.filter((item) => item.available).length;
  const activePrepMaterialOption = manualPrepMaterialOptions.find((item) => item.key === activePrepMaterialPreviewKey)
    ?? manualPrepMaterialOptions.find((item) => item.available)
    ?? manualPrepMaterialOptions[0]
    ?? null;
  const resolvedActivePrepMaterialPreviewKey = activePrepMaterialOption?.key ?? null;
  const activePrepMaterialPreviewData = activePrepMaterialOption
    ? prepMaterialPreviewByKey[activePrepMaterialOption.key]
    : null;
  const activePrepMaterialPreviewText = !activePrepMaterialOption
    ? "左侧选择一项资料后，这里会显示简要内容。"
    : activePrepMaterialOption.available
      ? activePrepMaterialPreviewData?.summary ?? ""
      : activePrepMaterialPreviewData?.emptyText ?? "这项资料还没有准备好。";
  const activePrepMaterialPreviewBullets = activePrepMaterialOption?.available
    ? activePrepMaterialPreviewData?.bullets
    : undefined;
  const activePrepMaterialPreviewChips = activePrepMaterialOption?.available
    ? activePrepMaterialPreviewData?.chips
    : undefined;
  const prepChecklistItems = [
    {
      label: "目标岗位已明确",
      ready: Boolean(targetRole.trim()),
      detail: targetRole.trim() ? `当前岗位：${targetRole.trim()}` : "建议先填一个明确岗位，再开始本轮练习。",
    },
    {
      label: "训练资料已整理",
      ready: enabledPrepMaterials.length > 0,
      detail: enabledPrepMaterials.length > 0 ? `当前已带入 ${enabledPrepMaterials.length} 项补充资料。` : "目前除目标岗位外，还没有其他资料被自动带入或手动选中。",
    },
    {
      label: "作答方式已确认",
      ready: true,
      detail: answerMode === "text"
        ? isTextOnlyInterviewEntry
          ? "当前仅开放文字模式，可以直接开始这一轮。"
          : "当前按文字模式进入，适合先把结构练顺。"
        : answerMode === "voice"
          ? "当前按语音模式进入，建议开始前做一次设备检查。"
          : "当前按 Live 实时语音进入，建议先完成设备检查并保持网络稳定。",
    },
    {
      label: "设备路径已确认",
      ready: answerMode === "text" ? true : micStatus !== "idle",
      detail: answerMode === "text"
        ? "文字模式不依赖麦克风，可以直接开始。"
        : micStatus === "ready"
          ? isLiveAnswerMode
            ? "Live 麦克风权限与 Web Audio 能力都已就绪。"
            : "浏览器录音权限与格式都已就绪。"
          : micStatus === "blocked" || micStatus === "unsupported"
            ? "已完成设备确认，本轮可按提示改走浏览器调整或手动音频上传。"
            : "语音模式下建议至少先做一次设备检测，再进入最终确认。",
    },
  ];
  const prepReadyCount = prepChecklistItems.filter((item) => item.ready).length;
  const prepReadinessPercent = Math.round((prepReadyCount / prepChecklistItems.length) * 100);
  const prepStartSuggestion = isLiveAnswerMode
    ? micStatus === "ready"
      ? "设备已经准备好，会话创建后会自动连接 Gemini Live 并进入实时语音面试。"
      : "建议先完成一次麦克风检测；如果当前环境不稳定，也可以先用文字模式开始这一轮。"
    : isVoiceAnswerMode
      ? micStatus === "ready"
        ? "设备已经准备好，可以直接用语音开始这一轮；如果你想先稳一稳，也可以随时切回文字模式。"
        : preferredRecordingMimeType
          ? "建议先点一次麦克风检测；如果你想先进入状态，也可以先用文字模式开始这一轮。"
          : "当前浏览器不太适合直接语音作答，建议先用文字模式开始；需要语音练习时，可稍后补充音频文件。"
      : isTextOnlyInterviewEntry
        ? "当前仅开放文字模式，适合先把开场、自我介绍和项目主线讲顺。"
        : "当前建议先用文字模式把开场和结构讲顺，完成一轮后再逐步加大练习强度。";
  const deviceStatusLabel = micStatus === "ready"
    ? "设备已就绪"
    : micStatus === "blocked"
      ? "权限未开启"
      : micStatus === "unsupported"
        ? "当前更适合文字模式"
        : micStatus === "checking"
          ? "正在检测中"
          : "待检测";
  const deviceStatusHeadline = micStatus === "ready"
    ? "可以直接开始语音作答"
    : micStatus === "checking"
      ? "正在确认你的麦克风状态"
      : micStatus === "blocked"
        ? "先打开权限，再决定是否用语音开练"
        : micStatus === "unsupported"
          ? "当前浏览器不适合直接录音"
          : "开始前先做一次麦克风检测";
  const deviceStatusSummary = micStatus === "ready"
    ? isLiveAnswerMode
      ? "浏览器已经准备好，你可以直接进入 Live 实时语音面试。"
      : "浏览器已经准备好，你可以直接用语音开始这一轮练习。"
    : micStatus === "checking"
      ? "正在帮你确认浏览器里的麦克风权限与录音状态，请稍等片刻。"
      : micStatus === "blocked"
        ? "暂时还没有获得麦克风权限。你可以先处理浏览器授权，或切到文字模式先把这一轮练起来。"
        : micStatus === "unsupported"
          ? isLiveAnswerMode
            ? "当前浏览器不适合直接进入 Live 实时语音，建议先用文字模式开始。"
            : "当前浏览器不适合直接录音，建议先用文字模式开始；需要语音练习时，也可以稍后补充音频文件。"
          : "语音模式下建议先做一次麦克风检测，确认状态后再进入这一轮，会更安心。";
  const practiceBlueprintSteps = [
    {
      title: "整理语境",
      detail: targetRole.trim() ? `围绕“${targetRole.trim()}”进入本轮训练。` : "先用目标岗位把这轮面试语境定下来。",
    },
    {
      title: "针对性追问",
      detail: targetJobDescription.trim() ? "会把岗位要求作为追问方向参考，让问题更贴近真实岗位要求。" : "补一段岗位要求，可以让问题更贴近真实岗位要求。",
    },
    {
      title: "会后复盘",
      detail: "结束后会回到复盘页查看亮点、问题点和下一步建议。",
    },
  ];
  const selectedPrepMaterialLabels = [
    targetRole.trim() ? "目标岗位" : null,
    ...autoEnabledPrepMaterials.map((item) => item.label),
    ...selectedOptionalPrepMaterials.map((item) => item.label),
  ].filter(Boolean) as string[];
  const autoSyncedPrepContextLabels = [
    targetRole.trim() ? "目标岗位" : null,
    ...autoEnabledPrepMaterials.map((item) => item.label),
  ].filter(Boolean) as string[];
  const optionalPrepMaterialLabels = selectedOptionalPrepMaterials.map((item) => item.label);
  const sessionStatusCards = [
    {
      label: "当前模式",
      value: getInterviewModeLabel(answerMode),
      detail: isDetailHydratedSession
        ? "当前会话由详情接口恢复，作答模式已同步"
        : answerMode === "text"
          ? "适合先把表达结构说完整"
          : answerMode === "voice"
            ? "当前按语音作答链路推进"
            : "当前按 Gemini Live 实时语音链路推进",
      icon: answerMode === "text" ? MessageSquare : Mic,
      cardClassName: "border-teal-100 bg-[linear-gradient(135deg,rgba(240,253,250,0.98),rgba(255,255,255,0.98))]",
      iconClassName: "border-teal-100 bg-white text-teal-600",
    },
    {
      label: "练习强度",
      value: selectedDifficulty.label,
      detail: `${selectedInterviewType.label} · ${selectedInterviewerStyle.label}`,
      icon: Gauge,
      cardClassName: "border-indigo-100 bg-[linear-gradient(135deg,rgba(238,242,255,0.98),rgba(255,255,255,0.98))]",
      iconClassName: "border-indigo-100 bg-white text-indigo-600",
    },
    {
      label: "预扣积分",
      value: chargedPoints ?? "—",
      detail: "创建会话时已完成本轮预留",
      icon: Sparkles,
      cardClassName: "border-amber-100 bg-[linear-gradient(135deg,rgba(255,251,235,0.98),rgba(255,255,255,0.98))]",
      iconClassName: "border-amber-100 bg-white text-amber-600",
    },
    {
      label: "剩余积分",
      value: pointsBalance ?? "—",
      detail: "便于决定是否继续再练一轮",
      icon: Wallet,
      cardClassName: "border-sky-100 bg-[linear-gradient(135deg,rgba(240,249,255,0.98),rgba(255,255,255,0.98))]",
      iconClassName: "border-sky-100 bg-white text-sky-600",
    },
  ];
  const carriedContextChips = [
    targetRole.trim() ? `目标岗位 · ${targetRole.trim()}` : null,
    targetCompany.trim() ? `目标企业 · ${targetCompany.trim()}` : null,
    targetJobDescription.trim() ? "岗位要求已带入" : null,
  ].filter(Boolean) as string[];
  const localCarriedSupplementRows = [
    selectedPrepMaterials.includes("CAMPUS_BACKGROUND") && educationBackgroundSummary
      ? { label: "教育背景", value: educationBackgroundSummary }
      : null,
    selectedPrepMaterials.includes("JOB_STATUS") && jobStatusText
      ? { label: "求职状态", value: truncateText(jobStatusText, 28) }
      : null,
    selectedPrepMaterials.includes("ACADEMIC_RECORDS") && academicRecordSummary
      ? { label: "成绩荣誉", value: truncateText(academicRecordSummary, 28) }
      : null,
    selectedPrepMaterials.includes("SELF_INTRO") && profileHint?.selfIntro?.trim()
      ? { label: "自我介绍", value: truncateText(profileHint.selfIntro, 34) }
      : null,
    selectedPrepMaterials.includes("LATEST_RESUME") && latestResumeSummary
      ? { label: "简历记录", value: truncateText(latestResumeSummary, 28) }
      : null,
    selectedPrepMaterials.includes("GROWTH_PORTRAIT") && portraitTagLabels.length > 0
      ? { label: "画像标签", value: truncateText(portraitTagLabels.slice(0, 4).join(" / "), 34) }
      : null,
    selectedPrepMaterials.includes("SKILL_TAGS") && skillTags.length > 0
      ? { label: "技能关键词", value: truncateText(skillTags.slice(0, 6).join(" / "), 34) }
      : null,
  ].filter(Boolean) as Array<{ label: string; value: string }>;
  const detailCarriedSupplementRows = restoredPromptContextRows.length > 0
    ? restoredPromptContextRows
    : restoredResumeSummaryLine && restoredPrepMaterialKeySet.has("LATEST_RESUME")
      ? [{ label: "简历记录", value: restoredResumeSummaryLine }]
      : [];
  const carriedSupplementRows = isDetailHydratedSession ? detailCarriedSupplementRows : localCarriedSupplementRows;
  const autoSyncedPrepContextSummary = autoSyncedPrepContextLabels.length > 0
    ? autoSyncedPrepContextLabels.join(" / ")
    : "目标岗位、目标企业和岗位要求会在填写后自动进入本轮语境。";
  const optionalPrepMaterialSummary = optionalPrepMaterialLabels.length > 0
    ? optionalPrepMaterialLabels.join(" / ")
    : "当前没有额外勾选个人资料，会以岗位语境为主开始这一轮。";
  const prepMaterialPrimaryLabel = selectedPrepMaterialLabels.find((item) => item !== "目标岗位")
    ?? selectedPrepMaterialLabels[0]
    ?? (targetRole.trim() ? "目标岗位" : "待补目标岗位");
  const prepMaterialTileDetail = selectedPrepMaterialLabels.length > 0
    ? `当前共带入 ${selectedPrepMaterialLabels.length} 项资料`
    : "补充岗位、岗位要求或画像资料后会显示在这里";
  const answerHelperPrimaryLabel = answerHelperEnabled
    ? enabledAnswerHelperOptions[0]?.label ?? "轻提醒已开启"
    : "暂不启用";
  const answerHelperTileDetail = answerHelperEnabled
    ? `当前共启用 ${enabledAnswerHelperOptions.length} 项轻提醒`
    : "关闭后会以更干净的问答体验开始";
  const answerModePrimaryLabel = getAnswerModeLabel(answerMode);
  const answerModeTileDetail = answerMode === "text"
    ? isTextOnlyInterviewEntry
      ? "当前仅开放文字模式"
      : "最稳妥，适合先把结构讲清楚"
    : answerMode === "voice"
      ? micStatus === "ready"
        ? "浏览器录音已就绪"
        : micStatus === "idle"
          ? "待检测麦克风"
          : deviceStatusLabel
      : micStatus === "ready"
        ? "Live 麦克风已就绪"
        : micStatus === "idle"
          ? "待检测 Live 麦克风"
          : deviceStatusLabel;
  const prepareSelectionCards: Array<{
    key: PrepareDrawerKey;
    title: string;
    value: string;
    detail: string;
    icon: LucideIcon;
    tone: PrepareSelectionTone;
    panelDescription: string;
  }> = [
    {
      key: "INTERVIEW_TYPE",
      title: "面试类型",
      value: selectedInterviewType.label,
      detail: selectedInterviewType.detail,
      icon: Target,
      tone: "indigo",
      panelDescription: "决定这轮主要练哪类问题主线，让追问方向先收拢到最有价值的一类场景里。",
    },
    {
      key: "INTERVIEWER_STYLE",
      title: "面试官风格",
      value: selectedInterviewerStyle.label,
      detail: selectedInterviewerStyle.cue,
      icon: Bot,
      tone: "teal",
      panelDescription: "把这轮的语气和压力感先定下来，更容易贴近你当前想练的表达节奏。",
    },
    {
      key: "DIFFICULTY",
      title: "练习强度",
      value: selectedDifficulty.label,
      detail: selectedDifficulty.detail,
      icon: Gauge,
      tone: "amber",
      panelDescription: "练习强度会影响这一轮的节奏感、追问密度与临场氛围，帮助你按状态选择更合适的训练方式。",
    },
    {
      key: "ANSWER_MODE",
      title: "作答模式",
      value: answerModePrimaryLabel,
      detail: answerModeTileDetail,
      icon: answerMode === "text" ? MessageSquare : Mic,
      tone: answerMode === "text" ? "teal" : "indigo",
      panelDescription: isTextOnlyInterviewEntry
        ? "当前仅保留文字模式，适合先把表达结构讲顺后再继续推进。"
        : answerMode === "text"
          ? "先决定这轮是以文字还是语音进入；切到语音后，设备确认也会在这里一起完成。"
          : answerMode === "voice"
            ? "当前是语音模式，下面继续完成设备确认，再决定是浏览器录音还是手动上传。"
            : "当前是 Live 测试模式，下面继续完成设备确认，正式开始后会直接连接 Gemini Live。",
    },
    {
      key: "PREP_MATERIALS",
      title: "带入资料",
      value: prepMaterialPrimaryLabel,
      detail: prepMaterialTileDetail,
      icon: Briefcase,
      tone: "slate",
      panelDescription: "顶部填写的岗位语境会自动同步到本轮；这里更重要的作用，是选择你希望 AI 额外参考哪些个人资料。",
    },
    {
      key: "ANSWER_HELPER",
      title: "回答辅助器",
      value: answerHelperPrimaryLabel,
      detail: answerHelperTileDetail,
      icon: WandSparkles,
      tone: "indigo",
      panelDescription: "这些轻提醒只会提示回答结构，不会替你生成答案，适合在开练前先决定要不要带上。",
    },
  ];
  const activePrepareCard = expandedPrepareDrawer
    ? prepareSelectionCards.find((item) => item.key === expandedPrepareDrawer) ?? null
    : null;
  const isContinuableInterviewPromptVisible = stage === "prepare"
    && isContinuableInterviewPromptOpen
    && Boolean(continuableInterviewRecord?.sessionId)
    && !isPrivacyNoticeOpen
    && !isStartConfirmOpen
    && !activePrepareCard;
  const isSessionCreatingModalVisible = stage === "prepare" && sessionPending;
  const isSummaryGeneratingModalVisible = stage === "session" && summaryPending;
  const isPrivacyNoticeVisible = stage === "prepare" && isPrivacyNoticeOpen;
  const isPrepareSetupModalVisible = stage === "prepare" && Boolean(activePrepareCard);
  const isInterviewOverlayVisible = isContinuableInterviewPromptVisible
    || isSessionCreatingModalVisible
    || isSummaryGeneratingModalVisible
    || isPrivacyNoticeVisible
    || isPrepareSetupModalVisible;

  useEffect(() => {
    if (isInterviewOverlayVisible) {
      setInterviewOverlaysLoaded(true);
    }
  }, [isInterviewOverlayVisible]);
  const confirmationOverviewCards = [
    {
      label: "目标岗位",
      value: targetRole.trim() || "待填写",
      detail: targetCompany.trim() ? `目标企业：${targetCompany.trim()}` : "建议先锁定一个明确岗位",
      icon: Briefcase,
      cardClassName: "border-teal-100/90 bg-[linear-gradient(135deg,rgba(240,253,250,0.96),rgba(255,255,255,0.98))]",
      iconClassName: "border-teal-100 bg-white text-teal-600",
    },
    {
      label: "面试类型",
      value: selectedInterviewType.label,
      detail: `当前按 ${selectedDifficulty.label} 难度进入`,
      icon: Sparkles,
      cardClassName: "border-indigo-100/90 bg-[linear-gradient(135deg,rgba(238,242,255,0.96),rgba(255,255,255,0.98))]",
      iconClassName: "border-indigo-100 bg-white text-indigo-600",
    },
    {
      label: "面试官风格",
      value: selectedInterviewerStyle.label,
      detail: selectedInterviewerStyle.detail,
      icon: Bot,
      cardClassName: "border-sky-100/90 bg-[linear-gradient(135deg,rgba(240,249,255,0.96),rgba(255,255,255,0.98))]",
      iconClassName: "border-sky-100 bg-white text-sky-600",
    },
    {
      label: "作答方式",
      value: answerMode === "text" ? "文字模式" : answerMode === "voice" ? "语音模式" : "Live 模式",
      detail: answerMode === "text" ? "更适合先把表达结构讲顺" : deviceStatusLabel,
      icon: answerMode === "text" ? MessageSquare : Mic,
      cardClassName: "border-amber-100/90 bg-[linear-gradient(135deg,rgba(255,251,235,0.96),rgba(255,255,255,0.98))]",
      iconClassName: "border-amber-100 bg-white text-amber-600",
    },
    {
      label: "带入资料",
      value: selectedPrepMaterialLabels.length > 0 ? `${selectedPrepMaterialLabels.length} 项已就绪` : "仅基础语境",
      detail: optionalPrepMaterialLabels.length > 0
        ? `额外带入 ${optionalPrepMaterialLabels.length} 项个人资料`
        : "当前以岗位语境为主",
      icon: Briefcase,
      cardClassName: "border-slate-200 bg-[linear-gradient(135deg,rgba(248,250,252,0.98),rgba(255,255,255,0.98))]",
      iconClassName: "border-slate-200 bg-white text-slate-600",
    },
    {
      label: "回答辅助器",
      value: answerHelperEnabled ? `${enabledAnswerHelperOptions.length} 项轻提醒` : "暂不启用",
      detail: answerHelperEnabled ? answerHelperSummaryText : "这轮会以更干净的问答体验开始",
      icon: WandSparkles,
      cardClassName: "border-fuchsia-100/90 bg-[linear-gradient(135deg,rgba(253,244,255,0.96),rgba(255,255,255,0.98))]",
      iconClassName: "border-fuchsia-100 bg-white text-fuchsia-600",
    },
  ];
  const summarySections: SummarySection[] = summary
    ? [
      {
        id: "strengths",
        title: "高光表现",
        sectionLabel: "Strengths",
        items: summary.strengths,
        emptyText: "这一轮还没有抽出明确高光点，建议补充更多项目细节后再练一轮。",
        icon: CheckCircle2,
        tone: "success",
      },
      {
        id: "weaknesses",
        title: "待提升区",
        sectionLabel: "Weaknesses",
        items: summary.weaknesses,
        emptyText: "当前没有给出明显短板，后续可以继续提升表达稳定性和量化程度。",
        icon: AlertCircle,
        tone: "warning",
      },
      {
        id: "suggestions",
        title: "下一步建议",
        sectionLabel: "Next Steps",
        items: summary.suggestions,
        emptyText: "后续会在这里展示更结构化的行动建议。",
        icon: WandSparkles,
        tone: "info",
      },
    ]
    : [];
  const summaryNarration = summary ? buildSummaryNarration(summary) : "";
  const isSessionCompleted = Boolean(summary);
  const isReadOnlySession = stage === "session" && isSessionCompleted;
  const answerModeStatusLabel = getAnswerModeStatusLabel(answerMode);
  const isLiveMicActive = liveMicStatus === "实时采集中";
  useEffect(() => {
    if (stage !== "session" || !sessionId || sessionPending || isSessionCompleted || isLiveAnswerMode) {
      return;
    }
    if (textMessageCount !== replyRoundUsed) {
      return;
    }
    if (!answerHelperEnabled || !enabledAnswerHelperCueKeysKey || !latestUserMessage) {
      return;
    }

    const requestKey = `${sessionId}:${latestUserMessage.id}:${enabledAnswerHelperCueKeysKey}`;
    if (answerHelperAnalysisSourceKeyRef.current === requestKey) {
      return;
    }

    answerHelperAnalysisSourceKeyRef.current = requestKey;
    const generation = practiceGenerationRef.current;
    let active = true;

    // 回答辅助器只在最新用户回答入库后触发一次，避免每次渲染重复请求。
    setAnswerHelperAnalysisPending(true);
    setAnswerHelperAnalysisError(null);

    const runAnswerHelperAnalysis = async () => {
      try {
        const response = await apiRequest<InterviewAnswerHelperAnalysis>(`/ai/interview/sessions/${sessionId}/answer-helper`, {
          method: "POST",
        });
        if (!active || !isPracticeCurrent(generation) || answerHelperAnalysisSourceKeyRef.current !== requestKey) {
          return;
        }
        setLatestAnswerHelperAnalysis(response);
      } catch {
        if (!active || !isPracticeCurrent(generation) || answerHelperAnalysisSourceKeyRef.current !== requestKey) {
          return;
        }
        setAnswerHelperAnalysisError("本轮回答辅助诊断暂未生成，可继续后续问答。");
      } finally {
        if (active && isPracticeCurrent(generation) && answerHelperAnalysisSourceKeyRef.current === requestKey) {
          setAnswerHelperAnalysisPending(false);
        }
      }
    };

    void runAnswerHelperAnalysis();

    return () => {
      active = false;
    };
  }, [
    answerHelperEnabled,
    enabledAnswerHelperCueKeysKey,
    isSessionCompleted,
    latestUserMessage,
    replyRoundUsed,
    sessionId,
    sessionPending,
    stage,
    textMessageCount,
    isLiveAnswerMode,
  ]);
  const selectedResumeRecordId = selectedPrepMaterials.includes("LATEST_RESUME") && effectiveResumeSnapshot
    ? effectiveResumeSnapshot.recordId
    : null;
  const sessionContextPrepMaterialKeys = enabledPrepMaterials.map((item) => item.key);
  const sessionPromptContext = useMemo(
    () => buildInterviewPromptContextFromPrepMaterials({
      selectedPrepMaterials,
      educationBackgroundSummary,
      jobStatusText,
      academicRecordSummary,
      portraitTagLabels,
      portraitEvidenceLines,
      latestResumeSnapshot: effectiveResumeSnapshot,
      selfIntro: profileHint?.selfIntro?.trim() ?? "",
      skillTags,
    }),
    [
      academicRecordSummary,
      educationBackgroundSummary,
      jobStatusText,
      effectiveResumeSnapshot,
      portraitEvidenceLines,
      portraitTagLabels,
      profileHint?.selfIntro,
      selectedPrepMaterials,
      skillTags,
    ],
  );
  const liveFocusTopics = [
    selectedInterviewType.label,
    selectedInterviewType.detail,
    selectedInterviewerStyle.label,
    answerHelperEnabled && enabledAnswerHelperOptions.length > 0
      ? `关注 ${enabledAnswerHelperOptions.map((item) => item.label).join("、")}`
      : null,
  ].filter(Boolean).join("；");
  const liveResumeSummary = effectiveResumeSnapshot?.summary?.trim()
    || effectiveResumeSnapshot?.suggestions[0]?.trim()
    || profileHint?.selfIntro?.trim()
    || sessionPromptContext.trim()
    || "未提供额外简历摘要。";

  useEffect(() => {
    if (prepMaterialsInitializedRef.current || profileLoading) {
      return;
    }

    // 默认只勾选已存在的教育背景和技能标签，其他材料需要用户主动授权。
    const defaults: PrepMaterialKey[] = [];
    if (educationBackgroundLines.length > 0) {
      defaults.push("CAMPUS_BACKGROUND");
    }
    if (skillTags.length > 0) {
      defaults.push("SKILL_TAGS");
    }
    setSelectedPrepMaterials(defaults);
    prepMaterialsInitializedRef.current = true;
  }, [profileLoading, educationBackgroundLines.length, skillTags.length]);

  useEffect(() => {
    setSelectedPrepMaterials((current) => {
      // 资料源被清空后自动移除勾选项，避免 prompt 带入空材料。
      const next = current.filter((materialKey) => manualPrepMaterialOptions.some((item) => item.key === materialKey && item.available));
      return next.length === current.length && next.every((item, index) => item === current[index]) ? current : next;
    });
  }, [
    educationBackgroundLines.length,
    jobStatusText,
    academicRecordLines.length,
    portraitTagLabels.length,
    portraitEvidenceLines.length,
    effectiveResumeSnapshot?.recordId,
    profileHint?.selfIntro,
    skillTags.length,
  ]);

  const resetSessionState = () => {
    // 退出或重新开始时统一清理 Live、TTS、录音和会话本地状态。
    closeLiveClient();
    clearAutoFinishTransitionTimeouts();
    clearStreamingMessageVisualSettleTimeout();
    stopTtsPlayback();
    clearTtsObjectUrlCache();
    setTtsPendingKey(null);
    recordingStopModeRef.current = "discard";
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== "inactive") {
      mediaRecorderRef.current.stop();
    } else {
      recordingStreamRef.current?.getTracks().forEach((track) => track.stop());
    }
    mediaRecorderRef.current = null;
    recordingStreamRef.current = null;
    recordingChunksRef.current = [];
    recordingStartedAtRef.current = null;
    setIsRecording(false);
    setRecordingElapsedMs(0);
    setRouteSessionHydrating(false);
    setSessionContextSource("local");
    setRestoredResumeSnapshot(null);
    setRestoredPromptContext(null);
    setRestoredPrepMaterialKeys(null);
    setSessionId(null);
    setSessionCreatedAt(null);
    setReplyRoundLimit(0);
    setReplyRoundUsed(0);
    setChargedPoints(null);
    setPointsBalance(null);
    setQuotaUnitsReserved(null);
    setMessages([]);
    setDraftAnswer("");
    setSummary(null);
    setLatestCoachFeedback(null);
    setLatestScoreHint(null);
    setLatestAnswerHelperAnalysis(null);
    setAnswerHelperAnalysisPending(false);
    setAnswerHelperAnalysisError(null);
    answerHelperAnalysisSourceKeyRef.current = null;
    setStatusNote(null);
    setErrorMessage(null);
    setIsStartConfirmOpen(false);
    setSessionPending(false);
    setSummaryPending(false);
    setAbandonPending(false);
    setVoiceFile(null);
    if (voiceFileInputRef.current) {
      voiceFileInputRef.current.value = "";
    }
  };

  const ensureLivePendingMessageId = (role: "USER" | "ASSISTANT") => {
    const ref = role === "USER" ? livePendingUserMessageIdRef : livePendingModelMessageIdRef;
    if (!ref.current) {
      ref.current = createLocalId(role === "USER" ? "live-user" : "live-assistant");
    }
    return ref.current;
  };

  const upsertLivePendingMessage = (role: "USER" | "ASSISTANT", text: string) => {
    const messageId = ensureLivePendingMessageId(role);
    upsertChatMessage({
      messageId,
      role,
      text,
      badge: role === "USER"
        ? "实时转写中"
        : liveAwaitingFinalTurnRef.current
          ? "口头总结中"
          : "实时追问中",
      scoreHint: null,
      audioObjectKey: null,
      isStreaming: true,
    });
  };

  const commitLivePendingMessage = (role: "USER" | "ASSISTANT", interrupted = false) => {
    const isUser = role === "USER";
    const messageIdRef = isUser ? livePendingUserMessageIdRef : livePendingModelMessageIdRef;
    const textBufferRef = isUser ? liveUserStreamBufferRef : liveModelStreamBufferRef;
    const normalizedText = textBufferRef.current.trim();

    if (messageIdRef.current && normalizedText) {
      upsertChatMessage({
        messageId: messageIdRef.current,
        role,
        text: normalizedText,
        badge: isUser
          ? "实时转写"
          : interrupted
            ? "输出被打断"
            : liveAwaitingFinalTurnRef.current
              ? "口头总结"
              : "Live 追问",
        scoreHint: null,
        audioObjectKey: null,
        isStreaming: false,
      });
      liveTranscriptRef.current = [
        ...liveTranscriptRef.current,
        {
          role,
          text: normalizedText,
        },
      ];
      if (isUser) {
        setReplyRoundUsed((current) => current + 1);
      }
    } else if (messageIdRef.current && !normalizedText) {
      removeChatMessage(messageIdRef.current);
    }

    messageIdRef.current = null;
    textBufferRef.current = "";
  };

  const handleFinalizeLiveInterview = async (activeSessionId: string) => {
    const transcriptMessages = liveTranscriptRef.current.filter((message) => message.text.trim());
    const hasUserReply = transcriptMessages.some((message) => message.role === "USER");
    if (!hasUserReply) {
      setSummaryPending(false);
      setLiveFinishPending(false);
      showErrorToast("请至少完成一轮有效的 Live 回答后，再生成复盘。");
      setStatusNote("当前还没有足够的 Live transcript，请先完成至少一轮有效对答。");
      liveAwaitingFinalTurnRef.current = false;
      return;
    }

    closeLiveClient({ preserveTranscript: true, preserveLogs: true });
    liveTranscriptRef.current = transcriptMessages;
    // Live transcript 最终回灌 Java 会话，再复用后端 summary 生成正式复盘。
    setSummaryPending(true);
    setLiveFinishPending(true);
    setStatusNote("正在同步 Live transcript 并生成本轮复盘...");

    try {
      await apiRequest<null>(`/ai/interview/sessions/${activeSessionId}/live-transcript`, {
        method: "POST",
        body: JSON.stringify({
          messages: transcriptMessages.map((message) => ({
            role: message.role,
            text: message.text,
          })),
        }),
      });
      const response = await apiRequest<InterviewSummary>(`/ai/interview/sessions/${activeSessionId}/summary`, {
        method: "POST",
      });
      setSummary(response);
      setStatusNote("Live transcript 已同步，复盘报告已经生成。");
      navigateToInterviewStage("report", activeSessionId);
    } catch (requestError) {
      liveTranscriptRef.current = transcriptMessages;
      showErrorToast(buildInterviewErrorMessage(requestError));
      setStatusNote("Live transcript 同步失败，当前会话仍保留在页面中，可稍后重试。");
    } finally {
      liveAwaitingFinalTurnRef.current = false;
      setSummaryPending(false);
      setLiveFinishPending(false);
    }
  };

  const handleToggleLiveMicrophone = async () => {
    const liveClient = liveClientRef.current;
    if (!liveClient) {
      showErrorToast("当前还没有建立 Live 连接，请稍后重试或手动重连。");
      return;
    }

    setErrorMessage(null);
    try {
      const nextEnabled = !liveClient.isMicrophoneEnabled();
      await liveClient.setMicrophoneEnabled(nextEnabled);
      setStatusNote(
        nextEnabled
          ? "已恢复 Live 麦克风，后续检测到说话后会自动上传语音。"
          : "已静音 Live 麦克风，你可以继续听 AI 输出，稍后再恢复。",
      );
    } catch (error) {
      showErrorToast(error instanceof Error ? error.message : "Live 麦克风切换失败，请稍后重试。");
    }
  };

  const handleReconnectLiveSession = () => {
    if (!sessionId) {
      return;
    }
    closeLiveClient({ preserveTranscript: true, preserveLogs: true });
    appendLiveLog("手动重连", "浏览器已请求重新建立 Live 连接。注意：Gemini 侧会以新的 Live 会话续接。");
    setStatusNote("正在重新连接 Live 服务。页面 transcript 会保留，但 Gemini 侧会以新的 Live 会话继续。");
    setLiveReconnectNonce((current) => current + 1);
  };

  const clearInterviewSessionTtsCache = async (activeSessionId: string | null) => {
    if (!activeSessionId) {
      return;
    }
    try {
      await apiRequest<null>(`/ai/interview/sessions/${activeSessionId}/tts-cache`, {
        method: "DELETE",
      });
    } catch {
      // 会话退出时缓存清理只做最佳努力，不阻断主流程
    }
  };

  liveEventHandlerRef.current = (event: InterviewLiveEvent) => {
    const payload = event.payload ?? {};
    switch (event.type) {
      case "client.connection":
        setLiveConnectionStatus(typeof payload.message === "string" ? payload.message : "连接状态已更新");
        if (typeof payload.message === "string") {
          appendLiveLog("连接状态", payload.message);
        }
        break;
      case "client.mic_status":
        setLiveMicStatus(typeof payload.message === "string" ? payload.message : "麦克风状态已更新");
        break;
      case "client.playback_status":
        setLivePlaybackStatus(typeof payload.message === "string" ? payload.message : "播放状态已更新");
        break;
      case "client.log":
        appendLiveLog(
          typeof payload.message === "string" ? payload.message : "Live 日志",
          typeof payload.detail === "string" ? payload.detail : "",
        );
        break;
      case "server.status":
        setLiveConnectionStatus(typeof payload.message === "string" ? payload.message : "Live 服务处理中");
        if (typeof payload.message === "string") {
          setStatusNote(payload.message);
          appendLiveLog("服务状态", payload.message);
        }
        break;
      case "server.ready":
        setLiveConnectionStatus(typeof payload.message === "string" ? payload.message : "Gemini Live 会话已建立");
        appendLiveLog(
          "会话就绪",
          `${typeof payload.model === "string" ? payload.model : ""}${typeof payload.voice === "string" ? ` / ${payload.voice}` : ""}`,
        );
        break;
      case "session.reconnecting":
        setLiveConnectionStatus(typeof payload.message === "string" ? payload.message : "Gemini Live 正在续接");
        appendLiveLog("连接续接", typeof payload.message === "string" ? payload.message : "");
        break;
      case "session.go_away":
        appendLiveLog("Gemini 即将主动切断连接", typeof payload.timeLeft === "string" ? payload.timeLeft : "");
        break;
      case "session.resumption":
        appendLiveLog("收到 session resumption handle", typeof payload.message === "string" ? payload.message : "");
        break;
      case "usage.update":
        setLiveTokenCount(typeof payload.totalTokenCount === "number" ? payload.totalTokenCount : 0);
        break;
      case "turn.user.transcript": {
        const nextText = mergeStreamingText(
          liveUserStreamBufferRef.current,
          typeof payload.text === "string" ? payload.text : "",
        );
        liveUserStreamBufferRef.current = nextText;
        setLiveUserCaption(nextText || "尚无输入字幕");
        upsertLivePendingMessage("USER", nextText);
        break;
      }
      case "turn.model.transcript":
      case "turn.model.text": {
        commitLivePendingMessage("USER");
        const nextText = mergeStreamingText(
          liveModelStreamBufferRef.current,
          typeof payload.text === "string" ? payload.text : "",
        );
        liveModelStreamBufferRef.current = nextText;
        setLiveModelCaption(nextText || "尚无输出字幕");
        upsertLivePendingMessage("ASSISTANT", nextText);
        break;
      }
      case "turn.model.interrupted":
        commitLivePendingMessage("ASSISTANT", true);
        appendLiveLog("模型输出被打断", typeof payload.message === "string" ? payload.message : "");
        break;
      case "turn.complete": {
        commitLivePendingMessage("USER");
        commitLivePendingMessage("ASSISTANT");
        setLiveUserCaption("尚无输入字幕");
        setLiveModelCaption("尚无输出字幕");
        if (liveAwaitingFinalTurnRef.current && sessionId) {
          void handleFinalizeLiveInterview(sessionId);
        } else {
          setStatusNote("上一轮 Live 输出已结束，可以继续回答。");
        }
        break;
      }
      case "error": {
        const detail = typeof payload.detail === "string" ? payload.detail : "";
        appendLiveLog("Live 错误", typeof payload.message === "string" ? `${payload.message}${detail ? `\n${detail}` : ""}` : detail);
        showErrorToast(typeof payload.message === "string" ? payload.message : "Live 会话发生异常，请稍后重试。");
        break;
      }
      default:
        break;
    }
  };

  useEffect(() => {
    if (!isLiveAnswerMode || role !== "STUDENT" || stage !== "session" || !sessionId || summary || routeSessionHydrating) {
      return;
    }
    if (liveClientRef.current) {
      return;
    }

    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const client = new InterviewLiveClient({
      wsUrl: `${protocol}//${window.location.host}/ai/interview/live/ws`,
      onEvent: (event) => {
        liveEventHandlerRef.current(event);
      },
    });
    liveClientRef.current = client;

    void client.connect({
      candidateName: studentName,
      targetRole: targetRole.trim() || "通用校招岗位",
      interviewType: selectedInterviewType.label,
      interviewerStyle: selectedInterviewerStyle.label,
      focusTopics: liveFocusTopics,
      resumeSummary: liveResumeSummary,
      jobDescription: targetJobDescription.trim(),
    }).catch((error) => {
      if (liveClientRef.current === client) {
        liveClientRef.current = null;
      }
      setLiveConnectionStatus("Live 连接失败");
      appendLiveLog("Live 连接失败", error instanceof Error ? error.message : String(error));
      showErrorToast("Live 连接失败，请确认 Python Live 服务已启动且 Gemini Key 已配置。");
    });
  }, [
    isLiveAnswerMode,
    liveFocusTopics,
    liveReconnectNonce,
    liveResumeSummary,
    role,
    routeSessionHydrating,
    selectedInterviewerStyle.label,
    selectedInterviewType.label,
    sessionId,
    stage,
    studentName,
    summary,
    targetJobDescription,
    targetRole,
  ]);

  useEffect(() => {
    window.scrollTo({ top: 0, behavior: "smooth" });
  }, [location.pathname, location.search]);

  useEffect(() => {
    if (!toast) {
      return undefined;
    }
    const timer = window.setTimeout(() => {
      setToast((current) => (current?.id === toast.id ? null : current));
    }, 3200);
    return () => window.clearTimeout(timer);
  }, [toast]);

  useEffect(() => {
    if (stage !== "prepare" || sessionContextSource !== "detail") {
      return;
    }
    setSessionContextSource("local");
  }, [sessionContextSource, stage]);

  useEffect(() => {
    if (role !== "STUDENT") {
      return;
    }
    if (stage === "prepare" || routeSessionId) {
      return;
    }

    startTransition(() => {
      navigate(buildInterviewStageHref("prepare"), { replace: true });
    });
  }, [navigate, role, routeSessionId, stage]);

  useEffect(() => {
    if (role !== "STUDENT") {
      setRouteSessionHydrating(false);
      return;
    }
    if (interviewEntryOptionsLoading) {
      return;
    }
    if (stage === "prepare" || !routeSessionId) {
      setRouteSessionHydrating(false);
      return;
    }

    const hasCurrentSession = sessionId === routeSessionId;
    const needsSummaryRestore = stage === "report" && !summary;
    const needsHydration = !hasCurrentSession || messages.length === 0 || needsSummaryRestore;
    if (!needsHydration) {
      setRouteSessionHydrating(false);
      return;
    }

    const detailOnlyRestore = !hasCurrentSession || messages.length === 0;
    let active = true;

    if (sessionId && sessionId !== routeSessionId) {
      invalidatePracticeGeneration();
    }

    // 直接访问 session/report 深链时，从后端详情恢复消息、summary 和 prompt context。
    setRouteSessionHydrating(true);
    setErrorMessage(null);
    setStatusNote(stage === "report" ? "正在恢复本轮复盘..." : "正在恢复当前会话...");

    const loadSessionDetail = async () => {
      try {
        const response = await apiRequest<InterviewSessionDetailResponse>(`/ai/interview/sessions/${routeSessionId}`);
        if (!active) {
          return;
        }

        let restoredPointsBalance = typeof response.pointsBalance === "number" && Number.isFinite(response.pointsBalance)
          ? response.pointsBalance
          : null;
        if (restoredPointsBalance === null) {
          try {
            const quotaResponse = await apiRequest<AiQuotaRemainingResponse>("/ai/quota/remaining");
            if (!active) {
              return;
            }
            restoredPointsBalance = typeof quotaResponse.pointsBalance === "number" && Number.isFinite(quotaResponse.pointsBalance)
              ? quotaResponse.pointsBalance
              : null;
          } catch {
            restoredPointsBalance = null;
          }
        }

        const restoredSessionContext = response.sessionContext;
        const restoredPrepKeys = restoredSessionContext?.prepMaterialKeys ?? [];
        const restoredManualPrepKeys = restoredPrepKeys.filter(
          (item): item is ManualPrepMaterialKey => item !== "TARGET_COMPANY" && item !== "TARGET_JD",
        );
        const restoredResumeDetail: ResumePrepSnapshot | null = response.resumeContext
          ? {
              recordId: response.resumeContext.recordId,
              summary: response.resumeContext.summary ?? "",
              suggestions: response.resumeContext.suggestions ?? [],
              scoreLabel: response.resumeContext.scoreLabel ?? null,
              targetRole: response.resumeContext.targetRole ?? "",
              targetContext: response.resumeContext.targetContext ?? "",
              inputMode: response.resumeContext.inputMode === "pdf" ? "pdf" : "text",
              jobDescription: response.resumeContext.jobDescription ?? "",
              resumeText: response.resumeContext.resumeTextExcerpt ?? "",
              pdfFileName: response.resumeContext.pdfFileName ?? null,
              pointsConsumed: 0,
              createdAt: response.resumeContext.createdAt ?? response.createdAt,
            }
          : null;
        const restoredAnswerMode = restoredSessionContext?.answerMode === "VOICE"
          ? "voice"
          : restoredSessionContext?.answerMode === "LIVE"
            ? "live"
          : restoredSessionContext?.answerMode === "TEXT"
            ? "text"
            : response.mode === "INTERVIEW_VOICE"
              ? "voice"
              : "text";
        if (!isAnswerModeEntryEnabled(restoredAnswerMode, interviewEntryOptions)) {
          invalidatePracticeGeneration();
          resetSessionState();
          showErrorToast(
            restoredAnswerMode === "live"
              ? "当前实时语音面试暂未开放，请先使用文字模式开始。"
              : "当前语音面试暂未开放，请先使用文字模式开始。",
          );
          startTransition(() => {
            navigate(buildInterviewStageHref("prepare"), { replace: true });
          });
          return;
        }
        const restoredAnswerHelperCueKeys = normalizeAnswerHelperCueKeys(restoredSessionContext?.answerHelperCueKeys ?? []);
        const restoredAnswerHelperEnabled = typeof restoredSessionContext?.answerHelperEnabled === "boolean"
          ? restoredSessionContext.answerHelperEnabled
          : true;
        const hydratedSummary = response.summary ?? null;
        const restoredMessages = buildHydratedChatMessages(response.messages ?? [], Boolean(hydratedSummary));
        const latestAssistantMessage = [...(response.messages ?? [])].reverse().find((message) => message.role === "ASSISTANT");
        const latestAssistantFeedbackMessage = [...(response.messages ?? [])].reverse().find(
          (message) => message.role === "ASSISTANT" && message.coachFeedback?.trim(),
        );

        stopTtsPlayback();
        clearTtsObjectUrlCache();
        setTtsPendingKey(null);
        setTtsPlayingKey(null);
        setSessionId(response.sessionId);
        setSessionCreatedAt(response.createdAt ?? null);
        setReplyRoundLimit(response.replyRoundLimit);
        setReplyRoundUsed(response.replyRoundUsed);
        setChargedPoints(response.prepaidPoints);
        setPointsBalance(restoredPointsBalance);
        setQuotaUnitsReserved(response.reservedQuotaWeight);
        setMessages(restoredMessages);
        setDraftAnswer("");
        setSummary(hydratedSummary);
        setLatestCoachFeedback(latestAssistantFeedbackMessage?.coachFeedback?.trim() || null);
        setLatestScoreHint(latestAssistantMessage?.scoreHint ?? null);
        setLatestAnswerHelperAnalysis(null);
        setAnswerHelperAnalysisPending(false);
        setAnswerHelperAnalysisError(null);
        answerHelperAnalysisSourceKeyRef.current = null;
        setStatusNote(
          hydratedSummary
            ? stage === "report"
              ? "已恢复本轮复盘。"
              : "已恢复本轮对话记录，可以继续查看或切到复盘页。"
            : "已恢复当前会话，可以继续作答。",
        );
        setSessionPending(false);
        setSummaryPending(false);
        setAbandonPending(false);
        setVoiceFile(null);
        setInterviewType(restoredSessionContext?.interviewType ?? "PROJECT_DEEP_DIVE");
        setInterviewerStyle(restoredSessionContext?.interviewerStyle ?? "STANDARD");
        setDifficulty(restoredSessionContext?.difficulty ?? "MEDIUM");
        setAnswerMode(restoredAnswerMode);
        setAnswerHelperEnabled(restoredAnswerHelperEnabled);
        setSelectedAnswerHelperCueKeys(
          restoredAnswerHelperEnabled
            ? (restoredAnswerHelperCueKeys.length > 0 ? restoredAnswerHelperCueKeys : defaultAnswerHelperCueKeys)
            : [],
        );
        setTargetRole(response.targetRole?.trim() || "");
        setTargetCompany(restoredSessionContext?.targetCompany?.trim() || "");
        setTargetJobDescription(restoredSessionContext?.targetJobDescription?.trim() || "");
        setSelectedPrepMaterials(restoredManualPrepKeys);
        setRestoredPrepMaterialKeys(restoredPrepKeys);
        setRestoredPromptContext(restoredSessionContext?.promptContext?.trim() || null);
        setRestoredResumeSnapshot(restoredResumeDetail);
        if (detailOnlyRestore) {
          setSessionContextSource("detail");
        }
        if (voiceFileInputRef.current) {
          voiceFileInputRef.current.value = "";
        }

        if (stage === "report" && !hydratedSummary) {
          setStatusNote("当前会话还没有生成复盘，已回到对话页。");
          startTransition(() => {
            navigate(buildInterviewStageHref("session", response.sessionId), { replace: true });
          });
        }
      } catch (requestError) {
        if (!active) {
          return;
        }

        invalidatePracticeGeneration();
        resetSessionState();
        showErrorToast(buildInterviewErrorMessage(requestError));
        startTransition(() => {
          navigate(buildInterviewStageHref("prepare"), { replace: true });
        });
      } finally {
        if (active) {
          setRouteSessionHydrating(false);
        }
      }
    };

    void loadSessionDetail();

    return () => {
      active = false;
    };
  }, [interviewEntryOptions, interviewEntryOptionsLoading, messages.length, navigate, role, routeSessionId, sessionId, stage, summary]);

  if (stage === "prepare" && ((profileLoading && !profileHint) || interviewEntryOptionsLoading)) {
    return <InterviewPageSkeleton />;
  }

  if (shouldShowRouteHydrationSkeleton) {
    return (
      <WorkspacePageLoadingScreen
        title={stage === "report" ? "正在恢复面试复盘" : "正在恢复模拟面试"}
        description={stage === "report"
          ? "正在读取本轮会话记录与总结报告，请稍候。"
          : "正在读取本轮会话记录与消息上下文，请稍候。"}
      />
    );
  }

  const handleResetPractice = () => {
    const activeSessionId = sessionId;
    invalidatePracticeGeneration();
    resetSessionState();
    navigateToInterviewStage("prepare");
    void clearInterviewSessionTtsCache(activeSessionId);
  };

  const handleAbandonPractice = async () => {
    const activeSessionId = sessionId;
    if (!activeSessionId) {
      handleResetPractice();
      return;
    }

    closeLiveClient();
    setAbandonPending(true);
    setErrorMessage(null);
    setStatusNote("正在放弃本轮并清理当前会话...");

    try {
      await apiRequest<null>(`/ai/interview/sessions/${activeSessionId}`, {
        method: "DELETE",
      });
      invalidatePracticeGeneration();
      resetSessionState();
      navigateToInterviewStage("prepare");
    } catch (requestError) {
      showErrorToast(buildInterviewErrorMessage(requestError));
      setStatusNote("放弃本轮失败，当前会话仍保留，你可以稍后重试。");
    } finally {
      setAbandonPending(false);
    }
  };

  const handleMicCheck = async () => {
    if (!navigator.mediaDevices?.getUserMedia) {
      setMicStatus("unsupported");
      setMicMessage(isLiveAnswerMode
        ? "当前浏览器暂时不适合进入 Live 语音模式，建议先用文字模式开始。"
        : "当前浏览器暂时不适合直接做麦克风检测，建议先用文字模式开始；需要语音练习时，也可以稍后补充音频文件。");
      return;
    }

    setMicStatus("checking");
    setMicMessage("正在帮你确认浏览器里的麦克风权限，请稍等片刻。");

    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      stream.getTracks().forEach((track) => track.stop());
      if (isLiveAnswerMode) {
        if (typeof AudioContext === "undefined") {
          setMicStatus("unsupported");
          setMicMessage("当前浏览器不支持 Live 所需的 Web Audio 能力，建议改用文字模式。");
          return;
        }
        setMicStatus("ready");
        setMicMessage("麦克风已经准备好，这一轮可以直接使用 Live 实时语音面试。");
        return;
      }

      if (!preferredRecordingMimeType) {
        setMicStatus("unsupported");
        setMicMessage("已经识别到麦克风，但当前浏览器不适合直接录音。建议先用文字模式开始，或稍后改用音频文件继续练习。");
        return;
      }

      setMicStatus("ready");
      setMicMessage("麦克风已经准备好，这一轮可以直接使用浏览器语音作答。");
    } catch {
      setMicStatus("blocked");
      setMicMessage("暂时没有获得麦克风权限。你可以先打开浏览器授权，或者切到文字模式先开始这一轮。");
    }
  };

  const appendAssistantMessage = (text: string | null | undefined, badge: string) => {
    if (!text?.trim()) {
      return;
    }

    setMessages((current) => [
      ...current,
      {
        id: createLocalId("assistant"),
        role: "ASSISTANT",
        text: text.trim(),
        createdAt: new Date().toISOString(),
        badge,
        scoreHint: null,
        audioObjectKey: null,
        isStreaming: false,
      },
    ]);
  };

  const upsertChatMessage = ({
    messageId,
    role,
    text,
    badge,
    scoreHint,
    audioObjectKey,
    isStreaming,
  }: {
    messageId: string;
    role: ChatMessage["role"];
    text: string | null | undefined;
    badge: string | null;
    scoreHint: number | null;
    audioObjectKey: string | null;
    isStreaming?: boolean;
  }) => {
    setMessages((current) => {
      const existingMessage = current.find((message) => message.id === messageId);
      const normalizedText = text?.trim() || existingMessage?.text || "";
      if (!normalizedText) {
        return current;
      }
      const nextMessage: ChatMessage = {
        id: messageId,
        role,
        text: normalizedText,
        createdAt: existingMessage?.createdAt ?? new Date().toISOString(),
        badge,
        scoreHint: scoreHint ?? existingMessage?.scoreHint ?? null,
        audioObjectKey: audioObjectKey ?? existingMessage?.audioObjectKey ?? null,
        isStreaming: isStreaming ?? existingMessage?.isStreaming ?? false,
      };

      if (!existingMessage) {
        return [...current, nextMessage];
      }

      return current.map((message) => (message.id === messageId ? nextMessage : message));
    });
  };

  const removeChatMessage = (messageId: string) => {
    setMessages((current) => current.filter((message) => message.id !== messageId));
  };

  const appendStreamingAssistantPlaceholder = (messageId: string) => {
    setMessages((current) => {
      if (current.some((message) => message.id === messageId)) {
        return current;
      }
      return [
        ...current,
        {
          id: messageId,
          role: "ASSISTANT",
          text: "",
          createdAt: new Date().toISOString(),
          badge: "正在生成追问",
          scoreHint: null,
          audioObjectKey: null,
          isStreaming: true,
        },
      ];
    });
  };

  const settleStreamingAssistantMessage = (messageId: string, badge: string) => {
    setMessages((current) => current.map((message) => (
      message.id === messageId && message.role === "ASSISTANT" && message.text.trim()
        ? {
            ...message,
            badge,
            isStreaming: false,
          }
        : message
    )));
  };

  const scheduleStreamingAssistantVisualSettle = (messageId: string, badge: string) => {
    clearStreamingMessageVisualSettleTimeout();
    streamingMessageVisualSettleTimeoutRef.current = window.setTimeout(() => {
      settleStreamingAssistantMessage(messageId, badge);
      streamingMessageVisualSettleTimeoutRef.current = null;
    }, STREAMING_MESSAGE_VISUAL_SETTLE_MS);
  };

  const runInterviewReplyStream = async (
    activeSessionId: string,
    answerText: string,
    assistantMessageId: string,
  ): Promise<InterviewReplyResponse> => {
    // 文本追问使用 SSE：start 更新状态、reply_delta 增量渲染、done 返回最终结构。
    const response = await apiRequest<Response>(`/ai/interview/sessions/${activeSessionId}/reply/stream`, {
      method: "POST",
      body: JSON.stringify({ answerText }),
      rawResponse: true,
    });

    if (!response.ok) {
      throw await buildApiClientErrorFromResponse(response, "流式面试追问失败");
    }

    if (!response.body) {
      throw new ApiClientError("当前流式响应不可用，请稍后重试。", 500, "SSE_STREAM_EMPTY");
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    let streamedFollowUp = "";
    let finalResult: InterviewReplyResponse | null = null;
    let streamCompleted = false;

    const consumeEventBlock = (rawBlock: string) => {
      const parsed = parseSseBlock(rawBlock);
      if (!parsed) {
        return;
      }

      const payload = parsed.payload as Record<string, unknown> | null;

      switch (parsed.eventName) {
        case "start":
          setStatusNote(
            typeof payload?.message === "string"
              ? payload.message
              : "已接收回答，AI 正在流式生成追问。",
          );
          break;
        case "reply_delta": {
          const delta = typeof payload?.delta === "string" ? payload.delta : "";
          if (!delta) {
            break;
          }
          streamedFollowUp += delta;
          upsertChatMessage({
            messageId: assistantMessageId,
            role: "ASSISTANT",
            text: streamedFollowUp,
            badge: "正在生成追问",
            scoreHint: null,
            audioObjectKey: null,
            isStreaming: true,
          });
          scheduleStreamingAssistantVisualSettle(assistantMessageId, "下一轮追问");
          break;
        }
        case "done":
          clearStreamingMessageVisualSettleTimeout();
          if (payload?.result) {
            finalResult = payload.result as InterviewReplyResponse;
            streamCompleted = true;
          }
          break;
        case "error":
          throw new ApiClientError(
            typeof payload?.message === "string" ? payload.message : "流式面试追问失败",
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
        if (streamCompleted) {
          break;
        }
        boundaryIndex = buffer.indexOf("\n\n");
      }

      if (streamCompleted) {
        break;
      }

      if (done) {
        break;
      }
    }

    if (!streamCompleted && buffer.trim()) {
      consumeEventBlock(buffer);
    }

    if (!finalResult) {
      throw new ApiClientError("AI 已完成流式输出，但未返回最终结果。", 500, "SSE_DONE_MISSING");
    }

    const resolvedFinalResult = finalResult as InterviewReplyResponse;

    if (resolvedFinalResult.followUpQuestion?.trim()) {
      upsertChatMessage({
        messageId: assistantMessageId,
        role: "ASSISTANT",
        text: resolvedFinalResult.followUpQuestion,
        badge: resolvedFinalResult.shouldFinish ? "结束提示" : "下一轮追问",
        scoreHint: null,
        audioObjectKey: null,
        isStreaming: false,
      });
    } else {
      removeChatMessage(assistantMessageId);
    }

    return resolvedFinalResult;
  };

  const runVoiceRoundtripStream = async (
    activeSessionId: string,
    audioFile: File,
    transcriptMessageId: string,
    assistantMessageId: string,
  ): Promise<InterviewVoiceRoundtripResponse> => {
    // 语音 roundtrip 的 SSE 先回 transcript，再继续复用追问 delta。
    const formData = new FormData();
    formData.append("audioFile", audioFile);

    const response = await apiRequest<Response>(`/ai/interview/sessions/${activeSessionId}/voice-roundtrip/stream`, {
      method: "POST",
      body: formData,
      rawResponse: true,
    });

    if (!response.ok) {
      throw await buildApiClientErrorFromResponse(response, "流式语音面试失败");
    }

    if (!response.body) {
      throw new ApiClientError("当前流式响应不可用，请稍后重试。", 500, "SSE_STREAM_EMPTY");
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    let streamedFollowUp = "";
    let finalResult: InterviewVoiceRoundtripResponse | null = null;
    let streamCompleted = false;

    const consumeEventBlock = (rawBlock: string) => {
      const parsed = parseSseBlock(rawBlock);
      if (!parsed) {
        return;
      }

      const payload = parsed.payload as Record<string, unknown> | null;

      switch (parsed.eventName) {
        case "start":
          setStatusNote(
            typeof payload?.message === "string"
              ? payload.message
              : "已接收语音，正在转写并流式生成追问。",
          );
          break;
        case "transcript": {
          const transcript = typeof payload?.transcript === "string" ? payload.transcript : "";
          if (!transcript.trim()) {
            break;
          }
          upsertChatMessage({
            messageId: transcriptMessageId,
            role: "USER",
            text: transcript,
            badge: "语音转写",
            scoreHint: null,
            audioObjectKey: typeof payload?.audioObjectKey === "string" ? payload.audioObjectKey : null,
            isStreaming: false,
          });
          setStatusNote("语音已完成转写，AI 正在流式生成下一轮追问...");
          break;
        }
        case "reply_delta": {
          const delta = typeof payload?.delta === "string" ? payload.delta : "";
          if (!delta) {
            break;
          }
          streamedFollowUp += delta;
          upsertChatMessage({
            messageId: assistantMessageId,
            role: "ASSISTANT",
            text: streamedFollowUp,
            badge: "正在生成追问",
            scoreHint: null,
            audioObjectKey: null,
            isStreaming: true,
          });
          scheduleStreamingAssistantVisualSettle(assistantMessageId, "语音追问");
          break;
        }
        case "done":
          clearStreamingMessageVisualSettleTimeout();
          if (payload?.result) {
            finalResult = payload.result as InterviewVoiceRoundtripResponse;
            streamCompleted = true;
          }
          break;
        case "error":
          throw new ApiClientError(
            typeof payload?.message === "string" ? payload.message : "流式语音面试失败",
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
        if (streamCompleted) {
          break;
        }
        boundaryIndex = buffer.indexOf("\n\n");
      }

      if (streamCompleted) {
        break;
      }

      if (done) {
        break;
      }
    }

    if (!streamCompleted && buffer.trim()) {
      consumeEventBlock(buffer);
    }

    if (!finalResult) {
      throw new ApiClientError("AI 已完成流式输出，但未返回最终结果。", 500, "SSE_DONE_MISSING");
    }

    const resolvedFinalResult = finalResult as InterviewVoiceRoundtripResponse;

    if (resolvedFinalResult.transcript?.trim()) {
      upsertChatMessage({
        messageId: transcriptMessageId,
        role: "USER",
        text: resolvedFinalResult.transcript,
        badge: "语音转写",
        scoreHint: resolvedFinalResult.scoreHint,
        audioObjectKey: resolvedFinalResult.audioObjectKey,
        isStreaming: false,
      });
    } else {
      removeChatMessage(transcriptMessageId);
    }

    if (resolvedFinalResult.followUpQuestion?.trim()) {
      upsertChatMessage({
        messageId: assistantMessageId,
        role: "ASSISTANT",
        text: resolvedFinalResult.followUpQuestion,
        badge: resolvedFinalResult.shouldFinish ? "结束提示" : "语音追问",
        scoreHint: null,
        audioObjectKey: null,
        isStreaming: false,
      });
    } else {
      removeChatMessage(assistantMessageId);
    }

    return resolvedFinalResult;
  };

  const handlePlayTts = async (playbackKey: string, text: string) => {
    const normalizedText = text.trim();
    if (!normalizedText) {
      return;
    }

    if (ttsPlayingKey === playbackKey) {
      stopTtsPlayback();
      return;
    }

    setErrorMessage(null);
    setTtsPendingKey(playbackKey);

    const playObjectUrl = async (objectUrl: string) => {
      stopTtsPlayback();
      const audio = new Audio(objectUrl);
      audio.onended = () => {
        if (ttsAudioRef.current === audio) {
          ttsAudioRef.current = null;
        }
        setTtsPlayingKey((current) => (current === playbackKey ? null : current));
      };
      audio.onerror = () => {
        if (ttsAudioRef.current === audio) {
          ttsAudioRef.current = null;
        }
        setTtsPlayingKey((current) => (current === playbackKey ? null : current));
        showErrorToast("语音播报播放失败，请稍后再试。");
      };
      ttsAudioRef.current = audio;
      await audio.play();
      setTtsPlayingKey(playbackKey);
    };

    try {
      const cachedObjectUrl = ttsObjectUrlCacheRef.current.get(playbackKey);
      if (cachedObjectUrl) {
        // 同一段文本的 TTS object URL 复用，避免重复生成音频和浪费额度。
        await playObjectUrl(cachedObjectUrl);
        return;
      }

      const response = await apiRequest<TextToSpeechResponse>("/ai/tts/synthesize", {
        method: "POST",
        body: JSON.stringify({
          text: normalizedText,
          stylePrompt: "Read aloud in a calm, supportive Chinese interview coach tone.",
          voiceName: "Zephyr",
          ...(sessionId ? { sessionId } : {}),
        }),
      });
      const objectUrl = window.URL.createObjectURL(createAudioBlob(response));
      ttsObjectUrlCacheRef.current.set(playbackKey, objectUrl);
      await playObjectUrl(objectUrl);
    } catch (error) {
      stopTtsPlayback();
      showErrorToast(
        error instanceof ApiClientError
          ? buildInterviewErrorMessage(error)
          : "语音播报生成失败，请稍后重试。",
      );
    } finally {
      setTtsPendingKey((current) => (current === playbackKey ? null : current));
    }
  };

  const togglePrepMaterial = (materialKey: PrepMaterialKey) => {
    if (autoPrepMaterialKeys.includes(materialKey)) {
      return;
    }
    const material = prepMaterialOptions.find((item) => item.key === materialKey);
    if (!material?.available) {
      return;
    }

    setSelectedPrepMaterials((current) => (
      current.includes(materialKey)
        ? current.filter((item) => item !== materialKey)
        : [...current, materialKey]
    ));
  };

  const applyRecommendedPrepMaterials = () => {
    setSelectedPrepMaterials(recommendedPrepMaterialKeys);
  };

  const toggleAnswerHelperCue = (cueKey: AnswerHelperCueKey) => {
    setSelectedAnswerHelperCueKeys((current) => {
      if (current.includes(cueKey)) {
        return current.length === 1 ? current : current.filter((item) => item !== cueKey);
      }

      return [...current, cueKey];
    });
  };

  const applyRecommendedAnswerHelperCues = () => {
    setSelectedAnswerHelperCueKeys(recommendedAnswerHelperCueKeys);
  };

  const togglePrepareDrawer = (drawerKey: PrepareDrawerKey) => {
    setExpandedPrepareDrawer((current) => (current === drawerKey ? null : drawerKey));
  };

  const renderExpandedPreparePanel = (drawerKey: PrepareDrawerKey) => {
    switch (drawerKey) {
      case "INTERVIEW_TYPE":
        return (
          <div className="grid gap-3 md:grid-cols-2">
            {interviewTypeOptions.map((item) => (
              <button
                key={item.value}
                type="button"
                onClick={() => setInterviewType(item.value)}
                className={joinClasses(
                  "rounded-[1.75rem] border px-5 py-4 text-left transition-all",
                  interviewType === item.value
                    ? "border-indigo-200 bg-indigo-50 shadow-sm"
                    : "border-slate-200 bg-slate-50 hover:border-slate-300 hover:bg-white",
                )}
              >
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <div className="text-base font-semibold text-slate-900">{item.label}</div>
                    <div className="mt-1.5 text-sm leading-7 text-slate-500">{item.detail}</div>
                  </div>
                  <span className="rounded-full bg-white px-2.5 py-1 text-xs font-semibold text-slate-500 shadow-sm">
                    {item.note}
                  </span>
                </div>
              </button>
            ))}
          </div>
        );
      case "INTERVIEWER_STYLE":
        return (
          <div className="grid gap-3 md:grid-cols-2">
            {interviewerStyleOptions.map((item) => (
              <button
                key={item.value}
                type="button"
                onClick={() => setInterviewerStyle(item.value)}
                className={joinClasses(
                  "rounded-[1.75rem] border px-5 py-4 text-left transition-all",
                  interviewerStyle === item.value
                    ? "border-teal-200 bg-teal-50 shadow-sm"
                    : "border-slate-200 bg-slate-50 hover:border-slate-300 hover:bg-white",
                )}
              >
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <div className="text-base font-semibold text-slate-900">{item.label}</div>
                    <div className="mt-1.5 text-sm leading-7 text-slate-500">{item.detail}</div>
                  </div>
                  <span className="rounded-full bg-white px-2.5 py-1 text-xs font-semibold text-slate-500 shadow-sm">
                    {item.cue}
                  </span>
                </div>
              </button>
            ))}
          </div>
        );
      case "DIFFICULTY":
        return (
          <div className="grid gap-2 sm:grid-cols-3">
            {difficultyOptions.map((item) => (
              <button
                key={item.value}
                type="button"
                onClick={() => setDifficulty(item.value)}
                className={joinClasses(
                  "rounded-[1.5rem] border px-4 py-4 text-left transition-all",
                  difficulty === item.value
                    ? "border-indigo-200 bg-indigo-50 shadow-sm"
                    : "border-slate-200 bg-slate-50 hover:border-slate-300 hover:bg-white",
                )}
              >
                <div className="text-base font-semibold text-slate-900">{item.label}</div>
                <div className="mt-1.5 text-sm leading-7 text-slate-500">{item.detail}</div>
              </button>
            ))}
          </div>
        );
      case "ANSWER_MODE":
        return (
          <div className="space-y-5">
            <div className={joinClasses("grid gap-3", isVoiceAnswerEntryEnabled || isLiveInterviewEntryEnabled ? "sm:grid-cols-3" : "sm:grid-cols-1")}>
              <button
                type="button"
                onClick={() => setAnswerMode("text")}
                className={joinClasses(
                  "rounded-[1.75rem] border px-5 py-5 text-left transition-all",
                  answerMode === "text"
                    ? "border-teal-200 bg-teal-50 shadow-sm"
                    : "border-slate-200 bg-slate-50 hover:border-slate-300 hover:bg-white",
                )}
              >
                <div className="flex items-center gap-3">
                  <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-white text-teal-600 shadow-sm">
                    <MessageSquare size={20} />
                  </div>
                  <div>
                    <div className="text-base font-semibold text-slate-900">文字回答</div>
                    <div className="mt-1.5 text-sm leading-7 text-slate-500">最稳定，适合先把结构讲清楚</div>
                  </div>
                </div>
              </button>
              {isVoiceAnswerEntryEnabled ? (
                <button
                  type="button"
                  onClick={() => setAnswerMode("voice")}
                  className={joinClasses(
                    "rounded-[1.75rem] border px-5 py-5 text-left transition-all",
                    answerMode === "voice"
                      ? "border-indigo-200 bg-indigo-50 shadow-sm"
                      : "border-slate-200 bg-slate-50 hover:border-slate-300 hover:bg-white",
                  )}
                >
                  <div className="flex items-center gap-3">
                    <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-white text-indigo-600 shadow-sm">
                      <Mic size={20} />
                    </div>
                    <div>
                      <div className="text-base font-semibold text-slate-900">语音回答</div>
                      <div className="mt-1.5 text-sm leading-7 text-slate-500">支持浏览器录音开始，也可补充音频文件</div>
                    </div>
                  </div>
                </button>
              ) : null}
              {isLiveInterviewEntryEnabled ? (
                <button
                  type="button"
                  onClick={() => setAnswerMode("live")}
                  className={joinClasses(
                    "rounded-[1.75rem] border px-5 py-5 text-left transition-all",
                    answerMode === "live"
                      ? "border-amber-200 bg-amber-50 shadow-sm"
                      : "border-slate-200 bg-slate-50 hover:border-slate-300 hover:bg-white",
                  )}
                >
                  <div className="flex items-center gap-3">
                    <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-white text-amber-600 shadow-sm">
                      <Headphones size={20} />
                    </div>
                    <div>
                      <div className="text-base font-semibold text-slate-900">Live 测试模式</div>
                      <div className="mt-1.5 text-sm leading-7 text-slate-500">独立 Python WS 直连 Gemini Live，结束后回灌正式复盘</div>
                    </div>
                  </div>
                </button>
              ) : null}
            </div>

            {isTextOnlyInterviewEntry ? (
              <div className="rounded-[1.5rem] border border-teal-100 bg-teal-50/80 px-4 py-3 text-sm leading-7 text-teal-700">
                当前仅开放文字模式，这一轮可以先把开场、自我介绍和项目主线讲顺。
              </div>
            ) : null}

            <motion.div
              layout
              transition={{ duration: 0.2, ease: "easeOut" }}
              className="overflow-hidden rounded-[1.75rem] border border-slate-200 bg-slate-50/80"
            >
              <AnimatePresence mode="wait" initial={false}>
                {answerMode === "voice" ? (
                  <motion.div
                    key="voice-mode-panel"
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0 }}
                    transition={{ duration: 0.16, ease: "easeOut" }}
                    className="p-5"
                  >
                    <div className="flex items-start justify-between gap-4">
                      <div>
                        <div className="text-base font-semibold text-slate-900">麦克风检测</div>
                        <p className="mt-2 text-[15px] leading-7 text-slate-600">{micMessage}</p>
                      </div>
                      <div
                        className={joinClasses(
                          "rounded-full border px-3 py-1.5 text-xs font-semibold shadow-sm",
                          micStatus === "ready"
                            ? "border-emerald-200 bg-emerald-50 text-emerald-700"
                            : micStatus === "blocked" || micStatus === "unsupported"
                              ? "border-amber-200 bg-amber-50 text-amber-700"
                              : micStatus === "checking"
                                ? "border-indigo-200 bg-indigo-50 text-indigo-700"
                                : "border-slate-200 bg-white text-slate-600",
                        )}
                      >
                        {deviceStatusLabel}
                      </div>
                    </div>

                    <div
                      className={joinClasses(
                        "relative mt-5 overflow-hidden rounded-[1.75rem] border p-5",
                        micStatus === "ready"
                          ? "border-emerald-200/70 bg-[linear-gradient(135deg,rgba(236,253,245,0.96),rgba(255,255,255,0.98))]"
                          : micStatus === "blocked" || micStatus === "unsupported"
                            ? "border-amber-200/70 bg-[linear-gradient(135deg,rgba(255,251,235,0.96),rgba(255,255,255,0.98))]"
                            : "border-indigo-200/70 bg-[linear-gradient(135deg,rgba(238,242,255,0.96),rgba(255,255,255,0.98))]",
                      )}
                    >
                      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_top_right,rgba(255,255,255,0.78),transparent_40%)]" />
                      <div className="relative flex items-center gap-5">
                        <div
                          className={joinClasses(
                            "flex h-20 w-20 shrink-0 items-center justify-center rounded-[1.75rem] border shadow-sm",
                            micStatus === "ready"
                              ? "border-emerald-200 bg-white text-emerald-600"
                              : micStatus === "blocked" || micStatus === "unsupported"
                                ? "border-amber-200 bg-white text-amber-600"
                                : "border-indigo-200 bg-white text-indigo-600",
                          )}
                        >
                          {micStatus === "blocked" || micStatus === "unsupported" ? <AlertCircle size={28} /> : <Mic size={28} />}
                        </div>
                        <div className="min-w-0 flex-1">
                          <div className="text-[12px] font-semibold tracking-[0.2em] text-slate-400">语音检测</div>
                          <div className="mt-3 text-xl font-semibold text-slate-900">{deviceStatusHeadline}</div>
                          <p className="mt-2 text-[15px] leading-7 text-slate-600">{deviceStatusSummary}</p>
                        </div>
                      </div>

                      <div className="relative mt-6 flex h-16 items-end gap-1.5 overflow-hidden rounded-2xl bg-white/70 px-4 py-3 shadow-[inset_0_1px_0_rgba(255,255,255,0.72)]">
                        {microphoneWaveLevels.map((level, index) => (
                          <motion.span
                            key={`${level}-${index}`}
                            aria-hidden="true"
                            className={joinClasses(
                              "block origin-bottom rounded-full",
                              micStatus === "ready"
                                ? "bg-emerald-400"
                                : micStatus === "checking"
                                  ? "bg-indigo-400"
                                  : "bg-slate-300",
                            )}
                            style={{
                              width: "0.375rem",
                              height: `${level * 100}%`,
                              opacity: micStatus === "ready" ? 1 : micStatus === "checking" ? 0.8 : 0.45,
                            }}
                            animate={
                              micStatus === "ready"
                                ? { scaleY: [0.48, 1, 0.62] }
                                : micStatus === "checking"
                                  ? { scaleY: [0.32, 0.72, 0.42], opacity: [0.45, 0.92, 0.55] }
                                  : { scaleY: 0.26 }
                            }
                            transition={
                              micStatus === "ready"
                                ? { duration: 1.05, repeat: Infinity, repeatType: "mirror", delay: index * 0.07, ease: "easeInOut" }
                                : micStatus === "checking"
                                  ? { duration: 0.9, repeat: Infinity, repeatType: "mirror", delay: index * 0.05, ease: "easeInOut" }
                                  : { duration: 0.15, ease: "easeOut" }
                            }
                          />
                        ))}
                      </div>
                    </div>

                    <div className="mt-4 rounded-[1.5rem] border border-dashed border-slate-200 bg-white/90 px-4 py-3 text-[15px] leading-7 text-slate-500">
                      {prepStartSuggestion}
                    </div>

                    <div className="mt-4 flex flex-wrap gap-3">
                      <button
                        type="button"
                        onClick={handleMicCheck}
                        className="inline-flex items-center rounded-full bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 shadow-sm transition-colors hover:text-indigo-600"
                      >
                        <Headphones size={16} className="mr-2" />
                        检测麦克风
                      </button>
                      {micStatus !== "ready" ? (
                        <button
                          type="button"
                          onClick={() => setAnswerMode("text")}
                          className="inline-flex items-center rounded-full border border-amber-200 bg-amber-50 px-4 py-2.5 text-sm font-semibold text-amber-700 transition-colors hover:bg-amber-100"
                        >
                          先用文字模式开始
                        </button>
                      ) : null}
                    </div>
                  </motion.div>
                ) : answerMode === "live" ? (
                  <motion.div
                    key="live-mode-panel"
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0 }}
                    transition={{ duration: 0.16, ease: "easeOut" }}
                    className="p-5"
                  >
                    <div className="flex items-start justify-between gap-4">
                      <div>
                        <div className="text-base font-semibold text-slate-900">Live 测试模式设备确认</div>
                        <p className="mt-2 text-[15px] leading-7 text-slate-600">
                          当前会在创建会话后自动连接独立 Python Live 服务，实时采集麦克风并接收 Gemini 语音输出；结束时再把 transcript 导回正式复盘链路。
                        </p>
                      </div>
                      <div
                        className={joinClasses(
                          "rounded-full border px-3 py-1.5 text-xs font-semibold shadow-sm",
                          micStatus === "ready"
                            ? "border-emerald-200 bg-emerald-50 text-emerald-700"
                            : micStatus === "blocked" || micStatus === "unsupported"
                              ? "border-amber-200 bg-amber-50 text-amber-700"
                              : micStatus === "checking"
                                ? "border-indigo-200 bg-indigo-50 text-indigo-700"
                                : "border-slate-200 bg-white text-slate-600",
                        )}
                      >
                        {deviceStatusLabel}
                      </div>
                    </div>

                    <div className="mt-5 grid gap-4 xl:grid-cols-[minmax(0,1.15fr)_minmax(17rem,0.85fr)]">
                      <div
                        className={joinClasses(
                          "relative overflow-hidden rounded-[1.75rem] border p-5",
                          micStatus === "ready"
                            ? "border-emerald-200/70 bg-[linear-gradient(135deg,rgba(236,253,245,0.96),rgba(255,255,255,0.98))]"
                            : micStatus === "blocked" || micStatus === "unsupported"
                              ? "border-amber-200/70 bg-[linear-gradient(135deg,rgba(255,251,235,0.96),rgba(255,255,255,0.98))]"
                              : "border-indigo-200/70 bg-[linear-gradient(135deg,rgba(238,242,255,0.96),rgba(255,255,255,0.98))]",
                        )}
                      >
                        <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_top_right,rgba(255,255,255,0.78),transparent_40%)]" />
                        <div className="relative flex items-center gap-5">
                          <div
                            className={joinClasses(
                              "flex h-20 w-20 shrink-0 items-center justify-center rounded-[1.75rem] border shadow-sm",
                              micStatus === "ready"
                                ? "border-emerald-200 bg-white text-emerald-600"
                                : micStatus === "blocked" || micStatus === "unsupported"
                                  ? "border-amber-200 bg-white text-amber-600"
                                  : "border-indigo-200 bg-white text-indigo-600",
                            )}
                          >
                            {micStatus === "blocked" || micStatus === "unsupported" ? <AlertCircle size={28} /> : <Headphones size={28} />}
                          </div>
                          <div className="min-w-0 flex-1">
                            <div className="text-xl font-semibold text-slate-950">{deviceStatusHeadline}</div>
                            <p className="mt-2 text-[15px] leading-7 text-slate-600">{deviceStatusSummary}</p>
                          </div>
                        </div>

                        <div className="mt-4 rounded-[1.35rem] border border-white/85 bg-white/88 px-4 py-3">
                          <div className="text-sm font-semibold text-slate-500">开始方式</div>
                          <div className="mt-2 text-[15px] leading-7 text-slate-600">实时麦克风 + 独立 Python WebSocket + Gemini Live</div>
                        </div>
                      </div>

                      <div className="rounded-[1.75rem] border border-slate-200 bg-white/90 p-5 shadow-sm">
                        <div className="text-base font-semibold text-slate-900">测试模式提示</div>
                        <div className="mt-3 space-y-3 text-sm leading-7 text-slate-600">
                          <p>当前属于测试模式，会先保留现有 `stt / tts` 链路不动。</p>
                          <p>Live 过程中的转写、状态和 token 统计会直接显示在会话页。</p>
                          <p>如果中途手动重连，页面 transcript 会保留，但 Gemini 侧会开启新的 Live 会话。</p>
                        </div>
                      </div>
                    </div>

                    <div className="mt-4 rounded-[1.5rem] border border-dashed border-slate-200 bg-white/90 px-4 py-3 text-[15px] leading-7 text-slate-500">
                      {prepStartSuggestion}
                    </div>

                    <div className="mt-4 flex flex-wrap gap-3">
                      <button
                        type="button"
                        onClick={handleMicCheck}
                        className="inline-flex items-center rounded-full bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 shadow-sm transition-colors hover:text-indigo-600"
                      >
                        <Headphones size={16} className="mr-2" />
                        检测 Live 麦克风
                      </button>
                      {micStatus !== "ready" ? (
                        <button
                          type="button"
                          onClick={() => setAnswerMode("text")}
                          className="inline-flex items-center rounded-full border border-amber-200 bg-amber-50 px-4 py-2.5 text-sm font-semibold text-amber-700 transition-colors hover:bg-amber-100"
                        >
                          先用文字模式开始
                        </button>
                      ) : null}
                    </div>
                  </motion.div>
                ) : (
                  <motion.div
                    key="text-mode-panel"
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0 }}
                    transition={{ duration: 0.16, ease: "easeOut" }}
                    className="p-5"
                  >
                    <div className="flex items-start gap-4">
                      <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl border border-teal-100 bg-white text-teal-600 shadow-sm">
                        <MessageSquare size={22} />
                      </div>
                      <div>
                        <div className="text-base font-semibold text-slate-900">文字模式更适合先把表达讲顺</div>
                        <p className="mt-2 text-[15px] leading-7 text-slate-600">
                          {isTextOnlyInterviewEntry
                            ? "当前仅保留文字模式，不依赖设备权限，适合先稳定开场、自我介绍和项目主线。"
                            : "不依赖设备权限，适合先稳定开场、自我介绍和项目主线。完成一轮后，再继续提高练习强度会更容易发现节奏问题。"}
                        </p>
                      </div>
                    </div>

                    <div className="mt-4 rounded-[1.5rem] border border-dashed border-slate-200 bg-white/90 px-4 py-3 text-[15px] leading-7 text-slate-500">
                      {prepStartSuggestion}
                    </div>
                  </motion.div>
                )}
              </AnimatePresence>
            </motion.div>
          </div>
        );
      case "PREP_MATERIALS":
        return (
          <div className="space-y-5">
            <div className="flex flex-col gap-3 xl:flex-row xl:items-end xl:justify-between">
              <div className="max-w-2xl">
                <div className="text-lg font-semibold text-slate-900">选择希望额外带入的个人资料</div>
                <p className="mt-1.5 text-sm leading-7 text-slate-500">
                  顶部岗位信息会自动同步，这里只配置额外参考的个人资料。
                </p>
                <div className="mt-3 flex flex-wrap gap-x-4 gap-y-2 text-sm text-slate-500">
                  <span>已选 {selectedOptionalPrepMaterials.length} 项</span>
                  <span>可选 {optionalAvailablePrepMaterialCount} 项</span>
                  {prepMaterialMissingCount > 0 ? <span>待补 {prepMaterialMissingCount} 项</span> : null}
                </div>
              </div>
              <div className="flex flex-wrap gap-2 xl:justify-end">
                <button
                  type="button"
                  onClick={applyRecommendedPrepMaterials}
                  disabled={recommendedPrepMaterialKeys.length === 0}
                  className="inline-flex items-center rounded-full border border-indigo-200 bg-white px-4 py-2 text-sm font-semibold text-indigo-600 shadow-sm transition-colors hover:bg-indigo-50 disabled:cursor-not-allowed disabled:border-slate-200 disabled:text-slate-400"
                >
                  <Sparkles size={14} className="mr-2" />
                  推荐组合
                </button>
                <button
                  type="button"
                  onClick={() => setSelectedPrepMaterials([])}
                  disabled={selectedOptionalPrepMaterials.length === 0}
                  className="inline-flex items-center rounded-full border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-600 shadow-sm transition-colors hover:border-slate-300 disabled:cursor-not-allowed disabled:text-slate-400"
                >
                  清空选择
                </button>
              </div>
            </div>

            <div className="grid gap-5 xl:grid-cols-[minmax(0,1.12fr)_minmax(19rem,0.82fr)]">
              <div className="space-y-4">
                {prepMaterialGroups.map((group) => (
                  <div key={group.title} className="rounded-[1.75rem] border border-slate-200 bg-white/92 p-5 shadow-sm">
                    <div className="flex items-center justify-between gap-3">
                      <div className="text-sm font-semibold text-slate-900">{group.title}</div>
                      <div className="text-xs font-medium text-slate-400">{group.keys.length} 项</div>
                    </div>

                    <div className="mt-3 space-y-3">
                      {group.keys.map((materialKey) => {
                        const item = manualPrepMaterialOptions.find((option) => option.key === materialKey);
                        if (!item) {
                          return null;
                        }

                        const isSelected = selectedOptionalPrepMaterials.some((selectedItem) => selectedItem.key === item.key);
                        const isPreviewing = resolvedActivePrepMaterialPreviewKey === item.key;

                        return (
                          <div
                            key={item.key}
                            className={joinClasses(
                              "grid gap-3 rounded-[1.35rem] border p-4 transition-all lg:grid-cols-[minmax(0,1fr)_auto] lg:items-center",
                              isPreviewing
                                ? "border-indigo-200 bg-indigo-50/60 shadow-sm"
                                : "border-slate-200 bg-slate-50/75",
                            )}
                          >
                            <button
                              type="button"
                              onClick={() => setActivePrepMaterialPreviewKey(item.key)}
                              className="min-w-0 text-left"
                            >
                              <div className="flex flex-wrap items-center gap-2">
                                <div className="text-[15px] font-semibold text-slate-900">{item.label}</div>
                                <span
                                  className={joinClasses(
                                    "inline-flex items-center rounded-full px-2.5 py-1 text-[11px] font-semibold",
                                    isSelected && item.available
                                      ? "bg-indigo-100 text-indigo-700"
                                      : item.available
                                        ? "bg-slate-100 text-slate-500"
                                        : "bg-amber-100 text-amber-700",
                                  )}
                                >
                                  {isSelected && item.available ? "已带入" : item.available ? "可带入" : "待补充"}
                                </span>
                              </div>
                            </button>

                            <button
                              type="button"
                              onClick={() => {
                                setActivePrepMaterialPreviewKey(item.key);
                                togglePrepMaterial(item.key);
                              }}
                              disabled={!item.available}
                              className={joinClasses(
                                "inline-flex min-w-[6rem] items-center justify-center rounded-full px-4 py-2 text-sm font-semibold transition-colors",
                                isSelected && item.available
                                  ? "bg-indigo-600 text-white shadow-sm hover:bg-indigo-500"
                                  : item.available
                                    ? "border border-slate-200 bg-white text-slate-700 hover:border-slate-300 hover:text-indigo-600"
                                    : "cursor-not-allowed border border-slate-200 bg-slate-100 text-slate-400",
                              )}
                            >
                              {isSelected && item.available ? "移除" : item.available ? "带入" : "待补"}
                            </button>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                ))}
              </div>

              <div className="xl:sticky xl:top-0">
                <div className="rounded-[1.75rem] border border-slate-200 bg-slate-50/85 p-5 shadow-sm">
                  <div>
                    <div className="text-[11px] tracking-[0.18em] text-slate-400">资料摘要</div>
                    <div className="mt-2 text-lg font-semibold text-slate-900">
                      {activePrepMaterialOption?.label ?? "选择资料"}
                    </div>
                  </div>

                  <div className="mt-4 rounded-[1.4rem] border border-slate-200 bg-white p-4">
                    <p className="text-sm leading-7 text-slate-600">{activePrepMaterialPreviewText}</p>

                    {activePrepMaterialPreviewBullets?.length ? (
                      <div className="mt-4 space-y-2">
                        {activePrepMaterialPreviewBullets.map((bullet) => (
                          <div
                            key={bullet}
                            className="rounded-[1rem] border border-slate-200 bg-slate-50/80 px-3 py-2 text-sm leading-6 text-slate-600"
                          >
                            {bullet}
                          </div>
                        ))}
                      </div>
                    ) : null}

                    {activePrepMaterialPreviewChips?.length ? (
                      <div className="mt-4 flex flex-wrap gap-2">
                        {activePrepMaterialPreviewChips.map((chip) => (
                          <span
                            key={chip}
                            className="rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-600 shadow-sm"
                          >
                            {chip}
                          </span>
                        ))}
                      </div>
                    ) : null}
                  </div>
                </div>
              </div>
            </div>
          </div>
        );
      case "ANSWER_HELPER":
        return (
          <>
            <div className="flex items-start justify-between gap-4">
              <div>
                <div className="text-base font-semibold text-slate-900">先决定要不要把轻提醒带进面试</div>
                <p className="mt-1.5 text-[15px] leading-7 text-slate-500">
                  它只负责给出提示，不会代替你生成答案。
                </p>
              </div>
              <button
                type="button"
                onClick={() => setAnswerHelperEnabled((current) => !current)}
                className={joinClasses(
                  "inline-flex items-center gap-3 rounded-full px-3 py-2 text-sm font-semibold shadow-sm transition-colors",
                  answerHelperEnabled
                    ? "bg-indigo-600 text-white hover:bg-indigo-700"
                    : "border border-slate-200 bg-white text-slate-600 hover:border-indigo-200 hover:text-indigo-600",
                )}
              >
                <span
                  className={joinClasses(
                    "relative inline-flex h-6 w-11 items-center rounded-full transition-colors",
                    answerHelperEnabled ? "bg-white/30" : "bg-slate-200",
                  )}
                >
                  <span
                    className={joinClasses(
                      "inline-flex h-5 w-5 rounded-full bg-white shadow-sm transition-transform",
                      answerHelperEnabled ? "translate-x-5" : "translate-x-0.5",
                    )}
                  />
                </span>
                {answerHelperEnabled ? "辅助器已开启" : "辅助器已关闭"}
              </button>
            </div>

            <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
              {answerHelperOptions.map((item) => {
                const isSelected = selectedAnswerHelperCueKeys.includes(item.key);
                return (
                  <button
                    key={item.key}
                    type="button"
                    onClick={() => toggleAnswerHelperCue(item.key)}
                    disabled={!answerHelperEnabled}
                    className={joinClasses(
                      "min-h-[9.4rem] rounded-[1.5rem] border p-4 text-left transition-all",
                      answerHelperEnabled && isSelected
                        ? "border-indigo-200 bg-white shadow-sm"
                        : answerHelperEnabled
                          ? "border-slate-200 bg-white/70 hover:border-slate-300 hover:bg-white"
                          : "cursor-not-allowed border-slate-200 bg-slate-100/80 text-slate-400",
                    )}
                  >
                    <div className="flex h-full flex-col justify-between gap-3">
                      <div className="flex items-center justify-between gap-3">
                        <div className="min-w-0 text-[17px] font-semibold text-slate-900">{item.label}</div>
                        <span
                          className={joinClasses(
                            "shrink-0 rounded-full px-2.5 py-1 text-xs font-semibold",
                            answerHelperEnabled && isSelected
                              ? "bg-indigo-100 text-indigo-700"
                              : "bg-slate-100 text-slate-500",
                          )}
                        >
                          {answerHelperEnabled && isSelected ? "启用中" : "未启用"}
                        </span>
                      </div>
                      <div className="text-sm leading-7 text-slate-500">{item.detail}</div>
                    </div>
                  </button>
                );
              })}
            </div>

            <div className="mt-4 flex flex-wrap items-center gap-2">
              <button
                type="button"
                onClick={applyRecommendedAnswerHelperCues}
                disabled={!answerHelperEnabled}
                className="inline-flex items-center rounded-full border border-indigo-200 bg-white px-4 py-2 text-sm font-semibold text-indigo-600 shadow-sm transition-colors hover:bg-indigo-50 disabled:cursor-not-allowed disabled:border-slate-200 disabled:text-slate-400"
              >
                <Sparkles size={14} className="mr-2" />
                按当前面试类型推荐提醒
              </button>
              <div className="inline-flex items-center rounded-full border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-slate-500">
                已启用 {enabledAnswerHelperOptions.length} 项轻提醒
              </div>
            </div>

            <div className="mt-4 rounded-[1.5rem] border border-dashed border-slate-200 bg-white/80 px-4 py-3 text-sm leading-7 text-slate-500">
              {answerHelperEnabled
                ? `当前会按更像陪练的方式提供 ${answerHelperSummaryText}。`
                : "当前会以更干净的面试体验开始，更接近纯问答练习；重新开启后会保留你当前选中的提醒项。"}
            </div>
          </>
        );
      default:
        return null;
    }
  };

  const handleDismissPrivacyNoticeOnce = () => {
    setIsPrivacyNoticeOpen(false);
  };

  const handleDismissPrivacyNoticeToday = () => {
    try {
      const todayStamp = getCurrentDateStamp();
      window.localStorage.setItem(interviewPrivacyNoticeDailyKey, todayStamp);
    } catch {
      // ignore storage write failures and still close for current render
    }
    setIsPrivacyNoticeOpen(false);
  };

  const handleOpenStartConfirm = () => {
    if (!targetRole.trim()) {
      showErrorToast("请先填写一个明确的目标岗位，再开始这轮模拟面试。");
      return;
    }

    if (!isAnswerModeEntryEnabled(answerMode, interviewEntryOptions)) {
      closeLiveClient();
      setAnswerMode("text");
      setExpandedPrepareDrawer("ANSWER_MODE");
      showErrorToast("当前仅开放文字模式，请按文字模式开始这一轮。");
      return;
    }

    if (isAudioAnswerMode && micStatus === "idle") {
      showErrorToast(isLiveAnswerMode
        ? "Live 模式下，请先在设备状态里完成一次检测或确认，再进入开始前确认。"
        : "语音模式下，请先在设备状态里完成一次检测或确认，再进入开始前确认。");
      setExpandedPrepareDrawer("ANSWER_MODE");
      return;
    }

    setErrorMessage(null);
    setIsStartConfirmOpen(true);
  };

  const handleStartSession = async () => {
    if (!isAnswerModeEntryEnabled(answerMode, interviewEntryOptions)) {
      closeLiveClient();
      setAnswerMode("text");
      setExpandedPrepareDrawer("ANSWER_MODE");
      showErrorToast("当前仅开放文字模式，请按文字模式开始这一轮。");
      return;
    }

    const generation = practiceGenerationRef.current;
    setIsStartConfirmOpen(false);
    setSessionPending(true);
    setErrorMessage(null);
    setSummary(null);
    setLatestCoachFeedback(null);
    setLatestScoreHint(null);
    setLatestAnswerHelperAnalysis(null);
    setAnswerHelperAnalysisPending(false);
    setAnswerHelperAnalysisError(null);
    answerHelperAnalysisSourceKeyRef.current = null;
    setStatusNote("AI 正在生成这轮开场问题...");

    try {
      // 创建会话时一次性提交面试类型、回答模式、准备材料和可选简历快照。
      const response = await apiRequest<InterviewSessionCreateResponse>("/ai/interview/sessions", {
        method: "POST",
        body: JSON.stringify({
          targetRole: targetRole.trim(),
          mode: "INTERVIEW_TEXT",
          ...(selectedResumeRecordId ? { resumeRecordId: selectedResumeRecordId } : {}),
          sessionContext: {
            interviewType,
            interviewerStyle,
            difficulty,
            answerMode: answerMode === "voice" ? "VOICE" : answerMode === "live" ? "LIVE" : "TEXT",
            ...(targetCompany.trim() ? { targetCompany: targetCompany.trim() } : {}),
            ...(targetJobDescription.trim() ? { targetJobDescription: targetJobDescription.trim() } : {}),
            ...(sessionContextPrepMaterialKeys.length > 0 ? { prepMaterialKeys: sessionContextPrepMaterialKeys } : {}),
            answerHelperEnabled,
            answerHelperCueKeys: answerHelperEnabled ? selectedAnswerHelperCueKeys : [],
            ...(sessionPromptContext ? { promptContext: sessionPromptContext } : {}),
          },
        }),
      });

      if (!isPracticeCurrent(generation)) {
        return;
      }

      setSessionId(response.sessionId);
      setSessionCreatedAt(new Date().toISOString());
      setSessionContextSource("local");
      setRestoredResumeSnapshot(null);
      setRestoredPromptContext(null);
      setRestoredPrepMaterialKeys(null);
      setReplyRoundLimit(response.replyRoundLimit);
      setReplyRoundUsed(0);
      setChargedPoints(response.chargedPoints);
      setPointsBalance(response.pointsBalanceAfterReserve);
      setQuotaUnitsReserved(response.quotaUnitsReserved);
      liveTranscriptRef.current = [];
      setMessages(response.firstQuestion.trim()
        ? [
            {
              id: createLocalId("assistant"),
              role: "ASSISTANT",
              text: response.firstQuestion,
              createdAt: new Date().toISOString(),
              badge: "开场问题",
              scoreHint: null,
              audioObjectKey: null,
              isStreaming: false,
            },
          ]
        : []);
      setLatestAnswerHelperAnalysis(null);
      setAnswerHelperAnalysisPending(false);
      setAnswerHelperAnalysisError(null);
      answerHelperAnalysisSourceKeyRef.current = null;
      resetLiveTransportState();
      setStatusNote(
        answerMode === "live"
          ? `会话已创建。当前按“${selectedInterviewType.label} / ${selectedInterviewerStyle.label}”练习，页面即将自动连接 Gemini Live。`
          : answerMode === "voice"
            ? `会话已创建。当前按“${selectedInterviewType.label} / ${selectedInterviewerStyle.label}”练习，${answerHelperEnabled ? `回答辅助器已开启（${answerHelperSummaryText}）` : "回答辅助器未开启"}。现在可以直接用浏览器录一段 webm 音频提交，或继续手动上传音频文件。`
            : `会话已创建。当前按“${selectedInterviewType.label} / ${selectedInterviewerStyle.label}”练习，${answerHelperEnabled ? `回答辅助器已开启（${answerHelperSummaryText}）` : "回答辅助器未开启"}，直接开始第一轮回答吧。`,
      );
      navigateToInterviewStage("session", response.sessionId);
    } catch (requestError) {
      if (!isPracticeCurrent(generation)) {
        return;
      }
      showErrorToast(buildInterviewErrorMessage(requestError));
      setStatusNote(null);
    } finally {
      if (isPracticeCurrent(generation)) {
        setSessionPending(false);
      }
    }
  };

  const handleTextReply = async () => {
    if (!sessionId || !draftAnswer.trim()) {
      return;
    }

    const answer = draftAnswer.trim();
    const generation = practiceGenerationRef.current;
    const assistantMessageId = createLocalId("assistant-stream");
    setSessionPending(true);
    setErrorMessage(null);
    setStatusNote("AI 正在分析你的回答并流式生成下一轮追问...");
    setMessages((current) => [
      ...current,
      {
        id: createLocalId("user"),
        role: "USER",
        text: answer,
        createdAt: new Date().toISOString(),
        badge: "我的回答",
        scoreHint: null,
        audioObjectKey: null,
        isStreaming: false,
      },
    ]);
    clearStreamingMessageVisualSettleTimeout();
    appendStreamingAssistantPlaceholder(assistantMessageId);
    setDraftAnswer("");

    try {
      const response = await runInterviewReplyStream(sessionId, answer, assistantMessageId);

      if (!isPracticeCurrent(generation)) {
        return;
      }

      setReplyRoundUsed((current) => current + 1);
      setLatestCoachFeedback(response.coachFeedback || "这一轮没有额外点评，建议直接继续下一轮。");
      setLatestScoreHint(response.scoreHint);

      if (response.summary || response.shouldFinish) {
        scheduleAutoFinishReportTransition(generation, sessionId, response.summary);
      } else {
        setStatusNote("下一轮追问已经准备好了，可以继续作答。");
      }
    } catch (requestError) {
      if (!isPracticeCurrent(generation)) {
        return;
      }
      clearStreamingMessageVisualSettleTimeout();
      removeChatMessage(assistantMessageId);
      showErrorToast(buildInterviewErrorMessage(requestError));
      setStatusNote("本轮提交没有成功落到服务端，可以调整内容后再试一次。");
    } finally {
      if (isPracticeCurrent(generation)) {
        setSessionPending(false);
      }
    }
  };

  const handleDraftAnswerKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.nativeEvent.isComposing) {
      return;
    }

    if (event.key === "Enter" && (event.ctrlKey || event.metaKey)) {
      event.preventDefault();
      void handleTextReply();
    }
  };

  const handleVoiceFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0] ?? null;
    recordingStopModeRef.current = "discard";
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== "inactive") {
      mediaRecorderRef.current.stop();
    }
    setVoiceFile(file);
    setErrorMessage(null);
    if (file) {
      setStatusNote("音频文件已就绪，可以直接提交到语音 roundtrip。");
    }
  };

  const handleVoiceRoundtrip = async (fileOverride?: File, statusHint?: string) => {
    const audioFile = fileOverride ?? voiceFile;
    if (!sessionId || !audioFile) {
      return;
    }

    const generation = practiceGenerationRef.current;
    const transcriptMessageId = createLocalId("voice-user-stream");
    const assistantMessageId = createLocalId("assistant-stream");

    setSessionPending(true);
    setErrorMessage(null);
    setStatusNote(statusHint || "正在转写音频并流式生成下一轮追问...");
    clearStreamingMessageVisualSettleTimeout();
    appendStreamingAssistantPlaceholder(assistantMessageId);

    try {
      const response = await runVoiceRoundtripStream(sessionId, audioFile, transcriptMessageId, assistantMessageId);

      if (!isPracticeCurrent(generation)) {
        return;
      }

      setReplyRoundUsed((current) => current + 1);
      setLatestCoachFeedback(response.coachFeedback || "语音 roundtrip 已完成，建议继续下一轮。");
      setLatestScoreHint(response.scoreHint);

      if (response.summary || response.shouldFinish) {
        scheduleAutoFinishReportTransition(generation, sessionId, response.summary);
      } else {
        setStatusNote("语音 roundtrip 已完成，下一轮追问已经生成。");
      }
      setVoiceFile(null);
      if (voiceFileInputRef.current) {
        voiceFileInputRef.current.value = "";
      }
    } catch (requestError) {
      if (!isPracticeCurrent(generation)) {
        return;
      }
      clearStreamingMessageVisualSettleTimeout();
      removeChatMessage(transcriptMessageId);
      removeChatMessage(assistantMessageId);
      showErrorToast(buildInterviewErrorMessage(requestError));
      setStatusNote("这段语音没有成功完成转写或追问生成，可以换文件后再试。");
    } finally {
      if (isPracticeCurrent(generation)) {
        setSessionPending(false);
      }
    }
  };

  const handleStartBrowserRecording = async () => {
    if (!sessionId) {
      showErrorToast("请先创建一轮面试会话，再开始录音。");
      return;
    }

    if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === "undefined") {
      setMicStatus("unsupported");
      setMicMessage("当前浏览器不支持 MediaRecorder 录音，请改用手动文件上传。");
      return;
    }

    if (!preferredRecordingMimeType) {
      setMicStatus("unsupported");
      setMicMessage("当前浏览器不支持 Gemini 兼容的 webm 录音，请改用 Chrome/Edge 或手动上传文件。");
      return;
    }

    try {
      const generation = practiceGenerationRef.current;
      setErrorMessage(null);
      setVoiceFile(null);
      setSessionPending(false);
      if (voiceFileInputRef.current) {
        voiceFileInputRef.current.value = "";
      }

      const stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
        },
      });
      const recorder = new MediaRecorder(stream, {
        mimeType: preferredRecordingMimeType,
      });

      recordingStreamRef.current = stream;
      mediaRecorderRef.current = recorder;
      recordingChunksRef.current = [];
      recordingStopModeRef.current = "save";
      recordingStartedAtRef.current = Date.now();
      setRecordingMimeType(preferredRecordingMimeType);

      recorder.ondataavailable = (event) => {
        if (event.data.size > 0) {
          recordingChunksRef.current.push(event.data);
        }
      };

      recorder.onerror = () => {
        if (!isPracticeCurrent(generation)) {
          recordingStreamRef.current?.getTracks().forEach((track) => track.stop());
          recordingStreamRef.current = null;
          mediaRecorderRef.current = null;
          recordingChunksRef.current = [];
          recordingStartedAtRef.current = null;
          return;
        }
        recordingStopModeRef.current = "discard";
        recordingStartedAtRef.current = null;
        setIsRecording(false);
        setRecordingElapsedMs(0);
        setMicStatus("blocked");
        setMicMessage("录音过程中发生异常，当前可以重试或改用手动上传。");
        setStatusNote("浏览器录音异常，本轮没有成功拿到可提交的音频。");
        showErrorToast("浏览器录音失败，请重试一次或改用手动上传。");
        recordingStreamRef.current?.getTracks().forEach((track) => track.stop());
        recordingStreamRef.current = null;
        mediaRecorderRef.current = null;
        recordingChunksRef.current = [];
      };

      recorder.onstop = () => {
        const stopMode = recordingStopModeRef.current;
        const chunks = [...recordingChunksRef.current];

        recordingStopModeRef.current = "save";
        recordingChunksRef.current = [];
        recordingStartedAtRef.current = null;
        setIsRecording(false);
        setRecordingElapsedMs(0);
        recordingStreamRef.current?.getTracks().forEach((track) => track.stop());
        recordingStreamRef.current = null;
        mediaRecorderRef.current = null;

        if (!isPracticeCurrent(generation)) {
          return;
        }

        if (!chunks.length || stopMode === "discard") {
          if (stopMode === "discard") {
            setMicMessage("录音已取消，你可以重新录制或改用手动上传。");
            setStatusNote("本次录音已取消，没有提交到后端。");
          } else {
            setMicMessage("没有采集到有效音频，请重试一次。");
            setStatusNote("本次录音没有拿到可用音频，可以直接重新录制。");
          }
          return;
        }

        const blob = new Blob(chunks, { type: preferredRecordingMimeType });
        const file = new File([blob], `interview-answer-${Date.now()}.webm`, {
          type: preferredRecordingMimeType,
        });

        setVoiceFile(file);
        setMicStatus("ready");

        if (stopMode === "upload") {
          setMicMessage(`录音已结束，正在按 ${preferredRecordingMimeType} 提交给语音 roundtrip。`);
          void handleVoiceRoundtrip(file, "浏览器已完成 webm 录音，正在提交并生成追问...");
          return;
        }

        setMicMessage("录音已结束，webm 文件已经准备好。");
        setStatusNote("录音已结束，你可以提交这段音频，或直接重新录制。");
      };

      recorder.start();
      setMicStatus("ready");
      setIsRecording(true);
      setRecordingElapsedMs(0);
      setMicMessage(`已开始录音，当前将以 ${preferredRecordingMimeType} 格式提交。`);
      setStatusNote("正在录音，结束后可直接按 webm 格式提交到语音 roundtrip。");
    } catch {
      setMicStatus("blocked");
      setMicMessage("没有拿到麦克风权限，本轮仍可先使用手动文件上传。");
      showErrorToast("浏览器无法访问麦克风，请允许权限后重试，或改用手动上传。");
    }
  };

  const handleStopBrowserRecording = (mode: "upload" | "save" | "discard") => {
    const recorder = mediaRecorderRef.current;

    if (!recorder) {
      return;
    }

    recordingStopModeRef.current = mode;

    if (mode === "upload") {
      setStatusNote("正在结束录音并准备提交 webm 音频...");
    } else if (mode === "discard") {
      setStatusNote("正在取消这次录音...");
    } else {
      setStatusNote("正在结束录音并保存本地结果...");
    }

    if (recorder.state !== "inactive") {
      recorder.stop();
    }
  };

  const handleGenerateSummary = async () => {
    if (!sessionId) {
      return;
    }

    if (sessionPending) {
      setStatusNote("当前这轮回答还在提交中，请等 AI 返回后再生成复盘。");
      return;
    }

    if (summary) {
      navigateToInterviewStage("report", sessionId);
      return;
    }

    if (isLiveAnswerMode) {
      if (liveFinishPending) {
        setStatusNote("当前正在等待 Live 收尾或 transcript 导入完成，请稍候。");
        return;
      }

      if (liveClientRef.current?.isConnected()) {
        setErrorMessage(null);
        setLiveFinishPending(true);
        liveAwaitingFinalTurnRef.current = true;
        setStatusNote("已请求 Gemini Live 做收尾总结，等待最后一段输出结束后自动生成复盘...");
        liveClientRef.current.finish();
        return;
      }

      void handleFinalizeLiveInterview(sessionId);
      return;
    }

    const generation = practiceGenerationRef.current;
    setSummaryPending(true);
    setErrorMessage(null);
    setStatusNote("AI 正在整理这轮模拟面试的总结报告...");

    try {
      const response = await apiRequest<InterviewSummary>(`/ai/interview/sessions/${sessionId}/summary`, {
        method: "POST",
      });
      if (!isPracticeCurrent(generation)) {
        return;
      }
      setSummary(response);
      setStatusNote("复盘报告已经生成，可以继续查看本轮总结。");
      navigateToInterviewStage("report", sessionId);
    } catch (requestError) {
      if (!isPracticeCurrent(generation)) {
        return;
      }
      showErrorToast(buildInterviewErrorMessage(requestError));
    } finally {
      if (isPracticeCurrent(generation)) {
        setSummaryPending(false);
      }
    }
  };

  const answerHelperAnalysisLevel = normalizeAnswerHelperAnalysisLevel(latestAnswerHelperAnalysis?.overallLevel);
  const answerHelperAnalysisTone = answerHelperAnalysisLevel === "READY"
    ? {
      badge: "bg-emerald-100 text-emerald-700",
      border: "border-emerald-100 bg-[linear-gradient(135deg,rgba(236,253,245,0.94),rgba(255,255,255,0.98))]",
      icon: "border-emerald-100 text-emerald-600",
    }
    : answerHelperAnalysisLevel === "WARN"
      ? {
        badge: "bg-amber-100 text-amber-700",
        border: "border-amber-100 bg-[linear-gradient(135deg,rgba(255,251,235,0.94),rgba(255,255,255,0.98))]",
        icon: "border-amber-100 text-amber-600",
      }
      : {
        badge: "bg-slate-100 text-slate-600",
        border: "border-slate-100 bg-[linear-gradient(135deg,rgba(248,250,252,0.94),rgba(255,255,255,0.98))]",
        icon: "border-slate-200 text-slate-500",
      };
  const answerHelperAnalysisLevelLabel = answerHelperAnalysisLevel === "READY"
    ? "结构较稳"
    : answerHelperAnalysisLevel === "WARN"
      ? "建议补强"
      : "待生成";
  const liveInterviewScore = typeof summary?.overallScore === "number"
    ? summary.overallScore
    : latestScoreHint;
  const liveInterviewScoreTone = liveInterviewScore !== null
    ? liveInterviewScore >= 85
      ? "border-emerald-200 bg-emerald-50 text-emerald-700"
      : liveInterviewScore >= 70
        ? "border-indigo-200 bg-indigo-50 text-indigo-700"
        : "border-amber-200 bg-amber-50 text-amber-700"
    : "border-slate-200 bg-slate-50 text-slate-500";
  const answerHelperPreviewChips = latestAnswerHelperAnalysis?.items.slice(0, 3) ?? [];
  const latestFeedbackEntries: Array<{
    key: string;
    panelClassName: string;
    pillLabel: string;
    title: string;
    summary: string;
    detail: ReactNode;
    badge: ReactNode;
  }> = [
    {
      key: "coach-feedback",
      panelClassName: "border-indigo-100 bg-[linear-gradient(135deg,rgba(238,242,255,0.92),rgba(255,255,255,0.98))]",
      pillLabel: "AI 即时点评",
      title: "AI 即时点评",
      summary: latestCoachFeedback || "完成一轮回答后，这里会自动切到 AI 对你这一轮表达的即时反馈。",
      detail: latestCoachFeedback
        ? "围绕这一轮表达结构、重点完整度和临场节奏给出即时判断。"
        : "发送一轮回答后，会自动更新到最新的即时点评。",
      badge: latestScoreHint !== null ? (
        <div className="inline-flex rounded-full bg-indigo-100 px-3 py-1 text-xs font-semibold text-indigo-700">
          建议分 {latestScoreHint}
        </div>
      ) : (
        <div className="inline-flex rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-500">
          等待更新
        </div>
      ),
    },
    {
      key: "answer-helper-feedback",
      panelClassName: answerHelperAnalysisTone.border,
      pillLabel: "回答辅助诊断",
      title: "回答辅助诊断",
      summary: !answerHelperEnabled
        ? "当前未启用回答辅助器，本轮不会生成独立的回答诊断。"
        : answerHelperAnalysisPending
          ? "正在生成这一轮的回答辅助诊断，稍后会自动更新这里的内容。"
          : answerHelperAnalysisError
            ? answerHelperAnalysisError
            : latestAnswerHelperAnalysis?.overallSummary
              ? latestAnswerHelperAnalysis.overallSummary
              : "发送一轮回答后，这里会展示独立 AI 对这轮表达的结构化诊断。",
      detail: !answerHelperEnabled ? (
        "回答辅助器当前处于关闭状态。"
      ) : answerHelperAnalysisPending ? (
        "诊断仍在生成中。"
      ) : answerHelperAnalysisError ? (
        "这轮诊断未正常返回。"
      ) : answerHelperPreviewChips.length > 0 ? (
        <div className="flex flex-wrap gap-2">
          {answerHelperPreviewChips.map((item) => {
            const itemLevel = normalizeAnswerHelperAnalysisLevel(item.level);
            return (
              <span
                key={`${item.key}-${itemLevel}`}
                className={joinClasses(
                  "rounded-full px-3 py-1.5 text-[12px] font-semibold",
                  itemLevel === "READY"
                    ? "bg-emerald-100 text-emerald-700"
                    : itemLevel === "WARN"
                      ? "bg-amber-100 text-amber-700"
                      : "bg-slate-100 text-slate-600",
                )}
              >
                {getAnswerHelperCueLabel(item.key)} · {itemLevel === "READY" ? "较完整" : itemLevel === "WARN" ? "待补强" : "提示"}
              </span>
            );
          })}
        </div>
      ) : latestAnswerHelperAnalysis?.details[0] ? (
        latestAnswerHelperAnalysis.details[0]
      ) : (
        "发送回答后会自动切换到这轮诊断。"
      ),
      badge: (
        <div className={joinClasses("inline-flex rounded-full px-3 py-1 text-xs font-semibold", answerHelperEnabled ? answerHelperAnalysisTone.badge : "bg-slate-100 text-slate-500")}>
          {answerHelperEnabled ? answerHelperAnalysisLevelLabel : "本轮未开启"}
        </div>
      ),
    },
  ];
  const reportDisplayTime = sessionCreatedAt;
  const reportOutcomeCards = [
    {
      label: "作答方式",
      value: getInterviewModeLabel(answerMode),
    },
    {
      label: "面试风格",
      value: selectedInterviewerStyle.label,
    },
    {
      label: "对答轮次",
      value: `${replyRoundUsed} 轮`,
    },
    {
      label: "练习类型",
      value: selectedInterviewType.label,
    },
  ];
  const activeLatestFeedbackEntry = latestFeedbackEntries[latestFeedbackIndex] ?? latestFeedbackEntries[0];
  const handleLatestFeedbackPageChange = (nextIndex: number) => {
    setLatestFeedbackIndex(nextIndex);
    setLatestFeedbackRotationNonce((current) => current + 1);
  };

  const sessionInfoSidebar = (
    <aside className="order-2 flex h-full min-h-0 flex-col gap-5 pr-1 xl:order-1">
      <div className="shrink-0 rounded-[2.25rem] border border-white/80 bg-white/82 p-6 backdrop-blur-xl">
        <div className="flex min-h-[3.55rem] items-center justify-between gap-3">
          <div>
            <h3 className="text-xl font-bold leading-tight text-slate-950">当前练习状态</h3>
          </div>
          <div className="flex h-12 w-12 items-center justify-center rounded-[1.25rem] bg-teal-100 text-teal-600">
            <Gauge size={22} />
          </div>
        </div>

        <div className="mt-5 space-y-4">
          <div className="rounded-[1.5rem] border border-slate-100 bg-slate-50/90 p-4">
            <div className="flex items-center justify-between gap-3">
              <span className="text-sm font-semibold text-slate-500">已作答 / 安全上限</span>
              <span className="text-sm font-semibold text-slate-500">安全上限仅作兜底</span>
            </div>
            <div className="mt-3 grid gap-3 sm:grid-cols-2 xl:grid-cols-1 2xl:grid-cols-2">
              <div className="rounded-[1.15rem] border border-white/80 bg-white/90 px-4 py-3 shadow-sm">
                <div className="text-sm font-semibold text-slate-500">已作答</div>
                <div className="mt-1 text-[1.4rem] font-semibold text-slate-900">{replyRoundUsed}</div>
              </div>
              <div className="rounded-[1.15rem] border border-white/80 bg-white/90 px-4 py-3 shadow-sm">
                <div className="text-sm font-semibold text-slate-500">安全上限</div>
                <div className="mt-1 text-[1.4rem] font-semibold text-slate-900">{replyRoundLimit || "—"}</div>
              </div>
            </div>
            <div className="mt-3 h-2 rounded-full bg-slate-200">
              <div
                className="h-full rounded-full bg-gradient-to-r from-teal-500 to-indigo-600"
                style={{ width: `${progressPercent}%` }}
              />
            </div>
            <div className="mt-3 text-[15px] leading-7 text-slate-500">
              AI 会根据你的回答质量决定是否结束，你也可以随时手动结束并生成复盘。
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            {sessionStatusCards.map((card) => {
              const Icon = card.icon;
              return (
                <div
                  key={card.label}
                  className={joinClasses(
                    "min-h-[8.75rem] rounded-[1.5rem] border p-4 shadow-sm",
                    card.cardClassName,
                  )}
                >
                  <div className="flex h-full flex-col">
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <div className="text-sm font-semibold text-slate-500">{card.label}</div>
                        <div className="mt-1.5 text-lg font-semibold text-slate-900">{card.value}</div>
                      </div>
                      <div className={joinClasses("flex h-11 w-11 shrink-0 items-center justify-center rounded-[1rem] border shadow-sm", card.iconClassName)}>
                        <Icon size={18} />
                      </div>
                    </div>
                    <div className="mt-3 text-sm leading-6 text-slate-500">
                      {card.detail}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>

      <div className="mt-auto flex min-h-0 flex-1 flex-col rounded-[2.25rem] border border-white/80 bg-white/82 p-6 backdrop-blur-xl">
        <div className="flex min-h-[3.55rem] items-center justify-between gap-3">
          <div>
            <h3 className="text-xl font-bold leading-tight text-slate-950">当前已带入的信息</h3>
          </div>
          <div className="flex h-12 w-12 items-center justify-center rounded-[1.25rem] bg-indigo-100 text-indigo-600">
            <Target size={22} />
          </div>
        </div>

        <div className="mt-5 min-h-0 flex-1 space-y-3 overflow-y-auto pr-1">
          <div className="rounded-[1.4rem] border border-slate-100 bg-slate-50/90 p-4">
            <div className="text-sm font-semibold text-slate-500">本轮语境</div>
            <div className="mt-2 flex flex-wrap gap-2">
              {carriedContextChips.length > 0 ? (
                carriedContextChips.map((item) => (
                  <span key={item} className="rounded-full bg-white px-3 py-1 text-xs font-medium text-slate-600 shadow-sm">
                    {item}
                  </span>
                ))
              ) : (
                <span className="rounded-full bg-white px-3 py-1 text-xs font-medium text-slate-500 shadow-sm">
                  当前主要按默认模拟面试语境推进
                </span>
              )}
            </div>
          </div>

          <div className="rounded-[1.4rem] border border-slate-100 bg-slate-50/90 p-4">
            <div className="text-sm font-semibold text-slate-500">补充资料</div>
            <div className="mt-2 flex flex-wrap gap-2">
              {optionalPrepMaterialLabels.length > 0 ? (
                optionalPrepMaterialLabels.map((item) => (
                  <span key={item} className="rounded-full bg-white px-3 py-1 text-xs font-medium text-slate-600 shadow-sm">
                    {item}
                  </span>
                ))
              ) : (
                <span className="rounded-full bg-white px-3 py-1 text-xs font-medium text-slate-500 shadow-sm">
                  当前未额外勾选个人资料
                </span>
              )}
            </div>
          </div>

          {carriedSupplementRows.length > 0 ? (
            <div className="rounded-[1.4rem] border border-slate-100 bg-slate-50/90 p-4">
              <div className="space-y-2.5">
                {carriedSupplementRows.map((item) => (
                  <div key={item.label} className="flex items-start justify-between gap-3 rounded-[1rem] bg-white px-3.5 py-2 shadow-sm">
                    <span className="shrink-0 text-xs font-semibold text-slate-500">{item.label}</span>
                    <span className="text-right text-sm leading-6 text-slate-600">{item.value}</span>
                  </div>
                ))}
              </div>
            </div>
          ) : (
            <div className="rounded-[1.4rem] border border-dashed border-slate-200 bg-slate-50/80 px-4 py-3 text-sm leading-7 text-slate-500">
              当前没有额外的补充资料摘要，这一轮主要按岗位语境推进。
            </div>
          )}
        </div>
      </div>
    </aside>
  );

  const sessionAssistantSidebar = (
    <aside className="order-3 flex h-full min-h-0 flex-col gap-5 pr-1">
      <div className="shrink-0 rounded-[2.25rem] border border-white/80 bg-white/82 p-6 backdrop-blur-xl">
        <div className="flex min-h-[3.55rem] items-center justify-between gap-3">
          <div>
            <h3 className="text-xl font-bold leading-tight text-slate-950">回答辅助器</h3>
          </div>
          <div className="flex h-12 w-12 items-center justify-center rounded-[1.25rem] bg-fuchsia-100 text-fuchsia-600">
            <WandSparkles size={22} />
          </div>
        </div>

        <div className="mt-5 space-y-4">
          <div className="flex flex-wrap items-center gap-2">
            <div className="inline-flex rounded-full bg-slate-100 px-3 py-1 text-[11px] font-semibold text-slate-500">
              {answerHelperReferenceLabel}
            </div>
            <div className={joinClasses(
              "inline-flex rounded-full px-3 py-1 text-[11px] font-semibold",
              answerHelperEnabled
                ? "bg-fuchsia-100 text-fuchsia-700"
                : "bg-slate-100 text-slate-500",
            )}>
              {answerHelperEnabled ? `已启用 ${enabledAnswerHelperOptions.length} 项` : "本轮未开启"}
            </div>
          </div>

          {answerHelperEnabled ? (
            answerHelperInsights.length > 0 ? (
              <div className="space-y-3">
                {answerHelperInsights.map((item) => (
                  <div key={item.key} className="rounded-[1.25rem] border border-slate-100 bg-slate-50/80 px-3.5 py-3">
                    <div className="flex items-start justify-between gap-3">
                      <div className="flex min-w-0 items-start gap-2.5">
                        <div className={joinClasses(
                          "mt-0.5 shrink-0",
                          item.status === "ready"
                            ? "text-emerald-600"
                            : item.status === "warn"
                              ? "text-amber-600"
                              : "text-slate-500",
                        )}>
                          {item.status === "ready" ? <CheckCircle2 size={16} /> : item.status === "warn" ? <AlertCircle size={16} /> : <Sparkles size={16} />}
                        </div>
                        <div className="min-w-0">
                          <div className="text-[14px] font-semibold text-slate-900">{item.label}</div>
                          <div className="mt-1 text-[13px] leading-5.5 text-slate-600">{item.detail}</div>
                        </div>
                      </div>
                      <div className={joinClasses(
                        "shrink-0 rounded-full px-2.5 py-1 text-[11px] font-semibold",
                        item.status === "ready"
                          ? "bg-emerald-100 text-emerald-700"
                          : item.status === "warn"
                            ? "bg-amber-100 text-amber-700"
                            : "bg-slate-100 text-slate-500",
                      )}>
                        {item.statusLabel}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="rounded-[1.25rem] border border-dashed border-fuchsia-200 bg-white/85 px-4 py-3 text-sm leading-6 text-slate-500">
                辅助器已经打开，但这轮还没有勾选具体提醒项；需要的话可以回准备页重新调整。
              </div>
            )
          ) : (
            <div className="rounded-[1.25rem] border border-dashed border-slate-200 bg-slate-50/80 px-4 py-3 text-sm leading-6 text-slate-500">
              当前未启用回答辅助器，本页不会给出 STAR、量化结果或完整度的即时自检提示。
            </div>
          )}
        </div>
      </div>

      <div className="mt-auto flex min-h-0 flex-1 flex-col rounded-[2.25rem] border border-white/80 bg-white/82 p-6 backdrop-blur-xl">
        <div className="flex min-h-[3.55rem] items-center justify-between gap-3">
          <div>
            <h3 className="text-xl font-bold leading-tight text-slate-950">最新点评</h3>
          </div>
          <div className="flex h-12 w-12 items-center justify-center rounded-[1.25rem] bg-indigo-100 text-indigo-600">
            <Sparkles size={22} />
          </div>
        </div>

        <div className="mt-5 flex items-center justify-between gap-3 text-sm text-slate-500">
          <div className="flex items-center gap-2">
            {latestFeedbackEntries.map((entry, index) => (
              <button
                key={entry.key}
                type="button"
                onClick={() => handleLatestFeedbackPageChange(index)}
                className={joinClasses(
                  "h-2.5 rounded-full transition-all",
                  index === latestFeedbackIndex ? "w-8 bg-indigo-600" : "w-2.5 bg-slate-300 hover:bg-slate-400",
                )}
                aria-label={`切换到${entry.title}`}
              />
            ))}
          </div>
          <div className="flex items-center gap-2">
            <span className="tabular-nums">
              {latestFeedbackIndex + 1} / {latestFeedbackEntries.length}
            </span>
            <button
              type="button"
              onClick={() => handleLatestFeedbackPageChange((latestFeedbackIndex - 1 + latestFeedbackEntries.length) % latestFeedbackEntries.length)}
              className="inline-flex h-8 w-8 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-500 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600"
              aria-label="查看上一张最新点评"
            >
              <ChevronLeft size={15} />
            </button>
            <button
              type="button"
              onClick={() => handleLatestFeedbackPageChange((latestFeedbackIndex + 1) % latestFeedbackEntries.length)}
              className="inline-flex h-8 w-8 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-500 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600"
              aria-label="查看下一张最新点评"
            >
              <ChevronRight size={15} />
            </button>
          </div>
        </div>

        <div className="mt-4 min-h-0 flex-1">
          <AnimatePresence mode="wait">
            <motion.div
              key={activeLatestFeedbackEntry.key}
              initial={{ opacity: 0, x: 18 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -18 }}
              transition={{ duration: 0.22 }}
              className={joinClasses(
                "flex h-full min-h-0 flex-col rounded-[1.5rem] border p-4 shadow-sm",
                activeLatestFeedbackEntry.panelClassName,
              )}
            >
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="inline-flex rounded-full bg-white px-3 py-1 text-xs font-semibold text-slate-500 shadow-sm">
                  {activeLatestFeedbackEntry.pillLabel}
                </div>
                {activeLatestFeedbackEntry.badge}
              </div>

              <div className="mt-4 flex min-h-0 flex-1 flex-col">
                <div className="rounded-[1.2rem] border border-white/80 bg-white/84 px-4 py-3">
                  <div className="text-[15px] leading-7 text-slate-700">
                    {activeLatestFeedbackEntry.summary}
                  </div>
                </div>
                <div className="mt-3 flex-1 text-[13px] leading-6 text-slate-500">
                  {activeLatestFeedbackEntry.detail}
                </div>
              </div>
            </motion.div>
          </AnimatePresence>
        </div>
      </div>
    </aside>
  );

  return (
    <div className={joinClasses(
      "relative bg-[#eef4ff] text-slate-900",
      stage === "session" ? "flex h-screen flex-col overflow-hidden" : "min-h-screen",
    )}>
      <div className="pointer-events-none fixed inset-0 z-0 overflow-hidden">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(79,70,229,0.16),transparent_28%),radial-gradient(circle_at_bottom_right,rgba(20,184,166,0.14),transparent_26%),linear-gradient(180deg,#eff6ff_0%,#f8fafc_60%,#f8fafc_100%)]" />
        <div className="page-top-glow page-top-glow--violet" />
      </div>

      <StudentWorkspaceTopbar
        sectionLabel={topbarSectionLabel}
        title={topbarTitle}
        navItems={buildStudentWorkspacePrimaryNav("interview")}
        leftAddon={(
          <Link
            to="/student/dashboard"
            className="ml-2 inline-flex h-10 items-center justify-center rounded-full border border-slate-200 bg-white px-4 text-sm font-semibold text-slate-600 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600"
          >
            <ChevronLeft size={18} className="mr-1.5" />
            返回工作台
          </Link>
        )}
        position="sticky"
        userId={profileHint?.userId ?? userId}
        displayName={studentName}
        avatar={profileHint?.avatar}
        tier={profileHint?.tier}
      />

      <main className={joinClasses(
        "relative mx-auto w-full px-4 sm:px-6 lg:px-8",
        stage === "session"
          ? "flex min-h-0 max-w-[118rem] flex-1 flex-col overflow-hidden pb-4 pt-2 lg:pb-5 lg:pt-3"
          : "max-w-[104rem] pb-6 pt-8 lg:pb-8 lg:pt-10",
      )}>
        {stage === "prepare" ? (
          <div className="page-enter-float grid gap-8 lg:grid-cols-[1.04fr_0.96fr]">
            <section className="relative flex items-center overflow-hidden rounded-[2.5rem] border border-white/80 bg-white/68 p-8 shadow-[0_24px_80px_rgba(79,70,229,0.10)] backdrop-blur-xl lg:p-10">
              <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_left,rgba(20,184,166,0.12),transparent_34%),radial-gradient(circle_at_bottom_right,rgba(79,70,229,0.12),transparent_34%)]" />
              <div className="relative w-full">
                <div className="inline-flex items-center rounded-full border border-white/80 bg-white/80 px-4 py-2 text-xs font-semibold text-slate-500 shadow-sm">
                  <WandSparkles size={14} className="mr-2 text-amber-500" />
                  准备区
                </div>

                <div className="mt-8">
                  <h1 className="text-4xl font-black tracking-tight text-slate-900 lg:text-6xl">
                    准备好开始
                    <br />
                    <span className="bg-gradient-to-r from-teal-500 to-indigo-600 bg-clip-text text-transparent">你的高光时刻了吗？</span>
                  </h1>
                  <div className="mt-6 h-20">
                    <AnimatePresence mode="wait">
                      <motion.p
                        key={quoteIndex}
                        initial={{ opacity: 0, y: 10 }}
                        animate={{ opacity: 1, y: 0 }}
                        exit={{ opacity: 0, y: -10 }}
                        transition={{ duration: 0.45 }}
                        className="max-w-2xl text-lg leading-9 text-slate-600"
                      >
                        {motivationalQuotes[quoteIndex]}
                      </motion.p>
                    </AnimatePresence>
                  </div>
                </div>

                <div className="relative mt-8 overflow-hidden rounded-[2rem] border border-slate-200/90 bg-[linear-gradient(135deg,rgba(255,255,255,0.98),rgba(240,249,255,0.95)_48%,rgba(238,242,255,0.96))] p-6 shadow-[0_22px_55px_rgba(148,163,184,0.18)]">
                  <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_left,rgba(20,184,166,0.10),transparent_32%),radial-gradient(circle_at_bottom_right,rgba(79,70,229,0.10),transparent_34%)]" />
                  <div className="relative">
                    <div className="flex min-h-[3.65rem] items-center justify-between gap-4">
                      <div>
                        <h2 className="text-2xl font-bold leading-tight text-slate-950">{studentName}，这轮会按“准备 - 回答 - 复盘”三步推进</h2>
                      </div>
                      <div className="rounded-full border border-indigo-100 bg-white/90 px-4 py-2 text-xs font-semibold tracking-[0.18em] text-indigo-600 shadow-sm">
                        {selectedInterviewType.label}
                      </div>
                    </div>

                    <div className="mt-5 grid gap-3 md:grid-cols-3">
                      {practiceBlueprintSteps.map((item, index) => (
                        <div key={item.title} className="rounded-[1.5rem] border border-white/90 bg-white/90 p-4 shadow-sm">
                          <div className="flex items-center gap-3">
                            <div className="flex h-8 w-8 items-center justify-center rounded-full border border-slate-200 bg-slate-50 text-sm font-semibold text-slate-700">
                              {index + 1}
                            </div>
                            <div className="text-sm font-semibold text-slate-900">{item.title}</div>
                          </div>
                          <div className="mt-3 text-sm leading-7 text-slate-600">{item.detail}</div>
                        </div>
                      ))}
                    </div>

                    <p className="mt-6 max-w-2xl text-sm leading-7 text-slate-500">
                      右侧把参数准备顺后，开始前会用最终确认弹窗集中再看一眼这轮设置。确认没问题，就可以直接进入模拟面试。
                    </p>
                  </div>
                </div>
              </div>
            </section>

            <section className="rounded-[2.5rem] border border-white/80 bg-white/80 p-8 shadow-[0_24px_80px_rgba(79,70,229,0.08)] backdrop-blur-xl lg:p-9">
              <div className="flex min-h-[3.65rem] items-center justify-between gap-4">
                <div>
                  <h2 className="text-2xl font-bold leading-tight text-slate-950">先完成这一轮的参数准备</h2>
                </div>
                <div className="flex h-12 w-12 items-center justify-center rounded-[1.5rem] bg-indigo-100 text-indigo-600">
                  <Target size={22} />
                </div>
              </div>

              <div className="mt-7 space-y-5">
                <div className="grid gap-5 lg:grid-cols-[1.08fr_0.92fr]">
                  <div>
                    <label className="block text-base font-semibold text-slate-800">目标岗位</label>
                    <div className="relative mt-2">
                      <Briefcase size={18} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" />
                      <input
                        value={targetRole}
                        onChange={(event) => setTargetRole(event.target.value)}
                        placeholder="例如：前端开发工程师 / Java 后端开发工程师"
                        className="w-full rounded-2xl border border-slate-200 bg-slate-50 px-11 py-3.5 text-sm text-slate-700 outline-none transition-all placeholder:text-slate-400 focus:border-indigo-300 focus:bg-white focus:ring-4 focus:ring-indigo-100"
                      />
                    </div>
                    {profileHint?.targetPosition?.trim() ? (
                      <button
                        type="button"
                        onClick={() => setTargetRole(profileHint.targetPosition?.trim() || "")}
                        className="mt-2 text-sm font-medium text-indigo-600 transition-colors hover:text-indigo-700"
                      >
                        使用画像里的目标岗位：{profileHint.targetPosition}
                      </button>
                    ) : null}
                  </div>

                  <div>
                    <label className="block text-base font-semibold text-slate-800">目标企业</label>
                    <input
                      value={targetCompany}
                      onChange={(event) => setTargetCompany(event.target.value)}
                      placeholder="例如：字节跳动 / 美团 / 某事业单位"
                      className="mt-2 w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3.5 text-sm text-slate-700 outline-none transition-all placeholder:text-slate-400 focus:border-indigo-300 focus:bg-white focus:ring-4 focus:ring-indigo-100"
                    />
                  </div>
                </div>

                <div>
                  <label className="block text-base font-semibold text-slate-800">目标岗位要求</label>
                  <textarea
                    value={targetJobDescription}
                    onChange={(event) => setTargetJobDescription(event.target.value)}
                    rows={5}
                    placeholder="例如：负责后台管理系统前端开发，要求熟悉 React / TypeScript / 工程化，理解性能优化与团队协作..."
                    className="mt-2 w-full rounded-[1.75rem] border border-slate-200 bg-slate-50 px-4 py-3.5 text-sm leading-7 text-slate-700 outline-none transition-all placeholder:text-slate-400 focus:border-indigo-300 focus:bg-white focus:ring-4 focus:ring-indigo-100"
                  />
                </div>

                <div className="grid gap-4 sm:grid-cols-2">
                  {prepareSelectionCards.map((item) => (
                    <PrepareSelectionCard
                      key={item.key}
                      title={item.title}
                      value={item.value}
                      detail={item.detail}
                      icon={item.icon}
                      tone={item.tone}
                      isActive={expandedPrepareDrawer === item.key}
                      onClick={() => togglePrepareDrawer(item.key)}
                    />
                  ))}
                </div>

                {profileError ? (
                  <div className="rounded-[1.5rem] border border-amber-200 bg-amber-50 px-4 py-3 text-sm leading-7 text-amber-700">
                    学生画像没有成功预取：{profileError}
                  </div>
                ) : null}

                <button
                  type="button"
                  onClick={handleOpenStartConfirm}
                  disabled={sessionPending}
                  className="inline-flex w-full items-center justify-center rounded-[1.75rem] bg-gradient-to-r from-teal-500 to-indigo-600 px-6 py-4 text-sm font-semibold text-white shadow-[0_20px_40px_rgba(79,70,229,0.24)] transition-transform hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-70"
                >
                  {sessionPending ? "正在创建会话..." : "开始前确认并开始"}
                  <ArrowRight size={16} className="ml-2" />
                </button>
              </div>
            </section>
          </div>
        ) : null}

        {interviewOverlaysLoaded ? (
          <Suspense fallback={isInterviewOverlayVisible ? <InterviewOverlayFallback /> : null}>
            {continuableInterviewRecord?.sessionId ? (
              <InterviewPracticeOverlay
                kind="continuable-interview"
                open={isContinuableInterviewPromptVisible}
                record={continuableInterviewRecord}
                onClose={() => setIsContinuableInterviewPromptOpen(false)}
                onContinue={() => {
                  setIsContinuableInterviewPromptOpen(false);
                  navigateToInterviewStage("session", continuableInterviewRecord.sessionId);
                }}
              />
            ) : null}

            <InterviewPracticeOverlay
              kind="session-creating"
              open={isSessionCreatingModalVisible}
              sessionTitle={sessionTitle}
              interviewTypeLabel={selectedInterviewType.label}
              interviewerStyleLabel={selectedInterviewerStyle.label}
              difficultyLabel={selectedDifficulty.label}
              answerModeLabel={answerModeStatusLabel}
              prepMaterialCount={selectedPrepMaterialLabels.length}
            />

            <InterviewPracticeOverlay
              kind="summary-generating"
              open={isSummaryGeneratingModalVisible}
              sessionTitle={sessionTitle}
              interviewerStyleLabel={selectedInterviewerStyle.label}
              answerModeLabel={answerModeStatusLabel}
              replyRoundUsed={replyRoundUsed}
            />

            <InterviewPracticeOverlay
              kind="privacy-notice"
              open={isPrivacyNoticeVisible}
              displayName={studentName}
              onDismissOnce={handleDismissPrivacyNoticeOnce}
              onDismissToday={handleDismissPrivacyNoticeToday}
            />

            <InterviewPracticeOverlay
              kind="prepare-setup"
              open={isPrepareSetupModalVisible && Boolean(activePrepareCard)}
              title={activePrepareCard?.title ?? ""}
              description={activePrepareCard?.panelDescription ?? ""}
              onClose={() => setExpandedPrepareDrawer(null)}
              widthClassName={
                activePrepareCard && ["INTERVIEW_TYPE", "INTERVIEWER_STYLE", "DIFFICULTY", "ANSWER_MODE"].includes(activePrepareCard.key)
                  ? "max-w-4xl"
                  : "max-w-5xl"
              }
            >
              {activePrepareCard ? renderExpandedPreparePanel(activePrepareCard.key) : null}
            </InterviewPracticeOverlay>
          </Suspense>
        ) : null}

        {stage === "prepare" && isStartConfirmOpen ? (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/45 px-4 py-8 backdrop-blur-sm sm:px-6">
            <div className="w-full max-w-6xl overflow-hidden rounded-[2.5rem] border border-white/70 bg-white/92 shadow-[0_30px_120px_rgba(15,23,42,0.24)] backdrop-blur-xl">
              <div className="flex items-start justify-between gap-4 border-b border-slate-100 px-6 py-5 lg:px-8">
                <div className="flex min-h-[4.1rem] flex-col justify-center">
                  <h2 className="text-[28px] font-semibold leading-tight text-slate-950">再看一眼这轮设置，就可以开始了</h2>
                  <p className="mt-2 text-[15px] leading-8 text-slate-500">
                    {`当前会继续使用“${studentName}”作为本轮匿名称呼。下面快速确认一下参数、带入资料和开始方式，确认无误就可以直接进入模拟面试。`}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setIsStartConfirmOpen(false)}
                  className="inline-flex h-11 w-11 items-center justify-center rounded-2xl border border-slate-200 bg-white text-slate-500 shadow-sm transition-colors hover:border-slate-300 hover:text-slate-700"
                >
                  <X size={18} />
                </button>
              </div>

              <div className="space-y-5 px-6 py-6 lg:max-h-[78vh] lg:overflow-y-auto lg:px-8">
                <div className="rounded-[1.9rem] border border-slate-200 bg-[linear-gradient(135deg,rgba(248,250,252,0.98),rgba(255,255,255,0.98))] p-5 shadow-sm">
                  <div className="flex items-start justify-between gap-4">
                    <div>
                      <div className="text-lg font-semibold text-slate-900">本轮设置摘要</div>
                      <p className="mt-2 text-[15px] leading-8 text-slate-500">
                        这一步只用快速确认关键设置，不再重复承担准备区里的引导信息。
                      </p>
                    </div>
                    <div className="rounded-full border border-slate-200 bg-white px-3 py-1 text-xs font-semibold text-slate-500 shadow-sm">
                      {prepReadinessPercent}% 已就绪
                    </div>
                  </div>
                  <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
                    {confirmationOverviewCards.map((item) => {
                      const Icon = item.icon;
                      return (
                        <div key={item.label} className="rounded-[1.35rem] border border-slate-200 bg-white/95 p-3.5 shadow-sm">
                          <div className="flex items-start gap-2.5">
                            <div className={joinClasses("mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-xl border shadow-sm", item.iconClassName)}>
                              <Icon size={15} />
                            </div>
                            <div className="min-w-0 flex-1">
                              <div className="text-sm font-semibold text-slate-500">{item.label}</div>
                              <div className="mt-1 text-[16px] font-semibold text-slate-900">{item.value}</div>
                            </div>
                          </div>
                          <div className="mt-2 text-[14px] leading-6 text-slate-500">
                            {item.detail}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>

                <div className={joinClasses(
                  "grid gap-5",
                  targetJobDescription.trim() && isAudioAnswerMode
                    ? "xl:grid-cols-3"
                    : targetJobDescription.trim() || isAudioAnswerMode
                      ? "lg:grid-cols-2"
                      : "",
                )}>
                  <div className="rounded-[1.9rem] border border-slate-200 bg-[linear-gradient(135deg,rgba(248,250,252,0.98),rgba(255,255,255,0.98))] p-5 shadow-sm">
                    <div className="flex items-start justify-between gap-4">
                      <div>
                        <div className="text-lg font-semibold text-slate-900">本轮会参考这些资料</div>
                        <p className="mt-2 text-[15px] leading-8 text-slate-500">
                          顶部填写的岗位语境会自动同步；这里主要帮你确认这轮额外带了哪些个人资料。
                        </p>
                      </div>
                      <div className="rounded-full border border-slate-200 bg-white px-3 py-1 text-xs font-semibold text-slate-500 shadow-sm">
                        {selectedPrepMaterialLabels.length} 项
                      </div>
                    </div>

                    <div className="mt-4 overflow-hidden rounded-[1.5rem] border border-slate-200 bg-white/95 shadow-sm">
                      <div className="border-b border-slate-100 px-5 py-4 last:border-b-0">
                        <div className="text-sm font-semibold text-slate-500">自动同步语境</div>
                        <div className="mt-2 text-[15px] leading-8 text-slate-600">
                          {autoSyncedPrepContextSummary}
                        </div>
                      </div>
                      <div className="px-5 py-4">
                        <div className="text-sm font-semibold text-slate-500">额外带入资料</div>
                        <div className="mt-2 text-[15px] leading-8 text-slate-600">
                          {optionalPrepMaterialSummary}
                        </div>
                      </div>
                    </div>
                  </div>

                  {targetJobDescription.trim() ? (
                    <div className="rounded-[1.9rem] border border-slate-200 bg-[linear-gradient(135deg,rgba(248,250,252,0.98),rgba(255,255,255,0.98))] p-5 shadow-sm">
                      <div className="text-lg font-semibold text-slate-900">岗位要求预览</div>
                      <p className="mt-3 text-[15px] leading-8 text-slate-600">
                        {truncateText(targetJobDescription, 220)}
                      </p>
                    </div>
                  ) : null}

                  {isAudioAnswerMode ? (
                    <div className="rounded-[1.9rem] border border-slate-200 bg-[linear-gradient(135deg,rgba(240,249,255,0.98),rgba(255,255,255,0.98))] p-5 shadow-sm">
                      <div className="flex items-start justify-between gap-4">
                        <div>
                          <div className="text-lg font-semibold text-slate-900">
                            {isLiveAnswerMode ? "Live 作答确认" : "语音作答确认"}
                          </div>
                          <p className="mt-2 text-[15px] leading-8 text-slate-500">{deviceStatusSummary}</p>
                        </div>
                        <div
                          className={joinClasses(
                            "rounded-full border px-3 py-1 text-xs font-semibold shadow-sm",
                            micStatus === "ready"
                              ? "border-emerald-200 bg-emerald-50 text-emerald-700"
                              : micStatus === "blocked" || micStatus === "unsupported"
                                ? "border-amber-200 bg-amber-50 text-amber-700"
                                : micStatus === "checking"
                                  ? "border-indigo-200 bg-indigo-50 text-indigo-700"
                                  : "border-slate-200 bg-white text-slate-600",
                          )}
                        >
                          {deviceStatusLabel}
                        </div>
                      </div>
                      <div className="mt-4 overflow-hidden rounded-[1.5rem] border border-slate-200 bg-white/95 shadow-sm">
                        <div className="border-b border-slate-100 px-5 py-4">
                          <div className="text-sm font-semibold text-slate-500">开始方式</div>
                          <div className="mt-2 text-[15px] leading-8 text-slate-600">
                            {isLiveAnswerMode ? "实时麦克风 + Python WS + Gemini Live" : recommendedInputPath}
                          </div>
                        </div>
                        <div className="px-5 py-4">
                          <div className="text-sm font-semibold text-slate-500">补充建议</div>
                          <div className="mt-2 text-[15px] leading-8 text-slate-600">
                            {isLiveAnswerMode ? "当前属于测试模式，建议佩戴耳机并保持网络稳定；如需重连，会开启新的 Live 会话。" : headsetSuggestion}
                          </div>
                        </div>
                      </div>
                    </div>
                  ) : null}
                </div>

                <div className="rounded-[1.6rem] border border-slate-200 bg-slate-50/85 px-5 py-4 text-[15px] leading-8 text-slate-600 shadow-sm">
                  {prepStartSuggestion}
                </div>

                <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
                  <button
                    type="button"
                    onClick={() => setIsStartConfirmOpen(false)}
                    className="inline-flex items-center justify-center rounded-[1.25rem] border border-slate-200 bg-white px-5 py-3.5 text-[15px] font-semibold text-slate-600 shadow-sm transition-colors hover:border-slate-300 hover:text-slate-800"
                  >
                    返回继续调整
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      void handleStartSession();
                    }}
                    disabled={sessionPending}
                    className="group relative inline-flex items-center justify-center overflow-hidden rounded-[1.25rem] bg-gradient-to-r from-teal-500 to-indigo-600 px-5 py-3.5 text-[15px] font-semibold text-white shadow-[0_20px_40px_rgba(79,70,229,0.20)] transition-transform hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-70"
                  >
                    <span
                      aria-hidden="true"
                      className="pointer-events-none absolute inset-y-[-24%] left-0 w-28 -translate-x-[180%] -skew-x-[18deg] bg-gradient-to-r from-transparent via-white/85 to-transparent opacity-0 transition-all duration-700 ease-out group-hover:translate-x-[420%] group-hover:opacity-100"
                    />
                    <span
                      aria-hidden="true"
                      className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(255,255,255,0.26),transparent_58%)] opacity-0 transition-opacity duration-300 group-hover:opacity-100"
                    />
                    <span className="relative z-10 inline-flex items-center">
                      {sessionPending ? "正在创建会话..." : "确认并开始这轮模拟面试"}
                      <ArrowRight size={16} className="ml-2" />
                    </span>
                  </button>
                </div>
              </div>
            </div>
          </div>
        ) : null}

        {stage === "session" ? (
          <div className="page-enter-float grid h-full min-h-0 gap-6 xl:grid-cols-[24rem_minmax(0,1fr)_24rem] 2xl:grid-cols-[25.5rem_minmax(0,1fr)_25.5rem]">
            {sessionInfoSidebar}
            <section className="order-1 flex h-full min-h-0 flex-col overflow-hidden rounded-[2.5rem] border border-white/80 bg-white/82 shadow-[0_24px_80px_rgba(79,70,229,0.10)] backdrop-blur-xl xl:order-2">
              <header className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-100 px-6 py-4">
                <div className="flex items-center gap-3">
                  <div className="flex h-12 w-12 items-center justify-center rounded-[1.25rem] bg-indigo-100 text-indigo-600">
                    <Bot size={22} />
                  </div>
                  <div className="flex min-h-12 items-center">
                    <h2 className="text-xl font-bold leading-tight text-slate-950">{sessionTitle}</h2>
                  </div>
                </div>

                <div className="flex flex-wrap items-center gap-3">
                  <div className={joinClasses("rounded-[1.15rem] border px-4 py-2.5 shadow-sm", liveInterviewScoreTone)}>
                    <div className="text-sm font-semibold text-current/75">实时综合分</div>
                    <div className="mt-1">
                      <AnimatedScoreValue value={liveInterviewScore} />
                    </div>
                  </div>
                  <div className="rounded-full border border-slate-200 bg-slate-50 px-4 py-2 text-xs font-semibold text-slate-600">
                    {isSessionCompleted ? "本轮已结束" : answerModeStatusLabel}
                  </div>
                  {isSessionCompleted ? (
                    <button
                      type="button"
                      onClick={() => {
                        navigateToInterviewStage("report", sessionId);
                      }}
                      className="inline-flex items-center rounded-full border border-indigo-200 bg-indigo-50 px-4 py-2.5 text-sm font-semibold text-indigo-700 shadow-sm transition-colors hover:border-indigo-300 hover:bg-indigo-100"
                    >
                      <Sparkles size={16} className="mr-2" />
                      查看复盘
                    </button>
                  ) : (
                    <>
                      <button
                        type="button"
                        onClick={() => {
                          void handleGenerateSummary();
                        }}
                        disabled={summaryPending || sessionPending || abandonPending || liveFinishPending}
                        className="inline-flex items-center rounded-full border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-600 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600 disabled:cursor-not-allowed disabled:opacity-70"
                      >
                        {summaryPending
                          ? "正在生成复盘..."
                          : liveFinishPending
                            ? "正在结束 Live..."
                            : isLiveAnswerMode
                              ? "结束 Live 并生成复盘"
                              : "手动结束并生成复盘"}
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          void handleAbandonPractice();
                        }}
                        disabled={sessionPending || summaryPending || abandonPending || isRecording || liveFinishPending}
                        className="inline-flex items-center rounded-full border border-rose-100 bg-rose-50 px-4 py-2.5 text-sm font-semibold text-rose-600 transition-colors hover:bg-rose-100 disabled:cursor-not-allowed disabled:opacity-70"
                      >
                        <PhoneOff size={16} className="mr-2" />
                        {abandonPending ? "正在放弃本轮..." : "放弃本轮并返回准备页"}
                      </button>
                    </>
                  )}
                </div>
              </header>

              <div className="flex min-h-0 flex-1 flex-col bg-slate-50/70 px-6 py-5">
                <div className="relative mt-4 min-h-0 flex-1">
                  {statusNote ? (
                    <div className="pointer-events-none absolute inset-x-0 top-0 z-10 flex justify-center px-3">
                      <div className="pointer-events-auto w-full max-w-3xl rounded-[1.5rem] border border-indigo-100 bg-[linear-gradient(135deg,rgba(238,242,255,0.94),rgba(255,255,255,0.96))] px-4 py-3 text-sm leading-7 text-slate-700 shadow-sm backdrop-blur-sm">
                        <div className="flex items-start justify-between gap-3">
                          <div className="flex min-w-0 items-start gap-3">
                            <div className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-indigo-100 text-indigo-600">
                              <Sparkles size={15} />
                            </div>
                            <div className="min-w-0 flex-1">{statusNote}</div>
                          </div>
                          <button
                            type="button"
                            onClick={() => setStatusNote(null)}
                            className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-slate-400 transition-colors hover:bg-white/80 hover:text-slate-600"
                            aria-label="关闭提示"
                          >
                            <X size={15} />
                          </button>
                        </div>
                      </div>
                    </div>
                  ) : null}

                  <div className={joinClasses("h-full overflow-y-auto pr-1", statusNote && "pt-[5.25rem]")}>
                    <div className="flex min-h-full flex-col justify-end space-y-4">
                    <AnimatePresence>
                      {messages.map((message) => (
                        <motion.div
                          key={message.id}
                          initial={{ opacity: 0, y: 14, scale: 0.98 }}
                          animate={{ opacity: 1, y: 0, scale: 1 }}
                          transition={{ type: "spring", stiffness: 230, damping: 22 }}
                          className={joinClasses("flex", message.role === "USER" ? "justify-end" : "justify-start")}
                        >
                          <div className={joinClasses("flex max-w-[86%] gap-3", message.role === "USER" ? "flex-row-reverse" : "flex-row")}>
                            {message.role === "USER" ? (
                              <StudentIdentityAvatar
                                userId={profileHint?.userId ?? userId}
                                role="STUDENT"
                                displayName={studentName}
                                avatar={profileHint?.avatar}
                                tier={profileHint?.tier}
                                className="h-11 w-11 border-2 border-white shadow-sm"
                                fallbackClassName="bg-gradient-to-r from-teal-500 to-teal-600"
                                textClassName="text-sm font-bold text-white"
                                allowLatestFallback
                              />
                            ) : (
                              <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full border-2 border-white bg-gradient-to-br from-indigo-500 to-violet-600 text-white shadow-sm">
                                <Bot size={18} />
                              </div>
                            )}

                            <div className={joinClasses("flex flex-col", message.role === "USER" ? "items-end" : "items-start")}>
                              {message.badge ? (
                                <span
                                  className={joinClasses(
                                    "mb-1 rounded-full px-3 py-1 text-[11px] font-semibold",
                                    message.role === "USER"
                                      ? "bg-teal-100 text-teal-700"
                                      : "bg-indigo-100 text-indigo-700",
                                  )}
                                >
                                  {message.badge}
                                </span>
                              ) : null}
                              <div
                                className={joinClasses(
                                  "rounded-[1.75rem] px-5 py-3.5 text-[15px] leading-7 shadow-sm",
                                  message.role === "USER"
                                    ? "rounded-tr-md bg-gradient-to-r from-teal-500 to-teal-600 text-white"
                                    : "rounded-tl-md border border-white/80 bg-white text-slate-700",
                                )}
                              >
                                {message.role === "ASSISTANT" && message.isStreaming && !message.text.trim() ? (
                                  <div className="flex items-center gap-2 text-slate-500">
                                    <motion.span
                                      animate={{ y: [0, -5, 0] }}
                                      transition={{ duration: 0.6, repeat: Number.POSITIVE_INFINITY, delay: 0 }}
                                      className="h-2 w-2 rounded-full bg-indigo-400"
                                    />
                                    <motion.span
                                      animate={{ y: [0, -5, 0] }}
                                      transition={{ duration: 0.6, repeat: Number.POSITIVE_INFINITY, delay: 0.18 }}
                                      className="h-2 w-2 rounded-full bg-indigo-400"
                                    />
                                    <motion.span
                                      animate={{ y: [0, -5, 0] }}
                                      transition={{ duration: 0.6, repeat: Number.POSITIVE_INFINITY, delay: 0.36 }}
                                      className="h-2 w-2 rounded-full bg-indigo-400"
                                    />
                                    <span className="ml-2 text-sm">正在生成下一轮追问...</span>
                                  </div>
                                ) : (
                                  <>
                                    {message.text}
                                    {message.role === "ASSISTANT" && message.isStreaming ? (
                                      <motion.span
                                        animate={{ opacity: [0.3, 1, 0.3] }}
                                        transition={{ duration: 0.9, repeat: Number.POSITIVE_INFINITY, ease: "easeInOut" }}
                                        className="ml-1 inline-block text-indigo-400"
                                      >
                                        |
                                      </motion.span>
                                    ) : null}
                                  </>
                                )}
                              </div>
                              <div
                                className={joinClasses(
                                  "mt-1.5 flex self-stretch flex-wrap items-center gap-2 text-[13px] text-slate-400",
                                  message.role === "USER" ? "justify-start" : "justify-end",
                                )}
                              >
                                {message.role === "USER" ? (
                                  <span className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2.5 py-1 font-medium text-slate-500">
                                    <Clock3 size={13} />
                                    {formatClockTime(message.createdAt)}
                                  </span>
                                ) : null}
                                {message.scoreHint !== null ? (
                                  <span className="rounded-full bg-slate-200 px-2.5 py-1 font-semibold text-slate-600">
                                    建议分 {message.scoreHint}
                                  </span>
                                ) : null}
                                {message.audioObjectKey ? (
                                  <span className="rounded-full bg-slate-200 px-2.5 py-1 font-semibold text-slate-600">
                                    已记录语音对象
                                  </span>
                                ) : null}
                                {isAudioAnswerMode && message.role === "ASSISTANT" && message.text.trim() ? (
                                  <button
                                    type="button"
                                    onClick={() => {
                                      void handlePlayTts(`message:${message.id}`, message.text);
                                    }}
                                    disabled={ttsPendingKey !== null && ttsPendingKey !== `message:${message.id}`}
                                    className="inline-flex items-center rounded-full bg-slate-200 px-2.5 py-1 font-semibold text-slate-600 transition-colors hover:bg-indigo-100 hover:text-indigo-700 disabled:cursor-not-allowed disabled:opacity-60"
                                  >
                                    {ttsPendingKey === `message:${message.id}` ? (
                                      <RefreshCw size={12} className="mr-1 animate-spin" />
                                    ) : ttsPlayingKey === `message:${message.id}` ? (
                                      <Volume2 size={12} className="mr-1" />
                                    ) : (
                                      <Play size={12} className="mr-1" />
                                    )}
                                    {ttsPlayingKey === `message:${message.id}` ? "停止播报" : ttsPendingKey === `message:${message.id}` ? "生成播报" : "朗读追问"}
                                  </button>
                                ) : null}
                                {message.role !== "USER" ? (
                                  <span className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2.5 py-1 font-medium text-slate-500">
                                    <Clock3 size={13} />
                                    {formatClockTime(message.createdAt)}
                                  </span>
                                ) : null}
                              </div>
                            </div>
                          </div>
                        </motion.div>
                      ))}
                    </AnimatePresence>

                    <div ref={messagesEndRef} />
                    </div>
                  </div>
                </div>
              </div>

              <div className="border-t border-slate-100 bg-white px-6 py-4">
                {isReadOnlySession ? (
                  <div className="rounded-[2rem] border border-indigo-100 bg-[linear-gradient(135deg,rgba(238,242,255,0.96),rgba(255,255,255,0.98))] p-5 shadow-sm">
                    <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
                      <div className="flex items-start gap-3">
                        <div className="flex h-12 w-12 items-center justify-center rounded-[1.25rem] bg-white text-indigo-600 shadow-sm">
                          <CheckCircle2 size={22} />
                        </div>
                        <div>
                          <div className="text-sm font-semibold text-slate-900">这轮会话已经结束</div>
                          <div className="mt-1 text-sm leading-7 text-slate-500">
                            当前页保留完整对话记录，输入区已切为只读；如果要继续看总结，直接去复盘页即可。
                          </div>
                        </div>
                      </div>
                      <div className="flex flex-wrap gap-3">
                        <button
                          type="button"
                          onClick={() => {
                            navigateToInterviewStage("report", sessionId);
                          }}
                          className="inline-flex items-center rounded-full bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-indigo-700"
                        >
                          <Sparkles size={16} className="mr-2" />
                          查看复盘
                        </button>
                        <button
                          type="button"
                          onClick={handleResetPractice}
                          className="inline-flex items-center rounded-full border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-600 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600"
                        >
                          <RefreshCw size={16} className="mr-2" />
                          再练一轮
                        </button>
                      </div>
                    </div>
                  </div>
                ) : answerMode === "text" ? (
                  <div>
                    <div className="rounded-[2rem] border border-slate-200 bg-[linear-gradient(180deg,rgba(248,250,252,0.95),rgba(255,255,255,0.98))] p-4 shadow-[inset_0_1px_0_rgba(255,255,255,0.72),0_18px_40px_rgba(148,163,184,0.10)]">
                      <div className="flex items-start gap-4">
                        <div className="mt-1 flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-white text-slate-600 shadow-sm">
                          <Send size={18} />
                        </div>
                        <div className="flex-1 rounded-[1.6rem] border border-white/90 bg-white/85 px-4 py-3 shadow-[inset_0_1px_0_rgba(255,255,255,0.8)]">
                          <textarea
                            value={draftAnswer}
                            onChange={(event) => setDraftAnswer(event.target.value)}
                            onKeyDown={handleDraftAnswerKeyDown}
                            placeholder="请结合项目背景、你的动作、结果数据与复盘反思整理本轮回答。Enter 仅换行，Ctrl+Enter 发送。"
                            className="min-h-[154px] w-full resize-none bg-transparent text-[16px] leading-8 text-slate-700 outline-none placeholder:text-[15px] placeholder:leading-8 placeholder:text-slate-400"
                          />
                        </div>
                        <button
                          type="button"
                          onClick={() => {
                            void handleTextReply();
                          }}
                          disabled={sessionPending || abandonPending || !draftAnswer.trim()}
                          className="inline-flex h-14 shrink-0 items-center justify-center rounded-[1.35rem] bg-indigo-600 px-6 text-lg font-bold text-white transition-colors hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-70"
                        >
                          发送
                        </button>
                      </div>
                    </div>
                    <div className="mt-3 px-1 text-center text-xs leading-6 text-slate-400">
                      当前会话中的追问、点评与总结内容均由 AI 自动生成，可能存在偏差、遗漏或时效性限制，仅作为模拟练习与表达辅助参考，不构成事实判断、招聘结论或专业建议。
                    </div>
                  </div>
                ) : isLiveAnswerMode ? (
                  <div className="rounded-[2rem] border border-slate-200 bg-slate-50 p-5">
                    <div className="flex flex-wrap items-start justify-between gap-4">
                      <div>
                        <div className="text-sm font-semibold text-slate-900">Gemini Live 测试模式</div>
                        <p className="mt-1 text-sm leading-7 text-slate-500">
                          当前会通过独立 Python WebSocket 直连 Gemini Live，实时采集麦克风并播放模型语音；结束时会把 transcript 回灌到正式复盘链路。
                        </p>
                      </div>
                      <div className="flex flex-wrap gap-3">
                        <button
                          type="button"
                          onClick={() => {
                            void handleToggleLiveMicrophone();
                          }}
                          disabled={sessionPending || summaryPending || abandonPending || liveFinishPending}
                          className="inline-flex items-center rounded-full border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-600 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600 disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          <Mic size={16} className="mr-2" />
                          {isLiveMicActive ? "静音麦克风" : "恢复麦克风"}
                        </button>
                        <button
                          type="button"
                          onClick={handleReconnectLiveSession}
                          disabled={sessionPending || summaryPending || abandonPending || liveFinishPending}
                          className="inline-flex items-center rounded-full border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-600 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600 disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          <RefreshCw size={16} className="mr-2" />
                          手动重连
                        </button>
                      </div>
                    </div>

                    <div className="mt-5 grid gap-4 xl:grid-cols-[minmax(0,1.15fr)_minmax(22rem,0.85fr)]">
                      <div className="space-y-4">
                        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                          {[
                            { label: "连接状态", value: liveConnectionStatus, tone: "border-indigo-200 bg-indigo-50/80 text-indigo-700" },
                            { label: "麦克风", value: liveMicStatus, tone: "border-emerald-200 bg-emerald-50/80 text-emerald-700" },
                            { label: "播放状态", value: livePlaybackStatus, tone: "border-sky-200 bg-sky-50/80 text-sky-700" },
                            { label: "累计 Token", value: `${liveTokenCount}`, tone: "border-amber-200 bg-amber-50/80 text-amber-700" },
                          ].map((item) => (
                            <div key={item.label} className={joinClasses("rounded-[1.35rem] border px-4 py-3 shadow-sm", item.tone)}>
                              <div className="text-sm font-semibold text-current/75">{item.label}</div>
                              <div className="mt-2 text-sm font-semibold leading-6 text-current">{item.value}</div>
                            </div>
                          ))}
                        </div>

                        <div className="grid gap-4 lg:grid-cols-2">
                          <div className="rounded-[1.6rem] border border-slate-200 bg-white p-4 shadow-sm">
                            <div className="text-sm font-semibold text-slate-500">我的实时字幕</div>
                            <div className="mt-3 min-h-[7.5rem] rounded-[1.25rem] border border-slate-100 bg-slate-50/80 px-4 py-3 text-sm leading-7 text-slate-700">
                              {liveUserCaption}
                            </div>
                          </div>
                          <div className="rounded-[1.6rem] border border-slate-200 bg-white p-4 shadow-sm">
                            <div className="text-sm font-semibold text-slate-500">AI 实时字幕</div>
                            <div className="mt-3 min-h-[7.5rem] rounded-[1.25rem] border border-slate-100 bg-slate-50/80 px-4 py-3 text-sm leading-7 text-slate-700">
                              {liveModelCaption}
                            </div>
                          </div>
                        </div>

                        <div className="rounded-[1.5rem] border border-dashed border-slate-200 bg-white/90 px-4 py-3 text-sm leading-7 text-slate-500">
                          当前已累计保留 {liveTranscriptRef.current.filter((message) => message.text.trim()).length} 条 transcript。手动结束时会先导入 Java 会话，再走正式复盘生成。
                        </div>
                      </div>

                      <div className="rounded-[1.6rem] border border-slate-200 bg-white p-4 shadow-sm">
                        <div className="flex items-center justify-between gap-3">
                          <div>
                            <div className="text-base font-semibold text-slate-950">最近事件</div>
                          </div>
                          <div className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-semibold text-slate-500">
                            {liveLogEntries.length} 条
                          </div>
                        </div>

                        <div className="mt-4 max-h-[19rem] space-y-3 overflow-y-auto pr-1">
                          {liveLogEntries.length > 0 ? (
                            liveLogEntries.map((entry) => (
                              <div key={entry.id} className="rounded-[1.2rem] border border-slate-100 bg-slate-50/80 px-3.5 py-3">
                                <div className="text-sm font-semibold text-slate-900">{entry.title}</div>
                                <div className="mt-1 text-sm leading-6 text-slate-500">{entry.detail || "无附加说明"}</div>
                              </div>
                            ))
                          ) : (
                            <div className="rounded-[1.2rem] border border-dashed border-slate-200 bg-slate-50/80 px-4 py-3 text-sm leading-6 text-slate-500">
                              当前还没有收到新的 Live 事件，创建会话并连上 Python 服务后会在这里持续刷新。
                            </div>
                          )}
                        </div>
                      </div>
                    </div>
                  </div>
                ) : (
                  <div className="rounded-[2rem] border border-slate-200 bg-slate-50 p-5">
                    <div className="flex flex-wrap items-start justify-between gap-4">
                      <div>
                        <div className="text-sm font-semibold text-slate-900">录一段 webm 语音继续本轮面试</div>
                        <p className="mt-1 text-sm leading-7 text-slate-500">
                          当前优先使用浏览器 `MediaRecorder` 录成 `audio/webm` 再走真实 `/voice-roundtrip`；如果浏览器不支持，也可以继续手动上传音频文件。
                        </p>
                      </div>
                      <button
                        type="button"
                        onClick={() => {
                          handleStopBrowserRecording("discard");
                          setAnswerMode("text");
                        }}
                        className="inline-flex items-center rounded-full border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-600 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600"
                      >
                        改用文字回答
                      </button>
                    </div>

                    <div className="mt-5 rounded-[1.75rem] border border-emerald-200 bg-emerald-50/70 p-5">
                      <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
                        <div className="flex items-start gap-3">
                          <div className="flex h-12 w-12 items-center justify-center rounded-[1.25rem] bg-white text-emerald-600 shadow-sm">
                            <Mic size={22} />
                          </div>
                          <div>
                            <div className="text-sm font-semibold text-slate-900">
                              {isRecording ? "正在录音中" : "浏览器录音"}
                            </div>
                            <div className="mt-1 text-sm text-slate-500">
                              {isRecording
                                ? `已录制 ${formatDurationLabel(recordingElapsedMs)} · ${recordingMimeType}`
                                : preferredRecordingMimeType
                                  ? `优先使用 ${preferredRecordingMimeType} 录制并提交`
                                  : "当前浏览器不支持 webm 录音，可改用手动上传音频文件"}
                            </div>
                          </div>
                        </div>

                        <div className="flex flex-wrap gap-3">
                          {!isRecording ? (
                            <button
                              type="button"
                              onClick={() => {
                                void handleStartBrowserRecording();
                              }}
                              disabled={sessionPending || abandonPending || !preferredRecordingMimeType}
                              className="inline-flex items-center rounded-full bg-emerald-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-70"
                            >
                              <Mic size={16} className="mr-2" />
                              开始录音
                            </button>
                          ) : (
                            <>
                              <button
                                type="button"
                                onClick={() => handleStopBrowserRecording("discard")}
                                className="inline-flex items-center rounded-full border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-600 shadow-sm transition-colors hover:border-rose-200 hover:text-rose-600"
                              >
                                取消录音
                              </button>
                              <button
                                type="button"
                                onClick={() => handleStopBrowserRecording("upload")}
                                className="inline-flex items-center rounded-full bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-indigo-700"
                              >
                                <UploadCloud size={16} className="mr-2" />
                                停止并提交
                              </button>
                            </>
                          )}
                        </div>
                      </div>

                      {isRecording ? (
                        <div className="mt-4 flex items-center gap-1 overflow-hidden rounded-[1.25rem] border border-white/80 bg-white/80 px-4 py-3">
                          {Array.from({ length: 20 }).map((_, index) => (
                            <motion.span
                              key={`record-bar-${index}`}
                              animate={{ height: ["10px", `${16 + (index % 5) * 7}px`, "10px"] }}
                              transition={{ duration: 0.7, repeat: Number.POSITIVE_INFINITY, delay: index * 0.04 }}
                              className="w-1.5 rounded-full bg-gradient-to-t from-emerald-400 to-teal-500"
                            />
                          ))}
                        </div>
                      ) : null}
                    </div>

                    <div className="mt-5 rounded-[1.75rem] border border-dashed border-indigo-200 bg-white p-5">
                      <input
                        ref={voiceFileInputRef}
                        type="file"
                        accept="audio/*,.webm,.wav,.mp3,.m4a"
                        className="hidden"
                        onChange={handleVoiceFileChange}
                      />
                      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
                        <div className="flex items-start gap-3">
                          <div className="flex h-12 w-12 items-center justify-center rounded-[1.25rem] bg-indigo-50 text-indigo-600">
                            <UploadCloud size={22} />
                          </div>
                          <div>
                            <div className="text-sm font-semibold text-slate-900">{voiceFile ? voiceFile.name : "选择一段音频文件"}</div>
                            <div className="mt-1 text-sm text-slate-500">文件大小：{formatFileSize(voiceFile)}</div>
                          </div>
                        </div>
                        <div className="flex flex-wrap gap-3">
                          <button
                            type="button"
                            onClick={() => voiceFileInputRef.current?.click()}
                            className="inline-flex items-center rounded-full border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-600 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600"
                          >
                            选择音频
                          </button>
                          <button
                            type="button"
                            onClick={() => {
                              void handleVoiceRoundtrip();
                            }}
                            disabled={sessionPending || abandonPending || !voiceFile}
                            className="inline-flex items-center rounded-full bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-70"
                          >
                            <Mic size={16} className="mr-2" />
                            提交音频
                          </button>
                        </div>
                      </div>
                    </div>
                  </div>
                )}

              </div>
            </section>
            {sessionAssistantSidebar}
          </div>
        ) : null}

        {stage === "report" && summary ? (
          <div className="page-enter-float space-y-6">
            <section className="overflow-hidden rounded-[2.25rem] border border-white/80 bg-white/82 p-6 shadow-[0_24px_80px_rgba(79,70,229,0.10)] backdrop-blur-xl lg:p-7">
              <div className="grid gap-8 xl:grid-cols-[minmax(0,1fr)_minmax(31rem,37rem)] xl:items-center">
                <div className="min-w-0">
                  <h1 className="text-[2rem] font-black tracking-tight text-slate-950 lg:text-[2.75rem]">{sessionTitle}</h1>
                  <p className="mt-3 max-w-2xl text-[15px] leading-7 text-slate-600">
                    系统已根据本轮问答表现，整理出整体分数、表达亮点、待改进点与下一步建议，方便你快速回看这一轮的表现。
                  </p>
                  <div className="mt-4 flex flex-wrap gap-3 text-sm text-slate-500">
                    {reportDisplayTime ? (
                      <span className="inline-flex items-center rounded-full bg-slate-100 px-3 py-1.5">
                        <Clock3 size={14} className="mr-2" />
                        会话创建于 {formatDateTime(reportDisplayTime)}
                      </span>
                    ) : null}
                    <span className="inline-flex items-center rounded-full bg-slate-100 px-3 py-1.5">
                      已作答 {textMessageCount} 轮
                    </span>
                    <span className="inline-flex items-center rounded-full bg-slate-100 px-3 py-1.5">
                      总分 {summary.overallScore}
                    </span>
                  </div>

                  <div className="mt-5 flex flex-wrap gap-3">
                    {isAudioAnswerMode ? (
                      <button
                        type="button"
                        onClick={() => {
                          void handlePlayTts("summary:report", summaryNarration);
                        }}
                        disabled={!summaryNarration || (ttsPendingKey !== null && ttsPendingKey !== "summary:report")}
                        className="inline-flex items-center rounded-full border border-slate-200 bg-white px-5 py-3 text-sm font-semibold text-slate-600 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600 disabled:cursor-not-allowed disabled:opacity-60"
                      >
                        {ttsPendingKey === "summary:report" ? (
                          <RefreshCw size={16} className="mr-2 animate-spin" />
                        ) : (
                          <Volume2 size={16} className="mr-2" />
                        )}
                        {ttsPlayingKey === "summary:report"
                          ? "停止朗读总结"
                          : ttsPendingKey === "summary:report"
                            ? "正在生成朗读"
                            : "朗读本轮总结"}
                      </button>
                    ) : null}
                    {sessionId ? (
                      <button
                        type="button"
                        onClick={() => {
                          navigateToInterviewStage("session", sessionId);
                        }}
                        className="inline-flex items-center rounded-full border border-slate-200 bg-white px-5 py-3 text-sm font-semibold text-slate-600 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600"
                      >
                        <MessageSquare size={16} className="mr-2" />
                        返回对话
                      </button>
                    ) : null}
                    {sessionId ? (
                      <Link
                        to={`/ai/history?type=interview&sessionId=${encodeURIComponent(sessionId)}`}
                        className="inline-flex items-center rounded-full border border-indigo-200 bg-indigo-50 px-5 py-3 text-sm font-semibold text-indigo-700 shadow-sm transition-colors hover:border-indigo-300 hover:bg-indigo-100"
                      >
                        <Sparkles size={16} className="mr-2" />
                        打开复盘中心
                      </Link>
                    ) : null}
                    <Link
                      to="/ai/resume"
                      className="inline-flex items-center rounded-full border border-emerald-200 bg-emerald-50 px-5 py-3 text-sm font-semibold text-emerald-700 shadow-sm transition-colors hover:border-emerald-300 hover:bg-emerald-100"
                    >
                      <Play size={16} className="mr-2" />
                      去看简历优化
                    </Link>
                    <button
                      type="button"
                      onClick={handleResetPractice}
                      className="inline-flex items-center rounded-full bg-gradient-to-r from-teal-500 to-indigo-600 px-5 py-3 text-sm font-semibold text-white shadow-[0_18px_40px_rgba(79,70,229,0.20)] transition-transform hover:-translate-y-0.5"
                    >
                      <RefreshCw size={16} className="mr-2" />
                      再练一轮
                    </button>
                  </div>
                </div>

                <div className="min-w-0 xl:justify-self-end">
                  <div className="grid gap-6 md:grid-cols-[minmax(0,1fr)_14.5rem] md:items-center xl:w-[40.5rem]">
                    <div className="min-w-0">
                      <div className="grid gap-4 sm:grid-cols-2">
                        {reportOutcomeCards.map((item) => (
                          <div
                            key={item.label}
                            className="min-h-[8.25rem] rounded-[1.65rem] border border-slate-200/70 bg-white/90 px-5 py-[1.125rem] shadow-[0_18px_38px_rgba(148,163,184,0.14)] backdrop-blur-sm"
                          >
                            <div className="text-sm font-semibold text-slate-500">{item.label}</div>
                            <div className="mt-2 text-[18px] font-semibold leading-7 text-slate-800">{item.value}</div>
                          </div>
                        ))}
                      </div>
                    </div>
                    <div className="flex shrink-0 justify-center md:justify-end">
                      <ScoreRing score={summary.overallScore} compact />
                    </div>
                  </div>
                </div>
              </div>
            </section>

            <section className="grid gap-6 lg:grid-cols-3">
              {summarySections.map((section) => (
                <SummaryCard key={section.id} section={section} />
              ))}
            </section>
          </div>
        ) : null}
      </main>

      <AnimatePresence>
        {toast ? (
          <motion.div
            key={toast.id}
            initial={{ opacity: 0, y: 18, scale: 0.96 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 8, scale: 0.96 }}
            className="fixed bottom-24 left-1/2 z-[80] w-full max-w-xl -translate-x-1/2 px-4"
          >
            <div className="flex items-center gap-3 rounded-full bg-slate-900/95 px-5 py-3 text-sm font-semibold text-white shadow-[0_24px_60px_rgba(15,23,42,0.28)] backdrop-blur">
              <AlertCircle size={18} className="shrink-0 text-rose-300" />
              <span className="min-w-0 flex-1 truncate">{toast.message}</span>
            </div>
          </motion.div>
        ) : null}
      </AnimatePresence>
    </div>
  );
}
