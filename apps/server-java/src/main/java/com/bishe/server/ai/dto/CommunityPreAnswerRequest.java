package com.bishe.server.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 社区帖子 AI 预回答请求。
 */
@Schema(description = "社区 AI 预回答请求")
public record CommunityPreAnswerRequest(
        @Schema(description = "帖子 ID", example = "9001")
        @Min(value = 1, message = "postId invalid")
        Long postId,
        @Schema(description = "帖子标题（可选，当前以后端帖子快照为准）", example = "How to prepare for Java backend interview?")
        @Size(max = 200, message = "title too long")
        String title,
        @Schema(description = "帖子正文（可选，当前以后端帖子快照为准）", example = "...")
        @Size(max = 5000, message = "content too long")
        String content
) {
}
