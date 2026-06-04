package com.bishe.server.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 学生资料中心验证码校验请求。
 */
@Schema(description = "学生资料中心验证码校验请求")
public record StudentProfileSecurityVerifyCodeRequest(
        @Schema(description = "6 位数字验证码", example = "123456")
        @NotBlank(message = "code is required")
        @Pattern(regexp = "^\\d{6}$", message = "code must be 6 digits")
        String code
) {
}
