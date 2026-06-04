package com.bishe.server.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 导师破冰私信生成结果。
 */
@Schema(description = "AI 破冰私信生成响应")
public record IcebreakMessageResponse(
        @Schema(description = "私信草稿", example = "老师您好，我正在准备后端实习，想请教您简历和项目表达上的建议。")
        String messageDraft,
        AiModerationPayload moderation
) {
}
