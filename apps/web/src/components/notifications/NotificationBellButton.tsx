import { AnimatePresence, motion } from "framer-motion";
import { Bell, BellRing, CheckCheck, Loader2 } from "lucide-react";
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type FocusEvent,
} from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";
import { formatDateTime } from "../../lib/formatters";
import {
  getNotificationCategoryLabel,
  resolveNotificationHref,
  type NotificationRecord,
} from "../../lib/notifications";
import { type SessionRole } from "../../lib/sessionStore";
import { useNotificationCenter } from "./NotificationCenterProvider";

type NotificationBellButtonProps = {
  className?: string;
  badgeClassName?: string;
  iconSize?: number;
  countCap?: number;
  ariaLabel?: string;
};

const RECENT_NOTIFICATION_LIMIT = 3;
const AUTO_PREVIEW_DURATION_MS = 10_000;

function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function dedupeNotifications(records: NotificationRecord[]) {
  // WS 新通知和最近通知列表可能包含同一条记录，铃铛面板只展示一次。
  const seenIds = new Set<number>();
  return records.filter((record) => {
    if (seenIds.has(record.id)) {
      return false;
    }
    seenIds.add(record.id);
    return true;
  });
}

export default function NotificationBellButton({
  className,
  badgeClassName,
  iconSize = 18,
  countCap = 9,
  ariaLabel = "打开通知中心",
}: NotificationBellButtonProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const { role } = useAuth();
  const {
    unreadCount,
    connectionStatus,
    latestEventVersion,
    latestIncomingNotification,
    latestIncomingEventVersion,
    recentNotifications,
    recentNotificationsLoading,
    recentNotificationsError,
    ensureRecentNotifications,
    dismissIncomingPreview,
    markAllNotificationsRead,
    markNotificationRead,
  } = useNotificationCenter();
  const historyWrapperRef = useRef<HTMLDivElement | null>(null);
  const historyCloseTimerRef = useRef<number | null>(null);
  const previewCloseTimerRef = useRef<number | null>(null);
  const previewExpireAtRef = useRef<number | null>(null);
  const historyInteractionRef = useRef(false);
  const [historyPanelOpen, setHistoryPanelOpen] = useState(false);
  const [incomingPreview, setIncomingPreview] = useState<NotificationRecord | null>(null);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [markingAllRead, setMarkingAllRead] = useState(false);
  const currentRole = (role ?? null) as SessionRole;
  const currentPath = `${location.pathname}${location.search}${location.hash}`;
  // 从业务页进入通知中心时附带来源；已经在通知中心时不再覆盖返回链。
  const fromPath = location.pathname === "/notifications" ? undefined : currentPath;
  const hasUnread = unreadCount > 0;
  const badgeText = unreadCount > countCap ? `${countCap}+` : `${unreadCount}`;
  const title = hasUnread
    ? `通知中心（当前 ${badgeText} 条未读）`
    : connectionStatus === "connected"
      ? "通知中心"
      : "通知中心（实时连接同步中）";

  const listNotificationsForPanel = useMemo(
    () => dedupeNotifications(recentNotifications).slice(0, RECENT_NOTIFICATION_LIMIT),
    [recentNotifications],
  );
  const canMarkAllRead = unreadCount > 0
    || recentNotifications.some((record) => !record.read)
    || Boolean(incomingPreview && !incomingPreview.read);

  const clearHistoryCloseTimer = useCallback(() => {
    if (historyCloseTimerRef.current !== null) {
      window.clearTimeout(historyCloseTimerRef.current);
      historyCloseTimerRef.current = null;
    }
  }, []);

  const clearPreviewCloseTimer = useCallback(() => {
    if (previewCloseTimerRef.current !== null) {
      window.clearTimeout(previewCloseTimerRef.current);
      previewCloseTimerRef.current = null;
    }
  }, []);

  const closePreviewNow = useCallback((notificationId?: number) => {
    clearPreviewCloseTimer();
    previewExpireAtRef.current = null;
    setPreviewOpen(false);
    setIncomingPreview(null);
    dismissIncomingPreview(notificationId);
  }, [clearPreviewCloseTimer, dismissIncomingPreview]);

  const schedulePreviewClose = useCallback((durationMs: number, notificationId?: number) => {
    clearPreviewCloseTimer();
    previewCloseTimerRef.current = window.setTimeout(() => {
      closePreviewNow(notificationId);
    }, Math.max(durationMs, 0));
  }, [clearPreviewCloseTimer, closePreviewNow]);

  const openHistoryPanel = useCallback((forceLoad = false) => {
    clearHistoryCloseTimer();
    closePreviewNow(incomingPreview?.id);
    historyInteractionRef.current = true;
    setHistoryPanelOpen(true);
    // 展开历史面板时按需补拉，避免每次顶栏渲染都触发列表请求。
    void ensureRecentNotifications(forceLoad);
  }, [clearHistoryCloseTimer, closePreviewNow, ensureRecentNotifications, incomingPreview?.id]);

  const closeHistoryPanelNow = useCallback(() => {
    clearHistoryCloseTimer();
    if (historyInteractionRef.current) {
      return;
    }
    setHistoryPanelOpen(false);
  }, [clearHistoryCloseTimer]);

  const scheduleHistoryClose = useCallback(() => {
    clearHistoryCloseTimer();
    historyCloseTimerRef.current = window.setTimeout(() => {
      closeHistoryPanelNow();
    }, 140);
  }, [clearHistoryCloseTimer, closeHistoryPanelNow]);

  const handleMarkAllRead = useCallback(async () => {
    if (markingAllRead || !canMarkAllRead) {
      return;
    }

    setMarkingAllRead(true);

    try {
      await markAllNotificationsRead();
    } catch {
      return;
    } finally {
      setMarkingAllRead(false);
    }
  }, [
    canMarkAllRead,
    markAllNotificationsRead,
    markingAllRead,
  ]);

  const navigateToNotification = useCallback((notification: NotificationRecord) => {
    const href = resolveNotificationHref(notification, currentRole) || "/notifications";
    const navigateOptions = href === "/notifications" && fromPath
      ? { state: { from: fromPath } }
      : undefined;

    // 跳转前乐观标记已读，失败不会阻断业务 deep-link。
    if (!notification.read) {
      void markNotificationRead(notification.id, {
        knownUnread: true,
        dismissPreview: true,
      }).catch(() => undefined);
    } else {
      dismissIncomingPreview(notification.id);
    }

    historyInteractionRef.current = false;
    setHistoryPanelOpen(false);
    closePreviewNow(notification.id);
    navigate(href, navigateOptions);
  }, [
    closePreviewNow,
    currentRole,
    dismissIncomingPreview,
    fromPath,
    markNotificationRead,
    navigate,
  ]);

  const handleHistoryInteractionStart = useCallback(() => {
    openHistoryPanel(false);
  }, [openHistoryPanel]);

  const handleHistoryInteractionEnd = useCallback(() => {
    historyInteractionRef.current = false;
    scheduleHistoryClose();
  }, [scheduleHistoryClose]);

  const handleHistoryBlurCapture = useCallback((event: FocusEvent<HTMLDivElement>) => {
    const nextTarget = event.relatedTarget;
    if (nextTarget instanceof Node && historyWrapperRef.current?.contains(nextTarget)) {
      return;
    }
    handleHistoryInteractionEnd();
  }, [handleHistoryInteractionEnd]);

  const handlePreviewInteractionStart = useCallback(() => {
    clearPreviewCloseTimer();
  }, [clearPreviewCloseTimer]);

  const handlePreviewInteractionEnd = useCallback(() => {
    if (!incomingPreview) {
      closePreviewNow();
      return;
    }

    const expireAt = previewExpireAtRef.current;
    if (!expireAt || expireAt <= Date.now()) {
      closePreviewNow(incomingPreview.id);
      return;
    }

    schedulePreviewClose(expireAt - Date.now(), incomingPreview.id);
  }, [closePreviewNow, incomingPreview, schedulePreviewClose]);

  const handlePreviewBlurCapture = useCallback((event: FocusEvent<HTMLDivElement>) => {
    const nextTarget = event.relatedTarget;
    if (nextTarget instanceof Node && event.currentTarget.contains(nextTarget)) {
      return;
    }
    handlePreviewInteractionEnd();
  }, [handlePreviewInteractionEnd]);

  useEffect(() => {
    if (!historyPanelOpen) {
      return;
    }
    void ensureRecentNotifications(false);
  }, [ensureRecentNotifications, historyPanelOpen, latestEventVersion]);

  useEffect(() => {
    if (!latestIncomingNotification || latestIncomingEventVersion <= 0) {
      return;
    }

    setIncomingPreview(latestIncomingNotification);

    // 已在通知页或正在看历史面板时只消化事件，不再弹出悬浮预览。
    if (location.pathname === "/notifications" || historyPanelOpen || historyInteractionRef.current) {
      setPreviewOpen(false);
      dismissIncomingPreview(latestIncomingNotification.id);
      return;
    }

    clearPreviewCloseTimer();
    previewExpireAtRef.current = Date.now() + AUTO_PREVIEW_DURATION_MS;
    setPreviewOpen(true);
    schedulePreviewClose(AUTO_PREVIEW_DURATION_MS, latestIncomingNotification.id);
  }, [
    clearPreviewCloseTimer,
    dismissIncomingPreview,
    historyPanelOpen,
    latestIncomingEventVersion,
    latestIncomingNotification,
    location.pathname,
    schedulePreviewClose,
  ]);

  useEffect(() => () => {
    clearHistoryCloseTimer();
    clearPreviewCloseTimer();
  }, [clearHistoryCloseTimer, clearPreviewCloseTimer]);

  return (
    <div ref={historyWrapperRef} className="relative">
      <Link
        to="/notifications"
        state={fromPath ? { from: fromPath } : undefined}
        aria-label={ariaLabel}
        title={title}
        onMouseEnter={handleHistoryInteractionStart}
        onMouseLeave={handleHistoryInteractionEnd}
        onFocus={handleHistoryInteractionStart}
        onClick={() => {
          historyInteractionRef.current = false;
          setHistoryPanelOpen(false);
          closePreviewNow(incomingPreview?.id);
        }}
        className={joinClasses(
          "relative inline-flex h-10 items-center justify-center rounded-full border border-slate-200 bg-white px-4 text-sm font-semibold text-slate-600 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600",
          className,
        )}
      >
        <Bell size={iconSize} />
        {hasUnread ? (
          <span
            className={joinClasses(
              "absolute -right-1 -top-1 inline-flex h-[1.35rem] min-w-[1.35rem] items-center justify-center rounded-full border-2 border-white bg-red-500 px-1 text-[10px] font-bold leading-none text-white shadow-[0_8px_18px_rgba(239,68,68,0.35)]",
              badgeClassName,
            )}
          >
            {badgeText}
          </span>
        ) : null}
      </Link>

      <AnimatePresence>
        {previewOpen && incomingPreview && !historyPanelOpen ? (
          <motion.div
            initial={{ opacity: 0, y: -10, scaleX: 0.94, scaleY: 0.8 }}
            animate={{ opacity: 1, y: 0, scaleX: 1, scaleY: 1 }}
            exit={{ opacity: 0, y: -8, scaleX: 0.96, scaleY: 0.82 }}
            transition={{ duration: 0.22, ease: [0.22, 1, 0.36, 1] }}
            style={{ transformOrigin: "calc(100% - 1.2rem) -0.65rem" }}
            className="absolute right-0 top-full z-[79] mt-2.5 w-[21rem]"
          >
            <motion.span
              aria-hidden="true"
              initial={{ opacity: 0, scaleY: 0.4 }}
              animate={{ opacity: 1, scaleY: 1 }}
              exit={{ opacity: 0, scaleY: 0.4 }}
              transition={{ duration: 0.16, ease: [0.22, 1, 0.36, 1] }}
              style={{ transformOrigin: "top center" }}
              className="pointer-events-none absolute -top-2.5 right-[1.18rem] h-3.5 w-[2px] rounded-full bg-gradient-to-b from-sky-200/25 via-white/95 to-white/0"
            />
            <motion.span
              aria-hidden="true"
              initial={{ opacity: 0, scale: 0.72, y: -4, rotate: 45 }}
              animate={{ opacity: 1, scale: 1, y: 0, rotate: 45 }}
              exit={{ opacity: 0, scale: 0.78, y: -3, rotate: 45 }}
              transition={{ duration: 0.16, ease: [0.22, 1, 0.36, 1] }}
              className="pointer-events-none absolute -top-2 right-[0.75rem] h-4 w-4 rounded-[0.38rem] border border-white/90 bg-white/95 shadow-[0_12px_26px_rgba(15,23,42,0.08)]"
            />

            <div
              onMouseEnter={handlePreviewInteractionStart}
              onMouseLeave={handlePreviewInteractionEnd}
              onFocusCapture={handlePreviewInteractionStart}
              onBlurCapture={handlePreviewBlurCapture}
              className="overflow-hidden rounded-[1.6rem] border border-white/90 bg-white/95 p-2.5 shadow-[0_24px_60px_rgba(15,23,42,0.16)] backdrop-blur-xl"
            >
              <button
                type="button"
                onClick={() => navigateToNotification(incomingPreview)}
                className="group flex w-full items-center gap-3 rounded-[1.35rem] border border-teal-100 bg-gradient-to-r from-teal-50 via-sky-50 to-white px-4 py-3.5 text-left transition-colors hover:border-teal-200 hover:from-teal-100 hover:via-sky-50 hover:to-white"
              >
                <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-white text-teal-600 shadow-[0_10px_24px_rgba(45,212,191,0.18)]">
                  <BellRing size={18} />
                </div>
                <div className="min-w-0 flex-1">
                  <div className="mb-1 text-[11px] font-semibold text-teal-600">新消息</div>
                  <div className="flex items-center gap-2">
                    <span className="inline-flex shrink-0 rounded-full bg-white px-2.5 py-1 text-[10px] font-semibold text-teal-700 shadow-[0_4px_12px_rgba(45,212,191,0.12)]">
                      {getNotificationCategoryLabel(String(incomingPreview.category))}
                    </span>
                    <span className="truncate text-sm font-bold text-slate-900">{incomingPreview.title}</span>
                  </div>
                  <div className="mt-2 text-[11px] text-slate-400">
                    {incomingPreview.createdAt ? formatDateTime(incomingPreview.createdAt) : "刚刚送达"}
                  </div>
                </div>
                {!incomingPreview.read ? <span className="h-2.5 w-2.5 shrink-0 rounded-full bg-rose-500" /> : null}
              </button>
            </div>
          </motion.div>
        ) : null}
      </AnimatePresence>

      <AnimatePresence>
        {historyPanelOpen ? (
          <motion.div
            initial={{ opacity: 0, y: -10, scaleX: 0.94, scaleY: 0.74 }}
            animate={{ opacity: 1, y: 0, scaleX: 1, scaleY: 1 }}
            exit={{ opacity: 0, y: -8, scaleX: 0.95, scaleY: 0.78 }}
            transition={{ duration: 0.24, ease: [0.22, 1, 0.36, 1] }}
            style={{ transformOrigin: "calc(100% - 1.2rem) -0.65rem" }}
            className="absolute right-0 top-full z-[80] mt-2.5 w-[23rem]"
            onMouseEnter={handleHistoryInteractionStart}
            onMouseLeave={handleHistoryInteractionEnd}
            onFocusCapture={handleHistoryInteractionStart}
            onBlurCapture={handleHistoryBlurCapture}
          >
            <span
              aria-hidden="true"
              className="absolute -top-4 right-[-0.6rem] h-4 w-[11rem]"
            />
            <motion.span
              aria-hidden="true"
              initial={{ opacity: 0, scaleY: 0.4 }}
              animate={{ opacity: 1, scaleY: 1 }}
              exit={{ opacity: 0, scaleY: 0.4 }}
              transition={{ duration: 0.18, ease: [0.22, 1, 0.36, 1] }}
              style={{ transformOrigin: "top center" }}
              className="pointer-events-none absolute -top-2.5 right-[1.18rem] h-3.5 w-[2px] rounded-full bg-gradient-to-b from-sky-200/25 via-white/95 to-white/0"
            />
            <motion.span
              aria-hidden="true"
              initial={{ opacity: 0, scale: 0.72, y: -4, rotate: 45 }}
              animate={{ opacity: 1, scale: 1, y: 0, rotate: 45 }}
              exit={{ opacity: 0, scale: 0.78, y: -3, rotate: 45 }}
              transition={{ duration: 0.18, ease: [0.22, 1, 0.36, 1] }}
              className="pointer-events-none absolute -top-2 right-[0.75rem] h-4 w-4 rounded-[0.38rem] border border-white/90 bg-white/95 shadow-[0_12px_26px_rgba(15,23,42,0.08)]"
            />

            <div className="overflow-hidden rounded-[1.6rem] border border-white/90 bg-white/95 p-3 shadow-[0_24px_60px_rgba(15,23,42,0.16)] backdrop-blur-xl">
              <div className="flex items-center justify-between gap-3 px-2 pb-3">
                <div>
                  <div className="text-sm font-bold text-slate-900">最近消息</div>
                  <div className="mt-1 text-[11px] text-slate-500">
                    {hasUnread ? `还有 ${badgeText} 条未读提醒` : "新的动态会先出现在这里"}
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => void handleMarkAllRead()}
                    disabled={!canMarkAllRead || markingAllRead}
                    className="inline-flex items-center rounded-full border border-slate-200 bg-slate-50 px-3 py-1.5 text-xs font-semibold text-slate-700 transition-colors hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {markingAllRead ? (
                      <Loader2 size={13} className="mr-1.5 animate-spin" />
                    ) : (
                      <CheckCheck size={13} className="mr-1.5" />
                    )}
                    全部已读
                  </button>
                  <Link
                    to="/notifications"
                    state={fromPath ? { from: fromPath } : undefined}
                    onClick={() => {
                      historyInteractionRef.current = false;
                      setHistoryPanelOpen(false);
                    }}
                    className="inline-flex items-center rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-600 transition-colors hover:border-teal-200 hover:bg-teal-50 hover:text-teal-600"
                  >
                    查看全部
                  </Link>
                </div>
              </div>

              <div className="space-y-2">
                {recentNotificationsLoading && !listNotificationsForPanel.length ? (
                  <div className="flex items-center justify-center gap-2 rounded-[1.3rem] border border-dashed border-slate-200 bg-slate-50/80 px-4 py-6 text-sm text-slate-500">
                    <Loader2 size={16} className="animate-spin" />
                    正在整理最近消息
                  </div>
                ) : null}

                {!recentNotificationsLoading && recentNotificationsError ? (
                  <div className="rounded-[1.3rem] border border-amber-100 bg-amber-50/80 px-4 py-4 text-sm text-amber-700">
                    {recentNotificationsError}
                  </div>
                ) : null}

                {!recentNotificationsLoading && !recentNotificationsError && !listNotificationsForPanel.length ? (
                  <div className="rounded-[1.3rem] border border-dashed border-slate-200 bg-slate-50/80 px-4 py-6 text-sm text-slate-500">
                    现在还没有新的通知，后续动态会自动出现在这里。
                  </div>
                ) : null}

                {listNotificationsForPanel.map((notification) => (
                  <button
                    key={notification.id}
                    type="button"
                    onClick={() => navigateToNotification(notification)}
                    className="group flex w-full items-center gap-3 rounded-[1.25rem] border border-slate-100 bg-slate-50/85 px-4 py-3 text-left transition-colors hover:border-indigo-100 hover:bg-indigo-50/65"
                  >
                    <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-white text-indigo-600 shadow-[0_8px_18px_rgba(79,70,229,0.08)]">
                      <Bell size={16} />
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center justify-between gap-3">
                        <div className="flex min-w-0 items-center gap-2">
                          <span className="inline-flex shrink-0 rounded-full bg-white px-2.5 py-1 text-[10px] font-semibold text-slate-600 shadow-[0_4px_12px_rgba(15,23,42,0.06)]">
                            {getNotificationCategoryLabel(String(notification.category))}
                          </span>
                          <span className="truncate text-sm font-bold text-slate-900">{notification.title}</span>
                        </div>
                        {!notification.read ? <span className="h-2.5 w-2.5 shrink-0 rounded-full bg-rose-500" /> : null}
                      </div>
                      <div className="mt-2 text-[11px] text-slate-400">
                        {formatDateTime(notification.createdAt)}
                      </div>
                    </div>
                  </button>
                ))}
              </div>
            </div>
          </motion.div>
        ) : null}
      </AnimatePresence>
    </div>
  );
}
