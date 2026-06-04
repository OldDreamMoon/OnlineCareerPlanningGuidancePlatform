package com.bishe.server.community.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 社区 7 日贡献榜响应。
 */
@Schema(description = "社区贡献榜响应")
public record CommunityLeaderboardResponse(
        @Schema(description = "时间窗口", example = "7d")
        String window,
        @Schema(description = "计分公式", example = "post*5 + comment*2 + like*1")
        String formula,
        @ArraySchema(schema = @Schema(description = "榜单记录"))
        List<LeaderboardItem> records,
        @Schema(description = "总记录数", example = "12")
        long total,
        @Schema(description = "当前页码", example = "1")
        int page,
        @Schema(description = "分页大小", example = "10")
        int size
) {

    /**
     * 单个榜单项。
     */
    @Schema(description = "榜单项")
    public record LeaderboardItem(
            @Schema(description = "排名", example = "1")
            int rank,
            @Schema(description = "学生用户 ID", example = "1001")
            long studentUserId,
            @Schema(description = "显示名称", example = "Alice")
            String displayName,
            @Schema(description = "贡献分", example = "39")
            long score,
            @Schema(description = "近 7 天发帖数", example = "3")
            int postCount,
            @Schema(description = "近 7 天评论数", example = "8")
            int commentCount,
            @Schema(description = "近 7 天获赞数", example = "8")
            int likeReceivedCount,
            @Schema(description = "最近活跃时间", example = "2026-03-05T09:12:00Z")
            Long latestActivityAt
    ) {
    }
}
