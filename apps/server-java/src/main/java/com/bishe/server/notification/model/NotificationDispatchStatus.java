package com.bishe.server.notification.model;

/**
 * 渠道任务状态。
 */
public enum NotificationDispatchStatus {
    PENDING,
    RUNNING,
    RETRY_WAIT,
    SENT,
    ACKED,
    SKIPPED,
    FAILED,
    DEAD;

    public static NotificationDispatchStatus from(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return PENDING;
        }
        for (NotificationDispatchStatus value : values()) {
            if (value.name().equalsIgnoreCase(rawValue.trim())) {
                return value;
            }
        }
        throw new IllegalArgumentException("unsupported notification dispatch status: " + rawValue);
    }
}
