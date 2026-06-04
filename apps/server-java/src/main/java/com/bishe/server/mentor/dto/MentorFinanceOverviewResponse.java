package com.bishe.server.mentor.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 导师财务中心聚合概览响应。
 */
public record MentorFinanceOverviewResponse(
        Metrics metrics,
        List<TrendPoint> trend,
        BillPage bills
) {

    /**
     * 财务指标概览。
     */
    public record Metrics(
            int totalRevenueFen,
            long totalRevenueOrderCount,
            int completedIncomeFen,
            long closedCount,
            int availableWithdrawalFen,
            int pendingWithdrawalFen,
            int completedWithdrawalFen,
            int refundedAmountFen,
            long refundedOrderCount,
            long answeredCount,
            BigDecimal avgRating
    ) {
    }

    /**
     * 趋势点。
     */
    public record TrendPoint(
            String key,
            String label,
            int paidFen,
            int refundedFen
    ) {
    }

    /**
     * 账单分页结果。
     */
    public record BillPage(
            List<BillItem> records,
            long total,
            int page,
            int size
    ) {
    }

    /**
     * 财务账单项。
     */
    public record BillItem(
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
            Long autoCancelAt
    ) {
    }
}
