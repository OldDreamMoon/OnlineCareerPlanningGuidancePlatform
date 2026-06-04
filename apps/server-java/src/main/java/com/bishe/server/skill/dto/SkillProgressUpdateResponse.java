package com.bishe.server.skill.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 技能进度更新响应。
 */
@Schema(description = "技能进度更新结果")
public record SkillProgressUpdateResponse(
        @Schema(description = "节点编码", example = "programming_language_foundations")
        String nodeCode,
        @Schema(description = "更新后的状态", example = "MASTERED")
        String status,
        @Schema(description = "是否触发画像刷新", example = "true")
        boolean portraitRefreshTriggered
) {
}
