package com.bishe.server.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 管理员用户列表分页结果。
 */
@Schema(description = "管理员用户列表分页结果")
public record AdminUserListResponse(
        @Schema(description = "用户列表")
        List<UserItem> records,
        @Schema(description = "总记录数", example = "25")
        long total,
        @Schema(description = "当前页码", example = "1")
        int page,
        @Schema(description = "分页大小", example = "10")
        int size
) {

    /**
     * 管理员用户列表项。
     */
    @Schema(description = "管理员用户列表项")
    public record UserItem(
            @Schema(description = "用户 ID", example = "1001")
            long userId,
            @Schema(description = "邮箱", example = "alice@example.com")
            String email,
            @Schema(description = "显示名称", example = "Alice")
            String displayName,
            @Schema(description = "角色", example = "STUDENT")
            String role,
            @Schema(description = "套餐", example = "FREE")
            String tier,
            @Schema(description = "账号状态", example = "ACTIVE")
            String status,
            @Schema(description = "认证状态，仅导师/企业角色返回", example = "PENDING")
            String approvalStatus,
            @Schema(description = "注册时间（ISO-8601 UTC）", example = "2026-03-05T08:00:00Z")
            Long createdAt,
            @Schema(description = "最近登录时间（ISO-8601 UTC）", example = "2026-03-07T01:20:00Z")
            Long lastLoginAt
    ) {
    }
}
