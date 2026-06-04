package com.bishe.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 更新帖子解决状态结果。
 */
@Schema(description = "帖子解决状态更新结果")
public record CommunityPostStatusUpdateResponse(
        @Schema(description = "帖子 ID", example = "9001")
        Long postId,
        @Schema(description = "帖子解决状态", example = "RESOLVED")
        String resolvedStatus,
        @Schema(description = "更新时间（ISO-8601 UTC）", example = "2026-03-25T10:00:00Z")
        Long updatedAt
) {
}
