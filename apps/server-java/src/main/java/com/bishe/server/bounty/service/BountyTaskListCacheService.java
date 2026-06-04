package com.bishe.server.bounty.service;

import com.bishe.server.bounty.dto.BountyTaskListResponse;
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
 * 悬赏公开列表缓存：只缓存公共列表主体，不缓存当前学生的 submittedByMe 状态。
 */
@Service
public class BountyTaskListCacheService {

    private static final Logger log = LoggerFactory.getLogger(BountyTaskListCacheService.class);
    private static final String KEY_PREFIX = "bounty:tasks:list:";
    private static final String INDEX_KEY = "bounty:tasks:list:index";
    private static final Duration TTL = Duration.ofMinutes(2);
    private static final Duration INDEX_TTL = Duration.ofMinutes(10);

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public BountyTaskListCacheService(
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider
    ) {
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    public BountyTaskListResponse getTaskList(
            String keyword,
            String status,
            int page,
            int size,
            Supplier<BountyTaskListResponse> databaseLoader
    ) {
        if (databaseLoader == null) {
            return null;
        }
        if (stringRedisTemplate == null) {
            // Redis 缺席时直接读库，viewer 态仍由上层 BountyService 合成。
            return sanitize(databaseLoader.get());
        }

        String key = buildKey(keyword, status, page, size);
        BountyTaskListResponse cached = readFromRedis(key);
        if (cached != null) {
            return cached;
        }

        BountyTaskListResponse loaded = sanitize(databaseLoader.get());
        if (loaded == null) {
            return null;
        }
        writeToRedis(key, loaded);
        // 写后回读验证序列化结构，失败则直接返回本次数据库结果。
        BountyTaskListResponse refreshed = readFromRedis(key);
        return refreshed == null ? loaded : refreshed;
    }

    public void evictAllNow() {
        evictAll();
    }

    public void evictAllAfterCommit() {
        runAfterCommit(this::evictAll);
    }

    private BountyTaskListResponse readFromRedis(String key) {
        try {
            String payload = stringRedisTemplate.opsForValue().get(key);
            if (payload == null || payload.isBlank()) {
                return null;
            }
            return sanitize(objectMapper.readValue(payload, BountyTaskListResponse.class));
        } catch (Exception ex) {
            log.debug("read bounty task list cache from redis failed: {}", ex.getMessage());
            return null;
        }
    }

    private void writeToRedis(String key, BountyTaskListResponse payload) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(payload), TTL);
            SetOperations<String, String> setOperations = stringRedisTemplate.opsForSet();
            // index 记录本轮产生过的列表 key，任务变更时可一次性清理所有筛选页。
            setOperations.add(INDEX_KEY, key);
            stringRedisTemplate.expire(INDEX_KEY, INDEX_TTL);
        } catch (Exception ex) {
            log.debug("write bounty task list cache to redis failed: {}", ex.getMessage());
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
            log.debug("evict bounty task list cache from redis failed: {}", ex.getMessage());
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

    private String buildKey(String keyword, String status, int page, int size) {
        String raw = String.join(
                "|",
                normalizeText(keyword),
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

    private BountyTaskListResponse sanitize(BountyTaskListResponse response) {
        if (response == null) {
            return null;
        }
        // 公共缓存强制剥离 submittedByMe，避免 A 学生的提交状态污染 B 学生。
        List<BountyTaskListResponse.TaskItem> records = response.records() == null
                ? List.of()
                : response.records().stream()
                .filter(Objects::nonNull)
                .map(item -> new BountyTaskListResponse.TaskItem(
                        item.taskId(),
                        item.enterpriseUserId(),
                        item.enterpriseName(),
                        item.enterpriseLogoUrl(),
                        item.title(),
                        item.descriptionSummary(),
                        item.rewardDescription(),
                        item.status(),
                        Math.max(item.submissionCount(), 0),
                        item.acceptedSubmissionId(),
                        false,
                        item.deadlineAt(),
                        item.createdAt(),
                        item.updatedAt()
                ))
                .toList();
        return new BountyTaskListResponse(
                records,
                Math.max(response.total(), 0L),
                Math.max(response.page(), 1),
                Math.min(Math.max(response.size(), 1), 50)
        );
    }
}
