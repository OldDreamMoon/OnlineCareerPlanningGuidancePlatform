package com.bishe.server.consult.dto;

import com.bishe.server.ai.dto.AiMetaPayload;
import com.bishe.server.ai.dto.AiModerationPayload;

/**
 * 导师履约工作区 AI 回复草稿响应。
 */
public record ConsultMentorReplyDraftResponse(
        String draftReply,
        String generationMode,
        String appliedInstruction,
        AiMetaPayload aiMeta,
        AiModerationPayload moderation
) {
}
