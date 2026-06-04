import { ApiClientError, apiRequest } from "./apiClient";

type AvatarTimeValue = number | string;

export type StudentAvatarMeta = {
  uploaded: boolean;
  contentType: string | null;
  updatedAt: AvatarTimeValue | null;
};

const STORAGE_KEY_PREFIX = "bishe.student.avatar.cache.";
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

function getVersionCacheKey(userId: number, updatedAt: AvatarTimeValue) {
  return `${STORAGE_KEY_PREFIX}${userId}.${String(updatedAt)}`;
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

export function deriveAvatarLabel(name: string | null | undefined) {
  const normalized = name?.trim();
  return normalized ? normalized.charAt(0).toUpperCase() : "同";
}

export function buildStudentAvatarPath(userId: number | null | undefined, updatedAt?: AvatarTimeValue | null) {
  if (!userId) {
    return "/profiles/students/0/avatar";
  }
  const basePath = `/profiles/students/${userId}/avatar`;
  return updatedAt ? `${basePath}?v=${encodeURIComponent(String(updatedAt))}` : basePath;
}

export async function blobToDataUrl(blob: Blob) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(typeof reader.result === "string" ? reader.result : "");
    reader.onerror = () => reject(new Error("avatar data url convert failed"));
    reader.readAsDataURL(blob);
  });
}

type AvatarLookupOptions = {
  userId: number | null | undefined;
  avatar?: StudentAvatarMeta | null;
  allowLatestFallback?: boolean;
  avatarPath?: string;
};

function resolveLookupKeys({ userId, avatar, allowLatestFallback }: AvatarLookupOptions) {
  if (!userId) {
    return [];
  }

  const keys: string[] = [];
  if (avatar?.updatedAt) {
    keys.push(getVersionCacheKey(userId, avatar.updatedAt));
  }
  if (!avatar?.updatedAt || allowLatestFallback) {
    keys.push(getLatestCacheKey(userId));
  }

  return keys;
}

export function getCachedStudentAvatarDataUrl(options: AvatarLookupOptions) {
  if (!options.userId) {
    return null;
  }

  if (options.avatar && !options.avatar.uploaded) {
    cleanupUserCache(options.userId, new Set());
    return null;
  }

  for (const key of resolveLookupKeys(options)) {
    const cachedValue = readCacheValue(key);
    if (cachedValue !== undefined) {
      return cachedValue;
    }
  }

  return null;
}

type PrimeAvatarCacheOptions = {
  userId: number;
  updatedAt?: AvatarTimeValue | null;
  dataUrl?: string;
  blob?: Blob;
};

export async function primeStudentAvatarCache(options: PrimeAvatarCacheOptions) {
  const nextDataUrl = options.dataUrl ?? (options.blob ? await blobToDataUrl(options.blob) : null);

  if (!nextDataUrl) {
    return null;
  }

  const latestKey = getLatestCacheKey(options.userId);
  const keepKeys = new Set<string>([latestKey]);

  writeCacheValue(latestKey, nextDataUrl);

  if (options.updatedAt) {
    const versionKey = getVersionCacheKey(options.userId, options.updatedAt);
    writeCacheValue(versionKey, nextDataUrl);
    keepKeys.add(versionKey);
  }

  cleanupUserCache(options.userId, keepKeys);
  return nextDataUrl;
}

export function clearStudentAvatarCache(userId: number) {
  cleanupUserCache(userId, new Set());
}

type LoadAvatarOptions = AvatarLookupOptions & {
  forceRefresh?: boolean;
};

export async function loadStudentAvatarDataUrl(options: LoadAvatarOptions) {
  const {
    userId,
    avatar,
    allowLatestFallback = false,
    forceRefresh = false,
    avatarPath = "/profiles/students/me/avatar",
  } = options;

  if (!userId) {
    return null;
  }

  if (avatar && !avatar.uploaded) {
    clearStudentAvatarCache(userId);
    return null;
  }

  if (!forceRefresh) {
    for (const key of resolveLookupKeys({ userId, avatar, allowLatestFallback })) {
      const cachedValue = readCacheValue(key);
      if (cachedValue !== undefined) {
        return cachedValue;
      }
    }
  }

  const response = await apiRequest<Response>(avatarPath, { rawResponse: true });
  if (!(response instanceof Response)) {
    throw new ApiClientError("头像加载失败", 500);
  }

  const latestKey = getLatestCacheKey(userId);
  const versionKey = avatar?.updatedAt ? getVersionCacheKey(userId, avatar.updatedAt) : null;

  if (response.status === 404) {
    writeCacheValue(latestKey, null);
    if (versionKey) {
      writeCacheValue(versionKey, null);
      cleanupUserCache(userId, new Set([latestKey, versionKey]));
    } else {
      cleanupUserCache(userId, new Set([latestKey]));
    }
    return null;
  }

  if (!response.ok) {
    throw new ApiClientError("头像加载失败", response.status);
  }

  const blob = await response.blob();
  return primeStudentAvatarCache({
    userId,
    updatedAt: avatar?.updatedAt,
    blob,
  });
}
