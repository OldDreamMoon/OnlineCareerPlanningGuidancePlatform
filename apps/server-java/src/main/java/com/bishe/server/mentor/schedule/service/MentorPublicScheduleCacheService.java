package com.bishe.server.mentor.schedule.service;

import com.bishe.server.mentor.schedule.dto.MentorScheduleSlotListResponse;
import com.bishe.server.mentor.schedule.dto.MentorScheduleSlotResponse;
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
import java.util.Set;
import java.util.function.Supplier;

/**
 * 导师公开排期缓存：只缓存学生侧可见的 AVAILABLE 时段列表。
 */
@Service
public class MentorPublicScheduleCacheService {

    private static final Logger log = LoggerFactory.getLogger(MentorPublicScheduleCacheService.class);
    private static final String KEY_PREFIX = "mentor:schedule:public:";
    private static final String INDEX_KEY_PREFIX = "mentor:schedule:public:index:";
    private static final Duration TTL = Duration.ofMinutes(3);
    private static final Duration INDEX_TTL = Duration.ofMinutes(10);
    private static final TypeReference<MentorScheduleSlotListResponse> RESPONSE_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public MentorPublicScheduleCacheService(
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider
    ) {
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    public MentorScheduleSlotListResponse getPublicSlots(
            long mentorUserId,
            Instant dateFrom,
            Instant dateTo,
            Supplier<MentorScheduleSlotListResponse> databaseLoader
    ) {
        if (mentorUserId <= 0 || databaseLoader == null) {
            return null;
        }
        if (stringRedisTemplate == null) {
            return sanitize(databaseLoader.get());
        }

        String key = buildKey(mentorUserId, dateFrom, dateTo);
        MentorScheduleSlotListResponse cached = readFromRedis(key);
        if (cached != null) {
            return cached;
        }

        MentorScheduleSlotListResponse loaded = sanitize(databaseLoader.get());
        if (loaded == null) {
            return null;
        }
        writeToRedis(mentorUserId, key, loaded);
        MentorScheduleSlotListResponse refreshed = readFromRedis(key);
        return refreshed == null ? loaded : refreshed;
    }

    public void evictNow(long mentorUserId) {
        evict(mentorUserId);
    }

    public void evictAfterCommit(long mentorUserId) {
        runAfterCommit(() -> evict(mentorUserId));
    }

    private MentorScheduleSlotListResponse readFromRedis(String key) {
        try {
            String payload = stringRedisTemplate.opsForValue().get(key);
            if (payload == null || payload.isBlank()) {
                return null;
            }
            return sanitize(objectMapper.readValue(payload, RESPONSE_TYPE));
        } catch (Exception ex) {
            log.debug("read mentor public schedule cache from redis failed: {}", ex.getMessage());
            return null;
        }
    }

    private void writeToRedis(long mentorUserId, String key, MentorScheduleSlotListResponse response) {
        String indexKey = buildIndexKey(mentorUserId);
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(response), TTL);
            stringRedisTemplate.opsForSet().add(indexKey, key);
            stringRedisTemplate.expire(indexKey, INDEX_TTL);
        } catch (Exception ex) {
            log.debug("write mentor public schedule cache to redis failed: {}", ex.getMessage());
        }
    }

    private void evict(long mentorUserId) {
        if (stringRedisTemplate == null || mentorUserId <= 0) {
            return;
        }
        String indexKey = buildIndexKey(mentorUserId);
        try {
            Set<String> keys = stringRedisTemplate.opsForSet().members(indexKey);
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
            stringRedisTemplate.delete(indexKey);
        } catch (Exception ex) {
            log.debug("evict mentor public schedule cache from redis failed: {}", ex.getMessage());
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

    private String buildKey(long mentorUserId, Instant dateFrom, Instant dateTo) {
        return KEY_PREFIX + mentorUserId + ":" + encodeInstant(dateFrom) + ":" + encodeInstant(dateTo);
    }

    private String buildIndexKey(long mentorUserId) {
        return INDEX_KEY_PREFIX + mentorUserId;
    }

    private String encodeInstant(Instant value) {
        return value == null ? "_" : Long.toString(value.toEpochMilli());
    }

    private MentorScheduleSlotListResponse sanitize(MentorScheduleSlotListResponse response) {
        if (response == null) {
            return null;
        }
        List<MentorScheduleSlotResponse> records = response.records() == null
                ? List.of()
                : response.records().stream()
                .filter(Objects::nonNull)
                .filter(item -> item.id() > 0)
                .toList();
        return new MentorScheduleSlotListResponse(records);
    }
}
