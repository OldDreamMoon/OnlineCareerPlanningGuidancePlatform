import { Button, ConfigProvider } from "antd";
import {
  Bell,
  BookOpen,
  Bot,
  BriefcaseBusiness,
  ChevronRight,
  Diamond,
  LayoutDashboard,
  MessageSquareMore,
  LogOut,
  ShieldAlert,
  Sparkles,
  Scale,
  Users,
  Wallet,
  Wrench,
} from "lucide-react";
import { useEffect, useRef } from "react";
import { NavLink, Outlet, useLocation, type Location } from "react-router-dom";
import { adminThemeConfig } from "../adminTheme";
import { useAuth } from "../auth/AuthContext";
import "../adminTheme.css";

type ActiveNavKey =
  | "dashboard"
  | "users"
  | "enterprise-tasks"
  | "mentor-operations"
  | "notifications"
  | "ai-applications"
  | "ai-gateway"
  | "content"
  | "payments"
  | "skills"
  | "runtime";

type RealNavItem = {
  key: ActiveNavKey;
  label: string;
  to: string;
  icon: typeof LayoutDashboard;
  children?: AdminNavChildItem[];
};
type AdminNavItem = RealNavItem;

type AdminNavSection = {
  key: string;
  label: string;
  items: AdminNavItem[];
};

type AdminNavChildItem = {
  key: string;
  label: string;
  to: string;
  isActive: (location: Location) => boolean;
};

function readSearchParam(location: Location, key: string) {
  return new URLSearchParams(location.search).get(key);
}

function resolveContentTab(location: Location) {
  if (readSearchParam(location, "traceId")) {
    return "audit";
  }
  const rawTab = readSearchParam(location, "tab");
  if (rawTab === "home" || rawTab === "overview") {
    return "home";
  }
  if (rawTab === "settings" || rawTab === "terms") {
    return "terms";
  }
  if (rawTab === "reports" || rawTab === "review" || rawTab === "audit" || rawTab === "terms") {
    return rawTab;
  }
  return "home";
}

function resolveAiApplicationsTab(location: Location) {
  const rawTab = readSearchParam(location, "tab");
  if (rawTab === "home" || rawTab === "overview" || rawTab === null) {
    return "home";
  }
  if (rawTab === "scenes" || rawTab === "policies") {
    return rawTab;
  }
  return "home";
}

function resolveGatewayTab(location: Location) {
  const rawTab = readSearchParam(location, "tab");
  if (rawTab === "routes" || rawTab === "templates") {
    return "scenes";
  }
  if (rawTab === "runtime") {
    return "providers";
  }
  if (rawTab === "scenes" || rawTab === "providers" || rawTab === "logs") {
    return rawTab;
  }
  if ((readSearchParam(location, "traceId") ?? "").trim()) {
    return "logs";
  }
  return "scenes";
}

function resolveRuntimeTab(location: Location) {
  const rawTab = readSearchParam(location, "tab");
  if (rawTab === "home" || rawTab === "overview") {
    return "home";
  }
  if (rawTab === "flags" || rawTab === "runtime" || rawTab === "moderation") {
    return "basic";
  }
  if (rawTab === "basic" || rawTab === "business" || rawTab === "operations" || rawTab === "ai-channels" || rawTab === "degrade") {
    if (rawTab === "business" || rawTab === "operations" || rawTab === "degrade") {
      return "basic";
    }
    return rawTab;
  }
  return "home";
}

function resolveOrderTab(location: Location) {
  return readSearchParam(location, "tab") === "reconciliation" ? "reconciliation" : "after-sales";
}

