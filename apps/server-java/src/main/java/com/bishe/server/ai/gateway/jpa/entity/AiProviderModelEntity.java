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
 * AI 提供商模型配置实体。
 */
@Entity
@Table(
        name = "ai_provider_models",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_ai_provider_models_provider_model", columnNames = {"provider_config_id", "model_code"})
        }
)
public class AiProviderModelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "provider_config_id", nullable = false)
    private long providerConfigId;

    @Column(name = "model_code", nullable = false, length = 120)
    private String modelCode;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "input_cost_per_1k", nullable = false, precision = 10, scale = 6)
    private BigDecimal inputCostPer1k;

    @Column(name = "output_cost_per_1k", nullable = false, precision = 10, scale = 6)
    private BigDecimal outputCostPer1k;

    @Column(name = "context_window")
    private Integer contextWindow;

    @Column(name = "max_output_tokens")
    private Integer maxOutputTokens;

    @Column(name = "supported_task_types_json", columnDefinition = "TEXT")
    private String supportedTaskTypesJson;

    @Column(name = "notes", length = 255)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AiProviderModelEntity() {
    }

    public static AiProviderModelEntity create(long providerConfigId) {
        AiProviderModelEntity entity = new AiProviderModelEntity();
        entity.providerConfigId = providerConfigId;
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

    public long getProviderConfigId() {
        return providerConfigId;
    }

    public void setProviderConfigId(long providerConfigId) {
        this.providerConfigId = providerConfigId;
    }

    public String getModelCode() {
        return modelCode;
    }

    public void setModelCode(String modelCode) {
        this.modelCode = modelCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public BigDecimal getInputCostPer1k() {
        return inputCostPer1k;
    }

    public void setInputCostPer1k(BigDecimal inputCostPer1k) {
        this.inputCostPer1k = inputCostPer1k;
    }

    public BigDecimal getOutputCostPer1k() {
        return outputCostPer1k;
    }

    public void setOutputCostPer1k(BigDecimal outputCostPer1k) {
        this.outputCostPer1k = outputCostPer1k;
    }

    public Integer getContextWindow() {
        return contextWindow;
    }

    public void setContextWindow(Integer contextWindow) {
        this.contextWindow = contextWindow;
    }

    public Integer getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(Integer maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public String getSupportedTaskTypesJson() {
        return supportedTaskTypesJson;
    }

    public void setSupportedTaskTypesJson(String supportedTaskTypesJson) {
        this.supportedTaskTypesJson = supportedTaskTypesJson;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
