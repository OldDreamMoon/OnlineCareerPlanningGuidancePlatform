package com.bishe.server.notification.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationWebSocketSessionRegistryTest {

    @Test
    void shouldAggregateLocalAndRemoteSessionCountsOnSuccessfulSend() throws Exception {
        NotificationWebSocketOnlineRegistryService onlineRegistryService = mock(NotificationWebSocketOnlineRegistryService.class);
        when(onlineRegistryService.countOnlineSessions(18L)).thenReturn(2);

        NotificationWebSocketSessionRegistry registry = new NotificationWebSocketSessionRegistry(onlineRegistryService);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-local-1");
        when(session.isOpen()).thenReturn(true);

        registry.register(18L, session);
        NotificationWebSocketSessionRegistry.DeliveryResult result = registry.sendToUser(18L, "{\"type\":\"PING\"}");

        assertThat(result.deliveredSessionIds()).containsExactly("session-local-1");
        assertThat(result.deliveredCount()).isEqualTo(1);
        assertThat(result.localSessionCount()).isEqualTo(1);
        assertThat(result.onlineSessionCount()).isEqualTo(2);
        assertThat(result.remoteSessionCount()).isEqualTo(1);
        verify(onlineRegistryService).refreshSessionPresence(18L, "session-local-1");
        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    void shouldRemoveClosedSessionAndKeepRemotePresenceHint() {
        NotificationWebSocketOnlineRegistryService onlineRegistryService = mock(NotificationWebSocketOnlineRegistryService.class);
        when(onlineRegistryService.countOnlineSessions(23L)).thenReturn(1);

        NotificationWebSocketSessionRegistry registry = new NotificationWebSocketSessionRegistry(onlineRegistryService);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-local-2");
        when(session.isOpen()).thenReturn(false);

        registry.register(23L, session);
        NotificationWebSocketSessionRegistry.DeliveryResult result = registry.sendToUser(23L, "{\"type\":\"PING\"}");

        assertThat(result.deliveredSessionIds()).isEqualTo(List.of());
        assertThat(result.removedSessionIds()).containsExactly("session-local-2");
        assertThat(result.deliveredCount()).isZero();
        assertThat(result.localSessionCount()).isZero();
        assertThat(result.onlineSessionCount()).isEqualTo(1);
        assertThat(result.remoteSessionCount()).isEqualTo(1);
        verify(onlineRegistryService).removeSessionPresence(23L, "session-local-2");
    }

    @Test
    void shouldSendOnlyLocalSessionsWhenRemoteBridgeConsumesRedisMessage() throws Exception {
        NotificationWebSocketOnlineRegistryService onlineRegistryService = mock(NotificationWebSocketOnlineRegistryService.class);

        NotificationWebSocketSessionRegistry registry = new NotificationWebSocketSessionRegistry(onlineRegistryService);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-local-3");
        when(session.isOpen()).thenReturn(true);

        registry.register(31L, session);
        NotificationWebSocketSessionRegistry.DeliveryResult result = registry.sendToLocalUser(31L, "{\"type\":\"PING\"}");

        assertThat(result.deliveredSessionIds()).containsExactly("session-local-3");
        assertThat(result.localSessionCount()).isEqualTo(1);
        assertThat(result.onlineSessionCount()).isEqualTo(1);
        assertThat(result.remoteSessionCount()).isZero();
        verify(session).sendMessage(any(TextMessage.class));
    }
}
