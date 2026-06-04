package com.bishe.server.growth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 每日签到结果。
 */
@Schema(description = "每日签到结果")
public record GrowthCheckinResponse(
        @Schema(description = "当前连续签到天数", example = "5")
        int streak,
        @Schema(description = "本次总获得积分（基础签到 + 连签奖励）", example = "16")
        int pointsEarned,
        @Schema(description = "签到日期", example = "2026-03-06")
        String checkinDate,
        @Schema(description = "基础签到积分", example = "10")
        int basePointsEarned,
        @Schema(description = "连签额外奖励积分", example = "6")
        int bonusPointsEarned,
        @Schema(description = "最新积分余额", example = "36")
        int newBalance,
        RewardPayload triggeredReward
) {
    /**
     * 本次触发的连签奖励。
     */
    @Schema(description = "本次触发的连签奖励")
    public record RewardPayload(
            @Schema(description = "奖励编码", example = "CHECKIN_STREAK_3")
            String rewardCode,
            @Schema(description = "奖励标题", example = "三日连签奖励")
            String title,
            @Schema(description = "奖励说明", example = "连续签到 3 天可额外获得 6 积分。")
            String description,
            @Schema(description = "触发所需连续签到天数", example = "3")
            int streakDays,
            @Schema(description = "奖励额外积分", example = "6")
            int bonusPoints
    ) {
    }
}
