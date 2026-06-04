package com.bishe.server.api;

import com.bishe.server.ai.dto.AiAsyncTaskDetailResponse;
import com.bishe.server.ai.dto.AiAsyncTaskSubmitResponse;
import com.bishe.server.ai.dto.AiHistoryResponse;
import com.bishe.server.ai.dto.AiMetaPayload;
import com.bishe.server.ai.dto.AiPingRequest;
import com.bishe.server.ai.dto.AiPingResponse;
import com.bishe.server.ai.dto.AiQuotaRemainingResponse;
import com.bishe.server.ai.dto.CommunityPreAnswerRequest;
import com.bishe.server.ai.dto.CommunityPreAnswerResponse;
import com.bishe.server.ai.dto.IcebreakMessageRequest;
import com.bishe.server.ai.dto.IcebreakMessageResponse;
import com.bishe.server.ai.dto.InterviewReplyRequest;
import com.bishe.server.ai.dto.InterviewReplyResponse;
import com.bishe.server.ai.dto.InterviewAnswerHelperResponse;
import com.bishe.server.ai.dto.InterviewEntryOptionsResponse;
import com.bishe.server.ai.dto.InterviewLiveTranscriptImportRequest;
import com.bishe.server.ai.dto.InterviewSessionCreateRequest;
import com.bishe.server.ai.dto.InterviewSessionCreateResponse;
import com.bishe.server.ai.dto.InterviewSessionDetailResponse;
import com.bishe.server.ai.dto.InterviewSummaryResponse;
import com.bishe.server.ai.dto.InterviewVoiceRoundtripResponse;
import com.bishe.server.ai.dto.ResumeHistoryDetailResponse;
import com.bishe.server.ai.dto.ResumeHistoryPreviewResponse;
import com.bishe.server.ai.dto.ResumeOptimizeRequest;
import com.bishe.server.ai.dto.ResumeOptimizeResponse;
import com.bishe.server.ai.dto.TextToSpeechRequest;
import com.bishe.server.ai.dto.TextToSpeechResponse;
import com.bishe.server.ai.quota.AiQuotaService;
import com.bishe.server.ai.service.AiHistoryService;
import com.bishe.server.ai.service.AiPracticeService;
import com.bishe.server.ai.service.AiResumeAsyncTaskService;
import com.bishe.server.common.ApiResponse;
import com.bishe.server.common.TraceId;
import com.bishe.server.common.exception.ApiException;
import com.bishe.server.featureflag.FeatureFlagService;
import com.bishe.server.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * AI 最小闭环接口：联调、简历优化、文本面试与配额查询。
 */
@Tag(name = "AI", description = "AI 联调、配额、简历优化与文本面试接口")
@RestController
@RequestMapping(path = "/api/v1/ai", produces = MediaType.APPLICATION_JSON_VALUE)
public class AiController {

    private final AiQuotaService aiQuotaService;
    private final AiPracticeService aiPracticeService;
    private final AiHistoryService aiHistoryService;
    private final AiResumeAsyncTaskService aiResumeAsyncTaskService;
    private final FeatureFlagService featureFlagService;

    public AiController(
            AiQuotaService aiQuotaService,
            AiPracticeService aiPracticeService,
            AiHistoryService aiHistoryService,
            AiResumeAsyncTaskService aiResumeAsyncTaskService,
            FeatureFlagService featureFlagService
    ) {
        this.aiQuotaService = aiQuotaService;
        this.aiPracticeService = aiPracticeService;
        this.aiHistoryService = aiHistoryService;
        this.aiResumeAsyncTaskService = aiResumeAsyncTaskService;
        this.featureFlagService = featureFlagService;
    }

