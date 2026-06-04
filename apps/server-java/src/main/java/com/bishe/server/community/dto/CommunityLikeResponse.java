package com.bishe.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 点赞/取消点赞响应。
 */
@Schema(description = "点赞状态结果")
public record CommunityLikeResponse(
        @Schema(description = "当前是否已点赞", example = "true")
        boolean liked,
        @Schema(description = "当前帖子点赞总数", example = "8")
        long likeCount
) {
}
