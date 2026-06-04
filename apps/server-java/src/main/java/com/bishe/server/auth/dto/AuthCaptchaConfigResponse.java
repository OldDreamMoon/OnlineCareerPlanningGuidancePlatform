package com.bishe.server.auth.dto;

/**
 * 注册页公开的人机验证配置。
 */
public record AuthCaptchaConfigResponse(
        boolean enabled,
        String provider,
        String captchaId,
        String product,
        long proofTtlSeconds,
        boolean demoModeEnabled,
        boolean demoRegisterEmailVerificationBypassEnabled,
        boolean demoPasswordResetBypassEnabled,
        boolean demoCertificationBypassEnabled
) {
}
