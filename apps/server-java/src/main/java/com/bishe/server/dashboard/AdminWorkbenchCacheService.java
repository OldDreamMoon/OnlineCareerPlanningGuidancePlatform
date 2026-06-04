package com.bishe.server.dashboard;

import com.bishe.server.common.TimePayloads;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 管理工作台摘要缓存：只缓存短 TTL 聚合结果，避免后台首页高频刷新时重复聚合多条读路径。
 */
@Service
public class AdminWorkbenchCacheService {

    private static final Logger log = LoggerFactory.getLogger(AdminWorkbenchCacheService.class);
    private static final String KEY_PREFIX = "admin:dashboard:workbench:";
    private static final String INDEX_KEY = "admin:dashboard:workbench:index";
    private static final Duration TTL = Duration.ofMinutes(1);
    private static final Duration INDEX_TTL = Duration.ofMinutes(10);
    private static final TypeReference<AdminDashboardService.WorkbenchPayload> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public AdminWorkbenchCacheService(
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider
    ) {
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    public AdminDashboardService.WorkbenchPayload getWorkbench(
            int hours,
            String timezone,
            Supplier<AdminDashboardService.WorkbenchPayload> databaseLoader
    ) {
        if (databaseLoader == null) {
            return null;
        }
        if (stringRedisTemplate == null) {
            return sanitize(databaseLoader.get());
        }

        String key = buildKey(hours, timezone);
        AdminDashboardService.WorkbenchPayload cached = readFromRedis(key);
        if (cached != null) {
            return cached;
        }

        AdminDashboardService.WorkbenchPayload loaded = sanitize(databaseLoader.get());
        if (loaded == null) {
            return null;
        }
        writeToRedis(key, loaded);
        AdminDashboardService.WorkbenchPayload refreshed = readFromRedis(key);
        return refreshed == null ? loaded : refreshed;
    }

    public void evictAllNow() {
        evictAll();
    }

    public void evictAllAfterCommit() {
        runAfterCommit(this::evictAll);
    }

    private AdminDashboardService.WorkbenchPayload readFromRedis(String key) {
        try {
            ValueOperations<String, String> valueOperations = stringRedisTemplate.opsForValue();
            String payload = valueOperations.get(key);
            if (payload == null || payload.isBlank()) {
                return null;
            }
            return sanitize(objectMapper.readValue(payload, PAYLOAD_TYPE));
        } catch (Exception ex) {
            log.debug("read admin workbench cache from redis failed: {}", ex.getMessage());
            return null;
        }
    }

    private void writeToRedis(String key, AdminDashboardService.WorkbenchPayload payload) {
        try {
            ValueOperations<String, String> valueOperations = stringRedisTemplate.opsForValue();
            valueOperations.set(key, objectMapper.writeValueAsString(payload), TTL);
            SetOperations<String, String> setOperations = stringRedisTemplate.opsForSet();
            setOperations.add(INDEX_KEY, key);
            stringRedisTemplate.expire(INDEX_KEY, INDEX_TTL);
        } catch (Exception ex) {
            log.debug("write admin workbench cache to redis failed: {}", ex.getMessage());
        }
    }

    private void evictAll() {
        if (stringRedisTemplate == null) {
            return;
        }
        try {
            Set<String> keys = stringRedisTemplate.opsForSet().members(INDEX_KEY);
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
            stringRedisTemplate.delete(INDEX_KEY);
        } catch (Exception ex) {
            log.debug("evict admin workbench cache from redis failed: {}", ex.getMessage());
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

    private String buildKey(int hours, String timezone) {
        String normalizedTimezone = timezone == null || timezone.isBlank() ? "Asia/Shanghai" : timezone.trim();
        String raw = hours + "|" + normalizedTimezone;
        return KEY_PREFIX + DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
    }

    private AdminDashboardService.WorkbenchPayload sanitize(AdminDashboardService.WorkbenchPayload payload) {
        if (payload == null || payload.providerRuntime() == null) {
            return null;
        }
        AdminDashboardService.ProviderRuntimeSummaryPayload runtime = payload.providerRuntime();
        return new AdminDashboardService.WorkbenchPayload(
                payload.generatedAt() == null ? TimePayloads.toEpochMillis(Instant.now()) : payload.generatedAt(),
                Math.max(payload.pendingReports(), 0L),
                Math.max(payload.reviewQueue(), 0L),
                Math.max(payload.afterSalesRequests(), 0L),
                Math.max(payload.reconciliationReviewRequired(), 0L),
                Math.max(payload.totalPendingTasks(), 0L),
                new AdminDashboardService.ProviderRuntimeSummaryPayload(
                        Math.max(runtime.hours(), 0),
                        runtime.timezone() == null || runtime.timezone().isBlank() ? "Asia/Shanghai" : runtime.timezone(),
                        Math.max(runtime.totalProviders(), 0L),
                        Math.max(runtime.healthyProviders(), 0L),
                        Math.max(runtime.degradedProviders(), 0L),
                        Math.max(runtime.downProviders(), 0L),
                        Math.max(runtime.disabledProviders(), 0L),
                        Math.max(runtime.idleProviders(), 0L),
                        Math.max(runtime.unhealthyProviders(), 0L)
                )
        );
    }
}
