package com.bishe.server.bounty.dto;

public record BountyStudentAvatarPayload(
        boolean uploaded,
        String contentType,
        Long updatedAt
) {
}
