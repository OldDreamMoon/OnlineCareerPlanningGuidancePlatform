package com.bishe.server.ai.gateway;

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
 * AI 网关后台列表缓存：缓存 provider、route、prompt template 三类读多写少的后台列表快照。
 */
@Service
public class AiGatewayAdminListCacheService {

    private static final Logger log = LoggerFactory.getLogger(AiGatewayAdminListCacheService.class);
    private static final String PROVIDERS_KEY = "ai:gateway:admin:providers";
    private static final String ROUTES_KEY = "ai:gateway:admin:routes";
    private static final String PROMPT_TEMPLATES_KEY = "ai:gateway:admin:prompt-templates";
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final TypeReference<AiGatewayAdminService.ProviderListPayload> PROVIDERS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<AiGatewayAdminService.RouteListPayload> ROUTES_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<AiGatewayAdminService.PromptTemplateListPayload> PROMPT_TEMPLATES_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public AiGatewayAdminListCacheService(
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider
    ) {
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    public AiGatewayAdminService.ProviderListPayload getProviders(
            Supplier<AiGatewayAdminService.ProviderListPayload> databaseLoader
    ) {
        return getPayload(
                PROVIDERS_KEY,
                PROVIDERS_TYPE,
                databaseLoader,
                () -> new AiGatewayAdminService.ProviderListPayload(List.of())
        );
    }

    public AiGatewayAdminService.RouteListPayload getRoutes(
            Supplier<AiGatewayAdminService.RouteListPayload> databaseLoader
    ) {
        return getPayload(
                ROUTES_KEY,
                ROUTES_TYPE,
                databaseLoader,
                () -> new AiGatewayAdminService.RouteListPayload(List.of())
        );
    }

    public AiGatewayAdminService.PromptTemplateListPayload getPromptTemplates(
            Supplier<AiGatewayAdminService.PromptTemplateListPayload> databaseLoader
    ) {
        return getPayload(
                PROMPT_TEMPLATES_KEY,
                PROMPT_TEMPLATES_TYPE,
                databaseLoader,
                () -> new AiGatewayAdminService.PromptTemplateListPayload(List.of())
        );
    }

    public void evictAllNow() {
        evictAll();
    }

    public void evictAllAfterCommit() {
        runAfterCommit(this::evictAll);
    }

    private <T> T getPayload(
            String cacheKey,
            TypeReference<T> typeReference,
            Supplier<T> databaseLoader,
            Supplier<T> emptySupplier
    ) {
        if (databaseLoader == null) {
            return emptySupplier.get();
        }
        if (stringRedisTemplate == null) {
            return sanitize(databaseLoader.get(), emptySupplier);
        }

        T cached = readFromRedis(cacheKey, typeReference);
        if (cached != null) {
            return cached;
        }

        T loaded = sanitize(databaseLoader.get(), emptySupplier);
        writeToRedis(cacheKey, loaded);
        T refreshed = readFromRedis(cacheKey, typeReference);
        return refreshed == null ? loaded : refreshed;
    }

    private <T> T readFromRedis(String cacheKey, TypeReference<T> typeReference) {
        try {
            String payload = stringRedisTemplate.opsForValue().get(cacheKey);
            if (payload == null || payload.isBlank()) {
                return null;
            }
            return objectMapper.readValue(payload, typeReference);
        } catch (Exception ex) {
            log.debug("read ai gateway admin list cache from redis failed: key={}, message={}", cacheKey, ex.getMessage());
            return null;
        }
    }

    private void writeToRedis(String cacheKey, Object payload) {
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(payload), TTL);
        } catch (Exception ex) {
            log.debug("write ai gateway admin list cache to redis failed: key={}, message={}", cacheKey, ex.getMessage());
        }
    }

    private void evictAll() {
        if (stringRedisTemplate == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(List.of(PROVIDERS_KEY, ROUTES_KEY, PROMPT_TEMPLATES_KEY));
        } catch (Exception ex) {
            log.debug("evict ai gateway admin list cache from redis failed: {}", ex.getMessage());
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

    private <T> T sanitize(T payload, Supplier<T> emptySupplier) {
        return payload == null ? emptySupplier.get() : payload;
    }
}
