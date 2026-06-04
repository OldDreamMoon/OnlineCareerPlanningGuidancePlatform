import { Suspense, useCallback, useMemo, useState, type ReactNode } from "react";
import { Building2, Briefcase, BriefcaseBusiness, FileSignature, Sparkles, Wallet } from "lucide-react";
import { Outlet, useLocation } from "react-router-dom";
import StudentWorkspaceTopbar, { buildStudentWorkspacePrimaryNav } from "../student/StudentWorkspaceTopbar";
import WorkspaceRoleTopbar from "./WorkspaceRoleTopbar";
import {
  WorkspaceTopbarCaptureProvider,
  type CapturedRoleTopbarConfig,
  type CapturedStudentTopbarConfig,
} from "./WorkspaceTopbarCaptureContext";
import { getEnterpriseWorkspaceNavItems, getMentorWorkspaceNavItems } from "../../lib/workspaceNav";
import { STUDENT_DASHBOARD_ROUTE } from "../../lib/workspaceRoutes";

type CapturedTopbarState<T> = {
  config: T;
  signature: string;
  routeSignature: string;
};

function getPathSignature(pathname: string, search: string) {
  return `${pathname}${search}`;
}

function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function WorkspaceOutletFallback({
  label,
  topbarPosition = "sticky",
}: {
  label: string;
  topbarPosition?: "fixed" | "sticky";
}) {
  return (
    <div
      className={joinClasses(
        "relative z-10 flex min-h-[calc(100vh-76px)] items-center justify-center px-6 pb-12",
        topbarPosition === "fixed" ? "pt-28" : "pt-12",
      )}
    >
      <div className="flex flex-col items-center gap-3 text-slate-400">
        <div className="h-9 w-9 animate-spin rounded-full border-2 border-slate-200 border-t-slate-900" />
        <span className="text-xs font-medium tracking-[0.18em] text-slate-500">{label}</span>
      </div>
    </div>
  );
}

function WorkspaceOutletSuspense({
  children,
  label,
  topbarPosition,
}: {
  children: ReactNode;
  label: string;
  topbarPosition?: "fixed" | "sticky";
}) {
  return (
    <Suspense fallback={<WorkspaceOutletFallback label={label} topbarPosition={topbarPosition} />}>
      {children}
    </Suspense>
  );
}

function useCapturedTopbar<T>(defaultConfig: T, defaultSignature: string) {
  const [captured, setCaptured] = useState<CapturedTopbarState<T>>({
    config: defaultConfig,
    signature: defaultSignature,
    routeSignature: defaultSignature,
  });

  const capture = useCallback((config: T, signature: string) => {
    setCaptured((current) => (
      current.routeSignature === defaultSignature && current.signature === signature
        ? current
        : { config, signature, routeSignature: defaultSignature }
    ));
  }, [defaultSignature]);

  const visibleCaptured = captured.routeSignature === defaultSignature
    ? captured
    : {
      config: defaultConfig,
      signature: defaultSignature,
      routeSignature: defaultSignature,
    };

  return { captured: visibleCaptured, capture };
}

