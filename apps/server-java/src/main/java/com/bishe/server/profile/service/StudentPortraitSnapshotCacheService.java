package com.bishe.server.profile.service;

import com.bishe.server.profile.repository.StudentProfileRepository;
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
import java.util.function.Supplier;

/**
 * 学生私有画像快照缓存：仅缓存资料中心读取所需的画像摘要快照。
 */
@Service
public class StudentPortraitSnapshotCacheService {

    private static final Logger log = LoggerFactory.getLogger(StudentPortraitSnapshotCacheService.class);
    private static final String KEY_PREFIX = "student:portrait:snapshot:";
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final TypeReference<StudentProfileRepository.PortraitSnapshotRow> SNAPSHOT_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public StudentPortraitSnapshotCacheService(
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider
    ) {
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    public StudentProfileRepository.PortraitSnapshotRow getSnapshot(
            long studentUserId,
            Supplier<StudentProfileRepository.PortraitSnapshotRow> databaseLoader
    ) {
        if (studentUserId <= 0 || databaseLoader == null) {
            return null;
        }
        if (stringRedisTemplate == null) {
            return sanitize(databaseLoader.get());
        }

        String key = buildKey(studentUserId);
        StudentProfileRepository.PortraitSnapshotRow cached = readFromRedis(key);
        if (cached != null) {
            return cached;
        }

        StudentProfileRepository.PortraitSnapshotRow loaded = sanitize(databaseLoader.get());
        if (loaded == null) {
            return null;
        }
        writeToRedis(key, loaded);
        StudentProfileRepository.PortraitSnapshotRow refreshed = readFromRedis(key);
        return refreshed == null ? loaded : refreshed;
    }

    public void evictNow(long studentUserId) {
        evict(studentUserId);
    }

    public void evictAfterCommit(long studentUserId) {
        runAfterCommit(() -> evict(studentUserId));
    }

    private StudentProfileRepository.PortraitSnapshotRow readFromRedis(String key) {
        try {
            String payload = stringRedisTemplate.opsForValue().get(key);
            if (payload == null || payload.isBlank()) {
                return null;
            }
            return sanitize(objectMapper.readValue(payload, SNAPSHOT_TYPE));
        } catch (Exception ex) {
            log.debug("read student portrait snapshot cache from redis failed: {}", ex.getMessage());
            return null;
        }
    }

    private void writeToRedis(String key, StudentProfileRepository.PortraitSnapshotRow snapshot) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(snapshot), TTL);
        } catch (Exception ex) {
            log.debug("write student portrait snapshot cache to redis failed: {}", ex.getMessage());
        }
    }

    private void evict(long studentUserId) {
        if (stringRedisTemplate == null || studentUserId <= 0) {
            return;
        }
        try {
            stringRedisTemplate.delete(buildKey(studentUserId));
        } catch (Exception ex) {
            log.debug("evict student portrait snapshot cache from redis failed: {}", ex.getMessage());
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

    private String buildKey(long studentUserId) {
        return KEY_PREFIX + studentUserId;
    }

    private StudentProfileRepository.PortraitSnapshotRow sanitize(StudentProfileRepository.PortraitSnapshotRow snapshot) {
        if (snapshot == null) {
            return null;
        }
        return new StudentProfileRepository.PortraitSnapshotRow(
                normalizeJson(snapshot.portraitTagsJson(), "[]"),
                normalizeJson(snapshot.evidenceJson(), "{}"),
                snapshot.updatedAt()
        );
    }

    private String normalizeJson(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
