package com.bishe.server.mentor.dto;

/**
 * 导师服务套餐响应。
 */
public record MentorServicePackageResponse(
        long id,
        String packageName,
        String sceneCode,
        String sceneLabel,
        String deliveryMode,
        Integer durationMinutes,
        int priceFen,
        String description,
        boolean enabled,
        int sortNo
) {
}
