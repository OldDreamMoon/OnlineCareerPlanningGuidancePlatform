package com.bishe.server.growth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 管理员临时补充学生测试积分响应。
 */
@Schema(description = "管理员临时补充学生测试积分响应")
public record AdminGrantPointsResponse(
        @Schema(description = "目标学生用户 ID", example = "1001")
        long userId,
        @Schema(description = "本次变更积分", example = "30")
        int deltaPoints,
        @Schema(description = "变更后余额", example = "30")
        int newBalance,
        @Schema(description = "账本原因码", example = "TEST_TOPUP")
        String reasonCode
) {
}
