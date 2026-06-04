import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import AppLoadingScreen from "./AppLoadingScreen";

export default function RequireAdminRoute() {
  const { ready, isAuthenticated, role } = useAuth();
  const location = useLocation();

  if (!ready) {
    return <AppLoadingScreen title="正在同步管理员会话..." />;
  }

  if (!isAuthenticated) {
    // 后台登录仍复用统一认证页，避免维护第二套登录状态。
    return <Navigate to="/auth?mode=login" replace state={{ from: location.pathname }} />;
  }

  if (role !== "ADMIN") {
    // 非管理员在后台入口直接展示阻断页，不把业务账号重定向到后台空壳。
    return (
      <div className="flex min-h-screen items-center justify-center bg-[linear-gradient(180deg,_#f8fafc_0%,_#eef2ff_100%)] px-6 py-10">
        <div className="w-full max-w-lg rounded-[32px] border border-slate-200/80 bg-white p-8 shadow-[0_30px_90px_-42px_rgba(15,23,42,0.38)]">
          <div className="inline-flex rounded-full border border-rose-200 bg-rose-50 px-3 py-1 text-xs font-semibold text-rose-600">
            Admin Only
          </div>
          <h1 className="mt-5 text-3xl font-semibold tracking-tight text-slate-950">访问受限</h1>
          <p className="mt-3 text-sm leading-7 text-slate-600">
            当前账号不是管理员角色，无法进入后台。请切换管理员账号后重试。
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            <a
              href="/auth?mode=login"
              className="inline-flex items-center justify-center rounded-2xl bg-slate-950 px-5 py-3 text-sm font-semibold text-white transition hover:bg-slate-800"
            >
              返回登录页
            </a>
            <a
              href="/"
              className="inline-flex items-center justify-center rounded-2xl border border-slate-200 px-5 py-3 text-sm font-semibold text-slate-700 transition hover:border-slate-300 hover:bg-slate-50"
            >
              返回平台首页
            </a>
          </div>
        </div>
      </div>
    );
  }

  return <Outlet />;
}
