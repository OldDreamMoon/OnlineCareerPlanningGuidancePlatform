package com.bishe.server.mentor.dto;

import java.util.List;

/**
 * 导师提现记录列表响应。
 */
public record MentorWithdrawalListResponse(
        List<MentorWithdrawalRecordResponse> records
) {
}
