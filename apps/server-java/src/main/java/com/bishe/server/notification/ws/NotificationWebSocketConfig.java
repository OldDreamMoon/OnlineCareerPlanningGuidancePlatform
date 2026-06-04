package com.bishe.server.notification.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 通知 WS 通道配置。
 */
@Configuration
@EnableWebSocket
public class NotificationWebSocketConfig implements WebSocketConfigurer {

    private final NotificationWebSocketHandler webSocketHandler;
    private final NotificationWebSocketHandshakeInterceptor handshakeInterceptor;

    public NotificationWebSocketConfig(
            NotificationWebSocketHandler webSocketHandler,
            NotificationWebSocketHandshakeInterceptor handshakeInterceptor
    ) {
        this.webSocketHandler = webSocketHandler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketHandler, "/ws/notifications")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns(
                        "http://localhost:*",
                        "http://127.0.0.1:*",
                        "https://localhost:*",
                        "https://127.0.0.1:*",
                        "https://*.app.github.dev",
                        "https://*.githubpreview.dev",
                        "https://*.vscode.dev"
                );
    }
}
