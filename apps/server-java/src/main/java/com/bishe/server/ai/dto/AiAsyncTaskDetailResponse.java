package com.bishe.server.ai.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * AI 异步任务详情响应。
 */
@Schema(description = "AI 异步任务详情响应")
public record AiAsyncTaskDetailResponse(
        @Schema(description = "任务 ID", example = "aitk_1234567890abcdef")
        String taskId,
        @Schema(description = "任务类型", example = "RESUME")
        String taskType,
        @Schema(description = "场景代码", example = "RESUME_OPTIMIZE")
        String sceneCode,
        @Schema(description = "命中的路由代码", example = "SYSTEM_RESUME_OPTIMIZE")
        String routeCode,
        @Schema(description = "执行模式", example = "ASYNC_JOB")
        String executionMode,
        @Schema(description = "当前状态", example = "RUNNING")
        String status,
        @Schema(description = "是否已进入终态", example = "false")
        boolean terminal,
        @Schema(description = "当前重试次数", example = "1")
        int currentAttempt,
        @Schema(description = "最大尝试次数", example = "3")
        int maxAttempts,
        @Schema(description = "命中的 provider 代码", example = "SYSTEM_RESUME_GEMINI_NATIVE")
        String providerCode,
        @Schema(description = "命中的 provider 类型", example = "GEMINI_NATIVE")
        String providerType,
        @Schema(description = "实际模型名", example = "gemini-2.5-flash")
        String modelName,
        @Schema(description = "命中的提示词模板名", example = "RESUME_OPTIMIZE_CORE")
        String promptTemplateName,
        @Schema(description = "命中的提示词模板版本号", example = "1")
        Integer promptTemplateVersionNo,
        @Schema(description = "结果摘要")
        String resultSummary,
        @Schema(description = "结果原始载荷 JSON")
        JsonNode resultPayload,
        @Schema(description = "若结果已落历史，可直接跳转使用的记录 ID", example = "123")
        Long linkedRecordId,
        @Schema(description = "失败错误码", example = "AI-2201")
        String errorCode,
        @Schema(description = "失败错误信息")
        String errorMessage,
        @Schema(description = "下次重试时间（UTC epoch 毫秒）", example = "1774256410000")
        Long nextRunAt,
        @Schema(description = "入队时间（UTC epoch 毫秒）", example = "1774256400000")
        Long queuedAt,
        @Schema(description = "首次开始执行时间（UTC epoch 毫秒）", example = "1774256402000")
        Long startedAt,
        @Schema(description = "完成时间（UTC epoch 毫秒）", example = "1774256408000")
        Long finishedAt,
        @Schema(description = "创建时间（UTC epoch 毫秒）", example = "1774256400000")
        Long createdAt,
        @Schema(description = "更新时间（UTC epoch 毫秒）", example = "1774256408000")
        Long updatedAt
) {
}
