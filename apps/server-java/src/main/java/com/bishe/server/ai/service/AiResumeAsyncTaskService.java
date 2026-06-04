package com.bishe.server.ai.service;

import com.bishe.server.ai.dto.AiAsyncTaskDetailResponse;
import com.bishe.server.ai.dto.AiAsyncTaskSubmitResponse;
import com.bishe.server.ai.dto.ResumeOptimizeRequest;
import com.bishe.server.ai.gateway.AiExecutionMode;
import com.bishe.server.ai.gateway.AiGatewayMode;
import com.bishe.server.ai.gateway.AiGatewayProperties;
import com.bishe.server.ai.gateway.AiProviderInvocation;
import com.bishe.server.ai.gateway.AiRouteResolver;
import com.bishe.server.ai.gateway.task.AiAsyncTaskService;
import com.bishe.server.common.TimePayloads;
import com.bishe.server.common.exception.ApiException;
import com.bishe.server.growth.service.GrowthCenterCacheService;
import com.bishe.server.governance.ContentGovernanceService;
import com.bishe.server.governance.ModerationDecision;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 简历优化异步任务应用服务：负责提交任务、查询任务，以及固化提交时的网关快照。
 */
@Service
public class AiResumeAsyncTaskService {

    public static final String RESUME_TASK_TYPE = "RESUME";
    public static final String RESUME_SCENE_CODE = "RESUME_OPTIMIZE";
    public static final String INPUT_MODE_TEXT = "text";
    public static final String INPUT_MODE_PDF = "pdf";

    private final AiGatewayProperties aiGatewayProperties;
    private final AiRouteResolver aiRouteResolver;
    private final AiAsyncTaskService aiAsyncTaskService;
    private final ContentGovernanceService contentGovernanceService;
    private final ObjectMapper objectMapper;
    private final GrowthCenterCacheService growthCenterCacheService;

    public AiResumeAsyncTaskService(
            AiGatewayProperties aiGatewayProperties,
            AiRouteResolver aiRouteResolver,
            AiAsyncTaskService aiAsyncTaskService,
            ContentGovernanceService contentGovernanceService,
            ObjectMapper objectMapper,
            GrowthCenterCacheService growthCenterCacheService
    ) {
        this.aiGatewayProperties = aiGatewayProperties;
        this.aiRouteResolver = aiRouteResolver;
        this.aiAsyncTaskService = aiAsyncTaskService;
        this.contentGovernanceService = contentGovernanceService;
        this.objectMapper = objectMapper;
        this.growthCenterCacheService = growthCenterCacheService;
    }

    @Transactional
    public AiAsyncTaskSubmitResponse submitResumeTextTask(long userId, String traceId, ResumeOptimizeRequest request) {
        // 文本简历先统一规范化，再进入治理审核和异步任务快照。
        ResumeAsyncTaskInput input = new ResumeAsyncTaskInput(
                INPUT_MODE_TEXT,
                normalizeTargetRole(request.targetRole()),
                normalizeTargetContext(request.targetContext()),
                normalizeJobDescription(request.jobDescription()),
                normalizeResumeText(request.resumeText()),
                null,
                null,
                null
        );
        ensureAiInputAllowed(traceId, userId, buildResumeInputText(input));
        AiProviderInvocation invocation = resolveInvocation(input);
        // 提交时固化 input/context/prompt/route，worker 后续执行不依赖页面当前状态。
        AiAsyncTaskService.TaskTicket ticket = aiAsyncTaskService.submitTask(new AiAsyncTaskService.SubmitTaskCommand(
                userId,
                RESUME_TASK_TYPE,
                RESUME_SCENE_CODE,
                invocation == null ? null : invocation.routeCode(),
                AiExecutionMode.ASYNC_JOB.name(),
                invocation == null ? null : invocation.providerCode(),
                invocation == null || invocation.providerType() == null ? null : invocation.providerType().name(),
                invocation == null ? null : invocation.model(),
                invocation == null ? null : invocation.promptTemplateName(),
                invocation == null ? null : invocation.promptTemplateVersionNo(),
                writeJson(input),
                writeJson(buildContextSnapshot(input)),
                writeJson(buildPromptSnapshot(invocation)),
                writeJson(buildRouteSnapshot(invocation)),
                3
        ));
        evictGrowthDailyTasks(userId);
        return new AiAsyncTaskSubmitResponse(
                ticket.taskId(),
                RESUME_TASK_TYPE,
                RESUME_SCENE_CODE,
                AiExecutionMode.ASYNC_JOB.name(),
                ticket.status(),
                ticket.createdAt()
        );
    }

