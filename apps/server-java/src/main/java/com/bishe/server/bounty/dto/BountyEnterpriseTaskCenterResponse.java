package com.bishe.server.bounty.dto;

import java.util.List;

public record BountyEnterpriseTaskCenterResponse(
        List<TaskCenterTaskItem> tasks,
        long total
) {
    public record TaskCenterTaskItem(
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
            Long deadlineAt,
            Long createdAt,
            Long updatedAt,
            int pendingCount,
            int contactedCount,
            int reviewedCount,
            List<SnapshotSubmissionItem> recentSubmissions
    ) {
    }

    public record SnapshotSubmissionItem(
            long submissionId,
            long studentUserId,
            String studentName,
            BountyStudentAvatarPayload studentAvatar,
            String status,
            String contentSummary,
            long communityScore7d,
            List<String> portraitTags,
            String reviewComment,
            Long reviewedAt,
            Long createdAt
    ) {
    }
}
