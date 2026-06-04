package com.bishe.server.auth.dto;

/**
 * 极验二次校验成功后的短时注册凭证。
 */
public record GeetestVerifyResponse(
        boolean valid,
        String provider,
        String verificationToken,
        Long expiresAt,
        String reason
) {
}
