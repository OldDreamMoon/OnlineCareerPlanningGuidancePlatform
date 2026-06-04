package com.bishe.server.mentor.service;

import com.bishe.server.ai.gateway.AiGatewayService;
import com.bishe.server.ai.quota.AiQuotaService;
import com.bishe.server.ai.quota.AiTaskType;
import com.bishe.server.common.TraceId;
import com.bishe.server.common.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 导师咨询准备单 AI 生成服务。
 */
@Service
public class MentorPrepSheetAiService {

    private final AiQuotaService aiQuotaService;
    private final AiGatewayService aiGatewayService;
    private final ObjectMapper objectMapper;

    public MentorPrepSheetAiService(
            AiQuotaService aiQuotaService,
            AiGatewayService aiGatewayService,
            ObjectMapper objectMapper
    ) {
        this.aiQuotaService = aiQuotaService;
        this.aiGatewayService = aiGatewayService;
        this.objectMapper = objectMapper;
    }

    public MentorPrepSheetContent generate(
            long studentUserId,
            MentorPrepSheetAiRequest request,
            MentorPrepSheetContent fallback
    ) {
        String traceId = TraceId.next();
        try {
            return aiQuotaService.executeStudentTask(
                    studentUserId,
                    traceId,
                    AiTaskType.COMMUNITY_REPLY,
                    "MENTOR_PREP_SHEET_GENERATE",
                    context -> {
                AiGatewayService.MentorPrepSheetGatewayResult gatewayResult = aiGatewayService.generateMentorPrepSheet(
                        request.scene(),
                        request.targetPosition(),
                        request.mentorContext(),
                        request.studentContext(),
                        request.latestResumeContext(),
                        request.latestInterviewSummaryContext(),
                        request.signalTags(),
                        fallback.summaryDraft(),
                        fallback.coreQuestions(),
                        fallback.suggestedMaterials(),
                        fallback.expectedOutcomes(),
                        context.modelPreference(),
                        context.tier()
                );
                MentorPrepSheetContent response = new MentorPrepSheetContent(
                        gatewayResult.summaryDraft(),
                        gatewayResult.coreQuestions(),
                        gatewayResult.suggestedMaterials(),
                        gatewayResult.expectedOutcomes()
                );
                return new AiQuotaService.AiTaskExecution<>(
                        response,
                        gatewayResult.gatewayResult(),
                        buildResultSummary(request.scene(), response.summaryDraft()),
                        buildResultPayloadJson(request, response)
                );
                    }
            );
        } catch (ApiException ex) {
            if (shouldFallbackToHeuristic(ex)) {
                return fallback;
            }
            throw ex;
        }
    }

    private boolean shouldFallbackToHeuristic(ApiException ex) {
        if (ex == null) {
            return false;
        }
        String code = ex.getCode();
        HttpStatus status = ex.getHttpStatus();
        return code != null
                && code.startsWith("AI-")
                && status != null
                && status.is5xxServerError();
    }

    private String buildResultSummary(String scene, String summaryDraft) {
        String normalizedScene = safe(scene);
        String normalizedSummary = safe(summaryDraft).replace('\n', ' ').trim();
        if (normalizedSummary.length() > 80) {
            normalizedSummary = normalizedSummary.substring(0, 80) + "...";
        }
        return normalizedScene.isBlank() ? normalizedSummary : normalizedScene + " - " + normalizedSummary;
    }

    private String buildResultPayloadJson(MentorPrepSheetAiRequest request, MentorPrepSheetContent response) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("scene", request.scene());
        payload.put("targetPosition", request.targetPosition());
        payload.put("signalTags", request.signalTags());
        payload.put("summaryDraft", response.summaryDraft());
        payload.put("coreQuestions", response.coreQuestions());
        payload.put("suggestedMaterials", response.suggestedMaterials());
        payload.put("expectedOutcomes", response.expectedOutcomes());
        payload.put("mentorContext", request.mentorContext());
        payload.put("studentContext", request.studentContext());
        payload.put("latestResumeContext", request.latestResumeContext());
        payload.put("latestInterviewSummaryContext", request.latestInterviewSummaryContext());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public record MentorPrepSheetAiRequest(
            String scene,
            String targetPosition,
            List<String> signalTags,
            String mentorContext,
            String studentContext,
            String latestResumeContext,
            String latestInterviewSummaryContext
    ) {
        public MentorPrepSheetAiRequest {
            signalTags = signalTags == null ? List.of() : List.copyOf(new ArrayList<>(signalTags));
        }
    }

    public record MentorPrepSheetContent(
            String summaryDraft,
            List<String> coreQuestions,
            List<String> suggestedMaterials,
            List<String> expectedOutcomes
    ) {
        public MentorPrepSheetContent {
            coreQuestions = coreQuestions == null ? List.of() : List.copyOf(new ArrayList<>(coreQuestions));
            suggestedMaterials = suggestedMaterials == null ? List.of() : List.copyOf(new ArrayList<>(suggestedMaterials));
            expectedOutcomes = expectedOutcomes == null ? List.of() : List.copyOf(new ArrayList<>(expectedOutcomes));
        }
    }
}
