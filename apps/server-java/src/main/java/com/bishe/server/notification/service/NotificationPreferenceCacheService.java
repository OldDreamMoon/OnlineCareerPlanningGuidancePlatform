package com.bishe.server.notification.service;

import com.bishe.server.auth.repository.UserRepository;
import com.bishe.server.notification.model.NotificationCategory;
import com.bishe.server.notification.repository.NotificationPreferenceRepository;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 用户通知偏好缓存：Redis 优先读取，更新后精确失效。
 */
@Service
public class NotificationPreferenceCacheService {

    private static final Logger log = LoggerFactory.getLogger(NotificationPreferenceCacheService.class);
    private static final String KEY_PREFIX = "notification:preferences:user:";
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final TypeReference<List<NotificationPreferenceRepository.NotificationPreferenceRow>> ROWS_TYPE =
            new TypeReference<>() {
            };

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserRepository userRepository;

    public NotificationPreferenceCacheService(
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
            UserRepository userRepository
    ) {
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
        this.userRepository = userRepository;
    }

    public Map<NotificationCategory, NotificationPreferenceRepository.NotificationPreferenceRow> getUserPreferences(
            long userId,
            Supplier<List<NotificationPreferenceRepository.NotificationPreferenceRow>> databaseLoader
    ) {
        if (userId <= 0 || databaseLoader == null) {
            return Map.of();
        }
        if (stringRedisTemplate == null) {
            return mapRows(sanitizeRows(databaseLoader.get()));
        }

        String key = buildKey(userId);
        Map<NotificationCategory, NotificationPreferenceRepository.NotificationPreferenceRow> cached = readFromRedis(key);
        if (cached != null) {
            return cached;
        }

        List<NotificationPreferenceRepository.NotificationPreferenceRow> loadedRows = sanitizeRows(databaseLoader.get());
        Map<NotificationCategory, NotificationPreferenceRepository.NotificationPreferenceRow> loaded = mapRows(loadedRows);
        writeToRedis(key, loadedRows);
        Map<NotificationCategory, NotificationPreferenceRepository.NotificationPreferenceRow> refreshed = readFromRedis(key);
        return refreshed == null ? loaded : refreshed;
    }

    public void evictUserPreferencesNow(long userId) {
        evict(userId);
    }

    public void evictUserPreferencesAfterCommit(long userId) {
        runAfterCommit(() -> evict(userId));
    }

    private Map<NotificationCategory, NotificationPreferenceRepository.NotificationPreferenceRow> readFromRedis(String key) {
        try {
            String payload = stringRedisTemplate.opsForValue().get(key);
            if (payload == null || payload.isBlank()) {
                return null;
            }
            return mapRows(sanitizeRows(objectMapper.readValue(payload, ROWS_TYPE)));
        } catch (Exception ex) {
            log.debug("read notification preferences from redis failed: {}", ex.getMessage());
            return null;
        }
    }

    private void writeToRedis(String key, List<NotificationPreferenceRepository.NotificationPreferenceRow> rows) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(rows), TTL);
        } catch (Exception ex) {
            log.debug("write notification preferences to redis failed: {}", ex.getMessage());
        }
    }

    private void evict(long userId) {
        if (stringRedisTemplate == null || userId <= 0) {
            return;
        }
        try {
            stringRedisTemplate.delete(buildKey(userId));
        } catch (Exception ex) {
            log.debug("evict notification preferences from redis failed: {}", ex.getMessage());
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

    private String buildKey(long userId) {
        String scope = userRepository.findById(userId)
                .map(user -> "user-" + user.id() + "-" + (user.createdAt() == null ? 0L : user.createdAt().toEpochMilli()))
                .orElse("user-" + userId);
        return KEY_PREFIX + scope;
    }

    private List<NotificationPreferenceRepository.NotificationPreferenceRow> sanitizeRows(
            List<NotificationPreferenceRepository.NotificationPreferenceRow> rows
    ) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .filter(row -> row != null && row.category() != null)
                .toList();
    }

    private Map<NotificationCategory, NotificationPreferenceRepository.NotificationPreferenceRow> mapRows(
            List<NotificationPreferenceRepository.NotificationPreferenceRow> rows
    ) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<NotificationCategory, NotificationPreferenceRepository.NotificationPreferenceRow> mapped = new LinkedHashMap<>();
        for (NotificationPreferenceRepository.NotificationPreferenceRow row : rows) {
            mapped.put(row.category(), row);
        }
        return mapped.isEmpty() ? Map.of() : Collections.unmodifiableMap(mapped);
    }
}
