package com.bishe.server.certification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 当前用户认证资料视图。
 */
@Schema(description = "当前用户认证资料视图")
public record CertificationOwnViewResponse(
        @Schema(description = "用户 ID", example = "2001")
        Long userId,
        @Schema(description = "角色", example = "MENTOR")
        String role,
        @Schema(description = "当前认证状态", example = "PENDING")
        String approvalStatus,
        CertificationSubmissionResponse currentSubmission,
        List<CertificationSubmissionResponse> submissions
) {
}
