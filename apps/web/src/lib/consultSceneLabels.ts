function normalizeSceneCode(sceneCode?: string | null) {
  const normalized = sceneCode?.trim();
  return normalized ? normalized.toUpperCase() : null;
}

export function resolveConsultSceneLabel(options: {
  sceneCode?: string | null;
  prepScene?: string | null;
  hasAppointment?: boolean;
}) {
  const prepScene = options.prepScene?.trim();
  if (prepScene) {
    return prepScene;
  }

  switch (normalizeSceneCode(options.sceneCode)) {
    case "RESUME_DIAGNOSIS":
      return "简历诊断";
    case "PROJECT_EXPRESSION":
    case "PROJECT_STORYTELLING":
      return "项目表达";
    case "INTERVIEW_REVIEW":
    case "MOCK_INTERVIEW_REVIEW":
      return "模拟面试复盘";
    case "CAREER_DIRECTION":
      return "岗位方向选择";
    case "DELIVERY_STRATEGY":
    case "CAMPUS_RECRUITMENT_STRATEGY":
      return "校招投递策略";
    case "CAREER_TRANSITION":
      return "转行 / 跨专业求职";
    case "OFFER_DECISION":
      return "Offer 对比与决策";
    case "DATA_ANALYSIS":
      return "数据分析";
    case "GENERAL_CONSULT":
      return "综合咨询";
    default:
      return options.hasAppointment ? "预约咨询" : "图文咨询";
  }
}
