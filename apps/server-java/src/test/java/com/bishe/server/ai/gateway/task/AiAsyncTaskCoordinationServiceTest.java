package com.bishe.server.ai.gateway.task;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiAsyncTaskCoordinationServiceTest {

    @Test
    void shouldSchedulePendingTaskIntoRedisReadyQueue() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        AiAsyncTaskCoordinationService service = new AiAsyncTaskCoordinationService(providerOf(redisTemplate));

        service.schedulePendingAfterCommit(21L, Instant.ofEpochMilli(1_745_100_000_000L));

        verify(zSetOperations).add("ai:async:ready", "21", 1_745_100_000_000D);
    }

    @Test
    void shouldClaimDueTasksFromRedisScript() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(), anyList(), any(), any(), any(), any(), any()))
                .thenReturn(List.of("21", "22"));

        AiAsyncTaskCoordinationService service = new AiAsyncTaskCoordinationService(providerOf(redisTemplate));

        List<Long> claimedJobIds = service.claimDueJobIds("ai-worker-a", 2, Duration.ofSeconds(45));

        assertThat(claimedJobIds).containsExactly(21L, 22L);
    }

    private ObjectProvider<StringRedisTemplate> providerOf(StringRedisTemplate redisTemplate) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("stringRedisTemplate", redisTemplate);
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }
}
