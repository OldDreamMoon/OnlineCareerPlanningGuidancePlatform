package com.bishe.server.governance;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 我的举报列表。
 */
@Schema(description = "我的举报列表")
public record CommunityReportListResponse(
        List<ReportItem> records,
        long total,
        int page,
        int size
) {

    /**
     * 举报记录项。
     */
    @Schema(description = "举报记录")
    public record ReportItem(
            Long reportId,
            String targetType,
            String targetId,
            String contentPostId,
            String contentTitle,
            String contentBody,
            String reasonCode,
            String detail,
            String status,
            String latestAction,
            Long createdAt,
            Long updatedAt
    ) {
    }
}
