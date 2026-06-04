package com.bishe.server.notification.service;

import com.bishe.server.notification.NotificationProperties;
import com.bishe.server.security.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * 为浏览器 WS 握手签发短时票据。
 */
@Service
public class NotificationWebSocketTicketService {

    private static final String CLAIM_TYPE = "type";
    private static final String TICKET_TYPE = "notification_ws_ticket";

    private final JwtProperties jwtProperties;
    private final NotificationProperties notificationProperties;

    public NotificationWebSocketTicketService(JwtProperties jwtProperties, NotificationProperties notificationProperties) {
        this.jwtProperties = jwtProperties;
        this.notificationProperties = notificationProperties;
    }

    public TicketIssueResult issueTicket(long userId) {
        Instant now = Instant.now();
        Instant expireAt = now.plusSeconds(Math.max(notificationProperties.getWebsocket().getTicketTtlSeconds(), 30L));
        String ticket = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYPE, TICKET_TYPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expireAt))
                .signWith(secretKey())
                .compact();
        return new TicketIssueResult(ticket, expireAt);
    }

    public long verifyAndExtractUserId(String ticket) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey())
                .build()
                .parseSignedClaims(ticket)
                .getPayload();
        if (!TICKET_TYPE.equals(String.valueOf(claims.get(CLAIM_TYPE)))) {
            throw new IllegalArgumentException("notification ws ticket invalid");
        }
        return Long.parseLong(claims.getSubject());
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 发票结果。
     */
    public record TicketIssueResult(
            String ticket,
            Instant expiresAt
    ) {
    }
}
