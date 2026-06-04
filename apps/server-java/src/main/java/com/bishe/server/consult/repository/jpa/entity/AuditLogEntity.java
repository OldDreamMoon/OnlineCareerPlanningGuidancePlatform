package com.bishe.server.consult.repository.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(
        name = "audit_logs",
        indexes = {
                @Index(name = "idx_audit_logs_trace", columnList = "trace_id"),
                @Index(name = "idx_audit_logs_action_time", columnList = "action_type, created_at")
        }
)
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Column(name = "operator_user_id", nullable = false)
    private Long operatorUserId;

    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType;

    @Column(name = "target_type", length = 30)
    private String targetType;

    @Column(name = "target_id", length = 100)
    private String targetId;

    @Column(name = "detail_json", columnDefinition = "TEXT")
    private String detailJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditLogEntity() {
    }

    public static AuditLogEntity create(
            String traceId,
            long operatorUserId,
            String actionType,
            String targetType,
            String targetId,
            String detailJson
    ) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.traceId = traceId;
        entity.operatorUserId = operatorUserId;
        entity.actionType = actionType;
        entity.targetType = targetType;
        entity.targetId = targetId;
        entity.detailJson = detailJson;
        entity.createdAt = Instant.now();
        return entity;
    }

    public Long getId() {
        return id;
    }

    public String getTraceId() {
        return traceId;
    }

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public String getActionType() {
        return actionType;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getDetailJson() {
        return detailJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
