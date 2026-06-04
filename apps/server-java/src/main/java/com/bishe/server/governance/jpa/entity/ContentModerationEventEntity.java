package com.bishe.server.governance.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(
        name = "content_moderation_events",
        indexes = {
                @Index(name = "idx_moderation_trace", columnList = "trace_id"),
                @Index(name = "idx_moderation_target", columnList = "target_type, target_id, created_at")
        }
)
public class ContentModerationEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;

    @Column(name = "target_id", length = 100)
    private String targetId;

    @Column(name = "risk_level", nullable = false, length = 20)
    private String riskLevel;

    @Column(name = "action", nullable = false, length = 20)
    private String action;

    @Column(name = "reason_code", nullable = false, length = 100)
    private String reasonCode;

    @Column(name = "masked_text", columnDefinition = "TEXT")
    private String maskedText;

    @Column(name = "operator_user_id", nullable = false)
    private Long operatorUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ContentModerationEventEntity() {
    }

    public static ContentModerationEventEntity create(
            String traceId,
            String sourceType,
            String targetType,
            String targetId,
            String riskLevel,
            String action,
            String reasonCode,
            String maskedText,
            long operatorUserId
    ) {
        ContentModerationEventEntity entity = new ContentModerationEventEntity();
        entity.traceId = traceId;
        entity.sourceType = sourceType;
        entity.targetType = targetType;
        entity.targetId = targetId;
        entity.riskLevel = riskLevel;
        entity.action = action;
        entity.reasonCode = reasonCode;
        entity.maskedText = maskedText;
        entity.operatorUserId = operatorUserId;
        return entity;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void updateTargetId(String targetId) {
        this.targetId = targetId;
    }

    public void updateDecision(String action, String riskLevel, String reasonCode) {
        this.action = action;
        this.riskLevel = riskLevel;
        this.reasonCode = reasonCode;
    }

    public Long getId() {
        return id;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public String getAction() {
        return action;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getMaskedText() {
        return maskedText;
    }

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
