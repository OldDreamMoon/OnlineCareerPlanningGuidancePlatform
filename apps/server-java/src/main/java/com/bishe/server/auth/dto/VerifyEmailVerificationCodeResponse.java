package com.bishe.server.auth.dto;

/**
 * 校验注册邮箱验证码响应。
 */
public record VerifyEmailVerificationCodeResponse(
        boolean verified,
        String provider,
        String verificationToken,
        Long expiresAt
) {
}
