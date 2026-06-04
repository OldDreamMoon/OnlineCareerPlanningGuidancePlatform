package com.bishe.server.growth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 每日任务完成结果。
 */
@Schema(description = "每日任务完成结果")
public record DailyTaskCompleteResponse(
        @Schema(description = "本次获得积分", example = "10")
        int pointsEarned,
        @Schema(description = "最新积分余额", example = "130")
        int newBalance
) {
}
