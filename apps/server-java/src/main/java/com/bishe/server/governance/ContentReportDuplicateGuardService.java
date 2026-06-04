package com.bishe.server.governance;

import com.bishe.server.auth.repository.UserRepository;
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
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 举报重复提交去重：Redis 优先返回短 TTL 重复举报结果，miss 时回退数据库判重。
 */
@Service
public class ContentReportDuplicateGuardService {

    private static final Logger log = LoggerFactory.getLogger(ContentReportDuplicateGuardService.class);
    private static final String KEY_PREFIX = "governance:report:dedupe:";
    private static final Duration TTL = Duration.ofHours(1);
    private static final TypeReference<DuplicateReportSnapshot> SNAPSHOT_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate stringRedisTemplate;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public ContentReportDuplicateGuardService(
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
            UserRepository userRepository,
            ObjectMapper objectMapper
    ) {
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    public ContentGovernanceRepository.ExistingReportRow findDuplicate(
            long reporterUserId,
            String targetType,
            String targetId,
            String reasonCode,
            Supplier<Optional<ContentGovernanceRepository.ExistingReportRow>> databaseLoader
    ) {
        if (reporterUserId <= 0 || databaseLoader == null) {
            return null;
        }
        if (stringRedisTemplate == null) {
            return sanitize(optionalRow(databaseLoader));
        }

        String key = buildKey(reporterUserId, targetType, targetId, reasonCode);
        ContentGovernanceRepository.ExistingReportRow cached = readFromRedis(key);
        if (cached != null) {
            return cached;
        }

        ContentGovernanceRepository.ExistingReportRow loaded = sanitize(optionalRow(databaseLoader));
        if (loaded != null) {
            rememberNow(reporterUserId, targetType, targetId, reasonCode, loaded);
        }
        return loaded;
    }

    public void rememberAfterCommit(
            long reporterUserId,
            String targetType,
            String targetId,
            String reasonCode,
            ContentGovernanceRepository.ExistingReportRow row
    ) {
        runAfterCommit(() -> rememberNow(reporterUserId, targetType, targetId, reasonCode, row));
    }

    public void evictNow(long reporterUserId, String targetType, String targetId, String reasonCode) {
        if (stringRedisTemplate == null || reporterUserId <= 0) {
            return;
        }
        try {
            stringRedisTemplate.delete(buildKey(reporterUserId, targetType, targetId, reasonCode));
        } catch (Exception ex) {
            log.debug("evict report duplicate guard cache from redis failed: {}", ex.getMessage());
        }
    }

    public void evictAfterCommit(long reporterUserId, String targetType, String targetId, String reasonCode) {
        runAfterCommit(() -> evictNow(reporterUserId, targetType, targetId, reasonCode));
    }

    private void rememberNow(
            long reporterUserId,
            String targetType,
            String targetId,
            String reasonCode,
            ContentGovernanceRepository.ExistingReportRow row
    ) {
        if (stringRedisTemplate == null) {
            return;
        }
        ContentGovernanceRepository.ExistingReportRow sanitized = sanitize(row);
        if (sanitized == null) {
            return;
        }
        try {
            DuplicateReportSnapshot snapshot = new DuplicateReportSnapshot(sanitized.reportId(), sanitized.status());
            stringRedisTemplate.opsForValue().set(
                    buildKey(reporterUserId, targetType, targetId, reasonCode),
                    objectMapper.writeValueAsString(snapshot),
                    TTL
            );
        } catch (Exception ex) {
            log.debug("write report duplicate guard cache to redis failed: {}", ex.getMessage());
        }
    }

    private ContentGovernanceRepository.ExistingReportRow readFromRedis(String key) {
        try {
            String payload = stringRedisTemplate.opsForValue().get(key);
            if (payload == null || payload.isBlank()) {
                return null;
            }
            DuplicateReportSnapshot snapshot = objectMapper.readValue(payload, SNAPSHOT_TYPE);
            return sanitize(snapshot == null ? null : new ContentGovernanceRepository.ExistingReportRow(
                    snapshot.reportId(),
                    snapshot.status()
            ));
        } catch (Exception ex) {
            log.debug("read report duplicate guard cache from redis failed: {}", ex.getMessage());
            return null;
        }
    }

    private ContentGovernanceRepository.ExistingReportRow optionalRow(
            Supplier<Optional<ContentGovernanceRepository.ExistingReportRow>> databaseLoader
    ) {
        Optional<ContentGovernanceRepository.ExistingReportRow> optional = databaseLoader.get();
        return optional == null ? null : optional.orElse(null);
    }

    private ContentGovernanceRepository.ExistingReportRow sanitize(ContentGovernanceRepository.ExistingReportRow row) {
        if (row == null || row.reportId() <= 0 || row.status() == null || row.status().isBlank()) {
            return null;
        }
        return new ContentGovernanceRepository.ExistingReportRow(
                row.reportId(),
                row.status().trim().toUpperCase(Locale.ROOT)
        );
    }

    private String buildKey(long reporterUserId, String targetType, String targetId, String reasonCode) {
        String scope = userRepository.findById(reporterUserId)
                .map(user -> "user-" + user.id() + "-" + (user.createdAt() == null ? 0L : user.createdAt().toEpochMilli()))
                .orElse("user-" + reporterUserId);
        return KEY_PREFIX
                + scope
                + ":"
                + normalize(targetType)
                + ":"
                + normalize(targetId)
                + ":"
                + normalize(reasonCode);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "NA";
        }
        return value.trim().toUpperCase(Locale.ROOT);
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

    private record DuplicateReportSnapshot(long reportId, String status) {
    }
}
