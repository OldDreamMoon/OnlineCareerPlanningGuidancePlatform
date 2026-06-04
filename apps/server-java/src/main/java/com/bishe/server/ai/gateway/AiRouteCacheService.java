package com.bishe.server.ai.gateway;

import com.bishe.server.common.tx.AfterCommitActionSynchronization;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

/**
 * AI 路由解析配置缓存：按 taskType + sceneCode + userTier 缓存解析候选列表，后台写入后整组失效。
 */
@Service
public class AiRouteCacheService {

    private static final Logger log = LoggerFactory.getLogger(AiRouteCacheService.class);
    private static final String KEY_PREFIX = "ai:gateway:routes:";
    private static final String KEY_REGISTRY = "ai:gateway:routes:keys";
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final TypeReference<List<AiGatewayAdminRepository.ResolvedRouteRow>> ROUTES_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public AiRouteCacheService(
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider
    ) {
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    public List<AiGatewayAdminRepository.ResolvedRouteRow> getEnabledRoutes(
            String taskType,
            String sceneCode,
            String userTier,
            Supplier<List<AiGatewayAdminRepository.ResolvedRouteRow>> databaseLoader
    ) {
        if (databaseLoader == null || taskType == null || taskType.isBlank()) {
            return List.of();
        }
        if (stringRedisTemplate == null) {
            return sanitize(databaseLoader.get());
        }

        String key = buildKey(taskType, sceneCode, userTier);
        List<AiGatewayAdminRepository.ResolvedRouteRow> cached = readFromRedis(key);
        if (cached != null) {
            return cached;
        }

        List<AiGatewayAdminRepository.ResolvedRouteRow> loaded = sanitize(databaseLoader.get());
        writeToRedis(key, loaded);
        List<AiGatewayAdminRepository.ResolvedRouteRow> refreshed = readFromRedis(key);
        return refreshed == null ? loaded : refreshed;
    }

    public void evictAllNow() {
        evictAll();
    }

    public void evictAllAfterCommit() {
        runAfterCommit(this::evictAll);
    }

    private List<AiGatewayAdminRepository.ResolvedRouteRow> readFromRedis(String key) {
        try {
            String payload = stringRedisTemplate.opsForValue().get(key);
            if (payload == null || payload.isBlank()) {
                return null;
            }
            return sanitize(objectMapper.readValue(payload, ROUTES_TYPE));
        } catch (Exception ex) {
            log.debug("read ai route cache from redis failed: {}", ex.getMessage());
            return null;
        }
    }

    private void writeToRedis(String key, List<AiGatewayAdminRepository.ResolvedRouteRow> routes) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(routes), TTL);
            stringRedisTemplate.opsForSet().add(KEY_REGISTRY, key);
        } catch (Exception ex) {
            log.debug("write ai route cache to redis failed: {}", ex.getMessage());
        }
    }

    private void evictAll() {
        if (stringRedisTemplate == null) {
            return;
        }
        try {
            Set<String> keys = stringRedisTemplate.opsForSet().members(KEY_REGISTRY);
            if (keys == null || keys.isEmpty()) {
                stringRedisTemplate.delete(KEY_REGISTRY);
                return;
            }
            List<String> toDelete = new ArrayList<>(keys);
            toDelete.add(KEY_REGISTRY);
            stringRedisTemplate.delete(toDelete);
        } catch (Exception ex) {
            log.debug("evict ai route cache from redis failed: {}", ex.getMessage());
        }
    }

    private void runAfterCommit(Runnable action) {
        AfterCommitActionSynchronization.registerOrRun(action);
    }

    private String buildKey(String taskType, String sceneCode, String userTier) {
        String normalizedTaskType = taskType.trim().toUpperCase(Locale.ROOT);
        String normalizedSceneCode = sceneCode == null || sceneCode.isBlank()
                ? "_default"
                : sceneCode.trim().toUpperCase(Locale.ROOT);
        String normalizedTier = userTier == null || userTier.isBlank()
                ? "_all"
                : userTier.trim().toUpperCase(Locale.ROOT);
        return KEY_PREFIX + normalizedTaskType + ":" + normalizedSceneCode + ":" + normalizedTier;
    }

    private List<AiGatewayAdminRepository.ResolvedRouteRow> sanitize(
            List<AiGatewayAdminRepository.ResolvedRouteRow> routes
    ) {
        if (routes == null || routes.isEmpty()) {
            return List.of();
        }
        return routes.stream()
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
