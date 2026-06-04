package com.bishe.server.bounty;

import com.bishe.server.bounty.dto.BountyTaskDetailResponse;
import com.bishe.server.bounty.service.BountyTaskDetailCacheService;
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

class BountyTaskDetailCacheServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldBackfillPublicTaskDetailOnCacheMiss() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        BountyTaskDetailResponse payload = buildPayload();
        String cacheKey = "bounty:tasks:detail:1001";
        when(valueOperations.get(cacheKey))
                .thenReturn(null, objectMapper.writeValueAsString(payload));

        BountyTaskDetailCacheService service = new BountyTaskDetailCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        BountyTaskDetailResponse cached = service.getTaskDetail(1001L, () -> payload);

        assertThat(cached).isNotNull();
        assertThat(cached.mine()).isFalse();
        assertThat(cached.mySubmission()).isNull();
        verify(valueOperations).set(eq(cacheKey), anyString(), any(Duration.class));
    }

    @Test
    void shouldEvictTaskDetailImmediatelyWithoutTransaction() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        BountyTaskDetailCacheService service = new BountyTaskDetailCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        service.evictAfterCommit(1001L);

        verify(redisTemplate).delete("bounty:tasks:detail:1001");
    }

    private BountyTaskDetailResponse buildPayload() {
        return new BountyTaskDetailResponse(
                1001L,
                2001L,
                "EnterpriseOne",
                null,
                "缓存详情任务",
                "验证详情公共主体缓存。",
                "优先面试",
                "OPEN",
                2,
                null,
                true,
                java.time.Instant.parse("2026-03-31T12:00:00Z").toEpochMilli(),
                null,
                java.time.Instant.parse("2026-03-30T12:00:00Z").toEpochMilli(),
                java.time.Instant.parse("2026-03-30T12:30:00Z").toEpochMilli(),
                new BountyTaskDetailResponse.MySubmission(
                        3001L,
                        "SUBMITTED",
                        "我的提交",
                        List.of("https://demo.example.com"),
                        null,
                        null,
                        java.time.Instant.parse("2026-03-30T12:20:00Z").toEpochMilli(),
                        java.time.Instant.parse("2026-03-30T12:25:00Z").toEpochMilli()
                )
        );
    }

    private ObjectProvider<StringRedisTemplate> providerOf(StringRedisTemplate redisTemplate) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("stringRedisTemplate", redisTemplate);
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }
}
