import { AnimatePresence, motion } from "framer-motion";
import {
  AlertCircle,
  AlertTriangle,
  ArrowLeft,
  ArrowRight,
  Bot,
  ChevronDown,
  ChevronRight,
  Clock,
  Download,
  FileSignature,
  FileText,
  GraduationCap,
  Layers,
  Lightbulb,
  MessageSquare,
  RefreshCw,
  Search,
  ShieldAlert,
  Sparkles,
  Target,
  ThumbsUp,
  Trash2,
  User,
  Zap,
  type LucideIcon,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import StudentWorkspaceTopbar, { buildStudentWorkspacePrimaryNav } from "../components/student/StudentWorkspaceTopbar";
import { ApiClientError, apiRequest, buildQuery } from "../lib/apiClient";
import { formatDate, formatDateTime, formatTime } from "../lib/formatters";
import type { ResumeHistoryDetail as ResumeExportDetail } from "../lib/resumeReportPdfDocument";

type HistoryTaskType = "ALL" | "RESUME" | "INTERVIEW_TEXT";
type TimeValue = number | string;

type SelectedQuery = { type: "resume"; recordId: number } | { type: "interview"; sessionId: string } | null;

type DeleteTarget = { type: "resume"; recordId: number } | { type: "interview"; sessionId: string };

type AiHistoryListItem = {
  id: number;
  taskType: "RESUME" | "INTERVIEW_TEXT";
  summary: string;
  pointsConsumed: number;
  sessionId: string | null;
  status: string | null;
  createdAt: TimeValue;
};

type AiHistoryResponse = {
  records: AiHistoryListItem[];
  total: number;
  page: number;
  size: number;
};

type AiAsyncTaskDetail = {
  taskId: string;
  status: string;
  resultSummary: string | null;
  linkedRecordId: number | null;
  errorCode: string | null;
  errorMessage: string | null;
  updatedAt: TimeValue | null;
  finishedAt: TimeValue | null;
};

type ResumeHistoryDetail = ResumeExportDetail;

type InterviewMessageItem = {
  role: "ASSISTANT" | "USER" | "SYSTEM" | string;
  text: string;
  coachFeedback: string | null;
  scoreHint: number | null;
  audioObjectKey: string | null;
  createdAt: TimeValue;
};

type InterviewSummary = {
  overallScore: number;
  strengths: string[];
  weaknesses: string[];
  suggestions: string[];
};

type InterviewSessionContextDetail = {
  interviewType: string;
  interviewerStyle: string;
  difficulty: string;
  answerMode: string;
  targetCompany: string;
  targetJobDescription: string;
  prepMaterialKeys: string[];
  answerHelperEnabled: boolean | null;
  answerHelperCueKeys: string[];
  promptContext: string;
};

type InterviewResumeContextDetail = {
  recordId: number;
  summary: string;
  suggestions: string[];
  scoreLabel: string;
  targetRole: string;
  targetContext: string;
  inputMode: string;
  jobDescription: string;
  pdfFileName: string | null;
  resumeTextExcerpt: string;
  createdAt: TimeValue;
};

type InterviewSessionDetail = {
  sessionId: string;
  targetRole: string;
  mode: string;
  status: string;
  prepaidPoints: number;
  reservedQuotaWeight: number;
  pointsBalance: number;
  replyRoundLimit: number;
  replyRoundUsed: number;
  endedByAi: boolean;
  finishReason: string;
  createdAt: TimeValue;
  summaryGeneratedAt: TimeValue | null;
  sessionContext: InterviewSessionContextDetail | null;
  resumeContext: InterviewResumeContextDetail | null;
  messages: InterviewMessageItem[];
  summary: InterviewSummary | null;
};

type OverviewState = {
  total: number | null;
  resumeCount: number | null;
  interviewCount: number | null;
  latestCreatedAt: TimeValue | null;
  loading: boolean;
  error: string | null;
};

type ListState = "loading" | "success" | "error" | "empty";
type DetailState = "idle" | "loading" | "success" | "error";

type ExportFeedback = {
  recordId: number;
  tone: "success" | "error" | "info";
  message: string;
} | null;

const PAGE_SIZE = 10;
const REVIEW_TIMEZONE_LESS_DATETIME_PATTERN = /^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2})(?::(\d{2})(\.\d{1,9})?)?$/;
const REVIEW_TIMEZONE_SUFFIX_PATTERN = /(Z|[+-]\d{2}:\d{2})$/i;

async function loadResumeReportExporter() {
  return import("../lib/resumeReportPrint");
}

const DICT = {
  STATUS: {
    ACTIVE: "进行中",
    COMPLETED: "已完成",
    ANALYZING: "分析中",
    FAILED: "生成失败",
    IN_PROGRESS: "进行中",
  } as Record<string, string>,
  INTERVIEW_MODE: {
    INTERVIEW_TEXT: "文本面试",
    INTERVIEW_VOICE: "语音面试",
    REAL_INTERVIEW: "全真模拟",
    PRESSURE_TEST: "压力测试",
    MOCK_INTERVIEW: "轻松练习",
  } as Record<string, string>,
  FINISH_REASON: {
    USER_MANUAL_FINISH: "用户主动结束",
    ROUND_LIMIT_REACHED: "达到轮次上限",
    SYSTEM_ERROR: "系统异常中断",
    ENOUGH_EVIDENCE: "证据充分，AI 主动结束",
    AI_JUDGEMENT: "AI 判断可结束",
  } as Record<string, string>,
  INPUT_MODE: {
    text: "文本输入",
    pdf: "PDF 解析",
  } as Record<string, string>,
  INTERVIEW_TYPE: {
    PROJECT_DEEP_DIVE: "项目深挖",
    FUNDAMENTALS: "八股基础",
    BEHAVIORAL: "行为面试",
    PRESSURE: "压力追问",
  } as Record<string, string>,
  INTERVIEWER_STYLE: {
    COACHING: "温和引导型",
    STANDARD: "常规校招型",
    PRESSURE: "高压追问型",
    HR: "HR 沟通型",
  } as Record<string, string>,
  DIFFICULTY: {
    EASY: "基础摸底",
    MEDIUM: "常规面试",
    HARD: "压力追问",
  } as Record<string, string>,
  ANSWER_MODE: {
    TEXT: "文字作答",
    VOICE: "语音作答",
  } as Record<string, string>,
  PREP_MATERIAL: {
    CAMPUS_BACKGROUND: "教育背景",
    JOB_STATUS: "求职状态",
    ACADEMIC_RECORDS: "成绩荣誉",
    GROWTH_PORTRAIT: "画像标签",
    LATEST_RESUME: "最新简历",
    SELF_INTRO: "自我介绍",
    SKILL_TAGS: "技能关键词",
    TARGET_COMPANY: "目标企业",
    TARGET_JD: "岗位要求",
  } as Record<string, string>,
  ANSWER_HELPER_CUE: {
    STAR: "STAR 结构提醒",
    METRICS: "量化结果提醒",
    COMPLETENESS: "回答完整度提示",
  } as Record<string, string>,
};

function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function isActiveInterviewStatus(status: string | null | undefined) {
  return (status ?? "").trim().toUpperCase() === "ACTIVE";
}

function normalizeReviewTimeValue(value: TimeValue | null | undefined) {
  if (typeof value !== "string") {
    return value;
  }

  const normalized = value.trim();
  if (
    !normalized
    || /^\d+$/.test(normalized)
    || REVIEW_TIMEZONE_SUFFIX_PATTERN.test(normalized)
    || /^\d{4}-\d{2}-\d{2}$/.test(normalized)
  ) {
    return normalized;
  }

  const match = normalized.match(REVIEW_TIMEZONE_LESS_DATETIME_PATTERN);
  if (!match) {
    return normalized;
  }

  const secondsPart = match[3] ? `:${match[3]}` : "";
  const fractionPart = match[4] ?? "";
  return `${match[1]}T${match[2]}${secondsPart}${fractionPart}Z`;
}

function formatDateTimeLabel(value?: TimeValue | null, fallback = "—") {
  return formatDateTime(normalizeReviewTimeValue(value), fallback);
}

function formatDateLabel(value?: TimeValue | null, fallback = "—") {
  return formatDate(normalizeReviewTimeValue(value), fallback);
}

function formatTimeLabel(value?: TimeValue | null, fallback = "—") {
  return formatTime(normalizeReviewTimeValue(value), fallback);
}

function getMappedLabel(value: string | null | undefined, dict: Record<string, string>, fallback = "未填写") {
  const normalized = (value ?? "").trim();
  if (!normalized) {
    return fallback;
  }
  return dict[normalized] || normalized;
}

function formatMappedLabelList(values: string[] | null | undefined, dict: Record<string, string>, fallback = "未带入") {
  if (!values || values.length === 0) {
    return fallback;
  }
  return values.map((value) => getMappedLabel(value, dict, value)).join(" / ");
}

function getDisplayStatus(item: AiHistoryListItem) {
  if (item.taskType === "RESUME") {
    return "已生成";
  }

  return DICT.STATUS[item.status || ""] || item.status || "未知";
}

function parseSelectedQuery(searchParams: URLSearchParams): SelectedQuery {
  const type = searchParams.get("type");

  if (type === "resume") {
    const recordId = Number(searchParams.get("recordId"));
    if (!Number.isNaN(recordId) && recordId > 0) {
      return { type: "resume", recordId };
    }
  }

  if (type === "interview") {
    const sessionId = searchParams.get("sessionId");
    if (sessionId) {
      return { type: "interview", sessionId };
    }
  }

  return null;
}

function parseResumeTaskIdQuery(searchParams: URLSearchParams) {
  if (searchParams.get("type") !== "resume") {
    return null;
  }
  const taskId = searchParams.get("taskId");
  return taskId?.trim() ? taskId.trim() : null;
}

