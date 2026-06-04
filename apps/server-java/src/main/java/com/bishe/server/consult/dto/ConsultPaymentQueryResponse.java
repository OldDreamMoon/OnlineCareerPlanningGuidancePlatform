package com.bishe.server.consult.dto;

/**
 * 学生侧查询支付状态响应。
 */
public record ConsultPaymentQueryResponse(
        String orderNo,
        String localStatus,
        String paymentMode,
        String providerTradeNo,
        String tradeStatus,
        String gatewayCode,
        String gatewayMessage,
        boolean syncedToPaid,
        Long paidAt,
        Long queriedAt
) {
}
