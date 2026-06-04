import { useEffect, useMemo, useState } from "react";
import { deriveAvatarLabel } from "../../lib/studentAvatar";
import {
  getCachedMentorAvatarDataUrl,
  isMentorAvatarCacheableUrl,
  loadMentorAvatarDataUrl,
} from "../../lib/mentorAvatar";

function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

export default function MentorIdentityAvatar({
  userId,
  displayName,
  avatarUrl,
  alt,
  className,
  imageClassName,
  fallbackClassName,
  textClassName,
  fallbackLabel,
  allowLatestFallback = false,
}: {
  userId: number | null | undefined;
  displayName: string;
  avatarUrl?: string | null;
  alt?: string;
  className?: string;
  imageClassName?: string;
  fallbackClassName?: string;
  textClassName?: string;
  fallbackLabel?: string;
  allowLatestFallback?: boolean;
}) {
  const normalizedAvatarUrl = avatarUrl?.trim() || null;
  const fallbackText = useMemo(
    () => fallbackLabel?.trim() || deriveAvatarLabel(displayName),
    [displayName, fallbackLabel],
  );
  const [resolvedAvatarUrl, setResolvedAvatarUrl] = useState<string | null>(() => {
    if (!normalizedAvatarUrl) {
      return null;
    }
    if (!isMentorAvatarCacheableUrl(normalizedAvatarUrl)) {
      return normalizedAvatarUrl;
    }
    return getCachedMentorAvatarDataUrl({
      userId,
      avatarUrl: normalizedAvatarUrl,
      allowLatestFallback,
    });
  });

  useEffect(() => {
    let active = true;

    if (!normalizedAvatarUrl) {
      setResolvedAvatarUrl(null);
      return () => {
        active = false;
      };
    }

    if (!isMentorAvatarCacheableUrl(normalizedAvatarUrl)) {
      setResolvedAvatarUrl(normalizedAvatarUrl);
      return () => {
        active = false;
      };
    }

    const cachedValue = getCachedMentorAvatarDataUrl({
      userId,
      avatarUrl: normalizedAvatarUrl,
      allowLatestFallback,
    });
    setResolvedAvatarUrl(cachedValue);

    void loadMentorAvatarDataUrl({
      userId,
      avatarUrl: normalizedAvatarUrl,
      allowLatestFallback,
    })
      .then((nextAvatarUrl) => {
        if (active) {
          setResolvedAvatarUrl(nextAvatarUrl);
        }
      })
      .catch(() => {
        if (active) {
          setResolvedAvatarUrl((current) => current ?? null);
        }
      });

    return () => {
      active = false;
    };
  }, [allowLatestFallback, normalizedAvatarUrl, userId]);

  return (
    <div className="relative shrink-0">
      <div
        className={joinClasses(
          "relative flex items-center justify-center overflow-hidden rounded-full",
          className,
        )}
      >
        {resolvedAvatarUrl ? (
          <img
            src={resolvedAvatarUrl}
            alt={alt ?? `${displayName} 的头像`}
            className={joinClasses("h-full w-full object-cover", imageClassName)}
          />
        ) : (
          <span
            className={joinClasses(
              "flex h-full w-full items-center justify-center font-bold",
              fallbackClassName,
              textClassName,
            )}
          >
            {fallbackText}
          </span>
        )}
      </div>
    </div>
  );
}
