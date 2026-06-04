package com.bishe.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 创建帖子响应。
 */
@Schema(description = "创建帖子结果")
public record CommunityPostCreateResponse(
        @Schema(description = "帖子 ID", example = "9001")
        Long postId,
        ModerationPayload moderation,
        @Schema(description = "是否已自动生成 AI 一楼评论", example = "true")
        boolean aiFirstCommentCreated,
        @Schema(description = "自动生成的 AI 一楼评论 ID；未生成时为 null", example = "7001")
        Long aiFirstCommentId
) {
}
