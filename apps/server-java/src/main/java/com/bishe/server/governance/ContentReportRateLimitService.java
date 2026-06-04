package com.bishe.server.governance;

import com.bishe.server.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 举报频控：Redis 优先，miss 时回源数据库并回填。
 */
@Service
public class ContentReportRateLimitService {

    private static final Logger log = LoggerFactory.getLogger(ContentReportRateLimitService.class);
    private static final String KEY_PREFIX = "governance:report:rate-limit:";
    private static final DefaultRedisScript<Long> ACQUIRE_SLOT_SCRIPT = new DefaultRedisScript<>(
            """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local windowStart = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local member = ARGV[4]
            local ttlMs = tonumber(ARGV[5])
            redis.call('ZREMRANGEBYSCORE', key, '-inf', windowStart - 1)
            local count = redis.call('ZCARD', key)
            if count >= limit then
              return 0
            end
            redis.call('ZADD', key, now, member)
            redis.call('PEXPIRE', key, ttlMs)
            return 1
            """,
            Long.class
    );

    private final StringRedisTemplate stringRedisTemplate;
    private final UserRepository userRepository;

    public ContentReportRateLimitService(
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
            UserRepository userRepository
    ) {
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
        this.userRepository = userRepository;
    }

    public boolean tryAcquire(long reporterUserId, Instant now, int limit, Duration window, Supplier<List<Instant>> databaseLoader) {
        List<Instant> recentReports = databaseLoader == null ? List.of() : sanitize(databaseLoader.get());
        if (recentReports.size() >= limit) {
            return false;
        }
        if (stringRedisTemplate == null) {
            return true;
        }

        String key = buildKey(reporterUserId);
        try {
            Boolean hasKey = stringRedisTemplate.hasKey(key);
            if (Boolean.FALSE.equals(hasKey)) {
                backfillRecentReports(key, recentReports, now, window);
            }
            Long acquired = stringRedisTemplate.execute(
                    ACQUIRE_SLOT_SCRIPT,
                    List.of(key),
                    String.valueOf(now.toEpochMilli()),
                    String.valueOf(now.minus(window).toEpochMilli()),
                    String.valueOf(limit),
                    buildMember(now),
                    String.valueOf(resolveTtlMillis(window))
            );
            return Long.valueOf(1L).equals(acquired);
        } catch (Exception ex) {
            log.debug("report rate limit redis path failed, fallback to database: {}", ex.getMessage());
            return recentReports.size() < limit;
        }
    }

    private void backfillRecentReports(String key, List<Instant> recentReports, Instant now, Duration window) {
        if (recentReports.isEmpty()) {
            return;
        }
        List<ZSetOperations.TypedTuple<String>> tuples = new ArrayList<>();
        for (int index = 0; index < recentReports.size(); index++) {
            Instant timestamp = recentReports.get(index);
            tuples.add(ZSetOperations.TypedTuple.of(
                    "backfill:" + timestamp.toEpochMilli() + ":" + index,
                    (double) timestamp.toEpochMilli()
            ));
        }
        stringRedisTemplate.opsForZSet().add(key, new java.util.HashSet<>(tuples));
        stringRedisTemplate.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, now.minus(window).toEpochMilli() - 1L);
        stringRedisTemplate.expire(key, Duration.ofMillis(resolveTtlMillis(window)));
    }

    private String buildKey(long reporterUserId) {
        String scope = userRepository.findById(reporterUserId)
                .map(user -> "user-" + user.id() + "-" + (user.createdAt() == null ? 0L : user.createdAt().toEpochMilli()))
                .orElse("user-" + reporterUserId);
        return KEY_PREFIX + scope;
    }

    private String buildMember(Instant now) {
        return now.toEpochMilli() + ":" + UUID.randomUUID();
    }

    private long resolveTtlMillis(Duration window) {
        return Math.max(window.plusMinutes(1).toMillis(), 60_000L);
    }

    private List<Instant> sanitize(List<Instant> timestamps) {
        if (timestamps == null || timestamps.isEmpty()) {
            return List.of();
        }
        return timestamps.stream()
                .filter(java.util.Objects::nonNull)
                .sorted()
                .toList();
    }
}