function resolveStudentDefaultTopbar(pathname: string): CapturedStudentTopbarConfig {
  if (pathname === STUDENT_DASHBOARD_ROUTE) {
    return {
      sectionLabel: "Student Dashboard",
      title: "学生工作台",
      brandIcon: Sparkles,
      navItems: buildStudentWorkspacePrimaryNav("dashboard"),
      position: "fixed",
    };
  }

  if (pathname.startsWith("/ai/resume")) {
    return {
      sectionLabel: "Resume Studio",
      title: "简历优化",
      brandIcon: Sparkles,
      navItems: buildStudentWorkspacePrimaryNav("resume"),
      position: "sticky",
    };
  }

  if (pathname.startsWith("/ai/interview")) {
    return {
      sectionLabel: "Interview Practice",
      title: "模拟面试",
      brandIcon: Sparkles,
      navItems: buildStudentWorkspacePrimaryNav("interview"),
      position: "sticky",
    };
  }

  if (pathname.startsWith("/community")) {
    return {
      sectionLabel: "Community",
      title: "社区互助",
      brandIcon: Sparkles,
      navItems: buildStudentWorkspacePrimaryNav("community"),
      position: "sticky",
    };
  }

  if (pathname.startsWith("/mentors")) {
    return {
      sectionLabel: "Mentor Marketplace",
      title: "导师广场",
      brandIcon: Sparkles,
      navItems: buildStudentWorkspacePrimaryNav("mentors"),
      position: "sticky",
    };
  }

  if (pathname.startsWith("/consult")) {
    return {
      sectionLabel: "Consult Orders",
      title: pathname.startsWith("/consult/create") ? "创建咨询订单" : "我的咨询订单",
      brandIcon: Sparkles,
      navItems: buildStudentWorkspacePrimaryNav("consultOrders"),
      position: "sticky",
    };
  }

  if (pathname.startsWith("/bounty")) {
    return {
      sectionLabel: "Enterprise Practice",
      title: pathname === "/bounty" ? "企业实战任务" : "任务详情与提交",
      brandIcon: Sparkles,
      navItems: buildStudentWorkspacePrimaryNav("enterprisePractice"),
      position: "sticky",
    };
  }

  if (pathname.startsWith("/ai/history")) {
    return {
      sectionLabel: "AI Review Center",
      title: "复盘中心",
      brandIcon: Sparkles,
      navItems: buildStudentWorkspacePrimaryNav("reviewCenter"),
      position: "sticky",
    };
  }

  return {
    sectionLabel: "Student Workspace",
    title: "学生工作台",
    brandIcon: Sparkles,
    navItems: buildStudentWorkspacePrimaryNav("dashboard"),
    position: "sticky",
  };
}

function resolveMentorDefaultTopbar(pathname: string): CapturedRoleTopbarConfig {
  if (pathname.startsWith("/mentor/profile")) {
    return {
      sectionLabel: "Profile & Services",
      title: "导师资料与服务",
      icon: BriefcaseBusiness,
      navItems: getMentorWorkspaceNavItems("profile"),
      displayName: null,
      userSubtitle: null,
      userFallbackLabel: "导师",
      userFallbackInitial: "导",
      refreshTitle: "刷新当前页面内容",
    };
  }

  if (pathname.startsWith("/mentor/finance")) {
    return {
      sectionLabel: "Finance Center",
      title: "导师财务中心",
      icon: Wallet,
      navItems: getMentorWorkspaceNavItems("finance"),
      displayName: null,
      userSubtitle: null,
      userFallbackLabel: "导师",
      userFallbackInitial: "导",
      refreshTitle: "刷新导师财务中心",
    };
  }

  if (pathname.startsWith("/mentor/orders")) {
    return {
      sectionLabel: "Mentor Orders",
      title: pathname.includes("/workspace") ? "导师履约工作区" : "导师订单中心",
      icon: Briefcase,
      navItems: getMentorWorkspaceNavItems("orders"),
      displayName: null,
      userSubtitle: null,
      userFallbackLabel: "导师",
      userFallbackInitial: "导",
      refreshTitle: "刷新导师订单数据",
    };
  }

  return {
    sectionLabel: "Mentor Workspace",
    title: "导师工作台",
    icon: Briefcase,
    navItems: getMentorWorkspaceNavItems("dashboard"),
    displayName: null,
    userSubtitle: null,
    userFallbackLabel: "导师",
    userFallbackInitial: "导",
    refreshTitle: "刷新导师工作台数据",
  };
}

