export type SessionRole = "STUDENT" | "MENTOR" | "ENTERPRISE" | "ADMIN" | null;

export type SessionState = {
  accessToken: string | null;
  refreshToken: string | null;
  role: SessionRole;
  userId: number | null;
  displayName: string | null;
  email: string | null;
};

type StoredSessionState = Omit<SessionState, "userId"> & {
  userId: number | null;
};

const SESSION_STORAGE_KEY = "bishe.admin.session";
const SESSION_SYNC_EVENT_KEY = "bishe.admin.session.sync";
const listeners = new Set<() => void>();

function emptySession(): SessionState {
  return {
    accessToken: null,
    refreshToken: null,
    role: null,
    userId: null,
    displayName: null,
    email: null,
  };
}

function getSessionStorage() {
  if (typeof window === "undefined") {
    return null;
  }

  return window.sessionStorage;
}

function readStoredSession(): SessionState {
  const storage = getSessionStorage();

  if (!storage) {
    return emptySession();
  }

  const raw = storage.getItem(SESSION_STORAGE_KEY) ?? window.localStorage.getItem(SESSION_STORAGE_KEY);

  if (!raw) {
    return emptySession();
  }

  try {
    const parsed = JSON.parse(raw) as Partial<StoredSessionState>;
    storage.setItem(SESSION_STORAGE_KEY, raw);
    return {
      accessToken: parsed.accessToken ?? null,
      refreshToken: parsed.refreshToken ?? null,
      role: parsed.role ?? null,
      userId: typeof parsed.userId === "number" ? parsed.userId : null,
      displayName: parsed.displayName ?? null,
      email: parsed.email ?? null,
    };
  } catch {
    storage.removeItem(SESSION_STORAGE_KEY);
    window.localStorage.removeItem(SESSION_STORAGE_KEY);
    return emptySession();
  }
}

let currentSession = readStoredSession();

function persistSession(nextSession: SessionState) {
  const storage = getSessionStorage();

  if (!storage) {
    return;
  }

  if (!nextSession.accessToken || !nextSession.refreshToken) {
    storage.removeItem(SESSION_STORAGE_KEY);
    window.localStorage.removeItem(SESSION_STORAGE_KEY);
    return;
  }

  const payload: StoredSessionState = {
    accessToken: nextSession.accessToken,
    refreshToken: nextSession.refreshToken,
    role: nextSession.role,
    userId: nextSession.userId,
    displayName: nextSession.displayName,
    email: nextSession.email,
  };

  const serializedPayload = JSON.stringify(payload);
  storage.setItem(SESSION_STORAGE_KEY, serializedPayload);
  window.localStorage.setItem(SESSION_STORAGE_KEY, serializedPayload);
}

function emitChange() {
  listeners.forEach((listener) => listener());
}

function syncSessionClearFromOtherTab() {
  currentSession = emptySession();
  persistSession(currentSession);
  emitChange();
}

function broadcastSessionClear() {
  if (typeof window === "undefined") {
    return;
  }

  window.localStorage.setItem(
    SESSION_SYNC_EVENT_KEY,
    JSON.stringify({
      type: "CLEAR",
      at: Date.now(),
    }),
  );
  window.localStorage.removeItem(SESSION_SYNC_EVENT_KEY);
}

if (typeof window !== "undefined") {
  window.addEventListener("storage", (event) => {
    if (event.key !== SESSION_SYNC_EVENT_KEY || !event.newValue) {
      return;
    }

    try {
      const payload = JSON.parse(event.newValue) as { type?: string };
      if (payload.type === "CLEAR") {
        syncSessionClearFromOtherTab();
      }
    } catch {
      return;
    }
  });
}

export function subscribeSession(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function getSessionSnapshot() {
  return currentSession;
}

function applySession(nextSession: SessionState) {
  currentSession = nextSession;
  persistSession(nextSession);
  emitChange();
}

export function setSession(nextSession: SessionState) {
  applySession(nextSession);
}

export function updateSession(patch: Partial<SessionState>) {
  setSession({
    ...currentSession,
    ...patch,
  });
}

export function clearSession() {
  applySession(emptySession());
  broadcastSessionClear();
}
