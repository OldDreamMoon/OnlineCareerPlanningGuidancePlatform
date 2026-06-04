package com.bishe.server.ai.quota.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * AI 调用日志实体。
 */
@Entity
@Table(
        name = "ai_call_logs",
        indexes = {
                @Index(name = "idx_ai_call_logs_user_task_time", columnList = "user_id, task_type, created_at")
        }
)
public class AiCallLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "task_type", nullable = false, length = 30)
    private String taskType;

    @Column(name = "scene_code", length = 60)
    private String sceneCode;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @Column(name = "route_code", length = 80)
    private String routeCode;

    @Column(name = "route_policy_code", length = 80)
    private String routePolicyCode;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "error_code", length = 20)
    private String errorCode;

    @Column(name = "request_tokens", nullable = false)
    private int requestTokens;

    @Column(name = "response_tokens", nullable = false)
    private int responseTokens;

    @Column(name = "total_tokens", nullable = false)
    private int totalTokens;

    @Column(name = "thoughts_tokens", nullable = false)
    private int thoughtsTokens;

    @Column(name = "reasoning_effort", length = 20)
    private String reasoningEffort;

    @Column(name = "thinking_budget")
    private Integer thinkingBudget;

    @Column(name = "thinking_level", length = 40)
    private String thinkingLevel;

    @Column(name = "estimated_cost", nullable = false, precision = 10, scale = 6)
    private BigDecimal estimatedCost;

    @Column(name = "charged_points", nullable = false)
    private int chargedPoints;

    @Column(name = "quota_weight", nullable = false)
    private int quotaWeight;

    @Column(name = "result_summary", columnDefinition = "TEXT")
    private String resultSummary;

    @Column(name = "result_payload_json", columnDefinition = "TEXT")
    private String resultPayloadJson;

    @Column(name = "user_deleted_at")
    private Instant userDeletedAt;

    @Column(name = "user_tier", nullable = false, length = 20)
    private String userTier;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AiCallLogEntity() {
    }

    public static AiCallLogEntity create(
            String traceId,
            long userId,
            String taskType,
            String sceneCode,
            String provider,
            String model,
            String routeCode,
            String routePolicyCode,
            long latencyMs,
            String status,
            String errorCode,
            int requestTokens,
            int responseTokens,
            int totalTokens,
            int thoughtsTokens,
            String reasoningEffort,
            Integer thinkingBudget,
            String thinkingLevel,
            BigDecimal estimatedCost,
            int chargedPoints,
            int quotaWeight,
            String resultSummary,
            String resultPayloadJson,
            String userTier
    ) {
        AiCallLogEntity entity = new AiCallLogEntity();
        entity.traceId = traceId;
        entity.userId = userId;
        entity.taskType = taskType;
        entity.sceneCode = sceneCode;
        entity.provider = provider;
        entity.model = model;
        entity.routeCode = routeCode;
        entity.routePolicyCode = routePolicyCode;
        entity.latencyMs = latencyMs;
        entity.status = status;
        entity.errorCode = errorCode;
        entity.requestTokens = requestTokens;
        entity.responseTokens = responseTokens;
        entity.totalTokens = totalTokens;
        entity.thoughtsTokens = thoughtsTokens;
        entity.reasoningEffort = reasoningEffort;
        entity.thinkingBudget = thinkingBudget;
        entity.thinkingLevel = thinkingLevel;
        entity.estimatedCost = estimatedCost;
        entity.chargedPoints = chargedPoints;
        entity.quotaWeight = quotaWeight;
        entity.resultSummary = resultSummary;
        entity.resultPayloadJson = resultPayloadJson;
        entity.userTier = userTier;
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

    public String getTaskType() {
        return taskType;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public int getChargedPoints() {
        return chargedPoints;
    }

    public String getResultSummary() {
        return resultSummary;
    }

    public String getResultPayloadJson() {
        return resultPayloadJson;
    }

    public Instant getUserDeletedAt() {
        return userDeletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
