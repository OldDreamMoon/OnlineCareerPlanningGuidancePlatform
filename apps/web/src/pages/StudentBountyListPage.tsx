import {
  AlertCircle,
  ArrowRight,
  Award,
  BriefcaseBusiness,
  CheckCircle2,
  Clock3,
  Loader2,
  RefreshCw,
  Search,
  Sparkles,
  Target,
  Users,
} from "lucide-react";
import { startTransition, useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import EnterpriseIdentityLogo from "../components/avatar/EnterpriseIdentityLogo";
import StudentWorkspaceNav from "../components/student/StudentWorkspaceNav";
import { ApiClientError, apiRequest, buildQuery } from "../lib/apiClient";
import { getEnterpriseTaskUiStatus } from "../lib/enterpriseTasks";
import { formatDateTime } from "../lib/formatters";

type BountyTaskListResponse = {
  records: BountyTaskItem[];
  total: number;
  page: number;
  size: number;
};

type BountyTaskItem = {
  taskId: number;
  enterpriseUserId: number;
  enterpriseName: string;
  enterpriseLogoUrl: string | null;
  title: string;
  descriptionSummary: string;
  rewardDescription: string;
  status: string;
  submissionCount: number;
  acceptedSubmissionId: number | null;
  submittedByMe: boolean;
  deadlineAt: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};

type StatusFilter = "ALL" | "OPEN" | "CLOSED";

const PAGE_SIZE = 8;
// 列表页按查询条件分别缓存，返回大厅时可以先恢复同一筛选快照。
const BOUNTY_LIST_SNAPSHOT_STORAGE_KEY_PREFIX = "bishe.student.bounty.list.snapshot.v1";

type BountyListQuerySnapshot = {
  version: 1;
  userId: number;
  queryKey: string;
  savedAt: string;
  listData: BountyTaskListResponse;
};

function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function buildErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiClientError && error.message) {
    return error.traceId ? `${error.message}（traceId: ${error.traceId}）` : error.message;
  }
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return fallback;
}

function getTaskStatusClassName(task: Pick<BountyTaskItem, "status" | "acceptedSubmissionId" | "deadlineAt">) {
  const uiStatus = getEnterpriseTaskUiStatus(task);
  switch (uiStatus) {
    case "即将截止":
      return "border-amber-200 bg-amber-50 text-amber-700";
    case "已完成筛选":
      return "border-emerald-200 bg-emerald-50 text-emerald-700";
    case "已结束":
      return "border-slate-200 bg-slate-100 text-slate-600";
    case "进行中":
    default:
      return "border-indigo-200 bg-indigo-50 text-indigo-700";
  }
}

function getPageItems(currentPage: number, totalPages: number) {
  if (totalPages <= 5) {
    return Array.from({ length: totalPages }, (_, index) => index + 1);
  }

  const pages = new Set<number>([1, totalPages, currentPage]);
  if (currentPage - 1 > 1) {
    pages.add(currentPage - 1);
  }
  if (currentPage + 1 < totalPages) {
    pages.add(currentPage + 1);
  }

  const sortedPages = Array.from(pages).sort((left, right) => left - right);
  const items: Array<number | string> = [];

  sortedPages.forEach((page, index) => {
    items.push(page);
    const nextPage = sortedPages[index + 1];
    if (nextPage && nextPage - page > 1) {
      items.push("...");
    }
  });

  return items;
}

function buildBountyListSnapshotQueryKey(params: {
  keyword: string;
  status: StatusFilter;
  page: number;
}) {
  // queryKey 只保留影响列表结果的字段，避免输入框草稿污染缓存命中。
  return JSON.stringify({
    keyword: params.keyword.trim(),
    status: params.status,
    page: params.page,
  });
}

function getBountyListSnapshotStorage() {
  if (typeof window === "undefined") {
    return null;
  }

  return window.sessionStorage;
}

function getBountyListSnapshotStorageKey(userId: number, queryKey: string) {
  return `${BOUNTY_LIST_SNAPSHOT_STORAGE_KEY_PREFIX}.${userId}.${encodeURIComponent(queryKey)}`;
}

