package com.bishe.server.community.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建社区帖子请求。
 */
@Schema(description = "创建社区帖子请求")
public record CommunityPostCreateRequest(
        @Schema(description = "帖子标题", example = "Java 后端面试技巧")
        @NotBlank(message = "title is required")
        @Size(max = 200, message = "title too long")
        String title,
        @Schema(description = "问题场景编码", example = "RESUME_REVIEW")
        @Size(max = 60, message = "scenarioCode too long")
        String scenarioCode,
        @Schema(description = "帖子正文", example = "总结几条我最近面试中常被问到的问题。")
        @NotBlank(message = "content is required")
        @Size(max = 10000, message = "content too long")
        String content,
        @ArraySchema(schema = @Schema(description = "帖子标签", example = "面经分享"))
        List<@Size(max = 50, message = "tag too long") String> tags
) {
}
