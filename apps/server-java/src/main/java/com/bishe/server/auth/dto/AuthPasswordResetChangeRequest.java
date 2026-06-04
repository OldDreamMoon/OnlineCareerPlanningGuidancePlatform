package com.bishe.server.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 忘记密码修改密码请求。
 */
public record AuthPasswordResetChangeRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email format is invalid")
        String email,
        @NotBlank(message = "passwordResetToken is required")
        @Size(max = 2048, message = "passwordResetToken too long")
        String passwordResetToken,
        @NotBlank(message = "newPassword is required")
        @Size(min = 8, max = 64, message = "newPassword length must be between 8 and 64")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "newPassword must include letters and numbers")
        String newPassword
) {
}
