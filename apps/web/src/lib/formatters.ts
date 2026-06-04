export type DateLike = Date | number | string | null | undefined;

const DATE_ONLY_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;
const TIMEZONE_LESS_DATETIME_PATTERN = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,9}))?)?$/;
const TIMEZONE_SUFFIX_PATTERN = /(Z|[+-]\d{2}:\d{2})$/i;
const DEFAULT_DISPLAY_TIME_ZONE = "Asia/Shanghai";
const UTC_LIKE_TIME_ZONE_PATTERN = /^(UTC|Etc\/UTC|Etc\/GMT|GMT|UCT|Zulu|Universal|Greenwich|Etc\/GMT[+-]?0?|GMT[+-]?0?(?::?00)?|UTC[+-]?0?(?::?00)?)$/i;

function buildLocalDate(
  year: number,
  month: number,
  day: number,
  hour = 0,
  minute = 0,
  second = 0,
  millisecond = 0,
) {
  const date = new Date(year, month - 1, day, hour, minute, second, millisecond);
  return Number.isNaN(date.getTime()) ? null : date;
}

function normalizeFractionToMilliseconds(value?: string) {
  if (!value) {
    return 0;
  }

  return Number(value.padEnd(3, "0").slice(0, 3));
}

function resolveInvalidDateFallback(value: DateLike, fallback: string) {
  if (typeof value === "string" && value.trim()) {
    return value;
  }
  return fallback;
}

function formatWithOptions(
  value: DateLike,
  options: Intl.DateTimeFormatOptions,
  fallback: string,
) {
  const date = parseDateValue(value);
  if (!date) {
    return resolveInvalidDateFallback(value, fallback);
  }

  return new Intl.DateTimeFormat("zh-CN", {
    ...options,
    timeZone: getBrowserTimeZone(),
  }).format(date);
}

function getDisplayDateParts(value: DateLike) {
  const date = parseDateValue(value);
  if (!date) {
    return null;
  }

  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: getBrowserTimeZone(),
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(date);

  const year = parts.find((part) => part.type === "year")?.value;
  const month = parts.find((part) => part.type === "month")?.value;
  const day = parts.find((part) => part.type === "day")?.value;

  if (!year || !month || !day) {
    return null;
  }

  return { year, month, day };
}

export function getBrowserTimeZone() {
  try {
    const resolvedTimeZone = Intl.DateTimeFormat().resolvedOptions().timeZone?.trim();
    if (resolvedTimeZone && !UTC_LIKE_TIME_ZONE_PATTERN.test(resolvedTimeZone)) {
      return resolvedTimeZone;
    }
  } catch {
    // 忽略运行时差异，统一回退到产品默认时区。
  }

  return DEFAULT_DISPLAY_TIME_ZONE;
}

export function parseDateValue(value: DateLike) {
  if (value instanceof Date) {
    return Number.isNaN(value.getTime()) ? null : new Date(value.getTime());
  }

  if (typeof value === "number") {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? null : date;
  }

  if (typeof value !== "string") {
    return null;
  }

  const normalized = value.trim();
  if (!normalized) {
    return null;
  }

  if (/^-?\d+$/.test(normalized)) {
    return parseDateValue(Number(normalized));
  }

  const dateOnlyMatch = normalized.match(DATE_ONLY_PATTERN);
  if (dateOnlyMatch) {
    return buildLocalDate(
      Number(dateOnlyMatch[1]),
      Number(dateOnlyMatch[2]),
      Number(dateOnlyMatch[3]),
    );
  }

  const timezoneLessDateTimeMatch = !TIMEZONE_SUFFIX_PATTERN.test(normalized)
    ? normalized.match(TIMEZONE_LESS_DATETIME_PATTERN)
    : null;
  if (timezoneLessDateTimeMatch) {
    return buildLocalDate(
      Number(timezoneLessDateTimeMatch[1]),
      Number(timezoneLessDateTimeMatch[2]),
      Number(timezoneLessDateTimeMatch[3]),
      Number(timezoneLessDateTimeMatch[4]),
      Number(timezoneLessDateTimeMatch[5]),
      Number(timezoneLessDateTimeMatch[6] ?? "0"),
      normalizeFractionToMilliseconds(timezoneLessDateTimeMatch[7]),
    );
  }

  const date = new Date(normalized);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function toTimestamp(value: DateLike) {
  const date = parseDateValue(value);
  return date ? date.getTime() : Number.NaN;
}

export function formatLocalDateKey(value: DateLike) {
  const parts = getDisplayDateParts(value);
  if (!parts) {
    return "";
  }

  return `${parts.year}-${parts.month}-${parts.day}`;
}

export function formatDateInputValue(value: DateLike) {
  return formatLocalDateKey(value);
}

export function isSameLocalDate(left: DateLike, right: DateLike) {
  const leftKey = formatLocalDateKey(left);
  const rightKey = formatLocalDateKey(right);
  return Boolean(leftKey && rightKey && leftKey === rightKey);
}

export function formatDateTime(value?: DateLike, fallback = "—") {
  return formatWithOptions(value, {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }, fallback);
}

export function formatDate(value?: DateLike, fallback = "—") {
  return formatWithOptions(value, {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }, fallback);
}

export function formatTime(value?: DateLike, fallback = "—") {
  return formatWithOptions(value, {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }, fallback);
}

export function formatMonthDay(value?: DateLike, fallback = "—") {
  const parts = getDisplayDateParts(value);
  if (!parts) {
    return resolveInvalidDateFallback(value, fallback);
  }

  return `${Number(parts.month)}月${Number(parts.day)}日`;
}

export function formatRelativeTime(value?: DateLike, fallback = "刚刚") {
  if (value === null || value === undefined || value === "") {
    return fallback;
  }

  const target = toTimestamp(value);
  if (!Number.isFinite(target)) {
    return resolveInvalidDateFallback(value, fallback);
  }

  const diff = Date.now() - target;
  if (diff < 60_000) {
    return "刚刚";
  }
  if (diff < 3_600_000) {
    return `${Math.max(1, Math.round(diff / 60_000))} 分钟前`;
  }
  if (diff < 86_400_000) {
    return `${Math.max(1, Math.round(diff / 3_600_000))} 小时前`;
  }
  if (diff < 2_592_000_000) {
    return `${Math.max(1, Math.round(diff / 86_400_000))} 天前`;
  }
  return formatMonthDay(value, fallback);
}

export function formatMoneyFen(value?: number | null) {
  if (value === undefined || value === null) {
    return "—";
  }

  return `¥${(value / 100).toFixed(2)}`;
}

export function formatCount(value?: number | null) {
  if (value === undefined || value === null) {
    return "—";
  }

  return new Intl.NumberFormat("zh-CN").format(value);
}

export function formatPercent(numerator: number, denominator: number, digits = 1) {
  if (denominator <= 0) {
    return `0.${"0".repeat(Math.max(digits, 0))}%`;
  }

  return `${((numerator / denominator) * 100).toFixed(digits)}%`;
}
