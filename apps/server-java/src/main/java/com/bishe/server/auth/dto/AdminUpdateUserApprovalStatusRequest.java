package com.bishe.server.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 管理员更新用户认证状态请求。
 */
public record AdminUpdateUserApprovalStatusRequest(
        @NotBlank(message = "approvalStatus is required")
        String approvalStatus
) {
}
