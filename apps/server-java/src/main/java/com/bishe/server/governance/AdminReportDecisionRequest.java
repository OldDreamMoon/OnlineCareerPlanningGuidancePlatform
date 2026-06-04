package com.bishe.server.governance;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 后台举报处置请求。
 */
@Schema(description = "后台举报处置请求")
public record AdminReportDecisionRequest(
        @Schema(description = "处置结论", example = "ACCEPTED")
        @NotBlank(message = "decision is required")
        String decision,
        @Schema(description = "执行动作", example = "TAKE_DOWN")
        @NotBlank(message = "action is required")
        String action,
        @Schema(description = "备注", example = "命中违规规则，执行下架")
        @Size(max = 1000, message = "comment too long")
        String comment
) {
}
