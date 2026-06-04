package com.bishe.server.mentor.service;

import com.bishe.server.mentor.dto.MentorListResponse;
import com.fasterxml.jackson.core.type.TypeReference;
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
 * 导师广场公开列表缓存：只缓存公共卡片主体，不缓存学生私有收藏态。
 */
@Service
public class MentorPublicListCacheService {

    private static final Logger log = LoggerFactory.getLogger(MentorPublicListCacheService.class);
    private static final String KEY_PREFIX = "mentor:list:public:";
    private static final String INDEX_KEY = "mentor:list:public:index";
    private static final Duration TTL = Duration.ofMinutes(3);
    private static final Duration INDEX_TTL = Duration.ofMinutes(10);
    private static final TypeReference<MentorListResponse> RESPONSE_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public MentorPublicListCacheService(
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider
    ) {
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    public MentorListResponse getMentorList(
            String keyword,
            String expertise,
            String scene,
            Integer minPrice,
            Integer maxPrice,
            Boolean available,
            int page,
            int size,
            Supplier<MentorListResponse> databaseLoader
    ) {
        if (databaseLoader == null) {
            return null;
        }
        if (stringRedisTemplate == null) {
            return sanitize(databaseLoader.get());
        }

        String key = buildKey(keyword, expertise, scene, minPrice, maxPrice, available, page, size);
        MentorListResponse cached = readFromRedis(key);
        if (cached != null) {
            return cached;
        }

        MentorListResponse loaded = sanitize(databaseLoader.get());
        if (loaded == null) {
            return null;
        }
        writeToRedis(key, loaded);
        MentorListResponse refreshed = readFromRedis(key);
        return refreshed == null ? loaded : refreshed;
    }

    public void evictAllNow() {
        evictAll();
    }

    public void evictAllAfterCommit() {
        runAfterCommit(this::evictAll);
    }

    private MentorListResponse readFromRedis(String key) {
        try {
            String payload = stringRedisTemplate.opsForValue().get(key);
            if (payload == null || payload.isBlank()) {
                return null;
            }
            return sanitize(objectMapper.readValue(payload, RESPONSE_TYPE));
        } catch (Exception ex) {
            log.debug("read mentor public list cache from redis failed: {}", ex.getMessage());
            return null;
        }
    }

    private void writeToRedis(String key, MentorListResponse response) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(response), TTL);
            stringRedisTemplate.opsForSet().add(INDEX_KEY, key);
            stringRedisTemplate.expire(INDEX_KEY, INDEX_TTL);
        } catch (Exception ex) {
            log.debug("write mentor public list cache to redis failed: {}", ex.getMessage());
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
            log.debug("evict mentor public list cache from redis failed: {}", ex.getMessage());
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

    private String buildKey(
            String keyword,
            String expertise,
            String scene,
            Integer minPrice,
            Integer maxPrice,
            Boolean available,
            int page,
            int size
    ) {
        String raw = String.join(
                "|",
                normalizeText(keyword),
                normalizeText(expertise),
                normalizeText(scene),
                minPrice == null ? "_" : String.valueOf(minPrice),
                maxPrice == null ? "_" : String.valueOf(maxPrice),
                available == null ? "_" : available.toString(),
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

    private MentorListResponse sanitize(MentorListResponse response) {
        if (response == null) {
            return null;
        }
        List<MentorListResponse.MentorItem> records = response.records() == null
                ? List.of()
                : response.records().stream()
                .filter(Objects::nonNull)
                .filter(item -> item.userId() > 0)
                .map(item -> new MentorListResponse.MentorItem(
                        item.userId(),
                        item.displayName(),
                        item.realName(),
                        item.showRealName(),
                        item.companyName(),
                        item.jobTitle(),
                        item.avatarUrl(),
                        item.expertiseTags() == null ? List.of() : item.expertiseTags().stream().filter(Objects::nonNull).toList(),
                        item.serviceScenes() == null ? List.of() : item.serviceScenes().stream().filter(Objects::nonNull).toList(),
                        item.bio(),
                        item.priceFen(),
                        item.avgRating(),
                        item.totalOrders(),
                        item.available(),
                        false
                ))
                .toList();
        return new MentorListResponse(records, response.total(), response.page(), response.size());
    }
}
