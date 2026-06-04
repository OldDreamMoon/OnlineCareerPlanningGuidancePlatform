import { AnimatePresence, motion } from "framer-motion";
import {
  AlertCircle,
  ArrowRight,
  Briefcase,
  CheckCircle2,
  ChevronLeft,
  FileImage,
  FileText,
  Loader2,
  MessageSquare,
  MessageSquarePlus,
  ShieldCheck,
  Sparkles,
  Target,
  Trash2,
  UploadCloud,
  XCircle,
  Zap,
} from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import MentorIdentityAvatar from "../components/avatar/MentorIdentityAvatar";
import StudentWorkspaceTopbar, { buildStudentWorkspacePrimaryNav } from "../components/student/StudentWorkspaceTopbar";
import { ApiClientError, apiRequest, buildQuery } from "../lib/apiClient";
import { formatMoneyFen } from "../lib/formatters";
import { buildMentorIdentityLine } from "../lib/mentorNames";

type TimeValue = number | string | null;

type MentorDetailResponse = {
  userId: number;
  displayName: string;
  realName: string | null;
  showRealName: boolean;
  companyName: string | null;
  jobTitle: string | null;
  avatarUrl: string;
  expertiseTags: string[];
  serviceScenes: string[];
  bio: string | null;
  priceFen: number;
  packages: MentorServicePackage[];
  avgRating: number | null;
  totalOrders: number;
  available: boolean;
  favorited: boolean;
  recentReviews: Array<{
    orderNo: string;
    studentDisplayName: string;
    rating: number;
    comment: string;
    createdAt: string;
  }>;
};

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

type PrepSheetDraft = {
  mentorUserId: number;
  scene: string;
  targetPosition: string;
  summary: string;
  coreQuestions: string[];
  materials: string[];
  expectedOutcomes: string[];
  updatedAt: string;
};

type MentorPrepSheetGenerateResponse = {
  mentorUserId: number;
  mentorDisplayName: string;
  mentorCompanyName: string | null;
  mentorJobTitle: string | null;
  scene: string;
  targetPosition: string;
  summaryDraft: string;
  coreQuestions: string[];
  suggestedMaterials: string[];
  expectedOutcomes: string[];
  signalTags: string[];
};

type StudentProfileSummary = {
  userId: number;
  displayName: string;
  targetPosition: string | null;
  skillTags: string[];
  selfIntro: string | null;
  portrait: {
    tags: Array<{
      label: string;
    }>;
  } | null;
};

type ConsultOrderCreateResponse = {
  orderNo: string;
  amountFen: number;
  status: string;
  appointmentStartAt: string | null;
  appointmentEndAt: string | null;
  sceneCode: string | null;
  currentAttachmentCount: number;
};

type ConsultOrderAttachmentBatchUploadResponse = {
  records: Array<{
    attachmentId: number;
    attachmentType: string;
    slotCode: string;
    originalFilename: string;
    description: string | null;
    sourceStage: string;
    sizeBytes: number;
    lifecycleStatus: string;
    uploadedAt: string;
  }>;
  currentAttachmentCount: number;
};

type StructuredQuestionForm = {
  problem: string;
  background: string;
  tried: string;
  expected: string;
  extra: string;
};

type PrepSheetForm = {
  summary: string;
  coreQuestions: string[];
  expectedOutcomesText: string;
  suggestedMaterials: string[];
};

type MaterialTypeOption = {
  code: string;
  label: string;
  slotMode: "single" | "multi";
};

type UploadMaterialItem = {
  id: string;
  file: File;
  attachmentType: string;
  typeLabel: string;
  slotMode: "single" | "multi";
  sizeLabel: string;
  description: string;
  status: "queued" | "uploading" | "uploaded" | "error";
  errorMsg: string | null;
};

const PREP_LATEST_STORAGE_PREFIX = "bishe.mentor.prep.latest.";
const CONSULT_OBJECTIVES = [
  "获得简历修改建议",
  "获得项目表达优化建议",
  "获得岗位方向建议",
  "获得模拟面试复盘建议",
  "获得 Offer 决策建议",
  "获得下一步行动清单",
];
const MATERIAL_TYPE_OPTIONS: MaterialTypeOption[] = [
  { code: "RESUME", label: "简历", slotMode: "single" },
  { code: "JOB_DESCRIPTION", label: "岗位 JD", slotMode: "single" },
  { code: "PROJECT_MATERIAL", label: "项目材料", slotMode: "multi" },
  { code: "OFFER_MATERIAL", label: "Offer 材料", slotMode: "multi" },
  { code: "SUPPLEMENTARY", label: "补充材料", slotMode: "multi" },
];
const MATERIAL_TYPE_MAP = new Map(MATERIAL_TYPE_OPTIONS.map((item) => [item.code, item]));
const SOURCE_PAGE_LABELS: Record<string, string> = {
  MENTOR_MARKETPLACE: "来自导师广场",
  MENTOR_MARKETPLACE_RECOMMENDATION: "来自 AI 推荐区",
  MENTOR_MARKETPLACE_FAVORITES: "来自收藏列表",
};
const SCENE_CODE_MAP: Record<string, string> = {
  "简历诊断": "RESUME_DIAGNOSIS",
  "项目表达": "PROJECT_STORYTELLING",
  "模拟面试复盘": "MOCK_INTERVIEW_REVIEW",
  "岗位方向选择": "CAREER_DIRECTION",
  "校招投递策略": "CAMPUS_RECRUITMENT_STRATEGY",
  "转行 / 跨专业求职": "CAREER_TRANSITION",
  "Offer 对比与决策": "OFFER_DECISION",
};

function getPackageDeliveryLabel(deliveryMode: MentorServicePackage["deliveryMode"], durationMinutes: number | null) {
  if (deliveryMode === "APPOINTMENT") {
    return durationMinutes ? `${durationMinutes} 分钟预约` : "预约时段";
  }
  return "图文异步";
}

function getSlotDurationMinutes(slot: MentorScheduleSlot) {
  const start = new Date(slot.startAt ?? "").getTime();
  const end = new Date(slot.endAt ?? "").getTime();
  if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start) {
    return 0;
  }
  return Math.round((end - start) / 60000);
}

function formatSlotDateTime(slot: MentorScheduleSlot) {
  const start = new Date(slot.startAt ?? "");
  const end = new Date(slot.endAt ?? "");
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) {
    return "时间待确认";
  }
  const dateLabel = start.toLocaleDateString("zh-CN", {
    month: "numeric",
    day: "numeric",
    weekday: "short",
  });
  const startLabel = start.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit", hour12: false });
  const endLabel = end.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit", hour12: false });
  return `${dateLabel} ${startLabel} - ${endLabel}`;
}

function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function buildLatestPrepStorageKey(userId: number | null) {
  return `${PREP_LATEST_STORAGE_PREFIX}${userId ?? "guest"}`;
}

function readLatestPrepDraft(userId: number | null) {
  if (typeof window === "undefined") {
    return null;
  }

  try {
    const raw = window.localStorage.getItem(buildLatestPrepStorageKey(userId));
    if (!raw) {
      return null;
    }
    return JSON.parse(raw) as PrepSheetDraft;
  } catch {
    return null;
  }
}

function writeLatestPrepDraft(userId: number | null, draft: PrepSheetDraft) {
  if (typeof window === "undefined") {
    return;
  }

  try {
    window.localStorage.setItem(buildLatestPrepStorageKey(userId), JSON.stringify(draft));
  } catch {
    // 本地草稿写入失败时不阻塞主流程。
  }
}

function normalizeSourcePage(rawValue: string | null) {
  if (!rawValue) {
    return "MENTOR_MARKETPLACE";
  }
  const normalized = rawValue.trim().toUpperCase().replace(/[^A-Z_]+/g, "_");
  return SOURCE_PAGE_LABELS[normalized] ? normalized : "MENTOR_MARKETPLACE";
}

function formatFileSize(sizeBytes: number) {
  if (sizeBytes >= 1024 * 1024) {
    return `${(sizeBytes / (1024 * 1024)).toFixed(1)} MB`;
  }
  return `${Math.max(1, Math.round(sizeBytes / 1024))} KB`;
}

function sanitizeTextList(values: string[]) {
  return values.map((item) => item.trim()).filter(Boolean);
}

function padCoreQuestions(values: string[]) {
  const normalized = sanitizeTextList(values).slice(0, 3);
  while (normalized.length < 3) {
    normalized.push("");
  }
  return normalized;
}

function buildStructuredDefaults(latestDraft: PrepSheetDraft | null): StructuredQuestionForm {
  return {
    problem: latestDraft?.coreQuestions[0]?.trim() || latestDraft?.summary?.trim() || "",
    background: latestDraft?.targetPosition?.trim() ? `目标岗位：${latestDraft.targetPosition.trim()}` : "",
    tried: "",
    expected: latestDraft?.expectedOutcomes?.join("、") || "",
    extra: "",
  };
}

