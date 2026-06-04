package com.bishe.server.auth.captcha;

import com.bishe.server.auth.dto.AuthCaptchaConfigResponse;
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
 * 登录前公开验证码配置缓存：Redis 优先读取极验公开配置，不缓存校验结果与用户凭证。
 */
@Service
public class AuthCaptchaConfigCacheService {

    private static final Logger log = LoggerFactory.getLogger(AuthCaptchaConfigCacheService.class);
    private static final String CACHE_KEY = "auth:captcha:public-config";
    private static final Duration TTL = Duration.ofMinutes(30);

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public AuthCaptchaConfigCacheService(
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider
    ) {
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    public AuthCaptchaConfigResponse getPublicConfig(Supplier<AuthCaptchaConfigResponse> databaseLoader) {
        if (databaseLoader == null) {
            return emptyConfig();
        }
        if (stringRedisTemplate == null) {
            return sanitize(databaseLoader.get());
        }

        AuthCaptchaConfigResponse cached = readFromRedis();
        if (cached != null) {
            return cached;
        }

        AuthCaptchaConfigResponse loaded = sanitize(databaseLoader.get());
        writeToRedis(loaded);
        AuthCaptchaConfigResponse refreshed = readFromRedis();
        return refreshed == null ? loaded : refreshed;
    }

    public void evictNow() {
        evict();
    }

    public void evictAfterCommit() {
        runAfterCommit(this::evict);
    }

    private AuthCaptchaConfigResponse readFromRedis() {
        try {
            String payload = stringRedisTemplate.opsForValue().get(CACHE_KEY);
            if (payload == null || payload.isBlank()) {
                return null;
            }
            return sanitize(objectMapper.readValue(payload, AuthCaptchaConfigResponse.class));
        } catch (Exception ex) {
            log.debug("read auth captcha config cache from redis failed: {}", ex.getMessage());
            return null;
        }
    }

    private void writeToRedis(AuthCaptchaConfigResponse payload) {
        try {
            stringRedisTemplate.opsForValue().set(CACHE_KEY, objectMapper.writeValueAsString(payload), TTL);
        } catch (Exception ex) {
            log.debug("write auth captcha config cache to redis failed: {}", ex.getMessage());
        }
    }

    private void evict() {
        if (stringRedisTemplate == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(CACHE_KEY);
        } catch (Exception ex) {
            log.debug("evict auth captcha config cache from redis failed: {}", ex.getMessage());
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

    private AuthCaptchaConfigResponse sanitize(AuthCaptchaConfigResponse payload) {
        return payload == null ? emptyConfig() : payload;
    }

    private AuthCaptchaConfigResponse emptyConfig() {
        return new AuthCaptchaConfigResponse(false, "GEETEST_V4", null, "bind", 0L, false, false, false, false);
    }
}
