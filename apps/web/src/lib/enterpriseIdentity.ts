import { apiRequest } from "./apiClient";
import { buildEnterpriseLogoUrl } from "./enterpriseLogo";

type TimeValue = number | string | null;

type EnterpriseIdentitySource = {
  userId?: number | null;
  displayName?: string | null;
  realName?: string | null;
  companyName?: string | null;
  logoUrl?: string | null;
  logoUpdatedAt?: TimeValue;
};

type EnterpriseIdentityResponse = {
  userId: number;
  displayName: string;
  realName: string | null;
  companyName: string | null;
  logoUrl: string | null;
  logoUpdatedAt?: TimeValue;
};

export type EnterpriseIdentitySnapshot = {
  userId: number | null;
  displayName: string | null;
  realName: string | null;
  companyName: string | null;
  logoUrl: string | null;
  logoUpdatedAt: TimeValue;
  updatedAt: string;
};

const ENTERPRISE_IDENTITY_STORAGE_PREFIX = "bishe.enterprise.identity.v1";
const ENTERPRISE_IDENTITY_PLACEHOLDERS = new Set([
  "企业",
  "企业代表",
  "企业账号",
  "当前账号",
  "当前用户",
  "用户",
]);

function normalizeEnterpriseIdentityText(value: string | null | undefined) {
  const normalized = value?.trim() ?? "";
  if (!normalized || ENTERPRISE_IDENTITY_PLACEHOLDERS.has(normalized)) {
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
  return `${ENTERPRISE_IDENTITY_STORAGE_PREFIX}.${scope}`;
}

function parseSnapshot(raw: string | null) {
  if (!raw) {
    return null;
  }

  try {
    const parsed = JSON.parse(raw) as Partial<EnterpriseIdentitySnapshot> | null;
    if (!parsed || typeof parsed !== "object") {
      return null;
    }

    return {
      userId: typeof parsed.userId === "number" ? parsed.userId : null,
      displayName: normalizeEnterpriseIdentityText(parsed.displayName),
      realName: normalizeEnterpriseIdentityText(parsed.realName),
      companyName: normalizeEnterpriseIdentityText(parsed.companyName),
      logoUrl: typeof parsed.logoUrl === "string" ? parsed.logoUrl : null,
      logoUpdatedAt: typeof parsed.logoUpdatedAt === "number" || typeof parsed.logoUpdatedAt === "string"
        ? parsed.logoUpdatedAt
        : null,
      updatedAt: typeof parsed.updatedAt === "string" ? parsed.updatedAt : new Date().toISOString(),
    } satisfies EnterpriseIdentitySnapshot;
  } catch {
    return null;
  }
}

function readStorageItem(key: string) {
  return getSessionStorage()?.getItem(key) ?? getLocalStorage()?.getItem(key) ?? null;
}

export function readEnterpriseIdentitySnapshot(userId?: number | null) {
  if (userId) {
    return parseSnapshot(readStorageItem(buildStorageKey(userId)));
  }

  return parseSnapshot(readStorageItem(buildStorageKey("current")));
}

export function writeEnterpriseIdentitySnapshot(options: {
  userId?: number | null;
  displayName?: string | null;
  realName?: string | null;
  companyName?: string | null;
  logoUrl?: string | null;
  logoUpdatedAt?: TimeValue;
  updatedAt?: string;
}) {
  const existing = readEnterpriseIdentitySnapshot(options.userId) ?? readEnterpriseIdentitySnapshot(null);
  const snapshot: EnterpriseIdentitySnapshot = {
    userId: typeof options.userId === "number" ? options.userId : existing?.userId ?? null,
    displayName: normalizeEnterpriseIdentityText(options.displayName) ?? existing?.displayName ?? null,
    realName: normalizeEnterpriseIdentityText(options.realName) ?? existing?.realName ?? null,
    companyName: normalizeEnterpriseIdentityText(options.companyName) ?? existing?.companyName ?? null,
    logoUrl: options.logoUrl !== undefined ? options.logoUrl : existing?.logoUrl ?? null,
    logoUpdatedAt: options.logoUpdatedAt !== undefined ? options.logoUpdatedAt : existing?.logoUpdatedAt ?? null,
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

export function resolveEnterpriseAccountName(
  profile: EnterpriseIdentitySource | null | undefined,
  fallback = "",
) {
  return normalizeEnterpriseIdentityText(profile?.realName)
    || normalizeEnterpriseIdentityText(fallback)
    || "";
}

export async function fetchEnterpriseIdentitySnapshot(userId?: number | null, signal?: AbortSignal) {
  const response = await apiRequest<EnterpriseIdentityResponse>("/profiles/enterprises/me", { signal });
  return writeEnterpriseIdentitySnapshot({
    userId: response.userId ?? userId ?? null,
    displayName: response.displayName,
    realName: response.realName,
    companyName: response.companyName,
    logoUrl: buildEnterpriseLogoUrl(response.logoUrl, response.logoUpdatedAt),
    logoUpdatedAt: response.logoUpdatedAt ?? null,
  });
}
