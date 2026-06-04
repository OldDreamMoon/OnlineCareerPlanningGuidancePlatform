package com.bishe.server.mentor;

import com.bishe.server.mentor.repository.AdminMentorOpsRepository;
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
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(AdminMentorOpsRepository.class)
class AdminMentorOpsJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final long MENTOR_PRIMARY_USER_ID = 6601L;
    private static final long MENTOR_SECONDARY_USER_ID = 6602L;
    private static final long STUDENT_USER_ID = 6603L;

    @Autowired
    private AdminMentorOpsRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM mentor_withdrawal_requests WHERE mentor_user_id IN (?, ?)", MENTOR_PRIMARY_USER_ID, MENTOR_SECONDARY_USER_ID);
        jdbcTemplate.update("DELETE FROM consult_after_sales_requests WHERE order_no LIKE 'PG-MENTOR-OPS-%'");
        jdbcTemplate.update(
                "DELETE FROM consult_orders WHERE mentor_user_id IN (?, ?) OR student_user_id = ?",
                MENTOR_PRIMARY_USER_ID,
                MENTOR_SECONDARY_USER_ID,
                STUDENT_USER_ID
        );
        jdbcTemplate.update("DELETE FROM mentor_schedule_slots WHERE mentor_user_id IN (?, ?)", MENTOR_PRIMARY_USER_ID, MENTOR_SECONDARY_USER_ID);
        jdbcTemplate.update("DELETE FROM mentor_service_packages WHERE mentor_user_id IN (?, ?)", MENTOR_PRIMARY_USER_ID, MENTOR_SECONDARY_USER_ID);
        jdbcTemplate.update("DELETE FROM mentor_profiles WHERE user_id IN (?, ?)", MENTOR_PRIMARY_USER_ID, MENTOR_SECONDARY_USER_ID);
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?, ?)", MENTOR_PRIMARY_USER_ID, MENTOR_SECONDARY_USER_ID, STUDENT_USER_ID);
    }

    @Test
    void repositoryShouldSupportMentorOpsAggregationOnPostgres() {
        insertUser(MENTOR_PRIMARY_USER_ID, "MENTOR", "pg-mentor-ops-primary@example.com", "导师治理 PG");
        insertUser(MENTOR_SECONDARY_USER_ID, "MENTOR", "pg-mentor-ops-secondary@example.com", "待审核导师");
        insertUser(STUDENT_USER_ID, "STUDENT", "pg-mentor-ops-student@example.com", "运营台学生");

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant availableAt = now.plus(2, ChronoUnit.DAYS);
        Instant bookedAt = now.plus(1, ChronoUnit.DAYS);

        insertMentorProfile(
                MENTOR_PRIMARY_USER_ID,
                "PG 导师公司",
                "系统设计导师",
                true,
                "https://example.com/avatar-primary.png",
                "avatars/primary.png",
                availableAt.minus(3, ChronoUnit.DAYS),
                "系统设计||后端架构",
                "模拟面试||简历优化",
                true,
                "APPROVED",
                29900,
                new BigDecimal("3.85"),
                now.minus(1, ChronoUnit.HOURS)
        );
        insertMentorProfile(
                MENTOR_SECONDARY_USER_ID,
                "PG 待审核公司",
                "待审核导师",
                false,
                null,
                null,
                null,
                "数据分析",
                "岗位诊断",
                false,
                "PENDING",
                9900,
                new BigDecimal("0.00"),
                now.minus(2, ChronoUnit.HOURS)
        );

        insertPackage(MENTOR_PRIMARY_USER_ID, "管理员约聊套餐", "模拟面试", "APPOINTMENT", 15900, true, 1);
        insertPackage(MENTOR_PRIMARY_USER_ID, "管理员简历套餐", "简历优化", "TEXT_ASYNC", 9900, true, 2);
        insertPackage(MENTOR_PRIMARY_USER_ID, "已下线套餐", "岗位诊断", "TEXT_ASYNC", 12900, false, 3);

        insertScheduleSlot(MENTOR_PRIMARY_USER_ID, availableAt, availableAt.plus(1, ChronoUnit.HOURS), "AVAILABLE", null);
        insertScheduleSlot(MENTOR_PRIMARY_USER_ID, now.plus(8, ChronoUnit.DAYS), now.plus(8, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS), "AVAILABLE", null);
        insertScheduleSlot(MENTOR_PRIMARY_USER_ID, bookedAt, bookedAt.plus(1, ChronoUnit.HOURS), "BOOKED", "PG-MENTOR-OPS-002");

        insertConsultOrder("PG-MENTOR-OPS-001", STUDENT_USER_ID, MENTOR_PRIMARY_USER_ID, 19900, "PAID", "超时待答复订单", now.minus(32, ChronoUnit.HOURS), now.minus(30, ChronoUnit.HOURS), null);
        insertConsultOrder("PG-MENTOR-OPS-002", STUDENT_USER_ID, MENTOR_PRIMARY_USER_ID, 12900, "PAID", "临近超时订单", now.minus(21, ChronoUnit.HOURS), now.minus(20, ChronoUnit.HOURS), null);
        insertConsultOrder("PG-MENTOR-OPS-003", STUDENT_USER_ID, MENTOR_PRIMARY_USER_ID, 14900, "CLOSED", "已完成订单", now.minus(3, ChronoUnit.DAYS), now.minus(3, ChronoUnit.DAYS), now.minus(2, ChronoUnit.DAYS));
        insertConsultOrder("PG-MENTOR-OPS-004", STUDENT_USER_ID, MENTOR_PRIMARY_USER_ID, 9900, "REFUNDED", "退款订单", now.minus(5, ChronoUnit.DAYS), now.minus(4, ChronoUnit.DAYS), now.minus(3, ChronoUnit.DAYS));

        insertAfterSales("PG-MENTOR-OPS-002", STUDENT_USER_ID, "PENDING");
        insertAfterSales("PG-MENTOR-OPS-004", STUDENT_USER_ID, "APPROVED");

        long completedWithdrawalId = insertWithdrawal(MENTOR_PRIMARY_USER_ID, 3200, "COMPLETED", "已完成打款", now.minus(5, ChronoUnit.DAYS));
        long pendingWithdrawalId = insertWithdrawal(MENTOR_PRIMARY_USER_ID, 8800, "PENDING", "待财务处理", now.minus(2, ChronoUnit.DAYS));
        long processingWithdrawalId = insertWithdrawal(MENTOR_PRIMARY_USER_ID, 6600, "PROCESSING", "正在打款", now.minus(1, ChronoUnit.DAYS));

        assertThat(repository.findMentorOps("系统设计", "APPROVED", "PROCESSING", 24))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.mentorUserId()).isEqualTo(MENTOR_PRIMARY_USER_ID);
                    assertThat(row.displayName()).isEqualTo("导师治理 PG");
                    assertThat(row.realName()).isEqualTo("导师治理 PG 实名");
                    assertThat(row.showRealName()).isTrue();
                    assertThat(row.companyName()).isEqualTo("PG 导师公司");
                    assertThat(row.jobTitle()).isEqualTo("系统设计导师");
                    assertThat(row.totalPackageCount()).isEqualTo(3);
                    assertThat(row.enabledPackageCount()).isEqualTo(2);
                    assertThat(row.enabledAppointmentPackageCount()).isEqualTo(1);
                    assertThat(row.startingPriceFen()).isEqualTo(9900);
                    assertThat(row.enabledPackageNames()).isEqualTo("管理员约聊套餐||管理员简历套餐");
                    assertThat(row.enabledSceneLabels()).isEqualTo("模拟面试||简历优化");
                    assertThat(row.weekAvailableSlotCount()).isEqualTo(1);
                    assertThat(row.nextThreeDayAvailableSlotCount()).isEqualTo(1);
                    assertThat(row.upcomingBookedSlotCount()).isEqualTo(1);
                    assertThat(row.nextAvailableAt()).isEqualTo(availableAt);
                    assertThat(row.nextBookedAt()).isEqualTo(bookedAt);
                    assertThat(row.totalOrderCount()).isEqualTo(4);
                    assertThat(row.paidOrderCount()).isEqualTo(2);
                    assertThat(row.closedOrderCount()).isEqualTo(1);
                    assertThat(row.refundedOrderCount()).isEqualTo(1);
                    assertThat(row.overdueReplyOrderCount()).isEqualTo(1);
                    assertThat(row.expiringReplyOrderCount()).isEqualTo(1);
                    assertThat(row.pendingAfterSalesCount()).isEqualTo(1);
                    assertThat(row.afterSalesImpactCount()).isEqualTo(2);
                    assertThat(row.pendingWithdrawalCount()).isEqualTo(1);
                    assertThat(row.processingWithdrawalCount()).isEqualTo(1);
                    assertThat(row.completedWithdrawalCount()).isEqualTo(1);
                    assertThat(row.openWithdrawalAmountFen()).isEqualTo(15400);
                    assertThat(row.latestWithdrawalId()).isEqualTo(processingWithdrawalId);
                    assertThat(row.latestWithdrawalAmountFen()).isEqualTo(6600);
                    assertThat(row.latestWithdrawalStatus()).isEqualTo("PROCESSING");
                    assertThat(row.latestWithdrawalNote()).isEqualTo("正在打款");
                    assertThat(row.avgRating()).isEqualByComparingTo("3.85");
                });

        assertThat(repository.findMentorOps(null, "PENDING", null, 24))
                .singleElement()
                .extracting(AdminMentorOpsRepository.AdminMentorOpsRow::mentorUserId)
                .isEqualTo(MENTOR_SECONDARY_USER_ID);

        assertThat(repository.findWithdrawalById(completedWithdrawalId))
                .isPresent()
                .get()
                .satisfies(row -> {
                    assertThat(row.mentorUserId()).isEqualTo(MENTOR_PRIMARY_USER_ID);
                    assertThat(row.amountFen()).isEqualTo(3200);
                    assertThat(row.status()).isEqualTo("COMPLETED");
                });

        assertThat(repository.updateWithdrawalStatus(processingWithdrawalId, "REJECTED", "人工驳回")).isTrue();
        assertThat(repository.findWithdrawalById(processingWithdrawalId))
                .isPresent()
                .get()
                .satisfies(row -> {
                    assertThat(row.status()).isEqualTo("REJECTED");
                    assertThat(row.note()).isEqualTo("人工驳回");
                });

        assertThat(repository.findMentorOps(null, "APPROVED", "REJECTED", 24))
                .singleElement()
                .extracting(AdminMentorOpsRepository.AdminMentorOpsRow::latestWithdrawalId)
                .isEqualTo(processingWithdrawalId);

        assertThat(pendingWithdrawalId).isPositive();
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
                displayName + " 实名"
        );
    }

    private void insertMentorProfile(
            long userId,
            String companyName,
            String jobTitle,
            boolean showRealName,
            String avatarUrl,
            String avatarObjectKey,
            Instant avatarUpdatedAt,
            String expertiseTags,
            String serviceScenes,
            boolean available,
            String approvalStatus,
            int priceFen,
            BigDecimal avgRating,
            Instant updatedAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO mentor_profiles(
                    user_id,
                    company_name,
                    job_title,
                    show_real_name,
                    avatar_url,
                    avatar_object_key,
                    avatar_updated_at,
                    expertise_tags,
                    service_scenes,
                    price_fen,
                    is_available,
                    approval_status,
                    avg_rating,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?)
                """,
                userId,
                companyName,
                jobTitle,
                showRealName,
                avatarUrl,
                avatarObjectKey,
                timestampOf(avatarUpdatedAt),
                expertiseTags,
                serviceScenes,
                priceFen,
                available,
                approvalStatus,
                avgRating,
                timestampOf(updatedAt)
        );
    }

    private void insertPackage(
            long mentorUserId,
            String packageName,
            String sceneLabel,
            String deliveryMode,
            int priceFen,
            boolean enabled,
            int sortNo
    ) {
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
                ) VALUES (?, ?, ?, ?, ?, 60, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                mentorUserId,
                packageName,
                "scene-" + sortNo,
                sceneLabel,
                deliveryMode,
                priceFen,
                packageName + " 描述",
                enabled,
                sortNo
        );
    }

    private void insertScheduleSlot(long mentorUserId, Instant startAt, Instant endAt, String status, String bookedOrderNo) {
        jdbcTemplate.update(
                """
                INSERT INTO mentor_schedule_slots(
                    mentor_user_id,
                    start_at,
                    end_at,
                    status,
                    booked_order_no,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                mentorUserId,
                timestampOf(startAt),
                timestampOf(endAt),
                status,
                bookedOrderNo
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
                timestampOf(createdAt),
                timestampOf(createdAt),
                timestampOf(paidAt),
                timestampOf(closedAt)
        );
    }

    private void insertAfterSales(String orderNo, long requesterUserId, String status) {
        jdbcTemplate.update(
                """
                INSERT INTO consult_after_sales_requests(
                    order_no,
                    requester_user_id,
                    request_type,
                    status,
                    reason,
                    review_note,
                    reviewer_user_id,
                    auto_triggered,
                    reviewed_at,
                    created_at,
                    updated_at
                ) VALUES (?, ?, 'REFUND', ?, ?, NULL, NULL, FALSE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                orderNo,
                requesterUserId,
                status,
                orderNo + " 售后说明"
        );
    }

    private long insertWithdrawal(long mentorUserId, int amountFen, String status, String note, Instant createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO mentor_withdrawal_requests(
                    mentor_user_id,
                    amount_fen,
                    status,
                    note,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                mentorUserId,
                amountFen,
                status,
                note,
                timestampOf(createdAt),
                timestampOf(createdAt)
        );
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

    private Timestamp timestampOf(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
