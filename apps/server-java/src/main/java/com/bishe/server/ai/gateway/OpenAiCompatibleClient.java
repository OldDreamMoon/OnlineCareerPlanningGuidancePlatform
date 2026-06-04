package com.bishe.server.ai.gateway;

import com.bishe.server.common.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
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
import java.util.function.Consumer;
import java.util.concurrent.TimeoutException;

/**
 * OpenAI-compatible provider HTTP 客户端。
 */
@Component
public class OpenAiCompatibleClient implements AiProviderClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);

    private static final int MAX_LOG_BODY_LENGTH = 4000;

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final AiGatewayRuntimeSettingsService runtimeSettingsService;

    public OpenAiCompatibleClient(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            AiGatewayRuntimeSettingsService runtimeSettingsService
    ) {
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
        this.runtimeSettingsService = runtimeSettingsService;
    }

    @Override
    public AiProviderType providerType() {
        return AiProviderType.OPENAI_COMPATIBLE;
    }

    @Override
    public AiProviderChatResult chatJson(AiProviderInvocation invocation, List<AiChatMessage> messages) {
        String baseUrl = normalizeBaseUrl(invocation.baseUrl());
        String model = normalizeRequired(invocation.model(), "provider model missing");
        String endpoint = baseUrl + "/chat/completions";
        List<AiChatMessage> effectiveMessages = mergeSystemPrompt(invocation.systemPrompt(), messages);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("temperature", invocation.temperature());
        payload.put("stream", false);
        Map<String, Object> responseFormat = resolveStructuredResponseFormat(invocation);
        if (responseFormat != null && !responseFormat.isEmpty()) {
            payload.put("response_format", responseFormat);
        }
        payload.put("messages", effectiveMessages.stream().map(message -> Map.of(
                "role", normalizeMessageRole(message.role()),
                "content", message.content()
        )).toList());
        AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot runtimeSettings = runtimeSettingsService.getRuntimeSettings();
        String requestBody = writeJson(payload);

        logJsonRequestStart(runtimeSettings, invocation, model, endpoint, effectiveMessages.size(), totalInputChars(effectiveMessages));

        long startedAt = System.nanoTime();
        String body = executeJsonRequest(invocation, endpoint, payload, requestBody, runtimeSettings);
        JsonNode root = readJson(body, invocation, endpoint, requestBody, runtimeSettings);
        String content = extractContent(root.path("choices").path(0).path("message").path("content"));
        if (content.isBlank()) {
            logInvalidResponse(runtimeSettings, "message content blank", invocation, endpoint, requestBody, body);
            throw new ApiException("AI-2001", "AI 服务返回了无效内容，请稍后重试。", HttpStatus.BAD_GATEWAY);
        }
        int requestTokens = root.path("usage").path("prompt_tokens").asInt(0);
        int responseTokens = root.path("usage").path("completion_tokens").asInt(0);
        int totalTokens = root.path("usage").path("total_tokens").asInt(requestTokens + responseTokens);
        BigDecimal estimatedCost = calculateEstimatedCost(requestTokens, responseTokens, invocation);
        long latencyMs = Math.max(Duration.ofNanos(System.nanoTime() - startedAt).toMillis(), 1L);
        logRequestSuccess(runtimeSettings, invocation, model, latencyMs, requestTokens, responseTokens, totalTokens);
        return new AiProviderChatResult(
                invocation.taskType(),
                content,
                invocation.providerCode(),
                model,
                latencyMs,
                requestTokens,
                responseTokens,
                totalTokens,
                0,
                invocation.thinkingConfig() == null ? null : invocation.thinkingConfig().reasoningEffortCode(),
                null,
                null,
                estimatedCost
        );
    }

    @Override
    public AiProviderTextToSpeechResult synthesizeSpeech(
            AiProviderInvocation invocation,
            String text,
            String stylePrompt,
            String voiceName
    ) {
        throw new ApiException("AI-2001", "当前 AI 服务不支持语音播报。", HttpStatus.BAD_GATEWAY);
    }

    @Override
    public AiProviderChatResult chatJsonStream(
            AiProviderInvocation invocation,
            List<AiChatMessage> messages,
            Consumer<String> deltaConsumer
    ) {
        String baseUrl = normalizeBaseUrl(invocation.baseUrl());
        String model = normalizeRequired(invocation.model(), "provider model missing");
        String endpoint = baseUrl + "/chat/completions";
        List<AiChatMessage> effectiveMessages = mergeSystemPrompt(invocation.systemPrompt(), messages);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("temperature", invocation.temperature());
        payload.put("stream", true);
        payload.put("stream_options", Map.of("include_usage", true));
        Map<String, Object> responseFormat = resolveStructuredResponseFormat(invocation);
        if (responseFormat != null && !responseFormat.isEmpty()) {
            payload.put("response_format", responseFormat);
        }
        payload.put("messages", effectiveMessages.stream().map(message -> Map.of(
                "role", normalizeMessageRole(message.role()),
                "content", message.content()
        )).toList());
        AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot runtimeSettings = runtimeSettingsService.getRuntimeSettings();
        String requestBody = writeJson(payload);

        logJsonRequestStart(runtimeSettings, invocation, model, endpoint, effectiveMessages.size(), totalInputChars(effectiveMessages));

        long startedAt = System.nanoTime();
        StreamExecutionResult streamResult = executeJsonStreamRequest(invocation, endpoint, payload, requestBody, runtimeSettings, deltaConsumer);
        if (streamResult.content().isBlank()) {
            logInvalidResponse(runtimeSettings, "message content blank", invocation, endpoint, requestBody, streamResult.rawStreamBody());
            throw new ApiException("AI-2001", "AI 服务返回了无效内容，请稍后重试。", HttpStatus.BAD_GATEWAY);
        }
        BigDecimal estimatedCost = calculateEstimatedCost(streamResult.requestTokens(), streamResult.responseTokens(), invocation);
        long latencyMs = Math.max(Duration.ofNanos(System.nanoTime() - startedAt).toMillis(), 1L);
        logRequestSuccess(runtimeSettings, invocation, model, latencyMs, streamResult.requestTokens(), streamResult.responseTokens(), streamResult.totalTokens());
        return new AiProviderChatResult(
                invocation.taskType(),
                streamResult.content(),
                invocation.providerCode(),
                model,
                latencyMs,
                streamResult.requestTokens(),
                streamResult.responseTokens(),
                streamResult.totalTokens(),
                0,
                invocation.thinkingConfig() == null ? null : invocation.thinkingConfig().reasoningEffortCode(),
                null,
                null,
                estimatedCost
        );
    }

    @Override
    public AiProviderSpeechResult transcribeAudio(AiProviderInvocation invocation, MultipartFile audioFile) {
        String baseUrl = normalizeBaseUrl(invocation.baseUrl());
        String model = normalizeRequired(invocation.model(), "provider model missing");
        String endpoint = baseUrl + "/audio/transcriptions";
        byte[] audioBytes = readBytes(audioFile);
        String filename = safeFilename(audioFile);
        String contentType = safeContentType(audioFile);
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("model", model);
        bodyBuilder.part(
                "file",
                new NamedByteArrayResource(audioBytes, filename)
        ).header(HttpHeaders.CONTENT_TYPE, contentType);
        AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot runtimeSettings = runtimeSettingsService.getRuntimeSettings();
        String requestBody = buildMultipartDebugBody(model, filename, contentType, audioBytes);

        logTranscribeRequestStart(runtimeSettings, invocation, model, endpoint, audioBytes.length, filename);

        long startedAt = System.nanoTime();
        String body = executeMultipartRequest(invocation, endpoint, bodyBuilder, requestBody, runtimeSettings);
        JsonNode root = readJson(body, invocation, endpoint, requestBody, runtimeSettings);
        String transcript = root.path("text").asText("").trim();
        if (transcript.isBlank()) {
            logInvalidResponse(runtimeSettings, "transcript blank", invocation, endpoint, requestBody, body);
            throw new ApiException("AI-2102", "语音转写失败，请重新录音或改用文字模式。", HttpStatus.BAD_GATEWAY);
        }
        int requestTokens = root.path("usage").path("prompt_tokens").asInt(0);
        int responseTokens = root.path("usage").path("completion_tokens").asInt(0);
        int totalTokens = root.path("usage").path("total_tokens").asInt(requestTokens + responseTokens);
        BigDecimal estimatedCost = calculateEstimatedCost(requestTokens, responseTokens, invocation);
        long latencyMs = Math.max(Duration.ofNanos(System.nanoTime() - startedAt).toMillis(), 1L);
        logRequestSuccess(runtimeSettings, invocation, model, latencyMs, requestTokens, responseTokens, totalTokens);
        return new AiProviderSpeechResult(
                invocation.taskType(),
                transcript,
                invocation.providerCode(),
                model,
                latencyMs,
                requestTokens,
                responseTokens,
                totalTokens,
                0,
                invocation.thinkingConfig() == null ? null : invocation.thinkingConfig().reasoningEffortCode(),
                null,
                null,
                estimatedCost
        );
    }

    private String executeJsonRequest(
            AiProviderInvocation invocation,
            String endpoint,
            Map<String, Object> payload,
            String requestBody,
            AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot runtimeSettings
    ) {
        WebClient client = webClientBuilder.build();
        return client.post()
                .uri(endpoint)
                .header(HttpHeaders.AUTHORIZATION, bearerToken(invocation.apiKey()))
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
                        .doBeforeRetry(retrySignal -> logRetry(runtimeSettings, invocation, endpoint, retrySignal.totalRetries() + 1, retrySignal.failure())))
                .onErrorMap(throwable -> mapProviderException(runtimeSettings, invocation, endpoint, requestBody, throwable))
                .block();
    }

    private StreamExecutionResult executeJsonStreamRequest(
            AiProviderInvocation invocation,
            String endpoint,
            Map<String, Object> payload,
            String requestBody,
            AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot runtimeSettings,
            Consumer<String> deltaConsumer
    ) {
        WebClient client = webClientBuilder.build();
        StringBuilder eventBuffer = new StringBuilder();
        StringBuilder rawStreamBody = new StringBuilder();
        StringBuilder contentBuilder = new StringBuilder();
        StreamTokenUsage usage = new StreamTokenUsage();
        try {
            client.post()
                    .uri(endpoint)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(invocation.apiKey()))
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
            return new StreamExecutionResult(contentBuilder.toString().trim(), usage.requestTokens, usage.responseTokens, usage.totalTokens, rawStreamBody.toString());
        } catch (Throwable throwable) {
            throw (RuntimeException) mapProviderException(runtimeSettings, invocation, endpoint, requestBody, throwable);
        }
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
        String trimmedBlock = eventBlock.trim();
        String data;
        if (isStandaloneStreamEvent(trimmedBlock)) {
            data = trimmedBlock;
        } else {
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
            data = dataBuilder.toString().trim();
        }
        if (data.isBlank() || "[DONE]".equals(data)) {
            return;
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(data);
        } catch (Exception ex) {
            throw new ApiException("AI-2001", "AI 服务返回了无效内容，请稍后重试。", HttpStatus.BAD_GATEWAY);
        }
        JsonNode usageNode = root.path("usage");
        if (!usageNode.isMissingNode() && !usageNode.isNull()) {
            usage.requestTokens = usageNode.path("prompt_tokens").asInt(usage.requestTokens);
            usage.responseTokens = usageNode.path("completion_tokens").asInt(usage.responseTokens);
            usage.totalTokens = usageNode.path("total_tokens").asInt(usage.requestTokens + usage.responseTokens);
        }
        JsonNode choicesNode = root.path("choices");
        if (!choicesNode.isArray()) {
            return;
        }
        for (JsonNode choice : choicesNode) {
            String deltaText = extractContent(choice.path("delta").path("content"));
            if (!deltaText.isBlank()) {
                contentBuilder.append(deltaText);
                if (deltaConsumer != null) {
                    deltaConsumer.accept(deltaText);
                }
            }
        }
    }

    private boolean isStandaloneStreamEvent(String eventBlock) {
        if (eventBlock == null || eventBlock.isBlank()) {
            return false;
        }
        String trimmed = eventBlock.trim();
        return "[DONE]".equals(trimmed) || (trimmed.startsWith("{") && trimmed.endsWith("}"));
    }

    private String executeMultipartRequest(
            AiProviderInvocation invocation,
            String endpoint,
            MultipartBodyBuilder bodyBuilder,
            String requestBody,
            AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot runtimeSettings
    ) {
        WebClient client = webClientBuilder.build();
        return client.post()
                .uri(endpoint)
                .header(HttpHeaders.AUTHORIZATION, bearerToken(invocation.apiKey()))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
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
                        .doBeforeRetry(retrySignal -> logRetry(runtimeSettings, invocation, endpoint, retrySignal.totalRetries() + 1, retrySignal.failure())))
                .onErrorMap(throwable -> mapProviderException(runtimeSettings, invocation, endpoint, requestBody, throwable))
                .block();
    }

    private boolean shouldRetry(Throwable throwable) {
        if (throwable instanceof ProviderHttpException httpException) {
            return httpException.statusCode() == 429 || httpException.statusCode() >= 500;
        }
        return throwable instanceof TimeoutException
                || throwable instanceof SocketTimeoutException
                || throwable instanceof WebClientRequestException;
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

    private List<AiChatMessage> mergeSystemPrompt(String systemPrompt, List<AiChatMessage> messages) {
        List<AiChatMessage> merged = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            merged.add(new AiChatMessage("system", systemPrompt.trim()));
        }
        if (messages != null) {
            merged.addAll(messages);
        }
        return merged;
    }

    private String normalizeMessageRole(String role) {
        String normalized = role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "assistant", "user", "system" -> normalized;
            case "developer" -> "system";
            case "model" -> "assistant";
            default -> "user";
        };
    }

    private String extractContent(JsonNode contentNode) {
        if (contentNode.isTextual()) {
            return contentNode.asText("").trim();
        }
        if (contentNode.isArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode item : contentNode) {
                String text = item.path("text").asText("").trim();
                if (!text.isBlank()) {
                    parts.add(text);
                }
            }
            return String.join("\n", parts).trim();
        }
        return "";
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
            throw new ApiException("AI-2001", "AI 服务返回了无效内容，请稍后重试。", HttpStatus.BAD_GATEWAY);
        }
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
            log.warn(
                    "openai-compatible invalid response taskType={}, provider={}, model={}, url={}, reason={}, requestBody={}, responseBody={}",
                    invocation.taskType(),
                    invocation.providerCode(),
                    invocation.model(),
                    endpoint,
                    reason,
                    bodyForLog(requestBody),
                    bodyForLog(responseBody)
            );
            return;
        }
        log.warn(
                "openai-compatible invalid response taskType={}, provider={}, model={}, url={}, reason={}, requestSize={}, responseSize={}",
                invocation.taskType(),
                invocation.providerCode(),
                invocation.model(),
                endpoint,
                reason,
                lengthOf(requestBody),
                lengthOf(responseBody)
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
            log.warn(
                    "openai-compatible http error taskType={}, provider={}, model={}, status={}, url={}, requestBody={}, responseBody={}",
                    invocation.taskType(),
                    invocation.providerCode(),
                    invocation.model(),
                    statusCode,
                    endpoint,
                    bodyForLog(requestBody),
                    bodyForLog(responseBody)
            );
            return;
        }
        log.warn(
                "openai-compatible http error taskType={}, provider={}, model={}, status={}, url={}, requestSize={}, responseSize={}",
                invocation.taskType(),
                invocation.providerCode(),
                invocation.model(),
                statusCode,
                endpoint,
                lengthOf(requestBody),
                lengthOf(responseBody)
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
            log.warn(
                    "openai-compatible transport error taskType={}, provider={}, model={}, url={}, requestBody={}, error={}",
                    invocation.taskType(),
                    invocation.providerCode(),
                    invocation.model(),
                    endpoint,
                    bodyForLog(requestBody),
                    summarizeException(throwable)
            );
            return;
        }
        log.warn(
                "openai-compatible transport error taskType={}, provider={}, model={}, url={}, requestSize={}, error={}",
                invocation.taskType(),
                invocation.providerCode(),
                invocation.model(),
                endpoint,
                lengthOf(requestBody),
                summarizeException(throwable)
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
                "openai-compatible request start taskType={}, provider={}, model={}, url={}, timeoutMs={}, maxRetries={}, messageCount={}, inputChars={}, apiKey={}",
                invocation.taskType(),
                invocation.providerCode(),
                model,
                endpoint,
                invocation.timeout().toMillis(),
                invocation.maxRetries(),
                messageCount,
                inputChars,
                maskSecret(invocation.apiKey())
        );
    }

    private void logTranscribeRequestStart(
            AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot runtimeSettings,
            AiProviderInvocation invocation,
            String model,
            String endpoint,
            int bytes,
            String filename
    ) {
        if (!runtimeSettings.aiRequestLogEnabled()) {
            return;
        }
        log.info(
                "openai-compatible transcribe start taskType={}, provider={}, model={}, url={}, bytes={}, filename={}, apiKey={}",
                invocation.taskType(),
                invocation.providerCode(),
                model,
                endpoint,
                bytes,
                filename,
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
            int totalTokens
    ) {
        if (!runtimeSettings.debugModeEnabled() && !runtimeSettings.aiRequestLogEnabled()) {
            return;
        }
        log.info(
                "openai-compatible request success taskType={}, provider={}, model={}, latencyMs={}, requestTokens={}, responseTokens={}, totalTokens={}",
                invocation.taskType(),
                invocation.providerCode(),
                model,
                latencyMs,
                requestTokens,
                responseTokens,
                totalTokens
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
                "openai-compatible retry scheduled taskType={}, provider={}, model={}, url={}, attempt={}, maxRetries={}, reason={}",
                invocation.taskType(),
                invocation.providerCode(),
                invocation.model(),
                endpoint,
                attemptNo,
                invocation.maxRetries(),
                summarizeException(throwable)
        );
    }

    private String buildMultipartDebugBody(String model, String filename, String contentType, byte[] audioBytes) {
        Map<String, Object> debugPayload = new LinkedHashMap<>();
        debugPayload.put("model", model);
        debugPayload.put("file", Map.of(
                "filename", filename,
                "contentType", contentType,
                "sizeBytes", audioBytes == null ? 0 : audioBytes.length,
                "base64", Base64.getEncoder().encodeToString(audioBytes == null ? new byte[0] : audioBytes)
        ));
        return writeJson(debugPayload);
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

    private boolean shouldUseJsonResponseFormat(AiProviderInvocation invocation) {
        return readExtraConfigBoolean(invocation.routeExtraConfigJson(), "responseFormatJsonObject")
                || readExtraConfigBoolean(invocation.routeExtraConfigJson(), "useJsonResponseFormat")
                || readExtraConfigBoolean(invocation.providerExtraConfigJson(), "responseFormatJsonObject")
                || readExtraConfigBoolean(invocation.providerExtraConfigJson(), "useJsonResponseFormat");
    }

    private Map<String, Object> resolveStructuredResponseFormat(AiProviderInvocation invocation) {
        if (shouldUseJsonResponseFormat(invocation)) {
            return Map.of("type", "json_object");
        }
        return AiStructuredOutputSchemas.buildOpenAiResponseFormat(invocation.taskType(), invocation.sceneCode());
    }

    private boolean readExtraConfigBoolean(String json, String fieldName) {
        if (json == null || json.isBlank()) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return node.path(fieldName).asBoolean(false);
        } catch (Exception ex) {
            return false;
        }
    }

    private String bearerToken(String apiKey) {
        String normalized = normalizeRequired(apiKey, "provider apiKey missing");
        return normalized.toLowerCase(Locale.ROOT).startsWith("bearer ") ? normalized : "Bearer " + normalized;
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = normalizeRequired(baseUrl, "provider baseUrl missing");
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ApiException("AI-2001", message, HttpStatus.BAD_GATEWAY);
        }
        return value.trim();
    }

    private byte[] readBytes(MultipartFile audioFile) {
        try {
            return audioFile == null ? new byte[0] : audioFile.getBytes();
        } catch (Exception ex) {
            throw new ApiException("AI-2102", "语音转写失败，请重新录音或改用文字模式。", HttpStatus.BAD_GATEWAY);
        }
    }

    private String safeFilename(MultipartFile audioFile) {
        if (audioFile == null || audioFile.getOriginalFilename() == null || audioFile.getOriginalFilename().isBlank()) {
            return "audio.webm";
        }
        return audioFile.getOriginalFilename().trim();
    }

    private String safeContentType(MultipartFile audioFile) {
        if (audioFile == null || audioFile.getContentType() == null || audioFile.getContentType().isBlank()) {
            return "application/octet-stream";
        }
        return audioFile.getContentType();
    }

    private int totalInputChars(List<AiChatMessage> messages) {
        return messages.stream()
                .map(AiChatMessage::content)
                .mapToInt(content -> content == null ? 0 : content.length())
                .sum();
    }

    private String maskSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return "missing";
        }
        String trimmed = secret.trim();
        int visible = Math.min(4, trimmed.length());
        return "***" + trimmed.substring(trimmed.length() - visible) + "(len=" + trimmed.length() + ")";
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

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        private NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }

    private static final class StreamExecutionResult {
        private final String content;
        private final int requestTokens;
        private final int responseTokens;
        private final int totalTokens;
        private final String rawStreamBody;

        private StreamExecutionResult(String content, int requestTokens, int responseTokens, int totalTokens, String rawStreamBody) {
            this.content = content;
            this.requestTokens = requestTokens;
            this.responseTokens = responseTokens;
            this.totalTokens = totalTokens;
            this.rawStreamBody = rawStreamBody;
        }

        private String content() {
            return content;
        }

        private int requestTokens() {
            return requestTokens;
        }

        private int responseTokens() {
            return responseTokens;
        }

        private int totalTokens() {
            return totalTokens;
        }

        private String rawStreamBody() {
            return rawStreamBody;
        }
    }

    private static final class StreamTokenUsage {
        private int requestTokens;
        private int responseTokens;
        private int totalTokens;
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
