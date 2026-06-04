package com.bishe.server.notification;

import com.bishe.server.auth.model.AppUser;
import com.bishe.server.auth.model.UserAccountStatus;
import com.bishe.server.auth.model.UserRole;
import com.bishe.server.auth.repository.UserRepository;
import com.bishe.server.notification.model.NotificationCategory;
import com.bishe.server.notification.model.NotificationPriority;
import com.bishe.server.notification.repository.NotificationPreferenceRepository;
import com.bishe.server.notification.service.NotificationPreferenceCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationPreferenceCacheServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldBackfillUserPreferencesOnCacheMiss() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        UserRepository userRepository = mock(UserRepository.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Instant createdAt = Instant.parse("2026-03-29T08:00:00Z");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, createdAt)));
        String key = "notification:preferences:user:user-7-" + createdAt.toEpochMilli();
        List<NotificationPreferenceRepository.NotificationPreferenceRow> rows = List.of(
                new NotificationPreferenceRepository.NotificationPreferenceRow(
                        1L,
                        7L,
                        NotificationCategory.CONSULT,
                        true,
                        false,
                        false,
                        true,
                        NotificationPriority.HIGH,
                        null,
                        Instant.parse("2026-03-30T08:00:00Z")
                )
        );
        when(valueOperations.get(key))
                .thenReturn(null, objectMapper.writeValueAsString(rows));

        NotificationPreferenceCacheService service = new NotificationPreferenceCacheService(
                objectMapper,
                providerOf(redisTemplate),
                userRepository
        );

        Map<NotificationCategory, NotificationPreferenceRepository.NotificationPreferenceRow> preferences =
                service.getUserPreferences(7L, () -> rows);

        assertThat(preferences).containsKey(NotificationCategory.CONSULT);
        assertThat(preferences.get(NotificationCategory.CONSULT).emailEnabled()).isTrue();
        verify(valueOperations).set(eq(key), anyString(), any(Duration.class));
    }

    @Test
    void shouldSupportCachedEmptyPreferenceSnapshot() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        UserRepository userRepository = mock(UserRepository.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Instant createdAt = Instant.parse("2026-03-30T08:00:00Z");
        when(userRepository.findById(9L)).thenReturn(Optional.of(user(9L, createdAt)));
        String key = "notification:preferences:user:user-9-" + createdAt.toEpochMilli();
        when(valueOperations.get(key)).thenReturn(null, "[]");

        NotificationPreferenceCacheService service = new NotificationPreferenceCacheService(
                objectMapper,
                providerOf(redisTemplate),
                userRepository
        );

        Map<NotificationCategory, NotificationPreferenceRepository.NotificationPreferenceRow> preferences =
                service.getUserPreferences(9L, List::of);

        assertThat(preferences).isEmpty();
        verify(valueOperations).set(eq(key), eq("[]"), any(Duration.class));
    }

    @Test
    void shouldEvictUserPreferencesImmediatelyWithoutTransaction() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        UserRepository userRepository = mock(UserRepository.class);
        Instant createdAt = Instant.parse("2026-03-31T08:00:00Z");
        when(userRepository.findById(11L)).thenReturn(Optional.of(user(11L, createdAt)));
        String key = "notification:preferences:user:user-11-" + createdAt.toEpochMilli();

        NotificationPreferenceCacheService service = new NotificationPreferenceCacheService(
                objectMapper,
                providerOf(redisTemplate),
                userRepository
        );

        service.evictUserPreferencesAfterCommit(11L);

        verify(redisTemplate).delete(key);
    }

    private ObjectProvider<StringRedisTemplate> providerOf(StringRedisTemplate redisTemplate) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("stringRedisTemplate", redisTemplate);
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }

    private AppUser user(long userId, Instant createdAt) {
        return new AppUser(
                userId,
                "notify-test-" + userId + "@example.com",
                "hash",
                UserRole.STUDENT,
                "FREE",
                UserAccountStatus.ACTIVE,
                "通知测试用户",
                createdAt
        );
    }
}
