package com.bishe.server.ai.gateway;

import java.util.Arrays;
import java.util.List;

/**
 * AI 路由执行模式：区分离线异步任务、同步阻塞请求与流式交互。
 */
public enum AiExecutionMode {
    SYNC_BLOCKING,
    STREAM_SSE,
    ASYNC_JOB,
    REALTIME_SESSION;

    public static AiExecutionMode from(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("executionMode invalid");
        }
        return AiExecutionMode.valueOf(rawValue.trim().toUpperCase());
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(Enum::name).toList();
    }
}
