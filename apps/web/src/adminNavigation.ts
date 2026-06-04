export type AdminRouteKey =
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

export const adminRouteKeys: AdminRouteKey[] = [
  "dashboard",
  "users",
  "enterprise-tasks",
  "mentor-operations",
  "notifications",
  "ai-applications",
  "ai-gateway",
  "content",
  "payments",
  "skills",
  "runtime",
];

export const adminRouteByKey: Record<AdminRouteKey, string> = {
  dashboard: "/admin/dashboard",
  users: "/admin/users",
  "enterprise-tasks": "/admin/enterprise/tasks",
  "mentor-operations": "/admin/mentors/operations",
  notifications: "/admin/notifications",
  "ai-applications": "/admin/ai/applications",
  "ai-gateway": "/admin/ai/gateway",
  content: "/admin/content",
  payments: "/admin/consult/orders",
  skills: "/admin/skills",
  runtime: "/admin/runtime",
};

export const adminLabelByKey: Record<AdminRouteKey, string> = {
  dashboard: "总览",
  users: "用户与认证",
  "enterprise-tasks": "企业任务",
  "mentor-operations": "导师经营",
  notifications: "通知运营",
  "ai-applications": "AI 应用运营",
  "ai-gateway": "AI 网关",
  content: "内容治理",
  payments: "支付与售后",
  skills: "技能资源",
  runtime: "运行配置",
};

export const adminHeaderMetaByKey: Record<AdminRouteKey, { title: string; showLocation: boolean; hasNotifications: boolean }> = {
  dashboard: {
    title: "管理员总览",
    showLocation: false,
    hasNotifications: true,
  },
  users: {
    title: "用户与认证中心",
    showLocation: true,
    hasNotifications: true,
  },
  "enterprise-tasks": {
    title: "企业任务治理台",
    showLocation: true,
    hasNotifications: true,
  },
  "mentor-operations": {
    title: "导师经营治理台",
    showLocation: true,
    hasNotifications: true,
  },
  notifications: {
    title: "通知运营台",
    showLocation: true,
    hasNotifications: true,
  },
  "ai-applications": {
    title: "AI 应用运营台",
    showLocation: true,
    hasNotifications: false,
  },
  "ai-gateway": {
    title: "AI 网关控制台",
    showLocation: true,
    hasNotifications: false,
  },
  content: {
    title: "内容治理中心",
    showLocation: true,
    hasNotifications: true,
  },
  payments: {
    title: "支付与售后工作台",
    showLocation: true,
    hasNotifications: true,
  },
  skills: {
    title: "技能资源治理台",
    showLocation: true,
    hasNotifications: true,
  },
  runtime: {
    title: "运行配置中心",
    showLocation: true,
    hasNotifications: true,
  },
};

export function getAdminRouteKey(pathname: string): AdminRouteKey {
  const matchedKey = adminRouteKeys.find((key) => pathname.startsWith(adminRouteByKey[key]));

  return matchedKey ?? "dashboard";
}
