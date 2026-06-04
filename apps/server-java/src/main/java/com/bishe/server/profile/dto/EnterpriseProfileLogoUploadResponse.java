package com.bishe.server.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 企业 Logo 上传响应。
 */
@Schema(description = "企业 Logo 上传响应")
public record EnterpriseProfileLogoUploadResponse(
        @Schema(description = "是否上传成功", example = "true")
        boolean uploaded,
        @Schema(description = "是否已配置 Logo", example = "true")
        boolean logoConfigured,
        @Schema(description = "压缩后 Logo 内容类型", example = "image/jpeg")
        String contentType,
        @Schema(description = "压缩后 Logo 大小（字节）", example = "182340")
        long sizeBytes,
        @Schema(description = "Logo 最近更新时间（UTC epoch 毫秒）", example = "1772445600000")
        Long updatedAt,
        @Schema(description = "企业 Logo 公开地址", example = "/api/v1/profiles/enterprises/2001/logo?v=1772445600000")
        String logoUrl
) {
}
