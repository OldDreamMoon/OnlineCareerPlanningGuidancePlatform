package com.bishe.server.governance;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 后台举报详情响应。
 */
@Schema(description = "后台举报详情")
public record AdminContentReportDetailResponse(
        @Schema(description = "举报单号", example = "7001")
        Long reportId,
        @Schema(description = "举报人用户 ID", example = "1001")
        Long reporterUserId,
        @Schema(description = "举报人显示名", example = "张三")
        String reporterDisplayName,
        @Schema(description = "目标类型", example = "POST")
        String targetType,
        @Schema(description = "目标 ID", example = "9001")
        String targetId,
        @Schema(description = "关联帖子 ID；评论场景为父帖 ID", example = "8001")
        String contentPostId,
        @Schema(description = "被举报内容标题；评论场景展示父帖标题", example = "帖子标题")
        String contentTitle,
        @Schema(description = "被举报内容正文；评论场景展示评论正文", example = "这里是正文内容")
        String contentBody,
        @Schema(description = "举报原因码", example = "RISK_LINK")
        String reasonCode,
        @Schema(description = "举报补充说明", example = "帖子中包含来路不明的外部链接")
        String reportDetail,
        @Schema(description = "当前状态", example = "PENDING")
        String status,
        @Schema(description = "最新动作", example = "TAKE_DOWN")
        String latestAction,
        @Schema(description = "累计举报人次", example = "3")
        long reportCount,
        @Schema(description = "目标当前状态", example = "REVIEW")
        String targetStatus,
        @Schema(description = "目标当前风险等级", example = "HIGH")
        String targetRiskLevel,
        @Schema(description = "创建时间（ISO-8601 UTC）", example = "2026-03-08T15:00:00Z")
        Long createdAt,
        @Schema(description = "更新时间（ISO-8601 UTC）", example = "2026-03-08T15:10:00Z")
        Long updatedAt
) {
}
