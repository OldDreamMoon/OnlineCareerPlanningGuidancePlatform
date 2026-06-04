package com.bishe.server.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 模拟面试入口可用状态。
 */
@Schema(description = "模拟面试入口可用状态")
public record InterviewEntryOptionsResponse(
        @Schema(description = "是否开放语音回答入口", example = "true")
        boolean voiceAnswerEnabled,
        @Schema(description = "是否开放 Live 实时语音入口", example = "true")
        boolean liveInterviewEnabled
) {
}
