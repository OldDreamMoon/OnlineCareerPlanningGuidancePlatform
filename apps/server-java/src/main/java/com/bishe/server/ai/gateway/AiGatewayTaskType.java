package com.bishe.server.ai.gateway;

import java.util.Arrays;
import java.util.List;

/**
 * AI 网关支持的任务类型，用于路由不同 provider。
 */
public enum AiGatewayTaskType {
    RESUME,
    INTERVIEW_TEXT,
    INTERVIEW_SUMMARY,
    PORTRAIT_SUMMARY,
    COMMUNITY_REPLY,
    ICEBREAK,
    STT,
    TTS;

    public static AiGatewayTaskType from(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("gateway taskType invalid");
        }
        return AiGatewayTaskType.valueOf(rawValue.trim().toUpperCase());
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(Enum::name).toList();
    }
}