function deriveInitialTab(searchParams: URLSearchParams): HistoryTaskType {
  const selectedQuery = parseSelectedQuery(searchParams);
  if (!selectedQuery) {
    return parseResumeTaskIdQuery(searchParams) ? "RESUME" : "ALL";
  }
  return selectedQuery.type === "resume" ? "RESUME" : "INTERVIEW_TEXT";
}

export default function AiReviewCenterPage() {
  const { role, displayName } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const selectedQuery = useMemo(() => parseSelectedQuery(searchParams), [searchParams]);
  const resumeTaskId = useMemo(() => parseResumeTaskIdQuery(searchParams), [searchParams]);
  const [activeTab, setActiveTab] = useState<HistoryTaskType>(() => deriveInitialTab(searchParams));

  const [overview, setOverview] = useState<OverviewState>({
    total: null,
    resumeCount: null,
    interviewCount: null,
    latestCreatedAt: null,
    loading: true,
    error: null,
  });
  const [listState, setListState] = useState<ListState>("loading");
  const [listRecords, setListRecords] = useState<AiHistoryListItem[]>([]);
  const [listPage, setListPage] = useState(1);
  const [listTotal, setListTotal] = useState(0);
  const [listError, setListError] = useState<string | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);

  const [detailState, setDetailState] = useState<DetailState>("idle");
  const [detailError, setDetailError] = useState<string | null>(null);
  const [resumeDetailMap, setResumeDetailMap] = useState<Record<number, ResumeHistoryDetail>>({});
  const [interviewDetailMap, setInterviewDetailMap] = useState<Record<string, InterviewSessionDetail>>({});
  const [resumeTaskState, setResumeTaskState] = useState<{
    loading: boolean;
    error: string | null;
    detail: AiAsyncTaskDetail | null;
  }>({
    loading: false,
    error: null,
    detail: null,
  });

  const [deleteConfirmTarget, setDeleteConfirmTarget] = useState<DeleteTarget | null>(null);
  const [deletePending, setDeletePending] = useState(false);
  const [exportingRecordId, setExportingRecordId] = useState<number | null>(null);
  const [exportFeedback, setExportFeedback] = useState<ExportFeedback>(null);

  const listRequestIdRef = useRef(0);
  const overviewRequestIdRef = useRef(0);
  const listScrollContainerRef = useRef<HTMLDivElement | null>(null);

  const currentTaskType = activeTab === "ALL" ? undefined : activeTab;
  const hasMore = listRecords.length < listTotal;
  const currentResumeDetail = selectedQuery?.type === "resume" ? resumeDetailMap[selectedQuery.recordId] ?? null : null;
  const currentInterviewDetail = selectedQuery?.type === "interview" ? interviewDetailMap[selectedQuery.sessionId] ?? null : null;

  const updateSelectedQuery = useCallback((nextQuery: SelectedQuery | null) => {
    // 选中详情写入 URL，AI 通知和刷新页面都能恢复同一条记录。
    const nextParams = new URLSearchParams(searchParams);
    nextParams.delete("type");
    nextParams.delete("recordId");
    nextParams.delete("sessionId");
    nextParams.delete("taskId");

    if (nextQuery?.type === "resume") {
      nextParams.set("type", "resume");
      nextParams.set("recordId", String(nextQuery.recordId));
    } else if (nextQuery?.type === "interview") {
      nextParams.set("type", "interview");
      nextParams.set("sessionId", nextQuery.sessionId);
    }

    setSearchParams(nextParams, { replace: true });
  }, [searchParams, setSearchParams]);

  const loadOverview = useCallback(async () => {
    if (role !== "STUDENT") {
      setOverview({
        total: 0,
        resumeCount: 0,
        interviewCount: 0,
        latestCreatedAt: null,
        loading: false,
        error: null,
      });
      return;
    }

    const requestId = ++overviewRequestIdRef.current;
    setOverview((current) => ({ ...current, loading: true, error: null }));

    try {
      // 概览只需要 total 和最新时间，用 size=1 减少历史列表开销。
      const [allResponse, resumeResponse, interviewResponse] = await Promise.all([
        apiRequest<AiHistoryResponse>(`/ai/history${buildQuery({ page: 1, size: 1 })}`),
        apiRequest<AiHistoryResponse>(`/ai/history${buildQuery({ page: 1, size: 1, taskType: "RESUME" })}`),
        apiRequest<AiHistoryResponse>(`/ai/history${buildQuery({ page: 1, size: 1, taskType: "INTERVIEW_TEXT" })}`),
      ]);

      if (overviewRequestIdRef.current !== requestId) {
        return;
      }

      setOverview({
        total: allResponse.total,
        resumeCount: resumeResponse.total,
        interviewCount: interviewResponse.total,
        latestCreatedAt: allResponse.records[0]?.createdAt ?? null,
        loading: false,
        error: null,
      });
    } catch (error) {
      if (overviewRequestIdRef.current !== requestId) {
        return;
      }

      const apiError = error as ApiClientError;
      setOverview((current) => ({
        ...current,
        loading: false,
        error: apiError.message || "概览数据加载失败",
      }));
    }
  }, [role]);

  const loadListPage = useCallback(async (nextPage: number, mode: "replace" | "append" = "replace") => {
    if (role !== "STUDENT") {
      setListRecords([]);
      setListPage(1);
      setListTotal(0);
      setListState("empty");
      setListError(null);
      return;
    }

    const requestId = ++listRequestIdRef.current;

    if (mode === "replace") {
      // tab 切换时清空旧列表，避免简历和面试记录短暂混排。
      setListState("loading");
      setListError(null);
      setListRecords([]);
      setListPage(1);
      setListTotal(0);
    } else {
      setLoadingMore(true);
    }

    try {
      // 历史列表统一走 /ai/history，taskType 决定简历或面试筛选。
      const response = await apiRequest<AiHistoryResponse>(`/ai/history${buildQuery({
        page: nextPage,
        size: PAGE_SIZE,
        taskType: currentTaskType,
      })}`);

      if (listRequestIdRef.current !== requestId) {
        return;
      }

      setListRecords((current) => (mode === "append" ? [...current, ...response.records] : response.records));
      setListPage(response.page);
      setListTotal(response.total);
      setListError(null);
      setListState(response.records.length === 0 && mode === "replace" ? "empty" : "success");
    } catch (error) {
      if (listRequestIdRef.current !== requestId) {
        return;
      }

      const apiError = error as ApiClientError;
      setListError(apiError.message || "列表加载失败");
      setListState("error");
    } finally {
      if (listRequestIdRef.current === requestId) {
        setLoadingMore(false);
      }
    }
  }, [currentTaskType, role]);

  useEffect(() => {
    void loadOverview();
  }, [loadOverview]);

  useEffect(() => {
    void loadListPage(1, "replace");
  }, [loadListPage]);

  useEffect(() => {
    if (!resumeTaskId) {
      setResumeTaskState({
        loading: false,
        error: null,
        detail: null,
      });
      return;
    }

    if (role !== "STUDENT") {
      return;
    }

    let active = true;
    setResumeTaskState({
      loading: true,
      error: null,
      detail: null,
    });

    // 简历异步任务通知可能先落到 taskId，完成后再跳转到真实 report record。
    void apiRequest<AiAsyncTaskDetail>(`/ai/tasks/${resumeTaskId}`)
      .then((response) => {
        if (!active) {
          return;
        }

        setResumeTaskState({
          loading: false,
          error: null,
          detail: response,
        });

        if (response.linkedRecordId && response.linkedRecordId > 0) {
          const nextParams = new URLSearchParams(searchParams);
          nextParams.set("type", "resume");
          nextParams.set("recordId", String(response.linkedRecordId));
          nextParams.delete("taskId");
          setSearchParams(nextParams, { replace: true });
        }
      })
      .catch((error) => {
        if (!active) {
          return;
        }
        const apiError = error as ApiClientError;
        setResumeTaskState({
          loading: false,
          error: apiError.message || "任务状态加载失败",
          detail: null,
        });
      });

    return () => {
      active = false;
    };
  }, [resumeTaskId, role, searchParams, setSearchParams]);

  useEffect(() => {
    if (listState === "loading") {
      return;
    }

    if (resumeTaskId) {
      return;
    }

    if (listRecords.length === 0) {
      if (selectedQuery) {
        updateSelectedQuery(null);
      }
      return;
    }

    const currentSelectedExists = listRecords.some((item) => (
      (selectedQuery?.type === "resume" && item.taskType === "RESUME" && item.id === selectedQuery.recordId)
      || (selectedQuery?.type === "interview" && item.taskType === "INTERVIEW_TEXT" && item.sessionId === selectedQuery.sessionId)
    ));

    if (!currentSelectedExists) {
      // 当前选中记录不在筛选列表里时，默认选中当前列表第一条。
      const firstRecord = listRecords[0];
      if (firstRecord.taskType === "RESUME") {
        updateSelectedQuery({ type: "resume", recordId: firstRecord.id });
      } else if (firstRecord.sessionId) {
        updateSelectedQuery({ type: "interview", sessionId: firstRecord.sessionId });
      }
    }
  }, [listRecords, listState, selectedQuery, updateSelectedQuery]);

  useEffect(() => {
    setExportFeedback(null);
  }, [selectedQuery]);

  useEffect(() => {
    if (role !== "STUDENT" || !selectedQuery) {
      setDetailState("idle");
      setDetailError(null);
      return;
    }

    if (selectedQuery.type === "resume" && resumeDetailMap[selectedQuery.recordId]) {
      setDetailState("success");
      setDetailError(null);
      return;
    }

    if (selectedQuery.type === "interview" && interviewDetailMap[selectedQuery.sessionId]) {
      setDetailState("success");
      setDetailError(null);
      return;
    }

    let active = true;
    setDetailState("loading");
    setDetailError(null);

    const loadDetail = async () => {
      try {
        if (selectedQuery.type === "resume") {
          // 简历详情拉 preview，导出和复盘展示共用同一份报告结构。
          const response = await apiRequest<ResumeHistoryDetail>(`/ai/history/resume/${selectedQuery.recordId}/preview`);
          if (!active) {
            return;
          }
          setResumeDetailMap((current) => ({ ...current, [response.recordId]: response }));
        } else {
          // 面试详情直接读取 session，包含问题、回答和总结复盘。
          const response = await apiRequest<InterviewSessionDetail>(`/ai/interview/sessions/${selectedQuery.sessionId}`);
          if (!active) {
            return;
          }
          setInterviewDetailMap((current) => ({ ...current, [response.sessionId]: response }));
        }

        if (active) {
          setDetailState("success");
        }
      } catch (error) {
        const apiError = error as ApiClientError;
        if (!active) {
          return;
        }
        setDetailState("error");
        setDetailError(apiError.message || "详情加载失败");
      }
    };

    void loadDetail();

    return () => {
      active = false;
    };
  }, [interviewDetailMap, resumeDetailMap, role, selectedQuery]);

  const handleLoadMore = useCallback(async () => {
    if (!hasMore || loadingMore || listState !== "success") {
      return;
    }

    await loadListPage(listPage + 1, "append");
  }, [hasMore, listPage, listState, loadListPage, loadingMore]);

  const handleListScroll = useCallback(() => {
    const container = listScrollContainerRef.current;
    if (!container || loadingMore || !hasMore || listState !== "success") {
      return;
    }

    const remainingDistance = container.scrollHeight - container.scrollTop - container.clientHeight;
    if (remainingDistance <= 160) {
      // 列表接近底部自动加载下一页，和按钮加载复用同一入口。
      void handleLoadMore();
    }
  }, [handleLoadMore, hasMore, listState, loadingMore]);

  useEffect(() => {
    if (listState !== "success") {
      return;
    }

    const container = listScrollContainerRef.current;
    if (!container) {
      return;
    }

    if (container.scrollHeight <= container.clientHeight + 24 && hasMore && !loadingMore) {
      void handleLoadMore();
    }
  }, [handleLoadMore, hasMore, listRecords, listState, loadingMore]);

  async function handleExport(recordId: number, detail?: ResumeHistoryDetail) {
    setExportingRecordId(recordId);
    setExportFeedback({
      recordId,
      tone: "info",
      message: "正在打开打印预览，请稍候。",
    });

    try {
      // 当前导出以浏览器打印预览为主，导出器懒加载减少首屏包体积。
      const { exportResumeReportAsPdf } = await loadResumeReportExporter();
      await exportResumeReportAsPdf(detail ?? recordId, displayName);

      setExportFeedback({
        recordId,
        tone: "success",
        message: "已打开浏览器打印预览，请选择另存为 PDF。",
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

  async function executeDelete() {
    if (!deleteConfirmTarget) {
      return;
    }

    setDeletePending(true);

    try {
      if (deleteConfirmTarget.type === "resume") {
        // 删除记录后清掉本地详情缓存，再刷新概览和列表。
        await apiRequest(`/ai/history/resume/${deleteConfirmTarget.recordId}`, { method: "DELETE" });
        setResumeDetailMap((current) => {
          const nextMap = { ...current };
          delete nextMap[deleteConfirmTarget.recordId];
          return nextMap;
        });
      } else {
        // 面试删除按 sessionId 处理，避免和简历 recordId 空间混淆。
        await apiRequest(`/ai/interview/sessions/${deleteConfirmTarget.sessionId}`, { method: "DELETE" });
        setInterviewDetailMap((current) => {
          const nextMap = { ...current };
          delete nextMap[deleteConfirmTarget.sessionId];
          return nextMap;
        });
      }

      setDeleteConfirmTarget(null);
      await Promise.all([loadOverview(), loadListPage(1, "replace")]);
    } catch (error) {
      const apiError = error as ApiClientError;
      setDetailError(apiError.message || "删除记录失败");
      setDetailState("error");
    } finally {
      setDeletePending(false);
    }
  }

  return (
    <div className="relative flex h-screen flex-col overflow-hidden bg-[#eef3ff] text-slate-900">
      <div className="pointer-events-none fixed inset-0 z-0 overflow-hidden">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(79,70,229,0.18),transparent_30%),radial-gradient(circle_at_bottom_right,rgba(20,184,166,0.12),transparent_28%),linear-gradient(180deg,#eff6ff_0%,#f8fafc_58%,#f8fafc_100%)]" />
      </div>

      <TopNav />

      <main className="page-enter-float relative z-10 mx-auto flex min-h-0 w-full max-w-[96rem] flex-1 flex-col overflow-hidden px-4 pb-4 pt-6 sm:px-6 lg:px-8 lg:pb-6 lg:pt-8">
        <OverviewSection overview={overview} />

        <div className="grid min-h-0 flex-1 gap-6 lg:grid-cols-12 lg:[grid-template-rows:minmax(0,1fr)]">
          <aside className="flex min-h-0 flex-col overflow-hidden rounded-[2rem] border border-white/70 bg-white/84 shadow-[0_22px_60px_rgba(148,163,184,0.18)] backdrop-blur-xl lg:col-span-4">
            <div className="border-b border-slate-100/80 p-5">
              <div className="flex rounded-full border border-slate-200/60 bg-slate-100/80 p-1">
                {(["ALL", "RESUME", "INTERVIEW_TEXT"] as const).map((tab) => (
                  <button
                    key={tab}
                    type="button"
                    onClick={() => setActiveTab(tab)}
                    className={joinClasses(
                      "flex-1 rounded-full py-2.5 text-[15px] font-semibold tracking-tight transition-all duration-300 lg:text-base",
                      activeTab === tab
                        ? "border border-slate-200/50 bg-white text-indigo-700 shadow-sm"
                        : "text-slate-500 hover:bg-slate-200/50 hover:text-slate-700",
                    )}
                  >
                    {tab === "ALL" && "全部记录"}
                    {tab === "RESUME" && "简历复盘"}
                    {tab === "INTERVIEW_TEXT" && "模拟面试"}
                  </button>
                ))}
              </div>
            </div>

            <div
              ref={listScrollContainerRef}
              onScroll={handleListScroll}
              className="custom-scrollbar min-h-0 flex-1 overflow-y-auto overscroll-contain"
            >
              <motion.div
                key={activeTab}
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ duration: 0.14, ease: [0.22, 1, 0.36, 1] }}
                className="min-h-full"
              >
                {renderListContent({
                  listState,
                  listRecords,
                  listPage,
                  listTotal,
                  listError,
                  selectedQuery,
                  loadingMore,
                  hasMore,
                  onRetry: () => {
                    void Promise.all([loadOverview(), loadListPage(1, "replace")]);
                  },
                  onSelect: updateSelectedQuery,
                })}
              </motion.div>
            </div>
          </aside>

          <section className="relative flex min-h-0 flex-col overflow-hidden rounded-[2rem] border border-white/70 bg-white/95 shadow-[0_22px_60px_rgba(148,163,184,0.18)] backdrop-blur-xl lg:col-span-8">
            <AnimatePresence mode="wait">
              {selectedQuery ? (
                <motion.div
                  key={selectedQuery.type === "resume" ? `resume-${selectedQuery.recordId}` : `interview-${selectedQuery.sessionId}`}
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -10 }}
                  className="absolute inset-0"
                >
                  <DetailWorkspace
                    selectedQuery={selectedQuery}
                    detailState={detailState}
                    detailError={detailError}
                    resumeDetail={currentResumeDetail}
                    interviewDetail={currentInterviewDetail}
                    deletePending={deletePending}
                    exportingRecordId={exportingRecordId}
                    exportFeedback={exportFeedback}
                    onRetry={() => {
                      if (selectedQuery.type === "resume") {
                        setResumeDetailMap((current) => {
                          const nextMap = { ...current };
                          delete nextMap[selectedQuery.recordId];
                          return nextMap;
                        });
                      } else {
                        setInterviewDetailMap((current) => {
                          const nextMap = { ...current };
                          delete nextMap[selectedQuery.sessionId];
                          return nextMap;
                        });
                      }
                    }}
                    onRequestDelete={setDeleteConfirmTarget}
                    onExport={(recordId) => {
                      void handleExport(recordId);
                    }}
                  />
                </motion.div>
              ) : (
                <motion.div
                  key={resumeTaskId ? "task-selection" : "empty-selection"}
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  exit={{ opacity: 0 }}
                  className="absolute inset-0"
                >
                  {resumeTaskId ? (
                    <AsyncTaskStatusPlaceholder
                      taskId={resumeTaskId}
                      loading={resumeTaskState.loading}
                      error={resumeTaskState.error}
                      detail={resumeTaskState.detail}
                    />
                  ) : (
                    <EmptyDetailPlaceholder />
                  )}
                </motion.div>
              )}
            </AnimatePresence>
          </section>
        </div>
      </main>

      <AnimatePresence>
        {deleteConfirmTarget ? (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4 backdrop-blur-sm">
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.95 }}
              className="w-full max-w-sm rounded-3xl bg-white p-6 shadow-2xl"
            >
              <div className="flex items-start gap-4">
                <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-rose-100 text-rose-600">
                  <AlertTriangle size={24} />
                </div>
                <div>
                  <h3 className="text-lg font-bold text-slate-900">确认删除记录？</h3>
                  <p className="mt-1 text-sm text-slate-500">删除后将无法找回此复盘数据，该操作不可逆转。</p>
                </div>
              </div>

              <div className="mt-6 flex justify-end gap-3">
                <button
                  type="button"
                  onClick={() => setDeleteConfirmTarget(null)}
                  className="rounded-full bg-slate-100 px-4 py-2 text-sm font-semibold text-slate-600 hover:bg-slate-200"
                >
                  取消
                </button>
                <button
                  type="button"
                  onClick={() => {
                    void executeDelete();
                  }}
                  disabled={deletePending}
                  className="rounded-full bg-rose-600 px-4 py-2 text-sm font-semibold text-white hover:bg-rose-700 disabled:cursor-not-allowed disabled:bg-rose-300"
                >
                  {deletePending ? "删除中..." : "确认删除"}
                </button>
              </div>
            </motion.div>
          </div>
        ) : null}
      </AnimatePresence>

      <style>{`
        .custom-scrollbar::-webkit-scrollbar { width: 6px; }
        .custom-scrollbar::-webkit-scrollbar-track { background: transparent; }
        .custom-scrollbar::-webkit-scrollbar-thumb { background-color: rgba(203, 213, 225, 0.4); border-radius: 20px; }
        .custom-scrollbar:hover::-webkit-scrollbar-thumb { background-color: rgba(148, 163, 184, 0.6); }
      `}</style>
    </div>
  );
}

