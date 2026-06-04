package com.bishe.server.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationWebSocketRemoteDispatchServiceTest {

    @Test
    void shouldPublishRemoteDispatchOnlyToOtherInstances() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        NotificationWebSocketOnlineRegistryService onlineRegistryService = mock(NotificationWebSocketOnlineRegistryService.class);
        NotificationWebSocketSessionRegistry sessionRegistry = mock(NotificationWebSocketSessionRegistry.class);
        when(onlineRegistryService.currentInstanceId()).thenReturn("node-a");
        when(redisTemplate.convertAndSend(eq("notify:ws:dispatch:node-b"), eq("{\"userId\":42,\"payload\":\"{\\\"type\\\":\\\"PING\\\"}\",\"forwardedAt\":\"2026-03-31T13:08:00Z\"}")))
                .thenReturn(1L);

        NotificationWebSocketRemoteDispatchService service = new FixedEnvelopeRemoteDispatchService(
                providerOf(redisTemplate),
                onlineRegistryService,
                sessionRegistry,
                new ObjectMapper()
        );

        service.publishToRemoteInstances(Set.of("node-a", "node-b"), 42L, "{\"type\":\"PING\"}");

        verify(redisTemplate).convertAndSend(
                "notify:ws:dispatch:node-b",
                "{\"userId\":42,\"payload\":\"{\\\"type\\\":\\\"PING\\\"}\",\"forwardedAt\":\"2026-03-31T13:08:00Z\"}"
        );
    }

    @Test
    void shouldDispatchRedisMessageIntoLocalSessions() {
        NotificationWebSocketOnlineRegistryService onlineRegistryService = mock(NotificationWebSocketOnlineRegistryService.class);
        NotificationWebSocketSessionRegistry sessionRegistry = mock(NotificationWebSocketSessionRegistry.class);

        NotificationWebSocketRemoteDispatchService service = new NotificationWebSocketRemoteDispatchService(
                providerOf(null),
                onlineRegistryService,
                sessionRegistry,
                new ObjectMapper()
        );
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn("{\"userId\":58,\"payload\":\"{\\\"type\\\":\\\"PING\\\"}\"}".getBytes(StandardCharsets.UTF_8));

        service.onMessage(
                message,
                null
        );

        verify(sessionRegistry).sendToLocalUser(58L, "{\"type\":\"PING\"}");
    }

    private ObjectProvider<StringRedisTemplate> providerOf(StringRedisTemplate redisTemplate) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        if (redisTemplate != null) {
            beanFactory.addBean("stringRedisTemplate", redisTemplate);
        }
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }

    private static final class FixedEnvelopeRemoteDispatchService extends NotificationWebSocketRemoteDispatchService {

        private FixedEnvelopeRemoteDispatchService(
                ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
                NotificationWebSocketOnlineRegistryService onlineRegistryService,
                NotificationWebSocketSessionRegistry sessionRegistry,
                ObjectMapper objectMapper
        ) {
            super(stringRedisTemplateProvider, onlineRegistryService, sessionRegistry, objectMapper);
        }

        @Override
        protected Long currentForwardedAt() {
            return java.time.Instant.parse("2026-03-31T13:08:00Z").toEpochMilli();
        }
    }
}
