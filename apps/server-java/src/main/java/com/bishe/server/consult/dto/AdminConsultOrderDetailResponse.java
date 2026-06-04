package com.bishe.server.consult.dto;

import java.util.List;

/**
 * 管理员侧咨询订单详情响应。
 */
public record AdminConsultOrderDetailResponse(
        String orderNo,
        long studentUserId,
        String studentDisplayName,
        long mentorUserId,
        String mentorDisplayName,
        int amountFen,
        String status,
        String questionText,
        String paymentMode,
        Long appointmentStartAt,
        Long appointmentEndAt,
        Long createdAt,
        Long paidAt,
        Long closedAt,
        Long autoCancelAt,
        Long mentorReplyDeadlineAt,
        PaymentSummary payment,
        ReviewSummary review,
        RefundSummary refund,
        List<ConsultAfterSalesRequestSummary> afterSalesRequests
) {

    /**
     * 最新支付记录摘要。
     */
    public record PaymentSummary(
            String channel,
            String mode,
            String status,
            String providerTradeNo,
            String idempotencyKey,
            Long recordedAt
    ) {
    }

    /**
     * 评价摘要。
     */
    public record ReviewSummary(
            int rating,
            String comment,
            Long createdAt
    ) {
    }

    /**
     * 售后/退款摘要。
     */
    public record RefundSummary(
            String reason,
            long operatorUserId,
            String previousStatus,
            boolean slotReleased,
            boolean reviewRemoved,
            Long processedAt
    ) {
    }
}
