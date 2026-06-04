package com.bishe.server.community;

import com.bishe.server.community.service.CommunityPostCommentListCacheService;
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

class CommunityPostCommentListCacheServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldBackfillVisibleCommentsOnCacheMiss() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        List<CommunityPostCommentListCacheService.CommentSnapshot> snapshots = List.of(
                new CommunityPostCommentListCacheService.CommentSnapshot(
                        7001L,
                        1001L,
                        "Alice",
                        null,
                        false,
                        "STUDENT",
                        null,
                        "第一条可见评论",
                        false,
                        Instant.parse("2026-03-30T12:00:00Z")
                )
        );
        when(valueOperations.get("community:posts:comments:9001"))
                .thenReturn(null, objectMapper.writeValueAsString(snapshots));

        CommunityPostCommentListCacheService service = new CommunityPostCommentListCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        List<CommunityPostCommentListCacheService.CommentSnapshot> cached = service.getVisibleComments(9001L, () -> snapshots);

        assertThat(cached).hasSize(1);
        assertThat(cached.getFirst().commentId()).isEqualTo(7001L);
        assertThat(cached.getFirst().content()).isEqualTo("第一条可见评论");
        verify(valueOperations).set(eq("community:posts:comments:9001"), anyString(), any(Duration.class));
    }

    @Test
    void shouldCacheEmptyCommentList() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("community:posts:comments:9002"))
                .thenReturn(null, objectMapper.writeValueAsString(List.of()));

        CommunityPostCommentListCacheService service = new CommunityPostCommentListCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        List<CommunityPostCommentListCacheService.CommentSnapshot> cached = service.getVisibleComments(9002L, List::of);

        assertThat(cached).isEmpty();
        verify(valueOperations).set(eq("community:posts:comments:9002"), anyString(), any(Duration.class));
    }

    @Test
    void shouldEvictImmediatelyWithoutTransaction() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

        CommunityPostCommentListCacheService service = new CommunityPostCommentListCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        service.evictAfterCommit(9001L);

        verify(redisTemplate).delete("community:posts:comments:9001");
    }

    private ObjectProvider<StringRedisTemplate> providerOf(StringRedisTemplate redisTemplate) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("stringRedisTemplate", redisTemplate);
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }
}
