package com.bishe.server.ai.gateway;

import com.bishe.server.ai.dto.ResumeRewriteItem;
import com.bishe.server.ai.dto.ResumeStructureItem;
import com.bishe.server.common.TimePayloads;
import com.bishe.server.common.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * AI 网关统一调用入口：支持 mock、legacy OpenAI-compatible 与 routed 多 provider 模式。
 */
@Service
public class AiGatewayService {

    private static final Logger log = LoggerFactory.getLogger(AiGatewayService.class);
    private static final List<String> DEFAULT_INTERVIEW_ANSWER_HELPER_CUE_KEYS = List.of("STAR", "METRICS", "COMPLETENESS");

    private final AiGatewayProperties properties;
    private final AiRouteResolver routeResolver;
    private final AiProviderRegistry providerRegistry;
    private final AiGatewayRuntimeSettingsService runtimeSettingsService;
    private final ObjectMapper objectMapper;

    public AiGatewayService(
            AiGatewayProperties properties,
            AiRouteResolver routeResolver,
            AiProviderRegistry providerRegistry,
            AiGatewayRuntimeSettingsService runtimeSettingsService,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.routeResolver = routeResolver;
        this.providerRegistry = providerRegistry;
        this.runtimeSettingsService = runtimeSettingsService;
        this.objectMapper = objectMapper;
    }

    public String resolveModelPreference(String modelPreference) {
        if (properties.getMode() == AiGatewayMode.OPENAI_COMPATIBLE) {
            if (modelPreference == null || modelPreference.isBlank() || isMockModelAlias(modelPreference)) {
                return safe(properties.getOpenaiCompatible().getDefaultModel()).trim();
            }
            return modelPreference.trim();
        }
        if (properties.getMode() == AiGatewayMode.MOCK) {
            return preferredModel(modelPreference, "mock-economy-model");
        }
        return modelPreference == null ? "" : modelPreference.trim();
    }

    public AiGatewayResult ping(String taskType, String scene, String modelPreference) {
        return ping(taskType, scene, modelPreference, "ALL");
    }

    public AiGatewayResult ping(String taskType, String scene, String modelPreference, String userTier) {
        logGatewayDispatch(taskType, scene, modelPreference);
        if (!useRealProvider()) {
            return buildGatewayResult(taskType, scene, safe(taskType) + ":" + safe(scene), modelPreference);
        }
        RoutedChatResult routedResult = invokeChat(
                safe(taskType).isBlank() ? "INTERVIEW_TEXT" : taskType.trim().toUpperCase(Locale.ROOT),
                normalizeSceneCode(scene),
                List.of(
                        new AiChatMessage("system", "Reply with a short JSON object: {\"ok\":true}. Do not use markdown."),
                        new AiChatMessage("user", "taskType=" + safe(taskType) + ", scene=" + safe(scene))
                ),
                modelPreference,
                userTier,
                Map.of(
                        "taskType", safe(taskType).isBlank() ? "INTERVIEW_TEXT" : taskType.trim().toUpperCase(Locale.ROOT),
                        "sceneCode", safe(scene)
                )
        );
        return toGatewayResult(routedResult.providerResult(), routedResult.invocation(), scene);
    }

    public ResumeOptimizeGatewayResult optimizeResume(
            String targetRole,
            String targetContext,
            String jobDescription,
            String resumeText,
            String modelPreference
    ) {
        return optimizeResume(targetRole, targetContext, jobDescription, resumeText, modelPreference, "ALL");
    }

    public ResumeOptimizeGatewayResult optimizeResume(
            String targetRole,
            String targetContext,
            String jobDescription,
            String resumeText,
            String modelPreference,
            String userTier
    ) {
        logGatewayDispatch("RESUME", "RESUME_OPTIMIZE", modelPreference);
        if (!useRealProvider()) {
            // mock/provider 分支在网关层收口，上层服务不需要判断当前运行模式。
            return optimizeResumeMock(targetRole, targetContext, jobDescription, resumeText, modelPreference);
        }
        RoutedChatResult routedResult = invokeChat(
                "RESUME",
                "RESUME_OPTIMIZE",
                buildResumeMessages(targetRole, targetContext, jobDescription, resumeText),
                modelPreference,
                userTier,
                Map.of(
                        "targetRole", safe(targetRole),
                        "targetContext", safe(targetContext),
                        "jobDescription", safe(jobDescription),
                        "resumeText", safe(resumeText),
                        "inputMode", "text"
                )
        );
        JsonNode payload = readProviderJson(routedResult.providerResult().content());
        return new ResumeOptimizeGatewayResult(
                textValue(payload, "summary", "简历方向较清晰，建议补充更多量化结果。"),
                stringList(payload.path("strengths"), List.of("目标岗位方向明确")),
                stringList(payload.path("risks"), List.of("缺少量化指标")),
                stringList(payload.path("suggestions"), List.of("补充 2-3 条结果导向表述")),
                normalizeScoreLabel(payload.path("scoreLabel").asText("")),
                resumeStructureItems(payload.path("structureItems")),
                resumeRewriteItems(payload.path("rewriteItems")),
                toGatewayResult(routedResult.providerResult(), routedResult.invocation(), "RESUME_OPTIMIZE")
        );
    }


    public ResumeOptimizeGatewayResult optimizeResumePdf(
            String targetRole,
            String targetContext,
            String jobDescription,
            MultipartFile resumeFile,
            String modelPreference
    ) {
        return optimizeResumePdf(targetRole, targetContext, jobDescription, resumeFile, modelPreference, "ALL");
    }

    public ResumeOptimizeGatewayResult optimizeResumePdf(
            String targetRole,
            String targetContext,
            String jobDescription,
            MultipartFile resumeFile,
            String modelPreference,
            String userTier
    ) {
        logGatewayDispatch("RESUME", "RESUME_OPTIMIZE", modelPreference);
        if (!useRealProvider()) {
            return optimizeResumePdfMock(targetRole, targetContext, jobDescription, resumeFile, modelPreference);
        }
        // PDF 简历必须走支持文件输入的专用路由，不能复用普通 chatJson 文本入口。
        AiProviderInvocation invocation = routeResolver.resolveResumePdf(
                "RESUME",
                "RESUME_OPTIMIZE",
                modelPreference,
                userTier,
                Map.of(
                        "targetRole", safe(targetRole),
                        "targetContext", safe(targetContext),
                        "jobDescription", safe(jobDescription),
                        "resumeFileName", resumeFile == null ? "" : safe(resumeFile.getOriginalFilename()),
                        "resumeMimeType", resumeFile == null ? MediaType.APPLICATION_PDF_VALUE : safe(resumeFile.getContentType()),
                        "inputMode", "pdf"
                )
        );
        logResolvedInvocation(invocation);
        AiProviderChatResult providerResult = providerRegistry.get(invocation.providerType())
                .optimizeResumePdf(invocation, targetRole, targetContext, jobDescription, resumeFile);
        JsonNode payload = readProviderJson(providerResult.content());
        return new ResumeOptimizeGatewayResult(
                textValue(payload, "summary", "简历方向较清晰，建议补充更多量化结果。"),
                stringList(payload.path("strengths"), List.of("目标岗位方向明确")),
                stringList(payload.path("risks"), List.of("缺少量化指标")),
                stringList(payload.path("suggestions"), List.of("补充 2-3 条结果导向表述")),
                normalizeScoreLabel(payload.path("scoreLabel").asText("")),
                resumeStructureItems(payload.path("structureItems")),
                resumeRewriteItems(payload.path("rewriteItems")),
                toGatewayResult(providerResult, invocation, "RESUME_OPTIMIZE")
        );
    }

    public CommunityPreAnswerGatewayResult generateCommunityPreAnswer(String title, String content, String modelPreference) {
        return generateCommunityPreAnswer(title, content, modelPreference, "ALL");
    }

    public CommunityPreAnswerGatewayResult generateCommunityPreAnswer(String title, String content, String modelPreference, String userTier) {
        logGatewayDispatch("COMMUNITY_REPLY", "COMMUNITY_PRE_ANSWER", modelPreference);
        if (!useRealProvider()) {
            return generateCommunityPreAnswerMock(title, content, modelPreference);
        }
        // 社区预回答只生成草稿文本，是否入库由社区服务再做治理和 bot 身份处理。
        RoutedChatResult routedResult = invokeChat(
                "COMMUNITY_REPLY",
                "COMMUNITY_PRE_ANSWER",
                buildCommunityPreAnswerMessages(title, content),
                modelPreference,
                userTier,
                Map.of(
                        "title", safe(title),
                        "content", safe(content)
                )
        );
        JsonNode payload = readProviderJson(routedResult.providerResult().content());
        return new CommunityPreAnswerGatewayResult(
                textValue(payload, "draftComment", "建议先明确你的目标岗位，再补充一段自己的项目背景与当前困惑，这样更容易获得高质量回复。"),
                toGatewayResult(routedResult.providerResult(), routedResult.invocation(), "COMMUNITY_PRE_ANSWER")
        );
    }

    public MentorOrderReplyDraftGatewayResult generateMentorOrderReplyDraft(
            String title,
            String context,
            String currentDraft,
            String instruction,
            String modelPreference
    ) {
        return generateMentorOrderReplyDraft(title, context, currentDraft, instruction, modelPreference, "ALL");
    }

    public MentorOrderReplyDraftGatewayResult generateMentorOrderReplyDraft(
            String title,
            String context,
            String currentDraft,
            String instruction,
            String modelPreference,
            String userTier
    ) {
        logGatewayDispatch("COMMUNITY_REPLY", "COMMUNITY_PRE_ANSWER", modelPreference);
        if (!useRealProvider()) {
            return generateMentorOrderReplyDraftMock(title, context, currentDraft, instruction, modelPreference);
        }
        RoutedChatResult routedResult = invokeChat(
                "COMMUNITY_REPLY",
                "COMMUNITY_PRE_ANSWER",
                buildMentorOrderReplyDraftMessages(title, context, currentDraft, instruction),
                modelPreference,
                userTier,
                Map.of(
                        "title", safe(title),
                        "context", safe(context),
                        "currentDraft", safe(currentDraft),
                        "instruction", safe(instruction)
                )
        );
        JsonNode payload = readProviderJson(routedResult.providerResult().content());
        return new MentorOrderReplyDraftGatewayResult(
                textValue(payload, "draftComment", "我已经看过你这次咨询的背景和目标，先给你一个可继续沟通的回复框架，你也可以告诉我最想优先解决的问题，我会再继续展开。"),
                toGatewayResult(routedResult.providerResult(), routedResult.invocation(), "COMMUNITY_PRE_ANSWER")
        );
    }

    public MentorPrepSheetGatewayResult generateMentorPrepSheet(
            String scene,
            String targetPosition,
            String mentorContext,
            String studentContext,
            String latestResumeContext,
            String latestInterviewSummaryContext,
            List<String> signalTags,
            String fallbackSummaryDraft,
            List<String> fallbackCoreQuestions,
            List<String> fallbackSuggestedMaterials,
            List<String> fallbackExpectedOutcomes,
            String modelPreference
    ) {
        return generateMentorPrepSheet(
                scene,
                targetPosition,
                mentorContext,
                studentContext,
                latestResumeContext,
                latestInterviewSummaryContext,
                signalTags,
                fallbackSummaryDraft,
                fallbackCoreQuestions,
                fallbackSuggestedMaterials,
                fallbackExpectedOutcomes,
                modelPreference,
                "ALL"
        );
    }

    public MentorPrepSheetGatewayResult generateMentorPrepSheet(
            String scene,
            String targetPosition,
            String mentorContext,
            String studentContext,
            String latestResumeContext,
            String latestInterviewSummaryContext,
            List<String> signalTags,
            String fallbackSummaryDraft,
            List<String> fallbackCoreQuestions,
            List<String> fallbackSuggestedMaterials,
            List<String> fallbackExpectedOutcomes,
            String modelPreference,
            String userTier
    ) {
        logGatewayDispatch("COMMUNITY_REPLY", "MENTOR_PREP_SHEET_GENERATE", modelPreference);
        if (!useRealProvider()) {
            return generateMentorPrepSheetMock(
                    scene,
                    targetPosition,
                    mentorContext,
                    studentContext,
                    latestResumeContext,
                    latestInterviewSummaryContext,
                    signalTags,
                    fallbackSummaryDraft,
                    fallbackCoreQuestions,
                    fallbackSuggestedMaterials,
                    fallbackExpectedOutcomes,
                    modelPreference
            );
        }
        LinkedHashMap<String, Object> promptVariables = new LinkedHashMap<>();
        promptVariables.put("scene", safe(scene));
        promptVariables.put("targetPosition", safe(targetPosition));
        promptVariables.put("mentorContext", safe(mentorContext));
        promptVariables.put("studentContext", safe(studentContext));
        promptVariables.put("latestResumeContext", safe(latestResumeContext));
        promptVariables.put("latestInterviewSummaryContext", safe(latestInterviewSummaryContext));
        promptVariables.put("signalTags", joinListForPrompt(signalTags));
        promptVariables.put("fallbackSummaryDraft", safe(fallbackSummaryDraft));
        promptVariables.put("fallbackCoreQuestions", joinListForPrompt(fallbackCoreQuestions));
        promptVariables.put("fallbackSuggestedMaterials", joinListForPrompt(fallbackSuggestedMaterials));
        promptVariables.put("fallbackExpectedOutcomes", joinListForPrompt(fallbackExpectedOutcomes));
        RoutedChatResult routedResult = invokeChat(
                "COMMUNITY_REPLY",
                "MENTOR_PREP_SHEET_GENERATE",
                buildMentorPrepSheetMessages(),
                modelPreference,
                userTier,
                promptVariables
        );
        JsonNode payload = tryReadProviderJson(routedResult.providerResult().content(), "MENTOR_PREP_SHEET_GENERATE");
        return new MentorPrepSheetGatewayResult(
                textValue(payload, "summaryDraft", fallbackSummaryDraft),
                resolveFixedLengthStringList(payload == null ? null : payload.path("coreQuestions"), fallbackCoreQuestions, 3),
                resolveStringList(payload == null ? null : payload.path("suggestedMaterials"), fallbackSuggestedMaterials),
                resolveStringList(payload == null ? null : payload.path("expectedOutcomes"), fallbackExpectedOutcomes),
                toGatewayResult(routedResult.providerResult(), routedResult.invocation(), "MENTOR_PREP_SHEET_GENERATE")
        );
    }

    public StudentPortraitSummaryGatewayResult generateStudentPortraitSummary(
            String targetPosition,
            List<String> strengthTags,
            List<String> riskTags,
            String signalLevel,
            String freshnessLevel,
            String evidenceContext,
            String fallbackHeadline,
            String fallbackSummary,
            List<String> fallbackNextActions,
            String modelPreference
    ) {
        return generateStudentPortraitSummary(
                targetPosition,
                strengthTags,
                riskTags,
                signalLevel,
                freshnessLevel,
                evidenceContext,
                fallbackHeadline,
                fallbackSummary,
                fallbackNextActions,
                modelPreference,
                "ALL"
        );
    }

