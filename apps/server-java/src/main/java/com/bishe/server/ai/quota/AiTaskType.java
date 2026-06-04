package com.bishe.server.ai.quota;

/**
 * AI 最小闭环支持的任务类型。
 */
public enum AiTaskType {
    RESUME,
    INTERVIEW_TEXT,
    INTERVIEW_SUMMARY,
    PORTRAIT_SUMMARY,
    COMMUNITY_REPLY,
    ICEBREAK,
    STT,
    TTS;

    public static AiTaskType from(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return INTERVIEW_TEXT;
        }
        return AiTaskType.valueOf(rawValue.trim().toUpperCase());
    }
}
