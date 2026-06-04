import { motion } from "framer-motion";
import {
  MessageSquare,
  ShieldCheck,
  Sparkles,
  Trophy,
  type LucideIcon,
} from "lucide-react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";
import NotificationBellButton from "../notifications/NotificationBellButton";
import StudentWorkspaceTopbar, { buildStudentWorkspacePrimaryNav } from "../student/StudentWorkspaceTopbar";
import WorkspaceRoleTopbar from "../workspace/WorkspaceRoleTopbar";
import { getMentorWorkspaceNavItems } from "../../lib/workspaceNav";
import { getDisplayInitial, joinClasses } from "./communityUtils";

type CommunityHeroStat = {
  label: string;
  value: string;
  hint?: string;
  accentClassName?: string;
};

type CommunityModuleLayoutProps = {
  activeTab: "feed" | "leaderboard" | "reports";
  sectionLabel?: string;
  topbarSectionLabel?: string;
  topbarTitle?: string;
  userSubtitle?: string;
  brandHref?: string;
  hideTabs?: boolean;
  title: string;
  description: string;
  stats?: CommunityHeroStat[];
  heroAddon?: ReactNode;
  children: ReactNode;
};

type TabDefinition = {
  key: "feed" | "leaderboard" | "reports";
  label: string;
  href: string;
  icon: LucideIcon;
};

const TAB_DEFINITIONS: TabDefinition[] = [
  { key: "feed", label: "讨论大厅", href: "/community", icon: MessageSquare },
  { key: "leaderboard", label: "贡献榜", href: "/community/leaderboard", icon: Trophy },
  { key: "reports", label: "治理反馈", href: "/community/reports", icon: ShieldCheck },
];

