package com.bishe.server.ai.gateway;

import com.bishe.server.ai.gateway.jpa.AiCallLogAdminAnalyticsJpaRepository;
import com.bishe.server.auth.repository.jpa.UserAccountJpaRepository;
import com.bishe.server.auth.repository.jpa.entity.UserAccountEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI 网关后台分析仓储：提供 AI 日志查询、详情与成本看板聚合能力。
 */
@Repository
@Transactional(readOnly = true)
public class AiGatewayAdminAnalyticsRepository {

    private final AiCallLogAdminAnalyticsJpaRepository aiCallLogAdminAnalyticsJpaRepository;
    private final UserAccountJpaRepository userAccountJpaRepository;

    public AiGatewayAdminAnalyticsRepository(
            AiCallLogAdminAnalyticsJpaRepository aiCallLogAdminAnalyticsJpaRepository,
            UserAccountJpaRepository userAccountJpaRepository
    ) {
        this.aiCallLogAdminAnalyticsJpaRepository = aiCallLogAdminAnalyticsJpaRepository;
        this.userAccountJpaRepository = userAccountJpaRepository;
    }

    public List<AiLogRow> findAiLogs(String taskType, String sceneCode, String provider, String status, Long userId, String traceId, int limit, int offset) {
        int safeLimit = Math.max(limit, 0);
        int safeOffset = Math.max(offset, 0);
        if (safeLimit == 0) {
            return List.of();
        }

        String normalizedTaskType = normalizeOptional(taskType);
        String normalizedSceneCode = normalizeOptional(sceneCode);
        String normalizedProvider = normalizeOptional(provider);
        String normalizedStatus = normalizeOptional(status);
        Long normalizedUserId = normalizeUserId(userId);
        String normalizedTraceId = normalizeOptional(traceId);

        List<AiCallLogAdminAnalyticsJpaRepository.AiLogListView> views;
        if (safeOffset == 0 || safeOffset % safeLimit == 0) {
            views = aiCallLogAdminAnalyticsJpaRepository.findAiLogs(
                    normalizedTaskType,
                    normalizedSceneCode,
                    normalizedProvider,
                    normalizedStatus,
                    normalizedUserId,
                    normalizedTraceId,
                    PageRequest.of(safeOffset / safeLimit, safeLimit)
            );
        } else {
            views = aiCallLogAdminAnalyticsJpaRepository.findAiLogs(
                    normalizedTaskType,
                    normalizedSceneCode,
                    normalizedProvider,
                    normalizedStatus,
                    normalizedUserId,
                    normalizedTraceId,
                    PageRequest.of(0, safeLimit + safeOffset)
            ).stream().skip(safeOffset).limit(safeLimit).toList();
        }

        Map<Long, UserAccountEntity> usersById = loadUsersById(views.stream()
                .map(AiCallLogAdminAnalyticsJpaRepository.AiLogListView::getUserId)
                .toList());
        return views.stream()
                .map(view -> toAiLogRow(view, usersById.get(view.getUserId())))
                .toList();
    }

    public Optional<AiLogDetailRow> findAiLogDetail(long logId) {
        return aiCallLogAdminAnalyticsJpaRepository.findAiLogDetail(logId)
                .map(view -> toAiLogDetailRow(
                        view,
                        view.getUserId() == null ? null : userAccountJpaRepository.findById(view.getUserId()).orElse(null)
                ));
    }

    public long countAiLogs(String taskType, String sceneCode, String provider, String status, Long userId, String traceId) {
        return aiCallLogAdminAnalyticsJpaRepository.countAiLogs(
                normalizeOptional(taskType),
                normalizeOptional(sceneCode),
                normalizeOptional(provider),
                normalizeOptional(status),
                normalizeUserId(userId),
                normalizeOptional(traceId)
        );
    }

    public OverviewRow summarizeOverview(Timestamp startAt, Timestamp endAt) {
        AiCallLogAdminAnalyticsJpaRepository.OverviewView view = aiCallLogAdminAnalyticsJpaRepository.summarizeOverview(
                toInstant(startAt),
                toInstant(endAt)
        );
        if (view == null) {
            return new OverviewRow(0L, BigDecimal.ZERO);
        }
        return new OverviewRow(view.getTotalCalls(), defaultCost(view.getTotalCost()));
    }

