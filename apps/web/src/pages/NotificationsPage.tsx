import {
  AlertCircle,
  BellRing,
  Bot,
  Briefcase,
  CheckCheck,
  ChevronLeft,
  ChevronRight,
  Mail,
  Megaphone,
  Monitor,
  RefreshCw,
  ShieldCheck,
  type LucideIcon,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Link, Navigate, matchPath, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { useNotificationCenter } from "../components/notifications/NotificationCenterProvider";
import StudentWorkspaceTopbar, { buildStudentWorkspacePrimaryNav } from "../components/student/StudentWorkspaceTopbar";
import WorkspaceRoleTopbar from "../components/workspace/WorkspaceRoleTopbar";
import {
  getNotificationActionLabel,
  getNotificationCategoryLabel,
  hasNotificationTarget,
  listNotifications,
  resolveNotificationHref,
  type NotificationRecord,
} from "../lib/notifications";
import { ApiClientError, apiRequest, isAbortError } from "../lib/apiClient";
import { buildEnterpriseLogoUrl } from "../lib/enterpriseLogo";
import { formatDateTime } from "../lib/formatters";
import { type SessionRole } from "../lib/sessionStore";
import { getEnterpriseWorkspaceNavItems, getMentorWorkspaceNavItems } from "../lib/workspaceNav";
import { resolveRoleCompatiblePath, resolveWorkspaceDashboardRoute } from "../lib/workspaceRoutes";

type ConnectionTone = {
  label: string;
  className: string;
};

type PermissionTone = {
  label: string;
  className: string;
};

type StatusSummaryTone = {
  textClassName: string;
  dotClassName: string;
};

type NotificationPageLocationState = {
  from?: string;
};

type EnterpriseNotificationIdentity = {
  displayName: string;
  companyName: string | null;
  logoUrl: string | null;
  logoUpdatedAt?: number | string | null;
};

const CATEGORY_ICONS: Record<string, LucideIcon> = {
  AI_TASK: Bot,
  CONSULT: Briefcase,
  BOUNTY: Briefcase,
  CERTIFICATION: ShieldCheck,
  SYSTEM: Megaphone,
  COMMUNITY: BellRing,
};

const CATEGORY_DESCRIPTIONS: Record<string, string> = {
  AI_TASK: "异步简历任务、后台重试与处理结果会回到这里。",
  CONSULT: "支付、导师回复、售后处理和履约变更统一回流。",
  BOUNTY: "企业任务提交、审核结果与继续接触留痕会统一回流到这里。",
  CERTIFICATION: "导师 / 企业认证审核结果和补件提醒统一管理。",
  SYSTEM: "平台公告、维护提醒与全局系统消息统一发布。",
  COMMUNITY: "帖子回复、社区审核结果和举报处理结果会正式回流到这里。",
};

const CATEGORY_FILTERS = [
  { id: "ALL", label: "全部通知" },
  { id: "AI_TASK", label: "AI 任务" },
  { id: "CONSULT", label: "咨询订单" },
  { id: "BOUNTY", label: "悬赏任务" },
  { id: "CERTIFICATION", label: "认证审核" },
  { id: "SYSTEM", label: "系统公告" },
  { id: "COMMUNITY", label: "社区互动" },
] as const;

const LAST_NON_NOTIFICATION_LOCATION_STORAGE_KEY = "app:last-non-notification-location";
const RETURN_ROUTE_LABELS = [
  { pattern: "/student/dashboard", label: "学生工作台" },
  { pattern: "/dashboard", label: "工作台入口" },
  { pattern: "/profile", label: "账号与资料中心" },
  { pattern: "/students/:studentUserId", label: "个人空间" },
  { pattern: "/skills", label: "技能星图" },
  { pattern: "/ai/history", label: "AI复盘中心" },
  { pattern: "/ai/interview/review", label: "面试复盘" },
  { pattern: "/ai/interview/session", label: "模拟面试进行中" },
  { pattern: "/ai/interview/prepare", label: "模拟面试准备" },
  { pattern: "/ai/interview", label: "模拟面试准备" },
  { pattern: "/ai/resume/review", label: "简历复盘" },
  { pattern: "/ai/resume", label: "简历优化" },
  { pattern: "/community/leaderboard", label: "社区贡献榜" },
  { pattern: "/community/reports", label: "举报与治理反馈" },
  { pattern: "/community/:postId", label: "讨论详情" },
  { pattern: "/community", label: "社区广场" },
  { pattern: "/mentors/:mentorUserId", label: "导师详情" },
  { pattern: "/mentors", label: "导师广场" },
  { pattern: "/bounty/:taskId", label: "悬赏任务详情" },
  { pattern: "/bounty", label: "企业实战任务" },
  { pattern: "/consult/create", label: "创建咨询订单" },
  { pattern: "/consult/orders/:orderNo", label: "咨询订单详情" },
  { pattern: "/consult/orders", label: "我的咨询订单" },
  { pattern: "/mentor/orders/:orderNo/workspace", label: "导师履约工作区" },
  { pattern: "/mentor/orders", label: "导师订单中心" },
  { pattern: "/mentor/profile", label: "导师资料与服务" },
  { pattern: "/mentor/finance", label: "导师财务中心" },
  { pattern: "/mentor/dashboard", label: "导师工作台" },
  { pattern: "/enterprise/tasks/create", label: "发布企业任务" },
  { pattern: "/enterprise/tasks/:taskId", label: "企业任务审核工作区" },
  { pattern: "/enterprise/tasks", label: "企业任务中心" },
  { pattern: "/enterprise/profile", label: "企业资料与认证" },
  { pattern: "/enterprise/dashboard", label: "企业工作台" },
] as const;
const NOTIFICATION_PAGE_SIZE = 20;

function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function buildWorkspaceLink(role: string | null) {
  const workspaceHref = resolveWorkspaceDashboardRoute(role);

  if (role === "MENTOR") {
    return {
      href: workspaceHref,
      label: "返回导师工作台",
    };
  }
  if (role === "ENTERPRISE") {
    return {
      href: workspaceHref,
      label: "返回企业工作台",
    };
  }
  return {
    href: workspaceHref,
    label: "返回学生工作台",
  };
}

function normalizeReturnPath(path: string | null | undefined) {
  const trimmed = path?.trim();
  if (!trimmed || !trimmed.startsWith("/")) {
    return null;
  }

  const pathname = trimmed.split(/[?#]/, 1)[0] ?? trimmed;
  if (pathname === "/notifications") {
    return null;
  }

  return trimmed;
}

function readRememberedReturnPath() {
  if (typeof window === "undefined") {
    return null;
  }

  return normalizeReturnPath(window.sessionStorage.getItem(LAST_NON_NOTIFICATION_LOCATION_STORAGE_KEY));
}

function resolveReturnLabel(path: string | null) {
  if (!path) {
    return null;
  }

  const pathname = path.split(/[?#]/, 1)[0] ?? path;
  const matched = RETURN_ROUTE_LABELS.find((item) => matchPath({ path: item.pattern, end: true }, pathname));
  return matched?.label ?? null;
}

function getConnectionTone(status: string): ConnectionTone {
  switch (status) {
    case "connected":
      return {
        label: "实时提醒正常",
        className: "border-emerald-200 bg-emerald-50 text-emerald-700",
      };
    case "connecting":
      return {
        label: "正在恢复同步",
        className: "border-sky-200 bg-sky-50 text-sky-700",
      };
    case "reconnecting":
      return {
        label: "正在恢复同步",
        className: "border-amber-200 bg-amber-50 text-amber-700",
      };
    case "error":
      return {
        label: "已切换收件箱补偿",
        className: "border-rose-200 bg-rose-50 text-rose-700",
      };
    default:
      return {
        label: "等待开始同步",
        className: "border-slate-200 bg-slate-100 text-slate-600",
      };
  }
}

function getPermissionTone(permission: string): PermissionTone {
  switch (permission) {
    case "granted":
      return {
        label: "桌面提醒已授权",
        className: "border-emerald-200 bg-emerald-50 text-emerald-700",
      };
    case "denied":
      return {
        label: "桌面提醒被浏览器阻止",
        className: "border-rose-200 bg-rose-50 text-rose-700",
      };
    case "unsupported":
      return {
        label: "当前浏览器不支持桌面提醒",
        className: "border-slate-200 bg-slate-100 text-slate-600",
      };
    default:
      return {
        label: "桌面提醒待授权",
        className: "border-amber-200 bg-amber-50 text-amber-700",
      };
  }
}

function getConnectionSummaryTone(status: string): StatusSummaryTone {
  switch (status) {
    case "connected":
      return {
        textClassName: "text-emerald-700",
        dotClassName: "bg-emerald-500",
      };
    case "connecting":
      return {
        textClassName: "text-sky-700",
        dotClassName: "bg-sky-500",
      };
    case "reconnecting":
      return {
        textClassName: "text-amber-700",
        dotClassName: "bg-amber-500",
      };
    case "error":
      return {
        textClassName: "text-rose-700",
        dotClassName: "bg-rose-500",
      };
    default:
      return {
        textClassName: "text-slate-600",
        dotClassName: "bg-slate-400",
      };
  }
}

function getPermissionSummaryTone(permission: string): StatusSummaryTone {
  switch (permission) {
    case "granted":
      return {
        textClassName: "text-emerald-700",
        dotClassName: "bg-emerald-500",
      };
    case "denied":
      return {
        textClassName: "text-rose-700",
        dotClassName: "bg-rose-500",
      };
    case "unsupported":
      return {
        textClassName: "text-slate-600",
        dotClassName: "bg-slate-400",
      };
    default:
      return {
        textClassName: "text-amber-700",
        dotClassName: "bg-amber-500",
      };
  }
}

function getCategoryScrollMaskClass(leftVisible: boolean, rightVisible: boolean) {
  if (leftVisible && rightVisible) {
    return "[mask-image:linear-gradient(to_right,transparent_0,black_1.6rem,black_calc(100%-1.6rem),transparent_100%)] [-webkit-mask-image:linear-gradient(to_right,transparent_0,black_1.6rem,black_calc(100%-1.6rem),transparent_100%)]";
  }
  if (leftVisible) {
    return "[mask-image:linear-gradient(to_right,transparent_0,black_1.6rem,black_100%)] [-webkit-mask-image:linear-gradient(to_right,transparent_0,black_1.6rem,black_100%)]";
  }
  if (rightVisible) {
    return "[mask-image:linear-gradient(to_right,black_0,black_calc(100%-1.6rem),transparent_100%)] [-webkit-mask-image:linear-gradient(to_right,black_0,black_calc(100%-1.6rem),transparent_100%)]";
  }
  return "[mask-image:none] [-webkit-mask-image:none]";
}

function getVerticalScrollMaskClass(topVisible: boolean, bottomVisible: boolean) {
  if (topVisible && bottomVisible) {
    return "[mask-image:linear-gradient(to_bottom,transparent_0,black_1.2rem,black_calc(100%-1.2rem),transparent_100%)] [-webkit-mask-image:linear-gradient(to_bottom,transparent_0,black_1.2rem,black_calc(100%-1.2rem),transparent_100%)]";
  }
  if (topVisible) {
    return "[mask-image:linear-gradient(to_bottom,transparent_0,black_1.2rem,black_100%)] [-webkit-mask-image:linear-gradient(to_bottom,transparent_0,black_1.2rem,black_100%)]";
  }
  if (bottomVisible) {
    return "[mask-image:linear-gradient(to_bottom,black_0,black_calc(100%-1.2rem),transparent_100%)] [-webkit-mask-image:linear-gradient(to_bottom,black_0,black_calc(100%-1.2rem),transparent_100%)]";
  }
  return "[mask-image:none] [-webkit-mask-image:none]";
}

function toUserMessage(error: unknown, fallback: string) {
  if (error instanceof ApiClientError) {
    return error.message || fallback;
  }
  if (error instanceof Error) {
    return error.message || fallback;
  }
  return fallback;
}

function readStringPayloadValue(input: unknown) {
  return typeof input === "string" && input.trim() ? input.trim() : null;
}

function formatContextValue(input: unknown): string | null {
  if (input === null || input === undefined) {
    return null;
  }
  if (typeof input === "string") {
    return input.trim() ? input.trim() : null;
  }
  if (typeof input === "number") {
    return Number.isFinite(input) ? String(input) : null;
  }
  if (typeof input === "boolean") {
    return input ? "是" : "否";
  }
  if (Array.isArray(input)) {
    const parts: string[] = input
      .map((item) => formatContextValue(item))
      .filter((item): item is string => Boolean(item));
    return parts.length > 0 ? parts.join(" / ") : null;
  }
  try {
    const serialized = JSON.stringify(input);
    return typeof serialized === "string" && serialized.trim() ? serialized : null;
  } catch {
    const fallback = String(input);
    return fallback.trim() ? fallback : null;
  }
}

function appendContextEntry(
  entries: Array<{ label: string; value: string }>,
  label: string,
  rawValue: unknown,
) {
  const value = formatContextValue(rawValue);
  if (!value) {
    return;
  }
  if (entries.some((item) => item.label === label && item.value === value)) {
    return;
  }
  entries.push({ label, value });
}

function mergeNotificationRecords(currentRecords: NotificationRecord[], nextRecords: NotificationRecord[]) {
  const recordMap = new Map<number, NotificationRecord>();
  currentRecords.forEach((item) => recordMap.set(item.id, item));
  nextRecords.forEach((item) => recordMap.set(item.id, item));
  return Array.from(recordMap.values());
}

function getCategoryIcon(category: string) {
  return CATEGORY_ICONS[category] ?? BellRing;
}

function getNotificationSourceLabel(notification: NotificationRecord) {
  return getNotificationCategoryLabel(String(notification.category));
}

function getNotificationActionHint(notification: NotificationRecord, role: SessionRole) {
  if (!hasNotificationTarget(notification, role)) {
    return "仅做知会";
  }

  switch (notification.actionCode) {
    case "VIEW_AI_RESUME_TASK_RESULT":
    case "VIEW_AI_RESUME_TASK_STATUS":
    case "VIEW_AI_REVIEW_CENTER":
      return "可查看结果";
    case "VIEW_CONSULT_ORDER":
      return "可继续处理";
    case "VIEW_BOUNTY_TASK":
      return role === "ENTERPRISE" ? "可进入审核" : "可查看进展";
    case "VIEW_CERTIFICATION_STATUS":
      return "可查看状态";
    case "VIEW_COMMUNITY_POST":
      return "可查看讨论";
    case "VIEW_COMMUNITY_REPORTS":
      return "可查看反馈";
    default:
      return "可查看详情";
  }
}

function buildContextEntries(notification: NotificationRecord, role: SessionRole) {
  const entries: Array<{ label: string; value: string }> = [];
  appendContextEntry(entries, "消息类型", notification.type);
  appendContextEntry(entries, "消息分类", getNotificationSourceLabel(notification));
  appendContextEntry(entries, "推荐操作", getNotificationActionLabel(notification, role));
  appendContextEntry(entries, "动作编码", notification.actionCode);
  appendContextEntry(entries, "引用类型", notification.refType);
  appendContextEntry(entries, "关联信息", notification.refId);

  const payloadFieldLabels: Array<[string, string]> = [
    ["announcementId", "公告编号"],
    ["taskId", "任务编号"],
    ["taskType", "任务类型"],
    ["status", "当前状态"],
    ["sceneCode", "场景代码"],
    ["linkedRecordId", "关联记录"],
    ["sessionId", "会话编号"],
    ["submissionId", "提交编号"],
    ["reviewStatus", "审核结果"],
    ["approvalStatus", "审核状态"],
    ["reportId", "举报编号"],
    ["targetType", "目标类型"],
    ["targetId", "目标编号"],
    ["postId", "帖子编号"],
    ["contentPostId", "内容帖子"],
    ["reasonCode", "原因代码"],
    ["sourceType", "来源类型"],
    ["decision", "处理结果"],
    ["action", "处理动作"],
    ["role", "角色"],
    ["errorCode", "错误代码"],
    ["errorMessage", "错误信息"],
    ["contentTitle", "内容标题"],
    ["postTitle", "帖子标题"],
  ];

  payloadFieldLabels.forEach(([key, label]) => appendContextEntry(entries, label, notification.payload[key]));
  return entries.slice(0, 14);
}

function buildSupplementSummary(notification: NotificationRecord | null) {
  if (!notification) {
    return null;
  }
  return readStringPayloadValue(notification.payload.resultSummary);
}

export default function NotificationsPage() {
  const { ready, isAuthenticated, role, displayName } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const {
    connectionStatus,
    browserPermission,
    latestEventVersion,
    refreshUnreadCount,
    markNotificationRead,
    markAllNotificationsRead,
    requestBrowserPermission,
  } = useNotificationCenter();
  const [records, setRecords] = useState<NotificationRecord[]>([]);
  const [inboxUnreadCount, setInboxUnreadCount] = useState(0);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [selectedCategory, setSelectedCategory] = useState<string>("ALL");
  const [categoryMaskLeftVisible, setCategoryMaskLeftVisible] = useState(false);
  const [categoryMaskRightVisible, setCategoryMaskRightVisible] = useState(false);
  const [inboxMaskTopVisible, setInboxMaskTopVisible] = useState(false);
  const [inboxMaskBottomVisible, setInboxMaskBottomVisible] = useState(false);
  const [listLoading, setListLoading] = useState(false);
  const [listLoadingMore, setListLoadingMore] = useState(false);
  const [listError, setListError] = useState<string | null>(null);
  const [selectedNotificationId, setSelectedNotificationId] = useState<number | null>(null);
  const [actionMessage, setActionMessage] = useState<string | null>(null);
  const [enterpriseIdentity, setEnterpriseIdentity] = useState<EnterpriseNotificationIdentity | null>(null);
  const categoryScrollRef = useRef<HTMLDivElement | null>(null);
  const inboxScrollRef = useRef<HTMLDivElement | null>(null);
  const currentRole = (role ?? null) as SessionRole;
  const workspaceLink = buildWorkspaceLink(role);
  // 返回链优先使用入口 state，其次使用 App 维护的最近非通知页，再按角色做兼容过滤。
  const returnPathFromState = normalizeReturnPath(((location.state as NotificationPageLocationState | null)?.from ?? null));
  const returnPath = resolveRoleCompatiblePath(returnPathFromState ?? readRememberedReturnPath(), currentRole);
  const returnLabel = resolveReturnLabel(returnPath);
  const canReturnByHistory = !returnPath && typeof window !== "undefined" && window.history.length > 1;
  const workspaceSectionLabel = "Notification Center";
  const connectionTone = getConnectionTone(connectionStatus);
  const permissionTone = getPermissionTone(browserPermission);
  const filteredRecords = records;
  const selectedNotification = filteredRecords.find((item) => item.id === selectedNotificationId) ?? filteredRecords[0] ?? null;
  const supplementSummary = buildSupplementSummary(selectedNotification);
  const contextEntries = selectedNotification ? buildContextEntries(selectedNotification, currentRole) : [];
  const selectedNotificationActionHint = selectedNotification
    ? getNotificationActionHint(selectedNotification, currentRole)
    : null;
  const selectedNotificationHasTarget = selectedNotification
    ? hasNotificationTarget(selectedNotification, currentRole)
    : false;
  const selectedCategoryLabel = CATEGORY_FILTERS.find((item) => item.id === selectedCategory)?.label ?? "全部通知";
  const selectedCategoryDescription = selectedCategory === "ALL"
    ? "统一查看平台消息，按业务分类快速筛选待办与留痕记录。"
    : CATEGORY_DESCRIPTIONS[selectedCategory] ?? "当前分类下的业务通知会统一沉淀在这里。";
  const hasMoreRecords = records.length < total;
  const connectionSummaryTone = getConnectionSummaryTone(connectionStatus);
  const permissionSummaryTone = getPermissionSummaryTone(browserPermission);
  const categoryScrollMaskClassName = getCategoryScrollMaskClass(
    categoryMaskLeftVisible,
    categoryMaskRightVisible,
  );
  const inboxScrollMaskClassName = getVerticalScrollMaskClass(
    inboxMaskTopVisible,
    inboxMaskBottomVisible,
  );
  const enterpriseDisplayName = enterpriseIdentity?.displayName?.trim() || displayName;
  const enterpriseCompanyName = enterpriseIdentity?.companyName?.trim() || enterpriseIdentity?.displayName?.trim() || null;
  const enterpriseLogoUrl = buildEnterpriseLogoUrl(
    enterpriseIdentity?.logoUrl ?? null,
    enterpriseIdentity?.logoUpdatedAt,
  );

  useEffect(() => {
    if (!ready || !isAuthenticated || role !== "ENTERPRISE") {
      setEnterpriseIdentity(null);
      return undefined;
    }

    const controller = new AbortController();
    // 企业通知中心头部需要 companyName/logo，普通 session 里只有通用 displayName。
    void apiRequest<EnterpriseNotificationIdentity>("/profiles/enterprises/me", { signal: controller.signal })
      .then((response) => {
        if (!controller.signal.aborted) {
          setEnterpriseIdentity(response);
        }
      })
      .catch((error) => {
        if (!controller.signal.aborted && !isAbortError(error)) {
          setEnterpriseIdentity(null);
        }
      });

    return () => controller.abort();
  }, [isAuthenticated, ready, role]);

  function syncCategoryMaskState() {
    const element = categoryScrollRef.current;
    if (!element) {
      setCategoryMaskLeftVisible(false);
      setCategoryMaskRightVisible(false);
      return;
    }
    const remaining = element.scrollWidth - element.scrollLeft - element.clientWidth;
    setCategoryMaskLeftVisible(element.scrollLeft > 12);
    setCategoryMaskRightVisible(remaining > 12);
  }

  function syncInboxMaskState() {
    const element = inboxScrollRef.current;
    if (!element) {
      setInboxMaskTopVisible(false);
      setInboxMaskBottomVisible(false);
      return;
    }

    const maxScrollable = element.scrollHeight - element.clientHeight;
    if (maxScrollable <= 12) {
      setInboxMaskTopVisible(false);
      setInboxMaskBottomVisible(false);
      return;
    }

    const remaining = element.scrollHeight - element.scrollTop - element.clientHeight;
    setInboxMaskTopVisible(element.scrollTop > 12);
    setInboxMaskBottomVisible(remaining > 12);
  }

  async function loadNotifications(
    nextPage = 1,
    nextUnreadOnly = unreadOnly,
    options?: { append?: boolean },
  ) {
    const append = options?.append ?? false;
    if (append) {
      setListLoadingMore(true);
    } else {
      setListLoading(true);
    }
    try {
      // category/unread/page 是通知中心的服务端分页维度，append 时按 id 去重合并。
      const response = await listNotifications({
        page: nextPage,
        size: NOTIFICATION_PAGE_SIZE,
        unreadOnly: nextUnreadOnly,
        category: selectedCategory === "ALL" ? null : selectedCategory,
      });
      const nextRecords = append ? mergeNotificationRecords(records, response.records) : response.records;
      setRecords(nextRecords);
      setInboxUnreadCount(response.unreadCount);
      setTotal(response.total);
      setPage(response.page);
      setListError(null);
      setSelectedNotificationId((currentValue) => {
        if (nextRecords.some((item) => item.id === currentValue)) {
          return currentValue;
        }
        return nextRecords[0]?.id ?? null;
      });
    } catch (error) {
      setListError(toUserMessage(error, "通知列表加载失败，请稍后重试。"));
    } finally {
      if (append) {
        setListLoadingMore(false);
      } else {
        setListLoading(false);
      }
    }
  }

  useEffect(() => {
    if (!ready || !isAuthenticated) {
      return;
    }
    // latestEventVersion 由 WebSocket 或同步补偿推进，变化后重拉列表闭环。
    void loadNotifications(1, unreadOnly);
  }, [isAuthenticated, latestEventVersion, ready, selectedCategory, unreadOnly]);

  useEffect(() => {
    if (filteredRecords.length === 0) {
      if (selectedNotificationId !== null) {
        setSelectedNotificationId(null);
      }
      return;
    }

    if (!filteredRecords.some((item) => item.id === selectedNotificationId)) {
      setSelectedNotificationId(filteredRecords[0]?.id ?? null);
    }
  }, [filteredRecords, selectedNotificationId]);

  useEffect(() => {
    syncCategoryMaskState();
    const frameId = window.requestAnimationFrame(() => {
      syncInboxMaskState();
    });
    const handleResize = () => {
      syncCategoryMaskState();
      syncInboxMaskState();
    };
    window.addEventListener("resize", handleResize);
    return () => {
      window.cancelAnimationFrame(frameId);
      window.removeEventListener("resize", handleResize);
    };
  }, [filteredRecords.length, listError, listLoading, selectedCategory, unreadOnly]);

  if (ready && !isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  async function handleSelectNotification(notification: NotificationRecord) {
    setSelectedNotificationId(notification.id);
    if (notification.read) {
      return;
    }

    // 已读先做本地乐观更新，失败后重新拉第一页恢复真实未读数。
    setRecords((currentValue) => currentValue.map((item) => (
      item.id === notification.id
        ? {
            ...item,
            read: true,
            readAt: new Date().toISOString(),
          }
        : item
    )));
    setInboxUnreadCount((currentValue) => Math.max(0, currentValue - 1));
    try {
      await markNotificationRead(notification.id, {
        knownUnread: true,
      });
    } catch (error) {
      setActionMessage(toUserMessage(error, "标记通知已读失败，请稍后再试。"));
      await loadNotifications(1, unreadOnly);
    }
  }

  async function handleMarkAllRead() {
    try {
      // 批量已读以后以服务端结果重新装载，避免分页之外的未读数残留。
      await markAllNotificationsRead();
      setActionMessage("当前页通知已同步标记为已读。");
      await loadNotifications(1, unreadOnly);
    } catch (error) {
      setActionMessage(toUserMessage(error, "批量标记已读失败，请稍后再试。"));
    }
  }

  async function handleRefresh() {
    setActionMessage(null);
    await Promise.all([
      loadNotifications(1, unreadOnly),
      refreshUnreadCount(),
    ]);
  }

  async function handleLoadMore() {
    if (listLoading || listLoadingMore || listError || !hasMoreRecords) {
      return;
    }
    await loadNotifications(page + 1, unreadOnly, { append: true });
  }

  function handleListScroll(event: React.UIEvent<HTMLDivElement>) {
    syncInboxMaskState();
    if (listLoading || listLoadingMore || listError || !hasMoreRecords) {
      return;
    }
    const target = event.currentTarget;
    const remaining = target.scrollHeight - target.scrollTop - target.clientHeight;
    if (remaining < 140) {
      // 列表滚动接近底部时自动补下一页，保留手动加载更多的同一逻辑。
      void handleLoadMore();
    }
  }

  function handleSelectCategory(category: string, button: HTMLButtonElement) {
    // 切分类会触发 useEffect 重拉第一页，同时把当前按钮滚到可见中间。
    setSelectedCategory(category);
    setPage(1);
    button.scrollIntoView({
      behavior: "smooth",
      block: "nearest",
      inline: "center",
    });
    window.requestAnimationFrame(() => {
      syncCategoryMaskState();
    });
  }

  function handleNavigate(notification: NotificationRecord) {
    if (!hasNotificationTarget(notification, currentRole)) {
      setActionMessage("这条通知已经成功入箱，但当前没有可直达的业务页目标。你可以先保留在通知中心回看，再按通知内容手动继续处理。");
      return;
    }
    // actionCode 的路由解释集中在 notifications.ts，页面只负责执行导航。
    const href = resolveNotificationHref(notification, currentRole);
    if (!href) {
      return;
    }
    setActionMessage(null);
    navigate(href);
  }

  function handleReturn() {
    if (returnPath) {
      navigate(returnPath);
      return;
    }

    if (canReturnByHistory) {
      navigate(-1);
      return;
    }

    navigate(workspaceLink.href);
  }

  const returnButtonLabel = returnPath
    ? returnLabel
      ? `返回${returnLabel}`
      : "返回上一页"
    : canReturnByHistory
      ? "返回上一页"
      : workspaceLink.label;
  const returnButtonClassName = "group inline-flex h-10 items-center justify-center rounded-full border border-slate-200 bg-white px-4 text-sm font-semibold text-slate-700 shadow-sm transition-colors duration-300 hover:border-sky-200 hover:text-sky-700 hover:bg-white";

  return (
    <div className="relative flex h-screen flex-col overflow-hidden bg-[#eff4ff] text-slate-900">
      <div className="pointer-events-none fixed inset-0 z-0 overflow-hidden">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(79,70,229,0.18),transparent_28%),radial-gradient(circle_at_bottom_right,rgba(20,184,166,0.12),transparent_24%),linear-gradient(180deg,#eef4ff_0%,#f8fafc_56%,#f8fafc_100%)]" />
        <div className="page-top-glow page-top-glow--indigo-soft" />
      </div>

      {role === "STUDENT" ? (
        <StudentWorkspaceTopbar
          sectionLabel={workspaceSectionLabel}
          title="通知中心"
          brandHref="/notifications"
          navItems={buildStudentWorkspacePrimaryNav()}
          rightActions={(
            <button
              type="button"
              onClick={handleReturn}
              className={returnButtonClassName}
            >
              <ChevronLeft size={16} className="mr-1.5 transition-transform duration-300 group-hover:-translate-x-0.5" />
              {returnButtonLabel}
            </button>
          )}
          position="sticky"
        />
      ) : role === "MENTOR" ? (
        <WorkspaceRoleTopbar
          sectionLabel={workspaceSectionLabel}
          title="通知中心"
          icon={BellRing}
          brandHref="/notifications"
          navItems={getMentorWorkspaceNavItems("notifications")}
          displayName={displayName}
          userSubtitle="导师消息收件箱"
          userFallbackLabel="导师"
          userFallbackInitial="导"
          rightActions={(
            <button
              type="button"
              onClick={handleReturn}
              className={returnButtonClassName}
            >
              <ChevronLeft size={16} className="mr-1.5 transition-transform duration-300 group-hover:-translate-x-0.5" />
              {returnButtonLabel}
            </button>
          )}
        />
      ) : role === "ENTERPRISE" ? (
        <WorkspaceRoleTopbar
          sectionLabel={workspaceSectionLabel}
          title="通知中心"
          icon={BellRing}
          brandHref="/notifications"
          navItems={getEnterpriseWorkspaceNavItems("notifications")}
          displayName={enterpriseDisplayName}
          userSubtitle={enterpriseCompanyName || "企业账号"}
          userFallbackLabel="企业代表"
          userFallbackInitial="企"
          userAvatarUrl={enterpriseLogoUrl}
          userAvatarDisplayName={enterpriseCompanyName || enterpriseDisplayName?.trim() || "企业"}
          rightActions={(
            <button
              type="button"
              onClick={handleReturn}
              className={returnButtonClassName}
            >
              <ChevronLeft size={16} className="mr-1.5 transition-transform duration-300 group-hover:-translate-x-0.5" />
              {returnButtonLabel}
            </button>
          )}
        />
      ) : (
        <nav className="sticky top-0 z-40 border-b border-white/60 bg-white/72 px-4 py-3 backdrop-blur-xl sm:px-6">
          <div className="mx-auto flex w-full max-w-[96rem] items-center justify-between gap-4">
            <div className="flex items-center gap-3">
              <Link
                to="/notifications"
                className="flex h-11 w-11 items-center justify-center rounded-2xl bg-gradient-to-br from-indigo-600 via-violet-500 to-teal-400 text-white shadow-[0_18px_35px_rgba(79,70,229,0.28)]"
              >
                <BellRing size={20} />
              </Link>
              <div className="flex min-h-11 items-center">
                <div className="text-xl font-bold text-slate-950">通知中心</div>
              </div>
            </div>

            <div className="flex items-center gap-3">
              <button
                type="button"
                onClick={handleReturn}
                className={returnButtonClassName}
              >
                <ChevronLeft size={16} className="mr-1.5 transition-transform duration-300 group-hover:-translate-x-0.5" />
                {returnButtonLabel}
              </button>
            </div>
          </div>
        </nav>
      )}

      <main className="relative z-10 mx-auto flex w-full max-w-[96rem] flex-1 min-h-0 flex-col overflow-hidden px-4 py-4 sm:px-6 lg:px-8 lg:py-5">
        <section className="mb-4 shrink-0 grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4">
          <div className="overflow-hidden rounded-[1.6rem] border border-white/80 bg-white/78 px-5 py-5 shadow-[0_18px_50px_rgba(148,163,184,0.14)] backdrop-blur-xl md:col-span-2 xl:col-span-2">
            <h1 className="text-[1.65rem] font-black tracking-tight text-slate-950 sm:text-[1.9rem]">
              通知中心
            </h1>
            <p className="mt-1.5 max-w-xl text-sm leading-6 text-slate-600">
              统一查看平台消息，了解最新动态，并快速回到业务主链路继续处理。收件箱会稳定保留通知记录，实时提醒只是帮助你更快注意到变化。
            </p>
            <div className="mt-4 flex flex-wrap gap-2">
              <button
                type="button"
                onClick={() => void handleRefresh()}
                className="inline-flex items-center rounded-full border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600"
              >
                <RefreshCw size={15} className={joinClasses("mr-2", listLoading && "animate-spin")} />
                同步刷新
              </button>
                <button
                  type="button"
                  onClick={() => void handleMarkAllRead()}
                  disabled={inboxUnreadCount <= 0}
                  className="inline-flex items-center rounded-full border border-slate-200 bg-slate-50 px-4 py-2 text-sm font-semibold text-slate-700 shadow-sm transition-colors hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-50"
                >
                <CheckCheck size={15} className="mr-2" />
                全部已读
              </button>
              <button
                type="button"
                onClick={() => void requestBrowserPermission()}
                className="inline-flex items-center rounded-full border border-emerald-200 bg-emerald-50 px-4 py-2 text-sm font-semibold text-emerald-700 shadow-sm transition-colors hover:border-emerald-300 hover:bg-emerald-100"
              >
                <Monitor size={15} className="mr-2" />
                启用桌面提醒
              </button>
            </div>
          </div>

          <div className="rounded-[1.6rem] border border-white/80 bg-white/84 px-5 py-4 shadow-[0_14px_40px_rgba(148,163,184,0.12)] backdrop-blur-xl">
            <div className="flex items-center justify-between">
              <div className="text-sm font-bold tracking-[0.12em] text-slate-600">未读消息</div>
              <span className="inline-flex h-11 w-11 items-center justify-center rounded-2xl bg-indigo-50 text-indigo-500">
                <Mail size={18} />
              </span>
            </div>
            <div className="mt-2.5 text-[2.15rem] font-black leading-none text-slate-900">{inboxUnreadCount}</div>
            <p className="mt-1.5 text-sm leading-[1.35rem] text-slate-500">当前仍需要你确认或回看的消息总数。</p>
          </div>

          <div className="rounded-[1.6rem] border border-white/80 bg-white/84 px-5 py-4 shadow-[0_14px_40px_rgba(148,163,184,0.12)] backdrop-blur-xl">
            <div className="flex items-center justify-between">
              <div className="text-sm font-bold tracking-[0.12em] text-slate-600">提醒状态摘要</div>
              <span className="inline-flex h-11 w-11 items-center justify-center rounded-2xl bg-slate-100 text-slate-500">
                <Monitor size={18} />
              </span>
            </div>
            <div className="mt-2.5 space-y-2.5">
              <div className={joinClasses("flex items-center gap-2.5 text-sm font-semibold", connectionSummaryTone.textClassName)}>
                <span className={joinClasses("h-2 w-2 rounded-full", connectionSummaryTone.dotClassName)} />
                {connectionTone.label}
              </div>
              <div className={joinClasses("flex items-center gap-2.5 text-sm font-semibold", permissionSummaryTone.textClassName)}>
                <span className={joinClasses("h-2 w-2 rounded-full", permissionSummaryTone.dotClassName)} />
                {permissionTone.label}
              </div>
              <div className="flex items-center gap-2.5 text-sm font-semibold text-slate-600">
                <span className="h-2 w-2 rounded-full bg-slate-400" />
                收件箱同步可用
              </div>
            </div>
          </div>
        </section>

        {actionMessage ? (
          <div className="mb-4 shrink-0 rounded-[1.15rem] border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
            {actionMessage}
          </div>
        ) : null}

        <section className="grid min-h-0 flex-1 gap-5 overflow-hidden xl:grid-cols-[minmax(24rem,0.6fr)_minmax(0,1.4fr)]">
          <div className="flex min-h-0 h-full flex-col overflow-hidden rounded-[1.65rem] border border-white/80 bg-white/86 shadow-[0_18px_48px_rgba(148,163,184,0.12)] backdrop-blur-xl">
            <div className="shrink-0 border-b border-slate-100 bg-slate-50/55 px-4 py-3">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <div className="text-[11px] font-bold tracking-[0.18em] text-slate-400">通知列表</div>
                  <h2 className="mt-1 text-lg font-bold text-slate-900">统一收件箱</h2>
                </div>
                <div className="inline-flex items-center rounded-full border border-slate-200 bg-white px-3 py-1 text-xs font-semibold text-slate-500 shadow-sm">
                  当前显示
                  {" "}
                  <span className="ml-1 text-slate-800">{filteredRecords.length}</span>
                </div>
              </div>

              <div className="mt-2.5 grid grid-cols-2 gap-2 rounded-[1rem] bg-slate-100/90 p-1.5">
                <button
                  type="button"
                  onClick={() => {
                    setPage(1);
                    setUnreadOnly(false);
                  }}
                  className={joinClasses(
                    "flex h-10 items-center justify-center rounded-[0.85rem] px-3 text-sm font-semibold transition-colors",
                    !unreadOnly ? "bg-white text-slate-900 shadow-sm" : "text-slate-500",
                  )}
                >
                  全部
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setPage(1);
                    setUnreadOnly(true);
                  }}
                  className={joinClasses(
                    "flex h-10 items-center justify-center rounded-[0.85rem] px-3 text-sm font-semibold transition-colors",
                    unreadOnly ? "bg-white text-slate-900 shadow-sm" : "text-slate-500",
                  )}
                >
                  仅未读
                </button>
              </div>

              <div className="relative mt-2.5 overflow-hidden rounded-[1rem]">
                <div
                  ref={categoryScrollRef}
                  onScroll={syncCategoryMaskState}
                  className={joinClasses(
                    "flex gap-2 overflow-x-auto scroll-smooth px-1 pb-1 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden",
                    categoryScrollMaskClassName,
                  )}
                >
                  {CATEGORY_FILTERS.map((item) => (
                    <button
                      key={item.id}
                      type="button"
                      onClick={(event) => handleSelectCategory(item.id, event.currentTarget)}
                      className={joinClasses(
                        "shrink-0 whitespace-nowrap rounded-lg px-3.5 py-1.5 text-sm font-semibold transition-colors",
                        selectedCategory === item.id
                          ? "border border-indigo-200 bg-indigo-50 text-indigo-700"
                          : "border border-slate-200 bg-white text-slate-600 hover:bg-slate-50",
                      )}
                    >
                      {item.label}
                    </button>
                  ))}
                </div>
              </div>

              <p className="mt-2 text-xs leading-5 text-slate-500">
                {selectedCategoryDescription}
              </p>
            </div>

            <div
              ref={inboxScrollRef}
              className={joinClasses(
                "flex-1 overflow-y-auto bg-slate-50/35 px-3 py-2.5",
                inboxScrollMaskClassName,
              )}
              onScroll={handleListScroll}
            >
              {listLoading && records.length === 0 ? (
                <div className="space-y-3">
                  {Array.from({ length: 6 }).map((_, index) => (
                    <div key={index} className="rounded-[1.2rem] border border-slate-100 bg-white px-4 py-4 shadow-sm">
                      <div className="flex items-start gap-3">
                        <div className="h-9 w-9 animate-pulse rounded-xl bg-slate-100" />
                        <div className="flex-1 space-y-2">
                          <div className="h-3 w-20 animate-pulse rounded-full bg-slate-100" />
                          <div className="h-4 w-2/3 animate-pulse rounded-full bg-slate-200" />
                          <div className="h-3 w-full animate-pulse rounded-full bg-slate-100" />
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              ) : listError ? (
                <div className="rounded-[1.35rem] border border-rose-200 bg-rose-50 px-5 py-6 text-center">
                  <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-white text-rose-500 shadow-sm">
                    <AlertCircle size={20} />
                  </div>
                  <h3 className="mt-4 text-sm font-bold text-slate-900">列表拉取失败</h3>
                  <p className="mt-2 text-sm leading-6 text-slate-500">网络连接出现波动，当前没有拉取到最新的通知列表。</p>
                  <button
                    type="button"
                    onClick={() => void handleRefresh()}
                    className="mt-4 inline-flex items-center rounded-full border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700 transition-colors hover:bg-slate-50"
                  >
                    重新加载
                  </button>
                </div>
              ) : filteredRecords.length === 0 ? (
                <div className="rounded-[1.35rem] border border-slate-200 bg-white px-6 py-12 text-center">
                  <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-slate-100 text-slate-500 shadow-sm">
                    <BellRing size={24} />
                  </div>
                  <h3 className="mt-4 text-base font-bold text-slate-900">
                    {unreadOnly ? "当前没有未读通知" : "当前筛选条件下没有结果"}
                  </h3>
                  <p className="mt-2 text-sm leading-7 text-slate-500">
                    {unreadOnly
                      ? "太棒了，当前待办都已确认。你可以切换到全部通知继续回看之前的消息记录。"
                      : `${selectedCategoryLabel}下暂时还没有新的消息。你可以稍后刷新，或切换到其他分类查看。`}
                  </p>
                </div>
              ) : (
                <div className="space-y-1.5">
                  {filteredRecords.map((notification, index) => {
                    const Icon = getCategoryIcon(String(notification.category));
                    const active = notification.id === selectedNotification?.id;
                    const actionHint = getNotificationActionHint(notification, currentRole);
                    return (
                      <button
                        key={notification.id}
                        type="button"
                        onClick={() => void handleSelectNotification(notification)}
                        className={joinClasses(
                          "relative w-full rounded-[1.15rem] border px-4 py-3.5 text-left transition-all",
                          index > 0 && "before:pointer-events-none before:absolute before:left-[3.6rem] before:right-4 before:top-0 before:h-px before:bg-[linear-gradient(90deg,rgba(226,232,240,0),rgba(203,213,225,0.92),rgba(226,232,240,0))]",
                          active
                            ? "border-sky-200 bg-[linear-gradient(135deg,rgba(255,255,255,0.98),rgba(240,249,255,0.98),rgba(236,253,245,0.95))] shadow-[0_14px_30px_rgba(56,189,248,0.12)] after:pointer-events-none after:absolute after:bottom-3 after:left-0 after:top-3 after:w-1 after:rounded-r-full after:bg-[linear-gradient(180deg,rgba(56,189,248,1),rgba(45,212,191,1))]"
                            : "border-transparent bg-transparent hover:border-slate-200 hover:bg-white",
                        )}
                      >
                        <div className="flex items-start gap-3">
                          <div className={joinClasses(
                            "mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-xl shadow-sm",
                            notification.read ? "bg-slate-100 text-slate-400" : "bg-gradient-to-br from-indigo-600 via-violet-500 to-teal-400 text-white",
                          )}>
                            <Icon size={17} />
                          </div>
                          <div className="min-w-0 flex-1">
                            <div className="mb-1.5 flex items-center gap-1.5">
                              {!notification.read ? <span className="h-1.5 w-1.5 shrink-0 rounded-full bg-indigo-500" /> : null}
                              <span className="rounded-md bg-slate-100 px-1.5 py-0.5 text-[11px] font-bold text-slate-500">
                                {getNotificationCategoryLabel(String(notification.category))}
                              </span>
                              <span className="rounded-md bg-indigo-50 px-1.5 py-0.5 text-[11px] font-medium text-indigo-600">
                                {actionHint}
                              </span>
                              <span className="ml-auto text-[11px] font-medium text-slate-400">
                                {notification.createdAt ? formatDateTime(notification.createdAt) : "刚刚入箱"}
                              </span>
                            </div>
                            <h3 className={joinClasses(
                              "truncate text-sm mb-1",
                              notification.read ? "font-medium text-slate-600" : "font-bold text-slate-900",
                            )}>
                              {notification.title}
                            </h3>
                            <p className="line-clamp-2 text-[13px] leading-6 text-slate-500">
                              {notification.content}
                            </p>
                          </div>
                          <ChevronRight
                            size={16}
                            className={joinClasses(
                              "mt-1 shrink-0",
                              active ? "text-sky-400" : "text-slate-300",
                            )}
                          />
                        </div>
                      </button>
                    );
                  })}

                  <div className="px-2 pb-1 pt-2 text-center text-xs text-slate-400">
                    {listLoadingMore
                      ? "正在继续加载更多通知..."
                      : hasMoreRecords
                        ? "继续下滑可加载更多通知"
                        : records.length > NOTIFICATION_PAGE_SIZE
                          ? "已经加载完当前可见的全部通知"
                          : ""}
                  </div>
                </div>
              )}
            </div>
          </div>

          <div className="flex min-h-0 h-full flex-col overflow-hidden rounded-[1.65rem] border border-white/80 bg-white/88 shadow-[0_18px_48px_rgba(148,163,184,0.12)] backdrop-blur-xl">
            {selectedNotification ? (
              <>
                <div className="shrink-0 border-b border-slate-100 bg-slate-50/45 px-5 py-4">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className={joinClasses(
                      "rounded-md border px-2.5 py-1 text-[12px] font-bold",
                      selectedNotification.read
                        ? "border-slate-200 bg-slate-100 text-slate-500"
                        : "border-indigo-200 bg-indigo-50 text-indigo-700",
                    )}>
                      {selectedNotification.read ? "已查看" : "待查看"}
                    </span>
                    <span className="rounded-md border border-slate-200 bg-white px-2.5 py-1 text-[12px] font-bold text-slate-500">
                      来自 {getNotificationSourceLabel(selectedNotification)}
                    </span>
                    {selectedNotificationActionHint ? (
                      <span className="rounded-md border border-indigo-100 bg-indigo-50 px-2.5 py-1 text-[12px] font-bold text-indigo-600">
                        {selectedNotificationActionHint}
                      </span>
                    ) : null}
                  </div>
                  <h2 className="mt-3 text-[1.55rem] font-black leading-tight text-slate-900">
                    {selectedNotification.title}
                  </h2>
                  <div className="mt-2.5 flex items-center gap-2 text-sm font-medium text-slate-400">
                    <Mail size={14} />
                    {selectedNotification.createdAt ? formatDateTime(selectedNotification.createdAt) : "刚刚入箱"}
                  </div>
                </div>

                <div className="flex-1 overflow-y-auto px-5 py-4">
                  <div className="space-y-[1.125rem]">
                    <div className="rounded-[1.3rem] border border-sky-100 bg-[linear-gradient(135deg,rgba(240,249,255,0.96),rgba(255,255,255,0.96),rgba(236,253,245,0.96))] px-5 py-5 shadow-[0_14px_34px_rgba(14,165,233,0.08)]">
                      <div className="flex flex-wrap items-center justify-between gap-4">
                        <div className="flex min-w-0 items-center gap-3">
                          <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-sky-100 text-sky-600">
                            <Mail size={18} />
                          </span>
                          <div className="min-w-0">
                            <h3 className="text-lg font-bold leading-none text-slate-900">消息重点</h3>
                          </div>
                        </div>
                        {selectedNotificationHasTarget ? (
                          <button
                            type="button"
                            onClick={() => handleNavigate(selectedNotification)}
                            className="group relative inline-flex shrink-0 items-center justify-center overflow-hidden rounded-full border border-sky-200 bg-[linear-gradient(135deg,rgba(255,255,255,0.96),rgba(240,249,255,0.98),rgba(236,254,255,0.94))] px-4 py-2.5 text-sm font-bold text-sky-700 shadow-[0_10px_22px_rgba(56,189,248,0.12)] transition-colors duration-300 hover:border-sky-300"
                          >
                            <span className="pointer-events-none absolute inset-0 bg-[linear-gradient(118deg,transparent_0%,transparent_26%,rgba(255,255,255,0.15)_38%,rgba(255,255,255,0.88)_50%,rgba(255,255,255,0.2)_62%,transparent_74%,transparent_100%)] opacity-0 translate-x-[-145%] transition-all duration-700 group-hover:translate-x-[145%] group-hover:opacity-100" />
                            <span className="pointer-events-none absolute inset-0 bg-[linear-gradient(135deg,rgba(224,242,254,0.14),rgba(255,255,255,0),rgba(207,250,254,0.16))] opacity-0 transition-opacity duration-300 group-hover:opacity-100" />
                            <span className="relative z-10">{getNotificationActionLabel(selectedNotification, currentRole)}</span>
                            <ChevronRight
                              size={16}
                              className="relative z-10 ml-1.5 transition-transform duration-300 group-hover:translate-x-1 group-hover:scale-110"
                            />
                          </button>
                        ) : null}
                      </div>
                      <div className="mt-4 text-sm leading-8 text-slate-700">
                        {selectedNotification.content}
                      </div>
                    </div>

                    {supplementSummary ? (
                      <div className="rounded-[1.3rem] border border-emerald-100 bg-[linear-gradient(135deg,rgba(236,253,245,0.96),rgba(255,255,255,0.98),rgba(240,249,255,0.92))] px-5 py-5 shadow-[0_14px_32px_rgba(16,185,129,0.08)]">
                        <div className="flex items-center gap-3">
                          <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-emerald-100 text-emerald-600">
                            <BellRing size={18} />
                          </span>
                          <div className="min-w-0">
                            <h3 className="text-lg font-bold leading-none text-slate-900">更多信息</h3>
                          </div>
                        </div>
                        <div className="mt-4 rounded-[1.1rem] border border-white/90 bg-white/90 px-4 py-4 text-sm leading-7 text-slate-700 shadow-[0_8px_20px_rgba(148,163,184,0.12)]">
                          {supplementSummary}
                        </div>
                      </div>
                    ) : null}

                    <div className="rounded-[1.25rem] border border-slate-100 bg-slate-50/90 px-5 py-5">
                      <div className="flex items-center gap-3">
                        <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-slate-200/80 text-slate-600">
                          <Monitor size={18} />
                        </span>
                        <div className="min-w-0">
                          <h3 className="text-lg font-bold leading-none text-slate-900">相关事项</h3>
                        </div>
                      </div>
                      <div className="mt-4 flex flex-wrap gap-2.5">
                        {contextEntries.map((item) => (
                          <div
                            key={item.label}
                            className="inline-flex max-w-full items-center gap-2 rounded-full border border-white/95 bg-white px-3.5 py-2 shadow-[0_6px_16px_rgba(148,163,184,0.12)]"
                          >
                            <span className="shrink-0 text-[11px] font-semibold text-slate-400">{item.label}</span>
                            <span className="min-w-0 break-all text-sm font-bold leading-5 text-slate-800">{item.value}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  </div>
                </div>

                {!selectedNotificationHasTarget || !selectedNotification.read ? (
                  <div className="shrink-0 border-t border-slate-100 bg-white px-5 py-4">
                    {!selectedNotificationHasTarget ? (
                      <div className="space-y-3">
                        <div className="flex items-start gap-2.5 rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm leading-6 text-slate-600">
                          <AlertCircle size={16} className="mt-0.5 shrink-0 text-slate-400" />
                          <p>这条消息主要用于提醒你了解进展，当前暂时没有单独入口。看完后可以回到对应页面继续处理。</p>
                        </div>
                        {!selectedNotification.read ? (
                          <div className="flex justify-end">
                            <button
                              type="button"
                              onClick={() => void handleSelectNotification(selectedNotification)}
                              className="inline-flex items-center justify-center rounded-xl border border-slate-200 bg-white px-5 py-3 text-sm font-bold text-slate-700 transition-colors hover:bg-slate-50"
                            >
                              标为已读
                            </button>
                          </div>
                        ) : null}
                      </div>
                    ) : !selectedNotification.read ? (
                      <div className="flex justify-end">
                        <button
                          type="button"
                          onClick={() => void handleSelectNotification(selectedNotification)}
                          className="inline-flex items-center justify-center rounded-xl border border-slate-200 bg-white px-5 py-3 text-sm font-bold text-slate-700 transition-colors hover:bg-slate-50"
                        >
                          标为已读
                        </button>
                      </div>
                    ) : null}
                  </div>
                ) : null}
              </>
            ) : (
              <div className="flex h-full flex-col items-center justify-center px-8 text-center">
                <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-slate-100 text-slate-500">
                  <BellRing size={24} />
                </div>
                <h3 className="mt-4 text-base font-bold text-slate-900">请选择一条通知以查看详情</h3>
                <p className="mt-2 max-w-sm text-sm leading-7 text-slate-500">
                  详情区会展示具体业务背景，并给出回到原业务页面继续处理的自然入口。
                </p>
              </div>
            )}
          </div>
        </section>
      </main>
    </div>
  );
}
