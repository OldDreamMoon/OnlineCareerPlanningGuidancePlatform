package com.bishe.server.ai.gateway.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AI 异步任务 worker 的 Redis 协调态：ready queue 与 in-flight lease。
 */
@Service
public class AiAsyncTaskCoordinationService {

    private static final Logger log = LoggerFactory.getLogger(AiAsyncTaskCoordinationService.class);
    private static final String READY_QUEUE_KEY = "ai:async:ready";
    private static final String LEASE_KEY_PREFIX = "ai:async:lease:";
    private static final Duration DEFAULT_LEASE_TTL = Duration.ofSeconds(60);
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> CLAIM_DUE_SCRIPT = buildClaimDueScript();

    private final StringRedisTemplate stringRedisTemplate;

    public AiAsyncTaskCoordinationService(ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider) {
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    public void schedulePendingAfterCommit(long jobId, Instant nextRunAt) {
        runAfterCommit(() -> scheduleNow(jobId, nextRunAt));
    }

    public void scheduleRetryAfterCommit(long jobId, Instant nextRunAt) {
        runAfterCommit(() -> scheduleNow(jobId, nextRunAt));
    }

    public void clearAfterCommit(long jobId) {
        runAfterCommit(() -> clearNow(jobId));
    }

    public void scheduleNow(long jobId, Instant nextRunAt) {
        if (jobId <= 0 || stringRedisTemplate == null) {
            return;
        }
        try {
            ZSetOperations<String, String> zSetOperations = stringRedisTemplate.opsForZSet();
            zSetOperations.add(READY_QUEUE_KEY, Long.toString(jobId), resolveScore(nextRunAt));
        } catch (Exception ex) {
            log.debug("schedule ai async task into redis ready queue failed: {}", ex.getMessage());
        }
    }

    public void clearNow(long jobId) {
        if (jobId <= 0 || stringRedisTemplate == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForZSet().remove(READY_QUEUE_KEY, Long.toString(jobId));
            stringRedisTemplate.delete(buildLeaseKey(jobId));
        } catch (Exception ex) {
            log.debug("clear ai async task coordination state failed: {}", ex.getMessage());
        }
    }

    public void releaseLeaseNow(long jobId) {
        if (jobId <= 0 || stringRedisTemplate == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(buildLeaseKey(jobId));
        } catch (Exception ex) {
            log.debug("release ai async task lease failed: {}", ex.getMessage());
        }
    }

    public List<Long> claimDueJobIds(String workerId, int limit, Duration leaseDuration) {
        if (!StringUtils.hasText(workerId) || limit <= 0 || stringRedisTemplate == null) {
            return List.of();
        }
        Duration effectiveLease = normalizeLeaseDuration(leaseDuration);
        long nowMillis = Instant.now().toEpochMilli();
        try {
            @SuppressWarnings("unchecked")
            List<Object> rawResult = stringRedisTemplate.execute(
                    CLAIM_DUE_SCRIPT,
                    Collections.singletonList(READY_QUEUE_KEY),
                    Long.toString(nowMillis),
                    Integer.toString(limit),
                    Long.toString(Math.max(effectiveLease.getSeconds(), 1L)),
                    LEASE_KEY_PREFIX,
                    workerId.trim()
            );
            return mapJobIds(rawResult);
        } catch (Exception ex) {
            log.debug("claim ai async tasks from redis failed: {}", ex.getMessage());
            return List.of();
        }
    }

    private double resolveScore(Instant nextRunAt) {
        Instant effectiveTime = nextRunAt == null ? Instant.now() : nextRunAt;
        return effectiveTime.toEpochMilli();
    }

    private Duration normalizeLeaseDuration(Duration leaseDuration) {
        if (leaseDuration == null || leaseDuration.isNegative() || leaseDuration.isZero()) {
            return DEFAULT_LEASE_TTL;
        }
        return leaseDuration;
    }

    private List<Long> mapJobIds(List<Object> rawResult) {
        if (rawResult == null || rawResult.isEmpty()) {
            return List.of();
        }
        List<Long> jobIds = new ArrayList<>();
        for (Object item : rawResult) {
            if (item == null) {
                continue;
            }
            try {
                long jobId = Long.parseLong(String.valueOf(item));
                if (jobId > 0) {
                    jobIds.add(jobId);
                }
            } catch (NumberFormatException ignored) {
                // ignore broken member and continue
            }
        }
        return jobIds;
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

    private String buildLeaseKey(long jobId) {
        return LEASE_KEY_PREFIX + jobId;
    }

    @SuppressWarnings("rawtypes")
    private static DefaultRedisScript<List> buildClaimDueScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptText("""
                local ready_key = KEYS[1]
                local now_millis = tonumber(ARGV[1])
                local limit = tonumber(ARGV[2])
                local lease_ttl_seconds = tonumber(ARGV[3])
                local lease_key_prefix = ARGV[4]
                local worker_id = ARGV[5]

                local candidates = redis.call('ZRANGEBYSCORE', ready_key, '-inf', now_millis, 'LIMIT', 0, math.max(limit * 4, limit))
                local claimed = {}
                for _, job_id in ipairs(candidates) do
                    local lease_key = lease_key_prefix .. job_id
                    local lease_ok = redis.call('SET', lease_key, worker_id, 'NX', 'EX', lease_ttl_seconds)
                    if lease_ok then
                        redis.call('ZREM', ready_key, job_id)
                        table.insert(claimed, job_id)
                        if #claimed >= limit then
                            break
                        end
                    end
                end
                return claimed
                """);
        script.setResultType(List.class);
        return script;
    }
}
