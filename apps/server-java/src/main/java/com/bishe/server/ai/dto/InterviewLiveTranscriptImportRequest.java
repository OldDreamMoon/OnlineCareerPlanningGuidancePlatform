package com.bishe.server.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 导入 Live 实时面试 transcript 请求。
 */
@Schema(description = "导入 Live 实时面试 transcript 请求")
public record InterviewLiveTranscriptImportRequest(
        @Schema(description = "按发生顺序排列的实时对话消息")
        @Size(min = 1, max = 240, message = "messages size invalid")
        List<@Valid MessageItem> messages
) {

        @Schema(description = "单条实时对话消息")
        public record MessageItem(
                @Schema(description = "消息角色，仅支持 USER / ASSISTANT", example = "ASSISTANT")
                @NotBlank(message = "role must not be blank")
                @Size(max = 20, message = "role too long")
                String role,
                @Schema(description = "消息正文", example = "先请你用一分钟介绍一下这个项目。")
                @NotBlank(message = "text must not be blank")
                @Size(max = 8000, message = "text too long")
                String text
        ) {
        }
}
