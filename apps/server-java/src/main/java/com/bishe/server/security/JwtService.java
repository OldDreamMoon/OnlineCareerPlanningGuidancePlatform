package com.bishe.server.security;

import com.bishe.server.auth.model.AppUser;
import com.bishe.server.auth.service.AuthException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 令牌签发与解析服务。
 */
@Service
public class JwtService {

    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_ROLE = "role";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    private final JwtProperties jwtProperties;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public String generateAccessToken(AppUser user) {
        Instant now = Instant.now();
        Instant expireAt = now.plusSeconds(jwtProperties.getAccessExpireMinutes() * 60);
        return Jwts.builder()
                .subject(String.valueOf(user.id()))
                .claim(CLAIM_TYPE, TOKEN_TYPE_ACCESS)
                .claim(CLAIM_ROLE, user.role().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expireAt))
                .signWith(secretKey())
                .compact();
    }

    public String generateRefreshToken(AppUser user) {
        Instant now = Instant.now();
        Instant expireAt = now.plusSeconds(jwtProperties.getRefreshExpireDays() * 24 * 60 * 60);
        return Jwts.builder()
                .subject(String.valueOf(user.id()))
                .claim(CLAIM_TYPE, TOKEN_TYPE_REFRESH)
                .claim(CLAIM_ROLE, user.role().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expireAt))
                .signWith(secretKey())
                .compact();
    }

    public Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception ex) {
            throw AuthException.tokenExpiredOrInvalid();
        }
    }

    public long extractUserId(Claims claims) {
        try {
            return Long.parseLong(claims.getSubject());
        } catch (Exception ex) {
            throw AuthException.tokenExpiredOrInvalid();
        }
    }

    public String extractRole(Claims claims) {
        Object role = claims.get(CLAIM_ROLE);
        if (role == null) {
            throw AuthException.tokenExpiredOrInvalid();
        }
        return role.toString();
    }

    public boolean isAccessToken(Claims claims) {
        return TOKEN_TYPE_ACCESS.equals(String.valueOf(claims.get(CLAIM_TYPE)));
    }

    public boolean isRefreshToken(Claims claims) {
        return TOKEN_TYPE_REFRESH.equals(String.valueOf(claims.get(CLAIM_TYPE)));
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
