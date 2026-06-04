import { useCallback, useMemo } from "react";

type AdminStaleCacheEnvelope<T> = {
  data: T;
  updatedAt: number;
};

type AdminStaleCacheResult<T> = {
  cached: T | null;
  cachedAt: number | null;
  hasCache: boolean;
  write: (data: T) => void;
  clear: () => void;
};

const STORAGE_PREFIX = "admin-stale-cache:";
const memoryCache = new Map<string, AdminStaleCacheEnvelope<unknown>>();

function canUseSessionStorage() {
  return typeof window !== "undefined" && typeof window.sessionStorage !== "undefined";
}

function sortCacheValue(value: unknown): unknown {
  // 缓存 key 需要稳定排序，否则同一组筛选参数换字段顺序会写出多份缓存。
  if (Array.isArray(value)) {
    return value.map(sortCacheValue);
  }

  if (value && typeof value === "object") {
    return Object.keys(value as Record<string, unknown>)
      .sort((left, right) => left.localeCompare(right, "en"))
      .reduce<Record<string, unknown>>((result, key) => {
        result[key] = sortCacheValue((value as Record<string, unknown>)[key]);
        return result;
      }, {});
  }

  return value;
}

function buildStorageKey(cacheKey: string) {
  return `${STORAGE_PREFIX}${cacheKey}`;
}

function readEnvelopeFromStorage<T>(cacheKey: string) {
  if (!canUseSessionStorage()) {
    return null;
  }

  try {
    const rawValue = window.sessionStorage.getItem(buildStorageKey(cacheKey));
    if (!rawValue) {
      return null;
    }
    const parsed = JSON.parse(rawValue) as AdminStaleCacheEnvelope<T>;
    if (!parsed || typeof parsed !== "object" || !("updatedAt" in parsed) || !("data" in parsed)) {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

export function buildAdminStaleCacheKey(scope: string, params?: unknown) {
  if (typeof params === "undefined") {
    return scope;
  }

  return `${scope}:${JSON.stringify(sortCacheValue(params))}`;
}

export function peekAdminStaleCache<T>(cacheKey: string) {
  // 内存缓存负责同页切 tab 的即时回显，sessionStorage 负责刷新页面后的弱恢复。
  const memoryEntry = memoryCache.get(cacheKey) as AdminStaleCacheEnvelope<T> | undefined;
  if (memoryEntry) {
    return memoryEntry;
  }

  const storageEntry = readEnvelopeFromStorage<T>(cacheKey);
  if (storageEntry) {
    memoryCache.set(cacheKey, storageEntry);
    return storageEntry;
  }

  return null;
}

export function writeAdminStaleCache<T>(cacheKey: string, data: T) {
  const envelope: AdminStaleCacheEnvelope<T> = {
    data,
    updatedAt: Date.now(),
  };

  memoryCache.set(cacheKey, envelope);

  if (!canUseSessionStorage()) {
    return envelope;
  }

  try {
    window.sessionStorage.setItem(buildStorageKey(cacheKey), JSON.stringify(envelope));
  } catch {
    // 忽略缓存写入失败，避免影响主流程。
  }

  return envelope;
}

export function clearAdminStaleCache(cacheKey: string) {
  memoryCache.delete(cacheKey);

  if (!canUseSessionStorage()) {
    return;
  }

  try {
    window.sessionStorage.removeItem(buildStorageKey(cacheKey));
  } catch {
    // 忽略缓存清理失败。
  }
}

export function useAdminStaleCache<T>(cacheKey: string): AdminStaleCacheResult<T> {
  // 后台页面先消费旧快照，再由页面自己的请求逻辑决定是否静默刷新。
  const cachedEnvelope = useMemo(() => peekAdminStaleCache<T>(cacheKey), [cacheKey]);

  const write = useCallback((data: T) => {
    writeAdminStaleCache(cacheKey, data);
  }, [cacheKey]);

  const clear = useCallback(() => {
    clearAdminStaleCache(cacheKey);
  }, [cacheKey]);

  return useMemo(() => ({
    cached: cachedEnvelope?.data ?? null,
    cachedAt: cachedEnvelope?.updatedAt ?? null,
    hasCache: Boolean(cachedEnvelope),
    write,
    clear,
  }), [cachedEnvelope, clear, write]);
}
