package com.bishe.server.consult.dto;

/**
 * 管理员手工退款/售后响应。
 */
public record AdminConsultOrderRefundResponse(
        String orderNo,
        String status,
        Long processedAt,
        boolean slotReleased,
        boolean reviewRemoved,
        String reason,
        boolean externalRefundTriggered,
        String externalProviderTradeNo,
        String externalRefundRequestNo,
        String externalRefundStatus
) {
}
