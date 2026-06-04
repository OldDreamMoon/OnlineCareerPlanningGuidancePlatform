package com.bishe.server.mentor.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 导师服务套餐编辑项。
 */
public record MentorServicePackageUpdateItem(
        @Size(max = 40, message = "packageName too long")
        String packageName,
        @Size(max = 60, message = "sceneCode too long")
        String sceneCode,
        @Size(max = 30, message = "sceneLabel too long")
        String sceneLabel,
        @Size(max = 20, message = "deliveryMode too long")
        String deliveryMode,
        @Min(value = 15, message = "durationMinutes too small")
        @Max(value = 180, message = "durationMinutes too large")
        Integer durationMinutes,
        Integer priceFen,
        @Size(max = 240, message = "description too long")
        String description,
        Boolean enabled
) {
}
