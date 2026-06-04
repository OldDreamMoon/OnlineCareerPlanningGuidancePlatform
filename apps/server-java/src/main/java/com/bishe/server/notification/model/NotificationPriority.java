package com.bishe.server.notification.model;

/**
 * 通知优先级。
 */
public enum NotificationPriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT;

    public static NotificationPriority from(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return NORMAL;
        }
        for (NotificationPriority value : values()) {
            if (value.name().equalsIgnoreCase(rawValue.trim())) {
                return value;
            }
        }
        throw new IllegalArgumentException("unsupported notification priority: " + rawValue);
    }

    public boolean meetsThreshold(NotificationPriority threshold) {
        NotificationPriority safeThreshold = threshold == null ? HIGH : threshold;
        return this.ordinal() >= safeThreshold.ordinal();
    }
}
