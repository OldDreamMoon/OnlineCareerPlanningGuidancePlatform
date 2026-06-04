package com.bishe.server.ai.interview.jpa.entity;

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

/**
 * 文本面试会话实体。
 */
@Entity
@Table(
        name = "interview_sessions",
        indexes = {
                @Index(name = "idx_interview_sessions_user_time", columnList = "student_user_id, created_at")
        }
)
public class InterviewSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "student_user_id", nullable = false)
    private Long studentUserId;

    @Column(name = "target_role", nullable = false, length = 100)
    private String targetRole;

    @Column(name = "mode", nullable = false, length = 30)
    private String mode;

    @Column(name = "resume_context_json", columnDefinition = "TEXT")
    private String resumeContextJson;

    @Column(name = "session_context_json", columnDefinition = "TEXT")
    private String sessionContextJson;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "reply_round_limit", nullable = false)
    private int replyRoundLimit;

    @Column(name = "reply_round_used", nullable = false)
    private int replyRoundUsed;

    @Column(name = "summary_generated", nullable = false)
    private int summaryGeneratedFlag;

    @Column(name = "prepaid_points", nullable = false)
    private int prepaidPoints;

    @Column(name = "reserved_quota_weight", nullable = false)
    private int reservedQuotaWeight;

    @Column(name = "summary_overall_score")
    private Integer summaryOverallScore;

    @Column(name = "summary_strengths_json", columnDefinition = "TEXT")
    private String summaryStrengthsJson;

    @Column(name = "summary_weaknesses_json", columnDefinition = "TEXT")
    private String summaryWeaknessesJson;

    @Column(name = "summary_suggestions_json", columnDefinition = "TEXT")
    private String summarySuggestionsJson;

    @Column(name = "summary_provider", length = 50)
    private String summaryProvider;

    @Column(name = "summary_model", length = 100)
    private String summaryModel;

    @Column(name = "summary_latency_ms")
    private Long summaryLatencyMs;

    @Column(name = "summary_generated_at")
    private Instant summaryGeneratedAt;

    @Column(name = "finish_reason", length = 50)
    private String finishReason;

    @Column(name = "ended_by_ai", nullable = false)
    private int endedByAiFlag;

    @Column(name = "user_deleted_at")
    private Instant userDeletedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InterviewSessionEntity() {
    }

    public static InterviewSessionEntity create(
            String sessionId,
            long studentUserId,
            String targetRole,
            String mode,
            String resumeContextJson,
            String sessionContextJson,
            int replyRoundLimit,
            int prepaidPoints,
            int reservedQuotaWeight
    ) {
        InterviewSessionEntity entity = new InterviewSessionEntity();
        entity.sessionId = sessionId;
        entity.studentUserId = studentUserId;
        entity.targetRole = targetRole;
        entity.mode = mode;
        entity.resumeContextJson = resumeContextJson;
        entity.sessionContextJson = sessionContextJson;
        entity.status = "ACTIVE";
        entity.replyRoundLimit = replyRoundLimit;
        entity.replyRoundUsed = 0;
        entity.summaryGeneratedFlag = 0;
        entity.prepaidPoints = prepaidPoints;
        entity.reservedQuotaWeight = reservedQuotaWeight;
        entity.endedByAiFlag = 0;
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

    public String getSessionId() {
        return sessionId;
    }

    public Long getStudentUserId() {
        return studentUserId;
    }

    public String getTargetRole() {
        return targetRole;
    }

    public String getMode() {
        return mode;
    }

    public String getResumeContextJson() {
        return resumeContextJson;
    }

    public String getSessionContextJson() {
        return sessionContextJson;
    }

    public String getStatus() {
        return status;
    }

    public int getReplyRoundLimit() {
        return replyRoundLimit;
    }

    public int getReplyRoundUsed() {
        return replyRoundUsed;
    }

    public boolean isSummaryGenerated() {
        return summaryGeneratedFlag != 0;
    }

    public int getPrepaidPoints() {
        return prepaidPoints;
    }

    public int getReservedQuotaWeight() {
        return reservedQuotaWeight;
    }

    public Integer getSummaryOverallScore() {
        return summaryOverallScore;
    }

    public String getSummaryStrengthsJson() {
        return summaryStrengthsJson;
    }

    public String getSummaryWeaknessesJson() {
        return summaryWeaknessesJson;
    }

    public String getSummarySuggestionsJson() {
        return summarySuggestionsJson;
    }

    public String getSummaryProvider() {
        return summaryProvider;
    }

    public String getSummaryModel() {
        return summaryModel;
    }

    public Long getSummaryLatencyMs() {
        return summaryLatencyMs;
    }

    public Instant getSummaryGeneratedAt() {
        return summaryGeneratedAt;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public boolean isEndedByAi() {
        return endedByAiFlag != 0;
    }

    public Instant getUserDeletedAt() {
        return userDeletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
