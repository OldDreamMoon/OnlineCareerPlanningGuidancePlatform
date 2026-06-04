package com.bishe.server.consult.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 管理员审核售后申请请求。
 */
public record AdminConsultAfterSalesReviewRequest(
        @NotNull(message = "approved required")
        Boolean approved,
        @Size(max = 1000, message = "reviewNote too long")
        String reviewNote
) {
}
