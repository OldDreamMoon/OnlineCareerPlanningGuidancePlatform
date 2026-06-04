package com.bishe.server.consult.dto;

import java.util.List;

/**
 * 管理员侧咨询订单列表响应。
 */
public record AdminConsultOrderListResponse(
        List<OrderItem> records,
        long total,
        int page,
        int size
) {

    /**
     * 列表项。
     */
    public record OrderItem(
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
            Long closedAt
    ) {
    }
}
