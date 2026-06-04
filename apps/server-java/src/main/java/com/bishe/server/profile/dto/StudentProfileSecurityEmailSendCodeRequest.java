package com.bishe.server.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 学生资料中心发送邮箱验证码请求。
 */
@Schema(description = "学生资料中心发送邮箱验证码请求")
public record StudentProfileSecurityEmailSendCodeRequest(
        @Schema(description = "发送阶段：CURRENT 表示验证当前邮箱，NEW 表示给新邮箱发送验证码", example = "CURRENT")
        @NotBlank(message = "stage is required")
        @Size(max = 20, message = "stage too long")
        String stage,
        @Schema(description = "新邮箱地址，仅 NEW 阶段必填", example = "new-alice@example.com")
        @Email(message = "newEmail format is invalid")
        @Size(max = 255, message = "newEmail too long")
        String newEmail
) {
}
