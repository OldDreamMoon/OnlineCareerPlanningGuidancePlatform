import { createContext, useContext, useEffect, useMemo, useState, useSyncExternalStore, type ReactNode } from "react";
import { apiRequest } from "../lib/apiClient";
import { clearSession, getSessionSnapshot, setSession, subscribeSession, updateSession } from "../lib/sessionStore";

type LoginResponse = {
  accessToken: string;
  refreshToken: string;
  role: "STUDENT" | "MENTOR" | "ENTERPRISE" | "ADMIN";
};

type MeResponse = {
  userId: number;
  role: "STUDENT" | "MENTOR" | "ENTERPRISE" | "ADMIN";
  displayName: string;
  email: string;
};

type AuthContextValue = {
  ready: boolean;
  loading: boolean;
  isAuthenticated: boolean;
  role: string | null;
  userId: number | null;
  displayName: string | null;
  email: string | null;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
  refreshProfile: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const session = useSyncExternalStore(subscribeSession, getSessionSnapshot, getSessionSnapshot);
  const [ready, setReady] = useState(() => !session.accessToken);
  const [loading, setLoading] = useState(false);

  const refreshProfile = async () => {
    // /auth/me 作为会话真相源，token 恢复后用它校准角色、姓名和邮箱。
    const me = await apiRequest<MeResponse>("/auth/me");
    updateSession({
      role: me.role,
      userId: me.userId,
      displayName: me.displayName,
      email: me.email,
    });
  };

  useEffect(() => {
    let active = true;

    (async () => {
      if (session.accessToken) {
        try {
          await refreshProfile();
        } catch {
          // 本地 token 失效或账号状态变化时直接清会话，路由守卫会回到登录页。
          clearSession();
        }
      }

      if (active) {
        setReady(true);
      }
    })();

    return () => {
      active = false;
    };
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      ready,
      loading,
      isAuthenticated: !!session.accessToken,
      role: session.role,
      userId: session.userId,
      displayName: session.displayName,
      email: session.email,
      login: async (email: string, password: string) => {
        setLoading(true);
        try {
          const normalizedEmail = email.trim().toLowerCase();
          const response = await apiRequest<LoginResponse>("/auth/login", {
            method: "POST",
            skipAuth: true,
            body: JSON.stringify({ email: normalizedEmail, password }),
          });

          // 先保存 token，随后 refreshProfile 用后端最新资料覆盖本地角色展示信息。
          setSession({
            accessToken: response.accessToken,
            refreshToken: response.refreshToken,
            role: response.role,
            userId: null,
            displayName: null,
            email: normalizedEmail,
          });

          await refreshProfile();
        } finally {
          setLoading(false);
        }
      },
      logout: () => {
        clearSession();
      },
      refreshProfile,
    }),
    [loading, ready, session.accessToken, session.displayName, session.email, session.role, session.userId],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error("useAuth must be used within AuthProvider");
  }

  return context;
}
