package com.bishe.server.consult.dto;

/**
 * 管理员查询支付宝沙箱退款状态响应。
 */
public record AdminConsultRefundQueryResponse(
        String orderNo,
        String paymentMode,
        String providerTradeNo,
        String refundRequestNo,
        String gatewayCode,
        String gatewayMessage,
        String refundStatus,
        int refundAmountFen,
        Long queriedAt
) {
}
