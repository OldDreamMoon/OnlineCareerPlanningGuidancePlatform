import { lazy, Suspense, useEffect, type ReactNode } from "react";
import { matchPath, Navigate, Route, Routes, useLocation, useNavigationType } from "react-router-dom";
import RequireAdminRoute from "./components/RequireAdminRoute";
import RequireAuthenticatedRoute from "./components/RequireAuthenticatedRoute";
import RequireRoleRoute from "./components/RequireRoleRoute";
import AppLoadingScreen from "./components/AppLoadingScreen";
import RouteSpinnerScreen from "./components/RouteSpinnerScreen";
import { SkillsRouteTransitionProvider } from "./components/skills/SkillsRouteTransitionProvider";
import { EnterpriseWorkspaceLayout, MentorWorkspaceLayout, StudentWorkspaceLayout } from "./components/workspace/WorkspaceRoleLayouts";
import { resolveWorkspaceDashboardRoute } from "./lib/workspaceRoutes";

const LandingPage = lazy(() => import("./pages/LandingPage"));
const AuthPage = lazy(() => import("./pages/AuthPage"));
const AuthStepTwoPage = lazy(() => import("./pages/AuthStepTwoPage"));
const LoginPage = lazy(() => import("./pages/LoginPage"));
const RegisterPage = lazy(() => import("./pages/RegisterPage"));
const ForbiddenPage = lazy(() => import("./pages/ForbiddenPage"));
const DashboardRoutePage = lazy(() => import("./pages/DashboardRoutePage"));
const DashboardPage = lazy(() => import("./pages/DashboardPage"));
const StudentProfilePage = lazy(() => import("./pages/StudentProfilePage"));
const StudentPublicProfilePage = lazy(() => import("./pages/StudentPublicProfilePage"));
const AiReviewCenterPage = lazy(() => import("./pages/AiReviewCenterPage"));
const InterviewPracticePage = lazy(() => import("./pages/InterviewPracticePage"));
const ResumeAnalyzerPage = lazy(() => import("./pages/ResumeAnalyzerPage"));
const NotificationsPage = lazy(() => import("./pages/NotificationsPage"));
const CommunityPage = lazy(() => import("./pages/CommunityPage"));
const CommunityLeaderboardPage = lazy(() => import("./pages/CommunityLeaderboardPage"));
const CommunityReportsPage = lazy(() => import("./pages/CommunityReportsPage"));
const CommunityPostDetailPage = lazy(() => import("./pages/CommunityPostDetailPage"));
const StudentBountyListPage = lazy(() => import("./pages/StudentBountyListPage"));
const StudentBountyDetailPage = lazy(() => import("./pages/StudentBountyDetailPage"));
const MentorDetailRedirectPage = lazy(() => import("./pages/MentorDetailRedirectPage"));
const MentorSelectionPage = lazy(() => import("./pages/MentorSelectionPage"));
const ConsultCreatePage = lazy(() => import("./pages/ConsultCreatePage"));
const StudentConsultOrdersPage = lazy(() => import("./pages/StudentConsultOrdersPage"));
const ConsultOrderDetailPage = lazy(() => import("./pages/ConsultOrderDetailPage"));
const MentorDashboardPage = lazy(() => import("./pages/MentorDashboardPage"));
const MentorProfilePage = lazy(() => import("./pages/MentorProfilePage"));
const MentorOrderCenterPage = lazy(() => import("./pages/MentorOrderCenterPage"));
const MentorOrderWorkspacePage = lazy(() => import("./pages/MentorOrderWorkspacePage"));
const MentorFinancePage = lazy(() => import("./pages/MentorFinancePage"));
const EnterpriseDashboardPage = lazy(() => import("./pages/EnterpriseDashboardPage"));
const EnterpriseProfilePage = lazy(() => import("./pages/EnterpriseProfilePage"));
const EnterpriseTaskCenterPage = lazy(() => import("./pages/EnterpriseTaskCenterPage"));
const EnterpriseTaskCreatePage = lazy(() => import("./pages/EnterpriseTaskCreatePage"));
const EnterpriseTaskReviewPage = lazy(() => import("./pages/EnterpriseTaskReviewPage"));
const AdminShellLayout = lazy(() => import("./components/AdminShellLayout"));
const AdminDashboardPage = lazy(() => import("./pages/AdminDashboardPage"));
const AdminEnterpriseTaskOpsPage = lazy(() => import("./pages/AdminEnterpriseTaskOpsPage"));
const AdminAiApplicationsPage = lazy(() => import("./pages/AdminAiApplicationsPage"));
const AdminAiGatewayPage = lazy(() => import("./pages/AdminAiGatewayPage"));
const AdminMentorOperationsPage = lazy(() => import("./pages/AdminMentorOperationsPage"));
const AdminNotificationsPage = lazy(() => import("./pages/AdminNotificationsPage"));
const AdminOrdersReconciliationPage = lazy(() => import("./pages/AdminOrdersReconciliationPage"));
const AdminContentModerationPage = lazy(() => import("./pages/AdminContentModerationPage"));
const AdminRuntimePage = lazy(() => import("./pages/AdminRuntimePage"));
const AdminSkillsPreviewPage = lazy(() => import("./pages/AdminSkillsPreviewPage"));
const AdminSkillsOperationsPage = lazy(() => import("./pages/AdminSkillsOperationsPage"));
const AdminUserCertificationReviewsPage = lazy(() => import("./pages/AdminUserCertificationReviewsPage"));
const AdminUsersPage = lazy(() => import("./pages/AdminUsersPage"));
const SkillsPage = lazy(() => import("./pages/SkillsPage"));

