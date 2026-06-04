import { apiRequest, buildQuery } from "./apiClient";

type TimeValue = number | string | null;

export type CommunityResolvedStatus = "OPEN" | "RESOLVED" | "CLOSED" | string;
export type CommunityModerationStatus = "PASS" | "REVIEW" | "BLOCK" | string;
export type CommunityRiskLevel = "LOW" | "MEDIUM" | "HIGH" | string;

export type CommunityModerationPayload = {
  sourceType: string;
  riskLevel: CommunityRiskLevel;
  action: string;
  reasonCode: string;
};

export type CommunityPostSummary = {
  postId: number;
  authorUserId: number;
  authorDisplayName: string;
  authorRealName?: string | null;
  authorShowRealName?: boolean;
  authorRole: string;
  authorAvatarUrl?: string | null;
  title: string;
  scenarioCode: string;
  resolvedStatus: CommunityResolvedStatus;
  content: string;
  tags: string[];
  commentCount: number;
  likeCount: number;
  likedByMe: boolean;
  moderationStatus: CommunityModerationStatus;
  riskLevel: CommunityRiskLevel;
  hasMentorReply: boolean;
  authoredByMe: boolean;
  participatedByMe: boolean;
  createdAt: TimeValue;
  updatedAt: TimeValue;
};

export type CommunityComment = {
  commentId: number;
  userId: number;
  displayName: string;
  realName?: string | null;
  showRealName?: boolean;
  role: string;
  avatarUrl?: string | null;
  content: string;
  ai: boolean;
  createdAt: TimeValue;
};

export type CommunityPostDetail = {
  postId: number;
  authorUserId: number;
  authorDisplayName: string;
  authorRealName?: string | null;
  authorShowRealName?: boolean;
  authorRole: string;
  authorAvatarUrl?: string | null;
  title: string;
  scenarioCode: string;
  resolvedStatus: CommunityResolvedStatus;
  moderationStatus: CommunityModerationStatus;
  riskLevel: CommunityRiskLevel;
  content: string;
  tags: string[];
  commentCount: number;
  likeCount: number;
  likedByMe: boolean;
  comments: CommunityComment[];
  createdAt: TimeValue;
  updatedAt: TimeValue;
};

export type CommunityPostListData = {
  records: CommunityPostSummary[];
  total: number;
  page: number;
  size: number;
};

export type CommunityPostCreateResponse = {
  postId: number;
  moderation: CommunityModerationPayload;
  aiFirstCommentCreated: boolean;
  aiFirstCommentId: number | null;
};

export type CommunityCommentCreateResponse = {
  commentId: number;
  moderation: CommunityModerationPayload;
};

export type CommunityLikeResponse = {
  liked: boolean;
  likeCount: number;
};

export type CommunityLeaderboardItem = {
  rank: number;
  studentUserId: number;
  displayName: string;
  score: number;
  postCount: number;
  commentCount: number;
  likeReceivedCount: number;
  latestActivityAt: TimeValue;
};

export type CommunityLeaderboardData = {
  window: string;
  formula: string;
  records: CommunityLeaderboardItem[];
  total: number;
  page: number;
  size: number;
};

export type CommunityPostStatusUpdateResponse = {
  postId: number;
  resolvedStatus: CommunityResolvedStatus;
  updatedAt: TimeValue;
};

export type CommunityReportRecord = {
  reportId: number;
  targetType: string;
  targetId: string;
  contentPostId: string | null;
  contentTitle: string | null;
  contentBody: string | null;
  reasonCode: string;
  detail: string | null;
  status: string;
  latestAction: string | null;
  createdAt: TimeValue;
  updatedAt: TimeValue;
};

export type CommunityReportListData = {
  records: CommunityReportRecord[];
  total: number;
  page: number;
  size: number;
};

export type CommunityReportCreateResponse = {
  reportId: number;
  status: string;
};

export type CommunityAiPreAnswerResponse = {
  draftComment: string;
  tag: string;
  moderation: {
    sourceType: string;
    riskLevel: string;
    action: string;
    reasonCode: string;
  } | null;
};

export type CommunityScenarioOption = {
  code: string;
  label: string;
  description: string;
};

export type CommunityReportReasonOption = {
  code: string;
  label: string;
};

