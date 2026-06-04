package com.bishe.server.ai.gateway;

import java.util.Arrays;
import java.util.List;

/**
 * Prompt 模板格式：兼容旧版纯文本模板，并支持结构化消息 bundle。
 */
public enum AiPromptTemplateFormat {
    TEXT,
    MESSAGE_BUNDLE;

    public static AiPromptTemplateFormat from(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return TEXT;
        }
        return valueOf(rawValue.trim().toUpperCase());
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(Enum::name).toList();
    }
}
