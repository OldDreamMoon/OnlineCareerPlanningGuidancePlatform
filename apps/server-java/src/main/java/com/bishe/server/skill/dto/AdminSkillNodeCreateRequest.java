package com.bishe.server.skill.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员创建技能节点请求。
 */
public record AdminSkillNodeCreateRequest(
        @NotBlank(message = "nodeCode required")
        @Size(max = 100, message = "nodeCode too long")
        String nodeCode,
        @NotBlank(message = "label required")
        @Size(max = 100, message = "label too long")
        String label,
        @Size(max = 500, message = "description too long")
        String description,
        @Size(max = 100, message = "parentCode too long")
        String parentCode,
        Integer sortOrder
) {
}
