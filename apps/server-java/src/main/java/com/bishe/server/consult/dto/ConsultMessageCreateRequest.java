package com.bishe.server.consult.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发送咨询消息请求。
 */
public record ConsultMessageCreateRequest(
        @NotBlank(message = "messageText is required")
        @Size(max = 2000, message = "messageText too long")
        String messageText
) {
}
