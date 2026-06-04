package com.bishe.server.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 学生资料中心发送验证码响应。
 */
@Schema(description = "学生资料中心发送验证码响应")
public record StudentProfileSecuritySendCodeResponse(
        @Schema(description = "是否发送成功", example = "true")
        boolean sent,
        @Schema(description = "发送阶段", example = "CURRENT")
        String stage,
        @Schema(description = "目标邮箱", example = "alice@example.com")
        String targetEmail,
        @Schema(description = "投递通道，EMAIL 或 MOCK", example = "EMAIL")
        String deliveryChannel,
        @Schema(description = "验证码过期时间（UTC epoch 毫秒）", example = "1772445600000")
        Long expiresAt,
        @Schema(description = "下次允许发送时间（UTC epoch 毫秒）", example = "1772445360000")
        Long nextSendAt,
        @Schema(description = "开发态验证码，仅在未配置邮件服务时返回", example = "123456")
        String debugCode
) {
}
