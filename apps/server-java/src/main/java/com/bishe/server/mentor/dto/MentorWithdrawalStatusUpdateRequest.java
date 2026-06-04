package com.bishe.server.mentor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 导师提现演示状态更新请求。
 */
public record MentorWithdrawalStatusUpdateRequest(
        @NotBlank(message = "status required")
        String status,
        @Size(max = 500, message = "note too long")
        String note
) {
}
