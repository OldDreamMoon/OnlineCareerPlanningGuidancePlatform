package com.bishe.server.mentor.service;

import com.bishe.server.mentor.dto.MentorDashboardResponse;
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
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 导师工作台首页摘要缓存：只缓存聚合读模型，不缓存订单事实。
 */
@Service
public class MentorDashboardCacheService {

    private static final Logger log = LoggerFactory.getLogger(MentorDashboardCacheService.class);
    private static final String KEY_PREFIX = "mentor:dashboard:";
    private static final Duration TTL = Duration.ofMinutes(3);
    private static final TypeReference<MentorDashboardResponse> RESPONSE_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public MentorDashboardCacheService(
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider
    ) {
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    public MentorDashboardResponse getDashboard(long mentorUserId, Supplier<MentorDashboardResponse> databaseLoader) {
        if (mentorUserId <= 0 || databaseLoader == null) {
            return null;
        }
        if (stringRedisTemplate == null) {
            // Redis 不可用时直接读库，不影响导师首页打开。
            return sanitize(databaseLoader.get());
        }

        String key = buildKey(mentorUserId);
        MentorDashboardResponse cached = readFromRedis(key);
        if (cached != null) {
            return cached;
        }

        MentorDashboardResponse loaded = sanitize(databaseLoader.get());
        if (loaded == null) {
            return null;
        }
        writeToRedis(key, loaded);
        // 写后回读能提前发现序列化兼容问题，失败则使用本次加载结果。
        MentorDashboardResponse refreshed = readFromRedis(key);
        return refreshed == null ? loaded : refreshed;
    }

    public void evictNow(long mentorUserId) {
        evict(mentorUserId);
    }

    public void evictAfterCommit(long mentorUserId) {
        runAfterCommit(() -> evict(mentorUserId));
    }

    private MentorDashboardResponse readFromRedis(String key) {
        try {
            String payload = stringRedisTemplate.opsForValue().get(key);
            if (payload == null || payload.isBlank()) {
                return null;
            }
            return sanitize(objectMapper.readValue(payload, RESPONSE_TYPE));
        } catch (Exception ex) {
            log.debug("read mentor dashboard cache from redis failed: {}", ex.getMessage());
            return null;
        }
    }

    private void writeToRedis(String key, MentorDashboardResponse response) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(response), TTL);
        } catch (Exception ex) {
            log.debug("write mentor dashboard cache to redis failed: {}", ex.getMessage());
        }
    }

    private void evict(long mentorUserId) {
        if (stringRedisTemplate == null || mentorUserId <= 0) {
            return;
        }
        try {
            stringRedisTemplate.delete(buildKey(mentorUserId));
        } catch (Exception ex) {
            log.debug("evict mentor dashboard cache from redis failed: {}", ex.getMessage());
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

    private String buildKey(long mentorUserId) {
        return KEY_PREFIX + mentorUserId;
    }

    private MentorDashboardResponse sanitize(MentorDashboardResponse response) {
        if (response == null) {
            return null;
        }
        List<MentorDashboardResponse.RecentOrderItem> recentOrders = response.recentOrders() == null
                ? List.of()
                : response.recentOrders().stream()
                .filter(Objects::nonNull)
                .toList();
        return new MentorDashboardResponse(
                response.pendingPaidCount(),
                response.answeredCount(),
                response.closedCount(),
                response.totalRevenueFen(),
                response.totalOrders(),
                response.avgRating(),
                recentOrders
        );
    }
}
