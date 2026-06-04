package com.bishe.server.notification.ws;

import com.bishe.server.notification.service.NotificationWebSocketTicketService;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * WS 握手阶段使用短票据鉴权。
 */
@Component
public class NotificationWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTRIBUTE_USER_ID = "notificationUserId";

    private final NotificationWebSocketTicketService ticketService;

    public NotificationWebSocketHandshakeInterceptor(NotificationWebSocketTicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        String ticket = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("ticket");
        if (ticket == null || ticket.isBlank()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            long userId = ticketService.verifyAndExtractUserId(ticket);
            attributes.put(ATTRIBUTE_USER_ID, userId);
            return true;
        } catch (Exception ex) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
