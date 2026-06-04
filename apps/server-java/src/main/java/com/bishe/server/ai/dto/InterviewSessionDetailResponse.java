package com.bishe.server.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 面试会话详情。
 */
@Schema(description = "面试会话详情")
public record InterviewSessionDetailResponse(
        @Schema(description = "会话ID", example = "is_10001")
        String sessionId,
        @Schema(description = "目标岗位", example = "Backend Engineer")
        String targetRole,
        @Schema(description = "会话模式", example = "INTERVIEW_VOICE")
        String mode,
        @Schema(description = "会话状态", example = "COMPLETED")
        String status,
        @Schema(description = "预扣积分", example = "25")
        int prepaidPoints,
        @Schema(description = "预留配额单位", example = "5")
        int reservedQuotaWeight,
        @Schema(description = "当前剩余积分", example = "80")
        int pointsBalance,
        @Schema(description = "安全轮次上限", example = "30")
        int replyRoundLimit,
        @Schema(description = "已使用轮次", example = "3")
        int replyRoundUsed,
        @Schema(description = "是否由 AI 主动结束", example = "true")
        boolean endedByAi,
        @Schema(description = "结束原因", example = "ENOUGH_EVIDENCE")
        String finishReason,
        @Schema(description = "创建时间（UTC epoch 毫秒）", example = "1772787600000")
        Long createdAt,
        @Schema(description = "总结生成时间（UTC epoch 毫秒）", example = "1772788200000")
        Long summaryGeneratedAt,
        @Schema(description = "固化的会话准备配置")
        InterviewSessionContextDetailResponse sessionContext,
        @Schema(description = "固化的简历上下文")
        InterviewResumeContextDetailResponse resumeContext,
        @Schema(description = "消息列表")
        List<InterviewMessageItem> messages,
        InterviewSummaryResponse summary
) {
}
