package com.bishe.server.notification.service;

import com.bishe.server.notification.model.NotificationChannel;
import com.bishe.server.notification.model.NotificationDispatchStatus;

import java.time.Duration;

/**
 * 单个渠道的投递器。
 */
public interface NotificationChannelDispatcher {

    NotificationChannel channel();

    DispatchResult dispatch(NotificationDispatchService.DispatchJobSnapshot job);

    /**
     * 单次投递结果。
     */
    record DispatchResult(
            NotificationDispatchStatus status,
            int attemptNo,
            boolean retryable,
            Duration retryDelay,
            String requestSnapshotJson,
            String responseSnapshotJson,
            String errorCode,
            String errorMessage,
            long latencyMs
    ) {
    }
}
