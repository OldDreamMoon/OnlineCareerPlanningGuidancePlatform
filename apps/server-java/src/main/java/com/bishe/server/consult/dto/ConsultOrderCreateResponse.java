package com.bishe.server.consult.dto;

/**
 * 创建咨询订单响应。
 */
public record ConsultOrderCreateResponse(
        String orderNo,
        int amountFen,
        String status,
        Long appointmentStartAt,
        Long appointmentEndAt,
        String sceneCode,
        int currentAttachmentCount
) {
}
