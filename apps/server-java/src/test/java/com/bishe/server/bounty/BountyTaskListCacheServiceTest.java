package com.bishe.server.bounty;

import com.bishe.server.bounty.dto.BountyTaskListResponse;
import com.bishe.server.bounty.service.BountyTaskListCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BountyTaskListCacheServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldBackfillPublicTaskListOnCacheMiss() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        BountyTaskListResponse payload = buildPayload();
        String cacheKey = "bounty:tasks:list:" + DigestUtils.md5DigestAsHex(
                "_|open|1|10".getBytes(StandardCharsets.UTF_8)
        );
        when(valueOperations.get(cacheKey))
                .thenReturn(null, objectMapper.writeValueAsString(payload));

        BountyTaskListCacheService service = new BountyTaskListCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        BountyTaskListResponse cached = service.getTaskList(null, "OPEN", 1, 10, () -> payload);

        assertThat(cached).isNotNull();
        assertThat(cached.records()).hasSize(1);
        assertThat(cached.records().getFirst().submittedByMe()).isFalse();
        verify(valueOperations).set(eq(cacheKey), anyString(), any(Duration.class));
        verify(setOperations).add("bounty:tasks:list:index", cacheKey);
    }

    @Test
    void shouldEvictIndexedTaskListKeysImmediatelyWithoutTransaction() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("bounty:tasks:list:index"))
                .thenReturn(Set.of("bounty:tasks:list:a", "bounty:tasks:list:b"));

        BountyTaskListCacheService service = new BountyTaskListCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        service.evictAllAfterCommit();

        verify(redisTemplate).delete(Set.of("bounty:tasks:list:a", "bounty:tasks:list:b"));
        verify(redisTemplate).delete("bounty:tasks:list:index");
    }

    private BountyTaskListResponse buildPayload() {
        return new BountyTaskListResponse(
                List.of(new BountyTaskListResponse.TaskItem(
                        1001L,
                        2001L,
                        "EnterpriseOne",
                        null,
                        "前端项目实战",
                        "完成企业展示页。",
                        "实习内推机会",
                        "OPEN",
                        2,
                        null,
                        true,
                        java.time.Instant.parse("2026-03-30T12:00:00Z").toEpochMilli(),
                        java.time.Instant.parse("2026-03-29T12:00:00Z").toEpochMilli(),
                        java.time.Instant.parse("2026-03-30T12:30:00Z").toEpochMilli()
                )),
                1L,
                1,
                10
        );
    }

    private ObjectProvider<StringRedisTemplate> providerOf(StringRedisTemplate redisTemplate) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("stringRedisTemplate", redisTemplate);
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }
}
