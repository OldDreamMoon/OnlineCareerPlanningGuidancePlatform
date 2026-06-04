package com.bishe.server.mentor.service;

import com.bishe.server.mentor.dto.MentorServicePackageResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 导师公开详情主体缓存：只缓存公共信息，不缓存 favorited 等用户态字段。
 */
@Service
public class MentorPublicDetailCacheService {

    private static final Logger log = LoggerFactory.getLogger(MentorPublicDetailCacheService.class);
    private static final String KEY_PREFIX = "mentor:detail:public:";
    private static final Duration TTL = Duration.ofMinutes(20);
    private static final TypeReference<MentorPublicDetailSnapshot> SNAPSHOT_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public MentorPublicDetailCacheService(
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider
    ) {
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    public MentorPublicDetailSnapshot getPublicDetail(
            long mentorUserId,
            Supplier<MentorPublicDetailSnapshot> databaseLoader
    ) {
        if (mentorUserId <= 0 || databaseLoader == null) {
            return null;
        }
        if (stringRedisTemplate == null) {
            return sanitize(databaseLoader.get());
        }

        String key = buildKey(mentorUserId);
        MentorPublicDetailSnapshot cached = readFromRedis(key);
        if (cached != null) {
            return cached;
        }

        MentorPublicDetailSnapshot loaded = sanitize(databaseLoader.get());
        if (loaded == null) {
            return null;
        }
        writeToRedis(key, loaded);
        MentorPublicDetailSnapshot refreshed = readFromRedis(key);
        return refreshed == null ? loaded : refreshed;
    }

    public void evictNow(long mentorUserId) {
        evict(mentorUserId);
    }

    public void evictAfterCommit(long mentorUserId) {
        runAfterCommit(() -> evict(mentorUserId));
    }

    private MentorPublicDetailSnapshot readFromRedis(String key) {
        try {
            String payload = stringRedisTemplate.opsForValue().get(key);
            if (payload == null || payload.isBlank()) {
                return null;
            }
            return sanitize(objectMapper.readValue(payload, SNAPSHOT_TYPE));
        } catch (Exception ex) {
            log.debug("read mentor public detail cache from redis failed: {}", ex.getMessage());
            return null;
        }
    }

    private void writeToRedis(String key, MentorPublicDetailSnapshot snapshot) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(snapshot), TTL);
        } catch (Exception ex) {
            log.debug("write mentor public detail cache to redis failed: {}", ex.getMessage());
        }
    }

    private void evict(long mentorUserId) {
        if (stringRedisTemplate == null || mentorUserId <= 0) {
            return;
        }
        try {
            stringRedisTemplate.delete(buildKey(mentorUserId));
        } catch (Exception ex) {
            log.debug("evict mentor public detail cache from redis failed: {}", ex.getMessage());
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

    private String buildKey(long mentorUserId) {
        return KEY_PREFIX + mentorUserId;
    }

    private MentorPublicDetailSnapshot sanitize(MentorPublicDetailSnapshot snapshot) {
        if (snapshot == null || snapshot.userId() <= 0) {
            return null;
        }
        return new MentorPublicDetailSnapshot(
                snapshot.userId(),
                snapshot.displayName(),
                snapshot.realName(),
                snapshot.showRealName(),
                snapshot.companyName(),
                snapshot.jobTitle(),
                snapshot.avatarUrl(),
                snapshot.avatarObjectKey(),
                snapshot.avatarUpdatedAt(),
                sanitizeList(snapshot.expertiseTags()),
                sanitizeList(snapshot.serviceScenes()),
                snapshot.bio(),
                snapshot.suitableFor(),
                snapshot.notSuitableFor(),
                snapshot.prepMaterials(),
                snapshot.replyRhythm(),
                snapshot.priceFen(),
                sanitizePackageList(snapshot.packages()),
                snapshot.avgRating(),
                snapshot.totalOrders(),
                snapshot.available()
        );
    }

    private List<String> sanitizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    private List<MentorServicePackageResponse> sanitizePackageList(List<MentorServicePackageResponse> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    public record MentorPublicDetailSnapshot(
            long userId,
            String displayName,
            String realName,
            boolean showRealName,
            String companyName,
            String jobTitle,
            String avatarUrl,
            String avatarObjectKey,
            Instant avatarUpdatedAt,
            List<String> expertiseTags,
            List<String> serviceScenes,
            String bio,
            String suitableFor,
            String notSuitableFor,
            String prepMaterials,
            String replyRhythm,
            int priceFen,
            List<MentorServicePackageResponse> packages,
            BigDecimal avgRating,
            int totalOrders,
            boolean available
    ) {
    }
}
