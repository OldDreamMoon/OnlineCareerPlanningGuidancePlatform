package com.bishe.server.ai.quota;

import com.bishe.server.ai.quota.jpa.AiCallLogJpaRepository;
import com.bishe.server.ai.quota.jpa.entity.AiCallLogEntity;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * AI 调用日志仓储。
 */
@Repository
public class AiQuotaRepository {

    private final AiCallLogJpaRepository aiCallLogJpaRepository;

    public AiQuotaRepository(AiCallLogJpaRepository aiCallLogJpaRepository) {
        this.aiCallLogJpaRepository = aiCallLogJpaRepository;
    }

    public int countSuccessfulCallsToday(long userId, String taskType, String sceneCode, LocalDate targetDate) {
        ZoneId zoneId = ZoneId.systemDefault();
        String normalizedSceneCode = normalizeSceneCode(sceneCode);
        Long count = aiCallLogJpaRepository.sumSuccessfulQuotaWeightForDay(
                userId,
                taskType,
                normalizedSceneCode,
                targetDate.atStartOfDay(zoneId).toInstant(),
                targetDate.plusDays(1).atStartOfDay(zoneId).minusNanos(1).toInstant()
        );
        return count == null ? 0 : Math.toIntExact(count);
    }

    public long insertCallLog(
            String traceId,
            long userId,
            String taskType,
            String sceneCode,
            String provider,
            String model,
            String routeCode,
            String routePolicyCode,
            long latencyMs,
            String status,
            String errorCode,
            int requestTokens,
            int responseTokens,
            int totalTokens,
            int thoughtsTokens,
            String reasoningEffort,
            Integer thinkingBudget,
            String thinkingLevel,
            BigDecimal estimatedCost,
            int chargedPoints,
            int quotaWeight,
            String resultSummary,
            String resultPayloadJson,
            String userTier
    ) {
        AiCallLogEntity entity = AiCallLogEntity.create(
                traceId,
                userId,
                taskType,
                sceneCode,
                provider,
                model,
                routeCode,
                routePolicyCode,
                latencyMs,
                status,
                errorCode,
                requestTokens,
                responseTokens,
                totalTokens,
                thoughtsTokens,
                reasoningEffort,
                thinkingBudget,
                thinkingLevel,
                estimatedCost,
                chargedPoints,
                quotaWeight,
                resultSummary,
                resultPayloadJson,
                userTier
        );
        return aiCallLogJpaRepository.saveAndFlush(entity).getId();
    }

    private String normalizeSceneCode(String sceneCode) {
        if (!StringUtils.hasText(sceneCode)) {
            return null;
        }
        return sceneCode.trim().toUpperCase();
    }

}
