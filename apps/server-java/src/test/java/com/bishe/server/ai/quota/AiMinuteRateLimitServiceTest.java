package com.bishe.server.ai.quota;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiMinuteRateLimitServiceTest {

    @Test
    void shouldAllowWhenRedisScriptAcquiresWindowSlot() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(), anyList(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(1L, 5L, 0L));

        AiMinuteRateLimitService service = new AiMinuteRateLimitService(providerOf(redisTemplate));

        AiMinuteRateLimitService.RateLimitDecision decision = service.acquire(
                "user-7-1000",
                "FREE",
                "RESUME",
                "trc-rate-allow"
        );

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.limit()).isEqualTo(AiMinuteRateLimitService.FREE_LIMIT_PER_MINUTE);
        assertThat(decision.currentCount()).isEqualTo(5);
        assertThat(decision.retryAfterSeconds()).isZero();
    }

    @Test
    void shouldBlockWhenRedisScriptReportsWindowOverflow() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(), anyList(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(0L, 30L, 4L));

        AiMinuteRateLimitService service = new AiMinuteRateLimitService(providerOf(redisTemplate));

        AiMinuteRateLimitService.RateLimitDecision decision = service.acquire(
                "user-8-2000",
                "PREMIUM",
                "INTERVIEW_TEXT",
                "trc-rate-block"
        );

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.limit()).isEqualTo(AiMinuteRateLimitService.PREMIUM_LIMIT_PER_MINUTE);
        assertThat(decision.currentCount()).isEqualTo(30);
        assertThat(decision.retryAfterSeconds()).isEqualTo(4);
    }

    @Test
    void shouldFallbackToAllowWhenRedisUnavailable() {
        AiMinuteRateLimitService service = new AiMinuteRateLimitService(emptyProvider());

        AiMinuteRateLimitService.RateLimitDecision decision = service.acquire(
                "user-9-3000",
                "FREE",
                "TTS",
                "trc-rate-fallback"
        );

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.limit()).isEqualTo(AiMinuteRateLimitService.FREE_LIMIT_PER_MINUTE);
    }

    private ObjectProvider<StringRedisTemplate> providerOf(StringRedisTemplate redisTemplate) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("stringRedisTemplate", redisTemplate);
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }

    private ObjectProvider<StringRedisTemplate> emptyProvider() {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }
}
