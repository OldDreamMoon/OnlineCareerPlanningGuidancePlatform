export type WorkspaceNavItem = {
  label: string;
  to?: string;
  href?: string;
  active?: boolean;
};

type MentorWorkspaceSection = "dashboard" | "orders" | "profile" | "notifications" | "finance" | "community";
type EnterpriseWorkspaceSection = "dashboard" | "tasks" | "create" | "profile" | "notifications";

export const MENTOR_PROFILE_WORKSPACE_ENTRY = "/mentor/profile?tab=profile";
export const MENTOR_COMMUNITY_WORKSPACE_ENTRY = "/community";

export function getMentorWorkspaceNavItems(activeSection: MentorWorkspaceSection): WorkspaceNavItem[] {
  return [
    { to: "/mentor/dashboard", label: "工作台", active: activeSection === "dashboard" },
    { to: "/mentor/orders", label: "订单中心", active: activeSection === "orders" },
    { to: "/mentor/finance", label: "财务中心", active: activeSection === "finance" },
    { to: MENTOR_COMMUNITY_WORKSPACE_ENTRY, label: "社区广场", active: activeSection === "community" },
  ];
}

export function getEnterpriseWorkspaceNavItems(activeSection: EnterpriseWorkspaceSection): WorkspaceNavItem[] {
  return [
    { to: "/enterprise/dashboard", label: "工作台", active: activeSection === "dashboard" },
    { to: "/enterprise/tasks", label: "任务中心", active: activeSection === "tasks" },
    { to: "/enterprise/tasks/create", label: "发布任务", active: activeSection === "create" },
  ];
}