function TopNav() {
  return (
    <StudentWorkspaceTopbar
      sectionLabel="AI Review Center"
      title="AI 复盘中心"
      navItems={buildStudentWorkspacePrimaryNav("reviewCenter")}
      rightActions={(
        <Link
          to="/student/dashboard"
          className="inline-flex h-10 items-center gap-2 rounded-full border border-slate-200 bg-white px-4 text-sm font-semibold text-slate-600 shadow-sm transition-colors hover:border-slate-300 hover:text-slate-900"
        >
          <ArrowLeft size={16} />
          返回工作台
        </Link>
      )}
      position="sticky"
    />
  );
}

function OverviewSection({ overview }: { overview: OverviewState }) {
  const latestDateDisplay = overview.loading ? "..." : formatDateLabel(overview.latestCreatedAt, "暂无记录");
  const latestTimeDisplay = overview.loading
    ? "..."
    : overview.latestCreatedAt
      ? formatTimeLabel(overview.latestCreatedAt, "--:--")
      : "等待新记录";

  const stats: Array<{
    label: string;
    value: number | string;
    icon: LucideIcon;
    color: string;
    surfaceClass: string;
    glowClass: string;
    iconWrapClass: string;
    valueClass: string;
    secondaryValue?: string;
    isTimestamp?: boolean;
  }> = [
    {
      label: "全部复盘记录",
      value: overview.loading ? "..." : overview.total ?? "—",
      icon: Layers,
      color: "text-indigo-700",
      surfaceClass: "border-indigo-100/80 bg-gradient-to-br from-indigo-50 via-white to-blue-100/80",
      glowClass: "bg-indigo-200/60",
      iconWrapClass: "border-indigo-200/70 bg-white/85 shadow-[0_12px_26px_rgba(99,102,241,0.16)]",
      valueClass: "text-indigo-950",
    },
    {
      label: "简历复盘",
      value: overview.loading ? "..." : overview.resumeCount ?? "—",
      icon: FileText,
      color: "text-amber-700",
      surfaceClass: "border-amber-100/80 bg-gradient-to-br from-amber-50 via-white to-orange-100/75",
      glowClass: "bg-amber-200/60",
      iconWrapClass: "border-amber-200/70 bg-white/85 shadow-[0_12px_26px_rgba(245,158,11,0.15)]",
      valueClass: "text-amber-950",
    },
    {
      label: "模拟面试",
      value: overview.loading ? "..." : overview.interviewCount ?? "—",
      icon: Bot,
      color: "text-teal-700",
      surfaceClass: "border-teal-100/80 bg-gradient-to-br from-teal-50 via-white to-cyan-100/75",
      glowClass: "bg-teal-200/60",
      iconWrapClass: "border-teal-200/70 bg-white/85 shadow-[0_12px_26px_rgba(20,184,166,0.15)]",
      valueClass: "text-teal-950",
    },
    {
      label: "最近一次",
      value: latestDateDisplay,
      secondaryValue: latestTimeDisplay,
      icon: Clock,
      color: "text-slate-700",
      surfaceClass: "border-slate-200/80 bg-gradient-to-br from-slate-50 via-white to-slate-100/85",
      glowClass: "bg-slate-200/70",
      iconWrapClass: "border-slate-200/80 bg-white/90 shadow-[0_12px_26px_rgba(148,163,184,0.16)]",
      valueClass: "text-slate-900",
      isTimestamp: true,
    },
  ];

  return (
    <section className="mb-8 grid gap-5 xl:grid-cols-[1.08fr_2.42fr]">
      <div className="relative overflow-hidden rounded-[2.25rem] bg-gradient-to-br from-indigo-900 via-indigo-800 to-blue-950 px-7 py-7 text-white shadow-[0_24px_58px_rgba(67,56,202,0.28)]">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_right,rgba(255,255,255,0.16),transparent_36%),linear-gradient(135deg,transparent_0%,rgba(255,255,255,0.04)_100%)]" />
        <div className="absolute -bottom-10 -right-10 opacity-20">
          <Layers size={140} />
        </div>
        <div className="relative z-10 flex h-full flex-col justify-center">
          <div className="mb-4 inline-flex w-fit items-center gap-2 rounded-full border border-white/16 bg-white/10 px-3 py-1.5 text-[11px] text-indigo-100">
            <Sparkles size={12} />
            统一复盘工作台
          </div>
          <h1 className="mb-2 text-3xl font-semibold tracking-tight lg:text-4xl">你的成长资产</h1>
          <p className="mt-2 max-w-[90%] text-sm leading-relaxed text-indigo-100/80">
            这里沉淀了你每一次 AI 面试与简历优化的真实反馈。
            <br />
            决定下一步是继续打磨、再次挑战，还是寻求导师帮助。
          </p>
          {overview.error ? (
            <div className="mt-4 inline-flex w-fit items-center rounded-full border border-amber-200/40 bg-amber-400/10 px-3 py-1.5 text-xs text-amber-100">
              <AlertTriangle size={14} className="mr-2" />
              概览统计暂未完全同步，列表数据仍可正常查看
            </div>
          ) : null}
        </div>
      </div>

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        {stats.map((stat) => (
          <div
            key={stat.label}
            className={joinClasses(
              "relative flex min-h-[164px] overflow-hidden rounded-[1.9rem] border p-6 shadow-[0_22px_60px_rgba(148,163,184,0.18)] backdrop-blur-xl",
              stat.surfaceClass,
            )}
          >
            <div className={joinClasses("absolute -right-10 -top-10 h-28 w-28 rounded-full blur-3xl", stat.glowClass)} />

            <div className="relative flex w-full items-center justify-between gap-4">
              <div className="flex min-w-0 flex-1 flex-col justify-center gap-2.5">
                <div className="min-w-0 text-[18px] font-black leading-tight tracking-tight text-slate-800 lg:text-[19px]">
                  {stat.label}
                </div>
                {stat.isTimestamp ? (
                  <div className="space-y-1.5">
                    <div className={joinClasses("text-xl font-black leading-tight tracking-tight", stat.valueClass)}>{stat.value}</div>
                    <div className="text-base font-semibold leading-tight text-slate-600">{stat.secondaryValue}</div>
                  </div>
                ) : (
                  <div className="text-[2.3rem] font-black leading-none tracking-tight">
                    <span className={joinClasses("drop-shadow-[0_8px_18px_rgba(255,255,255,0.3)]", stat.valueClass)}>{stat.value}</span>
                    {typeof stat.value === "number" ? (
                      <span className="ml-1 text-sm font-semibold text-slate-500">条</span>
                    ) : null}
                  </div>
                )}
              </div>

              <div
                className={joinClasses(
                  "flex h-12 w-12 shrink-0 items-center justify-center self-center rounded-[1rem] border",
                  stat.iconWrapClass,
                  stat.color,
                )}
              >
                <stat.icon size={19} />
              </div>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

function renderListContent({
  listState,
  listRecords,
  listPage,
  listTotal,
  listError,
  selectedQuery,
  loadingMore,
  hasMore,
  onRetry,
  onSelect,
}: {
  listState: ListState;
  listRecords: AiHistoryListItem[];
  listPage: number;
  listTotal: number;
  listError: string | null;
  selectedQuery: SelectedQuery;
  loadingMore: boolean;
  hasMore: boolean;
  onRetry: () => void;
  onSelect: (nextQuery: SelectedQuery | null) => void;
}) {
  if (listState === "loading") {
    return (
      <div className="flex h-full min-h-[18rem] flex-col items-center justify-center gap-4 p-6 text-center">
        <div className="flex h-14 w-14 items-center justify-center rounded-full border border-indigo-100 bg-indigo-50 text-indigo-600 shadow-sm">
          <RefreshCw size={20} className="animate-spin" />
        </div>
        <div>
          <p className="text-sm font-semibold text-slate-800">正在同步复盘记录</p>
          <p className="mt-1 text-xs leading-6 text-slate-500">拿到数据后会直接更新列表。</p>
        </div>
      </div>
    );
  }

  if (listState === "error") {
    return (
      <div className="flex h-full flex-col items-center justify-center p-6 text-center">
        <AlertCircle size={32} className="mb-4 text-rose-400" />
        <p className="text-sm font-semibold text-slate-800">列表加载失败</p>
        <p className="mt-2 max-w-xs text-xs leading-6 text-slate-500">{listError || "请稍后重试列表请求。"}</p>
        <button type="button" onClick={onRetry} className="mt-4 text-xs font-semibold text-indigo-600 hover:underline">
          点击重试
        </button>
      </div>
    );
  }

  if (listState === "empty" || listRecords.length === 0) {
    return <EmptyListPlaceholder />;
  }

  return (
    <div className="flex min-h-full flex-col gap-2 p-3 pb-6">
      <div className="flex items-center justify-between px-1 pb-1 text-xs font-medium text-slate-400">
        <span>当前已加载 {listRecords.length} 条</span>
        <span>第 {listPage} 页 / 共 {Math.max(1, Math.ceil(listTotal / PAGE_SIZE))} 页</span>
      </div>

      <div className="space-y-2">
        {listRecords.map((item) => {
        const isSelected = (
          (selectedQuery?.type === "resume" && item.taskType === "RESUME" && selectedQuery.recordId === item.id)
          || (selectedQuery?.type === "interview" && item.taskType === "INTERVIEW_TEXT" && selectedQuery.sessionId === item.sessionId)
        );
        const displayStatus = getDisplayStatus(item);

          return (
            <motion.div
              key={item.taskType === "RESUME" ? `resume-${item.id}` : `interview-${item.sessionId ?? item.id}`}
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ duration: 0.12, ease: "easeOut" }}
              onClick={() => {
                if (item.taskType === "RESUME") {
                  onSelect({ type: "resume", recordId: item.id });
                  return;
                }

                if (item.sessionId) {
                  onSelect({ type: "interview", sessionId: item.sessionId });
                }
              }}
              className={joinClasses(
                "group relative cursor-pointer rounded-[1.25rem] border p-4 transition-all duration-200",
                isSelected
                  ? "border-indigo-200 bg-indigo-50/60 shadow-[0_8px_20px_rgba(79,70,229,0.06)]"
                  : "border-transparent hover:border-slate-200 hover:bg-white/60",
              )}
            >
              {isSelected ? <div className="absolute left-0 top-1/2 h-8 w-1 -translate-y-1/2 rounded-r-full bg-indigo-500" /> : null}

              <div className="mb-2 flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <div className={joinClasses(
                    "flex items-center justify-center rounded-lg border p-2",
                    item.taskType === "RESUME"
                      ? "border-amber-100 bg-amber-50 text-amber-600"
                      : "border-teal-100 bg-teal-50 text-teal-600",
                  )}
                  >
                    {item.taskType === "RESUME" ? <FileText size={15} /> : <Bot size={15} />}
                  </div>
                  <span className="text-sm font-bold text-slate-700">
                    {item.taskType === "RESUME" ? "简历复盘" : "模拟面试"}
                  </span>
                  <span className={joinClasses(
                    "rounded border px-2 py-0.5 text-[11px]",
                    displayStatus === "已生成" || displayStatus === "已完成"
                      ? "border-emerald-100 bg-emerald-50 text-emerald-600"
                      : "border-slate-200 bg-slate-100 text-slate-500",
                  )}
                  >
                    {displayStatus}
                  </span>
                </div>

                <span className="text-xs font-medium text-slate-400">{formatDateLabel(item.createdAt)}</span>
              </div>

              <p className="mb-3 mt-1 line-clamp-2 text-sm leading-6 text-slate-600">
                {item.summary || "生成中..."}
              </p>

              <div className="mt-auto flex items-center justify-between">
                <div className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2.5 py-1 text-[11px] font-semibold text-slate-500">
                  <Zap size={11} className="text-orange-400" />
                  消耗 {item.pointsConsumed} 积分
                </div>
                {isSelected ? null : (
                  <div className="flex items-center text-xs font-bold text-indigo-500 opacity-0 transition-opacity group-hover:opacity-100">
                    查看详情
                    <ChevronRight size={12} />
                  </div>
                )}
              </div>
            </motion.div>
          );
        })}
      </div>

      <div className="mt-auto pt-2">
        {loadingMore ? (
          <div className="flex w-full items-center justify-center gap-1 rounded-xl border border-dashed border-slate-200 bg-slate-50/50 py-3 text-[11px] font-semibold text-slate-400">
            <ChevronDown size={14} className="animate-bounce" />
            正在加载更多记录...
          </div>
        ) : hasMore ? (
          <div className="rounded-xl border border-dashed border-slate-200 bg-slate-50/50 px-4 py-3 text-center text-[11px] font-medium text-slate-400">
            继续向下滚动，自动加载更多记录
          </div>
        ) : (
          <div className="rounded-xl border border-dashed border-slate-200 bg-slate-50/50 px-4 py-3 text-center text-xs font-medium text-slate-400">
            已展示当前筛选下的全部 {listTotal} 条记录
          </div>
        )}
      </div>
    </div>
  );
}

