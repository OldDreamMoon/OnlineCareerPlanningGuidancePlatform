package com.bishe.server.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 更新学生个人资料响应。
 */
@Schema(description = "更新学生资料结果")
public record StudentProfileUpdateResponse(
        @Schema(description = "资料是否更新成功", example = "true")
        boolean updated,
        @Schema(description = "是否已触发画像刷新", example = "true")
        boolean portraitRefreshTriggered
) {
}
