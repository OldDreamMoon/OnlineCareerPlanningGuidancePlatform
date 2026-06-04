package com.bishe.server.mentor.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 导师提现申请创建请求。
 */
public record MentorWithdrawalCreateRequest(
        @NotNull(message = "amountFen required")
        @Min(value = 1, message = "amountFen invalid")
        Integer amountFen,
        @Size(max = 500, message = "note too long")
        String note
) {
}
