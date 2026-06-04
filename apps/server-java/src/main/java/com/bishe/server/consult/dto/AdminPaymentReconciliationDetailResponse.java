package com.bishe.server.consult.dto;

import java.util.List;

/**
 * 管理员支付对账详情响应。
 */
public record AdminPaymentReconciliationDetailResponse(
        String orderNo,
        long studentUserId,
        String studentDisplayName,
        long mentorUserId,
        String mentorDisplayName,
        int amountFen,
        String orderStatus,
        String questionText,
        String paymentMode,
        String paymentChannel,
        String latestPaymentStatus,
        String providerTradeNo,
        Long appointmentStartAt,
        Long appointmentEndAt,
        Long createdAt,
        Long paidAt,
        Long closedAt,
        String reconciliationStatus,
        List<String> issueTags,
        List<String> recommendedActions,
        ManualSummary latestManualHandling,
        List<PaymentRecordItem> paymentRecords
) {
    /**
     * 最近一次人工处理摘要。
     */
    public record ManualSummary(
            String action,
            String note,
            long operatorUserId,
            Long processedAt
    ) {
    }

    /**
     * 支付记录摘要。
     */
    public record PaymentRecordItem(
            String channel,
            String mode,
            String status,
            String providerTradeNo,
            int amountFen,
            String idempotencyKey,
            String rawCallback,
            Long createdAt
    ) {
    }
}
