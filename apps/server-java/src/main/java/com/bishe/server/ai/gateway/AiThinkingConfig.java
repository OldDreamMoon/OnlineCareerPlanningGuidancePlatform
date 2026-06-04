package com.bishe.server.ai.gateway;

import java.util.Locale;

/**
 * 统一的思考量配置快照。
 */
public record AiThinkingConfig(
        AiReasoningEffort reasoningEffort,
        Integer thinkingBudget,
        String thinkingLevel,
        String source
) {

    public AiThinkingConfig {
        thinkingLevel = normalizeText(thinkingLevel);
        source = normalizeText(source);
        if (thinkingBudget != null && thinkingBudget < 0) {
            thinkingBudget = null;
        }
    }

    public static AiThinkingConfig of(
            AiReasoningEffort reasoningEffort,
            Integer thinkingBudget,
            String thinkingLevel,
            String source
    ) {
        AiReasoningEffort normalizedEffort = reasoningEffort;
        Integer normalizedBudget = thinkingBudget != null && thinkingBudget >= 0 ? thinkingBudget : null;
        String normalizedLevel = normalizeText(thinkingLevel);
        if (normalizedEffort == null && normalizedBudget != null) {
            normalizedEffort = inferEffortFromBudget(normalizedBudget);
        }
        if (normalizedEffort == null && normalizedLevel != null) {
            normalizedEffort = inferEffortFromLevel(normalizedLevel);
        }
        if (normalizedEffort == null) {
            return new AiThinkingConfig(null, normalizedBudget, normalizedLevel, source);
        }
        return new AiThinkingConfig(normalizedEffort, normalizedBudget, normalizedLevel, source);
    }

    public boolean isConfigured() {
        return reasoningEffort != null || thinkingBudget != null || thinkingLevel != null;
    }

    public AiThinkingConfig merge(AiThinkingConfig override) {
        if (override == null || !override.isConfigured()) {
            return this;
        }
        AiReasoningEffort mergedEffort = override.reasoningEffort() != null ? override.reasoningEffort() : reasoningEffort;
        Integer mergedBudget = override.thinkingBudget() != null ? override.thinkingBudget() : thinkingBudget;
        String mergedLevel = override.thinkingLevel() != null ? override.thinkingLevel() : thinkingLevel;
        String mergedSource = override.source() != null ? override.source() : source;
        return AiThinkingConfig.of(mergedEffort, mergedBudget, mergedLevel, mergedSource);
    }

    public String reasoningEffortCode() {
        return reasoningEffort == null ? null : reasoningEffort.name();
    }

    private static AiReasoningEffort inferEffortFromBudget(int budget) {
        if (budget <= 0) {
            return AiReasoningEffort.OFF;
        }
        if (budget <= 384) {
            return AiReasoningEffort.LOW;
        }
        if (budget <= 1280) {
            return AiReasoningEffort.MEDIUM;
        }
        return AiReasoningEffort.HIGH;
    }

    private static AiReasoningEffort inferEffortFromLevel(String thinkingLevel) {
        if (thinkingLevel == null || thinkingLevel.isBlank()) {
            return null;
        }
        String normalized = thinkingLevel.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "off", "none", "minimal" -> AiReasoningEffort.OFF;
            case "low" -> AiReasoningEffort.LOW;
            case "medium", "balanced", "standard" -> AiReasoningEffort.MEDIUM;
            case "high" -> AiReasoningEffort.HIGH;
            case "dynamic", "auto", "default" -> AiReasoningEffort.DYNAMIC;
            default -> null;
        };
    }

    private static String normalizeText(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return rawValue.trim();
    }
}
