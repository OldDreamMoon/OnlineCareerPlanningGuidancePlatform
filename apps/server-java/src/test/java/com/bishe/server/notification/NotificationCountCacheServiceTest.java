package com.bishe.server.notification;

import com.bishe.server.auth.model.AppUser;
import com.bishe.server.auth.model.UserAccountStatus;
import com.bishe.server.auth.model.UserRole;
import com.bishe.server.auth.repository.UserRepository;
import com.bishe.server.notification.model.NotificationCategory;
import com.bishe.server.notification.service.NotificationCountCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationCountCacheServiceTest {

    @Test
    void shouldFallbackToDatabaseWhenRedisUnavailable() {
        UserRepository userRepository = mock(UserRepository.class);
        NotificationCountCacheService service = new NotificationCountCacheService(emptyRedisProvider(), userRepository);

        NotificationCountCacheService.NotificationCountSnapshot snapshot = service.getCounts(
                8L,
                () -> new NotificationCountCacheService.NotificationCountSnapshot(
                        3L,
                        1L,
                        Map.of(NotificationCategory.CONSULT, 1L)
                )
        );

        assertThat(snapshot.unreadCount()).isEqualTo(3L);
        assertThat(snapshot.actionableCount(null)).isEqualTo(1L);
        assertThat(snapshot.actionableCount(NotificationCategory.CONSULT)).isEqualTo(1L);
        assertThat(snapshot.actionableCount(NotificationCategory.AI_TASK)).isZero();
    }

    @Test
    void shouldBackfillCountsToRedisOnCacheMiss() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        UserRepository userRepository = mock(UserRepository.class);
        String key = "notification:counts:user-8-1711782000000";

        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(key))
                .thenReturn(Map.of())
                .thenReturn(buildReadyEntries(3L, 2L, Map.of(
                        NotificationCategory.CONSULT, 1L,
                        NotificationCategory.AI_TASK, 1L
                )));
        when(userRepository.findById(8L)).thenReturn(Optional.of(buildUser(8L)));

        NotificationCountCacheService service = new NotificationCountCacheService(providerOf(redisTemplate), userRepository);

        NotificationCountCacheService.NotificationCountSnapshot snapshot = service.getCounts(
                8L,
                () -> new NotificationCountCacheService.NotificationCountSnapshot(
                        3L,
                        2L,
                        Map.of(
                                NotificationCategory.CONSULT, 1L,
                                NotificationCategory.AI_TASK, 1L
                        )
                )
        );

        assertThat(snapshot.unreadCount()).isEqualTo(3L);
        assertThat(snapshot.actionableCount(null)).isEqualTo(2L);
        assertThat(snapshot.actionableCount(NotificationCategory.CONSULT)).isEqualTo(1L);
        assertThat(snapshot.actionableCount(NotificationCategory.AI_TASK)).isEqualTo(1L);
        verify(hashOperations).putAll(eq(key), anyMap());
        verify(redisTemplate).expire(eq(key), any(Duration.class));
    }

    @Test
    void shouldIncrementCachedCountsWhenNotificationCreated() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        UserRepository userRepository = mock(UserRepository.class);
        String key = "notification:counts:user-8-1711782000000";

        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.hasKey(key, "_ready")).thenReturn(true);
        when(userRepository.findById(8L)).thenReturn(Optional.of(buildUser(8L)));

        NotificationCountCacheService service = new NotificationCountCacheService(providerOf(redisTemplate), userRepository);
        service.recordCreatedAfterCommit(8L, NotificationCategory.CONSULT, "VIEW_CONSULT_ORDER");

        verify(hashOperations).increment(key, "unread", 1L);
        verify(hashOperations).increment(key, "actionable:ALL", 1L);
        verify(hashOperations).increment(key, "actionable:CONSULT", 1L);
        verify(redisTemplate).expire(eq(key), any(Duration.class));
    }

    @Test
    void shouldEvictCountsCacheAfterRead() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        UserRepository userRepository = mock(UserRepository.class);
        String key = "notification:counts:user-8-1711782000000";
        when(userRepository.findById(8L)).thenReturn(Optional.of(buildUser(8L)));

        NotificationCountCacheService service = new NotificationCountCacheService(providerOf(redisTemplate), userRepository);
        service.evictAfterCommit(8L);

        verify(redisTemplate).delete(key);
    }

    private AppUser buildUser(long userId) {
        return new AppUser(
                userId,
                "notify@example.com",
                "hash",
                UserRole.STUDENT,
                "FREE",
                UserAccountStatus.ACTIVE,
                "NotifyUser",
                Instant.parse("2024-03-30T07:00:00Z")
        );
    }

    private Map<Object, Object> buildReadyEntries(long unreadCount, long actionableTotal, Map<NotificationCategory, Long> actionableByCategory) {
        LinkedHashMap<Object, Object> entries = new LinkedHashMap<>();
        entries.put("_ready", "1");
        entries.put("unread", String.valueOf(unreadCount));
        entries.put("actionable:ALL", String.valueOf(actionableTotal));
        for (NotificationCategory category : NotificationCategory.values()) {
            entries.put("actionable:" + category.name(), String.valueOf(actionableByCategory.getOrDefault(category, 0L)));
        }
        return entries;
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
