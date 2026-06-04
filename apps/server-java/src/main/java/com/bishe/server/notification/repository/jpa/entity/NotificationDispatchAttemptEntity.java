package com.bishe.server.notification.repository.jpa.entity;

import com.bishe.server.notification.model.NotificationDispatchStatus;
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

import java.time.Instant;

/**
 * 通知渠道单次派发尝试实体。
 */
@Entity
@Table(
        name = "notification_dispatch_attempts",
        indexes = {
                @Index(name = "idx_notification_dispatch_attempts_job", columnList = "job_id, created_at, id")
        }
)
public class NotificationDispatchAttemptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private NotificationDispatchStatus status;

    @Column(name = "request_snapshot_json", columnDefinition = "TEXT")
    private String requestSnapshotJson;

    @Column(name = "response_snapshot_json", columnDefinition = "TEXT")
    private String responseSnapshotJson;

    @Column(name = "error_code", length = 40)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "latency_ms", nullable = false)
    private Long latencyMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected NotificationDispatchAttemptEntity() {
    }

    public static NotificationDispatchAttemptEntity create(
            long jobId,
            int attemptNo,
            NotificationDispatchStatus status,
            String requestSnapshotJson,
            String responseSnapshotJson,
            String errorCode,
            String errorMessage,
            long latencyMs
    ) {
        NotificationDispatchAttemptEntity entity = new NotificationDispatchAttemptEntity();
        entity.jobId = jobId;
        entity.attemptNo = Math.max(attemptNo, 0);
        entity.status = status == null ? NotificationDispatchStatus.PENDING : status;
        entity.requestSnapshotJson = requestSnapshotJson;
        entity.responseSnapshotJson = responseSnapshotJson;
        entity.errorCode = errorCode;
        entity.errorMessage = errorMessage;
        entity.latencyMs = Math.max(latencyMs, 0L);
        return entity;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
