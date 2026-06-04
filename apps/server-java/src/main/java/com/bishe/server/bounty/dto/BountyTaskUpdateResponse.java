package com.bishe.server.bounty.dto;

public record BountyTaskUpdateResponse(
        long taskId,
        String status,
        Long updatedAt
) {
}
