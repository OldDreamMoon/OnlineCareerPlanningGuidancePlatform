package com.bishe.server.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 学生资料中心换绑邮箱请求。
 */
@Schema(description = "学生资料中心换绑邮箱请求")
public record StudentProfileEmailChangeRequest(
        @Schema(description = "当前邮箱验证通过后拿到的操作凭证")
        @NotBlank(message = "currentEmailVerificationToken is required")
        @Size(max = 2048, message = "currentEmailVerificationToken too long")
        String currentEmailVerificationToken,
        @Schema(description = "新邮箱地址", example = "new-alice@example.com")
        @NotBlank(message = "newEmail is required")
        @Email(message = "newEmail format is invalid")
        @Size(max = 255, message = "newEmail too long")
        String newEmail,
        @Schema(description = "新邮箱收到的 6 位验证码", example = "123456")
        @NotBlank(message = "newEmailCode is required")
        @Pattern(regexp = "^\\d{6}$", message = "newEmailCode must be 6 digits")
        String newEmailCode
) {
}
