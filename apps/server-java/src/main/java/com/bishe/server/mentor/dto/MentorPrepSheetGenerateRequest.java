package com.bishe.server.mentor.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 咨询准备单智能草稿生成请求。
 */
public record MentorPrepSheetGenerateRequest(
        @Min(value = 1, message = "mentorUserId invalid")
        Long mentorUserId,
        @NotBlank(message = "scene required")
        @Size(max = 30, message = "scene too long")
        String scene,
        @Size(max = 100, message = "targetPosition too long")
        String targetPosition
) {
}
