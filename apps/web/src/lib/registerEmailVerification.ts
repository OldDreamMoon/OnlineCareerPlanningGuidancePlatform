import { apiRequest } from "./apiClient";

type TimeValue = number | string;

export type SendEmailVerificationCodeResponse = {
  sent: boolean;
  provider: string;
  email: string;
  expiresAt: TimeValue;
  nextSendAt: TimeValue;
  debugCode: string | null;
};

export type VerifyEmailVerificationCodeResponse = {
  verified: boolean;
  provider: string;
  verificationToken: string;
  expiresAt: TimeValue;
};

export function sendEmailVerificationCode(payload: {
  email: string;
  captchaVerificationToken: string;
}) {
  return apiRequest<SendEmailVerificationCodeResponse>("/auth/email/send-code", {
    method: "POST",
    skipAuth: true,
    body: JSON.stringify(payload),
  });
}

export function verifyEmailVerificationCode(payload: {
  email: string;
  code: string;
}) {
  return apiRequest<VerifyEmailVerificationCodeResponse>("/auth/email/verify-code", {
    method: "POST",
    skipAuth: true,
    body: JSON.stringify(payload),
  });
}
