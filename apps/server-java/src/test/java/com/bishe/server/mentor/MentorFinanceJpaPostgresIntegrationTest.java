package com.bishe.server.mentor;

import com.bishe.server.mentor.repository.MentorFinanceRepository;
import com.bishe.server.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

@DataJpaTest
@Import(MentorFinanceRepository.class)
class MentorFinanceJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final long MENTOR_USER_ID = 5501L;
    private static final long STUDENT_A_USER_ID = 5502L;
    private static final long STUDENT_B_USER_ID = 5503L;

    @Autowired
    private MentorFinanceRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM mentor_withdrawal_requests WHERE mentor_user_id = ?", MENTOR_USER_ID);
        jdbcTemplate.update("DELETE FROM payment_records WHERE order_no LIKE 'PG-FINANCE-%'");
        jdbcTemplate.update(
                "DELETE FROM consult_orders WHERE mentor_user_id = ? OR student_user_id IN (?, ?)",
                MENTOR_USER_ID,
                STUDENT_A_USER_ID,
                STUDENT_B_USER_ID
        );
        jdbcTemplate.update("DELETE FROM mentor_profiles WHERE user_id = ?", MENTOR_USER_ID);
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?, ?)", MENTOR_USER_ID, STUDENT_A_USER_ID, STUDENT_B_USER_ID);
    }

    @Test
    void repositoryShouldSupportFinanceQueriesOnPostgres() {
        insertUser(MENTOR_USER_ID, "MENTOR", "mentor-finance-pg@example.com", "PG Mentor Finance");
        insertUser(STUDENT_A_USER_ID, "STUDENT", "mentor-finance-pg-student-a@example.com", "Finance Student A");
        insertUser(STUDENT_B_USER_ID, "STUDENT", "mentor-finance-pg-student-b@example.com", "Finance Student B");
        insertMentorProfile(MENTOR_USER_ID, new BigDecimal("4.75"));

        Instant createdAtOne = Instant.parse("2030-05-01T00:00:00Z");
        Instant paidAtOne = Instant.parse("2030-05-01T02:00:00Z");
        Instant closedAtOne = Instant.parse("2030-05-01T04:00:00Z");
        Instant createdAtTwo = Instant.parse("2030-05-02T00:00:00Z");
        Instant paidAtTwo = Instant.parse("2030-05-02T03:00:00Z");
        Instant createdAtThree = Instant.parse("2030-05-03T00:00:00Z");
        Instant paidAtThree = Instant.parse("2030-05-02T05:00:00Z");
        Instant closedAtThree = Instant.parse("2030-05-03T04:00:00Z");

        insertConsultOrder("PG-FINANCE-001", STUDENT_A_USER_ID, MENTOR_USER_ID, 15000, "CLOSED", "PG 简历诊断", createdAtOne, paidAtOne, closedAtOne);
        insertConsultOrder("PG-FINANCE-002", STUDENT_B_USER_ID, MENTOR_USER_ID, 9000, "ANSWERED", "PG mock interview retry", createdAtTwo, paidAtTwo, null);
        insertConsultOrder("PG-FINANCE-003", STUDENT_A_USER_ID, MENTOR_USER_ID, 4000, "REFUNDED", "PG 行为面试回顾", createdAtThree, paidAtThree, closedAtThree);

        insertPaymentRecord("PG-FINANCE-001", "WECHAT", 15000, "pg-finance-001");
        insertPaymentRecord("PG-FINANCE-002", "WECHAT", 9000, "pg-finance-002");
        insertPaymentRecord("PG-FINANCE-003", "ALIPAY", 4000, "pg-finance-003");

        long completedWithdrawalId = repository.createWithdrawal(MENTOR_USER_ID, 6000, "第一笔提现");
        long pendingWithdrawalId = repository.createWithdrawal(MENTOR_USER_ID, 2000, "第二笔提现");
        assertThat(repository.updateWithdrawalStatus(MENTOR_USER_ID, completedWithdrawalId, "COMPLETED", "已打款")).isTrue();

        assertThat(repository.findWithdrawalById(MENTOR_USER_ID, completedWithdrawalId))
                .isPresent()
                .get()
                .satisfies(row -> {
                    assertThat(row.amountFen()).isEqualTo(6000);
                    assertThat(row.status()).isEqualTo("COMPLETED");
                    assertThat(row.note()).isEqualTo("已打款");
                });

        assertThat(repository.findWithdrawalsByMentorUserId(MENTOR_USER_ID))
                .extracting(MentorFinanceRepository.WithdrawalRow::id, MentorFinanceRepository.WithdrawalRow::status)
                .containsExactly(
                        tuple(pendingWithdrawalId, "PENDING"),
                        tuple(completedWithdrawalId, "COMPLETED")
                );

        assertThat(repository.findOverviewMetrics(MENTOR_USER_ID))
                .isPresent()
                .get()
                .satisfies(metrics -> {
                    assertThat(metrics.totalRevenueFen()).isEqualTo(24000);
                    assertThat(metrics.totalRevenueOrderCount()).isEqualTo(2L);
                    assertThat(metrics.completedIncomeFen()).isEqualTo(15000);
                    assertThat(metrics.closedCount()).isEqualTo(1L);
                    assertThat(metrics.refundedAmountFen()).isEqualTo(4000);
                    assertThat(metrics.refundedOrderCount()).isEqualTo(1L);
                    assertThat(metrics.answeredCount()).isEqualTo(1L);
                    assertThat(metrics.avgRating()).isEqualByComparingTo("4.75");
                });

        assertThat(repository.sumCompletedIncomeFen(MENTOR_USER_ID)).isEqualTo(15000);
        assertThat(repository.sumWithdrawalAmountFen(MENTOR_USER_ID, java.util.List.of("PENDING", "PROCESSING"))).isEqualTo(2000);
        assertThat(repository.sumWithdrawalAmountFen(MENTOR_USER_ID, java.util.List.of("COMPLETED"))).isEqualTo(6000);

        assertThat(repository.countFinanceBills(
                MENTOR_USER_ID,
                new MentorFinanceRepository.FinanceBillQuery("finance student b", "ALL", "ALL", 1, 10)
        )).isEqualTo(1L);
        assertThat(repository.countFinanceBills(
                MENTOR_USER_ID,
                new MentorFinanceRepository.FinanceBillQuery(null, "PAID", "ALL", 1, 10)
        )).isEqualTo(1L);
        assertThat(repository.countFinanceBills(
                MENTOR_USER_ID,
                new MentorFinanceRepository.FinanceBillQuery(null, "COMPLETED", "ALL", 1, 10)
        )).isEqualTo(1L);

        assertThat(repository.findFinanceBills(
                MENTOR_USER_ID,
                new MentorFinanceRepository.FinanceBillQuery(null, "ALL", "ALL", 1, 2)
        ))
                .extracting(
                        MentorFinanceRepository.FinanceBillRow::orderNo,
                        MentorFinanceRepository.FinanceBillRow::counterpartDisplayName,
                        MentorFinanceRepository.FinanceBillRow::paymentMode
                )
                .containsExactly(
                        tuple("PG-FINANCE-003", "Finance Student A", "ALIPAY"),
                        tuple("PG-FINANCE-002", "Finance Student B", "WECHAT")
                );

        assertThat(repository.findTrendPoints(
                MENTOR_USER_ID,
                Instant.parse("2030-05-01T00:00:00Z"),
                Instant.parse("2030-05-04T00:00:00Z")
        ))
                .extracting(
                        MentorFinanceRepository.FinanceTrendPointRow::bucketDate,
                        MentorFinanceRepository.FinanceTrendPointRow::paidFen,
                        MentorFinanceRepository.FinanceTrendPointRow::refundedFen
                )
                .containsExactly(
                        tuple(java.time.LocalDate.parse("2030-05-01"), 15000, 0),
                        tuple(java.time.LocalDate.parse("2030-05-02"), 9000, 0),
                        tuple(java.time.LocalDate.parse("2030-05-03"), 0, 4000)
                );
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
                displayName
        );
    }

    private void insertMentorProfile(long userId, BigDecimal avgRating) {
        jdbcTemplate.update(
                """
                INSERT INTO mentor_profiles(
                    user_id,
                    approval_status,
                    avg_rating,
                    created_at,
                    updated_at
                ) VALUES (?, 'APPROVED', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                userId,
                avgRating
        );
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
