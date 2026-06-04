export type MentorNameSource = {
  displayName?: string | null;
  realName?: string | null;
  showRealName?: boolean | null;
  companyName?: string | null;
  jobTitle?: string | null;
};

type MentorNameOptions = {
  fallbackLabel?: string;
  emptyLabel?: string;
};

function normalizeMentorName(value?: string | null) {
  const normalized = value?.trim().replace(/\s+/g, " ");
  return normalized ? normalized : null;
}

export function buildMentorNickname(
  source?: MentorNameSource | null,
  fallbackDisplayName?: string | null,
  fallbackLabel = "导师",
) {
  return normalizeMentorName(source?.displayName)
    || normalizeMentorName(fallbackDisplayName)
    || normalizeMentorName(source?.realName)
    || fallbackLabel;
}

export function buildMentorDisplayNameWithRealNameIndicator(
  source?: MentorNameSource | null,
  fallbackDisplayName?: string | null,
  options?: MentorNameOptions,
) {
  const nickname = buildMentorNickname(source, fallbackDisplayName, options?.fallbackLabel ?? "导师");
  const realName = normalizeMentorName(source?.realName);
  if (!source?.showRealName || !realName || realName === nickname) {
    return nickname;
  }
  return `${nickname}（${realName}）`;
}

export function buildMentorIdentityLine(
  source?: MentorNameSource | null,
  _fallbackDisplayName?: string | null,
  options?: MentorNameOptions,
) {
  const realName = source?.showRealName ? normalizeMentorName(source?.realName) : null;
  const companyName = normalizeMentorName(source?.companyName);
  const jobTitle = normalizeMentorName(source?.jobTitle);
  const identityParts = [realName, companyName, jobTitle].filter(Boolean);
  if (identityParts.length > 0) {
    return identityParts.join(" · ");
  }
  return options?.emptyLabel ?? "公开身份待补充";
}
