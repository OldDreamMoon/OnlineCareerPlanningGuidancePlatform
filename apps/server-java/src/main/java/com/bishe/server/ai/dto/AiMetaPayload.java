package com.bishe.server.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * AI 调用元信息。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "AI 调用元信息")
public record AiMetaPayload(
        @Schema(description = "AI 任务类型", example = "RESUME")
        String taskType,
        @Schema(description = "供应商标识", example = "mock-provider")
        String provider,
        @Schema(description = "模型标识", example = "mock-economy-model")
        String model,
        @Schema(description = "响应耗时（毫秒）", example = "12")
        long latencyMs
) {
}
