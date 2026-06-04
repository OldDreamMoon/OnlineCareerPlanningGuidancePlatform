package com.bishe.server.consult.dto;

/**
 * 管理员查询支付宝沙箱交易结果响应。
 */
public record AdminConsultPaymentQueryResponse(
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
