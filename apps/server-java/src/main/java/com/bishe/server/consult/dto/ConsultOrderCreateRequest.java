package com.bishe.server.consult.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建咨询订单请求。
 */
public record ConsultOrderCreateRequest(
        @NotNull(message = "mentorUserId is required")
        Long mentorUserId,
        @Size(max = 60, message = "sceneCode too long")
        String sceneCode,
        @Size(max = 80, message = "sourcePage too long")
        String sourcePage,
        @NotBlank(message = "questionText is required")
        @Size(max = 2000, message = "questionText too long")
        String questionText,
        @Valid
        QuestionPayload questionPayload,
        @Size(max = 400, message = "problemSummary too long")
        String problemSummary,
        @Size(max = 8, message = "coreQuestions too many")
        List<@Size(max = 160, message = "coreQuestion too long") String> coreQuestions,
        @Size(max = 8, message = "expectedOutcomes too many")
        List<@Size(max = 80, message = "expectedOutcome too long") String> expectedOutcomes,
        @Size(max = 8, message = "selectedMaterialTypes too many")
        List<@Size(max = 40, message = "selectedMaterialType too long") String> selectedMaterialTypes,
        @Valid
        PrepSheetSnapshot prepSheetSnapshot,
        @Min(value = 1, message = "mentorPackageId invalid")
        Long mentorPackageId,
        @Min(value = 1, message = "scheduleSlotId invalid")
        Long scheduleSlotId
) {

    /**
     * 结构化问题快照。
     */
    public record QuestionPayload(
            @Size(max = 600, message = "primaryConcern too long")
            String primaryConcern,
            @Size(max = 600, message = "background too long")
            String background,
            @Size(max = 600, message = "attemptedActions too long")
            String attemptedActions,
            @Size(max = 600, message = "expectedHelp too long")
            String expectedHelp,
            @Size(max = 600, message = "additionalNotes too long")
            String additionalNotes
    ) {
    }

    /**
     * 准备单快照。
     */
    public record PrepSheetSnapshot(
            @Size(max = 30, message = "prep scene too long")
            String scene,
            @Size(max = 600, message = "summaryDraft too long")
            String summaryDraft,
            @Size(max = 8, message = "prep coreQuestions too many")
            List<@Size(max = 160, message = "prep coreQuestion too long") String> coreQuestions,
            @Size(max = 8, message = "suggestedMaterials too many")
            List<@Size(max = 120, message = "suggestedMaterial too long") String> suggestedMaterials,
            @Size(max = 8, message = "prep expectedOutcomes too many")
            List<@Size(max = 80, message = "prep expectedOutcome too long") String> expectedOutcomes
    ) {
    }
}
