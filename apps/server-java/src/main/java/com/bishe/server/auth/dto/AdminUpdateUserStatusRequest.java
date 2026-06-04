package com.bishe.server.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 管理员更新用户状态请求。
 */
public record AdminUpdateUserStatusRequest(
        @NotBlank(message = "status is required")
        String status
) {
}
