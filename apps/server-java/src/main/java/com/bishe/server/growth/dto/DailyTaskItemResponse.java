package com.bishe.server.growth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 每日任务项。
 */
@Schema(description = "每日任务项")
public record DailyTaskItemResponse(
        @Schema(description = "任务 ID", example = "1")
        long taskId,
        @Schema(description = "任务编码", example = "TASK_RESUME_OPTIMIZE")
        String taskCode,
        @Schema(description = "任务标题", example = "完成一次简历优化")
        String title,
        @Schema(description = "任务说明", example = "整理并优化一版简历内容")
        String description,
        @Schema(description = "任务奖励积分", example = "10")
        int points,
        @Schema(description = "是否已完成", example = "false")
        boolean completed
) {
}