const DOCUMENT_TITLE_SUFFIX = " - 大学生就业规划指导平台";
const LAST_NON_NOTIFICATION_LOCATION_STORAGE_KEY = "app:last-non-notification-location";

type TitleResolver = string | ((searchParams: URLSearchParams) => string);

type RouteTitleItem = {
  pattern: string;
  title: TitleResolver;
};

const routeTitleItems: RouteTitleItem[] = [
  // 页面标题跟路由守卫共用同一套路径语义，答辩时查 URL 可以先看这张表。
  { pattern: "/admin/login", title: "管理员登录" },
  { pattern: "/admin/users/reviews/:userId", title: "认证审核详情" },
  { pattern: "/admin/users/reviews", title: "认证审核" },
  { pattern: "/admin/users/:userId", title: "用户详情" },
  { pattern: "/admin/users", title: "用户与认证" },
  { pattern: "/admin/enterprise/tasks", title: "企业任务治理" },
  { pattern: "/admin/mentors/operations", title: "导师经营治理" },
  { pattern: "/admin/notifications", title: "通知运营" },
  {
    pattern: "/admin/ai/applications",
    title: (searchParams) => {
      const tab = searchParams.get("tab");
      if (tab === "scenes") {
        return "场景清单";
      }
      if (tab === "policies") {
        return "权益策略";
      }
      return "AI 应用运营";
    },
  },
  { pattern: "/admin/ai/gateway", title: "AI 网关" },
  {
    pattern: "/admin/consult/orders",
    title: (searchParams) => (searchParams.get("tab") === "reconciliation" ? "异常对账" : "售后工单"),
  },
  {
    pattern: "/admin/content",
    title: (searchParams) => {
      const tab = searchParams.get("tab");
      if (tab === "review") {
        return "待审队列";
      }
      if (tab === "audit") {
        return "审计日志";
      }
      if (tab === "terms" || tab === "settings") {
        return "敏感词库";
      }
      if (tab === "home" || tab === "overview") {
        return "内容治理";
      }
      return "举报中心";
    },
  },
  { pattern: "/admin/skills/preview", title: "技能星图预览" },
  { pattern: "/admin/skills", title: "技能资源治理" },
  {
    pattern: "/admin/runtime",
    title: (searchParams) => {
      const tab = searchParams.get("tab");
      if (tab === "basic" || tab === "business" || tab === "operations" || tab === "degrade" || tab === "flags" || tab === "runtime" || tab === "moderation") {
        return "基础策略";
      }
      if (tab === "ai-channels") {
        return "AI 运行态";
      }
      return "运行配置";
    },
  },
  { pattern: "/admin/dashboard", title: "系统总览" },
  { pattern: "/admin", title: "管理员后台" },
  { pattern: "/mentor/orders/:orderNo/workspace", title: "导师履约工作区" },
  { pattern: "/mentor/orders", title: "导师订单中心" },
  { pattern: "/mentor/profile", title: "导师资料与服务" },
  { pattern: "/mentor/finance", title: "导师财务中心" },
  { pattern: "/mentor/dashboard", title: "导师工作台" },
  {
    pattern: "/enterprise/tasks/create",
    title: (searchParams) => (searchParams.get("editTaskId") ? "编辑企业任务" : "发布企业任务"),
  },
  { pattern: "/enterprise/tasks/:taskId", title: "企业任务审核工作区" },
  { pattern: "/enterprise/tasks", title: "企业任务中心" },
  { pattern: "/enterprise/profile", title: "企业资料与认证" },
  { pattern: "/enterprise/dashboard", title: "企业工作台" },
  { pattern: "/students/:studentUserId", title: "个人空间" },
  { pattern: "/community/leaderboard", title: "社区贡献榜" },
  { pattern: "/community/reports", title: "举报与治理反馈" },
  { pattern: "/community/:postId", title: "讨论详情" },
  { pattern: "/community", title: "社区广场" },
  { pattern: "/consult/orders/:orderNo", title: "咨询订单详情" },
  { pattern: "/consult/orders", title: "我的咨询订单" },
  { pattern: "/consult/create", title: "创建咨询订单" },
  { pattern: "/bounty/:taskId", title: "任务详情与提交" },
  { pattern: "/bounty", title: "企业实战任务" },
  { pattern: "/mentors/:mentorUserId", title: "导师广场" },
  { pattern: "/mentors", title: "导师广场" },
  { pattern: "/ai/resume/review", title: "简历复盘" },
  { pattern: "/ai/resume", title: "简历优化" },
  { pattern: "/ai/interview/review", title: "面试复盘" },
  { pattern: "/ai/interview/session", title: "模拟面试进行中" },
  { pattern: "/ai/interview/prepare", title: "模拟面试准备" },
  { pattern: "/ai/interview", title: "模拟面试准备" },
  { pattern: "/ai/history", title: "AI复盘中心" },
  { pattern: "/notifications", title: "通知中心" },
  { pattern: "/skills", title: "技能星图" },
  { pattern: "/profile", title: "账号与资料中心" },
  { pattern: "/student/dashboard", title: "学生工作台" },
  { pattern: "/dashboard", title: "正在进入工作台" },
  { pattern: "/403", title: "无权限访问" },
  { pattern: "/login", title: "账号登录" },
  { pattern: "/register", title: "账号注册" },
  { pattern: "/auth/step2", title: "继续注册" },
  {
    pattern: "/auth",
    title: (searchParams) => {
      const panel = searchParams.get("panel");
      const mode = searchParams.get("mode");

      if (panel === "register-details") {
        return "完善注册资料";
      }
      if (mode === "register") {
        return "账号注册";
      }
      return "账号登录";
    },
  },
  { pattern: "/", title: "平台首页" },
];

