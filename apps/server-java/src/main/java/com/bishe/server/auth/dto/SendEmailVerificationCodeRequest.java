package com.bishe.server.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发送注册邮箱验证码请求。
 */
public record SendEmailVerificationCodeRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email format is invalid")
        String email,
        @NotBlank(message = "captchaVerificationToken is required")
        @Size(max = 2048, message = "captchaVerificationToken too long")
        String captchaVerificationToken
) {
}
