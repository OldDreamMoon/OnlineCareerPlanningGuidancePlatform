package com.bishe.server.consult.dto;

/**
 * 取消咨询订单响应。
 */
public record ConsultOrderCancelResponse(
        String orderNo,
        String status,
        Long canceledAt
) {
}
