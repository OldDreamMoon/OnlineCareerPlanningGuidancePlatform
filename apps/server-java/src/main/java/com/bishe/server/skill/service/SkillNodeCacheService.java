package com.bishe.server.skill.service;

import com.bishe.server.skill.repository.SkillRepository;
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
import java.util.function.Supplier;

/**
 * 技能树静态节点缓存：Redis 优先读取节点定义与层级元数据，不缓存用户进度真相。
 */
@Service
public class SkillNodeCacheService {

    private static final Logger log = LoggerFactory.getLogger(SkillNodeCacheService.class);
    private static final String CACHE_KEY = "skill:tree:nodes";
    private static final Duration TTL = Duration.ofMinutes(60);
    private static final TypeReference<List<SkillRepository.SkillNodeRow>> NODE_LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public SkillNodeCacheService(
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider
    ) {
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    public List<SkillRepository.SkillNodeRow> getAllNodes(
            Supplier<List<SkillRepository.SkillNodeRow>> databaseLoader
    ) {
        if (databaseLoader == null) {
            return List.of();
        }
        if (stringRedisTemplate == null) {
            return sanitize(databaseLoader.get());
        }

        List<SkillRepository.SkillNodeRow> cached = readFromRedis();
        if (cached != null) {
            return cached;
        }

        List<SkillRepository.SkillNodeRow> loaded = sanitize(databaseLoader.get());
        writeToRedis(loaded);
        List<SkillRepository.SkillNodeRow> refreshed = readFromRedis();
        return refreshed == null ? loaded : refreshed;
    }

    public void evictNow() {
        evict();
    }

    public void evictAfterCommit() {
        runAfterCommit(this::evict);
    }

    private List<SkillRepository.SkillNodeRow> readFromRedis() {
        try {
            String payload = stringRedisTemplate.opsForValue().get(CACHE_KEY);
            if (payload == null || payload.isBlank()) {
                return null;
            }
            return sanitize(objectMapper.readValue(payload, NODE_LIST_TYPE));
        } catch (Exception ex) {
            log.debug("read skill node cache from redis failed: {}", ex.getMessage());
            return null;
        }
    }

    private void writeToRedis(List<SkillRepository.SkillNodeRow> nodes) {
        try {
            stringRedisTemplate.opsForValue().set(CACHE_KEY, objectMapper.writeValueAsString(nodes), TTL);
        } catch (Exception ex) {
            log.debug("write skill node cache to redis failed: {}", ex.getMessage());
        }
    }

    private void evict() {
        if (stringRedisTemplate == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(CACHE_KEY);
        } catch (Exception ex) {
            log.debug("evict skill node cache from redis failed: {}", ex.getMessage());
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

    private List<SkillRepository.SkillNodeRow> sanitize(List<SkillRepository.SkillNodeRow> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        return nodes.stream()
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