function resolveDocumentTitle(pathname: string, search: string) {
  if (pathname === "/") {
    return "大学生就业规划指导平台";
  }

  const searchParams = new URLSearchParams(search);
  const matchedItem = routeTitleItems.find((item) => matchPath({ path: item.pattern, end: true }, pathname));
  if (!matchedItem) {
    return "大学生就业规划指导平台";
  }

  const pageTitle = typeof matchedItem.title === "function"
    ? matchedItem.title(searchParams)
    : matchedItem.title;
  return `${pageTitle}${DOCUMENT_TITLE_SUFFIX}`;
}

function AppDocumentTitleSync() {
  const location = useLocation();

  useEffect(() => {
    if (typeof document === "undefined") {
      return;
    }
    document.title = resolveDocumentTitle(location.pathname, location.search);

    // 通知中心需要“返回上一业务页”，因此只记录非通知页的最后访问位置。
    if (typeof window !== "undefined" && location.pathname !== "/notifications") {
      window.sessionStorage.setItem(
        LAST_NON_NOTIFICATION_LOCATION_STORAGE_KEY,
        `${location.pathname}${location.search}${location.hash}`,
      );
    }
  }, [location.hash, location.pathname, location.search]);

  return null;
}

function AppRouteScrollReset() {
  const location = useLocation();
  const navigationType = useNavigationType();

  useEffect(() => {
    if (typeof window === "undefined") {
      return undefined;
    }

    if (location.hash || navigationType === "POP") {
      return undefined;
    }

    // 正常导航回到顶部，hash 定位和浏览器返回保持用户原来的阅读位置。
    const frameId = window.requestAnimationFrame(() => {
      window.scrollTo({ top: 0, left: 0, behavior: "auto" });
    });

    return () => window.cancelAnimationFrame(frameId);
  }, [location.hash, location.pathname, navigationType]);

  return null;
}