    public StudentPortraitSummaryGatewayResult generateStudentPortraitSummary(
            String targetPosition,
            List<String> strengthTags,
            List<String> riskTags,
            String signalLevel,
            String freshnessLevel,
            String evidenceContext,
            String fallbackHeadline,
            String fallbackSummary,
            List<String> fallbackNextActions,
            String modelPreference,
            String userTier
    ) {
        logGatewayDispatch("PORTRAIT_SUMMARY", "STUDENT_PORTRAIT_SUMMARY", modelPreference);
        if (!useRealProvider()) {
            return generateStudentPortraitSummaryMock(
                    targetPosition,
                    strengthTags,
                    riskTags,
                    signalLevel,
                    freshnessLevel,
                    evidenceContext,
                    fallbackHeadline,
                    fallbackSummary,
                    fallbackNextActions,
                    modelPreference
            );
        }
        LinkedHashMap<String, Object> promptVariables = new LinkedHashMap<>();
        promptVariables.put("targetPosition", safe(targetPosition));
        promptVariables.put("strengthTags", joinListForPrompt(strengthTags));
        promptVariables.put("riskTags", joinListForPrompt(riskTags));
        promptVariables.put("signalLevel", safe(signalLevel));
        promptVariables.put("freshnessLevel", safe(freshnessLevel));
        promptVariables.put("evidenceContext", safe(evidenceContext));
        promptVariables.put("fallbackHeadline", safe(fallbackHeadline));
        promptVariables.put("fallbackSummary", safe(fallbackSummary));
        promptVariables.put("fallbackNextActions", joinListForPrompt(fallbackNextActions));
        RoutedChatResult routedResult = invokeChat(
                "PORTRAIT_SUMMARY",
                "STUDENT_PORTRAIT_SUMMARY",
                buildStudentPortraitSummaryMessages(),
                modelPreference,
                userTier,
                promptVariables
        );
        JsonNode payload = tryReadProviderJson(routedResult.providerResult().content(), "STUDENT_PORTRAIT_SUMMARY");
        return new StudentPortraitSummaryGatewayResult(
                textValue(payload, "headline", fallbackHeadline),
                textValue(payload, "summary", fallbackSummary),
                resolveStringList(payload == null ? null : payload.path("nextActions"), fallbackNextActions),
                toGatewayResult(routedResult.providerResult(), routedResult.invocation(), "STUDENT_PORTRAIT_SUMMARY")
        );
    }

    public IcebreakMessageGatewayResult generateIcebreakMessage(
            String mentorDisplayName,
            List<String> expertiseTags,
            String mentorBio,
            String studentGoal,
            String modelPreference
    ) {
        return generateIcebreakMessage(mentorDisplayName, expertiseTags, mentorBio, studentGoal, modelPreference, "ALL");
    }

    public IcebreakMessageGatewayResult generateIcebreakMessage(
            String mentorDisplayName,
            List<String> expertiseTags,
            String mentorBio,
            String studentGoal,
            String modelPreference,
            String userTier
    ) {
        logGatewayDispatch("ICEBREAK", "ICEBREAK_MESSAGE", modelPreference);
        if (!useRealProvider()) {
            return generateIcebreakMessageMock(mentorDisplayName, expertiseTags, mentorBio, studentGoal, modelPreference);
        }
        RoutedChatResult routedResult = invokeChat(
                "ICEBREAK",
                "ICEBREAK_MESSAGE",
                buildIcebreakMessageMessages(mentorDisplayName, expertiseTags, mentorBio, studentGoal),
                modelPreference,
                userTier,
                Map.of(
                        "mentorDisplayName", safe(mentorDisplayName),
                        "expertiseTags", String.join(", ", expertiseTags == null ? List.of() : expertiseTags),
                        "mentorBio", safe(mentorBio),
                        "studentGoal", safe(studentGoal)
                )
        );
        JsonNode payload = readProviderJson(routedResult.providerResult().content());
        return new IcebreakMessageGatewayResult(
                textValue(payload, "messageDraft", "老师您好，我正在准备相关求职方向，想结合您的经验请教几个问题，如果方便的话期待进一步沟通。"),
                toGatewayResult(routedResult.providerResult(), routedResult.invocation(), "ICEBREAK_MESSAGE")
        );
    }

    public InterviewQuestionGatewayResult createInterviewSession(String targetRole, String modelPreference) {
        return createInterviewSession(targetRole, null, null, modelPreference, "ALL");
    }

    public InterviewQuestionGatewayResult createInterviewSession(String targetRole, String resumeContext, String modelPreference) {
        return createInterviewSession(targetRole, null, resumeContext, modelPreference, "ALL");
    }

    public InterviewQuestionGatewayResult createInterviewSession(
            String targetRole,
            String sessionContext,
            String resumeContext,
            String modelPreference
    ) {
        return createInterviewSession(targetRole, sessionContext, resumeContext, modelPreference, "ALL");
    }

    public InterviewQuestionGatewayResult createInterviewSession(
            String targetRole,
            String sessionContext,
            String resumeContext,
            String modelPreference,
            String userTier
    ) {
        logGatewayDispatch("INTERVIEW_TEXT", "INTERVIEW_OPENING", modelPreference);
        if (!useRealProvider()) {
            String firstQuestion = buildOpeningQuestion(targetRole, sessionContext, resumeContext);
            return new InterviewQuestionGatewayResult(
                    firstQuestion,
                    buildGatewayResult("INTERVIEW_TEXT", "INTERVIEW_OPENING", safe(targetRole) + safe(sessionContext) + safe(resumeContext) + firstQuestion, modelPreference)
            );
        }
        RoutedChatResult routedResult = invokeChat(
                "INTERVIEW_TEXT",
                "INTERVIEW_OPENING",
                buildInterviewCreateMessages(targetRole, sessionContext, resumeContext),
                modelPreference,
                userTier,
                Map.of(
                        "targetRole", safe(targetRole),
                        "sessionContext", safe(sessionContext),
                        "resumeContext", safe(resumeContext)
                )
        );
        String fallbackFirstQuestion = buildOpeningQuestion(targetRole, sessionContext, resumeContext);
        JsonNode payload = tryReadProviderJson(routedResult.providerResult().content(), "INTERVIEW_OPENING");
        return new InterviewQuestionGatewayResult(
                resolveInterviewStructuredTextValue(payload, "firstQuestion", fallbackFirstQuestion),
                toGatewayResult(routedResult.providerResult(), routedResult.invocation(), "INTERVIEW_OPENING")
        );
    }

    public AudioTranscriptionGatewayResult transcribeInterviewAnswer(MultipartFile audioFile, String modelPreference) {
        return transcribeInterviewAnswer(audioFile, modelPreference, "ALL");
    }

    public AudioTranscriptionGatewayResult transcribeInterviewAnswer(MultipartFile audioFile, String modelPreference, String userTier) {
        logGatewayDispatch("STT", "INTERVIEW_VOICE_TRANSCRIBE", modelPreference);
        if (!useRealProvider()) {
            return transcribeInterviewAnswerMock(audioFile, modelPreference);
        }
        RoutedSpeechResult routedResult = invokeSpeech(
                "STT",
                "INTERVIEW_VOICE_TRANSCRIBE",
                audioFile,
                modelPreference,
                userTier
        );
        String transcript = safe(routedResult.providerResult().transcript()).trim();
        if (transcript.isBlank()) {
            throw new ApiException("AI-2102", "语音转写失败，请重新录音或改用文字模式。", HttpStatus.BAD_GATEWAY);
        }
        return new AudioTranscriptionGatewayResult(
                transcript,
                toGatewayResult(routedResult.providerResult(), routedResult.invocation(), "INTERVIEW_VOICE_TRANSCRIBE")
        );
    }

    public TextToSpeechGatewayResult synthesizeSpeech(String text, String stylePrompt, String voiceName, String modelPreference) {
        return synthesizeSpeech(text, stylePrompt, voiceName, modelPreference, "ALL");
    }

    public TextToSpeechGatewayResult synthesizeSpeech(
            String text,
            String stylePrompt,
            String voiceName,
            String modelPreference,
            String userTier
    ) {
        logGatewayDispatch("TTS", "TEXT_TO_SPEECH", modelPreference);
        if (!useRealProvider()) {
            return synthesizeSpeechMock(text, stylePrompt, voiceName, modelPreference);
        }
        String normalizedText = normalizeRequiredText(text, "tts text required");
        AiRouteResolver.RoutePlan routePlan = routeResolver.resolvePlan(
                "TTS",
                "TEXT_TO_SPEECH",
                modelPreference,
                userTier,
                Map.of(
                        "text", safe(text),
                        "stylePrompt", safe(stylePrompt),
                        "voiceName", safe(voiceName)
                )
        );
        AiProviderInvocation primaryCandidate = routePlan.primaryCandidate();
        String resolvedStylePrompt = resolveRouteExtraText(
                primaryCandidate == null ? null : primaryCandidate.routeExtraConfigJson(),
                "defaultStylePrompt",
                stylePrompt,
                "Read aloud in a warm and friendly tone:"
        );
        String resolvedVoiceName = resolveRouteExtraText(
                primaryCandidate == null ? null : primaryCandidate.routeExtraConfigJson(),
                "defaultVoiceName",
                voiceName,
                "Zephyr"
        );
        RoutedTextToSpeechResult routedResult = executeTextToSpeechWithFallback(
                routePlan,
                normalizedText,
                resolvedStylePrompt,
                resolvedVoiceName
        );
        AiProviderTextToSpeechResult providerResult = routedResult.providerResult();
        String audioBase64 = safe(providerResult.audioBase64()).trim();
        if (audioBase64.isBlank()) {
            throw new ApiException("AI-2103", "语音播报生成失败，本次将先保留文字内容。", HttpStatus.BAD_GATEWAY);
        }
        return new TextToSpeechGatewayResult(
                normalizedText,
                resolvedStylePrompt,
                resolvedVoiceName,
                safe(providerResult.mimeType()).isBlank() ? "audio/L16;codec=pcm;rate=24000" : providerResult.mimeType(),
                parseAudioSampleRate(providerResult.mimeType()),
                audioBase64,
                toGatewayResult(providerResult, routedResult.invocation(), "TEXT_TO_SPEECH")
        );
    }

    public InterviewReplyGatewayResult replyInterview(String targetRole, List<String> historyLines, String currentAnswer, String modelPreference) {
        return replyInterview(targetRole, null, null, historyLines, currentAnswer, modelPreference);
    }

    public InterviewReplyGatewayResult replyInterview(
            String targetRole,
            String resumeContext,
            List<String> historyLines,
            String currentAnswer,
            String modelPreference
    ) {
        return replyInterview(targetRole, null, resumeContext, historyLines, currentAnswer, modelPreference);
    }

    public InterviewReplyGatewayResult replyInterview(
            String targetRole,
            String sessionContext,
            String resumeContext,
            List<String> historyLines,
            String currentAnswer,
            String modelPreference
    ) {
        return replyInterview(targetRole, sessionContext, resumeContext, historyLines, currentAnswer, 0, 0, modelPreference, "ALL");
    }

    public InterviewReplyGatewayResult replyInterview(
            String targetRole,
            String sessionContext,
            String resumeContext,
            List<String> historyLines,
            String currentAnswer,
            int replyRoundLimit,
            int replyRoundUsed,
            String modelPreference
    ) {
        return replyInterview(targetRole, sessionContext, resumeContext, historyLines, currentAnswer, replyRoundLimit, replyRoundUsed, modelPreference, "ALL");
    }

    public InterviewReplyGatewayResult replyInterview(
            String targetRole,
            String sessionContext,
            String resumeContext,
            List<String> historyLines,
            String currentAnswer,
            int replyRoundLimit,
            int replyRoundUsed,
            String modelPreference,
            String userTier
    ) {
        logGatewayDispatch("INTERVIEW_TEXT", "INTERVIEW_REPLY", modelPreference);
        int userAnswerCount = Math.max(1, countUserAnswers(historyLines));
        int remainingReplyRounds = remainingReplyRounds(replyRoundLimit, userAnswerCount);
        if (!useRealProvider()) {
            return replyInterviewMock(targetRole, sessionContext, resumeContext, historyLines, currentAnswer, modelPreference);
        }
        RoutedChatResult routedResult = invokeChat(
                "INTERVIEW_TEXT",
                "INTERVIEW_REPLY",
                buildInterviewReplyMessages(targetRole, sessionContext, resumeContext, historyLines, currentAnswer, replyRoundLimit, replyRoundUsed),
                modelPreference,
                userTier,
                Map.of(
                        "targetRole", safe(targetRole),
                        "sessionContext", safe(sessionContext),
                        "resumeContext", safe(resumeContext),
                        "historyLines", joinHistoryLines(historyLines),
                        "currentAnswer", safe(currentAnswer),
                        "userAnswerCount", userAnswerCount,
                        "replyRoundLimit", Math.max(replyRoundLimit, 0),
                        "replyRoundUsed", Math.max(replyRoundUsed, 0),
                        "remainingReplyRounds", remainingReplyRounds
                )
        );
        return buildInterviewReplyGatewayResult(targetRole, sessionContext, historyLines, currentAnswer, routedResult);
    }

    public InterviewReplyGatewayResult streamInterviewReply(
            String targetRole,
            List<String> historyLines,
            String currentAnswer,
            String modelPreference,
            Consumer<String> followUpDeltaConsumer
    ) {
        return streamInterviewReply(targetRole, null, null, historyLines, currentAnswer, modelPreference, followUpDeltaConsumer, "ALL");
    }

    public InterviewReplyGatewayResult streamInterviewReply(
            String targetRole,
            String resumeContext,
            List<String> historyLines,
            String currentAnswer,
            String modelPreference,
            Consumer<String> followUpDeltaConsumer
    ) {
        return streamInterviewReply(targetRole, null, resumeContext, historyLines, currentAnswer, modelPreference, followUpDeltaConsumer, "ALL");
    }

    public InterviewReplyGatewayResult streamInterviewReply(
            String targetRole,
            String sessionContext,
            String resumeContext,
            List<String> historyLines,
            String currentAnswer,
            String modelPreference,
            Consumer<String> followUpDeltaConsumer
    ) {
        return streamInterviewReply(
                targetRole,
                sessionContext,
                resumeContext,
                historyLines,
                currentAnswer,
                0,
                0,
                modelPreference,
                followUpDeltaConsumer,
                "ALL"
        );
    }

    public InterviewReplyGatewayResult streamInterviewReply(
            String targetRole,
            String sessionContext,
            String resumeContext,
            List<String> historyLines,
            String currentAnswer,
            String modelPreference,
            Consumer<String> followUpDeltaConsumer,
            String userTier
    ) {
        return streamInterviewReply(
                targetRole,
                sessionContext,
                resumeContext,
                historyLines,
                currentAnswer,
                0,
                0,
                modelPreference,
                followUpDeltaConsumer,
                userTier
        );
    }

    public InterviewReplyGatewayResult streamInterviewReply(
            String targetRole,
            String sessionContext,
            String resumeContext,
            List<String> historyLines,
            String currentAnswer,
            int replyRoundLimit,
            int replyRoundUsed,
            String modelPreference,
            Consumer<String> followUpDeltaConsumer
    ) {
        return streamInterviewReply(
                targetRole,
                sessionContext,
                resumeContext,
                historyLines,
                currentAnswer,
                replyRoundLimit,
                replyRoundUsed,
                modelPreference,
                followUpDeltaConsumer,
                "ALL"
        );
    }

