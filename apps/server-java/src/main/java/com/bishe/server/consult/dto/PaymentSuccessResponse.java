package com.bishe.server.consult.dto;

/**
 * 支付成功响应。
 */
public record PaymentSuccessResponse(
        String orderNo,
        String status,
        String paymentMode,
        boolean alreadyProcessed,
        Long paidAt
) {
}
