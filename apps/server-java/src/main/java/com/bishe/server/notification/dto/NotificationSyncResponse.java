package com.bishe.server.notification.dto;

import java.util.List;

/**
 * 通知增量同步响应。
 */
public record NotificationSyncResponse(
        long unreadCount,
        long actionableCount,
        List<NotificationListResponse.NotificationItem> records,
        boolean hasMore,
        Long latestNotificationId,
        int limit
) {
}
