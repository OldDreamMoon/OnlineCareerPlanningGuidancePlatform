package com.bishe.server.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 极验验证结果的服务端二次校验请求。
 */
public record GeetestVerifyRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email format is invalid")
        String email,
        @NotBlank(message = "lotNumber is required")
        String lotNumber,
        @NotBlank(message = "captchaOutput is required")
        String captchaOutput,
        @NotBlank(message = "passToken is required")
        String passToken,
        @NotBlank(message = "genTime is required")
        String genTime
) {
}
