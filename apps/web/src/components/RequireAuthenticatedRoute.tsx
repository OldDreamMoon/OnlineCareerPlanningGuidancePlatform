import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { resolveRoleCompatiblePath, resolveWorkspaceDashboardRoute } from "../lib/workspaceRoutes";

export default function RequireAuthenticatedRoute() {
  const { ready, isAuthenticated, role } = useAuth();
  const location = useLocation();
  const currentPath = `${location.pathname}${location.search}${location.hash}`;

  if (!ready) {
    return null;
  }

  if (!isAuthenticated) {
    // 登录页根据 state.from 做回跳，深链进入私有页时不丢原目标。
    return <Navigate to="/auth?mode=login" replace state={{ from: currentPath }} />;
  }

  if (role === "ADMIN") {
    // 管理员账号不进入前台共享域，避免后台身份误操作学生/导师业务页面。
    const compatiblePath = resolveRoleCompatiblePath(currentPath, role);
    if (compatiblePath !== currentPath) {
      return <Navigate to={compatiblePath ?? resolveWorkspaceDashboardRoute(role)} replace />;
    }
  }

  return <Outlet />;
}
