package com.bishe.server.mentor.dto;

/**
 * 导师提现记录响应。
 */
public record MentorWithdrawalRecordResponse(
        long id,
        int amountFen,
        String status,
        Long createdAt,
        Long updatedAt,
        String note
) {
}