function buildPrepDefaults(latestDraft: PrepSheetDraft | null): PrepSheetForm {
  return {
    summary: latestDraft?.summary?.trim() || "",
    coreQuestions: padCoreQuestions(latestDraft?.coreQuestions || []),
    expectedOutcomesText: latestDraft?.expectedOutcomes?.join("、") || "",
    suggestedMaterials: sanitizeTextList(latestDraft?.materials || []),
  };
}

function buildQuestionTextPreview(structuredData: StructuredQuestionForm, prepSheet: PrepSheetForm) {
  const sections = [
    structuredData.problem.trim() ? `本次最想解决的问题：${structuredData.problem.trim()}` : "",
    structuredData.background.trim() ? `我的背景情况：${structuredData.background.trim()}` : "",
    structuredData.tried.trim() ? `我已经尝试过什么：${structuredData.tried.trim()}` : "",
    structuredData.expected.trim() ? `我希望导师给出的帮助：${structuredData.expected.trim()}` : "",
    structuredData.extra.trim() ? `额外补充说明：${structuredData.extra.trim()}` : "",
    prepSheet.summary.trim() ? `准备单摘要：${prepSheet.summary.trim()}` : "",
    sanitizeTextList(prepSheet.coreQuestions).length
      ? `核心咨询问题：\n${sanitizeTextList(prepSheet.coreQuestions).map((item, index) => `${index + 1}. ${item}`).join("\n")}`
      : "",
  ];

  return sections.filter(Boolean).join("\n\n").trim();
}

function getSceneRequiredMaterialTypes(scene: string) {
  if (scene.includes("简历")) {
    return ["RESUME", "JOB_DESCRIPTION"];
  }
  if (scene.includes("项目")) {
    return ["PROJECT_MATERIAL"];
  }
  if (scene.includes("Offer")) {
    return ["OFFER_MATERIAL"];
  }
  if (scene.includes("模拟面试")) {
    return ["RESUME", "PROJECT_MATERIAL"];
  }
  return ["RESUME"];
}

function detectSensitiveInfo(value: string) {
  return /1[3-9]\d{9}|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}|(?:\d{17}[\dXx])/.test(value);
}

function inferSceneCode(scene: string) {
  return SCENE_CODE_MAP[scene] || "GENERAL_CONSULT";
}

function getMaterialIcon(filename: string) {
  return /\.(png|jpg|jpeg)$/i.test(filename) ? FileImage : FileText;
}

function TopNav({ displayName, targetPosition }: { displayName: string | null; targetPosition: string }) {
  return (
    <StudentWorkspaceTopbar
      sectionLabel="Consult Creation"
      title="创建咨询订单"
      navItems={buildStudentWorkspacePrimaryNav("consultOrders")}
      position="sticky"
      displayName={displayName}
      userSubtitle={targetPosition || "正在完善求职方向"}
    />
  );
}

function SectionHeader({
  sectionLabel: _sectionLabel,
  title,
  description,
  icon: Icon,
}: {
  sectionLabel: string;
  title: string;
  description: string;
  icon: typeof Briefcase;
}) {
  return (
    <div className="mb-6 flex items-center gap-4">
      <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-[1.2rem] border border-indigo-100/50 bg-indigo-50/80 text-indigo-600 shadow-sm">
        <Icon size={22} />
      </div>
      <div className="flex min-h-[4.1rem] flex-col justify-center">
        <h2 className="text-2xl font-bold leading-tight text-slate-950">{title}</h2>
        <p className="mt-1 text-sm leading-relaxed text-slate-500">{description}</p>
      </div>
    </div>
  );
}

