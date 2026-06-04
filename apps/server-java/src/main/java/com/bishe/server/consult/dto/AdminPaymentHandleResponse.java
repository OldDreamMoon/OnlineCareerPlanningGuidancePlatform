package com.bishe.server.consult.dto;

/**
 * 管理员支付异常单人工处理响应。
 */
public record AdminPaymentHandleResponse(
        String orderNo,
        String action,
        String orderStatus,
        String reconciliationStatus,
        Long handledAt,
        String note
) {
}
