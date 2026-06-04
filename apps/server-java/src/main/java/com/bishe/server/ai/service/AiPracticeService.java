package com.bishe.server.ai.service;

import com.bishe.server.ai.dto.AiMetaPayload;
import com.bishe.server.ai.dto.CommunityPreAnswerRequest;
import com.bishe.server.ai.dto.CommunityPreAnswerResponse;
import com.bishe.server.ai.dto.IcebreakMessageRequest;
import com.bishe.server.ai.dto.IcebreakMessageResponse;
import com.bishe.server.ai.dto.AiModerationPayload;
import com.bishe.server.ai.dto.InterviewAnswerHelperItemResponse;
import com.bishe.server.ai.dto.InterviewAnswerHelperResponse;
import com.bishe.server.ai.dto.InterviewLiveTranscriptImportRequest;
import com.bishe.server.ai.dto.InterviewReplyRequest;
import com.bishe.server.ai.dto.InterviewReplyResponse;
import com.bishe.server.ai.dto.InterviewSessionCreateRequest;
import com.bishe.server.ai.dto.InterviewSessionCreateResponse;
import com.bishe.server.ai.dto.InterviewSummaryResponse;
import com.bishe.server.ai.dto.InterviewVoiceRoundtripResponse;
import com.bishe.server.ai.dto.ResumeOptimizeRequest;
import com.bishe.server.ai.dto.ResumeOptimizeResponse;
import com.bishe.server.ai.dto.ResumeRewriteItem;
import com.bishe.server.ai.dto.ResumeStructureItem;
import com.bishe.server.ai.dto.TextToSpeechRequest;
import com.bishe.server.ai.dto.TextToSpeechResponse;
import com.bishe.server.ai.gateway.AiGatewayService;
import com.bishe.server.ai.history.AiHistoryRepository;
import com.bishe.server.ai.interview.AiInterviewCreateGuardService;
import com.bishe.server.ai.interview.AiInterviewProperties;
import com.bishe.server.ai.interview.AiInterviewRepository;
import com.bishe.server.ai.quota.AiQuotaService;
import com.bishe.server.ai.quota.AiTaskType;
import com.bishe.server.auth.model.UserRole;
import com.bishe.server.common.exception.ApiException;
import com.bishe.server.community.repository.CommunityRepository;
import com.bishe.server.consult.dto.ConsultMentorReplyDraftResponse;
import com.bishe.server.featureflag.FeatureFlagService;
import com.bishe.server.governance.ContentGovernanceService;
import com.bishe.server.growth.service.GrowthCenterCacheService;
import com.bishe.server.mentor.repository.MentorRepository;
import com.bishe.server.notification.model.NotificationCategory;
import com.bishe.server.notification.model.NotificationPriority;
import com.bishe.server.notification.service.NotificationService;
import com.bishe.server.notification.service.PlatformNotificationPublishService;
import com.bishe.server.profile.service.StudentPortraitRefreshService;
import com.bishe.server.governance.ModerationDecision;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * AI 简历优化与文本面试应用服务。
 */
@Service
public class AiPracticeService {

    private static final AiModerationPayload PASS_MODERATION = new AiModerationPayload("AI_OUTPUT", "LOW", "PASS", "RULE_CLEAR");
    private static final String INTERVIEW_TEXT_MODE = "INTERVIEW_TEXT";
    private static final String INTERVIEW_VOICE_MODE = "INTERVIEW_VOICE";
    private static final String INTERVIEW_ANSWER_MODE_TEXT = "TEXT";
    private static final String INTERVIEW_ANSWER_MODE_VOICE = "VOICE";
    private static final String INTERVIEW_ANSWER_MODE_LIVE = "LIVE";
    private static final String INTERVIEW_LIVE_PLACEHOLDER_PROVIDER = "GEMINI_LIVE_PROXY";
    private static final String INTERVIEW_LIVE_PLACEHOLDER_MODEL = "SESSION_RESERVED";
    private static final String INTERVIEW_LIVE_PLACEHOLDER_SCENE_CODE = "INTERVIEW_LIVE_TEST";
    private static final String INTERVIEW_LIVE_PLACEHOLDER_ROUTE_CODE = "INTERVIEW_LIVE_PLACEHOLDER";
    private static final long MAX_AUDIO_FILE_BYTES = 25L * 1024L * 1024L;
    private static final int MAX_RESUME_TEXT_LENGTH = 6000;
    private static final int MAX_INTERVIEW_RESUME_CONTEXT_LENGTH = 1800;
    private static final int MAX_INTERVIEW_SESSION_PROMPT_CONTEXT_LENGTH = 6000;
    private static final String FINISH_REASON_MANUAL = "MANUAL_SUMMARY";
    private static final String FINISH_REASON_SAFETY_LIMIT = "SAFETY_LIMIT";
    private static final String DEFAULT_FINISH_STATEMENT = "好，这一轮你的关键信息已经比较完整了，我们先到这里，接下来我为你整理本轮复盘。";
    private static final Set<String> SUPPORTED_INTERVIEW_TYPE_CODES = Set.of("PROJECT_DEEP_DIVE", "FUNDAMENTALS", "BEHAVIORAL", "PRESSURE");
    private static final Set<String> SUPPORTED_INTERVIEWER_STYLE_CODES = Set.of("COACHING", "STANDARD", "PRESSURE", "HR");
    private static final Set<String> SUPPORTED_DIFFICULTY_CODES = Set.of("EASY", "MEDIUM", "HARD");
    private static final Set<String> SUPPORTED_ANSWER_MODE_CODES = Set.of(
            INTERVIEW_ANSWER_MODE_TEXT,
            INTERVIEW_ANSWER_MODE_VOICE,
            INTERVIEW_ANSWER_MODE_LIVE
    );
    private static final List<String> DEFAULT_ANSWER_HELPER_CUE_KEYS = List.of("STAR", "METRICS", "COMPLETENESS");
    private static final Set<String> SUPPORTED_ANSWER_HELPER_CUE_KEYS = Set.copyOf(DEFAULT_ANSWER_HELPER_CUE_KEYS);
    private static final Set<String> SUPPORTED_PREP_MATERIAL_KEYS = Set.of(
            "CAMPUS_BACKGROUND",
            "JOB_STATUS",
            "ACADEMIC_RECORDS",
            "GROWTH_PORTRAIT",
            "LATEST_RESUME",
            "SELF_INTRO",
            "SKILL_TAGS",
            "TARGET_COMPANY",
            "TARGET_JD"
    );

    private final AiQuotaService aiQuotaService;
    private final AiGatewayService aiGatewayService;
    private final AiHistoryRepository aiHistoryRepository;
    private final AiInterviewRepository aiInterviewRepository;
    private final AiInterviewProperties aiInterviewProperties;
    private final AiInterviewCreateGuardService aiInterviewCreateGuardService;
    private final CommunityRepository communityRepository;
    private final MentorRepository mentorRepository;
    private final StudentPortraitRefreshService studentPortraitRefreshService;
    private final ObjectMapper objectMapper;
    private final ContentGovernanceService contentGovernanceService;
    private final FeatureFlagService featureFlagService;
    private final NotificationService notificationService;
    private final InterviewTtsCacheService interviewTtsCacheService;
    private final GrowthCenterCacheService growthCenterCacheService;

    public AiPracticeService(
            AiQuotaService aiQuotaService,
            AiGatewayService aiGatewayService,
            AiHistoryRepository aiHistoryRepository,
            AiInterviewRepository aiInterviewRepository,
            AiInterviewProperties aiInterviewProperties,
            AiInterviewCreateGuardService aiInterviewCreateGuardService,
            CommunityRepository communityRepository,
            MentorRepository mentorRepository,
            StudentPortraitRefreshService studentPortraitRefreshService,
            ObjectMapper objectMapper,
            ContentGovernanceService contentGovernanceService,
            FeatureFlagService featureFlagService,
            NotificationService notificationService,
            InterviewTtsCacheService interviewTtsCacheService,
            GrowthCenterCacheService growthCenterCacheService
    ) {
        this.aiQuotaService = aiQuotaService;
        this.aiGatewayService = aiGatewayService;
        this.aiHistoryRepository = aiHistoryRepository;
        this.aiInterviewRepository = aiInterviewRepository;
        this.aiInterviewProperties = aiInterviewProperties;
        this.aiInterviewCreateGuardService = aiInterviewCreateGuardService;
        this.communityRepository = communityRepository;
        this.mentorRepository = mentorRepository;
        this.studentPortraitRefreshService = studentPortraitRefreshService;
        this.objectMapper = objectMapper;
        this.contentGovernanceService = contentGovernanceService;
        this.featureFlagService = featureFlagService;
        this.notificationService = notificationService;
        this.interviewTtsCacheService = interviewTtsCacheService;
        this.growthCenterCacheService = growthCenterCacheService;
    }

    public ResumeOptimizeResponse optimizeResume(long userId, String traceId, ResumeOptimizeRequest request) {
        // 同步简历链路仍保留：输入治理、额度扣减、输出治理和历史落库在一次调用内完成。
        ResumeRequestContext requestContext = buildTextResumeRequestContext(request);
        ensureAiInputAllowed(traceId, userId, buildResumeInputText(requestContext));
        ModeratedPayload<ResumeOptimizeResponse> moderated = aiQuotaService.executeStudentTask(
                userId,
                traceId,
                AiTaskType.RESUME,
                context -> buildResumeOptimizeExecution(
                        traceId,
                        userId,
                        requestContext,
                        aiGatewayService.optimizeResume(
                                requestContext.targetRole(),
                                requestContext.targetContext(),
                                requestContext.jobDescription(),
                                requestContext.resumeText(),
                                context.modelPreference(),
                                context.tier()
                        )
                ),
                this::attachResumeRecordId
        );
        evictGrowthDailyTasks(userId);
        if (moderated.blocked()) {
            throw buildAiOutputBlockedException(traceId, moderated.decision());
        }
        studentPortraitRefreshService.refreshNow(userId);
        return moderated.payload();
    }

    public ResumeOptimizeResponse optimizeResumePdf(
            long userId,
            String traceId,
            String targetRole,
            String targetContext,
            String jobDescription,
            MultipartFile resumeFile
    ) {
        validateResumePdfFile(resumeFile);
        // PDF 简历和文本简历共用结果治理与画像刷新，只是网关入口换成文件能力。
        ResumeRequestContext requestContext = buildPdfResumeRequestContext(targetRole, targetContext, jobDescription, resumeFile);
        ensureAiInputAllowed(traceId, userId, buildResumePdfInputText(requestContext, resumeFile));
        ModeratedPayload<ResumeOptimizeResponse> moderated = aiQuotaService.executeStudentTask(
                userId,
                traceId,
                AiTaskType.RESUME,
                context -> buildResumeOptimizeExecution(
                        traceId,
                        userId,
                        requestContext,
                        aiGatewayService.optimizeResumePdf(
                                requestContext.targetRole(),
                                requestContext.targetContext(),
                                requestContext.jobDescription(),
                                resumeFile,
                                context.modelPreference(),
                                context.tier()
                        )
                ),
                this::attachResumeRecordId
        );
        evictGrowthDailyTasks(userId);
        if (moderated.blocked()) {
            throw buildAiOutputBlockedException(traceId, moderated.decision());
        }
        studentPortraitRefreshService.refreshNow(userId);
        return moderated.payload();
    }

    private void evictGrowthDailyTasks(long userId) {
        growthCenterCacheService.evictDailyTasksNow(userId);
        growthCenterCacheService.evictDailyTasksAfterCommit(userId);
    }

    public CommunityPreAnswerResponse generateCommunityPreAnswer(long userId, String traceId, CommunityPreAnswerRequest request) {
        featureFlagService.requireCommunityAiDraftEnabled();
        long postId = requirePositiveId(request.postId(), "postId invalid");
        CommunityRepository.PostSummaryRow postRow = communityRepository.findVisiblePostDetail(userId, postId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "post not found", HttpStatus.NOT_FOUND));
        ensureAiInputAllowed(traceId, userId, "title=" + postRow.title() + "\ncontent=" + postRow.content());

