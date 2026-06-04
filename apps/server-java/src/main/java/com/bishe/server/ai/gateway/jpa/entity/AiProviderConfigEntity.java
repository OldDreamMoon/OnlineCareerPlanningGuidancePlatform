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
 * AI 提供商配置实体。
 */
@Entity
@Table(
        name = "ai_provider_configs",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_ai_provider_configs_code", columnNames = {"provider_code"})
        }
)
public class AiProviderConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "provider_code", nullable = false, length = 60)
    private String providerCode;

    @Column(name = "provider_type", nullable = false, length = 40)
    private String providerType;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(name = "base_url", nullable = false, length = 255)
    private String baseUrl;

    @Column(name = "api_key_ciphertext", columnDefinition = "TEXT")
    private String apiKeyCiphertext;

    @Column(name = "api_key_masked", length = 80)
    private String apiKeyMasked;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "timeout_ms", nullable = false)
    private int timeoutMs;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries;

    @Column(name = "cost_per_1k_input", nullable = false, precision = 10, scale = 6)
    private BigDecimal costPer1kInput;

    @Column(name = "cost_per_1k_output", nullable = false, precision = 10, scale = 6)
    private BigDecimal costPer1kOutput;

    @Column(name = "extra_config_json", columnDefinition = "TEXT")
    private String extraConfigJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AiProviderConfigEntity() {
    }

    public static AiProviderConfigEntity create(
            String providerCode,
            String providerType,
            String displayName,
            String baseUrl,
            String apiKeyCiphertext,
            String apiKeyMasked,
            boolean enabled,
            int timeoutMs,
            int maxRetries,
            BigDecimal costPer1kInput,
            BigDecimal costPer1kOutput,
            String extraConfigJson
    ) {
        AiProviderConfigEntity entity = new AiProviderConfigEntity();
        entity.providerCode = providerCode;
        entity.providerType = providerType;
        entity.displayName = displayName;
        entity.baseUrl = baseUrl;
        entity.apiKeyCiphertext = apiKeyCiphertext;
        entity.apiKeyMasked = apiKeyMasked;
        entity.enabled = enabled;
        entity.timeoutMs = timeoutMs;
        entity.maxRetries = maxRetries;
        entity.costPer1kInput = costPer1kInput;
        entity.costPer1kOutput = costPer1kOutput;
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

    public String getProviderCode() {
        return providerCode;
    }

    public void setProviderCode(String providerCode) {
        this.providerCode = providerCode;
    }

    public String getProviderType() {
        return providerType;
    }

    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKeyCiphertext() {
        return apiKeyCiphertext;
    }

    public void setApiKeyCiphertext(String apiKeyCiphertext) {
        this.apiKeyCiphertext = apiKeyCiphertext;
    }

    public String getApiKeyMasked() {
        return apiKeyMasked;
    }

    public void setApiKeyMasked(String apiKeyMasked) {
        this.apiKeyMasked = apiKeyMasked;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public BigDecimal getCostPer1kInput() {
        return costPer1kInput;
    }

    public void setCostPer1kInput(BigDecimal costPer1kInput) {
        this.costPer1kInput = costPer1kInput;
    }

    public BigDecimal getCostPer1kOutput() {
        return costPer1kOutput;
    }

    public void setCostPer1kOutput(BigDecimal costPer1kOutput) {
        this.costPer1kOutput = costPer1kOutput;
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
