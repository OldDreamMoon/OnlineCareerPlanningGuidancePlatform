import { Link } from "react-router-dom";
import MentorIdentityAvatar from "../avatar/MentorIdentityAvatar";
import StudentIdentityAvatar from "../avatar/StudentIdentityAvatar";
import { buildMentorDisplayNameWithRealNameIndicator } from "../../lib/mentorNames";
import { buildStudentAvatarPath } from "../../lib/studentAvatar";
import { getDisplayInitial, getRoleMeta, joinClasses } from "./communityUtils";

type CommunityAuthorIdentityProps = {
  userId?: number | null;
  displayName: string;
  realName?: string | null;
  showRealName?: boolean;
  role: string | null | undefined;
  avatarUrl?: string | null;
  timeLabel?: string;
  secondaryLabel?: string;
  authoredByMe?: boolean;
  ai?: boolean;
  size?: "sm" | "md";
  profileHref?: string | null;
  onProfileClick?: () => void;
  stopPropagationOnProfileClick?: boolean;
};

export default function CommunityAuthorIdentity({
  userId,
  displayName,
  realName,
  showRealName = false,
  role,
  avatarUrl,
  timeLabel,
  secondaryLabel,
  authoredByMe = false,
  ai = false,
  size = "md",
  profileHref,
  onProfileClick,
  stopPropagationOnProfileClick = false,
}: CommunityAuthorIdentityProps) {
  const roleMeta = getRoleMeta(role, ai);
  const compact = size === "sm";
  const normalizedRole = (role ?? "").toUpperCase();
  const isStudentAuthor = normalizedRole === "STUDENT" && !ai;
  const resolvedDisplayName = role === "MENTOR" && !ai
    ? buildMentorDisplayNameWithRealNameIndicator({
      displayName,
      realName,
      showRealName,
    }, displayName)
    : displayName;
  const wrapperClassName = joinClasses(
    "flex items-start gap-3 rounded-[1.2rem]",
    profileHref && !ai && "group transition-colors hover:bg-slate-50/90",
  );

  const content = (
    <>
      {role === "MENTOR" && !ai ? (
        <MentorIdentityAvatar
          userId={userId}
          displayName={resolvedDisplayName}
          avatarUrl={avatarUrl}
          className={joinClasses(
            "shadow-sm",
            compact ? "h-10 w-10" : "h-12 w-12",
          )}
          fallbackClassName={roleMeta.avatarClassName}
          textClassName={compact ? "text-sm" : "text-base"}
          fallbackLabel={getDisplayInitial(resolvedDisplayName)}
        />
      ) : isStudentAuthor ? (
        <StudentIdentityAvatar
          userId={userId}
          role={role}
          displayName={resolvedDisplayName}
          avatarPath={userId
            ? (authoredByMe ? "/profiles/students/me/avatar" : buildStudentAvatarPath(userId))
            : undefined}
          className={joinClasses(
            "shadow-sm",
            compact ? "h-10 w-10" : "h-12 w-12",
          )}
          textClassName={compact ? "text-sm" : "text-base"}
        />
      ) : (
        <div
          className={joinClasses(
            "flex shrink-0 items-center justify-center overflow-hidden rounded-full font-bold shadow-sm",
            compact ? "h-10 w-10 text-sm" : "h-12 w-12 text-base",
            avatarUrl ? "border border-slate-200 bg-white" : roleMeta.avatarClassName,
          )}
        >
          {avatarUrl ? (
            <img
              src={avatarUrl}
              alt={`${displayName} 的头像`}
              className="h-full w-full object-cover"
            />
          ) : (
            getDisplayInitial(ai ? "AI" : displayName)
          )}
        </div>
      )}
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-2">
          <span className={joinClasses(compact ? "text-base" : "text-lg", "font-bold text-slate-900")}>
            {resolvedDisplayName}
          </span>
          <span className={joinClasses("rounded-full px-2.5 py-1 text-xs font-semibold", roleMeta.badgeClassName)}>
            {roleMeta.label}
          </span>
          {authoredByMe ? (
            <span className="rounded-full border border-indigo-200 bg-indigo-50 px-2.5 py-1 text-xs font-semibold text-indigo-700">
              我
            </span>
          ) : null}
        </div>
        {timeLabel || secondaryLabel ? (
          <div className="mt-1.5 flex flex-wrap items-center gap-2 text-sm text-slate-500">
            {timeLabel ? <span>{timeLabel}</span> : null}
            {secondaryLabel ? <span>{secondaryLabel}</span> : null}
          </div>
        ) : null}
      </div>
    </>
  );

  if (profileHref && !ai) {
    return (
      <Link
        to={profileHref}
        onClick={(event) => {
          if (stopPropagationOnProfileClick) {
            event.stopPropagation();
          }
          onProfileClick?.();
        }}
        className={wrapperClassName}
      >
        {content}
      </Link>
    );
  }

  return <div className={wrapperClassName}>{content}</div>;
}
