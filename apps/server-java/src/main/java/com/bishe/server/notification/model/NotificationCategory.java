package com.bishe.server.notification.model;

/**
 * 平台通知分类。
 */
public enum NotificationCategory {
    AI_TASK,
    CONSULT,
    BOUNTY,
    CERTIFICATION,
    SYSTEM,
    COMMUNITY;

    public static NotificationCategory from(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return SYSTEM;
        }
        for (NotificationCategory value : values()) {
            if (value.name().equalsIgnoreCase(rawValue.trim())) {
                return value;
            }
        }
        throw new IllegalArgumentException("unsupported notification category: " + rawValue);
    }
}
