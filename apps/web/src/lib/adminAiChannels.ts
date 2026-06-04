export type AdminAiChannelCatalogItem = {
  channelCode: string;
  displayName: string;
  ownerDomain: string;
  frontEntry: string;
  taskType: string;
  sceneCode: string;
  summary: string;
};

export const adminAiChannelCatalog: AdminAiChannelCatalogItem[] = [
  {
    channelCode: "resume.optimize",
    displayName: "简历优化",
    ownerDomain: "学生 AI",
    frontEntry: "/ai/resume",
    taskType: "RESUME",
    sceneCode: "RESUME_OPTIMIZE",
    summary: "学生提交文本或 PDF 简历后触发的结构化优化与复盘链路。",
  },
  {
    channelCode: "community.pre-answer",
    displayName: "社区预答",
    ownerDomain: "社区",
    frontEntry: "/community",
    taskType: "COMMUNITY_REPLY",
    sceneCode: "COMMUNITY_PRE_ANSWER",
    summary: "围绕帖子草稿或讨论上下文生成预答与辅助思路。",
  },
  {
    channelCode: "community.mentor-prep-sheet",
    displayName: "导师准备单",
    ownerDomain: "导师广场",
    frontEntry: "/mentors",
    taskType: "COMMUNITY_REPLY",
    sceneCode: "MENTOR_PREP_SHEET_GENERATE",
    summary: "学生从导师广场进入咨询前，用于生成准备单与材料提示。",
  },
  {
    channelCode: "student.portrait.summary",
    displayName: "学生画像总结",
    ownerDomain: "成长画像",
    frontEntry: "/profiles/students/me?tab=portrait",
    taskType: "PORTRAIT_SUMMARY",
    sceneCode: "STUDENT_PORTRAIT_SUMMARY",
    summary: "围绕学生成长画像生成表达层结论、优势待补点与下一步建议的低频辅助链路。",
  },
  {
    channelCode: "icebreak.message",
    displayName: "破冰消息",
    ownerDomain: "导师广场",
    frontEntry: "/mentors",
    taskType: "ICEBREAK",
    sceneCode: "ICEBREAK_MESSAGE",
    summary: "帮助学生生成与导师建立联系的破冰开场语。",
  },
  {
    channelCode: "interview.opening",
    displayName: "面试首问",
    ownerDomain: "AI 面试",
    frontEntry: "/ai/interview",
    taskType: "INTERVIEW_TEXT",
    sceneCode: "INTERVIEW_OPENING",
    summary: "AI 模拟面试开始时生成第一轮主问题与语境。",
  },
  {
    channelCode: "interview.reply",
    displayName: "面试追问",
    ownerDomain: "AI 面试",
    frontEntry: "/ai/interview/session",
    taskType: "INTERVIEW_TEXT",
    sceneCode: "INTERVIEW_REPLY",
    summary: "学生作答后继续追问与轻点评的主对话链路。",
  },
  {
    channelCode: "interview.answer-helper",
    displayName: "回答辅助诊断",
    ownerDomain: "AI 面试",
    frontEntry: "/ai/interview/session",
    taskType: "INTERVIEW_TEXT",
    sceneCode: "INTERVIEW_ANSWER_HELPER",
    summary: "对最近一轮回答做独立结构化诊断，不侵入主会话 prompt。",
  },
  {
    channelCode: "interview.summary",
    displayName: "面试总结",
    ownerDomain: "AI 面试",
    frontEntry: "/ai/interview/review",
    taskType: "INTERVIEW_SUMMARY",
    sceneCode: "INTERVIEW_SUMMARY",
    summary: "面试结束后生成总结、优劣势与建议的链路。",
  },
  {
    channelCode: "interview.voice-transcribe",
    displayName: "语音转写",
    ownerDomain: "AI 面试",
    frontEntry: "/ai/interview/session",
    taskType: "STT",
    sceneCode: "INTERVIEW_VOICE_TRANSCRIBE",
    summary: "语音模式下把用户音频转为文本，再进入既有追问链路。",
  },
  {
    channelCode: "interview.tts",
    displayName: "面试播报 TTS",
    ownerDomain: "AI 面试",
    frontEntry: "/ai/interview/session",
    taskType: "TTS",
    sceneCode: "TEXT_TO_SPEECH",
    summary: "将 AI 面试官消息与报告总结转成可播放音频。",
  },
];