    @Transactional
    public AiAsyncTaskSubmitResponse submitResumePdfTask(
            long userId,
            String traceId,
            String targetRole,
            String targetContext,
            String jobDescription,
            MultipartFile resumeFile
    ) {
        validateResumePdfFile(resumeFile);
        // PDF 异步链只校验文件形态并保存 bytes，真正的多模态能力由路由 resolver 约束。
        ResumeAsyncTaskInput input = new ResumeAsyncTaskInput(
                INPUT_MODE_PDF,
                normalizeTargetRole(targetRole),
                normalizeTargetContext(targetContext),
                normalizeJobDescription(jobDescription),
                null,
                normalizeFileName(resumeFile),
                normalizeContentType(resumeFile),
                readFileBytes(resumeFile)
        );
        ensureAiInputAllowed(traceId, userId, buildResumePdfInputText(input));
        AiProviderInvocation invocation = resolveInvocation(input);
        AiAsyncTaskService.TaskTicket ticket = aiAsyncTaskService.submitTask(new AiAsyncTaskService.SubmitTaskCommand(
                userId,
                RESUME_TASK_TYPE,
                RESUME_SCENE_CODE,
                invocation == null ? null : invocation.routeCode(),
                AiExecutionMode.ASYNC_JOB.name(),
                invocation == null ? null : invocation.providerCode(),
                invocation == null || invocation.providerType() == null ? null : invocation.providerType().name(),
                invocation == null ? null : invocation.model(),
                invocation == null ? null : invocation.promptTemplateName(),
                invocation == null ? null : invocation.promptTemplateVersionNo(),
                writeJson(input),
                writeJson(buildContextSnapshot(input)),
                writeJson(buildPromptSnapshot(invocation)),
                writeJson(buildRouteSnapshot(invocation)),
                3
        ));
        evictGrowthDailyTasks(userId);
        return new AiAsyncTaskSubmitResponse(
                ticket.taskId(),
                RESUME_TASK_TYPE,
                RESUME_SCENE_CODE,
                AiExecutionMode.ASYNC_JOB.name(),
                ticket.status(),
                ticket.createdAt()
        );
    }

