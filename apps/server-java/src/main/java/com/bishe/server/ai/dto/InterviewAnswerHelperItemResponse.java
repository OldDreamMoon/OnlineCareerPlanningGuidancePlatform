package com.bishe.server.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 面试回答辅助诊断项。
 */
@Schema(description = "面试回答辅助诊断项")
public record InterviewAnswerHelperItemResponse(
        @Schema(description = "诊断项标识", example = "STAR")
        String key,
        @Schema(description = "当前等级", example = "WARN")
        String level,
        @Schema(description = "本项点评摘要", example = "背景和动作已经出现，但结果还不够明确。")
        String summary,
        @Schema(description = "下一步建议", example = "补一句结果变化或量化对比，让这一段更完整。")
        String nextAction
) {
}
