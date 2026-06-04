package com.bishe.server.notification.dto;

/**
 * WebSocket 短票据响应。
 */
public record NotificationWebSocketTicketResponse(
        String ticket,
        String wsPath,
        Long expiresAt
) {
}
