package com.bishe.server.auth.dto;

/**
 * 当前用户信息响应。
 */
public record MeResponse(
        Long userId,
        String role,
        String displayName,
        String email
) {
}
