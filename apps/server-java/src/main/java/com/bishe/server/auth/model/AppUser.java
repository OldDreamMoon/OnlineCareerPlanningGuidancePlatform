package com.bishe.server.auth.model;

import java.time.Instant;

/**
 * 鉴权领域用户对象。
 */
public record AppUser(
        Long id,
        String email,
        String passwordHash,
        UserRole role,
        String tier,
        UserAccountStatus status,
        String displayName,
        Instant createdAt
) {
}
