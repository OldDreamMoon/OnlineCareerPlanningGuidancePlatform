package com.bishe.server.ai.quota;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.IntSupplier;

/**
 * AI 配额日用量计数：Redis 优先，miss 时回源数据库并回填。
 */
@Service
public class AiQuotaUsageCounterService {

    private static final Logger log = LoggerFactory.getLogger(AiQuotaUsageCounterService.class);
    private static final String KEY_PREFIX = "ai:quota:daily:";

    private final StringRedisTemplate stringRedisTemplate;

    public AiQuotaUsageCounterService(ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider) {
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    public int getUsedToday(String userScope, String taskType, String sceneCode, LocalDate targetDate, IntSupplier databaseLoader) {
        if (databaseLoader == null) {
            return 0;
        }
        if (stringRedisTemplate == null) {
            return Math.max(databaseLoader.getAsInt(), 0);
        }

        String key = buildKey(userScope, taskType, sceneCode, targetDate);
        try {
            String cachedValue = stringRedisTemplate.opsForValue().get(key);
            Integer parsed = parseCount(cachedValue);
            if (parsed != null) {
                return parsed;
            }
        } catch (Exception ex) {
            log.debug("read ai quota counter from redis failed: {}", ex.getMessage());
            return Math.max(databaseLoader.getAsInt(), 0);
        }

        int loadedCount = Math.max(databaseLoader.getAsInt(), 0);
        try {
            stringRedisTemplate.opsForValue().setIfAbsent(key, String.valueOf(loadedCount), resolveTtl(targetDate));
            String refreshedValue = stringRedisTemplate.opsForValue().get(key);
            Integer refreshedCount = parseCount(refreshedValue);
            return refreshedCount == null ? loadedCount : refreshedCount;
        } catch (Exception ex) {
            log.debug("backfill ai quota counter to redis failed: {}", ex.getMessage());
            return loadedCount;
        }
    }

    public void recordSuccess(String userScope, String taskType, String sceneCode, LocalDate targetDate, int quotaWeight) {
        if (stringRedisTemplate == null || quotaWeight <= 0) {
            return;
        }
        String key = buildKey(userScope, taskType, sceneCode, targetDate);
        try {
            stringRedisTemplate.opsForValue().increment(key, quotaWeight);
            stringRedisTemplate.expire(key, resolveTtl(targetDate));
        } catch (Exception ex) {
            log.debug("increment ai quota counter in redis failed: {}", ex.getMessage());
        }
    }

    private String buildKey(String userScope, String taskType, String sceneCode, LocalDate targetDate) {
        String normalizedScope = StringUtils.hasText(userScope) ? userScope.trim() : "anonymous";
        String normalizedTaskType = taskType == null ? "UNKNOWN" : taskType.trim().toUpperCase();
        if (!StringUtils.hasText(sceneCode)) {
            return KEY_PREFIX + targetDate + ":" + normalizedScope + ":" + normalizedTaskType;
        }
        return KEY_PREFIX
                + targetDate
                + ":"
                + normalizedScope
                + ":"
                + normalizedTaskType
                + ":"
                + sceneCode.trim().toUpperCase();
    }

    private Duration resolveTtl(LocalDate targetDate) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireAt = targetDate.plusDays(1).atStartOfDay().plusMinutes(5);
        if (expireAt.isBefore(now)) {
            expireAt = now.plusMinutes(5);
        }
        return Duration.between(now, expireAt);
    }

    private Integer parseCount(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        try {
            return Math.max(Integer.parseInt(rawValue.trim()), 0);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
