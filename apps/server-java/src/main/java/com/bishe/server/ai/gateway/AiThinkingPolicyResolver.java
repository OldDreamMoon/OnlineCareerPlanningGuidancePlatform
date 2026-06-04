package com.bishe.server.ai.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

/**
 * 统一解析路由默认思考量策略与 provider/route 的扩展配置覆写。
 */
@Service
public class AiThinkingPolicyResolver {

    private static final Logger log = LoggerFactory.getLogger(AiThinkingPolicyResolver.class);
    private static final String SOURCE_SYSTEM_DEFAULT = "SYSTEM_DEFAULT";
    private static final String SOURCE_RUNTIME_SETTINGS_DEFAULT = "RUNTIME_SETTINGS_DEFAULT";
    private static final String SOURCE_PROVIDER_CONFIG = "PROVIDER_EXTRA_CONFIG";
    private static final String SOURCE_ROUTE_POLICY_CONFIG = "ROUTE_POLICY_CONFIG";
    private static final String SOURCE_ROUTE_CONFIG = "ROUTE_EXTRA_CONFIG";

    private final ObjectMapper objectMapper;
    private final AiGatewayRuntimeSettingsService runtimeSettingsService;

    public AiThinkingPolicyResolver(
            ObjectMapper objectMapper,
            AiGatewayRuntimeSettingsService runtimeSettingsService
    ) {
        this.objectMapper = objectMapper;
        this.runtimeSettingsService = runtimeSettingsService;
    }

    public AiThinkingConfig resolve(
            String taskType,
            String sceneCode,
            AiExecutionMode executionMode,
            String model,
            Map<String, Object> promptVariables,
            String providerExtraConfigJson,
            String policyExtraConfigJson,
        String routeExtraConfigJson
    ) {
        AiThinkingConfig resolved = defaultConfig(taskType, sceneCode, executionMode, model, promptVariables);
        resolved = resolved.merge(runtimeSettingsDefaultConfig());
        resolved = resolved.merge(parseExtraConfig(providerExtraConfigJson, SOURCE_PROVIDER_CONFIG));
        resolved = resolved.merge(parseExtraConfig(policyExtraConfigJson, SOURCE_ROUTE_POLICY_CONFIG));
        resolved = resolved.merge(parseExtraConfig(routeExtraConfigJson, SOURCE_ROUTE_CONFIG));
        return resolved;
    }

    public AiThinkingConfig readConfiguredOverride(String extraConfigJson, String source) {
        return parseExtraConfig(extraConfigJson, source);
    }

    private AiThinkingConfig defaultConfig(
            String taskType,
            String sceneCode,
            AiExecutionMode executionMode,
            String model,
            Map<String, Object> promptVariables
    ) {
        String normalizedTaskType = normalizeCode(taskType);
        String normalizedSceneCode = normalizeCode(sceneCode);
        String inputMode = normalizeCode(readPromptVariable(promptVariables, "inputMode"));
        String normalizedModel = normalizeCode(model);

        if ("STT".equals(normalizedTaskType) || "TTS".equals(normalizedTaskType)) {
            return AiThinkingConfig.of(AiReasoningEffort.OFF, null, null, SOURCE_SYSTEM_DEFAULT);
        }
        if ("RESUME".equals(normalizedTaskType) && "RESUME_OPTIMIZE".equals(normalizedSceneCode)) {
            if ("PDF".equals(inputMode) || executionMode == AiExecutionMode.ASYNC_JOB) {
                return AiThinkingConfig.of(AiReasoningEffort.HIGH, null, null, SOURCE_SYSTEM_DEFAULT);
            }
            return AiThinkingConfig.of(AiReasoningEffort.MEDIUM, null, null, SOURCE_SYSTEM_DEFAULT);
        }
        if ("INTERVIEW_TEXT".equals(normalizedTaskType) && "INTERVIEW_REPLY".equals(normalizedSceneCode)) {
            return AiThinkingConfig.of(AiReasoningEffort.LOW, null, null, SOURCE_SYSTEM_DEFAULT);
        }
        if ("INTERVIEW_TEXT".equals(normalizedTaskType) && "INTERVIEW_ANSWER_HELPER".equals(normalizedSceneCode)) {
            return AiThinkingConfig.of(AiReasoningEffort.LOW, null, null, SOURCE_SYSTEM_DEFAULT);
        }
        if ("INTERVIEW_TEXT".equals(normalizedTaskType) && "INTERVIEW_OPENING".equals(normalizedSceneCode)) {
            return AiThinkingConfig.of(AiReasoningEffort.MEDIUM, null, null, SOURCE_SYSTEM_DEFAULT);
        }
        if ("INTERVIEW_SUMMARY".equals(normalizedTaskType) && "INTERVIEW_SUMMARY".equals(normalizedSceneCode)) {
            return AiThinkingConfig.of(AiReasoningEffort.MEDIUM, null, null, SOURCE_SYSTEM_DEFAULT);
        }
        if ("COMMUNITY_REPLY".equals(normalizedTaskType) || "ICEBREAK".equals(normalizedTaskType)) {
            return AiThinkingConfig.of(AiReasoningEffort.LOW, null, null, SOURCE_SYSTEM_DEFAULT);
        }
        if (executionMode == AiExecutionMode.STREAM_SSE || executionMode == AiExecutionMode.REALTIME_SESSION) {
            return AiThinkingConfig.of(AiReasoningEffort.LOW, null, null, SOURCE_SYSTEM_DEFAULT);
        }
        if (executionMode == AiExecutionMode.ASYNC_JOB) {
            return AiThinkingConfig.of(AiReasoningEffort.HIGH, null, null, SOURCE_SYSTEM_DEFAULT);
        }
        if (normalizedModel.startsWith("GEMINI-3")) {
            return AiThinkingConfig.of(AiReasoningEffort.DYNAMIC, null, null, SOURCE_SYSTEM_DEFAULT);
        }
        return AiThinkingConfig.of(AiReasoningEffort.MEDIUM, null, null, SOURCE_SYSTEM_DEFAULT);
    }

