package com.bishe.server.notification.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationDispatchCoordinationServiceTest {

    @Test
    void shouldSchedulePendingJobIntoRedisReadyQueue() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        NotificationDispatchCoordinationService service = new NotificationDispatchCoordinationService(providerOf(redisTemplate));

        service.schedulePendingAfterCommit(17L, Instant.ofEpochMilli(1_745_000_000_000L));

        verify(zSetOperations).add("notify:dispatch:ready", "17", 1_745_000_000_000D);
    }

    @Test
    void shouldClaimDueJobsFromRedisScript() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(), anyList(), any(), any(), any(), any(), any()))
                .thenReturn(List.of("17", "18"));

        NotificationDispatchCoordinationService service = new NotificationDispatchCoordinationService(providerOf(redisTemplate));

        List<Long> claimedJobIds = service.claimDueJobIds("notify-worker-a", 2, Duration.ofSeconds(45));

        assertThat(claimedJobIds).containsExactly(17L, 18L);
    }

    @Test
    void shouldDedupeAckByShortTtlKey() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("notify:dispatch:ack:9:ntfjob_1"), eq("1"), eq(Duration.ofMinutes(2))))
                .thenReturn(false);

        NotificationDispatchCoordinationService service = new NotificationDispatchCoordinationService(providerOf(redisTemplate));

        boolean acquired = service.tryAcquireAck(9L, "ntfjob_1");

        assertThat(acquired).isFalse();
    }

    private ObjectProvider<StringRedisTemplate> providerOf(StringRedisTemplate redisTemplate) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("stringRedisTemplate", redisTemplate);
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }
}