    @Transactional(readOnly = true)
    public AiAsyncTaskDetailResponse getTaskDetail(long userId, String taskId) {
        // 任务查询始终带用户隔离，防止通过 taskId 枚举查看别人的简历结果。
        AiAsyncTaskService.TaskSnapshot snapshot = aiAsyncTaskService.findTaskForUser(userId, taskId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "async task not found", HttpStatus.NOT_FOUND));
        JsonNode resultPayload = readJson(snapshot.resultPayloadJson());
        return new AiAsyncTaskDetailResponse(
                snapshot.taskId(),
                snapshot.taskType(),
                snapshot.sceneCode(),
                snapshot.routeCode(),
                snapshot.executionMode().name(),
                snapshot.status().name(),
                snapshot.status().isTerminal(),
                snapshot.currentAttempt(),
                snapshot.maxAttempts(),
                snapshot.providerCode(),
                snapshot.providerType(),
                snapshot.modelName(),
                snapshot.promptTemplateName(),
                snapshot.promptTemplateVersionNo(),
                snapshot.resultSummary(),
                resultPayload,
                extractLinkedRecordId(resultPayload),
                snapshot.errorCode(),
                snapshot.errorMessage(),
                TimePayloads.toEpochMillis(snapshot.nextRunAt()),
                TimePayloads.toEpochMillis(snapshot.queuedAt()),
                TimePayloads.toEpochMillis(snapshot.startedAt()),
                TimePayloads.toEpochMillis(snapshot.finishedAt()),
                TimePayloads.toEpochMillis(snapshot.createdAt()),
                TimePayloads.toEpochMillis(snapshot.updatedAt())
        );
    }

    private AiProviderInvocation resolveInvocation(ResumeAsyncTaskInput input) {
        if (aiGatewayProperties.getMode() == AiGatewayMode.MOCK) {
            // mock 模式没有真实 provider route，worker 会按 mock 分支生成结果。
            return null;
        }
        Map<String, Object> promptVariables = buildPromptVariables(input);
        if (INPUT_MODE_PDF.equals(input.inputMode())) {
            return aiRouteResolver.resolveResumePdf(RESUME_TASK_TYPE, RESUME_SCENE_CODE, null, promptVariables);
        }
        return aiRouteResolver.resolve(RESUME_TASK_TYPE, RESUME_SCENE_CODE, null, promptVariables);
    }

    private Map<String, Object> buildPromptVariables(ResumeAsyncTaskInput input) {
        LinkedHashMap<String, Object> variables = new LinkedHashMap<>();
        variables.put("targetRole", safe(input.targetRole()));
        variables.put("targetContext", safe(input.targetContext()));
        variables.put("jobDescription", safe(input.jobDescription()));
        variables.put("inputMode", safe(input.inputMode()));
        if (INPUT_MODE_PDF.equals(input.inputMode())) {
            variables.put("resumeFileName", safe(input.resumeFileName()));
            variables.put("resumeMimeType", safe(input.resumeContentType()));
        } else {
            variables.put("resumeText", safe(input.resumeText()));
        }
        return variables;
    }

    private void evictGrowthDailyTasks(long userId) {
        growthCenterCacheService.evictDailyTasksNow(userId);
        growthCenterCacheService.evictDailyTasksAfterCommit(userId);
    }

    private Map<String, Object> buildContextSnapshot(ResumeAsyncTaskInput input) {
        // context snapshot 面向任务详情和排障展示，避免直接展开完整简历正文。
        LinkedHashMap<String, Object> context = new LinkedHashMap<>();
        context.put("taskType", RESUME_TASK_TYPE);
        context.put("sceneCode", RESUME_SCENE_CODE);
        context.put("inputMode", input.inputMode());
        context.put("targetRole", input.targetRole());
        context.put("targetContext", input.targetContext());
        context.put("jobDescription", input.jobDescription());
        if (INPUT_MODE_PDF.equals(input.inputMode())) {
            context.put("resumeFileName", input.resumeFileName());
            context.put("resumeContentType", input.resumeContentType());
        }
        return context;
    }

    private Map<String, Object> buildPromptSnapshot(AiProviderInvocation invocation) {
        if (invocation == null) {
            return null;
        }
        // prompt snapshot 保留模板渲染结果，便于后台追踪一次任务到底喂给了模型什么。
        LinkedHashMap<String, Object> prompt = new LinkedHashMap<>();
        prompt.put("systemPrompt", invocation.systemPrompt());
        prompt.put("promptTemplateName", invocation.promptTemplateName());
        prompt.put("promptTemplateVersionNo", invocation.promptTemplateVersionNo());
        prompt.put("promptTemplateFormat", invocation.promptTemplateFormat());
        prompt.put("promptTemplateBundleJson", invocation.promptTemplateBundleJson());
        prompt.put("promptSeedMessages", invocation.promptSeedMessages());
        return prompt;
    }

    private Map<String, Object> buildRouteSnapshot(AiProviderInvocation invocation) {
        if (invocation == null) {
            return null;
        }
        LinkedHashMap<String, Object> route = new LinkedHashMap<>();
        route.put("taskType", invocation.taskType());
        route.put("sceneCode", invocation.sceneCode());
        route.put("routeCode", invocation.routeCode());
        route.put("resolvedExecutionMode", invocation.executionMode().name());
        route.put("submittedExecutionMode", AiExecutionMode.ASYNC_JOB.name());
        route.put("providerCode", invocation.providerCode());
        route.put("providerDisplayName", invocation.providerDisplayName());
        route.put("providerType", invocation.providerType() == null ? null : invocation.providerType().name());
        route.put("baseUrl", invocation.baseUrl());
        route.put("model", invocation.model());
        route.put("timeoutMs", invocation.timeout() == null ? null : invocation.timeout().toMillis());
        route.put("maxRetries", invocation.maxRetries());
        route.put("temperature", invocation.temperature());
        route.put("providerExtraConfigJson", invocation.providerExtraConfigJson());
        route.put("routeExtraConfigJson", invocation.routeExtraConfigJson());
        return route;
    }

    private void ensureAiInputAllowed(String traceId, long userId, String inputText) {
        // 异步任务入队前先过 AI_INPUT 治理，违规内容不进入队列和模型调用。
        ModerationDecision moderationDecision = contentGovernanceService.moderateAiInput(traceId, userId, inputText);
        if (moderationDecision.isBlock()) {
            throw new ApiException(
                    "MOD-1001",
                    "input blocked by moderation policy",
                    HttpStatus.BAD_REQUEST,
                    Map.of("moderation", moderationDecision.toAiPayload()),
                    traceId
            );
        }
    }

    private JsonNode readJson(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(rawJson);
        } catch (IOException ex) {
            throw new ApiException("AI-2001", "async task payload invalid", HttpStatus.INTERNAL_SERVER_ERROR, null, null, ex);
        }
    }

    private Long extractLinkedRecordId(JsonNode resultPayload) {
        // worker 成功后会把历史记录 id 写进 resultPayload，前端据此打开复盘中心。
        if (resultPayload == null || !resultPayload.hasNonNull("recordId")) {
            return null;
        }
        long recordId = resultPayload.path("recordId").asLong(0L);
        return recordId > 0 ? recordId : null;
    }

    private byte[] readFileBytes(MultipartFile file) {
        try {
            return file == null ? new byte[0] : file.getBytes();
        } catch (IOException ex) {
            throw new ApiException("BIZ-1001", "resume file unreadable", HttpStatus.BAD_REQUEST, null, null, ex);
        }
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new ApiException("AI-2001", "async task snapshot invalid", HttpStatus.INTERNAL_SERVER_ERROR, null, null, ex);
        }
    }

    private void validateResumePdfFile(MultipartFile resumeFile) {
        if (resumeFile == null || resumeFile.isEmpty()) {
            throw new ApiException("BIZ-1001", "resume pdf required", HttpStatus.BAD_REQUEST);
        }
        // 当前只接受 PDF，避免 worker 端再面对不可解析的任意附件格式。
        String filename = resumeFile.getOriginalFilename();
        String contentType = resumeFile.getContentType();
        boolean pdfFilename = filename != null && filename.toLowerCase().endsWith(".pdf");
        boolean pdfContentType = contentType != null && contentType.toLowerCase().contains("pdf");
        if (!pdfFilename && !pdfContentType) {
            throw new ApiException("BIZ-1001", "resume file must be pdf", HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizeTargetRole(String targetRole) {
        if (targetRole == null || targetRole.isBlank()) {
            throw new ApiException("BIZ-1001", "targetRole must not be blank", HttpStatus.BAD_REQUEST);
        }
        String normalized = targetRole.trim();
        if (normalized.length() > 100) {
            throw new ApiException("BIZ-1001", "targetRole too long", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeTargetContext(String targetContext) {
        if (targetContext == null || targetContext.isBlank()) {
            return "";
        }
        String normalized = targetContext.trim();
        if (normalized.length() > 80) {
            throw new ApiException("BIZ-1001", "targetContext too long", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeJobDescription(String jobDescription) {
        if (jobDescription == null || jobDescription.isBlank()) {
            return "";
        }
        String normalized = jobDescription.trim();
        if (normalized.length() > 4000) {
            throw new ApiException("BIZ-1001", "jobDescription too long", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeResumeText(String resumeText) {
        if (resumeText == null || resumeText.isBlank()) {
            throw new ApiException("BIZ-1001", "resumeText must not be blank", HttpStatus.BAD_REQUEST);
        }
        String normalized = resumeText.trim();
        if (normalized.length() > 6000) {
            throw new ApiException("BIZ-1001", "resumeText too long", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeFileName(MultipartFile file) {
        return file == null || file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
    }

    private String normalizeContentType(MultipartFile file) {
        return file == null || file.getContentType() == null ? MediaType.APPLICATION_PDF_VALUE : file.getContentType().trim();
    }

    private String buildResumeInputText(ResumeAsyncTaskInput input) {
        return String.join(
                "\n",
                "targetRole=" + input.targetRole(),
                "targetContext=" + input.targetContext(),
                "jobDescription=\n" + input.jobDescription(),
                "resumeText=\n" + safe(input.resumeText())
        );
    }

    private String buildResumePdfInputText(ResumeAsyncTaskInput input) {
        return String.join(
                "\n",
                "targetRole=" + input.targetRole(),
                "targetContext=" + input.targetContext(),
                "jobDescription=\n" + input.jobDescription(),
                "resumeFileName=" + safe(input.resumeFileName()),
                "resumeMimeType=" + safe(input.resumeContentType()),
                "resumeBytes=" + (input.resumeFileBytes() == null ? 0 : input.resumeFileBytes().length)
        );
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public record ResumeAsyncTaskInput(
            String inputMode,
            String targetRole,
            String targetContext,
            String jobDescription,
            String resumeText,
            String resumeFileName,
            String resumeContentType,
            byte[] resumeFileBytes
    ) {
    }
}
