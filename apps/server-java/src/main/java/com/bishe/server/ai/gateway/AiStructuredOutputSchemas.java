package com.bishe.server.ai.gateway;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 统一维护 AI 结构化输出 schema，保证不同 provider 对同一任务使用一致约束。
 */
final class AiStructuredOutputSchemas {

    private AiStructuredOutputSchemas() {
    }

    static Map<String, Object> resolveResponseJsonSchema(String taskType, String sceneCode) {
        String normalizedTaskType = safe(taskType).trim().toUpperCase(Locale.ROOT);
        String normalizedSceneCode = safe(sceneCode).trim().toUpperCase(Locale.ROOT);
        if ("RESUME".equals(normalizedTaskType) && "RESUME_OPTIMIZE".equals(normalizedSceneCode)) {
            return resumeOptimizeResponseJsonSchema();
        }
        if ("COMMUNITY_REPLY".equals(normalizedTaskType) && "COMMUNITY_PRE_ANSWER".equals(normalizedSceneCode)) {
            return singleStringFieldResponseJsonSchema("draftComment");
        }
        if ("COMMUNITY_REPLY".equals(normalizedTaskType) && "MENTOR_PREP_SHEET_GENERATE".equals(normalizedSceneCode)) {
            return mentorPrepSheetResponseJsonSchema();
        }
        if ("PORTRAIT_SUMMARY".equals(normalizedTaskType) && "STUDENT_PORTRAIT_SUMMARY".equals(normalizedSceneCode)) {
            return portraitSummaryResponseJsonSchema();
        }
        if ("ICEBREAK".equals(normalizedTaskType) && "ICEBREAK_MESSAGE".equals(normalizedSceneCode)) {
            return singleStringFieldResponseJsonSchema("messageDraft");
        }
        if ("INTERVIEW_TEXT".equals(normalizedTaskType) && "INTERVIEW_OPENING".equals(normalizedSceneCode)) {
            return singleStringFieldResponseJsonSchema("firstQuestion");
        }
        if ("INTERVIEW_TEXT".equals(normalizedTaskType) && "INTERVIEW_REPLY".equals(normalizedSceneCode)) {
            return interviewReplyResponseJsonSchema();
        }
        if ("INTERVIEW_TEXT".equals(normalizedTaskType) && "INTERVIEW_ANSWER_HELPER".equals(normalizedSceneCode)) {
            return interviewAnswerHelperResponseJsonSchema();
        }
        if ("INTERVIEW_SUMMARY".equals(normalizedTaskType) && "INTERVIEW_SUMMARY".equals(normalizedSceneCode)) {
            return interviewSummaryResponseJsonSchema();
        }
        return null;
    }

    static Map<String, Object> buildOpenAiResponseFormat(String taskType, String sceneCode) {
        Map<String, Object> schema = resolveResponseJsonSchema(taskType, sceneCode);
        if (schema == null || schema.isEmpty()) {
            return null;
        }
        Map<String, Object> jsonSchema = new LinkedHashMap<>();
        jsonSchema.put("name", buildSchemaName(taskType, sceneCode));
        jsonSchema.put("strict", true);
        jsonSchema.put("schema", schema);
        Map<String, Object> responseFormat = new LinkedHashMap<>();
        responseFormat.put("type", "json_schema");
        responseFormat.put("json_schema", jsonSchema);
        return responseFormat;
    }

    private static String buildSchemaName(String taskType, String sceneCode) {
        String normalizedTaskType = safe(taskType).trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        String normalizedSceneCode = safe(sceneCode).trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        String rawName = (normalizedTaskType + "_" + normalizedSceneCode + "_response").replaceAll("_+", "_");
        return rawName.isBlank() ? "structured_response" : rawName;
    }

    private static Map<String, Object> resumeOptimizeResponseJsonSchema() {
        return objectResponseJsonSchema(
                linkedProperties(
                        "summary", stringJsonSchema(),
                        "strengths", arrayJsonSchema(stringJsonSchema()),
                        "risks", arrayJsonSchema(stringJsonSchema()),
                        "suggestions", arrayJsonSchema(stringJsonSchema()),
                        "scoreLabel", enumStringJsonSchema(List.of("S", "A+", "A", "A-", "B+", "B", "B-", "C+", "C", "D")),
                        "structureItems", arrayJsonSchema(objectResponseJsonSchema(
                                linkedProperties(
                                        "label", stringJsonSchema(),
                                        "score", integerJsonSchema(1, 5),
                                        "tip", stringJsonSchema()
                                ),
                                List.of("label", "score", "tip")
                        )),
                        "rewriteItems", arrayJsonSchema(objectResponseJsonSchema(
                                linkedProperties(
                                        "id", stringJsonSchema(),
                                        "title", stringJsonSchema(),
                                        "problem", stringJsonSchema(),
                                        "beforeText", stringJsonSchema(),
                                        "afterText", stringJsonSchema()
                                ),
                                List.of("id", "title", "problem", "beforeText", "afterText")
                        ))
                ),
                List.of("summary", "strengths", "risks", "suggestions", "scoreLabel", "structureItems", "rewriteItems")
        );
    }

