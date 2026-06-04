package com.bishe.server.consult.dto;

/**
 * 管理员审核售后申请响应。
 */
public record AdminConsultAfterSalesReviewResponse(
        long requestId,
        String orderNo,
        String status,
        String reviewNote,
        Long reviewedAt,
        AdminConsultOrderRefundResponse refund
) {
}
