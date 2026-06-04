package com.bishe.server.notification.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationWebSocketOnlineRegistryServiceTest {

    @Test
    void shouldRefreshRedisPresenceForUserAndInstanceIndexes() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        NotificationWebSocketOnlineRegistryService service = new NotificationWebSocketOnlineRegistryService(redisTemplate, "node-a");

        service.refreshSessionPresence(21L, "session-1");

        verify(valueOperations).set(
                "notify:ws:online:session:node-a:session-1",
                "21",
                Duration.ofSeconds(90)
        );
        verify(setOperations).add("notify:ws:online:user:21", "node-a:session-1");
        verify(redisTemplate).expire("notify:ws:online:user:21", Duration.ofMinutes(5));
        verify(setOperations).add("notify:ws:online:instance:node-a", "node-a:session-1");
        verify(redisTemplate).expire("notify:ws:online:instance:node-a", Duration.ofMinutes(5));
    }

    @Test
    void shouldCountOnlyAliveSessionsAndPruneStaleRefs() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("notify:ws:online:user:25"))
                .thenReturn(Set.of("node-a:session-1", "node-b:session-2"));
        when(redisTemplate.hasKey("notify:ws:online:session:node-a:session-1")).thenReturn(true);
        when(redisTemplate.hasKey("notify:ws:online:session:node-b:session-2")).thenReturn(false);

        NotificationWebSocketOnlineRegistryService service = new NotificationWebSocketOnlineRegistryService(redisTemplate, "node-a");

        int onlineCount = service.countOnlineSessions(25L);

        assertThat(onlineCount).isEqualTo(1);
        verify(setOperations).remove("notify:ws:online:user:25", "node-b:session-2");
    }

    @Test
    void shouldEvictLocalInstancePresenceOnShutdownHook() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("notify:ws:online:instance:node-a"))
                .thenReturn(Set.of("node-a:session-1", "node-a:session-2"));
        when(valueOperations.get("notify:ws:online:session:node-a:session-1")).thenReturn("31");
        when(valueOperations.get("notify:ws:online:session:node-a:session-2")).thenReturn("32");

        NotificationWebSocketOnlineRegistryService service = new NotificationWebSocketOnlineRegistryService(redisTemplate, "node-a");

        service.evictLocalInstancePresenceNow();

        verify(setOperations).remove("notify:ws:online:user:31", "node-a:session-1");
        verify(setOperations).remove("notify:ws:online:user:32", "node-a:session-2");
        verify(redisTemplate).delete("notify:ws:online:session:node-a:session-1");
        verify(redisTemplate).delete("notify:ws:online:session:node-a:session-2");
        verify(redisTemplate).delete("notify:ws:online:instance:node-a");
    }

    @Test
    void shouldResolveRemoteInstanceIdsAndPruneStaleRefs() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("notify:ws:online:user:29"))
                .thenReturn(Set.of("node-a:session-1", "node-b:session-2", "node-c:session-3"));
        when(redisTemplate.hasKey("notify:ws:online:session:node-a:session-1")).thenReturn(true);
        when(redisTemplate.hasKey("notify:ws:online:session:node-b:session-2")).thenReturn(true);
        when(redisTemplate.hasKey("notify:ws:online:session:node-c:session-3")).thenReturn(false);

        NotificationWebSocketOnlineRegistryService service = new NotificationWebSocketOnlineRegistryService(redisTemplate, "node-a");

        Set<String> remoteInstanceIds = service.findRemoteInstanceIds(29L);

        assertThat(remoteInstanceIds).containsExactly("node-b");
        verify(setOperations).remove("notify:ws:online:user:29", "node-c:session-3");
    }
}
