import {
  ArrowUp,
  Bell,
  Filter,
  Flame,
  GraduationCap,
  Loader2,
  MessageSquare,
  PenSquare,
  RefreshCw,
  Search,
  ShieldCheck,
  X,
} from "lucide-react";
import { AnimatePresence, motion } from "framer-motion";
import { useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import CommunityEmptyState from "../components/community/CommunityEmptyState";
import CommunityMarkdownComposer from "../components/community/CommunityMarkdownComposer";
import CommunityModuleLayout from "../components/community/CommunityModuleLayout";
import CommunityNotificationEntry from "../components/community/CommunityNotificationEntry";
import CommunityPostCard from "../components/community/CommunityPostCard";
import CommunityToast, { type CommunityToastState } from "../components/community/CommunityToast";
import {
  COMMUNITY_SCENARIO_OPTIONS,
  createCommunityPost,
  getCommunityLeaderboard,
  listCommunityPosts,
  type CommunityPostSummary,
} from "../lib/community";
import { formatCount } from "../lib/formatters";
import { listNotifications, type NotificationRecord } from "../lib/notifications";
import { ApiClientError, isAbortError } from "../lib/apiClient";
import {
  getCommunityRoleExperience,
  getDefaultCommunityLeaderboardState,
} from "../lib/communityRoleExperience";
import {
  getScenarioLabel,
  joinClasses,
  parseTagInput,
} from "../components/community/communityUtils";

const PAGE_SIZE = 8;
const COMMUNITY_FEED_SNAPSHOT_STORAGE_KEY_PREFIX = "bishe.community.feed.snapshot.v1";

type FeedTab = "latest" | "unanswered" | "mentor" | "mine";

type CommunityFeedSnapshot = {
  version: 1;
  role: string;
  userId: number | null;
  savedAt: string;
  leaderboardScore: string;
  leaderboardHint: string;
  rankingValue: string;
  rankingHint: string;
  communityNotifications: NotificationRecord[];
  communityUnreadCount: number;
  tagSuggestions: string[];
};

const FEED_TABS: Array<{
  key: FeedTab;
  label: string;
  icon: typeof RefreshCw;
}> = [
  { key: "latest", label: "最新发布", icon: RefreshCw },
  { key: "unanswered", label: "待回答", icon: MessageSquare },
  { key: "mentor", label: "导师参与", icon: GraduationCap },
  { key: "mine", label: "我的参与", icon: PenSquare },
];

function toUserMessage(error: unknown, fallback: string) {
  if (error instanceof ApiClientError) {
    return error.message || fallback;
  }
  if (error instanceof Error) {
    return error.message || fallback;
  }
  return fallback;
}

function getCommunityFeedSnapshotStorageKey(role: string | null, userId: number | null) {
  return `${COMMUNITY_FEED_SNAPSHOT_STORAGE_KEY_PREFIX}.${role ?? "UNKNOWN"}.${typeof userId === "number" ? userId : "anon"}`;
}

function getCommunityFeedSnapshotStorage() {
  if (typeof window === "undefined") {
    return null;
  }

  return window.sessionStorage;
}

function isValidNotificationRecord(input: unknown): input is NotificationRecord {
  if (!input || typeof input !== "object") {
    return false;
  }

  const record = input as Partial<NotificationRecord>;
  return typeof record.id === "number"
    && typeof record.title === "string"
    && typeof record.content === "string"
    && typeof record.read === "boolean"
    && typeof record.payload === "object"
    && record.payload !== null;
}

function readCommunityFeedSnapshot(role: string | null, userId: number | null): CommunityFeedSnapshot | null {
  const storage = getCommunityFeedSnapshotStorage();
  if (!storage) {
    return null;
  }

  const rawValue = storage.getItem(getCommunityFeedSnapshotStorageKey(role, userId));
  if (!rawValue) {
    return null;
  }

  try {
    const parsed = JSON.parse(rawValue) as Partial<CommunityFeedSnapshot>;
    if (
      parsed.version !== 1
      || parsed.role !== (role ?? "UNKNOWN")
      || (typeof userId === "number" ? parsed.userId !== userId : parsed.userId !== null)
      || typeof parsed.leaderboardScore !== "string"
      || typeof parsed.leaderboardHint !== "string"
      || typeof parsed.rankingValue !== "string"
      || typeof parsed.rankingHint !== "string"
      || !Array.isArray(parsed.communityNotifications)
      || !parsed.communityNotifications.every((item) => isValidNotificationRecord(item))
      || typeof parsed.communityUnreadCount !== "number"
      || !Array.isArray(parsed.tagSuggestions)
      || !parsed.tagSuggestions.every((item) => typeof item === "string")
    ) {
      storage.removeItem(getCommunityFeedSnapshotStorageKey(role, userId));
      return null;
    }

    return {
      version: 1,
      role: role ?? "UNKNOWN",
      userId: typeof userId === "number" ? userId : null,
      savedAt: typeof parsed.savedAt === "string" ? parsed.savedAt : new Date().toISOString(),
      leaderboardScore: parsed.leaderboardScore,
      leaderboardHint: parsed.leaderboardHint,
      rankingValue: parsed.rankingValue,
      rankingHint: parsed.rankingHint,
      communityNotifications: parsed.communityNotifications.slice(0, 3),
      communityUnreadCount: Math.max(0, Math.trunc(parsed.communityUnreadCount)),
      tagSuggestions: parsed.tagSuggestions.slice(0, 8),
    };
  } catch {
    storage.removeItem(getCommunityFeedSnapshotStorageKey(role, userId));
    return null;
  }
}

function writeCommunityFeedSnapshot(
  role: string | null,
  userId: number | null,
  payload: Omit<CommunityFeedSnapshot, "version" | "role" | "userId" | "savedAt">,
) {
  const storage = getCommunityFeedSnapshotStorage();
  if (!storage) {
    return;
  }

  const snapshot: CommunityFeedSnapshot = {
    version: 1,
    role: role ?? "UNKNOWN",
    userId: typeof userId === "number" ? userId : null,
    savedAt: new Date().toISOString(),
    leaderboardScore: payload.leaderboardScore,
    leaderboardHint: payload.leaderboardHint,
    rankingValue: payload.rankingValue,
    rankingHint: payload.rankingHint,
    communityNotifications: payload.communityNotifications.slice(0, 3),
    communityUnreadCount: Math.max(0, Math.trunc(payload.communityUnreadCount)),
    tagSuggestions: payload.tagSuggestions.slice(0, 8),
  };

  storage.setItem(getCommunityFeedSnapshotStorageKey(role, userId), JSON.stringify(snapshot));
}

function PostSkeletonCard() {
  return (
    <div className="rounded-[1.85rem] border border-slate-100 bg-white p-6 shadow-sm">
      <div className="flex animate-pulse items-start justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="h-10 w-10 rounded-full bg-slate-200" />
          <div className="space-y-2">
            <div className="h-3 w-24 rounded bg-slate-200" />
            <div className="h-2 w-32 rounded bg-slate-100" />
          </div>
        </div>
        <div className="h-6 w-16 rounded-full bg-slate-100" />
      </div>
      <div className="mt-5 space-y-3">
        <div className="h-5 w-3/4 rounded bg-slate-200" />
        <div className="h-4 w-full rounded bg-slate-100" />
        <div className="h-4 w-5/6 rounded bg-slate-100" />
      </div>
      <div className="mt-5 flex gap-2">
        <div className="h-7 w-24 rounded-full bg-slate-100" />
        <div className="h-7 w-20 rounded-full bg-slate-100" />
      </div>
    </div>
  );
}

type CommunityFeedPageContentProps = {
  role: string | null;
  userId: number | null;
};

function CommunityFeedPageContent({ role, userId }: CommunityFeedPageContentProps) {
  const navigate = useNavigate();
  const communityExperience = getCommunityRoleExperience(role);
  const initialCommunitySnapshotRef = useRef<CommunityFeedSnapshot | null>(
    // 社区首页只缓存轻量侧边信息，帖子列表仍走接口按页刷新。
    readCommunityFeedSnapshot(role, userId),
  );
  const initialCommunitySnapshot = initialCommunitySnapshotRef.current;
  const initialLeaderboardState = role === "MENTOR"
    ? getDefaultCommunityLeaderboardState(role)
    : initialCommunitySnapshot ?? getDefaultCommunityLeaderboardState(role);

  const [feedTab, setFeedTab] = useState<FeedTab>("latest");
  const [posts, setPosts] = useState<CommunityPostSummary[]>([]);
  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(true);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  const [searchInput, setSearchInput] = useState("");
  const [searchKeyword, setSearchKeyword] = useState("");
  const [selectedScenario, setSelectedScenario] = useState<string>("");
  const [selectedTag, setSelectedTag] = useState("");

  const [leaderboardScore, setLeaderboardScore] = useState<string>(initialLeaderboardState.leaderboardScore);
  const [leaderboardHint, setLeaderboardHint] = useState<string>(initialLeaderboardState.leaderboardHint);
  const [rankingValue, setRankingValue] = useState<string>(initialLeaderboardState.rankingValue);
  const [rankingHint, setRankingHint] = useState<string>(initialLeaderboardState.rankingHint);
  const [communityNotifications, setCommunityNotifications] = useState<NotificationRecord[]>(initialCommunitySnapshot?.communityNotifications ?? []);
  const [communityUnreadCount, setCommunityUnreadCount] = useState(initialCommunitySnapshot?.communityUnreadCount ?? 0);
  const [tagSuggestions, setTagSuggestions] = useState<string[]>(initialCommunitySnapshot?.tagSuggestions ?? []);

  const [composerTitle, setComposerTitle] = useState("");
  const [composerScenario, setComposerScenario] = useState(COMMUNITY_SCENARIO_OPTIONS[0]?.code ?? "GENERAL_HELP");
  const [composerTags, setComposerTags] = useState("");
  const [composerContent, setComposerContent] = useState("");
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [submittingPost, setSubmittingPost] = useState(false);
  const [toast, setToast] = useState<CommunityToastState | null>(null);
  const [showBackToTop, setShowBackToTop] = useState(false);
  const loadMoreSentinelRef = useRef<HTMLDivElement | null>(null);
  const loadMoreLockRef = useRef(false);

  const availableTags = tagSuggestions;
  const hasFeedLoadFailure = !loading && !!error && posts.length === 0;

  const showToast = (text: string, tone: CommunityToastState["tone"] = "info") => {
    setToast({
      id: Date.now() + Math.random(),
      tone,
      text,
    });
  };

  const displayedPosts = posts.filter((post) => {
    // tabs 是当前 feed 内的本地视图切换，不额外触发后端请求。
    switch (feedTab) {
      case "unanswered":
        return post.commentCount === 0 && post.moderationStatus === "PASS";
      case "mentor":
        return post.hasMentorReply;
      case "mine":
        return post.authoredByMe || post.participatedByMe;
      case "latest":
      default:
        return true;
    }
  });

  useEffect(() => {
    const snapshot = readCommunityFeedSnapshot(role, userId);
    const fallbackLeaderboardState = role === "MENTOR"
      ? getDefaultCommunityLeaderboardState(role)
      : snapshot ?? getDefaultCommunityLeaderboardState(role);

    setLeaderboardScore(fallbackLeaderboardState.leaderboardScore);
    setLeaderboardHint(fallbackLeaderboardState.leaderboardHint);
    setRankingValue(fallbackLeaderboardState.rankingValue);
    setRankingHint(fallbackLeaderboardState.rankingHint);
    setCommunityNotifications(snapshot?.communityNotifications ?? []);
    setCommunityUnreadCount(snapshot?.communityUnreadCount ?? 0);
    setTagSuggestions(snapshot?.tagSuggestions ?? []);
  }, [role, userId]);

  useEffect(() => {
    const controller = new AbortController();
    const isLoadMore = page > 1;

    if (isLoadMore) {
      setLoadingMore(true);
    } else {
      setLoading(true);
      setError(null);
    }

    // 列表接口按 page/keyword/tag/scenario 拉取，加载更多时只追加新页。
    void listCommunityPosts({
      page,
      size: PAGE_SIZE,
      keyword: searchKeyword || undefined,
      tag: selectedTag || undefined,
      scenarioCode: selectedScenario || undefined,
      signal: controller.signal,
    }).then((data) => {
      if (controller.signal.aborted) {
        return;
      }
      setPosts((current) => {
        const nextPosts = page === 1 ? data.records : [...current, ...data.records];
        // 标签建议来自当前可见帖子聚合，避免再维护一套独立推荐接口。
        const nextTagSuggestions = Array.from(
          new Set(nextPosts.flatMap((post) => post.tags).filter(Boolean)),
        ).slice(0, 8);
        setTagSuggestions(nextTagSuggestions);
        return nextPosts;
      });
      setHasMore(data.page * data.size < data.total);
      setError(null);
    }).catch((fetchError: unknown) => {
      if (isAbortError(fetchError)) {
        return;
      }
      if (controller.signal.aborted) {
        return;
      }
      const message = toUserMessage(fetchError, "社区内容暂时加载失败，请稍后再试。");
      setError(message);
      showToast(message, "error");
      if (page === 1) {
        setPosts([]);
      }
    }).finally(() => {
      loadMoreLockRef.current = false;
      if (controller.signal.aborted) {
        return;
      }
      setLoading(false);
      setLoadingMore(false);
    });

    return () => {
      controller.abort();
    };
  }, [page, refreshKey, role, searchKeyword, selectedScenario, selectedTag]);

  useEffect(() => {
    let active = true;

    // 首页右侧信息和通知概览不阻塞主 feed，失败时保留快照值。
    void listNotifications({ page: 1, size: 20, unreadOnly: false }).then((data) => {
      if (!active) {
        return;
      }
      const records = data.records.filter((item) => item.category === "COMMUNITY");
      setCommunityNotifications(records.slice(0, 3));
      setCommunityUnreadCount(records.filter((item) => !item.read).length);
    }).catch(() => {
      if (!active) {
        return;
      }
    });

    void getCommunityLeaderboard({ page: 1, size: 50, window: "7d" }).then((data) => {
      if (!active) {
        return;
      }

      const currentRow = data.records.find((item) => item.studentUserId === userId) ?? null;
      if (role === "MENTOR") {
        const mentorLeaderboardState = getDefaultCommunityLeaderboardState(role);
        setLeaderboardScore(mentorLeaderboardState.leaderboardScore);
        setLeaderboardHint(mentorLeaderboardState.leaderboardHint);
        setRankingValue(mentorLeaderboardState.rankingValue);
        setRankingHint(mentorLeaderboardState.rankingHint);
        return;
      }

      if (currentRow) {
        setLeaderboardScore(`${formatCount(currentRow.score)} 分`);
        setLeaderboardHint(`近 7 天 ${formatCount(currentRow.postCount)} 帖 / ${formatCount(currentRow.commentCount)} 评`);
        setRankingValue(`第 ${currentRow.rank} 名`);
        setRankingHint(`公开获赞 ${formatCount(currentRow.likeReceivedCount)} 次`);
        return;
      }

      setLeaderboardScore("冲榜中");
      setLeaderboardHint("继续分享与回复即可累计社区分。");
      setRankingValue("Top 50 外");
      setRankingHint(`当前榜单共展示 ${formatCount(data.total)} 位活跃同学。`);
    }).catch(() => {
      if (!active) {
        return;
      }
    });

    return () => {
      active = false;
    };
  }, [role, userId]);

  useEffect(() => {
    // 排行、通知、热门标签写入弱快照，下一次进入先展示上次状态。
    writeCommunityFeedSnapshot(role, userId, {
      leaderboardScore,
      leaderboardHint,
      rankingValue,
      rankingHint,
      communityNotifications,
      communityUnreadCount,
      tagSuggestions,
    });
  }, [
    communityNotifications,
    communityUnreadCount,
    leaderboardHint,
    leaderboardScore,
    rankingHint,
    rankingValue,
    role,
    tagSuggestions,
    userId,
  ]);

  useEffect(() => {
    if (typeof window === "undefined") {
      return undefined;
    }

    const handleScroll = () => {
      setShowBackToTop(window.scrollY > 720);
    };

    handleScroll();
    window.addEventListener("scroll", handleScroll, { passive: true });

    return () => {
      window.removeEventListener("scroll", handleScroll);
    };
  }, []);

  useEffect(() => {
    if (typeof IntersectionObserver === "undefined") {
      return undefined;
    }

    const sentinel = loadMoreSentinelRef.current;
    if (!sentinel || !hasMore) {
      return undefined;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        const entry = entries[0];
        if (!entry?.isIntersecting || loading || loadingMore || loadMoreLockRef.current) {
          return;
        }

        // loadMoreLock 防止 IntersectionObserver 在同一帧内连续推进页码。
        loadMoreLockRef.current = true;
        setPage((current) => current + 1);
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
  }, [hasMore, loading, loadingMore, role]);

  useEffect(() => {
    if (!isCreateModalOpen || typeof document === "undefined" || typeof window === "undefined") {
      return undefined;
    }

    const previousOverflow = document.body.style.overflow;
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !submittingPost) {
        setIsCreateModalOpen(false);
      }
    };

    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", handleKeyDown);

    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [isCreateModalOpen, submittingPost]);

  const resetListAndSearch = (nextKeyword: string, nextScenario: string, nextTag: string) => {
    // 任一查询维度变化都回到第一页，避免旧分页和新筛选混在一起。
    loadMoreLockRef.current = false;
    setSearchKeyword(nextKeyword);
    setSelectedScenario(nextScenario);
    setSelectedTag(nextTag);
    setPage(1);
    setPosts([]);
    setHasMore(true);
  };

  const handleSearchSubmit = () => {
    resetListAndSearch(searchInput.trim(), selectedScenario, selectedTag);
  };

  const handleScenarioChange = (scenarioCode: string) => {
    resetListAndSearch(searchKeyword, scenarioCode, selectedTag);
  };

  const handleTagChange = (tag: string) => {
    resetListAndSearch(searchKeyword, selectedScenario, selectedTag === tag ? "" : tag);
  };

  const handleCreatePost = async () => {
    if (!composerTitle.trim() || !composerContent.trim()) {
      showToast("先补全标题和正文，再把这条讨论发出去。", "error");
      return;
    }

    setSubmittingPost(true);
    try {
      // 发帖结果可能进入待审；公开与否以 moderation.action 决定。
      const response = await createCommunityPost({
        title: composerTitle.trim(),
        scenarioCode: composerScenario,
        content: composerContent.trim(),
        tags: parseTagInput(composerTags),
      });

      setComposerTitle("");
      setComposerScenario(COMMUNITY_SCENARIO_OPTIONS[0]?.code ?? "GENERAL_HELP");
      setComposerTags("");
      setComposerContent("");
      setFeedTab("latest");
      setPage(1);
      // 发布成功后清空筛选并刷新第一页，待审帖不会进入公开列表。
      resetListAndSearch("", "", "");
      setSearchInput("");
      setIsCreateModalOpen(false);
      showToast(
        response.moderation?.action === "REVIEW"
          ? communityExperience.publishPendingModeration
          : response.aiFirstCommentCreated
            ? communityExperience.publishAiSuccess
            : communityExperience.publishSuccess,
        response.moderation?.action === "REVIEW" ? "info" : "success",
      );
    } catch (submitError) {
      showToast(toUserMessage(submitError, "这条讨论暂时没发出去，请稍后再试。"), "error");
    } finally {
      setSubmittingPost(false);
    }
  };

  return (
    <CommunityModuleLayout
      activeTab="feed"
      topbarSectionLabel={communityExperience.topbarSectionLabel}
      topbarTitle="社区广场"
      userSubtitle={communityExperience.topbarUserSubtitle}
      title={communityExperience.heroTitle}
      description={communityExperience.heroDescription}
      stats={[
        {
          label: "社区未读提醒",
          value: `${communityUnreadCount}`,
          hint: communityUnreadCount > 0
            ? "有人回复、审核或举报结果需要你回看。"
            : communityExperience.unreadEmptyHint,
          accentClassName: communityUnreadCount > 0 ? "text-orange-500" : "text-indigo-600",
        },
        {
          label: communityExperience.identityLabel,
          value: leaderboardScore,
          hint: leaderboardHint,
          accentClassName: "text-indigo-600",
        },
        {
          label: communityExperience.participationLabel,
          value: rankingValue,
          hint: rankingHint,
          accentClassName: "text-emerald-500",
        },
      ]}
    >
      <div className="grid items-start gap-6 lg:grid-cols-12">
        <aside className="space-y-6 lg:col-span-3 lg:sticky lg:top-[92px]">
          <div className="rounded-[1.6rem] border border-slate-100 bg-white p-3 shadow-sm">
            <div className="space-y-1">
              {FEED_TABS.map((tab) => (
                <button
                  key={tab.key}
                  type="button"
                  onClick={() => setFeedTab(tab.key)}
                  className={joinClasses(
                    "flex w-full items-center gap-3 rounded-xl px-4 py-3.5 text-base font-semibold transition-all",
                    feedTab === tab.key ? "bg-indigo-50 text-indigo-700" : "text-slate-600 hover:bg-slate-50",
                  )}
                >
                  <tab.icon size={16} className={feedTab === tab.key ? "text-indigo-600" : "text-slate-400"} />
                  {tab.label}
                </button>
              ))}
            </div>
          </div>

          <div className="rounded-[1.6rem] border border-slate-100 bg-white p-5 shadow-sm">
            <div className="mb-4 flex items-center gap-2">
              <Filter size={16} className="text-slate-400" />
              <h3 className="text-lg font-bold text-slate-900">问题场景</h3>
            </div>
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                onClick={() => handleScenarioChange("")}
                className={joinClasses(
                  "rounded-full border px-3 py-1.5 text-sm font-semibold transition-colors",
                  !selectedScenario
                    ? "border-indigo-200 bg-indigo-50 text-indigo-700"
                    : "border-slate-200 bg-white text-slate-600 hover:border-indigo-200",
                )}
              >
                全部场景
              </button>
              {COMMUNITY_SCENARIO_OPTIONS.map((option) => (
                <button
                  key={option.code}
                  type="button"
                  onClick={() => handleScenarioChange(option.code)}
                  className={joinClasses(
                    "rounded-full border px-3 py-1.5 text-sm font-semibold transition-colors",
                    selectedScenario === option.code
                      ? "border-indigo-200 bg-indigo-50 text-indigo-700"
                      : "border-slate-200 bg-white text-slate-600 hover:border-indigo-200",
                  )}
                >
                  {option.label}
                </button>
              ))}
            </div>
          </div>

          <div className="relative mb-28 hidden lg:block">
            <div className="rounded-[1.6rem] border border-slate-100 bg-white p-5 shadow-sm">
              <div className="mb-4 flex items-center gap-2">
                <Flame size={16} className="text-orange-400" />
                <h3 className="text-lg font-bold text-slate-900">热门标签</h3>
              </div>
              {availableTags.length > 0 ? (
                <div className="flex flex-wrap gap-2">
                  {availableTags.map((tag) => (
                    <button
                      key={tag}
                      type="button"
                      onClick={() => handleTagChange(tag)}
                      className={joinClasses(
                        "rounded-full border px-3 py-1.5 text-sm font-semibold transition-colors",
                        selectedTag === tag
                          ? "border-indigo-200 bg-indigo-50 text-indigo-700"
                          : "border-slate-200 bg-white text-slate-600 hover:border-indigo-200",
                      )}
                    >
                      #{tag}
                    </button>
                  ))}
                </div>
              ) : (
                <div className="rounded-[1.2rem] border border-dashed border-slate-200 bg-slate-50 px-4 py-5 text-base leading-8 text-slate-500">
                  {communityExperience.emptyTagsDescription}
                </div>
              )}
            </div>

            <AnimatePresence>
              {showBackToTop ? (
                <motion.button
                  type="button"
                  aria-label="回到顶部"
                  initial={{ opacity: 0, y: 10, scale: 0.96 }}
                  animate={{ opacity: 1, y: 0, scale: 1 }}
                  exit={{ opacity: 0, y: 8, scale: 0.96 }}
                  transition={{ duration: 0.18, ease: "easeOut" }}
                  onClick={() => window.scrollTo({ top: 0, behavior: "smooth" })}
                  className="absolute left-[calc(100%-1.45rem)] top-full z-20 flex w-[5.3rem] -translate-x-1/2 flex-col items-center"
                >
                  <span className="h-14 w-[3px] rounded-full bg-gradient-to-b from-emerald-100 via-sky-200 to-white shadow-[0_10px_24px_rgba(148,163,184,0.2)]" />
                  <span className="relative mt-[-0.15rem] flex w-full flex-col items-center gap-1.5 rounded-[1.55rem] border border-emerald-100 bg-[linear-gradient(180deg,rgba(255,255,255,0.98),rgba(244,253,250,0.98),rgba(239,249,255,0.98))] px-3 py-3 text-center text-teal-900 shadow-[0_18px_36px_rgba(148,163,184,0.2)] transition-transform duration-200 hover:-translate-y-0.5 hover:shadow-[0_22px_42px_rgba(148,163,184,0.24)]"
                  >
                    <span className="absolute left-1/2 top-0 h-4 w-4 -translate-x-1/2 -translate-y-1/2 rounded-full border border-emerald-100 bg-white shadow-sm" />
                    <span className="inline-flex h-10 w-10 items-center justify-center rounded-full bg-[linear-gradient(135deg,rgba(236,253,245,1),rgba(224,242,254,1))] text-teal-700">
                      <ArrowUp size={18} />
                    </span>
                    <span className="text-[0.78rem] font-semibold leading-4 tracking-[0.04em]">回到顶部</span>
                  </span>
                </motion.button>
              ) : null}
            </AnimatePresence>
          </div>

          <div className="rounded-[1.6rem] border border-slate-100 bg-white p-5 shadow-sm lg:hidden">
            <div className="mb-4 flex items-center gap-2">
              <Flame size={16} className="text-orange-400" />
              <h3 className="text-lg font-bold text-slate-900">热门标签</h3>
            </div>
            {availableTags.length > 0 ? (
              <div className="flex flex-wrap gap-2">
                {availableTags.map((tag) => (
                  <button
                    key={tag}
                    type="button"
                    onClick={() => handleTagChange(tag)}
                    className={joinClasses(
                      "rounded-full border px-3 py-1.5 text-sm font-semibold transition-colors",
                      selectedTag === tag
                        ? "border-indigo-200 bg-indigo-50 text-indigo-700"
                        : "border-slate-200 bg-white text-slate-600 hover:border-indigo-200",
                    )}
                  >
                    #{tag}
                  </button>
                ))}
              </div>
            ) : (
              <div className="rounded-[1.2rem] border border-dashed border-slate-200 bg-slate-50 px-4 py-5 text-base leading-8 text-slate-500">
                {communityExperience.emptyTagsDescription}
              </div>
            )}
          </div>
        </aside>

        <div className="relative space-y-4 lg:col-span-6">
          <div className="sticky top-[84px] z-20 rounded-[1.6rem] border border-white/80 bg-white/72 p-4 shadow-[0_16px_40px_rgba(148,163,184,0.18)] backdrop-blur-xl">
            <div className="relative flex items-center">
              <Search className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" size={18} />
              <input
                value={searchInput}
                onChange={(event) => setSearchInput(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "Enter") {
                    event.preventDefault();
                    handleSearchSubmit();
                  }
                }}
                placeholder={communityExperience.searchPlaceholder}
                className="w-full rounded-xl border border-slate-100 bg-slate-50/60 py-3 pl-11 pr-20 text-base font-medium text-slate-800 outline-none transition-colors placeholder:text-slate-400 focus:border-indigo-300 focus:bg-white focus:ring-4 focus:ring-indigo-500/10"
              />
              <button
                type="button"
                onClick={handleSearchSubmit}
                className="absolute right-1.5 top-1/2 -translate-y-1/2 rounded-lg bg-indigo-50 px-3.5 py-2 text-sm font-bold text-indigo-600 transition-colors hover:bg-indigo-100"
              >
                搜索
              </button>
            </div>

            <div className="mt-3 flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-3">
              <div className="flex flex-wrap items-center gap-2 text-sm text-slate-500">
                <span>当前筛选：</span>
                {selectedScenario ? (
                  <span className="inline-flex items-center rounded-full border border-indigo-100 bg-indigo-50 px-2.5 py-1 font-semibold text-indigo-700">
                    {getScenarioLabel(selectedScenario)}
                  </span>
                ) : null}
                {selectedTag ? (
                  <span className="inline-flex items-center rounded-full border border-indigo-100 bg-indigo-50 px-2.5 py-1 font-semibold text-indigo-700">
                    #{selectedTag}
                  </span>
                ) : null}
                {searchKeyword ? (
                  <span className="inline-flex items-center rounded-full border border-slate-200 bg-slate-100 px-2.5 py-1 font-semibold text-slate-600">
                    {searchKeyword}
                  </span>
                ) : null}
                {!selectedScenario && !selectedTag && !searchKeyword ? <span>全部内容</span> : null}
              </div>
              <div className="flex items-center gap-1 text-sm text-slate-400">
                <RefreshCw size={12} className={loadingMore ? "animate-spin text-indigo-500" : ""} />
                {loadingMore ? "正在补充更多帖子..." : "新的讨论和回复会持续更新"}
              </div>
            </div>
          </div>

          {loading && page === 1 ? (
            Array.from({ length: 3 }).map((_, index) => <PostSkeletonCard key={`skeleton-${index}`} />)
          ) : hasFeedLoadFailure ? (
            <CommunityEmptyState
              icon={MessageSquare}
              title="讨论内容暂时没有同步成功"
              description="这批讨论还没有成功刷新，你可以稍后再试，或先调整筛选后重新拉取。"
              action={(
                <button
                  type="button"
                  onClick={() => setRefreshKey((current) => current + 1)}
                  className="rounded-full bg-slate-900 px-5 py-2.5 text-base font-semibold text-white transition-colors hover:bg-slate-800"
                >
                  重新加载当前内容
                </button>
              )}
            />
          ) : displayedPosts.length === 0 ? (
            <CommunityEmptyState
              icon={MessageSquare}
              title="当前筛选下还没有匹配的帖子"
              description={communityExperience.noResultDescription}
              action={(
                <button
                  type="button"
                  onClick={() => {
                    setFeedTab("latest");
                    setSearchInput("");
                    resetListAndSearch("", "", "");
                  }}
                  className="rounded-full bg-slate-900 px-5 py-2.5 text-base font-semibold text-white transition-colors hover:bg-slate-800"
                >
                  清空筛选
                </button>
              )}
            />
          ) : (
            displayedPosts.map((post) => (
              <CommunityPostCard
                key={post.postId}
                post={post}
                onSelect={(selectedPost) => navigate(`/community/${selectedPost.postId}`)}
              />
            ))
          )}

          {!loading && (hasMore || loadingMore || displayedPosts.length > 0) ? (
            <div className="space-y-3">
              <div ref={loadMoreSentinelRef} className="h-2 w-full" aria-hidden="true" />
              <div className="rounded-[1.4rem] border border-slate-200 bg-white/95 px-5 py-4 shadow-sm">
                <div className="flex items-center justify-center gap-2 text-base font-semibold text-slate-700">
                  {loadingMore ? (
                    <Loader2 size={16} className="animate-spin text-indigo-500" />
                  ) : hasMore ? (
                    <RefreshCw size={16} className="text-indigo-500" />
                  ) : (
                    <MessageSquare size={16} className="text-emerald-500" />
                  )}
                  {loadingMore
                    ? "正在接续加载更多讨论"
                    : hasMore
                      ? "滑到底部后会自动加载下一批"
                      : "当前讨论已经全部展开"}
                </div>
                <p className="mt-2 text-center text-sm text-slate-500">
                  {loadingMore
                    ? "新的内容会直接接在列表下方。"
                    : hasMore
                      ? "继续往下浏览，页面会自动补上更多内容。"
                      : "你可以换个筛选条件，或者直接发起一条新的讨论。"}
                </p>
              </div>
            </div>
          ) : null}
        </div>

        <aside className="space-y-6 lg:col-span-3 lg:sticky lg:top-[92px]">
          <div className="relative overflow-hidden rounded-[1.8rem] bg-gradient-to-br from-indigo-500 to-violet-600 p-6 text-white shadow-lg">
            <div className="absolute right-[-0.75rem] top-[-0.75rem] opacity-15">
              <PenSquare size={96} />
            </div>
            <div className="relative">
              <div className="text-sm font-semibold tracking-[0.18em] text-indigo-100">{communityExperience.sideCtaSectionLabel}</div>
              <h2 className="mt-2 text-2xl font-black">{communityExperience.sideCtaTitle}</h2>
              <p className="mt-2 text-base leading-8 text-indigo-100">
                {communityExperience.sideCtaDescription}
              </p>
              <button
                type="button"
                onClick={() => setIsCreateModalOpen(true)}
                className="mt-5 inline-flex w-full items-center justify-center gap-2 rounded-[1rem] bg-white px-4 py-3 text-base font-semibold text-indigo-600 shadow-sm transition-all hover:-translate-y-0.5 hover:shadow-md"
              >
                <PenSquare size={16} />
                {communityExperience.sideCtaButtonLabel}
              </button>
            </div>
          </div>

          <div className="rounded-[1.8rem] border border-slate-100 bg-white p-5 shadow-sm">
            <div className="mb-4 flex items-center gap-2">
              <Bell size={16} className="text-indigo-500" />
              <h3 className="text-lg font-bold text-slate-900">最新动态</h3>
            </div>
            {communityNotifications.length > 0 ? (
              <div className="space-y-3">
                {communityNotifications.map((item) => (
                  <CommunityNotificationEntry key={item.id} notification={item} role={role} />
                ))}
              </div>
            ) : (
              <div className="rounded-[1.3rem] border border-dashed border-slate-200 bg-slate-50 px-4 py-5 text-base leading-8 text-slate-500">
                {communityExperience.latestActivityEmpty}
              </div>
            )}
          </div>

          <div className="rounded-[1.8rem] border border-slate-100 bg-white p-5 shadow-sm">
            <div className="flex items-start gap-3 rounded-[1.2rem] border border-slate-100 bg-slate-50 px-4 py-4 text-base leading-8 text-slate-600">
              <ShieldCheck size={16} className="mt-1 shrink-0 text-slate-400" />
              <div>
                <div className="font-semibold text-slate-900">社区规则提醒</div>
                <div className="mt-1">
                  优先鼓励真实、友善、可执行的求职交流。涉及广告引流、不实信息或攻击性内容时，可以直接在帖子详情里举报。
                </div>
              </div>
            </div>
          </div>
        </aside>
      </div>

      <AnimatePresence>
        {isCreateModalOpen ? (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.18, ease: "easeOut" }}
            className="fixed inset-0 z-50 flex items-center justify-center px-4 py-6 backdrop-blur-sm"
          >
            <motion.button
              type="button"
              aria-label="关闭新讨论弹窗"
              onClick={() => {
                if (!submittingPost) {
                  setIsCreateModalOpen(false);
                }
              }}
              className="absolute inset-0 bg-slate-950/35"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.18, ease: "easeOut" }}
            />
            <motion.div
              role="dialog"
              aria-modal="true"
              aria-labelledby="community-create-title"
              initial={{ opacity: 0, y: 20, scale: 0.98 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 12, scale: 0.985 }}
              transition={{ duration: 0.2, ease: [0.22, 1, 0.36, 1] }}
              className="relative z-10 flex max-h-[90vh] w-full max-w-[56rem] transform-gpu flex-col overflow-hidden rounded-[2.2rem] border border-white/80 bg-white shadow-[0_30px_80px_rgba(15,23,42,0.22)]"
            >
              <div className="flex items-start justify-between gap-4 border-b border-slate-100 bg-white px-7 py-6">
                <div className="min-w-0">
                  <div className="inline-flex items-center gap-2 rounded-full bg-indigo-50 px-3 py-1 text-sm font-semibold text-indigo-600">
                    <PenSquare size={15} />
                    {communityExperience.createBadgeLabel}
                  </div>
                  <h2 id="community-create-title" className="mt-3 text-[2rem] font-black tracking-tight text-slate-900">
                    {communityExperience.createTitle}
                  </h2>
                  <p className="mt-2 text-base leading-8 text-slate-500">
                    {communityExperience.createDescription}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => {
                    if (!submittingPost) {
                      setIsCreateModalOpen(false);
                    }
                  }}
                  className="inline-flex h-11 w-11 shrink-0 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-500 shadow-sm transition-colors hover:border-slate-300 hover:text-slate-900 disabled:cursor-not-allowed disabled:opacity-60"
                  disabled={submittingPost}
                >
                  <X size={18} />
                </button>
              </div>

              <div className="overflow-y-auto px-7 py-6">
                <div className="rounded-[1.45rem] border border-indigo-100 bg-indigo-50/70 px-5 py-4">
                  <div className="text-base font-semibold text-slate-900">{communityExperience.createGuideTitle}</div>
                  <p className="mt-2 text-base leading-8 text-slate-600">
                    {communityExperience.createGuideDescription}
                  </p>
                </div>

                <div className="mt-5 grid gap-4 md:grid-cols-2">
                  <label className="space-y-2 md:col-span-2">
                    <span className="text-base font-semibold text-slate-900">标题</span>
                    <input
                      value={composerTitle}
                      onChange={(event) => setComposerTitle(event.target.value)}
                      placeholder={communityExperience.titlePlaceholder}
                      className="w-full rounded-[1.1rem] border border-slate-200 px-4 py-3.5 text-base font-semibold text-slate-900 outline-none transition-colors placeholder:text-slate-400 focus:border-indigo-300"
                    />
                  </label>

                  <label className="space-y-2">
                    <span className="text-base font-semibold text-slate-900">问题场景</span>
                    <select
                      value={composerScenario}
                      onChange={(event) => setComposerScenario(event.target.value)}
                      className="w-full rounded-[1.1rem] border border-slate-200 bg-white px-4 py-3.5 text-base text-slate-700 outline-none transition-colors focus:border-indigo-300"
                    >
                      {COMMUNITY_SCENARIO_OPTIONS.map((option) => (
                        <option key={option.code} value={option.code}>
                          {option.label}
                        </option>
                      ))}
                    </select>
                  </label>

                  <label className="space-y-2">
                    <span className="text-base font-semibold text-slate-900">标签</span>
                    <input
                      value={composerTags}
                      onChange={(event) => setComposerTags(event.target.value)}
                      placeholder={communityExperience.tagPlaceholder}
                      className="w-full rounded-[1.1rem] border border-slate-200 px-4 py-3.5 text-base text-slate-700 outline-none transition-colors placeholder:text-slate-400 focus:border-indigo-300"
                    />
                  </label>
                </div>

                <div className="mt-5">
                  <CommunityMarkdownComposer
                    value={composerContent}
                    onChange={setComposerContent}
                    placeholder={communityExperience.contentPlaceholder}
                    rows={12}
                    submitText={communityExperience.submitText}
                    onSubmit={handleCreatePost}
                    onCancel={() => setIsCreateModalOpen(false)}
                    submitting={submittingPost}
                    className="rounded-[1.8rem] border-slate-100 shadow-none"
                    note={communityExperience.composerNote}
                  />
                </div>
              </div>
            </motion.div>
          </motion.div>
        ) : null}
      </AnimatePresence>
      <CommunityToast
        toast={toast}
        onClose={(toastId) => {
          setToast((current) => (current?.id === toastId ? null : current));
        }}
      />
    </CommunityModuleLayout>
  );
}

export default function CommunityPage() {
  const { role, userId } = useAuth();

  return (
    <CommunityFeedPageContent
      role={role}
      userId={userId}
    />
  );
}
