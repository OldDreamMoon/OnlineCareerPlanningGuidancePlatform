package com.bishe.server.governance;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 敏感词列表响应。
 */
@Schema(description = "敏感词列表")
public record SensitiveTermsResponse(
        List<TermItem> records,
        long total,
        int page,
        int size,
        Summary summary
) {

    /**
     * 敏感词概览。
     */
    @Schema(description = "敏感词概览")
    public record Summary(
            long total,
            long enabled,
            long whitelist,
            long categories
    ) {
    }

    /**
     * 敏感词项。
     */
    @Schema(description = "敏感词项")
    public record TermItem(
            Long termId,
            String term,
            String termType,
            String riskLevel,
            String action,
            String sourceScope,
            boolean whitelist,
            boolean enabled,
            Long createdAt,
            Long updatedAt
    ) {
    }
}
