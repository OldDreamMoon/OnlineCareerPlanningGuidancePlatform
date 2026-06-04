package com.bishe.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 社区内容审查结果，占位实现先返回 PASS。
 */
@Schema(description = "内容审查结果")
public record ModerationPayload(
        @Schema(description = "审查来源类型", example = "COMMUNITY_POST")
        String sourceType,
        @Schema(description = "风险等级", example = "LOW")
        String riskLevel,
        @Schema(description = "处置动作", example = "PASS")
        String action,
        @Schema(description = "原因编码", example = "RULE_CLEAR")
        String reasonCode
) {
}
