import { Button, Result } from "antd";
import { useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { resolveWorkspaceDashboardRoute } from "../lib/workspaceRoutes";

function resolveWorkspaceHome(role: string | null) {
  return resolveWorkspaceDashboardRoute(role);
}

type ForbiddenPageLocationState = {
  fallbackPath?: string;
  attemptedPath?: string;
};

function normalizeInternalPath(value: string | null | undefined) {
  const trimmed = value?.trim();
  if (!trimmed || !trimmed.startsWith("/")) {
    return null;
  }
  return trimmed;
}

function resolveSafeFallbackPath(params: {
  fallbackPath: string | null;
  attemptedPath: string | null;
  workspaceHome: string;
}) {
  const { fallbackPath, attemptedPath, workspaceHome } = params;
  if (!fallbackPath || fallbackPath === "/403") {
    return workspaceHome;
  }
  if (attemptedPath && fallbackPath === attemptedPath) {
    return workspaceHome;
  }
  return fallbackPath;
}

export default function ForbiddenPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { isAuthenticated, role, logout } = useAuth();
  const locationState = location.state as ForbiddenPageLocationState | null;
  const attemptedPath = normalizeInternalPath(locationState?.attemptedPath);
  const workspaceHome = resolveWorkspaceHome(role);
  const safeFallbackPath = resolveSafeFallbackPath({
    fallbackPath: normalizeInternalPath(locationState?.fallbackPath),
    attemptedPath,
    workspaceHome,
  });
  const subtitle = isAuthenticated
    ? attemptedPath
      ? `当前账号无法访问 ${attemptedPath}，你可以先回到自己的工作台，或切换账号后再继续。`
      : "当前账号没有该页面的访问权限，可以返回当前工作台，或切换账号后继续。"
    : "当前未登录或账号权限不足，请先登录后再继续。";
  const primaryActionLabel = isAuthenticated ? "返回我的工作台" : "前往登录";
  const handlePrimaryAction = () => {
    navigate(isAuthenticated ? safeFallbackPath : "/auth?mode=login", { replace: true });
  };
  const handleSwitchAccount = () => {
    logout();
    navigate("/auth?mode=login", { replace: true });
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 px-6 py-10">
      <Result
        status="403"
        title="403 - 无权限访问"
        subTitle={subtitle}
        extra={(
          <div className="flex flex-wrap items-center justify-center gap-3">
            <Button type="primary" onClick={handlePrimaryAction}>
              {primaryActionLabel}
            </Button>
            {isAuthenticated ? (
              <Button onClick={handleSwitchAccount}>切换账号</Button>
            ) : null}
          </div>
        )}
      />
    </div>
  );
}
