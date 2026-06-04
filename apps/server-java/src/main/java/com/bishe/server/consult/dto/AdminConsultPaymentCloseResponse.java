package com.bishe.server.consult.dto;

/**
 * 管理员关闭支付宝沙箱交易响应。
 */
public record AdminConsultPaymentCloseResponse(
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