function DetailWorkspace({
  selectedQuery,
  detailState,
  detailError,
  resumeDetail,
  interviewDetail,
  deletePending,
  exportingRecordId,
  exportFeedback,
  onRetry,
  onRequestDelete,
  onExport,
}: {
  selectedQuery: SelectedQuery;
  detailState: DetailState;
  detailError: string | null;
  resumeDetail: ResumeHistoryDetail | null;
  interviewDetail: InterviewSessionDetail | null;
  deletePending: boolean;
  exportingRecordId: number | null;
  exportFeedback: ExportFeedback;
  onRetry: () => void;
  onRequestDelete: (target: DeleteTarget) => void;
  onExport: (recordId: number, detail?: ResumeHistoryDetail) => void;
}) {
  if (!selectedQuery || detailState === "idle") {
    return <EmptyDetailPlaceholder />;
  }

  if (detailState === "loading") {
    return <DetailSkeleton />;
  }

  if (detailState === "error") {
    return <DetailErrorPlaceholder message={detailError || "详情加载失败"} onRetry={onRetry} />;
  }

  if (selectedQuery.type === "resume" && resumeDetail) {
    return (
      <ResumeDetailPane
        detail={resumeDetail}
        exportingRecordId={exportingRecordId}
        exportFeedback={exportFeedback}
        deletePending={deletePending}
        onExport={onExport}
        onRequestDelete={onRequestDelete}
      />
    );
  }

  if (selectedQuery.type === "interview" && interviewDetail) {
    return (
      <InterviewDetailPane
        detail={interviewDetail}
        deletePending={deletePending}
        onRequestDelete={onRequestDelete}
      />
    );
  }

  return <DetailErrorPlaceholder message="详情未找到" onRetry={onRetry} />;
}

