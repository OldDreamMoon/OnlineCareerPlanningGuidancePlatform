package com.bishe.server.ai.quota;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * AI 分钟级限流：按用户作用域维护滑动窗口，优先走 Redis，异常时放行回退。
 */
@Service
public class AiMinuteRateLimitService {

    private static final Logger log = LoggerFactory.getLogger(AiMinuteRateLimitService.class);
    private static final String KEY_PREFIX = "ai:ratelimit:minute:";
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final Duration KEY_TTL = Duration.ofMinutes(2);
    static final int FREE_LIMIT_PER_MINUTE = 12;
    static final int PREMIUM_LIMIT_PER_MINUTE = 30;
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> ACQUIRE_SCRIPT = buildAcquireScript();

    private final StringRedisTemplate stringRedisTemplate;

    public AiMinuteRateLimitService(ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider) {
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    public RateLimitDecision acquire(String userScope, String tier, String taskType, String traceId) {
        int limit = resolveLimit(tier);
        if (!StringUtils.hasText(userScope) || stringRedisTemplate == null || limit < 0) {
            return RateLimitDecision.allowed(limit, 0);
        }

        String key = buildKey(userScope);
        long nowEpochMillis = Instant.now().toEpochMilli();
        String member = buildMember(traceId, taskType, nowEpochMillis);
        try {
            @SuppressWarnings("unchecked")
            List<Long> scriptResult = stringRedisTemplate.execute(
                    ACQUIRE_SCRIPT,
                    Collections.singletonList(key),
                    String.valueOf(nowEpochMillis),
                    String.valueOf(WINDOW.toMillis()),
                    String.valueOf(limit),
                    String.valueOf(KEY_TTL.getSeconds()),
                    member
            );
            return mapDecision(scriptResult, limit);
        } catch (Exception ex) {
            log.debug("acquire ai minute rate limit from redis failed: {}", ex.getMessage());
            return RateLimitDecision.allowed(limit, 0);
        }
    }

    private RateLimitDecision mapDecision(List<Long> scriptResult, int limit) {
        if (scriptResult == null || scriptResult.size() < 3) {
            return RateLimitDecision.allowed(limit, 0);
        }
        long allowedFlag = safeLong(scriptResult.get(0));
        int currentCount = Math.max((int) safeLong(scriptResult.get(1)), 0);
        int retryAfterSeconds = Math.max((int) safeLong(scriptResult.get(2)), 0);
        if (allowedFlag == 1L) {
            return RateLimitDecision.allowed(limit, currentCount);
        }
        return RateLimitDecision.blocked(limit, currentCount, retryAfterSeconds);
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private int resolveLimit(String tier) {
        if ("PREMIUM".equalsIgnoreCase(tier)) {
            return PREMIUM_LIMIT_PER_MINUTE;
        }
        return FREE_LIMIT_PER_MINUTE;
    }

    private String buildKey(String userScope) {
        return KEY_PREFIX + userScope.trim();
    }

    private String buildMember(String traceId, String taskType, long nowEpochMillis) {
        String normalizedTraceId = StringUtils.hasText(traceId) ? traceId.trim() : "anonymous";
        String normalizedTaskType = StringUtils.hasText(taskType) ? taskType.trim().toUpperCase() : "UNKNOWN";
        return nowEpochMillis + ":" + normalizedTaskType + ":" + normalizedTraceId;
    }

    @SuppressWarnings("rawtypes")
    private static DefaultRedisScript<List> buildAcquireScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptText("""
                local key = KEYS[1]
                local now = tonumber(ARGV[1])
                local window_millis = tonumber(ARGV[2])
                local limit = tonumber(ARGV[3])
                local ttl_seconds = tonumber(ARGV[4])
                local member = ARGV[5]
                local min_score = now - window_millis

                redis.call('ZREMRANGEBYSCORE', key, '-inf', min_score)
                local current = redis.call('ZCARD', key)
                if current >= limit then
                    local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
                    local retry_after = 1
                    if oldest ~= false and oldest[2] ~= nil then
                        retry_after = math.max(1, math.ceil(((tonumber(oldest[2]) + window_millis) - now) / 1000))
                    end
                    return {0, current, retry_after}
                end

                redis.call('ZADD', key, now, member)
                redis.call('EXPIRE', key, ttl_seconds)
                return {1, current + 1, 0}
                """);
        script.setResultType(List.class);
        return script;
    }

    public record RateLimitDecision(
            boolean allowed,
            int limit,
            int currentCount,
            int retryAfterSeconds
    ) {
        static RateLimitDecision allowed(int limit, int currentCount) {
            return new RateLimitDecision(true, limit, Math.max(currentCount, 0), 0);
        }

        static RateLimitDecision blocked(int limit, int currentCount, int retryAfterSeconds) {
            return new RateLimitDecision(false, limit, Math.max(currentCount, 0), Math.max(retryAfterSeconds, 0));
        }
    }
}