const navSections: AdminNavSection[] = [
  {
    key: "overview",
    label: "总览与账号",
    items: [
      {
        key: "dashboard",
        label: "系统总览",
        to: "/admin/dashboard",
        icon: LayoutDashboard,
      },
      {
        key: "users",
        label: "用户与认证",
        to: "/admin/users",
        icon: Users,
        children: [
          {
            key: "users-list",
            label: "用户列表",
            to: "/admin/users",
            isActive: (location) => location.pathname.startsWith("/admin/users") && !location.pathname.startsWith("/admin/users/reviews"),
          },
          {
            key: "users-reviews",
            label: "认证审核",
            to: "/admin/users/reviews",
            isActive: (location) => location.pathname.startsWith("/admin/users/reviews"),
          },
        ],
      },
    ],
  },
  {
    key: "operations",
    label: "业务治理",
    items: [
      {
        key: "enterprise-tasks",
        label: "企业任务治理",
        to: "/admin/enterprise/tasks",
        icon: BriefcaseBusiness,
      },
      {
        key: "mentor-operations",
        label: "导师经营治理",
        to: "/admin/mentors/operations",
        icon: Scale,
      },
      {
        key: "payments",
        label: "交易与售后",
        to: "/admin/consult/orders",
        icon: Wallet,
        children: [
          {
            key: "orders-after-sales",
            label: "售后工单",
            to: "/admin/consult/orders?tab=after-sales",
            isActive: (location) => location.pathname.startsWith("/admin/consult/orders") && resolveOrderTab(location) === "after-sales",
          },
          {
            key: "orders-reconciliation",
            label: "异常对账",
            to: "/admin/consult/orders?tab=reconciliation",
            isActive: (location) => location.pathname.startsWith("/admin/consult/orders") && resolveOrderTab(location) === "reconciliation",
          },
        ],
      },
      {
        key: "skills",
        label: "技能资源治理",
        to: "/admin/skills",
        icon: BookOpen,
      },
      {
        key: "notifications",
        label: "通知运营",
        to: "/admin/notifications",
        icon: Bell,
      },
      {
        key: "content",
        label: "内容治理",
        to: "/admin/content",
        icon: ShieldAlert,
        children: [
          {
            key: "content-reports",
            label: "举报中心",
            to: "/admin/content?tab=reports",
            isActive: (location) => location.pathname.startsWith("/admin/content") && resolveContentTab(location) === "reports",
          },
          {
            key: "content-review",
            label: "待审队列",
            to: "/admin/content?tab=review",
            isActive: (location) => location.pathname.startsWith("/admin/content") && resolveContentTab(location) === "review",
          },
          {
            key: "content-audit",
            label: "审计日志",
            to: "/admin/content?tab=audit",
            isActive: (location) => location.pathname.startsWith("/admin/content") && resolveContentTab(location) === "audit",
          },
          {
            key: "content-terms",
            label: "敏感词库",
            to: "/admin/content?tab=settings",
            isActive: (location) => location.pathname.startsWith("/admin/content") && resolveContentTab(location) === "terms",
          },
        ],
      },
    ],
  },
  {
    key: "ai-system",
    label: "AI 与系统",
    items: [
      {
        key: "ai-applications",
        label: "AI 应用运营",
        to: "/admin/ai/applications",
        icon: MessageSquareMore,
        children: [
          {
            key: "ai-applications-scenes",
            label: "场景清单",
            to: "/admin/ai/applications?tab=scenes",
            isActive: (location) => location.pathname.startsWith("/admin/ai/applications") && resolveAiApplicationsTab(location) === "scenes",
          },
          {
            key: "ai-applications-policies",
            label: "权益策略",
            to: "/admin/ai/applications?tab=policies",
            isActive: (location) => location.pathname.startsWith("/admin/ai/applications") && resolveAiApplicationsTab(location) === "policies",
          },
        ],
      },
      {
        key: "ai-gateway",
        label: "AI 网关",
        to: "/admin/ai/gateway",
        icon: Bot,
        children: [
          {
            key: "ai-gateway-scenes",
            label: "场景维护",
            to: "/admin/ai/gateway?tab=scenes",
            isActive: (location) => location.pathname.startsWith("/admin/ai/gateway") && resolveGatewayTab(location) === "scenes",
          },
          {
            key: "ai-gateway-providers",
            label: "服务商管理",
            to: "/admin/ai/gateway?tab=providers",
            isActive: (location) => location.pathname.startsWith("/admin/ai/gateway") && resolveGatewayTab(location) === "providers",
          },
          {
            key: "ai-gateway-logs",
            label: "调用日志",
            to: "/admin/ai/gateway?tab=logs",
            isActive: (location) => location.pathname.startsWith("/admin/ai/gateway") && resolveGatewayTab(location) === "logs",
          },
        ],
      },
    ],
  },
  {
    key: "runtime",
    label: "运行配置",
    items: [
      {
        key: "runtime",
        label: "运行配置",
        to: "/admin/runtime",
        icon: Wrench,
        children: [
          {
            key: "runtime-basic",
            label: "基础策略",
            to: "/admin/runtime?tab=basic",
            isActive: (location) => location.pathname.startsWith("/admin/runtime") && resolveRuntimeTab(location) === "basic",
          },
          {
            key: "runtime-ai-channels",
            label: "AI 运行态",
            to: "/admin/runtime?tab=ai-channels",
            isActive: (location) => location.pathname.startsWith("/admin/runtime") && resolveRuntimeTab(location) === "ai-channels",
          },
        ],
      },
    ],
  },
];

