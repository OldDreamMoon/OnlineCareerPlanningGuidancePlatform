package com.bishe.server.governance;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新增敏感词请求。
 */
@Schema(description = "新增敏感词请求")
public record SensitiveTermCreateRequest(
        @Schema(description = "敏感词文本", example = "代写")
        @NotBlank(message = "term is required")
        @Size(max = 200, message = "term too long")
        String term,
        @Schema(description = "敏感词类型", example = "POLITICS")
        @NotBlank(message = "termType is required")
        String termType,
        @Schema(description = "风险等级", example = "HIGH")
        @NotBlank(message = "riskLevel is required")
        String riskLevel,
        @Schema(description = "处置动作", example = "BLOCK")
        @NotBlank(message = "action is required")
        String action,
        @Schema(description = "适用范围", example = "COMMUNITY_POST")
        @NotBlank(message = "sourceScope is required")
        String sourceScope,
        @Schema(description = "是否白名单", example = "false")
        boolean whitelist,
        @Schema(description = "是否启用", example = "true")
        boolean enabled
) {
}
