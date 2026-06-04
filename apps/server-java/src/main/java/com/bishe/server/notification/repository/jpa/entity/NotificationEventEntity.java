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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 标准化通知事件实体。
 */
@Entity
@Table(
        name = "notification_events",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_notification_events_event_id", columnNames = {"event_id"}),
                @UniqueConstraint(name = "uq_notification_events_dedupe_key", columnNames = {"dedupe_key"})
        },
        indexes = {
                @Index(name = "idx_notification_events_category_time", columnList = "category, occurred_at, id"),
                @Index(name = "idx_notification_events_source", columnList = "source_type, source_id, occurred_at, id")
        }
)
public class NotificationEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "type", nullable = false, length = 60)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private NotificationCategory category;

    @Column(name = "source_type", length = 60)
    private String sourceType;

    @Column(name = "source_id", length = 64)
    private String sourceId;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private NotificationPriority priority;

    @Column(name = "dedupe_key", length = 160)
    private String dedupeKey;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected NotificationEventEntity() {
    }

    public static NotificationEventEntity create(
            String eventId,
            String type,
            NotificationCategory category,
            String sourceType,
            String sourceId,
            Long actorUserId,
            NotificationPriority priority,
            String dedupeKey,
            String payloadJson,
            Instant occurredAt
    ) {
        NotificationEventEntity entity = new NotificationEventEntity();
        entity.eventId = eventId;
        entity.type = type;
        entity.category = category;
        entity.sourceType = sourceType;
        entity.sourceId = sourceId;
        entity.actorUserId = actorUserId;
        entity.priority = priority;
        entity.dedupeKey = dedupeKey;
        entity.payloadJson = payloadJson;
        entity.occurredAt = occurredAt;
        return entity;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (occurredAt == null) {
            occurredAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getType() {
        return type;
    }

    public NotificationCategory getCategory() {
        return category;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public NotificationPriority getPriority() {
        return priority;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