    public InterviewReplyGatewayResult streamInterviewReply(
            String targetRole,
            String sessionContext,
            String resumeContext,
            List<String> historyLines,
            String currentAnswer,
            int replyRoundLimit,
            int replyRoundUsed,
            String modelPreference,
            Consumer<String> followUpDeltaConsumer,
            String userTier
    ) {
        logGatewayDispatch("INTERVIEW_TEXT", "INTERVIEW_REPLY", modelPreference);
        int userAnswerCount = Math.max(1, countUserAnswers(historyLines));
        int remainingReplyRounds = remainingReplyRounds(replyRoundLimit, userAnswerCount);
        if (!useRealProvider()) {
            InterviewReplyGatewayResult result = replyInterviewMock(targetRole, sessionContext, resumeContext, historyLines, currentAnswer, modelPreference);
            if (followUpDeltaConsumer != null && result.followUpQuestion() != null && !result.followUpQuestion().isBlank()) {
                followUpDeltaConsumer.accept(result.followUpQuestion());
            }
            return result;
        }
        JsonFieldStreamAccumulator followUpAccumulator = new JsonFieldStreamAccumulator("followUpQuestion");
        // provider 流式返回通常是 JSON 片段，前端只需要 followUpQuestion 的可见增量。
        RoutedChatResult routedResult = invokeChatStream(
                "INTERVIEW_TEXT",
                "INTERVIEW_REPLY",
                buildInterviewReplyMessages(targetRole, sessionContext, resumeContext, historyLines, currentAnswer, replyRoundLimit, replyRoundUsed),
                modelPreference,
                userTier,
                Map.of(
                        "targetRole", safe(targetRole),
                        "sessionContext", safe(sessionContext),
                        "resumeContext", safe(resumeContext),
                        "historyLines", joinHistoryLines(historyLines),
                        "currentAnswer", safe(currentAnswer),
                        "userAnswerCount", userAnswerCount,
                        "replyRoundLimit", Math.max(replyRoundLimit, 0),
                        "replyRoundUsed", Math.max(replyRoundUsed, 0),
                        "remainingReplyRounds", remainingReplyRounds
                ),
                rawDelta -> {
                    if (followUpDeltaConsumer == null || rawDelta == null || rawDelta.isBlank()) {
                        return;
                    }
                    String visibleDelta = followUpAccumulator.append(rawDelta);
                    if (!visibleDelta.isBlank()) {
                        followUpDeltaConsumer.accept(visibleDelta);
                        }
                }
        );
        return buildInterviewReplyGatewayResult(targetRole, sessionContext, historyLines, currentAnswer, routedResult);
    }

    public InterviewSummaryGatewayResult summarizeInterview(String targetRole, List<String> historyLines, String modelPreference) {
        return summarizeInterview(targetRole, null, null, historyLines, modelPreference, "ALL");
    }

    public InterviewSummaryGatewayResult summarizeInterview(
            String targetRole,
            String resumeContext,
            List<String> historyLines,
            String modelPreference
    ) {
        return summarizeInterview(targetRole, null, resumeContext, historyLines, modelPreference, "ALL");
    }

    public InterviewSummaryGatewayResult summarizeInterview(
            String targetRole,
            String sessionContext,
            String resumeContext,
            List<String> historyLines,
            String modelPreference
    ) {
        return summarizeInterview(targetRole, sessionContext, resumeContext, historyLines, modelPreference, "ALL");
    }

    public InterviewSummaryGatewayResult summarizeInterview(
            String targetRole,
            String sessionContext,
            String resumeContext,
            List<String> historyLines,
            String modelPreference,
            String userTier
    ) {
        logGatewayDispatch("INTERVIEW_SUMMARY", "INTERVIEW_SUMMARY", modelPreference);
        if (!useRealProvider()) {
            return summarizeInterviewMock(targetRole, sessionContext, resumeContext, historyLines, modelPreference);
        }
        RoutedChatResult routedResult = invokeChat(
                "INTERVIEW_SUMMARY",
                "INTERVIEW_SUMMARY",
                buildInterviewSummaryMessages(targetRole, sessionContext, resumeContext, historyLines),
                modelPreference,
                userTier,
                Map.of(
                        "targetRole", safe(targetRole),
                        "sessionContext", safe(sessionContext),
                        "resumeContext", safe(resumeContext),
                        "historyLines", joinHistoryLines(historyLines)
                )
        );
        JsonNode payload = readProviderJson(routedResult.providerResult().content());
        List<String> fallbackStrengths = defaultSummaryStrengths(historyLines);
        List<String> fallbackWeaknesses = defaultSummaryWeaknesses(historyLines);
        List<String> fallbackSuggestions = defaultSummarySuggestions(historyLines);
        return new InterviewSummaryGatewayResult(
                clamp(intValue(payload, "overallScore", summarizeScore(historyLines)), 50, 100),
                preferChineseInterviewList(stringList(payload.path("strengths"), fallbackStrengths), fallbackStrengths),
                preferChineseInterviewList(stringList(payload.path("weaknesses"), fallbackWeaknesses), fallbackWeaknesses),
                preferChineseInterviewList(stringList(payload.path("suggestions"), fallbackSuggestions), fallbackSuggestions),
                toGatewayResult(routedResult.providerResult(), routedResult.invocation(), "INTERVIEW_SUMMARY")
        );
    }

    public InterviewAnswerHelperGatewayResult analyzeInterviewAnswerHelper(
            String targetRole,
            String sessionContext,
            String resumeContext,
            List<String> historyLines,
            String currentAnswer,
            List<String> cueKeys,
            String modelPreference
    ) {
        return analyzeInterviewAnswerHelper(targetRole, sessionContext, resumeContext, historyLines, currentAnswer, cueKeys, modelPreference, "ALL");
    }

    public InterviewAnswerHelperGatewayResult analyzeInterviewAnswerHelper(
            String targetRole,
            String sessionContext,
            String resumeContext,
            List<String> historyLines,
            String currentAnswer,
            List<String> cueKeys,
            String modelPreference,
            String userTier
    ) {
        logGatewayDispatch("INTERVIEW_TEXT", "INTERVIEW_ANSWER_HELPER", modelPreference);
        List<String> normalizedCueKeys = normalizeInterviewAnswerHelperCueKeys(cueKeys);
        InterviewAnswerHelperHeuristic heuristic = buildInterviewAnswerHelperHeuristic(currentAnswer, normalizedCueKeys);
        if (!useRealProvider()) {
            return new InterviewAnswerHelperGatewayResult(
                    heuristic.overallLevel(),
                    heuristic.overallSummary(),
                    heuristic.items(),
                    heuristic.details(),
                    buildGatewayResult(
                            "INTERVIEW_TEXT",
                            "INTERVIEW_ANSWER_HELPER",
                            safe(targetRole) + safe(sessionContext) + safe(resumeContext) + joinHistoryLines(historyLines) + safe(currentAnswer),
                            modelPreference
                    )
            );
        }
        RoutedChatResult routedResult = invokeChat(
                "INTERVIEW_TEXT",
                "INTERVIEW_ANSWER_HELPER",
                buildInterviewAnswerHelperMessages(targetRole, sessionContext, resumeContext, historyLines, currentAnswer, normalizedCueKeys),
                modelPreference,
                userTier,
                Map.of(
                        "targetRole", safe(targetRole),
                        "sessionContext", safe(sessionContext),
                        "resumeContext", safe(resumeContext),
                        "answerHelperCueKeys", String.join(", ", normalizedCueKeys),
                        "historyLines", joinHistoryLines(historyLines),
                        "currentAnswer", safe(currentAnswer)
                )
        );
        AiGatewayResult gatewayResult = toGatewayResult(routedResult.providerResult(), routedResult.invocation(), "INTERVIEW_ANSWER_HELPER");
        try {
            JsonNode payload = readProviderJson(routedResult.providerResult().content());
            return new InterviewAnswerHelperGatewayResult(
                    normalizeInterviewAnswerHelperLevel(textValue(payload, "overallLevel", heuristic.overallLevel()), heuristic.overallLevel()),
                    preferChineseInterviewText(textValue(payload, "overallSummary", heuristic.overallSummary()), heuristic.overallSummary()),
                    interviewAnswerHelperItems(payload.path("items"), heuristic.items()),
                    preferChineseInterviewList(stringList(payload.path("details"), heuristic.details()), heuristic.details()),
                    gatewayResult
            );
        } catch (RuntimeException ex) {
            log.warn("ai interview answer helper fallback applied error={}", ex.getMessage());
            return new InterviewAnswerHelperGatewayResult(
                    heuristic.overallLevel(),
                    heuristic.overallSummary(),
                    heuristic.items(),
                    heuristic.details(),
                    gatewayResult
            );
        }
    }

    private RoutedChatResult invokeChat(
            String taskType,
            String sceneCode,
            List<AiChatMessage> messages,
            String modelPreference,
            String userTier,
            Map<String, Object> promptVariables
    ) {
        AiRouteResolver.RoutePlan routePlan = routeResolver.resolvePlan(taskType, sceneCode, modelPreference, userTier, promptVariables);
        return executeChatWithPlan(routePlan, messages, false, null);
    }

    private RoutedChatResult invokeChatStream(
            String taskType,
            String sceneCode,
            List<AiChatMessage> messages,
            String modelPreference,
            String userTier,
            Map<String, Object> promptVariables,
            Consumer<String> deltaConsumer
    ) {
        AiRouteResolver.RoutePlan routePlan = routeResolver.resolvePlan(taskType, sceneCode, modelPreference, userTier, promptVariables);
        return executeChatWithPlan(routePlan, messages, true, deltaConsumer);
    }

    private RoutedChatResult executeChatWithPlan(
            AiRouteResolver.RoutePlan routePlan,
            List<AiChatMessage> messages,
            boolean streaming,
            Consumer<String> deltaConsumer
    ) {
        List<AiProviderInvocation> candidates = orderCandidatesForExecution(
                requireRouteCandidates(routePlan),
                routePlan == null ? null : routePlan.strategyType()
        );
        // 按策略顺序逐个尝试候选路由，失败后再交给下一条候选做 failover。
        RuntimeException lastException = null;
        for (AiProviderInvocation candidate : candidates) {
            try {
                return executeChatCandidate(candidate, messages, streaming, deltaConsumer);
            } catch (RuntimeException ex) {
                lastException = ex;
                logRouteAttemptFailure(routePlan, candidate, ex);
                if (candidates.size() == 1) {
                    throw ex;
                }
            }
        }
        if (lastException != null) {
            throw lastException;
        }
        throw new ApiException("AI-2001", "route config missing", HttpStatus.BAD_GATEWAY);
    }

    private AiProviderInvocation ensureStreamInvocation(AiProviderInvocation invocation) {
        if (invocation == null
                || invocation.executionMode() == AiExecutionMode.STREAM_SSE
                || invocation.executionMode() == AiExecutionMode.REALTIME_SESSION) {
            return invocation;
        }
        // 文本路由可临时提升为 STREAM_SSE，保留 provider/model/prompt 等配置不变。
        return new AiProviderInvocation(
                invocation.taskType(),
                invocation.sceneCode(),
                invocation.routeCode(),
                AiExecutionMode.STREAM_SSE,
                invocation.providerCode(),
                invocation.providerDisplayName(),
                invocation.providerType(),
                invocation.baseUrl(),
                invocation.apiKey(),
                invocation.model(),
                invocation.timeout(),
                invocation.maxRetries(),
                invocation.costPer1kInput(),
                invocation.costPer1kOutput(),
                invocation.temperature(),
                invocation.systemPrompt(),
                invocation.promptTemplateName(),
                invocation.promptTemplateVersionNo(),
                invocation.promptTemplateFormat(),
                invocation.promptTemplateBundleJson(),
                invocation.promptSeedMessages(),
                invocation.thinkingConfig(),
                invocation.providerExtraConfigJson(),
                invocation.routeExtraConfigJson(),
                invocation.routePolicyCode(),
                invocation.routePolicyUserTier(),
                invocation.routeStrategyType(),
                invocation.candidateWeight()
        );
    }

    private RoutedChatResult executeChatCandidate(
            AiProviderInvocation invocation,
            List<AiChatMessage> messages,
            boolean streaming,
            Consumer<String> deltaConsumer
    ) {
        AiProviderInvocation effectiveInvocation = streaming ? ensureStreamInvocation(invocation) : invocation;
        logResolvedInvocation(effectiveInvocation);
        // 模板 seed messages 和业务 messages 在真正调用 provider 前合并，保持 invocation 可追踪。
        List<AiChatMessage> mergedMessages = mergePromptSeedMessages(effectiveInvocation, messages);
        AiProviderChatResult providerResult = streaming
                ? providerRegistry.get(effectiveInvocation.providerType()).chatJsonStream(effectiveInvocation, mergedMessages, deltaConsumer)
                : providerRegistry.get(effectiveInvocation.providerType()).chatJson(effectiveInvocation, mergedMessages);
        return new RoutedChatResult(effectiveInvocation, providerResult);
    }

    private RoutedSpeechResult invokeSpeech(
            String taskType,
            String sceneCode,
            MultipartFile audioFile,
            String modelPreference,
            String userTier
    ) {
        AiRouteResolver.RoutePlan routePlan = routeResolver.resolvePlan(taskType, sceneCode, modelPreference, userTier);
        List<AiProviderInvocation> candidates = orderCandidatesForExecution(
                requireRouteCandidates(routePlan),
                routePlan.strategyType()
        );
        RuntimeException lastException = null;
        for (AiProviderInvocation candidate : candidates) {
            try {
                logResolvedInvocation(candidate);
                AiProviderSpeechResult providerResult = providerRegistry.get(candidate.providerType()).transcribeAudio(candidate, audioFile);
                return new RoutedSpeechResult(candidate, providerResult);
            } catch (RuntimeException ex) {
                lastException = ex;
                logRouteAttemptFailure(routePlan, candidate, ex);
                if (candidates.size() == 1) {
                    throw ex;
                }
            }
        }
        if (lastException != null) {
            throw lastException;
        }
        throw new ApiException("AI-2001", "route config missing", HttpStatus.BAD_GATEWAY);
    }

    private RoutedTextToSpeechResult executeTextToSpeechWithFallback(
            AiRouteResolver.RoutePlan routePlan,
            String text,
            String stylePrompt,
            String voiceName
    ) {
        List<AiProviderInvocation> candidates = orderCandidatesForExecution(
                requireRouteCandidates(routePlan),
                routePlan == null ? null : routePlan.strategyType()
        );
        RuntimeException lastException = null;
        for (AiProviderInvocation candidate : candidates) {
            try {
                logResolvedInvocation(candidate);
                AiProviderTextToSpeechResult providerResult = providerRegistry.get(candidate.providerType())
                        .synthesizeSpeech(candidate, text, stylePrompt, voiceName);
                return new RoutedTextToSpeechResult(candidate, providerResult);
            } catch (RuntimeException ex) {
                lastException = ex;
                logRouteAttemptFailure(routePlan, candidate, ex);
                if (candidates.size() == 1) {
                    throw ex;
                }
            }
        }
        if (lastException != null) {
            throw lastException;
        }
        throw new ApiException("AI-2001", "route config missing", HttpStatus.BAD_GATEWAY);
    }

    private List<AiProviderInvocation> requireRouteCandidates(AiRouteResolver.RoutePlan routePlan) {
        if (routePlan == null || routePlan.candidates() == null || routePlan.candidates().isEmpty()) {
            throw new ApiException("AI-2001", "route config missing", HttpStatus.BAD_GATEWAY);
        }
        return routePlan.candidates();
    }