    public List<HourlyTrafficLogRow> findTrafficLogs(Timestamp startAt, Timestamp endAt) {
        return aiCallLogAdminAnalyticsJpaRepository.findTrafficLogs(toInstant(startAt), toInstant(endAt)).stream()
                .map(view -> new HourlyTrafficLogRow(view.getCreatedAt(), view.getStatus()))
                .toList();
    }

    public List<ProviderRuntimeLogRow> findProviderRuntimeLogs(Timestamp startAt, Timestamp endAt) {
        return aiCallLogAdminAnalyticsJpaRepository.findProviderRuntimeLogs(toInstant(startAt), toInstant(endAt)).stream()
                .map(view -> new ProviderRuntimeLogRow(
                        view.getProvider(),
                        view.getStatus(),
                        view.getLatencyMs(),
                        view.getCreatedAt()
                ))
                .toList();
    }

    public List<GroupedMetricRow> summarizeByModel(Timestamp startAt, Timestamp endAt) {
        return aiCallLogAdminAnalyticsJpaRepository.summarizeByModel(
                        toInstant(startAt),
                        toInstant(endAt),
                        PageRequest.of(0, 10)
                ).stream()
                .map(this::toGroupedMetricRow)
                .toList();
    }

    public List<GroupedMetricRow> summarizeByProvider(Timestamp startAt, Timestamp endAt) {
        return aiCallLogAdminAnalyticsJpaRepository.summarizeByProvider(
                        toInstant(startAt),
                        toInstant(endAt),
                        PageRequest.of(0, 10)
                ).stream()
                .map(this::toGroupedMetricRow)
                .toList();
    }

    public List<GroupedMetricRow> summarizeByTaskType(Timestamp startAt, Timestamp endAt) {
        return aiCallLogAdminAnalyticsJpaRepository.summarizeByTaskType(
                        toInstant(startAt),
                        toInstant(endAt),
                        PageRequest.of(0, 10)
                ).stream()
                .map(this::toGroupedMetricRow)
                .toList();
    }

    public List<GroupedMetricRow> summarizeByTier(Timestamp startAt, Timestamp endAt) {
        return aiCallLogAdminAnalyticsJpaRepository.summarizeByTier(
                        toInstant(startAt),
                        toInstant(endAt),
                        PageRequest.of(0, 10)
                ).stream()
                .map(this::toGroupedMetricRow)
                .toList();
    }

    public List<UserCostRow> summarizeTopUsers(Timestamp startAt, Timestamp endAt, int limit) {
        int safeLimit = Math.max(limit, 0);
        if (safeLimit == 0) {
            return List.of();
        }
        List<AiCallLogAdminAnalyticsJpaRepository.UserCostAggregateView> views = aiCallLogAdminAnalyticsJpaRepository.summarizeTopUsers(
                toInstant(startAt),
                toInstant(endAt),
                PageRequest.of(0, safeLimit)
        );
        Map<Long, UserAccountEntity> usersById = loadUsersById(views.stream()
                .map(AiCallLogAdminAnalyticsJpaRepository.UserCostAggregateView::getUserId)
                .toList());
        return views.stream()
                .map(view -> toUserCostRow(view, usersById.get(view.getUserId())))
                .toList();
    }

    public List<SceneMetricRow> summarizeByScene(Timestamp startAt, Timestamp endAt, int limit) {
        int safeLimit = Math.max(limit, 0);
        if (safeLimit == 0) {
            return List.of();
        }
        return aiCallLogAdminAnalyticsJpaRepository.summarizeByScene(
                        toInstant(startAt),
                        toInstant(endAt),
                        PageRequest.of(0, safeLimit)
                ).stream()
                .map(this::toSceneMetricRow)
                .toList();
    }

