package com.bishe.server.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 面试历史消息项。
 */
@Schema(description = "面试历史消息项")
public record InterviewMessageItem(
        @Schema(description = "发送方角色", example = "ASSISTANT")
        String role,
        @Schema(description = "消息内容")
        String text,
        @Schema(description = "该轮即时点评", example = "亮点是给出了量化结果，下一轮建议补充验证方式与技术取舍。")
        String coachFeedback,
        @Schema(description = "评分提示", example = "82")
        Integer scoreHint,
        @Schema(description = "关联音频对象标识", example = "upload://interview/is_10001/1710000000000-answer.webm")
        String audioObjectKey,
        @Schema(description = "创建时间（UTC epoch 毫秒）", example = "1772787600000")
        Long createdAt
) {
}
