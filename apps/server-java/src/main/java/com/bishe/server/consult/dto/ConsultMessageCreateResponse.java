package com.bishe.server.consult.dto;

/**
 * 发送咨询消息响应。
 */
public record ConsultMessageCreateResponse(
        long messageId,
        String orderNo,
        String senderRole,
        String orderStatus,
        Long createdAt
) {
}
