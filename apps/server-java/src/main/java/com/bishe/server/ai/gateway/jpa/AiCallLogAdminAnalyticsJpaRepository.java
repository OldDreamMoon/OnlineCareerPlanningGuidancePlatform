package com.bishe.server.ai.gateway.jpa;

import com.bishe.server.ai.quota.jpa.entity.AiCallLogEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * AI 管理后台日志分析 JPA 仓储。
 */
public interface AiCallLogAdminAnalyticsJpaRepository extends JpaRepository<AiCallLogEntity, Long> {

    interface AiLogListView {
        Long getId();

        String getTraceId();

        Long getUserId();

        String getTaskType();

        String getSceneCode();

        String getRouteCode();

        String getRoutePolicyCode();

        String getProvider();

        String getModel();

        String getStatus();

        String getErrorCode();

        long getLatencyMs();

        int getRequestTokens();

        int getResponseTokens();

        int getTotalTokens();

        int getThoughtsTokens();

        String getReasoningEffort();

        Integer getThinkingBudget();

        String getThinkingLevel();

        BigDecimal getEstimatedCost();

        int getChargedPoints();

        int getQuotaWeight();

        String getResultSummary();

        String getUserTier();

        Instant getCreatedAt();
    }

    interface AiLogDetailView extends AiLogListView {
        String getResultPayloadJson();
    }

    interface GroupedMetricView {
        String getName();

        long getCalls();

        BigDecimal getCost();
    }

    interface UserCostAggregateView {
        Long getUserId();

        long getCalls();

        BigDecimal getCost();
    }

    interface HourlyTrafficLogView {
        Instant getCreatedAt();

        String getStatus();
    }

    interface ProviderRuntimeLogView {
        String getProvider();

        String getStatus();

        long getLatencyMs();

        Instant getCreatedAt();
    }

    interface SceneMetricView {
        String getTaskType();

        String getSceneCode();

        long getCalls();

        long getSuccessCalls();

        Double getAvgLatencyMs();

        BigDecimal getTotalCost();

        Instant getLastCallAt();
    }

    interface OverviewView {
        long getTotalCalls();

        BigDecimal getTotalCost();
    }

    @Query("""
            select log.id as id,
                   log.traceId as traceId,
                   log.userId as userId,
                   log.taskType as taskType,
                   log.sceneCode as sceneCode,
                   log.routeCode as routeCode,
                   log.routePolicyCode as routePolicyCode,
                   log.provider as provider,
                   log.model as model,
                   log.status as status,
                   log.errorCode as errorCode,
                   log.latencyMs as latencyMs,
                   log.requestTokens as requestTokens,
                   log.responseTokens as responseTokens,
                   log.totalTokens as totalTokens,
                   log.thoughtsTokens as thoughtsTokens,
                   log.reasoningEffort as reasoningEffort,
                   log.thinkingBudget as thinkingBudget,
                   log.thinkingLevel as thinkingLevel,
                   log.estimatedCost as estimatedCost,
                   log.chargedPoints as chargedPoints,
                   log.quotaWeight as quotaWeight,
                   log.resultSummary as resultSummary,
                   log.userTier as userTier,
                   log.createdAt as createdAt
              from AiCallLogEntity log
             where (:taskType is null or log.taskType = :taskType)
               and (:sceneCode is null or log.sceneCode = :sceneCode)
               and (:provider is null or log.provider = :provider)
               and (:status is null or log.status = :status)
               and (:userId is null or log.userId = :userId)
               and (:traceId is null or log.traceId = :traceId)
          order by log.createdAt desc, log.id desc
            """)
    List<AiLogListView> findAiLogs(
            @Param("taskType") String taskType,
            @Param("sceneCode") String sceneCode,
            @Param("provider") String provider,
            @Param("status") String status,
            @Param("userId") Long userId,
            @Param("traceId") String traceId,
            Pageable pageable
    );

    @Query("""
            select count(log)
              from AiCallLogEntity log
             where (:taskType is null or log.taskType = :taskType)
               and (:sceneCode is null or log.sceneCode = :sceneCode)
               and (:provider is null or log.provider = :provider)
               and (:status is null or log.status = :status)
               and (:userId is null or log.userId = :userId)
               and (:traceId is null or log.traceId = :traceId)
            """)
    long countAiLogs(
            @Param("taskType") String taskType,
            @Param("sceneCode") String sceneCode,
            @Param("provider") String provider,
            @Param("status") String status,
            @Param("userId") Long userId,
            @Param("traceId") String traceId
    );

    @Query("""
            select log.id as id,
                   log.traceId as traceId,
                   log.userId as userId,
                   log.taskType as taskType,
                   log.sceneCode as sceneCode,
                   log.routeCode as routeCode,
                   log.routePolicyCode as routePolicyCode,
                   log.provider as provider,
                   log.model as model,
                   log.status as status,
                   log.errorCode as errorCode,
                   log.latencyMs as latencyMs,
                   log.requestTokens as requestTokens,
                   log.responseTokens as responseTokens,
                   log.totalTokens as totalTokens,
                   log.thoughtsTokens as thoughtsTokens,
                   log.reasoningEffort as reasoningEffort,
                   log.thinkingBudget as thinkingBudget,
                   log.thinkingLevel as thinkingLevel,
                   log.estimatedCost as estimatedCost,
                   log.chargedPoints as chargedPoints,
                   log.quotaWeight as quotaWeight,
                   log.resultSummary as resultSummary,
                   log.resultPayloadJson as resultPayloadJson,
                   log.userTier as userTier,
                   log.createdAt as createdAt
              from AiCallLogEntity log
             where log.id = :logId
            """)
    Optional<AiLogDetailView> findAiLogDetail(@Param("logId") long logId);

