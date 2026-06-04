package com.bishe.server.bounty.service;

import com.bishe.server.bounty.dto.BountyTaskDetailResponse;
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
import java.util.function.Supplier;

/**
 * 悬赏详情缓存：只缓存公共主体，不缓存 mine / mySubmission 等 viewer 即时态。
 */
@Service
public class BountyTaskDetailCacheService {

    private static final Logger log = LoggerFactory.getLogger(BountyTaskDetailCacheService.class);
    private static final String KEY_PREFIX = "bounty:tasks:detail:";
    private static final Duration TTL = Duration.ofMinutes(2);
    private static final TypeReference<BountyTaskDetailResponse> RESPONSE_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public BountyTaskDetailCacheService(
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider
    ) {
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    public BountyTaskDetailResponse getTaskDetail(
            long taskId,
            Supplier<BountyTaskDetailResponse> databaseLoader
    ) {
        if (taskId <= 0 || databaseLoader == null) {
            return null;
        }
        if (stringRedisTemplate == null) {
            // Redis 不可用时退回数据库读取，仍只返回公共主体。
            return sanitize(databaseLoader.get());
        }

        String key = buildKey(taskId);
        BountyTaskDetailResponse cached = readFromRedis(key);
        if (cached != null) {
            return cached;
        }

        BountyTaskDetailResponse loaded = sanitize(databaseLoader.get());
        if (loaded == null) {
            return null;
        }
        writeToRedis(key, loaded);
        // 回读失败不影响业务，使用本次 sanitize 后的数据库响应。
        BountyTaskDetailResponse refreshed = readFromRedis(key);
        return refreshed == null ? loaded : refreshed;
    }

    public void evictNow(long taskId) {
        evict(taskId);
    }

    public void evictAfterCommit(long taskId) {
        runAfterCommit(() -> evict(taskId));
    }

    private BountyTaskDetailResponse readFromRedis(String key) {
        try {
            String payload = stringRedisTemplate.opsForValue().get(key);
            if (payload == null || payload.isBlank()) {
                return null;
            }
            return sanitize(objectMapper.readValue(payload, RESPONSE_TYPE));
        } catch (Exception ex) {
            log.debug("read bounty task detail cache from redis failed: {}", ex.getMessage());
            return null;
        }
    }

    private void writeToRedis(String key, BountyTaskDetailResponse payload) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(payload), TTL);
        } catch (Exception ex) {
            log.debug("write bounty task detail cache to redis failed: {}", ex.getMessage());
        }
    }

    private void evict(long taskId) {
        if (stringRedisTemplate == null || taskId <= 0) {
            return;
        }
        try {
            stringRedisTemplate.delete(buildKey(taskId));
        } catch (Exception ex) {
            log.debug("evict bounty task detail cache from redis failed: {}", ex.getMessage());
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

    private String buildKey(long taskId) {
        return KEY_PREFIX + taskId;
    }

    private BountyTaskDetailResponse sanitize(BountyTaskDetailResponse response) {
        if (response == null || response.taskId() <= 0 || response.enterpriseUserId() <= 0) {
            return null;
        }
        // 详情缓存只保留任务公共字段，mine/mySubmission 每次由 Service 即时合成。
        return new BountyTaskDetailResponse(
                response.taskId(),
                response.enterpriseUserId(),
                response.enterpriseName(),
                response.enterpriseLogoUrl(),
                response.title(),
                response.description(),
                response.rewardDescription(),
                response.status(),
                Math.max(response.submissionCount(), 0),
                response.acceptedSubmissionId(),
                false,
                response.deadlineAt(),
                response.closedAt(),
                response.createdAt(),
                response.updatedAt(),
                null
        );
    }
}
