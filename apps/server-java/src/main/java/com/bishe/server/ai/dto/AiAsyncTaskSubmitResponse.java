package com.bishe.server.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * AI 异步任务提交响应。
 */
@Schema(description = "AI 异步任务提交响应")
public record AiAsyncTaskSubmitResponse(
        @Schema(description = "任务 ID", example = "aitk_1234567890abcdef")
        String taskId,
        @Schema(description = "任务类型", example = "RESUME")
        String taskType,
        @Schema(description = "场景代码", example = "RESUME_OPTIMIZE")
        String sceneCode,
        @Schema(description = "执行模式", example = "ASYNC_JOB")
        String executionMode,
        @Schema(description = "当前状态", example = "PENDING")
        String status,
        @Schema(description = "创建时间（UTC epoch 毫秒）", example = "1774256400000")
        Long createdAt
) {
}
