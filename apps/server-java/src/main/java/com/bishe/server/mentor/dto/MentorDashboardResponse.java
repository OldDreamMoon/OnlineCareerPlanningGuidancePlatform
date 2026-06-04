package com.bishe.server.mentor.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 导师工作台响应。
 */
public record MentorDashboardResponse(
        long pendingPaidCount,
        long answeredCount,
        long closedCount,
        int totalRevenueFen,
        int totalOrders,
        BigDecimal avgRating,
        List<RecentOrderItem> recentOrders
) {

    /**
     * 最近订单项。
     */
    public record RecentOrderItem(
            String orderNo,
            long studentUserId,
            String studentDisplayName,
            int amountFen,
            String status,
            String questionText,
            Long createdAt,
            Long paidAt,
            Long closedAt
    ) {
    }
}
