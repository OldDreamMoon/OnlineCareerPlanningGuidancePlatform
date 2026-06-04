package com.bishe.server.consult.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员手工退款/售后请求。
 */
public record AdminConsultOrderRefundRequest(
        @NotBlank(message = "reason required")
        @Size(max = 1000, message = "reason too long")
        String reason
) {
}
