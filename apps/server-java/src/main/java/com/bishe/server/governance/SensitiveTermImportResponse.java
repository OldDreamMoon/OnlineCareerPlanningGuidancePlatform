package com.bishe.server.governance;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 敏感词批量导入结果。
 */
@Schema(description = "敏感词批量导入结果")
public record SensitiveTermImportResponse(
        @Schema(description = "数据行总数", example = "20")
        int totalRows,
        @Schema(description = "新增条数", example = "10")
        int createdCount,
        @Schema(description = "更新条数", example = "8")
        int updatedCount,
        @Schema(description = "跳过条数", example = "2")
        int skippedCount,
        @Schema(description = "错误信息列表")
        List<String> errors
) {
}
