package com.bishe.server.community.service;

import com.bishe.server.community.dto.CommunityLeaderboardResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 社区贡献榜缓存：按分页参数缓存公开榜单读模型，避免高频刷新时重复聚合帖子、评论与获赞统计。
 */
@Service
public class CommunityLeaderboardCacheService {

    private static final Logger log = LoggerFactory.getLogger(CommunityLeaderboardCacheService.class);
    private static final String KEY_PREFIX = "community:leaderboard:";
    private static final String INDEX_KEY = "community:leaderboard:index";
    private static final Duration TTL = Duration.ofMinutes(1);
    private static final Duration INDEX_TTL = Duration.ofMinutes(10);
    private static final String DEFAULT_FORMULA = "post*5 + comment*2 + like*1";

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public CommunityLeaderboardCacheService(
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider
    ) {
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    public CommunityLeaderboardResponse getLeaderboard(
            String window,
            int page,
            int size,
            Supplier<CommunityLeaderboardResponse> databaseLoader
    ) {
        if (databaseLoader == null) {
            return null;
        }
        String normalizedWindow = normalizeWindow(window);
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 50);
        if (stringRedisTemplate == null) {
            return sanitize(normalizedWindow, normalizedPage, normalizedSize, databaseLoader.get());
        }

        String key = buildKey(normalizedWindow, normalizedPage, normalizedSize);
        CommunityLeaderboardResponse cached = readFromRedis(key, normalizedWindow, normalizedPage, normalizedSize);
        if (cached != null) {
            return cached;
        }

        CommunityLeaderboardResponse loaded = sanitize(normalizedWindow, normalizedPage, normalizedSize, databaseLoader.get());
        if (loaded == null) {
            return null;
        }
        writeToRedis(key, loaded);
        CommunityLeaderboardResponse refreshed = readFromRedis(key, normalizedWindow, normalizedPage, normalizedSize);
        return refreshed == null ? loaded : refreshed;
    }

    public void evictAllNow() {
        evictAll();
    }

    public void evictAllAfterCommit() {
        runAfterCommit(this::evictAll);
    }

    private CommunityLeaderboardResponse readFromRedis(String key, String window, int page, int size) {
        try {
            ValueOperations<String, String> valueOperations = stringRedisTemplate.opsForValue();
            String payload = valueOperations.get(key);
            if (payload == null || payload.isBlank()) {
                return null;
            }
            return sanitize(window, page, size, objectMapper.readValue(payload, CommunityLeaderboardResponse.class));
        } catch (Exception ex) {
            log.debug("read community leaderboard cache from redis failed: {}", ex.getMessage());
            return null;
        }
    }

    private void writeToRedis(String key, CommunityLeaderboardResponse payload) {
        try {
            ValueOperations<String, String> valueOperations = stringRedisTemplate.opsForValue();
            valueOperations.set(key, objectMapper.writeValueAsString(payload), TTL);
            SetOperations<String, String> setOperations = stringRedisTemplate.opsForSet();
            setOperations.add(INDEX_KEY, key);
            stringRedisTemplate.expire(INDEX_KEY, INDEX_TTL);
        } catch (Exception ex) {
            log.debug("write community leaderboard cache to redis failed: {}", ex.getMessage());
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
            log.debug("evict community leaderboard cache from redis failed: {}", ex.getMessage());
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

    private String buildKey(String window, int page, int size) {
        return KEY_PREFIX + normalizeWindow(window) + ":" + page + ":" + size;
    }

    private String normalizeWindow(String rawWindow) {
        return "7d";
    }

    private CommunityLeaderboardResponse sanitize(
            String window,
            int page,
            int size,
            CommunityLeaderboardResponse payload
    ) {
        if (payload == null) {
            return null;
        }
        List<CommunityLeaderboardResponse.LeaderboardItem> records = payload.records() == null
                ? List.of()
                : payload.records().stream()
                .filter(java.util.Objects::nonNull)
                .map(this::sanitizeRecord)
                .toList();
        return new CommunityLeaderboardResponse(
                normalizeWindow(payload.window() == null ? window : payload.window()),
                payload.formula() == null || payload.formula().isBlank() ? DEFAULT_FORMULA : payload.formula(),
                records,
                Math.max(payload.total(), 0L),
                Math.max(payload.page(), page),
                Math.max(payload.size(), size)
        );
    }

    private CommunityLeaderboardResponse.LeaderboardItem sanitizeRecord(
            CommunityLeaderboardResponse.LeaderboardItem item
    ) {
        return new CommunityLeaderboardResponse.LeaderboardItem(
                Math.max(item.rank(), 1),
                Math.max(item.studentUserId(), 0L),
                item.displayName() == null ? "" : item.displayName(),
                Math.max(item.score(), 0L),
                Math.max(item.postCount(), 0),
                Math.max(item.commentCount(), 0),
                Math.max(item.likeReceivedCount(), 0),
                item.latestActivityAt()
        );
    }
}
