package com.bishe.server.ai.quota;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiQuotaUsageCounterServiceTest {

    @Test
    void shouldReturnCachedCountWithoutDatabaseFallback() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("ai:quota:daily:2026-03-30:user-42-1000:RESUME")).thenReturn("5");

        AiQuotaUsageCounterService service = new AiQuotaUsageCounterService(providerOf(redisTemplate));
        AtomicInteger loaderCalls = new AtomicInteger();

        int usedToday = service.getUsedToday("user-42-1000", "resume", null, LocalDate.of(2026, 3, 30), () -> {
            loaderCalls.incrementAndGet();
            return 3;
        });

        assertThat(usedToday).isEqualTo(5);
        assertThat(loaderCalls.get()).isZero();
        verify(valueOperations, never()).setIfAbsent(eq("ai:quota:daily:2026-03-30:user-42-1000:RESUME"), eq("3"), any(Duration.class));
    }

    @Test
    void shouldLoadFromDatabaseAndBackfillRedisOnMiss() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("ai:quota:daily:2026-03-30:user-42-1000:RESUME")).thenReturn(null, "3");
        when(valueOperations.setIfAbsent(eq("ai:quota:daily:2026-03-30:user-42-1000:RESUME"), eq("3"), any(Duration.class))).thenReturn(true);

        AiQuotaUsageCounterService service = new AiQuotaUsageCounterService(providerOf(redisTemplate));
        AtomicInteger loaderCalls = new AtomicInteger();

        int usedToday = service.getUsedToday("user-42-1000", "resume", null, LocalDate.of(2026, 3, 30), () -> {
            loaderCalls.incrementAndGet();
            return 3;
        });

        assertThat(usedToday).isEqualTo(3);
        assertThat(loaderCalls.get()).isEqualTo(1);
        verify(valueOperations).setIfAbsent(eq("ai:quota:daily:2026-03-30:user-42-1000:RESUME"), eq("3"), any(Duration.class));
    }

    @Test
    void shouldIncrementRedisCounterAfterSuccessfulCall() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        AiQuotaUsageCounterService service = new AiQuotaUsageCounterService(providerOf(redisTemplate));

        service.recordSuccess("user-7-2000", "interview_text", null, LocalDate.of(2026, 3, 30), 2);

        verify(valueOperations).increment("ai:quota:daily:2026-03-30:user-7-2000:INTERVIEW_TEXT", 2);
        verify(redisTemplate).expire(eq("ai:quota:daily:2026-03-30:user-7-2000:INTERVIEW_TEXT"), any(Duration.class));
    }

    @Test
    void shouldUseSceneSpecificKeyWhenSceneQuotaIsEnabled() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        AiQuotaUsageCounterService service = new AiQuotaUsageCounterService(providerOf(redisTemplate));

        service.recordSuccess(
                "user-7-2000",
                "community_reply",
                "mentor_prep_sheet_generate",
                LocalDate.of(2026, 3, 30),
                1
        );

        verify(valueOperations).increment("ai:quota:daily:2026-03-30:user-7-2000:COMMUNITY_REPLY:MENTOR_PREP_SHEET_GENERATE", 1);
        verify(redisTemplate).expire(eq("ai:quota:daily:2026-03-30:user-7-2000:COMMUNITY_REPLY:MENTOR_PREP_SHEET_GENERATE"), any(Duration.class));
    }

    private ObjectProvider<StringRedisTemplate> providerOf(StringRedisTemplate redisTemplate) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("stringRedisTemplate", redisTemplate);
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }
}
