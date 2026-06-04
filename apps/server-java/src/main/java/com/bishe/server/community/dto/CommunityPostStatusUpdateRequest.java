package com.bishe.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 更新帖子解决状态请求。
 */
@Schema(description = "更新帖子解决状态请求")
public record CommunityPostStatusUpdateRequest(
        @Schema(description = "帖子解决状态", example = "RESOLVED")
        @NotBlank(message = "resolvedStatus is required")
        String resolvedStatus
) {
}
