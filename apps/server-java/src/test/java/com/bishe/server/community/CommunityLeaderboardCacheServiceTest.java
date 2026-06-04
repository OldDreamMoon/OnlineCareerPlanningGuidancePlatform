package com.bishe.server.community;

import com.bishe.server.community.dto.CommunityLeaderboardResponse;
import com.bishe.server.community.service.CommunityLeaderboardCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

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

class CommunityLeaderboardCacheServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldBackfillLeaderboardOnCacheMiss() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        CommunityLeaderboardResponse payload = buildPayload();
        when(valueOperations.get("community:leaderboard:7d:1:10"))
                .thenReturn(null, objectMapper.writeValueAsString(payload));

        CommunityLeaderboardCacheService service = new CommunityLeaderboardCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        CommunityLeaderboardResponse cached = service.getLeaderboard("7d", 1, 10, () -> payload);

        assertThat(cached).isNotNull();
        assertThat(cached.records()).hasSize(1);
        assertThat(cached.records().getFirst().score()).isEqualTo(8L);
        verify(valueOperations).set(eq("community:leaderboard:7d:1:10"), anyString(), any(Duration.class));
        verify(setOperations).add("community:leaderboard:index", "community:leaderboard:7d:1:10");
    }

    @Test
    void shouldEvictIndexedLeaderboardKeysImmediatelyWithoutTransaction() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("community:leaderboard:index"))
                .thenReturn(Set.of("community:leaderboard:7d:1:10", "community:leaderboard:7d:2:10"));

        CommunityLeaderboardCacheService service = new CommunityLeaderboardCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        service.evictAllAfterCommit();

        verify(redisTemplate).delete(Set.of("community:leaderboard:7d:1:10", "community:leaderboard:7d:2:10"));
        verify(redisTemplate).delete("community:leaderboard:index");
    }

    private CommunityLeaderboardResponse buildPayload() {
        return new CommunityLeaderboardResponse(
                "7d",
                "post*5 + comment*2 + like*1",
                List.of(new CommunityLeaderboardResponse.LeaderboardItem(
                        1,
                        1001L,
                        "Alice",
                        8L,
                        1,
                        1,
                        1,
                        java.time.Instant.parse("2026-03-30T12:00:00Z").toEpochMilli()
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
