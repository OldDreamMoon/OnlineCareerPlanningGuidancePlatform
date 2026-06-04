package com.bishe.server.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 学生头像上传响应。
 */
@Schema(description = "学生头像上传响应")
public record StudentProfileAvatarUploadResponse(
        @Schema(description = "是否上传成功", example = "true")
        boolean uploaded,
        @Schema(description = "压缩后头像内容类型", example = "image/jpeg")
        String contentType,
        @Schema(description = "压缩后头像大小（字节）", example = "182340")
        long sizeBytes,
        @Schema(description = "头像最近更新时间（UTC epoch 毫秒）", example = "1772445600000")
        Long updatedAt
) {
}
