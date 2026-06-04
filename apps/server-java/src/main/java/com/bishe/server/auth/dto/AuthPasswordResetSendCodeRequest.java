package com.bishe.server.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 忘记密码发送验证码请求。
 */
public record AuthPasswordResetSendCodeRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email format is invalid")
        String email,
        @Size(max = 2048, message = "captchaVerificationToken too long")
        String captchaVerificationToken
) {
}