    private Map<Long, UserAccountEntity> loadUsersById(Collection<Long> userIds) {
        List<Long> ids = userIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userAccountJpaRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(UserAccountEntity::getId, Function.identity(), (left, right) -> left));
    }

    private AiLogRow toAiLogRow(
            AiCallLogAdminAnalyticsJpaRepository.AiLogListView view,
            UserAccountEntity user
    ) {
        return new AiLogRow(
                defaultLong(view.getId()),
                view.getTraceId(),
                defaultLong(view.getUserId()),
                userEmail(user),
                userDisplayName(user),
                view.getTaskType(),
                view.getSceneCode(),
                view.getRouteCode(),
                view.getRoutePolicyCode(),
                view.getProvider(),
                view.getModel(),
                view.getStatus(),
                view.getErrorCode(),
                view.getLatencyMs(),
                view.getRequestTokens(),
                view.getResponseTokens(),
                view.getTotalTokens(),
                view.getThoughtsTokens(),
                view.getReasoningEffort(),
                view.getThinkingBudget(),
                view.getThinkingLevel(),
                defaultCost(view.getEstimatedCost()),
                view.getChargedPoints(),
                view.getQuotaWeight(),
                view.getResultSummary(),
                view.getUserTier(),
                view.getCreatedAt()
        );
    }

    private AiLogDetailRow toAiLogDetailRow(
            AiCallLogAdminAnalyticsJpaRepository.AiLogDetailView view,
            UserAccountEntity user
    ) {
        return new AiLogDetailRow(
                defaultLong(view.getId()),
                view.getTraceId(),
                defaultLong(view.getUserId()),
                userEmail(user),
                userDisplayName(user),
                view.getTaskType(),
                view.getSceneCode(),
                view.getRouteCode(),
                view.getRoutePolicyCode(),
                view.getProvider(),
                view.getModel(),
                view.getStatus(),
                view.getErrorCode(),
                view.getLatencyMs(),
                view.getRequestTokens(),
                view.getResponseTokens(),
                view.getTotalTokens(),
                view.getThoughtsTokens(),
                view.getReasoningEffort(),
                view.getThinkingBudget(),
                view.getThinkingLevel(),
                defaultCost(view.getEstimatedCost()),
                view.getChargedPoints(),
                view.getQuotaWeight(),
                view.getResultSummary(),
                view.getResultPayloadJson(),
                view.getUserTier(),
                view.getCreatedAt()
        );
    }

    private GroupedMetricRow toGroupedMetricRow(AiCallLogAdminAnalyticsJpaRepository.GroupedMetricView view) {
        return new GroupedMetricRow(
                view.getName(),
                view.getCalls(),
                defaultCost(view.getCost())
        );
    }

    private UserCostRow toUserCostRow(
            AiCallLogAdminAnalyticsJpaRepository.UserCostAggregateView view,
            UserAccountEntity user
    ) {
        return new UserCostRow(
                defaultLong(view.getUserId()),
                userEmail(user),
                userDisplayName(user),
                view.getCalls(),
                defaultCost(view.getCost())
        );
    }

    private SceneMetricRow toSceneMetricRow(AiCallLogAdminAnalyticsJpaRepository.SceneMetricView view) {
        return new SceneMetricRow(
                view.getTaskType(),
                view.getSceneCode(),
                view.getCalls(),
                view.getSuccessCalls(),
                roundLatency(view.getAvgLatencyMs()),
                defaultCost(view.getTotalCost()),
                view.getLastCallAt()
        );
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Long normalizeUserId(Long userId) {
        return userId != null && userId > 0 ? userId : null;
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal defaultCost(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private long roundLatency(Double value) {
        return value == null ? 0L : Math.round(value);
    }

    private String userEmail(UserAccountEntity user) {
        return user == null ? "" : user.getEmail();
    }

    private String userDisplayName(UserAccountEntity user) {
        return user == null ? "" : user.getDisplayName();
    }

    public record AiLogRow(
            long id,
            String traceId,
            long userId,
            String userEmail,
            String userDisplayName,
            String taskType,
            String sceneCode,
            String routeCode,
            String routePolicyCode,
            String provider,
            String model,
            String status,
            String errorCode,
            long latencyMs,
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
            String userTier,
            Instant createdAt
    ) {
    }

    public record AiLogDetailRow(
            long id,
            String traceId,
            long userId,
            String userEmail,
            String userDisplayName,
            String taskType,
            String sceneCode,
            String routeCode,
            String routePolicyCode,
            String provider,
            String model,
            String status,
            String errorCode,
            long latencyMs,
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
            String userTier,
            Instant createdAt
    ) {
    }

    public record HourlyTrafficLogRow(
            Instant createdAt,
            String status
    ) {
    }

    public record ProviderRuntimeLogRow(
            String provider,
            String status,
            long latencyMs,
            Instant createdAt
    ) {
    }

    public record SceneMetricRow(
            String taskType,
            String sceneCode,
            long calls,
            long successCalls,
            long avgLatencyMs,
            BigDecimal totalCost,
            Instant lastCallAt
    ) {
    }

    public record GroupedMetricRow(String name, long calls, BigDecimal cost) {
    }

    public record UserCostRow(long userId, String userEmail, String userDisplayName, long calls, BigDecimal cost) {
    }

    public record OverviewRow(long totalCalls, BigDecimal totalCost) {
    }
}
