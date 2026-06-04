package com.bishe.server.featureflag.api;

import com.bishe.server.featureflag.FeatureFlagService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员更新功能开关请求。
 */
@Schema(description = "管理员更新功能开关请求")
public record AdminFeatureFlagUpsertRequest(
        @Schema(description = "开关键", example = "payment.mode")
        @NotBlank(message = "key required")
        @Size(max = 100, message = "key too long")
        String key,
        @Schema(description = "开关值", example = "SANDBOX")
        @NotBlank(message = "value required")
        @Size(max = 100, message = "value too long")
        String value
) {
    public FeatureFlagService.FeatureFlagUpdateCommand toCommand() {
        return new FeatureFlagService.FeatureFlagUpdateCommand(key, value);
    }
}
