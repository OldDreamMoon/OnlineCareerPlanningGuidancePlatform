package com.bishe.server.bounty.dto;

import java.util.List;

public record BountySubmissionReviewResponse(
        long submissionId,
        long taskId,
        String status,
        String taskStatus,
        Long reviewedAt,
        String comment,
        String contactIntent,
        String rejectTemplate,
        String reviewNote,
        Long updatedAt,
        List<BountySubmissionHistoryItem> history
) {
}
