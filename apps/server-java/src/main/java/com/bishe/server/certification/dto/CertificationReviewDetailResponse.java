package com.bishe.server.certification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 管理员认证审核详情响应。
 */
@Schema(description = "管理员认证审核详情响应")
public record CertificationReviewDetailResponse(
        @Schema(description = "用户 ID", example = "2001")
        Long userId,
        @Schema(description = "邮箱", example = "mentor@bishe.local")
        String email,
        @Schema(description = "昵称", example = "导师李然")
        String displayName,
        @Schema(description = "角色", example = "MENTOR")
        String role,
        @Schema(description = "当前资料审核状态", example = "PENDING")
        String approvalStatus,
        CertificationSubmissionResponse currentSubmission,
        List<CertificationSubmissionResponse> submissions
) {
}
