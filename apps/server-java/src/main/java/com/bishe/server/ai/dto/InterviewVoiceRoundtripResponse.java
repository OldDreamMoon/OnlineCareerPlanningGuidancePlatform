package com.bishe.server.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 语音面试往返响应。
 */
@Schema(description = "语音面试往返响应")
public record InterviewVoiceRoundtripResponse(
        @Schema(description = "语音转写文本", example = "我负责订单中心性能治理，结合缓存和索引优化把接口延迟降低了 30%。")
        String transcript,
        @Schema(description = "语音对象标识", example = "upload://interview/is_10001/1710000000000-answer.webm")
        String audioObjectKey,
        AiMetaPayload transcriptMeta,
        @Schema(description = "AI 下一句追问或结束语", example = "你如何验证该优化结果？")
        String followUpQuestion,
        @Schema(description = "本轮点评", example = "亮点是给出了量化结果，下一步建议再补技术权衡与验证方式。")
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
