package com.bishe.server.ai.gateway;

import com.bishe.server.common.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.net.SocketTimeoutException;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeoutException;

/**
 * 统一整理上游 provider 错误，避免把 429/5xx 等运行态失败抹平成单一文案。
 */
final class AiProviderErrorMapper {

    private static final int MAX_BODY_SNIPPET_LENGTH = 240;

    private AiProviderErrorMapper() {
    }

    static ApiException mapHttpError(ObjectMapper objectMapper, int providerStatus, String providerBody, Throwable cause) {
        ProviderErrorResolution resolution = resolveHttpError(providerStatus);
        ProviderBodyDetail bodyDetail = extractProviderBodyDetail(objectMapper, providerBody);

        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("providerStatus", providerStatus);
        data.put("retryable", resolution.retryable());
        data.put("providerCategory", resolution.category());
        if (!bodyDetail.providerMessage().isBlank()) {
            data.put("providerMessage", bodyDetail.providerMessage());
        }
        if (!bodyDetail.providerBodySnippet().isBlank()) {
            data.put("providerBodySnippet", bodyDetail.providerBodySnippet());
        }

        return new ApiException("AI-2001", resolution.message(), resolution.httpStatus(), data, null, cause);
    }

    static ApiException mapTransportError(Throwable cause) {
        ProviderErrorResolution resolution = resolveTransportError(cause);
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("retryable", resolution.retryable());
        data.put("providerCategory", resolution.category());
        String providerMessage = summarizeThrowable(cause);
        if (!providerMessage.isBlank()) {
            data.put("providerMessage", providerMessage);
        }
        return new ApiException("AI-2001", resolution.message(), resolution.httpStatus(), data, null, cause);
    }

    private static ProviderErrorResolution resolveHttpError(int providerStatus) {
        if (providerStatus == 429) {
            return new ProviderErrorResolution(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "AI 服务当前请求过多，请稍后再试。",
                    true,
                    "RATE_LIMIT"
            );
        }
        if (providerStatus == 408 || providerStatus == 504) {
            return new ProviderErrorResolution(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "AI 服务响应超时，请稍后重试。",
                    true,
                    "TIMEOUT"
            );
        }
        if (providerStatus == 502 || providerStatus == 503) {
            return new ProviderErrorResolution(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI 服务暂时不可用，请稍后再试。",
                    true,
                    "UNAVAILABLE"
            );
        }
        if (providerStatus >= 500) {
            return new ProviderErrorResolution(
                    HttpStatus.BAD_GATEWAY,
                    "AI 服务临时异常，请稍后重试。",
                    true,
                    "SERVER_ERROR"
            );
        }
        if (providerStatus == 401 || providerStatus == 403) {
            return new ProviderErrorResolution(
                    HttpStatus.BAD_GATEWAY,
                    "AI 服务鉴权失败，请联系管理员检查配置。",
                    false,
                    "AUTH"
            );
        }
        if (providerStatus == 404) {
            return new ProviderErrorResolution(
                    HttpStatus.BAD_GATEWAY,
                    "AI 服务路由不可用，请联系管理员检查配置。",
                    false,
                    "ROUTE_MISSING"
            );
        }
        if (providerStatus == 400) {
            return new ProviderErrorResolution(
                    HttpStatus.BAD_GATEWAY,
                    "AI 服务请求格式异常，请稍后重试。",
                    false,
                    "REQUEST_INVALID"
            );
        }
        return new ProviderErrorResolution(
                HttpStatus.BAD_GATEWAY,
                "AI 服务请求失败，请稍后重试。",
                false,
                "REQUEST_FAILED"
        );
    }

    private static ProviderErrorResolution resolveTransportError(Throwable cause) {
        if (cause instanceof TimeoutException || cause instanceof SocketTimeoutException) {
            return new ProviderErrorResolution(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "AI 服务响应超时，请稍后重试。",
                    true,
                    "TIMEOUT"
            );
        }
        if (cause instanceof WebClientRequestException) {
            return new ProviderErrorResolution(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI 服务连接失败，请稍后重试。",
                    true,
                    "TRANSPORT"
            );
        }
        return new ProviderErrorResolution(
                HttpStatus.BAD_GATEWAY,
                "AI 服务请求失败，请稍后重试。",
                false,
                "UNKNOWN"
        );
    }

    private static ProviderBodyDetail extractProviderBodyDetail(ObjectMapper objectMapper, String providerBody) {
        String snippet = summarizeBody(providerBody);
        if (snippet.isBlank()) {
            return new ProviderBodyDetail("", "");
        }
        try {
            JsonNode root = objectMapper.readTree(providerBody);
            String providerMessage = firstNonBlankText(
                    root.path("error").path("message").asText(""),
                    root.path("error").path("status").asText(""),
                    root.path("error").path("code").asText(""),
                    root.path("message").asText(""),
                    root.path("detail").asText("")
            );
            return new ProviderBodyDetail(normalizeText(providerMessage), snippet);
        } catch (Exception ignored) {
            return new ProviderBodyDetail(snippet, snippet);
        }
    }

    private static String summarizeThrowable(Throwable cause) {
        if (cause == null || cause.getMessage() == null) {
            return "";
        }
        return normalizeText(cause.getMessage());
    }

    private static String summarizeBody(String providerBody) {
        String normalized = normalizeText(providerBody);
        if (normalized.isBlank()) {
            return "";
        }
        if (normalized.length() <= MAX_BODY_SNIPPET_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_BODY_SNIPPET_LENGTH) + "...";
    }

    private static String normalizeText(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }
        return rawText.replaceAll("\\s+", " ").trim();
    }

    private static String firstNonBlankText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private record ProviderErrorResolution(
            HttpStatus httpStatus,
            String message,
            boolean retryable,
            String category
    ) {
    }

    private record ProviderBodyDetail(
            String providerMessage,
            String providerBodySnippet
    ) {
    }
}
