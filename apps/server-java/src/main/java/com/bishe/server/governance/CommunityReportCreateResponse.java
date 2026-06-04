package com.bishe.server.governance;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 举报提交结果。
 */
@Schema(description = "举报提交结果")
public record CommunityReportCreateResponse(
        @Schema(description = "举报 ID", example = "5001")
        Long reportId,
        @Schema(description = "举报状态", example = "PENDING")
        String status
) {
}
