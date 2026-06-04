import { Navigate, Outlet, useLocation, type Location } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { type SessionRole } from "../lib/sessionStore";
import { resolveRoleCompatiblePath, resolveWorkspaceDashboardRoute } from "../lib/workspaceRoutes";

type AllowedRole = Exclude<SessionRole, null>;
type MismatchMode = "redirect" | "forbidden";

type RequireRoleRouteProps = {
  roles: AllowedRole[];
  mismatchMode?: MismatchMode | ((context: {
    role: AllowedRole | null;
    location: Location;
  }) => MismatchMode);
  resolveRedirectPath?: (context: {
    role: AllowedRole | null;
    location: Location;
  }) => string | null | undefined;
};

function buildCurrentPath(location: Location) {
  return `${location.pathname}${location.search}${location.hash}`;
}

export default function RequireRoleRoute({
  roles,
  mismatchMode = "redirect",
  resolveRedirectPath,
}: RequireRoleRouteProps) {
  const { ready, isAuthenticated, role } = useAuth();
  const location = useLocation();

  if (!ready) {
    return null;
  }

  if (!isAuthenticated) {
    // 角色页登录前保留完整 path/search/hash，认证成功后再按当前角色校正。
    return <Navigate to="/auth?mode=login" replace state={{ from: buildCurrentPath(location) }} />;
  }

  if (role && roles.includes(role as AllowedRole)) {
    return <Outlet />;
  }

  const currentPath = buildCurrentPath(location);
  const resolvedRedirectPath = resolveRedirectPath?.({
    role: (role ?? null) as AllowedRole | null,
    location,
  });
  const fallbackPath = resolvedRedirectPath ?? resolveRoleCompatiblePath(currentPath, role);
  const resolvedMismatchMode = typeof mismatchMode === "function"
    ? mismatchMode({
      role: (role ?? null) as AllowedRole | null,
      location,
    })
    : mismatchMode;

  if (resolvedMismatchMode === "forbidden") {
    // forbidden 模式保留 attemptedPath，403 页面可以给出角色不匹配的返回入口。
    const forbiddenFallbackPath = resolvedRedirectPath ?? resolveWorkspaceDashboardRoute(role);
    return (
      <Navigate
        to="/403"
        replace
        state={{
          attemptedPath: currentPath,
          fallbackPath: forbiddenFallbackPath,
        }}
      />
    );
  }

  return <Navigate to={fallbackPath ?? resolveWorkspaceDashboardRoute(role)} replace />;
}
