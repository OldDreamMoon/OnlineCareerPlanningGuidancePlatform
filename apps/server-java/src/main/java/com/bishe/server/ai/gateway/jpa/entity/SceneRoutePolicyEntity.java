package com.bishe.server.ai.gateway.jpa.entity;

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
 * AI 场景路由策略实体。
 */
@Entity
@Table(
        name = "ai_scene_route_policies",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_ai_scene_route_policies_code", columnNames = {"policy_code"}),
                @UniqueConstraint(name = "uq_ai_scene_route_policies_scene_tier", columnNames = {"task_type", "scene_code", "user_tier"})
        },
        indexes = {
                @Index(name = "idx_ai_scene_route_policies_lookup", columnList = "task_type, scene_code, user_tier, enabled")
        }
)
public class SceneRoutePolicyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "policy_code", nullable = false, length = 80)
    private String policyCode;

    @Column(name = "task_type", nullable = false, length = 40)
    private String taskType;

    @Column(name = "scene_code", nullable = false, length = 60)
    private String sceneCode;

    @Column(name = "user_tier", nullable = false, length = 20)
    private String userTier;

    @Column(name = "strategy_type", nullable = false, length = 20)
    private String strategyType;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "notes", length = 255)
    private String notes;

    @Column(name = "extra_config_json", columnDefinition = "TEXT")
    private String extraConfigJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SceneRoutePolicyEntity() {
    }

    public static SceneRoutePolicyEntity create(
            String policyCode,
            String taskType,
            String sceneCode,
            String userTier,
            String strategyType,
            boolean enabled,
            String notes,
            String extraConfigJson
    ) {
        SceneRoutePolicyEntity entity = new SceneRoutePolicyEntity();
        entity.policyCode = policyCode;
        entity.taskType = taskType;
        entity.sceneCode = sceneCode;
        entity.userTier = userTier;
        entity.strategyType = strategyType;
        entity.enabled = enabled;
        entity.notes = notes;
        entity.extraConfigJson = extraConfigJson;
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

    public String getPolicyCode() {
        return policyCode;
    }

    public void setPolicyCode(String policyCode) {
        this.policyCode = policyCode;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getSceneCode() {
        return sceneCode;
    }

    public void setSceneCode(String sceneCode) {
        this.sceneCode = sceneCode;
    }

    public String getUserTier() {
        return userTier;
    }

    public void setUserTier(String userTier) {
        this.userTier = userTier;
    }

    public String getStrategyType() {
        return strategyType;
    }

    public void setStrategyType(String strategyType) {
        this.strategyType = strategyType;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getExtraConfigJson() {
        return extraConfigJson;
    }

    public void setExtraConfigJson(String extraConfigJson) {
        this.extraConfigJson = extraConfigJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
