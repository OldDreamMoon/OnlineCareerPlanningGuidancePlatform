package com.bishe.server.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 学生资料中心修改密码响应。
 */
@Schema(description = "学生资料中心修改密码结果")
public record StudentProfilePasswordChangeResponse(
        @Schema(description = "密码是否更新成功", example = "true")
        boolean updated
) {
}
