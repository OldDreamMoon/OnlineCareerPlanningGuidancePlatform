package com.bishe.server.auth.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 注册邮箱验证码存储：Redis 优先，不可用时退化到进程内短时存储。
 */
@Service
public class EmailVerificationCodeStoreService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationCodeStoreService.class);
    private static final String KEY_PREFIX = "auth:register:email-code:";

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final Map<String, StoredCodeRecord> localStore = new ConcurrentHashMap<>();

    public EmailVerificationCodeStoreService(
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider
    ) {
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    public Optional<EmailCodeRecord> get(String normalizedEmail) {
        if (!StringUtils.hasText(normalizedEmail)) {
            return Optional.empty();
        }
        Optional<EmailCodeRecord> redisRecord = getFromRedis(normalizedEmail);
        if (redisRecord.isPresent()) {
            return redisRecord;
        }
        return getFromLocal(normalizedEmail);
    }

    public void put(String normalizedEmail, byte[] codeHash, Instant expiresAt, Instant nextSendAt) {
        if (!StringUtils.hasText(normalizedEmail) || codeHash == null || expiresAt == null || nextSendAt == null) {
            return;
        }
        StoredCodeRecord record = new StoredCodeRecord(
                Base64.getUrlEncoder().withoutPadding().encodeToString(codeHash),
                expiresAt.toEpochMilli(),
                nextSendAt.toEpochMilli()
        );
        Duration ttl = resolveTtl(record);
        putToRedis(normalizedEmail, record, ttl);
        localStore.put(normalizedEmail, record);
    }

    public void delete(String normalizedEmail) {
        if (!StringUtils.hasText(normalizedEmail)) {
            return;
        }
        deleteFromRedis(normalizedEmail);
        localStore.remove(normalizedEmail);
    }

    private Optional<EmailCodeRecord> getFromRedis(String normalizedEmail) {
        if (stringRedisTemplate == null) {
            return Optional.empty();
        }
        try {
            String payload = stringRedisTemplate.opsForValue().get(buildKey(normalizedEmail));
            if (!StringUtils.hasText(payload)) {
                return Optional.empty();
            }
            StoredCodeRecord stored = objectMapper.readValue(payload, StoredCodeRecord.class);
            if (isExpired(stored)) {
                deleteFromRedis(normalizedEmail);
                return Optional.empty();
            }
            return Optional.of(toEmailCodeRecord(stored));
        } catch (Exception ex) {
            log.debug("read register email verification code from redis failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<EmailCodeRecord> getFromLocal(String normalizedEmail) {
        StoredCodeRecord stored = localStore.get(normalizedEmail);
        if (stored == null) {
            return Optional.empty();
        }
        if (isExpired(stored)) {
            localStore.remove(normalizedEmail);
            return Optional.empty();
        }
        return Optional.of(toEmailCodeRecord(stored));
    }

    private void putToRedis(String normalizedEmail, StoredCodeRecord record, Duration ttl) {
        if (stringRedisTemplate == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(buildKey(normalizedEmail), objectMapper.writeValueAsString(record), ttl);
        } catch (Exception ex) {
            log.debug("write register email verification code to redis failed: {}", ex.getMessage());
        }
    }

    private void deleteFromRedis(String normalizedEmail) {
        if (stringRedisTemplate == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(buildKey(normalizedEmail));
        } catch (Exception ex) {
            log.debug("delete register email verification code from redis failed: {}", ex.getMessage());
        }
    }

    private String buildKey(String normalizedEmail) {
        return KEY_PREFIX + normalizedEmail.trim().toLowerCase();
    }

    private Duration resolveTtl(StoredCodeRecord stored) {
        long expireAtEpochMs = Math.max(stored.expiresAtEpochMs(), stored.nextSendAtEpochMs());
        long ttlMs = Math.max(expireAtEpochMs - System.currentTimeMillis(), 1_000L);
        return Duration.ofMillis(ttlMs);
    }

    private boolean isExpired(StoredCodeRecord stored) {
        return stored.expiresAtEpochMs() <= System.currentTimeMillis();
    }

    private EmailCodeRecord toEmailCodeRecord(StoredCodeRecord stored) {
        byte[] codeHash;
        try {
            codeHash = Base64.getUrlDecoder().decode(stored.codeHashBase64());
        } catch (Exception ex) {
            throw new IllegalStateException("register email verification code hash decode failed", ex);
        }
        return new EmailCodeRecord(
                Arrays.copyOf(codeHash, codeHash.length),
                Instant.ofEpochMilli(stored.expiresAtEpochMs()),
                Instant.ofEpochMilli(stored.nextSendAtEpochMs())
        );
    }

    public record EmailCodeRecord(
            byte[] codeHash,
            Instant expiresAt,
            Instant nextSendAt
    ) {
    }

    private record StoredCodeRecord(
            String codeHashBase64,
            long expiresAtEpochMs,
            long nextSendAtEpochMs
    ) {
    }
}
