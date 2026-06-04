export const SHARED_DASHBOARD_ROUTE = "/dashboard";
export const STUDENT_DASHBOARD_ROUTE = "/student/dashboard";

const STUDENT_PRIVATE_PREFIXES = [
  STUDENT_DASHBOARD_ROUTE,
  "/profile",
  "/skills",
  "/ai",
  "/mentors",
  "/consult",
] as const;

const MENTOR_PRIVATE_PREFIXES = [
  "/mentor/dashboard",
  "/mentor/profile",
  "/mentor/orders",
  "/mentor/finance",
] as const;

const ENTERPRISE_PRIVATE_PREFIXES = [
  "/enterprise/dashboard",
  "/enterprise/profile",
  "/enterprise/tasks",
] as const;

function getPathname(path: string | null | undefined) {
  const trimmed = path?.trim();
  if (!trimmed || !trimmed.startsWith("/")) {
    return null;
  }

  return trimmed.split(/[?#]/, 1)[0] ?? trimmed;
}

function matchesPathPrefix(pathname: string, prefix: string) {
  return pathname === prefix || pathname.startsWith(`${prefix}/`);
}

function matchesAnyPrefix(pathname: string, prefixes: readonly string[]) {
  return prefixes.some((prefix) => matchesPathPrefix(pathname, prefix));
}

function isAdminCompatibleCommunityPostDetailPath(pathname: string) {
  if (!matchesPathPrefix(pathname, "/community")) {
    return false;
  }

  const segments = pathname.split("/").filter(Boolean);
  return segments.length === 2
    && segments[0] === "community"
    && segments[1] !== "leaderboard"
    && segments[1] !== "reports";
}

export function resolveWorkspaceDashboardRoute(role: string | null | undefined) {
  switch (role) {
    case "ADMIN":
      return "/admin/dashboard";
    case "MENTOR":
      return "/mentor/dashboard";
    case "ENTERPRISE":
      return "/enterprise/dashboard";
    case "STUDENT":
    default:
      return STUDENT_DASHBOARD_ROUTE;
  }
}

export function isStudentDashboardPath(pathname: string | null | undefined) {
  return pathname === SHARED_DASHBOARD_ROUTE || pathname === STUDENT_DASHBOARD_ROUTE;
}

export function normalizeDashboardEntryPath(path: string | null | undefined, role: string | null | undefined) {
  const trimmed = path?.trim() ?? null;
  if (!trimmed || !trimmed.startsWith("/")) {
    return trimmed;
  }

  const pathname = getPathname(trimmed);
  if (!pathname) {
    return trimmed;
  }

  if (!isStudentDashboardPath(pathname)) {
    return trimmed;
  }

  const suffix = trimmed.slice(pathname.length);
  return `${resolveWorkspaceDashboardRoute(role)}${suffix}`;
}

export function isRoleCompatibleWithPath(role: string | null | undefined, path: string | null | undefined) {
  const pathname = getPathname(path);
  if (!pathname) {
    return false;
  }

  if (role === "ADMIN") {
    return matchesPathPrefix(pathname, "/admin") || isAdminCompatibleCommunityPostDetailPath(pathname);
  }

  if (matchesPathPrefix(pathname, "/admin")) {
    return false;
  }

  switch (role) {
    case "MENTOR":
      if (matchesAnyPrefix(pathname, ENTERPRISE_PRIVATE_PREFIXES)) {
        return false;
      }
      if (matchesAnyPrefix(pathname, STUDENT_PRIVATE_PREFIXES)) {
        return false;
      }
      if (matchesPathPrefix(pathname, "/bounty")) {
        return false;
      }
      return true;
    case "ENTERPRISE":
      if (matchesAnyPrefix(pathname, MENTOR_PRIVATE_PREFIXES)) {
        return false;
      }
      if (matchesAnyPrefix(pathname, STUDENT_PRIVATE_PREFIXES)) {
        return false;
      }
      if (matchesPathPrefix(pathname, "/community")) {
        return false;
      }
      return true;
    case "STUDENT":
    default:
      if (matchesAnyPrefix(pathname, MENTOR_PRIVATE_PREFIXES)) {
        return false;
      }
      if (matchesAnyPrefix(pathname, ENTERPRISE_PRIVATE_PREFIXES)) {
        return false;
      }
      return true;
  }
}

export function resolveRoleCompatiblePath(path: string | null | undefined, role: string | null | undefined) {
  const normalizedPath = normalizeDashboardEntryPath(path, role);
  if (!normalizedPath) {
    return null;
  }

  if (isRoleCompatibleWithPath(role, normalizedPath)) {
    return normalizedPath;
  }

  return resolveWorkspaceDashboardRoute(role);
}
