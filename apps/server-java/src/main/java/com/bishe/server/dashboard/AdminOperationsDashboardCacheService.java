package com.bishe.server.dashboard;

import com.bishe.server.common.TimePayloads;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

/**
 * 管理运营看板缓存：仅缓存固定 period 维度的短 TTL 聚合快照，降低后台高频刷新的多表聚合成本。
 */
@Service
public class AdminOperationsDashboardCacheService {

    private static final Logger log = LoggerFactory.getLogger(AdminOperationsDashboardCacheService.class);
    private static final String KEY_PREFIX = "admin:dashboard:operations:";
    private static final List<String> SUPPORTED_PERIODS = List.of("today", "week", "month");
    private static final Duration TTL = Duration.ofMinutes(1);

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public AdminOperationsDashboardCacheService(
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider
    ) {
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    public AdminDashboardService.OperationsDashboardPayload getOperationsDashboard(
            String period,
            Supplier<AdminDashboardService.OperationsDashboardPayload> databaseLoader
    ) {
        if (databaseLoader == null) {
            return null;
        }
        String normalizedPeriod = normalizePeriod(period);
        if (stringRedisTemplate == null) {
            // Redis 不可用时仍直接走数据库聚合，后台看板功能不被缓存依赖阻断。
            return sanitize(normalizedPeriod, databaseLoader.get());
        }

        AdminDashboardService.OperationsDashboardPayload cached = readFromRedis(normalizedPeriod);
        if (cached != null) {
            return cached;
        }

        AdminDashboardService.OperationsDashboardPayload loaded = sanitize(normalizedPeriod, databaseLoader.get());
        if (loaded == null) {
            return null;
        }
        writeToRedis(normalizedPeriod, loaded);
        // 写后再读一遍，确认 JSON 序列化后的结构也能被当前代码消费。
        AdminDashboardService.OperationsDashboardPayload refreshed = readFromRedis(normalizedPeriod);
        return refreshed == null ? loaded : refreshed;
    }

    public void evictAllNow() {
        evictAll();
    }

    public void evictAllAfterCommit() {
        runAfterCommit(this::evictAll);
    }

    private AdminDashboardService.OperationsDashboardPayload readFromRedis(String period) {
        try {
            String payload = stringRedisTemplate.opsForValue().get(buildKey(period));
            if (payload == null || payload.isBlank()) {
                return null;
            }
            return sanitize(period, objectMapper.readValue(payload, AdminDashboardService.OperationsDashboardPayload.class));
        } catch (Exception ex) {
            log.debug("read admin operations dashboard cache from redis failed: {}", ex.getMessage());
            return null;
        }
    }

    private void writeToRedis(String period, AdminDashboardService.OperationsDashboardPayload payload) {
        try {
            stringRedisTemplate.opsForValue().set(buildKey(period), objectMapper.writeValueAsString(payload), TTL);
        } catch (Exception ex) {
            log.debug("write admin operations dashboard cache to redis failed: {}", ex.getMessage());
        }
    }

    private void evictAll() {
        if (stringRedisTemplate == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(SUPPORTED_PERIODS.stream().map(this::buildKey).toList());
        } catch (Exception ex) {
            log.debug("evict admin operations dashboard cache from redis failed: {}", ex.getMessage());
        }
    }

    private void runAfterCommit(Runnable action) {
        if (action == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            // 指标缓存失效要等业务事务提交后执行，避免读到尚未提交的中间态。
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    private String buildKey(String period) {
        return KEY_PREFIX + normalizePeriod(period);
    }

    private String normalizePeriod(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return "week";
        }
        String normalized = rawValue.trim().toLowerCase();
        return SUPPORTED_PERIODS.contains(normalized) ? normalized : "week";
    }

    private AdminDashboardService.OperationsDashboardPayload sanitize(
            String fallbackPeriod,
            AdminDashboardService.OperationsDashboardPayload payload
    ) {
        if (payload == null || payload.overview() == null) {
            return null;
        }
        // 缓存层负责兜底非空时间戳和非负指标，页面不再处理脏聚合值。
        Long generatedAt = payload.generatedAt() == null
                ? TimePayloads.toEpochMillis(Instant.now())
                : payload.generatedAt();
        return new AdminDashboardService.OperationsDashboardPayload(
                normalizePeriod(payload.period() == null ? fallbackPeriod : payload.period()),
                generatedAt,
                payload.windowStartAt() == null ? generatedAt : payload.windowStartAt(),
                payload.windowEndAt() == null ? generatedAt : payload.windowEndAt(),
                sanitizeOverview(payload.overview()),
                sanitizeMetric(payload.activationRate(), "激活率", "0.0%"),
                sanitizeMetric(payload.retention7dRate(), "7日留存", "0.0%"),
                sanitizeMetric(payload.consultConversionRate(), "咨询转化率", "0.0%"),
                sanitizeMetric(payload.aiSuccessRate(), "AI 调用成功率", "0.0%"),
                sanitizeMetric(payload.communityAiCoverageRate(), "社区 AI 覆盖率", "0.0%"),
                sanitizeMetric(payload.moderationBlockRate(), "内容审查拦截率", "0.0%"),
                sanitizeDurationMetric(payload.reportHandleMedianHours(), "举报处置时效中位数"),
                sanitizeMetric(payload.portraitCompletenessRate(), "画像完整率", "0.0%"),
                sanitizeMetric(payload.leaderboardCoverageRate(), "社区榜单覆盖率", "0.0%")
        );
    }

    private AdminDashboardService.OverviewPayload sanitizeOverview(AdminDashboardService.OverviewPayload overview) {
        return new AdminDashboardService.OverviewPayload(
                Math.max(overview.newStudents(), 0L),
                Math.max(overview.activatedStudents(), 0L),
                Math.max(overview.activeStudents(), 0L),
                Math.max(overview.paidConsultStudents(), 0L),
                Math.max(overview.aiCalls(), 0L),
                Math.max(overview.aiSuccessCalls(), 0L),
                Math.max(overview.newPosts(), 0L),
                Math.max(overview.aiCoveredPosts(), 0L),
                Math.max(overview.moderationEvents(), 0L),
                Math.max(overview.blockedModerationEvents(), 0L),
                Math.max(overview.closedReports(), 0L),
                Math.max(overview.totalStudents(), 0L),
                Math.max(overview.portraitCompletedStudents(), 0L),
                Math.max(overview.activeStudents7d(), 0L),
                Math.max(overview.leaderboardCoveredStudents7d(), 0L)
        );
    }

    private AdminDashboardService.MetricPayload sanitizeMetric(
            AdminDashboardService.MetricPayload metric,
            String defaultLabel,
            String defaultValue
    ) {
        if (metric == null) {
            return new AdminDashboardService.MetricPayload(defaultLabel, defaultValue, 0L, 0L, "");
        }
        return new AdminDashboardService.MetricPayload(
                metric.label() == null || metric.label().isBlank() ? defaultLabel : metric.label(),
                metric.value() == null || metric.value().isBlank() ? defaultValue : metric.value(),
                Math.max(metric.numerator(), 0L),
                Math.max(metric.denominator(), 0L),
                metric.note() == null ? "" : metric.note()
        );
    }

    private AdminDashboardService.DurationMetricPayload sanitizeDurationMetric(
            AdminDashboardService.DurationMetricPayload metric,
            String defaultLabel
    ) {
        if (metric == null) {
            return new AdminDashboardService.DurationMetricPayload(defaultLabel, "0.0h", 0L, "");
        }
        return new AdminDashboardService.DurationMetricPayload(
                metric.label() == null || metric.label().isBlank() ? defaultLabel : metric.label(),
                metric.value() == null || metric.value().isBlank() ? "0.0h" : metric.value(),
                Math.max(metric.sampleSize(), 0L),
                metric.note() == null ? "" : metric.note()
        );
    }
}
