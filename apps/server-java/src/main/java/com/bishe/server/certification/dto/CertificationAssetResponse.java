package com.bishe.server.certification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 认证资料附件响应。
 */
@Schema(description = "认证资料附件响应")
public record CertificationAssetResponse(
        @Schema(description = "附件 ID", example = "9001")
        Long assetId,
        @Schema(description = "对象存储 bucket", example = "bishe-assets")
        String bucket,
        @Schema(description = "对象存储 key", example = "certification/mentor/20260317/20260317120000-register-user-proof.pdf")
        String objectKey,
        @Schema(description = "原始文件名", example = "mentor-proof.pdf")
        String originalFilename,
        @Schema(description = "文件类型", example = "application/pdf")
        String contentType,
        @Schema(description = "文件大小（字节）", example = "102400")
        Long sizeBytes,
        @Schema(description = "生命周期状态", example = "ACTIVE")
        String lifecycleStatus,
        @Schema(description = "删除原因", example = "REPLACED_BY_NEW_SUBMISSION")
        String deleteReason,
        @Schema(description = "上传时间（ISO-8601 UTC）", example = "2026-03-17T12:00:00Z")
        Long uploadedAt,
        @Schema(description = "删除时间（ISO-8601 UTC）", example = "2026-03-17T12:05:00Z")
        Long deletedAt
) {
}
