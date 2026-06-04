package com.bishe.server.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 管理员更新用户等级请求。
 */
public record AdminUpdateUserTierRequest(
        @NotBlank(message = "tier is required")
        String tier
) {
}
