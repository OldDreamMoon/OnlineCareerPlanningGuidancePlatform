package com.bishe.server.consult.dto;

import java.util.List;

/**
 * 导师订单中心工作台聚合响应。
 */
public record ConsultMentorWorkbenchResponse(
        Summary summary,
        List<OrderItem> records,
        long total,
        int page,
        int size,
        List<String> availablePaymentModes
) {

    /**
     * 顶部摘要卡片。
     */
    public record Summary(
            long pendingReplyCount,
            long expiringSoonCount,
            long waitingConfirmationCount,
            long afterSalesImpactCount
    ) {
    }

    /**
     * 导师订单中心列表项。
     */
    public record OrderItem(
            String orderNo,
            long counterpartUserId,
            String counterpartDisplayName,
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
            boolean afterSalesImpact,
            boolean pendingAfterSales,
            String latestAfterSalesStatus
    ) {
    }
}
