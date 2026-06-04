package com.bishe.server.notification.dto;

import java.util.List;

/**
 * 通知偏好列表响应。
 */
public record NotificationPreferencesResponse(
        List<PreferenceItem> records
) {

    /**
     * 分类偏好项。
     */
    public record PreferenceItem(
            String category,
            boolean inboxEnabled,
            boolean websocketEnabled,
            boolean browserPopupEnabled,
            boolean emailEnabled,
            boolean customized
    ) {
    }
}
