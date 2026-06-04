import {
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";
import {
  buildNotificationWebSocketUrl,
  getNotificationUnreadCount,
  issueNotificationWebSocketTicket,
  listNotifications,
  markAllNotificationsRead as markAllNotificationsReadRequest,
  markNotificationRead as markNotificationReadRequest,
  resolveNotificationHref,
  syncNotifications as syncNotificationsRequest,
  type NotificationRecord,
} from "../../lib/notifications";
import { type SessionRole } from "../../lib/sessionStore";
import {
  type BrowserPermissionState,
  type ConnectionStatus,
  NotificationCenterContext,
} from "./NotificationCenterContext";

type NotificationWebSocketEnvelope = {
  type?: string;
  jobId?: string;
  browserPopupAllowed?: boolean;
  notification?: NotificationRecord;
  reason?: string;
  serverTime?: number | string;
};

const NOTIFICATION_DEBUG_PREFIX = "[notifications]";
const RECENT_NOTIFICATION_LIMIT = 3;
const NOTIFICATION_SYNC_BATCH_SIZE = 20;
const NOTIFICATION_SYNC_MAX_BATCHES = 5;
const UNREAD_REFRESH_THROTTLE_MS = 1_200;

function getBrowserPermissionState(): BrowserPermissionState {
  if (typeof window === "undefined" || typeof window.Notification === "undefined") {
    return "unsupported";
  }
  return window.Notification.permission;
}

function sanitizeWebSocketUrl(rawUrl: string) {
  try {
    const url = new URL(rawUrl);
    if (url.searchParams.has("ticket")) {
      url.searchParams.set("ticket", "***");
    }
    return url.toString();
  } catch {
    return rawUrl;
  }
}

function logNotificationDebug(level: "info" | "warn" | "error", message: string, details?: unknown) {
  const logger = console[level] ?? console.info;
  if (details === undefined) {
    logger(`${NOTIFICATION_DEBUG_PREFIX} ${message}`);
    return;
  }
  logger(`${NOTIFICATION_DEBUG_PREFIX} ${message}`, details);
}

function dedupeNotificationRecords(records: NotificationRecord[]) {
  const seenIds = new Set<number>();
  // WS 推送和补偿同步可能命中同一条通知，按 notification id 去重。
  return records.filter((record) => {
    if (seenIds.has(record.id)) {
      return false;
    }
    seenIds.add(record.id);
    return true;
  });
}

function sortNotificationRecords(records: NotificationRecord[]) {
  return [...records].sort((left, right) => right.id - left.id);
}

function getLatestNotificationId(records: NotificationRecord[]) {
  return records.reduce((currentMax, record) => Math.max(currentMax, record.id), 0);
}

function markRecordAsRead(record: NotificationRecord) {
  if (record.read) {
    return record;
  }
  return {
    ...record,
    read: true,
    readAt: record.readAt ?? new Date().toISOString(),
  };
}

export default function NotificationCenterLiveProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const { ready, isAuthenticated, userId, role } = useAuth();
  const [unreadCount, setUnreadCount] = useState(0);
  const [connectionStatus, setConnectionStatus] = useState<ConnectionStatus>("idle");
  const [browserPermission, setBrowserPermission] = useState<BrowserPermissionState>(getBrowserPermissionState());
  const [latestEventVersion, setLatestEventVersion] = useState(0);
  const [latestIncomingNotification, setLatestIncomingNotification] = useState<NotificationRecord | null>(null);
  const [latestIncomingEventVersion, setLatestIncomingEventVersion] = useState(0);
  const [recentNotifications, setRecentNotifications] = useState<NotificationRecord[]>([]);
  const [recentNotificationsLoading, setRecentNotificationsLoading] = useState(false);
  const [recentNotificationsError, setRecentNotificationsError] = useState<string | null>(null);
  const [recentNotificationsEventVersion, setRecentNotificationsEventVersion] = useState<number | null>(null);
  const socketRef = useRef<WebSocket | null>(null);
  const reconnectTimerRef = useRef<number | null>(null);
  const reconnectAttemptsRef = useRef(0);
  const manualCloseRef = useRef(false);
  const navigateRef = useRef(navigate);
  const latestEventVersionRef = useRef(0);
  const browserPermissionRef = useRef<BrowserPermissionState>(browserPermission);
  const latestIncomingNotificationRef = useRef<NotificationRecord | null>(latestIncomingNotification);
  const recentNotificationsEventVersionRef = useRef<number | null>(recentNotificationsEventVersion);
  const recentRequestIdRef = useRef(0);
  const unreadRefreshPromiseRef = useRef<Promise<void> | null>(null);
  const unreadRefreshAtRef = useRef(0);
  // syncCursor 记录已见最大通知 id，SYNC_REQUIRED 时从该位置向后补拉。
  const syncCursorRef = useRef(0);
  const syncRequestPromiseRef = useRef<Promise<void> | null>(null);
  const sessionRef = useRef({
    ready,
    isAuthenticated,
    userId,
    role,
  });
  const seenMessageKeysRef = useRef<string[]>([]);

  useEffect(() => {
    navigateRef.current = navigate;
  }, [navigate]);

  useEffect(() => {
    latestIncomingNotificationRef.current = latestIncomingNotification;
  }, [latestIncomingNotification]);

  useEffect(() => {
    recentNotificationsEventVersionRef.current = recentNotificationsEventVersion;
  }, [recentNotificationsEventVersion]);

  useEffect(() => {
    sessionRef.current = {
      ready,
      isAuthenticated,
      userId,
      role,
    };
    const nextPermission = getBrowserPermissionState();
    browserPermissionRef.current = nextPermission;
    setBrowserPermission(nextPermission);
  }, [isAuthenticated, ready, role, userId]);

  function syncBrowserPermissionState() {
    const nextPermission = getBrowserPermissionState();
    if (browserPermissionRef.current !== nextPermission) {
      browserPermissionRef.current = nextPermission;
      setBrowserPermission(nextPermission);
      logNotificationDebug("info", "浏览器通知权限状态已同步", {
        permission: nextPermission,
      });
    }
    return nextPermission;
  }

  function syncRecentNotificationsEventVersion(nextVersion: number | null) {
    recentNotificationsEventVersionRef.current = nextVersion;
    setRecentNotificationsEventVersion(nextVersion);
  }

  function incrementLatestEventVersion() {
    const nextVersion = latestEventVersionRef.current + 1;
    latestEventVersionRef.current = nextVersion;
    setLatestEventVersion(nextVersion);
    return nextVersion;
  }

  function updateSyncCursor(nextNotificationId: number | null | undefined) {
    if (!nextNotificationId || nextNotificationId <= syncCursorRef.current) {
      return;
    }
    syncCursorRef.current = nextNotificationId;
  }

  async function refreshUnreadCount(force = false) {
    if (!sessionRef.current.isAuthenticated || !sessionRef.current.userId) {
      unreadRefreshPromiseRef.current = null;
      unreadRefreshAtRef.current = 0;
      setUnreadCount(0);
      return;
    }

    if (!force && unreadRefreshPromiseRef.current) {
      return unreadRefreshPromiseRef.current;
    }

    const now = Date.now();
    // 未读数刷新做节流，避免 WS、focus、列表操作同时触发多次请求。
    if (!force && unreadRefreshAtRef.current > 0 && now - unreadRefreshAtRef.current < UNREAD_REFRESH_THROTTLE_MS) {
      return;
    }

    let requestPromise: Promise<void> | null = null;
    requestPromise = (async () => {
      try {
        const response = await getNotificationUnreadCount();
        if (sessionRef.current.isAuthenticated && sessionRef.current.userId) {
          setUnreadCount(response.unreadCount);
          unreadRefreshAtRef.current = Date.now();
        }
      } catch {
        return;
      } finally {
        if (unreadRefreshPromiseRef.current === requestPromise) {
          unreadRefreshPromiseRef.current = null;
        }
      }
    })();

    unreadRefreshPromiseRef.current = requestPromise;
    return requestPromise;
  }

  async function ensureRecentNotifications(force = false) {
    if (!sessionRef.current.isAuthenticated || !sessionRef.current.userId) {
      recentRequestIdRef.current += 1;
      setRecentNotifications([]);
      setRecentNotificationsLoading(false);
      setRecentNotificationsError(null);
      syncRecentNotificationsEventVersion(null);
      return;
    }

    if (
      !force
      && recentNotificationsEventVersionRef.current !== null
      && recentNotificationsEventVersionRef.current === latestEventVersionRef.current
    ) {
      // 顶栏最近通知已对齐当前事件版本时，不重复请求。
      return;
    }

    const requestId = recentRequestIdRef.current + 1;
    const requestedEventVersion = latestEventVersionRef.current;
    recentRequestIdRef.current = requestId;
    setRecentNotificationsLoading(true);
    setRecentNotificationsError(null);

    try {
      const response = await listNotifications({
        page: 1,
        size: RECENT_NOTIFICATION_LIMIT,
        unreadOnly: false,
      });

      if (recentRequestIdRef.current !== requestId) {
        return;
      }

      setUnreadCount(response.unreadCount);
      response.records.forEach((record) => {
        rememberMessageKey(`notification:${record.id}`);
      });
      updateSyncCursor(getLatestNotificationId(response.records));
      setRecentNotifications(sortNotificationRecords(dedupeNotificationRecords(response.records)));
      syncRecentNotificationsEventVersion(requestedEventVersion);
    } catch {
      if (recentRequestIdRef.current !== requestId) {
        return;
      }
      setRecentNotificationsError("最近消息暂时还没同步出来");
    } finally {
      if (recentRequestIdRef.current === requestId) {
        setRecentNotificationsLoading(false);
      }
    }
  }

  function clearReconnectTimer() {
    if (reconnectTimerRef.current !== null) {
      window.clearTimeout(reconnectTimerRef.current);
      reconnectTimerRef.current = null;
      logNotificationDebug("info", "已清除待执行的通知重连定时器");
    }
  }

  function closeSocket() {
    clearReconnectTimer();
    const currentSocket = socketRef.current;
    socketRef.current = null;

    if (!currentSocket) {
      return;
    }

    manualCloseRef.current = true;
    currentSocket.onopen = null;
    currentSocket.onmessage = null;
    currentSocket.onerror = null;
    currentSocket.onclose = null;
    logNotificationDebug("info", "正在主动关闭通知 WebSocket", {
      readyState: currentSocket.readyState,
    });

    if (currentSocket.readyState === WebSocket.OPEN || currentSocket.readyState === WebSocket.CONNECTING) {
      currentSocket.close();
    }
  }

  function rememberMessageKey(messageKey: string) {
    const nextKeys = seenMessageKeysRef.current;
    nextKeys.push(messageKey);
    if (nextKeys.length > 240) {
      // 去重窗口只保留最近消息，避免长时间开页导致数组无界增长。
      nextKeys.splice(0, nextKeys.length - 240);
    }
  }

  function dismissIncomingPreview(notificationId?: number) {
    setLatestIncomingNotification((currentValue) => {
      if (!currentValue) {
        return null;
      }
      if (notificationId && currentValue.id !== notificationId) {
        return currentValue;
      }
      return null;
    });
  }

  function scheduleReconnect() {
    if (!sessionRef.current.isAuthenticated || reconnectTimerRef.current !== null) {
      return;
    }

    reconnectAttemptsRef.current += 1;
    // 轻量线性退避即可，通知中心还有 HTTP 同步兜底。
    const delay = Math.min(12_000, reconnectAttemptsRef.current * 1_500);
    setConnectionStatus("reconnecting");
    logNotificationDebug("warn", "通知 WebSocket 将尝试重连", {
      attempt: reconnectAttemptsRef.current,
      delayMs: delay,
    });
    reconnectTimerRef.current = window.setTimeout(() => {
      reconnectTimerRef.current = null;
      void connectWebSocket();
    }, delay);
  }

  async function requestBrowserPermission() {
    if (typeof window === "undefined" || typeof window.Notification === "undefined") {
      setBrowserPermission("unsupported");
      logNotificationDebug("warn", "当前环境不支持浏览器桌面通知");
      return "unsupported";
    }

    logNotificationDebug("info", "正在请求浏览器桌面通知权限");
    const nextPermission = await window.Notification.requestPermission();
    browserPermissionRef.current = nextPermission;
    setBrowserPermission(nextPermission);
    logNotificationDebug("info", "浏览器桌面通知权限请求完成", {
      permission: nextPermission,
    });
    return nextPermission;
  }

  function showBrowserNotification(notification: NotificationRecord, browserPopupAllowed: boolean | undefined) {
    const nextPermission = syncBrowserPermissionState();
    if (
      !browserPopupAllowed
      || nextPermission !== "granted"
      || typeof window === "undefined"
      || typeof window.Notification === "undefined"
    ) {
      logNotificationDebug("info", "当前通知未触发桌面提醒", {
        notificationId: notification.id,
        browserPopupAllowed: Boolean(browserPopupAllowed),
        browserPermission: nextPermission,
      });
      return;
    }

    const targetHref = resolveNotificationHref(notification, (sessionRef.current.role ?? null) as SessionRole);
    try {
      const popup = new window.Notification(notification.title, {
        body: notification.content,
        tag: `bishe-notification-${notification.id}`,
        data: {
          href: targetHref,
          notificationId: notification.id,
        },
      });

      logNotificationDebug("info", "已触发浏览器桌面通知", {
        notificationId: notification.id,
        title: notification.title,
        targetHref,
        visibilityState: document.visibilityState,
        hasFocus: document.hasFocus(),
      });

      popup.onclick = () => {
        logNotificationDebug("info", "用户点击了桌面通知", {
          notificationId: notification.id,
          targetHref,
        });
        popup.close();
        window.focus();
        if (targetHref) {
          navigateRef.current(targetHref);
        }
        void markNotificationRead(notification.id, {
          knownUnread: !notification.read,
          dismissPreview: true,
        });
      };

      popup.onerror = (event) => {
        logNotificationDebug("error", "浏览器桌面通知触发失败", {
          notificationId: notification.id,
          event,
        });
      };
    } catch (error) {
      logNotificationDebug("error", "创建浏览器桌面通知时发生异常", {
        notificationId: notification.id,
        error,
      });
    }
  }

  async function syncNotificationsFromServer(trigger: string) {
    if (!sessionRef.current.isAuthenticated || !sessionRef.current.userId) {
      syncRequestPromiseRef.current = null;
      syncCursorRef.current = 0;
      return;
    }

    if (syncRequestPromiseRef.current) {
      return syncRequestPromiseRef.current;
    }

    let requestPromise: Promise<void> | null = null;
    requestPromise = (async () => {
      try {
        let afterId = syncCursorRef.current > 0 ? syncCursorRef.current : undefined;
        let batchCount = 0;
        let unreadSnapshot: number | null = null;
        const mergedRecords: NotificationRecord[] = [];

        logNotificationDebug("info", "开始执行通知增量同步", {
          trigger,
          afterId,
        });

        // 分批补拉最多 5 批，避免长时间离线后一次同步阻塞前台。
        while (batchCount < NOTIFICATION_SYNC_MAX_BATCHES) {
          batchCount += 1;
          const response = await syncNotificationsRequest({
            afterId,
            limit: NOTIFICATION_SYNC_BATCH_SIZE,
          });
          unreadSnapshot = response.unreadCount;
          updateSyncCursor(response.latestNotificationId);
          if (response.records.length > 0) {
            mergedRecords.push(...response.records);
            const batchLatestId = getLatestNotificationId(response.records);
            updateSyncCursor(batchLatestId);
            afterId = batchLatestId;
          }
          if (!response.hasMore || response.records.length === 0) {
            break;
          }
        }

        if (unreadSnapshot !== null) {
          setUnreadCount(unreadSnapshot);
        }
        if (mergedRecords.length === 0) {
          return;
        }

        const syncedRecords = sortNotificationRecords(dedupeNotificationRecords(mergedRecords));
        syncedRecords.forEach((record) => {
          rememberMessageKey(`notification:${record.id}`);
        });

        const nextEventVersion = incrementLatestEventVersion();
        setRecentNotifications((currentValue) => (
          sortNotificationRecords(dedupeNotificationRecords([...syncedRecords, ...currentValue])).slice(0, RECENT_NOTIFICATION_LIMIT)
        ));
        setRecentNotificationsError(null);
        syncRecentNotificationsEventVersion(nextEventVersion);

        logNotificationDebug("info", "通知增量同步完成", {
          trigger,
          syncedCount: syncedRecords.length,
          latestCursor: syncCursorRef.current,
        });
      } catch (error) {
        logNotificationDebug("warn", "通知增量同步失败，回退为未读数刷新", {
          trigger,
          error,
        });
        await refreshUnreadCount(true);
      } finally {
        if (syncRequestPromiseRef.current === requestPromise) {
          syncRequestPromiseRef.current = null;
        }
      }
    })();

    syncRequestPromiseRef.current = requestPromise;
    return requestPromise;
  }

  async function handleIncomingMessage(socket: WebSocket, rawPayload: string) {
    let envelope: NotificationWebSocketEnvelope | null = null;

    try {
      envelope = JSON.parse(rawPayload) as NotificationWebSocketEnvelope;
    } catch (error) {
      logNotificationDebug("warn", "通知 WebSocket 收到无法解析的消息", {
        rawPayload,
        error,
      });
      return;
    }

    if (!envelope?.type) {
      logNotificationDebug("warn", "通知 WebSocket 收到缺少 type 的消息", {
        rawPayload,
      });
      return;
    }

    if (envelope.type === "CONNECTED") {
      reconnectAttemptsRef.current = 0;
      setConnectionStatus("connected");
      logNotificationDebug("info", "通知 WebSocket 已收到服务端 CONNECTED 确认", envelope);
      return;
    }

    if (envelope.type === "SYNC_REQUIRED") {
      // 服务端只发同步指令，不必逐条推送历史缺口。
      logNotificationDebug("info", "通知 WebSocket 收到服务端同步指令", envelope);
      await syncNotificationsFromServer(`ws:${envelope.reason ?? "unknown"}`);
      return;
    }

    if (envelope.type !== "NOTIFICATION_CREATED" || !envelope.notification) {
      logNotificationDebug("info", "通知 WebSocket 收到非通知事件", envelope);
      return;
    }

    if (envelope.jobId && socket.readyState === WebSocket.OPEN) {
      // ACK 只确认这次 websocket delivery job，不代表通知本身已读。
      socket.send(JSON.stringify({
        type: "ACK",
        jobId: envelope.jobId,
      }));
      logNotificationDebug("info", "通知 WebSocket 已回传 ACK", {
        jobId: envelope.jobId,
        notificationId: envelope.notification.id,
      });
    }

    const notificationKey = `notification:${envelope.notification.id}`;
    const messageKey = envelope.jobId || notificationKey;
    if (
      seenMessageKeysRef.current.includes(messageKey)
      || seenMessageKeysRef.current.includes(notificationKey)
    ) {
      logNotificationDebug("info", "通知 WebSocket 收到重复消息，已跳过", {
        messageKey,
        notificationKey,
      });
      return;
    }
    rememberMessageKey(messageKey);
    rememberMessageKey(notificationKey);
    updateSyncCursor(envelope.notification.id);

    logNotificationDebug("info", "通知中心收到新消息", {
      messageKey,
      notificationId: envelope.notification.id,
      title: envelope.notification.title,
      browserPopupAllowed: envelope.browserPopupAllowed,
    });
    setLatestIncomingNotification(envelope.notification);
    setLatestIncomingEventVersion((currentValue) => currentValue + 1);
    setUnreadCount((currentValue) => currentValue + (envelope?.notification?.read ? 0 : 1));
    const nextEventVersion = incrementLatestEventVersion();
    if (recentNotificationsEventVersionRef.current !== null) {
      // 顶栏最近通知打开过才做增量维护，没打开时保留懒加载策略。
      setRecentNotifications((currentValue) => (
        sortNotificationRecords(dedupeNotificationRecords([envelope.notification!, ...currentValue])).slice(0, RECENT_NOTIFICATION_LIMIT)
      ));
      setRecentNotificationsError(null);
      syncRecentNotificationsEventVersion(nextEventVersion);
    }
    showBrowserNotification(envelope.notification, envelope.browserPopupAllowed);
  }

  async function connectWebSocket() {
    if (
      !sessionRef.current.ready
      || !sessionRef.current.isAuthenticated
      || !sessionRef.current.userId
      || sessionRef.current.role === "ADMIN"
    ) {
      logNotificationDebug("info", "当前会话尚未满足通知 WebSocket 连接条件，跳过连接", {
        ready: sessionRef.current.ready,
        isAuthenticated: sessionRef.current.isAuthenticated,
        userId: sessionRef.current.userId,
        role: sessionRef.current.role,
      });
      return;
    }

    const currentSocket = socketRef.current;
    if (currentSocket && (currentSocket.readyState === WebSocket.OPEN || currentSocket.readyState === WebSocket.CONNECTING)) {
      logNotificationDebug("info", "通知 WebSocket 已存在活动连接，跳过重复建立", {
        readyState: currentSocket.readyState,
      });
      return;
    }

    clearReconnectTimer();
    setConnectionStatus(reconnectAttemptsRef.current > 0 ? "reconnecting" : "connecting");
    logNotificationDebug("info", "开始建立通知 WebSocket 连接", {
      reconnectAttempts: reconnectAttemptsRef.current,
      role: sessionRef.current.role,
      userId: sessionRef.current.userId,
    });

    try {
      // WS 使用一次性 ticket，避免把长期 JWT 放进 WebSocket URL。
      const ticketResponse = await issueNotificationWebSocketTicket();
      if (!sessionRef.current.isAuthenticated || !sessionRef.current.userId) {
        logNotificationDebug("warn", "通知 WebSocket 票据已签发，但当前会话已失效，取消连接");
        return;
      }

      const socketUrl = buildNotificationWebSocketUrl(ticketResponse);
      logNotificationDebug("info", "通知 WebSocket 票据签发成功", {
        wsPath: ticketResponse.wsPath,
        expiresAt: ticketResponse.expiresAt,
        socketUrl: sanitizeWebSocketUrl(socketUrl),
      });

      const nextSocket = new WebSocket(socketUrl);
      manualCloseRef.current = false;
      socketRef.current = nextSocket;

      nextSocket.onopen = () => {
        setConnectionStatus("connected");
        logNotificationDebug("info", "通知 WebSocket 传输层已打开", {
          socketUrl: sanitizeWebSocketUrl(socketUrl),
        });
      };

      nextSocket.onmessage = (event) => {
        void handleIncomingMessage(nextSocket, event.data);
      };

      nextSocket.onerror = (event) => {
        setConnectionStatus("error");
        logNotificationDebug("error", "通知 WebSocket 触发错误事件", {
          socketUrl: sanitizeWebSocketUrl(socketUrl),
          event,
        });
      };

      nextSocket.onclose = (event) => {
        if (socketRef.current === nextSocket) {
          socketRef.current = null;
        }

        if (manualCloseRef.current) {
          manualCloseRef.current = false;
          setConnectionStatus("idle");
          logNotificationDebug("info", "通知 WebSocket 已按预期关闭", {
            code: event.code,
            reason: event.reason,
            wasClean: event.wasClean,
          });
          return;
        }

        logNotificationDebug("warn", "通知 WebSocket 已断开，准备重连", {
          code: event.code,
          reason: event.reason,
          wasClean: event.wasClean,
        });
        scheduleReconnect();
      };
    } catch (error) {
      setConnectionStatus("error");
      logNotificationDebug("error", "通知 WebSocket 建连前阶段失败", error);
      scheduleReconnect();
    }
  }

  async function markNotificationRead(
    notificationId: number,
    options?: {
      knownUnread?: boolean;
      dismissPreview?: boolean;
    },
  ) {
    const currentIncomingNotification = latestIncomingNotificationRef.current;
    const matchesIncomingPreview = currentIncomingNotification?.id === notificationId;
    const shouldDecreaseUnread = Boolean(options?.knownUnread)
      || (matchesIncomingPreview && !currentIncomingNotification?.read);

    if (shouldDecreaseUnread) {
      // 已读先本地扣减，接口失败后再通过 unread count 校准。
      setUnreadCount((currentValue) => Math.max(0, currentValue - 1));
    }

    if (recentNotificationsEventVersionRef.current !== null) {
      setRecentNotifications((currentValue) => currentValue.map((record) => (
        record.id === notificationId ? markRecordAsRead(record) : record
      )));
      setRecentNotificationsError(null);
    }

    if (matchesIncomingPreview) {
      if (options?.dismissPreview) {
        setLatestIncomingNotification(null);
      } else {
        setLatestIncomingNotification({
          ...currentIncomingNotification,
          read: true,
          readAt: currentIncomingNotification.readAt ?? new Date().toISOString(),
        });
      }
    }

    try {
      const response = await markNotificationReadRequest(notificationId);
      const nextEventVersion = incrementLatestEventVersion();
      if (recentNotificationsEventVersionRef.current !== null) {
        syncRecentNotificationsEventVersion(nextEventVersion);
      }

      if (matchesIncomingPreview && !options?.dismissPreview) {
        setLatestIncomingNotification((currentValue) => (
          currentValue?.id === notificationId
            ? {
                ...currentValue,
                read: response.read,
                readAt: response.readAt,
              }
            : currentValue
        ));
      }

      await refreshUnreadCount(true);
    } catch (error) {
      await refreshUnreadCount(true);
      if (recentNotificationsEventVersionRef.current !== null) {
        void ensureRecentNotifications(true);
      }
      throw error;
    }
  }

  async function markAllNotificationsRead() {
    setUnreadCount(0);
    if (recentNotificationsEventVersionRef.current !== null) {
      setRecentNotifications((currentValue) => currentValue.map((record) => markRecordAsRead(record)));
      setRecentNotificationsError(null);
    }
    setLatestIncomingNotification((currentValue) => (
      currentValue
        ? {
            ...currentValue,
            read: true,
            readAt: currentValue.readAt ?? new Date().toISOString(),
          }
        : currentValue
    ));

    try {
      await markAllNotificationsReadRequest();
      const nextEventVersion = incrementLatestEventVersion();
      if (recentNotificationsEventVersionRef.current !== null) {
        syncRecentNotificationsEventVersion(nextEventVersion);
      }
      await refreshUnreadCount(true);
    } catch (error) {
      await refreshUnreadCount(true);
      if (recentNotificationsEventVersionRef.current !== null) {
        void ensureRecentNotifications(true);
      }
      throw error;
    }
  }

  useEffect(() => {
    if (!ready) {
      return;
    }

    if (!isAuthenticated || !userId || role === "ADMIN") {
      // 管理员通知当前不走前台 WS 通道，切换账号时清空全部实时状态。
      closeSocket();
      reconnectAttemptsRef.current = 0;
      seenMessageKeysRef.current = [];
      recentRequestIdRef.current += 1;
      unreadRefreshPromiseRef.current = null;
      unreadRefreshAtRef.current = 0;
      syncRequestPromiseRef.current = null;
      syncCursorRef.current = 0;
      setUnreadCount(0);
      setConnectionStatus("idle");
      latestEventVersionRef.current = 0;
      setLatestEventVersion(0);
      setLatestIncomingNotification(null);
      setLatestIncomingEventVersion(0);
      setRecentNotifications([]);
      setRecentNotificationsLoading(false);
      setRecentNotificationsError(null);
      syncRecentNotificationsEventVersion(null);
      return;
    }

    void refreshUnreadCount();
    void connectWebSocket();

    const intervalId = window.setInterval(() => {
      void refreshUnreadCount();
      if (!socketRef.current || socketRef.current.readyState === WebSocket.CLOSED) {
        void connectWebSocket();
      }
    }, 60_000);

    const handleFocus = () => {
      syncBrowserPermissionState();
      void refreshUnreadCount();
      if (!socketRef.current || socketRef.current.readyState === WebSocket.CLOSED) {
        void connectWebSocket();
      }
    };

    const handleVisibilityChange = () => {
      syncBrowserPermissionState();
      if (document.visibilityState !== "visible") {
        return;
      }
      void refreshUnreadCount();
      if (!socketRef.current || socketRef.current.readyState === WebSocket.CLOSED) {
        void connectWebSocket();
      }
    };

    const handleOnline = () => {
      void refreshUnreadCount(true);
      if (!socketRef.current || socketRef.current.readyState === WebSocket.CLOSED) {
        void connectWebSocket();
      }
    };

    window.addEventListener("focus", handleFocus);
    window.addEventListener("online", handleOnline);
    document.addEventListener("visibilitychange", handleVisibilityChange);

    return () => {
      window.clearInterval(intervalId);
      window.removeEventListener("focus", handleFocus);
      window.removeEventListener("online", handleOnline);
      document.removeEventListener("visibilitychange", handleVisibilityChange);
      closeSocket();
    };
  }, [isAuthenticated, ready, userId]);

  return (
    <NotificationCenterContext.Provider
      value={{
        unreadCount,
        connectionStatus,
        browserPermission,
        latestEventVersion,
        latestIncomingNotification,
        latestIncomingEventVersion,
        recentNotifications,
        recentNotificationsLoading,
        recentNotificationsError,
        ensureRecentNotifications,
        refreshUnreadCount,
        dismissIncomingPreview,
        markNotificationRead,
        markAllNotificationsRead,
        requestBrowserPermission,
      }}
    >
      {children}
    </NotificationCenterContext.Provider>
  );
}
