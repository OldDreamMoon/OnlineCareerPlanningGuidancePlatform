import { apiRequest } from "./apiClient";

type TimeValue = number | string | null;

type MentorIdentityResponse = {
  userId: number;
  displayName: string;
  avatarUrl: string | null;
  avatarUpdatedAt: TimeValue;
};

export type MentorIdentitySnapshot = {
  userId: number | null;
  displayName: string | null;
  avatarUrl: string | null;
  avatarUpdatedAt: TimeValue;
  updatedAt: string;
};

const MENTOR_IDENTITY_STORAGE_PREFIX = "bishe.mentor.identity.v1";
const MENTOR_DISPLAY_NAME_PLACEHOLDERS = new Set(["导师", "当前账号", "当前用户", "用户"]);

function normalizeMentorIdentityText(value: string | null | undefined) {
  const normalized = value?.trim() ?? "";
  if (!normalized || MENTOR_DISPLAY_NAME_PLACEHOLDERS.has(normalized)) {
    return null;
  }

  return normalized;
}

function getSessionStorage() {
  if (typeof window === "undefined") {
    return null;
  }

  return window.sessionStorage;
}

function getLocalStorage() {
  if (typeof window === "undefined") {
    return null;
  }

  return window.localStorage;
}

function buildStorageKey(scope: number | "current") {
  return `${MENTOR_IDENTITY_STORAGE_PREFIX}.${scope}`;
}

function parseSnapshot(raw: string | null) {
  if (!raw) {
    return null;
  }

  try {
    const parsed = JSON.parse(raw) as Partial<MentorIdentitySnapshot> | null;
    if (!parsed || typeof parsed !== "object") {
      return null;
    }

    return {
      userId: typeof parsed.userId === "number" ? parsed.userId : null,
      displayName: normalizeMentorIdentityText(parsed.displayName),
      avatarUrl: typeof parsed.avatarUrl === "string" ? parsed.avatarUrl : null,
      avatarUpdatedAt: typeof parsed.avatarUpdatedAt === "number" || typeof parsed.avatarUpdatedAt === "string"
        ? parsed.avatarUpdatedAt
        : null,
      updatedAt: typeof parsed.updatedAt === "string" ? parsed.updatedAt : new Date().toISOString(),
    } satisfies MentorIdentitySnapshot;
  } catch {
    return null;
  }
}

function readStorageItem(key: string) {
  return getSessionStorage()?.getItem(key) ?? getLocalStorage()?.getItem(key) ?? null;
}

export function readMentorIdentitySnapshot(userId?: number | null) {
  if (userId) {
    return parseSnapshot(readStorageItem(buildStorageKey(userId)));
  }

  return parseSnapshot(readStorageItem(buildStorageKey("current")));
}

export function writeMentorIdentitySnapshot(options: {
  userId?: number | null;
  displayName?: string | null;
  avatarUrl?: string | null;
  avatarUpdatedAt?: TimeValue;
  updatedAt?: string;
}) {
  const existing = readMentorIdentitySnapshot(options.userId) ?? readMentorIdentitySnapshot(null);
  const displayName = normalizeMentorIdentityText(options.displayName) ?? existing?.displayName ?? null;
  const snapshot: MentorIdentitySnapshot = {
    userId: typeof options.userId === "number" ? options.userId : existing?.userId ?? null,
    displayName,
    avatarUrl: options.avatarUrl !== undefined ? options.avatarUrl : existing?.avatarUrl ?? null,
    avatarUpdatedAt: options.avatarUpdatedAt !== undefined ? options.avatarUpdatedAt : existing?.avatarUpdatedAt ?? null,
    updatedAt: options.updatedAt ?? new Date().toISOString(),
  };

  const payload = JSON.stringify(snapshot);
  const currentKey = buildStorageKey("current");
  getSessionStorage()?.setItem(currentKey, payload);
  getLocalStorage()?.setItem(currentKey, payload);

  if (snapshot.userId) {
    const scopedKey = buildStorageKey(snapshot.userId);
    getSessionStorage()?.setItem(scopedKey, payload);
    getLocalStorage()?.setItem(scopedKey, payload);
  }

  return snapshot;
}

export async function fetchMentorIdentitySnapshot(userId?: number | null, signal?: AbortSignal) {
  const response = await apiRequest<MentorIdentityResponse>("/mentor/profile", { signal });
  return writeMentorIdentitySnapshot({
    userId: response.userId ?? userId ?? null,
    displayName: response.displayName,
    avatarUrl: response.avatarUrl ?? null,
    avatarUpdatedAt: response.avatarUpdatedAt ?? null,
  });
}
