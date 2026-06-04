package com.bishe.server.governance.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(
        name = "content_reports",
        indexes = {
                @Index(name = "idx_content_reports_reporter", columnList = "reporter_user_id, created_at"),
                @Index(name = "idx_content_reports_target", columnList = "target_type, target_id, status")
        }
)
public class ContentReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "reporter_user_id", nullable = false)
    private Long reporterUserId;

    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @Column(name = "target_id", nullable = false, length = 100)
    private String targetId;

    @Column(name = "reason_code", nullable = false, length = 50)
    private String reasonCode;

    @Column(name = "detail", length = 1000)
    private String detail;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "latest_action", nullable = false, length = 50)
    private String latestAction;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected ContentReportEntity() {
    }

    public static ContentReportEntity create(
            long reporterUserId,
            String targetType,
            String targetId,
            String reasonCode,
            String detail
    ) {
        ContentReportEntity entity = new ContentReportEntity();
        entity.reporterUserId = reporterUserId;
        entity.targetType = targetType;
        entity.targetId = targetId;
        entity.reasonCode = reasonCode;
        entity.detail = detail;
        entity.status = "PENDING";
        entity.latestAction = "NONE";
        entity.closedAt = null;
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

    public void updateStatus(String status, String latestAction, boolean closed) {
        this.status = status;
        this.latestAction = latestAction;
        Instant now = Instant.now();
        this.updatedAt = now;
        if (closed) {
            this.closedAt = now;
        }
    }

    public Long getId() {
        return id;
    }

    public Long getReporterUserId() {
        return reporterUserId;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getDetail() {
        return detail;
    }

    public String getStatus() {
        return status;
    }

    public String getLatestAction() {
        return latestAction;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
