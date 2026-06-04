package com.bishe.server.ai.gateway.task;

/**
 * 异步 AI 任务状态。
 */
public enum AiAsyncTaskStatus {
    PENDING,
    RUNNING,
    RETRY_WAIT,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }

    public static AiAsyncTaskStatus from(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("async task status invalid");
        }
        return AiAsyncTaskStatus.valueOf(rawValue.trim().toUpperCase());
    }
}
