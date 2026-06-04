package com.bishe.server.governance;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 治理策略与敏感词配置缓存：Redis 优先，更新后精确失效。
 */
@Service
public class ContentGovernanceConfigCacheService {

    private static final Logger log = LoggerFactory.getLogger(ContentGovernanceConfigCacheService.class);
    private static final String POLICY_KEY = "governance:config:policies";
    private static final String TERMS_LIST_KEY = "governance:config:sensitive-terms:list";
    private static final String TERMS_SOURCE_PREFIX = "governance:config:sensitive-terms:source:";
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final List<String> SENSITIVE_TERM_SOURCES = List.of(
            "ALL",
            "AI_INPUT",
            "AI_OUTPUT",
            "COMMUNITY_POST",
            "COMMUNITY_COMMENT"
    );
    private static final TypeReference<Map<String, String>> POLICY_MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<SensitiveTermRow>> TERMS_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public ContentGovernanceConfigCacheService(
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider
    ) {
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    public Map<String, String> getPolicyMap(Supplier<Map<String, String>> databaseLoader) {
        if (databaseLoader == null) {
            return Map.of();
        }
        if (stringRedisTemplate == null) {
            return sanitizePolicyMap(databaseLoader.get());
        }
        Map<String, String> cached = readPolicyMapFromRedis();
        if (!cached.isEmpty()) {
            return cached;
        }
        Map<String, String> loaded = sanitizePolicyMap(databaseLoader.get());
        writeToRedis(POLICY_KEY, loaded);
        Map<String, String> refreshed = readPolicyMapFromRedis();
        return refreshed.isEmpty() ? loaded : refreshed;
    }

    public List<SensitiveTermRow> getAllSensitiveTerms(
            Supplier<List<SensitiveTermRow>> databaseLoader
    ) {
        return getSensitiveTermsByKey(TERMS_LIST_KEY, databaseLoader);
    }

    public List<SensitiveTermRow> getEnabledTermsForSource(
            String sourceScope,
            Supplier<List<SensitiveTermRow>> databaseLoader
    ) {
        String normalizedSourceScope = normalizeSourceScope(sourceScope);
        return getSensitiveTermsByKey(TERMS_SOURCE_PREFIX + normalizedSourceScope, databaseLoader);
    }

    public void evictPoliciesAfterCommit() {
        runAfterCommit(this::evictPolicies);
    }

    public void evictPoliciesNow() {
        evictPolicies();
    }

    public void evictSensitiveTermsAfterCommit() {
        runAfterCommit(this::evictSensitiveTerms);
    }

    public void evictSensitiveTermsNow() {
        evictSensitiveTerms();
    }

    private List<SensitiveTermRow> getSensitiveTermsByKey(
            String key,
            Supplier<List<SensitiveTermRow>> databaseLoader
    ) {
        if (databaseLoader == null) {
            return List.of();
        }
        if (stringRedisTemplate == null) {
            return sanitizeSensitiveTerms(databaseLoader.get());
        }
        List<SensitiveTermRow> cached = readSensitiveTermsFromRedis(key);
        if (!cached.isEmpty()) {
            return cached;
        }
        List<SensitiveTermRow> loaded = sanitizeSensitiveTerms(databaseLoader.get());
        writeToRedis(key, loaded);
        List<SensitiveTermRow> refreshed = readSensitiveTermsFromRedis(key);
        return refreshed.isEmpty() ? loaded : refreshed;
    }

    private Map<String, String> readPolicyMapFromRedis() {
        try {
            String payload = stringRedisTemplate.opsForValue().get(POLICY_KEY);
            if (payload == null || payload.isBlank()) {
                return Map.of();
            }
            return sanitizePolicyMap(objectMapper.readValue(payload, POLICY_MAP_TYPE));
        } catch (Exception ex) {
            log.debug("read governance policies from redis failed: {}", ex.getMessage());
            return Map.of();
        }
    }

    private List<SensitiveTermRow> readSensitiveTermsFromRedis(String key) {
        try {
            String payload = stringRedisTemplate.opsForValue().get(key);
            if (payload == null || payload.isBlank()) {
                return List.of();
            }
            return sanitizeSensitiveTerms(objectMapper.readValue(payload, TERMS_TYPE));
        } catch (Exception ex) {
            log.debug("read governance sensitive terms from redis failed: {}", ex.getMessage());
            return List.of();
        }
    }

    private void writeToRedis(String key, Object value) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), TTL);
        } catch (Exception ex) {
            log.debug("write governance config cache to redis failed: {}", ex.getMessage());
        }
    }

    private void evictPolicies() {
        if (stringRedisTemplate == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(POLICY_KEY);
        } catch (Exception ex) {
            log.debug("evict governance policies from redis failed: {}", ex.getMessage());
        }
    }

    private void evictSensitiveTerms() {
        if (stringRedisTemplate == null) {
            return;
        }
        try {
            List<String> keys = new java.util.ArrayList<>();
            keys.add(TERMS_LIST_KEY);
            SENSITIVE_TERM_SOURCES.forEach(source -> keys.add(TERMS_SOURCE_PREFIX + source));
            stringRedisTemplate.delete(keys);
        } catch (Exception ex) {
            log.debug("evict governance sensitive terms from redis failed: {}", ex.getMessage());
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

    private String normalizeSourceScope(String sourceScope) {
        if (sourceScope == null || sourceScope.isBlank()) {
            return "ALL";
        }
        return sourceScope.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private Map<String, String> sanitizePolicyMap(Map<String, String> rawMap) {
        if (rawMap == null || rawMap.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> {
            if (key != null && value != null) {
                result.put(key, value);
            }
        });
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    private List<SensitiveTermRow> sanitizeSensitiveTerms(
            List<SensitiveTermRow> rows
    ) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
