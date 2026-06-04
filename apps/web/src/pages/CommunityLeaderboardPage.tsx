import {
  ArrowLeft,
  Trophy,
  User,
} from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import CommunityEmptyState from "../components/community/CommunityEmptyState";
import CommunityModuleLayout from "../components/community/CommunityModuleLayout";
import CommunityToast, { type CommunityToastState } from "../components/community/CommunityToast";
import {
  getCommunityLeaderboard,
  type CommunityLeaderboardItem,
} from "../lib/community";
import { formatCount, formatDateTime } from "../lib/formatters";
import { ApiClientError } from "../lib/apiClient";
import { joinClasses } from "../components/community/communityUtils";
import {
  buildStudentPublicProfileHref,
  cacheStudentProfileFromLeaderboard,
} from "../lib/studentPublicProfile";

function toUserMessage(error: unknown, fallback: string) {
  if (error instanceof ApiClientError) {
    return error.message || fallback;
  }
  if (error instanceof Error) {
    return error.message || fallback;
  }
  return fallback;
}

export default function CommunityLeaderboardPage() {
  const { role, userId } = useAuth();

  const [records, setRecords] = useState<CommunityLeaderboardItem[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<CommunityToastState | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  const showToast = (text: string, tone: CommunityToastState["tone"] = "info") => {
    setToast({
      id: Date.now() + Math.random(),
      tone,
      text,
    });
  };

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);

    void getCommunityLeaderboard({ page: 1, size: 50, window: "7d" }).then((data) => {
      if (!active) {
        return;
      }
      setRecords(data.records);
      setTotal(data.total);
    }).catch((fetchError) => {
      if (!active) {
        return;
      }
      const message = toUserMessage(fetchError, "贡献榜暂时加载失败，请稍后再试。");
      setError(message);
      showToast(message, "error");
      setRecords([]);
      setTotal(0);
    }).finally(() => {
      if (!active) {
        return;
      }
      setLoading(false);
    });

    return () => {
      active = false;
    };
  }, [refreshKey]);

  const currentUserRow = records.find((item) => item.studentUserId === userId) ?? null;
  const previousRow = currentUserRow && currentUserRow.rank > 1
    ? records.find((item) => item.rank === currentUserRow.rank - 1) ?? null
    : null;
  const hasLeaderboardLoadFailure = !loading && !!error && records.length === 0;
  const profileHref = role === "MENTOR"
    ? "/mentor/profile"
    : role === "ENTERPRISE"
      ? "/enterprise/dashboard"
      : role === "ADMIN"
        ? "/admin/dashboard"
        : "/profile";
  const profileLabel = role === "MENTOR"
    ? "查看导师资料"
    : role === "STUDENT"
      ? "查看我的求职画像"
      : "返回当前工作台";

  return (
    <CommunityModuleLayout
      activeTab="leaderboard"
      topbarSectionLabel="Community Leaderboard"
      topbarTitle="社区贡献榜"
      title="近 7 天社区贡献榜"
      description="榜单强调的是近 7 天内可解释的公开贡献，而不是单纯卷互动量。你可以通过发帖、评论和获赞，看到自己在社区中的持续沉淀。"
      stats={[
        {
          label: "榜单人数",
          value: loading ? "—" : formatCount(total),
          hint: "当前只展示近 7 天有有效公开贡献的学生用户。",
          accentClassName: "text-indigo-600",
        },
        {
          label: "我的定位",
          value: role === "STUDENT"
            ? currentUserRow ? `第 ${currentUserRow.rank} 名` : "暂未上榜"
            : "仅学生参与",
          hint: role === "STUDENT"
            ? currentUserRow
              ? `当前分数 ${formatCount(currentUserRow.score)}`
              : "继续分享与回复即可参与近 7 天榜单。"
            : "导师和企业本轮不进入榜单统计。",
          accentClassName: "text-emerald-500",
        },
      ]}
    >
      {loading ? (
        <div className="rounded-[1.9rem] border border-slate-100 bg-white px-6 py-16 text-center shadow-sm">
          正在加载贡献榜...
        </div>
      ) : hasLeaderboardLoadFailure ? (
        <CommunityEmptyState
          icon={Trophy}
          title="贡献榜暂时没有同步成功"
          description="榜单数据这会儿还没有成功刷新，你可以稍后再试，或先回讨论大厅继续参与社区。"
          action={(
            <button
              type="button"
              onClick={() => setRefreshKey((current) => current + 1)}
              className="rounded-full bg-slate-900 px-5 py-2.5 text-base font-semibold text-white transition-colors hover:bg-slate-800"
            >
              重新加载贡献榜
            </button>
          )}
        />
      ) : records.length === 0 ? (
        <CommunityEmptyState
          icon={Trophy}
          title="近 7 天暂时还没有形成榜单"
          description="当社区开始积累公开帖子、评论和获赞后，这里会自动生成可解释的贡献排序。"
          action={(
            <Link
              to="/community"
              className="rounded-full bg-slate-900 px-5 py-2.5 text-base font-semibold !text-white transition-colors hover:bg-slate-800 hover:!text-white visited:!text-white"
            >
              返回讨论大厅
            </Link>
          )}
        />
      ) : (
        <div className="grid items-start gap-6 lg:grid-cols-12">
          <div className="space-y-6 lg:col-span-8">
            <div className="rounded-[1.9rem] border border-slate-100 bg-white px-6 py-6 shadow-sm">
              <div className="flex items-center gap-3">
                <Trophy size={26} className="text-amber-500" />
                <div className="flex min-h-[3.25rem] items-center">
                  <h2 className="text-2xl font-black leading-tight text-slate-950">榜单公式说明</h2>
                </div>
              </div>
              <p className="mt-3 text-base leading-8 text-slate-500">
                最近 7 天按公式 <strong className="font-semibold text-slate-800">发帖*5 + 评论*2 + 获赞*1</strong> 计算。
                目的是鼓励有信息密度的分享和可复用的帮助，而不是追求无意义刷量。
              </p>
              <div className="mt-4 flex flex-wrap gap-3 text-sm font-semibold text-slate-600">
                <span className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1.5">发帖 +5</span>
                <span className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1.5">评论 +2</span>
                <span className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1.5">获赞 +1</span>
              </div>
            </div>

            <div className="overflow-hidden rounded-[1.9rem] border border-slate-100 bg-white shadow-sm">
              <table className="w-full text-left text-base">
                <thead className="bg-slate-50 text-slate-500">
                  <tr>
                    <th className="px-6 py-4 font-semibold">排名</th>
                    <th className="px-6 py-4 font-semibold">用户</th>
                    <th className="px-6 py-4 text-right font-semibold">综合分</th>
                    <th className="px-6 py-4 text-right font-semibold">发帖</th>
                    <th className="px-6 py-4 text-right font-semibold">评论</th>
                    <th className="px-6 py-4 text-right font-semibold">获赞</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-50">
                  {records.map((row) => {
                    const highlight = row.studentUserId === userId;
                    return (
                      <tr
                        key={row.studentUserId}
                        className={joinClasses(
                          "transition-colors hover:bg-slate-50/60",
                          highlight && "bg-indigo-50/60 hover:bg-indigo-50",
                        )}
                      >
                        <td className="px-6 py-4">
                          <div
                            className={joinClasses(
                              "flex h-9 w-9 items-center justify-center rounded-full font-bold",
                              row.rank === 1 && "bg-amber-100 text-amber-600",
                              row.rank === 2 && "bg-slate-200 text-slate-700",
                              row.rank === 3 && "bg-orange-100 text-orange-600",
                              row.rank > 3 && "bg-slate-100 text-slate-500",
                            )}
                          >
                            {row.rank}
                          </div>
                        </td>
                        <td className="px-6 py-4">
                          <div className="flex flex-wrap items-center gap-2">
                            <Link
                              to={buildStudentPublicProfileHref(row.studentUserId)}
                              onClick={() => {
                                cacheStudentProfileFromLeaderboard({
                                  studentUserId: row.studentUserId,
                                  displayName: row.displayName,
                                  leaderboardRank: row.rank,
                                  communityScore7d: row.score,
                                  postCount: row.postCount,
                                  commentCount: row.commentCount,
                                  likeReceivedCount: row.likeReceivedCount,
                                  latestActivityAt: row.latestActivityAt,
                                });
                              }}
                              className={joinClasses(
                                "font-bold transition-colors hover:text-indigo-600",
                                highlight ? "text-indigo-700" : "text-slate-900",
                              )}
                            >
                              {row.displayName}
                            </Link>
                            {highlight ? (
                              <span className="rounded-full border border-indigo-200 bg-indigo-50 px-2.5 py-1 text-xs font-semibold text-indigo-700">
                                我
                              </span>
                            ) : null}
                          </div>
                          <div className="mt-1 text-sm text-slate-400">
                            最近活跃：{formatDateTime(row.latestActivityAt)}
                          </div>
                        </td>
                        <td className="px-6 py-4 text-right font-black text-indigo-600">{formatCount(row.score)}</td>
                        <td className="px-6 py-4 text-right text-slate-500">{formatCount(row.postCount)}</td>
                        <td className="px-6 py-4 text-right text-slate-500">{formatCount(row.commentCount)}</td>
                        <td className="px-6 py-4 text-right text-slate-500">{formatCount(row.likeReceivedCount)}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>

          <aside className="space-y-6 lg:col-span-4 lg:sticky lg:top-[92px]">
            <div className="relative overflow-hidden rounded-[1.9rem] bg-indigo-600 px-6 py-6 text-white shadow-lg">
              <div className="absolute right-0 bottom-0 translate-x-4 translate-y-4 opacity-10">
                <User size={120} />
              </div>
              <div className="relative">
                <h3 className="text-3xl font-black">
                  {role === "STUDENT"
                    ? currentUserRow
                      ? `第 ${currentUserRow.rank} 名`
                      : "暂未上榜"
                    : "仅学生参与"}
                </h3>
                <div className="mt-4 rounded-[1.3rem] border border-white/10 bg-white/10 px-4 py-4 text-base leading-8 text-indigo-100">
                  {role !== "STUDENT"
                    ? "导师和企业本轮不会进入贡献榜统计，但仍欢迎你们继续参与社区答疑与治理。"
                    : currentUserRow
                      ? previousRow
                        ? `距离上一名还差 ${formatCount(previousRow.score - currentUserRow.score)} 分。继续补充高质量帖子或评论，就有机会往前再冲一档。`
                        : "你已经在榜单最前列了，继续保持真实且高质量的分享。"
                      : "你暂时还没有进入近 50 名，继续分享经验和回复讨论就会逐步积累贡献分。"}
                </div>
              </div>
            </div>

            <Link
              to="/community"
              className="inline-flex w-full items-center justify-center gap-2 rounded-full bg-slate-900 px-4 py-3 text-base font-semibold !text-white transition-colors hover:bg-slate-800 hover:!text-white visited:!text-white"
            >
              <ArrowLeft size={15} />
              返回讨论大厅继续贡献
            </Link>

            <Link
              to={profileHref}
              className="inline-flex w-full items-center justify-center gap-2 rounded-full bg-slate-900 px-4 py-3 text-base font-semibold !text-white transition-colors hover:bg-slate-800 hover:!text-white visited:!text-white"
            >
              <User size={15} />
              {profileLabel}
            </Link>
          </aside>
        </div>
      )}
      <CommunityToast
        toast={toast}
        onClose={(toastId) => {
          setToast((current) => (current?.id === toastId ? null : current));
        }}
      />
    </CommunityModuleLayout>
  );
}
