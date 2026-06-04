package com.bishe.server.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 社区帖子 AI 预回答结果。
 */
@Schema(description = "社区 AI 预回答响应")
public record CommunityPreAnswerResponse(
        @Schema(description = "AI 草稿评论", example = "可以先从 JVM、数据库和项目量化结果三部分准备。")
        String draftComment,
        @Schema(description = "草稿标记", example = "AI_GENERATED")
        String tag,
        AiModerationPayload moderation
) {
}
