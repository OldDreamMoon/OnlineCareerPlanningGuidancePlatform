package com.bishe.server.notification.ws;

import com.bishe.server.notification.service.NotificationService;
import com.bishe.server.notification.service.NotificationWebSocketSessionRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationWebSocketHandlerTest {

    @Test
    void shouldSendConnectedAndSyncRequiredEventsAfterConnectionEstablished() throws Exception {
        NotificationWebSocketSessionRegistry sessionRegistry = mock(NotificationWebSocketSessionRegistry.class);
        NotificationService notificationService = mock(NotificationService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        NotificationWebSocketHandler handler = new NotificationWebSocketHandler(
                sessionRegistry,
                notificationService,
                objectMapper
        );

        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(Map.of(
                NotificationWebSocketHandshakeInterceptor.ATTRIBUTE_USER_ID,
                18L
        ));

        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);

        handler.afterConnectionEstablished(session);

        verify(sessionRegistry).register(18L, session);
        verify(session, times(2)).sendMessage(messageCaptor.capture());

        JsonNode connectedPayload = objectMapper.readTree(messageCaptor.getAllValues().get(0).getPayload());
        JsonNode syncRequiredPayload = objectMapper.readTree(messageCaptor.getAllValues().get(1).getPayload());

        assertThat(connectedPayload.path("type").asText()).isEqualTo("CONNECTED");
        assertThat(connectedPayload.path("serverTime").asLong()).isPositive();
        assertThat(syncRequiredPayload.path("type").asText()).isEqualTo("SYNC_REQUIRED");
        assertThat(syncRequiredPayload.path("reason").asText()).isEqualTo("CONNECTED");
        assertThat(syncRequiredPayload.path("serverTime").asLong()).isPositive();
    }
}