function readBountyListSnapshot(
  userId: number | null,
  params: {
    keyword: string;
    status: StatusFilter;
    page: number;
  },
): BountyListQuerySnapshot | null {
  if (typeof userId !== "number") {
    return null;
  }

  const storage = getBountyListSnapshotStorage();
  if (!storage) {
    return null;
  }

  const queryKey = buildBountyListSnapshotQueryKey(params);
  const rawValue = storage.getItem(getBountyListSnapshotStorageKey(userId, queryKey));
  if (!rawValue) {
    return null;
  }

  try {
    const parsed = JSON.parse(rawValue) as Partial<BountyListQuerySnapshot>;
    // 快照必须同时匹配用户和查询条件，防止跨账号或跨筛选串数据。
    if (
      parsed.version !== 1
      || parsed.userId !== userId
      || parsed.queryKey !== queryKey
      || !parsed.listData
      || !Array.isArray(parsed.listData.records)
      || typeof parsed.listData.total !== "number"
      || typeof parsed.listData.page !== "number"
      || typeof parsed.listData.size !== "number"
    ) {
      storage.removeItem(getBountyListSnapshotStorageKey(userId, queryKey));
      return null;
    }

    return {
      version: 1,
      userId,
      queryKey,
      savedAt: typeof parsed.savedAt === "string" ? parsed.savedAt : new Date().toISOString(),
      listData: parsed.listData as BountyTaskListResponse,
    };
  } catch {
    storage.removeItem(getBountyListSnapshotStorageKey(userId, queryKey));
    return null;
  }
}

function writeBountyListSnapshot(
  userId: number | null,
  params: {
    keyword: string;
    status: StatusFilter;
    page: number;
  },
  listData: BountyTaskListResponse,
) {
  if (typeof userId !== "number") {
    return;
  }

  const storage = getBountyListSnapshotStorage();
  if (!storage) {
    return;
  }

  const queryKey = buildBountyListSnapshotQueryKey(params);
  const snapshot: BountyListQuerySnapshot = {
    version: 1,
    userId,
    queryKey,
    savedAt: new Date().toISOString(),
    listData,
  };

  storage.setItem(getBountyListSnapshotStorageKey(userId, queryKey), JSON.stringify(snapshot));
}

