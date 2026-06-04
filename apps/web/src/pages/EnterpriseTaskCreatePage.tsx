import { AnimatePresence, motion } from "framer-motion";
import {
  AlertCircle,
  ArrowLeft,
  Briefcase,
  CalendarDays,
  Check,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Code,
  Eye,
  FileText,
  Gift,
  Link as LinkIcon,
  Palette,
  PenTool,
  Send,
  Target,
} from "lucide-react";
import { startTransition, useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import EnterpriseWorkspaceShell from "../components/enterprise/EnterpriseWorkspaceShell";
import WorkspacePageLoadingScreen from "../components/WorkspacePageLoadingScreen";
import { ApiClientError, apiRequest, isAbortError } from "../lib/apiClient";
import { resolveEnterpriseAccountName } from "../lib/enterpriseIdentity";
import { buildEnterpriseLogoUrl } from "../lib/enterpriseLogo";
import {
  AUDIENCE_TAGS,
  buildExternalResourceHref,
  buildEnterpriseTaskDescription,
  buildEnterpriseTaskValidationMessages,
  buildTaskPreviewChips,
  createEmptyEnterpriseTaskForm,
  createEnterpriseTaskFormFromTask,
  DELIVERY_FORMAT_OPTIONS,
  DIRECTION_TAGS,
  normalizeTaskDateToDeadlineInstant,
  parseEnterpriseTaskDescription,
  type EnterpriseTaskCreateFormState,
} from "../lib/enterpriseTasks";
import { formatDateInputValue } from "../lib/formatters";
import { getEnterpriseWorkspaceNavItems } from "../lib/workspaceNav";
import {
  buildWorkspaceSnapshotStorageKey,
  readWorkspaceSnapshot,
  writeWorkspaceSnapshot,
} from "../lib/workspaceSnapshot";

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

type BountyTaskCreateResponse = {
  taskId: number;
  status: string;
  createdAt: string;
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

type BountyTaskUpdateResponse = {
  taskId: number;
  status: string;
  updatedAt: string;
};

type ToastState = {
  tone: "success" | "error" | "info";
  message: string;
};

type EnterpriseTaskCreateSnapshot = {
  profile: EnterpriseOwnProfileResponse | null;
  editingTask: BountyTaskDetailResponse | null;
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

const DELIVERY_FORMAT_ICONS = {
  说明文档: FileText,
  设计稿链接: Palette,
  代码仓库链接: Code,
  "演示视频/链接": LinkIcon,
} as const satisfies Record<string, (typeof FileText)>;

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

function formatPreviewDate(dateValue: string) {
  if (!dateValue) {
    return "尚未设置截止日期";
  }
  const [year, month, day] = dateValue.split("-");
  if (year && month && day) {
    return `${year}年${month}月${day}日`;
  }
  return dateValue;
}

function getEnterprisePublishGateMeta(approvalStatus: string | null | undefined) {
  if (approvalStatus === "APPROVED") {
    return null;
  }
  if (approvalStatus === "REJECTED") {
    return {
      toneClassName: "border-rose-200 bg-rose-50/90 text-rose-900 shadow-[0_10px_28px_rgba(244,63,94,0.12)]",
      iconClassName: "text-rose-600",
      title: "企业认证待补充，暂不能发布新任务",
      description: "请先在资料页补充认证材料，审核通过后即可继续发布或重新开放任务。",
    };
  }
  return {
    toneClassName: "border-amber-200 bg-amber-50/90 text-amber-900 shadow-[0_10px_28px_rgba(245,158,11,0.12)]",
    iconClassName: "text-amber-600",
    title: "企业认证审核中，暂不能发布新任务",
    description: "你可以先完善任务内容，认证通过后再正式发布。",
  };
}

function SectionCard({
  icon,
  title,
  desc,
  accentClassName,
  children,
}: {
  icon: ReactNode;
  title: string;
  desc: string;
  accentClassName: string;
  children: ReactNode;
}) {
  return (
    <motion.section variants={itemVariants} className="rounded-[2rem] border border-slate-200/60 bg-white/95 p-6 shadow-[0_12px_40px_rgba(148,163,184,0.1)] backdrop-blur-xl sm:p-8">
      <div className="mb-6 flex items-center gap-3 border-b border-slate-100 pb-5">
        <div className={joinClasses("flex h-10 w-10 items-center justify-center rounded-xl", accentClassName)}>
          {icon}
        </div>
        <div>
          <h2 className="text-[24px] font-bold tracking-tight text-slate-900">{title}</h2>
          <p className="mt-1.5 text-[16px] leading-7 text-slate-500">{desc}</p>
        </div>
      </div>
      {children}
    </motion.section>
  );
}

export default function EnterpriseTaskCreatePage() {
  const { role, userId } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const [profile, setProfile] = useState<EnterpriseOwnProfileResponse | null>(null);
  const [profileLoading, setProfileLoading] = useState(true);
  const [profileError, setProfileError] = useState<string | null>(null);
  const [editingTask, setEditingTask] = useState<BountyTaskDetailResponse | null>(null);
  const [taskLoading, setTaskLoading] = useState(false);
  const [taskError, setTaskError] = useState<string | null>(null);
  const [lastUpdatedAt, setLastUpdatedAt] = useState<string | null>(null);
  const [formData, setFormData] = useState<EnterpriseTaskCreateFormState>(() => createEmptyEnterpriseTaskForm());
  const [isCalendarOpen, setIsCalendarOpen] = useState(false);
  const [dropdownPos, setDropdownPos] = useState<"up" | "down">("down");
  const [viewDate, setViewDate] = useState(() => new Date());
  const [publishing, setPublishing] = useState(false);
  const [toast, setToast] = useState<ToastState | null>(null);

  const calendarRef = useRef<HTMLDivElement | null>(null);
  const editTaskId = Number(searchParams.get("editTaskId") ?? 0) || null;
  const isEditMode = editTaskId !== null;
  // 新建和编辑草稿隔离缓存，避免编辑旧任务时带入新建表单内容。
  const snapshotKey = buildWorkspaceSnapshotStorageKey("enterprise", "task-create", userId ?? "current", editTaskId ?? "new");

  const persistWorkspaceSnapshot = useCallback((
    snapshot: EnterpriseTaskCreateSnapshot,
    updatedAt = new Date().toISOString(),
  ) => {
    writeWorkspaceSnapshot(snapshotKey, snapshot, updatedAt);
    setLastUpdatedAt(updatedAt);
    return updatedAt;
  }, [snapshotKey]);

  const loadProfile = useCallback(async (options?: { signal?: AbortSignal; background?: boolean }) => {
    setProfileLoading(true);
    if (!options?.background) {
      setProfileError(null);
    }
    try {
      const response = await apiRequest<EnterpriseOwnProfileResponse>("/profiles/enterprises/me", { signal: options?.signal });
      setProfile(response);
      setLastUpdatedAt(new Date().toISOString());
    } catch (error) {
      if (options?.signal?.aborted || isAbortError(error)) {
        return;
      }
      if (options?.background) {
        setProfileError(null);
        return;
      }
      setProfileError(buildErrorMessage(error, "企业资料读取失败，请稍后重试。"));
    } finally {
      if (!options?.signal?.aborted) {
        setProfileLoading(false);
      }
    }
  }, []);

  const loadTask = useCallback(async (options?: { signal?: AbortSignal; background?: boolean }) => {
    if (!editTaskId) {
      setEditingTask(null);
      setTaskError(null);
      setTaskLoading(false);
      return;
    }

    setTaskLoading(true);
    setTaskError(null);
    try {
      // 编辑模式必须再校验 mine，不能只依赖前端路由传入的 editTaskId。
      const response = await apiRequest<BountyTaskDetailResponse>(`/bounty/tasks/${editTaskId}`, { signal: options?.signal });
      if (!response.mine) {
        throw new Error("仅可编辑本企业发布的任务。");
      }
      const nextForm = createEnterpriseTaskFormFromTask(response);
      setEditingTask(response);
      setFormData(nextForm);
      if (nextForm.deadline) {
        const [yearRaw, monthRaw] = nextForm.deadline.split("-");
        const year = Number(yearRaw);
        const month = Number(monthRaw);
        if (Number.isFinite(year) && Number.isFinite(month)) {
          setViewDate(new Date(year, month - 1, 1));
        }
      }
      setLastUpdatedAt(new Date().toISOString());
    } catch (error) {
      if (options?.signal?.aborted || isAbortError(error)) {
        return;
      }
      if (options?.background) {
        setTaskError(null);
        return;
      }
      setTaskError(buildErrorMessage(error, "任务详情读取失败，暂时无法进入编辑模式。"));
    } finally {
      if (!options?.signal?.aborted) {
        setTaskLoading(false);
      }
    }
  }, [editTaskId]);

  useEffect(() => {
    if (role !== "ENTERPRISE") {
      return undefined;
    }

    const controller = new AbortController();
    const snapshot = readWorkspaceSnapshot<EnterpriseTaskCreateSnapshot>(snapshotKey);

    // 快照只恢复工作台体验，profile 和任务详情仍会后台刷新校准。
    if (snapshot?.data) {
      setProfile(snapshot.data.profile);
      setEditingTask(snapshot.data.editingTask);
      setLastUpdatedAt(snapshot.updatedAt);
      setProfileLoading(false);
      setTaskLoading(false);
      if (isEditMode && snapshot.data.editingTask) {
        const nextForm = createEnterpriseTaskFormFromTask(snapshot.data.editingTask);
        setFormData(nextForm);
        if (nextForm.deadline) {
          const [yearRaw, monthRaw] = nextForm.deadline.split("-");
          const year = Number(yearRaw);
          const month = Number(monthRaw);
          if (Number.isFinite(year) && Number.isFinite(month)) {
            setViewDate(new Date(year, month - 1, 1));
          }
        }
      }
      void Promise.all([
        loadProfile({ signal: controller.signal, background: true }),
        loadTask({ signal: controller.signal, background: true }),
      ]);
    } else {
      void loadProfile({ signal: controller.signal });
      void loadTask({ signal: controller.signal });
    }

    return () => controller.abort();
  }, [isEditMode, loadProfile, loadTask, role, snapshotKey]);

  useEffect(() => {
    if (isEditMode) {
      return;
    }
    setEditingTask(null);
    setTaskError(null);
    setTaskLoading(false);
    setFormData(createEmptyEnterpriseTaskForm());
    setViewDate(new Date());
  }, [isEditMode]);

  useEffect(() => {
    if (!toast) {
      return undefined;
    }
    const timer = window.setTimeout(() => setToast(null), 3200);
    return () => window.clearTimeout(timer);
  }, [toast]);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (calendarRef.current && event.target instanceof Node && !calendarRef.current.contains(event.target)) {
        setIsCalendarOpen(false);
      }
    };

    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  useEffect(() => {
    if (!lastUpdatedAt) {
      return;
    }
    // profile / editingTask 变化后保留最新壳层和编辑对象，表单草稿由当前 state 控制。
    persistWorkspaceSnapshot({
      profile,
      editingTask,
    }, lastUpdatedAt);
  }, [editingTask, lastUpdatedAt, persistWorkspaceSnapshot, profile]);

  const taskDescription = useMemo(() => buildEnterpriseTaskDescription(formData), [formData]);
  const parsedPreview = useMemo(() => parseEnterpriseTaskDescription(taskDescription), [taskDescription]);
  const previewReferenceHref = buildExternalResourceHref(parsedPreview.referenceLink);
  const previewChips = useMemo(() => buildTaskPreviewChips(parsedPreview), [parsedPreview]);
  const descriptionTooLong = taskDescription.length > 4000;
  const publishGateMeta = getEnterprisePublishGateMeta(profile?.approvalStatus);
  // 发布新任务强依赖企业认证；编辑已有任务保留查看和局部保存入口。
  const createBlockedByApproval = !isEditMode && !!publishGateMeta;
  const validationMessages = useMemo(() => {
    const messages = buildEnterpriseTaskValidationMessages(formData);
    if (descriptionTooLong) {
      messages.push("整合后的任务说明超过接口允许的 4000 字，请适当精简背景或补充说明");
    }
    return messages;
  }, [descriptionTooLong, formData]);
  const isFormValid = validationMessages.length === 0;
  const editLocked = isEditMode && editingTask?.acceptedSubmissionId !== null;
  const editingUnavailable = isEditMode && (!editingTask || !!taskError);
  const submitDisabled = !isFormValid || publishing || editLocked || editingUnavailable || createBlockedByApproval;
  const submitHint = createBlockedByApproval
    ? (publishGateMeta?.description ?? "认证状态暂不支持发布新任务。")
    : editLocked
      ? "该任务已完成筛选，暂不支持继续修改任务信息。"
      : editingUnavailable
        ? (taskError ?? "任务详情暂未准备完成，请稍后再试。")
        : (validationMessages[0] ?? "请先完善左侧标星号的必填信息");

  const currentYear = viewDate.getFullYear();
  const currentMonthIndex = viewDate.getMonth();
  const daysInMonth = new Date(currentYear, currentMonthIndex + 1, 0).getDate();
  const firstDayIndex = new Date(currentYear, currentMonthIndex, 1).getDay();
  const days: Array<number | null> = [
    ...Array.from({ length: firstDayIndex }, (): number | null => null),
    ...Array.from({ length: daysInMonth }, (_, index): number | null => index + 1),
  ];

  const today = new Date();
  const datePresets = [
    { label: "1周后", value: formatDateInputValue(new Date(today.getFullYear(), today.getMonth(), today.getDate() + 7)) },
    { label: "2周后", value: formatDateInputValue(new Date(today.getFullYear(), today.getMonth(), today.getDate() + 14)) },
    { label: "1个月后", value: formatDateInputValue(new Date(today.getFullYear(), today.getMonth() + 1, today.getDate())) },
  ];

  const toggleArrayItem = (field: "deliveryFormats" | "directionTags" | "audienceTags", item: string) => {
    setFormData((current) => {
      const currentList = current[field];
      return {
        ...current,
        [field]: currentList.includes(item) ? currentList.filter((value) => value !== item) : [...currentList, item],
      };
    });
  };

  const handleTextChange = (field: keyof EnterpriseTaskCreateFormState, value: string) => {
    setFormData((current) => ({
      ...current,
      [field]: value,
    }));
  };

  const handleDateSelect = (day: number) => {
    const nextValue = `${currentYear}-${String(currentMonthIndex + 1).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
    handleTextChange("deadline", nextValue);
    setIsCalendarOpen(false);
  };

  const handlePublish = async () => {
    if (createBlockedByApproval) {
      setToast({ tone: "error", message: publishGateMeta?.title ?? "认证状态暂不支持发布新任务。" });
      return;
    }
    if (editLocked) {
      setToast({ tone: "error", message: "该任务已经产生中选结果，暂不允许修改任务信息。" });
      return;
    }
    if (editingUnavailable) {
      setToast({ tone: "error", message: taskError ?? "任务详情尚未读取完成，暂时无法保存修改。" });
      return;
    }
    if (!isFormValid) {
      setToast({ tone: "error", message: validationMessages[0] ?? "请先完善任务信息后再发布。" });
      return;
    }

    const deadlineAt = normalizeTaskDateToDeadlineInstant(formData.deadline);
    if (!deadlineAt) {
      setToast({ tone: "error", message: "截止时间格式异常，请重新选择日期。" });
      return;
    }

    setPublishing(true);
    try {
      // 后端当前仍接收四个基础字段，结构化表单压缩进 description 文本协议。
      const response = await apiRequest<BountyTaskCreateResponse | BountyTaskUpdateResponse>(isEditMode ? `/bounty/tasks/${editTaskId}` : "/bounty/tasks", {
        method: isEditMode ? "PUT" : "POST",
        body: JSON.stringify({
          title: formData.title.trim(),
          description: taskDescription,
          rewardDescription: formData.reward.trim(),
          deadlineAt,
        }),
      });
      setToast({ tone: "success", message: isEditMode ? "任务修改已保存，正在返回任务中心。" : "企业任务已发布，正在跳转任务中心。" });
      startTransition(() => {
        // 回任务中心时附带 taskId，任务中心负责自动选中和高亮。
        navigate(
          isEditMode
            ? `/enterprise/tasks?taskId=${response.taskId}&editedTaskId=${response.taskId}`
            : `/enterprise/tasks?createdTaskId=${response.taskId}`,
          { replace: false },
        );
      });
    } catch (error) {
      setToast({ tone: "error", message: buildErrorMessage(error, isEditMode ? "任务修改失败，请稍后重试。" : "任务发布失败，请稍后重试。") });
    } finally {
      setPublishing(false);
    }
  };

  const resolvedLogoUrl = buildEnterpriseLogoUrl(profile?.logoUrl ?? null, profile?.logoUpdatedAt);
  const accountName = resolveEnterpriseAccountName(profile);

  if (profileLoading && !profile) {
    return (
      <WorkspacePageLoadingScreen
        title={isEditMode ? "正在准备任务编辑页" : "正在准备任务发布页"}
        description="正在加载企业资料和任务表单，请稍候。"
      />
    );
  }

  return (
    <EnterpriseWorkspaceShell
      sectionLabel={isEditMode ? "Enterprise Task Edit" : "Enterprise Task Create"}
      title={isEditMode ? "编辑企业任务" : "发布企业任务"}
      displayName={accountName}
      companyName={profile?.companyName ?? null}
      logoUrl={resolvedLogoUrl}
      userSubtitle={profile?.companyName?.trim() || "企业账号"}
      navItems={getEnterpriseWorkspaceNavItems("create")}
      loading={profileLoading || taskLoading || publishing}
      lastUpdatedAt={lastUpdatedAt}
      onRefresh={() => {
        void Promise.all([loadProfile(), loadTask()]);
      }}
    >
      <motion.div variants={containerVariants} initial="hidden" animate="show" className="space-y-8">
        {profileError || taskError ? (
          <motion.section variants={itemVariants} className="rounded-[1.5rem] border border-amber-200 bg-amber-50/90 px-5 py-4 text-[15px] leading-7 text-amber-900 shadow-[0_10px_28px_rgba(245,158,11,0.12)]">
            <div className="flex items-start gap-3">
              <AlertCircle size={18} className="mt-0.5 shrink-0 text-amber-600" />
              <div>{taskError ?? profileError}</div>
            </div>
          </motion.section>
        ) : null}

        {editLocked ? (
          <motion.section variants={itemVariants} className="rounded-[1.5rem] border border-slate-200 bg-white/90 px-5 py-4 text-[15px] leading-7 text-slate-700 shadow-[0_10px_28px_rgba(15,23,42,0.08)]">
            该任务已完成筛选并已产生最终结果，本页仅保留信息查看，不再支持继续修改。
          </motion.section>
        ) : null}

        {createBlockedByApproval ? (
          <motion.section variants={itemVariants} className={joinClasses("rounded-[1.5rem] border px-5 py-4 text-[15px] leading-7", publishGateMeta?.toneClassName)}>
            <div className="flex items-start gap-3">
              <AlertCircle size={18} className={joinClasses("mt-0.5 shrink-0", publishGateMeta?.iconClassName)} />
              <div>
                <div className="text-[16px] font-semibold">{publishGateMeta?.title}</div>
                <div className="mt-1">{publishGateMeta?.description}</div>
                <div className="mt-3">
                  <Link to="/enterprise/profile" className="inline-flex items-center text-[15px] font-bold text-current underline-offset-4 transition hover:underline">
                    去资料与认证页处理
                  </Link>
                </div>
              </div>
            </div>
          </motion.section>
        ) : null}

        <motion.section variants={itemVariants} className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <Link
              to={isEditMode && editTaskId ? `/enterprise/tasks?taskId=${editTaskId}` : "/enterprise/tasks"}
              className="mb-4 inline-flex items-center text-[15px] font-medium text-slate-500 transition-colors hover:text-slate-900"
            >
              <ArrowLeft size={16} className="mr-1.5" />
              返回任务中心
            </Link>
            <h1 className="text-[32px] font-bold tracking-tight text-slate-900">{isEditMode ? "编辑企业任务" : "发布企业任务"}</h1>
            <p className="mt-2 text-[17px] leading-7 text-slate-600">
              {isEditMode
                ? "修改后将更新到任务中心。"
                : "完善任务背景、交付要求和奖励信息后即可发布。"}
            </p>
          </div>
          <Link
            to="/enterprise/dashboard"
            className="inline-flex items-center justify-center rounded-full border border-slate-200 bg-white px-5 py-2.5 text-[15px] font-semibold text-slate-600 shadow-sm transition-colors hover:bg-slate-50 hover:text-slate-900"
          >
            回到工作台
          </Link>
        </motion.section>

        <div className="grid gap-8 lg:grid-cols-12">
          <div className="flex flex-col gap-8 lg:col-span-8">
            <SectionCard
              icon={<PenTool size={20} className="text-indigo-600" />}
              title="1. 核心信息"
              desc="说明要解决的核心问题"
              accentClassName="bg-indigo-50 text-indigo-600"
            >
              <div className="space-y-6">
                <div>
                  <label className="mb-1.5 block text-[16px] font-semibold text-slate-800">
                    任务标题 <span className="text-rose-500">*</span>
                  </label>
                  <p className="mb-2.5 text-[15px] leading-6 text-slate-500">直接写清任务目标与交付方向。</p>
                  <input
                    type="text"
                    value={formData.title}
                    onChange={(event) => handleTextChange("title", event.target.value)}
                    placeholder="例如：AI 营销增长策略产品化方案设计"
                    className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-[15px] font-medium text-slate-900 placeholder-slate-400 shadow-sm transition-all focus:border-indigo-500 focus:bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
                  />
                </div>

                <div>
                  <label className="mb-1.5 block text-[16px] font-semibold text-slate-800">
                    任务背景 <span className="text-rose-500">*</span>
                  </label>
                  <textarea
                    value={formData.background}
                    onChange={(event) => handleTextChange("background", event.target.value)}
                    placeholder="简单介绍业务现状。例如：我们目前有一套面向 C 端的 AI 工具，现计划拓展 B 端中小企业市场..."
                    className="min-h-[100px] w-full resize-y rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-[15px] font-medium text-slate-900 placeholder-slate-400 shadow-sm transition-all focus:border-indigo-500 focus:bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
                  />
                </div>

                <div>
                  <label className="mb-1.5 block text-[16px] font-semibold text-slate-800">你希望学生解决什么问题？</label>
                  <textarea
                    value={formData.problem}
                    onChange={(event) => handleTextChange("problem", event.target.value)}
                    placeholder="描述更具体的痛点或目标。例如：现有产品缺乏商业化切入点，需要一套更有逻辑的增长策划方案..."
                    className="min-h-[100px] w-full resize-y rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-[15px] font-medium text-slate-900 placeholder-slate-400 shadow-sm transition-all focus:border-indigo-500 focus:bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
                  />
                </div>
              </div>
            </SectionCard>

            <SectionCard
              icon={<Target size={20} className="text-amber-600" />}
              title="2. 交付要求"
              desc="明确什么算一次有效提交"
              accentClassName="bg-amber-50 text-amber-600"
            >
              <div className="space-y-6">
                <div>
                  <label className="mb-1.5 block text-[16px] font-semibold text-slate-800">
                    需要提交什么 <span className="text-rose-500">*</span>
                  </label>
                  <textarea
                    value={formData.deliveryWhat}
                    onChange={(event) => handleTextChange("deliveryWhat", event.target.value)}
                    placeholder="具体列出所需产出物。例如：1 份包含用户旅程分析的 PPT 文档，至少 3 个核心页面的线框图..."
                    className="min-h-[100px] w-full resize-y rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-[15px] font-medium text-slate-900 placeholder-slate-400 shadow-sm transition-all focus:border-indigo-500 focus:bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
                  />
                </div>

                <div>
                  <label className="mb-2.5 block text-[16px] font-semibold text-slate-800">支持的交付形式（多选）</label>
                  <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                    {DELIVERY_FORMAT_OPTIONS.map((format) => {
                      const Icon = DELIVERY_FORMAT_ICONS[format.label];
                      const selected = formData.deliveryFormats.includes(format.label);
                      return (
                        <button
                          key={format.id}
                          type="button"
                          onClick={() => toggleArrayItem("deliveryFormats", format.label)}
                          className={joinClasses(
                            "relative flex flex-col items-center gap-2 rounded-xl border p-4 transition-all",
                            selected
                              ? "border-indigo-500 bg-indigo-50/80 text-indigo-700 shadow-[0_0_0_1px_rgba(99,102,241,1)]"
                              : "border-slate-200 bg-slate-50 text-slate-600 hover:border-slate-300 hover:bg-slate-100",
                          )}
                        >
                          <Icon size={24} className={selected ? "text-indigo-600" : "text-slate-400"} />
                          <span className="text-[14px] font-semibold">{format.label}</span>
                          {selected ? (
                            <div className="absolute right-2 top-2 rounded-full bg-indigo-500 p-0.5 text-white">
                              <Check size={10} />
                            </div>
                          ) : null}
                        </button>
                      );
                    })}
                  </div>
                </div>

                <div>
                  <label className="mb-1.5 block text-[16px] font-semibold text-slate-800">你最看重什么？（审核标准）</label>
                  <textarea
                    value={formData.valueMost}
                    onChange={(event) => handleTextChange("valueMost", event.target.value)}
                    placeholder="例如：相比视觉效果，我们更看重方案的数据依据和逻辑自洽程度..."
                    className="min-h-[80px] w-full resize-y rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-[15px] font-medium text-slate-900 placeholder-slate-400 shadow-sm transition-all focus:border-indigo-500 focus:bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
                  />
                </div>
              </div>
            </SectionCard>

            <SectionCard
              icon={<Briefcase size={20} className="text-emerald-600" />}
              title="3. 任务属性与奖励"
              desc="设置截止时间、期望与回报"
              accentClassName="bg-emerald-50 text-emerald-600"
            >
              <div className="space-y-6">
                <div className="grid gap-6 sm:grid-cols-2">
                  <div>
                    <label className="mb-1.5 block text-[16px] font-semibold text-slate-800">
                      奖励与继续接触预期 <span className="text-rose-500">*</span>
                    </label>
                    <input
                      type="text"
                      value={formData.reward}
                      onChange={(event) => handleTextChange("reward", event.target.value)}
                      placeholder="例如：基础奖金 ¥3000 + 优秀者发面试邀请"
                      className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-[15px] font-medium text-slate-900 placeholder-slate-400 shadow-sm transition-all focus:border-indigo-500 focus:bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
                    />
                  </div>

                  <div className="flex flex-col">
                    <label className="mb-2 block text-[16px] font-semibold text-slate-800">
                      截止时间 <span className="text-rose-500">*</span>
                    </label>
                    <div className="mb-2.5 flex flex-wrap gap-2">
                      {datePresets.map((preset) => (
                        <button
                          key={preset.label}
                          type="button"
                          onClick={() => handleTextChange("deadline", preset.value)}
                          className={joinClasses(
                            "rounded-lg border px-3 py-1.5 text-[14px] font-medium transition-all",
                            formData.deadline === preset.value
                              ? "border-indigo-200 bg-indigo-50 text-indigo-700"
                              : "border-slate-200 bg-slate-50 text-slate-600 hover:bg-slate-100 hover:text-slate-900",
                          )}
                        >
                          {preset.label}
                        </button>
                      ))}
                    </div>

                    <div className="relative" ref={calendarRef}>
                      <button
                        type="button"
                        onClick={(event) => {
                          if (!isCalendarOpen) {
                            const rect = event.currentTarget.getBoundingClientRect();
                            const spaceBelow = window.innerHeight - rect.bottom;
                            setDropdownPos(spaceBelow < 350 ? "up" : "down");
                          }
                          setIsCalendarOpen((current) => !current);
                        }}
                        className={joinClasses(
                          "flex w-full items-center justify-between rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-[15px] font-medium shadow-sm transition-all hover:bg-slate-100 focus:border-indigo-500 focus:bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500/20",
                          formData.deadline ? "text-slate-900" : "text-slate-400",
                        )}
                      >
                        <div className="flex items-center gap-2.5">
                          <CalendarDays size={18} className={formData.deadline ? "text-indigo-500" : "text-slate-400"} />
                          {formData.deadline || "请选择截止日期"}
                        </div>
                        <ChevronDown size={16} className={joinClasses("text-slate-400 transition-transform", isCalendarOpen && "rotate-180")} />
                      </button>

                      <AnimatePresence>
                        {isCalendarOpen ? (
                          <motion.div
                            initial={{ opacity: 0, y: dropdownPos === "down" ? 10 : -10, scale: 0.95 }}
                            animate={{ opacity: 1, y: 0, scale: 1 }}
                            exit={{ opacity: 0, y: dropdownPos === "down" ? 10 : -10, scale: 0.95 }}
                            transition={{ duration: 0.15 }}
                            className={joinClasses(
                              "absolute left-0 z-50 w-[300px] overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-[0_24px_50px_rgba(15,23,42,0.15)] ring-1 ring-slate-900/5",
                              dropdownPos === "down" ? "top-full mt-2 origin-top-left" : "bottom-full mb-2 origin-bottom-left",
                            )}
                          >
                            <div className="flex items-center justify-between border-b border-slate-100 bg-slate-50/80 px-4 py-3">
                              <button
                                type="button"
                                onClick={(event) => {
                                  event.stopPropagation();
                                  setViewDate(new Date(currentYear, currentMonthIndex - 1, 1));
                                }}
                                className="flex h-8 w-8 items-center justify-center rounded-lg border border-transparent text-slate-400 transition-colors hover:border-slate-200 hover:bg-white hover:text-slate-700 hover:shadow-sm"
                              >
                                <ChevronLeft size={16} />
                              </button>
                              <div className="text-[15px] font-bold text-slate-800">
                                {currentYear}年 {currentMonthIndex + 1}月
                              </div>
                              <button
                                type="button"
                                onClick={(event) => {
                                  event.stopPropagation();
                                  setViewDate(new Date(currentYear, currentMonthIndex + 1, 1));
                                }}
                                className="flex h-8 w-8 items-center justify-center rounded-lg border border-transparent text-slate-400 transition-colors hover:border-slate-200 hover:bg-white hover:text-slate-700 hover:shadow-sm"
                              >
                                <ChevronRight size={16} />
                              </button>
                            </div>

                            <div className="p-4">
                              <div className="mb-3 grid grid-cols-7 gap-1 text-center">
                                {["日", "一", "二", "三", "四", "五", "六"].map((dayLabel) => (
                                  <div key={dayLabel} className="text-[13px] font-bold text-slate-400">
                                    {dayLabel}
                                  </div>
                                ))}
                              </div>

                              <div className="grid grid-cols-7 gap-1">
                                {days.map((day, index) => {
                                  if (!day) {
                                    return <div key={`empty-${index}`} />;
                                  }
                                  const dateString = `${currentYear}-${String(currentMonthIndex + 1).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
                                  const selected = formData.deadline === dateString;
                                  const isToday =
                                    today.getDate() === day
                                    && today.getMonth() === currentMonthIndex
                                    && today.getFullYear() === currentYear;
                                  return (
                                    <button
                                      key={day}
                                      type="button"
                                      onClick={() => handleDateSelect(day)}
                                      className={joinClasses(
                                        "mx-auto flex h-8 w-8 items-center justify-center rounded-full text-[14px] font-medium transition-all",
                                        selected
                                          ? "bg-indigo-600 text-white shadow-md shadow-indigo-200 hover:scale-110 hover:bg-indigo-700"
                                          : isToday
                                            ? "bg-indigo-50 text-indigo-700 hover:scale-110 hover:bg-indigo-100"
                                            : "text-slate-700 hover:scale-110 hover:bg-slate-100",
                                      )}
                                    >
                                      {day}
                                    </button>
                                  );
                                })}
                              </div>
                            </div>
                          </motion.div>
                        ) : null}
                      </AnimatePresence>
                    </div>
                  </div>
                </div>

                <div>
                  <label className="mb-2 block text-[16px] font-semibold text-slate-800">适合的任务方向（可多选）</label>
                  <div className="flex flex-wrap gap-2">
                    {DIRECTION_TAGS.map((tag) => (
                      <button
                        key={tag}
                        type="button"
                        onClick={() => toggleArrayItem("directionTags", tag)}
                        className={joinClasses(
                          "rounded-lg border px-3 py-1.5 text-[14px] font-medium transition-all",
                          formData.directionTags.includes(tag)
                            ? "border-indigo-200 bg-indigo-50 text-indigo-700"
                            : "border-slate-200 bg-slate-50 text-slate-600 hover:bg-slate-100",
                        )}
                      >
                        {tag}
                      </button>
                    ))}
                  </div>
                </div>

                <div>
                  <label className="mb-2 block text-[16px] font-semibold text-slate-800">偏好的候选人群（可选）</label>
                  <div className="flex flex-wrap gap-2">
                    {AUDIENCE_TAGS.map((tag) => (
                      <button
                        key={tag}
                        type="button"
                        onClick={() => toggleArrayItem("audienceTags", tag)}
                        className={joinClasses(
                          "rounded-lg border px-3 py-1.5 text-[14px] font-medium transition-all",
                          formData.audienceTags.includes(tag)
                            ? "border-emerald-200 bg-emerald-50 text-emerald-700"
                            : "border-slate-200 bg-slate-50 text-slate-600 hover:bg-slate-100",
                        )}
                      >
                        {tag}
                      </button>
                    ))}
                  </div>
                </div>

                <div className="space-y-6 border-t border-slate-100 pt-6">
                  <div>
                    <label className="mb-1.5 block text-[16px] font-semibold text-slate-800">参考资料链接（可选）</label>
                    <input
                      type="text"
                      value={formData.referenceLink}
                      onChange={(event) => handleTextChange("referenceLink", event.target.value)}
                      placeholder="如官网资料、产品介绍页、公开文档链接"
                      className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-[15px] font-medium text-slate-900 placeholder-slate-400 shadow-sm transition-all focus:border-indigo-500 focus:bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
                    />
                  </div>

                  <div>
                    <label className="mb-1.5 block text-[16px] font-semibold text-slate-800">注意事项 / 常见误区提醒（可选）</label>
                    <textarea
                      value={formData.notes}
                      onChange={(event) => handleTextChange("notes", event.target.value)}
                      placeholder="例如不要只做表面包装、更看重分析逻辑、请附关键过程说明等"
                      className="min-h-[80px] w-full resize-y rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-[15px] font-medium text-slate-900 placeholder-slate-400 shadow-sm transition-all focus:border-indigo-500 focus:bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
                    />
                  </div>
                </div>
              </div>
            </SectionCard>
          </div>

          <div className="lg:col-span-4">
            <div className="sticky top-28 flex max-h-[calc(100vh-8rem)] flex-col gap-5">
              <motion.div variants={itemVariants} className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-[2rem] border border-slate-200/60 bg-white shadow-[0_12px_40px_rgba(148,163,184,0.12)]">
                <div className="z-10 flex items-center justify-between border-b border-slate-100 bg-slate-50/80 px-5 py-4">
                  <div className="flex items-center gap-2">
                    <Eye size={16} className="text-slate-400" />
                    <span className="text-[17px] font-bold text-slate-700">学生端效果预览</span>
                  </div>
                  <span className="text-[13px] font-semibold text-slate-500">预览</span>
                </div>

                <div className="flex-1 overflow-y-auto p-6 [&::-webkit-scrollbar-thumb]:rounded-full [&::-webkit-scrollbar-thumb]:bg-slate-200 [&::-webkit-scrollbar-track]:bg-transparent [&::-webkit-scrollbar]:w-1.5 hover:[&::-webkit-scrollbar-thumb]:bg-slate-300">
                  <h3 className={joinClasses("text-[24px] font-bold leading-tight", formData.title ? "text-slate-900" : "text-slate-300")}>
                    {formData.title || "任务标题预览"}
                  </h3>

                  <div className="mt-4 flex flex-wrap gap-2">
                    {previewChips.length > 0 ? (
                      previewChips.map((chip) => (
                        <span
                          key={chip}
                          className={joinClasses(
                            "rounded-md px-2.5 py-1 text-[13px] font-medium",
                            formData.audienceTags.includes(chip)
                              ? "bg-emerald-50 text-emerald-600"
                              : "bg-slate-100 text-slate-600",
                          )}
                        >
                          {chip}
                        </span>
                      ))
                    ) : (
                      <span className="rounded-md border border-slate-100 bg-slate-50 px-2.5 py-1 text-[13px] font-medium text-slate-400">
                        标签与方向预览
                      </span>
                    )}
                  </div>

                  <div className="mt-5 space-y-3 rounded-xl border border-slate-100/80 bg-slate-50/60 p-4">
                    <div>
                      <div className="mb-1 text-[15px] font-bold text-slate-800">任务背景</div>
                      <div className={joinClasses("text-[16px] leading-7", parsedPreview.background ? "text-slate-600" : "text-slate-400")}>
                        {parsedPreview.background || "任务背景预览"}
                      </div>
                    </div>
                    <div>
                      <div className="mb-1 text-[15px] font-bold text-slate-800">希望解决什么问题</div>
                      <div className={joinClasses("text-[16px] leading-7", parsedPreview.problem ? "text-slate-600" : "text-slate-400")}>
                        {parsedPreview.problem || "问题目标预览"}
                      </div>
                    </div>
                  </div>

                  <hr className="my-5 border-slate-100" />

                  <div className="space-y-3">
                    <div className="flex items-start gap-3">
                      <div className="mt-0.5 rounded bg-amber-50 p-1 text-amber-500">
                        <Gift size={14} />
                      </div>
                      <div>
                        <div className="text-[15px] font-bold text-slate-900">任务奖励</div>
                        <div className={joinClasses("mt-0.5 text-[15px] font-medium leading-6", formData.reward ? "text-slate-600" : "text-slate-300")}>
                          {formData.reward || "尚未填写奖励信息"}
                        </div>
                      </div>
                    </div>

                    <div className="flex items-start gap-3">
                      <div className="mt-0.5 rounded bg-slate-100 p-1 text-slate-500">
                        <CalendarDays size={14} />
                      </div>
                      <div>
                        <div className="text-[15px] font-bold text-slate-900">截止时间</div>
                        <div className={joinClasses("mt-0.5 text-[15px] font-medium leading-6", formData.deadline ? "text-slate-600" : "text-slate-300")}>
                          {formatPreviewDate(formData.deadline)}
                        </div>
                      </div>
                    </div>
                  </div>

                  <div className="mt-5 rounded-xl border border-slate-100 bg-slate-50 p-4">
                    <div className="mb-2 text-[15px] font-bold text-slate-900">需要提交什么</div>
                    <div className={joinClasses("text-[15px] leading-7", parsedPreview.deliveryWhat ? "text-slate-600" : "text-slate-400")}>
                      {parsedPreview.deliveryWhat || "交付要求预览"}
                    </div>

                    {parsedPreview.deliveryFormats.length > 0 ? (
                      <div className="mt-3 flex flex-wrap gap-1.5 border-t border-slate-200/60 pt-3">
                        {parsedPreview.deliveryFormats.map((format) => (
                          <span key={format} className="rounded border border-slate-200 bg-white px-2 py-0.5 text-[13px] text-slate-500">
                            {format}
                          </span>
                        ))}
                      </div>
                    ) : null}
                  </div>

                  {parsedPreview.valueMost ? (
                    <div className="mt-4 rounded-xl border border-slate-100 bg-white p-4 shadow-sm">
                      <div className="mb-1 text-[15px] font-bold text-slate-900">审核时最看重</div>
                      <div className="text-[16px] leading-7 text-slate-600">{parsedPreview.valueMost}</div>
                    </div>
                  ) : null}

                  {parsedPreview.notes || parsedPreview.referenceLink ? (
                    <div className="mt-3 space-y-3 pt-2">
                      {parsedPreview.notes ? (
                        <div className="rounded-xl border border-amber-100/60 bg-amber-50/40 p-3.5">
                          <div className="mb-1 flex items-center gap-1.5 text-[15px] font-bold text-amber-800">
                            <AlertCircle size={14} />
                            注意事项
                          </div>
                          <div className="text-[16px] leading-7 text-amber-900/70">{parsedPreview.notes}</div>
                        </div>
                      ) : null}
                      {parsedPreview.referenceLink ? (
                        previewReferenceHref ? (
                          <a
                            href={previewReferenceHref}
                            target="_blank"
                            rel="noreferrer"
                            className="flex items-center gap-2 rounded-xl border border-indigo-100/50 bg-indigo-50/50 px-3.5 py-2.5 text-[16px] text-indigo-600 transition-colors hover:bg-indigo-50"
                          >
                            <LinkIcon size={14} className="shrink-0" />
                            <span className="truncate">{parsedPreview.referenceLink}</span>
                          </a>
                        ) : (
                          <div className="flex items-center gap-2 rounded-xl border border-indigo-100/50 bg-indigo-50/50 px-3.5 py-2.5 text-[16px] text-indigo-600">
                            <LinkIcon size={14} className="shrink-0" />
                            <span className="truncate">{parsedPreview.referenceLink}</span>
                          </div>
                        )
                      ) : null}
                    </div>
                  ) : null}
                </div>
              </motion.div>

              <motion.div variants={itemVariants} className="shrink-0 flex flex-col gap-3">
                <button
                  type="button"
                  disabled={submitDisabled}
                  onClick={handlePublish}
                  className={joinClasses(
                    "group inline-flex w-full items-center justify-center rounded-2xl py-4 text-[16px] font-bold transition-all",
                    !submitDisabled
                      ? "bg-slate-900 text-white shadow-lg hover:-translate-y-0.5 hover:bg-slate-800 hover:shadow-xl"
                      : "cursor-not-allowed bg-slate-200 text-slate-400",
                  )}
                >
                  {publishing ? (isEditMode ? "正在保存修改..." : "正在发布任务...") : (isEditMode ? "保存修改" : "确认信息并发布任务")}
                  <Send size={16} className={joinClasses("ml-2 transition-transform", !submitDisabled && "group-hover:translate-x-1")} />
                </button>
                {submitDisabled ? (
                  <div className="flex items-center justify-center gap-1.5 text-[14px] text-slate-500">
                    <AlertCircle size={12} />
                    {submitHint}
                  </div>
                ) : null}
                <div className="rounded-2xl border border-slate-200 bg-white/80 px-4 py-3 text-[14px] leading-7 text-slate-500 shadow-sm">
                  发布后，学生将按这里展示的内容参与任务。
                </div>
                {createBlockedByApproval ? (
                  <div className="rounded-2xl border border-amber-200 bg-amber-50/80 px-4 py-3 text-[14px] leading-7 text-amber-800 shadow-sm">
                    认证通过后即可正式发布。
                  </div>
                ) : null}
                <div className="text-center text-[13px] text-slate-400">
                  内容篇幅 {taskDescription.length} / 4000
                </div>
              </motion.div>
            </div>
          </div>
        </div>
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
                "rounded-full px-5 py-3 text-[15px] font-semibold shadow-[0_14px_40px_rgba(15,23,42,0.18)]",
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
