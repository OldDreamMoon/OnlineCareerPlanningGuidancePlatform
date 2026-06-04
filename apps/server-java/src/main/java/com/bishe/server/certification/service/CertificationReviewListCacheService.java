package com.bishe.server.certification.service;

import com.bishe.server.certification.dto.CertificationReviewListResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.SetOperations;
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
 * 管理员认证审核列表缓存：只缓存分页摘要，不缓存详情与附件内容。
 */
@Service
public class CertificationReviewListCacheService {

    private static final Logger log = LoggerFactory.getLogger(CertificationReviewListCacheService.class);
    private static final String KEY_PREFIX = "certification:reviews:list:";
    private static final String INDEX_KEY = "certification:reviews:list:index";
    private static final Duration TTL = Duration.ofMinutes(2);
    private static final Duration INDEX_TTL = Duration.ofMinutes(10);

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public CertificationReviewListCacheService(
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider
    ) {
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    public CertificationReviewListResponse getReviewList(
            String keyword,
            String role,
            String status,
            int page,
            int size,
            Supplier<CertificationReviewListResponse> databaseLoader
    ) {
        if (databaseLoader == null) {
            return null;
        }
        if (stringRedisTemplate == null) {
            return sanitize(databaseLoader.get());
        }

        String key = buildKey(keyword, role, status, page, size);
        CertificationReviewListResponse cached = readFromRedis(key);
        if (cached != null) {
            return cached;
        }

        CertificationReviewListResponse loaded = sanitize(databaseLoader.get());
        if (loaded == null) {
            return null;
        }
        writeToRedis(key, loaded);
        CertificationReviewListResponse refreshed = readFromRedis(key);
        return refreshed == null ? loaded : refreshed;
    }

    public void evictAllNow() {
        evictAll();
    }

    public void evictAllAfterCommit() {
        runAfterCommit(this::evictAll);
    }

    private CertificationReviewListResponse readFromRedis(String key) {
        try {
            String payload = stringRedisTemplate.opsForValue().get(key);
            if (payload == null || payload.isBlank()) {
                return null;
            }
            return sanitize(objectMapper.readValue(payload, CertificationReviewListResponse.class));
        } catch (Exception ex) {
            log.debug("read certification review list cache from redis failed: {}", ex.getMessage());
            return null;
        }
    }

    private void writeToRedis(String key, CertificationReviewListResponse payload) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(payload), TTL);
            SetOperations<String, String> setOperations = stringRedisTemplate.opsForSet();
            setOperations.add(INDEX_KEY, key);
            stringRedisTemplate.expire(INDEX_KEY, INDEX_TTL);
        } catch (Exception ex) {
            log.debug("write certification review list cache to redis failed: {}", ex.getMessage());
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
            log.debug("evict certification review list cache from redis failed: {}", ex.getMessage());
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

    private String buildKey(String keyword, String role, String status, int page, int size) {
        String raw = String.join(
                "|",
                normalizeText(keyword),
                normalizeText(role),
                normalizeText(status),
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

    private CertificationReviewListResponse sanitize(CertificationReviewListResponse response) {
        if (response == null) {
            return null;
        }
        List<CertificationReviewListResponse.ReviewItem> records = response.records() == null
                ? List.of()
                : response.records().stream()
                .filter(Objects::nonNull)
                .map(item -> new CertificationReviewListResponse.ReviewItem(
                        item.userId(),
                        item.email(),
                        item.displayName(),
                        item.role(),
                        item.approvalStatus(),
                        item.submissionId(),
                        item.submissionStatus(),
                        item.realName(),
                        item.companyName(),
                        item.jobTitle(),
                        Math.max(item.activeAssetCount() == null ? 0 : item.activeAssetCount(), 0),
                        item.primaryAssetName(),
                        item.submittedAt(),
                        item.reviewedAt()
                ))
                .toList();
        return new CertificationReviewListResponse(
                records,
                response.total() == null ? 0L : Math.max(response.total(), 0L),
                response.page() == null ? 1 : Math.max(response.page(), 1),
                response.size() == null ? 10 : Math.max(response.size(), 1)
        );
    }
}
