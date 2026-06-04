package com.bishe.server.consult.dto;

import java.util.List;

/**
 * 管理员售后申请列表响应。
 */
public record AdminConsultAfterSalesRequestListResponse(
        List<RequestItem> records,
        long total,
        int page,
        int size
) {

    /**
     * 列表项。
     */
    public record RequestItem(
            long requestId,
            String orderNo,
            String orderStatus,
            int amountFen,
            long studentUserId,
            String studentDisplayName,
            long mentorUserId,
            String mentorDisplayName,
            String requestType,
            String status,
            String reason,
            String reviewNote,
            Long reviewerUserId,
            boolean autoTriggered,
            Long createdAt,
            Long reviewedAt
    ) {
    }
}
