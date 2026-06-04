package com.bishe.server.ai.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * AI 剩余配额查询响应。
 */
@Schema(description = "AI 剩余配额")
public record AiQuotaRemainingResponse(
        @Schema(description = "当前用户等级", example = "FREE")
        String tier,
        @ArraySchema(schema = @Schema(implementation = QuotaItem.class))
        List<QuotaItem> quotas,
        @Schema(description = "当前积分余额", example = "120")
        int pointsBalance
) {
    @Schema(description = "单任务类型配额")
    public record QuotaItem(
            @Schema(description = "任务类型", example = "RESUME")
            String taskType,
            @Schema(description = "每日免费次数，-1 表示不限制", example = "3")
            int dailyFreeLimit,
            @Schema(description = "今日已使用次数", example = "1")
            int usedToday,
            @Schema(description = "剩余免费次数，-1 表示不限制", example = "2")
            int remaining
    ) {
    }
}
