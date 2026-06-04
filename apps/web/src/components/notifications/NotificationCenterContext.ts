import { createContext, useContext } from "react";
import type { NotificationRecord } from "../../lib/notifications";

export type ConnectionStatus = "idle" | "connecting" | "connected" | "reconnecting" | "error";
export type BrowserPermissionState = NotificationPermission | "unsupported";

export type NotificationCenterContextValue = {
  unreadCount: number;
  connectionStatus: ConnectionStatus;
  browserPermission: BrowserPermissionState;
  latestEventVersion: number;
  latestIncomingNotification: NotificationRecord | null;
  latestIncomingEventVersion: number;
  recentNotifications: NotificationRecord[];
  recentNotificationsLoading: boolean;
  recentNotificationsError: string | null;
  ensureRecentNotifications: (force?: boolean) => Promise<void>;
  refreshUnreadCount: (force?: boolean) => Promise<void>;
  dismissIncomingPreview: (notificationId?: number) => void;
  markNotificationRead: (
    notificationId: number,
    options?: {
      knownUnread?: boolean;
      dismissPreview?: boolean;
    },
  ) => Promise<void>;
  markAllNotificationsRead: () => Promise<void>;
  requestBrowserPermission: () => Promise<BrowserPermissionState>;
};

function getFallbackBrowserPermission(): BrowserPermissionState {
  if (typeof window === "undefined" || typeof window.Notification === "undefined") {
    return "unsupported";
  }
  return window.Notification.permission;
}

const noop = () => {};
const noopAsync = async () => {};

export const inactiveNotificationCenterValue: NotificationCenterContextValue = {
  unreadCount: 0,
  connectionStatus: "idle",
  browserPermission: getFallbackBrowserPermission(),
  latestEventVersion: 0,
  latestIncomingNotification: null,
  latestIncomingEventVersion: 0,
  recentNotifications: [],
  recentNotificationsLoading: false,
  recentNotificationsError: null,
  ensureRecentNotifications: noopAsync,
  refreshUnreadCount: noopAsync,
  dismissIncomingPreview: noop,
  markNotificationRead: noopAsync,
  markAllNotificationsRead: noopAsync,
  requestBrowserPermission: async () => getFallbackBrowserPermission(),
};

export const NotificationCenterContext = createContext<NotificationCenterContextValue | null>(null);

export function useNotificationCenter() {
  const context = useContext(NotificationCenterContext);

  if (!context) {
    throw new Error("useNotificationCenter must be used within NotificationCenterProvider");
  }

  return context;
}
