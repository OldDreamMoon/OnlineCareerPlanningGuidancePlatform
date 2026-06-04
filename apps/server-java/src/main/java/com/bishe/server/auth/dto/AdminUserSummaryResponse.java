package com.bishe.server.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 管理员用户概览统计响应。
 */
@Schema(description = "管理员用户概览统计")
public record AdminUserSummaryResponse(
        @Schema(description = "平台总用户数", example = "12580")
        long totalUsers,
        @Schema(description = "导师用户数", example = "342")
        long mentorUsers,
        @Schema(description = "企业用户数", example = "28")
        long enterpriseUsers,
        @Schema(description = "高级会员用户数", example = "1890")
        long premiumUsers,
        @Schema(description = "待认证主体数，仅统计导师/企业资料审核状态为 PENDING 的账号", example = "56")
        long pendingApprovalUsers,
        @Schema(description = "已封禁用户数", example = "89")
        long suspendedUsers,
        @Schema(description = "近 7 天活跃用户数", example = "2180")
        long activeUsers7d,
        @Schema(description = "近 7 天新增用户数", example = "164")
        long newUsers7d
) {
}
