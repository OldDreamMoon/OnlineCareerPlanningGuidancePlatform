package com.bishe.server.certification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 认证提交响应。
 */
@Schema(description = "认证提交响应")
public record CertificationSubmissionResponse(
        @Schema(description = "提交 ID", example = "7001")
        Long submissionId,
        @Schema(description = "用户 ID", example = "2001")
        Long userId,
        @Schema(description = "角色", example = "MENTOR")
        String role,
        @Schema(description = "真实姓名", example = "李老师")
        String realName,
        @Schema(description = "公司 / 机构名称", example = "字节跳动")
        String companyName,
        @Schema(description = "当前岗位 / Title", example = "高级前端工程师")
        String jobTitle,
        @Schema(description = "提交状态", example = "PENDING")
        String status,
        @Schema(description = "是否为当前有效提交", example = "true")
        Boolean current,
        @Schema(description = "审核备注", example = "请补充更清晰的在职证明")
        String reviewNote,
        @Schema(description = "上一版本提交 ID", example = "6999")
        Long previousSubmissionId,
        @Schema(description = "提交时间（ISO-8601 UTC）", example = "2026-03-17T12:00:00Z")
        Long submittedAt,
        @Schema(description = "审核时间（ISO-8601 UTC）", example = "2026-03-18T08:30:00Z")
        Long reviewedAt,
        List<CertificationAssetResponse> assets
) {
}
