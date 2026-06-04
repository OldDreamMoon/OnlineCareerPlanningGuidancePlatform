package com.bishe.server.skill.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员更新技能节点请求。
 */
public record AdminSkillNodeUpdateRequest(
        @NotBlank(message = "label required")
        @Size(max = 100, message = "label too long")
        String label,
        @Size(max = 500, message = "description too long")
        String description,
        Integer sortOrder
) {
}
