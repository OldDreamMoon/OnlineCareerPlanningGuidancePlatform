package com.bishe.server.governance;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 提交举报请求。
 */
@Schema(description = "提交举报请求")
public record CommunityReportCreateRequest(
        @Schema(description = "目标类型", example = "POST")
        @NotBlank(message = "targetType is required")
        String targetType,
        @Schema(description = "目标 ID", example = "9001")
        @NotBlank(message = "targetId is required")
        String targetId,
        @Schema(description = "举报原因编码", example = "ABUSE")
        @NotBlank(message = "reasonCode is required")
        String reasonCode,
        @Schema(description = "补充说明", example = "包含明显不当内容")
        @Size(max = 1000, message = "detail too long")
        String detail
) {
}
