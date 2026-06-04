package com.bishe.server.auth.dto;

/**
 * 登录/刷新响应。
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        String role
) {
}
