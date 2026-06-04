package com.bishe.server.notification.dto;

/**
 * 全部已读响应。
 */
public record NotificationReadAllResponse(
        long updatedCount,
        long unreadCount
) {
}
