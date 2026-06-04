package com.bishe.server.governance;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 后台举报处理历史。
 */
@Schema(description = "后台举报处理历史")
public record AdminReportActionHistoryResponse(
        @Schema(description = "处理记录列表")
        List<ActionItem> records
) {

    /**
     * 举报处理记录项。
     */
    @Schema(description = "举报处理记录项")
    public record ActionItem(
            Long actionId,
            Long operatorUserId,
            String operatorDisplayName,
            String decision,
            String action,
            String comment,
            Long createdAt
    ) {
    }
}
