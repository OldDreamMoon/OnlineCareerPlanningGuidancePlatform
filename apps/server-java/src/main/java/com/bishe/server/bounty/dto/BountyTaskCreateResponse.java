package com.bishe.server.bounty.dto;

public record BountyTaskCreateResponse(
        long taskId,
        String status,
        Long createdAt
) {
}
