package com.bishe.server.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 文本面试追问响应。
 */
@Schema(description = "文本面试追问响应")
public record InterviewReplyResponse(
        @Schema(description = "AI 下一句追问或结束语", example = "你如何验证该优化结果？")
        String followUpQuestion,
        @Schema(description = "本轮点评", example = "量化结果比较清晰，下一轮可补充验证方式与技术取舍。")
        String coachFeedback,
        @Schema(description = "当前轮次建议分数", example = "78")
        int scoreHint,
        @Schema(description = "是否建议结束当前会话", example = "false")
        boolean shouldFinish,
        @Schema(description = "结束原因", example = "ENOUGH_EVIDENCE")
        String finishReason,
        @Schema(description = "当前会话状态", example = "ACTIVE")
        String sessionStatus,
        InterviewSummaryResponse summary,
        AiMetaPayload aiMeta,
        AiModerationPayload moderation
) {
}
