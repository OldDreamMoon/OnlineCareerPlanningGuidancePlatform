package com.bishe.server.consult.dto;

/**
 * 咨询售后申请摘要。
 */
public record ConsultAfterSalesRequestSummary(
        long id,
        long requesterUserId,
        String requestType,
        String status,
        String reason,
        String reviewNote,
        Long reviewerUserId,
        boolean autoTriggered,
        Long createdAt,
        Long reviewedAt
) {
}