function resolveEnterpriseDefaultTopbar(pathname: string, search: string): CapturedRoleTopbarConfig {
  if (pathname.startsWith("/enterprise/profile")) {
    return {
      sectionLabel: "Enterprise Profile",
      title: "企业资料与认证",
      icon: Building2,
      navItems: getEnterpriseWorkspaceNavItems("profile"),
      displayName: null,
      userSubtitle: null,
      userFallbackLabel: "企业代表",
      userFallbackInitial: "企",
      refreshTitle: "刷新企业资料",
    };
  }

  if (pathname === "/enterprise/tasks/create") {
    const isEditMode = new URLSearchParams(search).has("editTaskId");
    return {
      sectionLabel: isEditMode ? "Enterprise Task Edit" : "Enterprise Task Create",
      title: isEditMode ? "编辑企业任务" : "发布企业任务",
      icon: FileSignature,
      navItems: getEnterpriseWorkspaceNavItems("create"),
      displayName: null,
      userSubtitle: null,
      userFallbackLabel: "企业代表",
      userFallbackInitial: "企",
      refreshTitle: isEditMode ? "刷新任务编辑页" : "刷新任务发布页",
    };
  }

  if (pathname.startsWith("/enterprise/tasks/")) {
    return {
      sectionLabel: "Enterprise Task Review",
      title: "企业任务审核工作区",
      icon: Building2,
      navItems: getEnterpriseWorkspaceNavItems("tasks"),
      displayName: null,
      userSubtitle: null,
      userFallbackLabel: "企业代表",
      userFallbackInitial: "企",
      refreshTitle: "刷新审核工作区数据",
      maxWidthClassName: "max-w-[100rem]",
    };
  }

  if (pathname.startsWith("/enterprise/tasks")) {
    return {
      sectionLabel: "Enterprise Task Center",
      title: "企业任务中心",
      icon: Building2,
      navItems: getEnterpriseWorkspaceNavItems("tasks"),
      displayName: null,
      userSubtitle: null,
      userFallbackLabel: "企业代表",
      userFallbackInitial: "企",
      refreshTitle: "刷新企业任务中心",
    };
  }

  return {
    sectionLabel: "Enterprise Dashboard",
    title: "企业工作台",
    icon: Building2,
    navItems: getEnterpriseWorkspaceNavItems("dashboard"),
    displayName: null,
    userSubtitle: null,
    userFallbackLabel: "企业代表",
    userFallbackInitial: "企",
    refreshTitle: "刷新企业工作台数据",
  };
}

export function StudentWorkspaceLayout() {
  const location = useLocation();
  const defaultConfig = useMemo(
    () => resolveStudentDefaultTopbar(location.pathname),
    [location.pathname],
  );
  const defaultSignature = `student-default:${getPathSignature(location.pathname, location.search)}`;
  const { captured, capture } = useCapturedTopbar(defaultConfig, defaultSignature);
  const captureValue = useMemo(() => ({ captureStudentTopbar: capture }), [capture]);

  return (
    <>
      <StudentWorkspaceTopbar {...captured.config} />
      <WorkspaceTopbarCaptureProvider value={captureValue}>
        <WorkspaceOutletSuspense label="正在加载学生工作区" topbarPosition={captured.config.position}>
          <Outlet />
        </WorkspaceOutletSuspense>
      </WorkspaceTopbarCaptureProvider>
    </>
  );
}

export function MentorWorkspaceLayout() {
  const location = useLocation();
  const defaultConfig = useMemo(
    () => resolveMentorDefaultTopbar(location.pathname),
    [location.pathname],
  );
  const defaultSignature = `mentor-default:${getPathSignature(location.pathname, location.search)}`;
  const { captured, capture } = useCapturedTopbar(defaultConfig, defaultSignature);
  const captureValue = useMemo(() => ({ captureRoleTopbar: capture }), [capture]);

  return (
    <>
      <WorkspaceRoleTopbar {...captured.config} />
      <WorkspaceTopbarCaptureProvider value={captureValue}>
        <WorkspaceOutletSuspense label="正在加载导师工作区">
          <Outlet />
        </WorkspaceOutletSuspense>
      </WorkspaceTopbarCaptureProvider>
    </>
  );
}

export function EnterpriseWorkspaceLayout() {
  const location = useLocation();
  const defaultConfig = useMemo(
    () => resolveEnterpriseDefaultTopbar(location.pathname, location.search),
    [location.pathname, location.search],
  );
  const defaultSignature = `enterprise-default:${getPathSignature(location.pathname, location.search)}`;
  const { captured, capture } = useCapturedTopbar(defaultConfig, defaultSignature);
  const captureValue = useMemo(() => ({ captureRoleTopbar: capture }), [capture]);

  return (
    <>
      <WorkspaceRoleTopbar {...captured.config} />
      <WorkspaceTopbarCaptureProvider value={captureValue}>
        <WorkspaceOutletSuspense label="正在加载企业工作区">
          <Outlet />
        </WorkspaceOutletSuspense>
      </WorkspaceTopbarCaptureProvider>
    </>
  );
}