function ResumeDetailPane({
  detail,
  exportingRecordId,
  exportFeedback,
  deletePending,
  onExport,
  onRequestDelete,
}: {
  detail: ResumeHistoryDetail;
  exportingRecordId: number | null;
  exportFeedback: ExportFeedback;
  deletePending: boolean;
  onExport: (recordId: number, detail?: ResumeHistoryDetail) => void;
  onRequestDelete: (target: DeleteTarget) => void;
}) {
  return (
    <div className="flex h-full flex-col">
      <div className="sticky top-0 z-10 shrink-0 border-b border-slate-100 bg-white/90 px-8 py-5 backdrop-blur-md">
        <div className="flex items-start justify-between gap-4">
          <div>
            <div className="mb-2 flex items-center gap-3">
              <span className="rounded-md border border-amber-100 bg-amber-50 px-3 py-1.5 text-xs font-bold tracking-[0.16em] text-amber-600">
                简历复盘
              </span>
              <span className="flex items-center gap-1 text-sm font-medium text-slate-400">
                <Clock size={12} />
                {formatDateTimeLabel(detail.createdAt)}
              </span>
            </div>
            <h2 className="text-3xl font-black tracking-tight text-slate-900">{detail.targetRole}</h2>
            <div className="mt-1 text-sm leading-7 text-slate-500">
              上下文: {detail.targetContext || "未填写"} · 模式: {DICT.INPUT_MODE[detail.inputMode] || detail.inputMode} · 消耗: {detail.pointsConsumed} 积分
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => onExport(detail.recordId, detail)}
              disabled={exportingRecordId === detail.recordId}
              className="flex h-10 w-10 items-center justify-center rounded-full border border-slate-200 text-slate-500 transition-colors hover:border-indigo-200 hover:bg-indigo-50 hover:text-indigo-600 disabled:cursor-not-allowed disabled:opacity-60"
              title="导出报告"
            >
              {exportingRecordId === detail.recordId ? <RefreshCw size={16} className="animate-spin" /> : <Download size={16} />}
            </button>
            <button
              type="button"
              onClick={() => onRequestDelete({ type: "resume", recordId: detail.recordId })}
              disabled={deletePending}
              className="flex h-10 w-10 items-center justify-center rounded-full border border-slate-200 text-slate-500 transition-colors hover:border-rose-200 hover:bg-rose-50 hover:text-rose-600 disabled:cursor-not-allowed disabled:opacity-60"
              title="删除记录"
            >
              <Trash2 size={16} />
            </button>
            <Link
              to={`/ai/resume/review${buildQuery({ recordId: detail.recordId })}`}
              className="ml-2 inline-flex items-center gap-2 rounded-full border border-sky-200 bg-gradient-to-r from-sky-50 via-white to-emerald-50 px-5 py-2.5 text-base font-semibold text-sky-700 shadow-sm transition-all hover:-translate-y-0.5 hover:border-cyan-300 hover:from-sky-100 hover:to-emerald-100 hover:text-sky-800"
            >
              查看详情
              <ArrowRight size={14} />
            </Link>
          </div>
        </div>

        {exportFeedback?.recordId === detail.recordId ? (
          <div className={joinClasses(
            "mt-3 inline-flex items-center rounded-full px-3 py-1.5 text-xs font-semibold",
            exportFeedback.tone === "success"
              ? "bg-emerald-50 text-emerald-700"
              : exportFeedback.tone === "info"
                ? "bg-sky-50 text-sky-700"
                : "bg-rose-50 text-rose-700",
          )}
          >
            {exportFeedback.message}
          </div>
        ) : null}
      </div>

      <div className="custom-scrollbar flex-1 overflow-y-auto px-8 py-8">
        <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="space-y-8">
          <div className="flex gap-6 rounded-[1.5rem] border border-indigo-100 bg-indigo-50/50 p-6">
            <div className="flex h-24 w-24 shrink-0 flex-col items-center justify-center rounded-[1.2rem] border border-indigo-100 bg-white shadow-sm">
              <span className="mb-1 text-sm font-bold text-slate-400">RATING</span>
              <span className="text-4xl font-black text-indigo-600">{detail.scoreLabel}</span>
            </div>
            <div>
              <h3 className="mb-2 flex items-center gap-2 text-base font-bold text-slate-800">
                <Sparkles size={16} className="text-indigo-500" />
                复盘摘要
              </h3>
              <p className="text-base leading-8 text-slate-600">{detail.summary}</p>
            </div>
          </div>

          <section>
            <SectionTitle title="优化建议" icon={Lightbulb} />
            <div className="mt-4">
              <AnalysisCard title="建议清单" items={detail.suggestions} icon={Lightbulb} colorClass="text-amber-600" bgClass="border-amber-100 bg-amber-50" />
            </div>
          </section>

          <section>
            <SectionTitle title="原始输入上下文" icon={Search} />

            <div className="mb-4 mt-4 grid grid-cols-1 gap-4 sm:grid-cols-3">
              <InfoBlock label="目标岗位" value={detail.targetRole || "未填写"} />
              <InfoBlock label="上下文语境" value={detail.targetContext || "未填写"} title={detail.targetContext || "未填写"} />
              <InfoBlock label="输入模式" value={DICT.INPUT_MODE[detail.inputMode] || detail.inputMode} />
            </div>

            <div className="overflow-hidden rounded-2xl border border-slate-200 bg-slate-50 text-xs leading-relaxed text-slate-600">
              <div className="border-b border-slate-200 bg-white/50 p-5">
                <div className="mb-2 flex items-center gap-2 text-base font-bold text-slate-800">
                  <Target size={14} className="text-indigo-500" />
                  岗位要求
                </div>
                <div className="whitespace-pre-wrap text-sm leading-7">{detail.jobDescription || "本次未提供岗位要求。"}</div>
              </div>
              <div className="custom-scrollbar max-h-64 overflow-y-auto bg-slate-50 p-5">
                <div className="mb-2 flex items-center gap-2 text-base font-bold text-slate-800">
                  <FileSignature size={14} className="text-indigo-500" />
                  简历原文
                </div>
                {detail.inputMode === "pdf" ? (
                  <div className="inline-flex items-center gap-2 rounded-md border border-indigo-100 bg-indigo-50 px-3 py-1.5 text-sm font-semibold text-indigo-700">
                    [已上传 PDF 解析] 文件名: {detail.pdfFileName || "未返回文件名"}
                  </div>
                ) : (
                  <div className="whitespace-pre-wrap text-sm leading-7">{detail.resumeText || "本次未返回简历原文。"}</div>
                )}
              </div>
            </div>
          </section>
        </motion.div>
      </div>
    </div>
  );
}

