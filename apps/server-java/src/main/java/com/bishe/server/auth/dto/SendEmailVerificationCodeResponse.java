package com.bishe.server.auth.dto;

/**
 * 发送注册邮箱验证码响应。
 */
public record SendEmailVerificationCodeResponse(
        boolean sent,
        String provider,
        String email,
        Long expiresAt,
        Long nextSendAt,
        String debugCode
) {
}
