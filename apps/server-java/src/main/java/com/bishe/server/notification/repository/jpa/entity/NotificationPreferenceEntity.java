package com.bishe.server.notification.repository.jpa.entity;

import com.bishe.server.notification.model.NotificationCategory;
import com.bishe.server.notification.model.NotificationPriority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 用户通知偏好实体。
 */
@Entity
@Table(
        name = "notification_preferences",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_notification_preferences_user_category", columnNames = {"user_id", "category"})
        },
        indexes = {
                @Index(name = "idx_notification_preferences_user", columnList = "user_id, category")
        }
)
public class NotificationPreferenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private NotificationCategory category;

    @Column(name = "inbox_enabled", nullable = false)
    private boolean inboxEnabled = true;

    @Column(name = "websocket_enabled", nullable = false)
    private boolean websocketEnabled = true;

    @Column(name = "browser_popup_enabled", nullable = false)
    private boolean browserPopupEnabled = true;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_urgency_threshold", nullable = false, length = 20)
    private NotificationPriority emailUrgencyThreshold = NotificationPriority.HIGH;

    @Column(name = "quiet_hours_json", length = 255)
    private String quietHoursJson;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationPreferenceEntity() {
    }

    public static NotificationPreferenceEntity create(long userId, NotificationCategory category) {
        NotificationPreferenceEntity entity = new NotificationPreferenceEntity();
        entity.setUserId(userId);
        entity.setCategory(category);
        return entity;
    }

    @PrePersist
    void onCreate() {
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public NotificationCategory getCategory() {
        return category;
    }

    public void setCategory(NotificationCategory category) {
        this.category = category;
    }

    public boolean isInboxEnabled() {
        return inboxEnabled;
    }

    public void setInboxEnabled(boolean inboxEnabled) {
        this.inboxEnabled = inboxEnabled;
    }

    public boolean isWebsocketEnabled() {
        return websocketEnabled;
    }

    public void setWebsocketEnabled(boolean websocketEnabled) {
        this.websocketEnabled = websocketEnabled;
    }

    public boolean isBrowserPopupEnabled() {
        return browserPopupEnabled;
    }

    public void setBrowserPopupEnabled(boolean browserPopupEnabled) {
        this.browserPopupEnabled = browserPopupEnabled;
    }

    public boolean isEmailEnabled() {
        return emailEnabled;
    }

    public void setEmailEnabled(boolean emailEnabled) {
        this.emailEnabled = emailEnabled;
    }

    public NotificationPriority getEmailUrgencyThreshold() {
        return emailUrgencyThreshold;
    }

    public void setEmailUrgencyThreshold(NotificationPriority emailUrgencyThreshold) {
        this.emailUrgencyThreshold = emailUrgencyThreshold;
    }

    public String getQuietHoursJson() {
        return quietHoursJson;
    }

    public void setQuietHoursJson(String quietHoursJson) {
        this.quietHoursJson = quietHoursJson;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
