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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ConsultRepository 中订单详情与学生/导师订单列表读取在 PostgreSQL 下的最小验证。
 */
@DataJpaTest
@Import(ConsultRepository.class)
class ConsultRepositoryOrderReadJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final long MENTOR_USER_ID = 7041L;
    private static final long PRIMARY_STUDENT_USER_ID = 7042L;
    private static final long SECONDARY_STUDENT_USER_ID = 7043L;
    private static final long WORKBENCH_MENTOR_USER_ID = 7051L;
    private static final long EXPIRED_STUDENT_USER_ID = 7052L;
    private static final long EXPIRING_STUDENT_USER_ID = 7053L;
    private static final long ANSWERED_STUDENT_USER_ID = 7054L;
    private static final long REFUNDED_STUDENT_USER_ID = 7055L;
    private static final long AFTER_SALES_STUDENT_USER_ID = 7056L;
    private static final long OTHER_MENTOR_USER_ID = 7057L;
    private static final long OTHER_MENTOR_STUDENT_USER_ID = 7058L;

    private static final String DETAIL_ORDER_NO = "PG-CONSULT-READ-001";
    private static final String STUDENT_CREATED_ORDER_NO = "PG-CONSULT-READ-002";
    private static final String MENTOR_PAID_ORDER_NO = "PG-CONSULT-READ-003";
    private static final String EXPIRED_ORDER_NO = "PG-CONSULT-WB-001";
    private static final String EXPIRING_ORDER_NO = "PG-CONSULT-WB-002";
    private static final String ANSWERED_ORDER_NO = "PG-CONSULT-WB-003";
    private static final String REFUNDED_ORDER_NO = "PG-CONSULT-WB-004";
    private static final String AFTER_SALES_ORDER_NO = "PG-CONSULT-WB-005";
    private static final String OTHER_MENTOR_ORDER_NO = "PG-CONSULT-WB-006";

    @Autowired
    private ConsultRepository consultRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                "DELETE FROM payment_records WHERE order_no IN (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                DETAIL_ORDER_NO,
                STUDENT_CREATED_ORDER_NO,
                MENTOR_PAID_ORDER_NO,
                EXPIRED_ORDER_NO,
                EXPIRING_ORDER_NO,
                ANSWERED_ORDER_NO,
                REFUNDED_ORDER_NO,
                AFTER_SALES_ORDER_NO,
                OTHER_MENTOR_ORDER_NO
        );
        jdbcTemplate.update(
                "DELETE FROM consult_after_sales_requests WHERE order_no IN (?, ?, ?, ?, ?, ?)",
                EXPIRED_ORDER_NO,
                EXPIRING_ORDER_NO,
                ANSWERED_ORDER_NO,
                REFUNDED_ORDER_NO,
                AFTER_SALES_ORDER_NO,
                OTHER_MENTOR_ORDER_NO
        );
        jdbcTemplate.update(
                "DELETE FROM consult_orders WHERE order_no IN (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                DETAIL_ORDER_NO,
                STUDENT_CREATED_ORDER_NO,
                MENTOR_PAID_ORDER_NO,
                EXPIRED_ORDER_NO,
                EXPIRING_ORDER_NO,
                ANSWERED_ORDER_NO,
                REFUNDED_ORDER_NO,
                AFTER_SALES_ORDER_NO,
                OTHER_MENTOR_ORDER_NO
        );
        jdbcTemplate.update(
                "DELETE FROM users WHERE id IN (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                MENTOR_USER_ID,
                PRIMARY_STUDENT_USER_ID,
                SECONDARY_STUDENT_USER_ID,
                WORKBENCH_MENTOR_USER_ID,
                EXPIRED_STUDENT_USER_ID,
                EXPIRING_STUDENT_USER_ID,
                ANSWERED_STUDENT_USER_ID,
                REFUNDED_STUDENT_USER_ID,
                AFTER_SALES_STUDENT_USER_ID,
                OTHER_MENTOR_USER_ID,
                OTHER_MENTOR_STUDENT_USER_ID
        );
    }

    @Test
    void repositoryShouldUseJpaForOrderDetailAndParticipantLists() {
        insertUser(MENTOR_USER_ID, "MENTOR", "consult-read-mentor@example.com", "Read Mentor");
        insertUser(PRIMARY_STUDENT_USER_ID, "STUDENT", "consult-read-student@example.com", "Read Student");
        insertUser(SECONDARY_STUDENT_USER_ID, "STUDENT", "consult-read-student-2@example.com", "Second Student");

        insertOrder(
                DETAIL_ORDER_NO,
                PRIMARY_STUDENT_USER_ID,
                MENTOR_USER_ID,
                19900,
                "CLOSED",
                Instant.parse("2026-04-17T08:00:00Z"),
                Instant.parse("2026-04-17T09:00:00Z"),
                Instant.parse("2026-04-17T10:00:00Z"),
                Instant.parse("2026-04-17T11:00:00Z"),
                "RESUME_DIAGNOSIS",
                "MENTOR_MARKETPLACE",
                "请帮我诊断简历和项目表达",
                "{\"primaryConcern\":\"resume\"}",
                "希望快速定位面试转化率低的原因",
                "[\"项目亮点\",\"结果量化\"]",
                "[\"简历改写方向\",\"投递建议\"]",
                "RESUME,JOB_DESCRIPTION",
                "{\"scene\":\"简历诊断\"}"
        );
        insertOrder(
                STUDENT_CREATED_ORDER_NO,
                PRIMARY_STUDENT_USER_ID,
                MENTOR_USER_ID,
                12900,
                "CREATED",
                Instant.parse("2026-04-17T12:00:00Z"),
                null,
                null,
                null,
                "PROJECT_REVIEW",
                "MENTOR_PROFILE",
                "请看看我的项目经历",
                null,
                null,
                null,
                null,
                "PROJECT_MATERIAL",
                null
        );
        insertOrder(
                MENTOR_PAID_ORDER_NO,
                SECONDARY_STUDENT_USER_ID,
                MENTOR_USER_ID,
                15900,
                "PAID",
                Instant.parse("2026-04-17T11:30:00Z"),
                Instant.parse("2026-04-17T12:10:00Z"),
                null,
                null,
                "INTERVIEW_SIMULATION",
                "MENTOR_MARKETPLACE",
                "想模拟一次岗位一面",
                null,
                "希望提前演练高频追问",
                "[\"自我介绍\",\"项目追问\"]",
                "[\"模拟题单\"]",
                "RESUME",
                "{\"scene\":\"模拟面试\"}"
        );

        insertPaymentRecord(DETAIL_ORDER_NO, "MOCK");
        insertPaymentRecord(DETAIL_ORDER_NO, "ALIPAY");
        insertPaymentRecord(MENTOR_PAID_ORDER_NO, "SANDBOX");
        insertReview(DETAIL_ORDER_NO, PRIMARY_STUDENT_USER_ID, MENTOR_USER_ID, 5, "建议很具体");

        ConsultRepository.OrderDetailRow detail = consultRepository.findOrderDetail(DETAIL_ORDER_NO).orElseThrow();
        assertThat(detail.studentDisplayName()).isEqualTo("Read Student");
        assertThat(detail.mentorDisplayName()).isEqualTo("Read Mentor");
        assertThat(detail.sceneCode()).isEqualTo("RESUME_DIAGNOSIS");
        assertThat(detail.sourcePage()).isEqualTo("MENTOR_MARKETPLACE");
        assertThat(detail.questionPayloadJson()).isEqualTo("{\"primaryConcern\":\"resume\"}");
        assertThat(detail.problemSummary()).isEqualTo("希望快速定位面试转化率低的原因");
        assertThat(detail.coreQuestionsJson()).isEqualTo("[\"项目亮点\",\"结果量化\"]");
        assertThat(detail.expectedOutcomesJson()).isEqualTo("[\"简历改写方向\",\"投递建议\"]");
        assertThat(detail.selectedMaterialTypes()).isEqualTo("RESUME,JOB_DESCRIPTION");
        assertThat(detail.prepSheetSnapshotJson()).isEqualTo("{\"scene\":\"简历诊断\"}");
        assertThat(detail.paymentMode()).isEqualTo("ALIPAY");
        assertThat(detail.reviewRating()).isEqualTo(5);
        assertThat(detail.reviewComment()).isEqualTo("建议很具体");
        assertThat(detail.reviewCreatedAt()).isNotNull();

        List<ConsultRepository.OrderListRow> studentOrders = consultRepository.findOrdersForStudent(
                PRIMARY_STUDENT_USER_ID,
                null,
                1,
                10
        );
        assertThat(studentOrders).extracting(ConsultRepository.OrderListRow::orderNo)
                .containsExactly(STUDENT_CREATED_ORDER_NO, DETAIL_ORDER_NO);
        assertThat(studentOrders).extracting(ConsultRepository.OrderListRow::counterpartDisplayName)
                .containsExactly("Read Mentor", "Read Mentor");
        assertThat(studentOrders).extracting(ConsultRepository.OrderListRow::paymentMode)
                .containsExactly(null, "ALIPAY");

        assertThat(consultRepository.countOrdersForStudent(PRIMARY_STUDENT_USER_ID, null)).isEqualTo(2);
        assertThat(consultRepository.countOrdersForStudent(PRIMARY_STUDENT_USER_ID, ConsultOrderStatus.CLOSED)).isEqualTo(1);

        List<ConsultRepository.OrderListRow> mentorOrders = consultRepository.findOrdersForMentor(
                MENTOR_USER_ID,
                null,
                1,
                10
        );
        assertThat(mentorOrders).extracting(ConsultRepository.OrderListRow::orderNo)
                .containsExactly(STUDENT_CREATED_ORDER_NO, MENTOR_PAID_ORDER_NO, DETAIL_ORDER_NO);
        assertThat(mentorOrders).extracting(ConsultRepository.OrderListRow::counterpartDisplayName)
                .containsExactly("Read Student", "Second Student", "Read Student");
        assertThat(mentorOrders).extracting(ConsultRepository.OrderListRow::paymentMode)
                .containsExactly(null, "SANDBOX", "ALIPAY");

        assertThat(consultRepository.countOrdersForMentor(MENTOR_USER_ID, null)).isEqualTo(3);
        assertThat(consultRepository.countOrdersForMentor(MENTOR_USER_ID, ConsultOrderStatus.PAID)).isEqualTo(1);

        assertThat(consultRepository.findRecentOrdersForMentor(MENTOR_USER_ID, 2))
                .extracting(ConsultRepository.OrderListRow::orderNo)
                .containsExactly(STUDENT_CREATED_ORDER_NO, MENTOR_PAID_ORDER_NO);
    }

    @Test
    void repositoryShouldUseJpaForMentorWorkbenchAndAdminOrders() {
        Instant now = Instant.now();
        int replyTimeoutHours = 24;
        int expiringWindowHours = 12;

        insertUser(WORKBENCH_MENTOR_USER_ID, "MENTOR", "consult-workbench-mentor@example.com", "Workbench Mentor");
        insertUser(EXPIRED_STUDENT_USER_ID, "STUDENT", "consult-workbench-expired@example.com", "Expired Student");
        insertUser(EXPIRING_STUDENT_USER_ID, "STUDENT", "consult-workbench-expiring@example.com", "Expiring Student");
        insertUser(ANSWERED_STUDENT_USER_ID, "STUDENT", "consult-workbench-answered@example.com", "Answered Student");
        insertUser(REFUNDED_STUDENT_USER_ID, "STUDENT", "consult-workbench-refunded@example.com", "Refunded Student");
        insertUser(AFTER_SALES_STUDENT_USER_ID, "STUDENT", "consult-workbench-after-sales@example.com", "After Sales Student");
        insertUser(OTHER_MENTOR_USER_ID, "MENTOR", "consult-workbench-other-mentor@example.com", "Other Mentor");
        insertUser(OTHER_MENTOR_STUDENT_USER_ID, "STUDENT", "consult-workbench-other-student@example.com", "Other Student");

        insertOrder(
                EXPIRED_ORDER_NO,
                EXPIRED_STUDENT_USER_ID,
                WORKBENCH_MENTOR_USER_ID,
                19900,
                "PAID",
                now.minusSeconds(36 * 3600),
                now.minusSeconds(30 * 3600),
                null,
                null,
                "RESUME_DIAGNOSIS",
                "MENTOR_WORKBENCH",
                "导师回复已超时的订单",
                null,
                null,
                null,
                null,
                "RESUME",
                null
        );
        insertOrder(
                EXPIRING_ORDER_NO,
                EXPIRING_STUDENT_USER_ID,
                WORKBENCH_MENTOR_USER_ID,
                15900,
                "PAID",
                now.minusSeconds(22 * 3600),
                now.minusSeconds(20 * 3600),
                null,
                now.plusSeconds(2 * 24 * 3600),
                "INTERVIEW_SIMULATION",
                "MENTOR_WORKBENCH",
                "即将到期的订单",
                null,
                null,
                null,
                null,
                "RESUME",
                null
        );
        insertOrder(
                ANSWERED_ORDER_NO,
                ANSWERED_STUDENT_USER_ID,
                WORKBENCH_MENTOR_USER_ID,
                14900,
                "ANSWERED",
                now.minusSeconds(19 * 3600),
                now.minusSeconds(18 * 3600),
                null,
                null,
                "PROJECT_REVIEW",
                "MENTOR_WORKBENCH",
                "等待学生确认关闭",
                null,
                null,
                null,
                null,
                "PROJECT_MATERIAL",
                null
        );
        insertOrder(
                REFUNDED_ORDER_NO,
                REFUNDED_STUDENT_USER_ID,
                WORKBENCH_MENTOR_USER_ID,
                18900,
                "REFUNDED",
                now.minusSeconds(49 * 3600),
                now.minusSeconds(48 * 3600),
                now.minusSeconds(47 * 3600),
                null,
                "CAREER_PLAN",
                "ADMIN_CONSOLE",
                "管理员已退款的订单",
                null,
                null,
                null,
                null,
                "ROADMAP",
                null
        );
        insertOrder(
                AFTER_SALES_ORDER_NO,
                AFTER_SALES_STUDENT_USER_ID,
                WORKBENCH_MENTOR_USER_ID,
                12900,
                "CLOSED",
                now.minusSeconds(13 * 3600),
                now.minusSeconds(12 * 3600),
                now.minusSeconds(11 * 3600),
                now.plusSeconds(3 * 24 * 3600),
                "PROJECT_REVIEW",
                "MENTOR_WORKBENCH",
                "已发起售后的订单",
                null,
                null,
                null,
                null,
                "PROJECT_MATERIAL",
                null
        );
        insertOrder(
                OTHER_MENTOR_ORDER_NO,
                OTHER_MENTOR_STUDENT_USER_ID,
                OTHER_MENTOR_USER_ID,
                9900,
                "PAID",
                now.minusSeconds(2 * 3600),
                now.minusSeconds(90 * 60),
                null,
                null,
                "PROJECT_REVIEW",
                "MENTOR_WORKBENCH",
                "其他导师订单",
                null,
                null,
                null,
                null,
                "PROJECT_MATERIAL",
                null
        );

        insertPaymentRecord(EXPIRED_ORDER_NO, "WECHAT");
        insertPaymentRecord(EXPIRING_ORDER_NO, "ALIPAY");
        insertPaymentRecord(ANSWERED_ORDER_NO, "ALIPAY");
        insertPaymentRecord(REFUNDED_ORDER_NO, "WECHAT");
        insertPaymentRecord(REFUNDED_ORDER_NO, "SANDBOX");
        insertPaymentRecord(OTHER_MENTOR_ORDER_NO, "CREDIT_CARD");
        insertAfterSalesRequest(AFTER_SALES_ORDER_NO, AFTER_SALES_STUDENT_USER_ID, "PENDING", "希望继续申诉");

        ConsultRepository.MentorWorkbenchSummaryRow summary = consultRepository.summarizeMentorWorkbench(
                WORKBENCH_MENTOR_USER_ID,
                replyTimeoutHours,
                expiringWindowHours
        );
        assertThat(summary.pendingReplyCount()).isEqualTo(2);
        assertThat(summary.expiringSoonCount()).isEqualTo(2);
        assertThat(summary.waitingConfirmationCount()).isEqualTo(1);
        assertThat(summary.afterSalesImpactCount()).isEqualTo(2);

        assertThat(consultRepository.findMentorWorkbenchPaymentModes(WORKBENCH_MENTOR_USER_ID))
                .containsExactly("ALIPAY", "SANDBOX", "WECHAT");

        ConsultRepository.MentorWorkbenchQuery priorityQuery = new ConsultRepository.MentorWorkbenchQuery(
                null,
                null,
                "ALL",
                "ALL",
                null,
                "ALL",
                "PRIORITY",
                1,
                10
        );
        List<ConsultRepository.MentorWorkbenchOrderRow> workbenchOrders = consultRepository.findMentorWorkbenchOrders(
                WORKBENCH_MENTOR_USER_ID,
                priorityQuery,
                replyTimeoutHours,
                expiringWindowHours
        );
        assertThat(workbenchOrders).extracting(ConsultRepository.MentorWorkbenchOrderRow::orderNo)
                .containsExactly(
                        EXPIRED_ORDER_NO,
                        EXPIRING_ORDER_NO,
                        REFUNDED_ORDER_NO,
                        AFTER_SALES_ORDER_NO,
                        ANSWERED_ORDER_NO
                );
        assertThat(workbenchOrders.get(0).mentorReplyDeadlineAt()).isNotNull();
        assertThat(workbenchOrders.get(3).afterSalesImpact()).isTrue();
        assertThat(workbenchOrders.get(3).pendingAfterSales()).isTrue();
        assertThat(workbenchOrders.get(3).latestAfterSalesStatus()).isEqualTo("PENDING");

        ConsultRepository.MentorWorkbenchQuery keywordQuery = new ConsultRepository.MentorWorkbenchQuery(
                "after sales student",
                null,
                "ALL",
                "ALL",
                null,
                "ALL",
                "PRIORITY",
                1,
                10
        );
        assertThat(consultRepository.countMentorWorkbenchOrders(
                WORKBENCH_MENTOR_USER_ID,
                keywordQuery,
                replyTimeoutHours,
                expiringWindowHours
        )).isEqualTo(1);

        ConsultRepository.MentorWorkbenchQuery unsetPaymentQuery = new ConsultRepository.MentorWorkbenchQuery(
                null,
                null,
                "ALL",
                "ALL",
                "UNSET",
                "ALL",
                "PRIORITY",
                1,
                10
        );
        assertThat(consultRepository.countMentorWorkbenchOrders(
                WORKBENCH_MENTOR_USER_ID,
                unsetPaymentQuery,
                replyTimeoutHours,
                expiringWindowHours
        )).isEqualTo(1);

        ConsultRepository.MentorWorkbenchQuery afterSalesRiskQuery = new ConsultRepository.MentorWorkbenchQuery(
                null,
                null,
                "ALL",
                "ALL",
                null,
                "AFTER_SALES",
                "PRIORITY",
                1,
                10
        );
        assertThat(consultRepository.countMentorWorkbenchOrders(
                WORKBENCH_MENTOR_USER_ID,
                afterSalesRiskQuery,
                replyTimeoutHours,
                expiringWindowHours
        )).isEqualTo(2);

        assertThat(consultRepository.countAdminOrders("workbench mentor", null)).isEqualTo(5);
        assertThat(consultRepository.countAdminOrders("after sales student", null)).isEqualTo(1);
        assertThat(consultRepository.countAdminOrders(null, ConsultOrderStatus.REFUNDED)).isEqualTo(1);

        List<ConsultRepository.AdminOrderListRow> adminOrders = consultRepository.findAdminOrders(null, null, 1, 10);
        assertThat(adminOrders).extracting(ConsultRepository.AdminOrderListRow::orderNo)
                .containsExactly(
                        OTHER_MENTOR_ORDER_NO,
                        AFTER_SALES_ORDER_NO,
                        ANSWERED_ORDER_NO,
                        EXPIRING_ORDER_NO,
                        EXPIRED_ORDER_NO,
                        REFUNDED_ORDER_NO
                );
        assertThat(adminOrders.get(1).studentDisplayName()).isEqualTo("After Sales Student");
        assertThat(adminOrders.get(1).mentorDisplayName()).isEqualTo("Workbench Mentor");

        List<ConsultRepository.AdminOrderListRow> refundedAdminOrders = consultRepository.findAdminOrders(
                null,
                ConsultOrderStatus.REFUNDED,
                1,
                10
        );
        assertThat(refundedAdminOrders).hasSize(1);
        assertThat(refundedAdminOrders.get(0).orderNo()).isEqualTo(REFUNDED_ORDER_NO);
        assertThat(refundedAdminOrders.get(0).paymentMode()).isEqualTo("SANDBOX");
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

    private void insertOrder(
            String orderNo,
            long studentUserId,
            long mentorUserId,
            int amountFen,
            String status,
            Instant createdAt,
            Instant paidAt,
            Instant closedAt,
            Instant appointmentStartAt,
            String sceneCode,
            String sourcePage,
            String questionText,
            String questionPayloadJson,
            String problemSummary,
            String coreQuestionsJson,
            String expectedOutcomesJson,
            String selectedMaterialTypes,
            String prepSheetSnapshotJson
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO consult_orders(
                    order_no,
                    student_user_id,
                    mentor_user_id,
                    scene_code,
                    source_page,
                    amount_fen,
                    status,
                    question_text,
                    question_payload_json,
                    problem_summary,
                    core_questions_json,
                    expected_outcomes_json,
                    selected_material_types,
                    prep_sheet_snapshot_json,
                    appointment_start_at,
                    appointment_end_at,
                    paid_at,
                    closed_at,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                orderNo,
                studentUserId,
                mentorUserId,
                sceneCode,
                sourcePage,
                amountFen,
                status,
                questionText,
                questionPayloadJson,
                problemSummary,
                coreQuestionsJson,
                expectedOutcomesJson,
                selectedMaterialTypes,
                prepSheetSnapshotJson,
                appointmentStartAt == null ? null : Timestamp.from(appointmentStartAt),
                appointmentStartAt == null ? null : Timestamp.from(appointmentStartAt.plusSeconds(3600)),
                paidAt == null ? null : Timestamp.from(paidAt),
                closedAt == null ? null : Timestamp.from(closedAt),
                Timestamp.from(createdAt),
                Timestamp.from(createdAt.plusSeconds(60))
        );
    }

    private void insertPaymentRecord(String orderNo, String mode) {
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
                ) VALUES (?, 'ALIPAY', ?, ?, 100, 'SUCCESS', ?, '{}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                orderNo,
                mode,
                "TRADE-" + orderNo + "-" + mode,
                "IDEMP-" + orderNo + "-" + mode
        );
    }

    private void insertAfterSalesRequest(String orderNo, long requesterUserId, String status, String reason) {
        jdbcTemplate.update(
                """
                INSERT INTO consult_after_sales_requests(
                    order_no,
                    requester_user_id,
                    request_type,
                    status,
                    reason,
                    auto_triggered,
                    created_at,
                    updated_at
                ) VALUES (?, ?, 'REFUND', ?, ?, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                orderNo,
                requesterUserId,
                status,
                reason
        );
    }

    private void insertReview(String orderNo, long studentUserId, long mentorUserId, int rating, String comment) {
        jdbcTemplate.update(
                """
                UPDATE consult_orders
                   SET review_rating = ?,
                       review_comment = ?,
                       review_created_at = CURRENT_TIMESTAMP,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE order_no = ?
                """,
                rating,
                comment
                ,
                orderNo
        );
    }
}
