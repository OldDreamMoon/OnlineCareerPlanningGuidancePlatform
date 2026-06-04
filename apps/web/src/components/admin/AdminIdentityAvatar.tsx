import EnterpriseIdentityLogo from "../avatar/EnterpriseIdentityLogo";
import MentorIdentityAvatar from "../avatar/MentorIdentityAvatar";
import StudentIdentityAvatar from "../avatar/StudentIdentityAvatar";
import { buildEnterpriseLogoUrl } from "../../lib/enterpriseLogo";
import { buildStudentAvatarPath, deriveAvatarLabel, type StudentAvatarMeta } from "../../lib/studentAvatar";
import { getAdminRoleAvatarClassName } from "../../adminTheme";

function joinClassNames(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

type AdminIdentityAvatarProps = {
  role?: string | null;
  userId?: number | null;
  displayName: string;
  className?: string;
  imageClassName?: string;
  textClassName?: string;
  studentAvatar?: StudentAvatarMeta | null;
  mentorAvatarUrl?: string | null;
  enterpriseName?: string | null;
  enterpriseLogoUrl?: string | null;
  enterpriseLogoUpdatedAt?: number | string | null;
  fallbackLabel?: string;
};

export default function AdminIdentityAvatar({
  role,
  userId,
  displayName,
  className,
  imageClassName,
  textClassName,
  studentAvatar,
  mentorAvatarUrl,
  enterpriseName,
  enterpriseLogoUrl,
  enterpriseLogoUpdatedAt,
  fallbackLabel,
}: AdminIdentityAvatarProps) {
  const normalizedRole = (role ?? "").toUpperCase();
  const resolvedLabel = fallbackLabel?.trim() || deriveAvatarLabel(enterpriseName?.trim() || displayName);
  const fallbackClassName = getAdminRoleAvatarClassName(normalizedRole);

  if (normalizedRole === "STUDENT") {
    return (
      <StudentIdentityAvatar
        userId={userId}
        role="STUDENT"
        displayName={displayName}
        avatar={studentAvatar}
        avatarPath={buildStudentAvatarPath(userId ?? null, studentAvatar?.updatedAt)}
        className={className}
        imageClassName={imageClassName}
        fallbackClassName={fallbackClassName}
        textClassName={textClassName}
        allowLatestFallback
      />
    );
  }

  if (normalizedRole === "MENTOR") {
    return (
      <MentorIdentityAvatar
        userId={userId}
        displayName={displayName}
        avatarUrl={mentorAvatarUrl?.trim() || null}
        className={className}
        imageClassName={imageClassName}
        fallbackClassName={fallbackClassName}
        textClassName={textClassName}
        fallbackLabel={resolvedLabel}
        allowLatestFallback
      />
    );
  }

  if (normalizedRole === "ENTERPRISE") {
    const resolvedLogoUrl = enterpriseLogoUrl?.trim()
      ? buildEnterpriseLogoUrl(enterpriseLogoUrl, enterpriseLogoUpdatedAt)
      : userId
        ? `/api/v1/profiles/enterprises/${userId}/logo`
        : null;

    return (
      <EnterpriseIdentityLogo
        companyName={enterpriseName?.trim() || displayName}
        logoUrl={resolvedLogoUrl}
        className={joinClassNames("rounded-[1.15rem]", className)}
        imageClassName={imageClassName}
        fallbackClassName={fallbackClassName}
        textClassName={textClassName}
        fallbackLabel={resolvedLabel}
      />
    );
  }

  return (
    <div
      className={joinClassNames(
        "relative flex shrink-0 items-center justify-center overflow-hidden rounded-full font-bold text-white",
        fallbackClassName,
        className,
        textClassName,
      )}
    >
      {resolvedLabel}
    </div>
  );
}
