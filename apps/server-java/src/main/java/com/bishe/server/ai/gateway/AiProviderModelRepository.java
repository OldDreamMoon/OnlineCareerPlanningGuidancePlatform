package com.bishe.server.ai.gateway;

import com.bishe.server.ai.gateway.jpa.AiProviderModelJpaRepository;
import com.bishe.server.ai.gateway.jpa.entity.AiProviderModelEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * AI 提供商模型配置仓储。
 */
@Repository
public class AiProviderModelRepository {

    private final AiProviderModelJpaRepository aiProviderModelJpaRepository;

    public AiProviderModelRepository(AiProviderModelJpaRepository aiProviderModelJpaRepository) {
        this.aiProviderModelJpaRepository = aiProviderModelJpaRepository;
    }

    public List<ProviderModelRow> findAllProviderModels() {
        return aiProviderModelJpaRepository.findAllByOrderByProviderConfigIdAscEnabledDescModelCodeAscIdAsc().stream()
                .map(this::toRow)
                .toList();
    }

    public List<ProviderModelRow> findProviderModelsByProviderId(long providerConfigId) {
        return aiProviderModelJpaRepository.findByProviderConfigIdOrderByEnabledDescModelCodeAscIdAsc(providerConfigId).stream()
                .map(this::toRow)
                .toList();
    }

    public List<ProviderModelRow> findProviderModelsByProviderIds(List<Long> providerIds) {
        if (providerIds == null || providerIds.isEmpty()) {
            return List.of();
        }
        return aiProviderModelJpaRepository.findByProviderConfigIdInOrderByProviderConfigIdAscEnabledDescModelCodeAscIdAsc(providerIds).stream()
                .map(this::toRow)
                .toList();
    }

    @Transactional
    public void replaceProviderModels(long providerConfigId, List<ProviderModelMutation> models) {
        aiProviderModelJpaRepository.deleteAllByProviderConfigId(providerConfigId);
        if (models == null || models.isEmpty()) {
            return;
        }
        List<AiProviderModelEntity> entities = models.stream()
                .map(model -> toEntity(providerConfigId, model))
                .toList();
        aiProviderModelJpaRepository.saveAllAndFlush(entities);
    }

    private ProviderModelRow toRow(AiProviderModelEntity entity) {
        return new ProviderModelRow(
                entity.getId(),
                entity.getProviderConfigId(),
                entity.getModelCode(),
                entity.getDisplayName(),
                entity.isEnabled(),
                entity.getInputCostPer1k(),
                entity.getOutputCostPer1k(),
                entity.getContextWindow(),
                entity.getMaxOutputTokens(),
                entity.getSupportedTaskTypesJson(),
                entity.getNotes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private AiProviderModelEntity toEntity(long providerConfigId, ProviderModelMutation model) {
        AiProviderModelEntity entity = AiProviderModelEntity.create(providerConfigId);
        entity.setModelCode(model.modelCode());
        entity.setDisplayName(model.displayName());
        entity.setEnabled(model.enabled());
        entity.setInputCostPer1k(model.inputCostPer1k() == null ? BigDecimal.ZERO : model.inputCostPer1k());
        entity.setOutputCostPer1k(model.outputCostPer1k() == null ? BigDecimal.ZERO : model.outputCostPer1k());
        entity.setContextWindow(model.contextWindow());
        entity.setMaxOutputTokens(model.maxOutputTokens());
        entity.setSupportedTaskTypesJson(model.supportedTaskTypesJson());
        entity.setNotes(model.notes());
        return entity;
    }

    public record ProviderModelRow(
            long id,
            long providerConfigId,
            String modelCode,
            String displayName,
            boolean enabled,
            BigDecimal inputCostPer1k,
            BigDecimal outputCostPer1k,
            Integer contextWindow,
            Integer maxOutputTokens,
            String supportedTaskTypesJson,
            String notes,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record ProviderModelMutation(
            String modelCode,
            String displayName,
            boolean enabled,
            BigDecimal inputCostPer1k,
            BigDecimal outputCostPer1k,
            Integer contextWindow,
            Integer maxOutputTokens,
            String supportedTaskTypesJson,
            String notes
    ) {
    }
}
