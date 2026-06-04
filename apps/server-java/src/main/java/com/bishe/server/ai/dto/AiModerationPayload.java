package com.bishe.server.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * AI 输出审查结果，占位实现先统一返回 PASS。
 */
@Schema(description = "AI 内容审查结果")
public record AiModerationPayload(
        @Schema(description = "审查来源类型", example = "AI_OUTPUT")
        String sourceType,
        @Schema(description = "风险等级", example = "LOW")
        String riskLevel,
        @Schema(description = "处置动作", example = "PASS")
        String action,
        @Schema(description = "原因编码", example = "RULE_CLEAR")
        String reasonCode
) {
}
