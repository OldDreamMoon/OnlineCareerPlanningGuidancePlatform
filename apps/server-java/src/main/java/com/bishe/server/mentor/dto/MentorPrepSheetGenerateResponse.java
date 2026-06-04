package com.bishe.server.mentor.dto;

import java.util.List;

/**
 * 咨询准备单智能草稿响应。
 */
public record MentorPrepSheetGenerateResponse(
        long mentorUserId,
        String mentorDisplayName,
        String mentorCompanyName,
        String mentorJobTitle,
        String scene,
        String targetPosition,
        String summaryDraft,
        List<String> coreQuestions,
        List<String> suggestedMaterials,
        List<String> expectedOutcomes,
        List<String> signalTags
) {
}
