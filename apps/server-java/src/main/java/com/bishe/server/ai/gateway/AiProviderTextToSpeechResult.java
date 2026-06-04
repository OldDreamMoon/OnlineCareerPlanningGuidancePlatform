package com.bishe.server.ai.gateway;

import java.math.BigDecimal;

/**
 * provider 文本转语音原始结果。
 */
public record AiProviderTextToSpeechResult(
        String taskType,
        String mimeType,
        String audioBase64,
        String voiceName,
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
