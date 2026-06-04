import {
  AlertCircle,
  ArrowLeft,
  Bot,
  CornerDownRight,
  Flag,
  GraduationCap,
  Lightbulb,
  Loader2,
  Lock,
  MessageSquare,
  ShieldCheck,
  Sparkles,
  ThumbsUp,
} from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import CommunityAuthorIdentity from "../components/community/CommunityAuthorIdentity";
import CommunityEmptyState from "../components/community/CommunityEmptyState";
import CommunityMarkdownComposer from "../components/community/CommunityMarkdownComposer";
import CommunityMarkdownRenderer from "../components/community/CommunityMarkdownRenderer";
import CommunityModerationBanner from "../components/community/CommunityModerationBanner";
import CommunityModuleLayout from "../components/community/CommunityModuleLayout";
import CommunityToast, { type CommunityToastState } from "../components/community/CommunityToast";
import {
  createCommunityComment,
  createCommunityReport,
  ensureCommunityAiDraftMarkdown,
  generateCommunityPreAnswer,
  getCommunityPostDetail,
  likeCommunityPost,
  unlikeCommunityPost,
  updateCommunityPostStatus,
  type CommunityComment,
  type CommunityPostDetail,
} from "../lib/community";
import { formatDateTime, formatCount } from "../lib/formatters";
import { ApiClientError } from "../lib/apiClient";
import { buildMentorPublicDetailHref } from "../lib/mentorPublicProfile";
import {
  buildStudentPublicProfileHref,
  cacheStudentProfileFromCommunityComment,
  cacheStudentProfileFromCommunityPost,
} from "../lib/studentPublicProfile";
import {
  formatRelativeTime,
  getModerationStatusMeta,
  getReportReasonLabel,
  getResolvedStatusMeta,
  getRoleMeta,
  getScenarioLabel,
  joinClasses,
} from "../components/community/communityUtils";

type ReportDraft = {
  targetType: "POST" | "COMMENT";
  targetId: string;
  title: string;
  excerpt: string;
};

