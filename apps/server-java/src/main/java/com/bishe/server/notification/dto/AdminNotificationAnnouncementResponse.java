package com.bishe.server.notification.dto;

import java.util.List;

/**
 * 管理员发布系统公告响应。
 */
public record AdminNotificationAnnouncementResponse(
        String eventId,
        int notificationCount,
        List<String> targetRoles,
        Long createdAt
) {
}