        ModeratedPayload<CommunityPreAnswerResponse> moderated = aiQuotaService.executeTask(
                userId,
                traceId,
                AiTaskType.COMMUNITY_REPLY,
                "COMMUNITY_PRE_ANSWER",
                EnumSet.of(UserRole.STUDENT, UserRole.MENTOR),
                "user not found",
                context -> {
                    AiGatewayService.CommunityPreAnswerGatewayResult gatewayResult = aiGatewayService.generateCommunityPreAnswer(
                            postRow.title(),
                            postRow.content(),
                            context.modelPreference(),
                            context.tier()
                    );
                    ModeratedPayload<String> moderatedText = moderateAiOutputText(traceId, userId, "COMMUNITY_REPLY", gatewayResult.draftComment());
                    if (moderatedText.blocked()) {
                        return new AiQuotaService.AiTaskExecution<>(
                                new ModeratedPayload<>(null, moderatedText.decision(), true),
                                gatewayResult.gatewayResult(),
                                null,
                                buildModerationPayloadJson(moderatedText.decision())
                        );
                    }
                    CommunityPreAnswerResponse response = new CommunityPreAnswerResponse(
                            moderatedText.payload(),
                            "AI_GENERATED",
                            moderatedText.decision().toAiPayload()
                    );
                    String resultPayloadJson = toJson(Map.of(
                            "postId", postRow.postId(),
                            "draftComment", response.draftComment(),
                            "tag", response.tag(),
                            "moderation", response.moderation()
                    ));
                    return new AiQuotaService.AiTaskExecution<>(
                            new ModeratedPayload<>(response, moderatedText.decision(), false),
                            gatewayResult.gatewayResult(),
                            response.draftComment(),
                            resultPayloadJson
                    );
                }
        );
        if (moderated.blocked()) {
            throw buildAiOutputBlockedException(traceId, moderated.decision());
        }
        return moderated.payload();
    }

    public IcebreakMessageResponse generateIcebreakMessage(long userId, String traceId, IcebreakMessageRequest request) {
        long mentorId = requirePositiveId(request.mentorId(), "mentorId invalid");
        MentorRepository.MentorDetailRow mentorRow = mentorRepository.findMentorDetail(mentorId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "mentor not found", HttpStatus.NOT_FOUND));
        ensureAiInputAllowed(
                traceId,
                userId,
                "mentor=" + mentorRow.displayName() + "\nexpertise=" + String.join(",", mentorRow.expertiseTags()) + "\ngoal=" + request.studentGoal()
        );

        ModeratedPayload<IcebreakMessageResponse> moderated = aiQuotaService.executeStudentTask(userId, traceId, AiTaskType.ICEBREAK, context -> {
            AiGatewayService.IcebreakMessageGatewayResult gatewayResult = aiGatewayService.generateIcebreakMessage(
                    mentorRow.displayName(),
                    mentorRow.expertiseTags(),
                    mentorRow.bio(),
                    request.studentGoal(),
                    context.modelPreference(),
                    context.tier()
            );
            ModeratedPayload<String> moderatedText = moderateAiOutputText(traceId, userId, "ICEBREAK", gatewayResult.messageDraft());
            if (moderatedText.blocked()) {
                return new AiQuotaService.AiTaskExecution<>(
                        new ModeratedPayload<>(null, moderatedText.decision(), true),
                        gatewayResult.gatewayResult(),
                        null,
                        buildModerationPayloadJson(moderatedText.decision())
                );
            }
            IcebreakMessageResponse response = new IcebreakMessageResponse(
                    moderatedText.payload(),
                    moderatedText.decision().toAiPayload()
            );
            String resultPayloadJson = toJson(Map.of(
                    "mentorId", mentorId,
                    "messageDraft", response.messageDraft(),
                    "moderation", response.moderation()
            ));
            return new AiQuotaService.AiTaskExecution<>(
                    new ModeratedPayload<>(response, moderatedText.decision(), false),
                    gatewayResult.gatewayResult(),
                    response.messageDraft(),
                    resultPayloadJson
            );
        });
        if (moderated.blocked()) {
            throw buildAiOutputBlockedException(traceId, moderated.decision());
        }
        return moderated.payload();
    }

    public ConsultMentorReplyDraftResponse generateMentorOrderReplyDraft(
            long userId,
            String traceId,
            String title,
            String context,
            String currentDraft,
            String instruction
    ) {
        String normalizedCurrentDraftText = safe(currentDraft).trim();
        String normalizedCurrentDraft = normalizedCurrentDraftText.isBlank() ? null : normalizedCurrentDraftText;
        String normalizedInstructionText = safe(instruction).trim();
        String normalizedInstruction = normalizedInstructionText.isBlank() ? null : normalizedInstructionText;
        String generationMode = normalizedCurrentDraft == null ? "GENERATE_FROM_CONTEXT" : "POLISH_EXISTING";
        ensureAiInputAllowed(
                traceId,
                userId,
                String.join(
                        "\n",
                        "task=MENTOR_REPLY_DRAFT",
                        "title=" + safe(title),
                        "context=\n" + safe(context),
                        "generationMode=" + generationMode,
                        "currentDraft=" + safe(normalizedCurrentDraft),
                        "instruction=" + safe(normalizedInstruction)
                )
        );

        ModeratedPayload<ConsultMentorReplyDraftResponse> moderated = aiQuotaService.executeTask(
                userId,
                traceId,
                AiTaskType.COMMUNITY_REPLY,
                EnumSet.of(UserRole.MENTOR),
                "mentor not found",
                executionContext -> {
                    AiGatewayService.MentorOrderReplyDraftGatewayResult gatewayResult = aiGatewayService.generateMentorOrderReplyDraft(
                            title,
                            context,
                            normalizedCurrentDraft,
                            normalizedInstruction,
                            executionContext.modelPreference(),
                            executionContext.tier()
                    );
                    ModeratedPayload<String> moderatedText = moderateAiOutputText(traceId, userId, "MENTOR_REPLY_DRAFT", gatewayResult.draftReply());
                    if (moderatedText.blocked()) {
                        return new AiQuotaService.AiTaskExecution<>(
                                new ModeratedPayload<>(null, moderatedText.decision(), true),
                                gatewayResult.gatewayResult(),
                                null,
                                buildModerationPayloadJson(moderatedText.decision())
                        );
                    }
                    ConsultMentorReplyDraftResponse response = new ConsultMentorReplyDraftResponse(
                            moderatedText.payload(),
                            generationMode,
                            normalizedInstruction,
                            buildMeta("MENTOR_REPLY_DRAFT", gatewayResult.gatewayResult()),
                            moderatedText.decision().toAiPayload()
                    );
                    String resultPayloadJson = buildMentorReplyDraftPayloadJson(title, generationMode, response);
                    return new AiQuotaService.AiTaskExecution<>(
                            new ModeratedPayload<>(response, moderatedText.decision(), false),
                            gatewayResult.gatewayResult(),
                            response.draftReply(),
                            resultPayloadJson
                    );
                }
        );
        if (moderated.blocked()) {
            throw buildAiOutputBlockedException(traceId, moderated.decision());
        }
        return moderated.payload();
    }

    public TextToSpeechResponse synthesizeSpeech(long userId, String traceId, TextToSpeechRequest request) {
        featureFlagService.requireVoiceTtsEnabled();
        String normalizedText = normalizeTextToSpeechText(request.text());
        String normalizedSessionId = normalizeOptionalSessionId(request.sessionId());
        if (!normalizedSessionId.isBlank()) {
            loadSession(userId, normalizedSessionId);
            Optional<TextToSpeechResponse> cached = interviewTtsCacheService.get(
                    normalizedSessionId,
                    normalizedText,
                    request.stylePrompt(),
                    request.voiceName()
            );
            if (cached.isPresent()) {
                return cached.get();
            }
        }
        ensureAiInputAllowed(traceId, userId, String.join(
                "\n",
                "task=TTS",
                "text=" + normalizedText,
                "stylePrompt=" + safe(request.stylePrompt()),
                "voiceName=" + safe(request.voiceName())
        ));

        return aiQuotaService.executeStudentTask(userId, traceId, AiTaskType.TTS, context -> {
            AiGatewayService.TextToSpeechGatewayResult gatewayResult;
            try {
                gatewayResult = aiGatewayService.synthesizeSpeech(
                        normalizedText,
                        request.stylePrompt(),
                        request.voiceName(),
                        context.modelPreference(),
                        context.tier()
                );
            } catch (ApiException ex) {
                if ("AI-2001".equals(ex.getCode()) || "AI-2103".equals(ex.getCode())) {
                    throw new ApiException(
                            "AI-2103",
                            "语音播报生成失败，本次将先保留文字内容。",
                            HttpStatus.BAD_GATEWAY,
                            mergeAiErrorData(ex.getData(), "fallbackMode", "TEXT"),
                            traceId
                    );
                }
                throw ex;
            }

            TextToSpeechResponse response = new TextToSpeechResponse(
                    gatewayResult.text(),
                    gatewayResult.stylePrompt(),
                    gatewayResult.voiceName(),
                    gatewayResult.mimeType(),
                    gatewayResult.sampleRate(),
                    gatewayResult.audioBase64(),
                    buildMeta("TTS", gatewayResult.gatewayResult())
            );
            if (!normalizedSessionId.isBlank()) {
                interviewTtsCacheService.put(
                        normalizedSessionId,
                        normalizedText,
                        request.stylePrompt(),
                        request.voiceName(),
                        response
                );
            }
            return new AiQuotaService.AiTaskExecution<>(
                    response,
                    gatewayResult.gatewayResult(),
                    buildTextToSpeechResultSummary(response),
                    buildTextToSpeechPayloadJson(response)
            );
        });
    }

    @Transactional
    public InterviewSessionCreateResponse createInterviewSession(long userId, String traceId, InterviewSessionCreateRequest request) {
        // 创建会话先经过去重 guard，防止用户连续点击生成多条占位面试。
        String mode = normalizeMode(request.mode());
        String targetRole = normalizeResumeTargetRole(request.targetRole());
        InterviewSessionContextSnapshot sessionContext = normalizeInterviewSessionContext(request.sessionContext());
        String answerMode = sessionContext == null ? INTERVIEW_ANSWER_MODE_TEXT : sessionContext.answerMode();
        InterviewResumeContextSnapshot resumeContext = loadInterviewResumeContext(userId, request.resumeRecordId());
        AiInterviewCreateGuardService.GuardDecision guardDecision = aiInterviewCreateGuardService.begin(
                userId,
                buildInterviewCreateGuardPayload(targetRole, mode, resumeContext, sessionContext)
        );
        if (guardDecision.shouldReturnCached()) {
            return guardDecision.cachedResponse();
        }
        if (!guardDecision.shouldProceed()) {
            throw new ApiException("AI-2006", "interview session create already in progress", HttpStatus.CONFLICT);
        }

        String interviewSessionPromptContext = buildInterviewSessionPromptContext(sessionContext);
        String interviewResumePromptContext = buildInterviewResumePromptContext(resumeContext);
        try {
            ensureVoiceFeatureEnabledForMode(mode, answerMode);
            ensureAiInputAllowed(traceId, userId, buildInterviewCreateInputText(targetRole, mode, sessionContext, resumeContext));
            AiQuotaService.AiTaskExecutionContext context = aiQuotaService.reserveInterviewSession(
                    userId,
                    traceId,
                    aiInterviewProperties.getReservedQuotaWeight()
            );
            if (INTERVIEW_ANSWER_MODE_LIVE.equals(answerMode)) {
                // Live 模式由 Python/Gemini 实时通道承担问答，Java 侧只预留配额并创建占位会话。
                aiQuotaService.recordTaskSuccess(
                        traceId,
                        userId,
                        AiTaskType.INTERVIEW_TEXT,
                        context.tier(),
                        context.chargedPoints(),
                        buildLivePlaceholderGatewayResult(),
                        context.quotaWeight(),
                        "Gemini Live 测试会话占位已创建",
                        buildLivePlaceholderPayloadJson(targetRole, sessionContext)
                );

                String sessionId = generateSessionId();
                aiInterviewRepository.createSession(
                        sessionId,
                        userId,
                        targetRole,
                        mode,
                        resumeContext == null ? null : toJson(resumeContext),
                        sessionContext == null ? null : toJson(sessionContext),
                        aiInterviewProperties.getMaxReplyRounds(),
                        context.chargedPoints(),
                        context.quotaWeight()
                );

                InterviewSessionCreateResponse response = new InterviewSessionCreateResponse(
                        sessionId,
                        mode,
                        "",
                        context.chargedPoints(),
                        context.pointsBalance(),
                        context.quotaWeight(),
                        aiInterviewProperties.getMaxReplyRounds(),
                        null
                );
                aiInterviewCreateGuardService.rememberAfterCommit(guardDecision.keySuffix(), response);
                return response;
            }

            // 普通文本/语音模式先生成首问并入库，前端进入 session 后即可恢复上下文。
            AiGatewayService.InterviewQuestionGatewayResult gatewayResult = aiGatewayService.createInterviewSession(
                    targetRole,
                    interviewSessionPromptContext,
                    interviewResumePromptContext,
                    context.modelPreference(),
                    context.tier()
            );
            ModeratedPayload<String> moderatedQuestion = moderateAiOutputText(traceId, userId, "INTERVIEW_SESSION", gatewayResult.firstQuestion());
            if (moderatedQuestion.blocked()) {
                throw buildAiOutputBlockedException(traceId, moderatedQuestion.decision());
            }
            aiQuotaService.recordTaskSuccess(
                    traceId,
                    userId,
                    AiTaskType.INTERVIEW_TEXT,
                    context.tier(),
                    context.chargedPoints(),
                    gatewayResult.gatewayResult(),
                    context.quotaWeight(),
                    moderatedQuestion.payload(),
                    toJson(Map.of(
                            "firstQuestion", moderatedQuestion.payload(),
                            "moderation", moderatedQuestion.decision().toAiPayload()
                    ))
            );

            String sessionId = generateSessionId();
            aiInterviewRepository.createSession(
                    sessionId,
                    userId,
                    targetRole,
                    mode,
                    resumeContext == null ? null : toJson(resumeContext),
                    sessionContext == null ? null : toJson(sessionContext),
                    aiInterviewProperties.getMaxReplyRounds(),
                    context.chargedPoints(),
                    context.quotaWeight()
            );
            AiInterviewRepository.InterviewSessionRow session = loadSession(userId, sessionId);
            aiInterviewRepository.insertMessage(session.id(), "ASSISTANT", moderatedQuestion.payload(), null);
            InterviewSessionCreateResponse response = new InterviewSessionCreateResponse(
                    sessionId,
                    mode,
                    moderatedQuestion.payload(),
                    context.chargedPoints(),
                    context.pointsBalance(),
                    context.quotaWeight(),
                    session.replyRoundLimit(),
                    moderatedQuestion.decision().toAiPayload()
            );
            aiInterviewCreateGuardService.rememberAfterCommit(guardDecision.keySuffix(), response);
            return response;
        } catch (RuntimeException ex) {
            aiInterviewCreateGuardService.releaseNow(guardDecision.keySuffix());
            throw ex;
        }
    }

    @Transactional
    public InterviewReplyResponse replyInterview(long userId, String traceId, String sessionId, InterviewReplyRequest request) {
        AiInterviewRepository.InterviewSessionRow session = loadSession(userId, sessionId);
        ensureReplyAllowed(session);
        // 每轮追问不再重复扣预留额度，只记录本轮模型调用和复盘侧效果。
        AiQuotaService.AiTaskExecutionContext context = aiQuotaService.getTaskRuntimeContext(userId, traceId, AiTaskType.INTERVIEW_TEXT);
        return executeInterviewReply(
                userId,
                traceId,
                session,
                context,
                normalizeAnswer(request.answerText()),
                null
        );
    }

    @Transactional
    public InterviewReplyResponse streamInterviewReply(
            long userId,
            String traceId,
            String sessionId,
            InterviewReplyRequest request,
            Consumer<String> followUpDeltaConsumer
    ) {
        AiInterviewRepository.InterviewSessionRow session = loadSession(userId, sessionId);
        ensureReplyAllowed(session);
        AiQuotaService.AiTaskExecutionContext context = aiQuotaService.getTaskRuntimeContext(userId, traceId, AiTaskType.INTERVIEW_TEXT);
        return executeInterviewReplyStream(
                userId,
                traceId,
                session,
                context,
                normalizeAnswer(request.answerText()),
                null,
                followUpDeltaConsumer
        );
    }

    @Transactional
    public InterviewVoiceRoundtripResponse voiceRoundtripInterview(long userId, String traceId, String sessionId, MultipartFile audioFile) {
        featureFlagService.requireVoiceInterviewEnabled();
        featureFlagService.requireVoiceSttEnabled();
        validateAudioFile(audioFile);
        AiInterviewRepository.InterviewSessionRow session = loadSession(userId, sessionId);
        ensureReplyAllowed(session);
        AiQuotaService.AiTaskExecutionContext context = aiQuotaService.getTaskRuntimeContext(userId, traceId, AiTaskType.INTERVIEW_TEXT);

        // 语音轮次先 STT 得到文本，再复用文本追问链路，避免维护两套面试状态机。
        VoiceTranscriptionContext transcriptionContext = transcribeInterviewVoice(traceId, session, context, audioFile);
        InterviewReplyResponse reply = executeInterviewReply(
                userId,
                traceId,
                session,
                context,
                transcriptionContext.transcript(),
                transcriptionContext.audioObjectKey()
        );

        return new InterviewVoiceRoundtripResponse(
                transcriptionContext.transcript(),
                transcriptionContext.audioObjectKey(),
                transcriptionContext.transcriptMeta(),
                reply.followUpQuestion(),
                reply.coachFeedback(),
                reply.scoreHint(),
                reply.shouldFinish(),
                reply.finishReason(),
                reply.sessionStatus(),
                reply.summary(),
                reply.aiMeta(),
                reply.moderation()
        );
    }

    @Transactional
    public InterviewVoiceRoundtripResponse streamVoiceRoundtripInterview(
            long userId,
            String traceId,
            String sessionId,
            MultipartFile audioFile,
            Consumer<VoiceStreamTranscriptProgress> transcriptConsumer,
            Consumer<String> followUpDeltaConsumer
    ) {
        featureFlagService.requireVoiceInterviewEnabled();
        featureFlagService.requireVoiceSttEnabled();
        validateAudioFile(audioFile);
        AiInterviewRepository.InterviewSessionRow session = loadSession(userId, sessionId);
        ensureReplyAllowed(session);
        AiQuotaService.AiTaskExecutionContext context = aiQuotaService.getTaskRuntimeContext(userId, traceId, AiTaskType.INTERVIEW_TEXT);

        // 流式语音先把 transcript 推给前端，再继续流式输出追问 delta。
        VoiceTranscriptionContext transcriptionContext = transcribeInterviewVoice(traceId, session, context, audioFile);
        if (transcriptConsumer != null) {
            transcriptConsumer.accept(new VoiceStreamTranscriptProgress(
                    transcriptionContext.transcript(),
                    transcriptionContext.audioObjectKey(),
                    transcriptionContext.transcriptMeta()
            ));
        }
        InterviewReplyResponse reply = executeInterviewReplyStream(
                userId,
                traceId,
                session,
                context,
                transcriptionContext.transcript(),
                transcriptionContext.audioObjectKey(),
                followUpDeltaConsumer
        );

        return new InterviewVoiceRoundtripResponse(
                transcriptionContext.transcript(),
                transcriptionContext.audioObjectKey(),
                transcriptionContext.transcriptMeta(),
                reply.followUpQuestion(),
                reply.coachFeedback(),
                reply.scoreHint(),
                reply.shouldFinish(),
                reply.finishReason(),
                reply.sessionStatus(),
                reply.summary(),
                reply.aiMeta(),
                reply.moderation()
        );
    }

    @Transactional
    public InterviewSummaryResponse summarizeInterview(long userId, String traceId, String sessionId) {
        AiInterviewRepository.InterviewSessionRow session = loadSession(userId, sessionId);
        if (session.summaryGenerated()) {
            // 已生成复盘时直接读持久化结果，避免重复扣费和重复刷新画像。
            return buildPersistedSummary(session);
        }

        List<String> historyLines = aiInterviewRepository.findMessages(session.id()).stream()
                .map(message -> message.senderRole() + ": " + message.messageText())
                .toList();
        ensureAiInputAllowed(traceId, userId, String.join("\n", historyLines));
        AiQuotaService.AiTaskExecutionContext context = aiQuotaService.getTaskRuntimeContext(userId, traceId, AiTaskType.INTERVIEW_TEXT);
        InterviewSummaryResponse response = generateAndPersistSummary(
                session,
                userId,
                traceId,
                context.modelPreference(),
                context.tier(),
                FINISH_REASON_MANUAL,
                false,
                historyLines,
                readInterviewSessionContext(session.sessionContextJson()),
                readInterviewResumeContext(session.resumeContextJson())
        );
        studentPortraitRefreshService.refreshNow(userId);
        return response;
    }

    @Transactional
    public InterviewAnswerHelperResponse analyzeInterviewAnswerHelper(long userId, String traceId, String sessionId) {
        AiInterviewRepository.InterviewSessionRow session = loadSession(userId, sessionId);
        List<AiInterviewRepository.InterviewMessageRow> messageRows = aiInterviewRepository.findMessages(session.id());
        InterviewSessionContextSnapshot sessionContext = readInterviewSessionContext(session.sessionContextJson());
        List<String> cueKeys = resolveInterviewAnswerHelperCueKeys(sessionContext);
        // 答题辅助只分析最近一条用户回答，用来给当前轮次补 STAR、量化和完整度提示。
        AiInterviewRepository.InterviewMessageRow latestUserMessage = null;
        for (int index = messageRows.size() - 1; index >= 0; index -= 1) {
            AiInterviewRepository.InterviewMessageRow messageRow = messageRows.get(index);
            if ("USER".equalsIgnoreCase(messageRow.senderRole()) && !safe(messageRow.messageText()).isBlank()) {
                latestUserMessage = messageRow;
                break;
            }
        }
        if (latestUserMessage == null) {
            return buildIdleInterviewAnswerHelperResponse(cueKeys);
        }

        InterviewResumeContextSnapshot resumeContext = readInterviewResumeContext(session.resumeContextJson());
        List<String> historyLines = messageRows.stream()
                .map(message -> message.senderRole() + ": " + message.messageText())
                .toList();
        AiQuotaService.AiTaskExecutionContext context = aiQuotaService.getTaskRuntimeContext(userId, traceId, AiTaskType.INTERVIEW_TEXT);
        AiGatewayService.InterviewAnswerHelperGatewayResult gatewayResult = aiGatewayService.analyzeInterviewAnswerHelper(
                session.targetRole(),
                buildInterviewSessionPromptContext(sessionContext),
                buildInterviewResumePromptContext(resumeContext),
                historyLines,
                latestUserMessage.messageText(),
                cueKeys,
                context.modelPreference(),
                context.tier()
        );
        ModeratedPayload<InterviewAnswerHelperResponse> moderated = moderateInterviewAnswerHelperOutput(
                traceId,
                userId,
                cueKeys,
                gatewayResult
        );
        if (moderated.blocked()) {
            throw buildAiOutputBlockedException(traceId, moderated.decision());
        }
        InterviewAnswerHelperResponse response = moderated.payload();
        aiQuotaService.recordTaskSuccess(
                traceId,
                userId,
                AiTaskType.INTERVIEW_TEXT,
                context.tier(),
                0,
                gatewayResult.gatewayResult(),
                0,
                response.overallSummary(),
                buildInterviewAnswerHelperPayloadJson(session.sessionId(), cueKeys, response)
        );
        return response;
    }

    public void clearInterviewTtsCache(long userId, String sessionId) {
        AiInterviewRepository.InterviewSessionRow session = loadSession(userId, sessionId);
        interviewTtsCacheService.evictSession(session.sessionId());
    }

    @Transactional
    public void importLiveInterviewTranscript(
            long userId,
            String sessionId,
            InterviewLiveTranscriptImportRequest request
    ) {
        AiInterviewRepository.InterviewSessionRow session = loadSession(userId, sessionId);
        if (session.summaryGenerated()) {
            throw new ApiException("BIZ-1001", "live transcript import not allowed after summary generated", HttpStatus.BAD_REQUEST);
        }

        InterviewSessionContextSnapshot sessionContext = readInterviewSessionContext(session.sessionContextJson());
        if (sessionContext == null || !INTERVIEW_ANSWER_MODE_LIVE.equals(sessionContext.answerMode())) {
            throw new ApiException("BIZ-1001", "live transcript import only supports live interview session", HttpStatus.BAD_REQUEST);
        }

        List<LiveTranscriptMessageSnapshot> importedMessages = normalizeLiveTranscriptMessages(request.messages());
        if (importedMessages.isEmpty()) {
            throw new ApiException("BIZ-1001", "live transcript messages required", HttpStatus.BAD_REQUEST);
        }

        aiInterviewRepository.deleteMessages(session.id());
        int replyRoundUsed = 0;
        for (LiveTranscriptMessageSnapshot message : importedMessages) {
            aiInterviewRepository.insertMessage(session.id(), message.role(), message.text(), null, null, null);
            if ("USER".equals(message.role())) {
                replyRoundUsed += 1;
            }
        }
        aiInterviewRepository.setReplyRoundUsed(session.id(), Math.min(replyRoundUsed, session.replyRoundLimit()));
    }

    private InterviewReplyResponse executeInterviewReply(
            long userId,
            String traceId,
            AiInterviewRepository.InterviewSessionRow session,
            AiQuotaService.AiTaskExecutionContext context,
            String currentAnswer,
            String audioObjectKey
    ) {
        ensureAiInputAllowed(traceId, userId, currentAnswer);
        // 历史消息和当前回答一起进入模型，保证追问能延续上一轮上下文。
        List<String> historyLines = loadInterviewHistoryLines(session.id(), currentAnswer);
        InterviewResumeContextSnapshot resumeContext = readInterviewResumeContext(session.resumeContextJson());
        InterviewSessionContextSnapshot sessionContext = readInterviewSessionContext(session.sessionContextJson());
        AiGatewayService.InterviewReplyGatewayResult gatewayResult = aiGatewayService.replyInterview(
                session.targetRole(),
                buildInterviewSessionPromptContext(sessionContext),
                buildInterviewResumePromptContext(resumeContext),
                historyLines,
                currentAnswer,
                session.replyRoundLimit(),
                session.replyRoundUsed(),
                context.modelPreference(),
                context.tier()
        );
        return persistInterviewReply(userId, traceId, session, context, currentAnswer, audioObjectKey, historyLines, gatewayResult);
    }

    private InterviewReplyResponse executeInterviewReplyStream(
            long userId,
            String traceId,
            AiInterviewRepository.InterviewSessionRow session,
            AiQuotaService.AiTaskExecutionContext context,
            String currentAnswer,
            String audioObjectKey,
            Consumer<String> followUpDeltaConsumer
    ) {
        ensureAiInputAllowed(traceId, userId, currentAnswer);
        List<String> historyLines = loadInterviewHistoryLines(session.id(), currentAnswer);
        InterviewResumeContextSnapshot resumeContext = readInterviewResumeContext(session.resumeContextJson());
        InterviewSessionContextSnapshot sessionContext = readInterviewSessionContext(session.sessionContextJson());
        // streamedFollowUp 保存已通过预审的可见文本，后续 delta 基于它增量裁切。
        StringBuilder streamedFollowUp = new StringBuilder();
        AiGatewayService.InterviewReplyGatewayResult gatewayResult = aiGatewayService.streamInterviewReply(
                session.targetRole(),
                buildInterviewSessionPromptContext(sessionContext),
                buildInterviewResumePromptContext(resumeContext),
                historyLines,
                currentAnswer,
                session.replyRoundLimit(),
                session.replyRoundUsed(),
                context.modelPreference(),
                delta -> handleInterviewReplyStreamDelta(traceId, userId, streamedFollowUp, delta, followUpDeltaConsumer),
                context.tier()
        );
        return persistInterviewReply(userId, traceId, session, context, currentAnswer, audioObjectKey, historyLines, gatewayResult);
    }

    private void handleInterviewReplyStreamDelta(
            String traceId,
            long userId,
            StringBuilder streamedFollowUp,
            String delta,
            Consumer<String> followUpDeltaConsumer
    ) {
        if (delta == null || delta.isBlank()) {
            return;
        }
        String candidateText = streamedFollowUp + delta;
        // 流式输出也做增量预审，命中 BLOCK 时立即中断本轮 SSE。
        ModerationDecision previewDecision = contentGovernanceService.previewAiOutput(
                traceId,
                userId,
                "INTERVIEW_REPLY",
                String.join("\n", "followUpQuestion=" + candidateText, "coachFeedback=")
        );
        if (previewDecision.isBlock()) {
            throw buildAiOutputBlockedException(traceId, previewDecision);
        }
        String safeText = candidateText;
        if ("MASK".equalsIgnoreCase(previewDecision.action()) && previewDecision.maskedText() != null && !previewDecision.maskedText().isBlank()) {
            safeText = contentGovernanceService.maskTextForSource("AI_OUTPUT", candidateText);
        }
        String emittedDelta = safeText.substring(Math.min(streamedFollowUp.length(), safeText.length()));
        streamedFollowUp.setLength(0);
        streamedFollowUp.append(safeText);
        if (followUpDeltaConsumer != null && !emittedDelta.isBlank()) {
            followUpDeltaConsumer.accept(emittedDelta);
        }
    }

    private List<String> loadInterviewHistoryLines(long sessionRecordId, String currentAnswer) {
        List<String> historyLines = new ArrayList<>(aiInterviewRepository.findMessages(sessionRecordId).stream()
                .map(message -> message.senderRole() + ": " + message.messageText())
                .toList());
        historyLines.add("USER: " + currentAnswer);
        return historyLines;
    }

    private VoiceTranscriptionContext transcribeInterviewVoice(
            String traceId,
            AiInterviewRepository.InterviewSessionRow session,
            AiQuotaService.AiTaskExecutionContext context,
            MultipartFile audioFile
    ) {
        AiGatewayService.AudioTranscriptionGatewayResult transcriptionGatewayResult;
        try {
            // STT 路由失败时转换成可被前端识别的语音降级错误。
            transcriptionGatewayResult = aiGatewayService.transcribeInterviewAnswer(
                    audioFile,
                    context.modelPreference(),
                    context.tier()
            );
        } catch (ApiException ex) {
            if ("AI-2001".equals(ex.getCode()) || "AI-2102".equals(ex.getCode())) {
                throw new ApiException(
                        "AI-2102",
                        "语音转写失败，请重新录音或改用文字模式。",
                        HttpStatus.BAD_GATEWAY,
                        mergeAiErrorData(ex.getData(), "fallbackMode", INTERVIEW_TEXT_MODE),
                        traceId
                );
            }
            throw ex;
        }
        String transcript = normalizeAnswer(transcriptionGatewayResult.transcript());
        String audioObjectKey = buildAudioObjectKey(session.sessionId(), audioFile);
        recordInterviewTranscriptionLog(traceId, session, context, transcript, transcriptionGatewayResult.gatewayResult(), audioObjectKey);
        return new VoiceTranscriptionContext(
                transcript,
                audioObjectKey,
                buildMeta("STT", transcriptionGatewayResult.gatewayResult())
        );
    }

    private void recordInterviewTranscriptionLog(
            String traceId,
            AiInterviewRepository.InterviewSessionRow session,
            AiQuotaService.AiTaskExecutionContext context,
            String transcript,
            AiGatewayService.AiGatewayResult gatewayResult,
            String audioObjectKey
    ) {
        if (gatewayResult == null || session == null || context == null) {
            return;
        }
        try {
            aiQuotaService.recordTaskSuccess(
                    traceId,
                    session.studentUserId(),
                    AiTaskType.STT,
                    context.tier(),
                    0,
                    gatewayResult,
                    0,
                    buildInterviewTranscriptionSummary(transcript),
                    toJson(Map.of(
                            "sessionId", session.sessionId(),
                            "transcript", transcript,
                            "audioObjectKey", audioObjectKey
                    ))
            );
        } catch (Exception ex) {
            // 转写成功不应因日志记录失败而中断主链路
        }
    }

    private Map<String, Object> mergeAiErrorData(Object rawData, String extraKey, Object extraValue) {
        LinkedHashMap<String, Object> merged = new LinkedHashMap<>();
        if (rawData instanceof Map<?, ?> dataMap) {
            dataMap.forEach((key, value) -> {
                if (key instanceof String textKey) {
                    merged.put(textKey, value);
                }
            });
        }
        if (extraKey != null && !extraKey.isBlank() && extraValue != null) {
            merged.put(extraKey, extraValue);
        }
        return merged;
    }

    private InterviewReplyResponse persistInterviewReply(
            long userId,
            String traceId,
            AiInterviewRepository.InterviewSessionRow session,
            AiQuotaService.AiTaskExecutionContext context,
            String currentAnswer,
            String audioObjectKey,
            List<String> historyLines,
            AiGatewayService.InterviewReplyGatewayResult gatewayResult
    ) {
        boolean safetyLimitReached = session.replyRoundUsed() + 1 >= session.replyRoundLimit();
        boolean shouldFinish = gatewayResult.shouldFinish() || safetyLimitReached;
        // 模型主动结束或达到安全轮次上限时，当前回答后直接生成复盘。
        String finishReason = shouldFinish
                ? normalizeFinishReason(gatewayResult.finishReason(), safetyLimitReached ? FINISH_REASON_SAFETY_LIMIT : "ENOUGH_EVIDENCE")
                : null;
        String assistantText = shouldFinish
                ? normalizeFinishStatement(gatewayResult.followUpQuestion())
                : gatewayResult.followUpQuestion();

        ModeratedInterviewReply moderatedReply = moderateInterviewReplyOutput(
                traceId,
                userId,
                assistantText,
                gatewayResult.coachFeedback()
        );
        if (moderatedReply.blocked()) {
            throw buildAiOutputBlockedException(traceId, moderatedReply.decision());
        }

        InterviewSummaryResponse summary = null;
        String sessionStatus = "ACTIVE";
        if (shouldFinish) {
            // 复盘生成前把本轮 assistant 结束语拼入历史，保证总结包含最后一次反馈。
            List<String> summaryHistory = new ArrayList<>(historyLines);
            summaryHistory.add("ASSISTANT: " + moderatedReply.followUpQuestion());
            summary = generateAndPersistSummary(
                    session,
                    userId,
                    traceId,
                    context.modelPreference(),
                    context.tier(),
                    finishReason,
                    true,
                    summaryHistory,
                    readInterviewSessionContext(session.sessionContextJson()),
                    readInterviewResumeContext(session.resumeContextJson())
            );
            sessionStatus = "COMPLETED";
        }

        aiInterviewRepository.insertMessage(session.id(), "USER", currentAnswer, null, audioObjectKey);
        aiInterviewRepository.insertMessage(
                session.id(),
                "ASSISTANT",
                moderatedReply.followUpQuestion(),
                moderatedReply.coachFeedback(),
                gatewayResult.scoreHint(),
                null
        );
        aiInterviewRepository.incrementReplyRoundUsed(session.id());
        if (summary != null) {
            // 终态复盘会反哺画像；未结束的普通轮次低优先刷新，减少交互阻塞。
            studentPortraitRefreshService.refreshNow(userId);
        } else {
            studentPortraitRefreshService.refreshNowLowPriority(userId);
        }
        aiQuotaService.recordTaskSuccess(
                traceId,
                userId,
                AiTaskType.INTERVIEW_TEXT,
                context.tier(),
                0,
                gatewayResult.gatewayResult(),
                0,
                moderatedReply.followUpQuestion(),
                buildInterviewReplyPayloadJson(gatewayResult, moderatedReply, shouldFinish, finishReason, sessionStatus, summary)
        );

        return new InterviewReplyResponse(
                moderatedReply.followUpQuestion(),
                moderatedReply.coachFeedback(),
                gatewayResult.scoreHint(),
                shouldFinish,
                finishReason,
                sessionStatus,
                summary,
                buildMeta("INTERVIEW_TEXT", gatewayResult.gatewayResult()),
                moderatedReply.decision().toAiPayload()
        );
    }

    private InterviewSummaryResponse generateAndPersistSummary(
            AiInterviewRepository.InterviewSessionRow session,
            long userId,
            String traceId,
            String modelPreference,
            String userTier,
            String finishReason,
            boolean endedByAi,
            List<String> historyLines,
            InterviewSessionContextSnapshot sessionContext,
            InterviewResumeContextSnapshot resumeContext
    ) {
        // summary 同时写面试会话、AI 调用日志和通知，前端报告页可直接恢复。
        AiGatewayService.InterviewSummaryGatewayResult gatewayResult = aiGatewayService.summarizeInterview(
                session.targetRole(),
                buildInterviewSessionPromptContext(sessionContext),
                buildInterviewResumePromptContext(resumeContext),
                historyLines,
                modelPreference,
                userTier
        );
        ModeratedPayload<InterviewSummaryResponse> moderatedSummary = moderateInterviewSummaryOutput(traceId, userId, gatewayResult);
        if (moderatedSummary.blocked()) {
            throw buildAiOutputBlockedException(traceId, moderatedSummary.decision());
        }
        InterviewSummaryResponse response = moderatedSummary.payload();
        aiInterviewRepository.markSummaryGenerated(
                session.id(),
                response.overallScore(),
                toJson(response.strengths()),
                toJson(response.weaknesses()),
                toJson(response.suggestions()),
                gatewayResult.gatewayResult().provider(),
                gatewayResult.gatewayResult().model(),
                gatewayResult.gatewayResult().latencyMs(),
                finishReason,
                endedByAi
        );
        aiQuotaService.recordTaskSuccess(
                traceId,
                userId,
                AiTaskType.INTERVIEW_SUMMARY,
                userTier,
                0,
                gatewayResult.gatewayResult(),
                0,
                "summary:" + response.overallScore(),
                toJson(Map.of(
                        "overallScore", response.overallScore(),
                        "strengths", response.strengths(),
                        "weaknesses", response.weaknesses(),
                        "suggestions", response.suggestions(),
                        "moderation", response.moderation()
                ))
        );
        publishInterviewSummaryNotification(session, userId, response);
        return response;
    }

    private void publishInterviewSummaryNotification(
            AiInterviewRepository.InterviewSessionRow session,
            long userId,
            InterviewSummaryResponse response
    ) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", session.sessionId());
        payload.put("resultSummary", "本次模拟面试综合得分 " + response.overallScore() + " 分，复盘报告已经生成。");
        notificationService.publish(new PlatformNotificationPublishService.NotificationPublishCommand(
                "AI_INTERVIEW_SUMMARY_READY",
                NotificationCategory.AI_TASK,
                "AI_INTERVIEW",
                session.sessionId(),
                null,
                List.of(userId),
                NotificationPriority.NORMAL,
                null,
                "你的 AI 模拟面试复盘已生成，可前往复盘中心继续查看。",
                null,
                session.sessionId(),
                "VIEW_AI_REVIEW_CENTER",
                payload,
                "ai-interview-summary:" + session.sessionId(),
                Instant.now()
        ));
    }

    private AiInterviewRepository.InterviewSessionRow loadSession(long userId, String sessionId) {
        return aiInterviewRepository.findSessionBySessionId(userId, sessionId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "interview session not found", HttpStatus.NOT_FOUND));
    }

    private void ensureReplyAllowed(AiInterviewRepository.InterviewSessionRow session) {
        if (session.summaryGenerated() || !"ACTIVE".equalsIgnoreCase(session.status())) {
            throw new ApiException("BIZ-1001", "interview session already completed", HttpStatus.BAD_REQUEST);
        }
        if (session.replyRoundUsed() >= session.replyRoundLimit()) {
            throw new ApiException("BIZ-1001", "interview reply round limit reached", HttpStatus.BAD_REQUEST);
        }
    }

    private void ensureAiInputAllowed(String traceId, long userId, String inputText) {
        // AI_INPUT 治理是所有模型调用前置闸门，命中 BLOCK 不进入 quota/provider。
        ModerationDecision moderationDecision = contentGovernanceService.moderateAiInput(traceId, userId, inputText);
        if (moderationDecision.isBlock()) {
            throw buildAiInputBlockedException(traceId, moderationDecision);
        }
    }

    private ApiException buildAiInputBlockedException(String traceId, ModerationDecision moderationDecision) {
        return new ApiException(
                "MOD-1001",
                "input blocked by moderation policy",
                HttpStatus.BAD_REQUEST,
                Map.of("moderation", moderationDecision.toAiPayload()),
                traceId
        );
    }

    private ApiException buildAiOutputBlockedException(String traceId, ModerationDecision moderationDecision) {
        return new ApiException(
                "MOD-1002",
                "output blocked by moderation policy",
                HttpStatus.BAD_REQUEST,
                Map.of("moderation", moderationDecision.toAiPayload()),
                traceId
        );
    }

    private ModeratedPayload<ResumeOptimizeResponse> moderateResumeOutput(
            String traceId,
            long userId,
            ResumeRequestContext requestContext,
            AiGatewayService.ResumeOptimizeGatewayResult gatewayResult
    ) {
        String summary = safe(gatewayResult.summary()).trim();
        // provider 结构化字段缺失时使用启发式兜底，避免前端复盘页出现空模块。
        List<String> strengths = sanitizeStringList(gatewayResult.strengths(), List.of("经历方向与目标岗位存在一定相关性"));
        List<String> risks = sanitizeStringList(gatewayResult.risks(), List.of("建议继续补充更具体的量化结果与岗位匹配表达"));
        List<String> suggestions = sanitizeStringList(gatewayResult.suggestions(), List.of("围绕目标岗位继续打磨一条最有代表性的项目经历"));
        List<ResumeStructureItem> structureItems = resolveResumeStructureItems(
                gatewayResult.structureItems(),
                requestContext,
                summary,
                strengths,
                risks,
                suggestions
        );
        List<ResumeRewriteItem> rewriteItems = resolveResumeRewriteItems(
                gatewayResult.rewriteItems(),
                requestContext,
                risks,
                suggestions
        );
        String scoreLabel = resolveResumeScoreLabel(gatewayResult.scoreLabel(), structureItems, strengths, risks, suggestions);
        ModerationDecision moderationDecision = contentGovernanceService.moderateAiOutput(
                traceId,
                userId,
                "RESUME",
                buildResumeOutputText(summary, strengths, risks, suggestions, structureItems, rewriteItems)
        );
        if (moderationDecision.isBlock()) {
            return new ModeratedPayload<>(null, moderationDecision, true);
        }
        if ("MASK".equalsIgnoreCase(moderationDecision.action())) {
            summary = maskAiOutputText(summary);
            strengths = maskAiOutputList(strengths);
            risks = maskAiOutputList(risks);
            suggestions = maskAiOutputList(suggestions);
            structureItems = maskResumeStructureItems(structureItems);
            rewriteItems = maskResumeRewriteItems(rewriteItems);
        }
        return new ModeratedPayload<>(
                new ResumeOptimizeResponse(
                        summary,
                        strengths,
                        risks,
                        suggestions,
                        scoreLabel,
                        structureItems,
                        rewriteItems,
                        buildMeta("RESUME", gatewayResult.gatewayResult()),
                        moderationDecision.toAiPayload()
                ),
                moderationDecision,
                false
        );
    }

    private AiQuotaService.AiTaskExecution<ModeratedPayload<ResumeOptimizeResponse>> buildResumeOptimizeExecution(
            String traceId,
            long userId,
            ResumeRequestContext requestContext,
            AiGatewayService.ResumeOptimizeGatewayResult gatewayResult
    ) {
        ModeratedPayload<ResumeOptimizeResponse> moderatedResponse = moderateResumeOutput(traceId, userId, requestContext, gatewayResult);
        ResumeOptimizeResponse response = moderatedResponse.payload();
        String resultSummary = moderatedResponse.blocked() ? null : response.summary();
        String resultPayloadJson = moderatedResponse.blocked()
                ? buildModerationPayloadJson(moderatedResponse.decision())
                : toJson(buildResumePayloadMap(requestContext, response));
        return new AiQuotaService.AiTaskExecution<>(
                moderatedResponse,
                gatewayResult.gatewayResult(),
                resultSummary,
                resultPayloadJson
        );
    }

    private ModeratedPayload<ResumeOptimizeResponse> attachResumeRecordId(
            ModeratedPayload<ResumeOptimizeResponse> moderatedPayload,
            Long recordId
    ) {
        if (moderatedPayload == null || moderatedPayload.blocked() || moderatedPayload.payload() == null || recordId == null || recordId <= 0) {
            return moderatedPayload;
        }
        return new ModeratedPayload<>(moderatedPayload.payload().withRecordId(recordId), moderatedPayload.decision(), false);
    }

    private ModeratedPayload<String> moderateAiOutputText(String traceId, long userId, String targetType, String text) {
        ModerationDecision moderationDecision = contentGovernanceService.moderateAiOutput(traceId, userId, targetType, text);
        if (moderationDecision.isBlock()) {
            return new ModeratedPayload<>(null, moderationDecision, true);
        }
        String safeText = text;
        if ("MASK".equalsIgnoreCase(moderationDecision.action())) {
            safeText = maskAiOutputText(text);
        }
        return new ModeratedPayload<>(safeText, moderationDecision, false);
    }

    private ModeratedInterviewReply moderateInterviewReplyOutput(
            String traceId,
            long userId,
            String followUpQuestion,
            String coachFeedback
    ) {
        ModerationDecision moderationDecision = contentGovernanceService.moderateAiOutput(
                traceId,
                userId,
                "INTERVIEW_REPLY",
                String.join("\n", "followUpQuestion=" + followUpQuestion, "coachFeedback=" + coachFeedback)
        );
        if (moderationDecision.isBlock()) {
            return new ModeratedInterviewReply(null, null, moderationDecision, true);
        }
        String safeFollowUpQuestion = followUpQuestion;
        String safeCoachFeedback = coachFeedback;
        if ("MASK".equalsIgnoreCase(moderationDecision.action())) {
            safeFollowUpQuestion = maskAiOutputText(followUpQuestion);
            safeCoachFeedback = maskAiOutputText(coachFeedback);
        }
        return new ModeratedInterviewReply(safeFollowUpQuestion, safeCoachFeedback, moderationDecision, false);
    }

    private ModeratedPayload<InterviewSummaryResponse> moderateInterviewSummaryOutput(
            String traceId,
            long userId,
            AiGatewayService.InterviewSummaryGatewayResult gatewayResult
    ) {
        ModerationDecision moderationDecision = contentGovernanceService.moderateAiOutput(
                traceId,
                userId,
                "INTERVIEW_SUMMARY",
                buildInterviewSummaryText(gatewayResult.strengths(), gatewayResult.weaknesses(), gatewayResult.suggestions())
        );
        if (moderationDecision.isBlock()) {
            return new ModeratedPayload<>(null, moderationDecision, true);
        }
        List<String> strengths = gatewayResult.strengths();
        List<String> weaknesses = gatewayResult.weaknesses();
        List<String> suggestions = gatewayResult.suggestions();
        if ("MASK".equalsIgnoreCase(moderationDecision.action())) {
            strengths = maskAiOutputList(strengths);
            weaknesses = maskAiOutputList(weaknesses);
            suggestions = maskAiOutputList(suggestions);
        }
        return new ModeratedPayload<>(
                new InterviewSummaryResponse(
                        gatewayResult.overallScore(),
                        strengths,
                        weaknesses,
                        suggestions,
                        buildMeta("INTERVIEW_SUMMARY", gatewayResult.gatewayResult()),
                        moderationDecision.toAiPayload()
                ),
                moderationDecision,
                false
        );
    }

    private ModeratedPayload<InterviewAnswerHelperResponse> moderateInterviewAnswerHelperOutput(
            String traceId,
            long userId,
            List<String> cueKeys,
            AiGatewayService.InterviewAnswerHelperGatewayResult gatewayResult
    ) {
        List<InterviewAnswerHelperItemResponse> items = sanitizeInterviewAnswerHelperItems(gatewayResult.items(), cueKeys);
        List<String> details = sanitizeStringList(
                gatewayResult.details(),
                List.of("建议优先补最缺的一处信息，再继续下一轮表达。")
        ).stream().limit(3).toList();
        String overallLevel = normalizeInterviewAnswerHelperLevel(gatewayResult.overallLevel());
        String overallSummary = safe(gatewayResult.overallSummary()).trim();
        if (overallSummary.isBlank()) {
            overallSummary = "这一轮回答已经有一定主线，建议优先补最缺的一处表达证据。";
        }
        ModerationDecision moderationDecision = contentGovernanceService.moderateAiOutput(
                traceId,
                userId,
                "INTERVIEW_ANSWER_HELPER",
                buildInterviewAnswerHelperText(overallSummary, items, details)
        );
        if (moderationDecision.isBlock()) {
            return new ModeratedPayload<>(null, moderationDecision, true);
        }
        if ("MASK".equalsIgnoreCase(moderationDecision.action())) {
            overallSummary = maskAiOutputText(overallSummary);
            items = items.stream()
                    .map(item -> new InterviewAnswerHelperItemResponse(
                            item.key(),
                            item.level(),
                            maskAiOutputText(item.summary()),
                            maskAiOutputText(item.nextAction())
                    ))
                    .toList();
            details = maskAiOutputList(details).stream().limit(3).toList();
        }
        return new ModeratedPayload<>(
                new InterviewAnswerHelperResponse(
                        overallLevel,
                        overallSummary,
                        items,
                        details,
                        buildMeta("INTERVIEW_ANSWER_HELPER", gatewayResult.gatewayResult()),
                        moderationDecision.toAiPayload()
                ),
                moderationDecision,
                false
        );
    }

    private String buildInterviewReplyPayloadJson(
            AiGatewayService.InterviewReplyGatewayResult gatewayResult,
            ModeratedInterviewReply moderatedReply,
            boolean shouldFinish,
            String finishReason,
            String sessionStatus,
            InterviewSummaryResponse summary
    ) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("followUpQuestion", moderatedReply.followUpQuestion());
        payload.put("coachFeedback", moderatedReply.coachFeedback());
        payload.put("scoreHint", gatewayResult.scoreHint());
        payload.put("shouldFinish", shouldFinish);
        payload.put("finishReason", finishReason);
        payload.put("sessionStatus", sessionStatus);
        payload.put("moderation", moderatedReply.decision().toAiPayload());
        if (summary != null) {
            payload.put("summary", summary);
        }
        return toJson(payload);
    }

    private String buildInterviewAnswerHelperPayloadJson(
            String sessionId,
            List<String> cueKeys,
            InterviewAnswerHelperResponse response
    ) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("cueKeys", cueKeys);
        payload.put("overallLevel", response.overallLevel());
        payload.put("overallSummary", response.overallSummary());
        payload.put("items", response.items());
        payload.put("details", response.details());
        payload.put("aiMeta", response.aiMeta());
        payload.put("moderation", response.moderation());
        return toJson(payload);
    }

    private long requirePositiveId(Long rawValue, String message) {
        if (rawValue == null || rawValue <= 0) {
            throw new ApiException("BIZ-1001", message, HttpStatus.BAD_REQUEST);
        }
        return rawValue;
    }

    private String buildResumePdfInputText(ResumeRequestContext requestContext, MultipartFile resumeFile) {
        String filename = resumeFile == null || resumeFile.getOriginalFilename() == null ? "" : resumeFile.getOriginalFilename().trim();
        String mimeType = resumeFile == null || resumeFile.getContentType() == null ? MediaType.APPLICATION_PDF_VALUE : resumeFile.getContentType().trim();
        long bytes = resumeFile == null ? 0L : resumeFile.getSize();
        return String.join(
                "\n",
                "targetRole=" + requestContext.targetRole(),
                "targetContext=" + requestContext.targetContext(),
                "jobDescription=\n" + requestContext.jobDescription(),
                "resumeFileName=" + filename,
                "resumeMimeType=" + mimeType,
                "resumeBytes=" + bytes
        );
    }

    private String buildInterviewCreateInputText(
            String targetRole,
            String mode,
            InterviewSessionContextSnapshot sessionContext,
            InterviewResumeContextSnapshot resumeContext
    ) {
        List<String> lines = new ArrayList<>();
        lines.add("targetRole=" + safe(targetRole));
        lines.add("mode=" + safe(mode));
        if (sessionContext != null) {
            lines.add("sessionContext=\n" + buildInterviewSessionPromptContext(sessionContext));
        }
        if (resumeContext != null) {
            lines.add("resumeRecordId=" + resumeContext.recordId());
            lines.add("resumeSummary=" + safe(resumeContext.summary()));
        }
        return String.join("\n", lines);
    }

    private String buildResumeInputText(ResumeRequestContext requestContext) {
        return String.join(
                "\n",
                "targetRole=" + requestContext.targetRole(),
                "targetContext=" + requestContext.targetContext(),
                "jobDescription=\n" + requestContext.jobDescription(),
                "resumeText=\n" + requestContext.resumeText()
        );
    }

    private String buildResumeOutputText(
            String summary,
            List<String> strengths,
            List<String> risks,
            List<String> suggestions,
            List<ResumeStructureItem> structureItems,
            List<ResumeRewriteItem> rewriteItems
    ) {
        return String.join(
                "\n",
                "summary=" + summary,
                "strengths=" + String.join(" | ", strengths),
                "risks=" + String.join(" | ", risks),
                "suggestions=" + String.join(" | ", suggestions),
                "structureItems=" + structureItems.stream()
                        .map(item -> item.label() + ":" + item.score() + ":" + item.tip())
                        .reduce("", (left, right) -> left.isBlank() ? right : left + " | " + right),
                "rewriteItems=" + rewriteItems.stream()
                        .map(item -> item.title() + ":" + item.problem() + ":" + item.afterText())
                        .reduce("", (left, right) -> left.isBlank() ? right : left + " | " + right)
        );
    }

    private ResumeRequestContext buildTextResumeRequestContext(ResumeOptimizeRequest request) {
        String targetRole = normalizeResumeTargetRole(request.targetRole());
        String targetContext = normalizeResumeTargetContext(request.targetContext());
        String jobDescription = normalizeResumeJobDescription(request.jobDescription());
        String resumeText = normalizeResumeText(request.resumeText());
        return new ResumeRequestContext(
                targetRole,
                targetContext,
                "text",
                jobDescription,
                resumeText,
                null
        );
    }

    private ResumeRequestContext buildPdfResumeRequestContext(
            String targetRole,
            String targetContext,
            String jobDescription,
            MultipartFile resumeFile
    ) {
        String pdfFileName = resumeFile == null || resumeFile.getOriginalFilename() == null
                ? ""
                : resumeFile.getOriginalFilename().trim();
        String extractedResumeText = extractPdfResumeText(resumeFile);
        return new ResumeRequestContext(
                normalizeResumeTargetRole(targetRole),
                normalizeResumeTargetContext(targetContext),
                "pdf",
                normalizeResumeJobDescription(jobDescription),
                extractedResumeText,
                pdfFileName
        );
    }

    private String normalizeResumeTargetRole(String targetRole) {
        if (targetRole == null || targetRole.isBlank()) {
            throw new ApiException("BIZ-1001", "targetRole must not be blank", HttpStatus.BAD_REQUEST);
        }
        String normalized = targetRole.trim();
        if (normalized.length() > 100) {
            throw new ApiException("BIZ-1001", "targetRole too long", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeResumeTargetContext(String targetContext) {
        if (targetContext == null || targetContext.isBlank()) {
            return "";
        }
        String normalized = targetContext.trim();
        if (normalized.length() > 80) {
            throw new ApiException("BIZ-1001", "targetContext too long", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeResumeJobDescription(String jobDescription) {
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
        String normalized = normalizeResumeTextContent(resumeText);
        if (normalized.length() > MAX_RESUME_TEXT_LENGTH) {
            throw new ApiException("BIZ-1001", "resumeText too long", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeExtractedResumeText(String resumeText) {
        String normalized = normalizeResumeTextContent(resumeText);
        if (normalized.length() > MAX_RESUME_TEXT_LENGTH) {
            return normalized.substring(0, MAX_RESUME_TEXT_LENGTH).trim();
        }
        return normalized;
    }

    private InterviewResumeContextSnapshot loadInterviewResumeContext(long userId, Long resumeRecordId) {
        if (resumeRecordId == null) {
            return null;
        }
        AiHistoryRepository.ResumeHistoryDetailRow row = aiHistoryRepository.findResumeDetail(userId, resumeRecordId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "resume history not found", HttpStatus.NOT_FOUND));
        JsonNode payload = readJsonNode(row.resultPayloadJson());
        String summary = readJsonText(payload, "summary");
        if (summary.isBlank()) {
            summary = safe(row.resultSummary()).trim();
        }
        String resumeText = normalizeResumeTextContent(readJsonText(payload, "resumeText"));
        return new InterviewResumeContextSnapshot(
                row.id(),
                limitText(summary, 320),
                readJsonStringList(payload.path("suggestions")).stream().limit(4).toList(),
                normalizeResumeScoreLabel(readJsonText(payload, "scoreLabel")),
                limitText(readJsonText(payload, "targetRole"), 100),
                limitText(readJsonText(payload, "targetContext"), 80),
                "pdf".equalsIgnoreCase(readJsonText(payload, "inputMode")) ? "pdf" : "text",
                limitText(readJsonText(payload, "jobDescription"), 400),
                limitText(readJsonText(payload, "pdfFileName"), 180),
                limitText(resumeText, MAX_INTERVIEW_RESUME_CONTEXT_LENGTH),
                com.bishe.server.common.TimePayloads.toEpochMillis(row.createdAt())
        );
    }

    private InterviewResumeContextSnapshot readInterviewResumeContext(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, InterviewResumeContextSnapshot.class);
        } catch (IOException ex) {
            throw new ApiException("AI-2001", "history payload invalid", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private InterviewSessionContextSnapshot normalizeInterviewSessionContext(
            InterviewSessionCreateRequest.InterviewSessionContextRequest request
    ) {
        if (request == null) {
            return null;
        }
        String interviewType = normalizeInterviewTypeCode(request.interviewType());
        String interviewerStyle = normalizeInterviewerStyleCode(request.interviewerStyle());
        String difficulty = normalizeDifficultyCode(request.difficulty());
        String answerMode = normalizeAnswerModeCode(request.answerMode());
        String targetCompany = normalizeInterviewTargetCompany(request.targetCompany());
        String targetJobDescription = normalizeResumeJobDescription(request.targetJobDescription());
        List<String> prepMaterialKeys = normalizeInterviewPrepMaterialKeys(request.prepMaterialKeys());
        List<String> answerHelperCueKeys = normalizeInterviewAnswerHelperCueKeys(request.answerHelperCueKeys());
        boolean answerHelperEnabled = normalizeInterviewAnswerHelperEnabled(request.answerHelperEnabled(), answerHelperCueKeys);
        List<String> persistedAnswerHelperCueKeys = answerHelperEnabled
                ? (answerHelperCueKeys.isEmpty() ? DEFAULT_ANSWER_HELPER_CUE_KEYS : answerHelperCueKeys)
                : List.of();
        String promptContext = normalizeInterviewPromptContext(request.promptContext());
        if (promptContext.isBlank()
                && targetCompany.isBlank()
                && targetJobDescription.isBlank()
                && prepMaterialKeys.isEmpty()
                && answerHelperEnabled
                && DEFAULT_ANSWER_HELPER_CUE_KEYS.equals(persistedAnswerHelperCueKeys)
                && "PROJECT_DEEP_DIVE".equals(interviewType)
                && "STANDARD".equals(interviewerStyle)
                && "MEDIUM".equals(difficulty)
                && "TEXT".equals(answerMode)) {
            return null;
        }
        return new InterviewSessionContextSnapshot(
                interviewType,
                interviewerStyle,
                difficulty,
                answerMode,
                targetCompany,
                targetJobDescription,
                prepMaterialKeys,
                answerHelperEnabled,
                persistedAnswerHelperCueKeys,
                promptContext
        );
    }

    private InterviewSessionContextSnapshot readInterviewSessionContext(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, InterviewSessionContextSnapshot.class);
        } catch (IOException ex) {
            throw new ApiException("AI-2001", "history payload invalid", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String buildInterviewSessionPromptContext(InterviewSessionContextSnapshot sessionContext) {
        if (sessionContext == null) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        lines.add("本轮面试设定如下，请在开场、追问和总结阶段持续遵守。");
        lines.add("interviewType=" + sessionContext.interviewType());
        lines.add("interviewTypeLabel=" + interviewTypeLabel(sessionContext.interviewType()));
        lines.add("interviewFocus=" + interviewTypePromptGuidance(sessionContext.interviewType()));
        lines.add("interviewerStyle=" + sessionContext.interviewerStyle());
        lines.add("interviewerStyleLabel=" + interviewerStyleLabel(sessionContext.interviewerStyle()));
        lines.add("interviewerTone=" + interviewerStylePromptGuidance(sessionContext.interviewerStyle()));
        lines.add("difficulty=" + sessionContext.difficulty());
        lines.add("difficultyLabel=" + difficultyLabel(sessionContext.difficulty()));
        lines.add("difficultyGuidance=" + difficultyPromptGuidance(sessionContext.difficulty()));
        lines.add("answerMode=" + sessionContext.answerMode());
        lines.add("answerModeGuidance=" + answerModePromptGuidance(sessionContext.answerMode()));
        if (!safe(sessionContext.targetCompany()).isBlank()) {
            lines.add("targetCompany=" + sessionContext.targetCompany());
        }
        if (!safe(sessionContext.targetJobDescription()).isBlank()) {
            lines.add("targetJobDescription=\n" + sessionContext.targetJobDescription());
        }
        if (sessionContext.prepMaterialKeys() != null && !sessionContext.prepMaterialKeys().isEmpty()) {
            lines.add("selectedPrepMaterials=" + String.join(", ", sessionContext.prepMaterialKeys()));
        }
        if (!safe(sessionContext.promptContext()).isBlank()) {
            lines.add("candidateContext=\n" + sessionContext.promptContext());
        }
        return String.join("\n", lines);
    }

    // 面试里只带入精简后的简历快照，避免把整份简历长文本反复灌进 prompt。
    private String buildInterviewResumePromptContext(InterviewResumeContextSnapshot resumeContext) {
        if (resumeContext == null) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        lines.add("候选人已授权带入最近一份简历，请把它作为补充背景来追问。");
        lines.add("使用原则：优先围绕真实经历、技术动作、量化结果和岗位匹配度提问，不要替候选人编造经历。");
        lines.add("资料来源=最近一份 AI 简历记录");
        lines.add("recordId=" + resumeContext.recordId());
        if (!safe(resumeContext.summary()).isBlank()) {
            lines.add("简历摘要=" + resumeContext.summary());
        }
        if (!safe(resumeContext.targetRole()).isBlank()) {
            lines.add("简历目标岗位=" + resumeContext.targetRole());
        }
        if (!safe(resumeContext.targetContext()).isBlank()) {
            lines.add("求职语境=" + resumeContext.targetContext());
        }
        if (!safe(resumeContext.scoreLabel()).isBlank()) {
            lines.add("简历评级=" + resumeContext.scoreLabel());
        }
        if (!safe(resumeContext.jobDescription()).isBlank()) {
            lines.add("岗位 JD 摘要=\n" + resumeContext.jobDescription());
        }
        if (!safe(resumeContext.pdfFileName()).isBlank()) {
            lines.add("PDF 文件名=" + resumeContext.pdfFileName());
        }
        if (resumeContext.suggestions() != null && !resumeContext.suggestions().isEmpty()) {
            lines.add("简历优化建议=" + String.join(" | ", resumeContext.suggestions()));
        }
        if (!safe(resumeContext.resumeTextExcerpt()).isBlank()) {
            lines.add("简历内容摘录=\n" + resumeContext.resumeTextExcerpt());
        }
        return String.join("\n", lines);
    }

    private Map<String, Object> buildInterviewCreateGuardPayload(
            String targetRole,
            String mode,
            InterviewResumeContextSnapshot resumeContext,
            InterviewSessionContextSnapshot sessionContext
    ) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("targetRole", targetRole);
        payload.put("mode", mode);
        payload.put("resumeRecordId", resumeContext == null ? null : resumeContext.recordId());
        if (sessionContext != null) {
            payload.put("interviewType", sessionContext.interviewType());
            payload.put("interviewerStyle", sessionContext.interviewerStyle());
            payload.put("difficulty", sessionContext.difficulty());
            payload.put("answerMode", sessionContext.answerMode());
            payload.put("targetCompany", sessionContext.targetCompany());
            payload.put("targetJobDescription", sessionContext.targetJobDescription());
            payload.put("prepMaterialKeys", sessionContext.prepMaterialKeys());
            payload.put("answerHelperEnabled", sessionContext.answerHelperEnabled());
            payload.put("answerHelperCueKeys", sessionContext.answerHelperCueKeys());
            payload.put("promptContext", sessionContext.promptContext());
        }
        return payload;
    }

    private String normalizeResumeTextContent(String resumeText) {
        if (resumeText == null || resumeText.isBlank()) {
            return "";
        }
        return resumeText
                .replace('\u00A0', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\f ]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private JsonNode readJsonNode(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (IOException ex) {
            throw new ApiException("AI-2001", "history payload invalid", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String readJsonText(JsonNode node, String fieldName) {
        if (node == null || fieldName == null || fieldName.isBlank()) {
            return "";
        }
        JsonNode child = node.get(fieldName);
        return child == null ? "" : safe(child.asText("")).trim();
    }

    private List<String> readJsonStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = safe(item.asText("")).trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private String limitText(String value, int maxLength) {
        String normalized = safe(value).trim();
        if (normalized.isBlank() || maxLength <= 0 || normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength).trim();
    }

    // PDF 模式尽量提取正文，便于历史预览与“带入资料”复用；提取失败时不阻断主流程。
    private String extractPdfResumeText(MultipartFile resumeFile) {
        if (resumeFile == null || resumeFile.isEmpty()) {
            return "";
        }
        try (PDDocument document = Loader.loadPDF(resumeFile.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return normalizeExtractedResumeText(stripper.getText(document));
        } catch (IOException ex) {
            return "";
        }
    }

    private List<String> sanitizeStringList(List<String> values, List<String> fallbackValues) {
        if (values == null || values.isEmpty()) {
            return fallbackValues;
        }
        List<String> normalized = values.stream()
                .map(this::safe)
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .limit(6)
                .toList();
        return normalized.isEmpty() ? fallbackValues : normalized;
    }

    private LinkedHashMap<String, Object> buildResumePayloadMap(
            ResumeRequestContext requestContext,
            ResumeOptimizeResponse response
    ) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", response.summary());
        payload.put("strengths", response.strengths());
        payload.put("risks", response.risks());
        payload.put("suggestions", response.suggestions());
        payload.put("scoreLabel", response.scoreLabel());
        payload.put("structureItems", response.structureItems());
        payload.put("rewriteItems", response.rewriteItems());
        payload.put("targetRole", requestContext.targetRole());
        payload.put("targetContext", requestContext.targetContext());
        payload.put("inputMode", requestContext.inputMode());
        payload.put("jobDescription", requestContext.jobDescription());
        payload.put("resumeText", requestContext.resumeText());
        payload.put("pdfFileName", requestContext.pdfFileName());
        payload.put("moderation", response.moderation());
        return payload;
    }

    private List<ResumeStructureItem> resolveResumeStructureItems(
            List<ResumeStructureItem> providerItems,
            ResumeRequestContext requestContext,
            String summary,
            List<String> strengths,
            List<String> risks,
            List<String> suggestions
    ) {
        List<ResumeStructureItem> normalizedProviderItems = sanitizeResumeStructureItems(providerItems);
        if (!normalizedProviderItems.isEmpty()) {
            return normalizedProviderItems;
        }

        String heuristicText = String.join(
                " ",
                requestContext.targetRole(),
                requestContext.targetContext(),
                requestContext.jobDescription(),
                requestContext.resumeText(),
                summary,
                String.join(" ", strengths),
                String.join(" ", risks),
                String.join(" ", suggestions)
        ).toLowerCase(Locale.ROOT);

        int basicScore = clampResumeDimensionScore(
                2
                        + (requestContext.targetRole().isBlank() ? 0 : 1)
                        + (requestContext.targetContext().isBlank() ? 0 : 1)
                        + (countResumeSignals(heuristicText, List.of("邮箱", "电话", "联系", "姓名", "email", "phone")) > 0 ? 1 : 0)
        );
        int educationScore = clampResumeDimensionScore(
                1 + (countResumeSignals(heuristicText, List.of("大学", "学院", "专业", "学历", "gpa", "education", "bachelor")) >= 2 ? 3 : 1)
        );
        int skillScore = clampResumeDimensionScore(
                2 + (countResumeSignals(heuristicText, List.of("java", "spring", "mysql", "redis", "react", "vue", "typescript", "工程化", "性能")) >= 3 ? 2 : 1)
        );
        int projectScore = clampResumeDimensionScore(
                1
                        + (countResumeSignals(heuristicText, List.of("项目", "负责", "优化", "性能", "模块", "project", "latency")) >= 2 ? 2 : 0)
                        + (strengths.isEmpty() ? 0 : 1)
                        - (risks.stream().anyMatch(item -> item.contains("项目")) ? 1 : 0)
        );
        int internshipScore = clampResumeDimensionScore(
                1
                        + (countResumeSignals(heuristicText, List.of("实习", "业务", "团队", "协作", "intern", "team")) >= 2 ? 2 : 0)
                        + (strengths.stream().anyMatch(item -> item.contains("实习") || item.contains("业务")) ? 1 : 0)
        );
        int matchScore = clampResumeDimensionScore(
                2
                        + (requestContext.jobDescription().isBlank() ? 0 : 1)
                        + (strengths.size() > risks.size() ? 1 : 0)
                        + (requestContext.targetRole().isBlank() ? 0 : 1)
                        - (risks.size() >= 3 ? 1 : 0)
        );

        return List.of(
                new ResumeStructureItem("基本信息", basicScore, basicScore >= 4 ? "表达完整，阅读较顺畅" : "建议补联系信息与求职目标"),
                new ResumeStructureItem("教育背景", educationScore, educationScore >= 4 ? "教育信息较完整" : "建议补学校、专业与阶段"),
                new ResumeStructureItem("技能描述", skillScore, skillScore >= 4 ? "技能栈较完整" : "建议补技术关键词与熟练场景"),
                new ResumeStructureItem("项目经历", projectScore, projectScore >= 4 ? "项目主线较清晰" : "建议补技术动作与结果"),
                new ResumeStructureItem("实习经历", internshipScore, internshipScore >= 4 ? "业务价值表达较完整" : "建议补业务背景与个人贡献"),
                new ResumeStructureItem("岗位匹配度", matchScore, matchScore >= 4 ? "与目标岗位较贴合" : "建议补岗位关键词与成果")
        );
    }

    private List<ResumeStructureItem> sanitizeResumeStructureItems(List<ResumeStructureItem> providerItems) {
        if (providerItems == null || providerItems.isEmpty()) {
            return List.of();
        }
        List<ResumeStructureItem> normalized = new ArrayList<>();
        for (ResumeStructureItem item : providerItems) {
            if (item == null) {
                continue;
            }
            String label = safe(item.label()).trim();
            String tip = safe(item.tip()).trim();
            if (label.isBlank()) {
                continue;
            }
            normalized.add(new ResumeStructureItem(label, clampResumeDimensionScore(item.score()), tip.isBlank() ? "建议继续补强岗位证据。" : tip));
        }
        return normalized.stream().limit(8).toList();
    }

    private List<ResumeRewriteItem> resolveResumeRewriteItems(
            List<ResumeRewriteItem> providerItems,
            ResumeRequestContext requestContext,
            List<String> risks,
            List<String> suggestions
    ) {
        List<ResumeRewriteItem> normalizedProviderItems = sanitizeResumeRewriteItems(providerItems, requestContext);
        if (normalizedProviderItems.size() >= 2) {
            return normalizedProviderItems;
        }

        List<ResumeRewriteItem> fallbackItems = buildFallbackRewriteItems(requestContext, risks, suggestions);
        if (normalizedProviderItems.isEmpty()) {
            return fallbackItems;
        }

        List<ResumeRewriteItem> merged = new ArrayList<>(normalizedProviderItems);
        for (ResumeRewriteItem fallbackItem : fallbackItems) {
            boolean alreadyPresent = merged.stream().anyMatch(item -> item.id().equals(fallbackItem.id()));
            if (!alreadyPresent) {
                merged.add(fallbackItem);
            }
            if (merged.size() >= 4) {
                break;
            }
        }
        return merged;
    }

    private List<ResumeRewriteItem> sanitizeResumeRewriteItems(
            List<ResumeRewriteItem> providerItems,
            ResumeRequestContext requestContext
    ) {
        if (providerItems == null || providerItems.isEmpty()) {
            return List.of();
        }
        List<String> fallbackBeforeTexts = extractResumeSourceLines(requestContext);
        List<ResumeRewriteItem> normalized = new ArrayList<>();
        for (int index = 0; index < providerItems.size(); index++) {
            ResumeRewriteItem item = providerItems.get(index);
            if (item == null) {
                continue;
            }
            String problem = safe(item.problem()).trim();
            String afterText = safe(item.afterText()).trim();
            if (problem.isBlank() || afterText.isBlank()) {
                continue;
            }
            String title = safe(item.title()).trim();
            String beforeText = safe(item.beforeText()).trim();
            normalized.add(new ResumeRewriteItem(
                    normalizeRewriteItemId(item.id(), index),
                    title.isBlank() ? deriveResumeRewriteTitle(problem, index) : title,
                    problem,
                    beforeText.isBlank() ? fallbackBeforeTexts.get(Math.min(index, fallbackBeforeTexts.size() - 1)) : beforeText,
                    afterText,
                    item.isHeuristic()
            ));
        }
        return normalized.stream().limit(4).toList();
    }

    private List<ResumeRewriteItem> buildFallbackRewriteItems(
            ResumeRequestContext requestContext,
            List<String> risks,
            List<String> suggestions
    ) {
        List<String> sourceLines = extractResumeSourceLines(requestContext);
        List<String> issues = new ArrayList<>();
        issues.addAll(suggestions);
        issues.addAll(risks);
        issues = issues.stream().map(String::trim).filter(item -> !item.isBlank()).distinct().limit(4).toList();

        List<String> fallbackIssues = List.of(
                "建议补充业务价值、工程化实践与量化结果，让项目经历更有说服力。",
                "建议减少空泛形容词，改成更具体的岗位匹配证据与能力描述。",
                "建议突出关键技术动作，让经历不只停留在职责罗列。",
                "建议补充结果变化与个人贡献，让亮点更容易被识别。"
        );

        List<ResumeRewriteItem> items = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            String problem = index < issues.size() ? issues.get(index) : fallbackIssues.get(index);
            String beforeText = sourceLines.get(Math.min(index, sourceLines.size() - 1));
            items.add(new ResumeRewriteItem(
                    "rewrite-" + (index + 1),
                    deriveResumeRewriteTitle(problem, index),
                    problem,
                    beforeText,
                    buildFallbackRewriteAfterText(beforeText, problem, requestContext.targetRole(), requestContext.targetContext()),
                    true
            ));
        }
        return items;
    }

    private List<String> extractResumeSourceLines(ResumeRequestContext requestContext) {
        List<String> candidates = new ArrayList<>();
        String rawText = safe(requestContext.resumeText());
        if (!rawText.isBlank()) {
            String[] segments = rawText.split("[\\n\\r]+|(?<=[。；;.!?])");
            for (String segment : segments) {
                String normalized = segment == null ? "" : segment.trim();
                if (normalized.length() >= 12) {
                    candidates.add(normalized);
                }
            }
        }
        if (candidates.isEmpty()) {
            String role = requestContext.targetRole().isBlank() ? "目标岗位" : requestContext.targetRole();
            String fileName = safe(requestContext.pdfFileName()).isBlank() ? "PDF 简历" : requestContext.pdfFileName();
            candidates.add("已上传 " + fileName + "，建议围绕 " + role + " 进一步补强代表性经历。");
            candidates.add("建议从业务场景、技术动作与量化结果三个层次重写最核心的一段经历。");
        }
        return candidates.stream().limit(4).toList();
    }

    private String buildFallbackRewriteAfterText(
            String beforeText,
            String problem,
            String targetRole,
            String targetContext
    ) {
        String roleLabel = targetRole == null || targetRole.isBlank() ? "目标岗位" : targetRole.trim();
        String contextLabel = targetContext == null || targetContext.isBlank() ? "当前投递语境" : targetContext.trim();
        String conciseProblem = problem.split("[，。；;]")[0].trim();
        String normalizedBefore = beforeText.replaceAll("[。；;]+$", "").trim();
        if (normalizedBefore.isBlank()) {
            normalizedBefore = "当前经历描述";
        }
        return "面向 " + contextLabel + " 下的 " + roleLabel + "，建议把这段经历改成“场景 + 动作 + 结果”的一句话：先交代业务背景，再点明你负责的核心模块与技术动作，最后把“"
                + conciseProblem
                + "”落到可感知的指标变化上，例如延迟、效率、稳定性或成本改善。原表述“"
                + normalizedBefore
                + "”可以继续保留关键词，但需要补上个人贡献边界和结果验证方式。";
    }

    private String deriveResumeRewriteTitle(String problem, int index) {
        String normalizedProblem = safe(problem);
        if (normalizedProblem.contains("量化") || normalizedProblem.contains("指标")) {
            return "补强量化结果";
        }
        if (normalizedProblem.contains("技术") || normalizedProblem.contains("方案")) {
            return "补强技术动作";
        }
        if (normalizedProblem.contains("岗位") || normalizedProblem.contains("JD")) {
            return "补强岗位匹配";
        }
        return index == 0 ? "重写代表性项目" : "压缩空泛表达";
    }

    private String normalizeRewriteItemId(String rawId, int index) {
        String normalized = safe(rawId).trim();
        return normalized.isBlank() ? "rewrite-" + (index + 1) : normalized;
    }

    private List<ResumeStructureItem> maskResumeStructureItems(List<ResumeStructureItem> items) {
        return items.stream()
                .map(item -> new ResumeStructureItem(item.label(), item.score(), maskAiOutputText(item.tip())))
                .toList();
    }

    private List<ResumeRewriteItem> maskResumeRewriteItems(List<ResumeRewriteItem> items) {
        return items.stream()
                .map(item -> new ResumeRewriteItem(
                        item.id(),
                        maskAiOutputText(item.title()),
                        maskAiOutputText(item.problem()),
                        item.beforeText(),
                        maskAiOutputText(item.afterText()),
                        item.isHeuristic()
                ))
                .toList();
    }

    private String resolveResumeScoreLabel(
            String providerScoreLabel,
            List<ResumeStructureItem> structureItems,
            List<String> strengths,
            List<String> risks,
            List<String> suggestions
    ) {
        String normalizedProviderScore = normalizeResumeScoreLabel(providerScoreLabel);
        if (normalizedProviderScore != null) {
            return normalizedProviderScore;
        }
        double structureAverage = structureItems.stream().mapToInt(ResumeStructureItem::score).average().orElse(3.0d);
        int score = (int) Math.round(
                56
                        + structureAverage * 7
                        + strengths.size() * 4
                        - risks.size() * 3
                        - Math.max(suggestions.size() - 1, 0) * 2
        );
        return mapResumeScoreLabel(score);
    }

    private String normalizeResumeScoreLabel(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "S", "A+", "A", "A-", "B+", "B", "B-", "C+", "C", "D" -> normalized;
            default -> null;
        };
    }

    private String mapResumeScoreLabel(int score) {
        if (score >= 92) {
            return "S";
        }
        if (score >= 88) {
            return "A+";
        }
        if (score >= 84) {
            return "A";
        }
        if (score >= 80) {
            return "A-";
        }
        if (score >= 76) {
            return "B+";
        }
        if (score >= 72) {
            return "B";
        }
        if (score >= 68) {
            return "B-";
        }
        if (score >= 62) {
            return "C+";
        }
        if (score >= 56) {
            return "C";
        }
        return "D";
    }

    private int countResumeSignals(String text, List<String> keywords) {
        if (text == null || text.isBlank() || keywords == null || keywords.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && text.contains(keyword.toLowerCase(Locale.ROOT))) {
                count++;
            }
        }
        return count;
    }

    private int clampResumeDimensionScore(int score) {
        return Math.max(1, Math.min(5, score));
    }

    private String buildInterviewSummaryText(List<String> strengths, List<String> weaknesses, List<String> suggestions) {
        return String.join(
                "\n",
                "strengths=" + String.join(" | ", strengths),
                "weaknesses=" + String.join(" | ", weaknesses),
                "suggestions=" + String.join(" | ", suggestions)
        );
    }

    private String buildInterviewAnswerHelperText(
            String overallSummary,
            List<InterviewAnswerHelperItemResponse> items,
            List<String> details
    ) {
        return String.join(
                "\n",
                "overallSummary=" + safe(overallSummary),
                "items=" + items.stream()
                        .map(item -> item.key() + ":" + item.level() + ":" + item.summary() + ":" + item.nextAction())
                        .reduce("", (left, right) -> left.isBlank() ? right : left + " | " + right),
                "details=" + String.join(" | ", details)
        );
    }

    private List<InterviewAnswerHelperItemResponse> sanitizeInterviewAnswerHelperItems(
            List<AiGatewayService.InterviewAnswerHelperItemGatewayResult> providerItems,
            List<String> cueKeys
    ) {
        List<String> normalizedCueKeys = resolveInterviewAnswerHelperCueKeys(cueKeys);
        Map<String, AiGatewayService.InterviewAnswerHelperItemGatewayResult> providerByKey = new LinkedHashMap<>();
        if (providerItems != null) {
            for (AiGatewayService.InterviewAnswerHelperItemGatewayResult item : providerItems) {
                if (item == null) {
                    continue;
                }
                String key = normalizeInterviewAnswerHelperCueKey(item.key());
                if (key == null || providerByKey.containsKey(key)) {
                    continue;
                }
                providerByKey.put(key, item);
            }
        }
        List<InterviewAnswerHelperItemResponse> items = new ArrayList<>();
        for (String cueKey : normalizedCueKeys) {
            AiGatewayService.InterviewAnswerHelperItemGatewayResult item = providerByKey.get(cueKey);
            String summary = item == null ? defaultInterviewAnswerHelperSummary(cueKey) : safe(item.summary()).trim();
            String nextAction = item == null ? defaultInterviewAnswerHelperNextAction(cueKey) : safe(item.nextAction()).trim();
            items.add(new InterviewAnswerHelperItemResponse(
                    cueKey,
                    normalizeInterviewAnswerHelperLevel(item == null ? null : item.level()),
                    summary.isBlank() ? defaultInterviewAnswerHelperSummary(cueKey) : summary,
                    nextAction.isBlank() ? defaultInterviewAnswerHelperNextAction(cueKey) : nextAction
            ));
        }
        return List.copyOf(items);
    }

    private String defaultInterviewAnswerHelperSummary(String cueKey) {
        return switch (safe(cueKey).trim().toUpperCase(Locale.ROOT)) {
            case "METRICS" -> "当前还需要更明确的数据或结果对比。";
            case "COMPLETENESS" -> "这轮回答还可以再补一层收口与表达闭环。";
            default -> "这轮回答的背景、动作和结果还可以再压紧。";
        };
    }

    private String defaultInterviewAnswerHelperNextAction(String cueKey) {
        return switch (safe(cueKey).trim().toUpperCase(Locale.ROOT)) {
            case "METRICS" -> "补一组指标、比例或前后对比，让结果更具体。";
            case "COMPLETENESS" -> "结尾补一句验证方式、复盘或下一步，让整段更完整。";
            default -> "按“背景 - 动作 - 结果”补齐最缺的一段，再强调个人贡献。";
        };
    }

    private String normalizeInterviewAnswerHelperLevel(String value) {
        String normalized = safe(value).trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "READY", "WARN", "INFO" -> normalized;
            default -> "INFO";
        };
    }

    private String normalizeInterviewAnswerHelperCueKey(String value) {
        String normalized = safe(value).trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "STAR", "METRICS", "COMPLETENESS" -> normalized;
            default -> null;
        };
    }

    private List<String> resolveInterviewAnswerHelperCueKeys(InterviewSessionContextSnapshot sessionContext) {
        if (sessionContext == null) {
            return DEFAULT_ANSWER_HELPER_CUE_KEYS;
        }
        return resolveInterviewAnswerHelperCueKeys(sessionContext.answerHelperCueKeys());
    }

    private List<String> resolveInterviewAnswerHelperCueKeys(List<String> cueKeys) {
        if (cueKeys == null || cueKeys.isEmpty()) {
            return DEFAULT_ANSWER_HELPER_CUE_KEYS;
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String cueKey : cueKeys) {
            String normalizedCueKey = normalizeInterviewAnswerHelperCueKey(cueKey);
            if (normalizedCueKey != null) {
                normalized.add(normalizedCueKey);
            }
        }
        return normalized.isEmpty() ? DEFAULT_ANSWER_HELPER_CUE_KEYS : List.copyOf(normalized);
    }

    private InterviewAnswerHelperResponse buildIdleInterviewAnswerHelperResponse(List<String> cueKeys) {
        List<String> resolvedCueKeys = resolveInterviewAnswerHelperCueKeys(cueKeys);
        List<InterviewAnswerHelperItemResponse> items = resolvedCueKeys.stream()
                .map(cueKey -> new InterviewAnswerHelperItemResponse(
                        cueKey,
                        "INFO",
                        "当前还没有可分析的已发送回答。",
                        "先完成一轮回答后，这里会给出对应的补充建议。"
                ))
                .toList();
        return new InterviewAnswerHelperResponse(
                "INFO",
                "当前还没有可分析的已发送回答，先完成一轮作答后再看这一块。",
                items,
                List.of("先完成一轮回答后，这里会给出针对性的补充提示。"),
                null,
                PASS_MODERATION
        );
    }

    private String buildModerationPayloadJson(ModerationDecision moderationDecision) {
        return toJson(Map.of("moderation", moderationDecision.toAiPayload()));
    }

    private List<String> maskAiOutputList(List<String> values) {
        return values.stream().map(this::maskAiOutputText).toList();
    }

    private String maskAiOutputText(String value) {
        return contentGovernanceService.maskTextForSource("AI_OUTPUT", value);
    }

    private record ModeratedPayload<T>(T payload, ModerationDecision decision, boolean blocked) {
    }

    private record ModeratedInterviewReply(
            String followUpQuestion,
            String coachFeedback,
            ModerationDecision decision,
            boolean blocked
    ) {
    }

    public record VoiceStreamTranscriptProgress(
            String transcript,
            String audioObjectKey,
            AiMetaPayload transcriptMeta
    ) {
    }

    private record ResumeRequestContext(
            String targetRole,
            String targetContext,
            String inputMode,
            String jobDescription,
            String resumeText,
            String pdfFileName
    ) {
    }

    private record InterviewResumeContextSnapshot(
            long recordId,
            String summary,
            List<String> suggestions,
            String scoreLabel,
            String targetRole,
            String targetContext,
            String inputMode,
            String jobDescription,
            String pdfFileName,
            String resumeTextExcerpt,
            Long createdAt
    ) {
    }

    private record InterviewSessionContextSnapshot(
            String interviewType,
            String interviewerStyle,
            String difficulty,
            String answerMode,
            String targetCompany,
            String targetJobDescription,
            List<String> prepMaterialKeys,
            boolean answerHelperEnabled,
            List<String> answerHelperCueKeys,
            String promptContext
    ) {
    }

    private record VoiceTranscriptionContext(
            String transcript,
            String audioObjectKey,
            AiMetaPayload transcriptMeta
    ) {
    }

    private record LiveTranscriptMessageSnapshot(
            String role,
            String text
    ) {
    }

    private void ensureVoiceFeatureEnabledForMode(String mode, String answerMode) {
        if (INTERVIEW_VOICE_MODE.equals(mode) || INTERVIEW_ANSWER_MODE_LIVE.equals(answerMode)) {
            featureFlagService.requireVoiceInterviewEnabled();
        }
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return INTERVIEW_TEXT_MODE;
        }
        String normalized = mode.trim().toUpperCase(Locale.ROOT);
        if (!INTERVIEW_TEXT_MODE.equals(normalized) && !INTERVIEW_VOICE_MODE.equals(normalized)) {
            throw new ApiException("BIZ-1001", "mode invalid", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeInterviewTypeCode(String value) {
        if (value == null || value.isBlank()) {
            return "PROJECT_DEEP_DIVE";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_INTERVIEW_TYPE_CODES.contains(normalized)) {
            throw new ApiException("BIZ-1001", "interviewType invalid", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeInterviewerStyleCode(String value) {
        if (value == null || value.isBlank()) {
            return "STANDARD";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_INTERVIEWER_STYLE_CODES.contains(normalized)) {
            throw new ApiException("BIZ-1001", "interviewerStyle invalid", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeDifficultyCode(String value) {
        if (value == null || value.isBlank()) {
            return "MEDIUM";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_DIFFICULTY_CODES.contains(normalized)) {
            throw new ApiException("BIZ-1001", "difficulty invalid", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeAnswerModeCode(String value) {
        if (value == null || value.isBlank()) {
            return INTERVIEW_ANSWER_MODE_TEXT;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_ANSWER_MODE_CODES.contains(normalized)) {
            throw new ApiException("BIZ-1001", "answerMode invalid", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private List<LiveTranscriptMessageSnapshot> normalizeLiveTranscriptMessages(
            List<InterviewLiveTranscriptImportRequest.MessageItem> rawMessages
    ) {
        if (rawMessages == null || rawMessages.isEmpty()) {
            return List.of();
        }
        List<LiveTranscriptMessageSnapshot> normalized = new ArrayList<>();
        for (InterviewLiveTranscriptImportRequest.MessageItem rawMessage : rawMessages) {
            if (rawMessage == null) {
                continue;
            }
            String role = rawMessage.role() == null ? "" : rawMessage.role().trim().toUpperCase(Locale.ROOT);
            if (!"USER".equals(role) && !"ASSISTANT".equals(role)) {
                throw new ApiException("BIZ-1001", "live transcript role invalid", HttpStatus.BAD_REQUEST);
            }
            String text = rawMessage.text() == null ? "" : rawMessage.text().trim();
            if (text.isBlank()) {
                continue;
            }
            if (text.length() > 8000) {
                throw new ApiException("BIZ-1001", "live transcript text too long", HttpStatus.BAD_REQUEST);
            }
            normalized.add(new LiveTranscriptMessageSnapshot(role, text));
        }
        return List.copyOf(normalized);
    }

    private AiGatewayService.AiGatewayResult buildLivePlaceholderGatewayResult() {
        return new AiGatewayService.AiGatewayResult(
                "gemini-live-proxy",
                "interview-live-reserve",
                Instant.now().toEpochMilli(),
                INTERVIEW_LIVE_PLACEHOLDER_PROVIDER,
                INTERVIEW_LIVE_PLACEHOLDER_MODEL,
                0L,
                0,
                0,
                0,
                0,
                null,
                null,
                "minimal",
                BigDecimal.ZERO,
                INTERVIEW_LIVE_PLACEHOLDER_SCENE_CODE,
                INTERVIEW_LIVE_PLACEHOLDER_ROUTE_CODE,
                null
        );
    }

    private String buildLivePlaceholderPayloadJson(String targetRole, InterviewSessionContextSnapshot sessionContext) {
        return toJson(Map.of(
                "mode", INTERVIEW_ANSWER_MODE_LIVE,
                "targetRole", targetRole,
                "interviewType", sessionContext == null ? "" : sessionContext.interviewType(),
                "interviewerStyle", sessionContext == null ? "" : sessionContext.interviewerStyle()
        ));
    }

    private String normalizeOptionalSessionId(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim();
    }

    private String normalizeInterviewTargetCompany(String targetCompany) {
        if (targetCompany == null || targetCompany.isBlank()) {
            return "";
        }
        String normalized = targetCompany.trim();
        if (normalized.length() > 120) {
            throw new ApiException("BIZ-1001", "targetCompany too long", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private List<String> normalizeInterviewPrepMaterialKeys(List<String> rawKeys) {
        if (rawKeys == null || rawKeys.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String rawKey : rawKeys) {
            if (rawKey == null || rawKey.isBlank()) {
                continue;
            }
            String candidate = rawKey.trim().toUpperCase(Locale.ROOT);
            if (!SUPPORTED_PREP_MATERIAL_KEYS.contains(candidate)) {
                throw new ApiException("BIZ-1001", "prepMaterialKeys invalid", HttpStatus.BAD_REQUEST);
            }
            normalized.add(candidate);
        }
        if (normalized.size() > 12) {
            throw new ApiException("BIZ-1001", "prepMaterialKeys too many", HttpStatus.BAD_REQUEST);
        }
        return List.copyOf(normalized);
    }

    private boolean normalizeInterviewAnswerHelperEnabled(Boolean value, List<String> cueKeys) {
        if (value != null) {
            return value;
        }
        return true;
    }

    private List<String> normalizeInterviewAnswerHelperCueKeys(List<String> rawKeys) {
        if (rawKeys == null || rawKeys.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String rawKey : rawKeys) {
            if (rawKey == null || rawKey.isBlank()) {
                continue;
            }
            String candidate = rawKey.trim().toUpperCase(Locale.ROOT);
            if (!SUPPORTED_ANSWER_HELPER_CUE_KEYS.contains(candidate)) {
                throw new ApiException("BIZ-1001", "answerHelperCueKeys invalid", HttpStatus.BAD_REQUEST);
            }
            normalized.add(candidate);
        }
        if (normalized.size() > DEFAULT_ANSWER_HELPER_CUE_KEYS.size()) {
            throw new ApiException("BIZ-1001", "answerHelperCueKeys too many", HttpStatus.BAD_REQUEST);
        }
        return List.copyOf(normalized);
    }

    private String normalizeInterviewPromptContext(String promptContext) {
        if (promptContext == null || promptContext.isBlank()) {
            return "";
        }
        String normalized = normalizeResumeTextContent(promptContext);
        if (normalized.length() > MAX_INTERVIEW_SESSION_PROMPT_CONTEXT_LENGTH) {
            return normalized.substring(0, MAX_INTERVIEW_SESSION_PROMPT_CONTEXT_LENGTH).trim();
        }
        return normalized;
    }

    private String interviewTypeLabel(String interviewType) {
        return switch (safe(interviewType).trim().toUpperCase(Locale.ROOT)) {
            case "FUNDAMENTALS" -> "八股基础";
            case "BEHAVIORAL" -> "行为面试";
            case "PRESSURE" -> "压力追问";
            default -> "项目深挖";
        };
    }

    private String interviewTypePromptGuidance(String interviewType) {
        return switch (safe(interviewType).trim().toUpperCase(Locale.ROOT)) {
            case "FUNDAMENTALS" -> "优先围绕基础原理、概念理解、技术取舍和知识点表达追问。";
            case "BEHAVIORAL" -> "优先围绕动机、选择、协作、冲突处理和复盘能力发问，帮助候选人按 STAR 方式表达。";
            case "PRESSURE" -> "优先抓回答里的空档与模糊点连续追问，但仍保持专业和克制，不做人身压迫。";
            default -> "优先围绕最能体现岗位匹配度的一段项目经历深挖背景、动作、取舍和结果。";
        };
    }

    private String interviewerStyleLabel(String interviewerStyle) {
        return switch (safe(interviewerStyle).trim().toUpperCase(Locale.ROOT)) {
            case "COACHING" -> "温和引导型";
            case "PRESSURE" -> "高压追问型";
            case "HR" -> "HR 沟通型";
            default -> "常规校招型";
        };
    }

    private String interviewerStylePromptGuidance(String interviewerStyle) {
        return switch (safe(interviewerStyle).trim().toUpperCase(Locale.ROOT)) {
            case "COACHING" -> "语气温和、自然、有陪练感；可以简短认可后再追问，但不要过度安慰或教学。";
            case "PRESSURE" -> "语气更直接、更短促，追问更连续，但仍像真实面试官，不阴阳怪气。";
            case "HR" -> "更关注动机、岗位匹配、沟通协作和选择判断，语言自然，不堆砌技术术语。";
            default -> "保持真实一面面试官口吻，专业但不生硬，像真人面谈而不是系统播报。";
        };
    }

    private String difficultyLabel(String difficulty) {
        return switch (safe(difficulty).trim().toUpperCase(Locale.ROOT)) {
            case "EASY" -> "基础摸底";
            case "HARD" -> "压力追问";
            default -> "常规面试";
        };
    }

    private String difficultyPromptGuidance(String difficulty) {
        return switch (safe(difficulty).trim().toUpperCase(Locale.ROOT)) {
            case "EASY" -> "问题先由浅入深，不要一开始就多重连问，给候选人一定组织空间。";
            case "HARD" -> "可以更快指出细节缺口和证据不足之处，追问更锋利，但不要失真或攻击性表达。";
            default -> "保持真实校招一面强度，既追问关键细节，也给候选人正常表达空间。";
        };
    }

    private String answerModePromptGuidance(String answerMode) {
        return (INTERVIEW_ANSWER_MODE_VOICE.equalsIgnoreCase(answerMode) || INTERVIEW_ANSWER_MODE_LIVE.equalsIgnoreCase(answerMode))
                ? "候选人会更偏口头回答，问题尽量短一些、顺口一些，一次只推进一个重点。"
                : "候选人会以文字回答，可正常追问，但仍保持自然口语感，不写成问卷。";
    }

    private String normalizeAnswer(String answerText) {
        if (answerText == null || answerText.isBlank()) {
            throw new ApiException("BIZ-1001", "answerText invalid", HttpStatus.BAD_REQUEST);
        }
        return answerText.trim();
    }

    private String normalizeTextToSpeechText(String text) {
        if (text == null || text.isBlank()) {
            throw new ApiException("BIZ-1001", "text invalid", HttpStatus.BAD_REQUEST);
        }
        return text.trim();
    }

    private String normalizeFinishReason(String rawReason, String fallbackReason) {
        if (rawReason == null || rawReason.isBlank()) {
            return fallbackReason;
        }
        return rawReason.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeFinishStatement(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return DEFAULT_FINISH_STATEMENT;
        }
        return rawText.trim();
    }

    private String generateSessionId() {
        long suffix = Math.abs(ThreadLocalRandom.current().nextLong(100_000L, 999_999L));
        return "is_" + System.currentTimeMillis() + suffix;
    }

    private void validateAudioFile(MultipartFile audioFile) {
        if (audioFile == null || audioFile.isEmpty()) {
            throw new ApiException("BIZ-1001", "audio file required", HttpStatus.BAD_REQUEST);
        }
        if (audioFile.getSize() > MAX_AUDIO_FILE_BYTES) {
            throw new ApiException("BIZ-1001", "audio file too large", HttpStatus.BAD_REQUEST);
        }
    }

    private String buildAudioObjectKey(String sessionId, MultipartFile audioFile) {
        String originalFilename = audioFile == null ? null : audioFile.getOriginalFilename();
        String safeFilename = (originalFilename == null || originalFilename.isBlank())
                ? "answer.webm"
                : originalFilename.trim().replaceAll("\\s+", "-");
        return "upload://interview/" + sessionId + "/" + System.currentTimeMillis() + "-" + safeFilename;
    }

    private InterviewSummaryResponse buildPersistedSummary(AiInterviewRepository.InterviewSessionRow session) {
        return new InterviewSummaryResponse(
                session.summaryOverallScore() == null ? 0 : session.summaryOverallScore(),
                readStringList(session.summaryStrengthsJson()),
                readStringList(session.summaryWeaknessesJson()),
                readStringList(session.summarySuggestionsJson()),
                new AiMetaPayload(
                        "INTERVIEW_SUMMARY",
                        session.summaryProvider(),
                        session.summaryModel(),
                        session.summaryLatencyMs() == null ? 0L : session.summaryLatencyMs()
                ),
                PASS_MODERATION
        );
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (IOException ex) {
            throw new ApiException("AI-2001", "history payload invalid", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new ApiException("AI-2001", "history payload invalid", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String buildTextToSpeechResultSummary(TextToSpeechResponse response) {
        return "voice=" + safe(response.voiceName())
                + ",sampleRate=" + response.sampleRate()
                + ",textLength=" + safe(response.text()).length();
    }

    private String buildInterviewTranscriptionSummary(String transcript) {
        String normalized = safe(transcript).replace('\n', ' ').trim();
        if (normalized.length() > 80) {
            return normalized.substring(0, 80) + "...";
        }
        return normalized;
    }

    private String buildTextToSpeechPayloadJson(TextToSpeechResponse response) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("text", response.text());
        payload.put("stylePrompt", response.stylePrompt());
        payload.put("voiceName", response.voiceName());
        payload.put("mimeType", response.mimeType());
        payload.put("sampleRate", response.sampleRate());
        payload.put("aiMeta", response.aiMeta());
        return toJson(payload);
    }

    private String buildMentorReplyDraftPayloadJson(
            String title,
            String generationMode,
            ConsultMentorReplyDraftResponse response
    ) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        payload.put("generationMode", generationMode);
        payload.put("draftReply", response.draftReply());
        payload.put("appliedInstruction", response.appliedInstruction());
        payload.put("aiMeta", response.aiMeta());
        payload.put("moderation", response.moderation());
        return toJson(payload);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void validateResumePdfFile(MultipartFile resumeFile) {
        if (resumeFile == null || resumeFile.isEmpty()) {
            throw new ApiException("BIZ-1001", "resume pdf required", HttpStatus.BAD_REQUEST);
        }
        String filename = resumeFile.getOriginalFilename();
        String contentType = resumeFile.getContentType();
        boolean pdfFilename = filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".pdf");
        boolean pdfContentType = contentType != null && contentType.toLowerCase(Locale.ROOT).contains("pdf");
        if (!pdfFilename && !pdfContentType) {
            throw new ApiException("BIZ-1001", "resume file must be pdf", HttpStatus.BAD_REQUEST);
        }
    }

    private AiMetaPayload buildMeta(String taskType, AiGatewayService.AiGatewayResult gatewayResult) {
        return new AiMetaPayload(
                taskType,
                gatewayResult.provider(),
                gatewayResult.model(),
                gatewayResult.latencyMs()
        );
    }

}
