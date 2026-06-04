package com.bishe.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建评论请求。
 */
@Schema(description = "创建评论请求")
public record CommunityCommentCreateRequest(
        @Schema(description = "评论正文", example = "这条经验很有帮助，谢谢分享。")
        @NotBlank(message = "content is required")
        @Size(max = 5000, message = "content too long")
        String content
) {
}
