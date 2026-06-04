package com.bishe.server.ai.quota;

import com.bishe.server.ai.quota.jpa.AiQuotaPolicyJpaRepository;
import com.bishe.server.ai.quota.jpa.entity.AiQuotaPolicyEntity;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * AI 配额策略仓储。
 */
@Repository
public class AiQuotaPolicyRepository {

    private final AiQuotaPolicyJpaRepository aiQuotaPolicyJpaRepository;

    public AiQuotaPolicyRepository(AiQuotaPolicyJpaRepository aiQuotaPolicyJpaRepository) {
        this.aiQuotaPolicyJpaRepository = aiQuotaPolicyJpaRepository;
    }

    public Optional<QuotaPolicyRow> findPolicy(String tier, String taskType) {
        return findPolicy(tier, taskType, null);
    }

    public Optional<QuotaPolicyRow> findPolicy(String tier, String taskType, String sceneCode) {
        String normalizedSceneCode = normalizeSceneCode(sceneCode);
        if (normalizedSceneCode != null) {
            Optional<QuotaPolicyRow> exact = aiQuotaPolicyJpaRepository
                    .findFirstByTierAndTaskTypeAndSceneCode(tier, taskType, normalizedSceneCode)
                    .map(this::toRow);
            if (exact.isPresent()) {
                return exact;
            }
        }
        return aiQuotaPolicyJpaRepository.findFirstByTierAndTaskTypeAndSceneCodeIsNull(tier, taskType)
                .map(this::toRow);
    }

    public Optional<QuotaPolicyRow> findPolicyById(long id) {
        return aiQuotaPolicyJpaRepository.findById(id).map(this::toRow);
    }

    public List<QuotaPolicyRow> findTaskFallbackPoliciesByTier(String tier) {
        return aiQuotaPolicyJpaRepository.findAllByTierAndSceneCodeIsNullOrderByTaskTypeAsc(tier).stream()
                .map(this::toRow)
                .toList();
    }

    public List<QuotaPolicyRow> findAllPolicies() {
        return aiQuotaPolicyJpaRepository.findAllOrdered().stream()
                .map(this::toRow)
                .toList();
    }

    public boolean updatePolicy(
            long id,
            int dailyFreeLimit,
            int pointsPerCall,
            int dailyMaxLimit,
            String modelPreference,
            int maxInputTokens
    ) {
        Optional<AiQuotaPolicyEntity> entityOptional = aiQuotaPolicyJpaRepository.findById(id);
        if (entityOptional.isEmpty()) {
            return false;
        }
        AiQuotaPolicyEntity entity = entityOptional.get();
        entity.setDailyFreeLimit(dailyFreeLimit);
        entity.setPointsPerCall(pointsPerCall);
        entity.setDailyMaxLimit(dailyMaxLimit);
        entity.setModelPreference(modelPreference);
        entity.setMaxInputTokens(maxInputTokens);
        aiQuotaPolicyJpaRepository.saveAndFlush(entity);
        return true;
    }

    private QuotaPolicyRow toRow(AiQuotaPolicyEntity entity) {
        return new QuotaPolicyRow(
                entity.getId(),
                entity.getTier(),
                entity.getTaskType(),
                entity.getSceneCode(),
                entity.getDailyFreeLimit(),
                entity.getPointsPerCall(),
                entity.getDailyMaxLimit(),
                entity.getModelPreference(),
                entity.getMaxInputTokens(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String normalizeSceneCode(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        return rawValue.trim().toUpperCase(Locale.ROOT);
    }

    public record QuotaPolicyRow(
            long id,
            String tier,
            String taskType,
            String sceneCode,
            int dailyFreeLimit,
            int pointsPerCall,
            int dailyMaxLimit,
            String modelPreference,
            int maxInputTokens,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
