package com.bishe.server.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 创建文本面试会话响应。
 */
@Schema(description = "创建文本面试会话响应")
public record InterviewSessionCreateResponse(
        @Schema(description = "会话ID", example = "is_10001")
        String sessionId,
        @Schema(description = "会话模式", example = "INTERVIEW_VOICE")
        String mode,
        @Schema(description = "首个问题", example = "请介绍一个与你目标岗位最相关、且能体现量化结果的项目。")
        String firstQuestion,
        @Schema(description = "本次创建预扣积分", example = "25")
        int chargedPoints,
        @Schema(description = "预扣后剩余积分", example = "40")
        int pointsBalanceAfterReserve,
        @Schema(description = "本次会话预留配额单位", example = "5")
        int quotaUnitsReserved,
        @Schema(description = "安全轮次上限", example = "30")
        int replyRoundLimit,
        AiModerationPayload moderation
) {
}
