package com.bishe.server.profile;

import com.bishe.server.consult.PaymentMode;
import com.bishe.server.featureflag.FeatureFlagCacheService;
import com.bishe.server.featureflag.FeatureFlagService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 功能开关中心集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeatureFlagIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FeatureFlagCacheService featureFlagCacheService;

    @Autowired
    private FeatureFlagService featureFlagService;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM feature_flags");
        featureFlagCacheService.evictNow();
    }

    @Test
    void adminShouldListAndUpdateFeatureFlags() throws Exception {
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");

        mockMvc.perform(get("/api/v1/admin/feature-flags")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.records[?(@.key=='payment.enabled')].currentValue").value(org.hamcrest.Matchers.contains("true")))
                        .andExpect(jsonPath("$.data.records[?(@.key=='payment.mode')].currentValue").value(org.hamcrest.Matchers.contains("MOCK")))
                        .andExpect(jsonPath("$.data.records[?(@.key=='voice.interview.enabled')].currentValue").value(org.hamcrest.Matchers.contains("true")))
                        .andExpect(jsonPath("$.data.records[?(@.key=='voice.stt.enabled')].currentValue").value(org.hamcrest.Matchers.contains("true")))
                        .andExpect(jsonPath("$.data.records[?(@.key=='voice.tts.enabled')].currentValue").value(org.hamcrest.Matchers.contains("true")))
                        .andExpect(jsonPath("$.data.records[?(@.key=='community.ai-draft.enabled')].currentValue").value(org.hamcrest.Matchers.contains("true")))
                        .andExpect(jsonPath("$.data.records[?(@.key=='community.ai-first-reply.enabled')].currentValue").value(org.hamcrest.Matchers.contains("true")))
                        .andExpect(jsonPath("$.data.records[?(@.key=='student.portrait.async-refresh.enabled')].currentValue").value(org.hamcrest.Matchers.contains("true")))
                        .andExpect(jsonPath("$.data.records[?(@.key=='student.portrait.summary.mode')].currentValue").value(org.hamcrest.Matchers.contains("TEMPLATE")));

        upsertFeatureFlag("payment.mode", "SANDBOX", 1L);

        mockMvc.perform(get("/api/v1/admin/feature-flags")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.key=='payment.mode')].currentValue").value(org.hamcrest.Matchers.contains("MOCK")));

        mockMvc.perform(post("/api/v1/admin/feature-flags")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "payment.mode",
                                  "value": "SANDBOX"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("feature flag updated"))
                .andExpect(jsonPath("$.data.key").value("payment.mode"))
                .andExpect(jsonPath("$.data.currentValue").value("SANDBOX"))
                .andExpect(jsonPath("$.data.overridden").value(true));

        mockMvc.perform(get("/api/v1/admin/feature-flags")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.key=='payment.mode')].currentValue").value(org.hamcrest.Matchers.contains("SANDBOX")));
    }

    @Test
    void paymentModeFlagShouldOverrideDefaultPaymentMode() throws Exception {
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");
        assertThat(featureFlagService.resolvePaymentMode()).isEqualTo(PaymentMode.MOCK);

        updateFeatureFlag(adminToken, "payment.mode", "SANDBOX");

        assertThat(featureFlagService.resolvePaymentMode()).isEqualTo(PaymentMode.SANDBOX);
    }

    @Test
    void studentPortraitFlagsShouldExposeDefaults() {
        assertThat(featureFlagService.isStudentPortraitAsyncRefreshEnabled()).isTrue();
        assertThat(featureFlagService.getStudentPortraitSummaryMode()).isEqualTo(FeatureFlagService.StudentPortraitSummaryMode.TEMPLATE);
    }

    @Test
    void studentShouldReadInterviewEntryOptionsFromFeatureFlags() throws Exception {
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");
        registerUser("STUDENT", "feature-interview-entry@example.com", "Passw0rd!", "FeatureInterviewEntry");
        String studentToken = loginAndGetAccessToken("feature-interview-entry@example.com", "Passw0rd!");

        mockMvc.perform(get("/api/v1/ai/interview/entry-options")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.voiceAnswerEnabled").value(true))
                .andExpect(jsonPath("$.data.liveInterviewEnabled").value(true));

        updateFeatureFlag(adminToken, "voice.interview.enabled", "false");

        mockMvc.perform(get("/api/v1/ai/interview/entry-options")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.voiceAnswerEnabled").value(false))
                .andExpect(jsonPath("$.data.liveInterviewEnabled").value(false));
    }

    @Test
    void paymentAndSplitVoiceFlagsShouldBlockRelatedEndpoints() throws Exception {
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");
        long mentorUserId = registerUser("MENTOR", "feature-ai-mentor@example.com", "Passw0rd!", "FeatureAiMentor");
        registerUser("STUDENT", "feature-ai-student@example.com", "Passw0rd!", "FeatureAiStudent");
        String mentorToken = loginAndGetAccessToken("feature-ai-mentor@example.com", "Passw0rd!");
        String studentToken = loginAndGetAccessToken("feature-ai-student@example.com", "Passw0rd!");

        assertThat(featureFlagService.isPaymentEnabled()).isTrue();
        assertThat(featureFlagService.isVoiceInterviewEnabled()).isTrue();
        assertThat(featureFlagService.isVoiceSttEnabled()).isTrue();
        assertThat(featureFlagService.isVoiceTtsEnabled()).isTrue();
        assertThat(featureFlagService.isCommunityAiDraftEnabled()).isTrue();
        assertThat(featureFlagService.isCommunityAiFirstReplyEnabled()).isTrue();

        MvcResult createOrderResult = mockMvc.perform(post("/api/v1/consult/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {
                                  "mentorUserId": %d,
                                  "questionText": "我想先创建一笔订单，再验证支付入口开关。"
                                }
                                """).formatted(mentorUserId)))
                .andExpect(status().isOk())
                .andReturn();
        String orderNo = objectMapper.readTree(createOrderResult.getResponse().getContentAsString()).path("data").path("orderNo").asText();

        updateFeatureFlag(adminToken, "payment.enabled", "false");
        updateFeatureFlag(adminToken, "voice.interview.enabled", "false");
        updateFeatureFlag(adminToken, "voice.tts.enabled", "false");
        updateFeatureFlag(adminToken, "community.ai-draft.enabled", "false");

        mockMvc.perform(post("/api/v1/pay/orders/{orderNo}/create", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("支付入口暂未开放，请稍后再试。"));

        mockMvc.perform(post("/api/v1/consult/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {
                                  "mentorUserId": %d,
                                  "questionText": "我想确认支付入口关闭后是否还能继续下单。"
                                }
                                """).formatted(mentorUserId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("支付入口暂未开放，请稍后再试。"));

        mockMvc.perform(post("/api/v1/ai/interview/sessions")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetRole": "Backend Engineer",
                                  "mode": "INTERVIEW_TEXT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("INTERVIEW_TEXT"));

        mockMvc.perform(post("/api/v1/ai/interview/sessions")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {
                                  "targetRole": "Backend Engineer",
                                  "mode": "INTERVIEW_VOICE"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("语音面试入口暂未开放，请稍后再试。"));

        updateFeatureFlag(adminToken, "voice.interview.enabled", "true");
        updateFeatureFlag(adminToken, "voice.stt.enabled", "false");
        registerUser("STUDENT", "feature-ai-stt-student@example.com", "Passw0rd!", "FeatureAiSttStudent");
        String sttStudentToken = loginAndGetAccessToken("feature-ai-stt-student@example.com", "Passw0rd!");

        MvcResult voiceSessionResult = mockMvc.perform(post("/api/v1/ai/interview/sessions")
                        .header("Authorization", "Bearer " + sttStudentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetRole": "Backend Engineer",
                                  "mode": "INTERVIEW_VOICE"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String sessionId = objectMapper.readTree(voiceSessionResult.getResponse().getContentAsString()).path("data").path("sessionId").asText();

        mockMvc.perform(multipart("/api/v1/ai/interview/sessions/{sessionId}/voice-roundtrip", sessionId)
                        .file(new MockMultipartFile("audioFile", "answer.webm", "audio/webm", "voice test".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + sttStudentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("语音转写能力暂未开放，请稍后再试。"));

        mockMvc.perform(post("/api/v1/ai/tts/synthesize")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "你好，这是一个 TTS 功能开关测试。",
                                  "stylePrompt": "Read aloud in a calm tone:",
                                  "voiceName": "Zephyr"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("语音播报能力暂未开放，请稍后再试。"));

        MvcResult postResult = mockMvc.perform(post("/api/v1/community/posts")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "功能开关下的社区提问",
                                  "content": "我想验证社区 AI 预答开关是否生效。",
                                  "tags": ["求职提问"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiFirstCommentCreated").value(true))
                .andExpect(jsonPath("$.data.aiFirstCommentId").isNumber())
                .andReturn();
        long postId = objectMapper.readTree(postResult.getResponse().getContentAsString()).path("data").path("postId").asLong();
        assertThat(postId).isPositive();

        updateFeatureFlag(adminToken, "community.ai-first-reply.enabled", "false");

        mockMvc.perform(post("/api/v1/community/posts")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "功能开关下的社区追问",
                                  "content": "我想继续验证自动首评开关是否可以单独关闭。",
                                  "tags": ["求职提问"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiFirstCommentCreated").value(false))
                .andExpect(jsonPath("$.data.aiFirstCommentId").doesNotExist());

        mockMvc.perform(post("/api/v1/ai/community/pre-answer")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {
                                  "postId": %d
                                }
                                """).formatted(postId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("社区智能辅助暂未开放，请稍后再试。"));
    }

    private void updateFeatureFlag(String adminToken, String key, String value) throws Exception {
        mockMvc.perform(post("/api/v1/admin/feature-flags")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {
                                  "key": "%s",
                                  "value": "%s"
                                }
                                """).formatted(key, value)))
                .andExpect(status().isOk());
    }

    private void upsertFeatureFlag(String key, String value, long updatedBy) {
        int updated = jdbcTemplate.update(
                """
                UPDATE feature_flags
                   SET flag_value = ?,
                       description = ?,
                       updated_by = ?,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE flag_key = ?
                """,
                value,
                "feature flag cache direct write test",
                updatedBy,
                key
        );
        if (updated == 0) {
            jdbcTemplate.update(
                    """
                    INSERT INTO feature_flags(flag_key, flag_value, description, updated_by, created_at, updated_at)
                    VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    key,
                    value,
                    "feature flag cache direct write test",
                    updatedBy
            );
        }
    }

    private long registerUser(String role, String email, String password, String displayName) throws Exception {
        String body = String.format(
                "{" +
                        "\"role\":\"%s\"," +
                        "\"email\":\"%s\"," +
                        "\"password\":\"%s\"," +
                        "\"displayName\":\"%s\"" +
                        "}",
                role,
                email,
                password,
                displayName
        );

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        long userId = jsonNode.path("data").path("userId").asLong();
        if ("MENTOR".equals(role)) {
            jdbcTemplate.update(
                    "UPDATE mentor_profiles SET approval_status = 'APPROVED', updated_at = CURRENT_TIMESTAMP WHERE user_id = ?",
                    userId
            );
        }
        return userId;
    }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"email\":\"" + email + "\"," +
                                "\"password\":\"" + password + "\"" +
                                "}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }

}
