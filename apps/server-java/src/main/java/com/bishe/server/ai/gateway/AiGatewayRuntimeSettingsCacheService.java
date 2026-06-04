package com.bishe.server.ai.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * AI 网关 runtime settings 配置缓存：Redis 优先读取，后台更新后精确失效。
 */
@Service
public class AiGatewayRuntimeSettingsCacheService {

    private static final Logger log = LoggerFactory.getLogger(AiGatewayRuntimeSettingsCacheService.class);
    private static final String CACHE_KEY = "ai:gateway:runtime-settings";
    private static final Duration TTL = Duration.ofMinutes(30);

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public AiGatewayRuntimeSettingsCacheService(
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider
    ) {
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    public AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot getRuntimeSettings(
            Supplier<AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot> databaseLoader
    ) {
        if (databaseLoader == null) {
            return AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot.empty();
        }
        if (stringRedisTemplate == null) {
            return sanitize(databaseLoader.get());
        }

        AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot cached = readFromRedis();
        if (cached != null) {
            return cached;
        }

        AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot loaded = sanitize(databaseLoader.get());
        writeToRedis(loaded);
        AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot refreshed = readFromRedis();
        return refreshed == null ? loaded : refreshed;
    }

    public void evictNow() {
        evict();
    }

    public void evictAfterCommit() {
        runAfterCommit(this::evict);
    }

    private AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot readFromRedis() {
        try {
            String payload = stringRedisTemplate.opsForValue().get(CACHE_KEY);
            if (payload == null || payload.isBlank()) {
                return null;
            }
            return sanitize(objectMapper.readValue(payload, AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot.class));
        } catch (Exception ex) {
            log.debug("read ai gateway runtime settings from redis failed: {}", ex.getMessage());
            return null;
        }
    }

    private void writeToRedis(AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot snapshot) {
        try {
            stringRedisTemplate.opsForValue().set(CACHE_KEY, objectMapper.writeValueAsString(snapshot), TTL);
        } catch (Exception ex) {
            log.debug("write ai gateway runtime settings to redis failed: {}", ex.getMessage());
        }
    }

    private void evict() {
        if (stringRedisTemplate == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(CACHE_KEY);
        } catch (Exception ex) {
            log.debug("evict ai gateway runtime settings from redis failed: {}", ex.getMessage());
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

    private AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot sanitize(
            AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot snapshot
    ) {
        return snapshot == null ? AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot.empty() : snapshot;
    }
}
