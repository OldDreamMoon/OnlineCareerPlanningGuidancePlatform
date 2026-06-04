package com.bishe.server.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 文本面试回复请求。
 */
@Schema(description = "文本面试回复请求")
public record InterviewReplyRequest(
        @Schema(description = "本轮回答", example = "我做过一个任务调度系统，将平均延迟降低了 30%。")
        @NotBlank(message = "answerText must not be blank")
        @Size(max = 4000, message = "answerText too long")
        String answerText
) {
}
