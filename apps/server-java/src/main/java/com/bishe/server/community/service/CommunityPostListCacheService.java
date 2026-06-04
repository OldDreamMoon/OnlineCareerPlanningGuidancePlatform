package com.bishe.server.community.service;

import com.bishe.server.community.dto.CommunityPostListResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 社区帖子列表缓存：只缓存公开帖子主体，不缓存当前用户的即时交互态。
 */
@Service
public class CommunityPostListCacheService {

    private static final Logger log = LoggerFactory.getLogger(CommunityPostListCacheService.class);
    private static final String KEY_PREFIX = "community:posts:list:";
    private static final String INDEX_KEY = "community:posts:list:index";
    private static final Duration TTL = Duration.ofMinutes(2);
    private static final Duration INDEX_TTL = Duration.ofMinutes(10);

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public CommunityPostListCacheService(
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider
    ) {
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    public CommunityPostListResponse getPostList(
            String keyword,
            String tag,
            String scenarioCode,
            int page,
            int size,
            Supplier<CommunityPostListResponse> databaseLoader
    ) {
        if (databaseLoader == null) {
            return null;
        }
        if (stringRedisTemplate == null) {
            return sanitize(databaseLoader.get());
        }

        String key = buildKey(keyword, tag, scenarioCode, page, size);
        CommunityPostListResponse cached = readFromRedis(key);
        if (cached != null) {
            return cached;
        }

        CommunityPostListResponse loaded = sanitize(databaseLoader.get());
        if (loaded == null) {
            return null;
        }
        writeToRedis(key, loaded);
        CommunityPostListResponse refreshed = readFromRedis(key);
        return refreshed == null ? loaded : refreshed;
    }

    public void evictAllNow() {
        evictAll();
    }

    public void evictAllAfterCommit() {
        runAfterCommit(this::evictAll);
    }

    private CommunityPostListResponse readFromRedis(String key) {
        try {
            String payload = stringRedisTemplate.opsForValue().get(key);
            if (payload == null || payload.isBlank()) {
                return null;
            }
            return sanitize(objectMapper.readValue(payload, CommunityPostListResponse.class));
        } catch (Exception ex) {
            log.debug("read community post list cache from redis failed: {}", ex.getMessage());
            return null;
        }
    }

    private void writeToRedis(String key, CommunityPostListResponse payload) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(payload), TTL);
            stringRedisTemplate.opsForSet().add(INDEX_KEY, key);
            stringRedisTemplate.expire(INDEX_KEY, INDEX_TTL);
        } catch (Exception ex) {
            log.debug("write community post list cache to redis failed: {}", ex.getMessage());
        }
    }

    private void evictAll() {
        if (stringRedisTemplate == null) {
            return;
        }
        try {
            Set<String> keys = stringRedisTemplate.opsForSet().members(INDEX_KEY);
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
            stringRedisTemplate.delete(INDEX_KEY);
        } catch (Exception ex) {
            log.debug("evict community post list cache from redis failed: {}", ex.getMessage());
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

    private String buildKey(String keyword, String tag, String scenarioCode, int page, int size) {
        String raw = String.join(
                "|",
                normalizeText(keyword),
                normalizeText(tag),
                normalizeText(scenarioCode),
                String.valueOf(page),
                String.valueOf(size)
        );
        return KEY_PREFIX + DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return "_";
        }
        return value.trim().toLowerCase();
    }

    private CommunityPostListResponse sanitize(CommunityPostListResponse response) {
        if (response == null) {
            return null;
        }
        List<CommunityPostListResponse.PostItem> records = response.records() == null
                ? List.of()
                : response.records().stream()
                .filter(Objects::nonNull)
                .filter(item -> item.postId() != null && item.postId() > 0)
                .map(item -> new CommunityPostListResponse.PostItem(
                        item.postId(),
                        item.authorUserId(),
                        item.authorDisplayName(),
                        item.authorRealName(),
                        item.authorShowRealName(),
                        item.authorRole(),
                        item.authorAvatarUrl(),
                        item.title(),
                        item.scenarioCode(),
                        item.resolvedStatus(),
                        item.content(),
                        item.tags() == null ? List.of() : item.tags().stream().filter(Objects::nonNull).toList(),
                        Math.max(item.commentCount(), 0L),
                        Math.max(item.likeCount(), 0L),
                        false,
                        item.moderationStatus(),
                        item.riskLevel(),
                        item.hasMentorReply(),
                        false,
                        false,
                        item.createdAt(),
                        item.updatedAt()
                ))
                .toList();
        return new CommunityPostListResponse(
                records,
                Math.max(response.total(), 0L),
                Math.max(response.page(), 1),
                Math.min(Math.max(response.size(), 1), 50)
        );
    }
}
