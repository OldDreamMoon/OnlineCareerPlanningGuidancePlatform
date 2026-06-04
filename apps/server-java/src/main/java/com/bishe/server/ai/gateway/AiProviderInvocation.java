package com.bishe.server.ai.gateway;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * provider 执行时配置快照。
 */
public record AiProviderInvocation(
        String taskType,
        String sceneCode,
        String routeCode,
        AiExecutionMode executionMode,
        String providerCode,
        String providerDisplayName,
        AiProviderType providerType,
        String baseUrl,
        String apiKey,
        String model,
        Duration timeout,
        int maxRetries,
        BigDecimal costPer1kInput,
        BigDecimal costPer1kOutput,
        double temperature,
        String systemPrompt,
        String promptTemplateName,
        Integer promptTemplateVersionNo,
        String promptTemplateFormat,
        String promptTemplateBundleJson,
        List<AiChatMessage> promptSeedMessages,
        AiThinkingConfig thinkingConfig,
        String providerExtraConfigJson,
        String routeExtraConfigJson,
        String routePolicyCode,
        String routePolicyUserTier,
        String routeStrategyType,
        int candidateWeight
) {
}