    private List<AiProviderInvocation> orderCandidatesForExecution(List<AiProviderInvocation> candidates, String strategyType) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        if (candidates.size() == 1) {
            return List.copyOf(candidates);
        }
        String normalizedStrategy = normalizeStrategy(strategyType);
        if (!AiRouteResolver.AiRouteStrategyType.WEIGHTED.name().equals(normalizedStrategy)) {
            // SINGLE/FAILOVER 保持后台配置顺序，WEIGHTED 只把抽中的候选放到首位。
            return List.copyOf(candidates);
        }
        AiProviderInvocation selected = selectWeightedCandidate(candidates);
        ArrayList<AiProviderInvocation> ordered = new ArrayList<>(candidates.size());
        ordered.add(selected);
        for (AiProviderInvocation candidate : candidates) {
            if (candidate != selected) {
                ordered.add(candidate);
            }
        }
        return List.copyOf(ordered);
    }

    private AiProviderInvocation selectWeightedCandidate(List<AiProviderInvocation> candidates) {
        int totalWeight = 0;
        for (AiProviderInvocation candidate : candidates) {
            totalWeight += Math.max(candidate == null ? 0 : candidate.candidateWeight(), 1);
        }
        int pick = ThreadLocalRandom.current().nextInt(Math.max(totalWeight, 1));
        int cursor = 0;
        for (AiProviderInvocation candidate : candidates) {
            int weight = Math.max(candidate == null ? 0 : candidate.candidateWeight(), 1);
            cursor += weight;
            if (pick < cursor) {
                return candidate;
            }
        }
        return candidates.getFirst();
    }

    private String normalizeStrategy(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return AiRouteResolver.AiRouteStrategyType.SINGLE.name();
        }
        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
        if (AiRouteResolver.AiRouteStrategyType.FAILOVER.name().equals(normalized)
                || AiRouteResolver.AiRouteStrategyType.WEIGHTED.name().equals(normalized)) {
            return normalized;
        }
        return AiRouteResolver.AiRouteStrategyType.SINGLE.name();
    }

    private void logRouteAttemptFailure(
            AiRouteResolver.RoutePlan routePlan,
            AiProviderInvocation candidate,
            RuntimeException ex
    ) {
        if (routePlan == null || candidate == null) {
            return;
        }
        log.warn(
                "ai gateway candidate failed strategy={}, routePolicyCode={}, routeCode={}, provider={}, model={}, error={}",
                safe(routePlan.strategyType()),
                safe(routePlan.routePolicyCode()),
                safe(candidate.routeCode()),
                safe(candidate.providerCode()),
                safe(candidate.model()),
                ex.getMessage()
        );
    }

    private List<AiChatMessage> mergePromptSeedMessages(AiProviderInvocation invocation, List<AiChatMessage> messages) {
        List<AiChatMessage> promptSeedMessages = invocation.promptSeedMessages() == null ? List.of() : invocation.promptSeedMessages();
        List<AiChatMessage> requestMessages = messages == null ? List.of() : messages;
        boolean hasPromptSeeds = !promptSeedMessages.isEmpty();
        boolean hasMessages = !requestMessages.isEmpty();
        if (!hasPromptSeeds && !hasMessages) {
            return List.of();
        }
        if (!hasPromptSeeds) {
            return requestMessages;
        }
        if (!hasMessages) {
            return promptSeedMessages;
        }
        ArrayList<AiChatMessage> merged = new ArrayList<>(promptSeedMessages.size() + requestMessages.size());
        merged.addAll(promptSeedMessages);
        merged.addAll(requestMessages);
        return List.copyOf(merged);
    }


    private String resolveRouteExtraText(String extraConfigJson, String fieldName, String requestedValue, String fallback) {
        if (requestedValue != null && !requestedValue.isBlank()) {
            return requestedValue.trim();
        }
        if (extraConfigJson != null && !extraConfigJson.isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(extraConfigJson);
                String configured = node.path(fieldName).asText("").trim();
                if (!configured.isBlank()) {
                    return configured;
                }
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    private int parseAudioSampleRate(String mimeType) {
        String normalized = safe(mimeType).toLowerCase(Locale.ROOT);
        int index = normalized.indexOf("rate=");
        if (index < 0) {
            return 24000;
        }
        int start = index + 5;
        int end = start;
        while (end < normalized.length() && Character.isDigit(normalized.charAt(end))) {
            end++;
        }
        try {
            return Integer.parseInt(normalized.substring(start, end));
        } catch (Exception ex) {
            return 24000;
        }
    }

    private String normalizeRequiredText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ApiException("BIZ-1001", message, HttpStatus.BAD_REQUEST);
        }
        return value.trim();
    }

    private InterviewReplyGatewayResult buildInterviewReplyGatewayResult(
            String targetRole,
            String sessionContext,
            List<String> historyLines,
            String currentAnswer,
            RoutedChatResult routedResult
    ) {
        AiProviderChatResult providerResult = routedResult.providerResult();
        int userAnswerCount = Math.max(1, countUserAnswers(historyLines));
        JsonNode payload = tryReadProviderJson(providerResult.content(), "INTERVIEW_REPLY");
        int scoreHint = clamp(intValue(payload, "scoreHint", evaluateAnswerScore(currentAnswer, userAnswerCount)), 40, 98);
        boolean shouldFinish = hasExplicitBooleanField(payload, "shouldFinish")
                ? payload.path("shouldFinish").asBoolean()
                : false;
        String fallbackFollowUpQuestion = shouldFinish
                ? buildFinishStatement(targetRole, sessionContext)
                : buildFollowUpQuestion(currentAnswer, targetRole, sessionContext, scoreHint);
        String fallbackCoachFeedback = buildCoachFeedback(currentAnswer, sessionContext, scoreHint);
        String fallbackFinishReason = shouldFinish ? buildFinishReason(userAnswerCount, scoreHint) : null;
        String finishReason = shouldFinish
                ? resolveInterviewStructuredTextValue(payload, "finishReason", fallbackFinishReason)
                : null;
        return new InterviewReplyGatewayResult(
                resolveInterviewStructuredTextValue(payload, "followUpQuestion", fallbackFollowUpQuestion),
                resolveInterviewStructuredTextValue(payload, "coachFeedback", fallbackCoachFeedback),
                scoreHint,
                shouldFinish,
                finishReason,
                toGatewayResult(providerResult, routedResult.invocation(), "INTERVIEW_REPLY")
        );
    }

    private String joinHistoryLines(List<String> historyLines) {
        if (historyLines == null || historyLines.isEmpty()) {
            return "";
        }
        return String.join("\n", historyLines);
    }


    private ResumeOptimizeGatewayResult optimizeResumePdfMock(
            String targetRole,
            String targetContext,
            String jobDescription,
            MultipartFile resumeFile,
            String modelPreference
    ) {
        String filename = resumeFile == null || resumeFile.getOriginalFilename() == null || resumeFile.getOriginalFilename().isBlank()
                ? "resume.pdf"
                : resumeFile.getOriginalFilename().trim();
        String syntheticResumeText = "pdfFilename=" + filename + "\nresumeType=PDF";
        return optimizeResumeMock(targetRole, targetContext, jobDescription, syntheticResumeText, modelPreference);
    }

    private ResumeOptimizeGatewayResult optimizeResumeMock(
            String targetRole,
            String targetContext,
            String jobDescription,
            String resumeText,
            String modelPreference
    ) {
        String normalizedRole = safe(targetRole).isBlank() ? "目标岗位" : targetRole.trim();
        String normalizedText = safe(resumeText);
        String normalizedContext = safe(targetContext).trim();
        String normalizedJobDescription = safe(jobDescription).trim();
        boolean hasNumbers = containsDigits(normalizedText);
        boolean hasTech = containsAny(normalizedText, "架构", "性能", "缓存", "数据库", "并发", "监控", "指标", "benchmark", "latency", "database", "cache");
        List<String> strengths = new ArrayList<>();
        if (hasNumbers) {
            strengths.add("包含量化结果，能更直观体现业务价值");
        }
        if (hasTech) {
            strengths.add("技术方案描述较贴近 " + normalizedRole + " 岗位关注点");
        }
        if (!normalizedContext.isBlank()) {
            strengths.add("已带入“" + normalizedContext + "”语境，有利于输出更聚焦的建议");
        }
        if (strengths.isEmpty()) {
            strengths.add("经历方向与目标岗位存在一定相关性");
        }
        List<String> risks = new ArrayList<>();
        if (!hasNumbers) {
            risks.add("缺少量化指标，建议补充性能、成本或效率结果");
        }
        if (!hasTech) {
            risks.add("技术方案细节不足，建议说明核心设计与权衡");
        }
        if (!normalizedJobDescription.isBlank() && !containsAny(normalizedText + "\n" + normalizedJobDescription, "职责", "负责", "ownership", "owner")) {
            risks.add("与岗位 JD 的职责映射不够明确，建议补一条直接对应岗位要求的经历");
        }
        if (normalizedText.length() < 80) {
            risks.add("项目描述偏短，可补充职责边界与个人贡献");
        }
        if (risks.isEmpty()) {
            risks.add("可进一步突出个人主导度与复用价值");
        }
        List<String> suggestions = new ArrayList<>();
        suggestions.add("按“背景-行动-结果”重写一条与 " + normalizedRole + " 最相关的项目经历");
        suggestions.add(hasNumbers ? "保留已有数据，并补充验证方式或对比基线" : "补充至少 2 个量化结果，例如延迟、转化率或成本变化");
        suggestions.add(hasTech ? "增加一条技术取舍或稳定性治理细节" : "补充核心技术方案、难点和排障过程");
        String summary = hasNumbers && hasTech
                ? "简历内容与 " + normalizedRole + " 匹配度较好，建议继续强化个人主导贡献与复盘深度。"
                : "简历已有一定岗位相关性，但仍建议围绕量化结果与技术细节进一步打磨。";
        return new ResumeOptimizeGatewayResult(
                summary,
                strengths,
                risks,
                suggestions,
                null,
                List.of(),
                List.of(),
                buildGatewayResult("RESUME", "RESUME_OPTIMIZE", normalizedRole + normalizedContext + normalizedJobDescription + normalizedText, modelPreference)
        );
    }

    private CommunityPreAnswerGatewayResult generateCommunityPreAnswerMock(String title, String content, String modelPreference) {
        String normalizedTitle = safe(title).trim();
        String normalizedContent = safe(content).trim();
        String focus = containsAny(normalizedContent.toLowerCase(Locale.ROOT), "java", "spring", "后端", "mysql", "redis")
                ? "可以先围绕岗位核心技术栈、项目量化结果和你的具体卡点展开讨论。"
                : "可以先补充目标方向、当前基础和你最想解决的问题，方便大家给出更有针对性的建议。";
        String intro = (normalizedTitle.isBlank() ? "这类问题" : "你这个帖子《" + normalizedTitle + "》")
                + "很适合先从问题拆解开始。";
        String supplement = normalizedContent.length() > 60
                ? "也建议补充一段你已经尝试过的方法和结果。"
                : "如果能再补一段背景信息，回复会更聚焦。";
        String draft = """
                ### 可参考的回复思路

                %s

                #### 可以继续补充
                - %s
                - %s

                > 你可以结合自己的真实情况继续删改，再决定是否发送。
                """.formatted(intro, focus, supplement).trim();
        return new CommunityPreAnswerGatewayResult(
                draft,
                buildGatewayResult("COMMUNITY_REPLY", "COMMUNITY_PRE_ANSWER", normalizedTitle + normalizedContent, modelPreference)
        );
    }

    private IcebreakMessageGatewayResult generateIcebreakMessageMock(
            String mentorDisplayName,
            List<String> expertiseTags,
            String mentorBio,
            String studentGoal,
            String modelPreference
    ) {
        String mentorName = safe(mentorDisplayName).isBlank() ? "老师" : mentorDisplayName.trim();
        String tags = expertiseTags == null || expertiseTags.isEmpty() ? "相关方向" : String.join("、", expertiseTags);
        String goal = safe(studentGoal).isBlank() ? "想请教求职准备建议" : studentGoal.trim();
        String bioHint = safe(mentorBio).isBlank() ? "" : " 我看到您在" + tags + "方面经验丰富。";
        String messageDraft = mentorName + "您好，我正在准备" + goal + "。"
                + bioHint
                + " 如果您方便的话，想向您请教 1-2 个关于准备路径和项目表达的问题，期待与您进一步交流。";
        return new IcebreakMessageGatewayResult(
                messageDraft,
                buildGatewayResult("ICEBREAK", "ICEBREAK_MESSAGE", mentorName + tags + goal + safe(mentorBio), modelPreference)
        );
    }

    private MentorOrderReplyDraftGatewayResult generateMentorOrderReplyDraftMock(
            String title,
            String context,
            String currentDraft,
            String instruction,
            String modelPreference
    ) {
        String normalizedCurrentDraft = safe(currentDraft).trim();
        String normalizedInstruction = safe(instruction).trim();
        String draftReply;
        if (!normalizedCurrentDraft.isBlank()) {
            String suffix = normalizedCurrentDraft.endsWith("。") || normalizedCurrentDraft.endsWith("！") || normalizedCurrentDraft.endsWith("？")
                    ? ""
                    : "。";
            draftReply = normalizedCurrentDraft + suffix
                    + "\n\n我已经结合你当前提供的背景和目标，把表达整理得更适合直接发送。你下一条可以继续补充最想优先确认的问题、已经尝试过的方法，以及希望我重点判断的材料。";
        } else {
            String focus = safe(title).isBlank() ? "当前咨询问题" : title.trim();
            draftReply = "我已经看完你这次关于“" + focus + "”的咨询背景，先给你一个可直接继续沟通的回复框架。"
                    + "\n\n1. 我会先确认你当前最想优先解决的卡点；"
                    + "\n2. 再结合你已经提供的背景、目标和材料，给出更具体的判断与修改建议；"
                    + "\n3. 如果你愿意，也可以继续补充简历、JD 或项目细节，我会据此把建议再拆得更细。"
                    + "\n\n你可以先告诉我你最担心的一点，我下一条会直接围绕那个问题展开。";
        }
        if (!normalizedInstruction.isBlank()) {
            draftReply = draftReply + "\n\n我已尽量按照“" + normalizedInstruction + "”的方向来组织这版草稿。";
        }
        return new MentorOrderReplyDraftGatewayResult(
                draftReply,
                buildGatewayResult("COMMUNITY_REPLY", "COMMUNITY_PRE_ANSWER", safe(title) + safe(context) + safe(currentDraft) + safe(instruction), modelPreference)
        );
    }

    private MentorPrepSheetGatewayResult generateMentorPrepSheetMock(
            String scene,
            String targetPosition,
            String mentorContext,
            String studentContext,
            String latestResumeContext,
            String latestInterviewSummaryContext,
            List<String> signalTags,
            String fallbackSummaryDraft,
            List<String> fallbackCoreQuestions,
            List<String> fallbackSuggestedMaterials,
            List<String> fallbackExpectedOutcomes,
            String modelPreference
    ) {
        return new MentorPrepSheetGatewayResult(
                fallbackSummaryDraft,
                resolveFixedLengthStringList(null, fallbackCoreQuestions, 3),
                resolveStringList(null, fallbackSuggestedMaterials),
                resolveStringList(null, fallbackExpectedOutcomes),
                buildGatewayResult(
                        "COMMUNITY_REPLY",
                        "MENTOR_PREP_SHEET_GENERATE",
                        safe(scene)
                                + safe(targetPosition)
                                + safe(mentorContext)
                                + safe(studentContext)
                                + safe(latestResumeContext)
                                + safe(latestInterviewSummaryContext)
                                + joinListForPrompt(signalTags),
                        modelPreference
                )
        );
    }

    private StudentPortraitSummaryGatewayResult generateStudentPortraitSummaryMock(
            String targetPosition,
            List<String> strengthTags,
            List<String> riskTags,
            String signalLevel,
            String freshnessLevel,
            String evidenceContext,
            String fallbackHeadline,
            String fallbackSummary,
            List<String> fallbackNextActions,
            String modelPreference
    ) {
        return new StudentPortraitSummaryGatewayResult(
                fallbackHeadline,
                fallbackSummary,
                resolveStringList(null, fallbackNextActions),
                buildGatewayResult(
                        "PORTRAIT_SUMMARY",
                        "STUDENT_PORTRAIT_SUMMARY",
                        safe(targetPosition)
                                + joinListForPrompt(strengthTags)
                                + joinListForPrompt(riskTags)
                                + safe(signalLevel)
                                + safe(freshnessLevel)
                                + safe(evidenceContext),
                        modelPreference
                )
        );
    }

    private AudioTranscriptionGatewayResult transcribeInterviewAnswerMock(MultipartFile audioFile, String modelPreference) {
        String filename = audioFile == null ? "audio.webm" : safe(audioFile.getOriginalFilename());
        String transcript = "我负责订单中心重构，通过缓存预热、索引优化和监控告警把接口延迟降低了 30%，并沉淀了稳定性方案。";
        return new AudioTranscriptionGatewayResult(transcript, buildGatewayResult("STT", "INTERVIEW_VOICE_TRANSCRIBE", transcript + filename, modelPreference));
    }


    private TextToSpeechGatewayResult synthesizeSpeechMock(String text, String stylePrompt, String voiceName, String modelPreference) {
        String normalizedText = normalizeRequiredText(text, "tts text required");
        String effectiveStylePrompt = (stylePrompt == null || stylePrompt.isBlank())
                ? "Read aloud in a warm and friendly tone:"
                : stylePrompt.trim();
        String effectiveVoiceName = (voiceName == null || voiceName.isBlank()) ? "Zephyr" : voiceName.trim();
        byte[] pcmBytes = new byte[24000 / 3 * 2];
        for (int sampleIndex = 0; sampleIndex < pcmBytes.length / 2; sampleIndex++) {
            short sample = (short) (Math.sin(sampleIndex / 12.0) * 9000);
            pcmBytes[sampleIndex * 2] = (byte) (sample & 0xff);
            pcmBytes[sampleIndex * 2 + 1] = (byte) ((sample >> 8) & 0xff);
        }
        return new TextToSpeechGatewayResult(
                normalizedText,
                effectiveStylePrompt,
                effectiveVoiceName,
                "audio/L16;codec=pcm;rate=24000",
                24000,
                Base64.getEncoder().encodeToString(pcmBytes),
                buildGatewayResult("TTS", "TEXT_TO_SPEECH", normalizedText + effectiveStylePrompt + effectiveVoiceName, modelPreference)
        );
    }

    private InterviewReplyGatewayResult replyInterviewMock(
            String targetRole,
            String sessionContext,
            String resumeContext,
            List<String> historyLines,
            String currentAnswer,
            String modelPreference
    ) {
        int userAnswerCount = Math.max(1, countUserAnswers(historyLines));
        int scoreHint = evaluateAnswerScore(currentAnswer, userAnswerCount);
        boolean shouldFinish = shouldFinishInterview(currentAnswer, userAnswerCount, scoreHint);
        String finishReason = shouldFinish ? buildFinishReason(userAnswerCount, scoreHint) : null;
        String followUpQuestion = shouldFinish
                ? buildFinishStatement(targetRole, sessionContext)
                : buildFollowUpQuestion(currentAnswer, targetRole, sessionContext, scoreHint);
        return new InterviewReplyGatewayResult(
                followUpQuestion,
                buildCoachFeedback(currentAnswer, sessionContext, scoreHint),
                scoreHint,
                shouldFinish,
                finishReason,
                buildGatewayResult(
                        "INTERVIEW_TEXT",
                        "INTERVIEW_REPLY",
                        safe(targetRole) + safe(sessionContext) + safe(resumeContext) + String.join("\n", historyLines) + safe(currentAnswer),
                        modelPreference
                )
        );
    }

    private InterviewSummaryGatewayResult summarizeInterviewMock(
            String targetRole,
            String sessionContext,
            String resumeContext,
            List<String> historyLines,
            String modelPreference
    ) {
        int overallScore = summarizeScore(historyLines);
        return new InterviewSummaryGatewayResult(
                overallScore,
                defaultSummaryStrengths(historyLines),
                defaultSummaryWeaknesses(historyLines),
                defaultSummarySuggestions(historyLines),
                buildGatewayResult("INTERVIEW_SUMMARY", "INTERVIEW_SUMMARY", safe(targetRole) + safe(sessionContext) + safe(resumeContext) + String.join("\n", historyLines), modelPreference)
        );
    }

    private List<AiChatMessage> buildCommunityPreAnswerMessages(String title, String content) {
        return List.of(
                new AiChatMessage(
                        "system",
                        "你是求职社区助手。请仅返回 JSON：{\"draftComment\":string}。"
                                + "草稿要友好、具体、可直接作为评论发布。"
                                + "draftComment 必须使用简体中文 Markdown 组织内容，至少包含一个三级标题和一个无序列表；"
                                + "可以适当加入加粗，但不要输出代码块，不要输出 JSON 以外的解释。"
                ),
                new AiChatMessage("user", "title=" + safe(title) + "\ncontent=" + safe(content))
        );
    }

    private List<AiChatMessage> buildMentorOrderReplyDraftMessages(
            String title,
            String context,
            String currentDraft,
            String instruction
    ) {
        String normalizedCurrentDraft = safe(currentDraft).trim();
        String generationMode = normalizedCurrentDraft.isBlank() ? "GENERATE_FROM_CONTEXT" : "POLISH_EXISTING";
        return List.of(
                new AiChatMessage(
                        "system",
                        "你是导师咨询履约助手。请仅返回 JSON：{\"draftComment\":string}。"
                                + "要求：1) 站在导师回复学生的口吻；"
                                + "2) 结合订单上下文生成或润色一版中文回复草稿；"
                                + "3) 语气专业、真诚、具体，不夸大承诺；"
                                + "4) 只输出 JSON，不要 markdown。"
                ),
                new AiChatMessage(
                        "user",
                        "generationMode=" + generationMode
                                + "\ntitle=" + safe(title)
                                + "\ncontext=\n" + safe(context)
                                + "\ncurrentDraft=\n" + safe(currentDraft)
                                + "\ninstruction=" + safe(instruction)
                )
        );
    }

    private List<AiChatMessage> buildMentorPrepSheetMessages() {
        return List.of(
                new AiChatMessage(
                        "user",
                        "请基于系统给出的导师、学生和最近 AI 上下文，输出一版可继续编辑的咨询准备单 JSON 草稿。"
                )
        );
    }

    private List<AiChatMessage> buildStudentPortraitSummaryMessages() {
        return List.of(
                new AiChatMessage(
                        "user",
                        "请只基于系统提供的画像事实，输出一版更自然但不新增事实的学生成长画像总结 JSON。"
                )
        );
    }

    private List<AiChatMessage> buildIcebreakMessageMessages(String mentorDisplayName, List<String> expertiseTags, String mentorBio, String studentGoal) {
        return List.of(
                new AiChatMessage("system", "你是导师咨询破冰助手。请仅返回 JSON：{\"messageDraft\":string}。语气真诚、简洁、礼貌，不要输出 markdown。"),
                new AiChatMessage(
                        "user",
                        "mentorDisplayName=" + safe(mentorDisplayName)
                                + "\nexpertiseTags=" + String.join(", ", expertiseTags == null ? List.of() : expertiseTags)
                                + "\nmentorBio=" + safe(mentorBio)
                                + "\nstudentGoal=" + safe(studentGoal)
                )
        );
    }

    private List<AiChatMessage> buildResumeMessages(
            String targetRole,
            String targetContext,
            String jobDescription,
            String resumeText
    ) {
        return List.of(
                new AiChatMessage(
                        "system",
                        "你是简历优化助手。请仅返回 JSON，不要输出 markdown。JSON schema: "
                                + "{\"summary\":string,\"strengths\":string[],\"risks\":string[],\"suggestions\":string[],"
                                + "\"scoreLabel\":string,"
                                + "\"structureItems\":[{\"label\":string,\"score\":number,\"tip\":string}],"
                                + "\"rewriteItems\":[{\"id\":string,\"title\":string,\"problem\":string,\"beforeText\":string,\"afterText\":string}]}"
                                + "。要求：summary 聚焦总评；strengths/risks/suggestions 按需给 1-6 条、不必凑满、最多 6 条；structureItems 给 4-6 项、score 范围 1-5；rewriteItems 给 3-4 条。"
                ),
                new AiChatMessage(
                        "user",
                        "targetRole=" + safe(targetRole)
                                + "\ntargetContext=" + safe(targetContext)
                                + "\njobDescription=\n" + safe(jobDescription)
                                + "\nresumeText=\n" + safe(resumeText)
                )
        );
    }

    private List<AiChatMessage> buildInterviewCreateMessages(String targetRole, String sessionContext, String resumeContext) {
        List<AiChatMessage> messages = new ArrayList<>();
        messages.add(new AiChatMessage(
                "system",
                "你是中文模拟面试官。请仅返回 JSON，不要输出 markdown。JSON schema: {\"firstQuestion\":string}。"
                        + "要求："
                        + "1) firstQuestion 必须像真人面试官开场，先用一句自然问候或过渡把候选人带进场景，再抛出首问；"
                        + "2) 语气自然、克制、口语化，不要写成系统提示、评分标准或教程文案；"
                        + "3) firstQuestion 必须使用简体中文表达；除公司名、技术名词或必要缩写外，不得输出整段英文或英文主导句子；"
                        + "4) 首问优先围绕岗位匹配度最高的一段真实经历，鼓励候选人讲清背景、个人动作、关键取舍和量化结果；"
                        + "5) 如果提供了 sessionContext，必须遵守其中的面试类型、风格、强度、作答方式和岗位语境；"
                        + "6) 如果提供了 resumeContext，请优先围绕其中最值得深挖的一段真实经历切入，不要替候选人补全事实。"
        ));
        messages.add(new AiChatMessage("user", "targetRole=" + safe(targetRole)));
        if (sessionContext != null && !sessionContext.isBlank()) {
            messages.add(new AiChatMessage("user", "sessionContext=\n" + safe(sessionContext)));
        }
        if (resumeContext != null && !resumeContext.isBlank()) {
            messages.add(new AiChatMessage("user", "resumeContext=\n" + safe(resumeContext)));
        }
        return messages;
    }

    private List<AiChatMessage> buildInterviewReplyMessages(
            String targetRole,
            String sessionContext,
            String resumeContext,
            List<String> historyLines,
            String currentAnswer,
            int replyRoundLimit,
            int replyRoundUsed
    ) {
        int userAnswerCount = Math.max(1, countUserAnswers(historyLines));
        int remainingReplyRounds = remainingReplyRounds(replyRoundLimit, userAnswerCount);
        List<AiChatMessage> messages = new ArrayList<>();
        messages.add(new AiChatMessage(
                "system",
                "你是中文模拟面试官兼轻量陪练。请仅返回 JSON，不要输出 markdown。"
                        + "JSON schema: {\"followUpQuestion\":string,\"coachFeedback\":string,\"scoreHint\":number,\"shouldFinish\":boolean,\"finishReason\":string}。"
                        + "要求："
                        + "1) followUpQuestion 必须像真人面试官顺着候选人的上一轮回答自然接话，保持口语化，不要像 checklist；"
                        + "2) 每次只推进 1 个重点，优先追问贡献边界、技术细节、结果验证、取舍和复盘，不要一次塞很多子问题；"
                        + "3) shouldFinish=true 时，followUpQuestion 必须像真人收尾：先简短认可，再明确说明这一轮先到这里，接下来进入总结；不要只给一句生硬结论；"
                        + "4) followUpQuestion、coachFeedback 与 finishReason 必须使用简体中文表达；除公司名、技术名词或必要缩写外，不得输出整段英文或英文主导句子；"
                        + "5) coachFeedback 用产品界面可直接展示的中文短评，包含一处亮点和一处可改进点，语气支持但不幼态；"
                        + "6) 如果提供了 sessionContext，必须遵守其中的面试类型、风格、强度、作答方式和岗位语境；"
                        + "7) 如果提供了 resumeContext，请把它当作候选人的背景补充：优先围绕其中真实经历继续追问，不要捏造简历里没有的信息；"
                        + "8) 如果提供了 replyRoundLimit / remainingReplyRounds，请把它们只当作内部规划参考：replyRoundLimit 是最大安全上限，不是必须用满的目标轮次；信息已经充分时应提前结束，剩余轮次较少时应准备自然收尾；"
                        + "9) 不要在 followUpQuestion 或 coachFeedback 中直接向候选人提到轮次数、剩余次数、安全上限等内部控制信息；"
                        + "10) 不要为了缩短轮次而过早结束。除非候选人已经讲清背景、动作、结果、验证方式与关键取舍，或者回答明显重复，否则 shouldFinish 保持 false。"
        ));
        messages.add(new AiChatMessage("user", "targetRole=" + safe(targetRole)));
        if (sessionContext != null && !sessionContext.isBlank()) {
            messages.add(new AiChatMessage("user", "sessionContext=\n" + safe(sessionContext)));
        }
        if (resumeContext != null && !resumeContext.isBlank()) {
            messages.add(new AiChatMessage("user", "resumeContext=\n" + safe(resumeContext)));
        }
        if (replyRoundLimit > 0) {
            messages.add(new AiChatMessage("user", buildInterviewRoundControlContext(
                    userAnswerCount,
                    replyRoundLimit,
                    replyRoundUsed,
                    remainingReplyRounds
            )));
        }
        if (historyLines != null && !historyLines.isEmpty()) {
            messages.add(new AiChatMessage("user", "historyLines=\n" + String.join("\n", historyLines)));
        }
        messages.add(new AiChatMessage("user", "currentAnswer=" + safe(currentAnswer)));
        return messages;
    }

    private List<AiChatMessage> buildInterviewSummaryMessages(
            String targetRole,
            String sessionContext,
            String resumeContext,
            List<String> historyLines
    ) {
        List<AiChatMessage> messages = new ArrayList<>();
        messages.add(new AiChatMessage(
                "system",
                "你是中文模拟面试总结助手。请仅返回 JSON，不要输出 markdown。"
                        + "JSON schema: {\"overallScore\":number,\"strengths\":string[],\"weaknesses\":string[],\"suggestions\":string[]}。"
                        + "要求："
                        + "1) overallScore 输出 50-100 的整数；"
                        + "2) strengths / weaknesses / suggestions 按实际情况各给 1-6 条，不必凑满，最多 6 条；语言要像真实复盘结论，直接、具体、可执行，不要空话；"
                        + "3) strengths / weaknesses / suggestions 的文本内容必须使用简体中文表达；除公司名、技术名词或必要缩写外，不得输出整段英文或英文主导句子；"
                        + "4) 总结必须基于真实对话内容，不能臆造候选人没说过的经历或结论；"
                        + "5) 如果提供了 sessionContext，请结合这轮设定判断回答是否达到了相应风格和强度预期；"
                        + "6) 如果提供了 resumeContext，请结合候选人授权带入的简历背景判断其表达是否真正讲清了经历、结果与岗位匹配。"
        ));
        messages.add(new AiChatMessage("user", "targetRole=" + safe(targetRole)));
        if (sessionContext != null && !sessionContext.isBlank()) {
            messages.add(new AiChatMessage("user", "sessionContext=\n" + safe(sessionContext)));
        }
        if (resumeContext != null && !resumeContext.isBlank()) {
            messages.add(new AiChatMessage("user", "resumeContext=\n" + safe(resumeContext)));
        }
        messages.add(new AiChatMessage("user", "historyLines=\n" + String.join("\n", historyLines == null ? List.of() : historyLines)));
        return messages;
    }

    private List<AiChatMessage> buildInterviewAnswerHelperMessages(
            String targetRole,
            String sessionContext,
            String resumeContext,
            List<String> historyLines,
            String currentAnswer,
            List<String> cueKeys
    ) {
        List<String> normalizedCueKeys = normalizeInterviewAnswerHelperCueKeys(cueKeys);
        List<AiChatMessage> messages = new ArrayList<>();
        messages.add(new AiChatMessage(
                "system",
                "你是中文面试回答辅助诊断助手。请仅返回 JSON，不要输出 markdown。"
                        + "JSON schema: {\"overallLevel\":string,\"overallSummary\":string,\"items\":[{\"key\":string,\"level\":string,\"summary\":string,\"nextAction\":string}],\"details\":string[]}。"
                        + "要求："
                        + "1) 只分析 currentAnswer 这一轮已发送回答，不要继续追问，也不要代写完整答案；"
                        + "2) overallLevel 与 items[].level 只允许使用 READY、WARN、INFO；"
                        + "3) items[].key 只允许使用 STAR、METRICS、COMPLETENESS；如果 cueKeys 为空，则默认按这三项输出；"
                        + "4) overallSummary、items[].summary、items[].nextAction 与 details 必须使用简体中文表达；除公司名、技术名词或必要缩写外，不得输出整段英文或英文主导句子；"
                        + "5) items[].summary 直接指出当前表现；items[].nextAction 只给一句可执行补充建议，不要编造候选人没说过的事实；"
                        + "6) 可以参考 sessionContext、resumeContext 和 historyLines 理解语境，但最终只评价这轮 currentAnswer 的表达质量。"
        ));
        messages.add(new AiChatMessage("user", "targetRole=" + safe(targetRole)));
        if (sessionContext != null && !sessionContext.isBlank()) {
            messages.add(new AiChatMessage("user", "sessionContext=\n" + safe(sessionContext)));
        }
        if (resumeContext != null && !resumeContext.isBlank()) {
            messages.add(new AiChatMessage("user", "resumeContext=\n" + safe(resumeContext)));
        }
        messages.add(new AiChatMessage("user", "cueKeys=" + String.join(", ", normalizedCueKeys)));
        if (historyLines != null && !historyLines.isEmpty()) {
            messages.add(new AiChatMessage("user", "historyLines=\n" + String.join("\n", historyLines)));
        }
        messages.add(new AiChatMessage("user", "currentAnswer=" + safe(currentAnswer)));
        return messages;
    }

    private String buildInterviewRoundControlContext(
            int userAnswerCount,
            int replyRoundLimit,
            int replyRoundUsed,
            int remainingReplyRounds
    ) {
        return """
                roundControl=
                currentAnswerRound=%d
                replyRoundUsed=%d
                replyRoundLimit=%d
                remainingReplyRounds=%d
                planningRule=replyRoundLimit 只是最大安全上限，不是目标轮次；信息充分时可以提前结束；当 remainingReplyRounds 较少时请准备自然收尾。
                visibilityRule=这些轮次信息只供内部规划，不要在 followUpQuestion 或 coachFeedback 中直接说给候选人。
                """.formatted(
                Math.max(userAnswerCount, 1),
                Math.max(replyRoundUsed, 0),
                Math.max(replyRoundLimit, 0),
                Math.max(remainingReplyRounds, 0)
        ).trim();
    }

    private String preferChineseInterviewText(String candidate, String fallback) {
        String normalizedCandidate = safe(candidate).trim();
        String normalizedFallback = safe(fallback).trim();
        if (normalizedCandidate.isBlank()) {
            return normalizedFallback;
        }
        if (looksLikeStructuredPlaceholderText(normalizedCandidate)) {
            return normalizedFallback.isBlank() ? normalizedCandidate : normalizedFallback;
        }
        if (!looksLikeEnglishDominantInterviewText(normalizedCandidate)) {
            return normalizedCandidate;
        }
        return normalizedFallback.isBlank() ? normalizedCandidate : normalizedFallback;
    }

    private List<String> preferChineseInterviewList(List<String> candidate, List<String> fallback) {
        List<String> normalizedFallback = fallback == null ? List.of() : fallback;
        if (candidate == null || candidate.isEmpty()) {
            return normalizedFallback;
        }
        List<String> normalizedItems = new ArrayList<>();
        for (int index = 0; index < candidate.size(); index++) {
            String item = safe(candidate.get(index)).trim();
            if (item.isBlank()) {
                continue;
            }
            String fallbackItem;
            if (index < normalizedFallback.size()) {
                fallbackItem = normalizedFallback.get(index);
            } else if (!normalizedFallback.isEmpty()) {
                fallbackItem = normalizedFallback.get(normalizedFallback.size() - 1);
            } else {
                fallbackItem = item;
            }
            normalizedItems.add(preferChineseInterviewText(item, fallbackItem));
        }
        if (normalizedItems.isEmpty()) {
            return normalizedFallback;
        }
        return List.copyOf(normalizedItems);
    }

    private boolean looksLikeEnglishDominantInterviewText(String value) {
        int hanCount = 0;
        int latinCount = 0;
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN) {
                hanCount++;
                continue;
            }
            if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')) {
                latinCount++;
            }
        }
        if (latinCount < 12) {
            return false;
        }
        if (hanCount == 0) {
            return true;
        }
        return hanCount < 6 && latinCount >= hanCount * 4;
    }

    private boolean looksLikeStructuredPlaceholderText(String value) {
        String normalized = safe(value).trim();
        if (normalized.isBlank()) {
            return false;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (Set.of("string", "text", "number", "integer", "boolean", "object", "array", "null", "undefined").contains(lower)) {
            return true;
        }
        if ("{}".equals(normalized) || "[]".equals(normalized)) {
            return true;
        }
        int hanCount = 0;
        int latinCount = 0;
        for (int index = 0; index < normalized.length(); index++) {
            char ch = normalized.charAt(index);
            if (Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN) {
                hanCount++;
                continue;
            }
            if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')) {
                latinCount++;
            }
        }
        return hanCount == 0
                && latinCount > 0
                && latinCount <= 12
                && normalized.matches("[A-Za-z][A-Za-z0-9_\\- ]*");
    }

    private List<String> normalizeInterviewAnswerHelperCueKeys(List<String> cueKeys) {
        if (cueKeys == null || cueKeys.isEmpty()) {
            return DEFAULT_INTERVIEW_ANSWER_HELPER_CUE_KEYS;
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String cueKey : cueKeys) {
            String normalizedCueKey = normalizeInterviewAnswerHelperCueKey(cueKey, null);
            if (normalizedCueKey != null) {
                normalized.add(normalizedCueKey);
            }
        }
        return normalized.isEmpty() ? DEFAULT_INTERVIEW_ANSWER_HELPER_CUE_KEYS : List.copyOf(normalized);
    }

    private String normalizeInterviewAnswerHelperCueKey(String rawValue, String fallback) {
        String normalized = safe(rawValue).trim().toUpperCase(Locale.ROOT);
        if ("STAR".equals(normalized) || "METRICS".equals(normalized) || "COMPLETENESS".equals(normalized)) {
            return normalized;
        }
        return fallback;
    }

    private String normalizeInterviewAnswerHelperLevel(String rawValue, String fallback) {
        String normalized = safe(rawValue).trim().toUpperCase(Locale.ROOT);
        if ("READY".equals(normalized) || "WARN".equals(normalized) || "INFO".equals(normalized)) {
            return normalized;
        }
        return safe(fallback).isBlank() ? "INFO" : fallback;
    }

    private JsonNode readProviderJson(String content) {
        // provider 可能返回纯 JSON、Markdown fence 或夹杂说明文字，统一提取候选后再解析。
        List<String> candidates = buildStructuredJsonCandidates(content);
        for (String candidate : candidates) {
            JsonNode parsed = parseStructuredJsonCandidate(candidate);
            if (parsed != null) {
                return parsed;
            }
        }
        if (runtimeSettingsService.isAiRequestLogEnabled()) {
            log.warn("ai gateway structured content parse failed rawContent={}", content == null ? "" : content);
        } else {
            log.warn("ai gateway structured content parse failed contentLength={}", content == null ? 0 : content.length());
        }
        throw new ApiException("AI-2001", "provider invalid response", HttpStatus.BAD_GATEWAY);
    }

    private List<String> buildStructuredJsonCandidates(String content) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String rawContent = content == null ? "{}" : content.trim();
        if (rawContent.isBlank()) {
            candidates.add("{}");
            return new ArrayList<>(candidates);
        }
        // 候选按“原文 -> fence -> 首尾 JSON -> 平衡括号片段”递进，尽量容忍模型格式漂移。
        candidates.add(rawContent);
        String codeFenceBody = stripMarkdownCodeFence(rawContent);
        if (!codeFenceBody.isBlank()) {
            candidates.add(codeFenceBody);
        }
        String rawJsonSegment = extractJsonSegment(rawContent);
        if (!rawJsonSegment.isBlank()) {
            candidates.add(rawJsonSegment);
        }
        candidates.addAll(extractBalancedJsonSegments(rawContent));
        if (!codeFenceBody.isBlank()) {
            String fencedJsonSegment = extractJsonSegment(codeFenceBody);
            if (!fencedJsonSegment.isBlank()) {
                candidates.add(fencedJsonSegment);
            }
            candidates.addAll(extractBalancedJsonSegments(codeFenceBody));
        }
        return new ArrayList<>(candidates);
    }

    private String stripMarkdownCodeFence(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();
        if (!trimmed.startsWith("```")) {
            return "";
        }
        int firstLineBreak = trimmed.indexOf('\n');
        if (firstLineBreak < 0) {
            return "";
        }
        int closingFence = trimmed.lastIndexOf("```");
        if (closingFence <= firstLineBreak) {
            return "";
        }
        return trimmed.substring(firstLineBreak + 1, closingFence).trim();
    }

    private String extractJsonSegment(String content) {

        if (content == null || content.isBlank()) {
            return "";
        }
        int objectStart = content.indexOf('{');
        int objectEnd = content.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            return content.substring(objectStart, objectEnd + 1).trim();
        }
        int arrayStart = content.indexOf('[');
        int arrayEnd = content.lastIndexOf(']');
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return content.substring(arrayStart, arrayEnd + 1).trim();
        }
        return "";
    }

    private List<String> extractBalancedJsonSegments(String content) {
        List<String> segments = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return segments;
        }
        for (int index = 0; index < content.length(); index++) {
            char ch = content.charAt(index);
            if (ch != '{' && ch != '[') {
                continue;
            }
            String segment = extractBalancedJsonSegment(content, index);
            if (segment.isBlank()) {
                continue;
            }
            segments.add(segment);
            index = index + segment.length() - 1;
        }
        return segments;
    }

    private String extractBalancedJsonSegment(String content, int startIndex) {
        int braceDepth = 0;
        int bracketDepth = 0;
        boolean inString = false;
        boolean escaping = false;
        for (int index = startIndex; index < content.length(); index++) {
            char ch = content.charAt(index);
            if (escaping) {
                escaping = false;
                continue;
            }
            if (ch == '\\' && inString) {
                escaping = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == '{') {
                braceDepth++;
            } else if (ch == '}') {
                braceDepth--;
            } else if (ch == '[') {
                bracketDepth++;
            } else if (ch == ']') {
                bracketDepth--;
            }
            if (braceDepth == 0 && bracketDepth == 0 && index > startIndex) {
                return content.substring(startIndex, index + 1).trim();
            }
        }
        return "";
    }

    private JsonNode tryReadProviderJson(String content, String sceneCode) {
        try {
            return readProviderJson(content);
        } catch (ApiException ex) {
            log.warn(
                    "ai gateway structured parse fallback applied sceneCode={}, code={}, contentLength={}",
                    safe(sceneCode),
                    ex.getCode(),
                    content == null ? 0 : content.length()
            );
            return null;
        }
    }

    private JsonNode parseStructuredJsonCandidate(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(candidate);
            if (node != null && node.isTextual()) {
                String nested = node.asText("").trim();
                if (!nested.isBlank() && !nested.equals(candidate)) {
                    return parseStructuredJsonCandidate(nested);
                }
            }
            return node;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String textValue(JsonNode node, String fieldName, String fallback) {
        if (node == null) {
            return fallback;
        }
        String value = node.path(fieldName).asText("").trim();
        return value.isBlank() ? fallback : value;
    }

    private String resolveInterviewStructuredTextValue(JsonNode payload, String fieldName, String fallback) {
        return preferChineseInterviewText(textValue(payload, fieldName, fallback), fallback);
    }

    private int intValue(JsonNode node, String fieldName, int fallback) {
        if (node == null || node.path(fieldName).isMissingNode()) {
            return fallback;
        }
        return node.path(fieldName).asInt(fallback);
    }

    private List<String> stringList(JsonNode node, List<String> fallback) {
        if (node == null || !node.isArray()) {
            return fallback;
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText("").trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values.isEmpty() ? fallback : values;
    }

    private List<String> resolveStringList(JsonNode node, List<String> fallback) {
        List<String> base = stringList(node, fallback == null ? List.of() : fallback);
        if (base == null || base.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String item : base) {
            if (item != null && !item.isBlank()) {
                normalized.add(item.trim());
            }
        }
        return normalized.isEmpty() ? List.of() : List.copyOf(normalized);
    }

    private List<String> resolveFixedLengthStringList(JsonNode node, List<String> fallback, int expectedSize) {
        List<String> resolved = new ArrayList<>(resolveStringList(node, fallback));
        List<String> fallbackValues = resolveStringList(null, fallback);
        if (resolved.size() > expectedSize) {
            return List.copyOf(resolved.subList(0, expectedSize));
        }
        for (String item : fallbackValues) {
            if (resolved.size() >= expectedSize) {
                break;
            }
            if (!resolved.contains(item)) {
                resolved.add(item);
            }
        }
        while (resolved.size() < expectedSize) {
            resolved.add("请补充你的核心咨询问题");
        }
        return List.copyOf(resolved);
    }

    private String joinListForPrompt(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.joining(" | "));
    }

    private List<InterviewAnswerHelperItemGatewayResult> interviewAnswerHelperItems(
            JsonNode node,
            List<InterviewAnswerHelperItemGatewayResult> fallback
    ) {
        if (node == null || !node.isArray()) {
            return fallback;
        }
        List<InterviewAnswerHelperItemGatewayResult> normalizedFallback = fallback == null ? List.of() : fallback;
        Map<String, InterviewAnswerHelperItemGatewayResult> fallbackByKey = normalizedFallback.stream()
                .collect(java.util.stream.Collectors.toMap(
                        InterviewAnswerHelperItemGatewayResult::key,
                        item -> item,
                        (left, right) -> left,
                        java.util.LinkedHashMap::new
                ));
        Map<String, InterviewAnswerHelperItemGatewayResult> providerByKey = new java.util.LinkedHashMap<>();
        for (JsonNode itemNode : node) {
            String key = normalizeInterviewAnswerHelperCueKey(itemNode.path("key").asText(""), null);
            if (key == null || providerByKey.containsKey(key)) {
                continue;
            }
            InterviewAnswerHelperItemGatewayResult fallbackItem = fallbackByKey.get(key);
            String fallbackSummary = fallbackItem == null ? "当前还缺少这项诊断。" : fallbackItem.summary();
            String fallbackNextAction = fallbackItem == null ? "建议补一处更具体的表达证据。" : fallbackItem.nextAction();
            providerByKey.put(key, new InterviewAnswerHelperItemGatewayResult(
                    key,
                    normalizeInterviewAnswerHelperLevel(itemNode.path("level").asText(""), fallbackItem == null ? "INFO" : fallbackItem.level()),
                    preferChineseInterviewText(textValue(itemNode, "summary", fallbackSummary), fallbackSummary),
                    preferChineseInterviewText(textValue(itemNode, "nextAction", fallbackNextAction), fallbackNextAction)
            ));
        }
        if (providerByKey.isEmpty()) {
            return normalizedFallback;
        }
        List<InterviewAnswerHelperItemGatewayResult> resolved = new ArrayList<>();
        for (InterviewAnswerHelperItemGatewayResult fallbackItem : normalizedFallback) {
            resolved.add(providerByKey.getOrDefault(fallbackItem.key(), fallbackItem));
        }
        return resolved.isEmpty() ? List.copyOf(providerByKey.values()) : List.copyOf(resolved);
    }

    private String normalizeScoreLabel(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "S", "A+", "A", "A-", "B+", "B", "B-", "C+", "C", "D" -> normalized;
            default -> null;
        };
    }

    private List<ResumeStructureItem> resumeStructureItems(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<ResumeStructureItem> items = new ArrayList<>();
        for (JsonNode item : node) {
            String label = item.path("label").asText("").trim();
            String tip = item.path("tip").asText("").trim();
            if (label.isBlank()) {
                continue;
            }
            items.add(new ResumeStructureItem(label, clamp(item.path("score").asInt(3), 1, 5), tip));
        }
        return items;
    }

    private List<ResumeRewriteItem> resumeRewriteItems(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<ResumeRewriteItem> items = new ArrayList<>();
        for (int index = 0; index < node.size(); index++) {
            JsonNode item = node.get(index);
            String problem = item.path("problem").asText("").trim();
            String afterText = item.path("afterText").asText("").trim();
            if (problem.isBlank() || afterText.isBlank()) {
                continue;
            }
            String id = item.path("id").asText("").trim();
            items.add(new ResumeRewriteItem(
                    id.isBlank() ? "rewrite-" + (index + 1) : id,
                    item.path("title").asText("").trim(),
                    problem,
                    item.path("beforeText").asText("").trim(),
                    afterText,
                    item.path("isHeuristic").asBoolean(false)
            ));
        }
        return items;
    }

    private AiGatewayResult toGatewayResult(
            AiProviderChatResult providerResult,
            AiProviderInvocation invocation,
            String sceneCode
    ) {
        return new AiGatewayResult(
                "ai-gateway",
                safe(providerResult.taskType()).toLowerCase(Locale.ROOT),
                TimePayloads.toEpochMillis(Instant.now()),
                providerResult.provider(),
                providerResult.model(),
                providerResult.latencyMs(),
                providerResult.requestTokens(),
                providerResult.responseTokens(),
                providerResult.totalTokens(),
                providerResult.thoughtsTokens(),
                providerResult.reasoningEffort(),
                providerResult.thinkingBudget(),
                providerResult.thinkingLevel(),
                providerResult.estimatedCost() == null ? BigDecimal.ZERO : providerResult.estimatedCost(),
                resolveGatewaySceneCode(sceneCode, invocation),
                invocation == null ? null : normalizeSceneCode(invocation.routeCode()),
                invocation == null ? null : normalizeSceneCode(invocation.routePolicyCode())
        );
    }

    private AiGatewayResult toGatewayResult(
            AiProviderSpeechResult providerResult,
            AiProviderInvocation invocation,
            String sceneCode
    ) {
        return new AiGatewayResult(
                "ai-gateway",
                safe(providerResult.taskType()).toLowerCase(Locale.ROOT),
                TimePayloads.toEpochMillis(Instant.now()),
                providerResult.provider(),
                providerResult.model(),
                providerResult.latencyMs(),
                providerResult.requestTokens(),
                providerResult.responseTokens(),
                providerResult.totalTokens(),
                providerResult.thoughtsTokens(),
                providerResult.reasoningEffort(),
                providerResult.thinkingBudget(),
                providerResult.thinkingLevel(),
                providerResult.estimatedCost() == null ? BigDecimal.ZERO : providerResult.estimatedCost(),
                resolveGatewaySceneCode(sceneCode, invocation),
                invocation == null ? null : normalizeSceneCode(invocation.routeCode()),
                invocation == null ? null : normalizeSceneCode(invocation.routePolicyCode())
        );
    }

    private AiGatewayResult toGatewayResult(
            AiProviderTextToSpeechResult providerResult,
            AiProviderInvocation invocation,
            String sceneCode
    ) {
        return new AiGatewayResult(
                "ai-gateway",
                safe(providerResult.taskType()).toLowerCase(Locale.ROOT),
                TimePayloads.toEpochMillis(Instant.now()),
                providerResult.provider(),
                providerResult.model(),
                providerResult.latencyMs(),
                providerResult.requestTokens(),
                providerResult.responseTokens(),
                providerResult.totalTokens(),
                providerResult.thoughtsTokens(),
                providerResult.reasoningEffort(),
                providerResult.thinkingBudget(),
                providerResult.thinkingLevel(),
                providerResult.estimatedCost() == null ? BigDecimal.ZERO : providerResult.estimatedCost(),
                resolveGatewaySceneCode(sceneCode, invocation),
                invocation == null ? null : normalizeSceneCode(invocation.routeCode()),
                invocation == null ? null : normalizeSceneCode(invocation.routePolicyCode())
        );
    }

    private String resolveGatewaySceneCode(String sceneCode, AiProviderInvocation invocation) {
        if (sceneCode != null && !sceneCode.isBlank()) {
            return normalizeSceneCode(sceneCode);
        }
        if (invocation == null) {
            return null;
        }
        return normalizeSceneCode(invocation.sceneCode());
    }

    private boolean useRealProvider() {
        return properties.getMode() != AiGatewayMode.MOCK;
    }

    private void logGatewayDispatch(String taskType, String sceneCode, String modelPreference) {
        if (!runtimeSettingsService.isDebugModeEnabled()) {
            return;
        }
        log.info(
                "ai gateway dispatch mode={}, taskType={}, sceneCode={}, modelPreference={}",
                properties.getMode(),
                taskType,
                sceneCode,
                modelPreference
        );
    }

    private void logResolvedInvocation(AiProviderInvocation invocation) {
        if (!runtimeSettingsService.isDebugModeEnabled()) {
            return;
        }
        log.info(
                "ai gateway route resolved taskType={}, sceneCode={}, provider={}, providerType={}, routeCode={}, executionMode={}, model={}, timeoutMs={}, maxRetries={}",
                invocation.taskType(),
                invocation.sceneCode(),
                invocation.providerCode(),
                invocation.providerType(),
                invocation.routeCode(),
                invocation.executionMode(),
                invocation.model(),
                invocation.timeout().toMillis(),
                invocation.maxRetries()
        );
    }

    private String preferredModel(String modelPreference, String fallbackModel) {
        if (useRealProvider() && isMockModelAlias(modelPreference)) {
            return safe(fallbackModel).trim();
        }
        return (modelPreference == null || modelPreference.isBlank()) ? safe(fallbackModel).trim() : modelPreference.trim();
    }

    private boolean isMockModelAlias(String modelPreference) {
        return modelPreference != null && modelPreference.trim().toLowerCase(Locale.ROOT).startsWith("mock-");
    }

    private AiGatewayResult buildGatewayResult(String taskType, String sceneCode, String sourceText, String modelPreference) {
        int requestTokens = estimateTokens(sourceText);
        int responseTokens = Math.max(24, requestTokens / 3);
        int totalTokens = requestTokens + responseTokens;
        String model = (modelPreference == null || modelPreference.isBlank()) ? "mock-economy-model" : modelPreference.trim();
        BigDecimal estimatedCost = BigDecimal.valueOf(totalTokens)
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("0.010000"))
                .setScale(6, RoundingMode.HALF_UP);
        return new AiGatewayResult(
                "ai-gateway",
                safe(taskType).toLowerCase(Locale.ROOT),
                TimePayloads.toEpochMillis(Instant.now()),
                "mock-provider",
                model,
                Math.max(Duration.ofMillis(80 + requestTokens / 4).toMillis(), 1L),
                requestTokens,
                responseTokens,
                totalTokens,
                0,
                null,
                null,
                null,
                estimatedCost,
                normalizeSceneCode(sceneCode),
                null,
                null
        );
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 32;
        }
        return Math.max(32, (text.length() + 3) / 4);
    }

    private int countUserAnswers(List<String> historyLines) {
        if (historyLines == null || historyLines.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String historyLine : historyLines) {
            if (historyLine != null && historyLine.trim().toUpperCase(Locale.ROOT).startsWith("USER:")) {
                count++;
            }
        }
        return count;
    }

    private InterviewAnswerHelperHeuristic buildInterviewAnswerHelperHeuristic(String currentAnswer, List<String> cueKeys) {
        String normalizedAnswer = safe(currentAnswer).trim();
        List<String> normalizedCueKeys = normalizeInterviewAnswerHelperCueKeys(cueKeys);
        if (normalizedAnswer.isBlank()) {
            List<InterviewAnswerHelperItemGatewayResult> idleItems = normalizedCueKeys.stream()
                    .map(cueKey -> new InterviewAnswerHelperItemGatewayResult(
                            cueKey,
                            "INFO",
                            "当前还没有可分析的已发送回答。",
                            "先完成一轮回答后，这里会给出针对性的补充提示。"
                    ))
                    .toList();
            return new InterviewAnswerHelperHeuristic(
                    "INFO",
                    "当前还没有可分析的已发送回答，先完成一轮作答后再看这一块。",
                    idleItems,
                    List.of("先完成一轮回答后，这里会给出针对性的补充提示。")
            );
        }

        boolean hasDigits = containsDigits(normalizedAnswer);
        boolean hasSituation = containsAny(normalizedAnswer, "背景", "场景", "当时", "项目", "需求", "业务", "任务", "负责");
        boolean hasAction = containsAny(normalizedAnswer, "负责", "设计", "实现", "优化", "重构", "推进", "排查", "协调", "搭建", "上线", "改造");
        boolean hasResult = hasDigits || containsAny(normalizedAnswer, "结果", "最终", "提升", "降低", "减少", "增加", "缩短", "节省", "稳定");
        boolean hasClosing = containsAny(normalizedAnswer, "复盘", "总结", "反思", "所以", "因此", "后续", "权衡", "验证");

        List<InterviewAnswerHelperItemGatewayResult> items = new ArrayList<>();
        for (String cueKey : normalizedCueKeys) {
            if ("STAR".equals(cueKey)) {
                List<String> missingParts = new ArrayList<>();
                if (!hasSituation) {
                    missingParts.add("背景");
                }
                if (!hasAction) {
                    missingParts.add("动作");
                }
                if (!hasResult) {
                    missingParts.add("结果");
                }
                if (missingParts.isEmpty()) {
                    items.add(new InterviewAnswerHelperItemGatewayResult(
                            cueKey,
                            "READY",
                            hasClosing ? "背景、动作、结果和收口都已经比较完整。" : "背景、动作、结果已经交代出来了，主线比较清楚。",
                            hasClosing ? "继续把最关键的一处取舍或验证方式再讲细一点即可。" : "结尾再补一句关键取舍、复盘或验证方式，会更稳。"
                    ));
                } else {
                    items.add(new InterviewAnswerHelperItemGatewayResult(
                            cueKey,
                            "WARN",
                            "当前 STAR 结构还不够完整，缺少" + String.join(" / ", missingParts) + "。",
                            "按“背景 - 动作 - 结果”的顺序补齐最缺的一段，再把个人贡献说得更明确。"
                    ));
                }
                continue;
            }
            if ("METRICS".equals(cueKey)) {
                if (hasDigits) {
                    items.add(new InterviewAnswerHelperItemGatewayResult(
                            cueKey,
                            "READY",
                            "已经出现了数据或结果变化，量化意识比较好。",
                            "再补一组前后基线或验证方式，会更有说服力。"
                    ));
                } else {
                    items.add(new InterviewAnswerHelperItemGatewayResult(
                            cueKey,
                            "WARN",
                            hasResult ? "提到了结果方向，但还缺少更明确的数据支撑。" : "当前还没有明显的数据、比例或前后对比。",
                            "补一组指标、比例、耗时或结果对比，让这段回答更能站住。"
                    ));
                }
                continue;
            }
            if (normalizedAnswer.length() >= 110 && hasResult && hasClosing) {
                items.add(new InterviewAnswerHelperItemGatewayResult(
                        cueKey,
                        "READY",
                        "这轮回答已经有主线、结果和收口，完整度比较稳定。",
                        "继续把验证方式或关键取舍补得更具体，就会更像成熟面试表达。"
                ));
            } else {
                items.add(new InterviewAnswerHelperItemGatewayResult(
                        cueKey,
                        "WARN",
                        normalizedAnswer.length() < 70
                                ? "回答还偏短，信息量暂时不够支撑完整判断。"
                                : "主线已经有了，但结尾收口和表达闭环还可以更清楚。",
                        normalizedAnswer.length() < 70
                                ? "建议至少补齐背景、动作和结果，再加一句验证方式或复盘。"
                                : "结尾补一句结果影响、验证方式或复盘收获，让整段更完整。"
                ));
            }
        }

        long warnCount = items.stream().filter(item -> "WARN".equals(item.level())).count();
        String overallLevel = warnCount == 0 ? "READY" : "WARN";
        String overallSummary;
        if (warnCount == 0) {
            overallSummary = "这一轮回答的主线已经比较完整，量化和收口也相对稳定。接下来只要把验证方式或关键取舍再讲细一点就够了。";
        } else if (!hasDigits) {
            overallSummary = "这一轮已经有基本主线，但最值得优先补的是量化证据和结果对比。先把结果说得更具体，整体说服力会明显提升。";
        } else if (!hasClosing) {
            overallSummary = "这一轮已经有结果感，但结尾收口还可以再压紧一点。补一句验证方式、关键取舍或复盘结论，会更像完整面试回答。";
        } else {
            overallSummary = "这一轮已经能看出你的主要贡献，但结构还可以再压紧。优先把最缺的一处信息补齐，整段表达会更稳。";
        }
        List<String> details = items.stream()
                .filter(item -> "WARN".equals(item.level()))
                .map(InterviewAnswerHelperItemGatewayResult::nextAction)
                .distinct()
                .limit(3)
                .toList();
        if (details.isEmpty()) {
            details = List.of("这轮基础已经不错，后续继续把验证方式和关键取舍讲得更具体即可。");
        }
        return new InterviewAnswerHelperHeuristic(
                overallLevel,
                overallSummary,
                List.copyOf(items),
                List.copyOf(details)
        );
    }

    private int evaluateAnswerScore(String answerText, int userAnswerCount) {
        int score = 52 + Math.min(userAnswerCount * 6, 18);
        String normalized = safe(answerText);
        if (containsDigits(normalized)) {
            score += 10;
        }
        if (containsAny(normalized, "架构", "并发", "性能", "数据库", "缓存", "压测", "监控", "latency", "throughput", "database", "cache", "benchmark")) {
            score += 12;
        }
        if (normalized.length() >= 60) {
            score += 6;
        }
        if (normalized.length() >= 110) {
            score += 4;
        }
        return clamp(score, 48, 96);
    }

    private boolean shouldFinishInterview(String answerText, int userAnswerCount, int scoreHint) {
        if (userAnswerCount >= 6) {
            return true;
        }
        boolean hasMetrics = containsDigits(answerText);
        boolean hasDepth = containsAny(answerText, "架构", "并发", "性能", "数据库", "缓存", "压测", "监控", "architecture", "throughput", "latency", "database", "cache", "benchmark", "monitor");
        return userAnswerCount >= 5 && scoreHint >= 78 && (hasMetrics || hasDepth);
    }

    private String buildFinishReason(int userAnswerCount, int scoreHint) {
        if (userAnswerCount >= 6) {
            return "ENOUGH_DEPTH";
        }
        if (scoreHint >= 78) {
            return "ENOUGH_EVIDENCE";
        }
        return "REPETITIVE";
    }

    private int remainingReplyRounds(int replyRoundLimit, int userAnswerCount) {
        if (replyRoundLimit <= 0) {
            return 0;
        }
        return Math.max(replyRoundLimit - Math.max(userAnswerCount, 0), 0);
    }

    private boolean hasExplicitBooleanField(JsonNode node, String fieldName) {
        return node != null
                && !node.path(fieldName).isMissingNode()
                && !node.path(fieldName).isNull()
                && node.path(fieldName).isBoolean();
    }

    private String buildOpeningQuestion(String targetRole, String sessionContext, String resumeContext) {
        String normalizedRole = safe(targetRole).isBlank() ? "目标岗位" : targetRole.trim();
        String greeting = hasSessionContextFlag(sessionContext, "interviewerStyle=PRESSURE")
                ? "你好，我们直接开始。"
                : "你好，我们开始这轮模拟面试。";
        if (hasSessionContextFlag(sessionContext, "interviewType=BEHAVIORAL")
                || hasSessionContextFlag(sessionContext, "interviewerStyle=HR")) {
            return greeting + " 先请你简单做个自我介绍，再挑一段最能说明你为什么适合 "
                    + normalizedRole
                    + " 的经历，讲讲当时的背景、你的选择和结果。";
        }
        if (hasSessionContextFlag(sessionContext, "interviewType=FUNDAMENTALS")) {
            return greeting + " 先热个身，请你结合一个实际项目场景，讲讲你在准备 "
                    + normalizedRole
                    + " 时最常被问到、也最有把握说明白的一个基础知识点。";
        }
        if (!safe(resumeContext).isBlank()) {
            return greeting + " 我们先从你的代表性经历聊起。结合你最近简历里最能体现 "
                    + normalizedRole
                    + " 匹配度的一段项目，讲讲当时的业务背景、你具体负责什么，以及最后做出了什么结果。";
        }
        return greeting + " 我们先从你最有代表性的一个项目开始。请你挑一个和 "
                + normalizedRole
                + " 最相关的经历，讲讲背景、你的核心动作，以及最后拿到了什么结果。";
    }

    private String buildFinishStatement(String targetRole, String sessionContext) {
        String normalizedRole = safe(targetRole).isBlank() ? "这轮模拟面试" : safe(targetRole).trim() + " 这轮模拟面试";
        if (hasSessionContextFlag(sessionContext, "interviewerStyle=COACHING")) {
            return "好，我这边已经拿到比较充分的信息了。" + normalizedRole + " 先收在这里，我马上给你整理一份总结和改进建议。";
        }
        if (hasSessionContextFlag(sessionContext, "interviewerStyle=PRESSURE")) {
            return "好，关键点我已经了解了。" + normalizedRole + " 这一轮先到这里，接下来我会给你一份总结。";
        }
        return "好，我这边已经有足够信息了。" + normalizedRole + " 先到这里，接下来我会给你整理总结和改进建议。";
    }

    private String buildFollowUpQuestion(String answerText, String targetRole, String sessionContext, int scoreHint) {
        String normalizedRole = safe(targetRole).isBlank() ? "这个岗位" : targetRole.trim();
        String prefix = hasSessionContextFlag(sessionContext, "interviewerStyle=PRESSURE")
                ? "我继续追一个细节，"
                : hasSessionContextFlag(sessionContext, "interviewerStyle=COACHING")
                    ? "我顺着你刚才这段继续问一下，"
                    : "我接着追问一个细节，";
        if (hasSessionContextFlag(sessionContext, "interviewType=BEHAVIORAL")
                || hasSessionContextFlag(sessionContext, "interviewerStyle=HR")) {
            if (containsAny(answerText, "冲突", "分歧", "协作", "沟通", "推动")) {
                return prefix + "当时你是怎么沟通、怎么推动大家达成一致的？";
            }
            if (containsAny(answerText, "选择", "决定", "取舍")) {
                return prefix + "为什么最后会做那个选择？如果重来一次，你还会这么判断吗？";
            }
            return prefix + "在你刚才这段经历里，最能体现你个人作用的一步到底是什么？";
        }
        if (hasSessionContextFlag(sessionContext, "interviewType=FUNDAMENTALS")) {
            if (containsAny(answerText, "缓存", "cache")) {
                return prefix + "如果线上突然出现缓存失效或穿透，你一般会怎么判断和处理？";
            }
            if (containsAny(answerText, "数据库", "索引", "sql", "database")) {
                return prefix + "你刚才提到的数据库优化，背后的判断依据和原理你会怎么解释？";
            }
            return prefix + "如果把这个知识点讲给一个刚接触 " + normalizedRole + " 的同学听，你会怎么把原理和实际使用场景讲清楚？";
        }
        if (containsAny(answerText, "性能", "latency", "throughput")) {
            return prefix + "你是怎么验证这次性能优化在真实流量下依然有效的？";
        }
        if (containsAny(answerText, "并发", "concurrent", "锁", "lock")) {
            return prefix + "在高并发或异常重试场景下，你是怎么保证数据一致性的？";
        }
        if (containsAny(answerText, "数据库", "索引", "sql", "database")) {
            return prefix + "你当时是怎么定位到数据库瓶颈的，最后又是怎么把它优化掉的？";
        }
        if (containsDigits(answerText)) {
            return prefix + "这些结果具体是通过什么指标、什么观测方式验证出来的？";
        }
        if (scoreHint >= 75) {
            return prefix + "如果把这个项目再做一次，你会怎么说明当时最关键的技术权衡和取舍？";
        }
        return prefix + "能不能再补一个和 " + normalizedRole + " 更相关的量化结果，或者一个更能体现你个人判断的技术细节？";
    }

    private String buildCoachFeedback(String answerText, String sessionContext, int scoreHint) {
        String positive;
        if (containsDigits(answerText)) {
            positive = "亮点是你已经把结果感带出来了，听的人能比较快抓住价值。";
        } else if (containsAny(answerText, "架构", "并发", "性能", "数据库", "缓存", "压测", "监控", "architecture", "throughput", "latency", "database", "cache")) {
            positive = "亮点是你提到了关键技术动作，说明回答不是只停留在表面。";
        } else {
            positive = "亮点是你已经把事情主线讲出来了，听者能先跟上你的背景。";
        }

        String improvement;
        if (hasSessionContextFlag(sessionContext, "interviewType=BEHAVIORAL")
                || hasSessionContextFlag(sessionContext, "interviewerStyle=HR")) {
            improvement = "下一轮可以再补一下你当时为什么这么判断、怎么沟通推进，以及最后带来了什么变化。";
        } else if (containsDigits(answerText)) {
            improvement = "下一轮最好把验证方式、个人判断和关键取舍再讲细一点，这样说服力会更强。";
        } else {
            improvement = "下一轮建议补上更具体的数据、验证方式或个人决策细节，别只停在概括层。";
        }

        String close;
        if (scoreHint >= 82) {
            close = "整体已经很接近一轮面试里让人有记忆点的回答了。";
        } else if (scoreHint >= 72) {
            close = "再把结构压紧一点，会更像成熟、稳定的面试表达。";
        } else {
            close = "建议按背景、动作、结果的顺序再组织一次，整段会更稳。";
        }
        return positive + improvement + close;
    }

    private boolean hasSessionContextFlag(String sessionContext, String expectedFlag) {
        return safe(sessionContext).toUpperCase(Locale.ROOT).contains(safe(expectedFlag).toUpperCase(Locale.ROOT));
    }

    private int summarizeScore(List<String> historyLines) {
        String historyText = String.join("\n", historyLines == null ? List.of() : historyLines);
        int score = 68;
        if (containsDigits(historyText)) {
            score += 8;
        }
        if (containsAny(historyText, "架构", "并发", "性能", "数据库", "缓存", "压测", "监控", "权衡", "trade-off")) {
            score += 10;
        }
        score += Math.min(countUserAnswers(historyLines) * 2, 10);
        return clamp(score, 60, 95);
    }

    private List<String> defaultSummaryStrengths(List<String> historyLines) {
        String historyText = String.join("\n", historyLines == null ? List.of() : historyLines);
        LinkedHashSet<String> items = new LinkedHashSet<>();
        if (containsDigits(historyText)) {
            items.add("回答中包含量化结果，业务价值表达较清晰");
        }
        if (containsAny(historyText, "架构", "并发", "性能", "数据库", "缓存", "压测", "监控")) {
            items.add("能说明技术方案与稳定性治理细节");
        }
        items.add("整体表达能够围绕岗位相关项目展开");
        return new ArrayList<>(items);
    }

    private List<String> defaultSummaryWeaknesses(List<String> historyLines) {
        String historyText = String.join("\n", historyLines == null ? List.of() : historyLines);
        LinkedHashSet<String> items = new LinkedHashSet<>();
        if (!containsDigits(historyText)) {
            items.add("量化结果仍可进一步补充和细化");
        }
        if (!containsAny(historyText, "权衡", "trade-off", "为什么", "取舍")) {
            items.add("技术权衡与决策依据表达还不够充分");
        }
        items.add("可继续强化个人主导贡献与复盘深度");
        return new ArrayList<>(items);
    }

    private List<String> defaultSummarySuggestions(List<String> historyLines) {
        LinkedHashSet<String> items = new LinkedHashSet<>();
        items.add("用 STAR 或 背景-方案-结果 结构压缩回答节奏");
        items.add("为每个核心项目准备 2-3 个可复用的量化指标");
        items.add("补充故障处理、权衡取舍和验证手段，提升高级感");
        return new ArrayList<>(items);
    }

    private boolean containsDigits(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (char current : text.toCharArray()) {
            if (Character.isDigit(current)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null || text.isBlank() || keywords == null || keywords.length == 0) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeSceneCode(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return rawValue.trim().toUpperCase(Locale.ROOT);
    }

    private String safe(String rawValue) {
        return rawValue == null ? "" : rawValue;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class JsonFieldStreamAccumulator {
        private final String fieldName;
        private final StringBuilder rawBuffer = new StringBuilder();
        private String emittedValue = "";

        private JsonFieldStreamAccumulator(String fieldName) {
            this.fieldName = fieldName;
        }

        private String append(String rawDelta) {
            rawBuffer.append(rawDelta == null ? "" : rawDelta);
            String currentValue = extractCurrentValue(rawBuffer.toString(), fieldName);
            if (currentValue.length() <= emittedValue.length()) {
                return "";
            }
            String delta = currentValue.substring(emittedValue.length());
            emittedValue = currentValue;
            return delta;
        }

        private String extractCurrentValue(String source, String fieldName) {
            if (source == null || source.isBlank() || fieldName == null || fieldName.isBlank()) {
                return "";
            }
            String marker = "\"" + fieldName + "\"";
            int fieldIndex = source.indexOf(marker);
            if (fieldIndex < 0) {
                return "";
            }
            int colonIndex = source.indexOf(':', fieldIndex + marker.length());
            if (colonIndex < 0) {
                return "";
            }
            int valueStart = colonIndex + 1;
            while (valueStart < source.length() && Character.isWhitespace(source.charAt(valueStart))) {
                valueStart++;
            }
            if (valueStart >= source.length() || source.charAt(valueStart) != '"') {
                return "";
            }
            valueStart++;
            StringBuilder decoded = new StringBuilder();
            boolean escaping = false;
            for (int index = valueStart; index < source.length(); index++) {
                char current = source.charAt(index);
                if (escaping) {
                    switch (current) {
                        case '"', '\\', '/' -> decoded.append(current);
                        case 'b' -> decoded.append('\b');
                        case 'f' -> decoded.append('\f');
                        case 'n' -> decoded.append('\n');
                        case 'r' -> decoded.append('\r');
                        case 't' -> decoded.append('\t');
                        case 'u' -> {
                            if (index + 4 >= source.length()) {
                                return decoded.toString();
                            }
                            String hex = source.substring(index + 1, index + 5);
                            try {
                                decoded.append((char) Integer.parseInt(hex, 16));
                                index += 4;
                            } catch (NumberFormatException ex) {
                                return decoded.toString();
                            }
                        }
                        default -> decoded.append(current);
                    }
                    escaping = false;
                    continue;
                }
                if (current == '\\') {
                    escaping = true;
                    continue;
                }
                if (current == '"') {
                    return decoded.toString();
                }
                decoded.append(current);
            }
            return decoded.toString();
        }
    }

    private record RoutedChatResult(
            AiProviderInvocation invocation,
            AiProviderChatResult providerResult
    ) {
    }

    private record RoutedSpeechResult(
            AiProviderInvocation invocation,
            AiProviderSpeechResult providerResult
    ) {
    }

    private record RoutedTextToSpeechResult(
            AiProviderInvocation invocation,
            AiProviderTextToSpeechResult providerResult
    ) {
    }

    /**
     * 网关调用元数据。
     */
    public record AiGatewayResult(
            String service,
            String module,
            Long time,
            String provider,
            String model,
            long latencyMs,
            int requestTokens,
            int responseTokens,
            int totalTokens,
            int thoughtsTokens,
            String reasoningEffort,
            Integer thinkingBudget,
            String thinkingLevel,
            BigDecimal estimatedCost,
            String sceneCode,
            String routeCode,
            String routePolicyCode
    ) {
        public AiGatewayResult(
                String service,
                String module,
                Long time,
                String provider,
                String model,
                long latencyMs,
                int requestTokens,
                int responseTokens,
                int totalTokens,
                int thoughtsTokens,
                String reasoningEffort,
                Integer thinkingBudget,
                String thinkingLevel,
                BigDecimal estimatedCost
        ) {
            this(
                    service,
                    module,
                    time,
                    provider,
                    model,
                    latencyMs,
                    requestTokens,
                    responseTokens,
                    totalTokens,
                    thoughtsTokens,
                    reasoningEffort,
                    thinkingBudget,
                    thinkingLevel,
                    estimatedCost,
                    null,
                    null,
                    null
            );
        }
    }

    /**
     * 简历优化结果。
     */
    public record ResumeOptimizeGatewayResult(
            String summary,
            List<String> strengths,
            List<String> risks,
            List<String> suggestions,
            String scoreLabel,
            List<ResumeStructureItem> structureItems,
            List<ResumeRewriteItem> rewriteItems,
            AiGatewayResult gatewayResult
    ) {
    }

    /**
     * 社区预回答结果。
     */
    public record CommunityPreAnswerGatewayResult(
            String draftComment,
            AiGatewayResult gatewayResult
    ) {
    }

    /**
     * 导师履约回复草稿结果。
     */
    public record MentorOrderReplyDraftGatewayResult(
            String draftReply,
            AiGatewayResult gatewayResult
    ) {
    }

    /**
     * 导师咨询准备单草稿结果。
     */
    public record MentorPrepSheetGatewayResult(
            String summaryDraft,
            List<String> coreQuestions,
            List<String> suggestedMaterials,
            List<String> expectedOutcomes,
            AiGatewayResult gatewayResult
    ) {
    }

    /**
     * 学生成长画像总结结果。
     */
    public record StudentPortraitSummaryGatewayResult(
            String headline,
            String summary,
            List<String> nextActions,
            AiGatewayResult gatewayResult
    ) {
    }

    /**
     * 破冰私信结果。
     */
    public record IcebreakMessageGatewayResult(
            String messageDraft,
            AiGatewayResult gatewayResult
    ) {
    }

    /**
     * 文本面试首问结果。
     */
    public record InterviewQuestionGatewayResult(
            String firstQuestion,
            AiGatewayResult gatewayResult
    ) {
    }

    /**
     * 语音转写结果。
     */
    public record AudioTranscriptionGatewayResult(
            String transcript,
            AiGatewayResult gatewayResult
    ) {
    }


    /**
     * 文本转语音结果。
     */
    public record TextToSpeechGatewayResult(
            String text,
            String stylePrompt,
            String voiceName,
            String mimeType,
            int sampleRate,
            String audioBase64,
            AiGatewayResult gatewayResult
    ) {
    }

    /**
     * 文本面试追问结果。
     */
    public record InterviewReplyGatewayResult(
            String followUpQuestion,
            String coachFeedback,
            int scoreHint,
            boolean shouldFinish,
            String finishReason,
            AiGatewayResult gatewayResult
    ) {
    }

    public record InterviewAnswerHelperItemGatewayResult(
            String key,
            String level,
            String summary,
            String nextAction
    ) {
    }

    public record InterviewAnswerHelperGatewayResult(
            String overallLevel,
            String overallSummary,
            List<InterviewAnswerHelperItemGatewayResult> items,
            List<String> details,
            AiGatewayResult gatewayResult
    ) {
    }

    /**
     * 文本面试总结结果。
     */
    public record InterviewSummaryGatewayResult(
            int overallScore,
            List<String> strengths,
            List<String> weaknesses,
            List<String> suggestions,
            AiGatewayResult gatewayResult
    ) {
    }

    private record InterviewAnswerHelperHeuristic(
            String overallLevel,
            String overallSummary,
            List<InterviewAnswerHelperItemGatewayResult> items,
            List<String> details
    ) {
    }
}
