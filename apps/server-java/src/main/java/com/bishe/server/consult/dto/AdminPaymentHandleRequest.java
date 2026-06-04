package com.bishe.server.consult.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 管理员支付异常单人工处理请求。
 */
public record AdminPaymentHandleRequest(
        @NotBlank(message = "action required") String action,
        @NotBlank(message = "note required") String note
) {
}
