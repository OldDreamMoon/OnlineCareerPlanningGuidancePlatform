package com.bishe.server.skill.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 技能进度更新请求。
 */
public record SkillProgressUpdateRequest(
        @NotBlank(message = "nodeId is required")
        String nodeId,
        @NotBlank(message = "targetStatus is required")
        String targetStatus
) {
}
