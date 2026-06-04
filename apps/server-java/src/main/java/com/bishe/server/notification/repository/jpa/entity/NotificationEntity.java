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

import java.time.Instant;

/**
 * 通知收件箱实体。
 */
@Entity
@Table(
        name = "notifications",
        indexes = {
                @Index(name = "idx_notifications_user_time", columnList = "user_id, created_at, id"),
                @Index(name = "idx_notifications_user_read", columnList = "user_id, is_read, created_at, id"),
                @Index(name = "idx_notifications_user_read_archived", columnList = "user_id, is_read, archived_at, created_at, id"),
                @Index(name = "idx_notifications_event_id", columnList = "event_id"),
                @Index(name = "idx_notifications_category_time", columnList = "category, created_at, id")
        }
)
public class NotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private NotificationCategory category;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "ref_type", length = 60)
    private String refType;

    @Column(name = "ref_id", length = 64)
    private String refId;

    @Column(name = "action_code", length = 80)
    private String actionCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private NotificationPriority priority;

    @Column(name = "event_id", length = 64)
    private String eventId;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationEntity() {
    }

    public static NotificationEntity create(
            long userId,
            String type,
            NotificationCategory category,
            String title,
            String content,
            String refType,
            String refId,
            String actionCode,
            NotificationPriority priority,
            String eventId,
            String payloadJson
    ) {
        NotificationEntity entity = new NotificationEntity();
        entity.userId = userId;
        entity.type = type;
        entity.category = category;
        entity.title = title;
        entity.content = content;
        entity.refType = refType;
        entity.refId = refId;
        entity.actionCode = actionCode;
        entity.priority = priority;
        entity.eventId = eventId;
        entity.payloadJson = payloadJson;
        return entity;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
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

    public String getType() {
        return type;
    }

    public NotificationCategory getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getRefType() {
        return refType;
    }

    public String getRefId() {
        return refId;
    }

    public String getActionCode() {
        return actionCode;
    }

    public NotificationPriority getPriority() {
        return priority;
    }

    public String getEventId() {
        return eventId;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public boolean isRead() {
        return read;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
