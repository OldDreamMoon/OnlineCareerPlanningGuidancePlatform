package com.bishe.server.consult.dto;

import java.util.List;

/**
 * 咨询订单列表响应。
 */
public record ConsultOrderListResponse(
        List<OrderItem> records,
        long total,
        int page,
        int size
) {

    /**
     * 订单列表项。
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
            Long autoCancelAt
    ) {
    }
}
