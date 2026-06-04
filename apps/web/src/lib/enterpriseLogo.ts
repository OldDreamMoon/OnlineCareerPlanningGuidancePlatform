type TimeValue = number | string | null | undefined;

export function buildEnterpriseLogoUrl(logoUrl: string | null | undefined, updatedAt?: TimeValue) {
  const normalizedLogoUrl = logoUrl?.trim();
  if (!normalizedLogoUrl) {
    return null;
  }

  if (!updatedAt || normalizedLogoUrl.startsWith("blob:") || normalizedLogoUrl.startsWith("data:")) {
    return normalizedLogoUrl;
  }

  const versionValue = String(updatedAt).trim();
  if (!versionValue) {
    return normalizedLogoUrl;
  }

  try {
    const resolvedUrl = new URL(normalizedLogoUrl, window.location.origin);
    resolvedUrl.searchParams.set("v", versionValue);
    if (resolvedUrl.origin === window.location.origin) {
      return `${resolvedUrl.pathname}${resolvedUrl.search}${resolvedUrl.hash}`;
    }
    return resolvedUrl.toString();
  } catch {
    const separator = normalizedLogoUrl.includes("?") ? "&" : "?";
    return `${normalizedLogoUrl}${separator}v=${encodeURIComponent(versionValue)}`;
  }
}
