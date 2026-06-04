package com.bishe.server.ai.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 面试总结报告响应。
 */
@Schema(description = "面试总结报告")
public record InterviewSummaryResponse(
        @Schema(description = "总体得分", example = "78")
        int overallScore,
        @ArraySchema(schema = @Schema(example = "项目背景交代清晰"))
        List<String> strengths,
        @ArraySchema(schema = @Schema(example = "量化结果偏少"))
        List<String> weaknesses,
        @ArraySchema(schema = @Schema(example = "多补充数据和结果验证方式"))
        List<String> suggestions,
        AiMetaPayload aiMeta,
        AiModerationPayload moderation
) {
}
