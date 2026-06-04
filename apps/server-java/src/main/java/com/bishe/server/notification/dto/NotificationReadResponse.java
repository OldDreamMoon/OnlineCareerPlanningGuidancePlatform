package com.bishe.server.notification.dto;

/**
 * 通知已读响应。
 */
public record NotificationReadResponse(
        long id,
        boolean read,
        Long readAt
) {
}