    @Operation(summary = "AI 联调调用")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/ping", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AiPingResponse> ping(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody(required = false) AiPingRequest request
    ) {
        AiPingRequest safeRequest = request == null ? new AiPingRequest(null, null) : request;
        String traceId = TraceId.next();
        AiPingResponse data = aiQuotaService.ping(principal.getUserId(), traceId, safeRequest);
        return ApiResponse.ok(data, traceId);
    }

    @Operation(summary = "简历优化")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/resume/optimize", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<ResumeOptimizeResponse> optimizeResume(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ResumeOptimizeRequest request
    ) {
        String traceId = TraceId.next();
        ResumeOptimizeResponse data = aiPracticeService.optimizeResume(principal.getUserId(), traceId, request);
        return ApiResponse.ok(data, traceId);
    }

    @Operation(summary = "提交简历优化异步任务")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/resume/tasks", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<AiAsyncTaskSubmitResponse>> submitResumeOptimizeTask(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ResumeOptimizeRequest request
    ) {
        String traceId = TraceId.next();
        // 文本简历正式主链走异步任务，提交时固化输入、上下文、prompt 和路由快照。
        AiAsyncTaskSubmitResponse data = aiResumeAsyncTaskService.submitResumeTextTask(principal.getUserId(), traceId, request);
        return ResponseEntity.accepted().body(ApiResponse.ok("accepted", data, traceId));
    }

    @Operation(summary = "简历优化（SSE 分段输出）")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/resume/optimize/stream", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter optimizeResumeStream(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ResumeOptimizeRequest request
    ) {
        String traceId = TraceId.next();
        SseEmitter emitter = new SseEmitter(0L);
        emitter.onTimeout(emitter::complete);
        emitter.onError(throwable -> emitter.complete());

        // 兼容旧流式入口，主流程已经切到异步任务，SSE 仍保留演示和降级能力。
        CompletableFuture.runAsync(() -> {
            try {
                sendStreamEvent(emitter, "start", buildMessagePayload(traceId, "已接收请求，正在准备简历优化。"));
                ResumeOptimizeResponse data = aiPracticeService.optimizeResume(principal.getUserId(), traceId, request);
                sendStreamEvent(emitter, "summary", buildSummaryPayload(traceId, data.summary()));
                sendStreamEvent(emitter, "strengths", buildListPayload(traceId, data.strengths()));
                sendStreamEvent(emitter, "risks", buildListPayload(traceId, data.risks()));
                sendStreamEvent(emitter, "suggestions", buildListPayload(traceId, data.suggestions()));
                sendStreamEvent(emitter, "done", buildDonePayload(traceId, data));
                emitter.complete();
            } catch (Throwable ex) {
                try {
                    sendStreamEvent(emitter, "error", buildErrorPayload(traceId, ex));
                    emitter.complete();
                } catch (RuntimeException sendFailure) {
                    emitter.completeWithError(ex);
                }
            }
        });
        return emitter;
    }

    @Operation(summary = "PDF 简历优化")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/resume/optimize/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ResumeOptimizeResponse> optimizeResumePdf(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestPart("targetRole") String targetRole,
            @RequestPart(value = "targetContext", required = false) String targetContext,
            @RequestPart(value = "jobDescription", required = false) String jobDescription,
            @RequestPart("resumeFile") MultipartFile resumeFile
    ) {
        String traceId = TraceId.next();
        ResumeOptimizeResponse data = aiPracticeService.optimizeResumePdf(
                principal.getUserId(),
                traceId,
                targetRole,
                targetContext,
                jobDescription,
                resumeFile
        );
        return ApiResponse.ok(data, traceId);
    }

    @Operation(summary = "提交 PDF 简历优化异步任务")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/resume/tasks", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AiAsyncTaskSubmitResponse>> submitResumeOptimizePdfTask(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestPart("targetRole") String targetRole,
            @RequestPart(value = "targetContext", required = false) String targetContext,
            @RequestPart(value = "jobDescription", required = false) String jobDescription,
            @RequestPart("resumeFile") MultipartFile resumeFile
    ) {
        String traceId = TraceId.next();
        // PDF 异步任务只接受上传文件入口，路由层会选择支持 PDF 的模型方案。
        AiAsyncTaskSubmitResponse data = aiResumeAsyncTaskService.submitResumePdfTask(
                principal.getUserId(),
                traceId,
                targetRole,
                targetContext,
                jobDescription,
                resumeFile
        );
        return ResponseEntity.accepted().body(ApiResponse.ok("accepted", data, traceId));
    }

    @Operation(summary = "社区 AI 预回答")
    @PreAuthorize("hasAnyRole('STUDENT', 'MENTOR')")
    @PostMapping(path = "/community/pre-answer", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<CommunityPreAnswerResponse> generateCommunityPreAnswer(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CommunityPreAnswerRequest request
    ) {
        String traceId = TraceId.next();
        // 社区预回答只返回草稿，是否公开仍由用户发评论入口和治理链决定。
        CommunityPreAnswerResponse data = aiPracticeService.generateCommunityPreAnswer(principal.getUserId(), traceId, request);
        return ApiResponse.ok("generated", data, traceId);
    }

    @Operation(summary = "导师破冰私信生成")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/icebreak-message", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<IcebreakMessageResponse> generateIcebreakMessage(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody IcebreakMessageRequest request
    ) {
        String traceId = TraceId.next();
        IcebreakMessageResponse data = aiPracticeService.generateIcebreakMessage(principal.getUserId(), traceId, request);
        return ApiResponse.ok("generated", data, traceId);
    }

    @Operation(summary = "查询模拟面试入口可用状态")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping(path = "/interview/entry-options")
    public ApiResponse<InterviewEntryOptionsResponse> getInterviewEntryOptions() {
        boolean voiceInterviewEnabled = featureFlagService.isVoiceInterviewEnabled();
        return ApiResponse.ok(
                new InterviewEntryOptionsResponse(
                        voiceInterviewEnabled,
                        voiceInterviewEnabled
                ),
                TraceId.next()
        );
    }

    @Operation(summary = "创建文本面试会话")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/interview/sessions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<InterviewSessionCreateResponse> createInterviewSession(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody InterviewSessionCreateRequest request
    ) {
        String traceId = TraceId.next();
        // 创建会话时写入准备材料、首问和配额占位，后续追问复用同一 sessionId。
        InterviewSessionCreateResponse data = aiPracticeService.createInterviewSession(principal.getUserId(), traceId, request);
        return ApiResponse.ok("session created", data, traceId);
    }

    @Operation(summary = "查询文本面试会话详情")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping(path = "/interview/sessions/{sessionId}")
    public ApiResponse<InterviewSessionDetailResponse> getInterviewSessionDetail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String sessionId
    ) {
        return ApiResponse.ok(aiHistoryService.getInterviewDetail(principal.getUserId(), sessionId), TraceId.next());
    }

    @Operation(summary = "删除文本面试历史")
    @PreAuthorize("hasRole('STUDENT')")
    @DeleteMapping(path = "/interview/sessions/{sessionId}")
    public ApiResponse<Void> deleteInterviewSession(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String sessionId
    ) {
        // 删除历史前先清理 TTS 临时对象，避免详情记录删掉后缓存残留。
        aiPracticeService.clearInterviewTtsCache(principal.getUserId(), sessionId);
        aiHistoryService.deleteInterviewHistory(principal.getUserId(), sessionId);
        return ApiResponse.ok("deleted", null, TraceId.next());
    }

    @Operation(summary = "清理面试会话 TTS 临时缓存")
    @PreAuthorize("hasRole('STUDENT')")
    @DeleteMapping(path = "/interview/sessions/{sessionId}/tts-cache")
    public ApiResponse<Void> clearInterviewSessionTtsCache(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String sessionId
    ) {
        aiPracticeService.clearInterviewTtsCache(principal.getUserId(), sessionId);
        return ApiResponse.ok("tts cache cleared", null, TraceId.next());
    }

    @Operation(summary = "文本面试回复")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/interview/sessions/{sessionId}/reply", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<InterviewReplyResponse> replyInterview(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String sessionId,
            @Valid @RequestBody InterviewReplyRequest request
    ) {
        String traceId = TraceId.next();
        // 普通文本追问会追加消息、必要时生成总结，并低优先触发画像刷新。
        InterviewReplyResponse data = aiPracticeService.replyInterview(principal.getUserId(), traceId, sessionId, request);
        return ApiResponse.ok(data, traceId);
    }

    @Operation(summary = "文本面试回答辅助诊断")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/interview/sessions/{sessionId}/answer-helper")
    public ApiResponse<InterviewAnswerHelperResponse> analyzeInterviewAnswerHelper(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String sessionId
    ) {
        String traceId = TraceId.next();
        InterviewAnswerHelperResponse data = aiPracticeService.analyzeInterviewAnswerHelper(
                principal.getUserId(),
                traceId,
                sessionId
        );
        return ApiResponse.ok("answer helper analyzed", data, traceId);
    }

    @Operation(summary = "导入 Live 实时面试 transcript")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/interview/sessions/{sessionId}/live-transcript", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Void> importLiveInterviewTranscript(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String sessionId,
            @Valid @RequestBody InterviewLiveTranscriptImportRequest request
    ) {
        String traceId = TraceId.next();
        aiPracticeService.importLiveInterviewTranscript(principal.getUserId(), sessionId, request);
        return ApiResponse.ok("live transcript imported", null, traceId);
    }

    @Operation(summary = "文本面试回复（真流式）")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/interview/sessions/{sessionId}/reply/stream", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter replyInterviewStream(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String sessionId,
            @Valid @RequestBody InterviewReplyRequest request
    ) {
        String traceId = TraceId.next();
        SseEmitter emitter = new SseEmitter(0L);
        emitter.onTimeout(emitter::complete);
        emitter.onError(throwable -> emitter.complete());

        // 流式追问只把增量文本发给前端，最终 done 仍携带完整业务响应。
        CompletableFuture.runAsync(() -> {
            try {
                sendStreamEvent(emitter, "start", buildMessagePayload(traceId, "已接收回答，AI 正在流式生成追问。"));
                InterviewReplyResponse data = aiPracticeService.streamInterviewReply(
                        principal.getUserId(),
                        traceId,
                        sessionId,
                        request,
                        delta -> sendStreamEvent(emitter, "reply_delta", buildReplyDeltaPayload(traceId, delta))
                );
                sendStreamEvent(emitter, "done", buildDonePayload(traceId, data));
                emitter.complete();
            } catch (Throwable ex) {
                try {
                    sendStreamEvent(emitter, "error", buildErrorPayload(traceId, ex));
                    emitter.complete();
                } catch (RuntimeException sendFailure) {
                    emitter.completeWithError(ex);
                }
            }
        });
        return emitter;
    }

    @Operation(summary = "语音面试往返")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/interview/sessions/{sessionId}/voice-roundtrip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<InterviewVoiceRoundtripResponse> voiceRoundtripInterview(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String sessionId,
            @RequestPart("audioFile") MultipartFile audioFile
    ) {
        String traceId = TraceId.next();
        InterviewVoiceRoundtripResponse data = aiPracticeService.voiceRoundtripInterview(principal.getUserId(), traceId, sessionId, audioFile);
        return ApiResponse.ok(data, traceId);
    }

    @Operation(summary = "语音面试往返（真流式）")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/interview/sessions/{sessionId}/voice-roundtrip/stream", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter voiceRoundtripInterviewStream(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String sessionId,
            @RequestPart("audioFile") MultipartFile audioFile
    ) {
        String traceId = TraceId.next();
        SseEmitter emitter = new SseEmitter(0L);
        emitter.onTimeout(emitter::complete);
        emitter.onError(throwable -> emitter.complete());

        // 语音流先发 transcript，再发 reply_delta，前端按事件顺序回填字幕和追问。
        CompletableFuture.runAsync(() -> {
            try {
                sendStreamEvent(emitter, "start", buildMessagePayload(traceId, "已接收语音，正在转写并流式生成追问。"));
                InterviewVoiceRoundtripResponse data = aiPracticeService.streamVoiceRoundtripInterview(
                        principal.getUserId(),
                        traceId,
                        sessionId,
                        audioFile,
                        transcript -> sendStreamEvent(emitter, "transcript", buildTranscriptPayload(traceId, transcript.transcript(), transcript.audioObjectKey(), transcript.transcriptMeta())),
                        delta -> sendStreamEvent(emitter, "reply_delta", buildReplyDeltaPayload(traceId, delta))
                );
                sendStreamEvent(emitter, "done", buildDonePayload(traceId, data));
                emitter.complete();
            } catch (Throwable ex) {
                try {
                    sendStreamEvent(emitter, "error", buildErrorPayload(traceId, ex));
                    emitter.complete();
                } catch (RuntimeException sendFailure) {
                    emitter.completeWithError(ex);
                }
            }
        });
        return emitter;
    }

    @Operation(summary = "文本转语音")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/tts/synthesize", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<TextToSpeechResponse> synthesizeSpeech(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody TextToSpeechRequest request
    ) {
        String traceId = TraceId.next();
        TextToSpeechResponse data = aiPracticeService.synthesizeSpeech(principal.getUserId(), traceId, request);
        return ApiResponse.ok("tts synthesized", data, traceId);
    }

    @Operation(summary = "文本面试总结")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/interview/sessions/{sessionId}/summary")
    public ApiResponse<InterviewSummaryResponse> summarizeInterview(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String sessionId
    ) {
        String traceId = TraceId.next();
        // summary 可被主动结束或轮次上限触发，重复调用时 service 会复用已有总结。
        InterviewSummaryResponse data = aiPracticeService.summarizeInterview(principal.getUserId(), traceId, sessionId);
        return ApiResponse.ok("summary generated", data, traceId);
    }

    @Operation(summary = "查询 AI 使用历史")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping(path = "/history")
    public ApiResponse<AiHistoryResponse> getHistory(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String taskType
    ) {
        // AI 复盘中心统一从 history 列表取简历和面试记录，再按类型补详情。
        return ApiResponse.ok(aiHistoryService.getHistory(principal.getUserId(), page, size, taskType), TraceId.next());
    }

    @Operation(summary = "查询 AI 异步任务详情")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping(path = "/tasks/{taskId}")
    public ApiResponse<AiAsyncTaskDetailResponse> getAsyncTaskDetail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String taskId
    ) {
        // 异步任务详情按用户隔离，通知回流会先查 taskId 再跳到 linkedRecordId。
        return ApiResponse.ok(aiResumeAsyncTaskService.getTaskDetail(principal.getUserId(), taskId), TraceId.next());
    }

    @Operation(summary = "查询简历优化历史预览")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping(path = "/history/resume/{recordId}/preview")
    public ApiResponse<ResumeHistoryPreviewResponse> getResumeHistoryPreview(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable long recordId
    ) {
        return ApiResponse.ok(aiHistoryService.getResumePreview(principal.getUserId(), recordId), TraceId.next());
    }

    @Operation(summary = "查询简历优化历史详情")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping(path = "/history/resume/{recordId}")
    public ApiResponse<ResumeHistoryDetailResponse> getResumeHistoryDetail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable long recordId
    ) {
        return ApiResponse.ok(aiHistoryService.getResumeDetail(principal.getUserId(), recordId), TraceId.next());
    }

    @Operation(summary = "删除简历优化历史")
    @PreAuthorize("hasRole('STUDENT')")
    @DeleteMapping(path = "/history/resume/{recordId}")
    public ApiResponse<Void> deleteResumeHistory(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable long recordId
    ) {
        aiHistoryService.deleteResumeHistory(principal.getUserId(), recordId);
        return ApiResponse.ok("deleted", null, TraceId.next());
    }

    @Operation(summary = "导出简历优化 PDF")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping(path = "/resume/export/{recordId}", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportResumePdf(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable long recordId
    ) {
        AiHistoryService.ExportedResumePdf exportedResumePdf = aiHistoryService.exportResumePdf(principal.getUserId(), recordId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + exportedResumePdf.fileName() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(exportedResumePdf.bytes());
    }

    @Operation(summary = "查询 AI 剩余配额")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping(path = "/quota/remaining")
    public ApiResponse<AiQuotaRemainingResponse> getQuotaRemaining(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(aiQuotaService.getRemainingQuota(principal.getUserId()), TraceId.next());
    }

    private void sendStreamEvent(SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload, MediaType.APPLICATION_JSON));
        } catch (IOException ex) {
            throw new IllegalStateException("failed to send sse event", ex);
        }
    }

    private LinkedHashMap<String, Object> buildMessagePayload(String traceId, String message) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("traceId", traceId);
        payload.put("message", message);
        return payload;
    }

    private LinkedHashMap<String, Object> buildSummaryPayload(String traceId, String summary) {
        LinkedHashMap<String, Object> payload = buildMessagePayload(traceId, "简历总结已生成。");
        payload.put("summary", summary);
        return payload;
    }

    private LinkedHashMap<String, Object> buildListPayload(String traceId, List<String> items) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("traceId", traceId);
        payload.put("items", items == null ? List.of() : items);
        return payload;
    }

    private LinkedHashMap<String, Object> buildDonePayload(String traceId, Object result) {
        LinkedHashMap<String, Object> payload = buildMessagePayload(traceId, "流式输出完成。");
        payload.put("result", result);
        return payload;
    }

    private LinkedHashMap<String, Object> buildReplyDeltaPayload(String traceId, String delta) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("traceId", traceId);
        payload.put("delta", delta == null ? "" : delta);
        return payload;
    }

    private LinkedHashMap<String, Object> buildTranscriptPayload(String traceId, String transcript, String audioObjectKey, AiMetaPayload transcriptMeta) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("traceId", traceId);
        payload.put("transcript", transcript);
        payload.put("audioObjectKey", audioObjectKey);
        payload.put("transcriptMeta", transcriptMeta);
        return payload;
    }

    private LinkedHashMap<String, Object> buildErrorPayload(String fallbackTraceId, Throwable throwable) {
        ApiException apiException = unwrapApiException(throwable);
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("traceId", apiException != null && apiException.getTraceId() != null && !apiException.getTraceId().isBlank()
                ? apiException.getTraceId()
                : fallbackTraceId);
        if (apiException != null) {
            payload.put("code", apiException.getCode());
            payload.put("message", apiException.getMessage());
            payload.put("data", apiException.getData());
            return payload;
        }
        payload.put("code", "BIZ-5000");
        payload.put("message", "服务暂时开小差了，请稍后再试。");
        return payload;
    }

    private ApiException unwrapApiException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ApiException apiException) {
                return apiException;
            }
            current = current.getCause();
        }
        return null;
    }
}
