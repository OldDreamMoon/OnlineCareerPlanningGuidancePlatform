package com.bishe.server.profile;

import com.bishe.server.governance.ContentGovernanceConfigCacheService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 与治理模块联动测试：覆盖 AI 输入拦截与 AI 输出脱敏。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AiGovernanceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ContentGovernanceConfigCacheService contentGovernanceConfigCacheService;

    @BeforeEach
    @AfterEach
    void resetGovernanceConfigCache() {
        contentGovernanceConfigCacheService.evictPoliciesNow();
        contentGovernanceConfigCacheService.evictSensitiveTermsNow();
    }

    @Test
    void resumeOptimize_shouldBlockAiInputWithoutConsumingQuota() throws Exception {
        registerUser("STUDENT", "ai-gov-resume@example.com", "Passw0rd!", "AiGovResume");
        String studentToken = loginAndGetAccessToken("ai-gov-resume@example.com", "Passw0rd!");
        long userId = findUserIdByEmail("ai-gov-resume@example.com");

        createSensitiveTerm("违禁输入词A", "HIGH", "BLOCK", "AI_INPUT");

        mockMvc.perform(post("/api/v1/ai/resume/optimize")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetRole": "Backend Engineer",
                                  "resumeText": "我在项目里使用了违禁输入词A，需要验证输入治理是否能先拦截。"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MOD-1001"))
                .andExpect(jsonPath("$.data.moderation.sourceType").value("AI_INPUT"))
                .andExpect(jsonPath("$.data.moderation.action").value("BLOCK"));

        Integer callCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_call_logs WHERE user_id = ? AND task_type = 'RESUME'",
                Integer.class,
                userId
        );
        assertThat(callCount).isZero();

        mockMvc.perform(get("/api/v1/ai/quota/remaining")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quotas[?(@.taskType=='RESUME')].usedToday").value(org.hamcrest.Matchers.contains(0)));
    }

    @Test
    void resumeOptimizeAsyncTask_shouldBlockAiInputBeforeEnqueue() throws Exception {
        registerUser("STUDENT", "ai-gov-resume-async@example.com", "Passw0rd!", "AiGovResumeAsync");
        String studentToken = loginAndGetAccessToken("ai-gov-resume-async@example.com", "Passw0rd!");
        long userId = findUserIdByEmail("ai-gov-resume-async@example.com");

        createSensitiveTerm("违禁输入词异步A", "HIGH", "BLOCK", "AI_INPUT");

        mockMvc.perform(post("/api/v1/ai/resume/tasks")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetRole": "Backend Engineer",
                                  "resumeText": "我在项目里使用了违禁输入词异步A，需要验证异步提交前也会先拦截。"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MOD-1001"))
                .andExpect(jsonPath("$.data.moderation.sourceType").value("AI_INPUT"))
                .andExpect(jsonPath("$.data.moderation.action").value("BLOCK"));

        Integer taskCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_async_task_jobs WHERE user_id = ? AND task_type = 'RESUME'",
                Integer.class,
                userId
        );
        assertThat(taskCount).isZero();

        Integer callCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_call_logs WHERE user_id = ? AND task_type = 'RESUME'",
                Integer.class,
                userId
        );
        assertThat(callCount).isZero();
    }

    @Test
    void interviewFlow_shouldMaskAiOutputAndBlockUnsafeReply() throws Exception {
        registerUser("STUDENT", "ai-gov-interview@example.com", "Passw0rd!", "AiGovInterview");
        String studentToken = loginAndGetAccessToken("ai-gov-interview@example.com", "Passw0rd!");

        createSensitiveTerm("核心动作", "MEDIUM", "MASK", "AI_OUTPUT");

        MvcResult createResult = mockMvc.perform(post("/api/v1/ai/interview/sessions")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetRole": "Backend Engineer",
                                  "mode": "INTERVIEW_TEXT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.moderation.sourceType").value("AI_OUTPUT"))
                .andExpect(jsonPath("$.data.moderation.action").value("MASK"))
                .andExpect(jsonPath("$.data.firstQuestion").value(org.hamcrest.Matchers.containsString("****")))
                .andReturn();

        String sessionId = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data").path("sessionId").asText();
        assertThat(sessionId).startsWith("is_");

        createSensitiveTerm("违禁回答词B", "HIGH", "BLOCK", "AI_INPUT");

        mockMvc.perform(post("/api/v1/ai/interview/sessions/{sessionId}/reply", sessionId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answerText": "我这轮回答里包含违禁回答词B，应该先被输入治理拦截。"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MOD-1001"))
                .andExpect(jsonPath("$.data.moderation.sourceType").value("AI_INPUT"))
                .andExpect(jsonPath("$.data.moderation.action").value("BLOCK"));

        mockMvc.perform(get("/api/v1/ai/interview/sessions/{sessionId}", sessionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()").value(1))
                .andExpect(jsonPath("$.data.messages[0].text").value(org.hamcrest.Matchers.containsString("****")));
    }

    @Test
    void resumeOptimize_shouldBlockAiOutputWhenHighRiskTermMatched() throws Exception {
        registerUser("STUDENT", "ai-gov-output-block@example.com", "Passw0rd!", "AiGovOutputBlock");
        String studentToken = loginAndGetAccessToken("ai-gov-output-block@example.com", "Passw0rd!");

        createSensitiveTerm("缺少量化指标", "HIGH", "BLOCK", "AI_OUTPUT");

        mockMvc.perform(post("/api/v1/ai/resume/optimize")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetRole": "Backend Engineer",
                                  "resumeText": "我负责后端接口开发与缓存优化，但这里故意不写任何数字指标。"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MOD-1002"))
                .andExpect(jsonPath("$.message").value("output blocked by moderation policy"))
                .andExpect(jsonPath("$.data.moderation.sourceType").value("AI_OUTPUT"))
                .andExpect(jsonPath("$.data.moderation.action").value("BLOCK"))
                .andExpect(jsonPath("$.data.moderation.reasonCode").value("POLICY_HIGH_RISK"));
    }

    @Test
    void moderationPolicies_shouldToggleAiGovernanceImmediately() throws Exception {
        registerUser("STUDENT", "ai-gov-policy@example.com", "Passw0rd!", "AiGovPolicy");
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");
        String studentToken = loginAndGetAccessToken("ai-gov-policy@example.com", "Passw0rd!");
        long userId = findUserIdByEmail("ai-gov-policy@example.com");

        createSensitiveTerm("违禁输入词C", "HIGH", "BLOCK", "AI_INPUT");
        updatePolicies(adminToken, false, true, true, 3);

        mockMvc.perform(get("/api/v1/admin/content/moderation/policies")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiInputEnabled").value(false))
                .andExpect(jsonPath("$.data.aiOutputEnabled").value(true));

        mockMvc.perform(post("/api/v1/ai/resume/optimize")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetRole": "Backend Engineer",
                                  "resumeText": "我在项目里写了违禁输入词C，但由于策略已关闭，应该继续进入模型调用。"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.moderation.sourceType").value("AI_OUTPUT"))
                .andExpect(jsonPath("$.data.moderation.reasonCode").value("RULE_CLEAR"));

        Integer resumeCallCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_call_logs WHERE user_id = ? AND task_type = 'RESUME' AND status = 'SUCCESS'",
                Integer.class,
                userId
        );
        assertThat(resumeCallCount).isEqualTo(1);

        createSensitiveTerm("量化结果", "MEDIUM", "MASK", "AI_OUTPUT");
        updatePolicies(adminToken, true, false, true, 3);

        mockMvc.perform(get("/api/v1/admin/content/moderation/policies")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiInputEnabled").value(true))
                .andExpect(jsonPath("$.data.aiOutputEnabled").value(false));

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
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.moderation.sourceType").value("AI_OUTPUT"))
                .andExpect(jsonPath("$.data.moderation.action").value("PASS"))
                .andExpect(jsonPath("$.data.moderation.reasonCode").value("POLICY_DISABLED"))
                .andExpect(jsonPath("$.data.firstQuestion").isNotEmpty());
    }

    private void updatePolicies(String adminToken, boolean aiInputEnabled, boolean aiOutputEnabled, boolean communityStrictReviewEnabled,
            int autoHideReportThreshold) throws Exception {
        mockMvc.perform(put("/api/v1/admin/content/moderation/policies")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "aiInputEnabled": %s,
                                  "aiOutputEnabled": %s,
                                  "communityStrictReviewEnabled": %s,
                                  "autoHideReportThreshold": %d
                                }
                                """.formatted(aiInputEnabled, aiOutputEnabled, communityStrictReviewEnabled, autoHideReportThreshold)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    private void createSensitiveTerm(String term, String riskLevel, String action, String sourceScope) {
        jdbcTemplate.update(
                """
                        INSERT INTO sensitive_terms(term, risk_level, action, source_scope, is_whitelist, enabled, created_at, updated_at)
                        VALUES (?, ?, ?, ?, FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                term,
                riskLevel,
                action,
                sourceScope
        );
        contentGovernanceConfigCacheService.evictSensitiveTermsNow();
    }

    private void registerUser(String role, String email, String password, String displayName) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "%s",
                                  "email": "%s",
                                  "password": "%s",
                                  "displayName": "%s"
                                }
                                """.formatted(role, email, password, displayName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
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
