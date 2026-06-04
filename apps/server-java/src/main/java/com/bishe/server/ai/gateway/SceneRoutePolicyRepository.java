package com.bishe.server.ai.gateway;

import com.bishe.server.ai.gateway.jpa.SceneRoutePolicyJpaRepository;
import com.bishe.server.ai.gateway.jpa.entity.SceneRoutePolicyEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * AI 场景路由策略仓储。
 */
@Repository
public class SceneRoutePolicyRepository {

    private final SceneRoutePolicyJpaRepository sceneRoutePolicyJpaRepository;

    public SceneRoutePolicyRepository(SceneRoutePolicyJpaRepository sceneRoutePolicyJpaRepository) {
        this.sceneRoutePolicyJpaRepository = sceneRoutePolicyJpaRepository;
    }

    public List<SceneRoutePolicyRow> findAllRoutePolicies() {
        return sceneRoutePolicyJpaRepository.findAllOrdered().stream()
                .map(this::toRow)
                .toList();
    }

    public Optional<SceneRoutePolicyRow> findRoutePolicyById(long id) {
        return sceneRoutePolicyJpaRepository.findById(id).map(this::toRow);
    }

    public Optional<SceneRoutePolicyRow> findRoutePolicyByCode(String policyCode) {
        return sceneRoutePolicyJpaRepository.findByPolicyCode(policyCode).map(this::toRow);
    }

    public Optional<SceneRoutePolicyRow> findRoutePolicyBySceneAndTier(String taskType, String sceneCode, String userTier) {
        return sceneRoutePolicyJpaRepository.findByTaskTypeAndSceneCodeAndUserTier(taskType, sceneCode, userTier)
                .map(this::toRow);
    }

    public long insertRoutePolicy(
            String policyCode,
            String taskType,
            String sceneCode,
            String userTier,
            String strategyType,
            boolean enabled,
            String notes,
            String extraConfigJson
    ) {
        SceneRoutePolicyEntity entity = SceneRoutePolicyEntity.create(
                policyCode,
                taskType,
                sceneCode,
                userTier,
                strategyType,
                enabled,
                notes,
                extraConfigJson
        );
        return sceneRoutePolicyJpaRepository.saveAndFlush(entity).getId();
    }

    public boolean updateRoutePolicy(
            long id,
            String policyCode,
            String taskType,
            String sceneCode,
            String userTier,
            String strategyType,
            boolean enabled,
            String notes,
            String extraConfigJson
    ) {
        Optional<SceneRoutePolicyEntity> entityOptional = sceneRoutePolicyJpaRepository.findById(id);
        if (entityOptional.isEmpty()) {
            return false;
        }
        SceneRoutePolicyEntity entity = entityOptional.get();
        entity.setPolicyCode(policyCode);
        entity.setTaskType(taskType);
        entity.setSceneCode(sceneCode);
        entity.setUserTier(userTier);
        entity.setStrategyType(strategyType);
        entity.setEnabled(enabled);
        entity.setNotes(notes);
        entity.setExtraConfigJson(extraConfigJson);
        sceneRoutePolicyJpaRepository.saveAndFlush(entity);
        return true;
    }

    private SceneRoutePolicyRow toRow(SceneRoutePolicyEntity entity) {
        return new SceneRoutePolicyRow(
                entity.getId(),
                entity.getPolicyCode(),
                entity.getTaskType(),
                entity.getSceneCode(),
                entity.getUserTier(),
                entity.getStrategyType(),
                entity.isEnabled(),
                entity.getNotes(),
                entity.getExtraConfigJson(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public record SceneRoutePolicyRow(
            long id,
            String policyCode,
            String taskType,
            String sceneCode,
            String userTier,
            String strategyType,
            boolean enabled,
            String notes,
            String extraConfigJson,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
