package com.bishe.server.community;

import com.bishe.server.community.dto.CommunityPostListResponse;
import com.bishe.server.community.service.CommunityPostListCacheService;
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

class CommunityPostListCacheServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldBackfillPublicPostListOnCacheMiss() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        CommunityPostListResponse payload = buildPayload();
        String cacheKey = "community:posts:list:" + DigestUtils.md5DigestAsHex(
                "java|后端|general_help|1|10".getBytes(StandardCharsets.UTF_8)
        );
        when(valueOperations.get(cacheKey))
                .thenReturn(null, objectMapper.writeValueAsString(payload));

        CommunityPostListCacheService service = new CommunityPostListCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        CommunityPostListResponse cached = service.getPostList("Java", "后端", "GENERAL_HELP", 1, 10, () -> payload);

        assertThat(cached).isNotNull();
        assertThat(cached.records()).hasSize(1);
        assertThat(cached.records().getFirst().likedByMe()).isFalse();
        assertThat(cached.records().getFirst().authoredByMe()).isFalse();
        assertThat(cached.records().getFirst().participatedByMe()).isFalse();
        verify(valueOperations).set(eq(cacheKey), anyString(), any(Duration.class));
        verify(setOperations).add("community:posts:list:index", cacheKey);
    }

    @Test
    void shouldEvictIndexedPublicPostListKeysImmediatelyWithoutTransaction() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("community:posts:list:index"))
                .thenReturn(Set.of("community:posts:list:a", "community:posts:list:b"));

        CommunityPostListCacheService service = new CommunityPostListCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        service.evictAllAfterCommit();

        verify(redisTemplate).delete(Set.of("community:posts:list:a", "community:posts:list:b"));
        verify(redisTemplate).delete("community:posts:list:index");
    }

    private CommunityPostListResponse buildPayload() {
        return new CommunityPostListResponse(
                List.of(new CommunityPostListResponse.PostItem(
                        1001L,
                        2001L,
                        "Alice",
                        null,
                        false,
                        "STUDENT",
                        null,
                        "Java 后端求职记录",
                        "GENERAL_HELP",
                        "OPEN",
                        "分享一下最近的项目表达方法。",
                        List.of("后端", "面试"),
                        2L,
                        5L,
                        true,
                        "PASS",
                        "LOW",
                        true,
                        true,
                        true,
                        java.time.Instant.parse("2026-03-30T12:00:00Z").toEpochMilli(),
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
