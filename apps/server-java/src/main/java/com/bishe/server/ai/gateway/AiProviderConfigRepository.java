package com.bishe.server.ai.gateway;

import com.bishe.server.ai.gateway.jpa.AiProviderConfigJpaRepository;
import com.bishe.server.ai.gateway.jpa.entity.AiProviderConfigEntity;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * AI 提供商配置仓储。
 */
@Repository
public class AiProviderConfigRepository {

    private final AiProviderConfigJpaRepository aiProviderConfigJpaRepository;

    public AiProviderConfigRepository(AiProviderConfigJpaRepository aiProviderConfigJpaRepository) {
        this.aiProviderConfigJpaRepository = aiProviderConfigJpaRepository;
    }

    public List<ProviderConfigRow> findAllProviders() {
        return aiProviderConfigJpaRepository.findAllByOrderByEnabledDescProviderCodeAscIdAsc().stream()
                .map(this::toRow)
                .toList();
    }

    public Optional<ProviderConfigRow> findProviderById(long id) {
        return aiProviderConfigJpaRepository.findById(id).map(this::toRow);
    }

    public Optional<ProviderConfigRow> findProviderByCode(String providerCode) {
        return aiProviderConfigJpaRepository.findByProviderCode(providerCode).map(this::toRow);
    }

    public long insertProvider(
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
        AiProviderConfigEntity entity = AiProviderConfigEntity.create(
                providerCode,
                providerType,
                displayName,
                baseUrl,
                apiKeyCiphertext,
                apiKeyMasked,
                enabled,
                timeoutMs,
                maxRetries,
                costPer1kInput,
                costPer1kOutput,
                extraConfigJson
        );
        return aiProviderConfigJpaRepository.saveAndFlush(entity).getId();
    }

    public boolean updateProvider(
            long id,
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
        Optional<AiProviderConfigEntity> entityOptional = aiProviderConfigJpaRepository.findById(id);
        if (entityOptional.isEmpty()) {
            return false;
        }
        AiProviderConfigEntity entity = entityOptional.get();
        entity.setProviderCode(providerCode);
        entity.setProviderType(providerType);
        entity.setDisplayName(displayName);
        entity.setBaseUrl(baseUrl);
        entity.setApiKeyCiphertext(apiKeyCiphertext);
        entity.setApiKeyMasked(apiKeyMasked);
        entity.setEnabled(enabled);
        entity.setTimeoutMs(timeoutMs);
        entity.setMaxRetries(maxRetries);
        entity.setCostPer1kInput(costPer1kInput);
        entity.setCostPer1kOutput(costPer1kOutput);
        entity.setExtraConfigJson(extraConfigJson);
        aiProviderConfigJpaRepository.saveAndFlush(entity);
        return true;
    }

    private ProviderConfigRow toRow(AiProviderConfigEntity entity) {
        return new ProviderConfigRow(
                entity.getId(),
                entity.getProviderCode(),
                entity.getProviderType(),
                entity.getDisplayName(),
                entity.getBaseUrl(),
                entity.getApiKeyCiphertext(),
                entity.getApiKeyMasked(),
                entity.isEnabled(),
                entity.getTimeoutMs(),
                entity.getMaxRetries(),
                entity.getCostPer1kInput(),
                entity.getCostPer1kOutput(),
                entity.getExtraConfigJson(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public record ProviderConfigRow(
            long id,
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
            String extraConfigJson,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
