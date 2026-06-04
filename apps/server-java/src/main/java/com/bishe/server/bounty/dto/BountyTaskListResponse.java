package com.bishe.server.bounty.dto;

import java.util.List;

public record BountyTaskListResponse(
        List<TaskItem> records,
        long total,
        int page,
        int size
) {
    public record TaskItem(
            long taskId,
            long enterpriseUserId,
            String enterpriseName,
            String enterpriseLogoUrl,
            String title,
            String descriptionSummary,
            String rewardDescription,
            String status,
            int submissionCount,
            Long acceptedSubmissionId,
            boolean submittedByMe,
            Long deadlineAt,
            Long createdAt,
            Long updatedAt
    ) {
    }
}
