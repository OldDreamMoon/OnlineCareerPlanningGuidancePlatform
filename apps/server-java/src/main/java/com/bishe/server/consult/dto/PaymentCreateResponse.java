package com.bishe.server.consult.dto;

/**
 * 支付发起响应。
 */
public record PaymentCreateResponse(
        String orderNo,
        String status,
        String paymentMode,
        String channel,
        String paymentUrl,
        String instruction
) {
}
