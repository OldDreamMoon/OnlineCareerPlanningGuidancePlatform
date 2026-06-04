package com.bishe.server.consult;

import com.bishe.server.consult.repository.ConsultAfterSalesRepository;
import com.bishe.server.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(ConsultAfterSalesRepository.class)
class ConsultAfterSalesJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final long STUDENT_USER_ID = 4201L;
    private static final long MENTOR_USER_ID = 4202L;
    private static final long ADMIN_USER_ID = 4203L;

    @Autowired
    private ConsultAfterSalesRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM consult_after_sales_requests WHERE order_no IN (?, ?)", "PG-AFTER-001", "PG-AFTER-002");
        jdbcTemplate.update("DELETE FROM consult_orders WHERE order_no IN (?, ?)", "PG-AFTER-001", "PG-AFTER-002");
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?, ?)", STUDENT_USER_ID, MENTOR_USER_ID, ADMIN_USER_ID);
    }

    @Test
    void repositoryShouldSupportAfterSalesQueriesOnPostgres() {
        insertUser(STUDENT_USER_ID, "STUDENT", "after-sales-pg-student@example.com", "Pg Student");
        insertUser(MENTOR_USER_ID, "MENTOR", "after-sales-pg-mentor@example.com", "Pg Mentor");
        insertUser(ADMIN_USER_ID, "ADMIN", "after-sales-pg-admin@example.com", "Pg Admin");
        insertOrder("PG-AFTER-001", "CLOSED", Instant.parse("2030-04-03T08:00:00Z"));
        insertOrder("PG-AFTER-002", "REFUNDED", Instant.parse("2030-04-03T09:00:00Z"));

        long pendingRequestId = repository.createRequest(
                "PG-AFTER-001",
                STUDENT_USER_ID,
                ConsultAfterSalesRequestType.REFUND,
                "学生申请退款",
                false
        );
        long approvedRequestId = repository.createRequest(
                "PG-AFTER-002",
                STUDENT_USER_ID,
                ConsultAfterSalesRequestType.REFUND,
                "导师超时未答，系统自动退款",
                true
        );
        Instant reviewedAt = Instant.parse("2030-04-03T10:00:00Z");
        assertThat(repository.reviewRequest(
                approvedRequestId,
                ConsultAfterSalesRequestStatus.APPROVED,
                "审核通过",
                ADMIN_USER_ID,
                reviewedAt
        )).isTrue();
        assertThat(repository.reviewRequest(
                approvedRequestId,
                ConsultAfterSalesRequestStatus.REJECTED,
                "重复审核不应成功",
                ADMIN_USER_ID,
                reviewedAt.plusSeconds(60)
        )).isFalse();

        assertThat(repository.findById(pendingRequestId))
                .isPresent()
                .get()
                .satisfies(row -> {
                    assertThat(row.orderNo()).isEqualTo("PG-AFTER-001");
                    assertThat(row.requestType()).isEqualTo(ConsultAfterSalesRequestType.REFUND);
                    assertThat(row.status()).isEqualTo(ConsultAfterSalesRequestStatus.PENDING);
                    assertThat(row.reason()).isEqualTo("学生申请退款");
                    assertThat(row.autoTriggered()).isFalse();
                });

        assertThat(repository.existsPendingRequest("PG-AFTER-001")).isTrue();
        assertThat(repository.findPendingRequest("PG-AFTER-001"))
                .isPresent()
                .get()
                .extracting(ConsultAfterSalesRepository.AfterSalesRequestRow::id)
                .isEqualTo(pendingRequestId);
        assertThat(repository.existsPendingRequest("PG-AFTER-002")).isFalse();

        assertThat(repository.findByOrderNo("PG-AFTER-002"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.id()).isEqualTo(approvedRequestId);
                    assertThat(row.status()).isEqualTo(ConsultAfterSalesRequestStatus.APPROVED);
                    assertThat(row.reviewNote()).isEqualTo("审核通过");
                    assertThat(row.reviewerUserId()).isEqualTo(ADMIN_USER_ID);
                    assertThat(row.reviewedAt()).isEqualTo(reviewedAt);
                    assertThat(row.autoTriggered()).isTrue();
                });

        assertThat(repository.findAdminRequests(null, null, 1, 10))
                .extracting(ConsultAfterSalesRepository.AdminAfterSalesRequestRow::id)
                .containsExactly(pendingRequestId, approvedRequestId);

        assertThat(repository.findAdminRequests("pg student", ConsultAfterSalesRequestStatus.PENDING, 1, 10))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.id()).isEqualTo(pendingRequestId);
                    assertThat(row.orderStatus()).isEqualTo(ConsultOrderStatus.CLOSED);
                    assertThat(row.studentDisplayName()).isEqualTo("Pg Student");
                    assertThat(row.mentorDisplayName()).isEqualTo("Pg Mentor");
                });

        assertThat(repository.countAdminRequests(null, ConsultAfterSalesRequestStatus.APPROVED)).isEqualTo(1L);
        assertThat(repository.countAdminRequests("pg mentor", null)).isEqualTo(2L);
        Long afterSalesOrderId = jdbcTemplate.queryForObject(
                "SELECT order_id FROM consult_after_sales_requests WHERE id = ?",
                Long.class,
                pendingRequestId
        );
        assertThat(afterSalesOrderId).isNotNull();
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

    private void insertOrder(String orderNo, String status, Instant createdAt) {
        Timestamp timestamp = Timestamp.from(createdAt);
        jdbcTemplate.update(
                """
                INSERT INTO consult_orders(
                    order_no,
                    student_user_id,
                    mentor_user_id,
                    amount_fen,
                    status,
                    question_text,
                    appointment_start_at,
                    appointment_end_at,
                    paid_at,
                    closed_at,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, 8800, ?, 'PG 售后申请验证', NULL, NULL, ?, ?, ?, ?)
                """,
                orderNo,
                STUDENT_USER_ID,
                MENTOR_USER_ID,
                status,
                timestamp,
                "CLOSED".equals(status) || "REFUNDED".equals(status) ? timestamp : null,
                timestamp,
                timestamp
        );
    }
}
