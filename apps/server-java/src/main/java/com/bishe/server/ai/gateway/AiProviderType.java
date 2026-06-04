package com.bishe.server.ai.gateway;

/**
 * AI provider 后端类型。
 */
public enum AiProviderType {
    OPENAI_COMPATIBLE,
    GEMINI_NATIVE;

    public static AiProviderType from(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("providerType invalid");
        }
        return AiProviderType.valueOf(rawValue.trim().toUpperCase());
    }
}
