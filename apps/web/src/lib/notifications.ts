import { apiRequest, buildQuery } from "./apiClient";
import { buildEnterpriseTaskReviewHref } from "./enterpriseTasks";
import { type SessionRole } from "./sessionStore";

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? "/api/v1").replace(/\/$/, "");
export type NotificationTimeValue = number | string | null;

export type NotificationCategory =
  | "AI_TASK"
  | "CONSULT"
  | "BOUNTY"
  | "CERTIFICATION"
  | "SYSTEM"
  | "COMMUNITY";

export type NotificationRecord = {
  id: number;
  type: string;
  category: NotificationCategory | string;
  title: string;
  content: string;
  read: boolean;
  refType: string | null;
  refId: string | null;
  actionCode: string | null;
  actionable: boolean;
  payload: Record<string, unknown>;
  createdAt: NotificationTimeValue;
  readAt: NotificationTimeValue;
};

export type NotificationListData = {
  unreadCount: number;
  actionableCount: number;
  records: NotificationRecord[];
  total: number;
  page: number;
  size: number;
};

export type NotificationUnreadCountData = {
  unreadCount: number;
};

export type NotificationSyncData = {
  unreadCount: number;
  actionableCount: number;
  records: NotificationRecord[];
  hasMore: boolean;
  latestNotificationId: number | null;
  limit: number;
};

export type NotificationReadData = {
  id: number;
  read: boolean;
  readAt: NotificationTimeValue;
};

export type NotificationReadAllData = {
  updatedCount: number;
  unreadCount: number;
};

export type NotificationPreferenceItem = {
  category: NotificationCategory | string;
  inboxEnabled: boolean;
  websocketEnabled: boolean;
  browserPopupEnabled: boolean;
  emailEnabled: boolean;
  customized: boolean;
};

export type NotificationPreferencesData = {
  records: NotificationPreferenceItem[];
};

export type NotificationPreferenceUpdateCommand = {
  category: NotificationCategory | string;
  inboxEnabled?: boolean;
  websocketEnabled?: boolean;
  browserPopupEnabled?: boolean;
  emailEnabled?: boolean;
};

export type NotificationWebSocketTicketData = {
  ticket: string;
  wsPath: string;
  expiresAt: number | string;
};

const CATEGORY_LABELS: Record<string, string> = {
  AI_TASK: "AI 任务",
  CONSULT: "咨询订单",
  BOUNTY: "悬赏任务",
  CERTIFICATION: "认证审核",
  SYSTEM: "系统公告",
  COMMUNITY: "社区互动",
};

function readStringValue(input: unknown) {
  // 通知 payload 来自多个业务域，这里先统一把可用字符串安全取出来。
  return typeof input === "string" && input.trim() ? input.trim() : null;
}

function readPositiveNumberValue(input: unknown) {
  // 后端有些 id 可能以字符串进入 payload，前端跳转前统一收敛成正整数。
  if (typeof input === "number" && Number.isFinite(input) && input > 0) {
    return Math.trunc(input);
  }
  if (typeof input === "string" && input.trim()) {
    const parsed = Number(input);
    return Number.isFinite(parsed) && parsed > 0 ? Math.trunc(parsed) : null;
  }
  return null;
}

function resolveNotificationTargetRefId(notification: NotificationRecord) {
  // refId 是首选，但历史通知和不同业务模块也可能把目标 id 放在 payload 里。
  return readStringValue(notification.refId)
    ?? readStringValue(notification.payload.refId)
    ?? readStringValue(notification.payload.sourceId)
    ?? readStringValue(notification.payload.postId)
    ?? readStringValue(notification.payload.reportId)
    ?? readStringValue(notification.payload.orderNo)
    ?? readStringValue(notification.payload.taskId)
    ?? null;
}

export function getNotificationCategoryLabel(category: string | null | undefined) {
  // 未知分类直接透出原值，方便后台新增分类时前端不至于显示空白。
  if (!category) {
    return "平台通知";
  }
  return CATEGORY_LABELS[category] ?? category;
}

export function getNotificationActionLabel(notification: NotificationRecord, role?: SessionRole | null) {
  // 按动作码生成按钮文案；少数动作会根据角色换一种更准确的说法。
  switch (notification.actionCode) {
    case "VIEW_AI_RESUME_TASK_RESULT":
      return "查看复盘结果";
    case "VIEW_AI_RESUME_TASK_STATUS":
      return "查看任务状态";
    case "VIEW_AI_REVIEW_CENTER":
      return "打开复盘中心";
    case "VIEW_CONSULT_ORDER":
      return "查看订单处理";
    case "VIEW_CERTIFICATION_STATUS":
      return role === "ENTERPRISE" ? "查看资料与认证" : "查看认证状态";
    case "VIEW_BOUNTY_TASK":
      return role === "ENTERPRISE" ? "进入审核工作区" : "查看任务进展";
    case "VIEW_COMMUNITY_POST":
      return "回到社区帖子";
    case "VIEW_COMMUNITY_REPORTS":
      return "查看治理反馈";
    case "VIEW_NOTIFICATION_CENTER":
    default:
      return "打开通知中心";
  }
}

