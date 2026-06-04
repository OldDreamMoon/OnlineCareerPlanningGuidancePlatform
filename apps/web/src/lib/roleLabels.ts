export function getRoleDisplayLabel(role: string | null | undefined) {
  switch (role) {
    case "ADMIN":
      return "管理员";
    case "MENTOR":
      return "导师";
    case "ENTERPRISE":
      return "企业";
    case "STUDENT":
      return "学生";
    default:
      return "用户";
  }
}
