package com.bishe.server.ai.gateway.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * AI 模型路由配置实体。
 */
@Entity
@Table(
        name = "ai_model_routes",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_ai_model_routes_code", columnNames = {"route_code"})
        }
)
public class AiModelRouteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "route_code", nullable = false, length = 60)
    private String routeCode;

    @Column(name = "task_type", nullable = false, length = 40)
    private String taskType;

    @Column(name = "scene_code", length = 60)
    private String sceneCode;

    @Column(name = "scene_route_policy_id")
    private Long sceneRoutePolicyId;

    @Column(name = "provider_config_id", nullable = false)
    private long providerConfigId;

    @Column(name = "model_name", nullable = false, length = 120)
    private String modelName;

    @Column(name = "priority_no", nullable = false)
    private int priorityNo;

    @Column(name = "candidate_weight", nullable = false)
    private int candidateWeight;

    @Column(name = "execution_mode", nullable = false, length = 32)
    private String executionMode;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "temperature", nullable = false, precision = 5, scale = 2)
    private BigDecimal temperature;

    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(name = "prompt_template_name", length = 80)
    private String promptTemplateName;

    @Column(name = "extra_config_json", columnDefinition = "TEXT")
    private String extraConfigJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AiModelRouteEntity() {
    }

    public static AiModelRouteEntity create() {
        return new AiModelRouteEntity();
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

    public String getRouteCode() {
        return routeCode;
    }

    public void setRouteCode(String routeCode) {
        this.routeCode = routeCode;
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

    public Long getSceneRoutePolicyId() {
        return sceneRoutePolicyId;
    }

    public void setSceneRoutePolicyId(Long sceneRoutePolicyId) {
        this.sceneRoutePolicyId = sceneRoutePolicyId;
    }

    public long getProviderConfigId() {
        return providerConfigId;
    }

    public void setProviderConfigId(long providerConfigId) {
        this.providerConfigId = providerConfigId;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public int getPriorityNo() {
        return priorityNo;
    }

    public void setPriorityNo(int priorityNo) {
        this.priorityNo = priorityNo;
    }

    public int getCandidateWeight() {
        return candidateWeight;
    }

    public void setCandidateWeight(int candidateWeight) {
        this.candidateWeight = candidateWeight;
    }

    public String getExecutionMode() {
        return executionMode;
    }

    public void setExecutionMode(String executionMode) {
        this.executionMode = executionMode;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public BigDecimal getTemperature() {
        return temperature;
    }

    public void setTemperature(BigDecimal temperature) {
        this.temperature = temperature;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getPromptTemplateName() {
        return promptTemplateName;
    }

    public void setPromptTemplateName(String promptTemplateName) {
        this.promptTemplateName = promptTemplateName;
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
