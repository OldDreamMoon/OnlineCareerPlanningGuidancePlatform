package com.bishe.server.governance;

import com.bishe.server.auth.model.AppUser;
import com.bishe.server.auth.model.UserAccountStatus;
import com.bishe.server.auth.model.UserRole;
import com.bishe.server.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentReportRateLimitServiceTest {

    @Test
    void shouldFallbackToDatabaseWhenRedisUnavailable() {
        UserRepository userRepository = mock(UserRepository.class);
        ContentReportRateLimitService service = new ContentReportRateLimitService(emptyRedisProvider(), userRepository);

        boolean allowed = service.tryAcquire(
                8L,
                Instant.parse("2026-03-30T07:00:00Z"),
                5,
                Duration.ofMinutes(10),
                () -> List.of(
                        Instant.parse("2026-03-30T06:53:00Z"),
                        Instant.parse("2026-03-30T06:55:00Z")
                )
        );

        assertThat(allowed).isTrue();
    }

    @Test
    void shouldBackfillAndAcquireWithRedis() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
        UserRepository userRepository = mock(UserRepository.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(redisTemplate.hasKey("governance:report:rate-limit:user-8-1711782000000")).thenReturn(false);
        when(redisTemplate.execute(anyRedisLongScript(), eq(List.of("governance:report:rate-limit:user-8-1711782000000")), any(), any(), any(), any(), any()))
                .thenReturn(1L);
        when(userRepository.findById(8L)).thenReturn(Optional.of(new AppUser(
                8L,
                "reporter@example.com",
                "hash",
                UserRole.STUDENT,
                "FREE",
                UserAccountStatus.ACTIVE,
                "Reporter",
                Instant.parse("2024-03-30T07:00:00Z")
        )));

        ContentReportRateLimitService service = new ContentReportRateLimitService(providerOf(redisTemplate), userRepository);

        boolean allowed = service.tryAcquire(
                8L,
                Instant.parse("2026-03-30T07:00:00Z"),
                5,
                Duration.ofMinutes(10),
                () -> List.of(
                        Instant.parse("2026-03-30T06:53:00Z"),
                        Instant.parse("2026-03-30T06:55:00Z")
                )
        );

        assertThat(allowed).isTrue();
        verify(zSetOperations).add(eq("governance:report:rate-limit:user-8-1711782000000"), anyTypedTupleSet());
        verify(redisTemplate).expire(eq("governance:report:rate-limit:user-8-1711782000000"), any(Duration.class));
    }

    @SuppressWarnings("unchecked")
    private RedisScript<Long> anyRedisLongScript() {
        return (RedisScript<Long>) any(RedisScript.class);
    }

    @SuppressWarnings("unchecked")
    private Set<ZSetOperations.TypedTuple<String>> anyTypedTupleSet() {
        return (Set<ZSetOperations.TypedTuple<String>>) any(Set.class);
    }

    private ObjectProvider<StringRedisTemplate> providerOf(StringRedisTemplate redisTemplate) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("stringRedisTemplate", redisTemplate);
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }

    private ObjectProvider<StringRedisTemplate> emptyRedisProvider() {
        return new StaticListableBeanFactory().getBeanProvider(StringRedisTemplate.class);
    }
}
