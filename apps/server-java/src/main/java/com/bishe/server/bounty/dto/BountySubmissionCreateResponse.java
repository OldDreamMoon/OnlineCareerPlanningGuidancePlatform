package com.bishe.server.bounty.dto;

public record BountySubmissionCreateResponse(
        long submissionId,
        long taskId,
        String status,
        Long createdAt
) {
}
