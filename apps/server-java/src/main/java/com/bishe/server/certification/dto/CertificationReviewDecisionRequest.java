package com.bishe.server.certification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员认证审核请求。
 */
@Schema(description = "管理员认证审核请求")
public record CertificationReviewDecisionRequest(
        @NotBlank(message = "approvalStatus is required")
        @Schema(description = "审核结果，仅支持 APPROVED / REJECTED / PENDING", example = "APPROVED")
        String approvalStatus,
        @Size(max = 1000, message = "reviewNote too long")
        @Schema(description = "审核备注", example = "资料齐全，认证通过")
        String reviewNote
) {
}