export function resolveNotificationHref(notification: NotificationRecord, role: SessionRole) {
  const refId = resolveNotificationTargetRefId(notification);
  // 先把业务目标解析出来，后面 switch 只关心“这类动作应该去哪个工作区”。
  const linkedRecordId = readPositiveNumberValue(notification.payload.linkedRecordId);
  const submissionId = readPositiveNumberValue(notification.payload.submissionId);
  const interviewSessionId = readStringValue(notification.payload.sessionId)
    ?? (notification.refType === "AI_INTERVIEW" ? refId : null);
  const resumeTaskId = readStringValue(notification.payload.taskId)
    ?? (notification.refType === "AI_ASYNC_TASK" ? refId : null);

  // actionCode 是通知动作语义；同一业务通知会按当前角色回流到不同工作区。
  switch (notification.actionCode) {
    case "VIEW_AI_RESUME_TASK_RESULT":
    case "VIEW_AI_RESUME_TASK_STATUS":
    case "VIEW_AI_REVIEW_CENTER":
      if (interviewSessionId) {
        // AI 复盘中心用 query 精确打开面试/简历记录，通知页不再自己维护详情 UI。
        return `/ai/history${buildQuery({ type: "interview", sessionId: interviewSessionId })}`;
      }
      if (linkedRecordId) {
        return `/ai/history${buildQuery({ type: "resume", recordId: linkedRecordId })}`;
      }
      if (resumeTaskId) {
        return `/ai/history${buildQuery({ type: "resume", taskId: resumeTaskId })}`;
      }
      return "/ai/history";
    case "VIEW_CONSULT_ORDER":
      // 同一咨询订单通知，导师和学生的履约入口不同。
      if (role === "MENTOR" && refId) {
        return `/mentor/orders/${encodeURIComponent(refId)}/workspace`;
      }
      if (role === "STUDENT" && refId) {
        return `/consult/orders/${encodeURIComponent(refId)}`;
      }
      return "/notifications";
    case "VIEW_CERTIFICATION_STATUS":
      if (role === "MENTOR") {
        return "/mentor/profile?tab=certification";
      }
      if (role === "ENTERPRISE") {
        return "/enterprise/profile";
      }
      return "/student/dashboard";
    case "VIEW_BOUNTY_TASK":
      // 企业侧回审核工作区，学生侧回任务详情页。
      if (role === "ENTERPRISE") {
        return refId ? buildEnterpriseTaskReviewHref(refId, submissionId) : "/enterprise/tasks";
      }
      if (role === "STUDENT") {
        return refId ? `/bounty/${encodeURIComponent(refId)}` : "/bounty";
      }
      return "/notifications";
    case "VIEW_COMMUNITY_POST":
      return refId ? `/community/${encodeURIComponent(refId)}` : "/community";
    case "VIEW_COMMUNITY_REPORTS":
      return refId ? `/community/reports?reportId=${encodeURIComponent(refId)}` : "/community/reports";
    case "VIEW_NOTIFICATION_CENTER":
    default:
      return "/notifications";
  }
}

export function hasNotificationTarget(notification: NotificationRecord, role: SessionRole) {
  const href = resolveNotificationHref(notification, role);
  if (!href) {
    return false;
  }
  if (notification.actionCode === "VIEW_NOTIFICATION_CENTER") {
    // 通知中心本身也是合法落点，只是不会计入后端 actionable 统计。
    return true;
  }
  return href !== "/notifications";
}

function resolveApiOrigin() {
  // WebSocket 需要完整 origin；相对 API 地址在浏览器里回落到当前站点。
  if (API_BASE_URL.startsWith("http://") || API_BASE_URL.startsWith("https://")) {
    return new URL(API_BASE_URL).origin;
  }
  if (typeof window !== "undefined") {
    return window.location.origin;
  }
  return "http://localhost";
}

export function buildNotificationWebSocketUrl(ticketData: NotificationWebSocketTicketData) {
  // 用短票据放到 ws query，避免把长期 access token 挂在 WebSocket URL 上。
  const url = new URL(ticketData.wsPath, resolveApiOrigin());
  url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
  url.searchParams.set("ticket", ticketData.ticket);
  return url.toString();
}

export function listNotifications(params: { page?: number; size?: number; unreadOnly?: boolean; category?: string | null }) {
  // 通知中心列表统一从这里走，页面只传筛选条件。
  return apiRequest<NotificationListData>(`/notifications${buildQuery({
    page: params.page ?? 1,
    size: params.size ?? 20,
    unreadOnly: params.unreadOnly ?? false,
    category: params.category ?? undefined,
  })}`);
}

export function getNotificationUnreadCount() {
  return apiRequest<NotificationUnreadCountData>("/notifications/unread-count");
}

export function syncNotifications(params: { afterId?: number; limit?: number }) {
  // WebSocket 漏消息或页面重新进入时，用 afterId 补拉增量通知。
  return apiRequest<NotificationSyncData>(`/notifications/sync${buildQuery({
    afterId: params.afterId,
    limit: params.limit ?? 20,
  })}`);
}

export function markNotificationRead(notificationId: number) {
  // 单条已读用于点击动作后的即时状态回写。
  return apiRequest<NotificationReadData>(`/notifications/${notificationId}/read`, {
    method: "POST",
  });
}

export function markAllNotificationsRead() {
  // 批量已读保持后端未读数真相，前端不要只本地清零。
  return apiRequest<NotificationReadAllData>("/notifications/read-all", {
    method: "POST",
  });
}

export function listNotificationPreferences() {
  return apiRequest<NotificationPreferencesData>("/notifications/preferences");
}

export function updateNotificationPreference(command: NotificationPreferenceUpdateCommand) {
  // 偏好只提交改动项，后端负责和默认矩阵合并。
  return apiRequest<NotificationPreferenceItem>("/notifications/preferences", {
    method: "PUT",
    body: JSON.stringify(command),
  });
}

export function issueNotificationWebSocketTicket() {
  // WebSocket 握手前先换短票据，票据过期后重新申请。
  return apiRequest<NotificationWebSocketTicketData>("/notifications/ws-ticket", {
    method: "POST",
  });
}
