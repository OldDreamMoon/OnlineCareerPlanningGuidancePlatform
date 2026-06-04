package com.bishe.server.community.service;

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
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 社区帖子详情缓存：只缓存公开帖子主体与聚合计数，不缓存评论列表和当前用户交互态。
 */
@Service
public class CommunityPostDetailCacheService {

    private static final Logger log = LoggerFactory.getLogger(CommunityPostDetailCacheService.class);
    private static final String KEY_PREFIX = "community:posts:detail:";
    private static final Duration TTL = Duration.ofMinutes(2);
    private static final TypeReference<CommunityPostDetailSnapshot> SNAPSHOT_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public CommunityPostDetailCacheService(
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider
    ) {
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    public CommunityPostDetailSnapshot getPublicDetail(long postId, Supplier<CommunityPostDetailSnapshot> databaseLoader) {
        if (postId <= 0 || databaseLoader == null) {
            return null;
        }
        if (stringRedisTemplate == null) {
            return sanitize(databaseLoader.get());
        }

        String key = buildKey(postId);
        CommunityPostDetailSnapshot cached = readFromRedis(key);
        if (cached != null) {
            return cached;
        }

        CommunityPostDetailSnapshot loaded = sanitize(databaseLoader.get());
        if (loaded == null) {
            return null;
        }
        writeToRedis(key, loaded);
        CommunityPostDetailSnapshot refreshed = readFromRedis(key);
        return refreshed == null ? loaded : refreshed;
    }

    public void evictNow(long postId) {
        evict(postId);
    }

    public void evictAfterCommit(long postId) {
        runAfterCommit(() -> evict(postId));
    }

    private CommunityPostDetailSnapshot readFromRedis(String key) {
        try {
            String payload = stringRedisTemplate.opsForValue().get(key);
            if (payload == null || payload.isBlank()) {
                return null;
            }
            return sanitize(objectMapper.readValue(payload, SNAPSHOT_TYPE));
        } catch (Exception ex) {
            log.debug("read community post detail cache from redis failed: {}", ex.getMessage());
            return null;
        }
    }

    private void writeToRedis(String key, CommunityPostDetailSnapshot snapshot) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(snapshot), TTL);
        } catch (Exception ex) {
            log.debug("write community post detail cache to redis failed: {}", ex.getMessage());
        }
    }

    private void evict(long postId) {
        if (stringRedisTemplate == null || postId <= 0) {
            return;
        }
        try {
            stringRedisTemplate.delete(buildKey(postId));
        } catch (Exception ex) {
            log.debug("evict community post detail cache from redis failed: {}", ex.getMessage());
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

    private String buildKey(long postId) {
        return KEY_PREFIX + postId;
    }

    private CommunityPostDetailSnapshot sanitize(CommunityPostDetailSnapshot snapshot) {
        if (snapshot == null || snapshot.postId() <= 0 || snapshot.authorUserId() <= 0) {
            return null;
        }
        return new CommunityPostDetailSnapshot(
                snapshot.postId(),
                snapshot.authorUserId(),
                snapshot.authorDisplayName(),
                snapshot.authorRealName(),
                snapshot.authorShowRealName(),
                snapshot.authorRole(),
                snapshot.authorAvatarUrl(),
                snapshot.title(),
                snapshot.scenarioCode(),
                snapshot.resolvedStatus(),
                snapshot.moderationStatus(),
                snapshot.riskLevel(),
                snapshot.content(),
                snapshot.tags() == null ? List.of() : snapshot.tags().stream().filter(Objects::nonNull).toList(),
                Math.max(snapshot.commentCount(), 0L),
                Math.max(snapshot.likeCount(), 0L),
                snapshot.createdAt(),
                snapshot.updatedAt()
        );
    }

    public record CommunityPostDetailSnapshot(
            long postId,
            long authorUserId,
            String authorDisplayName,
            String authorRealName,
            boolean authorShowRealName,
            String authorRole,
            String authorAvatarUrl,
            String title,
            String scenarioCode,
            String resolvedStatus,
            String moderationStatus,
            String riskLevel,
            String content,
            List<String> tags,
            long commentCount,
            long likeCount,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
