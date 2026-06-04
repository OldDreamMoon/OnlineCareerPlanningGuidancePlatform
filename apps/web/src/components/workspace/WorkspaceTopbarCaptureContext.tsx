import { createContext, useContext, type ReactNode } from "react";
import type { LucideIcon } from "lucide-react";
import type { StudentAvatarMeta } from "../../lib/studentAvatar";
import type { WorkspaceNavItem } from "../../lib/workspaceNav";

export type CapturedStudentTopbarConfig = {
  sectionLabel?: string;
  title?: string;
  brandHref?: string;
  brandIcon?: LucideIcon;
  navItems?: WorkspaceNavItem[];
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

export type CapturedRoleTopbarConfig = {
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
  menuItems?: Array<{
    label: string;
    to?: string;
    onClick?: () => void;
    tone?: "default" | "accent" | "danger";
  }>;
  maxWidthClassName?: string;
};

type WorkspaceTopbarCaptureContextValue = {
  captureStudentTopbar?: (config: CapturedStudentTopbarConfig, signature: string) => void;
  captureRoleTopbar?: (config: CapturedRoleTopbarConfig, signature: string) => void;
};

const WorkspaceTopbarCaptureContext = createContext<WorkspaceTopbarCaptureContextValue | null>(null);

export function WorkspaceTopbarCaptureProvider({
  value,
  children,
}: {
  value: WorkspaceTopbarCaptureContextValue;
  children: ReactNode;
}) {
  return (
    <WorkspaceTopbarCaptureContext.Provider value={value}>
      {children}
    </WorkspaceTopbarCaptureContext.Provider>
  );
}

export function useWorkspaceTopbarCapture() {
  return useContext(WorkspaceTopbarCaptureContext);
}
