import { ApiClientError, apiRequest } from "./apiClient";

const STORAGE_KEY_PREFIX = "bishe.mentor.avatar.cache.";
const MISSING_SENTINEL = "__missing__";
const avatarMemoryCache = new Map<string, string | null>();

function getStorage() {
  if (typeof window === "undefined") {
    return null;
  }

  return window.sessionStorage;
}

function getLatestCacheKey(userId: number) {
  return `${STORAGE_KEY_PREFIX}${userId}.latest`;
}

function getLatestSourceKey(userId: number) {
  return `${STORAGE_KEY_PREFIX}${userId}.latest-source`;
}

function encodeAvatarUrlKey(avatarUrl: string) {
  if (typeof window === "undefined" || typeof window.btoa !== "function") {
    return encodeURIComponent(avatarUrl);
  }

  try {
    return window.btoa(avatarUrl).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
  } catch {
    return encodeURIComponent(avatarUrl);
  }
}

function getVersionCacheKey(userId: number, avatarUrl: string) {
  return `${STORAGE_KEY_PREFIX}${userId}.${encodeAvatarUrlKey(avatarUrl)}`;
}

function getUserCachePrefix(userId: number) {
  return `${STORAGE_KEY_PREFIX}${userId}.`;
}

function readCacheValue(key: string) {
  if (avatarMemoryCache.has(key)) {
    return avatarMemoryCache.get(key);
  }

  const storage = getStorage();
  const storedValue = storage?.getItem(key);
  if (storedValue === null || storedValue === undefined) {
    return undefined;
  }

  const resolvedValue = storedValue === MISSING_SENTINEL ? null : storedValue;
  avatarMemoryCache.set(key, resolvedValue);
  return resolvedValue;
}

function writeCacheValue(key: string, value: string | null) {
  avatarMemoryCache.set(key, value);

  const storage = getStorage();
  if (!storage) {
    return;
  }

  storage.setItem(key, value ?? MISSING_SENTINEL);
}

function cleanupUserCache(userId: number, keepKeys: Set<string>) {
  const userPrefix = getUserCachePrefix(userId);

  Array.from(avatarMemoryCache.keys()).forEach((key) => {
    if (key.startsWith(userPrefix) && !keepKeys.has(key)) {
      avatarMemoryCache.delete(key);
    }
  });

  const storage = getStorage();
  if (!storage) {
    return;
  }

  const removableKeys: string[] = [];
  for (let index = 0; index < storage.length; index += 1) {
    const key = storage.key(index);
    if (key && key.startsWith(userPrefix) && !keepKeys.has(key)) {
      removableKeys.push(key);
    }
  }

  removableKeys.forEach((key) => storage.removeItem(key));
}

export async function blobToDataUrl(blob: Blob) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(typeof reader.result === "string" ? reader.result : "");
    reader.onerror = () => reject(new Error("avatar data url convert failed"));
    reader.readAsDataURL(blob);
  });
}

export function isMentorAvatarCacheableUrl(avatarUrl: string | null | undefined) {
  if (!avatarUrl || typeof window === "undefined") {
    return false;
  }

  try {
    const resolvedUrl = new URL(avatarUrl, window.location.origin);
    return resolvedUrl.origin === window.location.origin;
  } catch {
    return false;
  }
}

type MentorAvatarLookupOptions = {
  userId: number | null | undefined;
  avatarUrl?: string | null;
  allowLatestFallback?: boolean;
};

export function getCachedMentorAvatarDataUrl(options: MentorAvatarLookupOptions) {
  const { userId, avatarUrl, allowLatestFallback = false } = options;
  if (!userId || !avatarUrl || !isMentorAvatarCacheableUrl(avatarUrl)) {
    return null;
  }

  const versionKey = getVersionCacheKey(userId, avatarUrl);
  const versionValue = readCacheValue(versionKey);
  if (versionValue !== undefined) {
    return versionValue;
  }

  if (!allowLatestFallback) {
    return null;
  }

  const latestSource = readCacheValue(getLatestSourceKey(userId));
  if (latestSource !== avatarUrl) {
    return null;
  }

  const latestValue = readCacheValue(getLatestCacheKey(userId));
  return latestValue === undefined ? null : latestValue;
}

type PrimeMentorAvatarCacheOptions = {
  userId: number;
  avatarUrl: string;
  dataUrl?: string;
  blob?: Blob;
};

export async function primeMentorAvatarCache(options: PrimeMentorAvatarCacheOptions) {
  if (!options.avatarUrl || !isMentorAvatarCacheableUrl(options.avatarUrl)) {
    return options.avatarUrl;
  }

  const nextDataUrl = options.dataUrl ?? (options.blob ? await blobToDataUrl(options.blob) : null);
  if (!nextDataUrl) {
    return null;
  }

  const latestKey = getLatestCacheKey(options.userId);
  const latestSourceKey = getLatestSourceKey(options.userId);
  const versionKey = getVersionCacheKey(options.userId, options.avatarUrl);
  const keepKeys = new Set<string>([latestKey, latestSourceKey, versionKey]);

  writeCacheValue(latestKey, nextDataUrl);
  writeCacheValue(latestSourceKey, options.avatarUrl);
  writeCacheValue(versionKey, nextDataUrl);
  cleanupUserCache(options.userId, keepKeys);
  return nextDataUrl;
}

export function clearMentorAvatarCache(userId: number) {
  cleanupUserCache(userId, new Set());
}

type LoadMentorAvatarOptions = MentorAvatarLookupOptions & {
  forceRefresh?: boolean;
};

export async function loadMentorAvatarDataUrl(options: LoadMentorAvatarOptions) {
  const {
    userId,
    avatarUrl,
    allowLatestFallback = false,
    forceRefresh = false,
  } = options;

  if (!userId || !avatarUrl) {
    return null;
  }

  if (!isMentorAvatarCacheableUrl(avatarUrl)) {
    return avatarUrl;
  }

  if (!forceRefresh) {
    const cachedValue = getCachedMentorAvatarDataUrl({ userId, avatarUrl, allowLatestFallback });
    if (cachedValue !== null) {
      return cachedValue;
    }
  }

  const response = await apiRequest<Response>(avatarUrl, { rawResponse: true });
  if (!(response instanceof Response)) {
    throw new ApiClientError("头像加载失败", 500);
  }

  const latestKey = getLatestCacheKey(userId);
  const latestSourceKey = getLatestSourceKey(userId);
  const versionKey = getVersionCacheKey(userId, avatarUrl);

  if (response.status === 404) {
    writeCacheValue(latestKey, null);
    writeCacheValue(latestSourceKey, avatarUrl);
    writeCacheValue(versionKey, null);
    cleanupUserCache(userId, new Set([latestKey, latestSourceKey, versionKey]));
    return null;
  }

  if (!response.ok) {
    throw new ApiClientError("头像加载失败", response.status);
  }

  const blob = await response.blob();
  return primeMentorAvatarCache({
    userId,
    avatarUrl,
    blob,
  });
}
