package com.bishe.server.ai.gateway;

import java.util.Locale;

/**
 * 统一抽象的推理强度档位。
 */
public enum AiReasoningEffort {
    OFF,
    LOW,
    MEDIUM,
    HIGH,
    DYNAMIC;

    public static AiReasoningEffort fromNullable(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "OFF", "NONE", "DISABLED", "DISABLE", "NO_THINKING", "MINIMAL" -> OFF;
            case "LOW", "LIGHT", "FAST" -> LOW;
            case "MEDIUM", "BALANCED", "NORMAL", "STANDARD" -> MEDIUM;
            case "HIGH", "DEEP", "SLOW", "THOROUGH" -> HIGH;
            case "DYNAMIC", "AUTO", "DEFAULT" -> DYNAMIC;
            default -> null;
        };
    }
}
