package com.bishe.server.ai.gateway.task.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * AI 异步任务事件实体。
 */
@Entity
@Table(
        name = "ai_async_task_events",
        indexes = {
                @Index(name = "idx_ai_async_task_events_delivery_status", columnList = "delivery_status, created_at, id"),
                @Index(name = "idx_ai_async_task_events_task_id", columnList = "task_id, created_at, id")
        }
)
public class AiAsyncTaskEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "task_job_id", nullable = false)
    private Long taskJobId;

    @Column(name = "task_id", nullable = false, length = 64)
    private String taskId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "delivery_status", nullable = false, length = 32)
    private String deliveryStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", columnDefinition = "jsonb")
    private String payloadJson;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AiAsyncTaskEventEntity() {
    }

    public static AiAsyncTaskEventEntity create(
            String eventId,
            long taskJobId,
            String taskId,
            long userId,
            String eventType,
            String deliveryStatus,
            String payloadJson
    ) {
        AiAsyncTaskEventEntity entity = new AiAsyncTaskEventEntity();
        entity.eventId = eventId;
        entity.taskJobId = taskJobId;
        entity.taskId = taskId;
        entity.userId = userId;
        entity.eventType = eventType;
        entity.deliveryStatus = deliveryStatus;
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
}
