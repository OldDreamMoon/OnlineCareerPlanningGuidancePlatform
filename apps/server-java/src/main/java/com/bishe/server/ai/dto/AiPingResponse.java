package com.bishe.server.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * AI 联调结果。
 */
@Schema(description = "AI 联调结果")
public record AiPingResponse(
        @Schema(description = "服务标识", example = "server-java-ai-module")
        String service,
        @Schema(description = "模块名称", example = "ai/gateway")
        String module,
        @Schema(description = "调用时间（UTC epoch 毫秒）", example = "1772787600000")
        Long time,
        @Schema(description = "任务类型", example = "INTERVIEW_TEXT")
        String taskType,
        @Schema(description = "用户等级", example = "FREE")
        String tier,
        @Schema(description = "是否命中免费额度", example = "true")
        boolean freeCall,
        @Schema(description = "本次扣减积分", example = "0")
        int chargedPoints,
        @Schema(description = "扣减后的积分余额", example = "10")
        int pointsBalance,
        @Schema(description = "今日已使用次数", example = "2")
        int usedToday,
        @Schema(description = "供应商标识", example = "mock-provider")
        String provider,
        @Schema(description = "模型标识", example = "mock-economy-model")
        String model,
        @Schema(description = "回显场景", example = "dashboard")
        String scene
) {
}
