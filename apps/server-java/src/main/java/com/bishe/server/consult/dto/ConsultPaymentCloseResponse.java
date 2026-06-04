package com.bishe.server.consult.dto;

/**
 * 学生侧关闭支付单响应。
 */
public record ConsultPaymentCloseResponse(
        String orderNo,
        String localStatus,
        String paymentMode,
        String providerTradeNo,
        String gatewayCode,
        String gatewayMessage,
        boolean closed,
        Long closedAt
) {
}
