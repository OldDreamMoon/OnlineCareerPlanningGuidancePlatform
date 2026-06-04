package com.bishe.server.notification.ws;

import com.bishe.server.common.TimePayloads;
import com.bishe.server.notification.service.NotificationService;
import com.bishe.server.notification.service.NotificationWebSocketSessionRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.Map;

/**
 * 浏览器端通知 WS handler。
 */
@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private final NotificationWebSocketSessionRegistry sessionRegistry;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public NotificationWebSocketHandler(
            NotificationWebSocketSessionRegistry sessionRegistry,
            NotificationService notificationService,
            ObjectMapper objectMapper
    ) {
        this.sessionRegistry = sessionRegistry;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = resolveUserId(session);
        if (userId == null || userId <= 0) {
            // 握手阶段没有解析出用户时立即关闭，WS 通道不接受匿名连接。
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("notification user missing"));
            return;
        }
        sessionRegistry.register(userId, session);
        synchronized (session) {
            // CONNECTED 告诉前端通道已建立，SYNC_REQUIRED 触发一次列表补拉校准。
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                    "type", "CONNECTED",
                    "serverTime", TimePayloads.toEpochMillis(Instant.now())
            ))));
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                    "type", "SYNC_REQUIRED",
                    "reason", "CONNECTED",
                    "serverTime", TimePayloads.toEpochMillis(Instant.now())
            ))));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long userId = resolveUserId(session);
        if (userId == null || userId <= 0) {
            return;
        }
        JsonNode payload = objectMapper.readTree(message.getPayload());
        String type = payload.path("type").asText("");
        if ("ACK".equalsIgnoreCase(type)) {
            String jobId = payload.path("jobId").asText("");
            if (!jobId.isBlank()) {
                // ACK 只确认 websocket delivery job，收件箱已读仍走通知业务接口。
                notificationService.acknowledgeWebSocketDelivery(userId, jobId);
            }
        }
        sessionRegistry.touch(userId, session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = resolveUserId(session);
        if (userId != null && userId > 0) {
            // 关闭时释放 presence，后续派发会落到离线重试/补拉链路。
            sessionRegistry.unregister(userId, session.getId());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        Long userId = resolveUserId(session);
        if (userId != null && userId > 0) {
            sessionRegistry.unregister(userId, session.getId());
        }
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private Long resolveUserId(WebSocketSession session) {
        Object rawValue = session.getAttributes().get(NotificationWebSocketHandshakeInterceptor.ATTRIBUTE_USER_ID);
        if (rawValue instanceof Number number) {
            return number.longValue();
        }
        return null;
    }
}
