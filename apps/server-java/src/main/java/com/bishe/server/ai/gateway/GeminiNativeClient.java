package com.bishe.server.ai.gateway;

import com.bishe.server.common.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Gemini 原生 provider HTTP 客户端。
 */
@Component
public class GeminiNativeClient implements AiProviderClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiNativeClient.class);
    private static final String JSON_RESPONSE_MIME_TYPE = "application/json";
    private static final String DEFAULT_TRANSCRIPTION_INSTRUCTION = "请将这段音频完整转写为纯文本，不要补充解释；若听不清，请尽量结合上下文还原。";
    private static final int MAX_PROVIDER_RESPONSE_BYTES = 16 * 1024 * 1024;
    private static final String DEFAULT_RESUME_PDF_INSTRUCTION =
            "请阅读我上传的 PDF 简历，并仅返回 JSON，不要输出 markdown。JSON schema: "
                    + "{\"summary\":string,\"strengths\":string[],\"risks\":string[],\"suggestions\":string[],"
                    + "\"scoreLabel\":string,"
                    + "\"structureItems\":[{\"label\":string,\"score\":number,\"tip\":string}],"
                    + "\"rewriteItems\":[{\"id\":string,\"title\":string,\"problem\":string,\"beforeText\":string,\"afterText\":string}]}。";
    private static final int MAX_LOG_BODY_LENGTH = 4000;

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final AiGatewayRuntimeSettingsService runtimeSettingsService;
    private final GeminiTransportResolver transportResolver;

    public GeminiNativeClient(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            AiGatewayRuntimeSettingsService runtimeSettingsService,
            GeminiTransportResolver transportResolver
    ) {
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
        this.runtimeSettingsService = runtimeSettingsService;
        this.transportResolver = transportResolver;
    }

    @Override
    public AiProviderType providerType() {
        return AiProviderType.GEMINI_NATIVE;
    }

    @Override
    public boolean supportsResumePdf() {
        return true;
    }

    @Override
    public AiProviderChatResult chatJson(AiProviderInvocation invocation, List<AiChatMessage> messages) {
        String model = normalizeRequired(invocation.model(), "provider model missing");
        GeminiThinkingResolution thinkingResolution = resolveGeminiThinking(invocation);
        GeminiTransportResolver.RequestSpec requestSpec = transportResolver.buildGenerateContentRequest(
                invocation.baseUrl(),
                invocation.apiKey(),
                model,
                invocation.providerExtraConfigJson(),
                false
        );
        String endpoint = requestSpec.endpoint();
        Map<String, Object> payload = buildChatPayload(invocation, messages, thinkingResolution);
        String requestBody = writeJson(payload);
        AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot runtimeSettings = runtimeSettingsService.getRuntimeSettings();

        logJsonRequestStart(runtimeSettings, invocation, model, endpoint, messages == null ? 0 : messages.size(), totalInputChars(messages));

        long startedAt = System.nanoTime();
        String body = executeJsonRequest(invocation, requestSpec, payload, requestBody, runtimeSettings);
        JsonNode root = readJson(body, invocation, endpoint, requestBody, runtimeSettings);
        String content = extractGeminiText(root);
        if (content.isBlank()) {
            logInvalidResponse(runtimeSettings, "candidate text blank", invocation, endpoint, requestBody, body);
            throw new ApiException("AI-2001", "AI 服务返回了无效内容，请稍后重试。", HttpStatus.BAD_GATEWAY);
        }
        int requestTokens = root.path("usageMetadata").path("promptTokenCount").asInt(0);
        int responseTokens = root.path("usageMetadata").path("candidatesTokenCount").asInt(0);
        int totalTokens = root.path("usageMetadata").path("totalTokenCount").asInt(requestTokens + responseTokens);
        int thoughtsTokens = extractThoughtsTokens(root);
        BigDecimal estimatedCost = calculateEstimatedCost(requestTokens, responseTokens, invocation);
        long latencyMs = Math.max(Duration.ofNanos(System.nanoTime() - startedAt).toMillis(), 1L);
        logRequestSuccess(runtimeSettings, invocation, model, latencyMs, requestTokens, responseTokens, totalTokens, thoughtsTokens, thinkingResolution);
        return new AiProviderChatResult(
                invocation.taskType(),
                content,
                invocation.providerCode(),
                model,
                latencyMs,
                requestTokens,
                responseTokens,
                totalTokens,
                thoughtsTokens,
                thinkingResolution.reasoningEffort(),
                thinkingResolution.thinkingBudget(),
                thinkingResolution.thinkingLevel(),
                estimatedCost
        );
    }

    @Override
    public AiProviderChatResult chatJsonStream(
            AiProviderInvocation invocation,
            List<AiChatMessage> messages,
            Consumer<String> deltaConsumer
    ) {
        String model = normalizeRequired(invocation.model(), "provider model missing");
        GeminiThinkingResolution thinkingResolution = resolveGeminiThinking(invocation);
        GeminiTransportResolver.RequestSpec requestSpec = transportResolver.buildGenerateContentRequest(
                invocation.baseUrl(),
                invocation.apiKey(),
                model,
                invocation.providerExtraConfigJson(),
                true
        );
        String endpoint = requestSpec.endpoint();
        Map<String, Object> payload = buildChatPayload(invocation, messages, thinkingResolution);
        String requestBody = writeJson(payload);
        AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot runtimeSettings = runtimeSettingsService.getRuntimeSettings();

        logJsonRequestStart(runtimeSettings, invocation, model, endpoint, messages == null ? 0 : messages.size(), totalInputChars(messages));

        long startedAt = System.nanoTime();
        StreamExecutionResult streamResult = executeJsonStreamRequest(invocation, requestSpec, payload, requestBody, runtimeSettings, deltaConsumer);
        if (streamResult.content().isBlank()) {
            logInvalidResponse(runtimeSettings, "candidate text blank", invocation, endpoint, requestBody, streamResult.rawStreamBody());
            throw new ApiException("AI-2001", "AI 服务返回了无效内容，请稍后重试。", HttpStatus.BAD_GATEWAY);
        }
        BigDecimal estimatedCost = calculateEstimatedCost(streamResult.requestTokens(), streamResult.responseTokens(), invocation);
        long latencyMs = Math.max(Duration.ofNanos(System.nanoTime() - startedAt).toMillis(), 1L);
        logRequestSuccess(
                runtimeSettings,
                invocation,
                model,
                latencyMs,
                streamResult.requestTokens(),
                streamResult.responseTokens(),
                streamResult.totalTokens(),
                streamResult.thoughtsTokens(),
                thinkingResolution
        );
        return new AiProviderChatResult(
                invocation.taskType(),
                streamResult.content(),
                invocation.providerCode(),
                model,
                latencyMs,
                streamResult.requestTokens(),
                streamResult.responseTokens(),
                streamResult.totalTokens(),
                streamResult.thoughtsTokens(),
                thinkingResolution.reasoningEffort(),
                thinkingResolution.thinkingBudget(),
                thinkingResolution.thinkingLevel(),
                estimatedCost
        );
    }

    @Override
    public AiProviderSpeechResult transcribeAudio(AiProviderInvocation invocation, MultipartFile audioFile) {
        String endpoint = "";
        String mimeType = "audio/webm";
        int audioBytesLength = 0;
        try {
            String model = normalizeRequired(invocation.model(), "provider model missing");
            GeminiTransportResolver.RequestSpec requestSpec = transportResolver.buildGenerateContentRequest(
                    invocation.baseUrl(),
                    invocation.apiKey(),
                    model,
                    invocation.providerExtraConfigJson(),
                    false
            );
            endpoint = requestSpec.endpoint();
            mimeType = audioFile == null || audioFile.getContentType() == null || audioFile.getContentType().isBlank()
                    ? "audio/webm"
                    : audioFile.getContentType();
            byte[] audioBytes = audioFile == null ? new byte[0] : audioFile.getBytes();
            audioBytesLength = audioBytes.length;
            String instruction = readExtraConfigText(invocation.routeExtraConfigJson(), "transcriptionInstruction", DEFAULT_TRANSCRIPTION_INSTRUCTION);
            Map<String, Object> payload = buildTranscriptionPayload(invocation, instruction, mimeType, audioBytes);
            String requestBody = writeJson(payload);
            AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot runtimeSettings = runtimeSettingsService.getRuntimeSettings();

            logTranscribeRequestStart(runtimeSettings, invocation, model, endpoint, mimeType, audioBytes.length);

            long startedAt = System.nanoTime();
            String body = executeJsonRequest(invocation, requestSpec, payload, requestBody, runtimeSettings);
            JsonNode root = readJson(body, invocation, endpoint, requestBody, runtimeSettings);
            String transcript = extractGeminiText(root);
            if (transcript.isBlank()) {
                logInvalidResponse(runtimeSettings, "transcript blank", invocation, endpoint, requestBody, body);
                throw new ApiException("AI-2102", "语音转写失败，请重新录音或改用文字模式。", HttpStatus.BAD_GATEWAY);
            }
            int requestTokens = root.path("usageMetadata").path("promptTokenCount").asInt(0);
            int responseTokens = root.path("usageMetadata").path("candidatesTokenCount").asInt(0);
            int totalTokens = root.path("usageMetadata").path("totalTokenCount").asInt(requestTokens + responseTokens);
            int thoughtsTokens = extractThoughtsTokens(root);
            BigDecimal estimatedCost = calculateEstimatedCost(requestTokens, responseTokens, invocation);
            long latencyMs = Math.max(Duration.ofNanos(System.nanoTime() - startedAt).toMillis(), 1L);
            logRequestSuccess(runtimeSettings, invocation, model, latencyMs, requestTokens, responseTokens, totalTokens, thoughtsTokens, GeminiThinkingResolution.none());
            return new AiProviderSpeechResult(
                    invocation.taskType(),
                    transcript,
                    invocation.providerCode(),
                    model,
                    latencyMs,
                    requestTokens,
                    responseTokens,
                    totalTokens,
                    thoughtsTokens,
                    null,
                    null,
                    null,
                    estimatedCost
            );
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error(
                    "gemini provider transcribe unexpected failure taskType={}, sceneCode={}, routeCode={}, provider={}, model={}, timeoutMs={}, maxRetries={}, url={}, mimeType={}, bytes={}, errorType={}, errorMessage={}",
                    invocation.taskType(),
                    invocation.sceneCode(),
                    invocation.routeCode(),
                    invocation.providerCode(),
                    invocation.model(),
                    invocation.timeout().toMillis(),
                    invocation.maxRetries(),
                    sanitizeEndpoint(endpoint),
                    mimeType,
                    audioBytesLength,
                    throwableType(ex),
                    summarizeException(ex),
                    ex
            );
            throw new ApiException("AI-2102", "语音转写失败，请重新录音或改用文字模式。", HttpStatus.BAD_GATEWAY, ex);
        }
    }

    @Override
    public AiProviderChatResult optimizeResumePdf(
            AiProviderInvocation invocation,
            String targetRole,
            String targetContext,
            String jobDescription,
            MultipartFile resumeFile
    ) {
        String endpoint = "";
        String mimeType = MediaType.APPLICATION_PDF_VALUE;
        int pdfBytesLength = 0;
        String resumeFileName = resumeFile == null || resumeFile.getOriginalFilename() == null
                ? ""
                : resumeFile.getOriginalFilename().trim();
        try {
            String model = normalizeRequired(invocation.model(), "provider model missing");
            GeminiThinkingResolution thinkingResolution = resolveGeminiThinking(invocation);
            GeminiTransportResolver.RequestSpec requestSpec = transportResolver.buildGenerateContentRequest(
                    invocation.baseUrl(),
                    invocation.apiKey(),
                    model,
                    invocation.providerExtraConfigJson(),
                    false
            );
            endpoint = requestSpec.endpoint();
            mimeType = resumeFile == null || resumeFile.getContentType() == null || resumeFile.getContentType().isBlank()
                    ? MediaType.APPLICATION_PDF_VALUE
                    : resumeFile.getContentType().trim();
            byte[] pdfBytes = resumeFile == null ? new byte[0] : resumeFile.getBytes();
            pdfBytesLength = pdfBytes.length;
            Map<String, Object> payload = buildResumePdfPayload(
                    invocation,
                    targetRole,
                    targetContext,
                    jobDescription,
                    resumeFileName,
                    mimeType,
                    pdfBytes,
                    thinkingResolution
            );
            String requestBody = writeJson(payload);
            AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot runtimeSettings = runtimeSettingsService.getRuntimeSettings();

            logResumePdfRequestStart(runtimeSettings, invocation, model, endpoint, mimeType, pdfBytes.length);

            long startedAt = System.nanoTime();
            String body = executeJsonRequest(invocation, requestSpec, payload, requestBody, runtimeSettings);
            JsonNode root = readJson(body, invocation, endpoint, requestBody, runtimeSettings);
            String content = extractGeminiText(root);
            if (content.isBlank()) {
                logInvalidResponse(runtimeSettings, "resume pdf candidate text blank", invocation, endpoint, requestBody, body);
                throw new ApiException("AI-2001", "AI 服务返回了无效内容，请稍后重试。", HttpStatus.BAD_GATEWAY);
            }
            int requestTokens = root.path("usageMetadata").path("promptTokenCount").asInt(0);
            int responseTokens = root.path("usageMetadata").path("candidatesTokenCount").asInt(0);
            int totalTokens = root.path("usageMetadata").path("totalTokenCount").asInt(requestTokens + responseTokens);
            int thoughtsTokens = extractThoughtsTokens(root);
            BigDecimal estimatedCost = calculateEstimatedCost(requestTokens, responseTokens, invocation);
            long latencyMs = Math.max(Duration.ofNanos(System.nanoTime() - startedAt).toMillis(), 1L);
            logRequestSuccess(runtimeSettings, invocation, model, latencyMs, requestTokens, responseTokens, totalTokens, thoughtsTokens, thinkingResolution);
            return new AiProviderChatResult(
                    invocation.taskType(),
                    content,
                    invocation.providerCode(),
                    model,
                    latencyMs,
                    requestTokens,
                    responseTokens,
                    totalTokens,
                    thoughtsTokens,
                    thinkingResolution.reasoningEffort(),
                    thinkingResolution.thinkingBudget(),
                    thinkingResolution.thinkingLevel(),
                    estimatedCost
            );
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error(
                    "gemini provider resume pdf unexpected failure taskType={}, sceneCode={}, routeCode={}, provider={}, model={}, timeoutMs={}, maxRetries={}, url={}, mimeType={}, bytes={}, resumeFileName={}, targetRole={}, targetContext={}, jobDescriptionChars={}, errorType={}, errorMessage={}",
                    invocation.taskType(),
                    invocation.sceneCode(),
                    invocation.routeCode(),
                    invocation.providerCode(),
                    invocation.model(),
                    invocation.timeout().toMillis(),
                    invocation.maxRetries(),
                    sanitizeEndpoint(endpoint),
                    mimeType,
                    pdfBytesLength,
                    resumeFileName,
                    safeValue(targetRole),
                    safeValue(targetContext),
                    lengthOf(jobDescription),
                    throwableType(ex),
                    summarizeException(ex),
                    ex
            );
            throw AiProviderErrorMapper.mapTransportError(ex);
        }
    }


    @Override
    public AiProviderTextToSpeechResult synthesizeSpeech(
            AiProviderInvocation invocation,
            String text,
            String stylePrompt,
            String voiceName
    ) {
        String endpoint = "";
        String effectiveVoiceName = safeValue(voiceName);
        int inputChars = lengthOf(text);
        try {
            String model = normalizeRequired(invocation.model(), "provider model missing");
            GeminiTransportResolver.RequestSpec requestSpec = transportResolver.buildGenerateContentRequest(
                    invocation.baseUrl(),
                    invocation.apiKey(),
                    model,
                    invocation.providerExtraConfigJson(),
                    false
            );
            endpoint = requestSpec.endpoint();
            String normalizedText = normalizeRequired(text, "tts text required");
            String effectiveStylePrompt = normalizeRequired(stylePrompt, "tts stylePrompt required");
            effectiveVoiceName = normalizeRequired(voiceName, "tts voiceName required");
            inputChars = normalizedText.length();
            Map<String, Object> payload = buildTextToSpeechPayload(invocation, normalizedText, effectiveStylePrompt, effectiveVoiceName);
            String requestBody = writeJson(payload);
            AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot runtimeSettings = runtimeSettingsService.getRuntimeSettings();

            logTtsRequestStart(runtimeSettings, invocation, model, endpoint, effectiveVoiceName, normalizedText.length());

            long startedAt = System.nanoTime();
            String body = executeJsonRequest(invocation, requestSpec, payload, requestBody, runtimeSettings);
            JsonNode root = readJson(body, invocation, endpoint, requestBody, runtimeSettings);
            InlineAudioPayload inlineAudio = extractGeminiInlineAudio(root);
            if (inlineAudio == null || inlineAudio.data() == null || inlineAudio.data().isBlank()) {
                logInvalidResponse(runtimeSettings, "tts audio inlineData blank", invocation, endpoint, requestBody, body);
                throw new ApiException("AI-2103", "语音播报生成失败，本次将先保留文字内容。", HttpStatus.BAD_GATEWAY);
            }
            int requestTokens = root.path("usageMetadata").path("promptTokenCount").asInt(0);
            int responseTokens = root.path("usageMetadata").path("candidatesTokenCount").asInt(0);
            int totalTokens = root.path("usageMetadata").path("totalTokenCount").asInt(requestTokens + responseTokens);
            int thoughtsTokens = extractThoughtsTokens(root);
            BigDecimal estimatedCost = calculateEstimatedCost(requestTokens, responseTokens, invocation);
            long latencyMs = Math.max(Duration.ofNanos(System.nanoTime() - startedAt).toMillis(), 1L);
            logRequestSuccess(runtimeSettings, invocation, model, latencyMs, requestTokens, responseTokens, totalTokens, thoughtsTokens, GeminiThinkingResolution.none());
            return new AiProviderTextToSpeechResult(
                    invocation.taskType(),
                    inlineAudio.mimeType(),
                    inlineAudio.data(),
                    effectiveVoiceName,
                    invocation.providerCode(),
                    model,
                    latencyMs,
                    requestTokens,
                    responseTokens,
                    totalTokens,
                    thoughtsTokens,
                    null,
                    null,
                    null,
                    estimatedCost
            );
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error(
                    "gemini provider tts unexpected failure taskType={}, sceneCode={}, routeCode={}, provider={}, model={}, timeoutMs={}, maxRetries={}, url={}, voiceName={}, inputChars={}, errorType={}, errorMessage={}",
                    invocation.taskType(),
                    invocation.sceneCode(),
                    invocation.routeCode(),
                    invocation.providerCode(),
                    invocation.model(),
                    invocation.timeout().toMillis(),
                    invocation.maxRetries(),
                    sanitizeEndpoint(endpoint),
                    effectiveVoiceName,
                    inputChars,
                    throwableType(ex),
                    summarizeException(ex),
                    ex
            );
            throw new ApiException("AI-2103", "语音播报生成失败，本次将先保留文字内容。", HttpStatus.BAD_GATEWAY, ex);
        }
    }


    private Map<String, Object> buildResumePdfPayload(
            AiProviderInvocation invocation,
            String targetRole,
            String targetContext,
            String jobDescription,
            String resumeFileName,
            String mimeType,
            byte[] pdfBytes,
            GeminiThinkingResolution thinkingResolution
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (invocation.systemPrompt() != null && !invocation.systemPrompt().isBlank()) {
            payload.put("systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", invocation.systemPrompt().trim()))
            ));
        }
        payload.put("contents", List.of(
                Map.of(
                        "role", "user",
                        "parts", List.of(
                                Map.of("text", buildResumePdfPrompt(targetRole, targetContext, jobDescription, resumeFileName)),
                                Map.of("inlineData", Map.of(
                                        "mimeType", mimeType,
                                        "data", Base64.getEncoder().encodeToString(pdfBytes)
                                ))
                        )
                )
        ));
        payload.put("tools", List.of());
        payload.put("safetySettings", List.of());
        payload.put("generationConfig", buildStructuredJsonGenerationConfig(invocation, thinkingResolution));
        return payload;
    }

    private String buildResumePdfPrompt(
            String targetRole,
            String targetContext,
            String jobDescription,
            String resumeFileName
    ) {
        String normalizedRole = targetRole == null ? "" : targetRole.trim();
        String normalizedContext = targetContext == null ? "" : targetContext.trim();
        String normalizedJobDescription = jobDescription == null ? "" : jobDescription.trim();
        String normalizedFileName = resumeFileName == null ? "" : resumeFileName.trim();
        return DEFAULT_RESUME_PDF_INSTRUCTION
                + "\ntargetRole=" + normalizedRole
                + "\ntargetContext=" + normalizedContext
                + "\nresumeFileName=" + normalizedFileName
                + "\njobDescription=\n" + normalizedJobDescription
                + "\n请重点总结与目标岗位的匹配度、量化成果、技术亮点、表达风险、结构完整度与可执行优化建议，并尽量给出 3-4 条 Before / After 改写示例。";
    }

    private Map<String, Object> buildTextToSpeechPayload(
            AiProviderInvocation invocation,
            String text,
            String stylePrompt,
            String voiceName
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (invocation.systemPrompt() != null && !invocation.systemPrompt().isBlank()) {
            payload.put("systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", invocation.systemPrompt().trim()))
            ));
        }
        payload.put("contents", List.of(
                Map.of(
                        "parts", List.of(
                                Map.of("text", stylePrompt.trim() + "\n" + text.trim())
                        )
                )
        ));
        payload.put("generationConfig", Map.of(
                "responseModalities", List.of("AUDIO"),
                "speechConfig", Map.of(
                        "voiceConfig", Map.of(
                                "prebuiltVoiceConfig", Map.of(
                                        "voiceName", voiceName.trim()
                                )
                        )
                )
        ));
        return payload;
    }

    private Map<String, Object> buildChatPayload(
            AiProviderInvocation invocation,
            List<AiChatMessage> messages,
            GeminiThinkingResolution thinkingResolution
    ) {
        List<String> systemPrompts = new ArrayList<>();
        if (invocation.systemPrompt() != null && !invocation.systemPrompt().isBlank()) {
            systemPrompts.add(invocation.systemPrompt().trim());
        }
        List<Map<String, Object>> contents = new ArrayList<>();
        for (AiChatMessage message : messages == null ? List.<AiChatMessage>of() : messages) {
            if (message == null || message.content() == null || message.content().isBlank()) {
                continue;
            }
            if ("system".equalsIgnoreCase(message.role()) || "developer".equalsIgnoreCase(message.role())) {
                systemPrompts.add(message.content().trim());
                continue;
            }
            String role = "assistant".equalsIgnoreCase(message.role()) || "model".equalsIgnoreCase(message.role()) ? "model" : "user";
            contents.add(Map.of(
                    "role", role,
                    "parts", List.of(Map.of("text", message.content()))
            ));
        }
        if (contents.isEmpty()) {
            contents.add(Map.of(
                    "role", "user",
                    "parts", List.of(Map.of("text", "请返回一个简短 JSON 响应。"))
            ));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        if (!systemPrompts.isEmpty()) {
            payload.put("systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", String.join("\n\n", systemPrompts)))
            ));
        }
        payload.put("contents", contents);
        payload.put("generationConfig", buildStructuredJsonGenerationConfig(invocation, thinkingResolution));
        return payload;
    }

    private Map<String, Object> buildTranscriptionPayload(AiProviderInvocation invocation, String instruction, String mimeType, byte[] audioBytes) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (invocation.systemPrompt() != null && !invocation.systemPrompt().isBlank()) {
            payload.put("systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", invocation.systemPrompt().trim()))
            ));
        }
        payload.put("contents", List.of(
                Map.of(
                        "role", "user",
                        "parts", List.of(
                                Map.of("text", instruction),
                                Map.of("inlineData", Map.of(
                                        "mimeType", mimeType,
                                        "data", Base64.getEncoder().encodeToString(audioBytes)
                                ))
                        )
                )
        ));
        payload.put("generationConfig", Map.of("temperature", invocation.temperature()));
        return payload;
    }

    private Map<String, Object> buildStructuredJsonGenerationConfig(
            AiProviderInvocation invocation,
            GeminiThinkingResolution thinkingResolution
    ) {
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", invocation.temperature());
        generationConfig.put("responseMimeType", JSON_RESPONSE_MIME_TYPE);
        if (thinkingResolution != null && thinkingResolution.transportEnabled()) {
            Map<String, Object> thinkingConfig = new LinkedHashMap<>();
            if (thinkingResolution.thinkingBudget() != null) {
                thinkingConfig.put("thinkingBudget", thinkingResolution.thinkingBudget());
            }
            if (thinkingResolution.thinkingLevel() != null && !thinkingResolution.thinkingLevel().isBlank()) {
                thinkingConfig.put("thinkingLevel", thinkingResolution.thinkingLevel());
            }
            if (!thinkingConfig.isEmpty()) {
                generationConfig.put("thinkingConfig", thinkingConfig);
            }
        }
        Map<String, Object> responseJsonSchema = resolveResponseJsonSchema(invocation);
        if (responseJsonSchema != null && !responseJsonSchema.isEmpty()) {
            generationConfig.put("responseJsonSchema", responseJsonSchema);
        }
        return generationConfig;
    }

    private GeminiThinkingResolution resolveGeminiThinking(AiProviderInvocation invocation) {
        if (invocation == null || invocation.thinkingConfig() == null || !invocation.thinkingConfig().isConfigured()) {
            return GeminiThinkingResolution.none();
        }
        AiThinkingConfig thinkingConfig = invocation.thinkingConfig();
        AiReasoningEffort reasoningEffort = thinkingConfig.reasoningEffort() == null
                ? AiReasoningEffort.DYNAMIC
                : thinkingConfig.reasoningEffort();
        String normalizedModel = normalizeModelName(invocation.model());
        if (normalizedModel.startsWith("GEMINI-3")) {
            return resolveGemini3Thinking(reasoningEffort, thinkingConfig, normalizedModel);
        }
        return resolveGemini25Thinking(reasoningEffort, thinkingConfig);
    }

    private GeminiThinkingResolution resolveGemini25Thinking(
            AiReasoningEffort reasoningEffort,
            AiThinkingConfig thinkingConfig
    ) {
        Integer budget = thinkingConfig.thinkingBudget();
        if (budget == null && thinkingConfig.thinkingLevel() != null) {
            budget = mapThinkingLevelToBudget(thinkingConfig.thinkingLevel());
        }
        if (budget == null) {
            budget = switch (reasoningEffort) {
                case OFF -> 0;
                case LOW -> 256;
                case MEDIUM -> 1024;
                case HIGH -> 2048;
                case DYNAMIC -> null;
            };
        }
        boolean transportEnabled = budget != null;
        return new GeminiThinkingResolution(
                reasoningEffort.name(),
                budget,
                null,
                transportEnabled
        );
    }

    private GeminiThinkingResolution resolveGemini3Thinking(
            AiReasoningEffort reasoningEffort,
            AiThinkingConfig thinkingConfig,
            String normalizedModel
    ) {
        String thinkingLevel = normalizeThinkingLevelForModel(thinkingConfig.thinkingLevel(), normalizedModel);
        if (thinkingLevel == null && thinkingConfig.thinkingBudget() != null) {
            thinkingLevel = mapThinkingBudgetToLevel(thinkingConfig.thinkingBudget(), normalizedModel);
        }
        if (thinkingLevel == null) {
            thinkingLevel = mapReasoningEffortToThinkingLevel(reasoningEffort, normalizedModel);
        }
        boolean transportEnabled = thinkingLevel != null && !thinkingLevel.isBlank();
        return new GeminiThinkingResolution(
                reasoningEffort.name(),
                null,
                thinkingLevel,
                transportEnabled
        );
    }

    private String mapReasoningEffortToThinkingLevel(AiReasoningEffort reasoningEffort, String normalizedModel) {
        boolean proModel = normalizedModel.contains("PRO");
        return switch (reasoningEffort) {
            case OFF -> proModel ? "low" : "minimal";
            case LOW -> "low";
            case MEDIUM -> proModel ? "high" : "medium";
            case HIGH -> "high";
            case DYNAMIC -> null;
        };
    }

    private String normalizeThinkingLevelForModel(String rawThinkingLevel, String normalizedModel) {
        if (rawThinkingLevel == null || rawThinkingLevel.isBlank()) {
            return null;
        }
        String normalized = rawThinkingLevel.trim().toLowerCase(Locale.ROOT);
        boolean proModel = normalizedModel.contains("PRO");
        return switch (normalized) {
            case "off", "none", "minimal" -> proModel ? "low" : "minimal";
            case "low" -> "low";
            case "medium", "balanced", "standard" -> proModel ? "high" : "medium";
            case "high", "max" -> "high";
            case "dynamic", "auto", "default" -> null;
            default -> normalized;
        };
    }

    private String mapThinkingBudgetToLevel(Integer thinkingBudget, String normalizedModel) {
        if (thinkingBudget == null) {
            return null;
        }
        boolean proModel = normalizedModel.contains("PRO");
        if (thinkingBudget <= 0) {
            return proModel ? "low" : "minimal";
        }
        if (thinkingBudget <= 384) {
            return "low";
        }
        if (thinkingBudget <= 1280) {
            return proModel ? "high" : "medium";
        }
        return "high";
    }

    private Integer mapThinkingLevelToBudget(String thinkingLevel) {
        if (thinkingLevel == null || thinkingLevel.isBlank()) {
            return null;
        }
        return switch (thinkingLevel.trim().toLowerCase(Locale.ROOT)) {
            case "off", "none", "minimal" -> 0;
            case "low" -> 256;
            case "medium", "balanced", "standard" -> 1024;
            case "high", "max" -> 2048;
            default -> null;
        };
    }

    private String normalizeModelName(String model) {
        if (model == null || model.isBlank()) {
            return "";
        }
        String normalized = model.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("MODELS/")) {
            return normalized.substring("MODELS/".length());
        }
        return normalized;
    }

    private Map<String, Object> resolveResponseJsonSchema(AiProviderInvocation invocation) {
        return AiStructuredOutputSchemas.resolveResponseJsonSchema(invocation.taskType(), invocation.sceneCode());
    }

    private String executeJsonRequest(
            AiProviderInvocation invocation,
            GeminiTransportResolver.RequestSpec requestSpec,
            Map<String, Object> payload,
            String requestBody,
            AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot runtimeSettings
    ) {
        WebClient client = buildWebClient();
        return client.post()
                .uri(requestSpec.endpoint())
                .headers(headers -> requestSpec.headers().forEach(headers::set))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(payload)
                .retrieve()
                .onStatus(
                        status -> status.isError(),
                        response -> response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(errorBody -> Mono.error(new ProviderHttpException(response.statusCode().value(), errorBody)))
                )
                .bodyToMono(String.class)
                .timeout(invocation.timeout())
                .retryWhen(Retry.max(Math.max(invocation.maxRetries(), 0))
                        .filter(this::shouldRetry)
                        .doBeforeRetry(retrySignal -> logRetry(runtimeSettings, invocation, requestSpec.endpoint(), retrySignal.totalRetries() + 1, retrySignal.failure())))
                .onErrorMap(throwable -> mapProviderException(runtimeSettings, invocation, requestSpec.endpoint(), requestBody, throwable))
                .block();
    }

    private StreamExecutionResult executeJsonStreamRequest(
            AiProviderInvocation invocation,
            GeminiTransportResolver.RequestSpec requestSpec,
            Map<String, Object> payload,
            String requestBody,
            AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot runtimeSettings,
            Consumer<String> deltaConsumer
    ) {
        WebClient client = buildWebClient();
        StringBuilder eventBuffer = new StringBuilder();
        StringBuilder rawStreamBody = new StringBuilder();
        StringBuilder contentBuilder = new StringBuilder();
        StreamTokenUsage usage = new StreamTokenUsage();
        try {
            client.post()
                    .uri(requestSpec.endpoint())
                    .headers(headers -> requestSpec.headers().forEach(headers::set))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .bodyValue(payload)
                    .retrieve()
                    .onStatus(
                            status -> status.isError(),
                            response -> response.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(errorBody -> Mono.error(new ProviderHttpException(response.statusCode().value(), errorBody)))
                    )
                    .bodyToFlux(String.class)
                    .timeout(invocation.timeout())
                    .doOnNext(chunk -> appendStreamChunk(chunk, eventBuffer, rawStreamBody, contentBuilder, usage, deltaConsumer))
                    .blockLast();
            flushStreamBuffer(eventBuffer, rawStreamBody, contentBuilder, usage, deltaConsumer);
            return new StreamExecutionResult(
                    contentBuilder.toString().trim(),
                    usage.requestTokens,
                    usage.responseTokens,
                    usage.totalTokens,
                    usage.thoughtsTokens,
                    rawStreamBody.toString()
            );
        } catch (Throwable throwable) {
            throw (RuntimeException) mapProviderException(runtimeSettings, invocation, requestSpec.endpoint(), requestBody, throwable);
        }
    }

    private boolean shouldRetry(Throwable throwable) {
        if (throwable instanceof ProviderHttpException httpException) {
            return httpException.statusCode() == 429 || httpException.statusCode() >= 500;
        }
        return throwable instanceof TimeoutException
                || throwable instanceof SocketTimeoutException
                || throwable instanceof WebClientRequestException;
    }

    private void appendStreamChunk(
            String chunk,
            StringBuilder eventBuffer,
            StringBuilder rawStreamBody,
            StringBuilder contentBuilder,
            StreamTokenUsage usage,
            Consumer<String> deltaConsumer
    ) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        String normalized = chunk.replace("\r\n", "\n");
        rawStreamBody.append(normalized);
        String trimmed = normalized.trim();
        if (!trimmed.isBlank() && isStandaloneStreamEvent(trimmed) && !normalized.contains("data:")) {
            handleStreamEvent(trimmed, contentBuilder, usage, deltaConsumer);
            return;
        }
        eventBuffer.append(normalized);
        int separatorIndex = eventBuffer.indexOf("\n\n");
        while (separatorIndex >= 0) {
            String eventBlock = eventBuffer.substring(0, separatorIndex);
            eventBuffer.delete(0, separatorIndex + 2);
            handleStreamEvent(eventBlock, contentBuilder, usage, deltaConsumer);
            separatorIndex = eventBuffer.indexOf("\n\n");
        }
    }

    private void flushStreamBuffer(
            StringBuilder eventBuffer,
            StringBuilder rawStreamBody,
            StringBuilder contentBuilder,
            StreamTokenUsage usage,
            Consumer<String> deltaConsumer
    ) {
        if (eventBuffer.length() == 0) {
            return;
        }
        String trailing = eventBuffer.toString().trim();
        eventBuffer.setLength(0);
        if (!trailing.isBlank()) {
            rawStreamBody.append("\n");
            handleStreamEvent(trailing, contentBuilder, usage, deltaConsumer);
        }
    }

    private void handleStreamEvent(
            String eventBlock,
            StringBuilder contentBuilder,
            StreamTokenUsage usage,
            Consumer<String> deltaConsumer
    ) {
        if (eventBlock == null || eventBlock.isBlank()) {
            return;
        }
        String data = extractEventData(eventBlock);
        if (data.isBlank() || "[DONE]".equalsIgnoreCase(data)) {
            return;
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(data);
        } catch (Exception ex) {
            throw new ApiException("AI-2001", "AI 服务返回了无效内容，请稍后重试。", HttpStatus.BAD_GATEWAY);
        }
        JsonNode usageMetadata = root.path("usageMetadata");
        if (!usageMetadata.isMissingNode() && !usageMetadata.isNull()) {
            usage.requestTokens = usageMetadata.path("promptTokenCount").asInt(usage.requestTokens);
            usage.responseTokens = usageMetadata.path("candidatesTokenCount").asInt(usage.responseTokens);
            usage.totalTokens = usageMetadata.path("totalTokenCount").asInt(usage.requestTokens + usage.responseTokens);
            usage.thoughtsTokens = usageMetadata.path("thoughtsTokenCount").asInt(usage.thoughtsTokens);
        }
        String deltaText = extractGeminiText(root);
        if (!deltaText.isBlank()) {
            contentBuilder.append(deltaText);
            if (deltaConsumer != null) {
                deltaConsumer.accept(deltaText);
            }
        }
    }

    private String extractEventData(String eventBlock) {
        String trimmedBlock = eventBlock.trim();
        if (isStandaloneStreamEvent(trimmedBlock)) {
            return trimmedBlock;
        }
        StringBuilder dataBuilder = new StringBuilder();
        for (String line : eventBlock.split("\n")) {
            if (line == null || line.isBlank() || line.startsWith(":")) {
                continue;
            }
            if (line.startsWith("data:")) {
                if (dataBuilder.length() > 0) {
                    dataBuilder.append('\n');
                }
                dataBuilder.append(line.substring(5).trim());
            }
        }
        return dataBuilder.toString().trim();
    }

    private boolean isStandaloneStreamEvent(String eventBlock) {
        if (eventBlock == null || eventBlock.isBlank()) {
            return false;
        }
        String trimmed = eventBlock.trim();
        return "[DONE]".equalsIgnoreCase(trimmed) || (trimmed.startsWith("{") && trimmed.endsWith("}"));
    }

    private Throwable mapProviderException(
            AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot runtimeSettings,
            AiProviderInvocation invocation,
            String endpoint,
            String requestBody,
            Throwable throwable
    ) {
        if (throwable instanceof ApiException) {
            return throwable;
        }
        if (throwable instanceof ProviderHttpException httpException) {
            logHttpError(runtimeSettings, invocation, endpoint, requestBody, httpException.statusCode(), httpException.body());
            return AiProviderErrorMapper.mapHttpError(objectMapper, httpException.statusCode(), httpException.body(), throwable);
        }
        logTransportError(runtimeSettings, invocation, endpoint, requestBody, throwable);
        return AiProviderErrorMapper.mapTransportError(throwable);
    }

    private BigDecimal calculateEstimatedCost(int requestTokens, int responseTokens, AiProviderInvocation invocation) {
        BigDecimal inputCost = BigDecimal.valueOf(requestTokens)
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP)
                .multiply(invocation.costPer1kInput() == null ? BigDecimal.ZERO : invocation.costPer1kInput());
        BigDecimal outputCost = BigDecimal.valueOf(responseTokens)
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP)
                .multiply(invocation.costPer1kOutput() == null ? BigDecimal.ZERO : invocation.costPer1kOutput());
        return inputCost.add(outputCost).setScale(6, RoundingMode.HALF_UP);
    }

    private int extractThoughtsTokens(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return 0;
        }
        return root.path("usageMetadata").path("thoughtsTokenCount").asInt(0);
    }

    private String extractGeminiText(JsonNode root) {
        JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray()) {
            return "";
        }
        List<String> texts = new ArrayList<>();
        for (JsonNode part : parts) {
            String text = part.path("text").asText("").trim();
            if (!text.isBlank()) {
                texts.add(text);
            }
        }
        return String.join("\n", texts).trim();
    }

    private InlineAudioPayload extractGeminiInlineAudio(JsonNode root) {
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray()) {
            return null;
        }
        for (JsonNode candidate : candidates) {
            JsonNode parts = candidate.path("content").path("parts");
            if (!parts.isArray()) {
                continue;
            }
            for (JsonNode part : parts) {
                JsonNode inlineData = part.path("inlineData");
                if (inlineData.isMissingNode() || inlineData.isNull()) {
                    inlineData = part.path("inline_data");
                }
                if (inlineData.isMissingNode() || inlineData.isNull()) {
                    continue;
                }
                String mimeType = firstNonBlankText(
                        inlineData.path("mimeType").asText(""),
                        inlineData.path("mime_type").asText("")
                );
                String data = inlineData.path("data").asText("").trim();
                if (!mimeType.isBlank() && !data.isBlank()) {
                    return new InlineAudioPayload(mimeType, data);
                }
            }
        }
        return null;
    }

    private JsonNode readJson(
            String body,
            AiProviderInvocation invocation,
            String endpoint,
            String requestBody,
            AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot runtimeSettings
    ) {
        try {
            return objectMapper.readTree(body == null || body.isBlank() ? "{}" : body);
        } catch (Exception ex) {
            logInvalidResponse(runtimeSettings, "response body is not valid JSON", invocation, endpoint, requestBody, body);
            throw new ApiException("AI-2001", "AI 服务返回了无效内容，请稍后重试。", HttpStatus.BAD_GATEWAY, ex);
        }
    }

    private String readExtraConfigText(String json, String fieldName, String fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            String value = node.path(fieldName).asText("").trim();
            return value.isBlank() ? fallback : value;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private WebClient buildWebClient() {
        return webClientBuilder.clone()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_PROVIDER_RESPONSE_BYTES))
                .build();
    }

    private void logInvalidResponse(
            AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot runtimeSettings,
            String reason,
            AiProviderInvocation invocation,
            String endpoint,
            String requestBody,
            String responseBody
    ) {
        if (runtimeSettings.aiRequestLogEnabled()) {
            log.error(
                    "gemini provider invalid response taskType={}, sceneCode={}, routeCode={}, provider={}, model={}, timeoutMs={}, maxRetries={}, url={}, reason={}, requestBody={}, responseBody={}",
                    invocation.taskType(),
                    invocation.sceneCode(),
                    invocation.routeCode(),
                    invocation.providerCode(),
                    invocation.model(),
                    invocation.timeout().toMillis(),
                    invocation.maxRetries(),
                    sanitizeEndpoint(endpoint),
                    reason,
                    bodyForLog(requestBody),
                    bodyForLog(responseBody)
            );
            return;
        }
        log.error(
                "gemini provider invalid response taskType={}, sceneCode={}, routeCode={}, provider={}, model={}, timeoutMs={}, maxRetries={}, url={}, reason={}, requestSize={}, responseSize={}, responseBodySnippet={}",
                invocation.taskType(),
                invocation.sceneCode(),
                invocation.routeCode(),
                invocation.providerCode(),
                invocation.model(),
                invocation.timeout().toMillis(),
                invocation.maxRetries(),
                sanitizeEndpoint(endpoint),
                reason,
                lengthOf(requestBody),
                lengthOf(responseBody),
                bodyForLog(responseBody)
        );
    }

    private void logHttpError(
            AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot runtimeSettings,
            AiProviderInvocation invocation,
            String endpoint,
            String requestBody,
            int statusCode,
            String responseBody
    ) {
        if (runtimeSettings.aiRequestLogEnabled()) {
            log.error(
                    "gemini provider http error taskType={}, sceneCode={}, routeCode={}, provider={}, model={}, timeoutMs={}, maxRetries={}, status={}, url={}, requestBody={}, responseBody={}",
                    invocation.taskType(),
                    invocation.sceneCode(),
                    invocation.routeCode(),
                    invocation.providerCode(),
                    invocation.model(),
                    invocation.timeout().toMillis(),
                    invocation.maxRetries(),
                    statusCode,
                    sanitizeEndpoint(endpoint),
                    bodyForLog(requestBody),
                    bodyForLog(responseBody)
            );
            return;
        }
        log.error(
                "gemini provider http error taskType={}, sceneCode={}, routeCode={}, provider={}, model={}, timeoutMs={}, maxRetries={}, status={}, url={}, requestSize={}, responseSize={}, responseBodySnippet={}",
                invocation.taskType(),
                invocation.sceneCode(),
                invocation.routeCode(),
                invocation.providerCode(),
                invocation.model(),
                invocation.timeout().toMillis(),
                invocation.maxRetries(),
                statusCode,
                sanitizeEndpoint(endpoint),
                lengthOf(requestBody),
                lengthOf(responseBody),
                bodyForLog(responseBody)
        );
    }

    private void logTransportError(
            AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot runtimeSettings,
            AiProviderInvocation invocation,
            String endpoint,
            String requestBody,
            Throwable throwable
    ) {
        if (runtimeSettings.aiRequestLogEnabled()) {
            log.error(
                    "gemini provider transport error taskType={}, sceneCode={}, routeCode={}, provider={}, model={}, timeoutMs={}, maxRetries={}, url={}, requestBody={}, errorType={}, errorMessage={}",
                    invocation.taskType(),
                    invocation.sceneCode(),
                    invocation.routeCode(),
                    invocation.providerCode(),
                    invocation.model(),
                    invocation.timeout().toMillis(),
                    invocation.maxRetries(),
                    sanitizeEndpoint(endpoint),
                    bodyForLog(requestBody),
                    throwableType(throwable),
                    summarizeException(throwable),
                    throwable
            );
            return;
        }
        log.error(
                "gemini provider transport error taskType={}, sceneCode={}, routeCode={}, provider={}, model={}, timeoutMs={}, maxRetries={}, url={}, requestSize={}, errorType={}, errorMessage={}",
                invocation.taskType(),
                invocation.sceneCode(),
                invocation.routeCode(),
                invocation.providerCode(),
                invocation.model(),
                invocation.timeout().toMillis(),
                invocation.maxRetries(),
                sanitizeEndpoint(endpoint),
                lengthOf(requestBody),
                throwableType(throwable),
                summarizeException(throwable),
                throwable
        );
    }

    private void logJsonRequestStart(
            AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot runtimeSettings,
            AiProviderInvocation invocation,
            String model,
            String endpoint,
            int messageCount,
            int inputChars
    ) {
        if (!runtimeSettings.aiRequestLogEnabled()) {
            return;
        }
        log.info(
                "gemini provider request start taskType={}, provider={}, model={}, url={}, timeoutMs={}, maxRetries={}, messageCount={}, inputChars={}, apiKey={}",
                invocation.taskType(),
                invocation.providerCode(),
                model,
                sanitizeEndpoint(endpoint),
                invocation.timeout().toMillis(),
                invocation.maxRetries(),
                messageCount,
                inputChars,
                maskSecret(invocation.apiKey())
        );
    }


    private void logResumePdfRequestStart(
            AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot runtimeSettings,
            AiProviderInvocation invocation,
            String model,
            String endpoint,
            String mimeType,
            int bytes
    ) {
        if (!runtimeSettings.aiRequestLogEnabled()) {
            return;
        }
        log.info(
                "gemini provider resume pdf start taskType={}, provider={}, model={}, url={}, mimeType={}, bytes={}, apiKey={}",
                invocation.taskType(),
                invocation.providerCode(),
                model,
                sanitizeEndpoint(endpoint),
                mimeType,
                bytes,
                maskSecret(invocation.apiKey())
        );
    }

    private void logTranscribeRequestStart(
            AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot runtimeSettings,
            AiProviderInvocation invocation,
            String model,
            String endpoint,
            String mimeType,
            int bytes
    ) {
        if (!runtimeSettings.aiRequestLogEnabled()) {
            return;
        }
        log.info(
                "gemini provider transcribe start taskType={}, provider={}, model={}, url={}, mimeType={}, bytes={}, apiKey={}",
                invocation.taskType(),
                invocation.providerCode(),
                model,
                sanitizeEndpoint(endpoint),
                mimeType,
                bytes,
                maskSecret(invocation.apiKey())
        );
    }

    private void logTtsRequestStart(
            AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot runtimeSettings,
            AiProviderInvocation invocation,
            String model,
            String endpoint,
            String voiceName,
            int inputChars
    ) {
        if (!runtimeSettings.aiRequestLogEnabled()) {
            return;
        }
        log.info(
                "gemini provider tts start taskType={}, provider={}, model={}, url={}, voiceName={}, inputChars={}, apiKey={}",
                invocation.taskType(),
                invocation.providerCode(),
                model,
                sanitizeEndpoint(endpoint),
                voiceName,
                inputChars,
                maskSecret(invocation.apiKey())
        );
    }

    private void logRequestSuccess(
            AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot runtimeSettings,
            AiProviderInvocation invocation,
            String model,
            long latencyMs,
            int requestTokens,
            int responseTokens,
            int totalTokens,
            int thoughtsTokens,
            GeminiThinkingResolution thinkingResolution
    ) {
        if (!runtimeSettings.debugModeEnabled() && !runtimeSettings.aiRequestLogEnabled()) {
            return;
        }
        log.info(
                "gemini provider request success taskType={}, provider={}, model={}, latencyMs={}, requestTokens={}, responseTokens={}, totalTokens={}, thoughtsTokens={}, reasoningEffort={}, thinkingBudget={}, thinkingLevel={}",
                invocation.taskType(),
                invocation.providerCode(),
                model,
                latencyMs,
                requestTokens,
                responseTokens,
                totalTokens,
                thoughtsTokens,
                thinkingResolution == null ? null : thinkingResolution.reasoningEffort(),
                thinkingResolution == null ? null : thinkingResolution.thinkingBudget(),
                thinkingResolution == null ? null : thinkingResolution.thinkingLevel()
        );
    }

    private void logRetry(
            AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot runtimeSettings,
            AiProviderInvocation invocation,
            String endpoint,
            long attemptNo,
            Throwable throwable
    ) {
        if (!runtimeSettings.debugModeEnabled() && !runtimeSettings.aiRequestLogEnabled()) {
            return;
        }
        log.warn(
                "gemini provider retry scheduled taskType={}, provider={}, model={}, url={}, attempt={}, maxRetries={}, reason={}",
                invocation.taskType(),
                invocation.providerCode(),
                invocation.model(),
                sanitizeEndpoint(endpoint),
                attemptNo,
                invocation.maxRetries(),
                summarizeException(throwable)
        );
    }

    private int totalInputChars(List<AiChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        return messages.stream()
                .filter(message -> message != null && message.content() != null)
                .mapToInt(message -> message.content().length())
                .sum();
    }

    private String sanitizeEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "";
        }
        int keyIndex = endpoint.indexOf("?key=");
        if (keyIndex < 0) {
            return endpoint;
        }
        return endpoint.substring(0, keyIndex + 5) + "***";
    }

    private String writeJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return String.valueOf(payload);
        }
    }

    private String bodyForLog(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String normalized = body.trim();
        if (normalized.length() <= MAX_LOG_BODY_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_LOG_BODY_LENGTH) + "...(truncated,len=" + normalized.length() + ")";
    }

    private int lengthOf(String body) {
        return body == null ? 0 : body.length();
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ApiException("AI-2001", message, HttpStatus.BAD_GATEWAY);
        }
        return value.trim();
    }

    private String maskSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return "missing";
        }
        String trimmed = secret.trim();
        int visible = Math.min(4, trimmed.length());
        return "***" + trimmed.substring(trimmed.length() - visible) + "(len=" + trimmed.length() + ")";
    }

    private String safeValue(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonBlankText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String summarizeException(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        String normalized = message.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 240 ? normalized : normalized.substring(0, 240) + "...";
    }

    private String throwableType(Throwable throwable) {
        return throwable == null ? "" : throwable.getClass().getName();
    }

    private record InlineAudioPayload(
            String mimeType,
            String data
    ) {
    }

    private record StreamExecutionResult(
            String content,
            int requestTokens,
            int responseTokens,
            int totalTokens,
            int thoughtsTokens,
            String rawStreamBody
    ) {
    }

    private static final class StreamTokenUsage {
        private int requestTokens;
        private int responseTokens;
        private int totalTokens;
        private int thoughtsTokens;
    }

    private record GeminiThinkingResolution(
            String reasoningEffort,
            Integer thinkingBudget,
            String thinkingLevel,
            boolean transportEnabled
    ) {
        private static GeminiThinkingResolution none() {
            return new GeminiThinkingResolution(null, null, null, false);
        }
    }

    private static final class ProviderHttpException extends RuntimeException {
        private final int statusCode;
        private final String body;

        private ProviderHttpException(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        private int statusCode() {
            return statusCode;
        }

        private String body() {
            return body;
        }
    }
}
