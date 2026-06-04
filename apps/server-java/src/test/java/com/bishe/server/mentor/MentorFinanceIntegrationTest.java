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

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 导师财务中心集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MentorFinanceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void mentorFinance_shouldPersistSimulatedWithdrawals() throws Exception {
        long mentorUserId = registerUser("MENTOR", "mentor-finance@example.com", "Passw0rd!", "FinanceMentor");
        long studentUserId = registerUser("STUDENT", "mentor-finance-student@example.com", "Passw0rd!", "FinanceStudent");
        String mentorToken = loginAndGetAccessToken("mentor-finance@example.com", "Passw0rd!");

        jdbcTemplate.update(
                """
                INSERT INTO consult_orders(order_no, student_user_id, mentor_user_id, amount_fen, status, question_text, created_at, updated_at, paid_at, closed_at)
                VALUES (?, ?, ?, ?, 'CLOSED', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                "ORD-FINANCE-001",
                studentUserId,
                mentorUserId,
                15000,
                "导师财务中心模拟提现测试订单"
        );

        mockMvc.perform(get("/api/v1/mentor/finance/withdrawals")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(0));

        MvcResult createResult = mockMvc.perform(post("/api/v1/mentor/finance/withdrawals")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amountFen": 6000,
                                  "note": "第一笔模拟提现申请"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("withdrawal created"))
                .andExpect(jsonPath("$.data.amountFen").value(6000))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();

        long withdrawalId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .asLong();
        assertThat(withdrawalId).isPositive();

        mockMvc.perform(post("/api/v1/mentor/finance/withdrawals/{withdrawalId}/status", withdrawalId)
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "PROCESSING"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));

        mockMvc.perform(post("/api/v1/mentor/finance/withdrawals/{withdrawalId}/status", withdrawalId)
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "COMPLETED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        mockMvc.perform(get("/api/v1/mentor/finance/withdrawals")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.records[0].amountFen").value(6000));
    }

    @Test
    void mentorFinanceOverview_shouldReflectJpaAggregates() throws Exception {
        long mentorUserId = registerUser("MENTOR", "mentor-finance-overview@example.com", "Passw0rd!", "OverviewMentor");
        long studentAUserId = registerUser("STUDENT", "mentor-finance-overview-student-a@example.com", "Passw0rd!", "OverviewStudentA");
        long studentBUserId = registerUser("STUDENT", "mentor-finance-overview-student-b@example.com", "Passw0rd!", "OverviewStudentB");
        String mentorToken = loginAndGetAccessToken("mentor-finance-overview@example.com", "Passw0rd!");

        jdbcTemplate.update(
                """
                UPDATE mentor_profiles
                   SET approval_status = 'APPROVED',
                       avg_rating = 4.60,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE user_id = ?
                """,
                mentorUserId
        );

        Instant now = Instant.now();
        Instant closedOrderCreatedAt = now.minus(4, ChronoUnit.DAYS);
        Instant closedOrderPaidAt = now.minus(3, ChronoUnit.DAYS);
        Instant closedOrderClosedAt = now.minus(2, ChronoUnit.DAYS);
        Instant answeredOrderCreatedAt = now.minus(3, ChronoUnit.DAYS);
        Instant answeredOrderPaidAt = now.minus(1, ChronoUnit.DAYS);
        Instant refundedOrderCreatedAt = now.minus(1, ChronoUnit.DAYS);
        Instant refundedOrderPaidAt = now.minus(5, ChronoUnit.DAYS);
        Instant refundedOrderClosedAt = now.minus(1, ChronoUnit.DAYS);

        insertConsultOrder(
                "ORD-FINANCE-OVERVIEW-001",
                studentAUserId,
                mentorUserId,
                15000,
                "CLOSED",
                "概览测试已完成订单",
                closedOrderCreatedAt,
                closedOrderPaidAt,
                closedOrderClosedAt
        );
        insertConsultOrder(
                "ORD-FINANCE-OVERVIEW-002",
                studentBUserId,
                mentorUserId,
                9000,
                "ANSWERED",
                "概览测试待关闭订单",
                answeredOrderCreatedAt,
                answeredOrderPaidAt,
                null
        );
        insertConsultOrder(
                "ORD-FINANCE-OVERVIEW-003",
                studentAUserId,
                mentorUserId,
                4000,
                "REFUNDED",
                "概览测试已退款订单",
                refundedOrderCreatedAt,
                refundedOrderPaidAt,
                refundedOrderClosedAt
        );

        insertPaymentRecord("ORD-FINANCE-OVERVIEW-001", "WECHAT", 15000, "overview-payment-001");
        insertPaymentRecord("ORD-FINANCE-OVERVIEW-002", "WECHAT", 9000, "overview-payment-002");
        insertPaymentRecord("ORD-FINANCE-OVERVIEW-003", "BANK_CARD", 4000, "overview-payment-003-a");
        insertPaymentRecord("ORD-FINANCE-OVERVIEW-003", "ALIPAY", 4000, "overview-payment-003-b");

        jdbcTemplate.update(
                """
                INSERT INTO mentor_withdrawal_requests(mentor_user_id, amount_fen, status, note, created_at, updated_at)
                VALUES (?, 3000, 'PENDING', '待审核提现', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                mentorUserId
        );
        jdbcTemplate.update(
                """
                INSERT INTO mentor_withdrawal_requests(mentor_user_id, amount_fen, status, note, created_at, updated_at)
                VALUES (?, 2000, 'COMPLETED', '已完成提现', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                mentorUserId
        );

        MvcResult overviewResult = mockMvc.perform(get("/api/v1/mentor/finance/overview")
                        .header("Authorization", "Bearer " + mentorToken)
                        .param("page", "1")
                        .param("size", "10")
                        .param("trendRange", "7D"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metrics.totalRevenueFen").value(24000))
                .andExpect(jsonPath("$.data.metrics.totalRevenueOrderCount").value(2))
                .andExpect(jsonPath("$.data.metrics.completedIncomeFen").value(15000))
                .andExpect(jsonPath("$.data.metrics.availableWithdrawalFen").value(10000))
                .andExpect(jsonPath("$.data.metrics.pendingWithdrawalFen").value(3000))
                .andExpect(jsonPath("$.data.metrics.completedWithdrawalFen").value(2000))
                .andExpect(jsonPath("$.data.metrics.refundedAmountFen").value(4000))
                .andExpect(jsonPath("$.data.metrics.refundedOrderCount").value(1))
                .andExpect(jsonPath("$.data.metrics.answeredCount").value(1))
                .andExpect(jsonPath("$.data.metrics.avgRating").value(4.6))
                .andExpect(jsonPath("$.data.bills.total").value(3))
                .andExpect(jsonPath("$.data.bills.records[0].orderNo").value("ORD-FINANCE-OVERVIEW-003"))
                .andExpect(jsonPath("$.data.bills.records[0].paymentMode").value("ALIPAY"))
                .andReturn();

        JsonNode overviewJson = objectMapper.readTree(overviewResult.getResponse().getContentAsString());
        int totalPaidFen = 0;
        int totalRefundedFen = 0;
        for (JsonNode trendPoint : overviewJson.path("data").path("trend")) {
            totalPaidFen += trendPoint.path("paidFen").asInt();
            totalRefundedFen += trendPoint.path("refundedFen").asInt();
        }
        assertThat(totalPaidFen).isEqualTo(24000);
        assertThat(totalRefundedFen).isEqualTo(4000);
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
        return jsonNode.path("data").path("userId").asLong();
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
        return jsonNode.path("data").path("accessToken").asText();
    }

    private void insertConsultOrder(
            String orderNo,
            long studentUserId,
            long mentorUserId,
            int amountFen,
            String status,
            String questionText,
            Instant createdAt,
            Instant paidAt,
            Instant closedAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO consult_orders(
                    order_no,
                    student_user_id,
                    mentor_user_id,
                    amount_fen,
                    status,
                    question_text,
                    created_at,
                    updated_at,
                    paid_at,
                    closed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                orderNo,
                studentUserId,
                mentorUserId,
                amountFen,
                status,
                questionText,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt),
                paidAt == null ? null : Timestamp.from(paidAt),
                closedAt == null ? null : Timestamp.from(closedAt)
        );
    }

    private void insertPaymentRecord(String orderNo, String mode, int amountFen, String idempotencyKey) {
        jdbcTemplate.update(
                """
                INSERT INTO payment_records(
                    order_no,
                    channel,
                    mode,
                    amount_fen,
                    status,
                    idempotency_key,
                    created_at,
                    updated_at
                ) VALUES (?, 'MOCK', ?, ?, 'SUCCESS', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                orderNo,
                mode,
                amountFen,
                idempotencyKey
        );
    }
}
