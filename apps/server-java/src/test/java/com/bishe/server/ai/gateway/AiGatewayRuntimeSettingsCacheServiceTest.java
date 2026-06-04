package com.bishe.server.ai.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiGatewayRuntimeSettingsCacheServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldBackfillRuntimeSettingsOnCacheMiss() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot snapshot =
                new AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot(
                        true,
                        true,
                        false,
                        false,
                        "HIGH",
                        2048,
                        "standard",
                        Instant.parse("2026-03-30T09:00:00Z")
                );
        when(valueOperations.get("ai:gateway:runtime-settings"))
                .thenReturn(null, objectMapper.writeValueAsString(snapshot));

        AiGatewayRuntimeSettingsCacheService service = new AiGatewayRuntimeSettingsCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot cached = service.getRuntimeSettings(() -> snapshot);

        assertThat(cached.debugModeEnabled()).isTrue();
        assertThat(cached.aiRequestLogEnabled()).isTrue();
        assertThat(cached.defaultReasoningEffort()).isEqualTo("HIGH");
        assertThat(cached.defaultThinkingBudget()).isEqualTo(2048);
        assertThat(cached.defaultThinkingLevel()).isEqualTo("standard");
        verify(valueOperations).set(eq("ai:gateway:runtime-settings"), anyString(), any(Duration.class));
    }

    @Test
    void shouldEvictImmediatelyWithoutTransaction() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

        AiGatewayRuntimeSettingsCacheService service = new AiGatewayRuntimeSettingsCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        service.evictAfterCommit();

        verify(redisTemplate).delete("ai:gateway:runtime-settings");
    }

    private ObjectProvider<StringRedisTemplate> providerOf(StringRedisTemplate redisTemplate) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("stringRedisTemplate", redisTemplate);
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }
}
