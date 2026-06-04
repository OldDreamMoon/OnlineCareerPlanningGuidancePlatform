package com.bishe.server.notification.service;

import com.bishe.server.auth.repository.UserRepository;
import com.bishe.server.notification.model.NotificationCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 通知计数旁路缓存：Redis 优先读取，提交后做增量更新或失效。
 */
@Service
public class NotificationCountCacheService {

    private static final Logger log = LoggerFactory.getLogger(NotificationCountCacheService.class);
    private static final String KEY_PREFIX = "notification:counts:";
    private static final String READY_FIELD = "_ready";
    private static final String UNREAD_FIELD = "unread";
    private static final String ACTIONABLE_TOTAL_FIELD = "actionable:ALL";
    private static final String ACTIONABLE_FIELD_PREFIX = "actionable:";
    private static final Duration TTL = Duration.ofHours(12);

    private final StringRedisTemplate stringRedisTemplate;
    private final UserRepository userRepository;

    public NotificationCountCacheService(
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
            UserRepository userRepository
    ) {
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
        this.userRepository = userRepository;
    }

    public NotificationCountSnapshot getCounts(long userId, Supplier<NotificationCountSnapshot> databaseLoader) {
        if (databaseLoader == null) {
            return NotificationCountSnapshot.empty();
        }
        if (stringRedisTemplate == null) {
            return sanitize(databaseLoader.get());
        }

        String key = buildKey(userId);
        NotificationCountSnapshot cached = readFromRedis(key);
        if (cached != null) {
            return cached;
        }

        NotificationCountSnapshot loaded = sanitize(databaseLoader.get());
        writeToRedis(key, loaded);
        NotificationCountSnapshot refreshed = readFromRedis(key);
        return refreshed == null ? loaded : refreshed;
    }

    public void recordCreatedAfterCommit(long userId, NotificationCategory category, String actionCode) {
        runAfterCommit(() -> incrementIfReady(userId, category, actionCode));
    }

    public void evictAfterCommit(long userId) {
        runAfterCommit(() -> evict(userId));
    }

    private NotificationCountSnapshot readFromRedis(String key) {
        try {
            HashOperations<String, Object, Object> hashOperations = stringRedisTemplate.opsForHash();
            Map<Object, Object> rawEntries = hashOperations.entries(key);
            if (rawEntries == null || rawEntries.isEmpty()) {
                return null;
            }
            if (!"1".equals(String.valueOf(rawEntries.get(READY_FIELD)))) {
                return null;
            }
            Long unreadCount = parseCount(rawEntries.get(UNREAD_FIELD));
            Long totalActionableCount = parseCount(rawEntries.get(ACTIONABLE_TOTAL_FIELD));
            if (unreadCount == null || totalActionableCount == null) {
                return null;
            }
            EnumMap<NotificationCategory, Long> actionableByCategory = new EnumMap<>(NotificationCategory.class);
            for (NotificationCategory category : NotificationCategory.values()) {
                Long count = parseCount(rawEntries.get(actionableField(category)));
                if (count == null) {
                    return null;
                }
                actionableByCategory.put(category, count);
            }
            return new NotificationCountSnapshot(unreadCount, totalActionableCount, actionableByCategory);
        } catch (Exception ex) {
            log.debug("read notification counts from redis failed: {}", ex.getMessage());
            return null;
        }
    }

    private void writeToRedis(String key, NotificationCountSnapshot snapshot) {
        try {
            LinkedHashMap<String, String> payload = new LinkedHashMap<>();
            payload.put(READY_FIELD, "1");
            payload.put(UNREAD_FIELD, String.valueOf(snapshot.unreadCount()));
            payload.put(ACTIONABLE_TOTAL_FIELD, String.valueOf(snapshot.totalActionableCount()));
            for (NotificationCategory category : NotificationCategory.values()) {
                payload.put(actionableField(category), String.valueOf(snapshot.actionableCount(category)));
            }
            stringRedisTemplate.opsForHash().putAll(key, payload);
            stringRedisTemplate.expire(key, TTL);
        } catch (Exception ex) {
            log.debug("write notification counts to redis failed: {}", ex.getMessage());
        }
    }

    private void incrementIfReady(long userId, NotificationCategory category, String actionCode) {
        if (stringRedisTemplate == null) {
            return;
        }
        String key = buildKey(userId);
        try {
            HashOperations<String, Object, Object> hashOperations = stringRedisTemplate.opsForHash();
            if (!Boolean.TRUE.equals(hashOperations.hasKey(key, READY_FIELD))) {
                return;
            }
            hashOperations.increment(key, UNREAD_FIELD, 1L);
            if (isActionable(actionCode)) {
                hashOperations.increment(key, ACTIONABLE_TOTAL_FIELD, 1L);
                hashOperations.increment(key, actionableField(category), 1L);
            }
            stringRedisTemplate.expire(key, TTL);
        } catch (Exception ex) {
            log.debug("increment notification counts in redis failed: {}", ex.getMessage());
        }
    }

    private void evict(long userId) {
        if (stringRedisTemplate == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(buildKey(userId));
        } catch (Exception ex) {
            log.debug("evict notification counts in redis failed: {}", ex.getMessage());
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

    private NotificationCountSnapshot sanitize(NotificationCountSnapshot snapshot) {
        return snapshot == null ? NotificationCountSnapshot.empty() : snapshot;
    }

    private String buildKey(long userId) {
        String scope = userRepository.findById(userId)
                .map(user -> "user-" + user.id() + "-" + (user.createdAt() == null ? 0L : user.createdAt().toEpochMilli()))
                .orElse("user-" + userId);
        return KEY_PREFIX + scope;
    }

    private String actionableField(NotificationCategory category) {
        return ACTIONABLE_FIELD_PREFIX + (category == null ? "ALL" : category.name());
    }

    private boolean isActionable(String actionCode) {
        return actionCode != null
                && !actionCode.isBlank()
                && !"VIEW_NOTIFICATION_CENTER".equalsIgnoreCase(actionCode);
    }

    private Long parseCount(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        try {
            return Math.max(Long.parseLong(String.valueOf(rawValue).trim()), 0L);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public record NotificationCountSnapshot(
            long unreadCount,
            long totalActionableCount,
            Map<NotificationCategory, Long> actionableByCategory
    ) {
        public NotificationCountSnapshot {
            EnumMap<NotificationCategory, Long> normalized = new EnumMap<>(NotificationCategory.class);
            if (actionableByCategory != null) {
                actionableByCategory.forEach((category, value) -> {
                    if (category != null) {
                        normalized.put(category, Math.max(value == null ? 0L : value, 0L));
                    }
                });
            }
            for (NotificationCategory category : NotificationCategory.values()) {
                normalized.putIfAbsent(category, 0L);
            }
            actionableByCategory = Map.copyOf(normalized);
            unreadCount = Math.max(unreadCount, 0L);
            totalActionableCount = Math.max(totalActionableCount, 0L);
        }

        public static NotificationCountSnapshot empty() {
            return new NotificationCountSnapshot(0L, 0L, Map.of());
        }

        public long actionableCount(NotificationCategory category) {
            if (category == null) {
                return totalActionableCount;
            }
            return actionableByCategory.getOrDefault(category, 0L);
        }
    }
}
