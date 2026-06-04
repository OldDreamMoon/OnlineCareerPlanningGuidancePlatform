export type CommunityLeaderboardState = {
  leaderboardScore: string;
  leaderboardHint: string;
  rankingValue: string;
  rankingHint: string;
};

export type CommunityRoleExperience = {
  topbarSectionLabel: string;
  topbarUserSubtitle: string;
  heroTitle: string;
  heroDescription: string;
  unreadEmptyHint: string;
  identityLabel: string;
  participationLabel: string;
  sideCtaSectionLabel: string;
  sideCtaTitle: string;
  sideCtaDescription: string;
  sideCtaButtonLabel: string;
  latestActivityEmpty: string;
  emptyTagsDescription: string;
  searchPlaceholder: string;
  noResultDescription: string;
  createBadgeLabel: string;
  createTitle: string;
  createDescription: string;
  createGuideTitle: string;
  createGuideDescription: string;
  titlePlaceholder: string;
  tagPlaceholder: string;
  contentPlaceholder: string;
  composerNote: string;
  submitText: string;
  publishPendingModeration: string;
  publishAiSuccess: string;
  publishSuccess: string;
};

function isMentorRole(role: string | null | undefined) {
  return (role ?? "").toUpperCase() === "MENTOR";
}

const STUDENT_EXPERIENCE: CommunityRoleExperience = {
  topbarSectionLabel: "Community Feed",
  topbarUserSubtitle: "继续你的社区互动",
  heroTitle: "交流经验，解决卡点，连接导师",
  heroDescription: "在进入导师咨询或 AI 工具前，你可以先在这里沉淀问题、寻找同路人的经验，并把每一次提问都整理成更清晰的行动线索。",
  unreadEmptyHint: "当前没有新的社区事务提醒。",
  identityLabel: "我的社区分",
  participationLabel: "近 7 天定位",
  sideCtaSectionLabel: "发起讨论",
  sideCtaTitle: "开启新的讨论",
  sideCtaDescription: "把问题整理成一条清楚的讨论，会更容易收到具体、可执行的建议。",
  sideCtaButtonLabel: "写一条新讨论",
  latestActivityEmpty: "目前还没有新的社区事务提醒。后续有人回复、审核或举报结果更新时，会在这里提醒你。",
  emptyTagsDescription: "当前还没有热门标签，先看看最近的讨论。",
  searchPlaceholder: "搜索问题、面经或经验...",
  noResultDescription: "你可以换个关键词、切回全部场景，或者直接开启一条新的讨论。",
  createBadgeLabel: "发布讨论",
  createTitle: "开启新讨论",
  createDescription: "把背景、目标和卡点写清楚，更容易收到真正有帮助的回应。",
  createGuideTitle: "让大家更快看懂你的问题",
  createGuideDescription: "可以写清楚你现在的阶段、已经试过的方法，以及最希望大家帮你判断的地方。",
  titlePlaceholder: "一句话写清这条讨论最想解决的问题",
  tagPlaceholder: "可以补充岗位、阶段或问题关键词",
  contentPlaceholder: "把你的背景、目标、已尝试的方法和当前卡住的位置写清楚，大家会更容易给出具体建议。",
  composerNote: "写清背景、尝试过程和期待回复方向，会更容易收到高质量建议。",
  submitText: "发布到讨论大厅",
  publishPendingModeration: "讨论已提交，审核通过后就会出现在讨论大厅。",
  publishAiSuccess: "讨论已发布，先看看参考回应，也欢迎继续补充背景。",
  publishSuccess: "讨论已发布，欢迎继续补充背景，等候更多回复。",
};

const MENTOR_EXPERIENCE: CommunityRoleExperience = {
  topbarSectionLabel: "Mentor Community",
  topbarUserSubtitle: "分享经验与参与答疑",
  heroTitle: "分享经验，参与答疑，连接同学",
  heroDescription: "在这里分享经验、拆解问题，也能持续观察同学们最真实的求职卡点，把建议沉淀成更清晰的行动线索。",
  unreadEmptyHint: "当前没有新的答疑或社区提醒。",
  identityLabel: "导师角色",
  participationLabel: "当前状态",
  sideCtaSectionLabel: "分享经验",
  sideCtaTitle: "发布一条讨论",
  sideCtaDescription: "把经验、案例或关键判断整理成一条讨论，更容易帮助同学快速抓住重点。",
  sideCtaButtonLabel: "发布新讨论",
  latestActivityEmpty: "目前还没有新的社区动态。后续有人回复或状态更新时，会在这里提醒你。",
  emptyTagsDescription: "当前还没有热门标签，先看看最近的讨论。",
  searchPlaceholder: "搜索问题、案例或经验...",
  noResultDescription: "你可以换个关键词、切回全部场景，或者直接分享一条新的经验。",
  createBadgeLabel: "发布讨论",
  createTitle: "发布新讨论",
  createDescription: "把你的经验、判断和适用场景写清楚，更容易帮助同学直接用起来。",
  createGuideTitle: "让大家更快看懂你的分享",
  createGuideDescription: "可以写清楚适用场景、判断依据和行动建议，帮助同学更快理解怎么落地。",
  titlePlaceholder: "一句话写清这次想分享的经验或讨论的话题",
  tagPlaceholder: "可以补充岗位、阶段、场景或关键词",
  contentPlaceholder: "把你的经验、判断依据、适用场景和建议步骤写清楚，大家会更容易理解并跟进。",
  composerNote: "写清适用场景、关键判断和建议步骤，会更容易帮助同学直接行动。",
  submitText: "发布到讨论大厅",
  publishPendingModeration: "讨论已提交，审核通过后会展示在讨论大厅。",
  publishAiSuccess: "讨论已发布，欢迎继续补充你的观点与案例。",
  publishSuccess: "讨论已发布，欢迎继续补充你的经验和建议。",
};

export function getCommunityRoleExperience(role: string | null | undefined): CommunityRoleExperience {
  return isMentorRole(role) ? MENTOR_EXPERIENCE : STUDENT_EXPERIENCE;
}

export function getDefaultCommunityLeaderboardState(role: string | null | undefined): CommunityLeaderboardState {
  if (isMentorRole(role)) {
    return {
      leaderboardScore: "导师答疑",
      leaderboardHint: "这里展示你当前的社区参与状态。",
      rankingValue: "持续参与中",
      rankingHint: "继续分享经验，帮助更多同学推进问题。",
    };
  }

  return {
    leaderboardScore: "冲榜中",
    leaderboardHint: "继续分享与回复即可累计社区分。",
    rankingValue: "Top 50 外",
    rankingHint: "近 7 天榜单按公开帖子、评论和获赞计算。",
  };
}
