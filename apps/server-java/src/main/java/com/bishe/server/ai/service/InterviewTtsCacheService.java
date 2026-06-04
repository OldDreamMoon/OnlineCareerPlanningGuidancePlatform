package com.bishe.server.ai.service;

import com.bishe.server.ai.dto.TextToSpeechResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 面试会话级 TTS 临时缓存。
 * Redis 可用时优先落 Redis；不可用时退化到进程内短时缓存，避免阻断主链路。
 */
@Service
public class InterviewTtsCacheService {

    private static final Logger log = LoggerFactory.getLogger(InterviewTtsCacheService.class);
    private static final String SESSION_INDEX_KEY_PREFIX = "ai:interview:tts:index:";
    private static final String ENTRY_KEY_PREFIX = "ai:interview:tts:entry:";

    private final AiTtsCacheProperties properties;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final Map<String, LocalCacheEntry> localEntries = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> localSessionIndex = new ConcurrentHashMap<>();

    public InterviewTtsCacheService(
            AiTtsCacheProperties properties,
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    public Optional<TextToSpeechResponse> get(String sessionId, String text, String stylePrompt, String voiceName) {
        if (!properties.isEnabled() || sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        String entryKey = buildEntryKey(sessionId, text, stylePrompt, voiceName);
        Optional<TextToSpeechResponse> redisResult = getFromRedis(entryKey);
        if (redisResult.isPresent()) {
            return redisResult;
        }
        return getFromLocal(entryKey);
    }

    public void put(String sessionId, String text, String stylePrompt, String voiceName, TextToSpeechResponse response) {
        if (!properties.isEnabled() || sessionId == null || sessionId.isBlank() || response == null) {
            return;
        }
        String entryKey = buildEntryKey(sessionId, text, stylePrompt, voiceName);
        Duration ttl = Duration.ofSeconds(properties.getTtlSeconds());
        putToRedis(sessionId, entryKey, response, ttl);
        putToLocal(sessionId, entryKey, response, ttl);
    }

    public void evictSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        evictSessionFromRedis(sessionId);
        evictSessionFromLocal(sessionId);
    }

    private Optional<TextToSpeechResponse> getFromRedis(String entryKey) {
        if (stringRedisTemplate == null) {
            return Optional.empty();
        }
        try {
            String cachedJson = stringRedisTemplate.opsForValue().get(entryKey);
            if (cachedJson == null || cachedJson.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(cachedJson, TextToSpeechResponse.class));
        } catch (Exception ex) {
            log.debug("read tts cache from redis failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<TextToSpeechResponse> getFromLocal(String entryKey) {
        LocalCacheEntry entry = localEntries.get(entryKey);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAtEpochMs() < System.currentTimeMillis()) {
            localEntries.remove(entryKey);
            return Optional.empty();
        }
        return Optional.of(entry.response());
    }

    private void putToRedis(String sessionId, String entryKey, TextToSpeechResponse response, Duration ttl) {
        if (stringRedisTemplate == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(response);
            stringRedisTemplate.opsForValue().set(entryKey, json, ttl);
            String sessionIndexKey = buildSessionIndexKey(sessionId);
            stringRedisTemplate.opsForSet().add(sessionIndexKey, entryKey);
            stringRedisTemplate.expire(sessionIndexKey, ttl);
        } catch (Exception ex) {
            log.debug("write tts cache to redis failed: {}", ex.getMessage());
        }
    }

    private void putToLocal(String sessionId, String entryKey, TextToSpeechResponse response, Duration ttl) {
        localEntries.put(entryKey, new LocalCacheEntry(response, System.currentTimeMillis() + ttl.toMillis()));
        localSessionIndex.computeIfAbsent(sessionId, ignored -> ConcurrentHashMap.newKeySet()).add(entryKey);
    }

    private void evictSessionFromRedis(String sessionId) {
        if (stringRedisTemplate == null) {
            return;
        }
        String sessionIndexKey = buildSessionIndexKey(sessionId);
        try {
            Set<String> members = stringRedisTemplate.opsForSet().members(sessionIndexKey);
            if (members != null && !members.isEmpty()) {
                stringRedisTemplate.delete(members);
            }
            stringRedisTemplate.delete(sessionIndexKey);
        } catch (Exception ex) {
            log.debug("evict tts cache from redis failed: {}", ex.getMessage());
        }
    }

    private void evictSessionFromLocal(String sessionId) {
        Set<String> entryKeys = localSessionIndex.remove(sessionId);
        if (entryKeys == null || entryKeys.isEmpty()) {
            return;
        }
        entryKeys.forEach(localEntries::remove);
    }

    private String buildEntryKey(String sessionId, String text, String stylePrompt, String voiceName) {
        return ENTRY_KEY_PREFIX + sessionId + ":" + buildFingerprint(text, stylePrompt, voiceName);
    }

    private String buildSessionIndexKey(String sessionId) {
        return SESSION_INDEX_KEY_PREFIX + sessionId;
    }

    private String buildFingerprint(String text, String stylePrompt, String voiceName) {
        String source = String.join(
                "\n",
                "text=" + safe(text),
                "stylePrompt=" + safe(stylePrompt),
                "voiceName=" + safe(voiceName)
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception ex) {
            return Integer.toHexString(source.hashCode());
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private record LocalCacheEntry(TextToSpeechResponse response, long expiresAtEpochMs) {
    }
}
