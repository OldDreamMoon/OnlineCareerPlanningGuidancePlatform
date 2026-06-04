package com.bishe.server.auth.dto;

import com.bishe.server.certification.dto.CertificationSubmissionResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 注册并提交认证资料响应。
 */
@Schema(description = "注册并提交认证资料响应")
public record RegisterWithCertificationResponse(
        @Schema(description = "用户 ID", example = "2001")
        Long userId,
        @Schema(description = "角色", example = "MENTOR")
        String role,
        @Schema(description = "当前认证状态", example = "PENDING")
        String approvalStatus,
        CertificationSubmissionResponse currentSubmission
) {
}
