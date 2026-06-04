package com.bishe.server.featureflag;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 功能开关配置缓存：统一缓存 feature flags 快照，供管理员列表与运行时读取复用。
 */
@Service
public class FeatureFlagCacheService {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagCacheService.class);
    private static final String CACHE_KEY = "feature-flags:snapshot";
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final TypeReference<List<FeatureFlagRepository.FeatureFlagRow>> SNAPSHOT_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public FeatureFlagCacheService(
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider
    ) {
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    public List<FeatureFlagRepository.FeatureFlagRow> getSnapshot(
            Supplier<List<FeatureFlagRepository.FeatureFlagRow>> databaseLoader
    ) {
        if (databaseLoader == null) {
            return List.of();
        }
        if (stringRedisTemplate == null) {
            return sanitize(databaseLoader.get());
        }

        List<FeatureFlagRepository.FeatureFlagRow> cached = readFromRedis();
        if (cached != null) {
            return cached;
        }

        List<FeatureFlagRepository.FeatureFlagRow> loaded = sanitize(databaseLoader.get());
        writeToRedis(loaded);
        List<FeatureFlagRepository.FeatureFlagRow> refreshed = readFromRedis();
        return refreshed == null ? loaded : refreshed;
    }

    public void evictNow() {
        evict();
    }

    public void evictAfterCommit() {
        runAfterCommit(this::evict);
    }

    private List<FeatureFlagRepository.FeatureFlagRow> readFromRedis() {
        try {
            String payload = stringRedisTemplate.opsForValue().get(CACHE_KEY);
            if (payload == null || payload.isBlank()) {
                return null;
            }
            return sanitize(objectMapper.readValue(payload, SNAPSHOT_TYPE));
        } catch (Exception ex) {
            log.debug("read feature flag cache from redis failed: {}", ex.getMessage());
            return null;
        }
    }

    private void writeToRedis(List<FeatureFlagRepository.FeatureFlagRow> rows) {
        try {
            stringRedisTemplate.opsForValue().set(CACHE_KEY, objectMapper.writeValueAsString(rows), TTL);
        } catch (Exception ex) {
            log.debug("write feature flag cache to redis failed: {}", ex.getMessage());
        }
    }

    private void evict() {
        if (stringRedisTemplate == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(CACHE_KEY);
        } catch (Exception ex) {
            log.debug("evict feature flag cache from redis failed: {}", ex.getMessage());
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

    private List<FeatureFlagRepository.FeatureFlagRow> sanitize(List<FeatureFlagRepository.FeatureFlagRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Map<String, FeatureFlagRepository.FeatureFlagRow> deduplicated = new LinkedHashMap<>();
        for (FeatureFlagRepository.FeatureFlagRow row : rows) {
            if (row == null || row.flagKey() == null || row.flagKey().isBlank()) {
                continue;
            }
            if (deduplicated.containsKey(row.flagKey())) {
                continue;
            }
            deduplicated.put(row.flagKey(), new FeatureFlagRepository.FeatureFlagRow(
                    row.flagKey(),
                    row.flagValue(),
                    row.description(),
                    row.updatedBy(),
                    row.updatedAt()
            ));
        }
        return deduplicated.values().stream()
                .filter(Objects::nonNull)
                .toList();
    }
}
