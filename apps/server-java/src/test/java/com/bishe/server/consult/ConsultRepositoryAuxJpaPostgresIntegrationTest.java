package com.bishe.server.consult;

import com.bishe.server.consult.repository.ConsultRepository;
import com.bishe.server.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ConsultRepository 中已抽离辅助读写在 PostgreSQL 下的最小验证。
 */
@DataJpaTest
@Import(ConsultRepository.class)
class ConsultRepositoryAuxJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final long MENTOR_USER_ID = 6901L;
    private static final long STUDENT_USER_ID = 6902L;
    private static final String ORDER_NO = "PG-CONSULT-AUX-001";

    @Autowired
    private ConsultRepository consultRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM payment_records WHERE order_no = ?", ORDER_NO);
        jdbcTemplate.update("DELETE FROM consult_orders WHERE order_no = ?", ORDER_NO);
        jdbcTemplate.update("DELETE FROM audit_logs WHERE target_type = 'ORDER' AND target_id = ?", ORDER_NO);
        jdbcTemplate.update("DELETE FROM mentor_profiles WHERE user_id = ?", MENTOR_USER_ID);
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", MENTOR_USER_ID, STUDENT_USER_ID);
    }

    @Test
    void repositoryShouldUseJpaForPaymentReviewAndAuditHelpers() {
        insertUser(MENTOR_USER_ID, "MENTOR", "consult-aux-mentor@example.com", "Consult Aux Mentor");
        insertUser(STUDENT_USER_ID, "STUDENT", "consult-aux-student@example.com", "Consult Aux Student");
        insertMentorProfile(MENTOR_USER_ID, new BigDecimal("4.25"));
        long orderId = insertOrder(ORDER_NO, STUDENT_USER_ID, MENTOR_USER_ID);

        consultRepository.insertPaymentRecord(
                orderId,
                ORDER_NO,
                "ALIPAY",
                "SANDBOX",
                "ALI-AUX-SUCCESS",
                9900,
                "SUCCESS",
                "IDEMP-AUX-SUCCESS",
                "{\"phase\":\"success\"}"
        );
        consultRepository.insertPaymentRecord(
                orderId,
                ORDER_NO,
                "ALIPAY",
                "SANDBOX",
                "ALI-AUX-PENDING",
                9900,
                "PENDING",
                "IDEMP-AUX-PENDING",
                "{\"phase\":\"pending\"}"
        );

        assertThat(consultRepository.findLatestPaymentRecord(orderId, ORDER_NO))
                .isPresent()
                .get()
                .satisfies(record -> {
                    assertThat(record.status()).isEqualTo("PENDING");
                    assertThat(record.providerTradeNo()).isEqualTo("ALI-AUX-PENDING");
                });
        Long paymentOrderId = jdbcTemplate.queryForObject(
                "SELECT order_id FROM payment_records WHERE order_no = ? ORDER BY id DESC LIMIT 1",
                Long.class,
                ORDER_NO
        );
        assertThat(paymentOrderId).isNotNull();
        assertThat(consultRepository.findPaymentRecordByIdempotencyKey("IDEMP-AUX-SUCCESS"))
                .isPresent()
                .get()
                .satisfies(record -> {
                    assertThat(record.status()).isEqualTo("SUCCESS");
                    assertThat(record.amountFen()).isEqualTo(9900);
                });
        assertThat(consultRepository.findLatestPaymentRecordByStatus(orderId, ORDER_NO, "SUCCESS"))
                .isPresent()
                .get()
                .extracting(ConsultRepository.PaymentRecordRow::providerTradeNo)
                .isEqualTo("ALI-AUX-SUCCESS");

        consultRepository.createReview(orderId, ORDER_NO, 5, "讲解很具体");
        Integer reviewRating = jdbcTemplate.queryForObject(
                "SELECT review_rating FROM consult_orders WHERE order_no = ?",
                Integer.class,
                ORDER_NO
        );
        assertThat(reviewRating).isEqualTo(5);
        assertThat(consultRepository.deleteReview(orderId, ORDER_NO)).isTrue();
        reviewRating = jdbcTemplate.queryForObject(
                "SELECT review_rating FROM consult_orders WHERE order_no = ?",
                Integer.class,
                ORDER_NO
        );
        assertThat(reviewRating).isNull();

        assertThat(consultRepository.findMentorAverageRating(MENTOR_USER_ID))
                .hasValueSatisfying(value -> assertThat(value).isEqualByComparingTo("4.25"));

        consultRepository.insertAuditLog(
                "TRACE-AUX-001",
                STUDENT_USER_ID,
                "PAYMENT_SYNC",
                "ORDER",
                ORDER_NO,
                "{\"source\":\"pg-test\"}"
        );
        assertThat(consultRepository.findLatestAuditLog("PAYMENT_SYNC", "ORDER", ORDER_NO))
                .isPresent()
                .get()
                .satisfies(log -> {
                    assertThat(log.operatorUserId()).isEqualTo(STUDENT_USER_ID);
                    assertThat(log.detailJson()).contains("pg-test");
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
                displayName + " Real"
        );
    }

    private void insertMentorProfile(long mentorUserId, BigDecimal avgRating) {
        jdbcTemplate.update(
                """
                INSERT INTO mentor_profiles(
                    user_id,
                    show_real_name,
                    expertise_tags,
                    service_scenes,
                    bio,
                    price_fen,
                    is_available,
                    approval_status,
                    total_orders,
                    avg_rating,
                    created_at,
                    updated_at
                ) VALUES (?, FALSE, ?, ?, ?, ?, TRUE, 'APPROVED', 0, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                mentorUserId,
                "职业规划,模拟面试",
                "模拟面试复盘,简历诊断",
                "辅助仓储测试导师资料",
                9900,
                avgRating
        );
    }

    private long insertOrder(String orderNo, long studentUserId, long mentorUserId) {
        jdbcTemplate.update(
                """
                INSERT INTO consult_orders(
                    order_no,
                    student_user_id,
                    mentor_user_id,
                    amount_fen,
                    status,
                    question_text,
                    closed_at,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, 9900, 'CLOSED', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                orderNo,
                studentUserId,
                mentorUserId,
                "辅助仓储测试问题"
        );
        Long orderId = jdbcTemplate.queryForObject(
                "SELECT id FROM consult_orders WHERE order_no = ?",
                Long.class,
                orderNo
        );
        assertThat(orderId).isNotNull();
        return orderId;
    }
}
