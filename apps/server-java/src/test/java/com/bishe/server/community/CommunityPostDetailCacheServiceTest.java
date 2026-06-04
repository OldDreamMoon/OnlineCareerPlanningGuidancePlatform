package com.bishe.server.community;

import com.bishe.server.community.service.CommunityPostDetailCacheService;
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

class CommunityPostDetailCacheServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldBackfillPublicPostDetailOnCacheMiss() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        CommunityPostDetailCacheService.CommunityPostDetailSnapshot snapshot =
                new CommunityPostDetailCacheService.CommunityPostDetailSnapshot(
                        9001L,
                        1001L,
                        "Alice",
                        null,
                        false,
                        "STUDENT",
                        null,
                        "帖子详情缓存测试",
                        "GENERAL_HELP",
                        "OPEN",
                        "PASS",
                        "LOW",
                        "用于验证详情主体缓存回填。",
                        List.of("缓存", "详情"),
                        2L,
                        5L,
                        Instant.parse("2026-03-30T12:00:00Z"),
                        Instant.parse("2026-03-30T12:30:00Z")
                );
        when(valueOperations.get("community:posts:detail:9001"))
                .thenReturn(null, objectMapper.writeValueAsString(snapshot));

        CommunityPostDetailCacheService service = new CommunityPostDetailCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        CommunityPostDetailCacheService.CommunityPostDetailSnapshot cached = service.getPublicDetail(9001L, () -> snapshot);

        assertThat(cached).isNotNull();
        assertThat(cached.postId()).isEqualTo(9001L);
        assertThat(cached.authorUserId()).isEqualTo(1001L);
        assertThat(cached.commentCount()).isEqualTo(2L);
        assertThat(cached.likeCount()).isEqualTo(5L);
        verify(valueOperations).set(eq("community:posts:detail:9001"), anyString(), any(Duration.class));
    }

    @Test
    void shouldEvictImmediatelyWithoutTransaction() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

        CommunityPostDetailCacheService service = new CommunityPostDetailCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        service.evictAfterCommit(9001L);

        verify(redisTemplate).delete("community:posts:detail:9001");
    }

    private ObjectProvider<StringRedisTemplate> providerOf(StringRedisTemplate redisTemplate) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("stringRedisTemplate", redisTemplate);
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }
}
