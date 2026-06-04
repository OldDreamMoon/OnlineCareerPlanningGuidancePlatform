package com.bishe.server.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * AI 配额/调用联调请求。
 */
@Schema(description = "AI 联调请求")
public record AiPingRequest(
        @Schema(description = "AI 任务类型", example = "INTERVIEW_TEXT")
        String taskType,
        @Schema(description = "调用场景标识", example = "dashboard")
        @Size(max = 100, message = "scene too long")
        String scene
) {
}
