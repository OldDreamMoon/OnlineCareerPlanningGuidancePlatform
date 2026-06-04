type TimeValue = number | string;

export type PendingRegisterRole = "STUDENT" | "MENTOR" | "ENTERPRISE";

export type PendingRegistrationState = {
  email: string;
  password: string;
  displayName: string;
  preferredRole: PendingRegisterRole | null;
  emailVerificationToken: string | null;
  emailVerifiedEmail: string | null;
  emailVerifiedAt: number | null;
  emailVerificationExpiresAt: TimeValue | null;
  createdAt: number;
};

const PENDING_REGISTRATION_STORAGE_KEY = "bishe.auth.pending-registration";

function getPendingRegistrationStorage() {
  if (typeof window === "undefined") {
    return null;
  }

  return window.sessionStorage;
}

function isPendingRegisterRole(value: unknown): value is PendingRegisterRole {
  return value === "STUDENT" || value === "MENTOR" || value === "ENTERPRISE";
}

function readPendingRegistration(): PendingRegistrationState | null {
  const storage = getPendingRegistrationStorage();

  if (!storage) {
    return null;
  }

  const raw = storage.getItem(PENDING_REGISTRATION_STORAGE_KEY);

  if (!raw) {
    return null;
  }

  try {
    const parsed = JSON.parse(raw) as Partial<PendingRegistrationState>;

    if (
      typeof parsed.email !== "string"
      || typeof parsed.password !== "string"
      || typeof parsed.displayName !== "string"
      || typeof parsed.createdAt !== "number"
    ) {
      storage.removeItem(PENDING_REGISTRATION_STORAGE_KEY);
      return null;
    }

    return {
      email: parsed.email,
      password: parsed.password,
      displayName: parsed.displayName,
      preferredRole: isPendingRegisterRole(parsed.preferredRole) ? parsed.preferredRole : null,
      emailVerificationToken: typeof parsed.emailVerificationToken === "string" ? parsed.emailVerificationToken : null,
      emailVerifiedEmail: typeof parsed.emailVerifiedEmail === "string" ? parsed.emailVerifiedEmail : null,
      emailVerifiedAt: typeof parsed.emailVerifiedAt === "number" ? parsed.emailVerifiedAt : null,
      emailVerificationExpiresAt:
        typeof parsed.emailVerificationExpiresAt === "number" || typeof parsed.emailVerificationExpiresAt === "string"
          ? parsed.emailVerificationExpiresAt
          : null,
      createdAt: parsed.createdAt,
    };
  } catch {
    storage.removeItem(PENDING_REGISTRATION_STORAGE_KEY);
    return null;
  }
}

let currentPendingRegistration = readPendingRegistration();

function persistPendingRegistration(nextState: PendingRegistrationState | null) {
  const storage = getPendingRegistrationStorage();

  if (!storage) {
    return;
  }

  if (!nextState) {
    storage.removeItem(PENDING_REGISTRATION_STORAGE_KEY);
    return;
  }

  storage.setItem(PENDING_REGISTRATION_STORAGE_KEY, JSON.stringify(nextState));
}

export function getPendingRegistrationSnapshot() {
  return currentPendingRegistration;
}

export function setPendingRegistration(nextState: PendingRegistrationState) {
  currentPendingRegistration = nextState;
  persistPendingRegistration(nextState);
}

export function clearPendingRegistration() {
  currentPendingRegistration = null;
  persistPendingRegistration(null);
}
