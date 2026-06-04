package com.bishe.server.bounty.dto;

public record BountyTaskManageResponse(
        long taskId,
        String status,
        Long updatedAt
) {
}