export default function StudentBountyListPage() {
  const { role, userId, displayName } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();

  const [keywordInput, setKeywordInput] = useState(searchParams.get("keyword") || "");
  const [appliedKeyword, setAppliedKeyword] = useState(searchParams.get("keyword") || "");
  const [draftStatusFilter, setDraftStatusFilter] = useState<StatusFilter>(() => {
    const rawValue = searchParams.get("status");
    return rawValue === "OPEN" || rawValue === "CLOSED" ? rawValue : "ALL";
  });
  const [statusFilter, setStatusFilter] = useState<StatusFilter>(() => {
    const rawValue = searchParams.get("status");
    return rawValue === "OPEN" || rawValue === "CLOSED" ? rawValue : "ALL";
  });
  const [page, setPage] = useState(Math.max(1, Number(searchParams.get("page") || "1")));
  const [listData, setListData] = useState<BountyTaskListResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [refreshSeed, setRefreshSeed] = useState(0);

  const totalPages = useMemo(
    () => Math.max(1, Math.ceil((listData?.total ?? 0) / PAGE_SIZE)),
    [listData?.total],
  );
  const pageItems = useMemo(() => getPageItems(page, totalPages), [page, totalPages]);
  const currentFilterLabel = statusFilter === "ALL" ? "全部任务" : statusFilter === "OPEN" ? "开放中" : "已关闭";
  const currentPageCount = listData?.records.length ?? 0;

  useEffect(() => {
    // URL 只同步已应用的搜索条件，刷新或通知回流后能恢复当前列表位置。
    const nextParams = new URLSearchParams();
    if (appliedKeyword.trim()) {
      nextParams.set("keyword", appliedKeyword.trim());
    }
    if (statusFilter !== "ALL") {
      nextParams.set("status", statusFilter);
    }
    if (page > 1) {
      nextParams.set("page", String(page));
    }

    startTransition(() => {
      setSearchParams(nextParams, { replace: true });
    });
  }, [appliedKeyword, page, setSearchParams, statusFilter]);

  useEffect(() => {
    if (role !== "STUDENT") {
      return;
    }

    let active = true;
    // 先回显同查询快照，再向后端确认任务状态和提交数量。
    const snapshot = readBountyListSnapshot(userId, {
      keyword: appliedKeyword,
      status: statusFilter,
      page,
    });

    setListData(snapshot?.listData ?? null);
    setErrorMessage(null);
    setLoading(true);

    // 学生大厅只拉学生可见任务，statusFilter 交给后端按任务开放状态过滤。
    void apiRequest<BountyTaskListResponse>(`/bounty/tasks${buildQuery({
      page,
      size: PAGE_SIZE,
      keyword: appliedKeyword.trim() || undefined,
      status: statusFilter === "ALL" ? undefined : statusFilter,
    })}`)
      .then((response) => {
        if (!active) {
          return;
        }
        setListData(response);
        setErrorMessage(null);
        writeBountyListSnapshot(userId, {
          keyword: appliedKeyword,
          status: statusFilter,
          page,
        }, response);
      })
      .catch((error) => {
        if (!active) {
          return;
        }
        // 有快照时保留旧列表，避免短暂网络失败把任务大厅清空。
        if (snapshot?.listData) {
          setListData(snapshot.listData);
          setErrorMessage(null);
          return;
        }

        setListData(null);
        setErrorMessage(buildErrorMessage(error, "加载实战任务失败"));
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [appliedKeyword, page, refreshSeed, role, statusFilter, userId]);

  return (
    <div className="min-h-screen bg-[#eef3ff] text-slate-900">
      <div className="pointer-events-none fixed inset-0 z-0 bg-[radial-gradient(circle_at_top,rgba(79,70,229,0.14),transparent_30%),radial-gradient(circle_at_bottom_right,rgba(20,184,166,0.12),transparent_28%),linear-gradient(180deg,#eff6ff_0%,#f8fafc_58%,#f8fafc_100%)]" />
      <StudentWorkspaceNav
        displayName={displayName}
        activeKey="bounty"
        sectionLabel="Enterprise Practice"
        title="企业实战任务"
        extraAction={(
          <Link
            to="/consult/orders"
            className="hidden rounded-full border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-600 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600 md:inline-flex"
          >
            我的咨询单
          </Link>
        )}
      />

      <main className="page-enter-float relative z-10 mx-auto w-full max-w-[96rem] px-4 py-8 sm:px-6 lg:px-8">
        <section className="grid gap-4 xl:grid-cols-[1.8fr_1fr]">
          <div className="relative overflow-hidden rounded-[2.25rem] bg-gradient-to-br from-indigo-950 via-indigo-900 to-violet-900 px-6 py-8 text-white shadow-[0_24px_58px_rgba(49,46,129,0.25)] lg:px-10 lg:py-10">
            <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_right,rgba(255,255,255,0.1),transparent_40%)]" />
            <div className="absolute -right-10 -bottom-10 opacity-10 rotate-12">
              <BriefcaseBusiness size={200} />
            </div>

            <div className="relative flex h-full flex-col justify-center">
              <div className="inline-flex items-center gap-2 rounded-full border border-white/20 bg-white/10 px-3.5 py-1.5 text-xs tracking-[0.2em] text-indigo-100">
                <Target size={14} />
                ENTERPRISE BOUNTY
              </div>
              <h1 className="mt-4 text-3xl font-semibold tracking-tight leading-tight text-white md:text-4xl lg:text-5xl">
                企业实战任务
              </h1>
              <p className="mt-4 max-w-xl text-base leading-relaxed text-indigo-100/90 lg:text-lg">
                浏览企业发布的真实小型实战场景。按要求提交你的产出成果，即可获得奖金或继续接触机会。平台只承接轻量任务闭环，用成果说话。
              </p>
              <div className="mt-8 flex items-center gap-4">
                <button
                  type="button"
                  onClick={() => {
                    document.getElementById("bounty-filter-bar")?.scrollIntoView({ behavior: "smooth", block: "center" });
                  }}
                  className="rounded-full bg-white px-6 py-3 text-sm font-bold text-indigo-900 transition-all hover:-translate-y-0.5 hover:bg-indigo-50 hover:shadow-lg"
                >
                  浏览任务后再决定是否提交
                </button>
              </div>
            </div>
          </div>

          <div className="flex flex-col gap-4">
            <div className="flex-1 rounded-[2rem] border border-white/70 bg-white/80 p-6 shadow-sm backdrop-blur-xl">
              <div className="flex items-baseline gap-3">
                <span className="text-4xl font-black tracking-tight text-slate-900 lg:text-5xl">{listData?.total ?? "—"}</span>
                <span className="text-sm font-medium text-slate-500">个任务总量</span>
              </div>
              <div className="mt-4 inline-flex items-center gap-2 text-sm font-medium text-indigo-600">
                <span className="relative flex h-2.5 w-2.5">
                  <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-indigo-400 opacity-75" />
                  <span className="relative inline-flex h-2.5 w-2.5 rounded-full bg-indigo-500" />
                </span>
                当前筛选：{currentFilterLabel}
              </div>
            </div>

            <div className="flex-1 rounded-[2rem] border border-white/70 bg-slate-50/80 p-6 shadow-sm backdrop-blur-xl">
              <div className="flex items-baseline gap-3">
                <span className="text-3xl font-black tracking-tight text-slate-700 lg:text-4xl">{currentPageCount}</span>
                <span className="text-sm font-medium text-slate-500">个当前页结果</span>
              </div>
              <div className="mt-3 text-xs leading-relaxed text-slate-500">
                当前第 {page} 页 / 共 {totalPages} 页。
                <br />
                正在浏览{currentFilterLabel}，先看要求，再决定是否承接。
              </div>
            </div>
          </div>
        </section>

        <section id="bounty-filter-bar" className="sticky top-20 z-40 mt-6 rounded-[1.5rem] border border-white/80 bg-white/70 p-3 shadow-[0_8px_30px_rgba(0,0,0,0.04)] backdrop-blur-xl">
          <div className="flex flex-col items-center justify-between gap-4 sm:flex-row">
            <div className="flex w-full items-center rounded-xl bg-slate-100/80 p-1 sm:w-auto">
              {([
                { value: "ALL", label: "全部任务" },
                { value: "OPEN", label: "开放中" },
                { value: "CLOSED", label: "已关闭" },
              ] as Array<{ value: StatusFilter; label: string }>).map((option) => (
                <button
                  key={option.value}
                  type="button"
                  onClick={() => setDraftStatusFilter(option.value)}
                  className={joinClasses(
                    "flex-1 rounded-lg px-4 py-2 text-sm font-medium transition-all duration-200 sm:flex-none",
                    draftStatusFilter === option.value
                      ? "bg-white text-slate-900 shadow-sm"
                      : "text-slate-500 hover:text-slate-700",
                  )}
                >
                  {option.label}
                </button>
              ))}
            </div>

            <div className="flex w-full flex-wrap items-center gap-3 sm:w-auto">
              <div className="relative w-full sm:w-64">
                <Search size={18} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  value={keywordInput}
                  onChange={(event) => setKeywordInput(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter") {
                      setPage(1);
                      setAppliedKeyword(keywordInput);
                      setStatusFilter(draftStatusFilter);
                    }
                  }}
                  placeholder="搜索任务或企业名称..."
                  className="w-full rounded-xl border-transparent bg-white/80 py-2.5 pl-10 pr-4 text-sm text-slate-900 shadow-sm ring-1 ring-inset ring-slate-200 transition-all focus:bg-white focus:outline-none focus:ring-2 focus:ring-indigo-600"
                />
              </div>
              <button
                type="button"
                onClick={() => {
                  setPage(1);
                  setAppliedKeyword(keywordInput);
                  setStatusFilter(draftStatusFilter);
                }}
                className="inline-flex h-10 flex-1 items-center justify-center rounded-xl bg-slate-900 px-5 text-sm font-semibold text-white shadow-sm transition-all hover:-translate-y-0.5 hover:bg-indigo-600 sm:flex-none"
              >
                应用筛选
              </button>
              <button
                type="button"
                onClick={() => setRefreshSeed((current) => current + 1)}
                className="inline-flex h-10 w-10 items-center justify-center rounded-xl bg-white text-slate-500 shadow-sm ring-1 ring-inset ring-slate-200 transition-colors hover:bg-slate-50 hover:text-indigo-600"
                title="刷新列表"
              >
                <RefreshCw size={18} className={loading ? "animate-spin" : ""} />
              </button>
            </div>
          </div>
        </section>

        {errorMessage ? (
          <section className="mt-6 rounded-[2rem] border border-dashed border-rose-200 bg-rose-50/40 px-6 py-10 text-center">
            <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full border border-rose-200/50 bg-rose-100 text-rose-500 shadow-sm">
              <AlertCircle size={28} />
            </div>
            <h2 className="mt-4 text-xl font-semibold text-slate-900">列表加载失败</h2>
            <p className="mx-auto mt-2 max-w-xl text-sm leading-7 text-slate-500">{errorMessage}</p>
            <button
              type="button"
              onClick={() => setRefreshSeed((current) => current + 1)}
              className="mt-6 inline-flex items-center gap-2 rounded-full bg-white px-6 py-2.5 text-sm font-semibold text-slate-700 shadow-sm ring-1 ring-inset ring-slate-200 transition-all hover:-translate-y-0.5 hover:bg-slate-50 hover:text-indigo-600"
            >
              <RefreshCw size={16} />
              重新加载
            </button>
          </section>
        ) : null}

        <section className="mt-6">
          {loading && !listData?.records.length ? (
            <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-3">
              {Array.from({ length: 6 }).map((_, index) => (
                <div key={index} className="h-[268px] rounded-[1.9rem] border border-white/80 bg-white/75 shadow-[0_18px_45px_rgba(148,163,184,0.10)]" />
              ))}
            </div>
          ) : listData?.records.length ? (
            <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-3">
              {listData.records.map((task) => (
                <article
                  key={task.taskId}
                  className="group flex h-full flex-col overflow-hidden rounded-[1.6rem] border border-white/80 bg-white/85 p-5 shadow-[0_12px_30px_rgba(148,163,184,0.10)] backdrop-blur-xl transition-shadow hover:shadow-[0_20px_40px_rgba(148,163,184,0.18)]"
                >
                  <div className="mb-3 flex items-start justify-between gap-4">
                    <div className="flex items-center gap-2 text-sm font-medium text-slate-500">
                      <EnterpriseIdentityLogo
                        companyName={task.enterpriseName}
                        logoUrl={task.enterpriseLogoUrl}
                        className="h-9 w-9 rounded-[0.95rem] shadow-sm"
                        imageClassName="p-0"
                        fallbackClassName="bg-slate-100 text-slate-600"
                        textClassName="text-sm"
                      />
                      {task.enterpriseName}
                    </div>
                    <div className="flex items-center gap-2">
                      {task.submittedByMe ? (
                        <span className="inline-flex items-center gap-1 rounded-full border border-emerald-100 bg-emerald-50 px-2 py-0.5 text-[11px] font-bold text-emerald-600">
                          <CheckCircle2 size={12} />
                          我已提交
                        </span>
                      ) : null}
                      <span className={joinClasses("shrink-0 rounded-full border px-3 py-1 text-xs font-semibold", getTaskStatusClassName(task))}>
                        {getEnterpriseTaskUiStatus(task)}
                      </span>
                    </div>
                  </div>

                  <h2 className="min-h-[3.5rem] text-lg font-bold leading-tight text-slate-900 transition-colors group-hover:text-indigo-600">
                    {task.title}
                  </h2>

                  <p className="mt-3 flex-grow line-clamp-2 text-sm leading-6 text-slate-500">
                    {task.descriptionSummary || "当前任务还没有补充摘要说明。"}
                  </p>

                  <div className="mt-4 flex items-start gap-2 rounded-xl border border-amber-100/50 bg-gradient-to-r from-amber-50 to-orange-50/50 px-3 py-2.5">
                    <Award size={18} className="mt-0.5 shrink-0 text-amber-500" />
                    <span className="text-sm font-semibold leading-snug text-amber-700">
                      {task.rewardDescription || "待企业补充"}
                    </span>
                  </div>

                  <div className="my-4 h-px w-full bg-gradient-to-r from-transparent via-slate-200 to-transparent" />

                  <div className="mt-auto flex items-end justify-between gap-4">
                    <div className="space-y-1.5">
                      <div className="flex items-center gap-1.5 text-xs text-slate-500">
                        <Clock3 size={14} className="text-slate-400" />
                        <span className={getEnterpriseTaskUiStatus(task) === "即将截止" ? "font-medium text-amber-600" : ""}>
                          截止：{task.deadlineAt ? formatDateTime(task.deadlineAt) : "未设置"}
                        </span>
                      </div>
                      <div className="flex items-center gap-4 text-xs text-slate-400">
                        <span className="flex items-center gap-1">
                          <Users size={14} />
                          已提交 {task.submissionCount} 份
                        </span>
                        <span className="hidden sm:inline">
                          更新于 {task.updatedAt ? formatDateTime(task.updatedAt) : "—"}
                        </span>
                      </div>
                    </div>

                    <Link
                      to={`/bounty/${task.taskId}`}
                      className={joinClasses(
                        "relative inline-flex shrink-0 items-center justify-center whitespace-nowrap rounded-full px-4 py-2 text-sm font-semibold transition-all duration-300",
                        task.submittedByMe
                          ? "border border-emerald-200 bg-white !text-emerald-600 hover:border-emerald-300 hover:bg-emerald-50"
                          : task.status === "CLOSED"
                            ? "bg-slate-100 !text-slate-500 hover:bg-slate-200"
                            : "bg-slate-900 !text-white hover:-translate-y-0.5 hover:bg-indigo-600 hover:shadow-md",
                      )}
                    >
                      {task.submittedByMe ? "查看进度" : "查看任务"}
                      {!task.submittedByMe && task.status !== "CLOSED" ? <ArrowRight size={14} className="ml-1.5 text-current" /> : null}
                    </Link>
                  </div>
                </article>
              ))}
            </div>
          ) : (
            <div className="rounded-[2rem] border border-dashed border-slate-200 bg-white/70 px-6 py-10 text-center shadow-[0_18px_45px_rgba(148,163,184,0.08)]">
              <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-indigo-50 text-indigo-400">
                <Sparkles size={28} />
              </div>
              <h2 className="mt-4 text-xl font-bold text-slate-900">当前筛选条件下没有任务</h2>
              <p className="mt-2 text-sm leading-7 text-slate-500">
                可以试着清空关键词、切回“全部任务”，或者稍后回来刷新看看有没有新的企业实战机会。
              </p>
            </div>
          )}
        </section>

        {!loading && listData?.records.length ? (
          <section className="mt-10 flex items-center justify-between border-t border-slate-200/50 pt-8">
            <div className="text-sm text-slate-500">
              显示 <span className="font-medium text-slate-900">{(page - 1) * PAGE_SIZE + 1}</span> 到{" "}
              <span className="font-medium text-slate-900">{(page - 1) * PAGE_SIZE + currentPageCount}</span> 项，共{" "}
              <span className="font-medium text-slate-900">{listData.total}</span> 项
            </div>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setPage((current) => Math.max(current - 1, 1))}
                disabled={page <= 1}
                className="inline-flex h-9 w-9 items-center justify-center rounded-lg border border-slate-200 bg-white/50 text-slate-400 transition-colors disabled:opacity-50"
              >
                {loading ? <Loader2 size={16} className="animate-spin" /> : "‹"}
              </button>
              {pageItems.map((item, index) => (
                <button
                  key={`${item}-${index}`}
                  type="button"
                  disabled={item === "..."}
                  onClick={() => {
                    if (typeof item === "number") {
                      setPage(item);
                    }
                  }}
                  className={joinClasses(
                    "inline-flex h-9 w-9 items-center justify-center rounded-lg text-sm font-medium transition-colors",
                    item === page
                      ? "bg-indigo-600 text-white shadow-sm"
                      : item === "..."
                        ? "cursor-default text-slate-400"
                        : "border border-slate-200 bg-white/50 text-slate-600 hover:border-indigo-200 hover:text-indigo-600",
                  )}
                >
                  {item}
                </button>
              ))}
              <button
                type="button"
                onClick={() => setPage((current) => Math.min(current + 1, totalPages))}
                disabled={page >= totalPages}
                className="inline-flex h-9 w-9 items-center justify-center rounded-lg border border-slate-200 bg-white/50 text-slate-400 transition-colors disabled:opacity-50"
              >
                ›
              </button>
            </div>
          </section>
        ) : null}
      </main>
    </div>
  );
}