function AdminPageSuspense({
  children,
  title = "正在加载管理员页面...",
}: {
  children: ReactNode;
  title?: string;
}) {
  return <Suspense fallback={<AppLoadingScreen title={title} />}>{children}</Suspense>;
}

function AppRouteSuspense({ children }: { children: ReactNode }) {
  const location = useLocation();
  const { pathname } = location;

  if (pathname.startsWith("/admin")) {
    const title = pathname === "/admin/login" ? "正在加载管理员登录页..." : "正在加载管理员页面...";
    return <Suspense fallback={<AppLoadingScreen title={title} />}>{children}</Suspense>;
  }

  if (pathname === "/") {
    return <Suspense fallback={<RouteSpinnerScreen label="正在加载首页" />}>{children}</Suspense>;
  }

  if (pathname === "/dashboard") {
    return <Suspense fallback={<RouteSpinnerScreen label="正在定位工作台" />}>{children}</Suspense>;
  }

  if (pathname === "/student/dashboard") {
    return <Suspense fallback={<RouteSpinnerScreen label="正在加载学生工作台" />}>{children}</Suspense>;
  }

  if (pathname === "/skills") {
    return <Suspense fallback={<RouteSpinnerScreen label="正在加载技能星图" />}>{children}</Suspense>;
  }

  return <Suspense fallback={<RouteSpinnerScreen label="正在加载页面" />}>{children}</Suspense>;
}

function resolveStudentOrdersFallback(role: "STUDENT" | "MENTOR" | "ENTERPRISE" | "ADMIN" | null) {
  if (role === "MENTOR") {
    return "/mentor/orders";
  }
  return resolveWorkspaceDashboardRoute(role);
}

function resolveStudentBountyFallback(role: "STUDENT" | "MENTOR" | "ENTERPRISE" | "ADMIN" | null) {
  if (role === "ENTERPRISE") {
    return "/enterprise/tasks";
  }
  return resolveWorkspaceDashboardRoute(role);
}

function isStudentWorkspaceRedirectMismatchPath(pathname: string) {
  return pathname === "/student/dashboard"
    || pathname === "/profile"
    || pathname === "/skills"
    || pathname === "/ai/history"
    || pathname === "/consult/orders"
    || pathname === "/bounty"
    || pathname.startsWith("/bounty/");
}

function resolveStudentWorkspaceMismatchMode({
  location,
}: {
  location: { pathname: string };
}) {
  return isStudentWorkspaceRedirectMismatchPath(location.pathname) ? "redirect" : "forbidden";
}

function resolveStudentWorkspaceFallback({
  role,
  location,
}: {
  role: "STUDENT" | "MENTOR" | "ENTERPRISE" | "ADMIN" | null;
  location: { pathname: string };
}) {
  if (location.pathname === "/consult/orders") {
    return resolveStudentOrdersFallback(role);
  }
  if (location.pathname === "/bounty" || location.pathname.startsWith("/bounty/")) {
    return resolveStudentBountyFallback(role);
  }
  return resolveWorkspaceDashboardRoute(role);
}

