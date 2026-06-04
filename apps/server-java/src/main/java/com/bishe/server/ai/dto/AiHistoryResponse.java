package com.bishe.server.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * AI 使用历史分页响应。
 */
@Schema(description = "AI 使用历史分页响应")
public record AiHistoryResponse(
        @Schema(description = "历史记录列表")
        List<AiHistoryRecordItem> records,
        @Schema(description = "总记录数", example = "15")
        long total,
        @Schema(description = "当前页码", example = "1")
        int page,
        @Schema(description = "分页大小", example = "10")
        int size
) {
}
