import { Building2 } from "lucide-react";
import { type ReactNode } from "react";
import WorkspaceRoleTopbar from "../workspace/WorkspaceRoleTopbar";
import type { WorkspaceNavItem } from "../../lib/workspaceNav";

type EnterpriseWorkspaceShellProps = {
  sectionLabel: string;
  title: string;
  displayName: string | null | undefined;
  companyName: string | null | undefined;
  logoUrl?: string | null;
  userSubtitle?: string | null;
  navItems: WorkspaceNavItem[];
  loading: boolean;
  lastUpdatedAt: string | null;
  onRefresh: () => void;
  children: ReactNode;
};

export default function EnterpriseWorkspaceShell({
  sectionLabel,
  title,
  displayName,
  companyName,
  logoUrl,
  userSubtitle,
  navItems,
  loading,
  lastUpdatedAt,
  onRefresh,
  children,
}: EnterpriseWorkspaceShellProps) {
  return (
    <div className="relative min-h-screen bg-[#eef3ff] text-slate-900">
      <div className="pointer-events-none fixed inset-0 z-0 overflow-hidden">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(71,85,105,0.12),transparent_30%),radial-gradient(circle_at_bottom_right,rgba(14,165,233,0.08),transparent_28%),linear-gradient(180deg,#f1f5f9_0%,#f8fafc_58%,#f8fafc_100%)]" />
        <div className="page-top-glow page-top-glow--slate" />
      </div>

      <WorkspaceRoleTopbar
        sectionLabel={sectionLabel}
        title={title}
        icon={Building2}
        navItems={navItems}
        displayName={displayName}
        userSubtitle={userSubtitle ?? (companyName?.trim() || "企业账号")}
        userFallbackLabel="企业代表"
        userFallbackInitial="企"
        userAvatarUrl={logoUrl}
        userAvatarDisplayName={companyName?.trim() || displayName?.trim() || "企业"}
        lastUpdatedAt={lastUpdatedAt}
        onRefresh={onRefresh}
        refreshing={loading}
        refreshTitle={`刷新${title}数据`}
      />

      <main className="relative z-10 mx-auto w-full max-w-[96rem] px-4 pb-6 pt-8 sm:px-6 lg:px-8 lg:pb-8 lg:pt-10">
        {children}
      </main>
    </div>
  );
}
