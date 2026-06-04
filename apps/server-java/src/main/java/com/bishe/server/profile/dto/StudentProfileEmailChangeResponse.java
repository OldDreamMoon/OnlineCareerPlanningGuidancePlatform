package com.bishe.server.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 学生资料中心换绑邮箱响应。
 */
@Schema(description = "学生资料中心换绑邮箱结果")
public record StudentProfileEmailChangeResponse(
        @Schema(description = "邮箱是否更新成功", example = "true")
        boolean updated,
        @Schema(description = "最新邮箱", example = "new-alice@example.com")
        String email
) {
}
