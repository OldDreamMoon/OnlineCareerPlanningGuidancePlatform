package com.bishe.server.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 更新学生隐私矩阵响应。
 */
@Schema(description = "更新学生隐私矩阵结果")
public record StudentProfilePrivacyUpdateResponse(
        @Schema(description = "设置是否更新成功", example = "true")
        boolean updated
) {
}