const REPORT_REASON_OPTIONS = [
  { code: "ABUSE", label: "人身攻击" },
  { code: "SPAM", label: "广告引流" },
  { code: "MISLEADING", label: "不实信息" },
  { code: "RISK_LINK", label: "风险链接" },
  { code: "EXPLICIT", label: "低俗违规" },
  { code: "OTHER", label: "其他" },
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

function truncateForQuery(value: string) {
  return value.length > 280 ? `${value.slice(0, 279)}…` : value;
}

function CommentCard({
  comment,
  postId,
  postTitle,
  canReport,
  onReport,
  highlighted,
}: {
  comment: CommunityComment;
  postId: number;
  postTitle: string;
  canReport: boolean;
  onReport: (draft: ReportDraft) => void;
  highlighted: boolean;
}) {
  const { role } = useAuth();
  const roleMeta = getRoleMeta(comment.role, comment.ai);
  const profileHref = !comment.ai && comment.role === "STUDENT"
    ? buildStudentPublicProfileHref(comment.userId)
    : !comment.ai && role === "STUDENT" && comment.role === "MENTOR"
      ? buildMentorPublicDetailHref(comment.userId, "community")
      : null;

  return (
    <div
      id={`comment-${comment.commentId}`}
      className={joinClasses(
        "scroll-mt-24 rounded-[1.6rem] border border-slate-100 bg-white px-5 py-5 shadow-sm transition-shadow",
        highlighted && "border-indigo-200 shadow-[0_0_0_4px_rgba(99,102,241,0.12)]",
      )}
    >
      <div className="flex items-start justify-between gap-4">
        <CommunityAuthorIdentity
          userId={comment.userId}
          displayName={comment.ai ? "AI 助手" : comment.displayName}
          realName={comment.ai ? null : comment.realName ?? null}
          showRealName={comment.ai ? false : comment.showRealName ?? false}
          role={comment.role}
          avatarUrl={comment.ai ? null : comment.avatarUrl ?? null}
          ai={comment.ai}
          timeLabel={formatRelativeTime(comment.createdAt)}
          size="sm"
          profileHref={profileHref}
          onProfileClick={() => {
            if (!comment.ai && comment.role === "STUDENT") {
              cacheStudentProfileFromCommunityComment({
                studentUserId: comment.userId,
                displayName: comment.displayName,
                postId,
                postTitle,
                createdAt: comment.createdAt,
              });
            }
          }}
        />
        {canReport ? (
          <button
            type="button"
            onClick={() => onReport({
              targetType: "COMMENT",
              targetId: String(comment.commentId),
              title: `评论 · ${comment.ai ? "AI 助手" : comment.displayName}`,
              excerpt: comment.content,
            })}
            className="inline-flex items-center gap-1 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-sm font-semibold text-slate-500 transition-colors hover:border-rose-200 hover:text-rose-600"
          >
            <Flag size={12} />
            举报
          </button>
        ) : null}
      </div>

      <div className="mt-4 rounded-[1.3rem] bg-slate-50/80 px-4 py-4">
        <CommunityMarkdownRenderer content={comment.content} className="space-y-3" />
      </div>

      <div className="mt-4 flex items-center gap-2 text-sm font-semibold">
        <span className={joinClasses("rounded-full px-2.5 py-1", roleMeta.badgeClassName)}>
          {roleMeta.label}
        </span>
        <span className="text-slate-400">关联主帖 #{postId}</span>
      </div>
    </div>
  );
}

type CommunityPostDetailWorkspaceProps = {
  role: string | null;
  userId: number | null;
};

function CommunityPostDetailWorkspace({ role, userId }: CommunityPostDetailWorkspaceProps) {
  const { postId } = useParams();
  const navigate = useNavigate();
  // 管理员从内容治理进入详情页时只做上下文核查，不开放互动动作。
  const isAdminViewer = role === "ADMIN";
  const adminReturnHref = "/admin/content?tab=reports";

  const numericPostId = Number(postId);
  // 评论通知会携带 hash，详情加载完成后再滚动到目标评论。
  const targetCommentAnchor = typeof window !== "undefined" && window.location.hash.startsWith("#comment-")
    ? decodeURIComponent(window.location.hash.slice(1))
    : null;

  const [post, setPost] = useState<CommunityPostDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<CommunityToastState | null>(null);

  const [replyContent, setReplyContent] = useState("");
  const [replySubmitting, setReplySubmitting] = useState(false);
  const [likeSubmitting, setLikeSubmitting] = useState(false);
  const [statusSubmitting, setStatusSubmitting] = useState<string | null>(null);

  const [aiDraft, setAiDraft] = useState("");
  const [aiLoading, setAiLoading] = useState(false);

  const [reportDraft, setReportDraft] = useState<ReportDraft | null>(null);
  const [reportReason, setReportReason] = useState("ABUSE");
  const [reportDetail, setReportDetail] = useState("");
  const [reportSubmitting, setReportSubmitting] = useState(false);

  const showToast = (text: string, tone: CommunityToastState["tone"] = "info") => {
    setToast({
      id: Date.now() + Math.random(),
      tone,
      text,
    });
  };

  useEffect(() => {
    if (!Number.isFinite(numericPostId) || numericPostId <= 0) {
      const message = "帖子编号无效，无法打开讨论工作区。";
      setLoading(false);
      setError(message);
      showToast(message, "error");
      return;
    }

    let active = true;
    setLoading(true);
    setError(null);

    // 帖子详情包含评论、点赞、viewer 态和治理状态，互动权限都以此为准。
    void getCommunityPostDetail(numericPostId).then((data) => {
      if (!active) {
        return;
      }
      setPost(data);
      setAiDraft("");
    }).catch((fetchError) => {
      if (!active) {
        return;
      }
      const message = toUserMessage(fetchError, "帖子详情加载失败，请稍后再试。");
      setError(message);
      showToast(message, "error");
      setPost(null);
    }).finally(() => {
      if (!active) {
        return;
      }
      setLoading(false);
    });

    return () => {
      active = false;
    };
  }, [numericPostId]);

  useEffect(() => {
    if (!post || !targetCommentAnchor) {
      return;
    }

    // 等 DOM 完成渲染后再定位评论锚点，避免通知回流落不到目标行。
    window.requestAnimationFrame(() => {
      const targetElement = document.getElementById(targetCommentAnchor);
      if (targetElement) {
        targetElement.scrollIntoView({ behavior: "smooth", block: "center" });
      }
    });
  }, [post, targetCommentAnchor]);

  const isAuthor = post?.authorUserId === userId;
  const canGenerateAiDraft = role === "STUDENT" || role === "MENTOR";
  // 社区互动只对公开且未关闭的帖子开放，管理员只读模式不进入这些动作。
  const canReply = !!post && post.moderationStatus === "PASS" && post.resolvedStatus !== "CLOSED" && canGenerateAiDraft;
  const canManageStatus = !!post && post.moderationStatus === "PASS";
  const canMarkResolved = !!post && canManageStatus && post.resolvedStatus === "OPEN";
  const canReopen = !!post && canManageStatus && post.resolvedStatus !== "OPEN";
  const canClose = !!post && canManageStatus && post.resolvedStatus !== "CLOSED";
  const moderationMeta = getModerationStatusMeta(post?.moderationStatus);
  const resolvedMeta = getResolvedStatusMeta(post?.resolvedStatus);
  const authorProfileHref = post
    ? post.authorRole === "STUDENT"
      ? buildStudentPublicProfileHref(post.authorUserId)
      : role === "STUDENT" && post.authorRole === "MENTOR"
        ? buildMentorPublicDetailHref(post.authorUserId, "community")
        : null
    : null;
  const followupCta = role === "MENTOR"
    ? {
        description: "如果这条讨论还值得继续扩展，可以回到社区首页继续看同类问题，或继续补充你的导师答疑意见。",
        to: "/community",
        label: "回社区继续答疑",
        icon: MessageSquare,
      }
    : isAdminViewer
      ? {
          description: "当前为管理员只读查看模式。如需继续处理，可返回内容治理中心查看举报详情与处置动作。",
          to: adminReturnHref,
          label: "返回内容治理",
          icon: ShieldCheck,
        }
    : {
        description: "如果这条讨论已经暴露出更复杂的求职卡点，可以继续回到导师广场寻找更深度的一对一支持。",
        to: "/mentors",
        label: "去导师广场继续求助",
        icon: GraduationCap,
      };
  const FollowupCtaIcon = followupCta.icon;

  const refreshPost = async () => {
    if (!Number.isFinite(numericPostId) || numericPostId <= 0) {
      return;
    }
    // 写操作成功后重新拉详情，评论数、审核状态和可见评论都交给后端校准。
    const data = await getCommunityPostDetail(numericPostId);
    setPost(data);
  };

  const handleReplySubmit = async () => {
    if (!post || !replyContent.trim()) {
      return;
    }

    setReplySubmitting(true);
    try {
      // 回复也走内容治理，REVIEW 状态只提示待审，不直接假定公开。
      const response = await createCommunityComment(post.postId, { content: replyContent.trim() });
      setReplyContent("");
      await refreshPost();
      showToast(
        response.moderation?.action === "REVIEW"
          ? "回复已提交审核，审核通过后会公开显示在讨论流中。"
          : "回复已发送，讨论工作区已刷新到最新状态。",
        response.moderation?.action === "REVIEW" ? "info" : "success",
      );
    } catch (submitError) {
      showToast(toUserMessage(submitError, "回复发送失败，请稍后再试。"), "error");
    } finally {
      setReplySubmitting(false);
    }
  };

  const handleToggleLike = async () => {
    if (!post || post.moderationStatus !== "PASS") {
      return;
    }

    setLikeSubmitting(true);
    try {
      // 点赞用服务端返回数量覆盖本地，避免多端操作后计数漂移。
      const response = post.likedByMe
        ? await unlikeCommunityPost(post.postId)
        : await likeCommunityPost(post.postId);

      setPost({
        ...post,
        likedByMe: response.liked,
        likeCount: response.likeCount,
      });
    } catch (submitError) {
      showToast(toUserMessage(submitError, "点赞状态更新失败，请稍后再试。"), "error");
    } finally {
      setLikeSubmitting(false);
    }
  };

  const handleUpdateStatus = async (nextStatus: "OPEN" | "RESOLVED" | "CLOSED") => {
    if (!post) {
      return;
    }

    setStatusSubmitting(nextStatus);
    try {
      // 解决/关闭状态只影响讨论入口，不改变内容治理 moderationStatus。
      const response = await updateCommunityPostStatus(post.postId, nextStatus);
      setPost({
        ...post,
        resolvedStatus: response.resolvedStatus,
        updatedAt: response.updatedAt,
      });
      showToast(
        nextStatus === "RESOLVED"
          ? "帖子已标记为已解决，社区会以此为信号帮助后续同学快速判断价值。"
          : nextStatus === "CLOSED"
            ? "帖子已关闭，新的回复入口已同步关闭。"
            : "帖子已重新开放讨论。",
        "success",
      );
    } catch (submitError) {
      showToast(toUserMessage(submitError, "帖子状态更新失败，请稍后再试。"), "error");
    } finally {
      setStatusSubmitting(null);
    }
  };

  const handleGenerateAiDraft = async () => {
    if (!post) {
      return;
    }

    setAiLoading(true);
    try {
      // AI 只生成可编辑草稿，真正公开仍需用户手动发送回复。
      const response = await generateCommunityPreAnswer({ postId: post.postId });
      setAiDraft(ensureCommunityAiDraftMarkdown(response.draftComment));
      showToast("AI 已生成一版回复思路草稿，你可以直接引用或继续改写。", "success");
    } catch (submitError) {
      showToast(toUserMessage(submitError, "AI 辅助暂时不可用，请稍后再试。"), "error");
    } finally {
      setAiLoading(false);
    }
  };

  const handleSubmitReport = async () => {
    if (!reportDraft) {
      return;
    }

    setReportSubmitting(true);
    try {
      // 举报提交后直接跳到个人治理记录，便于回看后续处置状态。
      const response = await createCommunityReport({
        targetType: reportDraft.targetType,
        targetId: reportDraft.targetId,
        reasonCode: reportReason,
        detail: reportDetail.trim() || undefined,
      });
      setReportDraft(null);
      setReportDetail("");
      navigate(`/community/reports?reportId=${encodeURIComponent(String(response.reportId))}`);
    } catch (submitError) {
      showToast(toUserMessage(submitError, "举报提交失败，请稍后再试。"), "error");
    } finally {
      setReportSubmitting(false);
    }
  };

  return (
    <CommunityModuleLayout
      activeTab="feed"
      sectionLabel="Discussion Workspace"
      topbarSectionLabel="Discussion Detail"
      topbarTitle="讨论详情"
      brandHref={isAdminViewer ? adminReturnHref : "/community"}
      hideTabs={isAdminViewer}
      title={post?.title || "讨论工作区"}
      description={post
        ? (isAdminViewer ? "管理员可在此只读回看主帖与评论上下文，用于辅助举报判断与内容核查。" : "阅读主帖、回看上下文，并在同一空间里完成回复、AI 辅助和治理反馈。")
        : (isAdminViewer ? "管理员查看模式下可直接打开社区原帖，快速核对正文与评论上下文。" : "社区详情页会把主帖、评论、治理提示和 AI 辅助统一放进同一个讨论工作区。")}
      stats={[
        {
          label: "讨论数",
          value: post ? formatCount(post.commentCount) : "—",
          hint: "评论流与主帖正文保持同一上下文。",
          accentClassName: "text-indigo-600",
        },
        {
          label: "有帮助",
          value: post ? formatCount(post.likeCount) : "—",
          hint: "点赞只针对公开可见的主帖内容。",
          accentClassName: "text-emerald-500",
        },
      ]}
    >
      {loading ? (
        <div className="rounded-[1.9rem] border border-slate-100 bg-white px-6 py-16 text-center shadow-sm">
          <div className="inline-flex items-center gap-2 text-base font-semibold text-slate-500">
            <Loader2 size={16} className="animate-spin text-indigo-500" />
            正在加载讨论工作区...
          </div>
        </div>
      ) : error || !post ? (
        <CommunityEmptyState
          icon={AlertCircle}
          title="帖子详情暂时无法打开"
          description="这条讨论当前没有成功同步，你可以先回到讨论大厅，稍后再试。"
          action={(
            <Link
              to={isAdminViewer ? adminReturnHref : "/community"}
              className="rounded-full bg-slate-900 px-5 py-2.5 text-base font-semibold !text-white transition-colors hover:bg-slate-800 hover:!text-white visited:!text-white"
            >
              {isAdminViewer ? "返回内容治理" : "返回讨论大厅"}
            </Link>
          )}
        />
      ) : (
        <div className="grid items-start gap-6 lg:grid-cols-12">
          <div className="space-y-6 lg:col-span-8">
            <div className="flex items-center gap-3 text-base text-slate-500">
              <Link
                to={isAdminViewer ? adminReturnHref : "/community"}
                className="inline-flex items-center gap-1 rounded-full border border-slate-200 bg-white px-3 py-1.5 font-semibold text-slate-600 transition-colors hover:border-indigo-200 hover:text-indigo-600"
              >
                <ArrowLeft size={14} />
                {isAdminViewer ? "返回内容治理" : "返回大厅"}
              </Link>
              <span>{isAdminViewer ? "当前为管理员只读查看模式，用于核对原帖上下文。" : "当前路由承接帖子阅读、回复与治理反馈。"}</span>
            </div>

            <CommunityModerationBanner
              moderationStatus={post.moderationStatus}
              resolvedStatus={post.resolvedStatus}
              riskLevel={post.riskLevel}
            />

            <section className="rounded-[1.95rem] border border-slate-100 bg-white px-6 py-6 shadow-sm lg:px-8">
              <div className="flex flex-wrap items-start justify-between gap-4">
                <CommunityAuthorIdentity
                  userId={post.authorUserId}
                  displayName={post.authorDisplayName}
                  realName={post.authorRealName ?? null}
                  showRealName={post.authorShowRealName ?? false}
                  role={post.authorRole}
                  avatarUrl={post.authorAvatarUrl ?? null}
                  timeLabel={formatRelativeTime(post.createdAt)}
                  secondaryLabel={`场景 · ${getScenarioLabel(post.scenarioCode)}`}
                  authoredByMe={isAuthor}
                  profileHref={authorProfileHref}
                  onProfileClick={() => {
                    if (post.authorRole === "STUDENT") {
                      cacheStudentProfileFromCommunityPost({
                        studentUserId: post.authorUserId,
                        displayName: post.authorDisplayName,
                        title: post.title,
                        postId: post.postId,
                        scenarioLabel: getScenarioLabel(post.scenarioCode),
                        createdAt: post.createdAt,
                      });
                    }
                  }}
                />
                <div className="flex flex-wrap items-center gap-2">
                  <span className={joinClasses("rounded-full px-3 py-1.5 text-sm font-semibold", moderationMeta.className)}>
                    {moderationMeta.label}
                  </span>
                  <span className={joinClasses("rounded-full px-3 py-1.5 text-sm font-semibold", resolvedMeta.className)}>
                    {resolvedMeta.label}
                  </span>
                  {!isAuthor && !isAdminViewer ? (
                    <button
                      type="button"
                      onClick={() => setReportDraft({
                        targetType: "POST",
                        targetId: String(post.postId),
                        title: post.title,
                        excerpt: truncateForQuery(post.content),
                      })}
                      className="inline-flex items-center gap-1 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-sm font-semibold text-slate-600 transition-colors hover:border-rose-200 hover:text-rose-600"
                    >
                      <Flag size={12} />
                      举报
                    </button>
                  ) : null}
                </div>
              </div>

              <div className="mt-6 flex flex-wrap gap-2">
                <span className="inline-flex items-center rounded-full border border-slate-200 bg-slate-50 px-3 py-1.5 text-sm font-semibold text-slate-600">
                  {getScenarioLabel(post.scenarioCode)}
                </span>
                {post.tags.map((tag) => (
                  <span
                    key={tag}
                    className="rounded-full border border-indigo-100 bg-indigo-50 px-3 py-1.5 text-sm font-semibold text-indigo-600"
                  >
                    #{tag}
                  </span>
                ))}
              </div>

              <div className="mt-8 rounded-[1.6rem] bg-slate-50/70 px-5 py-5">
                <CommunityMarkdownRenderer content={post.content} className="space-y-5" emptyText="作者暂未补充正文内容。" />
              </div>

              <div className="mt-6 flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-5">
                <div className="flex flex-wrap items-center gap-3">
                  <button
                    type="button"
                    disabled={isAdminViewer || likeSubmitting || post.moderationStatus !== "PASS"}
                    onClick={() => void handleToggleLike()}
                    className={joinClasses(
                      "inline-flex items-center gap-2 rounded-full border px-4 py-2 text-base font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-60",
                      post.likedByMe
                        ? "border-orange-200 bg-orange-50 text-orange-600 hover:bg-orange-100"
                        : "border-slate-200 bg-white text-slate-600 hover:border-orange-200 hover:text-orange-600",
                    )}
                  >
                    {likeSubmitting ? <Loader2 size={15} className="animate-spin" /> : <ThumbsUp size={15} />}
                    有帮助 {formatCount(post.likeCount)}
                  </button>
                  <div className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-slate-50 px-4 py-2 text-base font-semibold text-slate-600">
                    <MessageSquare size={15} />
                    讨论 {formatCount(post.commentCount)}
                  </div>
                </div>
                <div className="text-sm text-slate-400">
                  最近更新时间：{formatDateTime(post.updatedAt || post.createdAt)}
                </div>
              </div>
            </section>

            <section className="space-y-4 rounded-[1.95rem] border border-slate-100 bg-white px-6 py-6 shadow-sm lg:px-8">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <div className="text-xs font-bold text-slate-400">Comments</div>
                  <h2 className="mt-1 text-xl font-black text-slate-900">全部讨论</h2>
                </div>
                <div className="text-base text-slate-500">
                  当前共 {formatCount(post.comments.length)} 条公开回复
                </div>
              </div>

              {post.comments.length > 0 ? (
                <div className="space-y-4">
                  {post.comments.map((comment) => (
                    <CommentCard
                      key={comment.commentId}
                      comment={comment}
                      postId={post.postId}
                      postTitle={post.title}
                      canReport={!comment.ai && !isAdminViewer}
                      onReport={setReportDraft}
                      highlighted={targetCommentAnchor === `comment-${comment.commentId}`}
                    />
                  ))}
                </div>
              ) : (
                <CommunityEmptyState
                  icon={MessageSquare}
                  title="这条讨论暂时还没有公开回复"
                  description="如果你已经有思路，可以在下方直接补充自己的观点，帮助题主更快形成下一步动作。"
                />
              )}
            </section>

            <section className="rounded-[1.95rem] border border-slate-100 bg-white px-6 py-6 shadow-sm lg:px-8">
              <div className="mb-4 flex items-center gap-2">
                <CornerDownRight size={16} className="text-indigo-500" />
                <div>
                  <div className="text-xs font-bold text-slate-400">Reply</div>
                  <h2 className="mt-1 text-xl font-black text-slate-900">继续参与讨论</h2>
                </div>
              </div>

              {canReply ? (
                <CommunityMarkdownComposer
                  value={replyContent}
                  onChange={setReplyContent}
                  placeholder="先补充你的判断，再给出可以执行的建议。必要时可以直接引用原帖中的关键背景。"
                  submitText="发送回复"
                  onSubmit={handleReplySubmit}
                  submitting={replySubmitting}
                />
              ) : (
                <div className="rounded-[1.5rem] border border-slate-200 bg-slate-50 px-4 py-5 text-base leading-8 text-slate-500">
                  {post.resolvedStatus === "CLOSED"
                    ? "当前讨论已关闭，无法继续回复。"
                    : "只有公开可见且未关闭的帖子支持继续回复。"}
                </div>
              )}
            </section>
          </div>

          <aside className="space-y-6 lg:col-span-4 lg:sticky lg:top-[92px]">
            <div className="rounded-[1.85rem] border border-indigo-100 bg-white px-5 py-5 shadow-sm">
              <div className="mb-3 flex items-center gap-2 text-base font-bold text-indigo-600">
                <Sparkles size={16} />
                {isAdminViewer ? "查看说明" : "AI 辅助建议"}
              </div>
              <p className="text-base leading-8 text-slate-600">
                {isAdminViewer
                  ? "当前页面仅用于管理员核对原帖与评论内容，不开放社区互动与 AI 回复草稿。"
                  : "这块不会伪装成真实社区回复，而是给你一版思路草稿，帮助你更快组织有效表达。"}
              </p>
              {isAdminViewer ? (
                <div className="mt-4 rounded-[1.3rem] border border-slate-200 bg-slate-50 px-4 py-4 text-base leading-8 text-slate-500">
                  如需继续处理这条内容，请回到内容治理页执行处置动作。
                </div>
              ) : aiDraft ? (
                <div className="mt-4 space-y-3">
                  <div className="rounded-[1.3rem] border border-indigo-100 bg-indigo-50/70 px-4 py-4">
                    <CommunityMarkdownRenderer content={aiDraft} className="space-y-3" />
                  </div>
                  <button
                    type="button"
                    onClick={() => setReplyContent((current) => (current.trim() ? `${current.trim()}\n\n${aiDraft}` : aiDraft))}
                    className="w-full rounded-full border border-indigo-200 bg-white px-4 py-2.5 text-base font-semibold text-indigo-600 transition-colors hover:bg-indigo-50"
                  >
                    填入回复编辑器
                  </button>
                </div>
              ) : (
                <button
                  type="button"
                  disabled={!canGenerateAiDraft || aiLoading}
                  onClick={() => void handleGenerateAiDraft()}
                  className="mt-4 inline-flex w-full items-center justify-center gap-2 rounded-full bg-indigo-600 px-4 py-2.5 text-base font-semibold text-white transition-colors hover:bg-indigo-700 disabled:cursor-not-allowed disabled:bg-slate-200 disabled:text-slate-400"
                >
                  {aiLoading ? <Loader2 size={15} className="animate-spin" /> : <Bot size={15} />}
                  生成一版 AI 回复草稿
                </button>
              )}
            </div>

            {isAuthor ? (
              <div className="rounded-[1.85rem] border border-slate-100 bg-white px-5 py-5 shadow-sm">
                <div className="mb-3 flex items-center gap-2 text-base font-bold text-slate-900">
                  <ShieldCheck size={16} className="text-emerald-500" />
                  帖子状态管理
                </div>
                <p className="text-base leading-8 text-slate-500">
                  作为作者，你可以显式标记讨论是否已解决，或在需要时关闭回复入口。
                </p>
                <div className="mt-4 flex flex-wrap gap-2">
                  <button
                    type="button"
                    disabled={statusSubmitting !== null || !canMarkResolved}
                    onClick={() => void handleUpdateStatus("RESOLVED")}
                    className="rounded-full border border-emerald-200 bg-emerald-50 px-4 py-2 text-base font-semibold text-emerald-700 transition-colors hover:bg-emerald-100 disabled:cursor-not-allowed disabled:border-slate-200 disabled:bg-slate-100 disabled:text-slate-400"
                  >
                    {statusSubmitting === "RESOLVED" ? "处理中..." : "标记已解决"}
                  </button>
                  <button
                    type="button"
                    disabled={statusSubmitting !== null || !canReopen}
                    onClick={() => void handleUpdateStatus("OPEN")}
                    className="rounded-full border border-sky-200 bg-sky-50 px-4 py-2 text-base font-semibold text-sky-700 transition-colors hover:bg-sky-100 disabled:cursor-not-allowed disabled:border-slate-200 disabled:bg-slate-100 disabled:text-slate-400"
                  >
                    {statusSubmitting === "OPEN" ? "处理中..." : "重新开放"}
                  </button>
                  <button
                    type="button"
                    disabled={statusSubmitting !== null || !canClose}
                    onClick={() => void handleUpdateStatus("CLOSED")}
                    className="rounded-full border border-slate-200 bg-slate-100 px-4 py-2 text-base font-semibold text-slate-700 transition-colors hover:bg-slate-200 disabled:cursor-not-allowed disabled:border-slate-200 disabled:bg-slate-100 disabled:text-slate-400"
                  >
                    {statusSubmitting === "CLOSED" ? "处理中..." : "关闭讨论"}
                  </button>
                </div>
              </div>
            ) : null}

            <div className="rounded-[1.85rem] border border-slate-100 bg-white px-5 py-5 shadow-sm">
              <div className="mb-3 flex items-center gap-2 text-base font-bold text-slate-900">
                <Lightbulb size={16} className="text-amber-500" />
                继续深挖问题
              </div>
              <p className="text-base leading-8 text-slate-500">
                {followupCta.description}
              </p>
              <Link
                to={followupCta.to}
                className="mt-4 inline-flex w-full items-center justify-center gap-2 rounded-full bg-slate-900 px-4 py-2.5 text-base font-semibold !text-white transition-colors hover:bg-slate-800 hover:!text-white visited:!text-white"
              >
                <FollowupCtaIcon size={15} className="shrink-0 !text-white" />
                <span className="!text-white">{followupCta.label}</span>
              </Link>
            </div>

            <div className="rounded-[1.85rem] border border-slate-100 bg-white px-5 py-5 shadow-sm">
              <div className="mb-3 flex items-center gap-2 text-base font-bold text-slate-900">
                <Lock size={16} className="text-slate-400" />
                帖子摘要
              </div>
              <div className="space-y-3 text-base leading-8 text-slate-600">
                <div className="flex items-center justify-between gap-3">
                  <span>创建时间</span>
                  <span className="font-semibold text-slate-900">{formatDateTime(post.createdAt)}</span>
                </div>
                <div className="flex items-center justify-between gap-3">
                  <span>治理状态</span>
                  <span className={joinClasses("rounded-full px-2.5 py-1 text-sm font-semibold", moderationMeta.className)}>
                    {moderationMeta.label}
                  </span>
                </div>
                <div className="flex items-center justify-between gap-3">
                  <span>讨论状态</span>
                  <span className={joinClasses("rounded-full px-2.5 py-1 text-sm font-semibold", resolvedMeta.className)}>
                    {resolvedMeta.label}
                  </span>
                </div>
              </div>
            </div>
          </aside>
        </div>
      )}

      {reportDraft ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 px-4 py-6 backdrop-blur-sm">
          <div className="w-full max-w-lg overflow-hidden rounded-[2rem] border border-white/10 bg-white shadow-2xl">
            <div className="flex items-center justify-between border-b border-slate-100 bg-rose-50/60 px-6 py-4">
              <div>
                <div className="text-xs font-bold text-rose-500">Community Report</div>
                <h2 className="mt-1 text-xl font-black text-slate-900">举报违规内容</h2>
              </div>
              <button
                type="button"
                onClick={() => setReportDraft(null)}
                className="rounded-full border border-slate-200 bg-white px-3 py-1.5 text-sm font-semibold text-slate-500 transition-colors hover:text-slate-700"
              >
                关闭
              </button>
            </div>

            <div className="space-y-5 px-6 py-6">
              <div className="rounded-[1.4rem] border border-slate-100 bg-slate-50 px-4 py-4">
                <div className="text-sm font-semibold text-slate-400">
                  举报目标
                </div>
                <div className="mt-2 text-base font-bold text-slate-900">{reportDraft.title}</div>
                <div className="mt-2 text-base leading-8 text-slate-500 line-clamp-3">{reportDraft.excerpt}</div>
              </div>

              <div className="space-y-3">
                <div className="text-base font-semibold text-slate-900">请选择举报原因</div>
                <div className="flex flex-wrap gap-2">
                  {REPORT_REASON_OPTIONS.map((option) => (
                    <button
                      key={option.code}
                      type="button"
                      onClick={() => setReportReason(option.code)}
                      className={joinClasses(
                        "rounded-full border px-3 py-1.5 text-sm font-semibold transition-colors",
                        reportReason === option.code
                          ? "border-rose-200 bg-rose-50 text-rose-700"
                          : "border-slate-200 bg-white text-slate-600 hover:border-rose-200 hover:text-rose-600",
                      )}
                    >
                      {option.label}
                    </button>
                  ))}
                </div>
              </div>

              <div>
                <label className="mb-2 block text-base font-semibold text-slate-900">补充说明</label>
                <textarea
                  rows={4}
                  value={reportDetail}
                  onChange={(event) => setReportDetail(event.target.value)}
                  placeholder="可以补充为什么你认为它存在违规风险，便于平台更快核查。"
                  className="w-full resize-none rounded-[1.3rem] border border-slate-200 px-4 py-3 text-base leading-8 text-slate-700 outline-none transition-colors placeholder:text-slate-400 focus:border-rose-300"
                />
              </div>

              <div className="rounded-[1.2rem] border border-slate-100 bg-slate-50 px-4 py-3 text-sm leading-7 text-slate-500">
                当前原因：{getReportReasonLabel(reportReason)}。提交后会进入治理反馈页，你可以继续回看处理进度。
              </div>
            </div>

            <div className="flex flex-wrap items-center justify-end gap-3 border-t border-slate-100 bg-slate-50 px-6 py-4">
              <Link
                to={`/community/reports?targetType=${encodeURIComponent(reportDraft.targetType)}&targetId=${encodeURIComponent(reportDraft.targetId)}&title=${encodeURIComponent(truncateForQuery(reportDraft.title))}&body=${encodeURIComponent(truncateForQuery(reportDraft.excerpt))}&reasonCode=${encodeURIComponent(reportReason)}&detail=${encodeURIComponent(truncateForQuery(reportDetail))}`}
                className="rounded-full border border-slate-200 bg-white px-4 py-2 text-base font-semibold text-slate-600 transition-colors hover:border-indigo-200 hover:text-indigo-600"
              >
                去治理页完善
              </Link>
              <button
                type="button"
                disabled={reportSubmitting}
                onClick={() => void handleSubmitReport()}
                className="rounded-full bg-rose-600 px-5 py-2.5 text-base font-semibold text-white transition-colors hover:bg-rose-700 disabled:cursor-not-allowed disabled:bg-slate-200 disabled:text-slate-400"
              >
                {reportSubmitting ? "提交中..." : "直接提交举报"}
              </button>
            </div>
          </div>
        </div>
      ) : null}
      <CommunityToast
        toast={toast}
        onClose={(toastId) => {
          setToast((current) => (current?.id === toastId ? null : current));
        }}
      />
    </CommunityModuleLayout>
  );
}

export default function CommunityPostDetailPage() {
  const { role, userId } = useAuth();

  return <CommunityPostDetailWorkspace role={role} userId={userId} />;
}
