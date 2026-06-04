package com.bishe.server.mentor;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理员导师经营治理台集成回归测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminMentorOpsIntegrationTest {

    private static final int WITHDRAWAL_AMOUNT_FEN = 8800;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void adminMentorOps_shouldSupportOverviewListAndWithdrawalManage() throws Exception {
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");
        OverviewMetrics baselineOverview = getOverview(adminToken);

        registerUser("MENTOR", "admin-mentor-ops@example.com", "Passw0rd!", "MentorOpsRegression");
        String mentorToken = loginAndGetAccessToken("admin-mentor-ops@example.com", "Passw0rd!");
        long mentorUserId = findUserIdByEmail("admin-mentor-ops@example.com");
        prepareMentorFixture(mentorUserId);
        long withdrawalId = findLatestWithdrawalId(mentorUserId);

        OverviewMetrics afterSetupOverview = getOverview(adminToken);
        assertThat(afterSetupOverview.totalMentorCount()).isEqualTo(baselineOverview.totalMentorCount() + 1);
        assertThat(afterSetupOverview.approvedMentorCount()).isEqualTo(baselineOverview.approvedMentorCount() + 1);
        assertThat(afterSetupOverview.riskyMentorCount()).isEqualTo(baselineOverview.riskyMentorCount() + 1);
        assertThat(afterSetupOverview.pendingWithdrawalMentorCount()).isEqualTo(baselineOverview.pendingWithdrawalMentorCount() + 1);
        assertThat(afterSetupOverview.pendingWithdrawalAmountFen()).isEqualTo(baselineOverview.pendingWithdrawalAmountFen() + WITHDRAWAL_AMOUNT_FEN);

        mockMvc.perform(get("/api/v1/admin/mentors/operations")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("page", "1")
                        .param("size", "10")
                        .param("keyword", "MentorOpsRegression")
                        .param("approvalStatus", "APPROVED")
                        .param("riskLevel", "MEDIUM")
                        .param("withdrawalStatus", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].mentorUserId").value(mentorUserId))
                .andExpect(jsonPath("$.data.records[0].displayName").value("MentorOpsRegression"))
                .andExpect(jsonPath("$.data.records[0].approvalStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.records[0].highestRiskLevel").value("MEDIUM"))
                .andExpect(jsonPath("$.data.records[0].latestWithdrawalStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.records[0].openWithdrawalCount").value(1))
                .andExpect(jsonPath("$.data.records[0].openWithdrawalAmountFen").value(WITHDRAWAL_AMOUNT_FEN))
                .andExpect(jsonPath("$.data.records[0].riskSignals[0].code").value("WITHDRAWAL_PENDING"));

        mockMvc.perform(post("/api/v1/admin/mentors/operations/withdrawals/{withdrawalId}/status", withdrawalId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "PROCESSING"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("admin mentor withdrawal updated"))
                .andExpect(jsonPath("$.data.withdrawalId").value(withdrawalId))
                .andExpect(jsonPath("$.data.mentorUserId").value(mentorUserId))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));

        mockMvc.perform(get("/api/v1/admin/mentors/operations")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("page", "1")
                        .param("size", "10")
                        .param("keyword", "MentorOpsRegression")
                        .param("withdrawalStatus", "PROCESSING")
                        .param("riskLevel", "LOW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].latestWithdrawalStatus").value("PROCESSING"))
                .andExpect(jsonPath("$.data.records[0].highestRiskLevel").value("LOW"))
                .andExpect(jsonPath("$.data.records[0].riskSignals[0].code").value("WITHDRAWAL_PROCESSING"));

        mockMvc.perform(post("/api/v1/admin/mentors/operations/withdrawals/{withdrawalId}/status", withdrawalId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "COMPLETED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.withdrawalId").value(withdrawalId))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        OverviewMetrics afterCompleteOverview = getOverview(adminToken);
        assertThat(afterCompleteOverview.totalMentorCount()).isEqualTo(baselineOverview.totalMentorCount() + 1);
        assertThat(afterCompleteOverview.approvedMentorCount()).isEqualTo(baselineOverview.approvedMentorCount() + 1);
        assertThat(afterCompleteOverview.riskyMentorCount()).isEqualTo(baselineOverview.riskyMentorCount());
        assertThat(afterCompleteOverview.pendingWithdrawalMentorCount()).isEqualTo(baselineOverview.pendingWithdrawalMentorCount());
        assertThat(afterCompleteOverview.pendingWithdrawalAmountFen()).isEqualTo(baselineOverview.pendingWithdrawalAmountFen());

        mockMvc.perform(get("/api/v1/admin/mentors/operations")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("page", "1")
                        .param("size", "10")
                        .param("keyword", "MentorOpsRegression")
                        .param("withdrawalStatus", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].latestWithdrawalStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.records[0].openWithdrawalCount").value(0));

        mockMvc.perform(get("/api/v1/admin/mentors/operations/overview")
                        .header("Authorization", "Bearer " + mentorToken))
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
        String accessToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();
        assertThat(accessToken).isNotBlank();
        return accessToken;
    }

    private OverviewMetrics getOverview(String adminToken) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/admin/mentors/operations/overview")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return new OverviewMetrics(
                data.path("totalMentorCount").asLong(),
                data.path("approvedMentorCount").asLong(),
                data.path("riskyMentorCount").asLong(),
                data.path("pendingWithdrawalMentorCount").asLong(),
                data.path("pendingWithdrawalAmountFen").asLong()
        );
    }

    private long findUserIdByEmail(String email) {
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        assertThat(userId).isNotNull();
        return userId;
    }

    private void prepareMentorFixture(long mentorUserId) {
        jdbcTemplate.update(
                """
                UPDATE mentor_profiles
                   SET approval_status = 'APPROVED',
                       company_name = '管理员导师治理台',
                       job_title = '求职教练',
                       updated_at = CURRENT_TIMESTAMP
                 WHERE user_id = ?
                """,
                mentorUserId
        );

        jdbcTemplate.update(
                """
                INSERT INTO mentor_service_packages(
                    mentor_user_id,
                    package_name,
                    scene_code,
                    scene_label,
                    delivery_mode,
                    duration_minutes,
                    price_fen,
                    description,
                    enabled,
                    sort_no,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                mentorUserId,
                "管理员回归套餐",
                "resume_polish",
                "简历优化",
                "TEXT_ASYNC",
                60,
                19900,
                "用于管理员导师经营治理台集成测试"
        );

        jdbcTemplate.update(
                """
                INSERT INTO mentor_withdrawal_requests(
                    mentor_user_id,
                    amount_fen,
                    status,
                    note,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, 'PENDING', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                mentorUserId,
                WITHDRAWAL_AMOUNT_FEN,
                "管理员导师治理台回归提现申请"
        );
    }

    private long findLatestWithdrawalId(long mentorUserId) {
        Long withdrawalId = jdbcTemplate.queryForObject(
                """
                SELECT id
                  FROM mentor_withdrawal_requests
                 WHERE mentor_user_id = ?
                 ORDER BY id DESC
                 LIMIT 1
                """,
                Long.class,
                mentorUserId
        );
        assertThat(withdrawalId).isNotNull();
        return withdrawalId;
    }

    private record OverviewMetrics(
            long totalMentorCount,
            long approvedMentorCount,
            long riskyMentorCount,
            long pendingWithdrawalMentorCount,
            long pendingWithdrawalAmountFen
    ) {
    }
}
