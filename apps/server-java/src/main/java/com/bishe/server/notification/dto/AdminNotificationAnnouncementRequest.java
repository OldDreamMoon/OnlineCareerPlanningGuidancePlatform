package com.bishe.server.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 管理员发布系统公告请求。
 */
public record AdminNotificationAnnouncementRequest(
        @NotBlank(message = "title is required")
        @Size(max = 160, message = "title too long")
        String title,
        @NotBlank(message = "content is required")
        @Size(max = 1000, message = "content too long")
        String content,
        List<String> targetRoles,
        List<Long> targetUserIds,
        @Size(max = 20, message = "priority too long")
        String priority,
        @Size(max = 60, message = "refType too long")
        String refType,
        @Size(max = 64, message = "refId too long")
        String refId,
        @Size(max = 80, message = "actionCode too long")
        String actionCode,
        Boolean emailRequested
) {
}
