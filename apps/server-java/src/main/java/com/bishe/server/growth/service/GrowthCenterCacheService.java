package com.bishe.server.growth.service;

import com.bishe.server.growth.dto.DailyTaskItemResponse;
import com.bishe.server.growth.dto.GrowthCheckinOverviewResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 成长中心聚合缓存：承接签到概览与每日任务摘要。
 */
@Service
public class GrowthCenterCacheService {

    private static final Logger log = LoggerFactory.getLogger(GrowthCenterCacheService.class);
    private static final String CHECKIN_OVERVIEW_KEY_PREFIX = "growth:checkin:overview:";
    private static final String DAILY_TASKS_KEY_PREFIX = "growth:daily-tasks:";
    private static final String POINTS_LEDGER_SUMMARY_KEY_PREFIX = "growth:points-ledger-summary:";
    private static final Duration TTL = Duration.ofSeconds(30);
    private static final TypeReference<GrowthCheckinOverviewResponse> CHECKIN_OVERVIEW_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<DailyTaskItemResponse>> DAILY_TASKS_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public GrowthCenterCacheService(
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider
    ) {
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    public GrowthCheckinOverviewResponse getCheckinOverview(
            long userId,
            LocalDate businessDate,
            Supplier<GrowthCheckinOverviewResponse> databaseLoader
    ) {
        if (userId <= 0 || businessDate == null || databaseLoader == null) {
            return null;
        }
        if (stringRedisTemplate == null) {
            return sanitizeCheckinOverview(databaseLoader.get());
        }

        String key = buildCheckinOverviewKey(userId, businessDate);
        GrowthCheckinOverviewResponse cached = readCheckinOverview(key);
        if (cached != null) {
            return cached;
        }

        GrowthCheckinOverviewResponse loaded = sanitizeCheckinOverview(databaseLoader.get());
        if (loaded == null) {
            return null;
        }
        writeValue(key, loaded);
        GrowthCheckinOverviewResponse refreshed = readCheckinOverview(key);
        return refreshed == null ? loaded : refreshed;
    }

    public List<DailyTaskItemResponse> getDailyTasks(
            long userId,
            LocalDate businessDate,
            Supplier<List<DailyTaskItemResponse>> databaseLoader
    ) {
        if (userId <= 0 || businessDate == null || databaseLoader == null) {
            return List.of();
        }
        if (stringRedisTemplate == null) {
            return sanitizeDailyTasks(databaseLoader.get());
        }

        String key = buildDailyTasksKey(userId, businessDate);
        List<DailyTaskItemResponse> cached = readDailyTasks(key);
        if (!cached.isEmpty()) {
            return cached;
        }

        List<DailyTaskItemResponse> loaded = sanitizeDailyTasks(databaseLoader.get());
        if (loaded.isEmpty()) {
            return List.of();
        }
        writeValue(key, loaded);
        List<DailyTaskItemResponse> refreshed = readDailyTasks(key);
        return refreshed.isEmpty() ? loaded : refreshed;
    }

    public PointsLedgerSummarySnapshot getPointsLedgerSummary(
            long userId,
            LocalDate businessDate,
            Supplier<PointsLedgerSummarySnapshot> databaseLoader
    ) {
        if (userId <= 0 || businessDate == null || databaseLoader == null) {
            return new PointsLedgerSummarySnapshot(0, 0);
        }
        if (stringRedisTemplate == null) {
            return sanitizePointsLedgerSummary(databaseLoader.get());
        }

        String key = buildPointsLedgerSummaryKey(userId, businessDate);
        PointsLedgerSummarySnapshot cached = readPointsLedgerSummary(key);
        if (cached != null) {
            return cached;
        }

        PointsLedgerSummarySnapshot loaded = sanitizePointsLedgerSummary(databaseLoader.get());
        writeValue(key, loaded);
        PointsLedgerSummarySnapshot refreshed = readPointsLedgerSummary(key);
        return refreshed == null ? loaded : refreshed;
    }

    public void evictCheckinOverviewNow(long userId) {
        evict(buildCheckinOverviewKey(userId, LocalDate.now()));
    }

    public void evictCheckinOverviewAfterCommit(long userId) {
        runAfterCommit(() -> evictCheckinOverviewNow(userId));
    }

    public void evictDailyTasksNow(long userId) {
        evict(buildDailyTasksKey(userId, LocalDate.now()));
    }

    public void evictDailyTasksAfterCommit(long userId) {
        runAfterCommit(() -> evictDailyTasksNow(userId));
    }

    public void evictPointsLedgerSummaryNow(long userId) {
        evict(buildPointsLedgerSummaryKey(userId, LocalDate.now()));
    }

    public void evictPointsLedgerSummaryAfterCommit(long userId) {
        runAfterCommit(() -> evictPointsLedgerSummaryNow(userId));
    }

    private GrowthCheckinOverviewResponse readCheckinOverview(String key) {
        try {
            String payload = stringRedisTemplate.opsForValue().get(key);
            if (payload == null || payload.isBlank()) {
                return null;
            }
            return sanitizeCheckinOverview(objectMapper.readValue(payload, CHECKIN_OVERVIEW_TYPE));
        } catch (Exception ex) {
            log.debug("read growth checkin overview cache failed: {}", ex.getMessage());
            return null;
        }
    }

    private List<DailyTaskItemResponse> readDailyTasks(String key) {
        try {
            String payload = stringRedisTemplate.opsForValue().get(key);
            if (payload == null || payload.isBlank()) {
                return List.of();
            }
            return sanitizeDailyTasks(objectMapper.readValue(payload, DAILY_TASKS_TYPE));
        } catch (Exception ex) {
            log.debug("read growth daily tasks cache failed: {}", ex.getMessage());
            return List.of();
        }
    }

    private PointsLedgerSummarySnapshot readPointsLedgerSummary(String key) {
        try {
            String payload = stringRedisTemplate.opsForValue().get(key);
            if (payload == null || payload.isBlank()) {
                return null;
            }
            return sanitizePointsLedgerSummary(objectMapper.readValue(payload, PointsLedgerSummarySnapshot.class));
        } catch (Exception ex) {
            log.debug("read growth points ledger summary cache failed: {}", ex.getMessage());
            return null;
        }
    }

    private void writeValue(String key, Object payload) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(payload), TTL);
        } catch (Exception ex) {
            log.debug("write growth center cache failed: {}", ex.getMessage());
        }
    }

    private void evict(String key) {
        if (stringRedisTemplate == null || key == null || key.isBlank()) {
            return;
        }
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception ex) {
            log.debug("evict growth center cache failed: {}", ex.getMessage());
        }
    }

    private void runAfterCommit(Runnable action) {
        if (action == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
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

    private String buildCheckinOverviewKey(long userId, LocalDate businessDate) {
        return CHECKIN_OVERVIEW_KEY_PREFIX + userId + ":" + businessDate;
    }

    private String buildDailyTasksKey(long userId, LocalDate businessDate) {
        return DAILY_TASKS_KEY_PREFIX + userId + ":" + businessDate;
    }

    private String buildPointsLedgerSummaryKey(long userId, LocalDate businessDate) {
        return POINTS_LEDGER_SUMMARY_KEY_PREFIX + userId + ":" + businessDate;
    }

    private GrowthCheckinOverviewResponse sanitizeCheckinOverview(GrowthCheckinOverviewResponse response) {
        if (response == null) {
            return null;
        }
        List<Integer> checkedInDays = response.checkedInDays() == null
                ? List.of()
                : response.checkedInDays().stream()
                .filter(Objects::nonNull)
                .map(Integer::intValue)
                .filter(day -> day > 0)
                .distinct()
                .sorted()
                .toList();
        List<GrowthCheckinOverviewResponse.RewardRuleItem> rewardRules = response.rewardRules() == null
                ? List.of()
                : response.rewardRules().stream()
                .filter(Objects::nonNull)
                .filter(rule -> rule.rewardCode() != null && !rule.rewardCode().isBlank())
                .map(rule -> new GrowthCheckinOverviewResponse.RewardRuleItem(
                        rule.rewardCode(),
                        rule.title(),
                        rule.description(),
                        Math.max(rule.streakDays(), 0),
                        Math.max(rule.bonusPoints(), 0)
                ))
                .toList();
        GrowthCheckinOverviewResponse.NextRewardItem nextReward = response.nextReward() == null
                ? null
                : new GrowthCheckinOverviewResponse.NextRewardItem(
                response.nextReward().rewardCode(),
                response.nextReward().title(),
                response.nextReward().description(),
                Math.max(response.nextReward().streakDays(), 0),
                Math.max(response.nextReward().bonusPoints(), 0),
                Math.max(response.nextReward().remainingDays(), 0)
        );
        return new GrowthCheckinOverviewResponse(
                response.signedInToday(),
                Math.max(response.growthJourneyDays(), 1),
                Math.max(response.currentStreak(), 0),
                response.latestCheckinDate(),
                response.currentMonth(),
                Math.max(response.daysInCurrentMonth(), 0),
                checkedInDays,
                Math.max(response.basePointsPerDay(), 0),
                rewardRules,
                nextReward
        );
    }

    private List<DailyTaskItemResponse> sanitizeDailyTasks(List<DailyTaskItemResponse> tasks) {
        if (tasks == null) {
            return List.of();
        }
        return tasks.stream()
                .filter(Objects::nonNull)
                .filter(task -> task.taskId() > 0)
                .filter(task -> task.taskCode() != null && !task.taskCode().isBlank())
                .map(task -> new DailyTaskItemResponse(
                        task.taskId(),
                        task.taskCode(),
                        task.title(),
                        task.description(),
                        Math.max(task.points(), 0),
                        task.completed()
                ))
                .toList();
    }

    private PointsLedgerSummarySnapshot sanitizePointsLedgerSummary(PointsLedgerSummarySnapshot summary) {
        if (summary == null) {
            return new PointsLedgerSummarySnapshot(0, 0);
        }
        return new PointsLedgerSummarySnapshot(
                Math.max(summary.balance(), 0),
                Math.max(summary.total(), 0)
        );
    }

    public record PointsLedgerSummarySnapshot(int balance, long total) {
    }
}
