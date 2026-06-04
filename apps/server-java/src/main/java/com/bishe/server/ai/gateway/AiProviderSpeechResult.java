package com.bishe.server.ai.gateway;

import java.math.BigDecimal;

/**
 * provider 音频转写原始结果。
 */
public record AiProviderSpeechResult(
        String taskType,
        String transcript,
        String provider,
        String model,
        long latencyMs,
        int requestTokens,
        int responseTokens,
        int totalTokens,
        int thoughtsTokens,
        String reasoningEffort,
        Integer thinkingBudget,
        String thinkingLevel,
        BigDecimal estimatedCost
) {
}
