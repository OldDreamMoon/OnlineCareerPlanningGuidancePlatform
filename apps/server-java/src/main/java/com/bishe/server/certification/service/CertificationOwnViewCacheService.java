package com.bishe.server.certification.service;

import com.bishe.server.certification.dto.CertificationAssetResponse;
import com.bishe.server.certification.dto.CertificationOwnViewResponse;
import com.bishe.server.certification.dto.CertificationSubmissionResponse;
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
 * 当前用户认证概览缓存：按用户缓存 own view 摘要，不缓存附件内容本体。
 */
@Service
public class CertificationOwnViewCacheService {

    private static final Logger log = LoggerFactory.getLogger(CertificationOwnViewCacheService.class);
    private static final String KEY_PREFIX = "certification:own-view:";
    private static final Duration TTL = Duration.ofMinutes(2);
    private static final TypeReference<CertificationOwnViewResponse> RESPONSE_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public CertificationOwnViewCacheService(
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider
    ) {
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    public CertificationOwnViewResponse getOwnView(
            long userId,
            Supplier<CertificationOwnViewResponse> databaseLoader
    ) {
        if (userId <= 0 || databaseLoader == null) {
            return null;
        }
        if (stringRedisTemplate == null) {
            return sanitize(databaseLoader.get());
        }

        String key = buildKey(userId);
        CertificationOwnViewResponse cached = readFromRedis(key);
        if (cached != null) {
            return cached;
        }

        CertificationOwnViewResponse loaded = sanitize(databaseLoader.get());
        if (loaded == null) {
            return null;
        }
        writeToRedis(key, loaded);
        CertificationOwnViewResponse refreshed = readFromRedis(key);
        return refreshed == null ? loaded : refreshed;
    }

    public void evictNow(long userId) {
        evict(userId);
    }

    public void evictAfterCommit(long userId) {
        runAfterCommit(() -> evict(userId));
    }

    private CertificationOwnViewResponse readFromRedis(String key) {
        try {
            String payload = stringRedisTemplate.opsForValue().get(key);
            if (payload == null || payload.isBlank()) {
                return null;
            }
            return sanitize(objectMapper.readValue(payload, RESPONSE_TYPE));
        } catch (Exception ex) {
            log.debug("read certification own view cache from redis failed: {}", ex.getMessage());
            return null;
        }
    }

    private void writeToRedis(String key, CertificationOwnViewResponse payload) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(payload), TTL);
        } catch (Exception ex) {
            log.debug("write certification own view cache to redis failed: {}", ex.getMessage());
        }
    }

    private void evict(long userId) {
        if (stringRedisTemplate == null || userId <= 0) {
            return;
        }
        try {
            stringRedisTemplate.delete(buildKey(userId));
        } catch (Exception ex) {
            log.debug("evict certification own view cache from redis failed: {}", ex.getMessage());
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

    private String buildKey(long userId) {
        return KEY_PREFIX + userId;
    }

    private CertificationOwnViewResponse sanitize(CertificationOwnViewResponse response) {
        if (response == null || response.userId() == null || response.userId() <= 0
                || response.role() == null || response.role().isBlank()) {
            return null;
        }
        List<CertificationSubmissionResponse> submissions = response.submissions() == null
                ? List.of()
                : response.submissions().stream()
                .filter(Objects::nonNull)
                .map(this::sanitizeSubmission)
                .filter(Objects::nonNull)
                .toList();
        CertificationSubmissionResponse currentSubmission = sanitizeSubmission(response.currentSubmission());
        return new CertificationOwnViewResponse(
                response.userId(),
                response.role(),
                response.approvalStatus(),
                currentSubmission,
                submissions
        );
    }

    private CertificationSubmissionResponse sanitizeSubmission(CertificationSubmissionResponse submission) {
        if (submission == null || submission.submissionId() == null || submission.submissionId() <= 0
                || submission.userId() == null || submission.userId() <= 0) {
            return null;
        }
        List<CertificationAssetResponse> assets = submission.assets() == null
                ? List.of()
                : submission.assets().stream()
                .filter(Objects::nonNull)
                .toList();
        return new CertificationSubmissionResponse(
                submission.submissionId(),
                submission.userId(),
                submission.role(),
                submission.realName(),
                submission.companyName(),
                submission.jobTitle(),
                submission.status(),
                submission.current(),
                submission.reviewNote(),
                submission.previousSubmissionId(),
                submission.submittedAt(),
                submission.reviewedAt(),
                assets
        );
    }
}
