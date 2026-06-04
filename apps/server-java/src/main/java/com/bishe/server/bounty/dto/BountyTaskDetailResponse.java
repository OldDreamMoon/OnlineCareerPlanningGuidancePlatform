package com.bishe.server.bounty.dto;

import java.util.List;

public record BountyTaskDetailResponse(
        long taskId,
        long enterpriseUserId,
        String enterpriseName,
        String enterpriseLogoUrl,
        String title,
        String description,
        String rewardDescription,
        String status,
        int submissionCount,
        Long acceptedSubmissionId,
        boolean mine,
        Long deadlineAt,
        Long closedAt,
        Long createdAt,
        Long updatedAt,
        MySubmission mySubmission
) {
    public record MySubmission(
            long submissionId,
            String status,
            String contentText,
            List<String> attachmentLinks,
            String reviewComment,
            Long reviewedAt,
            Long createdAt,
            Long updatedAt
    ) {
    }
}
