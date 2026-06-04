package com.bishe.server.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 导师破冰私信生成请求。
 */
@Schema(description = "AI 破冰私信生成请求")
public record IcebreakMessageRequest(
        @Schema(description = "导师用户 ID", example = "2001")
        @Min(value = 1, message = "mentorId invalid")
        Long mentorId,
        @Schema(description = "学生当前诉求", example = "希望获得后端实习准备建议")
        @NotBlank(message = "studentGoal required")
        @Size(max = 500, message = "studentGoal too long")
        String studentGoal
) {
}
