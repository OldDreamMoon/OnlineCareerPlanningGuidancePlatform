package com.bishe.server.governance;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 内容治理策略快照。
 */
@Schema(description = "内容治理策略")
public record ModerationPoliciesResponse(
        @Schema(description = "AI 输入审查开关", example = "true")
        boolean aiInputEnabled,
        @Schema(description = "AI 输出审查开关", example = "true")
        boolean aiOutputEnabled,
        @Schema(description = "社区内容审查开关", example = "true")
        boolean communityStrictReviewEnabled,
        @Schema(description = "举报自动隐藏阈值", example = "3")
        int autoHideReportThreshold
) {
}
