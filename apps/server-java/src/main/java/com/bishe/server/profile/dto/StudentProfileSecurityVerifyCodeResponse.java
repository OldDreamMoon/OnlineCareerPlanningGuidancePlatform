package com.bishe.server.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 学生资料中心验证码校验响应。
 */
@Schema(description = "学生资料中心验证码校验响应")
public record StudentProfileSecurityVerifyCodeResponse(
        @Schema(description = "验证码是否校验成功", example = "true")
        boolean verified,
        @Schema(description = "后续操作凭证", example = "eyJhbGciOiJIUzI1NiJ9.eyJwdXJwb3NlIjoiUFJPRklMRV9QQVNTV09SRF9SRVNFVCJ9.signature")
        String verificationToken,
        @Schema(description = "凭证过期时间（UTC epoch 毫秒）", example = "1772446200000")
        Long expiresAt
) {
}
