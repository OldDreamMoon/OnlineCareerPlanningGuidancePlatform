import type { ThemeConfig } from "antd";

export const adminThemeConfig: ThemeConfig = {
  token: {
    colorPrimary: "#5b61f6",
    colorInfo: "#5b61f6",
    colorSuccess: "#10b981",
    colorWarning: "#f59e0b",
    colorError: "#ef4444",
    colorTextBase: "#172033",
    colorTextSecondary: "#5f6b85",
    colorBgBase: "#f3f6ff",
    colorBorder: "#d8def0",
    borderRadius: 16,
    borderRadiusLG: 20,
    fontFamily: "\"Inter\", \"PingFang SC\", \"Microsoft YaHei\", sans-serif",
  },
  components: {
    Card: {
      headerBg: "transparent",
      colorBorderSecondary: "#e6ebf5",
    },
    Button: {
      borderRadius: 14,
      controlHeight: 40,
      fontWeight: 600,
    },
    Input: {
      borderRadius: 14,
      activeBorderColor: "#5b61f6",
      hoverBorderColor: "#8b93ff",
    },
    Select: {
      optionSelectedBg: "#eef0ff",
    },
    Tabs: {
      itemSelectedColor: "#5b61f6",
      inkBarColor: "#5b61f6",
    },
    Table: {
      headerBg: "#f7f9ff",
      headerColor: "#40506e",
      borderColor: "#e7ebf4",
      rowHoverBg: "#f7f9ff",
    },
    Drawer: {
      colorBgElevated: "#ffffff",
    },
  },
};

export const adminRoleAvatarClassNameByRole = {
  STUDENT: "admin-role-avatar admin-role-avatar--student",
  MENTOR: "admin-role-avatar admin-role-avatar--mentor",
  ENTERPRISE: "admin-role-avatar admin-role-avatar--enterprise",
  ADMIN: "admin-role-avatar admin-role-avatar--admin",
} as const;

export function getAdminRoleAvatarClassName(role?: string | null) {
  switch (role) {
    case "MENTOR":
      return adminRoleAvatarClassNameByRole.MENTOR;
    case "ENTERPRISE":
      return adminRoleAvatarClassNameByRole.ENTERPRISE;
    case "ADMIN":
      return adminRoleAvatarClassNameByRole.ADMIN;
    default:
      return adminRoleAvatarClassNameByRole.STUDENT;
  }
}
