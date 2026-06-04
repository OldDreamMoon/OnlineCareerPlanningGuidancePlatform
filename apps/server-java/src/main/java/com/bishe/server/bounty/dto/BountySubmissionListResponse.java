package com.bishe.server.bounty.dto;

import com.bishe.server.profile.dto.StudentProfileSocialLinkItem;

import java.util.List;

public record BountySubmissionListResponse(
        List<SubmissionItem> records,
        long total,
        int page,
        int size
) {
    public record SubmissionItem(
            long submissionId,
            long studentUserId,
            String studentName,
            BountyStudentAvatarPayload studentAvatar,
            String status,
            String contentSummary,
            String contentText,
            List<String> attachmentLinks,
            long communityScore7d,
            List<String> portraitTags,
            Long portraitUpdatedAt,
            String reviewComment,
            String contactIntent,
            String rejectTemplate,
            String reviewNote,
            Long reviewedAt,
            Long createdAt,
            Long updatedAt,
            ContactInfo contact,
            List<BountySubmissionHistoryItem> history
    ) {
    }

    public record ContactInfo(
            String hint,
            boolean emailVisible,
            String email,
            boolean phoneVisible,
            String phone,
            boolean wechatVisible,
            String wechat,
            boolean socialVisible,
            List<StudentProfileSocialLinkItem> socialLinks
    ) {
    }
}