export default function CommunityModuleLayout({
  activeTab,
  sectionLabel = "Community Hub",
  topbarSectionLabel,
  topbarTitle,
  userSubtitle,
  brandHref = "/community",
  hideTabs = false,
  title,
  description,
  stats = [],
  heroAddon,
  children,
}: CommunityModuleLayoutProps) {
  const { displayName, role } = useAuth();
  const resolvedTopbarSectionLabel = topbarSectionLabel ?? sectionLabel;
  const resolvedTopbarTitle = topbarTitle ?? title;
  const resolvedUserSubtitle = userSubtitle ?? (role === "MENTOR"
    ? "继续你的社区答疑"
    : role === "ENTERPRISE"
      ? "回看当前社区动态"
      : "继续你的社区互动");
  const shouldUseCompactHeroCards = role === "MENTOR";

  return (
    <div className="relative min-h-screen bg-slate-50 text-slate-900">
      <div className="pointer-events-none fixed inset-0 z-0 overflow-hidden">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(79,70,229,0.04),transparent_36%),linear-gradient(180deg,transparent_0%,#f8fafc_60%,#f8fafc_100%)]" />
        <div className="page-top-glow page-top-glow--sky" />
      </div>

      {role === "STUDENT" ? (
        <StudentWorkspaceTopbar
          sectionLabel={resolvedTopbarSectionLabel}
          title={resolvedTopbarTitle}
          brandHref={brandHref}
          navItems={buildStudentWorkspacePrimaryNav("community")}
          displayName={displayName}
          userSubtitle={resolvedUserSubtitle}
          position="sticky"
        />
      ) : role === "MENTOR" ? (
        <WorkspaceRoleTopbar
          sectionLabel={resolvedTopbarSectionLabel}
          title={resolvedTopbarTitle}
          icon={Sparkles}
          brandHref={brandHref}
          navItems={getMentorWorkspaceNavItems("community")}
          displayName={displayName}
          userSubtitle={resolvedUserSubtitle}
          userFallbackLabel="导师"
          userFallbackInitial="导"
        />
      ) : (
        <nav className="sticky top-0 z-40 border-b border-white/60 bg-white/72 px-4 py-3 backdrop-blur-xl sm:px-6">
          <div className="mx-auto flex w-full max-w-[96rem] items-center justify-between gap-4">
            <Link to={brandHref} className="flex items-center gap-3">
              <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-gradient-to-br from-indigo-600 via-violet-500 to-teal-400 text-white shadow-[0_18px_35px_rgba(79,70,229,0.28)]">
                <Sparkles size={20} />
              </div>
              <div className="flex min-h-11 min-w-0 items-center">
                <div className="truncate text-xl font-bold leading-tight text-slate-950">{resolvedTopbarTitle}</div>
              </div>
            </Link>

            <div className="flex items-center gap-3">
              <NotificationBellButton />
              <div className="hidden items-center gap-3 rounded-full border border-white/80 bg-white/80 px-3 py-1.5 shadow-sm md:flex">
                <div className="flex h-10 w-10 items-center justify-center rounded-full border border-slate-200 bg-slate-50 text-sm font-bold text-indigo-600">
                  {getDisplayInitial(displayName)}
                </div>
                <div className="pr-2 leading-tight">
                  <div className="text-sm font-semibold text-slate-900">{displayName || "当前用户"}</div>
                  <div className="text-xs text-slate-500">{resolvedUserSubtitle}</div>
                </div>
              </div>
            </div>
          </div>
        </nav>
      )}

      <main className="relative z-10 mx-auto w-full max-w-[96rem] px-4 pb-6 pt-8 sm:px-6 lg:px-8 lg:pb-8 lg:pt-10">
        <motion.section
          initial={{ opacity: 0, y: 18 }}
          animate={{ opacity: 1, y: 0 }}
          className="relative overflow-hidden rounded-[2.3rem] border border-white bg-gradient-to-br from-indigo-50/85 via-white to-sky-50/60 px-7 py-8 shadow-[0_12px_48px_rgba(15,23,42,0.06)] backdrop-blur-md lg:px-10 lg:py-10"
        >
          <div className="absolute -right-12 -top-12 opacity-[0.04] text-indigo-900">
            <MessageSquare size={220} />
          </div>

          <div className="relative flex flex-col gap-6 lg:flex-row lg:items-center lg:justify-between">
            <div className="max-w-3xl space-y-4">
              <div>
                <h1 className="text-3xl font-black tracking-tight text-slate-900 lg:text-4xl">{title}</h1>
                <p className="mt-3 text-[15px] leading-8 text-slate-600">{description}</p>
              </div>
            </div>

            <div className="flex flex-col gap-4 lg:items-end">
              {stats.length > 0 ? (
                <div className="grid w-full gap-3 sm:grid-cols-2 lg:max-w-[42rem] xl:grid-cols-3">
                  {stats.map((stat) => (
                    <div
                      key={`${stat.label}-${stat.value}`}
                      className={joinClasses(
                        "rounded-[1.4rem] border border-slate-100 bg-white/70 shadow-sm backdrop-blur-sm",
                        shouldUseCompactHeroCards ? "min-h-[9.75rem] px-4 py-4" : "px-5 py-4",
                      )}
                    >
                      <div className="text-[11px] font-medium text-slate-500">{stat.label}</div>
                      <div
                        className={joinClasses(
                          "mt-2 font-black tracking-tight",
                          shouldUseCompactHeroCards ? "text-[1.45rem] leading-8" : "text-3xl tabular-nums",
                          stat.accentClassName ?? "text-indigo-600",
                        )}
                      >
                        {stat.value}
                      </div>
                      {stat.hint ? (
                        <div className={joinClasses("mt-2 text-slate-500", shouldUseCompactHeroCards ? "text-[12px] leading-6" : "text-xs")}>
                          {stat.hint}
                        </div>
                      ) : null}
                    </div>
                  ))}
                </div>
              ) : null}
              {heroAddon}
            </div>
          </div>
        </motion.section>

        {hideTabs ? null : (
          <div className="mt-7 mb-7 flex justify-center">
            <div className="inline-flex flex-wrap items-center justify-center gap-2 rounded-[1.8rem] border border-emerald-100 bg-[linear-gradient(135deg,rgba(255,255,255,0.94),rgba(236,253,245,0.92),rgba(240,249,255,0.92))] p-2.5 shadow-[0_16px_34px_rgba(148,163,184,0.14)] backdrop-blur-xl">
              {TAB_DEFINITIONS.map((tab) => (
                <Link
                  key={tab.key}
                  to={tab.href}
                  className={joinClasses(
                    "relative inline-flex min-w-[10.75rem] items-center justify-center gap-2.5 rounded-[1.15rem] px-5 py-3.5 text-[1.04rem] font-semibold tracking-[0.01em] transition-all duration-200",
                    activeTab === tab.key
                      ? "bg-[linear-gradient(135deg,rgba(236,253,245,0.98),rgba(224,242,254,0.98))] text-teal-900 shadow-[0_12px_24px_rgba(148,163,184,0.16)]"
                      : "text-slate-600 hover:bg-white/76 hover:text-teal-800",
                  )}
                >
                  <tab.icon size={18} className={activeTab === tab.key ? "text-teal-700" : "text-emerald-500"} />
                  {tab.label}
                </Link>
              ))}
            </div>
          </div>
        )}

        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          className={joinClasses("text-[15px]", hideTabs && "mt-7")}
        >
          {children}
        </motion.div>
      </main>
    </div>
  );
}
