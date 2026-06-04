package com.bishe.server.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 简历结构完整度项。
 */
@Schema(description = "简历结构完整度项")
public record ResumeStructureItem(
        @Schema(description = "维度标签", example = "项目经历")
        String label,
        @Schema(description = "维度评分，范围 1-5", example = "4")
        int score,
        @Schema(description = "维度建议", example = "项目主线已较清晰，建议再补一条量化结果。")
        String tip
) {
}
