import { apiRequest } from "./apiClient";

const GEETEST_SCRIPT_URL = "https://static.geetest.com/v4/gt4.js";
type TimeValue = number | string;

export type GeetestCaptchaPublicConfig = {
  enabled: boolean;
  provider: string;
  captchaId: string | null;
  product: "bind" | "float" | "popup";
  proofTtlSeconds: number;
  demoModeEnabled: boolean;
  demoRegisterEmailVerificationBypassEnabled: boolean;
  demoPasswordResetBypassEnabled: boolean;
  demoCertificationBypassEnabled: boolean;
};

export type GeetestVerifyResponse = {
  valid: boolean;
  provider: string;
  verificationToken: string;
  expiresAt: TimeValue;
  reason: string;
};

export type GeetestValidateResult = {
  lot_number: string;
  captcha_output: string;
  pass_token: string;
  gen_time: string;
};

export type GeetestError = {
  code?: string;
  msg?: string;
  desc?: {
    detail?: string;
  };
};

export type GeetestCaptchaObject = {
  getValidate: () => GeetestValidateResult | null;
  onReady: (callback: () => void) => GeetestCaptchaObject;
  onSuccess: (callback: () => void) => GeetestCaptchaObject;
  onError: (callback: (error: GeetestError) => void) => GeetestCaptchaObject;
  onClose?: (callback: () => void) => GeetestCaptchaObject;
  reset?: () => void;
  destroy?: () => void;
  showCaptcha?: () => void;
  showBox?: () => void;
};

declare global {
  interface Window {
    initGeetest4?: (
      config: {
        captchaId: string;
        product?: "bind" | "float" | "popup";
        protocol?: string;
      },
      handler: (captchaObj: GeetestCaptchaObject) => void,
    ) => void;
  }
}

let geetestScriptPromise: Promise<void> | null = null;

export function fetchGeetestCaptchaConfig() {
  return apiRequest<GeetestCaptchaPublicConfig>("/auth/captcha/config", {
    skipAuth: true,
  });
}

export function verifyGeetestCaptcha(payload: {
  email: string;
  lotNumber: string;
  captchaOutput: string;
  passToken: string;
  genTime: string;
}) {
  return apiRequest<GeetestVerifyResponse>("/auth/captcha/geetest/verify", {
    method: "POST",
    skipAuth: true,
    body: JSON.stringify(payload),
  });
}

export function loadGeetestScript() {
  if (typeof window === "undefined") {
    return Promise.reject(new Error("window is not available"));
  }

  if (window.initGeetest4) {
    return Promise.resolve();
  }

  if (geetestScriptPromise) {
    return geetestScriptPromise;
  }

  geetestScriptPromise = new Promise<void>((resolve, reject) => {
    const existingScript = document.querySelector<HTMLScriptElement>(`script[src="${GEETEST_SCRIPT_URL}"]`);

    if (existingScript && window.initGeetest4) {
      resolve();
      return;
    }

    const script = existingScript ?? document.createElement("script");
    script.src = GEETEST_SCRIPT_URL;
    script.async = true;

    script.onload = () => {
      if (window.initGeetest4) {
        resolve();
        return;
      }

      geetestScriptPromise = null;
      reject(new Error("initGeetest4 is unavailable after script load"));
    };

    script.onerror = () => {
      geetestScriptPromise = null;
      reject(new Error("failed to load geetest script"));
    };

    if (!existingScript) {
      document.head.appendChild(script);
    }
  });

  return geetestScriptPromise;
}

export function openGeetestCaptcha(captchaObj: GeetestCaptchaObject) {
  if (typeof captchaObj.showCaptcha === "function") {
    captchaObj.showCaptcha();
    return;
  }

  if (typeof captchaObj.showBox === "function") {
    captchaObj.showBox();
    return;
  }

  throw new Error("geetest captcha object cannot be opened");
}

export function resetGeetestCaptcha(captchaObj: GeetestCaptchaObject | null) {
  captchaObj?.reset?.();
}
