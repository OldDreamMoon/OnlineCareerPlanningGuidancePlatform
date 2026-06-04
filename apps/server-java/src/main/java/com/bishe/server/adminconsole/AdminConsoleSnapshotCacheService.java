package com.bishe.server.adminconsole;

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
 * 管理控制台快照缓存：聚合业务开关、运行参数、AI 渠道摘要与治理策略，避免运行配置中心频繁重复聚合。
 */
@Service
public class AdminConsoleSnapshotCacheService {

    private static final Logger log = LoggerFactory.getLogger(AdminConsoleSnapshotCacheService.class);
    private static final String CACHE_KEY = "admin:system:console-snapshot";
    private static final Duration TTL = Duration.ofMinutes(2);

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public AdminConsoleSnapshotCacheService(
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider
    ) {
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    public AdminSystemConsoleService.ConsoleSnapshotPayload getSnapshot(
            Supplier<AdminSystemConsoleService.ConsoleSnapshotPayload> databaseLoader
    ) {
        if (databaseLoader == null) {
            return null;
        }
        if (stringRedisTemplate == null) {
            return sanitize(databaseLoader.get());
        }

        AdminSystemConsoleService.ConsoleSnapshotPayload cached = readFromRedis();
        if (cached != null) {
            return cached;
        }

        AdminSystemConsoleService.ConsoleSnapshotPayload loaded = sanitize(databaseLoader.get());
        if (loaded == null) {
            return null;
        }
        writeToRedis(loaded);
        AdminSystemConsoleService.ConsoleSnapshotPayload refreshed = readFromRedis();
        return refreshed == null ? loaded : refreshed;
    }

    public void evictNow() {
        evict();
    }

    public void evictAfterCommit() {
        runAfterCommit(this::evict);
    }

    private AdminSystemConsoleService.ConsoleSnapshotPayload readFromRedis() {
        try {
            String payload = stringRedisTemplate.opsForValue().get(CACHE_KEY);
            if (payload == null || payload.isBlank()) {
                return null;
            }
            return sanitize(objectMapper.readValue(payload, AdminSystemConsoleService.ConsoleSnapshotPayload.class));
        } catch (Exception ex) {
            log.debug("read admin console snapshot cache from redis failed: {}", ex.getMessage());
            return null;
        }
    }

    private void writeToRedis(AdminSystemConsoleService.ConsoleSnapshotPayload payload) {
        try {
            stringRedisTemplate.opsForValue().set(CACHE_KEY, objectMapper.writeValueAsString(payload), TTL);
        } catch (Exception ex) {
            log.debug("write admin console snapshot cache to redis failed: {}", ex.getMessage());
        }
    }

    private void evict() {
        if (stringRedisTemplate == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(CACHE_KEY);
        } catch (Exception ex) {
            log.debug("evict admin console snapshot cache from redis failed: {}", ex.getMessage());
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

    private AdminSystemConsoleService.ConsoleSnapshotPayload sanitize(
            AdminSystemConsoleService.ConsoleSnapshotPayload payload
    ) {
        if (payload == null || payload.runtimeSettings() == null || payload.moderationPolicies() == null) {
            return null;
        }
        return new AdminSystemConsoleService.ConsoleSnapshotPayload(
                payload.generatedAt() == null ? TimePayloads.toEpochMillis(Instant.now()) : payload.generatedAt(),
                payload.featureFlags() == null ? List.of() : payload.featureFlags().stream().filter(java.util.Objects::nonNull).toList(),
                payload.runtimeSettings(),
                payload.moderationPolicies(),
                sanitizeAiChannels(payload.aiChannels())
        );
    }

    private AdminSystemConsoleService.AiChannelOverviewPayload sanitizeAiChannels(
            AdminSystemConsoleService.AiChannelOverviewPayload payload
    ) {
        if (payload == null) {
            return new AdminSystemConsoleService.AiChannelOverviewPayload(
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    List.of()
            );
        }
        return new AdminSystemConsoleService.AiChannelOverviewPayload(
                payload.totalProviders(),
                payload.enabledProviders(),
                payload.healthyProviders(),
                payload.degradedProviders(),
                payload.downProviders(),
                payload.idleProviders(),
                payload.disabledProviders(),
                payload.totalRoutes(),
                payload.enabledRoutes(),
                payload.sceneBoundRoutes(),
                payload.syncBlockingRoutes(),
                payload.streamRoutes(),
                payload.asyncRoutes(),
                payload.realtimeRoutes(),
                payload.totalPromptTemplates(),
                payload.activePromptTemplates(),
                payload.draftPromptTemplates(),
                payload.inactivePromptTemplates(),
                payload.keyProviders() == null ? List.of() : payload.keyProviders().stream().filter(java.util.Objects::nonNull).toList()
        );
    }
}
