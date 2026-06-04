package com.bishe.server.notification.service;

import com.bishe.server.notification.NotificationProperties;
import com.bishe.server.notification.model.NotificationChannel;
import com.bishe.server.notification.model.NotificationDispatchStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationDispatchWorkerTest {

    @Test
    void shouldMarkRetryWhenWebsocketDispatcherReturnsOfflineRetryableResult() {
        NotificationDispatchService dispatchService = mock(NotificationDispatchService.class);
        NotificationChannelDispatcher dispatcher = mock(NotificationChannelDispatcher.class);
        NotificationProperties notificationProperties = new NotificationProperties();
        when(dispatcher.channel()).thenReturn(NotificationChannel.WEBSOCKET);

        NotificationChannelDispatcher.DispatchResult result = new NotificationChannelDispatcher.DispatchResult(
                NotificationDispatchStatus.FAILED,
                1,
                true,
                Duration.ofSeconds(30),
                "{\"type\":\"NOTIFICATION_CREATED\"}",
                "{\"onlineSessionCount\":0}",
                NotificationWebSocketDispatcher.NO_ACTIVE_SESSION_ERROR_CODE,
                "waiting for active websocket session",
                8L
        );
        when(dispatcher.dispatch(job())).thenReturn(result);

        NotificationDispatchWorker worker = new NotificationDispatchWorker(
                dispatchService,
                List.of(dispatcher),
                notificationProperties
        );

        worker.process(job());

        verify(dispatchService).markRetry(11L, result);
        verify(dispatchService, never()).markSkipped(11L, result);
        verify(dispatchService, never()).markDead(11L, result);
    }

    private NotificationDispatchService.DispatchJobSnapshot job() {
        return new NotificationDispatchService.DispatchJobSnapshot(
                11L,
                "ntfjob_test_11",
                101L,
                "ntfevt_test_11",
                18L,
                NotificationChannel.WEBSOCKET,
                NotificationDispatchStatus.PENDING,
                1,
                1,
                "{\"type\":\"NOTIFICATION_CREATED\"}",
                null,
                null,
                null,
                null
        );
    }
}
