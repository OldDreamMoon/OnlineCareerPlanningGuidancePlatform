package com.bishe.server.governance;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 批量更新敏感词启停状态请求。
 */
@Schema(description = "批量更新敏感词启停状态请求")
public record SensitiveTermBatchUpdateRequest(
        @Schema(description = "敏感词 ID 列表", example = "[1,2,3]")
        @NotEmpty(message = "termIds is required")
        List<@NotNull Long> termIds,
        @Schema(description = "是否启用", example = "true")
        boolean enabled
) {
}
