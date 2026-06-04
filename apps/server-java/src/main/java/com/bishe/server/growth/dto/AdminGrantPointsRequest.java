package com.bishe.server.growth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 管理员临时补充学生测试积分请求。
 */
@Schema(description = "管理员临时补充学生测试积分请求")
public record AdminGrantPointsRequest(
        @NotNull(message = "userId is required")
        @Schema(description = "目标学生用户 ID", example = "1001")
        Long userId,
        @Min(value = 1, message = "points must be positive")
        @Schema(description = "补充积分数", example = "30")
        int points,
        @Size(max = 50, message = "reasonCode too long")
        @Schema(description = "账本原因码，默认 TEST_GRANT", example = "TEST_TOPUP")
        String reasonCode
) {
}
