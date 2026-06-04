package com.bishe.server.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 管理员重置用户密码请求。
 */
public record AdminResetPasswordRequest(
        @NotBlank(message = "newPassword is required")
        @Size(min = 8, max = 64, message = "newPassword length must be between 8 and 64")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "newPassword must include letters and numbers")
        String newPassword
) {
}
