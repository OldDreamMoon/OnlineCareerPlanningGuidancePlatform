package com.bishe.server.bounty;

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
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理员企业任务治理台集成回归测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminEnterpriseTaskOpsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void adminEnterpriseTaskOps_shouldSupportOverviewListDetailAndManage() throws Exception {
        registerUser("ENTERPRISE", "admin-task-enterprise@example.com", "Passw0rd!", "TaskEnterprise");
        approveEnterprise(findUserIdByEmail("admin-task-enterprise@example.com"));
        String enterpriseToken = loginAndGetAccessToken("admin-task-enterprise@example.com", "Passw0rd!");
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");

        long taskId = createTask(enterpriseToken, "管理员任务治理测试", "用于验证平台治理台的列表与状态流转。", "平台巡检奖励");

        mockMvc.perform(get("/api/v1/admin/enterprise/tasks/overview")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.totalTaskCount").value(1))
                .andExpect(jsonPath("$.data.openTaskCount").value(1));

        mockMvc.perform(get("/api/v1/admin/enterprise/tasks")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("page", "1")
                        .param("size", "10")
                        .param("keyword", "管理员任务治理测试"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[?(@.taskId==%s)].title".formatted(taskId)).value(contains("管理员任务治理测试")))
                .andExpect(jsonPath("$.data.records[?(@.taskId==%s)].status".formatted(taskId)).value(contains("OPEN")));

        mockMvc.perform(get("/api/v1/admin/enterprise/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.taskId").value(taskId))
                .andExpect(jsonPath("$.data.title").value("管理员任务治理测试"))
                .andExpect(jsonPath("$.data.descriptionPreview").isNotEmpty());

        mockMvc.perform(post("/api/v1/admin/enterprise/tasks/{taskId}/manage", taskId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "CLOSE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("admin enterprise task updated"))
                .andExpect(jsonPath("$.data.taskId").value(taskId))
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        mockMvc.perform(get("/api/v1/admin/enterprise/tasks/overview")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTaskCount").value(1))
                .andExpect(jsonPath("$.data.openTaskCount").value(0));

        mockMvc.perform(get("/api/v1/admin/enterprise/tasks")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("page", "1")
                        .param("size", "10")
                        .param("status", "CLOSED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[?(@.taskId==%s)].status".formatted(taskId)).value(contains("CLOSED")));

        mockMvc.perform(post("/api/v1/admin/enterprise/tasks/{taskId}/manage", taskId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "REOPEN"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value(taskId))
                .andExpect(jsonPath("$.data.status").value("OPEN"));

        mockMvc.perform(get("/api/v1/admin/enterprise/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value(taskId))
                .andExpect(jsonPath("$.data.status").value("OPEN"));

        mockMvc.perform(get("/api/v1/admin/enterprise/tasks")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("page", "1")
                        .param("size", "10")
                        .param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[?(@.taskId==%s)].status".formatted(taskId)).value(contains("OPEN")));
    }

    @Test
    void enterpriseAccessAdminEnterpriseTaskOps_shouldReturn403() throws Exception {
        registerUser("ENTERPRISE", "admin-task-forbidden@example.com", "Passw0rd!", "ForbiddenTaskEnterprise");
        String enterpriseToken = loginAndGetAccessToken("admin-task-forbidden@example.com", "Passw0rd!");

        mockMvc.perform(get("/api/v1/admin/enterprise/tasks/overview")
                        .header("Authorization", "Bearer " + enterpriseToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-1004"));
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
                .andExpect(status().isOk());
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

    private long createTask(String enterpriseToken, String title, String description, String rewardDescription) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/bounty/tasks")
                        .header("Authorization", "Bearer " + enterpriseToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "description": "%s",
                                  "rewardDescription": "%s"
                                }
                                """.formatted(title, description, rewardDescription)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("taskId").asLong();
    }

    private long findUserIdByEmail(String email) {
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        assertThat(userId).isNotNull();
        return userId;
    }

    private void approveEnterprise(long enterpriseUserId) {
        jdbcTemplate.update(
                """
                UPDATE enterprise_profiles
                   SET approval_status = 'APPROVED',
                       updated_at = CURRENT_TIMESTAMP
                 WHERE user_id = ?
                """,
                enterpriseUserId
        );
    }
}
