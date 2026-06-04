package com.bishe.server.consult.dto;

/**
 * 学生发起咨询售后申请响应。
 */
public record ConsultAfterSalesRequestCreateResponse(
        long requestId,
        String orderNo,
        String requestType,
        String status,
        boolean autoTriggered,
        String reason,
        Long createdAt
) {
}
