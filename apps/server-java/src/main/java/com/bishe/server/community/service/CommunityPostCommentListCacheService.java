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
 * 社区帖子评论列表缓存：只缓存公开帖子下的可见评论快照。
 */
@Service
public class CommunityPostCommentListCacheService {

    private static final Logger log = LoggerFactory.getLogger(CommunityPostCommentListCacheService.class);
    private static final String KEY_PREFIX = "community:posts:comments:";
    private static final Duration TTL = Duration.ofMinutes(2);
    private static final TypeReference<List<CommentSnapshot>> SNAPSHOT_LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public CommunityPostCommentListCacheService(
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider
    ) {
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    public List<CommentSnapshot> getVisibleComments(long postId, Supplier<List<CommentSnapshot>> databaseLoader) {
        if (postId <= 0 || databaseLoader == null) {
            return List.of();
        }
        if (stringRedisTemplate == null) {
            return sanitize(databaseLoader.get());
        }

        String key = buildKey(postId);
        List<CommentSnapshot> cached = readFromRedis(key);
        if (cached != null) {
            return cached;
        }

        List<CommentSnapshot> loaded = sanitize(databaseLoader.get());
        writeToRedis(key, loaded);
        List<CommentSnapshot> refreshed = readFromRedis(key);
        return refreshed == null ? loaded : refreshed;
    }

    public void evictNow(long postId) {
        evict(postId);
    }

    public void evictAfterCommit(long postId) {
        runAfterCommit(() -> evict(postId));
    }

    private List<CommentSnapshot> readFromRedis(String key) {
        try {
            String payload = stringRedisTemplate.opsForValue().get(key);
            if (payload == null || payload.isBlank()) {
                return null;
            }
            return sanitize(objectMapper.readValue(payload, SNAPSHOT_LIST_TYPE));
        } catch (Exception ex) {
            log.debug("read community post comment cache from redis failed: {}", ex.getMessage());
            return null;
        }
    }

    private void writeToRedis(String key, List<CommentSnapshot> comments) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(sanitize(comments)), TTL);
        } catch (Exception ex) {
            log.debug("write community post comment cache to redis failed: {}", ex.getMessage());
        }
    }

    private void evict(long postId) {
        if (stringRedisTemplate == null || postId <= 0) {
            return;
        }
        try {
            stringRedisTemplate.delete(buildKey(postId));
        } catch (Exception ex) {
            log.debug("evict community post comment cache from redis failed: {}", ex.getMessage());
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

    private List<CommentSnapshot> sanitize(List<CommentSnapshot> comments) {
        if (comments == null || comments.isEmpty()) {
            return List.of();
        }
        return comments.stream()
                .filter(Objects::nonNull)
                .filter(comment -> comment.commentId() > 0 && comment.userId() > 0)
                .map(comment -> new CommentSnapshot(
                        comment.commentId(),
                        comment.userId(),
                        comment.displayName(),
                        comment.realName(),
                        comment.showRealName(),
                        comment.role(),
                        comment.avatarUrl(),
                        comment.content(),
                        comment.ai(),
                        comment.createdAt()
                ))
                .toList();
    }

    public record CommentSnapshot(
            long commentId,
            long userId,
            String displayName,
            String realName,
            boolean showRealName,
            String role,
            String avatarUrl,
            String content,
            boolean ai,
            Instant createdAt
    ) {
    }
}
