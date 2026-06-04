package com.bishe.server.governance;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 批量删除敏感词请求。
 */
@Schema(description = "批量删除敏感词请求")
public record SensitiveTermBatchDeleteRequest(
        @Schema(description = "敏感词 ID 列表", example = "[1,2,3]")
        @NotEmpty(message = "termIds is required")
        List<@NotNull Long> termIds
) {
}
