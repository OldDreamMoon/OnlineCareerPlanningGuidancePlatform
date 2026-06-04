package com.bishe.server.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 配额与积分联动最小闭环测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiQuotaIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void freeTier_shouldUseFreeQuotaThenRequirePointsAndDeductLedger() throws Exception {
        registerUser("STUDENT", "ai-free@example.com", "Passw0rd!", "AiFree");
        String studentToken = loginAndGetAccessToken("ai-free@example.com", "Passw0rd!");

        mockMvc.perform(get("/api/v1/ai/quota/remaining")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tier").value("FREE"))
                .andExpect(jsonPath("$.data.pointsBalance").value(0))
                .andExpect(jsonPath("$.data.quotas[*].taskType", hasItem("INTERVIEW_TEXT")));

        String body = """
                {
                  "taskType": "RESUME",
                  "scene": "dashboard"
                }
                """;

        for (int index = 0; index < 3; index++) {
            mockMvc.perform(post("/api/v1/ai/ping")
                            .header("Authorization", "Bearer " + studentToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.taskType").value("RESUME"))
                    .andExpect(jsonPath("$.data.freeCall").value(true))
                    .andExpect(jsonPath("$.data.chargedPoints").value(0));
        }

        mockMvc.perform(post("/api/v1/ai/ping")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AI-2201"));

        mockMvc.perform(post("/api/v1/growth/checkin")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pointsEarned").value(10));

        mockMvc.perform(get("/api/v1/growth/points/ledger")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(20))
                .andExpect(jsonPath("$.data.total").value(2));

        mockMvc.perform(post("/api/v1/ai/ping")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.freeCall").value(false))
                .andExpect(jsonPath("$.data.chargedPoints").value(10))
                .andExpect(jsonPath("$.data.pointsBalance").value(10));

        mockMvc.perform(get("/api/v1/growth/points/ledger")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(10))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records[*].reasonCode", hasItem("AI_RESUME")))
                .andExpect(jsonPath("$.data.records[*].reasonCode", hasItem("CHECKIN")))
                .andExpect(jsonPath("$.data.records[*].reasonCode", hasItem("TASK_RESUME_OPTIMIZE")))
                .andExpect(jsonPath("$.data.records[*].deltaPoints", hasItem(-10)));
    }

    @Test
    void premiumTier_shouldBypassQuotaAndKeepPointsBalance() throws Exception {
        registerUser("STUDENT", "ai-premium@example.com", "Passw0rd!", "AiPremium");
        long userId = findUserIdByEmail("ai-premium@example.com");
        jdbcTemplate.update("UPDATE users SET tier = 'PREMIUM' WHERE id = ?", userId);
        String studentToken = loginAndGetAccessToken("ai-premium@example.com", "Passw0rd!");

        String body = """
                {
                  "taskType": "INTERVIEW_TEXT",
                  "scene": "dashboard"
                }
                """;
        for (int index = 0; index < 6; index++) {
            mockMvc.perform(post("/api/v1/ai/ping")
                            .header("Authorization", "Bearer " + studentToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.tier").value("PREMIUM"))
                    .andExpect(jsonPath("$.data.chargedPoints").value(0));
        }

        mockMvc.perform(get("/api/v1/ai/quota/remaining")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tier").value("PREMIUM"))
                .andExpect(jsonPath("$.data.pointsBalance").value(0))
                .andExpect(jsonPath("$.data.quotas[?(@.taskType == 'INTERVIEW_TEXT')].remaining", hasItem(-1)));
    }

    @Test
    void premiumTier_shouldBeRateLimitedWithinMinuteWindow() throws Exception {
        registerUser("STUDENT", "ai-rate-limit@example.com", "Passw0rd!", "AiRateLimit");
        long userId = findUserIdByEmail("ai-rate-limit@example.com");
        jdbcTemplate.update("UPDATE users SET tier = 'PREMIUM' WHERE id = ?", userId);
        String studentToken = loginAndGetAccessToken("ai-rate-limit@example.com", "Passw0rd!");

        String body = """
                {
                  "taskType": "RESUME",
                  "scene": "dashboard"
                }
                """;
        for (int index = 0; index < 30; index++) {
            mockMvc.perform(post("/api/v1/ai/ping")
                            .header("Authorization", "Bearer " + studentToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.tier").value("PREMIUM"));
        }

        mockMvc.perform(post("/api/v1/ai/ping")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AI-2202"));
    }

    @Test
    void freeTier_shouldApplyIndependentQuotaForCommunitySceneOverrides() throws Exception {
        registerUser("STUDENT", "ai-scene-free@example.com", "Passw0rd!", "AiSceneFree");
        registerUser("MENTOR", "ai-scene-mentor@example.com", "Passw0rd!", "AiSceneMentor");
        long studentUserId = findUserIdByEmail("ai-scene-free@example.com");
        long mentorUserId = findUserIdByEmail("ai-scene-mentor@example.com");
        String studentToken = loginAndGetAccessToken("ai-scene-free@example.com", "Passw0rd!");
        String mentorToken = loginAndGetAccessToken("ai-scene-mentor@example.com", "Passw0rd!");

        jdbcTemplate.update(
                """
                UPDATE ai_quota_policies
                   SET daily_free_limit = 1,
                       daily_max_limit = 1,
                       points_per_call = 99
                 WHERE tier = 'FREE'
                   AND task_type = 'COMMUNITY_REPLY'
                   AND scene_code = 'COMMUNITY_PRE_ANSWER'
                """
        );
        jdbcTemplate.update(
                """
                UPDATE ai_quota_policies
                   SET daily_free_limit = 2,
                       daily_max_limit = 2,
                       points_per_call = 88
                 WHERE tier = 'FREE'
                   AND task_type = 'COMMUNITY_REPLY'
                   AND scene_code = 'MENTOR_PREP_SHEET_GENERATE'
                """
        );

        long postId = createCommunityPost(mentorToken);

        mockMvc.perform(post("/api/v1/ai/community/pre-answer")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {
                                  "postId": %d
                                }
                                """).formatted(postId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tag").value("AI_GENERATED"))
                .andExpect(jsonPath("$.data.draftComment").isNotEmpty());

        mockMvc.perform(post("/api/v1/mentors/prep-sheet/generate")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {
                                  "mentorUserId": %d,
                                  "scene": "简历诊断",
                                  "targetPosition": "后端开发工程师"
                                }
                                """).formatted(mentorUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mentorUserId").value(mentorUserId))
                .andExpect(jsonPath("$.data.scene").value("简历诊断"))
                .andExpect(jsonPath("$.data.coreQuestions.length()").value(3));

        mockMvc.perform(post("/api/v1/mentors/prep-sheet/generate")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {
                                  "mentorUserId": %d,
                                  "scene": "简历诊断",
                                  "targetPosition": "后端开发工程师"
                                }
                                """).formatted(mentorUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mentorUserId").value(mentorUserId));

        mockMvc.perform(post("/api/v1/ai/community/pre-answer")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {
                                  "postId": %d
                                }
                                """).formatted(postId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AI-2201"));

        Integer communityPreAnswerUsage = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(SUM(quota_weight), 0)
                  FROM ai_call_logs
                 WHERE user_id = ?
                   AND task_type = 'COMMUNITY_REPLY'
                   AND scene_code = 'COMMUNITY_PRE_ANSWER'
                   AND status = 'SUCCESS'
                """,
                Integer.class,
                studentUserId
        );
        Integer mentorPrepUsage = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(SUM(quota_weight), 0)
                  FROM ai_call_logs
                 WHERE user_id = ?
                   AND task_type = 'COMMUNITY_REPLY'
                   AND scene_code = 'MENTOR_PREP_SHEET_GENERATE'
                   AND status = 'SUCCESS'
                """,
                Integer.class,
                studentUserId
        );
        assertThat(communityPreAnswerUsage).isEqualTo(1);
        assertThat(mentorPrepUsage).isEqualTo(2);
    }

    private long createCommunityPost(String accessToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/community/posts")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "如何准备后端实习面试？",
                                  "content": "我最近在准备简历、项目复盘和八股表达，想知道先补哪一块更合适。",
                                  "tags": ["求职提问", "后端"]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        long postId = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("postId").asLong();
        assertThat(postId).isPositive();
        return postId;
    }

    private void registerUser(String role, String email, String password, String displayName) throws Exception {
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
                .andExpect(jsonPath("$.code").value("OK"))
                .andReturn();

        long userId = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("userId").asLong();
        if ("MENTOR".equals(role)) {
            jdbcTemplate.update(
                    "UPDATE mentor_profiles SET approval_status = 'APPROVED', updated_at = CURRENT_TIMESTAMP WHERE user_id = ?",
                    userId
            );
        }
    }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        String loginBody = String.format(
                "{" +
                        "\"email\":\"%s\"," +
                        "\"password\":\"%s\"" +
                        "}",
                email,
                password
        );

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        String accessToken = jsonNode.path("data").path("accessToken").asText();
        assertThat(accessToken).isNotBlank();
        return accessToken;
    }

    private long findUserIdByEmail(String email) {
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        assertThat(userId).isNotNull();
        return userId;
    }
}
