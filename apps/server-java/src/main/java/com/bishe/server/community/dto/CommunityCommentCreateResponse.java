package com.bishe.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 创建评论响应。
 */
@Schema(description = "创建评论结果")
public record CommunityCommentCreateResponse(
        @Schema(description = "评论 ID", example = "7001")
        Long commentId,
        ModerationPayload moderation
) {
}
