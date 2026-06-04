import { AnimatePresence, motion } from "framer-motion";
import {
  AlertCircle,
  ArrowRight,
  Briefcase,
  ChevronRight,
  Compass,
  FileText,
  Heart,
  MessageSquare,
  RefreshCw,
  Search,
  Sparkles,
  Star,
  Users,
  X,
  Zap,
  CheckCircle2,
  Bot,
} from "lucide-react";
import { startTransition, useEffect, useMemo, useRef, useState, type KeyboardEvent as ReactKeyboardEvent, type MouseEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import MentorIdentityAvatar from "../components/avatar/MentorIdentityAvatar";
import StudentWorkspaceTopbar, { buildStudentWorkspacePrimaryNav } from "../components/student/StudentWorkspaceTopbar";
import { ApiClientError, apiRequest, buildQuery, isAbortError } from "../lib/apiClient";
import { formatDateTime, formatMoneyFen } from "../lib/formatters";
import { buildMentorIdentityLine } from "../lib/mentorNames";
import { buildStudentNickname } from "../lib/studentNames";

type TimeValue = number | string;

type StudentAvatarMeta = {
  uploaded: boolean;
  contentType: string | null;
  updatedAt: TimeValue | null;
};

type StudentProfileSummary = {
  userId: number;
  displayName: string;
  realName: string | null;
  targetPosition: string | null;
  tier: string | null;
  avatar: StudentAvatarMeta | null;
  skillTags: string[];
  selfIntro: string | null;
  portrait: {
    tags: Array<{
      label: string;
    }>;
  } | null;
};

type MentorListResponse = {
  records: MentorCardItem[];
  total: number;
  page: number;
  size: number;
};

type MentorCardItem = {
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
  avgRating: number | null;
  totalOrders: number;
  available: boolean;
  favorited: boolean;
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

type MentorDetailResponse = MentorCardItem & {
  suitableFor: string | null;
  notSuitableFor: string | null;
  prepMaterials: string | null;
  replyRhythm: string | null;
  packages: MentorServicePackage[];
  recentReviews: Array<{
    orderNo: string;
    studentDisplayName: string;
    rating: number;
    comment: string;
    createdAt: string;
  }>;
};

type MentorRecommendationsResponse = {
  scene: string;
  basisSummary: string;
  weakSignal: boolean;
  basisTags: string[];
  records: MentorRecommendationItem[];
};

type MentorRecommendationItem = MentorCardItem & {
  score: number;
  reasons: string[];
  risk: string | null;
  explainText: string;
};

type MentorFavoritesResponse = {
  total: number;
  mentorUserIds: number[];
};

type MentorFavoriteToggleResponse = {
  mentorUserId: number;
  favorited: boolean;
  totalFavorites: number;
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

type PriceFilter = {
  id: string;
  label: string;
  minPrice?: number;
  maxPrice?: number;
};

const SCENES = [
  "不限",
  "简历诊断",
  "项目表达",
  "模拟面试复盘",
  "岗位方向选择",
  "校招投递策略",
  "转行 / 跨专业求职",
  "Offer 对比与决策",
] as const;

const PRICE_FILTERS: PriceFilter[] = [
  { id: "all", label: "价格区间" },
  { id: "under200", label: "¥ 0 - 199", maxPrice: 19900 },
  { id: "200to299", label: "¥ 200 - 299", minPrice: 20000, maxPrice: 29900 },
  { id: "300plus", label: "¥ 300+", minPrice: 30000 },
];

const DEFAULT_PREP_MATERIALS = ["我的最新简历", "目标岗位 JD", "项目介绍"];
const DEFAULT_PREP_OUTCOMES = ["获得综合咨询建议"];
const PREP_STORAGE_PREFIX = "bishe.mentor.prep.";
const PREP_LATEST_STORAGE_PREFIX = "bishe.mentor.prep.latest.";
const MENTOR_PROFILE_SNAPSHOT_STORAGE_KEY_PREFIX = "bishe.student.mentor.market.profile.v1";
const MENTOR_FAVORITES_SNAPSHOT_STORAGE_KEY_PREFIX = "bishe.student.mentor.market.favorites.v1";
const MENTOR_LIST_SNAPSHOT_STORAGE_KEY_PREFIX = "bishe.student.mentor.market.list.v1";
const MENTOR_RECOMMENDATIONS_SNAPSHOT_STORAGE_KEY_PREFIX = "bishe.student.mentor.market.recommendations.v1";
const MENTOR_DETAIL_SNAPSHOT_STORAGE_KEY_PREFIX = "bishe.student.mentor.market.detail.v1";

function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function buildPrepStorageKey(userId: number | null, mentorUserId: number, scene: string) {
  return `${PREP_STORAGE_PREFIX}${userId ?? "guest"}:${mentorUserId}:${scene}`;
}

function buildLatestPrepStorageKey(userId: number | null) {
  return `${PREP_LATEST_STORAGE_PREFIX}${userId ?? "guest"}`;
}

function readPrepDraft(storageKey: string) {
  if (typeof window === "undefined") {
    return null;
  }

  try {
    const raw = window.localStorage.getItem(storageKey);
    if (!raw) {
      return null;
    }
    return JSON.parse(raw) as PrepSheetDraft;
  } catch {
    return null;
  }
}

function writePrepDraft(storageKey: string, draft: PrepSheetDraft) {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.setItem(storageKey, JSON.stringify(draft));
}

function getMentorMarketplaceSnapshotStorage() {
  if (typeof window === "undefined") {
    return null;
  }

  return window.sessionStorage;
}

function readMentorMarketplaceSnapshot<T>(storageKey: string) {
  const storage = getMentorMarketplaceSnapshotStorage();
  if (!storage) {
    return null;
  }

  const rawValue = storage.getItem(storageKey);
  if (!rawValue) {
    return null;
  }

  try {
    return JSON.parse(rawValue) as T;
  } catch {
    storage.removeItem(storageKey);
    return null;
  }
}

function writeMentorMarketplaceSnapshot(storageKey: string, value: unknown) {
  const storage = getMentorMarketplaceSnapshotStorage();
  if (!storage) {
    return;
  }

  storage.setItem(storageKey, JSON.stringify(value));
}

function getMentorProfileSnapshotStorageKey(userId: number) {
  return `${MENTOR_PROFILE_SNAPSHOT_STORAGE_KEY_PREFIX}.${userId}`;
}

function getMentorFavoritesSnapshotStorageKey(userId: number) {
  return `${MENTOR_FAVORITES_SNAPSHOT_STORAGE_KEY_PREFIX}.${userId}`;
}

function buildMentorListSnapshotQueryKey(params: {
  scene: string;
  keyword: string;
  availableOnly: boolean;
  favoritesOnly: boolean;
  priceFilterId: string;
  page: number;
}) {
  return JSON.stringify({
    scene: params.scene,
    keyword: params.keyword.trim(),
    availableOnly: params.availableOnly,
    favoritesOnly: params.favoritesOnly,
    priceFilterId: params.priceFilterId,
    page: params.page,
  });
}

function getMentorListSnapshotStorageKey(userId: number, queryKey: string) {
  return `${MENTOR_LIST_SNAPSHOT_STORAGE_KEY_PREFIX}.${userId}.${encodeURIComponent(queryKey)}`;
}

function buildMentorRecommendationsSnapshotQueryKey(params: {
  scene: string;
  keyword: string;
  availableOnly: boolean;
  favoritesOnly: boolean;
  priceFilterId: string;
}) {
  return JSON.stringify({
    scene: params.scene,
    keyword: params.keyword.trim(),
    availableOnly: params.availableOnly,
    favoritesOnly: params.favoritesOnly,
    priceFilterId: params.priceFilterId,
  });
}

function getMentorRecommendationsSnapshotStorageKey(userId: number, queryKey: string) {
  return `${MENTOR_RECOMMENDATIONS_SNAPSHOT_STORAGE_KEY_PREFIX}.${userId}.${encodeURIComponent(queryKey)}`;
}

function getMentorDetailSnapshotStorageKey(userId: number, mentorUserId: number) {
  return `${MENTOR_DETAIL_SNAPSHOT_STORAGE_KEY_PREFIX}.${userId}.${mentorUserId}`;
}

function mergeMentorRecords(current: MentorCardItem[], next: MentorCardItem[]) {
  const nextById = new Map(next.map((item) => [item.userId, item]));
  const merged = current.map((item) => nextById.get(item.userId) ?? item);
  const existingIds = new Set(current.map((item) => item.userId));
  next.forEach((item) => {
    if (!existingIds.has(item.userId)) {
      merged.push(item);
    }
  });
  return merged;
}

function getPriceFilterById(priceFilterId: string | null) {
  return PRICE_FILTERS.find((item) => item.id === priceFilterId) ?? PRICE_FILTERS[0];
}

function resolveSourceMessage(from: string | null) {
  if (from === "resume") {
    return "建议带着昨天完成的简历诊断报告来找导师，咨询效率更高。";
  }
  if (from === "interview") {
    return "建议带着刚完成的面试复盘问题来找导师，更容易把追问拆清楚。";
  }
  return "建议带着最近一轮求职卡点来找导师，系统会更容易给出匹配结果。";
}

function toMentorRoleText(mentor: Partial<MentorCardItem> | null | undefined) {
  return buildMentorIdentityLine({
    displayName: mentor?.displayName,
    realName: mentor?.realName,
    showRealName: mentor?.showRealName,
    companyName: mentor?.companyName,
    jobTitle: mentor?.jobTitle,
  }, mentor?.displayName);
}

function createDefaultPrepDraft({
  mentorUserId,
  scene,
  targetPosition,
}: {
  mentorUserId: number;
  scene: string;
  targetPosition: string;
}): PrepSheetDraft {
  return {
    mentorUserId,
    scene,
    targetPosition,
    summary: "",
    coreQuestions: ["", "", ""],
    materials: [...DEFAULT_PREP_MATERIALS],
    expectedOutcomes: [...DEFAULT_PREP_OUTCOMES],
    updatedAt: new Date().toISOString(),
  };
}

function FavoriteButton({
  isFavorite,
  onClick,
  className,
  disabled = false,
}: {
  isFavorite: boolean;
  onClick: (event: MouseEvent<HTMLButtonElement>) => void;
  className?: string;
  disabled?: boolean;
}) {
  return (
    <div className="relative group flex items-center justify-center">
      <motion.button
        whileHover={disabled ? undefined : { scale: 1.05 }}
        whileTap={disabled ? undefined : { scale: 0.85 }}
        onClick={onClick}
        disabled={disabled}
        className={joinClasses(
          "relative z-10 flex items-center justify-center rounded-full border p-2 transition-all duration-300",
          disabled && "cursor-not-allowed opacity-60",
          className,
          isFavorite
            ? "border-rose-100 bg-rose-50 text-rose-500 shadow-sm"
            : "border-transparent bg-transparent text-slate-400 hover:bg-slate-100 hover:text-slate-600",
        )}
      >
        <AnimatePresence>
          {isFavorite ? (
            <>
              <motion.div
                initial={{ opacity: 0.8, scale: 0.8 }}
                animate={{ opacity: 0, scale: 2.2 }}
                exit={{ opacity: 0, transition: { duration: 0 } }}
                transition={{ duration: 0.5, ease: "easeOut" }}
                className="pointer-events-none absolute inset-0 rounded-full border-2 border-rose-400"
              />
              {[0, 45, 90, 135, 180, 225, 270, 315].map((angle) => (
                <div
                  key={angle}
                  className="pointer-events-none absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2"
                >
                  <motion.div
                    initial={{ opacity: 1, scale: 0, x: 0, y: 0 }}
                    animate={{
                      opacity: 0,
                      scale: [0, 1.2, 0],
                      x: Math.cos((angle * Math.PI) / 180) * 26,
                      y: Math.sin((angle * Math.PI) / 180) * 26,
                    }}
                    exit={{ opacity: 0, transition: { duration: 0 } }}
                    transition={{ duration: 0.6, ease: "easeOut" }}
                    className="h-1.5 w-1.5 rounded-full bg-rose-400"
                  />
                </div>
              ))}
            </>
          ) : null}
        </AnimatePresence>

        <motion.div
          className="relative z-10"
          animate={isFavorite ? { scale: [1, 1.4, 0.9, 1.1, 1], rotate: [0, -15, 15, -5, 0] } : { scale: 1, rotate: 0 }}
          transition={{ duration: 0.5, type: "spring", bounce: 0.6 }}
        >
          <Heart size={18} fill={isFavorite ? "currentColor" : "none"} strokeWidth={isFavorite ? 2 : 2.5} />
        </motion.div>
      </motion.button>

      <div className="pointer-events-none absolute left-1/2 top-full z-50 mt-2 -translate-x-1/2 scale-95 opacity-0 transition-all duration-200 group-hover:scale-100 group-hover:opacity-100">
          <div className="relative whitespace-nowrap rounded-lg bg-slate-800/95 px-2.5 py-1.5 text-sm font-medium text-white shadow-xl backdrop-blur-sm">
          {isFavorite ? "取消收藏" : "加入收藏"}
          <div className="absolute bottom-full left-1/2 -translate-x-1/2 border-[5px] border-transparent border-b-slate-800/95" />
        </div>
      </div>
    </div>
  );
}

function TopNav({
  displayName,
  studentProfile,
  favoriteCount,
  favoritesOnly,
  onToggleFavoritesOnly,
}: {
  displayName: string | null;
  studentProfile: StudentProfileSummary | null;
  favoriteCount: number;
  favoritesOnly: boolean;
  onToggleFavoritesOnly: () => void;
}) {
  const heroName = buildStudentNickname(studentProfile, displayName, "同学");

  return (
    <StudentWorkspaceTopbar
      sectionLabel="Mentor Marketplace"
      title="导师广场"
      navItems={buildStudentWorkspacePrimaryNav("mentors")}
      rightActions={(
        <button
          type="button"
          onClick={onToggleFavoritesOnly}
          className={joinClasses(
            "relative inline-flex h-10 items-center justify-center rounded-full border px-4 text-sm font-semibold shadow-sm transition-colors",
            favoritesOnly
              ? "border-rose-200 bg-rose-50 text-rose-600"
              : "border-slate-200 bg-white text-slate-600 hover:border-indigo-200 hover:text-indigo-600",
          )}
        >
          <Heart size={18} className="mr-2" />
          <span className="text-sm">我的收藏 ({favoriteCount})</span>
        </button>
      )}
      position="sticky"
      captureKey={`favorites:${favoritesOnly ? "1" : "0"}:${favoriteCount}`}
      userId={studentProfile?.userId}
      displayName={heroName}
      avatar={studentProfile?.avatar}
      tier={studentProfile?.tier}
      userSubtitle={studentProfile?.targetPosition?.trim() || "正在完善求职方向"}
    />
  );
}

function PrepSheetModal({
  isOpen,
  onClose,
  mentor,
  scene,
  studentProfile,
  userId,
  onProceed,
}: {
  isOpen: boolean;
  onClose: () => void;
  mentor: MentorDetailResponse | null;
  scene: string;
  studentProfile: StudentProfileSummary | null;
  userId: number | null;
  onProceed: (draft: PrepSheetDraft) => void;
}) {
  const mentorUserId = mentor?.userId ?? 0;
  const storageKey = useMemo(() => buildPrepStorageKey(userId, mentorUserId, scene), [mentorUserId, scene, userId]);
  const [formData, setFormData] = useState<PrepSheetDraft | null>(null);
  const [isGenerating, setIsGenerating] = useState(false);
  const [generateError, setGenerateError] = useState<string | null>(null);
  const [signalTags, setSignalTags] = useState<string[]>([]);

  useEffect(() => {
    if (!isOpen || !mentor) {
      return;
    }

    const restoredDraft = readPrepDraft(storageKey);
    const nextTargetPosition = studentProfile?.targetPosition?.trim() || "待明确岗位方向";
    const baseDraft = restoredDraft && restoredDraft.mentorUserId === mentor.userId
      ? restoredDraft
      : createDefaultPrepDraft({
        mentorUserId: mentor.userId,
        scene,
        targetPosition: nextTargetPosition,
      });

    if (!restoredDraft) {
      baseDraft.summary = `我目前正在准备「${nextTargetPosition}」方向的求职，想围绕「${scene === "不限" ? "综合咨询" : scene}」做一次更聚焦的咨询。`;
      baseDraft.coreQuestions = [
        "",
        "",
        "",
      ];
      baseDraft.materials = [...DEFAULT_PREP_MATERIALS];
      baseDraft.expectedOutcomes = [...DEFAULT_PREP_OUTCOMES];
    }

    setFormData(baseDraft);
    setSignalTags([]);
    setGenerateError(null);
  }, [isOpen, mentor, scene, storageKey, studentProfile?.targetPosition]);

  useEffect(() => {
    if (!isOpen || !formData) {
      return;
    }
    writePrepDraft(storageKey, {
      ...formData,
      updatedAt: new Date().toISOString(),
    });
  }, [formData, isOpen, storageKey]);

  if (!isOpen || !mentor || !formData) {
    return null;
  }

  const handleGenerate = async () => {
    setIsGenerating(true);
    setGenerateError(null);
    try {
      const response = await apiRequest<MentorPrepSheetGenerateResponse>("/mentors/prep-sheet/generate", {
        method: "POST",
        body: JSON.stringify({
          mentorUserId: mentor.userId,
          scene,
          targetPosition: formData.targetPosition,
        }),
      });

      setFormData({
        mentorUserId: response.mentorUserId,
        scene: response.scene,
        targetPosition: response.targetPosition,
        summary: response.summaryDraft,
        coreQuestions: [0, 1, 2].map((index) => response.coreQuestions[index] ?? ""),
        materials: response.suggestedMaterials.length ? response.suggestedMaterials : [...DEFAULT_PREP_MATERIALS],
        expectedOutcomes: response.expectedOutcomes.length ? response.expectedOutcomes : [...DEFAULT_PREP_OUTCOMES],
        updatedAt: new Date().toISOString(),
      });
      setSignalTags(response.signalTags);
    } catch (error) {
      const apiError = error as ApiClientError;
      setGenerateError(apiError.message || "智能生成失败，请稍后重试。");
    } finally {
      setIsGenerating(false);
    }
  };

  const toggleMaterial = (material: string) => {
    setFormData((current) => {
      if (!current) {
        return current;
      }
      const materials = current.materials.includes(material)
        ? current.materials.filter((item) => item !== material)
        : [...current.materials, material];
      return {
        ...current,
        materials,
      };
    });
  };

  const toggleOutcome = (outcome: string) => {
    setFormData((current) => {
      if (!current) {
        return current;
      }
      const expectedOutcomes = current.expectedOutcomes.includes(outcome)
        ? current.expectedOutcomes.filter((item) => item !== outcome)
        : [...current.expectedOutcomes, outcome];
      return {
        ...current,
        expectedOutcomes: expectedOutcomes.length ? expectedOutcomes : [...DEFAULT_PREP_OUTCOMES],
      };
    });
  };

  const proceedDraft: PrepSheetDraft = {
    ...formData,
    summary: formData.summary.trim(),
    coreQuestions: formData.coreQuestions.map((item) => item.trim()),
    targetPosition: formData.targetPosition.trim(),
    updatedAt: new Date().toISOString(),
  };

  return (
    <AnimatePresence>
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-4 backdrop-blur-sm sm:p-6"
      >
        <motion.div
          initial={{ scale: 0.95, y: 20, opacity: 0 }}
          animate={{ scale: 1, y: 0, opacity: 1 }}
          exit={{ scale: 0.95, y: 20, opacity: 0 }}
          className="relative flex max-h-[92vh] w-full max-w-3xl flex-col overflow-hidden rounded-[2rem] bg-white shadow-[0_35px_100px_rgba(15,23,42,0.3)]"
        >
          <div className="relative border-b border-slate-100 bg-slate-50/80 px-8 py-6">
            <div className="absolute left-0 top-0 h-1 w-full bg-gradient-to-r from-indigo-500 via-purple-500 to-amber-500" />
            <div className="flex items-center justify-between gap-4">
              <div>
                <h2 className="text-2xl font-bold text-slate-900">咨询准备单</h2>
                <p className="mt-1 text-base text-slate-500">整理你的困惑，让 {mentor.displayName} 导师能更高效地帮你。</p>
              </div>
              <button
                type="button"
                onClick={onClose}
                className="rounded-full p-2 text-slate-400 transition-colors hover:bg-slate-200 hover:text-slate-600"
              >
                <X size={20} />
              </button>
            </div>
          </div>

          <div className="flex-1 overflow-y-auto px-8 py-6">
            <div className="mb-6 rounded-[1rem] border border-indigo-100 bg-indigo-50/50 p-4">
              <div className="flex flex-col gap-4 lg:flex-row lg:items-center">
                <div className="flex items-center gap-4">
                  <div className="flex h-12 w-12 items-center justify-center rounded-full bg-white shadow-sm">
                    <Sparkles className="text-indigo-600" size={20} />
                  </div>
                  <div>
                    <div className="text-base font-semibold text-slate-900">AI 一键生成草稿</div>
                    <div className="mt-0.5 text-sm text-slate-500">
                      基于你的目标岗位、当前场景和导师擅长方向，生成更适合沟通的咨询提纲。
                    </div>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={handleGenerate}
                  disabled={isGenerating}
                  className="ml-auto inline-flex items-center justify-center rounded-full bg-indigo-600 px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition-all hover:bg-indigo-700 disabled:opacity-70"
                >
                  {isGenerating ? <RefreshCw className="mr-2 animate-spin" size={16} /> : <Zap className="mr-2" size={16} />}
                  {signalTags.length ? "重新生成" : "智能生成"}
                </button>
              </div>

              {signalTags.length ? (
                <div className="mt-4 flex flex-wrap gap-2">
                  {signalTags.map((tag) => (
                    <span
                      key={tag}
                      className="inline-flex items-center rounded-full border border-indigo-200 bg-white px-3 py-1.5 text-sm font-semibold text-indigo-600"
                    >
                      {tag}
                    </span>
                  ))}
                </div>
              ) : null}

              {generateError ? (
                <div className="mt-4 rounded-2xl border border-rose-100 bg-rose-50 px-4 py-3 text-sm text-rose-700">
                  {generateError}
                </div>
              ) : null}
            </div>

            <div className="space-y-6">
              <div className="grid gap-4 md:grid-cols-2">
                <div className="rounded-2xl border border-slate-100 bg-slate-50 p-4">
                  <div className="text-sm font-bold  text-slate-400">已选导师</div>
                  <div className="mt-3 text-base font-semibold text-slate-900">{mentor.displayName}</div>
                  <div className="mt-1 text-sm text-slate-500">{toMentorRoleText(mentor)}</div>
                </div>
                <div className="rounded-2xl border border-slate-100 bg-slate-50 p-4">
                  <label className="text-sm font-bold  text-slate-400">目标岗位</label>
                  <input
                    type="text"
                    value={formData.targetPosition}
                    onChange={(event) => setFormData((current) => current ? { ...current, targetPosition: event.target.value } : current)}
                    className="mt-3 w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-base text-slate-700 shadow-sm outline-none transition focus:border-indigo-500 focus:ring-2 focus:ring-indigo-200"
                    placeholder="例如：前端开发工程师"
                  />
                </div>
              </div>

              <div>
                <div className="mb-2 block text-base font-bold text-slate-700">1. 当前问题场景</div>
                <div className="inline-flex items-center rounded-full border border-slate-200 bg-slate-50 px-4 py-2 text-base font-semibold text-slate-700">
                  {scene === "不限" ? "综合咨询" : scene}
                </div>
              </div>

              <div>
                <label className="mb-2 block text-base font-bold text-slate-700">2. 当前困扰摘要</label>
                <textarea
                  value={formData.summary}
                  onChange={(event) => setFormData((current) => current ? { ...current, summary: event.target.value } : current)}
                  placeholder="简单描述你目前的现状、卡点和最想解决的问题..."
                  className="min-h-[120px] w-full rounded-xl border border-slate-200 bg-white p-4 text-base text-slate-700 shadow-sm outline-none transition focus:border-indigo-500 focus:ring-2 focus:ring-indigo-200"
                />
              </div>

              <div>
                <label className="mb-2 block text-base font-bold text-slate-700">3. 本次咨询最想解决的 3 个核心问题</label>
                <div className="space-y-3">
                  {[0, 1, 2].map((index) => (
                    <div key={index} className="flex gap-3">
                      <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-slate-100 text-sm font-bold text-slate-500">
                        {index + 1}
                      </span>
                      <input
                        type="text"
                        value={formData.coreQuestions[index] ?? ""}
                        onChange={(event) => setFormData((current) => {
                          if (!current) {
                            return current;
                          }
                          const nextQuestions = [...current.coreQuestions];
                          nextQuestions[index] = event.target.value;
                          return {
                            ...current,
                            coreQuestions: nextQuestions,
                          };
                        })}
                        placeholder={`你想向导师提问的第 ${index + 1} 个问题...`}
                        className="w-full rounded-lg border border-slate-200 px-4 py-2 text-base outline-none transition focus:border-indigo-500 focus:ring-2 focus:ring-indigo-200"
                      />
                    </div>
                  ))}
                </div>
              </div>

              <div>
                <label className="mb-2 block text-base font-bold text-slate-700">4. 已准备材料清单</label>
                <div className="flex flex-wrap gap-2">
                  {["我的最新简历", "目标岗位 JD", "面试复盘记录", "项目介绍", "其他补充材料"].map((material) => {
                    const active = formData.materials.includes(material);
                    return (
                      <button
                        key={material}
                        type="button"
                        onClick={() => toggleMaterial(material)}
                        className={joinClasses(
                          "inline-flex items-center rounded-full border px-3 py-1.5 text-sm font-semibold transition-colors",
                          active
                            ? "border-emerald-200 bg-emerald-50 text-emerald-700"
                            : "border-slate-200 bg-white text-slate-600 hover:bg-slate-50",
                        )}
                      >
                        {active ? <CheckCircle2 size={14} className="mr-1" /> : null}
                        {material}
                      </button>
                    );
                  })}
                </div>
              </div>

              <div>
                <label className="mb-2 block text-base font-bold text-slate-700">5. 希望获得的结果</label>
                <div className="flex flex-wrap gap-2">
                  {["获得简历修改建议", "获得面试复盘建议", "获得求职方向建议", "获得综合咨询建议"].map((outcome) => {
                    const active = formData.expectedOutcomes.includes(outcome);
                    return (
                      <button
                        key={outcome}
                        type="button"
                        onClick={() => toggleOutcome(outcome)}
                        className={joinClasses(
                          "inline-flex items-center rounded-full border px-3 py-1.5 text-sm font-semibold transition-colors",
                          active
                            ? "border-indigo-200 bg-indigo-50 text-indigo-700"
                            : "border-slate-200 bg-white text-slate-600 hover:bg-slate-50",
                        )}
                      >
                        {outcome}
                      </button>
                    );
                  })}
                </div>
              </div>
            </div>
          </div>

          <div className="flex justify-end gap-3 border-t border-slate-100 bg-slate-50 px-8 py-5">
            <button
              type="button"
              onClick={onClose}
              className="rounded-full px-5 py-2.5 text-sm font-semibold text-slate-600 transition-colors hover:bg-slate-200"
            >
              保存草稿并关闭
            </button>
            <button
              type="button"
              onClick={() => onProceed(proceedDraft)}
              className="inline-flex items-center rounded-full bg-slate-900 px-6 py-2.5 text-sm font-semibold text-white shadow-md transition-transform hover:-translate-y-0.5 hover:bg-slate-800"
            >
              带着准备单去咨询
              <ArrowRight size={16} className="ml-2" />
            </button>
          </div>
        </motion.div>
      </motion.div>
    </AnimatePresence>
  );
}

export default function MentorSelectionPage() {
  const { role, userId, displayName } = useAuth();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  const [studentProfile, setStudentProfile] = useState<StudentProfileSummary | null>(null);

  const [scene, setScene] = useState(searchParams.get("scene") || "不限");
  const [keywordInput, setKeywordInput] = useState(searchParams.get("keyword") || "");
  const [appliedKeyword, setAppliedKeyword] = useState(searchParams.get("keyword") || "");
  const [availableOnly, setAvailableOnly] = useState(searchParams.get("available") === "true");
  const [favoritesOnly, setFavoritesOnly] = useState(searchParams.get("favorited") === "true");
  const [priceFilterId, setPriceFilterId] = useState(getPriceFilterById(searchParams.get("price")).id);
  const [page, setPage] = useState(1);
  const [selectedMentorId, setSelectedMentorId] = useState<number | null>(() => {
    const raw = searchParams.get("mentor");
    return raw ? Number(raw) : null;
  });
  const [priceMenuOpen, setPriceMenuOpen] = useState(false);

  const [mentorList, setMentorList] = useState<MentorListResponse | null>(null);
  const [listLoading, setListLoading] = useState(false);
  const [listError, setListError] = useState<string | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const [loadMoreError, setLoadMoreError] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [listRequestToken, setListRequestToken] = useState(0);

  const [recommendations, setRecommendations] = useState<MentorRecommendationsResponse | null>(null);
  const [recommendationsLoading, setRecommendationsLoading] = useState(false);
  const [recommendationsError, setRecommendationsError] = useState<string | null>(null);
  const [recommendationRefreshToken, setRecommendationRefreshToken] = useState(0);

  const [mentorDetail, setMentorDetail] = useState<MentorDetailResponse | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);

  const [favoriteIds, setFavoriteIds] = useState<Set<number>>(new Set());
  const [favoriteCount, setFavoriteCount] = useState(0);
  const [favoriteVersion, setFavoriteVersion] = useState(0);
  const [pendingFavoriteIds, setPendingFavoriteIds] = useState<Set<number>>(new Set());

  const [isPrepModalOpen, setIsPrepModalOpen] = useState(false);
  const loadMoreSentinelRef = useRef<HTMLDivElement | null>(null);
  const loadMoreLockRef = useRef(false);
  const selectionNeedsReconcileRef = useRef(false);
  const previousFilterKeyRef = useRef<string | null>(null);
  const selectedMentorIdRef = useRef<number | null>(selectedMentorId);

  const from = searchParams.get("from");
  const pageSize = 8;
  const currentPriceFilter = getPriceFilterById(priceFilterId);

  const recommendationMap = useMemo(() => {
    const map = new Map<number, MentorRecommendationItem>();
    for (const item of recommendations?.records ?? []) {
      map.set(item.userId, item);
    }
    return map;
  }, [recommendations?.records]);
  const filterKey = useMemo(
    () => JSON.stringify({
      scene,
      keyword: appliedKeyword.trim(),
      availableOnly,
      favoritesOnly,
      priceFilterId,
    }),
    [appliedKeyword, availableOnly, favoritesOnly, priceFilterId, scene],
  );
  const listSnapshotQueryKey = useMemo(
    () => buildMentorListSnapshotQueryKey({
      scene,
      keyword: appliedKeyword,
      availableOnly,
      favoritesOnly,
      priceFilterId,
      page,
    }),
    [appliedKeyword, availableOnly, favoritesOnly, page, priceFilterId, scene],
  );
  const recommendationsSnapshotQueryKey = useMemo(
    () => buildMentorRecommendationsSnapshotQueryKey({
      scene,
      keyword: appliedKeyword,
      availableOnly,
      favoritesOnly,
      priceFilterId,
    }),
    [appliedKeyword, availableOnly, favoritesOnly, priceFilterId, scene],
  );

  const selectedMentor = useMemo(() => {
    if (!selectedMentorId) {
      return mentorDetail;
    }
    if (mentorDetail && mentorDetail.userId === selectedMentorId) {
      return mentorDetail;
    }
    return mentorList?.records.find((item) => item.userId === selectedMentorId)
      ?? recommendations?.records.find((item) => item.userId === selectedMentorId)
      ?? null;
  }, [mentorDetail, mentorList?.records, recommendations?.records, selectedMentorId]);

  const selectedMentorRecentReviews = useMemo(() => {
    if (!selectedMentor || !mentorDetail || selectedMentor.userId !== mentorDetail.userId) {
      return [];
    }
    return mentorDetail.recentReviews;
  }, [mentorDetail, selectedMentor]);
  const selectedMentorServiceContext = useMemo(() => {
    if (!selectedMentor || !mentorDetail || selectedMentor.userId !== mentorDetail.userId) {
      return null;
    }
    return mentorDetail;
  }, [mentorDetail, selectedMentor]);
  const selectedMentorPackages = useMemo(() => {
    if (!selectedMentor || !mentorDetail || selectedMentor.userId !== mentorDetail.userId) {
      return [];
    }
    return mentorDetail.packages;
  }, [mentorDetail, selectedMentor]);

  useEffect(() => {
    selectedMentorIdRef.current = selectedMentorId;
  }, [selectedMentorId]);

  useEffect(() => {
    if (role !== "STUDENT") {
      return;
    }

    const nextParams = new URLSearchParams();
    if (scene !== "不限") {
      nextParams.set("scene", scene);
    }
    if (appliedKeyword.trim()) {
      nextParams.set("keyword", appliedKeyword.trim());
    }
    if (availableOnly) {
      nextParams.set("available", "true");
    }
    if (favoritesOnly) {
      nextParams.set("favorited", "true");
    }
    if (priceFilterId !== "all") {
      nextParams.set("price", priceFilterId);
    }
    if (selectedMentorId) {
      nextParams.set("mentor", String(selectedMentorId));
    }
    if (from) {
      nextParams.set("from", from);
    }

    startTransition(() => {
      setSearchParams(nextParams, { replace: true });
    });
  }, [
    appliedKeyword,
    availableOnly,
    favoritesOnly,
    from,
    priceFilterId,
    scene,
    selectedMentorId,
    setSearchParams,
    role,
  ]);

  useEffect(() => {
    if (role !== "STUDENT") {
      previousFilterKeyRef.current = null;
      selectionNeedsReconcileRef.current = false;
      setStudentProfile(null);
      return;
    }

    let active = true;
    const cachedProfile = typeof userId === "number"
      ? readMentorMarketplaceSnapshot<StudentProfileSummary>(getMentorProfileSnapshotStorageKey(userId))
      : null;

    // 导师广场先回显 sessionStorage 快照，再静默刷新学生画像用于推荐解释。
    setStudentProfile(cachedProfile);

    void apiRequest<StudentProfileSummary>("/profiles/students/me")
      .then((response) => {
        if (!active) {
          return;
        }
        setStudentProfile(response);
        const snapshotUserId = typeof userId === "number" ? userId : response.userId;
        writeMentorMarketplaceSnapshot(getMentorProfileSnapshotStorageKey(snapshotUserId), response);
      })
      .catch(() => {
        if (active && !cachedProfile) {
          setStudentProfile(null);
        }
      });

    return () => {
      active = false;
    };
  }, [role, userId]);

  useEffect(() => {
    if (role !== "STUDENT") {
      previousFilterKeyRef.current = null;
      selectionNeedsReconcileRef.current = false;
      return;
    }

    if (previousFilterKeyRef.current === null) {
      previousFilterKeyRef.current = filterKey;
      return;
    }

    if (previousFilterKeyRef.current !== filterKey) {
      previousFilterKeyRef.current = filterKey;
      // 筛选条件变化后等列表返回，再重新选择推荐或列表第一项。
      selectionNeedsReconcileRef.current = true;
    }
  }, [filterKey, role]);

  useEffect(() => {
    if (role !== "STUDENT") {
      setFavoriteIds(new Set());
      setFavoriteCount(0);
      return;
    }

    let active = true;
    const cachedFavorites = typeof userId === "number"
      ? readMentorMarketplaceSnapshot<MentorFavoritesResponse>(getMentorFavoritesSnapshotStorageKey(userId))
      : null;

    setFavoriteIds(new Set(cachedFavorites?.mentorUserIds ?? []));
    setFavoriteCount(cachedFavorites?.total ?? 0);

    void apiRequest<MentorFavoritesResponse>("/mentors/favorites")
      .then((response) => {
        if (!active) {
          return;
        }
        setFavoriteIds(new Set(response.mentorUserIds));
        setFavoriteCount(response.total);
        if (typeof userId === "number") {
          writeMentorMarketplaceSnapshot(getMentorFavoritesSnapshotStorageKey(userId), response);
        }
      })
      .catch(() => {
        if (active && !cachedFavorites) {
          setFavoriteIds(new Set());
          setFavoriteCount(0);
        }
      });

    return () => {
      active = false;
    };
  }, [favoriteVersion, role, userId]);

  useEffect(() => {
    if (role !== "STUDENT") {
      setRecommendations(null);
      setRecommendationsError(null);
      setRecommendationsLoading(false);
      return;
    }

    let active = true;
    const cachedRecommendations = typeof userId === "number"
      ? readMentorMarketplaceSnapshot<MentorRecommendationsResponse>(
        getMentorRecommendationsSnapshotStorageKey(userId, recommendationsSnapshotQueryKey),
      )
      : null;

    // AI 推荐区独立加载，失败时不影响主导师列表继续可用。
    setRecommendations(cachedRecommendations);
    setRecommendationsError(null);
    setRecommendationsLoading(true);

    void apiRequest<MentorRecommendationsResponse>(`/mentors/recommendations${buildQuery({
      scene: scene !== "不限" ? scene : undefined,
      keyword: appliedKeyword.trim() || undefined,
      minPrice: currentPriceFilter.minPrice,
      maxPrice: currentPriceFilter.maxPrice,
      available: availableOnly ? true : undefined,
      favorited: favoritesOnly ? true : undefined,
      refreshToken: recommendationRefreshToken || undefined,
    })}`)
      .then((response) => {
        if (!active) {
          return;
        }
        setRecommendations(response);
        setRecommendationsError(null);
        if (typeof userId === "number") {
          writeMentorMarketplaceSnapshot(
            getMentorRecommendationsSnapshotStorageKey(userId, recommendationsSnapshotQueryKey),
            response,
          );
        }
      })
      .catch((error) => {
        const apiError = error as ApiClientError;
        if (active) {
          if (!cachedRecommendations) {
            setRecommendations(null);
            setRecommendationsError(apiError.message || "AI 推荐暂时生成失败");
          }
        }
      })
      .finally(() => {
        if (active) {
          setRecommendationsLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [
    appliedKeyword,
    availableOnly,
    currentPriceFilter.maxPrice,
    currentPriceFilter.minPrice,
    favoritesOnly,
    recommendationRefreshToken,
    recommendationsSnapshotQueryKey,
    role,
    scene,
    favoriteVersion,
    userId,
  ]);

  useEffect(() => {
    if (role !== "STUDENT") {
      setMentorList(null);
      setListError(null);
      setListLoading(false);
      setLoadingMore(false);
      setLoadMoreError(null);
      setHasMore(false);
      return;
    }

    const controller = new AbortController();
    const isLoadMore = page > 1;
    const cachedMentorList = typeof userId === "number"
      ? readMentorMarketplaceSnapshot<MentorListResponse>(
        getMentorListSnapshotStorageKey(userId, listSnapshotQueryKey),
      )
      : null;

    // 列表也先读筛选维度快照，随后用 AbortController 防止旧请求覆盖新筛选。
    if (cachedMentorList) {
      setMentorList(cachedMentorList);
      setHasMore(cachedMentorList.page * cachedMentorList.size < cachedMentorList.total);
      setListError(null);
    } else if (!isLoadMore) {
      setMentorList(null);
      setHasMore(false);
    }

    if (isLoadMore) {
      setLoadingMore(true);
      setLoadMoreError(null);
    } else {
      setListLoading(true);
      if (!cachedMentorList) {
        setListError(null);
      }
      setLoadMoreError(null);
    }

    void apiRequest<MentorListResponse>(`/mentors${buildQuery({
      page,
      size: pageSize,
      scene: scene !== "不限" ? scene : undefined,
      keyword: appliedKeyword.trim() || undefined,
      minPrice: currentPriceFilter.minPrice,
      maxPrice: currentPriceFilter.maxPrice,
      available: availableOnly ? true : undefined,
      favorited: favoritesOnly ? true : undefined,
    })}`, { signal: controller.signal })
      .then((response) => {
        if (controller.signal.aborted) {
          return;
        }
        let nextMentorList: MentorListResponse = response;
        setMentorList((current) => {
          nextMentorList = {
            ...response,
            records: page === 1 ? response.records : mergeMentorRecords(current?.records ?? cachedMentorList?.records ?? [], response.records),
          };
          return nextMentorList;
        });
        setHasMore(nextMentorList.page * nextMentorList.size < nextMentorList.total);
        setListError(null);
        if (typeof userId === "number") {
          writeMentorMarketplaceSnapshot(
            getMentorListSnapshotStorageKey(userId, listSnapshotQueryKey),
            nextMentorList,
          );
        }

        if (page === 1 && !nextMentorList.records.length) {
          selectionNeedsReconcileRef.current = false;
          setSelectedMentorId(null);
          setMentorDetail(null);
          return;
        }

        const currentSelectedMentorId = selectedMentorIdRef.current;
        const hasSelectedMentor = currentSelectedMentorId !== null
          && nextMentorList.records.some((item) => item.userId === currentSelectedMentorId);
        const shouldPickFallback = page === 1
          && (currentSelectedMentorId === null || (selectionNeedsReconcileRef.current && !hasSelectedMentor));
        if (page === 1) {
          selectionNeedsReconcileRef.current = false;
        }

        if (shouldPickFallback) {
          // 当前选中导师不在新结果中时，优先选推荐命中的导师，否则选列表第一项。
          const recommendedMentorId = recommendations?.records[0]?.userId;
          const fallbackMentor = nextMentorList.records.find((item) => item.userId === recommendedMentorId) ?? nextMentorList.records[0];
          if (fallbackMentor && fallbackMentor.userId !== currentSelectedMentorId) {
            setSelectedMentorId(fallbackMentor.userId);
          }
        }
      })
      .catch((error) => {
        if (isAbortError(error) || controller.signal.aborted) {
          return;
        }
        const apiError = error as ApiClientError;
        const message = apiError.message || "加载导师列表失败";
        if (cachedMentorList) {
          setMentorList(cachedMentorList);
          setHasMore(cachedMentorList.page * cachedMentorList.size < cachedMentorList.total);
          if (!isLoadMore) {
            setListError(null);
          }
          return;
        }
        if (page === 1) {
          setMentorList(null);
          setListError(message);
          setHasMore(false);
          return;
        }
        setLoadMoreError(message);
      })
      .finally(() => {
        loadMoreLockRef.current = false;
        if (controller.signal.aborted) {
          return;
        }
        setListLoading(false);
        setLoadingMore(false);
      });

    return () => {
      controller.abort();
    };
  }, [
    appliedKeyword,
    availableOnly,
    currentPriceFilter.maxPrice,
    currentPriceFilter.minPrice,
    favoritesOnly,
    page,
    role,
    scene,
    favoriteVersion,
    listRequestToken,
    listSnapshotQueryKey,
    recommendations?.records,
    userId,
  ]);

  useEffect(() => {
    if (role !== "STUDENT" || typeof IntersectionObserver === "undefined") {
      return undefined;
    }

    const sentinel = loadMoreSentinelRef.current;
    if (!sentinel || !hasMore) {
      return undefined;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        const entry = entries[0];
        if (!entry?.isIntersecting || listLoading || loadingMore || loadMoreLockRef.current || loadMoreError) {
          return;
        }

        loadMoreLockRef.current = true;
        const nextPage = (mentorList?.page ?? 1) + 1;
        if (nextPage === page) {
          setListRequestToken((current) => current + 1);
          return;
        }
        setPage(nextPage);
      },
      {
        rootMargin: "0px 0px 320px 0px",
        threshold: 0.08,
      },
    );

    observer.observe(sentinel);
    return () => {
      observer.disconnect();
    };
  }, [hasMore, listLoading, loadingMore, loadMoreError, mentorList?.page, page, role]);

  useEffect(() => {
    if (role !== "STUDENT" || !selectedMentorId) {
      setMentorDetail(null);
      setDetailError(null);
      setDetailLoading(false);
      return;
    }

    let active = true;
    const cachedMentorDetail = typeof userId === "number"
      ? readMentorMarketplaceSnapshot<MentorDetailResponse>(
        getMentorDetailSnapshotStorageKey(userId, selectedMentorId),
      )
      : null;

    if (cachedMentorDetail) {
      setMentorDetail(cachedMentorDetail);
      setDetailError(null);
    }
    setDetailLoading(true);
    if (!cachedMentorDetail) {
      setDetailError(null);
    }

    void apiRequest<MentorDetailResponse>(`/mentors/${selectedMentorId}`)
      .then((response) => {
        if (!active) {
          return;
        }
        setMentorDetail(response);
        if (typeof userId === "number") {
          writeMentorMarketplaceSnapshot(getMentorDetailSnapshotStorageKey(userId, selectedMentorId), response);
        }
      })
      .catch((error) => {
        const apiError = error as ApiClientError;
        if (active) {
          if (!cachedMentorDetail) {
            setMentorDetail(null);
            setDetailError(apiError.message || "加载导师详情失败");
          }
        }
      })
      .finally(() => {
        if (active) {
          setDetailLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [role, selectedMentorId, favoriteVersion, userId]);

  const heroName = buildStudentNickname(studentProfile, displayName, "同学");
  const sourceMessage = resolveSourceMessage(from);
  const loadedMentorCount = mentorList?.records.length ?? 0;

  const handleApplySearch = () => {
    setPage(1);
    setAppliedKeyword(keywordInput);
  };

  const handleLoadMore = () => {
    if (listLoading || loadingMore || !hasMore || loadMoreLockRef.current) {
      return;
    }

    loadMoreLockRef.current = true;
    setLoadMoreError(null);
    const nextPage = (mentorList?.page ?? 1) + 1;
    if (nextPage === page) {
      setListRequestToken((current) => current + 1);
      return;
    }
    setPage(nextPage);
  };

  const handleMentorCardKeyDown = (event: ReactKeyboardEvent<HTMLElement>, mentorUserId: number) => {
    if (event.key !== "Enter" && event.key !== " ") {
      return;
    }

    event.preventDefault();
    setSelectedMentorId(mentorUserId);
  };

  const handleToggleFavorite = async (event: MouseEvent<HTMLButtonElement>, mentorUserId: number) => {
    event.stopPropagation();
    if (pendingFavoriteIds.has(mentorUserId)) {
      return;
    }

    const nextFavoriteIds = new Set(favoriteIds);
    const currentlyFavorited = nextFavoriteIds.has(mentorUserId);
    if (currentlyFavorited) {
      nextFavoriteIds.delete(mentorUserId);
    } else {
      nextFavoriteIds.add(mentorUserId);
    }

    // 收藏先乐观更新，失败后按原状态回滚。
    setFavoriteIds(nextFavoriteIds);
    setFavoriteCount((current) => Math.max(0, current + (currentlyFavorited ? -1 : 1)));
    setPendingFavoriteIds((current) => new Set(current).add(mentorUserId));

    try {
      const response = currentlyFavorited
        ? await apiRequest<MentorFavoriteToggleResponse>(`/mentors/${mentorUserId}/favorite`, { method: "DELETE" })
        : await apiRequest<MentorFavoriteToggleResponse>(`/mentors/${mentorUserId}/favorite`, { method: "POST" });

      setFavoriteCount(response.totalFavorites);
      setFavoriteIds((current) => {
        const updated = new Set(current);
        if (response.favorited) {
          updated.add(mentorUserId);
        } else {
          updated.delete(mentorUserId);
        }
        return updated;
      });
      setFavoriteVersion((current) => current + 1);

      if (favoritesOnly && currentlyFavorited) {
        setPage(1);
      }
    } catch (error) {
      const apiError = error as ApiClientError;
      setFavoriteIds((current) => {
        const reverted = new Set(current);
        if (currentlyFavorited) {
          reverted.add(mentorUserId);
        } else {
          reverted.delete(mentorUserId);
        }
        return reverted;
      });
      setFavoriteCount((current) => Math.max(0, current + (currentlyFavorited ? 1 : -1)));
      setListError(apiError.message || "更新收藏失败");
    } finally {
      setPendingFavoriteIds((current) => {
        const updated = new Set(current);
        updated.delete(mentorUserId);
        return updated;
      });
    }
  };

  const handleGoConsultWithPrep = (draft: PrepSheetDraft) => {
    // 准备单写入导师专属 key 和 latest key，咨询创建页用它承接上下文。
    writePrepDraft(buildPrepStorageKey(userId, draft.mentorUserId, draft.scene), draft);
    writePrepDraft(buildLatestPrepStorageKey(userId), draft);
    setIsPrepModalOpen(false);
    const orderCreateSource = favoritesOnly
      ? "MENTOR_MARKETPLACE_FAVORITES"
      : recommendationMap.has(draft.mentorUserId)
        ? "MENTOR_MARKETPLACE_RECOMMENDATION"
        : "MENTOR_MARKETPLACE";
    navigate(`/consult/create${buildQuery({
      mentorUserId: draft.mentorUserId,
      scene: draft.scene,
      source: orderCreateSource,
    })}`);
  };

  return (
    <div className="relative min-h-screen bg-[#eef3ff] pb-20 text-slate-900">
      <div className="pointer-events-none fixed inset-0 z-0 bg-[radial-gradient(circle_at_top,rgba(79,70,229,0.18),transparent_30%),radial-gradient(circle_at_bottom_right,rgba(20,184,166,0.12),transparent_28%),linear-gradient(180deg,#eff6ff_0%,#f8fafc_58%,#f8fafc_100%)]" />
      <div className="page-top-glow page-top-glow--indigo-soft" />

      <TopNav
        displayName={displayName}
        studentProfile={studentProfile}
        favoriteCount={favoriteCount}
        favoritesOnly={favoritesOnly}
        onToggleFavoritesOnly={() => {
          setFavoritesOnly((current) => !current);
          setPage(1);
        }}
      />

      <main className="relative z-10 mx-auto w-full max-w-[96rem] px-4 pb-8 pt-10 sm:px-6 lg:px-8">
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.35 }}
          className="space-y-8"
        >
          <section className="flex flex-col justify-between gap-4 md:flex-row md:items-end">
            <div>
              <h1 className="text-4xl font-black tracking-tight text-slate-950 lg:text-[2.8rem]">导师广场</h1>
              <p className="mt-2 text-lg text-slate-600">按问题、方向和 AI 推荐理由，找到最适合帮你破局的前辈。</p>
            </div>

            <div className="flex max-w-sm items-center gap-3 rounded-2xl border border-amber-200/60 bg-amber-50/80 px-4 py-3 shadow-sm backdrop-blur-sm">
              <div className="flex h-8 w-8 items-center justify-center rounded-full bg-amber-100 text-amber-600">
                <FileText size={16} />
              </div>
              <div className="text-base font-medium leading-tight text-amber-800">{sourceMessage}</div>
            </div>
          </section>

          <section className="relative overflow-hidden rounded-[2rem] border border-white/80 bg-white/60 p-6 shadow-[0_20px_50px_rgba(148,163,184,0.12)] backdrop-blur-xl">
            <div className="absolute inset-x-0 top-0 h-32 bg-gradient-to-r from-indigo-50/80 via-purple-50/80 to-transparent opacity-90" />

            <div className="relative z-10 mb-6 flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
              <div>
                <h2 className="flex items-center text-2xl font-bold text-slate-900">
                  <Sparkles className="mr-2 text-indigo-500" size={20} />
                  AI 为你推荐
                </h2>
                <p className="mt-1 text-base leading-7 text-slate-500">
                  {recommendations?.basisSummary || "基于你的目标岗位、技能标签和当前问题场景，为你计算更适合优先了解的导师。"}
                </p>
              </div>
              <button
                type="button"
                onClick={() => setRecommendationRefreshToken((current) => current + 1)}
                className="inline-flex items-center rounded-full border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-600 shadow-sm transition-colors hover:bg-slate-50"
              >
                <RefreshCw size={14} className="mr-2" />
                重新计算
              </button>
            </div>

            {recommendations?.basisTags?.length ? (
              <div className="relative z-10 mb-5 flex flex-wrap gap-2">
                {recommendations.basisTags.map((tag) => (
                  <span
                    key={tag}
                    className={joinClasses(
                      "inline-flex items-center rounded-full border px-3 py-1.5 text-sm font-semibold",
                      recommendations.weakSignal
                        ? "border-amber-200 bg-amber-50 text-amber-700"
                        : "border-indigo-200 bg-white/90 text-indigo-600",
                    )}
                  >
                    {tag}
                  </span>
                ))}
              </div>
            ) : null}

            {recommendationsLoading && !recommendations ? (
              <div className="relative z-10 grid gap-4 md:grid-cols-3">
                {Array.from({ length: 3 }).map((_, index) => (
                  <div key={index} className="h-[196px] rounded-[1.5rem] border border-white bg-white/80 shadow-sm" />
                ))}
              </div>
            ) : recommendationsError ? (
              <div className="relative z-10 rounded-[1.5rem] border border-amber-100 bg-amber-50 px-4 py-4 text-sm leading-7 text-amber-800">
                {recommendationsError}
              </div>
            ) : recommendations?.records.length ? (
              <div className="relative z-10 grid gap-4 md:grid-cols-3">
                {recommendations.records.map((item) => {
                  const isSelected = selectedMentorId === item.userId;
                  const isFavorite = favoriteIds.has(item.userId);
                  return (
                    <div
                      key={item.userId}
                      role="button"
                      tabIndex={0}
                      onClick={() => setSelectedMentorId(item.userId)}
                      onKeyDown={(event) => handleMentorCardKeyDown(event, item.userId)}
                      className={joinClasses(
                        "group relative cursor-pointer overflow-hidden rounded-[1.5rem] border p-5 text-left transition-all duration-300 hover:-translate-y-1 hover:shadow-xl focus:outline-none focus:ring-2 focus:ring-indigo-300 focus:ring-offset-2",
                        isSelected ? "border-indigo-400 bg-indigo-50/50 shadow-md ring-1 ring-indigo-400" : "border-white bg-white/80 shadow-sm",
                      )}
                      aria-pressed={isSelected}
                    >
                      <div className="flex items-start justify-between">
                        <div className="flex gap-3">
                          <MentorIdentityAvatar
                            userId={item.userId}
                            displayName={item.displayName}
                            avatarUrl={item.avatarUrl}
                            alt={item.displayName}
                            className="h-12 w-12 shadow-sm"
                            fallbackClassName="bg-gradient-to-br from-indigo-100 to-indigo-50 text-indigo-700"
                          />
                          <div className="min-w-0">
                            <div className="font-bold text-slate-900">{item.displayName}</div>
                            <div className="truncate text-sm text-slate-500">{toMentorRoleText(item)}</div>
                          </div>
                        </div>
                        <div className="flex flex-col items-end gap-2">
                          <FavoriteButton
                            isFavorite={isFavorite}
                            onClick={(event) => handleToggleFavorite(event, item.userId)}
                            disabled={pendingFavoriteIds.has(item.userId)}
                            className="bg-white/90"
                          />
                          <div className="text-right">
                            <div className="text-2xl font-black tracking-tighter text-indigo-600">{item.score}</div>
                            <div className="text-xs font-bold  text-indigo-400">匹配分</div>
                          </div>
                        </div>
                      </div>

                      <div className="mt-4 space-y-2">
                        {item.reasons.map((reason) => (
                          <div key={reason} className="flex items-start text-sm font-medium leading-6 text-slate-600">
                            <CheckCircle2 size={14} className="mr-1.5 mt-0.5 shrink-0 text-emerald-500" />
                            {reason}
                          </div>
                        ))}
                        {item.risk ? (
                          <div className="flex items-start pt-1 text-sm font-medium leading-6 text-amber-600">
                            <AlertCircle size={14} className="mr-1.5 mt-0.5 shrink-0" />
                            {item.risk}
                          </div>
                        ) : null}
                      </div>
                    </div>
                  );
                })}
              </div>
            ) : (
              <div className="relative z-10 rounded-[1.5rem] border border-dashed border-slate-200 bg-white/80 px-5 py-5 text-sm leading-7 text-slate-500">
                当前筛选条件下还没有合适的推荐结果，可以先放宽价格条件或切换到更通用的问题场景。
              </div>
            )}
          </section>

          <div className="grid gap-8 lg:grid-cols-[1.5fr_1fr]">
            <motion.div
              initial={{ opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.05, duration: 0.35 }}
              className="space-y-6"
            >
              <div className="rounded-[1.5rem] border border-white/70 bg-white/84 p-2 shadow-sm backdrop-blur-xl">
                <div className="flex flex-wrap gap-1">
                  {SCENES.map((sceneOption) => (
                    <button
                      key={sceneOption}
                      type="button"
                      onClick={() => {
                        setScene(sceneOption);
                        setPage(1);
                      }}
                      className={joinClasses(
                        "rounded-xl px-4 py-2.5 text-base font-semibold transition-all",
                        scene === sceneOption ? "bg-slate-900 text-white shadow-md" : "text-slate-600 hover:bg-slate-100",
                      )}
                    >
                      {sceneOption}
                    </button>
                  ))}
                </div>

                <div className="mt-3 flex flex-col gap-3 border-t border-slate-100 px-2 pb-1 pt-3 xl:flex-row xl:items-center xl:justify-between">
                  <div className="relative max-w-xl flex-1">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={16} />
                    <input
                      type="text"
                      value={keywordInput}
                      onChange={(event) => setKeywordInput(event.target.value)}
                      onKeyDown={(event) => {
                        if (event.key === "Enter") {
                          handleApplySearch();
                        }
                      }}
                      placeholder="搜索导师姓名、公司或领域..."
                      className="w-full rounded-full border-none bg-slate-100 py-2.5 pl-9 pr-4 text-base outline-none focus:ring-2 focus:ring-indigo-500"
                    />
                  </div>

                  <div className="flex flex-wrap items-center gap-2">
                    <button
                      type="button"
                      onClick={handleApplySearch}
                      className="inline-flex items-center rounded-full bg-slate-900 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-slate-800"
                    >
                      搜索
                    </button>

                    <div className="relative">
                      <button
                        type="button"
                        onClick={() => setPriceMenuOpen((current) => !current)}
                        className="inline-flex items-center rounded-full border border-slate-200 bg-white px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-50"
                      >
                        {currentPriceFilter.label}
                        <ChevronRight size={14} className={joinClasses("ml-1 opacity-50 transition-transform", priceMenuOpen && "rotate-90")} />
                      </button>
                      {priceMenuOpen ? (
                        <div className="absolute right-0 top-full z-30 mt-2 min-w-[12rem] rounded-2xl border border-slate-200 bg-white p-2 shadow-[0_20px_40px_rgba(15,23,42,0.08)]">
                          {PRICE_FILTERS.map((item) => (
                            <button
                              key={item.id}
                              type="button"
                              onClick={() => {
                                setPriceFilterId(item.id);
                                setPriceMenuOpen(false);
                                setPage(1);
                              }}
                              className={joinClasses(
                                "flex w-full items-center rounded-xl px-3 py-2 text-left text-sm transition-colors",
                                priceFilterId === item.id ? "bg-slate-900 text-white" : "text-slate-600 hover:bg-slate-100",
                              )}
                            >
                              {item.label}
                            </button>
                          ))}
                        </div>
                      ) : null}
                    </div>

                    <button
                      type="button"
                      onClick={() => {
                        setAvailableOnly((current) => !current);
                        setPage(1);
                      }}
                      className={joinClasses(
                        "inline-flex items-center rounded-full border px-3 py-2 text-sm font-medium",
                        availableOnly ? "border-emerald-200 bg-emerald-50 text-emerald-700" : "border-slate-200 bg-white text-slate-600 hover:bg-slate-50",
                      )}
                    >
                      仅看可接单
                    </button>

                    <button
                      type="button"
                      onClick={() => {
                        setFavoritesOnly((current) => !current);
                        setPage(1);
                      }}
                      className={joinClasses(
                        "inline-flex items-center rounded-full border px-3 py-2 text-sm font-medium",
                        favoritesOnly ? "border-rose-200 bg-rose-50 text-rose-600" : "border-slate-200 bg-white text-slate-600 hover:bg-slate-50",
                      )}
                    >
                      仅看收藏
                    </button>

                    <button
                      type="button"
                      onClick={() => {
                        setScene("不限");
                        setKeywordInput("");
                        setAppliedKeyword("");
                        setAvailableOnly(false);
                        setFavoritesOnly(false);
                        setPriceFilterId("all");
                        setPriceMenuOpen(false);
                        setPage(1);
                      }}
                      className="inline-flex items-center rounded-full border border-slate-200 bg-white px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-50"
                    >
                      重置筛选
                    </button>
                  </div>
                </div>
              </div>

              <div className="flex items-center justify-between">
                <div className="text-base text-slate-500">
                  {listLoading ? "正在加载导师..." : mentorList?.total ? `已加载 ${loadedMentorCount} / ${mentorList.total} 位导师` : `共 ${mentorList?.total ?? 0} 位导师`}
                </div>
                <div className="flex items-center gap-2">
                  <Link
                    to="/ai/resume"
                    className="inline-flex items-center rounded-full border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-600 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600"
                  >
                    返回 AI 简历页
                  </Link>
                  <button
                    type="button"
                    onClick={() => {
                      setPage(1);
                      setLoadMoreError(null);
                      setListRequestToken((current) => current + 1);
                      setRecommendationRefreshToken((current) => current + 1);
                    }}
                    className="inline-flex items-center rounded-full border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-600 shadow-sm transition-colors hover:border-slate-300 hover:text-slate-800"
                  >
                    <RefreshCw size={13} className={joinClasses("mr-1.5", (listLoading || loadingMore || recommendationsLoading) && "animate-spin")} />
                    刷新
                  </button>
                </div>
              </div>

              <div className="space-y-4">
                {listLoading && !mentorList ? (
                  Array.from({ length: 5 }).map((_, index) => (
                    <div key={index} className="h-[182px] rounded-[1.8rem] border border-white/80 bg-white/70 backdrop-blur-sm" />
                  ))
                ) : listError ? (
                  <div className="rounded-[1.8rem] border border-rose-100 bg-rose-50 px-4 py-4 text-sm leading-7 text-rose-700">
                    {listError}
                  </div>
                ) : mentorList?.records.length ? (
                  mentorList.records.map((mentor) => {
                    const recommendation = recommendationMap.get(mentor.userId);
                    const isSelected = selectedMentorId === mentor.userId;
                    const isFavorite = favoriteIds.has(mentor.userId);
                    return (
                      <div
                        key={mentor.userId}
                        role="button"
                        tabIndex={0}
                        onClick={() => setSelectedMentorId(mentor.userId)}
                        onKeyDown={(event) => handleMentorCardKeyDown(event, mentor.userId)}
                        className={joinClasses(
                          "group w-full cursor-pointer rounded-[1.8rem] border p-5 text-left transition-all duration-300 hover:-translate-y-0.5 hover:shadow-[0_15px_40px_rgba(15,23,42,0.08)] focus:outline-none focus:ring-2 focus:ring-indigo-300 focus:ring-offset-2",
                          isSelected ? "border-indigo-400 bg-white shadow-md ring-2 ring-indigo-50" : "border-white/80 bg-white/60 backdrop-blur-sm",
                        )}
                        aria-pressed={isSelected}
                      >
                        <div className="flex items-start justify-between gap-4">
                          <div className="flex min-w-0 gap-4">
                            <div className="relative shrink-0">
                              <MentorIdentityAvatar
                                userId={mentor.userId}
                                displayName={mentor.displayName}
                                avatarUrl={mentor.avatarUrl}
                                alt={mentor.displayName}
                                className="h-14 w-14 shadow-sm"
                                fallbackClassName="bg-gradient-to-br from-indigo-100 to-indigo-50 text-indigo-700"
                              />
                              {mentor.available ? (
                                <span className="absolute bottom-0 right-0 h-3.5 w-3.5 rounded-full border-2 border-white bg-emerald-500" />
                              ) : null}
                            </div>
                            <div className="min-w-0">
                              <div className="flex items-center gap-2">
                                <h3 className="max-w-[160px] truncate text-[1.35rem] font-bold text-slate-900 sm:max-w-[220px]">
                                  {mentor.displayName}
                                </h3>
                                {recommendation ? (
                                  <span className="inline-flex shrink-0 items-center rounded-md bg-indigo-50 px-2 py-0.5 text-xs font-bold text-indigo-600">
                                    <Sparkles size={10} className="mr-0.5" />
                                    AI 推荐
                                  </span>
                                ) : null}
                              </div>
                              <div className="mt-0.5 flex items-center gap-1.5 text-sm font-medium text-slate-600">
                                <Briefcase size={14} className="shrink-0 opacity-60" />
                                <span className="truncate">{toMentorRoleText(mentor)}</span>
                              </div>
                              {recommendation?.explainText ? (
                                <p className="mt-3 line-clamp-2 text-base leading-7 text-slate-500">{recommendation.explainText}</p>
                              ) : mentor.bio ? (
                                <p className="mt-3 line-clamp-2 text-base leading-7 text-slate-500">{mentor.bio}</p>
                              ) : null}
                            </div>
                          </div>
                          <div className="shrink-0 text-right">
                            <div className="text-xl font-black text-slate-900">{formatMoneyFen(mentor.priceFen)}</div>
                            <div className="mt-0.5 text-sm text-slate-500">起步价</div>
                          </div>
                        </div>

                        <div className="mt-4 flex items-center gap-2 overflow-hidden whitespace-nowrap">
                          {mentor.serviceScenes.slice(0, 3).map((tag) => (
                            <span key={tag} className="shrink-0 rounded-md bg-slate-100 px-2.5 py-1 text-sm font-medium text-slate-600">
                              {tag}
                            </span>
                          ))}
                          {mentor.expertiseTags.slice(0, 2).map((tag) => (
                            <span key={tag} className="shrink-0 rounded-md border border-slate-200 px-2.5 py-1 text-sm font-medium text-slate-500">
                              {tag}
                            </span>
                          ))}
                        </div>

                        <div className="mt-5 flex items-center justify-between border-t border-slate-100 pt-4">
                          <div className="flex items-center gap-4 text-sm font-medium text-slate-600">
                            <div className="flex items-center text-amber-500">
                              <Star size={16} fill="currentColor" className="mr-1" />
                              <span className="text-slate-700">{mentor.avgRating?.toFixed(1) ?? "—"}</span>
                            </div>
                            <div className="flex items-center">
                              <MessageSquare size={16} className="mr-1.5 opacity-60" />
                              {mentor.totalOrders} 次咨询
                            </div>
                          </div>

                          <FavoriteButton
                            isFavorite={isFavorite}
                            onClick={(event) => handleToggleFavorite(event, mentor.userId)}
                            disabled={pendingFavoriteIds.has(mentor.userId)}
                          />
                        </div>
                      </div>
                    );
                  })
                ) : (
                  <div className="rounded-[1.8rem] border border-dashed border-slate-200 bg-white/70 px-5 py-6 text-sm leading-7 text-slate-500">
                    当前筛选条件下没有匹配的导师。可以尝试放宽价格区间、取消“仅看可接单”或“仅看收藏”，或者切换到更通用的问题场景。
                  </div>
                )}
              </div>

              {mentorList?.records.length ? (
                <div className="mt-8 space-y-3">
                  <div className="flex flex-col gap-3 rounded-[1.4rem] border border-white/80 bg-white/80 px-4 py-4 text-base text-slate-600 shadow-sm sm:flex-row sm:items-center sm:justify-between">
                    <div>
                      <div className="font-semibold text-slate-800">已加载 {loadedMentorCount} / {mentorList.total} 位导师</div>
                      <div className="mt-1 text-sm leading-7 text-slate-500">
                        列表已切到分页懒加载模式，继续下滑会自动补下一页；切换筛选条件会自动回到第一页重新计算结果。
                      </div>
                    </div>
                    <div className="inline-flex items-center gap-2 self-start rounded-full border border-slate-200 bg-slate-50 px-3 py-2 text-sm font-semibold text-slate-500 sm:self-auto">
                      <RefreshCw size={13} className={loadingMore ? "animate-spin text-indigo-500" : ""} />
                      {loadingMore ? "正在补充更多导师..." : hasMore ? "继续下滑自动加载" : "已全部加载完成"}
                    </div>
                  </div>

                  {loadMoreError ? (
                    <div className="flex flex-col gap-3 rounded-[1.4rem] border border-amber-100 bg-amber-50 px-4 py-4 text-base text-amber-800 sm:flex-row sm:items-center sm:justify-between">
                      <div>{loadMoreError}</div>
                      <button
                        type="button"
                        onClick={handleLoadMore}
                        className="inline-flex items-center justify-center rounded-full bg-amber-500 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-amber-600"
                      >
                        重试加载
                      </button>
                    </div>
                  ) : null}

                  <div
                    ref={loadMoreSentinelRef}
                    className="flex min-h-[4.25rem] items-center justify-center rounded-[1.4rem] border border-dashed border-slate-200 bg-white/70 px-4 py-4 text-sm text-slate-500"
                  >
                    {loadingMore ? (
                      <span className="inline-flex items-center gap-2 font-medium text-indigo-600">
                        <RefreshCw size={14} className="animate-spin" />
                        正在补充下一页导师...
                      </span>
                    ) : hasMore ? (
                      <span>滚动到这里会自动补充更多导师，也可以停留片刻等待续载。</span>
                    ) : (
                      <span>导师列表已经全部展示完毕，可以继续在右侧预览区挑选并进入咨询准备。</span>
                    )}
                  </div>
                </div>
              ) : null}
            </motion.div>

            <div className="relative z-30 hidden lg:block">
              <div className="sticky top-[100px]">
                <motion.div
                  initial={{ opacity: 0, y: 16 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 0.1, duration: 0.35 }}
                  className="flex max-h-[calc(100vh-120px)] w-full flex-col overflow-hidden rounded-[2.2rem] border border-white/80 bg-white/90 shadow-[0_30px_80px_rgba(15,23,42,0.12)] backdrop-blur-xl"
                >
                  <div className="flex-1 overflow-y-auto [-ms-overflow-style:none] [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
                    {detailLoading && !selectedMentor ? (
                      <div className="space-y-4 p-6">
                        <div className="h-32 rounded-[1.6rem] bg-slate-100" />
                        <div className="h-24 rounded-[1.6rem] bg-slate-100" />
                        <div className="h-40 rounded-[1.6rem] bg-slate-100" />
                      </div>
                    ) : detailError ? (
                      <div className="p-6">
                        <div className="rounded-[1.6rem] border border-rose-100 bg-rose-50 px-4 py-4 text-sm leading-7 text-rose-700">
                          {detailError}
                        </div>
                      </div>
                    ) : selectedMentor ? (
                      <AnimatePresence mode="wait">
                        <motion.div
                          key={selectedMentor.userId}
                          initial={{ opacity: 0, x: 20 }}
                          animate={{ opacity: 1, x: 0 }}
                          exit={{ opacity: 0, x: -20 }}
                          transition={{ duration: 0.25, type: "spring", stiffness: 260, damping: 20 }}
                        >
                          <div className="relative h-32 shrink-0 bg-gradient-to-br from-indigo-500 via-purple-500 to-amber-400 opacity-90">
                            <div className="absolute inset-0 bg-black/10 mix-blend-overlay" />
                          </div>

                          <div className="relative px-6 pb-6 pt-0">
                            <div className="-mt-10 mb-4 flex items-end justify-between">
                              <MentorIdentityAvatar
                                userId={selectedMentor.userId}
                                displayName={selectedMentor.displayName}
                                avatarUrl={selectedMentor.avatarUrl}
                                alt={selectedMentor.displayName}
                                className="h-20 w-20 rounded-2xl shadow-lg"
                                imageClassName="rounded-2xl"
                                fallbackClassName="bg-slate-100 text-slate-500"
                              />
                              <div className="mb-1">
                                <FavoriteButton
                                  isFavorite={favoriteIds.has(selectedMentor.userId)}
                                  onClick={(event) => handleToggleFavorite(event, selectedMentor.userId)}
                                  disabled={pendingFavoriteIds.has(selectedMentor.userId)}
                                  className="bg-white/80 backdrop-blur-md"
                                />
                              </div>
                            </div>

                            <div>
                              <h2 className="text-2xl font-black text-slate-900">{selectedMentor.displayName}</h2>
                            <div className="mt-1 text-base font-semibold text-slate-600">{toMentorRoleText(selectedMentor)}</div>
                          </div>

                          <div className="mt-4 flex flex-wrap gap-2">
                            {selectedMentor.expertiseTags.map((tag) => (
                                <span key={tag} className="rounded-md border border-slate-200 bg-slate-50 px-2.5 py-1 text-sm font-semibold text-slate-600">
                                  {tag}
                                </span>
                              ))}
                              {selectedMentor.serviceScenes.slice(0, 3).map((tag) => (
                                <span key={tag} className="rounded-md bg-indigo-50 px-2.5 py-1 text-sm font-semibold text-indigo-600">
                                  {tag}
                                </span>
                              ))}
                            </div>

                            <p className="mt-5 text-base leading-8 text-slate-600">
                              {selectedMentor.bio || "当前导师暂未补充完整简介，可以先结合服务场景、评分和近期评价做第一轮判断。"}
                            </p>

                            {selectedMentorPackages.length ? (
                              <div className="mt-5 rounded-2xl border border-slate-200 bg-slate-50/80 p-4">
                                <div className="text-sm font-bold text-slate-600">可选套餐预览</div>
                                <div className="mt-3 space-y-3">
                                  {selectedMentorPackages.slice(0, 2).map((item) => (
                                    <div key={item.id} className="rounded-xl border border-white bg-white px-4 py-3">
                                      <div className="flex items-start justify-between gap-4">
                                        <div>
                                          <div className="text-sm font-bold text-slate-900">{item.packageName}</div>
                                          <div className="mt-1 text-sm text-slate-500">
                                            {item.sceneLabel} · {item.deliveryMode === "APPOINTMENT" ? `${item.durationMinutes ?? 45} 分钟预约` : "图文异步"}
                                          </div>
                                        </div>
                                        <div className="text-sm font-black text-slate-900">{formatMoneyFen(item.priceFen)}</div>
                                      </div>
                                    </div>
                                  ))}
                                </div>
                              </div>
                            ) : null}

                            {selectedMentorServiceContext ? (
                              <div className="mt-5 grid gap-3">
                                <div className="grid gap-3 sm:grid-cols-2">
                                  <div className="rounded-2xl border border-emerald-100 bg-emerald-50/70 p-4">
                                    <div className="text-sm font-bold text-emerald-600">适合咨询</div>
                                    <div className="mt-2 text-base leading-7 text-slate-700">
                                      {selectedMentorServiceContext.suitableFor || "当前导师还没有单独说明更适合的咨询人群。"}
                                    </div>
                                  </div>
                                  <div className="rounded-2xl border border-amber-100 bg-amber-50/70 p-4">
                                    <div className="text-sm font-bold text-amber-600">不太适合</div>
                                    <div className="mt-2 text-base leading-7 text-slate-700">
                                      {selectedMentorServiceContext.notSuitableFor || "当前导师还没有补充不适合的服务边界。"}
                                    </div>
                                  </div>
                                </div>
                                <div className="grid gap-3 sm:grid-cols-2">
                                  <div className="rounded-2xl border border-slate-200 bg-slate-50/80 p-4">
                                    <div className="text-sm font-bold text-slate-600">建议提前准备</div>
                                    <div className="mt-2 text-base leading-7 text-slate-700">
                                      {selectedMentorServiceContext.prepMaterials || "可以先准备简历、目标岗位 JD 和最想优先解决的 1-2 个问题。"}
                                    </div>
                                  </div>
                                  <div className="rounded-2xl border border-indigo-100 bg-indigo-50/80 p-4">
                                    <div className="text-sm font-bold text-indigo-600">回复节奏</div>
                                    <div className="mt-2 text-base leading-7 text-slate-700">
                                      {selectedMentorServiceContext.replyRhythm || "当前导师还没有单独补充回复节奏说明。"}
                                    </div>
                                  </div>
                                </div>
                              </div>
                            ) : null}

                            {recommendationMap.has(selectedMentor.userId) ? (
                              <div className="mt-6 rounded-2xl border border-indigo-100 bg-indigo-50/70 p-4">
                                <div className="flex items-center justify-between">
                                  <span className="text-sm font-bold  text-indigo-400">为什么推荐他</span>
                                  <div className="text-base font-black text-indigo-600">{recommendationMap.get(selectedMentor.userId)?.score}</div>
                                </div>
                                <div className="mt-3 space-y-2">
                                  {recommendationMap.get(selectedMentor.userId)?.reasons.map((reason) => (
                                    <div key={reason} className="flex items-start text-sm text-slate-600">
                                      <CheckCircle2 size={14} className="mr-2 mt-0.5 shrink-0 text-indigo-500" />
                                      {reason}
                                    </div>
                                  ))}
                                  {recommendationMap.get(selectedMentor.userId)?.risk ? (
                                    <div className="flex items-start text-sm text-amber-700">
                                      <AlertCircle size={14} className="mr-2 mt-0.5 shrink-0" />
                                      {recommendationMap.get(selectedMentor.userId)?.risk}
                                    </div>
                                  ) : null}
                                </div>
                              </div>
                            ) : null}

                            <div className="mt-6 rounded-2xl border border-slate-100 bg-slate-50 p-4">
                              <div className="mb-3 flex items-center justify-between">
                                <span className="text-sm font-bold  text-slate-400">最新评价</span>
                                <div className="flex items-center text-sm font-bold text-amber-500">
                                  <Star size={12} fill="currentColor" className="mr-1" />
                                  {selectedMentor.avgRating?.toFixed(1) ?? "—"}
                                </div>
                              </div>
                              {selectedMentorRecentReviews.length ? (
                                <div className="space-y-3">
                                  {selectedMentorRecentReviews.map((review) => (
                                    <div key={review.orderNo} className="text-sm">
                                      <div className="flex items-center justify-between gap-3">
                                        <span className="font-semibold text-slate-700">{review.studentDisplayName}</span>
                                        <span className="text-sm text-slate-400">{formatDateTime(review.createdAt)}</span>
                                      </div>
                                      <div className="mt-1 text-slate-600">{review.comment || "该评价未填写文字说明。"}</div>
                                    </div>
                                  ))}
                                </div>
                              ) : (
                                <div className="text-sm italic text-slate-400">暂无公开评价</div>
                              )}
                            </div>
                          </div>
                        </motion.div>
                      </AnimatePresence>
                    ) : (
                      <div className="flex min-h-[480px] flex-col items-center justify-center px-6 py-12 text-center">
                        <div className="flex h-20 w-20 items-center justify-center rounded-full bg-indigo-50">
                          <Bot size={34} className="text-indigo-300" />
                        </div>
                        <h3 className="mt-6 text-xl font-semibold text-slate-700">先从左侧选择一位导师</h3>
                        <p className="mt-3 max-w-sm text-base leading-8 text-slate-500">
                          选中导师后，这里会显示更完整的简介、评价和推荐理由，方便你完成第一轮判断。
                        </p>
                      </div>
                    )}
                  </div>

                  {selectedMentor ? (
                    <div className="shrink-0 border-t border-slate-100 bg-white/95 p-6 backdrop-blur-md">
                      <div className="mb-4 flex items-center justify-between">
                        <div>
                          <div className="text-sm font-bold  text-slate-400">套餐起步价</div>
                          <div className="mt-0.5 text-2xl font-black text-slate-900">{formatMoneyFen(selectedMentor.priceFen)}</div>
                        </div>
                        <div className="text-right">
                          <div className="text-sm font-bold  text-slate-400">状态</div>
                          <div className={joinClasses("mt-1 text-base font-bold", selectedMentor.available ? "text-emerald-500" : "text-amber-500")}>
                            {selectedMentor.available ? "近期可预约" : "排期较满"}
                          </div>
                        </div>
                      </div>

                      <button
                        type="button"
                        onClick={() => setIsPrepModalOpen(true)}
                        className="group flex w-full items-center justify-center rounded-2xl bg-gradient-to-r from-indigo-600 to-indigo-500 px-5 py-4 text-base font-bold text-white shadow-[0_15px_30px_rgba(79,70,229,0.3)] transition-all hover:-translate-y-0.5 hover:shadow-[0_20px_40px_rgba(79,70,229,0.4)]"
                      >
                        <FileText size={18} className="mr-2 opacity-80" />
                        生成咨询准备单
                        <ChevronRight size={18} className="ml-1 opacity-60 transition-transform group-hover:translate-x-1" />
                      </button>
                      <p className="mt-3 text-center text-sm text-slate-400">先准备问题，后决定是否下单</p>
                    </div>
                  ) : null}
                </motion.div>
              </div>
            </div>
          </div>
        </motion.div>
      </main>

      <PrepSheetModal
        isOpen={isPrepModalOpen}
        onClose={() => setIsPrepModalOpen(false)}
        mentor={mentorDetail ?? null}
        scene={scene}
        studentProfile={studentProfile}
        userId={userId}
        onProceed={handleGoConsultWithPrep}
      />
    </div>
  );
}
