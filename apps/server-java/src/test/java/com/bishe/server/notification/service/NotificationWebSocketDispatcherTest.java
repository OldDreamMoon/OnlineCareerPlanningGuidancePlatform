package com.bishe.server.notification.service;

import com.bishe.server.notification.NotificationProperties;
import com.bishe.server.notification.model.NotificationChannel;
import com.bishe.server.notification.model.NotificationDispatchStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationWebSocketDispatcherTest {

    @Test
    void shouldForwardRemoteOnlyPresenceThroughRedisBridge() {
        NotificationWebSocketSessionRegistry sessionRegistry = mock(NotificationWebSocketSessionRegistry.class);
        NotificationWebSocketOnlineRegistryService onlineRegistryService = mock(NotificationWebSocketOnlineRegistryService.class);
        NotificationWebSocketRemoteDispatchService remoteDispatchService = mock(NotificationWebSocketRemoteDispatchService.class);
        when(sessionRegistry.sendToUser(18L, "{\"type\":\"NOTIFICATION_CREATED\"}"))
                .thenReturn(new NotificationWebSocketSessionRegistry.DeliveryResult(
                        List.of(),
                        List.of(),
                        0,
                        0,
                        2,
                        2
                ));
        when(onlineRegistryService.findRemoteInstanceIds(18L)).thenReturn(Set.of("node-b"));
        when(remoteDispatchService.publishToRemoteInstances(Set.of("node-b"), 18L, "{\"type\":\"NOTIFICATION_CREATED\"}"))
                .thenReturn(1);

        NotificationWebSocketDispatcher dispatcher = new NotificationWebSocketDispatcher(
                websocketProperties(),
                sessionRegistry,
                onlineRegistryService,
                remoteDispatchService
        );

        NotificationChannelDispatcher.DispatchResult result = dispatcher.dispatch(snapshot(18L));

        assertThat(result.status()).isEqualTo(NotificationDispatchStatus.SENT);
        assertThat(result.errorCode()).isNull();
        assertThat(result.responseSnapshotJson()).contains("\"forwardedInstanceCount\":1");
        verify(remoteDispatchService).publishToRemoteInstances(Set.of("node-b"), 18L, "{\"type\":\"NOTIFICATION_CREATED\"}");
    }

    @Test
    void shouldMarkDeliverySentWhenCurrentInstanceHasReachableSession() {
        NotificationWebSocketSessionRegistry sessionRegistry = mock(NotificationWebSocketSessionRegistry.class);
        NotificationWebSocketOnlineRegistryService onlineRegistryService = mock(NotificationWebSocketOnlineRegistryService.class);
        NotificationWebSocketRemoteDispatchService remoteDispatchService = mock(NotificationWebSocketRemoteDispatchService.class);
        when(sessionRegistry.sendToUser(19L, "{\"type\":\"NOTIFICATION_CREATED\"}"))
                .thenReturn(new NotificationWebSocketSessionRegistry.DeliveryResult(
                        List.of("session-local-1"),
                        List.of(),
                        1,
                        1,
                        1,
                        0
                ));

        NotificationWebSocketDispatcher dispatcher = new NotificationWebSocketDispatcher(
                websocketProperties(),
                sessionRegistry,
                onlineRegistryService,
                remoteDispatchService
        );

        NotificationChannelDispatcher.DispatchResult result = dispatcher.dispatch(snapshot(19L));

        assertThat(result.status()).isEqualTo(NotificationDispatchStatus.SENT);
        assertThat(result.errorCode()).isNull();
        assertThat(result.responseSnapshotJson()).contains("\"deliveredCount\":1");
    }

    @Test
    void shouldRetryWhenRemotePresenceExistsButForwardBridgeHasNoSubscriber() {
        NotificationWebSocketSessionRegistry sessionRegistry = mock(NotificationWebSocketSessionRegistry.class);
        NotificationWebSocketOnlineRegistryService onlineRegistryService = mock(NotificationWebSocketOnlineRegistryService.class);
        NotificationWebSocketRemoteDispatchService remoteDispatchService = mock(NotificationWebSocketRemoteDispatchService.class);
        when(sessionRegistry.sendToUser(20L, "{\"type\":\"NOTIFICATION_CREATED\"}"))
                .thenReturn(new NotificationWebSocketSessionRegistry.DeliveryResult(
                        List.of(),
                        List.of(),
                        0,
                        0,
                        1,
                        1
                ));
        when(onlineRegistryService.findRemoteInstanceIds(20L)).thenReturn(Set.of("node-c"));
        when(remoteDispatchService.publishToRemoteInstances(Set.of("node-c"), 20L, "{\"type\":\"NOTIFICATION_CREATED\"}"))
                .thenReturn(0);

        NotificationWebSocketDispatcher dispatcher = new NotificationWebSocketDispatcher(
                websocketProperties(),
                sessionRegistry,
                onlineRegistryService,
                remoteDispatchService
        );

        NotificationChannelDispatcher.DispatchResult result = dispatcher.dispatch(snapshot(20L));

        assertThat(result.status()).isEqualTo(NotificationDispatchStatus.FAILED);
        assertThat(result.retryable()).isTrue();
        assertThat(result.errorCode()).isEqualTo("NOTIFY-WS-003");
    }

    @Test
    void shouldRetryWhenUserHasNoActiveWebsocketSession() {
        NotificationWebSocketSessionRegistry sessionRegistry = mock(NotificationWebSocketSessionRegistry.class);
        NotificationWebSocketOnlineRegistryService onlineRegistryService = mock(NotificationWebSocketOnlineRegistryService.class);
        NotificationWebSocketRemoteDispatchService remoteDispatchService = mock(NotificationWebSocketRemoteDispatchService.class);
        when(sessionRegistry.sendToUser(21L, "{\"type\":\"NOTIFICATION_CREATED\"}"))
                .thenReturn(new NotificationWebSocketSessionRegistry.DeliveryResult(
                        List.of(),
                        List.of(),
                        0,
                        0,
                        0,
                        0
                ));

        NotificationWebSocketDispatcher dispatcher = new NotificationWebSocketDispatcher(
                websocketProperties(),
                sessionRegistry,
                onlineRegistryService,
                remoteDispatchService
        );

        NotificationChannelDispatcher.DispatchResult result = dispatcher.dispatch(snapshot(21L));

        assertThat(result.status()).isEqualTo(NotificationDispatchStatus.FAILED);
        assertThat(result.retryable()).isTrue();
        assertThat(result.retryDelay()).isEqualTo(java.time.Duration.ofSeconds(30));
        assertThat(result.errorCode()).isEqualTo("NOTIFY-WS-001");
        assertThat(result.errorMessage()).isEqualTo("waiting for active websocket session");
    }

    private NotificationProperties websocketProperties() {
        NotificationProperties properties = new NotificationProperties();
        properties.getWebsocket().setOfflineRetryDelaySeconds(30L);
        return properties;
    }

    private NotificationDispatchService.DispatchJobSnapshot snapshot(long userId) {
        return new NotificationDispatchService.DispatchJobSnapshot(
                1L,
                "ntfjob_test_1",
                101L,
                "ntfevt_test_1",
                userId,
                NotificationChannel.WEBSOCKET,
                NotificationDispatchStatus.PENDING,
                1,
                10,
                "{\"type\":\"NOTIFICATION_CREATED\"}",
                null,
                null,
                null,
                null
        );
    }
}
