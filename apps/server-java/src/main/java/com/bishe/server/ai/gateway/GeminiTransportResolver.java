package com.bishe.server.ai.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Gemini / NewAPI 传输解析器：统一处理端点拼接、鉴权模式和 SSE 参数。
 */
@Component
public class GeminiTransportResolver {

    private final ObjectMapper objectMapper;

    public GeminiTransportResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RequestSpec buildGenerateContentRequest(
            String baseUrl,
            String apiKey,
            String model,
            String providerExtraConfigJson,
            boolean stream
    ) {
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        String normalizedModel = safe(model).trim();
        GeminiAuthMode authMode = resolveAuthMode(normalizedBaseUrl, providerExtraConfigJson);
        String endpoint = normalizedBaseUrl
                + "/models/"
                + urlEncode(normalizedModel)
                + (stream ? ":streamGenerateContent" : ":generateContent");

        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        List<String> queryParams = new ArrayList<>();
        if (stream) {
            queryParams.add("alt=sse");
        }
        applyAuth(authMode, apiKey, headers, queryParams);
        if (!queryParams.isEmpty()) {
            endpoint = endpoint + "?" + String.join("&", queryParams);
        }
        return new RequestSpec(endpoint, Map.copyOf(headers), authMode.name());
    }

    public RequestSpec buildModelsProbeRequest(String baseUrl, String apiKey, String providerExtraConfigJson) {
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        GeminiAuthMode authMode = resolveAuthMode(normalizedBaseUrl, providerExtraConfigJson);
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        List<String> queryParams = new ArrayList<>();
        applyAuth(authMode, apiKey, headers, queryParams);
        String endpoint = normalizedBaseUrl + "/models";
        if (!queryParams.isEmpty()) {
            endpoint = endpoint + "?" + String.join("&", queryParams);
        }
        return new RequestSpec(endpoint, Map.copyOf(headers), authMode.name());
    }

    public String normalizeBaseUrl(String baseUrl) {
        String normalized = safe(baseUrl).trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/v1beta") || normalized.endsWith("/v1alpha")) {
            return normalized;
        }
        if (normalized.endsWith("/v1")) {
            return normalized + "beta";
        }
        return normalized + "/v1beta";
    }

    private void applyAuth(
            GeminiAuthMode authMode,
            String apiKey,
            Map<String, String> headers,
            List<String> queryParams
    ) {
        String normalizedApiKey = safe(apiKey).trim();
        if (normalizedApiKey.isBlank()) {
            return;
        }
        switch (authMode) {
            case QUERY_API_KEY -> queryParams.add("key=" + urlEncode(normalizedApiKey));
            case X_GOOG_API_KEY -> headers.put("x-goog-api-key", normalizedApiKey);
            case BEARER_TOKEN -> headers.put(HttpHeaders.AUTHORIZATION, bearerToken(normalizedApiKey));
        }
    }

    private GeminiAuthMode resolveAuthMode(String normalizedBaseUrl, String providerExtraConfigJson) {
        String configured = firstNonBlank(
                readConfigText(providerExtraConfigJson, "authMode"),
                readConfigText(providerExtraConfigJson, "geminiAuthMode"),
                readConfigText(providerExtraConfigJson, "auth_type")
        );
        if (configured != null) {
            GeminiAuthMode parsed = GeminiAuthMode.tryParse(configured);
            if (parsed != null) {
                return parsed;
            }
        }
        String lowerBaseUrl = safe(normalizedBaseUrl).toLowerCase(Locale.ROOT);
        if (lowerBaseUrl.contains("googleapis.com") || lowerBaseUrl.contains("generativelanguage")) {
            return GeminiAuthMode.QUERY_API_KEY;
        }
        return GeminiAuthMode.BEARER_TOKEN;
    }

    private String readConfigText(String extraConfigJson, String fieldName) {
        if (extraConfigJson == null || extraConfigJson.isBlank() || fieldName == null || fieldName.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(extraConfigJson);
            String value = root.path(fieldName).asText("").trim();
            return value.isBlank() ? null : value;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String bearerToken(String apiKey) {
        return apiKey.toLowerCase(Locale.ROOT).startsWith("bearer ") ? apiKey : "Bearer " + apiKey;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(safe(value), StandardCharsets.UTF_8);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String safe(String rawValue) {
        return rawValue == null ? "" : rawValue;
    }

    public record RequestSpec(
            String endpoint,
            Map<String, String> headers,
            String authMode
    ) {
    }

    private enum GeminiAuthMode {
        BEARER_TOKEN,
        QUERY_API_KEY,
        X_GOOG_API_KEY;

        private static GeminiAuthMode tryParse(String rawValue) {
            if (rawValue == null || rawValue.isBlank()) {
                return null;
            }
            String normalized = rawValue.trim().toUpperCase(Locale.ROOT)
                    .replace('-', '_')
                    .replace(' ', '_');
            return switch (normalized) {
                case "BEARER", "BEARER_TOKEN" -> BEARER_TOKEN;
                case "QUERY", "QUERY_API_KEY", "API_KEY_QUERY" -> QUERY_API_KEY;
                case "X_GOOG_API_KEY", "XGOOGAPIKEY", "GOOG_HEADER" -> X_GOOG_API_KEY;
                default -> null;
            };
        }
    }
}
