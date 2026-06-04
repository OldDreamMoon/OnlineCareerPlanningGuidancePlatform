package com.bishe.server.ai.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiGatewayAdminListCacheServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldBackfillProviderListOnCacheMiss() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        AiGatewayAdminService.ProviderListPayload payload = new AiGatewayAdminService.ProviderListPayload(List.of(
                new AiGatewayAdminService.ProviderItem(
                        1L,
                        "OPENAI_CACHE",
                        "OPENAI_COMPATIBLE",
                        "OpenAI Cache",
                        "https://example.com/v1",
                        true,
                        15000,
                        1,
                        "0",
                        "0",
                        "sk-***",
                        true,
                        "",
                        List.of(),
                        java.time.Instant.parse("2026-03-30T09:00:00Z").toEpochMilli(),
                        java.time.Instant.parse("2026-03-30T09:30:00Z").toEpochMilli()
                )
        ));
        when(valueOperations.get("ai:gateway:admin:providers"))
                .thenReturn(null, objectMapper.writeValueAsString(payload));

        AiGatewayAdminListCacheService service = new AiGatewayAdminListCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        AiGatewayAdminService.ProviderListPayload cached = service.getProviders(() -> payload);

        assertThat(cached.records()).hasSize(1);
        assertThat(cached.records().getFirst().providerCode()).isEqualTo("OPENAI_CACHE");
        verify(valueOperations).set(eq("ai:gateway:admin:providers"), anyString(), any(Duration.class));
    }

    @Test
    void shouldEvictAllListsImmediatelyWithoutTransaction() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

        AiGatewayAdminListCacheService service = new AiGatewayAdminListCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        service.evictAllAfterCommit();

        verify(redisTemplate).delete(List.of(
                "ai:gateway:admin:providers",
                "ai:gateway:admin:routes",
                "ai:gateway:admin:prompt-templates"
        ));
    }

    private ObjectProvider<StringRedisTemplate> providerOf(StringRedisTemplate redisTemplate) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("stringRedisTemplate", redisTemplate);
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }
}
