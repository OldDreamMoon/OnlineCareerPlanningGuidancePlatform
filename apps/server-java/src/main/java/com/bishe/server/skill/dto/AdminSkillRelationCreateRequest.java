package com.bishe.server.skill.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员创建技能关联请求。
 */
public record AdminSkillRelationCreateRequest(
        @NotBlank(message = "sourceNodeCode required")
        @Size(max = 100, message = "sourceNodeCode too long")
        String sourceNodeCode,
        @NotBlank(message = "targetNodeCode required")
        @Size(max = 100, message = "targetNodeCode too long")
        String targetNodeCode,
        @NotBlank(message = "relationType required")
        @Size(max = 30, message = "relationType too long")
        String relationType,
        @NotBlank(message = "label required")
        @Size(max = 100, message = "label too long")
        String label,
        Integer sortOrder
) {
}
