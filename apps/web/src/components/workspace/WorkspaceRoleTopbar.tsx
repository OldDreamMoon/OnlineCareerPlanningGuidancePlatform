import { AnimatePresence, motion } from "framer-motion";
import { ChevronDown, RefreshCw, type LucideIcon } from "lucide-react";
import { useEffect, useLayoutEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";
import EnterpriseIdentityLogo from "../avatar/EnterpriseIdentityLogo";
import {
  fetchMentorIdentitySnapshot,
  readMentorIdentitySnapshot,
  writeMentorIdentitySnapshot,
  type MentorIdentitySnapshot,
} from "../../lib/mentorIdentity";
import {
  fetchEnterpriseIdentitySnapshot,
  readEnterpriseIdentitySnapshot,
  writeEnterpriseIdentitySnapshot,
  type EnterpriseIdentitySnapshot,
} from "../../lib/enterpriseIdentity";
import MentorIdentityAvatar from "../avatar/MentorIdentityAvatar";
import { getRoleDisplayLabel } from "../../lib/roleLabels";
import {
  MENTOR_PROFILE_WORKSPACE_ENTRY,
  type WorkspaceNavItem,
} from "../../lib/workspaceNav";
import { resolveWorkspaceDashboardRoute, STUDENT_DASHBOARD_ROUTE } from "../../lib/workspaceRoutes";
import NotificationBellButton from "../notifications/NotificationBellButton";
import { useWorkspaceTopbarCapture } from "./WorkspaceTopbarCaptureContext";

type WorkspaceRoleTopbarMenuTone = "default" | "accent" | "danger";

export type WorkspaceRoleTopbarMenuItem = {
  label: string;
  to?: string;
  onClick?: () => void;
  tone?: WorkspaceRoleTopbarMenuTone;
};

type WorkspaceRoleTopbarProps = {
  sectionLabel: string;
  title: string;
  icon: LucideIcon;
  navItems?: WorkspaceNavItem[];
  brandHref?: string;
  displayName: string | null | undefined;
  userSubtitle?: string | null;
  userFallbackLabel?: string;
  userFallbackInitial?: string;
  userAvatarUrl?: string | null;
  userAvatarDisplayName?: string | null;
  lastUpdatedAt?: string | null;
  onRefresh?: (() => void) | null;
  refreshing?: boolean;
  refreshTitle?: string;
  rightActions?: ReactNode;
  menuItems?: WorkspaceRoleTopbarMenuItem[];
  maxWidthClassName?: string;
};

const ROLE_IDENTITY_PLACEHOLDERS = new Set([
  "导师",
  "企业",
  "企业代表",
  "企业账号",
  "当前账号",
  "当前用户",
  "用户",
]);

function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function normalizeRoleIdentityText(value: string | null | undefined) {
  const normalized = value?.trim() ?? "";
  if (!normalized || ROLE_IDENTITY_PLACEHOLDERS.has(normalized)) {
    return null;
  }

  return normalized;
}

function getEmailIdentityName(email: string | null | undefined) {
  const normalizedEmail = email?.trim();
  if (!normalizedEmail) {
    return null;
  }

  return normalizeRoleIdentityText(normalizedEmail.split("@", 1)[0]);
}

function getInitial(name: string | null | undefined, fallback: string) {
  const normalized = name?.trim();
  return normalized ? normalized.charAt(0) : fallback;
}

function getWorkspaceHomeHref(role: string | null) {
  if (!role) {
    return undefined;
  }

  return resolveWorkspaceDashboardRoute(role);
}

function getDefaultMenuItems(role: string | null): WorkspaceRoleTopbarMenuItem[] {
  switch (role) {
    case "MENTOR":
      return [
        { label: "个人资料", to: MENTOR_PROFILE_WORKSPACE_ENTRY },
        { label: "修改密码", to: "/mentor/profile?tab=profile&action=password" },
      ];
    case "ENTERPRISE":
      return [
        { label: "企业资料", to: "/enterprise/profile" },
        { label: "修改密码", to: "/enterprise/profile?action=password" },
      ];
    case "ADMIN":
      return [
        { label: "管理后台", to: "/admin/dashboard" },
        { label: "通知中心", to: "/notifications", tone: "accent" },
      ];
    case "STUDENT":
      return [
        { label: "学生工作台", to: STUDENT_DASHBOARD_ROUTE },
        { label: "通知中心", to: "/notifications", tone: "accent" },
      ];
    default:
      return [];
  }
}

function getMenuItemClassName(tone: WorkspaceRoleTopbarMenuTone | undefined) {
  switch (tone) {
    case "accent":
      return "text-indigo-600 hover:bg-indigo-50";
    case "danger":
      return "text-rose-600 hover:bg-rose-50";
    default:
      return "text-slate-700 hover:bg-slate-100 hover:text-slate-900";
  }
}

function shouldRefreshMentorIdentity(snapshot: MentorIdentitySnapshot | null) {
  if (!snapshot) {
    return true;
  }

  const updatedAt = Date.parse(snapshot.updatedAt);
  if (Number.isNaN(updatedAt)) {
    return true;
  }

  return Date.now() - updatedAt >= 10 * 60 * 1000;
}

function shouldRefreshEnterpriseIdentity(snapshot: EnterpriseIdentitySnapshot | null) {
  if (!snapshot) {
    return true;
  }

  const updatedAt = Date.parse(snapshot.updatedAt);
  if (Number.isNaN(updatedAt)) {
    return true;
  }

  return Date.now() - updatedAt >= 10 * 60 * 1000;
}

export default function WorkspaceRoleTopbar({
  sectionLabel: _sectionLabel,
  title,
  icon: Icon,
  navItems = [],
  brandHref,
  displayName,
  userSubtitle,
  userFallbackLabel = "当前账号",
  userFallbackInitial = "用",
  userAvatarUrl,
  userAvatarDisplayName,
  lastUpdatedAt,
  onRefresh,
  refreshing = false,
  refreshTitle = "刷新页面数据",
  rightActions,
  menuItems,
  maxWidthClassName = "max-w-[96rem]",
}: WorkspaceRoleTopbarProps) {
  const {
    role,
    logout,
    userId,
    displayName: authDisplayName,
    email: authEmail,
  } = useAuth();
  const topbarCapture = useWorkspaceTopbarCapture();
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const [mentorIdentity, setMentorIdentity] = useState<MentorIdentitySnapshot | null>(() => (
    role === "MENTOR" ? readMentorIdentitySnapshot(userId) : null
  ));
  const [enterpriseIdentity, setEnterpriseIdentity] = useState<EnterpriseIdentitySnapshot | null>(() => (
    role === "ENTERPRISE" ? readEnterpriseIdentitySnapshot(userId) : null
  ));
  const userMenuRef = useRef<HTMLDivElement | null>(null);
  const providedDisplayName = normalizeRoleIdentityText(displayName);
  const cachedRoleDisplayName = role === "MENTOR"
    ? mentorIdentity?.displayName ?? null
    : role === "ENTERPRISE"
      ? enterpriseIdentity?.realName ?? null
      : null;
  const sessionDisplayName = role === "ENTERPRISE" ? null : normalizeRoleIdentityText(authDisplayName);
  const sessionEmailName = role === "ENTERPRISE" ? null : getEmailIdentityName(authEmail);
  const resolvedDisplayName = providedDisplayName
    || cachedRoleDisplayName
    || sessionDisplayName
    || sessionEmailName
    || "";
  const providedUserSubtitle = normalizeRoleIdentityText(userSubtitle);
  const resolvedUserSubtitle = providedUserSubtitle
    || (role === "ENTERPRISE" ? enterpriseIdentity?.companyName ?? null : null)
    || (role === "MENTOR" || role === "ENTERPRISE" ? null : getRoleDisplayLabel(role))
    || "";
  const resolvedUserAvatarDisplayName = normalizeRoleIdentityText(userAvatarDisplayName)
    || (role === "ENTERPRISE" ? enterpriseIdentity?.companyName ?? enterpriseIdentity?.realName ?? null : null)
    || resolvedDisplayName;
  const resolvedBrandHref = brandHref ?? getWorkspaceHomeHref(role);
  const resolvedMenuItems = useMemo(
    () => menuItems ?? getDefaultMenuItems(role),
    [menuItems, role],
  );
  const resolvedUserAvatarUrl = role === "MENTOR"
    ? (userAvatarUrl === undefined ? mentorIdentity?.avatarUrl ?? null : userAvatarUrl)
    : role === "ENTERPRISE"
      ? (userAvatarUrl === undefined ? enterpriseIdentity?.logoUrl ?? null : userAvatarUrl)
      : (userAvatarUrl ?? null);
  const captureSignature = JSON.stringify({
    sectionLabel: _sectionLabel,
    title,
    navItems: navItems.map((item) => [item.label, item.to ?? item.href ?? "", Boolean(item.active)]),
    brandHref: resolvedBrandHref ?? null,
    displayName: resolvedDisplayName,
    userSubtitle: resolvedUserSubtitle,
    userFallbackLabel,
    userFallbackInitial,
    userAvatarUrl: resolvedUserAvatarUrl,
    userAvatarDisplayName: resolvedUserAvatarDisplayName,
    lastUpdatedAt,
    hasRefreshAction: Boolean(onRefresh),
    refreshing,
    refreshTitle,
    hasRightActions: Boolean(rightActions),
    maxWidthClassName,
    menuItems: resolvedMenuItems.map((item) => [item.label, item.to ?? "", item.tone ?? "default"]),
  });

  useLayoutEffect(() => {
    topbarCapture?.captureRoleTopbar?.({
      sectionLabel: _sectionLabel,
      title,
      icon: Icon,
      navItems,
      brandHref,
      displayName,
      userSubtitle,
      userFallbackLabel,
      userFallbackInitial,
      userAvatarUrl,
      userAvatarDisplayName,
      lastUpdatedAt,
      onRefresh,
      refreshing,
      refreshTitle,
      rightActions,
      menuItems,
      maxWidthClassName,
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

  useEffect(() => {
    if (role !== "MENTOR") {
      setMentorIdentity(null);
      return undefined;
    }

    const cachedIdentity = readMentorIdentitySnapshot(userId);
    if (cachedIdentity) {
      setMentorIdentity(cachedIdentity);
    }

    const providedName = normalizeRoleIdentityText(displayName);
    if (providedName || userAvatarUrl !== undefined) {
      const nextSnapshot = writeMentorIdentitySnapshot({
        userId,
        displayName: providedName,
        avatarUrl: userAvatarUrl,
        avatarUpdatedAt: cachedIdentity?.avatarUpdatedAt ?? null,
      });
      setMentorIdentity(nextSnapshot);
    }

    const activeSnapshot = readMentorIdentitySnapshot(userId);

    if (!userId || !shouldRefreshMentorIdentity(activeSnapshot)) {
      return undefined;
    }

    const controller = new AbortController();
    void fetchMentorIdentitySnapshot(userId, controller.signal)
      .then((snapshot) => {
        if (!controller.signal.aborted) {
          setMentorIdentity(snapshot);
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setMentorIdentity(activeSnapshot ?? null);
        }
      });

    return () => controller.abort();
  }, [displayName, role, userAvatarUrl, userId]);

  useEffect(() => {
    if (role !== "ENTERPRISE") {
      setEnterpriseIdentity(null);
      return undefined;
    }

    const cachedIdentity = readEnterpriseIdentitySnapshot(userId);
    if (cachedIdentity) {
      setEnterpriseIdentity(cachedIdentity);
    }

    const providedName = normalizeRoleIdentityText(displayName);
    const providedCompanyName = normalizeRoleIdentityText(userSubtitle)
      || normalizeRoleIdentityText(userAvatarDisplayName);
    if (providedName || providedCompanyName || userAvatarUrl !== undefined) {
      const nextSnapshot = writeEnterpriseIdentitySnapshot({
        userId,
        realName: providedName,
        displayName: providedName,
        companyName: providedCompanyName,
        logoUrl: userAvatarUrl,
      });
      setEnterpriseIdentity(nextSnapshot);
    }

    const activeSnapshot = readEnterpriseIdentitySnapshot(userId);
    if (!userId || !shouldRefreshEnterpriseIdentity(activeSnapshot)) {
      return undefined;
    }

    const controller = new AbortController();
    void fetchEnterpriseIdentitySnapshot(userId, controller.signal)
      .then((snapshot) => {
        if (!controller.signal.aborted) {
          setEnterpriseIdentity(snapshot);
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setEnterpriseIdentity(activeSnapshot ?? null);
        }
      });

    return () => controller.abort();
  }, [displayName, role, userAvatarDisplayName, userAvatarUrl, userId, userSubtitle]);

  if (topbarCapture?.captureRoleTopbar) {
    return null;
  }

  return (
    <nav className="sticky top-0 z-40 border-b border-white/60 bg-white/72 px-4 py-3 backdrop-blur-xl sm:px-6">
      <div
        className={joinClasses(
          "mx-auto grid w-full grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] items-center gap-4",
          maxWidthClassName,
        )}
      >
        <div className="col-start-1 flex min-w-0 items-center gap-3 justify-self-start">
          {resolvedBrandHref ? (
            <Link
              to={resolvedBrandHref}
              className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-indigo-600 via-violet-500 to-teal-400 text-white shadow-[0_18px_35px_rgba(79,70,229,0.28)]"
            >
              <Icon size={20} />
            </Link>
          ) : (
            <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-indigo-600 via-violet-500 to-teal-400 text-white shadow-[0_18px_35px_rgba(79,70,229,0.28)]">
              <Icon size={20} />
            </div>
          )}
          <div className="flex min-h-11 min-w-0 items-center">
            <div className="truncate text-xl font-bold leading-tight text-slate-950">{title}</div>
          </div>
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
          {onRefresh ? (
            <button
              type="button"
              onClick={onRefresh}
              title={refreshTitle}
              className="inline-flex h-10 items-center justify-center rounded-full border border-slate-200 bg-white px-4 text-sm font-semibold text-slate-600 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600"
            >
              <RefreshCw size={17} className={joinClasses(refreshing && "animate-spin")} />
            </button>
          ) : null}
          {rightActions}
          <NotificationBellButton />
          {resolvedDisplayName ? (
            <div ref={userMenuRef} className="relative hidden md:block">
            <button
              type="button"
              onClick={() => setUserMenuOpen((current) => !current)}
              className="inline-flex items-center gap-3 rounded-full border border-white/80 bg-white/80 px-3 py-1.5 shadow-sm transition-colors hover:border-indigo-200"
            >
              {role === "ENTERPRISE" ? (
                <EnterpriseIdentityLogo
                  companyName={resolvedUserAvatarDisplayName}
                  logoUrl={resolvedUserAvatarUrl}
                  alt={`${resolvedUserAvatarDisplayName} 的公司 Logo`}
                  className="h-10 w-10 rounded-[1.1rem] shadow-sm"
                  imageClassName="p-0"
                  fallbackClassName="bg-slate-50 text-indigo-600"
                  textClassName="text-sm"
                  fallbackLabel={getInitial(resolvedUserAvatarDisplayName, userFallbackInitial)}
                />
              ) : (
                <MentorIdentityAvatar
                  userId={role === "MENTOR" ? userId : null}
                  displayName={resolvedUserAvatarDisplayName}
                  avatarUrl={resolvedUserAvatarUrl}
                  alt={`${resolvedUserAvatarDisplayName} 的头像`}
                  className="h-10 w-10 shadow-sm"
                  fallbackClassName="bg-slate-50 text-indigo-600"
                  textClassName="text-sm"
                  fallbackLabel={getInitial(resolvedUserAvatarDisplayName, userFallbackInitial)}
                />
              )}
              <div className="text-left leading-tight">
                <div className="text-sm font-semibold text-slate-900">{resolvedDisplayName}</div>
                {resolvedUserSubtitle ? (
                  <div className="text-xs text-slate-500">{resolvedUserSubtitle}</div>
                ) : null}
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
                  {resolvedMenuItems.map((item) => {
                    if (item.to) {
                      return (
                        <Link
                          key={`${item.label}-${item.to}`}
                          to={item.to}
                          onClick={() => setUserMenuOpen(false)}
                          className={joinClasses(
                            "flex w-full items-center rounded-[1rem] px-4 py-3 text-sm font-semibold transition-colors",
                            getMenuItemClassName(item.tone),
                          )}
                        >
                          {item.label}
                        </Link>
                      );
                    }

                    return (
                      <button
                        key={item.label}
                        type="button"
                        onClick={() => {
                          setUserMenuOpen(false);
                          item.onClick?.();
                        }}
                        className={joinClasses(
                          "flex w-full items-center rounded-[1rem] px-4 py-3 text-sm font-semibold transition-colors",
                          getMenuItemClassName(item.tone),
                        )}
                      >
                        {item.label}
                      </button>
                    );
                  })}
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
          ) : null}
        </div>
      </div>
    </nav>
  );
}
