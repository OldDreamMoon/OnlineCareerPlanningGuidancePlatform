import { clearSession, getSessionSnapshot, setSession } from "./sessionStore";

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? "/api/v1").replace(/\/$/, "");

export type ApiEnvelope<T> = {
  code: string;
  message: string;
  data: T;
  traceId: string;
  timestamp: number | string;
};

export class ApiClientError extends Error {
  status: number;
  code: string;
  traceId: string | null;
  data: unknown;

  constructor(message: string, status: number, code = "HTTP_ERROR", traceId: string | null = null, data: unknown = null) {
    super(message);
    this.name = "ApiClientError";
    this.status = status;
    this.code = code;
    this.traceId = traceId;
    this.data = data;
  }
}

export function isAbortError(error: unknown) {
  return error instanceof DOMException
    ? error.name === "AbortError"
    : error instanceof Error && error.name === "AbortError";
}

type RequestOptions = RequestInit & {
  skipAuth?: boolean;
  rawResponse?: boolean;
};

let refreshPromise: Promise<boolean> | null = null;
const inflightGetRequests = new Map<string, Promise<unknown>>();

function buildUrl(path: string) {
  // 这里统一兼容绝对 URL、已经带 /api 的路径和普通业务路径，页面不用关心 baseUrl 拼接。
  if (path.startsWith("http://") || path.startsWith("https://")) {
    return path;
  }

  if (path.startsWith("/api/")) {
    return path;
  }

  return `${API_BASE_URL}${path.startsWith("/") ? path : `/${path}`}`;
}

async function parseEnvelope<T>(response: Response): Promise<ApiEnvelope<T> | null> {
  // 后端大多数接口返回 ApiEnvelope；空响应保留 null，方便 raw/204 类场景复用。
  const text = await response.text();

  if (!text) {
    return null;
  }

  try {
    return JSON.parse(text) as ApiEnvelope<T>;
  } catch {
    throw new ApiClientError(text, response.status, "INVALID_JSON", null);
  }
}

async function refreshSession() {
  // 多个请求同时遇到 401 时共用一次 refresh，避免刷新接口并发打爆。
  if (refreshPromise) {
    return refreshPromise;
  }

  const session = getSessionSnapshot();

  if (!session.refreshToken) {
    clearSession();
    return false;
  }

  refreshPromise = (async () => {
    try {
      const response = await fetch(buildUrl("/auth/refresh"), {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ refreshToken: session.refreshToken }),
      });

      const envelope = await parseEnvelope<{ accessToken: string; refreshToken: string; role: string }>(response);

      if (!response.ok || !envelope?.data) {
        clearSession();
        return false;
      }

      setSession({
        ...session,
        accessToken: envelope.data.accessToken,
        refreshToken: envelope.data.refreshToken,
        role: envelope.data.role as typeof session.role,
      });

      return true;
    } catch {
      clearSession();
      return false;
    } finally {
      refreshPromise = null;
    }
  })();

  return refreshPromise;
}

async function requestInternal<T>(path: string, options: RequestOptions, allowRetry: boolean): Promise<T> {
  const { skipAuth = false, rawResponse = false, headers, ...rest } = options;
  const session = getSessionSnapshot();
  const requestHeaders = new Headers(headers ?? {});

  // 有 body 且不是 FormData 时默认按 JSON 发，文件上传则保留浏览器自动生成的 multipart boundary。
  if (!requestHeaders.has("Content-Type") && rest.body && !(rest.body instanceof FormData)) {
    requestHeaders.set("Content-Type", "application/json");
  }

  // token 只在统一请求层注入，页面里不要手工拼 Authorization。
  if (!skipAuth && session.accessToken) {
    requestHeaders.set("Authorization", `Bearer ${session.accessToken}`);
  }

  const response = await fetch(buildUrl(path), {
    ...rest,
    headers: requestHeaders,
  });

  if (response.status === 401 && !skipAuth && allowRetry && session.refreshToken) {
    // 第一次 401 才尝试 refresh；重试仍失败就向外抛错，避免递归刷新。
    const refreshed = await refreshSession();

    if (refreshed) {
      return requestInternal<T>(path, options, false);
    }
  }

  if (rawResponse) {
    // 少数下载/文件场景需要 Response 本体，直接返回给调用方处理。
    return response as T;
  }

  const envelope = await parseEnvelope<T>(response);

  if (!response.ok) {
    throw new ApiClientError(
      envelope?.message ?? response.statusText,
      response.status,
      envelope?.code ?? "HTTP_ERROR",
      envelope?.traceId ?? null,
      envelope?.data ?? null,
    );
  }

  return (envelope?.data ?? null) as T;
}

function buildInFlightGetKey(path: string, options: RequestOptions) {
  const { skipAuth = false, rawResponse = false, method, body, signal, headers } = options;
  const normalizedMethod = (method ?? "GET").toUpperCase();

  // 只对无 body、无 signal 的普通 GET 去重；带取消语义或原始响应的请求保留调用方控制权。
  if (normalizedMethod !== "GET" || rawResponse || body || signal) {
    return null;
  }

  const session = getSessionSnapshot();
  const requestHeaders = new Headers(headers ?? {});

  // GET 去重 key 要包含鉴权头，否则不同用户/登录态可能错误复用同一个响应。
  if (!skipAuth && session.accessToken) {
    requestHeaders.set("Authorization", `Bearer ${session.accessToken}`);
  }

  const serializedHeaders = Array.from(requestHeaders.entries())
    .sort(([leftKey], [rightKey]) => leftKey.localeCompare(rightKey))
    .map(([key, value]) => `${key}:${value}`)
    .join("|");

  return `${normalizedMethod} ${buildUrl(path)} ${serializedHeaders}`;
}

export function apiRequest<T>(path: string, options: RequestOptions = {}) {
  const inFlightKey = buildInFlightGetKey(path, options);

  if (!inFlightKey) {
    // 非普通 GET 不去重，尤其是写请求和可取消请求必须保持调用方语义。
    return requestInternal<T>(path, options, true);
  }

  const existingRequest = inflightGetRequests.get(inFlightKey);
  if (existingRequest) {
    // 同一时刻的重复 GET 共用 Promise，避免页面多个区块重复拉同一份数据。
    return existingRequest as Promise<T>;
  }

  const requestPromise = requestInternal<T>(path, options, true).finally(() => {
    // 请求结束后立刻释放 key，下一次刷新仍会真实访问后端。
    inflightGetRequests.delete(inFlightKey);
  });

  inflightGetRequests.set(inFlightKey, requestPromise as Promise<unknown>);
  return requestPromise;
}

export function buildQuery(params: Record<string, string | number | boolean | null | undefined>) {
  const searchParams = new URLSearchParams();

  // 统一过滤空值，页面构造筛选参数时不用手动处理 undefined/null/空串。
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === "") {
      return;
    }
    searchParams.set(key, String(value));
  });

  const query = searchParams.toString();
  return query ? `?${query}` : "";
}
