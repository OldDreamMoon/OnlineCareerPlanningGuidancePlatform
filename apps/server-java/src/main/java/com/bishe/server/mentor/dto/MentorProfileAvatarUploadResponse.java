package com.bishe.server.mentor.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 导师头像上传响应。
 */
@Schema(description = "导师头像上传响应")
public record MentorProfileAvatarUploadResponse(
        @Schema(description = "是否上传成功", example = "true")
        boolean uploaded,
        @Schema(description = "压缩后头像内容类型", example = "image/jpeg")
        String contentType,
        @Schema(description = "压缩后头像大小（字节）", example = "182340")
        long sizeBytes,
        @Schema(description = "头像最近更新时间（ISO-8601 UTC）", example = "2026-03-24T12:00:00Z")
        Long updatedAt,
        @Schema(description = "可直接用于前端展示的头像地址", example = "/api/v1/mentors/2001/avatar?v=2026-03-24T12%3A00%3A00Z")
        String avatarUrl
) {
}
