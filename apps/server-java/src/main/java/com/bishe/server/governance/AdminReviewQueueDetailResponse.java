package com.bishe.server.governance;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 后台待审项详情。
 */
@Schema(description = "后台待审项详情")
public record AdminReviewQueueDetailResponse(
        @Schema(description = "待审项 ID", example = "7001")
        Long itemId,
        @Schema(description = "来源类型", example = "COMMUNITY_COMMENT")
        String sourceType,
        @Schema(description = "目标类型", example = "COMMENT")
        String targetType,
        @Schema(description = "目标 ID", example = "9002")
        String targetId,
        @Schema(description = "风险等级", example = "MEDIUM")
        String riskLevel,
        @Schema(description = "原因码", example = "SENSITIVE_TERM")
        String reasonCode,
        @Schema(description = "预览内容", example = "这是一段预览")
        String preview,
        @Schema(description = "待审正文标题；评论场景为空", example = "灰测帖子标题")
        String contentTitle,
        @Schema(description = "待审正文全文", example = "这里是完整待审内容")
        String contentBody,
        @Schema(description = "关联帖子 ID；帖子场景为自身 ID，评论场景为父帖 ID", example = "8001")
        String postId,
        @Schema(description = "关联帖子标题", example = "父帖标题")
        String postTitle,
        @Schema(description = "关联帖子正文", example = "父帖正文内容")
        String postBody,
        @Schema(description = "作者用户 ID", example = "1001")
        Long authorUserId,
        @Schema(description = "作者显示名", example = "Alice")
        String authorDisplayName,
        @Schema(description = "作者角色", example = "STUDENT")
        String authorRole,
        @Schema(description = "创建时间（ISO-8601 UTC）", example = "2026-03-07T01:00:00Z")
        Long createdAt
) {
}