export default function App() {
  return (
    <SkillsRouteTransitionProvider>
      <AppDocumentTitleSync />
      <AppRouteScrollReset />
      <AppRouteSuspense>
        <Routes>
          <Route path="/" element={<LandingPage />} />
          <Route path="/auth" element={<AuthPage />} />
          <Route path="/auth/step2" element={<AuthStepTwoPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/403" element={<ForbiddenPage />} />
          <Route
            path="/admin/login"
            element={<Navigate to="/auth?mode=login" replace />}
          />

          <Route element={<RequireAuthenticatedRoute />}>
            <Route path="/dashboard" element={<DashboardRoutePage />} />
            <Route path="/students/:studentUserId" element={<StudentPublicProfilePage />} />
            <Route path="/notifications" element={<NotificationsPage />} />

            <Route
              element={(
                <RequireRoleRoute
                  roles={["STUDENT"]}
                  mismatchMode={resolveStudentWorkspaceMismatchMode}
                  resolveRedirectPath={resolveStudentWorkspaceFallback}
                />
              )}
            >
              <Route path="/skills" element={<SkillsPage />} />
              <Route element={<StudentWorkspaceLayout />}>
                <Route path="/student/dashboard" element={<DashboardPage />} />
                <Route path="/profile" element={<StudentProfilePage />} />
                <Route path="/ai/history" element={<AiReviewCenterPage />} />
                <Route path="/consult/orders" element={<StudentConsultOrdersPage />} />
                <Route path="/bounty" element={<StudentBountyListPage />} />
                <Route path="/bounty/:taskId" element={<StudentBountyDetailPage />} />
                <Route path="/ai/interview" element={<InterviewPracticePage />} />
                <Route path="/ai/interview/prepare" element={<InterviewPracticePage />} />
                <Route path="/ai/interview/session" element={<InterviewPracticePage />} />
                <Route path="/ai/interview/review" element={<InterviewPracticePage />} />
                <Route path="/ai/resume" element={<ResumeAnalyzerPage />} />
                <Route path="/ai/resume/review" element={<ResumeAnalyzerPage />} />
                <Route path="/mentors/:mentorUserId" element={<MentorDetailRedirectPage />} />
                <Route path="/mentors" element={<MentorSelectionPage />} />
                <Route path="/consult/create" element={<ConsultCreatePage />} />
                <Route path="/consult/orders/:orderNo" element={<ConsultOrderDetailPage />} />
              </Route>
            </Route>

            <Route element={<RequireRoleRoute roles={["MENTOR"]} mismatchMode="redirect" />}>
              <Route element={<MentorWorkspaceLayout />}>
                <Route path="/mentor/dashboard" element={<MentorDashboardPage />} />
                <Route path="/mentor/profile" element={<MentorProfilePage />} />
                <Route path="/mentor/orders" element={<MentorOrderCenterPage />} />
                <Route path="/mentor/orders/:orderNo/workspace" element={<MentorOrderWorkspacePage />} />
                <Route path="/mentor/finance" element={<MentorFinancePage />} />
              </Route>
            </Route>

            <Route element={<RequireRoleRoute roles={["ENTERPRISE"]} mismatchMode="redirect" />}>
              <Route element={<EnterpriseWorkspaceLayout />}>
                <Route path="/enterprise/dashboard" element={<EnterpriseDashboardPage />} />
                <Route path="/enterprise/profile" element={<EnterpriseProfilePage />} />
                <Route path="/enterprise/tasks" element={<EnterpriseTaskCenterPage />} />
                <Route path="/enterprise/tasks/create" element={<EnterpriseTaskCreatePage />} />
                <Route path="/enterprise/bounty/create" element={<Navigate to="/enterprise/tasks/create" replace />} />
                <Route path="/enterprise/tasks/:taskId" element={<EnterpriseTaskReviewPage />} />
              </Route>
            </Route>

            <Route element={<RequireRoleRoute roles={["STUDENT", "MENTOR"]} mismatchMode="forbidden" />}>
              <Route path="/community" element={<CommunityPage />} />
              <Route path="/community/leaderboard" element={<CommunityLeaderboardPage />} />
              <Route path="/community/reports" element={<CommunityReportsPage />} />
            </Route>
            <Route element={<RequireRoleRoute roles={["STUDENT", "MENTOR", "ADMIN"]} mismatchMode="forbidden" />}>
              <Route path="/community/:postId" element={<CommunityPostDetailPage />} />
            </Route>
          </Route>

          <Route element={<RequireAdminRoute />}>
            <Route
              path="/admin/skills/preview"
              element={(
                <AdminPageSuspense title="正在加载技能星图预览...">
                  <AdminSkillsPreviewPage />
                </AdminPageSuspense>
              )}
            />
            <Route
              path="/admin"
              element={(
                <AdminPageSuspense title="正在加载后台壳层...">
                  <AdminShellLayout />
                </AdminPageSuspense>
              )}
            >
              <Route index element={<Navigate to="dashboard" replace />} />
              <Route
                path="dashboard"
                element={(
                  <AdminPageSuspense title="正在加载系统总览...">
                    <AdminDashboardPage />
                  </AdminPageSuspense>
                )}
              />
              <Route
                path="users"
                element={(
                  <AdminPageSuspense title="正在加载用户与认证...">
                    <AdminUsersPage />
                  </AdminPageSuspense>
                )}
              />
              <Route
                path="users/reviews"
                element={(
                  <AdminPageSuspense title="正在加载认证审核...">
                    <AdminUserCertificationReviewsPage />
                  </AdminPageSuspense>
                )}
              />
              <Route
                path="users/reviews/:userId"
                element={(
                  <AdminPageSuspense title="正在加载认证审核详情...">
                    <AdminUserCertificationReviewsPage />
                  </AdminPageSuspense>
                )}
              />
              <Route
                path="users/:userId"
                element={(
                  <AdminPageSuspense title="正在加载用户详情...">
                    <AdminUsersPage />
                  </AdminPageSuspense>
                )}
              />
              <Route
                path="enterprise/tasks"
                element={(
                  <AdminPageSuspense title="正在加载企业任务治理...">
                    <AdminEnterpriseTaskOpsPage />
                  </AdminPageSuspense>
                )}
              />
              <Route
                path="mentors/operations"
                element={(
                  <AdminPageSuspense title="正在加载导师经营治理...">
                    <AdminMentorOperationsPage />
                  </AdminPageSuspense>
                )}
              />
              <Route
                path="notifications"
                element={(
                  <AdminPageSuspense title="正在加载通知运营...">
                    <AdminNotificationsPage />
                  </AdminPageSuspense>
                )}
              />
              <Route
                path="consult/orders"
                element={(
                  <AdminPageSuspense title="正在加载交易与售后...">
                    <AdminOrdersReconciliationPage />
                  </AdminPageSuspense>
                )}
              />
              <Route
                path="ai/applications"
                element={(
                  <AdminPageSuspense title="正在加载 AI 应用运营...">
                    <AdminAiApplicationsPage />
                  </AdminPageSuspense>
                )}
              />
              <Route
                path="ai/gateway"
                element={(
                  <AdminPageSuspense title="正在加载 AI 网关...">
                    <AdminAiGatewayPage />
                  </AdminPageSuspense>
                )}
              />
              <Route
                path="content"
                element={(
                  <AdminPageSuspense title="正在加载内容治理...">
                    <AdminContentModerationPage />
                  </AdminPageSuspense>
                )}
              />
              <Route
                path="skills"
                element={(
                  <AdminPageSuspense title="正在加载技能资源治理...">
                    <AdminSkillsOperationsPage />
                  </AdminPageSuspense>
                )}
              />
              <Route
                path="runtime"
                element={(
                  <AdminPageSuspense title="正在加载运行配置...">
                    <AdminRuntimePage />
                  </AdminPageSuspense>
                )}
              />
              <Route path="orders" element={<Navigate to="/admin/consult/orders" replace />} />
              <Route path="payments" element={<Navigate to="/admin/consult/orders" replace />} />
              <Route path="feature-flags" element={<Navigate to="/admin/runtime" replace />} />
              <Route path="system/settings" element={<Navigate to="/admin/runtime" replace />} />
              <Route path="settings" element={<Navigate to="/admin/runtime" replace />} />
              <Route path="ai-gateway" element={<Navigate to="/admin/ai/gateway" replace />} />
              <Route path="*" element={<Navigate to="/admin/dashboard" replace />} />
            </Route>
          </Route>

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AppRouteSuspense>
    </SkillsRouteTransitionProvider>
  );
}
