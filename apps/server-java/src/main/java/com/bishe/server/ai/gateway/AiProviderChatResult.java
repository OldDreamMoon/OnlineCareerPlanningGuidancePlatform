package com.bishe.server.ai.gateway;

import java.math.BigDecimal;

/**
 * provider 文本请求原始结果。
 */
public record AiProviderChatResult(
        String taskType,
        String content,
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
