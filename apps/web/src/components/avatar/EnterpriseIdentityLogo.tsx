import { useEffect, useMemo, useState } from "react";
import { deriveAvatarLabel } from "../../lib/studentAvatar";

function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

export default function EnterpriseIdentityLogo({
  companyName,
  logoUrl,
  alt,
  className,
  imageClassName,
  fallbackClassName,
  textClassName,
  fallbackLabel,
}: {
  companyName: string | null | undefined;
  logoUrl?: string | null;
  alt?: string;
  className?: string;
  imageClassName?: string;
  fallbackClassName?: string;
  textClassName?: string;
  fallbackLabel?: string;
}) {
  const normalizedCompanyName = companyName?.trim() || "企业";
  const normalizedLogoUrl = logoUrl?.trim() || null;
  const [loadFailed, setLoadFailed] = useState(false);
  const resolvedFallbackLabel = useMemo(
    () => fallbackLabel?.trim() || deriveAvatarLabel(normalizedCompanyName),
    [fallbackLabel, normalizedCompanyName],
  );

  useEffect(() => {
    setLoadFailed(false);
  }, [normalizedLogoUrl]);

  return (
    <div
      className={joinClasses(
        "relative isolate overflow-hidden rounded-[1.15rem]",
        className,
      )}
    >
      {normalizedLogoUrl && !loadFailed ? (
        <div className="absolute inset-0">
          <img
            src={normalizedLogoUrl}
            alt={alt ?? `${normalizedCompanyName} 的公司 Logo`}
            className={joinClasses("absolute inset-0 h-full w-full max-w-none object-cover", imageClassName)}
            onError={() => setLoadFailed(true)}
          />
        </div>
      ) : (
        <span
          className={joinClasses(
            "absolute inset-0 flex items-center justify-center bg-gradient-to-br from-slate-100 to-slate-200 font-bold text-slate-700",
            fallbackClassName,
            textClassName,
          )}
        >
          {resolvedFallbackLabel}
        </span>
      )}
    </div>
  );
}
