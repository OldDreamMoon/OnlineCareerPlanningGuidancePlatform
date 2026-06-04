package com.bishe.server.governance;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 后台举报中心列表。
 */
@Schema(description = "后台举报中心列表")
public record AdminContentReportListResponse(
        List<ReportItem> records,
        long total,
        int page,
        int size
) {

    /**
     * 后台举报项。
     */
    @Schema(description = "后台举报项")
    public record ReportItem(
            Long reportId,
            String targetType,
            String targetId,
            String contentPostId,
            String contentTitle,
            String contentBody,
            String reasonCode,
            String status,
            String latestAction,
            long reportCount,
            Long createdAt,
            Long updatedAt
    ) {
    }
}
