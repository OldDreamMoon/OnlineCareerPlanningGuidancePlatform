package com.bishe.server.notification.model;

/**
 * 通知触达渠道。
 */
public enum NotificationChannel {
    WEBSOCKET,
    EMAIL;

    public static NotificationChannel from(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("notification channel required");
        }
        for (NotificationChannel value : values()) {
            if (value.name().equalsIgnoreCase(rawValue.trim())) {
                return value;
            }
        }
        throw new IllegalArgumentException("unsupported notification channel: " + rawValue);
    }
}
