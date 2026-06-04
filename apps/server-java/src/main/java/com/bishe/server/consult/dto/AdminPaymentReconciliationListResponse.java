package com.bishe.server.consult.dto;

import java.util.List;

/**
 * 管理员支付对账列表响应。
 */
public record AdminPaymentReconciliationListResponse(
        List<OrderItem> records,
        long total,
        int page,
        int size
) {
    /**
     * 对账列表项。
     */
    public record OrderItem(
            String orderNo,
            long studentUserId,
            String studentDisplayName,
            long mentorUserId,
            String mentorDisplayName,
            int amountFen,
            String orderStatus,
            String paymentMode,
            String paymentChannel,
            String latestPaymentStatus,
            String providerTradeNo,
            String reconciliationStatus,
            List<String> issueTags,
            String latestManualAction,
            String latestManualNote,
            Long latestManualHandledAt,
            Long createdAt,
            Long paidAt,
            Long closedAt,
            Long latestPaymentCreatedAt
    ) {
    }
}
