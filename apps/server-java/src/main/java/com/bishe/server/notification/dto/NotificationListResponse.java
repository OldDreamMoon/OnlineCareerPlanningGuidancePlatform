package com.bishe.server.notification.dto;

import java.util.Map;
import java.util.List;

/**
 * 通知列表响应。
 */
public record NotificationListResponse(
        long unreadCount,
        long actionableCount,
        List<NotificationItem> records,
        long total,
        int page,
        int size
) {

    /**
     * 通知项。
     */
    public record NotificationItem(
            long id,
            String type,
            String category,
            String title,
            String content,
            boolean read,
            String refType,
            String refId,
            String actionCode,
            boolean actionable,
            Map<String, Object> payload,
            Long createdAt,
            Long readAt
    ) {
    }
}
