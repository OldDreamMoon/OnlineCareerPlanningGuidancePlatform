import { lazy, Suspense, type ReactNode } from "react";
import { useAuth } from "../../auth/AuthContext";
import {
  inactiveNotificationCenterValue,
  NotificationCenterContext,
  useNotificationCenter,
} from "./NotificationCenterContext";

const NotificationCenterLiveProvider = lazy(() => import("./NotificationCenterLiveProvider"));

function NotificationCenterFallback({ children }: { children: ReactNode }) {
  return (
    <NotificationCenterContext.Provider value={inactiveNotificationCenterValue}>
      {children}
    </NotificationCenterContext.Provider>
  );
}

export function NotificationCenterProvider({ children }: { children: ReactNode }) {
  const { ready, isAuthenticated, role } = useAuth();

  if (!ready || !isAuthenticated || role === "ADMIN") {
    return <NotificationCenterFallback>{children}</NotificationCenterFallback>;
  }

  return (
    <Suspense fallback={<NotificationCenterFallback>{children}</NotificationCenterFallback>}>
      <NotificationCenterLiveProvider>{children}</NotificationCenterLiveProvider>
    </Suspense>
  );
}

export { useNotificationCenter };