export default function ConsultCreatePage() {
  const { role, userId, displayName } = useAuth();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const latestDraft = useMemo(() => readLatestPrepDraft(userId), [userId]);
  // latest prep draft 承接导师广场准备单，URL 缺少参数时也能继续下单。
  const mentorUserId = Number(searchParams.get("mentorUserId") || latestDraft?.mentorUserId || 0);
  const initialScene = searchParams.get("scene") || latestDraft?.scene || "简历诊断";
  const sourcePage = normalizeSourcePage(searchParams.get("from") || searchParams.get("source"));
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const [mentor, setMentor] = useState<MentorDetailResponse | null>(null);
  const [studentProfile, setStudentProfile] = useState<StudentProfileSummary | null>(null);
  const [mentorLoading, setMentorLoading] = useState(role === "STUDENT" && mentorUserId > 0);
  const [mentorError, setMentorError] = useState<string | null>(null);
  const [activeScene, setActiveScene] = useState(initialScene);
  const [selectedPackageId, setSelectedPackageId] = useState<number | null>(null);
  const [scheduleSlots, setScheduleSlots] = useState<MentorScheduleSlot[]>([]);
  const [scheduleLoading, setScheduleLoading] = useState(false);
  const [scheduleError, setScheduleError] = useState<string | null>(null);
  const [selectedScheduleSlotId, setSelectedScheduleSlotId] = useState<number | null>(null);
  const [selectedObjectives, setSelectedObjectives] = useState<string[]>(
    latestDraft ? CONSULT_OBJECTIVES.filter((item) => latestDraft.expectedOutcomes.includes(item)) : [],
  );
  const [structuredData, setStructuredData] = useState<StructuredQuestionForm>(() => buildStructuredDefaults(latestDraft));
  const [prepSheet, setPrepSheet] = useState<PrepSheetForm>(() => buildPrepDefaults(latestDraft));
  const [activeMaterialType, setActiveMaterialType] = useState("RESUME");
  const [materials, setMaterials] = useState<UploadMaterialItem[]>([]);
  const [materialFeedback, setMaterialFeedback] = useState<string | null>(null);
  const [newSuggestedMaterial, setNewSuggestedMaterial] = useState("");
  const [isGeneratingPrep, setIsGeneratingPrep] = useState(false);
  const [generateError, setGenerateError] = useState<string | null>(null);
  const [submitLoading, setSubmitLoading] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  useEffect(() => {
    if (role !== "STUDENT" || mentorUserId <= 0) {
      setMentor(null);
      setMentorLoading(false);
      setMentorError(null);
      return;
    }

    let active = true;
    setMentorLoading(true);
    setMentorError(null);

    void apiRequest<MentorDetailResponse>(`/mentors/${mentorUserId}`)
      .then((response) => {
        if (!active) {
          return;
        }
        setMentor(response);
      })
      .catch((error) => {
        const apiError = error as ApiClientError;
        if (!active) {
          return;
        }
        setMentor(null);
        setMentorError(apiError.message || "加载导师信息失败");
      })
      .finally(() => {
        if (active) {
          setMentorLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [mentorUserId, role]);

  useEffect(() => {
    if (role !== "STUDENT") {
      setStudentProfile(null);
      return;
    }

    let active = true;
    void apiRequest<StudentProfileSummary>("/profiles/students/me")
      .then((response) => {
        if (active) {
          setStudentProfile(response);
        }
      })
      .catch(() => {
        if (active) {
          setStudentProfile(null);
        }
      });

    return () => {
      active = false;
    };
  }, [role]);

  const resolvedTargetPosition = useMemo(
    () => studentProfile?.targetPosition?.trim() || latestDraft?.targetPosition?.trim() || "",
    [latestDraft?.targetPosition, studentProfile?.targetPosition],
  );
  const portraitSummaryTags = useMemo(() => {
    const portraitTags = studentProfile?.portrait?.tags?.map((item) => item.label.trim()).filter(Boolean) ?? [];
    const skillTags = studentProfile?.skillTags?.map((item) => item.trim()).filter(Boolean) ?? [];
    return Array.from(new Set([...portraitTags, ...skillTags])).slice(0, 6);
  }, [studentProfile?.portrait?.tags, studentProfile?.skillTags]);
  const portraitSummaryText = studentProfile?.selfIntro?.trim()
    || (portraitSummaryTags.length ? `已同步 ${portraitSummaryTags.length} 个画像关键词` : "建议先去资料中心补充目标岗位、自我介绍与技能标签");

  useEffect(() => {
    if (!resolvedTargetPosition) {
      return;
    }

    // 学生目标岗位只自动填入背景字段，不覆盖用户已经手写的咨询背景。
    const autofillText = `目标岗位：${resolvedTargetPosition}`;
    setStructuredData((current) => {
      const currentBackground = current.background.trim();
      if ((currentBackground === "" || currentBackground.startsWith("目标岗位：")) && currentBackground !== autofillText) {
        return {
          ...current,
          background: autofillText,
        };
      }
      return current;
    });
  }, [resolvedTargetPosition]);

  useEffect(() => {
    if (mentorUserId <= 0) {
      return;
    }

    // 下单页编辑中的准备单持续写 latest，返回导师广场后仍能恢复。
    writeLatestPrepDraft(userId, {
      mentorUserId,
      scene: activeScene,
      targetPosition: resolvedTargetPosition,
      summary: prepSheet.summary.trim(),
      coreQuestions: sanitizeTextList(prepSheet.coreQuestions),
      materials: prepSheet.suggestedMaterials,
      expectedOutcomes: selectedObjectives,
      updatedAt: new Date().toISOString(),
    });
  }, [activeScene, mentorUserId, prepSheet.coreQuestions, prepSheet.summary, prepSheet.suggestedMaterials, resolvedTargetPosition, selectedObjectives, userId]);

  const enabledPackages = useMemo(
    () => (mentor?.packages ?? []).filter((item) => item.enabled),
    [mentor?.packages],
  );
  const selectedPackage = useMemo(
    () => enabledPackages.find((item) => item.id === selectedPackageId) ?? enabledPackages[0] ?? null,
    [enabledPackages, selectedPackageId],
  );
  const selectedScheduleSlot = useMemo(
    () => scheduleSlots.find((item) => item.id === selectedScheduleSlotId) ?? null,
    [scheduleSlots, selectedScheduleSlotId],
  );

  useEffect(() => {
    if (!enabledPackages.length) {
      setSelectedPackageId(null);
      return;
    }
    const preferredPackage = enabledPackages.find((item) => item.sceneLabel === initialScene || item.sceneCode === inferSceneCode(initialScene))
      ?? enabledPackages[0];
    setSelectedPackageId((current) => (
      current && enabledPackages.some((item) => item.id === current)
        ? current
        : preferredPackage.id
    ));
  }, [enabledPackages, initialScene]);

  useEffect(() => {
    if (!selectedPackage) {
      return;
    }
    if (activeScene !== selectedPackage.sceneLabel) {
      setActiveScene(selectedPackage.sceneLabel);
    }
  }, [activeScene, selectedPackage]);

  useEffect(() => {
    if (!selectedPackage || selectedPackage.deliveryMode !== "APPOINTMENT") {
      setScheduleSlots([]);
      setScheduleError(null);
      setScheduleLoading(false);
      setSelectedScheduleSlotId(null);
      return;
    }

    let active = true;
    setScheduleLoading(true);
    setScheduleError(null);
    const dateFrom = new Date().toISOString();

    // 只有预约型套餐才拉导师可用时段，并按套餐时长做二次过滤。
    void apiRequest<MentorScheduleSlotListResponse>(`/mentor/schedule/slots${buildQuery({ mentorUserId, dateFrom })}`)
      .then((response) => {
        if (!active) {
          return;
        }
        const filtered = (response.records ?? [])
          .filter((item) => item.status === "AVAILABLE")
          .filter((item) => {
            if (!selectedPackage.durationMinutes) {
              return true;
            }
            return getSlotDurationMinutes(item) === selectedPackage.durationMinutes;
          });
        setScheduleSlots(filtered);
        setSelectedScheduleSlotId((current) => (current && filtered.some((item) => item.id === current) ? current : filtered[0]?.id ?? null));
      })
      .catch((error) => {
        const apiError = error as ApiClientError;
        if (!active) {
          return;
        }
        setScheduleSlots([]);
        setSelectedScheduleSlotId(null);
        setScheduleError(apiError.message || "加载可预约时段失败");
      })
      .finally(() => {
        if (active) {
          setScheduleLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [mentorUserId, selectedPackage]);

  const questionTextPreview = useMemo(() => buildQuestionTextPreview(structuredData, prepSheet), [prepSheet, structuredData]);
  const questionTextTooLong = questionTextPreview.length > 2000;
  const hasSensitiveInfo = detectSensitiveInfo([
    structuredData.problem,
    structuredData.background,
    structuredData.expected,
    structuredData.extra,
    prepSheet.summary,
  ].join("\n"));

  const completionChecks = [
    structuredData.problem.trim().length >= 10,
    Boolean(structuredData.background.trim()),
    Boolean(structuredData.tried.trim()),
    Boolean(structuredData.expected.trim()),
    selectedObjectives.length > 0,
    materials.length > 0,
  ];
  const completionCount = completionChecks.filter(Boolean).length;
  const completenessLabel = completionCount >= 5 ? "信息较完整" : completionCount >= 3 ? "基本可用" : "建议补充";
  const completenessTone = completionCount >= 5 ? "emerald" : completionCount >= 3 ? "amber" : "rose";

  const requiredMaterialTypes = useMemo(() => getSceneRequiredMaterialTypes(activeScene), [activeScene]);
  const currentMaterialTypeSet = useMemo(() => new Set(materials.map((item) => item.attachmentType)), [materials]);
  const missingRequiredMaterials = requiredMaterialTypes.filter((code) => !currentMaterialTypeSet.has(code));
  // 必需材料当前只做提示不硬阻断，避免学生没有附件时无法先提交咨询问题。
  const canSubmit = mentorUserId > 0
    && Boolean(selectedPackage)
    && structuredData.problem.trim().length >= 10
    && !questionTextTooLong
    && !submitLoading
    && !isGeneratingPrep
    && (selectedPackage?.deliveryMode !== "APPOINTMENT" || Boolean(selectedScheduleSlotId));

  const containerVariants = {
    hidden: { opacity: 0 },
    show: { opacity: 1, transition: { staggerChildren: 0.08 } },
  };
  const itemVariants = {
    hidden: { opacity: 0, y: 18 },
    show: { opacity: 1, y: 0, transition: { type: "spring" as const, stiffness: 260, damping: 24 } },
  };

  const handleToggleObjective = (objective: string) => {
    setSelectedObjectives((current) => current.includes(objective)
      ? current.filter((item) => item !== objective)
      : [...current, objective]);
  };

  const handleImportQuestion = (question: string) => {
    const normalized = question.trim();
    if (!normalized) {
      return;
    }
    setStructuredData((current) => ({
      ...current,
      problem: current.problem.trim() ? `${current.problem.trim()}\n${normalized}` : normalized,
    }));
  };

  const handleAddSuggestedMaterial = () => {
    const normalized = newSuggestedMaterial.trim();
    if (!normalized) {
      return;
    }
    setPrepSheet((current) => ({
      ...current,
      suggestedMaterials: current.suggestedMaterials.includes(normalized)
        ? current.suggestedMaterials
        : [...current.suggestedMaterials, normalized],
    }));
    setNewSuggestedMaterial("");
  };

  const triggerUpload = () => {
    fileInputRef.current?.click();
  };

  const handleFilesSelected = (event: React.ChangeEvent<HTMLInputElement>) => {
    const pickedFiles = Array.from(event.target.files || []);
    if (!pickedFiles.length) {
      return;
    }

    const activeType = MATERIAL_TYPE_MAP.get(activeMaterialType);
    if (!activeType) {
      return;
    }

    // 单槽位材料本地先替换旧副本，提交后端时也会带 replaceCurrent。
    const acceptedFiles = activeType.slotMode === "single" ? pickedFiles.slice(0, 1) : pickedFiles;
    if (activeType.slotMode === "single" && pickedFiles.length > 1) {
      setMaterialFeedback(`${activeType.label} 为单槽位材料，本次仅保留第一份文件作为最新副本。`);
    } else {
      setMaterialFeedback(null);
    }

    setMaterials((current) => {
      const nextBase = activeType.slotMode === "single"
        ? current.filter((item) => item.attachmentType !== activeType.code)
        : [...current];

      acceptedFiles.forEach((file, index) => {
        nextBase.push({
          id: `${activeType.code}-${Date.now()}-${index}`,
          file,
          attachmentType: activeType.code,
          typeLabel: activeType.label,
          slotMode: activeType.slotMode,
          sizeLabel: formatFileSize(file.size),
          description: "",
          status: "queued",
          errorMsg: null,
        });
      });
      return nextBase;
    });

    event.target.value = "";
  };

  const handleRemoveMaterial = (id: string) => {
    setMaterials((current) => current.filter((item) => item.id !== id));
  };

  const handleGeneratePrepSheet = async (sceneToUse: string, resetStructuredData: boolean) => {
    if (!mentorUserId) {
      return;
    }

    setIsGeneratingPrep(true);
    setGenerateError(null);
    try {
      // AI 准备单只生成结构化问题和材料建议，正式订单仍以用户当前表单为准。
      const response = await apiRequest<MentorPrepSheetGenerateResponse>("/mentors/prep-sheet/generate", {
        method: "POST",
        body: JSON.stringify({
          mentorUserId,
          scene: sceneToUse,
          targetPosition: resolvedTargetPosition,
        }),
      });

      setActiveScene(response.scene);
      setPrepSheet({
        summary: response.summaryDraft || "",
        coreQuestions: padCoreQuestions(response.coreQuestions),
        expectedOutcomesText: response.expectedOutcomes.join("、"),
        suggestedMaterials: sanitizeTextList(response.suggestedMaterials),
      });
      setSelectedObjectives(CONSULT_OBJECTIVES.filter((item) => response.expectedOutcomes.includes(item)));
      if (resetStructuredData) {
        setStructuredData({
          problem: response.coreQuestions[0]?.trim() || response.summaryDraft || "",
          background: response.targetPosition?.trim() ? `目标岗位：${response.targetPosition.trim()}` : "",
          tried: "",
          expected: response.expectedOutcomes.join("、"),
          extra: "",
        });
      }
    } catch (error) {
      const apiError = error as ApiClientError;
      setGenerateError(apiError.message || "重新生成准备单失败");
    } finally {
      setIsGeneratingPrep(false);
    }
  };

  const handleSubmit = async () => {
    if (!canSubmit) {
      return;
    }

    setSubmitLoading(true);
    setSubmitError(null);

    try {
      // 先创建订单主体，附件按订单号再批量上传，便于失败时保留可支付订单。
      const createResponse = await apiRequest<ConsultOrderCreateResponse>("/consult/orders", {
        method: "POST",
        body: JSON.stringify({
          mentorUserId,
          sceneCode: selectedPackage?.sceneCode ?? inferSceneCode(activeScene),
          sourcePage,
          questionText: questionTextPreview,
          questionPayload: {
            primaryConcern: structuredData.problem.trim(),
            background: structuredData.background.trim(),
            attemptedActions: structuredData.tried.trim(),
            expectedHelp: structuredData.expected.trim(),
            additionalNotes: structuredData.extra.trim(),
          },
          problemSummary: prepSheet.summary.trim() || structuredData.problem.trim().slice(0, 120),
          coreQuestions: sanitizeTextList(prepSheet.coreQuestions),
          expectedOutcomes: selectedObjectives,
          selectedMaterialTypes: Array.from(new Set(materials.map((item) => item.attachmentType))),
          prepSheetSnapshot: {
            scene: activeScene,
            summaryDraft: prepSheet.summary.trim(),
            coreQuestions: sanitizeTextList(prepSheet.coreQuestions),
            suggestedMaterials: prepSheet.suggestedMaterials,
            expectedOutcomes: selectedObjectives,
          },
          mentorPackageId: selectedPackage && selectedPackage.id > 0 ? selectedPackage.id : undefined,
          scheduleSlotId: selectedPackage?.deliveryMode === "APPOINTMENT" ? selectedScheduleSlotId : undefined,
        }),
      });
      let attachmentUploadState: "partial" | null = null;

      if (materials.length) {
        setMaterials((current) => current.map((item) => ({ ...item, status: "uploading", errorMsg: null })));
        try {
          // 附件上传失败只标记 partial，不回滚已创建订单。
          const formData = new FormData();
          formData.append("manifestJson", JSON.stringify({
            items: materials.map((item) => ({
              attachmentType: item.attachmentType,
              slotCode: item.attachmentType,
              description: item.description.trim(),
              replaceCurrent: item.slotMode === "single",
              sourceStage: "ORDER_CREATE",
            })),
          }));
          materials.forEach((item) => {
            formData.append("files", item.file);
          });

          await apiRequest<ConsultOrderAttachmentBatchUploadResponse>(`/consult/orders/${createResponse.orderNo}/attachments/batch`, {
            method: "POST",
            body: formData,
          });
          setMaterials((current) => current.map((item) => ({ ...item, status: "uploaded", errorMsg: null })));
        } catch (error) {
          const apiError = error as ApiClientError;
          attachmentUploadState = "partial";
          setMaterials((current) => current.map((item) => ({ ...item, status: "error", errorMsg: apiError.message || "材料上传失败" })));
        }
      }

      navigate(`/consult/orders/${createResponse.orderNo}${buildQuery({
        entry: "create",
        attachmentUpload: attachmentUploadState,
      })}`, { replace: true });
    } catch (error) {
      const apiError = error as ApiClientError;
      setSubmitError(apiError.message || "创建咨询订单失败");
    } finally {
      setSubmitLoading(false);
    }
  };

  return (
    <div className="relative min-h-screen bg-[#eef3ff] pb-20 font-sans text-slate-900">
      <div className="pointer-events-none fixed inset-0 z-0 bg-[radial-gradient(circle_at_top,rgba(79,70,229,0.18),transparent_30%),radial-gradient(circle_at_bottom_right,rgba(20,184,166,0.12),transparent_28%),linear-gradient(180deg,#eff6ff_0%,#f8fafc_58%,#f8fafc_100%)]" />
      <div className="page-top-glow page-top-glow--indigo-soft" />

      <TopNav displayName={displayName} targetPosition={resolvedTargetPosition || "正在完善求职方向"} />

      <main className="relative z-10 mx-auto w-full max-w-[96rem] px-4 pb-8 pt-10 sm:px-6 lg:px-8">
        <motion.div variants={containerVariants} initial="hidden" animate="show" className="space-y-6">
          <motion.div variants={itemVariants} className="mb-4 flex items-center gap-4">
            <button
              type="button"
              onClick={() => navigate(-1)}
              className="flex h-10 w-10 items-center justify-center rounded-full border border-slate-200 bg-white/60 text-slate-500 shadow-sm backdrop-blur-md transition-all hover:bg-white hover:text-indigo-600"
            >
              <ChevronLeft size={20} />
            </button>
            <div>
              <h1 className="text-2xl font-black tracking-tight text-slate-900">创建咨询订单</h1>
              <p className="mt-1 text-sm text-slate-500">整理问题、确认材料、正式下单</p>
            </div>
            <div className="ml-auto flex items-center gap-3">
              <div className="inline-flex items-center gap-2 rounded-full border border-emerald-200/60 bg-emerald-50/80 px-3 py-1.5 text-xs font-semibold text-emerald-700 shadow-sm backdrop-blur-sm">
                <CheckCircle2 size={14} />
                {mentorUserId > 0 ? "已带入导师广场选择" : "待补齐导师上下文"}
              </div>
              <div className="hidden rounded-full border border-amber-200/60 bg-amber-50/80 px-3 py-1.5 text-xs font-semibold text-amber-700 shadow-sm lg:block">
                {SOURCE_PAGE_LABELS[sourcePage]}
              </div>
            </div>
          </motion.div>

          <div className="grid items-start gap-8 lg:grid-cols-12">
            <div className="space-y-8 lg:col-span-8">
              <motion.section variants={itemVariants} className="rounded-[2rem] border border-white/80 bg-white/70 p-6 shadow-[0_20px_50px_rgba(148,163,184,0.1)] backdrop-blur-xl">
                <SectionHeader
                  sectionLabel="Step 1"
                  title="导师与场景确认"
                  description="确认你选择的导师，以及本次咨询要聚焦的场景与目标。"
                  icon={Briefcase}
                />

                {mentorLoading ? (
                  <div className="space-y-4">
                    <div className="h-24 rounded-[1.5rem] bg-slate-100" />
                    <div className="h-16 rounded-[1.5rem] bg-slate-100" />
                  </div>
                ) : mentorError ? (
                  <div className="rounded-[1.5rem] border border-rose-100 bg-rose-50 px-4 py-4 text-sm leading-7 text-rose-700">
                    {mentorError}
                  </div>
                ) : mentor ? (
                  <>
                    <div className="flex flex-col gap-6 rounded-[1.5rem] border border-slate-100 bg-white p-5 shadow-sm sm:flex-row">
                      <div className="flex flex-1 gap-4">
                        <MentorIdentityAvatar
                          userId={mentor.userId}
                          displayName={mentor.displayName}
                          avatarUrl={mentor.avatarUrl}
                          alt={mentor.displayName}
                          className="h-16 w-16 shadow-sm"
                          fallbackClassName="bg-gradient-to-br from-indigo-100 to-indigo-50 text-indigo-700"
                        />
                        <div className="min-w-0">
                          <div className="flex flex-wrap items-center gap-2">
                            <h3 className="text-lg font-bold text-slate-900">{mentor.displayName}</h3>
                            {mentor.available ? (
                              <span className="flex items-center rounded-full border border-emerald-100 bg-emerald-50 px-2 py-0.5 text-[10px] font-bold text-emerald-600">
                                <span className="mr-1.5 h-1.5 w-1.5 rounded-full bg-emerald-500" />
                                近期可接单
                              </span>
                            ) : (
                              <span className="rounded-full border border-slate-200 bg-slate-100 px-2 py-0.5 text-[10px] font-bold text-slate-500">
                                当前较忙
                              </span>
                            )}
                          </div>
                          <div className="mt-1 text-sm font-medium text-slate-600">
                            {buildMentorIdentityLine(mentor, mentor.displayName)}
                          </div>
                          <div className="mt-2 flex flex-wrap gap-1.5">
                            {(mentor.expertiseTags.length ? mentor.expertiseTags : mentor.serviceScenes).slice(0, 4).map((tag) => (
                              <span key={tag} className="rounded-md bg-slate-100 px-2 py-0.5 text-[10px] font-semibold text-slate-500">
                                {tag}
                              </span>
                            ))}
                          </div>
                        </div>
                      </div>

                      <div className="hidden w-px bg-slate-100 sm:block" />

                      <div className="flex-1">
                        <div className="mb-3 text-sm font-bold text-slate-600">服务套餐</div>
                        <div className="space-y-3">
                          {enabledPackages.length ? enabledPackages.map((item) => {
                            const selected = selectedPackage?.id === item.id;
                            return (
                              <button
                                key={item.id}
                                type="button"
                                onClick={() => setSelectedPackageId(item.id)}
                                className={joinClasses(
                                  "w-full rounded-[1.15rem] border px-4 py-3 text-left transition-all",
                                  selected
                                    ? "border-indigo-500 bg-indigo-50 shadow-[0_12px_30px_rgba(99,102,241,0.14)]"
                                    : "border-slate-200 bg-white hover:border-indigo-200 hover:bg-indigo-50/40",
                                )}
                              >
                                <div className="flex items-start justify-between gap-4">
                                  <div>
                                    <div className="flex items-center gap-2 text-sm font-bold text-slate-900">
                                      <Target size={15} className={selected ? "text-indigo-600" : "text-slate-400"} />
                                      {item.packageName}
                                    </div>
                                    <div className="mt-1 text-sm font-medium text-slate-600">
                                      {item.sceneLabel} · {getPackageDeliveryLabel(item.deliveryMode, item.durationMinutes)}
                                    </div>
                                    {item.description ? (
                                      <p className="mt-2 text-sm leading-6 text-slate-500">
                                        {item.description}
                                      </p>
                                    ) : null}
                                  </div>
                                  <div className="text-right">
                                    <div className="text-xl font-black text-slate-900">{formatMoneyFen(item.priceFen)}</div>
                                    <div className="mt-1 text-xs font-semibold text-slate-400">
                                      {selected ? "当前已选" : "点击选择"}
                                    </div>
                                  </div>
                                </div>
                              </button>
                            );
                          }) : (
                            <div className="rounded-xl border border-dashed border-slate-200 bg-slate-50 px-4 py-4 text-sm leading-7 text-slate-500">
                              这位导师暂时还没有可用套餐，请返回广场重新选择导师。
                            </div>
                          )}
                        </div>

                        {selectedPackage?.deliveryMode === "APPOINTMENT" ? (
                          <div className="mt-4 rounded-[1.2rem] border border-indigo-100 bg-indigo-50/60 p-4">
                            <div className="flex items-center justify-between gap-3">
                              <div>
                                <div className="text-sm font-bold text-indigo-600">预约时段</div>
                                <div className="mt-1 text-sm font-semibold text-slate-900">
                                  请选择一个 {selectedPackage.durationMinutes ?? 45} 分钟的可预约时间
                                </div>
                              </div>
                              {scheduleLoading ? <Loader2 size={16} className="animate-spin text-indigo-500" /> : null}
                            </div>

                            {scheduleError ? (
                              <div className="mt-3 rounded-xl border border-rose-100 bg-rose-50 px-3 py-3 text-sm text-rose-700">
                                {scheduleError}
                              </div>
                            ) : null}

                            {scheduleSlots.length ? (
                              <div className="mt-3 grid gap-3 sm:grid-cols-2">
                                {scheduleSlots.map((slot) => {
                                  const selected = selectedScheduleSlotId === slot.id;
                                  return (
                                    <button
                                      key={slot.id}
                                      type="button"
                                      onClick={() => setSelectedScheduleSlotId(slot.id)}
                                      className={joinClasses(
                                        "rounded-xl border px-3 py-3 text-left transition-colors",
                                        selected
                                          ? "border-slate-900 bg-slate-900 text-white"
                                          : "border-white bg-white text-slate-700 hover:border-indigo-200 hover:bg-indigo-50",
                                      )}
                                    >
                                      <div className="text-sm font-bold">{formatSlotDateTime(slot)}</div>
                                      <div className={joinClasses("mt-1 text-xs", selected ? "text-slate-200" : "text-slate-500")}>
                                        {getSlotDurationMinutes(slot)} 分钟
                                      </div>
                                    </button>
                                  );
                                })}
                              </div>
                            ) : scheduleLoading ? null : (
                              <div className="mt-3 rounded-xl border border-dashed border-indigo-200 bg-white/90 px-4 py-4 text-sm leading-7 text-slate-500">
                                当前还没有与该套餐时长匹配的可预约时段。你可以先返回导师广场切换其他导师，或稍后再来查看。
                              </div>
                            )}
                          </div>
                        ) : selectedPackage ? (
                          <div className="mt-4 rounded-[1.2rem] border border-slate-200 bg-slate-50/80 px-4 py-4 text-sm leading-7 text-slate-500">
                            当前选择的是图文异步套餐。创建订单后会直接进入支付与材料同步，不需要提前占用导师排期。
                          </div>
                        ) : null}
                      </div>
                    </div>

                    <div className="mt-6">
                      <div className="mb-3 text-sm font-bold text-slate-700">你希望通过本次咨询达成什么目标？(多选)</div>
                      <div className="flex flex-wrap gap-2.5">
                        {CONSULT_OBJECTIVES.map((objective) => (
                          <button
                            key={objective}
                            type="button"
                            onClick={() => handleToggleObjective(objective)}
                            className={joinClasses(
                              "rounded-xl border px-4 py-2 text-sm font-medium transition-all duration-200",
                              selectedObjectives.includes(objective)
                                ? "border-indigo-600 bg-indigo-600 text-white shadow-md shadow-indigo-200"
                                : "border-slate-200 bg-white text-slate-600 hover:border-indigo-300 hover:bg-indigo-50",
                            )}
                          >
                            {objective}
                          </button>
                        ))}
                      </div>
                    </div>

                    <div className="mt-6 flex flex-wrap items-center gap-2">
                      <span className="rounded-full border border-indigo-100 bg-indigo-50 px-3 py-1 text-xs font-semibold text-indigo-600">
                        {SOURCE_PAGE_LABELS[sourcePage]}
                      </span>
                      {resolvedTargetPosition ? (
                        <span className="rounded-full border border-slate-200 bg-white px-3 py-1 text-xs font-semibold text-slate-700">
                          {resolvedTargetPosition}
                        </span>
                      ) : null}
                      {portraitSummaryTags.slice(0, 3).map((tag) => (
                        <span key={tag} className="rounded-full border border-slate-200 bg-white px-3 py-1 text-xs font-semibold text-slate-600">
                          {tag}
                        </span>
                      ))}
                    </div>
                  </>
                ) : (
                  <div className="rounded-[1.5rem] border border-dashed border-slate-200 bg-slate-50 px-4 py-5 text-sm leading-7 text-slate-500">
                    还没有选中导师，请先回导师广场选择一位导师，再带着准备单回来完成正式下单。
                  </div>
                )}
              </motion.section>

              <motion.section variants={itemVariants} className="relative overflow-hidden rounded-[2rem] border border-indigo-100/80 bg-gradient-to-br from-indigo-50/60 to-white/60 p-6 shadow-[0_20px_50px_rgba(99,102,241,0.08)] backdrop-blur-xl">
                <div className="pointer-events-none absolute right-0 top-0 h-64 w-64 rounded-full bg-indigo-400/10 blur-[60px]" />

                <div className="relative z-10 mb-6 flex flex-col justify-between gap-4 sm:flex-row sm:items-start">
                  <SectionHeader
                    sectionLabel="Step 2"
                    title="智能准备单草稿"
                    description="继续整理核心问题、预期收获和建议材料。"
                    icon={Sparkles}
                  />
                  <div className="relative z-10 flex gap-2">
                    <button
                      type="button"
                      disabled={isGeneratingPrep || mentorUserId <= 0}
                      onClick={() => void handleGeneratePrepSheet(activeScene, false)}
                      className="flex items-center gap-1.5 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-600 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600 disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      {isGeneratingPrep ? <Loader2 size={14} className="animate-spin" /> : <Zap size={14} />}
                      重新生成
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setPrepSheet(buildPrepDefaults(null));
                        setStructuredData(buildStructuredDefaults(null));
                        setSelectedObjectives([]);
                      }}
                      className="flex items-center gap-1.5 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-rose-500 shadow-sm transition-colors hover:border-rose-200 hover:bg-rose-50"
                    >
                      <Trash2 size={14} />
                      清空重写
                    </button>
                  </div>
                </div>

                {generateError ? (
                  <div className="relative z-10 mb-4 rounded-2xl border border-rose-100 bg-rose-50 px-4 py-3 text-sm text-rose-700">
                    {generateError}
                  </div>
                ) : null}

                <div className="relative z-10 space-y-4">
                  <div className="rounded-2xl border border-indigo-50 bg-white/80 p-5 shadow-sm backdrop-blur-sm">
                    <div className="mb-2 text-sm font-bold text-indigo-500">你的现状与痛点摘要</div>
                    <textarea
                      value={prepSheet.summary}
                      onChange={(event) => setPrepSheet((current) => ({ ...current, summary: event.target.value }))}
                      className="min-h-[70px] w-full resize-none rounded-lg bg-transparent p-2 text-sm leading-relaxed text-slate-700 transition-all focus:outline-none focus:ring-2 focus:ring-indigo-100"
                      placeholder="把你当前最想解决的困扰浓缩成一段摘要。"
                    />
                  </div>

                  <div className="space-y-3">
                    <div className="ml-1 text-sm font-bold text-indigo-500">梳理出的核心问题</div>
                    {prepSheet.coreQuestions.map((question, index) => (
                      <div key={`core-question-${index}`} className="group flex items-start justify-between gap-4 rounded-2xl border border-slate-100 bg-white p-4 shadow-sm transition-colors hover:border-indigo-200 focus-within:border-indigo-300">
                        <div className="flex flex-1 gap-3">
                          <span className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-indigo-100 text-xs font-bold text-indigo-600">
                            {index + 1}
                          </span>
                          <input
                            value={question}
                            onChange={(event) => {
                              const nextQuestions = [...prepSheet.coreQuestions];
                              nextQuestions[index] = event.target.value;
                              setPrepSheet((current) => ({ ...current, coreQuestions: nextQuestions }));
                            }}
                            className="mt-0.5 w-full bg-transparent py-1 text-sm text-slate-700 focus:outline-none"
                            placeholder={`核心问题 ${index + 1}`}
                          />
                        </div>
                        <button
                          type="button"
                          onClick={() => handleImportQuestion(question)}
                          className="mt-0.5 shrink-0 rounded-full bg-indigo-50 px-3 py-1.5 text-xs font-semibold text-indigo-600 opacity-0 transition-all hover:bg-indigo-100 group-hover:opacity-100 focus:opacity-100"
                        >
                          带入提问
                        </button>
                      </div>
                    ))}
                  </div>

                  <div className="grid gap-4 sm:grid-cols-2">
                    <div className="rounded-2xl border border-indigo-50 bg-white/80 p-5 shadow-sm backdrop-blur-sm">
                      <div className="mb-2 text-sm font-bold text-indigo-500">预期收获</div>
                      <textarea
                        value={prepSheet.expectedOutcomesText}
                        onChange={(event) => setPrepSheet((current) => ({ ...current, expectedOutcomesText: event.target.value }))}
                        className="min-h-[70px] w-full resize-none rounded-lg bg-transparent p-2 text-sm leading-relaxed text-slate-700 transition-all focus:outline-none focus:ring-2 focus:ring-indigo-100"
                        placeholder="例如：希望导师指出优先级最高的简历问题，并给出下一步修改建议。"
                      />
                    </div>
                    <div className="rounded-2xl border border-indigo-50 bg-white/80 p-5 shadow-sm backdrop-blur-sm">
                      <div className="mb-2 flex items-center justify-between text-sm font-bold text-indigo-500">
                        <span>建议准备材料</span>
                      </div>
                      <div className="mb-3 flex gap-2">
                        <input
                          value={newSuggestedMaterial}
                          onChange={(event) => setNewSuggestedMaterial(event.target.value)}
                          onKeyDown={(event) => {
                            if (event.key === "Enter") {
                              event.preventDefault();
                              handleAddSuggestedMaterial();
                            }
                          }}
                          className="min-w-0 flex-1 rounded-full border border-slate-200 bg-white px-4 py-2 text-sm text-slate-700 outline-none transition-colors focus:border-indigo-300"
                          placeholder="例如：最新简历、目标岗位 JD、项目复盘材料"
                        />
                        <button
                          type="button"
                          onClick={handleAddSuggestedMaterial}
                          disabled={!newSuggestedMaterial.trim()}
                          className="rounded-full bg-white px-4 py-2 text-[11px] font-semibold text-indigo-600 shadow-sm transition-colors hover:bg-indigo-50 disabled:cursor-not-allowed disabled:text-slate-400"
                        >
                          添加
                        </button>
                      </div>
                      <div className="mt-2 flex flex-wrap gap-2">
                        {prepSheet.suggestedMaterials.length ? prepSheet.suggestedMaterials.map((material) => (
                          <button
                            key={material}
                            type="button"
                            onClick={() => setPrepSheet((current) => ({
                              ...current,
                              suggestedMaterials: current.suggestedMaterials.filter((item) => item !== material),
                            }))}
                            className="inline-flex items-center rounded-full border border-indigo-100 bg-white px-3 py-1.5 text-[11px] font-semibold text-indigo-600 shadow-sm"
                          >
                            <CheckCircle2 size={12} className="mr-1" />
                            {material}
                          </button>
                        )) : (
                          <div className="text-sm text-slate-500">当前还没有准备材料建议。</div>
                        )}
                      </div>
                    </div>
                  </div>
                </div>
              </motion.section>

              <motion.section variants={itemVariants} className="rounded-[2rem] border border-white/80 bg-white/70 p-6 shadow-[0_20px_50px_rgba(148,163,184,0.1)] backdrop-blur-xl">
                <SectionHeader
                  sectionLabel="Step 3"
                  title="结构化问题填写"
                  description="补齐本次问题、背景和预期帮助。"
                  icon={MessageSquare}
                />

                <AnimatePresence>
                  {hasSensitiveInfo ? (
                    <motion.div
                      initial={{ opacity: 0, height: 0, marginBottom: 0 }}
                      animate={{ opacity: 1, height: "auto", marginBottom: 20 }}
                      exit={{ opacity: 0, height: 0, marginBottom: 0 }}
                      className="overflow-hidden"
                    >
                      <div className="flex items-start gap-3 rounded-2xl border border-rose-200 bg-rose-50 p-4">
                        <AlertCircle size={20} className="mt-0.5 shrink-0 text-rose-500" />
                        <div>
                          <h4 className="text-sm font-bold text-rose-800">脱敏提醒</h4>
                          <p className="mt-1 text-sm text-rose-700/90">
                            检测到你的输入可能包含手机号、邮箱或身份证号等敏感信息。建议在提交给导师前做必要脱敏处理。
                          </p>
                        </div>
                      </div>
                    </motion.div>
                  ) : null}
                </AnimatePresence>

                <div className="space-y-5">
                  <div className="space-y-1.5">
                    <label className="flex items-center gap-1 text-sm font-bold text-slate-800">
                      本次最想解决的问题
                      <span className="text-rose-500">*</span>
                    </label>
                    <textarea
                      value={structuredData.problem}
                      onChange={(event) => setStructuredData((current) => ({ ...current, problem: event.target.value }))}
                      placeholder="请详细描述你在求职中遇到的具体卡点。"
                      className="min-h-[120px] w-full rounded-2xl border border-slate-200 bg-white p-4 text-sm text-slate-700 shadow-sm transition-all focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-100"
                    />
                  </div>

                  <div className="grid gap-5 sm:grid-cols-2">
                    <div className="space-y-1.5">
                      <label className="text-sm font-bold text-slate-800">我的背景情况</label>
                      <textarea
                        value={structuredData.background}
                        onChange={(event) => setStructuredData((current) => ({ ...current, background: event.target.value }))}
                        placeholder="例如：当前学历、方向、目标岗位、已有实习或项目情况。"
                        className="min-h-[100px] w-full rounded-2xl border border-slate-200 bg-white p-4 text-sm text-slate-700 shadow-sm transition-all focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-100"
                      />
                    </div>
                    <div className="space-y-1.5">
                      <label className="text-sm font-bold text-slate-800">我已经尝试过什么</label>
                      <textarea
                        value={structuredData.tried}
                        onChange={(event) => setStructuredData((current) => ({ ...current, tried: event.target.value }))}
                        placeholder="例如：已经修改过简历、投递过多少岗位、做过哪些准备。"
                        className="min-h-[100px] w-full rounded-2xl border border-slate-200 bg-white p-4 text-sm text-slate-700 shadow-sm transition-all focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-100"
                      />
                    </div>
                  </div>

                  <div className="space-y-1.5">
                    <label className="text-sm font-bold text-slate-800">我希望导师给出的帮助</label>
                    <textarea
                      value={structuredData.expected}
                      onChange={(event) => setStructuredData((current) => ({ ...current, expected: event.target.value }))}
                      placeholder="例如：希望导师帮我指出最该优先修改的点，并给出一版可执行建议。"
                      className="min-h-[90px] w-full rounded-2xl border border-slate-200 bg-white p-4 text-sm text-slate-700 shadow-sm transition-all focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-100"
                    />
                  </div>

                  <div className="space-y-1.5">
                    <label className="text-sm font-bold text-slate-800">额外补充说明</label>
                    <textarea
                      value={structuredData.extra}
                      onChange={(event) => setStructuredData((current) => ({ ...current, extra: event.target.value }))}
                      placeholder="还有哪些导师提前知道会更高效的信息？"
                      className="min-h-[70px] w-full rounded-2xl border border-slate-200 bg-white p-4 text-sm text-slate-700 shadow-sm transition-all focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-100"
                    />
                  </div>

                  <div className="rounded-[1.4rem] border border-slate-100 bg-slate-50/80 p-4">
                    <div className="flex items-center justify-between gap-3">
                      <div>
                        <div className="text-sm font-bold text-slate-800">订单主问题预览</div>
                        <div className="mt-1 text-xs text-slate-500">
                          系统会将结构化内容整理为订单主问题文本，用于创单与后续消息线程引用。
                        </div>
                      </div>
                      <div className={joinClasses(
                        "rounded-full px-3 py-1 text-xs font-semibold",
                        questionTextTooLong ? "bg-rose-100 text-rose-700" : "bg-slate-200 text-slate-600",
                      )}
                      >
                        {questionTextPreview.length}/2000
                      </div>
                    </div>
                    <pre className="mt-3 whitespace-pre-wrap rounded-2xl bg-white p-4 text-sm leading-7 text-slate-600">
                      {questionTextPreview || "填写上方内容后，这里会自动生成订单主问题摘要。"}
                    </pre>
                  </div>
                </div>
              </motion.section>

              <motion.section variants={itemVariants} className="rounded-[2rem] border border-white/80 bg-white/70 p-6 shadow-[0_20px_50px_rgba(148,163,184,0.1)] backdrop-blur-xl">
                <SectionHeader
                  sectionLabel="Step 4"
                  title="咨询材料包"
                  description="上传简历、JD 和项目资料等初始材料。"
                  icon={FileText}
                />

                <div className="mb-4 flex flex-wrap gap-2">
                  {MATERIAL_TYPE_OPTIONS.map((type) => (
                    <button
                      key={type.code}
                      type="button"
                      onClick={() => setActiveMaterialType(type.code)}
                      className={joinClasses(
                        "rounded-xl border px-4 py-2 text-sm font-medium transition-all",
                        activeMaterialType === type.code
                          ? "border-indigo-600 bg-indigo-600 text-white shadow-md shadow-indigo-200"
                          : "border-slate-200 bg-white text-slate-600 hover:border-indigo-300 hover:bg-indigo-50",
                      )}
                    >
                      {type.label}
                      <span className="ml-2 text-[11px] opacity-80">{type.slotMode === "single" ? "单槽位" : "多槽位"}</span>
                    </button>
                  ))}
                </div>

                <input
                  ref={fileInputRef}
                  type="file"
                  multiple={MATERIAL_TYPE_MAP.get(activeMaterialType)?.slotMode === "multi"}
                  accept=".pdf,.doc,.docx,.png,.jpg,.jpeg"
                  className="hidden"
                  onChange={handleFilesSelected}
                />

                <button
                  type="button"
                  onClick={triggerUpload}
                  className="mt-4 w-full rounded-[1.5rem] border-2 border-dashed border-indigo-200 bg-indigo-50/30 p-8 text-center transition-colors hover:bg-indigo-50/60"
                >
                  <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-indigo-100 text-indigo-500">
                    <UploadCloud size={28} />
                  </div>
                  <div className="text-base font-bold text-slate-700">
                    点击上传 {MATERIAL_TYPE_MAP.get(activeMaterialType)?.label || "材料"}
                  </div>
                  <div className="mt-2 text-sm text-slate-500">
                    支持 PDF、DOC、DOCX、PNG、JPG，单个文件不超过 20MB
                  </div>
                </button>

                {materialFeedback ? (
                  <div className="mt-4 rounded-2xl border border-amber-200/60 bg-amber-50 px-4 py-3 text-sm text-amber-700">
                    {materialFeedback}
                  </div>
                ) : null}

                <div className="mt-8">
                  <div className="mb-4 flex items-center justify-between">
                    <h4 className="text-sm font-bold text-slate-800">当前材料包 ({materials.length})</h4>
                    <span className="text-xs text-slate-500">简历 / 岗位 JD 再次上传会自动替换当前最新版</span>
                  </div>

                  <div className="space-y-4">
                    {materials.length ? materials.map((material) => {
                      const Icon = getMaterialIcon(material.file.name);
                      if (material.status === "uploading") {
                        return (
                          <div key={material.id} className="rounded-[1.2rem] border border-dashed border-indigo-100 bg-indigo-50/50 p-4">
                            <div className="flex items-center justify-between">
                              <div className="flex items-center gap-4">
                                <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-100 text-indigo-600">
                                  <Icon size={20} />
                                </div>
                                <div>
                                  <div className="text-sm font-bold text-slate-700">{material.file.name}</div>
                                  <div className="mt-1 flex items-center gap-2">
                                    <span className="rounded border border-indigo-100 bg-white px-2 py-0.5 text-[10px] font-bold text-indigo-500">
                                      {material.typeLabel}
                                    </span>
                                    <span className="text-xs font-medium text-indigo-400">正在上传到订单材料包...</span>
                                  </div>
                                </div>
                              </div>
                              <Loader2 size={18} className="animate-spin text-indigo-400" />
                            </div>
                          </div>
                        );
                      }

                      if (material.status === "error") {
                        return (
                          <div key={material.id} className="rounded-[1.2rem] border border-rose-200 bg-rose-50/50 p-4">
                            <div className="flex items-start justify-between gap-4">
                              <div className="flex gap-4">
                                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-rose-100 text-rose-600">
                                  <XCircle size={20} />
                                </div>
                                <div>
                                  <div className="text-sm font-bold text-slate-800">{material.file.name}</div>
                                  <div className="mt-1 text-xs font-medium text-rose-500">{material.errorMsg || "上传失败，可在创单后重试"}</div>
                                </div>
                              </div>
                              <button
                                type="button"
                                onClick={() => handleRemoveMaterial(material.id)}
                                className="rounded-full p-1.5 text-slate-400 transition-colors hover:text-rose-600"
                              >
                                <Trash2 size={16} />
                              </button>
                            </div>
                          </div>
                        );
                      }

                      return (
                        <div key={material.id} className="group rounded-[1.2rem] border border-slate-200 bg-white p-4 shadow-sm transition-all hover:border-indigo-300 hover:shadow-md">
                          <div className="flex items-start justify-between">
                            <div className="flex gap-4">
                              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-slate-100 bg-slate-50 text-indigo-600 transition-colors group-hover:bg-indigo-50">
                                <Icon size={20} />
                              </div>
                              <div className="min-w-0">
                                <div className="truncate pr-4 text-sm font-bold text-slate-800">{material.file.name}</div>
                                <div className="mt-1.5 flex flex-wrap items-center gap-2">
                                  <span className={joinClasses(
                                    "rounded border px-2 py-0.5 text-[10px] font-bold",
                                    material.slotMode === "single"
                                      ? "border-emerald-200 bg-emerald-50 text-emerald-700"
                                      : "border-slate-200 bg-slate-50 text-slate-600",
                                  )}
                                  >
                                    {material.typeLabel}
                                  </span>
                                  <span className="text-xs font-medium text-slate-400">{material.sizeLabel}</span>
                                  {material.status === "uploaded" ? (
                                    <span className="text-[10px] font-semibold text-emerald-600">已同步到订单材料包</span>
                                  ) : null}
                                </div>
                              </div>
                            </div>
                            <button
                              type="button"
                              onClick={() => handleRemoveMaterial(material.id)}
                              className="rounded-full p-2 text-slate-400 opacity-0 transition-colors hover:bg-rose-50 hover:text-rose-500 group-hover:opacity-100"
                            >
                              <Trash2 size={18} />
                            </button>
                          </div>

                          <div className="mt-4 pl-14 pr-2">
                            <div className="relative">
                              <MessageSquarePlus className="absolute left-3 top-2.5 text-slate-400" size={14} />
                              <input
                                type="text"
                                value={material.description}
                                onChange={(event) => setMaterials((current) => current.map((item) => item.id === material.id
                                  ? { ...item, description: event.target.value }
                                  : item))}
                                placeholder="添加材料说明，告诉导师这份材料重点看什么"
                                className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2 pl-9 pr-4 text-xs text-slate-700 transition-all placeholder:text-slate-400 focus:border-indigo-400 focus:bg-white focus:outline-none focus:ring-2 focus:ring-indigo-100"
                              />
                            </div>
                          </div>
                        </div>
                      );
                    }) : (
                      <div className="rounded-[1.2rem] border border-dashed border-slate-200 bg-slate-50 px-4 py-5 text-sm text-slate-500">
                        还没有添加材料。你可以先上传简历，后续再补岗位 JD 或项目资料。
                      </div>
                    )}
                  </div>
                </div>

                <div className="mt-6 flex items-start gap-3 rounded-2xl border border-amber-200/60 bg-amber-50 p-4">
                  <AlertCircle size={20} className="mt-0.5 shrink-0 text-amber-500" />
                  <div>
                    <h4 className="text-sm font-bold text-amber-800">材料完整度提醒</h4>
                    <p className="mt-1 text-sm text-amber-700/90">
                      {missingRequiredMaterials.length
                        ? `当前场景更建议你补充：${missingRequiredMaterials.map((code) => MATERIAL_TYPE_MAP.get(code)?.label || code).join("、")}。`
                        : "当前场景要求的关键材料已基本覆盖，后续聊天阶段仍可继续补充或替换。"}
                    </p>
                  </div>
                </div>
              </motion.section>

              {submitError ? (
                <motion.div variants={itemVariants} className="rounded-2xl border border-rose-100 bg-rose-50 px-4 py-4 text-sm leading-7 text-rose-700">
                  {submitError}
                </motion.div>
              ) : null}
            </div>

            <div className="relative z-20 lg:col-span-4">
              <div className="sticky top-[100px] space-y-6">
                <motion.aside variants={itemVariants} className="flex flex-col overflow-hidden rounded-[2.2rem] border border-white/80 bg-white/90 shadow-[0_30px_80px_rgba(15,23,42,0.12)] backdrop-blur-xl">
                  <div className="h-2 w-full bg-gradient-to-r from-indigo-500 via-purple-500 to-amber-400" />

                  <div className="p-6">
                    <h2 className="mb-5 text-lg font-black tracking-tight text-slate-900">订单确认摘要</h2>

                    <div className="border-b border-slate-100 pb-5">
                      {mentor ? (
                        <div className="flex items-center gap-3">
                          <MentorIdentityAvatar
                            userId={mentor.userId}
                            displayName={mentor.displayName}
                            avatarUrl={mentor.avatarUrl}
                            alt={mentor.displayName}
                            className="h-10 w-10 bg-slate-100"
                          />
                          <div>
                            <div className="text-sm font-bold text-slate-800">{mentor.displayName}</div>
                            <div className="text-xs text-slate-500">{buildMentorIdentityLine(mentor, mentor.displayName)}</div>
                          </div>
                        </div>
                      ) : (
                        <div className="text-sm text-slate-500">待选择导师</div>
                      )}
                    </div>

                    <div className="space-y-4 border-b border-slate-100 py-5">
                      <div className="flex items-start justify-between gap-4">
                        <span className="text-sm text-slate-500">已选套餐</span>
                        <span className="max-w-[170px] text-right text-sm font-bold text-slate-800">
                          {selectedPackage?.packageName || "待选择"}
                        </span>
                      </div>
                      <div className="flex items-start justify-between gap-4">
                        <span className="text-sm text-slate-500">咨询场景</span>
                        <span className="text-right text-sm font-bold text-slate-800">{selectedPackage?.sceneLabel || activeScene}</span>
                      </div>
                      <div className="flex items-start justify-between gap-4">
                        <span className="text-sm text-slate-500">服务方式</span>
                        <span className="text-right text-sm font-bold text-slate-800">
                          {selectedPackage ? getPackageDeliveryLabel(selectedPackage.deliveryMode, selectedPackage.durationMinutes) : "待确认"}
                        </span>
                      </div>
                      {selectedPackage?.deliveryMode === "APPOINTMENT" ? (
                        <div className="flex items-start justify-between gap-4">
                          <span className="text-sm text-slate-500">预约时段</span>
                          <span className="max-w-[170px] text-right text-sm font-bold text-slate-800">
                            {selectedScheduleSlot ? formatSlotDateTime(selectedScheduleSlot) : "待选择"}
                          </span>
                        </div>
                      ) : null}
                      <div className="flex items-start justify-between gap-4">
                        <span className="text-sm text-slate-500">下单来源</span>
                        <span className="text-right text-sm font-bold text-slate-800">{SOURCE_PAGE_LABELS[sourcePage]}</span>
                      </div>
                      <div className="flex items-start justify-between gap-4">
                        <span className="text-sm text-slate-500">问题摘要</span>
                        <span className="max-w-[170px] text-right text-sm font-bold text-slate-800">
                          {structuredData.problem.trim() || prepSheet.summary.trim() || "待补充"}
                        </span>
                      </div>
                      <div className="flex items-start justify-between gap-4">
                        <span className="text-sm text-slate-500">咨询目标</span>
                        <span className="text-right text-sm font-bold text-slate-800">
                          {selectedObjectives.length ? `${selectedObjectives.length} 项已选` : "未选择"}
                        </span>
                      </div>
                      <div className="flex items-start justify-between gap-4">
                        <span className="text-sm text-slate-500">目标岗位</span>
                        <span className="max-w-[170px] text-right text-sm font-bold text-slate-800">
                          {resolvedTargetPosition || "待完善"}
                        </span>
                      </div>
                      <div className="flex items-start justify-between gap-4">
                        <span className="text-sm text-slate-500">已附材料</span>
                        <span className="text-right text-sm font-bold text-slate-800">{materials.length} 份文件</span>
                      </div>
                      <div className="flex items-start justify-between gap-4">
                        <span className="text-sm text-slate-500">问题完整度</span>
                        <span className={joinClasses(
                          "flex items-center gap-1 text-sm font-bold",
                          completenessTone === "emerald" ? "text-emerald-600" : completenessTone === "amber" ? "text-amber-600" : "text-rose-600",
                        )}
                        >
                          <CheckCircle2 size={14} />
                          {completenessLabel}
                        </span>
                      </div>
                    </div>

                    <div className="pt-5">
                      <div className="mb-6 flex items-end justify-between">
                        <span className="text-sm font-bold text-slate-800">本次咨询总计</span>
                        <div className="text-right">
                          <div className="text-3xl font-black leading-none text-indigo-600">
                            {selectedPackage ? formatMoneyFen(selectedPackage.priceFen) : mentor ? `${formatMoneyFen(mentor.priceFen)} 起` : "待确认"}
                          </div>
                          <div className="mt-1 text-sm text-slate-400">
                            {selectedPackage ? "按所选套餐结算" : "请选择一个服务套餐"}
                          </div>
                        </div>
                      </div>

                      <button
                        type="button"
                        disabled={!canSubmit}
                        onClick={() => void handleSubmit()}
                        className={joinClasses(
                          "group flex w-full items-center justify-center rounded-2xl px-5 py-4 text-base font-bold text-white transition-all",
                          canSubmit
                            ? "bg-gradient-to-r from-indigo-600 to-indigo-500 shadow-[0_15px_30px_rgba(79,70,229,0.3)] hover:-translate-y-0.5 hover:shadow-[0_20px_40px_rgba(79,70,229,0.4)]"
                            : "cursor-not-allowed bg-slate-300 shadow-none",
                        )}
                      >
                        {submitLoading ? <Loader2 size={18} className="mr-2 animate-spin" /> : null}
                        创建订单并继续
                        <ArrowRight size={18} className="ml-2 opacity-80 transition-transform group-hover:translate-x-1" />
                      </button>

                      {questionTextTooLong ? (
                        <div className="mt-3 text-xs text-rose-600">
                          当前整理出的订单主问题超过 2000 字，请精简结构化内容后再提交。
                        </div>
                      ) : null}
                      {selectedPackage?.deliveryMode === "APPOINTMENT" && !selectedScheduleSlotId ? (
                        <div className="mt-3 text-xs text-rose-600">
                          当前套餐需要先选择预约时段后才能继续创建订单。
                        </div>
                      ) : null}
                    </div>
                  </div>
                </motion.aside>

                <motion.aside variants={itemVariants} className="rounded-[2rem] border border-slate-200/80 bg-slate-50/80 p-5 shadow-sm backdrop-blur-xl">
                  <div className="mb-3 flex items-center gap-2 text-slate-700">
                    <ShieldCheck size={18} className="text-indigo-500" />
                    <h3 className="text-sm font-bold">服务规则与隐私保护</h3>
                  </div>
                  <ul className="space-y-2 text-xs leading-relaxed text-slate-500">
                    <li className="flex items-start gap-2">
                      <div className="mt-2 h-1 w-1 shrink-0 rounded-full bg-slate-300" />
                      创建订单后会自动跳转订单详情，你可以继续支付、查询支付状态、补传材料并跟进导师回复。
                    </li>
                    <li className="flex items-start gap-2">
                      <div className="mt-2 h-1 w-1 shrink-0 rounded-full bg-slate-300" />
                      请勿在提问或材料中包含手机号、身份证号、住址等不必要敏感隐私信息。
                    </li>
                    <li className="flex items-start gap-2">
                      <div className="mt-2 h-1 w-1 shrink-0 rounded-full bg-slate-300" />
                      订单创建后，你仍可在正式咨询消息区继续补充或替换材料。
                    </li>
                    <li className="flex items-start gap-2 font-medium text-indigo-600">
                      <div className="mt-2 h-1 w-1 shrink-0 rounded-full bg-indigo-400" />
                      导师接单后 48 小时内若无回复，系统会自动进入售后退款流程。
                    </li>
                  </ul>
                </motion.aside>
              </div>
            </div>
          </div>
        </motion.div>
      </main>

    </div>
  );
}
