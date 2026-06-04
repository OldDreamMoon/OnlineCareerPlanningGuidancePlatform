package com.bishe.server.consult;

import com.bishe.server.consult.repository.ConsultRepository;
import com.bishe.server.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ConsultRepository 中 consult_orders 状态流转与超时扫描在 PostgreSQL 下的最小验证。
 */
@DataJpaTest
@Import(ConsultRepository.class)
class ConsultOrderStateJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final long MENTOR_USER_ID = 6911L;
    private static final long STUDENT_USER_ID = 6912L;

    @Autowired
    private ConsultRepository consultRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM consult_orders WHERE order_no LIKE 'PG-CONSULT-STATE-%'");
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", MENTOR_USER_ID, STUDENT_USER_ID);
    }

    @Test
    void repositoryShouldUseJpaForOrderStateTransitionsAndTimeoutQueries() {
        insertUser(MENTOR_USER_ID, "MENTOR", "consult-state-mentor@example.com", "Consult State Mentor");
        insertUser(STUDENT_USER_ID, "STUDENT", "consult-state-student@example.com", "Consult State Student");

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        long flowOrderId = insertOrder("PG-CONSULT-STATE-FLOW", "CREATED", 12800, now.minus(10, ChronoUnit.MINUTES), null, null);
        long cancelOrderId = insertOrder("PG-CONSULT-STATE-CANCEL", "PAYING", 8800, now.minus(40, ChronoUnit.MINUTES), null, null);
        long failedOrderId = insertOrder("PG-CONSULT-STATE-FAILED", "CREATED", 7600, now.minus(5, ChronoUnit.MINUTES), null, null);
        insertOrder("PG-CONSULT-STATE-UNPAID-1", "CREATED", 9800, now.minus(2, ChronoUnit.HOURS), null, null);
        insertOrder("PG-CONSULT-STATE-UNPAID-2", "PAYING", 9900, now.minus(90, ChronoUnit.MINUTES), null, null);
        insertOrder("PG-CONSULT-STATE-PAID-1", "PAID", 10800, now.minus(3, ChronoUnit.HOURS), now.minus(2, ChronoUnit.HOURS), null);
        insertOrder("PG-CONSULT-STATE-PAID-2", "CLOSED", 15800, now.minus(4, ChronoUnit.HOURS), now.minus(3, ChronoUnit.HOURS), now.minus(2, ChronoUnit.HOURS));

        consultRepository.markOrderPaying(flowOrderId, "PG-CONSULT-STATE-FLOW");
        assertStatus("PG-CONSULT-STATE-FLOW", "PAYING");

        consultRepository.markOrderPaid(flowOrderId, "PG-CONSULT-STATE-FLOW");
        assertStatus("PG-CONSULT-STATE-FLOW", "PAID");
        assertThat(queryInstant("SELECT paid_at FROM consult_orders WHERE order_no = ?", "PG-CONSULT-STATE-FLOW")).isNotNull();

        consultRepository.markOrderAnswered(flowOrderId, "PG-CONSULT-STATE-FLOW");
        assertStatus("PG-CONSULT-STATE-FLOW", "ANSWERED");

        consultRepository.markOrderClosed(flowOrderId, "PG-CONSULT-STATE-FLOW");
        assertStatus("PG-CONSULT-STATE-FLOW", "CLOSED");
        assertThat(queryInstant("SELECT closed_at FROM consult_orders WHERE order_no = ?", "PG-CONSULT-STATE-FLOW")).isNotNull();

        assertThat(consultRepository.markOrderRefunded(flowOrderId, "PG-CONSULT-STATE-FLOW")).isTrue();
        assertStatus("PG-CONSULT-STATE-FLOW", "REFUNDED");

        assertThat(consultRepository.markOrderCanceled(cancelOrderId, "PG-CONSULT-STATE-CANCEL")).isTrue();
        assertStatus("PG-CONSULT-STATE-CANCEL", "CANCELED");

        assertThat(consultRepository.markOrderFailed(failedOrderId, "PG-CONSULT-STATE-FAILED")).isTrue();
        assertStatus("PG-CONSULT-STATE-FAILED", "FAILED");
        assertThat(consultRepository.markOrderFailed(failedOrderId, "PG-CONSULT-STATE-FAILED")).isFalse();

        assertThat(consultRepository.findTimedOutUnpaidOrderNos(now.minus(30, ChronoUnit.MINUTES)))
                .containsExactly("PG-CONSULT-STATE-UNPAID-1", "PG-CONSULT-STATE-UNPAID-2");
        assertThat(consultRepository.findTimedOutPaidOrderNos(now.minus(30, ChronoUnit.MINUTES)))
                .containsExactly("PG-CONSULT-STATE-PAID-1");

        assertThat(consultRepository.sumPaidRevenueForMentor(MENTOR_USER_ID)).isEqualTo(26600);
    }

    private void insertUser(long userId, String role, String email, String displayName) {
        jdbcTemplate.update(
                """
                INSERT INTO users(
                    id,
                    email,
                    password_hash,
                    role,
                    tier,
                    status,
                    display_name,
                    real_name,
                    is_deleted,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, 'FREE', 'ACTIVE', ?, ?, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                userId,
                email,
                "$2a$10$seed",
                role,
                displayName,
                displayName + " Real"
        );
    }

    private long insertOrder(String orderNo, String status, int amountFen, Instant createdAt, Instant paidAt, Instant closedAt) {
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
                STUDENT_USER_ID,
                MENTOR_USER_ID,
                amountFen,
                status,
                "Postgres state test question",
                createdAt == null ? null : Timestamp.from(createdAt),
                createdAt == null ? null : Timestamp.from(createdAt),
                paidAt == null ? null : Timestamp.from(paidAt),
                closedAt == null ? null : Timestamp.from(closedAt)
        );
        Long orderId = jdbcTemplate.queryForObject(
                "SELECT id FROM consult_orders WHERE order_no = ?",
                Long.class,
                orderNo
        );
        assertThat(orderId).isNotNull();
        return orderId;
    }

    private void assertStatus(String orderNo, String expectedStatus) {
        String actualStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM consult_orders WHERE order_no = ?",
                String.class,
                orderNo
        );
        assertThat(actualStatus).isEqualTo(expectedStatus);
    }

    private Instant queryInstant(String sql, String orderNo) {
        return jdbcTemplate.query(sql, rs -> rs.next() ? rs.getTimestamp(1).toInstant() : null, orderNo);
    }
}
