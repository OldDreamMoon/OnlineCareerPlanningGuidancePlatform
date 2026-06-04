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

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 使用历史详情与删除闭环测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiHistoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void aiHistory_shouldSupportDetailAndSoftDelete() throws Exception {
        registerUser("STUDENT", "ai-history@example.com", "Passw0rd!", "AiHistory");
        String studentToken = loginAndGetAccessToken("ai-history@example.com", "Passw0rd!");
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");
        long userId = findUserIdByEmail("ai-history@example.com");

        grantPoints(adminToken, userId, 25, "TEST_TOPUP");
        consumeInterviewFreeQuota(studentToken);
        long resumeRecordId = optimizeResume(studentToken);
        String sessionId = createInterviewSession(studentToken);
        completeInterviewSession(studentToken, sessionId);

        MvcResult historyResult = mockMvc.perform(get("/api/v1/ai/history")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andReturn();

        JsonNode historyJson = objectMapper.readTree(historyResult.getResponse().getContentAsString()).path("data");
        Map<String, JsonNode> recordsByTaskType = new HashMap<>();
        for (JsonNode item : historyJson.path("records")) {
            recordsByTaskType.put(item.path("taskType").asText(), item);
        }

        JsonNode resumeRecord = recordsByTaskType.get("RESUME");
        JsonNode interviewRecord = recordsByTaskType.get("INTERVIEW_TEXT");
        assertThat(resumeRecord).isNotNull();
        assertThat(interviewRecord).isNotNull();
        assertThat(resumeRecord.path("id").asLong()).isEqualTo(resumeRecordId);
        assertThat(interviewRecord.path("sessionId").asText()).isEqualTo(sessionId);

        mockMvc.perform(get("/api/v1/ai/history/resume/{recordId}", resumeRecordId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recordId").value(resumeRecordId))
                .andExpect(jsonPath("$.data.summary").isNotEmpty())
                .andExpect(jsonPath("$.data.scoreLabel").isNotEmpty())
                .andExpect(jsonPath("$.data.targetRole").value("Backend Engineer"))
                .andExpect(jsonPath("$.data.targetContext").value("校招正式批"))
                .andExpect(jsonPath("$.data.inputMode").value("text"))
                .andExpect(jsonPath("$.data.jobDescription").isNotEmpty())
                .andExpect(jsonPath("$.data.resumeText").isNotEmpty())
                .andExpect(jsonPath("$.data.structureItems[0].label").isNotEmpty())
                .andExpect(jsonPath("$.data.rewriteItems[0].afterText").isNotEmpty())
                .andExpect(jsonPath("$.data.strengths[0]").isNotEmpty());

        mockMvc.perform(get("/api/v1/ai/history/resume/{recordId}/preview", resumeRecordId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recordId").value(resumeRecordId))
                .andExpect(jsonPath("$.data.summary").isNotEmpty())
                .andExpect(jsonPath("$.data.suggestions[0]").isNotEmpty())
                .andExpect(jsonPath("$.data.scoreLabel").isNotEmpty())
                .andExpect(jsonPath("$.data.targetRole").value("Backend Engineer"))
                .andExpect(jsonPath("$.data.targetContext").value("校招正式批"))
                .andExpect(jsonPath("$.data.inputMode").value("text"))
                .andExpect(jsonPath("$.data.jobDescription").isNotEmpty())
                .andExpect(jsonPath("$.data.resumeText").isNotEmpty())
                .andExpect(jsonPath("$.data.strengths").doesNotExist())
                .andExpect(jsonPath("$.data.rewriteItems").doesNotExist());

        mockMvc.perform(get("/api/v1/ai/interview/sessions/{sessionId}", sessionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(sessionId))
                .andExpect(jsonPath("$.data.messages.length()").value(7))
                .andExpect(jsonPath("$.data.messages[2].coachFeedback").isNotEmpty())
                .andExpect(jsonPath("$.data.summary.overallScore").value(org.hamcrest.Matchers.greaterThanOrEqualTo(70)));

        mockMvc.perform(delete("/api/v1/ai/history/resume/{recordId}", resumeRecordId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("deleted"));

        mockMvc.perform(get("/api/v1/ai/history")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].taskType").value("INTERVIEW_TEXT"));

        mockMvc.perform(delete("/api/v1/ai/interview/sessions/{sessionId}", sessionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("deleted"));

        mockMvc.perform(get("/api/v1/ai/history")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void aiHistory_shouldRejectCrossUserAccessToResumeAndInterviewRecords() throws Exception {
        registerUser("STUDENT", "ai-history-owner@example.com", "Passw0rd!", "AiHistoryOwner");
        registerUser("STUDENT", "ai-history-visitor@example.com", "Passw0rd!", "AiHistoryVisitor");
        String ownerToken = loginAndGetAccessToken("ai-history-owner@example.com", "Passw0rd!");
        String visitorToken = loginAndGetAccessToken("ai-history-visitor@example.com", "Passw0rd!");
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");
        long ownerUserId = findUserIdByEmail("ai-history-owner@example.com");

        grantPoints(adminToken, ownerUserId, 25, "TEST_TOPUP");
        consumeInterviewFreeQuota(ownerToken);
        long resumeRecordId = optimizeResume(ownerToken, "ai-history-owner@example.com");
        String sessionId = createInterviewSession(ownerToken);
        completeInterviewSession(ownerToken, sessionId);

        mockMvc.perform(get("/api/v1/ai/history/resume/{recordId}", resumeRecordId)
                        .header("Authorization", "Bearer " + visitorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("resume history not found"));

        mockMvc.perform(get("/api/v1/ai/history/resume/{recordId}/preview", resumeRecordId)
                        .header("Authorization", "Bearer " + visitorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("resume history not found"));

        mockMvc.perform(get("/api/v1/ai/resume/export/{recordId}", resumeRecordId)
                        .header("Authorization", "Bearer " + visitorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("resume history not found"));

        mockMvc.perform(delete("/api/v1/ai/history/resume/{recordId}", resumeRecordId)
                        .header("Authorization", "Bearer " + visitorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("resume history not found"));

        mockMvc.perform(get("/api/v1/ai/interview/sessions/{sessionId}", sessionId)
                        .header("Authorization", "Bearer " + visitorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("interview session not found"));

        mockMvc.perform(delete("/api/v1/ai/interview/sessions/{sessionId}", sessionId)
                        .header("Authorization", "Bearer " + visitorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("interview session not found"));
    }

    private void consumeInterviewFreeQuota(String studentToken) throws Exception {
        String body = """
                {
                  "taskType": "INTERVIEW_TEXT",
                  "scene": "history-setup"
                }
                """;
        for (int index = 0; index < 5; index++) {
            mockMvc.perform(post("/api/v1/ai/ping")
                            .header("Authorization", "Bearer " + studentToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }
    }

    private long optimizeResume(String studentToken) throws Exception {
        return optimizeResume(studentToken, "ai-history@example.com");
    }

    private long optimizeResume(String studentToken, String ownerEmail) throws Exception {
        String body = """
                {
                  "targetRole": "Backend Engineer",
                  "targetContext": "校招正式批",
                  "jobDescription": "负责核心后端服务研发，要求熟悉 Java、MySQL、Redis 与性能优化。",
                  "resumeText": "负责订单中心后端接口与缓存优化，接口延迟下降 30%。"
                }
                """;
        mockMvc.perform(post("/api/v1/ai/resume/optimize")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        Long recordId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM ai_call_logs WHERE user_id = (SELECT id FROM users WHERE email = ?) AND task_type = 'RESUME' AND status = 'SUCCESS'",
                Long.class,
                ownerEmail
        );
        assertThat(recordId).isNotNull();
        return recordId;
    }

    private String createInterviewSession(String studentToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/ai/interview/sessions")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetRole": "Backend Engineer",
                                  "mode": "INTERVIEW_TEXT"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("sessionId").asText();
    }

    private void completeInterviewSession(String studentToken, String sessionId) throws Exception {
        String[] answers = new String[] {
                "我负责订单中心性能治理，结合缓存和数据库索引优化把接口延迟降低了 30%，并通过压测验证结果。",
                "为了保证高并发场景稳定，我增加了限流、重试和监控告警，把峰值吞吐提升到原来的 1.8 倍。",
                "我还输出了完整复盘，解释技术权衡、结果指标和后续复用方式，帮助团队沉淀了一套标准方案。"
        };
        for (String answer : answers) {
            mockMvc.perform(post("/api/v1/ai/interview/sessions/{sessionId}/reply", sessionId)
                            .header("Authorization", "Bearer " + studentToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("answerText", answer))))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/v1/ai/interview/sessions/{sessionId}/summary", sessionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());
    }

    private void grantPoints(String adminToken, long userId, int points, String reasonCode) throws Exception {
        String body = String.format(
                "{" +
                        "\"userId\":%d," +
                        "\"points\":%d," +
                        "\"reasonCode\":\"%s\"" +
                        "}",
                userId,
                points,
                reasonCode
        );

        mockMvc.perform(post("/api/v1/admin/growth/points/grant")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("points granted"));
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

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
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
