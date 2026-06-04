package com.bishe.server.ai.gateway;

import com.bishe.server.common.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prompt 模板渲染服务：负责解析变量声明、执行占位符替换，并兼容结构化消息 bundle。
 */
@Service
public class PromptTemplateRenderService {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.\\-]+)\\s*}}");

    private final ObjectMapper objectMapper;

    public PromptTemplateRenderService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 以运行时严格模式渲染纯文本模板；当占位变量缺失时立即抛错。
     */
    public RenderResult renderForRuntime(String content, String variablesJson, Map<String, Object> runtimeVariables) {
        return render(content, variablesJson, runtimeVariables, true);
    }

    /**
     * 以预览模式渲染纯文本模板；允许使用样例值或保留占位符。
     */
    public RenderResult renderForPreview(String content, String variablesJson, Map<String, Object> runtimeVariables) {
        return render(content, variablesJson, runtimeVariables, false);
    }

    /**
     * 以运行时严格模式渲染消息 bundle。
     */
    public BundleRenderResult renderBundleForRuntime(
            String content,
            String variablesJson,
            String bundleJson,
            Map<String, Object> runtimeVariables
    ) {
        return renderBundle(content, variablesJson, bundleJson, runtimeVariables, true);
    }

    /**
     * 以预览模式渲染消息 bundle。
     */
    public BundleRenderResult renderBundleForPreview(
            String content,
            String variablesJson,
            String bundleJson,
            Map<String, Object> runtimeVariables
    ) {
        return renderBundle(content, variablesJson, bundleJson, runtimeVariables, false);
    }

    /**
     * 解析后台输入的变量 JSON，要求顶层为对象结构。
     */
    public Map<String, Object> parseRuntimeVariablesJson(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            if (!root.isObject()) {
                throw new ApiException("BIZ-1001", "renderVariablesJson invalid", HttpStatus.BAD_REQUEST);
            }
            return objectMapper.convertValue(
                    root,
                    objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class)
            );
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException("BIZ-1001", "renderVariablesJson invalid", HttpStatus.BAD_REQUEST);
        }
    }

    private RenderResult render(String content, String variablesJson, Map<String, Object> runtimeVariables, boolean strictMode) {
        Map<String, VariableDefinition> definitions = parseVariableDefinitions(variablesJson);
        Map<String, String> normalizedRuntimeValues = normalizeRuntimeVariables(runtimeVariables);
        RenderState state = new RenderState();
        String renderedContent = renderTemplateText(content, definitions, normalizedRuntimeValues, strictMode, state);
        throwIfMissing(strictMode, state.missingVariables());
        return new RenderResult(
                renderedContent,
                List.copyOf(state.placeholders()),
                List.copyOf(state.missingVariables()),
                Map.copyOf(state.resolvedVariables())
        );
    }

    private BundleRenderResult renderBundle(
            String content,
            String variablesJson,
            String bundleJson,
            Map<String, Object> runtimeVariables,
            boolean strictMode
    ) {
        Map<String, VariableDefinition> definitions = parseVariableDefinitions(variablesJson);
        Map<String, String> normalizedRuntimeValues = normalizeRuntimeVariables(runtimeVariables);
        RenderState state = new RenderState();
        String renderedContent = renderTemplateText(content, definitions, normalizedRuntimeValues, strictMode, state);
        JsonNode renderedBundleNode = renderBundleJson(bundleJson, definitions, normalizedRuntimeValues, strictMode, state);
        throwIfMissing(strictMode, state.missingVariables());
        String renderedBundleJson = renderedBundleNode == null ? null : writeJson(renderedBundleNode);
        List<AiChatMessage> promptSeedMessages = extractPromptSeedMessages(renderedBundleNode);
        return new BundleRenderResult(
                renderedContent,
                renderedBundleJson,
                buildBundleSystemPrompt(renderedContent, renderedBundleNode),
                promptSeedMessages,
                List.copyOf(state.placeholders()),
                List.copyOf(state.missingVariables()),
                Map.copyOf(state.resolvedVariables())
        );
    }

    private JsonNode renderBundleJson(
            String bundleJson,
            Map<String, VariableDefinition> definitions,
            Map<String, String> runtimeVariables,
            boolean strictMode,
            RenderState state
    ) {
        if (bundleJson == null || bundleJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(bundleJson);
            return renderJsonNode(root, definitions, runtimeVariables, strictMode, state);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException("BIZ-1001", "bundleJson invalid", HttpStatus.BAD_REQUEST);
        }
    }

    private JsonNode renderJsonNode(
            JsonNode node,
            Map<String, VariableDefinition> definitions,
            Map<String, String> runtimeVariables,
            boolean strictMode,
            RenderState state
    ) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return objectMapper.nullNode();
        }
        if (node.isTextual()) {
            return objectMapper.getNodeFactory().textNode(renderTemplateText(node.asText(""), definitions, runtimeVariables, strictMode, state));
        }
        if (node.isArray()) {
            ArrayNode arrayNode = objectMapper.createArrayNode();
            for (JsonNode item : node) {
                arrayNode.add(renderJsonNode(item, definitions, runtimeVariables, strictMode, state));
            }
            return arrayNode;
        }
        if (node.isObject()) {
            ObjectNode objectNode = objectMapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                objectNode.set(field.getKey(), renderJsonNode(field.getValue(), definitions, runtimeVariables, strictMode, state));
            }
            return objectNode;
        }
        return node.deepCopy();
    }

    private String renderTemplateText(
            String content,
            Map<String, VariableDefinition> definitions,
            Map<String, String> runtimeVariables,
            boolean strictMode,
            RenderState state
    ) {
        String templateContent = content == null ? "" : content;
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(templateContent);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            String variableName = matcher.group(1) == null ? "" : matcher.group(1).trim();
            if (!variableName.isBlank()) {
                state.addPlaceholder(variableName);
            }
            VariableDefinition definition = definitions.get(variableName);
            String resolvedValue = resolveVariableValue(variableName, definition, runtimeVariables, strictMode);
            if (resolvedValue == null) {
                state.addMissing(variableName);
                matcher.appendReplacement(rendered, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            state.addResolved(variableName, resolvedValue);
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(resolvedValue));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private Map<String, VariableDefinition> parseVariableDefinitions(String variablesJson) {
        if (variablesJson == null || variablesJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            JsonNode root = objectMapper.readTree(variablesJson);
            LinkedHashMap<String, VariableDefinition> definitions = new LinkedHashMap<>();
            if (root.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    String variableName = normalizeVariableName(field.getKey());
                    if (variableName == null) {
                        continue;
                    }
                    definitions.put(variableName, parseVariableDefinition(variableName, field.getValue()));
                }
                return definitions;
            }
            if (root.isArray()) {
                for (JsonNode item : root) {
                    String variableName = normalizeVariableName(item.path("name").asText(null));
                    if (variableName == null) {
                        continue;
                    }
                    definitions.put(variableName, parseVariableDefinition(variableName, item));
                }
                return definitions;
            }
            throw new ApiException("BIZ-1001", "variablesJson invalid", HttpStatus.BAD_REQUEST);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException("BIZ-1001", "variablesJson invalid", HttpStatus.BAD_REQUEST);
        }
    }

    private VariableDefinition parseVariableDefinition(String variableName, JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return new VariableDefinition(variableName, false, null, null, null);
        }
        if (!node.isObject()) {
            String directValue = jsonNodeToString(node);
            return new VariableDefinition(variableName, false, directValue, directValue, null);
        }
        return new VariableDefinition(
                variableName,
                node.path("required").asBoolean(false),
                firstNonBlank(
                        jsonNodeToString(node.path("sampleValue")),
                        jsonNodeToString(node.path("example")),
                        jsonNodeToString(node.path("sample"))
                ),
                firstNonBlank(
                        jsonNodeToString(node.path("defaultValue")),
                        jsonNodeToString(node.path("default")),
                        jsonNodeToString(node.path("value"))
                ),
                firstNonBlank(node.path("description").asText(null), node.path("label").asText(null))
        );
    }

    private Map<String, String> normalizeRuntimeVariables(Map<String, Object> runtimeVariables) {
        if (runtimeVariables == null || runtimeVariables.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        runtimeVariables.forEach((key, value) -> {
            String variableName = normalizeVariableName(key);
            String variableValue = objectValueToString(value);
            if (variableName != null && variableValue != null) {
                normalized.put(variableName, variableValue);
            }
        });
        return normalized;
    }

    private String resolveVariableValue(
            String variableName,
            VariableDefinition definition,
            Map<String, String> runtimeVariables,
            boolean strictMode
    ) {
        String runtimeValue = runtimeVariables.get(variableName);
        if (runtimeValue != null && !runtimeValue.isBlank()) {
            return runtimeValue;
        }
        if (definition != null) {
            if (definition.defaultValue() != null) {
                return definition.defaultValue();
            }
            if (!strictMode && definition.sampleValue() != null && !definition.sampleValue().isBlank()) {
                return definition.sampleValue();
            }
            if (!definition.required()) {
                return "";
            }
        }
        if (!strictMode) {
            return null;
        }
        return null;
    }

    private String normalizeVariableName(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String normalized = rawValue.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String objectValueToString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            String normalized = text.trim();
            return normalized.isBlank() ? null : normalized;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Collection<?> collection) {
            return collectionToString(collection);
        }
        if (value.getClass().isArray()) {
            return arrayToString(value);
        }
        try {
            String json = objectMapper.writeValueAsString(value);
            return json == null || json.isBlank() ? null : json;
        } catch (Exception ex) {
            String normalized = String.valueOf(value).trim();
            return normalized.isBlank() ? null : normalized;
        }
    }

    private String collectionToString(Collection<?> collection) {
        if (collection.isEmpty()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (Object item : collection) {
            String value = objectValueToString(item);
            if (value != null && !value.isBlank()) {
                values.add(value);
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        return String.join("\n", values);
    }

    private String arrayToString(Object array) {
        int length = Array.getLength(array);
        if (length == 0) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (int index = 0; index < length; index++) {
            String value = objectValueToString(Array.get(array, index));
            if (value != null && !value.isBlank()) {
                values.add(value);
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        return String.join("\n", values);
    }

    private List<AiChatMessage> extractPromptSeedMessages(JsonNode bundleNode) {
        if (bundleNode == null || !bundleNode.isObject()) {
            return List.of();
        }
        JsonNode messagesNode = bundleNode.path("messages");
        if (!messagesNode.isArray()) {
            return List.of();
        }
        List<AiChatMessage> messages = new ArrayList<>();
        for (JsonNode item : messagesNode) {
            if (item == null || !item.isObject()) {
                continue;
            }
            String content = firstNonBlank(
                    jsonNodeToString(item.path("content")),
                    jsonNodeToString(item.path("text"))
            );
            if (content == null || content.isBlank()) {
                continue;
            }
            messages.add(new AiChatMessage(normalizeMessageRole(item.path("role").asText(null)), content.trim()));
        }
        return List.copyOf(messages);
    }

    private String normalizeMessageRole(String rawValue) {
        String normalized = rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "system", "developer", "assistant", "user" -> normalized;
            case "model" -> "assistant";
            default -> "user";
        };
    }

    private String buildBundleSystemPrompt(String renderedContent, JsonNode bundleNode) {
        List<String> sections = new ArrayList<>();
        if (renderedContent != null && !renderedContent.isBlank()) {
            sections.add(renderedContent.trim());
        }
        if (bundleNode != null && bundleNode.isObject()) {
            String systemInstruction = firstNonBlank(
                    jsonNodeToString(bundleNode.path("systemInstruction")),
                    jsonNodeToString(bundleNode.path("developerInstruction")),
                    jsonNodeToString(bundleNode.path("instruction"))
            );
            if (systemInstruction != null && !systemInstruction.isBlank()) {
                sections.add(systemInstruction.trim());
            }
        }
        return sections.isEmpty() ? null : String.join("\n\n", sections);
    }

    private String jsonNodeToString(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            String value = node.asText("").trim();
            return value.isBlank() ? null : value;
        }
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            for (JsonNode item : node) {
                String value = jsonNodeToString(item);
                if (value != null && !value.isBlank()) {
                    values.add(value);
                }
            }
            return values.isEmpty() ? null : String.join("\n", values);
        }
        try {
            String json = objectMapper.writeValueAsString(node);
            return json == null || json.isBlank() ? null : json;
        } catch (Exception ex) {
            return null;
        }
    }

    private void throwIfMissing(boolean strictMode, List<String> missingVariables) {
        if (strictMode && !missingVariables.isEmpty()) {
            throw new ApiException(
                    "AI-2003",
                    "prompt template variables missing: " + String.join(", ", missingVariables),
                    HttpStatus.BAD_GATEWAY
            );
        }
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            throw new ApiException("AI-2003", "prompt template bundle render failed", HttpStatus.BAD_GATEWAY);
        }
    }

    private String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return null;
    }

    public record RenderResult(
            String renderedContent,
            List<String> placeholderVariables,
            List<String> missingVariables,
            Map<String, String> resolvedVariables
    ) {
    }

    public record BundleRenderResult(
            String renderedContent,
            String renderedBundleJson,
            String systemPrompt,
            List<AiChatMessage> promptSeedMessages,
            List<String> placeholderVariables,
            List<String> missingVariables,
            Map<String, String> resolvedVariables
    ) {
    }

    private record VariableDefinition(
            String name,
            boolean required,
            String sampleValue,
            String defaultValue,
            String description
    ) {
    }

    private static final class RenderState {

        private final LinkedHashSet<String> placeholders = new LinkedHashSet<>();
        private final List<String> missingVariables = new ArrayList<>();
        private final LinkedHashMap<String, String> resolvedVariables = new LinkedHashMap<>();

        private void addPlaceholder(String placeholder) {
            if (placeholder != null && !placeholder.isBlank()) {
                placeholders.add(placeholder);
            }
        }

        private void addMissing(String placeholder) {
            if (placeholder != null && !placeholder.isBlank() && !missingVariables.contains(placeholder)) {
                missingVariables.add(placeholder);
            }
        }

        private void addResolved(String placeholder, String value) {
            if (placeholder != null && !placeholder.isBlank() && value != null && !value.isBlank()) {
                resolvedVariables.put(placeholder, value);
            }
        }

        private LinkedHashSet<String> placeholders() {
            return placeholders;
        }

        private List<String> missingVariables() {
            return missingVariables;
        }

        private LinkedHashMap<String, String> resolvedVariables() {
            return resolvedVariables;
        }
    }
}
