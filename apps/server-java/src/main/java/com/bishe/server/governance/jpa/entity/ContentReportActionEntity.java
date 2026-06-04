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
        name = "content_report_actions",
        indexes = {
                @Index(name = "idx_content_report_actions_report", columnList = "report_id, created_at")
        }
)
public class ContentReportActionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "operator_user_id", nullable = false)
    private Long operatorUserId;

    @Column(name = "decision", nullable = false, length = 20)
    private String decision;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "comment", length = 1000)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ContentReportActionEntity() {
    }

    public static ContentReportActionEntity create(
            long reportId,
            long operatorUserId,
            String decision,
            String action,
            String comment
    ) {
        ContentReportActionEntity entity = new ContentReportActionEntity();
        entity.reportId = reportId;
        entity.operatorUserId = operatorUserId;
        entity.decision = decision;
        entity.action = action;
        entity.comment = comment;
        return entity;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getReportId() {
        return reportId;
    }

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public String getDecision() {
        return decision;
    }

    public String getAction() {
        return action;
    }

    public String getComment() {
        return comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
