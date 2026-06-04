package com.bishe.server.community.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 社区帖子详情响应。
 */
@Schema(description = "社区帖子详情")
public record CommunityPostDetailResponse(
        @Schema(description = "帖子 ID", example = "9001")
        Long postId,
        @Schema(description = "作者用户 ID", example = "1002")
        Long authorUserId,
        @Schema(description = "作者显示名", example = "MentorBob")
        String authorDisplayName,
        @Schema(description = "作者公开真实姓名，仅在允许展示时返回", example = "顾航")
        String authorRealName,
        @Schema(description = "作者是否已开启真名展示", example = "true")
        boolean authorShowRealName,
        @Schema(description = "作者角色", example = "MENTOR")
        String authorRole,
        @Schema(description = "作者头像地址", example = "/api/v1/mentors/1002/avatar?v=1710000000000")
        String authorAvatarUrl,
        @Schema(description = "标题", example = "Java 后端面试技巧")
        String title,
        @Schema(description = "问题场景编码", example = "RESUME_REVIEW")
        String scenarioCode,
        @Schema(description = "帖子解决状态", example = "OPEN")
        String resolvedStatus,
        @Schema(description = "帖子当前治理状态", example = "PASS")
        String moderationStatus,
        @Schema(description = "帖子当前风险等级", example = "LOW")
        String riskLevel,
        @Schema(description = "正文", example = "总结几条我最近面试中常被问到的问题。")
        String content,
        List<String> tags,
        @Schema(description = "评论数", example = "3")
        long commentCount,
        @Schema(description = "点赞数", example = "8")
        long likeCount,
        @Schema(description = "当前用户是否已点赞", example = "true")
        boolean likedByMe,
        @ArraySchema(schema = @Schema(implementation = CommentItem.class))
        List<CommentItem> comments,
        @Schema(description = "创建时间（ISO-8601 UTC）", example = "2026-03-05T09:12:00Z")
        Long createdAt,
        @Schema(description = "更新时间（ISO-8601 UTC）", example = "2026-03-05T09:30:00Z")
        Long updatedAt
) {

    /**
     * 帖子评论项。
     */
    @Schema(description = "评论详情")
    public record CommentItem(
            @Schema(description = "评论 ID", example = "7001")
            Long commentId,
            @Schema(description = "评论用户 ID", example = "1001")
            Long userId,
            @Schema(description = "评论用户显示名", example = "Alice")
            String displayName,
            @Schema(description = "评论用户公开真实姓名，仅在允许展示时返回", example = "顾航")
            String realName,
            @Schema(description = "评论用户是否已开启真名展示", example = "true")
            boolean showRealName,
            @Schema(description = "评论用户角色", example = "STUDENT")
            String role,
            @Schema(description = "评论用户头像地址", example = "/api/v1/mentors/1002/avatar?v=1710000000000")
            String avatarUrl,
            @Schema(description = "评论内容", example = "这条经验很有帮助，谢谢分享。")
            String content,
            @Schema(description = "是否为 AI 评论", example = "false")
            boolean ai,
            @Schema(description = "评论创建时间（ISO-8601 UTC）", example = "2026-03-05T09:15:00Z")
            Long createdAt
    ) {
    }
}
