package com.bishe.server.featureflag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeatureFlagCacheServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldBackfillFeatureFlagSnapshotOnCacheMissAndEvict() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        List<FeatureFlagRepository.FeatureFlagRow> payload = List.of(
                new FeatureFlagRepository.FeatureFlagRow(
                        "payment.mode",
                        "SANDBOX",
                        "支付模式",
                        1L,
                        Instant.parse("2026-03-30T14:00:00Z")
                ),
                new FeatureFlagRepository.FeatureFlagRow(
                        "voice.enabled",
                        "false",
                        "语音能力",
                        1L,
                        Instant.parse("2026-03-30T14:05:00Z")
                )
        );
        when(valueOperations.get("feature-flags:snapshot"))
                .thenReturn(null, objectMapper.writeValueAsString(payload));

        FeatureFlagCacheService service = new FeatureFlagCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        List<FeatureFlagRepository.FeatureFlagRow> cached = service.getSnapshot(() -> payload);

        assertThat(cached).hasSize(2);
        assertThat(cached.get(0).flagKey()).isEqualTo("payment.mode");
        verify(valueOperations).set(eq("feature-flags:snapshot"), anyString(), any(Duration.class));

        service.evictAfterCommit();

        verify(redisTemplate).delete("feature-flags:snapshot");
    }

    private ObjectProvider<StringRedisTemplate> providerOf(StringRedisTemplate redisTemplate) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("stringRedisTemplate", redisTemplate);
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }
}
