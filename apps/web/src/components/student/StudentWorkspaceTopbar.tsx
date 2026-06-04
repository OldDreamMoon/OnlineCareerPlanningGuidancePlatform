import { AnimatePresence, motion } from "framer-motion";
import { ChevronDown, Sparkles, type LucideIcon } from "lucide-react";
import { useEffect, useLayoutEffect, useRef, useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";
import { buildStudentPublicProfileHref } from "../../lib/studentPublicProfile";
import { type StudentAvatarMeta } from "../../lib/studentAvatar";
import { getRoleDisplayLabel } from "../../lib/roleLabels";
import { STUDENT_DASHBOARD_ROUTE } from "../../lib/workspaceRoutes";
import StudentIdentityAvatar from "../avatar/StudentIdentityAvatar";
import NotificationBellButton from "../notifications/NotificationBellButton";
import { useWorkspaceTopbarCapture } from "../workspace/WorkspaceTopbarCaptureContext";

export type StudentWorkspaceTopbarNavItem = {
  label: string;
  to?: string;
  href?: string;
  active?: boolean;
};

export type StudentWorkspaceTopbarPrimarySection =
  | "dashboard"
  | "resume"
  | "interview"
  | "mentors"
  | "consultOrders"
  | "enterprisePractice"
  | "bounty"
  | "skills"
  | "community"
  | "reviewCenter";

type StudentWorkspaceTopbarProps = {
  sectionLabel?: string;
  title?: string;
  brandHref?: string;
  brandIcon?: LucideIcon;
  navItems?: StudentWorkspaceTopbarNavItem[];
  leftAddon?: ReactNode;
  rightActions?: ReactNode;
  position?: "fixed" | "sticky";
  userId?: number | null;
  displayName?: string | null;
  avatar?: StudentAvatarMeta | null;
  tier?: string | null;
  userSubtitle?: string | null;
  className?: string;
  captureKey?: string | number | boolean | null;
};

const PRIMARY_NAV_ITEMS: Array<{
  key: StudentWorkspaceTopbarPrimarySection;
  label: string;
  to: string;
}> = [
  { key: "dashboard", label: "工作台", to: STUDENT_DASHBOARD_ROUTE },
  { key: "resume", label: "简历优化", to: "/ai/resume" },
  { key: "interview", label: "模拟面试", to: "/ai/interview" },
  { key: "skills", label: "技能星图", to: "/skills" },
  { key: "community", label: "社区互助", to: "/community" },
  { key: "mentors", label: "导师广场", to: "/mentors" },
  { key: "consultOrders", label: "咨询订单", to: "/consult/orders" },
  { key: "enterprisePractice", label: "企业实战", to: "/bounty" },
  { key: "reviewCenter", label: "复盘中心", to: "/ai/history" },
];

function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function isPrimarySectionActive(
  itemKey: StudentWorkspaceTopbarPrimarySection,
  activeSection?: StudentWorkspaceTopbarPrimarySection | null,
) {
  if (!activeSection) {
    return false;
  }

  if (itemKey === activeSection) {
    return true;
  }

  if (itemKey === "enterprisePractice" && activeSection === "bounty") {
    return true;
  }

  return false;
}

export function buildStudentWorkspacePrimaryNav(
  activeSection?: StudentWorkspaceTopbarPrimarySection | null,
): StudentWorkspaceTopbarNavItem[] {
  return PRIMARY_NAV_ITEMS.map((item) => ({
    label: item.label,
    to: item.to,
    active: isPrimarySectionActive(item.key, activeSection),
  }));
}

export default function StudentWorkspaceTopbar({
  sectionLabel: _sectionLabel = "Student Dashboard",
  title = "学生工作台",
  brandHref = STUDENT_DASHBOARD_ROUTE,
  brandIcon: BrandIcon = Sparkles,
  navItems = buildStudentWorkspacePrimaryNav(),
  leftAddon,
  rightActions,
  position = "sticky",
  userId,
  displayName,
  avatar,
  tier,
  userSubtitle,
  className,
  captureKey,
}: StudentWorkspaceTopbarProps) {
  const {
    role,
    userId: authUserId,
    displayName: authDisplayName,
    logout,
  } = useAuth();
  const topbarCapture = useWorkspaceTopbarCapture();
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const userMenuRef = useRef<HTMLDivElement | null>(null);

  const resolvedUserId = userId ?? authUserId;
  const resolvedDisplayName = displayName?.trim() || authDisplayName?.trim() || "同学";
  const resolvedUserSubtitle = getRoleDisplayLabel(role) || userSubtitle?.trim() || "当前用户";
  const publicProfileHref = resolvedUserId ? buildStudentPublicProfileHref(resolvedUserId) : STUDENT_DASHBOARD_ROUTE;
  const captureSignature = JSON.stringify({
    sectionLabel: _sectionLabel,
    title,
    brandHref,
    navItems: navItems.map((item) => [item.label, item.to ?? item.href ?? "", Boolean(item.active)]),
    position,
    userId: resolvedUserId ?? null,
    displayName: resolvedDisplayName,
    tier: tier ?? null,
    userSubtitle: resolvedUserSubtitle,
    hasLeftAddon: Boolean(leftAddon),
    hasRightActions: Boolean(rightActions),
    className: className ?? "",
    captureKey: captureKey ?? null,
  });

  useLayoutEffect(() => {
    topbarCapture?.captureStudentTopbar?.({
      sectionLabel: _sectionLabel,
      title,
      brandHref,
      brandIcon: BrandIcon,
      navItems,
      leftAddon,
      rightActions,
      position,
      userId,
      displayName,
      avatar,
      tier,
      userSubtitle,
      className,
      captureKey,
    }, captureSignature);
  }, [captureSignature, topbarCapture]);

  useEffect(() => {
    if (!userMenuOpen) {
      return undefined;
    }

    const handlePointerDown = (event: MouseEvent) => {
      if (!userMenuRef.current?.contains(event.target as Node)) {
        setUserMenuOpen(false);
      }
    };

    window.addEventListener("mousedown", handlePointerDown);
    return () => window.removeEventListener("mousedown", handlePointerDown);
  }, [userMenuOpen]);

  if (topbarCapture?.captureStudentTopbar) {
    return null;
  }

  return (
    <nav
      className={joinClasses(
        position === "fixed" ? "fixed inset-x-0 top-0" : "sticky top-0",
        "z-40 border-b border-white/60 bg-white/72 px-4 py-3 backdrop-blur-xl sm:px-6",
        className,
      )}
    >
      <div className="mx-auto grid w-full max-w-[96rem] grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] items-center gap-4">
        <div className="col-start-1 flex min-w-0 items-center gap-3 justify-self-start">
          <Link
            to={brandHref}
            className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-indigo-600 via-violet-500 to-teal-400 text-white shadow-[0_18px_35px_rgba(79,70,229,0.28)]"
          >
            <BrandIcon size={20} />
          </Link>
          <div className="flex min-h-11 min-w-0 items-center">
            <div className="truncate text-xl font-bold leading-tight text-slate-950">{title}</div>
          </div>
          {leftAddon ? <div className="flex shrink-0 items-center">{leftAddon}</div> : null}
        </div>

        {navItems.length > 0 ? (
          <div className="col-start-2 hidden items-center justify-self-center gap-5 text-sm font-medium text-slate-500 lg:flex xl:gap-6">
            {navItems.map((item) => {
              if (item.to) {
                return (
                  <Link
                    key={`${item.label}-${item.to}`}
                    to={item.to}
                    className={joinClasses(
                      "whitespace-nowrap transition-colors",
                      item.active
                        ? "border-b-2 border-indigo-600 pb-1 font-semibold text-indigo-600"
                        : "hover:text-indigo-600",
                    )}
                  >
                    {item.label}
                  </Link>
                );
              }

              if (item.href) {
                return (
                  <a
                    key={`${item.label}-${item.href}`}
                    href={item.href}
                    className={joinClasses(
                      "whitespace-nowrap transition-colors",
                      item.active
                        ? "border-b-2 border-indigo-600 pb-1 font-semibold text-indigo-600"
                        : "hover:text-indigo-600",
                    )}
                  >
                    {item.label}
                  </a>
                );
              }

              return null;
            })}
          </div>
        ) : null}

        <div className="col-start-3 flex shrink-0 items-center justify-self-end gap-3">
          {rightActions}
          <NotificationBellButton />
          <div ref={userMenuRef} className="relative hidden md:block">
            <button
              type="button"
              onClick={() => setUserMenuOpen((current) => !current)}
              className="inline-flex items-center gap-3 rounded-full border border-white/80 bg-white/80 px-3 py-1.5 shadow-sm transition-colors hover:border-indigo-200"
            >
              <StudentIdentityAvatar
                userId={resolvedUserId}
                role={role}
                displayName={resolvedDisplayName}
                avatar={avatar}
                tier={tier}
                className="h-10 w-10"
                textClassName="text-sm"
              />
              <div className="text-left leading-tight">
                <div className="text-sm font-semibold text-slate-900">{resolvedDisplayName}</div>
                <div className="text-xs text-slate-500">{resolvedUserSubtitle}</div>
              </div>
              <ChevronDown
                size={16}
                className={joinClasses(
                  "text-slate-400 transition-transform",
                  userMenuOpen && "rotate-180",
                )}
              />
            </button>

            <AnimatePresence>
              {userMenuOpen ? (
                <motion.div
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: 8 }}
                  transition={{ duration: 0.18 }}
                  className="absolute right-0 top-full z-50 mt-3 w-48 overflow-hidden rounded-[1.4rem] border border-white/90 bg-white/95 p-2 shadow-[0_24px_60px_rgba(15,23,42,0.12)] backdrop-blur-xl"
                >
                  <Link
                    to={publicProfileHref}
                    onClick={() => setUserMenuOpen(false)}
                    className="flex w-full items-center rounded-[1rem] px-4 py-3 text-sm font-semibold text-slate-700 transition-colors hover:bg-teal-50 hover:text-teal-600"
                  >
                    个人空间
                  </Link>
                  <Link
                    to="/profile"
                    onClick={() => setUserMenuOpen(false)}
                    className="flex w-full items-center rounded-[1rem] px-4 py-3 text-sm font-semibold text-slate-700 transition-colors hover:bg-indigo-50 hover:text-indigo-600"
                  >
                    资料中心
                  </Link>
                  <Link
                    to="/profile?tab=security&action=password"
                    onClick={() => setUserMenuOpen(false)}
                    className="flex w-full items-center rounded-[1rem] px-4 py-3 text-sm font-semibold text-slate-700 transition-colors hover:bg-amber-50 hover:text-amber-600"
                  >
                    修改密码
                  </Link>
                  <button
                    type="button"
                    onClick={() => {
                      setUserMenuOpen(false);
                      logout();
                    }}
                    className="flex w-full items-center rounded-[1rem] px-4 py-3 text-sm font-semibold text-slate-700 transition-colors hover:bg-slate-100 hover:text-slate-900"
                  >
                    退出登录
                  </button>
                </motion.div>
              ) : null}
            </AnimatePresence>
          </div>
        </div>
      </div>
    </nav>
  );
}
