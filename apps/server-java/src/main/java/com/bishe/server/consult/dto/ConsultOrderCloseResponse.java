package com.bishe.server.consult.dto;

/**
 * 关闭咨询订单响应。
 */
public record ConsultOrderCloseResponse(
        String orderNo,
        String status,
        Long closedAt
) {
}