    private static Map<String, Object> interviewReplyResponseJsonSchema() {
        return objectResponseJsonSchema(
                linkedProperties(
                        "followUpQuestion", stringJsonSchema(),
                        "coachFeedback", stringJsonSchema(),
                        "scoreHint", integerJsonSchema(40, 98),
                        "shouldFinish", booleanJsonSchema(),
                        "finishReason", stringJsonSchema()
                ),
                List.of("followUpQuestion", "coachFeedback", "scoreHint", "shouldFinish")
        );
    }

    private static Map<String, Object> interviewSummaryResponseJsonSchema() {
        return objectResponseJsonSchema(
                linkedProperties(
                        "overallScore", integerJsonSchema(50, 100),
                        "strengths", arrayJsonSchema(stringJsonSchema()),
                        "weaknesses", arrayJsonSchema(stringJsonSchema()),
                        "suggestions", arrayJsonSchema(stringJsonSchema())
                ),
                List.of("overallScore", "strengths", "weaknesses", "suggestions")
        );
    }

    private static Map<String, Object> interviewAnswerHelperResponseJsonSchema() {
        return objectResponseJsonSchema(
                linkedProperties(
                        "overallLevel", enumStringJsonSchema(List.of("READY", "WARN", "INFO")),
                        "overallSummary", stringJsonSchema(),
                        "items", arrayJsonSchema(objectResponseJsonSchema(
                                linkedProperties(
                                        "key", enumStringJsonSchema(List.of("STAR", "METRICS", "COMPLETENESS")),
                                        "level", enumStringJsonSchema(List.of("READY", "WARN", "INFO")),
                                        "summary", stringJsonSchema(),
                                        "nextAction", stringJsonSchema()
                                ),
                                List.of("key", "level", "summary", "nextAction")
                        )),
                        "details", arrayJsonSchema(stringJsonSchema())
                ),
                List.of("overallLevel", "overallSummary", "items", "details")
        );
    }

    private static Map<String, Object> mentorPrepSheetResponseJsonSchema() {
        return objectResponseJsonSchema(
                linkedProperties(
                        "summaryDraft", stringJsonSchema(),
                        "coreQuestions", arrayJsonSchema(stringJsonSchema()),
                        "suggestedMaterials", arrayJsonSchema(stringJsonSchema()),
                        "expectedOutcomes", arrayJsonSchema(stringJsonSchema())
                ),
                List.of("summaryDraft", "coreQuestions", "suggestedMaterials", "expectedOutcomes")
        );
    }

    private static Map<String, Object> portraitSummaryResponseJsonSchema() {
        return objectResponseJsonSchema(
                linkedProperties(
                        "headline", stringJsonSchema(),
                        "summary", stringJsonSchema(),
                        "nextActions", arrayJsonSchema(stringJsonSchema())
                ),
                List.of("headline", "summary", "nextActions")
        );
    }

    private static Map<String, Object> singleStringFieldResponseJsonSchema(String fieldName) {
        return objectResponseJsonSchema(
                linkedProperties(fieldName, stringJsonSchema()),
                List.of(fieldName)
        );
    }

    private static Map<String, Object> objectResponseJsonSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private static Map<String, Object> stringJsonSchema() {
        return Map.of("type", "string");
    }

    private static Map<String, Object> enumStringJsonSchema(List<String> enumValues) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "string");
        schema.put("enum", enumValues);
        return schema;
    }

    private static Map<String, Object> booleanJsonSchema() {
        return Map.of("type", "boolean");
    }

    private static Map<String, Object> integerJsonSchema(Integer minimum, Integer maximum) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "integer");
        if (minimum != null) {
            schema.put("minimum", minimum);
        }
        if (maximum != null) {
            schema.put("maximum", maximum);
        }
        return schema;
    }

    private static Map<String, Object> arrayJsonSchema(Map<String, Object> itemSchema) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "array");
        schema.put("items", itemSchema);
        return schema;
    }

    private static Map<String, Object> linkedProperties(Object... keyValues) {
        Map<String, Object> properties = new LinkedHashMap<>();
        if (keyValues == null) {
            return properties;
        }
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            properties.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return properties;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
