package com.bishe.server.notification.repository.jpa.entity;

import com.bishe.server.notification.model.NotificationChannel;
import com.bishe.server.notification.model.NotificationDispatchStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 通知渠道派发任务实体。
 */
@Entity
@Table(
        name = "notification_dispatch_jobs",
        indexes = {
                @Index(name = "idx_notification_dispatch_jobs_status_run", columnList = "status, next_run_at, id"),
                @Index(name = "idx_notification_dispatch_jobs_user_channel", columnList = "user_id, channel, status, id"),
                @Index(name = "idx_notification_dispatch_jobs_status_created", columnList = "status, created_at, id")
        }
)
public class NotificationDispatchJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "job_id", nullable = false, length = 64)
    private String jobId;

    @Column(name = "notification_id", nullable = false)
    private Long notificationId;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 32)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private NotificationDispatchStatus status;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts;

    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt;

    @Column(name = "lease_owner", length = 80)
    private String leaseOwner;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "acked_at")
    private Instant ackedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "error_code", length = 40)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_id", insertable = false, updatable = false)
    private NotificationEntity notification;

    protected NotificationDispatchJobEntity() {
    }

    public static NotificationDispatchJobEntity create(
            String jobId,
            long notificationId,
            String eventId,
            long userId,
            NotificationChannel channel,
            int maxAttempts,
            String payloadJson
    ) {
        NotificationDispatchJobEntity entity = new NotificationDispatchJobEntity();
        entity.jobId = jobId;
        entity.notificationId = notificationId;
        entity.eventId = eventId;
        entity.userId = userId;
        entity.channel = channel;
        entity.status = NotificationDispatchStatus.PENDING;
        entity.attemptCount = 0;
        entity.maxAttempts = Math.max(maxAttempts, 1);
        entity.nextRunAt = Instant.now();
        entity.payloadJson = payloadJson;
        return entity;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (nextRunAt == null) {
            nextRunAt = now;
        }
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

    public String getJobId() {
        return jobId;
    }

    public Long getNotificationId() {
        return notificationId;
    }

    public String getEventId() {
        return eventId;
    }

    public Long getUserId() {
        return userId;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public NotificationDispatchStatus getStatus() {
        return status;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public Integer getMaxAttempts() {
        return maxAttempts;
    }

    public Instant getNextRunAt() {
        return nextRunAt;
    }

    public String getLeaseOwner() {
        return leaseOwner;
    }

    public Instant getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getAckedAt() {
        return ackedAt;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public NotificationEntity getNotification() {
        return notification;
    }
}