    @Query("""
            select count(log) as totalCalls,
                   coalesce(sum(log.estimatedCost), 0) as totalCost
              from AiCallLogEntity log
             where log.createdAt >= :startAt
               and log.createdAt <= :endAt
            """)
    OverviewView summarizeOverview(@Param("startAt") Instant startAt, @Param("endAt") Instant endAt);

    @Query("""
            select log.createdAt as createdAt,
                   log.status as status
              from AiCallLogEntity log
             where log.createdAt >= :startAt
               and log.createdAt <= :endAt
          order by log.createdAt asc, log.id asc
            """)
    List<HourlyTrafficLogView> findTrafficLogs(@Param("startAt") Instant startAt, @Param("endAt") Instant endAt);

    @Query("""
            select log.provider as provider,
                   log.status as status,
                   log.latencyMs as latencyMs,
                   log.createdAt as createdAt
              from AiCallLogEntity log
             where log.createdAt >= :startAt
               and log.createdAt <= :endAt
          order by log.createdAt asc, log.id asc
            """)
    List<ProviderRuntimeLogView> findProviderRuntimeLogs(@Param("startAt") Instant startAt, @Param("endAt") Instant endAt);

    @Query("""
            select log.model as name,
                   count(log) as calls,
                   coalesce(sum(log.estimatedCost), 0) as cost
              from AiCallLogEntity log
             where log.createdAt >= :startAt
               and log.createdAt <= :endAt
          group by log.model
          order by coalesce(sum(log.estimatedCost), 0) desc, count(log) desc, log.model asc
            """)
    List<GroupedMetricView> summarizeByModel(@Param("startAt") Instant startAt, @Param("endAt") Instant endAt, Pageable pageable);

    @Query("""
            select log.provider as name,
                   count(log) as calls,
                   coalesce(sum(log.estimatedCost), 0) as cost
              from AiCallLogEntity log
             where log.createdAt >= :startAt
               and log.createdAt <= :endAt
          group by log.provider
          order by coalesce(sum(log.estimatedCost), 0) desc, count(log) desc, log.provider asc
            """)
    List<GroupedMetricView> summarizeByProvider(@Param("startAt") Instant startAt, @Param("endAt") Instant endAt, Pageable pageable);

    @Query("""
            select log.taskType as name,
                   count(log) as calls,
                   coalesce(sum(log.estimatedCost), 0) as cost
              from AiCallLogEntity log
             where log.createdAt >= :startAt
               and log.createdAt <= :endAt
          group by log.taskType
          order by coalesce(sum(log.estimatedCost), 0) desc, count(log) desc, log.taskType asc
            """)
    List<GroupedMetricView> summarizeByTaskType(@Param("startAt") Instant startAt, @Param("endAt") Instant endAt, Pageable pageable);

    @Query("""
            select log.userTier as name,
                   count(log) as calls,
                   coalesce(sum(log.estimatedCost), 0) as cost
              from AiCallLogEntity log
             where log.createdAt >= :startAt
               and log.createdAt <= :endAt
          group by log.userTier
          order by coalesce(sum(log.estimatedCost), 0) desc, count(log) desc, log.userTier asc
            """)
    List<GroupedMetricView> summarizeByTier(@Param("startAt") Instant startAt, @Param("endAt") Instant endAt, Pageable pageable);

    @Query("""
            select log.userId as userId,
                   count(log) as calls,
                   coalesce(sum(log.estimatedCost), 0) as cost
              from AiCallLogEntity log
             where log.createdAt >= :startAt
               and log.createdAt <= :endAt
          group by log.userId
          order by coalesce(sum(log.estimatedCost), 0) desc, count(log) desc, log.userId asc
            """)
    List<UserCostAggregateView> summarizeTopUsers(
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            Pageable pageable
    );

    @Query("""
            select log.taskType as taskType,
                   log.sceneCode as sceneCode,
                   count(log) as calls,
                   sum(case when log.status = 'SUCCESS' then 1 else 0 end) as successCalls,
                   avg(log.latencyMs) as avgLatencyMs,
                   coalesce(sum(log.estimatedCost), 0) as totalCost,
                   max(log.createdAt) as lastCallAt
              from AiCallLogEntity log
             where log.createdAt >= :startAt
               and log.createdAt <= :endAt
               and log.sceneCode is not null
               and log.sceneCode <> ''
          group by log.taskType, log.sceneCode
          order by count(log) desc, coalesce(sum(log.estimatedCost), 0) desc, log.taskType asc, log.sceneCode asc
            """)
    List<SceneMetricView> summarizeByScene(@Param("startAt") Instant startAt, @Param("endAt") Instant endAt, Pageable pageable);
}
