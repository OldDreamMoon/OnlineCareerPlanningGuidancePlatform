package com.bishe.server.skill.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员创建技能资源请求。
 */
public record AdminSkillResourceCreateRequest(
        @NotBlank(message = "resourceCode required")
        @Size(max = 100, message = "resourceCode too long")
        String resourceCode,
        @NotBlank(message = "nodeCode required")
        @Size(max = 100, message = "nodeCode too long")
        String nodeCode,
        @NotBlank(message = "resourceType required")
        @Size(max = 20, message = "resourceType too long")
        String resourceType,
        @NotBlank(message = "title required")
        @Size(max = 200, message = "title too long")
        String title,
        @NotBlank(message = "sourceLabel required")
        @Size(max = 100, message = "sourceLabel too long")
        String sourceLabel,
        @NotBlank(message = "durationLabel required")
        @Size(max = 50, message = "durationLabel too long")
        String durationLabel,
        @NotBlank(message = "linkUrl required")
        @Size(max = 500, message = "linkUrl too long")
        String linkUrl,
        Integer sortOrder
) {
}
