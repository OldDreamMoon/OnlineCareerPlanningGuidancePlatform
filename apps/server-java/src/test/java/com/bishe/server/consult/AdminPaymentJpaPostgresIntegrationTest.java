package com.bishe.server.consult;

import com.bishe.server.consult.repository.AdminPaymentRepository;
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
@Import(AdminPaymentRepository.class)
class AdminPaymentJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final long STUDENT_USER_ID = 4101L;
    private static final long MENTOR_USER_ID = 4102L;

    @Autowired
    private AdminPaymentRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM audit_logs WHERE target_id IN (?, ?)", "PG-RECON-001", "PG-RECON-002");
        jdbcTemplate.update("DELETE FROM payment_records WHERE order_no IN (?, ?)", "PG-RECON-001", "PG-RECON-002");
        jdbcTemplate.update("DELETE FROM consult_orders WHERE order_no IN (?, ?)", "PG-RECON-001", "PG-RECON-002");
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", STUDENT_USER_ID, MENTOR_USER_ID);
    }

    @Test
    void repositoryShouldAggregatePaymentReconciliationDataOnPostgres() {
        Instant now = Instant.parse("2030-04-02T08:00:00Z");
        insertUser(STUDENT_USER_ID, "STUDENT", "payment-pg-student@example.com", "Pg Student");
        insertUser(MENTOR_USER_ID, "MENTOR", "payment-pg-mentor@example.com", "Pg Mentor");

        insertOrder("PG-RECON-002", "CANCELED", now.minusSeconds(7200), null, null);
        insertOrder("PG-RECON-001", "PAYING", now.minusSeconds(3600), now.plusSeconds(3600), now.plusSeconds(5400));
        insertPaymentRecord("PG-RECON-001", "INIT", "trade-init", now.minusSeconds(3500));
        insertPaymentRecord("PG-RECON-001", "SUCCESS", "trade-success", now.minusSeconds(1800));
        insertAuditLog("PG-RECON-001", "ADMIN_PAYMENT_MARK_REVIEWED", 9001L, now.minusSeconds(1200));
        insertAuditLog("PG-RECON-001", "ADMIN_PAYMENT_MARK_PAID", 9002L, now.minusSeconds(600));

        assertThat(repository.findOrders(null, null))
                .extracting(AdminPaymentRepository.PaymentReconciliationOrderRow::orderNo)
                .containsExactly("PG-RECON-001", "PG-RECON-002");

        assertThat(repository.findOrders("pg student", ConsultOrderStatus.PAYING))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.orderNo()).isEqualTo("PG-RECON-001");
                    assertThat(row.studentDisplayName()).isEqualTo("Pg Student");
                    assertThat(row.mentorDisplayName()).isEqualTo("Pg Mentor");
                    assertThat(row.latestPaymentStatus()).isEqualTo("SUCCESS");
                    assertThat(row.latestProviderTradeNo()).isEqualTo("trade-success");
                    assertThat(row.hasSuccessPayment()).isTrue();
                    assertThat(row.latestManualActionType()).isEqualTo("ADMIN_PAYMENT_MARK_PAID");
                    assertThat(row.latestManualOperatorUserId()).isEqualTo(9002L);
                    assertThat(row.appointmentStartAt()).isEqualTo(now.plusSeconds(3600));
                    assertThat(row.appointmentEndAt()).isEqualTo(now.plusSeconds(5400));
                });

        assertThat(repository.findPaymentRecords("PG-RECON-001"))
                .extracting(AdminPaymentRepository.PaymentRecordEventRow::status)
                .containsExactly("SUCCESS", "INIT");

        assertThat(repository.findLatestManualAudit("PG-RECON-001"))
                .isPresent()
                .get()
                .satisfies(row -> {
                    assertThat(row.actionType()).isEqualTo("ADMIN_PAYMENT_MARK_PAID");
                    assertThat(row.operatorUserId()).isEqualTo(9002L);
                });
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

    private void insertOrder(
            String orderNo,
            String status,
            Instant createdAt,
            Instant appointmentStartAt,
            Instant appointmentEndAt
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
                    appointment_start_at,
                    appointment_end_at,
                    paid_at,
                    closed_at,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, 7300, ?, 'PG 支付对账验证', ?, ?, NULL, NULL, ?, ?)
                """,
                orderNo,
                STUDENT_USER_ID,
                MENTOR_USER_ID,
                status,
                appointmentStartAt == null ? null : Timestamp.from(appointmentStartAt),
                appointmentEndAt == null ? null : Timestamp.from(appointmentEndAt),
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        );
    }

    private void insertPaymentRecord(String orderNo, String status, String providerTradeNo, Instant createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO payment_records(
                    order_no,
                    channel,
                    mode,
                    provider_trade_no,
                    amount_fen,
                    status,
                    idempotency_key,
                    raw_callback,
                    created_at,
                    updated_at
                ) VALUES (?, 'ALIPAY', 'SANDBOX', ?, 7300, ?, ?, 'callback', ?, ?)
                """,
                orderNo,
                providerTradeNo,
                status,
                providerTradeNo,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        );
    }

    private void insertAuditLog(String orderNo, String actionType, long operatorUserId, Instant createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO audit_logs(
                    trace_id,
                    operator_user_id,
                    action_type,
                    target_type,
                    target_id,
                    detail_json,
                    created_at
                ) VALUES (?, ?, ?, 'ORDER', ?, '{}', ?)
                """,
                "trace-" + actionType,
                operatorUserId,
                actionType,
                orderNo,
                Timestamp.from(createdAt)
        );
    }
}
