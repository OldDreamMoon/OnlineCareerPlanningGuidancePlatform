package com.bishe.server.ai.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 多 provider 路由与解析测试：覆盖 OpenAI-compatible 与 Gemini Native。
 */
@SpringBootTest(properties = {
        "ai.gateway.mode=ROUTED",
        "ai.gateway.allow-legacy-fallback=false",
        "spring.datasource.url=jdbc:h2:mem:bishe_ai_gateway_provider;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@ActiveProfiles("test")
@Transactional
class AiGatewayProviderClientTest {

    @Autowired
    private AiGatewayService aiGatewayService;

    @Autowired
    private JdbcTestHelper jdbcTestHelper;

    @Autowired
    private AiConfigCryptoService cryptoService;

    @Autowired
    private AiGatewayAdminService aiGatewayAdminService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AiRouteCacheService aiRouteCacheService;

    private HttpServer server;

    @BeforeEach
    void setUp() {
        jdbcTestHelper.clearAiGatewayConfigs();
        aiRouteCacheService.evictAllNow();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void routedMode_shouldParseOpenAiResumeResponseAndUsage() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> respondJson(exchange, 200, buildOpenAiResponse(Map.of(
                "summary", "简历较清晰",
                "strengths", java.util.List.of("项目贴近岗位"),
                "risks", java.util.List.of("量化指标偏少"),
                "suggestions", java.util.List.of("补充更多数据结果")
        ), 120, 40, 160)));
        server.start();

        long providerId = jdbcTestHelper.insertProvider(
                "OPENAI_MAIN",
                AiProviderType.OPENAI_COMPATIBLE.name(),
                "OpenAI 主路由",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                cryptoService.encrypt("test-openai-key"),
                cryptoService.mask("test-openai-key"),
                true,
                3000,
                1,
                new BigDecimal("0.100000"),
                new BigDecimal("0.200000"),
                null
        );
        jdbcTestHelper.insertRoute("RESUME_MAIN", "RESUME", null, providerId, "fake-model", 10, true, new BigDecimal("0.20"), null, null);

        AiGatewayService.ResumeOptimizeGatewayResult result = aiGatewayService.optimizeResume(
                "Backend Engineer",
                "校招正式批",
                "负责核心后端服务研发，要求熟悉 Java、MySQL、Redis 与性能优化。",
                "负责用户增长平台后端接口开发，优化缓存链路后延迟降低 30%。",
                "test-model"
        );

        assertThat(result.summary()).isEqualTo("简历较清晰");
        assertThat(result.strengths()).containsExactly("项目贴近岗位");
        assertThat(result.gatewayResult().provider()).isEqualTo("OPENAI_MAIN");
        assertThat(result.gatewayResult().model()).isEqualTo("test-model");
        assertThat(result.gatewayResult().requestTokens()).isEqualTo(120);
        assertThat(result.gatewayResult().responseTokens()).isEqualTo(40);
        assertThat(result.gatewayResult().estimatedCost().toPlainString()).isEqualTo("0.020000");
    }

    @Test
    void routedMode_shouldFallbackToRouteModel_whenPreferenceStillUsesMockAlias() throws Exception {
        AtomicReference<String> capturedModel = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>("");
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            String rawBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            capturedBody.set(rawBody);
            JsonNode requestJson = new ObjectMapper().readTree(rawBody);
            capturedModel.set(requestJson.path("model").asText());
            respondJson(exchange, 200, buildOpenAiResponse(Map.of(
                    "firstQuestion", "请介绍一个最能体现结果的项目。"
            ), 60, 20, 80));
        });
        server.start();

        long providerId = jdbcTestHelper.insertProvider(
                "OPENAI_ROUTE",
                AiProviderType.OPENAI_COMPATIBLE.name(),
                "OpenAI 文本",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                cryptoService.encrypt("route-key"),
                cryptoService.mask("route-key"),
                true,
                3000,
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null
        );
        jdbcTestHelper.insertRoute("INTERVIEW_OPENING_ROUTE", "INTERVIEW_TEXT", "INTERVIEW_OPENING", providerId, "route-model", 1, true, new BigDecimal("0.20"), null, "{\"responseFormatJsonObject\":true}");

        AiGatewayService.InterviewQuestionGatewayResult result = aiGatewayService.createInterviewSession("Backend Engineer", "mock-economy-model");

        assertThat(result.gatewayResult().model()).isEqualTo("route-model");
        assertThat(capturedModel.get()).isEqualTo("route-model");
        JsonNode requestJson = new ObjectMapper().readTree(capturedBody.get());
        assertThat(requestJson.path("response_format").path("type").asText()).isEqualTo("json_object");
    }

    @Test
    void routedMode_shouldUseJsonSchemaResponseFormatForInterviewByDefault() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>("");
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respondJson(exchange, 200, buildOpenAiResponse(Map.of(
                    "firstQuestion", "你好，我们开始这轮模拟面试。先请你挑一个最能体现结果的项目，讲讲你的职责和结果。"
            ), 62, 18, 80));
        });
        server.start();

        long providerId = jdbcTestHelper.insertProvider(
                "OPENAI_JSON_SCHEMA",
                AiProviderType.OPENAI_COMPATIBLE.name(),
                "OpenAI Json Schema",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                cryptoService.encrypt("json-schema-key"),
                cryptoService.mask("json-schema-key"),
                true,
                3000,
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null
        );
        jdbcTestHelper.insertRoute(
                "INTERVIEW_OPENING_JSON_SCHEMA",
                "INTERVIEW_TEXT",
                "INTERVIEW_OPENING",
                providerId,
                "json-schema-model",
                1,
                true,
                new BigDecimal("0.20"),
                null,
                null
        );

        AiGatewayService.InterviewQuestionGatewayResult result = aiGatewayService.createInterviewSession("Backend Engineer", "mock-economy-model");

        assertThat(result.gatewayResult().model()).isEqualTo("json-schema-model");
        JsonNode requestJson = new ObjectMapper().readTree(capturedBody.get());
        assertThat(requestJson.path("response_format").path("type").asText()).isEqualTo("json_schema");
        assertThat(requestJson.path("response_format").path("json_schema").path("strict").asBoolean()).isTrue();
        assertThat(requestJson.path("response_format").path("json_schema").path("name").asText())
                .isEqualTo("interview_text_interview_opening_response");
        assertThat(requestJson.path("response_format").path("json_schema").path("schema").path("properties").path("firstQuestion").path("type").asText())
                .isEqualTo("string");
    }

    @Test
    void routedMode_shouldEvictRouteCacheAfterAdminRouteUpdate() throws Exception {
        AtomicReference<String> capturedModel = new AtomicReference<>("");
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            String rawBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JsonNode requestJson = new ObjectMapper().readTree(rawBody);
            capturedModel.set(requestJson.path("model").asText());
            respondJson(exchange, 200, buildOpenAiResponse(Map.of(
                    "firstQuestion", "请介绍一个最能体现结果的项目。"
            ), 60, 20, 80));
        });
        server.start();

        long providerId = jdbcTestHelper.insertProvider(
                "OPENAI_ROUTE_CACHE",
                AiProviderType.OPENAI_COMPATIBLE.name(),
                "OpenAI Route Cache",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                cryptoService.encrypt("route-cache-key"),
                cryptoService.mask("route-cache-key"),
                true,
                3000,
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null
        );
        jdbcTestHelper.insertProviderModel(
                providerId,
                "route-model-v1",
                "Route Model V1",
                true,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                "[\"INTERVIEW_TEXT\"]",
                null
        );
        jdbcTestHelper.insertProviderModel(
                providerId,
                "route-model-v2",
                "Route Model V2",
                true,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                "[\"INTERVIEW_TEXT\"]",
                null
        );
        jdbcTestHelper.insertRoute(
                "INTERVIEW_OPENING_ROUTE_CACHE",
                "INTERVIEW_TEXT",
                "INTERVIEW_OPENING",
                providerId,
                "route-model-v1",
                1,
                true,
                new BigDecimal("0.20"),
                null,
                null
        );
        Long routeId = jdbcTemplate.queryForObject(
                "SELECT id FROM ai_model_routes WHERE route_code = ?",
                Long.class,
                "INTERVIEW_OPENING_ROUTE_CACHE"
        );
        assertThat(routeId).isNotNull();

        AiGatewayService.InterviewQuestionGatewayResult firstResult =
                aiGatewayService.createInterviewSession("Backend Engineer", "mock-economy-model");

        assertThat(firstResult.gatewayResult().model()).isEqualTo("route-model-v1");
        assertThat(capturedModel.get()).isEqualTo("route-model-v1");

        aiGatewayAdminService.updateRoute(
                routeId,
                 new AiGatewayAdminService.UpsertRouteCommand(
                         "INTERVIEW_OPENING_ROUTE_CACHE",
                         "INTERVIEW_TEXT",
                         "INTERVIEW_OPENING",
                         null,
                         providerId,
                         "route-model-v2",
                         1,
                         null,
                         AiExecutionMode.SYNC_BLOCKING.name(),
                         true,
                         "0.20",
                         null,
                         null,
                         null
                 )
         );

        AiGatewayService.InterviewQuestionGatewayResult secondResult =
                aiGatewayService.createInterviewSession("Backend Engineer", "mock-economy-model");

        assertThat(secondResult.gatewayResult().model()).isEqualTo("route-model-v2");
        assertThat(capturedModel.get()).isEqualTo("route-model-v2");
    }

    @Test
    void routedMode_shouldAppendV1betaForGeminiCompatibleBaseUrl() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>("");
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1beta/models/fake-gemini-chat:generateContent", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respondJson(exchange, 200, """
                    {
                      "candidates": [{"content": {"parts": [{"text": "{\\"firstQuestion\\":\\"请介绍一个你主导的后端项目。\\"}"}]}}],
                      "usageMetadata": {"promptTokenCount": 21, "candidatesTokenCount": 11, "totalTokenCount": 32}
                    }
                    """);
        });
        server.start();

        long providerId = jdbcTestHelper.insertProvider(
                "GEMINI_CHAT",
                AiProviderType.GEMINI_NATIVE.name(),
                "Gemini Chat",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                cryptoService.encrypt("gemini-key"),
                cryptoService.mask("gemini-key"),
                true,
                3000,
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null
        );
        jdbcTestHelper.insertRoute("INTERVIEW_OPENING_GEMINI", "INTERVIEW_TEXT", "INTERVIEW_OPENING", providerId, "fake-gemini-chat", 1, true, new BigDecimal("0.20"), null, null);

        AiGatewayService.InterviewQuestionGatewayResult result = aiGatewayService.createInterviewSession("Backend Engineer", "fake-gemini-chat");

        assertThat(result.firstQuestion()).contains("后端项目");
        assertThat(result.gatewayResult().provider()).isEqualTo("GEMINI_CHAT");
        JsonNode requestJson = new ObjectMapper().readTree(capturedBody.get());
        assertThat(requestJson.path("generationConfig").path("responseMimeType").asText()).isEqualTo("application/json");
        assertThat(requestJson.path("generationConfig").path("responseJsonSchema").path("type").asText()).isEqualTo("object");
        assertThat(requestJson.path("generationConfig").path("responseJsonSchema").path("properties").path("firstQuestion").path("type").asText()).isEqualTo("string");
        assertThat(requestJson.path("generationConfig").path("thinkingConfig").path("thinkingBudget").asInt()).isEqualTo(1024);
        assertThat(result.gatewayResult().reasoningEffort()).isEqualTo("MEDIUM");
        assertThat(result.gatewayResult().thinkingBudget()).isEqualTo(1024);
        assertThat(result.gatewayResult().thinkingLevel()).isNull();
    }

    @Test
    void routedMode_shouldFallbackOpeningQuestionWhenProviderReturnsSchemaPlaceholder() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1beta/models/fake-gemini-placeholder:generateContent", exchange -> respondJson(exchange, 200, """
                {
                  "candidates": [{"content": {"parts": [{"text": "{\\"firstQuestion\\":\\"string\\"}"}]}}],
                  "usageMetadata": {"promptTokenCount": 20, "candidatesTokenCount": 4, "totalTokenCount": 24}
                }
                """));
        server.start();

        long providerId = jdbcTestHelper.insertProvider(
                "GEMINI_PLACEHOLDER",
                AiProviderType.GEMINI_NATIVE.name(),
                "Gemini Placeholder",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta",
                cryptoService.encrypt("gemini-placeholder-key"),
                cryptoService.mask("gemini-placeholder-key"),
                true,
                3000,
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "{\"authMode\":\"BEARER_TOKEN\"}"
        );
        jdbcTestHelper.insertRoute(
                "INTERVIEW_OPENING_GEMINI_PLACEHOLDER",
                "INTERVIEW_TEXT",
                "INTERVIEW_OPENING",
                providerId,
                "fake-gemini-placeholder",
                1,
                true,
                new BigDecimal("0.20"),
                null,
                null
        );

        AiGatewayService.InterviewQuestionGatewayResult result = aiGatewayService.createInterviewSession(
                "Backend Engineer",
                "interviewType=PROJECT_DEEP_DIVE",
                null,
                "mock-economy-model"
        );

        assertThat(result.firstQuestion()).contains("Backend Engineer");
        assertThat(result.firstQuestion()).isNotEqualTo("string");
    }

    @Test
    void routedMode_shouldUseThinkingLevelForGemini3Models() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>("");
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1beta/models/gemini-3.0-flash:generateContent", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respondJson(exchange, 200, """
                    {
                      "candidates": [{"content": {"parts": [{"text": "{\\"firstQuestion\\":\\"请介绍一个你负责性能优化的项目。\\"}"}]}}],
                      "usageMetadata": {"promptTokenCount": 31, "candidatesTokenCount": 14, "totalTokenCount": 45, "thoughtsTokenCount": 12}
                    }
                    """);
        });
        server.start();

        long providerId = jdbcTestHelper.insertProvider(
                "GEMINI_THINKING_LEVEL",
                AiProviderType.GEMINI_NATIVE.name(),
                "Gemini 3 Thinking",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                cryptoService.encrypt("gemini-3-key"),
                cryptoService.mask("gemini-3-key"),
                true,
                3000,
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null
        );
        jdbcTestHelper.insertRoute(
                "INTERVIEW_OPENING_GEMINI_3",
                "INTERVIEW_TEXT",
                "INTERVIEW_OPENING",
                providerId,
                "gemini-3.0-flash",
                1,
                true,
                new BigDecimal("0.20"),
                null,
                null
        );

        AiGatewayService.InterviewQuestionGatewayResult result = aiGatewayService.createInterviewSession("Backend Engineer", "mock-economy-model");

        JsonNode requestJson = new ObjectMapper().readTree(capturedBody.get());
        assertThat(requestJson.path("generationConfig").path("thinkingConfig").path("thinkingLevel").asText()).isEqualTo("medium");
        assertThat(requestJson.path("generationConfig").path("thinkingConfig").has("thinkingBudget")).isFalse();
        assertThat(result.gatewayResult().reasoningEffort()).isEqualTo("MEDIUM");
        assertThat(result.gatewayResult().thinkingBudget()).isNull();
        assertThat(result.gatewayResult().thinkingLevel()).isEqualTo("medium");
        assertThat(result.gatewayResult().thoughtsTokens()).isEqualTo(12);
    }

    @Test
    void routedMode_shouldPreferPdfCapableRouteForResumePdf() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>("");
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1beta/models/fake-gemini-pdf:generateContent", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respondJson(exchange, 200, """
                    {
                      "candidates": [
                        {
                          "content": {
                            "parts": [
                              {
                                "text": "{\\"summary\\":\\"PDF 路由已切到 Gemini Native\\",\\"strengths\\":[\\"命中了原生 PDF provider\\"],\\"risks\\":[\\"仍需关注上游稳定性\\"],\\"suggestions\\":[\\"继续补充真实联调回归\\"],\\"scoreLabel\\":\\"A\\",\\"structureItems\\":[{\\"label\\":\\"岗位匹配\\",\\"score\\":4,\\"tip\\":\\"当前路由已经正确命中支持 PDF 的 provider\\"}],\\"rewriteItems\\":[{\\"id\\":\\"rewrite-1\\",\\"title\\":\\"项目成果表达\\",\\"problem\\":\\"原表达量化不足\\",\\"beforeText\\":\\"负责订单中心优化\\",\\"afterText\\":\\"主导订单中心链路优化，将核心接口延迟降低 30%，并补齐监控基线\\"}]}"
                              }
                            ]
                          }
                        }
                      ],
                      "usageMetadata": {"promptTokenCount": 42, "candidatesTokenCount": 18, "totalTokenCount": 60}
                    }
                    """);
        });
        server.start();

        long openAiProviderId = jdbcTestHelper.insertProvider(
                "OPENAI_PDF_UNSUPPORTED",
                AiProviderType.OPENAI_COMPATIBLE.name(),
                "OpenAI PDF Unsupported",
                "http://127.0.0.1:1/v1",
                cryptoService.encrypt("openai-pdf-key"),
                cryptoService.mask("openai-pdf-key"),
                true,
                3000,
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null
        );
        jdbcTestHelper.insertRoute(
                "RESUME_PDF_OPENAI_FIRST",
                "RESUME",
                "RESUME_OPTIMIZE",
                openAiProviderId,
                "fake-openai-pdf",
                10,
                true,
                new BigDecimal("0.20"),
                null,
                null,
                null
        );

        long geminiProviderId = jdbcTestHelper.insertProvider(
                "GEMINI_PDF_NATIVE",
                AiProviderType.GEMINI_NATIVE.name(),
                "Gemini PDF Native",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                cryptoService.encrypt("gemini-pdf-key"),
                cryptoService.mask("gemini-pdf-key"),
                true,
                3000,
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null
        );
        jdbcTestHelper.insertRoute(
                "RESUME_PDF_GEMINI_NATIVE",
                "RESUME",
                "RESUME_OPTIMIZE",
                geminiProviderId,
                "fake-gemini-pdf",
                20,
                true,
                new BigDecimal("0.20"),
                null,
                null,
                null
        );

        MockMultipartFile resumeFile = new MockMultipartFile(
                "resumeFile",
                "resume.pdf",
                "application/pdf",
                "%PDF-1.4 fake resume".getBytes(StandardCharsets.UTF_8)
        );

        AiGatewayService.ResumeOptimizeGatewayResult result = aiGatewayService.optimizeResumePdf(
                "Backend Engineer",
                "校招正式批",
                "负责核心后端服务研发，要求熟悉 Java、MySQL、Redis 与性能优化。",
                resumeFile,
                "mock-premium-model"
        );

        assertThat(result.summary()).isEqualTo("PDF 路由已切到 Gemini Native");
        assertThat(result.gatewayResult().provider()).isEqualTo("GEMINI_PDF_NATIVE");
        JsonNode requestJson = new ObjectMapper().readTree(capturedBody.get());
        assertThat(requestJson.path("contents").path(0).path("parts").path(0).path("text").asText()).contains("resumeFileName=resume.pdf");
        assertThat(requestJson.path("generationConfig").path("responseMimeType").asText()).isEqualTo("application/json");
        assertThat(requestJson.path("generationConfig").path("responseJsonSchema").path("properties").path("summary").path("type").asText()).isEqualTo("string");
        assertThat(requestJson.path("generationConfig").path("responseJsonSchema").path("properties").path("structureItems").path("items").path("properties").path("score").path("type").asText()).isEqualTo("integer");
    }

    @Test
    void routedMode_shouldUseActivePromptTemplateContentForRoute() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>("");
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respondJson(exchange, 200, buildOpenAiResponse(Map.of(
                    "followUpQuestion", "请展开讲讲你如何做容量预估？",
                    "coachFeedback", "建议补充容量评估依据。",
                    "scoreHint", 82,
                    "shouldFinish", false
            ), 70, 24, 94));
        });
        server.start();

        long providerId = jdbcTestHelper.insertProvider(
                "OPENAI_TEMPLATE_ROUTE",
                AiProviderType.OPENAI_COMPATIBLE.name(),
                "OpenAI Template Route",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                cryptoService.encrypt("template-route-key"),
                cryptoService.mask("template-route-key"),
                true,
                3000,
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null
        );
        jdbcTestHelper.insertPromptTemplate(
                "INTERVIEW_TEXT",
                "INTERVIEW_REPLY_CORE",
                1,
                "ACTIVE",
                AiPromptTemplateFormat.TEXT.name(),
                "你是结构化面试官，请根据候选人的回答继续追问，并仅输出 JSON。",
                "文本面试追问模板",
                null,
                null
        );
        jdbcTestHelper.insertRoute(
                "INTERVIEW_REPLY_TEMPLATE_ROUTE",
                "INTERVIEW_TEXT",
                "INTERVIEW_REPLY",
                providerId,
                "template-model",
                1,
                true,
                new BigDecimal("0.20"),
                null,
                "INTERVIEW_REPLY_CORE",
                "{\"responseFormatJsonObject\":true}"
        );

        AiGatewayService.InterviewReplyGatewayResult result = aiGatewayService.replyInterview(
                "Backend Engineer",
                java.util.List.of("assistant: 请介绍一个你最有代表性的项目。", "user: 我负责订单中心改造。"),
                "我主导了订单中心容量治理和缓存改造，核心接口延迟下降了 30%。",
                "template-model"
        );

        assertThat(result.followUpQuestion()).contains("容量预估");
        assertThat(result.gatewayResult().provider()).isEqualTo("OPENAI_TEMPLATE_ROUTE");
        assertThat(capturedBody.get()).contains("你是结构化面试官");
    }

    @Test
    void routedMode_shouldStreamInterviewReplyDeltaFromOpenAiCompatibleProvider() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>("");
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respondSse(exchange, buildOpenAiStreamResponse(
                    "{\"followUpQuestion\":\"请继续介绍",
                    "你如何做容量预估？\",\"coachFeedback\":\"建议补充容量评估依据。\",\"scoreHint\":82,\"shouldFinish\":false}",
                    70,
                    24,
                    94
            ));
        });
        server.start();

        long providerId = jdbcTestHelper.insertProvider(
                "OPENAI_STREAM_ROUTE",
                AiProviderType.OPENAI_COMPATIBLE.name(),
                "OpenAI Stream Route",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                cryptoService.encrypt("stream-route-key"),
                cryptoService.mask("stream-route-key"),
                true,
                3000,
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null
        );
        jdbcTestHelper.insertRoute(
                "INTERVIEW_REPLY_STREAM_ROUTE",
                "INTERVIEW_TEXT",
                "INTERVIEW_REPLY",
                providerId,
                "stream-model",
                1,
                true,
                new BigDecimal("0.20"),
                null,
                null,
                "{\"responseFormatJsonObject\":true}"
        );

        StringBuilder streamedDelta = new StringBuilder();
        AiGatewayService.InterviewReplyGatewayResult result = aiGatewayService.streamInterviewReply(
                "Backend Engineer",
                java.util.List.of("assistant: 请介绍一个你最有代表性的项目。", "user: 我负责订单中心改造。"),
                "我主导了订单中心容量治理和缓存改造，核心接口延迟下降了 30%。",
                "stream-model",
                streamedDelta::append
        );

        assertThat(streamedDelta.toString()).isEqualTo("请继续介绍你如何做容量预估？");
        assertThat(result.followUpQuestion()).isEqualTo("请继续介绍你如何做容量预估？");
        assertThat(result.coachFeedback()).isEqualTo("建议补充容量评估依据。");
        JsonNode requestJson = new ObjectMapper().readTree(capturedBody.get());
        assertThat(requestJson.path("stream").asBoolean()).isTrue();
        assertThat(requestJson.path("stream_options").path("include_usage").asBoolean()).isTrue();
    }

    @Test
    void routedMode_shouldParseMarkdownWrappedJsonContent() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> respondJson(exchange, 200, buildOpenAiRawContentResponse(
                """
                        ```json
                        {"firstQuestion":"请介绍一个你主导且有量化结果的项目。"}
                        ```
                        """,
                42,
                18,
                60
        )));
        server.start();

        long providerId = jdbcTestHelper.insertProvider(
                "OPENAI_MARKDOWN_JSON",
                AiProviderType.OPENAI_COMPATIBLE.name(),
                "OpenAI Markdown JSON",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                cryptoService.encrypt("markdown-key"),
                cryptoService.mask("markdown-key"),
                true,
                3000,
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null
        );
        jdbcTestHelper.insertRoute("INTERVIEW_OPENING_MARKDOWN", "INTERVIEW_TEXT", "INTERVIEW_OPENING", providerId, "markdown-model", 1, true, new BigDecimal("0.20"), null, null);

        AiGatewayService.InterviewQuestionGatewayResult result = aiGatewayService.createInterviewSession("Backend Engineer", "markdown-model");

        assertThat(result.firstQuestion()).contains("量化结果");
        assertThat(result.gatewayResult().provider()).isEqualTo("OPENAI_MARKDOWN_JSON");
        assertThat(result.gatewayResult().requestTokens()).isEqualTo(42);
        assertThat(result.gatewayResult().responseTokens()).isEqualTo(18);
    }

    @Test
    void routedMode_shouldFallbackReplyWhenStructuredContentCannotBeParsed() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> respondJson(exchange, 200, buildOpenAiRawContentResponse(
                """
                        这是一次不规范输出。
                        {"followUpQuestion":"请继续讲讲你怎么做容量预估？"}
                        还有一段多余说明 {"coachFeedback":"这段不该被一起截取"}
                        """,
                46,
                16,
                62
        )));
        server.start();

        long providerId = jdbcTestHelper.insertProvider(
                "OPENAI_REPLY_FALLBACK",
                AiProviderType.OPENAI_COMPATIBLE.name(),
                "OpenAI Reply Fallback",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                cryptoService.encrypt("reply-fallback-key"),
                cryptoService.mask("reply-fallback-key"),
                true,
                3000,
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null
        );
        jdbcTestHelper.insertRoute(
                "INTERVIEW_REPLY_FALLBACK_ROUTE",
                "INTERVIEW_TEXT",
                "INTERVIEW_REPLY",
                providerId,
                "reply-fallback-model",
                1,
                true,
                new BigDecimal("0.20"),
                null,
                null,
                null
        );

        AiGatewayService.InterviewReplyGatewayResult result = aiGatewayService.replyInterview(
                "Backend Engineer",
                java.util.List.of("assistant: 请介绍一下项目。", "user: 我负责缓存和容量治理。"),
                "我主导了缓存和索引优化，把接口延迟降低了 30%。",
                "reply-fallback-model"
        );

        assertThat(result.followUpQuestion()).isNotBlank();
        assertThat(result.followUpQuestion()).doesNotContain("不规范输出");
        assertThat(result.coachFeedback()).isNotBlank();
    }

    @Test
    void routedMode_shouldOptimizeResumePdfWithGeminiInlinePdfData() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>("");
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1beta/models/fake-gemini-resume:generateContent", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respondJson(exchange, 200, buildGeminiTextResponse(
                    "{\"summary\":\"简历重点比较清晰。\",\"strengths\":[\"项目结果明确\"],\"risks\":[\"可再补更多量化指标\"],\"suggestions\":[\"补充岗位相关技术亮点\"]}",
                    28,
                    18,
                    46
            ));
        });
        server.start();

        long providerId = jdbcTestHelper.insertProvider(
                "GEMINI_RESUME_PDF",
                AiProviderType.GEMINI_NATIVE.name(),
                "Gemini Resume PDF",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta",
                cryptoService.encrypt("gemini-resume-key"),
                cryptoService.mask("gemini-resume-key"),
                true,
                3000,
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null
        );
        jdbcTestHelper.insertRoute(
                "RESUME_GEMINI_PDF_ROUTE",
                "RESUME",
                "RESUME_OPTIMIZE",
                providerId,
                "fake-gemini-resume",
                1,
                true,
                new BigDecimal("0.20"),
                null,
                null
        );

        MockMultipartFile resumeFile = new MockMultipartFile(
                "resumeFile",
                "resume.pdf",
                "application/pdf",
                "%PDF-1.4 fake pdf bytes".getBytes(StandardCharsets.UTF_8)
        );
        AiGatewayService.ResumeOptimizeGatewayResult result = aiGatewayService.optimizeResumePdf(
                "Backend Engineer",
                "校招正式批",
                "负责核心后端服务研发，要求熟悉 Java、MySQL、Redis 与性能优化。",
                resumeFile,
                "mock-economy-model"
        );

        JsonNode requestJson = new ObjectMapper().readTree(capturedBody.get());
        assertThat(requestJson.path("contents").path(0).path("role").asText()).isEqualTo("user");
        assertThat(requestJson.path("contents").path(0).path("parts").path(0).path("text").asText()).contains("targetRole=Backend Engineer");
        assertThat(requestJson.path("contents").path(0).path("parts").path(0).path("text").asText()).contains("targetContext=校招正式批");
        assertThat(requestJson.path("contents").path(0).path("parts").path(0).path("text").asText()).contains("jobDescription=");
        assertThat(requestJson.path("contents").path(0).path("parts").path(0).path("text").asText()).contains("resumeFileName=resume.pdf");
        assertThat(requestJson.path("contents").path(0).path("parts").path(1).path("inlineData").path("mimeType").asText()).isEqualTo("application/pdf");
        assertThat(requestJson.path("contents").path(0).path("parts").path(1).path("inlineData").path("data").asText()).isNotBlank();
        assertThat(requestJson.path("generationConfig").path("responseMimeType").asText()).isEqualTo("application/json");
        assertThat(requestJson.path("generationConfig").path("responseJsonSchema").path("properties").path("scoreLabel").path("enum").size()).isGreaterThan(0);
        assertThat(capturedBody.get()).doesNotContain("fileData");
        assertThat(capturedBody.get()).doesNotContain("fileUri");
        assertThat(result.summary()).isEqualTo("简历重点比较清晰。");
        assertThat(result.gatewayResult().provider()).isEqualTo("GEMINI_RESUME_PDF");
        assertThat(result.gatewayResult().model()).isEqualTo("fake-gemini-resume");
    }

    @Test
    void routedMode_shouldRenderMessageBundlePromptTemplateIntoRuntimeMessages() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>("");
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respondJson(exchange, 200, buildOpenAiResponse(Map.of(
                    "followUpQuestion", "请继续说明你的量化结果。",
                    "coachFeedback", "继续补充量化结果",
                    "scoreHint", 86,
                    "shouldFinish", false
            ), 80, 20, 100));
        });
        server.start();

        long providerId = jdbcTestHelper.insertProvider(
                "OPENAI_BUNDLE_ROUTE",
                AiProviderType.OPENAI_COMPATIBLE.name(),
                "OpenAI Bundle Route",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                cryptoService.encrypt("bundle-route-key"),
                cryptoService.mask("bundle-route-key"),
                true,
                3000,
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null
        );
        jdbcTestHelper.insertPromptTemplate(
                "INTERVIEW_TEXT",
                "INTERVIEW_REPLY_BUNDLE",
                1,
                "ACTIVE",
                AiPromptTemplateFormat.MESSAGE_BUNDLE.name(),
                "bundle-header {{targetRole}}",
                "面试追问 bundle 模板",
                null,
                """
                {
                  "systemInstruction": "bundle-system role={{targetRole}}",
                  "messages": [
                    {"role": "developer", "content": "bundle-developer"},
                    {"role": "user", "content": "few-shot-user"},
                    {"role": "assistant", "content": "few-shot-assistant"}
                  ]
                }
                """
        );
        jdbcTestHelper.insertRoute(
                "INTERVIEW_REPLY_BUNDLE_ROUTE",
                "INTERVIEW_TEXT",
                "INTERVIEW_REPLY",
                providerId,
                "bundle-model",
                1,
                true,
                new BigDecimal("0.20"),
                null,
                "INTERVIEW_REPLY_BUNDLE",
                "{\"responseFormatJsonObject\":true}"
        );

        AiGatewayService.InterviewReplyGatewayResult result = aiGatewayService.replyInterview(
                "Backend Engineer",
                java.util.List.of("assistant: 你好，请介绍一下项目。"),
                "我负责缓存优化，把接口延迟降低了 30%。",
                "mock-economy-model"
        );

        JsonNode requestJson = new ObjectMapper().readTree(capturedBody.get());
        assertThat(requestJson.path("messages").path(0).path("role").asText()).isEqualTo("system");
        assertThat(requestJson.path("messages").path(0).path("content").asText()).contains("bundle-header Backend Engineer");
        assertThat(requestJson.path("messages").path(0).path("content").asText()).contains("bundle-system role=Backend Engineer");
        assertThat(requestJson.path("messages").path(1).path("role").asText()).isEqualTo("system");
        assertThat(requestJson.path("messages").path(1).path("content").asText()).isEqualTo("bundle-developer");
        assertThat(requestJson.path("messages").path(2).path("role").asText()).isEqualTo("user");
        assertThat(requestJson.path("messages").path(2).path("content").asText()).isEqualTo("few-shot-user");
        assertThat(requestJson.path("messages").path(3).path("role").asText()).isEqualTo("assistant");
        assertThat(requestJson.path("messages").path(3).path("content").asText()).isEqualTo("few-shot-assistant");
        assertThat(result.followUpQuestion()).isEqualTo("请继续说明你的量化结果。");
        assertThat(result.gatewayResult().model()).isEqualTo("bundle-model");
    }

    @Test
    void routedMode_shouldStreamInterviewReplyWithGeminiSseAndBearerAuth() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>("");
        AtomicReference<String> capturedAuthorization = new AtomicReference<>("");
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1beta/models/fake-gemini-stream:streamGenerateContent", exchange -> {
            capturedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respondSse(exchange, buildGeminiStreamResponse(
                    "{\"followUpQuestion\":\"请继续说明",
                    "你的技术取舍。\",\"coachFeedback\":\"继续补充技术取舍\",\"scoreHint\":88,\"shouldFinish\":false}",
                    24,
                    14,
                    38
            ));
        });
        server.start();

        long providerId = jdbcTestHelper.insertProvider(
                "GEMINI_STREAM_BEARER",
                AiProviderType.GEMINI_NATIVE.name(),
                "Gemini Stream Bearer",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                cryptoService.encrypt("gemini-stream-key"),
                cryptoService.mask("gemini-stream-key"),
                true,
                3000,
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "{\"authMode\":\"BEARER_TOKEN\"}"
        );
        jdbcTestHelper.insertRoute(
                "INTERVIEW_REPLY_GEMINI_STREAM",
                "INTERVIEW_TEXT",
                "INTERVIEW_REPLY",
                providerId,
                "fake-gemini-stream",
                1,
                AiExecutionMode.STREAM_SSE.name(),
                true,
                new BigDecimal("0.20"),
                null,
                null,
                null
        );

        StringBuilder deltaBuilder = new StringBuilder();
        AiGatewayService.InterviewReplyGatewayResult result = aiGatewayService.streamInterviewReply(
                "Backend Engineer",
                java.util.List.of("assistant: 先介绍一下项目背景。"),
                "我做了缓存和索引优化，把接口延迟降低了 30%。",
                "mock-economy-model",
                deltaBuilder::append
        );

        assertThat(capturedAuthorization.get()).isEqualTo("Bearer gemini-stream-key");
        JsonNode requestJson = new ObjectMapper().readTree(capturedBody.get());
        assertThat(requestJson.path("generationConfig").path("responseMimeType").asText()).isEqualTo("application/json");
        assertThat(requestJson.path("generationConfig").path("responseJsonSchema").path("properties").path("followUpQuestion").path("type").asText()).isEqualTo("string");
        assertThat(requestJson.path("generationConfig").path("responseJsonSchema").path("properties").path("shouldFinish").path("type").asText()).isEqualTo("boolean");
        assertThat(deltaBuilder.toString()).isEqualTo("请继续说明你的技术取舍。");
        assertThat(result.followUpQuestion()).isEqualTo("请继续说明你的技术取舍。");
        assertThat(result.gatewayResult().requestTokens()).isEqualTo(24);
        assertThat(result.gatewayResult().responseTokens()).isEqualTo(14);
    }

    @Test
    void routedMode_shouldTranscribeAudioWithGeminiInlineData() throws Exception {

        AtomicReference<String> capturedBody = new AtomicReference<>("");
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1beta/models/fake-gemini-stt:generateContent", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respondJson(exchange, 200, """
                    {
                      \"candidates\": [{\"content\": {\"parts\": [{\"text\": \"我负责订单中心性能治理，接口延迟降低了 30%。\"}]}}],
                      \"usageMetadata\": {\"promptTokenCount\": 12, \"candidatesTokenCount\": 8, \"totalTokenCount\": 20}
                    }
                    """);
        });
        server.start();

        long providerId = jdbcTestHelper.insertProvider(
                "GEMINI_STT",
                AiProviderType.GEMINI_NATIVE.name(),
                "Gemini STT",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta",
                cryptoService.encrypt("gemini-key"),
                cryptoService.mask("gemini-key"),
                true,
                3000,
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null
        );
        jdbcTestHelper.insertRoute(
                "STT_GEMINI_ROUTE",
                "STT",
                "INTERVIEW_VOICE_TRANSCRIBE",
                providerId,
                "fake-gemini-stt",
                1,
                true,
                new BigDecimal("0.10"),
                "你是语音转写助手。",
                "{\"transcriptionInstruction\":\"请转写音频内容，输出纯文本。\"}"
        );

        MockMultipartFile audioFile = new MockMultipartFile("audioFile", "answer.webm", "audio/webm", "fake audio".getBytes(StandardCharsets.UTF_8));
        AiGatewayService.AudioTranscriptionGatewayResult result = aiGatewayService.transcribeInterviewAnswer(audioFile, "mock-economy-model");

        assertThat(result.transcript()).contains("延迟降低了 30%");
        assertThat(result.gatewayResult().provider()).isEqualTo("GEMINI_STT");
        assertThat(result.gatewayResult().model()).isEqualTo("fake-gemini-stt");
        assertThat(capturedBody.get()).contains("audio/webm");
        assertThat(capturedBody.get()).contains("inlineData");
        assertThat(capturedBody.get()).doesNotContain("fileData");
        assertThat(capturedBody.get()).doesNotContain("fileUri");
        assertThat(capturedBody.get()).contains("请转写音频内容，输出纯文本");
        JsonNode requestJson = new ObjectMapper().readTree(capturedBody.get());
        assertThat(requestJson.path("generationConfig").has("responseJsonSchema")).isFalse();
        assertThat(requestJson.path("generationConfig").has("responseMimeType")).isFalse();
    }

    @Test
    void routedMode_shouldSynthesizeSpeechWithGeminiNativeTtsFormat() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>("");
        AtomicReference<String> capturedAuthorization = new AtomicReference<>("");
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1beta/models/fake-gemini-tts:generateContent", exchange -> {
            capturedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respondJson(exchange, 200, buildGeminiInlineAudioResponse(
                    "audio/L16;codec=pcm;rate=24000",
                    java.util.Base64.getEncoder().encodeToString("fake-pcm".getBytes(StandardCharsets.UTF_8)),
                    33,
                    243,
                    276
            ));
        });
        server.start();

        long providerId = jdbcTestHelper.insertProvider(
                "GEMINI_TTS",
                AiProviderType.GEMINI_NATIVE.name(),
                "Gemini TTS",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta",
                cryptoService.encrypt("gemini-tts-key"),
                cryptoService.mask("gemini-tts-key"),
                true,
                3000,
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null
        );
        jdbcTestHelper.insertRoute(
                "TTS_GEMINI_ROUTE",
                "TTS",
                "TEXT_TO_SPEECH",
                providerId,
                "fake-gemini-tts",
                1,
                true,
                BigDecimal.ZERO,
                null,
                "{\"defaultVoiceName\":\"Zephyr\",\"defaultStylePrompt\":\"Read aloud in a warm and friendly tone:\"}"
        );

        AiGatewayService.TextToSpeechGatewayResult result = aiGatewayService.synthesizeSpeech(
                "你好，这是 TTS 测试。",
                "Read aloud in a warm and friendly tone:",
                "Zephyr",
                "mock-economy-model"
        );

        JsonNode requestJson = new ObjectMapper().readTree(capturedBody.get());
        assertThat(capturedAuthorization.get()).isEqualTo("Bearer gemini-tts-key");
        assertThat(requestJson.path("contents").path(0).path("parts").path(0).path("text").asText())
                .contains("Read aloud in a warm and friendly tone:")
                .contains("你好，这是 TTS 测试。");
        assertThat(requestJson.path("generationConfig").path("responseModalities").path(0).asText()).isEqualTo("AUDIO");
        assertThat(requestJson.path("generationConfig").path("speechConfig").path("voiceConfig").path("prebuiltVoiceConfig").path("voiceName").asText())
                .isEqualTo("Zephyr");
        assertThat(result.gatewayResult().provider()).isEqualTo("GEMINI_TTS");
        assertThat(result.gatewayResult().model()).isEqualTo("fake-gemini-tts");
        assertThat(result.mimeType()).isEqualTo("audio/L16;codec=pcm;rate=24000");
        assertThat(result.sampleRate()).isEqualTo(24000);
        assertThat(result.audioBase64()).isNotBlank();
    }

    @Test
    void routedMode_shouldAcceptLargeGeminiTtsPayload() throws Exception {
        String largeAudioBase64 = java.util.Base64.getEncoder().encodeToString(new byte[320 * 1024]);
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1beta/models/fake-gemini-tts-large:generateContent", exchange -> respondJson(
                exchange,
                200,
                buildGeminiInlineAudioResponse(
                        "audio/L16;codec=pcm;rate=24000",
                        largeAudioBase64,
                        64,
                        512,
                        576
                )
        ));
        server.start();

        long providerId = jdbcTestHelper.insertProvider(
                "GEMINI_TTS_LARGE",
                AiProviderType.GEMINI_NATIVE.name(),
                "Gemini TTS Large",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta",
                cryptoService.encrypt("gemini-tts-large-key"),
                cryptoService.mask("gemini-tts-large-key"),
                true,
                3000,
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null
        );
        jdbcTestHelper.insertRoute(
                "TTS_GEMINI_ROUTE_LARGE",
                "TTS",
                "TEXT_TO_SPEECH",
                providerId,
                "fake-gemini-tts-large",
                1,
                true,
                BigDecimal.ZERO,
                null,
                "{\"defaultVoiceName\":\"Zephyr\",\"defaultStylePrompt\":\"Read aloud in a warm and friendly tone:\"}"
        );

        AiGatewayService.TextToSpeechGatewayResult result = aiGatewayService.synthesizeSpeech(
                "这是一段用于验证大体积 TTS 响应的测试文本。",
                "Read aloud in a warm and friendly tone:",
                "Zephyr",
                "mock-economy-model"
        );

        assertThat(result.audioBase64()).hasSize(largeAudioBase64.length());
        assertThat(result.sampleRate()).isEqualTo(24000);
        assertThat(result.gatewayResult().provider()).isEqualTo("GEMINI_TTS_LARGE");
    }

    @Test
    void routedMode_shouldRetryOnceOnOpenAiServerError() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            int current = attempts.incrementAndGet();
            if (current == 1) {
                respondJson(exchange, 500, "{\"error\":\"temporary\"}");
                return;
            }
            respondJson(exchange, 200, buildOpenAiResponse(Map.of(
                    "firstQuestion", "请介绍一个最能体现结果的项目。"
            ), 60, 20, 80));
        });
        server.start();

        long providerId = jdbcTestHelper.insertProvider(
                "OPENAI_RETRY",
                AiProviderType.OPENAI_COMPATIBLE.name(),
                "OpenAI Retry",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                cryptoService.encrypt("retry-key"),
                cryptoService.mask("retry-key"),
                true,
                3000,
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null
        );
        jdbcTestHelper.insertRoute("INTERVIEW_RETRY_ROUTE", "INTERVIEW_TEXT", "INTERVIEW_OPENING", providerId, "retry-model", 1, true, new BigDecimal("0.20"), null, null);

        AiGatewayService.InterviewQuestionGatewayResult result = aiGatewayService.createInterviewSession("Backend Engineer", "retry-model");

        assertThat(result.firstQuestion()).contains("项目");
        assertThat(attempts.get()).isEqualTo(2);
    }

    private String buildOpenAiResponse(Map<String, Object> payload, int promptTokens, int completionTokens, int totalTokens) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(Map.of(
                    "choices", java.util.List.of(Map.of("message", Map.of("content", objectMapper.writeValueAsString(payload)))),
                    "usage", Map.of(
                            "prompt_tokens", promptTokens,
                            "completion_tokens", completionTokens,
                            "total_tokens", totalTokens
                    )
            ));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String buildOpenAiRawContentResponse(String rawContent, int promptTokens, int completionTokens, int totalTokens) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(Map.of(
                    "choices", java.util.List.of(Map.of("message", Map.of("content", rawContent))),
                    "usage", Map.of(
                            "prompt_tokens", promptTokens,
                            "completion_tokens", completionTokens,
                            "total_tokens", totalTokens
                    )
            ));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String buildGeminiInlineAudioResponse(String mimeType, String audioBase64, int promptTokens, int completionTokens, int totalTokens) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(Map.of(
                    "candidates", java.util.List.of(Map.of(
                            "content", Map.of(
                                    "parts", java.util.List.of(Map.of(
                                            "inlineData", Map.of(
                                                    "mimeType", mimeType,
                                                    "data", audioBase64
                                            )
                                    ))
                            )
                    )),
                    "usageMetadata", Map.of(
                            "promptTokenCount", promptTokens,
                            "candidatesTokenCount", completionTokens,
                            "totalTokenCount", totalTokens
                    )
            ));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }


    private String buildGeminiTextResponse(String text, int promptTokens, int completionTokens, int totalTokens) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(Map.of(
                    "candidates", java.util.List.of(Map.of(
                            "content", Map.of(
                                    "parts", java.util.List.of(Map.of(
                                            "text", text
                                    ))
                            )
                    )),
                    "usageMetadata", Map.of(
                            "promptTokenCount", promptTokens,
                            "candidatesTokenCount", completionTokens,
                            "totalTokenCount", totalTokens
                    )
            ));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String buildOpenAiStreamResponse(String firstDeltaContent, String secondDeltaContent, int promptTokens, int completionTokens, int totalTokens) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String firstEvent = objectMapper.writeValueAsString(Map.of(
                    "choices", java.util.List.of(Map.of("delta", Map.of("content", firstDeltaContent)))
            ));
            String secondEvent = objectMapper.writeValueAsString(Map.of(
                    "choices", java.util.List.of(Map.of("delta", Map.of("content", secondDeltaContent))),
                    "usage", Map.of(
                            "prompt_tokens", promptTokens,
                            "completion_tokens", completionTokens,
                            "total_tokens", totalTokens
                    )
            ));
            return "data: " + firstEvent + "\n\n"
                    + "data: " + secondEvent + "\n\n"
                    + "data: [DONE]\n\n";
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String buildGeminiStreamResponse(String firstDeltaContent, String secondDeltaContent, int promptTokens, int completionTokens, int totalTokens) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String firstEvent = objectMapper.writeValueAsString(Map.of(
                    "candidates", java.util.List.of(Map.of(
                            "content", Map.of(
                                    "parts", java.util.List.of(Map.of("text", firstDeltaContent))
                            )
                    ))
            ));
            String secondEvent = objectMapper.writeValueAsString(Map.of(
                    "candidates", java.util.List.of(Map.of(
                            "content", Map.of(
                                    "parts", java.util.List.of(Map.of("text", secondDeltaContent))
                            )
                    )),
                    "usageMetadata", Map.of(
                            "promptTokenCount", promptTokens,
                            "candidatesTokenCount", completionTokens,
                            "totalTokenCount", totalTokens
                    )
            ));
            return "data: " + firstEvent + "\n\n"
                    + "data: " + secondEvent + "\n\n"
                    + "data: [DONE]\n\n";
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void respondJson(HttpExchange exchange, int statusCode, String body) throws IOException {

        exchange.getResponseHeaders().add("Content-Type", "application/json");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }


    private void respondSse(HttpExchange exchange, String body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }
}
