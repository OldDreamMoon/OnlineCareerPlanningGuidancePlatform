package com.bishe.server.ai.gateway;

import com.bishe.server.ai.gateway.jpa.AiModelRouteJpaRepository;
import com.bishe.server.ai.gateway.jpa.entity.AiModelRouteEntity;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * AI 模型路由仓储。
 */
@Repository
public class AiModelRouteRepository {

    private final AiModelRouteJpaRepository aiModelRouteJpaRepository;

    public AiModelRouteRepository(AiModelRouteJpaRepository aiModelRouteJpaRepository) {
        this.aiModelRouteJpaRepository = aiModelRouteJpaRepository;
    }

    public List<ModelRouteRow> findAllRoutes() {
        return aiModelRouteJpaRepository.findAllOrdered().stream()
                .map(this::toRow)
                .toList();
    }

    public Optional<ModelRouteRow> findRouteById(long id) {
        return aiModelRouteJpaRepository.findById(id).map(this::toRow);
    }

    public Optional<ModelRouteRow> findRouteByCode(String routeCode) {
        return aiModelRouteJpaRepository.findByRouteCode(routeCode).map(this::toRow);
    }

    public long insertRoute(
            String routeCode,
            String taskType,
            String sceneCode,
            Long sceneRoutePolicyId,
            long providerConfigId,
            String modelName,
            int priorityNo,
            int candidateWeight,
            String executionMode,
            boolean enabled,
            BigDecimal temperature,
            String systemPrompt,
            String promptTemplateName,
            String extraConfigJson
    ) {
        AiModelRouteEntity entity = AiModelRouteEntity.create();
        entity.setRouteCode(routeCode);
        entity.setTaskType(taskType);
        entity.setSceneCode(sceneCode);
        entity.setSceneRoutePolicyId(sceneRoutePolicyId);
        entity.setProviderConfigId(providerConfigId);
        entity.setModelName(modelName);
        entity.setPriorityNo(priorityNo);
        entity.setCandidateWeight(candidateWeight);
        entity.setExecutionMode(executionMode);
        entity.setEnabled(enabled);
        entity.setTemperature(temperature);
        entity.setSystemPrompt(systemPrompt);
        entity.setPromptTemplateName(promptTemplateName);
        entity.setExtraConfigJson(extraConfigJson);
        return aiModelRouteJpaRepository.saveAndFlush(entity).getId();
    }

    public boolean updateRoute(
            long id,
            String routeCode,
            String taskType,
            String sceneCode,
            Long sceneRoutePolicyId,
            long providerConfigId,
            String modelName,
            int priorityNo,
            int candidateWeight,
            String executionMode,
            boolean enabled,
            BigDecimal temperature,
            String systemPrompt,
            String promptTemplateName,
            String extraConfigJson
    ) {
        Optional<AiModelRouteEntity> entityOptional = aiModelRouteJpaRepository.findById(id);
        if (entityOptional.isEmpty()) {
            return false;
        }
        AiModelRouteEntity entity = entityOptional.get();
        entity.setRouteCode(routeCode);
        entity.setTaskType(taskType);
        entity.setSceneCode(sceneCode);
        entity.setSceneRoutePolicyId(sceneRoutePolicyId);
        entity.setProviderConfigId(providerConfigId);
        entity.setModelName(modelName);
        entity.setPriorityNo(priorityNo);
        entity.setCandidateWeight(candidateWeight);
        entity.setExecutionMode(executionMode);
        entity.setEnabled(enabled);
        entity.setTemperature(temperature);
        entity.setSystemPrompt(systemPrompt);
        entity.setPromptTemplateName(promptTemplateName);
        entity.setExtraConfigJson(extraConfigJson);
        aiModelRouteJpaRepository.saveAndFlush(entity);
        return true;
    }

    private ModelRouteRow toRow(AiModelRouteEntity entity) {
        return new ModelRouteRow(
                entity.getId(),
                entity.getSceneRoutePolicyId(),
                entity.getRouteCode(),
                entity.getTaskType(),
                entity.getSceneCode(),
                entity.getProviderConfigId(),
                entity.getModelName(),
                entity.getPriorityNo(),
                entity.getCandidateWeight(),
                entity.getExecutionMode(),
                entity.isEnabled(),
                entity.getTemperature(),
                entity.getSystemPrompt(),
                entity.getPromptTemplateName(),
                entity.getExtraConfigJson(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public record ModelRouteRow(
            long id,
            Long sceneRoutePolicyId,
            String routeCode,
            String taskType,
            String sceneCode,
            long providerConfigId,
            String modelName,
            int priorityNo,
            int candidateWeight,
            String executionMode,
            boolean enabled,
            BigDecimal temperature,
            String systemPrompt,
            String promptTemplateName,
            String extraConfigJson,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
