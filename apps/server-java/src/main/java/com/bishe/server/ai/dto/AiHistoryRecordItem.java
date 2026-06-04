package com.bishe.server.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * AI 使用历史单条记录。
 */
@Schema(description = "AI 使用历史单条记录")
public record AiHistoryRecordItem(
        @Schema(description = "历史记录 ID", example = "1")
        long id,
        @Schema(description = "任务类型", example = "RESUME")
        String taskType,
        @Schema(description = "记录摘要", example = "简历优化")
        String summary,
        @Schema(description = "本条记录对应积分消耗", example = "0")
        int pointsConsumed,
        @Schema(description = "会话 ID，仅文本面试时返回", example = "is_10001")
        String sessionId,
        @Schema(description = "会话状态，仅文本面试时返回", example = "COMPLETED")
        String status,
        @Schema(description = "创建时间（UTC epoch 毫秒）", example = "1772445600000")
        Long createdAt
) {
}
