package com.bishe.server.notification.service;

import com.bishe.server.notification.NotificationProperties;
import com.bishe.server.notification.model.NotificationChannel;
import com.bishe.server.notification.model.NotificationDispatchStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * WebSocket 实时推送渠道。
 */
@Component
public class NotificationWebSocketDispatcher implements NotificationChannelDispatcher {

    public static final String NO_ACTIVE_SESSION_ERROR_CODE = "NOTIFY-WS-001";
    public static final String REMOTE_FORWARD_UNAVAILABLE_ERROR_CODE = "NOTIFY-WS-003";

    private final NotificationProperties notificationProperties;
    private final NotificationWebSocketSessionRegistry sessionRegistry;
    private final NotificationWebSocketOnlineRegistryService onlineRegistryService;
    private final NotificationWebSocketRemoteDispatchService remoteDispatchService;

    public NotificationWebSocketDispatcher(
            NotificationProperties notificationProperties,
            NotificationWebSocketSessionRegistry sessionRegistry,
            NotificationWebSocketOnlineRegistryService onlineRegistryService,
            NotificationWebSocketRemoteDispatchService remoteDispatchService
    ) {
        this.notificationProperties = notificationProperties;
        this.sessionRegistry = sessionRegistry;
        this.onlineRegistryService = onlineRegistryService;
        this.remoteDispatchService = remoteDispatchService;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.WEBSOCKET;
    }

    @Override
    public DispatchResult dispatch(NotificationDispatchService.DispatchJobSnapshot job) {
        Instant startedAt = Instant.now();
        NotificationWebSocketSessionRegistry.DeliveryResult delivery = sessionRegistry.sendToUser(job.userId(), job.payloadJson());
        Set<String> remoteInstanceIds = delivery.remoteSessionCount() > 0
                ? onlineRegistryService.findRemoteInstanceIds(job.userId())
                : Set.of();
        int forwardedInstanceCount = remoteDispatchService.publishToRemoteInstances(remoteInstanceIds, job.userId(), job.payloadJson());
        long latencyMs = Duration.between(startedAt, Instant.now()).toMillis();
        String responseJson = "{\"deliveredCount\":" + delivery.deliveredCount()
                + ",\"localSessionCount\":" + delivery.localSessionCount()
                + ",\"onlineSessionCount\":" + delivery.onlineSessionCount()
                + ",\"remoteSessionCount\":" + delivery.remoteSessionCount()
                + ",\"remoteInstanceCount\":" + remoteInstanceIds.size()
                + ",\"forwardedInstanceCount\":" + forwardedInstanceCount + "}";
        if (delivery.deliveredCount() > 0 || forwardedInstanceCount > 0) {
            return new DispatchResult(
                    NotificationDispatchStatus.SENT,
                    job.attemptCount(),
                    false,
                    null,
                    job.payloadJson(),
                    responseJson,
                    null,
                    null,
                    latencyMs
            );
        }
        if (delivery.remoteSessionCount() > 0 || !remoteInstanceIds.isEmpty()) {
            return new DispatchResult(
                    NotificationDispatchStatus.FAILED,
                    job.attemptCount(),
                    true,
                    Duration.ofSeconds(3),
                    job.payloadJson(),
                    responseJson,
                    REMOTE_FORWARD_UNAVAILABLE_ERROR_CODE,
                    "remote websocket forward unavailable",
                    latencyMs
            );
        }
        return new DispatchResult(
                NotificationDispatchStatus.FAILED,
                job.attemptCount(),
                true,
                Duration.ofSeconds(Math.max(notificationProperties.getWebsocket().getOfflineRetryDelaySeconds(), 5L)),
                job.payloadJson(),
                responseJson,
                NO_ACTIVE_SESSION_ERROR_CODE,
                "waiting for active websocket session",
                latencyMs
        );
    }
}
