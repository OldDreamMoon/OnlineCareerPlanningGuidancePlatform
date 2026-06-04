package com.bishe.server.ai.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiRouteCacheServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldBackfillEnabledRoutesOnCacheMiss() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        String key = "ai:gateway:routes:INTERVIEW_TEXT:INTERVIEW_OPENING:ALL";
        List<AiGatewayAdminRepository.ResolvedRouteRow> routes = List.of(
                new AiGatewayAdminRepository.ResolvedRouteRow(
                        null,
                        null,
                        null,
                        "SINGLE",
                        "INTERVIEW_OPENING_ROUTE",
                        "INTERVIEW_TEXT",
                        "INTERVIEW_OPENING",
                        "route-model-v1",
                        1,
                        100,
                        AiExecutionMode.SYNC_BLOCKING.name(),
                        new BigDecimal("0.20"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "OPENAI_MAIN",
                        AiProviderType.OPENAI_COMPATIBLE.name(),
                        "OpenAI Main",
                        "https://example.com/v1",
                        "ciphertext",
                        3000,
                        1,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        null,
                        null,
                        null
                )
        );
        when(valueOperations.get(key))
                .thenReturn(null, objectMapper.writeValueAsString(routes));

        AiRouteCacheService service = new AiRouteCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        List<AiGatewayAdminRepository.ResolvedRouteRow> cached = service.getEnabledRoutes(
                "INTERVIEW_TEXT",
                "INTERVIEW_OPENING",
                "ALL",
                () -> routes
        );

        assertThat(cached).hasSize(1);
        assertThat(cached.getFirst().modelName()).isEqualTo("route-model-v1");
        verify(valueOperations).set(eq(key), anyString(), any(Duration.class));
        verify(setOperations).add("ai:gateway:routes:keys", key);
    }

    @Test
    void shouldSupportCachedEmptyRouteList() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        String key = "ai:gateway:routes:RESUME:_default:ALL";
        when(valueOperations.get(key)).thenReturn(null, "[]");

        AiRouteCacheService service = new AiRouteCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        List<AiGatewayAdminRepository.ResolvedRouteRow> cached = service.getEnabledRoutes("RESUME", "", "ALL", List::of);

        assertThat(cached).isEmpty();
        verify(valueOperations).set(eq(key), eq("[]"), any(Duration.class));
        verify(setOperations).add("ai:gateway:routes:keys", key);
    }

    @Test
    void shouldEvictAllImmediatelyWithoutTransaction() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("ai:gateway:routes:keys")).thenReturn(Set.of(
                "ai:gateway:routes:INTERVIEW_TEXT:INTERVIEW_OPENING",
                "ai:gateway:routes:RESUME:_DEFAULT"
        ));

        AiRouteCacheService service = new AiRouteCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        service.evictAllAfterCommit();

        verify(redisTemplate).delete(anyCollection());
    }

    private ObjectProvider<StringRedisTemplate> providerOf(StringRedisTemplate redisTemplate) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("stringRedisTemplate", redisTemplate);
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }
}
