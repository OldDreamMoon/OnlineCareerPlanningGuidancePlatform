export type StudentNameSource = {
  displayName?: string | null;
  realName?: string | null;
};

type StudentNameOptions = {
  fallbackLabel?: string;
  showRealName?: boolean;
  separator?: "paren" | "dot";
};

function normalizeStudentName(value?: string | null) {
  const normalized = value?.trim().replace(/\s+/g, " ");
  return normalized ? normalized : null;
}

export function buildStudentNickname(
  source?: StudentNameSource | null,
  fallbackDisplayName?: string | null,
  fallbackLabel = "同学",
) {
  return normalizeStudentName(source?.displayName)
    || normalizeStudentName(fallbackDisplayName)
    || normalizeStudentName(source?.realName)
    || fallbackLabel;
}

export function buildStudentNameWithRealName(
  source?: StudentNameSource | null,
  fallbackDisplayName?: string | null,
  options?: StudentNameOptions,
) {
  const nickname = buildStudentNickname(source, fallbackDisplayName, options?.fallbackLabel ?? "同学");
  const realName = normalizeStudentName(source?.realName);
  if (!options?.showRealName || !realName || realName === nickname) {
    return nickname;
  }

  if (options.separator === "dot") {
    return `${nickname} · ${realName}`;
  }

  return `${nickname}（${realName}）`;
}
