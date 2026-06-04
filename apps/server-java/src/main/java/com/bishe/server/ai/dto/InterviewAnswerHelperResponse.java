package com.bishe.server.ai.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 面试回答辅助诊断响应。
 */
@Schema(description = "面试回答辅助诊断响应")
public record InterviewAnswerHelperResponse(
        @Schema(description = "整体等级", example = "WARN")
        String overallLevel,
        @Schema(description = "整体摘要", example = "这一轮已经有主线，但量化结果和收口还可以更明确。")
        String overallSummary,
        @ArraySchema(schema = @Schema(implementation = InterviewAnswerHelperItemResponse.class))
        List<InterviewAnswerHelperItemResponse> items,
        @ArraySchema(schema = @Schema(example = "优先补一组前后对比数据，再补一句验证方式。"))
        List<String> details,
        AiMetaPayload aiMeta,
        AiModerationPayload moderation
) {
}
