package com.bishe.server.ai.gateway.task;

/**
 * 异步 AI 任务事件类型。
 */
public enum AiAsyncTaskEventType {
    SUBMITTED,
    STARTED,
    RETRY_SCHEDULED,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
