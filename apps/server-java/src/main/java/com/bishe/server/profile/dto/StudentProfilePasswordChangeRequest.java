package com.bishe.server.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 学生资料中心修改密码请求。
 */
@Schema(description = "学生资料中心修改密码请求")
public record StudentProfilePasswordChangeRequest(
        @Schema(description = "验证码验证通过后拿到的密码重置凭证")
        @NotBlank(message = "passwordResetToken is required")
        @Size(max = 2048, message = "passwordResetToken too long")
        String passwordResetToken,
        @Schema(description = "新密码", example = "Passw0rd!")
        @NotBlank(message = "newPassword is required")
        @Size(min = 8, max = 64, message = "newPassword length must be between 8 and 64")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "newPassword must include letters and numbers")
        String newPassword
) {
}
