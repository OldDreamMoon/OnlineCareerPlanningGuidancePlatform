package com.bishe.server.growth.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 签到总览：用于工作台日历、连续签到奖励和签到状态读取。
 */
@Schema(description = "签到总览")
public record GrowthCheckinOverviewResponse(
        @Schema(description = "今天是否已经签到", example = "false")
        boolean signedInToday,
        @Schema(description = "成长计划第 N 天，按学生账号创建日累计，含创建当日", example = "42")
        int growthJourneyDays,
        @Schema(description = "当前有效连续签到天数", example = "12")
        int currentStreak,
        @Schema(description = "最近一次签到日期", example = "2026-03-18")
        String latestCheckinDate,
        @Schema(description = "当前日历月份（YYYY-MM）", example = "2026-03")
        String currentMonth,
        @Schema(description = "当前月份总天数", example = "31")
        int daysInCurrentMonth,
        @Schema(description = "当前月份已签到的日期列表", example = "[7,8,9,10]")
        List<Integer> checkedInDays,
        @Schema(description = "基础签到积分", example = "10")
        int basePointsPerDay,
        @ArraySchema(schema = @Schema(implementation = RewardRuleItem.class))
        List<RewardRuleItem> rewardRules,
        NextRewardItem nextReward
) {
    @Schema(description = "连签奖励规则")
    public record RewardRuleItem(
            @Schema(description = "奖励编码", example = "CHECKIN_STREAK_7")
            String rewardCode,
            @Schema(description = "奖励标题", example = "七日连签奖励")
            String title,
            @Schema(description = "奖励说明", example = "连续签到 7 天可额外获得 14 积分。")
            String description,
            @Schema(description = "触发所需连续签到天数", example = "7")
            int streakDays,
            @Schema(description = "奖励额外积分", example = "14")
            int bonusPoints
    ) {
    }

    @Schema(description = "下一档连签奖励")
    public record NextRewardItem(
            @Schema(description = "奖励编码", example = "CHECKIN_STREAK_14")
            String rewardCode,
            @Schema(description = "奖励标题", example = "十四日连签奖励")
            String title,
            @Schema(description = "奖励说明", example = "连续签到 14 天可额外获得 30 积分。")
            String description,
            @Schema(description = "目标连续签到天数", example = "14")
            int streakDays,
            @Schema(description = "奖励额外积分", example = "30")
            int bonusPoints,
            @Schema(description = "距离下一档奖励还差多少天", example = "2")
            int remainingDays
    ) {
    }
}