function joinClassNames(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function resolveActiveKey(pathname: string): ActiveNavKey {
  if (pathname.startsWith("/admin/users")) {
    return "users";
  }
  if (pathname.startsWith("/admin/enterprise/tasks")) {
    return "enterprise-tasks";
  }
  if (pathname.startsWith("/admin/mentors/operations")) {
    return "mentor-operations";
  }
  if (pathname.startsWith("/admin/notifications")) {
    return "notifications";
  }
  if (pathname.startsWith("/admin/content")) {
    return "content";
  }
  if (pathname.startsWith("/admin/consult/orders") || pathname.startsWith("/admin/orders") || pathname.startsWith("/admin/payments")) {
    return "payments";
  }
  if (pathname.startsWith("/admin/ai/applications")) {
    return "ai-applications";
  }
  if (pathname.startsWith("/admin/ai/gateway")) {
    return "ai-gateway";
  }
  if (pathname.startsWith("/admin/skills")) {
    return "skills";
  }
  if (pathname.startsWith("/admin/runtime") || pathname.startsWith("/admin/feature-flags") || pathname.startsWith("/admin/system/settings") || pathname.startsWith("/admin/settings")) {
    return "runtime";
  }
  return "dashboard";
}

function RouteNavItem({
  active,
  icon: Icon,
  label,
  to,
}: {
  active: boolean;
  icon: typeof LayoutDashboard;
  label: string;
  to: string;
}) {
  return (
    <NavLink
      to={to}
      className={joinClassNames(
        "flex items-center gap-3 rounded-xl px-4 py-3 text-sm font-medium transition-[background-color,color,box-shadow] duration-200",
        active
          ? "bg-[linear-gradient(135deg,rgba(224,231,255,1),rgba(238,242,255,0.98))] text-[#312e81] shadow-none"
          : "text-slate-600 hover:bg-slate-100/90",
      )}
    >
      <Icon size={20} className={joinClassNames(active && "text-[#4338ca]")} />
      <span className={joinClassNames(active && "font-semibold")}>{label}</span>
    </NavLink>
  );
}

function ChildRouteNavItem({
  active,
  label,
  to,
}: {
  active: boolean;
  label: string;
  to: string;
}) {
  return (
    <NavLink
      to={to}
      className={joinClassNames(
        "ml-3 flex items-center justify-between gap-3 rounded-xl px-4 py-2.5 text-sm font-medium transition-[background-color,color,box-shadow] duration-200",
        active
          ? "bg-[linear-gradient(135deg,rgba(224,231,255,1),rgba(238,242,255,0.98))] text-[#312e81] shadow-none"
          : "text-slate-600 hover:bg-slate-100/90",
      )}
    >
      <span className={joinClassNames("truncate", active && "font-semibold")}>{label}</span>
      <ChevronRight
        size={16}
        className={joinClassNames(
          "shrink-0 transition-colors",
          active ? "text-[#4338ca]" : "text-slate-300",
        )}
      />
    </NavLink>
  );
}

export default function AdminShellLayout() {
  const location = useLocation();
  const { displayName, logout } = useAuth();
  const activeKey = resolveActiveKey(location.pathname);
  const mainContentScrollRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const container = mainContentScrollRef.current;
    if (!container) {
      return;
    }

    const resetScroll = () => {
      container.scrollTo({ top: 0, left: 0, behavior: "auto" });
      window.scrollTo({ top: 0, left: 0, behavior: "auto" });
    };

    resetScroll();
    const frameId = window.requestAnimationFrame(resetScroll);
    return () => window.cancelAnimationFrame(frameId);
  }, [location.pathname, location.search]);

  return (
    <div className="admin-theme flex h-screen w-full overflow-hidden bg-[#f8fafc] text-slate-900">
      <div className="pointer-events-none fixed inset-0 z-0">
        <div className="absolute right-0 top-0 h-[800px] w-[800px] rounded-full bg-gradient-to-b from-[#63dbf2]/10 to-transparent blur-[120px]" />
      </div>

      <ConfigProvider theme={adminThemeConfig}>
        <aside className="relative z-30 flex h-screen w-64 shrink-0 flex-col overflow-x-hidden rounded-r-2xl bg-white p-4 shadow-[4px_0_24px_rgba(44,47,49,0.02)]">
          <div className="mb-4 flex items-center gap-3 px-2 py-6">
            <div className="relative flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br from-[#5b61f6] via-[#4f46e5] to-[#4338ca] text-white shadow-lg shadow-[#4f46e5]/30">
              <Sparkles size={18} className="relative z-10" />
              <Diamond size={12} className="absolute -right-1.5 -top-1.5 rounded-full bg-white p-0.5 text-[#5b61f6] shadow-sm" />
            </div>
            <div className="min-w-0">
              <div className="max-w-[10rem] font-['Manrope'] text-[0.94rem] font-extrabold leading-[1.2] text-slate-900">大学生就业规划指导平台</div>
              <div className="text-[10px] font-bold tracking-[0.22em] text-slate-500">管理员后台</div>
            </div>
          </div>

          <nav className="flex-1 space-y-4 overflow-x-hidden overflow-y-auto">
            {navSections.map((section) => (
              <section key={section.key} className="space-y-1.5">
                <div className="px-4 pb-1 text-[10px] font-extrabold text-slate-400">
                  {section.label}
                </div>
                <div className="divide-y divide-slate-100/90">
                  {section.items.map((item) => {
                    const itemActive = activeKey === item.key;
                    return (
                      <div key={item.key} className="space-y-1 py-1.5 first:pt-0 last:pb-0">
                        <RouteNavItem
                          active={itemActive}
                          icon={item.icon}
                          label={item.label}
                          to={item.to}
                        />
                        {item.children?.length ? (
                          <div
                            className={joinClassNames(
                              "admin-shell-subnav ml-4 border-l-2 pl-3",
                              itemActive ? "admin-shell-subnav--active border-slate-200/80" : "border-transparent",
                            )}
                          >
                            <div className="admin-shell-subnav-inner">
                              <div className="divide-y divide-slate-100/90 pb-1">
                                {item.children.map((child) => (
                                  <div key={child.key} className="py-1 first:pt-0 last:pb-0">
                                    <ChildRouteNavItem
                                      active={child.isActive(location)}
                                      label={child.label}
                                      to={child.to}
                                    />
                                  </div>
                                ))}
                              </div>
                            </div>
                          </div>
                        ) : null}
                      </div>
                    );
                  })}
                </div>
              </section>
            ))}
          </nav>

          <div className="mt-auto border-t border-slate-200/70 pt-4">
            <Button
              type="text"
              className="flex h-auto w-full items-center justify-start gap-3 rounded-xl px-4 py-3 text-sm font-medium text-slate-600 hover:!bg-slate-100 hover:!text-slate-900"
              icon={<LogOut size={20} />}
              onClick={logout}
            >
              退出登录
            </Button>
          </div>
        </aside>

        <main className="relative z-10 flex min-w-0 flex-1 flex-col overflow-hidden">
          <header className="sticky top-0 z-20 flex items-center justify-end border-b border-slate-200/70 bg-[#f8fafc]/70 px-8 py-4 backdrop-blur-xl">
            <div className="flex items-center">
              <div className="flex items-center border-l border-slate-200/80 pl-6">
                <div className="hidden text-right sm:block">
                  <div className="text-sm font-bold text-slate-900">
                    {displayName ?? "系统管理员"}
                  </div>
                  <div className="text-[10px] font-extrabold tracking-[0.16em] text-[#5b61f6]">
                    当前登录
                  </div>
                </div>
              </div>
            </div>
          </header>

          <div ref={mainContentScrollRef} className="relative flex-1 overflow-auto p-8">
            <div className="pointer-events-none absolute inset-0 overflow-hidden opacity-50">
              <div className="absolute -right-24 -top-24 h-[500px] w-[500px] rounded-full bg-[#dbeafe]/40 blur-[100px]" />
              <div className="absolute bottom-48 -left-24 h-[400px] w-[400px] rounded-full bg-[#d1fae5]/30 blur-[80px]" />
            </div>

            <div className="relative z-10 min-h-full">
              <Outlet />
            </div>
          </div>
        </main>
      </ConfigProvider>
    </div>
  );
}