    private AiThinkingConfig runtimeSettingsDefaultConfig() {
        AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot snapshot = runtimeSettingsService.getRuntimeSettings();
        if (snapshot == null) {
            return new AiThinkingConfig(null, null, null, null);
        }
        if (snapshot.defaultReasoningEffort() == null
                && snapshot.defaultThinkingBudget() == null
                && snapshot.defaultThinkingLevel() == null) {
            return new AiThinkingConfig(null, null, null, null);
        }
        return AiThinkingConfig.of(
                AiReasoningEffort.fromNullable(snapshot.defaultReasoningEffort()),
                snapshot.defaultThinkingBudget(),
                snapshot.defaultThinkingLevel(),
                SOURCE_RUNTIME_SETTINGS_DEFAULT
        );
    }

    private AiThinkingConfig parseExtraConfig(String extraConfigJson, String source) {
        if (extraConfigJson == null || extraConfigJson.isBlank()) {
            return new AiThinkingConfig(null, null, null, null);
        }
        try {
            JsonNode root = objectMapper.readTree(extraConfigJson);
            JsonNode thinkingNode = root.path("thinking").isObject() ? root.path("thinking") : root;
            String reasoningEffort = firstNonBlankText(thinkingNode, "reasoningEffort", "thinkingMode", "reasoningMode");
            Integer thinkingBudget = firstInteger(thinkingNode, "thinkingBudget");
            String thinkingLevel = firstNonBlankText(thinkingNode, "thinkingLevel");
            if (reasoningEffort == null && thinkingBudget == null && thinkingLevel == null) {
                return new AiThinkingConfig(null, null, null, null);
            }
            return AiThinkingConfig.of(
                    AiReasoningEffort.fromNullable(reasoningEffort),
                    thinkingBudget,
                    thinkingLevel,
                    source
            );
        } catch (Exception ex) {
            log.warn("ignore invalid ai thinking config source={}, error={}", source, ex.getMessage());
            return new AiThinkingConfig(null, null, null, null);
        }
    }

    private String readPromptVariable(Map<String, Object> promptVariables, String key) {
        if (promptVariables == null || promptVariables.isEmpty() || key == null || key.isBlank()) {
            return null;
        }
        Object value = promptVariables.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlankText(JsonNode node, String... fieldNames) {
        if (node == null || fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            if (fieldName == null || fieldName.isBlank()) {
                continue;
            }
            JsonNode child = node.path(fieldName);
            if (child.isMissingNode() || child.isNull()) {
                continue;
            }
            String value = child.asText("").trim();
            if (!value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private Integer firstInteger(JsonNode node, String... fieldNames) {
        if (node == null || fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            if (fieldName == null || fieldName.isBlank()) {
                continue;
            }
            JsonNode child = node.path(fieldName);
            if (child.isMissingNode() || child.isNull()) {
                continue;
            }
            if (child.isInt() || child.isLong()) {
                return child.asInt();
            }
            if (child.isTextual()) {
                try {
                    return Integer.parseInt(child.asText("").trim());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private String normalizeCode(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return "";
        }
        return rawValue.trim().toUpperCase(Locale.ROOT);
    }
}
