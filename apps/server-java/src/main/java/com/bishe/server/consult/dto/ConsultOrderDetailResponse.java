package com.bishe.server.consult.dto;

import java.util.List;

/**
 * 咨询订单详情响应。
 */
public record ConsultOrderDetailResponse(
        String orderNo,
        long studentUserId,
        String studentDisplayName,
        long mentorUserId,
        String mentorDisplayName,
        int amountFen,
        String status,
        String sceneCode,
        String sourcePage,
        String questionText,
        QuestionPayloadSnapshot questionPayload,
        String problemSummary,
        List<String> coreQuestions,
        List<String> expectedOutcomes,
        List<String> selectedMaterialTypes,
        PrepSheetSnapshot prepSheetSnapshot,
        AttachmentsSummary attachmentsSummary,
        StudentProfileSummary studentProfile,
        String paymentMode,
        Long appointmentStartAt,
        Long appointmentEndAt,
        Long createdAt,
        Long paidAt,
        Long closedAt,
        Long autoCancelAt,
        Long mentorReplyDeadlineAt,
        ReviewSummary review,
        List<ConsultAfterSalesRequestSummary> afterSalesRequests
) {

    /**
     * 评价摘要。
     */
    public record ReviewSummary(
            int rating,
            String comment,
            Long createdAt
    ) {
    }

    /**
     * 结构化问题快照。
     */
    public record QuestionPayloadSnapshot(
            String primaryConcern,
            String background,
            String attemptedActions,
            String expectedHelp,
            String additionalNotes
    ) {
    }

    /**
     * 准备单快照。
     */
    public record PrepSheetSnapshot(
            String scene,
            String summaryDraft,
            List<String> coreQuestions,
            List<String> suggestedMaterials,
            List<String> expectedOutcomes
    ) {
    }

    /**
     * 当前订单材料摘要。
     */
    public record AttachmentsSummary(
            int currentAttachmentCount,
            List<String> currentMaterialTypes,
            List<ConsultOrderAttachmentItem> records
    ) {
    }

    /**
     * 学生基础档案与画像摘要。
     */
    public record StudentProfileSummary(
            String jobStatus,
            String schoolName,
            String major,
            String grade,
            String gpa,
            String targetPosition,
            String honors,
            List<String> skillTags,
            String selfIntro,
            List<PortraitTagSummary> portraitTags,
            Long portraitUpdatedAt
    ) {
    }

    /**
     * 学生画像标签摘要。
     */
    public record PortraitTagSummary(
            String code,
            String label,
            String source
    ) {
    }
}
