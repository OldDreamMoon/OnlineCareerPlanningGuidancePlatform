import {
  GraduationCap,
  MessageSquare,
  Tag,
  ThumbsUp,
} from "lucide-react";
import { useAuth } from "../../auth/AuthContext";
import type { CommunityPostSummary } from "../../lib/community";
import { buildMentorPublicDetailHref } from "../../lib/mentorPublicProfile";
import CommunityAuthorIdentity from "./CommunityAuthorIdentity";
import {
  formatRelativeTime,
  getModerationStatusMeta,
  getResolvedStatusMeta,
  getScenarioLabel,
  joinClasses,
  truncateText,
} from "./communityUtils";
import {
  buildStudentPublicProfileHref,
  cacheStudentProfileFromCommunityPost,
} from "../../lib/studentPublicProfile";

type CommunityPostCardProps = {
  post: CommunityPostSummary;
  onSelect: (post: CommunityPostSummary) => void;
};

export default function CommunityPostCard({
  post,
  onSelect,
}: CommunityPostCardProps) {
  const { role } = useAuth();
  const moderationMeta = getModerationStatusMeta(post.moderationStatus);
  const resolvedMeta = getResolvedStatusMeta(post.resolvedStatus);
  const subdued = post.moderationStatus === "REVIEW" || post.moderationStatus === "BLOCK";
  const profileHref = post.authorRole === "STUDENT"
    ? buildStudentPublicProfileHref(post.authorUserId)
    : role === "STUDENT" && post.authorRole === "MENTOR"
      ? buildMentorPublicDetailHref(post.authorUserId, "community")
      : null;

  return (
    <div
      role="button"
      tabIndex={0}
      onClick={() => onSelect(post)}
      onKeyDown={(event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          onSelect(post);
        }
      }}
      className={joinClasses(
        "group block w-full overflow-hidden rounded-[1.85rem] border p-6 text-left transition-all duration-300",
        subdued
          ? "border-slate-200 bg-slate-50/70 shadow-none hover:border-slate-300"
          : "border-slate-100 bg-white shadow-sm hover:-translate-y-0.5 hover:border-indigo-100 hover:shadow-[0_18px_48px_rgba(99,102,241,0.12)]",
        post.authoredByMe && "ring-1 ring-indigo-500/20",
      )}
    >
      <div className="flex items-start justify-between gap-4">
        <CommunityAuthorIdentity
          userId={post.authorUserId}
          displayName={post.authorDisplayName}
          realName={post.authorRealName ?? null}
          showRealName={post.authorShowRealName ?? false}
          role={post.authorRole}
          avatarUrl={post.authorAvatarUrl ?? null}
          timeLabel={formatRelativeTime(post.createdAt)}
          secondaryLabel={`场景 · ${getScenarioLabel(post.scenarioCode)}`}
          authoredByMe={post.authoredByMe}
          size="sm"
          profileHref={profileHref}
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
          stopPropagationOnProfileClick
        />
        <div className="flex flex-wrap justify-end gap-2">
          <span className={joinClasses("rounded-full px-3 py-1 text-xs font-semibold", moderationMeta.className)}>
            {moderationMeta.label}
          </span>
          <span className={joinClasses("rounded-full px-3 py-1 text-xs font-semibold", resolvedMeta.className)}>
            {resolvedMeta.label}
          </span>
        </div>
      </div>

      <div className={joinClasses("mt-5", subdued && "opacity-80")}>
        <h2 className="text-xl font-bold leading-8 tracking-tight text-slate-900 transition-colors group-hover:text-indigo-600">
          {post.title}
        </h2>
        <p className="mt-3 text-base leading-8 text-slate-600">
          {truncateText(post.content, 170) || "作者暂未补充正文内容。"}
        </p>
      </div>

      <div className="mt-5 flex flex-wrap gap-2">
        <span className="inline-flex items-center gap-1 rounded-full border border-slate-200 bg-slate-50 px-3 py-1.5 text-sm font-semibold text-slate-600">
          <Tag size={11} />
          {getScenarioLabel(post.scenarioCode)}
        </span>
        {post.tags.slice(0, 4).map((tag) => (
          <span
            key={tag}
            className="inline-flex rounded-full border border-indigo-100 bg-indigo-50/80 px-3 py-1.5 text-sm font-semibold text-indigo-600"
          >
            #{tag}
          </span>
        ))}
      </div>

      <div className="mt-6 flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-4">
        <div className="flex flex-wrap items-center gap-4 text-base text-slate-500">
          <span className="inline-flex items-center gap-1.5">
            <MessageSquare size={16} />
            {post.commentCount}
          </span>
          <span className="inline-flex items-center gap-1.5">
            <ThumbsUp size={16} />
            {post.likeCount}
          </span>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {post.hasMentorReply ? (
            <span className="inline-flex items-center gap-1.5 rounded-full border border-amber-200 bg-amber-50 px-3 py-1.5 text-sm font-semibold text-amber-700">
              <GraduationCap size={12} />
              导师已参与
            </span>
          ) : null}
          {post.participatedByMe && !post.authoredByMe ? (
            <span className="rounded-full border border-slate-200 bg-slate-100 px-3 py-1.5 text-sm font-semibold text-slate-600">
              我参与过
            </span>
          ) : null}
        </div>
      </div>
    </div>
  );
}
