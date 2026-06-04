package com.bishe.server.community.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 社区帖子列表响应。
 */
@Schema(description = "社区帖子列表")
public record CommunityPostListResponse(
        @ArraySchema(schema = @Schema(implementation = PostItem.class))
        List<PostItem> records,
        @Schema(description = "总条数", example = "1")
        long total,
        @Schema(description = "当前页码", example = "1")
        int page,
        @Schema(description = "当前页大小", example = "10")
        int size
) {

    /**
     * 帖子摘要项。
     */
    @Schema(description = "帖子摘要")
    public record PostItem(
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
            @Schema(description = "正文", example = "总结几条我最近面试中常被问到的问题。")
            String content,
            List<String> tags,
            @Schema(description = "评论数", example = "3")
            long commentCount,
            @Schema(description = "点赞数", example = "8")
            long likeCount,
            @Schema(description = "当前用户是否已点赞", example = "false")
            boolean likedByMe,
            @Schema(description = "帖子当前治理状态", example = "PASS")
            String moderationStatus,
            @Schema(description = "帖子当前风险等级", example = "LOW")
            String riskLevel,
            @Schema(description = "是否已有导师参与回复", example = "true")
            boolean hasMentorReply,
            @Schema(description = "当前用户是否为作者", example = "false")
            boolean authoredByMe,
            @Schema(description = "当前用户是否参与过该讨论", example = "false")
            boolean participatedByMe,
            @Schema(description = "创建时间（ISO-8601 UTC）", example = "2026-03-05T09:12:00Z")
            Long createdAt,
            @Schema(description = "更新时间（ISO-8601 UTC）", example = "2026-03-05T09:30:00Z")
            Long updatedAt
    ) {
    }
}
