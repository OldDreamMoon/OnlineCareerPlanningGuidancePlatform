package com.bishe.server.notification.service;

import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在线通知会话注册表。
 */
@Component
public class NotificationWebSocketSessionRegistry {

    private final NotificationWebSocketOnlineRegistryService onlineRegistryService;
    private final Map<Long, Map<String, WebSocketSession>> sessionsByUserId = new ConcurrentHashMap<>();

    public NotificationWebSocketSessionRegistry(NotificationWebSocketOnlineRegistryService onlineRegistryService) {
        this.onlineRegistryService = onlineRegistryService;
    }

    public void register(long userId, WebSocketSession session) {
        if (userId <= 0 || session == null) {
            return;
        }
        sessionsByUserId
                .computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>())
                .put(session.getId(), session);
        onlineRegistryService.refreshSessionPresence(userId, session.getId());
    }

    public void unregister(long userId, String sessionId) {
        if (userId <= 0 || sessionId == null || sessionId.isBlank()) {
            return;
        }
        Map<String, WebSocketSession> userSessions = sessionsByUserId.get(userId);
        if (userSessions == null) {
            onlineRegistryService.removeSessionPresence(userId, sessionId);
            return;
        }
        userSessions.remove(sessionId);
        if (userSessions.isEmpty()) {
            sessionsByUserId.remove(userId);
        }
        onlineRegistryService.removeSessionPresence(userId, sessionId);
    }

    public void touch(long userId, String sessionId) {
        if (userId <= 0 || sessionId == null || sessionId.isBlank()) {
            return;
        }
        Map<String, WebSocketSession> userSessions = sessionsByUserId.get(userId);
        WebSocketSession session = userSessions == null ? null : userSessions.get(sessionId);
        if (session != null && session.isOpen()) {
            onlineRegistryService.refreshSessionPresence(userId, sessionId);
        }
    }

    public DeliveryResult sendToUser(long userId, String payload) {
        DeliveryResult localDelivery = sendToLocalUser(userId, payload);
        int onlineSessionCount = Math.max(localDelivery.localSessionCount(), onlineRegistryService.countOnlineSessions(userId));
        int remoteSessionCount = Math.max(onlineSessionCount - localDelivery.localSessionCount(), 0);
        return new DeliveryResult(
                localDelivery.deliveredSessionIds(),
                localDelivery.removedSessionIds(),
                localDelivery.deliveredCount(),
                localDelivery.localSessionCount(),
                onlineSessionCount,
                remoteSessionCount
        );
    }

    public DeliveryResult sendToLocalUser(long userId, String payload) {
        Map<String, WebSocketSession> userSessions = sessionsByUserId.get(userId);
        if (userSessions == null || userSessions.isEmpty()) {
            return new DeliveryResult(List.of(), List.of(), 0, 0, 0, 0);
        }

        List<String> deliveredSessionIds = new ArrayList<>();
        List<String> removedSessionIds = new ArrayList<>();
        for (Map.Entry<String, WebSocketSession> entry : userSessions.entrySet()) {
            WebSocketSession session = entry.getValue();
            if (session == null || !session.isOpen()) {
                removedSessionIds.add(entry.getKey());
                continue;
            }
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(payload));
                }
                deliveredSessionIds.add(entry.getKey());
            } catch (Exception ex) {
                removedSessionIds.add(entry.getKey());
            }
        }

        for (String removedSessionId : removedSessionIds) {
            unregister(userId, removedSessionId);
        }
        int localSessionCount = countOpenLocalSessions(userId);
        return new DeliveryResult(
                deliveredSessionIds,
                removedSessionIds,
                deliveredSessionIds.size(),
                localSessionCount,
                localSessionCount,
                0
        );
    }

    @Scheduled(fixedDelay = 30_000L)
    public void refreshLocalSessionPresence() {
        for (Map.Entry<Long, Map<String, WebSocketSession>> userEntry : new ArrayList<>(sessionsByUserId.entrySet())) {
            long userId = userEntry.getKey();
            Map<String, WebSocketSession> userSessions = userEntry.getValue();
            if (userSessions == null || userSessions.isEmpty()) {
                continue;
            }
            for (Map.Entry<String, WebSocketSession> sessionEntry : new ArrayList<>(userSessions.entrySet())) {
                String sessionId = sessionEntry.getKey();
                WebSocketSession session = sessionEntry.getValue();
                if (session == null || !session.isOpen()) {
                    unregister(userId, sessionId);
                    continue;
                }
                onlineRegistryService.refreshSessionPresence(userId, sessionId);
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        onlineRegistryService.evictLocalInstancePresenceNow();
        sessionsByUserId.clear();
    }

    private int countOpenLocalSessions(long userId) {
        Map<String, WebSocketSession> userSessions = sessionsByUserId.get(userId);
        if (userSessions == null || userSessions.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (WebSocketSession session : userSessions.values()) {
            if (session != null && session.isOpen()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 单次 WS 发送结果。
     */
    public record DeliveryResult(
            List<String> deliveredSessionIds,
            List<String> removedSessionIds,
            int deliveredCount,
            int localSessionCount,
            int onlineSessionCount,
            int remoteSessionCount
    ) {
    }
}
