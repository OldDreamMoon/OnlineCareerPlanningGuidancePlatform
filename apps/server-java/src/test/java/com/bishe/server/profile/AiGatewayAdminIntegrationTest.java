package com.bishe.server.profile;

import com.bishe.server.ai.gateway.AiExecutionMode;
import com.bishe.server.ai.gateway.AiGatewayAdminListCacheService;
import com.bishe.server.ai.gateway.AiGatewayAdminService;
import com.bishe.server.ai.gateway.AiProviderInvocation;
import com.bishe.server.ai.gateway.AiRouteCacheService;
import com.bishe.server.ai.gateway.AiRouteResolver;
import com.bishe.server.ai.gateway.AiGatewayRuntimeSettingsCacheService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 网关后台管理接口测试：覆盖 provider/routing 存储与 API key 脱敏。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:bishe_ai_gateway_admin;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "ai.gateway.mode=ROUTED",
        "ai.gateway.allow-legacy-fallback=false",
        "ai.gateway.logging.debug-enabled=false",
        "ai.gateway.logging.ai-request-log-enabled=false",
        "ai.gateway.openai-compatible.base-url=https://newapi-proxy.example.com/v1",
        "ai.gateway.openai-compatible.api-key=test-bootstrap-key",
        "ai.gateway.openai-compatible.default-model=gemini-2.5-flash"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AiGatewayAdminIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AiRouteResolver aiRouteResolver;

    @Autowired
    private AiGatewayAdminService aiGatewayAdminService;

    @Autowired
    private AiGatewayAdminListCacheService aiGatewayAdminListCacheService;

    @Autowired
    private AiRouteCacheService aiRouteCacheService;

    @Autowired
    private AiGatewayRuntimeSettingsCacheService runtimeSettingsCacheService;

    @BeforeEach
    void setUp() {
        aiGatewayAdminListCacheService.evictAllNow();
        aiRouteCacheService.evictAllNow();
        runtimeSettingsCacheService.evictNow();
    }

    @Test
    void adminAiGateway_shouldCreateProviderAndMaskApiKey() throws Exception {
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");

        MvcResult createProviderResult = mockMvc.perform(post("/api/v1/admin/ai/providers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  \"providerCode\": \"gemini_main\",
                                  \"providerType\": \"GEMINI_NATIVE\",
                                  \"displayName\": \"Gemini 主路由\",
                                  \"baseUrl\": \"https://generativelanguage.googleapis.com/v1beta\",
                                  \"apiKey\": \"gemini-secret-key-123456\",
                                  \"enabled\": true,
                                  \"timeoutMs\": 20000,
                                  \"maxRetries\": 1,
                                  \"costPer1kInput\": \"0\",
                                  \"costPer1kOutput\": \"0\",
                                  \"extraConfigJson\": \"{\\\"region\\\":\\\"global\\\"}\"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.providerCode").value("GEMINI_MAIN"))
                .andExpect(jsonPath("$.data.providerType").value("GEMINI_NATIVE"))
                .andExpect(jsonPath("$.data.apiKeyMasked").value(org.hamcrest.Matchers.containsString("len=")))
                .andExpect(jsonPath("$.data.hasApiKey").value(true))
                .andReturn();

        JsonNode providerPayload = objectMapper.readTree(createProviderResult.getResponse().getContentAsString()).path("data");
        long providerId = providerPayload.path("id").asLong();
        assertThat(providerId).isPositive();

        String storedCiphertext = jdbcTemplate.queryForObject(
                "SELECT api_key_ciphertext FROM ai_provider_configs WHERE id = ?",
                String.class,
                providerId
        );
        assertThat(storedCiphertext).isNotBlank();
        assertThat(storedCiphertext).doesNotContain("gemini-secret-key-123456");

        mockMvc.perform(get("/api/v1/admin/ai/providers")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].providerCode").value("GEMINI_MAIN"))
                .andExpect(jsonPath("$.data.records[0].apiKeyMasked").value(org.hamcrest.Matchers.containsString("***")));

        mockMvc.perform(put("/api/v1/admin/ai/providers/{providerId}", providerId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  \"providerCode\": \"gemini_main\",
                                  \"providerType\": \"GEMINI_NATIVE\",
                                  \"displayName\": \"Gemini 音频主路由\",
                                  \"baseUrl\": \"https://generativelanguage.googleapis.com/v1beta\",
                                  \"enabled\": true,
                                  \"timeoutMs\": 25000,
                                  \"maxRetries\": 2,
                                  \"costPer1kInput\": \"0\",
                                  \"costPer1kOutput\": \"0\"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Gemini 音频主路由"))
                .andExpect(jsonPath("$.data.hasApiKey").value(true));
    }

    @Test
    void adminAiGateway_shouldCreateRouteForTaskType() throws Exception {
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");

        MvcResult providerResult = mockMvc.perform(post("/api/v1/admin/ai/providers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  \"providerCode\": \"openai_text\",
                                  \"providerType\": \"OPENAI_COMPATIBLE\",
                                  \"displayName\": \"OpenAI 文本\",
                                  \"baseUrl\": \"https://example.com/v1\",
                                  \"apiKey\": \"openai-secret-abc\",
                                  \"enabled\": true,
                                  \"timeoutMs\": 15000,
                                  \"maxRetries\": 1,
                                  \"costPer1kInput\": \"0\",
                                  \"costPer1kOutput\": \"0\",
                                  \"models\": [
                                    {
                                      \"modelCode\": \"gpt-4o-mini\",
                                      \"displayName\": \"GPT-4o mini\",
                                      \"enabled\": true,
                                      \"inputCostPer1k\": \"0\",
                                      \"outputCostPer1k\": \"0\"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long providerId = objectMapper.readTree(providerResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(post("/api/v1/admin/ai/routes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {
                                  \"routeCode\": \"interview_text_main\",
                                  \"taskType\": \"INTERVIEW_TEXT\",
                                  \"sceneCode\": \"INTERVIEW_REPLY\",
                                  \"providerConfigId\": %d,
                                  \"modelName\": \"gpt-4o-mini\",
                                  \"priorityNo\": 10,
                                  \"enabled\": true,
                                  \"temperature\": \"0.2\",
                                  \"systemPrompt\": \"你是面试教练。\",
                                  \"extraConfigJson\": \"{\\\"style\\\":\\\"coaching\\\"}\"
                                }
                                """).formatted(providerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routeCode").value("INTERVIEW_TEXT_MAIN"))
                .andExpect(jsonPath("$.data.providerCode").value("OPENAI_TEXT"))
                .andExpect(jsonPath("$.data.taskType").value("INTERVIEW_TEXT"))
                .andExpect(jsonPath("$.data.sceneCode").value("INTERVIEW_REPLY"));

        mockMvc.perform(get("/api/v1/admin/ai/routes")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[*].routeCode", org.hamcrest.Matchers.hasItem("INTERVIEW_TEXT_MAIN")))
                .andExpect(jsonPath("$.data.records[*].providerCode", org.hamcrest.Matchers.hasItem("OPENAI_TEXT")));

        mockMvc.perform(get("/api/v1/admin/ai/meta")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.providerTypes[0].code").value("OPENAI_COMPATIBLE"))
                .andExpect(jsonPath("$.data.taskTypes[?(@.code=='STT')]" ).exists());
    }

    @Test
    void adminAiGateway_shouldRejectRouteWhenProviderModelsMissing() throws Exception {
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");

        MvcResult providerResult = mockMvc.perform(post("/api/v1/admin/ai/providers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  \"providerCode\": \"openai_without_models\",
                                  \"providerType\": \"OPENAI_COMPATIBLE\",
                                  \"displayName\": \"OpenAI Empty Models\",
                                  \"baseUrl\": \"https://example.com/v1\",
                                  \"apiKey\": \"openai-secret-empty\",
                                  \"enabled\": true,
                                  \"timeoutMs\": 15000,
                                  \"maxRetries\": 1,
                                  \"costPer1kInput\": \"0\",
                                  \"costPer1kOutput\": \"0\"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long providerId = objectMapper.readTree(providerResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(post("/api/v1/admin/ai/routes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {
                                  \"routeCode\": \"interview_text_missing_models\",
                                  \"taskType\": \"INTERVIEW_TEXT\",
                                  \"sceneCode\": \"INTERVIEW_REPLY\",
                                  \"providerConfigId\": %d,
                                  \"modelName\": \"gpt-4o-mini\",
                                  \"priorityNo\": 10,
                                  \"enabled\": true,
                                  \"temperature\": \"0.2\"
                                }
                                """).formatted(providerId)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("provider models missing")));
    }

    @Test
    void adminAiGateway_shouldPreviewResolvedRoute() throws Exception {
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");

        MvcResult providerResult = mockMvc.perform(post("/api/v1/admin/ai/providers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "providerCode": "gemini_preview",
                                  "providerType": "GEMINI_NATIVE",
                                  "displayName": "Gemini Preview",
                                  "baseUrl": "https://newapi.example.com/v1",
                                  "apiKey": "preview-secret",
                                  "enabled": true,
                                  "timeoutMs": 16000,
                                  "maxRetries": 1,
                                  "costPer1kInput": "0",
                                  "costPer1kOutput": "0",
                                  "models": [
                                    {
                                      "modelCode": "gemini-2.5-flash",
                                      "displayName": "Gemini 2.5 Flash",
                                      "enabled": true,
                                      "inputCostPer1k": "0",
                                      "outputCostPer1k": "0"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long providerId = objectMapper.readTree(providerResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(post("/api/v1/admin/ai/routes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {
                                  "routeCode": "stt_preview_main",
                                  "taskType": "STT",
                                  "sceneCode": "INTERVIEW_VOICE_TRANSCRIBE",
                                  "providerConfigId": %d,
                                  "modelName": "gemini-2.5-flash",
                                  "priorityNo": 1,
                                  "enabled": true,
                                  "temperature": "0.1",
                                  "systemPrompt": "你是语音转写助手。",
                                  "extraConfigJson": "{\\"transcriptionInstruction\\":\\"请转写音频内容，输出纯文本。\\"}"
                                }
                                """).formatted(providerId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/ai/routes/resolve-preview")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskType": "STT",
                                  "sceneCode": "INTERVIEW_VOICE_TRANSCRIBE",
                                  "modelPreference": "mock-economy-model"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routeCode").value("STT_PREVIEW_MAIN"))
                .andExpect(jsonPath("$.data.providerCode").value("GEMINI_PREVIEW"))
                .andExpect(jsonPath("$.data.providerType").value("GEMINI_NATIVE"))
                .andExpect(jsonPath("$.data.model").value("gemini-2.5-flash"))
                .andExpect(jsonPath("$.data.baseUrl").value("https://newapi.example.com/v1"))
                .andExpect(jsonPath("$.data.reasoningEffort").value("OFF"))
                .andExpect(jsonPath("$.data.thinkingBudget").isEmpty())
                .andExpect(jsonPath("$.data.thinkingLevel").isEmpty())
                .andExpect(jsonPath("$.data.thinkingSource").value("SYSTEM_DEFAULT"));
    }

    @Test
    void adminAiGateway_shouldPersistPolicyThinkingConfigAndApplyToPreview() throws Exception {
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");

        MvcResult providerResult = mockMvc.perform(post("/api/v1/admin/ai/providers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "providerCode": "gemini_policy",
                                  "providerType": "GEMINI_NATIVE",
                                  "displayName": "Gemini Policy",
                                  "baseUrl": "https://policy.example.com/v1beta",
                                  "apiKey": "policy-secret",
                                  "enabled": true,
                                  "timeoutMs": 16000,
                                  "maxRetries": 1,
                                  "costPer1kInput": "0",
                                  "costPer1kOutput": "0",
                                  "models": [
                                    {
                                      "modelCode": "gemini-2.5-flash",
                                      "displayName": "Gemini 2.5 Flash",
                                      "enabled": true,
                                      "inputCostPer1k": "0",
                                      "outputCostPer1k": "0"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long providerId = objectMapper.readTree(providerResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        MvcResult policyResult = mockMvc.perform(post("/api/v1/admin/ai/route-policies")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "policyCode": "INTERVIEW_REPLY_PREMIUM_POLICY",
                                  "taskType": "INTERVIEW_TEXT",
                                  "sceneCode": "INTERVIEW_REPLY",
                                  "userTier": "PREMIUM",
                                  "strategyType": "SINGLE",
                                  "enabled": true,
                                  "reasoningEffort": "HIGH",
                                  "thinkingBudget": 2048,
                                  "thinkingLevel": "high",
                                  "notes": "VIP 场景高思考量"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reasoningEffort").value("HIGH"))
                .andExpect(jsonPath("$.data.thinkingBudget").value(2048))
                .andExpect(jsonPath("$.data.thinkingLevel").value("high"))
                .andReturn();
        long policyId = objectMapper.readTree(policyResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        String storedPolicyExtraConfig = jdbcTemplate.queryForObject(
                "SELECT extra_config_json FROM ai_scene_route_policies WHERE id = ?",
                String.class,
                policyId
        );
        assertThat(storedPolicyExtraConfig).contains("\"thinking\"");
        assertThat(storedPolicyExtraConfig).contains("\"reasoningEffort\":\"HIGH\"");
        assertThat(storedPolicyExtraConfig).contains("\"thinkingBudget\":2048");

        mockMvc.perform(put("/api/v1/admin/ai/route-policies/{policyId}", policyId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "policyCode": "INTERVIEW_REPLY_PREMIUM_POLICY",
                                  "taskType": "INTERVIEW_TEXT",
                                  "sceneCode": "INTERVIEW_REPLY",
                                  "userTier": "PREMIUM",
                                  "strategyType": "SINGLE",
                                  "enabled": true,
                                  "reasoningEffort": "MEDIUM",
                                  "thinkingBudget": 1024,
                                  "thinkingLevel": "standard",
                                  "notes": "VIP 场景平衡思考量"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reasoningEffort").value("MEDIUM"))
                .andExpect(jsonPath("$.data.thinkingBudget").value(1024))
                .andExpect(jsonPath("$.data.thinkingLevel").value("standard"));

        mockMvc.perform(post("/api/v1/admin/ai/routes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {
                                  "routeCode": "interview_reply_premium_main",
                                  "taskType": "INTERVIEW_TEXT",
                                  "sceneCode": "INTERVIEW_REPLY",
                                  "sceneRoutePolicyId": %d,
                                  "providerConfigId": %d,
                                  "modelName": "gemini-2.5-flash",
                                  "priorityNo": 1,
                                  "enabled": true,
                                  "temperature": "0.2"
                                }
                                """).formatted(policyId, providerId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/ai/routes/resolve-preview")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskType": "INTERVIEW_TEXT",
                                  "sceneCode": "INTERVIEW_REPLY",
                                  "userTier": "PREMIUM"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routeCode").value("INTERVIEW_REPLY_PREMIUM_MAIN"))
                .andExpect(jsonPath("$.data.reasoningEffort").value("MEDIUM"))
                .andExpect(jsonPath("$.data.thinkingBudget").value(1024))
                .andExpect(jsonPath("$.data.thinkingLevel").value("standard"))
                .andExpect(jsonPath("$.data.thinkingSource").value("ROUTE_POLICY_CONFIG"));
    }

    @Test
    void adminAiGatewayLists_shouldEvictCachedPayloadAfterConfigUpdates() {
        AiGatewayAdminService.ProviderItem provider = aiGatewayAdminService.createProvider(
                new AiGatewayAdminService.UpsertProviderCommand(
                        "openai_admin_cache",
                        "OPENAI_COMPATIBLE",
                        "OpenAI Cache V1",
                        "https://cache.example.com/v1",
                        "cache-secret-v1",
                        true,
                        15000,
                        1,
                        "0",
                        "0",
                        null,
                        List.of(
                                new AiGatewayAdminService.UpsertProviderModelCommand(
                                        "gpt-4o-mini",
                                        "GPT-4o mini",
                                        true,
                                        "0",
                                        "0",
                                        null,
                                        null,
                                        List.of("INTERVIEW_TEXT"),
                                        null
                                ),
                                new AiGatewayAdminService.UpsertProviderModelCommand(
                                        "gpt-4.1-mini",
                                        "GPT-4.1 mini",
                                        true,
                                        "0",
                                        "0",
                                        null,
                                        null,
                                        List.of("INTERVIEW_TEXT"),
                                        null
                                )
                        )
                )
        );

        AiGatewayAdminService.ProviderListPayload cachedProviders = aiGatewayAdminService.listProviders();
        assertThat(cachedProviders.records().stream()
                .filter(item -> item.id() == provider.id())
                .findFirst()
                .orElseThrow()
                .displayName()).isEqualTo("OpenAI Cache V1");

        aiGatewayAdminService.updateProvider(
                provider.id(),
                new AiGatewayAdminService.UpsertProviderCommand(
                        "openai_admin_cache",
                        "OPENAI_COMPATIBLE",
                        "OpenAI Cache V2",
                        "https://cache.example.com/v1",
                        null,
                        true,
                        18000,
                        2,
                        "0",
                        "0",
                        null,
                        null
                )
        );

        AiGatewayAdminService.ProviderListPayload refreshedProviders = aiGatewayAdminService.listProviders();
        assertThat(refreshedProviders.records().stream()
                .filter(item -> item.id() == provider.id())
                .findFirst()
                .orElseThrow()
                .displayName()).isEqualTo("OpenAI Cache V2");

        AiGatewayAdminService.PromptTemplateItem template = aiGatewayAdminService.createPromptTemplate(
                new AiGatewayAdminService.UpsertPromptTemplateCommand(
                        "INTERVIEW_TEXT",
                        "INTERVIEW_REPLY_CACHE_TEMPLATE",
                        1,
                        "ACTIVE",
                        "TEXT",
                        "提示词版本 v1",
                        "描述 v1",
                        "{\"version\":1}",
                        null
                )
        );

        AiGatewayAdminService.PromptTemplateListPayload cachedTemplates = aiGatewayAdminService.listPromptTemplates();
        assertThat(cachedTemplates.records().stream()
                .filter(item -> item.id() == template.id())
                .findFirst()
                .orElseThrow()
                .description()).isEqualTo("描述 v1");

        aiGatewayAdminService.updatePromptTemplate(
                template.id(),
                new AiGatewayAdminService.UpsertPromptTemplateCommand(
                        "INTERVIEW_TEXT",
                        "INTERVIEW_REPLY_CACHE_TEMPLATE",
                        1,
                        "ACTIVE",
                        "TEXT",
                        "提示词版本 v2",
                        "描述 v2",
                        "{\"version\":2}",
                        null
                )
        );

        AiGatewayAdminService.PromptTemplateListPayload refreshedTemplates = aiGatewayAdminService.listPromptTemplates();
        assertThat(refreshedTemplates.records().stream()
                .filter(item -> item.id() == template.id())
                .findFirst()
                .orElseThrow()
                .description()).isEqualTo("描述 v2");

        AiGatewayAdminService.RouteItem route = aiGatewayAdminService.createRoute(
                new AiGatewayAdminService.UpsertRouteCommand(
                        "interview_reply_cache_route",
                        "INTERVIEW_TEXT",
                        "INTERVIEW_REPLY",
                        null,
                        provider.id(),
                        "gpt-4o-mini",
                        1,
                        null,
                        AiExecutionMode.SYNC_BLOCKING.name(),
                        true,
                        "0.20",
                        null,
                        "INTERVIEW_REPLY_CACHE_TEMPLATE",
                        null
                )
        );

        AiGatewayAdminService.RouteListPayload cachedRoutes = aiGatewayAdminService.listRoutes();
        assertThat(cachedRoutes.records().stream()
                .filter(item -> item.id() == route.id())
                .findFirst()
                .orElseThrow()
                .modelName()).isEqualTo("gpt-4o-mini");

        aiGatewayAdminService.updateRoute(
                route.id(),
                new AiGatewayAdminService.UpsertRouteCommand(
                        "interview_reply_cache_route",
                        "INTERVIEW_TEXT",
                        "INTERVIEW_REPLY",
                        null,
                        provider.id(),
                        "gpt-4.1-mini",
                        1,
                        null,
                        AiExecutionMode.SYNC_BLOCKING.name(),
                        true,
                        "0.20",
                        null,
                        "INTERVIEW_REPLY_CACHE_TEMPLATE",
                        null
                )
        );

        AiGatewayAdminService.RouteListPayload refreshedRoutes = aiGatewayAdminService.listRoutes();
        assertThat(refreshedRoutes.records().stream()
                .filter(item -> item.id() == route.id())
                .findFirst()
                .orElseThrow()
                .modelName()).isEqualTo("gpt-4.1-mini");
    }

    @Test
    void adminAiGateway_shouldManagePromptTemplatesAndBindRoute() throws Exception {
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");

        mockMvc.perform(post("/api/v1/admin/ai/prompt-templates")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskType": "INTERVIEW_TEXT",
                                  "templateName": "INTERVIEW_REPLY_CUSTOM_CORE",
                                  "versionNo": 1,
                                  "status": "ACTIVE",
                                  "content": "你是结构化面试官，请结合候选人的回答继续深挖，并仅输出 JSON。",
                                  "description": "文本面试追问模板",
                                  "variablesJson": "{\\"notes\\":\\"v1\\"}"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskType").value("INTERVIEW_TEXT"))
                .andExpect(jsonPath("$.data.templateName").value("INTERVIEW_REPLY_CUSTOM_CORE"))
                .andExpect(jsonPath("$.data.versionNo").value(1))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/admin/ai/prompt-templates")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[*].templateName", org.hamcrest.Matchers.hasItem("INTERVIEW_REPLY_CUSTOM_CORE")));

        MvcResult providerResult = mockMvc.perform(post("/api/v1/admin/ai/providers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "providerCode": "openai_template_preview",
                                  "providerType": "OPENAI_COMPATIBLE",
                                  "displayName": "OpenAI Template Preview",
                                  "baseUrl": "https://openai.example.com/v1",
                                  "apiKey": "template-preview-secret",
                                  "enabled": true,
                                  "timeoutMs": 15000,
                                  "maxRetries": 1,
                                  "costPer1kInput": "0",
                                  "costPer1kOutput": "0",
                                  "models": [
                                    {
                                      "modelCode": "gpt-4o-mini",
                                      "displayName": "GPT-4o mini",
                                      "enabled": true,
                                      "inputCostPer1k": "0",
                                      "outputCostPer1k": "0"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long providerId = objectMapper.readTree(providerResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(post("/api/v1/admin/ai/routes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {
                                  "routeCode": "interview_reply_template",
                                  "taskType": "INTERVIEW_TEXT",
                                  "sceneCode": "INTERVIEW_REPLY",
                                  "providerConfigId": %d,
                                  "modelName": "gpt-4o-mini",
                                  "priorityNo": 1,
                                  "enabled": true,
                                  "temperature": "0.2",
                                  "promptTemplateName": "INTERVIEW_REPLY_CUSTOM_CORE",
                                  "extraConfigJson": "{\\"responseFormatJsonObject\\":true}"
                                }
                                """).formatted(providerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.promptTemplateName").value("INTERVIEW_REPLY_CUSTOM_CORE"))
                .andExpect(jsonPath("$.data.promptTemplateVersionNo").value(1));

        mockMvc.perform(post("/api/v1/admin/ai/routes/resolve-preview")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskType": "INTERVIEW_TEXT",
                                  "sceneCode": "INTERVIEW_REPLY"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routeCode").value("INTERVIEW_REPLY_TEMPLATE"))
                .andExpect(jsonPath("$.data.promptTemplateName").value("INTERVIEW_REPLY_CUSTOM_CORE"))
                .andExpect(jsonPath("$.data.promptTemplateVersionNo").value(1))
                .andExpect(jsonPath("$.data.systemPrompt").value(org.hamcrest.Matchers.containsString("结构化面试官")));
    }

    @Test
    void adminAiGateway_shouldPreviewPromptTemplateRenderAndResolveRuntimeVariables() throws Exception {
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");

        mockMvc.perform(post("/api/v1/admin/ai/prompt-templates/render-preview")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskType": "INTERVIEW_TEXT",
                                  "content": "你是 {{jobTitle}} 面试官，请结合 {{currentAnswer}} 继续深挖。",
                                  "variablesJson": "{\\"jobTitle\\":{\\"required\\":true,\\"sampleValue\\":\\"Backend Engineer\\"},\\"currentAnswer\\":{\\"required\\":true}}",
                                  "renderVariablesJson": "{\\"jobTitle\\":\\"Java Engineer\\",\\"currentAnswer\\":\\"我负责缓存治理，把接口延迟降低了 30%\\"}"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.renderedContent").value(org.hamcrest.Matchers.containsString("Java Engineer")))
                .andExpect(jsonPath("$.data.renderedContent").value(org.hamcrest.Matchers.containsString("接口延迟降低了 30%")))
                .andExpect(jsonPath("$.data.missingVariables.length()").value(0));

        MvcResult providerResult = mockMvc.perform(post("/api/v1/admin/ai/providers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "providerCode": "openai_render_preview",
                                  "providerType": "OPENAI_COMPATIBLE",
                                  "displayName": "OpenAI Render Preview",
                                  "baseUrl": "https://openai.example.com/v1",
                                  "apiKey": "render-preview-secret",
                                  "enabled": true,
                                  "timeoutMs": 15000,
                                  "maxRetries": 1,
                                  "costPer1kInput": "0",
                                  "costPer1kOutput": "0",
                                  "models": [
                                    {
                                      "modelCode": "gpt-4o-mini",
                                      "displayName": "GPT-4o mini",
                                      "enabled": true,
                                      "inputCostPer1k": "0",
                                      "outputCostPer1k": "0"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long providerId = objectMapper.readTree(providerResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(post("/api/v1/admin/ai/prompt-templates")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskType": "INTERVIEW_TEXT",
                                  "templateName": "INTERVIEW_REPLY_RENDER",
                                  "versionNo": 1,
                                  "status": "ACTIVE",
                                  "content": "你是 {{targetRole}} 面试官，请根据候选人的回答 {{currentAnswer}} 深挖技术细节，并仅输出 JSON。",
                                  "description": "带变量的追问模板",
                                  "variablesJson": "{\\"targetRole\\":{\\"required\\":true},\\"currentAnswer\\":{\\"required\\":true}}"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/ai/routes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {
                                  "routeCode": "interview_reply_render",
                                  "taskType": "INTERVIEW_TEXT",
                                  "sceneCode": "INTERVIEW_REPLY",
                                  "providerConfigId": %d,
                                  "modelName": "gpt-4o-mini",
                                  "priorityNo": 1,
                                  "enabled": true,
                                  "temperature": "0.2",
                                  "promptTemplateName": "INTERVIEW_REPLY_RENDER",
                                  "extraConfigJson": "{\\"responseFormatJsonObject\\":true}"
                                }
                                """).formatted(providerId)))
                .andExpect(status().isOk());

        AiProviderInvocation invocation = aiRouteResolver.resolve(
                "INTERVIEW_TEXT",
                "INTERVIEW_REPLY",
                null,
                java.util.Map.of(
                        "targetRole", "Backend Engineer",
                        "currentAnswer", "我负责缓存治理，把接口延迟降低了 30%",
                        "historyLines", "ASSISTANT: 请先介绍一个最有代表性的项目\nUSER: 我负责缓存治理",
                        "userAnswerCount", 1
                )
        );

        assertThat(invocation.promptTemplateName()).isEqualTo("INTERVIEW_REPLY_RENDER");
        assertThat(invocation.promptTemplateVersionNo()).isEqualTo(1);
        assertThat(invocation.systemPrompt()).contains("Backend Engineer");
        assertThat(invocation.systemPrompt()).contains("接口延迟降低了 30%");
    }

    @Test
    void adminAiGateway_shouldPublishAndRollbackPromptTemplateVersions() throws Exception {
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");

        MvcResult createV1Result = mockMvc.perform(post("/api/v1/admin/ai/prompt-templates")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskType": "INTERVIEW_TEXT",
                                  "templateName": "INTERVIEW_REPLY_FLOW",
                                  "versionNo": 1,
                                  "status": "ACTIVE",
                                  "content": "你是结构化面试官 v1。",
                                  "description": "v1",
                                  "variablesJson": "{\\"notes\\":\\"v1\\"}"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long templateIdV1 = objectMapper.readTree(createV1Result.getResponse().getContentAsString()).path("data").path("id").asLong();

        MvcResult createV2Result = mockMvc.perform(post("/api/v1/admin/ai/prompt-templates")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskType": "INTERVIEW_TEXT",
                                  "templateName": "INTERVIEW_REPLY_FLOW",
                                  "versionNo": 2,
                                  "status": "DRAFT",
                                  "content": "你是结构化面试官 v2。",
                                  "description": "v2",
                                  "variablesJson": "{\\"notes\\":\\"v2\\"}"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long templateIdV2 = objectMapper.readTree(createV2Result.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(post("/api/v1/admin/ai/prompt-templates/{templateId}/publish", templateIdV2)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(templateIdV2))
                .andExpect(jsonPath("$.data.versionNo").value(2))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM prompt_templates WHERE id = ?", String.class, templateIdV1)).isEqualTo("INACTIVE");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM prompt_templates WHERE id = ?", String.class, templateIdV2)).isEqualTo("ACTIVE");

        mockMvc.perform(post("/api/v1/admin/ai/prompt-templates/{templateId}/rollback", templateIdV2)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {
                                  "targetTemplateId": %d
                                }
                                """).formatted(templateIdV1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(templateIdV1))
                .andExpect(jsonPath("$.data.versionNo").value(1))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM prompt_templates WHERE id = ?", String.class, templateIdV1)).isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM prompt_templates WHERE id = ?", String.class, templateIdV2)).isEqualTo("INACTIVE");
    }

    @Test
    void adminAiGateway_shouldListAndUpdateQuotaPolicies() throws Exception {
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");
        Long policyId = jdbcTemplate.queryForObject(
                "SELECT id FROM ai_quota_policies WHERE tier = ? AND task_type = ? AND scene_code IS NULL",
                Long.class,
                "FREE",
                "INTERVIEW_TEXT"
        );
        assertThat(policyId).isNotNull();

        mockMvc.perform(get("/api/v1/admin/ai/quota-policies")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").exists())
                .andExpect(jsonPath("$.data.records[0].maxInputTokens").exists())
                .andExpect(jsonPath("$.data.records[*].sceneCode", org.hamcrest.Matchers.hasItem("COMMUNITY_PRE_ANSWER")))
                .andExpect(jsonPath("$.data.records[*].sceneCode", org.hamcrest.Matchers.hasItem("MENTOR_PREP_SHEET_GENERATE")));

        mockMvc.perform(put("/api/v1/admin/ai/quota-policies/{policyId}", policyId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dailyFreeLimit": 8,
                                  "pointsPerCall": 12,
                                  "dailyMaxLimit": 40,
                                  "modelPreference": "gpt-4o-mini",
                                  "maxInputTokens": 8192
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(policyId))
                .andExpect(jsonPath("$.data.tier").value("FREE"))
                .andExpect(jsonPath("$.data.taskType").value("INTERVIEW_TEXT"))
                .andExpect(jsonPath("$.data.dailyFreeLimit").value(8))
                .andExpect(jsonPath("$.data.pointsPerCall").value(12))
                .andExpect(jsonPath("$.data.dailyMaxLimit").value(40))
                .andExpect(jsonPath("$.data.modelPreference").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.data.maxInputTokens").value(8192));

        assertThat(jdbcTemplate.queryForObject("SELECT daily_free_limit FROM ai_quota_policies WHERE id = ?", Integer.class, policyId)).isEqualTo(8);
        assertThat(jdbcTemplate.queryForObject("SELECT points_per_call FROM ai_quota_policies WHERE id = ?", Integer.class, policyId)).isEqualTo(12);
        assertThat(jdbcTemplate.queryForObject("SELECT daily_max_limit FROM ai_quota_policies WHERE id = ?", Integer.class, policyId)).isEqualTo(40);
        assertThat(jdbcTemplate.queryForObject("SELECT model_preference FROM ai_quota_policies WHERE id = ?", String.class, policyId)).isEqualTo("gpt-4o-mini");
        assertThat(jdbcTemplate.queryForObject("SELECT max_input_tokens FROM ai_quota_policies WHERE id = ?", Integer.class, policyId)).isEqualTo(8192);
    }

    @Test
    void adminAiGateway_shouldUpdateSceneQuotaPolicyWithoutTouchingSiblingScene() throws Exception {
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");
        Long communityPreAnswerPolicyId = jdbcTemplate.queryForObject(
                "SELECT id FROM ai_quota_policies WHERE tier = ? AND task_type = ? AND scene_code = ?",
                Long.class,
                "FREE",
                "COMMUNITY_REPLY",
                "COMMUNITY_PRE_ANSWER"
        );
        Long mentorPrepPolicyId = jdbcTemplate.queryForObject(
                "SELECT id FROM ai_quota_policies WHERE tier = ? AND task_type = ? AND scene_code = ?",
                Long.class,
                "FREE",
                "COMMUNITY_REPLY",
                "MENTOR_PREP_SHEET_GENERATE"
        );
        assertThat(communityPreAnswerPolicyId).isNotNull();
        assertThat(mentorPrepPolicyId).isNotNull();

        Integer mentorPrepDailyFreeLimitBefore = jdbcTemplate.queryForObject(
                "SELECT daily_free_limit FROM ai_quota_policies WHERE id = ?",
                Integer.class,
                mentorPrepPolicyId
        );
        assertThat(mentorPrepDailyFreeLimitBefore).isNotNull();

        mockMvc.perform(put("/api/v1/admin/ai/quota-policies/{policyId}", communityPreAnswerPolicyId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dailyFreeLimit": 6,
                                  "pointsPerCall": 4,
                                  "dailyMaxLimit": 18,
                                  "modelPreference": "gemini-2.5-flash",
                                  "maxInputTokens": 6000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(communityPreAnswerPolicyId))
                .andExpect(jsonPath("$.data.taskType").value("COMMUNITY_REPLY"))
                .andExpect(jsonPath("$.data.sceneCode").value("COMMUNITY_PRE_ANSWER"))
                .andExpect(jsonPath("$.data.dailyFreeLimit").value(6))
                .andExpect(jsonPath("$.data.pointsPerCall").value(4))
                .andExpect(jsonPath("$.data.dailyMaxLimit").value(18))
                .andExpect(jsonPath("$.data.maxInputTokens").value(6000));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT daily_free_limit FROM ai_quota_policies WHERE id = ?",
                Integer.class,
                communityPreAnswerPolicyId
        )).isEqualTo(6);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT daily_free_limit FROM ai_quota_policies WHERE id = ?",
                Integer.class,
                mentorPrepPolicyId
        )).isEqualTo(mentorPrepDailyFreeLimitBefore);
    }

    @Test
    void adminAiGateway_shouldGetAndUpdateRuntimeSettings() throws Exception {
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");
        Long adminUserId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, "admin@test.local");
        assertThat(adminUserId).isNotNull();

        mockMvc.perform(get("/api/v1/admin/ai/runtime-settings")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.debugModeEnabled").value(false))
                .andExpect(jsonPath("$.data.aiRequestLogEnabled").value(false))
                .andExpect(jsonPath("$.data.defaultDebugModeEnabled").value(false))
                .andExpect(jsonPath("$.data.defaultAiRequestLogEnabled").value(false))
                .andExpect(jsonPath("$.data.defaultReasoningEffort").doesNotExist())
                .andExpect(jsonPath("$.data.defaultThinkingBudget").doesNotExist())
                .andExpect(jsonPath("$.data.defaultThinkingLevel").doesNotExist());

        mockMvc.perform(put("/api/v1/admin/ai/runtime-settings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "debugModeEnabled": true,
                                  "aiRequestLogEnabled": true,
                                  "defaultReasoningEffort": "HIGH",
                                  "defaultThinkingBudget": 2048,
                                  "defaultThinkingLevel": "standard"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.debugModeEnabled").value(true))
                .andExpect(jsonPath("$.data.aiRequestLogEnabled").value(true))
                .andExpect(jsonPath("$.data.defaultReasoningEffort").value("HIGH"))
                .andExpect(jsonPath("$.data.defaultThinkingBudget").value(2048))
                .andExpect(jsonPath("$.data.defaultThinkingLevel").value("standard"))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT setting_value FROM ai_gateway_runtime_settings WHERE setting_key = ?",
                String.class,
                "DEBUG_MODE_ENABLED"
        )).isEqualTo("true");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT setting_value FROM ai_gateway_runtime_settings WHERE setting_key = ?",
                String.class,
                "AI_REQUEST_LOG_ENABLED"
        )).isEqualTo("true");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT setting_value FROM ai_gateway_runtime_settings WHERE setting_key = ?",
                String.class,
                "DEFAULT_REASONING_EFFORT"
        )).isEqualTo("HIGH");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT setting_value FROM ai_gateway_runtime_settings WHERE setting_key = ?",
                String.class,
                "DEFAULT_THINKING_BUDGET"
        )).isEqualTo("2048");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT setting_value FROM ai_gateway_runtime_settings WHERE setting_key = ?",
                String.class,
                "DEFAULT_THINKING_LEVEL"
        )).isEqualTo("standard");
    }

    @Test
    void adminAiGateway_shouldReturnLogsAndCostDashboard() throws Exception {
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");
        Long adminUserId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, "admin@test.local");
        assertThat(adminUserId).isNotNull();
        Timestamp now = Timestamp.from(Instant.now());

        jdbcTemplate.update(
                """
                INSERT INTO ai_provider_configs(
                    provider_code, provider_type, display_name, base_url, api_key_ciphertext, api_key_masked,
                    enabled, timeout_ms, max_retries, cost_per_1k_input, cost_per_1k_output, extra_config_json, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "OPENAI_DEMO_MAIN",
                "OPENAI_COMPATIBLE",
                "OpenAI 演示主路由",
                "https://example.com/v1",
                "ciphertext",
                "***masked***",
                true,
                15000,
                1,
                new java.math.BigDecimal("0.682800"),
                new java.math.BigDecimal("2.048400"),
                null,
                now,
                now
        );
        Long providerId = jdbcTemplate.queryForObject("SELECT id FROM ai_provider_configs WHERE provider_code = ?", Long.class, "OPENAI_DEMO_MAIN");
        assertThat(providerId).isNotNull();

        jdbcTemplate.update(
                """
                INSERT INTO prompt_templates(
                    task_type, template_name, version_no, status, template_format, content,
                    description, variables_json, bundle_json, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "INTERVIEW_TEXT",
                "INTERVIEW_REPLY_CORE",
                1,
                "ACTIVE",
                "TEXT",
                "Reply template",
                "日志详情模板",
                "{\"resume\":\"string\"}",
                null,
                now,
                now
        );

        jdbcTemplate.update(
                """
                INSERT INTO ai_model_routes(
                    route_code, task_type, scene_code, provider_config_id, model_name, priority_no,
                    execution_mode, enabled, temperature, system_prompt, prompt_template_name, extra_config_json,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "interview_reply_log_main",
                "INTERVIEW_TEXT",
                "INTERVIEW_REPLY",
                providerId,
                "gpt-4o-mini",
                10,
                AiExecutionMode.SYNC_BLOCKING.name(),
                true,
                new java.math.BigDecimal("0.20"),
                "system prompt",
                "INTERVIEW_REPLY_CORE",
                "{\"thinking\":{\"reasoningEffort\":\"LOW\"}}",
                now,
                now
        );

        jdbcTemplate.update(
                """
                INSERT INTO ai_call_logs(
                    trace_id, user_id, task_type, scene_code, provider, model, latency_ms, status, error_code,
                    request_tokens, response_tokens, total_tokens, estimated_cost, charged_points,
                    quota_weight, result_summary, result_payload_json, user_tier, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "trc_admin_ai_001",
                adminUserId,
                "INTERVIEW_TEXT",
                "INTERVIEW_REPLY",
                "OPENAI_DEMO_MAIN",
                "gpt-4o-mini",
                1800L,
                "SUCCESS",
                null,
                120,
                60,
                180,
                new java.math.BigDecimal("0.819360"),
                5,
                1,
                "文本面试追问成功",
                "{\"ok\":true}",
                "FREE",
                now
        );
        jdbcTemplate.update(
                """
                INSERT INTO ai_call_logs(
                    trace_id, user_id, task_type, scene_code, provider, model, latency_ms, status, error_code,
                    request_tokens, response_tokens, total_tokens, estimated_cost, charged_points,
                    quota_weight, result_summary, result_payload_json, user_tier, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "trc_admin_ai_002",
                adminUserId,
                "STT",
                "INTERVIEW_VOICE_TRANSCRIBE",
                "GEMINI_NATIVE_CANDIDATE",
                "gemini-2.5-flash",
                2200L,
                "SUCCESS",
                null,
                40,
                20,
                60,
                new java.math.BigDecimal("0.204840"),
                0,
                1,
                "语音转写成功",
                "{\"transcript\":\"你好\"}",
                "FREE",
                now
        );

        mockMvc.perform(get("/api/v1/admin/ai/logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("provider", "OPENAI_DEMO_MAIN")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].traceId").value("trc_admin_ai_001"))
                .andExpect(jsonPath("$.data.records[0].sceneCode").value("INTERVIEW_REPLY"))
                .andExpect(jsonPath("$.data.records[0].userEmail").value("admin@test.local"))
                .andExpect(jsonPath("$.data.records[0].estimatedCost").value("0.819360"));

        Long logId = jdbcTemplate.queryForObject("SELECT id FROM ai_call_logs WHERE trace_id = ?", Long.class, "trc_admin_ai_001");
        assertThat(logId).isNotNull();

        mockMvc.perform(get("/api/v1/admin/ai/logs/{logId}", logId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sceneCode").value("INTERVIEW_REPLY"))
                .andExpect(jsonPath("$.data.currentRouteSnapshot.routeCode").value("INTERVIEW_REPLY_LOG_MAIN"))
                .andExpect(jsonPath("$.data.currentRouteSnapshot.promptTemplateName").value("INTERVIEW_REPLY_CORE"))
                .andExpect(jsonPath("$.data.currentRouteSnapshot.thinkingSource").value("ROUTE_EXTRA_CONFIG"));

        mockMvc.perform(get("/api/v1/admin/ai/cost-dashboard")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("period", "today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCalls").value(2))
                .andExpect(jsonPath("$.data.totalCost").value("1.024200"))
                .andExpect(jsonPath("$.data.byProvider[0].name").exists())
                .andExpect(jsonPath("$.data.byTier[0].name").value("FREE"));

        mockMvc.perform(get("/api/v1/admin/ai/cost-dashboard/export")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("period", "today"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment;")))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("section,name,calls,cost_cny,extra")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("OVERVIEW")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("TOP_USER")));
    }

    @Test
    void adminAiGateway_shouldReturnApplicationOpsOverview() throws Exception {
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");
        Long adminUserId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, "admin@test.local");
        assertThat(adminUserId).isNotNull();
        Timestamp now = Timestamp.from(Instant.now());

        jdbcTemplate.update(
                """
                INSERT INTO ai_provider_configs(
                    provider_code, provider_type, display_name, base_url, api_key_ciphertext, api_key_masked,
                    enabled, timeout_ms, max_retries, cost_per_1k_input, cost_per_1k_output, extra_config_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "OPS_MAIN_PROVIDER",
                "OPENAI_COMPATIBLE",
                "运营主路由",
                "https://example.com/v1",
                "ciphertext",
                "***masked***",
                true,
                15000,
                1,
                new java.math.BigDecimal("0.682800"),
                new java.math.BigDecimal("2.048400"),
                null
        );
        Long providerId = jdbcTemplate.queryForObject(
                "SELECT id FROM ai_provider_configs WHERE provider_code = ?",
                Long.class,
                "OPS_MAIN_PROVIDER"
        );
        assertThat(providerId).isNotNull();

        jdbcTemplate.update(
                """
                INSERT INTO prompt_templates(
                    task_type, template_name, version_no, status, template_format, content,
                    description, variables_json, bundle_json, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "INTERVIEW_TEXT",
                "INTERVIEW_REPLY_PROMPT",
                1,
                "ACTIVE",
                "TEXT",
                "Reply prompt",
                "运营台测试模板",
                "{\"resume\":\"string\"}",
                null,
                now,
                now
        );

        jdbcTemplate.update(
                """
                INSERT INTO ai_model_routes(
                    route_code, task_type, scene_code, provider_config_id, model_name, priority_no,
                    execution_mode, enabled, temperature, system_prompt, prompt_template_name, extra_config_json,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "interview_reply_main",
                "INTERVIEW_TEXT",
                "INTERVIEW_REPLY",
                providerId,
                "gpt-4o-mini",
                10,
                AiExecutionMode.SYNC_BLOCKING.name(),
                true,
                new java.math.BigDecimal("0.20"),
                "system prompt",
                "INTERVIEW_REPLY_PROMPT",
                null,
                now,
                now
        );
        jdbcTemplate.update(
                """
                INSERT INTO ai_model_routes(
                    route_code, task_type, scene_code, provider_config_id, model_name, priority_no,
                    execution_mode, enabled, temperature, system_prompt, prompt_template_name, extra_config_json,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "interview_opening_ready",
                "INTERVIEW_TEXT",
                "INTERVIEW_OPENING",
                providerId,
                "gpt-4o-mini",
                20,
                AiExecutionMode.SYNC_BLOCKING.name(),
                true,
                new java.math.BigDecimal("0.20"),
                "opening prompt",
                null,
                null,
                now,
                now
        );

        jdbcTemplate.update(
                """
                INSERT INTO ai_call_logs(
                    trace_id, user_id, task_type, scene_code, provider, model, latency_ms, status, error_code,
                    request_tokens, response_tokens, total_tokens, estimated_cost, charged_points, quota_weight,
                    result_summary, result_payload_json, user_tier, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "trc_ops_scene_001",
                adminUserId,
                "INTERVIEW_TEXT",
                "INTERVIEW_REPLY",
                "OPS_MAIN_PROVIDER",
                "gpt-4o-mini",
                1900L,
                "SUCCESS",
                null,
                120,
                60,
                180,
                new java.math.BigDecimal("0.819360"),
                5,
                1,
                "面试追问成功",
                "{\"ok\":true}",
                "FREE",
                now
        );
        jdbcTemplate.update(
                """
                INSERT INTO ai_call_logs(
                    trace_id, user_id, task_type, scene_code, provider, model, latency_ms, status, error_code,
                    request_tokens, response_tokens, total_tokens, estimated_cost, charged_points, quota_weight,
                    result_summary, result_payload_json, user_tier, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "trc_ops_scene_002",
                adminUserId,
                "INTERVIEW_TEXT",
                "INTERVIEW_REPLY",
                "OPS_MAIN_PROVIDER",
                "gpt-4o-mini",
                2100L,
                "SUCCESS",
                null,
                110,
                55,
                165,
                new java.math.BigDecimal("0.751080"),
                5,
                1,
                "面试追问成功",
                "{\"ok\":true}",
                "FREE",
                now
        );

        MvcResult result = mockMvc.perform(get("/api/v1/admin/ai/application-ops")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days").value(7))
                .andExpect(jsonPath("$.data.summary.totalChannels").value(11))
                .andExpect(jsonPath("$.data.summary.readyChannels").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.summary.activeChannels").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.summary.dormantChannels").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.summary.totalCalls").value(2))
                .andExpect(jsonPath("$.data.summary.totalCost").value("1.570440"))
                .andExpect(jsonPath("$.data.hotScenes[*].sceneCode", org.hamcrest.Matchers.hasItem("INTERVIEW_REPLY")))
                .andExpect(jsonPath("$.data.costScenes[*].sceneCode", org.hamcrest.Matchers.hasItem("INTERVIEW_REPLY")))
                .andExpect(jsonPath("$.data.records.length()").value(11))
                .andExpect(jsonPath("$.data.records[*].sceneCode", org.hamcrest.Matchers.hasItem("STUDENT_PORTRAIT_SUMMARY")))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        JsonNode recordsNode = data.path("records");
        JsonNode openingRecord = null;
        for (JsonNode recordNode : recordsNode) {
            if ("INTERVIEW_OPENING".equals(recordNode.path("sceneCode").asText())) {
                openingRecord = recordNode;
                break;
            }
        }
        assertThat(openingRecord).isNotNull();
        JsonNode verifiedOpeningRecord = Objects.requireNonNull(openingRecord);
        assertThat(verifiedOpeningRecord.path("status").asText()).isEqualTo("DORMANT");
    }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  \"email\": \"%s\",
                                  \"password\": \"%s\"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }
}
