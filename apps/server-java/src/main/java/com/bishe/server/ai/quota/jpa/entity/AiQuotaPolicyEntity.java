package com.bishe.server.ai.quota.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * AI 配额策略实体。
 */
@Entity
@Table(
        name = "ai_quota_policies",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_ai_quota_policies_tier_task_scene", columnNames = {"tier", "task_type", "scene_code"})
        },
        indexes = {
                @Index(name = "idx_ai_quota_policies_lookup", columnList = "tier, task_type, scene_code")
        }
)
public class AiQuotaPolicyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "tier", nullable = false, length = 20)
    private String tier;

    @Column(name = "task_type", nullable = false, length = 30)
    private String taskType;

    @Column(name = "scene_code", length = 60)
    private String sceneCode;

    @Column(name = "daily_free_limit", nullable = false)
    private int dailyFreeLimit;

    @Column(name = "points_per_call", nullable = false)
    private int pointsPerCall;

    @Column(name = "daily_max_limit", nullable = false)
    private int dailyMaxLimit;

    @Column(name = "model_preference", length = 50)
    private String modelPreference;

    @Column(name = "max_input_tokens", nullable = false)
    private int maxInputTokens;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AiQuotaPolicyEntity() {
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

    public String getTier() {
        return tier;
    }

    public String getTaskType() {
        return taskType;
    }

    public String getSceneCode() {
        return sceneCode;
    }

    public int getDailyFreeLimit() {
        return dailyFreeLimit;
    }

    public void setDailyFreeLimit(int dailyFreeLimit) {
        this.dailyFreeLimit = dailyFreeLimit;
    }

    public int getPointsPerCall() {
        return pointsPerCall;
    }

    public void setPointsPerCall(int pointsPerCall) {
        this.pointsPerCall = pointsPerCall;
    }

    public int getDailyMaxLimit() {
        return dailyMaxLimit;
    }

    public void setDailyMaxLimit(int dailyMaxLimit) {
        this.dailyMaxLimit = dailyMaxLimit;
    }

    public String getModelPreference() {
        return modelPreference;
    }

    public void setModelPreference(String modelPreference) {
        this.modelPreference = modelPreference;
    }

    public int getMaxInputTokens() {
        return maxInputTokens;
    }

    public void setMaxInputTokens(int maxInputTokens) {
        this.maxInputTokens = maxInputTokens;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
