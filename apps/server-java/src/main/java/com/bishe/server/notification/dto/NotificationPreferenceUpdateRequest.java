package com.bishe.server.notification.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 更新单个通知分类偏好。
 */
public record NotificationPreferenceUpdateRequest(
        @NotBlank(message = "category is required")
        String category,
        Boolean inboxEnabled,
        Boolean websocketEnabled,
        Boolean browserPopupEnabled,
        Boolean emailEnabled
) {
}
