import { COMMUNITY_REPORT_REASON_OPTIONS, COMMUNITY_SCENARIO_OPTIONS } from "../../lib/community";
import { formatRelativeTime as formatRelativeTimeByBrowserTimezone, type DateLike } from "../../lib/formatters";

export function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

export function getDisplayInitial(name: string | null | undefined) {
  const normalized = name?.trim();
  return normalized ? normalized.slice(0, 1).toUpperCase() : "社";
}

export function formatRelativeTime(value?: DateLike) {
  return formatRelativeTimeByBrowserTimezone(value, "刚刚");
}

export function getRoleMeta(role: string | null | undefined, ai = false) {
  if (ai) {
    return {
      label: "AI",
      badgeClassName: "border border-violet-200 bg-violet-50 text-violet-700",
      avatarClassName: "bg-gradient-to-br from-violet-500 to-indigo-500 text-white",
    };
  }

  switch ((role ?? "").toUpperCase()) {
    case "MENTOR":
      return {
        label: "导师",
        badgeClassName: "border border-amber-200 bg-amber-50 text-amber-700",
        avatarClassName: "bg-gradient-to-br from-amber-500 to-orange-500 text-white",
      };
    case "ADMIN":
      return {
        label: "管理员",
        badgeClassName: "border border-slate-200 bg-slate-100 text-slate-700",
        avatarClassName: "bg-gradient-to-br from-slate-500 to-slate-700 text-white",
      };
    case "ENTERPRISE":
      return {
        label: "企业",
        badgeClassName: "border border-teal-200 bg-teal-50 text-teal-700",
        avatarClassName: "bg-gradient-to-br from-teal-500 to-cyan-500 text-white",
      };
    case "STUDENT":
    default:
      return {
        label: "同学",
        badgeClassName: "border border-indigo-200 bg-indigo-50 text-indigo-700",
        avatarClassName: "bg-gradient-to-br from-indigo-500 to-sky-500 text-white",
      };
  }
}

export function getScenarioLabel(code: string | null | undefined) {
  if (!code) {
    return "综合求助";
  }
  return COMMUNITY_SCENARIO_OPTIONS.find((item) => item.code === code)?.label ?? code;
}

export function getReportReasonLabel(code: string | null | undefined) {
  if (!code) {
    return "未说明";
  }
  return COMMUNITY_REPORT_REASON_OPTIONS.find((item) => item.code === code)?.label ?? code;
}

export function getResolvedStatusMeta(status: string | null | undefined) {
  switch ((status ?? "").toUpperCase()) {
    case "RESOLVED":
      return {
        label: "已解决",
        className: "border border-emerald-200 bg-emerald-50 text-emerald-700",
      };
    case "CLOSED":
      return {
        label: "已关闭",
        className: "border border-slate-200 bg-slate-100 text-slate-600",
      };
    case "OPEN":
    default:
      return {
        label: "讨论中",
        className: "border border-sky-200 bg-sky-50 text-sky-700",
      };
  }
}

export function getModerationStatusMeta(status: string | null | undefined) {
  switch ((status ?? "").toUpperCase()) {
    case "REVIEW":
      return {
        label: "审核中",
        className: "border border-amber-200 bg-amber-50 text-amber-700",
      };
    case "BLOCK":
      return {
        label: "受限展示",
        className: "border border-rose-200 bg-rose-50 text-rose-700",
      };
    case "PASS":
    default:
      return {
        label: "公开可见",
        className: "border border-emerald-200 bg-emerald-50 text-emerald-700",
      };
  }
}

export function stripMarkdownToText(value: string | null | undefined) {
  if (!value) {
    return "";
  }

  return value
    .replace(/```[\s\S]*?```/g, " ")
    .replace(/`([^`]+)`/g, "$1")
    .replace(/!\[([^\]]*)\]\(([^)]+)\)/g, "$1")
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, "$1")
    .replace(/^#{1,6}\s+/gm, "")
    .replace(/^>\s?/gm, "")
    .replace(/^[-*+]\s+/gm, "")
    .replace(/^\d+\.\s+/gm, "")
    .replace(/[*_~]/g, "")
    .replace(/\n+/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

export function truncateText(value: string | null | undefined, maxLength = 160) {
  const normalized = stripMarkdownToText(value);
  if (normalized.length <= maxLength) {
    return normalized;
  }
  return `${normalized.slice(0, Math.max(0, maxLength - 1)).trimEnd()}…`;
}

export function parseTagInput(value: string) {
  return Array.from(
    new Set(
      value
        .split(/[,，\s]+/)
        .map((item) => item.trim())
        .filter(Boolean),
    ),
  ).slice(0, 6);
}