const COMMUNITY_AI_DRAFT_MARKDOWN_PATTERN = /(^#{1,6}\s)|(^[-*+]\s)|(^\d+\.\s)|(^>\s)|```|(\[[^\]]+\]\([^)]+\))|(`[^`]+`)|(\*\*[^*]+\*\*)/m;

export const COMMUNITY_SCENARIO_OPTIONS: CommunityScenarioOption[] = [
  { code: "GENERAL_HELP", label: "综合求助", description: "适合暂时无法准确归类的求职问题。" },
  { code: "RESUME_REVIEW", label: "简历问诊", description: "聚焦简历结构、项目包装和投递反馈。" },
  { code: "INTERVIEW_EXPERIENCE", label: "面经分享", description: "沉淀真实面试流程、考点与复盘。" },
  { code: "WRITTEN_TEST_HELP", label: "笔试求助", description: "围绕笔试题型、算法和时间分配交流。" },
  { code: "OFFER_COMPARISON", label: "Offer 比较", description: "适合 offer 选择、薪资和发展路径讨论。" },
  { code: "CAREER_DIRECTION", label: "求职规划", description: "聚焦岗位方向、时间线和行动计划。" },
];

export const COMMUNITY_REPORT_REASON_OPTIONS: CommunityReportReasonOption[] = [
  { code: "ABUSE", label: "人身攻击" },
  { code: "SPAM", label: "广告引流" },
  { code: "MISLEADING", label: "不实信息" },
  { code: "RISK_LINK", label: "风险链接" },
  { code: "EXPLICIT", label: "低俗违规" },
  { code: "OTHER", label: "其他" },
];

function stripDraftListPrefix(value: string) {
  return value.replace(/^[-*+\d.\s]+/, "").trim();
}

export function ensureCommunityAiDraftMarkdown(rawDraft: string | null | undefined) {
  const normalizedDraft = rawDraft?.replace(/\r\n?/g, "\n").trim() ?? "";
  if (!normalizedDraft) {
    return "";
  }

  if (COMMUNITY_AI_DRAFT_MARKDOWN_PATTERN.test(normalizedDraft)) {
    return normalizedDraft;
  }

  const normalizedLineText = normalizedDraft.replace(/\n+/g, " ").replace(/\s+/g, " ").trim();
  const sentenceCandidates = normalizedLineText
    .split(/(?<=[。！？；])/)
    .map((item) => stripDraftListPrefix(item))
    .filter(Boolean);
  const paragraphCandidates = normalizedDraft
    .split(/\n+/)
    .map((item) => stripDraftListPrefix(item))
    .filter(Boolean);

  const intro = sentenceCandidates[0] ?? paragraphCandidates[0] ?? normalizedLineText;
  const actionItemsSource = (sentenceCandidates.length > 1 ? sentenceCandidates.slice(1, 4) : paragraphCandidates.slice(1, 4))
    .map((item) => stripDraftListPrefix(item))
    .filter(Boolean);

  const lines = [
    "### 可参考的回复思路",
    "",
    intro,
  ];

  if (actionItemsSource.length > 0) {
    lines.push("", "#### 可以继续补充");
    actionItemsSource.forEach((item) => {
      lines.push(`- ${item}`);
    });
  }

  lines.push("", "> 你可以结合自己的真实情况继续删改，再决定是否发送。");
  return lines.join("\n");
}

export function listCommunityPosts(params: {
  page?: number;
  size?: number;
  keyword?: string;
  tag?: string;
  scenarioCode?: string;
  signal?: AbortSignal;
}) {
  return apiRequest<CommunityPostListData>(`/community/posts${buildQuery({
    page: params.page ?? 1,
    size: params.size ?? 10,
    keyword: params.keyword,
    tag: params.tag,
    scenarioCode: params.scenarioCode,
  })}`, {
    signal: params.signal,
  });
}

export function getCommunityPostDetail(postId: number) {
  return apiRequest<CommunityPostDetail>(`/community/posts/${postId}`);
}

export function createCommunityPost(command: {
  title: string;
  scenarioCode: string;
  content: string;
  tags: string[];
}) {
  return apiRequest<CommunityPostCreateResponse>("/community/posts", {
    method: "POST",
    body: JSON.stringify(command),
  });
}

export function createCommunityComment(postId: number, command: { content: string }) {
  return apiRequest<CommunityCommentCreateResponse>(`/community/posts/${postId}/comments`, {
    method: "POST",
    body: JSON.stringify(command),
  });
}

export function updateCommunityPostStatus(postId: number, resolvedStatus: CommunityResolvedStatus) {
  return apiRequest<CommunityPostStatusUpdateResponse>(`/community/posts/${postId}/status`, {
    method: "POST",
    body: JSON.stringify({ resolvedStatus }),
  });
}

export function likeCommunityPost(postId: number) {
  return apiRequest<CommunityLikeResponse>(`/community/posts/${postId}/like`, {
    method: "POST",
  });
}

export function unlikeCommunityPost(postId: number) {
  return apiRequest<CommunityLikeResponse>(`/community/posts/${postId}/like`, {
    method: "DELETE",
  });
}

export function getCommunityLeaderboard(params: { page?: number; size?: number; window?: string }) {
  return apiRequest<CommunityLeaderboardData>(`/community/leaderboard${buildQuery({
    page: params.page ?? 1,
    size: params.size ?? 20,
    window: params.window ?? "7d",
  })}`);
}

export function listCommunityReports(params: { page?: number; size?: number; status?: string }) {
  return apiRequest<CommunityReportListData>(`/community/reports/mine${buildQuery({
    page: params.page ?? 1,
    size: params.size ?? 12,
    status: params.status,
  })}`);
}

export function createCommunityReport(command: {
  targetType: string;
  targetId: string;
  reasonCode: string;
  detail?: string;
}) {
  return apiRequest<CommunityReportCreateResponse>("/community/reports", {
    method: "POST",
    body: JSON.stringify(command),
  });
}

export function generateCommunityPreAnswer(command: {
  postId: number;
  title?: string;
  content?: string;
}) {
  return apiRequest<CommunityAiPreAnswerResponse>("/ai/community/pre-answer", {
    method: "POST",
    body: JSON.stringify(command),
  });
}
