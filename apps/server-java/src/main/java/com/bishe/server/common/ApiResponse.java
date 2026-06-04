package com.bishe.server.common;

import java.time.Instant;

public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String traceId,
        Long timestamp
) {

    public static <T> ApiResponse<T> ok(T data, String traceId) {
        return new ApiResponse<>("OK", "success", data, traceId, TimePayloads.toEpochMillis(Instant.now()));
    }

    public static <T> ApiResponse<T> ok(String message, T data, String traceId) {
        return new ApiResponse<>("OK", message, data, traceId, TimePayloads.toEpochMillis(Instant.now()));
    }

    public static <T> ApiResponse<T> error(String code, String message, String traceId) {
        return new ApiResponse<>(code, message, null, traceId, TimePayloads.toEpochMillis(Instant.now()));
    }

    public static <T> ApiResponse<T> error(String code, String message, T data, String traceId) {
        return new ApiResponse<>(code, message, data, traceId, TimePayloads.toEpochMillis(Instant.now()));
    }
}
