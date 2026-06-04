package com.bishe.server.governance;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 批量敏感词操作响应。
 */
@Schema(description = "批量敏感词操作响应")
public record SensitiveTermBatchOperationResponse(
        @Schema(description = "实际影响条数", example = "3")
        int affectedCount
) {
}
