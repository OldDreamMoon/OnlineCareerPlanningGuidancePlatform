package com.bishe.server.consult.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 学生发起咨询售后申请请求。
 */
public record ConsultAfterSalesRequestCreateRequest(
        @NotBlank(message = "reason required")
        @Size(max = 1000, message = "reason too long")
        String reason
) {
}
