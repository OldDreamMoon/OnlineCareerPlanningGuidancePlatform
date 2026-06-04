import { useEffect, useMemo, useState } from "react";
import {
  deriveAvatarLabel,
  getCachedStudentAvatarDataUrl,
  loadStudentAvatarDataUrl,
  type StudentAvatarMeta,
} from "../../lib/studentAvatar";

function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

export default function StudentIdentityAvatar({
  userId,
  role,
  displayName,
  avatar,
  tier,
  alt,
  className,
  imageClassName,
  fallbackClassName,
  textClassName,
  showTierBadge = false,
  allowLatestFallback = false,
  avatarPath,
}: {
  userId: number | null | undefined;
  role?: string | null;
  displayName: string;
  avatar?: StudentAvatarMeta | null;
  tier?: string | null;
  alt?: string;
  className?: string;
  imageClassName?: string;
  fallbackClassName?: string;
  textClassName?: string;
  showTierBadge?: boolean;
  allowLatestFallback?: boolean;
  avatarPath?: string;
}) {
  const isPremium = tier === "PREMIUM";
  const fallbackLabel = useMemo(() => deriveAvatarLabel(displayName), [displayName]);
  const [avatarUrl, setAvatarUrl] = useState<string | null>(() => getCachedStudentAvatarDataUrl({
    userId,
    avatar,
    allowLatestFallback,
    avatarPath,
  }));

  useEffect(() => {
    let active = true;

    const cachedValue = getCachedStudentAvatarDataUrl({ userId, avatar, allowLatestFallback, avatarPath });
    setAvatarUrl(cachedValue);

    if (role !== "STUDENT" || !userId) {
      return () => {
        active = false;
      };
    }

    if (avatar && !avatar.uploaded) {
      setAvatarUrl(null);
      return () => {
        active = false;
      };
    }

    void loadStudentAvatarDataUrl({
      userId,
      avatar,
      allowLatestFallback,
      avatarPath,
    })
      .then((resolvedUrl) => {
        if (active) {
          setAvatarUrl(resolvedUrl);
        }
      })
      .catch(() => {
        if (active) {
          setAvatarUrl((current) => current ?? null);
        }
      });

    return () => {
      active = false;
    };
  }, [allowLatestFallback, avatar?.uploaded, avatar?.updatedAt, avatarPath, role, userId]);

  return (
    <div
      className={joinClasses(
        "relative shrink-0",
        isPremium && "rounded-full bg-[linear-gradient(135deg,#fdf2b8_0%,#f4c84d_46%,#d99b11_100%)] p-[2px]",
        isPremium && (showTierBadge
          ? "shadow-[0_10px_24px_rgba(234,179,8,0.32)]"
          : "shadow-[0_8px_18px_rgba(234,179,8,0.22)]"),
      )}
    >
      <div
        className={joinClasses(
          "relative flex items-center justify-center overflow-hidden rounded-full",
          className,
          isPremium && "border border-transparent bg-white",
        )}
      >
        {avatarUrl ? (
          <img
            src={avatarUrl}
            alt={alt ?? `${displayName} 的头像`}
            className={joinClasses("h-full w-full object-cover", imageClassName)}
          />
        ) : (
          <span
            className={joinClasses(
              "flex h-full w-full items-center justify-center bg-gradient-to-br from-indigo-500 to-violet-500 font-bold text-white",
              fallbackClassName,
              textClassName,
            )}
          >
            {fallbackLabel}
          </span>
        )}
      </div>
    </div>
  );
}
