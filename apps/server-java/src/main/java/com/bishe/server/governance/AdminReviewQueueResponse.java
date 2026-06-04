package com.bishe.server.governance;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 后台待审队列响应。
 */
@Schema(description = "后台待审队列")
public record AdminReviewQueueResponse(
        List<ReviewItem> records,
        long total,
        int page,
        int size
) {

    /**
     * 待审项。
     */
    @Schema(description = "待审项")
    public record ReviewItem(
            Long itemId,
            String sourceType,
            String targetType,
            String targetId,
            String riskLevel,
            String reasonCode,
            String preview,
            Long createdAt
    ) {
    }
}
