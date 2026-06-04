package com.bishe.server.governance;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 后台待审处置请求。
 */
@Schema(description = "后台待审处置请求")
public record AdminReviewDecisionRequest(
        @Schema(description = "人工审核结论", example = "APPROVE")
        @NotBlank(message = "decision is required")
        String decision,
        @Schema(description = "审核备注", example = "人工审核通过")
        @Size(max = 1000, message = "comment too long")
        String comment
) {
}
