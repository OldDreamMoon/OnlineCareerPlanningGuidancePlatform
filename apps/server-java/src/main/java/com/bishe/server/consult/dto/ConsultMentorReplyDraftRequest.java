package com.bishe.server.consult.dto;

import jakarta.validation.constraints.Size;

/**
 * 导师履约工作区 AI 回复草稿请求。
 */
public record ConsultMentorReplyDraftRequest(
        @Size(max = 2000, message = "currentDraft too long")
        String currentDraft,
        @Size(max = 300, message = "instruction too long")
        String instruction
) {
}