function InterviewDetailPane({
  detail,
  deletePending,
  onRequestDelete,
}: {
  detail: InterviewSessionDetail;
  deletePending: boolean;
  onRequestDelete: (target: DeleteTarget) => void;
}) {
  const summary = detail.summary;
  const noSummary = !summary;
  const canContinueInterview = isActiveInterviewStatus(detail.status) && Boolean(detail.sessionId?.trim());
  const sessionContext = detail.sessionContext;
  const resumeContext = detail.resumeContext;
  const prepMaterialSummary = formatMappedLabelList(sessionContext?.prepMaterialKeys, DICT.PREP_MATERIAL);
  const answerHelperCueSummary = formatMappedLabelList(sessionContext?.answerHelperCueKeys, DICT.ANSWER_HELPER_CUE, "未启用");

  return (
    <div className="flex h-full flex-col">
      <div className="sticky top-0 z-10 shrink-0 border-b border-slate-100 bg-white/90 px-8 py-5 backdrop-blur-md">
        <div className="flex items-start justify-between gap-4">
          <div>
            <div className="mb-2 flex items-center gap-3">
              <span className="rounded-md border border-teal-100 bg-teal-50 px-3 py-1.5 text-xs font-bold tracking-[0.16em] text-teal-600">
                模拟面试复盘
              </span>
              <span className="flex items-center gap-1 text-sm font-medium text-slate-400">
                <Clock size={12} />
                {formatDateTimeLabel(detail.createdAt)}
              </span>
            </div>
            <h2 className="text-3xl font-black tracking-tight text-slate-900">{detail.targetRole}</h2>
            <div className="mt-1 text-sm leading-7 text-slate-500">
              模式: {DICT.INTERVIEW_MODE[detail.mode] || detail.mode} · 状态: {DICT.STATUS[detail.status] || detail.status}
            </div>
          </div>

          <div className="flex items-center gap-2">
            {canContinueInterview ? (
              <Link
                to={`/ai/interview/session${buildQuery({ sessionId: detail.sessionId })}`}
                className="inline-flex items-center gap-2 rounded-full border border-teal-200 bg-gradient-to-r from-teal-50 via-white to-cyan-50 px-5 py-2.5 text-base font-semibold text-teal-700 shadow-sm transition-all hover:-translate-y-0.5 hover:border-teal-300 hover:from-teal-100 hover:to-cyan-100 hover:text-teal-800"
              >
                返回进行中会话
                <ArrowRight size={14} />
              </Link>
            ) : null}
            <button
              type="button"
              onClick={() => onRequestDelete({ type: "interview", sessionId: detail.sessionId })}
              disabled={deletePending}
              className="flex h-10 w-10 items-center justify-center rounded-full border border-slate-200 text-slate-500 transition-colors hover:border-rose-200 hover:bg-rose-50 hover:text-rose-600 disabled:cursor-not-allowed disabled:opacity-60"
              title="删除记录"
            >
              <Trash2 size={16} />
            </button>
            <Link
              to="/ai/interview"
              className="ml-2 inline-flex items-center gap-2 rounded-full border border-sky-200 bg-gradient-to-r from-sky-50 via-white to-emerald-50 px-5 py-2.5 text-base font-semibold text-sky-700 shadow-sm transition-all hover:-translate-y-0.5 hover:border-cyan-300 hover:from-sky-100 hover:to-emerald-100 hover:text-sky-800"
            >
              {canContinueInterview ? "新开一轮" : "再次练习"}
              <ArrowRight size={14} />
            </Link>
          </div>
        </div>
      </div>

      <div className="custom-scrollbar flex-1 overflow-y-auto px-8 py-8">
        <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="space-y-8">
          <div className="flex flex-col items-center gap-6 rounded-[1.5rem] border border-indigo-100 bg-gradient-to-br from-indigo-50 to-white p-6 md:flex-row">
            <div className="relative flex h-28 w-28 shrink-0 flex-col items-center justify-center rounded-full border-[6px] border-indigo-50 bg-white shadow-sm">
              <span className="absolute top-4 text-[11px] font-bold text-slate-400">SCORE</span>
              {noSummary ? (
                <span className="mt-2 text-xl font-bold text-slate-300">—</span>
              ) : (
                <span className="mt-2 text-4xl font-black text-indigo-600">{summary.overallScore}</span>
              )}
            </div>

            <div className="w-full flex-1 text-center md:text-left">
              <div className="mb-3 flex flex-col justify-between gap-2 sm:flex-row sm:items-center">
                <h3 className="text-xl font-bold text-slate-800">会话概览</h3>
                <div className="rounded-full border bg-white px-3 py-1.5 text-sm text-slate-500 shadow-sm">
                  目标: {detail.targetRole}
                </div>
              </div>

              {noSummary ? (
                <div className="inline-flex w-full items-center gap-2 rounded-xl border border-amber-100 bg-amber-50 px-3 py-2.5 text-base text-amber-600 md:w-auto">
                  <ShieldAlert size={16} />
                  本次会话尚未生成总结报告，但你仍可回顾问答记录。
                </div>
              ) : (
                <div className="inline-block w-full rounded-xl border border-slate-100 bg-white/60 p-3 text-base text-slate-600 md:w-auto">
                  <Clock size={14} className="mb-0.5 mr-1 inline text-slate-400" />
                  总结生成于:
                  <span className="ml-1 font-medium text-slate-700">{formatDateTimeLabel(detail.summaryGeneratedAt)}</span>
                </div>
              )}

              <div className="mt-4 grid grid-cols-2 gap-3 text-left sm:grid-cols-4">
                <InfoMetric label="配额消耗" value={`${detail.prepaidPoints} 积分（权 ${detail.reservedQuotaWeight}）`} />
                <InfoMetric label="对答回合" value={`${detail.replyRoundUsed} / ${detail.replyRoundLimit}`} />
                <InfoMetric label="结束触发者" value={detail.endedByAi ? "AI 主动结束" : "用户主动结束"} />
                <InfoMetric label="结束原因" value={DICT.FINISH_REASON[detail.finishReason] || detail.finishReason} />
              </div>
            </div>
          </div>

          {summary ? (
            <div className="grid gap-4 md:grid-cols-2">
              <AnalysisCard title="表现优势" items={summary.strengths} icon={ThumbsUp} colorClass="text-emerald-600" bgClass="border-emerald-100 bg-emerald-50" />
              <AnalysisCard title="亟待改进" items={summary.weaknesses} icon={AlertTriangle} colorClass="text-rose-600" bgClass="border-rose-100 bg-rose-50" />
              <div className="md:col-span-2">
                <AnalysisCard title="下一步建议" items={summary.suggestions} icon={Lightbulb} colorClass="text-indigo-600" bgClass="border-indigo-100 bg-indigo-50" />
              </div>
            </div>
          ) : null}

          <section>
            <SectionTitle title="消息时间线" icon={MessageSquare} />
            <div className="mt-4 space-y-6 rounded-[1.5rem] border border-slate-200 bg-slate-50/50 p-6">
              {detail.messages.length === 0 ? (
                <div className="rounded-2xl border border-dashed border-slate-200 bg-white/70 px-5 py-8 text-center text-base text-slate-500">
                  当前还没有可展示的会话消息。
                </div>
              ) : detail.messages.map((message, index) => {
                const isUser = message.role === "USER";
                const isSystem = message.role === "SYSTEM";
                const roleLabel = message.role === "ASSISTANT"
                  ? "AI 面试官"
                  : message.role === "USER"
                    ? "我"
                    : message.role === "SYSTEM"
                      ? "系统提示"
                      : message.role;

                if (isSystem) {
                  return (
                    <div key={`system-${index}-${message.createdAt}`} className="w-full text-center">
                      <span className="inline-block rounded-full border border-slate-200/50 bg-slate-200/60 px-3 py-1.5 text-xs font-medium text-slate-500 shadow-sm">
                        {formatTimeLabel(message.createdAt)} · {message.text}
                      </span>
                    </div>
                  );
                }

                return (
                  <div key={`msg-${index}-${message.createdAt}`} className={joinClasses("flex flex-col", isUser ? "items-end" : "items-start")}>
                    <div className={joinClasses("flex max-w-[85%] gap-3 sm:max-w-[75%]", isUser ? "flex-row-reverse" : "flex-row")}>
                      <div className="mt-1 shrink-0">
                        {isUser ? (
                          <div className="flex h-8 w-8 items-center justify-center rounded-full bg-slate-800 text-white shadow-sm">
                            <User size={14} />
                          </div>
                        ) : (
                          <div className="flex h-8 w-8 items-center justify-center rounded-full border border-indigo-200 bg-indigo-100 text-indigo-600 shadow-sm">
                            <Bot size={16} />
                          </div>
                        )}
                      </div>

                      <div className="flex flex-col">
                        <div className={joinClasses(
                          "mb-1 flex items-center gap-2 text-xs font-medium text-slate-400",
                          isUser && "flex-row-reverse",
                        )}
                        >
                          {roleLabel}
                          <span>{formatTimeLabel(message.createdAt)}</span>
                        </div>
                        <div className={joinClasses(
                          "relative rounded-[1.2rem] p-4 text-[15px] leading-8 shadow-sm",
                          isUser
                            ? "rounded-tr-sm bg-slate-800 text-white"
                            : "rounded-tl-sm border border-slate-100 bg-white text-slate-700",
                        )}
                        >
                          {message.text}
                          {message.scoreHint !== null ? (
                            <div className={joinClasses(
                              "absolute -bottom-2.5 rounded border border-white px-2.5 py-0.5 text-[11px] font-bold shadow-sm",
                              isUser
                                ? "-left-2 bg-emerald-100 text-emerald-700"
                                : "-right-2 bg-indigo-100 text-indigo-700",
                            )}
                            >
                              单轮打分: {message.scoreHint}
                            </div>
                          ) : null}
                        </div>
                        {message.coachFeedback?.trim() ? (
                          <div className="mt-3 rounded-[1rem] border border-indigo-100 bg-indigo-50/85 px-4 py-3.5 text-sm leading-7 text-slate-700 shadow-sm">
                            <div className="flex items-center gap-2 text-xs font-semibold text-indigo-700">
                              <Sparkles size={13} />
                              AI 即时点评
                            </div>
                            <div className="mt-2">{message.coachFeedback}</div>
                          </div>
                        ) : null}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </section>

          <section>
            <SectionTitle title="本轮输入配置" icon={Search} />

            <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <InfoBlock label="作答方式" value={getMappedLabel(sessionContext?.answerMode, DICT.ANSWER_MODE)} />
              <InfoBlock label="面试类型" value={getMappedLabel(sessionContext?.interviewType, DICT.INTERVIEW_TYPE)} />
              <InfoBlock label="面试官风格" value={getMappedLabel(sessionContext?.interviewerStyle, DICT.INTERVIEWER_STYLE)} />
              <InfoBlock label="练习强度" value={getMappedLabel(sessionContext?.difficulty, DICT.DIFFICULTY)} />
              <InfoBlock label="目标企业" value={sessionContext?.targetCompany?.trim() || "未填写"} title={sessionContext?.targetCompany?.trim() || "未填写"} />
              <InfoBlock label="回答辅助器" value={sessionContext?.answerHelperEnabled ? "已开启" : "未开启"} />
              <InfoBlock label="带入资料" value={prepMaterialSummary} title={prepMaterialSummary} />
              <InfoBlock label="辅助提醒" value={answerHelperCueSummary} title={answerHelperCueSummary} />
            </div>

            <div className="mt-4 overflow-hidden rounded-2xl border border-slate-200 bg-slate-50 text-sm leading-7 text-slate-600">
              <div className="border-b border-slate-200 bg-white/60 p-5">
                <div className="mb-2 flex items-center gap-2 text-base font-bold text-slate-800">
                  <Target size={15} className="text-indigo-500" />
                  岗位要求
                </div>
                <div className="whitespace-pre-wrap">
                  {sessionContext?.targetJobDescription?.trim() || resumeContext?.jobDescription?.trim() || "本轮未额外填写岗位要求。"}
                </div>
              </div>

              <div className="grid gap-0 border-b border-slate-200 bg-slate-50 md:grid-cols-3">
                <div className="border-b border-slate-200 p-5 md:border-b-0 md:border-r">
                  <div className="mb-2 text-base font-bold text-slate-800">简历快照</div>
                  <div className="space-y-1.5 text-sm leading-7">
                    <div>目标岗位：{resumeContext?.targetRole?.trim() || "未带入"}</div>
                    <div>求职语境：{resumeContext?.targetContext?.trim() || "未填写"}</div>
                    <div>输入方式：{getMappedLabel(resumeContext?.inputMode, DICT.INPUT_MODE, "未带入")}</div>
                  </div>
                </div>
                <div className="border-b border-slate-200 p-5 md:border-b-0 md:border-r">
                  <div className="mb-2 text-base font-bold text-slate-800">带入资料摘要</div>
                  <div className="text-sm leading-7">
                    {sessionContext?.promptContext?.trim() || "本轮没有额外的资料摘要。"}
                  </div>
                </div>
                <div className="p-5">
                  <div className="mb-2 text-base font-bold text-slate-800">简历摘要参考</div>
                  <div className="text-sm leading-7">
                    {resumeContext?.summary?.trim() || resumeContext?.resumeTextExcerpt?.trim() || "本轮未带入简历摘要。"}
                  </div>
                </div>
              </div>
            </div>
          </section>

          <CrossModuleCta type="interview" />
        </motion.div>
      </div>
    </div>
  );
}

function EmptyListPlaceholder() {
  return (
    <div className="flex h-full flex-col items-center justify-center p-6 text-center">
      <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-slate-100 text-slate-400">
        <Search size={24} />
      </div>
      <h3 className="mb-2 text-sm font-semibold text-slate-900">当前还没有可复盘记录</h3>
      <p className="mb-6 text-xs text-slate-500">去做一次真实的 AI 练习，给画像喂入新信号吧。</p>
      <div className="flex w-full flex-col gap-2">
        <Link
          to="/ai/resume"
          className="flex items-center justify-center gap-2 rounded-xl bg-indigo-600 py-2.5 text-xs font-semibold text-white transition-colors hover:bg-indigo-700"
        >
          去优化简历
          <ArrowRight size={14} />
        </Link>
        <Link
          to="/ai/interview"
          className="flex items-center justify-center gap-2 rounded-xl border border-slate-200 bg-white py-2.5 text-xs font-semibold text-slate-700 transition-colors hover:bg-slate-50"
        >
          去全真模拟面试
          <ArrowRight size={14} />
        </Link>
      </div>
    </div>
  );
}

function EmptyDetailPlaceholder() {
  return (
    <div className="absolute inset-0 flex flex-col items-center justify-center bg-slate-50/50 p-10 text-center">
      <div className="mb-6 flex h-24 w-24 items-center justify-center rounded-3xl border border-slate-100 bg-white text-slate-300 shadow-sm">
        <Layers size={40} />
      </div>
      <h2 className="mb-2 text-xl font-bold text-slate-800">请选择一条复盘记录</h2>
      <p className="max-w-sm text-sm text-slate-500">在左侧列表中点击任意一项，即可在此处查看完整的复盘报告。</p>
    </div>
  );
}

function AsyncTaskStatusPlaceholder({
  taskId,
  loading,
  error,
  detail,
}: {
  taskId: string;
  loading: boolean;
  error: string | null;
  detail: AiAsyncTaskDetail | null;
}) {
  if (loading) {
    return (
      <div className="absolute inset-0 flex flex-col items-center justify-center bg-slate-50/50 p-10 text-center">
        <RefreshCw size={32} className="mb-4 animate-spin text-indigo-400" />
        <h2 className="mb-2 text-xl font-bold text-slate-800">正在同步这次 AI 简历任务</h2>
        <p className="max-w-md text-sm leading-7 text-slate-500">会优先尝试定位到对应复盘记录；如果任务仍在处理中，这里会先展示当前状态。</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="absolute inset-0 flex flex-col items-center justify-center bg-slate-50/50 p-10 text-center">
        <AlertCircle size={40} className="mb-4 text-rose-300" />
        <h2 className="mb-2 text-lg font-bold text-slate-800">{error}</h2>
        <p className="max-w-md text-sm leading-7 text-slate-500">任务编号：{taskId}。你也可以先回到简历优化页重新提交，或稍后再来复盘中心查看。</p>
      </div>
    );
  }

  return (
    <div className="absolute inset-0 flex items-center justify-center bg-slate-50/50 p-8">
      <div className="w-full max-w-2xl rounded-[2rem] border border-white/80 bg-white/95 p-8 shadow-[0_18px_50px_rgba(148,163,184,0.12)]">
        <div className="inline-flex items-center gap-2 rounded-full border border-indigo-100 bg-indigo-50 px-4 py-2 text-xs font-semibold text-indigo-700">
          <Sparkles size={14} />
          Async Resume Task
        </div>
        <h2 className="mt-5 text-2xl font-black tracking-tight text-slate-900">这次 AI 简历任务正在等待复盘中心接住结果</h2>
        <p className="mt-3 text-sm leading-7 text-slate-600">
          {detail?.status === "FAILED"
            ? "任务已经终止，本次没有写入新的复盘记录。你可以先查看失败原因，再回到简历优化页重新发起。"
            : "任务可能仍在处理中，或结果正在同步到历史记录。复盘中心已经接住了这次回流请求，你可以稍后刷新查看。"}
        </p>

        <div className="mt-6 grid gap-4 sm:grid-cols-2">
          <div className="rounded-[1.5rem] border border-slate-100 bg-slate-50/80 p-4">
            <div className="text-[11px] text-slate-400">任务编号</div>
            <div className="mt-2 break-all text-sm font-semibold text-slate-800">{taskId}</div>
          </div>
          <div className="rounded-[1.5rem] border border-slate-100 bg-slate-50/80 p-4">
            <div className="text-[11px] text-slate-400">当前状态</div>
            <div className="mt-2 text-sm font-semibold text-slate-800">{detail?.status || "等待同步"}</div>
          </div>
        </div>

        {detail?.resultSummary || detail?.errorMessage ? (
          <div className="mt-4 rounded-[1.5rem] border border-slate-100 bg-slate-50/80 p-4">
            <div className="text-[11px] text-slate-400">
              {detail.errorMessage ? "状态说明" : "结果摘要"}
            </div>
            <div className="mt-2 text-sm leading-7 text-slate-600">{detail.errorMessage || detail.resultSummary}</div>
          </div>
        ) : null}

        <div className="mt-6 flex flex-wrap gap-3">
          <Link
            to="/ai/resume"
            className="inline-flex items-center rounded-full bg-indigo-600 px-5 py-3 text-sm font-semibold text-white transition-transform hover:-translate-y-0.5 hover:bg-indigo-700"
          >
            返回简历优化
            <ArrowRight size={16} className="ml-2" />
          </Link>
          <Link
            to="/ai/history?type=resume"
            className="inline-flex items-center rounded-full border border-slate-200 bg-white px-5 py-3 text-sm font-semibold text-slate-700 transition-colors hover:border-indigo-200 hover:text-indigo-600"
          >
            回到复盘中心列表
          </Link>
        </div>
      </div>
    </div>
  );
}

function DetailSkeleton() {
  return (
    <div className="flex h-full flex-col">
      <div className="border-b border-slate-100 bg-white/90 px-8 py-5">
        <div className="animate-pulse space-y-4">
          <div className="h-4 w-40 rounded bg-slate-200" />
          <div className="h-8 w-72 rounded bg-slate-100" />
          <div className="h-4 w-64 rounded bg-slate-100" />
        </div>
      </div>
      <div className="space-y-6 p-8">
        {[1, 2, 3].map((item) => (
          <div key={item} className="animate-pulse rounded-[1.5rem] border border-slate-100 bg-slate-50/80 p-6">
            <div className="h-5 w-36 rounded bg-slate-200" />
            <div className="mt-4 h-4 w-full rounded bg-slate-100" />
            <div className="mt-2 h-4 w-5/6 rounded bg-slate-100" />
          </div>
        ))}
      </div>
    </div>
  );
}

function DetailErrorPlaceholder({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="absolute inset-0 flex flex-col items-center justify-center bg-slate-50/50 p-10 text-center">
      <AlertCircle size={40} className="mb-4 text-rose-300" />
      <h2 className="mb-2 text-lg font-bold text-slate-800">{message}</h2>
      <p className="text-sm text-slate-500">无法读取所选记录的具体详情。</p>
      <button type="button" onClick={onRetry} className="mt-4 text-sm font-semibold text-indigo-600 hover:underline">
        重新加载详情
      </button>
    </div>
  );
}

function SectionTitle({ title, icon: Icon }: { title: string; icon: LucideIcon }) {
  return (
    <div className="mb-4 flex items-center gap-2 border-b border-slate-100 pb-2">
      <Icon size={20} className="text-slate-400" />
      <h3 className="text-xl font-bold text-slate-800">{title}</h3>
    </div>
  );
}

function AnalysisCard({
  title,
  items,
  icon: Icon,
  colorClass,
  bgClass,
}: {
  title: string;
  items: string[];
  icon: LucideIcon;
  colorClass: string;
  bgClass: string;
}) {
  if (!items || items.length === 0) {
    return null;
  }

  return (
    <div className={joinClasses("rounded-2xl border p-5", bgClass)}>
      <div className={joinClasses("mb-3 flex items-center gap-2 text-base font-bold", colorClass)}>
        <Icon size={16} />
        {title}
      </div>
      <ul className="space-y-2">
        {items.map((item, index) => (
          <li key={`${title}-${index}`} className="flex items-start gap-2 text-base leading-8 text-slate-700">
            <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-current opacity-40" />
            {item}
          </li>
        ))}
      </ul>
    </div>
  );
}

function SlantedRouteButton({
  to,
  label,
  tone,
}: {
  to: string;
  label: string;
  tone: "primary" | "mentor";
}) {
  const toneClass = tone === "primary"
    ? "border-cyan-200/80 bg-gradient-to-r from-[#74dff7] via-[#57b8ff] to-[#61e6c6] shadow-[0_16px_34px_rgba(56,189,248,0.22)] hover:shadow-[0_20px_42px_rgba(45,212,191,0.26)]"
    : "border-amber-200/80 bg-gradient-to-r from-[#ffd56f] via-[#ffb777] to-[#ff9cb6] shadow-[0_16px_34px_rgba(251,191,36,0.2)] hover:shadow-[0_20px_42px_rgba(251,146,60,0.24)]";
  const textClass = tone === "primary"
    ? "text-sky-950 [text-shadow:0_1px_0_rgba(255,255,255,0.18)]"
    : "text-rose-950 [text-shadow:0_1px_0_rgba(255,255,255,0.16)]";

  return (
    <Link
      to={to}
      className={joinClasses(
        "group relative isolate inline-flex shrink-0 overflow-hidden border transition-all duration-300 hover:-translate-y-0.5",
        "min-w-[15.5rem]",
        toneClass,
      )}
      style={{
        clipPath: "polygon(10% 0, 100% 0, 90% 100%, 0 100%)",
      }}
    >
      <span className="absolute inset-0 bg-[linear-gradient(135deg,rgba(255,255,255,0.20),transparent_44%,rgba(255,255,255,0.08))]" />
      <span className="absolute inset-0 bg-[linear-gradient(180deg,rgba(15,23,42,0.08),transparent_42%,rgba(255,255,255,0.06))]" />
      <span className="absolute inset-y-0 left-[-5rem] w-16 rotate-[14deg] bg-white/32 opacity-0 transition-all duration-700 group-hover:left-[calc(100%+2rem)] group-hover:opacity-100" />
      <span className={joinClasses(
        "relative z-10 inline-flex w-full items-center justify-center gap-3 px-8 py-3.5 text-[15px] font-semibold tracking-[0.01em] lg:text-base",
        textClass,
      )}
      >
        <span className={textClass}>{label}</span>
        <ArrowRight size={16} className={joinClasses("shrink-0", textClass)} />
      </span>
    </Link>
  );
}

function CrossModuleCta({ type }: { type: "resume" | "interview" }) {
  const primaryTo = type === "resume" ? "/ai/resume" : "/ai/interview";
  const primaryLabel = type === "resume" ? "继续简历优化" : "开启新一轮模拟面试";

  return (
    <div className="mt-12 space-y-4">
      <div className="flex items-center justify-between gap-4 rounded-[1.5rem] border border-slate-200 bg-white p-5 shadow-sm">
        <div>
          <h4 className="text-lg font-bold text-slate-800">觉得准备还不够？</h4>
          <p className="mt-1 text-sm leading-7 text-slate-500">
            你可以随时开启一次新的{type === "resume" ? "简历优化" : "模拟面试"}，获取更成熟的方案。
          </p>
        </div>
        <SlantedRouteButton to={primaryTo} label={primaryLabel} tone="primary" />
      </div>

      <div className="flex items-center justify-between gap-4 rounded-[1.5rem] border border-amber-200 bg-amber-50/50 p-5 shadow-sm">
        <div>
          <h4 className="flex items-center gap-2 text-lg font-bold text-slate-800">
            <GraduationCap size={16} className="text-amber-500" />
            想要更深度的人工指导？
          </h4>
          <p className="mt-1 text-sm leading-7 text-slate-600">携带这份 AI 复盘报告，去找大厂导师进行 1V1 咨询，直击盲区。</p>
        </div>
        <SlantedRouteButton to="/mentors" label="去找求职导师" tone="mentor" />
      </div>
    </div>
  );
}

function InfoBlock({ label, value, title }: { label: string; value: string; title?: string }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-3.5 shadow-sm">
      <div className="mb-1 text-[11px] font-bold text-slate-400">{label}</div>
      <div className="truncate text-base font-semibold text-slate-800" title={title || value}>{value}</div>
    </div>
  );
}

function InfoMetric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-slate-100 bg-slate-50 p-3">
      <div className="text-[11px] text-slate-400">{label}</div>
      <div className="mt-1 text-base font-semibold text-slate-800">{value}</div>
    </div>
  );
}
